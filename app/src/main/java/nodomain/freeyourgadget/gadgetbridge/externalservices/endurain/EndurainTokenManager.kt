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
package nodomain.freeyourgadget.gadgetbridge.externalservices.endurain

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.externalservices.OAuthTokenStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EndurainTokenManager(context: Context) : OAuthTokenStore(context, "endurain_tokens") {
    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(EndurainTokenManager::class.java)
    }

    /**
     * Renews the token pair against [serverUrl], answering through [callback] whether it worked.
     * Runs on a thread of its own.
     */
    fun performTokenRefresh(
        serverUrl: String,
        callback: (Boolean) -> Unit
    ) {
        Thread {
            try {
                val apiClient = EndurainApiClient(serverUrl, this)
                val response = apiClient.refreshToken()

                when {
                    response == null -> {
                        LOG.error("Token refresh failed: null response")
                        callback(false)
                    }

                    response.access_token != null -> {
                        LOG.info("Token refresh successful")
                        saveTokens(
                            response.access_token,
                            response.refresh_token!!,
                            response.expires_in!!,
                            response.refresh_token_expires_in!!
                        )
                        callback(true)
                    }

                    else -> {
                        LOG.error("Token refresh failed: {}", response.detail)
                        callback(false)
                    }
                }
            } catch (e: Exception) {
                LOG.error("Token refresh error", e)
                callback(false)
            }
        }.start()
    }
}
