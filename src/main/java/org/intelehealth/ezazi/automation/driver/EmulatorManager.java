package org.intelehealth.ezazi.automation.driver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;
import org.intelehealth.ezazi.automation.utils.AdbUtils;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.FluentWait;

/**
 * Optionally boots an Android Virtual Device before the suite and shuts it down afterwards.
 * <p>
 * Enabled with {@code emulator.start=true}. If any device is already online the emulator is not
 * started (fast local loop: keep your AVD open). Boot completion is detected by polling
 * {@code sys.boot_completed} with a {@link FluentWait} - no fixed sleeps.
 */
public final class EmulatorManager {

    private static final Logger LOG = LogManager.getLogger(EmulatorManager.class);
    private static final Path LOG_FILE = Path.of("target", "logs", "emulator.log");

    private static Process emulatorProcess;
    private static String startedSerial;

    private EmulatorManager() {
    }

    /** Starts the configured AVD when {@code emulator.start=true} and no device is online. */
    public static synchronized void startIfConfigured() {
        if (!ConfigReader.getBoolean(ConfigKeys.EMULATOR_START, false)) {
            return;
        }
        List<String> online = AdbUtils.getOnlineDevices();
        if (!online.isEmpty()) {
            LOG.info("Device(s) already online {} - emulator start skipped", online);
            return;
        }
        String avd = ConfigReader.get(ConfigKeys.EMULATOR_AVD);
        List<String> command = new ArrayList<>(List.of(AdbUtils.emulatorExecutable(), "-avd", avd,
                "-no-snapshot-save", "-no-boot-anim", "-netdelay", "none", "-netspeed", "full"));
        if (ConfigReader.getBoolean(ConfigKeys.EMULATOR_HEADLESS, false)) {
            command.addAll(List.of("-no-window", "-gpu", "swiftshader_indirect", "-no-audio"));
        }
        try {
            Files.createDirectories(LOG_FILE.getParent());
            LOG.info("Starting emulator: {}", String.join(" ", command));
            emulatorProcess = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(LOG_FILE.toFile()).start();
        } catch (IOException e) {
            throw new FrameworkException("Could not start emulator '" + avd + "'. Check ANDROID_HOME and that the AVD exists "
                    + "(emulator -list-avds).", e);
        }
        startedSerial = waitForBoot(ConfigReader.getSeconds(ConfigKeys.EMULATOR_BOOT_TIMEOUT, 240));
        LOG.info("Emulator '{}' booted as {}", avd, startedSerial);
    }

    /** Stops the emulator if (and only if) this framework started it. */
    public static synchronized void stopIfStartedByFramework() {
        if (startedSerial == null || !ConfigReader.getBoolean(ConfigKeys.EMULATOR_STOP_AFTER_SUITE, true)) {
            return;
        }
        try {
            AdbUtils.adb(Duration.ofSeconds(20), "-s", startedSerial, "emu", "kill");
        } catch (FrameworkException e) {
            LOG.warn("emu kill failed: {}", e.getMessage());
        } finally {
            if (emulatorProcess != null) {
                emulatorProcess.destroy();
            }
            LOG.info("Emulator {} stopped", startedSerial);
            startedSerial = null;
            emulatorProcess = null;
        }
    }

    private static String waitForBoot(Duration timeout) {
        try {
            return new FluentWait<>(Boolean.TRUE)
                    .withTimeout(timeout)
                    .pollingEvery(Duration.ofSeconds(3))
                    .ignoring(FrameworkException.class)
                    .withMessage("Emulator did not finish booting in " + timeout.toSeconds() + "s (see " + LOG_FILE + ")")
                    .until(ignored -> AdbUtils.getOnlineDevices().stream()
                            .filter(serial -> serial.startsWith("emulator-"))
                            .filter(serial -> "1".equals(AdbUtils.getProp(serial, "sys.boot_completed")))
                            .findFirst()
                            .orElse(null));
        } catch (TimeoutException e) {
            if (emulatorProcess != null) {
                emulatorProcess.destroyForcibly();
            }
            throw new FrameworkException(e.getMessage(), e);
        }
    }
}
