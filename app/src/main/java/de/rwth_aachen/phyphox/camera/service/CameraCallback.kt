package de.rwth_aachen.phyphox.camera.service

import androidx.camera.view.PreviewView

interface CameraCallback {
        fun onSetupCameraPreview(previewView: PreviewView)
}
