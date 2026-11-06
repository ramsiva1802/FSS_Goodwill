package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.time.LocalDateTime;
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
import PagePkg.Q_Complaint_Cls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;

/*
 * Complaint (Field Management > Complaint) - functional tests, CM01 .. CM23.
 * There is no create form: complaints come from the mobile app. The tests change only the Processing Remark of the
 * first complaint of the list, and always put the original text back. Changing the Complaint Status cannot be undone
 * reliably, so CM22 runs only when ALLOW_STATUS_CHANGE is true. The print icon opens the browser print preview, which
 * Selenium cannot control: CM23 runs only when RUN_PRINT_TEST is true.
 */
public class Q_Complaint_Test extends BaseCls {

	static final boolean ALLOW_STATUS_CHANGE = false;
	static final boolean RUN_PRINT_TEST = false;

	static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

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

	// opens Field Management > Complaint from the dashboard (waits and tries again while the page is still loading)
	private void go_to_complaint() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_by_name("Field Management", "Complaint");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Field Management", "Complaint");
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

	// every test starts with a fresh Complaint list screen
	@BeforeMethod
	public void open_complaint() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);
		try {
			go_to_complaint();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> c.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_complaint();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> c.is_list_page());
		}
		remember_clean_screen();
	}

	// presses Submit and waits for: back on the list, or a message, or a pop-up / alert.
	private void submit_and_wait(Q_Complaint_Cls c) {
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

	private String said(Q_Complaint_Cls c) {
		return c.validation_messages() + " | notice: " + last_notice + " | alert: " + last_alert;
	}

	// the first row of the list (the complaint used by the tests)
	private List<String> first_row(Q_Complaint_Cls c) {
		Assert.assertTrue(c.result_count() > 0, "No complaint is listed");
		return c.result_texts().get(0);
	}

	private void open_view(Q_Complaint_Cls c, String number) {
		c.Click_view(number);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_form_screen());
	}

	private void open_edit(Q_Complaint_Cls c, String number) {
		c.Click_edit(number);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_form_screen());
	}

	// opens the edit screen of a complaint, sets the Processing Remark and saves; returns true when it is back on the list
	private boolean save_remark(Q_Complaint_Cls c, String number, String text) throws InterruptedException {
		if (!c.is_list_page()) {
			go_to_complaint();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
		}
		open_edit(c, number);
		c.Set_remark(text);
		submit_and_wait(c);
		return c.is_list_page();
	}

	// the Processing Remark as the view screen shows it
	private String saved_remark(Q_Complaint_Cls c, String number) throws InterruptedException {
		if (!c.is_list_page()) {
			go_to_complaint();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
		}
		open_view(c, number);
		return c.remark();
	}

	// ======================= list and search form =======================

	// CM01: the list shows Search, the table filter, the 7 column headers, the entries line and a view, edit and print
	// icon in each row; there is no Create button
	@Test
	public void CM01_list_page_elements() {
		test = extent.createTest("CM01 Complaint list elements");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		Assert.assertTrue(c.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(c.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = c.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "COMPLAINT NUMBER", "COMPLAINT TYPE", "COMPLAINT DATE", "CUSTOMER NAME",
				"SALESMAN NAME", "COMPLAINT STATUS", "ACTION" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(c.verify_entries().getText().contains("Showing 1 to"), c.verify_entries().getText());
		for (List<String> row : c.result_texts()) {
			Assert.assertEquals(c.action_icon_count(row.get(0)), 3, "View, edit and print icons are expected: " + row.get(0));
		}
		test.pass("List screen elements are displayed: " + c.verify_entries().getText());
	}

	// CM02: the Search button opens the search form (hierarchy type, hierarchy name box, customer name, status, Search);
	// the defaults are Factory and ALL
	@Test
	public void CM02_search_form_opens() {
		test = extent.createTest("CM02 Search form opens");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		c.Open_search_form();
		Assert.assertTrue(c.verify_type().isDisplayed(), "Hierarchy Type is missing");
		Assert.assertTrue(c.verify_hierarchy().isDisplayed(), "The hierarchy name box is missing");
		Assert.assertTrue(c.verify_customer().isDisplayed(), "Customer Name is missing");
		Assert.assertTrue(c.verify_status().isDisplayed(), "Status is missing");
		Assert.assertTrue(c.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		test.info("Defaults: hierarchy type '" + c.selected(c.verify_type()) + "', status '" + c.selected(c.verify_status()) + "'");
		Assert.assertEquals(c.selected(c.verify_status()).toUpperCase(), "ALL", "Default status");
		test.pass("Search form is displayed");
	}

	// CM03: Hierarchy Type and Status have values (written to the report)
	@Test
	public void CM03_drop_down_options() {
		test = extent.createTest("CM03 Drop down options");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		c.Open_search_form();
		List<String> types = c.all_options_of(c.verify_type());
		List<String> statuses = c.all_options_of(c.verify_status());
		test.info("Hierarchy types: " + types);
		test.info("Statuses: " + statuses);
		Assert.assertFalse(types.isEmpty(), "Hierarchy Type has no values");
		Assert.assertTrue(statuses.size() > 1, "Status has no values: " + statuses);
		test.pass("Drop downs are filled");
	}

	// CM04: every status lists only complaints of that status (the status of the first row must be among the options)
	@Test
	public void CM04_search_by_status() {
		test = extent.createTest("CM04 Search by status");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		c.Open_search_form();
		List<String> statuses = c.all_options_of(c.verify_status());
		List<String> wrong = new ArrayList<String>();
		for (String status : statuses) {
			if (status.equalsIgnoreCase("ALL")) {
				continue;
			}
			c.Search_complaints("", "", "", status);
			test.info("Status " + status + ": " + c.result_count() + " rows (" + c.verify_entries().getText() + ")");
			for (List<String> row : c.result_texts()) {
				if (!row.get(5).equalsIgnoreCase(status)) {
					wrong.add(status + ": " + row.get(0) + " is " + row.get(5));
				}
			}
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: complaints of another status are listed: " + wrong);
		test.pass("Every status lists only its own complaints");
	}

	// CM05: search by customer lists only that customer's complaints
	@Test
	public void CM05_search_by_customer() {
		test = extent.createTest("CM05 Search by customer");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		String customer = null;
		for (List<String> row : c.result_texts()) {
			if (!row.get(3).isEmpty()) {
				customer = row.get(3);
				break;
			}
		}
		if (customer == null) {
			throw new SkipException("No complaint has a customer");
		}
		test.info("Customer used: " + customer);
		c.Search_complaints("", "", customer, "");
		Assert.assertTrue(c.result_count() > 0, "No complaint was found for " + customer);
		for (List<String> row : c.result_texts()) {
			Assert.assertEquals(row.get(3), customer, "A complaint of another customer: " + row);
		}
		test.pass("Only " + customer + " is listed");
	}

	// CM06: a customer that does not exist gives an empty table
	@Test
	public void CM06_search_no_match() {
		test = extent.createTest("CM06 Search without match");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		c.Search_complaints("", "", "NOMATCH" + unique(), "");
		Assert.assertEquals(c.result_count(), 0, "No complaint should be listed. Rows shown: " + c.result_texts());
		test.pass("Empty result for an unknown customer");
	}

	// CM07: the "Search:" box above the table filters the rows; unknown text gives an empty table
	@Test
	public void CM07_table_filter() {
		test = extent.createTest("CM07 Table filter");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		String key = first_row(c).get(1).split(" ")[0];
		c.Filter_table(key);
		Assert.assertTrue(c.result_count() > 0, "The filter for '" + key + "' hid every row");
		for (List<String> row : c.result_texts()) {
			Assert.assertTrue(row.toString().toLowerCase().contains(key.toLowerCase()), "A row that does not match: " + row);
		}
		c.Filter_table("NOMATCH" + unique());
		Assert.assertEquals(c.result_count(), 0, "No row should be listed for unknown text");
		test.pass("Table filter works");
	}

	// CM08: with up to 10 complaints there is one page: "Showing 1 to N of N", Previous and Next are disabled
	@Test
	public void CM08_single_page() {
		test = extent.createTest("CM08 Paging");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		String info = c.verify_entries().getText();
		test.info(info);
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("of (\\d+) entries").matcher(info);
		Assert.assertTrue(m.find(), "The entries line could not be read: " + info);
		int total = Integer.parseInt(m.group(1));
		if (total <= 10) {
			Assert.assertEquals(c.result_count(), total, "All complaints must be on one page");
			Assert.assertFalse(c.next_enabled(), "Next must be disabled when there is one page");
			Assert.assertFalse(c.previous_enabled(), "Previous must be disabled when there is one page");
		} else {
			Assert.assertTrue(c.next_enabled(), "Next must be enabled for " + total + " complaints");
		}
		test.pass("Paging matches the number of complaints");
	}

	// CM09: every row: a complaint number, a type, a date (dd/MM/yyyy HH:mm:ss), a salesman as "Name(CODE)" and a status;
	// a Product Related complaint has a customer; the list is sorted by date, newest first
	@Test
	public void CM09_rows_are_consistent() {
		test = extent.createTest("CM09 Rows are consistent");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		LocalDateTime previous = null;
		for (List<String> row : c.result_texts()) {
			Assert.assertTrue(row.get(0).matches("\\d{2}[A-Z]+\\d+"), "Complaint number: " + row);
			Assert.assertFalse(row.get(1).isEmpty(), "Complaint type is empty: " + row);
			Assert.assertTrue(row.get(2).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}"), "Complaint date: " + row);
			Assert.assertTrue(row.get(4).matches(".+\\(.+\\)"), "Salesman is not 'Name(CODE)': " + row);
			Assert.assertFalse(row.get(5).isEmpty(), "Complaint status is empty: " + row);
			if (row.get(1).equals("Product Related")) {
				Assert.assertFalse(row.get(3).isEmpty(), "A Product Related complaint has no customer: " + row);
			}
			LocalDateTime date = LocalDateTime.parse(row.get(2), ROW_DATE);
			if (previous != null) {
				Assert.assertFalse(date.isAfter(previous), "DEFECT: the list is not sorted newest first at " + row.get(0));
			}
			previous = date;
		}
		test.pass("All rows are consistent");
	}

	// ======================= view screen =======================

	// CM10: the view screen shows what the list shows: complaint number, type, date, customer (N/A without one),
	// salesman, a description; the tab is named after the complaint type; only Back is shown
	@Test
	public void CM10_view_screen_values() {
		test = extent.createTest("CM10 View screen values");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		List<String> row = first_row(c);
		open_view(c, row.get(0));
		test.info("Tab: '" + c.tab_text() + "', status shown: '" + c.selected(c.verify_complaint_status()) + "'");
		Assert.assertEquals(c.verify_number().getAttribute("value"), row.get(0), "Complaint No");
		Assert.assertEquals(c.verify_complaint_type().getAttribute("value"), row.get(1), "Complaint Type");
		Assert.assertEquals(c.verify_complaint_date().getAttribute("value"), row.get(2), "Complaint Date");
		String customer = row.get(3).isEmpty() ? "N/A" : row.get(3);
		Assert.assertEquals(c.verify_customer_field().getAttribute("value"), customer, "Customer");
		Assert.assertEquals(c.verify_salesman().getAttribute("value"), row.get(4), "Salesman");
		Assert.assertFalse(c.verify_description().getAttribute("value").isEmpty(), "The complaint description is empty");
		Assert.assertTrue(c.tab_text().toLowerCase().contains(row.get(1).split(" ")[0].toLowerCase()),
				"The tab '" + c.tab_text() + "' does not match the complaint type " + row.get(1));
		Assert.assertFalse(c.submit_visible(), "Submit must not be shown in the view screen");
		Assert.assertTrue(c.verify_back().isDisplayed(), "Back is missing");
		test.pass("View screen shows the complaint");
	}

	// CM11: the fields of the view screen are read-only
	@Test
	public void CM11_view_screen_is_read_only() {
		test = extent.createTest("CM11 View screen is read-only");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		open_view(c, first_row(c).get(0));
		Assert.assertTrue(c.is_locked(c.verify_number()), "Complaint No must be read-only");
		Assert.assertTrue(c.is_locked(c.verify_complaint_type()), "Complaint Type must be read-only");
		Assert.assertTrue(c.is_locked(c.verify_complaint_date()), "Complaint Date must be read-only");
		Assert.assertTrue(c.is_locked(c.verify_customer_field()), "Customer must be read-only");
		Assert.assertTrue(c.is_locked(c.verify_salesman()), "Salesman must be read-only");
		Assert.assertTrue(c.is_locked(c.verify_description()), "Complaint Description must be read-only");
		Assert.assertTrue(c.is_locked(c.verify_remark()), "Processing Remark must be read-only in the view screen");
		test.pass("View screen fields are read-only");
	}

	// CM12: the Complaint Status drop down of the view screen must be locked like the other fields (it looks open)
	@Test
	public void CM12_view_screen_status_locked() {
		test = extent.createTest("CM12 Complaint status in the view screen");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		open_view(c, first_row(c).get(0));
		String result = c.Try_change_status();
		test.info("Trying to change the status: " + result);
		Assert.assertFalse(result.startsWith("changed"),
				"DEFECT: the Complaint Status can be changed in the View screen (" + result + ")");
		test.pass("Status is locked in the view screen: " + result);
	}

	// CM13: the status in the list and the status in the view screen are the same
	@Test
	public void CM13_status_list_equals_view() {
		test = extent.createTest("CM13 Status in the list and in the view screen");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		List<String> row = first_row(c);
		open_view(c, row.get(0));
		String shown = c.selected(c.verify_complaint_status());
		test.info("List status '" + row.get(5) + "' | view screen status '" + shown + "' | options "
				+ c.all_options_of(c.verify_complaint_status()));
		Assert.assertTrue(shown.equalsIgnoreCase(row.get(5)),
				"DEFECT: the list shows '" + row.get(5) + "' but the view screen shows '" + shown
						+ "' (confirm if these two names are meant to be the same status)");
		test.pass("The status is the same in both screens");
	}

	// CM14: the view screen has the audio and uploaded file players with download icons (counts in the report)
	@Test
	public void CM14_audio_and_files() {
		test = extent.createTest("CM14 Audio and files");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		open_view(c, first_row(c).get(0));
		test.info("Audio players: " + c.audio_count() + ", download links: " + c.download_count());
		Assert.assertTrue(c.audio_count() >= 1, "No audio player is shown");
		test.pass("Audio players are shown");
	}

	// CM15: the picture slider opens the "Complaint Photos" pop-up: counter "1 / N", the next and previous arrows change it,
	// the x closes the pop-up (a complaint with pictures is used)
	@Test
	public void CM15_photo_gallery() {
		test = extent.createTest("CM15 Complaint photos");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		String number = null;
		for (List<String> row : c.result_texts()) {
			open_view(c, row.get(0));
			if (c.has_photos()) {
				number = row.get(0);
				break;
			}
			c.Click_back();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
		}
		if (number == null) {
			throw new SkipException("No complaint has a picture");
		}
		test.info("Complaint used: " + number);
		c.Open_photos();
		int[] first = c.photo_counter();
		test.info("Counter: " + first[0] + " / " + first[1] + ", pictures in the pop-up: " + c.photo_image_count());
		Assert.assertEquals(first[0], 1, "The pop-up must start on the first picture");
		Assert.assertTrue(first[1] >= 1, "The counter could not be read");
		if (first[1] > 1) {
			c.Next_photo();
			Assert.assertEquals(c.photo_counter()[0], 2, "Next must show the second picture");
			c.Previous_photo();
			Assert.assertEquals(c.photo_counter()[0], 1, "Previous must show the first picture again");
		}
		c.Close_photos();
		Assert.assertFalse(c.photos_open(), "The pop-up must close");
		test.pass("Photo gallery works");
	}

	// ======================= edit screen =======================

	// CM16: the edit screen shows the saved values; Complaint No stays read-only, Processing Remark and Complaint
	// Status can be changed; Submit and Back are shown
	@Test
	public void CM16_edit_screen() {
		test = extent.createTest("CM16 Edit screen");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		List<String> row = first_row(c);
		open_edit(c, row.get(0));
		Assert.assertEquals(c.verify_number().getAttribute("value"), row.get(0), "Complaint No");
		Assert.assertTrue(c.is_locked(c.verify_number()), "Complaint No must stay read-only");
		Assert.assertFalse(c.is_locked(c.verify_remark()), "Processing Remark must be editable");
		Assert.assertFalse(c.is_locked(c.verify_complaint_status()), "Complaint Status must be editable");
		Assert.assertTrue(c.submit_visible(), "Submit must be shown in the edit screen");
		test.info("Status options: " + c.all_options_of(c.verify_complaint_status()));
		test.pass("Edit screen is as expected");
	}

	// CM17: a changed Processing Remark is saved and shown in the view screen (the original is put back)
	@Test
	public void CM17_edit_remark() throws InterruptedException {
		test = extent.createTest("CM17 Edit processing remark");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		String number = first_row(c).get(0);
		open_edit(c, number);
		String original = c.remark();
		c.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
		String changed = "ZQ remark " + unique();
		try {
			Assert.assertTrue(save_remark(c, number, changed), "Not back on the list after Submit. " + said(c));
			test.info("Site said: " + said(c));
			Assert.assertEquals(saved_remark(c, number), changed, "The remark was not saved");
		} finally {
			boolean restored = save_remark(c, number, original);
			test.info("Original remark '" + original + "' put back: " + restored);
		}
		Assert.assertEquals(saved_remark(c, number), original, "The original remark was not put back");
		test.pass("Remark saved and restored");
	}

	// CM18: Back in the edit screen leaves the remark unchanged
	@Test
	public void CM18_edit_back_without_saving() throws InterruptedException {
		test = extent.createTest("CM18 Back in the edit screen");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		String number = first_row(c).get(0);
		open_edit(c, number);
		String original = c.remark();
		c.Set_remark("ZQ not saved " + unique());
		c.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
		Assert.assertEquals(saved_remark(c, number), original, "The remark was changed by pressing Back");
		test.pass("Back does not save");
	}

	// CM19: observation - an empty Processing Remark: the result is written to the report and the original is put back
	@Test
	public void CM19_empty_remark() throws InterruptedException {
		test = extent.createTest("CM19 Empty processing remark");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		String number = first_row(c).get(0);
		open_edit(c, number);
		String original = c.remark();
		c.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
		try {
			boolean saved = save_remark(c, number, "");
			test.info("Empty remark: " + (saved ? "ACCEPTED" : "refused") + " | " + said(c));
			if (saved) {
				test.warning("An empty Processing Remark was accepted. Confirm that this is intended.");
			}
		} finally {
			if (!c.is_list_page()) {
				go_to_complaint();
			}
			test.info("Original remark put back: " + save_remark(c, number, original));
		}
		test.pass("Observation recorded");
	}

	// CM20: observation - a very long Processing Remark (2000 characters): saved in full, cut, or refused; the
	// original is put back
	@Test
	public void CM20_long_remark() throws InterruptedException {
		test = extent.createTest("CM20 Long processing remark");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		String number = first_row(c).get(0);
		open_edit(c, number);
		String original = c.remark();
		c.Click_back();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> c.is_list_page());
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 2000; i++) {
			sb.append((char) ('a' + i % 26));
		}
		try {
			boolean saved = save_remark(c, number, sb.toString());
			test.info("2000 characters: " + (saved ? "accepted" : "refused") + " | " + said(c));
			if (saved) {
				int kept = saved_remark(c, number).length();
				test.info("Characters kept: " + kept);
				if (kept < 2000) {
					test.warning("The remark was cut to " + kept + " characters without a message.");
				}
			}
		} finally {
			if (!c.is_list_page()) {
				go_to_complaint();
			}
			test.info("Original remark put back: " + save_remark(c, number, original));
		}
		test.pass("Observation recorded");
	}

	// CM21: the Complaint Status drop down of the edit screen has the statuses of the search form (written to the report)
	@Test
	public void CM21_status_options_match_search_form() {
		test = extent.createTest("CM21 Status options");
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		c.Open_search_form();
		List<String> search_statuses = c.all_options_of(c.verify_status());
		open_edit(c, first_row(c).get(0));
		List<String> edit_statuses = c.all_options_of(c.verify_complaint_status());
		test.info("Search form statuses: " + search_statuses);
		test.info("Edit screen statuses: " + edit_statuses);
		List<String> missing = new ArrayList<String>();
		for (String s : search_statuses) {
			if (!s.equalsIgnoreCase("ALL") && !edit_statuses.contains(s)) {
				missing.add(s);
			}
		}
		Assert.assertTrue(missing.isEmpty(), "DEFECT: these statuses of the search form cannot be chosen in the edit screen: " + missing);
		test.pass("Both screens offer the same statuses");
	}

	// CM22: changing the Complaint Status is saved and shown in the list (the original is put back).
	// Only with ALLOW_STATUS_CHANGE: a status change may notify the salesman and cannot always be undone.
	@Test
	public void CM22_change_status() throws InterruptedException {
		test = extent.createTest("CM22 Change complaint status");
		if (!ALLOW_STATUS_CHANGE) {
			throw new SkipException("ALLOW_STATUS_CHANGE is false: a status change cannot be undone reliably");
		}
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		String number = first_row(c).get(0);
		open_edit(c, number);
		String original = c.selected(c.verify_complaint_status());
		List<String> options = c.all_options_of(c.verify_complaint_status());
		String other = null;
		for (String o : options) {
			if (!o.equals(original)) {
				other = o;
				break;
			}
		}
		Assert.assertNotNull(other, "No other status to choose: " + options);
		c.Select_complaint_status(other);
		submit_and_wait(c);
		test.info("Status " + original + " -> " + other + " | " + said(c));
		Assert.assertTrue(c.is_list_page(), "Not back on the list. " + said(c));
		test.info("Row after the change: " + c.row_of(number));
		open_edit(c, number);
		c.Select_complaint_status(original);
		submit_and_wait(c);
		test.info("Status put back to " + original + ": " + c.is_list_page());
		test.pass("Status changed and restored");
	}

	// CM23: the print icon (browser print preview is replaced): the page asks to print or opens a print window that shows
	// "Complaint Details - <number>". Only with RUN_PRINT_TEST.
	@Test
	public void CM23_print() {
		test = extent.createTest("CM23 Print");
		if (!RUN_PRINT_TEST) {
			throw new SkipException("RUN_PRINT_TEST is false: the print preview is a browser dialog");
		}
		Q_Complaint_Cls c = new Q_Complaint_Cls(driver);

		String number = first_row(c).get(0);
		String info = c.Click_print(number);
		test.info(info);
		Assert.assertTrue(info.contains("print called in the page: true") || info.contains("Complaint Details"),
				"The print icon did nothing: " + info);
		test.pass("Print icon works: " + info);
	}
}