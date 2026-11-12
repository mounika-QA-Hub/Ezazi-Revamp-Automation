package org.intelehealth.ezazi.automation.data;

/**
 * Implemented by every data-row type. The Allure listener uses it to name each data-driven
 * iteration "TC_LOGIN_001 - All fields empty" instead of a generic method name.
 */
public interface TestCaseInfo {

    /** @return test case id, e.g. TC_LOGIN_001 */
    String testCaseId();

    /** @return one-line human description */
    String description();
}
