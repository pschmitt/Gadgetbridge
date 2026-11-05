package nodomain.freeyourgadget.gadgetbridge.di

import javax.inject.Qualifier

/**
 * Marks the [kotlinx.coroutines.CoroutineDispatcher] for blocking IO. Inject it instead of using
 * [kotlinx.coroutines.Dispatchers.IO] directly, so that tests can replace it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
