package org.intelehealth.ezazi.automation.dataproviders;

import java.lang.reflect.Method;

import org.intelehealth.ezazi.automation.data.TestData;
import org.intelehealth.ezazi.automation.data.TestDataLoader;
import org.intelehealth.ezazi.automation.data.TestDataSource;
import org.testng.annotations.DataProvider;

/**
 * Central DataProviders.
 * <p>
 * The two generic providers read the {@link TestDataSource} annotation of the requesting test, so
 * a new data-driven test needs NO new provider:
 * <pre>{@code
 * @Test(dataProvider = DataProviders.TEST_DATA, dataProviderClass = DataProviders.class)
 * @TestDataSource(sheet = "Login")
 * public void myTest(TestData row) { ... }
 * }</pre>
 * Rows with {@code Run = N} are filtered out. Use {@link #TEST_DATA_PARALLEL} to run rows
 * concurrently (one device per thread; see README "Parallel execution").
 */
public final class DataProviders {

    /** Sequential generic provider. */
    public static final String TEST_DATA = "testData";

    /** Parallel generic provider (rows run on {@code data-provider-thread-count} threads). */
    public static final String TEST_DATA_PARALLEL = "testDataParallel";

    private DataProviders() {
    }

    @DataProvider(name = TEST_DATA)
    public static Object[][] testData(Method method) {
        return TestDataLoader.forMethod(method);
    }

    @DataProvider(name = TEST_DATA_PARALLEL, parallel = true)
    public static Object[][] testDataParallel(Method method) {
        return TestDataLoader.forMethod(method);
    }

    /**
     * Classic, explicit provider - shown for teams that prefer one named provider per sheet.
     * Equivalent to {@code @TestDataSource(sheet = "Login")} + {@link #TEST_DATA}.
     */
    @DataProvider(name = "loginSheet")
    public static Object[][] loginSheet() {
        return TestDataLoader.load("", "Login", TestData.class).stream()
                .map(row -> new Object[] {row})
                .toArray(Object[][]::new);
    }
}
