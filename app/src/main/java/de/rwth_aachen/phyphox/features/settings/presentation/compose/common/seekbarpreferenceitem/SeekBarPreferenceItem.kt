package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.seekbarpreferenceitem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferenceitem.PreferenceItem
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferencesummaryitem.PreferenceSummaryItem
import de.rwth_aachen.phyphox.ui.skeleton
import de.rwth_aachen.phyphox.ui.string.LoremIpsumStringUIModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel
import de.rwth_aachen.phyphox.ui.theme.PhyphoxTheme
import de.rwth_aachen.phyphox.utils.UiResourceState

@Composable
fun SeekBarPreferenceItem(
    modifier: Modifier = Modifier,
    title: StringUIModel,
    iconRes: Int? = null,
    summary: StringUIModel? = null,
    seekBarConfig: UiResourceState<SeekBarConfig>,
    onValueChange: (Float) -> Unit,
) {
    PreferenceItem(
        modifier = modifier,
        title = title,
        iconRes = iconRes,
        content = {
            summary?.let {
                PreferenceSummaryItem(primaryText = summary)
            }
            when (seekBarConfig) {
                UiResourceState.Loading -> Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height(16.dp)
                        .skeleton(),
                )

                is UiResourceState.Success<SeekBarConfig> -> Slider(
                    value = seekBarConfig.data.currentSize,
                    onValueChange = onValueChange,
                    valueRange = seekBarConfig.data.range,
                    steps = (seekBarConfig.data.range.endInclusive - seekBarConfig.data.range.start).toInt() - 1,
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
internal fun SeekBarPreferenceItemPreview(
    @PreviewParameter(SeekBarPreferenceItemPreviewProvider::class) graphSize: UiResourceState<SeekBarConfig>,
) {
    PhyphoxTheme {
        Surface {
            SeekBarPreferenceItem(
                title = LoremIpsumStringUIModel(2),
                summary = LoremIpsumStringUIModel(12),
                seekBarConfig = graphSize,
                onValueChange = {},
            )
        }
    }
}
