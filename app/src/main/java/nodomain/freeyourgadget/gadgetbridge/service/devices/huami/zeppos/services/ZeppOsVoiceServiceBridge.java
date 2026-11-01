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

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Forwards voice captured by the watch assistant to an external voice service app on the phone, and
 * relays the text reply of that app back to the watch.
 * <p>
 * Uses the same contract as the Fossil HR voice service: Gadgetbridge binds to the configured
 * component with action {@link #ACTION_VOICE_COMMAND} and sends {@link Message}s to its
 * {@link Messenger}:
 * <ul>
 *     <li>{@link #MSG_VOICE_DATA}: one Opus frame in {@link #EXTRA_VOICE_DATA}, with
 *     {@link #EXTRA_VOICE_ENCODING} ("OPUS", 16 kHz mono).</li>
 *     <li>{@link #MSG_VOICE_END}: the watch stopped recording.</li>
 * </ul>
 * Every message carries a {@code replyTo} messenger. The voice service may answer with
 * {@link #MSG_REPLY} (extras {@link #EXTRA_REPLY_TITLE}, {@link #EXTRA_REPLY_SUBTITLE},
 * {@link #EXTRA_REPLY_TEXT}) or {@link #MSG_ERROR} ({@link #EXTRA_REPLY_TEXT}).
 */
public class ZeppOsVoiceServiceBridge {
    private static final Logger LOG = LoggerFactory.getLogger(ZeppOsVoiceServiceBridge.class);

    public static final String ACTION_VOICE_COMMAND = "nodomain.freeyourgadget.gadgetbridge.VOICE_COMMAND";

    public static final int MSG_VOICE_DATA = 0;
    public static final int MSG_VOICE_END = 1;
    public static final int MSG_REPLY = 100;
    public static final int MSG_ERROR = 101;

    public static final String EXTRA_VOICE_DATA = "VOICE_DATA";
    public static final String EXTRA_VOICE_ENCODING = "VOICE_ENCODING";
    public static final String EXTRA_REPLY_TITLE = "TITLE";
    public static final String EXTRA_REPLY_SUBTITLE = "SUBTITLE";
    public static final String EXTRA_REPLY_TEXT = "TEXT";

    /** Frames kept while the service is still binding: ~60s of 20ms frames. */
    private static final int MAX_PENDING_FRAMES = 3000;
    private static final long REPLY_TIMEOUT_MS = 15_000;

    public interface Callback {
        void onVoiceServiceReply(@Nullable String title, @Nullable String subtitle, @Nullable String text);

        void onVoiceServiceError(@NonNull String message);
    }

    private final Context context;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Messenger replyMessenger = new Messenger(new Handler(Looper.getMainLooper(), this::handleReply));
    private final List<Message> pending = new ArrayList<>();

    @Nullable
    private Messenger serviceMessenger;
    private boolean bound;
    private boolean sessionActive;
    private boolean ended;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            LOG.info("Attached to voice service {}", name);
            serviceMessenger = new Messenger(service);
            for (final Message message : pending) {
                send(message);
            }
            pending.clear();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            LOG.info("Detached from voice service {}", name);
            serviceMessenger = null;
        }
    };

    public ZeppOsVoiceServiceBridge(final Context context, final Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public static boolean isConfigured(@Nullable final String servicePackage, @Nullable final String serviceClass) {
        return servicePackage != null && !servicePackage.trim().isEmpty()
                && serviceClass != null && !serviceClass.trim().isEmpty();
    }

    /**
     * Binds to the voice service for a new watch voice session.
     *
     * @return false if the service could not be bound
     */
    public boolean start(final String servicePackage, final String serviceClass) {
        stop();

        final Intent intent = new Intent(ACTION_VOICE_COMMAND);
        intent.setComponent(new ComponentName(servicePackage.trim(), serviceClass.trim()));

        try {
            bound = context.bindService(intent, connection, Service.BIND_AUTO_CREATE);
        } catch (final SecurityException e) {
            LOG.error("Not allowed to bind to voice service", e);
            bound = false;
        }

        if (!bound) {
            LOG.warn("Failed to bind to voice service {}/{}", servicePackage, serviceClass);
            return false;
        }

        sessionActive = true;
        ended = false;
        return true;
    }

    public boolean isActive() {
        return sessionActive;
    }

    public void sendVoiceFrame(final byte[] opusFrame) {
        if (!sessionActive || ended) {
            return;
        }
        final Message message = Message.obtain(null, MSG_VOICE_DATA);
        final Bundle data = new Bundle(2);
        data.putByteArray(EXTRA_VOICE_DATA, opusFrame);
        data.putString(EXTRA_VOICE_ENCODING, "OPUS");
        message.setData(data);
        enqueue(message);
    }

    /** The watch finished recording; wait for the reply of the voice service. */
    public void end() {
        if (!sessionActive || ended) {
            return;
        }
        ended = true;
        enqueue(Message.obtain(null, MSG_VOICE_END));
        handler.postDelayed(() -> {
            LOG.warn("No reply from voice service after {}ms", REPLY_TIMEOUT_MS);
            callback.onVoiceServiceError("No reply from voice service");
            stop();
        }, REPLY_TIMEOUT_MS);
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        pending.clear();
        sessionActive = false;
        serviceMessenger = null;
        if (bound) {
            try {
                context.unbindService(connection);
            } catch (final IllegalArgumentException e) {
                LOG.warn("Voice service was not bound", e);
            }
            bound = false;
        }
    }

    private void enqueue(final Message message) {
        message.replyTo = replyMessenger;
        if (serviceMessenger != null) {
            send(message);
        } else if (pending.size() < MAX_PENDING_FRAMES) {
            pending.add(message);
        } else {
            LOG.warn("Voice service not connected, dropping message {}", message.what);
        }
    }

    private void send(final Message message) {
        if (serviceMessenger == null) {
            return;
        }
        try {
            serviceMessenger.send(message);
        } catch (final RemoteException e) {
            LOG.error("Failed to send message to voice service", e);
            callback.onVoiceServiceError("Voice service not reachable");
            stop();
        }
    }

    private boolean handleReply(@NonNull final Message message) {
        if (!sessionActive) {
            LOG.warn("Ignoring voice service message {} outside of a session", message.what);
            return true;
        }
        final Bundle data = message.getData();
        LOG.info("Voice service replied with message {}", message.what);
        switch (message.what) {
            case MSG_REPLY:
                callback.onVoiceServiceReply(
                        data.getString(EXTRA_REPLY_TITLE),
                        data.getString(EXTRA_REPLY_SUBTITLE),
                        data.getString(EXTRA_REPLY_TEXT)
                );
                stop();
                return true;
            case MSG_ERROR:
                final String text = data.getString(EXTRA_REPLY_TEXT);
                callback.onVoiceServiceError(text != null ? text : "Voice service error");
                stop();
                return true;
            default:
                LOG.warn("Unknown voice service message {}", message.what);
                return false;
        }
    }
}
