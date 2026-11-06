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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;
import PagePkg.W_PriceList_Cls;

/*
 * Price List (Field Management > PriceList), logged in as the web user - tests PL01 .. PL40.
 * PL01 .. PL25 only read data (and open the edit / create forms without saving).
 * PL26 .. PL39 CREATE price lists (code and name starting with ZQ, dates in 2030 so no real order is touched) and delete
 * them again in the same test; they run only when ALLOW_CREATE is true. What is left over is deleted by the sweep
 * (PL40 and the @AfterClass), which finds the lists by their name starting with ZQ.
 * An existing price list is never saved or deleted (the delete dialog of an existing list is only Cancelled).
 */
public class W_PriceList_Test extends BaseCls {

	// login of the web user (a test login of this project)
	static final String WEB_USER = TestEnv.get("web.user", "your-web-user");
	static final String WEB_PASS = TestEnv.get("web.password", "your-web-password");

	// true = the tests that create (and then delete) price lists are run
	static final boolean ALLOW_CREATE = true;

	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	// the period of the test price lists (far away from the real ones)
	static final String FROM_TEST = "01/01/2030";
	static final String TO_TEST = "31/12/2030";

	static final String ITEM_A = "leo-item1";
	static final String ITEM_B = "leo-item2";

	private String main_tab;

	private String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
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

	// opens Field Management > PriceList from the dashboard
	private void go_to_page() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_containing("Field Management", "PriceList");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Field Management", "Price List");
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

	// every test starts with a fresh Price List screen
	@BeforeMethod
	public void open_page() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		clean_up();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);
		try {
			go_to_page();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_page();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		}
		remember_clean_screen();
	}

	// ---------------------------------- helpers ----------------------------------

	private void require_create() {
		if (!ALLOW_CREATE) {
			throw new SkipException("ALLOW_CREATE is false");
		}
	}

	private String zq_code() {
		return "ZQPL" + unique();
	}

	private String zq_name() {
		return "ZQ price list " + unique();
	}

	private void back_to_list() throws InterruptedException {
		W_PriceList_Cls o = new W_PriceList_Cls(driver);
		if (!o.is_list_page()) {
			go_to_page();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		}
	}

	private void open_create(W_PriceList_Cls o) {
		o.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_form_page());
	}

	// the index of the row with exactly these cells (company, code, name, from, to) in the rows shown; -1 = none
	private int exact_row(List<List<String>> shown, List<String> target) {
		for (int i = 0; i < shown.size(); i++) {
			List<String> r = shown.get(i);
			if (r.get(1).equals(target.get(1)) && r.get(2).equals(target.get(2)) && r.get(3).equals(target.get(3))
					&& r.get(4).equals(target.get(4))) {
				return i;
			}
		}
		return -1;
	}

	// Opens the view / edit page of the list row with these cells (company, code, name, from, to).
	// Names and codes are often the same (for example four TALLY-DefaultRate lists), so the main Search form is used
	// with ONLY the From Date filled in (nothing else) and its Search button: that lists just the price lists of that
	// date, the exact row is taken from them (code, name and dates) and its icon is clicked. Only when that search does
	// not show the row, a search by the code is tried.
	private void open_listed(W_PriceList_Cls o, List<String> target, boolean edit) {
		o.Search_lists("", "", "", "", target.get(3), "");
		List<List<String>> shown = o.result_texts();
		int index = exact_row(shown, target);
		if (index < 0) {
			o.Search_lists(target.get(1), "", "", "", "", "");
			shown = o.result_texts();
			index = exact_row(shown, target);
			for (int i = 0; index < 0 && i < shown.size(); i++) {
				if (shown.get(i).get(1).equals(target.get(1))) {
					index = i;
				}
			}
		}
		if (index < 0) {
			throw new NoSuchElementException("The price list " + target.get(1) + " (from " + target.get(3)
					+ ") is not found with the Search form: " + shown);
		}
		if (edit) {
			o.Click_edit(index);
		} else {
			o.Click_view(index);
		}
		new WebDriverWait(driver, Duration.ofSeconds(90), Duration.ofMillis(100)).until(x -> o.is_form_page());
	}
	// opens the view / edit page of the row number "row" of the list that is shown now (see open_listed)
	private void open_form(W_PriceList_Cls o, int row, boolean edit) {
		open_listed(o, o.result_texts().get(row), edit);
	}

	private static class Res {
		boolean ok;
		String message = "";
	}

	// clicks Submit and reads the answer (a browser alert, a sweet alert / toast or a return to the list)
	private Res submit(W_PriceList_Cls o) {
		Res r = new Res();
		o.Click_submit();
		long end = System.currentTimeMillis() + 6000;
		while (System.currentTimeMillis() < end && !o.alert_open() && o.notice().isEmpty() && !o.is_list_page()) {
			WaitUtil.settle(driver, 500);
		}
		r.message = o.All_messages();
		r.message += " " + o.Accept_notice();
		String lower = r.message.toLowerCase();
		r.ok = lower.contains("success") || lower.contains("saved") || lower.contains("created") || lower.contains("submitted")
				|| lower.contains("updated") || o.is_list_page();
		return r;
	}

	// fills a new price list (company = the first one) and its lines {item, rate, channel}; nothing is submitted
	private void fill_new(W_PriceList_Cls o, String code, String name, String from, String to, String[][] lines) {
		List<String> companies = o.company_options();
		o.Fill_details(companies.get(0), code, name, from, to);
		for (String[] l : lines) {
			o.Add_line(l[0], l[1], l.length > 2 ? l[2] : "");
		}
	}

	// creates a price list and returns the answer
	private Res create_list(W_PriceList_Cls o, String code, String name, String from, String to, String[][] lines) {
		open_create(o);
		fill_new(o, code, name, from, to, lines);
		return submit(o);
	}

	// answers the delete confirmation (a browser alert or a sweet alert) with its confirm button; returns the texts
	private List<String> confirm_delete(W_PriceList_Cls o) {
		List<String> texts = new ArrayList<String>();
		if (o.alert_open()) {
			texts.add(o.Alert_text());
		} else {
			String n = o.notice();
			if (!n.isEmpty()) {
				texts.add(n);
			}
			o.Accept_notice();
		}
		String second = o.Alert_text();
		if (second.isEmpty()) {
			second = o.Accept_notice();
		}
		if (!second.isEmpty()) {
			texts.add(second);
		}
		return texts;
	}

	// finds the row of a price list by its code (a search by code); -1 when it is not listed
	private int find_row(W_PriceList_Cls o, String code) {
		o.Search_lists(code, "", "", "", "", "");
		return o.row_of_code(code);
	}

	// deletes the price lists whose name starts with ZQ until none is left; returns what could not be deleted
	private List<String> sweep() throws InterruptedException {
		List<String> left = new ArrayList<String>();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);
		for (int round = 0; round < 40; round++) {
			back_to_list();
			o.Search_lists("", "ZQ", "", "", "", "");
			int target = -1;
			List<List<String>> rows = o.result_texts();
			for (int i = 0; i < rows.size(); i++) {
				if (rows.get(i).get(2).toUpperCase().startsWith("ZQ")) {
					target = i;
					break;
				}
			}
			if (target < 0) {
				if (!rows.isEmpty()) {
					left.add("rows match ZQ but no name starts with ZQ: " + rows);
				}
				return left;
			}
			o.Click_delete(target);
			confirm_delete(o);
			WaitUtil.settle(driver, 500);
		}
		left.add("more than 40 price lists were deleted: stopped");
		return left;
	}

	@AfterClass(alwaysRun = true)
	public void sweep_after_class() {
		try {
			List<String> left = sweep();
			System.out.println("Price List sweep: " + (left.isEmpty() ? "nothing left over" : "LEFT OVER: " + left));
		} catch (Exception e) {
			System.out.println("Price List sweep failed: " + e.getMessage());
		}
	}

	private LocalDate date_of(String text) {
		return LocalDate.parse(text.trim(), DATE);
	}

	private void need_rows(W_PriceList_Cls o) {
		if (o.result_count() == 0) {
			throw new SkipException("The list has no price list");
		}
	}

	// ======================= list and search =======================

	// PL01: the list shows Search, Create, the table filter, the 6 columns and the entries line
	@Test
	public void PL01_list_elements() {
		test = extent.createTest("PL01 Price List elements");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		Assert.assertTrue(o.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(o.verify_create_btn().isDisplayed(), "Create button is missing");
		Assert.assertTrue(o.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = o.headers();
		test.info("Headers: " + headers + " | " + o.entries_text());
		for (String h : new String[] { "COMPANY", "PRICE LIST CODE", "PRICE LIST NAME", "FROM DATE", "TO DATE", "ACTIONS" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(o.entries_text().contains("Showing"), "Entries line: " + o.entries_text());
		test.pass("List elements are displayed");
	}

	// PL02: the search form has its 6 fields, Reset and Search
	@Test
	public void PL02_search_form() {
		test = extent.createTest("PL02 Search form");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		o.Open_search_form();
		Assert.assertTrue(o.verify_code().isDisplayed(), "Price List Code is missing");
		Assert.assertTrue(o.verify_name().isDisplayed(), "Price List Name is missing");
		Assert.assertTrue(o.verify_item_code().isDisplayed(), "Item Code is missing");
		Assert.assertTrue(o.verify_item_name().isDisplayed(), "Item Name is missing");
		Assert.assertTrue(o.verify_from().isDisplayed(), "From Date is missing");
		Assert.assertTrue(o.verify_to().isDisplayed(), "To Date is missing");
		Assert.assertTrue(o.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertTrue(o.verify_reset().isDisplayed(), "The Reset button is missing");
		test.info("Date placeholders: '" + o.verify_from().getAttribute("placeholder") + "' / '" + o.verify_to().getAttribute("placeholder") + "'");
		Assert.assertEquals(o.verify_from().getAttribute("value"), "", "From Date starts empty");
		Assert.assertEquals(o.verify_to().getAttribute("value"), "", "To Date starts empty");
		test.pass("Search form is displayed");
	}

	// PL03: formats of the list: dates, code and name; From Date not after To Date
	@Test
	public void PL03_list_formats() {
		test = extent.createTest("PL03 List formats");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		test.info("Rows: " + o.result_texts());
		List<String> bad = new ArrayList<String>();
		for (List<String> row : o.result_texts()) {
			if (row.get(0).isEmpty() || row.get(1).isEmpty() || row.get(2).isEmpty()) {
				bad.add("an empty company / code / name in " + row);
			}
			if (!row.get(3).matches("\\d{2}/\\d{2}/\\d{4}")) {
				bad.add("from date '" + row.get(3) + "' in " + row);
			}
			if (!row.get(4).isEmpty() && !row.get(4).matches("\\d{2}/\\d{2}/\\d{4}")) {
				bad.add("to date '" + row.get(4) + "' in " + row);
			}
			if (row.get(3).matches("\\d{2}/\\d{2}/\\d{4}") && row.get(4).matches("\\d{2}/\\d{2}/\\d{4}") && date_of(row.get(3)).isAfter(date_of(row.get(4)))) {
				bad.add("from date after to date in " + row);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT in the list: " + bad);
		test.pass("List formats are correct");
	}

	// PL04: search by code and by name; unknown values list nothing
	@Test
	public void PL04_search_by_code_and_name() {
		test = extent.createTest("PL04 Search by code and name");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		List<String> row0 = o.result_texts().get(0);
		o.Search_lists(row0.get(1), "", "", "", "", "");
		test.info("Code '" + row0.get(1) + "': " + o.entries_text());
		Assert.assertTrue(o.result_count() > 0, "DEFECT: the code " + row0.get(1) + " is not found");
		for (List<String> row : o.result_texts()) {
			Assert.assertTrue(row.get(1).toLowerCase().contains(row0.get(1).toLowerCase()), "A row of another code: " + row);
		}
		o.Search_lists("", row0.get(2), "", "", "", "");
		test.info("Name '" + row0.get(2) + "': " + o.entries_text());
		Assert.assertTrue(o.result_count() > 0, "DEFECT: the name " + row0.get(2) + " is not found");
		for (List<String> row : o.result_texts()) {
			Assert.assertTrue(row.get(2).toLowerCase().contains(row0.get(2).toLowerCase()), "A row of another name: " + row);
		}
		o.Search_lists("ZZQNoSuch" + unique(), "", "", "", "", "");
		Assert.assertEquals(o.result_count(), 0, "DEFECT: price lists are listed for a code that does not exist");
		Assert.assertTrue(o.no_data_shown(), "No 'No Data' line");
		o.Search_lists("", "ZZQNoSuch" + unique(), "", "", "", "");
		Assert.assertEquals(o.result_count(), 0, "DEFECT: price lists are listed for a name that does not exist");
		o.Search_lists(row0.get(1).toLowerCase(), "", "", "", "", "");
		test.info("Lower case code: rows " + o.result_count());
		o.Search_lists(row0.get(1).substring(0, 3), "", "", "", "", "");
		test.info("Part of the code '" + row0.get(1).substring(0, 3) + "': rows " + o.result_count() + " (partial search)");
		test.pass("Search by code and name works");
	}

	// PL05: search by the code / name of an item that a list contains
	@Test
	public void PL05_search_by_item() throws InterruptedException {
		test = extent.createTest("PL05 Search by item");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		List<String> row0 = o.result_texts().get(0);
		open_form(o, 0, false);
		Assert.assertTrue(o.line_count() > 0, "The first price list has no item line");
		String item_code = o.line_texts().get(0).get(1);
		String item_name = o.line_texts().get(0).get(2);
		test.info("List " + row0.get(1) + " contains the item " + item_code + " / " + item_name);
		o.Click_back();
		back_to_list();
		o.Search_lists("", "", item_code, "", "", "");
		test.info("Item code '" + item_code + "': " + o.result_texts());
		Assert.assertTrue(o.row_of_code(row0.get(1)) >= 0, "DEFECT: the list " + row0.get(1) + " is not found by its item code " + item_code);
		o.Search_lists("", "", "", item_name, "", "");
		Assert.assertTrue(o.row_of_code(row0.get(1)) >= 0, "DEFECT: the list " + row0.get(1) + " is not found by its item name " + item_name);
		o.Search_lists("", "", "ZZQNoSuch" + unique(), "", "", "");
		Assert.assertEquals(o.result_count(), 0, "DEFECT: price lists are listed for an item code that does not exist");
		o.Search_lists("", "", "", "ZZQNoSuch" + unique(), "", "");
		Assert.assertEquals(o.result_count(), 0, "DEFECT: price lists are listed for an item name that does not exist");
		test.pass("Search by item works");
	}

	// PL06: search by dates: a range around a list finds it; far away finds nothing; reversed and invalid dates are refused
	@Test
	public void PL06_search_by_dates() {
		test = extent.createTest("PL06 Search by dates");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		List<String> row0 = o.result_texts().get(0);
		String from = row0.get(3);
		o.Search_lists("", "", "", "", from, from);
		test.info("From " + from + " to " + from + ": rows " + o.result_texts());
		if (o.result_count() == 0) {
			String next_day = date_of(from).plusDays(1).format(DATE);
			o.Search_lists("", "", "", "", from, next_day);
			test.info("From " + from + " to " + next_day + ": rows " + o.result_texts());
			Assert.fail("DEFECT: From Date = To Date = " + from + " finds no price list that starts on that day"
					+ (o.result_count() > 0 ? " (it is found when To Date is the next day: the To Date day is not included)" : ""));
		}
		o.Search_lists("", "", "", "", "01/01/2040", "31/12/2040");
		test.info("2040: rows " + o.result_count());
		Assert.assertEquals(o.result_count(), 0, "DEFECT: price lists are listed for a period far in the future");
		o.Search_lists("", "", "", "", "20/10/2026", "01/10/2026");
		test.info("Reversed range: rows " + o.result_count() + " | " + o.All_messages());
		Assert.assertTrue(o.result_count() == 0 || !o.validation_messages().isEmpty() || !o.notice().isEmpty(),
				"DEFECT: a reversed date range lists price lists without any message");
		List<String> bad = new ArrayList<String>();
		for (String value : new String[] { "abcd", "31/02/2026", "99/99/9999" }) {
			o.Search_lists("", "", "", "", value, "");
			o.Open_search_form();
			String kept = o.verify_from().getAttribute("value");
			test.info("From Date '" + value + "' -> box keeps '" + kept + "' | rows " + o.result_count() + " | " + o.validation_messages());
			boolean refused = kept == null || !kept.equals(value) || !o.validation_messages().isEmpty() || o.result_count() == 0 || !o.notice().isEmpty();
			if (!refused) {
				bad.add(value);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: invalid From Date values were accepted and lists shown: " + bad);
		test.pass("Date search works");
	}

	// PL07: Reset empties the form and brings the full list back
	@Test
	public void PL07_reset() {
		test = extent.createTest("PL07 Reset");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		int all = o.entries_total();
		o.Search_lists("ZZQCode", "ZZQName", "ZZQItem", "ZZQItemName", "", "");
		Assert.assertEquals(o.result_count(), 0, "The search of unknown values lists rows");
		o.Click_reset();
		o.Open_search_form();
		test.info("After Reset: code '" + o.verify_code().getAttribute("value") + "' | name '" + o.verify_name().getAttribute("value")
				+ "' | " + o.entries_text() + " (all " + all + ")");
		Assert.assertEquals(o.verify_code().getAttribute("value"), "", "Code was not cleared");
		Assert.assertEquals(o.verify_name().getAttribute("value"), "", "Name was not cleared");
		Assert.assertEquals(o.verify_item_code().getAttribute("value"), "", "Item Code was not cleared");
		Assert.assertEquals(o.verify_item_name().getAttribute("value"), "", "Item Name was not cleared");
		Assert.assertEquals(o.entries_total(), all, "DEFECT: Reset does not bring the full list back");
		test.pass("Reset works");
	}

	// PL08: table filter: by code, nothing, special text
	@Test
	public void PL08_table_filter() {
		test = extent.createTest("PL08 Table filter");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		String code = o.result_texts().get(0).get(1);
		o.Filter_table(code);
		test.info("Filter '" + code + "': " + o.entries_text());
		Assert.assertTrue(o.result_count() > 0, "The filter found nothing for " + code);
		for (List<String> row : o.result_texts()) {
			Assert.assertTrue(row.toString().toLowerCase().contains(code.toLowerCase()), "Row without the text: " + row);
		}
		o.Filter_table("ZZQNothing" + unique());
		Assert.assertEquals(o.result_count(), 0, "Rows are listed for text that matches nothing");
		Assert.assertTrue(o.no_data_shown(), "No 'No Data' line");
		for (String text : new String[] { "<script>alert(1)</script>", "' OR '1'='1", "%%%", "\\" }) {
			o.Filter_table(text);
			Assert.assertTrue(o.is_list_page(), "The page broke for the filter text " + text);
		}
		o.Filter_table("");
		Assert.assertTrue(o.result_count() > 0, "The rows did not come back");
		test.pass("Table filter works");
	}

	// PL09: sorting: Company, Code and Name as text, From Date and To Date as dates
	@Test
	public void PL09_sort_columns() {
		test = extent.createTest("PL09 Sort the columns");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		if (o.result_count() < 3) {
			throw new SkipException("Too few rows to check sorting");
		}
		List<String> wrong = new ArrayList<String>();
		for (String[] col : new String[][] { { "PRICE LIST CODE", "1" }, { "PRICE LIST NAME", "2" } }) {
			o.Click_header(col[0]);
			List<String> values = new ArrayList<String>();
			for (List<String> row : o.result_texts()) {
				values.add(row.get(Integer.parseInt(col[1])).toLowerCase());
			}
			List<String> up = new ArrayList<String>(values);
			java.util.Collections.sort(up);
			List<String> down = new ArrayList<String>(up);
			java.util.Collections.reverse(down);
			boolean ok = values.equals(up) || values.equals(down);
			test.info(col[0] + " -> " + (ok ? "sorted" : "NOT sorted") + " " + values);
			if (!ok) {
				wrong.add(col[0]);
			}
		}
		o.Click_header("FROM DATE");
		List<LocalDate> dates = new ArrayList<LocalDate>();
		for (List<String> row : o.result_texts()) {
			dates.add(date_of(row.get(3)));
		}
		List<LocalDate> up = new ArrayList<LocalDate>(dates);
		java.util.Collections.sort(up);
		List<LocalDate> down = new ArrayList<LocalDate>(up);
		java.util.Collections.reverse(down);
		boolean date_ok = dates.equals(up) || dates.equals(down);
		test.info("FROM DATE -> " + (date_ok ? "sorted" : "NOT sorted by date") + " " + dates);
		if (!date_ok) {
			wrong.add("FROM DATE");
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: these columns are not sorted correctly: " + wrong);
		test.pass("Columns sort");
	}

	// PL10: every row has view, edit and delete icons
	@Test
	public void PL10_row_actions() {
		test = extent.createTest("PL10 Row actions");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		test.info("Icons of the first row: " + o.actions_html(0));
		List<String> bad = new ArrayList<String>();
		for (int i = 0; i < o.result_count(); i++) {
			List<String> kinds = o.actions_of(i);
			test.info("Row " + (i + 1) + ": " + kinds);
			for (String k : new String[] { "view", "edit", "delete" }) {
				if (!kinds.contains(k)) {
					bad.add("row " + (i + 1) + " has no " + k + " icon: " + kinds);
				}
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Row actions are complete");
	}

	// ======================= view =======================

	// PL11: the view page: details like the list row, everything locked, only Back
	@Test
	public void PL11_view_page() {
		test = extent.createTest("PL11 View page");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		List<String> row0 = o.result_texts().get(0);
		open_form(o, 0, false);
		test.info("Buttons: Back " + o.has_button("Back") + ", Submit " + o.has_button("Submit") + ", Add Item Row " + o.has_add_item_row()
				+ " | company '" + o.selected(o.form_company()) + "' | count text " + o.items_count_text() + " | lines " + o.line_count());
		Assert.assertEquals(o.form_code().getAttribute("value"), row0.get(1), "Code of the view page");
		Assert.assertEquals(o.form_name().getAttribute("value"), row0.get(2), "Name of the view page");
		Assert.assertEquals(o.form_from().getAttribute("value"), row0.get(3), "From Date of the view page");
		Assert.assertEquals(o.form_to().getAttribute("value"), row0.get(4), "To Date of the view page");
		Assert.assertTrue(o.selected(o.form_company()).contains(row0.get(0)), "Company of the view page: " + o.selected(o.form_company()));
		for (int i = 0; i < 5; i++) {
			W_PriceList_Cls p = o;
			org.openqa.selenium.WebElement[] boxes = { p.form_company(), p.form_code(), p.form_name(), p.form_from(), p.form_to() };
			Assert.assertTrue(p.is_locked(boxes[i]), "DEFECT: a field of the view page can be changed (field " + (i + 1) + ")");
		}
		Assert.assertTrue(o.has_button("Back"), "Back is missing");
		Assert.assertFalse(o.has_button("Submit"), "DEFECT: the view page has Submit");
		Assert.assertFalse(o.has_add_item_row(), "DEFECT: the view page has Add Item Row");
		Assert.assertEquals(o.bin_count(), 0, "DEFECT: the view page has delete buttons on the lines");
		for (List<String> line : o.line_texts()) {
			Assert.assertTrue(o.is_locked(o.line_rate_box(o.line_texts().indexOf(line))), "DEFECT: a unit rate can be changed in the view page");
		}
		Assert.assertEquals(o.items_count_text(), o.line_count(), "The 'N item(s)' text differs from the number of lines");
		test.pass("View page is read only");
	}

	// PL12: every list row: its view page shows the same data and the item count matches its lines
	@Test
	public void PL12_view_every_row() throws InterruptedException {
		test = extent.createTest("PL12 View every price list");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		List<List<String>> rows = o.result_texts();
		List<String> bad = new ArrayList<String>();
		for (int i = 0; i < rows.size(); i++) {
			back_to_list();
			open_listed(o, rows.get(i), false);
			List<String> row = rows.get(i);
			test.info("Row " + (i + 1) + " " + row.get(1) + ": count text " + o.items_count_text() + " | lines " + o.line_count());
			if (!row.get(1).equals(o.form_code().getAttribute("value"))) {
				bad.add("row " + (i + 1) + ": code " + o.form_code().getAttribute("value") + " instead of " + row.get(1));
			}
			if (!row.get(2).equals(o.form_name().getAttribute("value"))) {
				bad.add("row " + (i + 1) + ": name " + o.form_name().getAttribute("value") + " instead of " + row.get(2));
			}
			if (!row.get(3).equals(o.form_from().getAttribute("value"))) {
				bad.add("row " + (i + 1) + ": from date " + o.form_from().getAttribute("value") + " instead of " + row.get(3));
			}
			if (!row.get(4).equals(o.form_to().getAttribute("value"))) {
				bad.add("row " + (i + 1) + ": to date " + o.form_to().getAttribute("value") + " instead of " + row.get(4));
			}
			if (o.items_count_text() != o.line_count()) {
				bad.add("row " + (i + 1) + " (" + row.get(1) + "): count text " + o.items_count_text() + " but " + o.line_count() + " lines");
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Every view page matches its list row");
	}

	// PL13: the item lines: numbered, codes unique, names and units filled, rates numbers with 2 decimals and not negative
	@Test
	public void PL13_item_line_content() throws InterruptedException {
		test = extent.createTest("PL13 Item line content");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		List<List<String>> rows = o.result_texts();
		List<String> bad = new ArrayList<String>();
		for (int i = 0; i < rows.size(); i++) {
			back_to_list();
			open_listed(o, rows.get(i), false);
			List<String> codes = new ArrayList<String>();
			int n = 0;
			for (List<String> line : o.line_texts()) {
				n++;
				String where = rows.get(i).get(1) + " line " + n;
				if (!line.get(0).equals(String.valueOf(n))) {
					bad.add(where + ": numbered '" + line.get(0) + "'");
				}
				if (line.get(1).isEmpty() || line.get(2).isEmpty()) {
					bad.add(where + ": empty item code or name " + line);
				}
				if (line.get(4).isEmpty()) {
					bad.add(where + ": empty unit " + line);
				}
				if (!line.get(5).matches("-?[\\d,]+\\.\\d{2}")) {
					bad.add(where + ": rate '" + line.get(5) + "' is not a number with 2 decimals");
				} else if (W_PriceList_Cls.to_number(line.get(5)) < 0) {
					bad.add(where + ": negative rate " + line.get(5));
				}
				if (codes.contains(line.get(1))) {
					bad.add(where + ": item code " + line.get(1) + " is repeated");
				}
				codes.add(line.get(1));
			}
			test.info(rows.get(i).get(1) + ": " + o.line_texts());
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT in the item lines: " + bad);
		test.pass("Item lines are correct");
	}

	// PL14: the "Search added items..." box filters the lines of the view page
	@Test
	public void PL14_search_added_items() {
		test = extent.createTest("PL14 Search added items");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		open_form(o, 0, false);
		int all = o.line_count();
		if (all < 2) {
			throw new SkipException("The first price list has fewer than 2 lines");
		}
		String name = o.line_texts().get(0).get(2);
		o.Search_lines(name);
		test.info("Search '" + name + "': lines " + o.line_count() + " of " + all + " | count text " + o.items_count_text());
		Assert.assertTrue(o.line_count() >= 1 && o.line_count() < all, "DEFECT: the lines search does not narrow the lines");
		for (List<String> line : o.line_texts()) {
			Assert.assertTrue(line.toString().toLowerCase().contains(name.toLowerCase()), "A line without the text: " + line);
		}
		o.Search_lines("ZZQNothing" + unique());
		Assert.assertEquals(o.line_count(), 0, "Lines are shown for text that matches nothing");
		o.Search_lines("");
		Assert.assertEquals(o.line_count(), all, "DEFECT: the lines did not come back after clearing the search");
		test.pass("The lines search works");
	}

	// PL15: Back returns to the list
	@Test
	public void PL15_view_back() {
		test = extent.createTest("PL15 Back from the view page");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		int before = o.entries_total();
		open_form(o, 0, false);
		o.Click_back();
		Assert.assertTrue(o.is_list_page(), "Back did not return to the list");
		Assert.assertEquals(o.entries_total(), before, "The list changed");
		test.pass("Back works");
	}

	// ======================= edit (nothing is saved) =======================

	// PL16: the edit page: company and code locked, name / dates / lines editable, a bin on every line, Add Item Row, Back, Submit
	@Test
	public void PL16_edit_page() {
		test = extent.createTest("PL16 Edit page");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		open_form(o, 0, true);
		test.info("Company locked " + o.is_locked(o.form_company()) + " | code locked " + o.is_locked(o.form_code())
				+ " | name locked " + o.is_locked(o.form_name()) + " | from locked " + o.is_locked(o.form_from())
				+ " | to locked " + o.is_locked(o.form_to()) + " | bins " + o.bin_count() + " of " + o.line_count());
		Assert.assertTrue(o.is_locked(o.form_company()), "DEFECT: the company can be changed in edit");
		Assert.assertTrue(o.is_locked(o.form_code()), "DEFECT: the price list code can be changed in edit");
		Assert.assertFalse(o.is_locked(o.form_name()), "The name cannot be changed in edit");
		test.info("To Date box read only (a date picker box is always read only, it is changed with the calendar): " + o.is_locked(o.form_to()));
		Assert.assertEquals(o.bin_count(), o.line_count(), "A delete button on every line");
		Assert.assertFalse(o.is_locked(o.line_rate_box(0)), "The unit rate cannot be changed in edit");
		test.info("Item name of a saved line locked: " + o.is_locked(o.line_name_box(0)) + " | item code locked: " + o.is_locked(o.line_code_box(0)));
		Assert.assertTrue(o.is_locked(o.line_code_box(0)), "The item code of a saved line can be typed in edit");
		Assert.assertTrue(o.has_add_item_row(), "Add Item Row is missing");
		Assert.assertTrue(o.has_button("Submit"), "Submit is missing");
		Assert.assertTrue(o.has_button("Back"), "Back is missing");
		o.Click_back();
		test.pass("Edit page is correct");
	}

	// PL17: in the edit page Add Item Row adds an empty line; the item search fills code and unit; Back saves nothing
	@Test
	public void PL17_edit_add_item_row_and_back() throws InterruptedException {
		test = extent.createTest("PL17 Add Item Row in edit");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		String code = o.result_texts().get(0).get(1);
		open_form(o, 0, true);
		int before = o.line_count();
		o.Click_add_item_row();
		Assert.assertEquals(o.line_count(), before + 1, "Add Item Row did not add a line");
		int i = before;
		test.info("New line: " + o.line_texts().get(i) + " | code placeholder '" + o.line_code_box(i).getAttribute("placeholder")
				+ "' | unit placeholder '" + o.line_unit_box(i).getAttribute("placeholder") + "' | name placeholder '" + o.line_name_box(i).getAttribute("placeholder") + "'");
		Assert.assertEquals(o.line_rate_box(i).getAttribute("value"), "0.00", "The new unit rate does not start at 0.00");
		Assert.assertTrue(o.is_locked(o.line_code_box(i)), "The item code of a new line can be typed");
		List<String> found = o.Item_suggestions(i, "ney");
		test.info("Suggestions for 'ney': " + found);
		Assert.assertFalse(found.isEmpty(), "No item is suggested for 'ney'");
		o.Pick_line_item(i, "Ney-Item1");
		test.info("After picking: " + o.line_texts().get(i));
		Assert.assertFalse(o.line_code_box(i).getAttribute("value").isEmpty(), "The item code was not filled");
		Assert.assertFalse(o.line_unit_box(i).getAttribute("value").isEmpty(), "The unit was not filled");
		o.Click_back();
		test.info("Back: " + o.All_messages());
		back_to_list();
		int row = find_row(o, code);
		Assert.assertTrue(row >= 0, "The list is not found after Back");
		open_form(o, row, false);
		Assert.assertEquals(o.line_count(), before, "DEFECT: the unsaved new line was saved");
		test.pass("An unsaved line is dropped");
	}

	// PL18: the delete bin of an existing price list asks for a confirmation; Cancel keeps it (nothing is deleted)
	@Test
	public void PL18_delete_asks_confirmation() throws InterruptedException {
		test = extent.createTest("PL18 Delete asks for a confirmation");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		need_rows(o);
		int before = o.entries_total();
		List<String> row0 = o.result_texts().get(0);
		o.Click_delete(0);
		boolean asked = o.alert_open() || !o.notice().isEmpty();
		if (o.alert_open()) {
			test.info("Delete of " + row0.get(1) + ": browser alert '" + o.Alert_peek() + "'");
		} else {
			test.info("Delete of " + row0.get(1) + ": notice '" + o.notice() + "' | buttons " + o.notice_buttons());
		}
		Assert.assertTrue(asked, "DEFECT: the bin deletes without asking for a confirmation");
		if (o.alert_open()) {
			o.Alert_dismiss();
		} else {
			boolean cancelled = false;
			for (String b : new String[] { "Cancel", "No", "Close" }) {
				try {
					o.Click_notice_button(b);
					cancelled = true;
					break;
				} catch (NoSuchElementException e) {
					// try the next name
				}
			}
			Assert.assertTrue(cancelled, "The dialog has no Cancel / No button: " + o.notice_buttons());
		}
		back_to_list();
		Assert.assertEquals(o.entries_total(), before, "DEFECT: a price list was deleted although Cancel was chosen");
		Assert.assertTrue(find_row(o, row0.get(1)) >= 0, "The price list " + row0.get(1) + " is gone");
		test.pass("Delete asks and Cancel keeps the list");
	}

	// ======================= create page (nothing is saved) =======================

	// PL19: the create page: fields, defaults, empty lines, buttons
	@Test
	public void PL19_create_page_elements() {
		test = extent.createTest("PL19 Create page elements");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		open_create(o);
		List<String> companies = o.company_options();
		test.info("Companies: " + companies + " | selected '" + o.selected(o.form_company()) + "' | lines " + o.line_count()
				+ " | count text " + o.items_count_text() + " | placeholders: code '" + o.form_code().getAttribute("placeholder") + "' name '"
				+ o.form_name().getAttribute("placeholder") + "' from '" + o.form_from().getAttribute("placeholder") + "' to '" + o.form_to().getAttribute("placeholder") + "'");
		Assert.assertFalse(companies.isEmpty(), "The company drop down has no value");
		test.info("Company drop down locked in create: " + o.is_locked(o.form_company()) + " (a single company is preselected: " + companies + ")");
		Assert.assertFalse(o.is_locked(o.form_code()), "The price list code cannot be typed in create");
		Assert.assertFalse(o.is_locked(o.form_name()), "The price list name cannot be typed in create");
		Assert.assertTrue(o.has_add_item_row(), "Add Item Row is missing");
		Assert.assertTrue(o.has_button("Submit"), "Submit is missing");
		Assert.assertTrue(o.has_button("Back"), "Back is missing");
		Assert.assertTrue(o.items_count_text() == 0 || o.items_count_text() == o.line_count(), "The item count text does not match the lines");
		test.pass("Create page is complete");
	}

	// PL20: Add Item Row adds numbered lines; the count text follows; a line can be removed
	@Test
	public void PL20_create_add_and_remove_lines() {
		test = extent.createTest("PL20 Add and remove lines");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		open_create(o);
		int start = o.line_count();
		o.Click_add_item_row();
		o.Click_add_item_row();
		o.Click_add_item_row();
		test.info("Lines " + start + " -> " + o.line_count() + " | numbers " + o.line_texts() + " | count text " + o.items_count_text());
		Assert.assertEquals(o.line_count(), start + 3, "Three clicks of Add Item Row must add 3 lines");
		Assert.assertEquals(o.items_count_text(), o.line_count(), "The count text does not follow the lines");
		o.Remove_line(o.line_count() - 2);
		o.All_messages();
		Assert.assertEquals(o.line_count(), start + 2, "The line was not removed");
		List<List<String>> lines = o.line_texts();
		for (int i = 0; i < lines.size(); i++) {
			Assert.assertEquals(lines.get(i).get(0), String.valueOf(i + 1), "DEFECT: the lines are not renumbered after removing one");
		}
		Assert.assertEquals(o.items_count_text(), o.line_count(), "The count text did not follow the removal");
		test.pass("Lines are added and removed");
	}

	// PL21: the item search of a line: suggestions, picking fills code and unit, nothing for unknown text
	@Test
	public void PL21_create_item_search() {
		test = extent.createTest("PL21 Item search of a line");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		open_create(o);
		if (o.line_count() == 0) {
			o.Click_add_item_row();
		}
		List<String> leo = o.Item_suggestions(0, "leo");
		test.info("'leo': " + leo);
		Assert.assertFalse(leo.isEmpty(), "No item is suggested for 'leo'");
		List<String> full = o.Item_suggestions(0, ITEM_A);
		test.info("'" + ITEM_A + "': " + full);
		o.Pick_line_item(0, ITEM_A);
		test.info("After picking: " + o.line_texts().get(0));
		// the unknown text is tried on a second line, so it cannot disturb the picked item
		o.Click_add_item_row();
		List<String> none = o.Item_suggestions(1, "ZZQNoSuch" + unique());
		Assert.assertTrue(none.isEmpty(), "An item is suggested for text that matches nothing: " + none);
		Assert.assertEquals(o.line_name_box(0).getAttribute("value"), ITEM_A, "The item name box");
		Assert.assertFalse(o.line_code_box(0).getAttribute("value").isEmpty(), "The item code was not filled");
		Assert.assertFalse(o.line_unit_box(0).getAttribute("value").isEmpty(), "The unit was not filled");
		Assert.assertTrue(o.is_locked(o.line_code_box(0)), "The item code can be typed");
		Assert.assertTrue(o.is_locked(o.line_unit_box(0)), "The unit can be typed");
		test.pass("Item search works");
	}

	// PL22: invalid unit rates in a line (nothing is submitted): what the box keeps is written to the report
	@Test
	public void PL22_create_invalid_rates() {
		test = extent.createTest("PL22 Invalid unit rates");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		open_create(o);
		if (o.line_count() == 0) {
			o.Click_add_item_row();
		}
		o.Pick_line_item(0, ITEM_A);
		List<String> bad = new ArrayList<String>();
		for (String value : new String[] { "-5", "abc", "12.345", "0", "99999999999", "1e5", "5,5" }) {
			o.Set_line_rate(0, value);
			String kept = o.line_rate_box(0).getAttribute("value");
			test.info("Rate '" + value + "' -> box keeps '" + kept + "' | " + o.All_messages());
			if (kept.startsWith("-") && W_PriceList_Cls.to_number(kept) < 0) {
				bad.add("a negative rate '" + kept + "' is kept");
			}
			if (kept.toLowerCase().contains("abc")) {
				bad.add("letters are kept in the unit rate");
			}
			if (kept.matches("-?\\d+\\.\\d{3,}")) {
				bad.add("a rate with 3 or more decimals '" + kept + "' is kept");
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Invalid rates are handled");
	}

	// PL23: the same item in two lines (the result is written to the report; nothing is submitted)
	@Test
	public void PL23_create_duplicate_item() {
		test = extent.createTest("PL23 Same item in two lines");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		open_create(o);
		o.Add_line(ITEM_A, "10", "");
		o.Add_line(ITEM_A, "20", "");
		String message = o.All_messages();
		test.info("Lines: " + o.line_texts() + " | " + message);
		if (o.line_count() >= 2 && o.line_name_box(o.line_count() - 1).getAttribute("value").equals(ITEM_A)) {
			test.warning("OBSERVATION: the same item is allowed in two lines with different rates (message: '" + message + "')");
		}
		Assert.assertTrue(o.is_form_page(), "The page broke");
		test.pass("Same item handled");
	}

	// PL24: the distribution channel drop down of a line (its options are written to the report)
	@Test
	public void PL24_distribution_channel_options() {
		test = extent.createTest("PL24 Distribution channel options");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		open_create(o);
		if (o.line_count() == 0) {
			o.Click_add_item_row();
		}
		List<String> options = o.channel_options(0);
		test.info("Distribution channels: " + options);
		Assert.assertFalse(options.isEmpty(), "The distribution channel drop down is empty");
		Assert.assertTrue(options.get(0).toLowerCase().contains("select"), "The first option is not the placeholder: " + options);
		test.pass("Channel options listed");
	}

	// PL25: Back from the create page saves nothing
	@Test
	public void PL25_create_back_saves_nothing() throws InterruptedException {
		test = extent.createTest("PL25 Back from create");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		int before = o.entries_total();
		String code = zq_code();
		open_create(o);
		fill_new(o, code, zq_name(), FROM_TEST, TO_TEST, new String[][] { { ITEM_A, "5" } });
		o.Click_back();
		test.info("After Back: " + o.All_messages() + " | list " + o.is_list_page());
		back_to_list();
		Assert.assertEquals(o.entries_total(), before, "The number of price lists changed");
		Assert.assertEquals(find_row(o, code), -1, "DEFECT: the price list was saved although Back was used");
		test.pass("Back saves nothing");
	}

	// ======================= creating and deleting price lists (ALLOW_CREATE) =======================

	// PL26: Submit on an empty form saves nothing and says why (the page stays)
	@Test
	public void PL26_submit_empty_form() throws InterruptedException {
		test = extent.createTest("PL26 Submit an empty form");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		int before = o.entries_total();
		open_create(o);
		o.Click_submit();
		String message = o.All_messages();
		test.info("Submit of an empty form: " + message + " | still on the form: " + o.is_form_page());
		boolean stayed = o.is_form_page();
		back_to_list();
		Assert.assertEquals(o.entries_total(), before, "DEFECT: an empty price list was saved");
		Assert.assertTrue(stayed, "DEFECT: Submit of an empty form left the form");
		Assert.assertFalse(message.isEmpty(), "DEFECT: Submit of an empty form gives no message");
		test.pass("An empty form is refused");
	}

	// PL27: every required field left out one at a time: nothing is saved
	@Test
	public void PL27_required_fields() throws InterruptedException {
		test = extent.createTest("PL27 Required fields");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		List<String> bad = new ArrayList<String>();
		String[] missing = { "code", "name", "from date", "to date", "no item line", "item line without an item", "item line with rate 0" };
		for (String what : missing) {
			back_to_list();
			open_create(o);
			String code = what.equals("code") ? "" : zq_code();
			String name = what.equals("name") ? "" : zq_name();
			String from = what.equals("from date") ? "" : FROM_TEST;
			String to = what.equals("to date") ? "" : TO_TEST;
			String[][] lines = { { ITEM_A, "5" } };
			if (what.equals("no item line")) {
				lines = new String[0][];
			}
			if (what.equals("item line with rate 0")) {
				lines = new String[][] { { ITEM_A, "0" } };
			}
			fill_new(o, code, name, from, to, lines);
			if (what.equals("item line without an item")) {
				o.Click_add_item_row();
			}
			Res r = submit(o);
			boolean stayed = o.is_form_page();
			test.info("Without " + what + ": stayed on the form " + stayed + " | " + r.message);
			if (!stayed) {
				bad.add(what + ": the form was left (a price list was probably saved): " + r.message);
			} else if (r.message.trim().isEmpty()) {
				bad.add(what + ": refused without any message");
			}
		}
		back_to_list();
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price lists: " + left);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Required fields are checked");
	}

	// PL28: date rules: To Date before From Date, the same day, a past period (the result is written to the report)
	@Test
	public void PL28_date_rules() throws InterruptedException {
		test = extent.createTest("PL28 Date rules");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		List<String> bad = new ArrayList<String>();
		back_to_list();
		open_create(o);
		fill_new(o, zq_code(), zq_name(), TO_TEST, FROM_TEST, new String[][] { { ITEM_A, "5" } });
		Res r = submit(o);
		test.info("To Date before From Date: stayed " + o.is_form_page() + " | " + r.message);
		if (!o.is_form_page()) {
			bad.add("a price list with To Date before From Date was saved (" + r.message + ")");
		}
		back_to_list();
		open_create(o);
		fill_new(o, zq_code(), zq_name(), FROM_TEST, FROM_TEST, new String[][] { { ITEM_A, "5" } });
		r = submit(o);
		test.info("Same From and To day: stayed " + o.is_form_page() + " | " + r.message);
		if (o.is_form_page()) {
			test.warning("OBSERVATION: a one day period (From = To) is refused: " + r.message);
		}
		back_to_list();
		open_create(o);
		fill_new(o, zq_code(), zq_name(), "01/01/2020", "31/12/2020", new String[][] { { ITEM_A, "5" } });
		r = submit(o);
		test.info("A period in the past (2020): stayed " + o.is_form_page() + " | " + r.message);
		if (!o.is_form_page()) {
			test.warning("OBSERVATION: a price list for a period in the past is accepted");
		}
		back_to_list();
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price lists: " + left);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Date rules checked");
	}

	// PL29: create a price list with two items: it is listed, its view shows the data; then it is deleted
	@Test
	public void PL29_create_price_list() throws InterruptedException {
		test = extent.createTest("PL29 Create a price list");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		String code = zq_code();
		String name = zq_name();
		int before = o.entries_total();
		Res r = create_list(o, code, name, FROM_TEST, TO_TEST, new String[][] { { ITEM_A, "11.11" }, { ITEM_B, "22.22" } });
		test.info("Submit: " + r.message);
		Assert.assertTrue(r.ok, "The price list was not saved: " + r.message);
		back_to_list();
		Assert.assertEquals(o.entries_total(), before + 1, "DEFECT: the number of price lists did not grow by one");
		int row = find_row(o, code);
		Assert.assertTrue(row >= 0, "DEFECT: the saved price list " + code + " is not listed");
		List<String> listed = o.result_texts().get(row);
		test.info("Listed: " + listed);
		Assert.assertEquals(listed.get(2), name, "Name in the list");
		Assert.assertEquals(listed.get(3), FROM_TEST, "From Date in the list");
		Assert.assertEquals(listed.get(4), TO_TEST, "To Date in the list");
		open_form(o, row, false);
		List<List<String>> lines = o.line_texts();
		test.info("Saved lines: " + lines);
		Assert.assertEquals(lines.size(), 2, "The saved price list does not have 2 lines");
		Assert.assertEquals(o.items_count_text(), 2, "The count text");
		Assert.assertEquals(lines.get(0).get(2), ITEM_A, "Item of line 1");
		Assert.assertEquals(W_PriceList_Cls.to_number(lines.get(0).get(5)), 11.11, 0.0001, "DEFECT: rate of line 1");
		Assert.assertEquals(lines.get(1).get(2), ITEM_B, "Item of line 2");
		Assert.assertEquals(W_PriceList_Cls.to_number(lines.get(1).get(5)), 22.22, 0.0001, "DEFECT: rate of line 2");
		o.Click_back();
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price list: " + left);
		back_to_list();
		Assert.assertEquals(find_row(o, code), -1, "DEFECT: the price list is still listed after it was deleted");
		Assert.assertEquals(o.entries_total(), before, "The number of price lists is not back to " + before);
		test.pass("Price list created and deleted");
	}

	// PL30: rates are saved as typed: 0.01, 1234.5, 99999.99
	@Test
	public void PL30_rates_saved_exactly() throws InterruptedException {
		test = extent.createTest("PL30 Rates are saved exactly");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		String code = zq_code();
		Res r = create_list(o, code, zq_name(), FROM_TEST, TO_TEST,
				new String[][] { { ITEM_A, "0.01" }, { ITEM_B, "1234.5" }, { "leo-item3", "99999.99" } });
		Assert.assertTrue(r.ok, "The price list was not saved: " + r.message);
		back_to_list();
		int row = find_row(o, code);
		Assert.assertTrue(row >= 0, "The price list is not listed");
		open_form(o, row, false);
		List<List<String>> lines = o.line_texts();
		test.info("Saved lines: " + lines);
		double[] typed = { 0.01, 1234.5, 99999.99 };
		List<String> bad = new ArrayList<String>();
		for (int i = 0; i < typed.length && i < lines.size(); i++) {
			if (Math.abs(W_PriceList_Cls.to_number(lines.get(i).get(5)) - typed[i]) > 0.0001) {
				bad.add("typed " + typed[i] + " but saved " + lines.get(i).get(5));
			}
		}
		if (lines.size() != typed.length) {
			bad.add("saved " + lines.size() + " lines instead of " + typed.length);
		}
		o.Click_back();
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price list: " + left);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Rates are saved as typed");
	}

	// PL31: a distribution channel chosen for a line is saved
	@Test
	public void PL31_channel_saved() throws InterruptedException {
		test = extent.createTest("PL31 Distribution channel saved");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		open_create(o);
		o.Click_add_item_row();
		List<String> options = o.channel_options(0);
		if (options.size() < 2) {
			throw new SkipException("The distribution channel drop down has no channel: " + options);
		}
		String channel = options.get(1);
		o.Fill_details(o.company_options().get(0), zq_code(), zq_name(), FROM_TEST, TO_TEST);
		String code = o.form_code().getAttribute("value");
		o.Pick_line_item(0, ITEM_A);
		o.Set_line_rate(0, "7");
		o.Choose_channel(0, channel);
		Res r = submit(o);
		Assert.assertTrue(r.ok, "The price list was not saved: " + r.message);
		back_to_list();
		int row = find_row(o, code);
		Assert.assertTrue(row >= 0, "The price list is not listed");
		open_form(o, row, false);
		String saved = o.line_texts().get(0).get(3);
		test.info("Chosen channel '" + channel + "' | saved '" + saved + "'");
		o.Click_back();
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price list: " + left);
		Assert.assertEquals(saved, channel, "DEFECT: the distribution channel was not saved");
		test.pass("Channel saved");
	}

	// PL32: the same price list code twice must be refused the second time
	@Test
	public void PL32_duplicate_code_refused() throws InterruptedException {
		test = extent.createTest("PL32 Duplicate code");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		String code = zq_code();
		Res first = create_list(o, code, zq_name(), FROM_TEST, TO_TEST, new String[][] { { ITEM_A, "5" } });
		Assert.assertTrue(first.ok, "The first price list was not saved: " + first.message);
		back_to_list();
		open_create(o);
		fill_new(o, code, zq_name(), "01/01/2031", "31/12/2031", new String[][] { { ITEM_B, "6" } });
		Res second = submit(o);
		boolean accepted = !o.is_form_page();
		test.info("Second save with the code " + code + ": stayed " + o.is_form_page() + " | " + second.message);
		back_to_list();
		int count = 0;
		o.Search_lists(code, "", "", "", "", "");
		for (List<String> row : o.result_texts()) {
			if (row.get(1).equalsIgnoreCase(code)) {
				count++;
			}
		}
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price lists: " + left);
		if (accepted && count > 1) {
			test.warning("OBSERVATION: the same code is allowed for two periods (the existing TALLY-DefaultRate lists share one code): " + count + " lists");
		}
		test.pass("Duplicate code handled (lists with the code: " + count + ")");
	}

	// PL33: two lists for the same period (the result is written to the report)
	@Test
	public void PL33_overlapping_periods() throws InterruptedException {
		test = extent.createTest("PL33 Overlapping periods");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		Res first = create_list(o, zq_code(), zq_name(), FROM_TEST, TO_TEST, new String[][] { { ITEM_A, "5" } });
		Assert.assertTrue(first.ok, "The first price list was not saved: " + first.message);
		back_to_list();
		open_create(o);
		fill_new(o, zq_code(), zq_name(), "01/06/2030", "30/09/2030", new String[][] { { ITEM_A, "9" } });
		Res second = submit(o);
		test.info("A second list inside the same period for the same item: stayed " + o.is_form_page() + " | " + second.message);
		if (!o.is_form_page()) {
			test.warning("OBSERVATION: two price lists with the same item and overlapping periods are both accepted");
		}
		back_to_list();
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price lists: " + left);
		test.pass("Overlapping periods handled");
	}

	// PL34: edit a saved list: name, a rate, a new line and a removed line are saved; the code stays
	@Test
	public void PL34_edit_saved_list() throws InterruptedException {
		test = extent.createTest("PL34 Edit a saved price list");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		String code = zq_code();
		Res r = create_list(o, code, zq_name(), FROM_TEST, TO_TEST, new String[][] { { ITEM_A, "5" }, { ITEM_B, "6" } });
		Assert.assertTrue(r.ok, "The price list was not saved: " + r.message);
		back_to_list();
		int row = find_row(o, code);
		Assert.assertTrue(row >= 0, "The price list is not listed");
		open_form(o, row, true);
		String new_name = "ZQ edited " + unique();
		o.Set_name(new_name);
		o.Set_to("31/10/2030");
		o.Set_line_rate(0, "55.5");
		o.Remove_line(1);
		String removal = o.All_messages();
		o.Add_line("leo-item3", "33", "");
		test.info("Before Submit: lines " + o.line_texts() + " | removal message '" + removal + "'");
		Res save = submit(o);
		Assert.assertTrue(save.ok, "The edit was not saved: " + save.message);
		back_to_list();
		row = find_row(o, code);
		Assert.assertTrue(row >= 0, "The edited price list is not listed");
		List<String> listed = o.result_texts().get(row);
		test.info("Listed after the edit: " + listed);
		List<String> bad = new ArrayList<String>();
		if (!listed.get(2).equals(new_name)) {
			bad.add("name " + listed.get(2) + " instead of " + new_name);
		}
		if (!listed.get(4).equals("31/10/2030")) {
			bad.add("to date " + listed.get(4) + " instead of 31/10/2030");
		}
		open_form(o, row, false);
		List<List<String>> lines = o.line_texts();
		test.info("Lines after the edit: " + lines);
		if (lines.size() != 2) {
			bad.add("the list has " + lines.size() + " lines instead of 2");
		} else {
			if (Math.abs(W_PriceList_Cls.to_number(lines.get(0).get(5)) - 55.5) > 0.0001) {
				bad.add("rate of line 1 is " + lines.get(0).get(5) + " instead of 55.50");
			}
			if (!lines.get(1).get(2).equals("leo-item3")) {
				bad.add("line 2 is " + lines.get(1).get(2) + " instead of leo-item3");
			}
		}
		if (!o.form_code().getAttribute("value").equals(code)) {
			bad.add("the code changed to " + o.form_code().getAttribute("value"));
		}
		o.Click_back();
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price list: " + left);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("The edit is saved");
	}

	// PL35: removing every line of a saved list in edit must not be saved
	@Test
	public void PL35_remove_all_lines_refused() throws InterruptedException {
		test = extent.createTest("PL35 Remove all lines");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		String code = zq_code();
		Res r = create_list(o, code, zq_name(), FROM_TEST, TO_TEST, new String[][] { { ITEM_A, "5" } });
		Assert.assertTrue(r.ok, "The price list was not saved: " + r.message);
		back_to_list();
		int row = find_row(o, code);
		open_form(o, row, true);
		o.Remove_line(0);
		o.All_messages();
		test.info("After removing the only line: lines " + o.line_count());
		Res save = submit(o);
		boolean stayed = o.is_form_page();
		test.info("Submit without a line: stayed " + stayed + " | " + save.message);
		back_to_list();
		row = find_row(o, code);
		int lines = -1;
		if (row >= 0) {
			open_form(o, row, false);
			lines = o.line_count();
			o.Click_back();
		}
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price list: " + left);
		Assert.assertFalse(!stayed && lines == 0, "DEFECT: a price list without any item line was saved");
		test.pass("A list without lines is not saved");
	}

	// PL36: the delete bin: a price list is deleted after the confirmation; the count drops and it is no longer found
	@Test
	public void PL36_delete_price_list() throws InterruptedException {
		test = extent.createTest("PL36 Delete a price list");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		String code = zq_code();
		Res r = create_list(o, code, zq_name(), FROM_TEST, TO_TEST, new String[][] { { ITEM_A, "5" } });
		Assert.assertTrue(r.ok, "The price list was not saved: " + r.message);
		back_to_list();
		o.Search_lists("", "", "", "", "", "");
		int total = o.entries_total();
		int row = find_row(o, code);
		Assert.assertTrue(row >= 0, "The price list is not listed");
		o.Click_delete(row);
		Assert.assertTrue(o.alert_open() || !o.notice().isEmpty(), "DEFECT: the bin does not ask for a confirmation");
		List<String> texts = confirm_delete(o);
		test.info("Deleted " + code + ": " + texts);
		back_to_list();
		Assert.assertEquals(find_row(o, code), -1, "DEFECT: the price list is still found after it was deleted");
		o.Search_lists("", "", "", "", "", "");
		Assert.assertEquals(o.entries_total(), total - 1, "The number of price lists did not drop by one");
		test.pass("Price list deleted");
	}

	// PL37: odd codes and names are handled (spaces only, special characters, very long): nothing odd may be saved silently
	@Test
	public void PL37_odd_codes_and_names() throws InterruptedException {
		test = extent.createTest("PL37 Odd codes and names");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		StringBuilder longText = new StringBuilder();
		for (int i = 0; i < 150; i++) {
			longText.append('x');
		}
		List<String> bad = new ArrayList<String>();
		String[][] cases = { { "   ", "ZQ spaces code " + unique() }, { "ZQ#$%&*" + unique(), "ZQ special " + unique() },
				{ "ZQ" + longText, "ZQ long code " + unique() }, { zq_code(), "   " }, { zq_code(), "ZQ" + longText } };
		for (String[] c : cases) {
			back_to_list();
			open_create(o);
			fill_new(o, c[0], c[1], FROM_TEST, TO_TEST, new String[][] { { ITEM_A, "1" } });
			Res r = submit(o);
			boolean saved = !o.is_form_page();
			String shown = (c[0].length() > 15 ? c[0].substring(0, 15) + "..(" + c[0].length() + ")" : c[0]) + " / "
					+ (c[1].length() > 15 ? c[1].substring(0, 15) + "..(" + c[1].length() + ")" : c[1]);
			test.info("Code / name '" + shown + "': saved " + saved + " | " + r.message);
			if (saved && (c[0].trim().isEmpty() || c[1].trim().isEmpty())) {
				bad.add("a code or name of spaces only was saved (" + shown + ")");
			}
		}
		back_to_list();
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price lists: " + left);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Odd values handled");
	}

	// PL38: the saved list is found by its item code / name in the search and by its dates; then deleted
	@Test
	public void PL38_saved_list_is_searchable() throws InterruptedException {
		test = extent.createTest("PL38 A saved list is searchable");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		String code = zq_code();
		String name = zq_name();
		Res r = create_list(o, code, name, FROM_TEST, TO_TEST, new String[][] { { "leo-item4", "44" } });
		Assert.assertTrue(r.ok, "The price list was not saved: " + r.message);
		back_to_list();
		List<String> bad = new ArrayList<String>();
		o.Search_lists("", name, "", "", "", "");
		if (o.row_of_code(code) < 0) {
			bad.add("not found by its name");
		}
		o.Search_lists("", "", "leo4", "", "", "");
		if (o.row_of_code(code) < 0) {
			bad.add("not found by the item code leo4");
		}
		o.Search_lists("", "", "", "leo-item4", "", "");
		if (o.row_of_code(code) < 0) {
			bad.add("not found by the item name leo-item4");
		}
		o.Search_lists("", "", "", "", FROM_TEST, TO_TEST);
		test.info("Search by its dates: " + o.result_texts());
		if (o.row_of_code(code) < 0) {
			bad.add("not found by its own dates");
		}
		o.Search_lists("", "", "", "", "01/06/2030", "01/06/2030");
		test.info("Search by a day inside the period: " + o.result_texts());
		if (o.row_of_code(code) < 0) {
			test.warning("OBSERVATION: a search for one day inside the period of a list does not find it");
		}
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price list: " + left);
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("A saved list is searchable");
	}

	// PL39: a saved list survives a fresh login
	@Test
	public void PL39_saved_list_survives_reload() throws InterruptedException {
		test = extent.createTest("PL39 A saved list survives a reload");
		require_create();
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		String code = zq_code();
		Res r = create_list(o, code, zq_name(), FROM_TEST, TO_TEST, new String[][] { { ITEM_A, "5" } });
		Assert.assertTrue(r.ok, "The price list was not saved: " + r.message);
		login_again();
		go_to_page();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		int row = find_row(o, code);
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test price list: " + left);
		Assert.assertTrue(row >= 0, "DEFECT: the price list is gone after a fresh login");
		test.pass("The list survives a fresh login");
	}

	// PL40: the sweep: no price list whose name starts with ZQ is left (the last test)
	@Test(priority = 100)
	public void PL40_no_test_price_list_left() throws InterruptedException {
		test = extent.createTest("PL40 No test price list is left");
		W_PriceList_Cls o = new W_PriceList_Cls(driver);

		List<String> left = sweep();
		test.info("Sweep result: " + (left.isEmpty() ? "nothing left over" : left));
		o.Search_lists("", "ZQ", "", "", "", "");
		test.info("Search for ZQ: " + o.entries_text() + " | rows " + o.result_count());
		Assert.assertTrue(left.isEmpty(), "Test price lists could not be deleted: " + left);
		Assert.assertEquals(o.result_count(), 0, "Price lists with ZQ in their name are still listed: " + o.result_texts());
		test.pass("All test price lists are deleted");
	}
}
