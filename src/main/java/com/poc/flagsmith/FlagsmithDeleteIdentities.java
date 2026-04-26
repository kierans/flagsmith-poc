package com.poc.flagsmith;

import com.fasterxml.jackson.databind.JsonNode;

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

    public static void main(String[] args) throws Exception {
        new FlagsmithDeleteIdentities().deleteAllIdentities();
    }

    public void deleteAllIdentities() throws Exception {
        int deleted = 0;
        String lastEvaluatedKey = null;

        ConfigService config = new ConfigService();
        FlagsmithAdminService adminService = new FlagsmithAdminService(
            config.resolveEnvironmentId(),
            config.resolveAdminKey()
        );

        try {
            while (true) {
                JsonNode data = adminService.fetchEdgeIdentities(lastEvaluatedKey);
                JsonNode results = data.get("results");

                if (results == null || results.isEmpty()) {
                    break;
                }

                for (JsonNode identity : results) {
                    String identityId = identity.get("identity_uuid").asText();
                    String identifier = identity.get("identifier").asText();

                    int status = adminService.deleteEdgeIdentity(identityId);

                    if (status == 204) {
                        deleted++;
                        System.out.printf("Deleted identity: %s (ID: %s)%n", identifier, identityId);
                    } else {
                        System.err.printf("Failed to delete %s. Status: %d%n", identityId, status);
                    }

                    // Small delay to avoid hitting rate limits
                    Thread.sleep(100);
                }

                JsonNode nextKey = data.get("last_evaluated_key");
                if (nextKey == null || nextKey.isNull()) {
                    break;
                }

                lastEvaluatedKey = nextKey.asText();
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.printf("%nDone. Deleted %d identities.%n", deleted);
    }
}
