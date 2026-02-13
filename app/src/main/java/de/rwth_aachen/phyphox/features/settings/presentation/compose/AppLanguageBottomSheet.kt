package de.rwth_aachen.phyphox.features.settings.presentation.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage.AppLanguageSheetUiModel
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage.LanguageUiModel
import de.rwth_aachen.phyphox.ui.string.resolve

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppLanguageBottomSheet(
    uiModel: AppLanguageSheetUiModel,
    onDismiss: () -> Unit,
    onConfirm: (identifier: String) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(),
            userScrollEnabled = true,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stickyHeader {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        )
                        .padding(16.dp),
                    text = stringResource(R.string.settingsLanguage),
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
            }
            items(uiModel.availableLocales) { item ->
                LanguageListItem(
                    language = item,
                    isSelected = item.identifier == uiModel.currentSelection.identifier,
                    onClick = {
                        onConfirm(item.identifier)
                    },
                )
            }
        }
    }
}


@Composable
private fun LanguageListItem(
    modifier: Modifier = Modifier,
    language: LanguageUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.selected),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(language.displayName.resolve())
            language.localDisplayName?.let {
                Text(
                    text = it.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
