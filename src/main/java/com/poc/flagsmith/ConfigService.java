package com.poc.flagsmith;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigService {
    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private static final Predicate<String> isNotBlank = (s) -> !s.isBlank();

    private Properties properties;

    public ConfigService() {
        try (InputStream is = ConfigService.class
            .getClassLoader()
            .getResourceAsStream("config.properties")
        ) {
            if (is != null) {
                properties = new Properties();
                properties.load(is);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String resolveApiUrl() {
        return
            resolveFromSysProp("flagsmith.api.url")
                .or(() -> resolveFromConfigProperties("flagsmith.api.url"))
                .or(() -> resolveFromEnv("FLAGSMITH_API_URL"))
                .orElseGet(() -> {
                    // Fall back — demo key for Flagsmith's hosted public sandbox
                    log.warn("No API URL configured — using public api.");

                    return "https://api.flagsmith.com/api/v1";
                });
    }

    public String resolveApiKey() {
        return
            resolveFromSysProp("flagsmith.api.key")
                .or(() -> resolveFromConfigProperties("flagsmith.api.key"))
                .or(() -> resolveFromEnv("FLAGSMITH_API_KEY"))
                .orElseGet(() -> {
                    // Fall back — demo key for Flagsmith's hosted public sandbox
                    log.warn("No API key configured — using Flagsmith public demo key.");
                    log.warn("Create a free account at https://app.flagsmith.com and set");
                    log.warn("flagsmith.api.key in src/main/resources/config.properties");

                    return "ser.aEnDFVnYLmsPCdDroN23V";  // Flagsmith public demo environment
                });
    }

    public String resolveAdminKey() {
        return
            resolveFromSysProp("flagsmith.admin.key")
                .or(() -> resolveFromConfigProperties("flagsmith.admin.key"))
                .or(() -> resolveFromEnv("FLAGSMITH_ADMIN_KEY"))
                .orElseGet(() -> {
                    log.error("No Admin key configured - error.");

                    throw new IllegalStateException("No Admin key configured.");
                });
    }

    public String resolveEnvironmentId() {
        return
            resolveFromSysProp("flagsmith.environment.id")
                .or(() -> resolveFromConfigProperties("flagsmith.environment.id"))
                .or(() -> resolveFromEnv("FLAGSMITH_ENV_ID"))
                .orElseGet(() -> {
                    log.error("No Environment ID configured - error.");

                    throw new IllegalStateException("No Environment ID configured.");
                });
    }

    private Optional<String> resolveFromSysProp(String prop) {
        var result = Optional.ofNullable(System.getProperty(prop))
            .filter(isNotBlank);

        if (result.isPresent()) {
            log.info("Using value from system property {}.", prop);
        }

        return result;
    }

    private Optional<String> resolveFromConfigProperties(String prop) {
        var result = Optional.ofNullable(properties)
            .flatMap((props) ->
                Optional.ofNullable(props.getProperty(prop))
                    .filter(isNotBlank)
            );

        if (result.isPresent()) {
            log.info("Using {} from config.properties.", prop);
        }

        return result;
    }

    private Optional<String> resolveFromEnv(String env) {
        var result = Optional.ofNullable(System.getenv(env))
            .filter(isNotBlank);

        if (result.isPresent()) {
            log.info("Using environment variable {}.", env);
        }

        return result;
    }
}
