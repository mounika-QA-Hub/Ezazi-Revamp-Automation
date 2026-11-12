package org.intelehealth.ezazi.automation.driver;

/**
 * Immutable description of the device a session runs on.
 *
 * @param udid            adb serial (e.g. {@code emulator-5554} or a real device serial); blank lets
 *                        Appium pick the only connected device
 * @param deviceName      human readable name (Appium requires a value but does not use it for routing)
 * @param platformVersion Android version, blank = let Appium detect it
 * @param systemPort      UiAutomator2 server port on the host; MUST be unique per parallel session
 * @param pooled          true when the device came from {@link DevicePool} and must be returned to it
 */
public record DeviceConfig(String udid, String deviceName, String platformVersion, int systemPort, boolean pooled) {

    /** @return short label for logs and reports */
    public String label() {
        String id = (udid == null || udid.isBlank()) ? "<auto>" : udid;
        return deviceName + " [" + id + ", systemPort=" + systemPort + "]";
    }

    @Override
    public String toString() {
        return label();
    }
}
