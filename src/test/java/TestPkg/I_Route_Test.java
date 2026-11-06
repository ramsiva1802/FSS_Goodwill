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
import PagePkg.I_Route_Cls;

/*
 * Route (Customer Details > Route) - functional tests, R01 .. R29.
 * Existing QA route used for reading only: R1 / Nedumangad Route / PLANT / GOODWILL POLYMERS FSS 2026-2027
 * (it is never changed or deleted). Routes created by the tests get the code prefix ZR and are deleted at the end
 * (R28 and R29), because the list has a delete icon.
 */
public class I_Route_Test extends BaseCls {

	static final String EXISTING_CODE = "R1";
	static final String EXISTING_NAME = "Nedumangad Route";

	// routes created by the tests
	String code1;
	String name1;
	List<String> created = new ArrayList<String>();

	// what the last Submit showed
	String last_alert = "";
	String last_notice = "";

	private String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
	}

	// runs ONCE: fresh session, login through the form
	@BeforeClass
	public void login_first() {
		login_again();
		code1 = "ZR" + unique();
		name1 = "ZQRoute" + unique();
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

	// opens Customer Details > Route from the dashboard
	private void go_to_route() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		try {
			dash.Click_submenu_by_name("Customer Details", "Route");
		} catch (NoSuchElementException e) {
			dash.Click_home();
			dash.Click_submenu_containing("Customer Details", "Route");
		}
		WaitUtil.settle(driver, 1500);
	}

	// every test starts with a fresh Route list screen
	@BeforeMethod
	public void open_route() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		I_Route_Cls r = new I_Route_Cls(driver);
		try {
			go_to_route();
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> r.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_route();
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> r.is_list_page());
		}
		remember_clean_screen();
	}

	// presses Submit and waits for: back on the list, or a message, or a pop-up / alert.
	// The alert / pop-up text is kept in last_alert / last_notice and then closed.
	private void submit_and_wait(I_Route_Cls r) {
		last_alert = "";
		last_notice = "";
		r.Click_submit();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> {
				String a = r.Accept_alert();
				if (!a.isEmpty()) {
					last_alert = a;
					return true;
				}
				return r.is_list_page() || !r.validation_messages().isEmpty() || !r.notice().isEmpty();
			});
		} catch (Exception e) {
			// nothing appeared: the test checks the screen itself
		}
		last_notice = r.notice();
		r.Close_notice();
		WaitUtil.settle(driver, 800);
		String late = r.Accept_alert();
		if (!late.isEmpty()) {
			last_alert = last_alert + " " + late;
		}
	}

	// opens the create form, fills it and submits it
	private void create_route(I_Route_Cls r, String code, String name, String type, String hierarchy) {
		r.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> r.is_form_screen());
		r.Fill_route(code, name, type, hierarchy);
		submit_and_wait(r);
	}

	// everything the site said after the last Submit
	private String said(I_Route_Cls r) {
		return r.validation_messages() + " | notice: " + last_notice + " | alert: " + last_alert;
	}

	// ======================= LIST screen =======================

	// R01: the list screen shows Search, Create, table filter, the 6 column headers, the entries line
	// and a view, edit and delete icon in a row
	@Test
	public void R01_list_page_elements() {
		test = extent.createTest("R01 Route list elements");
		I_Route_Cls r = new I_Route_Cls(driver);

		Assert.assertTrue(r.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(r.verify_create_btn().isDisplayed(), "Create button is missing");
		Assert.assertTrue(r.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");

		List<String> headers = r.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "COMPANY NAME", "ROUTE CODE", "ROUTE NAME", "HIERARCHY TYPE", "HIERARCHY NAME",
				"ACTION" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(r.verify_entries().getText().contains("Showing"), "The 'Showing .. entries' line is missing");
		Assert.assertTrue(r.Wait_for_row(EXISTING_CODE), "The route " + EXISTING_CODE + " is not in the list");
		Assert.assertEquals(r.action_icon_count(EXISTING_CODE), 3, "The view, edit and delete icons are expected");
		test.pass("List screen elements are displayed");
	}

	// R02: the Search button opens the search form (route code, route name, hierarchy name, Search)
	@Test
	public void R02_search_form_opens() {
		test = extent.createTest("R02 Search form opens");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Click_top_search();
		Assert.assertTrue(r.verify_search_code().isDisplayed(), "Route Code box is missing");
		Assert.assertTrue(r.verify_search_name().isDisplayed(), "Route Name box is missing");
		Assert.assertTrue(r.verify_search_hierarchy().isDisplayed(), "Hierarchy Name box is missing");
		Assert.assertTrue(r.verify_search_go().isDisplayed(), "Search button of the form is missing");
		test.pass("Search form is displayed");
	}

	// R03: search by route code R1 lists R1 (codes that contain R1 may be listed too)
	@Test
	public void R03_search_by_code() {
		test = extent.createTest("R03 Search by route code");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Search_route(EXISTING_CODE, "", "");
		Assert.assertTrue(r.Wait_for_row(EXISTING_CODE), "The route " + EXISTING_CODE + " was not found");
		test.pass("Found " + EXISTING_CODE + " (" + r.result_count() + " rows)");
	}

	// R04: search by route name finds that route
	@Test
	public void R04_search_by_name() {
		test = extent.createTest("R04 Search by route name");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Search_route("", EXISTING_NAME, "");
		Assert.assertTrue(r.Wait_for_row(EXISTING_CODE), "The route named " + EXISTING_NAME + " was not found");
		test.pass("Found by name");
	}

	// R05: search by hierarchy name lists routes of that hierarchy
	@Test
	public void R05_search_by_hierarchy_name() throws InterruptedException {
		test = extent.createTest("R05 Search by hierarchy name");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Search_route("", "", I_Route_Cls.HIERARCHY_NAME);
		WaitUtil.settle(driver, 2000);
		Assert.assertTrue(r.result_count() > 0, "No route was found for the hierarchy");
		for (List<String> row : r.result_texts()) {
			Assert.assertTrue(row.get(4).toUpperCase().contains("GOODWILL"), "A route of another hierarchy: " + row);
		}
		test.pass("Routes of the hierarchy are listed");
	}

	// R06: a route code that does not exist gives an empty table
	@Test
	public void R06_search_no_match() throws InterruptedException {
		test = extent.createTest("R06 Search without match");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Search_route("NOMATCH" + unique(), "", "");
		WaitUtil.settle(driver, 2000);
		Assert.assertEquals(r.result_count(), 0, "No route should be listed");
		test.pass("Empty result for an unknown code");
	}

	// R07: the "Search:" box above the table filters the rows
	@Test
	public void R07_table_filter() throws InterruptedException {
		test = extent.createTest("R07 Table filter");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.verify_table_filter().sendKeys("CITY");
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(r.result_count() > 0, "The filter hid every row");
		for (List<String> row : r.result_texts()) {
			Assert.assertTrue(row.toString().toUpperCase().contains("CITY"), "A row that does not match: " + row);
		}
		test.pass("Table filter works");
	}

	// R08: Next shows entries 11 to 20, Previous goes back to 1 to 10
	@Test
	public void R08_paging() throws InterruptedException {
		test = extent.createTest("R08 Paging");
		I_Route_Cls r = new I_Route_Cls(driver);

		Assert.assertTrue(r.verify_entries().getText().contains("Showing 1 to 10"), r.verify_entries().getText());
		r.Click_next();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(r.verify_entries().getText().contains("Showing 11 to 20"), r.verify_entries().getText());
		r.Click_previous();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(r.verify_entries().getText().contains("Showing 1 to 10"), r.verify_entries().getText());
		test.pass("Next and Previous work");
	}

	// ======================= FORM screen: create =======================

	// R09: Create opens the form with all fields, Back and Submit
	@Test
	public void R09_create_form_elements() {
		test = extent.createTest("R09 Create form elements");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Click_create();
		Assert.assertTrue(r.verify_company().isDisplayed(), "Company Name is missing");
		Assert.assertTrue(r.verify_code().isDisplayed(), "Route Code is missing");
		Assert.assertTrue(r.verify_name().isDisplayed(), "Route Name is missing");
		Assert.assertTrue(r.verify_hierarchy_type().isDisplayed(), "Hierarchy Type is missing");
		Assert.assertTrue(r.verify_hierarchy_name().isDisplayed(), "Hierarchy Name is missing");
		Assert.assertTrue(r.verify_back().isDisplayed(), "Back is missing");
		Assert.assertTrue(r.verify_submit().isDisplayed(), "Submit is missing");
		test.pass("All fields of the create form are displayed");
	}

	// R10: Company Name is fixed (greyed out) and shows the logged in company
	@Test
	public void R10_company_is_fixed() {
		test = extent.createTest("R10 Company is read-only");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Click_create();
		Assert.assertFalse(r.verify_company().isEnabled(), "Company must not be editable");
		Assert.assertTrue(r.selected(r.verify_company()).toUpperCase().contains("GOODWILL"),
				"Company shows: " + r.selected(r.verify_company()));
		test.pass("Company is read-only: " + r.selected(r.verify_company()));
	}

	// R11: Hierarchy Type starts on its placeholder and offers PLANT
	@Test
	public void R11_hierarchy_type_options() {
		test = extent.createTest("R11 Hierarchy type options");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Click_create();
		test.info("Default: " + r.selected(r.verify_hierarchy_type()));
		Assert.assertEquals(r.selected(r.verify_hierarchy_type()), "HierarchyType", "Placeholder expected");
		List<String> options = r.options_of(r.verify_hierarchy_type());
		test.info("Options: " + options);
		Assert.assertTrue(options.contains("PLANT"), "PLANT is missing: " + options);
		test.pass("Hierarchy types: " + options);
	}

	// R12: Submit on an empty form shows the 4 messages
	@Test
	public void R12_empty_submit() {
		test = extent.createTest("R12 Empty submit");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Click_create();
		r.Click_submit();
		List<String> messages = r.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(messages.contains("Please Enter Route Code"), "Route code message missing: " + messages);
		Assert.assertTrue(messages.contains("Please Enter Route Name"), "Route name message missing: " + messages);
		Assert.assertTrue(messages.contains("Please Select Hierarchy Type"), "Hierarchy type message missing: " + messages);
		Assert.assertTrue(messages.contains("Please Enter Hierarchy Name"), "Hierarchy name message missing: " + messages);
		Assert.assertTrue(r.is_form_screen(), "The form must stay open");
		test.pass("All four messages are shown");
	}

	// R13: only the route code is missing -> only that message
	@Test
	public void R13_missing_route_code() {
		test = extent.createTest("R13 Missing route code");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Click_create();
		r.Fill_route("", "ZQ Name " + unique(), I_Route_Cls.HIERARCHY_TYPE, I_Route_Cls.HIERARCHY_NAME);
		r.Click_submit();
		List<String> messages = r.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(messages.contains("Please Enter Route Code"), "Route code message missing: " + messages);
		Assert.assertEquals(messages.size(), 1, "Only one message was expected: " + messages);
		test.pass("Only the route code message is shown");
	}

	// R14: only the route name is missing -> only that message
	@Test
	public void R14_missing_route_name() {
		test = extent.createTest("R14 Missing route name");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Click_create();
		r.Fill_route("ZR" + unique(), "", I_Route_Cls.HIERARCHY_TYPE, I_Route_Cls.HIERARCHY_NAME);
		r.Click_submit();
		List<String> messages = r.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(messages.contains("Please Enter Route Name"), "Route name message missing: " + messages);
		Assert.assertEquals(messages.size(), 1, "Only one message was expected: " + messages);
		test.pass("Only the route name message is shown");
	}

	// R15: only the hierarchy type is missing -> only that message
	@Test
	public void R15_missing_hierarchy_type() {
		test = extent.createTest("R15 Missing hierarchy type");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Click_create();
		r.Fill_route("ZR" + unique(), "ZQ Name " + unique(), "", I_Route_Cls.HIERARCHY_NAME);
		r.Click_submit();
		List<String> messages = r.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(messages.contains("Please Select Hierarchy Type"), "Hierarchy type message missing: " + messages);
		test.pass("The hierarchy type message is shown");
	}

	// R16: only the hierarchy name is missing -> only that message
	@Test
	public void R16_missing_hierarchy_name() {
		test = extent.createTest("R16 Missing hierarchy name");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Click_create();
		r.Fill_route("ZR" + unique(), "ZQ Name " + unique(), I_Route_Cls.HIERARCHY_TYPE, "");
		r.Click_submit();
		List<String> messages = r.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(messages.contains("Please Enter Hierarchy Name"), "Hierarchy name message missing: " + messages);
		test.pass("The hierarchy name message is shown");
	}

	// R17: a route code of only spaces must be refused (probe)
	@Test
	public void R17_spaces_only_code() {
		test = extent.createTest("R17 Route code with spaces only");
		I_Route_Cls r = new I_Route_Cls(driver);

		create_route(r, "   ", "ZQSpace" + unique(), I_Route_Cls.HIERARCHY_TYPE, I_Route_Cls.HIERARCHY_NAME);
		test.info("Site said: " + said(r));
		Assert.assertFalse(r.is_list_page() && r.validation_messages().isEmpty(),
				"DEFECT: a route code of spaces was saved. Delete the route with the blank code by hand");
		test.pass("Spaces only are refused");
	}

	// R18: a hierarchy name that does not exist must be refused (probe)
	@Test
	public void R18_hierarchy_name_not_in_list() {
		test = extent.createTest("R18 Unknown hierarchy name");
		I_Route_Cls r = new I_Route_Cls(driver);

		String code = "ZRX" + unique();
		create_route(r, code, "ZQUnknown" + unique(), I_Route_Cls.HIERARCHY_TYPE, "NOTAHIERARCHY" + unique());
		test.info("Site said: " + said(r));
		if (r.is_list_page()) {
			created.add(code);
		}
		Assert.assertFalse(r.is_list_page() && r.validation_messages().isEmpty() && last_notice.isEmpty()
				&& last_alert.isEmpty(), "DEFECT: a hierarchy name that does not exist was accepted");
		test.pass("Unknown hierarchy name refused: " + said(r));
	}

	// ======================= create, view, edit, delete =======================

	// R19: a valid route is created and listed with the saved values
	@Test
	public void R19_create_route() {
		test = extent.createTest("R19 Create route");
		I_Route_Cls r = new I_Route_Cls(driver);

		test.info("Creating " + code1 + " / " + name1);
		create_route(r, code1, name1, I_Route_Cls.HIERARCHY_TYPE, I_Route_Cls.HIERARCHY_NAME);
		test.info("Site said: " + said(r));
		Assert.assertTrue(r.is_list_page(), "Not back on the list. " + said(r));
		created.add(code1);

		r.Search_route(code1, "", "");
		Assert.assertTrue(r.Wait_for_row(code1), "The new route is not in the list");
		List<String> row = r.row_of(code1);
		test.info("Row: " + row);
		Assert.assertTrue(row.get(0).toUpperCase().contains("GOODWILL"), "Company: " + row.get(0));
		Assert.assertEquals(row.get(2), name1, "Route name");
		Assert.assertEquals(row.get(3), I_Route_Cls.HIERARCHY_TYPE, "Hierarchy type");
		Assert.assertEquals(row.get(4), I_Route_Cls.HIERARCHY_NAME, "Hierarchy name");
		test.pass("Route created and listed");
	}

	// R20: the same route code again must be refused
	@Test(dependsOnMethods = "R19_create_route")
	public void R20_duplicate_code() {
		test = extent.createTest("R20 Duplicate route code");
		I_Route_Cls r = new I_Route_Cls(driver);

		create_route(r, code1, "ZQDup" + unique(), I_Route_Cls.HIERARCHY_TYPE, I_Route_Cls.HIERARCHY_NAME);
		test.info("Site said: " + said(r));
		if (r.is_list_page()) {
			r.Search_route(code1, "", "");
			r.Wait_for_row(code1);
			int same = 0;
			for (String c : r.Route_codes()) {
				if (c.equalsIgnoreCase(code1)) {
					same++;
				}
			}
			Assert.assertEquals(same, 1, "DEFECT: the duplicate route code " + code1 + " was saved again");
		}
		boolean refused = !r.is_list_page() || !r.validation_messages().isEmpty() || !last_notice.isEmpty()
				|| !last_alert.isEmpty();
		Assert.assertTrue(refused, "DEFECT: the duplicate route code was accepted silently");
		test.pass("Duplicate refused: " + said(r));
	}

	// R21: the view screen of R1 is read-only: values shown, fields disabled, only Back
	@Test
	public void R21_view_screen() {
		test = extent.createTest("R21 View screen");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Search_route(EXISTING_CODE, "", "");
		Assert.assertTrue(r.Wait_for_row(EXISTING_CODE));
		r.Click_view(EXISTING_CODE);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> r.verify_code().getAttribute("value").length() > 0);

		Assert.assertEquals(r.verify_code().getAttribute("value"), EXISTING_CODE);
		Assert.assertEquals(r.verify_name().getAttribute("value"), EXISTING_NAME);
		Assert.assertEquals(r.selected(r.verify_hierarchy_type()), "PLANT");
		Assert.assertEquals(r.verify_hierarchy_name().getAttribute("value"), I_Route_Cls.HIERARCHY_NAME);
		Assert.assertFalse(r.verify_code().isEnabled(), "Route code must be read-only");
		Assert.assertFalse(r.verify_name().isEnabled(), "Route name must be read-only");
		Assert.assertFalse(r.verify_hierarchy_type().isEnabled(), "Hierarchy type must be read-only");
		Assert.assertFalse(r.verify_hierarchy_name().isEnabled(), "Hierarchy name must be read-only");
		Assert.assertFalse(r.submit_visible(), "Submit must not be shown in the view screen");
		Assert.assertTrue(r.verify_back().isDisplayed(), "Back is missing");
		test.pass("View screen is read-only");
	}

	// R22: the edit screen shows the saved values of the new route and its fields are editable
	@Test(dependsOnMethods = "R19_create_route")
	public void R22_edit_screen_values() {
		test = extent.createTest("R22 Edit screen values");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Search_route(code1, "", "");
		Assert.assertTrue(r.Wait_for_row(code1));
		r.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> r.verify_code().getAttribute("value").length() > 0);

		Assert.assertEquals(r.verify_code().getAttribute("value"), code1);
		Assert.assertEquals(r.verify_name().getAttribute("value"), name1);
		Assert.assertEquals(r.selected(r.verify_hierarchy_type()), I_Route_Cls.HIERARCHY_TYPE);
		Assert.assertEquals(r.verify_hierarchy_name().getAttribute("value"), I_Route_Cls.HIERARCHY_NAME);
		Assert.assertTrue(r.verify_name().isEnabled(), "Route name must be editable");
		Assert.assertTrue(r.submit_visible(), "Submit must be shown in the edit screen");
		test.pass("Edit screen shows the saved values");
	}

	// R23: a changed route name is saved and shown in the list
	@Test(dependsOnMethods = "R19_create_route")
	public void R23_edit_name() {
		test = extent.createTest("R23 Edit route name");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Search_route(code1, "", "");
		Assert.assertTrue(r.Wait_for_row(code1));
		r.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> r.verify_code().getAttribute("value").length() > 0);

		name1 = name1 + "E";
		r.Fill_route("", name1, "", "");
		submit_and_wait(r);
		test.info("Site said: " + said(r));
		Assert.assertTrue(r.is_list_page(), "Not back on the list. " + said(r));

		r.Search_route(code1, "", "");
		Assert.assertTrue(r.Wait_for_row(code1));
		Assert.assertEquals(r.row_of(code1).get(2), name1);
		test.pass("Name changed to " + name1);
	}

	// R24: Back in the edit screen leaves the route unchanged
	@Test(dependsOnMethods = "R19_create_route")
	public void R24_edit_back_without_saving() {
		test = extent.createTest("R24 Back in edit screen");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Search_route(code1, "", "");
		Assert.assertTrue(r.Wait_for_row(code1));
		String before = r.row_of(code1).get(2);
		r.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> r.verify_code().getAttribute("value").length() > 0);
		r.Fill_route("", "ZQNotSaved", "", "");
		r.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> r.is_list_page());

		r.Search_route(code1, "", "");
		Assert.assertTrue(r.Wait_for_row(code1));
		Assert.assertEquals(r.row_of(code1).get(2), before, "The name was changed by pressing Back");
		test.pass("Back does not save");
	}

	// R25: Back in the create screen returns to the list without saving
	@Test
	public void R25_back_button_create() {
		test = extent.createTest("R25 Back in create screen");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Click_create();
		r.Fill_route("ZRBACK", "Back test", "", "");
		r.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> r.is_list_page());
		r.Search_route("ZRBACK", "", "");
		Assert.assertFalse(r.Wait_for_row("ZRBACK"), "A route was saved by pressing Back");
		test.pass("Back returns to the list without saving");
	}

	// R26: Cancel in the delete confirmation keeps the route
	@Test(dependsOnMethods = "R19_create_route")
	public void R26_delete_cancel() throws InterruptedException {
		test = extent.createTest("R26 Delete cancelled");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Search_route(code1, "", "");
		Assert.assertTrue(r.Wait_for_row(code1));
		r.Click_delete(code1);
		String text = r.Cancel_delete();
		test.info("Confirmation text: " + text);
		Assert.assertFalse(text.isEmpty(), "No delete confirmation was shown");
		WaitUtil.settle(driver, 1500);
		r.Search_route(code1, "", "");
		Assert.assertTrue(r.Wait_for_row(code1), "The route was deleted although Cancel was pressed");
		test.pass("Route kept after Cancel");
	}

	// R27: observation - a route code with symbols (@ # $): the result is only reported, the route is cleaned up later
	@Test
	public void R27_symbols_in_code() {
		test = extent.createTest("R27 Symbols in route code");
		I_Route_Cls r = new I_Route_Cls(driver);

		String code = "ZR@#" + unique();
		create_route(r, code, "ZQSym" + unique(), I_Route_Cls.HIERARCHY_TYPE, I_Route_Cls.HIERARCHY_NAME);
		if (r.is_list_page()) {
			created.add(code);
			test.warning("The code " + code + " with symbols was ACCEPTED. Confirm that this is intended.");
		} else {
			test.info("The code with symbols was refused: " + said(r));
		}
		test.pass("Observation recorded");
	}

	// R28: delete the created route: confirmation, then it is gone from the list
	@Test(dependsOnMethods = { "R19_create_route", "R20_duplicate_code", "R22_edit_screen_values", "R23_edit_name",
			"R24_edit_back_without_saving", "R26_delete_cancel" }, alwaysRun = true)
	public void R28_delete_route() {
		test = extent.createTest("R28 Delete route");
		I_Route_Cls r = new I_Route_Cls(driver);

		r.Search_route(code1, "", "");
		Assert.assertTrue(r.Wait_for_row(code1), "The route " + code1 + " to delete is not in the list");
		r.Click_delete(code1);
		List<String> messages = r.Confirm_delete();
		test.info("Messages: " + messages);
		Assert.assertFalse(messages.isEmpty(), "No delete confirmation was shown");

		r.Search_route(code1, "", "");
		Assert.assertTrue(r.Wait_for_no_row(code1), "DEFECT: the route is still listed after Delete");
		created.remove(code1);
		test.pass("Route deleted: " + messages);
	}

	// R29: clean up - every other ZR route that a test created (probes) is deleted
	@Test(dependsOnMethods = { "R27_symbols_in_code", "R28_delete_route" }, alwaysRun = true)
	public void R29_cleanup() {
		test = extent.createTest("R29 Clean up test routes");
		I_Route_Cls r = new I_Route_Cls(driver);

		for (String code : new ArrayList<String>(created)) {
			r.Search_route(code, "", "");
			if (!r.Wait_for_row(code)) {
				test.warning(code + " not found, skipped");
				continue;
			}
			r.Click_delete(code);
			test.info(code + " delete messages: " + r.Confirm_delete());
		}
		test.pass("Clean up done for: " + created);
	}
}