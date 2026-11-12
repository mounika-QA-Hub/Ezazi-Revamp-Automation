package org.intelehealth.ezazi.automation.utils;

import java.time.Duration;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intelehealth.ezazi.automation.driver.DriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Step;

/**
 * Touch gestures built on W3C Actions (the deprecated {@code TouchAction} is not used).
 * <p>
 * Directions: {@link #swipe(Direction)} describes where the FINGER moves; {@link #scroll(Direction)}
 * describes where the CONTENT goes (scroll DOWN = reveal content further down = finger moves up).
 */
public final class GestureUtils {

    private static final Logger LOG = LogManager.getLogger(GestureUtils.class);
    private static final Duration TAP_DURATION = Duration.ofMillis(80);
    private static final Duration SWIPE_DURATION = Duration.ofMillis(400);
    private static final int DEFAULT_MAX_SCROLLS = 8;

    /** Gesture direction. */
    public enum Direction { UP, DOWN, LEFT, RIGHT }

    private GestureUtils() {
    }

    private static AndroidDriver driver() {
        return DriverManager.getDriver();
    }

    private static PointerInput finger() {
        return new PointerInput(PointerInput.Kind.TOUCH, "finger");
    }

    /**
     * Taps a screen coordinate.
     *
     * @param point absolute screen point
     */
    public static void tap(Point point) {
        PointerInput finger = finger();
        Sequence tap = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), point.getX(), point.getY()))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, TAP_DURATION))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver().perform(List.of(tap));
    }

    /** Taps the centre of an element. */
    public static void tap(WebElement element) {
        tap(center(element));
    }

    /** Double-taps the centre of an element. */
    public static void doubleTap(WebElement element) {
        Point p = center(element);
        PointerInput finger = finger();
        Sequence seq = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), p.getX(), p.getY()))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, TAP_DURATION))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, Duration.ofMillis(100)))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, TAP_DURATION))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver().perform(List.of(seq));
    }

    /**
     * Long-presses an element.
     *
     * @param element  target
     * @param duration hold time (Android long-press threshold is ~500 ms)
     */
    @Step("Long press for {duration}")
    public static void longPress(WebElement element, Duration duration) {
        Point p = center(element);
        PointerInput finger = finger();
        Sequence seq = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), p.getX(), p.getY()))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, duration))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver().perform(List.of(seq));
    }

    /**
     * Swipes between two points.
     *
     * @param from     start point
     * @param to       end point
     * @param duration finger travel time (shorter = fling)
     */
    public static void swipe(Point from, Point to, Duration duration) {
        PointerInput finger = finger();
        Sequence seq = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), from.getX(), from.getY()))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, Duration.ofMillis(50)))
                .addAction(finger.createPointerMove(duration, PointerInput.Origin.viewport(), to.getX(), to.getY()))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver().perform(List.of(seq));
    }

    /** Swipes the finger across the middle 60% of the screen in the given direction. */
    @Step("Swipe {direction}")
    public static void swipe(Direction direction) {
        Dimension size = driver().manage().window().getSize();
        swipeWithin(new Rectangle(0, 0, size.getHeight(), size.getWidth()), direction, 0.6);
    }

    /**
     * Swipes inside an element (e.g. a carousel or a list) instead of the whole screen.
     *
     * @param element   container
     * @param direction finger direction
     * @param distance  fraction of the container (0.1 - 0.9)
     */
    public static void swipeWithin(WebElement element, Direction direction, double distance) {
        swipeWithin(element.getRect(), direction, distance);
    }

    private static void swipeWithin(Rectangle area, Direction direction, double distance) {
        int centerX = area.getX() + area.getWidth() / 2;
        int centerY = area.getY() + area.getHeight() / 2;
        int dx = (int) (area.getWidth() * distance / 2);
        int dy = (int) (area.getHeight() * distance / 2);
        Point from;
        Point to;
        switch (direction) {
            case UP -> { from = new Point(centerX, centerY + dy); to = new Point(centerX, centerY - dy); }
            case DOWN -> { from = new Point(centerX, centerY - dy); to = new Point(centerX, centerY + dy); }
            case LEFT -> { from = new Point(centerX + dx, centerY); to = new Point(centerX - dx, centerY); }
            case RIGHT -> { from = new Point(centerX - dx, centerY); to = new Point(centerX + dx, centerY); }
            default -> throw new IllegalArgumentException("Unsupported direction " + direction);
        }
        swipe(from, to, SWIPE_DURATION);
    }

    /**
     * Scrolls the content once in the given direction.
     *
     * @param contentDirection DOWN reveals content below, UP reveals content above
     */
    public static void scroll(Direction contentDirection) {
        swipe(switch (contentDirection) {
            case DOWN -> Direction.UP;
            case UP -> Direction.DOWN;
            case LEFT -> Direction.RIGHT;
            case RIGHT -> Direction.LEFT;
        });
    }

    /**
     * Scrolls until the element is visible.
     *
     * @param locator          element to find
     * @param contentDirection scroll direction
     * @param maxScrolls       safety limit
     * @return the visible element
     * @throws NoSuchElementException if it is not found after {@code maxScrolls}
     */
    @Step("Scroll to element {locator}")
    public static WebElement scrollToElement(By locator, Direction contentDirection, int maxScrolls) {
        for (int i = 0; i <= maxScrolls; i++) {
            if (WaitUtils.isVisibleNow(locator)) {
                return driver().findElement(locator);
            }
            if (i < maxScrolls) {
                scroll(contentDirection);
            }
        }
        throw new NoSuchElementException("Element " + locator + " not found after " + maxScrolls + " scrolls " + contentDirection);
    }

    /** Scrolls DOWN (up to 8 times) until the element is visible. */
    public static WebElement scrollToElement(By locator) {
        return scrollToElement(locator, Direction.DOWN, DEFAULT_MAX_SCROLLS);
    }

    /**
     * Fastest way to scroll to visible text: delegates to Android's native UiScrollable (one
     * server round-trip instead of many swipes). Works for the first scrollable container.
     *
     * @param text exact visible text
     * @return the element
     */
    @Step("Scroll to text \"{text}\"")
    public static WebElement scrollToText(String text) {
        String selector = "new UiScrollable(new UiSelector().scrollable(true)).setMaxSearchSwipes(15)"
                + ".scrollIntoView(new UiSelector().text(\"" + text.replace("\"", "\\\"") + "\"))";
        LOG.debug("UiScrollable search for '{}'", text);
        return driver().findElement(AppiumBy.androidUIAutomator(selector));
    }

    /**
     * Drags one element onto another.
     *
     * @param source element to drag
     * @param target drop target
     */
    @Step("Drag and drop")
    public static void dragAndDrop(WebElement source, WebElement target) {
        Point from = center(source);
        Point to = center(target);
        PointerInput finger = finger();
        Sequence seq = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), from.getX(), from.getY()))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, Duration.ofMillis(600)))
                .addAction(finger.createPointerMove(Duration.ofMillis(700), PointerInput.Origin.viewport(), to.getX(), to.getY()))
                .addAction(new Pause(finger, Duration.ofMillis(200)))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver().perform(List.of(seq));
    }

    private static Point center(WebElement element) {
        Rectangle rect = element.getRect();
        return new Point(rect.getX() + rect.getWidth() / 2, rect.getY() + rect.getHeight() / 2);
    }
}
