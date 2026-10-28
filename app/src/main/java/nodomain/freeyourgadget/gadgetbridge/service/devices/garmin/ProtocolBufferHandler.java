package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SYNC_CALENDAR;
import static nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminPreferences.PREF_GARMIN_EXPLORE_SYNC;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminPreferences;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminRealtimeSettingsFragment;
import nodomain.freeyourgadget.gadgetbridge.externalevents.gps.GBLocationProviderType;
import nodomain.freeyourgadget.gadgetbridge.externalevents.gps.GBLocationService;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiCredentials;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiCalendar;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiCore;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTransfer;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTypes;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDeviceStatus;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiFileAccess;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiFindMyWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiGncs;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiHttp;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiInstalledApps;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiRealtimeSettings;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmartProto.Smart;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmsNotification;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiEcgService;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiExploreSync;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.DataTransferHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.HttpHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.GFDIMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.ProtobufMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.status.ProtobufStatusMessage;
import nodomain.freeyourgadget.gadgetbridge.webview.CurrentPosition;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarEvent;
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarManager;

public class ProtocolBufferHandler implements MessageHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProtocolBufferHandler.class);
    private final GarminSupport deviceSupport;
    private final Map<Integer, ProtobufFragment> chunkedFragmentsMap;
    private final Map<Integer, GdiDataTransfer.FileAccessItemResponse> fileAccessResponsesByRequestId = new HashMap<>();
    private int maxChunkSize = 375; //tested on Vívomove Style
    private int lastProtobufRequestId;
    private final AppConfigHandler appConfigHandler;
    private HttpHandler httpHandler;
    private final DataTransferHandler dataTransferHandler;
    private final FileSyncServiceHandler fileSyncServiceHandler;
    private final EcgServiceHandler ecgServiceHandler;
    private final ExploreSyncHandler exploreSyncHandler;
    private final PushNotificationHandler pushNotificationHandler;

    private final Map<GdiSmsNotification.CannedListType, String[]> cannedListTypeMap = new HashMap<>();

    public ProtocolBufferHandler(GarminSupport deviceSupport) {
        this.deviceSupport = deviceSupport;
        chunkedFragmentsMap = new HashMap<>();
        appConfigHandler = new AppConfigHandler(deviceSupport);
        dataTransferHandler = new DataTransferHandler();
        fileSyncServiceHandler = new FileSyncServiceHandler(deviceSupport);
        ecgServiceHandler = new EcgServiceHandler(deviceSupport);
        exploreSyncHandler = new ExploreSyncHandler(deviceSupport);
        pushNotificationHandler = new PushNotificationHandler(deviceSupport);
    }

    public void setContext(final GBDevice gbDevice, final BluetoothAdapter btAdapter, final Context context) {
        // http handler needs the device
        httpHandler = new HttpHandler(deviceSupport);
    }

    private int getNextProtobufRequestId() {
        lastProtobufRequestId = (lastProtobufRequestId + 1) % 65536;
        return lastProtobufRequestId;
    }

    @Override
    public ProtobufMessage handle(GFDIMessage protobufMessage) {
        if (protobufMessage instanceof ProtobufMessage) {
            return processIncoming((ProtobufMessage) protobufMessage);
        } else if (protobufMessage instanceof ProtobufStatusMessage) {
            return processIncoming((ProtobufStatusMessage) protobufMessage);
        }
        return null;
    }

    private ProtobufMessage processIncoming(ProtobufMessage message) {
        ProtobufFragment protobufFragment = processChunkedMessage(message);

        if (protobufFragment.isComplete()) { //message is now complete
            LOG.info("Received protobuf message #{}, {}B: {}", message.getRequestId(), protobufFragment.totalLength, GB.hexdump(protobufFragment.fragmentBytes, 0, protobufFragment.totalLength));
            chunkedFragmentsMap.remove(message.getRequestId());

            final Smart smart;
            try {
                smart = Smart.parseFrom(protobufFragment.fragmentBytes);
            } catch (InvalidProtocolBufferException e) {
                LOG.error("Failed to parse protobuf message ({}): {}", e.getLocalizedMessage(), GB.hexdump(protobufFragment.fragmentBytes));
                return null;
            }
            boolean processed = false;
            if (smart.hasCoreService()) { //TODO: unify request and response???
                return prepareProtobufResponse(processProtobufCoreRequest(smart.getCoreService()), message.getRequestId());
            }
            if (smart.hasCalendarEventsService()) {
                return prepareProtobufResponse(processProtobufCalendarRequest(smart.getCalendarEventsService()), message.getRequestId());
            }
            if (smart.hasSmsNotificationService()) {
                return prepareProtobufResponse(processProtobufSmsNotificationMessage(smart.getSmsNotificationService()), message.getRequestId());
            }
            if (smart.hasConnectIqHttpService()) {
                final GdiHttp.ConnectIQHTTPService response = httpHandler.handle(smart.getConnectIqHttpService(), message.getRequestId());
                if (response != null) {
                    return prepareProtobufResponse(Smart.newBuilder().setConnectIqHttpService(response).build(), message.getRequestId());
                }
                processed = true;
                // Response will be async
            }
            if (smart.hasDataTransferService() && smart.getDataTransferService().hasFileAccessItemRequest()) {
                final GdiDataTransfer.FileAccessItemResponse response = dataTransferHandler.handleFileAccessItemRequest(
                        smart.getDataTransferService().getFileAccessItemRequest()
                );
                if (response.getStatus() == GdiDataTransfer.FileAccessItemResponse.Status.OK) {
                    // The data will be pushed after the watch acknowledges this response
                    fileAccessResponsesByRequestId.put(message.getRequestId(), response);
                }
                return prepareProtobufResponse(Smart.newBuilder().setDataTransferService(
                        GdiDataTransfer.DataTransferService.newBuilder().setFileAccessItemResponse(response)
                ).build(), message.getRequestId());
            }
            if (smart.hasDataTransferService()) {
                final GdiDataTransfer.DataTransferService response = dataTransferHandler.handle(smart.getDataTransferService(), message.getRequestId());
                if (response == null) {
                    return null;
                }
                return prepareProtobufResponse(Smart.newBuilder().setDataTransferService(response).build(), message.getRequestId());
            }
            if (smart.hasDeviceStatusService()) {
                processed = true;
                processProtobufDeviceStatusResponse(smart.getDeviceStatusService());
            }
            if (smart.hasFindMyWatchService()) {
                processed = true;
                processProtobufFindMyWatchResponse(smart.getFindMyWatchService());
            }
            if (smart.hasRealtimeSettingsService()) {
                processed = true;
                processProtobufSettingsService(smart.getRealtimeSettingsService());
            }
            if (smart.hasCredentialsService() && smart.getCredentialsService().hasGcOauthRequest()) {
                LOG.debug("Got OAuth request");
                final GarminPrefs devicePrefs = deviceSupport.getDevicePrefs();
                if (!devicePrefs.fakeOauthEnabled()) {
                    LOG.warn("Got OAuth request, but fake OAuth is disabled");
                } else {
                    final GdiCredentials.GCOAuthCredentialsResponse oauthResponse = GdiCredentials.GCOAuthCredentialsResponse.newBuilder()
                            .setOauthCredentials(GdiCredentials.OAuthCredentials.newBuilder()
                                    .setConsumerKey(UUID.randomUUID().toString())
                                    .setConsumerSecret(RandomStringUtils.insecure().next(35, true, true))
                                    .setOauthToken(UUID.randomUUID().toString())
                                    .setOauthSecret(RandomStringUtils.insecure().next(35, true, true))
                                    .build()
                            ).setGcsLocation(GdiCredentials.GCSLocation.PRODUCTION).build();

                    return prepareProtobufResponse(Smart.newBuilder().setCredentialsService(
                            GdiCredentials.CredentialsService.newBuilder()
                                    .setGcOauthResponse(oauthResponse)
                    ).build(), message.getRequestId());
                }
            }
            if (smart.hasGncsService()) {
                return prepareProtobufResponse(processProtobufNotificationsServiceMessage(smart.getGncsService()), message.getRequestId());
            }
            if (smart.hasConnectIqInstalledAppsService()) {
                final GdiInstalledApps.ConnectIQInstalledAppsService installedAppsService = smart.getConnectIqInstalledAppsService();

                if (installedAppsService.hasGetInstalledAppsResponse()) {
                    processed = true;

                    final List<GdiInstalledApps.GetInstalledAppsResponse.InstalledApp> installedAppsList = installedAppsService
                            .getGetInstalledAppsResponse()
                            .getInstalledAppList();

                    LOG.info("Got app list with {} apps", installedAppsList.size());

                    deviceSupport.onAppListReceived(installedAppsList);
                } else if (installedAppsService.hasDeleteAppResponse()) {
                    processed = true;

                    final GdiInstalledApps.DeleteAppResponse.Status status = installedAppsService.getDeleteAppResponse().getStatus();

                    LOG.info("Got app delete response, status = {}", status);

                    // Refresh app list
                    if (status == GdiInstalledApps.DeleteAppResponse.Status.OK) {
                        deviceSupport.onAppInfoReq();
                    }
                }
            }
            if (smart.hasFileAccessService()) {
                if (smart.getFileAccessService().hasPushItemResponse()) {
                    // Handled outside of new_sync_protocol, since this only applies to file uploads
                    processed = true;
                    deviceSupport.onPushItemResponse(smart.getFileAccessService().getPushItemResponse());
                }
                if (deviceSupport.newSyncProtocol()) {
                    processed = true;
                    final GdiFileAccess.Service response = fileSyncServiceHandler.handle(smart.getFileAccessService());
                    if (response != null) {
                        return prepareProtobufResponse(Smart.newBuilder().setFileAccessService(response).build(), message.getRequestId());
                    }
                } else {
                    LOG.warn("Ignoring file sync service - new sync protocol is disabled");
                }
            }
            if (smart.hasSamdService()) {
                if (deviceSupport.newSyncProtocol()) {
                    processed = true;
                    final GdiEcgService.EcgService response = ecgServiceHandler.handle(smart.getSamdService());
                    if (response != null) {
                        return prepareProtobufResponse(Smart.newBuilder().setSamdService(response).build(), message.getRequestId());
                    }
                } else {
                    LOG.warn("Ignoring zip transfer service - new sync protocol is disabled");
                }
            }
            if (smart.hasConnectIqAppSettingsService()) {
                processed = appConfigHandler.process(smart.getConnectIqAppSettingsService());
            }
            if (smart.hasDeviceMessageService()) {
                processed = pushNotificationHandler.handle(smart.getDeviceMessageService());
            }
            if (smart.hasExploreSyncService()) {
                if (deviceSupport.getDevicePrefs().getBoolean(PREF_GARMIN_EXPLORE_SYNC, false)) {
                    processed = true;
                    final GdiExploreSync.ExploreSyncService response = exploreSyncHandler.handle(smart.getExploreSyncService());
                    if (response != null) {
                        return prepareProtobufResponse(Smart.newBuilder().setExploreSyncService(response).build(), message.getRequestId());
                    }
                } else {
                    LOG.warn("Ignoring explore sync service - explore sync is disabled");
                }
            }
            if (processed) {
                message.setStatusMessage(new ProtobufStatusMessage(
                        message.getMessageType(),
                        GFDIMessage.Status.ACK,
                        message.getRequestId(),
                        message.getDataOffset(),
                        ProtobufStatusMessage.ProtobufChunkStatus.KEPT,
                        ProtobufStatusMessage.ProtobufStatusCode.NO_ERROR
                ));
            } else {
                LOG.warn("Unknown protobuf request: {}", smart);
                message.setStatusMessage(new ProtobufStatusMessage(
                        message.getMessageType(),
                        GFDIMessage.Status.ACK,
                        message.getRequestId(),
                        message.getDataOffset(),
                        ProtobufStatusMessage.ProtobufChunkStatus.DISCARDED,
                        ProtobufStatusMessage.ProtobufStatusCode.UNKNOWN_REQUEST_ID
                ));
            }
        }
        return null;
    }

    private ProtobufMessage processIncoming(ProtobufStatusMessage statusMessage) {
        //TODO: check status and react accordingly, right now we blindly proceed to next chunk
        if (statusMessage.isOK()) {
            DataTransferHandler.onDataChunkSuccessfullyReceived(statusMessage.getRequestId());

            final GdiDataTransfer.FileAccessItemResponse fileAccess = fileAccessResponsesByRequestId.remove(statusMessage.getRequestId());
            if (fileAccess != null) {
                deviceSupport.pushDataXferItem(fileAccess);
            }

            if (chunkedFragmentsMap.containsKey(statusMessage.getRequestId())) {
                final ProtobufFragment protobufFragment = chunkedFragmentsMap.get(statusMessage.getRequestId());
                LOG.debug("Protobuf message #{} found in queue: {}", statusMessage.getRequestId(), GB.lazyHexdump(protobufFragment.fragmentBytes));

                if (protobufFragment.totalLength <= (statusMessage.getDataOffset() + maxChunkSize)) {
                    chunkedFragmentsMap.remove(statusMessage.getRequestId());
                    return null;
                }
                return protobufFragment.getNextChunk(statusMessage);
            }
        }
        return null;
    }

    private ProtobufFragment processChunkedMessage(ProtobufMessage message) {
        if (message.isComplete()) //comment this out if for any reason also smaller messages should end up in the map
            return new ProtobufFragment(message.getMessageBytes());

        if (message.getDataOffset() == 0) { //store new messages beginning at 0, overwrite old messages
            chunkedFragmentsMap.put(message.getRequestId(), new ProtobufFragment(message));
            LOG.info("Protobuf request put in queue: #{} , {}", message.getRequestId(), GB.hexdump(message.getMessageBytes()));
        } else {
            if (chunkedFragmentsMap.containsKey(message.getRequestId())) {
                ProtobufFragment oldFragment = chunkedFragmentsMap.get(message.getRequestId());
                chunkedFragmentsMap.put(message.getRequestId(),
                        new ProtobufFragment(oldFragment, message));
            }
        }
        return chunkedFragmentsMap.get(message.getRequestId());
    }

    private Smart processProtobufCalendarRequest(GdiCalendar.CalendarService calendarService) {
        if (calendarService.hasCalendarEventsRequest()) {
            GdiCalendar.CalendarEventsRequest calendarServiceRequest = calendarService.getCalendarEventsRequest();

            final boolean syncEnabled = GBApplication.getDeviceSpecificSharedPrefs(deviceSupport.getDevice().getAddress())
                    .getBoolean(PREF_SYNC_CALENDAR, false);

            if (!syncEnabled) {
                LOG.warn("Got calendar request, but calendar sync is disabled");
                return Smart.newBuilder().setCalendarEventsService(
                        GdiCalendar.CalendarService.newBuilder().setCalendarEventsResponse(
                                GdiCalendar.CalendarEventsResponse.newBuilder()
                                        .addAllEvents(Collections.emptyList())
                                        .setStatus(GdiCalendar.CalendarEventsResponse.ResponseStatus.OK)
                        )
                ).build();
            }

            CalendarManager upcomingEvents = new CalendarManager(deviceSupport.getContext(), deviceSupport.getDevice().getAddress());
            List<CalendarEvent> mEvents = upcomingEvents.getCalendarEventList();
            List<GdiCalendar.CalendarEvent> watchEvents = new ArrayList<>();

            for (CalendarEvent mEvt : mEvents) {
                if (mEvt.getEndSeconds() < calendarServiceRequest.getStartDate() ||
                        mEvt.getBeginSeconds() > calendarServiceRequest.getEndDate()) {
                    LOG.debug("CalendarService Skipping event {} that is out of requested time range", mEvt.getTitle());
                    continue;
                }
                if (!calendarServiceRequest.getIncludeAllDay() && mEvt.isAllDay()) {
                    LOG.debug("CalendarService Skipping event {} that is AllDay", mEvt.getTitle());
                    continue;
                }

                if (watchEvents.size() >= calendarServiceRequest.getMaxEvents() * 2) { //NOTE: Tested with values higher than double of the reported max without issues
                    LOG.debug("Reached the maximum number of events supported by the watch");
                    break;
                }

                final int startDateSeconds;
                final int endDateSeconds;

                if (mEvt.isAllDay()) {
                    // For all-day events, Garmin expects the start and end date to match the midnight boundaries
                    // in the user's timezone. However, the calendar event will have them in the UTC timezone,
                    // so we need to convert it
                    startDateSeconds = (int) (DateTimeUtils.utcDateTimeToLocal(mEvt.getBegin()) / 1000);
                    endDateSeconds = (int) (DateTimeUtils.utcDateTimeToLocal(mEvt.getEnd()) / 1000);
                } else {
                    startDateSeconds = mEvt.getBeginSeconds();
                    endDateSeconds = mEvt.getEndSeconds();
                }

                final GdiCalendar.CalendarEvent.Builder event = GdiCalendar.CalendarEvent.newBuilder()
                        .setTitle(mEvt.getTitle().substring(0, Math.min(mEvt.getTitle().length(), calendarServiceRequest.getMaxTitleLength())))
                        .setAllDay(mEvt.isAllDay())
                        .setStartDate(startDateSeconds)
                        .setEndDate(endDateSeconds);

                if (calendarServiceRequest.getIncludeLocation() && mEvt.getLocation() != null) {
                    event.setLocation(mEvt.getLocation().substring(0, Math.min(mEvt.getLocation().length(), calendarServiceRequest.getMaxLocationLength())));
                }

                if (calendarServiceRequest.getIncludeDescription() && mEvt.getDescription() != null) {
                    event.setDescription(mEvt.getDescription().substring(0, Math.min(mEvt.getDescription().length(), calendarServiceRequest.getMaxDescriptionLength())));
                }
                if (calendarServiceRequest.getIncludeOrganizer() && mEvt.getOrganizer() != null) {
                    event.setDescription(mEvt.getOrganizer().substring(0, Math.min(mEvt.getOrganizer().length(), calendarServiceRequest.getMaxOrganizerLength())));
                }
                watchEvents.add(event.build());
            }

            LOG.debug("CalendarService Sending {} events to watch", watchEvents.size());
            return Smart.newBuilder().setCalendarEventsService(
                    GdiCalendar.CalendarService.newBuilder().setCalendarEventsResponse(
                            GdiCalendar.CalendarEventsResponse.newBuilder()
                                    .addAllEvents(watchEvents)
                                    .setStatus(GdiCalendar.CalendarEventsResponse.ResponseStatus.OK)
                    )
            ).build();
        }
        LOG.warn("Unknown CalendarService request: {}", calendarService);
        return Smart.newBuilder().setCalendarEventsService(
                GdiCalendar.CalendarService.newBuilder().setCalendarEventsResponse(
                        GdiCalendar.CalendarEventsResponse.newBuilder()
                                .setStatus(GdiCalendar.CalendarEventsResponse.ResponseStatus.UNKNOWN_RESPONSE_STATUS)
                )
        ).build();
    }

    private void processProtobufDeviceStatusResponse(GdiDeviceStatus.DeviceStatusService deviceStatusService) {
        if (deviceStatusService.hasRemoteDeviceBatteryStatusResponse()) {
            final GdiDeviceStatus.RemoteDeviceBatteryStatusResponse batteryStatusResponse = deviceStatusService.getRemoteDeviceBatteryStatusResponse();
            final int batteryLevel = batteryStatusResponse.getCurrentBatteryLevel();
            LOG.info("Received remote battery status {}: level={}", batteryStatusResponse.getStatus(), batteryLevel);
            final GBDeviceEventBatteryInfo batteryEvent = new GBDeviceEventBatteryInfo();
            batteryEvent.level = (short) batteryLevel;
            deviceSupport.evaluateGBDeviceEvent(batteryEvent);
            return;
        }
        if (deviceStatusService.hasActivityStatusResponse()) {
            final GdiDeviceStatus.GetCurrentActivityStatusResponse activityStatusResponse = deviceStatusService.getActivityStatusResponse();
            LOG.info("Received activity status: {}", activityStatusResponse.getActivityStatus());
            return;
        }
        LOG.warn("Unknown DeviceStatusService response: {}", deviceStatusService);
    }

    @Nullable
    private Smart processProtobufCoreRequest(@NonNull GdiCore.CoreService coreService) {
        if (coreService.hasSyncResponse()) {
            final GdiCore.SyncResponse syncResponse = coreService.getSyncResponse();
            LOG.info("Received sync status: {}", syncResponse.getStatus());
            return null;
        }

        if (coreService.hasGetLocationRequest()) {
            LOG.info("Got location request");
            final CurrentPosition currentPosition = new CurrentPosition();
            final Location location = currentPosition.getLastKnownLocation();
            final GdiCore.GetLocationResponse.Builder response = GdiCore.GetLocationResponse.newBuilder();
            if (location.getLatitude() == 0 && location.getLongitude() == 0) {
                response.setStatus(GdiCore.GetLocationResponse.Status.NO_VALID_LOCATION);
            } else if (currentPosition.isFromPreferences() &&
                    GBApplication.getPrefs().getBoolean("use_updated_location_if_available", false)) {
                LOG.warn("No fresh location available, replying with NO_VALID_LOCATION");
                response.setStatus(GdiCore.GetLocationResponse.Status.NO_VALID_LOCATION);
            } else {
                response.setStatus(GdiCore.GetLocationResponse.Status.OK)
                        .setLocationData(GarminUtils.toLocationData(location, GdiCore.LocationData.DataType.GENERAL_LOCATION));
            }
            return Smart.newBuilder().setCoreService(
                    GdiCore.CoreService.newBuilder().setGetLocationResponse(response)).build();
        }

        if (coreService.hasLocationUpdatedSetEnabledRequest()) {
            final GdiCore.LocationUpdatedSetEnabledRequest locationUpdatedSetEnabledRequest = coreService.getLocationUpdatedSetEnabledRequest();

            LOG.info("Received locationUpdatedSetEnabledRequest status: {}", locationUpdatedSetEnabledRequest.getEnabled());

            GdiCore.LocationUpdatedSetEnabledResponse.Builder response = GdiCore.LocationUpdatedSetEnabledResponse.newBuilder()
                    .setStatus(GdiCore.LocationUpdatedSetEnabledResponse.Status.OK);

            final boolean sendGpsPref = deviceSupport.getDevicePrefs().getBoolean(DeviceSettingsPreferenceConst.PREF_WORKOUT_SEND_GPS_TO_BAND, false);

            GdiCore.LocationUpdatedSetEnabledRequest.Request realtimeRequest = null;

            if (locationUpdatedSetEnabledRequest.getEnabled()) {
                for (final GdiCore.LocationUpdatedSetEnabledRequest.Request request : locationUpdatedSetEnabledRequest.getRequestsList()) {
                    final GdiCore.LocationUpdatedSetEnabledResponse.RequestStatus.Status requestedStatus;
                    if (GdiCore.LocationData.DataType.REALTIME_TRACKING.equals(request.getRequested())) {
                        realtimeRequest = request;
                        if (sendGpsPref) {
                            requestedStatus = GdiCore.LocationUpdatedSetEnabledResponse.RequestStatus.Status.OK;
                        } else {
                            requestedStatus = GdiCore.LocationUpdatedSetEnabledResponse.RequestStatus.Status.NOT_AVAILABLE;
                        }
                    } else {
                        requestedStatus = GdiCore.LocationUpdatedSetEnabledResponse.RequestStatus.Status.NOT_AVAILABLE;
                    }

                    response.addRequests(
                            GdiCore.LocationUpdatedSetEnabledResponse.RequestStatus.newBuilder()
                                    .setRequested(request.getRequested())
                                    .setStatus(requestedStatus)
                    );
                }
            }

            if (sendGpsPref) {
                if (realtimeRequest != null) {
                    GBLocationService.start(
                            deviceSupport.getContext(),
                            deviceSupport.getDevice(),
                            GBLocationProviderType.GPS,
                            1000 // TODO from realtimeRequest
                    );
                } else {
                    GBLocationService.stop(deviceSupport.getContext(), deviceSupport.getDevice());
                }
            }

            return Smart.newBuilder().setCoreService(
                    GdiCore.CoreService.newBuilder().setLocationUpdatedSetEnabledResponse(response)).build();
        }

        if (coreService.hasConnectionReadyNotification()) {
            LOG.info("Got ConnectedNotification");
            // ping-pong to notify the gadget that the connection is live and working
            return Smart.newBuilder().setCoreService(
                    GdiCore.CoreService.newBuilder().setConnectionReadyNotification(
                            GdiCore.ConnectionReadyNotification.newBuilder().build()
                    )).build();
        }

        LOG.warn("Unknown CoreService request: {}", coreService);
        return null;
    }

    private Smart processProtobufNotificationsServiceMessage(final GdiGncs.Service notificationsService) {
        if (notificationsService.hasMediaRequest()) {
            final GdiGncs.MediaRequest pictureRequest = notificationsService.getMediaRequest();
            final int notificationId = pictureRequest.getNotificationUid();
            final Bitmap bmp = deviceSupport.getNotificationAttachmentBitmap(notificationId);
            if (bmp == null) {
                return null;
            }

            final GdiGncs.DisplayAttributes parameters = pictureRequest.getAttributes();
            final int targetHeight = (int) Math.round(bmp.getHeight() * ((double) parameters.getWidthPx() / bmp.getWidth()));

            final Bitmap scaledBmp = Bitmap.createScaledBitmap(bmp, parameters.getWidthPx(), targetHeight, true);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaledBmp.compress(Bitmap.CompressFormat.JPEG, parameters.getQuality(), baos);
            final byte[] imageBytes = baos.toByteArray();

            final int transferId = DataTransferHandler.registerData(imageBytes);

            final GdiGncs.MediaResponse.Builder response = GdiGncs.MediaResponse.newBuilder()
                    .setStatus(GdiGncs.MediaResponse.Status.SUCCESS)
                    .setNotificationUid(notificationId)
                    .setType(GdiGncs.MediaType.JPEG)
                    .setTransferInfo(
                        GdiDataTypes.DataTransferItem.newBuilder()
                                    .setId(transferId)
                                    .setSize(imageBytes.length)
                                    .build()
                    );

            if (pictureRequest.hasIndex()) {
                response.setIndex(pictureRequest.getIndex());
            }

            return Smart.newBuilder().setGncsService(
                    GdiGncs.Service.newBuilder().setMediaResponse(response.build())
            ).build();
        }

        LOG.warn("Protobuf notificationsService request not implemented: {}", notificationsService);
        return null;
    }

    private Smart processProtobufSmsNotificationMessage(GdiSmsNotification.SmsNotificationService smsNotificationService) {
        if (smsNotificationService.hasSmsCannedListRequest()) {
            LOG.debug("Got request for sms canned list");

            // Mark canned messages as supported
            deviceSupport.evaluateGBDeviceEvent(new GBDeviceEventUpdatePreferences(GarminPreferences.PREF_FEAT_CANNED_MESSAGES, true));

            List<GdiSmsNotification.CannedListType> requestedTypes = smsNotificationService.getSmsCannedListRequest().getRequestedTypesList();

            populateCannedListTypeMap(requestedTypes);

            GdiSmsNotification.SmsCannedListResponse.Builder builder = GdiSmsNotification.SmsCannedListResponse.newBuilder()
                    .setStatus(GdiSmsNotification.SmsCannedListResponse.ResponseStatus.SUCCESS);
            boolean found = false;
            for (GdiSmsNotification.CannedListType requestedType : requestedTypes) {
                if (this.cannedListTypeMap.containsKey(requestedType)) {
                    found = true;
                    builder.addLists(GdiSmsNotification.SmsCannedList.newBuilder()
                            .addAllResponses(Arrays.asList(Objects.requireNonNull(this.cannedListTypeMap.get(requestedType))))
                            .setType(requestedType)
                    );
                } else {
                    LOG.warn("Missing canned messages data for type {}", requestedType);
                }
            }
            if (!found)
                builder.setStatus(GdiSmsNotification.SmsCannedListResponse.ResponseStatus.GENERIC_ERROR);

            return Smart.newBuilder().setSmsNotificationService(GdiSmsNotification.SmsNotificationService.newBuilder().setSmsCannedListResponse(builder)).build();
        } else {
            LOG.warn("Protobuf smsNotificationService request not implemented: {}", smsNotificationService);
            return null;
        }
    }

    private void populateCannedListTypeMap(List<GdiSmsNotification.CannedListType> requestedTypes) {
        if (this.cannedListTypeMap.isEmpty()) {
            for (GdiSmsNotification.CannedListType type :
                    requestedTypes) {
                String preferencesPrefix = "";
                if (GdiSmsNotification.CannedListType.SMS_MESSAGE_RESPONSE.equals(type))
                    preferencesPrefix = "canned_reply_";
                else if (GdiSmsNotification.CannedListType.PHONE_CALL_RESPONSE.equals(type))
                    preferencesPrefix = "canned_message_dismisscall_";
                else
                    continue;
                final ArrayList<String> messages = new ArrayList<>();
                for (int i = 1; i <= 16; i++) {
                    String message = deviceSupport.getDevicePrefs().getString(preferencesPrefix + i, null);
                    if (message != null && !message.isEmpty()) {
                        messages.add(message);
                    }
                }
                if (!messages.isEmpty())
                    this.cannedListTypeMap.put(type, messages.toArray(new String[0]));
            }
        }
    }

    private void processProtobufFindMyWatchResponse(GdiFindMyWatch.FindMyWatchService findMyWatchService) {
        if (findMyWatchService.hasCancelRequest()) {
            LOG.info("Watch found");
        }
        if (findMyWatchService.hasCancelResponse() || findMyWatchService.hasFindResponse()) {
            LOG.debug("Received findMyWatch response");
        }
        LOG.warn("Unknown FindMyWatchService response: {}", findMyWatchService);
    }

    private boolean processProtobufSettingsService(final GdiRealtimeSettings.RealtimeSettingsService settingsService) {
        boolean processed = false;

        if (settingsService.hasDefinitionResponse()) {
            processed = true;
            final Intent intent = new Intent(GarminRealtimeSettingsFragment.ACTION_SCREEN_DEFINITION);
            intent.putExtra(GarminRealtimeSettingsFragment.EXTRA_PROTOBUF, settingsService.getDefinitionResponse().getDefinition().toByteArray());
            LocalBroadcastManager.getInstance(deviceSupport.getContext()).sendBroadcast(intent);
        }

        if (settingsService.hasStateResponse()) {
            processed = true;
            final Intent intent = new Intent(GarminRealtimeSettingsFragment.ACTION_SCREEN_STATE);
            intent.putExtra(GarminRealtimeSettingsFragment.EXTRA_PROTOBUF, settingsService.getStateResponse().getState().toByteArray());
            LocalBroadcastManager.getInstance(deviceSupport.getContext()).sendBroadcast(intent);
        }

        if (settingsService.hasChangeResponse()) {
            processed = true;
            final Intent intent = new Intent(GarminRealtimeSettingsFragment.ACTION_CHANGE);
            intent.putExtra(GarminRealtimeSettingsFragment.EXTRA_PROTOBUF, settingsService.getChangeResponse().toByteArray());
            LocalBroadcastManager.getInstance(deviceSupport.getContext()).sendBroadcast(intent);
        }

        return processed;
    }

    public ProtobufMessage prepareProtobufRequest(Smart protobufPayload) {
        if (null == protobufPayload)
            return null;
        final int requestId = getNextProtobufRequestId();
        return prepareProtobufMessage(protobufPayload.toByteArray(), GFDIMessage.GarminMessage.PROTOBUF_REQUEST, requestId);
    }

    public void setMaxPacketSize(final int maxPacketSize) {
        // 20 = 18 bytes for ProtobufMessage header + 2 bytes for CRC
        maxChunkSize = maxPacketSize - 20;
        LOG.debug("Protobuf max chunk size: {}", maxChunkSize);
    }

    public ProtobufMessage prepareProtobufResponse(Smart protobufPayload, int requestId) {
        if (null == protobufPayload)
            return null;
        return prepareProtobufMessage(protobufPayload.toByteArray(), GFDIMessage.GarminMessage.PROTOBUF_RESPONSE, requestId);
    }

    private ProtobufMessage prepareProtobufMessage(byte[] bytes, GFDIMessage.GarminMessage garminMessage, int requestId) {
        if (bytes == null || bytes.length == 0)
            return null;
        LOG.info("Preparing protobuf message. Type {}, #{}, {}B: {}", garminMessage, requestId, bytes.length, GB.hexdump(bytes, 0, bytes.length));

        if (bytes.length > maxChunkSize) {
            chunkedFragmentsMap.put(requestId, new ProtobufFragment(bytes));
            return new ProtobufMessage(garminMessage,
                    requestId,
                    0,
                    bytes.length,
                    maxChunkSize,
                    ArrayUtils.subarray(bytes, 0, maxChunkSize));
        }
        return new ProtobufMessage(garminMessage, requestId, 0, bytes.length, bytes.length, bytes);
    }

    public ProtobufMessage setCannedMessages(CannedMessagesSpec cannedMessagesSpec) {
        final GdiSmsNotification.CannedListType cannedListType;
        switch (cannedMessagesSpec.getType()) {
            case CannedMessagesSpec.TYPE_REJECTEDCALLS:
                cannedListType = GdiSmsNotification.CannedListType.PHONE_CALL_RESPONSE;
                break;
            case CannedMessagesSpec.TYPE_GENERIC:
            case CannedMessagesSpec.TYPE_NEWSMS:
                cannedListType = GdiSmsNotification.CannedListType.SMS_MESSAGE_RESPONSE;
                break;
            default:
                LOG.warn("Unknown canned messages type, ignoring.");
                return null;
        }

        this.cannedListTypeMap.put(cannedListType, cannedMessagesSpec.getCannedMessages());

        Smart smart = Smart.newBuilder()
                .setSmsNotificationService(GdiSmsNotification.SmsNotificationService.newBuilder()
                        .setSmsCannedListChangedNotification(
                                GdiSmsNotification.SmsCannedListChangedNotification.newBuilder().addChangedTypes(cannedListType)
                        )
                ).build();

        return prepareProtobufRequest(smart);
    }

    public FileSyncServiceHandler getFileSyncServiceHandler() {
        return fileSyncServiceHandler;
    }

    public PushNotificationHandler getPushNotificationHandler() {
        return pushNotificationHandler;
    }

    public AppConfigHandler getAppConfigHandler() {
        return appConfigHandler;
    }

    ExploreSyncHandler getExploreSyncHandler() {
        return exploreSyncHandler;
    }

    private class ProtobufFragment {
        private final byte[] fragmentBytes;
        private final int totalLength;

        public ProtobufFragment(byte[] fragmentBytes) {
            this.fragmentBytes = fragmentBytes;
            this.totalLength = fragmentBytes.length;
        }

        public ProtobufFragment(ProtobufMessage message) {
            if (message.getDataOffset() != 0)
                throw new IllegalArgumentException("Cannot create fragment if message is not the first of the sequence");
            this.fragmentBytes = message.getMessageBytes();
            this.totalLength = message.getTotalProtobufLength();
        }

        public ProtobufFragment(ProtobufFragment existing, ProtobufMessage toMerge) {
            if (toMerge.getDataOffset() != existing.fragmentBytes.length)
                throw new IllegalArgumentException("Cannot merge fragment: incoming message has different offset than needed");
            this.fragmentBytes = ArrayUtils.addAll(existing.fragmentBytes, toMerge.getMessageBytes());
            this.totalLength = existing.totalLength;
        }

        public ProtobufMessage getNextChunk(ProtobufStatusMessage protobufStatusMessage) {
            int start = protobufStatusMessage.getDataOffset() + maxChunkSize;
            int length = Math.min(maxChunkSize, this.fragmentBytes.length - start);

            return new ProtobufMessage(protobufStatusMessage.getMessageType(),
                    protobufStatusMessage.getRequestId(),
                    start,
                    this.totalLength,
                    length,
                    ArrayUtils.subarray(this.fragmentBytes, start, start + length));
        }

        public boolean isComplete() {
            return totalLength == fragmentBytes.length;
        }
    }


}
