package nodomain.freeyourgadget.gadgetbridge.util;

import static androidx.core.app.ActivityCompat.startIntentSenderForResult;
import static nodomain.freeyourgadget.gadgetbridge.util.GB.toast;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanFilter;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.BluetoothLeDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.companion.DeviceFilter;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.R;

public final class CompanionDeviceUtil {
    private static final Logger LOG = LoggerFactory.getLogger(CompanionDeviceUtil.class);

    private CompanionDeviceUtil() {
    }

    /**
     * Returns the MAC addresses, in upper case, of all the devices associated with the CompanionDeviceManager.
     */
    public static List<String> getCompanionAssociations(final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Collections.emptyList();
        }
        final CompanionDeviceManager manager = getCompanionDeviceManager(context);
        if (manager == null) {
            return Collections.emptyList();
        }
        final List<String> addresses = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            for (final AssociationInfo association : manager.getMyAssociations()) {
                final MacAddress macAddress = association.getDeviceMacAddress();
                if (macAddress != null) {
                    addresses.add(macAddress.toString().toUpperCase(Locale.ROOT));
                }
            }
        } else {
            //noinspection deprecation
            for (final String address : manager.getAssociations()) {
                addresses.add(address.toUpperCase(Locale.ROOT));
            }
        }
        return addresses;
    }

    public static boolean isCompanionAssociated(final Context context, final String mac) {
        return getCompanionAssociations(context).contains(mac.toUpperCase(Locale.ROOT));
    }

    /**
     * @param useLe find the device by a BLE scan. Otherwise, find it by classic discovery or in the bonded devices.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    public static void associateCompanion(final Activity activity,
                                          final String mac,
                                          final boolean useLe,
                                          final int requestCode,
                                          @Nullable final Runnable onAssociated,
                                          @Nullable final Consumer<CharSequence> onFailure) {
        final CompanionDeviceManager manager = getCompanionDeviceManager(activity);
        if (manager == null) {
            return;
        }

        final DeviceFilter<?> deviceFilter;
        if (useLe) {
            deviceFilter = new BluetoothLeDeviceFilter.Builder()
                .setScanFilter(new ScanFilter.Builder().setDeviceAddress(mac).build())
                .build();
        } else {
            deviceFilter = new BluetoothDeviceFilter.Builder()
                .setAddress(mac)
                .build();
        }

        final AssociationRequest pairingRequest = new AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(true)
            .build();

        LOG.debug("Starting association request for {}", mac);
        manager.associate(pairingRequest, new CompanionDeviceManager.Callback() {
            @Override
            public void onFailure(final CharSequence error) {
                LOG.warn("Association with {} failed: {}", mac, error);
                if (onFailure != null) {
                    onFailure.accept(error);
                } else {
                    toast(activity, activity.getString(R.string.discovery_bonding_error, error), Toast.LENGTH_SHORT, GB.ERROR);
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onDeviceFound(@NonNull final IntentSender chooserLauncher) {
                try {
                    startIntentSenderForResult(activity, chooserLauncher, requestCode, null, 0, 0, 0, null);
                } catch (final IntentSender.SendIntentException e) {
                    LOG.error("Failed to start the companion device chooser", e);
                    if (onFailure != null) {
                        onFailure.accept(e.getMessage());
                    }
                }
            }

            @Override
            public void onAssociationCreated(@NonNull final AssociationInfo associationInfo) {
                LOG.debug("Association created for {}", mac);
                if (onAssociated != null) {
                    onAssociated.run();
                }
            }
        }, null);
    }

    public static boolean StartObservingAll(Context context) {
        return StartObserving(context, null);
    }

    public static boolean StartObserving(Context context, String mac) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            LOG.info("StartObserving - API {} < {} is too old",
                Build.VERSION.SDK_INT, Build.VERSION_CODES.S);
            return false;
        }

        if (mac != null && !BluetoothAdapter.checkBluetoothAddress(mac)) {
            LOG.warn("StartObserving - mac '{}' is invalid", mac);
            return false;
        }

        final CompanionDeviceManager manager = getCompanionDeviceManager(context);
        if (manager == null) {
            LOG.warn("StartObserving - CompanionDeviceManager is null");
            return false;
        }

        boolean success = false;
        final List<String> addresses;
        if (mac == null) {
            addresses = getCompanionAssociations(context);
            LOG.debug("StartObserving - {} associations", addresses.size());
        } else {
            addresses = Collections.singletonList(mac);
        }

        for (final String address : addresses) {
            try {
                LOG.debug("StartObserving - {}", address);
                manager.startObservingDevicePresence(address);
                success = true;
            } catch (Exception e) {
                LOG.warn("StartObserving - exception", e);
            }
        }
        return success;
    }


    public static boolean StopObservingAll(Context context) {
        return StopObserving(context, null);
    }

    public static boolean StopObserving(final Context context, final String mac) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            LOG.info("StopObserving - API {} < {} is too old",
                Build.VERSION.SDK_INT, Build.VERSION_CODES.S);
            return false;
        }

        if (mac != null && !BluetoothAdapter.checkBluetoothAddress(mac)) {
            LOG.warn("StopObserving - mac '{}' is invalid", mac);
            return false;
        }

        final CompanionDeviceManager manager = getCompanionDeviceManager(context);
        if (manager == null) {
            LOG.warn("StopObserving - CompanionDeviceManager is null");
            return false;
        }

        boolean success = false;
        final List<String> addresses;
        if (mac == null) {
            addresses = getCompanionAssociations(context);
            LOG.debug("StopObserving - {} associations", addresses.size());
        } else {
            addresses = Collections.singletonList(mac);
        }

        for (final String address : addresses) {
            try {
                LOG.debug("StopObserving - {}", address);
                manager.stopObservingDevicePresence(address);
                success = true;
            } catch (Exception e) {
                LOG.warn("StopObserving - exception {}", e.getMessage());
            }
        }
        return success;
    }

    public static boolean Disassociate(Context context, String mac) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            LOG.info("Disassociate - API {} < {} is too old",
                Build.VERSION.SDK_INT, Build.VERSION_CODES.O);
            return false;
        }

        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            LOG.warn("Disassociate - mac '{}' is invalid", mac);
            return false;
        }

        final CompanionDeviceManager manager = getCompanionDeviceManager(context);
        if (manager == null) {
            LOG.warn("Disassociate - CompanionDeviceManager is null");
        } else {
            try {
                StopObserving(context, mac);
                LOG.debug("Disassociate - {}", mac);
                manager.disassociate(mac);
                return true;
            } catch (Exception e) {
                LOG.info("Disassociate - exception {}", e.getMessage());
            }
        }

        return false;
    }

    @Nullable
    public static CompanionDeviceManager getCompanionDeviceManager(Context context) {
        if (context == null) {
            LOG.error("getCompanionDeviceManager - context is null");
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            LOG.debug("getCompanionDeviceManager - API {} < {} is too old",
                Build.VERSION.SDK_INT, Build.VERSION_CODES.O);
        } else {
            final CompanionDeviceManager manager = (CompanionDeviceManager) context.getSystemService(Context.COMPANION_DEVICE_SERVICE);
            if (manager != null) {
                return manager;
            }

            LOG.warn("getCompanionDeviceManager - CompanionDeviceManager is null");
            final PackageManager pm = context.getPackageManager();
            if (pm == null) {
                LOG.warn("getCompanionDeviceManager - PackageManager is null");
            } else {
                LOG.warn("getCompanionDeviceManager - FEATURE_COMPANION_DEVICE_SETUP is available: {}",
                    pm.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));
            }
        }
        return null;
    }
}
