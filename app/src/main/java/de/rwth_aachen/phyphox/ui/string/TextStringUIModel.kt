package de.rwth_aachen.phyphox.ui.string

import android.content.Context

class TextStringUIModel(val value: String) : StringUIModel() {
    override fun resolve(context: Context): String = value
}
