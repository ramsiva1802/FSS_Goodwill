package PagePkg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
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
 * NOTES - D_Hierarchy_Cls (Page Object Model)
 * -----------------------------------------
 * Company Info > Hierarchy Details has two screens:
 *   1. LIST screen   : Search / Create buttons, search form (type, code, name), table filter, result table
 *   2. CREATE screen : the hierarchy form with Back and Submit, an "Add New" pop-up for hierarchy types
 * Placeholders such as "Enter Hierarchy Code" exist on BOTH screens, and "Enter Phone Number" twice
 * on the create form, so form fields are found with input(placeholder, n): it returns the n-th
 * VISIBLE input with that placeholder. Drop downs are found by their label or by their first option text.
 * A fixed footer can cover the bottom buttons, so clicks go through click_safely().
 * The Billing Code must be unique, so every form fill generates a new one (2 random letters + number).
 * Two ways to fill the form:
 *   - Fill_mandatory / Fill_form : sample data, used by the functional tests
 *   - Enter_hierarchy            : real values from Excel, used by the data entry tests
 */
public class D_Hierarchy_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// the billing code used by the last form fill
	public String last_billing_code;

	// hierarchy type chosen by Fill_mandatory (a top level type, so no parent is needed)
	public static final String DEFAULT_TYPE = "SALES ORGANIZATION";

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

	@FindBy(xpath = "//input[@placeholder='Search ...']")
	WebElement Parent_name_search;

	@FindBy(xpath = "//input[@type='checkbox']")
	WebElement Counter_billing;

	@FindBy(xpath = "//input[@placeholder='Enter UserCode']")
	WebElement User_code;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Back']")
	WebElement Back_btn;

	// ---------- "Add New" hierarchy type pop-up ----------
	@FindBy(xpath = "//input[@placeholder='Enter Hierarchy Type Name']")
	WebElement Type_name_input;

	// ---------- Constructor ----------
	public D_Hierarchy_Cls(WebDriver driver) {
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

	// Date Of Commission opens a calendar pop-up, so the date is picked from the calendar.
	// Picks today (the highlighted day). If no calendar appears, the value is set through JavaScript.
	private void pick_date(WebElement field) {
		click_safely(field);
		By today = By.xpath("//table[contains(@class,'ui-datepicker-calendar')]//a[contains(@class,'ui-state-highlight')]");
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(today));
			driver.findElement(today).click();
		} catch (TimeoutException e) {
			((JavascriptExecutor) driver).executeScript(
					"arguments[0].value='10/09/2026'; arguments[0].dispatchEvent(new Event('change'));", field);
		}
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

	public WebElement verify_entries() {
		return Entries_info;
	}

	public WebElement verify_previous() {
		return Previous_btn;
	}

	public WebElement verify_next() {
		return Next_btn;
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

	// The search form (type, code, name) stays hidden until the top Search button is clicked
	public void open_search_form() {
		if (displayed(By.xpath("//input[@placeholder='Enter Hierarchy Type']")).isEmpty()) {
			click_safely(Top_search_btn);
		}
		input("Enter Hierarchy Type");
	}

	public WebElement verify_search_type() {
		return input("Enter Hierarchy Type");
	}

	public WebElement verify_search_code() {
		return input("Enter Hierarchy Code");
	}

	public WebElement verify_search_name() {
		return input("Enter Hierarchy Name");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	// Opens the search form, types the values (blank = leave empty) and clicks the form's Search button
	public void Search_hierarchy(String hierarchy_type, String code, String name) {
		open_search_form();
		type(input("Enter Hierarchy Type"), hierarchy_type);
		type(input("Enter Hierarchy Code"), code);
		type(input("Enter Hierarchy Name"), name);
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

	// the code (first column) of the first row
	public String first_row_code() {
		return driver.findElement(By.xpath("//table//tbody/tr[1]/td[1]")).getText().trim();
	}

	// the icons (view, edit) in the Action column of the row with this code
	private List<WebElement> action_icons(String code) {
		By links = By.xpath("//tr[td[normalize-space()='" + code + "']]/td[last()]//a");
		List<WebElement> icons = driver.findElements(links);
		if (icons.size() < 2) {
			icons = driver.findElements(By.xpath("//tr[td[normalize-space()='" + code + "']]/td[last()]//i"));
		}
		return icons;
	}

	public void Click_view(String code) {
		click_safely(action_icons(code).get(0));
	}

	public void Click_edit(String code) {
		click_safely(action_icons(code).get(1));
	}

	// the value currently inside the Hierarchy Code box of the form
	public String code_value() {
		return input("Enter Hierarchy Code").getAttribute("value");
	}

	// true if the list screen is on screen (the Create button only exists on the list screen)
	public boolean is_list_page() {
		return !displayed(By.xpath("//*[self::button or self::a][normalize-space()='Create']")).isEmpty();
	}

	// ======================= CREATE form: verification getters =======================

	public WebElement verify_code() {
		return input("Enter Hierarchy Code");
	}

	public WebElement verify_name() {
		return input("Enter Hierarchy Name");
	}

	public WebElement verify_address1() {
		return input("Enter Address 1");
	}

	public WebElement verify_address2() {
		return input("Enter Address 2");
	}

	public WebElement verify_gstin() {
		return input("Enter GSTIN Number");
	}

	public WebElement verify_email() {
		return input("Enter Email");
	}

	public WebElement verify_phone() {
		return input("Enter Phone Number", 1);
	}

	public WebElement verify_zip() {
		return input("Enter Zip Code");
	}

	public WebElement verify_fssai() {
		return input("Enter FSSAI Number");
	}

	public WebElement verify_country() {
		return select_by_label("Country");
	}

	public WebElement verify_state() {
		return select_by_label("State");
	}

	public WebElement verify_ceo() {
		return input("Enter CEO");
	}

	public WebElement verify_date() {
		return input("Enter Date Of Commission");
	}

	public WebElement verify_latitude() {
		return input("Enter Latitude");
	}

	public WebElement verify_longitude() {
		return input("Enter longitude");
	}

	public WebElement verify_billing() {
		return input("Enter Billing Code");
	}

	public WebElement verify_hierarchy_type() {
		return select_by_option("HierarchyType");
	}

	public WebElement verify_add_new() {
		return Add_new_btn;
	}

	public WebElement verify_upi() {
		return input("Enter Upi ID");
	}

	public WebElement verify_bank() {
		return input("Enter Bank Name");
	}

	public WebElement verify_account() {
		return input("Enter Account Number");
	}

	public WebElement verify_ifsc() {
		return input("Enter IFSCCode");
	}

	public WebElement verify_branch() {
		return input("Enter Branch Name");
	}

	public WebElement verify_branch_phone() {
		return input("Enter Phone Number", 2);
	}

	public WebElement verify_branch_zip() {
		return input("Enter Zip");
	}

	public WebElement verify_branch_address() {
		return input("Enter Address");
	}

	public WebElement verify_parent_type() {
		return select_by_label("Parent Hierarchy Type");
	}

	public WebElement verify_parent_name_search() {
		return Parent_name_search;
	}

	public WebElement verify_counter_billing() {
		return Counter_billing;
	}

	public WebElement verify_user_code() {
		return User_code;
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

	// ======================= CREATE form: filling =======================

	// Fills ONLY the mandatory fields with sample data and the default hierarchy type.
	// The six key values come from the test.
	public void Fill_mandatory(String code, String name, String gst, String email, String phone, String zip) {
		Fill_mandatory(code, name, gst, email, phone, zip, DEFAULT_TYPE);
	}

	public void Fill_mandatory(String code, String name, String gst, String email, String phone, String zip,
			String hierarchy_type) {
		wait.until(d -> !displayed(By.xpath("//input[@placeholder='Enter Billing Code']")).isEmpty());

		type(input("Enter Hierarchy Code"), code);
		type(input("Enter Hierarchy Name"), name);
		type(input("Enter Address 1"), "Auto Address 1");
		type(input("Enter GSTIN Number"), gst);
		type(input("Enter Email"), email);
		type(input("Enter Phone Number", 1), phone);
		type(input("Enter Zip Code"), zip);
		select_first(select_by_label("Country"));
		select_first(select_by_label("State"));     // the state list loads after the country is chosen
		type(input("Enter CEO"), "Auto CEO");
		pick_date(input("Enter Date Of Commission"));

		last_billing_code = new_billing_code();
		type(input("Enter Billing Code"), last_billing_code);

		select_text(select_by_option("HierarchyType"), hierarchy_type);
	}

	// Fills the optional fields
	public void Fill_optional() {
		type(input("Enter Address 2"), "Auto Address 2");
		type(input("Enter FSSAI Number"), "12345678901234");
		type(input("Enter Latitude"), "9.9312");
		type(input("Enter longitude"), "76.2673");
		type(input("Enter Upi ID"), "autotest@upi");
		type(input("Enter Bank Name"), "Auto Test Bank");
		type(input("Enter Account Number"), "123456789012");
		type(input("Enter IFSCCode"), "SBIN0001234");
		type(input("Enter Branch Name"), "Auto Branch");
		type(input("Enter Phone Number", 2), "9876543211");
		type(input("Enter Zip"), "682001");
		type(input("Enter Address"), "Auto Branch Address");
	}

	// Fills the whole form: mandatory + optional
	public void Fill_form(String code, String name, String gst, String email, String phone, String zip) {
		Fill_mandatory(code, name, gst, email, phone, zip);
		Fill_optional();
	}

	// DATA ENTRY: fills every mandatory field with the given values and chooses the given type, country and state.
	// The billing code is generated (it must be unique).
	public void Enter_hierarchy(String hierarchy_type, String code, String name, String address1, String gstin,
			String email, String phone, String zip, String ceo, String country, String state) {
		Enter_hierarchy(hierarchy_type, code, name, address1, gstin, email, phone, zip, ceo, country, state, "");
	}

	// DATA ENTRY with the billing code taken from the data file (a blank value means: generate a new one)
	public void Enter_hierarchy(String hierarchy_type, String code, String name, String address1, String gstin,
			String email, String phone, String zip, String ceo, String country, String state, String billing_code) {
		wait.until(d -> !displayed(By.xpath("//input[@placeholder='Enter Billing Code']")).isEmpty());

		type(input("Enter Hierarchy Code"), code);
		type(input("Enter Hierarchy Name"), name);
		type(input("Enter Address 1"), address1);
		type(input("Enter GSTIN Number"), gstin);
		type(input("Enter Email"), email);
		type(input("Enter Phone Number", 1), phone);
		type(input("Enter Zip Code"), zip);
		select_text(select_by_label("Country"), country);
		select_text(select_by_label("State"), state);     // the state list loads after the country is chosen
		type(input("Enter CEO"), ceo);
		pick_date(input("Enter Date Of Commission"));

		last_billing_code = billing_code.trim().isEmpty() ? new_billing_code() : billing_code.trim();
		type(input("Enter Billing Code"), last_billing_code);

		select_text(select_by_option("HierarchyType"), hierarchy_type);
	}

	// ======================= CREATE form: drop downs and actions =======================

	public void Select_hierarchy_type(String text) {
		select_text(select_by_option("HierarchyType"), text);
	}

	// the hierarchy types offered in the Hierarchy Type drop down
	public List<String> hierarchy_type_options() {
		return options_of(select_by_option("HierarchyType"));
	}

	// the parent hierarchy types offered for the chosen hierarchy type (waits up to 3 seconds for them to load)
	public List<String> parent_options() {
		WebElement parent = select_by_label("Parent Hierarchy Type");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(3), Duration.ofMillis(100)).until(d -> new Select(parent).getOptions().size() > 1);
		} catch (TimeoutException e) {
			// no parent types for this hierarchy type
		}
		return options_of(parent);
	}

	// the state options (after a country is chosen)
	public int state_option_count() {
		return options_of(select_by_label("State")).size();
	}

	public void Select_country_first() {
		select_first(select_by_label("Country"));
	}

	// the Distributer Type drop down only appears when the hierarchy type is DISTRIBUTOR
	public boolean is_distributer_visible() {
		return !displayed(By.xpath("//select[option[normalize-space()='DistributorType']]")).isEmpty();
	}

	public List<String> distributer_options() {
		return options_of(select_by_option("DistributorType"));
	}

	public void Click_submit() {
		click_safely(verify_submit());
	}

	public void Click_back() {
		wait.until(ExpectedConditions.visibilityOf(Back_btn));
		click_safely(Back_btn);
	}

	public void Tick_counter_billing() {
		click_safely(Counter_billing);
	}

	// true if the create form is still on screen (used after an invalid submit)
	public boolean is_create_form() {
		return !displayed(By.xpath("//input[@placeholder='Enter Billing Code']")).isEmpty();
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

	// ======================= "Add New" hierarchy type pop-up =======================

	// Clicks Add New and waits up to 4 seconds for the pop-up; if it did not open, clicks again through JavaScript
	public void Click_add_new() {
		wait.until(ExpectedConditions.visibilityOf(Add_new_btn));
		click_safely(Add_new_btn);
		try {
			new WebDriverWait(driver, Duration.ofSeconds(4), Duration.ofMillis(100)).until(d -> is_type_popup_open());
		} catch (TimeoutException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", Add_new_btn);
		}
	}

	public boolean is_type_popup_open() {
		return !displayed(By.xpath("//input[@placeholder='Enter Hierarchy Type Name']")).isEmpty();
	}

	public WebElement verify_type_name_input() {
		wait.until(d -> is_type_popup_open());
		return Type_name_input;
	}

	// the Submit button inside the pop-up (it comes after the "Hierarchy Type Name" label)
	public WebElement verify_popup_submit() {
		return driver.findElement(By.xpath(
				"//*[normalize-space(text())='Hierarchy Type Name']/following::button[normalize-space()='Submit'][1]"));
	}

	public void Click_popup_submit() {
		click_safely(verify_popup_submit());
	}

	// the X (close) of the pop-up: the nearest "close" element before the "Hierarchy Type Name" label
	public WebElement verify_popup_close() {
		return driver.findElement(By.xpath("//*[normalize-space(text())='Hierarchy Type Name']/preceding::*"
				+ "[contains(@class,'close') or normalize-space(text())='\u00D7' or normalize-space(text())='\u2715'"
				+ " or normalize-space(text())='\u2716'][1]"));
	}

	public void Close_popup() {
		click_safely(verify_popup_close());
	}
}