package de.rwth_aachen.phyphox.camera.service

import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LiveData

class CameraServiceLiveData private constructor() : LiveData<PreviewView?>() {

    companion object {
        @Volatile
        private var instance: CameraServiceLiveData? = null

        fun getInstance(): CameraServiceLiveData {
            return instance ?: synchronized(this) {
                instance ?: CameraServiceLiveData().also { instance = it }
            }
        }
    }

    fun postCustomObject(data: PreviewView?) {
        value = data
    }
}
