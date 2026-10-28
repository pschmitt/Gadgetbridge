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
package nodomain.freeyourgadget.gadgetbridge.externalservices.endurain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutUploader
import nodomain.freeyourgadget.gadgetbridge.externalservices.AuthStyle
import nodomain.freeyourgadget.gadgetbridge.externalservices.ExternalService
import nodomain.freeyourgadget.gadgetbridge.externalservices.ExternalServiceCalls
import nodomain.freeyourgadget.gadgetbridge.externalservices.ExternalServiceTokenRefreshWorker
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import org.slf4j.LoggerFactory

object EndurainService : ExternalService {
    override val id = "endurain"
    override val nameRes = R.string.service_name_endurain
    override val authStyle = AuthStyle.LOGIN

    /**
     * Version the server reported when it was last asked, and that server's address, for the
     * settings summary. Kept here rather than on a screen so it survives the screen being closed
     * and reopened.
     */
    @Volatile
    private var serverVersion: Pair<String, String>? = null

    override fun isLoggedIn(context: Context) =
        serverUrl() != null && EndurainTokenManager(context).isLoggedIn()

    override fun statusSummary(context: Context): CharSequence {
        val server = serverUrl()
        val tokenManager = EndurainTokenManager(context)
        var summary = if (tokenManager.isLoggedIn() && server != null) {
            context.getString(
                R.string.service_logged_in_refresh_token,
                server,
                DateTimeUtils.parseTimeStamp(tokenManager.getRefreshTokenExpiresAt())
            )
        } else {
            context.getString(R.string.service_not_logged_in)
        }
        serverVersion?.takeIf { it.first == server }?.let {
            summary += context.getString(R.string.service_server_version, it.second)
        }
        return summary
    }

    /** Renews an access token that ran out, and asks the server for its version. */
    override fun refreshStatus(context: Context, onRefreshed: () -> Unit) {
        val server = serverUrl() ?: return
        val tokenManager = EndurainTokenManager(context.applicationContext)
        if (tokenManager.isLoggedIn() && tokenManager.isAccessTokenExpired()) {
            tokenManager.performTokenRefresh(server) { onRefreshed() }
        }
        Thread {
            serverVersion = try {
                EndurainApiClient(server, tokenManager).fetchVersion()?.let { server to it }
            } catch (e: Exception) {
                LOG.error("Fetching the Endurain server version failed", e)
                null
            }
            onRefreshed()
        }.start()
    }

    override fun showSetup(fragment: Fragment) {
        EndurainSetupBottomSheet().show(fragment.parentFragmentManager, "endurain_setup")
    }

    override fun logout(context: Context): Boolean {
        val tokenManager = EndurainTokenManager(context)
        val confirmed = serverUrl()?.let { EndurainApiClient(it, tokenManager).logout() } ?: true
        tokenManager.clearTokens()
        return confirmed
    }

    /** The refresh token expires, so it is renewed on a schedule rather than at the next upload. */
    override fun scheduleBackgroundWork(context: Context) {
        ExternalServiceTokenRefreshWorker.schedule(context, this)
    }

    override fun credentialsExpired(context: Context): Boolean =
        EndurainTokenManager(context).isAccessTokenExpired()

    override fun refreshCredentials(context: Context): Boolean {
        val server = serverUrl() ?: return false
        return ExternalServiceCalls.await<Boolean>(WorkoutUploader.DEFAULT_UPLOAD_TIMEOUT_SECONDS) { done ->
            EndurainTokenManager(context).performTokenRefresh(server) { done(it) }
        } == true
    }

    /**
     * Endurain brokers the identity provider itself, so the link comes back naming a session on
     * the server rather than an authorization code, and the setup wizard collects the tokens.
     */
    override fun onLoginCallback(context: Context, uri: Uri) {
        val sessionId = uri.getQueryParameter("session_id")
        val result = Intent(SSO_CALLBACK_ACTION).apply {
            putExtra("session_id", sessionId)
            putExtra("success", sessionId != null)
        }
        if (sessionId == null) {
            LOG.error("SSO callback carried no session id")
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(result)
    }

    /**
     * Re-creating an activity to push a changed track destroys one the user may have been looking
     * at, so it is offered as a choice rather than done silently.
     */
    override fun onSettingsScreenCreated(fragment: PreferenceFragmentCompat) {
        val screen = fragment.preferenceScreen
        val replaceOnTrackChange = SwitchPreferenceCompat(screen.context).apply {
            key = GBPrefs.ENDURAIN_REPLACE_ON_TRACK_CHANGE
            setDefaultValue(false)
            layoutResource = R.layout.preference_checkbox
            setIcon(R.drawable.ic_warning)
            setTitle(R.string.pref_endurain_replace_on_track_change_title)
            setSummary(R.string.pref_endurain_replace_on_track_change_summary)
        }
        screen.addPreference(replaceOnTrackChange)
        // Set once it is in the hierarchy: a dependency named on a preference that is not yet
        // attached is resolved against an empty one, and throws.
        replaceOnTrackChange.dependency = autoUploadPrefKey
    }

    private val LOG = LoggerFactory.getLogger(EndurainService::class.java)

    /** Where the setup wizard listens for the outcome of a browser login. */
    const val SSO_CALLBACK_ACTION = "nodomain.freeyourgadget.gadgetbridge.ENDURAIN_SSO_CALLBACK"
}
