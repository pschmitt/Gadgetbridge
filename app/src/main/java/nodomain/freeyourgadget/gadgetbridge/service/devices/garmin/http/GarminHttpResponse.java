package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class GarminHttpResponse {
    private boolean complete = true;
    private int status = 200;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private byte[] body = new byte[0];

    private Callable<Void> onDataSuccessfullySentListener;

    public GarminHttpResponse() {
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(final boolean complete) {
        this.complete = complete;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(final int status) {
        this.status = status;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Returns the value of a header, using a case-insensitive name.
     */
    @Nullable
    public String getHeader(final String name) {
        for (final Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getKey().equalsIgnoreCase(name)) {
                return header.getValue();
            }
        }
        return null;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(final byte[] body) {
        this.body = body;
    }

    public Callable<Void> getOnDataSuccessfullySentListener() {
        return onDataSuccessfullySentListener;
    }

    public void setOnDataSuccessfullySentListener(final Callable<Void> onDataSuccessfullySentListener) {
        this.onDataSuccessfullySentListener = onDataSuccessfullySentListener;
    }
}
