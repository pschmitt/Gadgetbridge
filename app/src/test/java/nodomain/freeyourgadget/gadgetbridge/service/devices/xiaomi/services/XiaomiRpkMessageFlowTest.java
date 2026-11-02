package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.services;

import com.google.protobuf.ByteString;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Rpk;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.RpkInfoList;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.RpkList;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSupport;
import nodomain.freeyourgadget.gadgetbridge.test.GBTestApplication;

/**
 * Order of commands when messaging a watch app: rpk list (only while the app's sha is unknown), launch,
 * then message and session end once the band reports the app launched.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23, application = GBTestApplication.class)
public class XiaomiRpkMessageFlowTest {
    private static final String APP_ID = "org.example.watchapp";
    private static final ByteString SHA = ByteString.copyFrom(new byte[20]);
    private static final byte[] PAYLOAD = "{\"value\":1}".getBytes(StandardCharsets.UTF_8);

    private XiaomiSupport support;
    private XiaomiRpkService service;

    @Before
    public void setUp() {
        support = Mockito.mock(XiaomiSupport.class);
        Mockito.when(support.getWatchfaceService()).thenReturn(Mockito.mock(XiaomiWatchfaceService.class));
        service = new XiaomiRpkService(support);
    }

    @Test
    public void unknownAppFetchesListThenLaunchesThenSends() {
        service.sendWearAppMessage(APP_ID, PAYLOAD);
        Mockito.verify(support).sendCommand(Mockito.anyString(), Mockito.eq(XiaomiRpkService.COMMAND_TYPE),
                Mockito.eq(XiaomiRpkService.CMD_RPK_LIST));
        Assert.assertTrue(sentCommands().isEmpty());

        service.handleCommand(rpkList(APP_ID));
        final XiaomiProto.Command launch = single(sentCommands());
        Assert.assertEquals(XiaomiRpkService.CMD_RPK_LAUNCH, launch.getSubtype());
        Assert.assertEquals(APP_ID, launch.getRpk().getRpkLaunch().getApp().getId());
        Assert.assertEquals(SHA, launch.getRpk().getRpkLaunch().getApp().getSha());
        Assert.assertEquals(APP_ID, launch.getRpk().getRpkLaunch().getWearPackage());

        service.handleCommand(launched(APP_ID));
        final List<XiaomiProto.Command> sent = sentCommands();
        Assert.assertEquals(3, sent.size());
        Assert.assertEquals(XiaomiRpkService.CMD_RPK_MESSAGE, sent.get(1).getSubtype());
        Assert.assertArrayEquals(PAYLOAD, sent.get(1).getRpk().getRpkMessage().getPayload().toByteArray());
        Assert.assertEquals(XiaomiRpkService.CMD_RPK_SESSION_END, sent.get(2).getSubtype());
    }

    @Test
    public void knownAppIsLaunchedWithoutFetchingTheList() {
        service.handleCommand(rpkList(APP_ID));
        service.sendWearAppMessage(APP_ID, PAYLOAD);
        Assert.assertEquals(XiaomiRpkService.CMD_RPK_LAUNCH, single(sentCommands()).getSubtype());
        Mockito.verify(support, Mockito.never()).sendCommand(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    public void appMissingOnTheBandIsDropped() {
        service.sendWearAppMessage(APP_ID, PAYLOAD);
        service.handleCommand(rpkList("org.example.other"));
        Assert.assertTrue(sentCommands().isEmpty());

        // a later launch notification for it carries no message
        service.handleCommand(launched(APP_ID));
        Assert.assertTrue(sentCommands().isEmpty());
    }

    @Test
    public void launchWithoutPendingMessageSendsNothing() {
        service.handleCommand(rpkList(APP_ID));
        service.handleCommand(launched(APP_ID));
        Assert.assertTrue(sentCommands().isEmpty());
    }

    private List<XiaomiProto.Command> sentCommands() {
        final ArgumentCaptor<XiaomiProto.Command> captor = ArgumentCaptor.forClass(XiaomiProto.Command.class);
        Mockito.verify(support, Mockito.atLeast(0)).sendCommand(Mockito.anyString(), captor.capture());
        return captor.getAllValues();
    }

    private static XiaomiProto.Command single(final List<XiaomiProto.Command> commands) {
        Assert.assertEquals(1, commands.size());
        return commands.get(0);
    }

    private static XiaomiProto.Command rpkList(final String id) {
        return XiaomiProto.Command.newBuilder()
                .setType(XiaomiRpkService.COMMAND_TYPE)
                .setSubtype(XiaomiRpkService.CMD_RPK_LIST)
                .setRpk(Rpk.newBuilder().setRpkList(RpkList.newBuilder().addRpkInfo(
                        RpkInfoList.newBuilder().setId(id).setSha(SHA).setName("Watch app"))))
                .build();
    }

    private static XiaomiProto.Command launched(final String id) {
        return XiaomiProto.Command.newBuilder()
                .setType(XiaomiRpkService.COMMAND_TYPE)
                .setSubtype(XiaomiRpkService.CMD_RPK_LAUNCHED)
                .setRpk(Rpk.newBuilder().setRpkDel(XiaomiRpkService.rpkRef(id, SHA)))
                .build();
    }
}
