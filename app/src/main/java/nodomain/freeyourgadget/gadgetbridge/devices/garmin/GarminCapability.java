/*  Copyright (C) 2023-2026 Petr Kadlec, Mormegil, José Rebelo, Thomas Kuehne, Daniele Gobbetti

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
package nodomain.freeyourgadget.gadgetbridge.devices.garmin;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.CapabilitiesDeviceEvent;

public enum GarminCapability {
    CONNECT_MOBILE_FIT_LINK,
    GOLF_FIT_LINK,
    VIVOKID_JR_FIT_LINK,
    SYNC,
    DEVICE_INITIATES_SYNC,
    HOST_INITIATED_SYNC_REQUESTS,
    GNCS,
    ADVANCED_MUSIC_CONTROLS,
    FIND_MY_PHONE,
    FIND_MY_WATCH,
    CONNECTIQ_HTTP,
    CONNECTIQ_SETTINGS,
    CONNECTIQ_WATCH_APP_DOWNLOAD,
    CONNECTIQ_WIDGET_DOWNLOAD,
    CONNECTIQ_WATCH_FACE_DOWNLOAD,
    CONNECTIQ_DATA_FIELD_DOWNLOAD,
    CONNECTIQ_APP_MANAGEMENT,
    COURSE_DOWNLOAD,
    WORKOUT_DOWNLOAD,
    GOLF_COURSE_DOWNLOAD,
    DELTA_SOFTWARE_UPDATE_FILES,
    FITPAY,
    LIVETRACK,
    LIVETRACK_AUTO_START,
    LIVETRACK_MESSAGING,
    GROUP_LIVETRACK,
    WEATHER_CONDITIONS,
    WEATHER_ALERTS,
    GPS_EPHEMERIS_DOWNLOAD,
    EXPLICIT_ARCHIVE,
    SWING_SENSOR,
    SWING_SENSOR_REMOTE,
    INCIDENT_DETECTION,
    TRUEUP,
    INSTANT_INPUT,
    SEGMENTS,
    AUDIO_PROMPT_LAP,
    AUDIO_PROMPT_PACE_SPEED,
    AUDIO_PROMPT_HEART_RATE,
    AUDIO_PROMPT_POWER,
    AUDIO_PROMPT_NAVIGATION,
    AUDIO_PROMPT_CADENCE,
    SPORT_GENERIC,
    SPORT_RUNNING,
    SPORT_CYCLING,
    SPORT_TRANSITION,
    SPORT_FITNESS_EQUIPMENT,
    SPORT_SWIMMING,
    STOP_SYNC_AFTER_SOFTWARE_UPDATE,
    CALENDAR,
    WIFI_SETUP,
    SMS_NOTIFICATIONS,
    BASIC_MUSIC_CONTROLS,
    AUDIO_PROMPTS_SPEECH,
    DELTA_SOFTWARE_UPDATES,
    GARMIN_DEVICE_INFO_FILE_TYPE,
    SPORT_PROFILE_SETUP,
    HSA_SUPPORT,
    SPORT_STRENGTH,
    SPORT_CARDIO,
    UNION_PAY,
    IPASS,
    CIQ_AUDIO_CONTENT_PROVIDER,
    UNION_PAY_INTERNATIONAL,
    REQUEST_PAIR_FLOW,
    LOCATION_UPDATE,
    LTE_SUPPORT,
    DEVICE_DRIVEN_LIVETRACK_SUPPORT,
    CUSTOM_CANNED_TEXT_LIST_SUPPORT,
    EXPLORE_SYNC,
    INCIDENT_DETECT_AND_ASSISTANCE,
    CURRENT_TIME_REQUEST_SUPPORT,
    CONTACTS_SUPPORT,
    LAUNCH_REMOTE_CIQ_APP_SUPPORT,
    DEVICE_MESSAGES,
    WAYPOINT_TRANSFER,
    MULTI_LINK_SERVICE,
    OAUTH_CREDENTIALS,
    GOLF_9_PLUS_9,
    ANTI_THEFT_ALARM,
    INREACH,
    EVENT_SHARING,
    HSA_FEATURE_MESSAGE,
    HANDS_CALIBRATION,
    LOCATION_UPDATE_PROVIDER,
    LIVE_TRACK_COURSE_SHARING,
    SIMPLE_SETUP_FEATURE_LIST,
    WIND_DATA_APP_SUPPORT,
    HTTP_RAW_RESOURCE_REQUEST,
    SAMD,
    SYNC_2,
    NAVIGATION_SYNC,
    REALTIME_SETTINGS,
    HAND_CALIBRATION_IMG_DETECT,
    TOYSTORE_MOBILE,
    FEATURE_CAPABILITIES_EXCHANGE,
    DEEP_LINKING,
    GARMIN_CONNECT_SERVER,
    CREDENTIALS_OAUTH2_SUPPORT,
    GENERIC_CREDENTIALS,
    CAPABILITY_100, // supported by Forerunner 170 Music
    CAPABILITY_101, // supported by Forerunner 170 Music
    CAPABILITY_102,
    CAPABILITY_103,
    CAPABILITY_104,
    CAPABILITY_105, // supported by Forerunner 170 Music
    CAPABILITY_106,
    CAPABILITY_107,
    CAPABILITY_108,
    CAPABILITY_109,
    CAPABILITY_110,
    CAPABILITY_111,
    CAPABILITY_112, // supported by Forerunner 170 Music
    CAPABILITY_113,
    CAPABILITY_114,
    CAPABILITY_115, // supported by Forerunner 170 Music
    CAPABILITY_116,
    CAPABILITY_117, // supported by Forerunner 170 Music
    CAPABILITY_118,
    CAPABILITY_119,
    CAPABILITY_120,
    CAPABILITY_121,
    CAPABILITY_122,
    CAPABILITY_123,
    CAPABILITY_124,
    CAPABILITY_125,
    CAPABILITY_126,
    CAPABILITY_127,
    CAPABILITY_128, // supported by Forerunner 170 Music
    ;

    private static final Logger LOG = LoggerFactory.getLogger(GarminCapability.class);
    public static final SortedSet<GarminCapability> OUR_CAPABILITIES = new TreeSet<>();
    private static final Map<Integer, GarminCapability> FROM_ORDINAL = new HashMap<>(values().length);

    static {
        for (final GarminCapability cap : values()) {
            FROM_ORDINAL.put(cap.ordinal(), cap);
            OUR_CAPABILITIES.add(cap);
        }

        // not implemented: GPS_EPHEMERIS_EPO_REQUEST, GPS_EPHEMERIS_DATA_REQUEST
        OUR_CAPABILITIES.remove(GPS_EPHEMERIS_DOWNLOAD);

        // not implemented: LIVE_TRACK_STOP, LIVE_TRACK_AUTO_STOP, LIVE_TRACK_AUTO_START
        OUR_CAPABILITIES.remove(LIVETRACK);
        OUR_CAPABILITIES.remove(LIVETRACK_AUTO_START);

        // so far dumps from Garmin Connect have only supported
        // CAPABILITY_112 and CAPABLITY_113
        OUR_CAPABILITIES.remove(CAPABILITY_104);
        OUR_CAPABILITIES.remove(CAPABILITY_105);
        OUR_CAPABILITIES.remove(CAPABILITY_106);
        OUR_CAPABILITIES.remove(CAPABILITY_107);
        OUR_CAPABILITIES.remove(CAPABILITY_108);
        OUR_CAPABILITIES.remove(CAPABILITY_109);
        OUR_CAPABILITIES.remove(CAPABILITY_110);
        OUR_CAPABILITIES.remove(CAPABILITY_111);
        OUR_CAPABILITIES.remove(CAPABILITY_114);
        OUR_CAPABILITIES.remove(CAPABILITY_115);
        OUR_CAPABILITIES.remove(CAPABILITY_116);
        OUR_CAPABILITIES.remove(CAPABILITY_117);
        OUR_CAPABILITIES.remove(CAPABILITY_118);
        OUR_CAPABILITIES.remove(CAPABILITY_119);
    }

    @NonNull
    public static SortedSet<GarminCapability> setFromBinary(@NonNull final byte[] bytes) {
        final SortedSet<GarminCapability> result = new TreeSet<>();
        int current = 0;
        for (int b : bytes) {
            for (int i = 0; i < 8; i++) {
                if ((b & (1 << i)) != 0) {
                    if (FROM_ORDINAL.containsKey(current)) {
                        result.add(FROM_ORDINAL.get(current));
                    } else {
                        LOG.warn("gadget supported GarminCapability {} not found", current);
                    }
                }
                ++current;
            }
        }
        return result;
    }

    @NonNull
    public static SortedSet<GarminCapability> setFromLong(final long capabilitiesMask) {
        final SortedSet<GarminCapability> result = new TreeSet<>();

        for (int i = 0; i < Long.SIZE; i++) {
            if ((capabilitiesMask & (1L << i)) != 0) {
                GarminCapability cap = FROM_ORDINAL.get(i);
                if (cap != null) {
                    result.add(cap);
                }
            }
        }
        return result;
    }

    @NonNull
    public static byte[] setToBinary(@NonNull final Set<GarminCapability> capabilities) {
        final GarminCapability[] values = values();
        final byte[] result = new byte[(values.length + 7) / 8];
        int bytePos = 0;
        int bitPos = 0;
        for (int i = 0; i < values.length; ++i) {
            if (capabilities.contains(FROM_ORDINAL.get(i))) {
                result[bytePos] |= (1 << bitPos);
            }
            ++bitPos;
            if (bitPos >= 8) {
                bitPos = 0;
                ++bytePos;
            }
        }
        return result;
    }

    @NonNull
    public static String setToString(@NonNull final SortedSet<GarminCapability> capabilities) {
        final StringBuilder result = new StringBuilder();
        for (final GarminCapability cap : capabilities) {
            if (result.length() > 0) result.append(", ");
            result.append(cap.name());
        }
        return result.toString();
    }

    @NonNull
    public static List<GBDeviceEvent> getGBDeviceEvent(@NonNull final Set<GarminCapability> capabilities) {
        final SortedSet<Object> capabilitiesPref = new TreeSet<>();
        for (final GarminCapability capability : capabilities) {
            capabilitiesPref.add(capability.name());
        }
        return Arrays.asList(
                new CapabilitiesDeviceEvent(capabilities),
                new GBDeviceEventUpdatePreferences(GarminPreferences.PREF_GARMIN_CAPABILITIES, capabilitiesPref)
        );
    }
}
