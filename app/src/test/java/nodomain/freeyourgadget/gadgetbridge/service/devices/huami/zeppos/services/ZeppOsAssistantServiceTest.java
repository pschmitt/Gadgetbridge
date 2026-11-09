/*  Copyright (C) 2026 Alphons Jaimon

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.services;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.robolectric.RuntimeEnvironment;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.HuamiDevicePrefs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ZeppOsSupport;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class ZeppOsAssistantServiceTest extends TestBase {
    private static final String VOICE_PACKAGE = "com.example.voice";
    private static final String VOICE_CLASS = "com.example.voice.VoiceService";

    private ZeppOsSupport support;
    private HuamiDevicePrefs prefs;
    private ZeppOsAssistantService service;

    /** Messages received by the fake external voice service. */
    private final List<Message> received = new ArrayList<>();

    @Before
    public void setUpService() {
        support = mock(ZeppOsSupport.class);
        prefs = mock(HuamiDevicePrefs.class);
        when(support.getDevicePrefs()).thenReturn(prefs);
        when(support.getContext()).thenReturn(RuntimeEnvironment.getApplication());
        when(prefs.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        service = new ZeppOsAssistantService(support, ZeppOsAssistantService.ENDPOINT_ALEXA);
    }

    private void configureVoiceService() {
        when(prefs.getString(eq("voice_service_package"), anyString())).thenReturn(VOICE_PACKAGE);
        when(prefs.getString(eq("voice_service_class"), anyString())).thenReturn(VOICE_CLASS);

        final Messenger fakeService = new Messenger(new Handler(Looper.getMainLooper(), msg -> {
            final Message copy = Message.obtain(msg);
            received.add(copy);
            return true;
        }));
        final Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).setComponentNameAndServiceForBindService(
                new ComponentName(VOICE_PACKAGE, VOICE_CLASS), fakeService.getBinder());
    }

    private List<byte[]> capturedWrites() {
        final ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(support, org.mockito.Mockito.atLeastOnce())
                .writeToChunked2021(anyString(), eq(ZeppOsAssistantService.ENDPOINT_ALEXA), captor.capture(), anyBoolean());
        return captor.getAllValues();
    }

    private static byte[] startPayload() {
        return new byte[]{0x01, 0, 0, 0, 0, 0};
    }

    /** Voice data payload (version 3): 4-byte sequence, then 1-byte length + opus frame. */
    private static byte[] voicePayload(final byte[] frame) {
        final byte[] payload = new byte[5 + 1 + frame.length];
        payload[0] = 0x05;
        payload[5] = (byte) frame.length;
        System.arraycopy(frame, 0, payload, 6, frame.length);
        return payload;
    }

    @Test
    public void capabilitiesWithThreeBytesDoNotCrash() {
        // Amazfit Band 7 replies with only 3 bytes
        service.handlePayload(new byte[]{0x21, 0x03, 0x01});

        verify(support).evaluateGBDeviceEvent(any(GBDeviceEventUpdatePreferences.class));
        // version 3 requests the languages next
        assertArrayEquals(new byte[]{0x10}, capturedWrites().get(0));
    }

    @Test
    public void capabilitiesWithFourBytes() {
        service.handlePayload(new byte[]{0x21, 0x05, 0x01, 0x01});

        verify(support).evaluateGBDeviceEvent(any(GBDeviceEventUpdatePreferences.class));
    }

    @Test
    public void voiceIsForwardedAndReplyIsShownOnWatch() throws Exception {
        configureVoiceService();
        service.handlePayload(new byte[]{0x21, 0x03, 0x01});

        service.handlePayload(startPayload());
        final byte[] frame = {0x11, 0x22, 0x33};
        service.handlePayload(voicePayload(frame));
        service.handlePayload(new byte[]{0x02});
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(2, received.size());
        assertEquals(ZeppOsVoiceServiceBridge.MSG_VOICE_DATA, received.get(0).what);
        assertArrayEquals(frame, received.get(0).getData().getByteArray(ZeppOsVoiceServiceBridge.EXTRA_VOICE_DATA));
        assertEquals("OPUS", received.get(0).getData().getString(ZeppOsVoiceServiceBridge.EXTRA_VOICE_ENCODING));
        assertEquals(ZeppOsVoiceServiceBridge.MSG_VOICE_END, received.get(1).what);

        // The voice service replies with plain text
        final Message reply = Message.obtain(null, ZeppOsVoiceServiceBridge.MSG_REPLY);
        reply.getData().putString(ZeppOsVoiceServiceBridge.EXTRA_REPLY_TEXT, "Task added");
        received.get(1).replyTo.send(reply);
        shadowOf(Looper.getMainLooper()).idle();

        final List<byte[]> writes = capturedWrites();
        final byte[] last = writes.get(writes.size() - 1);
        assertEquals(0x09, last[0]); // simple text reply
        assertEquals("Task added", new String(last, 1, last.length - 2, "UTF-8"));
    }

    @Test
    public void startAckIsSentWhenVoiceServiceIsUsed() {
        configureVoiceService();
        service.handlePayload(startPayload());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));

        boolean ackSent = false;
        for (final byte[] write : capturedWrites()) {
            ackSent |= write.length == 2 && write[0] == 0x03 && write[1] == 0x00;
        }
        assertTrue(ackSent);
    }

    @Test
    public void missingReplyShowsErrorOnWatch() {
        configureVoiceService();
        service.handlePayload(startPayload());
        service.handlePayload(new byte[]{0x02});
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));

        final List<byte[]> writes = capturedWrites();
        final byte[] last = writes.get(writes.size() - 1);
        assertEquals(0x09, last[0]);
    }

    @Test
    public void recordingIsEndedAtTheLimit() {
        configureVoiceService();
        service.handlePayload(startPayload());
        service.handlePayload(voicePayload(new byte[]{0x11}));
        // Pauses do not matter, only the fixed limit
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        service.handlePayload(voicePayload(new byte[]{0x12}));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));

        assertEquals(3, received.size());
        assertEquals(ZeppOsVoiceServiceBridge.MSG_VOICE_END, received.get(2).what);
        // ...and the watch is told to stop listening
        boolean triggered = false;
        for (final byte[] write : capturedWrites()) {
            triggered |= write.length == 1 && write[0] == 0x06;
        }
        assertTrue(triggered);

        // A late CMD_END does not end the session twice
        service.handlePayload(new byte[]{0x02});
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(3, received.size());
    }

    @Test
    public void isConfigured() {
        assertTrue(ZeppOsVoiceServiceBridge.isConfigured(VOICE_PACKAGE, VOICE_CLASS));
        assertFalse(ZeppOsVoiceServiceBridge.isConfigured("", VOICE_CLASS));
        assertFalse(ZeppOsVoiceServiceBridge.isConfigured(VOICE_PACKAGE, null));
        assertFalse(ZeppOsVoiceServiceBridge.isConfigured(" ", " "));
    }
}
