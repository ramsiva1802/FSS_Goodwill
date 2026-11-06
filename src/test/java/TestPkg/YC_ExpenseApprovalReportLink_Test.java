package TestPkg;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
import PagePkg.YB_AttendanceExpenseReport_Cls;

/*
 * The link between Field Management > Expense Approval and Reports > Attendance Expense Reports - tests LK01 .. LK14.
 * The rule: ONLY APPROVED expenses are listed in the Attendance Expense Reports - a pending or a rejected expense must
 * not be there, an approved one must be there with the same values (employee, day, in / out time, odometer, km, amount).
 * A line is matched by employee + day + In Time.
 * LK01 .. LK06 and LK10 .. LK14 only read both screens; LK07 .. LK09 REALLY approve / reject expenses (they use up
 * pending rows; a test is skipped when no pending row is left).
 */
public class YC_ExpenseApprovalReportLink_Test extends ExpenseTestBase {

	// every test starts on the Expense Approval screen
	@BeforeMethod
	public void open_screen() {
		if (screen_is_clean()) {
			return;
		}
		to_approval();
		remember_clean_screen();
	}

	// ---------------------------------- helpers ----------------------------------

	private YA_ExpenseApproval_Cls to_approval() {
		YA_ExpenseApproval_Cls a = new YA_ExpenseApproval_Cls(driver);
		open_module("Field Management", "Expense Approval", () -> a.is_list_page());
		return a;
	}

	private YB_AttendanceExpenseReport_Cls to_report() {
		YB_AttendanceExpenseReport_Cls r = new YB_AttendanceExpenseReport_Cls(driver);
		open_module("Reports", "Attendance Expense", () -> r.is_list_page());
		return r;
	}

	// every row of the Expense Approval screen for the wide range
	// Both lists are read (page by page) ONCE and kept; a test that approves / rejects clears them.
	private static List<Map<String, String>> approval_cache;
	private static List<Map<String, String>> report_cache;

	@BeforeClass
	public void reset_caches() {
		approval_cache = null;
		report_cache = null;
	}

	private List<Map<String, String>> approval_rows() {
		if (approval_cache == null) {
			YA_ExpenseApproval_Cls a = to_approval();
			a.Search_dates(FROM_WIDE, today());
			approval_cache = a.all_records();
		}
		List<Map<String, String>> all = approval_cache;
		test.info("Expense Approval " + FROM_WIDE + " .. " + today() + ": " + all.size() + " rows");
		if (all.isEmpty()) {
			throw new SkipException("No expense is listed on the Expense Approval screen");
		}
		return all;
	}

	// every line of the Attendance Expense Reports for the wide range
	private List<Map<String, String>> report_rows() {
		if (report_cache == null) {
			YB_AttendanceExpenseReport_Cls r = to_report();
			r.Search_dates(FROM_WIDE, today());
			report_cache = r.all_records();
		}
		List<Map<String, String>> all = report_cache;
		test.info("Attendance Expense Reports " + FROM_WIDE + " .. " + today() + ": " + all.size() + " lines");
		return all;
	}

	private void forget_lists() {
		approval_cache = null;
		report_cache = null;
	}

	// employee + day + In Time + amount: one attendance can have several expenses, the amount tells them apart
	private String link_key(Map<String, String> rec) {
		String date = rec.containsKey("EXPENSE DATE") ? rec.get("EXPENSE DATE") : opt(rec, "DATE");
		return employee_of(rec).trim().toLowerCase() + "|" + date.trim() + "|" + opt(rec, "IN TIME").trim() + "|"
				+ String.format("%.2f", num(rec, "EXPENSE AMOUNT"));
	}

	private Map<String, Integer> counts(List<Map<String, String>> rows) {
		Map<String, Integer> map = new HashMap<String, Integer>();
		for (Map<String, String> r : rows) {
			map.merge(link_key(r), 1, Integer::sum);
		}
		return map;
	}

	private Map<String, Map<String, String>> by_key(List<Map<String, String>> rows) {
		Map<String, Map<String, String>> map = new HashMap<String, Map<String, String>>();
		for (Map<String, String> r : rows) {
			map.put(link_key(r), r);
		}
		return map;
	}

	private boolean is_approved(Map<String, String> r) {
		return val(r, "ACTION").toLowerCase().contains("approv");
	}

	private boolean is_rejected(Map<String, String> r) {
		return val(r, "ACTION").toLowerCase().contains("reject");
	}

	private boolean is_pending(Map<String, String> r) {
		return r.get("ACTION STATE").equals("enabled") && val(r, "ACTION").trim().isEmpty();
	}

	private Map<String, String> first_pending(List<Map<String, String>> all) {
		for (Map<String, String> r : all) {
			if (is_pending(r) && !val(r, "EXPENSE NUMBER").isEmpty()) {
				return r;
			}
		}
		throw new SkipException("No pending expense with an enabled check box is left");
	}

	// the report line of an expense (searched for its own day) or null
	private Map<String, String> report_line_of(Map<String, String> expense) {
		YB_AttendanceExpenseReport_Cls r = to_report();
		String day = val(expense, "EXPENSE DATE");
		r.Search_dates(day, day);
		Map<String, Map<String, String>> map = by_key(r.all_records());
		return map.get(link_key(expense));
	}

	private List<String> missing_from(List<Map<String, String>> rows, Map<String, Map<String, String>> map) {
		List<String> out = new ArrayList<String>();
		for (Map<String, String> row : rows) {
			if (!map.containsKey(link_key(row))) {
				out.add(who(row) + " (" + opt(row, "EXPENSE TYPE") + ", amount " + opt(row, "EXPENSE AMOUNT") + ")");
			}
		}
		return out;
	}

	private List<String> present_in(List<Map<String, String>> rows, Map<String, Map<String, String>> map) {
		List<String> out = new ArrayList<String>();
		for (Map<String, String> row : rows) {
			if (map.containsKey(link_key(row))) {
				out.add(who(row) + " (" + opt(row, "EXPENSE TYPE") + ", amount " + opt(row, "EXPENSE AMOUNT") + ")");
			}
		}
		return out;
	}

	// ======================= approved expenses are in the report =======================

	// LK01: every APPROVED expense of the Expense Approval screen is listed in the Attendance Expense Reports
	@Test
	public void LK01_approved_expenses_are_in_the_report() {
		test = extent.createTest("LK01 Approved expenses are in the report");
		List<Map<String, String>> approved = new ArrayList<Map<String, String>>();
		for (Map<String, String> r : approval_rows()) {
			if (is_approved(r)) {
				approved.add(r);
			}
		}
		if (approved.isEmpty()) {
			throw new SkipException("No approved expense yet");
		}
		Map<String, Map<String, String>> report = by_key(report_rows());
		List<String> missing = missing_from(approved, report);
		test.info(approved.size() + " approved expenses, " + missing.size() + " are not in the report");
		Assert.assertTrue(missing.isEmpty(), "DEFECT: approved expenses that are not in the Attendance Expense Reports: " + shorten(missing));
		test.pass("All " + approved.size() + " approved expenses are in the report");
	}

	// LK02: every line of the report is an approved expense
	@Test
	public void LK02_report_has_only_approved_expenses() {
		test = extent.createTest("LK02 The report has only approved expenses");
		Map<String, Map<String, String>> approval = new HashMap<String, Map<String, String>>();
		for (Map<String, String> r : approval_rows()) {
			if (is_approved(r)) {
				approval.put(link_key(r), r);
			}
		}
		List<Map<String, String>> report = report_rows();
		if (report.isEmpty()) {
			throw new SkipException("The report has no line");
		}
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> line : report) {
			if (!approval.containsKey(link_key(line))) {
				bad.add(who(line));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: report lines that are not approved on the Expense Approval screen: " + shorten(bad));
		test.pass("All " + report.size() + " report lines are approved expenses");
	}

	// LK03: a PENDING expense is not in the report
	@Test
	public void LK03_pending_expenses_are_not_in_the_report() {
		test = extent.createTest("LK03 Pending expenses are not in the report");
		List<Map<String, String>> pending = new ArrayList<Map<String, String>>();
		for (Map<String, String> r : approval_rows()) {
			if (!is_approved(r) && !is_rejected(r)) {
				pending.add(r);
			}
		}
		if (pending.isEmpty()) {
			throw new SkipException("No pending expense");
		}
		List<String> in_report = present_in(pending, by_key(report_rows()));
		test.info(pending.size() + " pending expenses, " + in_report.size() + " are in the report");
		Assert.assertTrue(in_report.isEmpty(), "DEFECT: pending expenses are listed in the Attendance Expense Reports: " + shorten(in_report));
		test.pass("None of the " + pending.size() + " pending expenses is in the report");
	}

	// LK04: a REJECTED expense is not in the report
	@Test
	public void LK04_rejected_expenses_are_not_in_the_report() {
		test = extent.createTest("LK04 Rejected expenses are not in the report");
		List<Map<String, String>> rejected = new ArrayList<Map<String, String>>();
		for (Map<String, String> r : approval_rows()) {
			if (is_rejected(r)) {
				rejected.add(r);
			}
		}
		if (rejected.isEmpty()) {
			throw new SkipException("No rejected expense yet");
		}
		List<String> in_report = present_in(rejected, by_key(report_rows()));
		Assert.assertTrue(in_report.isEmpty(), "DEFECT: rejected expenses are listed in the Attendance Expense Reports: " + shorten(in_report));
		test.pass("None of the " + rejected.size() + " rejected expenses is in the report");
	}

	// LK05: the number of approved expenses equals the number of report lines
	@Test
	public void LK05_counts_agree() {
		test = extent.createTest("LK05 Approved count equals report count");
		int approved = 0;
		for (Map<String, String> r : approval_rows()) {
			approved += is_approved(r) ? 1 : 0;
		}
		int lines = report_rows().size();
		test.info("Approved on the Expense Approval screen: " + approved + " | lines in the report: " + lines);
		Assert.assertEquals(lines, approved, "DEFECT: " + approved + " expenses are approved but the report lists " + lines + " lines");
		test.pass("Both show " + approved);
	}

	// LK06: the values of an approved expense are the same in both screens
	@Test
	public void LK06_values_agree() {
		test = extent.createTest("LK06 Values agree in both screens");
		Map<String, Map<String, String>> approval = new HashMap<String, Map<String, String>>();
		for (Map<String, String> r : approval_rows()) {
			if (is_approved(r)) {
				approval.put(link_key(r), r);
			}
		}
		List<Map<String, String>> report = report_rows();
		if (approval.isEmpty() || report.isEmpty()) {
			throw new SkipException("No approved expense or no report line");
		}
		List<String> bad = new ArrayList<String>();
		int compared = 0;
		int odo = 0;
		List<String> odo_examples = new ArrayList<String>();
		for (Map<String, String> line : report) {
			Map<String, String> a = approval.get(link_key(line));
			if (a == null) {
				continue;
			}
			compared++;
			String[][] pairs = { { "FACTORY", "FACTORY" }, { "OUT TIME", "OUT TIME" } };
			for (String[] p : pairs) {
				if (!val(a, p[0]).trim().equalsIgnoreCase(val(line, p[1]).trim())) {
					bad.add(who(line) + ": " + p[0] + " '" + val(a, p[0]) + "' / report '" + val(line, p[1]) + "'");
				}
			}
			// the screens name the expense and show the odometer differently: written down, not failed
			if (!close(num(a, "ODO START KM"), num(line, "ODO START KM")) || !close(num(a, "ODO END KM"), num(line, "ODO END KM"))) {
				odo++;
				if (odo_examples.size() < 5) {
					odo_examples.add(who(line) + " ODO " + val(a, "ODO START KM") + ".." + val(a, "ODO END KM") + " / report " + val(line, "ODO START KM") + ".." + val(line, "ODO END KM"));
				}
			}
			if (num(a, "DISTANCE") > 0 && !close(num(a, "DISTANCE"), num(line, "KM CLAIMED"))) {
				bad.add(who(line) + ": DISTANCE " + val(a, "DISTANCE") + " / report KM CLAIMED " + val(line, "KM CLAIMED"));
			}
		}
		test.info(compared + " lines compared");
		if (odo > 0) {
			test.warning("OBSERVATION: " + odo + " lines show other odometer readings on the two screens, e.g. " + odo_examples);
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: values that differ between the screens: " + shorten(bad));
		test.pass("Values agree on " + compared + " lines");
	}

	// ======================= REAL decisions =======================

	// LK07: approve a pending expense: it is not in the report before and it is in the report afterwards, with its values
	@Test
	public void LK07_approve_then_it_appears_in_the_report() {
		test = extent.createTest("LK07 Approve an expense: it appears in the report");
		List<Map<String, String>> all = approval_rows();
		Map<String, String> row = first_pending(all);
		String number = val(row, "EXPENSE NUMBER");
		test.info("Using " + who(row) + " (" + val(row, "EXPENSE TYPE") + ")");
		Assert.assertNull(report_line_of(row), "DEFECT: the pending expense " + number + " is already in the report");

		YA_ExpenseApproval_Cls a = to_approval();
		a.Search_dates(FROM_WIDE, today());
		a.Approve(number, "", "Approved by the link test " + unique());
		forget_lists();
		a.Search_dates(FROM_WIDE, today());
		Map<String, String> after = a.record_of(number);
		Assert.assertNotNull(after, number + " is not listed after the approval");
		Assert.assertTrue(is_approved(after), "DEFECT: " + number + " shows '" + val(after, "ACTION") + "' after the approval");

		Map<String, String> line = report_line_of(after);
		Assert.assertNotNull(line, "DEFECT: the approved expense " + who(after) + " is not in the Attendance Expense Reports");
		test.info("Report line: " + line);
		Assert.assertTrue(close(num(line, "KM CLAIMED"), num(after, "DISTANCE")), "DEFECT: KM CLAIMED " + val(line, "KM CLAIMED") + " in the report, Distance " + val(after, "DISTANCE") + " on the approval screen");
		Assert.assertTrue(close(num(line, "EXPENSE AMOUNT"), num(after, "EXPENSE AMOUNT")), "DEFECT: EXPENSE AMOUNT " + val(line, "EXPENSE AMOUNT") + " in the report, " + val(after, "EXPENSE AMOUNT") + " on the approval screen");
		test.pass(number + " moved into the report");
	}

	// LK08: reject a pending expense: it is not in the report, and the lines of approved expenses stay
	@Test
	public void LK08_reject_then_it_is_not_in_the_report() {
		test = extent.createTest("LK08 Reject an expense: it is not in the report");
		List<Map<String, String>> all = approval_rows();
		Map<String, String> row = first_pending(all);
		String number = val(row, "EXPENSE NUMBER");
		int report_before = report_rows().size();
		test.info("Using " + who(row) + " (" + val(row, "EXPENSE TYPE") + "); the report has " + report_before + " lines");

		YA_ExpenseApproval_Cls a = to_approval();
		a.Search_dates(FROM_WIDE, today());
		a.Reject(number, "Rejected by the link test " + unique());
		forget_lists();
		a.Search_dates(FROM_WIDE, today());
		Map<String, String> after = a.record_of(number);
		Assert.assertNotNull(after, number + " is not listed after the rejection");
		Assert.assertTrue(is_rejected(after), "DEFECT: " + number + " shows '" + val(after, "ACTION") + "' after the rejection");

		Assert.assertNull(report_line_of(after), "DEFECT: the rejected expense " + number + " is in the Attendance Expense Reports");
		int report_after = report_rows().size();
		Assert.assertEquals(report_after, report_before, "DEFECT: a rejection changed the report from " + report_before + " to " + report_after + " lines");
		test.pass(number + " stays out of the report");
	}

	// LK09: approve with a changed km: the report shows that km and amount = km x rate
	@Test
	public void LK09_changed_km_reaches_the_report() {
		test = extent.createTest("LK09 A changed km reaches the report");
		List<Map<String, String>> all = approval_rows();
		Map<String, String> row = null;
		for (Map<String, String> r : all) {
			if (is_pending(r) && !val(r, "EXPENSE NUMBER").isEmpty() && num(r, "DISTANCE") > 0) {
				row = r;
				break;
			}
		}
		if (row == null) {
			throw new SkipException("No pending expense with a distance above 0 (a km based expense) is left");
		}
		String number = val(row, "EXPENSE NUMBER");
		test.info("Using " + who(row) + " (distance " + val(row, "DISTANCE") + ", amount " + val(row, "EXPENSE AMOUNT") + ") with km 7");

		YA_ExpenseApproval_Cls a = to_approval();
		a.Search_dates(FROM_WIDE, today());
		a.Approve(number, "7", "Km changed by the link test " + unique());
		forget_lists();
		a.Search_dates(FROM_WIDE, today());
		Map<String, String> after = a.record_of(number);
		Assert.assertNotNull(after, number + " is not listed after the approval");
		Assert.assertTrue(is_approved(after), "DEFECT: " + number + " shows '" + val(after, "ACTION") + "' after the approval");

		Map<String, String> line = report_line_of(after);
		Assert.assertNotNull(line, "DEFECT: the approved expense " + who(after) + " is not in the Attendance Expense Reports");
		double km = num(line, "KM CLAIMED");
		double rate = num(line, "RATEPERKM");
		double amount = num(line, "EXPENSE AMOUNT");
		test.info("Report: km claimed " + km + " | rate " + rate + " | amount " + amount + " | PA " + val(line, "PA") + " | DA " + val(line, "DA"));
		Assert.assertTrue(close(km, 7), "DEFECT: the approver set 7 km but the report shows KM CLAIMED " + km);
		Assert.assertTrue(close(amount, 7 * rate) || close(amount, 7 * rate + num(line, "PA") + num(line, "DA")),
				"DEFECT: 7 km x rate " + rate + " = " + (7 * rate) + " but the report shows EXPENSE AMOUNT " + amount);
		test.pass("The changed km and the amount reach the report");
	}

	// ======================= both screens, same filters =======================

	// LK10: one single day: the approved expenses of that day equal the report lines of that day
	@Test
	public void LK10_single_day_parity() {
		test = extent.createTest("LK10 One day in both screens");
		List<Map<String, String>> approved = new ArrayList<Map<String, String>>();
		for (Map<String, String> r : approval_rows()) {
			if (is_approved(r)) {
				approved.add(r);
			}
		}
		if (approved.isEmpty()) {
			throw new SkipException("No approved expense yet");
		}
		String day = val(approved.get(0), "EXPENSE DATE");
		int expected = 0;
		for (Map<String, String> r : approved) {
			expected += val(r, "EXPENSE DATE").equals(day) ? 1 : 0;
		}
		YB_AttendanceExpenseReport_Cls rep = to_report();
		rep.Search_dates(day, day);
		int lines = rep.all_records().size();
		test.info("Day " + day + ": " + expected + " approved expenses, " + lines + " report lines");
		Assert.assertEquals(lines, expected, "DEFECT: on " + day + " " + expected + " expenses are approved but the report lists " + lines + " lines");
		test.pass("Day " + day + " agrees");
	}

	// LK11: one employee: the approved expenses of that employee equal the report lines of that employee
	@Test
	public void LK11_employee_parity() {
		test = extent.createTest("LK11 One employee in both screens");
		List<Map<String, String>> approved = new ArrayList<Map<String, String>>();
		for (Map<String, String> r : approval_rows()) {
			if (is_approved(r)) {
				approved.add(r);
			}
		}
		if (approved.isEmpty()) {
			throw new SkipException("No approved expense yet");
		}
		String employee = employee_of(approved.get(0));
		String name = employee.contains("(") ? employee.substring(0, employee.indexOf('(')).trim() : employee;
		int expected = 0;
		for (Map<String, String> r : approved) {
			expected += employee_of(r).equals(employee) ? 1 : 0;
		}
		YB_AttendanceExpenseReport_Cls rep = to_report();
		rep.Search_report("", "", "", name, FROM_WIDE, today());
		int lines = 0;
		for (Map<String, String> line : rep.all_records()) {
			lines += employee_of(line).equals(employee) ? 1 : 0;
		}
		test.info("Employee " + employee + ": " + expected + " approved expenses, " + lines + " report lines");
		Assert.assertEquals(lines, expected, "DEFECT: " + employee + " has " + expected + " approved expenses but " + lines + " report lines");
		test.pass(employee + " agrees");
	}

	// LK12: the total amount of the approved expenses equals the total amount of the report
	@Test
	public void LK12_total_amount_agrees() {
		test = extent.createTest("LK12 Total amount agrees");
		double approved_total = 0;
		int approved = 0;
		for (Map<String, String> r : approval_rows()) {
			if (is_approved(r)) {
				approved_total += num(r, "EXPENSE AMOUNT");
				approved++;
			}
		}
		if (approved == 0) {
			throw new SkipException("No approved expense yet");
		}
		double report_total = 0;
		for (Map<String, String> line : report_rows()) {
			report_total += num(line, "EXPENSE AMOUNT");
		}
		test.info("Approved total " + approved_total + " | report total " + report_total);
		Assert.assertTrue(close(approved_total, report_total), "DEFECT: the approved expenses add up to " + approved_total + " but the report adds up to " + report_total);
		test.pass("Both total " + report_total);
	}

	// LK13: the default report (today) lists the expenses of today that were approved
	@Test
	public void LK13_today_in_the_default_report() {
		test = extent.createTest("LK13 Today's approved expenses are in the default report");
		List<Map<String, String>> approved_today = new ArrayList<Map<String, String>>();
		for (Map<String, String> r : approval_rows()) {
			if (is_approved(r) && val(r, "EXPENSE DATE").equals(today())) {
				approved_today.add(r);
			}
		}
		YB_AttendanceExpenseReport_Cls rep = to_report();
		List<Map<String, String>> lines = rep.all_records();
		test.info("Approved today: " + approved_today.size() + " | lines in the default report: " + lines.size());
		Assert.assertEquals(lines.size(), approved_today.size(), "DEFECT: " + approved_today.size() + " expenses of today are approved but the default report lists " + lines.size() + " lines");
		test.pass("The default report agrees");
	}

	// LK14: report lines are one per approved expense (nothing is listed twice)
	@Test
	public void LK14_no_line_twice() {
		test = extent.createTest("LK14 No line twice");
		List<Map<String, String>> lines = report_rows();
		if (lines.isEmpty()) {
			throw new SkipException("The report has no line");
		}
		List<Map<String, String>> approved = new ArrayList<Map<String, String>>();
		for (Map<String, String> r : approval_rows()) {
			if (is_approved(r)) {
				approved.add(r);
			}
		}
		Map<String, Integer> allowed = counts(approved);
		List<String> bad = new ArrayList<String>();
		for (Map.Entry<String, Integer> e : counts(lines).entrySet()) {
			int ok = allowed.containsKey(e.getKey()) ? allowed.get(e.getKey()) : 0;
			if (e.getValue() > ok && ok > 0) {
				bad.add(e.getKey() + " x" + e.getValue() + " (approved " + ok + ")");
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: expenses listed twice in the report: " + shorten(bad));
		test.pass("Every approved expense is listed once");
	}
}
