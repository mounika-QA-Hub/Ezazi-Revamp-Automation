package org.intelehealth.ezazi.automation.base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.driver.AppiumServerManager;
import org.intelehealth.ezazi.automation.driver.DeviceConfig;
import org.intelehealth.ezazi.automation.driver.DevicePool;
import org.intelehealth.ezazi.automation.driver.DriverFactory;
import org.intelehealth.ezazi.automation.driver.DriverManager;
import org.intelehealth.ezazi.automation.driver.EmulatorManager;
import org.intelehealth.ezazi.automation.driver.SessionScope;
import org.intelehealth.ezazi.automation.reports.AllureManager;
import org.intelehealth.ezazi.automation.reports.ExecutionEnvironment;
import org.intelehealth.ezazi.automation.utils.DeviceUtils;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.appmanagement.ApplicationState;

/**
 * Parent of every UI test class. Owns the complete life cycle so tests contain only test logic.
 *
 * <pre>
 *  @BeforeSuite  (once)        load config, clean Allure results, start emulator (optional), start Appium
 *  @BeforeClass  (per class)   create the session when driver.session.scope=class (default)
 *  @BeforeMethod (per test)    create the session when scope=method / none exists on this thread,
 *                              or recreate it if the previous test killed it (self-healing)
 *  -- test runs --             AllureListener attaches screenshot/page source/logcat on failure
 *  @AfterMethod  (per test)    reset app state (relaunch) or quit a method-scoped session
 *  @AfterClass   (per class)   quit the session, release the device, clear ThreadLocals
 *  @AfterSuite   (once)        write Allure environment/categories, stop Appium and emulator
 * </pre>
 * Every configuration method uses {@code alwaysRun = true} so clean-up happens even when set-up
 * failed or groups are filtered - no leaked sessions, devices or Appium processes.
 */
public abstract class BaseTest {

    protected final Logger log = LogManager.getLogger(getClass());

    /** True when the current thread's session must end after the current test method. */
    private static final ThreadLocal<Boolean> METHOD_SCOPED = ThreadLocal.withInitial(() -> false);

    // =========================================================================== suite level

    @BeforeSuite(alwaysRun = true)
    public void beforeSuite() {
        ConfigReader.logEffectiveConfiguration();
        if (ConfigReader.getBoolean(ConfigKeys.ALLURE_CLEAN_RESULTS, true)) {
            AllureManager.cleanResults();
        }
        EmulatorManager.startIfConfigured();
        AppiumServerManager.start();
    }

    @AfterSuite(alwaysRun = true)
    public void afterSuite() {
        try {
            ExecutionEnvironment.put("Environment", ConfigReader.env());
            ExecutionEnvironment.put("App variant", ConfigReader.variant());
            ExecutionEnvironment.put("App package", ConfigReader.get(ConfigKeys.APP_PACKAGE, ""));
            ExecutionEnvironment.put("Java", System.getProperty("java.version"));
            ExecutionEnvironment.put("Host OS", System.getProperty("os.name"));
            AllureManager.writeEnvironment(ExecutionEnvironment.snapshot());
            AllureManager.copyCategories();
        } finally {
            AppiumServerManager.stop();
            EmulatorManager.stopIfStartedByFramework();
            AllureManager.generateReportIfEnabled();
        }
    }

    // =========================================================================== class level

    @BeforeClass(alwaysRun = true)
    public void beforeClass(ITestContext context) {
        if (sessionScope(context) == SessionScope.CLASS) {
            startSession(context, false);
        }
    }

    @AfterClass(alwaysRun = true)
    public void afterClass() {
        endSession();
    }

    // ========================================================================== method level

    @BeforeMethod(alwaysRun = true)
    public void beforeMethod(ITestContext context) {
        if (!DriverManager.hasDriver()) {
            // scope=method, or a parallel-DataProvider worker thread that has no class session:
            // either way the session belongs to this method only and ends in @AfterMethod.
            startSession(context, true);
            return;
        }
        if (!isSessionAlive()) {
            log.warn("Session on {} is no longer responding - recreating it", DriverManager.getDevice());
            boolean methodScoped = METHOD_SCOPED.get();
            endSession();
            startSession(context, methodScoped);
        }
        ensureAppInForeground();
    }

    /**
     * On a freshly booted device the launcher can come up over the app right after the session
     * starts (or a system dialog can background it). One cheap state query per test prevents a
     * 60-second "element not visible" timeout in that case.
     */
    private void ensureAppInForeground() {
        String appPackage = ConfigReader.get(ConfigKeys.APP_PACKAGE);
        try {
            if (driver().queryAppState(appPackage) != ApplicationState.RUNNING_IN_FOREGROUND) {
                log.info("{} is not in the foreground - activating it", appPackage);
                driver().activateApp(appPackage);
            }
        } catch (RuntimeException e) {
            log.warn("Could not verify app state: {}", e.getMessage());
        }
    }

    @AfterMethod(alwaysRun = true)
    public void afterMethod(ITestResult result) {
        if (METHOD_SCOPED.get()) {
            endSession();
            return;
        }
        if (DriverManager.hasDriver()) {
            resetAppState();
        }
    }

    // ============================================================================== helpers

    /** @return the current thread's driver (convenience for tests that need raw Appium calls) */
    protected AndroidDriver driver() {
        return DriverManager.getDriver();
    }

    /**
     * Wipes the app's data (logs out, forgets the device setup) and relaunches it, so the next test
     * starts like a first install. Call it after a test that signs in, because the app remembers the
     * login across relaunches and every later login-screen test would otherwise start on the home screen.
     */
    protected void resetAppToFirstLaunch() {
        String appPackage = ConfigReader.get(ConfigKeys.APP_PACKAGE);
        try {
            driver().executeScript("mobile: clearApp", java.util.Map.of("appId", appPackage));
            // clearing data also revokes runtime permissions; re-grant them (like autoGrantPermissions does)
            // or the app opens on its "Permissions Required" screen instead of the login screen
            if (ConfigReader.getBoolean(ConfigKeys.APP_AUTO_GRANT_PERMISSIONS, true)) {
                driver().executeScript("mobile: changePermissions", java.util.Map.of(
                        "permissions", "all", "appPackage", appPackage, "action", "grant"));
            }
            driver().activateApp(appPackage);
        } catch (RuntimeException e) {
            log.warn("Could not reset {} to first launch: {}", appPackage, e.getMessage());
        }
    }

    private void startSession(ITestContext context, boolean methodScoped) {
        DeviceConfig device = resolveDevice(context.getCurrentXmlTest());
        try {
            AndroidDriver driver = DriverFactory.createAndroidDriver(device, AppiumServerManager.getServerUrl());
            DriverManager.setDriver(driver, device);
            METHOD_SCOPED.set(methodScoped);
            recordEnvironment(device);
        } catch (RuntimeException e) {
            DevicePool.release(device);
            throw e;
        }
    }

    private void endSession() {
        DeviceConfig device = DriverManager.quitDriver();
        DevicePool.release(device);
        METHOD_SCOPED.remove();
    }

    /**
     * Brings the app back to its start screen between tests in the same session. Failures here are
     * logged, not thrown: a broken reset must not mark the remaining tests as SKIPPED - the next
     * {@code @BeforeMethod} detects a dead session and recreates it instead.
     */
    private void resetAppState() {
        String strategy = ConfigReader.get(ConfigKeys.APP_RESET_STRATEGY, "relaunch").toLowerCase();
        String appPackage = ConfigReader.get(ConfigKeys.APP_PACKAGE);
        try {
            switch (strategy) {
                case "relaunch" -> {
                    driver().terminateApp(appPackage);
                    driver().activateApp(appPackage);
                }
                case "clear" -> {
                    driver().executeScript("mobile: clearApp", java.util.Map.of("appId", appPackage));
                    driver().activateApp(appPackage);
                }
                case "none" -> log.debug("app.reset.strategy=none - keeping app state");
                default -> log.warn("Unknown app.reset.strategy '{}', expected relaunch|clear|none", strategy);
            }
        } catch (RuntimeException e) {
            log.warn("App reset ({}) failed: {}", strategy, e.getMessage());
        }
    }

    private boolean isSessionAlive() {
        try {
            driver().getCurrentPackage();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Device precedence: {@code <parameter name="udid">} in the suite XML (pins a {@code <test>} to a
     * device) &gt; the shared {@link DevicePool}.
     */
    private DeviceConfig resolveDevice(XmlTest xmlTest) {
        String udid = xmlTest.getParameter("udid");
        if (udid != null && !udid.isBlank()) {
            String systemPort = xmlTest.getParameter("systemPort");
            return new DeviceConfig(udid,
                    paramOrConfig(xmlTest, "deviceName", ConfigKeys.DEVICE_NAME, "Android Device"),
                    paramOrConfig(xmlTest, "platformVersion", ConfigKeys.DEVICE_PLATFORM_VERSION, ""),
                    systemPort == null || systemPort.isBlank()
                            ? ConfigReader.getInt(ConfigKeys.DEVICE_SYSTEM_PORT_BASE, 8200) : Integer.parseInt(systemPort.trim()),
                    false);
        }
        DeviceConfig pooled = DevicePool.acquire();
        return new DeviceConfig(pooled.udid(),
                paramOrDefault(xmlTest, "deviceName", pooled.deviceName()),
                paramOrDefault(xmlTest, "platformVersion", pooled.platformVersion()),
                pooled.systemPort(), true);
    }

    private static String paramOrDefault(XmlTest xmlTest, String param, String defaultValue) {
        String value = xmlTest.getParameter(param);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String paramOrConfig(XmlTest xmlTest, String param, String key, String defaultValue) {
        String value = xmlTest.getParameter(param);
        return value == null || value.isBlank() ? ConfigReader.get(key, defaultValue) : value;
    }

    private SessionScope sessionScope(ITestContext context) {
        SessionScope scope = SessionScope.from(ConfigReader.get(ConfigKeys.SESSION_SCOPE, "class"));
        XmlSuite.ParallelMode mode = context.getSuite().getXmlSuite().getParallel();
        if (scope == SessionScope.CLASS && mode == XmlSuite.ParallelMode.METHODS) {
            log.warn("parallel=\"methods\" requires one session per method - using driver.session.scope=method");
            return SessionScope.METHOD;
        }
        if (scope == SessionScope.CLASS && usesParallelDataProvider(context)) {
            log.info("{} uses a parallel DataProvider - using one session per test method", getClass().getSimpleName());
            return SessionScope.METHOD;
        }
        return scope;
    }

    /** A class-level session would pin a device that the DataProvider's worker threads need. */
    private boolean usesParallelDataProvider(ITestContext context) {
        return java.util.Arrays.stream(context.getAllTestMethods())
                .filter(m -> m.getRealClass() == getClass())
                .anyMatch(m -> m.getDataProviderMethod() != null && m.getDataProviderMethod().isParallel());
    }

    private static void recordEnvironment(DeviceConfig device) {
        String serial = DriverManager.getUdid();
        ExecutionEnvironment.put("Device", DeviceUtils.describe(serial));
        ExecutionEnvironment.put("Device UDID", serial);
        ExecutionEnvironment.put("App version",
                DeviceUtils.appVersion(serial, ConfigReader.get(ConfigKeys.APP_PACKAGE, "")));
        ExecutionEnvironment.put("Appium server", AppiumServerManager.getServerUrl().toString());
        ExecutionEnvironment.put("Device name", device.deviceName());
    }
}
