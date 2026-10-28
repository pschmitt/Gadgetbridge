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
package nodomain.freeyourgadget.gadgetbridge.activities.externalservices

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractPreferenceFragment
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractSettingsActivityV2
import nodomain.freeyourgadget.gadgetbridge.externalservices.ExternalService
import nodomain.freeyourgadget.gadgetbridge.externalservices.ExternalServiceRegistry
import java.text.Collator

/**
 * One row per registered [ExternalService], sorted by name, each opening that service's own
 * screen. The rows come from [ExternalServiceRegistry], so a new service appears here without
 * this screen being touched.
 */
class OnlineFitnessTrackersPreferencesActivity : AbstractSettingsActivityV2() {
    override fun newFragment(): PreferenceFragmentCompat =
        OnlineFitnessTrackersPreferencesFragment()

    class OnlineFitnessTrackersPreferencesFragment : AbstractPreferenceFragment() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.online_fitness_trackers_preferences, rootKey)

            findPreference<Preference>("pref_key_network_required")?.isVisible =
                !GBApplication.hasInternetAccess()

            val collator = Collator.getInstance()
            val services = ExternalServiceRegistry.services
                .sortedWith(compareBy(collator) { getString(it.nameRes) })
            for (service in services) {
                preferenceScreen.addPreference(
                    Preference(preferenceScreen.context).apply {
                        key = rowKey(service)
                        title = getString(service.nameRes)
                        isPersistent = false
                        isIconSpaceReserved = false
                        fragment = ExternalServiceSettingsFragment::class.java.name
                        extras.putString(ExternalServiceSettingsFragment.ARG_SERVICE_ID, service.id)
                    }
                )
            }
            refreshSummaries()
        }

        override fun onResume() {
            super.onResume()
            refreshSummaries()
        }

        private fun refreshSummaries() {
            val context = requireContext()
            for (service in ExternalServiceRegistry.services) {
                findPreference<Preference>(rowKey(service))?.summary = service.statusSummary(context)
            }
        }

        private fun rowKey(service: ExternalService) = "pref_key_external_service_${service.id}"
    }
}
