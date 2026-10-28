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
import androidx.core.content.edit

/**
 * Credentials of a service the user authenticates to with OAuth: a short-lived access token and
 * the refresh token that renews it, each with the moment it stops being usable.
 *
 * The user stays logged in for as long as the refresh token lasts, which is why that is what
 * [isLoggedIn] asks about.
 */
open class OAuthTokenStore(context: Context, fileName: String) : ExternalServiceTokenStore(context, fileName) {
    /**
     * Stores a token pair. A service answers with either a lifetime in seconds or the moment the
     * token expires, so a value too small to be a date is read as the former.
     */
    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        accessTokenExpiry: Int,
        refreshTokenExpiry: Int
    ) {
        preferences.edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putInt(KEY_ACCESS_EXPIRES_AT, expiryTimestamp(accessTokenExpiry))
            putInt(KEY_REFRESH_EXPIRES_AT, expiryTimestamp(refreshTokenExpiry))
        }
    }

    fun getAccessToken(): String? = preferences.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = preferences.getString(KEY_REFRESH_TOKEN, null)

    fun getAccessTokenExpiresAt(): Int = preferences.getInt(KEY_ACCESS_EXPIRES_AT, 0)

    fun getRefreshTokenExpiresAt(): Int = preferences.getInt(KEY_REFRESH_EXPIRES_AT, 0)

    fun isAccessTokenExpired(): Boolean = nowSeconds() >= getAccessTokenExpiresAt()

    fun isRefreshTokenExpired(): Boolean = nowSeconds() >= getRefreshTokenExpiresAt()

    open fun isLoggedIn(): Boolean = getRefreshToken() != null && !isRefreshTokenExpired()

    private fun expiryTimestamp(expiry: Int): Int =
        if (expiry < EPOCH_THRESHOLD) (nowSeconds() + expiry).toInt() else expiry

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_token_expires_at"
        private const val KEY_REFRESH_EXPIRES_AT = "refresh_token_expires_at"

        /** Epoch seconds passed this in 2001, so anything below it is a lifetime, not a date. */
        private const val EPOCH_THRESHOLD = 1000000000
    }
}
