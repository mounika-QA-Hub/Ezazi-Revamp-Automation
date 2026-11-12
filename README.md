# eZAZI Mobile Automation

Appium + TestNG + Allure framework for the **eZAZI** Android app (healthcare workers, phones and
tablets such as the Samsung Galaxy Tab A11 SM-X135G, Android 15).

* **Page Object Model** with a `BasePage` that waits, logs and reports every action
* **Parallel-safe**: `ThreadLocal` drivers, a device pool, unique UiAutomator2 ports per device
* **Zero sleeps**: only explicit/fluent waits (`WaitUtils`)
* **Data-driven** from Excel or JSON with `@TestDataSource`. Rows with `Run = N` are skipped, and relative dates (`TODAY-1 23:30`) mean the data never goes out of date
* **Allure**: steps, screenshot + page source + logcat on failure, environment info, failure categories
* **Self-healing runs**: retries infrastructure failures (never genuine assertion failures), recreates dead sessions, and cleanup always runs

---

## 1. Quick start (5 minutes)

```powershell
# 1. Prerequisites (one-time)
#    JDK 17+, Android SDK with ANDROID_HOME set, Node 20+, then:
npm install -g appium
appium driver install uiautomator2

# 2. Credentials (only for tests in the "requires-backend" group)
copy .env.example .env        # then edit .env - it is git-ignored

# 3. Put the APK in apps/  (default name: apps/ezazi-variantA.apk)

# 4. Start an emulator or plug in a device, then run:
mvnw clean test                       # smoke suite (default)
mvnw allure:serve                     # open the report
```

No Maven installation is needed: `mvnw` / `mvnw.cmd` (Maven Wrapper) downloads Maven 3.9.15 on first use.
**Eclipse:** *File > Import > Existing Maven Projects*, then right-click any test class and choose *Run As > TestNG Test*. Listeners
are registered automatically through `META-INF/services`, so IDE runs behave exactly like Maven runs.

---

## 2. Project structure

```
ezazi-mobile-automation/
├── pom.xml                              all versions pinned in <properties>
├── mvnw, mvnw.cmd, .mvn/                Maven Wrapper
├── .env.example                         copy to .env (git-ignored) for credentials
├── local.properties.example             copy to local.properties (git-ignored) for machine overrides
├── Jenkinsfile                          Jenkins pipeline
├── .github/workflows/android-ui-tests.yml   GitHub Actions (emulator + Allure)
├── apps/                                APKs under test (git-ignored binaries)
└── src
    ├── main/java/org/intelehealth/ezazi/automation
    │   ├── config/        ConfigReader (layered config + secrets), ConfigKeys (all key names)
    │   ├── driver/        DriverManager (ThreadLocal), DriverFactory, UiAutomator2OptionsFactory,
    │   │                  AppiumServerManager, EmulatorManager, DevicePool, DeviceConfig, SessionScope
    │   ├── pages/         BasePage, LoginPage, HomePage, AdmitPatientPage
    │   │   └── components/NativeDateTimePicker   reusable Android date/time picker driver
    │   ├── data/          TestData (row), TestDataSource (annotation), TestDataLoader, TestCaseInfo
    │   │   └── model/     LoginTestCase (JSON -> POJO example)
    │   ├── listeners/     AllureListener, TestExecutionListener, RetryAnalyzer, RetryTransformer,
    │   │                  SuiteConfigListener (threads from the command line)
    │   ├── reports/       AllureManager, AllureAttachments, ExecutionEnvironment
    │   ├── utils/         WaitUtils, GestureUtils, PermissionHandler, DeviceUtils, AdbUtils,
    │   │                  ScreenshotUtils, DateTimeUtils, RandomDataUtils, ExcelUtils, JsonUtils, ResourceUtils
    │   └── exceptions/    FrameworkException, ConfigurationException
    └── test
        ├── java/org/intelehealth/ezazi/automation
        │   ├── base/BaseTest.java                 the whole session life cycle
        │   ├── dataproviders/DataProviders.java   generic Excel/JSON providers (+ parallel)
        │   └── tests/
        │       ├── login/LoginTest.java           UI, Excel-driven, JSON-driven, password toggle
        │       ├── patient/AdmitPatientFormTest.java   date/time + boundary form test
        │       └── framework/FrameworkSelfTest.java    device-free checks (CI stage 1)
        └── resources
            ├── config/base.properties             framework defaults
            ├── config/env/{dev,qa,uat}.properties
            ├── config/variants/{variantA,variantB}.properties
            ├── suites/{smoke,regression,testng,framework,parallel-devices}.xml
            ├── testdata/excel/ezazi-testdata.xlsx  sheets: Login, AdmitPatient, HowTo
            ├── testdata/json/login-validation.json
            ├── allure/categories.json, allure.properties, log4j2.xml
            └── META-INF/services/org.testng.ITestNGListener   auto-registers listeners
```

---

## 3. Dependency versions

| Component | Version | Notes |
|---|---|---|
| Java | 17 (release target) | runs on 17+; verified on JDK 25 |
| Maven (wrapper) | 3.9.15 | |
| Appium java-client | 9.5.0 | latest 9.x, as required |
| Selenium (BOM) | 4.34.0 | pinned to the release java-client 9.5.0 was built against |
| TestNG | 7.12.0 | |
| Allure (testng + BOM) | 2.35.5 | Allure CLI for `allure:report` is 2.35.1 |
| AspectJ weaver | 1.9.25.1 | supports JDK 25 |
| Log4j2 (+ slf4j2 bridge) | 2.26.1 | |
| Apache POI | 5.5.1 | |
| Jackson (BOM) | 2.22.3 | |
| maven-surefire-plugin | 3.5.5 | |
| maven-compiler-plugin | 3.15.0 | |
| allure-maven | 2.17.0 | |
| Appium server / UiAutomator2 driver | 3.9.0 / 8.7.3 | versions on the dev machine and pinned in CI |

---

## 4. Running tests

| Goal | Command |
|---|---|
| Smoke (default) | `mvnw clean test` |
| Smoke + regression without backend | `mvnw clean test -Dsuite=testng` |
| Full regression (needs backend + `.env`) | `mvnw clean test -Dsuite=regression` |
| Framework self-test (no device) | `mvnw clean test -Dsuite=framework` |
| Other environment / variant | `mvnw clean test -Denv=uat -Dvariant=variantB` |
| Specific device | `mvnw clean test -Ddevice.udids=R58N12ABCDE` |
| Different APK | `mvnw clean test -Dapp.path=D:/builds/ezazi-0.2.0.apk` |
| Boot the AVD automatically | `mvnw clean test -Demulator.start=true -Demulator.avd=MounikaTab` |
| Headless emulator (CI) | add `-Demulator.headless=true` |
| One class / one method | `mvnw test -Dtest=LoginTest` or `-Dtest=LoginTest#passwordIsMaskedAndCanBeRevealed` *(or Run As > TestNG in Eclipse)* |
| No retries | `-Dretry.max.count=0` |
| Use an Appium server you started yourself | `-Dappium.server.start=false -Dappium.server.url=http://127.0.0.1:4723/` |

Any configuration key can be overridden with `-Dkey=value` or an environment variable (`APPIUM_PORT=4725`).

### Parallel execution

* **Same tests spread over N devices**: connect N devices/emulators. `device.udids=auto` and
  `parallel.threads=auto` give one thread per device automatically. Force it with `-Dparallel.threads=2`.
* **Each device runs the full suite** (phone vs tablet): `mvnw clean test -Dsuite=parallel-devices`.
  Edit the `udid` / `systemPort` parameters in `parallel-devices.xml`.
* **Parallel data rows**: use `dataProvider = DataProviders.TEST_DATA_PARALLEL`. BaseTest automatically
  switches that class to one session per method, so worker threads don't wait on a device held by the class session.
* **parallel="methods"**: add `-Dparallel.mode=methods`. BaseTest switches to `driver.session.scope=method` automatically.

Every parallel session needs its **own device** and **its own `systemPort`**. The device pool takes care of both.

### Reports

```powershell
allure serve target/allure-results                              # Allure CLI installed
allure generate target/allure-results -o target/allure-report --clean
mvnw allure:serve                                               # no CLI needed (plugin downloads it)
mvnw allure:report                                              # -> target/allure-report/index.html
```
Set `-Dallure.report.generate=true` to generate the HTML automatically at the end of the run (needs the `allure` CLI on PATH).
Logs: `target/logs/automation.log` (framework, every line has thread + test name) and `target/logs/appium-server.log`.

---

## 5. Configuration

Resolution order, **highest priority first**:

1. `-Dkey=value` (command line)
2. Environment variable: `appium.port` maps to `APPIUM_PORT`
3. `local.properties` (project root, git-ignored)
4. `config/variants/<variant>.properties`: package, activity, APK
5. `config/env/<env>.properties`: location, timeouts and retries per environment
6. `config/base.properties`: documented defaults for every key

**Secrets** are never read from property files. `ConfigReader.getSecret("EZAZI_PASSWORD")` looks at
system property, then environment variable, then `.env`. In data files write `${EZAZI_PASSWORD}`.
A missing key fails immediately with a message telling you which file to edit.

---

## 6. Writing tests

### A new page
```java
public class PartographPage extends BasePage {
    private static final By SAVE = AppiumBy.accessibilityId("save-partograph");   // locators stay here

    @Override protected By pageAnchor() { return SAVE; }

    @Step("Save partograph")
    public PartographPage save() { tap(SAVE, "Save"); return this; }
}
```
Locator priority: **accessibilityId > resourceId > UiAutomator > xpath** (xpath only as a last resort).
Pages never assert. They answer questions (`isErrorShown()`) and the tests assert.

### A new data-driven test (no provider code)
1. Add a sheet (e.g. `Partograph`) to `testdata/excel/ezazi-testdata.xlsx` with `TestCaseID`, `Description`, your input
   columns, `ExpectedResult` and `Run`.
2. Write the test:
```java
@Test(groups = "regression", dataProvider = DataProviders.TEST_DATA, dataProviderClass = DataProviders.class)
@TestDataSource(sheet = "Partograph")
public void partographValidation(TestData row) {
    new PartographPage().enterCervix(row.get("Cervix")).save();
    ...
}
```
JSON works the same way: `@TestDataSource(file = "testdata/json/x.json", type = MyPojo.class)`.
In Allure each row appears as `TC_ID - Description`.

### Assertions
* Hard (`Assert.assertTrue`) for blocking steps where continuing makes no sense.
* Soft (`SoftAssert` + `assertAll()`) when checking several fields of one screen, so every mismatch is reported in one run.
* `priority` / `dependsOnMethods` only when there's no alternative. Tests must be independent: the app is
  relaunched between tests (`app.reset.strategy=relaunch`).

### Date and time tokens (Excel/JSON)
`TODAY`, `TODAY+1`, `TODAY-30`, `YESTERDAY`, `TOMORROW`, `NOW`, `NOW-10m`, `NOW-3h`, `TODAY-1 23:30`, `2026-01-31 08:15`.
For device-clock tests use `DeviceUtils.setDateTime(...)` / `setTimeZone(...)` and restore with
`DeviceUtils.restoreAutomaticDateTime()` in an `@AfterMethod(alwaysRun = true)`.

---

## 7. Set-up and teardown

| Level | What happens | Why |
|---|---|---|
| `@BeforeSuite` | load and log config, clean `allure-results`, boot the emulator (optional), start Appium on a free port | one shared server for all threads; a free port avoids clashes |
| `@BeforeClass` | create the session (`driver.session.scope=class`, the default) | about 5 s session start, paid once per class |
| `@BeforeMethod` | create the session if this thread has none (method scope or parallel rows); recreate it if dead | self-healing after crashes |
| *(listener)* | on failure: screenshot, page source and logcat attached to Allure | captured before cleanup, while the failing screen is still showing |
| `@AfterMethod` | relaunch the app (class scope) or quit the session (method scope) | test independence; reset errors are logged, so later tests don't get skipped |
| `@AfterClass` | quit the session, release the device, clear the ThreadLocals | no leaked sessions or devices |
| `@AfterSuite` | write the Allure environment and categories, stop Appium and the emulator, optionally generate the report | |

Every config method has `alwaysRun = true`. Appium also has a JVM shutdown hook, so even an aborted run stops the server.

---

## 8. Design decisions (short)

* **ConfigReader**: layered and immutable, so it's thread-safe. Constants in `ConfigKeys` turn typos into compile errors. Secrets live only in env/.env.
* **DriverManager + DriverFactory**: `ThreadLocal` isolates parallel sessions. The factory is the only place that knows about capabilities, and session creation is retried once because UiAutomator2 install hiccups are the most common flake.
* **DevicePool**: a `BlockingQueue` of devices, each with a unique `systemPort`. Threads borrow a device and return it, so you scale by plugging in devices without editing XML.
* **AppiumServerManager**: uses a free port, writes logs to a file (clean console), and has a shutdown hook. Set `appium.server.start=false` to use a grid or device cloud.
* **BasePage / WaitUtils**: every action waits (250 ms polling), logs and appears as an Allure `@Step`. Implicit wait is 0, so explicit waits are predictable. Fields are filled without tapping first (see Troubleshooting: keyboard).
* **PermissionHandler**: one UiAutomator regex finds any "Allow" variant in a single round trip and clicks only when a dialog really exists. Prefers "While using the app".
* **RetryAnalyzer + RetryTransformer**: applied to every test automatically, but only infrastructure errors are retried. A real assertion failure is a bug and should show up straight away.
* **AllureListener**: attaches in `afterInvocation`, which runs before `@AfterMethod` resets the app and before Allure closes the result. `onTestFailure` can run too late and the attachments get lost.
* **TestData / @TestDataSource**: one generic DataProvider for every sheet. Missing columns fail fast, and `${SECRET}` placeholders keep credentials out of Excel.
* **Speed**: animations disabled, `noReset` (no re-install), session per class, instant checks for elements that must be *absent*, cached Excel parsing, adb on the host instead of Appium round trips.

---

## 9. Locator contract (for the app team)

eZAZI is React Native. Adding `accessibilityLabel` (exposed as content-desc / accessibility id) or `testID` gives stable,
fast locators. Current gaps found in build 0.1.0:

| Screen | Element | Today | Please add |
|---|---|---|---|
| Login | Username input | `EditText` instance 1 | `accessibilityLabel="username-input"` |
| Login | Password input | `EditText` instance 2 | `accessibilityLabel="password-input"` |
| Home | Admit patient button | not reachable (needs backend) | `accessibilityLabel="Admit Patient"` |
| Admit patient | all inputs | not reachable | labels as used in `AdmitPatientPage` |

---

## 10. Troubleshooting checklist

| Symptom | Check / fix |
|---|---|
| **SessionNotCreatedException** | `adb devices` shows the device as `device` (not `offline` / `unauthorized`). `appium driver list --installed` includes uiautomator2. The APK path exists. Read `target/logs/appium-server.log` (the real reason is near the end). |
| **UiAutomator2 server install/launch timeout** | `adb uninstall io.appium.uiautomator2.server` and `adb uninstall io.appium.uiautomator2.server.test`, then rerun. On slow emulators raise `timeout.app.launch.seconds`. Make sure no other session is using the same `systemPort`. |
| **ANDROID_HOME not set** | `setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"` (Windows) and add `%ANDROID_HOME%\platform-tools` to PATH. Restart the terminal or IDE. |
| **adb offline / unauthorized** | `adb kill-server && adb start-server`. Accept the RSA prompt on the device. Cold-boot the AVD (*Device Manager > Cold Boot Now*). Try another USB cable or port. |
| **Appium server fails to start** | `appium -v` works? On Windows, auto-detection can fail, so set `appium.js.path` / `appium.node.path` in `local.properties`. |
| **StaleElementReferenceException** | Don't keep `WebElement`s in fields. Use `By` locators (as the pages do) and call page methods again after navigation. `WaitUtils` already ignores stale elements while polling. |
| **Tap does nothing after typing** | The soft keyboard covered the button. `BasePage.type()` doesn't tap fields, and `LoginPage.tapSetup()` hides the keyboard first. Do the same in new pages. |
| **Element not found but visible** | Open the failing test's *Page source* attachment in Allure and check the actual `content-desc` / `text`. Use Appium Inspector against the same server. |
| **Allure report empty or no steps** | Results are in `target/allure-results` (not `allure-results`). Run through Maven so the AspectJ agent is active (`@Step` needs it). In Eclipse, add `-javaagent:<m2>/org/aspectj/aspectjweaver/1.9.25.1/aspectjweaver-1.9.25.1.jar` to the run configuration's VM args. |
| **`allure` command not found** | Use `mvnw allure:serve` (downloads the CLI), or install the CLI (`scoop install allure` / `npm i -g allure-commandline`). |
| **Permission dialogs block the test** | `app.auto.grant.permissions=true` (default). `PermissionHandler.acceptAllIfPresent()` covers anything that's left. |
| **"Unable to fetch locations"** | The backend isn't reachable from the device. Tests in the `requires-backend` group need network access to the eZAZI server. |
| **Port already in use** | Keep `appium.port=auto`. For parallel runs give each device its own `systemPort`. |
| **Tests wait for a device for a long time** | You have more threads than devices. Lower `-Dparallel.threads` or connect more devices. |

---

## 11. Assumptions

* **Variant B** package and APK name are placeholders (`variantB.properties`). Replace them with the real flavour.
* The **Home** and **Admit patient** screens are behind a working backend login, so their locators and validation messages
  (age 12–60, "Admission date cannot be in the future", and so on) are assumptions to confirm with the product team. The tests and data
  structure stay the same; only page constants and Excel expectations change.
* Issue links point to `https://intelehealth.atlassian.net/browse/{id}` (`allure.properties`). Change this to your tracker.
* `TC_LOGIN_005` (valid login) is `Run = N` until the QA backend is reachable from the device.
