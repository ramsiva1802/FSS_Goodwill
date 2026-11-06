package TestPkg;

import UtillPkg.WaitUtil;
import java.util.List;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.E_Employee_Cls;
import PagePkg.A_Login_Cls;

/*
 * NOTES - E_Employee_Test
 * ---------------------
 * Employee Info > Employee. UI checks only (no database).
 * Rules: Employee Type and Designation are Sales Executive; Is Web User = Yes enables the Role
 * drop down and No disables it; Belongs To uses PLANT / GOODWILL POLYMERS FSS 2026-2027.
 * Existing employees in the list (for example SE10 YAZER P SHERIF) are only READ: the Resign
 * button is never clicked, so no existing employee is changed.
 * The Billing Code must be unique: E_Employee_Cls generates a new one on every Fill_mandatory call.
 * Each test starts from the Employee list screen (see @BeforeMethod).
 */
public class E_Employee_Test extends BaseCls {

	String dash_url;

	// the employee created by scenario 22, reused by the search and duplicate scenarios
	String created_code;

	// messages the form shows when everything is empty (taken from the screen)
	String[] REQUIRED_MESSAGES = { "Please enter a Employee Code", "Please enter a valid First Name",
			"Please enter a valid Last Name", "Please enter a valid Address", "Please select a Gender",
			"Please enter a Email", "Please select a Country", "Please select a State", "Please enter a Billing Code",
			"Please select a Designation", "Please select a hierarchy type", "Please select a hierarchy name" };

	// a unique value per call: the number changes every 0.1 second and repeats only after about 115 days
	private String unique(String prefix) {
		return prefix + (System.currentTimeMillis() / 100) % 10000000;
	}

	// removes dots and capital letters so "Please enter a Email." equals "please enter a email"
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
		Assert.assertTrue(login.login_success(), "Could not log in for Employee tests");

		dash_url = driver.getCurrentUrl();
	}

	// opens Employee Info > Employee from the dashboard
	private void go_to_employee() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		try {
			dash.Click_submenu_by_name("Employee Info", "Employee");
		} catch (NoSuchElementException e) {
			dash.Click_home();
			dash.Click_submenu_containing("Employee Info", "Employee");
		}
		WaitUtil.settle(driver, 1500);
	}

	// runs before EVERY test: open the Employee list screen (log in again if the session broke)
	@BeforeMethod
	public void open_employee() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		try {
			go_to_employee();
		} catch (Exception e) {
			login_first();
			go_to_employee();
		}
		remember_clean_screen();
	}

	// opens the create form from the list screen
	private void open_create(E_Employee_Cls e) throws InterruptedException {
		e.Click_create();
		WaitUtil.settle(driver, 1500);
	}

	// ======================================================================
	// SCENARIO 1: list screen elements
	// Buttons, table filter, Show entries, all 8 column headers, entries text, pagination and rows.
	// ======================================================================
	@Test(priority = 1)
	public void s01_verify_list_page() {

		test = extent.createTest("S01 Employee List Page Verification");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		Assert.assertTrue(e.verify_create_btn().isDisplayed(), "Create button not displayed");
		test.pass("Create button displayed");

		Assert.assertTrue(e.verify_top_search().isDisplayed(), "Search button not displayed");
		test.pass("Search button displayed");

		Assert.assertTrue(e.verify_table_filter().isDisplayed(), "Table Search: box not displayed");
		test.pass("Table Search: box displayed");

		Assert.assertTrue(e.verify_entries_select().isDisplayed(), "Show entries drop down not displayed");
		test.pass("Show entries drop down displayed");

		for (String header : new String[] { "HIERARCHY NAME", "EMP CODE", "EMPLOYEE NAME", "EMPLOYEE TYPE", "STATE",
				"PHONE", "ACTION", "RESIGN" }) {
			Assert.assertTrue(e.verify_header(header).isDisplayed(), "Column header not displayed: " + header);
			test.pass("Column header displayed: " + header);
		}

		Assert.assertTrue(e.verify_entries().isDisplayed(), "Entries text not displayed");
		test.pass("Entries text: " + e.verify_entries().getText());

		Assert.assertTrue(e.verify_previous().isDisplayed() && e.verify_next().isDisplayed(),
				"Pagination (Previous / Next) not displayed");
		test.pass("Pagination displayed");

		Assert.assertTrue(e.result_count() >= 1, "Employee table has no rows");
		test.pass("Employee table shows " + e.result_count() + " row(s)");
	}

	// ======================================================================
	// SCENARIO 2: search form elements
	// Clicking Search opens the form: employee code, employee name, hierarchy type, hierarchy name, Search.
	// ======================================================================
	@Test(priority = 2)
	public void s02_verify_search_form() {

		test = extent.createTest("S02 Search Form Verification");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		e.open_search_form();

		Assert.assertTrue(e.verify_search_code().isDisplayed(), "Employee Code box not displayed");
		test.pass("Employee Code box displayed");

		Assert.assertTrue(e.verify_search_employee_name().isDisplayed(), "Employee Name box not displayed");
		test.pass("Employee Name box displayed");

		Assert.assertTrue(e.verify_search_hierarchy_type().isDisplayed(), "Hierarchy Type drop down not displayed");
		test.pass("Hierarchy Type drop down displayed");

		Assert.assertTrue(e.verify_search_hierarchy_name().isDisplayed(), "Hierarchy Name box not displayed");
		test.pass("Hierarchy Name box displayed");

		Assert.assertTrue(e.verify_search_go().isDisplayed(), "Search button of the form not displayed");
		test.pass("Search button of the form displayed");
	}

	// ======================================================================
	// SCENARIO 3: search by employee code
	// SE10 returns exactly one row: YAZER P SHERIF.
	// ======================================================================
	@Test(priority = 3)
	public void s03_search_by_code() throws InterruptedException {

		test = extent.createTest("S03 Search By Employee Code");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		e.Search_employee("SE10", "");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + e.result_texts());

		Assert.assertEquals(e.result_count(), 1, "SE10 should return exactly 1 row");
		Assert.assertTrue(e.result_texts().get(0).toUpperCase().contains("YAZER"), "SE10 row is not YAZER P SHERIF");
		test.pass("SE10 found: 1 row, YAZER P SHERIF");
	}

	// ======================================================================
	// SCENARIO 4: search by hierarchy type
	// PLANT returns the employees of the PLANT hierarchy (GOODWILL POLYMERS FSS 2026-2027).
	// ======================================================================
	@Test(priority = 4)
	public void s04_search_by_hierarchy_type() throws InterruptedException {

		test = extent.createTest("S04 Search By Hierarchy Type");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		e.Search_employee("", "PLANT");
		WaitUtil.settle(driver, 2000);
		List<String> rows = e.result_texts();
		test.info("Rows shown: " + rows);

		Assert.assertTrue(e.result_count() >= 1, "Hierarchy type search returned nothing");
		for (String row : rows) {
			Assert.assertTrue(row.toUpperCase().contains("GOODWILL POLYMERS FSS 2026-2027"),
					"A row is not in the PLANT hierarchy: " + row);
		}
		test.pass("Every returned row belongs to the PLANT hierarchy (" + e.result_count() + " row(s))");
	}

	// ======================================================================
	// SCENARIO 5: combined search
	// SE10 with the type PLANT returns the row; SE10 with another type returns nothing.
	// ======================================================================
	@Test(priority = 5)
	public void s05_combined_search() throws InterruptedException {

		test = extent.createTest("S05 Combined Search");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		e.Search_employee("SE10", "PLANT");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown (matching values): " + e.result_texts());
		Assert.assertEquals(e.result_count(), 1, "SE10 with PLANT should return 1 row");
		test.pass("SE10 with PLANT returned 1 row");

		e.Search_employee("SE10", "SALES ORGANIZATION");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown (code with the wrong type): " + e.result_texts());
		Assert.assertEquals(e.result_count(), 0, "SE10 with the wrong type should return 0 rows");
		test.pass("SE10 with the wrong type returned 0 rows");
	}

	// ======================================================================
	// SCENARIO 6: search for something that does not exist
	// Must return no rows.
	// ======================================================================
	@Test(priority = 6)
	public void s06_search_not_found() throws InterruptedException {

		test = extent.createTest("S06 Search Not Found");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		e.Search_employee("ZZZ999", "");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + e.result_texts());

		Assert.assertEquals(e.result_count(), 0, "A non existing code returned rows");
		test.pass("Non existing code returned 0 rows");
	}

	// ======================================================================
	// SCENARIO 7: the "Search:" box above the table
	// It filters the rows: SE10 leaves one row, a nonsense text leaves none.
	// ======================================================================
	@Test(priority = 7)
	public void s07_table_filter() throws InterruptedException {

		test = extent.createTest("S07 Table Search Box");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		e.Filter_table("SE10");
		WaitUtil.settle(driver, 1000);
		test.info("Rows shown for SE10: " + e.result_texts());
		Assert.assertEquals(e.result_count(), 1, "SE10 should leave exactly 1 row");
		test.pass("SE10 filter left 1 row");

		e.Filter_table("ZZZ999");
		WaitUtil.settle(driver, 1000);
		test.info("Rows shown for ZZZ999: " + e.result_texts());
		Assert.assertEquals(e.result_count(), 0, "A nonsense text should leave 0 rows");
		test.pass("Nonsense text left 0 rows");
	}

	// ======================================================================
	// SCENARIO 8: Show entries
	// With 10 entries shown there are at most 10 rows; with 25 all employees (at least 11) are shown.
	// ======================================================================
	@Test(priority = 8)
	public void s08_show_entries() throws InterruptedException {

		test = extent.createTest("S08 Show Entries");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		Assert.assertTrue(e.result_count() <= 10, "More than 10 rows are shown by default");
		test.pass("Default: " + e.result_count() + " rows (at most 10)");

		// the drop down is read first: it may not contain 25 (the largest option is chosen instead)
		test.info("Show entries options: " + e.entries_options());
		String chosen = e.Select_largest_entries();
		WaitUtil.settle(driver, 1500);
		test.info("Rows shown with " + chosen + " entries: " + e.result_count());
		Assert.assertTrue(e.result_count() >= 11, "Choosing " + chosen + " entries did not show all the employees");
		test.pass(chosen + " entries shows " + e.result_count() + " rows");
	}

	// ======================================================================
	// SCENARIO 9: pagination
	// Next shows page 2 (a different first row), Previous returns to page 1.
	// ======================================================================
	@Test(priority = 9)
	public void s09_pagination() throws InterruptedException {

		test = extent.createTest("S09 Pagination");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		String page_one = e.first_row_code();
		e.Click_next();
		WaitUtil.settle(driver, 1000);
		String page_two = e.first_row_code();
		test.info("First code on page 1: " + page_one + ", on page 2: " + page_two);
		Assert.assertNotEquals(page_two, page_one, "Next did not show another page");
		test.pass("Next showed page 2");

		e.Click_previous();
		WaitUtil.settle(driver, 1000);
		Assert.assertEquals(e.first_row_code(), page_one, "Previous did not return to page 1");
		test.pass("Previous returned to page 1");
	}

	// ======================================================================
	// SCENARIO 10: sorting
	// Clicking the EMP CODE header flips the order, clicking again restores it.
	// ======================================================================
	@Test(priority = 10)
	public void s10_sort_by_code() throws InterruptedException {

		test = extent.createTest("S10 Sort By Employee Code");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		String original = e.first_row_code();
		e.Click_header("EMP CODE");
		WaitUtil.settle(driver, 1000);
		String flipped = e.first_row_code();
		test.info("First code before: " + original + ", after one click: " + flipped);
		Assert.assertNotEquals(flipped, original, "Clicking the EMP CODE header did not change the order");
		test.pass("First click changed the order");

		e.Click_header("EMP CODE");
		WaitUtil.settle(driver, 1000);
		Assert.assertNotEquals(e.first_row_code(), flipped, "A second click did not change the order again");
		test.pass("Second click changed the order again");
	}

	// ======================================================================
	// SCENARIO 11: view and edit icons
	// The eye opens the details of SE10, the pencil opens the form filled with SE10 and Back returns to the list.
	// ======================================================================
	@Test(priority = 11)
	public void s11_view_and_edit_icons() throws InterruptedException {

		test = extent.createTest("S11 View And Edit Icons");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		e.Filter_table("SE10");
		WaitUtil.settle(driver, 1000);

		e.Click_view("SE10");
		WaitUtil.settle(driver, 2000);
		Assert.assertFalse(e.is_list_page(), "The view icon did not leave the list screen");
		Assert.assertEquals(e.code_value(), "SE10", "The details screen shows a different employee code");
		test.pass("View opened the details of SE10");

		open_employee();   // back to the list
		e.Filter_table("SE10");
		WaitUtil.settle(driver, 1000);

		e.Click_edit("SE10");
		WaitUtil.settle(driver, 2000);
		Assert.assertFalse(e.is_list_page(), "The edit icon did not leave the list screen");
		Assert.assertEquals(e.code_value(), "SE10", "The edit form shows a different employee code");
		Assert.assertTrue(e.verify_submit().isDisplayed(), "Submit not displayed on the edit form");
		test.pass("Edit form opened and shows SE10 (nothing was changed)");

		e.Click_back();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(e.is_list_page(), "Back did not return to the list");
		test.pass("Back returned to the list");
	}

	// ======================================================================
	// SCENARIO 12: Resign buttons
	// Every row has a Resign button. The button is only checked, never clicked.
	// ======================================================================
	@Test(priority = 12)
	public void s12_resign_buttons() {

		test = extent.createTest("S12 Resign Buttons");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		int rows = e.result_count();
		int resign = e.resign_button_count();
		test.info("Rows: " + rows + ", Resign buttons: " + resign);

		Assert.assertTrue(rows >= 1, "No rows in the table");
		Assert.assertEquals(resign, rows, "Not every row has a Resign button");
		test.pass("Every row has a Resign button");
	}

	// ======================================================================
	// SCENARIO 13: create form elements
	// Create opens the form and every field, drop down, radio button, check box and button is displayed.
	// ======================================================================
	@Test(priority = 13)
	public void s13_verify_create_form() throws InterruptedException {

		test = extent.createTest("S13 Create Form Verification");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);

		String[] labels = { "Employee Code", "First Name", "Last Name", "Address", "Gender", "Email/User Name",
				"Phone Number", "PF Number", "Country", "State", "Zip Code", "Date of Birth", "Blood Group",
				"Aadhaar Number", "Marital Status", "Billing Code", "Employee Type", "Designation", "Add New",
				"Employee Profile search", "Is Web User Yes", "Is Web User No", "Role", "Is Active",
				"Belongs To Hierarchy Type", "Belongs To Hierarchy Name", "Back", "Submit" };

		WebElement[] elements = { e.verify_code(), e.verify_first_name(), e.verify_last_name(), e.verify_address(),
				e.verify_gender(), e.verify_email(), e.verify_phone(), e.verify_pf(), e.verify_country(),
				e.verify_state(), e.verify_zip(), e.verify_dob(), e.verify_blood_group(), e.verify_aadhaar(),
				e.verify_marital_status(), e.verify_billing(), e.verify_employee_type(), e.verify_designation(),
				e.verify_add_new(), e.verify_profile_search(), e.verify_web_user_yes(), e.verify_web_user_no(),
				e.verify_role(), e.verify_active(), e.verify_hierarchy_type(), e.verify_hierarchy_name(),
				e.verify_back(), e.verify_submit() };

		for (int i = 0; i < labels.length; i++) {
			Assert.assertTrue(elements[i].isDisplayed(), labels[i] + " not displayed");
			test.pass(labels[i] + " displayed");
		}
	}

	// ======================================================================
	// SCENARIO 14: submit the EMPTY form
	// Every mandatory field shows its own message and nothing is saved.
	// The Employee Type message on the site says "hierarchy type": it is logged as an observation.
	// ======================================================================
	@Test(priority = 14)
	public void s14_empty_form_shows_messages() throws InterruptedException {

		test = extent.createTest("S14 Empty Form Shows Mandatory Messages");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);
		e.Click_submit();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(e.is_create_form(), "Empty form was accepted, the create form closed");
		test.pass("Empty form rejected, still on the create form");

		List<String> messages = e.validation_messages();
		test.info("Messages shown: " + messages);

		String shown = plain(messages.toString());
		for (String expected : REQUIRED_MESSAGES) {
			Assert.assertTrue(shown.contains(plain(expected)), "Message missing: " + expected);
			test.pass("Message shown: " + expected);
		}

		int hierarchy_type_messages = shown.split("please select a hierarchy type", -1).length - 1;
		test.info("'Please select a hierarchy type' appears " + hierarchy_type_messages
				+ " time(s). It is shown for both Employee Type and Hierarchy Type: the Employee Type message"
				+ " should probably say 'employee type'.");
	}

	// ======================================================================
	// SCENARIO 15: Back button
	// Back on the create form returns to the list without saving anything.
	// ======================================================================
	@Test(priority = 15)
	public void s15_back_button() throws InterruptedException {

		test = extent.createTest("S15 Back Button Returns To List");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);
		e.Click_back();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(e.is_list_page(), "Back did not return to the list page");
		test.pass("Back returned to the list page");
	}

	// ======================================================================
	// SCENARIO 16: drop down options
	// Marital Status, Employee Type and Designation offer the options seen on the screen.
	// ======================================================================
	@Test(priority = 16)
	public void s16_dropdown_options() throws InterruptedException {

		test = extent.createTest("S16 Drop Down Options");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);

		List<String> gender = e.gender_options();
		test.info("Gender options: " + gender);
		Assert.assertFalse(gender.isEmpty(), "Gender has no options");
		test.pass("Gender offers " + gender.size() + " option(s)");

		String marital = e.marital_options().toString();
		test.info("Marital Status options: " + marital);
		Assert.assertTrue(marital.contains("Single") && marital.contains("Married"), "Marital Status options are wrong");
		test.pass("Marital Status offers Single and Married");

		String types = e.employee_type_options().toString();
		test.info("Employee Type options: " + types);
		for (String expected : new String[] { "Sales Executive", "sales manager", "Zone Officer", "Distributor User" }) {
			Assert.assertTrue(types.toLowerCase().contains(expected.toLowerCase()), "Employee Type missing: " + expected);
		}
		test.pass("Employee Type offers Sales Executive and the other types");

		String designations = e.designation_options().toString();
		test.info("Designation options: " + designations);
		for (String expected : new String[] { "Manager", "Sales Executive", "Driver" }) {
			Assert.assertTrue(designations.contains(expected), "Designation missing: " + expected);
		}
		test.pass("Designation offers Manager, Sales Executive and Driver");
	}

	// ======================================================================
	// SCENARIO 17: Is Web User controls the Role drop down
	// Default is No and Role is disabled; Yes enables Role; No disables it again.
	// ======================================================================
	@Test(priority = 17)
	public void s17_web_user_and_role() throws InterruptedException {

		test = extent.createTest("S17 Is Web User And Role");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);

		Assert.assertTrue(e.verify_web_user_no().isSelected(), "Is Web User is not No by default");
		Assert.assertFalse(e.is_role_enabled(), "Role is enabled while Is Web User is No");
		test.pass("Default: Is Web User = No and Role is disabled");

		e.Select_web_user(true);
		WaitUtil.settle(driver, 1000);
		Assert.assertTrue(e.verify_web_user_yes().isSelected(), "Yes did not get selected");
		Assert.assertTrue(e.is_role_enabled(), "Role is still disabled after choosing Yes");
		test.info("Role options: " + e.role_options());
		test.pass("Is Web User = Yes enables Role");

		e.Select_web_user(false);
		WaitUtil.settle(driver, 1000);
		Assert.assertFalse(e.is_role_enabled(), "Role is still enabled after choosing No");
		test.pass("Is Web User = No disables Role again");
	}

	// ======================================================================
	// SCENARIO 18: Is Active check box
	// Ticked by default; it can be unticked.
	// ======================================================================
	@Test(priority = 18)
	public void s18_is_active() throws InterruptedException {

		test = extent.createTest("S18 Is Active Check Box");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);

		Assert.assertTrue(e.verify_active().isSelected(), "Is Active is not ticked by default");
		test.pass("Is Active is ticked by default");

		e.Click_active();
		WaitUtil.settle(driver, 500);
		Assert.assertFalse(e.verify_active().isSelected(), "Is Active could not be unticked");
		test.pass("Is Active can be unticked");
	}

	// ======================================================================
	// SCENARIO 19: Belongs To: hierarchy name suggestions
	// After choosing the hierarchy type PLANT and typing GOODWILL, the hierarchy
	// GOODWILL POLYMERS FSS 2026-2027 is offered.
	// ======================================================================
	@Test(priority = 19)
	public void s19_hierarchy_name_suggestions() throws InterruptedException {

		test = extent.createTest("S19 Hierarchy Name Suggestions");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);

		List<String> suggestions = e.hierarchy_suggestions(E_Employee_Cls.HIERARCHY_TYPE, E_Employee_Cls.HIERARCHY_NAME);
		test.info("Suggestions shown: " + suggestions);

		Assert.assertFalse(suggestions.isEmpty(), "No suggestions appeared (the suggestion locator may need adjusting)");
		Assert.assertTrue(suggestions.toString().toUpperCase().contains(E_Employee_Cls.HIERARCHY_NAME),
				"The hierarchy " + E_Employee_Cls.HIERARCHY_NAME + " was not offered");
		test.pass("The hierarchy " + E_Employee_Cls.HIERARCHY_NAME + " is offered");
	}

	// ======================================================================
	// SCENARIO 20: State list depends on Country
	// After a country is chosen the State drop down has options.
	// ======================================================================
	@Test(priority = 20)
	public void s20_state_depends_on_country() throws InterruptedException {

		test = extent.createTest("S20 State List Depends On Country");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);
		e.Select_country_first();
		WaitUtil.settle(driver, 1500);

		int states = e.state_option_count();
		test.info("State options after choosing a country: " + states);
		Assert.assertTrue(states >= 1, "No states offered after choosing a country");
		test.pass("State list filled after choosing a country (" + states + " states)");
	}

	// Rows for scenario 21: description, email, phone, zip, aadhaar (one value is wrong in each row)
	@DataProvider(name = "invalid_values")
	public Object[][] invalidValues() {
		return new Object[][] {
				{ "Invalid email (no @)", "notanemail", "9876543210", "682001", "123412341234" },
				{ "Phone too short", "auto@test.com", "12345", "682001", "123412341234" },
				{ "Phone with letters", "auto@test.com", "98abc43210", "682001", "123412341234" },
				{ "Zip with letters", "auto@test.com", "9876543210", "abcdef", "123412341234" },
				{ "Aadhaar with letters", "auto@test.com", "9876543210", "682001", "abcdabcdabcd" } };
	}

	// ======================================================================
	// SCENARIO 21: invalid values in one field
	// Each row has exactly one wrong value. The form must be rejected (it stays open).
	// If the site ACCEPTS one of these, that is a validation defect to report.
	// ======================================================================
	@Test(priority = 21, dataProvider = "invalid_values")
	public void s21_invalid_values(String description, String email, String phone, String zip, String aadhaar)
			throws InterruptedException {

		test = extent.createTest("S21 " + description);

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);
		e.Fill_mandatory(unique("EM"), "Auto", "Employee", email, "Auto Address");
		e.Fill_optional(phone, "PF12345", zip, "O+", aadhaar);
		e.Click_submit();
		WaitUtil.settle(driver, 2000);

		test.info("Messages shown: " + e.validation_messages());
		Assert.assertTrue(e.is_create_form(), "DEFECT: the site accepted: " + description);
		test.pass(description + ": rejected, still on the form");
	}

	// ======================================================================
	// SCENARIO 22: create a valid employee (all fields)
	// Employee Type and Designation are Sales Executive, Is Web User = No.
	// The form must be accepted (it closes or a message appears). The code is kept for scenarios 23 and 24.
	// ======================================================================
	@Test(priority = 22)
	public void s22_create_valid_employee() throws InterruptedException {

		test = extent.createTest("S22 Create Valid Employee");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		String stamp = unique("");
		created_code = "EM" + stamp;

		open_create(e);
		test.info("Creating employee " + created_code);
		e.Fill_form(created_code, "Auto", "Employee", "auto" + stamp + "@test.com", "Auto Address");
		test.info("Billing code used: " + e.last_billing_code);

		e.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = e.get_message();
		test.info("Message after submit: '" + message + "'");
		test.info("Validation messages: " + e.validation_messages());

		Assert.assertTrue(!e.is_create_form() || !message.isEmpty(),
				"Submit did not give any response. Messages: " + e.validation_messages());
		test.pass("Employee submitted: " + created_code);
	}

	// ======================================================================
	// SCENARIO 23: search the employee that was just created
	// ASSUMPTION: a created employee is visible in the list (as hierarchies are). If it is not,
	// change the expected count to 0.
	// ======================================================================
	@Test(priority = 23, dependsOnMethods = "s22_create_valid_employee")
	public void s23_search_created_employee() throws InterruptedException {

		test = extent.createTest("S23 Search Created Employee");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		e.Search_employee(created_code, "");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + e.result_texts());

		Assert.assertEquals(e.result_count(), 1, "The new employee " + created_code + " was not found in the list");
		test.pass("The new employee " + created_code + " is in the list");
	}

	// ======================================================================
	// SCENARIO 24: duplicate employee code
	// A second employee with the SAME code must be rejected: the form stays open or a message appears.
	// ======================================================================
	@Test(priority = 24, dependsOnMethods = "s22_create_valid_employee")
	public void s24_duplicate_code() throws InterruptedException {

		test = extent.createTest("S24 Duplicate Employee Code");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);
		e.Fill_form(created_code, "Auto", "Duplicate", "dup" + unique("") + "@test.com", "Auto Address");
		e.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = e.get_message();
		test.info("Message after submit: '" + message + "'");
		test.info("Validation messages: " + e.validation_messages());

		Assert.assertTrue(e.is_create_form() || !message.isEmpty(),
				"DEFECT: the duplicate employee code " + created_code + " was accepted without any message");
		test.pass("Duplicate employee code was not silently accepted");
	}

	// ======================================================================
	// SCENARIO 25: only mandatory fields (optional fields left empty)
	// Phone, PF, zip, date of birth, blood group, aadhaar and marital status are optional,
	// so the employee must still be accepted.
	// ======================================================================
	@Test(priority = 25)
	public void s25_optional_fields_blank() throws InterruptedException {

		test = extent.createTest("S25 Create With Optional Fields Blank");

		E_Employee_Cls e = new E_Employee_Cls(driver);
		String stamp = unique("");

		open_create(e);
		e.Fill_mandatory("EM" + stamp, "Auto", "Optional", "opt" + stamp + "@test.com", "Auto Address");
		e.Click_submit();
		WaitUtil.settle(driver, 3000);

		Assert.assertTrue(!e.is_create_form() || !e.get_message().isEmpty(),
				"Employee with optional fields blank was rejected. Messages: " + e.validation_messages());
		test.pass("Employee accepted without the optional fields: EM" + stamp);
	}

	// ======================================================================
	// SCENARIO 26: create a web user (Is Web User = Yes with a Role)
	// Choosing Yes enables Role; the first role is chosen and the employee must be accepted.
	// ======================================================================
	@Test(priority = 26)
	public void s26_create_web_user() throws InterruptedException {

		test = extent.createTest("S26 Create Web User With Role");

		E_Employee_Cls e = new E_Employee_Cls(driver);
		String stamp = unique("");

		open_create(e);
		e.Fill_mandatory("EM" + stamp, "Auto", "WebUser", "web" + stamp + "@test.com", "Auto Address");

		e.Select_web_user(true);
		WaitUtil.settle(driver, 1000);
		Assert.assertTrue(e.is_role_enabled(), "Role is disabled although Is Web User is Yes");
		e.Select_first_role();
		test.info("Role options: " + e.role_options());

		e.Click_submit();
		WaitUtil.settle(driver, 3000);

		Assert.assertTrue(!e.is_create_form() || !e.get_message().isEmpty(),
				"Web user was rejected. Messages: " + e.validation_messages());
		test.pass("Web user accepted: EM" + stamp);
	}

	// ======================================================================
	// SCENARIO 27: "Designations" Add New pop-up
	// Add New opens a pop-up with a designation box and Submit; an empty Submit keeps it open; X closes it.
	// (No designation is created, so no data is added.)
	// ======================================================================
	@Test(priority = 27)
	public void s27_add_designation_popup() throws InterruptedException {

		test = extent.createTest("S27 Add New Designation Pop-up");

		E_Employee_Cls e = new E_Employee_Cls(driver);

		open_create(e);
		e.Click_add_new();
		WaitUtil.settle(driver, 1000);

		Assert.assertTrue(e.is_designation_popup_open(), "The Add New pop-up did not open");
		test.pass("Add New pop-up opened");

		Assert.assertTrue(e.verify_popup_input().isDisplayed(), "Designation box not displayed");
		Assert.assertTrue(e.verify_popup_submit().isDisplayed(), "Pop-up Submit not displayed");
		test.pass("Designation box and Submit displayed");

		e.Click_popup_submit();
		WaitUtil.settle(driver, 1000);
		Assert.assertTrue(e.is_designation_popup_open(), "An empty designation was accepted (pop-up closed)");
		test.pass("Empty designation rejected, the pop-up stayed open. Messages: " + e.validation_messages());

		e.Close_popup();
		WaitUtil.settle(driver, 1000);
		Assert.assertFalse(e.is_designation_popup_open(), "The X did not close the pop-up");
		test.pass("X closed the pop-up");
	}
}