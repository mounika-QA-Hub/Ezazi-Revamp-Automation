package org.intelehealth.ezazi.automation.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares where a data-driven test gets its rows from. Combined with the generic
 * {@code DataProviders.TEST_DATA} provider, adding a new data-driven test needs zero provider code:
 *
 * <pre>{@code
 * @Test(dataProvider = DataProviders.TEST_DATA, dataProviderClass = DataProviders.class)
 * @TestDataSource(sheet = "Login")                                   // Excel, default workbook
 * public void login(TestData row) { ... }
 *
 * @TestDataSource(file = "testdata/json/login-validation.json", type = LoginTestCase.class)  // JSON -> POJO
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TestDataSource {

    /**
     * File path (project-relative or classpath). Blank = {@code testdata.excel.file} from config.
     * The extension decides the reader: .xlsx/.xls = Excel, .json = JSON.
     */
    String file() default "";

    /** Excel sheet name (ignored for JSON). */
    String sheet() default "";

    /** Row type: {@link TestData} (map-like, default) or any POJO with matching field names. */
    Class<?> type() default TestData.class;
}
