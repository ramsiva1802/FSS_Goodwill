package TestPkg;

import UtillPkg.WaitUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;

/*
 * NOTES - B_Dashboard_Test
 * ----------------------
 * Covers: dashboard elements, Home click, company-name refresh,
 * every module and sub module opening its page, profile menu and logout.
 * The dashboard URL cannot be opened directly (server error), so:
 *   @BeforeClass  -> login once through the login form, remember the dashboard URL
 *   @BeforeMethod -> return to the dashboard (Home click, back, or log in again)
 * A broken page (for example "Page not found") is recorded as a failure with a screenshot,
 * the browser steps back, and the run carries on with the next sub module / test.
 */
public class B_Dashboard_Test extends BaseCls {

	// the URL the site lands on after login (read from the browser, not typed)
	String dash_url;

	// runs ONCE before the tests: fresh session, then login through the form
	@BeforeClass
	public void login_first() {
		driver.get(url);   // open a normal page first (storage cannot be touched on an error page)
		driver.manage().deleteAllCookies();
		try {
			((JavascriptExecutor) driver).executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
		} catch (Exception e) {
			System.out.println("Could not clear browser storage: " + e.getMessage());
		}
		driver.get(url);   // now the login page is shown

		A_Login_Cls login = new A_Login_Cls(driver);
		login.Login(admin_user, admin_pass);
		Assert.assertTrue(login.login_success(), "Could not log in for dashboard tests");

		dash_url = driver.getCurrentUrl();
	}

	// runs before EVERY test: make sure we are on the dashboard,
	// even if the previous test failed and left the browser on some other page
	@BeforeMethod
	public void back_to_dashboard() {
		try {
			if (!driver.getCurrentUrl().equals(dash_url)) {
				go_dashboard();
			}
			// still not on the dashboard (error page, broken session) -> start over
			if (!driver.getCurrentUrl().equals(dash_url)) {
				login_first();
			}
		} catch (Exception e) {
			login_first();   // page crashed or Home link missing -> log in again from scratch
		}
	}

	// Returns to the dashboard using the Home link.
	// If the page has no Home link (error page), step back first; if that fails, log in again.
	private void go_dashboard() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		if (!dash.is_home_available()) {
			driver.navigate().back();   // error page or no menu -> step back first
		}
		try {
			dash.Click_home();
			WaitUtil.settle(driver, 1500);
		} catch (TimeoutException e) {
			System.out.println("Home link missing on: " + driver.getCurrentUrl());
			if (test != null) {
				test.warning("Home link missing on: " + driver.getCurrentUrl());
			}
			driver.navigate().back();
			if (!driver.getCurrentUrl().equals(dash_url)) {
				login_first();   // going back did not help -> log in again
			}
		}
	}

	// Supplies the module names to the test below, one run per module
	@DataProvider(name = "modules")
	public Object[][] modules() {
		Object[][] data = new Object[B_Dashboard_Cls.MODULES.length][1];
		for (int i = 0; i < B_Dashboard_Cls.MODULES.length; i++) {
			data[i][0] = B_Dashboard_Cls.MODULES[i];
		}
		return data;
	}

	// A page opened from the menu is OK if the URL changed, the title is not an error,
	// and the page text does not say "page not found" / 404 / server error
	private boolean page_opened() throws InterruptedException {
		WaitUtil.settle(driver, 1500);
		String title = driver.getTitle().toLowerCase();
		String body = driver.findElement(By.tagName("body")).getText().toLowerCase();

		boolean error_page = title.contains("error") || title.contains("404") || title.contains("not found")
				|| body.contains("page not found") || body.contains("404") || body.contains("server error");

		return !driver.getCurrentUrl().equals(dash_url) && !error_page;
	}

	// TEST 1: dashboard elements are displayed
	@Test(priority = 1)
	public void verify_dashboard() {

		test = extent.createTest("Dashboard Elements Verification");

		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		Assert.assertTrue(dash.verify_company().isDisplayed(), "Company name not displayed");
		test.pass("Company name displayed");

		Assert.assertTrue(dash.verify_home().isDisplayed(), "Home not displayed");
		test.pass("Home displayed");

		for (String module : B_Dashboard_Cls.MODULES) {
			Assert.assertTrue(dash.verify_module(module).isDisplayed(), module + " not displayed");
			test.pass(module + " displayed");
		}

		Assert.assertTrue(dash.verify_profile().isDisplayed(), "Profile not displayed");
		test.pass("Profile (Hi, AppAdmin) displayed");

		Assert.assertTrue(dash.verify_sales_trend().isDisplayed(), "Monthly Sales Trend not displayed");
		test.pass("Monthly Sales Trend displayed");

		Assert.assertTrue(dash.verify_activity().isDisplayed(), "Today's Activity not displayed");
		test.pass("Today's Activity displayed");

		Assert.assertTrue(dash.verify_volume().isDisplayed(), "Monthly Sales Volume not displayed");
		test.pass("Monthly Sales Volume displayed");

		Assert.assertTrue(dash.verify_distribution().isDisplayed(), "Monthly Distribution not displayed");
		test.pass("Monthly Distribution displayed");
	}

	// TEST 2: Home click
	@Test(priority = 2)
	public void click_home() throws InterruptedException {

		test = extent.createTest("Home Click Test");

		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		dash.Click_home();
		WaitUtil.settle(driver, 1500);
		Assert.assertEquals(driver.getCurrentUrl(), dash_url, "Home did not open the dashboard");
		test.pass("Home opened the dashboard: " + driver.getCurrentUrl());
	}

	// TEST 3: clicking the company name refreshes the page
	@Test(priority = 3)
	public void company_name_refresh() {

		test = extent.createTest("Company Name Click Refreshes Page");

		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		dash.set_marker();      // mark the current page
		dash.Click_company();   // click the company name
		Assert.assertTrue(dash.page_refreshed(), "Page did not refresh after clicking the company name");
		test.pass("Page refreshed, URL: " + driver.getCurrentUrl());
	}

	// TEST 4: each module opens, and each of its sub modules opens its own page
	@Test(priority = 4, dataProvider = "modules")
	public void module_and_submodules(String module) throws InterruptedException, IOException {

		test = extent.createTest("Module: " + module);

		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		List<String> names = dash.get_submenu_names(module);
		List<String> failures = new ArrayList<String>();

		// module with no dropdown: it is a direct link, so click it and check the page
		if (names.isEmpty()) {
			test.info(module + " has no sub modules, clicking the module itself");
			go_dashboard();
			dash.Click_module(module);
			if (page_opened()) {
				test.pass(module + " opened: " + driver.getCurrentUrl());
			} else {
				failures.add(module);
				test.fail(module + " opened an error page: " + driver.getCurrentUrl())
						.addScreenCaptureFromPath(screenshotMethod(driver, module.replace(" ", "_")));
				driver.navigate().back();   // leave the broken page
			}
		} else {
			test.info(module + " has " + names.size() + " sub modules: " + names);

			// click every sub module, starting each from the dashboard
			for (int i = 0; i < names.size(); i++) {
				go_dashboard();
				dash.Click_submenu(module, i);

				if (page_opened()) {
					test.pass(module + " > " + names.get(i) + " opened: " + driver.getCurrentUrl());
				} else {
					// DEFECT: record it with a screenshot of the broken page, then carry on
					failures.add(module + " > " + names.get(i));
					test.fail(module + " > " + names.get(i) + " opened an error page: " + driver.getCurrentUrl())
							.addScreenCaptureFromPath(screenshotMethod(driver,
									module.replace(" ", "_") + "_" + names.get(i).replace(" ", "_")));
					driver.navigate().back();   // leave the broken page
				}
			}
		}

		// the test fails at the END, so every sub module is checked even if one is broken
		Assert.assertTrue(failures.isEmpty(), "Pages not opening: " + failures);
	}

	// TEST 5: profile menu shows Logout, and Logout returns to the login page (runs last)
	@Test(priority = 99)
	public void profile_and_logout() {

		test = extent.createTest("Profile Menu and Logout");

		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
		A_Login_Cls login = new A_Login_Cls(driver);

		test.info("Clicking Hi, AppAdmin");
		dash.Click_profile();
		Assert.assertTrue(dash.verify_logout().isDisplayed(), "Logout option not displayed");
		test.pass("Logout option displayed");

		dash.Click_logout();
		Assert.assertTrue(login.verify_username().isDisplayed(), "Login page not shown after logout");
		test.pass("Logout successful, back on the login page");
	}
}