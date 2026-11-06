package TestPkg;

import UtillPkg.TestEnv;
import UtillPkg.WaitUtil;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
import PagePkg.V_OffTakeReport_Cls;

/*
 * Off Take Report (Field Management > OffTakeReport), logged in as the web user - tests OT01 .. OT32.
 * A read only report: nothing is created or changed. It lists salesman / date / customer / number of items;
 * the eye icon opens "Item Details", the picture icon opens the "Off-Take Photos" viewer.
 */
public class V_OffTakeReport_Test extends BaseCls {

	// login of the web user (a test login of this project)
	static final String WEB_USER = TestEnv.get("web.user", "your-web-user");
	static final String WEB_PASS = TestEnv.get("web.password", "your-web-password");

	static final String FROM_WIDE = "01/10/2026";
	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
		try {
			new V_OffTakeReport_Cls(driver).Close_modal();
		} catch (Exception e) {
			// no pop-up
		}
	}

	// opens Field Management > OffTakeReport from the dashboard
	private void go_to_report() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_containing("Field Management", "OffTake");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Field Management", "Off Take");
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

	// every test starts with a fresh Off Take Report screen
	@BeforeMethod
	public void open_report() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		clean_up();
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);
		try {
			go_to_report();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_report();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		}
		remember_clean_screen();
	}

	// ---------------------------------- helpers ----------------------------------

	private void search_wide(V_OffTakeReport_Cls o) {
		o.Search_report("", "", FROM_WIDE, today());
	}

	private LocalDate date_of(List<String> row) {
		return LocalDate.parse(row.get(1).trim(), DATE);
	}

	// the first listed row of the wide search; skips the test when there is none
	private List<String> first_row(V_OffTakeReport_Cls o) {
		search_wide(o);
		if (o.result_count() == 0) {
			throw new SkipException("No off take is listed for " + FROM_WIDE + " .. " + today());
		}
		return o.result_texts().get(0);
	}

	// the drop down option that equals the customer of a list row ("" when the customer is not in the drop down)
	private String customer_option_of(V_OffTakeReport_Cls o, List<String> row) {
		for (String option : o.customer_options()) {
			if (option.equalsIgnoreCase(row.get(2).trim())) {
				return option;
			}
		}
		return "";
	}

	private int items_of(List<String> row) {
		return (int) V_OffTakeReport_Cls.to_number(row.get(3));
	}

	// ======================= screen and search form =======================

	// OT01: the screen shows Search, the table filter, "Show N entries", the 5 column headers and the entries line
	@Test
	public void OT01_screen_elements() {
		test = extent.createTest("OT01 Off Take Report screen elements");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		Assert.assertTrue(o.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(o.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		Assert.assertTrue(o.verify_page_length().isDisplayed(), "'Show N entries' is missing");
		List<String> headers = o.headers();
		test.info("Headers: " + headers + " | " + o.entries_text());
		for (String h : new String[] { "SALESMAN", "DATE", "CUSTOMER", "NO. OF ITEMS", "ACTION" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(o.entries_text().contains("Showing"), "Entries line: " + o.entries_text());
		test.pass("Screen elements are displayed");
	}

	// OT02: the Search button opens the form with Salesman, Customer, From Date, To Date, Search and Clear
	@Test
	public void OT02_search_form_opens() {
		test = extent.createTest("OT02 Search form opens");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		o.Open_search_form();
		Assert.assertTrue(o.verify_salesman().isDisplayed(), "Salesman is missing");
		Assert.assertTrue(o.verify_customer().isDisplayed(), "Customer is missing");
		Assert.assertTrue(o.verify_from().isDisplayed(), "From Date is missing");
		Assert.assertTrue(o.verify_to().isDisplayed(), "To Date is missing");
		Assert.assertTrue(o.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertTrue(o.verify_clear().isDisplayed(), "The Clear button is missing");
		test.info("Salesman placeholder '" + o.verify_salesman().getAttribute("placeholder") + "' | Customer '"
				+ o.selected(o.verify_customer()) + "'");
		Assert.assertEquals(o.verify_from().getAttribute("value"), today(), "Default From Date");
		Assert.assertEquals(o.verify_to().getAttribute("value"), today(), "Default To Date");
		test.pass("Search form is displayed with its defaults");
	}

	// OT03: the customer drop down values are written to the report
	@Test
	public void OT03_customer_options() {
		test = extent.createTest("OT03 Customer options");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		o.Open_search_form();
		List<String> customers = o.options_of(o.verify_customer());
		test.info("Customers (" + customers.size() + "): " + (customers.size() > 15 ? customers.subList(0, 15) + " ..." : customers));
		Assert.assertFalse(customers.isEmpty(), "The Customer drop down has no value");
		test.pass("Customer drop down is filled");
	}

	// OT04: the default list shows only the off takes of today
	@Test
	public void OT04_default_list_is_today() {
		test = extent.createTest("OT04 Default list");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		test.info(o.entries_text() + " | " + o.result_count() + " rows");
		if (o.result_count() == 0) {
			throw new SkipException("No off take of today");
		}
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(1), today(), "An off take of another day is listed: " + row);
		}
		test.pass("Only today's off takes are listed");
	}

	// OT05: a wide date range lists off takes only inside the range
	@Test
	public void OT05_search_wide_date_range() {
		test = extent.createTest("OT05 Search a wide date range");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		search_wide(o);
		test.info(FROM_WIDE + " .. " + today() + ": " + o.entries_text());
		LocalDate from = LocalDate.parse(FROM_WIDE, DATE);
		LocalDate to = LocalDate.now();
		for (List<String> row : o.result_texts()) {
			LocalDate d = date_of(row);
			Assert.assertTrue(!d.isBefore(from) && !d.isAfter(to), "DEFECT: an off take outside the date range: " + row);
		}
		Assert.assertTrue(o.entries_total() >= o.result_count(), "The entries line is smaller than the rows shown");
		if (o.result_count() == 0) {
			Assert.assertTrue(o.no_data_shown(), "No rows and no 'No Data' line");
		}
		test.pass("Wide range works");
	}

	// OT06: one single day lists only that day
	@Test
	public void OT06_search_single_day() {
		test = extent.createTest("OT06 Search a single day");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		String day = first_row(o).get(1);
		o.Search_report("", "", day, day);
		test.info("Day " + day + " .. " + day + ": " + o.entries_text());
		if (o.result_count() == 0) {
			String next_day = LocalDate.parse(day, DATE).plusDays(1).format(DATE);
			o.Search_report("", "", day, next_day);
			test.info("Day " + day + " .. " + next_day + ": rows " + o.result_count() + " (the To Date may not include its own day)");
			Assert.fail("DEFECT: From Date = To Date = " + day + " finds no off take although one exists on that day"
					+ (o.result_count() > 0 ? " (it is found when To Date is the next day: the To Date day is not included)" : ""));
		}
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(1), day, "An off take of another day: " + row);
		}
		test.pass("Only " + day + " is listed");
	}

	// OT07: From Date after To Date: nothing (or a message) must be shown
	@Test
	public void OT07_reversed_date_range() {
		test = extent.createTest("OT07 Reversed date range");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		o.Search_report("", "", "20/10/2026", "01/10/2026");
		test.info("Rows: " + o.result_count() + " | messages: " + o.validation_messages() + " | notice: " + o.notice());
		Assert.assertTrue(o.result_count() == 0 || !o.validation_messages().isEmpty() || !o.notice().isEmpty(),
				"DEFECT: a reversed date range lists off takes without any message");
		test.pass("Reversed range gives nothing or a message");
	}

	// OT08: a range in the future has no off take
	@Test
	public void OT08_future_dates() {
		test = extent.createTest("OT08 Future date range");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		String from = LocalDate.now().plusDays(60).format(DATE);
		String to = LocalDate.now().plusDays(90).format(DATE);
		o.Search_report("", "", from, to);
		test.info(from + " .. " + to + ": rows " + o.result_count() + " | " + o.entries_text());
		Assert.assertEquals(o.result_count(), 0, "Off takes are listed for a future range");
		Assert.assertTrue(o.no_data_shown() || o.entries_total() == 0, "The table shows no 'No Data' line");
		test.pass("A future range lists nothing");
	}

	// OT09: invalid dates are refused
	@Test
	public void OT09_invalid_dates() {
		test = extent.createTest("OT09 Invalid dates");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (String value : new String[] { "abcd", "31/02/2026", "99/99/9999" }) {
			o.Search_report("", "", value, today());
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
		Assert.assertTrue(bad.isEmpty(), "DEFECT: invalid From Date values were accepted and off takes listed: " + bad);
		test.pass("Invalid dates are not accepted");
	}

	// OT10: the salesman search lists only that salesman
	@Test
	public void OT10_search_by_salesman() {
		test = extent.createTest("OT10 Search by salesman");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		String salesman = first_row(o).get(0);
		test.info("Salesman used: " + salesman);
		o.Search_report(salesman, "", FROM_WIDE, today());
		Assert.assertTrue(o.result_count() > 0, "No off take was found for " + salesman);
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(0), salesman, "An off take of another salesman: " + row);
		}
		test.pass("Only " + salesman + " is listed");
	}

	// OT11: the salesman box suggests salesmen; text that matches nothing gives nothing
	@Test
	public void OT11_salesman_suggestions() {
		test = extent.createTest("OT11 Salesman suggestions");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		String salesman = first_row(o).get(0);
		o.Open_search_form();
		List<String> found = new ArrayList<String>();
		String used = "";
		for (int len : new int[] { 2, 3, salesman.length() }) {
			String start = salesman.substring(0, Math.min(len, salesman.length()));
			found = o.Suggestions_for(o.verify_salesman(), start);
			test.info("Suggestions for '" + start + "': " + found);
			if (!found.isEmpty()) {
				used = start;
				break;
			}
		}
		if (found.isEmpty()) {
			// the search by a typed salesman works (OT10), so the list is probably a browser list the script cannot read
			throw new SkipException("The salesman suggestions cannot be read (a browser list?) for '" + salesman + "'");
		}
		if (used.length() > 2) {
			test.warning("OBSERVATION: the salesman box suggests only from " + used.length() + " letters (2 gave nothing)");
		}
		List<String> none = o.Suggestions_for(o.verify_salesman(), "ZZQNoSuch" + unique());
		Assert.assertTrue(none.isEmpty(), "A salesman is suggested for text that matches nothing: " + none);
		test.pass("Salesman suggestions work");
	}

	// OT12: a salesman that does not exist is not ignored
	@Test
	public void OT12_unknown_salesman() {
		test = extent.createTest("OT12 Unknown salesman");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		search_wide(o);
		int all = o.entries_total();
		o.Search_report("ZZQNoSuchSalesman" + unique(), "", FROM_WIDE, today());
		test.info("All " + all + " | now " + o.entries_text() + " | messages " + o.validation_messages() + " " + o.notice());
		if (all == 0) {
			throw new SkipException("No off take to compare with");
		}
		Assert.assertTrue(o.result_count() == 0 || o.entries_total() < all || !o.validation_messages().isEmpty()
				|| !o.notice().isEmpty(),
				"DEFECT: a salesman that does not exist is ignored and all " + all + " off takes are listed without a message");
		test.pass("An unknown salesman does not list every off take");
	}

	// OT13: the customer filter lists only that customer
	@Test
	public void OT13_search_by_customer() {
		test = extent.createTest("OT13 Search by customer");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		List<String> row0 = first_row(o);
		String customer = customer_option_of(o, row0);
		test.info("Customer of the first off take: '" + row0.get(2) + "' | in the drop down: '" + customer + "'");
		Assert.assertFalse(customer.isEmpty(), "DEFECT: the customer '" + row0.get(2)
				+ "' of an existing off take is not in the Customer drop down, so it cannot be filtered");
		o.Search_report("", customer, FROM_WIDE, today());
		Assert.assertTrue(o.result_count() > 0, "No off take was found for " + customer);
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(2), customer, "An off take of another customer: " + row);
		}
		test.pass("Only " + customer + " is listed");
	}

	// OT14: every customer of the drop down (first 5): the rows of that customer only
	@Test
	public void OT14_every_customer_filter() {
		test = extent.createTest("OT14 Customer filter for several customers");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		o.Open_search_form();
		List<String> customers = o.options_of(o.verify_customer());
		List<String> bad = new ArrayList<String>();
		int tried = 0;
		for (String customer : customers) {
			if (tried++ >= 5) {
				break;
			}
			o.Search_report("", customer, FROM_WIDE, today());
			test.info(customer + ": " + o.entries_text());
			for (List<String> row : o.result_texts()) {
				if (!row.get(2).equalsIgnoreCase(customer)) {
					bad.add(customer + " -> " + row);
				}
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: rows of another customer are listed: " + bad);
		test.pass("Customer filter is correct");
	}

	// OT15: salesman + customer + one day together
	@Test
	public void OT15_combined_filters() {
		test = extent.createTest("OT15 Combined filters");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		List<String> row0 = first_row(o);
		String customer = customer_option_of(o, row0);
		if (customer.isEmpty()) {
			test.warning("The customer '" + row0.get(2) + "' is not in the Customer drop down: the customer is left out of the filters");
		}
		String next_day = java.time.LocalDate.parse(row0.get(1), DATE).plusDays(1).format(DATE);
		o.Search_report(row0.get(0), customer, row0.get(1), next_day);
		test.info("Filters: " + row0.get(0) + " | " + customer + " | " + row0.get(1) + " .. " + next_day + " -> " + o.entries_text());
		Assert.assertTrue(o.result_count() > 0, "The off take is not found with its own filters");
		boolean found = false;
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(0), row0.get(0), "Another salesman: " + row);
			if (!customer.isEmpty()) {
				Assert.assertEquals(row.get(2), row0.get(2), "Another customer: " + row);
			}
			if (row.equals(row0)) {
				found = true;
			}
		}
		Assert.assertTrue(found, "The off take " + row0 + " is not in its own filter result");
		test.pass("Combined filters work");
	}

	// OT16: Clear empties the filters
	@Test
	public void OT16_clear_button() {
		test = extent.createTest("OT16 Clear");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		List<String> row0 = first_row(o);
		String customer = customer_option_of(o, row0);
		o.Search_report(row0.get(0), customer, FROM_WIDE, today());
		o.Click_clear();
		o.Open_search_form();
		test.info("After Clear: salesman '" + o.verify_salesman().getAttribute("value") + "' | customer '"
				+ o.selected(o.verify_customer()) + "' | from " + o.verify_from().getAttribute("value") + " | to "
				+ o.verify_to().getAttribute("value") + " | " + o.entries_text());
		Assert.assertEquals(o.verify_salesman().getAttribute("value"), "", "Salesman was not cleared");
		Assert.assertFalse(!customer.isEmpty() && o.selected(o.verify_customer()).equalsIgnoreCase(customer), "Customer was not cleared");
		test.pass("Clear empties the filters");
	}

	// ======================= table filter, sorting, paging =======================

	// OT17: the table filter finds by customer; nothing matches -> 'No Data'; clearing brings the rows back
	@Test
	public void OT17_table_filter() {
		test = extent.createTest("OT17 Table filter");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		String customer = first_row(o).get(2);
		o.Filter_table(customer);
		test.info("Filter '" + customer + "': " + o.entries_text());
		Assert.assertTrue(o.result_count() > 0, "The filter found nothing for " + customer);
		for (List<String> row : o.result_texts()) {
			Assert.assertTrue(row.get(2).equalsIgnoreCase(customer), "The filter lists another customer: " + row);
		}
		o.Filter_table("ZZQNothing" + unique());
		Assert.assertEquals(o.result_count(), 0, "Rows are listed for text that matches nothing");
		Assert.assertTrue(o.no_data_shown(), "No 'No Data' line is shown");
		o.Filter_table("");
		Assert.assertTrue(o.result_count() > 0, "The rows did not come back after clearing the filter");
		test.pass("Table filter works");
	}

	// OT18: the table filter finds by salesman and by date; special text does no harm
	@Test
	public void OT18_table_filter_salesman_date_and_special_text() {
		test = extent.createTest("OT18 Table filter salesman, date, special text");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		List<String> row0 = first_row(o);
		o.Filter_table(row0.get(0));
		test.info("Filter by salesman '" + row0.get(0) + "': rows " + o.result_count());
		if (o.result_count() == 0) {
			String first_word = row0.get(0).split(" ")[0];
			o.Filter_table(first_word);
			test.info("Filter by '" + first_word + "': rows " + o.result_count());
			Assert.assertTrue(o.result_count() > 0, "DEFECT: the table filter finds neither '" + row0.get(0) + "' nor '" + first_word + "' in the Salesman column");
			test.warning("OBSERVATION: the table filter does not find the full salesman name '" + row0.get(0) + "' but finds its first word");
		}
		o.Filter_table(row0.get(1));
		test.info("Filter by date '" + row0.get(1) + "': " + o.entries_text());
		for (List<String> row : o.result_texts()) {
			Assert.assertTrue(row.toString().contains(row0.get(1)), "Row without the date: " + row);
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

	private boolean sorted_text(List<String> values) {
		List<String> up = new ArrayList<String>(values);
		java.util.Collections.sort(up);
		List<String> down = new ArrayList<String>(up);
		java.util.Collections.reverse(down);
		return values.equals(up) || values.equals(down);
	}

	// OT19: Salesman, Customer, Date and No. of Items sort (text columns as text, date by date, items as numbers)
	@Test
	public void OT19_sort_columns() {
		test = extent.createTest("OT19 Sort the columns");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		search_wide(o);
		if (o.result_count() < 2) {
			throw new SkipException("Two rows are needed to check sorting");
		}
		List<String> wrong = new ArrayList<String>();
		for (String[] col : new String[][] { { "SALESMAN", "0" }, { "CUSTOMER", "2" } }) {
			o.Click_header(col[0]);
			List<String> values = new ArrayList<String>();
			for (List<String> row : o.result_texts()) {
				values.add(row.get(Integer.parseInt(col[1])).toLowerCase());
			}
			boolean ok = sorted_text(values);
			test.info(col[0] + ": " + values + " -> " + (ok ? "sorted" : "NOT sorted"));
			if (!ok) {
				wrong.add(col[0]);
			}
		}
		o.Click_header("DATE");
		List<LocalDate> dates = new ArrayList<LocalDate>();
		for (List<String> row : o.result_texts()) {
			dates.add(date_of(row));
		}
		List<LocalDate> up = new ArrayList<LocalDate>(dates);
		java.util.Collections.sort(up);
		List<LocalDate> down = new ArrayList<LocalDate>(up);
		java.util.Collections.reverse(down);
		boolean date_ok = dates.equals(up) || dates.equals(down);
		test.info("DATE: " + dates + " -> " + (date_ok ? "sorted" : "NOT sorted by date"));
		if (!date_ok) {
			wrong.add("DATE");
		}
		o.Click_header("NO. OF ITEMS");
		List<Double> numbers = new ArrayList<Double>();
		for (List<String> row : o.result_texts()) {
			numbers.add(V_OffTakeReport_Cls.to_number(row.get(3)));
		}
		List<Double> nup = new ArrayList<Double>(numbers);
		java.util.Collections.sort(nup);
		List<Double> ndown = new ArrayList<Double>(nup);
		java.util.Collections.reverse(ndown);
		boolean number_ok = numbers.equals(nup) || numbers.equals(ndown);
		test.info("NO. OF ITEMS: " + numbers + " -> " + (number_ok ? "sorted" : "NOT sorted as numbers"));
		if (!number_ok) {
			wrong.add("NO. OF ITEMS");
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: these columns are not sorted correctly: " + wrong);
		test.pass("All columns sort");
	}

	// OT20: Show N entries: the options, and the rows never exceed the chosen number
	@Test
	public void OT20_show_entries() {
		test = extent.createTest("OT20 Show entries");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		search_wide(o);
		List<String> options = o.page_length_options();
		test.info("Options: " + options + " | selected " + o.page_length_selected() + " | " + o.entries_text());
		Assert.assertFalse(options.isEmpty(), "'Show' has no option");
		Assert.assertEquals(o.page_length_selected(), "10", "Default page length");
		for (String option : options) {
			if (!option.matches("\\d+")) {
				continue;
			}
			o.Set_page_length(option);
			int n = Integer.parseInt(option);
			test.info("Show " + n + ": rows " + o.result_count() + " | " + o.entries_text());
			Assert.assertTrue(o.result_count() <= n, "DEFECT: more than " + n + " rows are shown");
			Assert.assertEquals(o.result_count(), Math.min(n, o.entries_total()), "Rows shown for Show " + n);
		}
		test.pass("Show entries works");
	}

	// OT21: paging: Next shows the next rows, Previous comes back
	@Test
	public void OT21_paging() {
		test = extent.createTest("OT21 Paging");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		search_wide(o);
		if (o.entries_total() <= 10) {
			throw new SkipException("Only " + o.entries_total() + " rows: no second page");
		}
		List<List<String>> page1 = o.result_texts();
		Assert.assertFalse(o.previous_enabled(), "Previous is enabled on the first page");
		o.Click_next();
		test.info("Page 2: " + o.entries_text());
		Assert.assertTrue(o.entries_text().contains("Showing 11 to"), "Page 2 text: " + o.entries_text());
		for (List<String> row : o.result_texts()) {
			Assert.assertFalse(page1.contains(row), "A row of page 1 is shown again on page 2: " + row);
		}
		o.Click_previous();
		Assert.assertEquals(o.result_texts(), page1, "Previous does not return to the first page");
		test.pass("Paging works");
	}

	// OT22: formats and content of the listed values
	@Test
	public void OT22_value_formats() {
		test = extent.createTest("OT22 Value formats");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		first_row(o);
		List<String> bad = new ArrayList<String>();
		for (List<String> row : o.result_texts()) {
			for (int c = 0; c < 4; c++) {
				if (row.get(c).isEmpty()) {
					bad.add("empty cell " + (c + 1) + " in " + row);
				}
			}
			if (!row.get(1).matches("\\d{2}/\\d{2}/\\d{4}")) {
				bad.add("date '" + row.get(1) + "'");
			}
			if (!row.get(3).matches("\\d+")) {
				bad.add("no. of items '" + row.get(3) + "' is not a whole number");
			} else if (items_of(row) <= 0) {
				bad.add("an off take with " + row.get(3) + " items: " + row);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT in the list: " + bad);
		test.pass("Formats are correct");
	}

	// ======================= Item Details =======================

	// OT23: the eye icon opens Item Details with its 6 columns
	@Test
	public void OT23_view_details_pop_up() {
		test = extent.createTest("OT23 View Details pop-up");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		List<String> row0 = first_row(o);
		test.info("Icons of the first row: " + o.actions_html(0));
		Assert.assertTrue(o.actions_of(0).contains("view"), "The row has no view icon: " + o.actions_of(0));
		o.Click_view(0);
		Assert.assertTrue(o.item_modal_open(), "The Item Details pop-up did not open");
		List<String> headers = o.item_headers();
		test.info("Item Details headers: " + headers + " | rows " + o.item_rows());
		for (String h : new String[] { "ITEM NAME", "LAST OFFTAKE QTY", "PURCHASE QTY", "SALES RETURN QTY", "CURRENT QTY", "NET SALE QTY" }) {
			Assert.assertTrue(headers.contains(h), "Item Details column missing: " + h);
		}
		Assert.assertFalse(o.item_rows().isEmpty(), "Item Details lists no item for " + row0);
		o.Close_modal();
		Assert.assertFalse(o.modal_open(), "The pop-up did not close");
		test.pass("Item Details opens");
	}

	// OT24: the number of item lines of every listed row equals its No. of Items
	@Test
	public void OT24_item_count_matches_list() {
		test = extent.createTest("OT24 Item count matches the list");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		first_row(o);
		List<List<String>> rows = o.result_texts();
		List<String> bad = new ArrayList<String>();
		for (int i = 0; i < rows.size(); i++) {
			try {
				o.Click_view(i);
				if (!o.item_modal_open()) {
					bad.add("row " + (i + 1) + ": Item Details did not open");
					continue;
				}
				int lines = o.item_rows().size();
				test.info("Row " + (i + 1) + " " + rows.get(i).get(2) + ": list says " + rows.get(i).get(3) + " items, pop-up lists " + lines);
				if (lines != items_of(rows.get(i))) {
					bad.add("row " + (i + 1) + " (" + rows.get(i).get(2) + "): list " + rows.get(i).get(3) + " but pop-up " + lines + " lines");
				}
			} finally {
				o.Close_modal();
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: item counts differ: " + bad);
		test.pass("Item counts match");
	}

	// OT25: the quantities of Item Details are whole, not negative numbers (the formula of Net Sale is written to the report)
	@Test
	public void OT25_item_quantities() {
		test = extent.createTest("OT25 Item quantities");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		first_row(o);
		List<List<String>> rows = o.result_texts();
		List<String> bad = new ArrayList<String>();
		for (int i = 0; i < rows.size(); i++) {
			try {
				o.Click_view(i);
				for (List<String> item : o.item_rows()) {
					test.info("Row " + (i + 1) + " item " + item);
					if (item.size() < 6) {
						bad.add("row " + (i + 1) + ": item line with " + item.size() + " cells");
						continue;
					}
					if (item.get(0).isEmpty()) {
						bad.add("row " + (i + 1) + ": an item with no name");
					}
					for (int c = 1; c < 6; c++) {
						if (!item.get(c).matches("-?[\\d,]+(\\.\\d+)?")) {
							bad.add("row " + (i + 1) + " " + item.get(0) + ": '" + item.get(c) + "' is not a number");
						} else if (V_OffTakeReport_Cls.to_number(item.get(c)) < 0) {
							bad.add("row " + (i + 1) + " " + item.get(0) + ": negative quantity " + item.get(c));
						}
					}
					double last = V_OffTakeReport_Cls.to_number(item.get(1));
					double purchase = V_OffTakeReport_Cls.to_number(item.get(2));
					double ret = V_OffTakeReport_Cls.to_number(item.get(3));
					double current = V_OffTakeReport_Cls.to_number(item.get(4));
					double net = V_OffTakeReport_Cls.to_number(item.get(5));
					double guess = last + purchase - ret - current;
					if (Math.abs(guess - net) > 0.0001) {
						test.warning("Net Sale " + net + " is not Last Offtake + Purchase - Sales Return - Current (" + guess
								+ ") for " + item.get(0) + " (formula to confirm)");
					}
				}
			} finally {
				o.Close_modal();
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT in the item quantities: " + bad);
		test.pass("Quantities are numbers");
	}

	// ======================= Off-Take Photos =======================

	// OT26: the picture icon opens the photo viewer: title, caption with the customer and the date, position, thumbnails
	@Test
	public void OT26_photos_pop_up() {
		test = extent.createTest("OT26 Photos pop-up");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		List<String> row0 = first_row(o);
		Assert.assertTrue(o.actions_of(0).contains("photos"), "The row has no photo icon: " + o.actions_of(0) + " | " + o.actions_html(0));
		o.Click_photos(0);
		Assert.assertTrue(o.photo_modal_open(), "The Off-Take Photos pop-up did not open");
		int[] pos = o.photo_position();
		test.info("Pop-up: " + o.photo_modal_text() + " | caption '" + o.photo_caption() + "' | position " + pos[0] + "/" + pos[1]
				+ " | thumbnails " + o.photo_thumb_count());
		Assert.assertTrue(o.photo_modal_text().contains("Off-Take Photos"), "The pop-up has no title");
		Assert.assertEquals(o.photo_caption(), row0.get(2) + " (" + row0.get(1) + ")", "DEFECT: the caption is not 'customer (date)' of the row");
		Assert.assertEquals(pos[0], 1, "The viewer does not start at photo 1");
		Assert.assertTrue(pos[1] >= 1, "The photo count is not shown");
		Assert.assertEquals(o.photo_thumb_count(), pos[1], "DEFECT: the number of thumbnails differs from the photo count");
		test.info("Photo icon title: " + o.photo_icon_title(0));
		Assert.assertTrue(o.photo_icon_title(0).contains("(" + pos[1] + ")"), "DEFECT: the photo count of the icon title '" + o.photo_icon_title(0)
				+ "' differs from the viewer count " + pos[1]);
		o.Close_modal();
		test.pass("Photo viewer opens");
	}

	// OT27: next / previous arrows and thumbnails move the position
	@Test
	public void OT27_photo_navigation() {
		test = extent.createTest("OT27 Photo navigation");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		first_row(o);
		o.Click_photos(0);
		int total = o.photo_position()[1];
		if (total < 2) {
			o.Close_modal();
			throw new SkipException("The viewer has only " + total + " photo");
		}
		List<String> bad = new ArrayList<String>();
		o.Photo_next();
		test.info("After Next: " + o.photo_position()[0] + "/" + total);
		if (o.photo_position()[0] != 2) {
			bad.add("Next gives position " + o.photo_position()[0] + " instead of 2");
		}
		o.Photo_previous();
		if (o.photo_position()[0] != 1) {
			bad.add("Previous gives position " + o.photo_position()[0] + " instead of 1");
		}
		o.Click_thumb(total - 1);
		test.info("After the last thumbnail: " + o.photo_position()[0] + "/" + total);
		if (o.photo_position()[0] != total) {
			bad.add("The last thumbnail gives position " + o.photo_position()[0] + " instead of " + total);
		}
		o.Photo_next();
		test.info("Next on the last photo: " + o.photo_position()[0] + "/" + total + " (wraps to 1 or stays)");
		int after = o.photo_position()[0];
		if (after != 1 && after != total) {
			bad.add("Next on the last photo gives position " + after);
		}
		o.Click_thumb(0);
		o.Photo_previous();
		test.info("Previous on the first photo: " + o.photo_position()[0] + "/" + total + " (wraps to the last or stays)");
		int before = o.photo_position()[0];
		if (before != 1 && before != total) {
			bad.add("Previous on the first photo gives position " + before);
		}
		o.Close_modal();
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Photo navigation works");
	}

	// OT28: zoom in / zoom out / rotate / fit change the big picture
	@Test
	public void OT28_photo_controls() {
		test = extent.createTest("OT28 Photo controls");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		first_row(o);
		o.Click_photos(0);
		if (!o.photo_has_control("zoom-in", "search-plus", "magnify-plus", "plus")) {
			o.Close_modal();
			throw new SkipException("The zoom in control was not found");
		}
		List<String> bad = new ArrayList<String>();
		double w0 = o.main_image_width();
		String t0 = o.main_image_transform();
		o.Click_photo_control("zoom-in", "search-plus", "magnify-plus", "plus");
		double w1 = o.main_image_width();
		String t1 = o.main_image_transform();
		test.info("Zoom in: width " + w0 + " -> " + w1 + " | transform " + t0 + " -> " + t1);
		if (w1 <= w0 + 0.5 && t1.equals(t0)) {
			bad.add("Zoom in changes nothing");
		}
		if (o.photo_has_control("zoom-out", "search-minus", "magnify-minus", "minus")) {
			o.Click_photo_control("zoom-out", "search-minus", "magnify-minus", "minus");
			o.Click_photo_control("zoom-out", "search-minus", "magnify-minus", "minus");
			double w2 = o.main_image_width();
			test.info("Zoom out twice: width " + w2);
			if (w2 >= w1 - 0.5 && o.main_image_transform().equals(t1)) {
				bad.add("Zoom out changes nothing");
			}
		}
		if (o.photo_has_control("rotate", "redo", "undo", "sync", "repeat")) {
			String before = o.main_image_transform();
			o.Click_photo_control("rotate", "redo", "undo", "sync", "repeat");
			String after = o.main_image_transform();
			test.info("Rotate: transform " + before + " -> " + after);
			if (after.equals(before)) {
				bad.add("Rotate changes nothing");
			}
		}
		if (o.photo_has_control("expand", "arrows", "fit", "reset", "compress", "maximize")) {
			o.Click_photo_control("expand", "arrows", "fit", "reset", "compress", "maximize");
			test.info("Fit: width " + o.main_image_width() + " | transform " + o.main_image_transform());
		}
		o.Close_modal();
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Photo controls work");
	}

	// OT29: the photos load (a broken picture shows the 'No image available' placeholder: written to the report)
	@Test
	public void OT29_photos_load() {
		test = extent.createTest("OT29 Photos load");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		first_row(o);
		List<List<String>> rows = o.result_texts();
		List<String> placeholders = new ArrayList<String>();
		for (int i = 0; i < rows.size(); i++) {
			try {
				o.Click_photos(i);
				if (!o.photo_modal_open()) {
					placeholders.add("row " + (i + 1) + ": the viewer did not open");
					continue;
				}
				int total = Math.max(1, o.photo_position()[1]);
				for (int p = 0; p < total; p++) {
					if (p > 0) {
						o.Click_thumb(p);
					}
					String src = o.main_image_src();
					boolean loaded = o.main_image_loaded();
					test.info("Row " + (i + 1) + " photo " + (p + 1) + "/" + total + ": " + (src.length() > 90 ? src.substring(0, 90) + "..." : src) + " | loaded " + loaded);
					if (!loaded || src.toLowerCase().contains("noimage") || src.toLowerCase().contains("no-image") || src.toLowerCase().contains("placeholder")) {
						placeholders.add("row " + (i + 1) + " photo " + (p + 1));
					}
				}
			} finally {
				o.Close_modal();
			}
		}
		if (!placeholders.isEmpty()) {
			test.warning("OBSERVATION: these photos show no real picture (placeholder / not loaded): " + placeholders);
		}
		Assert.assertTrue(o.is_list_page(), "The page broke");
		test.pass("Photos checked");
	}

	// OT30: closing a pop-up and opening the next row shows that row (nothing is kept), for both pop-ups
	@Test
	public void OT30_pop_ups_show_their_own_row() {
		test = extent.createTest("OT30 Pop-ups show their own row");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		first_row(o);
		List<List<String>> rows = o.result_texts();
		if (rows.size() < 2) {
			throw new SkipException("Two rows are needed");
		}
		o.Click_photos(0);
		String caption0 = o.photo_caption();
		o.Close_modal();
		o.Click_photos(1);
		String caption1 = o.photo_caption();
		test.info("Captions: '" + caption0 + "' then '" + caption1 + "'");
		Assert.assertEquals(caption1, rows.get(1).get(2) + " (" + rows.get(1).get(1) + ")", "DEFECT: the second photo viewer shows another row");
		Assert.assertEquals(o.photo_position()[0], 1, "The second viewer did not start at photo 1");
		o.Close_modal();

		o.Click_view(0);
		List<List<String>> items0 = o.item_rows();
		o.Close_modal();
		o.Click_view(1);
		List<List<String>> items1 = o.item_rows();
		o.Close_modal();
		test.info("Items of row 1: " + items0 + " | items of row 2: " + items1);
		Assert.assertEquals(items1.size(), items_of(rows.get(1)), "DEFECT: Item Details of row 2 lists another number of items");
		test.pass("Each pop-up shows its own row");
	}

	// OT31: the pop-ups close with x and with Escape
	@Test
	public void OT31_pop_ups_close() {
		test = extent.createTest("OT31 Pop-ups close");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		first_row(o);
		o.Click_view(0);
		Assert.assertTrue(o.item_modal_open(), "Item Details did not open");
		o.Close_modal();
		Assert.assertFalse(o.modal_open(), "Item Details did not close with x");
		o.Click_photos(0);
		Assert.assertTrue(o.photo_modal_open(), "The photo viewer did not open");
		o.Close_modal_with_escape();
		test.info("After Escape: pop-up open = " + o.modal_open());
		if (o.modal_open()) {
			test.warning("OBSERVATION: the photo viewer does not close with the Escape key");
			o.Close_modal();
		}
		Assert.assertFalse(o.modal_open(), "The pop-up did not close");
		Assert.assertTrue(o.is_list_page(), "The list is not shown after closing");
		test.pass("Pop-ups close");
	}

	// OT32: search results survive opening and closing a pop-up (the filters and rows stay)
	@Test
	public void OT32_list_unchanged_after_pop_up() {
		test = extent.createTest("OT32 List unchanged after a pop-up");
		V_OffTakeReport_Cls o = new V_OffTakeReport_Cls(driver);

		first_row(o);
		List<List<String>> before = o.result_texts();
		String entries = o.entries_text();
		o.Click_view(0);
		o.Close_modal();
		o.Click_photos(0);
		o.Close_modal();
		test.info("Before: " + entries + " | after: " + o.entries_text());
		Assert.assertEquals(o.result_texts(), before, "DEFECT: the list changed after opening and closing the pop-ups");
		Assert.assertEquals(o.entries_text(), entries, "The entries line changed");
		test.pass("The list is unchanged");
	}
}
