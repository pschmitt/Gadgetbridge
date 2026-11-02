package nodomain.freeyourgadget.gadgetbridge

import dagger.hilt.android.HiltAndroidApp

/**
 * The application entry point for Hilt.
 *
 * [GBApplication] remains in Java, since it is a large class with a lot of legacy initialization
 * logic. This subclass only exists so that Hilt can be enabled from Kotlin, which is required for
 * KSP to process the [HiltAndroidApp] annotation.
 *
 * Note that the generated Hilt base class injects the application component *before* calling
 * [GBApplication.onCreate], so the singleton graph is built before `app`, `prefs`, and
 * `deviceManager` are initialized. Hilt modules must therefore not eagerly depend on that legacy
 * state while the component is being created.
 */
@HiltAndroidApp
open class GBHiltApplication : GBApplication()
