package TestPkg;

import UtillPkg.TestEnv;
import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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
import PagePkg.U_DiscountPattern_Cls;
import PagePkg.A_Login_Cls;

/*
 * Discount Pattern (Field Management > Discount Pattern), logged in as the web user - tests DP01 .. DP42.
 * DP01 .. DP31 only read data and fill the create form without saving (and delete nothing).
 * DP32 .. DP41 CREATE patterns on test items (name starting with ZQ) and delete them again in the same test;
 * they run only when ALLOW_CREATE is true. Everything that is left over is deleted by the sweep at the end
 * (DP42 and the @AfterClass), which finds the patterns by their name starting with ZQ.
 * An existing pattern is never saved or deleted (the delete dialog of an existing pattern is only Cancelled).
 */
public class U_DiscountPattern_Test extends BaseCls {

	// login of the web user (a test login of this project)
	static final String WEB_USER = TestEnv.get("web.user", "your-web-user");
	static final String WEB_PASS = TestEnv.get("web.password", "your-web-password");

	// true = the tests that create (and then delete) discount patterns are run
	static final boolean ALLOW_CREATE = true;

	// subjects of the test patterns (typed in the Sub category / Item box)
	static final String ITEM_1 = "leo-item4";
	static final String ITEM_2 = "leo-item3";
	static final String CATEGORY_1 = "Leo-Cate";
	static final String SUBCATEGORY_1 = "ADHESIVE";

	static final List<String> APPLY_TO = Arrays.asList("Category", "SubCategory", "Item", "Menu Card", "Bill");

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
		try {
			new U_DiscountPattern_Cls(driver).Close_modal();
		} catch (Exception e) {
			// no pop-up
		}
	}

	// opens Field Management > Discount Pattern from the dashboard
	private void go_to_page() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_containing("Field Management", "Discount Pattern");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Field Management", "DiscountPattern");
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

	// every test starts with a fresh Discount Pattern list
	@BeforeMethod
	public void open_page() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		clean_up();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);
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

	private String zq_name() {
		return "ZQ name " + unique();
	}

	private void open_create(U_DiscountPattern_Cls o) {
		o.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_create_page());
	}

	private void back_to_list() throws InterruptedException {
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);
		if (!o.is_list_page()) {
			go_to_page();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		}
	}

	private static class Res {
		boolean ok;
		String message = "";
	}

	// clicks Submit and reads the answer (a browser alert, a sweet alert / toast or a return to the list)
	private Res submit(U_DiscountPattern_Cls o) {
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
				|| o.is_list_page();
		return r;
	}

	// answers the delete confirmation (a browser alert or a sweet alert) with its confirm button; returns the texts
	private List<String> confirm_delete(U_DiscountPattern_Cls o) {
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

	// searches the patterns by name and returns the row of the group (classification + subject), -1 when not found
	private int find_group(U_DiscountPattern_Cls o, String name, String classification, String subject) {
		o.Search_patterns("", name, "", "");
		return o.row_index(classification, subject);
	}

	// the patterns of the group in the row (view pop-up); the pop-up is closed again
	private List<List<String>> patterns_of_row(U_DiscountPattern_Cls o, int row) {
		o.Click_view(row);
		o.Modal_set_page_length_all();
		List<List<String>> rows = o.modal_rows();
		o.Close_modal();
		return rows;
	}

	// finds the pattern row {code, name, apply to, item, type, value} with this name in the group of a search result
	private List<String> pattern_named(U_DiscountPattern_Cls o, String name, String classification, String subject) {
		int row = find_group(o, name, classification, subject);
		if (row < 0) {
			return null;
		}
		for (List<String> p : patterns_of_row(o, row)) {
			if (p.get(1).equalsIgnoreCase(name)) {
				return p;
			}
		}
		return null;
	}

	// deletes the patterns whose name starts with ZQ, group by group, until none is found (or nothing can be deleted);
	// returns what could not be deleted
	private List<String> sweep() throws InterruptedException {
		List<String> left = new ArrayList<String>();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);
		for (int round = 0; round < 60; round++) {
			back_to_list();
			o.Search_patterns("", "ZQ", "", "");
			int groups = o.result_count();
			if (groups == 0) {
				return left;
			}
			// open the groups one by one until one has a ZQ pattern that can be deleted
			boolean deleted = false;
			List<String> diagnostics = new ArrayList<String>();
			for (int g = 0; g < groups && !deleted; g++) {
				o.Click_edit(g);
				if (!o.modal_open()) {
					diagnostics.add("group " + (g + 1) + ": the edit pop-up did not open");
					continue;
				}
				o.Modal_set_page_length_all();
				List<String> target = null;
				for (List<String> p : o.modal_rows()) {
					if (p.get(1).toUpperCase().contains("ZQ") || p.get(0).toUpperCase().startsWith("ZQ")) {
						target = p;
						break;
					}
				}
				if (target == null) {
					diagnostics.add("group " + (g + 1) + " pop-up: " + o.modal_text() + " | lines " + o.modal_rows());
					o.Close_modal();
					continue;
				}
				o.Click_modal_delete(target.get(0));
				confirm_delete(o);
				o.Close_modal();
				deleted = true;
				WaitUtil.settle(driver, 500);
			}
			if (!deleted) {
				left.add("groups match ZQ but no ZQ pattern could be found in them: " + o.result_texts() + " | " + diagnostics);
				return left;
			}
		}
		left.add("more than 60 patterns were deleted: stopped");
		return left;
	}

	@AfterClass(alwaysRun = true)
	public void sweep_after_class() {
		try {
			List<String> left = sweep();
			System.out.println("Discount Pattern sweep: " + (left.isEmpty() ? "nothing left over" : "LEFT OVER: " + left));
		} catch (Exception e) {
			System.out.println("Discount Pattern sweep failed: " + e.getMessage());
		}
	}

	// the first row's patterns (existing data), used by the read only tests
	private List<List<String>> first_group(U_DiscountPattern_Cls o) {
		if (o.result_count() == 0) {
			throw new SkipException("The list has no discount pattern");
		}
		o.Click_view(0);
		List<List<String>> rows = o.modal_rows();
		o.Close_modal();
		return rows;
	}

	// ======================= list and search =======================

	// DP01: the list shows Search, Create, the table filter, "Show N entries", the 4 columns and the entries line
	@Test
	public void DP01_list_elements() {
		test = extent.createTest("DP01 Discount Pattern list elements");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		Assert.assertTrue(o.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(o.verify_create_btn().isDisplayed(), "Create button is missing");
		Assert.assertTrue(o.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		Assert.assertTrue(o.verify_page_length().isDisplayed(), "'Show N entries' is missing");
		List<String> headers = o.headers();
		test.info("Headers: " + headers + " | " + o.entries_text());
		for (String h : new String[] { "CLASSIFICATION", "SUBCATEGORY / ITEM / CATEGORY", "NUMBER OF DISCOUNT PATTERN", "ACTION" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(o.entries_text().contains("Showing"), "Entries line: " + o.entries_text());
		test.pass("Screen elements are displayed");
	}

	// DP02: the search form has its 4 fields, Clear and Search; Apply Item / Sub category is locked until Apply To is chosen
	@Test
	public void DP02_search_form() {
		test = extent.createTest("DP02 Search form");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		o.Open_search_form();
		Assert.assertTrue(o.verify_code().isDisplayed(), "Discount Pattern Code is missing");
		Assert.assertTrue(o.verify_name().isDisplayed(), "Discount Pattern Name is missing");
		Assert.assertTrue(o.verify_apply_to().isDisplayed(), "Apply To is missing");
		Assert.assertTrue(o.verify_subject().isDisplayed(), "Apply Item / Sub category is missing");
		Assert.assertTrue(o.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertTrue(o.verify_clear().isDisplayed(), "The Clear button is missing");
		test.info("Subject box: placeholder '" + o.verify_subject().getAttribute("placeholder") + "' | locked " + o.is_locked(o.verify_subject()));
		Assert.assertTrue(o.is_locked(o.verify_subject()), "DEFECT: Apply Item / Sub category can be used before Apply To is chosen");
		o.Search_patterns("", "", "Item", "");
		o.Open_search_form();
		test.info("After choosing Apply To: locked " + o.is_locked(o.verify_subject()) + " | placeholder '" + o.verify_subject().getAttribute("placeholder") + "'");
		Assert.assertFalse(o.is_locked(o.verify_subject()), "Apply Item / Sub category stays locked after Apply To is chosen");
		test.pass("Search form is correct");
	}

	// DP03: Apply To has Category, SubCategory, Item, Menu Card and Bill (search form and create page)
	@Test
	public void DP03_apply_to_options() {
		test = extent.createTest("DP03 Apply To options");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		o.Open_search_form();
		List<String> search = o.options_of(o.verify_apply_to());
		test.info("Search form: " + search);
		Assert.assertEquals(search, APPLY_TO, "Apply To of the search form");
		open_create(o);
		List<String> create = o.options_of(o.create_apply_to());
		test.info("Create page: " + create);
		Assert.assertEquals(create, APPLY_TO, "Apply To of the create page");
		test.pass("Apply To options are correct");
	}

	// DP04: formats of the list: classification is one of the 5, the number is a whole number above 0, no empty cell
	@Test
	public void DP04_list_formats() {
		test = extent.createTest("DP04 List formats");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		Assert.assertTrue(o.result_count() > 0, "The list has no discount pattern");
		List<String> bad = new ArrayList<String>();
		for (List<String> row : o.result_texts()) {
			if (!APPLY_TO.contains(row.get(0))) {
				bad.add("classification '" + row.get(0) + "'");
			}
			if (row.get(1).isEmpty() && !row.get(0).equals("Bill")) {
				bad.add("an empty item / category in " + row);
			}
			if (!row.get(2).matches("\\d+") || Integer.parseInt(row.get(2)) < 1) {
				bad.add("number of patterns '" + row.get(2) + "' in " + row);
			}
		}
		test.info("Rows: " + o.result_texts());
		Assert.assertTrue(bad.isEmpty(), "DEFECT in the list: " + bad);
		test.pass("List formats are correct");
	}

	// DP05: Apply To filter: every option lists only its own classification
	@Test
	public void DP05_search_by_apply_to() {
		test = extent.createTest("DP05 Search by Apply To");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		List<String> bad = new ArrayList<String>();
		for (String apply : APPLY_TO) {
			o.Search_patterns("", "", apply, "");
			test.info(apply + ": " + o.entries_text() + " | rows " + o.result_count());
			for (List<String> row : o.result_texts()) {
				if (!row.get(0).equalsIgnoreCase(apply)) {
					bad.add(apply + " -> " + row);
				}
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: rows of another classification are listed: " + bad);
		test.pass("Apply To filter is correct");
	}

	// DP06: search by the code of an existing pattern finds its group
	@Test
	public void DP06_search_by_code() {
		test = extent.createTest("DP06 Search by code");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		List<String> p = first_group(o).get(0);
		String subject = o.result_texts().get(0).get(1);
		test.info("Code used: " + p.get(0) + " (group " + subject + ")");
		o.Search_patterns(p.get(0), "", "", "");
		Assert.assertTrue(o.result_count() > 0, "DEFECT: the code " + p.get(0) + " is not found");
		boolean found = false;
		for (List<String> row : o.result_texts()) {
			if (row.get(1).equalsIgnoreCase(subject)) {
				found = true;
			}
		}
		Assert.assertTrue(found, "The group " + subject + " is not listed for its own code");
		o.Search_patterns(p.get(0).toLowerCase(), "", "", "");
		test.info("Lower case code: rows " + o.result_count());
		test.pass("Search by code works");
	}

	// DP07: search by the name of an existing pattern finds its group
	@Test
	public void DP07_search_by_name() {
		test = extent.createTest("DP07 Search by name");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		List<String> p = first_group(o).get(0);
		String subject = o.result_texts().get(0).get(1);
		test.info("Name used: " + p.get(1) + " (group " + subject + ")");
		o.Search_patterns("", p.get(1), "", "");
		Assert.assertTrue(o.result_count() > 0, "DEFECT: the name " + p.get(1) + " is not found");
		boolean found = false;
		for (List<String> row : o.result_texts()) {
			if (row.get(1).equalsIgnoreCase(subject)) {
				found = true;
			}
		}
		Assert.assertTrue(found, "The group " + subject + " is not listed for its own pattern name");
		String part = p.get(1).substring(0, Math.max(1, p.get(1).length() / 2));
		o.Search_patterns("", part, "", "");
		test.info("Part of the name '" + part + "': rows " + o.result_count() + " (partial search)");
		test.pass("Search by name works");
	}

	// DP08: a code and a name that do not exist list nothing
	@Test
	public void DP08_unknown_code_and_name() {
		test = extent.createTest("DP08 Unknown code and name");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		int all = o.entries_total();
		o.Search_patterns("ZZQNoSuchCode" + unique(), "", "", "");
		test.info("Unknown code: rows " + o.result_count() + " | " + o.entries_text() + " (all " + all + ")");
		Assert.assertEquals(o.result_count(), 0, "DEFECT: patterns are listed for a code that does not exist");
		Assert.assertTrue(o.no_data_shown(), "No 'No Data' line is shown");
		o.Search_patterns("", "ZZQNoSuchName" + unique(), "", "");
		Assert.assertEquals(o.result_count(), 0, "DEFECT: patterns are listed for a name that does not exist");
		test.pass("Unknown values list nothing");
	}

	// DP09: Apply To + item / category: only that group
	@Test
	public void DP09_search_by_subject() {
		test = extent.createTest("DP09 Search by item / category");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		if (o.result_count() == 0) {
			throw new SkipException("The list has no discount pattern");
		}
		List<String> row0 = o.result_texts().get(0);
		test.info("Used: " + row0.get(0) + " / " + row0.get(1));
		o.Search_patterns("", "", row0.get(0), row0.get(1));
		Assert.assertTrue(o.result_count() > 0, "The group " + row0.get(1) + " is not found");
		for (List<String> row : o.result_texts()) {
			Assert.assertEquals(row.get(0), row0.get(0), "Another classification: " + row);
			Assert.assertEquals(row.get(1), row0.get(1), "Another item / category: " + row);
		}
		test.pass("Only " + row0.get(1) + " is listed");
	}

	// DP10: Clear empties the search form
	@Test
	public void DP10_clear() {
		test = extent.createTest("DP10 Clear");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		o.Search_patterns("ZZQCode", "ZZQName", "Item", "");
		o.Click_clear();
		o.Open_search_form();
		test.info("After Clear: code '" + o.verify_code().getAttribute("value") + "' | name '" + o.verify_name().getAttribute("value")
				+ "' | apply to '" + o.selected(o.verify_apply_to()) + "' | " + o.entries_text());
		Assert.assertEquals(o.verify_code().getAttribute("value"), "", "Code was not cleared");
		Assert.assertEquals(o.verify_name().getAttribute("value"), "", "Name was not cleared");
		Assert.assertEquals(o.selected(o.verify_apply_to()), "Select", "Apply To was not cleared");
		Assert.assertTrue(o.result_count() > 0, "The list is empty after Clear");
		test.pass("Clear empties the form");
	}

	// DP11: table filter: by item, nothing, special text
	@Test
	public void DP11_table_filter() {
		test = extent.createTest("DP11 Table filter");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		if (o.result_count() == 0) {
			throw new SkipException("The list has no discount pattern");
		}
		String subject = o.result_texts().get(0).get(1);
		o.Filter_table(subject);
		test.info("Filter '" + subject + "': " + o.entries_text());
		Assert.assertTrue(o.result_count() > 0, "The filter found nothing for " + subject);
		for (List<String> row : o.result_texts()) {
			Assert.assertTrue(row.toString().toLowerCase().contains(subject.toLowerCase()), "Row without the text: " + row);
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

	// DP12: sorting: classification and item as text, number of patterns as numbers
	@Test
	public void DP12_sort_columns() {
		test = extent.createTest("DP12 Sort the columns");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		o.Set_page_length(o.page_length_options().get(o.page_length_options().size() - 1));
		if (o.result_count() < 3) {
			throw new SkipException("Too few rows to check sorting");
		}
		List<String> wrong = new ArrayList<String>();
		for (String[] col : new String[][] { { "CLASSIFICATION", "0" }, { "SUBCATEGORY / ITEM / CATEGORY", "1" } }) {
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
		o.Click_header("NUMBER OF DISCOUNT PATTERN");
		List<Double> numbers = new ArrayList<Double>();
		for (List<String> row : o.result_texts()) {
			numbers.add(U_DiscountPattern_Cls.to_number(row.get(2)));
		}
		List<Double> nup = new ArrayList<Double>(numbers);
		java.util.Collections.sort(nup);
		List<Double> ndown = new ArrayList<Double>(nup);
		java.util.Collections.reverse(ndown);
		boolean number_ok = numbers.equals(nup) || numbers.equals(ndown);
		test.info("NUMBER OF DISCOUNT PATTERN -> " + (number_ok ? "sorted" : "NOT sorted as numbers") + " " + numbers);
		if (!number_ok) {
			wrong.add("NUMBER OF DISCOUNT PATTERN");
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: these columns are not sorted correctly: " + wrong);
		test.pass("All columns sort");
	}

	// DP13: Show N entries and paging
	@Test
	public void DP13_show_entries_and_paging() {
		test = extent.createTest("DP13 Show entries and paging");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		List<String> options = o.page_length_options();
		test.info("Options: " + options + " | selected " + o.page_length_selected() + " | " + o.entries_text());
		Assert.assertEquals(o.page_length_selected(), "10", "Default page length");
		int total = o.entries_total();
		for (String option : options) {
			if (!option.matches("\\d+")) {
				continue;
			}
			o.Set_page_length(option);
			int n = Integer.parseInt(option);
			Assert.assertEquals(o.result_count(), Math.min(n, total), "Rows shown for Show " + n);
		}
		o.Set_page_length("10");
		if (total > 10) {
			List<List<String>> page1 = o.result_texts();
			Assert.assertFalse(o.previous_enabled(), "Previous is enabled on the first page");
			o.Click_next();
			Assert.assertTrue(o.entries_text().contains("Showing 11 to"), "Page 2 text: " + o.entries_text());
			for (List<String> row : o.result_texts()) {
				Assert.assertFalse(page1.contains(row), "A row of page 1 is shown again on page 2: " + row);
			}
			o.Click_previous();
			Assert.assertEquals(o.result_texts(), page1, "Previous does not return to the first page");
		}
		test.pass("Show entries and paging work");
	}

	// ======================= view / edit pop-ups =======================

	// DP14: the view pop-up of the first group: header, columns and patterns
	@Test
	public void DP14_view_pop_up() {
		test = extent.createTest("DP14 View pop-up");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		if (o.result_count() == 0) {
			throw new SkipException("The list has no discount pattern");
		}
		List<String> row0 = o.result_texts().get(0);
		test.info("Icons of the first row: " + o.actions_of(0) + " | " + o.actions_html(0));
		o.Click_view(0);
		Assert.assertTrue(o.modal_open(), "The view pop-up did not open");
		test.info("Pop-up: " + o.modal_text());
		Assert.assertEquals(o.modal_mode(), "View Discount Pattern", "Mode of the pop-up");
		Assert.assertEquals(o.modal_subject(), row0.get(1), "Item / sub category of the pop-up");
		Assert.assertEquals(o.modal_number(), Integer.parseInt(row0.get(2)), "Number of discount patterns of the pop-up");
		List<String> headers = o.modal_headers();
		for (String h : new String[] { "DISCOUNT CODE", "DISCOUNT NAME", "APPLY TO", "ITEM / SUBCATEGORY", "DISCOUNT TYPE", "DISCOUNT VALUE" }) {
			Assert.assertTrue(headers.contains(h), "Pop-up column missing: " + h + " in " + headers);
		}
		Assert.assertFalse(headers.contains("ACTION"), "DEFECT: the view pop-up has an Action column");
		o.Close_modal();
		test.pass("View pop-up is correct");
	}

	// DP15: every group of the page: the pop-up shows the group, its number matches and so do its lines
	@Test
	public void DP15_view_every_group() {
		test = extent.createTest("DP15 View every group");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		List<List<String>> rows = o.result_texts();
		if (rows.isEmpty()) {
			throw new SkipException("The list has no discount pattern");
		}
		List<String> bad = new ArrayList<String>();
		for (int i = 0; i < rows.size(); i++) {
			try {
				o.Click_view(i);
				if (!o.modal_open()) {
					bad.add("row " + (i + 1) + ": the pop-up did not open");
					continue;
				}
				int number = Integer.parseInt(rows.get(i).get(2));
				test.info("Row " + (i + 1) + " " + rows.get(i).get(0) + " / " + rows.get(i).get(1) + ": list " + number
						+ " | pop-up number " + o.modal_number() + " | lines " + o.modal_entries_total());
				if (!o.modal_subject().equals(rows.get(i).get(1))) {
					bad.add("row " + (i + 1) + ": pop-up shows '" + o.modal_subject() + "' instead of '" + rows.get(i).get(1) + "'");
				}
				if (o.modal_number() != number) {
					bad.add("row " + (i + 1) + " (" + rows.get(i).get(1) + "): list " + number + " but pop-up number " + o.modal_number());
				}
				if (o.modal_entries_total() != number) {
					bad.add("row " + (i + 1) + " (" + rows.get(i).get(1) + "): list " + number + " but " + o.modal_entries_total() + " lines");
				}
			} finally {
				o.Close_modal();
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("All groups are consistent");
	}

	// DP16: the lines of every group: apply to, item, type and value make sense
	@Test
	public void DP16_pattern_line_content() {
		test = extent.createTest("DP16 Pattern line content");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		List<List<String>> rows = o.result_texts();
		if (rows.isEmpty()) {
			throw new SkipException("The list has no discount pattern");
		}
		List<String> bad = new ArrayList<String>();
		for (int i = 0; i < rows.size(); i++) {
			try {
				o.Click_view(i);
				List<String> codes = new ArrayList<String>();
				for (List<String> p : o.modal_rows()) {
					test.info("Group " + rows.get(i).get(1) + ": " + p);
					if (p.get(0).isEmpty() || p.get(1).isEmpty()) {
						bad.add(rows.get(i).get(1) + ": empty code or name in " + p);
					}
					if (!p.get(2).equalsIgnoreCase(rows.get(i).get(0))) {
						bad.add(rows.get(i).get(1) + ": apply to '" + p.get(2) + "' but the group is " + rows.get(i).get(0));
					}
					if (!rows.get(i).get(0).equals("Bill") && !p.get(3).equalsIgnoreCase(rows.get(i).get(1))) {
						bad.add(rows.get(i).get(1) + ": line item '" + p.get(3) + "'");
					}
					if (!p.get(4).equals("Amount") && !p.get(4).equals("Percentage")) {
						bad.add(rows.get(i).get(1) + ": discount type '" + p.get(4) + "'");
					}
					double value = U_DiscountPattern_Cls.to_number(p.get(5));
					if (value <= 0) {
						bad.add(rows.get(i).get(1) + ": discount value " + p.get(5) + " is not above 0");
					}
					if (p.get(4).equals("Percentage") && value > 100) {
						bad.add(rows.get(i).get(1) + ": percentage " + p.get(5) + " is above 100");
					}
					if (codes.contains(p.get(0))) {
						bad.add(rows.get(i).get(1) + ": the code " + p.get(0) + " is repeated");
					}
					codes.add(p.get(0));
				}
			} finally {
				o.Close_modal();
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT in the pattern lines: " + bad);
		test.pass("Pattern lines are correct");
	}

	// DP17: the table inside the pop-up: its filter and its Show entries
	@Test
	public void DP17_pop_up_table_filter() {
		test = extent.createTest("DP17 Pop-up table filter");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		if (o.result_count() == 0) {
			throw new SkipException("The list has no discount pattern");
		}
		o.Click_view(0);
		List<List<String>> all = o.modal_rows();
		Assert.assertFalse(all.isEmpty(), "The pop-up has no line");
		String code = all.get(0).get(0);
		o.Modal_filter(code);
		test.info("Filter '" + code + "': " + o.modal_rows());
		Assert.assertTrue(o.modal_rows().size() >= 1, "The pop-up filter found nothing for " + code);
		for (List<String> p : o.modal_rows()) {
			Assert.assertTrue(p.toString().toLowerCase().contains(code.toLowerCase()), "Row without the text: " + p);
		}
		o.Modal_filter("ZZQNothing" + unique());
		Assert.assertEquals(o.modal_rows().size(), 0, "Lines are listed for text that matches nothing");
		o.Modal_filter("");
		Assert.assertEquals(o.modal_rows().size(), all.size(), "The lines did not come back");
		test.info("Pop-up Show entries: " + new org.openqa.selenium.support.ui.Select(o.modal_page_length()).getFirstSelectedOption().getText());
		o.Close_modal();
		test.pass("Pop-up filter works");
	}

	// DP18: the edit pop-up: mode, Action column and a bin for every line
	@Test
	public void DP18_edit_pop_up() {
		test = extent.createTest("DP18 Edit pop-up");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		if (o.result_count() == 0) {
			throw new SkipException("The list has no discount pattern");
		}
		List<String> row0 = o.result_texts().get(0);
		o.Click_edit(0);
		Assert.assertTrue(o.modal_open(), "The edit pop-up did not open");
		test.info("Pop-up: " + o.modal_text());
		Assert.assertEquals(o.modal_mode(), "Edit Discount Pattern", "Mode of the pop-up");
		Assert.assertEquals(o.modal_subject(), row0.get(1), "Item / sub category of the pop-up");
		Assert.assertTrue(o.modal_headers().contains("ACTION"), "The edit pop-up has no Action column");
		Assert.assertEquals(o.modal_delete_count(), o.modal_rows().size(), "A bin for every line");
		o.Close_modal();
		test.pass("Edit pop-up is correct");
	}

	// DP19: the pop-up closes with its Close button and with the x
	@Test
	public void DP19_pop_up_closes() {
		test = extent.createTest("DP19 Pop-up closes");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		if (o.result_count() == 0) {
			throw new SkipException("The list has no discount pattern");
		}
		for (int mode = 0; mode < 2; mode++) {
			if (mode == 0) {
				o.Click_view(0);
			} else {
				o.Click_edit(0);
			}
			Assert.assertTrue(o.modal_open(), "The pop-up did not open");
			o.Click_modal_close_button();
			Assert.assertFalse(o.modal_open(), "The pop-up did not close with Close");
			if (mode == 0) {
				o.Click_view(0);
			} else {
				o.Click_edit(0);
			}
			o.Click_modal_x();
			Assert.assertFalse(o.modal_open(), "The pop-up did not close with the x");
		}
		Assert.assertTrue(o.is_list_page(), "The list is not shown");
		test.pass("Pop-ups close");
	}

	// DP20: the bin of an existing pattern asks for a confirmation; Cancel keeps the pattern (nothing is deleted)
	@Test
	public void DP20_delete_asks_confirmation() {
		test = extent.createTest("DP20 Delete asks for a confirmation");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		if (o.result_count() == 0) {
			throw new SkipException("The list has no discount pattern");
		}
		o.Click_edit(0);
		List<List<String>> before = o.modal_rows();
		String code = before.get(0).get(0);
		o.Click_modal_delete(code);
		boolean asked = o.alert_open() || !o.notice().isEmpty();
		if (o.alert_open()) {
			test.info("Delete of " + code + ": browser alert '" + o.Alert_peek() + "'");
		} else {
			test.info("Delete of " + code + ": notice '" + o.notice() + "' | buttons " + o.notice_buttons());
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
		List<List<String>> after = o.modal_open() ? o.modal_rows() : before;
		o.Close_modal();
		test.info("Lines before " + before.size() + " | after Cancel " + after.size());
		Assert.assertEquals(after, before, "DEFECT: the pattern was deleted although Cancel was chosen");
		test.pass("Delete asks and Cancel keeps the pattern");
	}

	// ======================= create page (nothing is saved) =======================

	// DP21: the create page: fields, defaults, empty grid, Back and Submit
	@Test
	public void DP21_create_page_elements() {
		test = extent.createTest("DP21 Create page elements");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		open_create(o);
		Assert.assertTrue(o.create_apply_to().isDisplayed(), "Apply To is missing");
		Assert.assertTrue(o.create_subject().isDisplayed(), "Sub category / Item is missing");
		Assert.assertTrue(o.create_code().isDisplayed(), "Discount Code is missing");
		Assert.assertTrue(o.create_name().isDisplayed(), "Discount Name is missing");
		Assert.assertTrue(o.create_value().isDisplayed(), "Discount Value is missing");
		test.info("Subject placeholder '" + o.create_subject().getAttribute("placeholder") + "' | code placeholder '"
				+ o.create_code().getAttribute("placeholder") + "' | type '" + o.chosen_type() + "' | headers " + o.headers());
		Assert.assertEquals(o.selected(o.create_apply_to()), "Select", "Apply To default");
		Assert.assertTrue(o.is_locked(o.create_subject()), "DEFECT: Sub category / Item can be used before Apply To is chosen");
		Assert.assertEquals(o.chosen_type(), "Percentage", "Default discount type");
		Assert.assertTrue(o.grid_no_data(), "The grid does not show 'No Data Available'");
		Assert.assertEquals(o.grid_count(), 0, "The grid is not empty");
		Assert.assertTrue(o.has_button("Add New"), "Add New is missing");
		Assert.assertTrue(o.has_button("Back"), "Back is missing");
		Assert.assertTrue(o.has_button("Submit"), "Submit is missing");
		test.pass("Create page is complete");
	}

	// DP22: Sub category / Item opens after Apply To is chosen, for each Apply To (the state is written to the report)
	@Test
	public void DP22_subject_follows_apply_to() {
		test = extent.createTest("DP22 Subject box follows Apply To");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		open_create(o);
		for (String apply : APPLY_TO) {
			o.Choose_apply_to(apply);
			boolean locked = o.is_locked(o.create_subject());
			test.info(apply + ": subject box locked " + locked + " | placeholder '" + o.create_subject().getAttribute("placeholder") + "'");
			if (!apply.equals("Bill")) {
				Assert.assertFalse(locked, "DEFECT: the subject box stays locked for " + apply);
			}
		}
		test.pass("Subject box follows Apply To");
	}

	// DP23: the subject box suggests items / categories / sub categories; text that matches nothing gives nothing
	@Test
	public void DP23_subject_suggestions() {
		test = extent.createTest("DP23 Subject suggestions");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		open_create(o);
		o.Choose_apply_to("Item");
		List<String> found = o.Suggestions_for(o.create_subject(), "leo");
		test.info("Item 'leo': " + found);
		Assert.assertFalse(found.isEmpty(), "No item is suggested for 'leo'");
		List<String> none = o.Suggestions_for(o.create_subject(), "ZZQNoSuch" + unique());
		Assert.assertTrue(none.isEmpty(), "An item is suggested for text that matches nothing: " + none);
		o.Choose_apply_to("Category");
		test.info("Category 'leo': " + o.Suggestions_for(o.create_subject(), "leo"));
		o.Choose_apply_to("SubCategory");
		test.info("SubCategory 'ad': " + o.Suggestions_for(o.create_subject(), "ad"));
		test.pass("Subject suggestions work");
	}

	// DP24: Add New with missing data adds nothing and gives a message
	@Test
	public void DP24_add_new_validation() {
		test = extent.createTest("DP24 Add New validation");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		open_create(o);
		List<String> bad = new ArrayList<String>();
		o.Click_add_new();
		String m0 = o.All_messages();
		test.info("Nothing filled: rows " + o.grid_count() + " | " + m0);
		if (o.grid_count() > 0) {
			bad.add("an empty pattern was added");
		}
		if (m0.isEmpty()) {
			bad.add("Add New with an empty form gives no message");
		}
		o.Choose_apply_to("Item");
		o.Click_add_new();
		test.info("Apply To only: rows " + o.grid_count() + " | " + o.All_messages());
		if (o.grid_count() > 0) {
			bad.add("a pattern with only Apply To was added");
		}
		o.Pick_subject(ITEM_1);
		o.Click_add_new();
		test.info("Apply To + item: rows " + o.grid_count() + " | " + o.All_messages());
		if (o.grid_count() > 0) {
			bad.add("a pattern with no name and no value was added");
		}
		o.Set_name(zq_name());
		o.Click_add_new();
		test.info("+ name: rows " + o.grid_count() + " | " + o.All_messages());
		if (o.grid_count() > 0) {
			bad.add("a pattern with no value was added");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Incomplete patterns are refused");
	}

	// DP25: invalid discount values are refused: percentage 0, negative, above 100, letters, 3 decimals; amount 0, negative, letters
	@Test
	public void DP25_invalid_values() {
		test = extent.createTest("DP25 Invalid discount values");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		open_create(o);
		o.Choose_apply_to("Item");
		o.Pick_subject(ITEM_1);
		List<String> bad = new ArrayList<String>();
		String[][] cases = { { "Percentage", "0" }, { "Percentage", "-5" }, { "Percentage", "101" }, { "Percentage", "100.01" },
				{ "Percentage", "abc" }, { "Percentage", "12.345" }, { "Amount", "0" }, { "Amount", "-5" }, { "Amount", "abc" } };
		for (String[] c : cases) {
			o.Choose_type(c[0]);
			o.Set_name("ZQ bad " + unique());
			o.Set_value(c[1]);
			int before = o.grid_count();
			o.Click_add_new();
			String message = o.All_messages();
			int after = o.grid_count();
			test.info(c[0] + " '" + c[1] + "': rows " + before + " -> " + after + " | value box '" + o.create_value().getAttribute("value") + "' | " + message);
			if (after > before) {
				bad.add(c[0] + " " + c[1] + " was added (" + o.grid_texts().get(after - 1) + ")");
				o.Remove_grid_row(after - 1);
				o.All_messages();
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: invalid discount values were accepted: " + bad);
		test.pass("Invalid values are refused");
	}

	// DP26: valid patterns are added to the grid with all their data; a row can be removed
	@Test
	public void DP26_add_and_remove_grid_rows() {
		test = extent.createTest("DP26 Add and remove grid rows");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		open_create(o);
		String name1 = zq_name();
		String code1 = "ZQC" + unique();
		o.Add_pattern("Item", ITEM_1, code1, name1, "Percentage", "10");
		test.info("Grid: " + o.grid_texts() + " | " + o.All_messages());
		Assert.assertEquals(o.grid_count(), 1, "The pattern was not added to the grid");
		List<String> g = o.grid_texts().get(0);
		Assert.assertEquals(g.get(0), "Item", "Classification");
		Assert.assertTrue(g.get(1).toLowerCase().contains(ITEM_1.toLowerCase()), "Item in the grid: " + g.get(1));
		Assert.assertEquals(g.get(2), code1, "Code in the grid");
		Assert.assertEquals(g.get(3), name1, "Name in the grid");
		Assert.assertEquals(g.get(4), "Percentage", "Type in the grid");
		Assert.assertEquals(U_DiscountPattern_Cls.to_number(g.get(5)), 10.0, 0.0001, "Value in the grid");
		test.info("After Add New: apply to '" + o.selected(o.create_apply_to()) + "' | subject '" + o.subject_text() + "' | name '"
				+ o.create_name().getAttribute("value") + "' | value '" + o.create_value().getAttribute("value") + "' (the form is cleared or kept)");
		o.Add_pattern("Item", ITEM_2, "", zq_name(), "Amount", "5");
		Assert.assertEquals(o.grid_count(), 2, "The second pattern was not added");
		Assert.assertEquals(o.grid_texts().get(1).get(4), "Amount", "Type of the second pattern");
		test.info("Second row: " + o.grid_texts().get(1));
		o.Remove_grid_row(0);
		o.All_messages();
		Assert.assertEquals(o.grid_count(), 1, "The row was not removed");
		Assert.assertEquals(o.grid_texts().get(0).get(4), "Amount", "The wrong row was removed");
		test.pass("Grid rows are added and removed");
	}

	// DP27: the same code twice in the grid is refused
	@Test
	public void DP27_duplicate_code_in_grid() {
		test = extent.createTest("DP27 Duplicate code in the grid");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		open_create(o);
		String code = "ZQC" + unique();
		o.Add_pattern("Item", ITEM_1, code, zq_name(), "Percentage", "5");
		o.Add_pattern("Item", ITEM_2, code, zq_name(), "Percentage", "6");
		String message = o.All_messages();
		test.info("Rows " + o.grid_count() + " | " + message);
		Assert.assertEquals(o.grid_count(), 1, "DEFECT: the same discount code was added twice to the grid");
		test.pass("A duplicate code is refused");
	}

	// DP28: the same name for the same item twice in the grid (the result is written to the report)
	@Test
	public void DP28_duplicate_name_in_grid() {
		test = extent.createTest("DP28 Duplicate name in the grid");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		open_create(o);
		String name = zq_name();
		o.Add_pattern("Item", ITEM_1, "", name, "Percentage", "5");
		o.Add_pattern("Item", ITEM_1, "", name, "Percentage", "7");
		test.info("Rows " + o.grid_count() + " | " + o.All_messages());
		if (o.grid_count() == 2) {
			test.warning("OBSERVATION: the same name with the same item is accepted twice (different value): " + o.grid_texts());
		}
		Assert.assertTrue(o.is_create_page(), "The page broke");
		test.pass("Duplicate name handled");
	}

	// DP29: Back returns to the list and nothing is created
	@Test
	public void DP29_back_creates_nothing() throws InterruptedException {
		test = extent.createTest("DP29 Back creates nothing");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		int before = o.entries_total();
		open_create(o);
		String name = zq_name();
		o.Add_pattern("Item", ITEM_1, "", name, "Percentage", "5");
		Assert.assertEquals(o.grid_count(), 1);
		o.Click_back();
		test.info("After Back: " + o.All_messages() + " | list page " + o.is_list_page());
		back_to_list();
		Assert.assertEquals(o.entries_total(), before, "The number of groups changed");
		o.Search_patterns("", name, "", "");
		Assert.assertEquals(o.result_count(), 0, "DEFECT: the pattern was saved although Back was used");
		test.pass("Back creates nothing");
	}

	// DP30: Submit with an empty grid saves nothing and gives a message
	@Test
	public void DP30_submit_empty_grid() {
		test = extent.createTest("DP30 Submit with an empty grid");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		open_create(o);
		o.Click_submit();
		String message = o.All_messages();
		test.info("Submit with an empty grid: " + message + " | still on the create page: " + o.is_create_page());
		Assert.assertTrue(o.is_create_page(), "DEFECT: Submit with an empty grid left the create page");
		Assert.assertFalse(message.isEmpty(), "DEFECT: Submit with an empty grid gives no message");
		test.pass("An empty grid is refused");
	}

	// DP31: odd discount codes and names in the grid (spaces only, special characters, very long): written to the report;
	// a name of spaces only must not be accepted
	@Test
	public void DP31_odd_codes_and_names() {
		test = extent.createTest("DP31 Odd codes and names");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		open_create(o);
		o.Choose_apply_to("Item");
		o.Pick_subject(ITEM_1);
		StringBuilder longText = new StringBuilder();
		for (int i = 0; i < 150; i++) {
			longText.append('x');
		}
		List<String> bad = new ArrayList<String>();
		String[][] cases = { { "ZQ spaces code", "   ", "ZQ n " + unique() }, { "ZQ#$%&*", "", "ZQ special " + unique() },
				{ longText.toString(), "", "ZQ long code " + unique() }, { "", "", "   " }, { "", "", longText.toString() } };
		for (String[] c : cases) {
			o.Choose_type("Percentage");
			o.Set_code(c[0]);
			o.Set_name(c[2]);
			o.Set_value("1");
			int before = o.grid_count();
			o.Click_add_new();
			String message = o.All_messages();
			int after = o.grid_count();
			String shown = after > before ? o.grid_texts().get(after - 1).toString() : "-";
			test.info("Code '" + (c[0].length() > 20 ? c[0].substring(0, 20) + "..(" + c[0].length() + ")" : c[0]) + "' name '"
					+ (c[2].length() > 20 ? c[2].substring(0, 20) + "..(" + c[2].length() + ")" : c[2]) + "': rows " + before + " -> " + after
					+ " | " + message + " | row " + (shown.length() > 120 ? shown.substring(0, 120) + "..." : shown));
			if (after > before) {
				if (c[2].trim().isEmpty()) {
					bad.add("a name of spaces only was accepted");
				}
				o.Remove_grid_row(after - 1);
				o.All_messages();
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Odd values handled");
	}

	// ======================= creating and deleting patterns (ALLOW_CREATE) =======================

	// DP32: create a Percentage pattern with an auto code: it is listed, its view shows the pattern; then it is deleted
	@Test
	public void DP32_create_percentage_pattern() throws InterruptedException {
		test = extent.createTest("DP32 Create a Percentage pattern");
		require_create();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		String name = zq_name();
		open_create(o);
		o.Add_pattern("Item", ITEM_1, "", name, "Percentage", "12.5");
		Assert.assertEquals(o.grid_count(), 1, "The pattern was not added to the grid");
		Res r = submit(o);
		test.info("Submit: " + r.message);
		Assert.assertTrue(r.ok, "The pattern was not saved: " + r.message);
		back_to_list();
		List<String> p = pattern_named(o, name, "Item", ITEM_1);
		Assert.assertNotNull(p, "DEFECT: the saved pattern " + name + " is not listed");
		test.info("Saved pattern: " + p);
		Assert.assertFalse(p.get(0).isEmpty(), "The auto generated code is empty");
		Assert.assertEquals(p.get(2), "Item", "Apply to");
		Assert.assertEquals(p.get(4), "Percentage", "Type");
		Assert.assertEquals(U_DiscountPattern_Cls.to_number(p.get(5)), 12.5, 0.0001, "DEFECT: the saved value differs");
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test pattern: " + left);
		o.Search_patterns("", name, "", "");
		Assert.assertEquals(o.result_count(), 0, "DEFECT: the pattern is still listed after it was deleted");
		test.pass("Pattern created and deleted: " + p);
	}

	// DP33: create a pattern with a typed code: the code is saved exactly as typed
	@Test
	public void DP33_create_pattern_with_typed_code() throws InterruptedException {
		test = extent.createTest("DP33 Create a pattern with a typed code");
		require_create();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		String name = zq_name();
		String code = "ZQC" + unique();
		open_create(o);
		o.Add_pattern("Item", ITEM_2, code, name, "Percentage", "7");
		Res r = submit(o);
		Assert.assertTrue(r.ok, "The pattern was not saved: " + r.message);
		back_to_list();
		List<String> p = null;
		o.Search_patterns(code, "", "", "");
		for (int i = 0; i < o.result_count(); i++) {
			for (List<String> line : patterns_of_row(o, i)) {
				if (line.get(1).equalsIgnoreCase(name)) {
					p = line;
				}
			}
		}
		test.info("Saved pattern: " + p);
		Assert.assertNotNull(p, "DEFECT: the pattern with the code " + code + " is not found");
		Assert.assertEquals(p.get(0), code, "DEFECT: the code was not saved as typed");
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test pattern: " + left);
		test.pass("Typed code saved");
	}

	// DP34: create an Amount pattern: type and value are saved
	@Test
	public void DP34_create_amount_pattern() throws InterruptedException {
		test = extent.createTest("DP34 Create an Amount pattern");
		require_create();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		String name = zq_name();
		open_create(o);
		o.Add_pattern("Item", ITEM_1, "", name, "Amount", "25");
		Res r = submit(o);
		Assert.assertTrue(r.ok, "The pattern was not saved: " + r.message);
		back_to_list();
		o.Search_patterns("", name, "", "");
		Assert.assertTrue(o.result_count() > 0, "DEFECT: the saved pattern is not listed");
		List<String> p = null;
		for (List<String> line : patterns_of_row(o, 0)) {
			if (line.get(1).equalsIgnoreCase(name)) {
				p = line;
			}
		}
		test.info("Saved pattern: " + p);
		Assert.assertNotNull(p, "The pattern is not in its group");
		Assert.assertEquals(p.get(4), "Amount", "Type");
		Assert.assertEquals(U_DiscountPattern_Cls.to_number(p.get(5)), 25.0, 0.0001, "Value");
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test pattern: " + left);
		test.pass("Amount pattern saved");
	}

	// DP35: three patterns for one item in one Submit: the group lists 3 more patterns; then they are deleted
	@Test
	public void DP35_create_three_patterns_for_one_item() throws InterruptedException {
		test = extent.createTest("DP35 Create three patterns for one item");
		require_create();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		o.Search_patterns("", "", "Item", ITEM_1);
		int existing = 0;
		int row = o.row_index("Item", ITEM_1);
		if (row >= 0) {
			existing = Integer.parseInt(o.result_texts().get(row).get(2));
		}
		test.info("Patterns of " + ITEM_1 + " before: " + existing);
		String tag = unique();
		open_create(o);
		o.Add_pattern("Item", ITEM_1, "", "ZQ three A " + tag, "Percentage", "5");
		o.Add_pattern("Item", ITEM_1, "", "ZQ three B " + tag, "Percentage", "10");
		o.Add_pattern("Item", ITEM_1, "", "ZQ three C " + tag, "Amount", "3");
		Assert.assertEquals(o.grid_count(), 3, "The 3 patterns were not added to the grid");
		Res r = submit(o);
		Assert.assertTrue(r.ok, "The patterns were not saved: " + r.message);
		back_to_list();
		o.Search_patterns("", "ZQ three", "Item", ITEM_1);
		row = o.row_index("Item", ITEM_1);
		Assert.assertTrue(row >= 0, "DEFECT: the group " + ITEM_1 + " is not listed");
		int now = Integer.parseInt(o.result_texts().get(row).get(2));
		test.info("Number of patterns of " + ITEM_1 + " now: " + now);
		Assert.assertEquals(now, existing + 3, "DEFECT: the number of patterns did not grow by 3");
		o.Click_view(row);
		int found = 0;
		for (List<String> line : o.modal_rows()) {
			if (line.get(1).contains("ZQ three") && line.get(1).endsWith(tag.substring(0))) {
				found++;
			}
		}
		o.Close_modal();
		Assert.assertEquals(found, 3, "The group does not list the 3 saved patterns");
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test patterns: " + left);
		o.Search_patterns("", "", "Item", ITEM_1);
		int after = o.row_index("Item", ITEM_1) < 0 ? 0 : Integer.parseInt(o.result_texts().get(o.row_index("Item", ITEM_1)).get(2));
		Assert.assertEquals(after, existing, "DEFECT: the number of patterns is not back to " + existing + " after deleting");
		test.pass("Three patterns created and deleted");
	}

	// DP36: a pattern for every Apply To (Category, SubCategory, Item, Menu Card, Bill): each is listed under its classification
	@Test
	public void DP36_create_for_every_apply_to() throws InterruptedException {
		test = extent.createTest("DP36 Create a pattern for every Apply To");
		require_create();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		String[][] subjects = { { "Category", CATEGORY_1 }, { "SubCategory", SUBCATEGORY_1 }, { "Item", ITEM_1 },
				{ "Menu Card", "a" }, { "Bill", "" } };
		List<String> problems = new ArrayList<String>();
		for (String[] s : subjects) {
			String name = "ZQ " + s[0] + " " + unique();
			back_to_list();
			open_create(o);
			o.Choose_apply_to(s[0]);
			if (!s[1].isEmpty() && !o.is_locked(o.create_subject())) {
				o.Pick_subject(s[1]);
			}
			String subject = o.subject_text();
			o.Choose_type("Percentage");
			o.Set_name(name);
			o.Set_value("2");
			o.Click_add_new();
			String addMessage = o.All_messages();
			if (o.grid_count() != 1) {
				problems.add(s[0] + ": not added to the grid (" + addMessage + ")");
				continue;
			}
			Res r = submit(o);
			test.info(s[0] + " (subject '" + subject + "'): " + r.message);
			if (!r.ok) {
				problems.add(s[0] + ": not saved (" + r.message + ")");
				continue;
			}
			back_to_list();
			o.Search_patterns("", name, "", "");
			boolean listed = false;
			for (List<String> row : o.result_texts()) {
				if (row.get(0).equalsIgnoreCase(s[0])) {
					listed = true;
				}
			}
			test.info(s[0] + ": listed under its classification = " + listed + " | rows " + o.result_texts());
			if (!listed) {
				problems.add(s[0] + ": the saved pattern is not listed under " + s[0]);
			}
		}
		List<String> left = sweep();
		if (!left.isEmpty()) {
			problems.add("not deleted: " + left);
		}
		Assert.assertTrue(problems.isEmpty(), "DEFECT: " + problems);
		test.pass("A pattern was created and deleted for every Apply To");
	}

	// DP37: the same code saved twice (two Submits) must be refused the second time
	@Test
	public void DP37_duplicate_code_on_server() throws InterruptedException {
		test = extent.createTest("DP37 Duplicate code on the server");
		require_create();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		String code = "ZQC" + unique();
		open_create(o);
		o.Add_pattern("Item", ITEM_1, code, zq_name(), "Percentage", "4");
		Res first = submit(o);
		Assert.assertTrue(first.ok, "The first pattern was not saved: " + first.message);
		back_to_list();
		open_create(o);
		o.Add_pattern("Item", ITEM_2, code, zq_name(), "Percentage", "9");
		Res second = o.grid_count() == 1 ? submit(o) : null;
		test.info("Second save with the same code: " + (second == null ? "refused when added to the grid: " + o.All_messages() : second.message));
		boolean accepted = second != null && second.ok && !o.is_create_page();
		back_to_list();
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test patterns: " + left);
		Assert.assertFalse(accepted, "DEFECT: the same discount code was saved twice (" + (second == null ? "" : second.message) + ")");
		test.pass("A duplicate code is refused");
	}

	// DP38: the same name for the same item saved twice (the result is written to the report; both are deleted)
	@Test
	public void DP38_duplicate_name_on_server() throws InterruptedException {
		test = extent.createTest("DP38 Duplicate name on the server");
		require_create();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		String name = zq_name();
		for (int i = 0; i < 2; i++) {
			open_create(o);
			o.Add_pattern("Item", ITEM_1, "", name, "Percentage", String.valueOf(3 + i));
			Res r = o.grid_count() == 1 ? submit(o) : null;
			test.info("Save " + (i + 1) + ": " + (r == null ? "not added: " + o.All_messages() : r.message));
			back_to_list();
		}
		o.Search_patterns("", name, "Item", ITEM_1);
		int count = 0;
		int row = o.row_index("Item", ITEM_1);
		if (row >= 0) {
			for (List<String> line : patterns_of_row(o, row)) {
				if (line.get(1).equalsIgnoreCase(name)) {
					count++;
				}
			}
		}
		test.info("Patterns with the name " + name + ": " + count);
		if (count > 1) {
			test.warning("OBSERVATION: the same name was saved twice for the same item");
		}
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test patterns: " + left);
		test.pass("Duplicate name handled");
	}

	// DP39: boundary values are saved as typed: percentage 100 and 0.01, amount 0.01 and 99999.99
	@Test
	public void DP39_boundary_values_saved() throws InterruptedException {
		test = extent.createTest("DP39 Boundary values");
		require_create();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		String tag = unique();
		String[][] cases = { { "Percentage", "100" }, { "Percentage", "0.01" }, { "Amount", "0.01" }, { "Amount", "99999.99" } };
		open_create(o);
		for (int i = 0; i < cases.length; i++) {
			o.Add_pattern("Item", ITEM_2, "", "ZQ bound " + i + " " + tag, cases[i][0], cases[i][1]);
		}
		test.info("Grid: " + o.grid_texts());
		List<String> problems = new ArrayList<String>();
		if (o.grid_count() != cases.length) {
			problems.add("only " + o.grid_count() + " of " + cases.length + " boundary values were added to the grid: " + o.All_messages());
		}
		if (o.grid_count() > 0) {
			Res r = submit(o);
			test.info("Submit: " + r.message);
			if (!r.ok) {
				problems.add("not saved: " + r.message);
			} else {
				back_to_list();
				o.Search_patterns("", "ZQ bound", "Item", ITEM_2);
				int row = o.row_index("Item", ITEM_2);
				if (row < 0) {
					problems.add("the group is not listed");
				} else {
					for (List<String> line : patterns_of_row(o, row)) {
						if (line.get(1).startsWith("ZQ bound")) {
							int i = Integer.parseInt(line.get(1).split(" ")[2]);
							test.info("Saved " + line);
							if (!line.get(4).equals(cases[i][0]) || Math.abs(U_DiscountPattern_Cls.to_number(line.get(5)) - Double.parseDouble(cases[i][1])) > 0.0001) {
								problems.add("typed " + cases[i][0] + " " + cases[i][1] + " but saved " + line.get(4) + " " + line.get(5));
							}
						}
					}
				}
			}
		}
		List<String> left = sweep();
		if (!left.isEmpty()) {
			problems.add("not deleted: " + left);
		}
		Assert.assertTrue(problems.isEmpty(), "DEFECT: " + problems);
		test.pass("Boundary values are saved as typed");
	}

	// DP40: delete lines in the edit pop-up: the number of the group drops; the last one removes the group from the list
	@Test
	public void DP40_delete_in_edit_pop_up() throws InterruptedException {
		test = extent.createTest("DP40 Delete in the edit pop-up");
		require_create();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		String tag = unique();
		open_create(o);
		o.Add_pattern("Item", ITEM_2, "", "ZQ del A " + tag, "Percentage", "5");
		o.Add_pattern("Item", ITEM_2, "", "ZQ del B " + tag, "Percentage", "6");
		Res r = submit(o);
		Assert.assertTrue(r.ok, "The patterns were not saved: " + r.message);
		back_to_list();
		o.Search_patterns("", "ZQ del", "Item", ITEM_2);
		int row = o.row_index("Item", ITEM_2);
		Assert.assertTrue(row >= 0, "The group is not listed");
		int number = Integer.parseInt(o.result_texts().get(row).get(2));
		o.Click_edit(row);
		o.Modal_set_page_length_all();
		String codeA = null;
		for (List<String> line : o.modal_rows()) {
			if (line.get(1).equals("ZQ del A " + tag)) {
				codeA = line.get(0);
			}
		}
		Assert.assertNotNull(codeA, "The pattern A is not in the pop-up: " + o.modal_rows());
		o.Click_modal_delete(codeA);
		List<String> messages = confirm_delete(o);
		test.info("Deleted A (" + codeA + "): " + messages + " | pop-up open " + o.modal_open());
		if (o.modal_open()) {
			o.Close_modal();
		}
		o.Search_patterns("", "ZQ del", "Item", ITEM_2);
		row = o.row_index("Item", ITEM_2);
		Assert.assertTrue(row >= 0, "The group disappeared after deleting one of two ZQ patterns");
		int after = Integer.parseInt(o.result_texts().get(row).get(2));
		test.info("Number of patterns: " + number + " -> " + after);
		Assert.assertEquals(after, number - 1, "DEFECT: the number of patterns did not drop by one");
		List<String> remaining = new ArrayList<String>();
		for (List<String> line : patterns_of_row(o, row)) {
			remaining.add(line.get(1));
		}
		Assert.assertFalse(remaining.contains("ZQ del A " + tag), "DEFECT: the deleted pattern is still in the group");
		Assert.assertTrue(remaining.contains("ZQ del B " + tag), "The other pattern was deleted too");
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test pattern: " + left);
		test.pass("Deleting in the edit pop-up works");
	}

	// DP41: a saved pattern keeps its data after the page is reloaded (a fresh login of the session)
	@Test
	public void DP41_saved_pattern_survives_reload() throws InterruptedException {
		test = extent.createTest("DP41 Saved pattern survives a reload");
		require_create();
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		String name = zq_name();
		open_create(o);
		o.Add_pattern("Item", ITEM_1, "", name, "Percentage", "8");
		Res r = submit(o);
		Assert.assertTrue(r.ok, "The pattern was not saved: " + r.message);
		login_again();
		go_to_page();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		o.Search_patterns("", name, "", "");
		Assert.assertTrue(o.result_count() > 0, "DEFECT: the pattern is gone after a fresh login");
		List<String> left = sweep();
		Assert.assertTrue(left.isEmpty(), "Could not delete the test pattern: " + left);
		test.pass("The pattern survives a fresh login");
	}

	// DP42: the sweep: no pattern whose name starts with ZQ is left (the last test)
	@Test(priority = 100)
	public void DP42_no_test_pattern_left() throws InterruptedException {
		test = extent.createTest("DP42 No test pattern is left");
		U_DiscountPattern_Cls o = new U_DiscountPattern_Cls(driver);

		List<String> left = sweep();
		test.info("Sweep result: " + (left.isEmpty() ? "nothing left over" : left));
		o.Search_patterns("", "ZQ", "", "");
		test.info("Search for ZQ: " + o.entries_text() + " | rows " + o.result_count());
		Assert.assertTrue(left.isEmpty(), "Test patterns could not be deleted: " + left);
		Assert.assertEquals(o.result_count(), 0, "Patterns with ZQ in their name are still listed: " + o.result_texts());
		test.pass("All test patterns are deleted");
	}
}
