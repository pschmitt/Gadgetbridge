package nodomain.freeyourgadget.gadgetbridge.service.devices.teufel

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

enum class TeufelMyndSource(val code: Byte, override val label: Int) : LabeledEntry {
    BLUETOOTH(0x00, R.string.menuitem_bluetooth),
    AUX(0x01, R.string.media_source_aux),
    USB(0x02, R.string.media_source_usb),
    ;

    companion object {
        fun fromCode(code: Byte): TeufelMyndSource? = entries.find { it.code == code }
        fun fromPreference(value: String): TeufelMyndSource? = entries.find { it.name == value.uppercase() }
    }
}

enum class TeufelMyndAutoOff(val seconds: Int, override val label: Int) : LabeledEntry {
    OFF(0, R.string.sony_automatic_power_off_off),
    MINUTES_5(300, R.string.minutes_5),
    MINUTES_10(600, R.string.minutes_10),
    MINUTES_15(900, R.string.minutes_15),
    MINUTES_30(1800, R.string.minutes_30),
    HOURS_1(3600, R.string.sony_automatic_power_off_1_hour),
    HOURS_3(10800, R.string.sony_automatic_power_off_3_hour),
    ;

    companion object {
        fun fromSeconds(seconds: Int): TeufelMyndAutoOff? = entries.find { it.seconds == seconds }
        fun fromPreference(value: String): TeufelMyndAutoOff? = entries.find { it.name == value.uppercase() }
    }
}
