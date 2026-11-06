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
 * Page class of Field Management > PriceList (logged in as the web user).
 *
 * LIST: Search and Create buttons, the table filter "Search:"; columns (cell index) 0 company, 1 price list code,
 *   2 price list name, 3 from date, 4 to date (may be empty), 5 actions (eye = view, pencil = edit, bin = delete).
 *   Search form: Price List Code, Price List Name, Item Code, Item Name, From Date, To Date (dd/mm/yyyy), Reset, Search.
 * FORM PAGE (view / edit / create): "PRICE LIST DETAILS": Company Name*, Price List Code*, Price List Name*,
 *   From Date*, To Date* (date pickers); "ITEM LINES": a search box "Search added items...", the count "N item(s)",
 *   a table [#, item code (auto), item name (search box), distribution channel, unit (auto), unit rate, (bin)],
 *   "Add Item Row", Back and Submit. In view mode everything is locked and only Back is shown; in edit mode the
 *   company and the code are locked.
 * Typing: an empty field is typed into directly, without clear().
 */
public class W_PriceList_Cls {

	WebDriver driver;
	WebDriverWait wait;

	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

	// ---------- Constructor ----------
	public W_PriceList_Cls(WebDriver driver) {
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
		// the click is done a moment AFTER the script has returned: a big price list opens slowly and the script itself
		// must not wait for that page (it would end with "script timeout" after 30 s); the caller waits for the page
		((JavascriptExecutor) driver).executeScript(
				"var e = arguments[0]; e.scrollIntoView({block:'center'}); setTimeout(function () { e.click(); }, 30);", element);
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

	public List<String> all_options_of(WebElement dropdown) {
		List<String> texts = new ArrayList<String>();
		for (WebElement o : new Select(dropdown).getOptions()) {
			texts.add(o.getText().trim());
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

	// a label that starts with the text (the star of a required field is part of the label)
	private String label_xpath(String label) {
		return "//label[starts-with(normalize-space(), '" + label + "')]";
	}

	private WebElement label_input(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::input[not(@type='hidden')][1]"));
	}

	private WebElement label_select(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::select[1]"));
	}

	// types a date into a date box; a date picker box that does not keep it is set with JavaScript
	private void set_date(WebElement field, String value) {
		try {
			type(field, value);
			field.sendKeys(Keys.TAB);
		} catch (Exception e) {
			// a read only picker box: it is set below
		}
		String kept = field.getAttribute("value");
		if (kept == null || !kept.equals(value)) {
			((JavascriptExecutor) driver).executeScript("var e = arguments[0]; var v = arguments[1];"
					+ " if (e._flatpickr) { e._flatpickr.setDate(v, true, 'd/m/Y'); } else { e.value = v; }"
					+ " e.dispatchEvent(new Event('input', {bubbles: true}));"
					+ " e.dispatchEvent(new Event('change', {bubbles: true}));"
					+ " e.dispatchEvent(new Event('blur', {bubbles: true}));", field, value);
		}
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

	// types into a search box and picks the suggestion that contains the text (the first one otherwise); when the page
	// shows no list the first entry is taken with the arrow key and Enter
	private void search_pick(WebElement field, String text) {
		type(field, text);
		pause(1500);
		List<WebElement> found = displayed(By.xpath(SUGGESTIONS));
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
		if (!found.isEmpty()) {
			click_safely(found.get(0));
		} else {
			field.sendKeys(Keys.ARROW_DOWN, Keys.ENTER);
		}
		pause(1000);
	}

	// ======================= list =======================

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

	// one script instead of a search over the whole page: the visible "Showing x to y of z entries" text
	public String entries_text() {
		Object text = ((JavascriptExecutor) driver).executeScript(
				"var m = (document.body.innerText || '').match(/Showing[^\\n]*entries[^\\n]*/); return m ? m[0].trim() : '';");
		return text == null ? "" : String.valueOf(text);
	}

	public int entries_total() {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("of\\s+([\\d,]+)\\s+entries").matcher(entries_text());
		return m.find() ? Integer.parseInt(m.group(1).replace(",", "")) : 0;
	}

	public void Click_next() {
		click_safely(first_displayed(By.xpath("//*[normalize-space(text())='Next']")));
		pause(1000);
	}

	public void Click_previous() {
		click_safely(first_displayed(By.xpath("//*[normalize-space(text())='Previous']")));
		pause(1000);
	}

	public boolean previous_enabled() {
		return driver.findElements(By.xpath("//*[normalize-space(text())='Previous']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	public WebElement verify_table_filter() {
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_filter')]//input | //input[@type='search']"
				+ " | //label[normalize-space()='Search:']/input | //*[normalize-space()='Search:']/following::input[1]"));
	}

	// types in the table filter and waits (up to 6 seconds) until every listed row contains the text
	public void Filter_table(String text) {
		type(verify_table_filter(), text);
		pause(1500);
		long end = System.currentTimeMillis() + 6000;
		while (!text.isEmpty() && System.currentTimeMillis() < end) {
			boolean all = true;
			for (List<String> row : result_texts()) {
				if (!row.toString().toLowerCase().contains(text.toLowerCase())) {
					all = false;
				}
			}
			if (all) {
				break;
			}
			pause(500);
		}
	}

	// ---- search form ----

	public WebElement verify_code() {
		return first_displayed(By.xpath("//input[@placeholder='Enter Code']"));
	}

	public WebElement verify_name() {
		return first_displayed(By.xpath("//input[@placeholder='Enter Name']"));
	}

	public WebElement verify_item_code() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'Enter Item Code')]"));
	}

	public WebElement verify_item_name() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'Enter Item Name')]"));
	}

	public WebElement verify_from() {
		return label_input("From Date");
	}

	public WebElement verify_to() {
		return label_input("To Date");
	}

	public boolean search_form_open() {
		return !displayed(By.xpath("//input[@placeholder='Enter Code']")).isEmpty();
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

	public WebElement verify_reset() {
		return first_displayed(By.xpath("//*[self::button or self::a][normalize-space()='Reset']"));
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches
	public void Search_lists(String code, String name, String item_code, String item_name, String from, String to) {
		Open_search_form();
		if (!code.isEmpty()) {
			type(verify_code(), code);
		}
		if (!name.isEmpty()) {
			type(verify_name(), name);
		}
		if (!item_code.isEmpty()) {
			type(verify_item_code(), item_code);
		}
		if (!item_name.isEmpty()) {
			type(verify_item_name(), item_name);
		}
		if (!from.isEmpty()) {
			set_date(verify_from(), from);
		}
		if (!to.isEmpty()) {
			set_date(verify_to(), to);
		}
		click_safely(verify_search_go());
		pause(3000);
	}

	public void Click_reset() {
		Open_search_form();
		click_safely(verify_reset());
		pause(2500);
	}

	// ---- table ----

	public List<String> headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : displayed(By.xpath("//table//th"))) {
			String t = th.getText().trim().toUpperCase().replaceAll("\\s+", " ");
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	private List<WebElement> result_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td][not(.//input)]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() >= 6
						&& !row.getText().toLowerCase().contains("no data")) {
					rows.add(row);
				}
			} catch (StaleElementReferenceException e) {
				// skip a row that was replaced while reading
			}
		}
		return rows;
	}

	// the list rows as texts, read with ONE script (a call per cell made the page very slow)
	@SuppressWarnings("unchecked")
	private List<List<String>> list_rows_by_script() {
		Object rows = ((JavascriptExecutor) driver).executeScript(
				"var out = []; var trs = document.querySelectorAll('table tbody tr');"
				+ " for (var i = 0; i < trs.length; i++) { var r = trs[i];"
				+ "   if (r.offsetParent === null || r.cells.length < 6 || r.querySelector('input')) { continue; }"
				+ "   if ((r.innerText || '').toLowerCase().indexOf('no data') >= 0) { continue; }"
				+ "   var cells = []; for (var c = 0; c < r.cells.length; c++) { cells.push((r.cells[c].innerText || '').replace(/\\s+/g, ' ').trim()); }"
				+ "   out.push(cells); } return out;");
		return (List<List<String>>) rows;
	}

	public int result_count() {
		return list_rows_by_script().size();
	}

	public boolean no_data_shown() {
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr"))) {
			try {
				if (row.isDisplayed() && row.getText().toLowerCase().contains("no data")) {
					return true;
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return false;
	}

	// [company, code, name, from date, to date, actions]
	public List<List<String>> result_texts() {
		return list_rows_by_script();
	}

	// the index of the first row with this price list code (-1 when not on this page)
	public int row_of_code(String code) {
		List<List<String>> rows = result_texts();
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).get(1).equalsIgnoreCase(code)) {
				return i;
			}
		}
		return -1;
	}

	public void Click_header(String name) {
		for (WebElement th : displayed(By.xpath("//table//th"))) {
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
		if (h.contains("trash") || h.contains("delete") || h.contains("remove")) {
			return "delete";
		}
		if (h.contains("eye") || h.contains("view")) {
			return "view";
		}
		if (h.contains("edit") || h.contains("pencil") || h.contains("pen")) {
			return "edit";
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
				pause(1500);
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

	// clicks the bin of a row (the confirmation is NOT answered)
	public void Click_delete(int row) {
		click_action(row, "delete");
	}

	public void Click_create() {
		click_safely(verify_create_btn());
		pause(1500);
	}

	// ======================= form page (view / edit / create) =======================

	public boolean is_form_page() {
		try {
			return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript(
					"return (document.body.innerText || '').toUpperCase().indexOf('PRICE LIST DETAILS') >= 0;"));
		} catch (Exception e) {
			return false;
		}
	}

	public WebElement form_company() {
		return label_select("Company Name");
	}

	public WebElement form_code() {
		return label_input("Price List Code");
	}

	public WebElement form_name() {
		return label_input("Price List Name");
	}

	public WebElement form_from() {
		return label_input("From Date");
	}

	public WebElement form_to() {
		return label_input("To Date");
	}

	public List<String> company_options() {
		List<String> out = all_options_of(form_company());
		if (!out.isEmpty()) {
			out.remove(0);
		}
		return out;
	}

	// chooses the company; when the drop down is locked (a single company is preselected) nothing is chosen
	public void Choose_company(String text) {
		if (is_locked(form_company())) {
			return;
		}
		select_text(form_company(), text);
		pause(800);
	}

	public void Set_code(String text) {
		type(form_code(), text);
	}

	public void Set_name(String text) {
		type(form_name(), text);
	}

	public void Set_from(String text) {
		set_date(form_from(), text);
	}

	public void Set_to(String text) {
		set_date(form_to(), text);
	}

	// fills the details (an empty value leaves the field as it is)
	public void Fill_details(String company, String code, String name, String from, String to) {
		if (!company.isEmpty()) {
			Choose_company(company);
		}
		if (!code.isEmpty()) {
			Set_code(code);
		}
		if (!name.isEmpty()) {
			Set_name(name);
		}
		if (!from.isEmpty()) {
			Set_from(from);
		}
		if (!to.isEmpty()) {
			Set_to(to);
		}
	}

	// the "N item(s)" count shown beside the item lines (-1 when it is not shown)
	public int items_count_text() {
		Object n = ((JavascriptExecutor) driver).executeScript(
				"var m = (document.body.innerText || '').match(/(\\d+)\\s*item\\(s\\)/); return m ? parseInt(m[1], 10) : -1;");
		return n == null ? -1 : ((Number) n).intValue();
	}

	public WebElement lines_search_box() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'Search added items')]"));
	}

	public void Search_lines(String text) {
		type(lines_search_box(), text);
		pause(1000);
	}

	// the visible item lines (rows with a box and at least 6 cells), found with ONE script
	@SuppressWarnings("unchecked")
	private List<WebElement> line_rows() {
		return (List<WebElement>) ((JavascriptExecutor) driver).executeScript(
				"return Array.prototype.filter.call(document.querySelectorAll('table tbody tr'), function (r) {"
				+ " return r.offsetParent !== null && r.cells.length >= 6 && r.querySelector('input'); });");
	}

	public int line_count() {
		return line_rows().size();
	}

	private WebElement line_cell(int index, int cell) {
		return line_rows().get(index).findElements(By.tagName("td")).get(cell);
	}

	private String cell_value(WebElement td) {
		List<WebElement> inputs = td.findElements(By.xpath(".//input[not(@type='hidden')]"));
		if (!inputs.isEmpty()) {
			String v = inputs.get(0).getAttribute("value");
			return v == null ? "" : v.trim();
		}
		List<WebElement> selects = td.findElements(By.tagName("select"));
		if (!selects.isEmpty()) {
			return new Select(selects.get(0)).getFirstSelectedOption().getText().trim();
		}
		return td.getText().trim();
	}

	// the values of every item line: [#, item code, item name, channel, unit, unit rate]
	@SuppressWarnings("unchecked")
	public List<List<String>> line_texts() {
		Object rows = ((JavascriptExecutor) driver).executeScript(
				"var out = []; var trs = document.querySelectorAll('table tbody tr');"
				+ " for (var i = 0; i < trs.length; i++) { var r = trs[i];"
				+ "   if (r.offsetParent === null || r.cells.length < 6 || !r.querySelector('input')) { continue; }"
				+ "   var cells = [];"
				+ "   for (var c = 0; c < r.cells.length; c++) { var td = r.cells[c];"
				+ "     var box = td.querySelector('input:not([type=hidden])'); var sel = td.querySelector('select');"
				+ "     if (box) { cells.push((box.value || '').trim()); }"
				+ "     else if (sel) { cells.push(sel.selectedIndex >= 0 ? sel.options[sel.selectedIndex].text.trim() : ''); }"
				+ "     else { cells.push((td.innerText || '').replace(/\\s+/g, ' ').trim()); } }"
				+ "   out.push(cells); } return out;");
		return (List<List<String>>) rows;
	}

	private WebElement line_input(int index, int cell) {
		List<WebElement> inputs = line_cell(index, cell).findElements(By.xpath(".//input[not(@type='hidden')]"));
		if (inputs.isEmpty()) {
			throw new NoSuchElementException("The line " + (index + 1) + " has no box in column " + (cell + 1));
		}
		return inputs.get(0);
	}

	public WebElement line_code_box(int index) {
		return line_input(index, 1);
	}

	public WebElement line_name_box(int index) {
		return line_input(index, 2);
	}

	public WebElement line_unit_box(int index) {
		return line_input(index, 4);
	}

	public WebElement line_rate_box(int index) {
		return line_input(index, 5);
	}

	public WebElement line_channel_box(int index) {
		List<WebElement> s = line_cell(index, 3).findElements(By.tagName("select"));
		if (s.isEmpty()) {
			throw new NoSuchElementException("The line " + (index + 1) + " has no channel drop down");
		}
		return s.get(0);
	}

	public List<String> channel_options(int index) {
		return all_options_of(line_channel_box(index));
	}

	public void Choose_channel(int index, String text) {
		select_text(line_channel_box(index), text);
		pause(500);
	}

	public int bin_count() {
		Object n = ((JavascriptExecutor) driver).executeScript(
				"var n = 0; var trs = document.querySelectorAll('table tbody tr');"
				+ " for (var i = 0; i < trs.length; i++) { var r = trs[i];"
				+ "   if (r.offsetParent === null || r.cells.length < 7 || !r.querySelector('input')) { continue; }"
				+ "   var b = r.cells[r.cells.length - 1].querySelector('button, a, i');"
				+ "   if (b && b.offsetParent !== null) { n++; } } return n;");
		return n == null ? 0 : ((Number) n).intValue();
	}

	public void Remove_line(int index) {
		List<WebElement> tds = line_rows().get(index).findElements(By.tagName("td"));
		js_click(tds.get(tds.size() - 1).findElement(By.xpath(".//button | .//a | .//i")));
		pause(600);
	}

	public void Click_add_item_row() {
		click_safely(first_displayed(By.xpath("//button[contains(normalize-space(),'Add Item Row')]")));
		pause(800);
	}

	// types the item name into a line and returns the suggestions (nothing is picked)
	public List<String> Item_suggestions(int index, String text) {
		return Suggestions_for(line_name_box(index), text);
	}

	// types the item name into a line and picks it (the item code and unit are then filled by the page)
	public void Pick_line_item(int index, String text) {
		WebElement box = line_name_box(index);
		search_pick(box, text);
		box.sendKeys(Keys.TAB);
		pause(1000);
	}

	public void Set_line_rate(int index, String value) {
		WebElement box = line_rate_box(index);
		type(box, value);
		box.sendKeys(Keys.TAB);
		pause(500);
	}

	// adds a new line with an item, its rate and optionally a distribution channel
	public void Add_line(String item, String rate, String channel) {
		// the form starts with one empty line: it is used first; Add Item Row is clicked only when the last line is filled
		int i = line_count() - 1;
		if (i < 0 || !String.valueOf(line_name_box(i).getAttribute("value")).trim().isEmpty()) {
			Click_add_item_row();
			i = line_count() - 1;
		}
		Pick_line_item(i, item);
		if (!rate.isEmpty()) {
			Set_line_rate(i, rate);
		}
		if (!channel.isEmpty()) {
			Choose_channel(i, channel);
		}
	}

	public void Click_back() {
		click_safely(first_displayed(By.xpath("//button[normalize-space()='Back'] | //a[normalize-space()='Back']")));
		pause(1500);
	}

	public void Click_submit() {
		click_safely(first_displayed(By.xpath("//button[normalize-space()='Submit']")));
		pause(2500);
	}

	// true when a button with exactly this text is displayed
	public boolean has_button(String text) {
		return !displayed(By.xpath("//button[normalize-space()='" + text + "'] | //a[normalize-space()='" + text + "']")).isEmpty();
	}

	public boolean has_add_item_row() {
		return !displayed(By.xpath("//button[contains(normalize-space(),'Add Item Row')]")).isEmpty();
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
