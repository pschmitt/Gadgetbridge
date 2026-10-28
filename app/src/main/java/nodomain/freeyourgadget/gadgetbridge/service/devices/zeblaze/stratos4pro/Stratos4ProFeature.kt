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
package nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Handler
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.preferences.DevicePrefs

/** One feature area of [Stratos4ProSupport]; shares its link, handler and sibling helpers. */
internal abstract class Stratos4ProFeature(protected val support: Stratos4ProSupport) {
    protected val device: GBDevice get() = support.device
    protected val context: Context get() = support.context
    protected val devicePrefs: DevicePrefs get() = support.devicePrefs
    protected val bluetoothAdapter: BluetoothAdapter get() = support.bluetoothAdapter

    /** Main looper handler, shared so dispose clears every helper's callbacks. */
    protected val handler: Handler get() = support.handler

    protected val chunks: Stratos4ProChunkAssembler get() = support.chunks
    protected val history: Stratos4ProHistorySync get() = support.history
    protected val workouts: Stratos4ProWorkoutSync get() = support.workouts
    protected val settings: Stratos4ProSettings get() = support.settings

    protected fun isConnected(): Boolean = support.isConnected

    protected fun createTransactionBuilder(taskName: String): TransactionBuilder =
        support.createTransactionBuilder(taskName)

    protected fun evaluateGBDeviceEvent(event: GBDeviceEvent) = support.evaluateGBDeviceEvent(event)

    protected fun queueFrames(label: String, frames: List<ByteArray>) = support.queueFrames(label, frames)

    protected fun queueFrames(label: String, vararg frames: ByteArray) = support.queueFrames(label, *frames)

    protected fun queueGetField(cmd: Byte, field: Byte) = support.queueGetField(cmd, field)
}
