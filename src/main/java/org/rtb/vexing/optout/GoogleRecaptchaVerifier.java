package org.rtb.vexing.optout;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.config.ApplicationConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class GoogleRecaptchaVerifier {

    private static final Logger logger = LoggerFactory.getLogger(GoogleRecaptchaVerifier.class);

    private final HttpClient httpClient;
    private final String recaptchaUrl;
    private final String recaptchaSecret;

    private GoogleRecaptchaVerifier(HttpClient httpClient, String recaptchaUrl, String recaptchaSecret) {
        this.httpClient = httpClient;
        this.recaptchaUrl = recaptchaUrl;
        this.recaptchaSecret = recaptchaSecret;
    }

    public static GoogleRecaptchaVerifier create(HttpClient httpClient, ApplicationConfig config) {
        Objects.requireNonNull(httpClient);
        Objects.requireNonNull(config);

        return new GoogleRecaptchaVerifier(httpClient, config.getString("recaptcha_url"),
                config.getString("recaptcha_secret"));
    }

    public Future<Void> verify(String recaptcha) {
        Objects.requireNonNull(recaptcha);

        final QueryStringEncoder encoder = new QueryStringEncoder("", StandardCharsets.UTF_8);
        encoder.addParam("secret", recaptchaSecret);
        encoder.addParam("response", recaptcha);
        final String encodedBody = encoder.toString().substring(1);

        final Future<Void> future = Future.future();
        httpClient.postAbs(recaptchaUrl, response -> handleResponse(response, future))
                .exceptionHandler(exception -> handleException(exception, future))
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .end(encodedBody);
        return future;
    }

    private void handleResponse(HttpClientResponse response, Future<Void> future) {
        response
                .bodyHandler(buffer -> handleResponseAndBody(response.statusCode(), buffer.toString(), future))
                .exceptionHandler(exception -> handleException(exception, future));
    }

    private void handleResponseAndBody(int statusCode, String body, Future<Void> future) {
        if (statusCode != 200) {
            logger.warn("Google recaptcha response code is {0}, body: {1}", statusCode, body);
            future.fail(new OptoutException(String.format("HTTP status code %d", statusCode)));
            return;
        }

        final RecaptchaResponse response;
        try {
            response = Json.decodeValue(body, RecaptchaResponse.class);
        } catch (DecodeException e) {
            future.fail(new OptoutException(String.format("Cannot parse Google recaptcha response: %s", body)));
            return;
        }

        if (Objects.equals(response.success, Boolean.TRUE)) {
            future.complete();
        } else {
            final String errors = response.errorCodes != null ? String.join(", ", response.errorCodes) : null;
            future.fail(new OptoutException(String.format("Google recaptcha verify failed: %s", errors)));
        }
    }

    private void handleException(Throwable exception, Future<Void> future) {
        logger.warn("Error occurred while sending request to verify google recaptcha", exception);
        future.fail(exception);
    }

    @Builder
    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class RecaptchaResponse {

        Boolean success;

        @JsonProperty("error-codes")
        List<String> errorCodes;
    }
}