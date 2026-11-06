package PagePkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * Page class of Reports > Order-to-Sales Difference (a LIST report, logged in as the web user).
 *
 * Top: Search button (opens the search form), Export button, the table filter "Search:" and "Show N entries".
 * Search form: Hierarchy Type (drop down), Supply By (search box), Route (search box), Employee Type (drop down),
 * Salesman (search box), Billing Status (drop down, first option "All"), From Date, To Date (dd/MM/yyyy),
 * Search and Clear.
 * Result table (one line per order item, scrolls sideways) with the columns
 * SL.NO, ORDER DATE, ORDER NO, CUSTOMER NAME, SALESMAN, INVOICE NO, INVOICE DATE, ITEM CODE, ITEM NAME, ORDER QTY,
 * ORDER RATE, BILLED QTY, BILLED RATE, ORDER AMOUNT, BILLED AMOUNT, QTY DIFFERENCE, AMOUNT DIFFERENCE, BILLING STATUS.
 * The cells are read BY COLUMN NAME (records()), so the order of the columns does not matter.
 * Typing: an empty field is typed into directly, without clear().
 */
public class Y_OrderToSalesDifference_Cls {

	WebDriver driver;
	WebDriverWait wait;

	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[1]")
	WebElement Top_search_btn;

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[2]")
	WebElement Search_go;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Clear']")
	WebElement Clear_btn;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Export']")
	WebElement Export_btn;

	@FindBy(xpath = "//*[contains(text(),'Showing')]")
	WebElement Entries_info;

	// ---------- Constructor ----------
	public Y_OrderToSalesDifference_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
		PageFactory.initElements(driver, this);
	}

	// ======================= helpers =======================

	private List<WebElement> displayed(By by) {
		List<WebElement> list = new ArrayList<WebElement>();
		for (WebElement e : driver.findElements(by)) {
			try {
				if (e.isDisplayed()) {
					list.add(e);
				}
			} catch (StaleElementReferenceException ex) {
				// the element was replaced while reading: skip it
			}
		}
		return list;
	}

	private WebElement first_displayed(By by) {
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	private void click_safely(WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			element.click();
		} catch (ElementClickInterceptedException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	// types into a field. An empty field is typed into directly (no clear()); a field with text is emptied
	// with Ctrl+A and Delete first.
	private void type(WebElement field, String value) {
		String current = field.getAttribute("value");
		if (current != null && !current.isEmpty()) {
			field.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
		}
		if (!value.isEmpty()) {
			field.sendKeys(value);
		}
	}

	private void select_text(WebElement dropdown, String text) {
		Select select = new Select(dropdown);
		wait.until(d -> select.getOptions().size() > 1);
		for (int i = 0; i < select.getOptions().size(); i++) {
			String option = select.getOptions().get(i).getText().trim().toLowerCase();
			if (option.equals(text.trim().toLowerCase())) {
				select.selectByIndex(i);
				return;
			}
		}
		for (int i = 0; i < select.getOptions().size(); i++) {
			String option = select.getOptions().get(i).getText().trim().toLowerCase();
			if (option.startsWith(text.trim().toLowerCase())) {
				select.selectByIndex(i);
				return;
			}
		}
		throw new NoSuchElementException("Option not found in the drop down: " + text);
	}

	// the option texts of a drop down, without the placeholder (first) option
	public List<String> options_of(WebElement dropdown) {
		List<String> texts = new ArrayList<String>();
		List<WebElement> options = new Select(dropdown).getOptions();
		for (int i = 1; i < options.size(); i++) {
			String t = options.get(i).getText().trim();
			if (!t.isEmpty()) {
				texts.add(t);
			}
		}
		return texts;
	}

	// every option text of a drop down, the first one included (Billing Status starts with the real option "All")
	public List<String> all_options_of(WebElement dropdown) {
		List<String> texts = new ArrayList<String>();
		for (WebElement o : new Select(dropdown).getOptions()) {
			String t = o.getText().trim();
			if (!t.isEmpty()) {
				texts.add(t);
			}
		}
		return texts;
	}

	public String selected(WebElement dropdown) {
		return new Select(dropdown).getFirstSelectedOption().getText().trim();
	}

	private void pause(int millis) {
		WaitUtil.settle(driver, millis);
	}

	// "1,00,000.00" -> 100000.0 (an empty or text cell gives 0)
	public static double to_number(String text) {
		String t = text == null ? "" : text.replaceAll("[^0-9.\\-]", "");
		if (t.isEmpty() || t.equals("-") || t.equals(".")) {
			return 0;
		}
		try {
			return Double.parseDouble(t);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private String label_xpath(String label) {
		return "//label[normalize-space()='" + label + "' or normalize-space()='" + label + " *' or normalize-space()='"
				+ label + ":']";
	}

	private WebElement label_input(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::input[not(@type='hidden')][1]"));
	}

	private WebElement label_select(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::select[1]"));
	}

	// types into a search box; returns the suggestions that are displayed (nothing is picked)
	public List<String> Suggestions_for(WebElement field, String text) {
		type(field, text);
		pause(1500);
		List<String> out = new ArrayList<String>();
		for (WebElement s : displayed(By.xpath(SUGGESTIONS))) {
			try {
				String t = s.getText().trim();
				if (!t.isEmpty()) {
					out.add(t);
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return out;
	}

	// types into a search box and clicks the suggestion that contains the text (or the first one)
	private void search_pick(WebElement field, String text) {
		type(field, text);
		pause(1500);
		List<WebElement> found = displayed(By.xpath(SUGGESTIONS));
		if (found.isEmpty()) {
			return;
		}
		for (WebElement s : found) {
			if (s.getText().trim().toLowerCase().contains(text.trim().toLowerCase())) {
				click_safely(s);
				pause(1000);
				return;
			}
		}
		click_safely(found.get(0));
		pause(1000);
	}

	// types a date into a date box; when the box does not keep it, the value is set with JavaScript
	private void set_date(WebElement field, String value) {
		type(field, value);
		field.sendKeys(Keys.TAB);
		String kept = field.getAttribute("value");
		if (kept == null || !kept.equals(value)) {
			((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];"
					+ " arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
					+ " arguments[0].dispatchEvent(new Event('change', {bubbles: true}));"
					+ " arguments[0].dispatchEvent(new Event('blur', {bubbles: true}));", field, value);
		}
	}

	// ======================= top and paging =======================

	public WebElement verify_top_search() {
		return Top_search_btn;
	}

	public WebElement verify_export() {
		return Export_btn;
	}

	public String entries_text() {
		try {
			return Entries_info.getText().trim();
		} catch (Exception e) {
			return "";
		}
	}

	// the total of "Showing 1 to 10 of 52 entries" (-1 when the page shows no such line)
	public int entries_total() {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("of\\s+([\\d,]+)\\s+entries").matcher(entries_text());
		return m.find() ? Integer.parseInt(m.group(1).replace(",", "")) : -1;
	}

	public void Click_next() {
		click_safely(first_displayed(By.xpath("//*[normalize-space(text())='Next']")));
		pause(1000);
	}

	public void Click_previous() {
		click_safely(first_displayed(By.xpath("//*[normalize-space(text())='Previous']")));
		pause(1000);
	}

	public boolean next_enabled() {
		List<WebElement> next = displayed(By.xpath("//*[normalize-space(text())='Next']"));
		if (next.isEmpty()) {
			return false;
		}
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Next']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	public boolean previous_enabled() {
		List<WebElement> prev = displayed(By.xpath("//*[normalize-space(text())='Previous']"));
		if (prev.isEmpty()) {
			return false;
		}
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Previous']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	// the page numbers shown between Previous and Next ("1 2 3 4 5 6")
	public List<String> page_numbers() {
		List<String> out = new ArrayList<String>();
		for (WebElement e : displayed(By.xpath("//ul[contains(@class,'pagination')]//li[not(contains(@class,'previous'))"
				+ " and not(contains(@class,'next'))]//a | //*[contains(@class,'paginate_button') and not(contains(@class,'previous'))"
				+ " and not(contains(@class,'next'))]"))) {
			String t = e.getText().trim();
			if (t.matches("\\d+") && !out.contains(t)) {
				out.add(t);
			}
		}
		return out;
	}

	public WebElement verify_table_filter() {
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_filter')]//input | //input[@type='search']"
				+ " | //label[normalize-space()='Search:']/input | //*[normalize-space()='Search:']/following::input[1]"));
	}

	public void Filter_table(String text) {
		type(verify_table_filter(), text);
		pause(1500);
	}

	// the "Show N entries" drop down
	public WebElement verify_page_length() {
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_length')]//select | //select[contains(@name,'length')]"
				+ " | //label[contains(normalize-space(),'Show')]//select"));
	}

	public List<String> page_length_options() {
		List<String> out = new ArrayList<String>();
		for (WebElement o : new Select(verify_page_length()).getOptions()) {
			out.add(o.getText().trim());
		}
		return out;
	}

	public void Set_page_length(String value) {
		new Select(verify_page_length()).selectByVisibleText(value);
		pause(1500);
	}

	public String page_length_selected() {
		return selected(verify_page_length());
	}

	public boolean is_list_page() {
		try {
			return Top_search_btn.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	// ======================= search form =======================

	public WebElement verify_hierarchy_type() {
		return label_select("Hierarchy Type");
	}

	public WebElement verify_supply_by() {
		return label_input("Supply By");
	}

	public WebElement verify_route() {
		return label_input("Route");
	}

	public WebElement verify_employee_type() {
		return label_select("Employee Type");
	}

	public WebElement verify_salesman() {
		return label_input("Salesman");
	}

	public WebElement verify_billing_status() {
		return label_select("Billing Status");
	}

	public WebElement verify_from() {
		return label_input("From Date");
	}

	public WebElement verify_to() {
		return label_input("To Date");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public WebElement verify_clear() {
		return Clear_btn;
	}

	public boolean search_form_open() {
		return !displayed(By.xpath(label_xpath("Salesman"))).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			click_safely(Top_search_btn);
			pause(900);
		}
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches
	public void Search_report(String hierarchy, String supply, String route, String employee_type, String salesman,
			String billing, String from, String to) {
		Open_search_form();
		if (!hierarchy.isEmpty()) {
			select_text(verify_hierarchy_type(), hierarchy);
			pause(800);
		}
		if (!supply.isEmpty()) {
			search_pick(verify_supply_by(), supply);
		}
		if (!route.isEmpty()) {
			search_pick(verify_route(), route);
		}
		if (!employee_type.isEmpty()) {
			select_text(verify_employee_type(), employee_type);
			pause(500);
		}
		if (!salesman.isEmpty()) {
			search_pick(verify_salesman(), salesman);
		}
		if (!billing.isEmpty()) {
			select_text(verify_billing_status(), billing);
		}
		if (!from.isEmpty()) {
			set_date(verify_from(), from);
		}
		if (!to.isEmpty()) {
			set_date(verify_to(), to);
		}
		click_safely(Search_go);
		pause(3500);
	}

	// the usual search: the dates only
	public void Search_dates(String from, String to) {
		Search_report("", "", "", "", "", "", from, to);
	}

	public void Click_clear() {
		Open_search_form();
		click_safely(Clear_btn);
		pause(2500);
	}

	// ======================= result table =======================

	// the headers of the result table, in order (the sort arrows are not part of the text)
	public List<String> headers() {
		List<String> best = new ArrayList<String>();
		for (WebElement table : displayed(By.xpath("//table[.//th]"))) {
			List<String> out = new ArrayList<String>();
			try {
				for (WebElement th : table.findElements(By.xpath(".//thead//th"))) {
					if (th.isDisplayed()) {
						String t = th.getText().trim().toUpperCase().replaceAll("\\s+", " ");
						out.add(t);
					}
				}
			} catch (StaleElementReferenceException e) {
				continue;
			}
			if (out.size() > best.size()) {
				best = out;
			}
		}
		return best;
	}

	private List<WebElement> result_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() >= 10
						&& !row.getText().toLowerCase().contains("no data")) {
					rows.add(row);
				}
			} catch (StaleElementReferenceException e) {
				// skip a row that was replaced while reading
			}
		}
		return rows;
	}

	public int result_count() {
		try {
			return result_rows().size();
		} catch (StaleElementReferenceException e) {
			return result_rows().size();
		}
	}

	public boolean no_data_shown() {
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr"))) {
			try {
				if (row.isDisplayed() && (row.getText().toLowerCase().contains("no data")
						|| row.getText().toLowerCase().contains("no records"))) {
					return true;
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return false;
	}

	// the cell texts of every result row, in column order
	public List<List<String>> result_texts() {
		List<List<String>> out = new ArrayList<List<String>>();
		for (WebElement row : result_rows()) {
			try {
				List<String> cells = new ArrayList<String>();
				for (WebElement td : row.findElements(By.tagName("td"))) {
					cells.add(td.getText().trim().replaceAll("\\s+", " "));
				}
				out.add(cells);
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return out;
	}

	// every shown row as {column name -> text}
	public List<Map<String, String>> records() {
		List<String> heads = headers();
		List<Map<String, String>> out = new ArrayList<Map<String, String>>();
		for (List<String> cells : result_texts()) {
			Map<String, String> rec = new LinkedHashMap<String, String>();
			for (int c = 0; c < cells.size(); c++) {
				rec.put(c < heads.size() ? heads.get(c) : "COLUMN " + (c + 1), cells.get(c));
			}
			out.add(rec);
		}
		return out;
	}

	// the rows of ALL pages: the largest "Show N entries" is chosen and the pages are walked with Next
	// (at most 60 pages). The page length is left as it is afterwards.
	public List<Map<String, String>> all_records() {
		List<Map<String, String>> all = new ArrayList<Map<String, String>>();
		try {
			String biggest = "";
			int max = 0;
			for (String option : page_length_options()) {
				if (option.matches("\\d+") && Integer.parseInt(option) > max) {
					max = Integer.parseInt(option);
					biggest = option;
				}
			}
			if (!biggest.isEmpty() && !page_length_selected().equals(biggest)) {
				Set_page_length(biggest);
			}
		} catch (Exception e) {
			// no page length box: the pages are walked with the current length
		}
		for (int page = 0; page < 60; page++) {
			all.addAll(records());
			if (!next_enabled()) {
				break;
			}
			Click_next();
		}
		return all;
	}

	public void Click_header(String name) {
		for (WebElement table : displayed(By.xpath("//table[.//th]"))) {
			for (WebElement th : table.findElements(By.xpath(".//thead//th"))) {
				if (th.isDisplayed() && th.getText().trim().toUpperCase().replaceAll("\\s+", " ").equals(name.toUpperCase())) {
					click_safely(th);
					pause(1200);
					return;
				}
			}
		}
		throw new NoSuchElementException("Column header not found: " + name);
	}

	// ======================= export =======================

	public void Click_export() {
		click_safely(Export_btn);
		pause(2500);
	}

	// ======================= messages =======================

	public List<String> validation_messages() {
		List<String> out = new ArrayList<String>();
		for (WebElement e : displayed(By.xpath("//*[starts-with(normalize-space(text()),'Please')]"))) {
			try {
				String t = e.getText().trim();
				if (!t.isEmpty() && !out.contains(t)) {
					out.add(t);
				}
			} catch (StaleElementReferenceException ex) {
				// the message is gone: skip it
			}
		}
		return out;
	}

	public String notice() {
		try {
			List<WebElement> found = displayed(By.xpath("//div[contains(@class,'toast') or contains(@class,'alert')"
					+ " or contains(@class,'notyf') or contains(@class,'swal2-popup')]"));
			return found.isEmpty() ? "" : found.get(0).getText().trim();
		} catch (StaleElementReferenceException e) {
			return "";
		}
	}
}
