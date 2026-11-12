package org.intelehealth.ezazi.automation.listeners;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.data.TestCaseInfo;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Logging and tracking: a clear START / PASS / FAIL / SKIP / RETRY line per test (with duration),
 * the test name in every log line of that thread (via Log4j {@link ThreadContext}), and a summary
 * at the end of the suite.
 */
public class TestExecutionListener implements ITestListener, ISuiteListener {

    private static final Logger LOG = LogManager.getLogger(TestExecutionListener.class);
    private static final AtomicInteger PASSED = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();
    private static final AtomicInteger SKIPPED = new AtomicInteger();
    private static final AtomicInteger RETRIED = new AtomicInteger();
    private long suiteStart;

    @Override
    public void onStart(ISuite suite) {
        suiteStart = System.currentTimeMillis();
        LOG.info("==================================================================");
        LOG.info(" Suite '{}' | env={} | variant={}", suite.getName(), ConfigReader.env(), ConfigReader.variant());
        LOG.info("==================================================================");
    }

    @Override
    public void onTestStart(ITestResult result) {
        ThreadContext.put("test", displayName(result));
        LOG.info("START  {}", displayName(result));
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        PASSED.incrementAndGet();
        LOG.info("PASS   {} ({} ms)", displayName(result), duration(result));
        ThreadContext.remove("test");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        FAILED.incrementAndGet();
        LOG.error("FAIL   {} ({} ms) -> {}", displayName(result), duration(result), describe(result.getThrowable()));
        ThreadContext.remove("test");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        if (result.wasRetried()) {
            RETRIED.incrementAndGet();
            LOG.warn("RETRY  {} ({} ms) -> {}", displayName(result), duration(result), describe(result.getThrowable()));
        } else {
            SKIPPED.incrementAndGet();
            LOG.warn("SKIP   {} -> {}", displayName(result), describe(result.getThrowable()));
        }
        ThreadContext.remove("test");
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        onTestFailure(result);
    }

    @Override
    public void onFinish(ISuite suite) {
        long seconds = (System.currentTimeMillis() - suiteStart) / 1000;
        LOG.info("==================================================================");
        LOG.info(" Suite '{}' finished in {}m {}s", suite.getName(), seconds / 60, seconds % 60);
        LOG.info(" PASSED: {} | FAILED: {} | SKIPPED: {} | RETRIED: {}", PASSED.get(), FAILED.get(), SKIPPED.get(), RETRIED.get());
        LOG.info(" Report: allure serve target/allure-results   |   Logs: target/logs/");
        LOG.info("==================================================================");
    }

    private static String displayName(ITestResult result) {
        Object[] params = result.getParameters();
        String base = result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName();
        if (params != null && params.length > 0 && params[0] instanceof TestCaseInfo info) {
            return base + " [" + info.testCaseId() + "]";
        }
        return base;
    }

    private static long duration(ITestResult result) {
        return result.getEndMillis() - result.getStartMillis();
    }

    private static String describe(Throwable t) {
        if (t == null) {
            return "no exception";
        }
        String message = String.valueOf(t.getMessage());
        int idx = message.indexOf('\n');
        return t.getClass().getSimpleName() + ": " + (idx > 0 ? message.substring(0, idx) : message);
    }
}
