package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;
import PagePkg.H_MenuCard_Cls;

/*
 * Menu Card (Stock Item > Menu Card) - functional tests, M01 .. M28.
 * Existing QA card used for reading only: GOAT / Football / Yes / 12 items (it is never changed).
 * Cards created by the tests get the code prefix ZQM and are deactivated at the end (M28),
 * because menu cards cannot be deleted.
 */
public class H_MenuCard_Test extends BaseCls {

	static final String EXISTING_CODE = "GOAT";
	static final String EXISTING_NAME = "Football";

	// cards created by the tests
	String code1;
	String name1;
	String code2;
	List<String> created = new ArrayList<String>();

	private String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
	}

	// runs ONCE: fresh session, login through the form
	@BeforeClass
	public void login_first() {
		login_again();
		code1 = "ZQM" + unique();
		name1 = "ZQ" + unique();
	}

	private void login_again() {
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
		Assert.assertTrue(login.login_success(), "Could not log in");
	}

	// opens Stock Item > Menu Card from the dashboard
	private void go_to_menu_card() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		try {
			dash.Click_submenu_by_name("Stock Item", "Menu Card");
		} catch (NoSuchElementException e) {
			dash.Click_home();
			dash.Click_submenu_containing("Stock Item", "Menu Card");
		}
		WaitUtil.settle(driver, 1500);
	}

	// every test starts on the Menu Card list screen
	@BeforeMethod
	public void open_menu_card() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);
		try {
			go_to_menu_card();
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_menu_card();
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_list_page());
		}
		remember_clean_screen();
	}
	// presses Submit and waits for: back on the list, or a message, or a pop-up
	private void submit_and_wait(H_MenuCard_Cls m) {
		m.Click_submit();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100))
					.until(d -> m.is_list_page() || !m.validation_messages().isEmpty() || !m.notice().isEmpty());
		} catch (Exception e) {
			// nothing appeared: the test checks the screen itself
		}
	}

	// fills a card with the first 'items' items of the category and submits it
	private void create_card(H_MenuCard_Cls m, String code, String name, String editable, int items) throws Exception {
		m.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_form_screen());
		m.Fill_header(code, name, "Created by automation", editable);
		m.Select_category(H_MenuCard_Cls.CATEGORY);
		m.Tick_first_items(items);
		submit_and_wait(m);
	}

	// ======================= LIST screen =======================

	// M01: the list screen shows Search, Create, table filter, the 5 column headers, the entries line and view/edit icons
	@Test
	public void M01_list_page_elements() {
		test = extent.createTest("M01 Menu Card list elements");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		Assert.assertTrue(m.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(m.verify_create_btn().isDisplayed(), "Create button is missing");
		Assert.assertTrue(m.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = m.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "CODE", "NAME", "IS EDITABLE", "NO OF ITEM", "ACTION" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(m.verify_entries().getText().contains("Showing"), "The 'Showing .. entries' line is missing");
		Assert.assertTrue(m.Wait_for_row(EXISTING_CODE), "The card " + EXISTING_CODE + " is not in the list");
		Assert.assertTrue(m.action_icon_count(EXISTING_CODE) >= 2, "The view and edit icons are missing in the row");
		test.pass("List screen elements are displayed");
	}
	// M02: the Search button opens the search form (code, name, Search)
	@Test
	public void M02_search_form_opens() {
		test = extent.createTest("M02 Search form opens");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_top_search();
		Assert.assertTrue(m.verify_search_code().isDisplayed(), "Code box of the search form is missing");
		Assert.assertTrue(m.verify_search_name().isDisplayed(), "Name box of the search form is missing");
		Assert.assertTrue(m.verify_search_go().isDisplayed(), "Search button of the search form is missing");
		test.pass("Search form is displayed");
	}

	// M03: search by an existing code finds that card only
	@Test
	public void M03_search_by_code() throws InterruptedException {
		test = extent.createTest("M03 Search by code");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Search_menu(EXISTING_CODE, "");
		Assert.assertTrue(m.Wait_for_row(EXISTING_CODE), "The card " + EXISTING_CODE + " was not found");
		Assert.assertEquals(m.result_count(), 1, "Only one card was expected");
		test.pass("Found " + EXISTING_CODE);
	}

	// M04: search by an existing name finds that card
	@Test
	public void M04_search_by_name() {
		test = extent.createTest("M04 Search by name");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Search_menu("", EXISTING_NAME);
		Assert.assertTrue(m.Wait_for_row(EXISTING_CODE), "The card with the name " + EXISTING_NAME + " was not found");
		test.pass("Found by name");
	}

	// M05: a code that does not exist gives an empty table
	@Test
	public void M05_search_no_match() throws InterruptedException {
		test = extent.createTest("M05 Search without match");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Search_menu("NOMATCH" + unique(), "");
		WaitUtil.settle(driver, 2000);
		Assert.assertEquals(m.result_count(), 0, "No card should be listed");
		test.pass("Empty result for an unknown code");
	}

	// M06: the "Search:" box above the table filters the rows
	@Test
	public void M06_table_filter() throws InterruptedException {
		test = extent.createTest("M06 Table filter");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.verify_table_filter().sendKeys("Foot");
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(m.Menu_codes().contains(EXISTING_CODE), "The filter hid the matching card");
		m.verify_table_filter().clear();
		test.pass("Table filter works");
	}

	// ======================= FORM screen: create =======================

	// M07: Create opens the form with all fields, the cart, Back and Submit
	@Test
	public void M07_create_form_elements() {
		test = extent.createTest("M07 Create form elements");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		Assert.assertTrue(m.verify_company().isDisplayed(), "Company is missing");
		Assert.assertTrue(m.verify_code().isDisplayed(), "Code is missing");
		Assert.assertTrue(m.verify_name().isDisplayed(), "Name is missing");
		Assert.assertTrue(m.verify_editable().isDisplayed(), "Is Editable is missing");
		Assert.assertTrue(m.verify_remarks().isDisplayed(), "Remarks is missing");
		Assert.assertTrue(m.verify_active().isDisplayed(), "Is Active is missing");
		Assert.assertTrue(m.verify_cart().isDisplayed(), "Cart button is missing");
		Assert.assertTrue(m.verify_category().isDisplayed(), "Category is missing");
		Assert.assertTrue(m.verify_sub_category().isDisplayed(), "Sub Category is missing");
		Assert.assertTrue(m.verify_item_search().isDisplayed(), "Item name search is missing");
		Assert.assertTrue(m.verify_back().isDisplayed(), "Back is missing");
		Assert.assertTrue(m.verify_submit().isDisplayed(), "Submit is missing");
		test.pass("All fields of the create form are displayed");
	}

	// M08: Company is fixed (greyed out) and shows the logged in company
	@Test
	public void M08_company_is_fixed() {
		test = extent.createTest("M08 Company is read-only");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		Assert.assertFalse(m.verify_company().isEnabled(), "Company must not be editable");
		Assert.assertTrue(m.selected(m.verify_company()).toUpperCase().contains("GOODWILL"),
				"Company shows: " + m.selected(m.verify_company()));
		test.pass("Company is read-only: " + m.selected(m.verify_company()));
	}

	// M09: Is Editable has Yes and No, the default is Yes
	@Test
	public void M09_is_editable_options() {
		test = extent.createTest("M09 Is Editable options");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		Assert.assertEquals(m.selected(m.verify_editable()), "Yes", "Default must be Yes");
		List<String> all = new ArrayList<String>();
		all.add(m.selected(m.verify_editable()));
		all.addAll(m.options_of(m.verify_editable()));
		test.info("Options: " + all);
		Assert.assertTrue(all.contains("No"), "The option No is missing");
		test.pass("Yes and No are available, Yes is the default");
	}

	// M10: Is Active is ticked by default and the cart starts at 0
	@Test
	public void M10_default_active_and_cart() {
		test = extent.createTest("M10 Defaults");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		Assert.assertTrue(m.verify_active().isSelected(), "Is Active must be ticked by default");
		Assert.assertEquals(m.cart_count(), 0, "The cart must start at 0");
		test.pass("Is Active is ticked, cart is 0");
	}

	// M11: Submit on an empty form shows the code, name and item messages
	@Test
	public void M11_empty_submit() {
		test = extent.createTest("M11 Empty submit");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Click_submit();
		List<String> messages = m.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(messages.contains("Please enter a code."), "Code message missing: " + messages);
		Assert.assertTrue(messages.contains("Please enter a name."), "Name message missing: " + messages);
		Assert.assertTrue(messages.contains("Please ensure at least one item is added to the menu card."),
				"Item message missing: " + messages);
		Assert.assertTrue(m.is_form_screen(), "The form must stay open");
		test.pass("All three messages are shown");
	}

	// M12: code and name filled, no item -> only the item message
	@Test
	public void M12_without_items() {
		test = extent.createTest("M12 Submit without items");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Fill_header("ZQMX" + unique(), "No item card", "", "");
		m.Click_submit();
		List<String> messages = m.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(messages.contains("Please ensure at least one item is added to the menu card."),
				"Item message missing: " + messages);
		Assert.assertFalse(messages.contains("Please enter a code."), "Code message must not be shown");
		Assert.assertFalse(messages.contains("Please enter a name."), "Name message must not be shown");
		test.pass("Only the item message is shown");
	}

	// M13: items ticked, code and name empty -> code and name messages, no item message
	@Test
	public void M13_without_code_and_name() {
		test = extent.createTest("M13 Submit without code and name");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Select_category(H_MenuCard_Cls.CATEGORY);
		m.Tick_first_items(1);
		m.Click_submit();
		List<String> messages = m.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(messages.contains("Please enter a code."), "Code message missing: " + messages);
		Assert.assertTrue(messages.contains("Please enter a name."), "Name message missing: " + messages);
		Assert.assertFalse(messages.contains("Please ensure at least one item is added to the menu card."),
				"Item message must not be shown");
		test.pass("Code and name messages are shown");
	}

	// ======================= FORM screen: select items =======================

	// M14: Sub Category has only the placeholder until a category is chosen
	@Test
	public void M14_sub_category_depends_on_category() {
		test = extent.createTest("M14 Sub category depends on category");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		Assert.assertTrue(m.options_of(m.verify_sub_category()).isEmpty(),
				"Sub Category must be empty before a category is chosen");
		m.Select_category(H_MenuCard_Cls.CATEGORY);
		List<String> subs = m.options_of(m.verify_sub_category());
		test.info("Sub categories of HOSE: " + subs);
		Assert.assertFalse(subs.isEmpty(), "Sub Category must list values after choosing a category");
		test.pass("Sub category follows the category");
	}

	// M15: choosing a category lists its items, a sub category narrows the list
	@Test
	public void M15_category_and_sub_category_filter() {
		test = extent.createTest("M15 Category and sub category filter");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Select_category(H_MenuCard_Cls.CATEGORY);
		Assert.assertTrue(m.item_row_count() > 0, "No items listed for the category");
		test.info("Items shown for HOSE (first page): " + m.item_row_count());

		m.Select_sub_category("Suction Hose Goodwill");
		Assert.assertTrue(m.item_row_count() > 0, "No items listed for the sub category");
		test.pass("Items are filtered by category and sub category");
	}

	// M16: ticking items gives the sort numbers 1, 2, 3 and the cart shows 3
	@Test
	public void M16_tick_items_sort_and_cart() {
		test = extent.createTest("M16 Tick items");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Select_category(H_MenuCard_Cls.CATEGORY);
		m.Tick_first_items(3);
		test.info("Sort numbers: " + m.sort_value(0) + ", " + m.sort_value(1) + ", " + m.sort_value(2));
		Assert.assertEquals(m.sort_value(0), "1");
		Assert.assertEquals(m.sort_value(1), "2");
		Assert.assertEquals(m.sort_value(2), "3");
		Assert.assertEquals(m.cart_count(), 3, "The cart must show 3");
		test.pass("Sort numbers 1,2,3 and cart 3");
	}

	// M17: unticking an item lowers the cart count and clears its sort number
	@Test
	public void M17_untick_item() {
		test = extent.createTest("M17 Untick item");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Select_category(H_MenuCard_Cls.CATEGORY);
		m.Tick_first_items(3);
		m.Untick_item(2);
		Assert.assertEquals(m.cart_count(), 2, "The cart must show 2");
		Assert.assertEquals(m.sort_value(2), "", "The sort number must be cleared");
		test.pass("Cart is 2 after unticking");
	}

	// M18: the cart pop-up lists the chosen items in sort order, Close shuts it
	@Test
	public void M18_cart_popup() {
		test = extent.createTest("M18 Cart pop-up");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Select_category(H_MenuCard_Cls.CATEGORY);
		m.Tick_first_items(2);
		String code_a = m.item_texts(0).get(0);
		String code_b = m.item_texts(1).get(0);

		m.Open_cart();
		List<List<String>> rows = m.cart_rows();
		test.info("Pop-up rows: " + rows);
		Assert.assertEquals(rows.size(), 2, "The pop-up must list 2 items");
		Assert.assertEquals(rows.get(0).get(1), code_a, "First row must be the first ticked item");
		Assert.assertEquals(rows.get(1).get(1), code_b, "Second row must be the second ticked item");
		m.Close_cart();
		Assert.assertFalse(m.cart_popup_open(), "The pop-up must close");
		test.pass("Cart pop-up lists the chosen items");
	}

	// M19: the item name search narrows the item table
	@Test
	public void M19_item_search() {
		test = extent.createTest("M19 Item name search");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Select_category(H_MenuCard_Cls.CATEGORY);
		String full = m.item_texts(0).get(1);
		String name = full.contains("\"") ? full.substring(full.lastIndexOf('"') + 1).trim() : full;
		m.Search_item(name);
		Assert.assertTrue(m.item_row_count() >= 1, "The searched item was not found: " + name);
		for (int i = 0; i < m.item_row_count(); i++) {
			Assert.assertTrue(m.item_texts(i).get(1).toLowerCase().contains(name.toLowerCase()),
					"A row that does not match the search is shown: " + m.item_texts(i));
		}
		test.pass("Search for '" + name + "' narrows the items");
	}
	

	// M20: a sort number with letters must not be accepted (probe, same kind of check as the Stock Item S24 defects)
	@Test
	public void M20_sort_number_letters() {
		test = extent.createTest("M20 Sort number with letters");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Select_category(H_MenuCard_Cls.CATEGORY);
		m.Tick_item(0);
		m.Set_sort(0, "abc");
		String value = m.sort_value(0);
		test.info("Value kept in the sort box: '" + value + "'");
		Assert.assertFalse(value.equals("abc"), "DEFECT: letters are accepted as a sort number");
		test.pass("Letters are not accepted as a sort number");
	}

	// ======================= create, view, edit =======================

	// M21: a full card (code, name, remarks, 3 items) is created and listed with 3 items and Is Editable Yes
	@Test
	public void M21_create_menu_card() throws Exception {
		test = extent.createTest("M21 Create menu card");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		test.info("Creating " + code1 + " / " + name1);
		create_card(m, code1, name1, "Yes", 3);
		Assert.assertTrue(m.is_list_page(), "Not back on the list. Messages: " + m.validation_messages()
				+ " Notice: " + m.notice());
		created.add(code1);

		m.Search_menu(code1, "");
		Assert.assertTrue(m.Wait_for_row(code1), "The new card is not in the list");
		List<String> row = m.row_of(code1);
		test.info("Row: " + row);
		Assert.assertEquals(row.get(1), name1);
		Assert.assertEquals(row.get(2), "Yes");
		Assert.assertEquals(row.get(3), "3", "No of item must be 3");
		test.pass("Card created and listed with 3 items");
	}

	// M22: a card with Is Editable = No is saved with No
	@Test(dependsOnMethods = "M21_create_menu_card")
	public void M22_create_not_editable_card() throws Exception {
		test = extent.createTest("M22 Create card with Is Editable No");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		code2 = "ZQN" + unique();
		create_card(m, code2, "ZQ NoEdit", "No", 2);
		Assert.assertTrue(m.is_list_page(), "Not back on the list. Messages: " + m.validation_messages());
		created.add(code2);

		m.Search_menu(code2, "");
		Assert.assertTrue(m.Wait_for_row(code2), "The new card is not in the list");
		Assert.assertEquals(m.row_of(code2).get(2), "No");
		Assert.assertEquals(m.row_of(code2).get(3), "2");
		test.pass("Card saved with Is Editable No and 2 items");
	}

	// M23: the same code again must be refused
	@Test(dependsOnMethods = "M21_create_menu_card")
	public void M23_duplicate_code() throws Exception {
		test = extent.createTest("M23 Duplicate code");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		create_card(m, code1, "ZQ Menu Duplicate", "Yes", 1);
		String message = m.validation_messages() + " " + m.notice();
		test.info("Message: " + message);
		boolean refused = !m.is_list_page() || !m.validation_messages().isEmpty() || !m.notice().isEmpty();
		if (m.is_list_page()) {
			m.Search_menu(code1, "");
			Assert.assertEquals(m.result_count(), 1, "DEFECT: the duplicate code " + code1 + " was saved again");
		}
		Assert.assertTrue(refused, "DEFECT: the duplicate code was accepted silently");
		test.pass("Duplicate code refused: " + message);
	}

	// M24: the view screen is read-only and shows the saved values and item count (existing card GOAT)
	@Test
	public void M24_view_screen() throws Exception {
		test = extent.createTest("M24 View screen");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Search_menu(EXISTING_CODE, "");
		Assert.assertTrue(m.Wait_for_row(EXISTING_CODE));
		int listed = Integer.parseInt(m.row_of(EXISTING_CODE).get(3));
		m.Click_view(EXISTING_CODE);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.verify_code().getAttribute("value").length() > 0);

		Assert.assertEquals(m.verify_code().getAttribute("value"), EXISTING_CODE);
		Assert.assertEquals(m.verify_name().getAttribute("value"), EXISTING_NAME);
		Assert.assertFalse(m.verify_code().isEnabled(), "Code must be read-only in the view screen");
		Assert.assertFalse(m.verify_name().isEnabled(), "Name must be read-only in the view screen");
		Assert.assertEquals(m.cart_count(), listed, "Cart must match the 'No of item' of the list");
		test.pass("View screen is read-only, " + listed + " items");
	}

	// M25: the edit screen shows the saved values of the new card with its 3 items ticked
	@Test(dependsOnMethods = "M21_create_menu_card")
	public void M25_edit_screen_values() throws Exception {
		test = extent.createTest("M25 Edit screen values");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Search_menu(code1, "");
		Assert.assertTrue(m.Wait_for_row(code1));
		m.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.verify_code().getAttribute("value").length() > 0);

		Assert.assertEquals(m.verify_code().getAttribute("value"), code1);
		Assert.assertEquals(m.verify_name().getAttribute("value"), name1);
		Assert.assertEquals(m.cart_count(), 3, "The 3 saved items must be loaded");
		Assert.assertTrue(m.verify_name().isEnabled(), "Name must be editable");
		test.pass("Edit screen shows the saved values");
	}

	// M26: a changed name is saved and shown in the list
	@Test(dependsOnMethods = "M21_create_menu_card")
	
	public void M26_edit_name() throws Exception {
		test = extent.createTest("M26 Edit name");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Search_menu(code1, "");
		Assert.assertTrue(m.Wait_for_row(code1));
		m.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.verify_code().getAttribute("value").length() > 0);

		name1 = name1 + "E";
		m.Fill_header(m.verify_code().getAttribute("value"), name1, "", "");
		submit_and_wait(m);
		Assert.assertTrue(m.is_list_page(), "Not back on the list. Messages: " + m.validation_messages());

		m.Search_menu(code1, "");
		Assert.assertTrue(m.Wait_for_row(code1));
		Assert.assertEquals(m.row_of(code1).get(1), name1);
		test.pass("Name changed to " + name1);
	}

	// M27: Back returns to the list without saving
	@Test
	public void M27_back_button() {
		test = extent.createTest("M27 Back button");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Fill_header("ZQMBACK", "Back test", "", "");
		m.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_list_page());
		m.Search_menu("ZQMBACK", "");
		Assert.assertFalse(m.Wait_for_row("ZQMBACK"), "A card was saved by pressing Back");
		test.pass("Back returns to the list without saving");
	}

	// M28: clean up - menu cards cannot be deleted, so every ZQM card is deactivated (Is Active unticked)
	@Test(dependsOnMethods = { "M21_create_menu_card", "M26_edit_name" }, alwaysRun = true)
	public void M28_deactivate_test_cards() throws Exception {
		test = extent.createTest("M28 Deactivate test cards");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		for (String code : created) {
			if (!m.is_list_page()) {
				go_to_menu_card();
			}
			m.Search_menu(code, "");
			if (!m.Wait_for_row(code)) {
				test.warning(code + " not found, skipped");
				continue;
			}
			m.Click_edit(code);
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.verify_code().getAttribute("value").length() > 0);
			m.Set_active(false);
			submit_and_wait(m);
			test.info(code + " deactivated, back on list: " + m.is_list_page());
			if (!m.is_list_page()) {
				go_to_menu_card();
			}
		}
		test.pass("Test cards deactivated: " + created);
	}
	
	// M29: the Selected Items pop-up must show the full stock name (names with an inch mark are cut)
	@Test
	public void M29_cart_popup_full_name() {
		test = extent.createTest("M29 Cart pop-up shows the full name");
		H_MenuCard_Cls m = new H_MenuCard_Cls(driver);

		m.Click_create();
		m.Select_category(H_MenuCard_Cls.CATEGORY);
		m.Tick_item(0);
		String name = m.item_texts(0).get(1);
		m.Open_cart();
		List<String> row = m.cart_rows().get(0);
		String shown = row.get(row.size() - 1);
		m.Close_cart();
		test.info("Item name: " + name + " | shown in the pop-up: " + shown);
		Assert.assertEquals(shown, name, "DEFECT: the pop-up cuts the stock name");
	}
}