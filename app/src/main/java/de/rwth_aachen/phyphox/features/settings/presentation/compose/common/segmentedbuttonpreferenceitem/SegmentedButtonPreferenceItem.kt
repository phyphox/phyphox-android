package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.segmentedbuttonpreferenceitem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferenceitem.PreferenceItem
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferencesummaryitem.PreferenceSummaryItem
import de.rwth_aachen.phyphox.ui.skeleton
import de.rwth_aachen.phyphox.ui.string.LoremIpsumStringUIModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel
import de.rwth_aachen.phyphox.ui.string.resolve
import de.rwth_aachen.phyphox.ui.theme.PhyphoxTheme
import de.rwth_aachen.phyphox.utils.UiResourceState

@Composable
fun SegmentedButtonPreferenceItem(
    modifier: Modifier = Modifier,
    title: StringUIModel,
    summary: StringUIModel? = null,
    iconRes: Int? = null,
    config: UiResourceState<List<SegmentedButtonUiModel<AppUiMode>>>,
    onOptionSelected: (AppUiMode) -> Unit,
) {
    PreferenceItem(
        modifier = modifier,
        title = title,
        iconRes = iconRes,
        content = {
            summary?.let {
                PreferenceSummaryItem(primaryText = summary)
            }
            when (config) {
                UiResourceState.Loading -> Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height(16.dp)
                        .skeleton(),
                )

                is UiResourceState.Success<List<SegmentedButtonUiModel<AppUiMode>>> -> SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    config.data.forEachIndexed { index, option ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = config.data.size,
                            ),
                            onClick = {
                                onOptionSelected(option.item)
                            },
                            selected = option.isSelected,
                            label = { Text(option.text.resolve()) },
                        )
                    }
                }
            }

        },
    )
}

@PreviewLightDark
@Composable
internal fun SegmentedButtonPreferenceItemPreview() {
    PhyphoxTheme {
        SegmentedButtonPreferenceItem(
            title = LoremIpsumStringUIModel(4),
            summary = LoremIpsumStringUIModel(12),
            iconRes = R.drawable.ic_dark_mode,
            config = UiResourceState.Success(
                data = listOf(
                    SegmentedButtonUiModel<AppUiMode>(
                        item = AppUiMode.DARK,
                        text = LoremIpsumStringUIModel(2),
                        isSelected = true,
                    ),
                    SegmentedButtonUiModel(
                        item = AppUiMode.SYSTEM,
                        text = LoremIpsumStringUIModel(1),
                        isSelected = false,
                    ),
                    SegmentedButtonUiModel(
                        item = AppUiMode.LIGHT,
                        text = LoremIpsumStringUIModel(1),
                        isSelected = false,
                    ),
                ),
            ),
            onOptionSelected = {},
        )
    }
}
