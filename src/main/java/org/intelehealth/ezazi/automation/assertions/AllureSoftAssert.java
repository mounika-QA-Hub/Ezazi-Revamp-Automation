package org.intelehealth.ezazi.automation.assertions;

import org.testng.asserts.IAssert;
import org.testng.asserts.SoftAssert;

/**
 * TestNG {@link SoftAssert} that reports every check (pass and fail) to the log and as an Allure
 * step. Use for multi-field validation; always finish with {@code assertAll()}.
 *
 * <pre>{@code
 * AllureSoftAssert softly = new AllureSoftAssert();
 * softly.assertTrue(page.isTitleDisplayed(), "Title is displayed");
 * softly.assertAll();
 * }</pre>
 * Write messages as the EXPECTATION ("Title is displayed"), so they read well in both outcomes.
 */
public class AllureSoftAssert extends SoftAssert {

    @Override
    public void onAssertSuccess(IAssert<?> assertCommand) {
        AssertionReporter.passed(assertCommand);
    }

    @Override
    public void onAssertFailure(IAssert<?> assertCommand, AssertionError ex) {
        AssertionReporter.failed(assertCommand, ex);
    }
}
