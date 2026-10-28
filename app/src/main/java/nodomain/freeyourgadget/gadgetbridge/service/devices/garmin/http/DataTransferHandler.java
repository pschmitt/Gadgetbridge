package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http;

import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTransfer.DataDownloadRequest;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTransfer.DataDownloadResponse;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTransfer.DataTransferService;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTransfer.FileAccessItemRequest;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTransfer.FileAccessItemResponse;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTypes;

public class DataTransferHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DataTransferHandler.class);
    private static final AtomicInteger idCounter = new AtomicInteger((new Random()).nextInt(Integer.MAX_VALUE / 2));
    private static final Map<Integer, Data> dataById = new HashMap<>();
    private static final Map<Integer, ChunkInfo> unprocessedChunksByRequestId = new HashMap<>();
    private static final Random random = new Random();

    public DataTransferService handle(
            final DataTransferService dataTransferService,
            final int requestId
    ) {
        if (dataTransferService.hasDataDownloadRequest()) {
            final DataDownloadResponse dataDownloadResponse
                    = handleDataDownloadRequest(dataTransferService.getDataDownloadRequest(), requestId);
            if (dataDownloadResponse != null) {
                return DataTransferService.newBuilder()
                        .setDataDownloadResponse(dataDownloadResponse)
                        .build();
            }
            return null;
        }

        LOG.warn("Unsupported data transfer service request: {}", dataTransferService);

        return null;
    }

    public DataDownloadResponse handleDataDownloadRequest(
            final DataDownloadRequest dataDownloadRequest,
            final int requestId
    ) {
        final int dataId = dataDownloadRequest.getId();
        final int offset = dataDownloadRequest.getOffset();
        LOG.debug("Received data download request (id: {}, offset: {})", dataId, offset);
        final Data data = dataById.get(dataId);
        if (data == null) {
            LOG.error("Device requested data with invalid id: {}", dataId);
            return DataDownloadResponse.newBuilder()
                    .setStatus(DataDownloadResponse.Status.INVALID_ID)
                    .setId(dataId)
                    .setOffset(offset)
                    .build();
        }
        final int maxChunkSize = dataDownloadRequest.hasMaxChunkSize() ? dataDownloadRequest.getMaxChunkSize() : Integer.MAX_VALUE;
        final byte[] chunk = data.getDataChunk(offset, maxChunkSize);
        if (chunk == null) {
            LOG.error("Device requested data with invalid offset: {}", offset);
            return DataDownloadResponse.newBuilder()
                    .setStatus(DataDownloadResponse.Status.INVALID_OFFSET)
                    .setId(dataId)
                    .setOffset(offset)
                    .build();
        }
        unprocessedChunksByRequestId.put(requestId, new ChunkInfo(dataId, offset, offset + chunk.length));
        return DataDownloadResponse.newBuilder()
                .setStatus(DataDownloadResponse.Status.SUCCESS)
                .setId(dataId)
                .setOffset(offset)
                .setPayload(ByteString.copyFrom(chunk))
                .build();
    }

    public FileAccessItemResponse handleFileAccessItemRequest(final FileAccessItemRequest request) {
        final int dataId = request.getDataXferId();
        LOG.debug("Received file access item request (id: {}, latency: {})", dataId, request.getExpectedLatency());
        if (!dataById.containsKey(dataId)) {
            LOG.error("Device requested file access to data with unknown id: {}", dataId);
            return FileAccessItemResponse.newBuilder()
                    .setStatus(FileAccessItemResponse.Status.INVALID_ID)
                    .setDataXferId(dataId)
                    .build();
        }
        return FileAccessItemResponse.newBuilder()
                .setStatus(FileAccessItemResponse.Status.OK)
                .setDataXferId(dataId)
                .setFileAccessItemUid(GdiDataTypes.UUID.newBuilder()
                        .setMostSignificant(dataId)
                        .setLeastSignificant(random.nextLong())
                        .build())
                .build();
    }

    @Nullable
    public static byte[] getData(final int dataId) {
        final Data data = dataById.get(dataId);
        return data != null ? data.data : null;
    }

    public static void onDataPushed(final int dataId, final boolean success) {
        final Data data = dataById.remove(dataId);
        if (data == null) {
            return;
        }
        LOG.info("Data pushed to the device (id: {}, size: {}, success: {})", dataId, data.data.length, success);
        if (success) {
            notifyDataSuccessfullySent(data);
        }
    }

    public static int registerData(final byte[] data) {
        int id = idCounter.getAndIncrement();
        LOG.info("New data will be sent to the device (id: {}, size: {})", id, data.length);
        dataById.put(id, new Data(data));
        return id;
    }

    public static void onDataChunkSuccessfullyReceived(final int requestId) {
        final ChunkInfo chunkInfo = unprocessedChunksByRequestId.get(requestId);
        if (chunkInfo == null) {
            return;
        }
        unprocessedChunksByRequestId.remove(requestId);
        final Data data = dataById.get(chunkInfo.dataId);
        if (data == null) {
            return;
        }
        data.onDataChunkSuccessfullyReceived(chunkInfo);
        if (data.isDataSuccessfullySent()) {
            LOG.info("Data successfully sent to the device (id: {}, size: {})", chunkInfo.dataId, data.data.length);
            notifyDataSuccessfullySent(data);
            dataById.remove(chunkInfo.dataId);
        } else {
            LOG.debug(
                    "Data chunk successfully sent to the device (dataId: {}, requestId: {}): {}-{}/{}",
                    chunkInfo.dataId, requestId, chunkInfo.start, chunkInfo.end, data.data.length
            );
        }
    }

    private static void notifyDataSuccessfullySent(final Data data) {
        for (Callable<Void> listener : data.onDataSuccessfullySentListeners) {
            try {
                listener.call();
            } catch (Exception e) {
                LOG.error("Data listener failed", e);
            }
        }
    }

    public static void addOnDataSuccessfullySentListener(final int dataId, final Callable<Void> listener) {
        Objects.requireNonNull(dataById.get(dataId)).onDataSuccessfullySentListeners.add(listener);
    }

    private static class ChunkInfo {
        private final int dataId;
        private final int start;
        private final int end;

        private ChunkInfo(int dataId, int start, int end) {
            this.dataId = dataId;
            this.start = start;
            this.end = end;
        }
    }

    private static class Data {
        // TODO Wouldn't it be better to store data as streams?
        // Because now we have to store the whole data in RAM.
        private final byte[] data;
        private final TreeMap<Integer, ChunkInfo> chunksReceivedByDevice;
        private final List<Callable<Void>> onDataSuccessfullySentListeners;

        private Data(byte[] data) {
            this.data = data;
            chunksReceivedByDevice = new TreeMap<>();
            onDataSuccessfullySentListeners = new ArrayList<>();
        }

        private byte[] getDataChunk(final int offset, final int maxChunkSize) {
            if (offset < 0 || offset >= data.length) {
                return null;
            }
            return Arrays.copyOfRange(data, offset, Math.min(offset + maxChunkSize, data.length));
        }

        private void onDataChunkSuccessfullyReceived(ChunkInfo newlyReceivedChunk) {
            final ChunkInfo alreadyReceivedChunk = chunksReceivedByDevice.get(newlyReceivedChunk.start);
            if (alreadyReceivedChunk == null || alreadyReceivedChunk.end < newlyReceivedChunk.end) {
                chunksReceivedByDevice.put(newlyReceivedChunk.start, newlyReceivedChunk);
            }
        }

        private boolean isDataSuccessfullySent() {
            Integer previousChunkEnd = null;
            for (Map.Entry<Integer, ChunkInfo> chunkEntry : chunksReceivedByDevice.entrySet()) {
                if (previousChunkEnd == null && chunkEntry.getKey() != 0) {
                    // The head of the data wasn't received by the device.
                    return false;
                }
                if (previousChunkEnd != null && chunkEntry.getKey() > previousChunkEnd) {
                    // There is some gap between received chunks.
                    return false;
                }
                previousChunkEnd = chunkEntry.getValue().end;
            }
            // Check if the end of the last chunk matches the data size.
            return previousChunkEnd != null && data.length == previousChunkEnd;
        }
    }
}
