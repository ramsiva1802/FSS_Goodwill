package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;
import PagePkg.J_RouteDayMapping_Cls;

/*
 * Route Day Mapping (Customer Details > Route Day Mapping) - functional tests, D01 .. D34.
 * Existing QA mappings used for reading only: employee apk (vehicle car) and RAM (vehicle bike) - never changed.
 * The mapping created by the tests (D25) is made for a Sales Executive that has no mapping yet (an "Auto" employee
 * or Sivakumar); it is deleted at the end (D33).
 * Vehicle rules: the Add (vehicle) button needs the hierarchy type and name first; a vehicle that is assigned to a
 * mapping is not offered in the Vehicle box; an unassigned vehicle is offered; after the mapping is deleted the
 * vehicle is offered again (D24c, D24d, D26b, D34).
 * A vehicle added in D24c cannot be deleted (the page has no delete for vehicles); it stays as a free vehicle.
 */
public class J_RouteDayMapping_Test extends BaseCls {

	static final String EXISTING_EMPLOYEE = "apk";
	static final String TYPE = "PLANT";
	static final String HIERARCHY = "GOODWILL POLYMERS FSS 2026-2027";
	static final String SETTING = "GPSE1";

	// vehicles that are already assigned to the existing mappings of apk and RAM
	static final String[] ASSIGNED_VEHICLES = { "car", "bike" };

	// the employee of the mapping created in D25 (text as shown in the Employee box) and its code for finding it
	String emp1 = "";
	String emp1_key = "";
	boolean mapping_created = false;

	// the vehicle created in D24c ("" when it was not created)
	String vehicle1 = "";

	// what the last Submit showed
	String last_alert = "";
	String last_notice = "";

	private String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
	}

	private String today() {
		return LocalDate.now().toString();
	}

	// runs ONCE: fresh session, login through the form
	@BeforeClass
	public void login_first() {
		login_again();
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

	// opens Customer Details > Route Day Mapping from the dashboard
	private void go_to_mapping() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		try {
			dash.Click_submenu_by_name("Customer Details", "Route Day Mapping");
		} catch (NoSuchElementException e) {
			dash.Click_home();
			dash.Click_submenu_containing("Customer Details", "Route Day Mapping");
		}
		WaitUtil.settle(driver, 1500);
	}

	// every test starts with a fresh Route Day Mapping list screen
	@BeforeMethod
	public void open_mapping() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);
		try {
			go_to_mapping();
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_mapping();
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_list_page());
		}
		remember_clean_screen();
	}

	// presses Submit and waits for: back on the list, or a message, or a pop-up / alert.
	// The alert / pop-up text is kept in last_alert / last_notice and then closed.
	private void submit_and_wait(J_RouteDayMapping_Cls m) {
		last_alert = "";
		last_notice = "";
		m.Click_submit();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> {
				String a = m.Accept_alert();
				if (!a.isEmpty()) {
					last_alert = a;
					return true;
				}
				return m.is_list_page() || !m.validation_messages().isEmpty() || !m.notice().isEmpty();
			});
		} catch (Exception e) {
			// nothing appeared: the test checks the screen itself
		}
		last_notice = m.notice();
		m.Close_notice();
		WaitUtil.settle(driver, 800);
		String late = m.Accept_alert();
		if (!late.isEmpty()) {
			last_alert = last_alert + " " + late;
		}
	}

	// everything the site said after the last Submit
	private String said(J_RouteDayMapping_Cls m) {
		return m.validation_messages() + " | notice: " + last_notice + " | alert: " + last_alert;
	}

	// true when one of the messages contains the key (capital letters ignored)
	private boolean has(List<String> messages, String key) {
		for (String s : messages) {
			if (s.toLowerCase().contains(key.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	// true when one of the suggestions is this vehicle (whole word, capital letters ignored)
	private boolean offers(List<String> suggestions, String vehicle) {
		Pattern word = Pattern.compile("\\b" + Pattern.quote(vehicle.toLowerCase()) + "\\b");
		for (String s : suggestions) {
			if (word.matcher(s.toLowerCase()).find()) {
				return true;
			}
		}
		return false;
	}

	// the code in brackets of "auto (em6670128)" = "em6670128"; the whole text when there are no brackets
	private String code_of(String cell) {
		int a = cell.lastIndexOf('(');
		int b = cell.lastIndexOf(')');
		if (a >= 0 && b > a) {
			return cell.substring(a + 1, b).trim();
		}
		return cell.trim();
	}

	// the name before the brackets of "auto (em6670128)" = "auto"
	private String name_of(String cell) {
		int a = cell.indexOf('(');
		return (a > 0 ? cell.substring(0, a) : cell).trim();
	}
	// finds a mapping in the list with the "Search:" box above the table
	private boolean find_mapping(J_RouteDayMapping_Cls m, String key) {
		m.Filter_table(key);
		return m.Wait_for_row(key);
	}

	// opens the create form
	private void open_create(J_RouteDayMapping_Cls m) {
		m.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_form_screen());
	}

	// opens the create form and chooses hierarchy type and hierarchy name (this loads the routes and the vehicles)
	private void open_create_with_hierarchy(J_RouteDayMapping_Cls m) {
		open_create(m);
		m.Select_type(TYPE);
		m.Select_hierarchy(HIERARCHY);
	}

	// fills the header fields with an existing employee (the save is never reached in the negative tests)
	private void fill_header(J_RouteDayMapping_Cls m, boolean employee, boolean settings, boolean date) {
		open_create_with_hierarchy(m);
		if (employee) {
			m.Select_employee(EXISTING_EMPLOYEE);
		}
		if (settings) {
			m.Select_settings(SETTING);
		}
		if (date) {
			m.Set_from_date(today());
		}
	}

	// ======================= LIST screen =======================

	// D01: the list shows Search, Create, table filter, the 6 column headers, the entries line and 3 icons in a row
	@Test
	public void D01_list_page_elements() {
		test = extent.createTest("D01 Route Day Mapping list elements");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		Assert.assertTrue(m.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(m.verify_create_btn().isDisplayed(), "Create button is missing");
		Assert.assertTrue(m.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");

		List<String> headers = m.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "EMPLOYEE", "SETTING CODE", "VEHICLE", "NO.OF ROUTES", "HIERARCHY NAME", "ACTION" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(m.verify_entries().getText().contains("Showing"), "The 'Showing .. entries' line is missing");
		Assert.assertTrue(m.Wait_for_row(EXISTING_EMPLOYEE), "The mapping of " + EXISTING_EMPLOYEE + " is not in the list");
		Assert.assertEquals(m.action_icon_count(EXISTING_EMPLOYEE), 3, "The view, edit and delete icons are expected");
		test.pass("List screen elements are displayed");
	}

	// D02: the Search button opens the search form with its 4 fields, Search and Clear; defaults are
	// SALES ORGANIZATION and All
	@Test
	public void D02_search_form_opens() {
		test = extent.createTest("D02 Search form opens");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		m.Click_top_search();
		Assert.assertTrue(m.verify_search_type().isDisplayed(), "Hierarchy Type is missing");
		Assert.assertTrue(m.verify_search_hierarchy().isDisplayed(), "Hierarchy Name is missing");
		Assert.assertTrue(m.verify_search_employee().isDisplayed(), "Employee is missing");
		Assert.assertTrue(m.verify_search_setting().isDisplayed(), "Setting Code is missing");
		Assert.assertTrue(m.verify_search_go().isDisplayed(), "Search button of the form is missing");
		Assert.assertTrue(m.verify_clear().isDisplayed(), "Clear button is missing");
		Assert.assertEquals(m.selected(m.verify_search_type()), "SALES ORGANIZATION", "Default hierarchy type");
		Assert.assertEquals(m.selected(m.verify_search_setting()), "All", "Default setting code");
		test.pass("Search form is displayed with its defaults");
	}

	// D03: the hierarchy types offer PLANT and SALES ORGANIZATION; the setting code has values only after a
	// hierarchy type and a hierarchy name are chosen
	@Test
	public void D03_search_form_options() {
		test = extent.createTest("D03 Search form options");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		m.Open_search_form();
		List<String> types = new ArrayList<String>();
		types.add(m.selected(m.verify_search_type()));
		types.addAll(m.options_of(m.verify_search_type()));
		test.info("Hierarchy types: " + types);
		Assert.assertTrue(types.contains("PLANT"), "PLANT is missing: " + types);
		Assert.assertTrue(types.contains("SALES ORGANIZATION"), "SALES ORGANIZATION is missing: " + types);

		test.info("Setting codes before a hierarchy is chosen: " + m.options_of(m.verify_search_setting()));
		m.Select_search_type(TYPE);
		m.Select_search_hierarchy(HIERARCHY);
		List<String> settings = m.options_of(m.verify_search_setting());
		test.info("Setting codes after choosing the hierarchy: " + settings);
		Assert.assertTrue(settings.contains(SETTING), "Setting code " + SETTING + " is missing: " + settings);
		test.pass("Search form drop downs are filled");
	}

	// D04: search by employee finds that employee's mapping
	@Test
	public void D04_search_by_employee() {
		test = extent.createTest("D04 Search by employee");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		m.Search_mapping(TYPE, "", EXISTING_EMPLOYEE, "");
		Assert.assertTrue(m.Wait_for_row(EXISTING_EMPLOYEE), "The mapping of " + EXISTING_EMPLOYEE + " was not found");
		for (List<String> row : m.result_texts()) {
			Assert.assertTrue(row.get(0).toLowerCase().contains(EXISTING_EMPLOYEE), "A row of another employee: " + row);
		}
		test.pass("Found the mapping of " + EXISTING_EMPLOYEE);
	}

	// D05: search by hierarchy and setting code lists only that setting code
	@Test
	public void D05_search_by_setting_code() {
		test = extent.createTest("D05 Search by setting code");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		m.Search_mapping(TYPE, HIERARCHY, "", SETTING);
		Assert.assertTrue(m.result_count() > 0, "No mapping was found for " + SETTING);
		for (List<String> row : m.result_texts()) {
			Assert.assertEquals(row.get(1), SETTING, "A row of another setting code: " + row);
		}
		test.pass("Only " + SETTING + " rows are listed");
	}

	// D06: an employee that does not exist gives an empty table
	@Test
	public void D06_search_no_match() {
		test = extent.createTest("D06 Search without match");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		m.Search_mapping(TYPE, "", "NOMATCH" + unique(), "");
		Assert.assertEquals(m.result_count(), 0, "No mapping should be listed");
		test.pass("Empty result for an unknown employee");
	}

	// D07: Clear lists the rows again and empties the search form
	@Test
	public void D07_clear_button() {
		test = extent.createTest("D07 Clear button");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		m.Search_mapping(TYPE, "", EXISTING_EMPLOYEE, "");
		int filtered = m.result_count();
		m.Click_clear();
		test.info("Rows after the search: " + filtered + ", after Clear: " + m.result_count());
		Assert.assertTrue(m.result_count() > filtered, "Clear must list more rows than the filtered search");
		m.Open_search_form();
		Assert.assertEquals(m.verify_search_employee().getAttribute("value"), "", "The employee box must be empty");
		test.pass("Clear resets the search");
	}

	// D08: the "Search:" box above the table filters the rows
	@Test
	public void D08_table_filter() {
		test = extent.createTest("D08 Table filter");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		m.Filter_table(EXISTING_EMPLOYEE);
		Assert.assertTrue(m.result_count() > 0, "The filter hid every row");
		for (List<String> row : m.result_texts()) {
			Assert.assertTrue(row.toString().toLowerCase().contains(EXISTING_EMPLOYEE), "A row that does not match: " + row);
		}
		test.pass("Table filter works");
	}

	// D09: Next shows the second page, Previous goes back to "Showing 1 to 10"
	@Test
	public void D09_paging() throws InterruptedException {
		test = extent.createTest("D09 Paging");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		Assert.assertTrue(m.verify_entries().getText().contains("Showing 1 to 10"), m.verify_entries().getText());
		m.Click_next();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(m.verify_entries().getText().contains("Showing 11 to"), m.verify_entries().getText());
		m.Click_previous();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(m.verify_entries().getText().contains("Showing 1 to 10"), m.verify_entries().getText());
		test.pass("Next and Previous work");
	}

	// ======================= FORM screen: create =======================

	// D10: Create opens the form with all fields, the route table, Back and Submit
	@Test
	public void D10_create_form_elements() {
		test = extent.createTest("D10 Create form elements");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create(m);
		Assert.assertTrue(m.verify_company().isDisplayed(), "Company is missing");
		Assert.assertTrue(m.verify_type().isDisplayed(), "Hierarchy Type is missing");
		Assert.assertTrue(m.verify_hierarchy().isDisplayed(), "Hierarchy Name is missing");
		Assert.assertTrue(m.verify_employee().isDisplayed(), "Employee is missing");
		Assert.assertTrue(m.verify_settings().isDisplayed(), "Settings Code is missing");
		Assert.assertTrue(m.verify_vehicle().isDisplayed(), "Vehicle is missing");
		Assert.assertTrue(m.verify_add_vehicle().isDisplayed(), "Add (vehicle) button is missing");
		Assert.assertTrue(m.verify_from_date().isDisplayed(), "From Date is missing");
		Assert.assertTrue(m.verify_route_filter().isDisplayed(), "Search Route box is missing");
		Assert.assertTrue(m.verify_all_box().isDisplayed(), "The ALL tick box is missing");
		Assert.assertTrue(m.verify_back().isDisplayed(), "Back is missing");
		Assert.assertTrue(m.verify_submit().isDisplayed(), "Submit is missing");
		test.pass("All fields of the create form are displayed");
	}

	// D11: Company is fixed (greyed out) and shows the logged in company
	@Test
	public void D11_company_is_fixed() {
		test = extent.createTest("D11 Company is read-only");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create(m);
		Assert.assertFalse(m.verify_company().isEnabled(), "Company must not be editable");
		Assert.assertTrue(m.selected(m.verify_company()).toUpperCase().contains("GOODWILL"),
				"Company shows: " + m.selected(m.verify_company()));
		test.pass("Company is read-only: " + m.selected(m.verify_company()));
	}

	// D12: Hierarchy Type starts on its placeholder and offers PLANT
	@Test
	public void D12_hierarchy_type_options() {
		test = extent.createTest("D12 Hierarchy type options");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create(m);
		Assert.assertEquals(m.selected(m.verify_type()), "HierarchyType", "Placeholder expected");
		List<String> options = m.options_of(m.verify_type());
		test.info("Options: " + options);
		Assert.assertTrue(options.contains(TYPE), TYPE + " is missing: " + options);
		test.pass("Hierarchy types: " + options);
	}

	// D13: Submit on an empty form shows the six messages (and the form stays open)
	@Test
	public void D13_empty_submit() {
		test = extent.createTest("D13 Empty submit");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create(m);
		m.Click_submit();
		List<String> messages = m.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(has(messages, "hierarchy type"), "Hierarchy type message missing: " + messages);
		Assert.assertTrue(has(messages, "hierarchy name"), "Hierarchy name message missing: " + messages);
		Assert.assertTrue(has(messages, "employee"), "Employee message missing: " + messages);
		Assert.assertTrue(has(messages, "settings code"), "Settings code message missing: " + messages);
		Assert.assertTrue(has(messages, "from date"), "From date message missing: " + messages);
		Assert.assertTrue(has(messages, "at least one route"), "Route message missing: " + messages);
		Assert.assertTrue(m.is_form_screen(), "The form must stay open");
		test.pass("All six messages are shown");
	}

	// D14: the route table is empty until a hierarchy is chosen, then it lists the routes
	@Test
	public void D14_routes_load_after_hierarchy() {
		test = extent.createTest("D14 Routes load after hierarchy");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create(m);
		Assert.assertEquals(m.route_row_count(), 0, "The route table must be empty before a hierarchy is chosen");
		m.Select_type(TYPE);
		m.Select_hierarchy(HIERARCHY);
		test.info("Routes listed: " + m.route_row_count());
		Assert.assertTrue(m.route_row_count() > 0, "No routes were listed for the hierarchy");
		test.pass("Routes are listed after choosing the hierarchy");
	}

	// D15: Settings Code offers GPSE1 once the hierarchy is chosen
	@Test
	public void D15_settings_code_options() {
		test = extent.createTest("D15 Settings code options");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create_with_hierarchy(m);
		List<String> options = m.options_of(m.verify_settings());
		test.info("Settings codes: " + options);
		Assert.assertTrue(options.contains(SETTING), SETTING + " is missing: " + options);
		test.pass("Settings code options: " + options);
	}

	// D16: ticking a route ticks all 7 days, unticking it clears them
	@Test
	public void D16_route_ticks_all_days() {
		test = extent.createTest("D16 Route ticks all days");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create_with_hierarchy(m);
		Assert.assertEquals(m.day_box_count(0), 7, "Seven day tick boxes are expected");
		m.Tick_route(0);
		test.info("Days after ticking the route: " + m.day_states(0));
		Assert.assertEquals(m.ticked_day_count(0), 7, "All 7 days must be ticked");
		m.Untick_route(0);
		Assert.assertEquals(m.ticked_day_count(0), 0, "All days must be cleared");
		test.pass("Route tick box controls the 7 days");
	}

	// D17: ticking one day ticks its route (only that day), unticking the day unticks the route
	@Test
	public void D17_day_ticks_route() {
		test = extent.createTest("D17 Day ticks route");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create_with_hierarchy(m);
		m.Tick_day(1, 3);
		test.info("Days: " + m.day_states(1) + ", route ticked: " + m.route_ticked(1));
		Assert.assertTrue(m.route_ticked(1), "The route must be ticked when a day is ticked");
		Assert.assertEquals(m.ticked_day_count(1), 1, "Only the chosen day must be ticked");
		m.Untick_day(1, 3);
		Assert.assertFalse(m.route_ticked(1), "The route must be unticked when its last day is unticked");
		test.pass("Day tick box controls the route tick box");
	}

	// D18: the ALL tick box ticks every route, unticking it clears every route
	@Test
	public void D18_all_checkbox() {
		test = extent.createTest("D18 ALL tick box");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create_with_hierarchy(m);
		m.Tick_all();
		Assert.assertEquals(m.ticked_route_count(), m.route_row_count(), "Every route must be ticked");
		Assert.assertEquals(m.ticked_day_count(0), 7, "Every day must be ticked");
		m.Untick_all();
		Assert.assertEquals(m.ticked_route_count(), 0, "No route must stay ticked");
		test.pass("ALL tick box works");
	}

	// D19: the Search Route box narrows the route table
	@Test
	public void D19_route_filter() {
		test = extent.createTest("D19 Search route");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create_with_hierarchy(m);
		int all = m.route_row_count();
		m.Filter_routes("CITY");
		test.info("Routes: " + all + " -> " + m.route_row_count());
		Assert.assertTrue(m.route_row_count() > 0, "No route matches CITY");
		Assert.assertTrue(m.route_row_count() < all, "The filter did not narrow the routes");
		for (String name : m.route_names()) {
			Assert.assertTrue(name.toUpperCase().contains("CITY"), "A route that does not match: " + name);
		}
		test.pass("Route filter works");
	}

	// D20: everything filled but no route ticked -> the route message
	@Test
	public void D20_missing_routes() {
		test = extent.createTest("D20 No route ticked");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		fill_header(m, true, true, true);
		m.Click_submit();
		List<String> messages = m.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(has(messages, "at least one route"), "Route message missing: " + messages);
		Assert.assertTrue(m.is_form_screen(), "The form must stay open");
		test.pass("Route message is shown");
	}

	// D21: employee missing -> the employee message
	@Test
	public void D21_missing_employee() {
		test = extent.createTest("D21 Missing employee");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		fill_header(m, false, true, true);
		m.Tick_first_routes(1);
		m.Click_submit();
		List<String> messages = m.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(has(messages, "employee"), "Employee message missing: " + messages);
		test.pass("Employee message is shown");
	}

	// D22: from date missing -> the date message
	@Test
	public void D22_missing_from_date() {
		test = extent.createTest("D22 Missing from date");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		fill_header(m, true, true, false);
		m.Tick_first_routes(1);
		m.Click_submit();
		List<String> messages = m.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(has(messages, "from date"), "From date message missing: " + messages);
		test.pass("From date message is shown");
	}

	// D23: settings code missing -> the settings code message
	@Test
	public void D23_missing_settings_code() {
		test = extent.createTest("D23 Missing settings code");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		fill_header(m, true, false, true);
		m.Tick_first_routes(1);
		m.Click_submit();
		List<String> messages = m.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(has(messages, "settings code"), "Settings code message missing: " + messages);
		test.pass("Settings code message is shown");
	}

	// ======================= vehicles =======================

	// D24a: the Add (vehicle) button does nothing until the hierarchy type and hierarchy name are chosen
	@Test
	public void D24a_add_vehicle_needs_hierarchy() {
		test = extent.createTest("D24a Add vehicle needs the hierarchy");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create(m);
		m.Click_add_vehicle();
		boolean opened = m.add_vehicle_open();
		test.info("Pop-up opened: " + opened + " | messages: " + m.validation_messages() + " | notice: " + m.notice());
		if (opened) {
			m.Close_add_vehicle();
		}
		Assert.assertFalse(opened, "The Add Vehicle pop-up must not open before the hierarchy type and name are chosen");
		test.pass("Add Vehicle needs the hierarchy first");
	}

	// D24b: after choosing the hierarchy, Add opens the pop-up (code, name, Submit); an empty Submit keeps it open;
	// the x closes it
	@Test
	public void D24b_add_vehicle_popup() {
		test = extent.createTest("D24b Add Vehicle pop-up");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create_with_hierarchy(m);
		m.Open_add_vehicle();
		Assert.assertTrue(m.verify_vehicle_code().isDisplayed(), "Vehicle Code is missing");
		Assert.assertTrue(m.verify_vehicle_name().isDisplayed(), "Vehicle Name is missing");
		m.Click_vehicle_submit();
		test.info("Empty pop-up submit shows: " + m.validation_messages() + " | notice: " + m.notice());
		Assert.assertTrue(m.add_vehicle_open(), "The pop-up must stay open when it is submitted empty");
		m.Close_add_vehicle();
		Assert.assertFalse(m.add_vehicle_open(), "The pop-up must close");
		test.pass("Add Vehicle pop-up works");
	}

	// D24c: a new vehicle that is not assigned to any mapping is offered in the Vehicle box
	// (also reports when it is offered only after the form is opened again)
	@Test
	public void D24c_new_vehicle_is_offered() throws Exception {
		test = extent.createTest("D24c New vehicle is offered");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		String code = "ZQV" + unique();
		open_create_with_hierarchy(m);
		m.Open_add_vehicle();
		m.Add_vehicle(code, code);
		test.info("Site said: " + m.validation_messages() + " | notice: " + m.notice() + " | alert: " + m.Accept_alert());
		m.Close_notice();
		if (m.add_vehicle_open()) {
			test.info("The pop-up is still open after Submit");
			m.Close_add_vehicle();
		}

		List<String> offered = m.vehicle_suggestions(code);
		test.info("Vehicle box offers right after adding: " + offered);
		if (!offers(offered, code)) {
			test.info("Vehicle box HTML: " + m.vehicle_box_html());
			go_to_mapping();
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_list_page());
			open_create_with_hierarchy(m);
			offered = m.vehicle_suggestions(code);
			test.info("Vehicle box offers after opening the form again: " + offered);
			Assert.assertTrue(offers(offered, code),
					"The new unassigned vehicle " + code + " is not offered, not even after reopening the form: " + offered);
			test.warning("The new vehicle is offered only after the form is opened again");
		}
		vehicle1 = code;
		test.pass("Vehicle " + code + " is offered");
	}

	// D24d: vehicles that are already assigned to a mapping (car, bike) are not offered.
	// The new free vehicle of D24c must be offered first, otherwise "nothing offered" proves nothing.
	@Test(dependsOnMethods = "D24c_new_vehicle_is_offered")
	public void D24d_assigned_vehicle_not_offered() {
		test = extent.createTest("D24d Assigned vehicle is not offered");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create_with_hierarchy(m);
		List<String> free = m.vehicle_suggestions(vehicle1);
		Assert.assertTrue(offers(free, vehicle1), "The Vehicle box does not offer the free vehicle " + vehicle1 + ": " + free);
		for (String vehicle : ASSIGNED_VEHICLES) {
			List<String> offered = m.vehicle_suggestions(vehicle);
			test.info("Typing '" + vehicle + "' offers: " + offered);
			Assert.assertFalse(offers(offered, vehicle),
					"DEFECT: the vehicle " + vehicle + " is already assigned but it is offered again: " + offered);
		}
		test.pass("Assigned vehicles are not offered");
	}
	// ======================= create, view, edit, delete =======================

	// D25: a mapping for a Sales Executive without a mapping, the new vehicle and 2 routes is created and
	// listed with 2 routes. The new row is found by comparing the list before and after the save.
	@Test(dependsOnMethods = "D24c_new_vehicle_is_offered", alwaysRun = true)
	public void D25_create_mapping() throws InterruptedException {
		test = extent.createTest("D25 Create mapping");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		// the employees that already have a mapping (all pages)
		Set<String> mapped = new HashSet<String>(m.Mapped_employees());
		test.info("Employees with a mapping: " + mapped);
		go_to_mapping();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_list_page());

		open_create_with_hierarchy(m);
		emp1 = m.Select_free_employee(mapped);
		Assert.assertNotNull(emp1, "No employee without a mapping was found in the Employee box");
		test.info("Employee chosen: " + emp1);

		m.Select_settings(SETTING);
		m.Set_from_date(today());
		if (!vehicle1.isEmpty()) {
			m.Select_vehicle(vehicle1);
			test.info("Vehicle box shows: " + m.verify_vehicle().getAttribute("value"));
		}
		m.Tick_first_routes(2);
		Assert.assertEquals(m.ticked_route_count(), 2);
		submit_and_wait(m);
		test.info("Site said: " + said(m));
		Assert.assertTrue(m.is_list_page(), "Not back on the list. " + said(m));

		// the new mapping is the row that was not in the list before
		List<String> after = m.Mapped_employees();
		test.info("Employees in the list after the save: " + after);
		String added = null;
		for (String a : after) {
			if (!mapped.contains(a)) {
				added = a;
				break;
			}
		}
		Assert.assertNotNull(added, "The new mapping is not in the list. Employee chosen: " + emp1
				+ " | site said: " + said(m));
		emp1_key = added;
		mapping_created = true;

		go_to_mapping();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_list_page());
		Assert.assertTrue(find_mapping(m, emp1_key), "The new mapping cannot be found with the list filter");
		List<String> row = m.row_of(emp1_key);
		test.info("Row: " + row);
		Assert.assertEquals(row.get(1), SETTING, "Setting code");
		if (!vehicle1.isEmpty()) {
			Assert.assertTrue(row.get(2).toLowerCase().contains(vehicle1.toLowerCase()), "Vehicle: " + row.get(2));
		}
		Assert.assertEquals(row.get(3), "2", "No. of routes");
		Assert.assertEquals(row.get(4), HIERARCHY, "Hierarchy name");
		test.pass("Mapping created for " + emp1);
	}

	// D26: a second mapping for the same employee must not be possible: the employee is not offered again,
	// or the save is refused
	@Test(dependsOnMethods = "D25_create_mapping")
	public void D26_duplicate_employee() {
		test = extent.createTest("D26 Duplicate employee mapping");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create_with_hierarchy(m);
		String code = code_of(emp1_key);
		List<String> offered = m.employee_suggestions(name_of(emp1_key));
		test.info("Employee box offers for '" + name_of(emp1_key) + "': " + offered);
		Assert.assertFalse(offered.isEmpty(), "The Employee box offers nothing for '" + name_of(emp1_key) + "'");
		if (!offers(offered, code)) {
			test.pass("The employee that already has a mapping is not offered again");
			return;
		}
		m.Select_employee(code);
		m.Select_settings(SETTING);
		m.Set_from_date(today());
		m.Tick_first_routes(1);
		submit_and_wait(m);
		test.info("Site said: " + said(m));
		if (m.is_list_page()) {
			m.Filter_table(emp1_key);
			m.Wait_for_row(emp1_key);
			int same = 0;
			for (List<String> row : m.result_texts()) {
				if (row.get(0).toLowerCase().contains(emp1_key.toLowerCase())) {
					same++;
				}
			}
			Assert.assertEquals(same, 1, "DEFECT: a second mapping was saved for " + emp1_key);
		}
		boolean refused = !m.is_list_page() || !last_notice.isEmpty() || !last_alert.isEmpty();
		Assert.assertTrue(refused, "DEFECT: the duplicate mapping was accepted silently");
		test.pass("Duplicate refused: " + said(m));
	}

	// D26b: the vehicle that is now assigned to the new mapping is no longer offered
	@Test(dependsOnMethods = "D25_create_mapping")
	public void D26b_assigned_new_vehicle_not_offered() {
		test = extent.createTest("D26b Vehicle assigned in D25 is not offered");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		if (vehicle1.isEmpty()) {
			throw new SkipException("No vehicle was created in D24c");
		}
		open_create_with_hierarchy(m);
		List<String> offered = m.vehicle_suggestions(vehicle1);
		test.info("Vehicle box offers for " + vehicle1 + ": " + offered);
		Assert.assertFalse(offers(offered, vehicle1),
				"DEFECT: the vehicle " + vehicle1 + " is assigned to a mapping but it is offered again: " + offered);
		test.pass("An assigned vehicle is not offered");
	}

	// D27: the view screen of the existing mapping is read-only: fields and tick boxes locked, only Back
	// (the Hierarchy Type is checked separately in D27b)
	@Test
	public void D27_view_screen() {
		test = extent.createTest("D27 View screen");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		m.Search_mapping(TYPE, "", EXISTING_EMPLOYEE, "");
		Assert.assertTrue(m.Wait_for_row(EXISTING_EMPLOYEE));
		m.Click_view(EXISTING_EMPLOYEE);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.verify_employee().getAttribute("value").length() > 0);

		Assert.assertTrue(m.verify_employee().getAttribute("value").toLowerCase().contains(EXISTING_EMPLOYEE));
		Assert.assertTrue(m.is_locked(m.verify_hierarchy()), "Hierarchy name must be read-only");
		Assert.assertTrue(m.is_locked(m.verify_employee()), "Employee must be read-only");
		Assert.assertTrue(m.is_locked(m.verify_settings()), "Settings code must be read-only");
		Assert.assertTrue(m.is_locked(m.verify_vehicle()), "Vehicle must be read-only");
		Assert.assertTrue(m.is_locked(m.verify_from_date()), "From date must be read-only");
		Assert.assertTrue(m.is_locked(m.verify_add_vehicle()), "Add (vehicle) must be disabled");
		Assert.assertTrue(m.ticked_route_count() > 0, "The mapped routes must be ticked");
		Assert.assertTrue(m.route_box_locked(0), "The route tick boxes must be locked");
		Assert.assertFalse(m.submit_visible(), "Submit must not be shown in the view screen");
		Assert.assertTrue(m.verify_back().isDisplayed(), "Back is missing");
		test.pass("View screen is read-only");
	}

	// D27b: in the view screen the Hierarchy Type must not be changeable (it looks greyed out)
	@Test
	public void D27b_view_screen_hierarchy_type() {
		test = extent.createTest("D27b View screen hierarchy type");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		m.Search_mapping(TYPE, "", EXISTING_EMPLOYEE, "");
		Assert.assertTrue(m.Wait_for_row(EXISTING_EMPLOYEE));
		m.Click_view(EXISTING_EMPLOYEE);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.verify_employee().getAttribute("value").length() > 0);

		test.info("Hierarchy type field: " + m.control_info(m.verify_type()));
		String result = m.Try_change_type();
		test.info("Trying to change it: " + result);
		Assert.assertFalse(result.startsWith("changed"),
				"DEFECT: the Hierarchy Type can be changed in the View screen (" + result + ")");
		test.pass("Hierarchy type is locked in the view screen: " + result);
	}
	// D28: the edit screen of the new mapping shows the saved values; hierarchy, employee and vehicle are locked,
	// settings code and from date can be changed
	@Test(dependsOnMethods = "D25_create_mapping")
	public void D28_edit_screen_values() {
		test = extent.createTest("D28 Edit screen values");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		Assert.assertTrue(find_mapping(m, emp1_key));
		m.Click_edit(emp1_key);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.verify_employee().getAttribute("value").length() > 0);

		Assert.assertTrue(m.verify_employee().getAttribute("value").toLowerCase().contains(name_of(emp1_key).toLowerCase()));
		Assert.assertEquals(m.verify_hierarchy().getAttribute("value"), HIERARCHY);
		Assert.assertEquals(m.selected(m.verify_settings()), SETTING);
		Assert.assertEquals(m.ticked_route_count(), 2, "The 2 saved routes must be ticked");
		Assert.assertTrue(m.is_locked(m.verify_employee()), "Employee must be locked in the edit screen");
		Assert.assertTrue(m.is_locked(m.verify_hierarchy()), "Hierarchy name must be locked in the edit screen");
		Assert.assertFalse(m.is_locked(m.verify_settings()), "Settings code must be editable");
		Assert.assertFalse(m.is_locked(m.verify_from_date()), "From date must be editable");
		Assert.assertTrue(m.submit_visible(), "Submit must be shown in the edit screen");
		test.pass("Edit screen shows the saved values");
	}

	// D29: untick one route in the edit screen -> the list shows 1 route
	@Test(dependsOnMethods = "D25_create_mapping")
	public void D29_edit_routes() {
		test = extent.createTest("D29 Edit routes");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		Assert.assertTrue(find_mapping(m, emp1_key));
		m.Click_edit(emp1_key);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.verify_employee().getAttribute("value").length() > 0);

		for (int i = 0; i < m.route_row_count(); i++) {
			if (m.route_ticked(i)) {
				m.Untick_route(i);
				break;
			}
		}
		Assert.assertEquals(m.ticked_route_count(), 1);
		submit_and_wait(m);
		test.info("Site said: " + said(m));
		Assert.assertTrue(m.is_list_page(), "Not back on the list. " + said(m));

		Assert.assertTrue(find_mapping(m, emp1_key));
		Assert.assertEquals(m.row_of(emp1_key).get(3), "1", "No. of routes after the edit");
		test.pass("The edit is saved: 1 route");
	}

	// D30: Back in the edit screen leaves the mapping unchanged
	@Test(dependsOnMethods = "D25_create_mapping")
	public void D30_edit_back_without_saving() {
		test = extent.createTest("D30 Back in edit screen");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		Assert.assertTrue(find_mapping(m, emp1_key));
		String before = m.row_of(emp1_key).get(3);
		m.Click_edit(emp1_key);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.verify_employee().getAttribute("value").length() > 0);
		m.Tick_all();
		m.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_list_page());

		Assert.assertTrue(find_mapping(m, emp1_key));
		Assert.assertEquals(m.row_of(emp1_key).get(3), before, "The routes were changed by pressing Back");
		test.pass("Back does not save");
	}

	// D31: Back in the create screen returns to the list
	@Test
	public void D31_back_button_create() {
		test = extent.createTest("D31 Back in create screen");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		open_create_with_hierarchy(m);
		m.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> m.is_list_page());
		Assert.assertTrue(m.is_list_page());
		test.pass("Back returns to the list");
	}

	// D32: Cancel in the delete confirmation keeps the mapping
	@Test(dependsOnMethods = "D25_create_mapping")
	public void D32_delete_cancel() {
		test = extent.createTest("D32 Delete cancelled");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		Assert.assertTrue(find_mapping(m, emp1_key));
		m.Click_delete(emp1_key);
		String text = m.Cancel_delete();
		test.info("Confirmation text: " + text);
		Assert.assertFalse(text.isEmpty(), "No delete confirmation was shown");
		Assert.assertTrue(find_mapping(m, emp1_key), "The mapping was deleted although Cancel was pressed");
		test.pass("Mapping kept after Cancel");
	}

	// D33: delete the created mapping: confirmation, then it is gone from the list.
	// Skipped when D25 did not create a mapping, so no other mapping can be deleted by mistake.
	@Test(dependsOnMethods = { "D25_create_mapping", "D26_duplicate_employee", "D26b_assigned_new_vehicle_not_offered",
			"D28_edit_screen_values", "D29_edit_routes", "D30_edit_back_without_saving",
			"D32_delete_cancel" }, alwaysRun = true)
	public void D33_delete_mapping() {
		test = extent.createTest("D33 Delete mapping");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		if (!mapping_created) {
			throw new SkipException("No mapping was created in D25, nothing to delete");
		}
		Assert.assertTrue(find_mapping(m, emp1_key), "The mapping of " + emp1_key + " to delete is not in the list");
		m.Click_delete(emp1_key);
		List<String> messages = m.Confirm_delete();
		test.info("Messages: " + messages);
		Assert.assertFalse(messages.isEmpty(), "No delete confirmation was shown");

		m.Filter_table(emp1_key);
		Assert.assertTrue(m.Wait_for_no_row(emp1_key), "DEFECT: the mapping is still listed after Delete");
		test.pass("Mapping deleted: " + messages);
	}

	// D34: after the mapping is deleted, its vehicle is free again and offered in the Vehicle box
	@Test(dependsOnMethods = "D33_delete_mapping", alwaysRun = true)
	public void D34_vehicle_offered_after_delete() {
		test = extent.createTest("D34 Vehicle offered again after delete");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		if (!mapping_created || vehicle1.isEmpty()) {
			throw new SkipException("No mapping with a new vehicle was created");
		}
		open_create_with_hierarchy(m);
		List<String> offered = m.vehicle_suggestions(vehicle1);
		test.info("Vehicle box offers for " + vehicle1 + ": " + offered);
		Assert.assertTrue(offers(offered, vehicle1),
				"DEFECT: the vehicle " + vehicle1 + " is free again after the delete but it is not offered: " + offered);
		test.pass("The vehicle is offered again after the mapping is deleted");
	}
}