package de.rwth_aachen.phyphox.features.settings.domain.model

data class AppLanguage(
    val identifier: String,
){
    companion object{
        const val SYSTEM_DEFAULT_IDENTIFIER = "system_default"
        val SYSTEM_DEFAULT = AppLanguage(SYSTEM_DEFAULT_IDENTIFIER)
    }
}
