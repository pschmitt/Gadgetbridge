package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.DrawableRes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;

public class NavigationUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NavigationUtils.class);

    /**
     * Prefix of the fake package names used for navigation icons. Devices usually cache icons
     * by package name - change the version if the drawable changes.
     */
    private static final String ICON_PACKAGE_PREFIX = "gadgetbridge.nav.v1.";

    public static boolean shouldSendNavigation(final Context context, final String appName) {
        final Prefs prefs = GBApplication.getPrefs();

        final boolean navigationForward = prefs.getBoolean("navigation_forward", true);
        final boolean navigationApp = prefs.getBoolean("navigation_app_" + appName, true);
        if (!navigationForward || !navigationApp) {
            LOG.info("Not forwarding navigation instruction for {}, user preferences do not allow this", appName);
            return false;
        }

        final boolean navigationScreenOn = prefs.getBoolean("nagivation_screen_on", true);
        if (!navigationScreenOn) {
            final PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && powerManager.isScreenOn()) {
                LOG.info("Not forwarding navigation instructions, screen seems to be on and settings do not allow this");
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the fake package name for the icon of a navigation action.
     */
    public static String getIconPackageName(final int action) {
        return ICON_PACKAGE_PREFIX + action;
    }

    /**
     * Returns the navigation action for a package name that matches {@link #getIconPackageName}, or -1 if not recognized.
     */
    public static int getAction(final String packageName) {
        if (packageName == null || !packageName.startsWith(ICON_PACKAGE_PREFIX)) {
            return -1;
        }
        try {
            return Integer.parseInt(packageName.substring(ICON_PACKAGE_PREFIX.length()));
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Returns a unicode arrow for a navigation action, for devices that can only show text, or an
     * empty string if there is none. Only uses characters from the arrow blocks that common fonts cover.
     */
    public static String getActionSymbol(final int action) {
        return switch (action) {
            case NavigationInfoSpec.ACTION_CONTINUE, NavigationInfoSpec.ACTION_MERGE -> "\u2191"; // ↑
            case NavigationInfoSpec.ACTION_TURN_LEFT -> "\u2190"; // ←
            case NavigationInfoSpec.ACTION_TURN_LEFT_SLIGHTLY, NavigationInfoSpec.ACTION_KEEP_LEFT -> "\u2196"; // ↖
            case NavigationInfoSpec.ACTION_TURN_LEFT_SHARPLY -> "\u2199"; // ↙
            case NavigationInfoSpec.ACTION_TURN_RIGHT -> "\u2192"; // →
            case NavigationInfoSpec.ACTION_TURN_RIGHT_SLIGHTLY, NavigationInfoSpec.ACTION_KEEP_RIGHT -> "\u2197"; // ↗
            case NavigationInfoSpec.ACTION_TURN_RIGHT_SHARPLY -> "\u2198"; // ↘
            case NavigationInfoSpec.ACTION_UTURN_LEFT -> "\u21A9"; // ↩
            case NavigationInfoSpec.ACTION_UTURN_RIGHT -> "\u21AA"; // ↪
            case NavigationInfoSpec.ACTION_ROUNDABOUT_LEFT -> "\u21BB"; // ↻
            case NavigationInfoSpec.ACTION_ROUNDABOUT_RIGHT, NavigationInfoSpec.ACTION_ROUNDABOUT_UTURN -> "\u21BA"; // ↺
            case NavigationInfoSpec.ACTION_ROUNDABOUT_STRAIGHT -> "\u2191"; // ↑
            case NavigationInfoSpec.ACTION_FINISH -> "\u2691"; // ⚑
            case NavigationInfoSpec.ACTION_OFFROUTE -> "\u26A0"; // ⚠
            default -> "";
        };
    }

    /**
     * Returns the drawable for a package name that matches {@link #getIconPackageName}, or 0 if not recognized.
     */
    @DrawableRes
    public static int getIconResource(final String packageName) {
        return switch (getAction(packageName)) {
            case NavigationInfoSpec.ACTION_CONTINUE -> R.drawable.ic_turn_straight;
            case NavigationInfoSpec.ACTION_TURN_LEFT -> R.drawable.ic_turn_left;
            case NavigationInfoSpec.ACTION_TURN_LEFT_SLIGHTLY, NavigationInfoSpec.ACTION_KEEP_LEFT ->
                R.drawable.ic_turn_left_slight;
            case NavigationInfoSpec.ACTION_TURN_LEFT_SHARPLY -> R.drawable.ic_turn_left_sharp;
            case NavigationInfoSpec.ACTION_TURN_RIGHT -> R.drawable.ic_turn_right;
            case NavigationInfoSpec.ACTION_TURN_RIGHT_SLIGHTLY, NavigationInfoSpec.ACTION_KEEP_RIGHT ->
                R.drawable.ic_turn_right_slight;
            case NavigationInfoSpec.ACTION_TURN_RIGHT_SHARPLY -> R.drawable.ic_turn_right_sharp;
            case NavigationInfoSpec.ACTION_UTURN_LEFT -> R.drawable.ic_turn_uleft;
            case NavigationInfoSpec.ACTION_UTURN_RIGHT -> R.drawable.ic_turn_uright;
            case NavigationInfoSpec.ACTION_ROUNDABOUT_LEFT -> R.drawable.ic_turn_round_left;
            case NavigationInfoSpec.ACTION_ROUNDABOUT_RIGHT -> R.drawable.ic_turn_round_right;
            case NavigationInfoSpec.ACTION_ROUNDABOUT_STRAIGHT -> R.drawable.ic_turn_round_straight;
            case NavigationInfoSpec.ACTION_ROUNDABOUT_UTURN -> R.drawable.ic_turn_round_uturn;
            case NavigationInfoSpec.ACTION_FINISH -> R.drawable.ic_turn_finish;
            case NavigationInfoSpec.ACTION_OFFROUTE -> R.drawable.ic_turn_offroute;
            case NavigationInfoSpec.ACTION_MERGE -> R.drawable.ic_turn_merge;
            default -> 0;
        };
    }
}
