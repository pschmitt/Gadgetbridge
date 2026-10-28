/*  Copyright (C) 2026 Arjan Schrijver, José Rebelo, Dany Mestas

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

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Renews one service's credentials in the background, so they are still good when a workout is
 * uploaded rather than being found expired at that moment. Which service it runs for is carried in
 * the work's input, so every service that needs this shares one worker.
 */
class ExternalServiceTokenRefreshWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val id = inputData.getString(INPUT_SERVICE_ID)
        val service = ExternalServiceRegistry.byId(id.orEmpty())
        if (service == null) {
            LOG.warn("Token refresh asked for unknown service '{}'", id)
            return Result.success()
        }
        if (!service.isLoggedIn(applicationContext)) {
            LOG.info("Not refreshing {}, it is not set up", id)
            return Result.success()
        }
        LOG.info("Refreshing the credentials of {}", id)
        return if (service.refreshCredentials(applicationContext)) Result.success() else Result.retry()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ExternalServiceTokenRefreshWorker::class.java)

        const val INPUT_SERVICE_ID = "service_id"

        /**
         * Arranges for [service] to have its credentials renewed regularly, replacing whatever was
         * scheduled before so a service that has just been logged out of stops being asked.
         */
        fun schedule(context: Context, service: ExternalService) {
            val workName = WORK_NAME_PREFIX + service.id
            val workManager = WorkManager.getInstance(context)
            if (!service.isLoggedIn(context)) {
                workManager.cancelUniqueWork(workName)
                return
            }
            val request = PeriodicWorkRequestBuilder<ExternalServiceTokenRefreshWorker>(
                REFRESH_INTERVAL_DAYS, TimeUnit.DAYS
            )
                .addTag(workName)
                .setInputData(Data.Builder().putString(INPUT_SERVICE_ID, service.id).build())
                .build()
            // KEEP, not REPLACE: this runs on every app start, and replacing the work would run
            // it again each time. A service that rotates its refresh token hands back a new one
            // and refuses the old, so refreshing more often than needed is how a user gets
            // logged out.
            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private const val WORK_NAME_PREFIX = "ExternalServiceTokenRefresh_"

        /** Comfortably inside the shortest refresh-token lifetime any supported service issues. */
        private const val REFRESH_INTERVAL_DAYS = 3L
    }
}
