package TestPkg;

import UtillPkg.WaitUtil;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import PagePkg.P_Collections_Cls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;

/*
 * Collections (Field Management > Collections) - functional tests, CL01 .. CL22.
 * A report screen with a details pop-up. Approve All, the row Approve tick box and the red x CHANGE collection records
 * and cannot be undone: CL19 .. CL22 run only when ALLOW_STATE_CHANGES is true (it is false here).
 */
public class P_Collections_Test extends BaseCls {

	// true = the tests that approve or reject collections are run (use a QA copy of the data only)
	static final boolean ALLOW_STATE_CHANGES = false;

	// a wide date range that covers the QA collections
	static final String FROM_WIDE = "01/09/2026";
	static final String TO_WIDE = "26/10/2026";

	static final String EMPLOYEE = "Santhosh";

	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	// the hierarchy type that lists collections (found on the first search)
	String type1 = "";

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

	// opens Field Management > Collections from the dashboard (waits and tries again while the page is still loading)
	private void go_to_collections() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_by_name("Field Management", "Collections");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Field Management", "Collections");
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

	// every test starts with a fresh Collections screen
	@BeforeMethod
	public void open_collections() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		P_Collections_Cls c = new P_Collections_Cls(driver);
		try {
			go_to_collections();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> c.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_collections();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> c.is_list_page());
		}
		remember_clean_screen();
	}

	// the total of "Showing 1 to 10 of 47 entries" (-1 when it cannot be read)
	private int total_entries(P_Collections_Cls c) {
		String t = c.verify_entries().getText().replace(",", "");
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("of (\\d+) entries").matcher(t);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	// searches the wide date range with the hierarchy type that has collections (the first one that lists rows)
	private void search_wide(P_Collections_Cls c, String employee, String customer, String mode, String status) {
		if (type1.isEmpty()) {
			c.Open_search_form();
			for (String type : c.all_options_of(c.verify_type())) {
				c.Search_collections(type, "", "", "", "", "", FROM_WIDE, TO_WIDE);
				if (c.result_count() > 0) {
					type1 = type;
					break;
				}
			}
			Assert.assertFalse(type1.isEmpty(), "No hierarchy type lists collections for " + FROM_WIDE + " .. " + TO_WIDE);
		}
		c.Search_collections(type1, "", employee, customer, mode, status, FROM_WIDE, TO_WIDE);
	}

	private BigDecimal number(String text) {
		return new BigDecimal(text.replace(",", "").replace("₹", "").trim());
	}

	// ======================= screen and search form =======================

	// CL01: the screen shows Search, Approve All, the table filter, the 11 column headers and, before a search,
	// "No Data Available" (the dates start on today)
	@Test
	public void CL01_screen_elements() {
		test = extent.createTest("CL01 Collections screen elements");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		Assert.assertTrue(c.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(c.approve_all_box() != null, "Approve All is missing");
		Assert.assertTrue(c.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = c.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "SUPPLY BY", "EMPLOYEE", "CUSTOMER NAME", "RECEIPT NUMBER", "DATE & TIME",
				"RECEIPT MODE", "RECEIPT TYPE", "DETAILS", "AMOUNT (₹)", "RECEIPT STATUS", "ACTION" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		test.info("Before a search: " + c.verify_entries().getText() + " | no data shown: " + c.no_data_shown());
		test.pass("Screen elements are displayed");
	}

	// CL02: the Search button opens the search form with its 8 fields, Clear Filter and Search; the defaults are
	// All for the receipt mode and status and today for both dates
	@Test
	public void CL02_search_form_opens() {
		test = extent.createTest("CL02 Search form opens");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		c.Open_search_form();
		Assert.assertTrue(c.verify_type().isDisplayed(), "Hierarchy Type is missing");
		Assert.assertTrue(c.verify_hierarchy().isDisplayed(), "Hierarchy Name is missing");
		Assert.assertTrue(c.verify_employee().isDisplayed(), "Employee is missing");
		Assert.assertTrue(c.verify_customer().isDisplayed(), "Customer is missing");
		Assert.assertTrue(c.verify_mode().isDisplayed(), "Receipt Mode is missing");
		Assert.assertTrue(c.verify_status().isDisplayed(), "Receipt Status is missing");
		Assert.assertTrue(c.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertTrue(c.verify_clear_filter().isDisplayed(), "Clear Filter is missing");
		Assert.assertEquals(c.selected(c.verify_mode()), "All", "Default receipt mode");
		Assert.assertEquals(c.selected(c.verify_status()), "All", "Default receipt status");
		String today = LocalDate.now().format(DATE);
		Assert.assertEquals(c.verify_from().getAttribute("value"), today, "Default From Date");
		Assert.assertEquals(c.verify_to().getAttribute("value"), today, "Default To Date");
		test.info("Default hierarchy type: " + c.selected(c.verify_type()));
		test.pass("Search form is displayed with its defaults");
	}

	// CL03: the drop downs have values (written to the report)
	@Test
	public void CL03_drop_down_options() {
		test = extent.createTest("CL03 Drop down options");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		c.Open_search_form();
		List<String> types = c.all_options_of(c.verify_type());
		List<String> modes = c.all_options_of(c.verify_mode());
		c.Open_search_form();
		List<String> statuses = c.all_options_of(c.verify_status());
		test.info("Hierarchy types: " + types);
		test.info("Receipt modes: " + modes);
		test.info("Receipt statuses: " + statuses);
		Assert.assertFalse(types.isEmpty(), "Hierarchy Type has no values");
		for (String mode : new String[] { "All", "Cash", "Upi", "Cheque" }) {
			Assert.assertTrue(modes.contains(mode), "The receipt mode " + mode + " is missing: " + modes);
		}
		Assert.assertTrue(statuses.contains("All") && statuses.contains("Pending"), "Receipt statuses: " + statuses);
		test.pass("Drop downs are filled");
	}

	// CL04: a wide date range lists the collections (the entries line and the first rows are written to the report)
	@Test
	public void CL04_search_wide_date_range() {
		test = extent.createTest("CL04 Search a wide date range");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "");
		test.info("Hierarchy type used: " + type1 + " | " + c.verify_entries().getText());
		Assert.assertTrue(c.result_count() > 0, "No collection was listed");
		Assert.assertTrue(c.verify_entries().getText().contains("Showing 1 to"), c.verify_entries().getText());
		test.info("First row: " + c.result_texts().get(0));
		test.pass(total_entries(c) + " collections listed");
	}

	// CL05: every receipt mode lists only that mode
	@Test
	public void CL05_search_by_receipt_mode() {
		test = extent.createTest("CL05 Search by receipt mode");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		List<String> wrong = new ArrayList<String>();
		for (String mode : new String[] { "Cash", "Upi", "Cheque" }) {
			search_wide(c, "", "", mode, "");
			test.info("Mode " + mode + ": " + c.result_count() + " rows on the first page (" + c.verify_entries().getText() + ")");
			for (List<String> row : c.result_texts()) {
				if (!row.get(5).equalsIgnoreCase(mode)) {
					wrong.add(mode + ": " + row.get(3) + " is " + row.get(5));
				}
			}
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: rows of another receipt mode are listed: " + wrong);
		test.pass("Every receipt mode lists only its own rows");
	}

	// CL06: every receipt status lists only that status
	@Test
	public void CL06_search_by_receipt_status() {
		test = extent.createTest("CL06 Search by receipt status");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "");
		List<String> statuses = c.all_options_of(c.verify_status());
		List<String> wrong = new ArrayList<String>();
		for (String status : statuses) {
			if (status.equalsIgnoreCase("All")) {
				continue;
			}
			search_wide(c, "", "", "", status);
			test.info("Status " + status + ": " + c.result_count() + " rows on the first page (" + c.verify_entries().getText() + ")");
			for (List<String> row : c.result_texts()) {
				if (!row.get(9).equalsIgnoreCase(status)) {
					wrong.add(status + ": " + row.get(3) + " is " + row.get(9));
				}
			}
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: rows of another status are listed: " + wrong);
		test.pass("Every receipt status lists only its own rows");
	}

	// CL07: search by employee lists only that employee's collections
	@Test
	public void CL07_search_by_employee() {
		test = extent.createTest("CL07 Search by employee");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, EMPLOYEE, "", "", "");
		Assert.assertTrue(c.result_count() > 0, "No collection was found for " + EMPLOYEE);
		for (List<String> row : c.result_texts()) {
			Assert.assertTrue(row.get(1).toLowerCase().contains(EMPLOYEE.toLowerCase()), "A collection of another employee: " + row);
		}
		test.pass(total_entries(c) + " collections of " + EMPLOYEE);
	}

	// CL08: search by customer (the customer of the first row) lists only that customer's collections
	@Test
	public void CL08_search_by_customer() {
		test = extent.createTest("CL08 Search by customer");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "");
		String customer = c.result_texts().get(0).get(2);
		test.info("Customer used: " + customer);
		search_wide(c, "", customer, "", "");
		Assert.assertTrue(c.result_count() > 0, "No collection was found for " + customer);
		for (List<String> row : c.result_texts()) {
			Assert.assertEquals(row.get(2), customer, "A collection of another customer: " + row);
		}
		test.pass("Only " + customer + " is listed");
	}

	// CL09: From Date and To Date: only collections of the range are listed
	@Test
	public void CL09_search_by_date_range() {
		test = extent.createTest("CL09 Search by date range");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "");
		int all = total_entries(c);
		LocalDate from = LocalDate.of(2026, 10, 2);
		LocalDate to = LocalDate.of(2026, 10, 7);
		c.Search_collections(type1, "", "", "", "", "", from.format(DATE), to.format(DATE));
		test.info("Rows for " + from.format(DATE) + " .. " + to.format(DATE) + ": " + c.verify_entries().getText() + " (all: " + all + ")");
		Assert.assertTrue(c.result_count() > 0, "No collection was found in the date range");
		for (List<String> row : c.result_texts()) {
			LocalDate day = LocalDate.parse(row.get(4).substring(0, 10), ROW_DATE);
			Assert.assertTrue(!day.isBefore(from) && !day.isAfter(to),
					"DEFECT: a collection outside the date range is listed: " + row);
		}
		test.pass("Only collections of the date range are listed");
	}

	// CL10: From Date after To Date: no collection (or a message) must be shown
	@Test
	public void CL10_reversed_date_range() {
		test = extent.createTest("CL10 Reversed date range");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "");
		c.Search_collections(type1, "", "", "", "", "", "20/10/2026", "01/10/2026");
		test.info("Rows: " + c.result_count() + " | messages: " + c.validation_messages() + " | notice: " + c.notice());
		Assert.assertTrue(c.result_count() == 0 || !c.validation_messages().isEmpty() || !c.notice().isEmpty(),
				"DEFECT: a reversed date range lists collections without any message");
		test.pass("Reversed range gives no collection or a message");
	}

	// CL11: an employee that does not exist gives an empty table ("No Data Available")
	@Test
	public void CL11_search_no_match() {
		test = extent.createTest("CL11 Search without match");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "");
		c.Search_collections(type1, "", "NOMATCH" + unique(), "", "", "", FROM_WIDE, TO_WIDE);
		Assert.assertEquals(c.result_count(), 0, "No collection should be listed. Rows shown: " + c.result_texts());
		test.pass("Empty result for an unknown employee");
	}

	// CL12: Clear Filter puts the search form back to its defaults
	@Test
	public void CL12_clear_filter() {
		test = extent.createTest("CL12 Clear Filter");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "Cash", "Pending");
		c.Click_clear_filter();
		c.Open_search_form();
		test.info("After Clear Filter: mode '" + c.selected(c.verify_mode()) + "', status '" + c.selected(c.verify_status())
				+ "', from '" + c.verify_from().getAttribute("value") + "', to '" + c.verify_to().getAttribute("value")
				+ "', type '" + c.selected(c.verify_type()) + "'");
		Assert.assertEquals(c.selected(c.verify_mode()), "All", "Receipt Mode after Clear Filter");
		Assert.assertEquals(c.selected(c.verify_status()), "All", "Receipt Status after Clear Filter");
		String today = LocalDate.now().format(DATE);
		Assert.assertEquals(c.verify_from().getAttribute("value"), today, "From Date after Clear Filter");
		Assert.assertEquals(c.verify_to().getAttribute("value"), today, "To Date after Clear Filter");
		test.pass("Clear Filter resets the search form");
	}

	// ======================= the table =======================

	// CL13: the "Search:" box above the table filters the rows; unknown text gives an empty table
	@Test
	public void CL13_table_filter() {
		test = extent.createTest("CL13 Table filter");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "");
		c.Filter_table(EMPLOYEE);
		Assert.assertTrue(c.result_count() > 0, "The filter hid every row");
		for (List<String> row : c.result_texts()) {
			Assert.assertTrue(row.toString().toLowerCase().contains(EMPLOYEE.toLowerCase()), "A row that does not match: " + row);
		}
		c.Filter_table("NOMATCH" + unique());
		Assert.assertEquals(c.result_count(), 0, "No row should be listed for unknown text");
		test.pass("Table filter works");
	}

	// CL14: Next shows entries 11 to 20, Previous goes back to 1 to 10
	@Test
	public void CL14_paging() {
		test = extent.createTest("CL14 Paging");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "");
		if (total_entries(c) <= 10) {
			throw new SkipException("Only " + total_entries(c) + " collections: one page");
		}
		c.Click_next();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> c.verify_entries().getText().contains("Showing 11 to"));
		c.Click_previous();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> c.verify_entries().getText().contains("Showing 1 to 10"));
		test.pass("Next and Previous work");
	}

	// CL15: every row: employee as "Name (CODE)", a receipt number, date and time, a known mode and type, a numeric
	// amount and a status
	@Test
	public void CL15_rows_are_consistent() {
		test = extent.createTest("CL15 Rows are consistent");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "");
		for (List<String> row : c.result_texts()) {
			Assert.assertTrue(row.get(1).matches(".+ \\(.+\\)"), "Employee is not 'Name (CODE)': " + row);
			Assert.assertFalse(row.get(2).isEmpty(), "Customer is empty: " + row);
			Assert.assertFalse(row.get(3).isEmpty(), "Receipt number is empty: " + row);
			Assert.assertTrue(row.get(4).matches("\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2} (AM|PM)"), "Date and time: " + row);
			Assert.assertTrue(row.get(5).equals("Cash") || row.get(5).equals("Upi") || row.get(5).equals("Cheque"),
					"Unknown receipt mode: " + row);
			Assert.assertTrue(row.get(6).equals("Bill Payment") || row.get(6).equals("Advance Payment"),
					"Unknown receipt type: " + row);
			Assert.assertTrue(row.get(8).matches("\\d+(\\.\\d+)?"), "Amount is not a number: " + row);
			Assert.assertFalse(row.get(9).isEmpty(), "Receipt status is empty: " + row);
		}
		test.pass("All rows are consistent");
	}

	// ======================= details pop-up =======================

	// opens the details of the first row of a mode, checks the pop-up and returns its tables
	private List<List<List<String>>> open_first_details(P_Collections_Cls c, String mode, List<String> row_out) {
		search_wide(c, "", "", mode, "");
		if (c.result_count() == 0) {
			throw new SkipException("No " + mode + " collection to open");
		}
		List<String> row = c.result_texts().get(0);
		row_out.addAll(row);
		test.info("Row used: " + row);
		c.Open_details(row.get(3));
		Assert.assertTrue(c.details_open(), "The details pop-up did not open");
		return c.details_tables();
	}

	// the sum of the second column of a table (the header row is skipped)
	private BigDecimal column_sum(List<List<String>> table, int column) {
		BigDecimal sum = BigDecimal.ZERO;
		for (int i = 1; i < table.size(); i++) {
			if (table.get(i).size() > column && table.get(i).get(column).matches("[\\d,]+(\\.\\d+)?")) {
				sum = sum.add(number(table.get(i).get(column)));
			}
		}
		return sum;
	}

	// CL16: Cash details: Collection Details (the amounts add up to the row amount) and Denominations (their amounts
	// add up to the row amount); the x closes the pop-up
	@Test
	public void CL16_cash_details() {
		test = extent.createTest("CL16 Cash details");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		List<String> row = new ArrayList<String>();
		List<List<List<String>>> tables = open_first_details(c, "Cash", row);
		String text = c.details_text();
		test.info("Pop-up text: " + text);
		Assert.assertTrue(text.contains("Collection Details"), "Collection Details is missing");
		Assert.assertTrue(text.contains("Denominations"), "Denominations is missing");
		BigDecimal amount = number(row.get(8));
		Assert.assertEquals(column_sum(tables.get(0), 1).compareTo(amount), 0,
				"DEFECT: the collection amounts " + column_sum(tables.get(0), 1) + " do not add up to the row amount " + amount);
		if (!text.contains("No denomination records found")) {
			Assert.assertEquals(column_sum(tables.get(1), 2).compareTo(amount), 0,
					"DEFECT: the denominations " + column_sum(tables.get(1), 2) + " do not add up to the row amount " + amount);
		}
		c.Close_details();
		Assert.assertFalse(c.details_open(), "The pop-up must close");
		test.pass("Cash details are consistent");
	}

	// CL17: UPI details: a UPI Details section with a reference number; the collection amounts add up to the row amount
	@Test
	public void CL17_upi_details() {
		test = extent.createTest("CL17 UPI details");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		List<String> row = new ArrayList<String>();
		List<List<List<String>>> tables = open_first_details(c, "Upi", row);
		String text = c.details_text();
		test.info("Pop-up text: " + text);
		Assert.assertTrue(text.contains("Upi Details"), "Upi Details is missing");
		Assert.assertTrue(text.contains("Reference Number"), "Reference Number is missing");
		BigDecimal amount = number(row.get(8));
		Assert.assertEquals(column_sum(tables.get(0), 1).compareTo(amount), 0,
				"DEFECT: the collection amounts " + column_sum(tables.get(0), 1) + " do not add up to the row amount " + amount);
		c.Close_details();
		test.pass("UPI details are consistent");
	}

	// CL18: cheque details: a Cheque Details section with cheque number, date and bank; the collection amounts add up
	// to the row amount
	@Test
	public void CL18_cheque_details() {
		test = extent.createTest("CL18 Cheque details");
		P_Collections_Cls c = new P_Collections_Cls(driver);

		List<String> row = new ArrayList<String>();
		List<List<List<String>>> tables = open_first_details(c, "Cheque", row);
		String text = c.details_text();
		test.info("Pop-up text: " + text);
		Assert.assertTrue(text.contains("Cheque Details"), "Cheque Details is missing");
		Assert.assertTrue(text.contains("Cheque Number") && text.contains("Cheque Date") && text.contains("Bank"),
				"The cheque columns are missing");
		BigDecimal amount = number(row.get(8));
		Assert.assertEquals(column_sum(tables.get(0), 1).compareTo(amount), 0,
				"DEFECT: the collection amounts " + column_sum(tables.get(0), 1) + " do not add up to the row amount " + amount);
		c.Close_details();
		test.pass("Cheque details are consistent");
	}

	// ======================= approve and reject (change records) =======================

	// CL19: the Approve All tick box ticks the Approve box of every row and unticking clears them.
	// Ticking may approve every listed collection at once: only with ALLOW_STATE_CHANGES.
	@Test
	public void CL19_approve_all_box() {
		test = extent.createTest("CL19 Approve All");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false: Approve All changes records");
		}
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "Pending");
		if (c.result_count() == 0) {
			throw new SkipException("No pending collection");
		}
		((JavascriptExecutor) driver).executeScript("arguments[0].click();", c.approve_all_box());
		test.info("Messages: " + c.Confirm_action());
		test.info("Row boxes ticked: " + c.ticked_row_boxes() + " of " + c.row_box_count());
		Assert.assertTrue(c.ticked_row_boxes() > 0 || c.result_count() == 0, "Approve All did not tick any row");
		test.pass("Approve All used");
	}

	// CL20: ticking the Approve box of one pending collection: the status changes from Pending (cannot be undone)
	@Test
	public void CL20_approve_one_collection() {
		test = extent.createTest("CL20 Approve one collection");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false: Approve changes records");
		}
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "Pending");
		if (c.result_count() == 0) {
			throw new SkipException("No pending collection");
		}
		String receipt = c.result_texts().get(0).get(3);
		test.info("Receipt used: " + receipt);
		test.info("Messages: " + c.Tick_approve(receipt));
		search_wide(c, "", "", "", "");
		List<String> row = c.row_of(receipt);
		test.info("Row after Approve: " + row);
		Assert.assertTrue(row == null || !row.get(9).equals("Pending"), "The collection is still Pending after Approve");
		test.pass("Collection " + receipt + " approved");
	}

	// CL21: the red x of one pending collection: the status changes from Pending or the row is removed
	// (cannot be undone)
	@Test
	public void CL21_reject_one_collection() {
		test = extent.createTest("CL21 Reject one collection");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false: Reject changes records");
		}
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "Pending");
		if (c.result_count() == 0) {
			throw new SkipException("No pending collection");
		}
		String receipt = c.result_texts().get(0).get(3);
		test.info("Receipt used: " + receipt);
		test.info("Messages: " + c.Click_reject(receipt));
		search_wide(c, "", "", "", "");
		List<String> row = c.row_of(receipt);
		test.info("Row after Reject: " + row);
		Assert.assertTrue(row == null || !row.get(9).equals("Pending"), "The collection is still Pending after Reject");
		test.pass("Collection " + receipt + " rejected");
	}

	// CL22: Cancel in the question of the Approve box keeps the collection Pending (if the box asks nothing the
	// collection is approved at once: this is reported)
	@Test
	public void CL22_approve_cancel() {
		test = extent.createTest("CL22 Approve cancelled");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false: the Approve box may approve at once");
		}
		P_Collections_Cls c = new P_Collections_Cls(driver);

		search_wide(c, "", "", "", "Pending");
		if (c.result_count() == 0) {
			throw new SkipException("No pending collection");
		}
		String receipt = c.result_texts().get(0).get(3);
		String question = c.Tick_approve_and_cancel(receipt);
		test.info("Question shown: '" + question + "'");
		Assert.assertFalse(question.isEmpty(), "DEFECT: the Approve box approves without asking for a confirmation");
		search_wide(c, "", "", "", "");
		List<String> row = c.row_of(receipt);
		Assert.assertNotNull(row, "The collection disappeared after Cancel");
		Assert.assertEquals(row.get(9), "Pending", "The collection must stay Pending after Cancel");
		test.pass("Cancel keeps the collection Pending");
	}
}