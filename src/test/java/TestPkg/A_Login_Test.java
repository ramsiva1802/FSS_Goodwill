package TestPkg;

import UtillPkg.WaitUtil;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.A_Login_Cls;

/*
 * NOTES - A_Login_Test
 * ------------------
 * Every test is independent: @BeforeMethod gives each one a fresh login page.
 * Positive tests  -> login must succeed and the app must move past the login page.
 * Negative tests  -> login must be rejected and the login page must stay.
 */
public class A_Login_Test extends BaseCls {

	// valid test account
	String valid_user = UtillPkg.TestEnv.get("admin.user", "your-admin-user");
	String valid_pass = UtillPkg.TestEnv.get("admin.password", "your-admin-password");

	// runs before EVERY test: forget any previous login and open a fresh login page
	@BeforeMethod
	public void reset_to_login() {
		driver.manage().deleteAllCookies();
		((JavascriptExecutor) driver).executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
		driver.get(url);
	}

	// helper used by every negative test: login must fail and the login form must remain
	private void check_login_rejected(String user, String pass) throws InterruptedException {
		A_Login_Cls login = new A_Login_Cls(driver);

		test.info("Entering username: '" + user + "' and password: '" + pass + "'");
		login.Login(user, pass);
		WaitUtil.settle(driver, 2000);

		Assert.assertTrue(login.is_login_page(), "Login was accepted but should have been rejected");
		test.pass("Login rejected, user stayed on the login page");
	}

	// ---------------- PAGE ELEMENTS ----------------

	@Test(priority = 1)
	public void verify_login_page() {

		test = extent.createTest("Login Page Elements Verification");

		A_Login_Cls login = new A_Login_Cls(driver);

		test.info("Verifying login page elements...");

		Assert.assertTrue(login.verify_left_logo().isDisplayed(), "Left logo not displayed");
		test.pass("Left logo displayed");

		// the heading fades in after the page is loaded: wait for it instead of a fixed pause
		try {
			new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(10), java.time.Duration.ofMillis(100))
					.until(d -> login.verify_heading().isDisplayed());
		} catch (org.openqa.selenium.TimeoutException e) {
			// the assertion below reports it
		}
		Assert.assertTrue(login.verify_heading().isDisplayed(), "Welcome heading not displayed");
		test.pass("Heading displayed");

		Assert.assertTrue(login.verify_username().isDisplayed(), "Username field not displayed");
		test.pass("Username field displayed");

		Assert.assertTrue(login.verify_password().isDisplayed(), "Password field not displayed");
		test.pass("Password field displayed");

		Assert.assertTrue(login.verify_signin().isDisplayed(), "Sign In button not displayed");
		test.pass("Sign In button displayed");

		Assert.assertTrue(login.verify_forgot().isDisplayed(), "Forgot password link not displayed");
		test.pass("Forgot password link displayed");
	}

	@Test(priority = 2)
	public void password_is_masked() {

		test = extent.createTest("Password Field Masked Test");

		A_Login_Cls login = new A_Login_Cls(driver);

		// type="password" means the characters are shown as dots
		String type = login.verify_password().getAttribute("type");
		Assert.assertEquals(type, "password", "Password field is not masked");
		test.pass("Password field is masked (type=" + type + ")");
	}

	// ---------------- POSITIVE ----------------

	@Test(priority = 3)
	public void valid_login() {

		test = extent.createTest("Valid Username and Valid Password");

		A_Login_Cls login = new A_Login_Cls(driver);

		test.info("Login with correct username and password");
		login.Login(valid_user, valid_pass);

		Assert.assertTrue(login.login_success(), "Login failed, still on the login page");
		test.pass("Login successful, moved to next page: " + driver.getCurrentUrl());
	}

	@Test(priority = 4)
	public void valid_login_with_enter_key() {

		test = extent.createTest("Valid Login using Enter key");

		A_Login_Cls login = new A_Login_Cls(driver);

		test.info("Login with correct credentials and press ENTER");
		login.Login_with_enter(valid_user, valid_pass);

		Assert.assertTrue(login.login_success(), "Enter key login failed");
		test.pass("Login with Enter key successful: " + driver.getCurrentUrl());
	}

	// ---------------- NEGATIVE ----------------

	@Test(priority = 5)
	public void valid_user_wrong_password() throws InterruptedException {
		test = extent.createTest("Valid Username and Wrong Password");
		check_login_rejected(valid_user, "wrongpass");
	}

	@Test(priority = 6)
	public void wrong_user_valid_password() throws InterruptedException {
		test = extent.createTest("Wrong Username and Valid Password");
		check_login_rejected("wronguser@gmail.com", valid_pass);
	}

	@Test(priority = 7)
	public void wrong_user_wrong_password() throws InterruptedException {
		test = extent.createTest("Wrong Username and Wrong Password");
		check_login_rejected("wronguser@gmail.com", "wrong123");
	}

	@Test(priority = 8)
	public void empty_username_and_password() throws InterruptedException {
		test = extent.createTest("Empty Username and Empty Password");
		check_login_rejected("", "");
	}

	@Test(priority = 9)
	public void empty_username_valid_password() throws InterruptedException {
		test = extent.createTest("Empty Username and Valid Password");
		check_login_rejected("", valid_pass);
	}

	@Test(priority = 10)
	public void valid_user_empty_password() throws InterruptedException {
		test = extent.createTest("Valid Username and Empty Password");
		check_login_rejected(valid_user, "");
	}

	@Test(priority = 11)
	public void password_case_sensitive() throws InterruptedException {
		test = extent.createTest("Password Case Sensitivity");
		check_login_rejected(valid_user, valid_pass.toLowerCase().equals(valid_pass) ? valid_pass.toUpperCase() : valid_pass.toLowerCase());
	}

	@Test(priority = 12)
	public void special_characters() throws InterruptedException {
		test = extent.createTest("Special Characters in Username and Password");
		check_login_rejected("!@$#%", "!@$#%");
	}

	@Test(priority = 13)
	public void sql_injection_attempt() throws InterruptedException {
		test = extent.createTest("SQL Injection Attempt");
		check_login_rejected("' OR '1'='1", "' OR '1'='1");
	}

	// ---------------- FORGOT PASSWORD ----------------

	@Test(priority = 14)
	public void forgot_password() throws InterruptedException {

		test = extent.createTest("Forgot Password Link Test");

		A_Login_Cls login = new A_Login_Cls(driver);

		test.info("Clicking Forgot password?");
		login.Click_forgot();
		WaitUtil.settle(driver, 2000);
		test.pass("Forgot password clicked, URL: " + driver.getCurrentUrl());
	}
}