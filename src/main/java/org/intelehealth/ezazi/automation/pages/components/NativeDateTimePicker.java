package org.intelehealth.ezazi.automation.pages.components;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.driver.DriverManager;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;
import org.intelehealth.ezazi.automation.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Step;

/**
 * Drives the stock Android {@code DatePickerDialog} / {@code TimePickerDialog} (what React Native's
 * community date-time picker opens on Android). Reusable by any page.
 * <p>
 * Dates: year list -> month arrows -> day. Times: switches to keyboard mode and types hour/minute,
 * which is far more reliable than dragging clock hands.
 */
public class NativeDateTimePicker {

    private static final Logger LOG = LogManager.getLogger(NativeDateTimePicker.class);

    private static final By DATE_PICKER = AppiumBy.id("android:id/datePicker");
    private static final By HEADER_YEAR = AppiumBy.id("android:id/date_picker_header_year");
    private static final By HEADER_DATE = AppiumBy.id("android:id/date_picker_header_date");
    private static final By PREV_MONTH = AppiumBy.id("android:id/prev");
    private static final By NEXT_MONTH = AppiumBy.id("android:id/next");
    private static final By OK_BUTTON = AppiumBy.id("android:id/button1");
    private static final By TIME_PICKER = AppiumBy.id("android:id/timePicker");
    private static final By TIME_MODE_TOGGLE = AppiumBy.id("android:id/toggle_mode");
    private static final By INPUT_HOUR = AppiumBy.id("android:id/input_hour");
    private static final By INPUT_MINUTE = AppiumBy.id("android:id/input_minute");
    private static final By AM_PM_SPINNER = AppiumBy.id("android:id/am_pm_spinner");
    private static final DateTimeFormatter HEADER_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

    private final AndroidDriver driver = DriverManager.getDriver();

    /** @return true if a native date picker is open (short wait) */
    public static boolean isDatePickerOpen() {
        return WaitUtils.isVisible(DATE_PICKER, WaitUtils.shortTimeout());
    }

    /** @return true if a native time picker is open (short wait) */
    public static boolean isTimePickerOpen() {
        return WaitUtils.isVisible(TIME_PICKER, WaitUtils.shortTimeout());
    }

    /**
     * Picks a date in an open date picker and confirms with OK.
     *
     * @param date target date
     */
    @Step("Pick date {date} in the native date picker")
    public void selectDate(LocalDate date) {
        WaitUtils.waitForVisible(DATE_PICKER);
        selectYear(date.getYear());
        Month shown = currentHeaderMonth();
        int diff = date.getMonthValue() - shown.getValue();
        By arrow = diff > 0 ? NEXT_MONTH : PREV_MONTH;
        for (int i = 0; i < Math.abs(diff); i++) {
            WaitUtils.waitForClickable(arrow).click();
        }
        String dayDescription = String.format(Locale.ENGLISH, "%02d %s %d", date.getDayOfMonth(),
                date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH), date.getYear());
        By day = AppiumBy.accessibilityId(dayDescription);
        if (!WaitUtils.isVisible(day, WaitUtils.shortTimeout())) {
            day = AppiumBy.androidUIAutomator("new UiSelector().resourceId(\"android:id/month_view\")"
                    + ".childSelector(new UiSelector().text(\"" + date.getDayOfMonth() + "\"))");
        }
        WaitUtils.waitForClickable(day).click();
        WaitUtils.waitForClickable(OK_BUTTON).click();
    }

    /**
     * Sets a time in an open time picker (keyboard mode) and confirms with OK.
     *
     * @param time target time
     */
    @Step("Pick time {time} in the native time picker")
    public void selectTime(LocalTime time) {
        WaitUtils.waitForVisible(TIME_PICKER);
        if (!WaitUtils.isVisibleNow(INPUT_HOUR)) {
            WaitUtils.waitForClickable(TIME_MODE_TOGGLE).click();
        }
        boolean twelveHour = WaitUtils.isVisible(AM_PM_SPINNER, WaitUtils.shortTimeout());
        int hour = time.getHour();
        if (twelveHour) {
            hour = hour % 12 == 0 ? 12 : hour % 12;
        }
        replaceText(INPUT_HOUR, String.valueOf(hour));
        replaceText(INPUT_MINUTE, String.format("%02d", time.getMinute()));
        if (twelveHour) {
            WaitUtils.waitForClickable(AM_PM_SPINNER).click();
            String amPm = time.getHour() < 12 ? "AM" : "PM";
            WaitUtils.waitForClickable(AppiumBy.androidUIAutomator("new UiSelector().text(\"" + amPm + "\")")).click();
        }
        WaitUtils.waitForClickable(OK_BUTTON).click();
    }

    private void selectYear(int year) {
        WaitUtils.waitForClickable(HEADER_YEAR).click();
        String scrollable = "new UiScrollable(new UiSelector().resourceId(\"android:id/date_picker_year_picker\"))"
                + ".scrollIntoView(new UiSelector().text(\"" + year + "\"))";
        WebElement yearItem = driver.findElement(AppiumBy.androidUIAutomator(scrollable));
        yearItem.click();
    }

    private Month currentHeaderMonth() {
        String header = WaitUtils.waitForVisible(HEADER_DATE).getText();
        try {
            return Month.from(HEADER_FORMAT.parse(header));
        } catch (DateTimeParseException e) {
            for (Month m : Month.values()) {
                if (header.contains(m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH))) {
                    return m;
                }
            }
            LOG.warn("Cannot parse date picker header '{}'", header);
            throw new FrameworkException("Unrecognised date picker header '" + header + "' (device language must be English)", e);
        }
    }

    private void replaceText(By locator, String value) {
        WebElement field = WaitUtils.waitForVisible(locator);
        field.clear();
        field.sendKeys(value);
    }
}
