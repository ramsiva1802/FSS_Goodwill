package TestPkg;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import PagePkg.S_OrderStatus_Cls;
import PagePkg.T_OrderBooking_Cls;
import PagePkg.YD_LocationBasedDelivery_Cls;

/*
 * Location-Based Delivery (Reports > Location-Based Delivery), logged in as the web user - tests LD01 .. LD50.
 * A read only report: one line per order with the customer, the plant, the distance, the delivery sequence of the
 * day, the order value and the order status.
 *
 * LD01 .. LD40 only read the report (screen, dates, filters, table, content and calculation rules, Download PDF).
 * LD41 .. LD50 work with REAL orders: an order is placed in Field Management > OrderBooking (route / customer / item of
 * the Order Booking tests, remark starting with ZQ), then checked in this report, edited / approved / deleted in
 * Field Management > Order Status, and checked here again. The tests run in the order of their names.
 *
 * Cells are read by column name. "DEFECT:" = the site is wrong, "OBSERVATION:" = something to look at.
 */
public class YD_LocationBasedDelivery_Test extends ExpenseTestBase {

	static final String[] HEADERS = { "SL.NO", "ORDER NO", "ORDER DATE AND TIME", "CUSTOMER CODE", "CUSTOMER NAME",
			"PLANT LOCATION", "DISTANCE", "DELIVERY SEQUENCE", "ORDER VALUE", "ORDER STATUS" };

	static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a", Locale.ENGLISH);
	static final DateTimeFormatter ORDER_DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	static final String ROUTE = T_OrderBooking_Test.ROUTE;
	static final String CUSTOMER = T_OrderBooking_Test.CUSTOMER;

	// every test starts with a fresh Location-Based Delivery screen
	@BeforeMethod
	public void open_screen() {
		if (screen_is_clean()) {
			return;
		}
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);
		open_module("Reports", "Location", () -> y.is_list_page());
		remember_clean_screen();
	}

	// ---------------------------------- helpers ----------------------------------

	// the lines of every page are read ONCE and kept (the report is read only); the search itself is still done
	private static List<Map<String, String>> wide_cache;
	private static Booked order_a;
	private static Booked order_b;
	private static String booking_problem = "";

	@BeforeClass
	public void reset_state() {
		wide_cache = null;
		order_a = null;
		order_b = null;
		booking_problem = "";
	}

	private List<Map<String, String>> wide(YD_LocationBasedDelivery_Cls y) {
		y.Search_dates(FROM_WIDE, today());
		if (wide_cache == null) {
			wide_cache = y.all_records();
		}
		List<Map<String, String>> all = wide_cache;
		test.info(FROM_WIDE + " .. " + today() + ": " + all.size() + " lines | " + y.entries_text());
		if (all.isEmpty()) {
			throw new SkipException("No order is listed for " + FROM_WIDE + " .. " + today());
		}
		return all;
	}

	private String header_of(YD_LocationBasedDelivery_Cls y, String name) {
		for (String h : y.headers()) {
			if (h.startsWith(name)) {
				return h;
			}
		}
		throw new AssertionError("Column header missing: " + name);
	}

	// the lines of the report are told apart by order number, value and status (the sequence and the SL.NO may change with a filter)
	@Override
	protected String key(Map<String, String> rec) {
		return no_of(rec) + "|" + val(rec, "ORDER VALUE") + "|" + val(rec, "ORDER STATUS");
	}

	@Override
	protected String who(Map<String, String> rec) {
		return no_of(rec) + " " + opt(rec, "CUSTOMER NAME") + " " + opt(rec, "ORDER DATE AND TIME");
	}

	private String no_of(Map<String, String> rec) {
		return opt(rec, "ORDER NO").replaceAll("\\s+", "");
	}

	private LocalDateTime when(Map<String, String> rec) {
		try {
			return LocalDateTime.parse(val(rec, "ORDER DATE AND TIME").trim().toUpperCase(), ORDER_TIME);
		} catch (Exception e) {
			return null;
		}
	}

	private String day_of(Map<String, String> rec) {
		String t = val(rec, "ORDER DATE AND TIME").trim();
		return t.length() >= 10 ? t.substring(0, 10) : t;
	}

	private LocalDate date_of(Map<String, String> rec) {
		return LocalDate.parse(day_of(rec), ORDER_DAY);
	}

	private String today_dash() {
		return LocalDate.now().format(ORDER_DAY);
	}

	private boolean has_distance(Map<String, String> rec) {
		return !val(rec, "DISTANCE").trim().isEmpty();
	}

	private int sequence_of(Map<String, String> rec) {
		return (int) num(rec, "DELIVERY SEQUENCE");
	}

	// the line of an order number in 'rows' or null
	private Map<String, String> find(List<Map<String, String>> rows, String number) {
		for (Map<String, String> r : rows) {
			if (no_of(r).equalsIgnoreCase(number)) {
				return r;
			}
		}
		return null;
	}

	// 0 text, 1 number, 2 date and time of the order
	private List<String> page_values(YD_LocationBasedDelivery_Cls y, String column, int kind) {
		List<String> out = new ArrayList<String>();
		for (Map<String, String> r : y.records()) {
			String v = val(r, column);
			if (kind == 2) {
				LocalDateTime t = when(r);
				v = t == null ? v : t.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
			}
			out.add(v);
		}
		return out;
	}

	private int direction(List<String> values, boolean as_number) {
		boolean asc = true;
		boolean desc = true;
		for (int i = 1; i < values.size(); i++) {
			int c = as_number ? Double.compare(YD_LocationBasedDelivery_Cls.to_number(values.get(i - 1)), YD_LocationBasedDelivery_Cls.to_number(values.get(i)))
					: values.get(i - 1).compareToIgnoreCase(values.get(i));
			asc &= c <= 0;
			desc &= c >= 0;
		}
		return asc && desc ? 2 : asc ? 1 : desc ? -1 : 0;
	}

	private void sort_check(String title, String column, int kind) {
		test = extent.createTest(title);
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);
		wide(y);
		String h = header_of(y, column);
		y.Click_header(h);
		int first = direction(page_values(y, column, kind), kind == 1);
		List<String> after_first = page_values(y, column, kind);
		y.Click_header(h);
		int second = direction(page_values(y, column, kind), kind == 1);
		test.info(column + ": after click 1 direction " + first + ", after click 2 direction " + second + " (1 up, -1 down, 2 equal, 0 mixed)");
		if (first == 2 || second == 2) {
			throw new SkipException("All values of " + column + " on the page are equal: sorting cannot be seen");
		}
		if (first == 0 || second == 0) {
			test.info("Values on the page after the first click: " + after_first);
		}
		Assert.assertTrue(first != 0 && second != 0, "DEFECT: the " + column + " column is not sorted after a click on its header");
		Assert.assertEquals(first, -second, "DEFECT: a second click on the " + column + " header does not reverse the order");
		test.pass(column + " sorts both ways");
	}

	// ======================= screen and search form =======================

	// LD01: Search, Download PDF, the table filter, the column headers and the entries line
	@Test
	public void LD01_screen_elements() {
		test = extent.createTest("LD01 Location-Based Delivery screen elements");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		Assert.assertTrue(y.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(y.verify_pdf().isDisplayed(), "Download PDF button is missing");
		Assert.assertTrue(y.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = y.headers();
		test.info("Headers: " + headers + " | " + y.entries_text());
		for (String h : HEADERS) {
			boolean found = false;
			for (String x : headers) {
				found |= x.startsWith(h);
			}
			Assert.assertTrue(found, "Column header missing: " + h);
		}
		Assert.assertTrue(y.has_page_length(), "The 'Show N entries' box is missing");
		test.pass("Screen elements are displayed");
	}

	// LD02: the Search button opens the form with its seven fields; both dates are today
	@Test
	public void LD02_search_form_opens() {
		test = extent.createTest("LD02 Search form opens");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		y.Open_search_form();
		Assert.assertTrue(y.verify_hierarchy_type().isDisplayed(), "Hierarchy Type is missing");
		Assert.assertTrue(y.verify_supply_by().isDisplayed(), "Supply By is missing");
		Assert.assertTrue(y.verify_route().isDisplayed(), "Route is missing");
		Assert.assertTrue(y.verify_employee_type().isDisplayed(), "Employee Type is missing");
		Assert.assertTrue(y.verify_salesman().isDisplayed(), "Salesman is missing");
		Assert.assertTrue(y.verify_from().isDisplayed(), "From Date is missing");
		Assert.assertTrue(y.verify_to().isDisplayed(), "To Date is missing");
		Assert.assertTrue(y.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertTrue(y.verify_clear().isDisplayed(), "The Clear button is missing");
		test.info("From " + y.verify_from().getAttribute("value") + " | To " + y.verify_to().getAttribute("value"));
		Assert.assertEquals(y.verify_from().getAttribute("value"), today(), "Default From Date");
		Assert.assertEquals(y.verify_to().getAttribute("value"), today(), "Default To Date");
		test.pass("Search form is displayed with its defaults");
	}

	// LD03: the options of the two drop downs are written to the report
	@Test
	public void LD03_dropdown_options() {
		test = extent.createTest("LD03 Drop down options");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		y.Open_search_form();
		List<String> hierarchy = y.options_of(y.verify_hierarchy_type());
		List<String> types = y.options_of(y.verify_employee_type());
		test.info("Hierarchy Type: " + hierarchy + " (placeholder '" + y.selected(y.verify_hierarchy_type()) + "')");
		test.info("Employee Type: " + types + " (placeholder '" + y.selected(y.verify_employee_type()) + "')");
		Assert.assertFalse(hierarchy.isEmpty(), "The Hierarchy Type drop down has no value");
		Assert.assertFalse(types.isEmpty(), "The Employee Type drop down has no value");
		test.pass("Drop down options are listed");
	}

	// LD04: the screen opens with today's orders only
	@Test
	public void LD04_default_list_is_today() {
		test = extent.createTest("LD04 The default list is today");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> rows = y.all_records();
		test.info("Default list: " + rows.size() + " orders | " + y.entries_text());
		if (rows.isEmpty()) {
			Assert.assertTrue(y.no_data_shown() || y.entries_total() <= 0, "No order and no 'No data' message");
			throw new SkipException("No order today");
		}
		for (Map<String, String> r : rows) {
			Assert.assertEquals(day_of(r), today_dash(), "DEFECT: the default list shows an order of another day: " + who(r));
		}
		test.pass("All " + rows.size() + " orders are of today");
	}

	// ======================= dates =======================

	// LD05: the wide date range lists orders
	@Test
	public void LD05_search_wide_date_range() {
		test = extent.createTest("LD05 Wide date range");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		Assert.assertTrue(y.entries_total() == -1 || y.entries_total() == all.size(), "DEFECT: the entries line says " + y.entries_total() + " but " + all.size() + " lines were read");
		test.pass(all.size() + " orders listed");
	}

	// LD06: one single day lists only that day, as many as the wide search has for it
	@Test
	public void LD06_search_single_day() {
		test = extent.createTest("LD06 Single day");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		String day = day_of(all.get(0));
		String typed = date_of(all.get(0)).format(DATE);
		int expected = 0;
		for (Map<String, String> r : all) {
			expected += day_of(r).equals(day) ? 1 : 0;
		}
		y.Search_dates(typed, typed);
		List<Map<String, String>> found = y.all_records();
		test.info("Day " + typed + ": " + found.size() + " orders, the wide search has " + expected);
		for (Map<String, String> r : found) {
			Assert.assertEquals(day_of(r), day, "A line of another day: " + who(r));
		}
		Assert.assertEquals(found.size(), expected, "DEFECT: From Date = To Date = " + typed + " lists " + found.size() + " orders but the wide search has " + expected + " on that day");
		test.pass("Only " + typed + " is listed");
	}

	// LD07: the oldest and the newest day of the data are both included
	@Test
	public void LD07_range_boundaries_included() {
		test = extent.createTest("LD07 Range boundaries");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		LocalDate oldest = LocalDate.MAX;
		LocalDate newest = LocalDate.MIN;
		for (Map<String, String> r : all) {
			LocalDate d = date_of(r);
			oldest = d.isBefore(oldest) ? d : oldest;
			newest = d.isAfter(newest) ? d : newest;
		}
		y.Search_dates(oldest.format(DATE), newest.format(DATE));
		int found = y.all_records().size();
		test.info("Range " + oldest.format(DATE) + " .. " + newest.format(DATE) + ": " + found + " orders (the wide search has " + all.size() + ")");
		Assert.assertEquals(found, all.size(), "DEFECT: the range " + oldest.format(DATE) + " .. " + newest.format(DATE) + " lists " + found + " orders but the wide search lists " + all.size());
		test.pass("Both boundary days are included");
	}

	// LD08: From Date after To Date: a message or nothing
	@Test
	public void LD08_reversed_date_range() {
		test = extent.createTest("LD08 Reversed date range");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		String alert = y.Search_dates_message("20/10/2026", "01/10/2026");
		if (!alert.isEmpty()) {
			test.pass("The site refuses a reversed range with the message: " + alert);
			return;
		}
		test.info("Rows: " + y.result_count() + " | messages: " + y.validation_messages() + " | notice: " + y.notice());
		Assert.assertTrue(y.result_count() == 0 || !y.validation_messages().isEmpty() || !y.notice().isEmpty(),
				"DEFECT: a reversed date range lists orders without any message");
		test.pass("Reversed range gives nothing or a message");
	}

	// LD09: a range in the future has no order
	@Test
	public void LD09_future_dates() {
		test = extent.createTest("LD09 Future date range");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		String from = LocalDate.now().plusDays(60).format(DATE);
		String to = LocalDate.now().plusDays(90).format(DATE);
		String alert = y.Search_dates_message(from, to);
		test.info(from + " .. " + to + ": rows " + y.result_count() + " | alert '" + alert + "' | " + y.entries_text());
		if (!alert.isEmpty()) {
			test.pass("The site answers a future range with: " + alert);
			return;
		}
		Assert.assertEquals(y.result_count(), 0, "Orders are listed for a future range");
		test.pass("A future range lists nothing");
	}

	// LD10: invalid From Date values are refused
	@Test
	public void LD10_invalid_dates() {
		test = extent.createTest("LD10 Invalid dates");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (String value : new String[] { "abcd", "31/02/2026", "99/99/9999" }) {
			String alert = y.Search_dates_message(value, today());
			y.Open_search_form();
			String kept = y.verify_from().getAttribute("value");
			test.info("From Date '" + value + "' -> alert '" + alert + "' | box keeps '" + kept + "' | rows " + y.result_count()
					+ " | messages " + y.validation_messages() + " | " + y.entries_text());
			boolean refused = !alert.isEmpty() || kept == null || !kept.equals(value) || !y.validation_messages().isEmpty()
					|| y.result_count() == 0 || !y.notice().isEmpty();
			if (!refused) {
				bad.add(value);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: invalid From Date values were accepted and orders listed: " + bad);
		test.pass("Invalid dates are not accepted");
	}

	// LD11: a range in the past (the whole of 2025) lists only days of 2025 or nothing
	@Test
	public void LD11_past_range() {
		test = extent.createTest("LD11 Past date range");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		y.Search_dates("01/01/2025", "31/12/2025");
		List<Map<String, String>> found = y.all_records();
		test.info("01/01/2025 .. 31/12/2025: " + found.size() + " orders | " + y.entries_text());
		for (Map<String, String> r : found) {
			Assert.assertEquals(date_of(r).getYear(), 2025, "DEFECT: the range of 2025 lists " + who(r));
		}
		test.pass("The past range lists only 2025 (" + found.size() + " orders)");
	}

	// LD12: both dates are required (marked with *): an empty date is refused, not silently replaced
	@Test
	public void LD12_dates_are_required() {
		test = extent.createTest("LD12 The dates are required");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (String which : new String[] { "from", "to", "both" }) {
			String alert = y.Search_blank(which);
			y.Open_search_form();
			boolean refused = !alert.isEmpty() || !y.validation_messages().isEmpty() || !y.notice().isEmpty() || y.result_count() == 0;
			test.info("Empty " + which + ": alert '" + alert + "' | messages " + y.validation_messages() + " | rows " + y.result_count());
			if (!refused) {
				bad.add(which);
			}
			y.Click_clear();
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the search ran without a required date and listed orders without a message: " + bad);
		test.pass("An empty date is refused");
	}

	// ======================= filters =======================

	// LD13: every Hierarchy Type lists only orders of the unfiltered list
	@Test
	public void LD13_hierarchy_type_filter() {
		test = extent.createTest("LD13 Hierarchy Type filter");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		y.Open_search_form();
		int sum = 0;
		for (String type : y.options_of(y.verify_hierarchy_type())) {
			y.Search_report(type, "", "", "", "", FROM_WIDE, today());
			List<Map<String, String>> found = y.all_records();
			sum += found.size();
			test.info("Hierarchy Type " + type + ": " + found.size() + " of " + all.size() + " orders");
			Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: orders under " + type + " that are not in the unfiltered list: " + shorten(not_in(found, all)));
			Assert.assertTrue(found.size() <= all.size(), "DEFECT: " + type + " lists more orders than the unfiltered list");
		}
		test.info("The Hierarchy Types add up to " + sum + " orders, the whole list has " + all.size());
		test.pass("Every Hierarchy Type lists orders of the whole list only");
	}

	// LD14: every Employee Type lists only orders of the unfiltered list
	@Test
	public void LD14_employee_type_filter() {
		test = extent.createTest("LD14 Employee Type filter");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		y.Open_search_form();
		for (String type : y.options_of(y.verify_employee_type())) {
			y.Search_report("", "", "", type, "", FROM_WIDE, today());
			List<Map<String, String>> found = y.all_records();
			test.info("Employee Type " + type + ": " + found.size() + " of " + all.size() + " orders");
			Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: orders under " + type + " that are not in the unfiltered list: " + shorten(not_in(found, all)));
		}
		test.pass("Every Employee Type lists orders of the whole list only");
	}

	// the first suggestion a search box makes for one of a few letters; "" when it suggests nothing
	private String first_suggestion(YD_LocationBasedDelivery_Cls y, org.openqa.selenium.WebElement box) {
		for (String letter : new String[] { "a", "e", "s", "g", "r", "1" }) {
			List<String> found = y.Suggestions_for(box, letter);
			test.info("Suggestions for '" + letter + "': " + found);
			if (!found.isEmpty()) {
				return found.get(0);
			}
		}
		return "";
	}

	// LD15: Supply By: a suggested name lists orders of the unfiltered list; an unknown name is not ignored
	@Test
	public void LD15_supply_by_filter() {
		test = extent.createTest("LD15 Supply By filter");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		y.Open_search_form();
		String pick = first_suggestion(y, y.verify_supply_by());
		if (pick.isEmpty()) {
			throw new SkipException("The Supply By box suggested nothing");
		}
		y.Search_report("", pick, "", "", "", FROM_WIDE, today());
		List<Map<String, String>> found = y.all_records();
		test.info("Supply By " + pick + ": " + found.size() + " of " + all.size() + " orders");
		Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: orders that are not in the unfiltered list: " + shorten(not_in(found, all)));
		y.Search_report("", "ZZQNoSuchSupplier" + unique(), "", "", "", FROM_WIDE, today());
		boolean ignored = y.result_count() > 0 && y.all_records().size() >= all.size() && y.validation_messages().isEmpty() && y.notice().isEmpty();
		Assert.assertFalse(ignored, "DEFECT: a supplier that does not exist is ignored and every order is listed without a message");
		test.pass("The Supply By filter works");
	}

	// LD16: Route: a suggested route lists orders of the unfiltered list; an unknown route is not ignored
	@Test
	public void LD16_route_filter() {
		test = extent.createTest("LD16 Route filter");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		y.Open_search_form();
		String pick = first_suggestion(y, y.verify_route());
		if (pick.isEmpty()) {
			throw new SkipException("The Route box suggested nothing");
		}
		y.Search_report("", "", pick, "", "", FROM_WIDE, today());
		List<Map<String, String>> found = y.all_records();
		test.info("Route " + pick + ": " + found.size() + " of " + all.size() + " orders");
		Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: orders that are not in the unfiltered list: " + shorten(not_in(found, all)));
		y.Search_report("", "", "ZZQNoSuchRoute" + unique(), "", "", FROM_WIDE, today());
		boolean ignored = y.result_count() > 0 && y.all_records().size() >= all.size() && y.validation_messages().isEmpty() && y.notice().isEmpty();
		Assert.assertFalse(ignored, "DEFECT: a route that does not exist is ignored and every order is listed without a message");
		test.pass("The Route filter works");
	}

	// LD17: Salesman: a suggested salesman lists orders of the unfiltered list; an unknown name is not ignored
	@Test
	public void LD17_salesman_filter() {
		test = extent.createTest("LD17 Salesman filter");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		y.Open_search_form();
		String pick = first_suggestion(y, y.verify_salesman());
		if (pick.isEmpty()) {
			throw new SkipException("The Salesman box suggested nothing");
		}
		y.Search_report("", "", "", "", pick, FROM_WIDE, today());
		List<Map<String, String>> found = y.all_records();
		test.info("Salesman " + pick + ": " + found.size() + " of " + all.size() + " orders");
		Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: orders that are not in the unfiltered list: " + shorten(not_in(found, all)));
		y.Search_report("", "", "", "", "ZZQNoSuchSalesman" + unique(), FROM_WIDE, today());
		boolean ignored = y.result_count() > 0 && y.all_records().size() >= all.size() && y.validation_messages().isEmpty() && y.notice().isEmpty();
		Assert.assertFalse(ignored, "DEFECT: a salesman that does not exist is ignored and every order is listed without a message");
		test.pass("The Salesman filter works");
	}

	// LD18: all filters together list orders of the unfiltered list
	@Test
	public void LD18_combined_filters() {
		test = extent.createTest("LD18 Combined filters");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		y.Open_search_form();
		List<String> hierarchy = y.options_of(y.verify_hierarchy_type());
		List<String> types = y.options_of(y.verify_employee_type());
		y.Search_report(hierarchy.isEmpty() ? "" : hierarchy.get(0), "", "", types.isEmpty() ? "" : types.get(0), "", FROM_WIDE, today());
		List<Map<String, String>> found = y.all_records();
		test.info("Hierarchy " + (hierarchy.isEmpty() ? "-" : hierarchy.get(0)) + " + Employee Type " + (types.isEmpty() ? "-" : types.get(0)) + ": " + found.size() + " orders");
		Assert.assertTrue(not_in(found, all).isEmpty(), "DEFECT: orders that are not in the unfiltered list: " + shorten(not_in(found, all)));
		test.pass("Combined filters list orders of the whole list only");
	}

	// LD19: Clear empties the form and shows today again
	@Test
	public void LD19_clear_button() {
		test = extent.createTest("LD19 Clear button");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		y.Open_search_form();
		List<String> hierarchy = y.options_of(y.verify_hierarchy_type());
		y.Search_report(hierarchy.isEmpty() ? "" : hierarchy.get(0), "", "", "", "", FROM_WIDE, today());
		y.Click_clear();
		y.Open_search_form();
		test.info("After Clear: Hierarchy '" + y.selected(y.verify_hierarchy_type()) + "' | Supply By '" + y.verify_supply_by().getAttribute("value")
				+ "' | Route '" + y.verify_route().getAttribute("value") + "' | Salesman '" + y.verify_salesman().getAttribute("value")
				+ "' | From " + y.verify_from().getAttribute("value") + " | To " + y.verify_to().getAttribute("value"));
		Assert.assertTrue(y.verify_supply_by().getAttribute("value").isEmpty(), "DEFECT: Supply By is not empty after Clear");
		Assert.assertTrue(y.verify_route().getAttribute("value").isEmpty(), "DEFECT: Route is not empty after Clear");
		Assert.assertTrue(y.verify_salesman().getAttribute("value").isEmpty(), "DEFECT: Salesman is not empty after Clear");
		Assert.assertEquals(y.verify_from().getAttribute("value"), today(), "From Date after Clear");
		Assert.assertEquals(y.verify_to().getAttribute("value"), today(), "To Date after Clear");
		test.pass("Clear resets the form");
	}

	// ======================= table =======================

	// LD20: the table filter finds an order by its number
	@Test
	public void LD20_table_filter_by_order_number() {
		test = extent.createTest("LD20 Table filter by order number");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		String number = no_of(all.get(0));
		y.Filter_table(number);
		List<Map<String, String>> found = y.records();
		test.info("Filter '" + number + "': " + found.size() + " lines");
		Assert.assertTrue(found.size() >= 1, "The table filter did not find " + number);
		for (Map<String, String> r : found) {
			Assert.assertTrue(no_of(r).contains(number), "A line without the filter text: " + who(r));
		}
		test.pass("The table filter finds the order");
	}

	// LD21: the table filter by a customer name lists only lines with that name
	@Test
	public void LD21_table_filter_by_customer() {
		test = extent.createTest("LD21 Table filter by customer");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		String name = val(all.get(0), "CUSTOMER NAME");
		int expected = 0;
		for (Map<String, String> r : all) {
			expected += val(r, "CUSTOMER NAME").toLowerCase().contains(name.toLowerCase()) ? 1 : 0;
		}
		y.Filter_table(name);
		List<Map<String, String>> found = y.all_records();
		test.info("Filter '" + name + "': " + found.size() + " lines, expected " + expected);
		for (Map<String, String> r : found) {
			Assert.assertTrue(r.toString().toLowerCase().contains(name.toLowerCase()), "A line without the filter text: " + who(r));
		}
		Assert.assertEquals(found.size(), expected, "DEFECT: the table filter lists " + found.size() + " lines but " + expected + " lines of the list contain '" + name + "'");
		test.pass("The customer filter works");
	}

	// LD22: no match and special characters
	@Test
	public void LD22_table_filter_no_match_and_special_text() {
		test = extent.createTest("LD22 Table filter no match and special text");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		wide(y);
		y.Filter_table("ZZQNoSuchOrder" + unique());
		Assert.assertEquals(y.result_count(), 0, "Lines are listed for a text that matches nothing");
		Assert.assertTrue(y.no_data_shown() || y.entries_total() == 0 || y.entries_text().contains("0 entries"), "No 'No data' message: " + y.entries_text());
		for (String text : new String[] { "<script>", "'", "%", "\\" }) {
			y.Filter_table(text);
			test.info("Filter '" + text + "': " + y.result_count() + " lines | " + y.entries_text());
			Assert.assertTrue(y.verify_table_filter().isDisplayed(), "The page broke after the filter text " + text);
		}
		y.Filter_table("");
		test.pass("No match and special text are handled");
	}

	@Test
	public void LD23_sort_order_no() {
		sort_check("LD23 Sort ORDER NO", "ORDER NO", 0);
	}

	@Test
	public void LD24_sort_order_date() {
		sort_check("LD24 Sort ORDER DATE AND TIME", "ORDER DATE AND TIME", 2);
	}

	@Test
	public void LD25_sort_customer_name() {
		sort_check("LD25 Sort CUSTOMER NAME", "CUSTOMER NAME", 0);
	}

	@Test
	public void LD26_sort_distance() {
		sort_check("LD26 Sort DISTANCE", "DISTANCE", 1);
	}

	@Test
	public void LD27_sort_delivery_sequence() {
		sort_check("LD27 Sort DELIVERY SEQUENCE", "DELIVERY SEQUENCE", 1);
	}

	@Test
	public void LD28_sort_order_value() {
		sort_check("LD28 Sort ORDER VALUE", "ORDER VALUE", 1);
	}

	// LD29: Next / Previous move between pages, no order is on two pages
	@Test
	public void LD29_paging() {
		test = extent.createTest("LD29 Paging");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		if (all.size() <= 10 || !y.next_enabled()) {
			throw new SkipException("Only " + all.size() + " orders: there is no second page");
		}
		List<String> first = new ArrayList<String>();
		for (Map<String, String> r : y.records()) {
			first.add(no_of(r));
		}
		test.info("Page numbers: " + y.page_numbers() + " | " + y.entries_text());
		Assert.assertFalse(y.previous_enabled(), "Previous is enabled on the first page");
		y.Click_next();
		List<String> second = new ArrayList<String>();
		for (Map<String, String> r : y.records()) {
			second.add(no_of(r));
		}
		test.info("Second page: " + second + " | " + y.entries_text());
		Assert.assertFalse(second.isEmpty(), "The second page is empty");
		for (String n : second) {
			Assert.assertFalse(first.contains(n), "DEFECT: the order " + n + " is on page 1 and on page 2");
		}
		Assert.assertTrue(y.entries_text().contains("11 to"), "The entries line of page 2 does not start at 11: " + y.entries_text());
		y.Click_previous();
		List<String> back = new ArrayList<String>();
		for (Map<String, String> r : y.records()) {
			back.add(no_of(r));
		}
		Assert.assertEquals(back, first, "DEFECT: Previous does not show the first page again");
		test.pass("Paging works");
	}

	// LD30: Show N entries limits the rows of a page
	@Test
	public void LD30_show_entries() {
		test = extent.createTest("LD30 Show entries");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		if (!y.has_page_length()) {
			throw new SkipException("The screen has no 'Show N entries' box");
		}
		List<String> options = y.page_length_options();
		test.info("Show entries options: " + options + " | selected " + y.page_length_selected());
		for (String option : options) {
			if (!option.matches("\\d+")) {
				continue;
			}
			int n = Integer.parseInt(option);
			y.Set_page_length(option);
			int shown = y.result_count();
			test.info("Show " + n + ": " + shown + " rows | " + y.entries_text());
			Assert.assertEquals(shown, Math.min(n, all.size()), "DEFECT: Show " + n + " entries shows " + shown + " rows (the list has " + all.size() + ")");
		}
		test.pass("Show entries works");
	}

	// LD31: the entries line says 1 to N of the total
	@Test
	public void LD31_entries_line() {
		test = extent.createTest("LD31 Entries line");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		String text = y.entries_text();
		test.info(text);
		Assert.assertTrue(text.matches("Showing 1 to \\d+ of [\\d,]+ entries.*"), "The entries line is not 'Showing 1 to N of M entries': " + text);
		Assert.assertEquals(y.entries_total(), all.size(), "DEFECT: the entries line total is not the number of orders read from all pages");
		test.pass("Entries line: " + text);
	}

	// ======================= content and calculations =======================

	// LD32: formats: SL.NO runs 1, 2, 3 ..., order number, order date and time, value with 2 decimals, status
	@Test
	public void LD32_value_formats() {
		test = extent.createTest("LD32 Value formats");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		List<String> bad = new ArrayList<String>();
		Set<String> statuses = new TreeSet<String>();
		for (Map<String, String> r : all) {
			if (!no_of(r).matches("\\d{2}OGP[A-Z0-9]+")) {
				bad.add(who(r) + " order number '" + val(r, "ORDER NO") + "'");
			}
			if (when(r) == null) {
				bad.add(who(r) + " date and time '" + val(r, "ORDER DATE AND TIME") + "'");
			}
			if (!val(r, "ORDER VALUE").matches(".?[\\d,]+\\.\\d{2}")) {
				bad.add(who(r) + " order value '" + val(r, "ORDER VALUE") + "'");
			}
			if (has_distance(r) && !val(r, "DISTANCE").matches("\\d+(\\.\\d+)?")) {
				bad.add(who(r) + " distance '" + val(r, "DISTANCE") + "'");
			}
			if (!val(r, "DELIVERY SEQUENCE").matches("\\d+")) {
				bad.add(who(r) + " delivery sequence '" + val(r, "DELIVERY SEQUENCE") + "'");
			}
			statuses.add(val(r, "ORDER STATUS"));
		}
		test.info("Statuses in the list: " + statuses);
		List<Map<String, String>> page = y.records();
		for (int i = 0; i < page.size(); i++) {
			if (!val(page.get(i), "SL.NO").equals(String.valueOf(i + 1))) {
				bad.add("SL.NO of row " + (i + 1) + " is '" + val(page.get(i), "SL.NO") + "'");
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: values with a wrong format: " + shorten(bad));
		test.pass("Formats are right on " + all.size() + " orders");
	}

	// LD33: every important cell is filled
	@Test
	public void LD33_required_cells_filled() {
		test = extent.createTest("LD33 Required cells are filled");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<String> bad = new ArrayList<String>();
		int no_distance = 0;
		for (Map<String, String> r : wide(y)) {
			for (String c : new String[] { "ORDER NO", "ORDER DATE AND TIME", "CUSTOMER CODE", "CUSTOMER NAME", "PLANT LOCATION", "DELIVERY SEQUENCE", "ORDER VALUE", "ORDER STATUS" }) {
				if (blank(r, c)) {
					bad.add(who(r) + " has no " + c);
				}
			}
			no_distance += has_distance(r) ? 0 : 1;
		}
		if (no_distance > 0) {
			test.warning("OBSERVATION: " + no_distance + " orders have no DISTANCE (the customer has no location?)");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: empty cells: " + shorten(bad));
		test.pass("All required cells are filled");
	}

	// LD34: an order number is listed once
	@Test
	public void LD34_order_numbers_unique() {
		test = extent.createTest("LD34 Order numbers are unique");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		Set<String> seen = new HashSet<String>();
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : wide(y)) {
			if (!seen.add(no_of(r))) {
				bad.add(no_of(r));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: order numbers listed more than once: " + shorten(bad));
		test.pass("Every order number is listed once");
	}

	// LD35: the order value is a number that is not negative; orders worth 0 are written to the report
	@Test
	public void LD35_order_value() {
		test = extent.createTest("LD35 Order value");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<String> bad = new ArrayList<String>();
		List<String> zero = new ArrayList<String>();
		double total = 0;
		for (Map<String, String> r : wide(y)) {
			double v = num(r, "ORDER VALUE");
			total += v;
			if (v < 0 || val(r, "ORDER VALUE").contains("-")) {
				bad.add(who(r) + " " + val(r, "ORDER VALUE"));
			}
			if (v == 0) {
				zero.add(who(r));
			}
		}
		test.info("Total order value of the list: " + String.format("%.2f", total));
		if (!zero.isEmpty()) {
			test.warning("OBSERVATION: " + zero.size() + " orders have an order value of 0.00, e.g. " + shorten(zero));
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: negative order value: " + shorten(bad));
		test.pass("Order values are not negative");
	}

	// LD36: the delivery sequence of a day: positive, no number twice on one day (and 1 .. n without a gap)
	@Test
	public void LD36_delivery_sequence_per_day() {
		test = extent.createTest("LD36 Delivery sequence per day");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		Map<String, List<Integer>> by_day = new LinkedHashMap<String, List<Integer>>();
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : wide(y)) {
			int s = sequence_of(r);
			if (s < 1) {
				bad.add(who(r) + " sequence " + val(r, "DELIVERY SEQUENCE"));
			}
			by_day.computeIfAbsent(day_of(r), k -> new ArrayList<Integer>()).add(s);
		}
		List<String> gaps = new ArrayList<String>();
		for (Map.Entry<String, List<Integer>> e : by_day.entrySet()) {
			List<Integer> list = e.getValue();
			Set<Integer> distinct = new HashSet<Integer>(list);
			if (distinct.size() != list.size()) {
				bad.add(e.getKey() + " has a delivery sequence number twice: " + list);
			}
			for (int i = 1; i <= list.size(); i++) {
				if (!distinct.contains(i)) {
					gaps.add(e.getKey() + " " + list);
					break;
				}
			}
		}
		if (!gaps.isEmpty()) {
			test.warning("OBSERVATION: the sequence of these days is not 1 .. n without a gap: " + shorten(gaps));
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: delivery sequence: " + shorten(bad));
		test.pass("Delivery sequence is right on " + by_day.size() + " days");
	}

	// LD37: inside a day the sequence follows the distance (nearest first, orders without a distance last)
	@Test
	public void LD37_sequence_follows_distance() {
		test = extent.createTest("LD37 Sequence follows distance");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		Map<String, List<Map<String, String>>> by_day = new LinkedHashMap<String, List<Map<String, String>>>();
		for (Map<String, String> r : wide(y)) {
			by_day.computeIfAbsent(day_of(r), k -> new ArrayList<Map<String, String>>()).add(r);
		}
		List<String> bad = new ArrayList<String>();
		for (Map.Entry<String, List<Map<String, String>>> e : by_day.entrySet()) {
			List<Map<String, String>> rows = new ArrayList<Map<String, String>>(e.getValue());
			rows.sort((a, b) -> Integer.compare(sequence_of(a), sequence_of(b)));
			double last = -1;
			for (Map<String, String> r : rows) {
				double d = has_distance(r) ? num(r, "DISTANCE") : Double.MAX_VALUE;
				if (d < last) {
					bad.add(e.getKey() + ": " + no_of(r) + " (sequence " + sequence_of(r) + ", distance " + (has_distance(r) ? val(r, "DISTANCE") : "none") + ") comes after a farther order");
				}
				last = Math.max(last, d);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the delivery sequence does not follow the distance: " + shorten(bad));
		test.pass("The sequence follows the distance on " + by_day.size() + " days");
	}

	// LD38: the distance is a plausible number
	@Test
	public void LD38_distance_values() {
		test = extent.createTest("LD38 Distance values");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<String> bad = new ArrayList<String>();
		Set<String> distances = new TreeSet<String>();
		for (Map<String, String> r : wide(y)) {
			if (!has_distance(r)) {
				continue;
			}
			double d = num(r, "DISTANCE");
			distances.add(val(r, "DISTANCE"));
			if (d < 0 || d > 5000) {
				bad.add(who(r) + " distance " + val(r, "DISTANCE"));
			}
		}
		test.info("Distances in the list: " + distances);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: impossible distance: " + shorten(bad));
		test.pass("Distances are plausible");
	}

	// LD39: an order is never dated in the future
	@Test
	public void LD39_no_future_orders() {
		test = extent.createTest("LD39 No order dated in the future");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : wide(y)) {
			LocalDateTime t = when(r);
			if (t != null && t.isAfter(LocalDateTime.now().plusMinutes(5))) {
				bad.add(who(r));
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: orders dated in the future: " + shorten(bad));
		test.pass("No order is dated in the future");
	}

	// LD40: the Download PDF page shows the same orders as the table
	@Test
	public void LD40_download_pdf_matches_the_table() {
		test = extent.createTest("LD40 Download PDF matches the table");
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);

		List<Map<String, String>> all = wide(y);
		Assert.assertTrue(y.Click_pdf(), "DEFECT: Download PDF did not open the report page");
		try {
			String text = y.pdf_text();
			List<String> headers = y.pdf_headers();
			List<List<String>> rows = y.pdf_rows();
			test.info("PDF page " + y.pdf_url() + " | headers " + headers + " | " + rows.size() + " rows");
			Assert.assertTrue(text.toUpperCase().contains("LOCATION-BASED DELIVERY REPORT"), "The title LOCATION-BASED DELIVERY REPORT is missing");
			Assert.assertTrue(text.contains("From Date : " + LocalDate.parse(FROM_WIDE, DATE)), "The From Date of the PDF is not " + LocalDate.parse(FROM_WIDE, DATE));
			Assert.assertTrue(text.contains("To Date : " + LocalDate.now()), "The To Date of the PDF is not " + LocalDate.now());
			Assert.assertTrue(text.contains("Generated On"), "'Generated On' is missing");
			Assert.assertTrue(y.pdf_has_button("Print"), "The Print button is missing");
			for (String h : new String[] { "SL.NO", "ORDER NO", "ORDER DATE AND TIME", "CUSTOMER CODE", "CUSTOMER NAME", "PLANT LOCATION", "DISTANCE", "DELIVERY SEQUENCE", "ORDER VALUE", "ORDER STATUS" }) {
				boolean found = false;
				for (String x : headers) {
					found |= x.startsWith(h);
				}
				Assert.assertTrue(found, "PDF column missing: " + h);
			}
			Assert.assertEquals(rows.size(), all.size(), "DEFECT: the PDF has " + rows.size() + " rows but the table has " + all.size());
			List<String> bad = new ArrayList<String>();
			for (int i = 0; i < Math.min(rows.size(), all.size()); i++) {
				List<String> p = rows.get(i);
				Map<String, String> t = all.get(i);
				String pdf_no = p.get(1).replace(" ", "");
				if (!pdf_no.equals(no_of(t))) {
					bad.add("row " + (i + 1) + ": PDF " + pdf_no + " / table " + no_of(t));
				} else if (!close(YD_LocationBasedDelivery_Cls.to_number(p.get(p.size() - 2)), num(t, "ORDER VALUE"))
						|| !p.get(p.size() - 1).equalsIgnoreCase(val(t, "ORDER STATUS"))) {
					bad.add(no_of(t) + ": PDF " + p.get(p.size() - 2) + " " + p.get(p.size() - 1) + " / table " + val(t, "ORDER VALUE") + " " + val(t, "ORDER STATUS"));
				}
			}
			Assert.assertTrue(bad.isEmpty(), "DEFECT: the PDF differs from the table: " + shorten(bad));
			test.pass("The PDF shows the same " + rows.size() + " orders");
		} finally {
			y.Close_pdf(main_tab);
		}
	}

	// ======================= real orders: Order Booking -> this report -> Order Status =======================

	private static class Booked {
		String no = "";
		String code = "";
		double amount;
		String item = "";
		String qty = "";
	}

	// places an order of the customer of the Order Booking tests (remark starting with ZQ)
	private Booked book(String item, String qty) {
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);
		open_module("Field Management", "Booking", () -> o.is_booking_page());
		Booked b = new Booked();
		b.item = item;
		b.qty = qty;
		o.Select_route(ROUTE);
		o.Search_customer(CUSTOMER);
		b.no = o.order_no();
		b.code = o.info_value("Customer Code");
		Assert.assertFalse(b.code.isEmpty() || b.code.equals("-"), "The customer " + CUSTOMER + " was not picked in Order Booking");
		o.Click_next();
		Assert.assertEquals(o.current_tab(), "Item Selection", "Could not reach Item Selection: " + o.All_messages());
		o.Pick_item(0, item);
		Assert.assertTrue(o.rate(0) > 0, "The item " + item + " was not picked");
		o.Set_qty(0, qty);
		o.Click_next();
		Assert.assertEquals(o.current_tab(), "Payment Details", "Could not reach Payment Details: " + o.All_messages());
		b.amount = o.payment_amount();
		o.Select_mode("Unpaid");
		o.Set_remark("ZQ delivery " + unique());
		o.Click_submit();
		long end = System.currentTimeMillis() + 10000;
		while (System.currentTimeMillis() < end && !o.success_popup_open()) {
			WaitUtilPause();
		}
		Assert.assertTrue(o.success_popup_open(), "The order was not placed. Messages: " + o.All_messages());
		String shown = o.success_popup_order_no();
		if (!shown.isEmpty()) {
			b.no = shown;
		}
		o.Click_success_ok();
		return b;
	}

	private void WaitUtilPause() {
		UtillPkg.WaitUtil.settle(driver, 500);
	}

	// the first order of this class is placed once and used by the later tests
	private Booked ensure_a() {
		if (order_a == null) {
			if (!booking_problem.isEmpty()) {
				throw new SkipException("An order could not be placed earlier: " + booking_problem);
			}
			try {
				order_a = book(T_OrderBooking_Test.ITEM_A, "3");
			} catch (AssertionError | RuntimeException e) {
				booking_problem = e.getMessage();
				throw e;
			}
		}
		return order_a;
	}

	private Booked ensure_b() {
		ensure_a();
		if (order_b == null) {
			order_b = book(T_OrderBooking_Test.ITEM_B, "2");
		}
		return order_b;
	}

	// the report of today: the lines of every page
	private List<Map<String, String>> report_today() {
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);
		open_module("Reports", "Location", () -> y.is_list_page());
		y.Search_dates(today(), today());
		return y.all_records();
	}

	private S_OrderStatus_Cls status_screen() {
		S_OrderStatus_Cls os = new S_OrderStatus_Cls(driver);
		open_module("Field Management", "Order Status", () -> os.is_list_page());
		return os;
	}

	// the row of an order in Order Status: [order number, date, route, customer, amount, delivery date, status, actions]
	private List<String> status_row(S_OrderStatus_Cls os, String no) {
		os.Search_orders("", "", today(), today(), "", "", "", "");
		os.Filter_table(no);
		return os.row_of(no);
	}

	// confirms the open dialog with its first button that is not Cancel / No / Close and accepts the messages after it
	private List<String> confirm_dialog(S_OrderStatus_Cls os) {
		List<String> texts = new ArrayList<String>();
		if (os.dialog_open()) {
			texts.add(os.Dialog_text());
			String chosen = "OK";
			for (String b : os.dialog_buttons()) {
				String l = b.toLowerCase();
				if (!l.contains("cancel") && !l.equals("no") && !l.contains("close")) {
					chosen = b;
					break;
				}
			}
			os.Click_dialog_button(chosen);
		}
		texts.addAll(os.Accept_messages());
		return texts;
	}

	// LD41: a placed order is in the report of today: Pending, with its customer, value and a delivery sequence
	@Test
	public void LD41_placed_order_appears_in_the_report() {
		test = extent.createTest("LD41 A placed order appears in the report");
		Booked b = ensure_a();
		test.info("Placed order " + b.no + " (" + b.item + " x " + b.qty + ", amount " + b.amount + ", customer code " + b.code + ")");

		List<Map<String, String>> rows = report_today();
		Map<String, String> line = find(rows, b.no);
		Assert.assertNotNull(line, "DEFECT: the placed order " + b.no + " is not in the Location-Based Delivery report of today (" + rows.size() + " orders)");
		test.info("Report line: " + line);
		Assert.assertEquals(val(line, "ORDER STATUS"), "Pending", "DEFECT: a new order is not Pending in the report");
		Assert.assertTrue(val(line, "CUSTOMER CODE").equalsIgnoreCase(b.code), "DEFECT: customer code " + val(line, "CUSTOMER CODE") + " in the report, " + b.code + " in Order Booking");
		Assert.assertTrue(val(line, "CUSTOMER NAME").toLowerCase().contains(CUSTOMER.toLowerCase()), "DEFECT: customer name '" + val(line, "CUSTOMER NAME") + "' in the report, " + CUSTOMER + " booked");
		Assert.assertTrue(close(num(line, "ORDER VALUE"), b.amount), "DEFECT: order value " + val(line, "ORDER VALUE") + " in the report, payment amount " + b.amount + " in Order Booking");
		Assert.assertEquals(day_of(line), today_dash(), "DEFECT: the order date is not today");
		LocalDateTime t = when(line);
		Assert.assertNotNull(t, "The order date and time has no format: " + val(line, "ORDER DATE AND TIME"));
		Assert.assertTrue(t.isAfter(LocalDateTime.now().minusHours(3)) && t.isBefore(LocalDateTime.now().plusMinutes(5)),
				"DEFECT: the order time " + t + " is not the time the order was placed");
		Assert.assertFalse(blank(line, "PLANT LOCATION"), "The order has no plant location");
		Assert.assertTrue(sequence_of(line) >= 1, "The order has no delivery sequence: " + val(line, "DELIVERY SEQUENCE"));
		test.pass("The order is in the report with the right customer, value and status");
	}

	// LD42: a second order is added: one more line today, both have different delivery sequence numbers
	@Test
	public void LD42_second_order_gets_its_own_sequence() {
		test = extent.createTest("LD42 A second order gets its own delivery sequence");
		Booked a = ensure_a();
		int before = report_today().size();
		Booked b = ensure_b();
		List<Map<String, String>> rows = report_today();
		test.info("Orders today: " + before + " -> " + rows.size() + " | placed " + b.no);
		Map<String, String> la = find(rows, a.no);
		Map<String, String> lb = find(rows, b.no);
		Assert.assertNotNull(la, "The first order " + a.no + " is not in the report");
		Assert.assertNotNull(lb, "DEFECT: the second order " + b.no + " is not in the report");
		Assert.assertTrue(rows.size() >= before + 1, "DEFECT: the report did not get a new line (" + before + " -> " + rows.size() + ")");
		Assert.assertNotEquals(sequence_of(la), sequence_of(lb), "DEFECT: two orders of one day have the delivery sequence " + sequence_of(la));
		Set<Integer> seen = new HashSet<Integer>();
		for (Map<String, String> r : rows) {
			Assert.assertTrue(seen.add(sequence_of(r)), "DEFECT: a delivery sequence number is used twice today: " + sequence_of(r));
		}
		test.info("Sequence of " + a.no + " = " + sequence_of(la) + " | of " + b.no + " = " + sequence_of(lb));
		test.pass("Both orders are listed with different sequence numbers");
	}

	// LD43: the order value of the report is the amount of the order in Order Status and in Order Booking
	@Test
	public void LD43_order_value_agrees_with_order_status() {
		test = extent.createTest("LD43 Order value agrees with Order Status");
		Booked a = ensure_a();
		Booked b = ensure_b();
		List<Map<String, String>> rows = report_today();
		S_OrderStatus_Cls os = status_screen();
		for (Booked x : new Booked[] { a, b }) {
			Map<String, String> line = find(rows, x.no);
			Assert.assertNotNull(line, "The order " + x.no + " is not in the report");
			List<String> row = status_row(os, x.no);
			Assert.assertNotNull(row, "The order " + x.no + " is not in Order Status");
			double status_amount = S_OrderStatus_Cls.to_number(row.get(4));
			test.info(x.no + ": booking " + x.amount + " | Order Status " + status_amount + " | report " + val(line, "ORDER VALUE"));
			Assert.assertTrue(close(status_amount, x.amount), "DEFECT: Order Status shows " + status_amount + " for " + x.no + ", Order Booking showed " + x.amount);
			Assert.assertTrue(close(num(line, "ORDER VALUE"), status_amount), "DEFECT: the report shows " + val(line, "ORDER VALUE") + " for " + x.no + ", Order Status shows " + status_amount);
			Assert.assertEquals(val(line, "ORDER STATUS"), row.get(6).trim(), "DEFECT: the status of " + x.no + " differs between the report and Order Status");
		}
		test.pass("Order values and statuses agree");
	}

	// LD44: the quantity of a pending order is changed in Order Status: the order value in the report follows
	@Test
	public void LD44_quantity_change_reaches_the_report() {
		test = extent.createTest("LD44 A quantity change reaches the report");
		Booked a = ensure_a();
		Booked b = ensure_b();
		List<Map<String, String>> rows = report_today();
		Map<String, String> before_b = find(rows, b.no);
		Map<String, String> before_a = find(rows, a.no);
		Assert.assertNotNull(before_b, "The order " + b.no + " is not in the report");
		double old_value = num(before_b, "ORDER VALUE");

		S_OrderStatus_Cls os = status_screen();
		List<String> row = status_row(os, b.no);
		Assert.assertNotNull(row, "The order " + b.no + " is not in Order Status");
		if (!row.get(6).trim().equalsIgnoreCase("Pending")) {
			throw new SkipException("The order " + b.no + " is " + row.get(6) + ": it cannot be edited");
		}
		os.Click_edit(b.no);
		Assert.assertTrue(os.modal_open(), "The Order Details pop-up did not open for editing " + b.no);
		String old_qty = os.line_texts().get(0).get(6);
		long new_qty = (long) S_OrderStatus_Cls.to_number(old_qty) + 2;
		os.Set_line_qty(0, String.valueOf(new_qty));
		double net_new = os.line_net(0);
		os.Click_modal_button("Save changes");
		test.info("Quantity " + old_qty + " -> " + new_qty + " | line net " + net_new + " | messages " + confirm_dialog(os));
		List<String> after = status_row(os, b.no);
		Assert.assertNotNull(after, "The order is not listed after saving");
		double status_amount = S_OrderStatus_Cls.to_number(after.get(4));
		Assert.assertNotEquals(status_amount, old_value, "The quantity change did not change the order amount in Order Status");

		List<Map<String, String>> rows_after = report_today();
		Map<String, String> line = find(rows_after, b.no);
		Assert.assertNotNull(line, "DEFECT: the edited order " + b.no + " left the report");
		test.info("Order value: before " + old_value + " | Order Status " + status_amount + " | report " + val(line, "ORDER VALUE"));
		Assert.assertTrue(close(num(line, "ORDER VALUE"), status_amount), "DEFECT: the report shows " + val(line, "ORDER VALUE") + " after the edit, Order Status shows " + status_amount);
		Assert.assertEquals(rows_after.size(), rows.size(), "DEFECT: an edit changed the number of orders in the report");
		Map<String, String> still_a = find(rows_after, a.no);
		Assert.assertNotNull(still_a, "The other order " + a.no + " left the report");
		Assert.assertTrue(close(num(still_a, "ORDER VALUE"), num(before_a, "ORDER VALUE")), "DEFECT: the edit of one order changed the value of another order");
		test.pass("The new order value reaches the report");
	}

	// LD45: every order of today that is in the report has the amount and the status of Order Status
	@Test
	public void LD45_report_agrees_with_order_status_today() {
		test = extent.createTest("LD45 The report agrees with Order Status today");
		ensure_a();
		List<Map<String, String>> rows = report_today();
		S_OrderStatus_Cls os = status_screen();
		os.Search_orders("", "", today(), today(), "", "", "", "");
		List<List<String>> status_rows = os.result_texts();
		test.info("Report today: " + rows.size() + " orders | Order Status today (first page): " + status_rows.size() + " orders");
		List<String> bad = new ArrayList<String>();
		int compared = 0;
		for (Map<String, String> line : rows) {
			for (List<String> s : status_rows) {
				if (s.get(0).equalsIgnoreCase(no_of(line))) {
					compared++;
					if (!close(S_OrderStatus_Cls.to_number(s.get(4)), num(line, "ORDER VALUE"))) {
						bad.add(no_of(line) + ": value " + val(line, "ORDER VALUE") + " / Order Status " + s.get(4));
					}
					if (!s.get(6).trim().equalsIgnoreCase(val(line, "ORDER STATUS"))) {
						bad.add(no_of(line) + ": status " + val(line, "ORDER STATUS") + " / Order Status " + s.get(6));
					}
				}
			}
		}
		List<String> missing = new ArrayList<String>();
		for (List<String> s : status_rows) {
			if (find(rows, s.get(0)) == null) {
				missing.add(s.get(0) + " (" + s.get(6) + ")");
			}
		}
		if (!missing.isEmpty()) {
			test.warning("OBSERVATION: orders of today in Order Status that are not in the report: " + shorten(missing));
		}
		Assert.assertTrue(compared > 0, "No order of the report could be found in Order Status");
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the report differs from Order Status: " + shorten(bad));
		test.pass(compared + " orders agree with Order Status");
	}

	// LD46: an order is APPROVED in Order Status: the report shows Approved, with the same value and sequence
	@Test
	public void LD46_approved_order_shows_approved() {
		test = extent.createTest("LD46 An approved order shows Approved");
		Booked a = ensure_a();
		List<Map<String, String>> rows = report_today();
		Map<String, String> before = find(rows, a.no);
		Assert.assertNotNull(before, "The order " + a.no + " is not in the report");

		S_OrderStatus_Cls os = status_screen();
		List<String> row = status_row(os, a.no);
		Assert.assertNotNull(row, "The order " + a.no + " is not in Order Status");
		if (!row.get(6).trim().equalsIgnoreCase("Pending")) {
			throw new SkipException("The order " + a.no + " is already " + row.get(6));
		}
		os.Click_edit(a.no);
		Assert.assertTrue(os.modal_open(), "The Order Details pop-up did not open for " + a.no);
		os.Click_modal_button("Approve");
		test.info("Approved " + a.no + " | messages " + confirm_dialog(os));
		List<String> after = status_row(os, a.no);
		Assert.assertNotNull(after, "The order is not listed in Order Status after the approval");
		Assert.assertEquals(after.get(6).trim(), "Approved", "DEFECT: Order Status does not show Approved");

		List<Map<String, String>> rows_after = report_today();
		Map<String, String> line = find(rows_after, a.no);
		Assert.assertNotNull(line, "DEFECT: the approved order left the report");
		test.info("Report line: " + line);
		Assert.assertEquals(val(line, "ORDER STATUS"), "Approved", "DEFECT: the report does not show Approved after the approval");
		Assert.assertTrue(close(num(line, "ORDER VALUE"), num(before, "ORDER VALUE")), "DEFECT: the order value changed with the approval");
		Assert.assertEquals(sequence_of(line), sequence_of(before), "DEFECT: the delivery sequence changed with the approval");
		Assert.assertEquals(rows_after.size(), rows.size(), "DEFECT: the approval changed the number of orders in the report");
		test.pass("The report shows Approved");
	}

	// LD47: a pending order is DELETED in Order Status: it leaves the report and the other orders stay
	@Test
	public void LD47_deleted_order_leaves_the_report() {
		test = extent.createTest("LD47 A deleted order leaves the report");
		ensure_a();
		Booked c = book(T_OrderBooking_Test.ITEM_A, "1");
		List<Map<String, String>> rows = report_today();
		Assert.assertNotNull(find(rows, c.no), "DEFECT: the placed order " + c.no + " is not in the report");
		test.info("Placed " + c.no + " | orders today: " + rows.size());

		S_OrderStatus_Cls os = status_screen();
		List<String> row = status_row(os, c.no);
		Assert.assertNotNull(row, "The order " + c.no + " is not in Order Status");
		os.Click_delete(c.no);
		test.info("Deleted " + c.no + " | messages " + confirm_dialog(os));
		Assert.assertNull(status_row(os, c.no), "The order is still listed in Order Status after the delete");

		List<Map<String, String>> after = report_today();
		test.info("Orders today: " + rows.size() + " -> " + after.size());
		Assert.assertNull(find(after, c.no), "DEFECT: the deleted order " + c.no + " is still in the Location-Based Delivery report");
		Assert.assertEquals(after.size(), rows.size() - 1, "DEFECT: the report lost " + (rows.size() - after.size()) + " lines for one deleted order");
		test.pass("The deleted order left the report");
	}

	// LD48: the Route filter finds the placed order
	@Test
	public void LD48_route_filter_finds_the_placed_order() {
		test = extent.createTest("LD48 The Route filter finds the placed order");
		Booked a = ensure_a();
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);
		open_module("Reports", "Location", () -> y.is_list_page());

		y.Search_report("", "", ROUTE, "", "", today(), today());
		List<Map<String, String>> found = y.all_records();
		test.info("Route " + ROUTE + " today: " + found.size() + " orders");
		Assert.assertNotNull(find(found, a.no), "DEFECT: the order " + a.no + " (route " + ROUTE + ") is not listed when the Route filter is " + ROUTE);
		List<Map<String, String>> today_all = report_today();
		Assert.assertTrue(not_in(found, today_all).isEmpty(), "DEFECT: the Route filter lists orders that are not in today's list: " + shorten(not_in(found, today_all)));
		test.pass("The Route filter finds the order");
	}

	// LD49: the single day search of the placed order's day lists it
	@Test
	public void LD49_search_by_the_order_day_lists_the_order() {
		test = extent.createTest("LD49 The order day lists the order");
		Booked a = ensure_a();
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);
		open_module("Reports", "Location", () -> y.is_list_page());

		String yesterday = LocalDate.now().minusDays(1).format(DATE);
		y.Search_dates(yesterday, yesterday);
		List<Map<String, String>> found = y.all_records();
		Assert.assertNull(find(found, a.no), "DEFECT: the order " + a.no + " placed today is listed for yesterday");
		y.Search_dates(today(), today());
		Assert.assertNotNull(find(y.all_records(), a.no), "DEFECT: the order " + a.no + " is not listed for today");
		test.pass("The order is only in its own day");
	}

	// LD50: Download PDF shows the placed order with the same value and status as the table
	@Test
	public void LD50_download_pdf_shows_the_placed_order() {
		test = extent.createTest("LD50 Download PDF shows the placed order");
		Booked a = ensure_a();
		YD_LocationBasedDelivery_Cls y = new YD_LocationBasedDelivery_Cls(driver);
		open_module("Reports", "Location", () -> y.is_list_page());
		y.Search_dates(today(), today());
		Map<String, String> line = find(y.all_records(), a.no);
		Assert.assertNotNull(line, "The order " + a.no + " is not in the table");

		Assert.assertTrue(y.Click_pdf(), "DEFECT: Download PDF did not open the report page");
		try {
			List<List<String>> rows = y.pdf_rows();
			List<String> pdf = null;
			for (List<String> p : rows) {
				if (p.get(1).replace(" ", "").equalsIgnoreCase(a.no)) {
					pdf = p;
				}
			}
			test.info("PDF rows: " + rows.size() + " | the order in the PDF: " + pdf);
			Assert.assertNotNull(pdf, "DEFECT: the order " + a.no + " is not in the PDF");
			Assert.assertTrue(close(YD_LocationBasedDelivery_Cls.to_number(pdf.get(pdf.size() - 2)), num(line, "ORDER VALUE")), "DEFECT: the PDF shows the value " + pdf.get(pdf.size() - 2) + ", the table " + val(line, "ORDER VALUE"));
			Assert.assertTrue(pdf.get(pdf.size() - 1).equalsIgnoreCase(val(line, "ORDER STATUS")), "DEFECT: the PDF shows the status " + pdf.get(pdf.size() - 1) + ", the table " + val(line, "ORDER STATUS"));
			test.pass("The PDF shows the order like the table");
		} finally {
			y.Close_pdf(main_tab);
		}
	}
}
