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
import PagePkg.G_StockItem_Cls;
import UtillPkg.Excel_Util;

/*
 * NOTES - ZC_StockItem_BulkEntry_Test
 * --------------------------------
 * BULK DATA ENTRY: creates one stock item for every row of StockItemData.xlsx (sheet "StockItem").
 * Excel columns (0-indexed), row 0 is the header:
 *   0 Stock Code   1 Stock Name   2 Alias Name   3 HSN Code   4 Stock MRP   5 Min Sale Qty
 *   6 Category     7 Sub Category   8 Brand   9 Unit   10 Stock Type   11 Result (written by the test)
 * Only the 11 mandatory fields are entered (no attributes, tax profile, base unit or conversion factor).
 * Every row is a full cycle: list -> Create -> fill -> Submit -> back on the list.
 * With VERIFY_IN_LIST the new code is also searched in the list to confirm it was entered.
 * A failed row is written to the report and into the Result column, and the loop goes on with the next row.
 * A row that was already entered (the code is already in the list) is skipped, so the test can be re-run
 * after an interruption and carries on with the missing rows.
 * Stock items cannot be deleted, so every row creates a permanent item. Close the Excel file before running.
 */
public class ZC_StockItem_BulkEntry_Test extends BaseCls {

	// the data file (close it in Excel before running)
	static final String EXCEL = "C:\\Users\\SILLAP-003\\Documents\\StockItemData.xlsx";
	static final String SHEET = "StockItem";
	static final int RESULT_COLUMN = 11;

	// true = after every submit, search the code in the list and confirm it is found exactly once
	static final boolean VERIFY_IN_LIST = true;

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

	// opens Stock Item > Stock Item from the dashboard
	private void go_to_stock_item() throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);

		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		try {
			dash.Click_submenu_by_name("Stock Item", "Stock Item");
		} catch (NoSuchElementException e) {
			dash.Click_home();
			dash.Click_submenu_containing("Stock Item", "Stock Item");
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

	@Test
	public void stock_item_bulk_creation() throws Exception {

		test = extent.createTest("Stock Item Bulk Creation");

		G_StockItem_Cls s = new G_StockItem_Cls(driver);
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));

		// ---------------- Step 1: open the Stock Item list ----------------
		test.info("Navigating to Stock Item > Stock Item");
		go_to_stock_item();
		wait.until(d -> s.is_list_page());

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
				test.warning("Row " + i + " skipped - the stock code is empty");
				continue;
			}

			try {
				// every row starts from the list screen
				if (!s.is_list_page()) {
					go_to_stock_item();
					wait.until(d -> s.is_list_page());
				}

				// an item that is already in the list is not entered twice
				s.Search_stock(code, "", "");
				WaitUtil.settle(driver, 2000);
				if (s.Stock_codes().contains(code)) {
					test.info("Row " + i + ": " + code + " is already in the list, skipped");
					write_result(i, "Already existed");
					skipped++;
					continue;
				}

				String name = Excel_Util.getCellData(EXCEL, SHEET, i, 1);
				String alias = Excel_Util.getCellData(EXCEL, SHEET, i, 2);
				String hsn = Excel_Util.getCellData(EXCEL, SHEET, i, 3);
				String mrp = Excel_Util.getCellData(EXCEL, SHEET, i, 4);
				String min_qty = Excel_Util.getCellData(EXCEL, SHEET, i, 5);
				String category = Excel_Util.getCellData(EXCEL, SHEET, i, 6);
				String sub_category = Excel_Util.getCellData(EXCEL, SHEET, i, 7);
				String brand = Excel_Util.getCellData(EXCEL, SHEET, i, 8);
				String unit = Excel_Util.getCellData(EXCEL, SHEET, i, 9);
				String stock_type = Excel_Util.getCellData(EXCEL, SHEET, i, 10);

				// ========== CYCLE START: Create ==========
				s.Click_create();
				wait.until(d -> s.is_form_screen());

				s.Enter_item(code, name, alias, hsn, mrp, min_qty, category, sub_category, brand, unit, stock_type);

				// ========== CYCLE END: Submit ==========
				s.Click_submit();

				// wait for whichever comes first: the list screen (accepted) or a validation message (rejected)
				wait.until(d -> s.is_list_page() || !s.validation_messages().isEmpty());

				if (!s.is_list_page()) {
					String why = "Rejected: " + s.validation_messages();
					test.fail("Row " + i + " (" + code + ") " + why);
					write_result(i, why);
					failed++;
					go_to_stock_item();   // leave the form, the next row starts from the list
					continue;
				}

				// ---------------- confirm it is in the list ----------------
				if (VERIFY_IN_LIST) {
					s.Search_stock(code, "", "");
					if (!s.Wait_for_row(code, 8)) {
						String why = "Submitted but not found in the list";
						test.fail("Row " + i + " (" + code + ") " + why);
						write_result(i, why);
						failed++;
						continue;
					}
				}

				test.pass("Row " + i + ": stock item " + code + " (" + name + ") entered");
				write_result(i, "Created");
				created++;

			} catch (Exception e) {
				String why = "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
				test.fail("Row " + i + " (" + code + ") stopped with an error. " + why);
				write_result(i, why.length() > 250 ? why.substring(0, 250) : why);
				failed++;
				try {
					go_to_stock_item();   // recover: the next row starts from the list
				} catch (Exception ignored) {
					// the next row will try again
				}
			}
		}

		String summary = "Summary: " + created + " created, " + skipped + " already existed, " + failed
				+ " failed out of " + rowcount;
		test.info(summary);
		System.out.println(summary);

		Assert.assertEquals(failed, 0, failed + " of " + rowcount + " stock items were not entered. See the report.");
	}
}