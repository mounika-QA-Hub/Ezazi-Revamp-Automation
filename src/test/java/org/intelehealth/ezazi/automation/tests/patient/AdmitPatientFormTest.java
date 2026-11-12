package org.intelehealth.ezazi.automation.tests.patient;

import static org.intelehealth.ezazi.automation.assertions.AllureAssert.VERIFY;

import java.time.LocalDateTime;

import org.intelehealth.ezazi.automation.base.BaseTest;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.data.TestData;
import org.intelehealth.ezazi.automation.data.TestDataSource;
import org.intelehealth.ezazi.automation.dataproviders.DataProviders;
import org.intelehealth.ezazi.automation.pages.AdmitPatientPage;
import org.intelehealth.ezazi.automation.pages.HomePage;
import org.intelehealth.ezazi.automation.pages.LoginPage;
import org.intelehealth.ezazi.automation.utils.DateTimeUtils;
import org.intelehealth.ezazi.automation.utils.RandomDataUtils;
import org.intelehealth.ezazi.automation.utils.WaitUtils;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;

/**
 * Data-driven form test with text, numeric, date and time inputs.
 * <p>
 * Covers boundary values (age min / max / max+1 / min-1), past and future dates and time ranges
 * that cross midnight. Dates in Excel are relative tokens ({@code NOW-10m}, {@code TODAY-1 23:30})
 * so the data never goes stale.
 * <p>
 * Requires a reachable backend (login) - hence the {@code requires-backend} group.
 */
@Epic("Patient management")
@Feature("Admit patient")
public class AdmitPatientFormTest extends BaseTest {

    @BeforeMethod(alwaysRun = true)
    public void openAdmitPatientForm() {
        LoginPage loginPage = new LoginPage();
        HomePage homePage = new HomePage();
        // app keeps the login between relaunches (noReset): wait for whichever screen appears first
        WaitUtils.until(d -> loginPage.isDisplayedNow() || homePage.isDisplayedNow() ? Boolean.TRUE : null,
                WaitUtils.appLaunchTimeout(), "Neither Login nor Home screen appeared after launch");
        if (loginPage.isDisplayedNow()) {
            String user = ConfigReader.findSecret(ConfigKeys.SECRET_USERNAME).orElse(null);
            String password = ConfigReader.findSecret(ConfigKeys.SECRET_PASSWORD).orElse(null);
            if (user == null || password == null) {
                throw new SkipException("Set EZAZI_USERNAME and EZAZI_PASSWORD in .env (see .env.example) to run patient tests");
            }
            loginPage.login(ConfigReader.get(ConfigKeys.APP_DEFAULT_LOCATION), user, password);
        }
        homePage.waitUntilLoaded().openAdmitPatientForm();
    }

    @Test(groups = {"regression", "requires-backend"},
            dataProvider = DataProviders.TEST_DATA, dataProviderClass = DataProviders.class,
            description = "Admit patient form: boundary values, past/future dates and cross-midnight times")
    @TestDataSource(sheet = "AdmitPatient")
    @Story("Admission form validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Each Excel row fills the form and checks the success or validation message.")
    public void admitPatientFormValidation(TestData row) {
        LocalDateTime onset = row.getDateTime("LabourOnsetDateTime");
        LocalDateTime admission = row.getDateTime("AdmissionDateTime");
        // resolved values are shown in the report, so "TODAY-1 23:30" is traceable to a real timestamp
        Allure.parameter("Resolved labour onset", String.valueOf(onset));
        Allure.parameter("Resolved admission", String.valueOf(admission));
        if (onset != null && admission != null) {
            Allure.parameter("Crosses midnight", DateTimeUtils.crossesMidnight(onset.toLocalTime(), admission.toLocalTime()));
        }

        AdmitPatientPage form = new AdmitPatientPage()
                .enterFirstName(resolveName(row.get("FirstName"), true))
                .enterLastName(resolveName(row.get("LastName"), false))
                .enterAge(row.get("Age"))
                .setLabourOnsetDateTime(onset)
                .setAdmissionDateTime(admission)
                .tapSave();

        VERIFY.assertTrue(form.isMessageDisplayed(row.get("ExpectedMessage")),
                row.testCaseId() + ": " + row.expectedResult() + " message '" + row.get("ExpectedMessage") + "' is shown");
    }

    private static String resolveName(String value, boolean first) {
        if ("RANDOM".equalsIgnoreCase(value)) {
            return first ? RandomDataUtils.firstName() : RandomDataUtils.lastName();
        }
        return value;
    }
}
