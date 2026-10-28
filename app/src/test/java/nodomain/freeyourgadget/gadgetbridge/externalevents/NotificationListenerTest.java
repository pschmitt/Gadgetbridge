package nodomain.freeyourgadget.gadgetbridge.externalevents;

import android.app.Notification;
import android.graphics.drawable.Icon;
import android.os.Build;

import org.junit.Test;
import org.robolectric.util.ReflectionHelpers;

import java.time.LocalTime;

import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationListenerTest extends TestBase {

    @Test
    public void isOutsideNotificationTimes_samedayWindow_tooEarly_MustReturnTrue() {
        assertTrue(NotificationListener.isOutsideNotificationTimes(
            /* now= */ LocalTime.of(6, 0),
            /* start= */ LocalTime.of(7, 0),
            /* end= */ LocalTime.of(20, 0)
        ));
    }

    @Test
    public void isOutsideNotificationTimes_samedayWindow_withinWindow_MustReturnFalse() {
        assertFalse(NotificationListener.isOutsideNotificationTimes(
            /* now= */ LocalTime.of(10, 0),
            /* start= */ LocalTime.of(7, 0),
            /* end= */ LocalTime.of(20, 0)
        ));
    }

    @Test
    public void isOutsideNotificationTimes_samedayWindow_tooLate_MustReturnTrue() {
        assertTrue(NotificationListener.isOutsideNotificationTimes(
            /* now= */ LocalTime.of(21, 0),
            /* start= */ LocalTime.of(7, 0),
            /* end= */ LocalTime.of(20, 0)
        ));
    }

    @Test
    public void isOutsideNotificationTimes_crossMidnightWindow_tooEarly_MustReturnTrue() {
        assertTrue(NotificationListener.isOutsideNotificationTimes(
            /* now= */ LocalTime.of(18, 0),
            /* start= */ LocalTime.of(20, 0),
            /* end= */ LocalTime.of(7, 0)
        ));
    }

    @Test
    public void isOutsideNotificationTimes_crossMidnightWindow_withinWindow_MustReturnFalse() {
        assertFalse(NotificationListener.isOutsideNotificationTimes(
            /* now= */ LocalTime.of(21, 0),
            /* start= */ LocalTime.of(20, 0),
            /* end= */ LocalTime.of(7, 0)
        ));
        assertFalse(NotificationListener.isOutsideNotificationTimes(
            /* now= */ LocalTime.of(6, 0),
            /* start= */ LocalTime.of(20, 0),
            /* end= */ LocalTime.of(7, 0)
        ));
    }

    @Test
    public void isOutsideNotificationTimes_crossMidnightWindow_tooLate_MustReturnTrue() {
        assertTrue(NotificationListener.isOutsideNotificationTimes(
            /* now= */ LocalTime.of(8, 0),
            /* start= */ LocalTime.of(20, 0),
            /* end= */ LocalTime.of(7, 0)
        ));
    }

    @Test
    public void populateNotificationIcon_preservesResourceOwner() {
        final Object previousVersion = ReflectionHelpers.getStaticField(Build.VERSION.class, "SDK_INT");
        try {
            ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 28);

            final Notification notification = new Notification.Builder(getContext())
                    .setSmallIcon(Icon.createWithResource("com.example.owner", android.R.drawable.ic_dialog_info))
                    .build();
            final NotificationSpec spec = new NotificationSpec();

            NotificationListener.populateNotificationIcon(notification, "com.example.source", spec);

            assertEquals(android.R.drawable.ic_dialog_info, spec.getIconId());
            assertEquals("com.example.owner", spec.getIconPackageId());
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", previousVersion);
        }
    }

    @Test
    public void populateNotificationIcon_usesSourceForLegacyResource() {
        final Notification notification = new Notification();
        notification.icon = android.R.drawable.ic_dialog_alert;
        final NotificationSpec spec = new NotificationSpec();

        NotificationListener.populateNotificationIcon(notification, "com.example.source", spec);

        assertEquals(android.R.drawable.ic_dialog_alert, spec.getIconId());
        assertEquals("com.example.source", spec.getIconPackageId());
    }
}
