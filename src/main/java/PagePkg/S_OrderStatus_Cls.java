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
 * Page class of Field Management > Order Status (a LIST screen; orders are created by the mobile app).
 *
 * Top: Search button (opens the search form) and the table filter "Search:".
 * Search form: Employee Type, Employee Name (search box), From Date, To Date (dd/MM/yyyy), Customer (search box),
 * Hierarchy Type (starts on Factory), Supply By (search box), Approval Status, Search and Clear.
 * Result table columns (cell index): 0 order number, 1 order date, 2 route, 3 customer, 4 order amount,
 * 5 delivery date, 6 order status, 7 actions. Actions: view (eye), edit (pencil), delete (bin), print.
 * Only Pending orders have edit and delete.
 * "Order Details" pop-up (view / edit): customer name (Change button when editing), order number, item lines
 * [sl, item code, item, unit, unit rate, discount, qty, net amount, delete], the "+" that opens the add-item
 * row (Item search, Attribute Name, Attribute Value, Add), Remark, Approve and Save changes.
 * Print opens the PrintOrder page (Print Order / Cancel) in a new tab.
 * Typing: an empty field is typed into directly, without clear().
 */
public class S_OrderStatus_Cls {

	WebDriver driver;
	WebDriverWait wait;

	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

	static final String MODAL = "//div[contains(@class,'modal') and contains(@class,'show')]"
			+ "[.//*[contains(normalize-space(text()),'Order Details')]]";

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

	// ---------- Constructor ----------
	public S_OrderStatus_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
		PageFactory.initElements(driver, this);
	}

	// ======================= helpers =======================

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

	private WebElement first_displayed(By by) {
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	private void click_safely(WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			element.click();
		} catch (ElementClickInterceptedException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void js_click(WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", element);
		pause(700);
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

	// every option text of a drop down, the first (placeholder) one included
	public List<String> all_options_of(WebElement dropdown) {
		List<String> texts = new ArrayList<String>();
		for (WebElement o : new Select(dropdown).getOptions()) {
			texts.add(o.getText().trim());
		}
		return texts;
	}

	public String selected(WebElement dropdown) {
		return new Select(dropdown).getFirstSelectedOption().getText().trim();
	}

	private void pause(int millis) {
		WaitUtil.settle(driver, millis);
	}

	// label text with or without a star or colon
	private String label_xpath(String label) {
		return "//label[normalize-space()='" + label + "' or normalize-space()='" + label + " *' or normalize-space()='"
				+ label + ":']";
	}

	private WebElement label_input(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::input[not(@type='hidden')][1]"));
	}

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

	// types a date into a date box; when the box does not keep it, the value is set with JavaScript
	private void set_date(WebElement field, String value) {
		type(field, value);
		field.sendKeys(Keys.TAB);
		String kept = field.getAttribute("value");
		if (kept == null || !kept.equals(value)) {
			((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];"
					+ " arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
					+ " arguments[0].dispatchEvent(new Event('change', {bubbles: true}));"
					+ " arguments[0].dispatchEvent(new Event('blur', {bubbles: true}));", field, value);
		}
	}

	// ======================= top and paging =======================

	public WebElement verify_top_search() {
		return Top_search_btn;
	}

	public WebElement verify_entries() {
		return Entries_info;
	}

	public String entries_text() {
		try {
			return Entries_info.getText().trim();
		} catch (Exception e) {
			return "";
		}
	}

	// the total of "Showing 1 to 10 of 86 entries" (0 when it cannot be read)
	public int entries_total() {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("of\\s+([\\d,]+)\\s+entries").matcher(entries_text());
		return m.find() ? Integer.parseInt(m.group(1).replace(",", "")) : 0;
	}

	public void Click_next() {
		click_safely(Next_btn);
		pause(1000);
	}

	public void Click_previous() {
		click_safely(Previous_btn);
		pause(1000);
	}

	public boolean next_enabled() {
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Next']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	public boolean previous_enabled() {
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Previous']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	public WebElement verify_table_filter() {
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_filter')]//input | //input[@type='search']"
				+ " | //label[normalize-space()='Search:']/input | //*[normalize-space()='Search:']/following::input[1]"));
	}

	public void Filter_table(String text) {
		type(verify_table_filter(), text);
		pause(1500);
	}

	public boolean is_list_page() {
		try {
			return Top_search_btn.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	// ======================= search form =======================

	public WebElement verify_type() {
		return label_select("Employee Type");
	}

	public WebElement verify_employee() {
		return label_input("Employee Name");
	}

	public WebElement verify_from() {
		return label_input("From Date");
	}

	public WebElement verify_to() {
		return label_input("To Date");
	}

	public WebElement verify_customer() {
		return label_input("Customer");
	}

	public WebElement verify_hierarchy() {
		return label_select("Hierarchy Type");
	}

	public WebElement verify_supply_by() {
		return label_input("Supply By");
	}

	public WebElement verify_status() {
		return label_select("Approval Status");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public WebElement verify_clear() {
		return Clear_btn;
	}

	public boolean search_form_open() {
		return !displayed(By.xpath(label_xpath("Employee Type"))).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			click_safely(Top_search_btn);
			pause(800);
		}
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches.
	// An employee needs an employee type: when none is given "Sales Executive" is chosen if the drop down has it.
	public void Search_orders(String type, String employee, String from, String to, String customer,
			String hierarchy, String supply_by, String status) {
		Open_search_form();
		if (!employee.isEmpty() && type.isEmpty() && options_of(verify_type()).contains("Sales Executive")) {
			type = "Sales Executive";
		}
		if (!type.isEmpty()) {
			select_text(verify_type(), type);
			pause(800);
		}
		if (!employee.isEmpty()) {
			search_pick(verify_employee(), employee);
		}
		if (!from.isEmpty()) {
			set_date(verify_from(), from);
		}
		if (!to.isEmpty()) {
			set_date(verify_to(), to);
		}
		if (!customer.isEmpty()) {
			search_pick(verify_customer(), customer);
		}
		if (!hierarchy.isEmpty()) {
			select_text(verify_hierarchy(), hierarchy);
			pause(800);
		}
		if (!supply_by.isEmpty()) {
			search_pick(verify_supply_by(), supply_by);
		}
		if (!status.isEmpty()) {
			select_text(verify_status(), status);
		}
		click_safely(Search_go);
		pause(3000);
	}

	// types text into a search box of the form without picking a suggestion; returns the suggestion texts
	public List<String> Suggestions_for(WebElement field, String text) {
		type(field, text);
		pause(1500);
		List<String> out = new ArrayList<String>();
		for (WebElement s : displayed(By.xpath(SUGGESTIONS))) {
			try {
				String t = s.getText().trim();
				if (!t.isEmpty()) {
					out.add(t);
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return out;
	}

	public void Click_clear() {
		Open_search_form();
		click_safely(Clear_btn);
		pause(2500);
	}

	// ======================= result table =======================

	public List<String> headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : displayed(By.xpath("//table//th"))) {
			out.add(th.getText().trim().toUpperCase().replaceAll("\\s+", " "));
		}
		return out;
	}

	private List<WebElement> result_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() >= 7
						&& !row.getText().toLowerCase().contains("no data")) {
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

	// true when the table shows its "No Data Available" row
	public boolean no_data_shown() {
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr"))) {
			try {
				if (row.isDisplayed() && row.getText().toLowerCase().contains("no data")) {
					return true;
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return false;
	}

	// the cell texts of every result row (line breaks turned into spaces):
	// [order number, order date, route, customer, order amount, delivery date, order status, actions]
	public List<List<String>> result_texts() {
		List<List<String>> out = new ArrayList<List<String>>();
		for (WebElement row : result_rows()) {
			try {
				List<String> cells = new ArrayList<String>();
				for (WebElement td : row.findElements(By.tagName("td"))) {
					cells.add(td.getText().trim().replaceAll("\\s+", " "));
				}
				out.add(cells);
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return out;
	}

	public List<String> row_of(String number) {
		for (List<String> cells : result_texts()) {
			if (cells.get(0).equalsIgnoreCase(number)) {
				return cells;
			}
		}
		return null;
	}

	private WebElement row_by_number(String number) {
		for (WebElement row : result_rows()) {
			if (row.findElements(By.tagName("td")).get(0).getText().trim().equalsIgnoreCase(number)) {
				return row;
			}
		}
		throw new NoSuchElementException("Order not found in the list: " + number);
	}

	// click on a header sorts the column
	public void Click_header(String name) {
		for (WebElement th : displayed(By.xpath("//table//th"))) {
			if (th.getText().trim().toUpperCase().replaceAll("\\s+", " ").equals(name.toUpperCase())) {
				click_safely(th);
				pause(1000);
				return;
			}
		}
		throw new NoSuchElementException("Column header not found: " + name);
	}

	// ======================= row actions =======================

	// the action icons of the last cell of a row
	private List<WebElement> action_icons(WebElement row) {
		List<WebElement> tds = row.findElements(By.tagName("td"));
		return tds.get(tds.size() - 1).findElements(By.xpath(".//a | .//button"
				+ " | .//span[@onclick] | .//i[not(ancestor::a) and not(ancestor::button) and not(ancestor::span[@onclick])]"));
	}

	private String html_of(WebElement e) {
		try {
			return String.valueOf(((JavascriptExecutor) driver).executeScript(
					"return (arguments[0].outerHTML || '') + ' ' + (arguments[0].parentElement ? arguments[0].parentElement.className : '');", e)).toLowerCase();
		} catch (StaleElementReferenceException ex) {
			return "";
		}
	}

	private String kind_of(WebElement icon) {
		String h = html_of(icon);
		if (h.contains("eye") || h.contains("view")) {
			return "view";
		}
		if (h.contains("pencil") || h.contains("edit")) {
			return "edit";
		}
		if (h.contains("trash") || h.contains("delete") || h.contains("remove")) {
			return "delete";
		}
		if (h.contains("print")) {
			return "print";
		}
		return "other";
	}

	// the kinds of the action icons of an order: view, edit, delete, print (or other)
	public List<String> actions_of(String number) {
		List<String> kinds = new ArrayList<String>();
		for (WebElement icon : action_icons(row_by_number(number))) {
			kinds.add(kind_of(icon));
		}
		return kinds;
	}

	// the HTML of the action icons of an order (for the report when the icons cannot be told apart)
	public String actions_html(String number) {
		StringBuilder sb = new StringBuilder();
		for (WebElement icon : action_icons(row_by_number(number))) {
			sb.append(html_of(icon).replaceAll("\\s+", " ")).append(" || ");
		}
		return sb.toString();
	}

	// the colour class / style of the print icon (it is red or green in the list)
	public String print_icon_state(String number) {
		for (WebElement icon : action_icons(row_by_number(number))) {
			if (kind_of(icon).equals("print")) {
				return String.valueOf(((JavascriptExecutor) driver).executeScript(
						"var e = arguments[0]; var t = e.querySelector('i') || e;"
						+ " return (t.className || '') + ' | ' + window.getComputedStyle(t).color;", icon));
			}
		}
		return "";
	}

	private void click_action(String number, String kind) {
		for (WebElement icon : action_icons(row_by_number(number))) {
			if (kind_of(icon).equals(kind)) {
				js_click(icon);
				pause(1500);
				return;
			}
		}
		throw new NoSuchElementException("The order " + number + " has no " + kind + " action");
	}

	public void Click_view(String number) {
		click_action(number, "view");
	}

	public void Click_edit(String number) {
		click_action(number, "edit");
	}

	public void Click_delete(String number) {
		click_action(number, "delete");
	}

	// ======================= Order Details pop-up =======================

	public boolean modal_open() {
		return !displayed(By.xpath(MODAL)).isEmpty();
	}

	private WebElement modal() {
		return first_displayed(By.xpath(MODAL));
	}

	public String modal_text() {
		try {
			return modal().getText().trim().replaceAll("\\s+", " ");
		} catch (Exception e) {
			return "";
		}
	}

	// the customer name shown in the pop-up (the read only box)
	public String modal_customer() {
		List<WebElement> boxes = modal().findElements(By.xpath(".//input[@readonly or @disabled][not(@type='hidden')]"));
		for (WebElement b : boxes) {
			String v = b.getAttribute("value");
			if (v != null && !v.trim().isEmpty() && b.isDisplayed()) {
				return v.trim();
			}
		}
		return "";
	}

	public boolean modal_has_button(String text) {
		for (WebElement b : modal().findElements(By.xpath(".//button | .//a[contains(@class,'btn')]"))) {
			try {
				if (b.isDisplayed() && b.getText().trim().toLowerCase().contains(text.toLowerCase())) {
					return true;
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return false;
	}

	public void Click_modal_button(String text) {
		for (WebElement b : modal().findElements(By.xpath(".//button | .//a[contains(@class,'btn')]"))) {
			try {
				if (b.isDisplayed() && b.getText().trim().toLowerCase().contains(text.toLowerCase())) {
					click_safely(b);
					pause(1500);
					return;
				}
			} catch (StaleElementReferenceException e) {
				// try the next one
			}
		}
		throw new NoSuchElementException("The pop-up has no button: " + text);
	}

	// closes the pop-up with its x (or the Close button, or Escape)
	public void Close_modal() {
		if (!modal_open()) {
			return;
		}
		List<WebElement> x = modal().findElements(By.xpath(".//button[contains(@class,'close') or contains(@class,'btn-close')"
				+ " or normalize-space()='×' or @data-bs-dismiss='modal' or @data-dismiss='modal']"));
		for (WebElement b : x) {
			if (b.isDisplayed()) {
				click_safely(b);
				pause(1200);
				return;
			}
		}
		driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
		pause(1200);
		if (modal_open()) {
			((JavascriptExecutor) driver).executeScript(
					"document.querySelectorAll('.modal.show').forEach(function(m){ m.classList.remove('show'); m.style.display='none'; });"
					+ " document.querySelectorAll('.modal-backdrop').forEach(function(b){ b.remove(); });"
					+ " document.body.classList.remove('modal-open'); document.body.style.overflow='';");
			pause(500);
		}
	}

	// true when the pop-up can be edited (its item lines have input boxes)
	public boolean modal_editable() {
		return !modal().findElements(By.xpath(".//table//tbody//input[not(@type='hidden')]")).isEmpty();
	}

	private List<WebElement> line_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : modal().findElements(By.xpath(".//table//tbody/tr[td]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() >= 7
						&& !row.getText().toLowerCase().contains("no data")) {
					rows.add(row);
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return rows;
	}

	public int line_count() {
		return line_rows().size();
	}

	private String cell_value(WebElement td) {
		List<WebElement> inputs = td.findElements(By.xpath(".//input[not(@type='hidden')]"));
		if (!inputs.isEmpty()) {
			String v = inputs.get(0).getAttribute("value");
			return v == null ? "" : v.trim();
		}
		List<WebElement> selects = td.findElements(By.tagName("select"));
		if (!selects.isEmpty()) {
			return new Select(selects.get(0)).getFirstSelectedOption().getText().trim();
		}
		return td.getText().trim().replaceAll("\\s+", " ");
	}

	// the value of every cell of every item line (inputs and drop downs give their value)
	public List<List<String>> line_texts() {
		List<List<String>> out = new ArrayList<List<String>>();
		for (WebElement row : line_rows()) {
			List<String> cells = new ArrayList<String>();
			for (WebElement td : row.findElements(By.tagName("td"))) {
				cells.add(cell_value(td));
			}
			out.add(cells);
		}
		return out;
	}

	// the "net amount" of one line (index from 0), the 8th cell
	public double line_net(int index) {
		return to_number(line_texts().get(index).get(7));
	}

	public double lines_total() {
		double sum = 0;
		for (List<String> cells : line_texts()) {
			sum += to_number(cells.get(7));
		}
		return sum;
	}

	public static double to_number(String text) {
		String t = text.replaceAll("[^0-9.\\-]", "");
		if (t.isEmpty() || t.equals("-") || t.equals(".")) {
			return 0;
		}
		return Double.parseDouble(t);
	}

	private WebElement line_cell_box(int index, int cell) {
		WebElement td = line_rows().get(index).findElements(By.tagName("td")).get(cell);
		List<WebElement> inputs = td.findElements(By.xpath(".//input[not(@type='hidden')]"));
		if (inputs.isEmpty()) {
			throw new NoSuchElementException("The line " + (index + 1) + " has no input in column " + (cell + 1));
		}
		return inputs.get(0);
	}

	// types a value into the unit rate (cell 5) of a line and leaves the box so the amount is calculated
	public void Set_line_rate(int index, String value) {
		WebElement box = line_cell_box(index, 4);
		type(box, value);
		box.sendKeys(Keys.TAB);
		pause(800);
	}

	// types a value into the ordered quantity (cell 7) of a line and leaves the box
	public void Set_line_qty(int index, String value) {
		WebElement box = line_cell_box(index, 6);
		type(box, value);
		box.sendKeys(Keys.TAB);
		pause(800);
	}

	private WebElement line_discount_box(int index) {
		WebElement td = line_rows().get(index).findElements(By.tagName("td")).get(5);
		List<WebElement> selects = td.findElements(By.tagName("select"));
		if (selects.isEmpty()) {
			throw new NoSuchElementException("The line " + (index + 1) + " has no discount drop down");
		}
		return selects.get(0);
	}

	public List<String> line_discount_options(int index) {
		return all_options_of(line_discount_box(index));
	}

	public void Select_line_discount(int index, String text) {
		select_text(line_discount_box(index), text);
		pause(800);
	}

	// true when the line has a delete (bin) button that is enabled
	public boolean line_delete_enabled(int index) {
		WebElement td = line_rows().get(index).findElements(By.tagName("td")).get(8);
		List<WebElement> icons = td.findElements(By.xpath(".//a | .//button | .//i"));
		if (icons.isEmpty()) {
			return false;
		}
		for (WebElement i : icons) {
			String h = html_of(i);
			if (h.contains("disabled") || h.contains("opacity")) {
				return false;
			}
		}
		return true;
	}

	public void Click_line_delete(int index) {
		WebElement td = line_rows().get(index).findElements(By.tagName("td")).get(8);
		js_click(td.findElement(By.xpath(".//a | .//button | .//i")));
		pause(800);
	}

	// ---- add item row ----

	// true when the add item row (Item search, Attribute Name, Attribute Value, Add) is displayed
	public boolean add_row_open() {
		return !displayed(By.xpath(MODAL + "//input[@placeholder='Search...' or contains(@placeholder,'earch')]"
				+ "[not(contains(@placeholder,'ustomer'))]")).isEmpty();
	}

	// the "+" that opens the add item row
	public void Click_plus() {
		if (add_row_open()) {
			return;
		}
		List<WebElement> plus = modal().findElements(By.xpath(".//*[contains(@class,'plus')]"));
		for (WebElement p : plus) {
			if (p.isDisplayed()) {
				js_click(p);
				pause(800);
				return;
			}
		}
		throw new NoSuchElementException("The + of the pop-up was not found");
	}

	public boolean plus_available() {
		for (WebElement p : modal().findElements(By.xpath(".//*[contains(@class,'plus')]"))) {
			if (p.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private WebElement item_box() {
		return first_displayed(By.xpath(MODAL + "//input[contains(@placeholder,'earch')][not(contains(@placeholder,'ustomer'))]"));
	}

	public void Search_item(String text) {
		Click_plus();
		search_pick(item_box(), text);
	}

	public List<String> Item_suggestions(String text) {
		Click_plus();
		List<String> found = Suggestions_for(item_box(), text);
		return found.isEmpty() ? modal_text_matches(text) : found;
	}

	public String item_text() {
		return item_box().getAttribute("value");
	}

	private WebElement attribute_name_box() {
		return first_displayed(By.xpath(MODAL + label_xpath("Attribute Name") + "/following::select[1]"));
	}

	private WebElement attribute_value_box() {
		return first_displayed(By.xpath(MODAL + label_xpath("Attribute Value") + "/following::select[1]"));
	}

	public List<String> attribute_names() {
		return options_of(attribute_name_box());
	}

	public void Select_attribute(String name) {
		select_text(attribute_name_box(), name);
		pause(1000);
	}

	public List<String> attribute_values() {
		return options_of(attribute_value_box());
	}

	public void Select_attribute_value(String value) {
		select_text(attribute_value_box(), value);
		pause(500);
	}

	// clicks Add; returns the text of the browser alert the page shows (and accepts it), or "" when there is none
	public String Click_add_item() {
		List<WebElement> add = displayed(By.xpath(MODAL + "//button[normalize-space()='Add']"));
		if (add.isEmpty()) {
			throw new NoSuchElementException("The Add button of the item row was not found");
		}
		try {
			add.get(0).click();
		} catch (ElementClickInterceptedException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", add.get(0));
		}
		pause(1500);
		try {
			Alert a = driver.switchTo().alert();
			String text = a.getText();
			a.accept();
			pause(500);
			return text;
		} catch (NoAlertPresentException e) {
			return "";
		}
	}

	// the visible texts below the modal that contain 'text' and are not table cells or boxes (a suggestion list
	// that the usual locators do not know)
	private List<String> modal_text_matches(String text) {
		List<String> out = new ArrayList<String>();
		String lower = text.toLowerCase();
		for (WebElement e : displayed(By.xpath("//*[not(*)][not(ancestor::table)][not(self::input or self::textarea"
				+ " or self::option or self::select or self::script or self::style or self::button or self::label)]"
				+ "[contains(translate(normalize-space(text()),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'" + lower + "')]"))) {
			try {
				String t = e.getText().trim();
				if (!t.isEmpty() && !out.contains(t)) {
					out.add(t);
				}
			} catch (StaleElementReferenceException ex) {
				// skip
			}
		}
		return out;
	}

	// ---- customer change ----

	public void Click_change_customer() {
		Click_modal_button("Change");
	}

	// the NEW customer search box that Change opens (below the read only customer name box): the last displayed
	// box with a customer placeholder that is not read only or disabled
	private WebElement customer_search_box() {
		wait.until(d -> customer_search_open());
		List<WebElement> boxes = displayed(By.xpath(MODAL + "//input[contains(@placeholder,'ustomer')"
				+ " and not(@readonly) and not(@disabled)]"));
		return boxes.get(boxes.size() - 1);
	}

	public boolean customer_search_open() {
		return !displayed(By.xpath(MODAL + "//input[contains(@placeholder,'ustomer') and not(@readonly) and not(@disabled)]")).isEmpty();
	}

	public List<String> Customer_suggestions(String text) {
		List<String> found = Suggestions_for(customer_search_box(), text);
		return found.isEmpty() ? modal_text_matches(text) : found;
	}

	// ---- remark ----

	private WebElement remark_box() {
		return first_displayed(By.xpath(MODAL + "//textarea"));
	}

	public String modal_remark() {
		return remark_box().getAttribute("value");
	}

	public boolean remark_editable() {
		WebElement r = remark_box();
		return r.isEnabled() && r.getAttribute("readonly") == null;
	}

	public void Set_remark(String text) {
		WebElement box = remark_box();
		box.click();
		type(box, text);
		pause(300);
	}

	// ======================= delete / approve dialogs =======================

	private String dialog_text() {
		List<WebElement> d = displayed(By.xpath("//div[contains(@class,'swal2-popup')]"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')][not(.//*[contains(text(),'Order Details')])]"));
		return d.isEmpty() ? "" : d.get(0).getText().trim().replaceAll("\\s+", " ");
	}

	// true when a dialog (browser alert, sweet alert or confirm pop-up) is open
	public boolean dialog_open() {
		try {
			driver.switchTo().alert();
			return true;
		} catch (NoAlertPresentException e) {
			return !displayed(By.xpath("//div[contains(@class,'swal2-popup')]"
					+ " | //div[contains(@class,'modal') and contains(@class,'show')][not(.//*[contains(text(),'Order Details')])]")).isEmpty();
		}
	}

	// the text of the open dialog (the browser alert text when it is an alert)
	public String Dialog_text() {
		try {
			return driver.switchTo().alert().getText();
		} catch (NoAlertPresentException e) {
			return dialog_text();
		}
	}

	public List<String> dialog_buttons() {
		List<String> out = new ArrayList<String>();
		try {
			driver.switchTo().alert();
			out.add("OK");
			out.add("Cancel");
			return out;
		} catch (NoAlertPresentException e) {
			// a pop-up: read its buttons
		}
		for (WebElement b : displayed(By.xpath("//div[contains(@class,'swal2-popup')]//button"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')][not(.//*[contains(text(),'Order Details')])]//button"))) {
			String t = b.getText().trim();
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	// clicks the button of the open dialog whose text contains 'text' (Yes, Delete, OK, Cancel, No ...);
	// for a browser alert: Cancel / No / Close dismiss it, anything else accepts it
	public void Click_dialog_button(String text) {
		try {
			Alert a = driver.switchTo().alert();
			if (text.equalsIgnoreCase("Cancel") || text.equalsIgnoreCase("Close") || text.equalsIgnoreCase("No")) {
				a.dismiss();
			} else {
				a.accept();
			}
			pause(1500);
			return;
		} catch (NoAlertPresentException e) {
			// a pop-up: look for the button
		}
		for (WebElement b : displayed(By.xpath("//div[contains(@class,'swal2-popup')]//button"
				+ " | //div[contains(@class,'modal') and contains(@class,'show')][not(.//*[contains(text(),'Order Details')])]//button"))) {
			if (b.getText().trim().toLowerCase().contains(text.toLowerCase())) {
				click_safely(b);
				pause(2000);
				return;
			}
		}
		throw new NoSuchElementException("The dialog has no button: " + text);
	}

	// accepts every message that follows an action (browser alerts and sweet alerts); returns their texts
	public List<String> Accept_messages() {
		List<String> texts = new ArrayList<String>();
		for (int round = 0; round < 3; round++) {
			String text = "";
			try {
				Alert a = driver.switchTo().alert();
				text = a.getText();
				a.accept();
			} catch (NoAlertPresentException e) {
				List<WebElement> ok = displayed(By.xpath("//button[contains(@class,'swal2-confirm')]"));
				if (!ok.isEmpty()) {
					text = dialog_text();
					click_safely(ok.get(0));
				}
			}
			if (text.isEmpty()) {
				break;
			}
			texts.add(text);
			pause(800);
		}
		return texts;
	}

	// ======================= print =======================

	// clicks the print icon of an order; the print page opens in a new tab (the tab is switched to).
	// Returns the handle of the list tab so Close_print can come back to it.
	public String Click_print(String number) {
		String list_tab = driver.getWindowHandle();
		Set<String> before = driver.getWindowHandles();
		click_action(number, "print");
		long end = System.currentTimeMillis() + 10000;
		while (System.currentTimeMillis() < end && driver.getWindowHandles().size() <= before.size()
				&& !driver.getCurrentUrl().toLowerCase().contains("printorder")) {
			pause(500);
		}
		for (String h : driver.getWindowHandles()) {
			if (!before.contains(h)) {
				driver.switchTo().window(h);
				break;
			}
		}
		pause(1500);
		return list_tab;
	}

	public String print_url() {
		return driver.getCurrentUrl();
	}

	public String print_text() {
		return driver.findElement(By.tagName("body")).getText().trim().replaceAll("\\s+", " ");
	}

	public boolean print_has_button(String text) {
		return !displayed(By.xpath("//*[self::button or self::a][contains(normalize-space(),'" + text + "')]")).isEmpty();
	}

	// closes the print tab and returns to the list tab (or goes back when the print page opened in the same tab)
	public void Close_print(String list_tab) {
		if (driver.getWindowHandles().size() > 1 && !driver.getWindowHandle().equals(list_tab)) {
			driver.close();
			driver.switchTo().window(list_tab);
		} else if (driver.getCurrentUrl().toLowerCase().contains("printorder")) {
			driver.navigate().back();
		}
		pause(1000);
	}

	// ======================= messages =======================

	public List<String> validation_messages() {
		List<String> out = new ArrayList<String>();
		for (WebElement e : displayed(By.xpath("//*[starts-with(normalize-space(text()),'Please')]"))) {
			try {
				String t = e.getText().trim();
				if (!t.isEmpty() && !out.contains(t)) {
					out.add(t);
				}
			} catch (StaleElementReferenceException ex) {
				// the message is gone: skip it
			}
		}
		return out;
	}

	public String notice() {
		try {
			List<WebElement> found = displayed(By.xpath("//div[contains(@class,'toast') or contains(@class,'alert')"
					+ " or contains(@class,'notyf') or contains(@class,'swal2-popup')]"));
			return found.isEmpty() ? "" : found.get(0).getText().trim();
		} catch (StaleElementReferenceException e) {
			return "";
		}
	}
}
