package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.switchpreferenceitem

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferenceitem.PreferenceItem
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferencesummaryitem.PreferenceSummaryItem
import de.rwth_aachen.phyphox.ui.skeleton
import de.rwth_aachen.phyphox.ui.string.LoremIpsumStringUIModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel
import de.rwth_aachen.phyphox.ui.theme.PhyphoxTheme
import de.rwth_aachen.phyphox.utils.UiResourceState
import de.rwth_aachen.phyphox.utils.isChecked

@Composable
fun SwitchPreferenceItem(
    modifier: Modifier = Modifier,
    title: StringUIModel,
    summary: StringUIModel? = null,
    iconRes: Int? = null,
    checked: UiResourceState<Boolean>,
    onCheckedChange: (Boolean) -> Unit,
) {

    PreferenceItem(
        modifier = modifier
            .clickable(checked is UiResourceState.Success) {
                onCheckedChange(checked.isChecked())
            },
        title = title,
        iconRes = iconRes,
        content = {
            summary?.let {
                PreferenceSummaryItem(primaryText = summary)
            }
        },
        trailingContent = {
            when (checked) {
                UiResourceState.Loading -> Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .width(48.dp)
                        .height(32.dp)
                        .skeleton(),
                )

                is UiResourceState.Success<Boolean> ->
                    Switch(
                        checked = checked.isChecked(),
                        onCheckedChange = onCheckedChange,
                    )
            }
        },
    )
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
internal fun SwitchPreferenceItemPreview(
    @PreviewParameter(SwitchPreferenceItemPreviewProvider::class) value: UiResourceState<Boolean>,
) {
    PhyphoxTheme {
        Surface {
            SwitchPreferenceItem(
                title = LoremIpsumStringUIModel(2),
                summary = LoremIpsumStringUIModel(15),
                iconRes = R.drawable.ic_dark_mode,
                checked = value,
                onCheckedChange = {},
            )
        }
    }
}
