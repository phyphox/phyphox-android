package de.rwth_aachen.phyphox.ui.string

import android.content.Context

abstract class StringUIModel {
    abstract fun resolve(context: Context): String

    protected fun resolveVarArgs(
        context: Context,
        args: Array<out Any>,
    ): Array<Any> {
        return args.map {
                if (it is StringUIModel) {
                    it.resolve(context)
                } else {
                    it
                }
            }.toTypedArray()
    }
}
