package org.intelehealth.ezazi.automation.pages;

import java.time.Duration;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.driver.DriverManager;
import org.intelehealth.ezazi.automation.utils.DeviceUtils;
import org.intelehealth.ezazi.automation.utils.GestureUtils;
import org.intelehealth.ezazi.automation.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.qameta.allure.Param;
import io.qameta.allure.Step;
import io.qameta.allure.model.Parameter;

/**
 * Parent of every page object. Provides waited, logged and Allure-reported actions so page
 * classes contain only locators and business-level methods.
 *
 * <h2>Rules for page classes</h2>
 * <ol>
 *   <li>Locators are {@code private static final By} fields inside the page - never in tests.</li>
 *   <li>Locator priority: accessibilityId &gt; resourceId &gt; UiAutomator &gt; xpath (last resort).</li>
 *   <li>Public methods describe user intent ({@code login(...)}), return the next page or {@code this},
 *       and are annotated with {@code @Step} so they appear in the Allure report.</li>
 *   <li>No assertions in pages - pages answer questions ({@code isErrorShown()}), tests assert.</li>
 * </ol>
 */
public abstract class BasePage {

    protected final Logger log = LogManager.getLogger(getClass());
    protected final AndroidDriver driver;

    protected BasePage() {
        this.driver = DriverManager.getDriver();
    }

    /**
     * A locator that is unique to this screen and visible once it has loaded.
     *
     * @return anchor locator
     */
    protected abstract By pageAnchor();

    /**
     * @return true when this screen is displayed (short wait)
     */
    public boolean isDisplayed() {
        return WaitUtils.isVisible(pageAnchor(), WaitUtils.shortTimeout());
    }

    /**
     * @return true when this screen is displayed right now (no wait) - use inside custom waits
     */
    public boolean isDisplayedNow() {
        return WaitUtils.isVisibleNow(pageAnchor());
    }

    /**
     * Waits for the page anchor.
     *
     * @param timeout maximum wait
     */
    protected void waitForPageAnchor(Duration timeout) {
        WaitUtils.waitForVisible(pageAnchor(), timeout);
    }

    // ------------------------------------------------------------------ element actions

    /**
     * Waits until clickable, then taps.
     *
     * @param locator     element
     * @param elementName readable name for logs/report
     */
    @Step("Tap \"{elementName}\"")
    protected void tap(By locator, String elementName) {
        log.debug("Tap {}", elementName);
        WaitUtils.waitForClickable(locator).click();
    }

    /**
     * Clears the field and types text. An empty text only clears the field.
     *
     * @param locator     input field
     * @param text        text to type
     * @param elementName readable name
     */
    @Step("Type \"{text}\" into \"{elementName}\"")
    protected void type(By locator, String text, String elementName) {
        log.debug("Type '{}' into {}", text, elementName);
        enterText(locator, text);
    }

    /**
     * Same as {@link #type} but the value is masked in logs and in the Allure report.
     *
     * @param locator     input field
     * @param secret      sensitive value
     * @param elementName readable name
     */
    @Step("Type ******** into \"{elementName}\"")
    protected void typeSecret(By locator, @Param(mode = Parameter.Mode.MASKED) String secret, String elementName) {
        log.debug("Type ******** into {}", elementName);
        enterText(locator, secret);
    }

    /**
     * UiAutomator2 sets text directly, so the field is NOT tapped first: tapping opens the soft
     * keyboard asynchronously, and a keyboard that appears late can cover the next button and
     * swallow its tap (a classic source of flaky form tests on tablets).
     */
    private void enterText(By locator, String text) {
        WebElement field = WaitUtils.waitForVisible(locator);
        field.clear();
        if (text != null && !text.isEmpty()) {
            field.sendKeys(text);
        }
    }

    /** @return visible text of an element */
    protected String getText(By locator) {
        return WaitUtils.waitForVisible(locator).getText();
    }

    /** @return attribute value, e.g. "password", "checked", "content-desc" */
    protected String getAttribute(By locator, String attribute) {
        return WaitUtils.waitForPresence(locator, WaitUtils.defaultTimeout()).getAttribute(attribute);
    }

    /** @return true if visible within the short timeout */
    protected boolean isVisible(By locator) {
        return WaitUtils.isVisible(locator, WaitUtils.shortTimeout());
    }

    /** @return true if visible right now (no wait) */
    protected boolean isVisibleNow(By locator) {
        return WaitUtils.isVisibleNow(locator);
    }

    /** @return all matching elements (no wait) */
    protected List<WebElement> findAll(By locator) {
        return driver.findElements(locator);
    }

    // ------------------------------------------------------------------ common screen actions

    /**
     * Locator for an element by its exact visible text (UiAutomator - faster than xpath).
     *
     * @param text exact text
     * @return locator
     */
    protected static By byText(String text) {
        return AppiumBy.androidUIAutomator("new UiSelector().text(\"" + text.replace("\"", "\\\"") + "\")");
    }

    /** Scrolls until the given text is visible and returns it. */
    protected WebElement scrollToText(String text) {
        return GestureUtils.scrollToText(text);
    }

    /** Hides the soft keyboard if shown. */
    public void hideKeyboard() {
        DeviceUtils.hideKeyboard();
    }

    /** Presses the Android back button. */
    @Step("Press device Back")
    public void pressBack() {
        driver.pressKey(new KeyEvent(AndroidKey.BACK));
    }
}
