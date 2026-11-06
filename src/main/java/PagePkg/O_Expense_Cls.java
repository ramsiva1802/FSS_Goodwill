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
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * Page class of Field Management > Expense.
 *
 * The page has two screens:
 *   1. LIST screen : Search / Create buttons, search form, table filter, result table (expense type, designation,
 *                    employee name, rate, daily expense, recurring expense and a view, edit and DELETE icon per row),
 *                    paging
 *   2. FORM screen : create / view / edit. Company (fixed), Expense Type (search box + Add pop-up with expense code,
 *                    expense type and expense category), Is Attendance Expense and Is Daily Recurring (Yes / No),
 *                    5 tick boxes (rate per KM, rate editable, odometer required, image needed, end KM needed),
 *                    Rate, Zone, Filter Type (Employee Wise / Designation Wise), Designation (Employee Wise only),
 *                    a table of employees or designations with an ALL tick box, Back, Submit.
 * Rules seen on the page: Daily Recurring = Yes locks Attendance and the 5 tick boxes; Attendance = Yes locks Daily
 * Recurring.
 * Messages are red toasts ("Please enter rate" ...).
 * Typing: an empty field is typed into directly, without clear().
 */
public class O_Expense_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// the radio groups
	public static final String ATTENDANCE = "Is Attendance Expense";
	public static final String DAILY = "Is Daily Recurring";

	// the suggestion lines of a search box
	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

	// ---------- LIST screen ----------
	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Create']")
	WebElement Create_btn;

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[1]")
	WebElement Top_search_btn;

	@FindBy(xpath = "//*[contains(text(),'Showing')]")
	WebElement Entries_info;

	@FindBy(xpath = "//*[normalize-space(text())='Previous']")
	WebElement Previous_btn;

	@FindBy(xpath = "//*[normalize-space(text())='Next']")
	WebElement Next_btn;

	// ---------- FORM screen buttons: always the VISIBLE one ----------
	static final By SUBMIT = By.xpath("//*[self::button or self::a][normalize-space()='Submit']"
			+ "[not(ancestor::div[contains(@class,'modal')])]");
	static final By BACK = By.xpath("//*[self::button or self::a][normalize-space()='Back']");
	static final By ADD = By.xpath("//*[self::button or self::a][normalize-space()='Add']");

	// ---------- Constructor ----------
	public O_Expense_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
		PageFactory.initElements(driver, this);
	}

	// ======================= helpers =======================

	// the visible elements that match a locator
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

	// the first visible element that matches a locator (waits for it)
	private WebElement first_displayed(By by) {
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	// Scrolls the element to the middle of the screen, then clicks it.
	// If the fixed footer still covers it, the click is done through JavaScript instead.
	private void click_safely(WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			element.click();
		} catch (ElementClickInterceptedException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	// scrolls the element into view and clicks it through JavaScript (also for boxes inside a scrolling table)
	private void js_click(WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", element);
		pause(400);
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

	// Picks the option whose text equals 'text' or starts with it (capital letters ignored)
	private void select_text(WebElement dropdown, String text) {
		Select select = new Select(dropdown);
		wait.until(d -> select.getOptions().size() > 1);
		for (int i = 0; i < select.getOptions().size(); i++) {
			String option = select.getOptions().get(i).getText().trim().toLowerCase();
			if (option.equals(text.trim().toLowerCase()) || option.startsWith(text.trim().toLowerCase())) {
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

	// the text of the option that is currently chosen in a drop down
	public String selected(WebElement dropdown) {
		return new Select(dropdown).getFirstSelectedOption().getText().trim();
	}

	private void pause(int millis) {
		WaitUtil.settle(driver, millis);
	}

	// a label that may end with a red * : "Rate", "Rate*" or "Rate *"
	private static String label_xpath(String text) {
		return "//label[normalize-space()='" + text + "' or normalize-space()='" + text + "*' or normalize-space()='"
				+ text + " *']";
	}

	private WebElement label_input(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::input[not(@type='hidden')][1]"));
	}

	private WebElement label_select(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::select[1]"));
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

	// true when the field cannot be changed: disabled, read-only, or locked by its style / a locked parent
	public boolean is_locked(WebElement element) {
		Object result = ((JavascriptExecutor) driver).executeScript("var e = arguments[0];"
				+ " var s = window.getComputedStyle(e);"
				+ " return e.disabled === true || e.readOnly === true || s.pointerEvents === 'none'"
				+ " || e.hasAttribute('disabled') || e.hasAttribute('readonly')"
				+ " || e.classList.contains('disabled') || e.classList.contains('readonly')"
				+ " || e.closest('.disabled, [disabled], .readonly') !== null;", element);
		return Boolean.TRUE.equals(result);
	}

	// ======================= LIST screen =======================

	public WebElement verify_create_btn() {
		return Create_btn;
	}

	public WebElement verify_top_search() {
		return Top_search_btn;
	}

	public WebElement verify_entries() {
		return Entries_info;
	}

	public void Click_next() {
		click_safely(Next_btn);
	}

	public void Click_previous() {
		click_safely(Previous_btn);
	}

	// the "Search:" box above the table
	public WebElement verify_table_filter() {
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_filter')]//input | //input[@type='search']"
				+ " | //label[normalize-space()='Search:']/input | //*[normalize-space()='Search:']/following::input[1]"));
	}

	// types into the "Search:" box above the table (it filters the rows of the list)
	public void Filter_table(String text) {
		type(verify_table_filter(), text);
		pause(1500);
	}

	public boolean is_list_page() {
		try {
			return Create_btn.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	// the form screen (create, view or edit) is the one with a Back button
	public boolean is_form_screen() {
		try {
			return !displayed(BACK).isEmpty();
		} catch (Exception e) {
			return false;
		}
	}

	public boolean submit_visible() {
		try {
			return !displayed(SUBMIT).isEmpty();
		} catch (Exception e) {
			return false;
		}
	}

	public void Click_create() {
		click_safely(Create_btn);
	}

	public void Click_top_search() {
		click_safely(Top_search_btn);
	}

	// the labels that are visible on the list screen once the search form is open (for the test report)
	public List<String> search_form_labels() {
		List<String> out = new ArrayList<String>();
		for (WebElement l : displayed(By.xpath("//label"))) {
			String t = l.getText().trim();
			if (!t.isEmpty() && !t.equals("Search:")) {
				out.add(t);
			}
		}
		return out;
	}

	// the table header texts in capital letters
	public List<String> headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : displayed(By.xpath("//table//th"))) {
			out.add(th.getText().trim().toUpperCase());
		}
		return out;
	}

	// the data rows of the result table (the "No data" row has only one cell, so it is not counted)
	private List<WebElement> result_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() > 1
						&& row.findElements(By.xpath(".//input[@type='checkbox']")).isEmpty()) {
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

	// the cell texts of every result row: [expense type, designation, employee, rate, daily, recurring, actions]
	public List<List<String>> result_texts() {
		List<List<String>> out = new ArrayList<List<String>>();
		for (WebElement row : result_rows()) {
			try {
				List<String> cells = new ArrayList<String>();
				for (WebElement td : row.findElements(By.tagName("td"))) {
					cells.add(td.getText().trim());
				}
				out.add(cells);
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return out;
	}

	// the cells of the row of one expense type and employee (the employee may be part of the text; "" = any)
	public List<String> row_of(String expense_type, String employee) {
		for (List<String> cells : result_texts()) {
			if (cells.size() > 3 && cells.get(0).equalsIgnoreCase(expense_type)
					&& cells.get(2).toLowerCase().contains(employee.toLowerCase())) {
				return cells;
			}
		}
		return null;
	}

	// waits until the table shows the row (true) or the wait is over (false)
	public boolean Wait_for_row(String expense_type, String employee) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12), Duration.ofMillis(100)).ignoring(StaleElementReferenceException.class)
					.until(d -> row_of(expense_type, employee) != null);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// true when the row is no longer in the table (waits a little)
	public boolean Wait_for_no_row(String expense_type, String employee) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10), Duration.ofMillis(100)).ignoring(StaleElementReferenceException.class)
					.until(d -> row_of(expense_type, employee) == null);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// the view, edit and delete icons of one row (links first, otherwise the icons themselves)
	private List<WebElement> action_icons(String expense_type, String employee) {
		for (WebElement row : result_rows()) {
			List<WebElement> cells = row.findElements(By.tagName("td"));
			if (cells.get(0).getText().trim().equalsIgnoreCase(expense_type)
					&& cells.get(2).getText().toLowerCase().contains(employee.toLowerCase())) {
				List<WebElement> icons = row.findElements(By.xpath(".//td[last()]//a | .//td[last()]//button"));
				if (icons.size() < 3) {
					icons = row.findElements(By.xpath(".//td[last()]//i"));
				}
				return icons;
			}
		}
		throw new NoSuchElementException("Expense row not found in the list: " + expense_type + " / " + employee);
	}

	public int action_icon_count(String expense_type, String employee) {
		return action_icons(expense_type, employee).size();
	}

	public void Click_view(String expense_type, String employee) {
		click_safely(action_icons(expense_type, employee).get(0));
	}

	public void Click_edit(String expense_type, String employee) {
		click_safely(action_icons(expense_type, employee).get(1));
	}

	public void Click_delete(String expense_type, String employee) {
		click_safely(action_icons(expense_type, employee).get(2));
	}

	// ======================= delete confirmation =======================

	private String dialog_text() {
		List<WebElement> d = displayed(By.xpath("//div[contains(@class,'swal2-popup')]"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')]//div[contains(@class,'modal-body')]"));
		return d.isEmpty() ? "" : d.get(0).getText().trim();
	}

	// Confirms the delete (browser alert or pop-up with Yes / OK / Delete / Confirm) and also accepts the
	// message that follows. Returns every message that was shown.
	public List<String> Confirm_delete() {
		List<String> texts = new ArrayList<String>();
		for (int round = 0; round < 4; round++) {
			boolean handled = false;
			long end = System.currentTimeMillis() + (round == 0 ? 6000 : 2500);
			while (System.currentTimeMillis() < end && !handled) {
				try {
					Alert a = driver.switchTo().alert();
					texts.add(a.getText());
					a.accept();
					handled = true;
					break;
				} catch (NoAlertPresentException e) {
					// no browser alert: look for a pop-up
				}
				List<WebElement> ok = displayed(By.xpath("//button[contains(@class,'swal2-confirm')]"
						+ " | //div[contains(@class,'modal') and contains(@class,'show')]//button[normalize-space()='Yes'"
						+ " or normalize-space()='OK' or normalize-space()='Ok' or normalize-space()='Delete'"
						+ " or normalize-space()='Confirm']"));
				if (!ok.isEmpty()) {
					texts.add(dialog_text());
					click_safely(ok.get(0));
					handled = true;
					break;
				}
				pause(300);
			}
			if (!handled) {
				break;
			}
			pause(500);
		}
		return texts;
	}

	// Dismisses the delete confirmation (Cancel / No / browser Cancel) and returns its text
	public String Cancel_delete() {
		long end = System.currentTimeMillis() + 6000;
		while (System.currentTimeMillis() < end) {
			try {
				Alert a = driver.switchTo().alert();
				String text = a.getText();
				a.dismiss();
				return text;
			} catch (NoAlertPresentException e) {
				// no browser alert: look for a pop-up
			}
			List<WebElement> cancel = displayed(By.xpath("//button[contains(@class,'swal2-cancel')]"
					+ " | //div[contains(@class,'modal') and contains(@class,'show')]//button[normalize-space()='No'"
					+ " or normalize-space()='Cancel' or normalize-space()='Close']"));
			if (!cancel.isEmpty()) {
				String text = dialog_text();
				click_safely(cancel.get(0));
				return text;
			}
			pause(300);
		}
		return "";
	}

	// ======================= FORM screen: header =======================

	public WebElement verify_company() {
		return label_select("Company");
	}

	public WebElement verify_expense_type() {
		return label_input("Expense Type");
	}

	// types an expense type in the search box and clicks the matching suggestion
	public void Select_expense_type(String text) {
		search_pick(verify_expense_type(), text);
	}

	// the suggestions the Expense Type box offers for the typed text
	public List<String> expense_type_suggestions(String text) {
		type(verify_expense_type(), text);
		pause(1500);
		List<String> out = new ArrayList<String>();
		for (WebElement s : displayed(By.xpath(SUGGESTIONS))) {
			String t = s.getText().trim();
			if (!t.isEmpty() && !out.contains(t)) {
				out.add(t);
			}
		}
		return out;
	}

	public WebElement verify_add_btn() {
		return first_displayed(ADD);
	}

	public WebElement verify_rate() {
		return label_input("Rate");
	}

	public void Type_rate(String rate) {
		type(verify_rate(), rate);
	}

	public WebElement verify_zone() {
		return label_select("Zone");
	}

	public void Select_zone(String zone) {
		select_text(verify_zone(), zone);
		pause(800);
	}

	public WebElement verify_filter_type() {
		return label_select("Filter Type");
	}

	public void Select_filter_type(String filter) {
		select_text(verify_filter_type(), filter);
		pause(1500);
	}

	// the Designation drop down (shown for Employee Wise only)
	public WebElement verify_designation() {
		return label_select("Designation");
	}

	public boolean designation_select_visible() {
		return !displayed(By.xpath(label_xpath("Designation") + "/following::select[1]")).isEmpty()
				&& !displayed(By.xpath(label_xpath("Designation"))).isEmpty();
	}

	public void Select_designation(String designation) {
		select_text(verify_designation(), designation);
		pause(2000);
	}

	// ======================= radio groups and tick boxes =======================

	// the 2 radio buttons (Yes, No) of a group
	private List<WebElement> radios(String group) {
		By[] options = { By.xpath("//fieldset[legend[normalize-space()='" + group + "']]//input[@type='radio']"),
				By.xpath("//legend[normalize-space()='" + group + "']/following::input[@type='radio'][position()<=2]"),
				By.xpath("//*[self::div or self::span or self::p or self::b or self::i or self::label]"
						+ "[normalize-space(text())='" + group + "']/following::input[@type='radio'][position()<=2]") };
		for (By by : options) {
			List<WebElement> found = driver.findElements(by);
			if (!found.isEmpty()) {
				return found;
			}
		}
		throw new NoSuchElementException("Radio group not found: " + group);
	}

	// the chosen option of a group ("Yes", "No" or "" when none is chosen)
	public String radio_selected(String group) {
		List<WebElement> list = radios(group);
		if (list.get(0).isSelected()) {
			return "Yes";
		}
		return list.get(1).isSelected() ? "No" : "";
	}

	// true when the radio buttons of a group cannot be changed
	public boolean radio_locked(String group) {
		return is_locked(radios(group).get(0));
	}

	public void Set_radio(String group, String option) {
		List<WebElement> list = radios(group);
		js_click(option.equalsIgnoreCase("Yes") ? list.get(0) : list.get(1));
	}

	// the tick box of a label text, found with JavaScript. keys: perkm, editable, odometer, image, endkm
	public WebElement checkbox(String key) {
		String text;
		switch (key) {
		case "perkm":
			text = "Is Rate Per KM";
			break;
		case "editable":
			text = "Is Rate Editable";
			break;
		case "odometer":
			text = "Is Odometer Required";
			break;
		case "image":
			text = "Is Image Needed";
			break;
		default:
			text = "Is End KM Needed";
		}
		Object found = ((JavascriptExecutor) driver).executeScript(
				"var text = arguments[0].toLowerCase(); var cands = [];"
				+ " var all = document.querySelectorAll('label, span, div, legend, p, b, strong, td, small, a');"
				+ " for (var i = 0; i < all.length; i++) {"
				+ "   var t = (all[i].textContent || '').replace(/\\s+/g, ' ').trim().toLowerCase();"
				+ "   if (t.indexOf(text) === 0 && t.length <= text.length + 6) { cands.push(all[i]); } }"
				+ " var el = null;"
				+ " for (var i = 0; i < cands.length; i++) { var inner = false;"
				+ "   for (var j = 0; j < cands.length; j++) { if (i !== j && cands[i].contains(cands[j])) { inner = true; break; } }"
				+ "   if (!inner) { el = cands[i]; break; } }"
				+ " if (!el) { return null; }"
				+ " if (el.htmlFor) { var t2 = document.getElementById(el.htmlFor); if (t2) { return t2; } }"
				+ " var own = el.querySelector(\"input[type='checkbox']\"); if (own) { return own; }"
				+ " var p = el;"
				+ " for (var k = 0; k < 4 && p; k++) {"
				+ "   var list = p.querySelectorAll(\"input[type='checkbox']\"); var best = null;"
				+ "   for (var m = 0; m < list.length; m++) {"
				+ "     if (list[m].compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING) { best = list[m]; } }"
				+ "   if (best) { return best; }"
				+ "   for (var m = 0; m < list.length; m++) {"
				+ "     if (el.compareDocumentPosition(list[m]) & Node.DOCUMENT_POSITION_FOLLOWING) { return list[m]; } }"
				+ "   p = p.parentElement; }"
				+ " return null;", text);
		if (found == null) {
			throw new NoSuchElementException("Tick box not found: " + text);
		}
		return (WebElement) found;
	}

	public void Set_checkbox(String key, boolean on) {
		WebElement box = checkbox(key);
		if (box.isSelected() != on) {
			js_click(box);
		}
	}

	// ======================= employee / designation table =======================

	// the rows of the table below the filter (the ones with a tick box in a cell; the ALL box is in a th)
	private List<WebElement> table_rows() {
		return displayed(By.xpath("//tr[td//input[@type='checkbox']]"));
	}

	public int table_row_count() {
		return table_rows().size();
	}

	// the texts of every row of the table (without the tick box cell), for example "TEST | Sivakumar (TEST)"
	public List<String> table_texts() {
		List<String> out = new ArrayList<String>();
		for (WebElement row : table_rows()) {
			List<WebElement> cells = row.findElements(By.tagName("td"));
			StringBuilder sb = new StringBuilder();
			for (int i = 1; i < cells.size(); i++) {
				if (sb.length() > 0) {
					sb.append(" | ");
				}
				sb.append(cells.get(i).getAttribute("textContent").trim());
			}
			out.add(sb.toString());
		}
		return out;
	}

	// the column headers of the table below the filter
	public String table_headers() {
		StringBuilder sb = new StringBuilder();
		for (WebElement th : displayed(By.xpath("//th[.//input[@type='checkbox'] or normalize-space()!='']"))) {
			sb.append(th.getText().trim().toUpperCase()).append(" ");
		}
		return sb.toString().trim();
	}

	// the "Showing 1 to 11 of 11 entries" text below the table (empty when there is none)
	public String table_entries_text() {
		List<WebElement> found = displayed(By.xpath("//*[contains(text(),'Showing')]"));
		return found.isEmpty() ? "" : found.get(0).getText().trim();
	}

	// ticks the row whose text contains 'text' (an employee code, an employee name or a designation)
	public void Tick_table_row(String text) {
		for (WebElement row : table_rows()) {
			if (row.getAttribute("textContent").toLowerCase().contains(text.toLowerCase())) {
				WebElement box = row.findElements(By.xpath(".//input[@type='checkbox']")).get(0);
				if (!box.isSelected()) {
					js_click(box);
				}
				return;
			}
		}
		throw new NoSuchElementException("Row not found in the table: " + text);
	}

	public int ticked_rows() {
		int n = 0;
		for (WebElement row : table_rows()) {
			if (row.findElements(By.xpath(".//input[@type='checkbox']")).get(0).isSelected()) {
				n++;
			}
		}
		return n;
	}

	// the ALL tick box of the table header
	public WebElement verify_all_box() {
		return first_displayed(By.xpath("//th//input[@type='checkbox']"));
	}

	public void Tick_all() {
		if (!verify_all_box().isSelected()) {
			js_click(verify_all_box());
			pause(600);
		}
	}

	public void Untick_all() {
		if (verify_all_box().isSelected()) {
			js_click(verify_all_box());
			pause(600);
		}
	}

	public WebElement verify_submit() {
		return first_displayed(SUBMIT);
	}

	public WebElement verify_back() {
		return first_displayed(BACK);
	}

	public void Click_submit() {
		click_safely(first_displayed(SUBMIT));
	}

	public void Click_back() {
		click_safely(first_displayed(BACK));
	}

	// ======================= Add Expense Type pop-up =======================

	public void Open_add_pop_up() {
		click_safely(first_displayed(ADD));
		first_displayed(By.xpath("//*[normalize-space(text())='Add Expense Type']"));
		pause(500);
	}

	public boolean add_pop_up_open() {
		return !displayed(By.xpath("//*[normalize-space(text())='Add Expense Type']")).isEmpty();
	}

	public WebElement verify_new_code() {
		return first_displayed(By.xpath("//input[@placeholder='Enter Expense Code']"));
	}

	public WebElement verify_new_type() {
		return first_displayed(By.xpath("//input[@placeholder='Enter Expense Type']"));
	}

	public WebElement verify_new_category() {
		return first_displayed(By.xpath("//label[normalize-space()='Expense Category']/following::select[1]"));
	}

	// the Save Changes button of the pop-up: the first button with "save" in its text after the "Add Expense Type" title
		private WebElement popup_submit() {
			By by = By.xpath("//*[normalize-space(text())='Add Expense Type']/following::button"
					+ "[contains(translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'save')][1]");
			wait.until(d -> !displayed(by).isEmpty());
			return displayed(by).get(0);
		}
		
	public void Click_pop_up_submit() {
		click_safely(popup_submit());
		pause(1200);
	}

	// fills the pop-up and submits it (category: Customer, Employee or Route)
	public void Add_expense_type(String code, String type, String category) {
		type(verify_new_code(), code);
		type(verify_new_type(), type);
		if (!category.isEmpty()) {
			select_text(verify_new_category(), category);
		}
		click_safely(popup_submit());
		pause(1800);
	}

	// closes the pop-up with a Close / Cancel / x control INSIDE the pop-up, then the Escape key as the last try
	public void Close_add_pop_up() {
		List<WebElement> close = displayed(By.xpath("//div[contains(@class,'modal')]"
				+ "[.//*[normalize-space(text())='Add Expense Type']]//*[self::button or self::span or self::a]"
				+ "[normalize-space()='Close' or normalize-space()='Cancel' or normalize-space()='×'"
				+ " or contains(@class,'close') or @aria-label='Close' or @data-bs-dismiss='modal'"
				+ " or @data-dismiss='modal']"));
		if (!close.isEmpty()) {
			click_safely(close.get(close.size() - 1));
			pause(1000);
		}
		if (add_pop_up_open()) {
			driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
			pause(800);
		}
		if (add_pop_up_open()) {
			((JavascriptExecutor) driver).executeScript("document.querySelectorAll('.modal.show').forEach(function(m) {"
					+ " var b = m.querySelector('[data-bs-dismiss], [data-dismiss], .btn-close, .close');"
					+ " if (b) { b.click(); } });");
			pause(800);
		}
	}

	// ======================= messages =======================

	// the red "Please ..." messages (toasts and texts under the fields)
	public List<String> validation_messages() {
		List<String> out = new ArrayList<String>();
		for (WebElement e : displayed(By.xpath("//*[starts-with(normalize-space(text()),'Please')]"))) {
			String t = e.getText().trim();
			if (!t.isEmpty() && !out.contains(t)) {
				out.add(t);
			}
		}
		return out;
	}

	// a pop-up / toast / alert text after Submit (empty when there is none)
	public String notice() {
		List<WebElement> found = displayed(By.xpath("//div[contains(@class,'swal2-popup') or contains(@class,'toast')"
				+ " or contains(@class,'alert') or contains(@class,'notyf')]"));
		return found.isEmpty() ? "" : found.get(0).getText().trim();
	}

	// accepts a browser alert if one is open and returns its text (empty when there is none)
	public String Accept_alert() {
		try {
			Alert a = driver.switchTo().alert();
			String text = a.getText();
			a.accept();
			return text;
		} catch (NoAlertPresentException e) {
			return "";
		}
	}

	// closes a sweet alert pop-up that is open
	public void Close_notice() {
		List<WebElement> ok = displayed(By.xpath("//button[contains(@class,'swal2-confirm')]"));
		if (!ok.isEmpty()) {
			click_safely(ok.get(0));
		}
	}
}