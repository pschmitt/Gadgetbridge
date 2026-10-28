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
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.slf4j.LoggerFactory
import java.security.GeneralSecurityException

/**
 * Credentials of one external service, in a preference file of its own that only that service
 * reads, encrypted at rest.
 *
 * A file whose contents no longer decrypt is cleared rather than left to throw on every read: the
 * key it was written under is gone, so nothing in it can be recovered, and the user is asked to log
 * in again instead of meeting a service that is permanently broken.
 *
 * FIXME MasterKey/EncryptedSharedPreferences is deprecated - we should move to Tink
 */
@Suppress("DEPRECATION")
abstract class ExternalServiceTokenStore(context: Context, private val fileName: String) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    protected val preferences: SharedPreferences = createPreferences(context)

    private fun createPreferences(context: Context): SharedPreferences {
        return try {
            encrypted(context)
        } catch (e: GeneralSecurityException) {
            LOG.warn("Unable to decrypt {} token preferences, resetting them instead", fileName, e)
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                .edit(commit = true) { clear() }
            encrypted(context)
        }
    }

    private fun encrypted(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        fileName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun clearTokens() {
        preferences.edit { clear() }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ExternalServiceTokenStore::class.java)
    }
}

/**
 * Credentials of a service the user authenticates to with a single token they paste in, which
 * neither expires on its own nor can be refreshed.
 */
open class ApiTokenStore(context: Context, fileName: String) : ExternalServiceTokenStore(context, fileName) {
    fun saveToken(apiToken: String) {
        preferences.edit { putString(KEY_API_TOKEN, apiToken) }
    }

    fun getApiToken(): String? = preferences.getString(KEY_API_TOKEN, null)

    open fun isLoggedIn(): Boolean = !getApiToken().isNullOrBlank()

    companion object {
        private const val KEY_API_TOKEN = "api_token"
    }
}
