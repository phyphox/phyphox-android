package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferenceitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.ui.string.LoremIpsumStringUIModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel
import de.rwth_aachen.phyphox.ui.string.resolve
import de.rwth_aachen.phyphox.ui.theme.PhyphoxTheme

@Composable
fun PreferenceItem(
    modifier: Modifier = Modifier,
    title: StringUIModel,
    iconRes: Int? = null,
    defaultSpacing: Dp = 4.dp,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(defaultSpacing),
    ) {
        iconRes?.let {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(defaultSpacing),
        ) {
            Text(text = title.resolve(), style = MaterialTheme.typography.bodyLarge)
            content?.let { content() }
        }
        trailingContent?.let { trailingContent() }
    }
}

@PreviewLightDark
@Composable
internal fun PreferenceItemPreview() {
    PhyphoxTheme {
        Surface {
            PreferenceItem(
                title = LoremIpsumStringUIModel(4),
                iconRes = R.drawable.ic_dark_mode,
                content = {
                    Text(
                        text = LoremIpsum(15).values.first(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
internal fun PreferenceItemWithTrailingItemPreview() {
    PhyphoxTheme {
        Surface {
            PreferenceItem(
                title = LoremIpsumStringUIModel(4),
                iconRes = R.drawable.ic_dark_mode,
                content = {
                    Text(
                        text = LoremIpsum(15).values.first(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = true,
                        onCheckedChange = {},
                    )
                },
            )
        }
    }
}

