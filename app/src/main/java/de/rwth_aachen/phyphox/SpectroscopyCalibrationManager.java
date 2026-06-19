package de.rwth_aachen.phyphox;


import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.OrientationEventListener;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import de.rwth_aachen.phyphox.camera.analyzer.SpectrumOrientation;

public class SpectroscopyCalibrationManager {

    public enum CalibrationState {
        UNCALIBRATED,
        START,
        FIRST_POINT_SELECTED,
        SECOND_POINT_SELECTED,
        CALIBRATED
    }

    public enum CalibrationMode { X_LINEAR, UNKNOWN }

    public static CalibrationMode calibrationModeFromString(String str){
        if(str == null){
            return CalibrationMode.UNKNOWN;
        }
        return switch (str){
            case "xLinear" -> CalibrationMode.X_LINEAR;
            default -> CalibrationMode.UNKNOWN;
        };
    }

    public static class CalibrationPoint {
        public double pixelPosition;
        public double wavelength;

        public CalibrationPoint(double pixelPosition, double wavelength) {
            this.pixelPosition = pixelPosition;
            this.wavelength = wavelength;
        }
    }

    public static class CalibrationParameters {
        public double slope;
        public double intercept;

        public CalibrationParameters(double slope, double intercept) {
            this.slope = slope;
            this.intercept = intercept;
        }
    }

    public ExpViewFragment topLevelParent;
    private SpectroscopyCalibrationDelegate delegate;
    private List<CalibrationPoint> calibrationPoints;
    private CalibrationState calibrationState;
    private CalibrationParameters calibrationParameters;
    private Context context;

    public SpectroscopyCalibrationManager(Context context, ExpViewFragment parent) {
        this.context = context;
        this.calibrationPoints = new ArrayList<>();
        this.calibrationState = CalibrationState.UNCALIBRATED;
        this.calibrationParameters = null;
        this.topLevelParent = parent;
    }

    public void setDelegate(SpectroscopyCalibrationDelegate delegate) {
        this.delegate = delegate;
    }

    public boolean isCalibrated() {
        return calibrationState == CalibrationState.CALIBRATED && calibrationParameters != null;
    }

    public boolean needsSecondPoint() {
        return calibrationState == CalibrationState.FIRST_POINT_SELECTED;
    }

    public CalibrationState getCalibrationState() {
        return calibrationState;
    }

    public List<CalibrationPoint> getCalibrationPoints() {
        return new ArrayList<>(calibrationPoints);
    }

    public void setCalibrationPoints(List<CalibrationPoint> points) {
        this.calibrationPoints = new ArrayList<>(points);
    }

    public void startCalibration() {
        calibrationPoints.clear();
        calibrationState = CalibrationState.START;
        calibrationParameters = null;

        if (delegate != null) {
            delegate.spectroscopyCalibrationDidStart(this);
        }
    }

    public void setUncalibratedMode() {
        calibrationState = CalibrationState.UNCALIBRATED;

        if (delegate != null) {
            delegate.spectroscopyUnCalibrated(this);
        }
    }

    public void addCalibrationReferencePoint(double pixelIndex, int calibrationMarkerViewCount) {
        CalibrationPoint point = new CalibrationPoint(pixelIndex, 0.0);
        CalibrationGraphUtility.manageCalibrationPoints(calibrationPoints, calibrationMarkerViewCount, point);
    }

    public void requestToAddCalibratedPoint(double pixelIndex) {
        if (calibrationPoints.size() == 1) {
            calibrationState = CalibrationState.FIRST_POINT_SELECTED;
            showWavelengthInputDialog(0, pixelIndex);
        } else {
            calibrationState = CalibrationState.SECOND_POINT_SELECTED;
            showWavelengthInputDialog(1, pixelIndex);
        }
    }

    private void showWavelengthInputDialog(int pointIndex, double pixelValue) {
        String title = pointIndex == 0
                ? getString("enterFirstCalibrationPoint")
                : getString("enterSecondCalibrationPoint");

        String message = String.format(Locale.getDefault(),
                getString("enterWavelengthValue"), pixelValue);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint(getString("spectroscopyWavelengthPlaceholder"));

        if (pointIndex == 0) {
            input.setText("420"); // Default for violet/blue
        } else {
            input.setText("680"); // Default for red
        }

        builder.setView(input);

        builder.setNegativeButton(getString("cancel"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                reconfigureCalibrationState();
                if (delegate != null) {
                    delegate.spectroscopyCalibrationDidDismiss(SpectroscopyCalibrationManager.this);
                }
                dialog.cancel();
            }
        });

        builder.setPositiveButton(getString("ok"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String wavelengthText = input.getText().toString();
                try {
                    double wavelength = Double.parseDouble(wavelengthText);
                    setWavelengthForPoint(pointIndex, wavelength);
                } catch (NumberFormatException e) {
                    reconfigureCalibrationState();
                    if (delegate != null) {
                        delegate.spectroscopyCalibrationDidDismiss(SpectroscopyCalibrationManager.this);
                    }
                }
            }
        });

        AlertDialog dialog = builder.create();

        if (delegate != null) {
            delegate.spectroscopyCalibrationShouldPresentDialog(this, dialog);
        } else {
            dialog.show();
        }
    }

    private void reconfigureCalibrationState() {
        if (calibrationState == CalibrationState.FIRST_POINT_SELECTED) {
            calibrationState = CalibrationState.START;
        } else if (calibrationState == CalibrationState.SECOND_POINT_SELECTED) {
            calibrationState = CalibrationState.FIRST_POINT_SELECTED;
        }
    }

    private void setWavelengthForPoint(int pointIndex, double wavelength) {
        if (pointIndex >= calibrationPoints.size()) {
            return;
        }

        calibrationPoints.get(pointIndex).wavelength = wavelength;

        if (delegate != null) {
            delegate.spectroscopyCalibrationDidUpdatePoints(this, calibrationPoints, calibrationState);
        }

        if (calibrationPoints.size() == 2) {
            performCalibration();
        }
    }

    private void performCalibration() {
        if (calibrationPoints.size() != 2) {
            return;
        }

        CalibrationPoint point1 = calibrationPoints.get(0);
        CalibrationPoint point2 = calibrationPoints.get(1);

        // Linear calibration: wavelength = slope * pixel + intercept
        double deltaWavelength = point2.wavelength - point1.wavelength;
        double deltaPixel = point2.pixelPosition - point1.pixelPosition;

        if (Math.abs(deltaPixel) <= 0.001) {
            // Points are too close, reset calibration
            resetCalibration();
            if (delegate != null) {
                delegate.spectroscopyDidFailWithError(this, "Calibration points are too close together");
            }
            return;
        }

        double slope = deltaWavelength / deltaPixel;
        double intercept = point1.wavelength - slope * point1.pixelPosition;

        calibrationParameters = new CalibrationParameters(slope, intercept);
        calibrationState = CalibrationState.CALIBRATED;

        if (delegate != null) {
            delegate.spectroscopyCalibrationDidComplete(this, slope, intercept);
        }
    }

    public void resetCalibration() {
        calibrationPoints.clear();
        calibrationState = CalibrationState.START;
        calibrationParameters = null;
        if (delegate != null) {
            delegate.spectroscopyCalibrationDidReset(this);
        }
    }

    // Helper method for getting localized strings
    private String getString(String key) {
        int resId = context.getResources().getIdentifier(key, "string", context.getPackageName());
        if (resId != 0) {
            return context.getString(resId);
        }
        return key;
    }
    // Delegate Interface
    public interface SpectroscopyCalibrationDelegate {
        void spectroscopyUnCalibrated(SpectroscopyCalibrationManager manager);
        void spectroscopyCalibrationDidStart(SpectroscopyCalibrationManager manager);
        void spectroscopyCalibrationDidUpdatePoints(SpectroscopyCalibrationManager manager,
                                                    List<CalibrationPoint> points, CalibrationState state);
        void spectroscopyCalibrationDidComplete(SpectroscopyCalibrationManager manager,
                                                double slope, double intercept);
        void spectroscopyCalibrationDidReset(SpectroscopyCalibrationManager manager);
        void spectroscopyCalibrationDidDismiss(SpectroscopyCalibrationManager manager);

        void spectroscopyCalibrationShouldPresentDialog(SpectroscopyCalibrationManager manager,
                                                         AlertDialog dialog);

        void spectroscopyDidFailWithError(SpectroscopyCalibrationManager manager, String error);
    }
}

// Utility class for managing calibration points
class CalibrationGraphUtility {

    public static <T> void manageCalibrationPoints(List<T> array, int currentCount, T newItem) {
        switch (currentCount) {
            case 0:
                array.clear();
                array.add(newItem);
                break;
            case 1:
                array.add(newItem);
                if (array.size() > 2) {
                    array.remove(1);
                }
                break;
            case 2:
                array.add(newItem);
                if (array.size() > 3) {
                    array.remove(2);
                }
                break;
            default:
                if (!array.isEmpty()) {
                    array.remove(array.size() - 1);
                }
                break;
        }
    }
}




