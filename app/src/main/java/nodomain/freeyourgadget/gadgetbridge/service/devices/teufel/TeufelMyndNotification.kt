package nodomain.freeyourgadget.gadgetbridge.service.devices.teufel

enum class TeufelMyndNotification(val id: Byte) {
    BATTERY_LOW(0x03),
    POWER_ADAPTER(0x09),
    VOLUME_CHANGE(0x85.toByte()),
    MUTE_STATUS(0x86.toByte()),
    USB_CONNECTION(0x87.toByte()),
    ECO_MODE(0x89.toByte()),
    BATTERY_LEVEL(0x8a.toByte()),
    SOUND_ICONS(0x8e.toByte()),
    PARTY_LINK(0x92.toByte()),
    CONNECTED_SOURCES(0x97.toByte()),
    BATTERY_FRIENDLY_CHARGING(0x98.toByte()),
    ;

    companion object {
        fun fromId(id: Byte): TeufelMyndNotification? = entries.find { it.id == id }
    }
}
