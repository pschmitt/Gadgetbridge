package nodomain.freeyourgadget.gadgetbridge.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import javax.inject.Singleton

/**
 * Bindings for services that used to be created by [nodomain.freeyourgadget.gadgetbridge.GBApplication].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDeviceManager(@ApplicationContext context: Context): DeviceManager =
        DeviceManager(context)
}
