package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.K_Customer_Cls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.TimeoutException;

/*
 * Customer (Customer Details > Customer) - functional tests, C01 .. C29.
 * Existing QA customer used for reading only: BD31552CB35C / MAHADEVA ELECTRICALS-PALLICHAL (never changed).
 * Customers created by the tests get the code prefix ZQC. Customers cannot be deleted, so C29 deactivates
 * them (Is Active unticked).
 * Mandatory fields: customer code, name, GSTIN, store name, mobile, route, customer group, customer class,
 * payment mode, address, city, zip code, country, state.
 */
public class K_Customer_Test extends BaseCls {

	static final String EXISTING_CODE = "BD31552CB35C";
	static final String EXISTING_NAME = "MAHADEVA ELECTRICALS-PALLICHAL";

	// customers created by the tests (code prefix ZQC)
	String code1;
	String name1;
	String gstin1;
	List<String> created = new ArrayList<String>();

	// what the last Submit showed
	String last_alert = "";
	String last_notice = "";

	private String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
	}

	// a GSTIN in the right format: 32 + PAN-like text + 1Z + one more character
	private String gstin() {
		return "32ZQAAA" + String.format("%04d", (System.currentTimeMillis() / 100) % 10000) + "Z1Z5";
	}

	// a 10 digit mobile number
	private String mobile() {
		return "98" + String.format("%08d", (System.currentTimeMillis() / 10) % 100000000L);
	}

	// runs ONCE: fresh session, login through the form
	@BeforeClass
	public void login_first() {
		login_again();
		code1 = "ZQC" + unique();
		name1 = "ZQ Cust " + unique();
		gstin1 = gstin();
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

	// opens Customer Details > Customer from the dashboard (waits and tries again while the page is still loading)
	private void go_to_customer() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_by_name("Customer Details", "Customer");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Customer Details", "Customer");
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

	// every test starts with a fresh Customer list screen
	@BeforeMethod
	public void open_customer() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		K_Customer_Cls c = new K_Customer_Cls(driver);
		try {
			go_to_customer();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_customer();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
		}
		remember_clean_screen();
	}

	// presses Submit and waits for: back on the list, or a message, or a pop-up / alert.
	// The alert / pop-up text is kept in last_alert / last_notice and then closed.
	private void submit_and_wait(K_Customer_Cls c) {
		last_alert = "";
		last_notice = "";
		c.Click_submit();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> {
				String a = c.Accept_alert();
				if (!a.isEmpty()) {
					last_alert = a;
					return true;
				}
				return c.is_list_page() || !c.validation_messages().isEmpty() || !c.notice().isEmpty();
			});
		} catch (Exception e) {
			// nothing appeared: the test checks the screen itself
		}
		last_notice = c.notice();
		c.Close_notice();
		WaitUtil.settle(driver, 800);
		String late = c.Accept_alert();
		if (!late.isEmpty()) {
			last_alert = last_alert + " " + late;
		}
	}

	// everything the site said after the last Submit
	private String said(K_Customer_Cls c) {
		return c.validation_messages() + " | notice: " + last_notice + " | alert: " + last_alert;
	}

	// true when one of the messages contains the key (capital letters ignored)
	private boolean has(List<String> messages, String key) {
		for (String s : messages) {
			if (s.toLowerCase().contains(key.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	// a complete valid customer (every mandatory field filled)
	private Map<String, String> valid(String code, String name) {
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("code", code);
		v.put("name", name);
		v.put("gstin", gstin());
		v.put("store", name);
		v.put("mobile", mobile());
		v.put("route", "Tvm South");
		v.put("group", "Hotel");
		v.put("class", "A+");
		v.put("payment", "Cash");
		v.put("address", "ZQ Test Address, Thiruvananthapuram");
		v.put("city", "Thiruvananthapuram");
		v.put("zip", "695001");
		v.put("country", "India");
		v.put("state", "Kerala");
		return v;
	}

	private void open_create(K_Customer_Cls c) {
		c.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_form_screen());
	}

	// opens the create form, fills it and submits it
	private void create_customer(K_Customer_Cls c, Map<String, String> values) {
		open_create(c);
		c.Fill(values);
		submit_and_wait(c);
	}

	// finds a customer in the list with the "Search:" box above the table
	private boolean find_customer(K_Customer_Cls c, String code) {
		c.Filter_table(code);
		return c.Wait_for_row(code);
	}

	// ======================= LIST screen =======================

	// C01: the list shows Search, Create, table filter, Show entries, the 8 column headers, the entries line and
	// a view and an edit icon in a row
	@Test
	public void C01_list_page_elements() {
		test = extent.createTest("C01 Customer list elements");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		Assert.assertTrue(c.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(c.verify_create_btn().isDisplayed(), "Create button is missing");
		Assert.assertTrue(c.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		Assert.assertTrue(c.verify_entries_select().isDisplayed(), "The 'Show entries' box is missing");

		List<String> headers = c.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "CUSTOMER CODE", "CUSTOMER NAME", "BALANCE", "CREDIT", "ROUTE", "RATE PROFILE",
				"GSTIN NUMBER", "ACTION" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(c.verify_entries().getText().contains("Showing 1 to 10"), c.verify_entries().getText());
		Assert.assertEquals(c.result_count(), 10, "10 rows are expected on the first page");
		Assert.assertEquals(c.action_icon_count(c.result_texts().get(0).get(0)), 2, "A view and an edit icon are expected");
		test.pass("List screen elements are displayed");
	}

	// C02: the Search button opens the search form (the fields that it has are written to the report)
	@Test
	public void C02_search_form_opens() {
		test = extent.createTest("C02 Search form opens");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		c.Open_search_form();
		test.info("Search form boxes: " + c.search_placeholders());
		Assert.assertTrue(c.search_form_open(), "The search form with a code box did not open");
		Assert.assertTrue(c.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		test.pass("Search form is displayed");
	}

	// C03: search by customer code finds that customer
	@Test
	public void C03_search_by_code() {
		test = extent.createTest("C03 Search by customer code");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		c.Search_customer(EXISTING_CODE, "");
		Assert.assertTrue(c.Wait_for_row(EXISTING_CODE), "The customer " + EXISTING_CODE + " was not found");
		Assert.assertEquals(c.result_count(), 1, "Only one customer was expected");
		test.pass("Found " + EXISTING_CODE);
	}

	// C04: search by customer name finds that customer
	@Test
	public void C04_search_by_name() {
		test = extent.createTest("C04 Search by customer name");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		c.Search_customer("", EXISTING_NAME);
		Assert.assertTrue(c.Wait_for_row(EXISTING_CODE), "The customer " + EXISTING_NAME + " was not found");
		test.pass("Found by name");
	}

	// C05: a code that does not exist gives an empty table
	@Test
	public void C05_search_no_match() {
		test = extent.createTest("C05 Search without match");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		c.Search_customer("NOMATCH" + unique(), "");
		Assert.assertEquals(c.result_count(), 0, "No customer should be listed");
		test.pass("Empty result for an unknown code");
	}

	// C06: the "Search:" box above the table finds a customer by code
	@Test
	public void C06_table_filter() {
		test = extent.createTest("C06 Table filter");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		Assert.assertTrue(find_customer(c, EXISTING_CODE), "The filter did not find " + EXISTING_CODE);
		Assert.assertEquals(c.row_of(EXISTING_CODE).get(1), EXISTING_NAME);
		test.pass("Table filter works");
	}

	// C07: the table filter with unknown text gives an empty table
	@Test
	public void C07_table_filter_no_match() {
		test = extent.createTest("C07 Table filter without match");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		c.Filter_table("NOMATCH" + unique());
		Assert.assertEquals(c.result_count(), 0, "No customer should be listed");
		test.pass("Empty result for unknown text");
	}

	// C08: choosing another "Show N entries" option lists that many rows
	@Test
	public void C08_show_entries() {
		test = extent.createTest("C08 Show entries");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		List<String> options = c.entries_options();
		test.info("Show entries options: " + options);
		Assert.assertTrue(options.size() > 1, "The 'Show entries' box has only one option: " + options);
		String chosen = c.Select_entries_index(1);
		String digits = chosen.replaceAll("\\D", "");
		test.info("Chosen: " + chosen + " | rows: " + c.result_count() + " | " + c.verify_entries().getText());
		if (!digits.isEmpty()) {
			int expected = Integer.parseInt(digits);
			Assert.assertEquals(c.result_count(), expected, "Rows after choosing " + chosen);
			Assert.assertTrue(c.verify_entries().getText().contains("Showing 1 to " + expected), c.verify_entries().getText());
		}
		test.pass("Show entries works");
	}

	// C09: Next shows entries 11 to 20, Previous goes back to 1 to 10
	@Test
	public void C09_paging() {
		test = extent.createTest("C09 Paging");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		c.Click_next();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> c.verify_entries().getText().contains("Showing 11 to 20"));
		c.Click_previous();
		new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> c.verify_entries().getText().contains("Showing 1 to 10"));
		test.pass("Next and Previous work");
	}

	// C10: clicking the CUSTOMER NAME header sorts the page by name (ascending)
	@Test
	public void C10_sort_by_name() {
		test = extent.createTest("C10 Sort by customer name");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		c.Click_header("CUSTOMER NAME");
		List<String> names = new ArrayList<String>();
		for (List<String> row : c.result_texts()) {
			names.add(row.get(1));
		}
		test.info("Names after the sort: " + names);
		for (int i = 1; i < names.size(); i++) {
			Assert.assertTrue(names.get(i - 1).compareToIgnoreCase(names.get(i)) <= 0,
					"The names are not in order: " + names.get(i - 1) + " > " + names.get(i));
		}
		test.pass("Sorted by name");
	}

	// ======================= FORM screen: create =======================

	// C11: Create opens the form with all fields, the drop downs, tick boxes, photo, Back and Submit
	@Test
	public void C11_create_form_elements() {
		test = extent.createTest("C11 Create form elements");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		open_create(c);
		for (String key : new String[] { "code", "name", "gstin", "store", "aadhaar", "fssai", "mobile", "email",
				"balance", "credit", "cheque", "route", "walk", "latitude", "longitude", "address", "city", "zip" }) {
			Assert.assertTrue(c.Field(key).isDisplayed(), "Field is missing: " + key);
		}
		for (String key : new String[] { "group", "rate", "dealer", "class", "prospect", "country", "state", "payment",
				"menucard" }) {
			Assert.assertTrue(c.Select_of(key).isDisplayed(), "Drop down is missing: " + key);
		}
		for (String key : new String[] { "tax", "active", "distributor", "scheme", "gps" }) {
			Assert.assertNotNull(c.checkbox(key), "Tick box is missing: " + key);
		}
		Assert.assertTrue(c.verify_photo_upload().isDisplayed(), "Upload Customer Photo is missing");
		Assert.assertTrue(c.verify_prospect_link().isDisplayed(), "View Prospect Details is missing");
		Assert.assertTrue(c.verify_back().isDisplayed(), "Back is missing");
		Assert.assertTrue(c.verify_submit().isDisplayed(), "Submit is missing");
		test.pass("All fields of the create form are displayed");
	}

	// C12: defaults: balance 0, credit limit 0, tax applicable / active / scheme ticked, under distributor and
	// disable GPS unticked
	@Test
	public void C12_default_values() {
		test = extent.createTest("C12 Default values");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		open_create(c);
		Assert.assertEquals(c.Field("balance").getAttribute("value"), "0", "Customer Balance default");
		Assert.assertEquals(c.Field("credit").getAttribute("value"), "0", "Credit Limit default");
		Assert.assertTrue(c.checkbox("tax").isSelected(), "Is Additional Tax Applicable must be ticked");
		Assert.assertTrue(c.checkbox("active").isSelected(), "Is Active must be ticked");
		Assert.assertTrue(c.checkbox("scheme").isSelected(), "Is Scheme Applicable must be ticked");
		Assert.assertFalse(c.checkbox("distributor").isSelected(), "Is Under Distributor must be unticked");
		Assert.assertFalse(c.checkbox("gps").isSelected(), "Disable GPS Validation must be unticked");
		test.pass("Defaults are correct");
	}

	// C13: Disable GPS Validation enables From Date and To Date; unticking disables them again
	@Test
	public void C13_gps_validation_dates() {
		test = extent.createTest("C13 Disable GPS validation");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		open_create(c);
		Assert.assertTrue(c.is_locked(c.verify_gps_from()), "From Date must be disabled at the start");
		Assert.assertTrue(c.is_locked(c.verify_gps_to()), "To Date must be disabled at the start");
		c.Set_checkbox("gps", true);
		Assert.assertFalse(c.is_locked(c.verify_gps_from()), "From Date must be enabled after ticking");
		Assert.assertFalse(c.is_locked(c.verify_gps_to()), "To Date must be enabled after ticking");
		c.Set_checkbox("gps", false);
		Assert.assertTrue(c.is_locked(c.verify_gps_from()), "From Date must be disabled after unticking");
		test.pass("GPS validation dates follow the tick box");
	}

	// C14: Submit on an empty form shows the 14 mandatory messages and none for the optional fields
	@Test
	public void C14_empty_submit() {
		test = extent.createTest("C14 Empty submit");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		open_create(c);
		c.Click_submit();
		List<String> messages = c.validation_messages();
		test.info("Messages: " + messages);
		for (String key : new String[] { "customer code", "customer name", "gstin", "store name", "mobile number",
				"route", "customer group", "customer class", "payment mode", "address", "city", "zip code", "country",
				"state" }) {
			Assert.assertTrue(has(messages, key), "Message missing for: " + key + " | " + messages);
		}
		for (String key : new String[] { "aadhaar", "fssai", "email", "balance", "credit", "cheque", "latitude",
				"longitude", "rate profile", "dealer", "menu card" }) {
			Assert.assertFalse(has(messages, key), "An optional field is reported as mandatory: " + key);
		}
		Assert.assertTrue(c.is_form_screen(), "The form must stay open");
		test.pass("The 14 mandatory messages are shown");
	}

	// C15: every mandatory field on its own: the form is complete except that field, Submit must show its message
	@Test
	public void C15_each_mandatory_field() throws InterruptedException {
		test = extent.createTest("C15 Each mandatory field");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		String[][] fields = { { "code", "customer code" }, { "name", "customer name" }, { "gstin", "gstin" },
				{ "store", "store name" }, { "mobile", "mobile number" }, { "route", "route" },
				{ "group", "customer group" }, { "class", "customer class" }, { "payment", "payment mode" },
				{ "address", "address" }, { "city", "city" }, { "zip", "zip code" }, { "country", "country" },
				{ "state", "state" } };
		List<String> failed = new ArrayList<String>();
		for (String[] f : fields) {
			if (!c.is_list_page()) {
				go_to_customer();
				new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
			}
			Map<String, String> v = valid("ZQC" + unique(), "ZQ Missing " + unique());
			v.put(f[0], "");
			if (f[0].equals("country")) {
				v.put("state", "");
			}
			open_create(c);
			c.Fill(v);
			c.Click_submit();
			List<String> messages = c.validation_messages();
			test.info("Without " + f[0] + ": " + messages);
			if (!has(messages, f[1]) || !c.is_form_screen()) {
				failed.add(f[0]);
			}
			go_to_customer();
		}
		Assert.assertTrue(failed.isEmpty(), "No message (or the form was saved) when these fields are empty: " + failed);
		test.pass("Every mandatory field is checked");
	}

	// C16: invalid values must be refused. If the site accepts one, the customer is saved (a defect) and is
	// deactivated at the end.
	@Test
	public void C16_invalid_values_refused() throws InterruptedException {
		test = extent.createTest("C16 Invalid values");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		String[][] probes = { { "gstin", "ABC123", "GSTIN too short" }, { "mobile", "98ABC12345", "mobile with letters" },
				{ "mobile", "98765", "mobile with 5 digits" }, { "zip", "ABCDEF", "zip code with letters" },
				{ "zip", "123", "zip code with 3 digits" }, { "email", "not-an-email", "email without @" },
				{ "balance", "-100", "negative balance" }, { "credit", "-100", "negative credit limit" },
				{ "latitude", "abc", "latitude with letters" }, { "longitude", "abc", "longitude with letters" },
				{ "aadhaar", "ABC", "aadhaar with letters" }, { "route", "NOROUTE" + "XYZ", "route that does not exist" },
				{ "name", "   ", "customer name of spaces" } };
		List<String> accepted = new ArrayList<String>();
		for (String[] p : probes) {
			if (!c.is_list_page()) {
				go_to_customer();
				new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
			}
			String code = "ZQC" + unique();
			Map<String, String> v = valid(code, "ZQ Probe " + unique());
			v.put(p[0], p[1]);
			if (p[0].equals("name")) {
				v.put("store", "ZQ Probe Store");
			}
			create_customer(c, v);
			boolean saved = c.is_list_page();
			test.info(p[2] + ": " + (saved ? "ACCEPTED" : "refused") + " | " + said(c));
			if (saved) {
				accepted.add(p[2]);
				created.add(code);
			}
			go_to_customer();
		}
		Assert.assertTrue(accepted.isEmpty(), "DEFECT: the site accepted: " + accepted);
		test.pass("All invalid values are refused");
	}

	// ======================= create, view, edit =======================

	// C17: a valid customer is created and listed with its name, route, GSTIN, balance 0 and credit 0
	@Test
	public void C17_create_customer() {
		test = extent.createTest("C17 Create customer");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		Map<String, String> v = valid(code1, name1);
		v.put("gstin", gstin1);
		test.info("Creating " + code1 + " / " + name1 + " / " + gstin1);
		create_customer(c, v);
		test.info("Site said: " + said(c));
		Assert.assertTrue(c.is_list_page(), "Not back on the list. " + said(c));
		created.add(code1);

		Assert.assertTrue(find_customer(c, code1), "The new customer is not in the list");
		List<String> row = c.row_of(code1);
		test.info("Row: " + row);
		Assert.assertEquals(row.get(1), name1, "Customer name");
		Assert.assertEquals(row.get(2), "0", "Balance");
		Assert.assertEquals(row.get(3), "0", "Credit");
		Assert.assertTrue(row.get(4).toLowerCase().contains("tvm south"), "Route: " + row.get(4));
		Assert.assertEquals(row.get(6), gstin1, "GSTIN");
		test.pass("Customer created and listed");
	}

	// C18: the same customer code again must be refused
	@Test(dependsOnMethods = "C17_create_customer")
	public void C18_duplicate_code() {
		test = extent.createTest("C18 Duplicate customer code");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		create_customer(c, valid(code1, "ZQ Dup " + unique()));
		test.info("Site said: " + said(c));
		boolean refused = !c.is_list_page() || !last_notice.isEmpty() || !last_alert.isEmpty();
		if (c.is_list_page()) {
			find_customer(c, code1);
			Assert.assertEquals(c.result_count(), 1, "DEFECT: the duplicate code " + code1 + " was saved again");
		}
		Assert.assertTrue(refused, "DEFECT: the duplicate customer code was accepted silently");
		test.pass("Duplicate refused: " + said(c));
	}

	// C19: observation - the same GSTIN for a second customer: the result is only reported
	@Test(dependsOnMethods = "C17_create_customer")
	public void C19_duplicate_gstin() {
		test = extent.createTest("C19 Duplicate GSTIN");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		String code = "ZQC" + unique();
		Map<String, String> v = valid(code, "ZQ Gstin " + unique());
		v.put("gstin", gstin1);
		create_customer(c, v);
		if (c.is_list_page()) {
			created.add(code);
			test.warning("A second customer with the same GSTIN was ACCEPTED. Confirm that this is intended.");
		} else {
			test.info("The same GSTIN was refused: " + said(c));
		}
		test.pass("Observation recorded");
	}

	// C20: the view screen of the existing customer shows the saved values, all locked, only Back
	@Test
	public void C20_view_screen() {
		test = extent.createTest("C20 View screen");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		Assert.assertTrue(find_customer(c, EXISTING_CODE));
		c.Click_view(EXISTING_CODE);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.Field("code").getAttribute("value").length() > 0);

		Assert.assertEquals(c.Field("code").getAttribute("value"), EXISTING_CODE);
		Assert.assertEquals(c.Field("name").getAttribute("value"), EXISTING_NAME);
		Assert.assertEquals(c.Field("gstin").getAttribute("value"), "32CZRPS0269K1ZS");
		Assert.assertEquals(c.Field("mobile").getAttribute("value"), "9447253258");
		Assert.assertEquals(c.selected(c.Select_of("group")), "Hotel");
		Assert.assertEquals(c.selected(c.Select_of("class")), "A+");
		Assert.assertEquals(c.selected(c.Select_of("country")), "India (IND)");
		Assert.assertEquals(c.selected(c.Select_of("state")), "Kerala (KL)");
		for (String key : new String[] { "code", "name", "gstin", "store", "mobile", "route", "address", "city", "zip" }) {
			Assert.assertTrue(c.is_locked(c.Field(key)), "Field must be read-only: " + key);
		}
		for (String key : new String[] { "group", "class", "country", "state", "payment" }) {
			Assert.assertTrue(c.is_locked(c.Select_of(key)), "Drop down must be read-only: " + key);
		}
		Assert.assertFalse(c.submit_visible(), "Submit must not be shown in the view screen");
		Assert.assertTrue(c.verify_back().isDisplayed(), "Back is missing");
		test.pass("View screen is read-only");
	}

	// C21: the edit screen of the new customer shows the saved values and its fields are editable
	@Test(dependsOnMethods = "C17_create_customer")
	public void C21_edit_screen_values() {
		test = extent.createTest("C21 Edit screen values");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		Assert.assertTrue(find_customer(c, code1));
		c.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.Field("code").getAttribute("value").length() > 0);

		Assert.assertEquals(c.Field("code").getAttribute("value"), code1);
		Assert.assertEquals(c.Field("name").getAttribute("value"), name1);
		Assert.assertEquals(c.Field("gstin").getAttribute("value"), gstin1);
		Assert.assertEquals(c.selected(c.Select_of("group")), "Hotel");
		Assert.assertEquals(c.selected(c.Select_of("country")), "India (IND)");
		Assert.assertEquals(c.selected(c.Select_of("state")), "Kerala (KL)");
		Assert.assertFalse(c.is_locked(c.Field("name")), "Customer name must be editable");
		Assert.assertTrue(c.submit_visible(), "Submit must be shown in the edit screen");
		test.pass("Edit screen shows the saved values");
	}

	// C22: a changed customer name is saved and shown in the list
	@Test(dependsOnMethods = "C17_create_customer")
	public void C22_edit_name() {
		test = extent.createTest("C22 Edit customer name");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		Assert.assertTrue(find_customer(c, code1));
		c.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.Field("code").getAttribute("value").length() > 0);

		name1 = name1 + "E";
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("name", name1);
		c.Fill(v);
		submit_and_wait(c);
		test.info("Site said: " + said(c));
		Assert.assertTrue(c.is_list_page(), "Not back on the list. " + said(c));

		Assert.assertTrue(find_customer(c, code1));
		Assert.assertEquals(c.row_of(code1).get(1), name1);
		test.pass("Name changed to " + name1);
	}

	// C23: Back in the edit screen leaves the customer unchanged
	@Test(dependsOnMethods = "C17_create_customer")
	public void C23_edit_back_without_saving() {
		test = extent.createTest("C23 Back in edit screen");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		Assert.assertTrue(find_customer(c, code1));
		String before = c.row_of(code1).get(1);
		c.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.Field("code").getAttribute("value").length() > 0);
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("name", "ZQNotSaved");
		c.Fill(v);
		c.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());

		Assert.assertTrue(find_customer(c, code1));
		Assert.assertEquals(c.row_of(code1).get(1), before, "The name was changed by pressing Back");
		test.pass("Back does not save");
	}

	// C24: Back in the create screen returns to the list without saving
	@Test
	public void C24_back_button_create() {
		test = extent.createTest("C24 Back in create screen");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		open_create(c);
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("code", "ZQCBACK");
		v.put("name", "Back test");
		c.Fill(v);
		c.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
		c.Filter_table("ZQCBACK");
		Assert.assertEquals(c.result_count(), 0, "A customer was saved by pressing Back");
		test.pass("Back returns to the list without saving");
	}

	// C25: State depends on Country: empty until a country is chosen, then it lists the states (Kerala for India)
	@Test
	public void C25_state_depends_on_country() {
		test = extent.createTest("C25 State depends on country");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		open_create(c);
		List<String> before = c.options_of(c.Select_of("state"));
		test.info("States before a country is chosen: " + before);
		Assert.assertTrue(before.isEmpty(), "State must be empty before a country is chosen: " + before);
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("country", "India");
		c.Fill(v);
		List<String> after = c.options_of(c.Select_of("state"));
		test.info("States for India: " + after);
		Assert.assertTrue(after.contains("Kerala (KL)"), "Kerala (KL) is missing: " + after);
		test.pass("State follows the country");
	}

	// C26: the Route box offers routes while typing and keeps the chosen one
	@Test
	public void C26_route_search() {
		test = extent.createTest("C26 Route search");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		open_create(c);
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("route", "Tvm South");
		c.Fill(v);
		String shown = c.Field("route").getAttribute("value");
		test.info("Route box shows: " + shown);
		Assert.assertTrue(shown.toLowerCase().contains("tvm south"), "The chosen route is not kept: " + shown);
		test.pass("Route search works");
	}

	// C27: the Is Under Distributor and Is Scheme Applicable tick boxes can be changed
	@Test
	public void C27_tick_boxes_toggle() {
		test = extent.createTest("C27 Tick boxes");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		open_create(c);
		c.Set_checkbox("distributor", true);
		Assert.assertTrue(c.checkbox("distributor").isSelected(), "Is Under Distributor must be ticked");
		c.Set_checkbox("scheme", false);
		Assert.assertFalse(c.checkbox("scheme").isSelected(), "Is Scheme Applicable must be unticked");
		c.Set_checkbox("tax", false);
		Assert.assertFalse(c.checkbox("tax").isSelected(), "Is Additional Tax Applicable must be unticked");
		test.pass("Tick boxes work");
	}

	// C28: a valid customer with the optional fields filled (aadhaar, FSSAI, email, balance, credit limit,
	// cheque amount, latitude, longitude) is saved and its balance and credit are shown in the list
	@Test
	public void C28_create_with_optional_fields() {
		test = extent.createTest("C28 Create with optional fields");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		String code = "ZQC" + unique();
		Map<String, String> v = valid(code, "ZQ Full " + unique());
		v.put("aadhaar", "123456789012");
		v.put("fssai", "12345678901234");
		v.put("email", "zq" + unique() + "@example.com");
		v.put("balance", "1500");
		v.put("credit", "5000");
		v.put("cheque", "200");
		v.put("latitude", "8.5241");
		v.put("longitude", "76.9366");
		create_customer(c, v);
		test.info("Site said: " + said(c));
		Assert.assertTrue(c.is_list_page(), "Not back on the list. " + said(c));
		created.add(code);

		Assert.assertTrue(find_customer(c, code), "The new customer is not in the list");
		Assert.assertEquals(c.row_of(code).get(2), "1500", "Balance");
		Assert.assertEquals(c.row_of(code).get(3), "5000", "Credit");
		test.pass("Customer with optional fields saved");
	}

	// C29: clean up - customers cannot be deleted, so every ZQC customer (this run and earlier runs) is
	// deactivated (Is Active unticked)
	@Test(dependsOnMethods = { "C16_invalid_values_refused", "C17_create_customer", "C18_duplicate_code",
			"C19_duplicate_gstin", "C21_edit_screen_values", "C22_edit_name", "C23_edit_back_without_saving",
			"C28_create_with_optional_fields" }, alwaysRun = true)
	public void C29_deactivate_test_customers() throws InterruptedException {
		test = extent.createTest("C29 Deactivate test customers");
		K_Customer_Cls c = new K_Customer_Cls(driver);

		List<String> todo = new ArrayList<String>(created);
		c.Select_largest_entries();
		c.Filter_table("ZQC");
		for (List<String> row : c.result_texts()) {
			String code = row.get(0);
			if (code.toUpperCase().startsWith("ZQC") && !todo.contains(code)) {
				todo.add(code);
			}
		}
		test.info("Customers to deactivate: " + todo);

		for (String code : todo) {
			if (!c.is_list_page()) {
				go_to_customer();
				new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
			}
			if (!find_customer(c, code)) {
				test.warning(code + " not found, skipped");
				continue;
			}
			c.Click_edit(code);
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.Field("code").getAttribute("value").length() > 0);
			c.Set_checkbox("active", false);
			submit_and_wait(c);
			test.info(code + " deactivated, back on list: " + c.is_list_page() + " | " + said(c));
			if (!c.is_list_page()) {
				go_to_customer();
			}
		}
		test.pass("Test customers deactivated: " + todo);
	}
}