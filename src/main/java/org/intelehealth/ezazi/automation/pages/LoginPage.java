package org.intelehealth.ezazi.automation.pages;

import java.util.List;

import org.intelehealth.ezazi.automation.exceptions.FrameworkException;
import org.intelehealth.ezazi.automation.utils.PermissionHandler;
import org.intelehealth.ezazi.automation.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

import io.appium.java_client.AppiumBy;
import io.qameta.allure.Param;
import io.qameta.allure.Step;
import io.qameta.allure.model.Parameter;

/**
 * eZAZI "Welcome Back!" sign-in / device setup screen.
 * <p>
 * Locators were taken from the real app (org.intelehealth.ezazi 0.1.0). The username and password
 * inputs have no accessibility label or test id yet, so they are located by EditText order; ask the
 * app team to add {@code testID="username-input"} / {@code testID="password-input"} and switch to
 * {@code AppiumBy.accessibilityId(...)} - see README "Locator contract".
 */
public class LoginPage extends BasePage {

    // ----- Validation messages shown by the app (public so tests/data can reference them) -----
    public static final String LOCATION_REQUIRED = "Please select a location";
    public static final String USERNAME_REQUIRED = "Please enter your username";
    public static final String PASSWORD_REQUIRED = "Please enter your password";
    public static final List<String> ALL_VALIDATION_MESSAGES = List.of(LOCATION_REQUIRED, USERNAME_REQUIRED, PASSWORD_REQUIRED);

    // ----- Locators (accessibilityId first, UiAutomator where no id exists) -----
    private static final By SETUP_BUTTON = AppiumBy.accessibilityId("Setup");
    private static final By LOCATION_DROPDOWN = AppiumBy.accessibilityId("Select Location");
    private static final By SHOW_PASSWORD_TOGGLE = AppiumBy.androidUIAutomator(
            "new UiSelector().descriptionMatches(\"(Show|Hide) password\")");
    private static final By FORGOT_PASSWORD_LINK = AppiumBy.accessibilityId("Forgot password?");
    private static final By LOCATION_FETCH_ERROR = AppiumBy.accessibilityId("Unable to fetch locations. Please try again.");
    // Location options are Buttons whose accessibility id is the location name, e.g. "Maternity Hospital 1"
    private static final By ALL_BUTTONS = AppiumBy.className("android.widget.Button");
    private static final By WELCOME_TITLE = byText("Welcome Back!");
    private static final By CONNECTION_ERROR_BANNER = byText("Something went wrong");
    // EditText #0 is the read-only location field, #1 username, #2 password
    private static final By USERNAME_INPUT = AppiumBy.androidUIAutomator(
            "new UiSelector().className(\"android.widget.EditText\").instance(1)");
    private static final By PASSWORD_INPUT = AppiumBy.androidUIAutomator(
            "new UiSelector().className(\"android.widget.EditText\").instance(2)");

    @Override
    protected By pageAnchor() {
        return SETUP_BUTTON;
    }

    /**
     * Waits for the screen after app (re)launch, accepting runtime-permission dialogs if the app
     * shows them first.
     *
     * @return this page
     */
    @Step("Wait for the Login screen")
    public LoginPage waitUntilLoaded() {
        By first = WaitUtils.waitForAnyVisible(WaitUtils.appLaunchTimeout(), SETUP_BUTTON, PermissionHandler.ANY_ALLOW_BUTTON);
        if (first == PermissionHandler.ANY_ALLOW_BUTTON) {
            PermissionHandler.acceptAllIfPresent();
            waitForPageAnchor(WaitUtils.appLaunchTimeout());
        }
        return this;
    }

    // ------------------------------------------------------------------ actions

    /**
     * Opens the location dropdown and picks a location.
     * <p>
     * The app loads locations from the server about 1 s after launch, so a dropdown opened earlier is
     * empty (or does not open). Each attempt waits briefly for the wanted option; if it is not there,
     * the dropdown is tapped again - that closes an empty list or opens one that did not open - and
     * checked again, until {@code timeout.explicit.seconds}. No fixed sleeps.
     *
     * @param location exact location name as shown in the list, e.g. "Maternity Hospital 1"
     * @return this page
     * @throws FrameworkException if the backend returned no locations or the name is not in the list
     */
    @Step("Select location \"{location}\"")
    public LoginPage selectLocation(String location) {
        By option = AppiumBy.accessibilityId(location);
        long deadline = System.currentTimeMillis() + WaitUtils.defaultTimeout().toMillis();
        tap(LOCATION_DROPDOWN, "Location dropdown");
        while (true) {
            By shown = waitForOption(option);
            if (shown == option) {
                tap(option, location);
                return this;
            }
            if (shown == LOCATION_FETCH_ERROR) {
                throw new FrameworkException("The app could not fetch locations (backend unreachable from the device?). "
                        + "Check device network and the server configured in this APK.");
            }
            if (System.currentTimeMillis() > deadline) {
                throw new FrameworkException("Location '" + location + "' did not appear in the dropdown within "
                        + WaitUtils.defaultTimeout().toSeconds() + "s. Buttons on screen: " + getVisibleButtonLabels()
                        + ". Check app.default.location in config/env/<env>.properties - the name must match exactly, "
                        + "including spaces (e.g. 'Maternity Hospital 1').");
            }
            log.debug("Location '{}' not shown yet - toggling the dropdown", location);
            tap(LOCATION_DROPDOWN, "Location dropdown (reopen)");
        }
    }

    /** @return the option or the fetch-error locator if one appears within the short timeout, else null */
    private By waitForOption(By option) {
        try {
            return WaitUtils.waitForAnyVisible(WaitUtils.shortTimeout(), option, LOCATION_FETCH_ERROR);
        } catch (TimeoutException notYet) {
            return null;
        }
    }

    /** @return accessibility labels of all buttons on screen except the login form's own controls */
    private List<String> getVisibleButtonLabels() {
        List<String> ownControls = List.of("Select Location", "Show password", "Hide password", "Setup");
        return findAll(ALL_BUTTONS).stream()
                .map(e -> e.getAttribute("content-desc"))
                .filter(d -> d != null && !d.isBlank() && !ownControls.contains(d))
                .toList();
    }

    /** Types the username (empty string clears the field). */
    @Step("Enter username \"{username}\"")
    public LoginPage enterUsername(String username) {
        type(USERNAME_INPUT, username, "Username");
        return this;
    }

    /** Types the password (masked in report). Empty string clears the field. */
    @Step("Enter password")
    public LoginPage enterPassword(@Param(mode = Parameter.Mode.MASKED) String password) {
        typeSecret(PASSWORD_INPUT, password, "Password");
        return this;
    }

    /** Taps the Setup (sign-in) button. */
    public LoginPage tapSetup() {
        hideKeyboard();
        tap(SETUP_BUTTON, "Setup");
        return this;
    }

    /** Toggles password visibility (eye icon). */
    public LoginPage togglePasswordVisibility() {
        tap(SHOW_PASSWORD_TOGGLE, "Show/Hide password");
        return this;
    }

    /** Taps "Forgot password?". */
    public void tapForgotPassword() {
        tap(FORGOT_PASSWORD_LINK, "Forgot password?");
    }

    /**
     * Full sign-in flow.
     *
     * @param location location name
     * @param username user name
     * @param password password (masked in report)
     */
    @Step("Sign in as \"{username}\" at \"{location}\"")
    public void login(String location, String username, @Param(mode = Parameter.Mode.MASKED) String password) {
        selectLocation(location);
        enterUsername(username);
        enterPassword(password);
        tapSetup();
    }

    // ------------------------------------------------------------------ queries (no assertions here)

    /** @return true if the validation message is shown (waits briefly) */
    public boolean isValidationMessageDisplayed(String message) {
        return isVisible(byText(message));
    }

    /** @return true if the validation message is shown right now (no wait) */
    public boolean isValidationMessageDisplayedNow(String message) {
        return isVisibleNow(byText(message));
    }

    /** @return true if the password field masks its content */
    public boolean isPasswordMasked() {
        return Boolean.parseBoolean(getAttribute(PASSWORD_INPUT, "password"));
    }

    /** @return the username field text (placeholder "Username" when empty) */
    public String getUsernameFieldText() {
        return getText(USERNAME_INPUT);
    }

    public boolean isWelcomeTitleDisplayed() {
        return isVisible(WELCOME_TITLE);
    }

    public boolean isLocationDropdownDisplayed() {
        return isVisible(LOCATION_DROPDOWN);
    }

    public boolean isUsernameFieldDisplayed() {
        return isVisible(USERNAME_INPUT);
    }

    public boolean isPasswordFieldDisplayed() {
        return isVisible(PASSWORD_INPUT);
    }

    public boolean isSetupButtonDisplayed() {
        return isVisible(SETUP_BUTTON);
    }

    public boolean isForgotPasswordLinkDisplayed() {
        return isVisible(FORGOT_PASSWORD_LINK);
    }

    /** @return true if the "Something went wrong / check your connection" banner is shown */
    public boolean isConnectionErrorDisplayed() {
        return isVisibleNow(CONNECTION_ERROR_BANNER);
    }

    /** @return true once the login screen has gone (e.g. after a successful sign-in) */
    public boolean waitUntilGone() {
        return WaitUtils.waitForInvisibility(SETUP_BUTTON, WaitUtils.defaultTimeout());
    }
}
