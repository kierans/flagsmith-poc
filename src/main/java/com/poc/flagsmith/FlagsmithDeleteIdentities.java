package com.poc.flagsmith;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * "Script" to delete identifies from a Flagsmith environment.
 * <br>
 * Useful to clean up an environment after running a test.
 * <br>
 * Configuration:
 *   src/main/resources/config.properties → set flagsmith.admin.key and flagsmith.environment.id
 *   OR pass -Dflagsmith.admin.key=<key> -Dflagsmith.environment.id=<ID> on the command line.
 */
public class FlagsmithDeleteIdentities {
    private static final String BASE_URL = "https://api.flagsmith.com/api/v1";
    private static final int PAGE_SIZE = 100;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        new FlagsmithDeleteIdentities().deleteAllIdentities();
    }

    public void deleteAllIdentities() {
        int deleted = 0;
        int page = 1;

        ConfigService config = new ConfigService();

        try {
            while (true) {
                String url = String.format(
                    "%s/environments/%s/edge-identities/?page=%d&page_size=%d",
                    BASE_URL, config.resolveEnvironmentId(), page, PAGE_SIZE
                );

                HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token " + config.resolveAdminKey())
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> listResponse = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString());

                if (listResponse.statusCode() != 200) {
                    System.err.println("Failed to list identities. Status: " + listResponse.statusCode());
                    break;
                }

                JsonNode data = objectMapper.readTree(listResponse.body());
                JsonNode results = data.get("results");

                if (results == null || results.isEmpty()) {
                    break;
                }

                for (JsonNode identity : results) {
                    String identityId = identity.get("identity_uuid").asText();
                    String identifier = identity.get("identifier").asText();

                    String deleteUrl = String.format(
                        "%s/environments/%s/edge-identities/%s/",
                        BASE_URL, config.resolveEnvironmentId(), identityId
                    );

                    HttpRequest deleteRequest = HttpRequest.newBuilder()
                        .uri(URI.create(deleteUrl))
                        .header("Authorization", "Token " + config.resolveAdminKey())
                        .header("Content-Type", "application/json")
                        .DELETE()
                        .build();

                    HttpResponse<Void> deleteResponse = httpClient.send(
                        deleteRequest, HttpResponse.BodyHandlers.discarding()
                    );

                    if (deleteResponse.statusCode() == 204) {
                        deleted++;
                        System.out.printf("Deleted identity: %s (ID: %s)%n", identifier, identityId);
                    } else {
                        System.err.printf("Failed to delete %s. Status: %d%n", identityId, deleteResponse.statusCode());
                    }

                    // Small delay to avoid hitting rate limits
                    Thread.sleep(100);
                }

                JsonNode next = data.get("next");
                if (next == null || next.isNull()) {
                    break;
                }

                page++;
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.printf("%nDone. Deleted %d identities.%n", deleted);
    }
}
