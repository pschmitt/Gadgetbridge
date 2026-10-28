/*  Copyright (C) 2016-2024 Andreas Shimokawa, Andrzej Surowiec, Arjan
    Schrijver, Carsten Pfeiffer, Daniel Dakhno, Daniele Gobbetti, Ganblejs,
    gfwilliams, Gordon Williams, Johannes Tysiak, José Rebelo, marco.altomonte,
    Petr Vaněk, Taavi Eomäe

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
package nodomain.freeyourgadget.gadgetbridge.activities;

import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_CONNECT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.navigation.NavigationView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.debug.DebugActivityV2;
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryActivityV2;
import nodomain.freeyourgadget.gadgetbridge.activities.welcome.WelcomeActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutListFragment;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes;
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils;
import nodomain.freeyourgadget.gadgetbridge.util.BarShade;
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.GBChangeLog;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.PermissionsUtils;

//TODO: extend AbstractGBActivity, but it requires actionbar that is not available
public class ControlCenterv2 extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, GBActivity, DashboardHost {
    private static final Logger LOG = LoggerFactory.getLogger(ControlCenterv2.class);
    public static final long REALTIME_HR_SAMPLE_TTL_MS = 5_000L;
    public static final int MENU_REFRESH_CODE = 1;
    private boolean isLanguageInvalid = false;
    private boolean isThemeInvalid = false;
    private ViewPager2 viewPager;
    private FragmentStateAdapter pagerAdapter;
    private SwipeRefreshLayout swipeLayout;
    private AlertDialog clDialog;
    private BarShade.ScrollListener barShades;
    private View toolbarShade;
    private View mainContent;
    private BottomNavigationView navigationView;
    private boolean bottomNavigationEnabled;
    private boolean pageBottomBarShown;

    enum NavTab {
        DASHBOARD("dashboard", R.id.bottom_nav_dashboard, R.string.bottom_nav_dashboard, R.drawable.ic_dashboard),
        WORKOUTS("workouts",   R.id.bottom_nav_workouts,  R.string.bottom_nav_workouts,  R.drawable.ic_activity_running),
        DEVICES("devices",     R.id.bottom_nav_devices,   R.string.bottom_nav_devices,   R.drawable.ic_devices_other);

        final String key; final int itemId, titleRes, iconRes;
        NavTab(String key, int itemId, int titleRes, int iconRes) {
            this.key = key; this.itemId = itemId; this.titleRes = titleRes; this.iconRes = iconRes;
        }
        static NavTab fromKey(String key) {
            for (NavTab t : values()) if (t.key.equals(key)) return t;
            return null;
        }
        static NavTab fromItemId(int itemId) {
            for (NavTab t : values()) if (t.itemId == itemId) return t;
            return null;
        }
    }
    private final List<NavTab> visibleTabs = new ArrayList<>();
    private boolean rebuildingNav = false;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, key) -> {
        if ("main_screens".equals(key)) {
            if (rebuildBottomNav()) {
                updateBottomNavigationVisibility();
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (Objects.requireNonNull(action)) {
                case GBApplication.ACTION_LANGUAGE_CHANGE:
                    setLanguage(GBApplication.getLanguage(), true);
                    break;
                case GBApplication.ACTION_THEME_CHANGE:
                    isThemeInvalid = true;
                    break;
                case GBApplication.ACTION_QUIT:
                    finish();
                    break;
                case DeviceService.ACTION_REALTIME_SAMPLES:
                    final GBDevice device = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
                    handleRealtimeSample(device, intent.getSerializableExtra(DeviceService.EXTRA_REALTIME_SAMPLE));
                    break;
                case GBDevice.ACTION_DEVICE_CHANGED:
                    GBDevice dev = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
                    if (dev != null && !dev.isBusy()) {
                        swipeLayout.setRefreshing(false);
                    }
                    break;
                case DeviceManager.ACTION_DEVICES_CHANGED:
                    LOG.debug("Devices changed, {} devices", GBApplication.app().getDeviceManager().getDevices().size());
                    if (rebuildBottomNav()) {
                        updateBottomNavigationVisibility();
                    }
                    break;
            }
        }
    };
    private boolean pesterWithPermissions = true;
    private final Map<GBDevice, ActivitySample> currentHRSample = new HashMap<>();
    private final Map<GBDevice, Long> currentHRSampleReceivedAt = new HashMap<>();

    public ActivitySample getCurrentHRSample(final GBDevice device) {
        final ActivitySample sample = currentHRSample.get(device);

        if (!device.getDeviceCoordinator().supportsLiveOnlyHeartRateDisplay(device)) {
            return sample;
        }

        final Long receivedAt = currentHRSampleReceivedAt.get(device);

        if (sample == null || receivedAt == null) {
            return null;
        }

        if (SystemClock.elapsedRealtime() - receivedAt > REALTIME_HR_SAMPLE_TTL_MS) {
            currentHRSample.remove(device);
            currentHRSampleReceivedAt.remove(device);
            return null;
        }

        return sample;
    }

    private void setCurrentHRSample(final GBDevice device, ActivitySample sample) {
        if (HeartRateUtils.getInstance().isValidHeartRateValue(sample.getHeartRate())) {
            currentHRSample.put(device, sample);
            currentHRSampleReceivedAt.put(device, SystemClock.elapsedRealtime());
        }
    }

    private void handleRealtimeSample(final GBDevice device, Serializable extra) {
        if (extra instanceof ActivitySample) {
            ActivitySample sample = (ActivitySample) extra;
            setCurrentHRSample(device, sample);
        }
    }

    /**
     * Rebuilds the bottom navigation tabs from the preferences and the paired devices.
     *
     * @return true if the visible tabs have changed
     */
    private boolean rebuildBottomNav() {
        boolean supportsActivityTracking = false;
        boolean supportsRecordedActivities = false;

        // Determine availability of device with activity tracking and/or recording functionality
        List<GBDevice> devices = GBApplication.app().getDeviceManager().getDevices();
        for (GBDevice dev : devices) {
            if (dev.getDeviceCoordinator().supportsActivityTracking(dev)) {
                supportsActivityTracking = true;
            }
            if (dev.getDeviceCoordinator().supportsRecordedActivities(dev)) {
                supportsRecordedActivities = true;
            }
            if (supportsActivityTracking && supportsRecordedActivities) break;
        }

        GBPrefs prefs = GBApplication.getPrefs();
        List<String> mainScreensPref = prefs.getList("main_screens", Arrays.asList("dashboard", "workouts", "devices"));
        final List<NavTab> tabs = new ArrayList<>();
        Set<NavTab> seen = EnumSet.noneOf(NavTab.class);
        for (String token : mainScreensPref) {
            NavTab tab = NavTab.fromKey(token.trim());
            if (tab == null || !seen.add(tab)) continue;
            if (tab == NavTab.DASHBOARD && !supportsActivityTracking) continue;
            if (tab == NavTab.WORKOUTS && !supportsRecordedActivities) continue;
            tabs.add(tab);
        }

        // Fallback: Devices is always available, so the bar is never empty
        if (tabs.isEmpty()) {
            tabs.add(NavTab.DEVICES);
        }

        if (tabs.equals(visibleTabs)) {
            // If nothing changed, don't do anything
            return false;
        }

        BottomNavigationView nav = findViewById(R.id.bottom_nav_bar);
        int selectedId = nav.getSelectedItemId();
        Menu menu = nav.getMenu();
        rebuildingNav = true;
        menu.clear();
        visibleTabs.clear();
        visibleTabs.addAll(tabs);
        for (int i = 0; i < tabs.size(); i++) {
            final NavTab tab = tabs.get(i);
            menu.add(Menu.NONE, tab.itemId, i, tab.titleRes).setIcon(tab.iconRes);
        }

        pagerAdapter.notifyDataSetChanged();   // adapter reads visibleTabs

        NavTab keep = NavTab.fromItemId(selectedId);
        NavTab target = (keep != null && visibleTabs.contains(keep)) ? keep : visibleTabs.get(0);
        nav.setSelectedItemId(target.itemId);
        viewPager.setCurrentItem(visibleTabs.indexOf(target), false);
        rebuildingNav = false;
        return true;
    }

    private void updateBottomNavigationVisibility() {
        bottomNavigationEnabled = visibleTabs.size() > 1 && GBApplication.getPrefs().getBoolean("display_bottom_navigation_bar", true);
        LOG.debug("Bottom navigation: tabs={}, enabled={}, pageBottomBarShown={}", visibleTabs, bottomNavigationEnabled, pageBottomBarShown);
        navigationView.setVisibility(bottomNavigationEnabled && !pageBottomBarShown ? View.VISIBLE : View.GONE);
        barShades.setBottomShade(bottomNavigationEnabled || pageBottomBarShown ? findViewById(R.id.bottom_nav_shade) : null);
        ViewCompat.requestApplyInsets(mainContent);
    }

    private void selectDefaultTab() {
        String key = GBApplication.getPrefs().getString("default_main_screen", "dashboard");
        NavTab tab = NavTab.fromKey(key);
        int index = visibleTabs.indexOf(tab);
        if (index < 0) return;

        viewPager.setCurrentItem(index, false);
        ((BottomNavigationView) findViewById(R.id.bottom_nav_bar)).setSelectedItemId(tab.itemId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // installSplashScreen must run before init sets the real theme
        SplashScreen.installSplashScreen(this);
        AbstractGBActivity.init(this, AbstractGBActivity.NO_ACTIONBAR);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GBPrefs prefs = GBApplication.getPrefs();

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("cl")) {
                GBChangeLog cl = GBChangeLog.createChangeLog(this);
                try {
                    if (cl.hasChanges(false)) {
                        clDialog = cl.getMaterialLogDialog();
                    } else {
                        clDialog = cl.getMaterialFullLogDialog();
                    }
                    clDialog.show();
                } catch (Exception ex) {
                    GB.toast(getBaseContext(), getString(R.string.error_showing_changelog), Toast.LENGTH_LONG, GB.ERROR, ex);
                }
            }
        }

        // Initialize drawer
        NavigationView drawerNavigationView = findViewById(R.id.nav_view);
        drawerNavigationView.setNavigationItemSelectedListener(this);

        View navigationHeaderView = drawerNavigationView.getHeaderView(0).findViewById(R.id.drawer_header);
        final int headerPaddingLeft = navigationHeaderView.getPaddingLeft();
        final int headerPaddingRight = navigationHeaderView.getPaddingRight();
        final int headerPaddingBottom = navigationHeaderView.getPaddingBottom();

        // Configure ViewPager2 with fragment adapter and default fragment
        viewPager = findViewById(R.id.dashboard_viewpager);
        pagerAdapter = new MainFragmentsPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // Initialize bottom navigation
        rebuildBottomNav();
        navigationView = findViewById(R.id.bottom_nav_bar);
        navigationView.setOnItemSelectedListener(menuItem -> {
            NavTab tab = NavTab.fromItemId(menuItem.getItemId());
            int index = visibleTabs.indexOf(tab);
            if (index >= 0 && !rebuildingNav) {
                viewPager.setCurrentItem(index, true);
            }
            return true;
        });
        prefs.getPreferences().registerOnSharedPreferenceChangeListener(prefListener);

        // Initialize actionbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // targetSdk 35 forces edge-to-edge. We intercept the insets on the ConstraintLayout
        // itself (main_content) and apply them directly to the toolbar. The drawer header is
        // padded here too.
        mainContent = findViewById(R.id.main_content);
        final int toolbarPaddingLeft = toolbar.getPaddingLeft();
        final int toolbarPaddingRight = toolbar.getPaddingRight();
        final int toolbarPaddingBottom = toolbar.getPaddingBottom();
        final int bottomNavPaddingLeft = navigationView.getPaddingLeft();
        final int bottomNavPaddingRight = navigationView.getPaddingRight();
        final int bottomNavPaddingTop = navigationView.getPaddingTop();
        swipeLayout = findViewById(R.id.dashboard_swipe_layout);
        ViewCompat.setOnApplyWindowInsetsListener(mainContent, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            toolbar.setPadding(toolbarPaddingLeft, insets.top, toolbarPaddingRight, toolbarPaddingBottom);
            navigationView.setPadding(bottomNavPaddingLeft, bottomNavPaddingTop, bottomNavPaddingRight, insets.bottom);
            // #6894 - when the bottom nav is not visible, the FAB would show up behind the system navbar
            // A bottom bar of the page applies the system navbar inset itself
            final int contentPaddingBottom = navigationView.getVisibility() == View.VISIBLE || pageBottomBarShown ? 0 : insets.bottom;
            swipeLayout.setPadding(swipeLayout.getPaddingLeft(), swipeLayout.getPaddingTop(), swipeLayout.getPaddingRight(), contentPaddingBottom);
            navigationHeaderView.setPadding(headerPaddingLeft, insets.top, headerPaddingRight, headerPaddingBottom);
            return windowInsets;
        });
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.controlcenter_navigation_drawer_open, R.string.controlcenter_navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();
        // Every theme defines its own toolbar_bg
        // Not TypedValue.data: the Dynamic surface colors are color state list resources (lStar),
        // for which data is not a color. MaterialColors resolves those too.
        toolbar.setBackgroundColor(MaterialColors.getColor(toolbar, R.attr.toolbar_bg));
        // Same for the bottom bar; main_bar_bg follows toolbar_bg in every theme, so both bars match
        navigationView.setBackgroundColor(MaterialColors.getColor(navigationView, R.attr.main_bar_bg));
        toolbarShade = findViewById(R.id.toolbar_shade);
        barShades = new BarShade.ScrollListener(findViewById(R.id.main_content));
        updateBottomNavigationVisibility();

        // A page's fragment, and its view, are created after the page is selected
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull final FragmentManager fm, @NonNull final Fragment fragment,
                                              @NonNull final View view, final Bundle savedInstanceState) {
                if (fragment instanceof HeaderShadePage) {
                    barShades.addDivider(((HeaderShadePage) fragment).getHeaderShade());
                }
                bindTopShade();
            }

            @Override
            public void onFragmentViewDestroyed(@NonNull final FragmentManager fm, @NonNull final Fragment fragment) {
                bindTopShade();
            }
        }, false);
        bindTopShade();

        // Sync ViewPager changes with BottomNavigationView
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (rebuildingNav || position >= visibleTabs.size()) return;
                int id = visibleTabs.get(position).itemId;
                if (navigationView.getSelectedItemId() != id) {
                    navigationView.setSelectedItemId(id);
                }

                bindTopShade();
            }

            @Override
            public void onPageScrollStateChanged(final int state) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    requestLostLayouts(viewPager.getChildAt(0));
                }
            }
        });
        if (savedInstanceState == null) {
            selectDefaultTab();
        }

        // Make sure the SwipeRefreshLayout doesn't interfere with the ViewPager2
        viewPager.getChildAt(0).setOnTouchListener((v, event) -> {
            if (prefs.refreshOnSwipe()) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    swipeLayout.setEnabled(false);
                } else {
                    swipeLayout.setEnabled(true);
                }
            }
            return false;
        });

        // Set pull-down-to-refresh action
        swipeLayout.setEnabled(prefs.refreshOnSwipe());
        swipeLayout.setOnRefreshListener(() -> {
            if (prefs.refreshOnSwipe()) {
                List<GBDevice> devices1 = GBApplication.app().getDeviceManager().getDevices();
                final boolean anyConnected = devices1.stream().anyMatch(GBDevice::isInitialized);
                if (!anyConnected) {
                    // No devices are connected at all
                    GB.toast(getString(R.string.info_no_devices_connected), Toast.LENGTH_LONG, GB.WARN);
                    swipeLayout.setRefreshing(false);
                    return;
                }
                // Fetch activity for all connected devices
                GBApplication.deviceService().onFetchRecordedData(RecordedDataTypes.TYPE_SYNC);

                // Hide 'refreshing' animation immediately if no devices are connected that support sync
                final boolean anySupported = devices1.stream().filter(GBDevice::isInitialized)
                        .anyMatch(dev -> dev.getDeviceCoordinator().supportsDataFetching(dev));
                if (!anySupported) {
                    swipeLayout.setRefreshing(false);
                    GB.toast(getString(R.string.info_no_devices_to_sync), Toast.LENGTH_LONG, GB.WARN);
                }
            } else {
                swipeLayout.setRefreshing(false);
            }
        });

        // Set up local intent listener
        IntentFilter filterLocal = new IntentFilter();
        filterLocal.addAction(GBApplication.ACTION_LANGUAGE_CHANGE);
        filterLocal.addAction(GBApplication.ACTION_THEME_CHANGE);
        filterLocal.addAction(GBApplication.ACTION_QUIT);
        filterLocal.addAction(DeviceService.ACTION_REALTIME_SAMPLES);
        filterLocal.addAction(GBDevice.ACTION_DEVICE_CHANGED);
        filterLocal.addAction(DeviceManager.ACTION_DEVICES_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filterLocal);

        // Open the Welcome flow on first run, only check permissions on next runs
        boolean firstRun = prefs.getBoolean("first_run", true);
        if (firstRun) {
            launchWelcomeActivity();
        } else {
            pesterWithPermissions = prefs.getBoolean("permission_pestering", true);
            if (pesterWithPermissions && !PermissionsUtils.checkAllPermissions(this)) {
                Intent permissionsIntent = new Intent(this, PermissionsActivity.class);
                permissionsIntent.putExtra(PermissionsActivity.ARG_SHOW_DO_NOT_ASK_BUTTON, true);
                startActivity(permissionsIntent);
            }
        }

        final GBChangeLog cl = GBChangeLog.createChangeLog(this);
        if (cl.isFirstRunEver()) {
            cl.skipLogDialog();
        } else if (prefs.getBoolean("show_changelog", true) && cl.isFirstRun() && cl.hasChanges(false)) {
            try {
                cl.getMaterialLogDialog().show();
            } catch (Exception ex) {
                GB.toast(this, getString(R.string.error_showing_changelog), Toast.LENGTH_LONG, GB.ERROR, ex);
            }
        }

        GBApplication.deviceService().requestDeviceInfo();
    }

    /**
     * Requests the layout again for each view that waits for a layout, but whose parent does not.
     */
    private static void requestLostLayouts(final View view) {
        if (view.getVisibility() == View.GONE) {
            return;
        }
        // The pager detaches and attaches a page again while it scrolls. After that, a view can
        // wait for a layout that its parents do not request, so it does not get one.
        if (view.isLayoutRequested() && view.getParent() instanceof View && !((View) view.getParent()).isLayoutRequested()) {
            view.requestLayout();
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                requestLostLayouts(group.getChildAt(i));
            }
        }
    }

    /**
     * Uses the shade of the visible page, if it has one of its own, instead of the shade below
     * the toolbar.
     */
    private void bindTopShade() {
        final Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + pagerAdapter.getItemId(viewPager.getCurrentItem()));
        final View headerShade = fragment instanceof HeaderShadePage
                ? ((HeaderShadePage) fragment).getHeaderShade() : null;
        barShades.setTopShade(headerShade != null ? headerShade : toolbarShade);
    }

    /**
     * Shows or hides the bottom bar of the current page. The bottom navigation is hidden while the bar shows.
     */
    public void setPageBottomBarShown(final boolean shown) {
        if (shown == pageBottomBarShown) {
            return;
        }
        pageBottomBarShown = shown;
        updateBottomNavigationVisibility();
    }

    @Override
    public void onDashboardEditingChanged(final boolean editing) {
        setPageBottomBarShown(editing);
        viewPager.setUserInputEnabled(!editing);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (clDialog != null) {
            outState.putBoolean("cl", clDialog.isShowing());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleShortcut(getIntent());
        if (isLanguageInvalid || isThemeInvalid) {
            isLanguageInvalid = false;
            isThemeInvalid = false;
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        GBApplication.getPrefs().getPreferences().unregisterOnSharedPreferenceChangeListener(prefListener);
        super.onDestroy();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        final DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        final int itemId = item.getItemId();
        if (itemId == R.id.action_settings) {
            final Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivityForResult(settingsIntent, MENU_REFRESH_CODE);
            return false;
        } else if (itemId == R.id.action_debug) {
            final Intent debugIntent = new Intent(this, DebugActivityV2.class);
            startActivity(debugIntent);
            return false;
        } else if (itemId == R.id.action_data_management) {
            final Intent dbIntent = new Intent(this, DataManagementActivity.class);
            startActivity(dbIntent);
            return false;
        } else if (itemId == R.id.device_action_discover) {
            launchDiscoveryActivity();
            return false;
        } else if (itemId == R.id.action_quit) {
            GBApplication.quit();
            return false;
        } else if (itemId == R.id.donation_link) {
            final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://liberapay.com/Gadgetbridge")); //TODO: centralize if ever used somewhere else
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return false;
        } else if (itemId == R.id.external_changelog) {
            final GBChangeLog cl = GBChangeLog.createChangeLog(this);
            try {
                if (cl.hasChanges(false)) {
                    clDialog = cl.getMaterialLogDialog();
                } else {
                    clDialog = cl.getMaterialFullLogDialog();
                }
                clDialog.show();
            } catch (Exception ex) {
                GB.toast(getBaseContext(), getString(R.string.error_showing_changelog), Toast.LENGTH_LONG, GB.ERROR, ex);
            }
            return false;
        } else if (itemId == R.id.about) {
            final Intent aboutIntent = new Intent(this, AboutActivity.class);
            startActivity(aboutIntent);
            return false;
        }

        return false;  // we do not want the drawer menu item to get selected
    }


    private void launchWelcomeActivity() {
        startActivity(new Intent(this, WelcomeActivity.class));
    }

    private void launchDiscoveryActivity() {
        startActivity(new Intent(this, DiscoveryActivityV2.class));
    }

    private void handleShortcut(Intent intent) {
        if (ACTION_CONNECT.equals(intent.getAction())) {
            String btDeviceAddress = intent.getStringExtra("device");
            if (btDeviceAddress != null) {
                GBDevice candidate = DeviceHelper.getInstance().findAvailableDevice(btDeviceAddress);
                if (candidate != null && !candidate.isConnected()) {
                    GBApplication.deviceService(candidate).connect();
                }
            }
        }
    }

    @Override
    public void setLanguage(Locale language, boolean invalidateLanguage) {
        if (invalidateLanguage) {
            isLanguageInvalid = true;
        }
        AndroidUtils.setLanguage(this, language);
    }

    private class MainFragmentsPagerAdapter extends FragmentStateAdapter {
        public MainFragmentsPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (visibleTabs.get(position)) {
                case DASHBOARD -> new DashboardFragment();
                case WORKOUTS -> new WorkoutListFragment();
                default -> new DevicesFragment();
            };
        }

        @Override
        public int getItemCount() {
            return visibleTabs.size();
        }

        // Stable IDs so ViewPager2 doesn't reuse the wrong fragment after a reorder
        @Override
        public long getItemId(int position) {
            return visibleTabs.get(position).ordinal();
        }
        @Override
        public boolean containsItem(long itemId) {
            for (NavTab tab : visibleTabs) {
                if (tab.ordinal() == itemId) return true;
            }
            return false;
        }
    }
}
