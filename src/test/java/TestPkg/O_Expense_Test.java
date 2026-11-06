package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.O_Expense_Cls;
import PagePkg.A_Login_Cls;

/*
 * Expense (Field Management > Expense) - functional tests, EX01 .. EX30.
 * Existing QA rows (Daily, Four Wheeler, Ex HQ ... for apk, Suresh Kammath, YAZER P) are only read.
 * The tests make two expense types with the Add pop-up (code prefix ZQE, name prefix ZQExp). The page has no delete
 * for an expense type, so these two stay in the expense type master. The list rows of these two types are deleted
 * at the end (EX29, EX30); only rows whose expense type starts with ZQ are ever deleted.
 */
public class O_Expense_Test extends BaseCls {

	static final String ZONE = "GOODWILL POLYMERS";
	static final String DESIGNATION = "Sales Executive";

	// an employee of the designation Sales Executive that has no row for the new expense types
	static final String EMPLOYEE_CODE = "TEST";
	static final String EMPLOYEE_TEXT = "Sivakumar (TEST)";

	// the expense types created in EX17
	String exp1 = "";
	String exp2 = "";
	boolean types_created = false;
	boolean row_created = false;

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
		try {
			login.Login(admin_user, admin_pass);
		} catch (TimeoutException e) {
			// the login page did not show in time: load it again once
			driver.manage().deleteAllCookies();
			driver.get(url);
			login.Login(admin_user, admin_pass);
		}
		Assert.assertTrue(login.login_success(), "Could not log in");
	}

	// opens Field Management > Expense from the dashboard (waits and tries again while the page is still loading)
	private void go_to_expense() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_by_name("Field Management", "Expense");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Field Management", "Expense");
				}
				WaitUtil.settle(driver, 600);
				return;
			} catch (ElementClickInterceptedException e) {
				if (attempt == 4) {
					throw e;
				}
				WaitUtil.settle(driver, 2500);
			}
		}
	}

	// every test starts with a fresh Expense list screen
	@BeforeMethod
	public void open_expense() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		O_Expense_Cls e = new O_Expense_Cls(driver);
		try {
			go_to_expense();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> e.is_list_page());
		} catch (Exception ex) {
			login_again();
			go_to_expense();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> e.is_list_page());
		}
		remember_clean_screen();
	}

	// presses Submit and waits for: back on the list, or a message, or a pop-up / alert.
	// The alert / pop-up text is kept in last_alert / last_notice and then closed.
	private void submit_and_wait(O_Expense_Cls e) {
		last_alert = "";
		last_notice = "";
		e.Click_submit();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> {
				String a = e.Accept_alert();
				if (!a.isEmpty()) {
					last_alert = a;
					return true;
				}
				return e.is_list_page() || !e.validation_messages().isEmpty() || !e.notice().isEmpty();
			});
		} catch (Exception ex) {
			// nothing appeared: the test checks the screen itself
		}
		last_notice = e.notice();
		e.Close_notice();
		WaitUtil.settle(driver, 800);
		String late = e.Accept_alert();
		if (!late.isEmpty()) {
			last_alert = last_alert + " " + late;
		}
	}

	// everything the site said after the last Submit
	private String said(O_Expense_Cls e) {
		return e.validation_messages() + " | notice: " + last_notice + " | alert: " + last_alert;
	}

	// true when one of the messages contains the key (capital letters ignored)
	private boolean has(List<String> messages, String key) {
		for (String m : messages) {
			if (m.toLowerCase().contains(key.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	private void open_create(O_Expense_Cls e) {
		e.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> e.is_form_screen());
	}

	// fills an Employee Wise expense: an empty value is left out. employee = an employee code to tick
	// (the employee table stays empty while no zone is chosen, so the tick is then skipped)
	private void fill_employee_wise(O_Expense_Cls e, String type, String rate, String zone, String employee) {
		if (!type.isEmpty()) {
			e.Select_expense_type(type);
		}
		if (!rate.isEmpty()) {
			e.Type_rate(rate);
		}
		if (!zone.isEmpty()) {
			e.Select_zone(zone);
		}
		e.Select_filter_type("Employee Wise");
		if (!employee.isEmpty()) {
			e.Select_designation(DESIGNATION);
			try {
				e.Tick_table_row(employee);
			} catch (NoSuchElementException ex) {
				test.info("Employee " + employee + " is not listed" + (zone.isEmpty() ? " (no zone chosen)" : ""));
				if (!zone.isEmpty()) {
					throw ex;
				}
			}
		}
	}

	// finds a row in the list with the "Search:" box above the table
	private boolean find_row(O_Expense_Cls e, String type, String employee) {
		e.Filter_table(type);
		return e.Wait_for_row(type, employee);
	}

	// ======================= LIST screen =======================

	// EX01: the list shows Search, Create, table filter, the 7 column headers, the entries line and 3 icons in a row
	@Test
	public void EX01_list_page_elements() {
		test = extent.createTest("EX01 Expense list elements");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		Assert.assertTrue(e.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(e.verify_create_btn().isDisplayed(), "Create button is missing");
		Assert.assertTrue(e.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = e.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "EXPENSE TYPE", "DESIGNATION", "EMPLOYEE NAME", "RATE", "DAILY EXPENSE",
				"RECURRING EXPENSE", "ACTIONS" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(e.verify_entries().getText().contains("Showing 1 to 10"), e.verify_entries().getText());
		List<String> first = e.result_texts().get(0);
		Assert.assertEquals(e.action_icon_count(first.get(0), first.get(2)), 3, "The view, edit and delete icons are expected");
		test.pass("List screen elements are displayed: " + e.verify_entries().getText());
	}

	// EX02: the Search button opens the search form (its labels are written to the report)
	@Test
	public void EX02_search_form_opens() {
		test = extent.createTest("EX02 Search form opens");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		e.Click_top_search();
		List<String> labels = e.search_form_labels();
		test.info("Search form labels: " + labels);
		Assert.assertFalse(labels.isEmpty(), "The search form did not open (no label is shown)");
		test.pass("Search form is displayed: " + labels);
	}

	// EX03: the "Search:" box above the table filters the rows by expense type; unknown text gives an empty table
	@Test
	public void EX03_table_filter() {
		test = extent.createTest("EX03 Table filter");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		e.Filter_table("Four Wheeler");
		Assert.assertTrue(e.result_count() > 0, "The filter hid every row");
		for (List<String> row : e.result_texts()) {
			Assert.assertTrue(row.toString().toLowerCase().contains("four wheeler"), "A row that does not match: " + row);
		}
		e.Filter_table("NOMATCH" + unique());
		Assert.assertEquals(e.result_count(), 0, "No row should be listed for unknown text");
		test.pass("Table filter works");
	}

	// EX04: Next shows entries 11 to 20, Previous goes back to 1 to 10
	@Test
	public void EX04_paging() throws InterruptedException {
		test = extent.createTest("EX04 Paging");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		e.Click_next();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(e.verify_entries().getText().contains("Showing 11 to 20"), e.verify_entries().getText());
		e.Click_previous();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(e.verify_entries().getText().contains("Showing 1 to 10"), e.verify_entries().getText());
		test.pass("Next and Previous work");
	}

	// EX05: every row has a designation, an employee as "Name (CODE)", a numeric rate and Yes / No in both columns
	@Test
	public void EX05_rows_are_consistent() {
		test = extent.createTest("EX05 Rows are consistent");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		for (List<String> row : e.result_texts()) {
			Assert.assertFalse(row.get(0).isEmpty(), "Expense type is empty: " + row);
			Assert.assertFalse(row.get(1).isEmpty(), "Designation is empty: " + row);
			Assert.assertTrue(row.get(2).matches(".+ \\(.+\\)"), "Employee is not 'Name (CODE)': " + row);
			Assert.assertTrue(row.get(3).matches("\\d+(\\.\\d+)?"), "Rate is not a number: " + row);
			Assert.assertTrue(row.get(4).equals("Yes") || row.get(4).equals("No"), "Daily expense is not Yes / No: " + row);
			Assert.assertTrue(row.get(5).equals("Yes") || row.get(5).equals("No"), "Recurring expense is not Yes / No: " + row);
		}
		test.pass("All rows are consistent");
	}

	// ======================= FORM screen: create =======================

	// EX06: Create opens the form with all fields, both radio groups, the 5 tick boxes, Back and Submit
	@Test
	public void EX06_create_form_elements() {
		test = extent.createTest("EX06 Create form elements");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		Assert.assertTrue(e.verify_company().isDisplayed(), "Company is missing");
		Assert.assertTrue(e.verify_expense_type().isDisplayed(), "Expense Type is missing");
		Assert.assertTrue(e.verify_add_btn().isDisplayed(), "Add button is missing");
		Assert.assertEquals(e.radio_selected(O_Expense_Cls.ATTENDANCE).isEmpty(), false, "Is Attendance Expense is missing");
		Assert.assertEquals(e.radio_selected(O_Expense_Cls.DAILY).isEmpty(), false, "Is Daily Recurring is missing");
		for (String key : new String[] { "perkm", "editable", "odometer", "image", "endkm" }) {
			Assert.assertNotNull(e.checkbox(key), "Tick box is missing: " + key);
		}
		Assert.assertTrue(e.verify_rate().isDisplayed(), "Rate is missing");
		Assert.assertTrue(e.verify_zone().isDisplayed(), "Zone is missing");
		Assert.assertTrue(e.verify_filter_type().isDisplayed(), "Filter Type is missing");
		Assert.assertTrue(e.verify_back().isDisplayed(), "Back is missing");
		Assert.assertTrue(e.verify_submit().isDisplayed(), "Submit is missing");
		test.pass("All fields of the create form are displayed");
	}

	// EX07: defaults: company fixed, attendance No, daily recurring No, tick boxes unticked and enabled,
	// Filter Type "Select a Filter", no table rows
	@Test
	public void EX07_default_values() {
		test = extent.createTest("EX07 Default values");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		Assert.assertFalse(e.verify_company().isEnabled(), "Company must not be editable");
		Assert.assertTrue(e.selected(e.verify_company()).toUpperCase().contains("GOODWILL"), e.selected(e.verify_company()));
		Assert.assertEquals(e.radio_selected(O_Expense_Cls.ATTENDANCE), "No", "Is Attendance Expense default");
		Assert.assertEquals(e.radio_selected(O_Expense_Cls.DAILY), "No", "Is Daily Recurring default");
		for (String key : new String[] { "perkm", "editable", "odometer", "image", "endkm" }) {
			Assert.assertFalse(e.checkbox(key).isSelected(), "Tick box must be unticked: " + key);
			Assert.assertFalse(e.is_locked(e.checkbox(key)), "Tick box must be enabled: " + key);
		}
		Assert.assertEquals(e.selected(e.verify_filter_type()), "Select a Filter", "Filter Type default");
		Assert.assertEquals(e.table_row_count(), 0, "The table must be empty before a filter is chosen");
		test.pass("Defaults are as shown");
	}

	// EX08: Zone and Filter Type have values (written to the report)
	@Test
	public void EX08_drop_down_options() {
		test = extent.createTest("EX08 Drop down options");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		List<String> zones = e.options_of(e.verify_zone());
		List<String> filters = e.options_of(e.verify_filter_type());
		test.info("Zones: " + zones);
		test.info("Filter types: " + filters);
		Assert.assertTrue(zones.contains(ZONE), ZONE + " is missing: " + zones);
		Assert.assertTrue(filters.contains("Employee Wise") && filters.contains("Designation Wise"), "Filter types: " + filters);
		test.pass("Drop downs are filled");
	}

	// EX09: Submit on an empty form shows the 4 messages (and the form stays open)
	@Test
	public void EX09_empty_submit() {
		test = extent.createTest("EX09 Empty submit");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		e.Click_submit();
		List<String> messages = e.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(has(messages, "at least one designation"), "Designation message missing: " + messages);
		Assert.assertTrue(has(messages, "select zone"), "Zone message missing: " + messages);
		Assert.assertTrue(has(messages, "expense type"), "Expense type message missing: " + messages);
		Assert.assertTrue(has(messages, "enter rate"), "Rate message missing: " + messages);
		Assert.assertTrue(e.is_form_screen(), "The form must stay open");
		test.pass("The four messages are shown");
	}

	// EX10: Employee Wise shows the Designation drop down; choosing Sales Executive lists its employees
	// (ALL box, employee code and name)
	@Test
	public void EX10_employee_wise_lists_employees() {
		test = extent.createTest("EX10 Employee wise");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		e.Select_zone(ZONE);
		e.Select_filter_type("Employee Wise");
		Assert.assertTrue(e.designation_select_visible(), "The Designation drop down must be shown for Employee Wise");
		List<String> designations = e.options_of(e.verify_designation());
		test.info("Designations: " + designations);
		Assert.assertTrue(designations.contains(DESIGNATION), DESIGNATION + " is missing: " + designations);
		e.Select_designation(DESIGNATION);
		test.info("Table headers: " + e.table_headers() + " | " + e.table_entries_text());
		test.info("Employees: " + e.table_texts());
		Assert.assertTrue(e.table_row_count() > 0, "No employee was listed");
		Assert.assertTrue(e.table_texts().toString().contains(EMPLOYEE_CODE), EMPLOYEE_CODE + " is not listed: " + e.table_texts());
		Assert.assertTrue(e.verify_all_box().isDisplayed(), "The ALL tick box is missing");
		test.pass("Employees of " + DESIGNATION + " are listed");
	}

	// EX11: Designation Wise lists the designations (Manager, Sales Executive, Driver) and hides the Designation
	// drop down
	@Test
	public void EX11_designation_wise_lists_designations() {
		test = extent.createTest("EX11 Designation wise");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		e.Select_zone(ZONE);
		e.Select_filter_type("Designation Wise");
		test.info("Designations listed: " + e.table_texts());
		Assert.assertFalse(e.designation_select_visible(), "The Designation drop down must be hidden for Designation Wise");
		Assert.assertTrue(e.table_texts().toString().contains("Manager"), "Manager is missing: " + e.table_texts());
		Assert.assertTrue(e.table_texts().toString().contains(DESIGNATION), DESIGNATION + " is missing: " + e.table_texts());
		test.pass("Designations are listed");
	}

	// EX12: the radio buttons lock each other: Attendance Yes locks Daily Recurring; Daily Recurring Yes locks
	// Attendance and the 5 tick boxes; setting them back to No unlocks everything
	@Test
	public void EX12_radio_rules() {
		test = extent.createTest("EX12 Radio rules");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		e.Set_radio(O_Expense_Cls.ATTENDANCE, "Yes");
		test.info("Attendance Yes: daily recurring '" + e.radio_selected(O_Expense_Cls.DAILY) + "', locked "
				+ e.radio_locked(O_Expense_Cls.DAILY));
		Assert.assertTrue(e.radio_locked(O_Expense_Cls.DAILY), "Is Daily Recurring must be locked when Attendance is Yes");
		Assert.assertEquals(e.radio_selected(O_Expense_Cls.DAILY), "No", "Is Daily Recurring must show No");
		Assert.assertFalse(e.is_locked(e.checkbox("perkm")), "The tick boxes must stay enabled when Attendance is Yes");

		e.Set_radio(O_Expense_Cls.ATTENDANCE, "No");
		Assert.assertFalse(e.radio_locked(O_Expense_Cls.DAILY), "Is Daily Recurring must unlock when Attendance is No");

		e.Set_radio(O_Expense_Cls.DAILY, "Yes");
		test.info("Daily Recurring Yes: attendance '" + e.radio_selected(O_Expense_Cls.ATTENDANCE) + "', locked "
				+ e.radio_locked(O_Expense_Cls.ATTENDANCE));
		Assert.assertTrue(e.radio_locked(O_Expense_Cls.ATTENDANCE), "Is Attendance Expense must be locked when Daily Recurring is Yes");
		Assert.assertEquals(e.radio_selected(O_Expense_Cls.ATTENDANCE), "No", "Is Attendance Expense must show No");
		for (String key : new String[] { "perkm", "editable", "odometer", "image", "endkm" }) {
			Assert.assertTrue(e.is_locked(e.checkbox(key)), "The tick box must be locked when Daily Recurring is Yes: " + key);
		}

		e.Set_radio(O_Expense_Cls.DAILY, "No");
		Assert.assertFalse(e.radio_locked(O_Expense_Cls.ATTENDANCE), "Is Attendance Expense must unlock again");
		Assert.assertFalse(e.is_locked(e.checkbox("perkm")), "The tick boxes must unlock again");
		test.pass("The radio rules work");
	}

	// EX13: the ALL tick box ticks every employee, unticking it clears them; one employee can be ticked alone
	@Test
	public void EX13_all_box_and_single_tick() {
		test = extent.createTest("EX13 ALL tick box");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		e.Select_zone(ZONE);
		e.Select_filter_type("Employee Wise");
		e.Select_designation(DESIGNATION);
		e.Tick_all();
		Assert.assertEquals(e.ticked_rows(), e.table_row_count(), "Every employee must be ticked");
		e.Untick_all();
		Assert.assertEquals(e.ticked_rows(), 0, "No employee must stay ticked");
		e.Tick_table_row(EMPLOYEE_CODE);
		Assert.assertEquals(e.ticked_rows(), 1, "Only the chosen employee must be ticked");
		test.pass("ALL tick box works");
	}

	// EX14: each mandatory field on its own: the form is complete except that field, Submit must show its message
	@Test
	public void EX14_each_mandatory_field() throws InterruptedException {
		test = extent.createTest("EX14 Each mandatory field");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		String[][] fields = { { "type", "expense type" }, { "rate", "enter rate" }, { "zone", "select zone" },
				{ "employee", "select at least one" } };
		List<String> failed = new ArrayList<String>();
		for (String[] f : fields) {
			if (!e.is_list_page()) {
				go_to_expense();
				new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> e.is_list_page());
			}
			open_create(e);
			fill_employee_wise(e, f[0].equals("type") ? "" : "Daily", f[0].equals("rate") ? "" : "10",
					f[0].equals("zone") ? "" : ZONE, f[0].equals("employee") ? "" : EMPLOYEE_CODE);
			e.Click_submit();
			List<String> messages = e.validation_messages();
			test.info("Without " + f[0] + ": " + messages);
			if (!has(messages, f[1]) || !e.is_form_screen()) {
				failed.add(f[0]);
			}
			go_to_expense();
		}
		Assert.assertTrue(failed.isEmpty(), "No message (or the form was saved) when these are missing: " + failed);
		test.pass("Every mandatory field is checked");
	}

	// ======================= Add Expense Type pop-up =======================

	// EX15: the Add button opens the pop-up (expense code, expense type, expense category with Customer / Employee /
	// Route); an empty Submit keeps it open (the message is written to the report); the x closes it
	@Test
	public void EX15_add_pop_up() {
		test = extent.createTest("EX15 Add Expense Type pop-up");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		e.Open_add_pop_up();
		Assert.assertTrue(e.verify_new_code().isDisplayed(), "Expense Code is missing");
		Assert.assertTrue(e.verify_new_type().isDisplayed(), "Expense Type is missing");
		List<String> categories = e.options_of(e.verify_new_category());
		test.info("Categories: " + categories);
		Assert.assertTrue(categories.contains("Customer") && categories.contains("Employee") && categories.contains("Route"),
				"Categories: " + categories);
		e.Click_pop_up_submit();
		test.info("Empty pop-up submit shows: " + e.validation_messages() + " | notice: " + e.notice());
		Assert.assertTrue(e.add_pop_up_open(), "The pop-up must stay open when it is submitted empty");
		e.Close_add_pop_up();
		Assert.assertFalse(e.add_pop_up_open(), "The pop-up must close");
		test.pass("Add Expense Type pop-up works");
	}

	// EX16: an expense type with a code but no name (and one with a name but no category) must be refused
	@Test
	public void EX16_add_pop_up_needs_all_fields() {
		test = extent.createTest("EX16 Add pop-up needs every field");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		e.Open_add_pop_up();
		e.Add_expense_type("ZQEX" + unique(), "", "Employee");
		test.info("Without a name: pop-up open " + e.add_pop_up_open() + " | " + e.validation_messages() + " | " + e.notice());
		Assert.assertTrue(e.add_pop_up_open(), "DEFECT: an expense type without a name was accepted");
		e.Close_add_pop_up();

		open_create_again(e);
		e.Open_add_pop_up();
		e.Add_expense_type("ZQEY" + unique(), "ZQNoCat" + unique(), "");
		test.info("Without a category: pop-up open " + e.add_pop_up_open() + " | " + e.validation_messages() + " | "
				+ e.notice());
		Assert.assertTrue(e.add_pop_up_open(), "DEFECT: an expense type without a category was accepted");
		test.pass("The pop-up needs a name and a category");
	}

	private void open_create_again(O_Expense_Cls e) {
		if (e.add_pop_up_open()) {
			e.Close_add_pop_up();
		}
		if (!e.is_form_screen()) {
			open_create(e);
		}
	}

	// EX17: two new expense types are made with the pop-up (category Employee) and are offered in the Expense Type box
	@Test
	public void EX17_add_expense_types() {
		test = extent.createTest("EX17 Add expense types");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		String name1 = "ZQExp" + unique();
		open_create(e);
		e.Open_add_pop_up();
		e.Add_expense_type("ZQE" + unique(), name1, "Employee");
		test.info("Site said: " + e.validation_messages() + " | notice: " + e.notice() + " | alert: " + e.Accept_alert());
		e.Close_notice();
		if (e.add_pop_up_open()) {
			e.Close_add_pop_up();
		}
		List<String> offered1 = e.expense_type_suggestions(name1);
		test.info("Expense Type box offers for " + name1 + ": " + offered1);
		Assert.assertTrue(offered1.toString().contains(name1), "The new expense type is not offered: " + offered1);
		exp1 = name1;

		String name2 = "ZQExp" + unique() + "B";
		e.Open_add_pop_up();
		e.Add_expense_type("ZQE" + unique() + "B", name2, "Employee");
		e.Close_notice();
		if (e.add_pop_up_open()) {
			e.Close_add_pop_up();
		}
		List<String> offered2 = e.expense_type_suggestions(name2);
		Assert.assertTrue(offered2.toString().contains(name2), "The second expense type is not offered: " + offered2);
		exp2 = name2;
		types_created = true;
		test.pass("Expense types " + exp1 + " and " + exp2 + " added");
	}

	// ======================= create, view, edit, delete =======================

	// EX18: an Employee Wise expense for Sivakumar (TEST) is created and listed with the type, designation, employee,
	// rate 100 and Daily No / Recurring No
	@Test(dependsOnMethods = "EX17_add_expense_types")
	public void EX18_create_employee_wise() {
		test = extent.createTest("EX18 Create employee wise expense");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		fill_employee_wise(e, exp1, "100", ZONE, EMPLOYEE_CODE);
		submit_and_wait(e);
		test.info("Site said: " + said(e));
		Assert.assertTrue(e.is_list_page(), "Not back on the list. " + said(e));
		row_created = true;

		Assert.assertTrue(find_row(e, exp1, EMPLOYEE_CODE), "The new expense is not in the list");
		List<String> row = e.row_of(exp1, EMPLOYEE_CODE);
		test.info("Row: " + row);
		Assert.assertEquals(row.get(1), DESIGNATION, "Designation");
		Assert.assertEquals(row.get(2), EMPLOYEE_TEXT, "Employee");
		Assert.assertEquals(row.get(3), "100", "Rate");
		Assert.assertEquals(row.get(4), "No", "Daily expense");
		Assert.assertEquals(row.get(5), "No", "Recurring expense");
		test.pass("Expense created and listed");
	}

	// EX19: the same expense type for the same employee again must be refused
	@Test(dependsOnMethods = "EX18_create_employee_wise")
	public void EX19_duplicate_expense() {
		test = extent.createTest("EX19 Duplicate expense");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		fill_employee_wise(e, exp1, "100", ZONE, EMPLOYEE_CODE);
		submit_and_wait(e);
		test.info("Site said: " + said(e));
		if (e.is_list_page()) {
			find_row(e, exp1, EMPLOYEE_CODE);
			int same = 0;
			for (List<String> row : e.result_texts()) {
				if (row.get(0).equalsIgnoreCase(exp1) && row.get(2).contains(EMPLOYEE_CODE)) {
					same++;
				}
			}
			Assert.assertEquals(same, 1, "DEFECT: the same expense was saved twice for " + EMPLOYEE_TEXT);
		}
		boolean refused = !e.is_list_page() || !last_notice.isEmpty() || !last_alert.isEmpty();
		Assert.assertTrue(refused, "DEFECT: the duplicate expense was accepted silently");
		test.pass("Duplicate refused: " + said(e));
	}

	// EX20: invalid rates must be refused (letters, negative, two decimal points); each probe uses another employee.
	// If the site accepts one, the row is saved (a defect) and deleted at the end.
	@Test(dependsOnMethods = "EX17_add_expense_types")
	public void EX20_invalid_rates_refused() throws InterruptedException {
		test = extent.createTest("EX20 Invalid rates");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		String[][] probes = { { "letters in the rate", "abc", "EM6670128" }, { "negative rate", "-50", "EM6669562" },
				{ "rate with two decimal points", "12.34.56", "EM6669972" } };
		List<String> accepted = new ArrayList<String>();
		for (String[] p : probes) {
			if (!e.is_list_page()) {
				go_to_expense();
				new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> e.is_list_page());
			}
			open_create(e);
			fill_employee_wise(e, exp1, p[1], ZONE, p[2]);
			submit_and_wait(e);
			boolean saved = e.is_list_page();
			test.info(p[0] + ": " + (saved ? "ACCEPTED" : "refused") + " | " + said(e));
			if (saved) {
				accepted.add(p[0]);
			}
			go_to_expense();
		}
		Assert.assertTrue(accepted.isEmpty(), "DEFECT: the site accepted: " + accepted);
		test.pass("All invalid rates are refused");
	}

	// EX21: a Designation Wise expense for Manager is created and listed for the managers (Suresh Kammath)
	@Test(dependsOnMethods = "EX17_add_expense_types")
	public void EX21_create_designation_wise() {
		test = extent.createTest("EX21 Create designation wise expense");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		e.Select_expense_type(exp2);
		e.Type_rate("50");
		e.Select_zone(ZONE);
		e.Select_filter_type("Designation Wise");
		e.Tick_table_row("Manager");
		submit_and_wait(e);
		test.info("Site said: " + said(e));
		Assert.assertTrue(e.is_list_page(), "Not back on the list. " + said(e));

		e.Filter_table(exp2);
		Assert.assertTrue(e.Wait_for_row(exp2, ""), "The new expense is not in the list");
		for (List<String> row : e.result_texts()) {
			test.info("Row: " + row);
			Assert.assertEquals(row.get(1), "Manager", "Designation of " + row);
			Assert.assertEquals(row.get(3), "50", "Rate of " + row);
		}
		test.pass("Designation wise expense created for the managers");
	}

	// EX22: Is Daily Recurring = Yes: the expense is saved with that flag (the list shows Yes in the Daily or the
	// Recurring column; the row is written to the report)
	@Test(dependsOnMethods = "EX21_create_designation_wise")
	public void EX22_create_daily_recurring() {
		test = extent.createTest("EX22 Create daily recurring expense");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		fill_employee_wise(e, exp2, "75", ZONE, EMPLOYEE_CODE);
		// choosing an expense type may reset the radios, so the radio is set after everything else
		e.Set_radio(O_Expense_Cls.DAILY, "Yes");
		test.info("Is Daily Recurring before Submit: " + e.radio_selected(O_Expense_Cls.DAILY));
		submit_and_wait(e);
		test.info("Site said: " + said(e));
		Assert.assertTrue(e.is_list_page(), "Not back on the list. " + said(e));

		Assert.assertTrue(find_row(e, exp2, EMPLOYEE_CODE), "The new expense is not in the list");
		List<String> row = e.row_of(exp2, EMPLOYEE_CODE);
		test.info("Row: " + row + " (daily column '" + row.get(4) + "', recurring column '" + row.get(5) + "')");
		Assert.assertEquals(row.get(3), "75", "Rate");
		Assert.assertTrue(row.get(4).equals("Yes") || row.get(5).equals("Yes"),
				"DEFECT: Is Daily Recurring = Yes was not saved: " + row);
		test.pass("Daily recurring expense saved");
	}

	// EX23: the view screen of an existing row ("Daily" of apk) is read-only: rate and zone locked, no Submit, Back
	@Test
	public void EX23_view_screen() {
		test = extent.createTest("EX23 View screen");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		Assert.assertTrue(find_row(e, "Daily", "apk"), "The row Daily / apk was not found");
		e.Click_view("Daily", "apk");
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> e.is_form_screen());
		test.info("Rate shown: '" + e.verify_rate().getAttribute("value") + "', expense type shown: '"
				+ e.verify_expense_type().getAttribute("value") + "'");
		Assert.assertEquals(e.verify_rate().getAttribute("value"), "50", "Rate");
		Assert.assertTrue(e.is_locked(e.verify_rate()), "Rate must be read-only");
		Assert.assertTrue(e.is_locked(e.verify_zone()), "Zone must be read-only");
		Assert.assertFalse(e.submit_visible(), "Submit must not be shown in the view screen");
		Assert.assertTrue(e.verify_back().isDisplayed(), "Back is missing");
		test.pass("View screen is read-only");
	}

	// EX24: the edit screen of the new row shows the saved rate and zone; the rate can be changed
	@Test(dependsOnMethods = "EX18_create_employee_wise")
	public void EX24_edit_screen_values() {
		test = extent.createTest("EX24 Edit screen values");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		Assert.assertTrue(find_row(e, exp1, EMPLOYEE_CODE));
		e.Click_edit(exp1, EMPLOYEE_CODE);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> e.is_form_screen());
		test.info("Rate '" + e.verify_rate().getAttribute("value") + "', zone '" + e.selected(e.verify_zone())
				+ "', expense type locked: " + e.is_locked(e.verify_expense_type()));
		Assert.assertEquals(e.verify_rate().getAttribute("value"), "100", "Rate");
		Assert.assertFalse(e.is_locked(e.verify_rate()), "Rate must be editable");
		Assert.assertTrue(e.submit_visible(), "Submit must be shown in the edit screen");
		test.pass("Edit screen shows the saved values");
	}

	// EX25: a changed rate is saved and shown in the list
	@Test(dependsOnMethods = "EX18_create_employee_wise")
	public void EX25_edit_rate() {
		test = extent.createTest("EX25 Edit rate");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		Assert.assertTrue(find_row(e, exp1, EMPLOYEE_CODE));
		e.Click_edit(exp1, EMPLOYEE_CODE);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> e.is_form_screen());
		e.Type_rate("150");
		submit_and_wait(e);
		test.info("Site said: " + said(e));
		Assert.assertTrue(e.is_list_page(), "Not back on the list. " + said(e));

		Assert.assertTrue(find_row(e, exp1, EMPLOYEE_CODE));
		Assert.assertEquals(e.row_of(exp1, EMPLOYEE_CODE).get(3), "150", "Rate after the edit");
		test.pass("Rate changed to 150");
	}

	// EX26: Back in the edit screen leaves the row unchanged
	@Test(dependsOnMethods = "EX18_create_employee_wise")
	public void EX26_edit_back_without_saving() {
		test = extent.createTest("EX26 Back in edit screen");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		Assert.assertTrue(find_row(e, exp1, EMPLOYEE_CODE));
		String before = e.row_of(exp1, EMPLOYEE_CODE).get(3);
		e.Click_edit(exp1, EMPLOYEE_CODE);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> e.is_form_screen());
		e.Type_rate("999");
		e.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> e.is_list_page());

		Assert.assertTrue(find_row(e, exp1, EMPLOYEE_CODE));
		Assert.assertEquals(e.row_of(exp1, EMPLOYEE_CODE).get(3), before, "The rate was changed by pressing Back");
		test.pass("Back does not save");
	}

	// EX27: Back in the create screen returns to the list without saving
	@Test
	public void EX27_back_button_create() {
		test = extent.createTest("EX27 Back in create screen");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		open_create(e);
		e.Type_rate("1");
		e.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> e.is_list_page());
		Assert.assertTrue(e.is_list_page());
		test.pass("Back returns to the list");
	}

	// EX28: Cancel in the delete confirmation keeps the row
	@Test(dependsOnMethods = "EX18_create_employee_wise")
	public void EX28_delete_cancel() {
		test = extent.createTest("EX28 Delete cancelled");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		Assert.assertTrue(find_row(e, exp1, EMPLOYEE_CODE));
		e.Click_delete(exp1, EMPLOYEE_CODE);
		String text = e.Cancel_delete();
		test.info("Confirmation text: " + text);
		Assert.assertFalse(text.isEmpty(), "No delete confirmation was shown");
		Assert.assertTrue(find_row(e, exp1, EMPLOYEE_CODE), "The row was deleted although Cancel was pressed");
		test.pass("Row kept after Cancel");
	}

	// EX29: delete the new row (exp1 / Sivakumar): confirmation, then it is gone. Only a ZQ expense type is deleted.
	@Test(dependsOnMethods = { "EX18_create_employee_wise", "EX19_duplicate_expense", "EX24_edit_screen_values",
			"EX25_edit_rate", "EX26_edit_back_without_saving", "EX28_delete_cancel" }, alwaysRun = true)
	public void EX29_delete_row() {
		test = extent.createTest("EX29 Delete row");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		if (!row_created) {
			throw new SkipException("No row was created in EX18, nothing to delete");
		}
		Assert.assertTrue(exp1.startsWith("ZQ"), "Refusing to delete an expense that is not a test expense");
		Assert.assertTrue(find_row(e, exp1, EMPLOYEE_CODE), "The row to delete is not in the list");
		e.Click_delete(exp1, EMPLOYEE_CODE);
		List<String> messages = e.Confirm_delete();
		test.info("Messages: " + messages);
		Assert.assertFalse(messages.isEmpty(), "No delete confirmation was shown");

		e.Filter_table(exp1);
		Assert.assertTrue(e.Wait_for_no_row(exp1, EMPLOYEE_CODE), "DEFECT: the row is still listed after Delete");
		test.pass("Row deleted: " + messages);
	}

	// EX30: clean up - every row whose expense type starts with ZQ (the other tests and leftovers of earlier runs)
	// is deleted
	@Test(dependsOnMethods = { "EX20_invalid_rates_refused", "EX22_create_daily_recurring", "EX29_delete_row" },
			alwaysRun = true)
	public void EX30_cleanup() {
		test = extent.createTest("EX30 Clean up test expenses");
		O_Expense_Cls e = new O_Expense_Cls(driver);

		for (int round = 1; round <= 25; round++) {
			e.Filter_table("ZQ");
			List<String> mine = null;
			for (List<String> row : e.result_texts()) {
				if (row.get(0).toUpperCase().startsWith("ZQ")) {
					mine = row;
					break;
				}
			}
			if (mine == null) {
				test.info("Round " + round + ": no test expense left");
				break;
			}
			test.info("Round " + round + ": deleting " + mine);
			Assert.assertTrue(mine.get(0).toUpperCase().startsWith("ZQ"), "Refusing to delete: " + mine);
			e.Click_delete(mine.get(0), mine.get(2));
			test.info("Delete showed: " + e.Confirm_delete());
		}
		e.Filter_table("ZQ");
		int left = 0;
		for (List<String> row : e.result_texts()) {
			if (row.get(0).toUpperCase().startsWith("ZQ")) {
				left++;
			}
		}
		Assert.assertEquals(left, 0, "Test expenses are still in the list");
		test.pass("No test expense is left in the list");
	}
}