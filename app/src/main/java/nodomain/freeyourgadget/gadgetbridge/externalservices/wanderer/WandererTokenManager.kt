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
package nodomain.freeyourgadget.gadgetbridge.externalservices.wanderer

import android.content.Context
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.externalservices.ApiTokenStore

class WandererTokenManager(context: Context) : ApiTokenStore(context, "wanderer_tokens") {
    /**
     * Stores the account's Wanderer handle, the `preferred_username` half of `@user@domain`. Trail
     * pages are namespaced by their owner's handle, so a link to one cannot be built without it.
     */
    fun saveHandle(handle: String) {
        preferences.edit { putString(KEY_HANDLE, handle) }
    }

    fun getHandle(): String? = preferences.getString(KEY_HANDLE, null)

    /** Wanderer issues its API tokens under a fixed prefix, so anything else is a typo. */
    override fun isLoggedIn(): Boolean = getApiToken()?.startsWith(TOKEN_PREFIX) == true

    companion object {
        const val TOKEN_PREFIX = "wanderer_key_"
        private const val KEY_HANDLE = "handle"
    }
}
