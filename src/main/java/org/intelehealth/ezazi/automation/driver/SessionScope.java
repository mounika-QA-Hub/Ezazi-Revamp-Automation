package org.intelehealth.ezazi.automation.driver;

import java.util.Locale;

/**
 * How long one Appium session lives.
 * <ul>
 *   <li>{@link #CLASS} (default, fastest): one session per test class; the app is relaunched between
 *       tests according to {@code app.reset.strategy}.</li>
 *   <li>{@link #METHOD} (most isolated): a brand-new session per test method. Required for
 *       {@code parallel="methods"} and parallel DataProviders.</li>
 * </ul>
 */
public enum SessionScope {
    CLASS, METHOD;

    /**
     * Parses a config value, defaulting to {@link #CLASS}.
     *
     * @param value text such as "class" or "method"
     * @return scope
     */
    public static SessionScope from(String value) {
        if (value == null || value.isBlank()) {
            return CLASS;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
