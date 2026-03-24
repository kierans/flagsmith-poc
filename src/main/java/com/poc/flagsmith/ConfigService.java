package com.poc.flagsmith;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigService {
    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

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
    // -----------------------------------------------------------------------
    // API key resolution: system property > config file > demo key
    // -----------------------------------------------------------------------
    public String resolveApiKey() throws Exception {
        // 1. Command-line / JVM system property
        String fromSysProp = System.getProperty("flagsmith.api.key");
        if (fromSysProp != null && !fromSysProp.isBlank()) {
            log.info("Using API key from system property.");

            return fromSysProp;
        }

        // 2. config.properties on the classpath
        if (properties != null) {
            String fromFile = properties.getProperty("flagsmith.api.key");

            if (fromFile != null && !fromFile.isBlank() && !fromFile.equals("YOUR_FLAGSMITH_ENV_KEY_HERE")) {
                log.info("Using API key from config.properties.");

                return fromFile;
            }
        }

        // 3. Environment variable
        String fromEnv = System.getenv("FLAGSMITH_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            log.info("Using API key from environment variable FLAGSMITH_API_KEY.");

            return fromEnv;
        }

        // 4. Fall back — demo key for Flagsmith's hosted public sandbox
        log.warn("No API key configured — using Flagsmith public demo key.");
        log.warn("Create a free account at https://app.flagsmith.com and set");
        log.warn("flagsmith.api.key in src/main/resources/config.properties");

        return "ser.aEnDFVnYLmsPCdDroN23V";  // Flagsmith public demo environment
    }

    public String resolveAdminKey() throws Exception {
        // 1. Command-line / JVM system property
        String fromSysProp = System.getProperty("flagsmith.admin.key");
        if (fromSysProp != null && !fromSysProp.isBlank()) {
            log.info("Using Admin key from system property.");

            return fromSysProp;
        }

        // 2. config.properties on the classpath
        if (properties != null) {
            String fromFile = properties.getProperty("flagsmith.admin.key");

            if (fromFile != null && !fromFile.isBlank() && !fromFile.equals("YOUR_FLAGSMITH_ENV_KEY_HERE")) {
                log.info("Using Admin key from config.properties.");

                return fromFile;
            }
        }

        // 3. Environment variable
        String fromEnv = System.getenv("FLAGSMITH_ADMIN_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            log.info("Using Admin key from environment variable FLAGSMITH_ADMIN_KEY.");

            return fromEnv;
        }

        // 4. Can't continue - error
        log.error("No Admin key configured - error.");

        throw new IllegalStateException("No Admin key configured.");
    }

    public String resolveEnvironmentId() throws Exception {
        // 1. Command-line / JVM system property
        String fromSysProp = System.getProperty("flagsmith.environment.id");
        if (fromSysProp != null && !fromSysProp.isBlank()) {
            log.info("Using Environment ID from system property.");

            return fromSysProp;
        }

        // 2. config.properties on the classpath
        if (properties != null) {
            String fromFile = properties.getProperty("flagsmith.environment.id");

            if (fromFile != null && !fromFile.isBlank() && !fromFile.equals("YOUR_FLAGSMITH_ENV_ID_HERE")) {
                log.info("Using Environment ID from config.properties.");

                return fromFile;
            }
        }

        // 3. Environment variable
        String fromEnv = System.getenv("FLAGSMITH_ENV_ID");
        if (fromEnv != null && !fromEnv.isBlank()) {
            log.info("Using Environment ID from environment variable FLAGSMITH_ENV_ID.");

            return fromEnv;
        }

        // 4. Can't continue - error
        log.error("No Environment ID configured - error.");

        throw new IllegalStateException("No Environment ID configured.");
    }
}
