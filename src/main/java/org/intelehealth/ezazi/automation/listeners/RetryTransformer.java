package org.intelehealth.ezazi.automation.listeners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.testng.IAnnotationTransformer;
import org.testng.IRetryAnalyzer;
import org.testng.annotations.ITestAnnotation;
import org.testng.internal.annotations.DisabledRetryAnalyzer;

/**
 * Attaches {@link RetryAnalyzer} to every {@code @Test} that does not declare its own
 * {@code retryAnalyzer}, so nobody has to remember to add it.
 * <p>
 * Registered through {@code META-INF/services/org.testng.ITestNGListener}, so it is active in
 * Maven, IDE and CI runs alike without listing it in each suite XML.
 */
public class RetryTransformer implements IAnnotationTransformer {

    @Override
    @SuppressWarnings("rawtypes")
    public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
        Class<? extends IRetryAnalyzer> current = annotation.getRetryAnalyzerClass();
        if (current == null || current == DisabledRetryAnalyzer.class) {
            annotation.setRetryAnalyzer(RetryAnalyzer.class);
        }
    }
}
