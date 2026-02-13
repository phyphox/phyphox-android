package de.rwth_aachen.phyphox.features.settings.presentation

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import de.rwth_aachen.phyphox.common.AppDelegate
import de.rwth_aachen.phyphox.features.settings.domain.model.AppLanguage
import de.rwth_aachen.phyphox.features.settings.domain.usecase.language.ObserveCurrentAppLanguageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class AppLanguageDelegate @Inject constructor(
    private val observeCurrentAppLanguageUseCase: ObserveCurrentAppLanguageUseCase,
) : AppDelegate {
    override fun start(coroutineScope: CoroutineScope) {
        observeCurrentAppLanguageUseCase()
            .onEach { appLanguage ->
                val localeList =
                    if (appLanguage.identifier == AppLanguage.SYSTEM_DEFAULT_IDENTIFIER) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(appLanguage.identifier)
                    }
                AppCompatDelegate.setApplicationLocales(localeList)
            }.launchIn(coroutineScope)
    }

    override fun stop() = Unit
}
