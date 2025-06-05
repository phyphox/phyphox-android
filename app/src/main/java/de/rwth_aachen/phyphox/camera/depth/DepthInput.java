package de.rwth_aachen.phyphox.camera.depth;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.locks.Lock;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.DataOutput;
import de.rwth_aachen.phyphox.ExperimentTimeReference;
import de.rwth_aachen.phyphox.camera.helper.CameraHelper;

@RequiresApi(api = Build.VERSION_CODES.M)
public class DepthInput {
    public enum DepthExtractionMode {
        average, closest, weighted
    }

    DepthExtractionMode extractionMode;
    float x1, x2, y1, y2;

    private final ExperimentTimeReference experimentTimeReference;
    private Lock dataLock;

    public DataBuffer dataZ; //Data-buffer for x
    public DataBuffer dataT; //Data-buffer for t

    public CameraManager cameraManager;
    private String cameraId = null;
    int w, h;

    private String previewCameraId = null;
    int previewW, previewH;
    private float zoom = 1.0f;
    private Surface previewSurface = null;

    private CameraDevice cameraDevice = null;
    private CameraCaptureSession session = null;
    private ImageReader imageReader = null;
    private DepthReader depthReader = null;

    private CameraDevice previewCameraDevice = null;
    private CameraCaptureSession previewSession = null;

    public static class DepthInputException extends Exception {
        public DepthInputException(String message) {
            super(message);
        }
    }

    public static boolean isAvailable() {
        Map<String, CameraCharacteristics> cams = CameraHelper.getCameraList();
        for (CameraCharacteristics cam : cams.values()) {
            int[] caps = cam.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            for (int cap : caps) {
                if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
                    return true;
                }
            }
        }
        return false;
    }

    public DepthInput(DepthExtractionMode mode, float x1, float x2, float y1, float y2, Vector<DataOutput> buffers, Lock lock, ExperimentTimeReference experimentTimeReference, CameraManager cameraManager) {
        this.dataLock = lock;
        this.experimentTimeReference = experimentTimeReference;
        this.cameraManager = cameraManager;

        this.extractionMode = mode;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;

        //Store the buffer references if any
        if (buffers == null)
            return;

        if (buffers.size() > 0 && buffers.get(0) != null)
            this.dataZ = buffers.get(0).buffer;
        if (buffers.size() > 1 && buffers.get(1) != null)
            this.dataT = buffers.get(1).buffer;

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static int countCameras(int lensFacing) {
        int count = 0;
        Map<String, CameraCharacteristics> cams = CameraHelper.getCameraList();
        for (Map.Entry<String, CameraCharacteristics> cam : cams.entrySet()) {
            int foundFacing = cam.getValue().get(CameraCharacteristics.LENS_FACING);
            if (lensFacing >= 0 && lensFacing != foundFacing)
                continue;
            int[] caps = cam.getValue().get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            for (int cap : caps) {
                if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
                    Size[] sizes = cam.getValue().get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.DEPTH16);
                    if (sizes == null)
                        continue;
                    count++;
                }
            }
        }
        return count;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static Size getMaxResolution(int lensFacing) {
        Size maxSize = new Size(0, 0);
        Map<String, CameraCharacteristics> cams = CameraHelper.getCameraList();
        for (Map.Entry<String, CameraCharacteristics> cam : cams.entrySet()) {
            int foundFacing = cam.getValue().get(CameraCharacteristics.LENS_FACING);
            if (lensFacing >= 0 && lensFacing != foundFacing)
                continue;
            int[] caps = cam.getValue().get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            for (int cap : caps) {
                if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
                    Size[] sizes = cam.getValue().get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.DEPTH16);
                    if (sizes == null)
                        continue;
                    for (Size size : sizes) {
                        if (size.getWidth() * size.getHeight() > maxSize.getWidth() * maxSize.getHeight())
                            maxSize = size;
                    }
                }
            }
        }
        return maxSize;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static float getMaxRate(int lensFacing) {
        float maxRate = 0.f;
        Map<String, CameraCharacteristics> cams = CameraHelper.getCameraList();
        for (Map.Entry<String, CameraCharacteristics> cam : cams.entrySet()) {
            int foundFacing = cam.getValue().get(CameraCharacteristics.LENS_FACING);
            if (lensFacing >= 0 && lensFacing != foundFacing)
                continue;
            int[] caps = cam.getValue().get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            for (int cap : caps) {
                if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
                    Size[] sizes = cam.getValue().get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.DEPTH16);
                    if (sizes == null)
                        continue;
                    Range<Integer>[] ranges = cam.getValue().get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    if (ranges == null)
                        continue;
                    for (Range<Integer> range : ranges) {
                        if (range.getUpper() > maxRate)
                            maxRate = range.getUpper();
                    }
                }
            }
        }
        return maxRate;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static String findCamera(int lensFacing) {
        String cameraId = null;
        boolean foundBackfacing = false;
        Map<String, CameraCharacteristics> cams = CameraHelper.getCameraList();
        for (Map.Entry<String, CameraCharacteristics> cam : cams.entrySet()) {
            int foundFacing = cam.getValue().get(CameraCharacteristics.LENS_FACING);
            if (lensFacing >= 0 && lensFacing != foundFacing)
                continue;
            int[] caps = cam.getValue().get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            for (int cap : caps) {
                if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
                    Size[] sizes = cam.getValue().get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.DEPTH16);
                    if (sizes == null)
                        continue;
                    cameraId = cam.getKey();
                    if (foundFacing == CameraCharacteristics.LENS_FACING_BACK)
                        foundBackfacing = true;
                    break;
                }
            }
            if (foundBackfacing)
                break;
        }
        return cameraId;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public String findPreviewCamera(int lensFacing) {
        zoom = pickDefaultZoom();
        Map<String, CameraCharacteristics> cams = CameraHelper.getCameraList();
        for (Map.Entry<String, CameraCharacteristics> cam : cams.entrySet()) {
            int foundFacing = cam.getValue().get(CameraCharacteristics.LENS_FACING);
            if (lensFacing >= 0 && lensFacing != foundFacing)
                continue;
            Size[] sizes = cam.getValue().get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class);
            if (sizes == null || sizes.length == 0)
                continue;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Range<Float> range = cam.getValue().get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                if (range.getLower() > zoom || range.getUpper() < zoom) {
                    continue;
                }
            }
            return cam.getKey();
        }

        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void determinePreviewSize(int outWidth, int outHeight) {
        if (previewCameraId == null) {
            previewW = 0;
            previewH = 0;
            return;
        }
        Size[] sizes = CameraHelper.getCameraList().get(previewCameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class);
        if (sizes == null)
            return;

        float targetAspect = (float)w / (float)h;
        int targetWidth, targetHeight;
        if ((float)outWidth/(float)outHeight > targetAspect) {
            targetWidth = Math.round(outHeight * targetAspect);
            targetHeight = outHeight;
        } else {
            targetWidth = outWidth;
            targetHeight = Math.round(outWidth / targetAspect);
        }

        //Log.d("DepthInput", "Searching modes for " + targetWidth + "x" + targetHeight);

        previewW = 0;
        previewH = 0;
        for (Size size : sizes) {
            int newW = size.getWidth();
            int newH = size.getHeight();
            //Log.d("DepthInput", "Considering " + newW + "x" + newH);
            if ((previewW < targetWidth || previewH < targetHeight)) {
                //As long as any side is still too small, we take anything that is larger than before
                if (newW > previewW || newH > previewH) {
                    previewW = newW;
                    previewH = newH;
                }
            } else {
                //We already have something large enough. Now we need to get a bit more picky.
                //However, we do not want to pick something smaller
                if ((newW < targetWidth || newH < targetHeight))
                    continue;
                //Most important criterium: Match aspect ratio of depth sensor
                float aspect = (float)previewW/(float)previewH;
                float newAspect = (float)newW/(float)newH;
                if (Math.abs(targetAspect - newAspect) < Math.abs(targetAspect - aspect)) {
                    previewW = size.getWidth();
                    previewH = size.getHeight();
                } else if (Math.abs(targetAspect - newAspect) == Math.abs(targetAspect - aspect)) {
                    //Same aspect and larger than target? Pick the smaller one
                    if (newW < previewW) {
                        previewW = size.getWidth();
                        previewH = size.getHeight();
                    }
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setCamera(String cameraId) {
        CameraCharacteristics chars = CameraHelper.getCameraList().get(cameraId);
        if (chars == null) {
            Log.e("depthInput", "setCamera: Camera not found in CameraList.");
            return;
        }
        Size[] sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.DEPTH16);
        w = 0;
        h = 0;
        for (Size size : sizes) {
            if (size.getWidth() > w) {
                w = size.getWidth();
                h = size.getHeight();
            }
        }
        this.cameraId = cameraId;

        previewCameraId = findPreviewCamera(chars.get(CameraCharacteristics.LENS_FACING));
    }

    public String getCurrentCameraId() {
        return cameraId;
    }

    public void setExtractionMode(DepthExtractionMode mode) {
        this.extractionMode = mode;
    }

    public DepthExtractionMode getExtractionMode() {
        return this.extractionMode;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public float pickDefaultZoom() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return 1.0f;
        if (cameraId == null)
            return 1.0f;
        CameraCharacteristics chars = CameraHelper.getCameraList().get(cameraId);
        if (chars == null)
            return 1.0f;
        Range<Float> range = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        if (range.getLower() > 1.0f || range.getUpper() < 1.0) {
            return range.getLower();
        }
        return 1.0f;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startCamera() throws DepthInputException {
        if (depthReader == null)
            depthReader = new DepthReader();
        if (cameraId == null) {
            throw new DepthInputException("No camera with depth capability available.");
        }

        Log.i("DepthInput", "Setting up camera \"" + cameraId + "\" at " + w + "x" + h);
        imageReader = ImageReader.newInstance(w, h, ImageFormat.DEPTH16, 10);
        imageReader.setOnImageAvailableListener(depthReader, null);

        if (imageReader == null) {
            Log.e("DepthInput", "imageReader is null.");
            stop();
            return;
        }

        try {
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    if (imageReader == null)
                        return;

                    DepthInput.this.cameraDevice = camera;

                    List<Surface> surfaces = new ArrayList<>();
                    surfaces.add(imageReader.getSurface());
                    try {
                        cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                DepthInput.this.session = session;
                                try {
                                    CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                                    builder.addTarget(imageReader.getSurface());
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, pickDefaultZoom());
                                    session.setRepeatingRequest(builder.build(), null, null);
                                } catch (Exception e) {
                                    Log.e("DepthInput", "Creating requests threw an exception: " + e.getMessage());
                                    e.printStackTrace();
                                    stop();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {
                                Log.e("DepthInput", "Configure failed.");
                                stop();
                            }
                        }, null);
                    } catch (Exception e) {
                        Log.e("DepthInput", "createCaptureSession threw an exception: " + e.getMessage());
                        e.printStackTrace();
                        stop();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    Log.e("DepthInput", "CameraDevice disconnected.");
                    stop();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e("DepthInput", "onError: " + error);
                    stop();
                }
            }, null);
        } catch (SecurityException e) {
            throw new DepthInputException("Security exception: Please reload and make sure to grand camera permissions.");
        } catch (Exception e) {
            throw new DepthInputException("DepthInput: openCamera threw an exception: " + e.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startPreviewCamera() throws DepthInputException {
        if (previewCameraId == null) {
            throw new DepthInputException("No preview camera available.");
        }

        Log.i("DepthInput", "Setting up camera \"" + previewCameraId + "\" for preview.");

        try {
            cameraManager.openCamera(previewCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    Log.d("DepthInput", "Preview camera open");
                    DepthInput.this.previewCameraDevice = camera;
                    try {
                        createPreviewSession();
                    } catch (Exception e) {
                        Log.e("DepthInput", "Error while attaching preview surface: " + e.getMessage());
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    Log.e("DepthInput", "PreviewCameraDevice disconnected.");
                    stop();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e("DepthInput", "onError for preview: " + error);
                    stop();
                }
            }, null);
        } catch (SecurityException e) {
            throw new DepthInputException("Security exception: Please reload and make sure to grand camera permissions.");
        } catch (Exception e) {
            throw new DepthInputException("DepthInput preview: openCamera threw an exception: " + e.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void attachPreviewSurface(SurfaceTexture surface, int width, int height) throws DepthInputException {
        Log.d("DepthInput", "Attaching preview surface with " + width + "x" + height);

        determinePreviewSize(width, height);
        Log.d("DepthInput", "Optimum buffer size is " + previewW + "x" + previewH);
        surface.setDefaultBufferSize(previewW, previewH);
        previewSurface = new Surface(surface);
        if (previewCameraDevice == null)
            startPreviewCamera(); //This will call createPreviewSession when ready
        else
            createPreviewSession();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void createPreviewSession() {
        if (previewSurface == null)
            return;

        Log.d("DepthInput", "Creating preview session.");
        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(previewSurface);
        try {
            previewCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    previewSession = session;
                    try {
                        CaptureRequest.Builder builder = previewCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.addTarget(previewSurface);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom);
                        session.setRepeatingRequest(builder.build(), null, null);
                    } catch (Exception e) {
                        Log.e("DepthPreview", "Creating requests threw an exception: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e("DepthPreview", "Configure failed.");
                }
            }, null);
        } catch (Exception e) {
            Log.e("DepthPreview", "createCaptureSession threw an exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void detachPreviewSurface() {
        Log.d("DepthInput", "Detaching preview surface.");
        if (previewSession != null) {
            try {
                previewSession.stopRepeating();
                previewSession.abortCaptures();
            } catch (Exception e) {
                //Don't worry
            }
            previewSession.close();
            previewSession = null;
        }
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startCameras() throws DepthInputException {
        if (session == null)
            startCamera();
        if (previewSession == null)
            startPreviewCamera();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void stopCameras() {
        Log.d("DepthInput", "Stopping all cameras.");
        if (session != null) {
            try {
                session.stopRepeating();
                session.abortCaptures();
            } catch (Exception e) {
                //Don't worry
            }
            session.close();
            session = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (previewSession != null) {
            try {
                previewSession.stopRepeating();
                previewSession.abortCaptures();
            } catch (Exception e) {
                //Don't worry
            }
            previewSession.close();
            previewSession = null;
        }
        if (previewCameraDevice != null) {
            previewCameraDevice.close();
            previewCameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void start() throws DepthInputException {
        depthReader.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void stop() {
        if (depthReader != null)
            depthReader.stop();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    class DepthReader implements ImageReader.OnImageAvailableListener {
        boolean measuring = false;

        public void start() {
            measuring = true;
        }

        public void stop() {
            measuring = false;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image == null)
                return;
            if (measuring && image.getFormat() == ImageFormat.DEPTH16) {
                int w = image.getWidth();
                int h = image.getHeight();
                double t = experimentTimeReference.getExperimentTimeFromEvent(image.getTimestamp());

                Image.Plane[] planes = image.getPlanes();
                if (planes.length == 1) {
                    Image.Plane plane = planes[0];
                    ShortBuffer buffer = plane.getBuffer().asShortBuffer();

                    int dx = plane.getPixelStride()/2;
                    int dy = plane.getRowStride()/2;
                    int xp1 = Math.round(w * x1);
                    int xp2 = Math.round(w * x2);
                    int yp1 = Math.round(h * y1);
                    int yp2 = Math.round(h * y2);
                    int xi1 = Math.max(Math.min(Math.min(xp1, xp2), w-1), 0);
                    int xi2 = Math.max(Math.min(Math.max(xp1, xp2), w-1), 0);
                    int yi1 = Math.max(Math.min(Math.min(yp1, yp2), h-1), 0);
                    int yi2 = Math.max(Math.min(Math.max(yp1, yp2), h-1), 0);

                    double z;
                    double sum = 0.;
                    switch (extractionMode) {
                        case closest:
                            z = Double.POSITIVE_INFINITY;
                            break;
                        case average:
                        case weighted:
                        default:
                            z = 0.;
                            break;
                    }

                    for (int x = xi1; x <= xi2; x++) {
                        for (int y = yi1; y <= yi2; y++) {
                            short sample = buffer.get(dx*x + y*dy);
                            short depth = (short)(sample & 0x1FFF);
                            short confidence = (short) ((sample >> 13) & 0x7);
                            if (confidence == 1)
                                continue;
                            switch (extractionMode) {
                                case average:
                                    z += depth;
                                    sum++;
                                    break;
                                case closest:
                                    z = Math.min(z, depth);
                                    break;
                                case weighted:
                                    float weight = confidence == 0 ? 1.f : (confidence - 1) / 7.f;
                                    z += depth*weight;
                                    sum += weight;
                                    break;
                            }
                        }
                    }
                    switch (extractionMode) {
                        case average:
                        case weighted:
                            if (sum > 0)
                                z /= sum;
                            else
                                z = Double.NaN;
                            break;
                    }
                    if (Double.isInfinite(z))
                        z = Double.NaN;

                    dataLock.lock();
                    try {
                        if (dataZ != null)
                            dataZ.append(z*0.001); //Given in millimeters, but phyphox uses meter
                        if (dataT != null) {
                            dataT.append(t);
                        }
                    } finally {
                        dataLock.unlock();
                    }
                } else {
                    Log.e("DepthInput", "imageReader encountered unexpected number of planes: " + planes.length);
                }
            }
            image.close();
        }
    }

}

