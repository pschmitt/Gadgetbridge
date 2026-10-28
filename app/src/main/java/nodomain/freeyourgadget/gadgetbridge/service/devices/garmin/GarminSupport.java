/*  Copyright (C) 2024-2026 Daniele Gobbetti, José Rebelo, kuhy, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin;

import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.JsonElement;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.config.DynamicAppConfig;
import nodomain.freeyourgadget.gadgetbridge.activities.files.TextFileViewerActivity;
import nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards.LoyaltyCard;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.repository.WorkoutTemplateRepository;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventAppInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.devices.PendingFileProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.SleepAsAndroidFeature;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminFitFileInstallHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminGpxRouteInstallHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminPreferences;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminPrgInstallItem;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminCapability;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.externalevents.gps.GBLocationService;
import nodomain.freeyourgadget.gadgetbridge.externalevents.sleepasandroid.SleepAsAndroidAction;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.install.InstallItem;
import nodomain.freeyourgadget.gadgetbridge.install.InstallSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceApp;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.HydrationContainer;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes;
import nodomain.freeyourgadget.gadgetbridge.model.weather.Weather;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiCore;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTypes;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDeviceStatus;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTransfer;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiFileAccess;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiFindMyWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiInstalledApps;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiRealtimeSettings;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmartProto.Smart;
import nodomain.freeyourgadget.gadgetbridge.service.SleepAsAndroidSender;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.ICommunicator;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.v1.CommunicatorV1;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.GarminWatchAppCommunicator;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.v2.CommunicatorV2;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.watchapps.GarminLoyaltyCardsApp;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.watchapps.GarminNavigationApp;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.watchapps.GarminWatchApp;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.CapabilitiesDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.FileDownloadedDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.IncomingFitDefinitionDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.MaxPacketSizeDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.NotificationSubscriptionDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.ProtobufResponseEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.SupportedFileTypesDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.WeatherRequestDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitAsyncProcessor;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.DataTransferHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitImporter;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitLocalMessageBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.GpxRouteFileConverter;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RecordData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.AlarmLabel;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.AlarmMode;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.Tone;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.WeatherReport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitAlarmSettings;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitDeviceSettings;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitFileId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitWeatherConditions;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.workouts.GarminWorkoutFitEncoder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.CurrentTimeRequestMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.DeviceInformationMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.DownloadRequestMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.GFDIMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.MusicControlEntityUpdateMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.SetDeviceSettingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.SetFileFlagsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.SupportedFileTypesMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.SystemEventMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.status.GenericStatusMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.status.NotificationSubscriptionStatusMessage;
import nodomain.freeyourgadget.gadgetbridge.util.ArrayUtils;
import nodomain.freeyourgadget.gadgetbridge.util.CompressionUtils;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.MediaManager;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.UuidUtil;
import nodomain.freeyourgadget.gadgetbridge.util.notifications.GBProgressNotification;

import static nodomain.freeyourgadget.gadgetbridge.GBApplication.ACTION_APP_IS_IN_BACKGROUND;
import static nodomain.freeyourgadget.gadgetbridge.GBApplication.ACTION_APP_IS_IN_FOREGROUND;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_ALLOW_HIGH_MTU;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_HYDRATION_SEND_TODAY;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_HYDRATION_UNIT;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_KEEP_ACTIVITY_DATA_ON_DEVICE;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_NEW_SYNC_PROTOCOL;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SEND_APP_NOTIFICATIONS;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_TIME_SYNC;
import static nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminPreferences.PREF_GARMIN_EXPLORE_SYNC;
import static nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminPreferences.PREF_GARMIN_LEGACY_SYNC_FLUSH;
import static nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminPreferences.PREF_GARMIN_MLR;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivityUser.PREF_USER_GOAL_HYDRATION_ML;
import static nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiFileAccess.TransportProtocol.MULTILINK_TRANSPORT_PIPE;


public class GarminSupport extends AbstractBTLESingleDeviceSupport implements ICommunicator.Callback {
    private static final Logger LOG = LoggerFactory.getLogger(GarminSupport.class);
    private final ProtocolBufferHandler protocolBufferHandler;
    private final NotificationsHandler notificationsHandler;
    private final FileTransferHandler fileTransferHandler;
    private final Queue<FileToDownload> filesToDownload;
    private FileToDownload currentlyDownloading;
    private final List<MessageHandler> messageHandlers;
    private final List<FileType> supportedFileTypeList = new ArrayList<>();
    private ICommunicator communicator;
    private GarminWatchAppCommunicator watchAppCommunicator;
    private BluetoothGattService watchAppService;
    private final GarminNavigationApp navigationApp = new GarminNavigationApp(this);
    private final GarminLoyaltyCardsApp loyaltyCardsApp = new GarminLoyaltyCardsApp(this);
    private final Map<UUID, GarminWatchApp> watchApps = Map.of(
        navigationApp.getUuid(), navigationApp,
        loyaltyCardsApp.getUuid(), loyaltyCardsApp
    );
    private GarminRfcommListener rfcommListener;
    private String classicAddress;
    private MediaManager mediaManager;
    private boolean mFirstConnect = false;
    private boolean isBusyFetching;

    private int maxPacketSize = 400;

    private SleepAsAndroidSender sleepAsAndroidSender;
    private ScheduledExecutorService saaAlarmScheduler;

    private GBProgressNotification transferNotification;

    final Map<UUID, GdiInstalledApps.GetInstalledAppsResponse.InstalledApp> installedApps = new HashMap<>();

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            switch (action) {
                case ACTION_APP_IS_IN_FOREGROUND:
                    sendOutgoingMessage("set foreground", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.HOST_DID_ENTER_FOREGROUND, 0));
                    break;
                case ACTION_APP_IS_IN_BACKGROUND:
                    sendOutgoingMessage("set background", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.HOST_DID_ENTER_BACKGROUND, 0));
                    break;
            }
        }
    };

    public GarminSupport() {
        super(LOG);
        addSupportedService(CommunicatorV1.UUID_SERVICE_GARMIN_GFDI_V0);
        addSupportedService(CommunicatorV1.UUID_SERVICE_GARMIN_GFDI_V1);
        addSupportedService(CommunicatorV2.UUID_SERVICE_GARMIN_ML_GFDI);
        addSupportedService(GarminWatchAppCommunicator.UUID_SERVICE_GARMIN_WATCH_APPS);
        protocolBufferHandler = new ProtocolBufferHandler(this);
        fileTransferHandler = new FileTransferHandler(this);
        filesToDownload = new LinkedList<>();
        messageHandlers = new CopyOnWriteArrayList<>();
        notificationsHandler = new NotificationsHandler();
        messageHandlers.add(fileTransferHandler);
        messageHandlers.add(protocolBufferHandler);
        messageHandlers.add(notificationsHandler);

        IntentFilter filterLocal = new IntentFilter();
        filterLocal.addAction(ACTION_APP_IS_IN_FOREGROUND);
        filterLocal.addAction(ACTION_APP_IS_IN_BACKGROUND);
        LocalBroadcastManager.getInstance(GBApplication.getContext()).registerReceiver(broadcastReceiver, filterLocal);
    }

    @Override
    public void setContext(final GBDevice gbDevice, final BluetoothAdapter btAdapter, final Context context) {
        super.setContext(gbDevice, btAdapter, context);
        this.mediaManager = new MediaManager(context);
        this.protocolBufferHandler.setContext(gbDevice, btAdapter, context);
        this.transferNotification = new GBProgressNotification(context, GB.NOTIFICATION_CHANNEL_ID_TRANSFER);
        if (gbDevice.getDeviceCoordinator().supportsSleepAsAndroid(gbDevice)) {
            this.sleepAsAndroidSender = new SleepAsAndroidSender(gbDevice);
        }
    }

    @Override
    public SleepAsAndroidSender getSleepAsAndroidSender() {
        return sleepAsAndroidSender;
    }

    @Override
    public void dispose() {
        synchronized (ConnectionMonitor) {
            LOG.info("Garmin dispose()");
            resetFileSyncState();
            if (sleepAsAndroidSender != null) {
                sleepAsAndroidSender.stopTracking();
            }
            stopSleepAsAndroidSchedulers();
            if (communicator != null) {
                communicator.dispose();
            }
            if (watchAppCommunicator != null) {
                watchAppCommunicator.dispose();
            }
            if (rfcommListener != null) {
                rfcommListener.close();
                rfcommListener = null;
            }
            // BT disconnect: tell ExploreSync to drop in-flight buffers.
            if (protocolBufferHandler != null && protocolBufferHandler.getExploreSyncHandler() != null) {
                protocolBufferHandler.getExploreSyncHandler().onDisconnected();
            }
            GBLocationService.stop(getContext(), getDevice());
            try {
                LocalBroadcastManager.getInstance(GBApplication.getContext()).unregisterReceiver(broadcastReceiver);
            } catch (final Exception e) {
                LOG.error("Failed to unregister broadcast receiver", e);
            }
            super.dispose();
        }
    }

    public void onFileDownloadProgress(final int progress) {
        transferNotification.setChunkProgress(progress);
    }

    public void addFileToDownloadList(FileTransferHandler.DirectoryEntry directoryEntry) {
        if (newSyncProtocol() && directoryEntry.getFiletype() != FileType.FILETYPE.DEVICE_XML) {
            if (directoryEntry.getFiletype() == FileType.FILETYPE.DIRECTORY) {
                LOG.debug("Got directory entry, syncing with new protocol");
                sendProtobufRequest("request file list",
                        Smart.newBuilder().setFileAccessService(
                                protocolBufferHandler.getFileSyncServiceHandler().requestFileList()
                        ).build());
                return;
            }
            LOG.warn("Ignoring directory entry {} in new sync protocol", directoryEntry.getFileName());
            return;
        }
        filesToDownload.add(new FileToDownload(directoryEntry));
        // Only grow the visible total once a transfer is active; before start()
        // the initial size is computed from the queue sum in processDownloadQueue.
        if (isBusyFetching && directoryEntry.getFiletype() != FileType.FILETYPE.DIRECTORY) {
            transferNotification.incrementTotalSize(directoryEntry.getFileSize());
        }
    }

    public void addFileToDownloadList(GdiFileAccess.ItemReference file) {
        filesToDownload.add(new FileToDownload(file));
        if (isBusyFetching && file.hasDataSize()) {
            transferNotification.incrementTotalSize(file.getDataSize());
        }
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }

    @Override
    public GarminPrefs getDevicePrefs() {
        return new GarminPrefs(GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()), gbDevice);
    }

    @NonNull
    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        builder.setDeviceState(GBDevice.State.INITIALIZING);

        resetFileSyncState();

        if (getDevicePrefs().getBoolean(GarminPreferences.PREF_GARMIN_RFCOMM_FILE_TRANSFER, false) && rfcommListener == null) {
            rfcommListener = new GarminRfcommListener(this, null);
            rfcommListener.start();
        }

        if (getDevicePrefs().getBoolean(PREF_ALLOW_HIGH_MTU, true)) {
            builder.requestMtu(515);
        }

        final CommunicatorV2 communicatorV2 = new CommunicatorV2(this);
        if (communicatorV2.initializeDevice(builder)) {
            communicator = communicatorV2;
        } else {
            // V2 did not manage to initialize, attempt V1
            final CommunicatorV1 communicatorV1 = new CommunicatorV1(this);
            if (!communicatorV1.initializeDevice(builder)) {
                // Neither V1 nor V2 worked, not a Garmin device?
                LOG.warn("Failed to find a known Garmin service");
                builder.setDeviceState(GBDevice.State.NOT_CONNECTED);
                return builder;
            }

            communicator = communicatorV1;
        }

        watchAppCommunicator = new GarminWatchAppCommunicator(this);
        if (!watchAppCommunicator.initializeDevice(builder, watchAppService)) {
            watchAppCommunicator = null;
        }

        return builder;
    }

    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt) {
        watchAppService = gatt.getService(GarminWatchAppCommunicator.UUID_SERVICE_GARMIN_WATCH_APPS);
        super.onServicesDiscovered(gatt);
    }

    @Override
    public void onMtuChanged(final BluetoothGatt gatt, final int mtu, final int status) {
        super.onMtuChanged(gatt, mtu, status);
        if (status != BluetoothGatt.GATT_SUCCESS) {
            return;
        }

        if (mtu < 23) {
            LOG.warn("Ignoring mtu of {}, too low", mtu);
            return;
        }
        if (!getDevicePrefs().getBoolean(PREF_ALLOW_HIGH_MTU, true)) {
            LOG.warn("Ignoring mtu change to {} - high mtu is disabled", mtu);
            return;
        }

        communicator.onMtuChanged(mtu);
        if (watchAppCommunicator != null) {
            watchAppCommunicator.onMtuChanged(mtu);
        }
    }

    @Override
    public boolean onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] value) {
        final UUID characteristicUUID = characteristic.getUuid();
        if (super.onCharacteristicChanged(gatt, characteristic, value)) {
            LOG.debug("Change of characteristic {} handled by parent", characteristicUUID);
            return true;
        }

        if (watchAppCommunicator != null && watchAppCommunicator.onCharacteristicChanged(characteristic, value)) {
            return true;
        }

        return communicator.onCharacteristicChanged(gatt, characteristic, value);
    }

    @Override
    public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (communicator != null) {
            communicator.onConnectionStateChange(gatt, status, newState);
        }
    }

    /**
     * Messages from the RFCOMM link are handled on the listener thread, which routes all replies
     * back over RFCOMM.
     */
    private boolean isRfcommThread() {
        return rfcommListener != null && Thread.currentThread() == rfcommListener;
    }

    @Override
    public void onMessage(final byte[] message) {
        if (rfcommListener != null && !Thread.holdsLock(this)) {
            // BLE and RFCOMM messages arrive on different threads and share the file sync state
            synchronized (this) {
                onMessage(message);
            }
            return;
        }
        if (null == message) {
            return; //message is not complete yet TODO check before calling
        }
//        LOG.debug("COBS decoded MESSAGE: {}", GB.hexdump(message));

        GFDIMessage parsedMessage = GFDIMessage.parseIncoming(message);

        if (null == parsedMessage) {
            LOG.error("GFDIMessage is null - this should never happen");
            return; //message cannot be handled
        }

        if (parsedMessage instanceof CurrentTimeRequestMessage && !getDevicePrefs().getBoolean(PREF_TIME_SYNC, true)) {
            LOG.warn("Replying UNSUPPORTED to current time request - time sync is disabled");
            sendOutgoingMessage("send status", new GenericStatusMessage(parsedMessage.getGarminMessage(), GFDIMessage.Status.UNSUPPORTED));
            return;
        }

        LOG.debug("INCOMING message {}/{}: {}", parsedMessage.getClass().getSimpleName(), parsedMessage.getGarminMessage(), GB.lazyHexdump(message));
        /*
        the handler elaborates the followup message but might change the status message since it does
        check the integrity of the incoming message payload. Hence we let the handlers elaborate the
        incoming message, then we send the status message of the incoming message, then the response
        and finally we send the followup.
         */

        GFDIMessage followup = null;
        for (MessageHandler han : messageHandlers) {
            followup = han.handle(parsedMessage);
            if (followup != null) {
                break;
            }
        }

        sendAck("send status", parsedMessage); //send status message

        if (parsedMessage instanceof DeviceInformationMessage deviceInformation && !isRfcommThread()) {
            classicAddress = deviceInformation.getClassicAddress();
            if (rfcommListener != null) {
                rfcommListener.requestConnection(classicAddress);
            }
        }

        sendOutgoingMessage("send reply", parsedMessage); //send reply if any

        sendOutgoingMessage("send followup", followup); //send followup message if any

        final List<GBDeviceEvent> events = parsedMessage.getGBDeviceEvent();
        for (final GBDeviceEvent event : events) {
            if (isRfcommThread()) {
                // The RFCOMM link has its own handshake. The BLE link owns initialization and
                // its packet size, so do not let the second handshake touch them.
                if (event instanceof CapabilitiesDeviceEvent) {
                    completeRfcommInitialization();
                    continue;
                }
                if (event instanceof MaxPacketSizeDeviceEvent) {
                    continue;
                }
            }
            evaluateGBDeviceEvent(event);
        }

        processDownloadQueue();

    }

    protected String getNotificationAttachmentPath(int notificationId) {
        return notificationsHandler.getNotificationAttachmentPath(notificationId);
    }

    protected Bitmap getNotificationAttachmentBitmap(int notificationId) {
        final String picturePath = getNotificationAttachmentPath(notificationId);
        final Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
        if (bitmap == null) {
            LOG.warn("Failed to load bitmap for {} from {}", notificationId, picturePath);
        }
        return bitmap;
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        sendOutgoingMessage("send call", notificationsHandler.onSetCallState(callSpec));
    }

    @Override
    public void evaluateGBDeviceEvent(GBDeviceEvent deviceEvent) {
        if (deviceEvent instanceof WeatherRequestDeviceEvent) {
            WeatherSpec weather = Weather.getWeatherSpec();
            if (weather != null) {
                sendWeatherConditions(weather);
            }
        } else if (deviceEvent instanceof CapabilitiesDeviceEvent) {
            final Set<GarminCapability> capabilities = ((CapabilitiesDeviceEvent) deviceEvent).capabilities;
            completeInitialization();
            if (capabilities.contains(GarminCapability.REALTIME_SETTINGS)) {
                final String language = Locale.getDefault().getLanguage();
                final String country = Locale.getDefault().getCountry();
                final String localeString = language + "_" + country.toUpperCase();
                sendProtobufRequest("init realtime settings", Smart.newBuilder()
                        .setRealtimeSettingsService(
                            GdiRealtimeSettings.RealtimeSettingsService.newBuilder()
                                        .setInitRequest(
                                            GdiRealtimeSettings.InitRequest.newBuilder()
                                                        .setLanguage(localeString.length() == 5 ? localeString : "en_US")
                                                        .setRegion("us") // FIXME choose region
                                        )
                        )
                        .build());
            }
        } else if (deviceEvent instanceof ProtobufResponseEvent protobufResponseEvent) {
            sendOutgoingMessage(
                    "protobuf response event for " + protobufResponseEvent.messageId,
                    protocolBufferHandler.prepareProtobufResponse(
                            protobufResponseEvent.payload,
                            protobufResponseEvent.messageId
                    )
            );
        } else if (deviceEvent instanceof NotificationSubscriptionDeviceEvent) {
            final boolean enable = ((NotificationSubscriptionDeviceEvent) deviceEvent).enable;
            notificationsHandler.setEnabled(enable);

            final NotificationSubscriptionStatusMessage.NotificationStatus finalStatus;
            if (getDevicePrefs().getBoolean(PREF_SEND_APP_NOTIFICATIONS, true)) {
                finalStatus = NotificationSubscriptionStatusMessage.NotificationStatus.ENABLED;
            } else {
                finalStatus = NotificationSubscriptionStatusMessage.NotificationStatus.DISABLED;
            }

            LOG.info("NOTIFICATIONS ARE NOW enabled={}, status={}", enable, finalStatus);

            sendOutgoingMessage("toggle notification subscription", new NotificationSubscriptionStatusMessage(
                    GFDIMessage.Status.ACK,
                    finalStatus,
                    enable,
                    0
            ));
        } else if (deviceEvent instanceof MaxPacketSizeDeviceEvent maxPacketSizeDeviceEvent) {
            LOG.debug("Got new max packet size of {}", maxPacketSizeDeviceEvent.getMaxPacketSize());
            maxPacketSize = maxPacketSizeDeviceEvent.getMaxPacketSize();
            fileTransferHandler.setMaxPacketSize(maxPacketSizeDeviceEvent.getMaxPacketSize());
            protocolBufferHandler.setMaxPacketSize(maxPacketSizeDeviceEvent.getMaxPacketSize());
        } else if (deviceEvent instanceof SupportedFileTypesDeviceEvent) {
            this.supportedFileTypeList.clear();
            this.supportedFileTypeList.addAll(((SupportedFileTypesDeviceEvent) deviceEvent).getSupportedFileTypes());
        } else if (deviceEvent instanceof FileDownloadedDeviceEvent fileDownloadedDeviceEvent) {
            final FileTransferHandler.DirectoryEntry entry = fileDownloadedDeviceEvent.directoryEntry;
            if (!fileDownloadedDeviceEvent.success) {
                LOG.warn("FILE DOWNLOAD FAILED");
                // Continue to the next one
                currentlyDownloading = null;
                return;
            }

            if (entry != null) {
                final String filename = entry.getFileName();
                LOG.debug("FILE DOWNLOAD COMPLETE {}", filename);
                transferNotification.incrementTotalProgress(entry.getFileSize());

                if (entry.getFiletype().isFitFile()) {
                    try (DBHandler handler = GBApplication.acquireDB()) {
                        final DaoSession session = handler.getDaoSession();

                        final PendingFileProvider pendingFileProvider = new PendingFileProvider(gbDevice, session);
                        pendingFileProvider.addPendingFile(fileDownloadedDeviceEvent.localPath);
                    } catch (final Exception e) {
                        GB.toast(getContext(), "Error saving pending file", Toast.LENGTH_LONG, GB.ERROR, e);
                    }
                }

                if (entry.getFiletype() != FileType.FILETYPE.DEVICE_XML && !getKeepActivityDataOnDevice()) { // delete file from watch upon successful download
                    sendOutgoingMessage("archive file " + entry.getFileIndex(), new SetFileFlagsMessage(entry.getFileIndex(), SetFileFlagsMessage.FileFlags.ARCHIVE));
                }
            } else if (fileDownloadedDeviceEvent.localPath != null) {
                LOG.debug("ZIP DOWNLOAD COMPLETE {}", fileDownloadedDeviceEvent.localPath);
                final File zipFile = new File(fileDownloadedDeviceEvent.localPath);
                if (gbDevice.isBusy()) {
                    transferNotification.incrementTotalProgress(zipFile.length());
                }

                try (DBHandler handler = GBApplication.acquireDB()) {
                    final DaoSession session = handler.getDaoSession();

                    final PendingFileProvider pendingFileProvider = new PendingFileProvider(gbDevice, session);
                    pendingFileProvider.addPendingFile(fileDownloadedDeviceEvent.localPath);
                } catch (final Exception e) {
                    GB.toast(getContext(), "Error saving pending file", Toast.LENGTH_LONG, GB.ERROR, e);
                }
            } else {
                LOG.error("Got invalid FileDownloadedDeviceEvent");
            }

            currentlyDownloading = null;
        } else if (deviceEvent instanceof IncomingFitDefinitionDeviceEvent) {
            final FitLocalMessageHandler fitLocalMessageHandler = new FitLocalMessageHandler(this, ((IncomingFitDefinitionDeviceEvent) deviceEvent).getRecordDefinitions(), maxPacketSize);
            messageHandlers.add(fitLocalMessageHandler);
        } else {
            super.evaluateGBDeviceEvent(deviceEvent);
        }
    }

    /**
     * @noinspection BooleanMethodIsAlwaysInverted
     */
    private boolean getKeepActivityDataOnDevice() {
        return getDevicePrefs().getBoolean(PREF_KEEP_ACTIVITY_DATA_ON_DEVICE, false);
    }

    @Override
    public void onFetchRecordedData(final int dataTypes) {
        if (dataTypes == RecordedDataTypes.TYPE_DEBUGLOGS) {
            addFileToDownloadList(fileTransferHandler.getDeviceXmlDirectoryEntry());
            processDownloadQueue();
            return;
        }

        if (dataTypes == RecordedDataTypes.TYPE_SYNC) {
            requestBatteryUpdate();
        }

        if (newSyncProtocol() && !getDevicePrefs().getBoolean(PREF_GARMIN_LEGACY_SYNC_FLUSH, true)) {
            // #6700 - Some firmwares will freeze on the legacy sync request
            LOG.warn("Legacy sync flush is disabled - requesting file list directly");
            sendProtobufRequest("directly request file list",
                Smart.newBuilder().setFileAccessService(
                    protocolBufferHandler.getFileSyncServiceHandler().requestFileList()
                ).build());
            return;
        }

        if (this.supportedFileTypeList.isEmpty() && !newSyncProtocol()) {
            LOG.warn("No known supported file types");
            return;
        }

        // FIXME respect dataTypes?

        if (rfcommListener != null && rfcommListener.isConnected()) {
            // This device only lists files over RFCOMM. When the link is down, the BLE fetch
            // below returns no FIT files and onDirectoryWithoutFitFiles() requests the link.
            final GFDIMessage download = fileTransferHandler.initiateDownload();
            if (download != null) {
                rfcommListener.sendMessage(download.getOutgoingMessage());
            }
            return;
        }

        // We initiate download here even in the new sync protocol so that the watch "flushes" the data
        // otherwise we might get incomplete monitor files
        sendOutgoingMessage("fetch recorded data", fileTransferHandler.initiateDownload());

        //TODO: ask the watch to initiate the sync? Something like:
        //        sendOutgoingMessage("set sync ready", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.SYNC_READY, 0));
        //        sendOutgoingMessage("set foreground", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.HOST_DID_ENTER_FOREGROUND, 0));
    }

    public boolean newSyncProtocol() {
        return getDevicePrefs().getBoolean(PREF_NEW_SYNC_PROTOCOL, false);
    }

    public boolean mlrEnabled() {
        return getDevicePrefs().getBoolean(PREF_GARMIN_MLR, true);
    }

    @Override
    public void onNotification(final NotificationSpec notificationSpec) {
        sendOutgoingMessage("send notification " + notificationSpec.getId(), notificationsHandler.onNotification(notificationSpec));
    }

    @Override
    public void onDeleteNotification(int id) {
        sendOutgoingMessage("delete notification " + id, notificationsHandler.onDeleteNotification(id));
    }

    @Override
    public void onAppInfoReq() {
        onAppInfoReq(GdiInstalledApps.AppType.ALL);
    }

    private void onAppInfoReq(final GdiInstalledApps.AppType appType) {
        sendProtobufRequest("request apps of type " + appType,
            Smart.newBuilder().setConnectIqInstalledAppsService(
                GdiInstalledApps.ConnectIQInstalledAppsService.newBuilder().setGetInstalledAppsRequest(
                    GdiInstalledApps.GetInstalledAppsRequest.newBuilder()
                        .setAppType(appType)
                )
            ).build());
    }

    @Override
    public void onSetNavigationInfo(final NavigationInfoSpec navigationInfoSpec) {
        if (!getCoordinator().supportsNavigation(getDevice())) {
            return;
        }
        if (!navigationApp.onSetNavigationInfo(navigationInfoSpec)) {
            // Fallback to normal notifications
            super.onSetNavigationInfo(navigationInfoSpec);
        }
    }

    @Override
    public void onSetLoyaltyCards(final ArrayList<LoyaltyCard> cards) {
        loyaltyCardsApp.onSetLoyaltyCards(cards);
    }

    public void onWatchAppMessage(final UUID app, final JsonElement message) {
        final GarminWatchApp watchApp = watchApps.get(app);
        if (watchApp == null) {
            LOG.debug("Message from unknown watch app {}", app);
            return;
        }
        watchApp.onMessage(message);
    }

    @Nullable
    public GarminWatchAppCommunicator getWatchAppCommunicator() {
        return watchAppCommunicator;
    }

    @Override
    public void onAppStart(final UUID uuid, final boolean start) {
        if (!getCoordinator().experimentalSettingEnabled(getDevice(), "garmin_experimental_app_management")) {
            LOG.warn("Experimental app management not enabled");
        }
    }

    @Override
    public void onAppDelete(final UUID uuid) {
        if (!getCoordinator().experimentalSettingEnabled(getDevice(), "garmin_experimental_app_management")) {
            LOG.warn("Experimental app management not enabled");
            return;
        }

        final GdiInstalledApps.GetInstalledAppsResponse.InstalledApp app = installedApps.get(uuid);

        if (app == null) {
            LOG.warn("Unknown app {}", uuid);
            return;
        }

        sendProtobufRequest("delete app",
                Smart.newBuilder().setConnectIqInstalledAppsService(
                        GdiInstalledApps.ConnectIQInstalledAppsService.newBuilder().setDeleteAppRequest(
                                GdiInstalledApps.DeleteAppRequest.newBuilder()
                                        .setStoreAppId(app.getStoreAppId())
                                        .setAppType(app.getAppType())
                        )
                ).build());
    }

    @Override
    public void onAppConfigRequest(final UUID uuid) {
        sendProtobufRequest("app config request " + uuid,
                protocolBufferHandler.getAppConfigHandler().onAppConfigRequest(uuid));
    }

    @Override
    public void onAppConfigSet(final UUID uuid, final ArrayList<DynamicAppConfig> configs) {
        sendProtobufRequest("app config set " + uuid,
                protocolBufferHandler.getAppConfigHandler().onAppConfigSet(uuid, configs));
    }

    public void onAppListReceived(final List<GdiInstalledApps.GetInstalledAppsResponse.InstalledApp> apps) {
        installedApps.clear();

        final List<GBDeviceApp> gbApps = new ArrayList<>(apps.size());

        for (final GdiInstalledApps.GetInstalledAppsResponse.InstalledApp installedApp : apps) {
            final boolean isSystemApp = !installedApp.hasFilename();
            final GBDeviceApp.Type type = switch (installedApp.getAppType()) {
                case WATCH_FACE -> isSystemApp ? GBDeviceApp.Type.WATCHFACE_SYSTEM : GBDeviceApp.Type.WATCHFACE;
                case DATA_FIELD, ACTIVITY -> isSystemApp ? GBDeviceApp.Type.UNKNOWN /* otherwise too many appear */ : GBDeviceApp.Type.APP_ACTIVITYTRACKER;
                case AUDIO_CONTENT_PROVIDER -> isSystemApp ? GBDeviceApp.Type.APP_SYSTEM : GBDeviceApp.Type.APP_GENERIC;
                case WATCH_APP -> isSystemApp ? GBDeviceApp.Type.APP_SYSTEM : GBDeviceApp.Type.APP_GENERIC;
                default -> GBDeviceApp.Type.UNKNOWN;
            };

            final UUID uuid = UuidUtil.fromBytes(installedApp.getStoreAppId().toByteArray());
            installedApps.put(uuid, installedApp);
            final GBDeviceApp gbDeviceApp = new GBDeviceApp(
                    uuid,
                    installedApp.getName(),
                    "",
                    installedApp.getVersion() != 0 ? String.valueOf(installedApp.getVersion()) : null,
                    type
            );
            gbDeviceApp.setConfigurable(type == GBDeviceApp.Type.APP_GENERIC);
            gbDeviceApp.setCanBeStarted(false); // we do not support starting
            gbApps.add(gbDeviceApp);
        }

        gbApps.sort(Comparator.comparing(GBDeviceApp::getName));

        final Set<String> installedAppsPref = installedApps.keySet().stream()
                .map(UUID::toString)
                .collect(Collectors.toSet());
        evaluateGBDeviceEvent(new GBDeviceEventUpdatePreferences(GarminPreferences.PREF_GARMIN_INSTALLED_APPS, installedAppsPref));

        final GBDeviceEventAppInfo appInfoCmd = new GBDeviceEventAppInfo();
        appInfoCmd.apps = gbApps.toArray(new GBDeviceApp[0]);
        evaluateGBDeviceEvent(appInfoCmd);
    }

    @Override
    public void onSendWeather() { //todo: find the closest one relative to the requested lat/long
        WeatherSpec weatherSpec = Weather.getWeatherSpec();
        if (weatherSpec == null) {
            LOG.warn("No weather found in singleton");
            return;
        }
        sendWeatherConditions(weatherSpec);
    }

    /**
     * Wrap and send a watch-bound Smart RPC.
     */
    void sendProtobufRequest(final String taskName, final Smart payload) {
        sendOutgoingMessage(taskName, protocolBufferHandler.prepareProtobufRequest(payload));
    }

    void sendOutgoingMessage(final String taskName, final GFDIMessage message) {
        if (message == null)
            return;
        byte[] out = message.getOutgoingMessage();
        if (out != null && LOG.isDebugEnabled()) {
            LOG.debug("OUTGOING message {}: {}", message.getClass().getSimpleName(), GB.lazyHexdump(out));
        }
        if (isRfcommThread()) {
            rfcommListener.sendMessage(out);
            return;
        }
        if (communicator == null) {
            LOG.error("outgoing communicator is null");
            return;
        }
        communicator.sendMessage(taskName, out);
    }

    private void sendAck(final String taskName, final GFDIMessage message) {
        if (message == null)
            return;
        byte[] ack = message.getAckBytestream();
        if (ack != null && LOG.isDebugEnabled()) {
            LOG.debug("OUTGOING ACK {}: {}", message.getClass().getSimpleName(), GB.lazyHexdump(ack));
        }
        if (isRfcommThread()) {
            rfcommListener.sendMessage(ack);
            return;
        }
        if (communicator == null) {
            LOG.error("ack communicator is null");
            return;
        }
        communicator.sendMessage(taskName, ack);
    }

    private void sendWeatherConditions(WeatherSpec weather) {
        if (!getCoordinator().supports(getDevice(), GarminCapability.WEATHER_CONDITIONS)) {
            // Device does not support sending weather as fit
            LOG.debug("Not sending weather to device, it does not support WEATHER_CONDITIONS. Weather will be sent via HTTP once the watch requests it.");
            return;
        }

        final FitLocalMessageBuilder weatherLocalMessage = encodeWeather(weather);

        final FitLocalMessageHandler weatherHandler = new FitLocalMessageHandler(this, weatherLocalMessage, maxPacketSize);
        messageHandlers.add(weatherHandler);

        sendOutgoingMessage("send " + weatherLocalMessage.getDefinitions().size() + " weather definitions", weatherHandler.init());
    }

    protected void unregisterHandler(final MessageHandler registered) {
        messageHandlers.remove(registered);
        LOG.debug("{} handler removed, handlers left: {}", registered.getClass().getSimpleName(), messageHandlers.stream().count());
    }

    public static FitLocalMessageBuilder encodeWeather(final WeatherSpec weather) {

        final FitLocalMessageBuilder weatherLocalMessage = new FitLocalMessageBuilder();

        final FitWeatherConditions.Builder today = new FitWeatherConditions.Builder();
        today.setWeatherReport(WeatherReport.current);
        today.setTimestamp((long) weather.getTimestamp());
        today.setObservedAtTime((long) weather.getTimestamp());
        today.weatherTemperature(weather.getCurrentTemp());
        today.weatherLowTemperature(weather.getTodayMinTemp());
        today.weatherHighTemperature(weather.getTodayMaxTemp());
        today.weatherCondition(weather.getCurrentConditionCode());
        today.weatherWindDirection(weather.getWindDirection());
        today.weatherPrecipitationProbability(weather.getPrecipProbability());
        today.weatherWindSpeed(weather.getWindSpeed());
        today.weatherTemperatureFeelsLike(weather.getFeelsLikeTemp());
        today.weatherRelativeHumidity(weather.getCurrentHumidity());
        today.setObservedLocationLat((double) weather.getLatitude());
        today.setObservedLocationLong((double) weather.getLongitude());
        today.weatherAirQuality(weather.getAirQuality());
        today.weatherDewPoint(weather.getDewPoint());
        today.setLocation(weather.getLocation());
        weatherLocalMessage.addRecordData(today.build(weatherLocalMessage.getNextAvailableLocalMessageType()));

        final int hourlyMessageType = weatherLocalMessage.getNextAvailableLocalMessageType();
        for (int hour = 0; hour <= 11; hour++) {
            if (hour < weather.getHourly().size()) {
                WeatherSpec.Hourly hourly = weather.getHourly().get(hour);
                final FitWeatherConditions.Builder weatherHourlyForecast = new FitWeatherConditions.Builder();
                weatherHourlyForecast.setWeatherReport(WeatherReport.hourly_forecast);
                weatherHourlyForecast.setTimestamp((long) hourly.getTimestamp());
                weatherHourlyForecast.weatherTemperature(hourly.getTemp());
                weatherHourlyForecast.weatherCondition(hourly.getConditionCode());
                weatherHourlyForecast.weatherWindDirection(hourly.getWindDirection());
                weatherHourlyForecast.weatherWindSpeed(hourly.getWindSpeed());
                weatherHourlyForecast.weatherPrecipitationProbability(hourly.getPrecipProbability());
                weatherHourlyForecast.weatherTemperatureFeelsLike(hourly.getTemp()); //TODO: switch to actual feels like field once Hourly contains this information
                weatherHourlyForecast.weatherRelativeHumidity(hourly.getHumidity());
                weatherHourlyForecast.weatherDewPoint(hourly.getDewPoint());
                weatherHourlyForecast.weatherUvIndex(hourly.getUvIndex());
                weatherHourlyForecast.weatherAirQuality(null); // null to ensure the definition is added TODO: add once Hourly contains this information
                weatherHourlyForecast.weatherAtmosphericPressure(hourly.getPressure());
                // TODO: FIT encoding unclear for hourly.getCloudCover()
                weatherLocalMessage.addRecordData(weatherHourlyForecast.build(hourlyMessageType));
            }
        }

        // Always ensure that todayDailyForecast or weatherDailyForecast have the same fields set,
        // since they share the same message type in the definition message
        final int dailyMessageType = weatherLocalMessage.getNextAvailableLocalMessageType();

        final FitWeatherConditions.Builder todayDailyForecast = new FitWeatherConditions.Builder();
        todayDailyForecast.setWeatherReport(WeatherReport.daily_forecast);
        todayDailyForecast.setTimestamp((long) weather.getTimestamp());
        todayDailyForecast.weatherLowTemperature(weather.getTodayMinTemp());
        todayDailyForecast.weatherHighTemperature(weather.getTodayMaxTemp());
        todayDailyForecast.weatherCondition(weather.getCurrentConditionCode());
        todayDailyForecast.weatherPrecipitationProbability(weather.getPrecipProbability());
        todayDailyForecast.weatherDayOfWeek(weather.getTimestamp());
        todayDailyForecast.weatherAirQuality(weather.getAirQuality());
        todayDailyForecast.weatherRelativeHumidity(weather.getCurrentHumidity());
        todayDailyForecast.weatherWindSpeed(weather.getWindSpeed());
        todayDailyForecast.weatherWindDirection(weather.getWindDirection());
        todayDailyForecast.weatherUvIndex(weather.getUvIndex());
        todayDailyForecast.weatherAtmosphericPressure(weather.getPressure());
        // TODO: FIT encoding unclear for weather.getCloudCover()
        weatherLocalMessage.addRecordData(todayDailyForecast.build(dailyMessageType));

        for (int day = 0; day < 4; day++) {
            if (day < weather.getForecasts().size()) {
                WeatherSpec.Daily daily = weather.getForecasts().get(day);
                int ts = weather.getTimestamp() + (day + 1) * 24 * 60 * 60;
                final FitWeatherConditions.Builder weatherDailyForecast = new FitWeatherConditions.Builder();
                weatherDailyForecast.setWeatherReport(WeatherReport.daily_forecast);
                weatherDailyForecast.setTimestamp((long) weather.getTimestamp());
                weatherDailyForecast.weatherLowTemperature(daily.getMinTemp());
                weatherDailyForecast.weatherHighTemperature(daily.getMaxTemp());
                weatherDailyForecast.weatherCondition(daily.getConditionCode());
                weatherDailyForecast.weatherPrecipitationProbability(daily.getPrecipProbability());
                weatherDailyForecast.weatherDayOfWeek(ts);
                weatherDailyForecast.weatherAirQuality(daily.getAirQuality());
                weatherDailyForecast.weatherRelativeHumidity(daily.getHumidity());
                weatherDailyForecast.weatherWindSpeed(daily.getWindSpeed());
                weatherDailyForecast.weatherWindDirection(daily.getWindDirection());
                weatherDailyForecast.weatherUvIndex(daily.getUvIndex());
                weatherDailyForecast.weatherAtmosphericPressure(daily.getPressure());
                // TODO: FIT encoding unclear for daily.getCloudCover()
                weatherLocalMessage.addRecordData(weatherDailyForecast.build(dailyMessageType));
            }
        }

        return weatherLocalMessage;
    }

    /**
     * Some devices (e.g. Edge 520 Plus) list no files over BLE and only serve them over a Classic
     * Bluetooth RFCOMM link, which they open after an SDP query. Remembered per device, so the
     * listener is up on the next connect, when the device opens the link after saving an activity.
     * Only reached with "New sync protocol" disabled, those devices reject the protobuf file list.
     */
    public void onDirectoryWithoutFitFiles() {
        if (classicAddress == null) {
            return;
        }
        if (rfcommListener != null) {
            rfcommListener.requestConnection(classicAddress);
            return;
        }
        LOG.info("Directory lists no FIT files, falling back to RFCOMM file transfer");
        GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).edit()
                .putBoolean(GarminPreferences.PREF_GARMIN_RFCOMM_FILE_TRANSFER, true)
                .apply();
        rfcommListener = new GarminRfcommListener(this, classicAddress);
        rfcommListener.start();
    }

    private void completeRfcommInitialization() {
        // Mirrors what the official app sends on this link, after which the device requests a sync
        LOG.info("RFCOMM link initialized");
        sendOutgoingMessage("rfcomm: request supported file types", new SupportedFileTypesMessage());
        sendDeviceSettings();
        sendOutgoingMessage("rfcomm: set sync ready", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.SYNC_READY, 0));
    }

    private void completeInitialization() {
        if (gbDevice.getState() == GBDevice.State.INITIALIZED) {
            LOG.error("completeInitialization() was called, but the device is already initialized. This should never happen! Please report this to the project.");
            LOG.warn("preventing double initialization");
            return;
        }
        sendOutgoingMessage("request supported file types", new SupportedFileTypesMessage());
        sendDeviceSettings();

        if (GBApplication.getPrefs().syncTime() && getDevicePrefs().getBoolean(PREF_TIME_SYNC, true)) {
            onSetTime();
        }
        //following is needed for vivomove style
        sendOutgoingMessage("set sync ready", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.SYNC_READY, 0));

        requestBatteryUpdate();
        onAppInfoReq(GdiInstalledApps.AppType.WATCH_APP);


        gbDevice.setUpdateState(GBDevice.State.INITIALIZED, getContext());


        if (mFirstConnect) {
            // sendOutgoingMessage("set pair start", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.PAIR_START, 0));
            sendOutgoingMessage("set pair complete", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.PAIR_COMPLETE, 0));
            sendOutgoingMessage("set sync complete", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.SYNC_COMPLETE, 0));
            // sendOutgoingMessage("set tutorial complete", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.TUTORIAL_COMPLETE, 0));
            sendOutgoingMessage("set setup wizard complete", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.SETUP_WIZARD_COMPLETE, 0));
            this.mFirstConnect = false;
        }
    }

    @Override
    public void onSendConfiguration(final String config) {
        if (config.startsWith("protobuf:")) {
            try {
                final Smart smart = Smart.parseFrom(GB.hexStringToByteArray(config.replaceFirst("protobuf:", "")));
                sendProtobufRequest("send config", smart);
            } catch (final Exception e) {
                LOG.error("Failed to send {} as protobuf", config, e);
            }

            return;
        }

        if (isHydrationContainerPref(config)) {
            sendHydrationSettings();
            return;
        }

        switch (config) {
            case PREF_SEND_APP_NOTIFICATIONS:
                NotificationSubscriptionDeviceEvent notificationSubscriptionDeviceEvent = new NotificationSubscriptionDeviceEvent();
                notificationSubscriptionDeviceEvent.enable = true; // actual status is fetched from preferences
                evaluateGBDeviceEvent(notificationSubscriptionDeviceEvent);
                return;
            case PREF_HYDRATION_UNIT:
                sendHydrationSettings();
                return;
            case PREF_USER_GOAL_HYDRATION_ML:
            case PREF_HYDRATION_SEND_TODAY:
                sendHydrationUpdate();
                return;
        }
    }

    private static boolean isHydrationContainerPref(final String config) {
        for (int container = 1; container <= HydrationContainer.COUNT; container++) {
            if (config.equals(HydrationContainer.volumeKey(container)) ||
                    config.equals(HydrationContainer.unitKey(container))) {
                return true;
            }
        }
        return false;
    }

    private void sendHydrationUpdate() {
        if (!getCoordinator().supportsHydration(getDevice())) {
            return;
        }
        try {
            sendProtobufRequest("hydration update", protocolBufferHandler.getPushNotificationHandler().hydrationUpdate());
        } catch (final Exception e) {
            LOG.error("Failed to send hydration update", e);
        }
    }

    private void sendHydrationSettings() {
        if (!getCoordinator().supportsHydration(getDevice())) {
            return;
        }
        sendProtobufRequest("hydration settings", protocolBufferHandler.getPushNotificationHandler().hydrationSettings());
    }

    private void resetFileSyncState() {
        // Clear any in-flight transfer notification; otherwise a disconnect
        // mid-sync leaves the progress notification pinned indefinitely.
        transferNotification.finish();
        isBusyFetching = false;
        currentlyDownloading = null;
        filesToDownload.clear();
    }

    private void processDownloadQueue() {
        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "Processing download queue - {} files to download, currentlyDownloading = {}, isBusyFetching = {}",
                    filesToDownload.size(),
                    currentlyDownloading != null,
                    isBusyFetching
            );
        }

        if (!filesToDownload.isEmpty() && currentlyDownloading == null) {
            LOG.debug("Processing next file of {}", filesToDownload.size());

            // Gate on our own fetch state, not gbDevice.isBusy(): the latter can
            // be set/cleared by unrelated paths and would desync the notification's
            // start/finish pairing.
            if (!isBusyFetching) {
                LOG.debug("Starting download queue");

                isBusyFetching = true;
                transferNotification.start(
                        R.string.busy_task_fetch_activity_data,
                        0,
                        filesToDownload.stream().mapToLong(FileToDownload::getSize).sum(),
                        (progress, total) -> getContext().getString(
                                R.string.busy_task_progress_str,
                                FormatUtils.formatBytes(progress),
                                FormatUtils.formatBytes(total)
                        )
                );
                getDevice().setBusyTask(R.string.busy_task_fetch_activity_data, getContext());
                getDevice().sendDeviceUpdateIntent(getContext());
            }

            while (!filesToDownload.isEmpty()) {
                currentlyDownloading = filesToDownload.remove();
                if (currentlyDownloading.getDirectoryEntry() != null) {
                    FileTransferHandler.DirectoryEntry directoryEntry = currentlyDownloading.getDirectoryEntry();
                    if (alreadyDownloaded(directoryEntry)) {
                        LOG.debug("File: {} already downloaded, not downloading again.", directoryEntry.getFileName());
                        currentlyDownloading = null;
                        if (!getKeepActivityDataOnDevice()) { // delete file from watch if already downloaded
                            sendOutgoingMessage("archive file " + directoryEntry.getFileIndex(), new SetFileFlagsMessage(directoryEntry.getFileIndex(), SetFileFlagsMessage.FileFlags.ARCHIVE));
                        }
                        if (directoryEntry.getFiletype() != FileType.FILETYPE.DIRECTORY) {
                            transferNotification.incrementTotalProgress(directoryEntry.getFileSize());
                        }
                        continue;
                    }

                    final DownloadRequestMessage downloadRequestMessage = fileTransferHandler.downloadDirectoryEntry(directoryEntry);
                    LOG.debug("Will download file: {}", directoryEntry.getOutputPath());
                    if (directoryEntry.getFiletype() != FileType.FILETYPE.DIRECTORY) {
                        transferNotification.setChunkProgress(0);
                    }
                    sendOutgoingMessage("download file " + directoryEntry.getFileIndex(), downloadRequestMessage);
                } else if (currentlyDownloading.getSyncFile() != null) {
                    LOG.debug("Will download file: {}/{}", currentlyDownloading.getSyncFile().getUid().getMostSignificant(), currentlyDownloading.getSyncFile().getUid().getLeastSignificant());

                    sendProtobufRequest("request file",
                            Smart.newBuilder().setFileAccessService(
                                    protocolBufferHandler.getFileSyncServiceHandler().requestFile(currentlyDownloading.getSyncFile())
                            ).build());
                } else {
                    LOG.error("Unexpected FileToDownload");
                }

                return;
            }
        }

        if (filesToDownload.isEmpty() && currentlyDownloading == null && isBusyFetching) {
            LOG.debug("No more files to download, will process pending files");

            final List<File> filesToProcess;
            try (DBHandler handler = GBApplication.acquireDbReadOnly()) {
                final DaoSession session = handler.getDaoSession();

                final PendingFileProvider pendingFileProvider = new PendingFileProvider(gbDevice, session);

                filesToProcess = pendingFileProvider.getAllPendingFiles()
                        .stream()
                        .map(pf -> new File(pf.getPath()))
                        .collect(Collectors.toList());
            } catch (final Exception e) {
                LOG.error("Failed to get pending files", e);
                return;
            }

            // Keep the device marked as busy while we process the files asynchronously, but unset
            // isBusyFetching so we do not start multiple processors
            isBusyFetching = false;

            if (filesToProcess.isEmpty()) {
                LOG.debug("No pending files to process");
                finishFileSync();
                return;
            }

            transferNotification.start(R.string.busy_task_processing_files, 0, filesToProcess.size());

            final FitAsyncProcessor fitAsyncProcessor = new FitAsyncProcessor(getContext(), getDevice());
            fitAsyncProcessor.process(filesToProcess, false, new FitAsyncProcessor.Callback() {
                @Override
                public void onProgress(final int i) {
                    transferNotification.setTotalProgress(i);
                }

                @Override
                public void onFinish() {
                    finishFileSync();
                }
            });
        }
    }

    /**
     * Common tail of both "no more files to download" exits — whether
     * there was nothing queued for parsing, or {@link FitAsyncProcessor}
     * just finished parsing what was queued. Unblocks the device's busy
     * state, signals the new data to the UI, and re-arms ExploreSync's
     * historical catalog walk so any activities recorded since the
     * initial connect get picked up. Watches that don't support the
     * service reject our StartSyncRequest and the handler tears the
     * session down on its own.
     */
    @VisibleForTesting
    void finishFileSync() {
        // FIXME: This should probably only happen after exploresync also finishes
        sendOutgoingMessage("set sync complete", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.SYNC_COMPLETE, 0));

        getDevice().unsetBusyTask();
        GB.signalActivityDataFinish(getDevice());
        transferNotification.finish();
        getDevice().sendDeviceUpdateIntent(getContext());

        if (getCoordinator().supports(getDevice(), GarminCapability.EXPLORE_SYNC)) {
            if (getDevicePrefs().getBoolean(PREF_GARMIN_EXPLORE_SYNC, false)) {
                protocolBufferHandler.getExploreSyncHandler().startSession();
            }
        }
    }

    private void requestBatteryUpdate() {
        sendProtobufRequest("enable battery updates", Smart.newBuilder()
                .setDeviceStatusService(
                        GdiDeviceStatus.DeviceStatusService.newBuilder()
                                .setRemoteDeviceBatteryStatusRequest(
                                        GdiDeviceStatus.RemoteDeviceBatteryStatusRequest.newBuilder()
                                )
                )
                .build());
    }

    private void sendDeviceSettings() {
        final Map<SetDeviceSettingsMessage.GarminDeviceSetting, Object> settings = new LinkedHashMap<>(3);
        settings.put(SetDeviceSettingsMessage.GarminDeviceSetting.AUTO_UPLOAD_ENABLED, true);
        settings.put(SetDeviceSettingsMessage.GarminDeviceSetting.WEATHER_CONDITIONS_ENABLED, true);
        settings.put(SetDeviceSettingsMessage.GarminDeviceSetting.WEATHER_ALERTS_ENABLED, false);
        sendOutgoingMessage("send device settings", new SetDeviceSettingsMessage(settings));
    }

    @Override
    public void onSetTime() {
        sendOutgoingMessage("set time", new SystemEventMessage(SystemEventMessage.GarminSystemEventType.TIME_UPDATED, 0));
    }

    @Override
    public void onFindDevice(boolean start) {
        if (!getCoordinator().supportsFindDevice(gbDevice)){
            LOG.warn("onFindDevice: supportsFindDevice=false");
            return;
        }

        final GdiFindMyWatch.FindMyWatchService.Builder a = GdiFindMyWatch.FindMyWatchService.newBuilder();
        if (start) {
            a.setFindRequest(
                    GdiFindMyWatch.FindMyWatchRequest.newBuilder()
                            .setTimeout(60)
            );
        } else {
            a.setCancelRequest(
                    GdiFindMyWatch.FindMyWatchCancelRequest.newBuilder()
            );
        }
        sendProtobufRequest("find device",
                Smart.newBuilder().setFindMyWatchService(a).build());
    }

    @Override
    public void onSleepAsAndroidAction(final String action, final Bundle extras) {
        if (sleepAsAndroidSender == null) {
            LOG.warn("SaA sender not initialized, dropping {}", action);
            return;
        }
        if (!gbDevice.isInitialized()) {
            LOG.warn("Device not initialized, dropping SaA action {}", action);
            return;
        }
        try {
            sleepAsAndroidSender.validateAction(action);
        } catch (final UnsupportedOperationException e) {
            return;
        }

        switch (action) {
            case SleepAsAndroidAction.CHECK_CONNECTED:
                sleepAsAndroidSender.confirmConnected();
                break;
            case SleepAsAndroidAction.START_TRACKING:
                sleepAsAndroidSender.startTracking(extras);
                toggleSleepAsAndroidRealtimeData(true);
                break;
            case SleepAsAndroidAction.STOP_TRACKING:
                toggleSleepAsAndroidRealtimeData(false);
                sleepAsAndroidSender.stopTracking();
                break;
            case SleepAsAndroidAction.SET_PAUSE: {
                final long pauseTimestamp = extras.getLong("TIMESTAMP");
                final long delay = pauseTimestamp > 0 ? pauseTimestamp - System.currentTimeMillis() : 0;
                sleepAsAndroidSender.pauseTracking(delay);
                break;
            }
            case SleepAsAndroidAction.SET_SUSPENDED: {
                final boolean suspended = extras.getBoolean("SUSPENDED", false);
                sleepAsAndroidSender.pauseTracking(suspended);
                break;
            }
            case SleepAsAndroidAction.SET_BATCH_SIZE:
                sleepAsAndroidSender.setBatchSize(extras.getLong("SIZE", 12L));
                break;
            case SleepAsAndroidAction.HINT:
                LOG.debug("Ignoring SaA hint for repeat = {}", extras.getInt("REPEAT", 1));
                break;
            case SleepAsAndroidAction.SHOW_NOTIFICATION: {
                final NotificationSpec spec = new NotificationSpec();
                spec.setTitle(extras.getString("TITLE"));
                spec.setBody(extras.getString("TEXT"));
                onNotification(spec);
                break;
            }
            case SleepAsAndroidAction.UPDATE_ALARM:
                // Most Garmin devices do not support alarms, so we do not schedule anything on the
                // watch - the alarm is triggered by START_ALARM, using find device to vibrate.
                LOG.debug("Ignoring SaA alarm update for {}", new Date(extras.getLong("TIMESTAMP")));
                break;
            case SleepAsAndroidAction.START_ALARM:
                scheduleSleepAsAndroidAlarmVibration(extras.getInt("DELAY", 60000));
                break;
            case SleepAsAndroidAction.STOP_ALARM:
                cancelSleepAsAndroidAlarmVibration();
                break;
            default:
                LOG.warn("Received unsupported SaA action: {}", action);
                break;
        }
    }

    /**
     * Enables or disables the realtime data streams that Sleep as Android consumes, for the
     * features that are supported and enabled.
     */
    private void toggleSleepAsAndroidRealtimeData(final boolean enable) {
        if (sleepAsAndroidSender.hasFeature(SleepAsAndroidFeature.ACCELEROMETER)
                && sleepAsAndroidSender.isFeatureEnabled(SleepAsAndroidFeature.ACCELEROMETER)) {
            communicator.onEnableRealtimeAccelerometer(enable);
        }
        if (sleepAsAndroidSender.hasFeature(SleepAsAndroidFeature.HEART_RATE)
                && sleepAsAndroidSender.isFeatureEnabled(SleepAsAndroidFeature.HEART_RATE)
                && sleepAsAndroidSender.isHeartRateRequested()) {
            communicator.onEnableRealtimeHeartRateMeasurement(enable);
        }
        if (sleepAsAndroidSender.hasFeature(SleepAsAndroidFeature.SPO2)
                && sleepAsAndroidSender.isFeatureEnabled(SleepAsAndroidFeature.SPO2)
                && sleepAsAndroidSender.isOximetryRequested()) {
            communicator.onEnableRealtimeSpo2(enable);
        }
        if (sleepAsAndroidSender.hasFeature(SleepAsAndroidFeature.RR_INTERVALS)
                && sleepAsAndroidSender.isFeatureEnabled(SleepAsAndroidFeature.RR_INTERVALS)) {
            communicator.onEnableRealtimeRrIntervals(enable);
        }
    }

    private void scheduleSleepAsAndroidAlarmVibration(final int delayMs) {
        cancelSleepAsAndroidAlarmVibration();
        if (delayMs == -1) {
            return;
        }
        saaAlarmScheduler = Executors.newSingleThreadScheduledExecutor();
        saaAlarmScheduler.schedule(
                () -> onFindDevice(true),
                Math.max(0, delayMs),
                TimeUnit.MILLISECONDS
        );
    }

    private void cancelSleepAsAndroidAlarmVibration() {
        stopSleepAsAndroidSchedulers();
        onFindDevice(false);
    }

    private void stopSleepAsAndroidSchedulers() {
        if (saaAlarmScheduler != null) {
            saaAlarmScheduler.shutdownNow();
            saaAlarmScheduler = null;
        }
    }

    @Override
    public void onSetAlarms(final ArrayList<? extends Alarm> alarms) {
        final int alarmSlotCount = getCoordinator().getAlarmSlotCount(getDevice());

        final List<RecordData> dataRecords = new ArrayList<>(1 + alarmSlotCount);

        final int currentTime = (int) (System.currentTimeMillis() / 1000);

        dataRecords.add(new FitFileId.Builder()
                .setType(FileType.FILETYPE.SETTINGS)
                .setManufacturer(1) // garmin
                .setProduct(65534) // connect
                .setTimeCreated((long) currentTime)
                .setSerialNumber(1L)
                .setNumber(1)
                .build());

        final List<Integer> deviceSettingsTimes = new ArrayList<>();
        final List<Integer> deviceSettingsMode = new ArrayList<>();
        final List<Integer> deviceSettingsEnabled = new ArrayList<>();
        final List<Long> deviceSettingsRepeat = new ArrayList<>();

        int numberEnabledAlarms = 0;
        for (Alarm alarm : alarms) {
            if (alarm.getUnused()) {
                continue;
            }

            final Tone soundCode = switch (Alarm.ALARM_SOUND.values()[alarm.getSoundCode()]) {
                case OFF -> Tone.OFF;
                case TONE -> Tone.SOUND;
                case VIBRATION -> Tone.VIBRATION;
                case UNSET, TONE_AND_VIBRATION -> Tone.SOUND_AND_VIBRATION;
            };
            final AlarmLabel label;

            final String alarmTitle = alarm.getTitle();
            if (StringUtils.isBlank(alarmTitle)) {
                label = AlarmLabel.NONE;
            } else {
                AlarmLabel alarmLabel;
                try {
                    alarmLabel = AlarmLabel.valueOf(alarmTitle);
                } catch (final Exception e) {
                    LOG.error("Invalid alarm label {}", alarmTitle, e);
                    alarmLabel = AlarmLabel.NONE;
                }
                label = alarmLabel;
            }

            final long repetitionCode = alarm.getRepetition() != 0 ? alarm.getRepetition() : 128L;

            final FitAlarmSettings.Builder alarmBuilder = new FitAlarmSettings.Builder()
                    .setTime(LocalTime.of(alarm.getHour(), alarm.getMinute()))
                    .setRepeat(repetitionCode)
                    .setEnabled(alarm.getEnabled())
                    .setSound(soundCode)
                    .setBacklight(alarm.getBacklight())
                    .setTimeCreated((long) currentTime)
                    .setSnooze(0)
                    .setLabel(label)
                    .setMessageIndex(numberEnabledAlarms);

            dataRecords.add(alarmBuilder.build());

            deviceSettingsTimes.add(alarm.getHour() * 60 + alarm.getMinute());
            deviceSettingsMode.add(AlarmMode.Custom.num);
            deviceSettingsEnabled.add(alarm.getEnabled() ? 1 : 0);
            deviceSettingsRepeat.add(repetitionCode);

            numberEnabledAlarms++;
        }

        if (numberEnabledAlarms > 0) {
            final FitDeviceSettings.Builder deviceSettingsBuilder = new FitDeviceSettings.Builder()
                    .setAlarmsTime(deviceSettingsTimes.toArray(new Integer[0]))
                    .setAlarmsMode(deviceSettingsMode.toArray(new Integer[0]))
                    .setAlarmsEnabled(deviceSettingsEnabled.toArray(new Integer[0]))
                    .setAlarmsRepeat(deviceSettingsRepeat.toArray(new Long[0]));

            dataRecords.add(deviceSettingsBuilder.build());
        }

        final FitFile fitFile = new FitFile(dataRecords);
        communicator.sendMessage(
                "set alarms",
                fileTransferHandler.initiateUpload(
                        fitFile.getOutgoingMessage(),
                        fitFile.getFileType()
                ).getOutgoingMessage()
        );
    }

    @Override
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {
        sendOutgoingMessage("set canned messages", protocolBufferHandler.setCannedMessages(cannedMessagesSpec));
    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {
        if (!mediaManager.onSetMusicInfo(musicSpec)) {
            return;
        }

        LOG.debug("onSetMusicInfo: {}", musicSpec.toString());

        Map<MusicControlEntityUpdateMessage.MusicEntity, String> attributes = new HashMap<>();

        attributes.put(MusicControlEntityUpdateMessage.TRACK.ARTIST, musicSpec.getArtist());
        attributes.put(MusicControlEntityUpdateMessage.TRACK.ALBUM, musicSpec.getAlbum());
        attributes.put(MusicControlEntityUpdateMessage.TRACK.TITLE, musicSpec.getTrack());
        attributes.put(MusicControlEntityUpdateMessage.TRACK.DURATION, String.valueOf(musicSpec.getDuration()));

        sendOutgoingMessage("set music info", new MusicControlEntityUpdateMessage(attributes));

        // Update the music state spec as well
        final MusicStateSpec bufferMusicStateSpec = mediaManager.getBufferMusicStateSpec();
        if (bufferMusicStateSpec != null) {
            sendMusicState(bufferMusicStateSpec, bufferMusicStateSpec.getPosition());
        }
    }

    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {
        if (!mediaManager.onSetMusicState(stateSpec)) {
            return;
        }

        LOG.debug("onSetMusicState: {}", stateSpec.toString());

        sendMusicState(stateSpec, stateSpec.getPosition());
    }

    private void sendMusicState(final MusicStateSpec stateSpec, final int progress) {
        if (!getCoordinator().supportsMusicInfo(getDevice())) {
            LOG.warn("sendMusicState: supportsMusicInfo=false");
            return;
        }

        final int playing;
        final float playRate;
        if (stateSpec.getState() == MusicStateSpec.STATE_PLAYING) {
            playing = 1;
            playRate = stateSpec.getPlayRate() > 0 ? stateSpec.getPlayRate() / 100f : 1.0f;
        } else {
            playing = 0;
            playRate = 0;
        }
        final Map<MusicControlEntityUpdateMessage.MusicEntity, String> attributes = new HashMap<>();
        attributes.put(
                MusicControlEntityUpdateMessage.PLAYER.PLAYBACK_INFO,
                String.format(Locale.ROOT, "%d,%.1f,%.3f", playing, playRate, (float) progress)
        );
        sendOutgoingMessage("set music state", new MusicControlEntityUpdateMessage(attributes));
    }

    @Override
    public void onSetPhoneVolume(final float volume) {
        final Map<MusicControlEntityUpdateMessage.MusicEntity, String> attributes = new HashMap<>();

        attributes.put(MusicControlEntityUpdateMessage.PLAYER.VOLUME, String.format(Locale.ROOT, "%.2f", volume / 100f));

        sendOutgoingMessage("set phone volume", new MusicControlEntityUpdateMessage(attributes));
    }

    private boolean alreadyDownloaded(final FileTransferHandler.DirectoryEntry entry) {
        // Current filename
        final Optional<File> file = getFile(entry.getOutputPath());
        if (file.isPresent()) {
            if (file.get().length() == 0) {
                LOG.warn("File {} is empty", entry.getOutputPath());
                return false;
            }
            return true;
        }

        // Legacy filename 1, before we had per-type/year folder.
        // Same shape as DirectoryEntry's basename.
        final String legacyName1 = entry.getFileName();
        final Optional<File> legacyFile1 = getFile(legacyName1);
        if (legacyFile1.isPresent()) {
            if (legacyFile1.get().length() == 0) {
                LOG.warn("Legacy file 1 {} is empty", legacyName1);
                return false;
            }
            return true;
        }

        // Legacy filename 2: [TYPE]_[INDEX]_[timestamp].[fit/bin]
        // (timestamp segment swapped vs filename 1). Skipped when the
        // entry has no date — there's no plausible legacy file to
        // match against without a timestamp segment.
        if (entry.getFileDate() != null) {
            final String legacyTimestamp = GarminUtils.FILENAME_TIMESTAMP_FORMAT.format(entry.getFileDate().toInstant());
            final String legacyName2 = entry.getFiletype().name() + "_" +
                    entry.getFileIndex() + "_" +
                    legacyTimestamp +
                    (entry.getFiletype().isFitFile() ? ".fit" : ".bin");
            final Optional<File> legacyFile2 = getFile(legacyName2);
            if (legacyFile2.isPresent()) {
                if (legacyFile2.get().length() == 0) {
                    LOG.warn("Legacy file 2 {} is empty", legacyName2);
                    return false;
                }
                return true;
            }
        }

        return false;
    }

    private Optional<File> getFile(final String fileName) {
        File dir;
        try {
            dir = getWritableExportDirectory();
            File outputFile = new File(dir, fileName);
            if (outputFile.exists())
                return Optional.of(outputFile);
        } catch (IOException e) {
            LOG.error("Failed to get file", e);
        }
        return Optional.empty();
    }

    public File getWritableExportDirectory() throws IOException {
        return getDevice().getDeviceCoordinator().getWritableExportDirectory(getDevice(), true);
    }

    @Override
    public void onSetGpsLocation(final Location location) {
        final GdiCore.LocationUpdatedNotification.Builder locationUpdatedNotification = GdiCore.LocationUpdatedNotification.newBuilder()
                .addLocationData(
                        GarminUtils.toLocationData(location, GdiCore.LocationData.DataType.REALTIME_TRACKING)
                );

        sendProtobufRequest("set gps location",
                Smart.newBuilder().setCoreService(
                        GdiCore.CoreService.newBuilder().setLocationUpdatedNotification(locationUpdatedNotification)
                ).build());
    }

    @Nullable
    public DocumentFile getAgpsFile(final String url) {
        final Prefs prefs = getDevicePrefs();
        final String filename = prefs.getString(GarminPreferences.agpsFilename(url), "");
        if (filename.isEmpty()) {
            LOG.debug("agps file not configured for {}", url);
            return null;
        }

        final String folderUri = prefs.getString(GarminPreferences.PREF_GARMIN_AGPS_FOLDER, "");
        if (folderUri.isEmpty()) {
            LOG.debug("agps folder not set");
            return null;
        }
        final DocumentFile folder = DocumentFile.fromTreeUri(getContext(), Uri.parse(folderUri));
        if (folder == null) {
            LOG.warn("Failed to find agps folder on {}", folderUri);
            return null;
        }

        final DocumentFile localFile = folder.findFile(filename);
        if (localFile == null) {
            LOG.warn("Failed to find agps file '{}' for '{}' on '{}'", filename, url, folderUri);
            return null;
        }
        if (!localFile.isFile()) {
            LOG.warn("Local agps file {} for {} can't be read: isFile={} canRead={}", folderUri, url, localFile.isFile(), localFile.canRead());
            return null;
        }
        return localFile;
    }

    public GarminCoordinator getCoordinator() {
        return (GarminCoordinator) getDevice().getDeviceCoordinator();
    }

    @Override
    public boolean connectFirstTime() {
        mFirstConnect = true;
        return connect();
    }

    @Override
    public void onReadConfiguration(final String config) {
        if (config.startsWith("screenId:")) {
            final int screenId = Integer.parseInt(config.replaceFirst("screenId:", ""));

            LOG.debug("Requesting screen {}", screenId);

            final String language = Locale.getDefault().getLanguage();
            final String country = Locale.getDefault().getCountry();
            final String localeString = language + "_" + country.toUpperCase();

            sendProtobufRequest("get settings screen " + screenId,
                    Smart.newBuilder()
                            .setRealtimeSettingsService(GdiRealtimeSettings.RealtimeSettingsService.newBuilder()
                                    .setDefinitionRequest(
                                        GdiRealtimeSettings.ScreenDefinitionRequest.newBuilder()
                                                    .setScreenId(screenId)
                                                    .setUnk2(0)
                                                    .setLocale(localeString.length() == 5 ? localeString : "en_US")
                                    )
                            ).build());

            sendProtobufRequest("get settings state " + screenId,
                    Smart.newBuilder()
                            .setRealtimeSettingsService(GdiRealtimeSettings.RealtimeSettingsService.newBuilder()
                                    .setStateRequest(
                                        GdiRealtimeSettings.ScreenStateRequest.newBuilder()
                                                    .setScreenId(screenId)
                                    )
                            ).build());
        }
    }

    @Override
    public void onSyncWorkoutTemplate(final long templateId) {
        final WorkoutTemplate template = WorkoutTemplateRepository.INSTANCE.load(templateId);
        if (template == null) {
            LOG.error("Workout template {} not found", templateId);
            return;
        }

        final FitFile fitFile = GarminWorkoutFitEncoder.INSTANCE.encode(
                template,
                GarminTimeUtils.javaMillisToGarminTimestamp(System.currentTimeMillis())
        );
        if (fitFile == null) {
            LOG.error("Failed to encode workout template {} as a fit file", templateId);
            WorkoutTemplateRepository.INSTANCE.markSyncFailed(templateId, "FIT encoding failed");
            return;
        }

        // The hash is saved before the upload, so an edit made during the upload shows as out of date
        final int contentHash = WorkoutTemplateRepository.INSTANCE.contentHash(template);
        WorkoutTemplateRepository.INSTANCE.markSyncPending(templateId);

        communicator.sendMessage(
                "upload workout template " + templateId,
                fileTransferHandler.initiateUpload(
                        fitFile.getOutgoingMessage(),
                        FileType.FILETYPE.WORKOUTS,
                        success -> {
                            if (success) {
                                WorkoutTemplateRepository.INSTANCE.markSynced(templateId, null, contentHash);
                            } else {
                                WorkoutTemplateRepository.INSTANCE.markSyncFailed(
                                        templateId,
                                        getContext().getString(R.string.workout_template_sync_failed_transfer)
                                );
                            }
                        }
                ).getOutgoingMessage()
        );
    }

    @Override
    public void onInstallApp(Uri uri, @NonNull final Bundle options) {
        final GarminFitFileInstallHandler fitFileInstallHandler = new GarminFitFileInstallHandler(uri, options, getContext());
        if (fitFileInstallHandler.isValid()) {
            final FileType.FILETYPE fileType = fitFileInstallHandler.getFileType();
            communicator.sendMessage(
                    options.getString(BUNDLE_EXTRA_INSTALL_TASK_NAME, "upload " + fileType + " file"),
                    fileTransferHandler.initiateUpload(
                            fitFileInstallHandler.getRawBytes(),
                            fileType
                    ).getOutgoingMessage()
            );
            return;
        }

        final GarminGpxRouteInstallHandler garminGpxRouteInstallHandler = new GarminGpxRouteInstallHandler(uri, getContext());
        if (garminGpxRouteInstallHandler.isValid()) {
            final String trackName = options.getString(GarminGpxRouteInstallHandler.EXTRA_TRACK_NAME);
            final boolean includeNavigation = options.getBoolean(GarminGpxRouteInstallHandler.EXTRA_NAVIGATION_ENABLED);
            final boolean includeStraightNavigation = options.getBoolean(GarminGpxRouteInstallHandler.EXTRA_STRAIGHT_NAVIGATION_ENABLED);
            final GpxRouteFileConverter gpxRouteFileConverter = new GpxRouteFileConverter(
                    garminGpxRouteInstallHandler.getGpxFile(),
                    trackName, includeNavigation, includeStraightNavigation, null
            );


            final FitFile convertedFile = gpxRouteFileConverter.getConvertedFile();
            final FileType.FILETYPE fileType = convertedFile.getFileType();
            communicator.sendMessage(
                    options.getString(BUNDLE_EXTRA_INSTALL_TASK_NAME, "upload " + fileType + " file"),
                    fileTransferHandler.initiateUpload(
                            convertedFile.getOutgoingMessage(),
                            fileType
                    ).getOutgoingMessage()
            );
            return;
        }

        LOG.warn("no install handler found for {} {}", uri, options.getString(BUNDLE_EXTRA_INSTALL_TASK_NAME));
    }

    @Override
    public void onInstall(@NonNull final InstallSession session) {
        getDevice().setBusyTask(R.string.uploading, getContext());
        getDevice().sendDeviceUpdateIntent(getContext());
        installItem(session, session.getItems(), 0);
    }

    /**
     * Uploads the item at the specified index. The next item starts when the upload of this item finishes.
     */
    private void installItem(final InstallSession session, final List<InstallItem> items, final int index) {
        if (index >= items.size()) {
            // Refresh app list
            onAppInfoReq(GdiInstalledApps.AppType.WATCH_APP);
            if (getDevice().isBusy()) {
                getDevice().unsetBusyTask();
                getDevice().sendDeviceUpdateIntent(getContext());
            }
            return;
        }
        final InstallItem item = items.get(index);
        if (!(item instanceof GarminPrgInstallItem prgItem)) {
            LOG.error("Unsupported install item {}", item.getClass());
            session.failed(item, getContext().getString(R.string.fwapp_install_device_not_supported));
            installItem(session, items, index + 1);
            return;
        }

        final byte[] bytes;
        try {
            bytes = prgItem.getFile().readBytes(getContext());
        } catch (final IOException e) {
            LOG.error("Failed to read {}", item.getDisplayName(), e);
            session.failed(item, getContext().getString(R.string.installation_failed_));
            installItem(session, items, index + 1);
            return;
        }

        final FileTransferHandler.UploadListener listener = new FileTransferHandler.UploadListener() {
            @Override
            public void onProgress(final int percentage) {
                session.progress(item, percentage);
            }

            @Override
            public void onComplete(final boolean success) {
                if (success) {
                    session.installed(item);
                } else {
                    session.failed(item, null);
                }
                installItem(session, items, index + 1);
            }
        };

        final String taskName = "upload prg " + item.getDisplayName();
        if (communicator instanceof CommunicatorV2 communicatorV2 && mlrEnabled()
                && getDevicePrefs().getBoolean(GarminPreferences.PREF_GARMIN_PRG_TRANSFER_MLR, false)) {
            installPrgViaMlr(communicatorV2, bytes, taskName, listener);
            return;
        }

        LOG.debug("Uploading {} bytes for {}", bytes.length, item.getDisplayName());
        communicator.sendMessage(
                taskName,
                fileTransferHandler.initiateUpload(bytes, FileType.FILETYPE.PRG, listener).getOutgoingMessage()
        );
    }

    private void notifyConnectIqErrorReport(final File file, final byte[] content) {
        final String text = new String(content, StandardCharsets.UTF_8);
        final int errorStart = text.lastIndexOf("Error: ");
        final String error;
        if (errorStart >= 0) {
            final int errorEnd = text.indexOf('\n', errorStart);
            error = text.substring(errorStart, errorEnd >= 0 ? errorEnd : text.length());
        } else {
            error = file.getName();
        }

        notifyWatchError(file, getContext().getString(R.string.garmin_connect_iq_error_report), error);
    }

    private void notifyWatchError(final File file, final String title, final String content) {
        final Intent viewIntent = new Intent(getContext(), TextFileViewerActivity.class);
        viewIntent.putExtra(TextFileViewerActivity.EXTRA_PATH, file.getAbsolutePath());
        final PendingIntent viewPendingIntent = PendingIntent.getActivity(
                getContext(),
                0,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        final Notification notification = new NotificationCompat.Builder(getContext(), GB.NOTIFICATION_CHANNEL_ID_DEVICE_WARNINGS)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle(title)
                .setContentText(content)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(viewPendingIntent)
                .addAction(0, getContext().getString(R.string.view_file), viewPendingIntent)
                .build();

        GB.notify("connect_iq_error_report_" + getDevice().getAddress(), 0, notification, getContext());
    }

    @Override
    public void onHeartRateTest() {
        communicator.onHeartRateTest();
    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(final boolean enable) {
        communicator.onEnableRealtimeHeartRateMeasurement(enable);
    }

    @Override
    public void onEnableRealtimeSteps(final boolean enable) {
        communicator.onEnableRealtimeSteps(enable);
    }

    public void downloadFileFromServiceV2(final int fileHandle) {
        LOG.info("Requesting file service V2 handle={}", fileHandle);
        if (!(communicator instanceof CommunicatorV2 communicatorV2)) {
            LOG.error("Communicator is not V2");
            return;
        }
        communicatorV2.startTransfer(new CommunicatorV2.ServiceCallback() {
            private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            private boolean started = false;
            private CommunicatorV2.ServiceWriter writer;

            @Override
            public void onConnect(final CommunicatorV2.ServiceWriter writer) {
                this.writer = writer;

                final ByteBuffer buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                buf.putShort((short) fileHandle);
                buf.put((byte) 0x00);
                buf.put((byte) 0x00);
                writer.write("request file", buf.array());
            }

            @Override
            public void onClose() {
                if (baos.size() == 0) {
                    LOG.warn("File transfer closed with 0 bytes in the buffer");
                    return;
                }

                LOG.debug("Attempting to inflate {} bytes", baos.size());

                final byte[] inflated = CompressionUtils.INSTANCE.inflate(baos.toByteArray());
                if (inflated == null) {
                    if (currentlyDownloading != null && currentlyDownloading.getSyncFile() != null) {
                        currentlyDownloading = null;
                    }
                    return;
                }

                LOG.debug("Inflated {} bytes", inflated.length);

                final GdiFileAccess.ItemReference gdiFile = (currentlyDownloading != null) ? currentlyDownloading.getSyncFile() : null;
                String typeName;
                FileType.FILETYPE filetype;
                if(gdiFile != null && gdiFile.hasDataType() && gdiFile.getDataType().hasGdxmlString()) {
                    typeName = gdiFile.getDataType().getGdxmlString();
                    filetype = FileType.FILETYPE.findByTypeName(typeName);
                } else {
                    typeName = null;
                    filetype = null;
                }

                String fileExtension = FileUtils.guessFileExtension(inflated);
                final boolean assumeFit;
                if (filetype != null) {
                    assumeFit = filetype.isFitFile();
                } else if (fileExtension != null) {
                    assumeFit = "fit".equals(fileExtension);
                } else {
                    assumeFit = false;
                }
                if (fileExtension == null){
                    fileExtension = "bin";
                }
                if (typeName == null) {
                    typeName = fileExtension.toUpperCase(Locale.ROOT);
                }

                LOG.debug("Identified file as typeName={} fileExtension={} assumeFit={}", typeName, fileExtension, assumeFit);

                if(assumeFit) {
                    final File file;
                    try {
                        final File cacheDir = getContext().getExternalCacheDir();
                        final File inflateDir = new File(cacheDir, "garmin-inflated");
                        //noinspection ResultOfMethodCallIgnored
                        inflateDir.mkdirs();
                        file = File.createTempFile("activity-files-import", ".fit", inflateDir);
                        file.deleteOnExit();
                        FileUtils.copyStreamToFile(new ByteArrayInputStream(inflated), file);
                    } catch (final IOException e) {
                        LOG.error("Failed to create temp file for activity file", e);
                        if (currentlyDownloading != null && currentlyDownloading.getSyncFile() != null) {
                            currentlyDownloading = null;
                        }
                        return;
                    }

                    LOG.debug("Dumped inflated bytes to {}", file.getAbsolutePath());

                    try {
                        final FitImporter fitImporter = new FitImporter(getContext(), gbDevice);
                        fitImporter.importFile(file, false);
                    } catch (final Exception e) {
                        LOG.error("Failed to parse file as fit", e);
                        if (currentlyDownloading != null && currentlyDownloading.getSyncFile() != null) {
                            currentlyDownloading = null;
                        }
                        return;
                    }
                } else {
                    try {
                        Date fileDate = null;
                        if(gdiFile != null && gdiFile.hasModifiedTime()) {
                            int ts = gdiFile.getModifiedTime();
                            if(ts != 0){
                                fileDate = new Date(GarminTimeUtils.garminTimestampToJavaMillis(ts));
                            }
                        }
                        if (fileDate == null && gdiFile != null && gdiFile.hasUid()) {
                            // The upper 32 bits of id1 are the creation time of the file
                            final int idTimestamp = (int) (gdiFile.getUid().getMostSignificant() >>> 32);
                            if (idTimestamp != 0 && idTimestamp != -1) {
                                fileDate = new Date(GarminTimeUtils.garminTimestampToJavaMillis(idTimestamp));
                            }
                        }
                        String suffix = "";
                        if(gdiFile != null && gdiFile.hasUid()) {
                            suffix = new UUID(gdiFile.getUid().getMostSignificant(), gdiFile.getUid().getLeastSignificant()).toString();
                        }
                        String fileName =  GarminUtils.buildExportPath(typeName, fileDate, suffix, fileExtension);
                        File deviceDir = getWritableExportDirectory();
                        File outputFile = new File(deviceDir, fileName);
                        LOG.info("Saving {} bytes to {}", inflated.length, outputFile.getAbsolutePath());
                        final File parentFile = outputFile.getParentFile();
                        parentFile.mkdirs();
                        // overwrite the file even if it exists - metadata of non-FIT files not always available
                        // potentially resulting in the same name for a newer file
                        FileUtils.writeToFile(inflated, outputFile);
                        if (fileDate != null) {
                            outputFile.setLastModified(fileDate.getTime());
                        }
                        if (BuildConfig.DEBUG && "IQErrorReports".equals(typeName)) {
                            notifyConnectIqErrorReport(outputFile, inflated);
                        }
                        if (BuildConfig.DEBUG && "ErrorShutdownReports".equals(typeName)) {
                            notifyWatchError(
                                outputFile,
                                getContext().getString(R.string.app_crash_notification_title, getDevice().getAliasOrName()),
                                "Received ErrorShutdownReports"
                            );
                        }
                    } catch (final IOException e) {
                        LOG.error("Failed to save file", e);
                        if (currentlyDownloading != null && currentlyDownloading.getSyncFile() != null) {
                            currentlyDownloading = null;
                        }
                        return;
                    }
                }

                if (gdiFile != null && !getKeepActivityDataOnDevice()) { // delete file from watch upon successful download
                    final GdiFileAccess.Service syncedCommand = protocolBufferHandler.getFileSyncServiceHandler()
                            .markSynced(gdiFile);
                    if (syncedCommand != null) {
                        sendProtobufRequest("mark file as synced",
                                Smart.newBuilder().setFileAccessService(syncedCommand).build());
                    }
                }

                LOG.debug("New file sync success");
                if (currentlyDownloading != null && currentlyDownloading.getSyncFile() != null) {
                    transferNotification.incrementTotalProgress(currentlyDownloading.getSyncFile().getDataSize());
                    currentlyDownloading = null;
                }
            }

            @Override
            public void onMessage(final byte[] value) {
                if (!started) {
                    if (!ArrayUtils.equals(new byte[]{0, 0, 0}, value, 0)) {
                        LOG.error("Got unexpected first message");
                        if (currentlyDownloading != null && currentlyDownloading.getSyncFile() != null) {
                            transferNotification.incrementTotalProgress(currentlyDownloading.getSyncFile().getDataSize());
                            currentlyDownloading = null;
                        }
                        return;
                    }
                    started = true;
                    return;
                }
                LOG.debug("Buffering {} bytes", value.length);
                baos.write(value, 0, value.length);
                transferNotification.setChunkProgress(baos.size());
            }
        });
    }

    private PendingItemPush activeItemPush;
    private final Queue<PendingItemPush> queuedItemPushes = new LinkedList<>();

    private record PendingItemPush(CommunicatorV2 communicatorV2,
                                   GdiFileAccess.ItemReference item,
                                   int priority,
                                   byte[] compressed,
                                   String taskName,
                                   FileTransferHandler.UploadListener listener) {
    }

    /**
     * Upload a PRG through a MLR file transfer, after a {@code PushItemRequest} reserves the transfer slot.
     * <p>
     * The data starts with a control message, with the token from {@code PushItemResponse}, followed by the PRG,
     * compressed with zlib, with a 1 KB window. The vendor app uses a better compression, but the Java deflater
     * does not support a window size different from the 32 KB default, which the watch does not accept.
     * <p>
     * The vendor app sends a header before the PRG:
     * - 00:00:00:1d
     * - the header length (u32) - 28 bytes on Fenix 3, 33 bytes on Venu 3
     * - the store version (u32)
     * - the 16-byte store app UUID
     * - zero padding up to the header length.
     * <p>
     * The watch then reports this version and UUID. This is required for app settings to work. However, the watch
     * only accepts the header if the store signature of the PRG is valid for the same UUID and version. Since
     * self-signed PRG files will never have a store signature, we do not replicate the logic.
     */
    private void installPrgViaMlr(final CommunicatorV2 communicatorV2,
                                  final byte[] rawPrgBytes,
                                  final String taskName,
                                  final FileTransferHandler.UploadListener listener) {
        final FileType.FILETYPE prgFileType = GarminPrgFile.getFileType(rawPrgBytes);
        if (prgFileType == null) {
            listener.onComplete(false);
            return;
        }

        final GdiDataTypes.UUID fileId = GdiDataTypes.UUID.newBuilder()
                .setMostSignificant(-1L)
                .setLeastSignificant(new Random().nextLong())
                .build();
        final GdiFileAccess.DataType fileType = GdiFileAccess.DataType.newBuilder()
                .setFormat(GdiFileAccess.DataType.Format.GDXML_DATA_TYPE)
                .setGdxmlString(prgFileType.typeName)
                .build();
        final GdiFileAccess.ItemReference file = GdiFileAccess.ItemReference.newBuilder()
                .setUid(fileId)
                .setDataType(fileType)
                .setDataSize(rawPrgBytes.length)
                .build();

        pushItemViaMlr(communicatorV2, file, 37, rawPrgBytes, taskName, listener);
    }

    /**
     * Sends a data_xfer item over a MLR file transfer, after the watch requested access to it.
     */
    public void pushDataXferItem(final GdiDataTransfer.FileAccessItemResponse fileAccessItem) {
        final int dataId = fileAccessItem.getDataXferId();
        final byte[] data = DataTransferHandler.getData(dataId);
        if (data == null) {
            LOG.error("No data to push for data_xfer id {}", dataId);
            return;
        }
        if (!(communicator instanceof CommunicatorV2 communicatorV2) || !mlrEnabled()) {
            LOG.error("Unable to push data_xfer id {} without MLR", dataId);
            DataTransferHandler.onDataPushed(dataId, false);
            return;
        }

        final GdiFileAccess.ItemReference item = GdiFileAccess.ItemReference.newBuilder()
            .setUid(fileAccessItem.getFileAccessItemUid())
            .setDataType(GdiFileAccess.DataType.newBuilder().setFormat(GdiFileAccess.DataType.Format.DATA_XFER))
            .setDataSize(data.length)
            .build();

        pushItemViaMlr(communicatorV2, item, 34, data, "push data_xfer " + dataId, new FileTransferHandler.UploadListener() {
            @Override
            public void onProgress(final int percentage) {
            }

            @Override
            public void onComplete(final boolean success) {
                DataTransferHandler.onDataPushed(dataId, success);
            }
        });
    }

    /**
     * Pushes an item over a MLR file transfer. If an item is already being pushed, the new one gets queued.
     */
    private void pushItemViaMlr(final CommunicatorV2 communicatorV2,
                                final GdiFileAccess.ItemReference item,
                                final int priority,
                                final byte[] rawBytes,
                                final String taskName,
                                final FileTransferHandler.UploadListener listener) {
        final byte[] compressed = CompressionUtils.INSTANCE.deflateStored(rawBytes, 1024);

        LOG.info("{} via MLR transfer ({} bytes raw, {} bytes compressed)", taskName, rawBytes.length, compressed.length);

        final PendingItemPush push = new PendingItemPush(communicatorV2, item, priority, compressed, taskName, listener);
        synchronized (queuedItemPushes) {
            if (activeItemPush != null) {
                LOG.debug("Queueing {}, already running {}", taskName, activeItemPush.taskName());
                queuedItemPushes.add(push);
                return;
            }
            activeItemPush = push;
        }
        startItemPush(push);
    }

    private void startItemPush(final PendingItemPush push) {
        sendProtobufRequest("reserve push slot for " + push.taskName(),
                Smart.newBuilder().setFileAccessService(
                        GdiFileAccess.Service.newBuilder().setPushItemRequest(
                                GdiFileAccess.PushItemDataRequest.newBuilder()
                                        .setItem(push.item())
                                        .setPriority(push.priority())
                                        .addSupportedTransports(MULTILINK_TRANSPORT_PIPE)
                                        .setRequestedCompressionWindow(15)
                        )
                ).build());
    }

    private void onItemPushFinished() {
        final PendingItemPush next;
        synchronized (queuedItemPushes) {
            next = queuedItemPushes.poll();
            activeItemPush = next;
        }
        if (next != null) {
            startItemPush(next);
        }
    }

    public void onPushItemResponse(final GdiFileAccess.PushItemDataResponse response) {
        final PendingItemPush pending;
        synchronized (queuedItemPushes) {
            pending = activeItemPush;
        }
        if (pending == null) {
            LOG.warn("Got push item response, but no push is pending: {}", response);
            return;
        }

        final int token = response.getTransferHandle();
        LOG.info("Got push item response token {} for {}", token, pending.taskName());

        pending.communicatorV2().startTransfer(new CommunicatorV2.ServiceCallback() {
            private CommunicatorV2.ServiceWriter writer;
            private boolean dataQueued = false;
            private boolean fullySent = false;
            private long totalAckedBytes = 0;
            private int lastPercentage = -1;

            @Override
            public void onConnect(final CommunicatorV2.ServiceWriter writer) {
                this.writer = writer;

                final ByteBuffer ctrl = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
                ctrl.put((byte) 0x00);
                ctrl.put((byte) 0x01);
                ctrl.putShort((short) token);
                ctrl.put((byte) 0x00);
                ctrl.put((byte) 0x00);

                writer.write(pending.taskName(), ctrl.array());
            }

            @Override
            public void onMessage(final byte[] value) {
                LOG.debug("Push transfer message for {} ({} bytes): {}", pending.taskName(), value.length, GB.hexdump(value));
                if (dataQueued) {
                    return;
                }
                if (value.length == 0 || value[0] != 0) {
                    LOG.warn("Watch rejected the transfer for {}", pending.taskName());
                    writer.close();
                    return;
                }
                dataQueued = true;
                writer.setAckListener((ackedBytes, allAcked) -> {
                    totalAckedBytes += ackedBytes;
                    final int percentage = (int) (100L * totalAckedBytes / pending.compressed().length);
                    if (percentage != lastPercentage) {
                        lastPercentage = percentage;
                        pending.listener().onProgress(Math.min(percentage, 100));
                    }
                    if (allAcked) {
                        fullySent = true;
                        writer.close();
                    }
                });
                writer.write(pending.taskName(), pending.compressed());
            }

            @Override
            public void onClose() {
                LOG.info("Push transfer for {} closed, fully sent = {}", pending.taskName(), fullySent);
                pending.listener().onComplete(fullySent);
                onItemPushFinished();
            }
        }, true);
    }

    @Override
    public void onTestNewFunction(@Nullable Bundle options) {
        //communicator.onEnableRealtimeAccelerometer(true);
    }
}
