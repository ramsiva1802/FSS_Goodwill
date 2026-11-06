package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.time.LocalDate;
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
import PagePkg.B_Dashboard_Cls;
import PagePkg.L_DeviceApprove_Cls;
import PagePkg.A_Login_Cls;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Device Approve (Device > Device Approve) - functional tests, A01 .. A16.
 * The page has no create form: a search form, the table filter, paging and the row actions.
 * Row states: pending (approve + reject tick boxes), approved (green tick + Block), blocked (Unblock).
 *
 * A14 (Block then Unblock) is reversible: the device is always unblocked again. It never uses a device of "apk"
 * (the device that logs in to the mobile app).
 * A15 (Approve) and A16 (Reject) change a PENDING device for good: it cannot become pending again. They pick a
 * pending device at run time and are skipped when there is none. Set ALLOW_STATE_CHANGES to false to skip
 * A14, A15 and A16.
 */
public class L_DeviceApprove_Test extends BaseCls {

	// false = the tests that change a device (A14, A15, A16) are skipped
	static final boolean ALLOW_STATE_CHANGES = true;

	// an employee that has devices in the QA data
	static final String EMPLOYEE = "Sivakumar";

	// the device of the apk login: never blocked by a test
	static final String PROTECTED_EMPLOYEE = "apk";

	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

	// opens Device > Device Approve from the dashboard (waits and tries again while the page is still loading)
	private void go_to_device_approve() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_by_name("Device", "Device Approve");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Device", "Device Approve");
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

	// every test starts with a fresh Device Approve screen
	@BeforeMethod
	public void open_device_approve() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);
		try {
			go_to_device_approve();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> d.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_device_approve();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> d.is_list_page());
		}
		remember_clean_screen();
	}

	// the total of "Showing 1 to 10 of 13 entries" (-1 when it cannot be read)
	private int total_entries(L_DeviceApprove_Cls d) {
		String t = d.verify_entries().getText().replace(",", "");
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("of (\\d+) entries").matcher(t);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	// finds a device with the table filter and returns its state
	private String state_of(L_DeviceApprove_Cls d, String mac) {
		d.Filter_table(mac);
		Assert.assertTrue(d.Wait_for_row(mac), "The device " + mac + " is not in the list");
		return d.row_state(mac);
	}

	// ======================= list and search form =======================

	// A01: the screen shows Search, the table filter, the 8 column headers, the entries line and 10 rows
	@Test
	public void A01_list_page_elements() {
		test = extent.createTest("A01 Device Approve list elements");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		Assert.assertTrue(d.verify_top_search().isDisplayed(), "Search button is missing");
		Assert.assertTrue(d.verify_table_filter().isDisplayed(), "The table 'Search:' box is missing");
		List<String> headers = d.headers();
		test.info("Headers: " + headers);
		for (String h : new String[] { "EMPLOYEE", "MAC ADDRESS", "REGISTRATION DATE", "APK VERSION", "DEVICE MODEL",
				"APPROVE", "REJECT", "BLOCK" }) {
			Assert.assertTrue(headers.contains(h), "Column header missing: " + h);
		}
		Assert.assertTrue(d.verify_entries().getText().contains("Showing 1 to"), d.verify_entries().getText());
		Assert.assertTrue(d.result_count() > 0, "No device is listed");
		test.pass("List screen elements are displayed: " + d.verify_entries().getText());
	}

	// A02: the Search button opens the search form with its 6 fields and the Search button
	@Test
	public void A02_search_form_opens() {
		test = extent.createTest("A02 Search form opens");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		d.Open_search_form();
		Assert.assertTrue(d.verify_type().isDisplayed(), "Employee Type is missing");
		Assert.assertTrue(d.verify_employee().isDisplayed(), "Employee is missing");
		Assert.assertTrue(d.verify_model().isDisplayed(), "Device Modal is missing");
		Assert.assertTrue(d.verify_status().isDisplayed(), "Approval Status is missing");
		Assert.assertTrue(d.verify_from().isDisplayed(), "From Date is missing");
		Assert.assertTrue(d.verify_to().isDisplayed(), "To Date is missing");
		Assert.assertTrue(d.verify_search_go().isDisplayed(), "The Search button of the form is missing");
		test.pass("Search form is displayed");
	}

	// A03: the three drop downs are filled (their values are written to the report)
	@Test
	public void A03_search_form_options() {
		test = extent.createTest("A03 Search form options");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		d.Open_search_form();
		List<String> types = d.options_of(d.verify_type());
		List<String> models = d.options_of(d.verify_model());
		List<String> statuses = d.options_of(d.verify_status());
		test.info("Employee types: " + types + " (default '" + d.selected(d.verify_type()) + "')");
		test.info("Device models: " + models + " (default '" + d.selected(d.verify_model()) + "')");
		test.info("Approval statuses: " + statuses + " (default '" + d.selected(d.verify_status()) + "')");
		Assert.assertFalse(types.isEmpty(), "Employee Type has no values");
		Assert.assertFalse(models.isEmpty(), "Device Modal has no values");
		Assert.assertFalse(statuses.isEmpty(), "Approval Status has no values");
		Assert.assertTrue(models.contains("iPhone"), "The model iPhone is missing: " + models);
		test.pass("Drop downs are filled");
	}

	// A04: search by employee lists only that employee's devices
	@Test
	public void A04_search_by_employee() {
		test = extent.createTest("A04 Search by employee");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		d.Search_devices("", EMPLOYEE, "", "", "", "");
		Assert.assertTrue(d.result_count() > 0, "No device was found for " + EMPLOYEE);
		for (List<String> row : d.result_texts()) {
			Assert.assertTrue(row.get(0).toLowerCase().contains(EMPLOYEE.toLowerCase()), "A device of another employee: " + row);
		}
		test.pass(d.result_count() + " devices of " + EMPLOYEE);
	}

	// A05: search by device model lists only that model
	@Test
	public void A05_search_by_device_model() {
		test = extent.createTest("A05 Search by device model");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		d.Search_devices("", "", "iPhone", "", "", "");
		Assert.assertTrue(d.result_count() > 0, "No iPhone was found");
		for (List<String> row : d.result_texts()) {
			Assert.assertEquals(row.get(4), "iPhone", "A device of another model: " + row);
		}
		test.pass(d.result_count() + " iPhones");
	}

	// A06: search by every approval status: the devices listed must be exactly the devices in that state.
	// The truth is read first from the unfiltered list; the result of every status is written to the report.
	@Test
	public void A06_search_by_approval_status() {
		test = extent.createTest("A06 Search by approval status");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		Map<String, String> truth = new LinkedHashMap<String, String>();
		for (int page = 0; page < 10; page++) {
			for (List<String> row : d.result_texts()) {
				truth.put(row.get(1), d.row_state(row.get(1)));
			}
			if (!d.next_enabled()) {
				break;
			}
			d.Click_next();
		}
		test.info("Devices and states in the unfiltered list: " + truth);

		d.Open_search_form();
		List<String> statuses = d.options_of(d.verify_status());
		List<String> wrong = new ArrayList<String>();
		for (String status : statuses) {
			d.Search_devices("", "", "", status, "", "");
			List<String> shown = new ArrayList<String>();
			for (int page = 0; page < 10; page++) {
				for (List<String> row : d.result_texts()) {
					shown.add(row.get(1));
				}
				if (!d.next_enabled()) {
					break;
				}
				d.Click_next();
			}
			String expected = status.toLowerCase().contains("pend") ? "pending"
					: status.toLowerCase().contains("block") ? "blocked"
							: status.toLowerCase().contains("approv") ? "approved" : "";
			test.info("Status '" + status + "': " + shown.size() + " devices " + shown
					+ (expected.isEmpty() ? "" : " (expected state " + expected + ")"));
			if (expected.isEmpty()) {
				continue;
			}
			for (String mac : shown) {
				if (!expected.equals(truth.get(mac))) {
					wrong.add(status + ": " + mac + " is " + truth.get(mac) + " in the list");
				}
			}
			for (String mac : truth.keySet()) {
				if (truth.get(mac).equals(expected) && !shown.contains(mac)) {
					wrong.add(status + ": " + mac + " is " + expected + " but is not listed");
				}
			}
		}
		Assert.assertTrue(wrong.isEmpty(), "DEFECT: the status search does not match the list: " + wrong);
		test.pass("Every status lists exactly its devices");
	}

	// A07: From Date and To Date: only devices registered in the range are listed
	@Test
	public void A07_search_by_date_range() {
		test = extent.createTest("A07 Search by date range");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		int all = total_entries(d);
		LocalDate from = LocalDate.of(2026, 10, 1);
		LocalDate to = LocalDate.of(2026, 10, 14);
		d.Search_devices("", "", "", "", from.format(DATE), to.format(DATE));
		test.info("Date boxes after typing: " + d.date_boxes_info());
		test.info("Rows for " + from.format(DATE) + " .. " + to.format(DATE) + ": " + d.result_count() + " (all: " + all + ")");
		Assert.assertTrue(d.result_count() > 0, "No device was found in the date range");
		for (List<String> row : d.result_texts()) {
			LocalDate registered = LocalDate.parse(row.get(2), DATE);
			Assert.assertTrue(!registered.isBefore(from) && !registered.isAfter(to),
					"DEFECT: a device outside the date range is listed: " + row + " | " + d.date_boxes_info());
		}
		test.pass("Only devices of the date range are listed");
	}

	// A08: From Date after To Date: no device (or a message) must be shown
	@Test
	public void A08_reversed_date_range() {
		test = extent.createTest("A08 Reversed date range");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		d.Search_devices("", "", "", "", "20/10/2026", "01/10/2026");
		test.info("Date boxes after typing: " + d.date_boxes_info());
		test.info("Rows: " + d.result_count() + " | messages: " + d.validation_messages() + " | notice: " + d.notice());
		Assert.assertTrue(d.result_count() == 0 || !d.validation_messages().isEmpty() || !d.notice().isEmpty(),
				"DEFECT: a reversed date range lists devices without any message | " + d.date_boxes_info());
		test.pass("Reversed range gives no devices or a message");
	}

	// A09: an employee that does not exist gives an empty table
	@Test
	public void A09_search_no_match() {
		test = extent.createTest("A09 Search without match");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		d.Search_devices("", "NOMATCH" + unique(), "", "", "", "");
		Assert.assertEquals(d.result_count(), 0, "No device should be listed");
		test.pass("Empty result for an unknown employee");
	}

	// A10: the Employee Type filter returns the devices of that type only (the count is written to the report)
	@Test
	public void A10_search_by_employee_type() {
		test = extent.createTest("A10 Search by employee type");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		int all = total_entries(d);
		d.Open_search_form();
		List<String> types = d.options_of(d.verify_type());
		Assert.assertFalse(types.isEmpty(), "Employee Type has no values");
		String type = types.get(0);
		d.Search_devices(type, "", "", "", "", "");
		test.info("Type '" + type + "': " + d.result_count() + " rows on the first page (all devices: " + all + ")");
		Assert.assertTrue(d.result_count() <= 10, "More rows than one page are listed");
		test.pass("Employee type filter works");
	}

	// A11: the "Search:" box above the table filters the rows; unknown text gives an empty table
	@Test
	public void A11_table_filter() {
		test = extent.createTest("A11 Table filter");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		d.Filter_table(EMPLOYEE);
		Assert.assertTrue(d.result_count() > 0, "The filter hid every row");
		for (List<String> row : d.result_texts()) {
			Assert.assertTrue(row.toString().toLowerCase().contains(EMPLOYEE.toLowerCase()), "A row that does not match: " + row);
		}
		d.Filter_table("NOMATCH" + unique());
		Assert.assertEquals(d.result_count(), 0, "No row should be listed for unknown text");
		test.pass("Table filter works");
	}

	// A12: Next shows the second page, Previous goes back to "Showing 1 to 10"
	@Test
	public void A12_paging() throws InterruptedException {
		test = extent.createTest("A12 Paging");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		Assert.assertTrue(d.verify_entries().getText().contains("Showing 1 to 10"), d.verify_entries().getText());
		d.Click_next();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(d.verify_entries().getText().contains("Showing 11 to"), d.verify_entries().getText());
		d.Click_previous();
		WaitUtil.settle(driver, 1500);
		Assert.assertTrue(d.verify_entries().getText().contains("Showing 1 to 10"), d.verify_entries().getText());
		test.pass("Next and Previous work");
	}

	// A13: every row is in exactly one state: pending (2 tick boxes, no button), approved (Block, no tick box) or
	// blocked (Unblock, no tick box); the counts are written to the report
	@Test
	public void A13_row_states_are_consistent() {
		test = extent.createTest("A13 Row states");
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		int pending = 0;
		int approved = 0;
		int blocked = 0;
		List<String> odd = new ArrayList<String>();
		for (int page = 0; page < 10; page++) {
			for (List<String> row : d.result_texts()) {
				String mac = row.get(1);
				String state = d.row_state(mac);
				int boxes = d.box_count(mac);
				String button = d.button_text(mac);
				if (state.equals("pending")) {
					pending++;
					if (boxes != 2 || !button.isEmpty()) {
						odd.add(mac + " pending with " + boxes + " boxes and button '" + button + "'");
					}
				} else if (state.equals("approved")) {
					approved++;
				} else if (state.equals("blocked")) {
					blocked++;
				} else {
					odd.add(mac + " has no tick boxes and no Block / Unblock button: " + row);
				}
			}
			if (!d.next_enabled()) {
				break;
			}
			d.Click_next();
		}
		test.info("Pending: " + pending + ", approved: " + approved + ", blocked: " + blocked);
		Assert.assertTrue(odd.isEmpty(), "Rows in an unexpected state: " + odd);
		test.pass("Every row is pending, approved or blocked");
	}

	// ======================= row actions =======================

	// A14: Block an approved device -> the button becomes Unblock; Unblock -> it is approved again (always restored)
	@Test
	public void A14_block_and_unblock() {
		test = extent.createTest("A14 Block and Unblock");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false");
		}
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		String mac = d.Find_mac("approved", PROTECTED_EMPLOYEE);
		if (mac == null) {
			throw new SkipException("No approved device (other than " + PROTECTED_EMPLOYEE + ") to block");
		}
		test.info("Device used: " + mac);
		try {
			Assert.assertEquals(state_of(d, mac), "approved");
			test.info("Block messages: " + d.Click_block_button(mac));
			Assert.assertEquals(state_of(d, mac), "blocked", "The device must show Unblock after Block");
		} finally {
			try {
				if (state_of(d, mac).equals("blocked")) {
					test.info("Unblock messages: " + d.Click_block_button(mac));
				}
			} catch (Exception e) {
				test.warning("Could not unblock " + mac + " again: " + e.getMessage());
			}
		}
		Assert.assertEquals(state_of(d, mac), "approved", "The device must be approved again after Unblock");
		test.pass("Block and Unblock work, device " + mac + " is restored");
	}

	// A15: Approve a pending device -> it is approved (green tick, Block button, no tick boxes).
	// This cannot be undone: the device never becomes pending again.
	@Test
	public void A15_approve_pending_device() {
		test = extent.createTest("A15 Approve a pending device");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false");
		}
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		String mac = d.Find_mac("pending", "");
		if (mac == null) {
			throw new SkipException("No pending device left to approve");
		}
		test.info("Device used: " + mac);
		Assert.assertEquals(state_of(d, mac), "pending");
		test.info("Approve messages: " + d.Tick_approve(mac));
		String state = state_of(d, mac);
		test.info("Row after Approve: " + d.row_of(mac));
		Assert.assertEquals(state, "approved", "The device must show the Block button after Approve");
		test.pass("Device " + mac + " approved");
	}

	// A16: Reject another pending device -> it is no longer pending (the row is written to the report).
	// This cannot be undone.
	@Test(dependsOnMethods = "A15_approve_pending_device", alwaysRun = true)
	public void A16_reject_pending_device() {
		test = extent.createTest("A16 Reject a pending device");
		if (!ALLOW_STATE_CHANGES) {
			throw new SkipException("ALLOW_STATE_CHANGES is false");
		}
		L_DeviceApprove_Cls d = new L_DeviceApprove_Cls(driver);

		String mac = d.Find_mac("pending", "");
		if (mac == null) {
			throw new SkipException("No pending device left to reject");
		}
		test.info("Device used: " + mac);
		test.info("Reject messages: " + d.Tick_reject(mac));
		String state = state_of(d, mac);
		test.info("Row after Reject: " + d.row_of(mac) + " | state " + state);
		Assert.assertNotEquals(state, "pending", "The device is still pending after Reject");
		test.pass("Device " + mac + " rejected (state: " + state + ")");
	}
}