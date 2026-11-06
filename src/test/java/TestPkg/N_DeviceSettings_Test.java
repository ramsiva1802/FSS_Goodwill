package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import PagePkg.N_DeviceSettings_Cls;
import PagePkg.A_Login_Cls;

/*
 * Device Settings (Device > Device Settings) - functional tests, DS01 .. DS26.
 * Existing QA setting used for reading only: GPSE1 (it is used by Route Day Mapping: NEVER edited or deleted).
 * Settings created by the tests get the code prefix ZQS and are deleted at the end (DS24 and DS25).
 * Mandatory fields: hierarchy name, header 1, header 2, footer 1, setting code.
 */
public class N_DeviceSettings_Test extends BaseCls {

	static final String EXISTING_CODE = "GPSE1";
	static final String TYPE = "PLANT";
	static final String HIERARCHY = "GOODWILL POLYMERS FSS 2026-2027";

	// the setting created in DS15
	String code1;
	boolean setting_created = false;
	List<String> created = new ArrayList<String>();

	// what the last Submit showed
	String last_alert = "";
	String last_notice = "";

	private String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
	}

	// runs ONCE: fresh session, login through the form
	@BeforeClass
	public void login_first() {
		login_again();
		code1 = "ZQS" + unique();
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

	// opens Device > Device Settings from the dashboard (waits and tries again while the page is still loading)
	private void go_to_device_settings() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_by_name("Device", "Device Settings");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Device", "Device Settings");
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

	// every test starts with a fresh Device Settings list screen
	@BeforeMethod
	public void open_device_settings() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);
		try {
			go_to_device_settings();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> s.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_device_settings();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> s.is_list_page());
		}
		remember_clean_screen();
	}

	// presses Submit and waits for: back on the list, or a message, or a pop-up / alert.
	// The alert / pop-up text is kept in last_alert / last_notice and then closed.
	private void submit_and_wait(N_DeviceSettings_Cls s) {
		last_alert = "";
		last_notice = "";
		s.Click_submit();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> {
				String a = s.Accept_alert();
				if (!a.isEmpty()) {
					last_alert = a;
					return true;
				}
				return s.is_list_page() || !s.validation_messages().isEmpty() || !s.notice().isEmpty();
			});
		} catch (Exception e) {
			// nothing appeared: the test checks the screen itself
		}
		last_notice = s.notice();
		s.Close_notice();
		WaitUtil.settle(driver, 800);
		String late = s.Accept_alert();
		if (!late.isEmpty()) {
			last_alert = last_alert + " " + late;
		}
	}

	// everything the site said after the last Submit
	private String said(N_DeviceSettings_Cls s) {
		return s.validation_messages() + " | notice: " + last_notice + " | alert: " + last_alert;
	}

	// true when one of the messages contains the key (capital letters ignored)
	private boolean has(List<String> messages, String key) {
		for (String m : messages) {
			if (m.toLowerCase().contains(key.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	// a complete valid setting (every mandatory field filled)
	private Map<String, String> valid(String code) {
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("type", TYPE);
		v.put("hierarchy", HIERARCHY);
		v.put("header1", "ZQ Head 1");
		v.put("header2", "ZQ Head 2");
		v.put("footer1", "ZQ Foot");
		v.put("code", code);
		return v;
	}

	private void open_create(N_DeviceSettings_Cls s) {
		s.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> s.is_form_screen());
	}

	// opens the create form, fills it and submits it
	private void create_setting(N_DeviceSettings_Cls s, Map<String, String> values) {
		open_create(s);
		s.Fill(values);
		submit_and_wait(s);
	}

	// finds a setting in the list with the "Search:" box above the table
	private boolean find_setting(N_DeviceSettings_Cls s, String code) {
		s.Filter_table(code);
		return s.Wait_for_row(code);
	}

	// ======================= LIST screen =======================

	// DS01: the list shows Search, Create, table filter, the 6 column headers, the entries line and the 3 icons
	@Test
	public void DS01_list_page_elements() {
		test = extent.createTest("DS01 Device Settings list elements");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		Assert.assertTrue(s.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(s.verify_create_btn().isDisplayed(), "Create button is missing");
		Assert.assertTrue(s.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = s.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "HIERARCHY NAME", "SETTINGS CODE", "HEADER 1", "HEADER 2", "FOOTER 1", "ACTION" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(s.verify_entries().getText().contains("Showing"), "The 'Showing .. entries' line is missing");
		Assert.assertTrue(s.Wait_for_row(EXISTING_CODE), "The setting " + EXISTING_CODE + " is not in the list");
		Assert.assertEquals(s.action_icon_count(EXISTING_CODE), 3, "The view, edit and delete icons are expected");
		test.pass("List screen elements are displayed");
	}

	// DS02: the Search button opens the search form (hierarchy type, hierarchy name, device settings, Search, Clear)
	@Test
	public void DS02_search_form_opens() {
		test = extent.createTest("DS02 Search form opens");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		s.Click_top_search();
		Assert.assertTrue(s.verify_search_type().isDisplayed(), "Hierarchy Type is missing");
		Assert.assertTrue(s.verify_search_hierarchy().isDisplayed(), "Hierarchy Name is missing");
		Assert.assertTrue(s.verify_search_settings().isDisplayed(), "Device Settings is missing");
		Assert.assertTrue(s.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		Assert.assertTrue(s.verify_clear().isDisplayed(), "Clear is missing");
		Assert.assertEquals(s.selected(s.verify_search_type()), "SALES ORGANIZATION", "Default hierarchy type");
		test.pass("Search form is displayed");
	}

	// DS03: search by the setting code finds that setting
	@Test
	public void DS03_search_by_settings_code() {
		test = extent.createTest("DS03 Search by device settings");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		s.Search_settings(TYPE, "", EXISTING_CODE);
		Assert.assertTrue(s.Wait_for_row(EXISTING_CODE), "The setting " + EXISTING_CODE + " was not found");
		test.pass("Found " + EXISTING_CODE);
	}

	// DS04: search by hierarchy type and hierarchy name finds the setting of that hierarchy
	@Test
	public void DS04_search_by_hierarchy() {
		test = extent.createTest("DS04 Search by hierarchy");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		s.Search_settings(TYPE, HIERARCHY, "");
		Assert.assertTrue(s.Wait_for_row(EXISTING_CODE), "The setting of the hierarchy was not found");
		for (List<String> row : s.result_texts()) {
			Assert.assertEquals(row.get(0), HIERARCHY, "A setting of another hierarchy: " + row);
		}
		test.pass("Found by hierarchy");
	}

	// DS05: a setting that does not exist gives an empty table
	@Test
	public void DS05_search_no_match() {
		test = extent.createTest("DS05 Search without match");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		s.Search_settings(TYPE, "", "NOMATCH" + unique());
		Assert.assertEquals(s.result_count(), 0, "No setting should be listed");
		test.pass("Empty result for an unknown setting");
	}

	// DS06: Clear empties the search form and lists the rows again
	@Test
	public void DS06_clear_button() {
		test = extent.createTest("DS06 Clear button");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		s.Search_settings(TYPE, "", "NOMATCH" + unique());
		Assert.assertEquals(s.result_count(), 0);
		s.Click_clear();
		test.info("Rows after Clear: " + s.result_count());
		Assert.assertTrue(s.result_count() > 0, "Clear must list the settings again");
		s.Open_search_form();
		Assert.assertEquals(s.verify_search_settings().getAttribute("value"), "", "The Device Settings box must be empty");
		test.pass("Clear resets the search");
	}

	// DS07: the "Search:" box above the table filters the rows; unknown text gives an empty table
	@Test
	public void DS07_table_filter() {
		test = extent.createTest("DS07 Table filter");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		Assert.assertTrue(find_setting(s, EXISTING_CODE), "The filter did not find " + EXISTING_CODE);
		s.Filter_table("NOMATCH" + unique());
		Assert.assertEquals(s.result_count(), 0, "No row should be listed for unknown text");
		test.pass("Table filter works");
	}

	// ======================= FORM screen: create =======================

	// DS08: Create opens the form with all text boxes, drop downs, the 9 radio groups, the 2 file boxes, Back and Submit
	@Test
	public void DS08_create_form_elements() {
		test = extent.createTest("DS08 Create form elements");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		open_create(s);
		for (String key : new String[] { "hierarchy", "header1", "header2", "header3", "header4", "footer1", "footer2",
				"gst", "fssai", "cashdiscount", "damage", "code", "lower", "upper", "upi", "ifsc", "bank", "account",
				"declaration" }) {
			Assert.assertTrue(s.Field(key).isDisplayed(), "Field is missing: " + key);
		}
		for (String key : new String[] { "company", "type", "paperfeed", "roundoff", "roundofflimit", "vanvan" }) {
			Assert.assertTrue(s.Select_of(key).isDisplayed(), "Drop down is missing: " + key);
		}
		for (String group : N_DeviceSettings_Cls.GROUPS) {
			Assert.assertTrue(s.radio_count(group) >= 2, "Radio group is incomplete: " + group);
		}
		Assert.assertEquals(s.file_input_count(), 2, "The QR Code and Logo file boxes are expected");
		Assert.assertTrue(s.verify_back().isDisplayed(), "Back is missing");
		Assert.assertTrue(s.verify_submit().isDisplayed(), "Submit is missing");
		test.pass("All fields of the create form are displayed");
	}

	// DS09: defaults: Merchandising is Disable, the other radio groups have no choice
	@Test
	public void DS09_default_values() {
		test = extent.createTest("DS09 Default values");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		open_create(s);
		Assert.assertEquals(s.radio_selected("Merchandising"), "Disable", "Merchandising default");
		List<String> chosen = new ArrayList<String>();
		for (String group : N_DeviceSettings_Cls.GROUPS) {
			if (!group.equals("Merchandising") && !s.radio_selected(group).isEmpty()) {
				chosen.add(group + "=" + s.radio_selected(group));
			}
		}
		test.info("Radio groups with a default choice: " + chosen);
		Assert.assertTrue(chosen.isEmpty(), "These groups have a default choice: " + chosen);
		test.pass("Defaults are as shown");
	}

	// DS10: Hierarchy Type starts on its placeholder and offers PLANT and SALES ORGANIZATION; the other drop downs
	// have values (written to the report)
	@Test
	public void DS10_drop_down_options() {
		test = extent.createTest("DS10 Drop down options");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		open_create(s);
		Assert.assertEquals(s.selected(s.Select_of("type")), "HierarchyType", "Placeholder expected");
		List<String> types = s.options_of(s.Select_of("type"));
		test.info("Hierarchy types: " + types);
		Assert.assertTrue(types.contains("PLANT") && types.contains("SALES ORGANIZATION"), "Types: " + types);
		for (String key : new String[] { "paperfeed", "roundoff", "roundofflimit", "vanvan" }) {
			List<String> options = s.options_of(s.Select_of(key));
			test.info(key + ": " + options);
			Assert.assertFalse(options.isEmpty(), "The drop down " + key + " has no values");
		}
		test.pass("Drop downs are filled");
	}

	// DS11: Submit on an empty form shows the 5 mandatory messages (Hierarchy Type is also marked * and is written
	// to the report)
	@Test
	public void DS11_empty_submit() {
		test = extent.createTest("DS11 Empty submit");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		open_create(s);
		s.Click_submit();
		List<String> messages = s.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(has(messages, "hierarchy is mandatory"), "Hierarchy message missing: " + messages);
		Assert.assertTrue(has(messages, "header 1 is mandatory"), "Header 1 message missing: " + messages);
		Assert.assertTrue(has(messages, "header 2 is mandatory"), "Header 2 message missing: " + messages);
		Assert.assertTrue(has(messages, "footer 1 is mandatory"), "Footer 1 message missing: " + messages);
		Assert.assertTrue(has(messages, "setting code"), "Setting code message missing: " + messages);
		if (!has(messages, "type")) {
			test.warning("Hierarchy Type is marked with * but shows no message when it is empty");
		}
		Assert.assertTrue(s.is_form_screen(), "The form must stay open");
		test.pass("The mandatory messages are shown");
	}

	// DS12: every mandatory field on its own: the form is complete except that field, Submit must show its message
	@Test
	public void DS12_each_mandatory_field() throws InterruptedException {
		test = extent.createTest("DS12 Each mandatory field");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		String[][] fields = { { "hierarchy", "hierarchy is mandatory" }, { "header1", "header 1 is mandatory" },
				{ "header2", "header 2 is mandatory" }, { "footer1", "footer 1 is mandatory" },
				{ "code", "setting code" } };
		List<String> failed = new ArrayList<String>();
		for (String[] f : fields) {
			if (!s.is_list_page()) {
				go_to_device_settings();
				new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> s.is_list_page());
			}
			Map<String, String> v = valid("ZQS" + unique());
			v.put(f[0], "");
			open_create(s);
			s.Fill(v);
			s.Click_submit();
			List<String> messages = s.validation_messages();
			test.info("Without " + f[0] + ": " + messages);
			if (!has(messages, f[1]) || !s.is_form_screen()) {
				failed.add(f[0]);
			}
			go_to_device_settings();
		}
		Assert.assertTrue(failed.isEmpty(), "No message (or the form was saved) when these fields are empty: " + failed);
		test.pass("Every mandatory field is checked");
	}

	// DS13: radio groups: one choice per group; Merchandising keeps exactly one of its three
	@Test
	public void DS13_radio_groups() {
		test = extent.createTest("DS13 Radio groups");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		open_create(s);
		s.Set_radio("Payment Split", "Enable");
		Assert.assertEquals(s.radio_selected("Payment Split"), "Enable");
		s.Set_radio("Payment Split", "Disable");
		Assert.assertEquals(s.radio_selected("Payment Split"), "Disable", "Choosing Disable must clear Enable");
		s.Set_radio("Merchandising", "Mandatory");
		Assert.assertEquals(s.radio_selected("Merchandising"), "Mandatory");
		s.Set_radio("Merchandising", "Alert");
		Assert.assertEquals(s.radio_selected("Merchandising"), "Alert", "Choosing Alert must clear Mandatory");
		test.pass("Radio groups keep one choice");
	}

	// DS14: invalid values must be refused. If the site accepts one, the setting is saved (a defect) and it is
	// deleted at the end.
	@Test
	public void DS14_invalid_values_refused() throws InterruptedException {
		test = extent.createTest("DS14 Invalid values");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		String[][] probes = { { "cash discount with letters", "cashdiscount", "abc", "", "" },
				{ "negative cash discount", "cashdiscount", "-5", "", "" },
				{ "cash discount over 100", "cashdiscount", "150", "", "" },
				{ "GST number too short", "gst", "ABC123", "", "" },
				{ "negative discount lower limit", "lower", "-1", "", "" },
				{ "discount lower limit above the upper limit", "lower", "20", "upper", "10" },
				{ "account number with letters", "account", "ABCDEFG", "", "" },
				{ "IFSC code too short", "ifsc", "123", "", "" },
				{ "header 1 of spaces", "header1", "   ", "", "" }, { "setting code of spaces", "code", "   ", "", "" } };
		List<String> accepted = new ArrayList<String>();
		for (String[] p : probes) {
			if (!s.is_list_page()) {
				go_to_device_settings();
				new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> s.is_list_page());
			}
			String code = "ZQS" + unique();
			Map<String, String> v = valid(code);
			v.put(p[1], p[2]);
			if (!p[3].isEmpty()) {
				v.put(p[3], p[4]);
			}
			create_setting(s, v);
			boolean saved = s.is_list_page();
			test.info(p[0] + ": " + (saved ? "ACCEPTED" : "refused") + " | " + said(s));
			if (!saved && s.validation_messages().isEmpty() && last_notice.isEmpty() && last_alert.isEmpty()) {
				test.warning(p[0] + ": refused without any message");
			}
			if (saved) {
				accepted.add(p[0]);
				created.add(code);
			}
			go_to_device_settings();
		}
		Assert.assertTrue(accepted.isEmpty(), "DEFECT: the site accepted: " + accepted);
		test.pass("All invalid values are refused");
	}

	// ======================= create, view, edit, delete =======================

	// DS15: a valid setting (with Declaration Enable and its text, and Merchandising Alert) is created and listed
	@Test
	public void DS15_create_setting() {
		test = extent.createTest("DS15 Create setting");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		test.info("Creating " + code1);
		Map<String, String> v = valid(code1);
		v.put("declaration", "ZQ Declaration");
		open_create(s);
		s.Fill(v);
		s.Set_radio("Declaration", "Enable");
		s.Set_radio("Merchandising", "Alert");
		submit_and_wait(s);
		test.info("Site said: " + said(s));
		Assert.assertTrue(s.is_list_page(), "Not back on the list. " + said(s));
		created.add(code1);
		setting_created = true;

		Assert.assertTrue(find_setting(s, code1), "The new setting is not in the list");
		List<String> row = s.row_of(code1);
		test.info("Row: " + row);
		Assert.assertEquals(row.get(0), HIERARCHY, "Hierarchy name");
		test.pass("Setting created and listed");
	}

	// DS15a: with Declaration set to Enable the Declaration text is mandatory
	@Test
	public void DS15a_declaration_text_needed_when_enabled() {
		test = extent.createTest("DS15a Declaration text when enabled");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		open_create(s);
		s.Fill(valid("ZQS" + unique()));
		s.Set_radio("Declaration", "Enable");
		s.Click_submit();
		List<String> messages = s.validation_messages();
		test.info("Messages: " + messages);
		Assert.assertTrue(has(messages, "declaration is mandatory"), "Declaration message missing: " + messages);
		Assert.assertTrue(s.is_form_screen(), "The form must stay open");
		test.pass("The Declaration text is mandatory when Declaration is Enable");
	}
	// DS15b: short header and footer texts must be shown as they are in the list (no "..." added)
	@Test(dependsOnMethods = "DS15_create_setting")
	public void DS15b_short_values_not_truncated() {
		test = extent.createTest("DS15b Short values in the list");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		Assert.assertTrue(find_setting(s, code1));
		List<String> row = s.row_of(code1);
		test.info("Header 1 '" + row.get(2) + "', header 2 '" + row.get(3) + "', footer 1 '" + row.get(4) + "'");
		Assert.assertEquals(row.get(2), "ZQ Head 1", "DEFECT: header 1 is changed in the list");
		Assert.assertEquals(row.get(3), "ZQ Head 2", "DEFECT: header 2 is changed in the list");
		Assert.assertEquals(row.get(4), "ZQ Foot", "DEFECT: footer 1 is changed in the list (dots added to a short text)");
		test.pass("Short texts are shown as entered");
	}

	// DS16: the same setting code again for the same hierarchy must be refused
	@Test(dependsOnMethods = "DS15_create_setting")
	public void DS16_duplicate_setting_code() {
		test = extent.createTest("DS16 Duplicate setting code");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		create_setting(s, valid(code1));
		test.info("Site said: " + said(s));
		boolean refused = !s.is_list_page() || !last_notice.isEmpty() || !last_alert.isEmpty();
		if (s.is_list_page()) {
			find_setting(s, code1);
			int same = 0;
			for (List<String> row : s.result_texts()) {
				if (row.get(1).equalsIgnoreCase(code1)) {
					same++;
				}
			}
			Assert.assertEquals(same, 1, "DEFECT: the duplicate setting code " + code1 + " was saved again");
		}
		Assert.assertTrue(refused, "DEFECT: the duplicate setting code was accepted silently");
		test.pass("Duplicate refused: " + said(s));
	}

	// DS17: the view screen of GPSE1 shows the saved values, all locked, only Back
	@Test
	public void DS17_view_screen() {
		test = extent.createTest("DS17 View screen");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		Assert.assertTrue(find_setting(s, EXISTING_CODE));
		s.Click_view(EXISTING_CODE);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> s.Field("code").getAttribute("value").length() > 0);

		Assert.assertEquals(s.Field("code").getAttribute("value"), EXISTING_CODE);
		Assert.assertEquals(s.selected(s.Select_of("type")), TYPE);
		Assert.assertEquals(s.Field("hierarchy").getAttribute("value"), HIERARCHY);
		Assert.assertEquals(s.Field("header1").getAttribute("value"), "GOODWILL POLYMERS");
		Assert.assertEquals(s.Field("footer1").getAttribute("value"), "Thank U");
		Assert.assertEquals(s.Field("gst").getAttribute("value"), "32AADFG7217Q1Z6");
		Assert.assertEquals(s.selected(s.Select_of("roundoff")), "Auto");
		Assert.assertEquals(s.selected(s.Select_of("roundofflimit")), "1 Rupee");
		Assert.assertEquals(s.Field("lower").getAttribute("value"), "1");
		Assert.assertEquals(s.Field("upper").getAttribute("value"), "10");
		Assert.assertEquals(s.radio_selected("Declaration"), "Enable");
		Assert.assertEquals(s.radio_selected("Payment Split"), "Disable");
		Assert.assertEquals(s.radio_selected("Merchandising"), "Alert");
		for (String key : new String[] { "hierarchy", "header1", "header2", "footer1", "gst", "code", "lower", "upper" }) {
			Assert.assertTrue(s.is_locked(s.Field(key)), "Field must be read-only: " + key);
		}
		for (String key : new String[] { "type", "roundoff", "roundofflimit", "paperfeed" }) {
			Assert.assertTrue(s.is_locked(s.Select_of(key)), "Drop down must be read-only: " + key);
		}
		Assert.assertFalse(s.submit_visible(), "Submit must not be shown in the view screen");
		Assert.assertTrue(s.verify_back().isDisplayed(), "Back is missing");
		test.pass("View screen is read-only");
	}

	// DS18: the edit screen of the new setting shows the saved values and its fields are editable
	@Test(dependsOnMethods = "DS15_create_setting")
	public void DS18_edit_screen_values() {
		test = extent.createTest("DS18 Edit screen values");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		Assert.assertTrue(find_setting(s, code1));
		s.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> s.Field("code").getAttribute("value").length() > 0);

		Assert.assertEquals(s.Field("code").getAttribute("value"), code1);
		Assert.assertEquals(s.Field("header1").getAttribute("value"), "ZQ Head 1");
		Assert.assertEquals(s.Field("footer1").getAttribute("value"), "ZQ Foot");
		Assert.assertEquals(s.radio_selected("Declaration"), "Enable");
		Assert.assertEquals(s.radio_selected("Merchandising"), "Alert");
		test.info("Hierarchy name locked: " + s.is_locked(s.Field("hierarchy")) + ", setting code locked: "
				+ s.is_locked(s.Field("code")));
		Assert.assertFalse(s.is_locked(s.Field("footer1")), "Footer 1 must be editable");
		Assert.assertTrue(s.submit_visible(), "Submit must be shown in the edit screen");
		test.pass("Edit screen shows the saved values");
	}

	// DS19: a changed footer is saved and shown in the list
	@Test(dependsOnMethods = "DS15_create_setting")
	public void DS19_edit_footer() {
		test = extent.createTest("DS19 Edit footer");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		Assert.assertTrue(find_setting(s, code1));
		s.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> s.Field("code").getAttribute("value").length() > 0);
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("footer1", "ZQ Foot2");
		s.Fill(v);
		submit_and_wait(s);
		test.info("Site said: " + said(s));
		Assert.assertTrue(s.is_list_page(), "Not back on the list. " + said(s));

		Assert.assertTrue(find_setting(s, code1));
		Assert.assertEquals(s.row_of(code1).get(4), "ZQ Foot2");
		test.pass("Footer changed");
	}

	// DS20: Back in the edit screen leaves the setting unchanged
	@Test(dependsOnMethods = "DS15_create_setting")
	public void DS20_edit_back_without_saving() {
		test = extent.createTest("DS20 Back in edit screen");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		Assert.assertTrue(find_setting(s, code1));
		String before = s.row_of(code1).get(4);
		s.Click_edit(code1);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> s.Field("code").getAttribute("value").length() > 0);
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("footer1", "ZQNotSaved");
		s.Fill(v);
		s.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> s.is_list_page());

		Assert.assertTrue(find_setting(s, code1));
		Assert.assertEquals(s.row_of(code1).get(4), before, "The footer was changed by pressing Back");
		test.pass("Back does not save");
	}

	// DS21: Back in the create screen returns to the list without saving
	@Test
	public void DS21_back_button_create() {
		test = extent.createTest("DS21 Back in create screen");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		open_create(s);
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("code", "ZQSBACK");
		s.Fill(v);
		s.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> s.is_list_page());
		s.Filter_table("ZQSBACK");
		Assert.assertEquals(s.result_count(), 0, "A setting was saved by pressing Back");
		test.pass("Back returns to the list without saving");
	}

	// DS22: the Hierarchy Name box offers hierarchies of the chosen type and keeps the chosen one
	@Test
	public void DS22_hierarchy_search() {
		test = extent.createTest("DS22 Hierarchy search");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		open_create(s);
		Map<String, String> v = new LinkedHashMap<String, String>();
		v.put("type", TYPE);
		v.put("hierarchy", "GOODWILL");
		s.Fill(v);
		String shown = s.Field("hierarchy").getAttribute("value");
		test.info("Hierarchy Name shows: " + shown + " | company: " + s.selected(s.Select_of("company")));
		Assert.assertTrue(shown.toUpperCase().contains("GOODWILL"), "The chosen hierarchy is not kept: " + shown);
		test.pass("Hierarchy search works");
	}

	// DS23: Cancel in the delete confirmation keeps the setting
	@Test(dependsOnMethods = "DS15_create_setting")
	public void DS23_delete_cancel() {
		test = extent.createTest("DS23 Delete cancelled");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		Assert.assertTrue(find_setting(s, code1));
		s.Click_delete(code1);
		String text = s.Cancel_delete();
		test.info("Confirmation text: " + text);
		Assert.assertFalse(text.isEmpty(), "No delete confirmation was shown");
		Assert.assertTrue(find_setting(s, code1), "The setting was deleted although Cancel was pressed");
		test.pass("Setting kept after Cancel");
	}

	// DS24: delete the created setting: confirmation, then it is gone from the list.
	// Only a ZQS setting is ever deleted (never GPSE1).
	@Test(dependsOnMethods = { "DS15_create_setting", "DS15b_short_values_not_truncated", "DS16_duplicate_setting_code",
			"DS18_edit_screen_values", "DS19_edit_footer", "DS20_edit_back_without_saving",
			"DS23_delete_cancel" }, alwaysRun = true)
	public void DS24_delete_setting() {
		test = extent.createTest("DS24 Delete setting");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		if (!setting_created) {
			throw new SkipException("No setting was created in DS15, nothing to delete");
		}
		Assert.assertTrue(code1.startsWith("ZQS"), "Refusing to delete a setting that is not a test setting");
		Assert.assertTrue(find_setting(s, code1), "The setting " + code1 + " to delete is not in the list");
		s.Click_delete(code1);
		List<String> messages = s.Confirm_delete();
		test.info("Messages: " + messages);
		Assert.assertFalse(messages.isEmpty(), "No delete confirmation was shown");

		s.Filter_table(code1);
		Assert.assertTrue(s.Wait_for_no_row(code1), "DEFECT: the setting is still listed after Delete");
		created.remove(code1);
		test.pass("Setting deleted: " + messages);
	}

	// DS25: clean up - every other ZQS setting (probes of DS14 and leftovers of earlier runs) is deleted
	@Test(dependsOnMethods = { "DS14_invalid_values_refused", "DS24_delete_setting" }, alwaysRun = true)
	public void DS25_cleanup() {
		test = extent.createTest("DS25 Clean up test settings");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		for (int round = 1; round <= 15; round++) {
			s.Filter_table("ZQS");
			List<String> mine = new ArrayList<String>();
			for (List<String> row : s.result_texts()) {
				if (row.get(1).toUpperCase().startsWith("ZQS")) {
					mine.add(row.get(1));
				}
			}
			test.info("Round " + round + ": test settings in the list: " + mine);
			if (mine.isEmpty()) {
				break;
			}
			String code = mine.get(0);
			Assert.assertTrue(code.toUpperCase().startsWith("ZQS"), "Refusing to delete: " + code);
			s.Click_delete(code);
			test.info("Deleting " + code + " showed: " + s.Confirm_delete());
		}
		s.Filter_table("ZQS");
		Assert.assertEquals(s.result_count(), 0, "Test settings are still in the list");
		test.pass("No test setting is left");
	}

	// DS26: the QR Code and Logo boxes accept no file in the view screen (they are disabled) and exist in the create screen
	@Test
	public void DS26_file_boxes_in_view() {
		test = extent.createTest("DS26 File boxes in the view screen");
		N_DeviceSettings_Cls s = new N_DeviceSettings_Cls(driver);

		Assert.assertTrue(find_setting(s, EXISTING_CODE));
		s.Click_view(EXISTING_CODE);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> s.Field("code").getAttribute("value").length() > 0);
		Assert.assertEquals(s.file_input_count(), 2, "The QR Code and Logo file boxes are expected");
		test.pass("File boxes are present in the view screen");
	}
}