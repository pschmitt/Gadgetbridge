package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http;

import android.net.Uri;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
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

    // Connect IQ apps set the request body's Content-Type with a Communications.REQUEST_CONTENT_TYPE_*
    // value, which reaches us as that enum's number. Garmin Connect maps it to the MIME type.
    private static final String CIQ_CONTENT_TYPE_URL_ENCODED = "0";
    private static final String CIQ_CONTENT_TYPE_JSON = "1";
    private static final String MIME_URL_ENCODED = "application/x-www-form-urlencoded";
    private static final String MIME_JSON = "application/json";

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
                    String value = jsonObject.get(key).getAsString();
                    if ("content-type".equalsIgnoreCase(key)) {
                        value = mimeTypeOf(value);
                    }
                    headers.put(key, value);
                }
            }
        }
    }

    private static String mimeTypeOf(final String connectIqContentType) {
        return switch (connectIqContentType) {
            case CIQ_CONTENT_TYPE_URL_ENCODED -> MIME_URL_ENCODED;
            case CIQ_CONTENT_TYPE_JSON -> MIME_JSON;
            default -> connectIqContentType;
        };
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
                    final JsonElement body = GarminJson.decode(webRequestBodyBytes);
                    if (isUrlEncoded() && body instanceof JsonObject form) {
                        return urlEncode(form).getBytes(StandardCharsets.UTF_8);
                    }
                    return body.toString().getBytes(StandardCharsets.UTF_8);
                } catch (final GarminJsonException e) {
                    LOG.error("WebRequest body is not GarminJson, will send raw bytes", e);
                    return webRequestBodyBytes;
                }
            }
        }
        return null;
    }

    private boolean isUrlEncoded() {
        for (final Map.Entry<String, String> header : headers.entrySet()) {
            if ("content-type".equalsIgnoreCase(header.getKey())) {
                return header.getValue().startsWith(MIME_URL_ENCODED);
            }
        }
        return false;
    }

    private static String urlEncode(final JsonObject form) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, JsonElement> e : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            final JsonElement value = e.getValue();
            final String text = value instanceof JsonPrimitive primitive ? primitive.getAsString()
                    : value.isJsonNull() ? "" : value.toString();
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(text, StandardCharsets.UTF_8));
        }
        return sb.toString();
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
