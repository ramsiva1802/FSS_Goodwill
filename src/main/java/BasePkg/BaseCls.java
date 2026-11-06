package BasePkg;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.io.FileHandler;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

import PagePkg.B_Dashboard_Cls;
import UtillPkg.ReportText;

/*
 * BaseCls (optimized project)
 *
 * Speed:
 *  - a page is used only when it is completely loaded (the page scripts bind the buttons at the end of the load)
 *  - no implicit wait (it makes every "is it there?" check slow); the page classes use short explicit waits
 *  - the module tests open their screen by its address instead of Home > menu > sub menu for every test
 *
 * Works while Chrome is minimized or hidden:
 *  - the flags below stop Chrome from slowing down or freezing the page of a window that nobody looks at
 *  - run with  -Dheadless=true  (VM argument) to run without any visible window at all
 */
public class BaseCls {

    public static WebDriver driver;
    public String url = UtillPkg.TestEnv.get("fss.url", "http://your-sfa-server:port/");

    // admin login used by every test class after the login tests
    public String admin_user = UtillPkg.TestEnv.get("admin.user", "your-admin-user");
    public String admin_pass = UtillPkg.TestEnv.get("admin.password", "your-admin-password");

    public static ExtentSparkReporter reporter;
    public static ExtentReports extent;
    public static ExtentTest test;

    // builds the browser (also used to start a new one when the old one is gone)
    protected static WebDriver newBrowser() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-background-timer-throttling",
                "--disable-features=CalculateNativeWinOcclusion,IntensiveWakeUpThrottling",
                "--disable-notifications",
                "--disable-infobars",
                "--disable-extensions",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-search-engine-choice-screen",
                "--remote-allow-origins=*");
        boolean headless = Boolean.getBoolean("headless");
        if (headless) {
            options.addArguments("--headless=new", "--window-size=1920,1080");
        }
        WebDriver d = new ChromeDriver(options);
        d.manage().timeouts().implicitlyWait(Duration.ZERO);
        d.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        d.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        if (headless) {
            d.manage().window().setSize(new org.openqa.selenium.Dimension(1920, 1080));
        } else {
            d.manage().window().maximize();
        }
        return d;
    }

    @BeforeTest
    public void SetupBrowser() {

        reporter = new ExtentSparkReporter(".//Report//Goodwill.html");
        reporter.config().setDocumentTitle("Goodwill Automation report");
        reporter.config().setReportName("Goodwill FSS - Functional test");
        reporter.config().setTheme(Theme.DARK);

        extent = new ExtentReports();
        extent.attachReporter(reporter);
        extent.setSystemInfo("hostname", "localhost");
        extent.setSystemInfo("os", "Windows 10");
        extent.setSystemInfo("Tester name", "Ramprakash P L");
        extent.setSystemInfo("Browser name", "Chrome");

        driver = newBrowser();

        driver.get(url);
    }

    // the Login and Dashboard tests click Home and the menu themselves; every other class opens its
    // module by the address it learned on the first visit
    @BeforeClass(alwaysRun = true)
    public void configure_navigation() {
        String name = getClass().getSimpleName();
        B_Dashboard_Cls.FAST_NAV = !(name.startsWith("A_") || name.startsWith("B_"));
        clean_baseline = null;
    }

    // ---------- "is the screen still as it was when the module was opened?" ----------
    // After a module was opened freshly, a snapshot of what the screen shows (address, open pop-ups, the value of
    // every box (shown or hidden), the first row, the number of rows, the "Showing x of y" line, the sort column) is kept.
    // The next test only opens the module again when the screen no longer matches it, so a test that only read
    // data (or opened and closed a pop-up) does not cost a page load for the next test.
    private static String clean_baseline = null;

    private static final String SNAPSHOT_JS = "return (function () {"
            + " function shown(e) { return e.offsetParent !== null; }"
            + " var parts = [location.pathname + location.search];"
            + " parts.push('pop-ups:' + document.querySelectorAll('.modal.show, .swal2-popup').length);"
            + " var boxes = document.querySelectorAll('input:not([type=hidden]), select, textarea');"
            + " for (var i = 0; i < boxes.length; i++) { var b = boxes[i];"
            + "   parts.push((shown(b) ? 'v:' : 'h:') + (b.type || b.tagName) + '=' + ((b.type === 'checkbox' || b.type === 'radio') ? b.checked : b.value)); }"
            + " var rows = Array.prototype.filter.call(document.querySelectorAll('table tbody tr'), shown);"
            + " parts.push('rows:' + rows.length);"
            + " parts.push('first:' + (rows.length ? (rows[0].innerText || '').replace(/\\s+/g, ' ').slice(0, 160) : ''));"
            + " var info = (document.body.innerText.match(/Showing[^\\n]*/) || [''])[0]; parts.push(info);"
            + " var sorted = document.querySelectorAll('th.sorting_asc, th.sorting_desc'); parts.push('sort:' + sorted.length"
            + "   + (sorted.length ? sorted[0].innerText + sorted[0].className : ''));"
            + " return parts.join('|'); })()";

    private String screen_snapshot() {
        return String.valueOf(((org.openqa.selenium.JavascriptExecutor) driver).executeScript(SNAPSHOT_JS));
    }

    // true when the screen still shows exactly what the last fresh opening of the module showed
    protected boolean screen_is_clean() {
        if (!B_Dashboard_Cls.FAST_NAV || clean_baseline == null) {
            return false;
        }
        try {
            return clean_baseline.equals(screen_snapshot());
        } catch (Exception e) {
            return false; // an alert is open, the page is changing ...: open the module again
        }
    }

    // called after a fresh opening of the module
    protected void remember_clean_screen() {
        try {
            clean_baseline = screen_snapshot();
        } catch (Exception e) {
            clean_baseline = null;
        }
    }

    // starts a new browser when the old one was closed or crashed (used by the test classes when they log in again)
    protected void restartBrowser() {
        try {
            driver.quit();
        } catch (Exception e) {
            // already gone
        }
        driver = newBrowser();
        driver.get(url);
    }

    // the test that was reported last (to notice a test method that failed before it created its own report entry)
    private static ExtentTest last_reported;

    // After every test the report gets a plain-language summary: what the test did, what it checked, and for a
    // failure what went wrong, on which step and what the screen showed (see UtillPkg.ReportText).
    @AfterMethod
    public void browserclose(ITestResult result) throws IOException {
        if (test == null || test == last_reported) {
            test = extent.createTest(result.getName());
        }
        last_reported = test;

        if (result.getStatus() == ITestResult.FAILURE) {
            Throwable problem = result.getThrowable();
            try {
                test.log(Status.FAIL, ReportText.headline_for_failure(ReportText.explain(problem), problem));
                test.log(Status.FAIL, MarkupHelper.createCodeBlock(ReportText.failure_block(test, result, driver)));
            } catch (Exception e) {
                test.log(Status.FAIL, "FAILED - " + result.getName() + ": " + problem);
            }

            try {
                String screenshotpath = screenshotMethod(driver, result.getName());
                test.addScreenCaptureFromPath(screenshotpath);
            } catch (Exception e) {
                // the browser is gone: the screenshot is skipped so the remaining tests are not skipped
                test.log(Status.WARNING, "no screenshot (browser not reachable): " + e.getClass().getSimpleName());
            }
        }
        else if (result.getStatus() == ITestResult.SKIP) {
            test.log(Status.SKIP, ReportText.skip_headline(result));
            test.log(Status.SKIP, MarkupHelper.createCodeBlock(ReportText.skip_block(test, result)));
        }
        else if (result.getStatus() == ITestResult.SUCCESS) {
            test.log(Status.PASS, ReportText.pass_headline(test));
            test.log(Status.PASS, MarkupHelper.createCodeBlock(ReportText.pass_block(test, result)));
        }
    }

    protected String screenshotMethod(WebDriver driver, String screenshotname) throws IOException {
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        String destination = ".//Reports//screenshot//" + screenshotname + ".png";
        File dest = new File(destination);
        dest.getParentFile().mkdirs();
        FileHandler.copy(src, dest);
        return dest.getAbsolutePath();
    }

    @AfterTest
    public void teardown() {
        extent.flush();
        driver.quit();
    }
}
