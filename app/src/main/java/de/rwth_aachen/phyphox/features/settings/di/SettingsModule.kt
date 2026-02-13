package de.rwth_aachen.phyphox.features.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import de.rwth_aachen.phyphox.common.AppDelegate
import de.rwth_aachen.phyphox.features.settings.data.DefaultAppPreferencesRepository
import de.rwth_aachen.phyphox.features.settings.data.local.DefaultLocalAppPreferencesDataSource
import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import de.rwth_aachen.phyphox.features.settings.domain.data.local.LocalAppPreferencesDataSource
import de.rwth_aachen.phyphox.features.settings.presentation.AppLanguageDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.accessport.AccessPortViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.accessport.DefaultAccessPortViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage.AppLanguageViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage.DefaultAppLanguageViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.appuimode.UiModeViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.appuimode.DefaultUiModeViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.graphsize.DefaultGraphSizeViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.graphsize.GraphSizeViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.proximitylock.DefaultProximityLockViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.proximitylock.ProximityLockViewmodelDelegate
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    internal abstract fun bindsAccessPortDelegate(
        implementation: DefaultAccessPortViewmodelDelegate,
    ): AccessPortViewmodelDelegate

    @Binds
    internal abstract fun bindsAppLanguageDelegate(
        implementation: DefaultAppLanguageViewmodelDelegate,
    ): AppLanguageViewmodelDelegate

    @Binds
    internal abstract fun bindsAppUiModeDelegate(
        implementation: DefaultUiModeViewmodelDelegate,
    ): UiModeViewmodelDelegate

    @Binds
    internal abstract fun bindsGraphSizeDelegate(
        implementation: DefaultGraphSizeViewmodelDelegate,
    ): GraphSizeViewmodelDelegate

    @Binds
    internal abstract fun bindsProximityLockDelegate(
        implementation: DefaultProximityLockViewmodelDelegate,
    ): ProximityLockViewmodelDelegate

    @Binds
    internal abstract fun bindLocalDataSource(
        implementation: DefaultLocalAppPreferencesDataSource,
    ): LocalAppPreferencesDataSource

    @Binds
    internal abstract fun bndAppPreferencesRepository(
        implementation: DefaultAppPreferencesRepository,
    ): AppPreferencesRepository

    @Binds
    @Singleton
    @IntoSet
    abstract fun provideAppLanguageDelegate(implementation: AppLanguageDelegate): AppDelegate

    companion object {
        @Provides
        @Singleton
        @SettingsDataStore
        fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
            return PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("preferences") },
            )
        }
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsDataStore
