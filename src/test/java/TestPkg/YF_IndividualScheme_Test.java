package TestPkg;

import UtillPkg.IndividualSchemeData;
import UtillPkg.IndividualSchemeData.Row;
import UtillPkg.TestEnv;
import UtillPkg.WaitUtil;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.ITest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.aventstack.extentreports.markuputils.MarkupHelper;

import BasePkg.BaseCls;
import PagePkg.A_Login_Cls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.YF_IndividualScheme_Cls;

/*
 * Individual Scheme (Scheme > Individual Scheme), logged in as the web user - DATA ENTRY of the 100 combinations that are still
 * to be created (the 8 combinations that exist already - Amount base with the Amount and Qty promotions at the four levels - are
 * not in the list):
 *   Amount base 7 promotions x 4 levels = 28,  Quantity base 9 x 4 = 36,  Weight base 9 x 4 = 36.
 * The list (codes, names, master data, values) is UtillPkg.IndividualSchemeData; its matrix is TestData\IndividualScheme_Matrix.csv.
 *
 *   IS00           inspects the create form: the options of the drop downs and, for each Applicable For level, the boxes the form
 *                  shows (reported, nothing saved).
 *   IS001 .. IS100 one execution per combination (a DataProvider): code and name, Promotion Base Type, Scheme Applicable Type
 *                  (+ Amount / Quantity for Each and Multiple), Applicable For, the dates, then the ROW: the category / subcategory /
 *                  item / menu card chosen from the dropdown, Min and Max of the base, the promotion value (and Per Unit, Free
 *                  Item + Qty where the form shows them), Is Active, Auto Apply, Submit. The scheme is then looked up in the
 *                  Individual Scheme listing. A code that is already listed is NOT created again (the run only verifies it).
 *
 * What each execution writes: the report (every step, the dropdown rows that were clicked, the box that got each value, the boxes of
 * the form before Submit, the messages after Submit, a screenshot when it failed) and a line in Reports\IndividualScheme_Results.csv.
 *
 * Run a part only (VM arguments):  -Dis.base=QTY  -Dis.promo=PER  -Dis.level=ITEM  -Dis.only=IND_QTY_PER_003_ITEM  -Dis.max=5
 *
 * ASSUMPTIONS (the task does not fix them): the free item of the Qty / Each Quantity / Multiple Quantity / Weight promotions is
 * leo-item1; the Per Unit of the Special Rate is 1; a menu card is searched by its name (Football / MN01) and, when the dropdown
 * has no such row, by its code (GOAT / Menu_1). A value goes only into the box whose placeholder / heading has the words of its
 * meaning (amount, percent, each, multiple, rate, weight); a box without a rule is reported as a WARNING and left empty.
 */
public class YF_IndividualScheme_Test extends BaseCls implements ITest {

	// login of the web user (a test login of this project)
	static final String WEB_USER = TestEnv.get("web.user", "your-web-user");
	static final String WEB_PASS = TestEnv.get("web.password", "your-web-password");

	static final String FROM_DATE = "2026-11-04";
	static final String TO_DATE = "2026-11-30";
	static final String METHOD = "Auto Apply";

	// the words of the value box of a promotion row (Qty has no box in the row: its value is the Qty of the Free Item row)
	private static String[] promo_words(String promo) {
		switch (promo) {
		case "AMT":
			return new String[] { "amount", "amt" };
		case "PER":
			return new String[] { "percent", "%" };
		case "EAMT":
		case "EQTY":
			return new String[] { "each" };
		case "MAMT":
		case "MQTY":
			return new String[] { "multiple" };
		case "RATE":
			return new String[] { "rate", "special" };
		case "WGT":
			return new String[] { "weight" };
		default:
			return new String[0];
		}
	}

	private String main_tab;
	private static final ThreadLocal<String> NAME = new ThreadLocal<String>();
	private String current_step = "";

	@Override
	public String getTestName() {
		return NAME.get() == null ? "YF_IndividualScheme_Test" : NAME.get();
	}

	// ---------------------------------- login and navigation ----------------------------------

	// runs ONCE: fresh session, login as the web user
	@BeforeClass
	public void login_first() {
		login_again();
	}

	private void login_again() {
		try {
			driver.get(url);
		} catch (org.openqa.selenium.NoSuchSessionException | org.openqa.selenium.NoSuchWindowException e) {
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
	}

	// opens Scheme > Individual Scheme from the dashboard
	private void go_to_page() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				dash.Click_submenu_containing("Scheme", "Individual Scheme");
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

	// every execution starts on the Individual Scheme list
	@BeforeMethod
	public void open_page(Method method, Object[] args) throws InterruptedException {
		NAME.set(args != null && args.length > 0 ? String.valueOf(args[0]) : method.getName());
		current_step = "";
		YF_IndividualScheme_Cls o = new YF_IndividualScheme_Cls(driver);
		try {
			if (o.is_list_page()) {
				return;
			}
		} catch (Exception e) {
			// the page is not readable: open it again
		}
		clean_up();
		try {
			go_to_page();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		} catch (Exception e) {
			login_again();
			go_to_page();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
		}
	}

	// ---------------------------------- helpers ----------------------------------

	private void step(String text) {
		current_step = text;
		System.out.println("STEP: " + text);
		if (test != null) {
			test.info(text);
		}
	}

	// one line of Reports\IndividualScheme_Results.csv
	private static synchronized void record(String... cells) {
		try {
			File f = new File(".//Reports//IndividualScheme_Results.csv");
			f.getParentFile().mkdirs();
			boolean fresh = !f.exists();
			try (FileWriter w = new FileWriter(f, true)) {
				if (fresh) {
					w.write("No,Code,Name,Base Type,Promotion Type,Applicable For,Selected,Min,Max,Value,Extra,Result,Message\n");
				}
				StringBuilder line = new StringBuilder();
				for (int i = 0; i < cells.length; i++) {
					line.append(i == 0 ? "" : ",").append('"').append(cells[i] == null ? "" : cells[i].replace("\"", "'").replace("\n", " ")).append('"');
				}
				w.write(line.append('\n').toString());
			}
		} catch (IOException e) {
			System.out.println("Could not write the result file: " + e.getMessage());
		}
	}

	private static Row row_of(String code) {
		for (Row r : IndividualSchemeData.rows()) {
			if (r.code.equals(code)) {
				return r;
			}
		}
		throw new IllegalArgumentException("Unknown scheme code " + code);
	}

	// ---------------------------------- the 100 combinations ----------------------------------

	@DataProvider(name = "schemes")
	public Object[][] schemes() {
		String only_base = System.getProperty("is.base", "");
		String only_promo = System.getProperty("is.promo", "");
		String only_level = System.getProperty("is.level", "");
		String only_code = System.getProperty("is.only", "");
		int max = Integer.getInteger("is.max", 100);
		List<Object[]> rows = new ArrayList<Object[]>();
		for (Row r : IndividualSchemeData.rows()) {
			if ((!only_base.isEmpty() && !only_base.equals(r.base_code)) || (!only_promo.isEmpty() && !only_promo.equals(r.promo_code))
					|| (!only_level.isEmpty() && !only_level.equals(r.level_code)) || (!only_code.isEmpty() && !only_code.equals(r.code))
					|| rows.size() >= max) {
				continue;
			}
			rows.add(new Object[] { r.code, r.name });
		}
		return rows.toArray(new Object[0][]);
	}

	// IS00: what the create form offers (nothing is saved)
	@Test(priority = 0)
	public void IS00_inspect_create_form() {
		test = extent.createTest("IS00 Individual Scheme create form: drop down options and rows");
		YF_IndividualScheme_Cls o = new YF_IndividualScheme_Cls(driver);
		step("Open the create form");
		o.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_create_page());

		List<String> problems = new ArrayList<String>();
		check_options(o, "Promotion Base Type", Arrays.asList("Amount", "Quantity", "Weight"), problems);
		check_options(o, "Scheme Applicable Type", Arrays.asList("Amount", "Quantity", "Percentage", "Each", "Multiple", "Special Rate", "Weight"),
				problems);
		check_options(o, "Applicable For", Arrays.asList("Category", "SubCategory", "Item", "Menu Card"), problems);

		for (String[] level : IndividualSchemeData.LEVELS) {
			step("Scheme Applicable Type = Amount, Applicable For = " + level[1] + ": the rows of the form");
			o.Select_applicable_type("Amount", "");
			o.Select_applicable_for(level[1]);
			test.info(MarkupHelper.createCodeBlock("Boxes of the form for " + level[1] + ":\n" + o.describe_form()));
		}
		for (String type : new String[] { "Each", "Multiple" }) {
			step("Scheme Applicable Type = " + type + ": does the form ask for Amount / Quantity?");
			String sub = o.Select_applicable_type(type, "Amount");
			test.info(type + ": second drop down chosen = '" + (sub.isEmpty() ? "none appeared" : sub) + "'");
		}
		o.Click_cancel();
		Assert.assertTrue(problems.isEmpty(), "The drop downs do not offer what the task expects: " + problems);
		test.pass("The create form offers the expected options");
	}

	private void check_options(YF_IndividualScheme_Cls o, String label, List<String> expected, List<String> problems) {
		List<String> offered = o.options_by_label(label);
		test.info(label + " offers: " + offered);
		for (String e : expected) {
			boolean found = false;
			for (String x : offered) {
				if (x.equalsIgnoreCase(e)) {
					found = true;
				}
			}
			if (!found) {
				problems.add(label + " has no '" + e + "'");
			}
		}
	}

	// IS001 .. IS100: one combination
	@Test(dataProvider = "schemes", priority = 1)
	public void IS_scheme(String code, String name) throws Exception {
		test = extent.createTest(code + " - " + name);
		YF_IndividualScheme_Cls o = new YF_IndividualScheme_Cls(driver);
		Row r = row_of(code);
		String result = "FAILED";
		String message = "";
		String selected = r.master + (r.master_alt == null ? "" : " (" + r.master_alt + ")");
		try {
			// 1. never create a scheme twice
			step("Look for the scheme code " + code + " in the listing");
			if (o.scheme_exists(code)) {
				result = "ALREADY EXISTS";
				message = "listing row: " + o.listing_row(code);
				step("The scheme " + code + " is already in the listing: it is not created again (" + message + ")");
				test.pass("Already listed - not created again: " + message);
				return;
			}

			// 2. the form
			step("Open the create form");
			o.Click_create();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_create_page());

			step("Scheme Code '" + code + "', Scheme Name '" + name + "'");
			o.Enter_code(code);
			o.Enter_name(name);
			step("Promotion Base Type = " + r.base_option);
			o.Select_base_type(r.base_option);
			step("Scheme Applicable Type = " + r.promo_option + (r.promo_sub.isEmpty() ? "" : " (" + r.promo_sub + ")"));
			String sub = o.Select_applicable_type(r.promo_option, r.promo_sub);
			if (!r.promo_sub.isEmpty()) {
				test.info("Second drop down for " + r.promo_option + ": " + (sub.isEmpty() ? "none appeared (nothing chosen)" : "'" + sub + "' chosen"));
			}
			step("Applicable For = " + r.level_option);
			o.Select_applicable_for(r.level_option);

			step("Row: " + r.level_option + " '" + selected + "' (" + r.parent + "), Min " + r.min + ", Max " + r.max + ", value " + r.value
					+ (r.extra.isEmpty() ? "" : ", " + r.extra));
			List<String> report = o.Fill_rows(r.master, r.master_alt, r.min, r.max, promo_words(r.promo_code), r.value,
					r.promo_code.equals("RATE") ? IndividualSchemeData.PER_UNIT : null, IndividualSchemeData.FREE_ITEM);
			boolean value_done = false;
			for (String line : report) {
				if (line.startsWith("WARNING")) {
					test.warning(line);
				} else {
					test.info(line);
					value_done |= line.contains(" value ") || line.contains(" free Qty ");
				}
			}
			if (!value_done) {
				test.warning("The promotion value " + r.value + " (" + r.promo_name + ") was not typed anywhere: no box of the form fits its meaning.");
			}

			step("From Date " + FROM_DATE + ", To Date " + TO_DATE);
			o.Enter_from_date(FROM_DATE);
			o.Enter_to_date(TO_DATE);
			step("Is Active checked, Scheme Application Method = " + METHOD);
			o.Set_active(true);
			o.Select_method(METHOD);

			test.info(MarkupHelper.createCodeBlock("The form before Submit:\n" + o.describe_form()));

			// 3. Submit
			step("Submit");
			o.Click_submit();
			String answer = o.wait_for_answer(25);
			String popup = o.dismiss_popup();
			message = answer.isEmpty() ? popup : answer;
			test.info("Messages after Submit: " + (message.isEmpty() ? "(none)" : message));

			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(200)).until(x -> {
				try {
					return o.is_list_page() || !o.is_create_page();
				} catch (Exception e) {
					return false;
				}
			});
			if (o.is_create_page()) {
				throw new AssertionError("The scheme was not saved: the form is still open. Messages: " + (message.isEmpty() ? "(none)" : message));
			}

			// 4. the listing
			step("Look for " + code + " in the Individual Scheme listing");
			if (!o.is_list_page()) {
				go_to_page();
				new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
			}
			Assert.assertTrue(o.scheme_exists(code), "The scheme " + code + " is not in the Individual Scheme listing after Submit. Messages: " + message);
			List<String> row = o.listing_row(code);
			result = "SAVED";
			step("Saved and listed: " + row);
			test.pass("Scheme " + code + " saved: " + row);
		} catch (Throwable t) {
			result = "FAILED";
			message = (message.isEmpty() ? "" : message + " | ") + "step '" + current_step + "': " + t.getMessage();
			throw t;
		} finally {
			record(String.valueOf(r.no), code, name, r.base_option, r.promo_name, r.level_option, selected, r.min, r.max, r.value, r.extra, result, message);
		}
	}
}
