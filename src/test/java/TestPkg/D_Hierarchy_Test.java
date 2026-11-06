package TestPkg;

import UtillPkg.WaitUtil;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.D_Hierarchy_Cls;
import PagePkg.A_Login_Cls;

/*
 * NOTES - D_Hierarchy_Test
 * ----------------------
 * Company Info > Hierarchy Details. UI checks only (no database).
 * Seed data on the list: PGWPP (PLANT) and SGWPS (SALES ORGANIZATION).
 * Parent Hierarchy Type depends on the chosen Hierarchy Type; DISTRIBUTOR shows an extra
 * Distributer Type drop down; User Code is disabled until Counter Billing is ticked.
 * The Billing Code must be unique: D_Hierarchy_Cls generates a new one on every Fill_mandatory call.
 * Each test starts from the Hierarchy Details list screen (see @BeforeMethod).
 */
public class D_Hierarchy_Test extends BaseCls {

	String dash_url;

	// the hierarchy created by scenario 22, reused by the search and duplicate scenarios
	String created_code;
	String created_name;

	// messages the form shows when everything is empty (taken from the screen; more may exist below the fold)
	String[] REQUIRED_MESSAGES = { "Please enter a Hierarchy Code", "Please enter a Hierarchy Name",
			"Please enter Address1", "Please enter GSTIN Number", "Please enter Email", "Please enter Phone Number",
			"Please enter ZipCode", "Please select a Country", "Please select a state", "Please enter CEO name",
			"Please enter Date of Commission", "Please enter Billing Code", "Please select a Hierarchy Type" };

	// a unique code per call: the number changes every 0.1 second and repeats only after about 115 days
	private String unique(String prefix) {
		return prefix + (System.currentTimeMillis() / 100) % 10000000;
	}

	// removes dots and capital letters so "Please enter Email." equals "please enter email"
	private String plain(String text) {
		return text.toLowerCase().replace(".", "").trim();
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
		Assert.assertTrue(login.login_success(), "Could not log in for Hierarchy tests");

		dash_url = driver.getCurrentUrl();
	}

	// opens Company Info > Hierarchy Details from the dashboard
	private void go_to_hierarchy() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		dash.Click_submenu_containing("Company Info", "Hierarchy");
		WaitUtil.settle(driver, 1500);
	}

	// runs before EVERY test: open the Hierarchy Details list screen (log in again if the session broke)
	@BeforeMethod
	public void open_hierarchy() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		try {
			go_to_hierarchy();
		} catch (Exception e) {
			login_first();
			go_to_hierarchy();
		}
		remember_clean_screen();
	}

	// opens the create form from the list screen
	private void open_create(D_Hierarchy_Cls h) throws InterruptedException {
		h.Click_create();
		WaitUtil.settle(driver, 1500);
	}

	// ======================================================================
	// SCENARIO 1: list screen elements
	// Buttons, table filter, all 6 column headers, entries text, pagination and the two seed rows.
	// ======================================================================
	@Test(priority = 1)
	public void s01_verify_list_page() {

		test = extent.createTest("S01 Hierarchy List Page Verification");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		Assert.assertTrue(h.verify_create_btn().isDisplayed(), "Create button not displayed");
		test.pass("Create button displayed");

		Assert.assertTrue(h.verify_top_search().isDisplayed(), "Search button not displayed");
		test.pass("Search button displayed");

		Assert.assertTrue(h.verify_table_filter().isDisplayed(), "Table Search: box not displayed");
		test.pass("Table Search: box displayed");

		for (String header : new String[] { "CODE", "NAME", "HIERARCHY TYPE", "GSTIN NUMBER", "STATE", "ACTION" }) {
			Assert.assertTrue(h.verify_header(header).isDisplayed(), "Column header not displayed: " + header);
			test.pass("Column header displayed: " + header);
		}

		Assert.assertTrue(h.verify_entries().isDisplayed(), "Entries text not displayed");
		test.pass("Entries text: " + h.verify_entries().getText());

		Assert.assertTrue(h.verify_previous().isDisplayed(), "Previous button not displayed");
		Assert.assertTrue(h.verify_next().isDisplayed(), "Next button not displayed");
		test.pass("Pagination (Previous / Next) displayed");

		String rows = h.result_texts().toString();
		test.info("Rows shown: " + rows);
		Assert.assertTrue(rows.contains("PGWPP"), "Seed row PGWPP not in the table");
		Assert.assertTrue(rows.contains("SGWPS"), "Seed row SGWPS not in the table");
		test.pass("Seed rows PGWPP and SGWPS are in the table");
	}

	// ======================================================================
	// SCENARIO 2: search form elements
	// Clicking the top Search button opens the form: type, code and name boxes and its Search button.
	// ======================================================================
	@Test(priority = 2)
	public void s02_verify_search_form() {

		test = extent.createTest("S02 Search Form Verification");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		h.open_search_form();

		Assert.assertTrue(h.verify_search_type().isDisplayed(), "Hierarchy Type box not displayed");
		test.pass("Hierarchy Type box displayed");

		Assert.assertTrue(h.verify_search_code().isDisplayed(), "Hierarchy Code box not displayed");
		test.pass("Hierarchy Code box displayed");

		Assert.assertTrue(h.verify_search_name().isDisplayed(), "Hierarchy Name box not displayed");
		test.pass("Hierarchy Name box displayed");

		Assert.assertTrue(h.verify_search_go().isDisplayed(), "Search button of the form not displayed");
		test.pass("Search button of the form displayed");
	}

	// ======================================================================
	// SCENARIO 3: search by hierarchy code
	// PGWPP returns exactly one row and that row is a PLANT.
	// ======================================================================
	@Test(priority = 3)
	public void s03_search_by_code() throws InterruptedException {

		test = extent.createTest("S03 Search By Hierarchy Code");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		h.Search_hierarchy("", "PGWPP", "");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + h.result_texts());

		Assert.assertEquals(h.result_count(), 1, "PGWPP should return exactly 1 row");
		Assert.assertTrue(h.result_texts().get(0).toUpperCase().contains("PLANT"), "PGWPP row is not a PLANT");
		test.pass("PGWPP found: 1 row, type PLANT");
	}

	// ======================================================================
	// SCENARIO 4: search by hierarchy name (partial text)
	// "GOODWILL" matches both seed rows.
	// ======================================================================
	@Test(priority = 4)
	public void s04_search_by_name() throws InterruptedException {

		test = extent.createTest("S04 Search By Hierarchy Name");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		h.Search_hierarchy("", "", "GOODWILL");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + h.result_texts());

		Assert.assertTrue(h.result_count() >= 2, "Name search should find at least the 2 seed rows");
		test.pass("Name search found " + h.result_count() + " rows");
	}

	// ======================================================================
	// SCENARIO 5: search by hierarchy type
	// PLANT returns PGWPP and every returned row is a PLANT.
	// ======================================================================
	@Test(priority = 5)
	public void s05_search_by_type() throws InterruptedException {

		test = extent.createTest("S05 Search By Hierarchy Type");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		h.Search_hierarchy("PLANT", "", "");
		WaitUtil.settle(driver, 2000);
		List<String> rows = h.result_texts();
		test.info("Rows shown: " + rows);

		Assert.assertTrue(h.result_count() >= 1, "Type search returned nothing");
		for (String row : rows) {
			Assert.assertTrue(row.toUpperCase().contains("PLANT"), "A row is not a PLANT: " + row);
		}
		test.pass("Every returned row is a PLANT (" + h.result_count() + " row(s))");
	}

	// ======================================================================
	// SCENARIO 6: combined search
	// Code + name + type that belong together return the row; a code with the wrong type returns nothing.
	// ======================================================================
	@Test(priority = 6)
	public void s06_combined_search() throws InterruptedException {

		test = extent.createTest("S06 Combined Search");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		h.Search_hierarchy("PLANT", "PGWPP", "GOODWILL");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown (matching values): " + h.result_texts());
		Assert.assertEquals(h.result_count(), 1, "Matching code + name + type should return 1 row");
		test.pass("Matching code, name and type returned 1 row");

		h.Search_hierarchy("SALES ORGANIZATION", "PGWPP", "");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown (code with the wrong type): " + h.result_texts());
		Assert.assertEquals(h.result_count(), 0, "PGWPP with the wrong type should return 0 rows");
		test.pass("Code with the wrong type returned 0 rows");
	}

	// ======================================================================
	// SCENARIO 7: search for something that does not exist
	// Must return no rows. (Same check that failed on the Main Company screen.)
	// ======================================================================
	@Test(priority = 7)
	public void s07_search_not_found() throws InterruptedException {

		test = extent.createTest("S07 Search Not Found");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		h.Search_hierarchy("", "ZZZ999", "");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + h.result_texts());

		Assert.assertEquals(h.result_count(), 0, "A non existing code returned rows");
		test.pass("Non existing code returned 0 rows");
	}

	// ======================================================================
	// SCENARIO 8: the "Search:" box above the table
	// It filters the rows already in the table: SGWPS leaves one row, a nonsense text leaves none.
	// ======================================================================
	@Test(priority = 8)
	public void s08_table_filter() throws InterruptedException {

		test = extent.createTest("S08 Table Search Box");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		h.Filter_table("SGWPS");
		WaitUtil.settle(driver, 1000);
		test.info("Rows shown for SGWPS: " + h.result_texts());
		Assert.assertEquals(h.result_count(), 1, "SGWPS should leave exactly 1 row");
		test.pass("SGWPS filter left 1 row");

		h.Filter_table("ZZZ999");
		WaitUtil.settle(driver, 1000);
		test.info("Rows shown for ZZZ999: " + h.result_texts());
		Assert.assertEquals(h.result_count(), 0, "A nonsense text should leave 0 rows");
		test.pass("Nonsense text left 0 rows");
	}

	// ======================================================================
	// SCENARIO 9: sorting
	// Clicking the CODE header flips the order (the first row changes), clicking again flips it back.
	// ======================================================================
	@Test(priority = 9)
	public void s09_sort_by_code() throws InterruptedException {

		test = extent.createTest("S09 Sort By Code");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		String original = h.first_row_code();
		h.Click_header("CODE");
		WaitUtil.settle(driver, 1000);
		String flipped = h.first_row_code();
		test.info("First code before: " + original + ", after one click: " + flipped);
		Assert.assertNotEquals(flipped, original, "Clicking the CODE header did not change the order");
		test.pass("First click reversed the order");

		h.Click_header("CODE");
		WaitUtil.settle(driver, 1000);
		Assert.assertEquals(h.first_row_code(), original, "A second click did not restore the order");
		test.pass("Second click restored the order");
	}

	// ======================================================================
	// SCENARIO 10: view icon
	// The eye icon of PGWPP opens the details of that hierarchy (the list is replaced by a form).
	// ======================================================================
	@Test(priority = 10)
	public void s10_view_icon() throws InterruptedException {

		test = extent.createTest("S10 View Icon");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		h.Click_view("PGWPP");
		WaitUtil.settle(driver, 2000);

		Assert.assertFalse(h.is_list_page(), "The view icon did not leave the list screen");
		test.pass("View opened a details screen");

		Assert.assertEquals(h.code_value(), "PGWPP", "The details screen shows a different hierarchy code");
		test.pass("Details screen shows PGWPP");
	}

	// ======================================================================
	// SCENARIO 11: edit icon
	// The pencil icon of PGWPP opens the form filled with that hierarchy, and Back returns to the list.
	// ======================================================================
	@Test(priority = 11)
	public void s11_edit_icon() throws InterruptedException {

		test = extent.createTest("S11 Edit Icon");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		h.Click_edit("PGWPP");
		WaitUtil.settle(driver, 2000);

		Assert.assertFalse(h.is_list_page(), "The edit icon did not leave the list screen");
		Assert.assertEquals(h.code_value(), "PGWPP", "The edit form shows a different hierarchy code");
		test.pass("Edit form opened and shows PGWPP");

		Assert.assertTrue(h.verify_submit().isDisplayed(), "Submit not displayed on the edit form");
		test.pass("Submit displayed on the edit form");

		h.Click_back();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(h.is_list_page(), "Back did not return to the list");
		test.pass("Back returned to the list (nothing was changed)");
	}

	// ======================================================================
	// SCENARIO 12: create form elements
	// Create opens the form and every field, drop down, check box and button is displayed.
	// ======================================================================
	@Test(priority = 12)
	public void s12_verify_create_form() throws InterruptedException {

		test = extent.createTest("S12 Create Form Verification");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);

		String[] labels = { "Hierarchy Code", "Hierarchy Name", "Address 1", "Address 2", "GSTIN Number", "Email",
				"Phone Number", "Zip Code", "FSSAI Number", "Country", "State", "CEO", "Date Of Commission", "Latitude",
				"Longitude", "Billing Code", "Hierarchy Type", "Add New", "Upi ID", "Bank Name", "Account Number",
				"IFSCCode", "Branch Name", "Branch Phone Number", "Branch Zip", "Branch Address",
				"Parent Hierarchy Type", "Hierarchy Name search", "Counter Billing", "User Code", "Back", "Submit" };

		WebElement[] elements = { h.verify_code(), h.verify_name(), h.verify_address1(), h.verify_address2(),
				h.verify_gstin(), h.verify_email(), h.verify_phone(), h.verify_zip(), h.verify_fssai(),
				h.verify_country(), h.verify_state(), h.verify_ceo(), h.verify_date(), h.verify_latitude(),
				h.verify_longitude(), h.verify_billing(), h.verify_hierarchy_type(), h.verify_add_new(),
				h.verify_upi(), h.verify_bank(), h.verify_account(), h.verify_ifsc(), h.verify_branch(),
				h.verify_branch_phone(), h.verify_branch_zip(), h.verify_branch_address(), h.verify_parent_type(),
				h.verify_parent_name_search(), h.verify_counter_billing(), h.verify_user_code(), h.verify_back(),
				h.verify_submit() };

		for (int i = 0; i < labels.length; i++) {
			Assert.assertTrue(elements[i].isDisplayed(), labels[i] + " not displayed");
			test.pass(labels[i] + " displayed");
		}
	}

	// ======================================================================
	// SCENARIO 13: submit the EMPTY form
	// Every mandatory field shows its own message and nothing is saved.
	// ======================================================================
	@Test(priority = 13)
	public void s13_empty_form_shows_messages() throws InterruptedException {

		test = extent.createTest("S13 Empty Form Shows Mandatory Messages");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);
		h.Click_submit();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(h.is_create_form(), "Empty form was accepted, the create form closed");
		test.pass("Empty form rejected, still on the create form");

		List<String> messages = h.validation_messages();
		test.info("Messages shown: " + messages);

		String shown = plain(messages.toString());
		for (String expected : REQUIRED_MESSAGES) {
			Assert.assertTrue(shown.contains(plain(expected)), "Message missing: " + expected);
			test.pass("Message shown: " + expected);
		}
	}

	// ======================================================================
	// SCENARIO 14: Back button
	// Back on the create form returns to the list without saving anything.
	// ======================================================================
	@Test(priority = 14)
	public void s14_back_button() throws InterruptedException {

		test = extent.createTest("S14 Back Button Returns To List");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);
		h.Click_back();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(h.is_list_page(), "Back did not return to the list page");
		test.pass("Back returned to the list page");
	}

	// ======================================================================
	// SCENARIO 15: hierarchy types offered
	// The Hierarchy Type drop down lists PLANT, SALES OFFICE, SALES ORGANIZATION and DISTRIBUTOR.
	// ======================================================================
	@Test(priority = 15)
	public void s15_hierarchy_type_options() throws InterruptedException {

		test = extent.createTest("S15 Hierarchy Type Options");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);
		List<String> options = h.hierarchy_type_options();
		test.info("Hierarchy types offered: " + options);

		String shown = options.toString().toUpperCase();
		for (String expected : new String[] { "PLANT", "SALES OFFICE", "SALES ORGANIZATION", "DISTRIBUTOR" }) {
			Assert.assertTrue(shown.contains(expected), "Hierarchy type missing: " + expected);
			test.pass("Hierarchy type offered: " + expected);
		}
	}

	// Rows for scenario 16: hierarchy type, the parent types it must offer
	@DataProvider(name = "parents")
	public Object[][] parents() {
		return new Object[][] { { "SALES OFFICE", new String[] { "SALES ORGANIZATION" } },
				{ "PLANT", new String[] { "SALES ORGANIZATION", "SALES OFFICE" } },
				{ "DISTRIBUTOR", new String[] { "SALES ORGANIZATION", "SALES OFFICE", "PLANT", "Depot" } } };
	}

	// ======================================================================
	// SCENARIO 16: Parent Hierarchy Type depends on the Hierarchy Type
	// Choosing a type must fill the "Belongs To" parent list with exactly the allowed parents.
	// ======================================================================
	@Test(priority = 16, dataProvider = "parents")
	public void s16_parent_depends_on_type(String type, String[] expected) throws InterruptedException {

		test = extent.createTest("S16 Parent Types For " + type);

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);
		h.Select_hierarchy_type(type);
		WaitUtil.settle(driver, 1000);

		List<String> actual = h.parent_options();
		test.info("Parent types offered for " + type + ": " + actual);

		Set<String> actual_set = new HashSet<String>();
		for (String a : actual) {
			actual_set.add(a.toLowerCase());
		}
		Set<String> expected_set = new HashSet<String>();
		for (String e : Arrays.asList(expected)) {
			expected_set.add(e.toLowerCase());
		}

		Assert.assertEquals(actual_set, expected_set, "Wrong parent types for " + type);
		test.pass("Parent types for " + type + " are correct: " + actual);
	}

	// ======================================================================
	// SCENARIO 17: Distributer Type drop down
	// Appears only for DISTRIBUTOR and offers HSD, EXD and HFDD; it is hidden for PLANT.
	// ======================================================================
	@Test(priority = 17)
	public void s17_distributer_type() throws InterruptedException {

		test = extent.createTest("S17 Distributer Type Drop Down");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);

		h.Select_hierarchy_type("PLANT");
		WaitUtil.settle(driver, 1000);
		Assert.assertFalse(h.is_distributer_visible(), "Distributer Type is shown for PLANT");
		test.pass("Distributer Type is hidden for PLANT");

		h.Select_hierarchy_type("DISTRIBUTOR");
		WaitUtil.settle(driver, 1000);
		Assert.assertTrue(h.is_distributer_visible(), "Distributer Type is not shown for DISTRIBUTOR");
		test.pass("Distributer Type is shown for DISTRIBUTOR");

		List<String> options = h.distributer_options();
		test.info("Distributer types offered: " + options);
		Assert.assertEquals(options.toString().toUpperCase(), "[HSD, EXD, HFDD]", "Wrong distributer types");
		test.pass("Distributer types are HSD, EXD and HFDD");
	}

	// ======================================================================
	// SCENARIO 18: "Add New" hierarchy type pop-up
	// Add New opens a pop-up with a name box and Submit; an empty Submit keeps it open; X closes it.
	// (No new hierarchy type is created, so no data is added.)
	// ======================================================================
	@Test(priority = 18)
	public void s18_add_new_popup() throws InterruptedException {

		test = extent.createTest("S18 Add New Hierarchy Type Pop-up");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);
		h.Click_add_new();
		WaitUtil.settle(driver, 1000);

		Assert.assertTrue(h.is_type_popup_open(), "The Add New pop-up did not open");
		test.pass("Add New pop-up opened");

		Assert.assertTrue(h.verify_type_name_input().isDisplayed(), "Hierarchy Type Name box not displayed");
		Assert.assertTrue(h.verify_popup_submit().isDisplayed(), "Pop-up Submit not displayed");
		test.pass("Hierarchy Type Name box and Submit displayed");

		h.Click_popup_submit();
		WaitUtil.settle(driver, 1000);
		Assert.assertTrue(h.is_type_popup_open(), "An empty hierarchy type name was accepted (pop-up closed)");
		test.pass("Empty name rejected, the pop-up stayed open. Messages: " + h.validation_messages());

		h.Close_popup();
		WaitUtil.settle(driver, 1000);
		Assert.assertFalse(h.is_type_popup_open(), "The X did not close the pop-up");
		test.pass("X closed the pop-up");
	}

	// ======================================================================
	// SCENARIO 19: Counter Billing and User Code
	// User Code is disabled until Counter Billing is ticked.
	// ======================================================================
	@Test(priority = 19)
	public void s19_counter_billing() throws InterruptedException {

		test = extent.createTest("S19 Counter Billing And User Code");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);

		Assert.assertFalse(h.verify_user_code().isEnabled(), "User Code is enabled before Counter Billing is ticked");
		test.pass("User Code is disabled by default");

		h.Tick_counter_billing();
		WaitUtil.settle(driver, 1000);
		Assert.assertTrue(h.verify_counter_billing().isSelected(), "Counter Billing did not get ticked");
		test.pass("Counter Billing ticked");

		Assert.assertTrue(h.verify_user_code().isEnabled(), "User Code is still disabled after ticking Counter Billing");
		test.pass("User Code became enabled");
	}

	// ======================================================================
	// SCENARIO 20: State list depends on Country
	// After a country is chosen the State drop down has options.
	// ======================================================================
	@Test(priority = 20)
	public void s20_state_depends_on_country() throws InterruptedException {

		test = extent.createTest("S20 State List Depends On Country");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);
		h.Select_country_first();
		WaitUtil.settle(driver, 1500);

		int states = h.state_option_count();
		test.info("State options after choosing a country: " + states);
		Assert.assertTrue(states >= 1, "No states offered after choosing a country");
		test.pass("State list filled after choosing a country (" + states + " states)");
	}

	// Rows for scenario 21: description, GST, email, phone, zip (one value is wrong in each row)
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
	// SCENARIO 21: invalid values in one field
	// Each row has exactly one wrong value. The form must be rejected (it stays open).
	// If the site ACCEPTS one of these, that is a validation defect to report.
	// ======================================================================
	@Test(priority = 21, dataProvider = "invalid_values")
	public void s21_invalid_values(String description, String gst, String email, String phone, String zip)
			throws InterruptedException {

		test = extent.createTest("S21 " + description);

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);
		h.Fill_mandatory(unique("HC"), "Auto Hierarchy", gst, email, phone, zip);
		h.Click_submit();
		WaitUtil.settle(driver, 2000);

		test.info("Messages shown: " + h.validation_messages());
		Assert.assertTrue(h.is_create_form(), "DEFECT: the site accepted: " + description);
		test.pass(description + ": rejected, still on the form");
	}

	// ======================================================================
	// SCENARIO 22: create a valid hierarchy (all fields)
	// A top level type (SALES ORGANIZATION) is used so no parent is needed.
	// The form must be accepted (it closes or a message appears). The code is kept for scenarios 23 and 24.
	// ======================================================================
	@Test(priority = 22)
	public void s22_create_valid_hierarchy() throws InterruptedException {

		test = extent.createTest("S22 Create Valid Hierarchy");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		String stamp = unique("");
		created_code = "HC" + stamp;
		created_name = "Auto Hierarchy " + stamp;

		open_create(h);
		test.info("Creating hierarchy " + created_code + " / " + created_name);
		h.Fill_form(created_code, created_name, "32AAAAA0000A1Z5", "auto" + stamp + "@test.com", "9876543210",
				"682001");
		test.info("Billing code used: " + h.last_billing_code);

		h.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = h.get_message();
		test.info("Message after submit: '" + message + "'");
		test.info("Validation messages: " + h.validation_messages());

		Assert.assertTrue(!h.is_create_form() || !message.isEmpty(),
				"Submit did not give any response. Messages: " + h.validation_messages());
		test.pass("Hierarchy submitted: " + created_code);
	}

	// ======================================================================
	// SCENARIO 23: search the hierarchy that was just created
	// ASSUMPTION: a created hierarchy is visible in the list. If this screen behaves like Main Company
	// (saved but not visible to this user), change the expected count to 0.
	// ======================================================================
	@Test(priority = 23, dependsOnMethods = "s22_create_valid_hierarchy")
	public void s23_search_created_hierarchy() throws InterruptedException {

		test = extent.createTest("S23 Search Created Hierarchy");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		h.Search_hierarchy("", created_code, "");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + h.result_texts());

		Assert.assertEquals(h.result_count(), 1, "The new hierarchy " + created_code + " was not found in the list");
		test.pass("The new hierarchy " + created_code + " is in the list");
	}

	// ======================================================================
	// SCENARIO 24: duplicate hierarchy code
	// A second hierarchy with the SAME code must be rejected: the form stays open or a message appears.
	// ======================================================================
	@Test(priority = 24, dependsOnMethods = "s22_create_valid_hierarchy")
	public void s24_duplicate_code() throws InterruptedException {

		test = extent.createTest("S24 Duplicate Hierarchy Code");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);

		open_create(h);
		h.Fill_form(created_code, "Duplicate " + created_name, "32AAAAA0000A1Z5", "dup@test.com", "9876543210",
				"682001");
		h.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = h.get_message();
		test.info("Message after submit: '" + message + "'");
		test.info("Validation messages: " + h.validation_messages());

		Assert.assertTrue(h.is_create_form() || !message.isEmpty(),
				"DEFECT: the duplicate hierarchy code " + created_code + " was accepted without any message");
		test.pass("Duplicate hierarchy code was not silently accepted");
	}

	// ======================================================================
	// SCENARIO 25: only mandatory fields (optional fields left empty)
	// Address 2, FSSAI, latitude, longitude and the bank / branch block are optional,
	// so the hierarchy must still be accepted.
	// ======================================================================
	@Test(priority = 25)
	public void s25_optional_fields_blank() throws InterruptedException {

		test = extent.createTest("S25 Create With Optional Fields Blank");

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);
		String code = unique("HC");

		open_create(h);
		h.Fill_mandatory(code, "Auto Optional Blank " + code, "32AAAAA0000A1Z5", "opt@test.com", "9876543210",
				"682001");
		h.Click_submit();
		WaitUtil.settle(driver, 3000);

		Assert.assertTrue(!h.is_create_form() || !h.get_message().isEmpty(),
				"Hierarchy with optional fields blank was rejected. Messages: " + h.validation_messages());
		test.pass("Hierarchy accepted without the optional fields: " + code);
	}
}