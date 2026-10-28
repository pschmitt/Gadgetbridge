/*  Copyright (C) 2026 Vladimir Tasic

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro

/** Watch language ids (`a5 ab 06`). */
object Stratos4ProLanguage {
    /** Watch language id for an ISO language (+ country), null if unknown. */
    fun id(language: String, country: String?): Int? = when (language) {
        "zh" -> if (country == "TW" || country == "HK" || country == "MO") 23 else 1
        "en" -> 2
        "ko" -> 3
        "ja" -> 4
        "de" -> 5
        "es" -> 6
        "fr" -> 7
        "it" -> 8
        "pt" -> 9
        "ar" -> 10
        "hi" -> 12
        "pl" -> 13
        "ru" -> 14
        "nl" -> 15
        "tr" -> 16
        "ur" -> 18
        "in", "id" -> 19
        "pa" -> 20
        "th" -> 21
        "cs" -> 22
        "iw", "he" -> 24
        "sk" -> 25
        "hu" -> 26
        "ro" -> 27
        "be" -> 28
        "sq" -> 29
        "eu" -> 37
        "bg" -> 38
        "fa" -> 40
        "ca" -> 48
        "hr" -> 51
        "da" -> 57
        "ms" -> 64
        "mk" -> 65
        "nb", "no" -> 71
        "sv" -> 74
        "sr", "sh" -> 75
        "sl" -> 77
        "uk" -> 88
        "el" -> 90
        "fi" -> 98
        "vi" -> 99
        "lv" -> 109
        "et" -> 111
        else -> null
    }

    /** Watch id for a Gadgetbridge language pref value ("de_DE", "pt"), null for "auto" or unknown. */
    fun idForPref(pref: String): Int? {
        if (pref == "auto") return null
        val parts = pref.split("_")
        return id(parts[0], parts.getOrNull(1))
    }
}
