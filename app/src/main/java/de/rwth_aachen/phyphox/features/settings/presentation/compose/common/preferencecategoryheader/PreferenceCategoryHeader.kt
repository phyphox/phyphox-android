package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferencecategoryheader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import de.rwth_aachen.phyphox.ui.string.StringUIModel
import de.rwth_aachen.phyphox.ui.string.resolve
import de.rwth_aachen.phyphox.ui.theme.PhyphoxTheme

@Composable
fun PreferenceCategoryHeader(
    modifier: Modifier = Modifier,
    title: StringUIModel,
) {
    Text(
        text = title.resolve(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
internal fun SeekBarPreferenceItemPreview(
    @PreviewParameter(PreferenceCategoryHeaderPreviewProvider::class) preview: StringUIModel,
) {
    PhyphoxTheme {
        Surface {
            PreferenceCategoryHeader(
                title = preview,
            )
        }
    }
}
