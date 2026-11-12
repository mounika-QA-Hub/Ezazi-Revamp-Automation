package org.intelehealth.ezazi.automation.reports;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects facts about the run (env, variant, devices, app version...) from any thread and
 * hands them to {@link AllureManager#writeEnvironment(Map)} at the end of the suite.
 */
public final class ExecutionEnvironment {

    private static final Map<String, String> VALUES = new ConcurrentHashMap<>();

    private ExecutionEnvironment() {
    }

    /**
     * Records a value (first writer wins, so parallel threads don't overwrite each other).
     *
     * @param key   label shown in Allure
     * @param value value
     */
    public static void put(String key, String value) {
        if (key != null && value != null && !value.isBlank()) {
            VALUES.putIfAbsent(key, value);
        }
    }

    /** @return sorted copy of the collected values */
    public static Map<String, String> snapshot() {
        return new TreeMap<>(VALUES);
    }
}
