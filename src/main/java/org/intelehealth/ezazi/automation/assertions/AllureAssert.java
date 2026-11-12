package org.intelehealth.ezazi.automation.assertions;

import org.testng.asserts.Assertion;
import org.testng.asserts.IAssert;

/**
 * Hard assertion (stops the test on failure) that reports every check to the log and as an Allure
 * step. Stateless, so one shared instance is safe across parallel threads:
 *
 * <pre>{@code
 * AllureAssert.VERIFY.assertTrue(page.isPasswordMasked(), "Password is masked by default");
 * }</pre>
 */
public class AllureAssert extends Assertion {

    /** Shared instance. */
    public static final AllureAssert VERIFY = new AllureAssert();

    @Override
    public void onAssertSuccess(IAssert<?> assertCommand) {
        AssertionReporter.passed(assertCommand);
    }

    @Override
    public void onAssertFailure(IAssert<?> assertCommand, AssertionError ex) {
        AssertionReporter.failed(assertCommand, ex);
    }
}
