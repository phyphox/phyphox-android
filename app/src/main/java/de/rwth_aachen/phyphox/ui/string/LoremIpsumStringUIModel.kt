package de.rwth_aachen.phyphox.ui.string

import android.content.Context
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum

class LoremIpsumStringUIModel(val wordCount: Int = 500) : StringUIModel() {
    override fun resolve(context: Context): String = LoremIpsum(wordCount).values.first()
}
