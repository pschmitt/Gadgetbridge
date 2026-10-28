/*  Copyright (C) 2015-2026 Carsten Pfeiffer, Daniele Gobbetti, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.service.btle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

// part of the generic BLE specs see https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/uuids/service_uuids.yaml
public enum GattService {
    SERVICE_GAP("00001800-0000-1000-8000-00805f9b34fb", "GAP"),
    SERVICE_GATT("00001801-0000-1000-8000-00805f9b34fb", "GATT"),
    SERVICE_IMMEDIATE_ALERT("00001802-0000-1000-8000-00805f9b34fb", "Immediate Alert"),
    SERVICE_LINK_LOSS ("00001803-0000-1000-8000-00805f9b34fb", "Link Loss"),
    SERVICE_TX_POWER ("00001804-0000-1000-8000-00805f9b34fb", "Tx Power"),
    SERVICE_CURRENT_TIME ("00001805-0000-1000-8000-00805f9b34fb", "Current Time"),
    SERVICE_REFERENCE_TIME_UPDATE("00001806-0000-1000-8000-00805f9b34fb", "Reference Time Update"),
    SERVICE_NEXT_DST_CHANGE ("00001807-0000-1000-8000-00805f9b34fb", "Next DST Change"),
    SERVICE_GLUCOSE ("00001808-0000-1000-8000-00805f9b34fb", "Glucose"),
    SERVICE_HEALTH_THERMOMETER("00001809-0000-1000-8000-00805f9b34fb", "Health Thermometer"),
    SERVICE_DEVICE_INFORMATION ("0000180a-0000-1000-8000-00805f9b34fb", "Device Information"),
    SERVICE_HEART_RATE ("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate"),
    SERVICE_PHONE_ALERT_STATUS ("0000180e-0000-1000-8000-00805f9b34fb", "Phone Alert Status"),
    SERVICE_BATTERY("0000180f-0000-1000-8000-00805f9b34fb", "Battery"),
    SERVICE_BLOOD_PRESSURE("00001810-0000-1000-8000-00805f9b34fb", "Blood Pressure"),
    SERVICE_ALERT_NOTIFICATION("00001811-0000-1000-8000-00805f9b34fb", "Alert Notification"),
    SERVICE_HUMAN_INTERFACE_DEVICE("00001812-0000-1000-8000-00805f9b34fb", "Human Interface Device"),
    SERVICE_SCAN_PARAMETERS ("00001813-0000-1000-8000-00805f9b34fb", "Scan Parameters"),
    SERVICE_RUNNING_SPEED_AND_CADENCE ("00001814-0000-1000-8000-00805f9b34fb", "Running Speed and Cadence"),
    SERVICE_AUTOMATION_IO ("00001815-0000-1000-8000-00805f9b34fb", "Automation IO"),
    SERVICE_CYCLING_SPEED_AND_CADENCE ("00001816-0000-1000-8000-00805f9b34fb", "Cycling Speed and Cadence"),
    SERVICE_CYCLING_POWER ("00001818-0000-1000-8000-00805f9b34fb", "Cycling Power"),
    SERVICE_LOCATION_AND_NAVIGATION ("00001819-0000-1000-8000-00805f9b34fb", "Location and Navigation"),
    SERVICE_ENVIRONMENTAL_SENSING ("0000181a-0000-1000-8000-00805f9b34fb", "Environmental Sensing"),
    SERVICE_BODY_COMPOSITION("0000181b-0000-1000-8000-00805f9b34fb", "Body Composition"),
    SERVICE_USER_DATA ("0000181c-0000-1000-8000-00805f9b34fb", "User Data"),
    SERVICE_WEIGHT_SCALE("0000181d-0000-1000-8000-00805f9b34fb", "Weight Scale"),
    SERVICE_BOND_MANAGEMENT ("0000181e-0000-1000-8000-00805f9b34fb", "Bond Management"),
    SERVICE_CONTINUOUS_GLUCOSE_MONITORING ("0000181f-0000-1000-8000-00805f9b34fb", "Continuous Glucose Monitoring"),
    SERVICE_INTERNET_PROTOCOL_SUPPORT ("00001820-0000-1000-8000-00805f9b34fb", "Internet Protocol Support"),
    SERVICE_INDOOR_POSITIONING ("00001821-0000-1000-8000-00805f9b34fb", "Indoor Positioning"),
    SERVICE_PULSE_OXIMETER ("00001822-0000-1000-8000-00805f9b34fb", "Pulse Oximeter"),
    SERVICE_HTTP_PROXY ("00001823-0000-1000-8000-00805f9b34fb", "HTTP Proxy"),
    SERVICE_TRANSPORT_DISCOVERY ("00001824-0000-1000-8000-00805f9b34fb", "Transport Discovery"),
    SERVICE_OBJECT_TRANSFER ("00001825-0000-1000-8000-00805f9b34fb", "Object Transfer"),
    SERVICE_FITNESS_MACHINE ("00001826-0000-1000-8000-00805f9b34fb", "Fitness Machine"),
    SERVICE_MESH_PROVISIONING ("00001827-0000-1000-8000-00805f9b34fb", "Mesh Provisioning"),
    SERVICE_MESH_PROXY ("00001828-0000-1000-8000-00805f9b34fb", "Mesh Proxy"),
    SERVICE_RECONNECTION_CONFIGURATION ("00001829-0000-1000-8000-00805f9b34fb", "Reconnection Configuration"),
    SERVICE_INSULIN_DELIVERY ("0000183a-0000-1000-8000-00805f9b34fb", "Insulin Delivery"),
    SERVICE_BINARY_SENSOR ("0000183b-0000-1000-8000-00805f9b34fb", "Binary Sensor"),
    SERVICE_EMERGENCY_CONFIGURATION ("0000183c-0000-1000-8000-00805f9b34fb", "Emergency Configuration"),
    SERVICE_AUTHORIZATION_CONTROL ("0000183d-0000-1000-8000-00805f9b34fb", "Authorization Control"),
    SERVICE_PHYSICAL_ACTIVITY_MONITOR ("0000183e-0000-1000-8000-00805f9b34fb", "Physical Activity Monitor"),
    SERVICE_ELAPSED_TIME ("0000183f-0000-1000-8000-00805f9b34fb", "Elapsed Time"),
    SERVICE_GENERIC_HEALTH_SENSOR ("00001840-0000-1000-8000-00805f9b34fb", "Generic Health Sensor"),
    SERVICE_AUDIO_INPUT_CONTROL ("00001843-0000-1000-8000-00805f9b34fb", "Audio Input Control"),
    SERVICE_VOLUME_CONTROL ("00001844-0000-1000-8000-00805f9b34fb", "Volume Control"),
    SERVICE_VOLUME_OFFSET_CONTROL ("00001845-0000-1000-8000-00805f9b34fb", "Volume Offset Control"),
    SERVICE_COORDINATED_SET_IDENTIFICATION ("00001846-0000-1000-8000-00805f9b34fb", "Coordinated Set Identification"),
    SERVICE_DEVICE_TIME ("00001847-0000-1000-8000-00805f9b34fb", "Device Time"),
    SERVICE_MEDIA_CONTROL ("00001848-0000-1000-8000-00805f9b34fb", "Media Control"),
    SERVICE_GENERIC_MEDIA_CONTROL ("00001849-0000-1000-8000-00805f9b34fb", "Generic Media Control"),
    SERVICE_CONSTANT_TONE_EXTENSION ("0000184a-0000-1000-8000-00805f9b34fb", "Constant Tone Extension"),
    SERVICE_TELEPHONE_BEARER ("0000184b-0000-1000-8000-00805f9b34fb", "Telephone Bearer"),
    SERVICE_GENERIC_TELEPHONE_BEARER ("0000184c-0000-1000-8000-00805f9b34fb", "Generic Telephone Bearer"),
    SERVICE_MICROPHONE_CONTROL ("0000184d-0000-1000-8000-00805f9b34fb", "Microphone Control"),
    SERVICE_AUDIO_STREAM_CONTROL ("0000184e-0000-1000-8000-00805f9b34fb", "Audio Stream Control"),
    SERVICE_BROADCAST_AUDIO_SCAN ("0000184f-0000-1000-8000-00805f9b34fb", "Broadcast Audio Scan"),
    SERVICE_PUBLISHED_AUDIO_CAPABILITIES ("00001850-0000-1000-8000-00805f9b34fb", "Published Audio Capabilities"),
    SERVICE_BASIC_AUDIO_ANNOUNCEMENT ("00001851-0000-1000-8000-00805f9b34fb", "Basic Audio Announcement"),
    SERVICE_BROADCAST_AUDIO_ANNOUNCEMENT ("00001852-0000-1000-8000-00805f9b34fb", "Broadcast Audio Announcement"),
    SERVICE_COMMON_AUDIO ("00001853-0000-1000-8000-00805f9b34fb", "Common Audio"),
    SERVICE_HEARING_ACCESS ("00001854-0000-1000-8000-00805f9b34fb", "Hearing Access"),
    SERVICE_TELEPHONY_AND_MEDIA_AUDIO ("00001855-0000-1000-8000-00805f9b34fb", "Telephony and Media Audio"),
    SERVICE_PUBLIC_BROADCAST_ANNOUNCEMENT ("00001856-0000-1000-8000-00805f9b34fb", "Public Broadcast Announcement"),
    SERVICE_ELECTRONIC_SHELF_LABEL ("00001857-0000-1000-8000-00805f9b34fb", "Electronic Shelf Label"),
    SERVICE_GAMING_AUDIO ("00001858-0000-1000-8000-00805f9b34fb", "Gaming Audio"),
    SERVICE_MESH_PROXY_SOLICITATION ("00001859-0000-1000-8000-00805f9b34fb", "Mesh Proxy Solicitation"),
    SERVICE_INDUSTRIAL_MEASUREMENT_DEVICE ("0000185a-0000-1000-8000-00805f9b34fb", "Industrial Measurement Device"),
    SERVICE_RANGING ("0000185b-0000-1000-8000-00805f9b34fb", "Ranging"),
    SERVICE_HID_ISO ("0000185c-0000-1000-8000-00805f9b34fb", "HID ISO"),
    SERVICE_COOKWARE ("0000185d-0000-1000-8000-00805f9b34fb", "Cookware"),
    SERVICE_VOICE_ASSISTANT ("0000185e-0000-1000-8000-00805f9b34fb", "Voice Assistant"),
    SERVICE_GENERIC_VOICE_ASSISTANT ("0000185f-0000-1000-8000-00805f9b34fb", "Generic Voice Assistant"),
    SERVICE_TIRE_PRESSURE_MONITORING_SYSTEM ("00001860-0000-1000-8000-00805f9b34fb", "Tire Pressure Monitoring System"),
    ;

    @NonNull
    public final UUID uuid;
    @NonNull
    public final String prettyName;

    GattService(@NonNull String uuid, @NonNull String prettyName) {
        this.uuid = UUID.fromString(uuid);
        this.prettyName = prettyName;
    }

    @NonNull
    public UUID getUuid() {
        return uuid;
    }

    @NonNull
    @Override
    public String toString(){
        return uuid + " (" + prettyName + ")";
    }

    @Nullable
    public static GattService lookup(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (GattService value : GattService.values()) {
            if (value.uuid.equals(uuid)) {
                return value;
            }
        }
        return null;
    }
}
