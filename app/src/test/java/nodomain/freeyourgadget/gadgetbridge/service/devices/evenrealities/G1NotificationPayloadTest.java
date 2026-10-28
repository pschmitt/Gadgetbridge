package nodomain.freeyourgadget.gadgetbridge.service.devices.evenrealities;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;

import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.test.GBTestApplication;

/**
 * Message privacy modes leave title and body null. The payload must still carry title and
 * message, as empty strings.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23, application = GBTestApplication.class)
public class G1NotificationPayloadTest {

    private static JSONObject payload(final String title, final String body) throws Exception {
        final NotificationSpec spec = new NotificationSpec();
        spec.setSourceAppId("com.example.chat");
        spec.setSourceName("Chat");
        spec.setTitle(title);
        spec.setBody(body);

        final G1Communications.CommandNotificationSendControl command =
                new G1Communications.CommandNotificationSendControl(handler -> {}, spec);
        return new JSONObject(new String(command.payload, StandardCharsets.UTF_8))
                .getJSONObject("ncs_notification");
    }

    @Test
    public void sendsEmptyTitleAndMessageWhenAllContentHidden() throws Exception {
        final JSONObject notification = payload(null, null);

        Assert.assertEquals("", notification.getString("title"));
        Assert.assertEquals("", notification.getString("message"));
    }

    @Test
    public void sendsTitleAndMessage() throws Exception {
        final JSONObject notification = payload("Alice", "Hello");

        Assert.assertEquals("Alice", notification.getString("title"));
        Assert.assertEquals("Hello", notification.getString("message"));
    }
}
