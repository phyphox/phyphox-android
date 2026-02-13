package de.rwth_aachen.phyphox.features.settings.domain.data.local.converter

import de.rwth_aachen.phyphox.features.settings.domain.model.AppLanguage


fun toAppLanguage(identifier: String?): AppLanguage? {
    return identifier?.let {
        AppLanguage(it)
    }
}
