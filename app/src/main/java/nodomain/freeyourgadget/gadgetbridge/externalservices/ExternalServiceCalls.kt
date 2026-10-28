/*  Copyright (C) 2026 José Rebelo, Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.externalservices

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Bridge between the API clients, which answer through a callback on a thread of their own, and
 * the service methods, which are documented as blocking so the engine can drive them in order.
 */
object ExternalServiceCalls {
    /**
     * Runs [start] to completion and answers what it called back with, or null when it did not
     * call back within [timeoutSeconds]. Blocking: call off the main thread.
     */
    fun <T> await(timeoutSeconds: Long, start: ((T) -> Unit) -> Unit): T? {
        val latch = CountDownLatch(1)
        var result: T? = null
        start { value ->
            result = value
            latch.countDown()
        }
        return if (latch.await(timeoutSeconds, TimeUnit.SECONDS)) result else null
    }
}
