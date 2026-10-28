/*  Copyright (C) 2017-2024 Carsten Pfeiffer, Daniele Gobbetti, Felix
    Konstantin Maurer, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.externalevents

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.PeriodicDbExporter
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import nodomain.freeyourgadget.gadgetbridge.util.backup.PeriodicZipExporter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action && Intent.ACTION_MY_PACKAGE_REPLACED != intent.action) {
            LOG.warn("Ignoring unexpected action {}", intent.action)
            return
        }

        LOG.info("Received {}", intent.action)

        if (!GBApplication.getPrefs().autoStart) {
            LOG.info("Not starting, auto-start is disabled")
            return
        }

        LOG.info("Boot or reinstall completed, starting Gadgetbridge")

        if (GBApplication.getPrefs().getBoolean(GBPrefs.AUTO_CONNECT_BLUETOOTH, false)) {
            LOG.info("Auto-connect is enabled, attempting to connect")
            GBApplication.deviceService().connect()
        } else {
            LOG.info("Not connecting, auto-connect is disabled")
        }

        LOG.debug("Enabling periodic db exporter")
        PeriodicDbExporter.scheduleNextExecution(context)

        LOG.debug("Enabling periodic zip exporter")
        PeriodicZipExporter.scheduleNextExecution(context)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(AutoStartReceiver::class.java)
    }
}
