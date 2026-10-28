/*  Copyright (C) 2023-2024 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.services;

import android.util.Pair;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.greenrobot.dao.query.QueryBuilder;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.GBException;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.entities.CalendarSyncState;
import nodomain.freeyourgadget.gadgetbridge.entities.CalendarSyncStateDao;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.externalevents.CalendarReceiver;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ZeppOsSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.AbstractZeppOsService;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ZeppOsTransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class ZeppOsCalendarService extends AbstractZeppOsService {
    private static final Logger LOG = LoggerFactory.getLogger(ZeppOsCalendarService.class);

    public static final short ENDPOINT = 0x0007;

    public static final byte CMD_CAPABILITIES_REQUEST = 0x01;
    public static final byte CMD_CAPABILITIES_RESPONSE = 0x02;
    public static final byte CMD_EVENTS_REQUEST = 0x05;
    public static final byte CMD_EVENTS_RESPONSE = 0x06;
    public static final byte CMD_CREATE_EVENT = 0x07;
    public static final byte CMD_CREATE_EVENT_ACK = 0x08;
    public static final byte CMD_DELETE_EVENT = 0x09;
    public static final byte CMD_DELETE_EVENT_ACK = 0x0a;

    public static final String PREF_VERSION = "zepp_os_calendar_version";

    private int version = -1;

    public ZeppOsCalendarService(final ZeppOsSupport support) {
        super(support, false);
    }

    @Override
    public short getEndpoint() {
        return ENDPOINT;
    }

    @Override
    public void initialize(final ZeppOsTransactionBuilder builder) {
        requestCapabilities(builder);
    }

    public void requestCapabilities(final ZeppOsTransactionBuilder builder) {
        write(builder, CMD_CAPABILITIES_REQUEST);
    }

    @Override
    public void handlePayload(final byte[] payload) {
        switch (payload[0]) {
            case CMD_CAPABILITIES_RESPONSE:
                version = payload[1];
                getSupport().evaluateGBDeviceEvent(new GBDeviceEventUpdatePreferences(PREF_VERSION, version));
                if (version != 1 && version != 3) {
                    LOG.warn("Unsupported calendar service version {}", version);
                    return;
                }
                LOG.info("Calendar service version={}", version);
                break;
            case CMD_EVENTS_RESPONSE:
                LOG.info("Got calendar events from band");
                try {
                    decodeAndUpdateCalendarEvents(payload);
                } catch (GBException e) {
                    LOG.error("zepp decodeAndUpdateCalendarEvents error", e);
                }
                return;
            case CMD_CREATE_EVENT_ACK:
                LOG.info("Calendar create event ACK, status = {}", payload[1]);
                return;
            case CMD_DELETE_EVENT_ACK:
                LOG.info("Calendar delete event ACK, status = {}", payload[1]);
                return;
            default:
                LOG.warn("Unexpected calendar payload byte {}", String.format("0x%02x", payload[0]));
        }
    }


    public void syncCalendar() {
        LOG.info("Requesting calendar events from band");

        write("request calendar events", new byte[]{CMD_EVENTS_REQUEST, 0x00, 0x00});
    }

    public void addEvent(final CalendarEventSpec calendarEventSpec) {
        if (calendarEventSpec.getType() != CalendarEventSpec.TYPE_UNKNOWN) {
            LOG.warn("Unsupported calendar event type {}", calendarEventSpec.getType());
            return;
        }

        LOG.info("Sending calendar event {} to band", calendarEventSpec.getId());

        int length = 34;
        if (calendarEventSpec.getTitle() != null) {
            length += calendarEventSpec.getTitle().getBytes(StandardCharsets.UTF_8).length;
        }
        if (calendarEventSpec.getDescription() != null) {
            length += calendarEventSpec.getDescription().getBytes(StandardCharsets.UTF_8).length;
        }

        if (version == 3) {
            if (calendarEventSpec.getLocation() != null) {
                length += calendarEventSpec.getLocation().getBytes(StandardCharsets.UTF_8).length;
            }
            // Extra null byte at the end
            length++;
        }

        final ByteBuffer buf = ByteBuffer.allocate(length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(CMD_CREATE_EVENT);
        buf.putInt((int) calendarEventSpec.getId());

        if (calendarEventSpec.getTitle() != null) {
            buf.put(calendarEventSpec.getTitle().getBytes(StandardCharsets.UTF_8));
        }
        buf.put((byte) 0x00);

        if (calendarEventSpec.getDescription() != null) {
            buf.put(calendarEventSpec.getDescription().getBytes(StandardCharsets.UTF_8));
        }
        buf.put((byte) 0x00);

        buf.putInt(calendarEventSpec.getTimestamp());
        buf.putInt(calendarEventSpec.getTimestamp() + calendarEventSpec.getDurationInSeconds());

        // Remind
        final boolean syncReminders = getDevicePrefs().getBoolean(DeviceSettingsPreferenceConst.PREF_CALENDAR_SYNC_EVENT_REMINDERS, false);
        if (syncReminders && calendarEventSpec.getReminders() != null && !calendarEventSpec.getReminders().isEmpty()) {
            buf.putInt((int) (calendarEventSpec.getReminders().get(0) / 1000L));
        } else {
            buf.putInt(0);
        }

        // Repeat
        buf.put((byte) 0x00); // ?
        buf.put((byte) 0x00); // ?
        buf.put((byte) 0x00); // ?
        buf.put((byte) 0x00); // ?
        // ?
        buf.put((byte) 0xff); // ?
        buf.put((byte) 0xff); // ?
        buf.put((byte) 0xff); // ?
        buf.put((byte) 0xff); // ?
        buf.put(bool(calendarEventSpec.getAllDay()));
        buf.put((byte) 0x00); // ?
        buf.put((byte) 130); // ?
        buf.put((byte) 0x00); // ?
        buf.put((byte) 0x00); // ?
        buf.put((byte) 0x00); // ?
        buf.put((byte) 0x00); // ?
        // TODO: Description here

        if (version == 3) {
            if (calendarEventSpec.getLocation() != null) {
                buf.put(calendarEventSpec.getLocation().getBytes(StandardCharsets.UTF_8));
            }
            buf.put((byte) 0x00);
        }

        write("add calendar event", buf.array());
    }

    public void deleteEvent(final byte type, final long id) {
        if (type != CalendarEventSpec.TYPE_UNKNOWN) {
            LOG.warn("Unsupported calendar event type {}", type);
            return;
        }

        LOG.info("Deleting calendar event {} from band", id);

        final ByteBuffer buf = ByteBuffer.allocate(5);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(CMD_DELETE_EVENT);
        buf.putInt((int) id);

        write("delete calendar event", buf.array());
    }

    private void decodeAndUpdateCalendarEvents(final byte[] payload) throws GBException {
        final int numEvents = payload[1] + (payload[2] << 8);

        LOG.debug("Zepp calendar events count {}", numEvents);

        if (payload.length < 1 + numEvents * 34) {
            LOG.warn("Unexpected payload length of {} for {} calendar events", payload.length, numEvents);
            return;
        }

        /**
         * Events ids on the watch.
         */
        final List<Integer> idsWatch = new ArrayList<>(numEvents);

        int i = 3;
        for (int j = 0; j < numEvents; j++) {
            if (payload.length - i < 34) {
                LOG.error("Not enough bytes remaining to parse a calendar event ({})", payload.length - i);
                return;
            }

            final int eventId = BLETypeConversions.toUint32(payload, i);
            idsWatch.add(eventId);
            i += 4;

            final Pair<String, Integer> title = StringUtils.untilNullTerminatorWithLength(payload, i);
            if (title == null) {
                LOG.error("Failed to decode title");
                return;
            }
            i += title.second + 1;

            final Pair<String, Integer> description = StringUtils.untilNullTerminatorWithLength(payload, i);
            if (description == null) {
                LOG.error("Failed to decode description");
                return;
            }
            i += description.second + 1;

            final int startTime = BLETypeConversions.toUint32(payload, i);
            i += 4;

            final int endTime = BLETypeConversions.toUint32(payload, i);
            i += 4;

            final int reminderTime = BLETypeConversions.toUint32(payload, i);
            i += 4;

            i += 8;

            boolean allDay = (payload[i] == 0x01);
            i++;

            final Pair<String, Integer> unknown = StringUtils.untilNullTerminatorWithLength(payload, i);
            if (unknown == null) {
                LOG.error("Failed to decode unknown");
                return;
            }
            i += unknown.second + 1;

            if (!unknown.first.isEmpty()) {
                LOG.debug("unknown is not empty: {}", unknown);
            }

            i += 5;

            Pair<String, Integer> address = new Pair<>("", 0);
            if (version == 3) {
                address = StringUtils.untilNullTerminatorWithLength(payload, i);
                if (address == null) {
                    LOG.error("Failed to decode address");
                    return;
                }
                i += address.second + 1;
            }


            LOG.info("Calendar Event {}: {}. address = {}, all day = {}", eventId, title.first, address.first, allDay);
        }

        if (i != payload.length) {
            LOG.warn("Unexpected calendar events payload trailer, {} bytes were not consumed", payload.length - i);
        }

        // Copied from BangleJSDeviceSupport

        ArrayList<Integer> idsList = new ArrayList<>(numEvents);

        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            DaoSession session = dbHandler.getDaoSession();
            Long deviceId = DBHelper.getDevice(getSupport().getDevice(), session).getId();

            QueryBuilder<CalendarSyncState> qb = session.getCalendarSyncStateDao().queryBuilder();
            //FIXME just use that and don't query every time?
            List<CalendarSyncState> states = qb.where(
                CalendarSyncStateDao.Properties.DeviceId.eq(deviceId)).build().list();

            LOG.info("zepp calendar sync: "+ idsWatch.size() +" events on the device, "+ states.size() +" on our db");
            for (int j = 0; j < idsWatch.size(); j++) {
                int id = idsWatch.get(j);
                qb = session.getCalendarSyncStateDao().queryBuilder(); //is this needed again?
                CalendarSyncState calendarSyncState = qb.where(
                    qb.and(CalendarSyncStateDao.Properties.DeviceId.eq(deviceId),
                        CalendarSyncStateDao.Properties.CalendarEntryId.eq(id))).build().unique();
                if(calendarSyncState == null) {
                    deleteEvent(CalendarEventSpec.TYPE_UNKNOWN, id);
                    LOG.info("event id="+ id +" is on device id="+ deviceId +", removing it there");
                } else {
                    //used for later, no need to check twice the ones that do not match
                    idsList.add(id);
                }
            }

            //remove all elements not in ids from database (we don't have them)
            for(CalendarSyncState calendarSyncState : states) {
                long id = calendarSyncState.getCalendarEntryId();
                if(!idsList.contains((int) id)) {
                    qb = session.getCalendarSyncStateDao().queryBuilder(); //is this needed again?
                    qb.where(qb.and(CalendarSyncStateDao.Properties.DeviceId.eq(deviceId),
                            CalendarSyncStateDao.Properties.CalendarEntryId.eq(id)))
                        .buildDelete().executeDeleteWithoutDetachingEntities();
                    LOG.info("event id={} is not on device id={}, removing from our db", id, deviceId);
                }
            }

        } catch (Exception e1) {
            GB.toast("Database Error while forcefully syncing Calendar", Toast.LENGTH_SHORT, GB.ERROR, e1);
        }

        CalendarReceiver.forceSync(this.getSupport().getDevice());
    }
}
