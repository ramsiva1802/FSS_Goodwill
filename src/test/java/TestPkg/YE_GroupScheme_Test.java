package TestPkg;

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
import org.openqa.selenium.NoSuchElementException;
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
import PagePkg.YE_GroupScheme_Cls;

/*
 * Group Scheme (Scheme > Group Scheme), logged in as the web user - DATA ENTRY of all 81 combinations:
 *   3 Group Combinations (Flexible / Fixed / Key)  x  3 Promotion Base Types (Amount / Quantity / Weight)
 *   x  9 Scheme Applicable Types (Amount, Qty, Percentage, Each Amount, Each Quantity, Multiple Amount, Multiple Quantity,
 *   Special Rate, Weight).
 *
 *   GS00           inspects the create form: the options of every drop down and the boxes the form shows (reported, nothing saved).
 *   GS01 .. GS81   one execution per combination (a DataProvider): the scheme code GRP_<GROUP>_<BASE>_<PROMOTION>_001 and the name
 *                  "Group <Group> - <Base> Base - <Promotion> Promotion" are entered with the Group Combination, Base Type,
 *                  Applicable Type (+ Amount / Quantity for Each and Multiple), Applicable For, Min / Max of the base, the items of
 *                  the sections, the promotion value, the dates, Is Active and Auto Apply, then Submit. The scheme is then looked up
 *                  in the Group Scheme listing. A code that is already in the listing is NOT created again (the run only verifies it).
 *
 * What each execution writes: the report (every step, the boxes the form showed before Submit, the messages after Submit, a screenshot
 * when it failed) and one line in Reports\GroupScheme_Results.csv.
 *
 * Run a part only (VM arguments):  -Dgs.group=FLEX  -Dgs.base=AMT  -Dgs.promo=PER  -Dgs.only=GRP_KEY_WGT_RATE_001  -Dgs.max=5
 *
 * ASSUMPTIONS (the task does not fix them; change them here in one place):
 *   - Applicable For and the items per Group Combination: Flexible = Category (Rono-Cate, Leo-Cate); Fixed = Item (CRono-item1 ..
 *     CRono-item4); Key = Item (CRono-item1, CRono-item3). The free item of the Qty / Each Quantity / Multiple Quantity / Weight
 *     promotions is leo-item1.
 *   - Special Rate has a second box, "Per Unit": it gets 1 (the task gives no value; change PER_UNIT).
 *   - Key Item Combination rows have a box "Min.Key Value": it gets the Min of the base (500 / 5 / 5); the task gives no value for it.
 *   - The promotion value goes into the box of the promotion section whose placeholder / column has the words of its meaning
 *     (amount, percent, rate ...) and, for the free-item promotions, into the Qty box of the "Free Item" section. When no box
 *     fits, the report says so (WARNING) and nothing is typed into another box.
 */
public class YE_GroupScheme_Test extends BaseCls implements ITest {

	// login of the web user (a test login of this project)
	static final String WEB_USER = TestEnv.get("web.user", "your-web-user");
	static final String WEB_PASS = TestEnv.get("web.password", "your-web-password");

	// common values of every scheme
	static final String FROM_DATE = "2026-11-04";
	static final String TO_DATE = "2026-11-30";
	static final String METHOD = "Auto Apply";

	// { code part, name part, text of the drop down, Applicable For, items (separated by ;) }
	static final String[][] GROUPS = {
			{ "FLEX", "Flexible", "Flexible Item Combination", "Category", "Rono-Cate;Leo-Cate" },
			{ "FIX", "Fixed", "Fixed Item Combination", "Item", "CRono-item1;CRono-item2;CRono-item3;CRono-item4" },
			{ "KEY", "Key Combination", "Key Item Combination", "Item", "CRono-item1;CRono-item3" } };

	// { code part, name part, text of the drop down, Min, Max }
	static final String[][] BASES = {
			{ "AMT", "Amount", "Amount", "500", "5000" },
			{ "QTY", "Quantity", "Quantity", "5", "20" },
			{ "WGT", "Weight", "Weight", "5", "50" } };

	// { code part, name part, text of the drop down, Amount / Quantity of Each and Multiple, sample value, free item (Y / N) }
	static final String[][] PROMOTIONS = {
			{ "AMT", "Amount", "Amount", "", "100", "N" },
			{ "QTY", "Qty", "Quantity", "", "2", "Y" },
			{ "PER", "Percentage", "Percentage", "", "10", "N" },
			{ "EAMT", "Each Amount", "Each", "Amount", "20", "N" },
			{ "EQTY", "Each Quantity", "Each", "Quantity", "1", "Y" },
			{ "MAMT", "Multiple Amount", "Multiple", "Amount", "50", "N" },
			{ "MQTY", "Multiple Quantity", "Multiple", "Quantity", "2", "Y" },
			{ "RATE", "Special Rate", "Special Rate", "", "75", "N" },
			{ "WGT", "Weight", "Weight", "", "1", "Y" } };

	static final String FREE_ITEM = "leo-item1";

	// the "Per Unit" box of the Special Rate row: the task gives no value for it, 1 (the rate is for one unit) is assumed
	static final String PER_UNIT = "1";

	// the words that mark the value box of a PROMOTION ROW (the column next to the item / category box). Each, Multiple, Percentage ...
	// have a column of their own (its placeholder is the word, for example "Each" or "Weight"); Qty has no box in the row, its value
	// is the Qty of the Free Item section.
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
		default: // QTY: no box in the row, its value is the Qty of the Free Item section
			return new String[0];
		}
	}

	// the value box of the Free Item section
	static final String[] FREE_WORDS = { "qty", "quantity", "weight" };
	private String main_tab;
	private static final ThreadLocal<String> NAME = new ThreadLocal<String>();
	private final List<String> steps = new ArrayList<String>();
	private String current_step = "";

	@Override
	public String getTestName() {
		return NAME.get() == null ? "YE_GroupScheme_Test" : NAME.get();
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

	// opens Scheme > Group Scheme from the dashboard
	private void go_to_page() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				dash.Click_submenu_containing("Scheme", "Group Scheme");
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

	// every execution starts on the Group Scheme list
	@BeforeMethod
	public void open_page(Method method, Object[] args) throws InterruptedException {
		NAME.set(args != null && args.length > 0 ? String.valueOf(args[0]) : method.getName());
		steps.clear();
		current_step = "";
		YE_GroupScheme_Cls o = new YE_GroupScheme_Cls(driver);
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
		steps.add(text);
		System.out.println("STEP: " + text);
		if (test != null) {
			test.info(text);
		}
	}

	// one line of Reports\GroupScheme_Results.csv
	private static synchronized void record(String... cells) {
		try {
			File f = new File(".//Reports//GroupScheme_Results.csv");
			f.getParentFile().mkdirs();
			boolean fresh = !f.exists();
			try (FileWriter w = new FileWriter(f, true)) {
				if (fresh) {
					w.write("Code,Name,Group Combination,Base Type,Promotion Type,Applicable For,Items,Min,Max,Value,Result,Message\n");
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

	private static String[] group_of(String code) {
		for (String[] g : GROUPS) {
			if (g[0].equals(code)) {
				return g;
			}
		}
		throw new IllegalArgumentException("Unknown group " + code);
	}

	private static String[] base_of(String code) {
		for (String[] b : BASES) {
			if (b[0].equals(code)) {
				return b;
			}
		}
		throw new IllegalArgumentException("Unknown base " + code);
	}

	private static String[] promo_of(String code) {
		for (String[] p : PROMOTIONS) {
			if (p[0].equals(code)) {
				return p;
			}
		}
		throw new IllegalArgumentException("Unknown promotion " + code);
	}

	// ---------------------------------- the 81 combinations ----------------------------------

	// every combination in the order of the task: Flexible (Amount, Quantity, Weight base), Fixed, Key; the promotions in their order
	@DataProvider(name = "schemes")
	public Object[][] schemes() {
		String only_group = System.getProperty("gs.group", "");
		String only_base = System.getProperty("gs.base", "");
		String only_promo = System.getProperty("gs.promo", "");
		String only_code = System.getProperty("gs.only", "");
		int max = Integer.getInteger("gs.max", 81);
		List<Object[]> rows = new ArrayList<Object[]>();
		for (String[] g : GROUPS) {
			for (String[] b : BASES) {
				for (String[] p : PROMOTIONS) {
					String code = "GRP_" + g[0] + "_" + b[0] + "_" + p[0] + "_001";
					if ((!only_group.isEmpty() && !only_group.equals(g[0])) || (!only_base.isEmpty() && !only_base.equals(b[0]))
							|| (!only_promo.isEmpty() && !only_promo.equals(p[0])) || (!only_code.isEmpty() && !only_code.equals(code))
							|| rows.size() >= max) {
						continue;
					}
					String name = "Group " + g[1] + " - " + b[1] + " Base - " + p[1] + " Promotion";
					rows.add(new Object[] { code, name, g[0], b[0], p[0] });
				}
			}
		}
		return rows.toArray(new Object[0][]);
	}

	// GS00: what the create form offers (nothing is saved)
	@Test(priority = 0)
	public void GS00_inspect_create_form() {
		test = extent.createTest("GS00 Group Scheme create form: drop down options and boxes");
		YE_GroupScheme_Cls o = new YE_GroupScheme_Cls(driver);
		step("Open the create form");
		o.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_create_page());

		List<String> problems = new ArrayList<String>();
		check_options(o, "Promotion Base Type", Arrays.asList("Amount", "Quantity", "Weight"), problems);
		check_options(o, "Scheme Applicable Type",
				Arrays.asList("Amount", "Quantity", "Percentage", "Each", "Multiple", "Special Rate", "Weight"), problems);
		check_options(o, "Applicable For", Arrays.asList("Category", "SubCategory", "Item", "Menu Card", "Bill"), problems);
		check_options(o, "Group Combination", Arrays.asList("Flexible Item Combination", "Fixed Item Combination", "Key Item Combination"),
				problems);

		for (String type : new String[] { "Each", "Multiple" }) {
			step("Scheme Applicable Type = " + type + ": does the form ask for Amount / Quantity?");
			String sub = o.Select_applicable_type(type, "Amount");
			test.info(type + ": second drop down chosen = '" + (sub.isEmpty() ? "none appeared" : sub) + "'");
		}
		test.info(MarkupHelper.createCodeBlock("Boxes of the form:\n" + o.describe_form()));
		o.Click_back();
		Assert.assertTrue(problems.isEmpty(), "The drop downs do not offer what the task expects: " + problems);
		test.pass("The create form offers the expected options");
	}

	private void check_options(YE_GroupScheme_Cls o, String label, List<String> expected, List<String> problems) {
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

	// GS01 .. GS81: one combination
	@Test(dataProvider = "schemes", priority = 1)
	public void GS_scheme(String code, String name, String group, String base, String promo) throws Exception {
		test = extent.createTest(code + " - " + name);
		YE_GroupScheme_Cls o = new YE_GroupScheme_Cls(driver);
		String[] g = group_of(group), b = base_of(base), p = promo_of(promo);
		List<String> items = Arrays.asList(g[4].split(";"));
		String value = p[4];
		String result = "FAILED";
		String message = "";
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

			step("Group Combination = " + g[2]);
			o.Select_group_combination(g[2]);
			step("Promotion Base Type = " + b[2]);
			o.Select_base_type(b[2]);
			step("Scheme Applicable Type = " + p[2] + (p[3].isEmpty() ? "" : " (" + p[3] + ")"));
			String sub = o.Select_applicable_type(p[2], p[3]);
			if (!p[3].isEmpty()) {
				test.info("Second drop down for " + p[2] + ": " + (sub.isEmpty() ? "none appeared (nothing chosen)" : "'" + sub + "' chosen"));
			}
			step("Applicable For = " + g[3]);
			o.Select_applicable_for(g[3]);

			step("Base values: " + o.min_label() + " " + b[3] + ", Max " + b[4]);
			o.Enter_min(b[3]);
			o.Enter_max(b[4]);

			// the sections of the form (they depend on the choices above)
			int sections = o.section_count();
			step("The form shows " + sections + " item section(s)");
			if (sections == 0) {
				test.warning("No section with an 'Add Item' button is shown for this combination: no item can be selected.");
			}
			boolean value_done = false;
			String[] row_words = promo_words(promo);
			for (int i = 0; i < sections; i++) {
				String header = o.section_header(i);
				if (o.is_free_section(i)) {
					step("Section '" + header + "': item " + FREE_ITEM + ", value " + value);
					test.info("Dropdown rows clicked: " + o.Fill_items(i, Arrays.asList(FREE_ITEM)));
					String filled = o.Fill_value(i, value, FREE_WORDS);
					report_fill(header, filled);
					value_done |= !filled.isEmpty();
				} else {
					step("Section '" + header + "': items " + items);
					test.info("Dropdown rows clicked: " + o.Fill_items(i, items));
					if (row_words.length > 0) {
						step("Section '" + header + "': value " + value + " (" + p[1] + ") into the '" + String.join("' / '", row_words) + "' box of every row");
						String filled = o.Fill_value(i, value, row_words);
						report_fill(header, filled);
						value_done |= !filled.isEmpty();
						// Special Rate has a second box in the row: "Per Unit" (the units the rate is for)
						if (promo.equals("RATE")) {
							step("Section '" + header + "': Per Unit " + PER_UNIT);
							report_fill(header + " (Per Unit)", o.Fill_value(i, PER_UNIT, "per unit", "unit"));
						}
					}
					// Key Item Combination: every key item row has the box "Min.Key Value" (the minimum of the key item): the Min of the base
					if (group.equals("KEY")) {
						step("Section '" + header + "': Min.Key Value " + b[3] + " (the Min of the base)");
						report_fill(header + " (Min.Key Value)", o.Fill_value(i, b[3], "key"));
					}
					for (String empty : o.Unfilled_boxes(i)) {
						test.warning("Section '" + header + "': the box '" + empty + "' is still empty / 0 before Submit.");
					}
				}
			}
			if (!value_done) {
				test.warning("The promotion value " + value + " (" + p[1] + ") was not typed anywhere: no box of the form fits its meaning.");
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
			step("Look for " + code + " in the Group Scheme listing");
			if (!o.is_list_page()) {
				go_to_page();
				new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_list_page());
			}
			Assert.assertTrue(o.scheme_exists(code), "The scheme " + code + " is not in the Group Scheme listing after Submit. Messages: " + message);
			List<String> row = o.listing_row(code);
			result = "SAVED";
			step("Saved and listed: " + row);
			test.pass("Scheme " + code + " saved: " + row);
		} catch (Throwable t) {
			result = "FAILED";
			message = (message.isEmpty() ? "" : message + " | ") + "step '" + current_step + "': " + t.getMessage();
			throw t;
		} finally {
			record(code, name, g[2], b[2], p[1], g[3], g[4], b[3], b[4], value, result, message);
		}
	}

	private void report_fill(String header, String filled) {
		if (filled.isEmpty()) {
			test.warning("Section '" + header + "': no value box found for this promotion (nothing was typed there).");
		} else {
			test.info("Section '" + header + "': value typed into " + filled);
		}
	}
}
