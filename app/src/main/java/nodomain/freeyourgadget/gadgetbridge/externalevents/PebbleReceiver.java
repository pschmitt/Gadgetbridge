/*  Copyright (C) 2015-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.externalevents;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.CompiledNotificationAppConfig;
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppConfig;
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRepository;
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRules;
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationText;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class PebbleReceiver extends BroadcastReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(PebbleReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {

        Prefs prefs = GBApplication.getPrefs();
        if ("never".equals(prefs.getString("notification_mode_pebblemsg", "when_screen_off"))) {
            return;
        }
        if ("when_screen_off".equals(prefs.getString("notification_mode_pebblemsg", "when_screen_off"))) {
            PowerManager powermanager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powermanager.isScreenOn()) {
                return;
            }
        }

        String messageType = intent.getStringExtra("messageType");
        if (!"PEBBLE_ALERT".equals(messageType)) {
            LOG.info("non PEBBLE_ALERT message type not supported");
            return;
        }

        if (!intent.hasExtra("notificationData")) {
            LOG.info("missing notificationData extra");
            return;
        }

        NotificationSpec notificationSpec = new NotificationSpec();

        String notificationData = intent.getStringExtra("notificationData");
        try {
            JSONArray notificationJSON = new JSONArray(notificationData);
            notificationSpec.setTitle(notificationJSON.getJSONObject(0).getString("title"));
            notificationSpec.setBody(notificationJSON.getJSONObject(0).getString("body"));
        } catch (JSONException e) {
            LOG.warn("exception in onReceive", e);
            return;
        }

        if (notificationSpec.getTitle() != null) {
            notificationSpec.setType(NotificationType.UNKNOWN);
            final String sender = intent.getStringExtra("sender");
            final NotificationAppRepository repository = NotificationAppRepository.INSTANCE;
            final String senderPackage = sender != null ? repository.pebbleSenderToPackageName(sender) : null;
            notificationSpec.setSourceAppId(senderPackage);
            final CompiledNotificationAppConfig appConfig = repository.get(senderPackage);
            if (!NotificationAppRules.INSTANCE.shouldSendPebble(appConfig, repository.getDefaultAction())) {
                LOG.info("Ignoring Pebble message, application {} is not allowed to send Pebble messages", sender);
                return;
            }

            final CompiledNotificationAppConfig allAppsConfig = repository.get(NotificationAppConfig.ALL_APPS);
            if (!NotificationAppRules.INSTANCE.passesFilters(allAppsConfig, appConfig, notificationSpec.getTitle(), notificationSpec.getBody())) {
                return;
            }

            final NotificationText replacedText = NotificationAppRules.INSTANCE.applyReplacements(
                    allAppsConfig,
                    appConfig,
                    notificationSpec.getTitle(),
                    notificationSpec.getBody()
            );
            notificationSpec.setTitle(replacedText.getTitle());
            notificationSpec.setBody(replacedText.getBody());

            if ("Conversations".equals(sender)) {
                notificationSpec.setType(NotificationType.CONVERSATIONS);
            }
            else if ("OsmAnd".equals(sender)) {
                notificationSpec.setType(NotificationType.GENERIC_NAVIGATION);
            }
            GBApplication.deviceService().onNotification(notificationSpec);
        }
    }
}
