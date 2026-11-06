package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.K_Customer_Cls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;
import UtillPkg.Excel_Util;

/*
 * Customer bulk entry: every row of CustomerData.xlsx is entered through the Create form.
 * Excel columns: 0 Customer Code, 1 Customer Name, 2 GSTIN, 3 Store Name, 4 Mobile Number, 5 Route,
 * 6 Customer Group, 7 Customer Class, 8 Payment Mode, 9 Address, 10 City, 11 Zip Code, 12 Country, 13 State,
 * 14 Result (written by the test).
 * Result values: Created / Already existed / Rejected: <message> / Error: <reason>.
 * Close the Excel file before running (the test writes the Result column into it).
 * Customers cannot be deleted: the BKC customers stay in the list after the run.
 */
public class ZE_Customer_BulkEntry_Test extends BaseCls {

	// the data file
	static final String EXCEL = "C:\\Users\\SILLAP-003\\Documents\\CustomerData.xlsx";
	static final String SHEET = "Customer";
	static final int RESULT_COLUMN = 14;

	// the form keys of K_Customer_Cls.Fill(map) in the order of the Excel columns 0 .. 13
	static final String[] KEYS = { "code", "name", "gstin", "store", "mobile", "route", "group", "class", "payment",
			"address", "city", "zip", "country", "state" };

	// true = after every submit, search the code in the list and confirm it is found
	static final boolean VERIFY_IN_LIST = true;

	// what the last Submit showed
	String last_alert = "";
	String last_notice = "";

	// runs ONCE: fresh session, login through the form
	@BeforeClass
	public void login_first() {
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
		Assert.assertTrue(login.login_success(), "Could not log in for the bulk entry");
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

	// writes the outcome of a row into the Result column (a locked Excel file must not fail the test)
	private void write_result(int row, String text) {
		try {
			Excel_Util.setCellData(EXCEL, SHEET, row, RESULT_COLUMN, text);
		} catch (Exception e) {
			System.out.println("Could not write the result of row " + row + " into Excel: " + e.getMessage());
		}
	}

	// presses Submit and waits for: back on the list, or a message, or a pop-up / alert.
	// The alert / pop-up text is kept in last_alert / last_notice and then closed.
	private void submit_and_wait(K_Customer_Cls c) throws InterruptedException {
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
			// nothing appeared: the caller checks the screen itself
		}
		last_notice = c.notice();
		c.Close_notice();
		WaitUtil.settle(driver, 800);
		String late = c.Accept_alert();
		if (!late.isEmpty()) {
			last_alert = last_alert + " " + late;
		}
	}

	@Test
	public void customer_bulk_creation() throws Exception {

		test = extent.createTest("Customer Bulk Creation");

		K_Customer_Cls c = new K_Customer_Cls(driver);
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));

		// ---------------- Step 1: open the Customer list ----------------
		test.info("Navigating to Customer Details > Customer");
		go_to_customer();
		wait.until(d -> c.is_list_page());

		// ---------------- Step 2: read the Excel ----------------
		int rowcount = Excel_Util.getRowCount(EXCEL, SHEET);
		test.info("Excel file: " + EXCEL);
		test.info("Excel Row Count: " + rowcount);
		System.out.println("rowcount = " + rowcount);

		int created = 0;
		int skipped = 0;
		int failed = 0;

		// ---------------- Step 3: one full cycle per Excel row ----------------
		for (int i = 1; i <= rowcount; i++) {

			String code = Excel_Util.getCellData(EXCEL, SHEET, i, 0).trim();
			if (code.isEmpty()) {
				test.warning("Row " + i + " skipped - the customer code is empty");
				continue;
			}

			try {
				// every row starts from the list screen
				if (!c.is_list_page()) {
					go_to_customer();
					wait.until(d -> c.is_list_page());
				}

				// a customer that is already in the list is not entered twice
				c.Filter_table(code);
				if (c.Wait_for_row(code)) {
					test.info("Row " + i + ": " + code + " is already in the list, skipped");
					write_result(i, "Already existed");
					skipped++;
					continue;
				}

				// read the row
				Map<String, String> values = new LinkedHashMap<String, String>();
				for (int col = 0; col < KEYS.length; col++) {
					values.put(KEYS[col], Excel_Util.getCellData(EXCEL, SHEET, i, col).trim());
				}

				// create
				c.Click_create();
				wait.until(d -> c.is_form_screen());
				c.Fill(values);
				submit_and_wait(c);

				// a message under a field (or a pop-up while the form stays open) = the row was rejected
				if (!c.is_list_page()) {
					String why = c.validation_messages() + " " + last_notice + " " + last_alert;
					test.fail("Row " + i + ": " + code + " rejected: " + why);
					write_result(i, "Rejected: " + why.trim());
					failed++;
					go_to_customer();
					continue;
				}

				// confirm that the customer is in the list
				if (VERIFY_IN_LIST) {
					c.Filter_table(code);
					if (!c.Wait_for_row(code)) {
						test.fail("Row " + i + ": " + code + " was submitted but is not in the list");
						write_result(i, "Not found in the list after submit");
						failed++;
						continue;
					}
				}

				write_result(i, "Created");
				test.pass("Row " + i + ": " + code + " created");
				created++;

			} catch (Exception e) {
				String reason = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).split("\n")[0];
				test.fail("Row " + i + ": " + code + " error - " + reason);
				write_result(i, "Error: " + reason);
				failed++;
				try {
					c.Accept_alert();
					go_to_customer();
				} catch (Exception ignore) {
					// the next row tries again from the list screen
				}
			}
		}

		// ---------------- Step 4: summary ----------------
		String summary = "Created: " + created + ", already existed: " + skipped + ", failed: " + failed
				+ " (Excel rows: " + rowcount + ")";
		test.info(summary);
		System.out.println(summary);
		Assert.assertEquals(failed, 0, "Some rows were not created. " + summary);
	}
}