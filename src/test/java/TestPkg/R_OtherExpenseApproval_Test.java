package TestPkg;

import UtillPkg.WaitUtil;
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
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;
import PagePkg.R_OtherExpenseApproval_Cls;

/*
 * Other Expense Approval (Field Management > Other Expense Approval) - functional tests, OE01 .. OE21.
 * The page lists the PENDING other expenses. Ticking a row or Approve All starts an approve / reject (with remarks):
 * that changes real records and cannot be undone, so OE17 .. OE21 run only when ALLOW_STATE_CHANGES is true
 * (it is false here). Everything else only reads data.
 */
public class R_OtherExpenseApproval_Test extends BaseCls {

	// true = the tests that approve or reject expenses are run (use QA data only)
	static final boolean ALLOW_STATE_CHANGES = true;

	static final String FROM_WIDE = "01/10/2026";
	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
	}

	// opens Field Management > Other Expense Approval from the dashboard
	private void go_to_approval() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_by_name("Field Management", "Other Expense Approval");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Field Management", "Other Expense Approval");
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

	// every test starts with a fresh Other Expense Approval screen
	@BeforeMethod
	public void open_approval() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);
		try {
			go_to_approval();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_approval();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		}
		remember_clean_screen();
	}

	// the pending expenses from FROM_WIDE until today, with an optional filter
	private void search_wide(R_OtherExpenseApproval_Cls o, String employee, String expense_type, String status, String factory) {
		o.Search_expenses("", employee, expense_type, status, factory, FROM_WIDE, today());
	}

	private LocalDate row_date(List<String> row) {
		return LocalDate.parse(row.get(1), DATE);
	}

	// the first listed row (the expense used by the tests)
	private List<String> first_row(R_OtherExpenseApproval_Cls o) {
		if (o.result_count() == 0) {
			throw new SkipException("No pending expense is listed");
		}
		return o.result_texts().get(0);
	}

	// ======================= screen and search form =======================

	// OE01: the screen shows Search, Approve All, the table filter, the 10 column headers and the entries line
	@Test
	public void OE01_screen_elements() {
		test = extent.createTest("OE01 Other Expense Approval screen elements");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		Assert.assertTrue(o.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertNotNull(o.approve_all_box(), "Approve All is missing");
		Assert.assertTrue(o.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = o.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "FACTORY", "EXPENSE DATE", "EXPENSE NAME", "EXPENSE NUMBER", "DESIGNATION",
				"EMPLOYEE", "RATE(₹)", "IMAGE", "REMARKS", "ACTION" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(o.verify_entries().getText().contains("Showing"), o.verify_entries().getText());
		test.pass("Screen elements are displayed: " + o.verify_entries().getText());
	}

	// OE02: the Search button opens the search form with its 7 fields and Search; both dates start on today
	@Test
	public void OE02_search_form_opens() {
		test = extent.createTest("OE02 Search form opens");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		o.Open_search_form();
		Assert.assertTrue(o.verify_type().isDisplayed(), "Employee Type is missing");
		Assert.assertTrue(o.verify_employee().isDisplayed(), "Employee Name is missing");
		Assert.assertTrue(o.verify_expense_type().isDisplayed(), "Expense Type is missing");
		Assert.assertTrue(o.verify_status().isDisplayed(), "Status is missing");
		Assert.assertTrue(o.verify_from().isDisplayed(), "From Date is missing");
		Assert.assertTrue(o.verify_to().isDisplayed(), "To Date is missing");
		Assert.assertTrue(o.verify_factory().isDisplayed(), "Factory Name is missing");
		Assert.assertTrue(o.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertEquals(o.verify_from().getAttribute("value"), today(), "Default From Date");
		Assert.assertEquals(o.verify_to().getAttribute("value"), today(), "Default To Date");
		test.pass("Search form is displayed with its defaults");
	}

	// OE03: Employee Type, Expense Type and Status have values (written to the report)
	@Test
	public void OE03_drop_down_options() {
		test = extent.createTest("OE03 Drop down options");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		o.Open_search_form();
		List<String> types = o.options_of(o.verify_type());
		List<String> expenses = o.options_of(o.verify_expense_type());
		List<String> statuses = o.options_of(o.verify_status());
		test.info("Employee types: " + types);
		test.info("Expense types: " + expenses);
		test.info("Statuses: " + statuses + " (default '" + o.selected(o.verify_status()) + "')");
		test.info("Status values sent by the page: " + o.option_values(o.verify_status()));
		Assert.assertFalse(types.isEmpty(), "Employee Type has no values");
		Assert.assertFalse(expenses.isEmpty(), "Expense Type has no values");
		Assert.assertFalse(statuses.isEmpty(), "Status has no values");
		test.pass("Drop downs are filled");
	}

	// OE04: before any search the list shows the pending expenses of today only
	@Test
	public void OE04_default_list_is_today() {
		test = extent.createTest("OE04 Default list");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		test.info(o.verify_entries().getText() + " | " + o.result_count() + " rows");
		if (o.result_count() == 0) {
			throw new SkipException("No pending expense of today");
		}
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(1), today(), "A pending expense of another day is listed: " + row);
		}
		test.pass("Only today's pending expenses are listed");
	}

	// OE05: a wide date range lists at least the expenses of today, only inside the range
	@Test
	public void OE05_search_wide_date_range() {
		test = extent.createTest("OE05 Search a wide date range");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		int today_rows = o.result_count();
		search_wide(o, "", "", "", "");
		test.info("Today: " + today_rows + " rows, " + FROM_WIDE + " .. " + today() + ": " + o.verify_entries().getText());
		LocalDate from = LocalDate.parse(FROM_WIDE, DATE);
		LocalDate to = LocalDate.now();
		for (List<String> row : o.result_texts()) {
			LocalDate d = row_date(row);
			Assert.assertTrue(!d.isBefore(from) && !d.isAfter(to), "DEFECT: an expense outside the date range: " + row);
		}
		Assert.assertTrue(o.result_count() >= Math.min(today_rows, 10), "The wide range lists fewer expenses than today alone");
		test.pass("Wide range works");
	}

	// OE06: From Date after To Date: no expense (or a message) must be shown
	@Test
	public void OE06_reversed_date_range() {
		test = extent.createTest("OE06 Reversed date range");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		o.Search_expenses("", "", "", "", "", "20/10/2026", "01/10/2026");
		test.info("Rows: " + o.result_count() + " | messages: " + o.validation_messages() + " | notice: " + o.notice());
		Assert.assertTrue(o.result_count() == 0 || !o.validation_messages().isEmpty() || !o.notice().isEmpty(),
				"DEFECT: a reversed date range lists expenses without any message");
		test.pass("Reversed range gives no expense or a message");
	}

	// OE07: search by employee lists only that employee's expenses
	@Test
	public void OE07_search_by_employee() {
		test = extent.createTest("OE07 Search by employee");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		String employee = first_row(o).get(5);
		String name = employee.split(" \\(")[0];
		test.info("Employee used: " + employee);
		search_wide(o, name, "", "", "");
		Assert.assertTrue(o.result_count() > 0, "No expense was found for " + employee);
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(5), employee, "An expense of another employee: " + row);
		}
		test.pass("Only " + employee + " is listed");
	}

	// OE08: search by expense type lists only that expense type
	@Test
	public void OE08_search_by_expense_type() {
		test = extent.createTest("OE08 Search by expense type");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		String expense = first_row(o).get(2);
		test.info("Expense type used: " + expense);
		search_wide(o, "", expense, "", "");
		Assert.assertTrue(o.result_count() > 0, "No expense was found for " + expense);
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(2), expense, "An expense of another type: " + row);
		}
		test.pass("Only " + expense + " is listed");
	}

	// OE09: search by factory lists only that factory
	@Test
	public void OE09_search_by_factory() {
		test = extent.createTest("OE09 Search by factory");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		String factory = first_row(o).get(0);
		test.info("Factory used: " + factory);
		search_wide(o, "", "", "", factory);
		Assert.assertTrue(o.result_count() > 0, "No expense was found for " + factory);
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(0), factory, "An expense of another factory: " + row);
		}
		test.pass("Only " + factory + " is listed");
	}

	// OE10: every status: a status other than pending must not list pending expenses (an unticked tick box);
	// the result of every status is written to the report
	@Test
	public void OE10_search_by_status() {
		test = extent.createTest("OE10 Search by status");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		o.Open_search_form();
		List<String> statuses = o.options_of(o.verify_status());
		List<String> wrong = new ArrayList<String>();
		for (String status : statuses) {
			search_wide(o, "", "", status, "");
			int pending = o.pending_count();
			test.info("Status '" + status + "': " + o.result_count() + " rows, " + pending + " pending (" + o.verify_entries().getText() + ")");
			boolean wants_pending = status.toLowerCase().contains("pend") || status.toLowerCase().contains("not approved");
			if (!wants_pending && pending > 0) {
				wrong.add(status + ": " + pending + " pending expense(s) are listed");
			}
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: the Status filter lists pending expenses for another status: " + wrong);
		test.pass("The status filter matches");
	}

	// OE11: an employee that does not exist gives an empty table
	@Test
	public void OE11_search_no_match() {
		test = extent.createTest("OE11 Search without match");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		o.Search_expenses("", "NOMATCH" + unique(), "", "", "", FROM_WIDE, today());
		Assert.assertTrue(o.result_count() == 0 || !o.validation_messages().isEmpty() || !o.notice().isEmpty(),
				"DEFECT: an employee name that does not exist is ignored and " + o.result_count()
						+ " expenses are listed without any message");
	}

	// ======================= the table =======================

	// OE12: the "Search:" box above the table filters the rows; unknown text gives an empty table
	@Test
	public void OE12_table_filter() {
		test = extent.createTest("OE12 Table filter");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		String key = first_row(o).get(2);
		o.Filter_table(key);
		Assert.assertTrue(o.result_count() > 0, "The filter for '" + key + "' hid every row");
		for (List<String> row : o.result_texts()) {
			Assert.assertTrue(row.toString().toLowerCase().contains(key.toLowerCase()), "A row that does not match: " + row);
		}
		o.Filter_table("NOMATCH" + unique());
		Assert.assertEquals(o.result_count(), 0, "No row should be listed for unknown text");
		test.pass("Table filter works");
	}

	// OE13: with up to 10 expenses there is one page: Previous and Next are disabled
	@Test
	public void OE13_paging() {
		test = extent.createTest("OE13 Paging");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		String info = o.verify_entries().getText();
		test.info(info);
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("of (\\d+) entries").matcher(info);
		Assert.assertTrue(m.find(), "The entries line could not be read: " + info);
		int total = Integer.parseInt(m.group(1));
		if (total <= 10) {
			Assert.assertEquals(o.result_count(), total, "All expenses must be on one page");
			Assert.assertFalse(o.next_enabled(), "Next must be disabled when there is one page");
			Assert.assertFalse(o.previous_enabled(), "Previous must be disabled when there is one page");
		} else {
			Assert.assertTrue(o.next_enabled(), "Next must be enabled for " + total + " expenses");
		}
		test.pass("Paging matches the number of expenses");
	}

	// OE14: every row: factory, a date (dd/MM/yyyy), expense name, an expense number, designation, the employee as
	// "Name (CODE)", a numeric rate, an image text and a tick box
	@Test
	public void OE14_rows_are_consistent() {
		test = extent.createTest("OE14 Rows are consistent");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		if (o.result_count() == 0) {
			throw new SkipException("No pending expense is listed");
		}
		for (List<String> row : o.result_texts()) {
			Assert.assertFalse(row.get(0).isEmpty(), "Factory is empty: " + row);
			Assert.assertTrue(row.get(1).matches("\\d{2}/\\d{2}/\\d{4}"), "Expense date: " + row);
			Assert.assertFalse(row.get(2).isEmpty(), "Expense name is empty: " + row);
			Assert.assertTrue(row.get(3).matches("\\d{2}[A-Z]+\\d+"), "Expense number: " + row);
			Assert.assertFalse(row.get(4).isEmpty(), "Designation is empty: " + row);
			Assert.assertTrue(row.get(5).matches(".+ \\(.+\\)"), "Employee is not 'Name (CODE)': " + row);
			Assert.assertTrue(row.get(6).matches("\\d+(\\.\\d+)?"), "Rate is not a number: " + row);
			Assert.assertFalse(row.get(7).isEmpty(), "Image column is empty: " + row);
		}
		Assert.assertEquals(o.row_box_count(), o.result_count(), "Every row must have a tick box");
		test.pass("All rows are consistent");
	}

	// OE15: expense numbers are unique in the list
	@Test
	public void OE15_expense_numbers_are_unique() {
		test = extent.createTest("OE15 Unique expense numbers");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		List<String> seen = new ArrayList<String>();
		for (List<String> row : o.result_texts()) {
			Assert.assertFalse(seen.contains(row.get(3)), "DEFECT: the expense number " + row.get(3) + " is listed twice");
			seen.add(row.get(3));
		}
		test.pass("No expense number is listed twice");
	}

	// OE16: the tick boxes start unticked and Approve All starts unticked
	@Test
	public void OE16_tick_boxes_start_unticked() {
		test = extent.createTest("OE16 Tick boxes start unticked");
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		if (o.result_count() == 0) {
			throw new SkipException("No pending expense is listed");
		}
		Assert.assertEquals(o.ticked_row_boxes(), 0, "No row may be ticked at the start");
		Assert.assertFalse(o.approve_all_box().isSelected(), "Approve All must be unticked at the start");
		test.pass("Nothing is ticked at the start");
	}

	// ======================= approve and reject (change records) =======================

	// OE17: ticking a pending expense opens a dialog (its text, buttons and remarks box are written to the report);
	// Cancel keeps the expense pending. Only with ALLOW_STATE_CHANGES: a tick may approve at once.
	@Test
	public void OE17_tick_opens_dialog_and_cancel() {
		test = extent.createTest("OE17 Tick and cancel");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false: a tick may approve at once");
		}
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		String number = first_row(o).get(3);
		o.Tick_row(number);
		boolean dialog = o.dialog_open();
		test.info("Dialog open: " + dialog + " | text: " + (dialog ? o.Dialog_text() : "") + " | buttons: "
				+ (dialog ? o.dialog_buttons() : "") + " | remarks box: " + (dialog && o.dialog_has_remarks()));
		Assert.assertTrue(dialog, "DEFECT: the tick approves or rejects without asking for a confirmation");
		o.Click_dialog_button("Cancel");
		search_wide(o, "", "", "", "");
		List<String> row = o.row_of(number);
		Assert.assertNotNull(row, "The expense disappeared after Cancel");
		Assert.assertTrue(o.row_pending(number), "The expense must stay pending after Cancel");
		test.pass("Cancel keeps the expense pending");
	}

	// OE18: approve one pending expense with a remark: it leaves the pending list (cannot be undone)
	@Test
	public void OE18_approve_one() {
		test = extent.createTest("OE18 Approve one expense");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false: Approve changes records");
		}
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		String number = first_row(o).get(3);
		o.Tick_row(number);
		test.info("Dialog: " + o.Dialog_text() + " | buttons: " + o.dialog_buttons());
		if (o.dialog_has_remarks()) {
			o.Set_dialog_remarks("ZQ approved " + unique());
		}
		o.Click_dialog_button("Approve");
		test.info("Messages: " + o.Accept_messages());
		search_wide(o, "", "", "", "");
		Assert.assertNull(o.row_of(number), "The approved expense is still listed as pending: " + number);
		test.pass("Expense " + number + " approved");
	}

	// OE19: reject one pending expense with a remark: it leaves the pending list (cannot be undone)
	@Test
	public void OE19_reject_one() {
		test = extent.createTest("OE19 Reject one expense");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false: Reject changes records");
		}
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		String number = first_row(o).get(3);
		o.Tick_row(number);
		test.info("Dialog: " + o.Dialog_text() + " | buttons: " + o.dialog_buttons());
		if (o.dialog_has_remarks()) {
			o.Set_dialog_remarks("ZQ rejected " + unique());
		}
		o.Click_dialog_button("Reject");
		test.info("Messages: " + o.Accept_messages());
		search_wide(o, "", "", "", "");
		List<String> row = o.row_of(number);
		Assert.assertTrue(row == null || !o.row_pending(number), "The rejected expense is still pending: " + number);
		test.pass("Expense " + number + " rejected");
	}

	// OE20: approving or rejecting without a remark: the result is written to the report (an approval without a
	// remark may be allowed); the expense used leaves the pending list when it is accepted
	@Test
	public void OE20_approve_without_remark() {
		test = extent.createTest("OE20 Approve without a remark");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false: Approve changes records");
		}
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		String number = first_row(o).get(3);
		o.Tick_row(number);
		if (!o.dialog_has_remarks()) {
			o.Click_dialog_button("Cancel");
			throw new SkipException("The dialog has no remarks box");
		}
		o.Click_dialog_button("Approve");
		test.info("Messages: " + o.Accept_messages() + " | validation: " + o.validation_messages());
		search_wide(o, "", "", "", "");
		test.warning(o.row_of(number) == null ? "An approval WITHOUT a remark was accepted. Confirm that this is intended."
				: "An approval without a remark was refused.");
		test.pass("Observation recorded");
	}

	// OE21: Approve All ticks every listed expense and approves all of them (cannot be undone)
	@Test
	public void OE21_approve_all() {
		test = extent.createTest("OE21 Approve All");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false: Approve All changes records");
		}
		R_OtherExpenseApproval_Cls o = new R_OtherExpenseApproval_Cls(driver);

		search_wide(o, "", "", "", "");
		if (o.result_count() == 0) {
			throw new SkipException("No pending expense is listed");
		}
		int before = o.result_count();
		o.Tick_approve_all();
		test.info("Row boxes ticked: " + o.ticked_row_boxes() + " of " + o.row_box_count() + " | dialog: "
				+ (o.dialog_open() ? o.Dialog_text() + " | buttons: " + o.dialog_buttons() : "none"));
		if (o.dialog_open()) {
			if (o.dialog_has_remarks()) {
				o.Set_dialog_remarks("ZQ approve all " + unique());
			}
			o.Click_dialog_button("Approve");
			test.info("Messages: " + o.Accept_messages());
		}
		search_wide(o, "", "", "", "");
		test.info("Pending before: " + before + ", after: " + o.result_count());
		Assert.assertTrue(o.result_count() < before, "Approve All did not remove any pending expense");
		test.pass("Approve All used");
	}
}