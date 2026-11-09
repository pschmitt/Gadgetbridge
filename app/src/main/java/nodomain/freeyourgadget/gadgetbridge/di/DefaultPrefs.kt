package nodomain.freeyourgadget.gadgetbridge.di

import javax.inject.Qualifier

/**
 * Marks the app's default [android.content.SharedPreferences], as opposed to the device or widget
 * scoped ones from [nodomain.freeyourgadget.gadgetbridge.util.preferences.ScopedPrefsProvider].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultPrefs
