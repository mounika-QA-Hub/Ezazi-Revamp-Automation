package org.intelehealth.ezazi.automation.exceptions;

/**
 * Thrown when a configuration key or secret is missing or has an invalid value.
 * The message always tells the reader how to fix the problem.
 */
public class ConfigurationException extends FrameworkException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
