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
 * Page class of Customer Details > Route.
 *
 * The page has two screens:
 *   1. LIST screen : Search / Create buttons, search form (route code, route name, hierarchy name), table filter,
 *                    result table (company, route code, route name, hierarchy type, hierarchy name and a view,
 *                    edit and DELETE icon per row), paging
 *   2. FORM screen : create / view (greyed out, only Back) / edit. Company name (fixed), route code, route name,
 *                    hierarchy type (drop down), hierarchy name (text box that may offer suggestions), Back, Submit.
 * Typing: an empty field is typed into directly, without clear().
 * Delete: the bin icon opens a confirmation (browser alert or pop-up); Confirm_delete / Cancel_delete handle both.
 */
public class I_Route_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// data used when filling the form (existing values of the QA data)
	public static final String HIERARCHY_TYPE = "PLANT";
	public static final String HIERARCHY_NAME = "GOODWILL POLYMERS FSS 2026-2027";

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

	// ---------- FORM screen ----------
	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Back']")
	WebElement Back_btn;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Submit']")
	WebElement Submit_btn;

	// ---------- Constructor ----------
	public I_Route_Cls(WebDriver driver) {
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

	// a visible drop down found by the text of its label (the label may end with a red *)
	private WebElement label_select(String label) {
		return first_displayed(By.xpath("//label[contains(normalize-space(),'" + label + "')]/following::select[1]"));
	}

	// a visible text input found by its placeholder
	private WebElement input(String placeholder) {
		return first_displayed(By.xpath("//input[@placeholder='" + placeholder + "']"));
	}

	private void pause(int millis) {
		WaitUtil.settle(driver, millis);
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

	public WebElement verify_previous() {
		return Previous_btn;
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

	// the "Search:" box above the table
	public WebElement verify_table_filter() {
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_filter')]//input | //input[@type='search']"
				+ " | //label[normalize-space()='Search:']/input | //*[normalize-space()='Search:']/following::input[1]"));
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
			return Back_btn.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	public boolean submit_visible() {
		try {
			return Submit_btn.isDisplayed();
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

	// the search form of the list screen
	public WebElement verify_search_code() {
		return input("Enter Route Code");
	}

	public WebElement verify_search_name() {
		return input("Enter Route Name");
	}

	public WebElement verify_search_hierarchy() {
		return input("Enter Hierarchy Name");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	// opens the search form when it is hidden, fills it and searches
	public void Search_route(String code, String name, String hierarchy) {
		if (displayed(By.xpath("//input[@placeholder='Enter Route Code']")).isEmpty()) {
			Click_top_search();
		}
		type(verify_search_code(), code);
		type(verify_search_name(), name);
		type(verify_search_hierarchy(), hierarchy);
		click_safely(Search_go);
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

	// the cell texts of every result row: [company, route code, route name, hierarchy type, hierarchy name, action]
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

	public List<String> Route_codes() {
		List<String> codes = new ArrayList<String>();
		for (List<String> cells : result_texts()) {
			codes.add(cells.get(1));
		}
		return codes;
	}

	// the cells of the row of one route code (null when it is not in the table)
	public List<String> row_of(String code) {
		for (List<String> cells : result_texts()) {
			if (cells.get(1).equalsIgnoreCase(code)) {
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
		throw new NoSuchElementException("Route not found in the list: " + code);
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
	// "Deleted successfully" message that follows. Returns every message that was shown.
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

	// ======================= FORM screen =======================

	public WebElement verify_company() {
		return label_select("Company Name");
	}

	public WebElement verify_code() {
		return input("Route Code");
	}

	public WebElement verify_name() {
		return input("Route Name");
	}

	public WebElement verify_hierarchy_type() {
		return label_select("Hierarchy Type");
	}

	// the hierarchy name of the FORM (the last visible one: the search form has the same placeholder)
	public WebElement verify_hierarchy_name() {
		By by = By.xpath("//input[@placeholder='Enter Hierarchy Name']");
		wait.until(d -> !displayed(by).isEmpty());
		List<WebElement> list = displayed(by);
		return list.get(list.size() - 1);
	}

	public WebElement verify_submit() {
		return Submit_btn;
	}

	public WebElement verify_back() {
		return Back_btn;
	}

	// types the hierarchy name and picks the suggestion that matches it (if the box offers suggestions)
	public void Select_hierarchy_name(String text) {
		WebElement field = verify_hierarchy_name();
		type(field, text);
		pause(1500);
		List<WebElement> suggestions = displayed(By.xpath("//ul[contains(@class,'ui-autocomplete')]//li"
				+ " | //li[contains(@class,'ui-menu-item')]"
				+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='']"
				+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li"));
		if (suggestions.isEmpty()) {
			return;
		}
		for (WebElement s : suggestions) {
			if (s.getText().trim().toLowerCase().contains(text.trim().toLowerCase())) {
				click_safely(s);
				return;
			}
		}
		click_safely(suggestions.get(0));
	}

	// fills the form; an empty value leaves that field untouched
	public void Fill_route(String code, String name, String hierarchy_type, String hierarchy_name) {
		if (!code.isEmpty()) {
			type(verify_code(), code);
		}
		if (!name.isEmpty()) {
			type(verify_name(), name);
		}
		if (!hierarchy_type.isEmpty()) {
			select_text(verify_hierarchy_type(), hierarchy_type);
		}
		if (!hierarchy_name.isEmpty()) {
			Select_hierarchy_name(hierarchy_name);
		}
	}

	public void Click_submit() {
		click_safely(Submit_btn);
	}

	public void Click_back() {
		click_safely(Back_btn);
	}

	// ======================= messages =======================

	// the red "Please ..." messages shown under the fields
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