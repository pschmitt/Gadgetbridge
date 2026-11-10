/*  Copyright (C) 2026 Pedro

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.soundpeats

/**
 * Command ids used by the Soundpeats protocol.
 *
 * A device response to command `X` arrives as `X or 0x8000` — see [responseFor].
 *
 * Not every device implements every command: the per-model capability matrix is served by the
 * vendor backend (`/api/app/v1/devices/firmware/{otaCode}/versions/{version}/features`). The
 * commands known to be advertised for the Q3 Pro (otaCode `S12`) are marked with (*).
 */
/**
 * Command ids exchanged with the earbuds.
 *
 * This is the full table as observed on the wire, so entries the current support does not use are
 * kept on purpose: they document the protocol and make it easier to add features later.
 */
object SoundpeatsCommands {
    // Generic / housekeeping
    const val RESET = 0x0305
    const val RESTART = 0x0316
    const val GET_FIRMWARE_VERSION = 0x0309
    const val GET_EARPHONE_COLOR = 0x0326
    const val GET_INITIALIZATION_STATUS = 0x0367
    const val SET_INITIALIZATION_STATUS = 0x0368

    // Battery
    const val GET_LEFT_BATTERY = 0x0306
    const val GET_RIGHT_BATTERY = 0x0307
    const val GET_CASE_BATTERY = 0x0323

    // Noise control
    const val GET_NOISE_CANCELLATION_MODE = 0x0310 // (*)
    const val SET_NOISE_CANCELLATION_MODE = 0x0311 // (*)
    const val GET_NOISE_CANCELLATION_SCENE = 0x0324 // (*)
    const val SET_NOISE_CANCELLATION_SCENE = 0x0325 // (*)
    const val GET_TRANSPARENCY_SCENE_MODE = 0x034C // (*)
    const val SET_TRANSPARENCY_SCENE_MODE = 0x034D // (*)
    const val GET_ANC_ENV_ADAPTIVE_STATUS = 0x0364
    const val SET_ANC_ENV_ADAPTIVE = 0x0365

    // Equalizer
    const val MODIFY_EQ_V1 = 0x0E01
    const val MODIFY_EQ_V2 = 0x0E02 // (*) Q3 Pro only advertises the v2 setter
    const val GET_DYNAMIC_EQ_STATUS = 0x0331
    const val SET_DYNAMIC_EQ = 0x0332
    const val GET_DYNAMIC_BASS_STATUS = 0x033C
    const val SET_DYNAMIC_BASS = 0x033D

    // Modes
    const val GET_GAME_MODE_STATUS = 0x030E // (*)
    const val SET_GAME_MODE = 0x030F // (*)
    const val GET_MOVIE_MODE_STATUS = 0x0327 // (*)
    const val SET_MOVIE_MODE = 0x0328 // (*)
    const val GET_LDAC_STATUS = 0x0338
    const val SET_LDAC = 0x0339
    const val GET_SPACE_STATUS = 0x0357
    const val SET_SPACE_STATUS = 0x0358
    const val SET_SPACE_MODEL = 0x0359

    // Touch controls
    const val GET_TOUCH_DISABLE_STATUS = 0x0312 // (*)
    const val SET_TOUCH_DISABLE = 0x0313 // (*)
    const val GET_CUSTOM_OPERATION_MAPPING = 0x03AA
    const val SET_CUSTOM_OPERATION_MAPPING = 0x03AB
    const val RESET_CUSTOM_OPERATION_MAPPING = 0x03AC

    // Wear detection / fit
    const val GET_IN_EAR_DETECTION_STATUS = 0x030C
    const val SET_IN_EAR_DETECTION = 0x030D
    const val FIT_STATUS = 0x0363
    const val GET_FALL_DETECTION_STATUS = 0x0355
    const val SET_FALL_DETECTION = 0x0356

    // Multipoint
    const val GET_1TO2_STATUS = 0x0314 // (*)
    const val SET_1TO2 = 0x0315 // (*)
    const val GET_HISTORICAL_CONNECTED_DEVICES = 0x0352
    const val SET_DEVICE_CONNECTION_STATUS = 0x0353
    const val SEND_DEVICE_BT_ADDRESS = 0x0351

    // Voice prompts
    const val GET_PROMPT_VOLUME = 0x0319 // (*)
    const val SET_PROMPT_VOLUME = 0x0320 // (*)
    const val GET_VOICE_LANGUAGE = 0x0321 // (*)
    const val SET_VOICE_LANGUAGE = 0x0322 // (*)

    // Find my earbuds
    const val FIND_EARPHONES_DEPRECATED = 0x0333
    const val SET_LEFT_EAR_RING = 0x033A
    const val SET_RIGHT_EAR_RING = 0x033B
    const val GET_LEFT_EAR_RING_STATUS = 0x034A
    const val GET_RIGHT_EAR_RING_STATUS = 0x034B

    // Water drainage (swim / sport models)
    const val SET_WATER_DRAINAGE = 0x033E // (*)
    const val GET_WATER_DRAINAGE_STATUS = 0x0350 // (*)

    // Audio
    const val GET_VOLUME_BALANCE = 0x0375
    const val SET_VOLUME_BALANCE = 0x0376
    const val GET_PERSONALIZED_VOLUME_STATUS = 0x0340
    const val SET_PERSONALIZED_VOLUME = 0x0341
    const val GET_ADAPTIVE_VOLUME_STATUS = 0x0346
    const val SET_ADAPTIVE_VOLUME = 0x0347
    const val GET_DOLBY_EFFECT_STATUS = 0x0348
    const val SET_DOLBY_EFFECT = 0x0349
    const val SET_DOLBY_MODEL = 0x0354
    const val SET_DOLBY_SUPPORT = 0x0362
    const val GET_HEARING_PROTECTION_STATUS = 0x034E
    const val SET_HEARING_PROTECTION = 0x034F
    const val GET_PHONE_CODEC = 0x0377

    // LE Audio / Auracast
    const val SET_LE_AUDIO = 0x0334
    const val GET_LE_AUDIO_STATUS = 0x0335
    const val SET_LE_AUDIO_BROADCAST = 0x0336
    const val GET_LE_AUDIO_BROADCAST_STATUS = 0x0337
    const val AURACAST_CONTROL = 0x0378

    // Misc
    const val GET_LIGHT_EFFECT_STATUS = 0x030A
    const val SET_LIGHT_EFFECT = 0x030B
    const val GET_CLASSIC_BLUETOOTH_STATUS = 0x0317
    const val GET_PANORAMIC_SOUND_MODE = 0x0329
    const val SET_PANORAMIC_SOUND_MODE = 0x0330
    const val SET_LEFT_EAR_MEDIA_VOLUME = 0x0344
    const val SET_RIGHT_EAR_MEDIA_VOLUME = 0x0345
    const val GET_PRIVACY_STATUS = 0x036B
    const val SET_PRIVACY = 0x036C
    const val GET_AUTO_SHUTDOWN_TIME = 0x036E
    const val SET_AUTO_SHUTDOWN_TIME = 0x036F

    /** Responses (device -> host) echo the command with the high bit set. */
    const val RESPONSE_MASK = 0x8000

    @JvmStatic
    fun responseFor(command: Int): Int = command or RESPONSE_MASK

    @JvmStatic
    fun isResponse(command: Int): Boolean = (command and RESPONSE_MASK) != 0
}
