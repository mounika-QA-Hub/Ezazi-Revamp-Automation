package org.intelehealth.ezazi.automation.listeners;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.SkipException;

/**
 * Re-runs a failed test up to {@code retry.max.count} times (default 1).
 * <p>
 * By default only <b>infrastructure</b> failures (timeouts, lost sessions, stale elements...) are
 * retried. Genuine assertion failures are NOT retried ({@code retry.on.assertion.failure=false})
 * because retrying a real bug just hides it and doubles execution time.
 * <p>
 * Applied to every test automatically by {@link RetryTransformer}. Disable with {@code -Dretry.max.count=0}.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOG = LogManager.getLogger(RetryAnalyzer.class);
    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    public boolean retry(ITestResult result) {
        int maxRetries = ConfigReader.getInt(ConfigKeys.RETRY_MAX_COUNT, 1);
        Throwable cause = result.getThrowable();
        if (maxRetries <= 0 || cause instanceof SkipException) {
            return false;
        }
        if (cause instanceof AssertionError && !ConfigReader.getBoolean(ConfigKeys.RETRY_ON_ASSERTION_FAILURE, false)) {
            LOG.info("Not retrying {}: assertion failure (set retry.on.assertion.failure=true to change)", result.getName());
            return false;
        }
        int attempt = attempts.incrementAndGet();
        if (attempt <= maxRetries) {
            LOG.warn("Retrying {} (retry {}/{}) after: {}", result.getName(), attempt, maxRetries,
                    cause == null ? "unknown" : cause.getClass().getSimpleName() + ": " + firstLine(cause.getMessage()));
            return true;
        }
        return false;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int idx = message.indexOf('\n');
        return idx > 0 ? message.substring(0, idx) : message;
    }
}
