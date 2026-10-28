package nodomain.freeyourgadget.gadgetbridge.devices.soundcore

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.util.regex.Pattern

class SoundcoreSpaceOneCoordinator : SoundcoreEarbudsCoordinator() {
    override fun isExperimental(): Boolean = true

    override fun getModelName(): String = "A3035"

    override fun getSupportedDeviceName(): Pattern =
        Pattern.compile("soundcore Space One", Pattern.CASE_INSENSITIVE)

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundcore_space_one

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_headphones

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind = DeviceCoordinator.DeviceKind.HEADPHONES

    override fun getBatteryCount(device: GBDevice): Int = 1

    override fun supportsExtendedInfoRequest(): Boolean = false

    override fun supportsSessionInitRequest(): Boolean = false

    override fun getDefaultCapabilities(): Set<Capability> = setOf(
        Capability.NoiseControl,
        Capability.AmbientSound,
        Capability.Transparency,
        Capability.AdaptiveNoiseCancellation,
        Capability.NoiseCancellationModes,
        Capability.WindNoiseReduction,
        Capability.VocalMode,
        Capability.WearingDetection,
        Capability.LDAC,
        Capability.Multipoint,
        Capability.AutoPowerOff,
        Capability.VoicePrompts,
    )
}
