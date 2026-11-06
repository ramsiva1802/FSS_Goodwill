package TestPkg;

import UtillPkg.WaitUtil;
import java.util.List;

import org.openqa.selenium.JavascriptExecutor;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;
import PagePkg.C_MainCompany_Cls;

/*
 * NOTES - C_MainCompany_Test
 * ------------------------
 * Company Info > Main Company.
 * Business rule: a created company is saved but is NOT visible in the UI list for this user,
 * so the UI search for a new company is expected to return 0 rows.
 * Only the UI is checked here (no database checks).
 * 15 fields are mandatory; FSSAI, Latitude, Longitude and Address2 are optional.
 * The Billing Code must be unique: C_MainCompany_Cls generates a new one (2 random letters + number)
 * on every Fill_mandatory call.
 * Each test starts from the Main Company list screen (see @BeforeMethod).
 */
public class C_MainCompany_Test extends BaseCls {

	String dash_url;

	// the company created by scenario 9, reused by the search and duplicate scenarios
	String created_code;
	String created_name;
	String created_billing;

	// the 15 messages the form shows when everything is empty (taken from the screen)
	String[] REQUIRED_MESSAGES = { "Please Enter Companycode", "Please Enter a Valid Companyname",
			"Please Enter GSTIN Number", "Please Enter Email", "Please Enter Phone Number", "Please Enter ZipCode",
			"Please select a Country", "Please select a State", "Please Enter CEO", "Please Enter Date of commission",
			"Please Enter UPIID", "Please Enter BankName", "Please Enter Billing Code", "Please Enter Address1",
			"Please Select Currency" };

	// a unique code per call: the number changes every 0.1 second and repeats only after about 115 days
	private String unique(String prefix) {
		return prefix + (System.currentTimeMillis() / 100) % 10000000;
	}

	// runs ONCE: fresh session, login through the form
	@BeforeClass
	public void login_first() {
		driver.get(url);
		driver.manage().deleteAllCookies();
		try {
			((JavascriptExecutor) driver).executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
		} catch (Exception e) {
			System.out.println("Could not clear browser storage: " + e.getMessage());
		}
		driver.get(url);

		A_Login_Cls login = new A_Login_Cls(driver);
		login.Login(admin_user, admin_pass);
		Assert.assertTrue(login.login_success(), "Could not log in for Main Company tests");

		dash_url = driver.getCurrentUrl();
	}

	// runs before EVERY test: open Company Info > Main Company (the list screen)
	@BeforeMethod
	public void open_main_company() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		dash.Click_submenu_by_name("Company Info", "Main Company");
		WaitUtil.settle(driver, 1500);
		remember_clean_screen();
	}

	// opens the create form from the list screen
	private void open_create(C_MainCompany_Cls mc) throws InterruptedException {
		mc.Click_create();
		WaitUtil.settle(driver, 1500);
	}

	// ======================================================================
	// SCENARIO 1: list screen elements
	// Checks that every control on the Main Company list screen is displayed
	// and the existing company is shown in the table.
	// ======================================================================
	@Test(priority = 1)
	public void s01_verify_list_page() {

		test = extent.createTest("S01 Main Company List Page Verification");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		mc.open_search_form();   // the search boxes only appear after clicking Search

		Assert.assertTrue(mc.verify_create_btn().isDisplayed(), "Create button not displayed");
		test.pass("Create button displayed");

		Assert.assertTrue(mc.verify_top_search().isDisplayed(), "Search button not displayed");
		test.pass("Search button displayed");

		Assert.assertTrue(mc.verify_search_code().isDisplayed(), "Company Code search box not displayed");
		test.pass("Company Code search box displayed");

		Assert.assertTrue(mc.verify_search_name().isDisplayed(), "Company Name search box not displayed");
		test.pass("Company Name search box displayed");

		Assert.assertTrue(mc.verify_entries().isDisplayed(), "Entries info not displayed");
		test.pass("Entries info displayed: " + mc.verify_entries().getText());

		Assert.assertTrue(mc.result_count() >= 1, "Company table has no rows");
		test.pass("Company table shows " + mc.result_count() + " row(s)");
	}

	// ======================================================================
	// SCENARIO 2: search an existing company
	// Search by code, by name, and by a code that does not exist.
	// The rows actually shown are written to the report before each check,
	// so a failure shows what the table really contained.
	// ======================================================================
	@Test(priority = 2)
	public void s02_search_existing_company() throws InterruptedException {

		test = extent.createTest("S02 Search Existing Company");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		test.info("Searching by company code GWP");
		mc.Search_company("GWP", "");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + mc.result_texts());
		Assert.assertEquals(mc.result_count(), 1, "GWP should return exactly 1 row");
		test.pass("GWP found: 1 row");

		test.info("Searching by company name");
		mc.Search_company("", "GOODWILL");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + mc.result_texts());
		Assert.assertTrue(mc.result_count() >= 1, "Name search returned nothing");
		test.pass("Name search found " + mc.result_count() + " row(s)");

		test.info("Searching a code that does not exist");
		mc.Search_company("ZZZ999", "");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + mc.result_texts());
		Assert.assertEquals(mc.result_count(), 0, "A non existing code returned rows");
		test.pass("Non existing code returned 0 rows");
	}

	// ======================================================================
	// SCENARIO 3: create form elements
	// Create opens the form and every field, drop down and button is displayed.
	// ======================================================================
	@Test(priority = 3)
	public void s03_verify_create_form() throws InterruptedException {

		test = extent.createTest("S03 Create Form Verification");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		open_create(mc);

		Assert.assertTrue(mc.verify_company_code().isDisplayed(), "Company Code not displayed");
		test.pass("Company Code displayed");

		Assert.assertTrue(mc.verify_company_name().isDisplayed(), "Company Name not displayed");
		test.pass("Company Name displayed");

		Assert.assertTrue(mc.verify_gstin().isDisplayed(), "GSTIN not displayed");
		test.pass("GSTIN displayed");

		Assert.assertTrue(mc.verify_email().isDisplayed(), "Email not displayed");
		test.pass("Email displayed");

		Assert.assertTrue(mc.verify_phone().isDisplayed(), "Phone not displayed");
		test.pass("Phone displayed");

		Assert.assertTrue(mc.verify_zip().isDisplayed(), "Zip not displayed");
		test.pass("Zip displayed");

		Assert.assertTrue(mc.verify_country().isDisplayed(), "Country not displayed");
		test.pass("Country displayed");

		Assert.assertTrue(mc.verify_state().isDisplayed(), "State not displayed");
		test.pass("State displayed");

		Assert.assertTrue(mc.verify_currency().isDisplayed(), "Currency not displayed");
		test.pass("Currency displayed");

		Assert.assertTrue(mc.verify_logo_upload().isDisplayed(), "Upload logo not displayed");
		test.pass("Upload Company Logo displayed");

		Assert.assertTrue(mc.verify_back().isDisplayed(), "Back not displayed");
		test.pass("Back displayed");

		Assert.assertTrue(mc.verify_submit().isDisplayed(), "Submit not displayed");
		test.pass("Submit displayed");
	}

	// ======================================================================
	// SCENARIO 4: Back button
	// Back on the create form must return to the list screen without saving anything.
	// ======================================================================
	@Test(priority = 4)
	public void s04_back_button() throws InterruptedException {

		test = extent.createTest("S04 Back Button Returns To List");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		open_create(mc);
		mc.Click_back();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(mc.is_list_page(), "Back did not return to the list page");
		test.pass("Back returned to the list page");
	}

	// ======================================================================
	// SCENARIO 5: submit the EMPTY form
	// All 15 mandatory fields must each show their own message, and nothing is saved.
	// ======================================================================
	@Test(priority = 5)
	public void s05_empty_form_shows_all_messages() throws InterruptedException {

		test = extent.createTest("S05 Empty Form Shows All Mandatory Messages");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		open_create(mc);
		mc.Click_submit();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(mc.is_create_form(), "Empty form was accepted, the create form closed");
		test.pass("Empty form rejected, still on the create form");

		List<String> messages = mc.validation_messages();
		test.info("Messages shown: " + messages);

		// every expected message must be on screen (compared in lower case to ignore capital letters)
		String shown = messages.toString().toLowerCase();
		for (String expected : REQUIRED_MESSAGES) {
			Assert.assertTrue(shown.contains(expected.toLowerCase()), "Message missing: " + expected);
			test.pass("Message shown: " + expected);
		}

		Assert.assertEquals(messages.size(), REQUIRED_MESSAGES.length, "Unexpected number of messages: " + messages);
		test.pass("Exactly " + REQUIRED_MESSAGES.length + " messages shown");
	}

	// ======================================================================
	// SCENARIO 6: only the key fields blank
	// Every other mandatory field is filled, so only the 5 key fields (name, GST, email,
	// phone, zip) may show a message and the company code message must NOT appear.
	// ======================================================================
	@Test(priority = 6)
	public void s06_key_fields_blank() throws InterruptedException {

		test = extent.createTest("S06 Key Fields Blank");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		open_create(mc);
		mc.Fill_mandatory(unique("AT"), "", "", "", "", "");
		mc.Click_submit();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(mc.is_create_form(), "Form with blank key fields was accepted");
		test.pass("Form with blank key fields rejected");

		List<String> messages = mc.validation_messages();
		test.info("Messages shown: " + messages);
		Assert.assertEquals(messages.size(), 5, "Expected exactly 5 messages, got: " + messages);
		Assert.assertFalse(messages.toString().toLowerCase().contains("companycode"),
				"Company code message shown although the code was filled");
		test.pass("Only the 5 blank key fields show a message");
	}

	// ======================================================================
	// SCENARIO 7: one key field blank at a time
	// For each of the 6 key fields: leave only that field blank, fill the rest,
	// Submit must be rejected. Failures are collected so every field is tested.
	// ======================================================================
	@Test(priority = 7)
	public void s07_each_key_field_blank() throws InterruptedException {

		test = extent.createTest("S07 Each Key Field Blank");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		String[] fields = { "Company Code", "Company Name", "GSTIN", "Email", "Phone", "Zip" };
		java.util.List<String> accepted_wrongly = new java.util.ArrayList<String>();

		open_create(mc);

		for (int i = 0; i < fields.length; i++) {
			// valid values for all 6 key fields, then blank out field number i
			String[] v = { unique("AT"), "Auto Test Company", "32AAAAA0000A1Z5", "auto@test.com", "9876543210",
					"682001" };
			v[i] = "";

			mc.Fill_mandatory(v[0], v[1], v[2], v[3], v[4], v[5]);
			mc.Click_submit();
			WaitUtil.settle(driver, 1500);

			if (mc.is_create_form()) {
				test.pass(fields[i] + " blank: rejected");
			} else {
				accepted_wrongly.add(fields[i]);
				test.fail(fields[i] + " blank: the form was accepted");
				open_main_company();   // the form closed, so reopen it for the next field
				open_create(mc);
			}
		}

		Assert.assertTrue(accepted_wrongly.isEmpty(), "Form accepted with a blank: " + accepted_wrongly);
	}

	// Rows for scenario 8: description, GST, email, phone, zip (one value is wrong in each row)
	@DataProvider(name = "invalid_values")
	public Object[][] invalidValues() {
		return new Object[][] {
				{ "Invalid email (no @)", "32AAAAA0000A1Z5", "notanemail", "9876543210", "682001" },
				{ "Phone too short", "32AAAAA0000A1Z5", "auto@test.com", "12345", "682001" },
				{ "Phone with letters", "32AAAAA0000A1Z5", "auto@test.com", "98abc43210", "682001" },
				{ "GST too short", "123", "auto@test.com", "9876543210", "682001" },
				{ "Zip with letters", "32AAAAA0000A1Z5", "auto@test.com", "9876543210", "abcdef" } };
	}

	// ======================================================================
	// SCENARIO 8: invalid values in one field
	// Each row has exactly one wrong value. The form must be rejected (it stays open).
	// If the site ACCEPTS one of these, that is a validation defect to report.
	// ======================================================================
	@Test(priority = 8, dataProvider = "invalid_values")
	public void s08_invalid_values(String description, String gst, String email, String phone, String zip)
			throws InterruptedException {

		test = extent.createTest("S08 " + description);

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		open_create(mc);
		mc.Fill_mandatory(unique("AT"), "Auto Test Company", gst, email, phone, zip);
		mc.Click_submit();
		WaitUtil.settle(driver, 2000);

		test.info("Messages shown: " + mc.validation_messages());

		Assert.assertTrue(mc.is_create_form(), "DEFECT: the site accepted: " + description);
		test.pass(description + ": rejected, still on the form");
	}

	// ======================================================================
	// SCENARIO 9: create a valid company (all fields)
	// The form is filled completely and submitted. It must be accepted
	// (the form closes or a message appears). The code is kept for scenarios 10 and 11.
	// ======================================================================
	@Test(priority = 9)
	public void s09_create_valid_company() throws InterruptedException {

		test = extent.createTest("S09 Create Valid Company");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		String stamp = unique("");
		created_code = "AT" + stamp;
		created_name = "Auto Test Company " + stamp;

		open_create(mc);
		test.info("Creating company " + created_code + " / " + created_name);
		mc.Fill_form(created_code, created_name, "32AAAAA0000A1Z5", "auto" + stamp + "@test.com", "9876543210",
				"682001");
		created_billing = mc.last_billing_code;   // the unique billing code that was typed
		test.info("Billing code used: " + created_billing);

		mc.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = mc.get_message();
		test.info("Message after submit: '" + message + "'");

		Assert.assertTrue(!mc.is_create_form() || !message.isEmpty(), "Submit did not give any response");
		test.pass("Company submitted: " + created_code);
	}

	// ======================================================================
	// SCENARIO 10: search the company that was just created
	// The company is saved but is NOT visible to this user, so the UI must show 0 rows.
	// ======================================================================
	@Test(priority = 10, dependsOnMethods = "s09_create_valid_company")
	public void s10_search_created_company() throws InterruptedException {

		test = extent.createTest("S10 Search Created Company (not visible to this user)");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		mc.Search_company(created_code, "");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + mc.result_texts());

		Assert.assertEquals(mc.result_count(), 0, "The new company is visible to this user, but it should not be");
		test.pass("Search for " + created_code + " returned 0 rows, as expected for this user");
	}

	// ======================================================================
	// SCENARIO 11: duplicate company code
	// Creating a second company with the SAME code must be rejected: the form stays open
	// or an error message is shown. (a fresh billing code is generated, so only the
	// company code is duplicated)
	// ======================================================================
	@Test(priority = 11, dependsOnMethods = "s09_create_valid_company")
	public void s11_duplicate_code() throws InterruptedException {

		test = extent.createTest("S11 Duplicate Company Code");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);

		open_create(mc);
		mc.Fill_form(created_code, "Duplicate " + created_name, "32AAAAA0000A1Z5", "dup@test.com", "9876543210",
				"682001");
		mc.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = mc.get_message();
		test.info("Message after submit: '" + message + "'");
		test.info("Validation messages: " + mc.validation_messages());

		Assert.assertTrue(mc.is_create_form() || !message.isEmpty(),
				"DEFECT: the duplicate company code " + created_code + " was accepted without any message");
		test.pass("Duplicate company code was not silently accepted");
	}

	// ======================================================================
	// SCENARIO 12: only mandatory fields (optional fields left empty)
	// FSSAI, Latitude, Longitude and Address2 are optional, so the company must still be accepted.
	// ======================================================================
	@Test(priority = 12)
	public void s12_optional_fields_blank() throws InterruptedException {

		test = extent.createTest("S12 Create With Optional Fields Blank");

		C_MainCompany_Cls mc = new C_MainCompany_Cls(driver);
		String code = unique("AT");

		open_create(mc);
		mc.Fill_mandatory(code, "Auto Optional Blank " + code, "32AAAAA0000A1Z5", "opt@test.com", "9876543210",
				"682001");
		mc.Click_submit();
		WaitUtil.settle(driver, 3000);

		Assert.assertTrue(!mc.is_create_form() || !mc.get_message().isEmpty(),
				"Company with optional fields blank was rejected. Messages: " + mc.validation_messages());
		test.pass("Company accepted without the optional fields: " + code);
	}
}