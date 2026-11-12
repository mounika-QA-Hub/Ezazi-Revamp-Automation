package org.intelehealth.ezazi.automation.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.exceptions.ConfigurationException;

/**
 * Single source of truth for configuration.
 *
 * <h2>Resolution order (highest priority first)</h2>
 * <ol>
 *   <li>JVM system property, e.g. {@code -Dappium.port=4725}</li>
 *   <li>OS environment variable with the key upper-cased and dots replaced by underscores,
 *       e.g. {@code APPIUM_PORT=4725} (handy on CI)</li>
 *   <li>{@code local.properties} in the project root (untracked, per-machine tweaks)</li>
 *   <li>{@code config/variants/<variant>.properties} (app package, APK, activity...)</li>
 *   <li>{@code config/env/<env>.properties} (server URLs, timeouts per environment)</li>
 *   <li>{@code config/base.properties} (framework defaults)</li>
 * </ol>
 * Secrets (credentials) are never read from property files: use {@link #getSecret(String)}, which
 * reads environment variables and the untracked {@code .env} file.
 * <p>
 * The class is immutable after loading and therefore safe to use from parallel threads.
 */
public final class ConfigReader {

    private static final Logger LOG = LogManager.getLogger(ConfigReader.class);

    private static final String BASE_FILE = "config/base.properties";
    private static final String ENV_FILE_PATTERN = "config/env/%s.properties";
    private static final String VARIANT_FILE_PATTERN = "config/variants/%s.properties";
    private static final String LOCAL_FILE = "local.properties";
    private static final String DOT_ENV_FILE = ".env";
    private static final Pattern SENSITIVE_KEY = Pattern.compile("(?i).*(password|secret|token|apikey|api_key).*");

    private final Properties properties = new Properties();
    private final Map<String, String> dotEnv;
    private final List<String> loadedSources = new ArrayList<>();
    private final String env;
    private final String variant;

    private ConfigReader() {
        Properties base = loadClasspathFile(BASE_FILE, true);
        properties.putAll(base);

        this.env = firstNonBlank(System.getProperty(ConfigKeys.ENV), System.getenv("ENV"),
                base.getProperty(ConfigKeys.ENV), "qa");
        this.variant = firstNonBlank(System.getProperty(ConfigKeys.VARIANT), System.getenv("VARIANT"),
                base.getProperty(ConfigKeys.VARIANT), "variantA");

        properties.putAll(loadClasspathFile(String.format(ENV_FILE_PATTERN, env), true));
        properties.putAll(loadClasspathFile(String.format(VARIANT_FILE_PATTERN, variant), true));
        properties.putAll(loadProjectFile(LOCAL_FILE));
        properties.setProperty(ConfigKeys.ENV, env);
        properties.setProperty(ConfigKeys.VARIANT, variant);

        this.dotEnv = Collections.unmodifiableMap(loadDotEnv(projectRoot().resolve(DOT_ENV_FILE)));
        LOG.info("Configuration loaded for env='{}', variant='{}' from {}", env, variant, loadedSources);
    }

    private static final class Holder {
        private static final ConfigReader INSTANCE = new ConfigReader();
    }

    private static ConfigReader instance() {
        return Holder.INSTANCE;
    }

    // ------------------------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------------------------

    /**
     * Returns a mandatory value.
     *
     * @param key configuration key, see {@link ConfigKeys}
     * @return trimmed, non-blank value
     * @throws ConfigurationException if the key is missing or blank
     */
    public static String get(String key) {
        return getOptional(key).orElseThrow(() -> new ConfigurationException(
                "Missing configuration key '" + key + "' (env=" + instance().env + ", variant=" + instance().variant
                        + "). Add it to config/base.properties, config/env/" + instance().env + ".properties, config/variants/"
                        + instance().variant + ".properties, or pass -D" + key + "=<value>."));
    }

    /**
     * Returns the value for {@code key} or {@code defaultValue} if it is missing or blank.
     */
    public static String get(String key, String defaultValue) {
        return getOptional(key).orElse(defaultValue);
    }

    /**
     * Returns the value for {@code key}, or an empty Optional when missing or blank.
     */
    public static Optional<String> getOptional(String key) {
        String value = System.getProperty(key);
        if (isBlank(value)) {
            value = System.getenv(toEnvVarName(key));
        }
        if (isBlank(value)) {
            value = instance().properties.getProperty(key);
        }
        return isBlank(value) ? Optional.empty() : Optional.of(value.trim());
    }

    /** Mandatory integer value. */
    public static int getInt(String key) {
        return parseInt(key, get(key));
    }

    /** Integer value with default. */
    public static int getInt(String key, int defaultValue) {
        return getOptional(key).map(v -> parseInt(key, v)).orElse(defaultValue);
    }

    /** Boolean value with default ("true"/"yes"/"y"/"1" are treated as true). */
    public static boolean getBoolean(String key, boolean defaultValue) {
        return getOptional(key).map(ConfigReader::parseBoolean).orElse(defaultValue);
    }

    /** Duration expressed in seconds in the properties file. */
    public static Duration getSeconds(String key, long defaultSeconds) {
        return Duration.ofSeconds(getInt(key, (int) defaultSeconds));
    }

    /** Comma separated list, blanks removed. Missing key gives an empty list. */
    public static List<String> getList(String key) {
        return getOptional(key)
                .map(v -> Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList())
                .orElse(List.of());
    }

    /**
     * Reads a secret such as a password. Lookup order: system property, OS environment
     * variable, then the untracked {@code .env} file in the project root.
     *
     * @param name secret name, e.g. {@link ConfigKeys#SECRET_PASSWORD}
     * @throws ConfigurationException with set-up instructions when it is not defined
     */
    public static String getSecret(String name) {
        return findSecret(name).orElseThrow(() -> new ConfigurationException(
                "Secret '" + name + "' is not set. Export it as an environment variable or add '" + name
                        + "=<value>' to the .env file in the project root (copy .env.example). Never commit real credentials."));
    }

    /** Optional variant of {@link #getSecret(String)}. */
    public static Optional<String> findSecret(String name) {
        String value = System.getProperty(name);
        if (isBlank(value)) {
            value = System.getenv(name);
        }
        if (isBlank(value)) {
            value = instance().dotEnv.get(name);
        }
        return isBlank(value) ? Optional.empty() : Optional.of(value.trim());
    }

    /** Selected environment, e.g. {@code qa}. */
    public static String env() {
        return instance().env;
    }

    /** Selected app variant, e.g. {@code variantA}. */
    public static String variant() {
        return instance().variant;
    }

    /**
     * Effective configuration (after overrides) with sensitive values masked. Used for logs and
     * the Allure environment widget.
     */
    public static Map<String, String> effectiveConfiguration() {
        Map<String, String> result = new TreeMap<>();
        for (String key : instance().properties.stringPropertyNames()) {
            String value = getOptional(key).orElse("");
            result.put(key, SENSITIVE_KEY.matcher(key).matches() ? "********" : value);
        }
        return result;
    }

    /** Logs the effective configuration at DEBUG level (INFO for the selection keys). */
    public static void logEffectiveConfiguration() {
        LOG.info("Running with env='{}' variant='{}' (sources: {})", env(), variant(), instance().loadedSources);
        effectiveConfiguration().forEach((k, v) -> LOG.debug("  {} = {}", k, v));
    }

    /** Project root directory (Maven sets the working directory to ${basedir}). */
    public static Path projectRoot() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    }

    // ------------------------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------------------------

    private Properties loadClasspathFile(String resource, boolean mandatory) {
        Properties p = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                if (mandatory) {
                    throw new ConfigurationException("Configuration file '" + resource
                            + "' not found on the classpath (src/test/resources). Check -Denv / -Dvariant values.");
                }
                return p;
            }
            try (Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
                p.load(reader);
            }
            loadedSources.add(resource);
            return p;
        } catch (IOException e) {
            throw new ConfigurationException("Unable to read configuration file '" + resource + "'", e);
        }
    }

    private Properties loadProjectFile(String fileName) {
        Properties p = new Properties();
        Path file = projectRoot().resolve(fileName);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(reader);
                loadedSources.add(file.getFileName().toString());
            } catch (IOException e) {
                throw new ConfigurationException("Unable to read " + file, e);
            }
        }
        return p;
    }

    private static Map<String, String> loadDotEnv(Path file) {
        Map<String, String> values = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return values;
        }
        try {
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int idx = line.indexOf('=');
                String key = line.substring(0, idx).trim();
                String value = stripQuotes(line.substring(idx + 1).trim());
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new ConfigurationException("Unable to read .env file " + file, e);
        }
        return values;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String toEnvVarName(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    private static int parseInt(String key, String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Configuration key '" + key + "' must be a whole number but was '" + value + "'", e);
        }
    }

    private static boolean parseBoolean(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("yes") || v.equals("y") || v.equals("1");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }
}
