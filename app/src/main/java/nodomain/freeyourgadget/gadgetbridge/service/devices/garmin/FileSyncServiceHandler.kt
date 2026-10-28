/*  Copyright (C) 2025-2026 José Rebelo, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin

import com.google.common.collect.Lists
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTypes
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiFileAccess
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmartProto.Smart
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.FileType.FILETYPE
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.FileDownloadedDeviceEvent
import nodomain.freeyourgadget.gadgetbridge.util.protobuf.buildWith
import org.slf4j.LoggerFactory

class FileSyncServiceHandler(val deviceSupport: GarminSupport) {
    private var nextPageId: Int? = null
    private var cursorId: Int? = null

    fun handle(fileSyncService: GdiFileAccess.Service): GdiFileAccess.Service? {
        return when {
            fileSyncService.hasItemAddedNotification() -> handleItemAddedNotification(fileSyncService.itemAddedNotification)
            fileSyncService.hasItemListResponse() -> handleItemListResponse(fileSyncService.itemListResponse)
            fileSyncService.hasPullItemResponse() -> handlePullItemResponse(fileSyncService.pullItemResponse)
            fileSyncService.hasModifyFlagsResponse() -> handleModifyFlagsResponse(fileSyncService.modifyFlagsResponse)
            fileSyncService.hasTransferStatusRequest() -> handleTransferStatusRequest(fileSyncService.transferStatusRequest)
            fileSyncService.hasItemUpdatedNotification() -> handleItemUpdatedNotification(fileSyncService.itemUpdatedNotification)
            fileSyncService.hasSyncButtonNotification() -> handleSyncButtonNotification(fileSyncService.syncButtonNotification)
            else -> {
                LOG.warn("Unhandled file sync service: {}", fileSyncService)
                return null
            }
        }
    }

    private fun handleSyncButtonNotification(startSyncNotification: GdiFileAccess.SyncButtonNotification): GdiFileAccess.Service? {
        LOG.debug("Got start sync notification: {}", startSyncNotification)
        deviceSupport.onFetchRecordedData(RecordedDataTypes.TYPE_ALL)
        return null
    }


    private fun handleItemUpdatedNotification(fileUpdateNotification: GdiFileAccess.ItemUpdatedNotification): GdiFileAccess.Service? {
        LOG.debug("Got file updated flags: {}", fileUpdateNotification)
        // no action required
        return null
    }

    private fun handleModifyFlagsResponse(fileModifiedFlags: GdiFileAccess.ModifyFlagsResponse): GdiFileAccess.Service? {
        LOG.debug("Got file modified flags: {}", fileModifiedFlags)
        // no action required
        return null
    }

    private fun handleTransferStatusRequest(transferStatusRequest: GdiFileAccess.TransferStatusRequest): GdiFileAccess.Service? {
        LOG.debug("Got transfer status request: {}", transferStatusRequest)
        val response = GdiFileAccess.TransferStatusResponse.newBuilder().buildWith {
            nextTransferPriority = 1
        }
        return GdiFileAccess.Service.newBuilder().buildWith {
            transferStatusResponse = response
        }
    }

    private fun handleItemAddedNotification(newFileNotification: GdiFileAccess.ItemAddedNotification): GdiFileAccess.Service? {
        LOG.debug("Got new file notification: {}", newFileNotification)

        for (file in newFileNotification.itemsList) {
            if (!file.hasDataType()) {
                LOG.warn("New file has no type: {}", file)
                continue
            }

            conditionallyDownload(file, null)
        }
        return null
    }

    private fun handlePullItemResponse(fileResponse: GdiFileAccess.PullItemDataResponse): GdiFileAccess.Service? {
        LOG.debug("Got file response: {}", fileResponse)

        if (fileResponse.result != GdiFileAccess.ItemAccessResult.SUCCESS) {
            LOG.warn("File download failed with status={} - {}", fileResponse.result, fileResponse)
            // Signal to the support class that the download failed so it can also continue to the next one
            val fileDownloadedDeviceEvent = FileDownloadedDeviceEvent()
            fileDownloadedDeviceEvent.success = false
            deviceSupport.evaluateGBDeviceEvent(fileDownloadedDeviceEvent)
        } else {
            deviceSupport.downloadFileFromServiceV2(fileResponse.transferHandle)
        }

        return null
    }

    private fun handleItemListResponse(fileListResponse: GdiFileAccess.ItemListResponse): GdiFileAccess.Service? {
        if (!fileListResponse.hasStatus() || fileListResponse.status != GdiFileAccess.ItemListResponse.Status.SUCCESS) {
            LOG.warn(
                "Handling file list response with failed status={} - {}",
                if (fileListResponse.hasStatus()) fileListResponse.status else null,
                fileListResponse
            )
        } else {
            LOG.debug(
                "Handling file list response with status={}, files={}, cursorId={}, nextPageId={}",
                if (fileListResponse.hasStatus()) fileListResponse.status else null,
                fileListResponse.itemsList.size,
                if (fileListResponse.hasSessionId()) fileListResponse.sessionId else null,
                if (fileListResponse.hasNextTransactionId()) fileListResponse.nextTransactionId else null,
            )
        }

        // Only the first entry for a type seems to contain the type name, so keep track of them
        val nameMap: MutableMap<Int?, String?> = HashMap()
        for (file in fileListResponse.itemsList) {
            if (!file.hasDataType()) {
                LOG.warn("Ignoring listed file without type information: {}", file)
                continue
            }

            if (file.dataType.hasGdxmlStringKey() && file.dataType.hasGdxmlString()) {
                nameMap.put(file.dataType.gdxmlStringKey, file.dataType.gdxmlString)
            }

            var typeName = if (file.dataType.hasGdxmlString()) {
                file.dataType.gdxmlString
            } else if (file.dataType.hasGdxmlStringKey()) {
                nameMap[file.dataType.gdxmlStringKey]
            } else {
                null
            }

            conditionallyDownload(file, typeName)
        }

        // #5461 - some watches to not send the next page ID
        // however, from previous logs, it always seems to match the max seen across all sent items, so attempt
        // to fall back to that as a workaround so we can fetch the subsequent files
        nextPageId = fileListResponse.nextTransactionId

        // The device sets cursor_id when there are more items pending for *this same* listing
        // request. Keep pulling pages within this cursor immediately instead of stopping after
        // one page - otherwise anything past the first ~100 items (which can easily be all SPORTS
        // backlog) is never seen.
        cursorId = if (fileListResponse.hasSessionId()) fileListResponse.sessionId else null
        if (cursorId != null) {
            deviceSupport.sendProtobufRequest(
                "continue file list",
                Smart.newBuilder().setFileAccessService(requestFileList()).build()
            )
        }

        return null
    }

    fun requestFileList(): GdiFileAccess.Service {
        LOG.debug("Requesting file list starting at page {} (cursorId={})", nextPageId, cursorId)

        val fileListRequestBuilder = GdiFileAccess.ItemListRequest.newBuilder().apply {
            // Exclusion flags? If we omit this, it sends back already synced files going back months.
            addExcludeAllFlags(GdiDataTypes.UUID.newBuilder().setMostSignificant(FLAGS_SYNCED).setLeastSignificant(FLAGS_SYNCED).build())
            addGetFlags(GdiDataTypes.UUID.newBuilder().setMostSignificant(FLAGS_SYNCED).setLeastSignificant(FLAGS_SYNCED).build())
        }

        val currentcursorId = cursorId
        if (currentcursorId != null) {
            fileListRequestBuilder.sessionId = currentcursorId
        } else {
            nextPageId?.let { fileListRequestBuilder.transactionId = it }
        }

        return GdiFileAccess.Service.newBuilder().buildWith {
            itemListRequest = fileListRequestBuilder.build()
        }
    }

    fun requestFile(fileToRequest: GdiFileAccess.ItemReference): GdiFileAccess.Service {
        LOG.debug(
            "Requesting file: {}/{} ({})",
            fileToRequest.uid.mostSignificant,
            fileToRequest.uid.leastSignificant,
            fileToRequest.dataType.gdxmlString
        )
        return GdiFileAccess.Service.newBuilder().buildWith {
            pullItemRequest = GdiFileAccess.PullItemDataRequest.newBuilder().buildWith {
                item = fileToRequest
                priority = 24
                addSupportedTransports(GdiFileAccess.TransportProtocol.MULTILINK_TRANSPORT_PIPE)
                offset = 0
                requestedCompressionWindow = 15
            }
        }
    }

    fun markSynced(syncFile: GdiFileAccess.ItemReference): GdiFileAccess.Service? {
        val fetchUnknownFiles = deviceSupport.devicePrefs.fetchUnknownFiles
        if (fetchUnknownFiles) {
            // Since some of the unknown files are not really supposed to be marked as synced (eg. settings, courses, locations)
            // let's avoid sending the command if it's not a file that we process
            if (syncFile.dataType.gdxmlString == null) {
                LOG.warn("Will not mark {}/{} as synced - unknown type", syncFile.uid.mostSignificant, syncFile.uid.leastSignificant)
                return null
            }
            val fileType = FileType.FILETYPE.findByTypeName(syncFile.dataType.gdxmlString);
            if (fileType == null || !fileType.pull) {
                LOG.warn(
                    "Will not mark {}/{} ({}) as synced - not a file to process",
                    syncFile.uid.mostSignificant,
                    syncFile.uid.leastSignificant,
                    syncFile.dataType.gdxmlStringKey
                )
                return null
            }
        }

        return GdiFileAccess.Service.newBuilder().buildWith {
            modifyFlagsRequest = GdiFileAccess.ModifyFlagsRequest.newBuilder().buildWith {
                uid = syncFile.uid
                addSetFlags(GdiDataTypes.UUID.newBuilder().setMostSignificant(FLAGS_SYNCED).setLeastSignificant(FLAGS_SYNCED).build())
            }
        }
    }

    private fun conditionallyDownload(file: GdiFileAccess.ItemReference, rawTypeName: String?) {
        var computedName: String? = null;
        if (rawTypeName == null || rawTypeName.length < 1) {
            if (file.hasDataType() && file.dataType.hasGdxmlString()) {
                computedName = file.dataType.gdxmlString;
            } else if (file.hasDataType() && file.dataType.hasFormat()) {
                if (file.dataType.gdxmlStringKey == 3) {
                    computedName = FILETYPE.DEVICE_XML.name
                } else {
                    // generate a name for fetchUnknownFiles
                    computedName = "TYPE_" + file.dataType.gdxmlStringKey;
                }
            } else {
                LOG.warn("Ignoring file with no type name: {}", file)
                return
            }
        } else {
            computedName = rawTypeName;
        }

        val fileType = FileType.FILETYPE.findByTypeName(computedName)
        val typeName = if (fileType != null) {
            if (fileType.typeName != null) {
                fileType.typeName
            } else {
                fileType.name
            }
        } else {
            computedName
        }

        if (!deviceSupport.devicePrefs.fetchUnknownFiles) {
            if (fileType == null || !fileType.pull) {
                LOG.warn("Ignoring file: {} {}", typeName, file)
                return
            }
        }

        // ensure the to-be-downloaded file has a good type.name
        // used by a later processing stage for non-FIT files
        val actualFile: GdiFileAccess.ItemReference
        if (!file.hasDataType() || !file.dataType.hasGdxmlString() || !typeName.contentEquals(file.dataType.gdxmlString)) {
            val builder = file.toBuilder()
            builder.setDataType(GdiFileAccess.DataType.newBuilder().setGdxmlString(typeName).build())
            actualFile = builder.build();
        } else {
            actualFile = file
        }

        LOG.debug(
            "Adding file to download: {}/{} ({})",
            actualFile.uid.mostSignificant,
            actualFile.uid.leastSignificant,
            actualFile.dataType.gdxmlString
        )
        deviceSupport.addFileToDownloadList(actualFile)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(FileSyncServiceHandler::class.java)

        private const val FLAGS_SYNCED = 42405L
    }
}
