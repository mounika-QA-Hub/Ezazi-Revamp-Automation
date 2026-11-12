package org.intelehealth.ezazi.automation.exceptions;

/**
 * Base unchecked exception for framework (infrastructure) problems such as a missing
 * device, a failed Appium session or an unreadable data file.
 * <p>
 * Using a dedicated type makes it easy to tell "the framework/environment broke" apart from
 * "the app behaved wrongly" (an {@link AssertionError}) in logs, Allure categories and retries.
 */
public class FrameworkException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FrameworkException(String message) {
        super(message);
    }

    public FrameworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
