package TestPkg;

import UtillPkg.TestEnv;
import UtillPkg.WaitUtil;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import PagePkg.A_Login_Cls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.Y_OrderToSalesDifference_Cls;

/*
 * Order-to-Sales Difference (Reports > Order-to-Sales Difference), logged in as the web user - tests OS01 .. OS46.
 * A read only report: one line per order item with the ordered and the billed quantity / rate / amount, the two
 * differences and the billing status. Nothing is created or changed.
 * The CALCULATION tests (OS30 .. OS40) read every line of every page and check:
 *   Order Amount = Order Qty x Order Rate, Billed Amount = Billed Qty x Billed Rate,
 *   Qty Difference = Order Qty - Billed Qty, Amount Difference = Order Amount - Billed Amount,
 *   and that the Billing Status agrees with the billed quantity and the invoice.
 * Cells are read by column name, so the order of the columns does not matter.
 */
public class Y_OrderToSalesDifference_Test extends BaseCls {

	// login of the web user (a test login of this project)
	static final String WEB_USER = TestEnv.get("web.user", "your-web-user");
	static final String WEB_PASS = TestEnv.get("web.password", "your-web-password");

	static final String FROM_WIDE = "01/01/2026";
	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	static final double TOLERANCE = 0.011;

	static final String[] HEADERS = { "SL.NO", "ORDER DATE", "ORDER NO", "CUSTOMER NAME", "SALESMAN", "INVOICE NO", "INVOICE DATE",
			"ITEM CODE", "ITEM NAME", "ORDER QTY", "ORDER RATE", "BILLED QTY", "BILLED RATE", "ORDER AMOUNT", "BILLED AMOUNT",
			"QTY DIFFERENCE", "AMOUNT DIFFERENCE", "BILLING STATUS" };

	private String main_tab;

	private String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
	}

	private String today() {
		return LocalDate.now().format(DATE);
	}

	// runs ONCE: fresh session, login as the web user
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
			login.Login(WEB_USER, WEB_PASS);
		} catch (TimeoutException e) {
			driver.manage().deleteAllCookies();
			driver.get(url);
			login.Login(WEB_USER, WEB_PASS);
		}
		Assert.assertTrue(login.login_success(), "Could not log in as " + WEB_USER);
		main_tab = driver.getWindowHandle();
	}

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
	}

	// opens Reports > Order-to-Sales Difference from the dashboard
	private void go_to_report() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				// the report may sit under any top menu (Off Take is under Field Management), so every menu is tried
				String[] modules = { "Reports", "Field Management", "Stock Item", "Customer Details", "Employee Info",
						"Company Info", "Device", "Scheme" };
				String[] names = { "Order-to-Sales", "Order to Sales", "Order To Sales", "OrderToSales", "Sales Difference",
						"Order Sales", "Difference" };
				boolean opened = false;
				for (String module : modules) {
					for (String name : names) {
						try {
							dash.Click_submenu_containing(module, name);
							opened = true;
							break;
						} catch (java.util.NoSuchElementException | NoSuchElementException e) {
							dash.Click_home();
						}
					}
					if (opened) {
						break;
					}
				}
				if (!opened) {
					StringBuilder all = new StringBuilder();
					for (String module : modules) {
						all.append(module).append(" = ").append(dash.get_submenu_names(module)).append("; ");
					}
					throw new NoSuchElementException("No menu item for 'Order-to-Sales Difference'. Menus found: " + all);
				}
				WaitUtil.settle(driver, 800);
				return;
			} catch (ElementClickInterceptedException e) {
				if (attempt == 4) {
					throw e;
				}
				WaitUtil.settle(driver, 2500);
			}
		}
	}

	// every test starts with a fresh Order-to-Sales Difference screen
	@BeforeMethod
	public void open_report() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		clean_up();
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);
		try {
			go_to_report();
			new WebDriverWait(driver, Duration.ofSeconds(30), Duration.ofMillis(100)).until(x -> o.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_report();
			new WebDriverWait(driver, Duration.ofSeconds(30), Duration.ofMillis(100)).until(x -> o.is_list_page());
		}
		remember_clean_screen();
	}

	// ---------------------------------- helpers ----------------------------------

	private String val(Map<String, String> rec, String column) {
		String v = rec.get(column);
		if (v == null) {
			throw new AssertionError("The result table has no column '" + column + "'. Columns found: " + rec.keySet());
		}
		return v;
	}

	private double num(Map<String, String> rec, String column) {
		return Y_OrderToSalesDifference_Cls.to_number(val(rec, column));
	}

	private boolean blank(Map<String, String> rec, String column) {
		return val(rec, column).trim().isEmpty();
	}

	private LocalDate date_of(Map<String, String> rec) {
		return LocalDate.parse(val(rec, "ORDER DATE").trim(), DATE);
	}

	// "order no / item code" - names a line in a message
	private String line(Map<String, String> rec) {
		return val(rec, "ORDER NO") + " / " + val(rec, "ITEM CODE") + " (" + val(rec, "CUSTOMER NAME") + ")";
	}

	// identifies a line without the serial number (the same line can be on another page after a new search)
	private String key(Map<String, String> rec) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : rec.entrySet()) {
			if (!e.getKey().equals("SL.NO")) {
				sb.append(e.getValue()).append('|');
			}
		}
		return sb.toString();
	}

	private boolean close(double a, double b) {
		return Math.abs(a - b) <= TOLERANCE;
	}

	// searches the wide date range and returns every line of every page; skips the test when there is none
	private List<Map<String, String>> wide(Y_OrderToSalesDifference_Cls o) {
		o.Search_dates(FROM_WIDE, today());
		List<Map<String, String>> all = o.all_records();
		test.info(FROM_WIDE + " .. " + today() + ": " + all.size() + " lines | " + o.entries_text());
		if (all.isEmpty()) {
			throw new SkipException("No order line is listed for " + FROM_WIDE + " .. " + today());
		}
		return all;
	}

	// the first page of the wide search (the list as it is shown)
	private List<Map<String, String>> wide_first_page(Y_OrderToSalesDifference_Cls o) {
		o.Search_dates(FROM_WIDE, today());
		List<Map<String, String>> page = o.records();
		if (page.isEmpty()) {
			throw new SkipException("No order line is listed for " + FROM_WIDE + " .. " + today());
		}
		return page;
	}

	private String shorten(List<String> bad) {
		return bad.size() > 12 ? bad.subList(0, 12) + " ... and " + (bad.size() - 12) + " more" : bad.toString();
	}

	// the lines of an extra filter must all be lines of the unfiltered list
	private List<String> not_in(List<Map<String, String>> filtered, List<Map<String, String>> everything) {
		List<String> keys = new ArrayList<String>();
		for (Map<String, String> r : everything) {
			keys.add(key(r));
		}
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : filtered) {
			if (!keys.contains(key(r))) {
				bad.add(line(r));
			}
		}
		return bad;
	}

	// ======================= screen and search form =======================

	// OS01: the screen shows Search, Export, the table filter, "Show N entries" and the 18 column headers
	@Test
	public void OS01_screen_elements() {
		test = extent.createTest("OS01 Order-to-Sales Difference screen elements");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		Assert.assertTrue(o.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(o.verify_export().isDisplayed(), "Export button is missing");
		Assert.assertTrue(o.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		Assert.assertTrue(o.verify_page_length().isDisplayed(), "'Show N entries' is missing");
		List<String> headers = o.headers();
		test.info("Headers: " + headers + " | " + o.entries_text());
		for (String h : HEADERS) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		test.pass("Screen elements are displayed");
	}

	// OS02: the Search button opens the form with its 8 fields, Search and Clear, and the defaults
	@Test
	public void OS02_search_form_opens() {
		test = extent.createTest("OS02 Search form opens");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		o.Open_search_form();
		Assert.assertTrue(o.verify_hierarchy_type().isDisplayed(), "Hierarchy Type is missing");
		Assert.assertTrue(o.verify_supply_by().isDisplayed(), "Supply By is missing");
		Assert.assertTrue(o.verify_route().isDisplayed(), "Route is missing");
		Assert.assertTrue(o.verify_employee_type().isDisplayed(), "Employee Type is missing");
		Assert.assertTrue(o.verify_salesman().isDisplayed(), "Salesman is missing");
		Assert.assertTrue(o.verify_billing_status().isDisplayed(), "Billing Status is missing");
		Assert.assertTrue(o.verify_from().isDisplayed(), "From Date is missing");
		Assert.assertTrue(o.verify_to().isDisplayed(), "To Date is missing");
		Assert.assertTrue(o.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertTrue(o.verify_clear().isDisplayed(), "The Clear button is missing");
		test.info("Defaults: hierarchy '" + o.selected(o.verify_hierarchy_type()) + "' | employee type '"
				+ o.selected(o.verify_employee_type()) + "' | billing '" + o.selected(o.verify_billing_status()) + "' | from "
				+ o.verify_from().getAttribute("value") + " | to " + o.verify_to().getAttribute("value"));
		Assert.assertEquals(o.verify_to().getAttribute("value"), today(), "Default To Date");
		Assert.assertEquals(o.selected(o.verify_billing_status()), "All", "Default Billing Status");
		Assert.assertFalse(o.verify_from().getAttribute("value").isEmpty(), "Default From Date is empty");
		test.pass("Search form is displayed with its defaults");
	}

	// OS03: the options of the three drop downs are written to the report; Billing Status has All and Not Billed
	@Test
	public void OS03_dropdown_options() {
		test = extent.createTest("OS03 Drop down options");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		o.Open_search_form();
		List<String> hierarchy = o.options_of(o.verify_hierarchy_type());
		List<String> types = o.options_of(o.verify_employee_type());
		List<String> billing = o.all_options_of(o.verify_billing_status());
		test.info("Hierarchy Type (" + hierarchy.size() + "): " + hierarchy);
		test.info("Employee Type (" + types.size() + "): " + types);
		test.info("Billing Status (" + billing.size() + "): " + billing);
		Assert.assertFalse(hierarchy.isEmpty(), "The Hierarchy Type drop down has no value");
		Assert.assertFalse(types.isEmpty(), "The Employee Type drop down has no value");
		Assert.assertTrue(billing.contains("All"), "Billing Status has no 'All': " + billing);
		boolean not_billed = false;
		for (String b : billing) {
			not_billed |= b.toLowerCase().contains("not");
		}
		Assert.assertTrue(not_billed, "Billing Status has no 'Not Billed': " + billing);
		test.pass("Drop downs are filled");
	}

	// OS04: the list that opens with the screen only has lines inside the default From / To dates
	@Test
	public void OS04_default_list() {
		test = extent.createTest("OS04 Default list");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> rows = o.records();
		o.Open_search_form();
		LocalDate from = LocalDate.parse(o.verify_from().getAttribute("value"), DATE);
		LocalDate to = LocalDate.parse(o.verify_to().getAttribute("value"), DATE);
		test.info("Default range " + from.format(DATE) + " .. " + to.format(DATE) + ": " + rows.size() + " lines on page 1 | " + o.entries_text());
		if (rows.isEmpty()) {
			throw new SkipException("The default list is empty");
		}
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : rows) {
			LocalDate d = date_of(r);
			if (d.isBefore(from) || d.isAfter(to)) {
				bad.add(val(r, "ORDER DATE") + " " + line(r));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the default list shows lines outside " + from.format(DATE) + " .. " + to.format(DATE) + ": " + shorten(bad));
		test.pass("The default list follows the default dates");
	}

	// OS05: a wide date range lists only lines inside the range
	@Test
	public void OS05_search_wide_date_range() {
		test = extent.createTest("OS05 Search a wide date range");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		LocalDate from = LocalDate.parse(FROM_WIDE, DATE);
		LocalDate to = LocalDate.now();
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : all) {
			LocalDate d = date_of(r);
			if (d.isBefore(from) || d.isAfter(to)) {
				bad.add(val(r, "ORDER DATE") + " " + line(r));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: lines outside the date range: " + shorten(bad));
		if (o.entries_total() >= 0) {
			Assert.assertEquals(all.size(), o.entries_total(), "The lines of all pages differ from the entries line");
		}
		test.pass("Wide range works: " + all.size() + " lines");
	}

	// OS06: one single day lists every line of that day (the To Date day is included)
	@Test
	public void OS06_search_single_day() {
		test = extent.createTest("OS06 Search a single day");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		String day = val(all.get(0), "ORDER DATE");
		int expected = 0;
		for (Map<String, String> r : all) {
			if (val(r, "ORDER DATE").equals(day)) {
				expected++;
			}
		}
		o.Search_dates(day, day);
		List<Map<String, String>> found = o.all_records();
		test.info("Day " + day + " .. " + day + ": " + found.size() + " lines, the wide search has " + expected + " lines on that day");
		for (Map<String, String> r : found) {
			Assert.assertEquals(val(r, "ORDER DATE"), day, "A line of another day: " + line(r));
		}
		Assert.assertEquals(found.size(), expected, "DEFECT: From Date = To Date = " + day + " lists " + found.size()
				+ " lines but the wide search has " + expected + " lines on that day (the To Date day is not fully included)");
		test.pass("Only " + day + " is listed");
	}

	// OS07: the first day (From Date) and the last day (To Date) of the range are both included
	@Test
	public void OS07_range_boundaries_included() {
		test = extent.createTest("OS07 Range boundaries");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		LocalDate oldest = LocalDate.MAX;
		LocalDate newest = LocalDate.MIN;
		for (Map<String, String> r : all) {
			LocalDate d = date_of(r);
			if (d.isBefore(oldest)) {
				oldest = d;
			}
			if (d.isAfter(newest)) {
				newest = d;
			}
		}
		o.Search_dates(oldest.format(DATE), newest.format(DATE));
		List<Map<String, String>> found = o.all_records();
		test.info("Range " + oldest.format(DATE) + " .. " + newest.format(DATE) + ": " + found.size() + " lines (the wide search has " + all.size() + ")");
		Assert.assertEquals(found.size(), all.size(), "DEFECT: the range from the oldest to the newest order day lists " + found.size()
				+ " lines but the wide search lists " + all.size() + ": the first or the last day is not included");
		test.pass("Both boundary days are included");
	}

	// OS08: From Date after To Date: nothing (or a message) must be shown
	@Test
	public void OS08_reversed_date_range() {
		test = extent.createTest("OS08 Reversed date range");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		o.Search_dates("20/10/2026", "01/10/2026");
		test.info("Rows: " + o.result_count() + " | messages: " + o.validation_messages() + " | notice: " + o.notice());
		Assert.assertTrue(o.result_count() == 0 || !o.validation_messages().isEmpty() || !o.notice().isEmpty(),
				"DEFECT: a reversed date range lists lines without any message");
		test.pass("Reversed range gives nothing or a message");
	}

	// OS09: a range in the future has no line
	@Test
	public void OS09_future_dates() {
		test = extent.createTest("OS09 Future date range");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		String from = LocalDate.now().plusDays(60).format(DATE);
		String to = LocalDate.now().plusDays(90).format(DATE);
		o.Search_dates(from, to);
		test.info(from + " .. " + to + ": rows " + o.result_count() + " | " + o.entries_text());
		Assert.assertEquals(o.result_count(), 0, "Lines are listed for a future range");
		Assert.assertTrue(o.no_data_shown() || o.entries_total() <= 0, "The table shows no 'No data' line");
		test.pass("A future range lists nothing");
	}

	// OS10: invalid dates are refused
	@Test
	public void OS10_invalid_dates() {
		test = extent.createTest("OS10 Invalid dates");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (String value : new String[] { "abcd", "31/02/2026", "99/99/9999" }) {
			o.Search_dates(value, today());
			o.Open_search_form();
			String kept = o.verify_from().getAttribute("value");
			test.info("From Date '" + value + "' -> box keeps '" + kept + "' | rows " + o.result_count() + " | messages "
					+ o.validation_messages() + " | " + o.entries_text());
			boolean refused = kept == null || !kept.equals(value) || !o.validation_messages().isEmpty()
					|| o.result_count() == 0 || !o.notice().isEmpty();
			if (!refused) {
				bad.add(value);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: invalid From Date values were accepted and lines listed: " + bad);
		test.pass("Invalid dates are not accepted");
	}

	// ======================= filters =======================

	// OS11: the salesman search lists only that salesman
	@Test
	public void OS11_search_by_salesman() {
		test = extent.createTest("OS11 Search by salesman");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		String salesman = val(all.get(0), "SALESMAN");
		test.info("Salesman used: " + salesman);
		o.Search_report("", "", "", "", salesman, "", FROM_WIDE, today());
		List<Map<String, String>> found = o.all_records();
		int expected = 0;
		for (Map<String, String> r : all) {
			if (val(r, "SALESMAN").equalsIgnoreCase(salesman)) {
				expected++;
			}
		}
		test.info(found.size() + " lines found, the wide search has " + expected + " lines of " + salesman);
		Assert.assertTrue(found.size() > 0, "No line was found for " + salesman);
		for (Map<String, String> r : found) {
			Assert.assertTrue(val(r, "SALESMAN").equalsIgnoreCase(salesman), "A line of another salesman: " + line(r) + " " + val(r, "SALESMAN"));
		}
		Assert.assertEquals(found.size(), expected, "DEFECT: the salesman search lists " + found.size() + " lines but the wide search has " + expected + " lines of " + salesman);
		test.pass("Only " + salesman + " is listed");
	}

	// OS12: the salesman box suggests salesmen; text that matches nothing gives nothing
	@Test
	public void OS12_salesman_suggestions() {
		test = extent.createTest("OS12 Salesman suggestions");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		String salesman = val(all.get(0), "SALESMAN");
		o.Open_search_form();
		List<String> found = new ArrayList<String>();
		String used = "";
		for (int len : new int[] { 1, 2, 3, salesman.length() }) {
			String start = salesman.substring(0, Math.min(len, salesman.length()));
			found = o.Suggestions_for(o.verify_salesman(), start);
			test.info("Suggestions for '" + start + "': " + found);
			if (!found.isEmpty()) {
				used = start;
				break;
			}
		}
		if (found.isEmpty()) {
			throw new SkipException("The salesman suggestions cannot be read for '" + salesman + "'");
		}
		if (used.length() > 2) {
			test.warning("OBSERVATION: the salesman box suggests only from " + used.length() + " letters");
		}
		List<String> none = o.Suggestions_for(o.verify_salesman(), "ZZQNoSuch" + unique());
		Assert.assertTrue(none.isEmpty(), "A salesman is suggested for text that matches nothing: " + none);
		test.pass("Salesman suggestions work");
	}

	// OS13: a salesman that does not exist is not ignored
	@Test
	public void OS13_unknown_salesman() {
		test = extent.createTest("OS13 Unknown salesman");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		o.Search_report("", "", "", "", "ZZQNoSuchSalesman" + unique(), "", FROM_WIDE, today());
		int now = o.result_count();
		test.info("All " + all.size() + " | now " + now + " rows on page 1 | " + o.entries_text() + " | messages " + o.validation_messages() + " " + o.notice());
		boolean ignored = now > 0 && o.all_records().size() >= all.size() && o.validation_messages().isEmpty() && o.notice().isEmpty();
		Assert.assertFalse(ignored, "DEFECT: a salesman that does not exist is ignored and all " + all.size() + " lines are listed without a message");
		test.pass("An unknown salesman does not list every line");
	}

	// OS14: Supply By: a suggested value lists only lines of the unfiltered list; an unknown value is not ignored
	@Test
	public void OS14_supply_by_filter() {
		test = extent.createTest("OS14 Supply By filter");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		o.Open_search_form();
		String pick = "";
		for (String letter : new String[] { "a", "e", "s", "r", "1" }) {
			List<String> found = o.Suggestions_for(o.verify_supply_by(), letter);
			test.info("Supply By suggestions for '" + letter + "': " + found);
			if (!found.isEmpty()) {
				pick = found.get(0);
				break;
			}
		}
		if (pick.isEmpty()) {
			throw new SkipException("The Supply By box suggested nothing for a, e, s, r, 1");
		}
		o.Search_report("", pick, "", "", "", "", FROM_WIDE, today());
		List<Map<String, String>> found = o.all_records();
		test.info("Supply By '" + pick + "': " + found.size() + " of " + all.size() + " lines");
		Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: lines that are not in the unfiltered list: " + shorten(not_in(found, all)));
		o.Search_report("", "ZZQNoSuchSupply" + unique(), "", "", "", "", FROM_WIDE, today());
		boolean ignored = o.result_count() > 0 && o.all_records().size() >= all.size() && o.validation_messages().isEmpty() && o.notice().isEmpty();
		Assert.assertFalse(ignored, "DEFECT: a Supply By value that does not exist is ignored and every line is listed without a message");
		test.pass("Supply By filter works");
	}

	// OS15: Route: a suggested route lists only lines of the unfiltered list; an unknown route is not ignored
	@Test
	public void OS15_route_filter() {
		test = extent.createTest("OS15 Route filter");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		o.Open_search_form();
		String pick = "";
		for (String letter : new String[] { "a", "e", "c", "w", "1" }) {
			List<String> found = o.Suggestions_for(o.verify_route(), letter);
			test.info("Route suggestions for '" + letter + "': " + found);
			if (!found.isEmpty()) {
				pick = found.get(0);
				break;
			}
		}
		if (pick.isEmpty()) {
			throw new SkipException("The Route box suggested nothing for a, e, c, w, 1");
		}
		o.Search_report("", "", pick, "", "", "", FROM_WIDE, today());
		List<Map<String, String>> found = o.all_records();
		test.info("Route '" + pick + "': " + found.size() + " of " + all.size() + " lines");
		Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: lines that are not in the unfiltered list: " + shorten(not_in(found, all)));
		o.Search_report("", "", "ZZQNoSuchRoute" + unique(), "", "", "", FROM_WIDE, today());
		boolean ignored = o.result_count() > 0 && o.all_records().size() >= all.size() && o.validation_messages().isEmpty() && o.notice().isEmpty();
		Assert.assertFalse(ignored, "DEFECT: a route that does not exist is ignored and every line is listed without a message");
		test.pass("Route filter works");
	}

	// OS16: every Hierarchy Type: the lines are lines of the unfiltered list
	@Test
	public void OS16_hierarchy_type_filter() {
		test = extent.createTest("OS16 Hierarchy Type filter");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		o.Open_search_form();
		List<String> options = o.options_of(o.verify_hierarchy_type());
		List<String> bad = new ArrayList<String>();
		int tried = 0;
		for (String option : options) {
			if (tried++ >= 4) {
				break;
			}
			o.Search_report(option, "", "", "", "", "", FROM_WIDE, today());
			List<Map<String, String>> found = o.all_records();
			test.info("Hierarchy Type '" + option + "': " + found.size() + " of " + all.size() + " lines | messages " + o.validation_messages());
			for (String b : not_in(found, all)) {
				bad.add(option + " -> " + b);
			}
			Assert.assertTrue(o.is_list_page(), "The page broke for the Hierarchy Type " + option);
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: lines that are not in the unfiltered list: " + shorten(bad));
		test.pass("Hierarchy Type filter works");
	}

	// OS17: every Employee Type: the lines are lines of the unfiltered list
	@Test
	public void OS17_employee_type_filter() {
		test = extent.createTest("OS17 Employee Type filter");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		o.Open_search_form();
		List<String> options = o.options_of(o.verify_employee_type());
		List<String> bad = new ArrayList<String>();
		int tried = 0;
		for (String option : options) {
			if (tried++ >= 4) {
				break;
			}
			o.Search_report("", "", "", option, "", "", FROM_WIDE, today());
			List<Map<String, String>> found = o.all_records();
			List<String> salesmen = new ArrayList<String>();
			for (Map<String, String> r : found) {
				if (!salesmen.contains(val(r, "SALESMAN"))) {
					salesmen.add(val(r, "SALESMAN"));
				}
			}
			test.info("Employee Type '" + option + "': " + found.size() + " of " + all.size() + " lines | salesmen " + salesmen);
			for (String b : not_in(found, all)) {
				bad.add(option + " -> " + b);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: lines that are not in the unfiltered list: " + shorten(bad));
		test.pass("Employee Type filter works");
	}

	// OS18: the Billing Status filter lists only lines of that status
	@Test
	public void OS18_billing_status_filter() {
		test = extent.createTest("OS18 Billing Status filter");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		wide(o);
		o.Open_search_form();
		List<String> options = o.all_options_of(o.verify_billing_status());
		List<String> bad = new ArrayList<String>();
		for (String option : options) {
			if (option.equalsIgnoreCase("All")) {
				continue;
			}
			o.Search_report("", "", "", "", "", option, FROM_WIDE, today());
			List<Map<String, String>> found = o.all_records();
			test.info("Billing Status '" + option + "': " + found.size() + " lines");
			for (Map<String, String> r : found) {
				if (!val(r, "BILLING STATUS").trim().equalsIgnoreCase(option.trim())) {
					bad.add(option + " -> " + line(r) + " is '" + val(r, "BILLING STATUS") + "'");
				}
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the Billing Status filter lists lines of another status: " + shorten(bad));
		test.pass("Billing Status filter works");
	}

	// OS19: the lines of every status together are exactly the lines of "All"
	@Test
	public void OS19_status_filters_add_up_to_all() {
		test = extent.createTest("OS19 Status filters add up to All");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		o.Open_search_form();
		List<String> options = o.all_options_of(o.verify_billing_status());
		int sum = 0;
		StringBuilder parts = new StringBuilder();
		for (String option : options) {
			if (option.equalsIgnoreCase("All")) {
				continue;
			}
			o.Search_report("", "", "", "", "", option, FROM_WIDE, today());
			int n = o.all_records().size();
			sum += n;
			parts.append(option).append(' ').append(n).append(" + ");
		}
		test.info(parts + "= " + sum + " | All = " + all.size());
		Assert.assertEquals(sum, all.size(), "DEFECT: the lines of the statuses together (" + sum + ") are not the lines of All (" + all.size() + ")");
		test.pass("The statuses add up to All");
	}

	// OS20: salesman + billing status + date range together
	@Test
	public void OS20_combined_filters() {
		test = extent.createTest("OS20 Combined filters");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		Map<String, String> first = all.get(0);
		String salesman = val(first, "SALESMAN");
		String status = val(first, "BILLING STATUS").trim();
		String day = val(first, "ORDER DATE");
		o.Search_report("", "", "", "", salesman, status, day, today());
		List<Map<String, String>> found = o.all_records();
		test.info("Filters: " + salesman + " | " + status + " | " + day + " .. " + today() + " -> " + found.size() + " lines");
		Assert.assertTrue(found.size() > 0, "The line " + line(first) + " is not found with its own filters");
		boolean contained = false;
		for (Map<String, String> r : found) {
			Assert.assertTrue(val(r, "SALESMAN").equalsIgnoreCase(salesman), "Another salesman: " + line(r));
			Assert.assertTrue(val(r, "BILLING STATUS").trim().equalsIgnoreCase(status), "Another status: " + line(r));
			Assert.assertFalse(date_of(r).isBefore(LocalDate.parse(day, DATE)), "A line before the From Date: " + line(r));
			contained |= key(r).equals(key(first));
		}
		Assert.assertTrue(contained, "The line " + line(first) + " is not in its own filter result");
		test.pass("Combined filters work");
	}

	// OS21: Clear empties the filters
	@Test
	public void OS21_clear_button() {
		test = extent.createTest("OS21 Clear");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		String salesman = val(all.get(0), "SALESMAN");
		o.Search_report("", "", "", "", salesman, "Not Billed", FROM_WIDE, today());
		o.Click_clear();
		o.Open_search_form();
		test.info("After Clear: salesman '" + o.verify_salesman().getAttribute("value") + "' | billing '" + o.selected(o.verify_billing_status())
				+ "' | hierarchy '" + o.selected(o.verify_hierarchy_type()) + "' | from " + o.verify_from().getAttribute("value") + " | to "
				+ o.verify_to().getAttribute("value") + " | " + o.entries_text());
		Assert.assertEquals(o.verify_salesman().getAttribute("value"), "", "Salesman was not cleared");
		Assert.assertEquals(o.selected(o.verify_billing_status()), "All", "Billing Status was not reset to All");
		Assert.assertEquals(o.verify_to().getAttribute("value"), today(), "To Date was not reset to today");
		test.pass("Clear empties the filters");
	}

	// ======================= table filter, sorting, paging =======================

	// OS22: the table filter finds by customer; nothing matches -> 'No data'; clearing brings the rows back
	@Test
	public void OS22_table_filter() {
		test = extent.createTest("OS22 Table filter");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		String customer = wide_first_page(o).get(0).get("CUSTOMER NAME");
		o.Filter_table(customer);
		List<Map<String, String>> rows = o.records();
		test.info("Filter '" + customer + "': " + rows.size() + " rows | " + o.entries_text());
		Assert.assertTrue(rows.size() > 0, "The filter found nothing for " + customer);
		for (Map<String, String> r : rows) {
			Assert.assertTrue(val(r, "CUSTOMER NAME").equalsIgnoreCase(customer), "The filter lists another customer: " + line(r));
		}
		o.Filter_table("ZZQNothing" + unique());
		Assert.assertEquals(o.result_count(), 0, "Rows are listed for text that matches nothing");
		Assert.assertTrue(o.no_data_shown(), "No 'No data' line is shown");
		o.Filter_table("");
		Assert.assertTrue(o.result_count() > 0, "The rows did not come back after clearing the filter");
		test.pass("Table filter works");
	}

	// OS23: the table filter finds by order no, item code, item name and status; special text does no harm
	@Test
	public void OS23_table_filter_columns_and_special_text() {
		test = extent.createTest("OS23 Table filter columns and special text");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		Map<String, String> first = wide_first_page(o).get(0);
		for (String column : new String[] { "ORDER NO", "ITEM CODE", "ITEM NAME", "BILLING STATUS", "SALESMAN" }) {
			String text = val(first, column);
			o.Filter_table(text);
			List<Map<String, String>> rows = o.records();
			test.info("Filter by " + column + " '" + text + "': " + rows.size() + " rows");
			Assert.assertTrue(rows.size() > 0, "DEFECT: the table filter does not find '" + text + "' (" + column + ")");
			for (Map<String, String> r : rows) {
				Assert.assertTrue(r.toString().toLowerCase().contains(text.toLowerCase()), "Row without '" + text + "': " + line(r));
			}
		}
		for (String text : new String[] { "<script>alert(1)</script>", "' OR '1'='1", "%%%", "\\" }) {
			o.Filter_table(text);
			test.info("Filter '" + text + "': rows " + o.result_count());
			Assert.assertTrue(o.is_list_page(), "The page broke for the filter text " + text);
		}
		o.Filter_table("");
		Assert.assertTrue(o.result_count() > 0, "The rows did not come back");
		test.pass("Filter works");
	}

	private boolean text_sorted(List<String> values) {
		List<String> up = new ArrayList<String>(values);
		Collections.sort(up);
		List<String> down = new ArrayList<String>(up);
		Collections.reverse(down);
		return values.equals(up) || values.equals(down);
	}

	private boolean number_sorted(List<Double> values) {
		List<Double> up = new ArrayList<Double>(values);
		Collections.sort(up);
		List<Double> down = new ArrayList<Double>(up);
		Collections.reverse(down);
		return values.equals(up) || values.equals(down);
	}

	// OS24: the text columns sort as text
	@Test
	public void OS24_sort_text_columns() {
		test = extent.createTest("OS24 Sort the text columns");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		if (wide_first_page(o).size() < 2) {
			throw new SkipException("Two rows are needed to check sorting");
		}
		List<String> wrong = new ArrayList<String>();
		for (String column : new String[] { "ORDER NO", "CUSTOMER NAME", "SALESMAN", "ITEM CODE", "ITEM NAME", "BILLING STATUS" }) {
			o.Click_header(column);
			List<String> values = new ArrayList<String>();
			for (Map<String, String> r : o.records()) {
				values.add(val(r, column).toLowerCase());
			}
			boolean ok = text_sorted(values);
			test.info(column + ": " + values + " -> " + (ok ? "sorted" : "NOT sorted"));
			if (!ok) {
				wrong.add(column);
			}
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: these columns are not sorted correctly: " + wrong);
		test.pass("Text columns sort");
	}

	// OS25: the date columns sort by date
	@Test
	public void OS25_sort_date_column() {
		test = extent.createTest("OS25 Sort the order date");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		if (wide_first_page(o).size() < 2) {
			throw new SkipException("Two rows are needed to check sorting");
		}
		o.Click_header("ORDER DATE");
		List<LocalDate> dates = new ArrayList<LocalDate>();
		for (Map<String, String> r : o.records()) {
			dates.add(date_of(r));
		}
		List<LocalDate> up = new ArrayList<LocalDate>(dates);
		Collections.sort(up);
		List<LocalDate> down = new ArrayList<LocalDate>(up);
		Collections.reverse(down);
		boolean ok = dates.equals(up) || dates.equals(down);
		test.info("ORDER DATE: " + dates + " -> " + (ok ? "sorted" : "NOT sorted by date"));
		Assert.assertTrue(ok, "DEFECT: ORDER DATE is not sorted by date (probably sorted as text): " + dates);
		test.pass("Order date sorts");
	}

	// OS26: the number columns sort as numbers (not as text)
	@Test
	public void OS26_sort_number_columns() {
		test = extent.createTest("OS26 Sort the number columns");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		if (wide_first_page(o).size() < 2) {
			throw new SkipException("Two rows are needed to check sorting");
		}
		List<String> wrong = new ArrayList<String>();
		for (String column : new String[] { "ORDER QTY", "ORDER RATE", "BILLED QTY", "BILLED RATE", "ORDER AMOUNT", "BILLED AMOUNT",
				"QTY DIFFERENCE", "AMOUNT DIFFERENCE" }) {
			o.Click_header(column);
			List<Double> values = new ArrayList<Double>();
			for (Map<String, String> r : o.records()) {
				if (!blank(r, column)) {
					values.add(num(r, column));
				}
			}
			if (values.size() < 2) {
				test.info(column + ": fewer than 2 filled cells on this page, not checked");
				continue;
			}
			boolean ok = number_sorted(values);
			test.info(column + ": " + values + " -> " + (ok ? "sorted" : "NOT sorted as numbers"));
			if (!ok) {
				wrong.add(column);
			}
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: these columns are not sorted as numbers: " + wrong);
		test.pass("Number columns sort");
	}

	// OS27: Show N entries: the options, and the rows never exceed the chosen number
	@Test
	public void OS27_show_entries() {
		test = extent.createTest("OS27 Show entries");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		wide_first_page(o);
		List<String> options = o.page_length_options();
		test.info("Options: " + options + " | selected " + o.page_length_selected() + " | " + o.entries_text());
		Assert.assertFalse(options.isEmpty(), "'Show' has no option");
		Assert.assertEquals(o.page_length_selected(), "10", "Default page length");
		int total = o.entries_total();
		if (total < 0) {
			total = o.all_records().size();
		}
		for (String option : options) {
			if (!option.matches("\\d+")) {
				continue;
			}
			o.Set_page_length(option);
			int n = Integer.parseInt(option);
			test.info("Show " + n + ": rows " + o.result_count() + " of " + total);
			Assert.assertTrue(o.result_count() <= n, "DEFECT: more than " + n + " rows are shown");
			Assert.assertEquals(o.result_count(), Math.min(n, total), "Rows shown for Show " + n);
		}
		test.pass("Show entries works");
	}

	// OS28: paging: Next shows the next rows, Previous comes back, the serial numbers continue
	@Test
	public void OS28_paging() {
		test = extent.createTest("OS28 Paging");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> page1 = wide_first_page(o);
		if (!o.next_enabled()) {
			throw new SkipException("Only one page: no second page");
		}
		test.info("Page numbers: " + o.page_numbers() + " | " + o.entries_text());
		Assert.assertFalse(o.previous_enabled(), "Previous is enabled on the first page");
		o.Click_next();
		List<Map<String, String>> page2 = o.records();
		test.info("Page 2: " + page2.size() + " rows, first SL.NO " + (page2.isEmpty() ? "-" : val(page2.get(0), "SL.NO")));
		Assert.assertFalse(page2.isEmpty(), "The second page is empty");
		Assert.assertTrue(o.previous_enabled(), "Previous is disabled on the second page");
		List<String> keys1 = new ArrayList<String>();
		for (Map<String, String> r : page1) {
			keys1.add(key(r));
		}
		for (Map<String, String> r : page2) {
			Assert.assertFalse(keys1.contains(key(r)), "A line of page 1 is shown again on page 2: " + line(r));
		}
		Assert.assertEquals((int) num(page2.get(0), "SL.NO"), page1.size() + 1, "The serial number does not continue on page 2");
		o.Click_previous();
		Assert.assertEquals(o.records(), page1, "Previous does not return to the first page");
		test.pass("Paging works");
	}

	// OS29: the serial numbers run 1, 2, 3 ... without a gap over all pages
	@Test
	public void OS29_serial_numbers() {
		test = extent.createTest("OS29 Serial numbers");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		for (int i = 0; i < all.size(); i++) {
			int sl = (int) num(all.get(i), "SL.NO");
			if (sl != i + 1) {
				bad.add("line " + (i + 1) + " has SL.NO " + sl);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the serial numbers are not 1, 2, 3 ...: " + shorten(bad));
		test.pass("Serial numbers are continuous for " + all.size() + " lines");
	}

	// ======================= content =======================

	// OS30: formats of the listed values
	@Test
	public void OS30_value_formats() {
		test = extent.createTest("OS30 Value formats");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : all) {
			for (String column : new String[] { "ORDER NO", "CUSTOMER NAME", "SALESMAN", "ITEM CODE", "ITEM NAME", "BILLING STATUS" }) {
				if (blank(r, column)) {
					bad.add("empty " + column + " in " + line(r));
				}
			}
			if (!val(r, "ORDER DATE").matches("\\d{2}/\\d{2}/\\d{4}")) {
				bad.add("order date '" + val(r, "ORDER DATE") + "' in " + line(r));
			}
			if (!blank(r, "INVOICE DATE") && !val(r, "INVOICE DATE").matches("\\d{2}/\\d{2}/\\d{4}")) {
				bad.add("invoice date '" + val(r, "INVOICE DATE") + "' in " + line(r));
			}
			for (String column : new String[] { "ORDER QTY", "ORDER RATE", "BILLED QTY", "BILLED RATE", "ORDER AMOUNT", "BILLED AMOUNT" }) {
				if (!val(r, column).matches("-?[\\d,]+(\\.\\d+)?")) {
					bad.add(column + " '" + val(r, column) + "' is not a number in " + line(r));
				} else if (!val(r, column).matches("-?[\\d,]+\\.\\d{2}")) {
					bad.add(column + " '" + val(r, column) + "' does not have 2 decimals in " + line(r));
				}
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT in the list: " + shorten(bad));
		test.pass("Formats are correct");
	}

	// OS31: quantities must be above zero, rates and billed values must not be negative (a zero rate is written to the report)
	@Test
	public void OS31_values_are_sensible() {
		test = extent.createTest("OS31 Quantities and rates are sensible");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		List<String> zero_rate = new ArrayList<String>();
		for (Map<String, String> r : all) {
			if (num(r, "ORDER QTY") <= 0) {
				bad.add("order qty " + val(r, "ORDER QTY") + " in " + line(r));
			}
			for (String column : new String[] { "ORDER RATE", "BILLED QTY", "BILLED RATE", "BILLED AMOUNT" }) {
				if (num(r, column) < 0) {
					bad.add(column + " " + val(r, column) + " in " + line(r));
				}
			}
			if (num(r, "ORDER RATE") == 0) {
				zero_rate.add(line(r));
			}
		}
		if (!zero_rate.isEmpty()) {
			test.warning("OBSERVATION: " + zero_rate.size() + " lines have an Order Rate of 0.00: " + shorten(zero_rate));
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: impossible values: " + shorten(bad));
		test.pass("Values are sensible");
	}

	// OS32: every line of one order shows the same date, customer and salesman
	@Test
	public void OS32_order_lines_agree() {
		test = extent.createTest("OS32 Lines of one order agree");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		Map<String, Map<String, String>> first_of = new LinkedHashMap<String, Map<String, String>>();
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : all) {
			String no = val(r, "ORDER NO");
			Map<String, String> first = first_of.get(no);
			if (first == null) {
				first_of.put(no, r);
				continue;
			}
			for (String column : new String[] { "ORDER DATE", "CUSTOMER NAME", "SALESMAN" }) {
				if (!val(first, column).equals(val(r, column))) {
					bad.add(no + ": " + column + " '" + val(first, column) + "' and '" + val(r, column) + "'");
				}
			}
		}
		test.info(all.size() + " lines in " + first_of.size() + " orders");
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the lines of one order disagree: " + shorten(bad));
		test.pass("The lines of each order agree");
	}

	// OS33: the same order and item is not listed twice with identical values (written as an observation)
	@Test
	public void OS33_no_repeated_lines() {
		test = extent.createTest("OS33 No repeated lines");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> seen = new ArrayList<String>();
		List<String> repeated = new ArrayList<String>();
		for (Map<String, String> r : all) {
			String k = key(r);
			if (seen.contains(k)) {
				repeated.add(line(r) + " qty " + val(r, "ORDER QTY") + " rate " + val(r, "ORDER RATE"));
			}
			seen.add(k);
		}
		if (!repeated.isEmpty()) {
			test.warning("OBSERVATION: " + repeated.size() + " lines are repeated with exactly the same values (a duplicate or the same item added twice): " + shorten(repeated));
		}
		Assert.assertTrue(o.is_list_page(), "The page broke");
		test.pass("Repeated lines checked: " + repeated.size());
	}

	// ======================= calculations =======================

	// OS34: Order Amount = Order Qty x Order Rate on every line
	@Test
	public void OS34_order_amount_calculation() {
		test = extent.createTest("OS34 Order Amount = Order Qty x Order Rate");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : all) {
			double expected = num(r, "ORDER QTY") * num(r, "ORDER RATE");
			double shown = num(r, "ORDER AMOUNT");
			if (!close(expected, shown)) {
				bad.add(line(r) + ": " + val(r, "ORDER QTY") + " x " + val(r, "ORDER RATE") + " = " + String.format("%.2f", expected)
						+ " but Order Amount shows " + val(r, "ORDER AMOUNT"));
			}
		}
		test.info(all.size() + " lines checked, " + bad.size() + " differ");
		Assert.assertTrue(bad.isEmpty(), "DEFECT: Order Amount is not Order Qty x Order Rate on " + bad.size() + " of " + all.size()
				+ " lines (if the amount includes a discount or tax, the report does not show it): " + shorten(bad));
		test.pass("Order Amount is correct on all " + all.size() + " lines");
	}

	// OS35: Order Amount is never negative
	@Test
	public void OS35_no_negative_order_amount() {
		test = extent.createTest("OS35 No negative Order Amount");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : all) {
			if (num(r, "ORDER AMOUNT") < 0) {
				bad.add(line(r) + ": qty " + val(r, "ORDER QTY") + " rate " + val(r, "ORDER RATE") + " amount " + val(r, "ORDER AMOUNT"));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: a negative Order Amount is shown: " + shorten(bad));
		test.pass("No negative Order Amount");
	}

	// OS36: Billed Amount = Billed Qty x Billed Rate on every line
	@Test
	public void OS36_billed_amount_calculation() {
		test = extent.createTest("OS36 Billed Amount = Billed Qty x Billed Rate");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		int billed_lines = 0;
		for (Map<String, String> r : all) {
			if (num(r, "BILLED QTY") > 0) {
				billed_lines++;
			}
			double expected = num(r, "BILLED QTY") * num(r, "BILLED RATE");
			double shown = num(r, "BILLED AMOUNT");
			if (!close(expected, shown)) {
				bad.add(line(r) + ": " + val(r, "BILLED QTY") + " x " + val(r, "BILLED RATE") + " = " + String.format("%.2f", expected)
						+ " but Billed Amount shows " + val(r, "BILLED AMOUNT"));
			}
		}
		test.info(all.size() + " lines checked, " + billed_lines + " of them billed, " + bad.size() + " differ");
		if (billed_lines == 0) {
			test.warning("OBSERVATION: no line is billed in this range, so only the zero case was checked");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: Billed Amount is not Billed Qty x Billed Rate: " + shorten(bad));
		test.pass("Billed Amount is correct");
	}

	// OS37: Qty Difference = Order Qty - Billed Qty (blank cells are written to the report)
	@Test
	public void OS37_qty_difference_calculation() {
		test = extent.createTest("OS37 Qty Difference = Order Qty - Billed Qty");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		List<String> blanks = new ArrayList<String>();
		int checked = 0;
		int order_minus_billed = 0;
		int billed_minus_order = 0;
		for (Map<String, String> r : all) {
			double diff_a = num(r, "ORDER QTY") - num(r, "BILLED QTY");
			if (blank(r, "QTY DIFFERENCE")) {
				blanks.add(line(r) + " (" + val(r, "BILLING STATUS") + ", expected " + String.format("%.2f", diff_a) + ")");
				continue;
			}
			checked++;
			double shown = num(r, "QTY DIFFERENCE");
			boolean a = close(shown, diff_a);
			boolean b = close(shown, -diff_a);
			if (a) {
				order_minus_billed++;
			}
			if (b && !a) {
				billed_minus_order++;
			}
			if (!a && !b) {
				bad.add(line(r) + ": order " + val(r, "ORDER QTY") + ", billed " + val(r, "BILLED QTY") + ", difference shows " + val(r, "QTY DIFFERENCE"));
			}
		}
		test.info(checked + " lines with a difference checked: " + order_minus_billed + " as Order - Billed, " + billed_minus_order + " as Billed - Order | " + blanks.size() + " blank");
		if (!blanks.isEmpty()) {
			test.warning("OBSERVATION: Qty Difference is blank on " + blanks.size() + " lines (for a line that is not billed it would be the full Order Qty): " + shorten(blanks));
		}
		if (checked == 0) {
			throw new SkipException("Qty Difference is blank on all " + all.size() + " lines: nothing to calculate");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: Qty Difference is not the difference of Order Qty and Billed Qty: " + shorten(bad));
		Assert.assertFalse(order_minus_billed > 0 && billed_minus_order > 0,
				"DEFECT: Qty Difference is Order - Billed on " + order_minus_billed + " lines but Billed - Order on " + billed_minus_order + " lines");
		test.pass("Qty Difference is correct on " + checked + " lines");
	}

	// OS38: Amount Difference = Order Amount - Billed Amount (blank cells are written to the report)
	@Test
	public void OS38_amount_difference_calculation() {
		test = extent.createTest("OS38 Amount Difference = Order Amount - Billed Amount");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		List<String> blanks = new ArrayList<String>();
		int checked = 0;
		int order_minus_billed = 0;
		int billed_minus_order = 0;
		for (Map<String, String> r : all) {
			double diff_a = num(r, "ORDER AMOUNT") - num(r, "BILLED AMOUNT");
			if (blank(r, "AMOUNT DIFFERENCE")) {
				blanks.add(line(r) + " (" + val(r, "BILLING STATUS") + ", expected " + String.format("%.2f", diff_a) + ")");
				continue;
			}
			checked++;
			double shown = num(r, "AMOUNT DIFFERENCE");
			boolean a = close(shown, diff_a);
			boolean b = close(shown, -diff_a);
			if (a) {
				order_minus_billed++;
			}
			if (b && !a) {
				billed_minus_order++;
			}
			if (!a && !b) {
				bad.add(line(r) + ": order " + val(r, "ORDER AMOUNT") + ", billed " + val(r, "BILLED AMOUNT") + ", difference shows " + val(r, "AMOUNT DIFFERENCE"));
			}
		}
		test.info(checked + " lines with a difference checked: " + order_minus_billed + " as Order - Billed, " + billed_minus_order + " as Billed - Order | " + blanks.size() + " blank");
		if (!blanks.isEmpty()) {
			test.warning("OBSERVATION: Amount Difference is blank on " + blanks.size() + " lines (for a line that is not billed it would be the full Order Amount): " + shorten(blanks));
		}
		if (checked == 0) {
			throw new SkipException("Amount Difference is blank on all " + all.size() + " lines: nothing to calculate");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: Amount Difference is not the difference of Order Amount and Billed Amount: " + shorten(bad));
		Assert.assertFalse(order_minus_billed > 0 && billed_minus_order > 0,
				"DEFECT: Amount Difference is Order - Billed on " + order_minus_billed + " lines but Billed - Order on " + billed_minus_order + " lines");
		test.pass("Amount Difference is correct on " + checked + " lines");
	}

	// OS39: a line that is not billed has no invoice and nothing billed
	@Test
	public void OS39_not_billed_lines() {
		test = extent.createTest("OS39 Not Billed lines");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		int count = 0;
		for (Map<String, String> r : all) {
			if (!val(r, "BILLING STATUS").toLowerCase().contains("not")) {
				continue;
			}
			count++;
			if (!blank(r, "INVOICE NO")) {
				bad.add(line(r) + " has the invoice " + val(r, "INVOICE NO"));
			}
			if (!blank(r, "INVOICE DATE")) {
				bad.add(line(r) + " has the invoice date " + val(r, "INVOICE DATE"));
			}
			for (String column : new String[] { "BILLED QTY", "BILLED RATE", "BILLED AMOUNT" }) {
				if (num(r, column) != 0) {
					bad.add(line(r) + " " + column + " " + val(r, column));
				}
			}
		}
		test.info(count + " Not Billed lines of " + all.size());
		if (count == 0) {
			throw new SkipException("No Not Billed line in this range");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: a Not Billed line shows an invoice or a billed value: " + shorten(bad));
		test.pass("Not Billed lines are clean");
	}

	// OS40: the Billing Status agrees with the billed quantity and the invoice on every line
	@Test
	public void OS40_billing_status_agrees_with_quantities() {
		test = extent.createTest("OS40 Billing Status agrees with the quantities");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		List<String> statuses = new ArrayList<String>();
		for (Map<String, String> r : all) {
			String status = val(r, "BILLING STATUS").trim();
			if (!statuses.contains(status)) {
				statuses.add(status);
			}
			String s = status.toLowerCase();
			double ordered = num(r, "ORDER QTY");
			double billed = num(r, "BILLED QTY");
			boolean has_invoice = !blank(r, "INVOICE NO");
			if (s.contains("not")) {
				if (billed > 0) {
					bad.add(line(r) + " is '" + status + "' but Billed Qty is " + val(r, "BILLED QTY"));
				}
			} else if (s.contains("partial")) {
				if (!(billed > 0 && billed < ordered)) {
					bad.add(line(r) + " is '" + status + "' but order " + val(r, "ORDER QTY") + " billed " + val(r, "BILLED QTY"));
				}
			} else {
				if (billed <= 0 || billed + TOLERANCE < ordered) {
					bad.add(line(r) + " is '" + status + "' but order " + val(r, "ORDER QTY") + " billed " + val(r, "BILLED QTY"));
				}
				if (!has_invoice) {
					bad.add(line(r) + " is '" + status + "' but has no invoice no");
				}
			}
			if (billed > 0 && s.contains("not")) {
				bad.add(line(r) + " has billed quantity but the status is '" + status + "'");
			}
		}
		test.info("Statuses seen: " + statuses + " over " + all.size() + " lines");
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the Billing Status does not agree with the quantities: " + shorten(bad));
		test.pass("Billing Status agrees with the quantities");
	}

	// OS41: a billed line has an invoice no and an invoice date that is not before the order date
	@Test
	public void OS41_invoice_dates() {
		test = extent.createTest("OS41 Invoice dates");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		List<String> bad = new ArrayList<String>();
		int invoiced = 0;
		for (Map<String, String> r : all) {
			boolean no = !blank(r, "INVOICE NO");
			boolean dt = !blank(r, "INVOICE DATE");
			if (no != dt) {
				bad.add(line(r) + ": invoice no '" + val(r, "INVOICE NO") + "' and invoice date '" + val(r, "INVOICE DATE") + "'");
			}
			if (dt && val(r, "INVOICE DATE").matches("\\d{2}/\\d{2}/\\d{4}")) {
				invoiced++;
				LocalDate invoice = LocalDate.parse(val(r, "INVOICE DATE"), DATE);
				if (invoice.isBefore(date_of(r))) {
					bad.add(line(r) + ": invoice date " + val(r, "INVOICE DATE") + " is before the order date " + val(r, "ORDER DATE"));
				}
				if (invoice.isAfter(LocalDate.now())) {
					bad.add(line(r) + ": invoice date " + val(r, "INVOICE DATE") + " is in the future");
				}
			}
		}
		test.info(invoiced + " invoiced lines of " + all.size());
		if (invoiced == 0) {
			throw new SkipException("No invoiced line in this range");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: invoice no / date problems: " + shorten(bad));
		test.pass("Invoice dates are correct");
	}

	// OS42: the filtered list and the full list agree line by line (a filtered line shows the same values as in the full list)
	@Test
	public void OS42_filter_shows_the_same_values() {
		test = extent.createTest("OS42 A filter shows the same values");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> all = wide(o);
		String status = val(all.get(0), "BILLING STATUS").trim();
		o.Search_report("", "", "", "", "", status, FROM_WIDE, today());
		List<Map<String, String>> found = o.all_records();
		test.info("Status " + status + ": " + found.size() + " lines of " + all.size());
		List<String> bad = not_in(found, all);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: a filtered line shows other values than in the full list (amounts / quantities differ): " + shorten(bad));
		test.pass("Filtered values equal the full list");
	}

	// OS43: the same search twice gives the same lines
	@Test
	public void OS43_search_twice() {
		test = extent.createTest("OS43 Search twice");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> first = wide(o);
		o.Search_dates(FROM_WIDE, today());
		List<Map<String, String>> second = o.all_records();
		test.info("First " + first.size() + " lines, second " + second.size() + " lines");
		Assert.assertEquals(second, first, "DEFECT: the same search gives different lines the second time");
		test.pass("The same search gives the same lines");
	}

	// ======================= export =======================

	// OS44: Export does not break the page (the file itself is not opened: the browser download is only started)
	@Test
	public void OS44_export() {
		test = extent.createTest("OS44 Export");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		wide_first_page(o);
		Assert.assertTrue(o.verify_export().isEnabled(), "The Export button is disabled");
		int tabs_before = driver.getWindowHandles().size();
		o.Click_export();
		int tabs_after = driver.getWindowHandles().size();
		String notice = "";
		try {
			notice = o.notice();
		} catch (Exception e) {
			// no notice
		}
		test.info("After Export: tabs " + tabs_before + " -> " + tabs_after + " | notice '" + notice + "' | messages " + o.validation_messages());
		clean_up();
		Assert.assertTrue(o.is_list_page(), "The report is not shown after Export");
		Assert.assertFalse(notice.toLowerCase().contains("error") || notice.toLowerCase().contains("fail")
				|| notice.toLowerCase().contains("exception"), "DEFECT: Export shows an error: " + notice);
		test.pass("Export runs without an error");
	}

	// OS45: Export on an empty result does not break the page
	@Test
	public void OS45_export_empty_result() {
		test = extent.createTest("OS45 Export with no data");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		o.Search_dates(LocalDate.now().plusDays(60).format(DATE), LocalDate.now().plusDays(90).format(DATE));
		Assert.assertEquals(o.result_count(), 0, "The future range lists lines");
		o.Click_export();
		String notice = o.notice();
		test.info("Export of an empty list: notice '" + notice + "' | messages " + o.validation_messages());
		clean_up();
		Assert.assertTrue(o.is_list_page(), "The report is not shown after Export");
		Assert.assertFalse(notice.toLowerCase().contains("exception") || notice.toLowerCase().contains("error"), "DEFECT: Export of an empty list shows an error: " + notice);
		test.pass("Export of an empty list does not break the page");
	}

	// OS46: opening the search form and closing it again does not change the list
	@Test
	public void OS46_list_unchanged_by_the_form() {
		test = extent.createTest("OS46 List unchanged by the search form");
		Y_OrderToSalesDifference_Cls o = new Y_OrderToSalesDifference_Cls(driver);

		List<Map<String, String>> before = wide_first_page(o);
		String entries = o.entries_text();
		o.Open_search_form();
		o.Click_clear();
		o.Search_dates(FROM_WIDE, today());
		List<Map<String, String>> after = o.records();
		test.info("Before: " + entries + " | after: " + o.entries_text());
		Assert.assertEquals(after, before, "DEFECT: the list changed after Clear and the same search");
		test.pass("The list is unchanged");
	}
}
