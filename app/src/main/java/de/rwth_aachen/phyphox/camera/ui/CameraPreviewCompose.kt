package de.rwth_aachen.phyphox.camera.ui

class CameraPreviewCompose(){}
/**
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.camera.core.CameraX
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.Commons.REQUIRED_PERMISSIONS
import de.rwth_aachen.phyphox.camera.model.CameraState
import de.rwth_aachen.phyphox.camera.model.CameraUiState
import de.rwth_aachen.phyphox.camera.viewmodel.CameraPreviewViewModel

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@Composable
fun CameraPreviewCompose(
    context: Context,
    owner: LifecycleOwner,
    viewModel: CameraPreviewViewModel = viewModel(),
) {

    val previewUiState: CameraUiState by viewModel.previewUiState.collectAsState()

    var hasCamPermission by remember {
        mutableStateOf(
            REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) ==
                        PackageManager.PERMISSION_GRANTED
            })
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            hasCamPermission = it.size == 2
        })

    LaunchedEffect(key1 = true, block = {
        launcher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    } )

    if(previewUiState.cameraState == CameraState.NOT_READY) {
        Text(text = stringResource(R.string.camera_not_ready))
    } else if(previewUiState.cameraState == CameraState.READY){
        Box{
            Column(modifier = Modifier.fillMaxSize()) {
                if(hasCamPermission){
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            viewModel.startCameraPreviewView(
                                lifecycleOwner = owner,
                                context = context
                            )
                        }
                    )
                }
            }
        }
    }



    Column(
        modifier = Modifier.fillMaxSize(), Arrangement.Bottom, Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {}
        ) {
            Text(text = "Capture")
        }
    }




}
*/
