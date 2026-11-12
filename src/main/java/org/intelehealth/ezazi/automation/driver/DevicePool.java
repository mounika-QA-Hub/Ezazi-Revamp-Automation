package org.intelehealth.ezazi.automation.driver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;
import org.intelehealth.ezazi.automation.utils.AdbUtils;

/**
 * Thread-safe pool of devices for parallel execution.
 * <p>
 * Each test thread {@link #acquire() borrows} a device (with its own UiAutomator2 {@code systemPort})
 * and {@link #release(DeviceConfig) returns} it when its session ends. Threads that find the pool
 * empty simply wait, so running more threads than devices is safe (just not faster).
 * <p>
 * Devices come from {@code device.udids}: either an explicit comma-separated list or {@code auto}
 * (every device currently listed by {@code adb devices}).
 */
public final class DevicePool {

    private static final Logger LOG = LogManager.getLogger(DevicePool.class);
    private static volatile BlockingQueue<DeviceConfig> pool;
    private static volatile int size;

    private DevicePool() {
    }

    /**
     * Borrows a device, waiting up to {@code device.acquire.timeout.seconds}.
     *
     * @return a device reserved for the calling thread
     */
    public static DeviceConfig acquire() {
        BlockingQueue<DeviceConfig> queue = queue();
        Duration timeout = ConfigReader.getSeconds(ConfigKeys.DEVICE_ACQUIRE_TIMEOUT, 600);
        try {
            DeviceConfig device = queue.poll(timeout.toSeconds(), TimeUnit.SECONDS);
            if (device == null) {
                throw new FrameworkException("No free device after " + timeout.toSeconds()
                        + "s. Reduce parallel threads or connect more devices (device.udids).");
            }
            LOG.info("Device acquired: {}", device);
            return device;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FrameworkException("Interrupted while waiting for a device", e);
        }
    }

    /**
     * Returns a device to the pool. Devices that were not taken from the pool are ignored.
     *
     * @param device device to release (null-safe)
     */
    public static void release(DeviceConfig device) {
        if (device != null && device.pooled()) {
            queue().offer(device);
            LOG.info("Device released: {}", device);
        }
    }

    /**
     * Number of devices in the pool (initialises the pool on first call).
     *
     * @return pool capacity
     */
    public static int size() {
        queue();
        return size;
    }

    private static BlockingQueue<DeviceConfig> queue() {
        if (pool == null) {
            synchronized (DevicePool.class) {
                if (pool == null) {
                    List<DeviceConfig> devices = discoverDevices();
                    size = devices.size();
                    pool = new LinkedBlockingQueue<>(devices);
                    LOG.info("Device pool initialised with {} device(s): {}", size, devices);
                }
            }
        }
        return pool;
    }

    private static List<DeviceConfig> discoverDevices() {
        List<String> udids = ConfigReader.getList(ConfigKeys.DEVICE_UDIDS);
        if (udids.isEmpty() || (udids.size() == 1 && "auto".equalsIgnoreCase(udids.get(0)))) {
            try {
                udids = AdbUtils.getOnlineDevices();
            } catch (FrameworkException e) {
                LOG.warn("Could not query adb for devices: {}", e.getMessage());
                udids = List.of();
            }
            if (udids.isEmpty()) {
                LOG.warn("adb reports no online device. Using a single '<auto>' slot; Appium will fail fast if nothing is connected.");
                udids = List.of("");
            }
        }
        String deviceName = ConfigReader.get(ConfigKeys.DEVICE_NAME, "Android Device");
        String platformVersion = ConfigReader.get(ConfigKeys.DEVICE_PLATFORM_VERSION, "");
        int basePort = ConfigReader.getInt(ConfigKeys.DEVICE_SYSTEM_PORT_BASE, 8200);
        List<DeviceConfig> devices = new ArrayList<>();
        for (int i = 0; i < udids.size(); i++) {
            devices.add(new DeviceConfig(udids.get(i), deviceName, platformVersion, basePort + i, true));
        }
        return devices;
    }
}
