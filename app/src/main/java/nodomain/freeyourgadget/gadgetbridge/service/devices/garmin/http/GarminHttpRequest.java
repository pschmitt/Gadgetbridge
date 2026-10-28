package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http;

import android.net.Uri;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiHttp;
import nodomain.freeyourgadget.gadgetbridge.util.HttpUtils;

public class GarminHttpRequest {
    private static final Logger LOG = LoggerFactory.getLogger(GarminHttpRequest.class);

    private final GdiHttp.RawResourceRequest rawRequest;
    private final GdiHttp.ConnectIQHTTPRequest webRequest;

    private final int messageRequestId;

    private final String method;
    private final Uri uri;
    private final Map<String, String> headers;

    public GarminHttpRequest(final GdiHttp.RawResourceRequest rawRequest,
                             final int messageRequestId) {
        this.messageRequestId = messageRequestId;
        this.rawRequest = rawRequest;
        this.webRequest = null;
        this.method = rawRequest.getMethod().name();
        this.uri = Uri.parse(rawRequest.getUrl());
        this.headers = headersToMap(rawRequest.getHeadersList());
    }

    public GarminHttpRequest(final GdiHttp.ConnectIQHTTPRequest webRequest,
                             final int messageRequestId) throws GarminJsonException {
        this.messageRequestId = messageRequestId;
        this.rawRequest = null;
        this.webRequest = webRequest;
        this.method = webRequest.getHttpMethod().name();
        this.uri = Uri.parse(webRequest.getUrl());
        this.headers = new LinkedHashMap<>();
        if (!webRequest.getHttpHeaderFields().isEmpty()) {
            final JsonElement garminHeaders = GarminJson.decode(webRequest.getHttpHeaderFields().toByteArray());
            if (garminHeaders instanceof JsonObject jsonObject) {
                for (String key : jsonObject.keySet()) {
                    headers.put(key, jsonObject.get(key).getAsString());
                }
            }
        }
    }

    public int getMessageRequestId() {
        return messageRequestId;
    }

    public GdiHttp.RawResourceRequest getRawRequest() {
        return rawRequest;
    }

    public GdiHttp.ConnectIQHTTPRequest getWebRequest() {
        return webRequest;
    }

    public Uri getUri() {
        return uri;
    }

    public String getDomain() {
        return uri.getHost();
    }

    public String getUrl() {
        return rawRequest != null ? rawRequest.getUrl() : webRequest.getUrl();
    }

    public String getPath() {
        return uri.getPath();
    }

    public byte[] getBody() {
        return rawRequest != null ? rawRequest.getRawBody().toByteArray() : webRequest.getHttpBody().toByteArray();
    }

    public byte[] getBodyToSend() {
        if (rawRequest != null) {
            if (rawRequest.hasBody()) {
                return rawRequest.getBody().getBytes(StandardCharsets.UTF_8);
            }
            return rawRequest.getRawBody().toByteArray();
        } else {
            final byte[] webRequestBodyBytes = webRequest.getHttpBody().toByteArray();
            if (webRequestBodyBytes != null) {
                try {
                    return GarminJson.decode(webRequestBodyBytes).toString().getBytes(StandardCharsets.UTF_8);
                } catch (final GarminJsonException e) {
                    LOG.error("WebRequest body is not GarminJson, will send raw bytes", e);
                    return webRequestBodyBytes;
                }
            }
        }
        return null;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getQuery() {
        return HttpUtils.urlQueryParameters(uri.getQuery());
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    private static Map<String, String> headersToMap(final List<GdiHttp.RawResourceRequest.HTTPHeader> headers) {
        final Map<String, String> ret = new HashMap<>();
        for (final GdiHttp.RawResourceRequest.HTTPHeader header : headers) {
            ret.put(header.getKey().toLowerCase(Locale.ROOT), header.getValue());
        }
        return ret;
    }
}
