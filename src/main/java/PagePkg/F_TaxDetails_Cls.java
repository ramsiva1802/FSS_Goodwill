package PagePkg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
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
 * NOTES - F_TaxDetails_Cls (Page Object Model)
 * ------------------------------------------
 * Stock Item > Tax Details has two screens:
 *   1. LIST screen : Search / Create buttons, search form (Tax Profile Name), table filter, result table
 *                    (a view, edit and delete icon per row), paging
 *   2. FORM screen : Tax Profile Name, exactly 3 tax rows (Code, Name, Value, InState / OutState),
 *                    an Additional Tax block (name, value, Value In, How to Apply, Applicable for)
 *                    and Back / Submit. The same screen is used to create, view (greyed out) and edit.
 * EVERY input on the form has the placeholder "Enter ...", so inputs are found by POSITION:
 * form_inputs() lists the visible text inputs that come after the "Tax Profile Name" label, in page order:
 *   0 = profile name, then per tax row 1..3: code, name, value (1-3, 4-6, 7-9),
 *   10 = additional tax name, 11 = additional tax value.
 * Radio buttons in page order: 1 = row 1 InState, 2 = row 1 OutState, 3 / 4 = row 2, 5 / 6 = row 3,
 *   7 = Value In Percentage, 8 = Value In Amount.
 * A fixed footer can cover the bottom buttons, so clicks go through click_safely().
 * Typing: an empty field is typed into directly, without clear(). On this form clear() triggers the
 * validation and can make the cursor jump to the start after the first character.
 * Deleting a profile shows a browser alert "Deleted successfully", handled by Confirm_delete().
 */
public class F_TaxDetails_Cls {

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

	@FindBy(xpath = "//*[contains(text(),'Showing')]")
	WebElement Entries_info;

	@FindBy(xpath = "//*[normalize-space(text())='Previous']")
	WebElement Previous_btn;

	@FindBy(xpath = "//*[normalize-space(text())='Next']")
	WebElement Next_btn;

	// ---------- FORM screen ----------
	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Back']")
	WebElement Back_btn;

	// ---------- Constructor ----------
	public F_TaxDetails_Cls(WebDriver driver) {
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

	// types into a field. An empty field is typed into directly (no clear(), which can trigger the validation
	// and make the cursor jump); a field that already has text is emptied with Ctrl+A and Delete first.
	private void type(WebElement field, String value) {
		String current = field.getAttribute("value");
		if (current != null && !current.isEmpty()) {
			field.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
		}
		if (!value.isEmpty()) {
			field.sendKeys(value);
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

	// the Tax Profile Name box of the search form: the only visible "Enter ..." box on the list screen
	private WebElement search_input() {
		By by = By.xpath("//input[@placeholder='Enter ...']");
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	// The search form stays hidden until the top Search button is clicked
	public void open_search_form() {
		if (displayed(By.xpath("//input[@placeholder='Enter ...']")).isEmpty()) {
			click_safely(Top_search_btn);
		}
		search_input();
	}

	public WebElement verify_search_input() {
		return search_input();
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	// Opens the search form, types the profile name (blank = leave empty) and clicks the form's Search button
	public void Search_profile(String profile_name) {
		open_search_form();
		type(search_input(), profile_name);
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

	// the profile names (first column) of the rows in the table
	public List<String> Profile_names() {
		List<String> names = new ArrayList<String>();
		for (WebElement cell : driver.findElements(By.xpath("//table//tbody/tr/td[1]"))) {
			names.add(cell.getText().trim());
		}
		return names;
	}

	// waits up to 'seconds' seconds until the table shows a row containing this text; true if it appeared
	public boolean Wait_for_row(String text, int seconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(seconds)).until(d -> result_texts().toString().contains(text));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	// the profile name (first column) of the first row
	public String first_row_name() {
		return driver.findElement(By.xpath("//table//tbody/tr[1]/td[1]")).getText().trim();
	}

	// the icons (view, edit, delete) of the row with this profile name
	private List<WebElement> row_icons(String profile_name) {
		List<WebElement> icons = driver.findElements(
				By.xpath("//tr[td[normalize-space()='" + profile_name + "']]/td[last()]//a"));
		if (icons.size() < 3) {
			icons = driver.findElements(By.xpath("//tr[td[normalize-space()='" + profile_name + "']]/td[last()]//i"));
		}
		return icons;
	}

	// the icon number 'index' (0 view, 1 edit, 2 delete) of the row with this profile name;
	// a clear error if the row is missing
	private WebElement icon(String profile_name, int index) {
		List<WebElement> icons = row_icons(profile_name);
		if (icons.size() <= index) {
			throw new NoSuchElementException("Row '" + profile_name + "' not found in the table (" + icons.size() + " icons)");
		}
		return icons.get(index);
	}

	public int icon_count(String profile_name) {
		return row_icons(profile_name).size();
	}

	public void Click_view(String profile_name) {
		click_safely(icon(profile_name, 0));
	}

	public void Click_edit(String profile_name) {
		click_safely(icon(profile_name, 1));
	}

	public void Click_delete(String profile_name) {
		click_safely(icon(profile_name, 2));
	}

	// Handles everything the delete shows: browser alerts (a question and / or "Deleted successfully")
	// and pop-up buttons (Yes / OK / Confirm / Delete). Returns the texts of the alerts that were accepted.
	public List<String> Confirm_delete() {
		List<String> alert_texts = new ArrayList<String>();
		By confirm = By.xpath("//button[normalize-space()='Yes' or normalize-space()='OK' or normalize-space()='Ok'"
				+ " or normalize-space()='Confirm' or normalize-space()='Delete' or normalize-space()='Yes, delete it!']");

		for (int step = 1; step <= 4; step++) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(3), Duration.ofMillis(100)).until(ExpectedConditions.alertIsPresent());
				Alert alert = driver.switchTo().alert();
				alert_texts.add(alert.getText());
				alert.accept();
				continue;   // another alert may follow
			} catch (TimeoutException e) {
				// no browser alert right now
			}

			List<WebElement> buttons = displayed(confirm);
			if (!buttons.isEmpty()) {
				click_safely(buttons.get(0));
				continue;
			}

			if (!alert_texts.isEmpty()) {
				break;   // something was handled and nothing else is showing
			}
		}
		return alert_texts;
	}

	// true if the list screen is on screen (the Create button only exists on the list screen)
	public boolean is_list_page() {
		return !displayed(By.xpath("//*[self::button or self::a][normalize-space()='Create']")).isEmpty();
	}

	// ======================= FORM screen: inputs =======================

	// the visible text inputs after the "Tax Profile Name" label, in page order (see the notes at the top)
	private List<WebElement> form_inputs() {
		By by = By.xpath("//*[normalize-space(text())='Tax Profile Name']/following::input"
				+ "[not(@type='radio' or @type='checkbox' or @type='hidden' or @type='submit' or @type='button')]");
		wait.until(d -> displayed(by).size() >= 12);
		return displayed(by);
	}

	public WebElement verify_profile_name() {
		return form_inputs().get(0);
	}

	// row = 1..3
	public WebElement verify_code(int row) {
		return form_inputs().get(1 + (row - 1) * 3);
	}

	public WebElement verify_tax_name(int row) {
		return form_inputs().get(2 + (row - 1) * 3);
	}

	public WebElement verify_value(int row) {
		return form_inputs().get(3 + (row - 1) * 3);
	}

	public WebElement verify_additional_name() {
		return form_inputs().get(10);
	}

	public WebElement verify_additional_value() {
		return form_inputs().get(11);
	}

	// the number of inputs on the form (grows when the green plus adds an additional tax row)
	public int form_input_count() {
		return displayed(By.xpath("//*[normalize-space(text())='Tax Profile Name']/following::input"
				+ "[not(@type='radio' or @type='checkbox' or @type='hidden' or @type='submit' or @type='button')]"))
						.size();
	}

	// ======================= FORM screen: radio buttons and check boxes =======================

	private List<WebElement> radios() {
		By by = By.xpath("//input[@type='radio']");
		wait.until(d -> displayed(by).size() >= 8);
		return displayed(by);
	}

	// row = 1..3
	public WebElement verify_in_state(int row) {
		return radios().get((row - 1) * 2);
	}

	public WebElement verify_out_state(int row) {
		return radios().get((row - 1) * 2 + 1);
	}

	public WebElement verify_percentage() {
		return radios().get(6);
	}

	public WebElement verify_amount() {
		return radios().get(7);
	}

	public void Select_state_type(int row, boolean in_state) {
		click_safely(in_state ? verify_in_state(row) : verify_out_state(row));
	}

	public void Select_value_in(boolean percentage) {
		click_safely(percentage ? verify_percentage() : verify_amount());
	}

	private List<WebElement> checkboxes() {
		By by = By.xpath("//input[@type='checkbox']");
		wait.until(d -> displayed(by).size() >= 3);
		return displayed(by);
	}

	// n = 1..3
	public WebElement verify_applicable(int n) {
		return checkboxes().get(n - 1);
	}

	public void Tick_applicable(int n) {
		click_safely(verify_applicable(n));
	}

	// the text next to each of the 3 "Applicable for" check boxes
	public List<String> applicable_labels() {
		List<String> labels = new ArrayList<String>();
		for (int i = 1; i <= 3; i++) {
			try {
				labels.add(verify_applicable(i).findElement(By.xpath("./following::*[self::label or self::span]"
						+ "[normalize-space()][1]")).getText().trim());
			} catch (Exception e) {
				labels.add("");
			}
		}
		return labels;
	}

	// ======================= FORM screen: How to Apply, plus / minus =======================

	public WebElement verify_how_to_apply() {
		By by = By.xpath("//select[option[normalize-space()='Select an item']]");
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	public List<String> how_to_apply_options() {
		return options_of(verify_how_to_apply());
	}

	public void Select_first_how_to_apply() {
		select_first(verify_how_to_apply());
	}

	// the green plus and red minus icons beside "Applicable for".
	// TODO: if these are not found, inspect the icons and adjust the locators.
	public WebElement verify_plus() {
		return displayed(By.xpath("//*[contains(@class,'plus')]")).get(0);
	}

	public WebElement verify_minus() {
		return displayed(By.xpath("//*[contains(@class,'minus')]")).get(0);
	}

	public void Click_plus() {
		click_safely(verify_plus());
	}

	public void Click_minus() {
		click_safely(verify_minus());
	}

	// ======================= FORM screen: filling =======================

	// Fills the profile name and the 3 tax rows (arrays of 3: codes, names, values)
	public void Fill_profile(String profile_name, String[] codes, String[] names, String[] values) {
		type(verify_profile_name(), profile_name);
		for (int row = 1; row <= 3; row++) {
			type(verify_code(row), codes[row - 1]);
			type(verify_tax_name(row), names[row - 1]);
			type(verify_value(row), values[row - 1]);
		}
	}

	// Fills the Additional Tax block: name, value, Value In, the first How to Apply option and Applicable for Tax n
	public void Fill_additional(String name, String value, boolean percentage, int applicable_tax) {
		type(verify_additional_name(), name);
		type(verify_additional_value(), value);
		Select_value_in(percentage);
		Select_first_how_to_apply();
		Tick_applicable(applicable_tax);
	}

	// ======================= FORM screen: buttons and messages =======================

	// true if the form screen (create, view or edit) is on screen: the "Additional Tax" block is visible
	public boolean is_form_screen() {
		return !displayed(By.xpath("//*[normalize-space(text())='Additional Tax']")).isEmpty();
	}

	public boolean is_submit_displayed() {
		return !displayed(By.id("SaveSubmit")).isEmpty()
				|| !displayed(By.xpath("//button[normalize-space()='Submit']")).isEmpty();
	}

	// the form's Submit button (its id is SaveSubmit on the other screens)
	public WebElement verify_submit() {
		List<WebElement> by_id = displayed(By.id("SaveSubmit"));
		if (!by_id.isEmpty()) {
			return by_id.get(0);
		}
		return displayed(By.xpath("//button[normalize-space()='Submit']")).get(0);
	}

	public WebElement verify_back() {
		return Back_btn;
	}

	public void Click_submit() {
		click_safely(verify_submit());
	}

	public void Click_back() {
		wait.until(ExpectedConditions.visibilityOf(Back_btn));
		click_safely(Back_btn);
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