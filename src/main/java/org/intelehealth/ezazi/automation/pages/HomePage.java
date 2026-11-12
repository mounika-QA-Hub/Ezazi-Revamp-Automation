package org.intelehealth.ezazi.automation.pages;

import org.intelehealth.ezazi.automation.utils.WaitUtils;
import org.openqa.selenium.By;

import io.appium.java_client.AppiumBy;
import io.qameta.allure.Step;

/**
 * Home screen shown after a successful sign-in.
 * <p>
 * Locators captured from the real app (org.intelehealth.ezazi 0.1.0, 2026-11-11). In this build the
 * Home screen is a placeholder: title "Home", the text "Logged in. Active cases will appear here."
 * and a "Log out (EZ-943)" button. It has NO "Admit Patient" entry yet, so
 * {@link #openAdmitPatientForm()} uses an assumed locator - update it when the feature ships.
 */
public class HomePage extends BasePage {

    private static final By TITLE = byText("Home");
    private static final By ACTIVE_CASES_PLACEHOLDER = byText("Logged in. Active cases will appear here.");
    // label currently carries a ticket id ("Log out (EZ-943)"), so match on the start only
    private static final By LOGOUT_BUTTON = AppiumBy.androidUIAutomator(
            "new UiSelector().className(\"android.widget.Button\").descriptionStartsWith(\"Log out\")");
    // ASSUMPTION: not present in build 0.1.0
    private static final By ADMIT_PATIENT_BUTTON = AppiumBy.accessibilityId("Admit Patient");

    @Override
    protected By pageAnchor() {
        return LOGOUT_BUTTON;
    }

    /** Waits for the Home screen after sign-in. */
    @Step("Wait for the Home screen")
    public HomePage waitUntilLoaded() {
        waitForPageAnchor(WaitUtils.appLaunchTimeout());
        return this;
    }

    /** @return true if the "Home" title is shown */
    public boolean isTitleDisplayed() {
        return isVisible(TITLE);
    }

    /** @return true if the placeholder text for active cases is shown */
    public boolean isActiveCasesPlaceholderDisplayed() {
        return isVisible(ACTIVE_CASES_PLACEHOLDER);
    }

    /** Logs out and returns to the login screen. */
    @Step("Log out")
    public LoginPage logout() {
        tap(LOGOUT_BUTTON, "Log out");
        return new LoginPage().waitUntilLoaded();
    }

    /** Opens the Admit Patient form (not available in build 0.1.0 - see class comment). */
    @Step("Open the Admit Patient form")
    public AdmitPatientPage openAdmitPatientForm() {
        tap(ADMIT_PATIENT_BUTTON, "Admit Patient");
        return new AdmitPatientPage().waitUntilLoaded();
    }
}
