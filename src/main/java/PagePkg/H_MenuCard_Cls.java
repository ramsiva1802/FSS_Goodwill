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
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * Page class of Stock Item > Menu Card.
 *
 * The page has two screens:
 *   1. LIST screen : Search / Create buttons, search form (code, name), table filter, result table
 *                    (code, name, is editable, no of item, view + edit icon per row, NO delete), paging
 *   2. FORM screen : create / view (greyed out) / edit. Header (company, code, name, is editable, remarks,
 *                    is active), cart button with the item count, "Select Items" block (category, sub category,
 *                    item name search, item table with tick box + sort by + stock code + stock name), Back, Submit.
 * Ticking an item fills the next sort number automatically and raises the cart count.
 * The cart button opens the "Selected Items" pop-up (sort by, stock code, stock name, Close).
 * Menu cards cannot be deleted: they are deactivated by unticking Is Active in the edit form.
 * Typing: an empty field is typed into directly, without clear().
 */
public class H_MenuCard_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// category that has stock items in the QA data
	public static final String CATEGORY = "HOSE";

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

	// ---------- FORM screen ----------
	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Back']")
	WebElement Back_btn;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Submit']")
	WebElement Submit_btn;

	// ---------- Constructor ----------
	public H_MenuCard_Cls(WebDriver driver) {
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

	// the first visible element of the first locator that finds one (waits for any of them)
	private WebElement first_of(By... locators) {
		wait.until(d -> {
			for (By by : locators) {
				if (!displayed(by).isEmpty()) {
					return true;
				}
			}
			return false;
		});
		for (By by : locators) {
			List<WebElement> found = displayed(by);
			if (!found.isEmpty()) {
				return found.get(0);
			}
		}
		throw new NoSuchElementException("Element not found");
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

	public WebElement verify_entries() {
		return Entries_info;
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

	public boolean is_form_screen() {
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

	// the search form (code, name) of the list screen
	public WebElement verify_search_code() {
		return input("Enter Code");
	}

	public WebElement verify_search_name() {
		return input("Enter Name");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	// opens the search form when it is hidden, fills it and searches
	public void Search_menu(String code, String name) {
		if (displayed(By.xpath("//input[@placeholder='Enter Code']")).isEmpty()) {
			Click_top_search();
		}
		type(verify_search_code(), code);
		type(verify_search_name(), name);
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

	// the cell texts of every result row: [code, name, is editable, no of item]
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

	public List<String> Menu_codes() {
		List<String> codes = new ArrayList<String>();
		for (List<String> cells : result_texts()) {
			codes.add(cells.get(0));
		}
		return codes;
	}

	// the cells of the row of one code (null when the code is not in the table)
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
		throw new NoSuchElementException("Menu card not found in the list: " + code);
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

	// ======================= FORM screen: header =======================

	public WebElement verify_company() {
		return label_select("Company");
	}

	public WebElement verify_code() {
		return input("Enter Code");
	}

	public WebElement verify_name() {
		return input("Enter Name");
	}

	public WebElement verify_editable() {
		return label_select("Is Editable");
	}

	public WebElement verify_remarks() {
		return first_displayed(By.xpath("//textarea[@placeholder='Enter Remark']"));
	}

	public WebElement verify_active() {
		return first_of(
				By.xpath("//input[@type='checkbox' and (contains(@id,'Active') or contains(@name,'Active'))]"),
				By.xpath("//label[contains(normalize-space(),'Is Active')]//input[@type='checkbox']"),
				By.xpath("//label[contains(normalize-space(),'Is Active')]/preceding::input[@type='checkbox'][1]"));
	}

	public WebElement verify_submit() {
		return Submit_btn;
	}

	public WebElement verify_back() {
		return Back_btn;
	}

	public void Fill_header(String code, String name, String remarks, String editable) {
		type(verify_code(), code);
		type(verify_name(), name);
		if (!remarks.isEmpty()) {
			type(verify_remarks(), remarks);
		}
		if (!editable.isEmpty()) {
			select_text(verify_editable(), editable);
		}
	}

	// ticks or unticks Is Active
	public void Set_active(boolean on) {
		WebElement box = verify_active();
		if (box.isSelected() != on) {
			click_safely(box);
		}
	}

	public void Click_submit() {
		click_safely(Submit_btn);
	}

	public void Click_back() {
		click_safely(Back_btn);
	}

	// ======================= FORM screen: cart and pop-up =======================

	public WebElement verify_cart() {
		return first_of(By.xpath("//*[self::button or self::a][.//i[contains(@class,'cart')]]"),
				By.xpath("//*[contains(@class,'badge')]/ancestor::*[self::button or self::a][1]"));
	}

	// the number on the cart button
	public int cart_count() {
		String digits = verify_cart().getText().replaceAll("\\D", "");
		return digits.isEmpty() ? 0 : Integer.parseInt(digits);
	}

	public void Open_cart() {
		click_safely(verify_cart());
		first_displayed(By.xpath("//*[contains(normalize-space(text()),'Selected Items')]"));
	}

	// the rows of the "Selected Items" pop-up: [sort by, stock code, stock name]
	public List<List<String>> cart_rows() {
		List<List<String>> out = new ArrayList<List<String>>();
		List<WebElement> rows = displayed(By.xpath(
				"//div[contains(@class,'modal') and contains(@class,'show')]//tbody/tr[td]"));
		for (WebElement row : rows) {
			List<String> cells = new ArrayList<String>();
			for (WebElement td : row.findElements(By.tagName("td"))) {
				cells.add(td.getAttribute("textContent").trim());
			}
			if (cells.size() > 1) {
				out.add(cells);
			}
		}
		return out;
	}

	public boolean cart_popup_open() {
		return !displayed(By.xpath("//div[contains(@class,'modal') and contains(@class,'show')]")).isEmpty();
	}

	public void Close_cart() {
		click_safely(first_displayed(By.xpath("//div[contains(@class,'modal')]//button[normalize-space()='Close']")));
		wait.until(d -> !cart_popup_open());
	}

	// ======================= FORM screen: select items =======================

	public WebElement verify_category() {
		return label_select("Category:");
	}

	public WebElement verify_sub_category() {
		return label_select("Sub Category:");
	}

	public WebElement verify_item_search() {
		return input("Enter Item Name");
	}

	public void Select_category(String category) {
		select_text(verify_category(), category);
		wait_for_items();
	}

	public void Select_sub_category(String sub_category) {
		select_text(verify_sub_category(), sub_category);
		wait_for_items();
	}

	// searches an item by name with the magnifier next to the item name box
	public void Search_item(String text) {
		type(verify_item_search(), text);
		WebElement icon = first_displayed(By.xpath(
				"//input[@placeholder='Enter Item Name']/following::*[self::i or self::button or self::span][1]"));
		click_safely(icon);
		wait_for_items();
	}

	private void wait_for_items() {
		WaitUtil.settle(driver, 1500);
	}

	// the rows of the item table (the ones with a tick box)
	private List<WebElement> item_rows() {
		return displayed(By.xpath("//tr[td/input[@type='checkbox']]"));
	}

	public int item_row_count() {
		return item_rows().size();
	}

	// [stock code, stock name] of one item row
	public List<String> item_texts(int index) {
		List<WebElement> cells = item_rows().get(index).findElements(By.tagName("td"));
		List<String> out = new ArrayList<String>();
		out.add(cells.get(2).getText().trim());
		out.add(cells.get(3).getText().trim());
		return out;
	}

	public boolean item_ticked(int index) {
		return item_rows().get(index).findElement(By.xpath(".//input[@type='checkbox']")).isSelected();
	}

	// the sort number of an item row
	public String sort_value(int index) {
		WebElement sort = item_rows().get(index)
				.findElement(By.xpath(".//input[not(@type='checkbox') and not(@type='hidden')]"));
		String v = sort.getAttribute("value");
		return v == null ? "" : v.trim();
	}

	public void Set_sort(int index, String value) {
		WebElement sort = item_rows().get(index)
				.findElement(By.xpath(".//input[not(@type='checkbox') and not(@type='hidden')]"));
		type(sort, value);
	}

	public void Tick_item(int index) {
		WebElement box = item_rows().get(index).findElement(By.xpath(".//input[@type='checkbox']"));
		if (!box.isSelected()) {
			click_safely(box);
			wait_for_items();
		}
	}

	public void Untick_item(int index) {
		WebElement box = item_rows().get(index).findElement(By.xpath(".//input[@type='checkbox']"));
		if (box.isSelected()) {
			click_safely(box);
			wait_for_items();
		}
	}

	// ticks the first 'count' items of the table
	public void Tick_first_items(int count) {
		for (int i = 0; i < count; i++) {
			Tick_item(i);
		}
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

	// closes a sweet alert / alert pop-up that is open
	public void Close_notice() {
		List<WebElement> ok = displayed(By.xpath("//button[contains(@class,'swal2-confirm')]"));
		if (!ok.isEmpty()) {
			click_safely(ok.get(0));
		}
	}
}