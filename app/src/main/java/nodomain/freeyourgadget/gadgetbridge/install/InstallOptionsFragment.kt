package nodomain.freeyourgadget.gadgetbridge.install

import androidx.fragment.app.Fragment

/**
 * The inputs received by an [InstallHandlerV2]. The install screen shows it only once the session is ready.
 */
abstract class InstallOptionsFragment : Fragment() {
    /**
     * Returns the options to be passed to the device support, or null when an input is not valid. The
     * fragment shows the error itself.
     */
    abstract fun collectOptions(): Any?
}
