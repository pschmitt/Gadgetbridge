/*  Copyright (C) 2016-2026 Carsten Pfeiffer, João Paulo Barraca, JohnnySun, Thomas Kuehne

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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BleNamesResolver {
    private static final Map<String, String> mServices = new HashMap<>(200);
    private static final Map<String, String> mCharacteristics = new HashMap<>(600);
    private static final SparseArray<String> mValueFormats = new SparseArray<>(10);
    private static final SparseArray<String> mAppearance = new SparseArray<>(20);
    private static final SparseArray<String> mHeartRateSensorLocation = new SparseArray<>(10);

    @NonNull
    static public String resolveServiceName(@Nullable final BluetoothGattService service) {
        if (service != null) {
            return resolveServiceName(service.getUuid());
        }
        return resolveServiceName((UUID) null);
    }

    @NonNull
    static public String resolveServiceName(@Nullable final UUID uuid) {
        if (uuid != null) {
            String result = mServices.get(uuid.toString());
            if (result != null) {
                return result;
            }
        }
        return "Unknown Service";
    }

    static public String resolveValueTypeDescription(final int format) {
        return mValueFormats.get(format, "Unknown Format");
    }

    @NonNull
    static public String resolveCharacteristicName(@Nullable final BluetoothGattCharacteristic characteristic) {
        if (characteristic != null) {
            UUID uuid = characteristic.getUuid();
            if (uuid != null) {
                String result = mCharacteristics.get(uuid.toString());
                if (result != null) {
                    return result;
                }
            }
        }
        return "Unknown Characteristic";
    }

    static public String resolveUuid(final String uuid) {
        String result = mServices.get(uuid);
        if (result != null) return "Service: " + result;

        result = mCharacteristics.get(uuid);
        if (result != null) return "Characteristic: " + result;

        result = "Unknown UUID";
        return result;
    }

    static public String resolveAppearance(int key) {
        return mAppearance.get(key, "Unknown Appearance");
    }

    static public String resolveHeartRateSensorLocation(int key) {
        return mHeartRateSensorLocation.get(key, "Other");
    }

    static public boolean isService(final String uuid) {
        return mServices.containsKey(uuid);
    }

    static public boolean isCharacteristic(final String uuid) {
        return mCharacteristics.containsKey(uuid);
    }

    @NonNull
    public static String toString(@Nullable BluetoothGattCharacteristic characteristic) {
        if (characteristic != null) {
            UUID uuid = characteristic.getUuid();
            return uuid + " (" + resolveCharacteristicName(characteristic) + ")";
        }
        return "<null> (" + resolveCharacteristicName(null) + ")";
    }

    /// lookup description for numeric BluetoothProfile.STATE_...
    static public String getStateString(int state) {
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return "STATE_DISCONNECTED";
            case BluetoothProfile.STATE_CONNECTING:
                return "STATE_CONNECTING";
            case BluetoothProfile.STATE_CONNECTED:
                return "STATE_CONNECTED";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "STATE_DISCONNECTING";
            default:
                return "state_" + state;
        }
    }

    /// lookup description for numeric BluetoothDevice.BOND_...
    static public String getBondStateString(int state) {
        switch (state) {
            case BluetoothDevice.BOND_NONE:
                return "BOND_NONE";
            case BluetoothDevice.BOND_BONDED:
                return "BOND_BONDED";
            case BluetoothDevice.BOND_BONDING:
                return "BOND_BONDING";
            default:
                return "bond_" + state;
        }
    }

    /// lookup description for numeric status used by {@link android.bluetooth.BluetoothGattCallback}
    static public String getStatusString(int status) {
        // android.bluetooth.BluetoothGatt's numbers are incomplete
        // numeric values taken from GattStatus in https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/system/stack/include/gatt_api.h
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                return "GATT_SUCCESS";
            case 0x01:
                // GATT_CONN_L2C_FAILURE is also 0x01
                return "GATT_INVALID_HANDLE";
            case BluetoothGatt.GATT_READ_NOT_PERMITTED:
                return "GATT_READ_NOT_PERMITTED";
            case BluetoothGatt.GATT_WRITE_NOT_PERMITTED:
                return "GATT_WRITE_NOT_PERMITTED";
            case 0x04:
                return "GATT_INVALID_PDU";
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
                return "GATT_INSUFFICIENT_AUTHENTICATION";
            case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
                return "GATT_REQUEST_NOT_SUPPORTED";
            case BluetoothGatt.GATT_INVALID_OFFSET:
                return "GATT_INVALID_OFFSET";
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION:
                // GATT_CONN_TIMEOUT is also 0x08
                return "GATT_INSUFFICIENT_AUTHORIZATION";
            case 0x09:
                return "GATT_PREPARE_Q_FULL";
            case 0x0a:
                return "GATT_NOT_FOUND";
            case 0x0b:
                return "GATT_NOT_LONG";
            case 0x0c:
                return "GATT_INSUF_KEY_SIZE";
            case BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH:
                return "GATT_INVALID_ATTRIBUTE_LENGTH";
            case 0x0e:
                return "GATT_ERR_UNLIKELY";
            case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
                return "GATT_INSUFFICIENT_ENCRYPTION";
            case 0x10:
                return "GATT_UNSUPPORT_GRP_TYPE";
            case 0x11:
                return "GATT_INSUF_RESOURCE";
            case 0x12:
                return "GATT_DATABASE_OUT_OF_SYNC";
            case 0x13:
                // GATT_CONN_TERMINATE_PEER_USER is also 0x13
                return "GATT_VALUE_NOT_ALLOWED";
            case 0x15:
                return "GATT_CONN_TERMINATED_POWER_OFF";
            case 0x16:
                return "GATT_CONN_TERMINATE_LOCAL_HOST";
            case 0x22:
                return "GATT_CONN_LMP_TIMEOUT";
            case 0x3e:
                return "GATT_CONN_FAILED_ESTABLISHMENT";
            case 0x87:
                return "GATT_ILLEGAL_PARAMETER";
            case 0x7f:
                return "GATT_TOO_SHORT";
            case 0x80:
                return "GATT_NO_RESOURCES";
            case 0x81:
                return "GATT_INTERNAL_ERROR";
            case 0x82:
                return "GATT_WRONG_STATE";
            case 0x83:
                return "GATT_DB_FULL";
            case 0x84:
                return "GATT_BUSY";
            case 0x85:
                return "GATT_ERROR";
            case 0x86:
                return "GATT_CMD_STARTED";
            case 0x88:
                return "GATT_PENDING";
            case 0x89:
                return "GATT_AUTH_FAIL";
            case 0x8a:
                return "GATT_MORE";
            case 0x8b:
                return "GATT_INVALID_CFG";
            case 0x8c:
                return "GATT_SERVICE_STARTED";
            case 0x8d:
                return "GATT_ENCRYPED_NO_MITM";
            case 0x8e:
                return "GATT_NOT_ENCRYPTED";
            case BluetoothGatt.GATT_CONNECTION_CONGESTED:
                return "GATT_CONNECTION_CONGESTED";
            case 0x90:
                return "GATT_DUP_REG";
            case 0x91:
                return "GATT_ALREADY_OPEN";
            case 0x92:
                return "GATT_CANCEL";
            case BluetoothGatt.GATT_CONNECTION_TIMEOUT:
                return "GATT_CONNECTION_TIMEOUT";
            case 0xFD:
                return "GATT_CCC_CFG_ERR";
            case 0xFE:
                return "GATT_PRC_IN_PROGRESS";
            case 0xFF:
                return "GATT_OUT_OF_RANGE";
            case BluetoothGatt.GATT_FAILURE:
                return "GATT_FAILURE";
            default:
                return "failed_" + status;
        }
    }

    /// Name for the status of {@code onConnectionStateChange}. For a disconnect
    /// that is a link-layer reason (tGATT_DISCONN_REASON in gatt_api.h), not a
    /// GATT operation status: 8 is a supervision timeout here, while
    /// {@link #getStatusString} would call it GATT_INSUFFICIENT_AUTHORIZATION.
    public static String getConnectionStatusString(int status) {
        switch (status) {
            case 0x00:
                return "GATT_SUCCESS";
            case 0x01:
                return "GATT_CONN_L2C_FAILURE";
            case 0x05:
                return "GATT_CONN_AUTH_FAILURE";
            case 0x06:
                return "GATT_CONN_KEY_MISSING";
            case 0x08:
                return "GATT_CONN_TIMEOUT";
            case 0x13:
                return "GATT_CONN_TERMINATE_PEER_USER";
            case 0x16:
                return "GATT_CONN_TERMINATE_LOCAL_HOST";
            case 0x22:
                return "GATT_CONN_LMP_TIMEOUT";
            case 0x3D:
                return "GATT_CONN_MIC_FAILURE";
            case 0x3E:
                return "GATT_CONN_FAILED_ESTABLISHMENT";
            case 0x85:
                return "GATT_ERROR";
            case 0x93:
                return "GATT_CONNECTION_TIMEOUT";
            case 0x100:
                return "GATT_CONN_CANCEL";
            default:
                return "conn_status_" + status;
        }
    }

    /// lookup description for numeric {@link android.bluetooth.BluetoothStatusCodes}
    public static String getBluetoothStatusString(int status) {
        // many of the values in BluetoothStatusCodes aren't accessible due to @SystemApi
        switch (status) {
            case 0:
                return "SUCCESS";
            case 1:
                return "ERROR_BLUETOOTH_NOT_ENABLED";
            case 2:
                return "ERROR_BLUETOOTH_NOT_ALLOWED";
            case 3:
                return "ERROR_DEVICE_NOT_BONDED";
            case 4:
                return "ERROR_DEVICE_NOT_CONNECTED";
            case 6:
                return "ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION";
            case 7:
                return "ERROR_MISSING_BLUETOOTH_SCAN_PERMISSION";
            case 9:
                return "ERROR_PROFILE_SERVICE_NOT_BOUND";
            case 10:
                return "FEATURE_SUPPORTED";
            case 11:
                return "FEATURE_NOT_SUPPORTED";
            case 12:
                return "ERROR_NOT_ACTIVE_DEVICE";
            case 13:
                return "ERROR_NO_ACTIVE_DEVICES";
            case 14:
                return "ERROR_PROFILE_NOT_CONNECTED";
            case 15:
                return "ERROR_TIMEOUT";
            case 16:
                return "REASON_LOCAL_APP_REQUEST";
            case 17:
                return "REASON_LOCAL_STACK_REQUEST";
            case 18:
                return "REASON_REMOTE_REQUEST";
            case 19:
                return "REASON_SYSTEM_POLICY";
            case 20:
                return "ERROR_HARDWARE_GENERIC";
            case 21:
                return "ERROR_BAD_PARAMETERS";
            case 22:
                return "ERROR_LOCAL_NOT_ENOUGH_RESOURCES";
            case 23:
                return "ERROR_REMOTE_NOT_ENOUGH_RESOURCES";
            case 24:
                return "ERROR_REMOTE_OPERATION_REJECTED";
            case 25:
                return "ERROR_REMOTE_LINK_ERROR";
            case 26:
                return "ERROR_ALREADY_IN_TARGET_STATE";
            case 27:
                return "ERROR_REMOTE_OPERATION_NOT_SUPPORTED";
            case 28:
                return "ERROR_CALLBACK_NOT_REGISTERED";
            case 29:
                return "ERROR_ANOTHER_ACTIVE_REQUEST";
            case 30:
                return "FEATURE_NOT_CONFIGURED";
            case 200:
                return "ERROR_GATT_WRITE_NOT_ALLOWED";
            case 201:
                return "ERROR_GATT_WRITE_REQUEST_BUSY";
            case 400:
                return "ALLOWED";
            case 401:
                return "NOT_ALLOWED";
            case 1000:
                return "ERROR_ANOTHER_ACTIVE_OOB_REQUEST";
            case 1100:
                return "ERROR_DISCONNECT_REASON_LOCAL_REQUEST";
            case 1101:
                return "ERROR_DISCONNECT_REASON_REMOTE_REQUEST";
            case 1102:
                return "ERROR_DISCONNECT_REASON_LOCAL";
            case 1103:
                return "ERROR_DISCONNECT_REASON_REMOTE";
            case 1104:
                return "ERROR_DISCONNECT_REASON_TIMEOUT";
            case 1105:
                return "ERROR_DISCONNECT_REASON_SECURITY";
            case 1106:
                return "ERROR_DISCONNECT_REASON_SYSTEM_POLICY";
            case 1107:
                return "ERROR_DISCONNECT_REASON_RESOURCE_LIMIT_REACHED";
            case 1108:
                return "ERROR_DISCONNECT_REASON_CONNECTION_ALREADY_EXISTS";
            case 1109:
                return "ERROR_DISCONNECT_REASON_BAD_PARAMETERS";
            case 1116:
                return "ERROR_AUDIO_DEVICE_ALREADY_CONNECTED";
            case 1117:
                return "ERROR_AUDIO_DEVICE_ALREADY_DISCONNECTED";
            case 1118:
                return "ERROR_AUDIO_ROUTE_BLOCKED";
            case 1119:
                return "ERROR_CALL_ACTIVE";
            case 1200:
                return "ERROR_LE_BROADCAST_INVALID_BROADCAST_ID";
            case 1201:
                return "ERROR_LE_BROADCAST_INVALID_CODE";
            case 1202:
                return "ERROR_LE_BROADCAST_ASSISTANT_INVALID_SOURCE_ID";
            case 1203:
                return "ERROR_LE_BROADCAST_ASSISTANT_DUPLICATE_ADDITION";
            case 1204:
                return "ERROR_LE_CONTENT_METADATA_INVALID_PROGRAM_INFO";
            case 1205:
                return "ERROR_LE_CONTENT_METADATA_INVALID_LANGUAGE";
            case 1206:
                return "ERROR_LE_CONTENT_METADATA_INVALID_OTHER";
            case 1207:
                return "ERROR_CSIP_INVALID_GROUP_ID";
            case 1208:
                return "ERROR_CSIP_GROUP_LOCKED_BY_OTHER";
            case 1209:
                return "ERROR_CSIP_LOCKED_GROUP_MEMBER_LOST";
            case 1210:
                return "ERROR_HAP_PRESET_NAME_TOO_LONG";
            case 1211:
                return "ERROR_HAP_INVALID_PRESET_INDEX";
            case 1300:
                return "ERROR_NO_LE_CONNECTION";
            case 1301:
                return "ERROR_DISTANCE_MEASUREMENT_INTERNAL";
            case 2000:
                return "RFCOMM_LISTENER_START_FAILED_UUID_IN_USE";
            case 2001:
                return "RFCOMM_LISTENER_OPERATION_FAILED_NO_MATCHING_SERVICE_RECORD";
            case 2002:
                return "RFCOMM_LISTENER_OPERATION_FAILED_DIFFERENT_APP";
            case 2003:
                return "RFCOMM_LISTENER_FAILED_TO_CREATE_SERVER_SOCKET";
            case 2004:
                return "RFCOMM_LISTENER_FAILED_TO_CLOSE_SERVER_SOCKET";
            case 2005:
                return "RFCOMM_LISTENER_NO_SOCKET_AVAILABLE";
            case 3000:
                return "ERROR_NOT_DUAL_MODE_AUDIO_DEVICE";
            default:
                return "status_" + status;
        }
    }

    /// lookup description for numeric {@link BluetoothGatt#requestConnectionPriority} priorities
    public static String getConnectionPriorityString(int priority){
        return switch (priority) {
            case BluetoothGatt.CONNECTION_PRIORITY_BALANCED -> "CONNECTION_PRIORITY_BALANCED";
            case BluetoothGatt.CONNECTION_PRIORITY_HIGH -> "CONNECTION_PRIORITY_HIGH";
            case BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER -> "CONNECTION_PRIORITY_LOW_POWER";
            case BluetoothGatt.CONNECTION_PRIORITY_DCK -> "CONNECTION_PRIORITY_DCK";
            default -> "priority_" + priority;
        };
    }

    /// lookup description for numeric bond loss reasons (introduced in API 36.1)
    public static String getBondLossReasonString(int reason){
        return switch (reason) {
            case 0 -> "BOND_LOSS_REASON_UNKNOWN";
            case 1 -> "BOND_LOSS_REASON_BREDR_AUTH_FAILURE";
            case 2 -> "BOND_LOSS_REASON_BREDR_INCOMING_PAIRING";
            case 3 -> "BOND_LOSS_REASON_LE_ENCRYPT_FAILURE";
            case 4 -> "BOND_LOSS_REASON_LE_INCOMING_PAIRING";
            default -> "reason_" + reason;
        };
    }

    static public String getCharacteristicPropertyString(int property) {
        StringBuilder builder = new StringBuilder();
        if (BluetoothGattCharacteristic.PROPERTY_BROADCAST == (BluetoothGattCharacteristic.PROPERTY_BROADCAST & property)) {
            builder.append("broadcast,");
        }
        if (BluetoothGattCharacteristic.PROPERTY_READ == (BluetoothGattCharacteristic.PROPERTY_READ & property)) {
            builder.append("read,");
        }
        if (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE & property)) {
            builder.append("writeNoResponse,");
        }
        if (BluetoothGattCharacteristic.PROPERTY_WRITE == (BluetoothGattCharacteristic.PROPERTY_WRITE & property)) {
            builder.append("write,");
        }
        if (BluetoothGattCharacteristic.PROPERTY_NOTIFY == (BluetoothGattCharacteristic.PROPERTY_NOTIFY & property)) {
            builder.append("notify,");
        }
        if (BluetoothGattCharacteristic.PROPERTY_INDICATE == (BluetoothGattCharacteristic.PROPERTY_INDICATE & property)) {
            builder.append("indicate,");
        }
        if (builder.length() > 0) {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    static public String getAdapterStateString(int state) {
        return switch (state) {
            case BluetoothAdapter.STATE_OFF -> "STATE_OFF";
            case BluetoothAdapter.STATE_TURNING_ON -> "STATE_TURNING_ON";
            case BluetoothAdapter.STATE_ON -> "STATE_ON";
            case BluetoothAdapter.STATE_TURNING_OFF -> "STATE_TURNING_OFF";
            default -> "state_" + state;
        };
    }

    static {
        // common services
        for (GattService gattService : GattService.values()) {
            mServices.put(gattService.uuid.toString(), gattService.prettyName);
        }

        // source: https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/uuids/service_class.yaml
        mServices.put("00001000-0000-1000-8000-00805f9b34fb", "ServiceDiscoveryServerServiceClassID");
        mServices.put("00001001-0000-1000-8000-00805f9b34fb", "BrowseGroupDescriptorServiceClassID");
        mServices.put("00001101-0000-1000-8000-00805f9b34fb", "SerialPort");
        mServices.put("00001102-0000-1000-8000-00805f9b34fb", "LANAccessUsingPPP");
        mServices.put("00001103-0000-1000-8000-00805f9b34fb", "Dial-Up Networking");
        mServices.put("00001104-0000-1000-8000-00805f9b34fb", "IrMCSync");
        mServices.put("00001105-0000-1000-8000-00805f9b34fb", "OBEXObjectPush");
        mServices.put("00001106-0000-1000-8000-00805f9b34fb", "OBEX File Transfer");
        mServices.put("00001107-0000-1000-8000-00805f9b34fb", "IrMCSyncCommand");
        mServices.put("00001108-0000-1000-8000-00805f9b34fb", "Headset");
        mServices.put("00001109-0000-1000-8000-00805f9b34fb", "CordlessTelephony");
        mServices.put("0000110a-0000-1000-8000-00805f9b34fb", "Audio Source");
        mServices.put("0000110b-0000-1000-8000-00805f9b34fb", "Audio Sink");
        mServices.put("0000110c-0000-1000-8000-00805f9b34fb", "A/V Remote Control Target");
        mServices.put("0000110d-0000-1000-8000-00805f9b34fb", "Advanced Audio Distribution");
        mServices.put("0000110e-0000-1000-8000-00805f9b34fb", "A/V Remote Control");
        mServices.put("0000110f-0000-1000-8000-00805f9b34fb", "A/V Remote Control Controller");
        mServices.put("00001110-0000-1000-8000-00805f9b34fb", "Intercom");
        mServices.put("00001111-0000-1000-8000-00805f9b34fb", "Fax");
        mServices.put("00001112-0000-1000-8000-00805f9b34fb", "Headset Audio Gateway");
        mServices.put("00001113-0000-1000-8000-00805f9b34fb", "WAP");
        mServices.put("00001114-0000-1000-8000-00805f9b34fb", "WAP_CLIENT");
        mServices.put("00001115-0000-1000-8000-00805f9b34fb", "PANU");
        mServices.put("00001116-0000-1000-8000-00805f9b34fb", "NAP");
        mServices.put("00001117-0000-1000-8000-00805f9b34fb", "GN");
        mServices.put("00001118-0000-1000-8000-00805f9b34fb", "DirectPrinting");
        mServices.put("00001119-0000-1000-8000-00805f9b34fb", "ReferencePrinting");
        mServices.put("0000111a-0000-1000-8000-00805f9b34fb", "Imaging");
        mServices.put("0000111b-0000-1000-8000-00805f9b34fb", "Imaging Responder");
        mServices.put("0000111c-0000-1000-8000-00805f9b34fb", "Imaging Automatic Archive");
        mServices.put("0000111d-0000-1000-8000-00805f9b34fb", "Imaging Referenced Objects");
        mServices.put("0000111e-0000-1000-8000-00805f9b34fb", "Hands-Free");
        mServices.put("0000111f-0000-1000-8000-00805f9b34fb", "AG Hands-Free");
        mServices.put("00001120-0000-1000-8000-00805f9b34fb", "DirectPrintingReferencedObjectsService");
        mServices.put("00001121-0000-1000-8000-00805f9b34fb", "ReflectedUI");
        mServices.put("00001122-0000-1000-8000-00805f9b34fb", "BasicPrinting");
        mServices.put("00001123-0000-1000-8000-00805f9b34fb", "PrintingStatus");
        mServices.put("00001124-0000-1000-8000-00805f9b34fb", "HID");
        mServices.put("00001125-0000-1000-8000-00805f9b34fb", "HardcopyCableReplacement");
        mServices.put("00001126-0000-1000-8000-00805f9b34fb", "HCR_Print");
        mServices.put("00001127-0000-1000-8000-00805f9b34fb", "HCR_Scan");
        mServices.put("00001128-0000-1000-8000-00805f9b34fb", "Common_ISDN_Access");
        mServices.put("0000112d-0000-1000-8000-00805f9b34fb", "SIM Access");
        mServices.put("0000112e-0000-1000-8000-00805f9b34fb", "Phonebook Access Client");
        mServices.put("0000112f-0000-1000-8000-00805f9b34fb", "Phonebook Access Server");
        mServices.put("00001130-0000-1000-8000-00805f9b34fb", "Phonebook Access Profile");
        mServices.put("00001131-0000-1000-8000-00805f9b34fb", "Headset - HS");
        mServices.put("00001132-0000-1000-8000-00805f9b34fb", "Message Access Server");
        mServices.put("00001133-0000-1000-8000-00805f9b34fb", "Message Notification Server");
        mServices.put("00001134-0000-1000-8000-00805f9b34fb", "Message Access Profile");
        mServices.put("00001135-0000-1000-8000-00805f9b34fb", "GNSS");
        mServices.put("00001136-0000-1000-8000-00805f9b34fb", "GNSS_Server");
        mServices.put("00001137-0000-1000-8000-00805f9b34fb", "3D Display");
        mServices.put("00001138-0000-1000-8000-00805f9b34fb", "3D Glasses");
        mServices.put("00001139-0000-1000-8000-00805f9b34fb", "3D Synch Profile");
        mServices.put("0000113a-0000-1000-8000-00805f9b34fb", "Multi Profile Specification");
        mServices.put("0000113b-0000-1000-8000-00805f9b34fb", "MPS");
        mServices.put("0000113c-0000-1000-8000-00805f9b34fb", "CTN Access Service");
        mServices.put("0000113d-0000-1000-8000-00805f9b34fb", "CTN Notification Service");
        mServices.put("0000113e-0000-1000-8000-00805f9b34fb", "Calendar Tasks and Notes Profile");
        mServices.put("00001200-0000-1000-8000-00805f9b34fb", "PnPInformation");
        mServices.put("00001201-0000-1000-8000-00805f9b34fb", "Generic Networking");
        mServices.put("00001202-0000-1000-8000-00805f9b34fb", "GenericFileTransfer");
        mServices.put("00001203-0000-1000-8000-00805f9b34fb", "Generic Audio");
        mServices.put("00001204-0000-1000-8000-00805f9b34fb", "GenericTelephony");
        mServices.put("00001205-0000-1000-8000-00805f9b34fb", "UPNP_Service");
        mServices.put("00001206-0000-1000-8000-00805f9b34fb", "UPNP_IP_Service");
        mServices.put("00001300-0000-1000-8000-00805f9b34fb", "ESDP_UPNP_IP_PAN");
        mServices.put("00001301-0000-1000-8000-00805f9b34fb", "ESDP_UPNP_IP_LAP");
        mServices.put("00001302-0000-1000-8000-00805f9b34fb", "ESDP_UPNP_L2CAP");
        mServices.put("00001303-0000-1000-8000-00805f9b34fb", "Video Source");
        mServices.put("00001304-0000-1000-8000-00805f9b34fb", "Video Sink");
        mServices.put("00001305-0000-1000-8000-00805f9b34fb", "Video Distribution");
        mServices.put("00001400-0000-1000-8000-00805f9b34fb", "HDP");
        mServices.put("00001401-0000-1000-8000-00805f9b34fb", "HDP Source");
        mServices.put("00001402-0000-1000-8000-00805f9b34fb", "HDP Sink");

        // custom services
        mServices.put("0000fdab-0000-1000-8000-00805f9b34fb", "(Propr: Xiaomi Proximity Unlock Service)");
        mServices.put("0000fe95-0000-1000-8000-00805f9b34fb", "(Propr: Xiaomi Wear Service)");
        mServices.put("0000fee0-0000-3512-2118-0009af100700", "(Propr: Xiaomi MiLi Service)");
        mServices.put("00001530-0000-3512-2118-0009af100700", "(Propr: Xiaomi Weight Service)");
        mServices.put("0000fee0-0000-1000-8000-00805f9b34fb", "(Propr: Xiaomi Huami Service)");
        mServices.put("14701820-620a-3973-7c78-9cfff0876abd", "(Propr: HPLUS Service)");
        mServices.put("16186f00-0000-1000-8000-00807f9b34fb", "(Propr: Xiaomi Wear Service - Mi Watch Lite/Redmi Watch)");
        mServices.put("16187f00-0000-1000-8000-00807f9b34fb", "(Propr: Xiaomi Wear Service - Mi Smart Watch 4C/Redmi Band)");
        mServices.put("1314f000-1000-9000-7000-301291e21220", "(Propr: Xiaomi Wear Service - Mi Watch/Mi Watch Color/Mi Watch Color Sport)");
        mServices.put("7495fe00-a7f3-424b-92dd-4a006a3aef56", "(Propr: Xiaomi Wear Service - Mi Watch CN)");
        mServices.put("0000fff0-0000-1000-8000-00805f9b34fb", "(Propr: Custom Service)");
        mServices.put("02f00000-0000-0000-0000-00000000ffe0", "(Propr: Nothing CMF Data");
        mServices.put("02f00000-0000-0000-0000-00000000fe00", "(Propr: Nothing CMF Firmware");
        mServices.put("77d4e67c-2fe2-2334-0d35-9ccd078f529c", "(Propr: Nothing CMF Shell");
        mServices.put("000055ff-0000-1000-8000-00805f9b34fb", "(Propr: GloryFit Command");
        mServices.put("000056ff-0000-1000-8000-00805f9b34fb", "(Propr: GloryFit Data");
        mServices.put("af9df7a1-e595-11e3-96b4-0002a5d5c51b", "(Propr: OneTouch");
        mServices.put("9b012401-bc30-ce9a-e111-0f67e491abde", "(Propr: Garmin GFDI V0)");
        mServices.put("6217ff4b-fb31-1140-ad5a-a45545d7ecf3", "(Propr: Polar Settings)");
        mServices.put("6a4e2401-667b-11e3-949a-0800200c9a66", "(Propr: Garmin GFDI V1)");
        mServices.put("6a4e2800-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML)");
        mServices.put("6a4e8022-667b-11e3-949a-0800200c9a66", "(Propr: Garmin Watch Apps)");
        mServices.put("86f61000-f706-58a0-95b2-1fb9261e4dc7", "(Propr: Ultrahuman State)");
        mServices.put("86f65000-f706-58a0-95b2-1fb9261e4dc7", "(Propr: Ultrahuman Request)");
        mServices.put("86f66000-f706-58a0-95b2-1fb9261e4dc7", "(Propr: Ultrahuman Data)");
        mServices.put("8d53dc1d-1db7-4cd3-868b-8a527460aa84", "(Propr: SMP - Simple Management Protocol)");
        mServices.put("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e", "(Propr: NUS - Nordic UART Service)");
        mServices.put("de5bf728-d711-4e47-af26-65e3012a5dc7", "(Propr: Yawell Serial)");
        mServices.put("0000ae00-0000-1000-8000-00805f9b34fb", "(Propr: Sinilink-APP)");
        mServices.put("0000ff10-0000-1000-8000-00805f9b34fb", "(Propr: 8BitDo)");
        mServices.put("65970000-4bda-4c1e-af4b-551c4cf74769", "(Propr: Victron)");

        // common characteristics
        for (GattCharacteristic characteristic : GattCharacteristic.values()) {
            mCharacteristics.put(characteristic.uuid.toString(), characteristic.prettyName);
        }

        // custom characteristics
        mCharacteristics.put("14702856-620a-3973-7c78-9cfff0876abd", "(Propr: HPLUS Control)");
        mCharacteristics.put("14702853-620a-3973-7c78-9cfff0876abd", "(Propr: HPLUS Measurements)");
        mCharacteristics.put("df334c80-e6a7-d082-274d-78fc66f85e16", "(Propr: Garmin GFDI V0 TX)");
        mCharacteristics.put("4acbcd28-7425-868e-f447-915c8f00d0cb", "(Propr: Garmin GFDI V0 RX)");
        mCharacteristics.put("6a4e4c80-667b-11e3-949a-0800200c9a66", "(Propr: Garmin GFDI V1 TX)");
        mCharacteristics.put("6a4ecd28-667b-11e3-949a-0800200c9a66", "(Propr: Garmin GFDI V1 RX)");
        mCharacteristics.put("6a4e2810-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML 0 RX)");
        mCharacteristics.put("6a4e2820-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML 0 TX)");
        mCharacteristics.put("6a4e2811-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML 1 RX)");
        mCharacteristics.put("6a4e2821-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML 1 TX)");
        mCharacteristics.put("6a4e2812-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML 2 RX)");
        mCharacteristics.put("6a4e2822-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML 2 TX)");
        mCharacteristics.put("6a4e2813-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML 3 RX)");
        mCharacteristics.put("6a4e2823-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML 3 TX)");
        mCharacteristics.put("6a4e2814-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML 4 RX)");
        mCharacteristics.put("6a4e2824-667b-11e3-949a-0800200c9a66", "(Propr: Garmin ML 4 TX)");
        mCharacteristics.put("00000051-0000-1000-8000-00805f9b34fb", "(Propr: Xiaomi V1 Command Read)");
        mCharacteristics.put("00000052-0000-1000-8000-00805f9b34fb", "(Propr: Xiaomi V1 Command Write)");
        mCharacteristics.put("00000053-0000-1000-8000-00805f9b34fb", "(Propr: Xiaomi V1 Activity Data)");
        mCharacteristics.put("00000055-0000-1000-8000-00805f9b34fb", "(Propr: Xiaomi V1 Data Upload)");
        mCharacteristics.put("16186f01-0000-1000-8000-00807f9b34fb", "(Propr: Xiaomi V1 Command Read)");
        mCharacteristics.put("16186f02-0000-1000-8000-00807f9b34fb", "(Propr: Xiaomi V1 Command Write)");
        mCharacteristics.put("16186f03-0000-1000-8000-00807f9b34fb", "(Propr: Xiaomi V1 Activity Data)");
        mCharacteristics.put("16186f04-0000-1000-8000-00807f9b34fb", "(Propr: Xiaomi V1 Data Upload)");
        mCharacteristics.put("16187f02-0000-1000-8000-00807f9b34fb", "(Propr: Xiaomi V1 Command Read)");
        mCharacteristics.put("16187f01-0000-1000-8000-00807f9b34fb", "(Propr: Xiaomi V1 Command Write)");
        mCharacteristics.put("16187f03-0000-1000-8000-00807f9b34fb", "(Propr: Xiaomi V1 Activity Data)");
        mCharacteristics.put("16187f04-0000-1000-8000-00807f9b34fb", "(Propr: Xiaomi V1 Data Upload)");
        mCharacteristics.put("1314f005-1000-9000-7000-301291e21220", "(Propr: Xiaomi V1 Command Read)");
        mCharacteristics.put("1314f001-1000-9000-7000-301291e21220", "(Propr: Xiaomi V1 Command Write)");
        mCharacteristics.put("1314f002-1000-9000-7000-301291e21220", "(Propr: Xiaomi V1 Activity Data)");
        mCharacteristics.put("1314f007-1000-9000-7000-301291e21220", "(Propr: Xiaomi V1 Data Upload)");
        mCharacteristics.put("74950002-a7f3-424b-92dd-4a006a3aef56", "(Propr: Xiaomi V1 Command Read)");
        mCharacteristics.put("74950001-a7f3-424b-92dd-4a006a3aef56", "(Propr: Xiaomi V1 Command Write)");
        mCharacteristics.put("74950003-a7f3-424b-92dd-4a006a3aef56", "(Propr: Xiaomi V1 Activity Data)");
        mCharacteristics.put("0000005e-0000-1000-8000-00805f9b34fb", "(Propr: Xiaomi V2 RX)");
        mCharacteristics.put("0000005f-0000-1000-8000-00805f9b34fb", "(Propr: Xiaomi V2 TX)");
        mCharacteristics.put("0000fff1-0000-1000-8000-00805f9b34fb", "(Propr: Nothing CMF Command Read");
        mCharacteristics.put("0000fff2-0000-1000-8000-00805f9b34fb", "(Propr: Nothing CMF Command Write");
        mCharacteristics.put("02f00000-0000-0000-0000-00000000ffe1", "(Propr: Nothing CMF Data Write");
        mCharacteristics.put("02f00000-0000-0000-0000-00000000ffe2", "(Propr: Nothing CMF Data Read");
        mCharacteristics.put("77d4ff01-2fe2-2334-0d35-9ccd078f529c", "(Propr: Nothing CMF Shell Write");
        mCharacteristics.put("77d4ff02-2fe2-2334-0d35-9ccd078f529c", "(Propr: Nothing CMF Shell Read");
        mCharacteristics.put("02f00000-0000-0000-0000-00000000ff01", "(Propr: Nothing CMF Firmware Write");
        mCharacteristics.put("02f00000-0000-0000-0000-00000000ff02", "(Propr: Nothing CMF Firmware Read");
        mCharacteristics.put("000033f1-0000-1000-8000-00805f9b34fb", "(Propr: GloryFit Command Write");
        mCharacteristics.put("000033f2-0000-1000-8000-00805f9b34fb", "(Propr: GloryFit Command Read");
        mCharacteristics.put("000034f1-0000-1000-8000-00805f9b34fb", "(Propr: GloryFit Data Write");
        mCharacteristics.put("000034f2-0000-1000-8000-00805f9b34fb", "(Propr: GloryFit Data Read");
        mCharacteristics.put("af9df7a2-e595-11e3-96b4-0002a5d5c51b", "(Propr: OneTouch Write");
        mCharacteristics.put("af9df7a3-e595-11e3-96b4-0002a5d5c51b", "(Propr: OneTouch Read");
        mCharacteristics.put("00010203-0405-0607-0809-0a0b0c0d2b12", "(Propr: Telink OTA Write)");
        mCharacteristics.put("ebe0ccb7-7a0a-4b0c-8a1a-6ff2997da3a6", "(Propr: Lywsd TIME)");
        mCharacteristics.put("ebe0ccc4-7a0a-4b0c-8a1a-6ff2997da3a6", "(Propr: Lywsd BATTERY)");
        mCharacteristics.put("ebe0ccbe-7a0a-4b0c-8a1a-6ff2997da3a6", "(Propr: Lywsd TEMPERATURE_UNIT)");
        mCharacteristics.put("ebe0ccd8-7a0a-4b0c-8a1a-6ff2997da3a6", "(Propr: Lywsd CONN_INTERVAL)");
        mCharacteristics.put("ebe0ccbc-7a0a-4b0c-8a1a-6ff2997da3a6", "(Propr: Lywsd HISTORY)");
        mCharacteristics.put("ebe0ccc1-7a0a-4b0c-8a1a-6ff2997da3a6", "(Propr: Lywsd LIVE_DATA)");
        mCharacteristics.put("ebe0ccba-7a0a-4b0c-8a1a-6ff2997da3a6", "(Propr: Lywsd HISTORY_LAST_ID)");
        mCharacteristics.put("00000001-0000-3512-2118-0009af100700", "(Propr: Huami Raw Sensor Control)");
        mCharacteristics.put("00000002-0000-3512-2118-0009af100700", "(Propr: Huami Raw Sensor Data)");
        mCharacteristics.put("00000003-0000-3512-2118-0009af100700", "(Propr: Huami Configuration)");
        mCharacteristics.put("00000004-0000-3512-2118-0009af100700", "(Propr: Huami Activity Control)");
        mCharacteristics.put("00000005-0000-3512-2118-0009af100700", "(Propr: Huami Activity Data)");
        mCharacteristics.put("00000006-0000-3512-2118-0009af100700", "(Propr: Huami Battery Info)");
        mCharacteristics.put("00000007-0000-3512-2118-0009af100700", "(Propr: Huami Realtime Steps)");
        mCharacteristics.put("00000008-0000-3512-2118-0009af100700", "(Propr: Huami User Settings)");
        mCharacteristics.put("00000009-0000-3512-2118-0009af100700", "(Propr: Huami Auth)");
        mCharacteristics.put("0000000f-0000-3512-2118-0009af100700", "(Propr: Huami Workout)");
        mCharacteristics.put("00000010-0000-3512-2118-0009af100700", "(Propr: Huami Device Event)");
        mCharacteristics.put("00000012-0000-3512-2118-0009af100700", "(Propr: Huami Audio Control)");
        mCharacteristics.put("00000013-0000-3512-2118-0009af100700", "(Propr: Huami Audio Data)");
        mCharacteristics.put("00000016-0000-3512-2118-0009af100700", "(Propr: Huami 2021 Chunked Write)");
        mCharacteristics.put("00000017-0000-3512-2118-0009af100700", "(Propr: Huami 2021 Chunked Read)");
        mCharacteristics.put("00000020-0000-3512-2118-0009af100700", "(Propr: Huami Chunked Transfer)");
        mCharacteristics.put("00000023-0000-3512-2118-0009af100700", "(Propr: Zepp OS File Transfer V3 Send)");
        mCharacteristics.put("00000024-0000-3512-2118-0009af100700", "(Propr: Zepp OS File Transfer V3 Receive)");
        mCharacteristics.put("00001531-0000-3512-2118-0009af100700", "(Propr: Huami Firmware Control)");
        mCharacteristics.put("00001532-0000-3512-2118-0009af100700", "(Propr: Huami Firmware Data)");
        mCharacteristics.put("86f61001-f706-58a0-95b2-1fb9261e4dc7", "(Propr: Ultrahuman State)");
        mCharacteristics.put("86f65001-f706-58a0-95b2-1fb9261e4dc7", "(Propr: Ultrahuman Command)");
        mCharacteristics.put("86f65002-f706-58a0-95b2-1fb9261e4dc7", "(Propr: Ultrahuman Response)");
        mCharacteristics.put("86f66001-f706-58a0-95b2-1fb9261e4dc7", "(Propr: Ultrahuman Data)");
        mCharacteristics.put("da2e7828-fbce-4e01-ae9e-261174997c48", "(Propr: SMP - Simple Management Protocol)");
        mCharacteristics.put("6217ff4d-91bb-91d0-7e2a-7cd3bda8a1f3", "(Propr: Polar Settings)");
        mCharacteristics.put("6e400002-b5a3-f393-e0a9-e50e24dcca9e", "(Propr: Nordic UART TX)");
        mCharacteristics.put("6e400003-b5a3-f393-e0a9-e50e24dcca9e", "(Propr: Nordic UART RX)");
        mCharacteristics.put("de5bf729-d711-4e47-af26-65e3012a5dc7", "(Propr: Yawell Notify)");
        mCharacteristics.put("de5bf72a-d711-4e47-af26-65e3012a5dc7", "(Propr: Yawell Write)");
        mCharacteristics.put("0000ae04-0000-1000-8000-00805f9b34fb", "(Propr: Sinilink-APP RX)");
        mCharacteristics.put("0000ae10-0000-1000-8000-00805f9b34fb", "(Propr: Sinilink-APP TX)");
        mCharacteristics.put("0000ff13-0000-1000-8000-00805f9b34fb", "(Propr: 8BitDo Controller)");
        mCharacteristics.put("6597ffff-4bda-4c1e-af4b-551c4cf74769", "(Propr: Victron Keep-alive)");
        mCharacteristics.put("6597eeff-4bda-4c1e-af4b-551c4cf74769", "(Propr: Victron Consumed Ah)");
        mCharacteristics.put("6597ed8e-4bda-4c1e-af4b-551c4cf74769", "(Propr: Victron Power)");
        mCharacteristics.put("6597ed8d-4bda-4c1e-af4b-551c4cf74769", "(Propr: Victron Voltage)");
        mCharacteristics.put("6597ed8c-4bda-4c1e-af4b-551c4cf74769", "(Propr: Victron Current)");
        mCharacteristics.put("65970fff-4bda-4c1e-af4b-551c4cf74769", "(Propr: Victron Charge)");

        mValueFormats.put(52, "32bit float");
        mValueFormats.put(50, "16bit float");
        mValueFormats.put(34, "16bit signed int");
        mValueFormats.put(36, "32bit signed int");
        mValueFormats.put(33, "8bit signed int");
        mValueFormats.put(18, "16bit unsigned int");
        mValueFormats.put(20, "32bit unsigned int");
        mValueFormats.put(17, "8bit unsigned int");

        // lets add also couple appearance string description
        // https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.gap.appearance.xml
        mAppearance.put(833, "Heart Rate Sensor: Belt");
        mAppearance.put(832, "Generic Heart Rate Sensor");
        mAppearance.put(0, "Unknown");
        mAppearance.put(64, "Generic Phone");
        mAppearance.put(1157, "Cycling: Speed and Cadence Sensor");
        mAppearance.put(1152, "General Cycling");
        mAppearance.put(1153, "Cycling Computer");
        mAppearance.put(1154, "Cycling: Speed Sensor");
        mAppearance.put(1155, "Cycling: Cadence Sensor");
        mAppearance.put(1156, "Cycling: Speed and Cadence Sensor");
        mAppearance.put(1157, "Cycling: Power Sensor");

        mHeartRateSensorLocation.put(0, "Other");
        mHeartRateSensorLocation.put(1, "Chest");
        mHeartRateSensorLocation.put(2, "Wrist");
        mHeartRateSensorLocation.put(3, "Finger");
        mHeartRateSensorLocation.put(4, "Hand");
        mHeartRateSensorLocation.put(5, "Ear Lobe");
        mHeartRateSensorLocation.put(6, "Foot");
    }
}
