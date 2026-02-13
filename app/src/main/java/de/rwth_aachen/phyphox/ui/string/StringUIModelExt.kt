package de.rwth_aachen.phyphox.ui.string

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Suppress("unused")
val emptyStringUiModel = "".toStringUIModel()

@Suppress("unused")
fun String.toStringUIModel(): StringUIModel = TextStringUIModel(this)

@Suppress("unused")
fun String?.toStringOrEmptyUIModel(): StringUIModel = this?.toStringUIModel() ?: emptyStringUiModel

@Suppress("unused")
fun @receiver:StringRes Int.toStringUIModel(
    vararg args: Any,
): StringUIModel = ResourceStringUIModel(
    resId = this,
    formatArgs = args,
)

@Suppress("unused")
@Composable
fun StringUIModel.resolve() = resolve(LocalContext.current)

@Suppress("unused")
@Composable
fun StringUIModel?.resolveOrDefault(default: String = ""): String = this?.resolve() ?: default
