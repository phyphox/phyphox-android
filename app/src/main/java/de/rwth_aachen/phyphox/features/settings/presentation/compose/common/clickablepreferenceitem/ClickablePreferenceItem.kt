package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.clickablepreferenceitem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
fun ClickablePreferenceItem(
    modifier: Modifier = Modifier,
    title: StringUIModel,
    summary: UiResourceState<StringUIModel>,
    iconRes: Int? = null,
    onClick: () -> Unit = {},
) {

    PreferenceItem(
        modifier = modifier.clickable(
            enabled = summary is UiResourceState.Success,
            onClick = onClick,
        ),
        title = title,
        iconRes = iconRes,
        content = {
            when (summary) {
                UiResourceState.Loading -> Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height(16.dp)
                        .skeleton(),
                )

                is UiResourceState.Success -> PreferenceSummaryItem(
                    primaryText = summary.data,
                )
            }
        },
    )
}



@Preview(showBackground = true)
@Composable
internal fun ClickablePreferenceItemPreview(
    @PreviewParameter(ClickablePreferenceItemPreviewProvider::class) preview: UiResourceState<StringUIModel>,
) {
    PhyphoxTheme {
        Surface {
            ClickablePreferenceItem(
                title = LoremIpsumStringUIModel(2),
                summary = preview,
            )
        }
    }
}
