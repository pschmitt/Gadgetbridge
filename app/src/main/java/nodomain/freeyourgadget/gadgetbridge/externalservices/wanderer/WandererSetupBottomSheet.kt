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

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutUploader
import nodomain.freeyourgadget.gadgetbridge.externalservices.AbstractApiTokenSetupBottomSheet
import nodomain.freeyourgadget.gadgetbridge.externalservices.ExternalServiceCalls
import nodomain.freeyourgadget.gadgetbridge.util.InternetUtils

class WandererSetupBottomSheet : AbstractApiTokenSetupBottomSheet(
    R.layout.wanderer_bottomsheet_setup_wizard,
    R.id.wanderer_api_token,
    R.id.wanderer_server_name
) {
    override val service = WandererService

    /**
     * Handle read while the token was being checked, so it is stored alongside it. Null when the
     * server did not give one, which only costs the link to the trail.
     */
    private var handle: String? = null

    override fun validate(apiToken: String): String? = when {
        serverName.isEmpty() || apiToken.isEmpty() ->
            getString(R.string.service_setup_missing_information)

        !apiToken.startsWith(WandererTokenManager.TOKEN_PREFIX) ->
            getString(R.string.service_setup_api_token_prefix_error, WandererTokenManager.TOKEN_PREFIX)

        else -> null
    }

    override fun checkToken(apiToken: String): Pair<Boolean, String?> {
        val context = requireContext()
        val apiClient = WandererApiClient(serverName, WandererTokenManager(context))
        val timeout = WorkoutUploader.DEFAULT_UPLOAD_TIMEOUT_SECONDS

        val reachable = ExternalServiceCalls.await<Pair<Boolean, String?>>(timeout) { done ->
            apiClient.checkServerReachable(apiToken) { ok, reason -> done(ok to reason) }
        } ?: return false to InternetUtils.connectFailureReason(context, serverName)
        if (!reachable.first) {
            return false to (reachable.second ?: InternetUtils.connectFailureReason(context, serverName))
        }

        handle = ExternalServiceCalls.await<String?>(timeout) { done ->
            apiClient.fetchUserHandle(apiToken) { done(it) }
        }
        return true to null
    }

    override fun onTokenAccepted(apiToken: String) {
        WandererTokenManager(requireContext()).apply {
            saveToken(apiToken)
            handle?.let { saveHandle(it) }
        }
    }
}
