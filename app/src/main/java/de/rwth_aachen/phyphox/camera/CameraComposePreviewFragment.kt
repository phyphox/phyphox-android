package de.rwth_aachen.phyphox.camera

/**
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import de.rwth_aachen.phyphox.camera.ui.CameraPreviewCompose

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraComposePreviewFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return ComposeView(requireContext()).apply {
            setContent {
                CameraPreviewCompose(
                    context = context,
                    owner = viewLifecycleOwner
                )
            }
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


    }


}
*/
