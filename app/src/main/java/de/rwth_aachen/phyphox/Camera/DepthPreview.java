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
    private TextureView camView;
    Matrix transformation = new Matrix();
    private MarkerOverlayView overlayView;

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
        int w = depthInput.previewW;
        int h = depthInput.previewH;
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

    public void stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;
        if (depthInput != null)
            depthInput.detachPreviewSurface();
    }

    public void setExtractionMode(DepthInput.DepthExtractionMode mode) {
        depthInput.setExtractionMode(mode);
    }

    public void setCamera(String id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;
        depthInput.stopCameras();
        depthInput.setCamera(id);
        try {
            depthInput.startCameras();
        } catch (Exception e) {
            Toast.makeText(getContext(), "DepthPreview: setCamera could not restart depthInput: " + e.getMessage(), Toast.LENGTH_LONG).show(); //Present message
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        if (depthInput == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                depthInput.attachPreviewSurface(st, width, height);
                updateTransformation(width, height);
            } catch (Exception e) {
                Toast.makeText(getContext(), "DepthPreview: startCamera for changed surface texture failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); //Present message
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        if (depthInput == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            depthInput.detachPreviewSurface();
            try {
                depthInput.attachPreviewSurface(st, width, height);
                updateTransformation(width, height);
            } catch (Exception e) {
                Toast.makeText(getContext(), "DepthPreview: startCamera for changed surface texture failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); //Present message
            }
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {

    }
}
