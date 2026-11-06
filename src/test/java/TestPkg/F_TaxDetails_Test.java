package TestPkg;

import UtillPkg.WaitUtil;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;
import PagePkg.F_TaxDetails_Cls;

/*
 * NOTES - F_TaxDetails_Test
 * -----------------------
 * Stock Item > Tax Details. UI checks only (no database).
 * A tax profile has a name, EXACTLY 3 tax rows (code, name, value, InState / OutState) and an
 * optional Additional Tax. Seed profiles: RST@10 and GST@18% (they are only READ, never edited or deleted).
 * Every profile created by the tests has a name starting with "TX". The last scenario deletes every
 * profile starting with TX (also leftovers of earlier runs), so the QA data is left as it was.
 * The site reports form errors ONE STEP AT A TIME: first the profile name, then the codes, then the values.
 * After a search the table refreshes a moment later, so the tests wait for the expected row to appear.
 * Each test starts from the Tax Details list screen (see @BeforeMethod).
 */
public class F_TaxDetails_Test extends BaseCls {

	String dash_url;

	// the profiles used by the search, view, edit and duplicate scenarios
	String main_profile;
	String main_stamp;
	String additional_profile;

	// a unique value per call: the number changes every 0.1 second and repeats only after about 115 days
	private String unique(String prefix) {
		return prefix + (System.currentTimeMillis() / 100) % 10000000;
	}

	// counts how many times a part of a message appears in the list of messages (capital letters ignored)
	private int count_of(List<String> messages, String part) {
		int count = 0;
		for (String message : messages) {
			if (message.toLowerCase().contains(part.toLowerCase())) {
				count++;
			}
		}
		return count;
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
		Assert.assertTrue(login.login_success(), "Could not log in for Tax Details tests");

		dash_url = driver.getCurrentUrl();
	}

	// opens Stock Item > Tax Details from the dashboard
	private void go_to_tax_details() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		try {
			dash.Click_submenu_by_name("Stock Item", "Tax Details");
		} catch (NoSuchElementException e) {
			dash.Click_home();
			dash.Click_submenu_containing("Stock Item", "Tax");
		}
		WaitUtil.settle(driver, 1500);
	}

	// runs before EVERY test: open the Tax Details list screen (log in again if the session broke)
	@BeforeMethod
	public void open_tax_details() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		try {
			go_to_tax_details();
		} catch (Exception e) {
			login_first();
			go_to_tax_details();
		}
		remember_clean_screen();
	}

	// opens the create form from the list screen
	private void open_create(F_TaxDetails_Cls t) throws InterruptedException {
		t.Click_create();
		WaitUtil.settle(driver, 1500);
	}

	// fills a profile with three valid tax rows: codes, names and values (5, 10, 15) made from the stamp
	private void fill_valid_profile(F_TaxDetails_Cls t, String profile, String stamp) {
		t.Fill_profile(profile, new String[] { "C" + stamp + "1", "C" + stamp + "2", "C" + stamp + "3" },
				new String[] { "Auto Tax 1", "Auto Tax 2", "Auto Tax 3" }, new String[] { "5", "10", "15" });
	}

	// ======================================================================
	// SCENARIO 1: list screen elements
	// Buttons, table filter, the 3 column headers, entries text, pagination, the two seed profiles
	// and the three icons (view, edit, delete) on each seed row.
	// ======================================================================
	@Test(priority = 1)
	public void s01_verify_list_page() {

		test = extent.createTest("S01 Tax Details List Page Verification");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		Assert.assertTrue(t.verify_create_btn().isDisplayed(), "Create button not displayed");
		test.pass("Create button displayed");

		Assert.assertTrue(t.verify_top_search().isDisplayed(), "Search button not displayed");
		test.pass("Search button displayed");

		Assert.assertTrue(t.verify_table_filter().isDisplayed(), "Table Search: box not displayed");
		test.pass("Table Search: box displayed");

		for (String header : new String[] { "TAX PROFILE NAME", "IS ADDITIONAL TAX ADDED", "ACTION" }) {
			Assert.assertTrue(t.verify_header(header).isDisplayed(), "Column header not displayed: " + header);
			test.pass("Column header displayed: " + header);
		}

		Assert.assertTrue(t.verify_entries().isDisplayed(), "Entries text not displayed");
		test.pass("Entries text: " + t.verify_entries().getText());

		Assert.assertTrue(t.verify_previous().isDisplayed() && t.verify_next().isDisplayed(),
				"Pagination (Previous / Next) not displayed");
		test.pass("Pagination displayed");

		String rows = t.result_texts().toString();
		test.info("Rows shown: " + rows);
		Assert.assertTrue(rows.contains("RST@10"), "Seed profile RST@10 not in the table");
		Assert.assertTrue(rows.contains("GST@18%"), "Seed profile GST@18% not in the table");
		test.pass("Seed profiles RST@10 and GST@18% are in the table");

		Assert.assertEquals(t.icon_count("RST@10"), 3, "RST@10 should have a view, edit and delete icon");
		Assert.assertEquals(t.icon_count("GST@18%"), 3, "GST@18% should have a view, edit and delete icon");
		test.pass("Each seed row has 3 icons (view, edit, delete)");
	}

	// ======================================================================
	// SCENARIO 2: search form elements
	// Clicking Search opens the form: the Tax Profile Name box and its Search button.
	// ======================================================================
	@Test(priority = 2)
	public void s02_verify_search_form() {

		test = extent.createTest("S02 Search Form Verification");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		t.open_search_form();

		Assert.assertTrue(t.verify_search_input().isDisplayed(), "Tax Profile Name box not displayed");
		test.pass("Tax Profile Name box displayed");

		Assert.assertTrue(t.verify_search_go().isDisplayed(), "Search button of the form not displayed");
		test.pass("Search button of the form displayed");
	}

	// ======================================================================
	// SCENARIO 3: search by profile name
	// RST@10 returns exactly one row; the part "GST" returns the GST@18% profile.
	// ======================================================================
	@Test(priority = 3)
	public void s03_search_by_name() throws InterruptedException {

		test = extent.createTest("S03 Search By Profile Name");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		t.Search_profile("RST@10");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown for RST@10: " + t.result_texts());
		Assert.assertEquals(t.result_count(), 1, "RST@10 should return exactly 1 row");
		Assert.assertTrue(t.result_texts().get(0).contains("RST@10"), "The row is not RST@10");
		test.pass("RST@10 found: 1 row");

		t.Search_profile("GST");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown for GST: " + t.result_texts());
		Assert.assertTrue(t.result_texts().toString().contains("GST@18%"), "GST@18% was not found by a part of its name");
		test.pass("A part of the name (GST) found GST@18%");
	}

	// ======================================================================
	// SCENARIO 4: search for something that does not exist
	// Must return no rows.
	// ======================================================================
	@Test(priority = 4)
	public void s04_search_not_found() throws InterruptedException {

		test = extent.createTest("S04 Search Not Found");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		t.Search_profile("ZZZ999");
		WaitUtil.settle(driver, 2000);
		test.info("Rows shown: " + t.result_texts());

		Assert.assertEquals(t.result_count(), 0, "A non existing profile returned rows");
		test.pass("Non existing profile returned 0 rows");
	}

	// ======================================================================
	// SCENARIO 5: the "Search:" box above the table
	// It filters the rows: RST leaves one row, a nonsense text leaves none.
	// ======================================================================
	@Test(priority = 5)
	public void s05_table_filter() throws InterruptedException {

		test = extent.createTest("S05 Table Search Box");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		t.Filter_table("RST");
		WaitUtil.settle(driver, 1000);
		test.info("Rows shown for RST: " + t.result_texts());
		Assert.assertEquals(t.result_count(), 1, "RST should leave exactly 1 row");
		test.pass("RST filter left 1 row");

		t.Filter_table("ZZZ999");
		WaitUtil.settle(driver, 1000);
		test.info("Rows shown for ZZZ999: " + t.result_texts());
		Assert.assertEquals(t.result_count(), 0, "A nonsense text should leave 0 rows");
		test.pass("Nonsense text left 0 rows");
	}

	// ======================================================================
	// SCENARIO 6: sorting
	// Clicking the TAX PROFILE NAME header flips the order, clicking again flips it back.
	// ======================================================================
	@Test(priority = 6)
	public void s06_sort_by_name() throws InterruptedException {

		test = extent.createTest("S06 Sort By Tax Profile Name");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		String original = t.first_row_name();
		t.Click_header("TAX PROFILE NAME");
		WaitUtil.settle(driver, 1000);
		String flipped = t.first_row_name();
		test.info("First profile before: " + original + ", after one click: " + flipped);
		Assert.assertNotEquals(flipped, original, "Clicking the header did not change the order");
		test.pass("First click changed the order");

		t.Click_header("TAX PROFILE NAME");
		WaitUtil.settle(driver, 1000);
		Assert.assertEquals(t.first_row_name(), original, "A second click did not restore the order");
		test.pass("Second click restored the order");
	}

	// ======================================================================
	// SCENARIO 7: view icon
	// The eye icon of RST@10 opens its details: the values of the 3 tax rows, the form is greyed out
	// (fields disabled, no Submit) and Back returns to the list.
	// ======================================================================
	@Test(priority = 7)
	public void s07_view_icon() throws InterruptedException {

		test = extent.createTest("S07 View Icon");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		t.Click_view("RST@10");
		WaitUtil.settle(driver, 2000);

		Assert.assertTrue(t.is_form_screen(), "The view icon did not open the details screen");
		test.pass("View opened the details screen");

		Assert.assertEquals(t.verify_profile_name().getAttribute("value"), "RST@10", "Wrong profile name");
		String[][] expected = { { "RST@10", "RST1", "10" }, { "RST@20", "RST2", "20" }, { "RST@30", "RST3", "30" } };
		for (int row = 1; row <= 3; row++) {
			Assert.assertEquals(t.verify_code(row).getAttribute("value"), expected[row - 1][0], "Wrong code in row " + row);
			Assert.assertEquals(t.verify_tax_name(row).getAttribute("value"), expected[row - 1][1], "Wrong name in row " + row);
			Assert.assertEquals(t.verify_value(row).getAttribute("value"), expected[row - 1][2], "Wrong value in row " + row);
		}
		test.pass("The 3 tax rows show the saved codes, names and values");

		Assert.assertTrue(t.verify_out_state(1).isSelected(), "Row 1 should be OutState");
		Assert.assertTrue(t.verify_in_state(2).isSelected(), "Row 2 should be InState");
		Assert.assertTrue(t.verify_in_state(3).isSelected(), "Row 3 should be InState");
		test.pass("InState / OutState show the saved choices");

		Assert.assertFalse(t.verify_profile_name().isEnabled(), "The profile name is editable on the view screen");
		Assert.assertFalse(t.verify_value(1).isEnabled(), "The value is editable on the view screen");
		Assert.assertFalse(t.is_submit_displayed(), "A Submit button is shown on the view screen");
		test.pass("View screen is read only (fields disabled, no Submit)");

		t.Click_back();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(t.is_list_page(), "Back did not return to the list");
		test.pass("Back returned to the list");
	}

	// ======================================================================
	// SCENARIO 8: edit icon
	// The pencil of RST@10 opens an editable form filled with its data and a Submit button.
	// Nothing is changed: Back is pressed.
	// ======================================================================
	@Test(priority = 8)
	public void s08_edit_icon() throws InterruptedException {

		test = extent.createTest("S08 Edit Icon");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		t.Click_edit("RST@10");
		WaitUtil.settle(driver, 2000);

		Assert.assertTrue(t.is_form_screen(), "The edit icon did not open the form");
		Assert.assertEquals(t.verify_profile_name().getAttribute("value"), "RST@10", "Wrong profile name");
		Assert.assertEquals(t.verify_code(2).getAttribute("value"), "RST@20", "Wrong code in row 2");
		test.pass("Edit form opened and shows RST@10");

		Assert.assertTrue(t.verify_value(1).isEnabled(), "The value is not editable on the edit screen");
		Assert.assertTrue(t.is_submit_displayed(), "Submit not displayed on the edit form");
		test.pass("Edit form is editable and has a Submit button");

		t.Click_back();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(t.is_list_page(), "Back did not return to the list");
		test.pass("Back returned to the list (nothing was changed)");
	}

	// ======================================================================
	// SCENARIO 9: create form elements
	// Profile name, the 3 tax rows, the Additional Tax block, plus / minus, Back and Submit.
	// ======================================================================
	@Test(priority = 9)
	public void s09_verify_create_form() throws InterruptedException {

		test = extent.createTest("S09 Create Form Verification");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		open_create(t);

		Assert.assertTrue(t.verify_profile_name().isDisplayed(), "Tax Profile Name not displayed");
		test.pass("Tax Profile Name displayed");

		for (int row = 1; row <= 3; row++) {
			Assert.assertTrue(t.verify_code(row).isDisplayed(), "Code of row " + row + " not displayed");
			Assert.assertTrue(t.verify_tax_name(row).isDisplayed(), "Name of row " + row + " not displayed");
			Assert.assertTrue(t.verify_value(row).isDisplayed(), "Value of row " + row + " not displayed");
			Assert.assertTrue(t.verify_in_state(row).isDisplayed() && t.verify_out_state(row).isDisplayed(),
					"InState / OutState of row " + row + " not displayed");
			test.pass("Row " + row + " displayed: code, name, value, InState, OutState");
		}

		Assert.assertTrue(t.verify_additional_name().isDisplayed(), "Additional Tax name not displayed");
		Assert.assertTrue(t.verify_additional_value().isDisplayed(), "Additional Tax value not displayed");
		Assert.assertTrue(t.verify_percentage().isDisplayed() && t.verify_amount().isDisplayed(),
				"Value In (Percentage / Amount) not displayed");
		Assert.assertTrue(t.verify_how_to_apply().isDisplayed(), "How to Apply not displayed");
		for (int n = 1; n <= 3; n++) {
			Assert.assertTrue(t.verify_applicable(n).isDisplayed(), "Applicable for check box " + n + " not displayed");
		}
		test.pass("Additional Tax block displayed: name, value, Value In, How to Apply, 3 Applicable for check boxes");

		Assert.assertTrue(t.verify_plus().isDisplayed(), "Plus icon not displayed");
		Assert.assertTrue(t.verify_minus().isDisplayed(), "Minus icon not displayed");
		test.pass("Plus and minus icons displayed");

		Assert.assertTrue(t.verify_back().isDisplayed(), "Back not displayed");
		Assert.assertTrue(t.verify_submit().isDisplayed(), "Submit not displayed");
		test.pass("Back and Submit displayed");
	}

	// ======================================================================
	// SCENARIO 10: default choices
	// Row 1 is OutState, rows 2 and 3 are InState, Value In is Amount, no Applicable for box is ticked.
	// ======================================================================
	@Test(priority = 10)
	public void s10_default_choices() throws InterruptedException {

		test = extent.createTest("S10 Default Choices");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		open_create(t);

		Assert.assertTrue(t.verify_out_state(1).isSelected(), "Row 1 is not OutState by default");
		Assert.assertTrue(t.verify_in_state(2).isSelected(), "Row 2 is not InState by default");
		Assert.assertTrue(t.verify_in_state(3).isSelected(), "Row 3 is not InState by default");
		test.pass("Row 1 = OutState, rows 2 and 3 = InState by default");

		Assert.assertTrue(t.verify_amount().isSelected(), "Value In is not Amount by default");
		test.pass("Value In = Amount by default");

		for (int n = 1; n <= 3; n++) {
			Assert.assertFalse(t.verify_applicable(n).isSelected(), "Applicable for box " + n + " is ticked by default");
		}
		test.pass("No Applicable for box is ticked by default");
	}

	// ======================================================================
	// SCENARIO 11: submit the EMPTY form
	// Only "Please Enter Profile Name" is shown: the site reports errors one step at a time.
	// ======================================================================
	@Test(priority = 11)
	public void s11_empty_form_shows_message() throws InterruptedException {

		test = extent.createTest("S11 Empty Form Shows Profile Name Message");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		open_create(t);
		t.Click_submit();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(t.is_form_screen(), "Empty form was accepted, the form closed");
		test.pass("Empty form rejected, still on the form");

		List<String> messages = t.validation_messages();
		test.info("Messages shown: " + messages);
		Assert.assertEquals(count_of(messages, "please enter profile name"), 1, "Profile name message missing");
		test.pass("Profile name message shown");
		test.info("OBSERVATION: the Code and Value messages are not shown together with it. They appear one step"
				+ " at a time, after the profile name (then the codes) are filled.");
	}

	// ======================================================================
	// SCENARIO 11b: profile name filled, codes empty
	// "Please Enter Code" is shown for each of the 3 rows.
	// ======================================================================
	@Test(priority = 11)
	public void s11b_codes_missing() throws InterruptedException {

		test = extent.createTest("S11b Codes Missing");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		open_create(t);
		t.Fill_profile("TX" + unique(""), new String[] { "", "", "" }, new String[] { "", "", "" },
				new String[] { "", "", "" });
		t.Click_submit();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(t.is_form_screen(), "A profile without codes was accepted");
		List<String> messages = t.validation_messages();
		test.info("Messages shown: " + messages);
		Assert.assertEquals(count_of(messages, "please enter code"), 3, "There should be a Code message for each of the 3 rows");
		test.pass("3 'Please Enter Code' messages shown");
	}

	// ======================================================================
	// SCENARIO 12: values missing
	// With the profile name, codes and names filled and the values empty,
	// "Please Enter The Value" appears for each of the 3 rows.
	// ======================================================================
	@Test(priority = 12)
	public void s12_values_missing() throws InterruptedException {

		test = extent.createTest("S12 Values Missing");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);
		String stamp = unique("");

		open_create(t);
		t.Fill_profile("TX" + stamp, new String[] { "C" + stamp + "1", "C" + stamp + "2", "C" + stamp + "3" },
				new String[] { "Auto Tax 1", "Auto Tax 2", "Auto Tax 3" }, new String[] { "", "", "" });
		t.Click_submit();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(t.is_form_screen(), "A profile without values was accepted");
		test.pass("Profile without values rejected");

		List<String> messages = t.validation_messages();
		test.info("Messages shown: " + messages);
		Assert.assertEquals(count_of(messages, "please enter the value"), 3, "There should be a Value message for each of the 3 rows");
		test.pass("3 'Please Enter The Value' messages shown");
	}

	// ======================================================================
	// SCENARIO 13: Back button
	// Back on the form returns to the list without saving anything.
	// ======================================================================
	@Test(priority = 13)
	public void s13_back_button() throws InterruptedException {

		test = extent.createTest("S13 Back Button Returns To List");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		open_create(t);
		t.Click_back();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(t.is_list_page(), "Back did not return to the list page");
		test.pass("Back returned to the list page");
	}

	// ======================================================================
	// SCENARIO 14: "Applicable for" labels
	// The three check boxes must be labelled Tax1, Tax2 and Tax3. In the screenshots the third one reads Tax2.
	// ======================================================================
	@Test(priority = 14)
	public void s14_applicable_labels() throws InterruptedException {

		test = extent.createTest("S14 Applicable For Labels");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		open_create(t);
		List<String> labels = t.applicable_labels();
		test.info("Labels shown: " + labels);

		Assert.assertEquals(labels.toString(), "[Tax1, Tax2, Tax3]",
				"DEFECT: the Applicable for labels should be Tax1, Tax2, Tax3 but are " + labels);
		test.pass("Labels are Tax1, Tax2, Tax3");
	}

	// ======================================================================
	// SCENARIO 15: radio buttons and check boxes
	// For each tax row exactly one of InState / OutState is always selected (what a click does is logged,
	// because the rows may be fixed by design). Percentage and Amount exclude each other.
	// ======================================================================
	@Test(priority = 15)
	public void s15_radio_buttons() throws InterruptedException {

		test = extent.createTest("S15 Radio Buttons");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		open_create(t);

		for (int row = 1; row <= 3; row++) {
			boolean in_before = t.verify_in_state(row).isSelected();
			t.Select_state_type(row, !in_before);   // click the one that is not selected
			WaitUtil.settle(driver, 300);
			boolean in_after = t.verify_in_state(row).isSelected();
			boolean out_after = t.verify_out_state(row).isSelected();
			test.info("Row " + row + ": InState was " + in_before + ", after clicking the other one InState=" + in_after
					+ " OutState=" + out_after);
			Assert.assertTrue(in_after ^ out_after, "Row " + row + " must have exactly one of InState / OutState selected");
		}
		test.pass("Every row always has exactly one of InState / OutState selected");

		t.Select_value_in(true);
		Assert.assertTrue(t.verify_percentage().isSelected() && !t.verify_amount().isSelected(),
				"Choosing Percentage did not unselect Amount");
		t.Select_value_in(false);
		Assert.assertTrue(t.verify_amount().isSelected() && !t.verify_percentage().isSelected(),
				"Choosing Amount did not unselect Percentage");
		test.pass("Percentage and Amount exclude each other");

		t.Tick_applicable(1);
		t.Tick_applicable(2);
		Assert.assertTrue(t.verify_applicable(1).isSelected() && t.verify_applicable(2).isSelected(),
				"Two Applicable for boxes could not be ticked together");
		test.pass("Applicable for boxes can be ticked together");
	}

	// ======================================================================
	// SCENARIO 16: How to Apply options
	// The drop down offers at least one option (the options are logged).
	// ======================================================================
	@Test(priority = 16)
	public void s16_how_to_apply_options() throws InterruptedException {

		test = extent.createTest("S16 How To Apply Options");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		open_create(t);
		List<String> options = t.how_to_apply_options();
		test.info("How to Apply options: " + options);

		Assert.assertFalse(options.isEmpty(), "How to Apply has no options");
		test.pass("How to Apply offers " + options.size() + " option(s)");
	}

	// ======================================================================
	// SCENARIO 17: plus and minus icons
	// The green plus adds another additional tax row (more inputs); the red minus removes it again.
	// ======================================================================
	@Test(priority = 17)
	public void s17_plus_and_minus() throws InterruptedException {

		test = extent.createTest("S17 Plus And Minus Icons");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		open_create(t);

		int before = t.form_input_count();
		t.Click_plus();
		WaitUtil.settle(driver, 1000);
		int after_plus = t.form_input_count();
		test.info("Inputs before: " + before + ", after plus: " + after_plus);
		Assert.assertTrue(after_plus > before, "The plus icon did not add an additional tax row");
		test.pass("Plus added an additional tax row");

		t.Click_minus();
		WaitUtil.settle(driver, 1000);
		Assert.assertEquals(t.form_input_count(), before, "The minus icon did not remove the added row");
		test.pass("Minus removed the added row");
	}

	// Rows for scenario 18: description, the value placed in row 1
	@DataProvider(name = "invalid_values")
	public Object[][] invalidValues() {
		return new Object[][] { { "Value with letters", "abc" }, { "Negative value", "-5" },
				{ "Special characters in value", "@#$" } };
	}

	// ======================================================================
	// SCENARIO 18: invalid values
	// One tax value is wrong; the form must be rejected (it stays open).
	// If the site ACCEPTS one of these, that is a validation defect to report
	// (the profile it saved is deleted by the last scenario, its name starts with TX).
	// ======================================================================
	@Test(priority = 18, dataProvider = "invalid_values")
	public void s18_invalid_values(String description, String bad_value) throws InterruptedException {

		test = extent.createTest("S18 " + description);

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);
		String stamp = unique("");
		String profile = "TX" + stamp;

		open_create(t);
		t.Fill_profile(profile, new String[] { "C" + stamp + "1", "C" + stamp + "2", "C" + stamp + "3" },
				new String[] { "Auto Tax 1", "Auto Tax 2", "Auto Tax 3" }, new String[] { bad_value, "10", "15" });
		t.Click_submit();
		WaitUtil.settle(driver, 2000);

		test.info("Messages shown: " + t.validation_messages());

		Assert.assertTrue(t.is_form_screen(), "DEFECT: the site accepted: " + description + " (profile " + profile + ")");
		test.pass(description + ": rejected, still on the form");
	}

	// ======================================================================
	// SCENARIO 19: the same code in two rows
	// ASSUMPTION: codes inside one profile must be different. If the site allows it, change this scenario.
	// ======================================================================
	@Test(priority = 19)
	public void s19_same_code_twice() throws InterruptedException {

		test = extent.createTest("S19 Same Code In Two Rows");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);
		String stamp = unique("");
		String profile = "TX" + stamp;

		open_create(t);
		t.Fill_profile(profile, new String[] { "C" + stamp, "C" + stamp, "C" + stamp + "3" },
				new String[] { "Auto Tax 1", "Auto Tax 2", "Auto Tax 3" }, new String[] { "5", "10", "15" });
		t.Click_submit();
		WaitUtil.settle(driver, 2000);

		String message = t.get_message();
		test.info("Messages shown: " + t.validation_messages() + " | pop-up: '" + message + "'");

		Assert.assertTrue(t.is_form_screen() || !message.isEmpty(),
				"DEFECT?: the same code in two rows was accepted without any message (profile " + profile + ")");
		test.pass("The same code in two rows was not silently accepted");
	}

	// ======================================================================
	// SCENARIO 20: create a valid profile (no additional tax)
	// The 3 rows are filled with valid values. The form must be accepted (it closes or a message appears).
	// ======================================================================
	@Test(priority = 20)
	public void s20_create_valid_profile() throws InterruptedException {

		test = extent.createTest("S20 Create Valid Profile");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		main_stamp = unique("");
		main_profile = "TX" + main_stamp;

		open_create(t);
		test.info("Creating profile " + main_profile);
		fill_valid_profile(t, main_profile, main_stamp);
		t.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = t.get_message();
		test.info("Message after submit: '" + message + "'");
		test.info("Validation messages: " + t.validation_messages());

		Assert.assertTrue(!t.is_form_screen() || !message.isEmpty(),
				"Submit did not give any response. Messages: " + t.validation_messages());
		test.pass("Profile submitted: " + main_profile);
	}

	// ======================================================================
	// SCENARIO 21: search the profile that was just created
	// It is found exactly once and "Is Additional Tax Added" is No.
	// ======================================================================
	@Test(priority = 21, dependsOnMethods = "s20_create_valid_profile")
	public void s21_search_created_profile() throws InterruptedException {

		test = extent.createTest("S21 Search Created Profile");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		t.Search_profile(main_profile);
		Assert.assertTrue(t.Wait_for_row(main_profile, 8), "The new profile " + main_profile + " did not appear in the list");
		test.info("Rows shown: " + t.result_texts());

		Assert.assertEquals(t.result_count(), 1, "The new profile " + main_profile + " was not found exactly once");
		Assert.assertTrue(t.result_texts().get(0).contains("No"), "Is Additional Tax Added should be No");
		test.pass("The new profile is in the list with Is Additional Tax Added = No");
	}

	// ======================================================================
	// SCENARIO 22: duplicate profile name
	// A second profile with the SAME name must be rejected: the form stays open or a message appears.
	// ======================================================================
	@Test(priority = 22, dependsOnMethods = "s20_create_valid_profile")
	public void s22_duplicate_profile_name() throws InterruptedException {

		test = extent.createTest("S22 Duplicate Profile Name");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);
		String stamp = unique("");

		open_create(t);
		fill_valid_profile(t, main_profile, stamp);   // same profile name, different codes
		t.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = t.get_message();
		test.info("Message after submit: '" + message + "'");
		test.info("Validation messages: " + t.validation_messages());

		Assert.assertTrue(t.is_form_screen() || !message.isEmpty(),
				"DEFECT: the duplicate profile name " + main_profile + " was accepted without any message");
		test.pass("Duplicate profile name was not silently accepted");
	}

	// ======================================================================
	// SCENARIO 23: view the created profile
	// The details screen shows exactly what was entered.
	// ======================================================================
	@Test(priority = 23, dependsOnMethods = "s20_create_valid_profile")
	public void s23_view_created_profile() throws InterruptedException {

		test = extent.createTest("S23 View Created Profile");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		t.Search_profile(main_profile);
		Assert.assertTrue(t.Wait_for_row(main_profile, 8), "The new profile " + main_profile + " did not appear in the list");
		t.Click_view(main_profile);
		WaitUtil.settle(driver, 2000);

		Assert.assertEquals(t.verify_profile_name().getAttribute("value"), main_profile, "Wrong profile name");
		String[] values = { "5", "10", "15" };
		for (int row = 1; row <= 3; row++) {
			Assert.assertEquals(t.verify_code(row).getAttribute("value"), "C" + main_stamp + row, "Wrong code in row " + row);
			Assert.assertEquals(t.verify_tax_name(row).getAttribute("value"), "Auto Tax " + row, "Wrong name in row " + row);
			Assert.assertEquals(t.verify_value(row).getAttribute("value"), values[row - 1], "Wrong value in row " + row);
		}
		test.pass("The view screen shows exactly what was entered");
	}

	// ======================================================================
	// SCENARIO 24: edit the created profile
	// Changing the value of row 1 from 5 to 7 and submitting saves it: the view screen shows 7.
	// (Only the profile created by the tests is edited, never a seed profile.)
	// ======================================================================
	@Test(priority = 24, dependsOnMethods = "s20_create_valid_profile")
	public void s24_edit_created_profile() throws InterruptedException {

		test = extent.createTest("S24 Edit Created Profile");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);

		t.Search_profile(main_profile);
		Assert.assertTrue(t.Wait_for_row(main_profile, 8), "The new profile " + main_profile + " did not appear in the list");
		t.Click_edit(main_profile);
		WaitUtil.settle(driver, 2000);

		Assert.assertEquals(t.verify_value(1).getAttribute("value"), "5", "The edit form does not show the saved value");
		t.Fill_profile(main_profile, new String[] { "C" + main_stamp + "1", "C" + main_stamp + "2", "C" + main_stamp + "3" },
				new String[] { "Auto Tax 1", "Auto Tax 2", "Auto Tax 3" }, new String[] { "7", "10", "15" });
		t.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = t.get_message();
		test.info("Message after submit: '" + message + "'");
		Assert.assertTrue(!t.is_form_screen() || !message.isEmpty(),
				"The edit was not accepted. Messages: " + t.validation_messages());
		test.pass("The edit was accepted");

		open_tax_details();
		t.Search_profile(main_profile);
		Assert.assertTrue(t.Wait_for_row(main_profile, 8), "The edited profile " + main_profile + " did not appear in the list");
		t.Click_view(main_profile);
		WaitUtil.settle(driver, 2000);
		Assert.assertEquals(t.verify_value(1).getAttribute("value"), "7", "The changed value was not saved");
		test.pass("The changed value (7) is saved");
	}

	// ======================================================================
	// SCENARIO 25: create a profile WITH an additional tax
	// Additional Tax name, value, Value In = Percentage, the first How to Apply option and Applicable for Tax1.
	// It must be accepted, and the list then shows Is Additional Tax Added = Yes.
	// ======================================================================
	@Test(priority = 25)
	public void s25_create_with_additional_tax() throws InterruptedException {

		test = extent.createTest("S25 Create Profile With Additional Tax");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);
		String stamp = unique("");
		additional_profile = "TX" + stamp;

		open_create(t);
		fill_valid_profile(t, additional_profile, stamp);
		t.Fill_additional("Auto Cess", "2", true, 1);
		t.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = t.get_message();
		test.info("Message after submit: '" + message + "'");
		test.info("Validation messages: " + t.validation_messages());

		Assert.assertTrue(!t.is_form_screen() || !message.isEmpty(),
				"Profile with additional tax was rejected. Messages: " + t.validation_messages());
		test.pass("Profile with additional tax submitted: " + additional_profile);

		t.Search_profile(additional_profile);
		Assert.assertTrue(t.Wait_for_row(additional_profile, 8),
				"The new profile " + additional_profile + " did not appear in the list");
		test.info("Rows shown: " + t.result_texts());
		Assert.assertEquals(t.result_count(), 1, "The new profile was not found exactly once");
		Assert.assertTrue(t.result_texts().get(0).contains("Yes"), "Is Additional Tax Added should be Yes");
		test.pass("The list shows Is Additional Tax Added = Yes");
	}

	// ======================================================================
	// SCENARIO 26: delete the test profiles (clean up)
	// The red trash icon deletes a profile and the site shows an alert "Deleted successfully".
	// Every profile whose name starts with TX (the names the tests use) is deleted one by one,
	// including leftovers of earlier runs. Seed profiles (RST@10, GST@18%) are never touched.
	// ======================================================================
	@Test(priority = 99, alwaysRun = true)
	public void s26_delete_test_profiles() throws InterruptedException {

		test = extent.createTest("S26 Delete Test Profiles");

		F_TaxDetails_Cls t = new F_TaxDetails_Cls(driver);
		int deleted = 0;

		for (int round = 1; round <= 15; round++) {
			t.Search_profile("TX");
			WaitUtil.settle(driver, 2000);

			List<String> mine = new ArrayList<String>();
			for (String name : t.Profile_names()) {
				if (name.startsWith("TX")) {
					mine.add(name);
				}
			}
			test.info("Round " + round + ": test profiles in the list: " + mine);
			if (mine.isEmpty()) {
				break;
			}

			String profile = mine.get(0);
			Assert.assertTrue(profile.startsWith("TX"), "Refusing to delete a profile that is not a test profile: " + profile);

			t.Click_delete(profile);
			List<String> alerts = t.Confirm_delete();
			test.info("Deleting " + profile + " showed: " + alerts);
			WaitUtil.settle(driver, 1500);
			deleted++;
		}

		// nothing starting with TX may be left
		t.Search_profile("TX");
		WaitUtil.settle(driver, 2000);
		List<String> left = new ArrayList<String>();
		for (String name : t.Profile_names()) {
			if (name.startsWith("TX")) {
				left.add(name);
			}
		}
		Assert.assertTrue(left.isEmpty(), "Test profiles still in the list: " + left);
		test.pass("Deleted " + deleted + " test profile(s), none left");
	}
}