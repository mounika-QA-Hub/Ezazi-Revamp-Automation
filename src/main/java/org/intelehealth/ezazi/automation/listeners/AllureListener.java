package org.intelehealth.ezazi.automation.listeners;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.data.TestCaseInfo;
import org.intelehealth.ezazi.automation.driver.DriverManager;
import org.intelehealth.ezazi.automation.reports.AllureAttachments;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import io.qameta.allure.Allure;

/**
 * Enriches Allure results.
 * <ul>
 *   <li>Names data-driven iterations after their data row ("TC_LOGIN_001 - All fields empty").</li>
 *   <li>On failure attaches a screenshot, the page source (XML) and the app's logcat.</li>
 * </ul>
 * Attachments are taken in {@link #afterInvocation} - immediately after the test body and BEFORE
 * {@code @AfterMethod} runs - so the session is still alive and the screen still shows the failure,
 * and before Allure closes the test result (attachments added from {@code onTestFailure} can arrive
 * after Allure has already written the result and be lost).
 */
public class AllureListener implements ITestListener, IInvokedMethodListener {

    private static final Logger LOG = LogManager.getLogger(AllureListener.class);

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult result) {
        if (!method.isTestMethod()) {
            return;
        }
        Object[] params = result.getParameters();
        if (params != null && params.length > 0 && params[0] instanceof TestCaseInfo info && info.testCaseId() != null) {
            String name = info.testCaseId() + " - " + info.description();
            Allure.getLifecycle().updateTestCase(tc -> tc.setName(name));
            Allure.parameter("Test case", info.testCaseId());
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result) {
        if (!method.isTestMethod() || !DriverManager.hasDriver()) {
            return;
        }
        if (result.getStatus() == ITestResult.FAILURE || result.getThrowable() != null) {
            LOG.info("Collecting failure artifacts for {}", result.getName());
            AllureAttachments.failureArtifacts();
        } else if (ConfigReader.getBoolean(ConfigKeys.REPORT_SCREENSHOT_ON_SUCCESS, false)) {
            AllureAttachments.screenshot("Final screen");
        }
    }
}
