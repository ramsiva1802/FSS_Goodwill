package PagePkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * Page class of Field Management > Discount Pattern (logged in as the web user).
 *
 * LIST: Search and Create buttons, the table filter "Search:", "Show N entries"; columns (cell index)
 *   0 classification (Category / SubCategory / Item / Menu Card / Bill), 1 subcategory / item / category,
 *   2 number of discount patterns, 3 action (eye = view, edit icon = edit).
 *   Search form: Discount Pattern Code, Discount Pattern Name, Apply To, Apply Item / Sub category (search box,
 *   disabled until Apply To is chosen), Clear, Search.
 * VIEW / EDIT pop-up: "MODE / ITEM / SUB CATEGORY / NUMBER OF DISCOUNT PATTERNS" header, an inner table with its own
 *   "Show N entries" and "Search:" [code, name, apply to, item / subcategory, type, value (+ bin in edit)], Close.
 * CREATE page: Apply To, Sub category / Item (search box), Discount Code (auto or typed), Discount Name,
 *   Discount Type (Amount / Percentage, Percentage by default), Discount Value, Add New, the grid of added patterns
 *   [classification, item, code, name, type, value, bin], Back, Submit.
 * Typing: an empty field is typed into directly, without clear().
 */
public class U_DiscountPattern_Cls {

	WebDriver driver;
	WebDriverWait wait;

	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

	static final String MODAL_SHOWN = "//div[contains(@class,'modal') and contains(@class,'show')]";

	// ---------- Constructor ----------
	public U_DiscountPattern_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
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

	private void js_click(WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", element);
		pause(800);
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

	private void pause(int millis) {
		WaitUtil.settle(driver, millis);
	}

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

	// true when the box cannot be typed into (read only, disabled or no pointer events)
	public boolean is_locked(WebElement e) {
		return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript(
				"var e = arguments[0]; return !!(e.readOnly || e.disabled || e.hasAttribute('readonly')"
				+ " || e.hasAttribute('disabled') || window.getComputedStyle(e).pointerEvents === 'none');", e));
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

	// types into a search box and returns the suggestions that are displayed (nothing is picked)
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

	// types into a search box and clicks the suggestion that contains the text (or the first one);
	// when no list appears the search icon beside the box is used and a listed entry with the text is clicked
	private void search_pick(WebElement field, String text) {
		type(field, text);
		pause(1500);
		List<WebElement> found = displayed(By.xpath(SUGGESTIONS));
		if (found.isEmpty()) {
			List<WebElement> icon = field.findElements(By.xpath("./following-sibling::*[self::button or self::span or self::i]"
					+ " | ./parent::*/*[self::button or contains(@class,'input-group-text')]"));
			for (WebElement i : icon) {
				if (i.isDisplayed()) {
					js_click(i);
					break;
				}
			}
			pause(1500);
			found = displayed(By.xpath(SUGGESTIONS + " | " + MODAL_SHOWN + "//table//tbody/tr[td]"));
		}
		if (found.isEmpty()) {
			return;
		}
		for (WebElement s : found) {
			try {
				if (s.getText().trim().toLowerCase().contains(text.trim().toLowerCase())) {
					click_safely(s);
					pause(1000);
					return;
				}
			} catch (StaleElementReferenceException e) {
				// try the next one
			}
		}
		click_safely(found.get(0));
		pause(1000);
	}

	// ======================= list: top, filter, paging =======================

	public WebElement verify_top_search() {
		return first_displayed(By.xpath("(//*[self::button or self::a][normalize-space()='Search'])[1]"));
	}

	public WebElement verify_create_btn() {
		return first_displayed(By.xpath("//*[self::button or self::a][normalize-space()='Create']"));
	}

	public boolean is_list_page() {
		try {
			return !displayed(By.xpath("//*[self::button or self::a][normalize-space()='Create']")).isEmpty();
		} catch (Exception e) {
			return false;
		}
	}

	public String entries_text() {
		List<WebElement> found = displayed(By.xpath("//*[contains(text(),'Showing')][not(ancestor::div[contains(@class,'modal')])]"));
		return found.isEmpty() ? "" : found.get(0).getText().trim();
	}

	public int entries_total() {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("of\\s+([\\d,]+)\\s+entries").matcher(entries_text());
		return m.find() ? Integer.parseInt(m.group(1).replace(",", "")) : 0;
	}

	public void Click_next() {
		click_safely(first_displayed(By.xpath("//*[normalize-space(text())='Next'][not(ancestor::div[contains(@class,'modal')])]")));
		pause(1000);
	}

	public void Click_previous() {
		click_safely(first_displayed(By.xpath("//*[normalize-space(text())='Previous'][not(ancestor::div[contains(@class,'modal')])]")));
		pause(1000);
	}

	public boolean previous_enabled() {
		return driver.findElements(By.xpath("//*[normalize-space(text())='Previous'][not(ancestor::div[contains(@class,'modal')])]"
				+ "/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	public WebElement verify_table_filter() {
		return first_displayed(By.xpath("(//div[contains(@class,'dataTables_filter')]//input | //input[@type='search'])"
				+ "[not(ancestor::div[contains(@class,'modal')])]"));
	}

	public void Filter_table(String text) {
		type(verify_table_filter(), text);
		pause(1500);
	}

	public WebElement verify_page_length() {
		return first_displayed(By.xpath("(//div[contains(@class,'dataTables_length')]//select | //select[contains(@name,'length')])"
				+ "[not(ancestor::div[contains(@class,'modal')])]"));
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
		pause(1200);
	}

	public String page_length_selected() {
		return selected(verify_page_length());
	}

	// ======================= list: search form =======================

	public WebElement verify_code() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'Enter Discount Code')]"));
	}

	public WebElement verify_name() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'Enter Discount Name')]"));
	}

	public WebElement verify_apply_to() {
		return label_select("Apply To");
	}

	public WebElement verify_subject() {
		return label_input("Apply Item / Sub category");
	}

	public boolean search_form_open() {
		return !displayed(By.xpath("//input[contains(@placeholder,'Enter Discount Code')]")).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			click_safely(verify_top_search());
			pause(800);
		}
	}

	public WebElement verify_search_go() {
		return first_displayed(By.xpath("(//*[self::button or self::a][normalize-space()='Search'])[2]"));
	}

	public WebElement verify_clear() {
		return first_displayed(By.xpath("//*[self::button or self::a][normalize-space()='Clear']"));
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches
	public void Search_patterns(String code, String name, String apply_to, String subject) {
		Open_search_form();
		if (!code.isEmpty()) {
			type(verify_code(), code);
		}
		if (!name.isEmpty()) {
			type(verify_name(), name);
		}
		if (!apply_to.isEmpty()) {
			select_text(verify_apply_to(), apply_to);
			pause(800);
		}
		if (!subject.isEmpty()) {
			search_pick(verify_subject(), subject);
		}
		click_safely(verify_search_go());
		pause(3000);
	}

	public void Click_clear() {
		Open_search_form();
		click_safely(verify_clear());
		pause(2500);
	}

	// ======================= list: table =======================

	public List<String> headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : displayed(By.xpath("//table//th[not(ancestor::div[contains(@class,'modal')])]"))) {
			String t = th.getText().trim().toUpperCase().replaceAll("\\s+", " ");
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	private List<WebElement> result_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td][not(ancestor::div[contains(@class,'modal')])]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() >= 4
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
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[not(ancestor::div[contains(@class,'modal')])]"))) {
			try {
				String t = row.isDisplayed() ? row.getText().toLowerCase() : "";
				if (t.contains("no data") || t.contains("no matching") || t.contains("no records")) {
					return true;
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return false;
	}

	// [classification, item / category, number of patterns, action]
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

	// the index of the row with this classification and item / category (-1 when not listed on this page)
	public int row_index(String classification, String subject) {
		List<List<String>> rows = result_texts();
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).get(0).equalsIgnoreCase(classification) && rows.get(i).get(1).equalsIgnoreCase(subject)) {
				return i;
			}
		}
		return -1;
	}

	public void Click_header(String name) {
		for (WebElement th : displayed(By.xpath("//table//th[not(ancestor::div[contains(@class,'modal')])]"))) {
			if (th.getText().trim().toUpperCase().replaceAll("\\s+", " ").equals(name.toUpperCase())) {
				click_safely(th);
				pause(1000);
				return;
			}
		}
		throw new NoSuchElementException("Column header not found: " + name);
	}

	private List<WebElement> action_icons(int row) {
		List<WebElement> tds = result_rows().get(row).findElements(By.tagName("td"));
		return tds.get(tds.size() - 1).findElements(By.xpath(".//a | .//button | .//span[@onclick]"
				+ " | .//i[not(ancestor::a) and not(ancestor::button) and not(ancestor::span[@onclick])]"));
	}

	private String html_of(WebElement e) {
		try {
			return String.valueOf(((JavascriptExecutor) driver).executeScript(
					"return ((arguments[0].outerHTML || '') + ' ' + (arguments[0].title || '')).toLowerCase();", e));
		} catch (StaleElementReferenceException ex) {
			return "";
		}
	}

	private String kind_of(WebElement icon) {
		String h = html_of(icon);
		if (h.contains("eye") || h.contains("view")) {
			return "view";
		}
		if (h.contains("edit") || h.contains("pencil") || h.contains("pen")) {
			return "edit";
		}
		if (h.contains("trash") || h.contains("delete") || h.contains("bin")) {
			return "delete";
		}
		return "other";
	}

	public List<String> actions_of(int row) {
		List<String> kinds = new ArrayList<String>();
		for (WebElement icon : action_icons(row)) {
			kinds.add(kind_of(icon));
		}
		return kinds;
	}

	public String actions_html(int row) {
		StringBuilder sb = new StringBuilder();
		for (WebElement icon : action_icons(row)) {
			sb.append(html_of(icon).replaceAll("\\s+", " ")).append(" || ");
		}
		return sb.toString();
	}

	private void click_action(int row, String kind) {
		for (WebElement icon : action_icons(row)) {
			if (kind_of(icon).equals(kind)) {
				js_click(icon);
				pause(1200);
				return;
			}
		}
		throw new NoSuchElementException("The row " + (row + 1) + " has no " + kind + " action");
	}

	public void Click_view(int row) {
		click_action(row, "view");
	}

	public void Click_edit(int row) {
		click_action(row, "edit");
	}

	// ======================= view / edit pop-up =======================

	public boolean modal_open() {
		return !displayed(By.xpath(MODAL_SHOWN)).isEmpty();
	}

	public String modal_text() {
		List<WebElement> m = displayed(By.xpath(MODAL_SHOWN));
		return m.isEmpty() ? "" : m.get(0).getText().trim().replaceAll("\\s+", " ");
	}

	private String modal_part(String regex) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(modal_text());
		return m.find() ? m.group(1).trim() : "";
	}

	// "View Discount Pattern" or "Edit Discount Pattern"
	public String modal_mode() {
		return modal_part("MODE\\s+(.+?)\\s+ITEM\\s*/\\s*SUB\\s*CATEGORY");
	}

	public String modal_subject() {
		return modal_part("ITEM\\s*/\\s*SUB\\s*CATEGORY\\s+(.+?)\\s+NUMBER OF DISCOUNT PATTERNS");
	}

	public int modal_number() {
		String n = modal_part("NUMBER OF DISCOUNT PATTERNS\\s+(\\d+)");
		return n.isEmpty() ? -1 : Integer.parseInt(n);
	}

	public int modal_entries_total() {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("of\\s+([\\d,]+)\\s+entries").matcher(modal_text());
		return m.find() ? Integer.parseInt(m.group(1).replace(",", "")) : 0;
	}

	public List<String> modal_headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : displayed(By.xpath(MODAL_SHOWN + "//table//th"))) {
			String t = th.getText().trim().toUpperCase().replaceAll("\\s+", " ");
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	private List<WebElement> modal_row_elements() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : displayed(By.xpath(MODAL_SHOWN + "//table//tbody/tr[td]"))) {
			try {
				if (row.findElements(By.tagName("td")).size() >= 6 && !row.getText().toLowerCase().contains("no data")) {
					rows.add(row);
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return rows;
	}

	// the pattern rows of the pop-up (first page of its table): [code, name, apply to, item, type, value (, action)]
	public List<List<String>> modal_rows() {
		List<List<String>> out = new ArrayList<List<String>>();
		for (WebElement row : modal_row_elements()) {
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

	public WebElement modal_filter_box() {
		return first_displayed(By.xpath(MODAL_SHOWN + "//input[@type='search' or contains(@aria-controls,'')][not(@type='hidden')]"));
	}

	public void Modal_filter(String text) {
		type(modal_filter_box(), text);
		pause(1200);
	}

	public WebElement modal_page_length() {
		return first_displayed(By.xpath(MODAL_SHOWN + "//select"));
	}

	public void Modal_set_page_length(String value) {
		new Select(modal_page_length()).selectByVisibleText(value);
		pause(1000);
	}

	// shows as many lines as possible in the pop-up (the last option of its "Show N entries")
	public void Modal_set_page_length_all() {
		try {
			Select s = new Select(modal_page_length());
			s.selectByIndex(s.getOptions().size() - 1);
			pause(1000);
		} catch (Exception e) {
			// the pop-up has no such drop down
		}
	}

	// true when the pop-up has bin icons (edit mode)
	public int modal_delete_count() {
		int n = 0;
		for (WebElement row : modal_row_elements()) {
			List<WebElement> tds = row.findElements(By.tagName("td"));
			for (WebElement e : tds.get(tds.size() - 1).findElements(By.xpath(".//a | .//button | .//i"))) {
				if (kind_of(e).equals("delete")) {
					n++;
					break;
				}
			}
		}
		return n;
	}

	// deletes the pattern with this code in the edit pop-up (the confirmation is NOT answered)
	public void Click_modal_delete(String code) {
		for (WebElement row : modal_row_elements()) {
			List<WebElement> tds = row.findElements(By.tagName("td"));
			if (tds.get(0).getText().trim().equalsIgnoreCase(code)) {
				for (WebElement e : tds.get(tds.size() - 1).findElements(By.xpath(".//a | .//button | .//i"))) {
					if (kind_of(e).equals("delete")) {
						js_click(e);
						return;
					}
				}
			}
		}
		throw new NoSuchElementException("The pattern " + code + " is not in the pop-up (or has no bin)");
	}

	public void Close_modal() {
		for (int round = 0; round < 3; round++) {
			List<WebElement> modals = displayed(By.xpath(MODAL_SHOWN));
			if (modals.isEmpty()) {
				return;
			}
			List<WebElement> x = modals.get(0).findElements(By.xpath(".//button[normalize-space()='Close' or contains(@class,'btn-close')"
					+ " or normalize-space()='×' or @data-bs-dismiss='modal' or @data-dismiss='modal' or @aria-label='Close']"));
			boolean clicked = false;
			for (WebElement b : x) {
				if (b.isDisplayed()) {
					click_safely(b);
					clicked = true;
					break;
				}
			}
			if (!clicked) {
				driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
			}
			pause(1000);
		}
		if (!displayed(By.xpath(MODAL_SHOWN)).isEmpty()) {
			((JavascriptExecutor) driver).executeScript(
					"document.querySelectorAll('.modal.show').forEach(function(m){ m.classList.remove('show'); m.style.display='none'; });"
					+ " document.querySelectorAll('.modal-backdrop').forEach(function(b){ b.remove(); });"
					+ " document.body.classList.remove('modal-open'); document.body.style.overflow='';");
			pause(500);
		}
	}

	// closes the pop-up with its Close button only
	public void Click_modal_close_button() {
		click_safely(first_displayed(By.xpath(MODAL_SHOWN + "//button[normalize-space()='Close']")));
		pause(1000);
	}

	// closes the pop-up with the x of its header only
	public void Click_modal_x() {
		click_safely(first_displayed(By.xpath(MODAL_SHOWN + "//button[contains(@class,'btn-close') or contains(@class,'close')"
				+ " or normalize-space()='×' or @aria-label='Close']")));
		pause(1000);
	}

	// ======================= create page =======================

	public void Click_create() {
		click_safely(verify_create_btn());
		pause(1500);
	}

	public boolean is_create_page() {
		try {
			return !displayed(By.xpath("//button[normalize-space()='Add New']")).isEmpty();
		} catch (Exception e) {
			return false;
		}
	}

	public WebElement create_apply_to() {
		return label_select("Apply To");
	}

	public WebElement create_subject() {
		return label_input("Sub category / Item");
	}

	public WebElement create_code() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'Auto-generated')]"));
	}

	public WebElement create_name() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'Enter Discount Name')]"));
	}

	public WebElement create_value() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'Enter Discount Value')]"));
	}

	public void Choose_apply_to(String text) {
		select_text(create_apply_to(), text);
		pause(1000);
	}

	// types the subject and picks the suggestion (the first one when none contains the text)
	public void Pick_subject(String text) {
		search_pick(create_subject(), text);
	}

	public String subject_text() {
		return String.valueOf(create_subject().getAttribute("value"));
	}

	public void Set_code(String text) {
		type(create_code(), text);
	}

	public void Set_name(String text) {
		type(create_name(), text);
	}

	public void Set_value(String text) {
		type(create_value(), text);
	}

	// the radio of the discount type, found by its label text with JavaScript
	public WebElement type_radio(String label) {
		Object found = ((JavascriptExecutor) driver).executeScript(
				"var all = document.querySelectorAll('label, span'); var wanted = arguments[0];"
				+ " for (var i = 0; i < all.length; i++) {"
				+ "   var t = (all[i].textContent || '').replace(/\\s+/g, ' ').trim();"
				+ "   if (t === wanted && all[i].offsetParent !== null) {"
				+ "     if (all[i].htmlFor) { var x = document.getElementById(all[i].htmlFor); if (x) { return x; } }"
				+ "     var own = all[i].querySelector(\"input[type='radio']\"); if (own) { return own; }"
				+ "     var p = all[i].previousElementSibling; if (p && p.type === 'radio') { return p; }"
				+ "     if (all[i].parentElement) { var q = all[i].parentElement.querySelector(\"input[type='radio']\"); if (q) { return q; } } } }"
				+ " return null;", label);
		if (found == null) {
			throw new NoSuchElementException("The radio button was not found: " + label);
		}
		return (WebElement) found;
	}

	public void Choose_type(String label) {
		js_click(type_radio(label));
	}

	public String chosen_type() {
		for (String t : new String[] { "Amount", "Percentage" }) {
			try {
				if (type_radio(t).isSelected()) {
					return t;
				}
			} catch (NoSuchElementException e) {
				// try the other
			}
		}
		return "";
	}

	public void Click_add_new() {
		click_safely(first_displayed(By.xpath("//button[normalize-space()='Add New']")));
		pause(1200);
	}

	// fills the create form for one pattern and clicks Add New (an empty value leaves the field as it is)
	public void Add_pattern(String apply_to, String subject, String code, String name, String type, String value) {
		if (!apply_to.isEmpty()) {
			Choose_apply_to(apply_to);
		}
		if (!subject.isEmpty()) {
			Pick_subject(subject);
		}
		if (!code.isEmpty()) {
			Set_code(code);
		}
		if (!name.isEmpty()) {
			Set_name(name);
		}
		if (!type.isEmpty()) {
			Choose_type(type);
		}
		if (!value.isEmpty()) {
			Set_value(value);
		}
		Click_add_new();
	}

	private List<WebElement> grid_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : displayed(By.xpath("//table//tbody/tr[td]"))) {
			try {
				if (row.findElements(By.tagName("td")).size() >= 7 && !row.getText().toLowerCase().contains("no data")) {
					rows.add(row);
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return rows;
	}

	public int grid_count() {
		return grid_rows().size();
	}

	public boolean grid_no_data() {
		for (WebElement row : displayed(By.xpath("//table//tbody/tr"))) {
			try {
				if (row.getText().toLowerCase().contains("no data")) {
					return true;
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return false;
	}

	// [classification, item, code, name, type, value, action]
	public List<List<String>> grid_texts() {
		List<List<String>> out = new ArrayList<List<String>>();
		for (WebElement row : grid_rows()) {
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

	public void Remove_grid_row(int index) {
		List<WebElement> tds = grid_rows().get(index).findElements(By.tagName("td"));
		js_click(tds.get(tds.size() - 1).findElement(By.xpath(".//a | .//button | .//i")));
		pause(600);
	}

	public void Click_back() {
		click_safely(first_displayed(By.xpath("//button[normalize-space()='Back'] | //a[normalize-space()='Back']")));
		pause(1500);
	}

	public void Click_submit() {
		click_safely(first_displayed(By.xpath("//button[normalize-space()='Submit']")));
		pause(2500);
	}

	// ======================= messages and dialogs =======================

	public boolean alert_open() {
		try {
			driver.switchTo().alert();
			return true;
		} catch (NoAlertPresentException e) {
			return false;
		}
	}

	public String Alert_text() {
		try {
			Alert a = driver.switchTo().alert();
			String text = a.getText();
			a.accept();
			pause(800);
			return text;
		} catch (NoAlertPresentException e) {
			return "";
		}
	}

	// the text of a browser alert that is left open (it is NOT accepted)
	public String Alert_peek() {
		try {
			return driver.switchTo().alert().getText();
		} catch (NoAlertPresentException e) {
			return "";
		}
	}

	public void Alert_dismiss() {
		try {
			driver.switchTo().alert().dismiss();
			pause(800);
		} catch (NoAlertPresentException e) {
			// none
		}
	}

	public String notice() {
		try {
			List<WebElement> found = displayed(By.xpath("//div[contains(@class,'swal2-popup')] | //div[contains(@class,'toast')]"
					+ " | //div[contains(@class,'notyf')] | //div[contains(@class,'alert') and not(contains(@class,'modal'))]"));
			return found.isEmpty() ? "" : found.get(0).getText().trim().replaceAll("\\s+", " ");
		} catch (StaleElementReferenceException e) {
			return "";
		}
	}

	// clicks the OK / Yes / Delete button of a sweet alert; returns its text ("" when there is none)
	public String Accept_notice() {
		String text = notice();
		List<WebElement> ok = displayed(By.xpath("//button[contains(@class,'swal2-confirm')]"));
		if (!ok.isEmpty()) {
			click_safely(ok.get(0));
			pause(1000);
		}
		return text;
	}

	// clicks the button of an open sweet alert whose text contains 'text' (Cancel, No, Yes, Delete ...)
	public void Click_notice_button(String text) {
		for (WebElement b : displayed(By.xpath("//div[contains(@class,'swal2-popup')]//button"))) {
			if (b.getText().trim().toLowerCase().contains(text.toLowerCase())) {
				click_safely(b);
				pause(1000);
				return;
			}
		}
		throw new NoSuchElementException("The dialog has no button: " + text);
	}

	// true when a button with exactly this text is displayed
	public boolean has_button(String text) {
		return !displayed(By.xpath("//button[normalize-space()='" + text + "'] | //a[normalize-space()='" + text + "']")).isEmpty();
	}

	// the buttons of an open sweet alert
	public List<String> notice_buttons() {
		List<String> out = new ArrayList<String>();
		for (WebElement b : displayed(By.xpath("//div[contains(@class,'swal2-popup')]//button"))) {
			String t = b.getText().trim();
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	public List<String> validation_messages() {
		List<String> out = new ArrayList<String>();
		for (WebElement e : displayed(By.xpath("//*[starts-with(normalize-space(text()),'Please')]"
				+ " | //*[contains(@class,'invalid-feedback') or contains(@class,'text-danger') or contains(@class,'error')][normalize-space()!='']"))) {
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

	// every message the page gave after an action: browser alert (accepted), sweet alert / toast and validation texts
	public String All_messages() {
		StringBuilder sb = new StringBuilder();
		String alert = Alert_text();
		if (!alert.isEmpty()) {
			sb.append("alert: ").append(alert).append(" | ");
		}
		String n = notice();
		if (!n.isEmpty()) {
			sb.append("notice: ").append(n).append(" | ");
		}
		List<String> v = validation_messages();
		if (!v.isEmpty()) {
			sb.append("validation: ").append(v);
		}
		return sb.toString();
	}
}
