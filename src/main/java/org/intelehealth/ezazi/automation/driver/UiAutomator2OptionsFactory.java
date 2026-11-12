package org.intelehealth.ezazi.automation.driver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.exceptions.ConfigurationException;

import io.appium.java_client.android.options.UiAutomator2Options;

/**
 * Builds {@link UiAutomator2Options} from configuration + the selected device.
 * <p>
 * All capabilities are created in one place so the team never copies capability code into tests.
 * Speed-related capabilities (animations off, no re-install, hidden-API error ignored) are on by
 * default because they remove the most common sources of slowness and flakiness.
 */
public final class UiAutomator2OptionsFactory {

    private UiAutomator2OptionsFactory() {
    }

    /**
     * @param device target device
     * @return fully populated options
     */
    public static UiAutomator2Options build(DeviceConfig device) {
        UiAutomator2Options options = new UiAutomator2Options()
                .setPlatformName("Android")
                .setDeviceName(device.deviceName())
                .setSystemPort(device.systemPort())
                .setNewCommandTimeout(ConfigReader.getSeconds(ConfigKeys.NEW_COMMAND_TIMEOUT, 300))
                .setNoReset(ConfigReader.getBoolean(ConfigKeys.APP_NO_RESET, true))
                .setFullReset(ConfigReader.getBoolean(ConfigKeys.APP_FULL_RESET, false))
                .setAutoGrantPermissions(ConfigReader.getBoolean(ConfigKeys.APP_AUTO_GRANT_PERMISSIONS, true))
                .setAppWaitDuration(ConfigReader.getSeconds(ConfigKeys.TIMEOUT_APP_LAUNCH, 60))
                .setDisableWindowAnimation(true)
                .setIgnoreHiddenApiPolicyError(true)
                .setUiautomator2ServerLaunchTimeout(Duration.ofSeconds(90))
                .setUiautomator2ServerInstallTimeout(Duration.ofSeconds(90))
                .setAdbExecTimeout(Duration.ofSeconds(60));

        // Switches the device to Appium's invisible keyboard for the session (the original IME is
        // restored when the session ends). A soft keyboard that pops up late can cover buttons and
        // swallow taps - the main cause of "passes in one run, fails in the next" form tests.
        if (ConfigReader.getBoolean(ConfigKeys.DRIVER_HIDE_KEYBOARD, true)) {
            options.setCapability("appium:hideKeyboard", true);
        }

        if (notBlank(device.udid())) {
            options.setUdid(device.udid());
        }
        if (notBlank(device.platformVersion())) {
            options.setPlatformVersion(device.platformVersion());
        }

        String appPackage = ConfigReader.get(ConfigKeys.APP_PACKAGE);
        options.setAppPackage(appPackage);
        ConfigReader.getOptional(ConfigKeys.APP_ACTIVITY).ifPresent(options::setAppActivity);

        // If an APK is configured it is installed only when missing/older (noReset keeps app data).
        ConfigReader.getOptional(ConfigKeys.APP_PATH).ifPresent(path -> options.setApp(resolveApk(path)));
        return options;
    }

    private static String resolveApk(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (!path.isAbsolute()) {
            path = ConfigReader.projectRoot().resolve(path);
        }
        if (!Files.isRegularFile(path)) {
            throw new ConfigurationException("APK not found at '" + path + "' (app.path for variant '"
                    + ConfigReader.variant() + "'). Copy the APK there, pass -Dapp.path=<file>, "
                    + "or set app.path empty to launch an already-installed app.");
        }
        return path.toAbsolutePath().toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
