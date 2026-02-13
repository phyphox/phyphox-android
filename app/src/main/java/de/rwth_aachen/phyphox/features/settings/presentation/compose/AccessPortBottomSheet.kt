package de.rwth_aachen.phyphox.features.settings.presentation.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.accessport.AccessPortSheetUiModel
import de.rwth_aachen.phyphox.ui.string.resolve
import de.rwth_aachen.phyphox.ui.string.toStringUIModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessPortBottomSheet(
    uiModel: AccessPortSheetUiModel,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    val context = LocalContext.current

    var port by remember { mutableStateOf(uiModel.currentPort.resolve(context)) }
    var isError by remember { mutableStateOf(false) }
    LaunchedEffect(uiModel.error) {
        isError = uiModel.error != null
    }


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.settingsPort),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it
                    isError = !uiModel.range.contains(it.toIntOrNull())
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = stringResource(R.string.settingsPort),
                    )
                },
                isError = isError,
                supportingText = {
                    if (uiModel.error != null && isError) {
                        Text(text = uiModel.error.resolve())
                    }else{
                        Text(text = uiModel.range.toString())
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onConfirm(port)
                },
                enabled = !isError,
            ) {
                Text(stringResource(id = android.R.string.ok))
            }

        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun AccessPortBottomSheetPreview() {
    // State for showing the bottom sheet in the preview
    val sheetState = rememberModalBottomSheetState()

    // Example with valid input
    val uiModel = AccessPortSheetUiModel(
        currentPort = "8080".toStringUIModel(),
        range = 1024..65535,
        error = "Some error".toStringUIModel(),
    )

    // Example with an error state (uncomment to see)
    /*
    val uiModelWithError = AccessPortSheetUiModel(
        currentPort = StringUIModel.StaticString("100"), // Invalid initial value
        range = 1024..65535,
        error = StringUIModel.StringResource(R.string.settings_we_access_port_invalid, 1024, 65535)
    )
    */

    AccessPortBottomSheet(
        uiModel = uiModel,
        onDismiss = {},
        onConfirm = {},
        sheetState = sheetState,
    )
}
