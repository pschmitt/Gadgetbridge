package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.services;

import com.google.protobuf.ByteString;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.RpkInfoList;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * Wear app messaging, checked against decrypted commands captured between Mi Fitness and a Xiaomi Smart Band 10
 * while WatchDrip+ sent glucose to its band app.
 */
public class XiaomiRpkServiceTest {
    private static final String APP_ID = "com.thatguysservice.huami_xdrip";
    private static final String WEAR_PACKAGE = "com.application.watch.watchdrip";
    private static final ByteString SHA = ByteString.copyFrom(GB.hexStringToByteArray("515e6fbbaae846a3945ffb938f3694ee0b8d2639"));

    private static final String CAPTURED_LAUNCH = "08141004b2015c325a0a370a1f636f6d2e7468617467757973736572766963652e6875616d695f78647269701214515e6fbbaae846a3945ffb938f3694ee0b8d2639121f636f6d2e6170706c69636174696f6e2e77617463682e776174636864726970";
    private static final String CAPTURED_LAUNCHED = "08141006b201392a370a1f636f6d2e7468617467757973736572766963652e6875616d695f78647269701214515e6fbbaae846a3945ffb938f3694ee0b8d2639";
    private static final String CAPTURED_SESSION_END = "08141007b2013d423b0a370a1f636f6d2e7468617467757973736572766963652e6875616d695f78647269701214515e6fbbaae846a3945ffb938f3694ee0b8d26391001";

    private final RpkInfoList app = XiaomiRpkService.rpkRef(APP_ID, SHA);

    @Test
    public void launchMatchesCapture() {
        assertBytes(CAPTURED_LAUNCH, XiaomiRpkService.buildLaunchCommand(app, WEAR_PACKAGE));
    }

    @Test
    public void sessionEndMatchesCapture() {
        assertBytes(CAPTURED_SESSION_END, XiaomiRpkService.buildSessionEndCommand(app));
    }

    @Test
    public void launchedResponseParses() throws Exception {
        final XiaomiProto.Command cmd = XiaomiProto.Command.parseFrom(GB.hexStringToByteArray(CAPTURED_LAUNCHED));
        Assert.assertEquals(XiaomiRpkService.CMD_RPK_LAUNCHED, cmd.getSubtype());
        Assert.assertEquals(APP_ID, cmd.getRpk().getRpkDel().getId());
        Assert.assertEquals(SHA, cmd.getRpk().getRpkDel().getSha());
    }

    @Test
    public void messageCarriesPayload() throws Exception {
        final byte[] payload = "{\"bg\":{\"val\":\"153\"}}".getBytes(StandardCharsets.UTF_8);
        final XiaomiProto.Command cmd = XiaomiProto.Command.parseFrom(
                XiaomiRpkService.buildMessageCommand(app, payload).toByteArray());
        Assert.assertEquals(XiaomiRpkService.COMMAND_TYPE, cmd.getType());
        Assert.assertEquals(XiaomiRpkService.CMD_RPK_MESSAGE, cmd.getSubtype());
        Assert.assertEquals(app, cmd.getRpk().getRpkMessage().getApp());
        Assert.assertArrayEquals(payload, cmd.getRpk().getRpkMessage().getPayload().toByteArray());
    }

    private static void assertBytes(final String expectedHex, final XiaomiProto.Command cmd) {
        Assert.assertEquals(expectedHex, GB.hexdump(cmd.toByteArray()).toLowerCase());
    }
}
