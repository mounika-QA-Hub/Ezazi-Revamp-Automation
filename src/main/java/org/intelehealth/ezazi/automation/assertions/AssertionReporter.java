package org.intelehealth.ezazi.automation.assertions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.asserts.IAssert;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;

/**
 * Makes every assertion visible: one log line and one Allure step per check, passed or failed.
 * Without this, a passing assertion leaves no trace and a report looks as if nothing was verified.
 */
final class AssertionReporter {

    private static final Logger LOG = LogManager.getLogger("VERIFY");

    private AssertionReporter() {
    }

    static void passed(IAssert<?> assertion) {
        String text = describe(assertion);
        LOG.info("VERIFY PASS  {}", text);
        Allure.step("Verify: " + text, Status.PASSED);
    }

    static void failed(IAssert<?> assertion, AssertionError error) {
        String text = describe(assertion);
        String detail = " (expected [" + assertion.getExpected() + "] but found [" + assertion.getActual() + "])";
        LOG.error("VERIFY FAIL  {}{}", text, detail);
        Allure.step("Verify: " + text + detail, Status.FAILED);
    }

    private static String describe(IAssert<?> assertion) {
        String message = assertion.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "expected [" + assertion.getExpected() + "], actual [" + assertion.getActual() + "]";
    }
}
