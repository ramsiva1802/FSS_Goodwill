package TestPkg;

import UtillPkg.WaitUtil;
import java.time.Duration;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;
import PagePkg.I_Route_Cls;
import UtillPkg.Excel_Util;

/*
 * Route bulk entry: every row of RouteData.xlsx is entered through the Create form.
 * Excel columns: 0 Route Code, 1 Route Name, 2 Hierarchy Type, 3 Hierarchy Name, 4 Result (written by the test).
 * Result values: Created / Already existed / Rejected: <message> / Error: <reason>.
 * Close the Excel file before running (the test writes the Result column into it).
 */
public class ZD_Route_BulkEntry_Test extends BaseCls {

	// the data file
	static final String EXCEL = "C:\\Users\\SILLAP-003\\Documents\\RouteData.xlsx";
	static final String SHEET = "Route";
	static final int RESULT_COLUMN = 4;

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
		login.Login(admin_user, admin_pass);
		Assert.assertTrue(login.login_success(), "Could not log in for the bulk entry");
	}

	// opens Customer Details > Route from the dashboard
	private void go_to_route() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		try {
			dash.Click_submenu_by_name("Customer Details", "Route");
		} catch (NoSuchElementException e) {
			dash.Click_home();
			dash.Click_submenu_containing("Customer Details", "Route");
		}
		WaitUtil.settle(driver, 1500);
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
	private void submit_and_wait(I_Route_Cls r) throws InterruptedException {
		last_alert = "";
		last_notice = "";
		r.Click_submit();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> {
				String a = r.Accept_alert();
				if (!a.isEmpty()) {
					last_alert = a;
					return true;
				}
				return r.is_list_page() || !r.validation_messages().isEmpty() || !r.notice().isEmpty();
			});
		} catch (Exception e) {
			// nothing appeared: the caller checks the screen itself
		}
		last_notice = r.notice();
		r.Close_notice();
		WaitUtil.settle(driver, 800);
		String late = r.Accept_alert();
		if (!late.isEmpty()) {
			last_alert = last_alert + " " + late;
		}
	}

	@Test
	public void route_bulk_creation() throws Exception {

		test = extent.createTest("Route Bulk Creation");

		I_Route_Cls r = new I_Route_Cls(driver);
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));

		// ---------------- Step 1: open the Route list ----------------
		test.info("Navigating to Customer Details > Route");
		go_to_route();
		wait.until(d -> r.is_list_page());

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
				test.warning("Row " + i + " skipped - the route code is empty");
				continue;
			}

			try {
				// every row starts from the list screen
				if (!r.is_list_page()) {
					go_to_route();
					wait.until(d -> r.is_list_page());
				}

				// a route that is already in the list is not entered twice
				r.Search_route(code, "", "");
				WaitUtil.settle(driver, 2000);
				if (r.Route_codes().contains(code)) {
					test.info("Row " + i + ": " + code + " is already in the list, skipped");
					write_result(i, "Already existed");
					skipped++;
					continue;
				}

				String name = Excel_Util.getCellData(EXCEL, SHEET, i, 1);
				String type = Excel_Util.getCellData(EXCEL, SHEET, i, 2);
				String hierarchy = Excel_Util.getCellData(EXCEL, SHEET, i, 3);

				// create
				r.Click_create();
				wait.until(d -> r.is_form_screen());
				r.Fill_route(code, name, type, hierarchy);
				submit_and_wait(r);

				// a message under a field (or a pop-up while the form stays open) = the row was rejected
				if (!r.is_list_page()) {
					String why = r.validation_messages() + " " + last_notice + " " + last_alert;
					test.fail("Row " + i + ": " + code + " rejected: " + why);
					write_result(i, "Rejected: " + why.trim());
					failed++;
					go_to_route();
					continue;
				}

				// confirm that the route is in the list
				if (VERIFY_IN_LIST) {
					r.Search_route(code, "", "");
					if (!r.Wait_for_row(code)) {
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
					r.Accept_alert();
					go_to_route();
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