package org.intelehealth.ezazi.automation.pages;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.intelehealth.ezazi.automation.pages.components.NativeDateTimePicker;
import org.intelehealth.ezazi.automation.utils.DateTimeUtils;
import org.intelehealth.ezazi.automation.utils.WaitUtils;
import org.openqa.selenium.By;

import io.appium.java_client.AppiumBy;
import io.qameta.allure.Step;

/**
 * "Admit patient" form - the sample form with text, number, date and time inputs.
 * <p>
 * ASSUMPTION: this screen sits behind a backend login, so locators could not be captured from the
 * current build. They follow the README "Locator contract" (React Native {@code accessibilityLabel}
 * per input). Only the constants below need updating once the screen is available - tests and data
 * stay untouched.
 * <p>
 * Date/time fields work with either native pickers or plain text inputs: after tapping the field
 * the page checks whether a picker opened and uses {@link NativeDateTimePicker}; otherwise it types.
 */
public class AdmitPatientPage extends BasePage {

    private static final By SCREEN_TITLE = AppiumBy.accessibilityId("Admit Patient Form");
    private static final By FIRST_NAME_INPUT = AppiumBy.accessibilityId("first-name-input");
    private static final By LAST_NAME_INPUT = AppiumBy.accessibilityId("last-name-input");
    private static final By AGE_INPUT = AppiumBy.accessibilityId("age-input");
    private static final By ADMISSION_DATE_INPUT = AppiumBy.accessibilityId("admission-date-input");
    private static final By ADMISSION_TIME_INPUT = AppiumBy.accessibilityId("admission-time-input");
    private static final By LABOUR_ONSET_DATE_INPUT = AppiumBy.accessibilityId("labour-onset-date-input");
    private static final By LABOUR_ONSET_TIME_INPUT = AppiumBy.accessibilityId("labour-onset-time-input");
    private static final By SAVE_BUTTON = AppiumBy.accessibilityId("save-patient-button");

    @Override
    protected By pageAnchor() {
        return SCREEN_TITLE;
    }

    /** Waits for the form. */
    public AdmitPatientPage waitUntilLoaded() {
        waitForPageAnchor(WaitUtils.defaultTimeout());
        return this;
    }

    public AdmitPatientPage enterFirstName(String firstName) {
        type(FIRST_NAME_INPUT, firstName, "First name");
        return this;
    }

    public AdmitPatientPage enterLastName(String lastName) {
        type(LAST_NAME_INPUT, lastName, "Last name");
        return this;
    }

    public AdmitPatientPage enterAge(String age) {
        type(AGE_INPUT, age, "Age");
        return this;
    }

    /** Sets admission date + time; {@code null} leaves both fields empty. */
    @Step("Set admission date/time to {dateTime}")
    public AdmitPatientPage setAdmissionDateTime(LocalDateTime dateTime) {
        if (dateTime != null) {
            setDate(ADMISSION_DATE_INPUT, dateTime.toLocalDate(), "Admission date");
            setTime(ADMISSION_TIME_INPUT, dateTime.toLocalTime(), "Admission time");
        }
        return this;
    }

    /** Sets labour onset date + time; {@code null} leaves both fields empty. */
    @Step("Set labour onset date/time to {dateTime}")
    public AdmitPatientPage setLabourOnsetDateTime(LocalDateTime dateTime) {
        if (dateTime != null) {
            setDate(LABOUR_ONSET_DATE_INPUT, dateTime.toLocalDate(), "Labour onset date");
            setTime(LABOUR_ONSET_TIME_INPUT, dateTime.toLocalTime(), "Labour onset time");
        }
        return this;
    }

    /** Taps Save (scrolls to it first on small screens). */
    public AdmitPatientPage tapSave() {
        hideKeyboard();
        if (!isVisibleNow(SAVE_BUTTON)) {
            scrollToText("Save");
        }
        tap(SAVE_BUTTON, "Save");
        return this;
    }

    /** @return true if the given message (success or validation) is displayed */
    public boolean isMessageDisplayed(String message) {
        return WaitUtils.isVisible(byText(message), WaitUtils.defaultTimeout());
    }

    private void setDate(By field, LocalDate date, String name) {
        tap(field, name);
        if (NativeDateTimePicker.isDatePickerOpen()) {
            new NativeDateTimePicker().selectDate(date);
        } else {
            type(field, DateTimeUtils.formatForApp(date), name);
        }
    }

    private void setTime(By field, LocalTime time, String name) {
        tap(field, name);
        if (NativeDateTimePicker.isTimePickerOpen()) {
            new NativeDateTimePicker().selectTime(time);
        } else {
            type(field, DateTimeUtils.formatForApp(time), name);
        }
    }
}
