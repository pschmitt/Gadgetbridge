package nodomain.freeyourgadget.gadgetbridge.activities.debug

import android.os.Build
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceFragmentCompat
import com.bytehamster.lib.preferencesearch.SearchPreferenceFragment
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractSettingsActivityV2

class DebugActivityV2 : AbstractSettingsActivityV2(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    override fun newFragment(): PreferenceFragmentCompat? {
        return MainDebugFragment()
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        val screen = searchableScreens().firstOrNull { it.xml == result.resourceFile }
        if (screen == null) {
            super.onSearchResultClicked(result)
            return
        }

        result.closeSearchPage(this)
        supportFragmentManager.popBackStack(SearchPreferenceFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        val fragment = screen.newFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
        result.highlight(fragment)
    }

    class SearchableScreen(
        @XmlRes val xml: Int,
        @StringRes val title: Int,
        val newFragment: () -> AbstractDebugFragment
    )

    companion object {
        fun searchableScreens(): List<SearchableScreen> {
            val screens = mutableListOf(
                SearchableScreen(
                    R.xml.debug_preferences_notifications,
                    R.string.menuitem_notifications,
                    ::NotificationsDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_calls,
                    R.string.phone_calls,
                    ::CallsDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_music,
                    R.string.menuitem_music,
                    ::MusicDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_weather,
                    R.string.menuitem_weather,
                    ::WeatherDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_navigation,
                    R.string.pref_header_navigation,
                    ::NavigationDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_database,
                    R.string.database,
                    ::DatabaseDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_preferences,
                    R.string.menuitem_settings,
                    ::PreferencesDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_dashboard,
                    R.string.bottom_nav_dashboard,
                    ::DashboardDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_widgets,
                    R.string.menuitem_widgets,
                    ::WidgetsDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_location,
                    R.string.pref_header_location,
                    ::LocationDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_health_connect,
                    R.string.healthconnect_settings,
                    ::HealthConnectDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_other_actions,
                    R.string.debug_other_actions,
                    ::OtherDebugFragment
                ),
                SearchableScreen(
                    R.xml.debug_preferences_internethelper,
                    R.string.prefs_internet_helper_title,
                    ::InternetHelperDebugFragment
                ),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                screens.add(
                    SearchableScreen(
                        R.xml.debug_preferences_companion,
                        R.string.companion_pairing_request_title,
                        ::CompanionDebugFragment
                    )
                )
            }
            return screens
        }
    }
}
