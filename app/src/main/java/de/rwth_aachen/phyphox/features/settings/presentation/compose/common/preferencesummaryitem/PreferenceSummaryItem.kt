package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferencesummaryitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import de.rwth_aachen.phyphox.ui.string.LoremIpsumStringUIModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel
import de.rwth_aachen.phyphox.ui.string.resolve
import de.rwth_aachen.phyphox.ui.theme.PhyphoxTheme


@Composable
fun PreferenceSummaryItem(
    modifier: Modifier = Modifier,
    primaryText: StringUIModel,
    secondaryText: StringUIModel? = null,
) {

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            modifier = modifier,
            text = primaryText.resolve(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        secondaryText?.let {
            Text(
                modifier = modifier,
                text = it.resolve(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

}

@PreviewLightDark
@Composable
internal fun PreferenceSummaryItemPreview() {
    PhyphoxTheme {
        PreferenceSummaryItem(
            primaryText = LoremIpsumStringUIModel(4),
        )
    }
}
