/*  Copyright (C) 2026 Dany Mestas

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
import android.net.Uri
import nodomain.freeyourgadget.gadgetbridge.BuildConfig
import nodomain.freeyourgadget.gadgetbridge.externalservices.endurain.EndurainService
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The redirect each service's login sends must open [ExternalServiceOAuthCallbackActivity] in the
 * manifest of the variant under test. Bangle.js has its own application id, so its run catches a
 * callback scheme that does not follow the variant's id.
 */
class ExternalServiceOAuthCallbackActivityTest : TestBase() {
    @Test
    fun everyServiceRedirectOpensTheCallbackActivity() {
        for (service in ExternalServiceRegistry.services) {
            val redirect = ExternalServiceOAuthCallbackActivity.redirectUri(service)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$redirect?session_id=test"))
                .addCategory(Intent.CATEGORY_BROWSABLE)

            val handlers = app.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.activityInfo.name }

            assertEquals(
                redirect,
                listOf(BuildConfig.APPLICATION_ID to ExternalServiceOAuthCallbackActivity::class.java.name),
                handlers
            )
        }
    }

    @Test
    fun redirectSchemeIsThePackageNameAsAValidUriScheme() {
        assertEquals(
            "${BuildConfig.APPLICATION_ID.replace('_', '-')}://endurain/oauth/callback",
            ExternalServiceOAuthCallbackActivity.redirectUri(EndurainService)
        )
        // RFC 3986 section 3.1; browsers resolve anything else as a relative path.
        assertTrue(
            BuildConfig.OAUTH_REDIRECT_SCHEME,
            BuildConfig.OAUTH_REDIRECT_SCHEME.matches(Regex("[A-Za-z][A-Za-z0-9+.-]*"))
        )
    }
}
