package org.intelehealth.ezazi.automation.utils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.driver.DriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;

import io.appium.java_client.AppiumBy;
import io.qameta.allure.Step;

/**
 * Handles Android runtime-permission dialogs conditionally: it only clicks when a dialog is really
 * on screen, so tests work the same with or without {@code autoGrantPermissions}.
 * <p>
 * One UiAutomator query matches every "allow" button variant (one round-trip instead of four),
 * then the most appropriate one is chosen: "While using the app" &gt; "Allow all" &gt; "Allow" &gt;
 * "Only this time".
 */
public final class PermissionHandler {

    private static final Logger LOG = LogManager.getLogger(PermissionHandler.class);

    /** Matches any allow-type button of the system permission controller (all Android versions). */
    public static final By ANY_ALLOW_BUTTON = AppiumBy.androidUIAutomator(
            "new UiSelector().resourceIdMatches(\".*:id/permission_allow(_foreground_only|_all|_one_time)?_button\")");

    private static final By ANY_DENY_BUTTON = AppiumBy.androidUIAutomator(
            "new UiSelector().resourceIdMatches(\".*:id/permission_deny(_and_dont_ask_again)?_button\")");

    /** Preference order, best first. */
    private static final List<String> ALLOW_PRIORITY = List.of(
            "permission_allow_foreground_only_button",
            "permission_allow_all_button",
            "permission_allow_button",
            "permission_allow_one_time_button");

    private static final int MAX_DIALOGS = 8;

    private PermissionHandler() {
    }

    /**
     * Accepts every permission dialog that appears in a row (the app often asks for location,
     * notifications, storage... back to back).
     *
     * @return number of dialogs accepted
     */
    @Step("Accept runtime permission dialogs (if shown)")
    public static int acceptAllIfPresent() {
        return acceptAllIfPresent(WaitUtils.shortTimeout());
    }

    /**
     * Same as {@link #acceptAllIfPresent()} with a custom wait for the FIRST dialog.
     *
     * @param firstDialogTimeout how long to wait for the first dialog
     * @return number of dialogs accepted
     */
    public static int acceptAllIfPresent(Duration firstDialogTimeout) {
        int accepted = 0;
        Duration wait = firstDialogTimeout;
        while (accepted < MAX_DIALOGS) {
            Optional<WebElement> button = findBestAllowButton(wait);
            if (button.isEmpty()) {
                break;
            }
            try {
                String id = button.get().getAttribute("resource-id");
                button.get().click();
                accepted++;
                LOG.info("Permission dialog accepted via '{}'", id);
            } catch (StaleElementReferenceException e) {
                LOG.debug("Permission button went stale, re-checking");
            }
            wait = Duration.ofSeconds(2); // follow-up dialogs appear quickly
        }
        return accepted;
    }

    /**
     * Denies the current permission dialog if one is visible.
     *
     * @return true when a dialog was denied
     */
    @Step("Deny runtime permission dialog (if shown)")
    public static boolean denyIfPresent() {
        if (WaitUtils.isVisible(ANY_DENY_BUTTON, WaitUtils.shortTimeout())) {
            DriverManager.getDriver().findElement(ANY_DENY_BUTTON).click();
            return true;
        }
        return false;
    }

    /** @return true if a permission dialog is on screen right now */
    public static boolean isDialogShown() {
        return WaitUtils.isVisibleNow(ANY_ALLOW_BUTTON);
    }

    private static Optional<WebElement> findBestAllowButton(Duration timeout) {
        try {
            List<WebElement> buttons = WaitUtils.until(d -> {
                List<WebElement> found = d.findElements(ANY_ALLOW_BUTTON);
                return found.isEmpty() ? null : found;
            }, timeout, "No permission dialog");
            for (String preferred : ALLOW_PRIORITY) {
                for (WebElement b : buttons) {
                    String id = b.getAttribute("resource-id");
                    if (id != null && id.endsWith(":id/" + preferred)) {
                        return Optional.of(b);
                    }
                }
            }
            return Optional.of(buttons.get(0));
        } catch (TimeoutException | StaleElementReferenceException e) {
            return Optional.empty();
        }
    }
}
