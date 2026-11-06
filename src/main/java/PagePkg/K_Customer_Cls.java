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
 * Page class of Customer Details > Customer.
 *
 * The page has two screens:
 *   1. LIST screen : Search / Create buttons, search form, table filter, "Show N entries", result table (customer
 *                    code, name, balance, credit, route, rate profile, GSTIN, a view and an edit icon per row,
 *                    NO delete), paging
 *   2. FORM screen : create / view (greyed out, only Back) / edit. Text fields, drop downs, the Route and
 *                    Customer Walk Order search boxes, the address block, 4 tick boxes, "Disable GPS Validation"
 *                    (enables From Date / To Date), photo upload, Back, Submit.
 * Customers cannot be deleted: they are deactivated by unticking Is Active in the edit form.
 * The form is filled through Fill(map) with these keys (an empty value leaves the field as it is):
 *   code, name, gstin, store, aadhaar, fssai, mobile, email, balance, credit, cheque, route, group, rate, dealer,
 *   class, latitude, longitude, address, city, zip, country, state, payment
 * Typing: an empty field is typed into directly, without clear().
 */
public class K_Customer_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// the order in which Fill(map) fills the form (country before state)
	public static final String[] ORDER = { "code", "name", "gstin", "store", "aadhaar", "fssai", "mobile", "email",
			"balance", "credit", "cheque", "route", "group", "rate", "dealer", "class", "latitude", "longitude",
			"address", "city", "zip", "country", "state", "payment" };

	static final String LOWER = "translate(@placeholder,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')";

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

	@FindBy(xpath = "//*[contains(text(),'Showing')]")
	WebElement Entries_info;

	@FindBy(xpath = "//*[normalize-space(text())='Previous']")
	WebElement Previous_btn;

	@FindBy(xpath = "//*[normalize-space(text())='Next']")
	WebElement Next_btn;

	// ---------- FORM screen buttons: always the VISIBLE one ----------
	static final By SUBMIT = By.xpath("//*[self::button or self::a][normalize-space()='Submit']");
	static final By BACK = By.xpath("//*[self::button or self::a][normalize-space()='Back']");

	// ---------- Constructor ----------
	public K_Customer_Cls(WebDriver driver) {
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

	// a visible text box found by the start of its label text (a label may end with a red *)
	private WebElement label_input(String label) {
		return first_displayed(By.xpath("//label[starts-with(normalize-space(),'" + label
				+ "')]/following::input[not(@type='hidden')][1]"));
	}

	// a visible drop down found by the start of its label text
	private WebElement label_select(String label) {
		return first_displayed(By.xpath("//label[starts-with(normalize-space(),'" + label + "')]/following::select[1]"));
	}

	// the visible suggestion lines of a search box
	private List<WebElement> suggestions() {
		return displayed(By.xpath(SUGGESTIONS));
	}

	// types into a search box and clicks the suggestion that contains the text (or the first one);
	// when the box shows no suggestion the typed text stays as it is
	private void search_pick(WebElement field, String text) {
		type(field, text);
		pause(1500);
		List<WebElement> found = suggestions();
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
		pause(2000);
	}

	// the "Show N entries" drop down
	public WebElement verify_entries_select() {
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_length')]//select"
				+ " | //label[contains(normalize-space(),'Show')]//select"));
	}

	// the options of the "Show N entries" drop down
	public List<String> entries_options() {
		List<String> out = new ArrayList<String>();
		for (WebElement o : new Select(verify_entries_select()).getOptions()) {
			out.add(o.getText().trim());
		}
		return out;
	}

	// chooses the option at this position and returns its text
	public String Select_entries_index(int index) {
		Select select = new Select(verify_entries_select());
		select.selectByIndex(index);
		pause(2500);
		return select.getFirstSelectedOption().getText().trim();
	}

	// chooses the last (largest) option
	public void Select_largest_entries() {
		Select select = new Select(verify_entries_select());
		select.selectByIndex(select.getOptions().size() - 1);
		pause(3000);
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
	private By search_input_by(String word) {
		return By.xpath("//input[contains(" + LOWER + ",'" + word + "')]");
	}

	public boolean search_form_open() {
		return !displayed(search_input_by("code")).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			Click_top_search();
			pause(800);
		}
	}

	// the placeholders of the text boxes of the search form (for the test report)
	public List<String> search_placeholders() {
		List<String> out = new ArrayList<String>();
		for (WebElement e : displayed(By.xpath("//input[@placeholder and not(@type='search')]"))) {
			out.add(e.getAttribute("placeholder"));
		}
		return out;
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	// opens the search form when it is hidden, fills code and / or name and searches
	public void Search_customer(String code, String name) {
		Open_search_form();
		if (!code.isEmpty()) {
			type(first_displayed(search_input_by("code")), code);
		}
		if (!name.isEmpty()) {
			type(first_displayed(search_input_by("name")), name);
		}
		click_safely(Search_go);
		pause(2000);
	}

	// the table header texts in capital letters
	public List<String> headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : displayed(By.xpath("//table//th"))) {
			out.add(th.getText().trim().toUpperCase());
		}
		return out;
	}

	// clicks a column header (sorts the table)
	public void Click_header(String text) {
		for (WebElement th : displayed(By.xpath("//table//th"))) {
			if (th.getText().trim().toUpperCase().contains(text.toUpperCase())) {
				click_safely(th);
				pause(2000);
				return;
			}
		}
		throw new NoSuchElementException("Column header not found: " + text);
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

	// the cell texts of every result row: [code, name, balance, credit, route, rate profile, gstin, action]
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

	// the cells of the row of one customer code (null when it is not in the table)
	public List<String> row_of(String code) {
		for (List<String> cells : result_texts()) {
			if (cells.get(0).equalsIgnoreCase(code)) {
				return cells;
			}
		}
		return null;
	}

	// waits until the table shows the code (true) or the wait is over (false)
	public boolean Wait_for_row(String code) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12), Duration.ofMillis(100)).ignoring(StaleElementReferenceException.class)
					.until(d -> row_of(code) != null);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// the view and edit icons of one row (links first, otherwise the icons themselves)
	private List<WebElement> action_icons(String code) {
		for (WebElement row : result_rows()) {
			if (row.findElements(By.tagName("td")).get(0).getText().trim().equalsIgnoreCase(code)) {
				List<WebElement> icons = row.findElements(By.xpath(".//td[last()]//a | .//td[last()]//button"));
				if (icons.size() < 2) {
					icons = row.findElements(By.xpath(".//td[last()]//i"));
				}
				return icons;
			}
		}
		throw new NoSuchElementException("Customer not found in the list: " + code);
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

	// ======================= FORM screen: fields =======================

	// a text box / text area of the form by its key (see the class notes)
	public WebElement Field(String key) {
		switch (key) {
		case "code":
			return label_input("Customer Code");
		case "name":
			return label_input("Customer Name");
		case "gstin":
			return label_input("GSTIN");
		case "store":
			return label_input("Store Name");
		case "aadhaar":
			return label_input("Aadhaar Number");
		case "fssai":
			return label_input("FSSAI Number");
		case "mobile":
			return label_input("Mobile Number");
		case "email":
			return label_input("Email");
		case "balance":
			return label_input("Customer Balance");
		case "credit":
			return label_input("Credit Limit");
		case "cheque":
			return label_input("Cheque Amount Due");
		case "route":
			return label_input("Route");
		case "walk":
			return label_input("Customer Walk Order");
		case "latitude":
			return label_input("Latitude");
		case "longitude":
			return label_input("Longitude");
		case "city":
			return label_input("City");
		case "zip":
			return label_input("Zip Code");
		case "address":
			return first_displayed(By.xpath("//label[starts-with(normalize-space(),'Address')]/following::textarea[1]"));
		default:
			throw new NoSuchElementException("Unknown field key: " + key);
		}
	}

	// a drop down of the form by its key
	public WebElement Select_of(String key) {
		switch (key) {
		case "group":
			return label_select("Customer Group");
		case "rate":
			return label_select("Rate Profile");
		case "dealer":
			return label_select("Dealer Category");
		case "class":
			return label_select("Customer Class");
		case "country":
			return label_select("Country");
		case "state":
			return label_select("State");
		case "payment":
			return label_select("Payment Mode");
		case "prospect":
			return label_select("Prospect");
		case "menucard":
			return label_select("Menu Card");
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
			case "route":
				search_pick(Field("route"), v);
				break;
			case "group":
			case "rate":
			case "dealer":
			case "class":
			case "payment":
				select_text(Select_of(key), v);
				break;
			case "country":
				select_text(Select_of("country"), v);
				pause(1200);
				break;
			case "state":
				select_text(Select_of("state"), v);
				break;
			default:
				type(Field(key), v);
			}
		}
	}

	// the tick box of a label text, found with JavaScript (also works for hidden / custom tick boxes)
	public WebElement checkbox(String key) {
		String text;
		switch (key) {
		case "tax":
			text = "Is Additional Tax Applicable";
			break;
		case "active":
			text = "Is Active";
			break;
		case "distributor":
			text = "Is Under Distributor";
			break;
		case "scheme":
			text = "Is Scheme Applicable";
			break;
		default:
			text = "Disable GPS Validation";
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
			((JavascriptExecutor) driver).executeScript(
					"arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", box);
			pause(500);
		}
	}

	// From Date / To Date of the GPS block
	public WebElement verify_gps_from() {
		return first_displayed(By.xpath("//label[normalize-space()='From Date']/following::input[1]"));
	}

	public WebElement verify_gps_to() {
		return first_displayed(By.xpath("//label[normalize-space()='To Date']/following::input[1]"));
	}

	public WebElement verify_photo_upload() {
		return first_displayed(By.xpath("//*[contains(normalize-space(text()),'Upload Customer Photo')]"));
	}

	public WebElement verify_prospect_link() {
		return first_displayed(By.xpath("//a[contains(normalize-space(),'View Prospect Details')]"));
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

	// the red "Please ..." messages under the fields
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