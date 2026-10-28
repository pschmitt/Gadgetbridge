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

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * How the user proves who they are to a service, which decides the wording of the row that starts
 * setup. The flow behind it is the service's own business.
 */
enum class AuthStyle(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int
) {
    /** An account on the service, entered through the service's own login flow. */
    LOGIN(R.string.pref_service_log_in_title, R.string.pref_service_log_in_summary),

    /** A token the user creates on the service and pastes in. */
    API_TOKEN(R.string.pref_service_api_token_title, R.string.pref_service_api_token_summary)
}

/**
 * One service outside Gadgetbridge that the user connects their account to, described by what it is
 * called, how it is set up and how to tell whether it is ready to be used.
 *
 * Implementations are stateless singletons registered once in [ExternalServiceRegistry]; anything
 * they need to remember lives in the preferences [id] namespaces, or in an
 * [ExternalServiceTokenStore].
 */
interface ExternalService {
    /**
     * Stable identifier. It namespaces this service's preference keys and routes its setup result
     * back, so changing one would orphan the settings of every user who had it set up.
     */
    val id: String

    /** The service's own name, for anything that lists services to the user. */
    @get:StringRes
    val nameRes: Int

    val authStyle: AuthStyle

    /**
     * Preference holding the address of the user's instance, or null for a service that lives at
     * one address it hosts itself and has nothing to ask.
     */
    val serverPrefKey: String?
        get() = "${id}_server"

    /** Preference holding whether workouts go to this service without being asked for. */
    val autoUploadPrefKey: String
        get() = "${id}_auto_upload_enabled"

    /** Address of the user's instance, or null when they have not given one. */
    fun serverUrl(): String? =
        serverPrefKey?.let { GBApplication.getPrefs().preferences.getString(it, null) }

    /**
     * Whether the user has finished setting this service up: an address, for a self-hosted one,
     * and credentials that are held and still usable.
     */
    fun isLoggedIn(context: Context): Boolean

    /**
     * One line saying where the user stands with this service, for its settings rows. Names the
     * instance for a self-hosted service, since the user may have more than one in mind.
     */
    fun statusSummary(context: Context): CharSequence = when {
        !isLoggedIn(context) -> context.getString(R.string.service_not_logged_in)
        serverUrl() != null -> context.getString(R.string.service_logged_in_to, serverUrl())
        else -> context.getString(R.string.service_logged_in)
    }

    /**
     * Fetches whatever [statusSummary] shows that the service has to be asked for, calling
     * [onRefreshed] on a background thread each time something new arrived. Called when the
     * service's settings screen opens.
     */
    fun refreshStatus(context: Context, onRefreshed: () -> Unit) {}

    /** Shows this service's setup UI, which reports back on [externalServiceLoginResultKey]. */
    fun showSetup(fragment: Fragment)

    /**
     * Forgets the credentials, telling the service first when it has somewhere to say so. Answers
     * whether that went through; the credentials are dropped locally either way, so a user who
     * cannot reach the server is not stuck logged in. Blocking: call off the main thread.
     */
    fun logout(context: Context): Boolean

    /**
     * Adds whatever this service owns beyond the rows every settings screen already has. Called
     * once the shared rows are in place, so a preference declaring a dependency on one of them
     * finds it.
     */
    fun onSettingsScreenCreated(fragment: PreferenceFragmentCompat) {}

    /**
     * Arranges whatever this service needs doing on its own while the app is not in front of the
     * user, such as refreshing a credential before it expires. Called once at startup, so an
     * implementation schedules rather than does the work, and answers for a service that is not
     * set up by scheduling nothing.
     */
    fun scheduleBackgroundWork(context: Context) {}

    /**
     * Renews the stored credentials, for a service whose login expires. Answers whether it worked;
     * a service whose credentials do not expire has nothing to do and says so. Blocking: called
     * from a worker.
     */
    fun refreshCredentials(context: Context): Boolean = true

    /**
     * Whether the stored credentials have run out and [refreshCredentials] has to go first. A
     * service whose access token is short-lived says so here, so a call is not spent finding out
     * with a 401. Answers false for a service whose credentials do not expire.
     */
    fun credentialsExpired(context: Context): Boolean = false

    /**
     * Handles the link the browser comes back on after a login, whose host is this service's [id].
     * What the rest of it carries is the service's own business.
     */
    fun onLoginCallback(context: Context, uri: Uri) {}
}

/**
 * Key a service's setup UI reports its outcome on, as a boolean extra named `success`. One
 * convention for every service, so the settings screen listens without knowing which is which.
 */
fun externalServiceLoginResultKey(id: String): String = "external_service_login_result_$id"
