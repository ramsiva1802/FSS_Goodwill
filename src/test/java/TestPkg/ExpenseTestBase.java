package TestPkg;

import UtillPkg.TestEnv;
import UtillPkg.WaitUtil;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;

import BasePkg.BaseCls;
import PagePkg.A_Login_Cls;
import PagePkg.B_Dashboard_Cls;

/*
 * What the Expense Approval, Attendance Expense Report and link tests have in common: the login of the web user,
 * closing extra tabs, opening a screen from the menu, and reading the cells of a result row by column name.
 * A column name is matched exactly first, then by its beginning ("EXPENSE AMOUNT" finds "EXPENSE AMOUNT (₹)").
 */
public abstract class ExpenseTestBase extends BaseCls {

	// login of the web user (a test login of this project)
	static final String WEB_USER = TestEnv.get("web.user", "your-web-user");
	static final String WEB_PASS = TestEnv.get("web.password", "your-web-password");

	static final String FROM_WIDE = "01/01/2026";
	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	static final double TOLERANCE = 0.011;

	protected String main_tab;

	protected String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
	}

	protected String today() {
		return LocalDate.now().format(DATE);
	}

	// runs ONCE: fresh session, login as the web user
	@BeforeClass
	public void login_first() {
		login_again();
	}

	// the browser window may have been closed (by hand or by the site): use another open window, or start a new browser
	protected void ensure_window() {
		try {
			Set<String> handles = driver.getWindowHandles();
			if (handles.isEmpty()) {
				restartBrowser();
				return;
			}
			try {
				driver.getWindowHandle();
			} catch (org.openqa.selenium.NoSuchWindowException e) {
				driver.switchTo().window(handles.iterator().next());
			}
		} catch (org.openqa.selenium.WebDriverException e) {
			restartBrowser();
		}
	}

	protected void login_again() {
		ensure_window();
		try {
			driver.get(url);
			driver.manage().deleteAllCookies();
		} catch (org.openqa.selenium.WebDriverException e) {
			restartBrowser();
		}
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

	protected void clean_up() {
		ensure_window();
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

	// Home > top menu > the sub menu whose name contains 'part'
	private void go_to(String module, String part) {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_containing(module, part);
				} catch (java.util.NoSuchElementException e) {
					throw new org.openqa.selenium.NoSuchElementException("Menu " + module + " has no item containing '" + part
							+ "'. Items: " + dash.get_submenu_names(module));
				}
				WaitUtil.settle(driver, 800);
				return;
			} catch (ElementClickInterceptedException e) {
				if (attempt == 4) {
					throw e;
				}
				WaitUtil.settle(driver, 2500);
			}
		}
	}

	// opens a screen from the menu and waits until 'ready' is true; logs in again once when that fails
	protected void open_module(String module, String part, BooleanSupplier ready) {
		clean_up();
		try {
			go_to(module, part);
			new WebDriverWait(driver, Duration.ofSeconds(30), Duration.ofMillis(100)).until(x -> ready.getAsBoolean());
		} catch (Exception e) {
			login_again();
			go_to(module, part);
			new WebDriverWait(driver, Duration.ofSeconds(30), Duration.ofMillis(100)).until(x -> ready.getAsBoolean());
		}
	}

	// ======================= reading a record =======================

	// the key of a column in the record: exact name first, then a name that starts with it; null when there is none
	protected String column(Map<String, String> rec, String name) {
		if (rec.containsKey(name)) {
			return name;
		}
		for (String k : rec.keySet()) {
			if (k.startsWith(name)) {
				return k;
			}
		}
		return null;
	}

	protected boolean has(Map<String, String> rec, String name) {
		return column(rec, name) != null;
	}

	protected String val(Map<String, String> rec, String name) {
		String k = column(rec, name);
		if (k == null) {
			throw new AssertionError("The result table has no column '" + name + "'. Columns found: " + rec.keySet());
		}
		return rec.get(k);
	}

	protected String opt(Map<String, String> rec, String name) {
		String k = column(rec, name);
		return k == null ? "" : rec.get(k);
	}

	protected double num(Map<String, String> rec, String name) {
		return PagePkg.YA_ExpenseApproval_Cls.to_number(val(rec, name));
	}

	protected boolean blank(Map<String, String> rec, String name) {
		return val(rec, name).trim().isEmpty();
	}

	protected LocalDate date_in(Map<String, String> rec, String name) {
		return LocalDate.parse(val(rec, name).trim(), DATE);
	}

	// null when the cell is empty or is not a yyyy-MM-dd HH:mm:ss time
	protected LocalDateTime time_in(Map<String, String> rec, String name) {
		try {
			return LocalDateTime.parse(val(rec, name).trim(), DT);
		} catch (Exception e) {
			return null;
		}
	}

	// the employee of a record (the approval screen says EMPLOYEE, the report says SALES PERSON NAME)
	protected String employee_of(Map<String, String> rec) {
		String e = opt(rec, "EMPLOYEE");
		if (e.isEmpty() || column(rec, "EMPLOYEE").equals("EMPLOYEE TYPE")) {
			e = opt(rec, "SALES PERSON NAME");
		}
		return e;
	}

	// names a line in a message
	protected String who(Map<String, String> rec) {
		String d = rec.containsKey("EXPENSE DATE") ? rec.get("EXPENSE DATE") : opt(rec, "DATE");
		String n = opt(rec, "EXPENSE NUMBER");
		return (n.isEmpty() ? "" : n + " ") + employee_of(rec) + " " + d;
	}

	// identifies a line (every cell except the serial number and the action) so the same line can be found again
	protected String key(Map<String, String> rec) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : rec.entrySet()) {
			if (!e.getKey().equals("SL.NO") && !e.getKey().startsWith("ACTION")) {
				sb.append(e.getValue()).append('|');
			}
		}
		return sb.toString();
	}

	protected boolean close(double a, double b) {
		return Math.abs(a - b) <= TOLERANCE;
	}

	protected String shorten(List<String> bad) {
		return bad.size() > 12 ? bad.subList(0, 12) + " ... and " + (bad.size() - 12) + " more" : bad.toString();
	}

	// the lines of an extra filter must all be lines of the unfiltered list
	protected List<String> not_in(List<Map<String, String>> filtered, List<Map<String, String>> everything) {
		List<String> keys = new ArrayList<String>();
		for (Map<String, String> r : everything) {
			keys.add(key(r));
		}
		List<String> bad = new ArrayList<String>();
		for (Map<String, String> r : filtered) {
			if (!keys.contains(key(r))) {
				bad.add(who(r));
			}
		}
		return bad;
	}
}
