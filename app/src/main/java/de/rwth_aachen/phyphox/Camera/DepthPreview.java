package de.rwth_aachen.phyphox.Camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.rwth_aachen.phyphox.MarkerOverlayView;

public class DepthPreview extends FrameLayout implements TextureView.SurfaceTextureListener {
    DepthInput depthInput = null;
    private String cameraId;
    private CameraDevice cameraDevice;
    private TextureView camView;
    Surface surface = null;
    Matrix transformation = new Matrix();
    private MarkerOverlayView overlayView;
    private CameraCaptureSession captureSession;
    private int w = 0, h = 0;
    private int outWidth = 0, outHeight = 0;
    private float zoom = 1.0f;

    private boolean interactive = false;
    private double aspectRatio = 2.5;

    private boolean processingGesture = false;
    int panningIndexX = 0;
    int panningIndexY = 0;
    RectF outer = null;

    private void init() {
        camView = new TextureView(getContext());
        camView.setSurfaceTextureListener(this);
        addView(camView);

        overlayView = new MarkerOverlayView(getContext());
        addView(overlayView);
    }

    public DepthPreview(Context context) {
        super(context);
        init();
    }

    public static class DepthPreviewException extends Exception {
        public DepthPreviewException(String message) {
            super(message);
        }
    }

    public DepthPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            stopCamera();
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public void setAspectRatio(double aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int height, width;

        //Measure Width
        if (widthMode == MeasureSpec.UNSPECIFIED && heightMode == MeasureSpec.UNSPECIFIED) {
            width = 600;
            height = (int)Math.round(600/aspectRatio);
        } else if (widthMode == MeasureSpec.UNSPECIFIED) {
            height = heightSize;
            width = (int)Math.round(height * aspectRatio);
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            width = widthSize;
            height = (int)Math.round(width / aspectRatio);
        } else {
            width = widthSize;
            height = heightSize;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!interactive)
            return super.onTouchEvent(event);

        float[] touch = new float[] {event.getX(), event.getY()};

        Matrix invert = new Matrix();
        transformation.invert(invert);
        invert.mapPoints(touch);
        float x = touch[1] / (float)getHeight();
        float y = 1.0f - touch[0] / (float)getWidth();

        if (outer == null)
            return super.onTouchEvent(event);

        if (!processingGesture
                && (!(x > 0.0 && x < 1.0 && y > 0.0 && y < 1.0)))
            return super.onTouchEvent(event);

        final int action = event.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                processingGesture = true;

                float d11 = (x- depthInput.x1)*(x- depthInput.x1) + (y - depthInput.y1)*(y - depthInput.y1);
                float d12 = (x- depthInput.x1)*(x- depthInput.x1) + (y - depthInput.y2)*(y - depthInput.y2);
                float d21 = (x- depthInput.x2)*(x- depthInput.x2) + (y - depthInput.y1)*(y - depthInput.y1);
                float d22 = (x- depthInput.x2)*(x- depthInput.x2) + (y - depthInput.y2)*(y - depthInput.y2);

                final float dist = 0.1f;

                if (d11 < dist && d11 < d12 && d11 < d21 && d11 < d22) {
                    panningIndexX = 1;
                    panningIndexY = 1;
                } else if (d12 < dist && d12 < d21 && d12 < d22) {
                    panningIndexX = 1;
                    panningIndexY = 2;
                } else if (d21 < dist && d21 < d22) {
                    panningIndexX = 2;
                    panningIndexY = 1;
                } else if (d22 < dist) {
                    panningIndexX = 2;
                    panningIndexY = 2;
                } else {
                    panningIndexX = 0;
                    panningIndexY = 0;

                    return super.onTouchEvent(event);
                }

                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (panningIndexX == 1) {
                    depthInput.x1 = x;
                } else if (panningIndexX == 2) {
                    depthInput.x2 = x;
                }

                if (panningIndexY == 1) {
                    depthInput.y1 = y;
                } else if (panningIndexY == 2) {
                    depthInput.y2 = y;
                }

                updateOverlay();

                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            }
            case MotionEvent.ACTION_UP: {

                processingGesture = false;

                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {

                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            }
        }

        return true;
    }

    private void updateTransformation(int outWidth, int outHeight) {
        if (w == 0 || h == 0)
            return;

        int rotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();

        transformation = new Matrix();

        boolean landscape = (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation);
        float targetAspect = landscape ? (float)w / (float)h : (float)h / (float)w; //Careful: We calculate relative to a portrait orientation, so h and w of the camera start flipped
        float sx, sy;
        if ((float)outWidth/(float)outHeight > targetAspect) {
            sx = outHeight * targetAspect / (float)outWidth;
            sy = 1.f;
        } else {
            sx = 1.f;
            sy = outWidth / targetAspect / (float)outHeight;
        }

        if (Surface.ROTATION_90 == rotation) {
            transformation.postScale(sx / targetAspect , sy * targetAspect);
            transformation.postRotate(-90);
            transformation.postTranslate(0.5f*(1.f-sx)*outWidth, 0.5f*(1.f+sy)*outHeight);
        } else if (Surface.ROTATION_180 == rotation) {
            transformation.postRotate(180);
            transformation.postScale(sx, sy);
            transformation.postTranslate(0.5f*(1.f+sx)*outWidth, 0.5f*(1.f+sy)*outHeight);
        } else if (Surface.ROTATION_270 == rotation) {
            transformation.postScale(sx / targetAspect , sy * targetAspect);
            transformation.postRotate(90);
            transformation.postTranslate(0.5f*(1.f+sx)*outWidth, 0.5f*(1.f-sy)*outHeight);
        } else {
            transformation.postScale(sx, sy);
            transformation.postTranslate(0.5f*(1.f-sx)*outWidth, 0.5f*(1.f-sy)*outHeight);
        }

        camView.setTransform(transformation);
        updateOverlay();
    }

    private void updateOverlay() {
        float xmin = Math.min(depthInput.x1, depthInput.x2);
        float xmax = Math.max(depthInput.x1, depthInput.x2);
        float ymin = Math.min(depthInput.y1, depthInput.y2);
        float ymax = Math.max(depthInput.y1, depthInput.y2);
        RectF inner = new RectF((1.0f-ymax) * getWidth(), xmin * getHeight(), (1.0f-ymin) * getWidth(), xmax * getHeight());
        outer = new RectF(0, 0, getWidth(), getHeight());
        transformation.mapRect(inner);
        transformation.mapRect(outer);
        int[] off = new int[2];
        int[] off2 = new int[2];
        Point[] points;
        if (interactive) {
            points = new Point[4];
            points[0] = new Point(Math.round(inner.left), Math.round(inner.top));
            points[1] = new Point(Math.round(inner.right), Math.round(inner.top));
            points[2] = new Point(Math.round(inner.left), Math.round(inner.bottom));
            points[3] = new Point(Math.round(inner.right), Math.round(inner.bottom));
        } else
            points = null;

        overlayView.setClipRect(outer);
        overlayView.setPassepartout(inner);
        overlayView.update(null, points);
    }

    public void attachDepthInput(DepthInput depthInput) {
        this.depthInput = depthInput;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public String findMatchingCamera(int outWidth, int outHeight) {
        if (depthInput == null)
            return null;
        String depthCamId = depthInput.getCurrentCameraId();
        if (depthCamId == null)
            return null;
        Map<String, CameraCharacteristics> cams = CameraHelper.getCameraList();
        CameraCharacteristics depthCamChar = cams.get(depthCamId);
        if (depthCamChar == null)
            return null;

        String cameraId = null;

        int camFacing = depthCamChar.get(CameraCharacteristics.LENS_FACING);
        zoom = depthInput.pickDefaultZoom();

        for (Map.Entry<String, CameraCharacteristics> cam : cams.entrySet()) {
            if (cam.getValue().get(CameraCharacteristics.LENS_FACING) != camFacing)
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

            float targetAspect = (float)depthInput.getCurrentWidth() / (float)depthInput.getCurrentHeight();
            int targetWidth, targetHeight;
            if ((float)outWidth/(float)outHeight > targetAspect) {
                targetWidth = Math.round(outHeight * targetAspect);
                targetHeight = outHeight;
            } else {
                targetWidth = outWidth;
                targetHeight = Math.round(outWidth / targetAspect);
            }

            w = 0;
            h = 0;
            for (Size size : sizes) {
                int newW = size.getWidth();
                int newH = size.getHeight();
                if ((w < targetWidth || h < targetHeight)) {
                    //As long as any side is still too small, we take anything that is larger than before
                    if (newW > w || newH > h) {
                        w = newW;
                        h = newH;
                    }
                } else {
                    //We already have something large enough. Now we need to get a bit more picky.
                    //However, we do not want to pick something smaller
                    if ((newW < targetWidth || newH < targetHeight))
                        continue;
                    //Most important criterium: Match aspect ratio of depth sensor
                    float aspect = (float)w/(float)h;
                    float newAspect = (float)newW/(float)newH;
                    if (Math.abs(targetAspect - newAspect) < Math.abs(targetAspect - aspect)) {
                        w = size.getWidth();
                        h = size.getHeight();
                    } else if (Math.abs(targetAspect - newAspect) == Math.abs(targetAspect - aspect)) {
                        //Same aspect and larger than target? Pick the smaller one
                        if (newW < w) {
                            w = size.getWidth();
                            h = size.getHeight();
                        }
                    }
                }
            }

            return cam.getKey();
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startCamera(int outWidth, int outHeight) throws DepthPreviewException {
        cameraId = findMatchingCamera(outWidth, outHeight);
        if (cameraId == null) {
            throw new DepthPreviewException("No suitable camera found.");
        }
        Log.i("DepthPreview", "Setting up camera \"" + cameraId + "\" at " + w + "x" + h);
        camView.getSurfaceTexture().setDefaultBufferSize(w, h);
        updateTransformation(outWidth, outHeight);
        try {
            depthInput.cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    DepthPreview.this.cameraDevice = camera;

                    List<Surface> surfaces = new ArrayList<>();
                    if (surface == null)
                        surface = new Surface(camView.getSurfaceTexture());
                    surfaces.add(surface);
                    try {
                        cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                DepthPreview.this.captureSession = session;
                                try {
                                    CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                    builder.addTarget(surface);
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom);
                                    session.setRepeatingRequest(builder.build(), null, null);
                                } catch (Exception e) {
                                    Toast.makeText(getContext(), "DepthPreview: Creating requests threw an exception: " + e.getMessage(), Toast.LENGTH_LONG).show(); //Present message
                                    Log.e("DepthPreview", "Creating requests threw an exception: " + e.getMessage());
                                    e.printStackTrace();
                                    stop();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {
                                Toast.makeText(getContext(), "DepthPreview: Configure failed.", Toast.LENGTH_LONG).show(); //Present message
                                Log.e("DepthPreview", "Configure failed.");
                                stop();
                            }
                        }, null);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "DepthPreview: createCaptureSession threw an exception: " + e.getMessage(), Toast.LENGTH_LONG).show(); //Present message
                        Log.e("DepthPreview", "createCaptureSession threw an exception: " + e.getMessage());
                        e.printStackTrace();
                        stop();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    Toast.makeText(getContext(), "DepthPreview: CameraDevice disconnected.", Toast.LENGTH_LONG).show(); //Present message
                    Log.e("DepthPreview", "CameraDevice disconnected.");
                    stop();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Toast.makeText(getContext(), "DepthPreview: onError: " + error, Toast.LENGTH_LONG).show(); //Present message
                    Log.e("DepthPreview", "onError: " + error);
                    stop();
                }
            }, null);
        } catch (SecurityException e) {
            //This should be caught earlier anyways. Here it is just in case...
            throw new DepthPreviewException("Security exception: Please reload and make sure to grand camera permissions.");
        } catch (Exception e) {
            throw new DepthPreviewException("openCamera threw an exception: " + e.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void stopCamera() {
        if (captureSession != null) {
            try {
                captureSession.abortCaptures();
            } catch (Exception e) {
                //Don't worry
            }
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }

    }

    public void setExtractionMode(DepthInput.DepthExtractionMode mode) {
        depthInput.setExtractionMode(mode);
    }

    public void setCamera(String id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;
        stopCamera();
        depthInput.stopCamera();
        depthInput.setCamera(id);
        try {
            startCamera(outWidth, outHeight);
        } catch (Exception e) {
            Toast.makeText(getContext(), "DepthPreview: setCamera could not restart camera: " + e.getMessage(), Toast.LENGTH_LONG).show(); //Present message
        }
        try {
            depthInput.startCamera();
        } catch (Exception e) {
            Toast.makeText(getContext(), "DepthPreview: setCamera could not restart depthInput: " + e.getMessage(), Toast.LENGTH_LONG).show(); //Present message
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        outWidth = width;
        outHeight = height;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startCamera(width, height);
            } catch (Exception e) {
                Toast.makeText(getContext(), "DepthPreview: startCamera for changed surface texture failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); //Present message
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        outWidth = width;
        outHeight = height;
        updateTransformation(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {

    }
}
