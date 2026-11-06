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
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * Page class of Field Management > OrderBooking (a three step wizard, logged in as the web user).
 *
 * Step 1 "Customer Details": Route (drop down), Customer (search box, at least 3 letters, a route first) and the
 *   details card (Order No, Customer Code, GST Number, Phone, Current Advance, Credit Limit, Address); Next.
 * Step 2 "Item Selection": a table [no, item (search box), quantity, rate (read only), amount (read only),
 *   scheme dot, remove]; Add Row, Add Multiple (the "Select Item" pop-up: "Beginning with" box, items with their
 *   rate; a click on an item opens "Set Quantity"), Back, Next. The amount includes the tax of the item.
 * Step 3 "Payment Details": Amount (read only), Mode of Payment, Delivery Date, Remark, "Mark as Important Order",
 *   Back and the button that places the order.
 * Typing: an empty field is typed into directly, without clear().
 */
public class T_OrderBooking_Cls {

	WebDriver driver;
	WebDriverWait wait;

	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

	static final String ITEM_ROWS = "//table//tbody/tr[.//input][not(ancestor::div[contains(@class,'modal')])]";
	static final String MODAL_SHOWN = "//div[contains(@class,'modal') and contains(@class,'show')]";

	// ---------- Constructor ----------
	public T_OrderBooking_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
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

	public static double to_number(String text) {
		String t = text == null ? "" : text.replaceAll("[^0-9.\\-]", "");
		if (t.isEmpty() || t.equals("-") || t.equals(".")) {
			return 0;
		}
		try {
			return Double.parseDouble(t);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// true when the box cannot be typed into (read only, disabled or no pointer events)
	public boolean is_locked(WebElement e) {
		return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript(
				"var e = arguments[0]; return !!(e.readOnly || e.disabled || e.hasAttribute('readonly')"
				+ " || e.hasAttribute('disabled') || window.getComputedStyle(e).pointerEvents === 'none');", e));
	}

	private String label_xpath(String label) {
		return "//label[normalize-space()='" + label + "' or normalize-space()='" + label + " *' or normalize-space()='"
				+ label + ":']";
	}

	// types text into a search box; returns the suggestions (texts) that are displayed
	private List<String> suggestions_for(WebElement field, String text) {
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

	// clicks the suggestion that contains the text (or the first one); when the page shows no list (a browser list
	// is used) the first entry is taken with the arrow key and Enter
	private void pick_suggestion(WebElement field, String text) {
		List<WebElement> found = displayed(By.xpath(SUGGESTIONS));
		for (WebElement s : found) {
			try {
				if (s.getText().trim().toLowerCase().contains(text.trim().toLowerCase())) {
					click_safely(s);
					pause(1000);
					return;
				}
			} catch (StaleElementReferenceException e) {
				// try the next one
			}
		}
		if (!found.isEmpty()) {
			click_safely(found.get(0));
		} else {
			field.sendKeys(Keys.ARROW_DOWN, Keys.ENTER);
		}
		pause(1000);
	}

	// ======================= tabs and navigation =======================

	// the step that is displayed, found from its content: Customer Details, Item Selection, Payment Details
	public String current_tab() {
		if (!displayed(By.xpath(label_xpath("Route") + "/following::select[1]")).isEmpty()) {
			return "Customer Details";
		}
		if (!displayed(By.xpath("//button[normalize-space()='Add Row']")).isEmpty()) {
			return "Item Selection";
		}
		if (!displayed(By.xpath(label_xpath("Mode of Payment"))).isEmpty()) {
			return "Payment Details";
		}
		return "";
	}

	public boolean is_booking_page() {
		return !current_tab().isEmpty();
	}

	public void Click_tab(String name) {
		click_safely(first_displayed(By.xpath("//*[self::a or self::button or self::li][normalize-space()='" + name + "']")));
		pause(1000);
	}

	public boolean tab_exists(String name) {
		return !displayed(By.xpath("//*[self::a or self::button or self::li][normalize-space()='" + name + "']")).isEmpty();
	}

	public WebElement next_button() {
		return first_displayed(By.xpath("//button[normalize-space()='Next'] | //a[normalize-space()='Next']"));
	}

	public WebElement back_button() {
		return first_displayed(By.xpath("//button[normalize-space()='Back'] | //a[normalize-space()='Back']"));
	}

	public void Click_next() {
		click_safely(next_button());
		pause(1500);
	}

	public void Click_back() {
		click_safely(back_button());
		pause(1200);
	}

	// ======================= step 1: customer =======================

	public WebElement verify_route() {
		return first_displayed(By.xpath(label_xpath("Route") + "/following::select[1]"));
	}

	public List<String> route_options() {
		List<String> out = all_options_of(verify_route());
		if (!out.isEmpty()) {
			out.remove(0);
		}
		return out;
	}

	public void Select_route(String text) {
		select_text(verify_route(), text);
		pause(1500);
	}

	public String route_selected() {
		return selected(verify_route());
	}

	public WebElement verify_customer() {
		return first_displayed(By.xpath("//input[contains(@placeholder,'ustomer')]"));
	}

	public String customer_text() {
		return String.valueOf(verify_customer().getAttribute("value"));
	}

	// types the text and returns the suggestions (nothing is picked)
	public List<String> Customer_suggestions(String text) {
		return suggestions_for(verify_customer(), text);
	}

	// types the text and picks the suggestion that contains it
	public void Search_customer(String text) {
		WebElement box = verify_customer();
		type(box, text);
		pause(1500);
		pick_suggestion(box, text);
		pause(1500);
	}

	// the value of a row of the details card: Order No, Customer Code, GST Number, Phone, Current Advance,
	// Credit Limit, Address
	public String info_value(String label) {
		List<WebElement> found = displayed(By.xpath("(//*[normalize-space(text())='" + label
				+ "'])[1]/following::*[normalize-space()!=''][1]"));
		return found.isEmpty() ? "" : found.get(0).getText().trim();
	}

	public String order_no() {
		return info_value("Order No");
	}

	public String hint_text() {
		List<WebElement> found = displayed(By.xpath("//*[contains(text(),'Select a route first')]"));
		return found.isEmpty() ? "" : found.get(0).getText().trim();
	}

	// ======================= step 2: items =======================

	private List<WebElement> item_rows() {
		return displayed(By.xpath(ITEM_ROWS));
	}

	public int row_count() {
		return item_rows().size();
	}

	private WebElement row_cell(int index, int cell) {
		return item_rows().get(index).findElements(By.tagName("td")).get(cell);
	}

	private WebElement row_input(int index, int cell) {
		List<WebElement> inputs = row_cell(index, cell).findElements(By.xpath(".//input[not(@type='hidden')]"));
		if (inputs.isEmpty()) {
			throw new NoSuchElementException("Row " + (index + 1) + " has no box in column " + (cell + 1));
		}
		return inputs.get(0);
	}

	public WebElement item_box(int index) {
		return row_input(index, 1);
	}

	public WebElement qty_box(int index) {
		return row_input(index, 2);
	}

	public WebElement rate_box(int index) {
		return row_input(index, 3);
	}

	public WebElement amount_box(int index) {
		return row_input(index, 4);
	}

	private String value_of(WebElement td) {
		List<WebElement> inputs = td.findElements(By.xpath(".//input[not(@type='hidden')]"));
		if (!inputs.isEmpty()) {
			String v = inputs.get(0).getAttribute("value");
			return v == null ? "" : v.trim();
		}
		return td.getText().trim();
	}

	public String item_text(int index) {
		return value_of(row_cell(index, 1));
	}

	public String qty_text(int index) {
		return value_of(row_cell(index, 2));
	}

	public String rate_text(int index) {
		return value_of(row_cell(index, 3));
	}

	public String amount_text(int index) {
		return value_of(row_cell(index, 4));
	}

	public double qty(int index) {
		return to_number(qty_text(index));
	}

	public double rate(int index) {
		return to_number(rate_text(index));
	}

	public double amount(int index) {
		return to_number(amount_text(index));
	}

	public double items_total() {
		double sum = 0;
		for (int i = 0; i < row_count(); i++) {
			sum += amount(i);
		}
		return sum;
	}

	// the number shown in the first column of every row
	public List<String> row_numbers() {
		List<String> out = new ArrayList<String>();
		for (int i = 0; i < row_count(); i++) {
			out.add(row_cell(i, 0).getText().trim());
		}
		return out;
	}

	// the item names of the rows (empty rows give "")
	public List<String> row_items() {
		List<String> out = new ArrayList<String>();
		for (int i = 0; i < row_count(); i++) {
			out.add(item_text(i));
		}
		return out;
	}

	// types in the item box of a row and returns the suggestions (nothing is picked)
	public List<String> Item_suggestions(int index, String text) {
		return suggestions_for(item_box(index), text);
	}

	// types the item name into a row and picks it; the rate must then be filled
	public void Pick_item(int index, String text) {
		WebElement box = item_box(index);
		type(box, text);
		pause(1500);
		pick_suggestion(box, text);
		box.sendKeys(Keys.TAB);
		pause(1500);
	}

	// types the quantity of a row and leaves the box so the amount is calculated
	public void Set_qty(int index, String value) {
		WebElement box = qty_box(index);
		type(box, value);
		box.sendKeys(Keys.TAB);
		pause(800);
	}

	public void Click_add_row() {
		click_safely(first_displayed(By.xpath("//button[normalize-space()='Add Row']")));
		pause(800);
	}

	public void Remove_row(int index) {
		WebElement td = row_cell(index, 6);
		js_click(td.findElement(By.xpath(".//button | .//a | .//i | .//span")));
		pause(800);
	}

	// the colour of the scheme dot of a row (grey = no scheme, green = scheme available)
	public String scheme_state(int index) {
		WebElement td = row_cell(index, 5);
		List<WebElement> dots = td.findElements(By.xpath(".//*"));
		WebElement dot = dots.isEmpty() ? td : dots.get(0);
		return String.valueOf(((JavascriptExecutor) driver).executeScript(
				"var e = arguments[0]; var s = window.getComputedStyle(e);"
				+ " return (e.className || '') + ' | ' + s.backgroundColor + ' | ' + s.color;", dot));
	}

	public void Click_scheme(int index) {
		WebElement td = row_cell(index, 5);
		List<WebElement> dots = td.findElements(By.xpath(".//*"));
		js_click(dots.isEmpty() ? td : dots.get(0));
		pause(800);
	}

	// ---- Add Multiple ----

	public void Click_add_multiple() {
		click_safely(first_displayed(By.xpath("//button[normalize-space()='Add Multiple']")));
		pause(1500);
	}

	public boolean multiple_open() {
		return !displayed(By.xpath(MODAL_SHOWN + "[.//*[normalize-space(text())='Select Item']]")).isEmpty();
	}

	private WebElement multiple_search_box() {
		return first_displayed(By.xpath(MODAL_SHOWN + "//input[not(@type='hidden') and not(@type='number')]"));
	}

	public List<String> Multiple_search(String text) {
		type(multiple_search_box(), text);
		pause(1500);
		return multiple_items();
	}

	// "name | rate" of every item listed in the Select Item pop-up
	public List<String> multiple_items() {
		List<String> out = new ArrayList<String>();
		for (WebElement row : displayed(By.xpath(MODAL_SHOWN + "//table//tbody/tr[td]"))) {
			try {
				List<WebElement> tds = row.findElements(By.tagName("td"));
				if (tds.size() >= 2) {
					out.add(tds.get(0).getText().trim() + " | " + tds.get(1).getText().trim());
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return out;
	}

	public boolean quantity_dialog_open() {
		return !displayed(By.xpath(MODAL_SHOWN + "//button[normalize-space()='Set Quantity']")).isEmpty();
	}

	public String dialog_qty_text() {
		List<WebElement> boxes = displayed(By.xpath(MODAL_SHOWN + "//input[@type='number']"));
		return boxes.isEmpty() ? "" : String.valueOf(boxes.get(boxes.size() - 1).getAttribute("value"));
	}

	// clicks the listed item whose name equals 'name' (the list is searched first); the Set Quantity dialog opens
	public void Click_multiple_item(String name) {
		List<String> found = Multiple_search(name);
		if (found.isEmpty() && name.length() > 6) {
			found = Multiple_search(name.substring(0, 6));
		}
		for (WebElement row : displayed(By.xpath(MODAL_SHOWN + "//table//tbody/tr[td]"))) {
			try {
				String first = row.findElements(By.tagName("td")).get(0).getText().trim();
				if (first.equalsIgnoreCase(name.trim())) {
					js_click(row);
					pause(1000);
					return;
				}
			} catch (StaleElementReferenceException e) {
				// try the next one
			}
		}
		throw new NoSuchElementException("The item is not listed in Select Item: " + name + " (found " + found + ")");
	}

	public void Set_dialog_qty(String value) {
		List<WebElement> boxes = displayed(By.xpath(MODAL_SHOWN + "//input[@type='number']"));
		if (boxes.isEmpty()) {
			throw new NoSuchElementException("The Set Quantity box was not found");
		}
		type(boxes.get(boxes.size() - 1), value);
	}

	public void Click_set_quantity() {
		click_safely(first_displayed(By.xpath(MODAL_SHOWN + "//button[normalize-space()='Set Quantity']")));
		pause(1500);
	}

	// picks an item in Select Item and gives its quantity (the pop-up stays open for the next item)
	public void Pick_multiple(String name, String qty) {
		Click_multiple_item(name);
		Set_dialog_qty(qty);
		Click_set_quantity();
	}

	// closes the Select Item pop-up with its x
	public void Close_multiple() {
		for (WebElement b : displayed(By.xpath(MODAL_SHOWN + "//button[contains(@class,'close') or contains(@class,'btn-close')"
				+ " or normalize-space()='×' or @data-bs-dismiss='modal' or @data-dismiss='modal']"))) {
			try {
				click_safely(b);
				pause(800);
			} catch (StaleElementReferenceException e) {
				// closed already
			}
		}
		if (!displayed(By.xpath(MODAL_SHOWN)).isEmpty()) {
			((JavascriptExecutor) driver).executeScript(
					"document.querySelectorAll('.modal.show').forEach(function(m){ m.classList.remove('show'); m.style.display='none'; });"
					+ " document.querySelectorAll('.modal-backdrop').forEach(function(b){ b.remove(); });"
					+ " document.body.classList.remove('modal-open'); document.body.style.overflow='';");
			pause(500);
		}
	}

	// ======================= step 3: payment =======================

	public WebElement verify_amount() {
		return first_displayed(By.xpath(label_xpath("Amount") + "/following::input[not(@type='hidden')][1]"));
	}

	public double payment_amount() {
		return to_number(verify_amount().getAttribute("value"));
	}

	public WebElement verify_mode() {
		return first_displayed(By.xpath(label_xpath("Mode of Payment") + "/following::select[1]"));
	}

	public List<String> mode_options() {
		return all_options_of(verify_mode());
	}

	public void Select_mode(String text) {
		select_text(verify_mode(), text);
		pause(1000);
	}

	public String mode_selected() {
		return selected(verify_mode());
	}

	public WebElement verify_delivery() {
		return first_displayed(By.xpath(label_xpath("Delivery Date") + "/following::input[not(@type='hidden')][1]"));
	}

	public String delivery_text() {
		return String.valueOf(verify_delivery().getAttribute("value"));
	}

	public void Set_delivery(String value) {
		WebElement box = verify_delivery();
		type(box, value);
		box.sendKeys(Keys.TAB);
		String kept = box.getAttribute("value");
		if (kept == null || !kept.equals(value)) {
			((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];"
					+ " arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
					+ " arguments[0].dispatchEvent(new Event('change', {bubbles: true}));"
					+ " arguments[0].dispatchEvent(new Event('blur', {bubbles: true}));", box, value);
		}
	}

	public WebElement verify_remark() {
		return first_displayed(By.xpath(label_xpath("Remark") + "/following::textarea[1]"));
	}

	public void Set_remark(String text) {
		WebElement box = verify_remark();
		box.click();
		type(box, text);
	}

	public String remark_text() {
		return String.valueOf(verify_remark().getAttribute("value"));
	}

	// the "Mark as Important Order" tick box, found by its text with JavaScript
	public WebElement important_box() {
		Object found = ((JavascriptExecutor) driver).executeScript(
				"var all = document.querySelectorAll('label, span, div');"
				+ " for (var i = 0; i < all.length; i++) {"
				+ "   var t = (all[i].textContent || '').replace(/\\s+/g, ' ').trim();"
				+ "   if (t === 'Mark as Important Order') {"
				+ "     if (all[i].htmlFor) { var x = document.getElementById(all[i].htmlFor); if (x) { return x; } }"
				+ "     var own = all[i].querySelector(\"input[type='checkbox']\"); if (own) { return own; }"
				+ "     var p = all[i].previousElementSibling; if (p && p.type === 'checkbox') { return p; }"
				+ "     if (all[i].parentElement) { var q = all[i].parentElement.querySelector(\"input[type='checkbox']\"); if (q) { return q; } } } }"
				+ " return null;");
		if (found == null) {
			throw new NoSuchElementException("The Mark as Important Order tick box was not found");
		}
		return (WebElement) found;
	}

	public void Set_important(boolean on) {
		WebElement box = important_box();
		if (box.isSelected() != on) {
			js_click(box);
		}
	}

	// the visible inputs of the payment form that are not Amount, Delivery Date, Remark or the tick box (they
	// appear for some payment modes): "label/placeholder=value"
	public List<String> payment_extra_fields() {
		List<String> out = new ArrayList<String>();
		for (WebElement in : displayed(By.xpath(label_xpath("Mode of Payment") + "/following::*[self::input or self::select or self::textarea]"
				+ "[not(@type='hidden') and not(@type='checkbox')]"))) {
			try {
				String id = String.valueOf(in.getAttribute("id")).toLowerCase();
				String ph = String.valueOf(in.getAttribute("placeholder"));
				String lab = String.valueOf(((JavascriptExecutor) driver).executeScript(
						"var e = arguments[0]; var l = e.id ? document.querySelector(\"label[for='\" + e.id + \"']\") : null;"
						+ " if (!l) { var p = e.previousElementSibling; if (p && p.tagName === 'LABEL') { l = p; } }"
						+ " if (!l && e.parentElement) { l = e.parentElement.querySelector('label'); }"
						+ " return l ? l.textContent.trim() : '';", in));
				String key = (lab + " " + ph + " " + id).toLowerCase();
				if (key.contains("delivery") || key.contains("remark") || key.contains("mode of payment")
						|| key.contains("important") || key.trim().equals("amount") || key.startsWith("amount")
						|| in.getTagName().equals("textarea") || (in.getTagName().equals("select") && key.contains("mode"))) {
					continue;
				}
				out.add(lab + "/" + ph + "=" + in.getAttribute("value"));
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return out;
	}

	// fills every empty extra input of the payment form (reference, cheque number ...) with test values
	public void Fill_extra_fields(String suffix) {
		for (WebElement in : displayed(By.xpath(label_xpath("Mode of Payment") + "/following::input[not(@type='hidden') and not(@type='checkbox')]"))) {
			try {
				String key = (String.valueOf(in.getAttribute("id")) + " " + in.getAttribute("placeholder") + " " + in.getAttribute("name")).toLowerCase();
				if (key.contains("delivery") || key.contains("amount") || is_locked(in)) {
					continue;
				}
				String v = in.getAttribute("value");
				if (v != null && !v.isEmpty()) {
					continue;
				}
				String type = String.valueOf(in.getAttribute("type")).toLowerCase();
				if (type.equals("date")) {
					continue;
				}
				in.sendKeys(type.equals("number") || key.contains("no") || key.contains("number") ? "123456" : "ZQ" + suffix);
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
	}

	// the button that places the order (Submit / Place Order / Save ...), never Back or Next
	public WebElement submit_button() {
		List<WebElement> buttons = displayed(By.xpath("//button | //input[@type='submit' or @type='button'] | //a[contains(@class,'btn')]"));
		String[] words = { "submit", "place", "book", "confirm", "save", "order" };
		for (String w : words) {
			for (WebElement b : buttons) {
				String t = (b.getText() + " " + b.getAttribute("value")).toLowerCase();
				if (t.contains(w) && !t.contains("next") && !t.contains("back")) {
					return b;
				}
			}
		}
		throw new NoSuchElementException("The button that places the order was not found. Buttons: " + button_texts());
	}

	public List<String> button_texts() {
		List<String> out = new ArrayList<String>();
		for (WebElement b : displayed(By.xpath("//button | //input[@type='submit' or @type='button']"))) {
			String t = (b.getText() + " " + b.getAttribute("value")).trim();
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	public void Click_submit() {
		click_safely(submit_button());
		pause(2500);
	}

	// ======================= messages =======================

	// the text of a browser alert (it is accepted), or ""
	public String Alert_text() {
		try {
			Alert a = driver.switchTo().alert();
			String text = a.getText();
			a.accept();
			pause(800);
			return text;
		} catch (NoAlertPresentException e) {
			return "";
		}
	}

	public boolean alert_open() {
		try {
			driver.switchTo().alert();
			return true;
		} catch (NoAlertPresentException e) {
			return false;
		}
	}

	// the sweet alert / toast text that is displayed (the OK of a sweet alert is NOT clicked)
	public String notice() {
		try {
			List<WebElement> found = displayed(By.xpath("//div[contains(@class,'swal2-popup')] | //div[contains(@class,'toast')]"
					+ " | //div[contains(@class,'notyf')] | //div[contains(@class,'alert') and not(contains(@class,'modal'))]"));
			return found.isEmpty() ? "" : found.get(0).getText().trim().replaceAll("\\s+", " ");
		} catch (StaleElementReferenceException e) {
			return "";
		}
	}

	// clicks OK / Close / Yes of a sweet alert; returns its text ("" when there is none)
	public String Accept_notice() {
		String text = notice();
		List<WebElement> ok = displayed(By.xpath("//button[contains(@class,'swal2-confirm')]"));
		if (!ok.isEmpty()) {
			click_safely(ok.get(0));
			pause(1000);
		}
		return text;
	}

	// ---- the "Thank You!" pop-up after an order is submitted (title, message, Order No, OK) ----

	private static final String THANK_YOU = "//div[contains(@class,'swal2-popup') or contains(@class,'modal')]"
			+ "[.//*[contains(normalize-space(text()),'Thank You')]]";

	public boolean success_popup_open() {
		try {
			return !displayed(By.xpath(THANK_YOU)).isEmpty();
		} catch (StaleElementReferenceException e) {
			return false;
		}
	}

	public String success_popup_text() {
		try {
			List<WebElement> p = displayed(By.xpath(THANK_YOU));
			return p.isEmpty() ? "" : p.get(0).getText().trim().replaceAll("\\s+", " ");
		} catch (StaleElementReferenceException e) {
			return "";
		}
	}

	// the order number written in the pop-up ("Order No: 26OG...")
	public String success_popup_order_no() {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("Order No:?\\s*(\\S+)").matcher(success_popup_text());
		return m.find() ? m.group(1) : "";
	}

	// clicks OK of the Thank You pop-up
	public void Click_success_ok() {
		List<WebElement> ok = displayed(By.xpath(THANK_YOU + "//button[normalize-space()='OK' or contains(@class,'swal2-confirm')]"));
		if (ok.isEmpty()) {
			throw new NoSuchElementException("The OK button of the Thank You pop-up was not found");
		}
		click_safely(ok.get(0));
		pause(1500);
	}

	public List<String> validation_messages() {
		List<String> out = new ArrayList<String>();
		for (WebElement e : displayed(By.xpath("//*[starts-with(normalize-space(text()),'Please')]"
				+ " | //*[contains(@class,'invalid-feedback') or contains(@class,'text-danger') or contains(@class,'error')][normalize-space()!='']"))) {
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

	// every message the page gave after an action: browser alert, sweet alert / toast and validation texts
	public String All_messages() {
		StringBuilder sb = new StringBuilder();
		String alert = Alert_text();
		if (!alert.isEmpty()) {
			sb.append("alert: ").append(alert).append(" | ");
		}
		String n = notice();
		if (!n.isEmpty()) {
			sb.append("notice: ").append(n).append(" | ");
		}
		List<String> v = validation_messages();
		if (!v.isEmpty()) {
			sb.append("validation: ").append(v);
		}
		return sb.toString();
	}
}
