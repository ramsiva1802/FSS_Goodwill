package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.D_Hierarchy_Cls;
import PagePkg.A_Login_Cls;
import UtillPkg.Excel_Util;

/*
 * NOTES - Hierarchy_BulkEntry_Test
 * --------------------------------
 * BULK DATA ENTRY: creates one hierarchy for every row of an Excel sheet.
 * The test runs once per JOB (see jobs() below): SALES ORGANIZATION first, then PLANT.
 * Excel columns (0-indexed), row 0 is the header:
 *   0 Hierarchy Code   1 Hierarchy Name   2 Address 1   3 GSTIN Number
 *   4 Email            5 Phone Number     6 Zip Code     7 CEO          8 Billing Code
 * Country and State are the same for every row (constants below); the Hierarchy Type comes from the job.
 * Every row is a full cycle: open the list -> Create -> fill -> Submit -> back on the list.
 * A failed row is written to the report and the loop goes on with the next row.
 * With VERIFY_IN_LIST the new code is also searched in the list to confirm it was entered.
 */
public class ZB_Hierarchy_salesorganisation_Dataentry extends BaseCls {

	static final String SHEET = "Sheet1";

	// the same for every row of every job
	static final String COUNTRY = "India";
	static final String STATE = "Kerala";

	// true = after every submit, search the code in the list and confirm it is found exactly once
	static final boolean VERIFY_IN_LIST = true;

	// the jobs: Excel file (close it in Excel before running) and the hierarchy type for all its rows.
	// A parent type goes first. To skip a job, delete or comment out its line.
	@DataProvider(name = "jobs")
	public Object[][] jobs() {
		return new Object[][] {
				{ "D:\\Idrive\\Ramprakash\\Project\\SFA\\Saas\\excel\\Hierarchy_Data.xlsx", "SALES ORGANIZATION" },
				{ "D:\\Idrive\\Ramprakash\\Project\\SFA\\Saas\\excel\\Hierarchy_Data_Plant.xlsx", "PLANT" } };
	}

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
		login.Login(admin_user, admin_pass);
		Assert.assertTrue(login.login_success(), "Could not log in for the bulk entry");
	}

	// opens Company Info > Hierarchy Details from the dashboard
	private void go_to_hierarchy() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		dash.Click_submenu_containing("Company Info", "Hierarchy");
		WaitUtil.settle(driver, 1500);
	}

	@Test(dataProvider = "jobs")
	public void hierarchy_bulk_creation(String excel, String type) throws Exception {

		test = extent.createTest("Hierarchy Bulk Creation: " + type);

		D_Hierarchy_Cls h = new D_Hierarchy_Cls(driver);
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100));

		// ---------------- Step 1: navigate to Hierarchy Details ----------------
		test.info("Navigating to Company Info > Hierarchy Details");
		go_to_hierarchy();
		wait.until(d -> h.is_list_page());

		// ---------------- Step 2: read the Excel ----------------
		int rowcount = Excel_Util.getRowCount(excel, SHEET);
		test.info("Excel file: " + excel);
		test.info("Excel Row Count: " + rowcount);
		System.out.println("rowcount = " + rowcount);

		int passCount = 0;
		int failCount = 0;

		// ---------------- Step 3: one full cycle per Excel row ----------------
		for (int i = 1; i <= rowcount; i++) {

			String code = Excel_Util.getCellData(excel, SHEET, i, 0).trim();
			if (code.isEmpty()) {
				test.warning("Row " + i + " skipped - the hierarchy code is empty");
				continue;
			}

			try {
				// every row starts from the list screen
				if (!h.is_list_page()) {
					go_to_hierarchy();
					wait.until(d -> h.is_list_page());
				}

				String name = Excel_Util.getCellData(excel, SHEET, i, 1);
				String address = Excel_Util.getCellData(excel, SHEET, i, 2);
				String gstin = Excel_Util.getCellData(excel, SHEET, i, 3);
				String email = Excel_Util.getCellData(excel, SHEET, i, 4);
				String phone = Excel_Util.getCellData(excel, SHEET, i, 5);
				String zip = Excel_Util.getCellData(excel, SHEET, i, 6);
				String ceo = Excel_Util.getCellData(excel, SHEET, i, 7);
				String billing = Excel_Util.getCellData(excel, SHEET, i, 8).trim();

				// ========== CYCLE START: Create ==========
				test.info("Row " + i + ": clicking Create");
				h.Click_create();
				wait.until(d -> h.is_create_form());

				test.info("Row " + i + ": filling " + code + " | " + name);
				test.info("Country: " + COUNTRY + " | State: " + STATE + " | Hierarchy Type: " + type);
				h.Enter_hierarchy(type, code, name, address, gstin, email, phone, zip, ceo, COUNTRY, STATE, billing);

				// ========== CYCLE END: Submit ==========
				h.Click_submit();

				// wait for whichever comes first: the list screen (accepted) or a validation message (rejected)
				wait.until(d -> h.is_list_page() || !h.validation_messages().isEmpty());

				if (!h.is_list_page()) {
					test.fail("Row " + i + " (" + code + ") was rejected. Messages: " + h.validation_messages());
					failCount++;
					go_to_hierarchy();   // leave the form, the next row starts from the list
					continue;
				}

				// ---------------- confirm it is in the list ----------------
				if (VERIFY_IN_LIST) {
					h.Search_hierarchy("", code, "");
					WaitUtil.settle(driver, 1500);
					if (h.result_count() != 1) {
						test.fail("Row " + i + " (" + code + ") was submitted but is not in the list exactly once. Rows: "
								+ h.result_texts());
						failCount++;
						continue;
					}
				}

				test.pass("Row " + i + ": hierarchy " + code + " entered (billing code " + h.last_billing_code + ")");
				passCount++;

			} catch (Exception e) {
				test.fail("Row " + i + " (" + code + ") stopped with an error: " + e.getMessage());
				failCount++;
				try {
					go_to_hierarchy();   // recover: the next row starts from the list
				} catch (Exception ignored) {
					// the next row will try again
				}
			}
		}

		test.info("Summary: " + passCount + " passed, " + failCount + " failed out of " + rowcount);
		System.out.println("Summary: " + passCount + " passed, " + failCount + " failed out of " + rowcount);

		Assert.assertEquals(failCount, 0, failCount + " of " + rowcount + " hierarchies were not entered. See the report.");
	}
}