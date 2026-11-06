package PagePkg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * NOTES - C_MainCompany_Cls (Page Object Model)
 * -------------------------------------------
 * Company Info > Main Company has two screens:
 *   1. LIST screen   : Search / Create buttons, search form, result table
 *   2. CREATE screen : the company form with Back and Submit
 * Both are kept in this one class because they belong to one module.
 * The list screen inputs have placeholders like "Enter Company Code",
 * the create form uses "Company Code", so the locators below do not clash.
 * The create form has 15 mandatory fields; FSSAI, Latitude, Longitude and Address2 are optional.
 * The search boxes on the list screen stay hidden until the top Search button is clicked.
 * A fixed footer covers the bottom buttons, so clicks go through click_safely().
 * Date of Commission opens a calendar pop-up, so the date is picked from the calendar.
 * The Billing Code must be unique, so every Fill_mandatory call generates a new one
 * (2 random letters + a number) and keeps it in last_billing_code.
 */
public class C_MainCompany_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// the billing code used by the last Fill_mandatory call (the DB check compares against it)
	public String last_billing_code;

	// ---------- LIST screen ----------
	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Create']")
	WebElement Create_btn;

	// the top-left Search button (the second 'Search' is the one inside the search form)
	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[1]")
	WebElement Top_search_btn;

	@FindBy(xpath = "//input[@placeholder='Enter Company Code']")
	WebElement Search_code;

	@FindBy(xpath = "//input[@placeholder='Enter Company Name']")
	WebElement Search_name;

	// TODO: if this clicks the wrong button, inspect the Search button inside the form
	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[2]")
	WebElement Search_go;

	@FindBy(xpath = "//table//tbody/tr")
	List<WebElement> Table_rows;

	@FindBy(xpath = "//*[contains(text(),'Showing')]")
	WebElement Entries_info;

	// ---------- CREATE form ----------
	@FindBy(xpath = "//input[@placeholder='Company Code']")
	WebElement Company_code;

	@FindBy(xpath = "//input[@placeholder='Company Name']")
	WebElement Company_name;

	@FindBy(xpath = "//input[@placeholder='Enter GSTIN Number']")
	WebElement Gstin;

	@FindBy(xpath = "//input[@placeholder='Enter Email']")
	WebElement Email;

	@FindBy(xpath = "//input[@placeholder='Enter Phone Number']")
	WebElement Phone;

	@FindBy(xpath = "//input[@placeholder='Enter Zip Code']")
	WebElement Zip;

	@FindBy(xpath = "//input[@placeholder='Enter FSSAI Number']")
	WebElement Fssai;

	@FindBy(xpath = "//input[@placeholder='Enter CEO']")
	WebElement Ceo;

	@FindBy(xpath = "//input[@placeholder='Enter Date of Commission']")
	WebElement Commission_date;

	@FindBy(xpath = "//input[@placeholder='Enter Latitude']")
	WebElement Latitude;

	@FindBy(xpath = "//input[@placeholder='Enter Longitude']")
	WebElement Longitude;

	@FindBy(xpath = "//input[@placeholder='Enter UPIID']")
	WebElement Upiid;

	@FindBy(xpath = "//input[@placeholder='Enter Bank Name']")
	WebElement Bank_name;

	@FindBy(xpath = "//input[@placeholder='Enter Billing Code']")
	WebElement Billing_code;

	@FindBy(xpath = "//input[@placeholder='Enter Address1']")
	WebElement Address1;

	@FindBy(xpath = "//input[@placeholder='Enter Address2']")
	WebElement Address2;

	// drop downs are found through their label text
	@FindBy(xpath = "//label[normalize-space()='Country']/following::select[1]")
	WebElement Country;

	@FindBy(xpath = "//label[normalize-space()='State']/following::select[1]")
	WebElement State;

	@FindBy(xpath = "//label[normalize-space()='Currency']/following::select[1]")
	WebElement Currency;

	@FindBy(xpath = "//*[contains(text(),'Upload')]")
	WebElement Upload_logo;

	// Back button: it calls CancelCreatePage() when clicked
	@FindBy(xpath = "//button[@onclick='CancelCreatePage()']")
	WebElement Back_btn;

	// Submit button: its id is SaveSubmit
	@FindBy(id = "SaveSubmit")
	WebElement Submit_btn;

	// ---------- Constructor ----------
	public C_MainCompany_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
		PageFactory.initElements(driver, this);
	}

	// ---------- Getter methods for verification ----------
	public WebElement verify_create_btn() {
		return Create_btn;
	}

	public WebElement verify_top_search() {
		return Top_search_btn;
	}

	public WebElement verify_search_code() {
		return Search_code;
	}

	public WebElement verify_search_name() {
		return Search_name;
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public WebElement verify_entries() {
		return Entries_info;
	}

	public WebElement verify_company_code() {
		return Company_code;
	}

	public WebElement verify_company_name() {
		return Company_name;
	}

	public WebElement verify_gstin() {
		return Gstin;
	}

	public WebElement verify_email() {
		return Email;
	}

	public WebElement verify_phone() {
		return Phone;
	}

	public WebElement verify_zip() {
		return Zip;
	}

	public WebElement verify_country() {
		return Country;
	}

	public WebElement verify_state() {
		return State;
	}

	public WebElement verify_currency() {
		return Currency;
	}

	public WebElement verify_logo_upload() {
		return Upload_logo;
	}

	public WebElement verify_back() {
		return Back_btn;
	}

	public WebElement verify_submit() {
		return Submit_btn;
	}

	// ---------- Safe click ----------
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

	// ---------- LIST screen actions ----------
	public void Click_create() {
		wait.until(ExpectedConditions.visibilityOf(Create_btn));
		click_safely(Create_btn);
	}

	// The search boxes are hidden until the top Search button is clicked.
	// Opens the search form if it is not already open.
	public void open_search_form() {
		if (driver.findElements(By.xpath("//input[@placeholder='Enter Company Code']")).stream()
				.noneMatch(WebElement::isDisplayed)) {
			click_safely(Top_search_btn);
		}
		wait.until(ExpectedConditions.visibilityOf(Search_code));
	}

	// Opens the search form, types the values (blank = leave empty) and clicks Search
	public void Search_company(String code, String name) {
		open_search_form();
		Search_code.clear();
		Search_code.sendKeys(code);
		Search_name.clear();
		Search_name.sendKeys(name);
		click_safely(Search_go);
	}

	// Number of real result rows (the grey "No data available" row is not counted)
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

	// The text of every row in the result table (used to see what a search really returned)
	public List<String> result_texts() {
		List<String> texts = new ArrayList<String>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr"))) {
			texts.add(row.getText().replace("\n", " | "));
		}
		return texts;
	}

	// ---------- CREATE form actions ----------

	// Fills ONLY the 15 mandatory fields.
	// The six key values come from the test, the rest are fixed sample data.
	// The billing code is generated here because it must be unique every time.
	public void Fill_mandatory(String code, String name, String gst, String email, String phone, String zip) {
		wait.until(ExpectedConditions.visibilityOf(Company_code));

		type(Company_code, code);
		type(Company_name, name);
		type(Gstin, gst);
		type(Email, email);
		type(Phone, phone);
		type(Zip, zip);
		type(Ceo, "Auto CEO");
		pick_date(Commission_date);   // picked from the calendar pop-up
		type(Upiid, "autotest@upi");
		type(Bank_name, "Auto Test Bank");

		// billing code must be unique: 2 random letters + a number that changes every 0.1 second
		last_billing_code = random_letters(2) + (System.currentTimeMillis() / 100) % 100000000;
		type(Billing_code, last_billing_code);

		type(Address1, "Auto Address Line 1");

		select_first(Country);
		select_first(State);      // the state list usually loads after the country is chosen
		select_first(Currency);
	}

	// Fills the 4 optional fields
	public void Fill_optional() {
		type(Fssai, "12345678901234");
		type(Latitude, "9.9312");
		type(Longitude, "76.2673");
		type(Address2, "Auto Address Line 2");
	}

	// Fills the whole form: mandatory + optional
	public void Fill_form(String code, String name, String gst, String email, String phone, String zip) {
		Fill_mandatory(code, name, gst, email, phone, zip);
		Fill_optional();
	}

	// clear, then type
	private void type(WebElement field, String value) {
		field.clear();
		field.sendKeys(value);
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

	// Date of Commission opens a calendar pop-up, so the date is picked from the calendar.
	// Picks today (the highlighted day). If no calendar appears, the value is set through JavaScript.
	private void pick_date(WebElement field) {
		click_safely(field);   // opens the calendar
		By today = By.xpath("//table[contains(@class,'ui-datepicker-calendar')]//a[contains(@class,'ui-state-highlight')]");
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(today));
			driver.findElement(today).click();   // the calendar closes after a day is picked
		} catch (TimeoutException e) {
			((JavascriptExecutor) driver).executeScript(
					"arguments[0].value='10/09/2026'; arguments[0].dispatchEvent(new Event('change'));", field);
		}
	}

	// Picks the first real option (index 0 is the "Country"/"State" placeholder).
	// Waits because dependent drop downs fill up after the previous one is chosen.
	private void select_first(WebElement dropdown) {
		wait.until(d -> new Select(dropdown).getOptions().size() > 1);
		new Select(dropdown).selectByIndex(1);
	}

	public void Click_submit() {
		wait.until(ExpectedConditions.visibilityOf(Submit_btn));
		click_safely(Submit_btn);
	}

	public void Click_back() {
		wait.until(ExpectedConditions.visibilityOf(Back_btn));
		click_safely(Back_btn);
	}

	// true if the create form is still on screen (used after an invalid submit)
	public boolean is_create_form() {
		return driver.findElements(By.xpath("//input[@placeholder='Company Code']")).stream()
				.anyMatch(WebElement::isDisplayed);
	}

	// true if the list screen is on screen (the Create button only exists on the list screen)
	public boolean is_list_page() {
		return driver.findElements(By.xpath("//*[self::button or self::a][normalize-space()='Create']")).stream()
				.anyMatch(WebElement::isDisplayed);
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
}