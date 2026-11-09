package nodomain.freeyourgadget.gadgetbridge.di

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceService
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
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

    /**
     * The app's default [SharedPreferences], which back [GBPrefs].
     */
    @Provides
    @Singleton
    @DefaultPrefs
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Provides
    @Singleton
    fun provideGBPrefs(@DefaultPrefs sharedPreferences: SharedPreferences): GBPrefs =
        GBPrefs(sharedPreferences)

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /**
     * The facade for talking to all devices. Use [DeviceService.forDevice] for a single device.
     */
    @Provides
    @Singleton
    fun provideDeviceService(@ApplicationContext context: Context): DeviceService =
        GBDeviceService(context)
}
