package com.fundatechperu.shareway.booking.infrastructure.integration;

import com.fundatechperu.shareway.booking.infrastructure.security.InternalApiKey;
import com.fundatechperu.shareway.booking.infrastructure.security.InternalApiProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

/**
 * POSTs JSON to another ShareWay service with the X-Internal-Api-Key header. Redirects are not
 * followed (a 3xx is retried like a server error) and the 90 s timeout covers the cold start of
 * free Render services. It never throws for an HTTP status or an I/O error: both are reported
 * in the {@link HttpResult}.
 */
@Component
public class InternalHttpClient {

    private final RestClient restClient;

    public InternalHttpClient(IntegrationProperties properties, InternalApiKey internalApiKey) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(InternalApiProperties.API_KEY_HEADER, internalApiKey.value())
                .build();
    }

    public HttpResult post(String url, String jsonBody) {
        try {
            return restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .exchange((request, response) -> new HttpResult(response.getStatusCode().value(),
                            StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8), null));
        } catch (ResourceAccessException exception) {
            // Timeouts, refused connections, DNS and other I/O errors.
            return new HttpResult(0, null, exception.getMessage());
        }
    }

    /**
     * @param status  HTTP status, or 0 when no answer arrived (ioError then explains why)
     * @param body    answer body (may be empty)
     * @param ioError I/O error message when the call could not complete
     */
    public record HttpResult(int status, String body, String ioError) {

        public boolean isIoError() {
            return status == 0;
        }
    }
}
