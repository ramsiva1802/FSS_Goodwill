package PagePkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * NOTES - E_Employee_Cls (Page Object Model)
 * ----------------------------------------
 * Employee Info > Employee has two screens:
 *   1. LIST screen   : Search / Create buttons, search form, table filter, "Show N entries",
 *                      result table (8 columns, a view and edit icon and a Resign button per row), paging
 *   2. CREATE screen : the employee form with Back and Submit, a "Designations" Add New pop-up
 * Placeholders such as "Enter Employee Code" and "Search ..." exist on BOTH screens (and twice on one
 * screen), so fields are found with input(placeholder, n): the n-th VISIBLE input with that placeholder.
 * Drop downs are found by their label or by the text of their placeholder option.
 * Rules used when filling the form:
 *   - Employee Type and Designation are always "Sales Executive"
 *   - Belongs To uses hierarchy type PLANT and hierarchy GOODWILL POLYMERS FSS 2026-2027
 *   - Is Web User = Yes enables the Role drop down, No disables it
 * The Billing Code must be unique: every Fill_mandatory call generates a new one (2 random letters + number).
 */
public class E_Employee_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// fixed data (see the rules above)
	public static final String EMPLOYEE_TYPE = "Sales Executive";
	public static final String DESIGNATION = "Sales Executive";
	public static final String HIERARCHY_TYPE = "PLANT";
	public static final String HIERARCHY_NAME = "GOODWILL POLYMERS FSS 2026-2027";

	// the billing code used by the last Fill_mandatory call
	public String last_billing_code;

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

	// ---------- CREATE form ----------
	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Add New']")
	WebElement Add_new_btn;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Back']")
	WebElement Back_btn;

	// ---------- "Designations" Add New pop-up ----------
	@FindBy(xpath = "//input[@placeholder='Enter Designation']")
	WebElement Designation_input;

	// ---------- Constructor ----------
	public E_Employee_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
		PageFactory.initElements(driver, this);
	}

	// ======================= helpers =======================

	// the visible elements that match a locator
	private List<WebElement> displayed(By by) {
		List<WebElement> list = new ArrayList<WebElement>();
		for (WebElement e : driver.findElements(by)) {
			if (e.isDisplayed()) {
				list.add(e);
			}
		}
		return list;
	}

	// the n-th (1 = first) visible input with this placeholder
	private WebElement input(String placeholder, int n) {
		By by = By.xpath("//input[@placeholder='" + placeholder + "']");
		wait.until(d -> displayed(by).size() >= n);
		return displayed(by).get(n - 1);
	}

	private WebElement input(String placeholder) {
		return input(placeholder, 1);
	}

	// a visible drop down found by the text of its label
	private WebElement select_by_label(String label) {
		By by = By.xpath("//label[normalize-space()='" + label + "']/following::select[1]");
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	// a visible drop down found by the text of one of its options (its placeholder option)
	private WebElement select_by_option(String option) {
		By by = By.xpath("//select[option[normalize-space()='" + option + "']]");
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

	// clear, then type
	private void type(WebElement field, String value) {
		field.clear();
		field.sendKeys(value);
	}

	// types into the visible box with this placeholder. The form redraws itself while the country and state load,
	// so a box that is 'not interactable' for a moment is looked up again and tried for up to 5 seconds
	private void type_input(String placeholder, String value) {
		long end = System.currentTimeMillis() + 5000;
		while (true) {
			try {
				type(input(placeholder), value);
				return;
			} catch (ElementNotInteractableException | StaleElementReferenceException e) {
				if (System.currentTimeMillis() > end) {
					throw e;
				}
				WaitUtil.nap(150);
			}
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

	// Picks the option with this text; if there is none, picks the first real option
	private void select_text_or_first(WebElement dropdown, String text) {
		try {
			select_text(dropdown, text);
		} catch (NoSuchElementException e) {
			select_first(dropdown);
		}
	}

	// Picks the first real option (index 0 is the placeholder)
	private void select_first(WebElement dropdown) {
		wait.until(d -> new Select(dropdown).getOptions().size() > 1);
		new Select(dropdown).selectByIndex(1);
	}

	// the option texts of a drop down, without the placeholder (first) option and blank ones
	private List<String> options_of(WebElement dropdown) {
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

	// returns the given number of random capital letters, for example "QX"
	private String random_letters(int count) {
		Random random = new Random();
		StringBuilder letters = new StringBuilder();
		for (int i = 0; i < count; i++) {
			letters.append((char) ('A' + random.nextInt(26)));
		}
		return letters.toString();
	}

	// a billing code must be unique: 2 random letters + a number that changes every 0.1 second
	private String new_billing_code() {
		return random_letters(2) + (System.currentTimeMillis() / 100) % 100000000;
	}

	// ======================= LIST screen =======================

	public WebElement verify_create_btn() {
		return Create_btn;
	}

	public WebElement verify_top_search() {
		return Top_search_btn;
	}

	// the "Search:" box above the table: the VISIBLE search box of the table filter
	private WebElement table_filter() {
		By by = By.xpath("//div[contains(@class,'dataTables_filter')]//input | //input[@type='search']"
				+ " | //label[normalize-space()='Search:']/input");
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	public WebElement verify_table_filter() {
		return table_filter();
	}

	// the "Show N entries" drop down
	private WebElement entries_select() {
		By by = By.xpath("//div[contains(@class,'dataTables_length')]//select | //label[contains(normalize-space(),'Show')]//select");
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	public WebElement verify_entries_select() {
		return entries_select();
	}

	// the texts of the Show entries drop down, for example [10, 25, 50, 100]
	public List<String> entries_options() {
		List<String> texts = new ArrayList<String>();
		for (WebElement o : new Select(entries_select()).getOptions()) {
			texts.add(o.getText().trim());
		}
		return texts;
	}

	// chooses the biggest number in the Show entries drop down (All counts as the biggest) and returns its text
	public String Select_largest_entries() {
		String best = null;
		long best_value = -1;
		for (String text : entries_options()) {
			long value = text.matches("\\d+") ? Long.parseLong(text) : Long.MAX_VALUE;
			if (value > best_value) {
				best_value = value;
				best = text;
			}
		}
		Select_entries(best);
		return best;
	}

	public void Select_entries(String number) {
		new Select(entries_select()).selectByVisibleText(number);
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

	// a column header of the result table, found by its text (capital letters ignored)
	public WebElement verify_header(String name) {
		return driver.findElement(By.xpath("//th[contains(translate(normalize-space(),'abcdefghijklmnopqrstuvwxyz',"
				+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZ'),'" + name.toUpperCase() + "')]"));
	}

	public void Click_header(String name) {
		click_safely(verify_header(name));
	}

	public void Click_create() {
		wait.until(ExpectedConditions.visibilityOf(Create_btn));
		click_safely(Create_btn);
	}

	// The search form stays hidden until the top Search button is clicked
	public void open_search_form() {
		if (displayed(By.xpath("//input[@placeholder='Enter Employee Code']")).isEmpty()) {
			click_safely(Top_search_btn);
		}
		input("Enter Employee Code");
	}

	public WebElement verify_search_code() {
		return input("Enter Employee Code");
	}

	// the Employee Name search box of the search form (the first "Search ..." box)
	public WebElement verify_search_employee_name() {
		return input("Search ...", 1);
	}

	public WebElement verify_search_hierarchy_type() {
		return select_by_option("HierarchyType");
	}

	// the Hierarchy Name search box of the search form (the second "Search ..." box)
	public WebElement verify_search_hierarchy_name() {
		return input("Search ...", 2);
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	// Opens the search form, types the employee code and / or chooses the hierarchy type
	// (blank = leave empty) and clicks the form's Search button
	public void Search_employee(String code, String hierarchy_type) {
		open_search_form();
		type_input("Enter Employee Code", code);
		if (!hierarchy_type.isEmpty()) {
			select_text(select_by_option("HierarchyType"), hierarchy_type);
		}
		click_safely(Search_go);
	}

	// Types in the "Search:" box above the table (it filters the table)
	public void Filter_table(String text) {
		WebElement box = table_filter();
		box.clear();
		box.sendKeys(text);
	}

	// Number of real result rows ("No data available" / "No matching records" is not counted)
	public int result_count() {
		int count = 0;
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr"))) {
			String text = row.getText().toLowerCase();
			if (!text.isEmpty() && !text.contains("no data") && !text.contains("no matching")
					&& !text.contains("no records")) {
				count++;
			}
		}
		return count;
	}

	// The text of every row of the result table (to see what a search really returned)
	public List<String> result_texts() {
		List<String> texts = new ArrayList<String>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr"))) {
			texts.add(row.getText().replace("\n", " | "));
		}
		return texts;
	}

	// the employee code (second column) of the first row
	public String first_row_code() {
		return driver.findElement(By.xpath("//table//tbody/tr[1]/td[2]")).getText().trim();
	}

	// the number of Resign buttons shown in the table
	public int resign_button_count() {
		return displayed(By.xpath("//table//tbody/tr//*[self::button or self::a][normalize-space()='Resign']")).size();
	}

	// the view and edit icons of the row with this employee code (the Resign button has text, the icons do not)
	private List<WebElement> action_icons(String code) {
		List<WebElement> icons = driver.findElements(
				By.xpath("//tr[td[normalize-space()='" + code + "']]//a[not(normalize-space())]"));
		if (icons.size() < 2) {
			icons = driver.findElements(By.xpath("//tr[td[normalize-space()='" + code + "']]//i"));
		}
		return icons;
	}

	public void Click_view(String code) {
		click_safely(action_icons(code).get(0));
	}

	public void Click_edit(String code) {
		click_safely(action_icons(code).get(1));
	}

	// the value currently inside the Employee Code box of the form
	public String code_value() {
		return input("Enter Employee Code").getAttribute("value");
	}

	// true if the list screen is on screen (the Create button only exists on the list screen)
	public boolean is_list_page() {
		return !displayed(By.xpath("//*[self::button or self::a][normalize-space()='Create']")).isEmpty();
	}

	// ======================= CREATE form: verification getters =======================

	public WebElement verify_code() {
		return input("Enter Employee Code");
	}

	public WebElement verify_first_name() {
		return input("Enter First Name");
	}

	public WebElement verify_last_name() {
		return input("Enter Last Name");
	}

	// the Address box is a text area
	public WebElement verify_address() {
		By by = By.xpath("//textarea[@placeholder='Enter Address']");
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	public WebElement verify_gender() {
		return select_by_label("Gender");
	}

	public WebElement verify_email() {
		return input("Enter Email");
	}

	public WebElement verify_phone() {
		return input("Enter Phone Number");
	}

	public WebElement verify_pf() {
		return input("Enter PF Number");
	}

	public WebElement verify_country() {
		return select_by_label("Country");
	}

	public WebElement verify_state() {
		return select_by_label("State");
	}

	public WebElement verify_zip() {
		return input("Enter Zip Code");
	}

	public WebElement verify_dob() {
		return input("Enter Date of Birth");
	}

	public WebElement verify_blood_group() {
		return input("Enter Blood Group");
	}

	public WebElement verify_aadhaar() {
		return input("Enter Aadhaar Number");
	}

	public WebElement verify_marital_status() {
		return select_by_label("Marital Status");
	}

	public WebElement verify_billing() {
		return input("Enter Billing Code");
	}

	public WebElement verify_employee_type() {
		return select_by_label("Employee Type");
	}

	public WebElement verify_designation() {
		return select_by_label("Designation");
	}

	public WebElement verify_add_new() {
		return Add_new_btn;
	}

	// the Employee Profile search box (the first "Search ..." box of the create form)
	public WebElement verify_profile_search() {
		return input("Search ...", 1);
	}

	public WebElement verify_role() {
		return select_by_label("Role");
	}

	public WebElement verify_hierarchy_type() {
		return select_by_option("Hierarchy Type");
	}

	// the Hierarchy Name search box of the create form (the second "Search ..." box)
	public WebElement verify_hierarchy_name() {
		return input("Search ...", 2);
	}

	public WebElement verify_back() {
		return Back_btn;
	}

	// the form's Submit button (its id is SaveSubmit; the pop-up has its own Submit button)
	public WebElement verify_submit() {
		List<WebElement> by_id = displayed(By.id("SaveSubmit"));
		if (!by_id.isEmpty()) {
			return by_id.get(0);
		}
		return displayed(By.xpath("//button[normalize-space()='Submit']")).get(0);
	}

	// ======================= Is Web User, Is Active =======================

	// the two Is Web User radio buttons: 1 = Yes, 2 = No
	private WebElement web_user_radio(int n) {
		By by = By.xpath("//input[@type='radio']");
		wait.until(d -> displayed(by).size() >= 2);
		return displayed(by).get(n - 1);
	}

	public WebElement verify_web_user_yes() {
		return web_user_radio(1);
	}

	public WebElement verify_web_user_no() {
		return web_user_radio(2);
	}

	public void Select_web_user(boolean yes) {
		click_safely(web_user_radio(yes ? 1 : 2));
	}

	public boolean is_role_enabled() {
		return select_by_label("Role").isEnabled();
	}

	public List<String> role_options() {
		return options_of(select_by_label("Role"));
	}

	public void Select_first_role() {
		select_first(select_by_label("Role"));
	}

	// picks the role with this text; blank or not found = the first role
	public void Select_role(String text) {
		if (text.isEmpty()) {
			select_first(select_by_label("Role"));
		} else {
			select_text_or_first(select_by_label("Role"), text);
		}
	}

	public WebElement verify_active() {
		return displayed(By.xpath("//input[@type='checkbox']")).get(0);
	}

	public void Click_active() {
		click_safely(verify_active());
	}

	// ======================= CREATE form: filling =======================

	// Fills ONLY the mandatory fields. Employee Type and Designation are Sales Executive,
	// and Belongs To is PLANT / GOODWILL POLYMERS FSS 2026-2027. Is Web User stays No.
	public void Fill_mandatory(String code, String first_name, String last_name, String email, String address) {
		wait.until(d -> !displayed(By.xpath("//input[@placeholder='Enter First Name']")).isEmpty());

		type_input("Enter Employee Code", code);
		type_input("Enter First Name", first_name);
		type_input("Enter Last Name", last_name);
		type(verify_address(), address);
		select_text_or_first(select_by_label("Gender"), "Male");
		type_input("Enter Email", email);
		select_text_or_first(select_by_label("Country"), "India");
		select_text_or_first(select_by_label("State"), "Kerala");    // the state list loads after the country

		last_billing_code = new_billing_code();
		type_input("Enter Billing Code", last_billing_code);

		select_text(select_by_label("Employee Type"), EMPLOYEE_TYPE);
		select_text(select_by_label("Designation"), DESIGNATION);

		Select_hierarchy(HIERARCHY_TYPE, HIERARCHY_NAME);
	}

	// Fills the optional fields with sample data
	public void Fill_optional() {
		Fill_optional("9876543210", "PF12345", "682001", "O+", "123412341234");
	}

	// Fills the optional fields with the given values
	public void Fill_optional(String phone, String pf, String zip, String blood_group, String aadhaar) {
		type_input("Enter Phone Number", phone);
		type_input("Enter PF Number", pf);
		type_input("Enter Zip Code", zip);
		type_input("Enter Blood Group", blood_group);
		type_input("Enter Aadhaar Number", aadhaar);
		select_text_or_first(select_by_label("Marital Status"), "Single");
	}

	// Fills the whole form: mandatory + optional
	public void Fill_form(String code, String first_name, String last_name, String email, String address) {
		Fill_mandatory(code, first_name, last_name, email, address);
		Fill_optional();
	}

	// ======================= Belongs To (hierarchy type and name) =======================

	// every visible suggestion of the hierarchy name search (the list shown while typing)
	private List<WebElement> suggestion_items() {
		By by = By.xpath("//ul[contains(@class,'ui-autocomplete') or contains(@class,'autocomplete')"
				+ " or contains(@class,'suggest') or contains(@class,'dropdown-menu')]//li"
				+ " | //div[contains(@class,'autocomplete') or contains(@class,'suggest')]//*[self::div or self::li or self::a]");
		return displayed(by);
	}

	// types the first word of the text in the Hierarchy Name box and waits up to 5 seconds for suggestions
	private WebElement type_in_hierarchy_name(String text) {
		WebElement box = input("Search ...", 2);
		type(box, text.split(" ")[0]);
		try {
			new WebDriverWait(driver, Duration.ofSeconds(5), Duration.ofMillis(100)).until(d -> !suggestion_items().isEmpty());
		} catch (TimeoutException e) {
			// no suggestion list appeared
		}
		return box;
	}

	// Chooses the hierarchy type, then the hierarchy name from the suggestions.
	// TODO: if no suggestion is found, inspect the suggestion list on the site and adjust suggestion_items().
	public void Select_hierarchy(String hierarchy_type, String hierarchy_name) {
		select_text(select_by_option("Hierarchy Type"), hierarchy_type);
		WaitUtil.settle(driver, 800);

		WebElement box = type_in_hierarchy_name(hierarchy_name);
		for (WebElement suggestion : suggestion_items()) {
			if (suggestion.getText().toLowerCase().contains(hierarchy_name.toLowerCase())) {
				click_safely(suggestion);
				return;
			}
		}
		box.sendKeys(Keys.ARROW_DOWN, Keys.ENTER);   // fallback: take the first suggestion
	}

	// the suggestions shown for a hierarchy type and a typed text (used to verify the dependency)
	public List<String> hierarchy_suggestions(String hierarchy_type, String text) {
		select_text(select_by_option("Hierarchy Type"), hierarchy_type);
		WaitUtil.settle(driver, 800);
		type_in_hierarchy_name(text);
		List<String> names = new ArrayList<String>();
		for (WebElement suggestion : suggestion_items()) {
			names.add(suggestion.getText().trim());
		}
		return names;
	}

	// ======================= CREATE form: drop downs and actions =======================

	public List<String> gender_options() {
		return options_of(select_by_label("Gender"));
	}

	public List<String> marital_options() {
		return options_of(select_by_label("Marital Status"));
	}

	public List<String> employee_type_options() {
		return options_of(select_by_label("Employee Type"));
	}

	public List<String> designation_options() {
		return options_of(select_by_label("Designation"));
	}

	// the state options (after a country is chosen)
	public int state_option_count() {
		return options_of(select_by_label("State")).size();
	}

	public void Select_country_first() {
		select_first(select_by_label("Country"));
	}

	public void Click_submit() {
		click_safely(verify_submit());
	}

	public void Click_back() {
		wait.until(ExpectedConditions.visibilityOf(Back_btn));
		click_safely(Back_btn);
	}

	// true if the create form is still on screen (used after an invalid submit)
	public boolean is_create_form() {
		return !displayed(By.xpath("//input[@placeholder='Enter First Name']")).isEmpty();
	}

	// The red "Please ..." messages currently shown under the form fields
	public List<String> validation_messages() {
		List<String> messages = new ArrayList<String>();
		for (WebElement e : driver.findElements(By.xpath("//*[starts-with(normalize-space(text()),'Please')]"))) {
			if (e.isDisplayed() && !e.getText().trim().isEmpty()) {
				messages.add(e.getText().trim());
			}
		}
		return messages;
	}

	// Text of a success/error pop-up after Submit (empty string if none is found).
	// TODO: replace the classes with the ones your site uses once you inspect the message.
	public String get_message() {
		List<WebElement> msgs = driver.findElements(By.xpath(
				"//*[contains(@class,'swal2-popup') or contains(@class,'toast') or contains(@class,'alert')]"));
		for (WebElement m : msgs) {
			if (m.isDisplayed() && !m.getText().trim().isEmpty()) {
				return m.getText().trim();
			}
		}
		return "";
	}

	// ======================= "Designations" Add New pop-up =======================

	// Clicks Add New and waits up to 4 seconds for the pop-up; if it did not open, clicks again through JavaScript
	public void Click_add_new() {
		wait.until(ExpectedConditions.visibilityOf(Add_new_btn));
		click_safely(Add_new_btn);
		try {
			new WebDriverWait(driver, Duration.ofSeconds(4), Duration.ofMillis(100)).until(d -> is_designation_popup_open());
		} catch (TimeoutException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", Add_new_btn);
		}
	}

	public boolean is_designation_popup_open() {
		return !displayed(By.xpath("//input[@placeholder='Enter Designation']")).isEmpty();
	}

	public WebElement verify_popup_input() {
		wait.until(d -> is_designation_popup_open());
		return Designation_input;
	}

	// the Submit button inside the pop-up (the first Submit after the designation box)
	public WebElement verify_popup_submit() {
		return driver.findElement(By.xpath("//input[@placeholder='Enter Designation']/following::button[normalize-space()='Submit'][1]"));
	}

	public void Click_popup_submit() {
		click_safely(verify_popup_submit());
	}

	// the X (close) of the pop-up: the nearest "close" element before the designation box
	public WebElement verify_popup_close() {
		return driver.findElement(By.xpath("//input[@placeholder='Enter Designation']/preceding::*"
				+ "[contains(@class,'close') or normalize-space(text())='\u00D7' or normalize-space(text())='\u2715'"
				+ " or normalize-space(text())='\u2716'][1]"));
	}

	public void Close_popup() {
		click_safely(verify_popup_close());
	}
}