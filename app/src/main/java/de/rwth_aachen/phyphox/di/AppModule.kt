package de.rwth_aachen.phyphox.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.rwth_aachen.phyphox.features.settings.di.SettingsModule

@Module(includes = [SettingsModule::class])
@InstallIn(SingletonComponent::class)
abstract class AppModule {}

