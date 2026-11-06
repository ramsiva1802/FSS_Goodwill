package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;
import PagePkg.M_LoginStatus_Cls;

/*
 * Login Status (Device > Login Status) - functional tests, L01 .. L14.
 * A read-only report: nothing is changed by these tests.
 * Status dot: green = logged in (no logout time), red = logged out (has a logout time).
 */
public class M_LoginStatus_Test extends BaseCls {

	// an employee that has a login record in the QA data
	static final String EMPLOYEE = "Sivakumar";

	static final String HIERARCHY = "GOODWILL POLYMERS FSS 2026-2027";
	static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

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

	// opens Device > Login Status from the dashboard (waits and tries again while the page is still loading)
	private void go_to_login_status() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_by_name("Device", "Login Status");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Device", "Login Status");
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

	// every test starts with a fresh Login Status screen
	@BeforeMethod
	public void open_login_status() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);
		try {
			go_to_login_status();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> l.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_login_status();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> l.is_list_page());
		}
		remember_clean_screen();
	}

	// the total of "Showing 1 to 5 of 5 entries" (-1 when it cannot be read)
	private int total_entries(M_LoginStatus_Cls l) {
		String t = l.verify_entries().getText().replace(",", "");
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("of (\\d+) entries").matcher(t);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	// L01: the screen shows Search, the table filter, the 8 column headers, the entries line and rows
	@Test
	public void L01_list_page_elements() {
		test = extent.createTest("L01 Login Status list elements");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		Assert.assertTrue(l.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(l.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = l.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "EMPLOYEE", "HIERARCHY NAME", "APK VERSION", "MAC ADDRESS", "DEVICE MODEL",
				"LOGIN TIME", "LOGOUT TIME", "STATUS" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(l.verify_entries().getText().contains("Showing 1 to"), l.verify_entries().getText());
		Assert.assertTrue(l.result_count() > 0, "No login record is listed");
		test.pass("List screen elements are displayed: " + l.verify_entries().getText());
	}

	// L02: the Search button opens the search form (Employee Type, Employee Name, Search); default is "Employee Type"
	@Test
	public void L02_search_form_opens() {
		test = extent.createTest("L02 Search form opens");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		l.Open_search_form();
		Assert.assertTrue(l.verify_type().isDisplayed(), "Employee Type is missing");
		Assert.assertTrue(l.verify_employee().isDisplayed(), "Employee Name is missing");
		Assert.assertTrue(l.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertEquals(l.selected(l.verify_type()), "Employee Type", "Default of Employee Type");
		test.pass("Search form is displayed");
	}

	// L03: Employee Type has values (written to the report)
	@Test
	public void L03_employee_type_options() {
		test = extent.createTest("L03 Employee type options");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		l.Open_search_form();
		List<String> types = l.options_of(l.verify_type());
		test.info("Employee types: " + types);
		Assert.assertFalse(types.isEmpty(), "Employee Type has no values");
		test.pass("Employee types: " + types);
	}

	// L04: the Employee Name box offers matching employees while typing
	@Test
	public void L04_employee_name_suggestions() {
		test = extent.createTest("L04 Employee name suggestions");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		l.Open_search_form();
		List<String> offered = l.employee_suggestions(EMPLOYEE.substring(0, 4));
		test.info("Typing '" + EMPLOYEE.substring(0, 4) + "' offers: " + offered);
		boolean found = false;
		for (String s : offered) {
			if (s.toLowerCase().contains(EMPLOYEE.toLowerCase())) {
				found = true;
			}
		}
		Assert.assertTrue(found, EMPLOYEE + " is not offered: " + offered);
		test.pass("Suggestions work");
	}

	// L05: search by employee name lists only that employee
	@Test
	public void L05_search_by_employee() {
		test = extent.createTest("L05 Search by employee");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		l.Search_status("", EMPLOYEE);
		Assert.assertTrue(l.result_count() > 0, "No login record was found for " + EMPLOYEE);
		for (List<String> row : l.result_texts()) {
			Assert.assertTrue(row.get(0).toLowerCase().contains(EMPLOYEE.toLowerCase()), "A record of another employee: " + row);
		}
		test.pass(l.result_count() + " record(s) of " + EMPLOYEE);
	}

	// L06: search by every employee type: no error, never more rows than the unfiltered list (counts in the report)
	@Test
	public void L06_search_by_employee_type() {
		test = extent.createTest("L06 Search by employee type");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		int all = total_entries(l);
		l.Open_search_form();
		List<String> types = l.options_of(l.verify_type());
		for (String type : types) {
			l.Search_status(type, "");
			test.info("Type '" + type + "': " + l.result_count() + " rows (all: " + all + ")");
			Assert.assertTrue(l.result_count() <= all, "More rows than the unfiltered list for " + type);
		}
		test.pass("Employee type search works for " + types);
	}

	// L07: an employee that does not exist gives an empty table
	@Test
	public void L07_search_no_match() {
		test = extent.createTest("L07 Search without match");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		l.Search_status("", "NOMATCH" + unique());
		Assert.assertEquals(l.result_count(), 0, "No record should be listed");
		test.pass("Empty result for an unknown employee");
	}

	// L08: the "Search:" box above the table filters the rows; unknown text gives an empty table
	@Test
	public void L08_table_filter() {
		test = extent.createTest("L08 Table filter");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		l.Filter_table(EMPLOYEE);
		Assert.assertTrue(l.result_count() > 0, "The filter hid every row");
		for (List<String> row : l.result_texts()) {
			Assert.assertTrue(row.toString().toLowerCase().contains(EMPLOYEE.toLowerCase()), "A row that does not match: " + row);
		}
		l.Filter_table("NOMATCH" + unique());
		Assert.assertEquals(l.result_count(), 0, "No row should be listed for unknown text");
		test.pass("Table filter works");
	}

	// L09: with up to 10 records there is one page: "Showing 1 to N of N", Next and Previous are disabled
	@Test
	public void L09_single_page() {
		test = extent.createTest("L09 Paging");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		int total = total_entries(l);
		test.info(l.verify_entries().getText());
		Assert.assertTrue(total >= 0, "The entries line could not be read: " + l.verify_entries().getText());
		if (total <= 10) {
			Assert.assertEquals(l.result_count(), total, "All records must be on one page");
			Assert.assertFalse(l.next_enabled(), "Next must be disabled when there is one page");
			Assert.assertFalse(l.previous_enabled(), "Previous must be disabled when there is one page");
		} else {
			Assert.assertTrue(l.next_enabled(), "Next must be enabled when there are more than 10 records");
		}
		test.pass("Paging matches the number of records");
	}

	// L10: the status dot matches the logout time: green = no logout time, red = a logout time
	@Test
	public void L10_status_dot_matches_logout_time() {
		test = extent.createTest("L10 Status dot");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		List<String> wrong = new ArrayList<String>();
		List<List<String>> rows = l.result_texts();
		for (int i = 0; i < rows.size(); i++) {
			String color = l.status_color(i);
			String logout = rows.get(i).get(6);
			test.info(rows.get(i).get(0) + ": dot " + color + ", logout time '" + logout + "'");
			if (color.equals("unknown")) {
				test.info("Status cell HTML: " + l.status_html(i));
				wrong.add(rows.get(i).get(0) + ": the dot colour could not be read");
			} else if (color.equals("green") && !logout.isEmpty()) {
				wrong.add(rows.get(i).get(0) + ": green dot but a logout time is shown");
			} else if (color.equals("red") && logout.isEmpty()) {
				wrong.add(rows.get(i).get(0) + ": red dot but no logout time");
			}
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: status dot and logout time do not match: " + wrong);
		test.pass("Status dots match the logout times");
	}

	// L11: login and logout times are dd-MM-yyyy HH:mm:ss and the logout is not before the login
	@Test
	public void L11_times_are_valid() {
		test = extent.createTest("L11 Login and logout times");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		for (List<String> row : l.result_texts()) {
			LocalDateTime login = LocalDateTime.parse(row.get(5), TIME);
			if (!row.get(6).isEmpty()) {
				LocalDateTime logout = LocalDateTime.parse(row.get(6), TIME);
				Assert.assertFalse(logout.isBefore(login), "DEFECT: logout before login: " + row);
			}
		}
		test.pass("All times are valid");
	}

	// L12: every record has the employee as "Name (CODE)", the hierarchy, a MAC address, a device model and a login time
	@Test
	public void L12_records_are_complete() {
		test = extent.createTest("L12 Records are complete");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		for (List<String> row : l.result_texts()) {
			Assert.assertTrue(row.get(0).matches(".+ \\(.+\\)"), "Employee is not 'Name (CODE)': " + row.get(0));
			Assert.assertEquals(row.get(1), HIERARCHY, "Hierarchy name of " + row.get(0));
			Assert.assertFalse(row.get(3).isEmpty(), "MAC address is empty: " + row);
			Assert.assertFalse(row.get(4).isEmpty(), "Device model is empty: " + row);
			Assert.assertFalse(row.get(5).isEmpty(), "Login time is empty: " + row);
		}
		test.pass("All records are complete");
	}

	// L13: every employee appears once per device: no MAC address is listed twice
	@Test
	public void L13_no_duplicate_device() {
		test = extent.createTest("L13 No duplicate device");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		List<String> seen = new ArrayList<String>();
		for (List<String> row : l.result_texts()) {
			Assert.assertFalse(seen.contains(row.get(3)), "DEFECT: the device " + row.get(3) + " is listed twice");
			seen.add(row.get(3));
		}
		test.pass("No device is listed twice");
	}

	// L14: the search form can be used again after a search (Employee Name keeps working, no error)
	@Test
	public void L14_search_twice() {
		test = extent.createTest("L14 Search twice");
		M_LoginStatus_Cls l = new M_LoginStatus_Cls(driver);

		l.Search_status("", EMPLOYEE);
		int first = l.result_count();
		l.Search_status("", "NOMATCH" + unique());
		Assert.assertEquals(l.result_count(), 0, "The second search must show no row");
		l.Search_status("", EMPLOYEE);
		Assert.assertEquals(l.result_count(), first, "The same search must give the same result again");
		test.pass("Repeated searches give the same result");
	}
}