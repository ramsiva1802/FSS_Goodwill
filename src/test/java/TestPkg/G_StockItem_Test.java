package TestPkg;

import UtillPkg.WaitUtil;
import java.util.ArrayList;
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
import PagePkg.A_Login_Cls;
import PagePkg.G_StockItem_Cls;

/*
 * NOTES - G_StockItem_Test
 * ----------------------
 * Stock Item > Stock Item. UI checks only (no database).
 * MANDATORY (11): Stock Item Code, Stock Item Name, Alias Name, HSN Code, Stock MRP, Minimum Sale Quantity,
 *   Category, Sub Category, Brand, Unit, Stock Type.   OPTIONAL: Product Group, Conversion Factor,
 *   Tax Profile, Base Unit, Is Active.
 * Seed data (only READ): cr4 = CRono-item4 (Rono-Cate / Rono-Sub2 / Cr7-Brand, GST@18%, Goal number, MRP 75,
 * min qty 7, HSN 1599487845) and ney1 = Ney-Item1 with the attribute Skill (Dribble, Passing).
 * Stock items CANNOT be deleted. Every item the tests create has a code starting with "ZQ"; the last scenario
 * deactivates them (unticks Is Active), which removes them from the default Active list.
 * The Add pop-ups (category, sub category, brand, unit), + New and Edit Attribute are only OPENED and
 * cancelled, never saved, and Delete is never clicked, so no master data is created or changed.
 * After a search the table refreshes a moment later, so the tests wait for the expected row to appear.
 * Each test starts from the Stock Item list screen (see @BeforeMethod).
 */
public class G_StockItem_Test extends BaseCls {

	String dash_url;

	// the item used by the search, view, edit and duplicate scenarios
	String created_code;
	String created_name;

	// the 11 mandatory fields and the message each one shows when it is empty (taken from the screen)
	String[] MANDATORY_FIELDS = { "Stock Item Code", "Stock Item Name", "Alias Name", "HSN Code", "Stock MRP",
			"Minimum Sale Quantity", "Category", "Sub Category", "Brand", "Unit", "Stock Type" };
	String[] MANDATORY_MESSAGES = { "Please enter a stock code", "Please enter a stock name",
			"Please enter a Alias Name", "Please enter a HSNCode", "Please enter a stock MRP",
			"Please enter a Minimum Sale Qty", "Please select a Category", "Please select a SubCategory",
			"Please select a Brand", "Please select a Unit", "Please select a Stock Type" };

	// a unique value per call: the number changes every 0.1 second and repeats only after about 115 days
	private String unique(String prefix) {
		return prefix + (System.currentTimeMillis() / 100) % 10000000;
	}

	// removes dots and capital letters so "Please enter a stock code." equals "please enter a stock code"
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
		Assert.assertTrue(login.login_success(), "Could not log in for Stock Item tests");

		dash_url = driver.getCurrentUrl();
	}

	// opens Stock Item > Stock Item from the dashboard
	private void go_to_stock_item() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		try {
			dash.Click_submenu_by_name("Stock Item", "Stock Item");
		} catch (NoSuchElementException e) {
			dash.Click_home();
			dash.Click_submenu_containing("Stock Item", "Stock Item");
		}
		WaitUtil.settle(driver, 1500);
	}

	// runs before EVERY test: open the Stock Item list screen (log in again if the session broke)
	@BeforeMethod
	public void open_stock_item() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		try {
			go_to_stock_item();
		} catch (Exception e) {
			login_first();
			go_to_stock_item();
		}
		remember_clean_screen();
	}

	// opens the create form from the list screen
	private void open_create(G_StockItem_Cls s) throws InterruptedException {
		s.Click_create();
		WaitUtil.settle(driver, 1500);
	}

	// ======================================================================
	// SCENARIO 1: list screen elements
	// Buttons, table filter, Show entries, the 7 column headers, entries text, pagination, rows,
	// and a view and edit icon (but no delete) on a seed row.
	// ======================================================================
	@Test(priority = 1)
	public void s01_verify_list_page() {

		test = extent.createTest("S01 Stock Item List Page Verification");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		Assert.assertTrue(s.verify_create_btn().isDisplayed(), "Create button not displayed");
		test.pass("Create button displayed");

		Assert.assertTrue(s.verify_top_search().isDisplayed(), "Search button not displayed");
		test.pass("Search button displayed");

		Assert.assertTrue(s.verify_table_filter().isDisplayed(), "Table Search: box not displayed");
		test.pass("Table Search: box displayed");

		Assert.assertTrue(s.verify_entries_select().isDisplayed(), "Show entries drop down not displayed");
		test.pass("Show entries drop down displayed");

		for (String header : new String[] { "STOCK CODE", "STOCK NAME", "SUB CATEGORY", "TAX PROFILE", "UNIT", "BRAND",
				"ACTION" }) {
			Assert.assertTrue(s.verify_header(header).isDisplayed(), "Column header not displayed: " + header);
			test.pass("Column header displayed: " + header);
		}

		Assert.assertTrue(s.verify_entries().getText().contains("Showing 1 to"), "Entries text is wrong: " + s.verify_entries().getText());
		test.pass("Entries text: " + s.verify_entries().getText());

		Assert.assertTrue(s.verify_previous().isDisplayed() && s.verify_next().isDisplayed(),
				"Pagination (Previous / Next) not displayed");
		test.pass("Pagination displayed");

		Assert.assertTrue(s.result_count() >= 1, "The table has no rows");
		test.pass("The table shows " + s.result_count() + " rows");

		Assert.assertEquals(s.icon_count("cr4"), 2, "cr4 should have a view and an edit icon only");
		test.pass("A seed row has 2 icons (view, edit) and no delete icon");
	}

	// ======================================================================
	// SCENARIO 2: search form elements
	// Clicking Search opens the form: stock code, stock name, status (default Active) and the Search button.
	// ======================================================================
	@Test(priority = 2)
	public void s02_verify_search_form() {

		test = extent.createTest("S02 Search Form Verification");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.open_search_form();

		Assert.assertTrue(s.verify_search_code().isDisplayed(), "Stock Code box not displayed");
		test.pass("Stock Code box displayed");

		Assert.assertTrue(s.verify_search_name().isDisplayed(), "Stock Name box not displayed");
		test.pass("Stock Name box displayed");

		Assert.assertTrue(s.verify_search_status().isDisplayed(), "Status drop down not displayed");
		Assert.assertEquals(s.selected(s.verify_search_status()), "Active", "Status is not Active by default");
		test.info("Status options: " + s.status_options());
		test.pass("Status drop down displayed, Active by default");

		Assert.assertTrue(s.verify_search_go().isDisplayed(), "Search button of the form not displayed");
		test.pass("Search button of the form displayed");
	}

	// ======================================================================
	// SCENARIO 3: search by stock code
	// cr4 returns exactly one row: CRono-item4.
	// ======================================================================
	@Test(priority = 3)
	public void s03_search_by_code() throws InterruptedException {

		test = extent.createTest("S03 Search By Stock Code");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.Search_stock("cr4", "", "");
		Assert.assertTrue(s.Wait_for_row("CRono-item4", 8), "cr4 did not appear in the list");
		test.info("Rows shown: " + s.result_texts());

		Assert.assertEquals(s.result_count(), 1, "cr4 should return exactly 1 row");
		test.pass("cr4 found: 1 row, CRono-item4");
	}

	// ======================================================================
	// SCENARIO 4: search by stock name
	// CRono-item4 returns exactly one row: cr4.
	// ======================================================================
	@Test(priority = 4)
	public void s04_search_by_name() throws InterruptedException {

		test = extent.createTest("S04 Search By Stock Name");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.Search_stock("", "CRono-item4", "");
		Assert.assertTrue(s.Wait_for_row("cr4", 8), "CRono-item4 did not appear in the list");
		test.info("Rows shown: " + s.result_texts());

		Assert.assertEquals(s.result_count(), 1, "CRono-item4 should return exactly 1 row");
		test.pass("CRono-item4 found: 1 row, cr4");
	}

	// ======================================================================
	// SCENARIO 5: combined search
	// A code and its own name return the row; a code with another item's name returns nothing.
	// ======================================================================
	@Test(priority = 5)
	public void s05_combined_search() throws InterruptedException {

		test = extent.createTest("S05 Combined Search");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.Search_stock("cr4", "CRono-item4", "");
		Assert.assertTrue(s.Wait_for_row("cr4", 8), "The matching code and name did not return the row");
		Assert.assertEquals(s.result_count(), 1, "Matching code and name should return 1 row");
		test.pass("Matching code and name returned 1 row");

		s.Search_stock("cr4", "Ney-Item1", "");
		WaitUtil.settle(driver, 2500);
		test.info("Rows shown (code with another item's name): " + s.result_texts());
		Assert.assertEquals(s.result_count(), 0, "A code with another item's name should return 0 rows");
		test.pass("A code with another item's name returned 0 rows");
	}

	// ======================================================================
	// SCENARIO 6: search for something that does not exist
	// Must return no rows.
	// ======================================================================
	@Test(priority = 6)
	public void s06_search_not_found() throws InterruptedException {

		test = extent.createTest("S06 Search Not Found");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.Search_stock("ZZZ999", "", "");
		WaitUtil.settle(driver, 2500);
		test.info("Rows shown: " + s.result_texts());

		Assert.assertEquals(s.result_count(), 0, "A non existing code returned rows");
		test.pass("Non existing code returned 0 rows");
	}

	// ======================================================================
	// SCENARIO 7: the "Search:" box above the table
	// It filters the rows of the page: leo4 leaves its row, a nonsense text leaves none.
	// ======================================================================
	@Test(priority = 7)
	public void s07_table_filter() throws InterruptedException {

		test = extent.createTest("S07 Table Search Box");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.Filter_table("leo4");
		WaitUtil.settle(driver, 1500);
		test.info("Rows shown for leo4: " + s.result_texts());
		Assert.assertTrue(s.result_count() >= 1 && s.result_texts().toString().contains("leo4"), "leo4 did not stay in the table");
		test.pass("leo4 filter left its row");

		s.Filter_table("ZZZ999");
		WaitUtil.settle(driver, 1500);
		test.info("Rows shown for ZZZ999: " + s.result_texts());
		Assert.assertEquals(s.result_count(), 0, "A nonsense text should leave 0 rows");
		test.pass("Nonsense text left 0 rows");
	}

	// ======================================================================
	// SCENARIO 8: Show entries
	// By default at most 10 rows are shown; the largest option shows more than 10.
	// ======================================================================
	@Test(priority = 8)
	public void s08_show_entries() throws InterruptedException {

		test = extent.createTest("S08 Show Entries");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		Assert.assertTrue(s.result_count() <= 10, "More than 10 rows are shown by default");
		test.pass("Default: " + s.result_count() + " rows (at most 10)");

		String chosen = s.Select_largest_entries();
		WaitUtil.settle(driver, 2000);
		test.info("Chose '" + chosen + "' entries, rows shown: " + s.result_count());
		Assert.assertTrue(s.result_count() > 10, "The largest entries option did not show more than 10 rows");
		test.pass("'" + chosen + "' entries shows " + s.result_count() + " rows");
	}

	// ======================================================================
	// SCENARIO 9: pagination
	// Next shows page 2 (a different first row), Previous returns to page 1, and a page number opens that page.
	// ======================================================================
	@Test(priority = 9)
	public void s09_pagination() throws InterruptedException {

		test = extent.createTest("S09 Pagination");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		String page_one = s.first_row_code();
		s.Click_next();
		WaitUtil.settle(driver, 1500);
		String page_two = s.first_row_code();
		test.info("First code on page 1: " + page_one + ", on page 2: " + page_two);
		Assert.assertNotEquals(page_two, page_one, "Next did not show another page");
		test.pass("Next showed page 2");

		s.Click_previous();
		WaitUtil.settle(driver, 1500);
		Assert.assertEquals(s.first_row_code(), page_one, "Previous did not return to page 1");
		test.pass("Previous returned to page 1");

		s.Click_page("3");
		WaitUtil.settle(driver, 1500);
		Assert.assertNotEquals(s.first_row_code(), page_one, "Page 3 shows the first page's rows");
		test.pass("Page number 3 opened another page");
	}

	// ======================================================================
	// SCENARIO 10: sorting
	// Clicking the STOCK CODE header re-orders the rows; clicking again reverses the order.
	// ======================================================================
	@Test(priority = 10)
	public void s10_sort_by_code() throws InterruptedException {

		test = extent.createTest("S10 Sort By Stock Code");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		String original = s.first_row_code();
		s.Click_header("STOCK CODE");
		WaitUtil.settle(driver, 1500);
		String first_click = s.first_row_code();
		test.info("First code before: " + original + ", after one click: " + first_click);

		s.Click_header("STOCK CODE");
		WaitUtil.settle(driver, 1500);
		String second_click = s.first_row_code();
		test.info("First code after the second click: " + second_click);

		Assert.assertNotEquals(second_click, first_click, "A second click did not reverse the order");
		test.pass("Clicking the header again reversed the order");
	}

	// ======================================================================
	// SCENARIO 11: view icon of cr4
	// The details show exactly the saved values, the form is greyed out (no Submit) and Back returns to the list.
	// ======================================================================
	@Test(priority = 11)
	public void s11_view_icon() throws InterruptedException {

		test = extent.createTest("S11 View Icon");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.Search_stock("cr4", "", "");
		Assert.assertTrue(s.Wait_for_row("cr4", 8), "cr4 did not appear in the list");
		s.Click_view("cr4");
		WaitUtil.settle(driver, 2000);

		Assert.assertTrue(s.is_form_screen(), "The view icon did not open the details screen");
		test.pass("View opened the details screen");

		Assert.assertEquals(s.verify_code().getAttribute("value"), "cr4", "Wrong stock code");
		Assert.assertEquals(s.verify_name().getAttribute("value"), "CRono-item4", "Wrong stock name");
		Assert.assertEquals(s.verify_alias().getAttribute("value"), "CRono-item4", "Wrong alias");
		Assert.assertEquals(s.verify_hsn().getAttribute("value"), "1599487845", "Wrong HSN code");
		Assert.assertEquals(s.verify_mrp().getAttribute("value"), "75", "Wrong MRP");
		Assert.assertEquals(s.verify_min_qty().getAttribute("value"), "7", "Wrong minimum sale quantity");
		Assert.assertEquals(s.verify_conversion().getAttribute("value"), "0", "Wrong conversion factor");
		Assert.assertEquals(s.selected(s.verify_category()), "Rono-Cate", "Wrong category");
		Assert.assertEquals(s.selected(s.verify_sub_category()), "Rono-Sub2", "Wrong sub category");
		Assert.assertEquals(s.selected(s.verify_brand()), "Cr7-Brand", "Wrong brand");
		Assert.assertEquals(s.selected(s.verify_tax_profile()), "GST@18%", "Wrong tax profile");
		Assert.assertEquals(s.selected(s.verify_unit()), "Goal number", "Wrong unit");
		Assert.assertEquals(s.selected(s.verify_base_unit()), "Goal number", "Wrong base unit");
		Assert.assertEquals(s.selected(s.verify_stock_type()), "Complete Products", "Wrong stock type");
		Assert.assertTrue(s.verify_active().isSelected(), "Is Active should be ticked");
		test.pass("The details show the saved values of cr4");

		Assert.assertFalse(s.verify_code().isEnabled(), "The stock code is editable on the view screen");
		Assert.assertFalse(s.verify_mrp().isEnabled(), "The MRP is editable on the view screen");
		Assert.assertFalse(s.verify_active().isEnabled(), "Is Active is editable on the view screen");
		Assert.assertFalse(s.is_submit_displayed(), "A Submit button is shown on the view screen");
		test.pass("View screen is read only (fields disabled, no Submit)");

		s.Click_back();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(s.is_list_page(), "Back did not return to the list");
		test.pass("Back returned to the list");
	}

	// ======================================================================
	// SCENARIO 12: view an item with an attribute
	// ney1 has the attribute Skill with the values Dribble and Passing.
	// ======================================================================
	@Test(priority = 12)
	public void s12_view_item_with_attribute() throws InterruptedException {

		test = extent.createTest("S12 View Item With Attribute");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.Search_stock("ney1", "", "");
		Assert.assertTrue(s.Wait_for_row("ney1", 8), "ney1 did not appear in the list");
		s.Click_view("ney1");
		WaitUtil.settle(driver, 2000);

		Assert.assertEquals(s.attribute_count(), 1, "ney1 should show exactly one attribute");
		Assert.assertEquals(s.selected_attribute(0), "Skill", "The attribute should be Skill");
		String card = s.card_text(0);
		test.info("Attribute block text: " + card);
		Assert.assertTrue(card.contains("Dribble") && card.contains("Passing"), "The values Dribble and Passing are not shown");
		test.pass("ney1 shows the attribute Skill with Dribble and Passing");
	}

	// ======================================================================
	// SCENARIO 13: create form elements
	// Every field, drop down, Add button, the check box, the Item Attributes block, Back and Submit.
	// ======================================================================
	@Test(priority = 13)
	public void s13_verify_create_form() throws InterruptedException {

		test = extent.createTest("S13 Create Form Verification");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);

		String[] labels = { "Company", "Product Group", "Category", "Sub Category", "Brand", "Conversion Factor",
				"Stock Type", "Stock Item Code", "Stock Item Name", "Alias Name", "HSN Code", "Stock MRP",
				"Minimum Sale Quantity", "Tax Profile", "Unit", "Base Unit", "Is Active", "Upload Item Photo",
				"Add (Category)", "Add (Sub Category)", "Add (Brand)", "Add (Unit)", "Add Attribute", "Back", "Submit" };

		WebElement[] elements = { s.verify_company(), s.verify_product_group(), s.verify_category(),
				s.verify_sub_category(), s.verify_brand(), s.verify_conversion(), s.verify_stock_type(), s.verify_code(),
				s.verify_name(), s.verify_alias(), s.verify_hsn(), s.verify_mrp(), s.verify_min_qty(),
				s.verify_tax_profile(), s.verify_unit(), s.verify_base_unit(), s.verify_active(),
				s.verify_upload_photo(), s.verify_add(1), s.verify_add(2), s.verify_add(3), s.verify_add(4),
				s.verify_add_attribute(), s.verify_back(), s.verify_submit() };

		for (int i = 0; i < labels.length; i++) {
			Assert.assertTrue(elements[i].isDisplayed(), labels[i] + " not displayed");
			test.pass(labels[i] + " displayed");
		}

		Assert.assertTrue(s.verify_active().isSelected(), "Is Active should be ticked by default");
		test.pass("Is Active is ticked by default");

		Assert.assertTrue(s.is_no_attributes_message(), "The 'No attributes added yet' message is not shown");
		test.pass("'No attributes added yet' is shown");
	}

	// ======================================================================
	// SCENARIO 14: submit the EMPTY form
	// The form is rejected and each of the 11 mandatory fields shows its own message.
	// The optional fields (Conversion Factor, Tax Profile, Base Unit) show no message.
	// ======================================================================
	@Test(priority = 14)
	public void s14_empty_form_shows_messages() throws InterruptedException {

		test = extent.createTest("S14 Empty Form Shows All Mandatory Messages");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);
		s.Click_submit();
		WaitUtil.settle(driver, 2000);

		Assert.assertTrue(s.is_form_screen(), "Empty form was accepted, the form closed");
		test.pass("Empty form rejected, still on the form");

		List<String> messages = s.validation_messages();
		test.info("Messages shown (" + messages.size() + "): " + messages);

		String shown = plain(messages.toString());
		for (String expected : MANDATORY_MESSAGES) {
			Assert.assertTrue(shown.contains(plain(expected)), "Message missing: " + expected);
			test.pass("Message shown: " + expected);
		}

		Assert.assertFalse(shown.contains("tax profile") || shown.contains("base unit") || shown.contains("conversion"),
				"A message is shown for an optional field: " + messages);
		test.pass("No message for the optional fields (Tax Profile, Base Unit, Conversion Factor)");
	}

	// ======================================================================
	// SCENARIO 15: one mandatory field blank at a time
	// For each of the 11 mandatory fields: fill everything, leave only that field empty (a drop down is put
	// back to its first option), Submit must be rejected and show that field's own message.
	// ======================================================================
	@Test(priority = 15)
	public void s15_each_mandatory_field_blank() throws InterruptedException {

		test = extent.createTest("S15 Each Mandatory Field Blank");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);
		List<String> accepted_wrongly = new ArrayList<String>();
		List<String> message_missing = new ArrayList<String>();

		open_create(s);

		for (int i = 0; i < MANDATORY_FIELDS.length; i++) {
			String stamp = unique("");
			String[] v = { "ZQ" + stamp, "Auto Item", "Auto Alias", "1599487845", "75", "7" };
			if (i < 6) {
				v[i] = "";   // a text field left empty
			}

			s.Fill_mandatory(v[0], v[1], v[2], v[3], v[4], v[5]);
			if (i >= 6) {
				s.Reset_choice(MANDATORY_FIELDS[i]);   // a drop down put back to its empty first option
			}
			s.Click_submit();
			WaitUtil.settle(driver, 2000);

			if (!s.is_form_screen()) {
				accepted_wrongly.add(MANDATORY_FIELDS[i]);
				test.fail(MANDATORY_FIELDS[i] + " blank: the form was accepted (item ZQ" + stamp + ")");
				open_stock_item();   // the form closed, so reopen it for the next field
				open_create(s);
				continue;
			}

			String shown = plain(s.validation_messages().toString());
			if (shown.contains(plain(MANDATORY_MESSAGES[i]))) {
				test.pass(MANDATORY_FIELDS[i] + " blank: rejected with '" + MANDATORY_MESSAGES[i] + "'");
			} else {
				message_missing.add(MANDATORY_FIELDS[i]);
				test.fail(MANDATORY_FIELDS[i] + " blank: rejected but the message '" + MANDATORY_MESSAGES[i]
						+ "' was not shown. Messages: " + s.validation_messages());
			}
		}

		Assert.assertTrue(accepted_wrongly.isEmpty(), "Form accepted with a blank: " + accepted_wrongly);
		Assert.assertTrue(message_missing.isEmpty(), "The expected message was missing for: " + message_missing);
	}

	// ======================================================================
	// SCENARIO 16: Back button
	// Back on the form returns to the list without saving anything.
	// ======================================================================
	@Test(priority = 16)
	public void s16_back_button() throws InterruptedException {

		test = extent.createTest("S16 Back Button Returns To List");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);
		s.Click_back();
		WaitUtil.settle(driver, 1500);

		Assert.assertTrue(s.is_list_page(), "Back did not return to the list page");
		test.pass("Back returned to the list page");
	}

	// ======================================================================
	// SCENARIO 17: drop down options
	// The drop downs offer the existing QA values (the full lists are logged).
	// ======================================================================
	@Test(priority = 17)
	public void s17_dropdown_options() throws InterruptedException {

		test = extent.createTest("S17 Drop Down Options");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);

		List<String> categories = s.category_options();
		test.info("Category options: " + categories);
		Assert.assertTrue(categories.contains("Rono-Cate"), "Category Rono-Cate missing");
		test.pass("Category offers Rono-Cate");

		List<String> brands = s.brand_options();
		test.info("Brand options: " + brands);
		Assert.assertTrue(brands.contains("Cr7-Brand"), "Brand Cr7-Brand missing");
		test.pass("Brand offers Cr7-Brand");

		List<String> taxes = s.tax_profile_options();
		test.info("Tax Profile options: " + taxes);
		Assert.assertTrue(taxes.contains("GST@18%") && taxes.contains("RST@10"), "Tax profiles GST@18% and RST@10 missing");
		test.pass("Tax Profile offers GST@18% and RST@10");

		List<String> units = s.unit_options();
		test.info("Unit options: " + units + " | Base Unit options: " + s.base_unit_options());
		Assert.assertTrue(units.contains("Goal number"), "Unit Goal number missing");
		test.pass("Unit offers Goal number");

		List<String> types = s.stock_type_options();
		test.info("Stock Type options: " + types);
		Assert.assertTrue(types.contains("Complete Products"), "Stock type Complete Products missing");
		test.pass("Stock Type offers Complete Products");
	}

	// ======================================================================
	// SCENARIO 18: Sub Category depends on Category
	// ASSUMPTION: after choosing the category Rono-Cate, only its sub categories (Rono-Sub, Rono-Sub2)
	// are offered, not the Ney sub categories.
	// ======================================================================
	@Test(priority = 18)
	public void s18_sub_category_depends_on_category() throws InterruptedException {

		test = extent.createTest("S18 Sub Category Depends On Category");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);
		s.Select_category("Rono-Cate");
		WaitUtil.settle(driver, 2000);

		List<String> subs = s.sub_category_options();
		test.info("Sub Category options after choosing Rono-Cate: " + subs);

		Assert.assertTrue(subs.contains("Rono-Sub") && subs.contains("Rono-Sub2"), "The Rono sub categories are not offered");
		test.pass("Rono-Sub and Rono-Sub2 are offered");
		Assert.assertFalse(subs.toString().contains("Ney-Sub"), "DEFECT?: sub categories of another category (Ney-Sub) are offered");
		test.pass("Sub categories of other categories are not offered");
	}

	// Rows for scenario 19: Add button number, the pop-up title, the placeholders of its two boxes
	@DataProvider(name = "popups")
	public Object[][] popups() {
		return new Object[][] { { 1, "Add Category", "Enter Category Code", "Enter Category Name" },
				{ 2, "Add Subcategory", "Enter Subcategory Code", "Enter Subcategory Name" },
				{ 3, "Add Brand", "Enter Brand Code", "Enter Brand Name" },
				{ 4, "Add Unit", "Enter Unit Code", "Enter Unit Name" } };
	}

	// ======================================================================
	// SCENARIO 19: the Add pop-ups (category, sub category, brand, unit)
	// Each opens with a code box, a name box and Save; an empty Save keeps it open; X closes it.
	// Nothing is saved, so no master data is created.
	// ======================================================================
	@Test(priority = 19, dataProvider = "popups")
	public void s19_add_popups(int button, String title, String code_box, String name_box) throws InterruptedException {

		test = extent.createTest("S19 " + title + " Pop-up");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);
		s.Open_add_popup(button, title);
		WaitUtil.settle(driver, 800);

		Assert.assertTrue(s.is_popup_open(title), "The pop-up " + title + " did not open");
		test.pass(title + " pop-up opened");

		Assert.assertTrue(s.verify_popup_input(code_box).isDisplayed(), "Code box not displayed");
		Assert.assertTrue(s.verify_popup_input(name_box).isDisplayed(), "Name box not displayed");
		Assert.assertTrue(s.verify_popup_save().isDisplayed(), "Save not displayed");
		test.pass("Code box, name box and Save displayed");

		s.Click_popup_save();
		WaitUtil.settle(driver, 1000);
		Assert.assertTrue(s.is_popup_open(title), "An empty " + title + " was accepted (the pop-up closed)");
		test.pass("Empty Save rejected, the pop-up stayed open. Messages: " + s.validation_messages());

		s.Close_popup(title);
		WaitUtil.settle(driver, 1000);
		Assert.assertFalse(s.is_popup_open(title), "The X did not close the pop-up");
		test.pass("X closed the pop-up");
	}

	// ======================================================================
	// SCENARIO 20: Add Attribute and the red x
	// At first there are no attributes and a message says so; Add Attribute adds a card each time;
	// the red x removes a card; when none is left the message is back.
	// ======================================================================
	@Test(priority = 20)
	public void s20_attribute_cards() throws InterruptedException {

		test = extent.createTest("S20 Attribute Cards");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);

		Assert.assertEquals(s.attribute_count(), 0, "There should be no attribute at first");
		Assert.assertTrue(s.is_no_attributes_message(), "The 'No attributes added yet' message is not shown");
		test.pass("No attributes at first, with the message");

		s.Click_add_attribute();
		WaitUtil.settle(driver, 800);
		Assert.assertEquals(s.attribute_count(), 1, "Add Attribute did not add a card");
		Assert.assertFalse(s.is_no_attributes_message(), "The message is still shown with a card present");
		test.pass("Add Attribute added a card and the message is gone");

		s.Click_add_attribute();
		WaitUtil.settle(driver, 800);
		Assert.assertEquals(s.attribute_count(), 2, "A second Add Attribute did not add another card");
		test.pass("A second card was added");

		s.Click_remove_attribute(1);
		WaitUtil.settle(driver, 800);
		Assert.assertEquals(s.attribute_count(), 1, "The red x did not remove the card");
		s.Click_remove_attribute(0);
		WaitUtil.settle(driver, 800);
		Assert.assertEquals(s.attribute_count(), 0, "The red x did not remove the last card");
		Assert.assertTrue(s.is_no_attributes_message(), "The message did not come back");
		test.pass("The red x removed the cards and the message is back");
	}

	// ======================================================================
	// SCENARIO 21: attribute values (presets)
	// Choosing the attribute Skill offers its values Dribble, Shoot and Passing; clicking a value picks it.
	// ======================================================================
	@Test(priority = 21)
	public void s21_attribute_presets() throws InterruptedException {

		test = extent.createTest("S21 Attribute Preset Values");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);
		s.Click_add_attribute();
		WaitUtil.settle(driver, 800);

		List<String> options = s.attribute_options(0);
		test.info("Attribute options: " + options);
		Assert.assertTrue(options.contains("Skill"), "The attribute Skill is not offered");

		s.Select_attribute(0, "Skill");
		WaitUtil.settle(driver, 1500);
		String card = s.card_text(0);
		test.info("Attribute block text: " + card);
		Assert.assertTrue(card.contains("Dribble") && card.contains("Shoot") && card.contains("Passing"),
				"The preset values of Skill are not shown");
		test.pass("Skill offers Dribble, Shoot and Passing");

		s.Click_preset(0, "Dribble");
		WaitUtil.settle(driver, 800);
		Assert.assertTrue(s.card_text_count(0, "Dribble") >= 2, "Clicking Dribble did not pick it (it should also appear in the values box)");
		test.pass("Clicking a value picks it");
	}

	// ======================================================================
	// SCENARIO 22: "+ New" attribute pop-up
	// It opens with an attribute name box, a value box with Add, Cancel and Save Values.
	// An empty Save Values keeps it open; Cancel closes it. Nothing is saved.
	// ======================================================================
	@Test(priority = 22)
	public void s22_new_attribute_popup() throws InterruptedException {

		test = extent.createTest("S22 New Attribute Pop-up");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);
		s.Click_add_attribute();
		WaitUtil.settle(driver, 800);
		s.Click_new_attribute(0);
		WaitUtil.settle(driver, 1000);

		Assert.assertTrue(s.is_add_values_open(), "The + New pop-up did not open");
		test.pass("+ New pop-up opened");

		Assert.assertTrue(s.verify_values_attribute_name().isDisplayed(), "Attribute Name box not displayed");
		Assert.assertTrue(s.verify_values_input().isDisplayed(), "Value box not displayed");
		Assert.assertTrue(s.verify_cancel().isDisplayed(), "Cancel not displayed");
		test.pass("Attribute Name box, value box, Cancel and Save Values displayed");

		s.Click_values_save();
		WaitUtil.settle(driver, 1000);
		Assert.assertTrue(s.is_add_values_open(), "An empty attribute was accepted (the pop-up closed)");
		test.pass("Empty Save Values rejected, the pop-up stayed open");

		s.Click_cancel();
		WaitUtil.settle(driver, 1000);
		Assert.assertFalse(s.is_add_values_open(), "Cancel did not close the pop-up");
		test.pass("Cancel closed the pop-up");
	}

	// ======================================================================
	// ======================================================================
	// SCENARIO 23: "Edit Attribute" pop-up
	// It opens with an Attribute Name drop down, the values of the attribute shown, a Delete, Cancel and
	// Save Changes button. Only Cancel is clicked: nothing is changed and Delete is never clicked.
	// OBSERVATION: the pop-up may start with the first attribute (Colour) and not the attribute chosen on
	// the card; what it shows is logged.
	// ======================================================================
	@Test(priority = 23)
	public void s23_edit_attribute_popup() throws InterruptedException {

		test = extent.createTest("S23 Edit Attribute Pop-up");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);
		s.Click_add_attribute();
		WaitUtil.settle(driver, 800);
		s.Select_attribute(0, "Skill");
		WaitUtil.settle(driver, 1500);
		s.Click_edit_attribute(0);
		WaitUtil.settle(driver, 1000);

		Assert.assertTrue(s.is_edit_attribute_open(), "The Edit Attribute pop-up did not open");
		test.pass("Edit Attribute pop-up opened");

		String text = s.edit_attribute_text();
		test.info("Pop-up text: " + text);
		Assert.assertTrue(text.contains("Attribute Name"), "The pop-up has no Attribute Name field");
		Assert.assertTrue(s.verify_edit_delete().isDisplayed() && s.verify_cancel().isDisplayed()
				&& s.verify_edit_save_changes().isDisplayed(), "Delete / Cancel / Save Changes not displayed");
		test.pass("The pop-up shows Attribute Name, Delete, Cancel and Save Changes");

		if (!text.contains("Skill")) {
			test.info("OBSERVATION: the attribute Skill was chosen on the card, but the pop-up does not show Skill."
					+ " It starts with another attribute.");
		}

		s.Click_cancel();
		WaitUtil.settle(driver, 1000);
		Assert.assertFalse(s.is_edit_attribute_open(), "Cancel did not close the pop-up");
		test.pass("Cancel closed the pop-up (nothing was changed)");
	}
	// Rows for scenario 24: description, HSN code, MRP, minimum sale quantity, conversion factor
	@DataProvider(name = "invalid_values")
	public Object[][] invalidValues() {
		return new Object[][] { { "MRP with letters", "1599487845", "abc", "7", "0" },
				{ "Negative MRP", "1599487845", "-5", "7", "0" },
				{ "Minimum sale quantity with letters", "1599487845", "75", "abc", "0" },
				{ "Negative minimum sale quantity", "1599487845", "75", "-3", "0" },
				{ "Conversion factor with letters", "1599487845", "75", "7", "abc" },
				{ "HSN code with letters", "ABCD1234", "75", "7", "0" } };
	}

	// ======================================================================
	// SCENARIO 24: invalid values
	// One value is wrong; the form must be rejected (it stays open).
	// If the site ACCEPTS one of these, that is a validation defect to report
	// (the item it saved starts with ZQ and is deactivated by the last scenario).
	// ======================================================================
	@Test(priority = 24, dataProvider = "invalid_values")
	public void s24_invalid_values(String description, String hsn, String mrp, String min_qty, String conversion)
			throws InterruptedException {

		test = extent.createTest("S24 " + description);

		G_StockItem_Cls s = new G_StockItem_Cls(driver);
		String stamp = unique("");

		open_create(s);
		s.Fill_item("ZQ" + stamp, "Auto Item " + stamp, "Auto Alias", hsn, mrp, min_qty, conversion);
		s.Click_submit();
		WaitUtil.settle(driver, 2500);

		test.info("Messages shown: " + s.validation_messages());
		Assert.assertTrue(s.is_form_screen(), "DEFECT: the site accepted: " + description + " (item ZQ" + stamp + ")");
		test.pass(description + ": rejected, still on the form");
	}

	// ======================================================================
	// SCENARIO 25: create a valid stock item (all fields)
	// The 11 mandatory and the optional fields are filled. The form must be accepted
	// (it closes or a message appears).
	// ======================================================================
	@Test(priority = 25)
	public void s25_create_valid_item() throws InterruptedException {

		test = extent.createTest("S25 Create Valid Stock Item");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		String stamp = unique("");
		created_code = "ZQ" + stamp;
		created_name = "Auto Item " + stamp;

		open_create(s);
		test.info("Creating stock item " + created_code + " / " + created_name);
		s.Fill_item(created_code, created_name, "Auto Alias", "1599487845", "75", "7", "0");
		s.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = s.get_message();
		test.info("Message after submit: '" + message + "'");
		test.info("Validation messages: " + s.validation_messages());

		Assert.assertTrue(!s.is_form_screen() || !message.isEmpty(),
				"Submit did not give any response. Messages: " + s.validation_messages());
		test.pass("Stock item submitted: " + created_code);
	}

	// ======================================================================
	// SCENARIO 26: search the item that was just created
	// It is found exactly once with the sub category, tax profile, unit and brand that were chosen.
	// ======================================================================
	@Test(priority = 26, dependsOnMethods = "s25_create_valid_item")
	public void s26_search_created_item() throws InterruptedException {

		test = extent.createTest("S26 Search Created Stock Item");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.Search_stock(created_code, "", "");
		Assert.assertTrue(s.Wait_for_row(created_code, 8), "The new item " + created_code + " did not appear in the list");
		test.info("Rows shown: " + s.result_texts());

		Assert.assertEquals(s.result_count(), 1, "The new item was not found exactly once");
		String row = s.result_texts().get(0);
		for (String expected : new String[] { created_name, "Rono-Sub2", "GST@18%", "Goal number", "Cr7-Brand" }) {
			Assert.assertTrue(row.contains(expected), "The row does not show: " + expected + " | row: " + row);
		}
		test.pass("The new item is in the list with the chosen values");
	}

	// ======================================================================
	// SCENARIO 27: view the created item
	// The details screen shows exactly what was entered.
	// ======================================================================
	@Test(priority = 27, dependsOnMethods = "s25_create_valid_item")
	public void s27_view_created_item() throws InterruptedException {

		test = extent.createTest("S27 View Created Stock Item");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.Search_stock(created_code, "", "");
		Assert.assertTrue(s.Wait_for_row(created_code, 8), "The new item did not appear in the list");
		s.Click_view(created_code);
		WaitUtil.settle(driver, 2000);

		Assert.assertEquals(s.verify_code().getAttribute("value"), created_code, "Wrong stock code");
		Assert.assertEquals(s.verify_name().getAttribute("value"), created_name, "Wrong stock name");
		Assert.assertEquals(s.verify_alias().getAttribute("value"), "Auto Alias", "Wrong alias");
		Assert.assertEquals(s.verify_hsn().getAttribute("value"), "1599487845", "Wrong HSN code");
		Assert.assertEquals(s.verify_mrp().getAttribute("value"), "75", "Wrong MRP");
		Assert.assertEquals(s.verify_min_qty().getAttribute("value"), "7", "Wrong minimum sale quantity");
		Assert.assertEquals(s.selected(s.verify_category()), "Rono-Cate", "Wrong category");
		Assert.assertEquals(s.selected(s.verify_sub_category()), "Rono-Sub2", "Wrong sub category");
		Assert.assertEquals(s.selected(s.verify_brand()), "Cr7-Brand", "Wrong brand");
		Assert.assertEquals(s.selected(s.verify_tax_profile()), "GST@18%", "Wrong tax profile");
		Assert.assertEquals(s.selected(s.verify_unit()), "Goal number", "Wrong unit");
		Assert.assertEquals(s.selected(s.verify_stock_type()), "Complete Products", "Wrong stock type");
		test.pass("The view screen shows exactly what was entered");
	}

	// ======================================================================
	// SCENARIO 28: edit the created item
	// Changing the MRP from 75 to 80 and submitting saves it: the view screen shows 80.
	// (Only the item created by the tests is edited, never a seed item.)
	// ======================================================================
	@Test(priority = 28, dependsOnMethods = "s25_create_valid_item")
	public void s28_edit_created_item() throws InterruptedException {

		test = extent.createTest("S28 Edit Created Stock Item");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		s.Search_stock(created_code, "", "");
		Assert.assertTrue(s.Wait_for_row(created_code, 8), "The new item did not appear in the list");
		s.Click_edit(created_code);
		WaitUtil.settle(driver, 2000);

		Assert.assertEquals(s.verify_mrp().getAttribute("value"), "75", "The edit form does not show the saved MRP");
		Assert.assertTrue(s.verify_mrp().isEnabled(), "The MRP is not editable on the edit screen");
		s.Change_mrp("80");
		s.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = s.get_message();
		test.info("Message after submit: '" + message + "'");
		Assert.assertTrue(!s.is_form_screen() || !message.isEmpty(), "The edit was not accepted. Messages: " + s.validation_messages());
		test.pass("The edit was accepted");

		open_stock_item();
		s.Search_stock(created_code, "", "");
		Assert.assertTrue(s.Wait_for_row(created_code, 8), "The edited item did not appear in the list");
		s.Click_view(created_code);
		WaitUtil.settle(driver, 2000);
		Assert.assertEquals(s.verify_mrp().getAttribute("value"), "80", "The changed MRP was not saved");
		test.pass("The changed MRP (80) is saved");
	}

	// ======================================================================
	// SCENARIO 29: duplicate stock code
	// A second item with the SAME code must be rejected: the form stays open or a message appears.
	// ======================================================================
	@Test(priority = 29, dependsOnMethods = "s25_create_valid_item")
	public void s29_duplicate_code() throws InterruptedException {

		test = extent.createTest("S29 Duplicate Stock Code");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);

		open_create(s);
		s.Fill_item(created_code, "Duplicate " + created_name, "Auto Alias", "1599487845", "75", "7", "0");
		s.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = s.get_message();
		test.info("Message after submit: '" + message + "'");
		test.info("Validation messages: " + s.validation_messages());

		Assert.assertTrue(s.is_form_screen() || !message.isEmpty(),
				"DEFECT: the duplicate stock code " + created_code + " was accepted without any message");
		test.pass("Duplicate stock code was not silently accepted");
	}

	// ======================================================================
	// SCENARIO 30: only the mandatory fields (optional fields left empty)
	// Product Group, Conversion Factor, Tax Profile and Base Unit are optional, so the item must still be
	// accepted and found in the list.
	// ======================================================================
	@Test(priority = 30)
	public void s30_optional_fields_blank() throws InterruptedException {

		test = extent.createTest("S30 Create With Optional Fields Blank");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);
		String stamp = unique("");
		String code = "ZQ" + stamp;

		open_create(s);
		s.Fill_mandatory(code, "Auto Optional Blank " + stamp, "Auto Alias", "1599487845", "75", "7");
		s.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = s.get_message();
		test.info("Message after submit: '" + message + "'");
		Assert.assertTrue(!s.is_form_screen() || !message.isEmpty(),
				"The item with the optional fields blank was rejected. Messages: " + s.validation_messages());
		test.pass("Item accepted without the optional fields: " + code);

		s.Search_stock(code, "", "");
		Assert.assertTrue(s.Wait_for_row(code, 8), "The new item did not appear in the list");
		Assert.assertEquals(s.result_count(), 1, "The new item was not found exactly once");
		test.info("Rows shown: " + s.result_texts());
		test.pass("The item is in the list");
	}

	// ======================================================================
	// SCENARIO 31: create an item WITH an attribute
	// Add Attribute, choose Skill and pick the value Dribble; the item is accepted and its view screen
	// shows the attribute Skill with Dribble.
	// ======================================================================
	@Test(priority = 31)
	public void s31_create_item_with_attribute() throws InterruptedException {

		test = extent.createTest("S31 Create Stock Item With Attribute");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);
		String stamp = unique("");
		String code = "ZQ" + stamp;

		open_create(s);
		s.Fill_item(code, "Auto Attribute Item " + stamp, "Auto Alias", "1599487845", "75", "7", "0");
		s.Click_add_attribute();
		WaitUtil.settle(driver, 800);
		s.Select_attribute(0, "Skill");
		WaitUtil.settle(driver, 1500);
		s.Click_preset(0, "Dribble");
		WaitUtil.settle(driver, 800);
		s.Click_submit();
		WaitUtil.settle(driver, 3000);

		String message = s.get_message();
		test.info("Message after submit: '" + message + "'");
		Assert.assertTrue(!s.is_form_screen() || !message.isEmpty(),
				"The item with an attribute was rejected. Messages: " + s.validation_messages());
		test.pass("Item with an attribute submitted: " + code);

		s.Search_stock(code, "", "");
		Assert.assertTrue(s.Wait_for_row(code, 8), "The new item did not appear in the list");
		s.Click_view(code);
		WaitUtil.settle(driver, 2000);

		Assert.assertEquals(s.attribute_count(), 1, "The saved item should show one attribute");
		Assert.assertEquals(s.selected_attribute(0), "Skill", "The saved attribute should be Skill");
		Assert.assertTrue(s.card_text(0).contains("Dribble"), "The value Dribble was not saved");
		test.pass("The saved item shows the attribute Skill with Dribble");
	}

	// ======================================================================
	// SCENARIO 32: deactivate the test items (clean up) and check the Status filter
	// Items cannot be deleted. Every active item whose code starts with ZQ is edited: Is Active is unticked
	// and the item is saved. It then disappears from the default Active list and shows under Inactive.
	// Items that do not start with ZQ are never touched.
	// ======================================================================
	@Test(priority = 99, alwaysRun = true)
	public void s32_deactivate_test_items() throws InterruptedException {

		test = extent.createTest("S32 Deactivate Test Items");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);
		List<String> done = new ArrayList<String>();

		for (int round = 1; round <= 20; round++) {
			s.Search_stock("ZQ", "", "");
			WaitUtil.settle(driver, 2500);

			String next = null;
			for (String code : s.Stock_codes()) {
				if (code.startsWith("ZQ")) {
					next = code;
					break;
				}
			}
			test.info("Round " + round + ": next active test item: " + next);
			if (next == null) {
				break;
			}
			Assert.assertFalse(done.contains(next), "The item " + next + " is still active after it was deactivated");

			s.Click_edit(next);
			WaitUtil.settle(driver, 2000);
			if (s.verify_active().isSelected()) {
				s.Click_active();
			}
			s.Click_submit();
			WaitUtil.settle(driver, 3000);
			test.info("Deactivating " + next + " showed: '" + s.get_message() + "' | " + s.validation_messages());
			done.add(next);
			open_stock_item();
		}

		// nothing starting with ZQ may be left in the Active list
		s.Search_stock("ZQ", "", "");
		WaitUtil.settle(driver, 2500);
		List<String> active_left = new ArrayList<String>();
		for (String code : s.Stock_codes()) {
			if (code.startsWith("ZQ")) {
				active_left.add(code);
			}
		}
		Assert.assertTrue(active_left.isEmpty(), "Test items still active: " + active_left);
		test.pass("Deactivated " + done.size() + " test item(s), none left active");

		// the Inactive status shows them
		if (!done.isEmpty()) {
			s.Search_stock("ZQ", "", "Inactive");
			WaitUtil.settle(driver, 2500);
			test.info("Inactive rows: " + s.result_texts());
			Assert.assertTrue(s.result_count() >= done.size(), "The Inactive status does not show the deactivated items");
			test.pass("The Inactive status shows the deactivated items");
		}
	}
}