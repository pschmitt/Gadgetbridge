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

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.externalservices.OnlineFitnessTrackersPreferencesActivity
import org.slf4j.LoggerFactory

/**
 * Where a service's login sends the browser back to. The host of the link is the service's
 * [ExternalService.id], so one activity serves every service rather than each bringing its own and
 * its own `<activity>` in the manifest.
 */
class ExternalServiceOAuthCallbackActivity : AbstractGBActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Come back to the front, which dismisses the browser tab the login opened.
        startActivity(
            Intent(applicationContext, OnlineFitnessTrackersPreferencesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )

        val uri = intent.data
        val service = uri?.host?.let { ExternalServiceRegistry.byId(it) }
        when {
            uri == null -> {
                LOG.error("No URI in the login callback")
                Toast.makeText(this, R.string.service_invalid_oauth_callback, Toast.LENGTH_SHORT).show()
            }

            service == null -> {
                LOG.error("Login callback for unknown service '{}'", uri.host)
                Toast.makeText(this, R.string.service_invalid_oauth_callback, Toast.LENGTH_SHORT).show()
            }

            else -> {
                LOG.info("Login callback for {}", service.id)
                Toast.makeText(this, R.string.service_oauth_completing_login, Toast.LENGTH_SHORT).show()
                service.onLoginCallback(applicationContext, uri)
            }
        }
        finish()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ExternalServiceOAuthCallbackActivity::class.java)
    }
}
