package org.intelehealth.ezazi.automation.utils;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight random test-data generator (no extra dependency, thread-safe via
 * {@link ThreadLocalRandom}). Use for data that must be unique per run, e.g. patient names.
 */
public final class RandomDataUtils {

    private static final List<String> FIRST_NAMES = List.of(
            "Asha", "Priya", "Lakshmi", "Fatima", "Amina", "Grace", "Neha", "Sunita", "Zainab", "Mary",
            "Kavya", "Halima", "Joy", "Divya", "Esther", "Anjali", "Rukhsar", "Pooja", "Mercy", "Sarita");
    private static final List<String> LAST_NAMES = List.of(
            "Sharma", "Reddy", "Khan", "Patel", "Okafor", "Mwangi", "Iyer", "Das", "Achieng", "Naidu",
            "Banerjee", "Otieno", "Singh", "Begum", "Mensah", "Rao", "Kumari", "Wanjiru", "Joshi", "Bello");
    private static final String ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100_000);

    private RandomDataUtils() {
    }

    private static ThreadLocalRandom random() {
        return ThreadLocalRandom.current();
    }

    /** @return a realistic first name */
    public static String firstName() {
        return pickOne(FIRST_NAMES);
    }

    /** @return a realistic last name */
    public static String lastName() {
        return pickOne(LAST_NAMES);
    }

    /** @return "First Last" */
    public static String fullName() {
        return firstName() + " " + lastName();
    }

    /**
     * @param min inclusive
     * @param max inclusive
     * @return random integer in range
     */
    public static int numberBetween(int min, int max) {
        return random().nextInt(min, max + 1);
    }

    /** @return 10-digit mobile number starting with 6-9 */
    public static String mobileNumber() {
        StringBuilder sb = new StringBuilder().append(numberBetween(6, 9));
        for (int i = 0; i < 9; i++) {
            sb.append(random().nextInt(10));
        }
        return sb.toString();
    }

    /**
     * @param length number of characters
     * @return random alphanumeric string without look-alike characters (0/O, 1/l)
     */
    public static String alphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(random().nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Unique id, safe across parallel threads within one run.
     *
     * @param prefix e.g. "PAT"
     * @return e.g. PAT-48213-7Kq
     */
    public static String uniqueId(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet() + "-" + alphanumeric(3);
    }

    /**
     * @param maxDaysBack upper bound
     * @return a date between today-maxDaysBack and yesterday
     */
    public static LocalDate pastDate(int maxDaysBack) {
        return LocalDate.now().minusDays(numberBetween(1, Math.max(1, maxDaysBack)));
    }

    /**
     * @param values candidates
     * @param <T>    type
     * @return one random element
     */
    public static <T> T pickOne(List<T> values) {
        return values.get(random().nextInt(values.size()));
    }
}
