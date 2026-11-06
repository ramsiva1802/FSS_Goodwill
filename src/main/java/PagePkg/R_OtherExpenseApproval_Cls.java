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
 * Page class of Field Management > Other Expense Approval (one LIST screen, no create form).
 *
 * Top: Search button (opens the search form), the "Approve All" tick box and the table filter "Search:".
 * Search form: Employee Type, Employee Name (search box), Expense Type, Status, From Date, To Date (both start on
 * today, dd/MM/yyyy), Factory Name (search box), Search.
 * Result table columns (cell index): 0 factory, 1 expense date, 2 expense name, 3 expense number, 4 designation,
 * 5 employee, 6 rate, 7 image, 8 remarks, 9 action (a tick box).
 * Only pending (unapproved) expenses are listed. Ticking a row or Approve All starts an approve / reject: the dialog
 * that follows (remarks and Approve / Reject buttons) is handled by the dialog methods. These change records.
 * Typing: an empty field is typed into directly, without clear().
 */
public class R_OtherExpenseApproval_Cls {

	WebDriver driver;
	WebDriverWait wait;

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

	@FindBy(xpath = "//*[contains(text(),'Showing')]")
	WebElement Entries_info;

	@FindBy(xpath = "//*[normalize-space(text())='Previous']")
	WebElement Previous_btn;

	@FindBy(xpath = "//*[normalize-space(text())='Next']")
	WebElement Next_btn;

	// ---------- Constructor ----------
	public R_OtherExpenseApproval_Cls(WebDriver driver) {
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

	private void js_click(WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", element);
		pause(700);
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
	// the value of every option of a drop down (what the page sends to the server)
	public List<String> option_values(WebElement dropdown) {
		List<String> values = new ArrayList<String>();
		for (WebElement o : new Select(dropdown).getOptions()) {
			values.add(o.getText().trim() + "=" + o.getAttribute("value"));
		}
		return values;
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

	public boolean next_enabled() {
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Next']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	public boolean previous_enabled() {
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Previous']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

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

	// the "Approve All" tick box, found by its text with JavaScript (also works for custom tick boxes)
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
		return label_select("Employee Type");
	}

	public WebElement verify_employee() {
		return label_input("Employee Name");
	}

	public WebElement verify_expense_type() {
		return label_select("Expense Type");
	}

	public WebElement verify_status() {
		return label_select("Status");
	}

	public WebElement verify_from() {
		return label_input("From Date");
	}

	public WebElement verify_to() {
		return label_input("To Date");
	}

	public WebElement verify_factory() {
		return label_input("Factory Name");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public boolean search_form_open() {
		return !displayed(By.xpath("//label[normalize-space()='Employee Type']")).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			click_safely(Top_search_btn);
			pause(800);
		}
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches.
	// An employee needs an employee type: when none is given "Sales Executive" is chosen if the drop down has it.
	public void Search_expenses(String type, String employee, String expense_type, String status, String factory,
			String from, String to) {
		Open_search_form();
		if (!employee.isEmpty() && type.isEmpty() && options_of(verify_type()).contains("Sales Executive")) {
			type = "Sales Executive";
		}
		if (!type.isEmpty()) {
			select_text(verify_type(), type);
			pause(800);
		}
		if (!employee.isEmpty()) {
			search_pick(verify_employee(), employee);
		}
		if (!expense_type.isEmpty()) {
			select_text(verify_expense_type(), expense_type);
		}
		if (!status.isEmpty()) {
			select_text(verify_status(), status);
		}
		if (!factory.isEmpty()) {
			search_pick(verify_factory(), factory);
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

	// ======================= result table =======================

	public List<String> headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : displayed(By.xpath("//table//th"))) {
			out.add(th.getText().trim().toUpperCase().replaceAll("\\s+", " "));
		}
		return out;
	}

	private List<WebElement> result_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() > 5
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

	// the cell texts of every result row (line breaks turned into spaces): [factory, date, expense name,
	// expense number, designation, employee, rate, image, remarks, action]
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

	public List<String> row_of(String number) {
		for (List<String> cells : result_texts()) {
			if (cells.size() > 9 && cells.get(3).equalsIgnoreCase(number)) {
				return cells;
			}
		}
		return null;
	}

	private WebElement row_by_number(String number) {
		for (WebElement row : result_rows()) {
			if (row.findElements(By.tagName("td")).get(3).getText().trim().equalsIgnoreCase(number)) {
				return row;
			}
		}
		throw new NoSuchElementException("Expense not found in the list: " + number);
	}

	// the tick box of a row (null when the row has none)
	private WebElement row_box(WebElement row) {
		List<WebElement> boxes = row.findElements(By.xpath("./td[last()]//input[@type='checkbox']"));
		return boxes.isEmpty() ? null : boxes.get(0);
	}

	public int row_box_count() {
		int n = 0;
		for (WebElement row : result_rows()) {
			if (row_box(row) != null) {
				n++;
			}
		}
		return n;
	}

	public int ticked_row_boxes() {
		int n = 0;
		for (WebElement row : result_rows()) {
			WebElement box = row_box(row);
			if (box != null && box.isSelected()) {
				n++;
			}
		}
		return n;
	}

	// pending = the row has a visible, enabled and unticked tick box
	public boolean row_pending(String number) {
		WebElement box = row_box(row_by_number(number));
		return box != null && box.isDisplayed() && box.isEnabled() && !box.isSelected();
	}

	// the number of rows that are pending
	public int pending_count() {
		int n = 0;
		for (WebElement row : result_rows()) {
			WebElement box = row_box(row);
			if (box != null && box.isDisplayed() && box.isEnabled() && !box.isSelected()) {
				n++;
			}
		}
		return n;
	}

	// ======================= approve / reject (they change records) =======================

	private String dialog_text() {
		List<WebElement> d = displayed(By.xpath("//div[contains(@class,'swal2-popup')]"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')]"));
		return d.isEmpty() ? "" : d.get(0).getText().trim().replaceAll("\\s+", " ");
	}

	// ticks the tick box of a row (an approve / reject dialog may follow)
	public void Tick_row(String number) {
		WebElement box = row_box(row_by_number(number));
		if (box == null) {
			throw new NoSuchElementException("The row has no tick box: " + number);
		}
		js_click(box);
		pause(1000);
	}

	// ticks Approve All (an approve / reject dialog may follow)
	public void Tick_approve_all() {
		js_click(approve_all_box());
		pause(1000);
	}

	// true when a dialog (browser alert, sweet alert or pop-up) is open
	public boolean dialog_open() {
		try {
			driver.switchTo().alert();
			return true;
		} catch (NoAlertPresentException e) {
			return !displayed(By.xpath("//div[contains(@class,'swal2-popup')]"
					+ " | //div[contains(@class,'modal') and contains(@class,'show')]")).isEmpty();
		}
	}

	// the text of the open dialog (the browser alert text when it is an alert)
	public String Dialog_text() {
		try {
			return driver.switchTo().alert().getText();
		} catch (NoAlertPresentException e) {
			return dialog_text();
		}
	}

	// the texts of the buttons of the open pop-up
	public List<String> dialog_buttons() {
		List<String> out = new ArrayList<String>();
		for (WebElement b : displayed(By.xpath("//div[contains(@class,'swal2-popup')]//button"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')]//button"))) {
			String t = b.getText().trim();
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	// true when the open pop-up has a remarks box
	public boolean dialog_has_remarks() {
		return !displayed(By.xpath("//div[contains(@class,'swal2-popup')]//textarea | //div[contains(@class,'swal2-popup')]//input[@type='text']"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')]//textarea"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')]//input[@type='text']")).isEmpty();
	}

	public void Set_dialog_remarks(String text) {
		WebElement box = first_displayed(By.xpath("//div[contains(@class,'swal2-popup')]//textarea"
				+ " | //div[contains(@class,'swal2-popup')]//input[@type='text']"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')]//textarea"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')]//input[@type='text']"));
		type(box, text);
	}

	// clicks the button of the open pop-up whose text contains 'text' (Approve, Reject, Cancel, Close ...);
	// for a browser alert: Approve / Reject / OK accept it, anything else dismisses it
	public void Click_dialog_button(String text) {
		try {
			Alert a = driver.switchTo().alert();
			if (text.equalsIgnoreCase("Cancel") || text.equalsIgnoreCase("Close") || text.equalsIgnoreCase("No")) {
				a.dismiss();
			} else {
				a.accept();
			}
			pause(1500);
			return;
		} catch (NoAlertPresentException e) {
			// a pop-up: look for the button
		}
		for (WebElement b : displayed(By.xpath("//div[contains(@class,'swal2-popup')]//button"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')]//button"))) {
			if (b.getText().trim().toLowerCase().contains(text.toLowerCase())) {
				click_safely(b);
				pause(2000);
				return;
			}
		}
		throw new NoSuchElementException("The dialog has no button: " + text);
	}

	// accepts every message that follows an action (browser alerts and sweet alerts); returns their texts
	public List<String> Accept_messages() {
		List<String> texts = new ArrayList<String>();
		for (int round = 0; round < 3; round++) {
			String text = "";
			try {
				Alert a = driver.switchTo().alert();
				text = a.getText();
				a.accept();
			} catch (NoAlertPresentException e) {
				List<WebElement> ok = displayed(By.xpath("//button[contains(@class,'swal2-confirm')]"));
				if (!ok.isEmpty()) {
					text = dialog_text();
					click_safely(ok.get(0));
				}
			}
			if (text.isEmpty()) {
				break;
			}
			texts.add(text);
			pause(800);
		}
		return texts;
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
					+ " or contains(@class,'notyf')]"));
			return found.isEmpty() ? "" : found.get(0).getText().trim();
		} catch (StaleElementReferenceException e) {
			return "";
		}
	}
}