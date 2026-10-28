package nodomain.freeyourgadget.gadgetbridge.util;

import static org.junit.Assert.assertNotEquals;

import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

import org.junit.Test;

/**
 * Every navigation action must resolve to an icon drawable, so watches
 * requesting {@code gadgetbridge.nav.v1.<action>} icons always get one.
 */
public class NavigationUtilsTest extends TestBase {
    private static final int[] ALL_ACTIONS = {
            NavigationInfoSpec.ACTION_CONTINUE,
            NavigationInfoSpec.ACTION_TURN_LEFT,
            NavigationInfoSpec.ACTION_TURN_LEFT_SLIGHTLY,
            NavigationInfoSpec.ACTION_TURN_LEFT_SHARPLY,
            NavigationInfoSpec.ACTION_TURN_RIGHT,
            NavigationInfoSpec.ACTION_TURN_RIGHT_SLIGHTLY,
            NavigationInfoSpec.ACTION_TURN_RIGHT_SHARPLY,
            NavigationInfoSpec.ACTION_KEEP_LEFT,
            NavigationInfoSpec.ACTION_KEEP_RIGHT,
            NavigationInfoSpec.ACTION_UTURN_LEFT,
            NavigationInfoSpec.ACTION_UTURN_RIGHT,
            NavigationInfoSpec.ACTION_OFFROUTE,
            NavigationInfoSpec.ACTION_ROUNDABOUT_RIGHT,
            NavigationInfoSpec.ACTION_ROUNDABOUT_LEFT,
            NavigationInfoSpec.ACTION_ROUNDABOUT_STRAIGHT,
            NavigationInfoSpec.ACTION_ROUNDABOUT_UTURN,
            NavigationInfoSpec.ACTION_FINISH,
            NavigationInfoSpec.ACTION_MERGE,
    };

    @Test
    public void everyActionResolvesToAnIcon() {
        for (final int action : ALL_ACTIONS) {
            final String packageName = NavigationUtils.getIconPackageName(action);
            assertNotEquals("no icon for action " + action, 0, NavigationUtils.getIconResource(packageName));
        }
    }

    @Test
    public void unknownPackagesResolveToZero() {
        org.junit.Assert.assertEquals(0, NavigationUtils.getIconResource(null));
        org.junit.Assert.assertEquals(0, NavigationUtils.getIconResource("com.google.android.apps.maps"));
        org.junit.Assert.assertEquals(0, NavigationUtils.getIconResource("gadgetbridge.nav.v1.notanumber"));
        org.junit.Assert.assertEquals(0, NavigationUtils.getIconResource("gadgetbridge.nav.v1.999"));
    }
}
