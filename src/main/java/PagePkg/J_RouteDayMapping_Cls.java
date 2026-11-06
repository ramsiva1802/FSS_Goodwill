package PagePkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
 * Page class of Customer Details > Route Day Mapping.
 *
 * The page has two screens:
 *   1. LIST screen : Search / Create buttons, search form (hierarchy type, hierarchy name, employee, setting code,
 *                    Search, Clear), table filter, result table (employee, setting code, vehicle, no. of routes,
 *                    hierarchy name and a view, edit and DELETE icon per row), paging
 *   2. FORM screen : create / view (all greyed out, only Back) / edit (hierarchy, employee and vehicle are locked).
 *                    Company (fixed), hierarchy type, hierarchy name, employee, settings code, vehicle (+ Add Vehicle
 *                    pop-up), from date, Search Route filter and the route table: a tick box per route and the seven
 *                    day tick boxes (Mon .. Sun), ALL tick box in the header, Back, Submit.
 * Hierarchy name, employee and vehicle are "search" boxes: the text is typed and the matching suggestion is clicked.
 * The Add (vehicle) button and the Vehicle box need the hierarchy type and hierarchy name first.
 * A vehicle that is assigned to a mapping is not offered; an unassigned one is.
 * The Add Vehicle pop-up has its own Submit button: the Submit of the form is always the one outside the pop-up.
 * Ticking a route ticks its 7 days; ticking a day ticks its route.
 * Typing: an empty field is typed into directly, without clear().
 */
public class J_RouteDayMapping_Cls {

	WebDriver driver;
	WebDriverWait wait;

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

	@FindBy(xpath = "//*[normalize-space(text())='Previous']")
	WebElement Previous_btn;

	@FindBy(xpath = "//*[normalize-space(text())='Next']")
	WebElement Next_btn;

	// ---------- FORM screen buttons: always the VISIBLE one (the Add Vehicle pop-up has a hidden Submit) ----------
	static final By SUBMIT = By.xpath("//*[self::button or self::a][normalize-space()='Submit']"
			+ "[not(ancestor::div[contains(@class,'modal')])]");
	static final By BACK = By.xpath("//*[self::button or self::a][normalize-space()='Back']");
	static final By ADD_VEHICLE = By.xpath("//*[self::button or self::a][normalize-space()='Add']");

	// the suggestion lines of a search box
	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

	// ---------- Constructor ----------
	public J_RouteDayMapping_Cls(WebDriver driver) {
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

	// a visible drop down found by the text of its label
	private WebElement label_select(String label) {
		return first_displayed(By.xpath("//label[normalize-space()='" + label + "']/following::select[1]"));
	}

	// a visible text box found by the text of its label
	private WebElement label_input(String label) {
		return first_displayed(By.xpath("//label[normalize-space()='" + label
				+ "']/following::input[not(@type='hidden')][1]"));
	}

	private WebElement placeholder_input(String placeholder) {
		return first_displayed(By.xpath("//input[@placeholder='" + placeholder + "']"));
	}

	private void pause(int millis) {
		WaitUtil.settle(driver, millis);
	}

	// the visible suggestion lines of a search box
	private List<WebElement> suggestions() {
		return displayed(By.xpath(SUGGESTIONS));
	}

	// the texts of the visible suggestion lines
	private List<String> suggestion_texts() {
		List<String> out = new ArrayList<String>();
		for (WebElement s : suggestions()) {
			String t = s.getText().trim();
			if (!t.isEmpty() && !out.contains(t)) {
				out.add(t);
			}
		}
		return out;
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
				pause(1200);
				return;
			}
		}
		click_safely(found.get(0));
		pause(1200);
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

	// types into the "Search:" box above the table (it filters the rows of the list)
	public void Filter_table(String text) {
		type(verify_table_filter(), text);
		pause(1200);
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
		return placeholder_input("Search hierarchy...");
	}

	public WebElement verify_search_employee() {
		return placeholder_input("Search employee...");
	}

	public WebElement verify_search_setting() {
		return label_select("Setting Code");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public WebElement verify_clear() {
		return Clear_btn;
	}

	public boolean search_form_open() {
		return !displayed(By.xpath("//input[@placeholder='Search employee...']")).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			Click_top_search();
		}
	}

	public void Select_search_type(String type) {
		select_text(verify_search_type(), type);
		pause(800);
	}

	public void Select_search_hierarchy(String text) {
		search_pick(verify_search_hierarchy(), text);
		pause(1000);
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches.
	// The setting code only has values after a hierarchy type and a hierarchy name are chosen.
	public void Search_mapping(String type, String hierarchy, String employee, String setting) {
		Open_search_form();
		if (!type.isEmpty()) {
			Select_search_type(type);
		}
		if (!hierarchy.isEmpty()) {
			Select_search_hierarchy(hierarchy);
		}
		if (!employee.isEmpty()) {
			search_pick(verify_search_employee(), employee);
		}
		if (!setting.isEmpty()) {
			select_text(verify_search_setting(), setting);
		}
		click_safely(Search_go);
		pause(1500);
	}

	public void Click_clear() {
		click_safely(Clear_btn);
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

	// the cell texts of every result row: [employee, setting code, vehicle, no. of routes, hierarchy name, action]
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

	// the cells of the first row whose employee contains the text (null when there is none)
	public List<String> row_of(String employee) {
		for (List<String> cells : result_texts()) {
			if (cells.get(0).toLowerCase().contains(employee.toLowerCase())) {
				return cells;
			}
		}
		return null;
	}

	// waits until the table shows the employee (true) or the wait is over (false)
	public boolean Wait_for_row(String employee) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12), Duration.ofMillis(100)).ignoring(StaleElementReferenceException.class)
					.until(d -> row_of(employee) != null);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// true when the employee is no longer in the table (waits a little)
	public boolean Wait_for_no_row(String employee) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10), Duration.ofMillis(100)).ignoring(StaleElementReferenceException.class)
					.until(d -> row_of(employee) == null);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private boolean next_enabled() {
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Next']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	// the employee of every row of every page, in small letters (the list is on its last page afterwards)
	public List<String> Mapped_employees() {
		List<String> out = new ArrayList<String>();
		for (int page = 0; page < 10; page++) {
			for (List<String> cells : result_texts()) {
				out.add(cells.get(0).toLowerCase());
			}
			if (!next_enabled()) {
				break;
			}
			Click_next();
			pause(1500);
		}
		return out;
	}

	// the view, edit and delete icons of one row (links first, otherwise the icons themselves)
	private List<WebElement> action_icons(String employee) {
		for (WebElement row : result_rows()) {
			if (row.findElements(By.tagName("td")).get(0).getText().toLowerCase().contains(employee.toLowerCase())) {
				List<WebElement> icons = row.findElements(By.xpath(".//td[last()]//a | .//td[last()]//button"));
				if (icons.size() < 3) {
					icons = row.findElements(By.xpath(".//td[last()]//i"));
				}
				return icons;
			}
		}
		throw new NoSuchElementException("Mapping not found in the list: " + employee);
	}

	public int action_icon_count(String employee) {
		return action_icons(employee).size();
	}

	public void Click_view(String employee) {
		click_safely(action_icons(employee).get(0));
	}

	public void Click_edit(String employee) {
		click_safely(action_icons(employee).get(1));
	}

	public void Click_delete(String employee) {
		click_safely(action_icons(employee).get(2));
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

	public WebElement verify_type() {
		return label_select("Hierarchy Type");
	}

	public WebElement verify_hierarchy() {
		return label_input("Hierarchy Name");
	}

	public WebElement verify_employee() {
		return label_input("Employee");
	}

	public WebElement verify_settings() {
		return label_select("Settings Code");
	}

	public WebElement verify_vehicle() {
		return label_input("Vehicle");
	}
	
	// the HTML around the Vehicle box (for the test report when no suggestion is found)
	public String vehicle_box_html() {
		Object html = ((JavascriptExecutor) driver).executeScript("var p = arguments[0].closest('div');"
				+ " if (p && p.parentElement) { p = p.parentElement; }"
				+ " return p ? p.outerHTML.substring(0, 700) : '';", verify_vehicle());
		return String.valueOf(html);
	}

	// disabled / read-only flags, class, background colour and pointer-events of a field (for the test report)
	public String control_info(WebElement element) {
		Object info = ((JavascriptExecutor) driver).executeScript("var e = arguments[0];"
				+ " var s = window.getComputedStyle(e);"
				+ " return 'disabled=' + e.disabled + ', readonly=' + e.readOnly + ', class=' + e.className"
				+ " + ', background=' + s.backgroundColor + ', pointer-events=' + s.pointerEvents;", element);
		return String.valueOf(info);
	}

	// tries to choose another hierarchy type. Returns "locked: ..", "unchanged" or "changed from X to Y"
	public String Try_change_type() {
		Select select = new Select(verify_type());
		String before = select.getFirstSelectedOption().getText().trim();
		try {
			List<WebElement> options = select.getOptions();
			for (int i = 0; i < options.size(); i++) {
				String t = options.get(i).getText().trim();
				if (!t.equals(before) && !t.equals("HierarchyType")) {
					select.selectByIndex(i);
					break;
				}
			}
		} catch (Exception e) {
			return "locked: " + e.getClass().getSimpleName();
		}
		String after = new Select(verify_type()).getFirstSelectedOption().getText().trim();
		return before.equals(after) ? "unchanged" : "changed from " + before + " to " + after;
	}

	public WebElement verify_add_vehicle() {
		return first_displayed(ADD_VEHICLE);
	}

	public WebElement verify_from_date() {
		return first_displayed(By.xpath("//label[normalize-space()='From Date']/following::input[@type='date'][1]"));
	}

	public WebElement verify_route_filter() {
		return placeholder_input("Filter routes...");
	}

	public WebElement verify_submit() {
		return first_displayed(SUBMIT);
	}

	public WebElement verify_back() {
		return first_displayed(BACK);
	}

	public void Select_type(String type) {
		select_text(verify_type(), type);
		pause(800);
	}

	public void Select_hierarchy(String text) {
		search_pick(verify_hierarchy(), text);
		pause(1000);
	}

	public void Select_employee(String text) {
		search_pick(verify_employee(), text);
	}

	public void Select_vehicle(String text) {
		search_pick(verify_vehicle(), text);
	}

	public void Select_settings(String code) {
		select_text(verify_settings(), code);
	}

	// the suggestions the Employee box offers for the typed text
	public List<String> employee_suggestions(String text) {
		type(verify_employee(), text);
		pause(1500);
		return suggestion_texts();
	}

	// the suggestions the Vehicle box offers for the typed text (empty list = no vehicle is offered)
	public List<String> vehicle_suggestions(String text) {
		type(verify_vehicle(), text);
		pause(1500);
		return suggestion_texts();
	}

	// sets the date through JavaScript, format yyyy-MM-dd (the box shows it as mm/dd/yyyy)
	public void Set_from_date(String iso_date) {
		WebElement field = verify_from_date();
		((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];"
				+ " arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
				+ " arguments[0].dispatchEvent(new Event('change', {bubbles: true}));", field, iso_date);
	}

	// Picks a Sales Executive that is not in 'mapped_employees' (small letters, as read from the list).
	// Tries "Auto", "Sivakumar" and then single letters in the Employee box and clicks the first suggestion
	// that has no mapping yet. Returns the chosen suggestion text, or null when none was found.
	public String Select_free_employee(Set<String> mapped_employees) {
		for (String text : new String[] { "Auto", "Sivakumar", "a", "e", "i", "o", "s", "m", "r", "u", "n" }) {
			type(verify_employee(), text);
			pause(1500);
			for (WebElement s : suggestions()) {
				String t = s.getText().trim();
				if (t.isEmpty()) {
					continue;
				}
				boolean used = false;
				for (String m : mapped_employees) {
					if (m.contains(t.toLowerCase()) || t.toLowerCase().contains(m)) {
						used = true;
						break;
					}
				}
				if (!used) {
					click_safely(s);
					pause(1000);
					return t;
				}
			}
		}
		return null;
	}

	public void Click_submit() {
		click_safely(first_displayed(SUBMIT));
	}

	public void Click_back() {
		click_safely(first_displayed(BACK));
	}

	// ======================= Add Vehicle pop-up =======================

	// clicks Add and does not wait: before the hierarchy is chosen nothing opens
	public void Click_add_vehicle() {
		click_safely(first_displayed(ADD_VEHICLE));
		pause(1500);
	}

	// clicks Add and waits for the pop-up (the hierarchy type and name must be chosen first)
	public void Open_add_vehicle() {
		Click_add_vehicle();
		first_displayed(By.xpath("//*[normalize-space(text())='Add Vehicle']"));
	}

	public boolean add_vehicle_open() {
		return !displayed(By.xpath("//*[normalize-space(text())='Add Vehicle']")).isEmpty();
	}

	public WebElement verify_vehicle_code() {
		return first_displayed(By.xpath("//*[normalize-space(text())='Vehicle Code']"
				+ "/following::input[not(@type='hidden')][1]"));
	}

	public WebElement verify_vehicle_name() {
		return first_displayed(By.xpath("//*[normalize-space(text())='Vehicle Name']"
				+ "/following::input[not(@type='hidden')][1]"));
	}

	// the Submit of the pop-up: the first visible Submit after the "Add Vehicle" title
	private WebElement popup_submit() {
		By by = By.xpath("//*[normalize-space(text())='Add Vehicle']/following::*"
				+ "[self::button or self::a][normalize-space()='Submit'][1]");
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	public void Click_vehicle_submit() {
		click_safely(popup_submit());
		pause(1000);
	}

	public void Add_vehicle(String code, String name) {
		type(verify_vehicle_code(), code);
		type(verify_vehicle_name(), name);
		click_safely(popup_submit());
		pause(1500);
	}

	// closes the pop-up with its x (or the Escape key when the x cannot be found)
	public void Close_add_vehicle() {
		List<WebElement> x = displayed(By.xpath("//*[normalize-space(text())='Add Vehicle']/following::*"
				+ "[self::button or self::span or self::a][contains(@class,'close') or normalize-space()='×'"
				+ " or normalize-space()='x' or @aria-label='Close'][1]"));
		if (!x.isEmpty()) {
			click_safely(x.get(0));
		} else {
			driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
		}
		pause(800);
	}

	// ======================= FORM screen: route table =======================

	// the rows of the route table (the ones with a tick box in a cell; the header ALL box is in a th)
	private List<WebElement> route_rows() {
		return displayed(By.xpath("//tr[td//input[@type='checkbox']]"));
	}

	public int route_row_count() {
		return route_rows().size();
	}

	public String route_name(int index) {
		return route_rows().get(index).findElements(By.tagName("td")).get(1).getText().trim();
	}

	public List<String> route_names() {
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < route_rows().size(); i++) {
			names.add(route_name(i));
		}
		return names;
	}

	// the tick box of a route (the first tick box of its row)
	private WebElement route_box(int index) {
		return route_rows().get(index).findElements(By.xpath(".//input[@type='checkbox']")).get(0);
	}

	// the 7 day tick boxes of a route, Mon .. Sun
	private List<WebElement> day_boxes(int index) {
		List<WebElement> all = route_rows().get(index).findElements(By.xpath(".//input[@type='checkbox']"));
		return new ArrayList<WebElement>(all.subList(1, all.size()));
	}

	public int day_box_count(int index) {
		return day_boxes(index).size();
	}

	public boolean route_ticked(int index) {
		return route_box(index).isSelected();
	}

	public boolean route_box_locked(int index) {
		return is_locked(route_box(index));
	}

	// the states of the 7 day tick boxes of a route
	public List<Boolean> day_states(int index) {
		List<Boolean> states = new ArrayList<Boolean>();
		for (WebElement d : day_boxes(index)) {
			states.add(d.isSelected());
		}
		return states;
	}

	public int ticked_day_count(int index) {
		int n = 0;
		for (Boolean b : day_states(index)) {
			if (b) {
				n++;
			}
		}
		return n;
	}

	public void Tick_route(int index) {
		if (!route_box(index).isSelected()) {
			click_safely(route_box(index));
			pause(400);
		}
	}

	public void Untick_route(int index) {
		if (route_box(index).isSelected()) {
			click_safely(route_box(index));
			pause(400);
		}
	}

	public void Tick_first_routes(int count) {
		for (int i = 0; i < count; i++) {
			Tick_route(i);
		}
	}

	// day: 0 = Mon .. 6 = Sun
	public void Tick_day(int index, int day) {
		WebElement box = day_boxes(index).get(day);
		if (!box.isSelected()) {
			click_safely(box);
			pause(400);
		}
	}

	public void Untick_day(int index, int day) {
		WebElement box = day_boxes(index).get(day);
		if (box.isSelected()) {
			click_safely(box);
			pause(400);
		}
	}

	// the ALL tick box of the table header
	public WebElement verify_all_box() {
		return first_displayed(By.xpath("//th//input[@type='checkbox']"));
	}

	public void Tick_all() {
		if (!verify_all_box().isSelected()) {
			click_safely(verify_all_box());
			pause(600);
		}
	}

	public void Untick_all() {
		if (verify_all_box().isSelected()) {
			click_safely(verify_all_box());
			pause(600);
		}
	}

	public int ticked_route_count() {
		int n = 0;
		for (int i = 0; i < route_rows().size(); i++) {
			if (route_ticked(i)) {
				n++;
			}
		}
		return n;
	}

	// the "Search Route" filter
	public void Filter_routes(String text) {
		type(verify_route_filter(), text);
		pause(1000);
	}

	// ======================= messages =======================

	// the red "Please ..." messages (under the fields and in the toast)
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