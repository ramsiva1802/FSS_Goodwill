package PagePkg;

import UtillPkg.WaitUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
import org.openqa.selenium.StaleElementReferenceException;

/*
 * NOTES - G_StockItem_Cls (Page Object Model)
 * -----------------------------------------
 * Stock Item > Stock Item has two screens:
 *   1. LIST screen : Search / Create buttons, search form (code, name, status), table filter, "Show N entries",
 *                    result table (a view and an edit icon per row, NO delete), paging
 *   2. FORM screen : the stock item form (create, view = greyed out with only Back, edit), with
 *                    Add pop-ups (category, sub category, brand, unit) and the Item Attributes block
 *                    (Add Attribute cards, + New pop-up, Edit Attribute pop-up).
 * MANDATORY fields (11): Stock Item Code, Stock Item Name, Alias Name, HSN Code, Stock MRP,
 *   Minimum Sale Quantity, Category, Sub Category, Brand, Unit, Stock Type.
 * OPTIONAL: Product Group, Conversion Factor, Tax Profile, Base Unit, Is Active (ticked by default).
 * Drop downs are found by their label. Text inputs by their placeholder ("Enter Stock Item Code" ...).
 * Items cannot be deleted: they are deactivated by unticking Is Active in the edit form.
 * Typing: an empty field is typed into directly, without clear() (clear can trigger the validation).
 * A fixed footer can cover the bottom buttons, so clicks go through click_safely().
 */
public class G_StockItem_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// data used when filling the form (existing values of the QA data)
	public static final String CATEGORY = "Rono-Cate";
	public static final String SUB_CATEGORY = "Rono-Sub2";
	public static final String BRAND = "Cr7-Brand";
	public static final String TAX_PROFILE = "GST@18%";
	public static final String UNIT = "Goal number";
	public static final String STOCK_TYPE = "Complete Products";

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

	@FindBy(xpath = "//*[self::button or self::a][contains(normalize-space(),'Add Attribute')]")
	WebElement Add_attribute_btn;

	// ---------- Constructor ----------
	public G_StockItem_Cls(WebDriver driver) {
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

	// the text of the option that is currently chosen in a drop down
	public String selected(WebElement dropdown) {
		return new Select(dropdown).getFirstSelectedOption().getText().trim();
	}

	// a visible drop down found by the text of its label
	private WebElement label_select(String label) {
		return first_displayed(By.xpath("//label[normalize-space()='" + label + "']/following::select[1]"));
	}

	// a visible text input found by its placeholder
	private WebElement input(String placeholder) {
		return first_displayed(By.xpath("//input[@placeholder='" + placeholder + "']"));
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
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_filter')]//input | //input[@type='search']"
				+ " | //label[normalize-space()='Search:']/input"));
	}

	public WebElement verify_table_filter() {
		return table_filter();
	}

	// the "Show N entries" drop down
	private WebElement entries_select() {
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_length')]//select"
				+ " | //label[contains(normalize-space(),'Show')]//select"));
	}

	public WebElement verify_entries_select() {
		return entries_select();
	}

	// Chooses the largest "Show N entries" option and returns its text
	public String Select_largest_entries() {
		Select select = new Select(entries_select());
		int last = select.getOptions().size() - 1;
		select.selectByIndex(last);
		return select.getOptions().get(last).getText().trim();
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

	// clicks a page number of the pagination, for example "2"
	public void Click_page(String number) {
		click_safely(first_displayed(By.xpath("//a[normalize-space()='" + number + "']")));
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
		if (displayed(By.xpath("//input[@placeholder='Enter Stock Code']")).isEmpty()) {
			click_safely(Top_search_btn);
		}
		input("Enter Stock Code");
	}

	public WebElement verify_search_code() {
		return input("Enter Stock Code");
	}

	public WebElement verify_search_name() {
		return input("Enter Stock Name");
	}

	public WebElement verify_search_status() {
		return label_select("Status");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public List<String> status_options() {
		List<String> texts = new ArrayList<String>();
		for (WebElement option : new Select(verify_search_status()).getOptions()) {
			texts.add(option.getText().trim());
		}
		return texts;
	}

	// Opens the search form, types the code and name (blank = leave empty), chooses the status
	// (blank = keep the default Active) and clicks the form's Search button
	public void Search_stock(String code, String name, String status) {
		open_search_form();
		type(input("Enter Stock Code"), code);
		type(input("Enter Stock Name"), name);
		if (!status.isEmpty()) {
			select_text(label_select("Status"), status);
		}
		click_safely(Search_go);
	}

	// Types in the "Search:" box above the table (it filters the table)
	public void Filter_table(String text) {
		WebElement box = table_filter();
		box.clear();
		box.sendKeys(text);
	}

	// Number of real result rows ("No data available" / "No matching records" is not counted).
	// A row that went stale because the table was redrawn is skipped.
	public int result_count() {
		int count = 0;
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr"))) {
			try {
				String text = row.getText().toLowerCase();
				if (!text.isEmpty() && !text.contains("no data") && !text.contains("no matching")
						&& !text.contains("no records")) {
					count++;
				}
			} catch (StaleElementReferenceException e) {
				// the table was redrawn while reading
			}
		}
		return count;
	}

	// The text of every row of the result table (stale rows are skipped)
	public List<String> result_texts() {
		List<String> texts = new ArrayList<String>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr"))) {
			try {
				texts.add(row.getText().replace("\n", " | "));
			} catch (StaleElementReferenceException e) {
				// the table was redrawn while reading
			}
		}
		return texts;
	}

	// the stock codes (first column) of the rows in the table (stale rows are skipped)
	public List<String> Stock_codes() {
		List<String> codes = new ArrayList<String>();
		for (WebElement cell : driver.findElements(By.xpath("//table//tbody/tr/td[1]"))) {
			try {
				codes.add(cell.getText().trim());
			} catch (StaleElementReferenceException e) {
				// the table was redrawn while reading
			}
		}
		return codes;
	}

	// waits up to 'seconds' seconds until the table shows a row containing this text; true if it appeared
	public boolean Wait_for_row(String text, int seconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(seconds)).ignoring(StaleElementReferenceException.class)
					.until(d -> result_texts().toString().contains(text));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}
	// the stock code (first column) of the first row
	public String first_row_code() {
		return driver.findElement(By.xpath("//table//tbody/tr[1]/td[1]")).getText().trim();
	}

	// the icons (view, edit) of the row with this stock code
	private List<WebElement> row_icons(String code) {
		List<WebElement> icons = driver.findElements(
				By.xpath("//tr[td[normalize-space()='" + code + "']]/td[last()]//a"));
		if (icons.size() < 2) {
			icons = driver.findElements(By.xpath("//tr[td[normalize-space()='" + code + "']]/td[last()]//i"));
		}
		return icons;
	}

	// the icon number 'index' (0 view, 1 edit); a clear error if the row is missing
	private WebElement icon(String code, int index) {
		List<WebElement> icons = row_icons(code);
		if (icons.size() <= index) {
			throw new NoSuchElementException("Row '" + code + "' not found in the table (" + icons.size() + " icons)");
		}
		return icons.get(index);
	}

	public int icon_count(String code) {
		return row_icons(code).size();
	}

	public void Click_view(String code) {
		click_safely(icon(code, 0));
	}

	public void Click_edit(String code) {
		click_safely(icon(code, 1));
	}

	// true if the list screen is on screen (the Create button only exists on the list screen)
	public boolean is_list_page() {
		return !displayed(By.xpath("//*[self::button or self::a][normalize-space()='Create']")).isEmpty();
	}

	// ======================= FORM screen: fields =======================

	public WebElement verify_company() {
		return label_select("Company");
	}

	// the Product Group multi select
	public WebElement verify_product_group() {
		return first_displayed(By.xpath("//*[@placeholder='Select Multi Products' or normalize-space(text())='Select Multi Products']"));
	}

	public WebElement verify_category() {
		return label_select("Category");
	}

	public WebElement verify_sub_category() {
		return label_select("Sub Category");
	}

	public WebElement verify_brand() {
		return label_select("Brand");
	}

	public WebElement verify_tax_profile() {
		return label_select("Tax Profile");
	}

	public WebElement verify_unit() {
		return label_select("Unit");
	}

	public WebElement verify_base_unit() {
		return label_select("Base Unit");
	}

	public WebElement verify_stock_type() {
		return label_select("Stock Type");
	}

	public WebElement verify_code() {
		return input("Enter Stock Item Code");
	}

	public WebElement verify_name() {
		return input("Enter Stock Item Name");
	}

	public WebElement verify_alias() {
		return input("Enter Alias Name");
	}

	public WebElement verify_hsn() {
		return input("Enter HSN Code");
	}

	public WebElement verify_mrp() {
		return input("Enter Stock MRP");
	}

	public WebElement verify_min_qty() {
		return input("Enter Minimum Sale Quantity");
	}

	public WebElement verify_conversion() {
		return input("Enter Conversion Factor");
	}

	public WebElement verify_active() {
		return first_displayed(By.xpath("//input[@type='checkbox']"));
	}

	public void Click_active() {
		click_safely(verify_active());
	}

	public WebElement verify_upload_photo() {
		return first_displayed(By.xpath("//*[contains(normalize-space(text()),'Upload')]"));
	}

	// the Add buttons beside Category (1), Sub Category (2), Brand (3) and Unit (4)
	public WebElement verify_add(int n) {
		By by = By.xpath("//*[self::button or self::a][normalize-space()='Add']");
		wait.until(d -> displayed(by).size() >= n);
		return displayed(by).get(n - 1);
	}

	// ======================= FORM screen: drop down options and choices =======================

	public List<String> category_options() {
		return options_of(verify_category());
	}

	public List<String> sub_category_options() {
		return options_of(verify_sub_category());
	}

	public List<String> brand_options() {
		return options_of(verify_brand());
	}

	public List<String> tax_profile_options() {
		return options_of(verify_tax_profile());
	}

	public List<String> unit_options() {
		return options_of(verify_unit());
	}

	public List<String> base_unit_options() {
		return options_of(verify_base_unit());
	}

	public List<String> stock_type_options() {
		return options_of(verify_stock_type());
	}

	public void Select_category(String text) {
		select_text(verify_category(), text);
	}

	// puts one of the mandatory drop downs (Category, Sub Category, Brand, Unit, Stock Type) back to its
	// empty first option. The first option is disabled, so it is set through JavaScript.
	public void Reset_choice(String field) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].selectedIndex = 0; arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
				label_select(field));
	}

	// ======================= FORM screen: filling =======================

	// Fills ONLY the 11 mandatory fields: 6 text boxes (a blank value is left empty) and the drop downs
	// Category, Sub Category, Brand, Unit and Stock Type (existing QA data, see the constants).
	public void Fill_mandatory(String code, String name, String alias, String hsn, String mrp, String min_qty) {
		type(verify_code(), code);
		type(verify_name(), name);
		type(verify_alias(), alias);
		type(verify_hsn(), hsn);
		type(verify_mrp(), mrp);
		type(verify_min_qty(), min_qty);

		select_text(verify_category(), CATEGORY);
		WaitUtil.settle(driver, 1500);
		select_text(verify_sub_category(), SUB_CATEGORY);
		select_text(verify_brand(), BRAND);
		select_text(verify_unit(), UNIT);
		select_text(verify_stock_type(), STOCK_TYPE);
	}

	// Fills the optional fields: Conversion Factor, Tax Profile and Base Unit
	public void Fill_optional(String conversion) {
		type(verify_conversion(), conversion);
		select_text(verify_tax_profile(), TAX_PROFILE);
		select_text(verify_base_unit(), UNIT);
	}

	// Fills the whole form: mandatory + optional
	public void Fill_item(String code, String name, String alias, String hsn, String mrp, String min_qty,
			String conversion) {
		Fill_mandatory(code, name, alias, hsn, mrp, min_qty);
		Fill_optional(conversion);
	}

	public void Change_mrp(String mrp) {
		type(verify_mrp(), mrp);
	}

	// ======================= FORM screen: Add pop-ups (category, sub category, brand, unit) =======================

	// Clicks the Add button number n and waits up to 4 seconds for the pop-up with this title
	public void Open_add_popup(int n, String title) {
		click_safely(verify_add(n));
		try {
			new WebDriverWait(driver, Duration.ofSeconds(4), Duration.ofMillis(100)).until(d -> is_popup_open(title));
		} catch (TimeoutException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", verify_add(n));
		}
	}

	public boolean is_popup_open(String title) {
		return !displayed(By.xpath("//*[normalize-space(text())='" + title + "']")).isEmpty();
	}

	public WebElement verify_popup_input(String placeholder) {
		return input(placeholder);
	}

	public WebElement verify_popup_save() {
		return first_displayed(By.xpath("//button[normalize-space()='Save']"));
	}

	public void Click_popup_save() {
		click_safely(verify_popup_save());
	}

	// the X (close) of a pop-up: the nearest close element after or before its title
	public void Close_popup(String title) {
		By close_after = By.xpath("//*[normalize-space(text())='" + title + "']/following::*"
				+ "[contains(@class,'close') or normalize-space(text())='\u00D7' or normalize-space(text())='\u2715'][1]");
		By close_before = By.xpath("//*[normalize-space(text())='" + title + "']/preceding::*"
				+ "[contains(@class,'close') or normalize-space(text())='\u00D7' or normalize-space(text())='\u2715'][1]");
		List<WebElement> found = displayed(close_after);
		if (found.isEmpty()) {
			found = displayed(close_before);
		}
		click_safely(found.get(0));
	}

	// ======================= FORM screen: Item Attributes =======================

	// the attribute drop down of every attribute card (the cards are the blocks that contain them)
	private List<WebElement> attribute_selects() {
		return displayed(By.xpath("//select[option[contains(normalize-space(),'Select Attribute Type')]]"));
	}

	public int attribute_count() {
		return attribute_selects().size();
	}

	public boolean is_no_attributes_message() {
		return !displayed(By.xpath("//*[contains(normalize-space(text()),'No attributes added yet')]")).isEmpty();
	}

	public WebElement verify_add_attribute() {
		return Add_attribute_btn;
	}

	public void Click_add_attribute() {
		click_safely(Add_attribute_btn);
	}

	// the block (card) of the attribute number 'index' (0 = first)
	private WebElement card(int index) {
		return attribute_selects().get(index).findElement(
				By.xpath("./ancestor::div[.//*[contains(normalize-space(text()),'Values')]][1]"));
	}

	public String card_text(int index) {
		return card(index).getText();
	}

	// how many times a text appears in the card (a chosen value appears in the values box and in the presets)
	public int card_text_count(int index, String text) {
		String all = card_text(index);
		int count = 0;
		int from = 0;
		while ((from = all.indexOf(text, from)) >= 0) {
			count++;
			from += text.length();
		}
		return count;
	}

	public String selected_attribute(int index) {
		return selected(attribute_selects().get(index));
	}

	public List<String> attribute_options(int index) {
		return options_of(attribute_selects().get(index));
	}

	public void Select_attribute(int index, String text) {
		select_text(attribute_selects().get(index), text);
	}

	// clicks a value chip of the card: the LAST match is the preset chip below the values box
	public void Click_preset(int index, String value) {
		List<WebElement> chips = card(index).findElements(By.xpath(".//*[normalize-space(text())='" + value + "']"));
		click_safely(chips.get(chips.size() - 1));
	}

	// the red x of the card: the first button after the attribute drop down.
	// TODO: if the wrong thing is clicked, inspect the red x and adjust this.
	public void Click_remove_attribute(int index) {
		click_safely(attribute_selects().get(index).findElement(By.xpath("./following::button[1]")));
	}

	public void Click_new_attribute(int index) {
		click_safely(card(index).findElement(By.xpath(".//*[self::button or self::a][contains(normalize-space(),'New')]")));
	}

	public void Click_edit_attribute(int index) {
		click_safely(card(index).findElement(By.xpath(".//*[self::button or self::a][normalize-space()='Edit Attribute']")));
	}

	// ---- "Add Values" pop-up (+ New) ----
	public boolean is_add_values_open() {
		return !displayed(By.xpath("//button[normalize-space()='Save Values']")).isEmpty();
	}

	public WebElement verify_values_attribute_name() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'Colour, Size, Material')]"));
	}

	public WebElement verify_values_input() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'Enter a value')]"));
	}

	public WebElement verify_values_save() {
		return first_displayed(By.xpath("//button[normalize-space()='Save Values']"));
	}

	public void Click_values_save() {
		click_safely(verify_values_save());
	}

	// ---- "Edit Attribute" pop-up ----
	public boolean is_edit_attribute_open() {
		return !displayed(By.xpath("//button[normalize-space()='Save Changes']")).isEmpty();
	}

	public WebElement verify_edit_save_changes() {
		return first_displayed(By.xpath("//button[normalize-space()='Save Changes']"));
	}

	public WebElement verify_edit_delete() {
		return first_displayed(By.xpath("//button[normalize-space()='Delete']"));
	}

	// the text inside the Edit Attribute pop-up (its attribute name and value chips)
	public String edit_attribute_text() {
		return verify_edit_save_changes().findElement(
				By.xpath("./ancestor::div[.//*[contains(normalize-space(text()),'Attribute Name')]][1]")).getText();
	}

	// the Cancel button of the open pop-up (Add Values or Edit Attribute)
	public WebElement verify_cancel() {
		return first_displayed(By.xpath("//button[normalize-space()='Cancel']"));
	}

	public void Click_cancel() {
		click_safely(verify_cancel());
	}

	// ======================= FORM screen: buttons and messages =======================

	// true if the form screen (create, view or edit) is on screen
	public boolean is_form_screen() {
		return !displayed(By.xpath("//input[@placeholder='Enter Stock Item Code']")).isEmpty();
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
	// BULK DATA ENTRY: fills the 11 mandatory fields with the values given (one Excel row)
	public void Enter_item(String code, String name, String alias, String hsn, String mrp, String min_qty,
			String category, String sub_category, String brand, String unit, String stock_type) {
		type(verify_code(), code);
		type(verify_name(), name);
		type(verify_alias(), alias);
		type(verify_hsn(), hsn);
		type(verify_mrp(), mrp);
		type(verify_min_qty(), min_qty);

		select_text(verify_category(), category);
		WaitUtil.settle(driver, 1500);
		select_text(verify_sub_category(), sub_category);
		select_text(verify_brand(), brand);
		select_text(verify_unit(), unit);
		select_text(verify_stock_type(), stock_type);
	}
	// The red validation messages currently shown ("Please ..." texts, or error / danger styled texts)
	public List<String> validation_messages() {
		List<String> messages = new ArrayList<String>();
		for (WebElement e : driver.findElements(By.xpath("//*[starts-with(normalize-space(text()),'Please')"
				+ " or ((contains(@class,'text-danger') or contains(@class,'error') or contains(@class,'invalid'))"
				+ " and string-length(normalize-space(text())) > 0)]"))) {
			if (e.isDisplayed() && !e.getText().trim().isEmpty() && !messages.contains(e.getText().trim())) {
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