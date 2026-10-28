package nodomain.freeyourgadget.gadgetbridge.service.devices.igpsport;

import org.junit.Assert;
import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.proto.igpsport.Ins;

/**
 * Message privacy modes leave title and body null. The message must still carry name and
 * content, as empty strings.
 */
public class IGPSportNotificationTest {

    private static Ins.ins_data_message build(final String title, final String body) {
        final NotificationSpec spec = new NotificationSpec();
        spec.setType(NotificationType.UNKNOWN);
        spec.setSourceName("Chat");
        spec.setTitle(title);
        spec.setBody(body);
        return IGPSportDeviceSupport.buildNotificationMessage(spec).getInsDataMsg();
    }

    @Test
    public void sendsEmptyNameAndContentWhenAllContentHidden() {
        final Ins.ins_data_message message = build(null, null);

        Assert.assertTrue(message.hasName());
        Assert.assertEquals("", message.getName());
        Assert.assertTrue(message.hasContent());
        Assert.assertEquals("", message.getContent());
    }

    @Test
    public void sendsNameAndContent() {
        final Ins.ins_data_message message = build("Alice", "Hello");

        Assert.assertEquals("Alice", message.getName());
        Assert.assertEquals("Hello", message.getContent());
    }
}
