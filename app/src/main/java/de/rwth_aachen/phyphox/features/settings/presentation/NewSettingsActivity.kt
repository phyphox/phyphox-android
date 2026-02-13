package de.rwth_aachen.phyphox.features.settings.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.rwth_aachen.phyphox.features.settings.presentation.compose.SettingsRoot
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.SettingsEvent
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.SettingsViewmodelViewModel
import de.rwth_aachen.phyphox.ui.theme.PhyphoxTheme

@AndroidEntryPoint
class NewSettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewmodelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContent {
            PhyphoxTheme {
                Surface {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    ObserveSettingsEvents()
                    SettingsRoot(
                        uiState = uiState,
                        onActionEvent = viewModel::onActionEvent,
                    )
                }

            }
        }
    }


    @Composable
    private fun ObserveSettingsEvents() {
        val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        LaunchedEffect(Unit) {
            viewModel.uiEvent.collect { event ->
                when (event) {
                    SettingsEvent.NavigateBack -> onBackPressedDispatcher?.onBackPressed()
                    is SettingsEvent.OpenWebpage -> {}
                    is SettingsEvent.OpenWebpageFromResourceID -> {}
                }
            }
        }

    }


}

