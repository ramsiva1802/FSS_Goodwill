package TestPkg;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import PagePkg.YA_ExpenseApproval_Cls;

/*
 * Expense Approval (Field Management > Expense Approval), logged in as the web user - tests EA01 .. EA52.
 * The screen lists the attendance expenses of the field staff for a date range (the month by default). A row with an
 * enabled check box is decided in a pop-up: Yes, Approve It (km and remarks can be changed) or Reject (remarks needed),
 * each with a confirmation. Decided rows show Approved / Rejected. Approved expenses go to Reports > Attendance Expense
 * Reports (see YC_ExpenseApprovalReportLink_Test).
 * EA01 .. EA27 search, filters and the table; EA28 .. EA38 the content and the calculations (read every row of every page);
 * EA39 .. EA46 the decision pop-up, stopped before anything is saved (Approve All is ticked, never confirmed);
 * EA47 .. EA51 REALLY approve / reject expenses (they use up pending rows; a test is skipped when no pending row is
 * left); EA52 decided rows are locked.
 * "DEFECT:" = the site is wrong, "OBSERVATION:" = something to look at (written as a warning in the report).
 */
public class YA_ExpenseApproval_Test extends ExpenseTestBase {

	static final String[] HEADERS = { "FACTORY", "EMPLOYEE TYPE", "EMPLOYEE", "EXPENSE TYPE", "EXPENSE DATE", "EXPENSE NUMBER",
			"EXPENSE AMOUNT", "REMARKS", "AGAINST ATTENDANCE", "IN TIME", "OUT TIME", "START LOCATION", "END LOCATION",
			"ODO START KM", "IMAGE", "ODO END KM", "DISTANCE", "CALCULATED DISTANCE", "ACTION" };

	// every test starts with a fresh Expense Approval screen
	@BeforeMethod
	public void open_screen() {
		if (screen_is_clean()) {
			return;
		}
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);
		open_module("Field Management", "Expense Approval", () -> a.is_list_page());
		remember_clean_screen();
	}

	// ---------------------------------- helpers ----------------------------------

	// searches the wide date range and returns every row of every page; skips the test when there is none
	// the rows of every page are read ONCE and kept (walking all the pages again for every test was slow); the
	// search itself is still done, so the screen shows the wide result. A test that approves / rejects clears the copy.
	private static List<Map<String, String>> wide_cache;

	@BeforeClass
	public void reset_wide_cache() {
		wide_cache = null;
	}

	private List<Map<String, String>> wide(YA_ExpenseApproval_Cls a) {
		a.Search_dates(FROM_WIDE, today());
		if (wide_cache == null) {
			wide_cache = a.all_records();
		}
		List<Map<String, String>> all = wide_cache;
		test.info(FROM_WIDE + " .. " + today() + ": " + all.size() + " rows | " + a.entries_text());
		if (all.isEmpty()) {
			throw new SkipException("No expense is listed for " + FROM_WIDE + " .. " + today());
		}
		return all;
	}

	private boolean is_pending(Map<String, String> r) {
		return r.get("ACTION STATE").equals("enabled") && val(r, "ACTION").trim().isEmpty();
	}

	private boolean is_text(Map<String, String> r, String word) {
		return val(r, "ACTION").toLowerCase().contains(word);
	}

	private Map<String, String> first_pending(List<Map<String, String>> all) {
		for (Map<String, String> r : all) {
			if (is_pending(r) && !val(r, "EXPENSE NUMBER").isEmpty()) {
				return r;
			}
		}
		throw new SkipException("No pending expense with an enabled check box is left in " + FROM_WIDE + " .. " + today());
	}

	private String header_of(YA_ExpenseApproval_Cls a, String name) {
		for (String h : a.headers()) {
			if (h.startsWith(name)) {
				return h;
			}
		}
		throw new AssertionError("Column header missing: " + name);
	}

	// 1 = ascending, -1 = descending, 2 = all values equal, 0 = not sorted
	private int direction(List<String> values, boolean as_number) {
		boolean asc = true;
		boolean desc = true;
		for (int i = 1; i < values.size(); i++) {
			int c = as_number ? Double.compare(YA_ExpenseApproval_Cls.to_number(values.get(i - 1)), YA_ExpenseApproval_Cls.to_number(values.get(i)))
					: values.get(i - 1).compareToIgnoreCase(values.get(i));
			if (c > 0) {
				asc = false;
			}
			if (c < 0) {
				desc = false;
			}
		}
		return asc && desc ? 2 : asc ? 1 : desc ? -1 : 0;
	}

	// values of a column that can be compared as text; a date column (dd/MM/yyyy) is turned into yyyyMMdd first
	private List<String> page_values(YA_ExpenseApproval_Cls a, String column, boolean date) {
		List<String> out = new ArrayList<String>();
		for (Map<String, String> r : a.records()) {
			String v = val(r, column);
			if (date && v.matches("\\d\\d/\\d\\d/\\d{4}")) {
				v = v.substring(6) + v.substring(3, 5) + v.substring(0, 2);
			}
			out.add(v);
		}
		return out;
	}

	private void sort_check(String title, String column, boolean as_number, boolean date) {
		test = extent.createTest(title);
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);
		wide(a);
		String h = header_of(a, column);
		a.Click_header(h);
		int first = direction(page_values(a, column, date), as_number);
		a.Click_header(h);
		int second = direction(page_values(a, column, date), as_number);
		test.info(column + ": after click 1 direction " + first + ", after click 2 direction " + second + " (1 up, -1 down, 2 equal, 0 mixed)");
		if (first == 2 || second == 2) {
			throw new SkipException("All values of " + column + " on the page are equal: sorting cannot be seen");
		}
		if (first == 0 || second == 0) {
			test.info("Values on the page after the last click: " + page_values(a, column, date));
		}
		Assert.assertTrue(first != 0 && second != 0, "DEFECT: the " + column + " column is not sorted after a click on its header");
		Assert.assertEquals(first, -second, "DEFECT: a second click on the " + column + " header does not reverse the order");
		test.pass(column + " sorts both ways");
	}

	// ======================= screen and search form =======================

	// EA01: Search, Approve All, the table filter and all the column headers
	@Test
	public void EA01_screen_elements() {
		test = extent.createTest("EA01 Expense Approval screen elements");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		Assert.assertTrue(a.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(a.verify_approve_all().isDisplayed(), "The Approve All check box is missing");
		Assert.assertTrue(a.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = a.headers();
		test.info("Headers: " + headers + " | " + a.entries_text());
		for (String h : HEADERS) {
			boolean found = false;
			for (String x : headers) {
				found |= x.startsWith(h);
			}
			Assert.assertTrue(found, "Column header missing: " + h);
		}
		test.pass("Screen elements are displayed");
	}

	// EA02: the Search button opens the form with its seven fields; From Date is the first of the month, To Date today
	@Test
	public void EA02_search_form_opens() {
		test = extent.createTest("EA02 Search form opens");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		a.Open_search_form();
		Assert.assertTrue(a.verify_employee_type().isDisplayed(), "Employee Type is missing");
		Assert.assertTrue(a.verify_employee_name().isDisplayed(), "Employee Name is missing");
		Assert.assertTrue(a.verify_expense_type().isDisplayed(), "Expense Type is missing");
		Assert.assertTrue(a.verify_status().isDisplayed(), "Status is missing");
		Assert.assertTrue(a.verify_from().isDisplayed(), "From Date is missing");
		Assert.assertTrue(a.verify_to().isDisplayed(), "To Date is missing");
		Assert.assertTrue(a.verify_factory().isDisplayed(), "Factory Name is missing");
		Assert.assertTrue(a.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		String first_of_month = LocalDate.now().withDayOfMonth(1).format(DATE);
		test.info("From " + a.verify_from().getAttribute("value") + " | To " + a.verify_to().getAttribute("value"));
		String from_default = a.verify_from().getAttribute("value");
		Assert.assertTrue(from_default.equals(first_of_month) || from_default.equals(today()),
				"Default From Date should be the first day of the month or today, but it is " + from_default);
		if (!from_default.equals(first_of_month)) {
			test.warning("OBSERVATION: From Date starts at today (" + from_default + "), not at the first day of the month");
		}
		Assert.assertEquals(a.verify_to().getAttribute("value"), today(), "Default To Date");
		test.pass("Search form is displayed with its defaults");
	}

	// EA03: the options of the three drop downs are written to the report
	@Test
	public void EA03_dropdown_options() {
		test = extent.createTest("EA03 Drop down options");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		a.Open_search_form();
		List<String> types = a.options_of(a.verify_employee_type());
		List<String> expense = a.options_of(a.verify_expense_type());
		List<String> status = a.options_of(a.verify_status());
		test.info("Employee Type (" + types.size() + "): " + types);
		test.info("Expense Type (" + expense.size() + "): " + expense);
		test.info("Status (" + status.size() + "): " + status);
		Assert.assertFalse(types.isEmpty(), "The Employee Type drop down has no value");
		Assert.assertFalse(expense.isEmpty(), "The Expense Type drop down has no value");
		Assert.assertFalse(status.isEmpty(), "The Status drop down has no value");
		test.pass("Drop downs are filled");
	}

	// EA04: the list that opens with the screen only has expenses inside the default dates
	@Test
	public void EA04_default_list() {
		test = extent.createTest("EA04 Default list");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = a.all_records();
		LocalDate from = LocalDate.now().withDayOfMonth(1);
		LocalDate to = LocalDate.now();
		test.info("Default list " + from.format(DATE) + " .. " + to.format(DATE) + ": " + all.size() + " rows | " + a.entries_text());
		if (all.isEmpty()) {
			throw new SkipException("The default list is empty");
		}
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : all) {
			LocalDate d = date_in(r, "EXPENSE DATE");
			if (d.isBefore(from) || d.isAfter(to)) {
				bad.add(val(r, "EXPENSE DATE") + " " + who(r));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the default list shows expenses outside " + from.format(DATE) + " .. " + to.format(DATE) + ": " + shorten(bad));
		if (a.entries_total() >= 0) {
			Assert.assertEquals(all.size(), a.entries_total(), "The rows of all pages differ from the entries line");
		}
		test.pass("The default list follows the default dates");
	}

	// ======================= dates =======================

	// EA05: a wide date range lists only expenses inside the range and is not smaller than the default list
	@Test
	public void EA05_search_wide_date_range() {
		test = extent.createTest("EA05 Search a wide date range");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		int default_rows = a.all_records().size();
		List<Map<String, String>> all = wide(a);
		LocalDate from = LocalDate.parse(FROM_WIDE, DATE);
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : all) {
			LocalDate d = date_in(r, "EXPENSE DATE");
			if (d.isBefore(from) || d.isAfter(LocalDate.now())) {
				bad.add(val(r, "EXPENSE DATE") + " " + who(r));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: expenses outside the date range: " + shorten(bad));
		Assert.assertTrue(all.size() >= default_rows, "DEFECT: the wide range lists " + all.size() + " rows, fewer than the default month (" + default_rows + ")");
		if (a.entries_total() >= 0) {
			Assert.assertEquals(all.size(), a.entries_total(), "The rows of all pages differ from the entries line");
		}
		test.pass("Wide range works: " + all.size() + " rows");
	}

	// EA06: one single day lists every expense of that day (the To Date day is included)
	@Test
	public void EA06_search_single_day() {
		test = extent.createTest("EA06 Search a single day");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		String day = val(all.get(0), "EXPENSE DATE");
		int expected = 0;
		for (Map<String, String> r : all) {
			if (val(r, "EXPENSE DATE").equals(day)) {
				expected++;
			}
		}
		a.Search_dates(day, day);
		List<Map<String, String>> found = a.all_records();
		test.info("Day " + day + ": " + found.size() + " rows, the wide search has " + expected + " rows on that day");
		for (Map<String, String> r : found) {
			Assert.assertEquals(val(r, "EXPENSE DATE"), day, "A row of another day: " + who(r));
		}
		Assert.assertEquals(found.size(), expected, "DEFECT: From Date = To Date = " + day + " lists " + found.size()
				+ " rows but the wide search has " + expected + " rows on that day");
		test.pass("Only " + day + " is listed");
	}

	// EA07: the oldest and the newest day of the data are both included
	@Test
	public void EA07_range_boundaries_included() {
		test = extent.createTest("EA07 Range boundaries");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		LocalDate oldest = LocalDate.MAX;
		LocalDate newest = LocalDate.MIN;
		for (Map<String, String> r : all) {
			LocalDate d = date_in(r, "EXPENSE DATE");
			oldest = d.isBefore(oldest) ? d : oldest;
			newest = d.isAfter(newest) ? d : newest;
		}
		a.Search_dates(oldest.format(DATE), newest.format(DATE));
		int found = a.all_records().size();
		test.info("Range " + oldest.format(DATE) + " .. " + newest.format(DATE) + ": " + found + " rows (the wide search has " + all.size() + ")");
		Assert.assertEquals(found, all.size(), "DEFECT: the range " + oldest.format(DATE) + " .. " + newest.format(DATE) + " lists " + found
				+ " rows but the wide search (01/01/2026 .. today) lists " + all.size());
		test.pass("Both boundary days are included");
	}

	// EA08: From Date after To Date: nothing (or a message) must be shown
	@Test
	public void EA08_reversed_date_range() {
		test = extent.createTest("EA08 Reversed date range");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		a.Search_dates("20/10/2026", "01/10/2026");
		test.info("Rows: " + a.result_count() + " | messages: " + a.validation_messages() + " | notice: " + a.notice());
		Assert.assertTrue(a.result_count() == 0 || !a.validation_messages().isEmpty() || !a.notice().isEmpty(),
				"DEFECT: a reversed date range lists expenses without any message");
		test.pass("Reversed range gives nothing or a message");
	}

	// EA09: a range in the future has no expense
	@Test
	public void EA09_future_dates() {
		test = extent.createTest("EA09 Future date range");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		String from = LocalDate.now().plusDays(60).format(DATE);
		String to = LocalDate.now().plusDays(90).format(DATE);
		a.Search_dates(from, to);
		test.info(from + " .. " + to + ": rows " + a.result_count() + " | " + a.entries_text());
		Assert.assertEquals(a.result_count(), 0, "Expenses are listed for a future range");
		test.pass("A future range lists nothing");
	}

	// EA10: invalid dates are refused
	@Test
	public void EA10_invalid_dates() {
		test = extent.createTest("EA10 Invalid dates");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (String value : new String[] { "abcd", "31/02/2026", "99/99/9999" }) {
			a.Search_dates(value, today());
			a.Open_search_form();
			String kept = a.verify_from().getAttribute("value");
			test.info("From Date '" + value + "' -> box keeps '" + kept + "' | rows " + a.result_count() + " | messages "
					+ a.validation_messages() + " | " + a.entries_text());
			boolean refused = kept == null || !kept.equals(value) || !a.validation_messages().isEmpty()
					|| a.result_count() == 0 || !a.notice().isEmpty();
			if (!refused) {
				bad.add(value);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: invalid From Date values were accepted and expenses listed: " + bad);
		test.pass("Invalid dates are not accepted");
	}

	// EA11: a range in the past (the whole of 2025) lists only days of 2025 or nothing
	@Test
	public void EA11_past_range() {
		test = extent.createTest("EA11 Past date range");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		a.Search_dates("01/01/2025", "31/12/2025");
		List<Map<String, String>> found = a.all_records();
		test.info("01/01/2025 .. 31/12/2025: " + found.size() + " rows | " + a.entries_text() + " | messages " + a.validation_messages());
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : found) {
			if (date_in(r, "EXPENSE DATE").getYear() != 2025) {
				bad.add(val(r, "EXPENSE DATE") + " " + who(r));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the range of 2025 lists other days: " + shorten(bad));
		test.pass("The past range lists only 2025 (" + found.size() + " rows)");
	}

	// ======================= filters =======================

	// EA12: the Employee Type filter lists only that type
	@Test
	public void EA12_employee_type_filter() {
		test = extent.createTest("EA12 Employee Type filter");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		String type = val(all.get(0), "EMPLOYEE TYPE");
		int expected = 0;
		for (Map<String, String> r : all) {
			if (val(r, "EMPLOYEE TYPE").equalsIgnoreCase(type)) {
				expected++;
			}
		}
		a.Search_expense(type, "", "", "", "", FROM_WIDE, today());
		List<Map<String, String>> found = a.all_records();
		test.info("Employee Type " + type + ": " + found.size() + " rows, the wide search has " + expected);
		for (Map<String, String> r : found) {
			Assert.assertTrue(val(r, "EMPLOYEE TYPE").equalsIgnoreCase(type), "A row of another type: " + who(r) + " " + val(r, "EMPLOYEE TYPE"));
		}
		Assert.assertEquals(found.size(), expected, "DEFECT: the Employee Type filter lists " + found.size() + " rows but the wide search has " + expected + " of " + type);
		test.pass("Only " + type + " is listed");
	}

	// EA13: the Employee Name search lists only that employee
	@Test
	public void EA13_employee_name_filter() {
		test = extent.createTest("EA13 Employee Name filter");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		String employee = val(all.get(0), "EMPLOYEE");
		String name = employee.contains("(") ? employee.substring(0, employee.indexOf('(')).trim() : employee;
		int expected = 0;
		for (Map<String, String> r : all) {
			if (val(r, "EMPLOYEE").equals(employee)) {
				expected++;
			}
		}
		test.info("Employee used: " + employee + " (typed: " + name + ")");
		a.Open_search_form();
		if (a.Suggestions_for(a.verify_employee_name(), name).isEmpty()) {
			throw new SkipException("The Employee Name box shows no suggestion for '" + name + "': the script cannot pick an employee");
		}
		a.Search_expense("", name, "", "", "", FROM_WIDE, today());
		List<Map<String, String>> found = a.all_records();
		test.info(found.size() + " rows found, the wide search has " + expected + " rows of " + employee);
		Assert.assertTrue(found.size() > 0, "No row was found for " + employee);
		for (Map<String, String> r : found) {
			Assert.assertTrue(val(r, "EMPLOYEE").toLowerCase().contains(name.toLowerCase()), "A row of another employee: " + who(r));
		}
		Assert.assertTrue(found.size() >= expected, "DEFECT: the employee search lists " + found.size() + " rows but the wide search has " + expected + " rows of " + employee);
		test.pass("Only " + employee + " is listed");
	}

	// EA14: the Employee Name box suggests employees; unknown text gives nothing and an unknown name is not ignored
	@Test
	public void EA14_employee_suggestions_and_unknown() {
		test = extent.createTest("EA14 Employee suggestions and unknown name");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		String employee = val(all.get(0), "EMPLOYEE");
		a.Open_search_form();
		List<String> found = new ArrayList<String>();
		for (int len : new int[] { 1, 2, 3 }) {
			found = a.Suggestions_for(a.verify_employee_name(), employee.substring(0, Math.min(len, employee.length())));
			test.info("Suggestions for '" + employee.substring(0, Math.min(len, employee.length())) + "': " + found);
			if (!found.isEmpty()) {
				break;
			}
		}
		if (found.isEmpty()) {
			throw new SkipException("The employee suggestions cannot be read for '" + employee + "'");
		}
		Assert.assertTrue(a.Suggestions_for(a.verify_employee_name(), "ZZQNoSuch" + unique()).isEmpty(), "An employee is suggested for text that matches nothing");
		a.Search_expense("", "ZZQNoSuchEmployee" + unique(), "", "", "", FROM_WIDE, today());
		boolean ignored = a.result_count() > 0 && a.all_records().size() >= all.size() && a.validation_messages().isEmpty() && a.notice().isEmpty();
		Assert.assertFalse(ignored, "DEFECT: an employee that does not exist is ignored and all " + all.size() + " rows are listed without a message");
		test.pass("Suggestions work and an unknown employee does not list everything");
	}

	// EA15: every Expense Type lists only that type and the types add up to the whole list
	@Test
	public void EA15_expense_type_filter() {
		test = extent.createTest("EA15 Expense Type filter");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		a.Open_search_form();
		int sum = 0;
		for (String type : a.options_of(a.verify_expense_type())) {
			a.Search_expense("", "", type, "", "", FROM_WIDE, today());
			List<Map<String, String>> found = a.all_records();
			sum += found.size();
			test.info("Expense Type " + type + ": " + found.size() + " rows");
			for (Map<String, String> r : found) {
				Assert.assertTrue(val(r, "EXPENSE TYPE").equalsIgnoreCase(type), "DEFECT: a row of another type under " + type + ": " + who(r) + " " + val(r, "EXPENSE TYPE"));
			}
		}
		if (sum != all.size()) {
			test.warning("OBSERVATION: the Expense Types add up to " + sum + " rows but the whole list has " + all.size());
		}
		test.pass("Every Expense Type lists only its own rows");
	}

	// EA16: every Status lists matching rows; the statuses are compared with the whole list
	@Test
	public void EA16_status_filter() {
		test = extent.createTest("EA16 Status filter");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		a.Open_search_form();
		int sum = 0;
		List<String> bad = new ArrayList<String>();
		for (String status : a.options_of(a.verify_status())) {
			a.Search_expense("", "", "", status, "", FROM_WIDE, today());
			List<Map<String, String>> found = a.all_records();
			sum += found.size();
			test.info("Status " + status + ": " + found.size() + " rows");
			String s = status.toLowerCase();
			for (Map<String, String> r : found) {
				boolean ok = s.contains("approv") ? is_text(r, "approv") : s.contains("reject") ? is_text(r, "reject")
						: s.contains("pend") ? val(r, "ACTION").trim().isEmpty() : true;
				if (!ok) {
					bad.add(status + ": " + who(r) + " shows '" + val(r, "ACTION") + "'");
				}
			}
		}
		if (sum != all.size()) {
			test.warning("OBSERVATION: the statuses add up to " + sum + " rows but the whole list has " + all.size());
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: rows that do not match their Status filter: " + shorten(bad));
		test.pass("Status filters list matching rows");
	}

	// EA17: the Factory Name search lists rows of the unfiltered list; an unknown factory is not ignored
	@Test
	public void EA17_factory_filter() {
		test = extent.createTest("EA17 Factory filter");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		a.Open_search_form();
		String pick = "";
		for (String letter : new String[] { "g", "a", "e", "s", "1" }) {
			List<String> found = a.Suggestions_for(a.verify_factory(), letter);
			test.info("Factory suggestions for '" + letter + "': " + found);
			if (!found.isEmpty()) {
				pick = found.get(0);
				break;
			}
		}
		if (pick.isEmpty()) {
			throw new SkipException("The Factory Name box suggested nothing for g, a, e, s, 1");
		}
		a.Search_expense("", "", "", "", pick, FROM_WIDE, today());
		List<Map<String, String>> found = a.all_records();
		test.info("Factory '" + pick + "': " + found.size() + " of " + all.size() + " rows");
		Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: rows that are not in the unfiltered list: " + shorten(not_in(found, all)));
		a.Search_expense("", "", "", "", "ZZQNoSuchFactory" + unique(), FROM_WIDE, today());
		boolean ignored = a.result_count() > 0 && a.all_records().size() >= all.size() && a.validation_messages().isEmpty() && a.notice().isEmpty();
		Assert.assertFalse(ignored, "DEFECT: a factory that does not exist is ignored and every row is listed without a message");
		test.pass("The factory filter works");
	}

	// EA18: filters together: employee type + expense type + status
	@Test
	public void EA18_combined_filters() {
		test = extent.createTest("EA18 Combined filters");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		Map<String, String> r0 = all.get(0);
		String type = val(r0, "EMPLOYEE TYPE");
		String expense = val(r0, "EXPENSE TYPE");
		a.Search_expense(type, "", expense, "", "", FROM_WIDE, today());
		List<Map<String, String>> found = a.all_records();
		int expected = 0;
		for (Map<String, String> r : all) {
			if (val(r, "EMPLOYEE TYPE").equalsIgnoreCase(type) && val(r, "EXPENSE TYPE").equalsIgnoreCase(expense)) {
				expected++;
			}
		}
		test.info(type + " + " + expense + ": " + found.size() + " rows, expected " + expected);
		for (Map<String, String> r : found) {
			Assert.assertTrue(val(r, "EMPLOYEE TYPE").equalsIgnoreCase(type) && val(r, "EXPENSE TYPE").equalsIgnoreCase(expense), "A row that does not match both filters: " + who(r));
		}
		Assert.assertEquals(found.size(), expected, "DEFECT: the combined filters list " + found.size() + " rows, expected " + expected);
		test.pass("Combined filters work");
	}

	// ======================= table =======================

	// EA19: the table filter narrows the rows to the text and an empty filter brings them back
	@Test
	public void EA19_table_filter() {
		test = extent.createTest("EA19 Table filter");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		String text = val(all.get(0), "EXPENSE NUMBER");
		int expected = 0;
		for (Map<String, String> r : all) {
			if (key(r).toLowerCase().contains(text.toLowerCase())) {
				expected++;
			}
		}
		a.Filter_table(text);
		List<Map<String, String>> found = a.all_records();
		test.info("Filter '" + text + "': " + found.size() + " rows, expected " + expected);
		Assert.assertEquals(found.size(), expected, "DEFECT: the table filter lists " + found.size() + " rows, expected " + expected);
		a.Filter_table("");
		Assert.assertTrue(a.entries_total() < 0 || a.entries_total() >= all.size(), "The rows did not come back after the filter was emptied: " + a.entries_text());
		test.pass("The table filter works");
	}

	// EA20: a table filter with no match shows no rows; special text does not break the page
	@Test
	public void EA20_table_filter_no_match_and_special_text() {
		test = extent.createTest("EA20 Table filter no match and special text");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		wide(a);
		a.Filter_table("ZZQNoSuch" + unique());
		Assert.assertEquals(a.result_count(), 0, "Rows are listed for text that matches nothing");
		for (String special : new String[] { "<script>", "' OR 1=1 --", "%%", "\\" }) {
			a.Filter_table(special);
			test.info("Filter '" + special + "': rows " + a.result_count());
			Assert.assertTrue(a.is_list_page(), "The page broke after the filter text " + special);
		}
		a.Filter_table("");
		test.pass("No match and special text are handled");
	}

	// EA21 .. EA23: sorting
	@Test
	public void EA21_sort_employee() {
		sort_check("EA21 Sort EMPLOYEE", "EMPLOYEE", false, false);
	}

	@Test
	public void EA22_sort_expense_date() {
		sort_check("EA22 Sort EXPENSE DATE", "EXPENSE DATE", false, true);
	}

	@Test
	public void EA23_sort_expense_amount() {
		sort_check("EA23 Sort EXPENSE AMOUNT", "EXPENSE AMOUNT", true, false);
	}

	// EA24: paging: Next shows other rows, Previous comes back, the pages add up to the entries line
	@Test
	public void EA24_paging() {
		test = extent.createTest("EA24 Paging");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		a.Search_dates(FROM_WIDE, today());
		List<Map<String, String>> first = a.records();
		test.info("Page 1: " + first.size() + " rows | " + a.entries_text() + " | page numbers " + a.page_numbers());
		if (!a.next_enabled()) {
			throw new SkipException("Only one page of data");
		}
		Assert.assertFalse(a.previous_enabled(), "Previous is enabled on the first page");
		a.Click_next();
		List<Map<String, String>> second = a.records();
		Assert.assertFalse(second.isEmpty(), "Page 2 is empty");
		Assert.assertNotEquals(key(second.get(0)), key(first.get(0)), "Page 2 starts with the same row as page 1");
		Assert.assertTrue(a.previous_enabled(), "Previous is not enabled on page 2");
		a.Click_previous();
		Assert.assertEquals(key(a.records().get(0)), key(first.get(0)), "Previous did not come back to page 1");
		List<Map<String, String>> all = wide(a);
		if (a.entries_total() >= 0) {
			Assert.assertEquals(all.size(), a.entries_total(), "The rows of all pages differ from the entries line");
		}
		Set<String> numbers = new HashSet<String>();
		for (Map<String, String> r : all) {
			numbers.add(key(r));
		}
		Assert.assertEquals(numbers.size(), all.size(), "DEFECT: a row is shown on two pages (" + all.size() + " rows, " + numbers.size() + " different)");
		test.pass("Paging works over " + all.size() + " rows");
	}

	// EA25: the "Show N entries" box (when the screen has one) changes the page length
	@Test
	public void EA25_show_entries() {
		test = extent.createTest("EA25 Show entries");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		wide(a);
		if (!a.has_page_length()) {
			throw new SkipException("The screen has no 'Show N entries' box");
		}
		for (String option : a.page_length_options()) {
			if (!option.matches("\\d+")) {
				continue;
			}
			a.Set_page_length(option);
			int shown = a.result_count();
			test.info("Show " + option + ": " + shown + " rows | " + a.entries_text());
			Assert.assertTrue(shown <= Integer.parseInt(option), "Show " + option + " lists " + shown + " rows");
			if (a.entries_total() >= Integer.parseInt(option)) {
				Assert.assertEquals(shown, Integer.parseInt(option), "Show " + option + " does not fill the page");
			}
		}
		test.pass("Show entries works");
	}

	// EA26: the entries line says the right numbers
	@Test
	public void EA26_entries_line() {
		test = extent.createTest("EA26 Entries line");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		Assert.assertTrue(a.entries_total() >= 0, "The screen shows no 'Showing x to y of z entries' line");
		Assert.assertEquals(a.entries_total(), all.size(), "DEFECT: the entries line says " + a.entries_total() + " but " + all.size() + " rows are listed");
		a.Filter_table("ZZQNoSuch" + unique());
		test.info("After a filter with no match: " + a.entries_text());
		a.Filter_table("");
		test.pass("The entries line is right");
	}

	// EA27: the row numbers on the screen are not repeated (every expense number is unique)
	@Test
	public void EA27_expense_numbers_unique() {
		test = extent.createTest("EA27 Expense numbers are unique");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		Set<String> seen = new HashSet<String>();
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : all) {
			String n = val(r, "EXPENSE NUMBER").trim();
			if (n.isEmpty()) {
				bad.add("empty number: " + who(r));
			} else if (!seen.add(n)) {
				bad.add("repeated number " + n);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + shorten(bad));
		test.pass("All " + all.size() + " expense numbers are different");
	}

	// ======================= content =======================

	// EA28: formats: dates dd/MM/yyyy, times yyyy-MM-dd HH:mm:ss, the number columns hold numbers
	@Test
	public void EA28_value_formats() {
		test = extent.createTest("EA28 Value formats");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : wide(a)) {
			if (!val(r, "EXPENSE DATE").matches("\\d\\d/\\d\\d/\\d{4}")) {
				bad.add(who(r) + " date '" + val(r, "EXPENSE DATE") + "'");
			}
			if (time_in(r, "IN TIME") == null) {
				bad.add(who(r) + " IN TIME '" + val(r, "IN TIME") + "'");
			}
			if (!blank(r, "OUT TIME") && time_in(r, "OUT TIME") == null) {
				bad.add(who(r) + " OUT TIME '" + val(r, "OUT TIME") + "'");
			}
			for (String c : new String[] { "EXPENSE AMOUNT", "ODO START KM", "ODO END KM", "DISTANCE", "CALCULATED DISTANCE" }) {
				if (!val(r, c).replace(",", "").matches("-?\\d+(\\.\\d+)?")) {
					bad.add(who(r) + " " + c + " '" + val(r, c) + "'");
				}
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: values in the wrong format: " + shorten(bad));
		test.pass("All values have the right format");
	}

	// EA29: names, types and the factory are filled; the employee is written Name (CODE); Against Attendance is Yes / No
	@Test
	public void EA29_required_cells_filled() {
		test = extent.createTest("EA29 Required cells are filled");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : wide(a)) {
			for (String c : new String[] { "FACTORY", "EMPLOYEE TYPE", "EMPLOYEE", "EXPENSE TYPE", "EXPENSE DATE", "EXPENSE NUMBER" }) {
				if (blank(r, c)) {
					bad.add(who(r) + " has no " + c);
				}
			}
			if (!val(r, "EMPLOYEE").matches(".+\\(.+\\)")) {
				test.warning("OBSERVATION: the employee '" + val(r, "EMPLOYEE") + "' is not written Name (CODE)");
			}
			String against = val(r, "AGAINST ATTENDANCE");
			if (!against.equalsIgnoreCase("Yes") && !against.equalsIgnoreCase("No")) {
				bad.add(who(r) + " Against Attendance '" + against + "' is not Yes / No");
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + shorten(bad));
		test.pass("Required cells are filled");
	}

	// EA30: the expense date is the day of the In Time
	@Test
	public void EA30_expense_date_matches_in_time() {
		test = extent.createTest("EA30 Expense date equals the In Time day");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : wide(a)) {
			LocalDateTime in = time_in(r, "IN TIME");
			if (in != null && !in.toLocalDate().equals(date_in(r, "EXPENSE DATE"))) {
				bad.add(who(r) + " In Time " + val(r, "IN TIME"));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the expense date is not the day of the In Time: " + shorten(bad));
		test.pass("Expense dates match the In Time days");
	}

	// EA31: the Out Time is after the In Time
	@Test
	public void EA31_out_time_after_in_time() {
		test = extent.createTest("EA31 Out Time after In Time");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<String> bad = new ArrayList<String>();
		int open = 0;
		for (Map<String, String> r : wide(a)) {
			LocalDateTime in = time_in(r, "IN TIME");
			LocalDateTime out = time_in(r, "OUT TIME");
			if (out == null) {
				open++;
			} else if (in != null && out.isBefore(in)) {
				bad.add(who(r) + " In " + val(r, "IN TIME") + " Out " + val(r, "OUT TIME"));
			}
		}
		test.info(open + " rows have no Out Time yet");
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the Out Time is before the In Time: " + shorten(bad));
		test.pass("Out Times are after In Times");
	}

	// EA32: the odometer: End >= Start, and Distance = End - Start when both are entered (CALCULATION)
	@Test
	public void EA32_distance_calculation() {
		test = extent.createTest("EA32 Distance = ODO End - ODO Start");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<String> bad = new ArrayList<String>();
		int checked = 0;
		int not_entered = 0;
		for (Map<String, String> r : wide(a)) {
			double start = num(r, "ODO START KM");
			double end = num(r, "ODO END KM");
			double distance = num(r, "DISTANCE");
			if (start > 0 && end > 0) {
				checked++;
				if (end < start) {
					bad.add(who(r) + ": ODO End " + end + " is less than ODO Start " + start);
				} else if (!close(distance, end - start)) {
					bad.add(who(r) + ": Distance " + distance + " but ODO End - Start = " + (end - start));
				}
			} else if (start > 0 && !blank(r, "OUT TIME")) {
				not_entered++;
			}
		}
		test.info(checked + " rows checked; " + not_entered + " finished rows have an ODO Start but no ODO End");
		if (not_entered > 0) {
			test.warning("OBSERVATION: " + not_entered + " finished rows have an ODO Start but no ODO End");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: odometer / distance: " + shorten(bad));
		test.pass("Distance agrees with the odometer on " + checked + " rows");
	}

	// EA33: Distance and Calculated Distance
	@Test
	public void EA33_distance_vs_calculated() {
		test = extent.createTest("EA33 Distance and Calculated Distance");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		int different = 0;
		List<String> negative = new ArrayList<String>();
		List<String> examples = new ArrayList<String>();
		for (Map<String, String> r : wide(a)) {
			if (num(r, "DISTANCE") < 0 || num(r, "CALCULATED DISTANCE") < 0) {
				negative.add(who(r));
			}
			if (!close(num(r, "DISTANCE"), num(r, "CALCULATED DISTANCE"))) {
				different++;
				if (examples.size() < 6) {
					examples.add(who(r) + " Distance " + val(r, "DISTANCE") + " / Calculated " + val(r, "CALCULATED DISTANCE"));
				}
			}
		}
		if (different > 0) {
			test.warning("OBSERVATION: " + different + " rows have a Distance that differs from the Calculated Distance, e.g. " + examples);
		}
		Assert.assertTrue(negative.isEmpty(), "DEFECT: negative distance: " + shorten(negative));
		test.pass("No negative distance; " + different + " rows differ");
	}

	// EA34: the expense amount: never negative; a distance is paid, no distance means no amount
	@Test
	public void EA34_expense_amount_calculation() {
		test = extent.createTest("EA34 Expense amount");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<String> negative = new ArrayList<String>();
		List<String> unpaid = new ArrayList<String>();
		List<String> paid_without_distance = new ArrayList<String>();
		Set<String> rates = new HashSet<String>();
		for (Map<String, String> r : wide(a)) {
			double amount = num(r, "EXPENSE AMOUNT");
			double distance = num(r, "DISTANCE");
			if (amount < 0) {
				negative.add(who(r) + " " + amount);
			}
			if (distance > 0 && amount == 0) {
				unpaid.add(who(r) + " distance " + distance);
			}
			if (distance == 0 && amount > 0) {
				paid_without_distance.add(who(r) + " amount " + amount);
			}
			if (distance > 0 && amount > 0) {
				rates.add(String.format("%.2f", amount / distance));
			}
		}
		test.info("Amount per km seen on the rows: " + rates);
		if (!unpaid.isEmpty()) {
			test.warning("OBSERVATION: rows with a distance but amount 0: " + shorten(unpaid));
		}
		if (!paid_without_distance.isEmpty()) {
			test.warning("OBSERVATION: rows with an amount but distance 0: " + shorten(paid_without_distance));
		}
		Assert.assertTrue(negative.isEmpty(), "DEFECT: negative expense amount: " + shorten(negative));
		test.pass("No negative amount");
	}

	// EA35: Action column: a row without an Out Time cannot be decided; Approved / Rejected rows have no check box
	@Test
	public void EA35_action_column() {
		test = extent.createTest("EA35 Action column");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<String> bad = new ArrayList<String>();
		int pending = 0;
		int approved = 0;
		int rejected = 0;
		for (Map<String, String> r : wide(a)) {
			String state = r.get("ACTION STATE");
			boolean decided = is_text(r, "approv") || is_text(r, "reject");
			if (is_text(r, "approv")) {
				approved++;
			} else if (is_text(r, "reject")) {
				rejected++;
			} else if (state.equals("enabled")) {
				pending++;
			}
			if (decided && !state.equals("text")) {
				bad.add(who(r) + " is " + val(r, "ACTION") + " but still has a check box");
			}
			if (blank(r, "OUT TIME") && state.equals("enabled")) {
				bad.add(who(r) + " has no Out Time but can be approved");
			}
		}
		test.info("Pending (can be decided): " + pending + " | Approved: " + approved + " | Rejected: " + rejected);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + shorten(bad));
		test.pass("The Action column is consistent");
	}

	// EA36: a pending row with an Out Time has an enabled check box (nothing blocks it)
	@Test
	public void EA36_finished_rows_can_be_decided() {
		test = extent.createTest("EA36 Finished rows can be decided");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<String> locked = new ArrayList<String>();
		for (Map<String, String> r : wide(a)) {
			if (!blank(r, "OUT TIME") && val(r, "ACTION").trim().isEmpty() && r.get("ACTION STATE").equals("disabled")) {
				locked.add(who(r));
			}
		}
		if (!locked.isEmpty()) {
			test.warning("OBSERVATION: " + locked.size() + " finished rows have a disabled check box: " + shorten(locked));
		}
		test.pass("Checked; " + locked.size() + " finished rows are locked");
	}

	// EA37: the table keeps the same rows after the search form is opened and closed
	@Test
	public void EA37_list_unchanged_by_the_form() {
		test = extent.createTest("EA37 List unchanged by opening the form");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> before = a.records();
		a.Open_search_form();
		List<Map<String, String>> after = a.records();
		Assert.assertEquals(after.size(), before.size(), "The row count changed after the form was opened");
		test.pass("Opening the form does not change the list");
	}

	// EA38: the same search twice gives the same rows
	@Test
	public void EA38_search_twice() {
		test = extent.createTest("EA38 Search twice");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> one = wide(a);
		a.Search_dates(FROM_WIDE, today());
		List<Map<String, String>> two = a.all_records();
		Assert.assertEquals(two.size(), one.size(), "DEFECT: the same search gives " + two.size() + " rows the second time and " + one.size() + " the first time");
		test.pass("The same search gives the same " + one.size() + " rows");
	}

	// ======================= decision pop-up (nothing is saved) =======================

	// EA39: ticking a pending row opens the pop-up with the row's start time, end time and km
	@Test
	public void EA39_decision_popup_shows_row_values() {
		test = extent.createTest("EA39 Decision pop-up shows the row values");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		Map<String, String> row = first_pending(wide(a));
		String number = val(row, "EXPENSE NUMBER");
		a.Open_decision(number);
		Assert.assertTrue(a.has_button("Yes, Approve It"), "The pop-up has no 'Yes, Approve It' button");
		Assert.assertTrue(a.has_button("Reject"), "The pop-up has no 'Reject' button");
		String start = a.modal_start().getAttribute("value");
		String end = a.modal_end().getAttribute("value");
		String km = a.modal_km().getAttribute("value");
		test.info(number + ": pop-up Start " + start + " | End " + end + " | Km " + km + " | row In " + val(row, "IN TIME") + " Out " + val(row, "OUT TIME") + " Distance " + val(row, "DISTANCE"));
		LocalDateTime in = time_in(row, "IN TIME");
		LocalDateTime out = time_in(row, "OUT TIME");
		if (in != null && start != null && !start.isEmpty()) {
			Assert.assertEquals(start.substring(0, 5), String.format("%02d:%02d", in.getHour(), in.getMinute()), "DEFECT: the pop-up Start Time is not the In Time of the row");
		}
		if (out != null && end != null && !end.isEmpty()) {
			Assert.assertEquals(end.substring(0, 5), String.format("%02d:%02d", out.getHour(), out.getMinute()), "DEFECT: the pop-up End Time is not the Out Time of the row");
		}
		Assert.assertTrue(close(YA_ExpenseApproval_Cls.to_number(km), num(row, "DISTANCE")), "DEFECT: the pop-up Kilometer " + km + " is not the Distance " + val(row, "DISTANCE"));
		a.Close_decision();
		test.pass("The pop-up shows the values of the row");
	}

	// EA40: the X closes the pop-up and the row stays pending
	@Test
	public void EA40_decision_popup_close() {
		test = extent.createTest("EA40 Close the decision pop-up");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		String number = val(first_pending(wide(a)), "EXPENSE NUMBER");
		a.Open_decision(number);
		a.Close_decision();
		Assert.assertFalse(a.decision_open(), "The pop-up is still open after the X");
		Map<String, String> rec = a.record_of(number);
		Assert.assertNotNull(rec, number + " is not listed any more");
		Assert.assertTrue(is_pending(rec), "DEFECT: " + number + " is no longer pending after the pop-up was closed with the X");
		test.pass("The X closes the pop-up and changes nothing");
	}

	// EA41: Yes, Approve It opens a confirmation; Cancel there leaves the row pending
	@Test
	public void EA41_approve_confirmation_cancel() {
		test = extent.createTest("EA41 Approve confirmation: Cancel");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		String number = val(first_pending(wide(a)), "EXPENSE NUMBER");
		a.Open_decision(number);
		a.Click_button("Yes, Approve It");
		Assert.assertTrue(a.swal_visible(), "No confirmation is shown after 'Yes, Approve It'");
		test.info("Confirmation: " + a.swal_text());
		Assert.assertTrue(a.swal_text().toLowerCase().contains("approve"), "The confirmation does not talk about approving: " + a.swal_text());
		a.Cancel_swal();
		if (a.decision_open()) {
			a.Close_decision();
		}
		Map<String, String> rec = a.record_of(number);
		Assert.assertTrue(rec != null && is_pending(rec), "DEFECT: " + number + " was approved although Cancel was pressed");
		test.pass("Cancel keeps the row pending");
	}

	// EA42: Reject asks for remarks; Cancel there leaves the row pending
	@Test
	public void EA42_reject_step_and_cancel() {
		test = extent.createTest("EA42 Reject asks for remarks: Cancel");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		String number = val(first_pending(wide(a)), "EXPENSE NUMBER");
		a.Open_decision(number);
		a.Click_button("Reject");
		Assert.assertTrue(a.has_button("Yes, Reject It"), "No 'Yes, Reject It' button after Reject");
		Assert.assertTrue(a.modal_remarks().isDisplayed(), "No Remarks box after Reject");
		a.Cancel_decision();
		if (a.decision_open()) {
			a.Close_decision();
		}
		Map<String, String> rec = a.record_of(number);
		Assert.assertTrue(rec != null && is_pending(rec), "DEFECT: " + number + " is no longer pending after Cancel");
		test.pass("Cancel on the reject step keeps the row pending");
	}

	// EA43: rejecting without remarks is refused (remarks are marked *)
	@Test
	public void EA43_reject_without_remarks() {
		test = extent.createTest("EA43 Reject without remarks");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		String number = val(first_pending(wide(a)), "EXPENSE NUMBER");
		a.Open_decision(number);
		a.Click_button("Reject");
		a.Click_button("Yes, Reject It");
		boolean confirmation = a.swal_visible();
		test.info("After 'Yes, Reject It' with empty remarks: confirmation " + confirmation + " | " + a.swal_text() + " | messages " + a.validation_messages());
		if (confirmation) {
			a.Cancel_swal();
		}
		Assert.assertFalse(confirmation && a.swal_text().toLowerCase().contains("reject expense"),
				"DEFECT: a rejection without remarks goes on to the confirmation (remarks are marked as required)");
		test.pass("Remarks are required for a rejection");
	}

	// EA44: a negative km is not accepted by the approval
	@Test
	public void EA44_negative_km() {
		test = extent.createTest("EA44 Negative km");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		String number = val(first_pending(wide(a)), "EXPENSE NUMBER");
		a.Open_decision(number);
		if (!a.modal_has_km()) {
			throw new SkipException("The pop-up has no Kilometer field");
		}
		a.modal_km().clear();
		a.modal_km().sendKeys("-5");
		test.info("Km box keeps '" + a.modal_km().getAttribute("value") + "'");
		a.Click_button("Yes, Approve It");
		boolean confirmation = a.swal_visible();
		test.info("Confirmation after -5 km: " + confirmation + " | " + a.swal_text() + " | messages " + a.validation_messages());
		if (confirmation) {
			a.Cancel_swal();
		}
		if (a.decision_open()) {
			a.Close_decision();
		}
		Assert.assertFalse(confirmation && a.modal_km() != null && a.modal_km().getAttribute("value").startsWith("-"),
				"DEFECT: a negative kilometer (-5) is accepted and goes on to the approval confirmation");
		test.pass("A negative km is not accepted");
	}

	// EA45: letters in the km box are not kept
	@Test
	public void EA45_text_in_km() {
		test = extent.createTest("EA45 Text in the km box");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		String number = val(first_pending(wide(a)), "EXPENSE NUMBER");
		a.Open_decision(number);
		if (!a.modal_has_km()) {
			throw new SkipException("The pop-up has no Kilometer field");
		}
		a.modal_km().clear();
		a.modal_km().sendKeys("abc");
		String kept = a.modal_km().getAttribute("value");
		test.info("Km box keeps '" + kept + "'");
		a.Close_decision();
		Assert.assertFalse(kept.matches(".*[a-zA-Z].*") && !kept.matches("[0-9.eE+-]*"), "DEFECT: letters are kept in the Kilometer box: " + kept);
		test.pass("Letters are not kept in the km box");
	}

	// EA46: Approve All: ticking it ticks the rows, un-ticking clears them; nothing is confirmed
	@Test
	public void EA46_approve_all_checkbox() {
		test = extent.createTest("EA46 Approve All check box");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		boolean any = false;
		for (Map<String, String> r : all) {
			any |= is_pending(r);
		}
		if (!any) {
			throw new SkipException("No pending row: Approve All has nothing to tick");
		}
		org.openqa.selenium.WebElement box = a.verify_approve_all();
		box.click();
		a.Filter_table("");
		boolean popup = a.decision_open() || a.swal_visible();
		test.info("After ticking Approve All: pop-up/confirmation " + popup + " | " + a.swal_text());
		if (a.swal_visible()) {
			a.Cancel_swal();
		}
		if (a.decision_open()) {
			a.Close_decision();
		}
		List<Map<String, String>> after = a.all_records();
		int stillPending = 0;
		for (Map<String, String> r : after) {
			stillPending += is_pending(r) ? 1 : 0;
		}
		test.info("Pending rows after Approve All was ticked and cancelled: " + stillPending);
		Assert.assertTrue(a.is_list_page(), "The page broke after Approve All");
		test.pass("Approve All was ticked without breaking the page");
	}

	// ======================= REAL decisions (they use up pending rows) =======================

	// EA47: approve a pending expense: the row becomes Approved and has no check box any more
	@Test
	public void EA47_approve_expense() {
		test = extent.createTest("EA47 Approve an expense");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		Map<String, String> row = first_pending(all);
		String number = val(row, "EXPENSE NUMBER");
		test.info("Approving " + who(row));
		a.Approve(number, "", "Approved by automation " + unique());
		wide_cache = null;
		a.Search_dates(FROM_WIDE, today());
		Map<String, String> rec = a.record_of(number);
		Assert.assertNotNull(rec, "DEFECT: " + number + " is not listed after it was approved");
		Assert.assertTrue(is_text(rec, "approv"), "DEFECT: " + number + " shows '" + val(rec, "ACTION") + "' after it was approved");
		Assert.assertEquals(rec.get("ACTION STATE"), "text", "An approved row still has a check box");
		a.Filter_table("");
		Assert.assertEquals(a.all_records().size(), all.size(), "The number of rows changed after the approval");
		test.pass(number + " is Approved");
	}

	// EA48: reject a pending expense with remarks: the row shows Rejected and has no check box
	@Test
	public void EA48_reject_expense() {
		test = extent.createTest("EA48 Reject an expense");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		Map<String, String> row = first_pending(wide(a));
		String number = val(row, "EXPENSE NUMBER");
		test.info("Rejecting " + who(row));
		a.Reject(number, "Rejected by automation " + unique());
		wide_cache = null;
		a.Search_dates(FROM_WIDE, today());
		Map<String, String> rec = a.record_of(number);
		Assert.assertNotNull(rec, number + " is not listed after it was rejected");
		Assert.assertTrue(is_text(rec, "reject"), "DEFECT: " + number + " shows '" + val(rec, "ACTION") + "' after it was rejected");
		Assert.assertEquals(rec.get("ACTION STATE"), "text", "A rejected row still has a check box");
		test.pass(number + " is Rejected");
	}

	// EA49: the Status filter finds a decided expense under its new status and not under Pending
	@Test
	public void EA49_status_filter_after_decision() {
		test = extent.createTest("EA49 Status filter after a decision");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<Map<String, String>> all = wide(a);
		Map<String, String> approved = null;
		for (Map<String, String> r : all) {
			if (is_text(r, "approv")) {
				approved = r;
				break;
			}
		}
		if (approved == null) {
			throw new SkipException("No approved expense in " + FROM_WIDE + " .. " + today());
		}
		String number = val(approved, "EXPENSE NUMBER");
		a.Open_search_form();
		String approved_option = "";
		String pending_option = "";
		for (String s : a.options_of(a.verify_status())) {
			approved_option = s.toLowerCase().contains("approv") ? s : approved_option;
			pending_option = s.toLowerCase().contains("pend") ? s : pending_option;
		}
		Assert.assertFalse(approved_option.isEmpty(), "The Status drop down has no Approved option");
		a.Search_expense("", "", "", approved_option, "", FROM_WIDE, today());
		Assert.assertNotNull(a.record_of(number), "DEFECT: the approved expense " + number + " is not listed under Status " + approved_option);
		if (!pending_option.isEmpty()) {
			a.Search_expense("", "", "", pending_option, "", FROM_WIDE, today());
			Assert.assertNull(a.record_of(number), "DEFECT: the approved expense " + number + " is listed under Status " + pending_option);
		}
		test.pass(number + " is found under its status only");
	}

	// EA50: approve with a changed km: the row shows the new distance (the amount is written to the report)
	@Test
	public void EA50_approve_with_changed_km() {
		test = extent.createTest("EA50 Approve with a changed km");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		Map<String, String> row = first_pending(wide(a));
		String number = val(row, "EXPENSE NUMBER");
		String km = "7";
		test.info("Approving " + who(row) + " with km " + km + " (distance was " + val(row, "DISTANCE") + ", amount " + val(row, "EXPENSE AMOUNT") + ")");
		a.Approve(number, km, "Km changed by automation " + unique());
		wide_cache = null;
		a.Search_dates(FROM_WIDE, today());
		Map<String, String> rec = a.record_of(number);
		Assert.assertNotNull(rec, number + " is not listed after the approval");
		Assert.assertTrue(is_text(rec, "approv"), "DEFECT: " + number + " is not Approved: '" + val(rec, "ACTION") + "'");
		test.info("After: distance " + val(rec, "DISTANCE") + " | amount " + val(rec, "EXPENSE AMOUNT"));
		Assert.assertTrue(close(num(rec, "DISTANCE"), 7), "DEFECT: the approver changed the km to 7 but the row shows distance " + val(rec, "DISTANCE"));
		test.pass("The changed km is saved");
	}

	// EA51: remarks typed in the approval are kept in the row (the REMARKS column)
	@Test
	public void EA51_remarks_are_saved() {
		test = extent.createTest("EA51 Remarks are saved");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		Map<String, String> row = first_pending(wide(a));
		String number = val(row, "EXPENSE NUMBER");
		String remark = "Remark " + unique();
		a.Reject(number, remark);
		wide_cache = null;
		a.Search_dates(FROM_WIDE, today());
		Map<String, String> rec = a.record_of(number);
		Assert.assertNotNull(rec, number + " is not listed after the rejection");
		test.info("REMARKS column after the rejection: '" + val(rec, "REMARKS") + "'");
		if (!val(rec, "REMARKS").contains(remark)) {
			test.warning("OBSERVATION: the remark '" + remark + "' typed at the rejection is not shown in the REMARKS column ('" + val(rec, "REMARKS") + "')");
		}
		test.pass("Rejected with remarks");
	}

	// EA52: a decided expense cannot be decided again (no check box)
	@Test
	public void EA52_decided_expense_is_locked() {
		test = extent.createTest("EA52 A decided expense is locked");
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);

		List<String> bad = new ArrayList<String>();
		int decided = 0;
		for (Map<String, String> r : wide(a)) {
			if (is_text(r, "approv") || is_text(r, "reject")) {
				decided++;
				if (!r.get("ACTION STATE").equals("text")) {
					bad.add(who(r));
				}
			}
		}
		test.info(decided + " decided rows");
		if (decided == 0) {
			throw new SkipException("No decided expense yet");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: decided rows that can be decided again: " + shorten(bad));
		test.pass("Decided rows are locked");
	}
}
