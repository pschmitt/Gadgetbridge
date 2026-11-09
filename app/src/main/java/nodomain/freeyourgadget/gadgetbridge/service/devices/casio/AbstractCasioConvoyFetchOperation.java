/*  Copyright (C) 2026 Davide Gessa

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.casio;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.CasioConstants;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEOperation;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.miband.operations.OperationStatus;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * DATA_REQUEST_SP block fetch
 *
 *   phone → h0011  00 feat 00 00 00                request
 *   watch → h0011  00 feat len_lo len_hi 00 00 00  echo with total length
 *   watch → h0014  payload                         one or more CONVOY notifications
 *   phone → h0011  04 feat 00 00 00                ACK - the watch marks the data consumed
 *
 * The DATA_REQ_SP/CONVOY CCCDs are toggled on before and off after the fetch,
 * as the official app does. Subclasses implement {@link #parsePayload} for the
 * feature-specific layout.
 *
 * If fallbackLength > 0, CONVOY notifications are reassembled until
 * the echo length (or the fallback, when no echo arrives) is reached. If it is
 * <= 0 the first CONVOY notification is treated as the whole payload
 */
public abstract class AbstractCasioConvoyFetchOperation<T extends Casio2C2DSupport>
        extends AbstractBTLEOperation<T> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCasioConvoyFetchOperation.class);
    private static final int TIMEOUT_MS = 30_000;

    private final byte mFeature;
    private final int mFallbackLength;
    private final int mBusyTaskLabel;

    private byte mLastWrittenCmd = 0x00;
    private final Handler mTimeoutHandler = new Handler(Looper.getMainLooper());
    private final ByteArrayOutputStream mBuffer = new ByteArrayOutputStream();
    private int mExpectedLength = -1;

    protected AbstractCasioConvoyFetchOperation(T support, byte feature,
                                                int fallbackLength, int busyTaskLabel) {
        super(support);
        this.mFeature = feature;
        this.mFallbackLength = fallbackLength;
        this.mBusyTaskLabel = busyTaskLabel;
    }

    /** Called with the complete payload once all CONVOY notifications arrived */
    protected abstract void parsePayload(byte[] data);

    /** Hook run at the very end of operationFinished() */
    protected void onFetchFinished() {
    }

    private void enableRequiredNotifications(boolean enable) {
        try {
            TransactionBuilder builder = performInitialized("enableRequiredNotifications");
            builder.setCallback(this);
            builder.notify(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID, enable);
            builder.notify(CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID, enable);
            builder.queue();
        } catch (IOException e) {
            LOG.error("Error enabling required notifications", e);
        }
    }

    private void requestBlock() {
        mLastWrittenCmd = 0x00;
        mBuffer.reset();
        mExpectedLength = -1;
        try {
            TransactionBuilder builder = performInitialized("requestBlock");
            builder.setCallback(this);
            builder.writeLegacy(
                    getCharacteristic(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID),
                    new byte[]{0x00, mFeature, 0x00, 0x00, 0x00});
            builder.queue();
        } catch (IOException e) {
            LOG.error("Error requesting block 0x{}", String.format("%02x", mFeature), e);
        }
    }

    private void writeAck() {
        mLastWrittenCmd = 0x04;
        try {
            TransactionBuilder builder = performInitialized("writeAck");
            builder.setCallback(this);
            builder.writeLegacy(
                    getCharacteristic(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID),
                    new byte[]{0x04, mFeature, 0x00, 0x00, 0x00});
            builder.queue();
        } catch (IOException e) {
            LOG.error("Error writing ack - finishing anyway", e);
            enableRequiredNotifications(false);
            operationFinished();
        }
    }

    @Override
    protected void prePerform() throws IOException {
        super.prePerform();
        getDevice().setBusyTask(mBusyTaskLabel, getContext());
        GB.updateTransferNotification(null,
                getContext().getString(R.string.busy_task_fetch_activity_data), true, 0,
                getContext());
    }

    @Override
    protected void doPerform() throws IOException {
        mTimeoutHandler.postDelayed(this::onTimeout, TIMEOUT_MS);
        enableRequiredNotifications(true);
        requestBlock();
    }

    private void onTimeout() {
        LOG.warn("{} timed out", getClass().getSimpleName());
        GB.toast(getContext(), getContext().getString(R.string.busy_task_fetch_activity_data)
                + ": timeout", Toast.LENGTH_SHORT, GB.WARN);
        enableRequiredNotifications(false);
        operationFinished();
    }

    @Override
    protected void operationFinished() {
        if (operationStatus == OperationStatus.FINISHED) return;
        mTimeoutHandler.removeCallbacksAndMessages(null);
        LOG.info("{} finished", getClass().getSimpleName());
        unsetBusy();
        GB.updateTransferNotification(null,
                getContext().getString(R.string.busy_task_fetch_activity_data), false, 100,
                getContext());
        GB.signalActivityDataFinish(getDevice());

        operationStatus = OperationStatus.FINISHED;
        if (getDevice() != null) {
            try {
                TransactionBuilder builder = performInitialized("finished");
                builder.setCallback(null);
                builder.sleep(0);
                builder.queue();
            } catch (IOException ex) {
                LOG.error("Error resetting Gatt callback", ex);
            }
        }
        onFetchFinished();
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           byte[] data) {
        UUID characteristicUUID = characteristic.getUuid();

        if (data == null || data.length == 0) return true;

        if (characteristicUUID.equals(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID)) {
            if (data.length >= 4 && data[0] == 0x00 && data[1] == mFeature) {
                mExpectedLength = (data[2] & 0xff) | ((data[3] & 0xff) << 8);
                LOG.debug("Block 0x{} incoming: {} bytes",
                        String.format("%02x", mFeature), mExpectedLength);
                GB.updateTransferNotification(null,
                        getContext().getString(R.string.busy_task_fetch_activity_data), true, 10,
                        getContext());
            }
            return true;
        } else if (characteristicUUID.equals(CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID)) {
            mBuffer.write(data, 0, data.length);
            final int expected = mExpectedLength > 0 ? mExpectedLength : mFallbackLength;
            if (mFallbackLength <= 0 || mBuffer.size() >= expected) {
                parsePayload(mBuffer.toByteArray());
                GB.updateTransferNotification(null,
                        getContext().getString(R.string.busy_task_fetch_activity_data), true, 80,
                        getContext());
                writeAck();
            }
            return true;
        }

        return super.onCharacteristicChanged(gatt, characteristic, data);
    }

    @Override
    public boolean onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
        UUID uuid = characteristic.getUuid();
        if (uuid.equals(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID)) {
            if (mLastWrittenCmd == 0x00) {
                LOG.debug("Block 0x{} request sent", String.format("%02x", mFeature));
            } else if (mLastWrittenCmd == 0x04) {
                LOG.debug("Block 0x{} ACK sent, finishing", String.format("%02x", mFeature));
                enableRequiredNotifications(false);
                operationFinished();
            }
            return true;
        }
        return super.onCharacteristicWrite(gatt, characteristic, status);
    }
}
