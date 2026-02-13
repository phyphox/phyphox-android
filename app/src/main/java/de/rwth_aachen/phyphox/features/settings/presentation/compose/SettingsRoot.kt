package de.rwth_aachen.phyphox.features.settings.presentation.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.features.settings.presentation.compose.settingscontent.SettingsContent
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.SettingsAction
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.SettingsUiState
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.accessport.AccessPortSheetUiModel
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage.AppLanguageSheetUiModel
import de.rwth_aachen.phyphox.ui.theme.PhyphoxTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoot(
    modifier: Modifier = Modifier,
    uiState: SettingsUiState,
    onActionEvent: (SettingsAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(uiState.modal) {
        if (uiState.modal == null) {
            sheetState.hide()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.action_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onActionEvent(SettingsAction.OnBackPressed)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        SettingsContent(
            modifier = modifier.padding(innerPadding),
            currentLanguage = uiState.currentLanguage,
            seekbarConfig = uiState.graphSize,
            appUiMode = uiState.appUiMode,
            accessPort = uiState.accessPort,
            proximityLockEnabled = uiState.proximityLockEnabled,
            onActionEvent = onActionEvent,
        )
    }
    when (val modal = uiState.modal) {
        is AccessPortSheetUiModel -> AccessPortBottomSheet(
            uiModel = modal,
            sheetState = sheetState,
            onDismiss = {
                coroutineScope.launch {
                    sheetState.hide()
                    onActionEvent.invoke(SettingsAction.OnModalDismissed)
                }
            },
            onConfirm = { newPort ->
                onActionEvent.invoke(SettingsAction.OnAccessPortChanged(newPort))
            },
        )

        is AppLanguageSheetUiModel -> AppLanguageBottomSheet(
            uiModel = modal,
            onDismiss = {
                coroutineScope.launch {
                    sheetState.hide()
                    onActionEvent.invoke(SettingsAction.OnModalDismissed)
                }
            },
            onConfirm = { newLanguage ->
                onActionEvent.invoke(SettingsAction.OnAppLanguageChanged(newLanguage))
            },
            sheetState = sheetState,
        )
    }
}


@Composable
@PreviewLightDark
internal fun SettingsRootPreview() {
    PhyphoxTheme {
        SettingsRoot(
            uiState = SettingsUiState(),
            onActionEvent = {},
        )
    }
}
