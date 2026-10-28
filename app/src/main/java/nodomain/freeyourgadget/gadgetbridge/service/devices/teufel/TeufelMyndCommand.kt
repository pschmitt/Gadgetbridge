package nodomain.freeyourgadget.gadgetbridge.service.devices.teufel

enum class TeufelMyndCommand(val commandId: Short) {
    // Battery
    BATTERY_LEVEL_GET(0x0302),
    POWER_ADAPTER_STATUS_GET(0x0857),

    // Auto-Off Timer
    AUTO_OFF_TIMER_GET(0x0189),
    AUTO_OFF_TIMER_SET(0x0109),

    // Device Color
    DEVICE_COLOR_GET(0x0900),

    // ECO Mode
    ECO_MODE_ENABLE(0x0830),
    ECO_MODE_DISABLE(0x0831),
    ECO_MODE_GET(0x0832),

    // Equalizer
    EQ_GET(0x090d),
    EQ_SET(0x090f),

    // Master Volume
    VOLUME_GET(0x0866),
    VOLUME_SET(0x0867),

    // Master Mute
    MUTE_GET(0x0876),
    MUTE_SET(0x0877),

    // MCU Firmware
    MCU_FIRMWARE_GET(0x0856),

    // PartyLink
    PARTY_LINK_START(0x0901),
    PARTY_LINK_STOP(0x0902),
    PARTY_LINK_GET(0x0903),

    // Sound Icons
    SOUND_ICONS_DISABLE(0x0828),
    SOUND_ICONS_ENABLE(0x0829),
    SOUND_ICONS_GET(0x082a),

    // Bluetooth Firmware
    BT_FIRMWARE_GET(0x0304),

    // Battery Capacity
    BATTERY_CAPACITY_GET(0x0913),

    // Battery Friendly Charging
    BATTERY_FRIENDLY_CHARGING_ENABLE(0x0910),
    BATTERY_FRIENDLY_CHARGING_DISABLE(0x0911),
    BATTERY_FRIENDLY_CHARGING_GET(0x0912),

    // Multipoint
    MULTIPOINT_ENABLE(0x084c),
    MULTIPOINT_DISABLE(0x084d),
    MULTIPOINT_GET(0x084e),

    // Source Selection
    SOURCE_GET(0x0880),
    SOURCE_SET(0x0881),
    CONNECTED_SOURCES_GET(0x0882),

    // LED Brightness
    LED_BRIGHTNESS_GET(0x0840),
    LED_BRIGHTNESS_SET(0x0841),

    // Notifications
    NOTIFICATION_REGISTER(0x4001),
    NOTIFICATION_CANCEL(0x4002),
    NOTIFICATION_EVENT(0x4003),
    ;

    constructor(commandId: Int) : this(commandId.toShort())

    companion object {
        const val ACK_MASK: Int = 0x8000
        const val EQ_BASS: Byte = 0x00.toByte()
        const val EQ_TREBLE: Byte = 0x01.toByte()

        fun fromCommandId(commandId: Short): TeufelMyndCommand? =
            entries.find { it.commandId == commandId }

        fun fromAckCommandId(ackCommandId: Short): TeufelMyndCommand? {
            val originalId = (ackCommandId.toInt() and 0xFFFF xor ACK_MASK).toShort()
            return fromCommandId(originalId)
        }

        fun isAck(commandId: Short): Boolean =
            (commandId.toInt() and ACK_MASK) != 0
    }
}
