package org.intelehealth.ezazi.automation.listeners;

import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.config.ConfigKeys;
import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.driver.DevicePool;
import org.testng.IAlterSuiteListener;
import org.testng.xml.XmlSuite;

/**
 * Lets parallelism be controlled from the command line instead of editing XML:
 * <pre>
 *   mvn test -Dparallel.threads=2                 # 2 threads (one per device)
 *   mvn test -Dparallel.threads=auto              # one thread per connected device (default)
 *   mvn test -Dparallel.mode=methods -Ddriver.session.scope=method
 * </pre>
 * Suites declared with {@code parallel="tests"} (one {@code <test>} per device, e.g.
 * parallel-devices.xml) are left exactly as written.
 */
public class SuiteConfigListener implements IAlterSuiteListener {

    private static final Logger LOG = LogManager.getLogger(SuiteConfigListener.class);

    @Override
    public void alter(List<XmlSuite> suites) {
        for (XmlSuite suite : suites) {
            if (suite.getParallel() == XmlSuite.ParallelMode.TESTS) {
                LOG.info("Suite '{}' uses parallel=\"tests\"; keeping XML settings", suite.getName());
                continue;
            }
            ConfigReader.getOptional(ConfigKeys.PARALLEL_MODE).ifPresent(mode ->
                    suite.setParallel(XmlSuite.ParallelMode.getValidParallel(mode.toLowerCase(Locale.ROOT))));

            String threads = ConfigReader.get(ConfigKeys.PARALLEL_THREADS, "auto");
            int count = "auto".equalsIgnoreCase(threads) ? Math.max(1, DevicePool.size()) : Integer.parseInt(threads.trim());
            suite.setThreadCount(count);
            suite.setDataProviderThreadCount(count);
            if (count > 1 && (suite.getParallel() == null || suite.getParallel() == XmlSuite.ParallelMode.NONE)) {
                suite.setParallel(XmlSuite.ParallelMode.CLASSES);
            }
            LOG.info("Suite '{}': parallel={} threads={}", suite.getName(), suite.getParallel(), count);
        }
    }
}
