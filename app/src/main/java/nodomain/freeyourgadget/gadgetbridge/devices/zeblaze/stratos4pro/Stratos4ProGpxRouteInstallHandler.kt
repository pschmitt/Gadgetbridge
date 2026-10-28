/*  Copyright (C) 2026 Vladimir Tasic

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
package nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.stratos4pro

import android.content.Context
import android.net.Uri
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.install.InstallActivity
import nodomain.freeyourgadget.gadgetbridge.devices.GpxRouteInstallHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.GenericItem
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitCourse
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitRecord
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils
import nodomain.freeyourgadget.gadgetbridge.util.UriHelper
import nodomain.freeyourgadget.gadgetbridge.util.gpx.model.GpxFile
import nodomain.freeyourgadget.gadgetbridge.util.gpx.model.GpxTrack
import nodomain.freeyourgadget.gadgetbridge.util.gpx.model.GpxTrackPoint
import nodomain.freeyourgadget.gadgetbridge.util.gpx.model.GpxTrackSegment
import org.slf4j.LoggerFactory

/** A GPX route, or a FIT course or activity turned into the same model for the GPX installer. */
class Stratos4ProGpxRouteInstallHandler(uri: Uri, context: Context) : GpxRouteInstallHandler(uri, context) {
    private val fitRoute: GpxFile? = if (super.getGpxFile() == null) readFit(uri, context) else null

    override fun getGpxFile(): GpxFile? = super.getGpxFile() ?: fitRoute

    override fun isValid() = gpxFile != null

    override fun getName(): String = super.getName().ifBlank { fitRoute?.name.orEmpty() }

    override fun isCompatible(device: GBDevice) = device.deviceCoordinator is Stratos4ProCoordinator

    override fun validateInstallation(installActivity: InstallActivity, device: GBDevice) {
        super.validateInstallation(installActivity, device)
        // The base only knows its own GPX parse; a FIT route passes the same device checks.
        if (fitRoute == null || device.isBusy || !isCompatible(device) || !device.isInitialized) return
        val kind = mContext.getString(R.string.kind_gpx_route)
        val coordinator = device.deviceCoordinator
        val item = GenericItem(mContext.getString(R.string.installhandler_firmware_name, mContext.getString(coordinator.deviceNameResource), kind, name))
        item.setIcon(coordinator.defaultIconResource)
        installActivity.setInfoText(mContext.getString(R.string.fw_upgrade_notice, kind))
        installActivity.setInstallItem(item)
        installActivity.setInstallEnabled(true)
    }

    private fun readFit(uri: Uri, context: Context): GpxFile? = try {
        val helper = UriHelper.get(uri, context)
        fitToGpx(helper.openInputStream().use { FileUtils.readAll(it, 10 * 1024 * 1024) }, helper.fileName.substringBeforeLast('.'))
    } catch (e: Exception) {
        LOG.warn("Not a usable FIT route: {}", e.toString())
        null
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(Stratos4ProGpxRouteInstallHandler::class.java)

        /** The `record` positions of a FIT course or activity as one track; null if not FIT or under 2 points. */
        internal fun fitToGpx(bytes: ByteArray, fallbackName: String?): GpxFile? {
            if (bytes.size < 12 || String(bytes, 8, 4, Charsets.US_ASCII) != ".FIT") return null
            val records = FitFile.parseIncoming(bytes).records
            val points = records.filterIsInstance<FitRecord>().mapNotNull { r ->
                GpxTrackPoint(r.positionLong ?: return@mapNotNull null, r.positionLat ?: return@mapNotNull null, 0.0, null)
            }
            if (points.size < 2) return null
            val name = records.filterIsInstance<FitCourse>().firstNotNullOfOrNull { it.name } ?: fallbackName
            return GpxFile(name, null, null, listOf(GpxTrack(name, null, listOf(GpxTrackSegment(points, emptyList())))), emptyList())
        }
    }
}
