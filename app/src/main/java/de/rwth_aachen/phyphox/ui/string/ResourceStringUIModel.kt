package de.rwth_aachen.phyphox.ui.string

import android.content.Context
import androidx.annotation.StringRes

class ResourceStringUIModel(
    @StringRes val resId: Int,
    vararg val formatArgs: Any,
) : StringUIModel() {
    override fun resolve(context: Context): String {
        return if (formatArgs.isEmpty()) {
            context.getString(resId, *resolveVarArgs(context, formatArgs))
        } else {
            context.getString(resId, *resolveVarArgs(context, formatArgs))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ResourceStringUIModel
        if (resId != other.resId) return false
        if (!formatArgs.contentEquals(other.formatArgs)) return false
        return true
    }

    override fun hashCode(): Int {
        return (9 * resId) + formatArgs.contentHashCode()
    }

    override fun toString(): String {
        return "ResourceStringUIModel(resId=$resId, formatArgs=${formatArgs.contentToString()})"
    }

}
