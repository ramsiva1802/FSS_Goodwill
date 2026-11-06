package PagePkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * Page class of Device > Device Settings.
 *
 * The page has two screens:
 *   1. LIST screen : Search / Create buttons, search form (hierarchy type, hierarchy name, device settings, Search,
 *                    Clear), table filter, result table (hierarchy name, settings code, header 1, header 2,
 *                    footer 1, a view, an edit and a DELETE icon per row), paging
 *   2. FORM screen : create / view (greyed out, only Back) / edit. Company, hierarchy type, hierarchy name, header 1-4,
 *                    footer 1-2, GST, FSSAI, cash discount, paper feed, round off, round off limit, van to van
 *                    transfer type, damage warehouse, setting code, discount limits, declaration, UPI, IFSC, bank,
 *                    account number, 9 radio groups (Enable / Disable; Merchandising has Mandatory / Alert / Disable),
 *                    QR code and logo upload, Back, Submit.
 * The form is filled through Fill(map) with these keys (an empty value leaves the field as it is):
 *   type, hierarchy, header1, header2, header3, header4, footer1, footer2, gst, fssai, cashdiscount, paperfeed,
 *   roundoff, roundofflimit, vanvan, damage, code (= setting code), lower, upper, declaration, upi, ifsc, bank, account
 * Typing: an empty field is typed into directly, without clear().
 */
public class N_DeviceSettings_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// the order in which Fill(map) fills the form (the hierarchy type before the hierarchy name)
	public static final String[] ORDER = { "type", "hierarchy", "header1", "header2", "header3", "header4", "footer1",
			"footer2", "gst", "fssai", "cashdiscount", "paperfeed", "roundoff", "roundofflimit", "vanvan", "damage",
			"code", "lower", "upper", "declaration", "upi", "ifsc", "bank", "account" };

	// the radio groups
	public static final String[] GROUPS = { "Payment Split", "Stock Verification", "Declaration", "Customer Adding",
			"E-Invoice", "Printer", "Is GPS Billing", "Customer Location", "Merchandising" };

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

	// top-left Search button (the second 'Search' is the one inside the search form)
	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[1]")
	WebElement Top_search_btn;

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[2]")
	WebElement Search_go;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Clear']")
	WebElement Clear_btn;

	@FindBy(xpath = "//*[contains(text(),'Showing')]")
	WebElement Entries_info;

	// ---------- FORM screen buttons: always the VISIBLE one ----------
	static final By SUBMIT = By.xpath("//*[self::button or self::a][normalize-space()='Submit']");
	static final By BACK = By.xpath("//*[self::button or self::a][normalize-space()='Back']");

	// ---------- Constructor ----------
	public N_DeviceSettings_Cls(WebDriver driver) {
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

	// a label that may end with a red * : "Header 1", "Header 1*" or "Header 1 *"
	private static String label_xpath(String text) {
		return "//label[normalize-space()='" + text + "' or normalize-space()='" + text + "*' or normalize-space()='"
				+ text + " *']";
	}

	// a visible text box found by its label
	private WebElement label_input(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::input[not(@type='hidden')][1]"));
	}

	// a visible drop down found by its label
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

	// ---- the search form of the list screen ----
	public WebElement verify_search_type() {
		return label_select("Hierarchy Type");
	}

	public WebElement verify_search_hierarchy() {
		return label_input("Hierarchy Name");
	}

	public WebElement verify_search_settings() {
		return label_input("Device Settings");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public WebElement verify_clear() {
		return Clear_btn;
	}

	public boolean search_form_open() {
		return !displayed(By.xpath(label_xpath("Device Settings"))).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			click_safely(Top_search_btn);
			pause(800);
		}
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches
	public void Search_settings(String type, String hierarchy, String settings) {
		Open_search_form();
		if (!type.isEmpty()) {
			select_text(verify_search_type(), type);
			pause(800);
		}
		if (!hierarchy.isEmpty()) {
			search_pick(verify_search_hierarchy(), hierarchy);
		}
		if (!settings.isEmpty()) {
			type(verify_search_settings(), settings);
		}
		click_safely(Search_go);
		pause(2000);
	}

	public void Click_clear() {
		Open_search_form();
		click_safely(first_displayed(By.xpath("//*[self::button or self::a][normalize-space()='Clear']")));
		pause(1500);
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

	// the cell texts of every result row: [hierarchy name, settings code, header 1, header 2, footer 1, action]
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

	// the cells of the row of one settings code (null when it is not in the table)
	public List<String> row_of(String code) {
		for (List<String> cells : result_texts()) {
			if (cells.size() > 1 && cells.get(1).equalsIgnoreCase(code)) {
				return cells;
			}
		}
		return null;
	}

	// waits until the table shows the settings code (true) or the wait is over (false)
	public boolean Wait_for_row(String code) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12), Duration.ofMillis(100)).ignoring(StaleElementReferenceException.class)
					.until(d -> row_of(code) != null);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// true when the code is no longer in the table (waits a little)
	public boolean Wait_for_no_row(String code) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10), Duration.ofMillis(100)).ignoring(StaleElementReferenceException.class)
					.until(d -> row_of(code) == null);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// the view, edit and delete icons of one row (links first, otherwise the icons themselves)
	private List<WebElement> action_icons(String code) {
		for (WebElement row : result_rows()) {
			if (row.findElements(By.tagName("td")).get(1).getText().trim().equalsIgnoreCase(code)) {
				List<WebElement> icons = row.findElements(By.xpath(".//td[last()]//a | .//td[last()]//button"));
				if (icons.size() < 3) {
					icons = row.findElements(By.xpath(".//td[last()]//i"));
				}
				return icons;
			}
		}
		throw new NoSuchElementException("Setting not found in the list: " + code);
	}

	public int action_icon_count(String code) {
		return action_icons(code).size();
	}

	public void Click_view(String code) {
		click_safely(action_icons(code).get(0));
	}

	public void Click_edit(String code) {
		click_safely(action_icons(code).get(1));
	}

	public void Click_delete(String code) {
		click_safely(action_icons(code).get(2));
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

	// ======================= FORM screen: fields =======================

	// a text box / text area of the form by its key (see the class notes)
	public WebElement Field(String key) {
		switch (key) {
		case "hierarchy":
			return label_input("Hierarchy Name");
		case "header1":
			return label_input("Header 1");
		case "header2":
			return label_input("Header 2");
		case "header3":
			return label_input("Header 3");
		case "header4":
			return label_input("Header 4");
		case "footer1":
			return label_input("Footer 1");
		case "footer2":
			return label_input("Footer 2");
		case "gst":
			return label_input("GST Number");
		case "fssai":
			return label_input("FSSAI Number");
		case "cashdiscount":
			return label_input("Cash Discount %");
		case "damage":
			return label_input("Damage Warehouse");
		case "code":
			return label_input("Setting Code");
		case "lower":
			return label_input("Discount Lower Limit");
		case "upper":
			return label_input("Discount Upper Limit");
		case "upi":
			return label_input("UPI ID");
		case "ifsc":
			return label_input("IFSC Code");
		case "bank":
			return label_input("Bank Name");
		case "account":
			return label_input("Account Number");
		case "declaration":
			return first_displayed(By.xpath(label_xpath("Declaration") + "/following::textarea[1]"));
		default:
			throw new NoSuchElementException("Unknown field key: " + key);
		}
	}

	// a drop down of the form by its key
	public WebElement Select_of(String key) {
		switch (key) {
		case "company":
			return label_select("Company");
		case "type":
			return label_select("Hierarchy Type");
		case "paperfeed":
			return label_select("Paper Feed");
		case "roundoff":
			return label_select("Round Off");
		case "roundofflimit":
			return label_select("Round Off Limit");
		case "vanvan":
			return label_select("Van to Van Transfer Type");
		default:
			throw new NoSuchElementException("Unknown drop down key: " + key);
		}
	}

	// fills the form; a missing or empty value leaves that field as it is
	public void Fill(Map<String, String> values) {
		for (String key : ORDER) {
			String v = values.get(key);
			if (v == null || v.isEmpty()) {
				continue;
			}
			switch (key) {
			case "type":
				select_text(Select_of("type"), v);
				pause(800);
				break;
			case "paperfeed":
			case "roundoff":
			case "roundofflimit":
			case "vanvan":
				select_text(Select_of(key), v);
				break;
			case "hierarchy":
			case "damage":
				search_pick(Field(key), v);
				break;
			default:
				type(Field(key), v);
			}
		}
	}

	// ======================= radio groups =======================

	// the radio buttons of a group ("Payment Split" ... "Merchandising"), in the order they are shown
	private List<WebElement> radios(String group) {
		int size = group.equals("Merchandising") ? 3 : 2;
		By[] options = { By.xpath("//fieldset[legend[normalize-space()='" + group + "']]//input[@type='radio']"),
				By.xpath("//legend[normalize-space()='" + group + "']/following::input[@type='radio'][position()<="
						+ size + "]"),
				By.xpath("//*[self::div or self::span or self::p or self::b or self::i][normalize-space(text())='"
						+ group + "']/following::input[@type='radio'][position()<=" + size + "]") };
		for (By by : options) {
			List<WebElement> found = driver.findElements(by);
			if (!found.isEmpty()) {
				return found;
			}
		}
		throw new NoSuchElementException("Radio group not found: " + group);
	}

	private String[] radio_names(String group) {
		return group.equals("Merchandising") ? new String[] { "Mandatory", "Alert", "Disable" }
				: new String[] { "Enable", "Disable" };
	}

	// the chosen option of a group ("" when none is chosen)
	public String radio_selected(String group) {
		List<WebElement> list = radios(group);
		String[] names = radio_names(group);
		for (int i = 0; i < list.size() && i < names.length; i++) {
			if (list.get(i).isSelected()) {
				return names[i];
			}
		}
		return "";
	}

	public int radio_count(String group) {
		return radios(group).size();
	}

	// chooses an option of a group
	public void Set_radio(String group, String option) {
		List<WebElement> list = radios(group);
		String[] names = radio_names(group);
		for (int i = 0; i < names.length; i++) {
			if (names[i].equalsIgnoreCase(option)) {
				((JavascriptExecutor) driver).executeScript(
						"arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", list.get(i));
				pause(300);
				return;
			}
		}
		throw new NoSuchElementException("Option not found in " + group + ": " + option);
	}

	// the QR code and logo file boxes
	public int file_input_count() {
		return driver.findElements(By.xpath("//input[@type='file']")).size();
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

	// ======================= messages =======================

	// the red messages under the fields: "... is Mandatory" and "Please ..."
	public List<String> validation_messages() {
		List<String> out = new ArrayList<String>();
		for (WebElement e : displayed(By.xpath("//*[contains(normalize-space(text()),'is Mandatory')"
				+ " or starts-with(normalize-space(text()),'Please')]"))) {
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