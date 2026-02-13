package de.rwth_aachen.phyphox.features.settings.domain.model

enum class AppUiMode(val identifier: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");



    companion object {
        @Suppress("unused")
        fun fromString(identifier: String): AppUiMode? {
            return entries.find { it.identifier == identifier }
        }
    }
}
