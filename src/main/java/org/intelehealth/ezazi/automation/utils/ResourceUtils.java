package org.intelehealth.ezazi.automation.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.intelehealth.ezazi.automation.config.ConfigReader;
import org.intelehealth.ezazi.automation.exceptions.FrameworkException;

/**
 * Opens files that may live either on the file system (absolute or project-relative path) or on
 * the classpath ({@code src/test/resources}). Lets data files be referenced the same way from
 * IDE runs, Maven runs and CI.
 */
public final class ResourceUtils {

    private ResourceUtils() {
    }

    /**
     * @param location file path or classpath resource, e.g. {@code testdata/excel/ezazi-testdata.xlsx}
     * @return an open stream (caller closes it)
     * @throws FrameworkException if the file is found in neither place
     */
    public static InputStream open(String location) {
        Path path = Paths.get(location);
        Path candidate = path.isAbsolute() ? path : ConfigReader.projectRoot().resolve(path);
        try {
            if (Files.isRegularFile(candidate)) {
                return Files.newInputStream(candidate);
            }
        } catch (IOException e) {
            throw new FrameworkException("Cannot open file " + candidate, e);
        }
        String resource = location.replace('\\', '/').replaceFirst("^/", "");
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new FrameworkException("Resource '" + location + "' not found on disk (" + candidate
                    + ") or on the classpath (src/test/resources/" + resource + ")");
        }
        return in;
    }
}
