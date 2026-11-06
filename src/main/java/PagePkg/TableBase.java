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
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * Shared parts of the list pages that have a search form above a result table (DataTables style):
 * typing into boxes, drop downs, search boxes with suggestions, dates, the table "Search:" filter, "Show N entries",
 * Previous / Next, and reading the result table BY COLUMN NAME (records()).
 * A header that appears twice (for example IMAGE) is named IMAGE and IMAGE 2.
 * A page class adds its own fields and, when it needs more from a row (a check box ...), overrides decorate().
 */
public abstract class TableBase {

	protected WebDriver driver;
	protected WebDriverWait wait;

	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

	protected TableBase(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
	}

	// ======================= helpers =======================

	protected List<WebElement> displayed(By by) {
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

	protected WebElement first_displayed(By by) {
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	protected void click_safely(WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			element.click();
		} catch (ElementClickInterceptedException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	// types into a field. An empty field is typed into directly (no clear()); a field with text is emptied
	// with Ctrl+A and Delete first.
	protected void type(WebElement field, String value) {
		String current = field.getAttribute("value");
		if (current != null && !current.isEmpty()) {
			field.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
		}
		if (!value.isEmpty()) {
			field.sendKeys(value);
		}
	}

	protected void select_text(WebElement dropdown, String text) {
		Select select = new Select(dropdown);
		wait.until(d -> select.getOptions().size() > 1);
		for (int i = 0; i < select.getOptions().size(); i++) {
			if (select.getOptions().get(i).getText().trim().equalsIgnoreCase(text.trim())) {
				select.selectByIndex(i);
				return;
			}
		}
		for (int i = 0; i < select.getOptions().size(); i++) {
			if (select.getOptions().get(i).getText().trim().toLowerCase().startsWith(text.trim().toLowerCase())) {
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

	public String selected(WebElement dropdown) {
		return new Select(dropdown).getFirstSelectedOption().getText().trim();
	}

	protected void pause(int millis) {
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

	protected String label_xpath(String label) {
		return "//label[normalize-space()='" + label + "' or normalize-space()='" + label + " *' or normalize-space()='"
				+ label + ":']";
	}

	protected WebElement label_input(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::input[not(@type='hidden')][1]"));
	}

	protected WebElement label_select(String label) {
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
	protected void search_pick(WebElement field, String text) {
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
	protected void set_date(WebElement field, String value) {
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

	// ======================= paging and table filter =======================

	public String entries_text() {
		try {
			List<WebElement> found = displayed(By.xpath("//*[contains(text(),'Showing')]"));
			return found.isEmpty() ? "" : found.get(0).getText().trim();
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
		if (displayed(By.xpath("//*[normalize-space(text())='Next']")).isEmpty()) {
			return false;
		}
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Next']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	public boolean previous_enabled() {
		if (displayed(By.xpath("//*[normalize-space(text())='Previous']")).isEmpty()) {
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

	public boolean has_page_length() {
		return !displayed(By.xpath("//div[contains(@class,'dataTables_length')]//select | //select[contains(@name,'length')]"
				+ " | //label[contains(normalize-space(),'Show')]//select")).isEmpty();
	}

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

	// ======================= result table =======================

	// the headers of the result table, in order (the sort arrows are not part of the text); a repeated header
	// gets " 2", " 3" ...
	public List<String> headers() {
		List<String> best = new ArrayList<String>();
		for (WebElement table : displayed(By.xpath("//table[.//th]"))) {
			List<String> out = new ArrayList<String>();
			try {
				for (WebElement th : table.findElements(By.xpath(".//thead//th"))) {
					{
						String t = header_text(th);
						String name = t;
						int n = 2;
						while (out.contains(name)) {
							name = t + " " + n++;
						}
						out.add(name);
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

	private String header_text(WebElement th) {
		String t = th.getAttribute("textContent");
		return t == null ? "" : t.trim().toUpperCase().replaceAll("\\s+", " ");
	}

	protected List<WebElement> result_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() >= 8
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
				String t = row.getText().toLowerCase();
				if (row.isDisplayed() && (t.contains("no data") || t.contains("no records") || t.contains("no matching"))) {
					return true;
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return false;
	}

	// a page class adds what it needs from a row (for example the state of a check box) to the record
	protected void decorate(WebElement row, Map<String, String> record) {
	}

	// every shown row as {column name -> text}
	public List<Map<String, String>> records() {
		List<String> heads = headers();
		List<Map<String, String>> out = new ArrayList<Map<String, String>>();
		for (WebElement row : result_rows()) {
			try {
				Map<String, String> rec = new LinkedHashMap<String, String>();
				List<WebElement> cells = row.findElements(By.tagName("td"));
				for (int c = 0; c < cells.size(); c++) {
					rec.put(c < heads.size() ? heads.get(c) : "COLUMN " + (c + 1),
							cells.get(c).getText().trim().replaceAll("\\s+", " "));
				}
				decorate(row, rec);
				out.add(rec);
			} catch (StaleElementReferenceException e) {
				// skip a row that was replaced while reading
			}
		}
		return out;
	}

	// the rows of ALL pages: the largest "Show N entries" is chosen (when the page has it) and the pages are walked
	// with Next (at most 60 pages)
	public List<Map<String, String>> all_records() {
		List<Map<String, String>> all = new ArrayList<Map<String, String>>();
		try {
			if (has_page_length()) {
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
				if (header_text(th).equals(name.toUpperCase()) && th.isDisplayed()) {
					click_safely(th);
					pause(1200);
					return;
				}
			}
		}
		throw new NoSuchElementException("Column header not found: " + name);
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
