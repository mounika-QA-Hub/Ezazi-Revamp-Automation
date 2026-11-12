package org.intelehealth.ezazi.automation.tests.login;

import static org.intelehealth.ezazi.automation.assertions.AllureAssert.VERIFY;

import java.util.List;

import org.intelehealth.ezazi.automation.assertions.AllureSoftAssert;
import org.intelehealth.ezazi.automation.base.BaseTest;
import org.intelehealth.ezazi.automation.data.TestData;
import org.intelehealth.ezazi.automation.data.TestDataSource;
import org.intelehealth.ezazi.automation.data.model.LoginTestCase;
import org.intelehealth.ezazi.automation.dataproviders.DataProviders;
import org.intelehealth.ezazi.automation.pages.HomePage;
import org.intelehealth.ezazi.automation.pages.LoginPage;
import org.intelehealth.ezazi.automation.reports.AllureAttachments;
import org.testng.annotations.Test;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;

/**
 * Sign-in / device setup screen tests.
 * <p>
 * Every check is logged as {@code VERIFY PASS/FAIL} and shown as a step in Allure:
 * {@code VERIFY} (hard assertion) for blocking checks, {@link AllureSoftAssert} to check several
 * fields and report ALL mismatches in one run. Assertions live here - page classes never assert.
 */
@Epic("Authentication")
@Feature("Sign in / device setup")
public class LoginTest extends BaseTest {

    @Test(groups = {"smoke", "regression"}, description = "Login screen shows all mandatory elements")
    @Story("Login screen layout")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Launching the app lands on the 'Welcome Back!' screen with location, username, password, "
            + "forgot-password and Setup controls visible.")
    public void loginScreenShowsAllMandatoryElements() {
        LoginPage loginPage = new LoginPage().waitUntilLoaded();

        AllureSoftAssert softly = new AllureSoftAssert();
        softly.assertTrue(loginPage.isWelcomeTitleDisplayed(), "'Welcome Back!' title is displayed");
        softly.assertTrue(loginPage.isLocationDropdownDisplayed(), "Location dropdown is displayed");
        softly.assertTrue(loginPage.isUsernameFieldDisplayed(), "Username field is displayed");
        softly.assertTrue(loginPage.isPasswordFieldDisplayed(), "Password field is displayed");
        softly.assertTrue(loginPage.isForgotPasswordLinkDisplayed(), "'Forgot password?' link is displayed");
        softly.assertTrue(loginPage.isSetupButtonDisplayed(), "'Setup' button is displayed");
        softly.assertAll();
    }

    @Test(groups = {"smoke", "regression"},
            dataProvider = DataProviders.TEST_DATA, dataProviderClass = DataProviders.class,
            description = "Mandatory-field validation driven by Excel (sheet 'Login')")
    @TestDataSource(sheet = "Login")
    @Story("Mandatory field validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Each Excel row enters a username/password combination, taps Setup and verifies exactly the "
            + "expected validation messages are shown (blank expected cell = message must NOT be shown).")
    public void loginValidatesMandatoryFields(TestData row) {
        LoginPage loginPage = new LoginPage().waitUntilLoaded();

        if ("SUCCESS".equalsIgnoreCase(row.expectedResult())) {
            try {
                loginPage.login(row.get("Location"), row.get("Username"), row.get("Password"));
                boolean loginScreenClosed = loginPage.waitUntilGone();
                // capture BEFORE the reset in 'finally', so the report shows the real result screen
                AllureAttachments.screenshot(loginScreenClosed ? "Screen after sign-in" : "Still on login screen after Setup");
                AllureAttachments.pageSource();
                VERIFY.assertTrue(loginScreenClosed, row.testCaseId() + ": login screen closes after a valid sign-in "
                        + "(if this fails, check the device log for 'Login failed' / INVALID_CREDENTIALS)");

                HomePage home = new HomePage();
                AllureSoftAssert softly = new AllureSoftAssert();
                softly.assertTrue(home.isDisplayed(), "Home screen with 'Log out' button is displayed");
                softly.assertTrue(home.isTitleDisplayed(), "'Home' title is displayed");
                softly.assertTrue(home.isActiveCasesPlaceholderDisplayed(), "Active cases placeholder text is displayed");
                softly.assertAll(row.testCaseId());
            } finally {
                resetAppToFirstLaunch(); // the app remembers the login; later tests need the login screen
            }
            return;
        }

        loginPage.enterUsername(row.get("Username"))
                .enterPassword(row.get("Password"))
                .tapSetup();

        AllureSoftAssert softly = new AllureSoftAssert();
        verifyMessage(softly, loginPage, LoginPage.USERNAME_REQUIRED, row.get("ExpectedUsernameError"));
        verifyMessage(softly, loginPage, LoginPage.PASSWORD_REQUIRED, row.get("ExpectedPasswordError"));
        verifyMessage(softly, loginPage, LoginPage.LOCATION_REQUIRED, row.get("ExpectedLocationError"));
        softly.assertAll(row.testCaseId());
    }

    @Test(groups = {"regression"},
            dataProvider = DataProviders.TEST_DATA, dataProviderClass = DataProviders.class,
            description = "Validation edge cases driven by JSON mapped to a POJO")
    @TestDataSource(file = "testdata/json/login-validation.json", type = LoginTestCase.class)
    @Story("Mandatory field validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("JSON example: every case lists the validation messages that must appear; all other known "
            + "messages must stay hidden.")
    public void loginValidationEdgeCasesFromJson(LoginTestCase testCase) {
        LoginPage loginPage = new LoginPage().waitUntilLoaded()
                .enterUsername(testCase.getUsername())
                .enterPassword(testCase.getPassword())
                .tapSetup();

        AllureSoftAssert softly = new AllureSoftAssert();
        List<String> expected = testCase.getExpectedErrors();
        for (String message : LoginPage.ALL_VALIDATION_MESSAGES) {
            verifyMessage(softly, loginPage, message, expected.contains(message) ? message : "");
        }
        softly.assertAll(testCase.testCaseId());
    }

    @Test(groups = {"regression"}, description = "Password is masked by default and can be revealed")
    @Story("Password visibility")
    @Severity(SeverityLevel.NORMAL)
    @Issue("EZAZI-101")
    @Description("Typed password is hidden by default; tapping the eye icon reveals it and tapping again hides it.")
    public void passwordIsMaskedAndCanBeRevealed() {
        LoginPage loginPage = new LoginPage().waitUntilLoaded().enterPassword("Sample@123");
        VERIFY.assertTrue(loginPage.isPasswordMasked(), "Password is masked by default");

        loginPage.togglePasswordVisibility();
        VERIFY.assertFalse(loginPage.isPasswordMasked(), "Password is visible after tapping the eye icon");

        loginPage.togglePasswordVisibility();
        VERIFY.assertTrue(loginPage.isPasswordMasked(), "Password is masked again after the second tap");
    }

    /**
     * Expected non-blank: message must be visible (waits briefly).
     * Expected blank: message must NOT be visible (checked instantly - no wasted waiting).
     */
    private static void verifyMessage(AllureSoftAssert softly, LoginPage page, String message, String expected) {
        if (expected == null || expected.isBlank()) {
            softly.assertFalse(page.isValidationMessageDisplayedNow(message), "'" + message + "' is NOT shown");
        } else {
            softly.assertTrue(page.isValidationMessageDisplayed(expected), "'" + expected + "' is shown");
        }
    }
}
