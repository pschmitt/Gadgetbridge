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

import nodomain.freeyourgadget.gadgetbridge.externalservices.endurain.EndurainService
import nodomain.freeyourgadget.gadgetbridge.externalservices.wanderer.WandererService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The preference keys derived from a service id are the ones existing installs already hold. */
class ExternalServiceRegistryTest {
    @Test
    fun servicesKeepThePreferenceKeysUsersAlreadyHave() {
        assertEquals("endurain_server", EndurainService.serverPrefKey)
        assertEquals("endurain_auto_upload_enabled", EndurainService.autoUploadPrefKey)
        assertEquals("wanderer_server", WandererService.serverPrefKey)
        assertEquals("wanderer_auto_upload_enabled", WandererService.autoUploadPrefKey)
    }

    @Test
    fun everyServiceIsFoundByItsId() {
        assertEquals(
            ExternalServiceRegistry.services.size,
            ExternalServiceRegistry.services.map { it.id }.toSet().size
        )
        for (service in ExternalServiceRegistry.services) {
            assertSame(service, ExternalServiceRegistry.byId(service.id))
        }
    }
}
