package org.intelehealth.ezazi.automation.config;

/**
 * Every configuration key used by the framework, in one place.
 * <p>
 * Always reference these constants instead of string literals so a typo becomes a compile
 * error rather than a confusing run-time "missing key" failure.
 */
public final class ConfigKeys {

    // ----- Selection -----
    public static final String ENV = "env";
    public static final String VARIANT = "variant";

    // ----- Appium server -----
    public static final String APPIUM_SERVER_START = "appium.server.start";
    public static final String APPIUM_SERVER_URL = "appium.server.url";
    public static final String APPIUM_HOST = "appium.host";
    public static final String APPIUM_PORT = "appium.port";
    public static final String APPIUM_JS_PATH = "appium.js.path";
    public static final String APPIUM_NODE_PATH = "appium.node.path";
    public static final String APPIUM_LOG_LEVEL = "appium.log.level";
    public static final String APPIUM_RELAXED_SECURITY = "appium.relaxed.security";
    public static final String APPIUM_START_TIMEOUT = "appium.start.timeout.seconds";

    // ----- Devices -----
    public static final String DEVICE_NAME = "device.name";
    public static final String DEVICE_UDIDS = "device.udids";
    public static final String DEVICE_PLATFORM_VERSION = "device.platform.version";
    public static final String DEVICE_SYSTEM_PORT_BASE = "device.system.port.base";
    public static final String DEVICE_ACQUIRE_TIMEOUT = "device.acquire.timeout.seconds";

    // ----- Emulator -----
    public static final String EMULATOR_START = "emulator.start";
    public static final String EMULATOR_AVD = "emulator.avd";
    public static final String EMULATOR_HEADLESS = "emulator.headless";
    public static final String EMULATOR_BOOT_TIMEOUT = "emulator.boot.timeout.seconds";
    public static final String EMULATOR_STOP_AFTER_SUITE = "emulator.stop.after.suite";

    // ----- Application under test -----
    public static final String APP_PATH = "app.path";
    public static final String APP_PACKAGE = "app.package";
    public static final String APP_ACTIVITY = "app.activity";
    public static final String APP_NO_RESET = "app.no.reset";
    public static final String APP_FULL_RESET = "app.full.reset";
    public static final String APP_AUTO_GRANT_PERMISSIONS = "app.auto.grant.permissions";
    public static final String APP_RESET_STRATEGY = "app.reset.strategy";
    public static final String APP_DATE_FORMAT = "app.date.format";
    public static final String APP_TIME_FORMAT = "app.time.format";
    public static final String APP_DEFAULT_LOCATION = "app.default.location";

    // ----- Driver / session -----
    public static final String SESSION_SCOPE = "driver.session.scope";
    public static final String NEW_COMMAND_TIMEOUT = "driver.new.command.timeout.seconds";
    public static final String SESSION_CREATE_RETRIES = "driver.session.create.retries";
    public static final String DRIVER_HIDE_KEYBOARD = "driver.hide.keyboard";

    // ----- Timeouts -----
    public static final String TIMEOUT_EXPLICIT = "timeout.explicit.seconds";
    public static final String TIMEOUT_SHORT = "timeout.short.seconds";
    public static final String TIMEOUT_APP_LAUNCH = "timeout.app.launch.seconds";
    public static final String TIMEOUT_POLLING_MILLIS = "timeout.polling.millis";

    // ----- Parallel execution -----
    public static final String PARALLEL_MODE = "parallel.mode";
    public static final String PARALLEL_THREADS = "parallel.threads";

    // ----- Test data -----
    public static final String TESTDATA_EXCEL_FILE = "testdata.excel.file";

    // ----- Retry -----
    public static final String RETRY_MAX_COUNT = "retry.max.count";
    public static final String RETRY_ON_ASSERTION_FAILURE = "retry.on.assertion.failure";

    // ----- Reporting -----
    public static final String ALLURE_RESULTS_DIR = "allure.results.directory";
    public static final String ALLURE_CLEAN_RESULTS = "allure.clean.results";
    public static final String ALLURE_REPORT_GENERATE = "allure.report.generate";
    public static final String ALLURE_REPORT_DIR = "allure.report.dir";
    public static final String REPORT_LOGCAT_LINES = "report.logcat.lines";
    public static final String REPORT_SCREENSHOT_ON_SUCCESS = "report.screenshot.on.success";

    // ----- Secrets: names of environment variables / .env entries (never put values in properties) -----
    public static final String SECRET_USERNAME = "EZAZI_USERNAME";
    public static final String SECRET_PASSWORD = "EZAZI_PASSWORD";

    private ConfigKeys() {
    }
}
