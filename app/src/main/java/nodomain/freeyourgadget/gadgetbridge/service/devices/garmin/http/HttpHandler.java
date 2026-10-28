/*  Copyright (C) 2024-2026 José Rebelo, kuhy, Daniele Gobbetti, Thomas Kuehne

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

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTypes;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiHttp;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.AgpsInterceptor;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.ContactsInterceptor;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.FirewallInterceptor;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.HttpInterceptor;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.HydrationInterceptor;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.ImageServiceInterceptor;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.OauthInterceptor;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.WeatherInterceptor;

@SuppressWarnings("ClassCanBeRecord")
public class HttpHandler {
    private static final Logger LOG = LoggerFactory.getLogger(HttpHandler.class);

    private final List<HttpInterceptor> interceptors;

    public HttpHandler(final GarminSupport deviceSupport) {
        this.interceptors = Arrays.asList(
                new WeatherInterceptor(),
                new AgpsInterceptor(deviceSupport),
                new ImageServiceInterceptor(deviceSupport),
                new ContactsInterceptor(deviceSupport),
                new OauthInterceptor(deviceSupport),
                new HydrationInterceptor(deviceSupport),
                // Should always be the last one
                new FirewallInterceptor(deviceSupport)
        );
    }

    public GdiHttp.ConnectIQHTTPService handle(final GdiHttp.ConnectIQHTTPService httpService, final int messageRequestId) {
        if (httpService.hasRawResourceRequest()) {
            final GdiHttp.RawResourceResponse rawResponse = handleRawRequest(httpService.getRawResourceRequest(), messageRequestId);
            if (rawResponse != null) {
                return GdiHttp.ConnectIQHTTPService.newBuilder()
                        .setRawResourceResponse(rawResponse)
                        .build();
            }
            return null;
        } else if (httpService.hasConnectIqHttpRequest()) {
            final GdiHttp.ConnectIQHTTPResponse webResponse = handleWebRequest(httpService.getConnectIqHttpRequest(), messageRequestId);
            if (webResponse != null) {
                return GdiHttp.ConnectIQHTTPService.newBuilder()
                        .setConnectIqHttpResponse(webResponse)
                        .build();
            }
            return null;
        } else if (httpService.hasOpenWebpageNotification()) {
            return handleShowURLRequest(httpService.getOpenWebpageNotification(), messageRequestId);
        }

        LOG.warn("Unsupported http service request {}", httpService);

        return null;
    }

    public GdiHttp.RawResourceResponse handleRawRequest(final GdiHttp.RawResourceRequest rawRequest,
                                                                   final int messageRequestId) {
        LOG.debug("Got rawRequest {}: {} - {}", messageRequestId, rawRequest.getMethod(), rawRequest.getUrl());

        if (rawRequest.getFormsCount() > 0) {
            LOG.warn("RawRequest {} requires unsupported MultiPartForm support: {}", messageRequestId, rawRequest);
            return null;
        }

        final GarminHttpRequest request = new GarminHttpRequest(rawRequest, messageRequestId);

        final GarminHttpResponse response = handleRequest(request);

        if (response == null) {
            return GdiHttp.RawResourceResponse.newBuilder()
                    .setStatus(GdiHttp.RawResourceResponse.ResponseStatus.NETWORK_REQUEST_TIMED_OUT)
                    .build();
        }

        if (!response.isComplete()) {
            // Async response
            return null;
        }

        return createRawResponse(request, response);
    }

    @Nullable
    private GarminHttpResponse handleRequest(final GarminHttpRequest request) {
        final Optional<HttpInterceptor> interceptorOpt = interceptors.stream()
                .filter(it -> it.supports(request))
                .findFirst();

        if (interceptorOpt.isEmpty()) {
            LOG.warn("No interceptor for {}", request.getPath());
            return null;
        }

        final HttpInterceptor interceptor = interceptorOpt.get();
        LOG.debug("Handling request to {}", interceptor.getClass().getSimpleName());
        final GarminHttpResponse response = interceptor.handle(request);
        if (response != null) {
            LOG.debug(
                    "Response from interceptor: {}, {} bytes, complete={}",
                    response.getStatus(),
                    response.getBody().length,
                    response.isComplete()
            );
        } else {
            LOG.debug("No response from interceptor");
        }
        return response;
    }

    public static GdiHttp.RawResourceResponse createRawResponse(
            final GarminHttpRequest request,
            final GarminHttpResponse response
    ) {
        final List<GdiHttp.RawResourceResponse.HTTPHeader> responseHeaders = new ArrayList<>();
        for (final Map.Entry<String, String> h : response.getHeaders().entrySet()) {
            responseHeaders.add(
                    GdiHttp.RawResourceResponse.HTTPHeader.newBuilder()
                            .setKey(h.getKey())
                            .setValue(h.getValue())
                            .build()
            );
        }

        if (request.getRawRequest().hasUseDataXfer() && request.getRawRequest().getUseDataXfer()) {
            LOG.debug("Data will be returned using data_xfer");
            final int id = DataTransferHandler.registerData(response.getBody());
            if (response.getOnDataSuccessfullySentListener() != null) {
                DataTransferHandler.addOnDataSuccessfullySentListener(id, response.getOnDataSuccessfullySentListener());
            }
            return GdiHttp.RawResourceResponse.newBuilder()
                    .setStatus(GdiHttp.RawResourceResponse.ResponseStatus.OK)
                    .setHttpStatusCode(response.getStatus())
                    .addAllHeaders(responseHeaders)
                    .setXferData(
                        GdiDataTypes.DataTransferItem.newBuilder()
                                    .setId(id)
                                    .setSize(response.getBody().length)
                                    .build()
                    )
                    .build();
        }

        GdiDataTypes.DataTransferItem x;

        final byte[] responseBody;
        if ("gzip".equals(request.getHeaders().get("accept-encoding"))) {
            LOG.debug("Compressing response");
            responseHeaders.add(
                    GdiHttp.RawResourceResponse.HTTPHeader.newBuilder()
                            .setKey("Content-Encoding")
                            .setValue("gzip")
                            .build()
            );

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(response.getBody());
                gzos.finish();
                gzos.flush();
                responseBody = baos.toByteArray();
            } catch (final Exception e) {
                LOG.error("Failed to compress response", e);
                return null;
            }
        } else {
            responseBody = response.getBody();
        }

        return GdiHttp.RawResourceResponse.newBuilder()
                .setStatus(GdiHttp.RawResourceResponse.ResponseStatus.OK)
                .setHttpStatusCode(response.getStatus())
                .setResourceData(ByteString.copyFrom(responseBody))
                .addAllHeaders(responseHeaders)
                .build();
    }

    public GdiHttp.ConnectIQHTTPService handleShowURLRequest(final GdiHttp.OpenWebpageNotification showURLNotification,
                                                           final int messageRequestId) {
        LOG.warn("Got unsupported ShowUrlNotification {}: {} {} {}",
                messageRequestId,
                showURLNotification.hasUrl() ? showURLNotification.getUrl() : null,
                showURLNotification.hasParams() ? showURLNotification.getParams() : null,
                showURLNotification.hasAppName() ? showURLNotification.getAppName() : null);
        // TODO: ask the user if they want to open the web page
        return null;
    }

    public GdiHttp.ConnectIQHTTPResponse handleWebRequest(final GdiHttp.ConnectIQHTTPRequest webRequest,
                                                                   final int messageRequestId) {
        LOG.debug("Got webRequest {}: {} {}", messageRequestId, webRequest.getHttpMethod(), webRequest.getUrl());

        try {
            final GarminHttpRequest request = new GarminHttpRequest(webRequest, messageRequestId);

            final GarminHttpResponse response = handleRequest(request);

            if (response == null) {
                return GdiHttp.ConnectIQHTTPResponse.newBuilder()
                        .setStatus(GdiHttp.ConnectIQHTTPResponse.ResponseStatus.NETWORK_REQUEST_TIMED_OUT)
                        .setHttpStatusCode(0)
                        .build();
            }

            if (!response.isComplete()) {
                // Async response
                return null;
            }

            return createWebResponse(request, response);
        } catch (final Exception e) {
            LOG.error("Failed to create web response", e);
            return GdiHttp.ConnectIQHTTPResponse.newBuilder()
                    .setStatus(GdiHttp.ConnectIQHTTPResponse.ResponseStatus.UNKNOWN)
                    .setHttpStatusCode(0)
                    .build();
        }
    }

    public static GdiHttp.ConnectIQHTTPService createSuccessResponse(final GarminHttpRequest request,
                                                                   final GarminHttpResponse response) {
        if (request.getRawRequest() != null) {
            final GdiHttp.RawResourceResponse rawResponse = createRawResponse(request, response);
            if (rawResponse != null) {
                return GdiHttp.ConnectIQHTTPService.newBuilder()
                        .setRawResourceResponse(rawResponse)
                        .build();
            }
            return null;
        } else if (request.getWebRequest() != null) {
            final GdiHttp.ConnectIQHTTPResponse webResponse = createWebResponse(request, response);
            if (webResponse != null) {
                return GdiHttp.ConnectIQHTTPService.newBuilder()
                        .setConnectIqHttpResponse(webResponse)
                        .build();
            }
            return null;
        } else {
            throw new IllegalArgumentException("Should never happen");
        }
    }

    public static GdiHttp.ConnectIQHTTPService createErrorResponse(final GarminHttpRequest request) {
        if (request.getRawRequest() != null) {
            return GdiHttp.ConnectIQHTTPService.newBuilder()
                    .setRawResourceResponse(GdiHttp.RawResourceResponse.newBuilder()
                            .setStatus(GdiHttp.RawResourceResponse.ResponseStatus.UNKNOWN)
                            .build())
                    .build();
        } else if (request.getWebRequest() != null) {
            return GdiHttp.ConnectIQHTTPService.newBuilder()
                    .setConnectIqHttpResponse(GdiHttp.ConnectIQHTTPResponse.newBuilder()
                            .setStatus(GdiHttp.ConnectIQHTTPResponse.ResponseStatus.UNKNOWN)
                            .setHttpStatusCode(0)
                            .build())
                    .build();
        } else {
            throw new IllegalArgumentException("Should never happen");
        }
    }

    public static GdiHttp.ConnectIQHTTPResponse createWebResponse(
            final GarminHttpRequest request,
            final GarminHttpResponse response
    ) {
        final GdiHttp.ConnectIQHTTPRequest webRequest = request.getWebRequest();
        try {
            final byte[] headers;
            if (webRequest.getIncludeHttpHeaderFieldsInResponse()) {
                byte[] encodedHeaders = null;
                try {
                    final JsonObject jsonObject = new JsonObject();
                    for (Map.Entry<String, String> e : response.getHeaders().entrySet()) {
                        jsonObject.addProperty(e.getKey(), e.getValue());
                    }
                    encodedHeaders = GarminJson.encode(jsonObject);
                } catch (final GarminJsonException e) {
                    LOG.error("Failed to encode headers", e);
                }
                headers = encodedHeaders;
            } else {
                headers = null;
            }

            if (response.getBody().length > webRequest.getMaxResponseLength()) {
                // TODO figure out the compression algorithm
                return GdiHttp.ConnectIQHTTPResponse.newBuilder()
                        .setStatus(GdiHttp.ConnectIQHTTPResponse.ResponseStatus.NETWORK_RESPONSE_TOO_LARGE)
                        .setHttpStatusCode(0)
                        .build();
            }

            final JsonElement jsonElement;
            final String contentType = response.getHeader("content-type");
            if (contentType != null && contentType.startsWith("application/json")) {
                try (final InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(response.getBody()))) {
                    jsonElement = JsonParser.parseReader(reader);
                } catch (IOException e) {
                    LOG.error("Failed to decode body as JSON", e);
                    return GdiHttp.ConnectIQHTTPResponse.newBuilder()
                            .setStatus(GdiHttp.ConnectIQHTTPResponse.ResponseStatus.INVALID_HTTP_BODY_IN_NETWORK_RESPONSE)
                            .setHttpStatusCode(0)
                            .build();
                }
            } else if (webRequest.getResponseType() == GdiHttp.ConnectIQHTTPRequest.ResponseType.JSON) {
                // We got non-json
                return GdiHttp.ConnectIQHTTPResponse.newBuilder()
                        .setStatus(GdiHttp.ConnectIQHTTPResponse.ResponseStatus.INVALID_HTTP_BODY_IN_NETWORK_RESPONSE)
                        .setHttpStatusCode(response.getStatus())
                        .build();
            } else {
                jsonElement = new JsonPrimitive(new String(response.getBody(), StandardCharsets.UTF_8));
            }
            final byte[] body = GarminJson.encode(jsonElement);

            final GdiHttp.ConnectIQHTTPResponse.Builder builder = GdiHttp.ConnectIQHTTPResponse.newBuilder()
                    .setStatus(GdiHttp.ConnectIQHTTPResponse.ResponseStatus.OK)
                    .setHttpStatusCode(response.getStatus())
                    .setHttpBody(ByteString.copyFrom(body))
                    .setInflatedSize(0); // 0 for non-compressed

            if (headers != null) {
                builder.setHttpHeaderFields(ByteString.copyFrom(headers));
            }

            return builder.build();
        } catch (final GarminJsonException e) {
            LOG.error("Garmin JSON error (?)", e);
            return null;
        }
    }
}
