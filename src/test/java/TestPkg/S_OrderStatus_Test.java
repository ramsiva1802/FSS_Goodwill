package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
import PagePkg.A_Login_Cls;
import PagePkg.S_OrderStatus_Cls;

/*
 * Order Status (Field Management > Order Status) - functional tests, OS01 .. OS60.
 * Orders are created by the mobile app, so the tests work on the orders that already exist.
 * Read only: list, search form, filters, sorting, paging, view, print, edit pop-up (closed WITHOUT saving),
 * delete dialog (Cancelled).
 * Two switches guard the tests that change real orders:
 *  - ALLOW_EDIT_SAVE: saves an edited remark / quantity of ONE pending order and puts the old value back.
 *  - ALLOW_APPROVE_DELETE: approves and deletes pending orders. This cannot be undone: it is false here.
 */
public class S_OrderStatus_Test extends BaseCls {

	// true = tests that save a change to a pending order (the old value is restored afterwards)
	static final boolean ALLOW_EDIT_SAVE = true;

	// true = tests that APPROVE or DELETE a pending order (irreversible, use QA data only)
	static final boolean ALLOW_APPROVE_DELETE = false;

	static final String FROM_WIDE = "01/10/2026";
	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a", Locale.ENGLISH);

	private String main_tab;

	private String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
	}

	private String today() {
		return LocalDate.now().format(DATE);
	}

	// runs ONCE: fresh session, login through the form
	@BeforeClass
	public void login_first() {
		login_again();
	}

	private void login_again() {
		try {
			driver.get(url);
		} catch (org.openqa.selenium.NoSuchSessionException | org.openqa.selenium.NoSuchWindowException e) {
			// the browser was closed: start a new one
			restartBrowser();
		}
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
		main_tab = driver.getWindowHandle();
	}

	// closes print tabs, alerts and pop-ups that a failed test may have left open
	private void clean_up() {
		try {
			driver.switchTo().alert().dismiss();
		} catch (Exception e) {
			// no alert
		}
		try {
			Set<String> handles = driver.getWindowHandles();
			if (main_tab != null && !handles.isEmpty() && !handles.contains(main_tab)) {
				main_tab = handles.iterator().next();
			}
			if (handles.size() > 1 && main_tab != null) {
				for (String h : handles) {
					if (!h.equals(main_tab)) {
						driver.switchTo().window(h);
						driver.close();
					}
				}
				driver.switchTo().window(main_tab);
			}
		} catch (Exception e) {
			System.out.println("Could not close the extra tabs: " + e.getMessage());
		}
		try {
			new S_OrderStatus_Cls(driver).Close_modal();
		} catch (Exception e) {
			// no pop-up
		}
	}

	// opens Field Management > Order Status from the dashboard
	private void go_to_order_status() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_by_name("Field Management", "Order Status");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Field Management", "Order Status");
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

	// every test starts with a fresh Order Status screen
	@BeforeMethod
	public void open_order_status() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		clean_up();
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);
		try {
			go_to_order_status();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_order_status();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		}
		remember_clean_screen();
	}

	// ---------------------------------- helpers ----------------------------------

	// the orders from FROM_WIDE until today, with an optional status
	private void search_wide(S_OrderStatus_Cls o, String status) {
		o.Search_orders("", "", FROM_WIDE, today(), "", "", "", status);
	}

	private String day_of(List<String> row) {
		return row.get(1).length() >= 10 ? row.get(1).substring(0, 10) : row.get(1);
	}

	private LocalDate date_of(String text) {
		return LocalDate.parse(text.trim().substring(0, 10), DATE);
	}

	private double amount_of(List<String> row) {
		return S_OrderStatus_Cls.to_number(row.get(4));
	}

	private boolean is_pending(List<String> row) {
		return row.get(6).trim().equalsIgnoreCase("Pending");
	}

	// the first order (of the first page of the wide search) whose status is 'status' (the status drop down is
	// used when the first page has none); skips the test when there is none
	private List<String> find_order(S_OrderStatus_Cls o, String status) {
		search_wide(o, "");
		for (List<String> row : o.result_texts()) {
			if (row.get(6).trim().equalsIgnoreCase(status)) {
				return row;
			}
		}
		try {
			search_wide(o, status);
			for (List<String> row : o.result_texts()) {
				if (row.get(6).trim().equalsIgnoreCase(status)) {
					return row;
				}
			}
		} catch (NoSuchElementException e) {
			// the drop down has no such status
		}
		throw new SkipException("No " + status + " order is listed");
	}

	// the first order that is not pending
	private List<String> find_closed_order(S_OrderStatus_Cls o) {
		search_wide(o, "");
		for (List<String> row : o.result_texts()) {
			if (!is_pending(row)) {
				return row;
			}
		}
		throw new SkipException("No approved / invoiced order is listed");
	}

	// the first order of the wide search
	private List<String> first_order(S_OrderStatus_Cls o) {
		search_wide(o, "");
		if (o.result_count() == 0) {
			throw new SkipException("No order is listed");
		}
		return o.result_texts().get(0);
	}

	// the list shows the order again (wide search) and its row is returned
	private List<String> reload_row(S_OrderStatus_Cls o, String number) {
		search_wide(o, "");
		List<String> row = o.row_of(number);
		if (row == null) {
			search_wide(o, "Pending");
			row = o.row_of(number);
		}
		return row;
	}

	// confirms the open dialog with its first button that is not Cancel / No / Close
	private List<String> confirm_dialog(S_OrderStatus_Cls o) {
		List<String> texts = new ArrayList<String>();
		if (o.dialog_open()) {
			texts.add(o.Dialog_text());
			String chosen = "OK";
			for (String b : o.dialog_buttons()) {
				String l = b.toLowerCase();
				if (!l.contains("cancel") && !l.equals("no") && !l.contains("close")) {
					chosen = b;
					break;
				}
			}
			o.Click_dialog_button(chosen);
		}
		texts.addAll(o.Accept_messages());
		return texts;
	}

	private void open_view(S_OrderStatus_Cls o, String number) {
		o.Click_view(number);
		Assert.assertTrue(o.modal_open(), "The Order Details pop-up did not open for " + number);
	}

	private void open_edit(S_OrderStatus_Cls o, String number) {
		o.Click_edit(number);
		Assert.assertTrue(o.modal_open(), "The Order Details pop-up did not open for editing " + number);
	}

	private boolean has_cell(List<List<String>> lines, int cell, String text) {
		for (List<String> l : lines) {
			if (l.get(cell).toLowerCase().contains(text.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	// ======================= screen and search form =======================

	// OS01: the screen shows Search, the table filter, the 8 column headers and the entries line
	@Test
	public void OS01_screen_elements() {
		test = extent.createTest("OS01 Order Status screen elements");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		Assert.assertTrue(o.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(o.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = o.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "ORDER NUMBER", "ORDER DATE", "ROUTE NAME", "CUSTOMER NAME", "ORDER AMOUNT",
				"DELIVERY DATE", "ORDER STATUS", "ACTIONS" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(o.entries_text().contains("Showing"), "Entries line: " + o.entries_text());
		test.pass("Screen elements are displayed: " + o.entries_text());
	}

	// OS02: the Search button opens the search form with its 7 fields, Search and Clear
	@Test
	public void OS02_search_form_opens() {
		test = extent.createTest("OS02 Search form opens");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		o.Open_search_form();
		Assert.assertTrue(o.verify_type().isDisplayed(), "Employee Type is missing");
		Assert.assertTrue(o.verify_employee().isDisplayed(), "Employee Name is missing");
		Assert.assertTrue(o.verify_from().isDisplayed(), "From Date is missing");
		Assert.assertTrue(o.verify_to().isDisplayed(), "To Date is missing");
		Assert.assertTrue(o.verify_customer().isDisplayed(), "Customer is missing");
		Assert.assertTrue(o.verify_hierarchy().isDisplayed(), "Hierarchy Type is missing");
		Assert.assertTrue(o.verify_supply_by().isDisplayed(), "Supply By is missing");
		Assert.assertTrue(o.verify_status().isDisplayed(), "Approval Status is missing");
		Assert.assertTrue(o.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertTrue(o.verify_clear().isDisplayed(), "The Clear button is missing");
		test.info("From " + o.verify_from().getAttribute("value") + " | To " + o.verify_to().getAttribute("value")
				+ " | Hierarchy Type '" + o.selected(o.verify_hierarchy()) + "' | Approval Status '"
				+ o.selected(o.verify_status()) + "'");
		Assert.assertEquals(o.verify_from().getAttribute("value"), today(), "Default From Date");
		Assert.assertEquals(o.verify_to().getAttribute("value"), today(), "Default To Date");
		test.pass("Search form is displayed with its defaults");
	}

	// OS03: the drop down values are written to the report
	@Test
	public void OS03_drop_down_options() {
		test = extent.createTest("OS03 Drop down options");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		o.Open_search_form();
		List<String> types = o.options_of(o.verify_type());
		List<String> hierarchies = o.options_of(o.verify_hierarchy());
		List<String> statuses = o.options_of(o.verify_status());
		test.info("Employee types: " + types);
		test.info("Hierarchy types: " + hierarchies);
		test.info("Approval statuses: " + statuses);
		Assert.assertFalse(types.isEmpty(), "Employee Type has no values");
		Assert.assertFalse(hierarchies.isEmpty(), "Hierarchy Type has no values");
		Assert.assertFalse(statuses.isEmpty(), "Approval Status has no values");
		for (String s : new String[] { "Pending", "Approved" }) {
			Assert.assertTrue(statuses.contains(s), "Approval Status has no '" + s + "': " + statuses);
		}
		test.pass("Drop downs are filled");
	}

	// OS04: before any search the list shows the orders of today only
	@Test
	public void OS04_default_list_is_today() {
		test = extent.createTest("OS04 Default list");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		test.info(o.entries_text() + " | " + o.result_count() + " rows");
		if (o.result_count() == 0) {
			throw new SkipException("No order of today");
		}
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(day_of(row), today(), "An order of another day is listed: " + row);
		}
		test.pass("Only today's orders are listed");
	}

	// OS05: a wide date range lists orders only inside the range and the entries line matches
	@Test
	public void OS05_search_wide_date_range() {
		test = extent.createTest("OS05 Search a wide date range");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		search_wide(o, "");
		test.info(FROM_WIDE + " .. " + today() + ": " + o.entries_text());
		Assert.assertTrue(o.result_count() > 0, "No order is listed for " + FROM_WIDE + " .. " + today());
		LocalDate from = LocalDate.parse(FROM_WIDE, DATE);
		LocalDate to = LocalDate.now();
		for (List<String> row : o.result_texts()) {
			LocalDate d = date_of(row.get(1));
			Assert.assertTrue(!d.isBefore(from) && !d.isAfter(to), "DEFECT: an order outside the date range: " + row);
		}
		Assert.assertTrue(o.entries_total() >= o.result_count(), "The entries line is smaller than the rows shown");
		test.pass("Wide range works");
	}

	// OS06: one single day lists only the orders of that day
	@Test
	public void OS06_search_single_day() {
		test = extent.createTest("OS06 Search a single day");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		String day = day_of(first_order(o));
		o.Search_orders("", "", day, day, "", "", "", "");
		test.info("Day " + day + ": " + o.entries_text());
		Assert.assertTrue(o.result_count() > 0, "No order was found for the day " + day + " that has an order");
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(day_of(row), day, "An order of another day: " + row);
		}
		test.pass("Only the day " + day + " is listed");
	}

	// OS07: From Date after To Date: no order (or a message) must be shown
	@Test
	public void OS07_reversed_date_range() {
		test = extent.createTest("OS07 Reversed date range");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		o.Search_orders("", "", "20/10/2026", "01/10/2026", "", "", "", "");
		test.info("Rows: " + o.result_count() + " | messages: " + o.validation_messages() + " | notice: " + o.notice());
		Assert.assertTrue(o.result_count() == 0 || !o.validation_messages().isEmpty() || !o.notice().isEmpty(),
				"DEFECT: a reversed date range lists orders without any message");
		test.pass("Reversed range gives no order or a message");
	}

	// OS08: a range in the future has no order
	@Test
	public void OS08_future_dates() {
		test = extent.createTest("OS08 Future date range");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		String from = LocalDate.now().plusDays(60).format(DATE);
		String to = LocalDate.now().plusDays(90).format(DATE);
		o.Search_orders("", "", from, to, "", "", "", "");
		test.info(from + " .. " + to + ": rows " + o.result_count() + " | " + o.entries_text());
		Assert.assertEquals(o.result_count(), 0, "Orders are listed for a future range");
		Assert.assertTrue(o.no_data_shown() || o.entries_total() == 0, "The table shows no 'No Data' line");
		test.pass("A future range lists nothing");
	}

	// OS09: invalid dates are refused (letters, 31/02, empty)
	@Test
	public void OS09_invalid_dates() {
		test = extent.createTest("OS09 Invalid dates");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (String value : new String[] { "abcd", "31/02/2026", "99/99/9999" }) {
			o.Search_orders("", "", value, today(), "", "", "", "");
			o.Open_search_form();
			String kept = o.verify_from().getAttribute("value");
			test.info("From Date '" + value + "' -> box keeps '" + kept + "' | rows " + o.result_count()
					+ " | messages " + o.validation_messages() + " | " + o.entries_text());
			boolean refused = kept == null || !kept.equals(value) || !o.validation_messages().isEmpty()
					|| o.result_count() == 0 || !o.notice().isEmpty();
			if (!refused) {
				bad.add(value);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: invalid From Date values were accepted and orders listed: " + bad);
		test.pass("Invalid dates are not accepted");
	}

	// OS10: every approval status lists only orders of that status
	@Test
	public void OS10_search_by_status() {
		test = extent.createTest("OS10 Search by approval status");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		o.Open_search_form();
		List<String> statuses = o.options_of(o.verify_status());
		List<String> wrong = new ArrayList<String>();
		for (String status : statuses) {
			search_wide(o, status);
			int count = o.result_count();
			StringBuilder seen = new StringBuilder();
			for (List<String> row : o.result_texts()) {
				if (seen.indexOf(row.get(6)) < 0) {
					seen.append(row.get(6)).append(' ');
				}
				if (!row.get(6).trim().equalsIgnoreCase(status.trim())) {
					wrong.add(status + " -> " + row.get(0) + " is " + row.get(6));
				}
			}
			test.info("Status '" + status + "': " + o.entries_text() + " | statuses on page 1: " + seen);
			Assert.assertTrue(count > 0 || o.no_data_shown() || o.entries_total() == 0,
					"No rows and no 'No Data' for " + status);
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: orders of another status are listed: " + wrong);
		test.pass("Every status lists its own orders");
	}

	// OS11: the status counts add up: Pending + Approved + others = all orders
	@Test
	public void OS11_status_counts_add_up() {
		test = extent.createTest("OS11 Status counts add up");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		search_wide(o, "");
		int all = o.entries_total();
		o.Open_search_form();
		int sum = 0;
		for (String status : o.options_of(o.verify_status())) {
			search_wide(o, status);
			sum += o.entries_total();
			test.info(status + ": " + o.entries_total());
		}
		test.info("All: " + all + " | sum of the statuses: " + sum);
		Assert.assertEquals(sum, all, "DEFECT: the orders of the statuses do not add up to all orders");
		test.pass("Statuses add up to " + all);
	}

	// OS12: search by customer lists only that customer
	@Test
	public void OS12_search_by_customer() {
		test = extent.createTest("OS12 Search by customer");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		String customer = first_order(o).get(3);
		test.info("Customer used: " + customer);
		o.Search_orders("", "", FROM_WIDE, today(), customer, "", "", "");
		Assert.assertTrue(o.result_count() > 0, "No order was found for " + customer);
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(3), customer, "An order of another customer: " + row);
		}
		test.pass("Only " + customer + " is listed");
	}

	// OS13: a customer that does not exist: nothing is listed and the search is not ignored
	@Test
	public void OS13_unknown_customer() {
		test = extent.createTest("OS13 Unknown customer");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		search_wide(o, "");
		int all = o.entries_total();
		o.Search_orders("", "", FROM_WIDE, today(), "ZZQNoSuchCustomer" + unique(), "", "", "");
		test.info("All orders " + all + " | rows now " + o.result_count() + " | " + o.entries_text()
				+ " | messages " + o.validation_messages() + " " + o.notice());
		Assert.assertTrue(o.result_count() == 0 || o.entries_total() < all || !o.validation_messages().isEmpty()
				|| !o.notice().isEmpty(),
				"DEFECT: a customer that does not exist is ignored and all " + all + " orders are listed without a message");
		test.pass("An unknown customer does not list every order");
	}

	// OS14: the customer search box suggests customers
	@Test
	public void OS14_customer_suggestions() {
		test = extent.createTest("OS14 Customer suggestions");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		String customer = first_order(o).get(3);
		o.Open_search_form();
		List<String> found = o.Suggestions_for(o.verify_customer(), customer.substring(0, Math.min(3, customer.length())));
		test.info("Suggestions for '" + customer.substring(0, Math.min(3, customer.length())) + "': " + found);
		Assert.assertFalse(found.isEmpty(), "No customer is suggested");
		List<String> none = o.Suggestions_for(o.verify_customer(), "ZZQNoSuch" + unique());
		Assert.assertTrue(none.isEmpty(), "A customer is suggested for text that matches nothing: " + none);
		test.pass("Customer suggestions work");
	}

	// OS15: employee search (needs the employee type Sales Executive): the suggested employee is searched
	@Test
	public void OS15_search_by_employee() {
		test = extent.createTest("OS15 Search by employee");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		o.Open_search_form();
		List<String> types = o.options_of(o.verify_type());
		if (!types.contains("Sales Executive")) {
			throw new SkipException("Employee Type has no Sales Executive: " + types);
		}
		o.Search_orders("Sales Executive", "a", FROM_WIDE, today(), "", "", "", "");
		o.Open_search_form();
		test.info("Employee typed 'a' (first suggestion picked): picked '" + o.verify_employee().getAttribute("value")
				+ "' -> " + o.entries_text());
		Assert.assertTrue(o.entries_text().contains("Showing") || o.no_data_shown(), "The search gave no result line");
		test.pass("Employee search ran");
	}

	// OS16: an employee name that does not exist: not ignored
	@Test
	public void OS16_unknown_employee() {
		test = extent.createTest("OS16 Unknown employee");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		search_wide(o, "");
		int all = o.entries_total();
		o.Search_orders("Sales Executive", "ZZQNoSuchEmp" + unique(), FROM_WIDE, today(), "", "", "", "");
		test.info("All " + all + " | now " + o.entries_text() + " | messages " + o.validation_messages() + " " + o.notice());
		Assert.assertTrue(o.result_count() == 0 || o.entries_total() < all || !o.validation_messages().isEmpty()
				|| !o.notice().isEmpty(),
				"DEFECT: an employee name that does not exist is ignored and all " + all + " orders are listed without a message");
		test.pass("An unknown employee does not list every order");
	}

	// OS17: every hierarchy type can be searched (counts written to the report)
	@Test
	public void OS17_hierarchy_types() {
		test = extent.createTest("OS17 Hierarchy types");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		o.Open_search_form();
		List<String> hierarchies = o.options_of(o.verify_hierarchy());
		test.info("Hierarchy types: " + hierarchies);
		for (String h : hierarchies) {
			o.Search_orders("", "", FROM_WIDE, today(), "", h, "", "");
			test.info(h + ": " + o.entries_text() + " | rows " + o.result_count());
			Assert.assertTrue(o.entries_text().contains("Showing") || o.no_data_shown(),
					"No result line after searching the hierarchy type " + h);
		}
		test.pass("All hierarchy types can be searched");
	}

	// OS18: Supply By search box suggests values and the search runs
	@Test
	public void OS18_supply_by() {
		test = extent.createTest("OS18 Supply By");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		o.Open_search_form();
		List<String> found = new ArrayList<String>();
		String used = "";
		for (String letter : new String[] { "a", "e", "s", "r", "1" }) {
			found = o.Suggestions_for(o.verify_supply_by(), letter);
			test.info("Suggestions for '" + letter + "': " + found);
			if (!found.isEmpty()) {
				used = letter;
				break;
			}
		}
		if (found.isEmpty()) {
			throw new SkipException("Supply By suggests nothing for a, e, s, r or 1 (the list may be empty)");
		}
		o.Search_orders("", "", FROM_WIDE, today(), "", "", used, "");
		o.Open_search_form();
		test.info("Searched Supply By '" + o.verify_supply_by().getAttribute("value") + "': " + o.entries_text());
		Assert.assertTrue(o.entries_text().contains("Showing") || o.no_data_shown(), "The search gave no result line");
		test.pass("Supply By works");
	}

	// OS19: several filters together: status + customer + date range
	@Test
	public void OS19_combined_filters() {
		test = extent.createTest("OS19 Combined filters");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = first_order(o);
		String day = day_of(order);
		o.Search_orders("", "", day, day, order.get(3), "", "", order.get(6));
		test.info("Filters: " + day + " | " + order.get(3) + " | " + order.get(6) + " -> " + o.entries_text());
		Assert.assertTrue(o.row_of(order.get(0)) != null, "The order " + order.get(0) + " is not found with its own filters");
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(day_of(row), day, "Another day: " + row);
			Assert.assertEquals(row.get(3), order.get(3), "Another customer: " + row);
			Assert.assertEquals(row.get(6), order.get(6), "Another status: " + row);
		}
		test.pass("Combined filters work");
	}

	// OS20: Clear empties the filters
	@Test
	public void OS20_clear_button() {
		test = extent.createTest("OS20 Clear");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		String customer = first_order(o).get(3);
		o.Search_orders("", "", FROM_WIDE, today(), customer, "", "", "Pending");
		o.Click_clear();
		o.Open_search_form();
		test.info("After Clear: customer '" + o.verify_customer().getAttribute("value") + "' | status '"
				+ o.selected(o.verify_status()) + "' | from " + o.verify_from().getAttribute("value") + " | to "
				+ o.verify_to().getAttribute("value") + " | " + o.entries_text());
		Assert.assertEquals(o.verify_customer().getAttribute("value"), "", "Customer was not cleared");
		Assert.assertFalse(o.selected(o.verify_status()).equalsIgnoreCase("Pending"), "Approval Status was not cleared");
		test.pass("Clear empties the filters");
	}

	// ======================= table filter, sorting, paging =======================

	// OS21: the table filter finds by customer; text that matches nothing shows 'No data'; clearing brings the rows back
	@Test
	public void OS21_table_filter() {
		test = extent.createTest("OS21 Table filter");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		String customer = first_order(o).get(3);
		o.Filter_table(customer);
		test.info("Filter '" + customer + "': " + o.entries_text());
		Assert.assertTrue(o.result_count() > 0, "The filter found no order for " + customer);
		for (List<String> row : o.result_texts()) {
			Assert.assertTrue(row.get(3).equalsIgnoreCase(customer), "The filter lists another customer: " + row);
		}
		o.Filter_table("ZZQNothing" + unique());
		Assert.assertEquals(o.result_count(), 0, "Rows are listed for text that matches nothing");
		Assert.assertTrue(o.no_data_shown(), "No 'No Data' line is shown");
		o.Filter_table("");
		Assert.assertTrue(o.result_count() > 0, "The rows did not come back after clearing the filter");
		test.pass("Table filter works");
	}

	// OS22: the table filter also finds by order number and by status
	@Test
	public void OS22_table_filter_number_and_status() {
		test = extent.createTest("OS22 Table filter by number and status");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = first_order(o);
		o.Filter_table(order.get(0));
		Assert.assertTrue(o.row_of(order.get(0)) != null, "The order number " + order.get(0) + " was not found");
		Assert.assertEquals(o.result_count(), 1, "An order number must give one row");
		o.Filter_table(order.get(6));
		test.info("Filter '" + order.get(6) + "': " + o.entries_text());
		for (List<String> row : o.result_texts()) {
			Assert.assertTrue(row.toString().toLowerCase().contains(order.get(6).toLowerCase()), "Row without the text: " + row);
		}
		test.pass("Number and status filters work");
	}

	// OS23: special characters and script text in the table filter do no harm
	@Test
	public void OS23_table_filter_special_text() {
		test = extent.createTest("OS23 Table filter special text");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		first_order(o);
		for (String text : new String[] { "<script>alert(1)</script>", "' OR '1'='1", "%%%", "\\" }) {
			o.Filter_table(text);
			test.info("Filter '" + text + "': rows " + o.result_count() + " | " + o.entries_text());
			Assert.assertFalse(o.dialog_open(), "An alert / dialog appeared for the filter text " + text);
			Assert.assertTrue(o.is_list_page(), "The page broke for the filter text " + text);
		}
		o.Filter_table("");
		Assert.assertTrue(o.result_count() > 0, "The rows did not come back");
		test.pass("Special text is handled");
	}

	private List<Double> column_numbers(S_OrderStatus_Cls o, int cell) {
		List<Double> out = new ArrayList<Double>();
		for (List<String> row : o.result_texts()) {
			out.add(S_OrderStatus_Cls.to_number(row.get(cell)));
		}
		return out;
	}

	private boolean ascending(List<Double> v) {
		for (int i = 1; i < v.size(); i++) {
			if (v.get(i - 1) > v.get(i)) {
				return false;
			}
		}
		return true;
	}

	private boolean descending(List<Double> v) {
		for (int i = 1; i < v.size(); i++) {
			if (v.get(i - 1) < v.get(i)) {
				return false;
			}
		}
		return true;
	}

	// OS24: clicking Order Amount sorts it up, a second click sorts it down
	@Test
	public void OS24_sort_by_amount() {
		test = extent.createTest("OS24 Sort by order amount");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		first_order(o);
		o.Click_header("ORDER AMOUNT");
		List<Double> first = column_numbers(o, 4);
		o.Click_header("ORDER AMOUNT");
		List<Double> second = column_numbers(o, 4);
		test.info("1st click: " + first + " | 2nd click: " + second);
		Assert.assertTrue((ascending(first) && descending(second)) || (descending(first) && ascending(second)),
				"DEFECT: Order Amount is not sorted up and then down. 1st: " + first + " 2nd: " + second);
		test.pass("Amount sorting works");
	}

	// OS25: Order Number, Customer Name, Route Name and Order Status sort as text; Order Date sorts by date
	@Test
	public void OS25_sort_text_and_date_columns() {
		test = extent.createTest("OS25 Sort the other columns");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		first_order(o);
		String[] names = { "ORDER NUMBER", "ROUTE NAME", "CUSTOMER NAME", "ORDER STATUS" };
		int[] cells = { 0, 2, 3, 6 };
		List<String> wrong = new ArrayList<String>();
		for (int k = 0; k < names.length; k++) {
			o.Click_header(names[k]);
			List<String> up = new ArrayList<String>();
			for (List<String> row : o.result_texts()) {
				up.add(row.get(cells[k]).toLowerCase());
			}
			List<String> expected_up = new ArrayList<String>(up);
			java.util.Collections.sort(expected_up);
			List<String> expected_down = new ArrayList<String>(expected_up);
			java.util.Collections.reverse(expected_down);
			boolean ok = up.equals(expected_up) || up.equals(expected_down);
			test.info(names[k] + " 1st click: " + up + " -> " + (ok ? "sorted" : "NOT sorted"));
			if (!ok) {
				wrong.add(names[k]);
			}
		}
		o.Click_header("ORDER DATE");
		List<LocalDateTime> dates = new ArrayList<LocalDateTime>();
		for (List<String> row : o.result_texts()) {
			try {
				dates.add(LocalDateTime.parse(row.get(1).trim().toUpperCase(), DATE_TIME));
			} catch (Exception e) {
				dates.add(LocalDate.parse(day_of(row), DATE).atStartOfDay());
			}
		}
		List<LocalDateTime> up = new ArrayList<LocalDateTime>(dates);
		java.util.Collections.sort(up);
		List<LocalDateTime> down = new ArrayList<LocalDateTime>(up);
		java.util.Collections.reverse(down);
		boolean date_ok = dates.equals(up) || dates.equals(down);
		test.info("ORDER DATE 1st click: " + dates + " -> " + (date_ok ? "sorted" : "NOT sorted by date"));
		if (!date_ok) {
			wrong.add("ORDER DATE");
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: these columns are not sorted correctly: " + wrong);
		test.pass("All columns sort");
	}

	// OS26: paging: Next shows the next 10 orders, Previous comes back
	@Test
	public void OS26_paging() {
		test = extent.createTest("OS26 Paging");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		search_wide(o, "");
		if (o.entries_total() <= 10) {
			throw new SkipException("Only " + o.entries_total() + " orders: no second page");
		}
		List<List<String>> page1 = o.result_texts();
		Assert.assertEquals(page1.size(), 10, "The first page does not show 10 rows");
		Assert.assertFalse(o.previous_enabled(), "Previous is enabled on the first page");
		o.Click_next();
		test.info("Page 2: " + o.entries_text());
		Assert.assertTrue(o.entries_text().contains("Showing 11 to 20"), "Page 2 text: " + o.entries_text());
		for (List<String> row : o.result_texts()) {
			Assert.assertFalse(page1.contains(row), "An order of page 1 is shown again on page 2: " + row);
		}
		o.Click_previous();
		Assert.assertEquals(o.result_texts(), page1, "Previous does not return to the first page");
		test.pass("Paging works");
	}

	// OS27: a new search goes back to the first page
	@Test
	public void OS27_search_resets_to_page_one() {
		test = extent.createTest("OS27 Search resets paging");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		search_wide(o, "");
		if (o.entries_total() <= 10) {
			throw new SkipException("No second page");
		}
		o.Click_next();
		search_wide(o, "");
		test.info(o.entries_text());
		Assert.assertTrue(o.entries_text().contains("Showing 1 to"), "A new search does not start on page 1: " + o.entries_text());
		test.pass("A new search starts on page 1");
	}

	// ======================= data quality =======================

	// OS28: formats of the listed values: amount with 2 decimals, dates, delivery on or after the order day
	@Test
	public void OS28_value_formats() {
		test = extent.createTest("OS28 Value formats");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		first_order(o);
		List<String> bad = new ArrayList<String>();
		for (List<String> row : o.result_texts()) {
			if (!row.get(4).matches("[\\d,]+\\.\\d{2}")) {
				bad.add(row.get(0) + " amount '" + row.get(4) + "'");
			}
			if (!row.get(1).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2} [AP]M")) {
				bad.add(row.get(0) + " order date '" + row.get(1) + "'");
			}
			if (!row.get(5).matches("\\d{2}/\\d{2}/\\d{4}")) {
				bad.add(row.get(0) + " delivery date '" + row.get(5) + "'");
			} else if (date_of(row.get(5)).isBefore(date_of(row.get(1)))) {
				bad.add(row.get(0) + " delivery " + row.get(5) + " is before the order day " + day_of(row));
			}
			if (S_OrderStatus_Cls.to_number(row.get(4)) <= 0) {
				bad.add(row.get(0) + " amount is not above 0: " + row.get(4));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: wrong values in the list: " + bad);
		test.pass("Formats are correct");
	}

	// OS29: order numbers are unique and no cell is empty
	@Test
	public void OS29_order_numbers_unique() {
		test = extent.createTest("OS29 Order numbers unique");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		first_order(o);
		List<String> seen = new ArrayList<String>();
		for (List<String> row : o.result_texts()) {
			Assert.assertFalse(seen.contains(row.get(0)), "DEFECT: order number repeated: " + row.get(0));
			seen.add(row.get(0));
			for (int c = 0; c < 7; c++) {
				Assert.assertFalse(row.get(c).isEmpty(), "Empty cell " + (c + 1) + " in " + row);
			}
		}
		test.pass("Order numbers are unique: " + seen);
	}

	// ======================= row actions =======================

	// OS30: Pending orders have view, edit, delete and print; other orders have view and print only
	@Test
	public void OS30_actions_by_status() {
		test = extent.createTest("OS30 Actions by status");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		first_order(o);
		test.info("Icons of the first order: " + o.actions_html(o.result_texts().get(0).get(0)));
		List<String> bad = new ArrayList<String>();
		for (List<String> row : o.result_texts()) {
			List<String> kinds = o.actions_of(row.get(0));
			test.info(row.get(0) + " (" + row.get(6) + "): " + kinds);
			if (!kinds.contains("view")) {
				bad.add(row.get(0) + " has no view");
			}
			if (!kinds.contains("print")) {
				bad.add(row.get(0) + " has no print");
			}
			if (is_pending(row)) {
				if (!kinds.contains("edit")) {
					bad.add(row.get(0) + " is pending but has no edit");
				}
				if (!kinds.contains("delete")) {
					bad.add(row.get(0) + " is pending but has no delete");
				}
			} else if (kinds.contains("edit") || kinds.contains("delete")) {
				bad.add(row.get(0) + " is " + row.get(6) + " but can be edited / deleted: " + kinds);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT in the row actions: " + bad);
		test.pass("Actions follow the order status");
	}

	// ======================= view =======================

	// OS31: view a pending order: pop-up with number, customer, lines and remark, all read only
	@Test
	public void OS31_view_pending_order() {
		test = extent.createTest("OS31 View a pending order");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_view(o, order.get(0));
		test.info("Pop-up: " + o.modal_text());
		Assert.assertTrue(o.modal_text().contains("Order Details"), "The pop-up has no 'Order Details' title");
		Assert.assertTrue(o.modal_text().contains(order.get(0)), "The pop-up does not show the order number " + order.get(0));
		Assert.assertEquals(o.modal_customer(), order.get(3), "Customer in the pop-up");
		Assert.assertTrue(o.line_count() > 0, "The pop-up has no item line");
		Assert.assertFalse(o.modal_editable(), "DEFECT: the view pop-up has editable boxes");
		Assert.assertFalse(o.modal_has_button("Save changes"), "DEFECT: the view pop-up has Save changes");
		Assert.assertFalse(o.modal_has_button("Approve"), "DEFECT: the view pop-up has Approve");
		Assert.assertFalse(o.remark_editable(), "DEFECT: the remark can be edited in view");
		o.Close_modal();
		Assert.assertFalse(o.modal_open(), "The pop-up did not close");
		test.pass("View pop-up of " + order.get(0) + " is read only");
	}

	// OS32: the item lines add up to the order amount of the list
	@Test
	public void OS32_lines_total_equals_amount() {
		test = extent.createTest("OS32 Lines total equals order amount");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = first_order(o);
		open_view(o, order.get(0));
		double lines = o.lines_total();
		test.info("Lines " + o.line_texts() + " | total " + lines + " | list amount " + amount_of(order));
		o.Close_modal();
		if (Math.abs(lines - amount_of(order)) > 0.005) {
			test.warning("The list amount " + amount_of(order) + " differs from the lines total " + lines);
		}
		Assert.assertEquals(lines, amount_of(order), 0.5, "DEFECT: the item lines do not add up to the order amount of the list");
		test.pass("Lines add up to " + amount_of(order));
	}

	// OS33: view an approved / invoiced order: read only, delete buttons of the lines are not usable
	@Test
	public void OS33_view_closed_order() {
		test = extent.createTest("OS33 View an approved / invoiced order");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_closed_order(o);
		open_view(o, order.get(0));
		test.info(order.get(6) + " order: " + o.modal_text());
		Assert.assertTrue(o.modal_text().contains(order.get(0)), "The pop-up does not show the order number");
		Assert.assertEquals(o.modal_customer(), order.get(3), "Customer in the pop-up");
		Assert.assertTrue(o.line_count() > 0, "The pop-up has no item line");
		Assert.assertFalse(o.modal_editable(), "DEFECT: a " + order.get(6) + " order can be edited");
		Assert.assertFalse(o.modal_has_button("Save changes"), "DEFECT: Save changes on a " + order.get(6) + " order");
		Assert.assertFalse(o.modal_has_button("Approve"), "DEFECT: Approve on a " + order.get(6) + " order");
		if (Math.abs(o.lines_total() - amount_of(order)) > 0.005) {
			test.warning("The list amount " + amount_of(order) + " differs from the lines total " + o.lines_total());
		}
		Assert.assertEquals(o.lines_total(), amount_of(order), 0.5, "Lines total against the list amount");
		for (int i = 0; i < o.line_count(); i++) {
			Assert.assertFalse(o.line_delete_enabled(i), "DEFECT: the line " + (i + 1) + " of a " + order.get(6) + " order can be deleted");
		}
		o.Close_modal();
		test.pass("Closed order is read only");
	}

	// OS34: every order of the first page opens, shows its own number and customer and its lines add up
	@Test
	public void OS34_view_every_order_of_page() {
		test = extent.createTest("OS34 View every order of the page");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		first_order(o);
		List<String> bad = new ArrayList<String>();
		for (List<String> order : o.result_texts()) {
			try {
				o.Click_view(order.get(0));
				if (!o.modal_open()) {
					bad.add(order.get(0) + ": the pop-up did not open");
					continue;
				}
				if (!o.modal_text().contains(order.get(0))) {
					bad.add(order.get(0) + ": the pop-up shows another order number");
				}
				if (!o.modal_customer().equals(order.get(3))) {
					bad.add(order.get(0) + ": customer '" + o.modal_customer() + "' instead of '" + order.get(3) + "'");
				}
				if (o.line_count() == 0) {
					bad.add(order.get(0) + ": no item line");
				} else if (Math.abs(o.lines_total() - amount_of(order)) > 0.5) {
					bad.add(order.get(0) + ": lines " + o.lines_total() + " but amount " + amount_of(order));
				} else if (Math.abs(o.lines_total() - amount_of(order)) > 0.005) {
					test.warning(order.get(0) + " (" + order.get(6) + "): lines " + o.lines_total() + " but list amount "
							+ amount_of(order) + " (rounded)");
				}
			} finally {
				o.Close_modal();
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT in the order details: " + bad);
		test.pass("All orders of the page open correctly");
	}

	// OS35: closing the pop-up and opening another order shows the other order (nothing is kept)
	@Test
	public void OS35_pop_up_shows_own_order() {
		test = extent.createTest("OS35 Pop-up shows its own order");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		first_order(o);
		if (o.result_count() < 2) {
			throw new SkipException("Two orders are needed");
		}
		List<String> a = o.result_texts().get(0);
		List<String> b = o.result_texts().get(1);
		open_view(o, a.get(0));
		List<List<String>> lines_a = o.line_texts();
		o.Close_modal();
		open_view(o, b.get(0));
		test.info("Order A " + a.get(0) + " -> order B " + b.get(0) + ": " + o.modal_text());
		Assert.assertTrue(o.modal_text().contains(b.get(0)), "The pop-up of B shows another order number");
		Assert.assertFalse(o.modal_text().contains(a.get(0)), "DEFECT: the pop-up of B still shows the number of A");
		Assert.assertEquals(o.modal_customer(), b.get(3), "Customer of B");
		if (!a.get(3).equals(b.get(3))) {
			Assert.assertNotEquals(o.line_texts(), lines_a, "The lines of B are the lines of A");
		}
		o.Close_modal();
		test.pass("Each pop-up shows its own order");
	}

	// ======================= edit pop-up (closed without saving) =======================

	// OS36: edit pop-up of a pending order: editable lines, Change, +, Remark, Approve and Save changes
	@Test
	public void OS36_edit_pop_up_elements() {
		test = extent.createTest("OS36 Edit pop-up elements");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		test.info("Pop-up: " + o.modal_text());
		Assert.assertTrue(o.modal_text().contains(order.get(0)), "The pop-up does not show the order number");
		Assert.assertEquals(o.modal_customer(), order.get(3), "Customer in the pop-up");
		Assert.assertTrue(o.modal_editable(), "The edit pop-up has no editable boxes");
		Assert.assertTrue(o.modal_has_button("Change"), "Change (customer) button is missing");
		Assert.assertTrue(o.modal_has_button("Approve"), "Approve button is missing");
		Assert.assertTrue(o.modal_has_button("Save changes"), "Save changes button is missing");
		Assert.assertTrue(o.plus_available(), "The + (add item) is missing");
		Assert.assertTrue(o.remark_editable(), "The remark is not editable");
		Assert.assertTrue(o.line_count() > 0, "No item line");
		for (int i = 0; i < o.line_count(); i++) {
			Assert.assertTrue(o.line_delete_enabled(i), "The delete button of line " + (i + 1) + " is not usable");
		}
		o.Close_modal();
		test.pass("Edit pop-up is complete");
	}

	// OS37: doubling a quantity doubles the line amount; closing without saving keeps the order unchanged
	@Test
	public void OS37_quantity_recalculates_and_cancel_keeps_order() {
		test = extent.createTest("OS37 Quantity recalculates");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		List<String> line = o.line_texts().get(0);
		double net_before = o.line_net(0);
		double qty = S_OrderStatus_Cls.to_number(line.get(6));
		o.Set_line_qty(0, String.valueOf((long) (qty * 2)));
		double net_after = o.line_net(0);
		test.info("Qty " + qty + " -> " + (qty * 2) + ": net " + net_before + " -> " + net_after);
		Assert.assertEquals(net_after, net_before * 2, Math.max(0.05, net_before * 0.01), "DEFECT: the line amount did not double");
		o.Close_modal();

		List<String> row = reload_row(o, order.get(0));
		Assert.assertEquals(amount_of(row), amount_of(order), 0.001, "DEFECT: closing the pop-up without saving changed the order amount");
		open_view(o, order.get(0));
		Assert.assertEquals(o.line_texts().get(0).get(6), line.get(6), "DEFECT: the unsaved quantity was kept");
		o.Close_modal();
		test.pass("Recalculates, and nothing is saved on close");
	}

	// OS38: invalid quantities: a negative or zero quantity must not give a negative amount
	@Test
	public void OS38_invalid_quantity() {
		test = extent.createTest("OS38 Invalid quantity");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		List<String> bad = new ArrayList<String>();
		for (String value : new String[] { "-5", "0", "1.5", "abc", "99999999999" }) {
			o.Set_line_qty(0, value);
			List<String> now = o.line_texts().get(0);
			test.info("Qty '" + value + "': box keeps '" + now.get(6) + "' | net " + now.get(7) + " | messages "
					+ o.validation_messages() + " " + o.notice());
			double net = S_OrderStatus_Cls.to_number(now.get(7));
			if (net < 0) {
				bad.add("qty " + value + " gives a negative amount " + net);
			}
			if (value.equals("abc") && now.get(6).contains("abc")) {
				bad.add("letters are kept in the quantity");
			}
			if (value.equals("0") && net > 0) {
				bad.add("quantity 0 still gives an amount of " + net);
			}
		}
		o.Close_modal();
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Invalid quantities are handled");
	}

	// OS39: invalid unit rates: a negative rate must not give a negative amount
	@Test
	public void OS39_invalid_rate() {
		test = extent.createTest("OS39 Invalid unit rate");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		List<String> bad = new ArrayList<String>();
		for (String value : new String[] { "-10", "0", "12.34.56", "abc" }) {
			o.Set_line_rate(0, value);
			List<String> now = o.line_texts().get(0);
			test.info("Rate '" + value + "': box keeps '" + now.get(4) + "' | net " + now.get(7) + " | messages "
					+ o.validation_messages() + " " + o.notice());
			double net = S_OrderStatus_Cls.to_number(now.get(7));
			if (net < 0) {
				bad.add("rate " + value + " gives a negative amount " + net);
			}
			if (value.equals("abc") && now.get(4).contains("abc")) {
				bad.add("letters are kept in the unit rate");
			}
			if (value.equals("12.34.56") && now.get(4).equals("12.34.56")) {
				bad.add("the rate 12.34.56 is kept");
			}
		}
		o.Close_modal();
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Invalid rates are handled");
	}

	// OS40: the discount drop down of a line: its options are written to the report and a discount lowers the amount
	@Test
	public void OS40_line_discount() {
		test = extent.createTest("OS40 Line discount");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		int last = o.line_count() - 1;
		List<String> options = o.line_discount_options(last);
		test.info("Discount options of line " + (last + 1) + ": " + options);
		Assert.assertTrue(options.size() > 1, "The discount drop down has no discount to choose");
		o.Select_line_discount(last, "-- None --");
		double none = o.line_net(last);
		String discount = options.get(options.size() - 1);
		o.Select_line_discount(last, discount);
		double with = o.line_net(last);
		test.info("Net without discount " + none + " | with '" + discount + "' " + with);
		o.Close_modal();
		Assert.assertTrue(with < none, "DEFECT: choosing the discount '" + discount + "' did not lower the amount");
		test.pass("Discount lowers the amount");
	}

	// ======================= calculations (pop-up closed without saving) =======================
	// A line amount = unit rate x quantity x (1 - discount %) x tax factor (the tax of the item, e.g. 1.18). The tax
	// factor is worked out from the line itself, so the tests only check that the amount follows the rate,
	// the quantity and the discount.

	// the first number in brackets of a discount option: "19+6+7+3 (31.31%) (25 Roll+)" -> 31.31, "01 (20)" -> 20,
	// "-- None --" -> 0
	private double discount_percent(String label) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(\\s*([\\d.]+)\\s*%?\\s*\\)").matcher(label);
		return m.find() ? Double.parseDouble(m.group(1)) : 0;
	}

	private double tol(double expected) {
		return Math.max(0.06, Math.abs(expected) * 0.002);
	}

	// opens the edit pop-up of the first pending order whose first line has a rate and a quantity above 0
	private List<String> open_edit_for_calculation(S_OrderStatus_Cls o) {
		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		List<String> line = o.line_texts().get(0);
		if (S_OrderStatus_Cls.to_number(line.get(4)) <= 0 || S_OrderStatus_Cls.to_number(line.get(6)) <= 0) {
			o.Close_modal();
			throw new SkipException("The first line has no rate / quantity above 0");
		}
		return order;
	}

	// OS54: the line amount follows the quantity (x2, x3, x5) and comes back with the old quantity
	@Test
	public void OS54_amount_follows_quantity() {
		test = extent.createTest("OS54 Amount follows the quantity");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		open_edit_for_calculation(o);
		o.Select_line_discount(0, "-- None --");
		double q0 = S_OrderStatus_Cls.to_number(o.line_texts().get(0).get(6));
		double net0 = o.line_net(0);
		List<String> bad = new ArrayList<String>();
		for (int k : new int[] { 2, 3, 5 }) {
			o.Set_line_qty(0, String.valueOf((long) (q0 * k)));
			double expected = net0 * k;
			double net = o.line_net(0);
			test.info("Qty " + q0 + " x " + k + ": net " + net + " (expected " + expected + ")");
			if (Math.abs(net - expected) > tol(expected)) {
				bad.add("qty x" + k + " gives " + net + " instead of " + expected);
			}
		}
		o.Set_line_qty(0, String.valueOf((long) q0));
		if (Math.abs(o.line_net(0) - net0) > tol(net0)) {
			bad.add("the old quantity gives " + o.line_net(0) + " instead of " + net0);
		}
		o.Close_modal();
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the line amount does not follow the quantity: " + bad);
		test.pass("Amount follows the quantity");
	}

	// OS55: the line amount follows the unit rate (x2, x3, 10, 100) and comes back with the old rate
	@Test
	public void OS55_amount_follows_rate() {
		test = extent.createTest("OS55 Amount follows the unit rate");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		open_edit_for_calculation(o);
		o.Select_line_discount(0, "-- None --");
		String old_rate = o.line_texts().get(0).get(4);
		double r0 = S_OrderStatus_Cls.to_number(old_rate);
		double net0 = o.line_net(0);
		List<String> bad = new ArrayList<String>();
		for (double r : new double[] { r0 * 2, r0 * 3, 10, 100 }) {
			String text = String.valueOf(Math.round(r * 100) / 100.0);
			o.Set_line_rate(0, text);
			double expected = net0 * S_OrderStatus_Cls.to_number(text) / r0;
			double net = o.line_net(0);
			test.info("Rate " + old_rate + " -> " + text + ": net " + net + " (expected " + expected + ")");
			if (Math.abs(net - expected) > tol(expected)) {
				bad.add("rate " + text + " gives " + net + " instead of " + expected);
			}
		}
		o.Set_line_rate(0, old_rate);
		if (Math.abs(o.line_net(0) - net0) > tol(net0)) {
			bad.add("the old rate gives " + o.line_net(0) + " instead of " + net0);
		}
		o.Close_modal();
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the line amount does not follow the unit rate: " + bad);
		test.pass("Amount follows the unit rate");
	}

	// OS56: every discount of the drop down: amount = amount without discount x (1 - discount %);
	// "-- None --" brings the first amount back
	@Test
	public void OS56_amount_follows_every_discount() {
		test = extent.createTest("OS56 Amount follows every discount");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		open_edit_for_calculation(o);
		int last = o.line_count() - 1;
		for (int line = 0; line <= last; line++) {
			List<String> options = o.line_discount_options(line);
			o.Select_line_discount(line, "-- None --");
			double none = o.line_net(line);
			List<String> bad = new ArrayList<String>();
			for (String option : options) {
				if (option.contains("None")) {
					continue;
				}
				o.Select_line_discount(line, option);
				double expected = none * (1 - discount_percent(option) / 100);
				double net = o.line_net(line);
				test.info("Line " + (line + 1) + " discount '" + option + "': net " + net + " (expected " + expected + " from " + none + ")");
				if (Math.abs(net - expected) > tol(expected)) {
					bad.add("line " + (line + 1) + " '" + option + "' gives " + net + " instead of " + expected);
				}
			}
			o.Select_line_discount(line, "-- None --");
			if (Math.abs(o.line_net(line) - none) > tol(none)) {
				bad.add("line " + (line + 1) + ": '-- None --' gives " + o.line_net(line) + " instead of " + none);
			}
			if (!bad.isEmpty()) {
				o.Close_modal();
				Assert.fail("DEFECT: the line amount does not follow the discount: " + bad);
			}
		}
		o.Close_modal();
		test.pass("Amount follows every discount");
	}

	// OS57: rate x2, quantity x3 and a discount together; the same result in any order of the changes
	@Test
	public void OS57_rate_quantity_and_discount_together() {
		test = extent.createTest("OS57 Rate, quantity and discount together");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		open_edit_for_calculation(o);
		List<String> options = o.line_discount_options(0);
		String discount = null;
		for (String option : options) {
			if (!option.contains("None")) {
				discount = option;
			}
		}
		if (discount == null) {
			o.Close_modal();
			throw new SkipException("The line has no discount to choose");
		}
		o.Select_line_discount(0, "-- None --");
		List<String> line = o.line_texts().get(0);
		String old_rate = line.get(4);
		String old_qty = line.get(6);
		double net0 = o.line_net(0);
		String new_rate = String.valueOf(Math.round(S_OrderStatus_Cls.to_number(old_rate) * 2 * 100) / 100.0);
		String new_qty = String.valueOf((long) (S_OrderStatus_Cls.to_number(old_qty) * 3));
		double expected = net0 * (S_OrderStatus_Cls.to_number(new_rate) / S_OrderStatus_Cls.to_number(old_rate))
				* (S_OrderStatus_Cls.to_number(new_qty) / S_OrderStatus_Cls.to_number(old_qty))
				* (1 - discount_percent(discount) / 100);

		// order 1: rate, quantity, discount
		o.Set_line_rate(0, new_rate);
		o.Set_line_qty(0, new_qty);
		o.Select_line_discount(0, discount);
		double first = o.line_net(0);

		// back to the start, order 2: discount, quantity, rate
		o.Select_line_discount(0, "-- None --");
		o.Set_line_rate(0, old_rate);
		o.Set_line_qty(0, old_qty);
		double back = o.line_net(0);
		o.Select_line_discount(0, discount);
		o.Set_line_qty(0, new_qty);
		o.Set_line_rate(0, new_rate);
		double second = o.line_net(0);
		o.Close_modal();

		test.info("Rate " + old_rate + " -> " + new_rate + ", qty " + old_qty + " -> " + new_qty + ", discount '" + discount
				+ "': expected " + expected + " | order 1 " + first + " | order 2 " + second + " | back to start " + back + " (was " + net0 + ")");
		Assert.assertEquals(back, net0, tol(net0), "DEFECT: the amount did not come back to the start");
		Assert.assertEquals(first, expected, tol(expected), "DEFECT: rate x quantity x discount is wrong");
		Assert.assertEquals(second, first, tol(first), "DEFECT: the amount depends on the order of the changes");
		test.pass("Rate, quantity and discount work together");
	}

	// OS58: changing one line does not change the other lines
	@Test
	public void OS58_other_lines_not_affected() {
		test = extent.createTest("OS58 Other lines are not affected");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		open_edit_for_calculation(o);
		if (o.line_count() < 2) {
			o.Close_modal();
			throw new SkipException("The order has only one line");
		}
		List<List<String>> before = o.line_texts();
		double total_before = o.lines_total();
		double net_before = o.line_net(0);
		o.Set_line_qty(0, String.valueOf((long) (S_OrderStatus_Cls.to_number(before.get(0).get(6)) * 2)));
		o.Set_line_rate(0, String.valueOf(Math.round(S_OrderStatus_Cls.to_number(before.get(0).get(4)) * 3 * 100) / 100.0));
		List<List<String>> after = o.line_texts();
		double total_after = o.lines_total();
		double changed = o.line_net(0) - net_before;
		o.Close_modal();

		List<String> bad = new ArrayList<String>();
		for (int i = 1; i < before.size(); i++) {
			if (!before.get(i).equals(after.get(i))) {
				bad.add("line " + (i + 1) + " changed from " + before.get(i) + " to " + after.get(i));
			}
		}
		test.info("Line 1 net " + net_before + " -> " + (net_before + changed) + " | lines total " + total_before + " -> " + total_after);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: another line changed: " + bad);
		Assert.assertEquals(total_after - total_before, changed, tol(changed), "DEFECT: the total does not move by the change of the line");
		test.pass("Only the edited line changes");
	}

	// OS59: amounts keep 2 decimals and are rounded correctly for small and long rates
	@Test
	public void OS59_decimals_and_rounding() {
		test = extent.createTest("OS59 Decimals and rounding");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		open_edit_for_calculation(o);
		o.Select_line_discount(0, "-- None --");
		String old_rate = o.line_texts().get(0).get(4);
		double r0 = S_OrderStatus_Cls.to_number(old_rate);
		double net0 = o.line_net(0);
		double factor = net0 / (r0 * S_OrderStatus_Cls.to_number(o.line_texts().get(0).get(6)));
		List<String> bad = new ArrayList<String>();
		for (String rate : new String[] { "0.01", "10.555", "99.999", "1234.5678" }) {
			o.Set_line_rate(0, rate);
			List<String> now = o.line_texts().get(0);
			double used = S_OrderStatus_Cls.to_number(now.get(4));
			double expected = used * S_OrderStatus_Cls.to_number(now.get(6)) * factor;
			test.info("Rate '" + rate + "' (box keeps '" + now.get(4) + "'): net " + now.get(7) + " (expected about " + expected + ")");
			if (!now.get(7).matches("-?[\\d,]+\\.\\d{2}")) {
				bad.add("amount '" + now.get(7) + "' for rate " + rate + " does not have 2 decimals");
			}
			if (Math.abs(S_OrderStatus_Cls.to_number(now.get(7)) - expected) > Math.max(0.06, expected * 0.002)) {
				bad.add("rate " + rate + " gives " + now.get(7) + " instead of about " + expected);
			}
		}
		o.Close_modal();
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Decimals and rounding are correct");
	}

	// OS60: a NEW line (added with the + row) calculates with rate, quantity and discount
	@Test
	public void OS60_new_line_calculation() {
		test = extent.createTest("OS60 New line calculation");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		int before = o.line_count();
		if (o.Item_suggestions("leo").isEmpty()) {
			o.Close_modal();
			throw new SkipException("No item is suggested for 'leo'");
		}
		o.Search_item("leo");
		List<String> attributes = o.attribute_names();
		if (!attributes.isEmpty()) {
			o.Select_attribute(attributes.get(0));
			List<String> values = o.attribute_values();
			if (!values.isEmpty()) {
				o.Select_attribute_value(values.get(0));
			}
		}
		o.Click_add_item();
		Assert.assertEquals(o.line_count(), before + 1, "No line was added");
		int idx = o.line_count() - 1;
		List<String> line = o.line_texts().get(idx);
		double r0 = S_OrderStatus_Cls.to_number(line.get(4));
		double q0 = S_OrderStatus_Cls.to_number(line.get(6));
		double net0 = o.line_net(idx);
		test.info("New line " + line + " | rate " + r0 + " qty " + q0 + " net " + net0);
		Assert.assertEquals(net0, r0 * q0, 0.06 + net0 * 0.2, "The new line amount is not near rate x quantity");
		double factor = r0 * q0 == 0 ? 1 : net0 / (r0 * q0);

		List<String> bad = new ArrayList<String>();
		o.Set_line_rate(idx, "100");
		o.Set_line_qty(idx, "3");
		double expected = 100 * 3 * factor;
		if (Math.abs(o.line_net(idx) - expected) > tol(expected)) {
			bad.add("rate 100 x qty 3 gives " + o.line_net(idx) + " instead of " + expected);
		}
		for (String option : o.line_discount_options(idx)) {
			if (option.contains("None")) {
				continue;
			}
			o.Select_line_discount(idx, option);
			double with = expected * (1 - discount_percent(option) / 100);
			test.info("Discount '" + option + "': net " + o.line_net(idx) + " (expected " + with + ")");
			if (Math.abs(o.line_net(idx) - with) > tol(with)) {
				bad.add("discount '" + option + "' gives " + o.line_net(idx) + " instead of " + with);
			}
		}
		o.Close_modal();
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the new line does not calculate correctly: " + bad);
		test.pass("New line calculates correctly");
	}

	// OS41: add an item (item, attribute, value, Add): a new line appears; closing without saving drops it
	@Test
	public void OS41_add_item_and_cancel() {
		test = extent.createTest("OS41 Add an item");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		int before = o.line_count();
		List<String> suggestions = o.Item_suggestions("leo");
		test.info("Item suggestions for 'leo': " + suggestions);
		if (suggestions.isEmpty()) {
			o.Close_modal();
			throw new SkipException("No item is suggested for 'leo'");
		}
		o.Search_item("leo");
		test.info("Item picked: " + o.item_text() + " | attributes: " + o.attribute_names());
		List<String> attributes = o.attribute_names();
		if (!attributes.isEmpty()) {
			o.Select_attribute(attributes.get(0));
			List<String> values = o.attribute_values();
			test.info("Attribute values: " + values);
			if (!values.isEmpty()) {
				o.Select_attribute_value(values.get(0));
			}
		}
		o.Click_add_item();
		test.info("Lines " + before + " -> " + o.line_count() + " | " + o.line_texts());
		Assert.assertEquals(o.line_count(), before + 1, "No line was added by Add. Messages: " + o.validation_messages() + " " + o.notice());
		double total_now = o.lines_total();
		Assert.assertTrue(total_now > amount_of(order) - 0.05, "The total did not include the new line");
		o.Close_modal();

		open_view(o, order.get(0));
		Assert.assertEquals(o.line_count(), before, "DEFECT: the unsaved new line was kept");
		o.Close_modal();
		test.pass("Add item works and is not saved on close");
	}

	// OS42: Add without choosing an item must not add a line
	@Test
	public void OS42_add_without_item() {
		test = extent.createTest("OS42 Add without an item");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		int before = o.line_count();
		o.Click_plus();
		String alert = "";
		try {
			alert = o.Click_add_item();
		} catch (NoSuchElementException e) {
			test.info("Add button not usable: " + e.getMessage());
		}
		test.info("Alert: '" + alert + "' | lines " + before + " -> " + o.line_count() + " | messages "
				+ o.validation_messages() + " " + o.notice());
		int after = o.line_count();
		o.Close_modal();
		Assert.assertEquals(after, before, "DEFECT: Add without an item added a line");
		Assert.assertFalse(alert.isEmpty() && o.validation_messages().isEmpty(), "No message tells the user to select an item");
		test.pass("Nothing is added without an item");
	}

	// OS43: text that is not an item gives no suggestion and adds no line
	@Test
	public void OS43_add_unknown_item() {
		test = extent.createTest("OS43 Add an unknown item");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		int before = o.line_count();
		List<String> found = o.Item_suggestions("ZZQNoSuchItem" + unique());
		test.info("Suggestions: " + found);
		Assert.assertTrue(found.isEmpty(), "An item is suggested for text that matches nothing: " + found);
		String alert = "";
		try {
			alert = o.Click_add_item();
		} catch (NoSuchElementException e) {
			test.info("Add button not usable: " + e.getMessage());
		}
		test.info("Alert: '" + alert + "'");
		int after = o.line_count();
		o.Close_modal();
		Assert.assertEquals(after, before, "DEFECT: an unknown item added a line");
		test.pass("An unknown item is not added");
	}

	// OS44: Change customer opens a customer search that suggests customers (nothing is saved)
	@Test
	public void OS44_change_customer_search() {
		test = extent.createTest("OS44 Change customer");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		o.Click_change_customer();
		Assert.assertTrue(o.customer_search_open(), "Change did not open the customer search box");
		List<String> found = o.Customer_suggestions("sree");
		test.info("Customers for 'sree': " + found);
		Assert.assertFalse(found.isEmpty(), "No customer is suggested for 'sree'");
		List<String> none = o.Customer_suggestions("ZZQNoSuch" + unique());
		Assert.assertTrue(none.isEmpty(), "A customer is suggested for text that matches nothing: " + none);
		o.Close_modal();
		List<String> row = reload_row(o, order.get(0));
		Assert.assertEquals(row.get(3), order.get(3), "DEFECT: the customer changed without saving");
		test.pass("Customer search works and nothing is saved");
	}

	// OS45: the remark can be typed; closing without saving keeps the old remark
	@Test
	public void OS45_remark_not_saved_on_close() {
		test = extent.createTest("OS45 Remark not saved on close");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		String old = o.modal_remark();
		o.Set_remark("ZQ not saved " + unique());
		test.info("Remark '" + old + "' -> '" + o.modal_remark() + "'");
		Assert.assertTrue(o.modal_remark().startsWith("ZQ not saved"), "The remark box did not take the typed text");
		o.Close_modal();
		open_view(o, order.get(0));
		Assert.assertEquals(o.modal_remark(), old, "DEFECT: the unsaved remark was kept");
		o.Close_modal();
		test.pass("An unsaved remark is dropped");
	}

	// OS46: deleting every line must not be possible to save (observation: the delete of a line is only clicked
	// when the switch ALLOW_EDIT_SAVE is true because a line delete may act at once)
	@Test
	public void OS46_delete_line_and_cancel() {
		test = extent.createTest("OS46 Delete a line and cancel");
		if (!ALLOW_EDIT_SAVE) {
			throw new SkipException("ALLOW_EDIT_SAVE is false");
		}
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		int before = o.line_count();
		if (before < 2) {
			o.Close_modal();
			throw new SkipException("The order has only one line");
		}
		o.Click_line_delete(before - 1);
		List<String> messages = new ArrayList<String>();
		if (o.dialog_open()) {
			messages.add("dialog: " + o.Dialog_text() + " " + o.dialog_buttons());
			confirm_dialog(o);
		}
		test.info("Lines " + before + " -> " + o.line_count() + " | " + messages + " | " + o.notice());
		Assert.assertEquals(o.line_count(), before - 1, "The line was not removed from the pop-up");
		o.Close_modal();
		open_view(o, order.get(0));
		int kept = o.line_count();
		o.Close_modal();
		Assert.assertEquals(kept, before, "DEFECT: a line deleted in the pop-up was removed without Save changes");
		test.pass("A deleted line is only applied with Save changes");
	}

	// ======================= print =======================

	// the number after "Grand Total (₹)" of the print page
	private double grand_total(String text) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("Grand Total\\s*\\([^)]*\\)\\s*([\\d,]+\\.\\d+)").matcher(text);
		return m.find() ? S_OrderStatus_Cls.to_number(m.group(1)) : -1;
	}

	private void check_print(S_OrderStatus_Cls o, List<String> order) {
		String list_tab = o.Click_print(order.get(0));
		try {
			String text = o.print_text();
			test.info("Print page " + o.print_url() + ": " + text);
			Assert.assertTrue(o.print_url().toLowerCase().contains("printorder"), "The print page did not open: " + o.print_url());
			Assert.assertTrue(o.print_url().contains(order.get(0)), "The print page URL has another order number: " + o.print_url());
			Assert.assertTrue(text.contains(order.get(0)), "The print page does not show the order number");
			Assert.assertTrue(o.print_has_button("Print Order"), "The Print Order button is missing");
			Assert.assertTrue(o.print_has_button("Cancel"), "The Cancel button is missing");
			Assert.assertTrue(text.toLowerCase().contains(order.get(3).toLowerCase().split("[,.]")[0].trim()),
					"The print page does not show the customer " + order.get(3));
			Assert.assertEquals(grand_total(text), amount_of(order), 0.05,
					"DEFECT: the Grand Total of the print page differs from the order amount of the list");
		} finally {
			o.Close_print(list_tab);
		}
	}

	// OS47: print of a pending order
	@Test
	public void OS47_print_pending_order() {
		test = extent.createTest("OS47 Print a pending order");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		test.info("Print icon state: " + o.print_icon_state(order.get(0)));
		check_print(o, order);
		Assert.assertTrue(o.is_list_page(), "The list is not shown after closing the print page");
		test.pass("Print page of " + order.get(0) + " is correct");
	}

	// OS48: print of an approved / invoiced order, and the print page lists the items of the order
	@Test
	public void OS48_print_closed_order() {
		test = extent.createTest("OS48 Print an approved / invoiced order");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_closed_order(o);
		open_view(o, order.get(0));
		List<List<String>> lines = o.line_texts();
		o.Close_modal();
		test.info("Print icon state: " + o.print_icon_state(order.get(0)));
		String list_tab = o.Click_print(order.get(0));
		try {
			String text = o.print_text().replaceAll("\\s+", " ").toLowerCase();
			Assert.assertTrue(text.contains(order.get(0).toLowerCase()), "The print page does not show the order number");
			Assert.assertEquals(grand_total(o.print_text()), amount_of(order), 0.05, "DEFECT: Grand Total differs from the list amount");
			List<String> missing = new ArrayList<String>();
			for (List<String> line : lines) {
				String name = line.get(2).replaceAll("\\s+", " ").toLowerCase();
				if (!text.contains(name)) {
					missing.add(name);
				}
			}
			Assert.assertTrue(missing.isEmpty(), "DEFECT: items of the order are missing on the print page: " + missing);
		} finally {
			o.Close_print(list_tab);
		}
		test.pass("Print page lists the items of " + order.get(0));
	}

	// ======================= delete dialog =======================

	// OS49: Delete of a pending order asks for a confirmation; Cancel keeps the order
	@Test
	public void OS49_delete_asks_confirmation_and_cancel_keeps() {
		test = extent.createTest("OS49 Delete asks for confirmation");
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		o.Click_delete(order.get(0));
		Assert.assertTrue(o.dialog_open(), "DEFECT: Delete does not ask for a confirmation");
		test.info("Dialog: " + o.Dialog_text() + " | buttons " + o.dialog_buttons());
		boolean cancelled = false;
		for (String b : new String[] { "Cancel", "No", "Close" }) {
			try {
				o.Click_dialog_button(b);
				cancelled = true;
				break;
			} catch (NoSuchElementException e) {
				// try the next name
			}
		}
		Assert.assertTrue(cancelled, "The delete dialog has no Cancel / No button: " + o.dialog_buttons());
		List<String> row = reload_row(o, order.get(0));
		Assert.assertNotNull(row, "DEFECT: the order was deleted although Cancel was chosen");
		Assert.assertEquals(row.get(6), "Pending", "The status changed after Cancel");
		test.pass("Delete asks and Cancel keeps the order");
	}

	// ======================= changes (guarded by the switches) =======================

	// OS50: save a changed remark of a pending order, check it in the view, then put the old remark back
	@Test
	public void OS50_save_remark_and_restore() {
		test = extent.createTest("OS50 Save a remark and restore it");
		if (!ALLOW_EDIT_SAVE) {
			throw new SkipException("ALLOW_EDIT_SAVE is false");
		}
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		String old = o.modal_remark();
		String text = "ZQ remark " + unique();
		o.Set_remark(text);
		o.Click_modal_button("Save changes");
		List<String> messages = confirm_dialog(o);
		test.info("Saved remark '" + text + "' | messages " + messages);

		List<String> row = reload_row(o, order.get(0));
		Assert.assertNotNull(row, "The order is no longer listed after saving");
		open_view(o, order.get(0));
		String saved = o.modal_remark();
		o.Close_modal();

		// put the old remark back
		open_edit(o, order.get(0));
		o.Set_remark(old);
		o.Click_modal_button("Save changes");
		confirm_dialog(o);
		reload_row(o, order.get(0));
		open_view(o, order.get(0));
		String restored = o.modal_remark();
		o.Close_modal();
		test.info("Saved '" + saved + "' | restored '" + restored + "' (old '" + old + "')");
		Assert.assertEquals(saved, text, "DEFECT: the saved remark is not shown");
		Assert.assertEquals(restored, old, "The old remark could not be put back");
		Assert.assertEquals(row.get(6), "Pending", "The order status changed when only the remark was saved");
		test.pass("Remark saved and restored");
	}

	// OS51: save a quantity change: the order amount in the list changes; the old quantity is put back
	@Test
	public void OS51_save_quantity_and_restore() {
		test = extent.createTest("OS51 Save a quantity and restore it");
		if (!ALLOW_EDIT_SAVE) {
			throw new SkipException("ALLOW_EDIT_SAVE is false");
		}
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		String old_qty = o.line_texts().get(0).get(6);
		double net_old = o.line_net(0);
		o.Set_line_qty(0, String.valueOf((long) S_OrderStatus_Cls.to_number(old_qty) + 1));
		double net_new = o.line_net(0);
		double expected_amount = amount_of(order) - net_old + net_new;
		o.Click_modal_button("Save changes");
		test.info("Qty " + old_qty + " -> +1 | message " + confirm_dialog(o));

		List<String> row = reload_row(o, order.get(0));
		double amount_after = row == null ? -1 : amount_of(row);

		// restore
		open_edit(o, order.get(0));
		o.Set_line_qty(0, old_qty);
		o.Click_modal_button("Save changes");
		confirm_dialog(o);
		List<String> back = reload_row(o, order.get(0));
		test.info("Amount: before " + amount_of(order) + " | after +1 qty " + amount_after + " (expected " + expected_amount
				+ ") | restored " + (back == null ? "-" : amount_of(back)));
		Assert.assertEquals(amount_after, expected_amount, 0.05, "DEFECT: the order amount in the list is not the saved total");
		Assert.assertNotNull(back, "The order is not listed after restoring");
		Assert.assertEquals(amount_of(back), amount_of(order), 0.05, "The old quantity could not be put back");
		test.pass("Quantity saved and restored");
	}

	// OS52: Approve a pending order: its status becomes Approved and it can no longer be edited or deleted
	@Test
	public void OS52_approve_order() {
		test = extent.createTest("OS52 Approve a pending order");
		if (!ALLOW_APPROVE_DELETE) {
			throw new SkipException("ALLOW_APPROVE_DELETE is false: approving cannot be undone");
		}
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		open_edit(o, order.get(0));
		o.Click_modal_button("Approve");
		List<String> messages = confirm_dialog(o);
		test.info("Approved " + order.get(0) + " | messages " + messages);
		List<String> row = reload_row(o, order.get(0));
		if (row == null) {
			search_wide(o, "Approved");
			row = o.row_of(order.get(0));
		}
		Assert.assertNotNull(row, "The approved order is not listed");
		Assert.assertEquals(row.get(6), "Approved", "DEFECT: the status is not Approved after Approve");
		List<String> kinds = o.actions_of(order.get(0));
		Assert.assertFalse(kinds.contains("edit") || kinds.contains("delete"), "DEFECT: an approved order can still be edited / deleted: " + kinds);
		test.pass("Order approved");
	}

	// OS53: Delete a pending order: it leaves the list and the number of orders drops by one
	@Test
	public void OS53_delete_order() {
		test = extent.createTest("OS53 Delete a pending order");
		if (!ALLOW_APPROVE_DELETE) {
			throw new SkipException("ALLOW_APPROVE_DELETE is false: deleting cannot be undone");
		}
		S_OrderStatus_Cls o = new S_OrderStatus_Cls(driver);

		List<String> order = find_order(o, "Pending");
		search_wide(o, "");
		int total = o.entries_total();
		o.Click_delete(order.get(0));
		List<String> messages = confirm_dialog(o);
		test.info("Deleted " + order.get(0) + " | messages " + messages);
		search_wide(o, "");
		test.info("Orders " + total + " -> " + o.entries_total());
		Assert.assertNull(o.row_of(order.get(0)), "DEFECT: the deleted order is still listed");
		Assert.assertEquals(o.entries_total(), total - 1, "The number of orders did not drop by one");
		test.pass("Order deleted");
	}
}
