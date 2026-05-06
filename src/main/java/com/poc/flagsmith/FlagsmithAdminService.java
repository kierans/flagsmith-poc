package com.poc.flagsmith;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP client for the Flagsmith Management (Admin) API.
 */
public class FlagsmithAdminService {
    private static final int PAGE_SIZE = 10;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiUrl;
    private final String environmentId;
    private final String authorisationToken;

    public FlagsmithAdminService(String apiUrl, String environmentId, String authorisationToken) {
        this.apiUrl = apiUrl;
        this.environmentId = environmentId;
        this.authorisationToken = authorisationToken;
    }

    /**
     * Fetches one page of edge identities for the configured environment.
     *
     * @param lastEvaluatedKey cursor returned by the previous page, or {@code null} for the first page
     * @return the raw API response node containing {@code results} and optionally {@code last_evaluated_key}
     */
    public JsonNode fetchEdgeIdentities(String lastEvaluatedKey) throws Exception {
        StringBuilder url = new StringBuilder()
            .append(apiUrl)
            .append("/environments/")
            .append(environmentId)
            .append("/edge-identities/?page_size=")
            .append(PAGE_SIZE);

        if (lastEvaluatedKey != null) {
            url.append("&last_evaluated_key=")
               .append(URLEncoder.encode(lastEvaluatedKey, StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url.toString()))
            .header("Authorization", "Token " + authorisationToken)
            .header("Content-Type", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to list edge identities. Status: " + response.statusCode());
        }

        return objectMapper.readTree(response.body());
    }

    /**
     * Deletes a single edge identity by UUID.
     *
     * @return the HTTP status code (204 on success)
     */
    public int deleteEdgeIdentity(String identityUuid) throws Exception {
        String url = String.format(
            "%s/environments/%s/edge-identities/%s/",
            apiUrl,
            environmentId,
            identityUuid
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Token " + authorisationToken)
            .header("Content-Type", "application/json")
            .DELETE()
            .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        return response.statusCode();
    }
}
