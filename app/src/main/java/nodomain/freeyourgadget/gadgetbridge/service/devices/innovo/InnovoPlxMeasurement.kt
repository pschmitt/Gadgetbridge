package nodomain.freeyourgadget.gadgetbridge.service.devices.innovo

import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getSFloat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Innovo PLX measurements - Layout follows Bluetooth SIG PLX Continuous Measurement (0x2A5F),
 * with vendor-specific flag bits.
 */
data class InnovoPlxMeasurement(
    val spo2: Float?,
    val pulseRate: Float?,
    val respiratoryRate: Int?,
    val measurementStatus: Int?,
    val deviceStatus: Int?,
    val perfusionIndex: Float?,
) {
    /**
     * Unstable signal, low perfusion or no finger.
     */
    fun isUnreliable(): Boolean = deviceStatus != null && (deviceStatus and DEVICE_STATUS_UNRELIABLE) != 0

    companion object {
        private const val FLAG_PLETH = 0x01
        private const val FLAG_SPO2_PR = 0x02
        private const val FLAG_MEASUREMENT_STATUS = 0x04
        private const val FLAG_DEVICE_STATUS = 0x08
        private const val FLAG_PERFUSION_INDEX = 0x10
        private const val FLAG_RESPIRATORY_RATE = 0x20
        private const val FLAG_RR_INTERVAL = 0x40
        private const val FLAG_ARTERIAL_STATE = 0x80

        private const val DEVICE_STATUS_UNSTABLE = 1 shl 2
        private const val DEVICE_STATUS_LOW_PERFUSION = 1 shl 5
        private const val DEVICE_STATUS_FINGER_OUT = 1 shl 11
        private const val DEVICE_STATUS_UNRELIABLE =
            DEVICE_STATUS_UNSTABLE or DEVICE_STATUS_LOW_PERFUSION or DEVICE_STATUS_FINGER_OUT

        fun parse(value: ByteArray): InnovoPlxMeasurement? {
            val buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
            val flags = buf.get().toInt() and 0xff
            if ((flags and FLAG_PLETH) != 0) {
                return null
            }

            // The fields are not in flag bit order
            var spo2: Float? = null
            var pulseRate: Float? = null
            if ((flags and FLAG_SPO2_PR) != 0) {
                spo2 = buf.getSFloat()
                pulseRate = buf.getSFloat()
            }
            val respiratoryRate = if ((flags and FLAG_RESPIRATORY_RATE) != 0) buf.get().toInt() and 0xff else null
            if ((flags and FLAG_RR_INTERVAL) != 0) {
                buf.getShort()
            }
            if ((flags and FLAG_ARTERIAL_STATE) != 0) {
                buf.get()
            }
            val measurementStatus =
                if ((flags and FLAG_MEASUREMENT_STATUS) != 0) buf.getShort().toInt() and 0xffff else null
            val deviceStatus = if ((flags and FLAG_DEVICE_STATUS) != 0) {
                (buf.get().toInt() and 0xff) or
                    ((buf.get().toInt() and 0xff) shl 8) or
                    ((buf.get().toInt() and 0xff) shl 16)
            } else {
                null
            }
            val perfusionIndex = if ((flags and FLAG_PERFUSION_INDEX) != 0) buf.getSFloat() else null

            return InnovoPlxMeasurement(
                spo2 = spo2,
                pulseRate = pulseRate,
                respiratoryRate = respiratoryRate,
                measurementStatus = measurementStatus,
                deviceStatus = deviceStatus,
                perfusionIndex = perfusionIndex,
            )
        }
    }
}
