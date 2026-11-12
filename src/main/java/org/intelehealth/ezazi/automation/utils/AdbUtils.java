package org.intelehealth.ezazi.automation.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;

/**
 * Thin, dependency-free wrapper around the host {@code adb} binary.
 * <p>
 * Running adb on the host (instead of Appium's {@code mobile: shell}) works even when no Appium
 * session exists (e.g. emulator boot, collecting logcat after a crash) and does not require the
 * Appium server to run with {@code --relaxed-security}.
 */
public final class AdbUtils {

    private static final Logger LOG = LogManager.getLogger(AdbUtils.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** Result of an executed command. */
    public record CommandResult(int exitCode, String output) {
        /** @return true when the command exited with code 0 */
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    private AdbUtils() {
    }

    /**
     * Android SDK root from {@code ANDROID_HOME} or {@code ANDROID_SDK_ROOT}.
     *
     * @return SDK path or {@code null} if neither variable is set
     */
    public static Path androidSdkRoot() {
        for (String name : List.of("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return Paths.get(value);
            }
        }
        return null;
    }

    /** Absolute path of adb inside the SDK, or plain {@code adb} (resolved from PATH). */
    public static String adbExecutable() {
        return sdkTool("platform-tools", "adb");
    }

    /** Absolute path of the emulator binary inside the SDK, or plain {@code emulator}. */
    public static String emulatorExecutable() {
        return sdkTool("emulator", "emulator");
    }

    private static String sdkTool(String folder, String tool) {
        Path sdk = androidSdkRoot();
        if (sdk != null) {
            Path candidate = sdk.resolve(folder).resolve(IS_WINDOWS ? tool + ".exe" : tool);
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return tool;
    }

    /**
     * Runs {@code adb <args>} and waits for completion.
     *
     * @param timeout maximum time to wait
     * @param args    adb arguments, e.g. {@code "-s", "emulator-5554", "shell", "getprop"}
     * @return exit code and combined stdout/stderr
     */
    public static CommandResult adb(Duration timeout, String... args) {
        List<String> command = new ArrayList<>();
        command.add(adbExecutable());
        command.addAll(Arrays.asList(args));
        return execute(command, timeout);
    }

    /**
     * Runs {@code adb -s <udid> shell <command>} and returns trimmed output.
     *
     * @param udid    device serial; blank means "the only connected device"
     * @param command shell command parts
     * @return trimmed output
     * @throws FrameworkException if the command fails
     */
    public static String shell(String udid, String... command) {
        List<String> args = new ArrayList<>(deviceArgs(udid));
        args.add("shell");
        args.addAll(Arrays.asList(command));
        CommandResult result = adb(DEFAULT_TIMEOUT, args.toArray(String[]::new));
        if (!result.isSuccess()) {
            throw new FrameworkException("adb shell " + String.join(" ", command) + " failed on '" + udid + "': " + result.output());
        }
        return result.output().trim();
    }

    /**
     * Serials of devices that are online ({@code adb devices} state = device).
     *
     * @return list of serials, never null
     */
    public static List<String> getOnlineDevices() {
        CommandResult result = adb(DEFAULT_TIMEOUT, "devices");
        List<String> devices = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && "device".equals(parts[1])) {
                devices.add(parts[0]);
            }
        }
        return devices;
    }

    /**
     * Reads an Android system property.
     *
     * @param udid     device serial
     * @param property property name, e.g. {@code ro.build.version.release}
     * @return value or empty string
     */
    public static String getProp(String udid, String property) {
        try {
            return shell(udid, "getprop", property);
        } catch (FrameworkException e) {
            LOG.debug("getprop {} failed: {}", property, e.getMessage());
            return "";
        }
    }

    /**
     * Last {@code lines} logcat lines, filtered to the app process when it is running.
     *
     * @param udid        device serial
     * @param appPackage  application id used to find the process id (may be null)
     * @param lines       number of lines
     * @return logcat text (never null)
     */
    public static String logcat(String udid, String appPackage, int lines) {
        List<String> args = new ArrayList<>(deviceArgs(udid));
        args.addAll(List.of("logcat", "-d", "-v", "threadtime", "-t", String.valueOf(lines)));
        if (appPackage != null && !appPackage.isBlank()) {
            String pid = adb(DEFAULT_TIMEOUT, concat(deviceArgs(udid), "shell", "pidof", appPackage)).output().trim();
            if (pid.matches("\\d+")) {
                args.add("--pid=" + pid);
            }
        }
        return adb(DEFAULT_TIMEOUT, args.toArray(String[]::new)).output();
    }

    private static List<String> deviceArgs(String udid) {
        return (udid == null || udid.isBlank()) ? List.of() : List.of("-s", udid);
    }

    private static String[] concat(List<String> first, String... rest) {
        List<String> all = new ArrayList<>(first);
        all.addAll(Arrays.asList(rest));
        return all.toArray(String[]::new);
    }

    /**
     * Executes an OS command with a hard timeout; output is read asynchronously so a chatty or
     * hanging process can never block the test thread forever.
     *
     * @param command command and arguments
     * @param timeout maximum duration
     * @return result
     */
    public static CommandResult execute(List<String> command, Duration timeout) {
        LOG.debug("exec: {}", String.join(" ", command));
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new FrameworkException("Cannot start '" + command.get(0)
                    + "'. Is the Android SDK installed and ANDROID_HOME set?", e);
        }
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new FrameworkException("Command timed out after " + timeout.toSeconds() + "s: " + String.join(" ", command));
            }
            return new CommandResult(process.exitValue(), output.get(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new FrameworkException("Interrupted while running " + String.join(" ", command), e);
        } catch (ExecutionException | TimeoutException e) {
            return new CommandResult(process.exitValue(), "");
        }
    }

    private static String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
