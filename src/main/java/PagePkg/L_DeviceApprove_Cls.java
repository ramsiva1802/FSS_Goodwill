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
 * Page class of Device > Device Approve (one LIST screen, no create form).
 *
 * Top: Search button (opens the search form) and the table filter "Search:".
 * Search form: Employee Type, Employee (search box), Device Modal, Approval Status, From Date, To Date, Search.
 * Result table columns (cell index): 0 employee, 1 MAC address, 2 registration date, 3 APK version,
 * 4 device model, 5 approve, 6 reject, 7 block.
 * Row states:
 *   pending  = an approve and a reject tick box, no button
 *   approved = a green tick in Approve and a red "Block" button
 *   blocked  = a green "Unblock" button
 *   other    = anything else (for example a rejected device)
 * Approve / Reject / Block / Unblock ask for a confirmation (browser alert or pop-up), handled by Confirm_action().
 * A row is found by its MAC address.
 */
public class L_DeviceApprove_Cls {

	WebDriver driver;
	WebDriverWait wait;
	// the employee type that must be chosen before an employee can be searched
	public static final String EMPLOYEE_TYPE = "Sales Executive";
	// ---------- screen ----------
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
	public L_DeviceApprove_Cls(WebDriver driver) {
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

	// the suggestion lines of a search box
	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

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

	// ======================= screen: top =======================

	public WebElement verify_top_search() {
		return Top_search_btn;
	}

	public WebElement verify_entries() {
		return Entries_info;
	}

	public WebElement verify_next() {
		return Next_btn;
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

	// types into the "Search:" box above the table (it filters the rows of the list)
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

	// ======================= search form =======================

	public WebElement verify_type() {
		return first_displayed(By.xpath("//label[normalize-space()='Employee Type']/following::select[1]"));
	}

	public WebElement verify_employee() {
		return first_displayed(By.xpath("//label[normalize-space()='Employee']/following::input[not(@type='hidden')][1]"));
	}

	public WebElement verify_model() {
		return first_displayed(By.xpath("//label[normalize-space()='Device Modal']/following::select[1]"));
	}

	public WebElement verify_status() {
		return first_displayed(By.xpath("//label[normalize-space()='Approval Status']/following::select[1]"));
	}

	public WebElement verify_from() {
		return first_displayed(By.xpath("//label[normalize-space()='From Date']/following::input[not(@type='hidden')][1]"));
	}

	public WebElement verify_to() {
		return first_displayed(By.xpath("//label[normalize-space()='To Date']/following::input[not(@type='hidden')][1]"));
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

	// type, read-only flag and value of the two date boxes (for the test report)
	public String date_boxes_info() {
		WebElement from = verify_from();
		WebElement to = verify_to();
		return "From: type=" + from.getAttribute("type") + ", readonly=" + from.getAttribute("readonly") + ", value='"
				+ from.getAttribute("value") + "' | To: type=" + to.getAttribute("type") + ", readonly="
				+ to.getAttribute("readonly") + ", value='" + to.getAttribute("value") + "'";
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches.
	// Dates are written like the table shows them: dd/MM/yyyy
	public void Search_devices(String employee_type, String employee, String model, String status, String from,
			String to) {
		if (!employee.isEmpty() && employee_type.isEmpty()) {
			employee_type = EMPLOYEE_TYPE;
		}
		Open_search_form();
		if (!employee_type.isEmpty()) {
			select_text(verify_type(), employee_type);
		}
		if (!employee.isEmpty()) {
			search_pick(verify_employee(), employee);
		}
		if (!model.isEmpty()) {
			select_text(verify_model(), model);
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
		pause(2500);
	}

	// ======================= result table =======================

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
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() > 1) {
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

	// the cell texts of every result row: [employee, MAC, registration date, APK version, device model,
	// approve, reject, block]
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

	// the cells of the row of one MAC address (null when it is not in the table)
	public List<String> row_of(String mac) {
		for (List<String> cells : result_texts()) {
			if (cells.size() > 1 && cells.get(1).equalsIgnoreCase(mac)) {
				return cells;
			}
		}
		return null;
	}

	// waits until the table shows the MAC address (true) or the wait is over (false)
	public boolean Wait_for_row(String mac) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12), Duration.ofMillis(100)).ignoring(StaleElementReferenceException.class)
					.until(d -> row_of(mac) != null);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private WebElement row_by_mac(String mac) {
		for (WebElement row : result_rows()) {
			if (row.findElements(By.tagName("td")).get(1).getText().trim().equalsIgnoreCase(mac)) {
				return row;
			}
		}
		throw new NoSuchElementException("Device not found in the list: " + mac);
	}

	// the VISIBLE tick boxes of a row (approve and reject); blocked rows keep hidden boxes in the page
	private List<WebElement> boxes_of(WebElement row) {
		List<WebElement> shown = new ArrayList<WebElement>();
		for (WebElement b : row.findElements(By.xpath("./td[6]//input[@type='checkbox'] | ./td[7]//input[@type='checkbox']"))) {
			if (b.isDisplayed()) {
				shown.add(b);
			}
		}
		return shown;
	}

	// the visible Block / Unblock button of a row (null when it has none)
	private WebElement block_button(WebElement row) {
		for (WebElement b : row.findElements(By.xpath("./td[8]//button | ./td[8]//a | ./td[8]//input[@type='button']"))) {
			if (b.isDisplayed()) {
				return b;
			}
		}
		return null;
	}

	// the number of tick boxes of a row
	public int box_count(String mac) {
		return boxes_of(row_by_mac(mac)).size();
	}

	// the text of the Block / Unblock button of a row ("" when it has none)
	public String button_text(String mac) {
		WebElement b = block_button(row_by_mac(mac));
		if (b == null) {
			return "";
		}
		String t = b.getText().trim();
		return t.isEmpty() ? String.valueOf(b.getAttribute("value")).trim() : t;
	}

	// pending / approved / blocked / other (the button decides first, then the visible tick boxes)
	public String row_state(String mac) {
		String button = button_text(mac);
		if (button.equalsIgnoreCase("Unblock")) {
			return "blocked";
		}
		if (button.equalsIgnoreCase("Block")) {
			return "approved";
		}
		if (!boxes_of(row_by_mac(mac)).isEmpty()) {
			return "pending";
		}
		return "other";
	}
	// Scans all pages of the current list and returns the MAC address of the first device in this state
	// whose employee does not contain 'exclude' (null when there is none). Leaves the list on its last page.
	public String Find_mac(String state, String exclude) {
		for (int page = 0; page < 10; page++) {
			for (List<String> cells : result_texts()) {
				if (cells.size() < 3) {
					continue;
				}
				String mac = cells.get(1);
				if (!exclude.isEmpty() && cells.get(0).toLowerCase().contains(exclude.toLowerCase())) {
					continue;
				}
				try {
					if (row_state(mac).equals(state)) {
						return mac;
					}
				} catch (NoSuchElementException e) {
					// the row changed while reading: skip it
				}
			}
			if (!next_enabled()) {
				break;
			}
			Click_next();
			pause(1500);
		}
		return null;
	}

	// ======================= row actions =======================

	private String dialog_text() {
		List<WebElement> d = displayed(By.xpath("//div[contains(@class,'swal2-popup')]"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')]//div[contains(@class,'modal-body')]"));
		return d.isEmpty() ? "" : d.get(0).getText().trim();
	}

	// Confirms what an action asks (browser alert or pop-up with Yes / OK / Approve / Reject / Block / Unblock /
	// Confirm) and also accepts the message that follows. Returns every message that was shown.
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
						+ " or normalize-space()='Approve' or normalize-space()='Reject' or normalize-space()='Block'"
						+ " or normalize-space()='Unblock']"));
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

	// ticks Approve of a pending device and confirms; returns the messages that were shown
	public List<String> Tick_approve(String mac) {
		click_safely(boxes_of(row_by_mac(mac)).get(0));
		List<String> messages = Confirm_action();
		pause(2000);
		return messages;
	}

	// ticks Reject of a pending device and confirms; returns the messages that were shown
	public List<String> Tick_reject(String mac) {
		click_safely(boxes_of(row_by_mac(mac)).get(1));
		List<String> messages = Confirm_action();
		pause(2000);
		return messages;
	}

	// clicks Block / Unblock of a device and confirms; returns the messages that were shown
	public List<String> Click_block_button(String mac) {
		WebElement b = block_button(row_by_mac(mac));
		if (b == null) {
			throw new NoSuchElementException("The device has no Block / Unblock button: " + mac);
		}
		click_safely(b);
		List<String> messages = Confirm_action();
		pause(2000);
		return messages;
	}

	// ======================= messages =======================

	// the red "Please ..." messages
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

	// a pop-up / toast / alert text (empty when there is none)
	public String notice() {
		List<WebElement> found = displayed(By.xpath("//div[contains(@class,'swal2-popup') or contains(@class,'toast')"
				+ " or contains(@class,'alert') or contains(@class,'notyf')]"));
		return found.isEmpty() ? "" : found.get(0).getText().trim();
	}
}