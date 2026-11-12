package org.intelehealth.ezazi.automation.tests.framework;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.data.TestData;
import org.intelehealth.ezazi.automation.data.TestDataLoader;
import org.intelehealth.ezazi.automation.data.model.LoginTestCase;
import org.intelehealth.ezazi.automation.utils.DateTimeUtils;
import org.intelehealth.ezazi.automation.utils.RandomDataUtils;
import org.testng.annotations.Test;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;

/**
 * Device-free checks of the framework itself (config, data readers, date tokens).
 * Runs in seconds - use it as the first CI stage: {@code mvn test -Dsuite=framework}.
 * Does NOT extend BaseTest, so no Appium server or device is needed.
 */
@Epic("Framework")
@Feature("Self test")
public class FrameworkSelfTest {

    @Test(groups = "framework")
    public void configResolvesEnvironmentAndVariant() {
        assertFalse(ConfigReader.env().isBlank());
        assertFalse(ConfigReader.get(ConfigKeys.APP_PACKAGE).isBlank(), "app.package must be defined for the variant");
        assertTrue(ConfigReader.getInt(ConfigKeys.TIMEOUT_EXPLICIT) > 0);
    }

    @Test(groups = "framework")
    public void systemPropertyOverridesFileValue() {
        String key = ConfigKeys.TIMEOUT_POLLING_MILLIS;
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "123");
            assertEquals(ConfigReader.getInt(key), 123);
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test(groups = "framework")
    public void excelRowsWithRunNAreSkipped() {
        List<Object> rows = TestDataLoader.load("", "Login", TestData.class);
        assertFalse(rows.isEmpty(), "Login sheet should have runnable rows");
        assertTrue(rows.stream().map(r -> ((TestData) r).asMap().get(TestData.RUN))
                .allMatch(run -> !"N".equalsIgnoreCase(run)), "Run=N rows must be filtered out");
    }

    @Test(groups = "framework")
    public void jsonRowsMapToPojo() {
        List<Object> rows = TestDataLoader.load("testdata/json/login-validation.json", "", LoginTestCase.class);
        assertFalse(rows.isEmpty());
        LoginTestCase first = (LoginTestCase) rows.get(0);
        assertFalse(first.testCaseId().isBlank());
        assertTrue(first.getExpectedErrors() != null);
    }

    @Test(groups = "framework")
    public void relativeDateTokensResolve() {
        LocalDate today = LocalDate.now();
        assertEquals(DateTimeUtils.resolveDate("TODAY"), today);
        assertEquals(DateTimeUtils.resolveDate("today+1"), today.plusDays(1));
        assertEquals(DateTimeUtils.resolveDate("TODAY-30"), today.minusDays(30));
        assertEquals(DateTimeUtils.resolveDate("YESTERDAY"), today.minusDays(1));
        assertEquals(DateTimeUtils.resolveDate("2026-01-31"), LocalDate.of(2026, 1, 31));
        assertEquals(DateTimeUtils.resolveDateTime("TODAY-1 23:30"), today.minusDays(1).atTime(23, 30));
        assertEquals(DateTimeUtils.resolveTime("11:30 PM"), LocalTime.of(23, 30));

        LocalDateTime nowMinus2h = DateTimeUtils.resolveDateTime("NOW-2h");
        long minutes = ChronoUnit.MINUTES.between(nowMinus2h, LocalDateTime.now());
        assertTrue(minutes >= 119 && minutes <= 121, "NOW-2h should be ~120 minutes ago but was " + minutes);
    }

    @Test(groups = "framework")
    public void crossMidnightHelpers() {
        assertTrue(DateTimeUtils.crossesMidnight(LocalTime.of(23, 30), LocalTime.of(0, 30)));
        assertFalse(DateTimeUtils.crossesMidnight(LocalTime.of(8, 0), LocalTime.of(9, 0)));
        assertEquals(DateTimeUtils.durationAcrossMidnight(LocalTime.of(23, 30), LocalTime.of(0, 30)), Duration.ofHours(1));
    }

    @Test(groups = "framework")
    public void randomDataIsWithinBounds() {
        for (int i = 0; i < 50; i++) {
            int n = RandomDataUtils.numberBetween(12, 60);
            assertTrue(n >= 12 && n <= 60);
        }
        assertEquals(RandomDataUtils.mobileNumber().length(), 10);
        assertTrue(RandomDataUtils.uniqueId("PAT").startsWith("PAT-"));
    }
}
