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
 * Page class of Field Management > Collections (one LIST screen, no create form).
 *
 * Top: Search button (opens the search form), the "Approve All" tick box and the table filter "Search:".
 * Search form: Hierarchy Type, Hierarchy Name, Employee, Customer (search boxes), Receipt Mode, Receipt Status,
 * From Date, To Date, Clear Filter, Search. The dates start on today (dd/MM/yyyy).
 * Result table columns (cell index): 0 supply by, 1 employee, 2 customer name, 3 receipt number, 4 date and time,
 * 5 receipt mode, 6 receipt type, 7 details (eye icon), 8 amount, 9 receipt status, 10 action (a tick box = approve
 * and a red x = reject).
 * The eye opens the "Collection Details" pop-up: invoices or receipt number with amounts, denominations (cash),
 * UPI details or cheque details.
 * Approve All, the row tick box and the red x change records: Confirm_action() handles what they ask.
 * Typing: an empty field is typed into directly, without clear().
 */
public class P_Collections_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// the suggestion lines of a search box
	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

	// top-left Search button (the second 'Search' is the one inside the search form)
	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[1]")
	WebElement Top_search_btn;

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[2]")
	WebElement Search_go;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Clear Filter']")
	WebElement Clear_filter_btn;

	@FindBy(xpath = "//*[contains(text(),'Showing')]")
	WebElement Entries_info;

	@FindBy(xpath = "//*[normalize-space(text())='Previous']")
	WebElement Previous_btn;

	@FindBy(xpath = "//*[normalize-space(text())='Next']")
	WebElement Next_btn;

	// ---------- Constructor ----------
	public P_Collections_Cls(WebDriver driver) {
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

	private void js_click(WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", element);
		pause(500);
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
		wait.until(d -> !select.getOptions().isEmpty());
		for (int i = 0; i < select.getOptions().size(); i++) {
			String option = select.getOptions().get(i).getText().trim().toLowerCase();
			if (option.equals(text.trim().toLowerCase()) || option.startsWith(text.trim().toLowerCase())) {
				select.selectByIndex(i);
				return;
			}
		}
		throw new NoSuchElementException("Option not found in the drop down: " + text);
	}

	// ALL the option texts of a drop down (here the first option is a real value, for example Factory or All)
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

	// the text of the option that is currently chosen in a drop down
	public String selected(WebElement dropdown) {
		return new Select(dropdown).getFirstSelectedOption().getText().trim();
	}

	private void pause(int millis) {
		WaitUtil.settle(driver, millis);
	}

	private WebElement label_input(String label) {
		return first_displayed(By.xpath("//label[normalize-space()='" + label
				+ "']/following::input[not(@type='hidden')][1]"));
	}

	private WebElement label_select(String label) {
		return first_displayed(By.xpath("//label[normalize-space()='" + label + "']/following::select[1]"));
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

	public WebElement verify_entries() {
		return Entries_info;
	}

	public void Click_next() {
		click_safely(Next_btn);
	}

	public void Click_previous() {
		click_safely(Previous_btn);
	}

	public boolean next_enabled() {
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Next']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	// the "Search:" box above the table
	public WebElement verify_table_filter() {
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_filter')]//input | //input[@type='search']"
				+ " | //label[normalize-space()='Search:']/input | //*[normalize-space()='Search:']/following::input[1]"));
	}

	public void Filter_table(String text) {
		type(verify_table_filter(), text);
		pause(1500);
	}

	public boolean is_list_page() {
		try {
			return Top_search_btn.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	// the tick box found by its label text (JavaScript: also works for hidden or custom tick boxes)
	public WebElement approve_all_box() {
		Object found = ((JavascriptExecutor) driver).executeScript(
				"var all = document.querySelectorAll('label, span, div, b, strong, a');"
				+ " for (var i = 0; i < all.length; i++) {"
				+ "   var t = (all[i].textContent || '').replace(/\\s+/g, ' ').trim();"
				+ "   if (t === 'Approve All') {"
				+ "     if (all[i].htmlFor) { var x = document.getElementById(all[i].htmlFor); if (x) { return x; } }"
				+ "     var own = all[i].querySelector(\"input[type='checkbox']\"); if (own) { return own; }"
				+ "     var p = all[i];"
				+ "     for (var k = 0; k < 3 && p; k++) {"
				+ "       var list = p.querySelectorAll(\"input[type='checkbox']\");"
				+ "       for (var m = list.length - 1; m >= 0; m--) {"
				+ "         if (list[m].compareDocumentPosition(all[i]) & Node.DOCUMENT_POSITION_FOLLOWING) { return list[m]; } }"
				+ "       p = p.parentElement; } } }"
				+ " return null;");
		if (found == null) {
			throw new NoSuchElementException("The Approve All tick box was not found");
		}
		return (WebElement) found;
	}

	// ======================= search form =======================

	public WebElement verify_type() {
		return label_select("Hierarchy Type");
	}

	public WebElement verify_hierarchy() {
		return label_input("Hierarchy Name");
	}

	public WebElement verify_employee() {
		return label_input("Employee");
	}

	public WebElement verify_customer() {
		return label_input("Customer");
	}

	public WebElement verify_mode() {
		return label_select("Receipt Mode");
	}

	public WebElement verify_status() {
		return label_select("Receipt Status");
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

	public WebElement verify_clear_filter() {
		return Clear_filter_btn;
	}

	public boolean search_form_open() {
		return !displayed(By.xpath("//label[normalize-space()='Receipt Mode']")).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			click_safely(Top_search_btn);
			pause(800);
		}
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches.
	// Dates are written like the screen shows them: dd/MM/yyyy
	public void Search_collections(String type, String hierarchy, String employee, String customer, String mode,
			String status, String from, String to) {
		Open_search_form();
		if (!type.isEmpty()) {
			select_text(verify_type(), type);
			pause(800);
		}
		if (!hierarchy.isEmpty()) {
			search_pick(verify_hierarchy(), hierarchy);
		}
		if (!employee.isEmpty()) {
			search_pick(verify_employee(), employee);
		}
		if (!customer.isEmpty()) {
			search_pick(verify_customer(), customer);
		}
		if (!mode.isEmpty()) {
			select_text(verify_mode(), mode);
		}
		if (!status.isEmpty()) {
			select_text(verify_status(), status);
		}
		if (!from.isEmpty()) {
			set_date(verify_from(), from);
		}
		if (!to.isEmpty()) {
			set_date(verify_to(), to);
		}
		click_safely(Search_go);
		pause(3000);
	}

	public void Click_clear_filter() {
		Open_search_form();
		click_safely(Clear_filter_btn);
		pause(2000);
	}

	// ======================= result table =======================

	// the table header texts in capital letters (line breaks turned into spaces)
	public List<String> headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : displayed(By.xpath("//table//th"))) {
			out.add(th.getText().trim().toUpperCase().replaceAll("\\s+", " "));
		}
		return out;
	}

	// the data rows of the result table (the "No Data Available" row has only one cell, so it is not counted)
	private List<WebElement> result_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() > 5
						&& !row.getText().contains("No Data Available")) {
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
		return !displayed(By.xpath("//*[normalize-space(text())='No Data Available']")).isEmpty();
	}

	// the cell texts of every result row (line breaks turned into spaces): [supply by, employee, customer,
	// receipt number, date and time, mode, type, details, amount, status, action]
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

	// the cells of the row of one receipt number (null when it is not in the table)
	public List<String> row_of(String receipt) {
		for (List<String> cells : result_texts()) {
			if (cells.size() > 9 && cells.get(3).equalsIgnoreCase(receipt)) {
				return cells;
			}
		}
		return null;
	}

	private WebElement row_by_receipt(String receipt) {
		for (WebElement row : result_rows()) {
			if (row.findElements(By.tagName("td")).get(3).getText().trim().equalsIgnoreCase(receipt)) {
				return row;
			}
		}
		throw new NoSuchElementException("Receipt not found in the list: " + receipt);
	}

	// ======================= details pop-up =======================

	public void Open_details(String receipt) {
		WebElement row = row_by_receipt(receipt);
		List<WebElement> icons = row.findElements(By.xpath("./td[8]//a | ./td[8]//button | ./td[8]//i"));
		if (icons.isEmpty()) {
			throw new NoSuchElementException("The row has no details icon: " + receipt);
		}
		click_safely(icons.get(0));
		first_displayed(By.xpath("//*[normalize-space(text())='Collection Details']"));
		pause(800);
	}

	public boolean details_open() {
		return !displayed(By.xpath("//*[normalize-space(text())='Collection Details']")).isEmpty();
	}

	// the text of the open details pop-up
	public String details_text() {
		Object text = ((JavascriptExecutor) driver).executeScript(
				"var list = Array.prototype.slice.call(document.querySelectorAll('.modal'));"
				+ " for (var i = 0; i < list.length; i++) {"
				+ "   if (list[i].textContent.indexOf('Collection Details') >= 0 && list[i].offsetParent !== null) {"
				+ "     return list[i].textContent.replace(/\\s+/g, ' ').trim(); } }"
				+ " return '';");
		return String.valueOf(text);
	}

	// every table of the open details pop-up: a list of tables, each a list of rows, each a list of cell texts
	@SuppressWarnings("unchecked")
	public List<List<List<String>>> details_tables() {
		Object result = ((JavascriptExecutor) driver).executeScript(
				"var list = Array.prototype.slice.call(document.querySelectorAll('.modal'));"
				+ " var m = null;"
				+ " for (var i = 0; i < list.length; i++) {"
				+ "   if (list[i].textContent.indexOf('Collection Details') >= 0 && list[i].offsetParent !== null) { m = list[i]; } }"
				+ " if (!m) { return []; }"
				+ " var out = [];"
				+ " m.querySelectorAll('table').forEach(function(t) { var rows = [];"
				+ "   t.querySelectorAll('tr').forEach(function(tr) { var c = [];"
				+ "     tr.querySelectorAll('th, td').forEach(function(x) { c.push(x.textContent.replace(/\\s+/g, ' ').trim()); });"
				+ "     rows.push(c); });"
				+ "   out.push(rows); });"
				+ " return out;");
		return (List<List<List<String>>>) result;
	}

	public void Close_details() {
		List<WebElement> close = displayed(By.xpath("//div[contains(@class,'modal')]"
				+ "[.//*[normalize-space(text())='Collection Details']]//button"));
		if (!close.isEmpty()) {
			click_safely(close.get(0));
			pause(1000);
		}
		if (details_open()) {
			driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
			pause(800);
		}
	}

	// ======================= row actions (they change records) =======================

	private String dialog_text() {
		List<WebElement> d = displayed(By.xpath("//div[contains(@class,'swal2-popup')]"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')]//div[contains(@class,'modal-body')]"));
		return d.isEmpty() ? "" : d.get(0).getText().trim();
	}

	// Confirms what an action asks (browser alert or pop-up with Yes / OK / Approve / Reject / Confirm) and also
	// accepts the message that follows. Returns every message that was shown.
	public List<String> Confirm_action() {
		List<String> texts = new ArrayList<String>();
		for (int round = 0; round < 4; round++) {
			boolean handled = false;
			long end = System.currentTimeMillis() + (round == 0 ? 5000 : 2500);
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
						+ " or normalize-space()='OK' or normalize-space()='Ok' or normalize-space()='Confirm'"
						+ " or normalize-space()='Approve' or normalize-space()='Reject']"));
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

	// Dismisses what an action asks (Cancel / No / browser Cancel) and returns its text ("" when nothing was asked)
	public String Cancel_action() {
		long end = System.currentTimeMillis() + 5000;
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
					+ " or normalize-space()='Cancel']"));
			if (!cancel.isEmpty()) {
				String text = dialog_text();
				click_safely(cancel.get(0));
				return text;
			}
			pause(300);
		}
		return "";
	}

	// ticks the Approve box of a row and confirms; returns the messages that were shown
	public List<String> Tick_approve(String receipt) {
		WebElement box = row_by_receipt(receipt).findElement(By.xpath("./td[last()]//input[@type='checkbox']"));
		js_click(box);
		List<String> messages = Confirm_action();
		pause(2000);
		return messages;
	}

	// ticks the Approve box of a row and dismisses the question; returns its text ("" when nothing was asked)
	public String Tick_approve_and_cancel(String receipt) {
		WebElement box = row_by_receipt(receipt).findElement(By.xpath("./td[last()]//input[@type='checkbox']"));
		js_click(box);
		String text = Cancel_action();
		pause(1500);
		return text;
	}

	// clicks the red x of a row and confirms; returns the messages that were shown
	public List<String> Click_reject(String receipt) {
		List<WebElement> icons = row_by_receipt(receipt).findElements(By.xpath(
				"./td[last()]//a | ./td[last()]//button | ./td[last()]//i | ./td[last()]//span"));
		if (icons.isEmpty()) {
			throw new NoSuchElementException("The row has no reject icon: " + receipt);
		}
		click_safely(icons.get(icons.size() - 1));
		List<String> messages = Confirm_action();
		pause(2000);
		return messages;
	}

	// the number of row tick boxes that are ticked / the number of rows that have one
	public int ticked_row_boxes() {
		int n = 0;
		for (WebElement row : result_rows()) {
			List<WebElement> boxes = row.findElements(By.xpath("./td[last()]//input[@type='checkbox']"));
			if (!boxes.isEmpty() && boxes.get(0).isSelected()) {
				n++;
			}
		}
		return n;
	}

	public int row_box_count() {
		int n = 0;
		for (WebElement row : result_rows()) {
			if (!row.findElements(By.xpath("./td[last()]//input[@type='checkbox']")).isEmpty()) {
				n++;
			}
		}
		return n;
	}

	// ======================= messages =======================

	// the red "Please ..." messages (a toast that disappears while reading is skipped)
	public List<String> validation_messages() {
		List<String> out = new ArrayList<String>();
		for (WebElement e : displayed(By.xpath("//*[starts-with(normalize-space(text()),'Please')]"))) {
			try {
				String t = e.getText().trim();
				if (!t.isEmpty() && !out.contains(t)) {
					out.add(t);
				}
			} catch (StaleElementReferenceException ex) {
				// the toast is gone: skip it
			}
		}
		return out;
	}

	// a pop-up / toast / alert text (empty when there is none or it disappears while reading)
	public String notice() {
		try {
			List<WebElement> found = displayed(By.xpath("//div[contains(@class,'swal2-popup') or contains(@class,'toast')"
					+ " or contains(@class,'alert') or contains(@class,'notyf')]"));
			return found.isEmpty() ? "" : found.get(0).getText().trim();
		} catch (StaleElementReferenceException e) {
			return "";
		}
	}
}