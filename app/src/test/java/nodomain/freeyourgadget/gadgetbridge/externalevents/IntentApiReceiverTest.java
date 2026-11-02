package nodomain.freeyourgadget.gadgetbridge.externalevents;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class IntentApiReceiverTest extends TestBase {
    private static final String ADDRESS = "AA:BB:CC:DD:EE:01";
    private static final String OTHER_ADDRESS = "AA:BB:CC:DD:EE:02";
    private static final String APP_ID = "org.example.watchapp";
    private static final String PAYLOAD = "{\"value\":1}";

    private final IntentApiReceiver receiver = new IntentApiReceiver();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        registerDeviceWithManager(new GBDevice(ADDRESS, "Testie", "Test Alias", "Test Folder", DeviceType.TEST));
        GBApplication.getDeviceSpecificSharedPrefs(ADDRESS).edit().clear().commit();
        GBApplication.getDeviceSpecificSharedPrefs(OTHER_ADDRESS).edit().clear().commit();
        while (nextStartedService() != null) {
            // Drain app startup noise so each test can assert only its own service invocation.
        }
    }

    @Test
    public void watchAppMessage_sentToDeviceThatAllowsIt() {
        allowWatchAppMessages(ADDRESS);

        receiver.onReceive(getContext(), watchAppMessage(ADDRESS));

        final Intent forwarded = nextStartedService();
        assertNotNull(forwarded);
        assertEquals(DeviceService.ACTION_SEND_WATCH_APP_MESSAGE, forwarded.getAction());
        assertEquals(APP_ID, forwarded.getStringExtra(DeviceService.EXTRA_WATCH_APP_ID));
        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8),
                forwarded.getByteArrayExtra(DeviceService.EXTRA_WATCH_APP_PAYLOAD));
    }

    @Test
    public void watchAppMessage_rejectedWhenDeviceDoesNotAllowIt() {
        receiver.onReceive(getContext(), watchAppMessage(ADDRESS));

        assertNull(nextStartedService());
    }

    @Test
    public void watchAppMessage_permissionOfAnotherDeviceDoesNotCount() {
        allowWatchAppMessages(OTHER_ADDRESS);

        receiver.onReceive(getContext(), watchAppMessage(ADDRESS));

        assertNull(nextStartedService());
    }

    @Test
    public void watchAppMessage_rejectedWithoutDevice() {
        allowWatchAppMessages(ADDRESS);

        receiver.onReceive(getContext(), watchAppMessage(null));

        assertNull(nextStartedService());
    }

    @Test
    public void watchAppMessage_rejectedForUnknownDevice() {
        allowWatchAppMessages(OTHER_ADDRESS);

        receiver.onReceive(getContext(), watchAppMessage(OTHER_ADDRESS));

        assertNull(nextStartedService());
    }

    private void allowWatchAppMessages(final String address) {
        GBApplication.getDeviceSpecificSharedPrefs(address).edit()
                .putBoolean(IntentApiReceiver.THIRD_PARTY_APPS_WATCH_APP_MESSAGES, true)
                .commit();
    }

    private Intent watchAppMessage(final String address) {
        final Intent intent = new Intent(IntentApiReceiver.COMMAND_SEND_WATCH_APP_MESSAGE)
                .putExtra("appId", APP_ID)
                .putExtra("payload", PAYLOAD);
        if (address != null) {
            intent.putExtra(IntentApiReceiver.EXTRA_DEVICE, address);
        }
        return intent;
    }

    private Intent nextStartedService() {
        return shadowOf((Application) app).getNextStartedService();
    }
}
