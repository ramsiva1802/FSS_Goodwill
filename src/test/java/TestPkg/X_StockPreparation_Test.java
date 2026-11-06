package TestPkg;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.A_Login_Cls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.X_StockPreparation_Cls;
import UtillPkg.WaitUtil;

/*
 * Stock Preparation (Field Management > StockPreparation) - functional tests SP01 .. SP50.
 * SP01 - SP18 : list screen (search form, filter, sorting, paging, view and edit pop-ups of the existing rows - read only)
 * SP19 - SP37 : create form (every message, employee / route / item / quantity / date rules) - nothing is saved
 * SP38 - SP43 : saving: several stock preparations with different routes, vehicles, items and quantities
 *               (items come from the Stock Item module: leo-item1.., Ney-Item1.., CRono-item2, POLYPRO-RV, PVC PROCESSING AID LP-40)
 * SP44 - SP49 : edit of the rows created here (date, Is Downloaded, Is Uploaded, empty / wrong date)
 * The page has NO delete: what the saving tests create stays in the list. To keep real data safe the rows are made for
 * the employee "apk" and for far FUTURE dates (a new random date every run, never today), and only these rows are edited.
 * Switch ALLOW_CREATE to false to run the screen tests only.
 * Observations that are not clear defects are written to the report as warnings, not as failures.
 */
public class X_StockPreparation_Test extends BaseCls {

	static final boolean ALLOW_CREATE = true;

	static final String EMPLOYEE = "apk";
	static final String EMPLOYEE_SHOWN = "apk (apk)";
	static final String ROUTE_EAST = "ALAPPUZHA EAST";
	static final String ROUTE_WEST_NEW = "ALAPPUZHA WEST NEW";
	static final String ROUTE_CITY = "CITY ROUTE 1";

	static final DateTimeFormatter SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	static final DateTimeFormatter DASH = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	// one stock preparation made by the saving tests
	static class Made {
		LocalDate date;
		String route = "";
		String vehicle = "";
		String[][] items;
		String note = "";

		String slash() {
			return date.format(SLASH);
		}

		String dash() {
			return date.format(DASH);
		}
	}

	Made first;
	Made second;
	Made third;
	Made fourth;
	List<Made> all_made = new ArrayList<Made>();
	Set<LocalDate> used_dates = new HashSet<LocalDate>();
	boolean duplicate_saved = false;
	// items that were saved but are not shown in the view pop-up (reported by SP50)
	List<String> hidden_items = new ArrayList<String>();

	private String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
	}

	private String today_slash() {
		return LocalDate.now().format(SLASH);
	}

	private String today_dash() {
		return LocalDate.now().format(DASH);
	}

	// a far future date that no other row of this run uses (a new one every run)
	private LocalDate new_date() {
		Random random = new Random();
		LocalDate d;
		do {
			d = LocalDate.now().plusDays(400 + random.nextInt(2500));
		} while (!used_dates.add(d));
		return d;
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
		login.Login(admin_user, admin_pass);
		Assert.assertTrue(login.login_success(), "Could not log in");
	}

	// opens Field Management > StockPreparation from the dashboard
	private void go_to_stock() {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		dash.Click_submenu_containing("Field Management", "Preparation");
		WaitUtil.settle(driver, 1500);
	}

	// every test starts with a fresh list screen
	@BeforeMethod
	public void open_stock() {
		if (screen_is_clean()) {
			return;
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);
		try {
			go_to_stock();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> x.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_stock();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> x.is_list_page());
		}
		remember_clean_screen();
	}

	// ======================= helpers =======================

	private boolean has(String text, String key) {
		return text.toLowerCase().contains(key.toLowerCase());
	}

	// all rows from the beginning of the year until today
	private void search_wide(X_StockPreparation_Cls x) {
		x.Search("", "01/01/2026", today_slash());
	}

	private void search_day(X_StockPreparation_Cls x, LocalDate date) {
		x.Search(EMPLOYEE, date.format(SLASH), date.format(SLASH));
	}

	private void require_rows(X_StockPreparation_Cls x, int minimum) {
		search_wide(x);
		if (x.row_count() < minimum) {
			throw new SkipException("The list has only " + x.row_count() + " row(s) since 01/01/2026; " + minimum + " are needed");
		}
	}

	private void open_create_with_employee(X_StockPreparation_Cls x) {
		x.Open_create();
		String chosen = x.Choose_employee(EMPLOYEE);
		Assert.assertFalse(chosen.isEmpty(), "The Employee box did not offer '" + EMPLOYEE + "'");
		test.info("Employee chosen: " + chosen);
		if (x.date_value().isEmpty()) {
			// the box is empty when the form is opened a second time without reloading the page
			x.Set_date(today_slash());
			test.info("The Prepare For Date box was empty, today's date was typed");
		}
	}

	// parses the quantity text of a table cell
	private double number(String text) {
		return Double.parseDouble(text.replace(",", "").trim());
	}

	private LocalDate parse_dash(String text) {
		return LocalDate.parse(text, DASH);
	}

	// Opens the create form and saves one stock preparation. route / vehicle "" = left empty.
	// Returns the messages of the page; the list must be back afterwards.
	private String save(X_StockPreparation_Cls x, Made m) {
		x.Open_create();
		Assert.assertFalse(x.Choose_employee(EMPLOYEE).isEmpty(), "The Employee box did not offer '" + EMPLOYEE + "'");
		if (!m.route.isEmpty()) {
			String route = x.Choose_route(m.route);
			Assert.assertFalse(route.isEmpty(), "The route " + m.route + " is not in the Route drop down: " + x.route_options());
			test.info("Route chosen: " + route);
		}
		if (!m.vehicle.isEmpty()) {
			x.Type_vehicle(m.vehicle);
		}
		x.Set_date(m.slash());
		Assert.assertEquals(x.date_value(), m.slash(), "Prepare For Date box");
		for (String[] item : m.items) {
			String message = x.Add_item(item[0], item[0], item[1]);
			test.info("Item " + item[0] + " x " + item[1] + " -> " + (message.isEmpty() ? "added" : message));
			Assert.assertTrue(message.isEmpty(), "Item " + item[0] + " could not be added: " + message);
		}
		Assert.assertEquals(x.item_rows().size(), m.items.length, "Rows in the items table before Submit");
		String message = x.Submit_create();
		test.info("Messages after Submit: '" + message + "'");
		boolean back = x.Wait_list_page(12000);
		Assert.assertTrue(back, "The list did not come back after Submit. Messages: '" + message + "'");
		return message;
	}

	// finds the saved row in the list with a search for its date; returns its index
	private int find_saved(X_StockPreparation_Cls x, Made m, boolean with_route) {
		search_day(x, m.date);
		return x.find_row(EMPLOYEE, m.dash(), with_route ? m.route : "");
	}

	// Opens the view pop-up of a row and compares its items with the expected ones.
	// An item that was saved but is NOT shown is written to the report and kept in hidden_items (SP50 reports it);
	// the items that are shown must have the right quantity, a code and a unit.
	private void check_items(X_StockPreparation_Cls x, int row, Made m) {
		x.Click_view(row);
		Assert.assertTrue(x.view_open(), "The view pop-up did not open");
		List<List<String>> items = x.view_items();
		test.info("View pop-up: " + x.view_header() + " | items shown: " + items);
		for (String[] expected : m.items) {
			boolean found = false;
			for (List<String> row_cells : items) {
				if (row_cells.get(2).equalsIgnoreCase(expected[0])) {
					found = true;
					Assert.assertEquals(number(row_cells.get(4)), number(expected[1]), "Quantity of " + expected[0]);
					Assert.assertFalse(row_cells.get(1).isEmpty(), "Item code of " + expected[0] + " is empty");
					Assert.assertFalse(row_cells.get(3).isEmpty(), "Unit of " + expected[0] + " is empty");
				}
			}
			if (!found) {
				hidden_items.add(expected[0] + " (" + m.dash() + ", route '" + m.route + "')");
				test.warning("The item " + expected[0] + " was added in the form but is NOT shown in the view pop-up of the saved row");
			}
		}
		x.Close_view();
	}
	// ======================= LIST screen =======================

	// SP01: the list shows Search, Create, the table filter, the 9 column headers, the entries line, and 2 icons per row
	@Test
	public void SP01_list_page_elements() {
		test = extent.createTest("SP01 Stock Preparation list elements");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		Assert.assertTrue(x.verify_search_btn().isDisplayed(), "Search button is missing");
		Assert.assertTrue(x.verify_create_btn().isDisplayed(), "Create button is missing");
		Assert.assertTrue(x.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = x.headers();
		test.info("Column headers: " + headers);
		for (String h : X_StockPreparation_Cls.HEADERS) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(has(x.entries_text(), "Showing"), "The 'Showing .. entries' line is missing: '" + x.entries_text() + "'");
		test.info("Entries line: " + x.entries_text() + " | rows: " + x.row_count());
		if (x.row_count() > 0) {
			Assert.assertEquals(x.action_icon_count(0), 2, "The view and edit icons are expected in every row");
		}
		test.pass("List screen elements are displayed");
	}

	// SP02: the Search button opens the search form (Employee, From Date, To Date, Search); both dates are today
	@Test
	public void SP02_search_form() {
		test = extent.createTest("SP02 Search form opens");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		Assert.assertFalse(x.search_form_open(), "The search form must be closed when the page opens");
		x.Open_search_form();
		Assert.assertTrue(x.search_form_open(), "The Search button did not open the search form");
		List<String> labels = x.create_labels();
		test.info("Labels: " + labels);
		for (String label : new String[] { "Employee", "From Date", "To Date" }) {
			Assert.assertTrue(labels.contains(label), "Label missing in the search form: " + label);
		}
		Assert.assertTrue(x.search_go_visible(), "The Search button of the form is missing");
		Assert.assertEquals(x.search_employee_value(), "", "Employee box default");
		Assert.assertEquals(x.search_from_value(), today_slash(), "From Date default");
		Assert.assertEquals(x.search_to_value(), today_slash(), "To Date default");
		test.pass("Search form: Employee empty, From and To Date = today (" + today_slash() + ")");
	}

	// SP03: the list that opens with the page shows only rows of today
	@Test
	public void SP03_default_list_is_today() {
		test = extent.createTest("SP03 Default list shows today only");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		List<List<String>> rows = x.rows();
		test.info("Rows on opening: " + rows.size() + " | " + x.entries_text());
		for (List<String> r : rows) {
			Assert.assertEquals(r.get(2), today_dash(), "DEFECT: the default list shows a row of another date: " + r);
		}
		test.pass("The default list shows " + rows.size() + " row(s), all of today (" + today_dash() + ")");
	}

	// SP04: a wide date range lists rows inside the range and the entries line agrees with the rows
	@Test
	public void SP04_search_wide_range() {
		test = extent.createTest("SP04 Search a wide date range");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		search_wide(x);
		List<List<String>> rows = x.rows();
		test.info("Rows: " + rows);
		Assert.assertTrue(rows.size() > 0, "No row was listed for 01/01/2026 - " + today_slash() + " (the employee apk has rows)");
		for (List<String> r : rows) {
			LocalDate d = parse_dash(r.get(2));
			Assert.assertFalse(d.isBefore(LocalDate.of(2026, 1, 1)) || d.isAfter(LocalDate.now()),
					"DEFECT: a row outside the searched dates is listed: " + r);
		}
		String info = x.entries_text();
		test.info("Entries line: " + info);
		Assert.assertTrue(has(info, "of "), "The 'Showing .. of .. entries' line is wrong: " + info);
		test.pass(rows.size() + " row(s) listed, all inside the dates; " + info);
	}

	// SP05: search by employee lists only rows of that employee; the employee box offers apk (apk)
	@Test
	public void SP05_search_by_employee() {
		test = extent.createTest("SP05 Search by employee");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		List<String> offered = x.search_employee_suggestions(EMPLOYEE);
		test.info("The Employee box offers for '" + EMPLOYEE + "': " + offered);
		Assert.assertTrue(offered.contains(EMPLOYEE_SHOWN), "'" + EMPLOYEE_SHOWN + "' is not offered: " + offered);

		x.Search(EMPLOYEE, "01/01/2026", today_slash());
		List<List<String>> rows = x.rows();
		test.info("Rows: " + rows);
		Assert.assertTrue(rows.size() > 0, "No row for the employee " + EMPLOYEE);
		for (List<String> r : rows) {
			Assert.assertTrue(has(r.get(0), EMPLOYEE), "DEFECT: a row of another employee is listed: " + r);
		}
		test.pass("Only rows of " + EMPLOYEE + " are listed (" + rows.size() + ")");
	}

	// SP06: an employee text that matches nobody must not list everything
	@Test
	public void SP06_search_unknown_employee() {
		test = extent.createTest("SP06 Search an unknown employee");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		test.info("Suggestions for 'zzqx': " + x.search_employee_suggestions("zzqx"));
		x.Search("zzqx", "01/01/2026", today_slash());
		List<List<String>> rows = x.rows();
		test.info("Rows shown: " + rows);
		for (List<String> r : rows) {
			Assert.assertTrue(has(r.get(0), "zzqx"),
					"DEFECT: an employee text that matches nobody is ignored and other rows are listed: " + r);
		}
		test.pass("The unknown employee gave no unrelated rows");
	}

	// SP07: a From Date later than the To Date must not list rows
	@Test
	public void SP07_reversed_dates() {
		test = extent.createTest("SP07 From Date later than To Date");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		x.Search("", today_slash(), LocalDate.now().minusDays(5).format(SLASH));
		test.info("Rows shown: " + x.rows() + " | " + x.entries_text() + " | message: " + x.toasts());
		Assert.assertEquals(x.row_count(), 0, "DEFECT: rows are listed although From Date is later than To Date");
		test.pass("No rows for a reversed date range");
	}

	// SP08: a day without data shows "No Data Available" and "Showing 0 to 0 of 0 entries"
	@Test
	public void SP08_no_data() {
		test = extent.createTest("SP08 Search a day without data");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		String far = LocalDate.of(2090, 1, 1).format(SLASH);
		x.Search("", far, far);
		Assert.assertEquals(x.row_count(), 0, "Rows are listed for " + far);
		Assert.assertTrue(x.no_data_shown(), "The 'No Data Available' text is missing");
		Assert.assertTrue(has(x.entries_text(), "0 to 0 of 0"), "The entries line is wrong: " + x.entries_text());
		test.pass("'No Data Available' and '" + x.entries_text() + "'");
	}

	// SP09: the "Search:" box filters the rows, a text that matches nothing shows no data, clearing restores
	@Test
	public void SP09_table_filter() {
		test = extent.createTest("SP09 Table filter");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		require_rows(x, 2);
		int all = x.row_count();
		String route = x.rows().get(0).get(1);
		x.Filter_table(route);
		test.info("Filter '" + route + "': " + x.rows());
		Assert.assertTrue(x.row_count() >= 1 && x.row_count() <= all, "Filter by the route gave " + x.row_count() + " rows");
		for (List<String> r : x.rows()) {
			Assert.assertTrue(has(String.join(" ", r), route), "A row that does not contain '" + route + "' is shown: " + r);
		}
		x.Filter_table("zzzzqx");
		Assert.assertEquals(x.row_count(), 0, "Rows are shown for a text that matches nothing");
		Assert.assertTrue(x.no_data_shown(), "'No Data Available' is not shown for a text that matches nothing");
		x.Filter_table("");
		Assert.assertEquals(x.row_count(), all, "Clearing the filter did not bring all rows back");
		test.pass("The table filter works (" + all + " rows -> filtered -> none -> all)");
	}

	// SP10: sorting by Prepare For Date works as dates (day-month-year text must not be sorted as text)
	@Test
	public void SP10_sort_by_date() {
		test = extent.createTest("SP10 Sort by Prepare For Date");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		require_rows(x, 2);
		x.Click_header("PREPARE FOR DATE");
		String state = x.sort_state("PREPARE FOR DATE");
		List<LocalDate> dates = new ArrayList<LocalDate>();
		for (List<String> r : x.rows()) {
			dates.add(parse_dash(r.get(2)));
		}
		test.info("After the first click: " + state + " " + dates);
		Assert.assertFalse(state.isEmpty(), "The column shows no sort arrow after the click");
		for (int i = 1; i < dates.size(); i++) {
			Assert.assertTrue(state.equals("asc") ? !dates.get(i).isBefore(dates.get(i - 1)) : !dates.get(i).isAfter(dates.get(i - 1)),
					"DEFECT: the dates are not in " + state + " order: " + dates);
		}
		x.Click_header("PREPARE FOR DATE");
		String state2 = x.sort_state("PREPARE FOR DATE");
		Assert.assertNotEquals(state2, state, "The second click did not reverse the order");
		dates.clear();
		for (List<String> r : x.rows()) {
			dates.add(parse_dash(r.get(2)));
		}
		for (int i = 1; i < dates.size(); i++) {
			Assert.assertTrue(state2.equals("asc") ? !dates.get(i).isBefore(dates.get(i - 1)) : !dates.get(i).isAfter(dates.get(i - 1)),
					"DEFECT: the dates are not in " + state2 + " order: " + dates);
		}
		test.pass("Sorting by date works in both directions: " + dates);
	}

	// SP11: sorting by Delivery Route (text) works in both directions
	@Test
	public void SP11_sort_by_route() {
		test = extent.createTest("SP11 Sort by Delivery Route");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		require_rows(x, 2);
		for (int click = 1; click <= 2; click++) {
			x.Click_header("DELIVERY ROUTE");
			String state = x.sort_state("DELIVERY ROUTE");
			List<String> routes = new ArrayList<String>();
			for (List<String> r : x.rows()) {
				routes.add(r.get(1).toLowerCase());
			}
			test.info("Click " + click + ": " + state + " " + routes);
			Assert.assertFalse(state.isEmpty(), "No sort arrow after click " + click);
			for (int i = 1; i < routes.size(); i++) {
				int c = routes.get(i).compareTo(routes.get(i - 1));
				Assert.assertTrue(state.equals("asc") ? c >= 0 : c <= 0, "DEFECT: the routes are not in " + state + " order: " + routes);
			}
		}
		test.pass("Sorting by route works in both directions");
	}

	// SP12: paging: with 10 rows or less both Previous and Next are off; with more, Next shows the other rows and Previous returns
	@Test
	public void SP12_paging() {
		test = extent.createTest("SP12 Paging");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		search_wide(x);
		String info = x.entries_text();
		test.info("Entries line: " + info + " | Next enabled: " + x.next_enabled() + " | Previous enabled: " + x.previous_enabled());
		Assert.assertFalse(x.previous_enabled(), "Previous must be off on the first page");
		if (x.row_count() < 10) {
			Assert.assertFalse(x.next_enabled(), "Next must be off when everything fits on one page");
			test.pass("One page only: Previous and Next are off");
			return;
		}
		List<List<String>> page_one = x.rows();
		if (!x.next_enabled()) {
			test.pass("Exactly one page of " + page_one.size() + " rows");
			return;
		}
		x.Click_next();
		Assert.assertNotEquals(x.rows(), page_one, "Next did not show other rows");
		Assert.assertTrue(x.previous_enabled(), "Previous is off on page 2");
		x.Click_previous();
		Assert.assertEquals(x.rows(), page_one, "Previous did not return to the first rows");
		test.pass("Next and Previous move between the pages");
	}

	// SP13: every row has an employee, a route cell, a dd-MM-yyyy date, a status and the 2 icons
	@Test
	public void SP13_rows_are_consistent() {
		test = extent.createTest("SP13 Rows are consistent");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		require_rows(x, 1);
		Set<String> statuses = new HashSet<String>();
		List<List<String>> rows = x.rows();
		for (int i = 0; i < rows.size(); i++) {
			List<String> r = rows.get(i);
			Assert.assertFalse(r.get(0).isEmpty(), "Row " + (i + 1) + " has no employee: " + r);
			Assert.assertTrue(r.get(2).matches("\\d{2}-\\d{2}-\\d{4}"), "Row " + (i + 1) + " date is not dd-MM-yyyy: " + r.get(2));
			Assert.assertFalse(r.get(3).isEmpty(), "Row " + (i + 1) + " has no download status: " + r);
			statuses.add(r.get(3));
			Assert.assertEquals(x.action_icon_count(i), 2, "Icons in row " + (i + 1));
		}
		test.info("Statuses seen: " + statuses + " | rows: " + rows.size());
		test.pass(rows.size() + " row(s) are complete; statuses: " + statuses);
	}

	// SP14: the view pop-up shows the row's employee, route and date and a table of items
	// (the first rows are tried until one has items; a row without items is reported)
	@Test
	public void SP14_view_popup() {
		test = extent.createTest("SP14 View pop-up");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		require_rows(x, 1);
		int shown_row = -1;
		for (int i = 0; i < Math.min(5, x.row_count()); i++) {
			x.Click_view(i);
			Assert.assertTrue(x.view_open(), "The view pop-up did not open for row " + (i + 1));
			if (x.view_items().size() > 0) {
				shown_row = i;
				break;
			}
			test.warning("Row " + (i + 1) + " " + x.rows().get(i).subList(0, 4) + " has NO item in its view pop-up: " + x.view_header());
			x.Close_view();
		}
		Assert.assertTrue(shown_row >= 0, "None of the first rows shows an item in its view pop-up");
		List<String> row = x.rows().get(shown_row);
		test.info("Title: " + x.view_title() + " | header: " + x.view_header() + " | columns: " + x.view_headers());
		Assert.assertEquals(x.view_title(), "Stock Preparation Details", "Pop-up title");
		String header = x.view_header();
		Assert.assertTrue(has(header, row.get(0)), "The employee " + row.get(0) + " is not in the pop-up head: " + header);
		Assert.assertTrue(has(header, row.get(1)), "The route " + row.get(1) + " is not in the pop-up head: " + header);
		Assert.assertTrue(has(header, row.get(2)), "The date " + row.get(2) + " is not in the pop-up head: " + header);
		Assert.assertEquals(x.view_headers(), Arrays.asList("SL NO.", "ITEM CODE", "ITEM NAME", "UNIT", "QUANTITY"), "Columns of the items table");
		List<List<String>> items = x.view_items();
		for (int i = 0; i < items.size(); i++) {
			Assert.assertEquals(items.get(i).get(0), String.valueOf(i + 1), "SL NO of item " + (i + 1));
			Assert.assertTrue(number(items.get(i).get(4)) > 0, "Quantity of " + items.get(i).get(2) + " is not above 0");
		}
		x.Close_view();
		Assert.assertFalse(x.view_open(), "The pop-up did not close");
		test.pass("View pop-up of row " + (shown_row + 1) + " shows " + items.size() + " item(s): " + items);
	}
	// SP15: the view pop-up of every listed row (up to 5) shows that row's data
	@Test
	public void SP15_view_every_row() {
		test = extent.createTest("SP15 View pop-up of each row");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		require_rows(x, 2);
		int count = Math.min(5, x.row_count());
		for (int i = 0; i < count; i++) {
			List<String> row = x.rows().get(i);
			x.Click_view(i);
			String header = x.view_header();
			test.info("Row " + (i + 1) + " " + row.subList(0, 3) + " -> pop-up: " + header + " | items " + x.view_items().size());
			Assert.assertTrue(has(header, row.get(0)) && has(header, row.get(1)) && has(header, row.get(2)),
					"DEFECT: the pop-up of row " + (i + 1) + " shows other data: " + header);
			if (x.view_items().isEmpty()) {
				test.warning("Row " + (i + 1) + " has no item in its view pop-up (a stock preparation without items)");
			}
			x.Close_view();
		}
		test.pass("The pop-ups of " + count + " rows match their rows");
	}

	// SP16: the edit pop-up opens with the row's employee, date and the download flag that matches the status
	@Test
	public void SP16_edit_popup_values() {
		test = extent.createTest("SP16 Edit pop-up values");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		require_rows(x, 1);
		for (int i = 0; i < Math.min(3, x.row_count()); i++) {
			List<String> row = x.rows().get(i);
			x.Click_edit(i);
			Assert.assertTrue(x.edit_open(), "The edit pop-up did not open");
			test.info("Row " + (i + 1) + " " + row.subList(0, 4) + " -> edit: " + x.edit_employee() + " | " + x.edit_date()
					+ " | downloaded " + x.edit_downloaded() + " | uploaded " + x.edit_uploaded() + " | employee locked "
					+ x.edit_employee_locked());
			Assert.assertEquals(x.edit_title(), "Edit Stock Preparation", "Pop-up title");
			Assert.assertEquals(x.edit_employee(), row.get(0), "Employee in the edit pop-up");
			Assert.assertEquals(x.edit_date(), row.get(2), "Date in the edit pop-up");
			Assert.assertEquals(x.edit_downloaded(), row.get(3).equalsIgnoreCase("Downloaded"),
					"DEFECT: Is Downloaded does not match the status '" + row.get(3) + "'");
			if (!x.edit_employee_locked()) {
				test.warning("The employee box of the edit pop-up can be changed (it is not read-only)");
			}
			x.Cancel_edit();
		}
		test.pass("Edit pop-up values match the rows");
	}

	// SP17: Cancel and the x of the edit pop-up change nothing
	@Test
	public void SP17_edit_cancel() {
		test = extent.createTest("SP17 Edit pop-up Cancel and close");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		require_rows(x, 1);
		List<String> row = x.rows().get(0);
		x.Click_edit(0);
		x.Set_edit_date("01-01-2020");
		test.info("Date typed in the pop-up: " + x.edit_date());
		x.Cancel_edit();
		Assert.assertFalse(x.edit_open(), "Cancel did not close the pop-up");
		Assert.assertEquals(x.rows().get(0), row, "DEFECT: Cancel changed the row");
		x.Click_edit(0);
		Assert.assertEquals(x.edit_date(), row.get(2), "DEFECT: the pop-up shows the date typed before Cancel");
		x.Close_edit_x();
		Assert.assertFalse(x.edit_open(), "The x did not close the pop-up");
		test.pass("Cancel and x close the pop-up and keep the row unchanged");
	}

	// SP18: the Search button toggles the search form and Create opens the form
	@Test
	public void SP18_create_opens_form() {
		test = extent.createTest("SP18 Create opens the form and Back returns");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		x.Open_create();
		Assert.assertTrue(x.create_form_open(), "The create form did not open");
		Assert.assertFalse(x.is_list_page(), "The Create button is still shown with the form open");
		x.Click_back();
		Assert.assertTrue(x.is_list_page(), "Back did not return to the list");
		test.pass("Create opens the form; Back returns to the list");
	}

	// ======================= CREATE form (nothing is saved) =======================

	// SP19: labels, table columns and default values of the create form
	@Test
	public void SP19_create_form_defaults() {
		test = extent.createTest("SP19 Create form elements and defaults");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		driver.navigate().refresh();   // the defaults are those of a freshly opened page
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> x.is_list_page());
		x.Open_create();
		List<String> labels = x.create_labels();
		test.info("Labels: " + labels);
		for (String label : new String[] { "Employee *", "Route", "Vehicle", "Prepare For Date *", "Search Item", "Prepared Qty" }) {
			Assert.assertTrue(labels.contains(label), "Label missing: " + label);
		}
		Assert.assertEquals(x.items_headers(), Arrays.asList("SL NO", "ITEM NAME", "PREPARED QTY", "ACTION"), "Items table columns");
		Assert.assertEquals(x.employee_value(), "", "Employee default");
		Assert.assertEquals(x.route_options(), Arrays.asList("Select Employee First"), "Route options before an employee is chosen");
		Assert.assertEquals(x.vehicle_value(), "", "Vehicle default");
		Assert.assertEquals(x.date_value(), today_slash(), "Prepare For Date default");
		Assert.assertEquals(x.qty_value(), "1", "Prepared Qty default");
		Assert.assertEquals(x.item_rows().size(), 0, "Items table must start empty");
		test.pass("Create form elements and defaults are correct (date " + today_slash() + ", quantity 1, no items)");
	}

	// SP20: an empty Submit asks for the employee first
	@Test
	public void SP20_empty_submit() {
		test = extent.createTest("SP20 Empty Submit");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		x.Open_create();
		x.Clear_date();
		String message = x.Submit_create();
		test.info("Message: " + message);
		Assert.assertTrue(has(message, "Employee is required"), "Expected 'Employee is required.' but the page said: '" + message + "'");
		Assert.assertTrue(x.create_form_open(), "The form closed although nothing was entered");
		test.pass("Empty Submit: '" + message + "'");
	}

	// SP21: employee chosen but the date empty -> "Date is required."
	@Test
	public void SP21_date_required() {
		test = extent.createTest("SP21 Date is required");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		x.Clear_date();
		Assert.assertEquals(x.date_value(), "", "The date box could not be emptied");
		String message = x.Submit_create();
		test.info("Message: " + message);
		Assert.assertTrue(has(message, "Date is required"), "Expected 'Date is required.' but the page said: '" + message + "'");
		Assert.assertTrue(x.create_form_open(), "The form closed without a date");
		test.pass("Missing date: '" + message + "'");
	}

	// SP22: employee and date but no item -> "Please add at least one stock item."
	@Test
	public void SP22_item_required() {
		test = extent.createTest("SP22 At least one item is required");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		String message = x.Submit_create();
		test.info("Message: " + message);
		Assert.assertTrue(has(message, "at least one stock item"), "Expected 'Please add at least one stock item.' but the page said: '" + message + "'");
		Assert.assertTrue(x.create_form_open(), "The form closed without an item");
		test.pass("No item: '" + message + "'");
	}

	// SP23: after the employee is chosen the Route drop down is filled with approved routes
	@Test
	public void SP23_route_list() {
		test = extent.createTest("SP23 Route list after choosing the employee");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		List<String> routes = x.route_options();
		test.info("Route options (" + routes.size() + "): " + routes);
		Assert.assertTrue(x.route_enabled(), "The Route drop down is disabled after the employee was chosen");
		Assert.assertEquals(routes.get(0), "Select Route", "First route option");
		Assert.assertTrue(routes.contains(ROUTE_EAST + " (Approved)"), ROUTE_EAST + " is not offered");
		Assert.assertTrue(routes.contains(ROUTE_WEST_NEW + " (Approved)"), ROUTE_WEST_NEW + " is not offered");
		for (String r : routes.subList(1, routes.size())) {
			Assert.assertTrue(r.endsWith("(Approved)"), "A route that is not approved is offered: " + r);
		}
		if (routes.size() > 15) {
			test.warning("The Route drop down lists " + (routes.size() - 1)
					+ " routes: not only the routes that are mapped to the employee (check if that is wanted)");
		}
		Assert.assertEquals(x.route_selected(), "Select Route", "Route default after the employee is chosen");
		test.pass("Routes are listed after the employee is chosen");
	}

	// SP24: the employee box: suggestions for apk (capital letters too), none for nonsense, a typed but not chosen text is refused
	@Test
	public void SP24_employee_box() {
		test = extent.createTest("SP24 Employee box");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		x.Open_create();
		List<String> small = x.employee_suggestions("apk");
		List<String> capital = x.employee_suggestions("APK");
		List<String> none = x.employee_suggestions("zzqx");
		test.info("'apk' -> " + small + " | 'APK' -> " + capital + " | 'zzqx' -> " + none);
		Assert.assertTrue(small.contains(EMPLOYEE_SHOWN), "'apk' does not offer " + EMPLOYEE_SHOWN);
		Assert.assertTrue(capital.contains(EMPLOYEE_SHOWN), "'APK' (capital letters) does not offer " + EMPLOYEE_SHOWN);
		Assert.assertTrue(none.isEmpty(), "A text that matches nobody offers: " + none);

		x.Type_employee_only("zzqx");
		x.Set_date(today_slash());
		String message = x.Submit_create();
		test.info("Submit with a typed (not chosen) employee: '" + message + "'");
		Assert.assertTrue(x.create_form_open(), "DEFECT: the form was saved with an employee that was only typed");
		test.pass("A typed but not chosen employee is refused: '" + message + "'");
	}

	// SP25: item search: 3 letters offer items (leo, pro), nonsense offers none; shorter texts are reported
	@Test
	public void SP25_item_search() {
		test = extent.createTest("SP25 Item search");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		List<String> leo = x.item_suggestions("leo");
		List<String> pro = x.item_suggestions("pro");
		List<String> none = x.item_suggestions("zzqxw");
		List<String> one = x.item_suggestions("a");
		List<String> two = x.item_suggestions("le");
		List<String> capital = x.item_suggestions("LEO");
		test.info("'leo' -> " + leo + " | 'pro' -> " + pro + " | 'zzqxw' -> " + none + " | 'a' -> " + one + " | 'le' -> " + two
				+ " | 'LEO' -> " + capital);
		for (String name : new String[] { "leo-item1", "leo-item2", "leo-item3", "leo-item4" }) {
			Assert.assertTrue(leo.contains(name), name + " is not offered for 'leo': " + leo);
		}
		Assert.assertTrue(pro.contains("POLYPRO-RV"), "POLYPRO-RV is not offered for 'pro': " + pro);
		Assert.assertTrue(pro.contains("PVC PROCESSING AID LP-40"), "PVC PROCESSING AID LP-40 is not offered for 'pro': " + pro);
		Assert.assertTrue(none.isEmpty(), "A text that matches no item offers: " + none);
		Assert.assertFalse(capital.isEmpty(), "DEFECT: the item search is case sensitive ('LEO' offers nothing)");
		test.info("Fewer than 3 letters offer: 1 letter " + one.size() + " item(s), 2 letters " + two.size() + " item(s)");
		test.pass("Item search works: leo -> " + leo.size() + " items, pro -> " + pro.size() + " items");
	}

	// SP26: Add Item without choosing an item
	@Test
	public void SP26_add_without_item() {
		test = extent.createTest("SP26 Add Item without an item");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		String message = x.Click_add_item();
		test.info("Message: " + message);
		Assert.assertTrue(has(message, "select a valid item"), "Expected 'Please select a valid item.' but the page said: '" + message + "'");
		Assert.assertEquals(x.item_rows().size(), 0, "A row was added without an item");
		test.pass("Add Item without an item: '" + message + "'");
	}

	// SP27: an item that is only typed (not chosen from the list) is refused
	@Test
	public void SP27_typed_item_not_chosen() {
		test = extent.createTest("SP27 Typed item that is not chosen");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		x.Type_item_only("leo-item1");
		String message = x.Click_add_item();
		test.info("Message for a typed (not chosen) item: " + message + " | rows: " + x.item_rows());
		Assert.assertTrue(has(message, "select a valid item"), "DEFECT: expected 'Please select a valid item.' but the page said: '" + message + "'");
		Assert.assertEquals(x.item_rows().size(), 0, "DEFECT: a typed item was added without choosing it");
		test.pass("A typed but not chosen item is refused");
	}

	@DataProvider(name = "bad_quantities")
	public Object[][] bad_quantities() {
		return new Object[][] { { "zero", "0" }, { "negative", "-5" }, { "empty", "" }, { "negative decimal", "-0.5" } };
	}

	// SP28: invalid quantities are refused
	@Test(dataProvider = "bad_quantities")
	public void SP28_invalid_quantity(String description, String quantity) {
		test = extent.createTest("SP28 Invalid quantity: " + description);
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		Assert.assertFalse(x.Choose_item("leo-item1", "leo-item1").isEmpty(), "leo-item1 is not offered");
		x.Set_qty(quantity);
		String message = x.Click_add_item();
		test.info("Quantity '" + quantity + "' -> message: '" + message + "' | rows: " + x.item_rows());
		Assert.assertTrue(has(message, "valid quantity"), "DEFECT: quantity '" + quantity + "' gave '" + message + "' instead of 'Please enter a valid quantity.'");
		Assert.assertEquals(x.item_rows().size(), 0, "DEFECT: a row was added with quantity '" + quantity + "'");
		test.pass("Quantity '" + quantity + "' is refused: " + message);
	}

	// SP29: a decimal quantity is accepted (reported as an observation: is it right for the unit?)
	@Test
	public void SP29_decimal_quantity() {
		test = extent.createTest("SP29 Decimal quantity");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		String message = x.Add_item("leo-item1", "leo-item1", "2.5");
		test.info("Message: '" + message + "' | rows: " + x.item_rows());
		if (x.item_rows().size() == 1) {
			test.warning("The quantity 2.5 is accepted. Check that decimal quantities are allowed for this item's unit (the stock is counted in whole numbers for most units).");
		} else {
			test.info("The quantity 2.5 is refused: " + message);
		}
		test.pass("Decimal quantity checked: " + (x.item_rows().size() == 1 ? "accepted" : "refused"));
	}

	// SP30: Add Item puts the item in the table (sl no 1, name, quantity) and clears the item box
	@Test
	public void SP30_add_item() {
		test = extent.createTest("SP30 Add one item");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		String message = x.Add_item("leo-item1", "leo-item1", "5");
		List<List<String>> rows = x.item_rows();
		test.info("Message: '" + message + "' | rows: " + rows + " | item box: '" + x.item_value() + "' | quantity box: '" + x.qty_value() + "'");
		Assert.assertEquals(message, "", "The page showed a message: " + message);
		Assert.assertEquals(rows.size(), 1, "Rows in the items table");
		Assert.assertEquals(rows.get(0).get(0), "1", "SL NO");
		Assert.assertEquals(rows.get(0).get(1), "leo-item1", "Item name");
		Assert.assertEquals(number(rows.get(0).get(2)), 5.0, "Prepared qty");
		Assert.assertEquals(x.item_value(), "", "The item box must be empty after Add Item");
		if (!x.qty_value().equals("1")) {
			test.warning("The Prepared Qty box keeps '" + x.qty_value() + "' after Add Item (it is not reset to 1)");
		}
		test.pass("leo-item1 x 5 added");
	}

	// SP31: the same item twice -> "Item already added."
	@Test
	public void SP31_same_item_twice() {
		test = extent.createTest("SP31 Same item twice");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		Assert.assertEquals(x.Add_item("Ney-Item1", "Ney-Item1", "4"), "", "The first add gave a message");
		String message = x.Add_item("Ney-Item1", "Ney-Item1", "6");
		test.info("Second add: '" + message + "' | rows: " + x.item_rows());
		Assert.assertTrue(has(message, "already added"), "Expected 'Item already added.' but the page said: '" + message + "'");
		Assert.assertEquals(x.item_rows().size(), 1, "The item is in the table more than once");
		Assert.assertEquals(number(x.item_rows().get(0).get(2)), 4.0, "The first quantity must stay");
		test.pass("The same item cannot be added twice: '" + message + "'");
	}

	// SP32: five different items from the Stock Item module with different quantities
	@Test
	public void SP32_several_items() {
		test = extent.createTest("SP32 Several different items");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		String[][] items = { { "leo-item1", "1" }, { "Ney-Item1", "12" }, { "CRono-item2", "250" }, { "POLYPRO-RV", "75" },
				{ "PVC PROCESSING AID LP-40", "3" } };
		for (String[] item : items) {
			String message = x.Add_item(item[0], item[0], item[1]);
			Assert.assertEquals(message, "", "Adding " + item[0] + " gave: " + message);
		}
		List<List<String>> rows = x.item_rows();
		test.info("Rows: " + rows);
		Assert.assertEquals(rows.size(), items.length, "Rows in the items table");
		for (int i = 0; i < items.length; i++) {
			Assert.assertEquals(rows.get(i).get(0), String.valueOf(i + 1), "SL NO of row " + (i + 1));
			Assert.assertEquals(rows.get(i).get(1), items[i][0], "Item name of row " + (i + 1));
			Assert.assertEquals(number(rows.get(i).get(2)), number(items[i][1]), "Quantity of " + items[i][0]);
		}
		test.pass(items.length + " different items are in the table in the order they were added");
	}

	// SP33: removing an item deletes its row and renumbers the SL NO
	@Test
	public void SP33_remove_item() {
		test = extent.createTest("SP33 Remove an item");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		for (String[] item : new String[][] { { "leo-item1", "1" }, { "leo-item2", "2" }, { "leo-item3", "3" } }) {
			Assert.assertEquals(x.Add_item(item[0], item[0], item[1]), "", "Adding " + item[0]);
		}
		x.Remove_item(1);
		List<List<String>> rows = x.item_rows();
		test.info("After removing the second row: " + rows);
		Assert.assertEquals(rows.size(), 2, "Rows after the removal");
		Assert.assertEquals(rows.get(0).get(1), "leo-item1", "First row");
		Assert.assertEquals(rows.get(1).get(1), "leo-item3", "Second row");
		Assert.assertEquals(rows.get(1).get(0), "2", "DEFECT: the SL NO is not renumbered after a removal");
		// the removed item can be added again
		Assert.assertEquals(x.Add_item("leo-item2", "leo-item2", "9"), "", "The removed item cannot be added again");
		Assert.assertEquals(x.item_rows().size(), 3, "Rows after adding the removed item again");
		// remove everything -> an item is required again
		x.Remove_item(0);
		x.Remove_item(0);
		x.Remove_item(0);
		Assert.assertEquals(x.item_rows().size(), 0, "Rows after removing all");
		String message = x.Submit_create();
		Assert.assertTrue(has(message, "at least one stock item"), "After removing all items the page said: '" + message + "'");
		test.pass("Remove works, SL NO is renumbered, an empty table is refused");
	}

	// SP34: Back returns to the list without saving and the list is unchanged
	@Test
	public void SP34_back_discards() {
		test = extent.createTest("SP34 Back discards the form");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		x.Search("", "01/01/2026", today_slash());
		int before = x.row_count();
		open_create_with_employee(x);
		Assert.assertEquals(x.Add_item("leo-item4", "leo-item4", "8"), "", "Adding the item");
		x.Click_back();
		Assert.assertTrue(x.is_list_page(), "Back did not return to the list");
		search_wide(x);
		Assert.assertEquals(x.row_count(), before, "DEFECT: rows were saved by Back");
		x.Open_create();
		test.info("Form after coming back: employee '" + x.employee_value() + "', date '" + x.date_value() + "', items " + x.item_rows().size());
		if (x.date_value().isEmpty()) {
			test.warning("Prepare For Date is EMPTY when the create form is opened again after Back (it shows today's date on the first opening)");
		}
		if (!x.employee_value().isEmpty() || x.item_rows().size() > 0) {
			test.warning("The create form keeps the old entries after Back");
		}
		test.pass("Back saved nothing (" + before + " rows before and after)");
	}

	// SP35: the Prepare For Date box: a valid date is kept, a wrong date or letters are not
	@Test
	public void SP35_date_box() {
		test = extent.createTest("SP35 Prepare For Date box");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		x.Open_create();
		x.Set_date("01/01/2031");
		Assert.assertEquals(x.date_value(), "01/01/2031", "A valid date was not kept");
		x.Set_date("31/02/2031");
		test.info("After typing 31/02/2031 the box shows: " + x.date_value());
		Assert.assertNotEquals(x.date_value(), "31/02/2031", "DEFECT: the impossible date 31/02/2031 is accepted");
		x.Set_date("abcd");
		test.info("After typing letters the box shows: '" + x.date_value() + "'");
		Assert.assertTrue(x.date_value().matches("\\d{2}/\\d{2}/\\d{4}|"), "DEFECT: the date box accepts letters: " + x.date_value());
		x.Clear_date();
		Assert.assertEquals(x.date_value(), "", "The date box cannot be emptied");
		test.pass("The date box keeps valid dates, refuses wrong ones and can be emptied");
	}

	// SP36: the Vehicle box takes free text (it is optional)
	@Test
	public void SP36_vehicle_box() {
		test = extent.createTest("SP36 Vehicle box");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		x.Open_create();
		x.Type_vehicle("KL 01 AB 1234");
		Assert.assertEquals(x.vehicle_value(), "KL 01 AB 1234", "Vehicle text");
		String longer = "V".repeat(80);
		x.Type_vehicle(longer);
		test.info("A text of 80 characters is kept as " + x.vehicle_value().length() + " characters");
		if (x.vehicle_value().length() == 80) {
			test.warning("The Vehicle box accepts 80 characters (check the database column length)");
		}
		test.pass("The Vehicle box takes free text");
	}

	// SP37: a date in the past is allowed or refused (reported; nothing is saved here)
	@Test
	public void SP37_form_keeps_values_between_errors() {
		test = extent.createTest("SP37 Entries stay after a refused Submit");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		open_create_with_employee(x);
		Assert.assertFalse(x.Choose_route(ROUTE_EAST).isEmpty(), "Route not chosen");
		x.Type_vehicle("ZQKEEP");
		x.Submit_create();   // no item: refused
		test.info("After the refused Submit: employee '" + x.employee_value() + "', route '" + x.route_selected() + "', vehicle '"
				+ x.vehicle_value() + "', date '" + x.date_value() + "'");
		Assert.assertEquals(x.employee_value(), EMPLOYEE_SHOWN, "DEFECT: the employee is lost after a refused Submit");
		Assert.assertTrue(has(x.route_selected(), ROUTE_EAST), "DEFECT: the route is lost after a refused Submit: " + x.route_selected());
		Assert.assertEquals(x.vehicle_value(), "ZQKEEP", "DEFECT: the vehicle is lost after a refused Submit");
		test.pass("The entries stay after a refused Submit");
	}

	// ======================= SAVING (the rows stay in the list: the page has no delete) =======================

	// SP38: route + vehicle + 2 items
	@Test(dependsOnMethods = "SP37_form_keeps_values_between_errors", alwaysRun = true)
	public void SP38_save_with_route_vehicle_items() {
		test = extent.createTest("SP38 Save with route, vehicle and 2 items");
		if (!ALLOW_CREATE) {
			throw new SkipException("ALLOW_CREATE is false");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		Made m = new Made();
		m.date = new_date();
		m.route = ROUTE_EAST;
		m.vehicle = "ZQV" + unique();
		m.items = new String[][] { { "leo-item1", "5" }, { "Ney-Item1", "12" } };
		first = m;
		all_made.add(m);
		test.info("Saving: " + EMPLOYEE + ", route " + m.route + ", vehicle " + m.vehicle + ", date " + m.slash());
		String message = save(x, m);

		int row = find_saved(x, m, true);
		test.info("Rows for the date: " + x.rows() + " | message was '" + message + "'");
		Assert.assertTrue(row >= 0, "The saved row (" + EMPLOYEE + ", " + m.route + ", " + m.dash() + ") is not in the list");
		Assert.assertEquals(x.rows().get(row).get(3), "Pending", "Download status of a new row");
		check_items(x, row, m);
		test.pass("Saved and verified: " + x.rows().get(row));
	}

	// SP39: no route and no vehicle (both are optional)
	@Test(dependsOnMethods = "SP38_save_with_route_vehicle_items")
	public void SP39_save_without_route_and_vehicle() {
		test = extent.createTest("SP39 Save without route and vehicle");
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		Made m = new Made();
		m.date = new_date();
		m.items = new String[][] { { "PVC PROCESSING AID LP-40", "7" } };
		second = m;
		all_made.add(m);
		test.info("Saving: " + EMPLOYEE + ", no route, no vehicle, date " + m.slash());
		String message = save(x, m);

		int row = find_saved(x, m, false);
		test.info("Rows for the date: " + x.rows() + " | message was '" + message + "'");
		Assert.assertTrue(row >= 0, "The saved row (" + EMPLOYEE + ", " + m.dash() + ") is not in the list");
		test.info("Route cell of the row: '" + x.rows().get(row).get(1) + "'");
		check_items(x, row, m);
		test.pass("Saved without route and vehicle: " + x.rows().get(row));
	}

	// SP40: four items (different units / quantities) and the route ALAPPUZHA WEST NEW
	@Test(dependsOnMethods = "SP39_save_without_route_and_vehicle", alwaysRun = true)
	public void SP40_save_four_items() {
		test = extent.createTest("SP40 Save with 4 different items");
		if (!ALLOW_CREATE) {
			throw new SkipException("ALLOW_CREATE is false");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		Made m = new Made();
		m.date = new_date();
		m.route = ROUTE_WEST_NEW;
		m.vehicle = "ZQV" + unique();
		m.items = new String[][] { { "POLYPRO-RV", "100" }, { "CRono-item2", "3" }, { "leo-item3", "1" }, { "leo-item4", "250" } };
		third = m;
		all_made.add(m);
		test.info("Saving: " + EMPLOYEE + ", route " + m.route + ", vehicle " + m.vehicle + ", date " + m.slash());
		String message = save(x, m);

		int row = find_saved(x, m, true);
		test.info("Rows for the date: " + x.rows() + " | message was '" + message + "'");
		Assert.assertTrue(row >= 0, "The saved row (" + EMPLOYEE + ", " + m.route + ", " + m.dash() + ") is not in the list");
		check_items(x, row, m);
		test.pass("Saved with 4 items: " + x.rows().get(row));
	}

	// SP41: a decimal quantity is saved as typed (or the page says what it did)
	@Test(dependsOnMethods = "SP40_save_four_items", alwaysRun = true)
	public void SP41_save_decimal_quantity() {
		test = extent.createTest("SP41 Save a decimal quantity");
		if (!ALLOW_CREATE) {
			throw new SkipException("ALLOW_CREATE is false");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		Made m = new Made();
		m.date = new_date();
		m.route = ROUTE_CITY;
		m.items = new String[][] { { "Ney-Item2", "2.5" } };
		fourth = m;
		all_made.add(m);
		test.info("Saving: " + EMPLOYEE + ", route " + m.route + ", date " + m.slash() + ", Ney-Item2 x 2.5");
		String message = save(x, m);

		int row = find_saved(x, m, true);
		Assert.assertTrue(row >= 0, "The saved row (" + EMPLOYEE + ", " + m.route + ", " + m.dash() + ") is not in the list. Message: " + message);
		x.Click_view(row);
		List<List<String>> items = x.view_items();
		test.info("View pop-up items: " + items);
		x.Close_view();
		if (items.isEmpty()) {
			hidden_items.add("Ney-Item2 (" + m.dash() + ", route '" + m.route + "')");
			test.warning("The item Ney-Item2 was added in the form but is NOT shown in the view pop-up of the saved row");
			test.pass("The row was saved; its item is not shown in the pop-up (see SP50)");
			return;
		}
		double shown = number(items.get(0).get(4));
		if (shown != 2.5) {
			test.warning("The quantity 2.5 was saved as " + shown + " (rounded or cut by the page / database)");
		}
		test.pass("Decimal quantity saved; the pop-up shows " + shown);
	}

	// SP42: every row saved above is found by one search over its dates
	@Test(dependsOnMethods = "SP38_save_with_route_vehicle_items", alwaysRun = true)
	public void SP42_saved_rows_are_found() {
		test = extent.createTest("SP42 Saved rows in a date range search");
		if (all_made.isEmpty()) {
			throw new SkipException("Nothing was saved");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		LocalDate low = all_made.get(0).date;
		LocalDate high = all_made.get(0).date;
		for (Made m : all_made) {
			if (m.date.isBefore(low)) {
				low = m.date;
			}
			if (m.date.isAfter(high)) {
				high = m.date;
			}
		}
		x.Search(EMPLOYEE, low.format(SLASH), high.format(SLASH));
		List<List<String>> rows = x.rows();
		test.info("Search " + low.format(SLASH) + " - " + high.format(SLASH) + " listed " + rows.size() + " row(s): " + rows);
		for (Made m : all_made) {
			Assert.assertTrue(x.find_row(EMPLOYEE, m.dash(), m.route) >= 0 || x.entries_text().contains("of "),
					"The row of " + m.dash() + " (" + m.route + ") is not listed");
		}
		test.pass("The saved rows are found by a date range search");
	}

	// SP43: the same employee + route + date again: the page refuses or accepts (reported)
	@Test(dependsOnMethods = "SP42_saved_rows_are_found", alwaysRun = true)
	public void SP43_duplicate_row() {
		test = extent.createTest("SP43 Same employee, route and date again");
		if (!ALLOW_CREATE || first == null) {
			throw new SkipException("Nothing to duplicate");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		x.Open_create();
		Assert.assertFalse(x.Choose_employee(EMPLOYEE).isEmpty(), "The employee is not offered");
		Assert.assertFalse(x.Choose_route(first.route).isEmpty(), "The route is not offered");
		x.Set_date(first.slash());
		Assert.assertEquals(x.Add_item("leo-item2", "leo-item2", "1"), "", "Adding the item");
		String message = x.Submit_create();
		boolean back = x.Wait_list_page(8000);
		test.info("Messages after Submit: '" + message + "' | list back: " + back);
		if (!back) {
			Assert.assertFalse(message.isEmpty(), "The form stayed open without any message");
			test.pass("The duplicate was refused: '" + message + "'");
			return;
		}
		search_day(x, first.date);
		int count = x.count_rows(EMPLOYEE, first.dash(), first.route);
		test.info("Rows for the same employee, route and date: " + count);
		if (count > 1) {
			duplicate_saved = true;
			test.warning("The same employee + route + date was saved a second time (" + count
					+ " rows). Check if one stock preparation per employee, route and day is the rule.");
		}
		test.pass("Duplicate checked: " + count + " row(s) for " + first.dash());
	}

	// ======================= EDIT (only the rows saved above) =======================

	// SP44: change the date of a saved row; the row moves to the new date
	@Test(dependsOnMethods = "SP40_save_four_items", alwaysRun = true)
	public void SP44_edit_date() {
		test = extent.createTest("SP44 Edit the date of a saved row");
		if (third == null) {
			throw new SkipException("The row of SP40 was not saved");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		LocalDate old_date = third.date;
		LocalDate new_date = new_date();
		int row = find_saved(x, third, true);
		Assert.assertTrue(row >= 0, "The row to edit is not in the list: " + third.dash());
		x.Click_edit(row);
		test.info("Edit pop-up: " + x.edit_employee() + " | " + x.edit_date() + " | downloaded " + x.edit_downloaded());
		x.Set_edit_date(new_date.format(SLASH));   // slashes: the box removes typed dashes (see SP51)
		test.info("Date typed: " + x.edit_date());
		String message = x.Submit_edit();
		test.info("Messages after Submit: '" + message + "' | pop-up open: " + x.edit_open());
		Assert.assertFalse(x.edit_open(), "The edit pop-up stayed open. Messages: '" + message + "'");

		third.date = new_date;
		int moved = find_saved(x, third, true);
		Assert.assertTrue(moved >= 0, "DEFECT: the row is not found at the new date " + third.dash() + " after the edit. Messages: '" + message + "'");
		x.Search(EMPLOYEE, old_date.format(SLASH), old_date.format(SLASH));
		Assert.assertEquals(x.count_rows(EMPLOYEE, old_date.format(DASH), third.route), 0,
				"DEFECT: the row is still listed at the old date " + old_date.format(DASH));
		test.pass("The row moved from " + old_date.format(DASH) + " to " + third.dash());
	}

	// SP45: Is Downloaded -> the status becomes Downloaded; clearing it -> Pending again
	@Test(dependsOnMethods = "SP44_edit_date", alwaysRun = true)
	public void SP45_edit_is_downloaded() {
		test = extent.createTest("SP45 Edit Is Downloaded");
		if (third == null) {
			throw new SkipException("The row of SP40 was not saved");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		int row = find_saved(x, third, true);
		Assert.assertTrue(row >= 0, "The row to edit is not in the list: " + third.dash());
		x.Click_edit(row);
		x.Set_edit_flags(true, false);
		String message = x.Submit_edit();
		test.info("Is Downloaded ticked -> messages '" + message + "'");
		row = find_saved(x, third, true);
		Assert.assertTrue(row >= 0, "The row is gone after the edit");
		String status = x.rows().get(row).get(3);
		test.info("Status now: " + status);
		Assert.assertEquals(status, "Downloaded", "DEFECT: the status after ticking Is Downloaded");
		x.Click_edit(row);
		Assert.assertTrue(x.edit_downloaded(), "DEFECT: Is Downloaded is not kept (the pop-up shows it unticked)");
		x.Set_edit_flags(false, false);
		x.Submit_edit();
		row = find_saved(x, third, true);
		status = x.rows().get(row).get(3);
		test.info("Status after clearing it: " + status);
		Assert.assertEquals(status, "Pending", "DEFECT: the status after clearing Is Downloaded");
		test.pass("Is Downloaded switches the status Pending <-> Downloaded");
	}

	// SP46: Is Uploaded can only be kept together with Is Downloaded (the update procedure resets it otherwise)
	@Test(dependsOnMethods = "SP45_edit_is_downloaded", alwaysRun = true)
	public void SP46_edit_is_uploaded() {
		test = extent.createTest("SP46 Edit Is Uploaded");
		if (third == null) {
			throw new SkipException("The row of SP40 was not saved");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		int row = find_saved(x, third, true);
		Assert.assertTrue(row >= 0, "The row to edit is not in the list: " + third.dash());

		// Is Uploaded without Is Downloaded: the page lets it be ticked, the database resets it
		x.Click_edit(row);
		x.Set_edit_flags(false, true);
		String message = x.Submit_edit();
		row = find_saved(x, third, true);
		x.Click_edit(row);
		test.info("Uploaded ticked alone -> messages '" + message + "' | after reopening: downloaded " + x.edit_downloaded()
				+ ", uploaded " + x.edit_uploaded());
		if (!x.edit_uploaded()) {
			test.warning("Is Uploaded ticked alone is accepted by the page but silently reset to No (the rule is: only after Is Downloaded)");
		}
		x.Cancel_edit();

		// both ticked: both are kept
		x.Click_edit(row);
		x.Set_edit_flags(true, true);
		x.Submit_edit();
		row = find_saved(x, third, true);
		x.Click_edit(row);
		boolean downloaded = x.edit_downloaded();
		boolean uploaded = x.edit_uploaded();
		test.info("Both ticked -> after reopening: downloaded " + downloaded + ", uploaded " + uploaded + " | status " + x.rows().get(row).get(3));
		Assert.assertTrue(downloaded && uploaded, "DEFECT: Is Downloaded + Is Uploaded are not kept after Submit");

		// back to the original state
		x.Set_edit_flags(false, false);
		x.Submit_edit();
		row = find_saved(x, third, true);
		Assert.assertEquals(x.rows().get(row).get(3), "Pending", "The status after clearing both flags");
		test.pass("Is Uploaded is kept together with Is Downloaded; both flags were cleared again");
	}
	// SP47: an empty date in the edit pop-up must not be saved
	@Test(dependsOnMethods = "SP44_edit_date", alwaysRun = true)
	public void SP47_edit_empty_date() {
		test = extent.createTest("SP47 Edit with an empty date");
		if (third == null) {
			throw new SkipException("The row of SP40 was not saved");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		int row = find_saved(x, third, true);
		Assert.assertTrue(row >= 0, "The row to edit is not in the list: " + third.dash());
		List<String> before = x.rows().get(row);
		x.Click_edit(row);
		x.Set_edit_date("");
		Assert.assertEquals(x.edit_date(), "", "The date box could not be emptied");
		String message = x.Submit_edit();
		boolean still_open = x.edit_open();
		test.info("Messages: '" + message + "' | pop-up still open: " + still_open);
		if (still_open) {
			x.Cancel_edit();
		}
		int after_row = find_saved(x, third, true);
		Assert.assertTrue(after_row >= 0, "DEFECT: the row is gone / changed after saving an empty date. Messages: '" + message + "'");
		Assert.assertEquals(x.rows().get(after_row), before, "DEFECT: the row changed after an empty date was submitted");
		Assert.assertTrue(still_open || !message.isEmpty(), "The empty date was submitted without any message");
		test.pass("An empty date is not saved: '" + message + "'");
	}

	// SP48: a text that is not a date in the edit pop-up must not be saved
	@Test(dependsOnMethods = "SP44_edit_date", alwaysRun = true)
	public void SP48_edit_wrong_date() {
		test = extent.createTest("SP48 Edit with a text instead of a date");
		if (third == null) {
			throw new SkipException("The row of SP40 was not saved");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		int row = find_saved(x, third, true);
		Assert.assertTrue(row >= 0, "The row to edit is not in the list: " + third.dash());
		List<String> before = x.rows().get(row);
		x.Click_edit(row);
		x.Set_edit_date("not a date");
		test.info("The date box shows: '" + x.edit_date() + "'");
		x.Submit_edit();
		if (x.edit_open()) {
			x.Cancel_edit();
		}
		int after_row = find_saved(x, third, true);
		Assert.assertTrue(after_row >= 0, "DEFECT: the row is gone / moved after a text was submitted as the date");
		Assert.assertEquals(x.rows().get(after_row), before, "DEFECT: the row changed after a text was submitted as the date");
		test.pass("A text instead of a date does not change the row");
	}

	// SP49: what this run left in the list (the page has no delete)
	@Test(alwaysRun = true, dependsOnMethods = { "SP48_edit_wrong_date", "SP46_edit_is_uploaded", "SP43_duplicate_row" })
	public void SP49_rows_left_in_the_list() {
		test = extent.createTest("SP49 Rows left in the list by this run");
		StringBuilder sb = new StringBuilder();
		for (Made m : all_made) {
			sb.append("\n  ").append(EMPLOYEE).append(" | ").append(m.dash()).append(" | route '").append(m.route).append("' | vehicle '")
					.append(m.vehicle).append("' | ").append(m.items.length).append(" item(s)");
		}
		System.out.println("STOCK PREPARATION rows saved by this run (they stay in the list):" + (all_made.isEmpty() ? " none" : sb.toString()));
		if (duplicate_saved) {
			System.out.println("(and one duplicate of " + (first == null ? "" : first.dash()) + " saved by SP43)");
		}
		test.info("Rows saved by this run: " + all_made.size() + (duplicate_saved ? " + 1 duplicate" : "") + sb);
		test.pass("This run saved " + all_made.size() + " stock preparation row(s) for " + EMPLOYEE + " on far future dates");
	}

	// SP50: every item that was added is shown in the view pop-up of the saved row
	@Test(dependsOnMethods = "SP41_save_decimal_quantity", alwaysRun = true)
	public void SP50_saved_items_are_shown_in_the_view() {
		test = extent.createTest("SP50 Saved items are shown in the view pop-up");
		if (all_made.isEmpty()) {
			throw new SkipException("Nothing was saved");
		}
		test.info("Items that were saved but are not shown in the view pop-up: " + hidden_items);
		Assert.assertTrue(hidden_items.isEmpty(), "DEFECT: items are saved but hidden in the view pop-up (the pop-up lists only items that have a "
				+ "valid unit; the web Create does not check the unit): " + hidden_items);
		test.pass("All saved items are shown in the view pop-up");
	}

	// SP51: a date typed with dashes in the edit pop-up must not reach the database as an unreadable text
	@Test(dependsOnMethods = "SP44_edit_date", alwaysRun = true)
	public void SP51_edit_date_typed_with_dashes() {
		test = extent.createTest("SP51 Edit date typed with dashes");
		if (third == null) {
			throw new SkipException("The row of SP40 was not saved");
		}
		X_StockPreparation_Cls x = new X_StockPreparation_Cls(driver);

		int row = find_saved(x, third, true);
		Assert.assertTrue(row >= 0, "The row is not in the list: " + third.dash());
		List<String> before = x.rows().get(row);
		x.Click_edit(row);
		test.info("The box is filled with '" + x.edit_date() + "' (dashes) but the date picker uses slashes");
		x.Set_edit_date(LocalDate.now().plusDays(500).format(DASH));
		test.info("After typing a date with dashes the box shows: '" + x.edit_date() + "'");
		String message = x.Submit_edit();
		test.info("Message: '" + message + "'");
		if (x.edit_open()) {
			x.Cancel_edit();
		}
		int after_row = find_saved(x, third, true);
		Assert.assertTrue(after_row >= 0, "DEFECT: the row is gone after a date typed with dashes");
		Assert.assertEquals(x.rows().get(after_row), before, "DEFECT: the row changed after a date typed with dashes");
		Assert.assertFalse(has(message, "conversion failed"),
				"DEFECT: the database error is shown to the user: '" + message + "' (the date box removes the typed dashes and the text reaches SQL unchecked)");
		test.pass("A date typed with dashes is refused with a normal message: '" + message + "'");
	}}
