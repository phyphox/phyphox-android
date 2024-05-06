package de.rwth_aachen.phyphox.camera.viewmodel

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraViewModelFactory(
) : ViewModelProvider.Factory{

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CameraViewModel() as T
    }
}
