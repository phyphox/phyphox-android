package de.rwth_aachen.phyphox.features.settings.presentation.compose.settingscontent

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.clickablepreferenceitem.ClickablePreferenceItem
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.clickablepreferenceitem.LanguagePreferenceItem
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferencecategoryheader.PreferenceCategoryHeader
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.seekbarpreferenceitem.SeekBarConfig
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.seekbarpreferenceitem.SeekBarPreferenceItem
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.segmentedbuttonpreferenceitem.SegmentedButtonPreferenceItem
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.segmentedbuttonpreferenceitem.SegmentedButtonUiModel
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.switchpreferenceitem.SwitchPreferenceItem
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.SettingsAction
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage.LanguageUiModel
import de.rwth_aachen.phyphox.ui.string.ResourceStringUIModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel
import de.rwth_aachen.phyphox.ui.theme.PhyphoxTheme
import de.rwth_aachen.phyphox.utils.UiResourceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    currentLanguage: UiResourceState<LanguageUiModel>,
    seekbarConfig: UiResourceState<SeekBarConfig>,
    appUiMode: UiResourceState<List<SegmentedButtonUiModel<AppUiMode>>>,
    accessPort: UiResourceState<StringUIModel>,
    proximityLockEnabled: UiResourceState<Boolean>,
    onActionEvent: (SettingsAction) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    modifier = Modifier
                        .width(172.dp),
                    contentScale = ContentScale.Fit,
                    painter = painterResource(R.drawable.settings_logo),
                    contentDescription = null,
                )
            }
        }
        item { HorizontalDivider() }
        item {
            PreferenceCategoryHeader(
                title = ResourceStringUIModel(resId = R.string.settingsHeadLanguage),
            )
        }
        item {
            LanguagePreferenceItem(
                title = ResourceStringUIModel(resId = R.string.settingsLanguage),
                summary = currentLanguage,
                iconRes = R.drawable.setting_language,
                onClick = {
                    onActionEvent(SettingsAction.OnAppLanguageClicked)
                },
            )
        }
        item {
            ClickablePreferenceItem(
                title = ResourceStringUIModel(resId = R.string.settingsTranslation),
                summary = UiResourceState.Success(ResourceStringUIModel(R.string.settingsTranslationMore)),
                iconRes = R.drawable.setting_translate,
                onClick = {
                    onActionEvent(SettingsAction.OnLearnMoreAboutTranslationClicked)
                },
            )
        }

        item { HorizontalDivider() }
        // Graph View Category
        item {
            PreferenceCategoryHeader(
                title = ResourceStringUIModel(resId = R.string.settingGraphViewEdit),
            )
        }
        item {
            SeekBarPreferenceItem(
                title = ResourceStringUIModel(R.string.settingGraphSize),
                summary = ResourceStringUIModel(R.string.settingGraphSizeSubTitle),
                iconRes = R.drawable.ic_line_width,
                seekBarConfig = seekbarConfig,
                onValueChange = {
                    onActionEvent(SettingsAction.OnGraphSizeChanged(it))
                },
            )
        }
        item {
            SegmentedButtonPreferenceItem(
                title = ResourceStringUIModel(resId = R.string.settings_theme_title),
                config = appUiMode,
                iconRes = R.drawable.ic_dark_mode,
                onOptionSelected = {
                    onActionEvent(SettingsAction.OnUiModeItemSelected(it))
                },
            )
        }
        item {
            HorizontalDivider()
        }
        // Advanced Category
        item {
            PreferenceCategoryHeader(
                title = ResourceStringUIModel(resId = R.string.settingsHeadAdvanced),
            )
        }
        item {
            ClickablePreferenceItem(
                title = ResourceStringUIModel(resId = R.string.settingsPort),
                summary = accessPort,
                iconRes = R.drawable.setting_http,
                onClick = {
                    onActionEvent(SettingsAction.OnAccessPortClicked)
                },
            )
        }
        item {
            SwitchPreferenceItem(
                title = ResourceStringUIModel(resId = R.string.settingsProximityLock),
                summary = ResourceStringUIModel(resId = R.string.settingsProximityLockDetail),
                iconRes = R.drawable.setting_lock,
                checked = proximityLockEnabled,
                onCheckedChange = {
                    onActionEvent(SettingsAction.OnProximityLockChanged(it))
                },
            )
        }
    }

}


@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
fun SettingsContentPreview() {
    PhyphoxTheme {
        Surface {
            SettingsContent(
                currentLanguage = UiResourceState.Loading,
                seekbarConfig = UiResourceState.Loading,
                appUiMode = UiResourceState.Loading,
                accessPort = UiResourceState.Loading,
                proximityLockEnabled = UiResourceState.Loading,
                onActionEvent = {},
            )
        }
    }
}
