/*  Copyright (C) 2026 grapemane

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http;

import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiHttp;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class GarminHttpRequestTest extends TestBase {
    private static GarminHttpRequest post(final JsonObject headers, final JsonObject body) throws Exception {
        final GdiHttp.ConnectIQHTTPRequest.Builder builder = GdiHttp.ConnectIQHTTPRequest.newBuilder()
                .setUrl("https://example.com/api")
                .setHttpMethod(GdiHttp.ConnectIQHTTPRequest.HTTPMethod.POST)
                .setHttpBody(ByteString.copyFrom(GarminJson.encode(body)));
        if (headers != null) {
            builder.setHttpHeaderFields(ByteString.copyFrom(GarminJson.encode(headers)));
        }
        return new GarminHttpRequest(builder.build(), 1);
    }

    private static JsonObject contentType(final Object value) {
        final JsonObject headers = new JsonObject();
        if (value instanceof Number n) {
            headers.addProperty("Content-Type", n);
        } else {
            headers.addProperty("Content-Type", (String) value);
        }
        return headers;
    }

    private static JsonObject body() {
        final JsonObject body = new JsonObject();
        body.addProperty("name", "a b&c");
        body.addProperty("count", 3);
        return body;
    }

    @Test
    public void jsonContentTypeEnumIsMappedToMimeType() throws Exception {
        final GarminHttpRequest request = post(contentType(1), body());
        Assert.assertEquals("application/json", request.getHeaders().get("Content-Type"));
        Assert.assertEquals("{\"name\":\"a b&c\",\"count\":3}",
                new String(request.getBodyToSend(), StandardCharsets.UTF_8));
    }

    @Test
    public void urlEncodedContentTypeEnumIsMappedAndBodyFormEncoded() throws Exception {
        final GarminHttpRequest request = post(contentType(0), body());
        Assert.assertEquals("application/x-www-form-urlencoded", request.getHeaders().get("Content-Type"));
        Assert.assertEquals("name=a+b%26c&count=3",
                new String(request.getBodyToSend(), StandardCharsets.UTF_8));
    }

    @Test
    public void literalContentTypeIsKept() throws Exception {
        final GarminHttpRequest request = post(contentType("application/json; charset=utf-8"), body());
        Assert.assertEquals("application/json; charset=utf-8", request.getHeaders().get("Content-Type"));
    }

    @Test
    public void noContentTypeKeepsJsonBody() throws Exception {
        final GarminHttpRequest request = post(null, body());
        Assert.assertNull(request.getHeaders().get("Content-Type"));
        Assert.assertEquals("{\"name\":\"a b&c\",\"count\":3}",
                new String(request.getBodyToSend(), StandardCharsets.UTF_8));
    }

    /**
     * The request's Content-Type (Communications.REQUEST_CONTENT_TYPE_*, carried inside the
     * app's header dictionary) and the expected response type (Communications.HTTP_RESPONSE_CONTENT_TYPE_*,
     * carried in the response_type field) are independent: mapping the first must not change
     * how a response is handed back for any value of the second.
     */
    @Test
    public void requestContentTypeDoesNotAffectResponseHandling() throws Exception {
        final Object[] requestContentTypes = {1, 0, "application/json; charset=utf-8", null};
        final String[] serverContentTypes = {"application/json", "text/plain"};
        for (final GdiHttp.ConnectIQHTTPRequest.ResponseType responseType : GdiHttp.ConnectIQHTTPRequest.ResponseType.values()) {
            for (final String serverContentType : serverContentTypes) {
                final GdiHttp.ConnectIQHTTPResponse baseline = HttpHandler.createWebResponse(
                        post(null, body(), responseType), serverResponse(serverContentType));
                for (final Object requestContentType : requestContentTypes) {
                    final String label = "request " + requestContentType + ", response_type " + responseType
                            + ", server " + serverContentType;
                    final GarminHttpRequest request = post(
                            requestContentType == null ? null : contentType(requestContentType), body(), responseType);
                    Assert.assertEquals(label, responseType, request.getWebRequest().getResponseType());
                    Assert.assertEquals(label, baseline,
                            HttpHandler.createWebResponse(request, serverResponse(serverContentType)));
                }
            }
        }
    }

    private static GarminHttpRequest post(final JsonObject headers, final JsonObject body,
                                          final GdiHttp.ConnectIQHTTPRequest.ResponseType responseType) throws Exception {
        final GdiHttp.ConnectIQHTTPRequest.Builder builder = post(headers, body).getWebRequest().toBuilder()
                .setResponseType(responseType)
                .setMaxResponseLength(4096);
        return new GarminHttpRequest(builder.build(), 1);
    }

    private static GarminHttpResponse serverResponse(final String contentType) {
        final GarminHttpResponse response = new GarminHttpResponse();
        response.setStatus(200);
        response.getHeaders().put("Content-Type", contentType);
        response.setBody("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        return response;
    }
}
