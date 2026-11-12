package org.intelehealth.ezazi.automation.driver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.exceptions.ConfigurationException;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;
import org.intelehealth.ezazi.automation.utils.AdbUtils;

import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;

/**
 * Starts/stops one local Appium server per test run (shared by all parallel sessions).
 * <p>
 * Uses a free port when {@code appium.port=auto} so concurrent runs on the same machine (or a
 * forgotten Appium Desktop) never collide. Server output goes to {@code target/logs/appium-server.log}
 * to keep the console readable. Set {@code appium.server.start=false} + {@code appium.server.url}
 * to reuse an already running server or a device cloud / Appium grid instead.
 */
public final class AppiumServerManager {

    private static final Logger LOG = LogManager.getLogger(AppiumServerManager.class);
    private static final Path LOG_FILE = Path.of("target", "logs", "appium-server.log");

    private static AppiumDriverLocalService service;
    private static URL serverUrl;
    private static OutputStream logStream;

    private AppiumServerManager() {
    }

    /**
     * Starts the server (idempotent) or registers the external server URL.
     */
    public static synchronized void start() {
        if (serverUrl != null) {
            return;
        }
        if (!ConfigReader.getBoolean(ConfigKeys.APPIUM_SERVER_START, true)) {
            serverUrl = toUrl(ConfigReader.get(ConfigKeys.APPIUM_SERVER_URL));
            LOG.info("Using external Appium server at {}", serverUrl);
            return;
        }

        String host = ConfigReader.get(ConfigKeys.APPIUM_HOST, "127.0.0.1");
        int port = resolvePort(ConfigReader.get(ConfigKeys.APPIUM_PORT, "auto"));
        AppiumServiceBuilder builder = new AppiumServiceBuilder()
                .withIPAddress(host)
                .usingPort(port)
                .withTimeout(ConfigReader.getSeconds(ConfigKeys.APPIUM_START_TIMEOUT, 60))
                .withArgument(GeneralServerFlag.LOG_LEVEL, ConfigReader.get(ConfigKeys.APPIUM_LOG_LEVEL, "info"))
                .withArgument(GeneralServerFlag.LOG_TIMESTAMP)
                .withArgument(GeneralServerFlag.LOCAL_TIMEZONE)
                .withEnvironment(androidEnvironment());

        if (ConfigReader.getBoolean(ConfigKeys.APPIUM_RELAXED_SECURITY, false)) {
            builder.withArgument(GeneralServerFlag.RELAXED_SECURITY);
        }
        ConfigReader.getOptional(ConfigKeys.APPIUM_JS_PATH).ifPresent(js -> builder.withAppiumJS(new File(js)));
        ConfigReader.getOptional(ConfigKeys.APPIUM_NODE_PATH).ifPresent(node -> builder.usingDriverExecutable(new File(node)));

        service = AppiumDriverLocalService.buildService(builder);
        // Server output -> file only (keeps the console readable). Not using withLogFile(): on Windows
        // Appium's own --log handle and the Java stream would lock the same file.
        service.clearOutPutStreams();
        try {
            logStream = new FileOutputStream(prepareLogFile(), false);
            service.addOutPutStream(logStream);
        } catch (IOException e) {
            LOG.warn("Cannot write Appium log file {}: {}", LOG_FILE, e.getMessage());
        }
        long start = System.currentTimeMillis();
        try {
            service.start();
        } catch (RuntimeException e) {
            throw new FrameworkException("Appium server failed to start on " + host + ":" + port
                    + ". Is Appium installed (npm i -g appium) with the uiautomator2 driver? See " + LOG_FILE, e);
        }
        serverUrl = service.getUrl();
        Runtime.getRuntime().addShutdownHook(new Thread(AppiumServerManager::stop, "appium-server-shutdown"));
        LOG.info("Appium server started at {} in {} ms (log: {})", serverUrl, System.currentTimeMillis() - start, LOG_FILE);
    }

    /**
     * @return URL of the running server
     * @throws FrameworkException if {@link #start()} was not called
     */
    public static synchronized URL getServerUrl() {
        if (serverUrl == null) {
            throw new FrameworkException("Appium server is not started. BaseTest @BeforeSuite must run first "
                    + "(run tests through a suite XML or make sure your class extends BaseTest).");
        }
        return serverUrl;
    }

    /** Stops the server if this JVM started it. Safe to call multiple times. */
    public static synchronized void stop() {
        if (service != null && service.isRunning()) {
            service.stop();
            LOG.info("Appium server stopped");
        }
        if (logStream != null) {
            try {
                logStream.close();
            } catch (IOException ignored) {
                // nothing useful to do during shutdown
            }
        }
        logStream = null;
        service = null;
        serverUrl = null;
    }

    private static int resolvePort(String configured) {
        if (configured == null || configured.isBlank() || "auto".equalsIgnoreCase(configured) || "0".equals(configured)) {
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                return socket.getLocalPort();
            } catch (IOException e) {
                throw new FrameworkException("Could not find a free port for Appium", e);
            }
        }
        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("appium.port must be a number or 'auto' but was '" + configured + "'", e);
        }
    }

    private static File prepareLogFile() {
        try {
            Files.createDirectories(LOG_FILE.getParent());
        } catch (IOException e) {
            throw new FrameworkException("Cannot create log directory " + LOG_FILE.getParent(), e);
        }
        return LOG_FILE.toFile();
    }

    private static Map<String, String> androidEnvironment() {
        Map<String, String> env = new HashMap<>(System.getenv());
        Path sdk = AdbUtils.androidSdkRoot();
        if (sdk != null) {
            env.putIfAbsent("ANDROID_HOME", sdk.toString());
            env.putIfAbsent("ANDROID_SDK_ROOT", sdk.toString());
        } else {
            LOG.warn("ANDROID_HOME / ANDROID_SDK_ROOT is not set - UiAutomator2 sessions will fail to start.");
        }
        return env;
    }

    private static URL toUrl(String value) {
        try {
            return new URI(value).toURL();
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            throw new ConfigurationException("appium.server.url is not a valid URL: '" + value + "'", e);
        }
    }
}
