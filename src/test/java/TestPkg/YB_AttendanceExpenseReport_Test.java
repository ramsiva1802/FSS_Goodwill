package TestPkg;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import PagePkg.YB_AttendanceExpenseReport_Cls;

/*
 * Attendance Expense Reports (Reports > Attendance Expense Reports), logged in as the web user - tests AR01 .. AR36.
 * A read only report: one line per APPROVED attendance expense with the odometer readings, the km claimed, the GPS
 * estimate, the rate per km and the amount. Nothing is created or changed.
 * The CALCULATION tests (AR26 .. AR31) read every line of every page and check
 *   KM CLAIMED = ODO END KM - ODO START KM and EXPENSE AMOUNT = KM CLAIMED x RATEPERKM (PA and DA may be added).
 * Cells are read by column name. "DEFECT:" = the site is wrong, "OBSERVATION:" = something to look at.
 */
public class YB_AttendanceExpenseReport_Test extends ExpenseTestBase {

	static final String[] HEADERS = { "DATE", "SALES PERSON NAME", "DESIGNATION", "FACTORY", "EXPENSE NAME", "IN TIME", "OUT TIME",
			"ODO START KM", "IMAGE", "ODO END KM", "KM CLAIMED", "GPS KM ESTIMATE", "RATEPERKM", "EXPENSE AMOUNT", "PA", "POINTS",
			"DA", "LEAVE REQUESTED" };

	// every test starts with a fresh Attendance Expense Reports screen
	@BeforeMethod
	public void open_screen() {
		if (screen_is_clean()) {
			return;
		}
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);
		open_module("Reports", "Attendance Expense", () -> r.is_list_page());
		remember_clean_screen();
	}

	// ---------------------------------- helpers ----------------------------------

	// the lines of every page are read ONCE and kept (the report is read only); the search itself is still done
	private static List<Map<String, String>> wide_cache;

	@BeforeClass
	public void reset_wide_cache() {
		wide_cache = null;
	}

	private List<Map<String, String>> wide(YB_AttendanceExpenseReport_Cls r) {
		r.Search_dates(FROM_WIDE, today());
		if (wide_cache == null) {
			wide_cache = r.all_records();
		}
		List<Map<String, String>> all = wide_cache;
		test.info(FROM_WIDE + " .. " + today() + ": " + all.size() + " lines | " + r.entries_text());
		if (all.isEmpty()) {
			throw new SkipException("No line is listed for " + FROM_WIDE + " .. " + today());
		}
		return all;
	}

	private String header_of(YB_AttendanceExpenseReport_Cls r, String name) {
		for (String h : r.headers()) {
			if (h.startsWith(name)) {
				return h;
			}
		}
		throw new AssertionError("Column header missing: " + name);
	}

	private int direction(List<String> values, boolean as_number) {
		boolean asc = true;
		boolean desc = true;
		for (int i = 1; i < values.size(); i++) {
			int c = as_number ? Double.compare(YB_AttendanceExpenseReport_Cls.to_number(values.get(i - 1)), YB_AttendanceExpenseReport_Cls.to_number(values.get(i)))
					: values.get(i - 1).compareToIgnoreCase(values.get(i));
			asc &= c <= 0;
			desc &= c >= 0;
		}
		return asc && desc ? 2 : asc ? 1 : desc ? -1 : 0;
	}

	private List<String> page_values(YB_AttendanceExpenseReport_Cls r, String column, boolean date) {
		List<String> out = new ArrayList<String>();
		for (Map<String, String> rec : r.records()) {
			String v = val(rec, column);
			if (date && v.matches("\\d\\d/\\d\\d/\\d{4}")) {
				v = v.substring(6) + v.substring(3, 5) + v.substring(0, 2);
			}
			out.add(v);
		}
		return out;
	}

	private void sort_check(String title, String column, boolean as_number, boolean date) {
		test = extent.createTest(title);
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);
		wide(r);
		String h = header_of(r, column);
		r.Click_header(h);
		int first = direction(page_values(r, column, date), as_number);
		r.Click_header(h);
		int second = direction(page_values(r, column, date), as_number);
		test.info(column + ": after click 1 direction " + first + ", after click 2 direction " + second + " (1 up, -1 down, 2 equal, 0 mixed)");
		if (first == 2 || second == 2) {
			throw new SkipException("All values of " + column + " on the page are equal: sorting cannot be seen");
		}
		if (first == 0 || second == 0) {
			test.info("Values on the page after the last click: " + page_values(r, column, date));
		}
		Assert.assertTrue(first != 0 && second != 0, "DEFECT: the " + column + " column is not sorted after a click on its header");
		Assert.assertEquals(first, -second, "DEFECT: a second click on the " + column + " header does not reverse the order");
		test.pass(column + " sorts both ways");
	}

	// ======================= screen and search form =======================

	// AR01: Search, Export, the table filter and the column headers
	@Test
	public void AR01_screen_elements() {
		test = extent.createTest("AR01 Attendance Expense Reports screen elements");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		Assert.assertTrue(r.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(r.verify_export().isDisplayed(), "Export button is missing");
		Assert.assertTrue(r.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = r.headers();
		test.info("Headers: " + headers + " | " + r.entries_text());
		for (String h : HEADERS) {
			boolean found = false;
			for (String x : headers) {
				found |= x.startsWith(h);
			}
			Assert.assertTrue(found, "Column header missing: " + h);
		}
		test.pass("Screen elements are displayed");
	}

	// AR02: the Search button opens the form with its six fields, Search and Clear; both dates are today
	@Test
	public void AR02_search_form_opens() {
		test = extent.createTest("AR02 Search form opens");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		r.Open_search_form();
		Assert.assertTrue(r.verify_hierarchy_type().isDisplayed(), "Hierarchy Type is missing");
		Assert.assertTrue(r.verify_hierarchy_name().isDisplayed(), "Hierarchy Name is missing");
		Assert.assertTrue(r.verify_employee_type().isDisplayed(), "Employee Type is missing");
		Assert.assertTrue(r.verify_employee_name().isDisplayed(), "Employee Name is missing");
		Assert.assertTrue(r.verify_from().isDisplayed(), "From Date is missing");
		Assert.assertTrue(r.verify_to().isDisplayed(), "To Date is missing");
		Assert.assertTrue(r.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertTrue(r.verify_clear().isDisplayed(), "The Clear button is missing");
		test.info("From " + r.verify_from().getAttribute("value") + " | To " + r.verify_to().getAttribute("value"));
		Assert.assertEquals(r.verify_from().getAttribute("value"), today(), "Default From Date");
		Assert.assertEquals(r.verify_to().getAttribute("value"), today(), "Default To Date");
		test.pass("Search form is displayed with its defaults");
	}

	// AR03: the options of the two drop downs are written to the report
	@Test
	public void AR03_dropdown_options() {
		test = extent.createTest("AR03 Drop down options");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		r.Open_search_form();
		List<String> hierarchy = r.options_of(r.verify_hierarchy_type());
		List<String> types = r.options_of(r.verify_employee_type());
		test.info("Hierarchy Type (" + hierarchy.size() + "): " + hierarchy);
		test.info("Employee Type (" + types.size() + "): " + types);
		Assert.assertFalse(hierarchy.isEmpty(), "The Hierarchy Type drop down has no value");
		Assert.assertFalse(types.isEmpty(), "The Employee Type drop down has no value");
		test.pass("Drop downs are filled");
	}

	// AR04: the list that opens with the screen only has today's lines
	@Test
	public void AR04_default_list() {
		test = extent.createTest("AR04 Default list is today");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = r.all_records();
		test.info("Default list: " + all.size() + " lines | " + r.entries_text());
		if (all.isEmpty()) {
			throw new SkipException("No approved expense for today");
		}
		for (Map<String, String> rec : all) {
			Assert.assertEquals(val(rec, "DATE"), today(), "DEFECT: the default list shows a line of another day: " + who(rec));
		}
		test.pass("The default list shows only today");
	}

	// ======================= dates =======================

	// AR05: a wide date range lists only lines inside the range
	@Test
	public void AR05_search_wide_date_range() {
		test = extent.createTest("AR05 Search a wide date range");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		LocalDate from = LocalDate.parse(FROM_WIDE, DATE);
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> rec : all) {
			LocalDate d = date_in(rec, "DATE");
			if (d.isBefore(from) || d.isAfter(LocalDate.now())) {
				bad.add(val(rec, "DATE") + " " + who(rec));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: lines outside the date range: " + shorten(bad));
		if (r.entries_total() >= 0) {
			Assert.assertEquals(all.size(), r.entries_total(), "The lines of all pages differ from the entries line");
		}
		test.pass("Wide range works: " + all.size() + " lines");
	}

	// AR06: one single day lists every line of that day (the To Date day is included)
	@Test
	public void AR06_search_single_day() {
		test = extent.createTest("AR06 Search a single day");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		String day = val(all.get(0), "DATE");
		int expected = 0;
		for (Map<String, String> rec : all) {
			expected += val(rec, "DATE").equals(day) ? 1 : 0;
		}
		r.Search_dates(day, day);
		List<Map<String, String>> found = r.all_records();
		test.info("Day " + day + ": " + found.size() + " lines, the wide search has " + expected + " on that day");
		for (Map<String, String> rec : found) {
			Assert.assertEquals(val(rec, "DATE"), day, "A line of another day: " + who(rec));
		}
		Assert.assertEquals(found.size(), expected, "DEFECT: From Date = To Date = " + day + " lists " + found.size() + " lines but the wide search has " + expected + " on that day");
		test.pass("Only " + day + " is listed");
	}

	// AR07: the oldest and the newest day of the data are both included
	@Test
	public void AR07_range_boundaries_included() {
		test = extent.createTest("AR07 Range boundaries");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		LocalDate oldest = LocalDate.MAX;
		LocalDate newest = LocalDate.MIN;
		for (Map<String, String> rec : all) {
			LocalDate d = date_in(rec, "DATE");
			oldest = d.isBefore(oldest) ? d : oldest;
			newest = d.isAfter(newest) ? d : newest;
		}
		r.Search_dates(oldest.format(DATE), newest.format(DATE));
		int found = r.all_records().size();
		test.info("Range " + oldest.format(DATE) + " .. " + newest.format(DATE) + ": " + found + " lines (the wide search has " + all.size() + ")");
		Assert.assertEquals(found, all.size(), "DEFECT: the range from the oldest to the newest day lists " + found + " lines but the wide search lists " + all.size());
		test.pass("Both boundary days are included");
	}

	// AR08: From Date after To Date: nothing (or a message) must be shown
	@Test
	public void AR08_reversed_date_range() {
		test = extent.createTest("AR08 Reversed date range");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		String alert_text = "";
		try {
			r.Search_dates("20/10/2026", "01/10/2026");
		} catch (org.openqa.selenium.UnhandledAlertException e) {
			alert_text = e.getAlertText();
		}
		try {
			alert_text += driver.switchTo().alert().getText();
			driver.switchTo().alert().accept();
		} catch (Exception e) {
			// no alert left
		}
		if (!alert_text.isEmpty()) {
			test.pass("The site refuses a reversed range with the message: " + alert_text);
			return;
		}
		test.info("Rows: " + r.result_count() + " | messages: " + r.validation_messages() + " | notice: " + r.notice());
		Assert.assertTrue(r.result_count() == 0 || !r.validation_messages().isEmpty() || !r.notice().isEmpty(),
				"DEFECT: a reversed date range lists lines without any message");
		test.pass("Reversed range gives nothing or a message");
	}

	// AR09: a range in the future has no line
	@Test
	public void AR09_future_dates() {
		test = extent.createTest("AR09 Future date range");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		String from = LocalDate.now().plusDays(60).format(DATE);
		String to = LocalDate.now().plusDays(90).format(DATE);
		r.Search_dates(from, to);
		test.info(from + " .. " + to + ": rows " + r.result_count() + " | " + r.entries_text());
		Assert.assertEquals(r.result_count(), 0, "Lines are listed for a future range");
		test.pass("A future range lists nothing");
	}

	// AR10: invalid dates are refused
	@Test
	public void AR10_invalid_dates() {
		test = extent.createTest("AR10 Invalid dates");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (String value : new String[] { "abcd", "31/02/2026", "99/99/9999" }) {
			r.Search_dates(value, today());
			r.Open_search_form();
			String kept = r.verify_from().getAttribute("value");
			test.info("From Date '" + value + "' -> box keeps '" + kept + "' | rows " + r.result_count() + " | messages "
					+ r.validation_messages() + " | " + r.entries_text());
			if (!(kept == null || !kept.equals(value) || !r.validation_messages().isEmpty() || r.result_count() == 0 || !r.notice().isEmpty())) {
				bad.add(value);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: invalid From Date values were accepted and lines listed: " + bad);
		test.pass("Invalid dates are not accepted");
	}

	// AR11: a range in the past (the whole of 2025) lists only days of 2025 or nothing
	@Test
	public void AR11_past_range() {
		test = extent.createTest("AR11 Past date range");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		r.Search_dates("01/01/2025", "31/12/2025");
		List<Map<String, String>> found = r.all_records();
		test.info("01/01/2025 .. 31/12/2025: " + found.size() + " lines | " + r.entries_text() + " | messages " + r.validation_messages());
		for (Map<String, String> rec : found) {
			Assert.assertEquals(date_in(rec, "DATE").getYear(), 2025, "DEFECT: the range of 2025 lists " + who(rec));
		}
		test.pass("The past range lists only 2025 (" + found.size() + " lines)");
	}

	// ======================= filters =======================

	// AR12: the Employee Type filter lists only that designation / type
	@Test
	public void AR12_employee_type_filter() {
		test = extent.createTest("AR12 Employee Type filter");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		r.Open_search_form();
		List<String> types = r.options_of(r.verify_employee_type());
		test.info("Employee Type options: " + types);
		int sum = 0;
		for (String type : types) {
			r.Search_report("", "", type, "", FROM_WIDE, today());
			List<Map<String, String>> found = r.all_records();
			sum += found.size();
			test.info("Employee Type " + type + ": " + found.size() + " lines of " + all.size());
			Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: lines under " + type + " that are not in the unfiltered list: " + shorten(not_in(found, all)));
			Assert.assertTrue(found.size() <= all.size(), "DEFECT: " + type + " lists more lines than the unfiltered list");
			if (!found.isEmpty()) {
				Set<String> designations = new TreeSet<String>();
				for (Map<String, String> rec : found) {
					designations.add(val(rec, "DESIGNATION"));
				}
				test.info(type + " -> designations in the lines: " + designations);
			}
		}
		if (sum != all.size()) {
			test.warning("OBSERVATION: the Employee Types add up to " + sum + " lines but the whole list has " + all.size());
		}
		test.pass("Every Employee Type lists only lines of the whole list");
	}

	// AR13: the Employee Name search lists only that employee
	@Test
	public void AR13_employee_name_filter() {
		test = extent.createTest("AR13 Employee Name filter");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		String employee = val(all.get(0), "SALES PERSON NAME");
		String name = employee.contains("(") ? employee.substring(0, employee.indexOf('(')).trim() : employee;
		int expected = 0;
		for (Map<String, String> rec : all) {
			expected += val(rec, "SALES PERSON NAME").equals(employee) ? 1 : 0;
		}
		r.Open_search_form();
		if (r.Suggestions_for(r.verify_employee_name(), name).isEmpty()) {
			throw new SkipException("The Employee Name box shows no suggestion for '" + name + "': the script cannot pick an employee");
		}
		r.Search_report("", "", "", name, FROM_WIDE, today());
		List<Map<String, String>> found = r.all_records();
		test.info(found.size() + " lines found for '" + name + "', the wide search has " + expected + " lines of " + employee);
		Assert.assertTrue(found.size() > 0, "No line was found for " + employee);
		for (Map<String, String> rec : found) {
			Assert.assertTrue(val(rec, "SALES PERSON NAME").toLowerCase().contains(name.toLowerCase()), "A line of another employee: " + who(rec));
		}
		Assert.assertTrue(found.size() >= expected, "DEFECT: the employee search lists " + found.size() + " lines but the wide search has " + expected + " of " + employee);
		test.pass("Only " + employee + " is listed");
	}

	// AR14: the Employee Name box suggests employees; an unknown name is not ignored
	@Test
	public void AR14_employee_suggestions_and_unknown() {
		test = extent.createTest("AR14 Employee suggestions and unknown name");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		String employee = val(all.get(0), "SALES PERSON NAME");
		r.Open_search_form();
		List<String> found = new ArrayList<String>();
		for (int len : new int[] { 1, 2, 3 }) {
			String start = employee.substring(0, Math.min(len, employee.length()));
			found = r.Suggestions_for(r.verify_employee_name(), start);
			test.info("Suggestions for '" + start + "': " + found);
			if (!found.isEmpty()) {
				break;
			}
		}
		if (found.isEmpty()) {
			throw new SkipException("The employee suggestions cannot be read for '" + employee + "'");
		}
		Assert.assertTrue(r.Suggestions_for(r.verify_employee_name(), "ZZQNoSuch" + unique()).isEmpty(), "An employee is suggested for text that matches nothing");
		r.Search_report("", "", "", "ZZQNoSuchEmployee" + unique(), FROM_WIDE, today());
		boolean ignored = r.result_count() > 0 && r.all_records().size() >= all.size() && r.validation_messages().isEmpty() && r.notice().isEmpty();
		Assert.assertFalse(ignored, "DEFECT: an employee that does not exist is ignored and all " + all.size() + " lines are listed without a message");
		test.pass("Suggestions work and an unknown employee does not list everything");
	}

	// AR15: Hierarchy Type and Hierarchy Name filter to lines of the unfiltered list; an unknown name is not ignored
	@Test
	public void AR15_hierarchy_filter() {
		test = extent.createTest("AR15 Hierarchy filter");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		r.Open_search_form();
		List<String> types = r.options_of(r.verify_hierarchy_type());
		if (types.isEmpty()) {
			throw new SkipException("The Hierarchy Type drop down has no value");
		}
		String pick = "";
		r.Search_report(types.get(0), "", "", "", FROM_WIDE, today());
		for (String letter : new String[] { "a", "e", "s", "g", "1" }) {
			List<String> found;
			try {
				found = r.Suggestions_for(r.verify_hierarchy_name(), letter);
			} catch (org.openqa.selenium.TimeoutException e) {
				throw new SkipException("There is no Hierarchy Name box after choosing the type " + types.get(0));
			}
			test.info("Hierarchy Name suggestions for '" + letter + "': " + found);
			if (!found.isEmpty()) {
				pick = found.get(0);
				break;
			}
		}
		if (pick.isEmpty()) {
			throw new SkipException("The Hierarchy Name box suggested nothing for type " + types.get(0));
		}
		r.Search_report(types.get(0), pick, "", "", FROM_WIDE, today());
		List<Map<String, String>> found = r.all_records();
		test.info("Hierarchy " + types.get(0) + " / " + pick + ": " + found.size() + " of " + all.size() + " lines");
		Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: lines that are not in the unfiltered list: " + shorten(not_in(found, all)));
		r.Search_report("", "ZZQNoSuchHierarchy" + unique(), "", "", FROM_WIDE, today());
		boolean ignored = r.result_count() > 0 && r.all_records().size() >= all.size() && r.validation_messages().isEmpty() && r.notice().isEmpty();
		Assert.assertFalse(ignored, "DEFECT: a hierarchy that does not exist is ignored and every line is listed without a message");
		test.pass("The hierarchy filter works");
	}

	// AR16: filters together: employee type + the day of the first line
	@Test
	public void AR16_combined_filters() {
		test = extent.createTest("AR16 Combined filters");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		String designation = val(all.get(0), "DESIGNATION");
		String day = val(all.get(0), "DATE");
		int expected = 0;
		for (Map<String, String> rec : all) {
			expected += val(rec, "DESIGNATION").equalsIgnoreCase(designation) && val(rec, "DATE").equals(day) ? 1 : 0;
		}
		r.Search_report("", "", designation, "", day, day);
		List<Map<String, String>> found = r.all_records();
		test.info(designation + " on " + day + ": " + found.size() + " lines, expected " + expected);
		Assert.assertEquals(found.size(), expected, "DEFECT: the combined filters list " + found.size() + " lines, expected " + expected);
		test.pass("Combined filters work");
	}

	// AR17: Clear brings the form back to today and lists today's lines
	@Test
	public void AR17_clear_button() {
		test = extent.createTest("AR17 Clear button");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		r.Search_report("", "", val(all.get(0), "DESIGNATION"), "", FROM_WIDE, today());
		r.Click_clear();
		r.Open_search_form();
		test.info("After Clear: from " + r.verify_from().getAttribute("value") + " | to " + r.verify_to().getAttribute("value")
				+ " | employee type '" + r.selected(r.verify_employee_type()) + "' | rows " + r.result_count());
		Assert.assertEquals(r.verify_from().getAttribute("value"), today(), "From Date after Clear");
		Assert.assertEquals(r.verify_to().getAttribute("value"), today(), "To Date after Clear");
		Assert.assertTrue(r.verify_employee_name().getAttribute("value").isEmpty(), "Employee Name is not empty after Clear");
		for (Map<String, String> rec : r.all_records()) {
			Assert.assertEquals(val(rec, "DATE"), today(), "DEFECT: after Clear a line of another day is listed: " + who(rec));
		}
		test.pass("Clear resets the form");
	}

	// ======================= table =======================

	// AR18: the table filter narrows the lines to the text and an empty filter brings them back
	@Test
	public void AR18_table_filter() {
		test = extent.createTest("AR18 Table filter");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		String text = val(all.get(0), "SALES PERSON NAME");
		int expected = 0;
		for (Map<String, String> rec : all) {
			expected += key(rec).toLowerCase().contains(text.toLowerCase()) ? 1 : 0;
		}
		r.Filter_table(text);
		List<Map<String, String>> found = r.all_records();
		test.info("Filter '" + text + "': " + found.size() + " lines, expected " + expected);
		Assert.assertEquals(found.size(), expected, "DEFECT: the table filter lists " + found.size() + " lines, expected " + expected);
		r.Filter_table("");
		test.pass("The table filter works");
	}

	// AR19: a table filter with no match shows no lines; special text does not break the page
	@Test
	public void AR19_table_filter_no_match_and_special_text() {
		test = extent.createTest("AR19 Table filter no match and special text");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		wide(r);
		r.Filter_table("ZZQNoSuch" + unique());
		Assert.assertEquals(r.result_count(), 0, "Lines are listed for text that matches nothing");
		for (String special : new String[] { "<script>", "' OR 1=1 --", "%%", "\\" }) {
			r.Filter_table(special);
			test.info("Filter '" + special + "': rows " + r.result_count());
			Assert.assertTrue(r.is_list_page(), "The page broke after the filter text " + special);
		}
		r.Filter_table("");
		test.pass("No match and special text are handled");
	}

	@Test
	public void AR20_sort_sales_person() {
		sort_check("AR20 Sort SALES PERSON NAME", "SALES PERSON NAME", false, false);
	}

	@Test
	public void AR21_sort_date() {
		sort_check("AR21 Sort DATE", "DATE", false, true);
	}

	@Test
	public void AR22_sort_expense_amount() {
		sort_check("AR22 Sort EXPENSE AMOUNT", "EXPENSE AMOUNT", true, false);
	}

	// AR23: paging: Next shows other lines, Previous comes back, the pages add up to the entries line
	@Test
	public void AR23_paging() {
		test = extent.createTest("AR23 Paging");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		r.Search_dates(FROM_WIDE, today());
		List<Map<String, String>> first = r.records();
		test.info("Page 1: " + first.size() + " lines | " + r.entries_text() + " | page numbers " + r.page_numbers());
		if (!r.next_enabled()) {
			throw new SkipException("Only one page of data");
		}
		Assert.assertFalse(r.previous_enabled(), "Previous is enabled on the first page");
		r.Click_next();
		List<Map<String, String>> second = r.records();
		Assert.assertFalse(second.isEmpty(), "Page 2 is empty");
		Assert.assertNotEquals(key(second.get(0)), key(first.get(0)), "Page 2 starts with the same line as page 1");
		r.Click_previous();
		Assert.assertEquals(key(r.records().get(0)), key(first.get(0)), "Previous did not come back to page 1");
		List<Map<String, String>> all = wide(r);
		if (r.entries_total() >= 0) {
			Assert.assertEquals(all.size(), r.entries_total(), "The lines of all pages differ from the entries line");
		}
		test.pass("Paging works over " + all.size() + " lines");
	}

	// AR24: the "Show N entries" box (when the screen has one) changes the page length
	@Test
	public void AR24_show_entries() {
		test = extent.createTest("AR24 Show entries");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		wide(r);
		if (!r.has_page_length()) {
			throw new SkipException("The screen has no 'Show N entries' box");
		}
		for (String option : r.page_length_options()) {
			if (option.matches("\\d+")) {
				r.Set_page_length(option);
				int shown = r.result_count();
				test.info("Show " + option + ": " + shown + " lines | " + r.entries_text());
				Assert.assertTrue(shown <= Integer.parseInt(option), "Show " + option + " lists " + shown + " lines");
			}
		}
		test.pass("Show entries works");
	}

	// AR25: the entries line says the right numbers
	@Test
	public void AR25_entries_line() {
		test = extent.createTest("AR25 Entries line");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> all = wide(r);
		Assert.assertTrue(r.entries_total() >= 0, "The screen shows no 'Showing x to y of z entries' line");
		Assert.assertEquals(r.entries_total(), all.size(), "DEFECT: the entries line says " + r.entries_total() + " but " + all.size() + " lines are listed");
		test.pass("The entries line is right");
	}

	// ======================= content and calculations =======================

	// AR26: KM CLAIMED = ODO END KM - ODO START KM (CALCULATION)
	@Test
	public void AR26_km_claimed_calculation() {
		test = extent.createTest("AR26 KM CLAIMED = ODO END - ODO START");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<String> bad = new ArrayList<String>();
		int checked = 0;
		int open_days = 0;
		for (Map<String, String> rec : wide(r)) {
			double start = num(rec, "ODO START KM");
			double end = num(rec, "ODO END KM");
			double claimed = num(rec, "KM CLAIMED");
			if (end == 0) {
				open_days++;
				continue;
			}
			checked++;
			if (end < start) {
				bad.add(who(rec) + ": ODO End " + end + " is less than ODO Start " + start);
			} else if (!close(claimed, end - start)) {
				bad.add(who(rec) + ": KM Claimed " + claimed + " but ODO End - Start = " + (end - start));
			}
		}
		if (open_days > 0) {
			test.warning("OBSERVATION: " + open_days + " lines have ODO END KM 0 (no end reading) and are not checked");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: KM CLAIMED is not ODO END - ODO START: " + shorten(bad));
		test.pass("KM CLAIMED is right on " + checked + " lines");
	}

	// AR27: EXPENSE AMOUNT = KM CLAIMED x RATEPERKM (PA and DA may be added) (CALCULATION)
	@Test
	public void AR27_expense_amount_calculation() {
		test = extent.createTest("AR27 EXPENSE AMOUNT = KM CLAIMED x RATEPERKM");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<String> bad = new ArrayList<String>();
		int plain = 0;
		int with_allowance = 0;
		int other = 0;
		List<String> examples = new ArrayList<String>();
		for (Map<String, String> rec : wide(r)) {
			double km = num(rec, "KM CLAIMED");
			double rate = num(rec, "RATEPERKM");
			double amount = num(rec, "EXPENSE AMOUNT");
			double pa = num(rec, "PA");
			double da = num(rec, "DA");
			if (km == 0) {
				other++;
				if (examples.size() < 6) {
					examples.add(who(rec) + " " + opt(rec, "EXPENSE NAME") + " " + amount);
				}
				continue;
			}
			if (close(amount, km * rate)) {
				plain++;
			} else if (close(amount, km * rate + pa + da)) {
				with_allowance++;
			} else {
				bad.add(who(rec) + ": amount " + amount + " but " + km + " km x " + rate + " = " + (km * rate) + " (PA " + pa + ", DA " + da + ")");
			}
		}
		test.info(plain + " lines = km x rate; " + with_allowance + " lines = km x rate + PA + DA; " + other + " lines without km (other expense types, not checked), e.g. " + examples);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: EXPENSE AMOUNT is not KM CLAIMED x RATEPERKM: " + shorten(bad));
		test.pass("EXPENSE AMOUNT is right on " + (plain + with_allowance) + " lines");
	}

	// AR28: the rate per km: not negative; the rates used are written to the report
	@Test
	public void AR28_rate_per_km() {
		test = extent.createTest("AR28 Rate per km");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<String> bad = new ArrayList<String>();
		Set<String> rates = new TreeSet<String>();
		for (Map<String, String> rec : wide(r)) {
			double rate = num(rec, "RATEPERKM");
			rates.add(val(rec, "DESIGNATION") + " = " + String.format("%.2f", rate));
			if (rate < 0) {
				bad.add(who(rec) + " rate " + rate);
			}
			if (rate == 0 && num(rec, "KM CLAIMED") > 0) {
				test.warning("OBSERVATION: " + who(rec) + " claims " + val(rec, "KM CLAIMED") + " km but the rate per km is 0");
			}
		}
		test.info("Rates per designation: " + rates);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: negative rate per km: " + shorten(bad));
		test.pass("Rates are not negative");
	}

	// AR29: no negative number in any number column
	@Test
	public void AR29_no_negative_numbers() {
		test = extent.createTest("AR29 No negative numbers");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (Map<String, String> rec : wide(r)) {
			for (String c : new String[] { "ODO START KM", "ODO END KM", "KM CLAIMED", "GPS KM ESTIMATE", "RATEPERKM", "EXPENSE AMOUNT", "PA", "POINTS", "DA" }) {
				if (num(rec, c) < 0) {
					bad.add(who(rec) + " " + c + " " + val(rec, c));
				}
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: negative values: " + shorten(bad));
		test.pass("No negative value");
	}

	// AR30: the km claimed is not far above the GPS estimate (observation only)
	@Test
	public void AR30_claimed_vs_gps() {
		test = extent.createTest("AR30 KM claimed against the GPS estimate");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		int over = 0;
		List<String> examples = new ArrayList<String>();
		List<Map<String, String>> all = wide(r);
		for (Map<String, String> rec : all) {
			double claimed = num(rec, "KM CLAIMED");
			double gps = num(rec, "GPS KM ESTIMATE");
			if (claimed > 0 && claimed > gps * 2 + 1) {
				over++;
				if (examples.size() < 6) {
					examples.add(who(rec) + " claimed " + claimed + " / GPS " + gps);
				}
			}
		}
		if (over > 0) {
			test.warning("OBSERVATION: " + over + " of " + all.size() + " lines claim more than twice the GPS estimate, e.g. " + examples);
		}
		test.pass("Checked " + all.size() + " lines; " + over + " claim far more than the GPS estimate");
	}

	// AR31: the same employee has no repeated line for the same day and In Time
	@Test
	public void AR31_no_duplicate_lines() {
		test = extent.createTest("AR31 No duplicate lines");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		Set<String> seen = new HashSet<String>();
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> rec : wide(r)) {
			String k = key(rec);
			if (!seen.add(k)) {
				bad.add(k);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: repeated lines: " + shorten(bad));
		test.pass("No repeated line");
	}

	// AR32: formats: dates dd/MM/yyyy, times yyyy-MM-dd HH:mm:ss, number columns hold numbers, Leave Requested is Y / N
	@Test
	public void AR32_value_formats() {
		test = extent.createTest("AR32 Value formats");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (Map<String, String> rec : wide(r)) {
			if (!val(rec, "DATE").matches("\\d\\d/\\d\\d/\\d{4}")) {
				bad.add(who(rec) + " date '" + val(rec, "DATE") + "'");
			}
			if (time_in(rec, "IN TIME") == null) {
				bad.add(who(rec) + " IN TIME '" + val(rec, "IN TIME") + "'");
			}
			if (!blank(rec, "OUT TIME") && time_in(rec, "OUT TIME") == null) {
				bad.add(who(rec) + " OUT TIME '" + val(rec, "OUT TIME") + "'");
			}
			for (String c : new String[] { "ODO START KM", "ODO END KM", "KM CLAIMED", "GPS KM ESTIMATE", "RATEPERKM", "EXPENSE AMOUNT", "PA", "POINTS", "DA" }) {
				if (!val(rec, c).replace(",", "").matches("-?\\d+(\\.\\d+)?")) {
					bad.add(who(rec) + " " + c + " '" + val(rec, c) + "'");
				}
			}
			String leave = val(rec, "LEAVE REQUESTED");
			if (!leave.equalsIgnoreCase("Y") && !leave.equalsIgnoreCase("N")) {
				bad.add(who(rec) + " Leave Requested '" + leave + "' is not Y / N");
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: values in the wrong format: " + shorten(bad));
		test.pass("All values have the right format");
	}

	// AR33: the day is the day of the In Time, and the Out Time is after the In Time
	@Test
	public void AR33_dates_and_times_agree() {
		test = extent.createTest("AR33 Date and times agree");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (Map<String, String> rec : wide(r)) {
			LocalDateTime in = time_in(rec, "IN TIME");
			LocalDateTime out = time_in(rec, "OUT TIME");
			if (in != null && !in.toLocalDate().equals(date_in(rec, "DATE"))) {
				bad.add(who(rec) + " In Time " + val(rec, "IN TIME"));
			}
			if (in != null && out != null && out.isBefore(in)) {
				bad.add(who(rec) + " Out " + val(rec, "OUT TIME") + " is before In " + val(rec, "IN TIME"));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + shorten(bad));
		test.pass("Dates and times agree");
	}

	// AR34: name, designation, factory and expense name are filled
	@Test
	public void AR34_required_cells_filled() {
		test = extent.createTest("AR34 Required cells are filled");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (Map<String, String> rec : wide(r)) {
			for (String c : new String[] { "SALES PERSON NAME", "DESIGNATION", "FACTORY", "EXPENSE NAME" }) {
				if (blank(rec, c)) {
					bad.add(who(rec) + " has no " + c);
				}
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + shorten(bad));
		test.pass("Required cells are filled");
	}

	// ======================= export and repeat =======================

	// AR35: Export gives no error (the downloaded file is not opened), also for an empty result
	@Test
	public void AR35_export() {
		test = extent.createTest("AR35 Export");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		wide(r);
		r.Click_export();
		test.info("After Export: notice '" + r.notice() + "'");
		Assert.assertTrue(r.is_list_page(), "The screen broke after Export");
		String from = LocalDate.now().plusDays(60).format(DATE);
		r.Search_dates(from, LocalDate.now().plusDays(90).format(DATE));
		r.Click_export();
		test.info("Export of an empty result: notice '" + r.notice() + "'");
		Assert.assertTrue(r.is_list_page(), "The screen broke after Export of an empty result");
		test.pass("Export works");
	}

	// AR36: the same search twice gives the same lines
	@Test
	public void AR36_search_twice() {
		test = extent.createTest("AR36 Search twice");
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);

		List<Map<String, String>> one = wide(r);
		r.Search_dates(FROM_WIDE, today());
		List<Map<String, String>> two = r.all_records();
		Assert.assertEquals(two.size(), one.size(), "DEFECT: the same search gives " + two.size() + " lines the second time and " + one.size() + " the first time");
		test.pass("The same search gives the same " + one.size() + " lines");
	}
}
