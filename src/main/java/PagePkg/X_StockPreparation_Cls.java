package PagePkg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import UtillPkg.WaitUtil;

/*
 * NOTES - X_StockPreparation_Cls (Page Object Model)
 * ------------------------------------------------
 * Field Management > StockPreparation. One page with three parts (all found by the ids of the page):
 *   1. LIST   : Search (#searchBtn) and Create (#createBtn) buttons, the "Search:" table filter, the search form
 *               (#Selectemployee, #FromDate, #ToDate, #btnsearch), the grid #tblStockPreparationGrid
 *               (9 columns, a view icon and an edit icon per row), paging.
 *   2. CREATE : #txtEmployee, #ddlRoute (filled after the employee is chosen), #txtVehicle, #txtRequestDate (dd/mm/yyyy),
 *               item search #txtSearchItem + #txtPrepQty + #btnAddItem, items table #tblItems (a trash button per row),
 *               Back and #btnSubmit. Messages are red toastr toasts.
 *   3. Pop-ups: view #modal-xl-new (items table #tblStockdetails) and edit #modal-edit-stock
 *               (#editSelectEmployee, #editPrepareDate, #editIsDownloaded, #editIsUploaded, #btnSubmitEdit, Cancel).
 * Employee and item boxes are jQuery UI autocomplete boxes: the text is typed and the suggestion is clicked.
 * Pop-ups are "shown" when they carry the class "show" (a minimized window never finishes their fade).
 * All table reads are done with one JavaScript call, so a big grid does not slow the test.
 */
public class X_StockPreparation_Cls {

	WebDriver driver;
	WebDriverWait wait;

	public static final String[] HEADERS = { "EMPLOYEE NAME", "DELIVERY ROUTE", "PREPARE FOR DATE", "DOWNLOAD STATUS",
			"STO NUMBER", "JOURNEY NUMBER", "TRIP ID", "EWAY BILL", "ACTIONS" };

	// what the last submit (create or edit) showed on the screen
	public String last_message = "";

	public X_StockPreparation_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
	}

	// ======================= helpers =======================

	private Object js(String script, Object... args) {
		return ((JavascriptExecutor) driver).executeScript(script, args);
	}

	private WebElement by_id(String id) {
		return driver.findElement(By.id(id));
	}

	// true when the element exists and is visible (not for pop-ups: see modal_open)
	private boolean shown(String id) {
		try {
			return Boolean.TRUE.equals(js("var e = document.getElementById(arguments[0]); if (!e) { return false; }"
					+ " var s = window.getComputedStyle(e); return e.offsetParent !== null && s.visibility !== 'hidden';", id));
		} catch (org.openqa.selenium.WebDriverException e) {
			return false;
		}
	}

	private boolean wait_for(BooleanSupplier condition, int millis) {
		long end = System.currentTimeMillis() + millis;
		while (System.currentTimeMillis() < end) {
			try {
				if (condition.getAsBoolean()) {
					return true;
				}
			} catch (RuntimeException e) {
				// the page is changing: try again
			}
			WaitUtil.nap(100);
		}
		return false;
	}

	// scrolls to the element and clicks it; when something covers it the click is done with JavaScript
	private void safe_click(WebElement element) {
		js("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			element.click();
		} catch (ElementClickInterceptedException e) {
			WaitUtil.unblock(driver);
			js("arguments[0].click();", element);
		}
	}

	private void type(WebElement box, String text) {
		box.clear();
		if (!text.isEmpty()) {
			box.sendKeys(text);
		}
	}

	@SuppressWarnings("unchecked")
	private List<List<String>> table_of(Object raw) {
		List<List<String>> out = new ArrayList<List<String>>();
		if (raw instanceof List) {
			for (Object row : (List<Object>) raw) {
				List<String> cells = new ArrayList<String>();
				for (Object cell : (List<Object>) row) {
					cells.add(String.valueOf(cell));
				}
				out.add(cells);
			}
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	private List<String> list_of(Object raw) {
		List<String> out = new ArrayList<String>();
		if (raw instanceof List) {
			for (Object o : (List<Object>) raw) {
				out.add(String.valueOf(o));
			}
		}
		return out;
	}

	// ======================= messages (toasts) =======================

	// removes the toasts that are still on the screen (so the next message is the new one)
	public void Clear_toasts() {
		js("if (window.toastr && toastr.clear) { toastr.clear(); }"
				+ " document.querySelectorAll('#toast-container .toast').forEach(function (e) { e.remove(); });");
	}

	// the texts of the toasts that are on the screen now
	public List<String> toasts() {
		List<String> out = new ArrayList<String>();
		try {
			for (String t : list_of(js("return Array.prototype.filter.call(document.querySelectorAll('.toast-message'),"
					+ " function (e) { return e.offsetParent !== null; }).map(function (e) { return e.innerText.trim(); });"))) {
				if (!t.isEmpty() && !out.contains(t)) {
					out.add(t);
				}
			}
		} catch (org.openqa.selenium.WebDriverException e) {
			// an alert is open or the page is changing
		}
		return out;
	}

	// waits up to 'millis' for a toast and returns all toast texts seen (joined with " | "); "" when none came
	public String Wait_toast(int millis) {
		Set<String> seen = new LinkedHashSet<String>();
		wait_for(() -> {
			seen.addAll(toasts());
			return !seen.isEmpty();
		}, millis);
		WaitUtil.nap(200);
		seen.addAll(toasts());
		return String.join(" | ", seen);
	}

	// accepts a browser alert if one is open and returns its text (empty when there is none)
	public String Accept_alert() {
		try {
			org.openqa.selenium.Alert a = driver.switchTo().alert();
			String text = a.getText();
			a.accept();
			return text;
		} catch (NoAlertPresentException e) {
			return "";
		}
	}

	// ======================= autocomplete boxes =======================

	private List<String> visible_suggestions() {
		return list_of(js("return Array.prototype.filter.call(document.querySelectorAll('ul.ui-autocomplete li'),"
				+ " function (e) { return e.offsetParent !== null; }).map(function (e) { return e.innerText.trim(); });"));
	}

	// types the text and waits (up to 3 s) for suggestions; returns what is offered
	private List<String> suggestions_for(WebElement box, String text) {
		type(box, text);
		wait_for(() -> !visible_suggestions().isEmpty(), 3000);
		WaitUtil.nap(150);
		return visible_suggestions();
	}

	// types the text, clicks the first suggestion that contains 'contains'; returns its text ("" = nothing offered / matched)
	private String pick(WebElement box, String text, String contains) {
		List<String> found = suggestions_for(box, text);
		if (found.isEmpty()) {
			return "";
		}
		for (WebElement li : driver.findElements(By.xpath("//ul[contains(@class,'ui-autocomplete')]//li[div]"))) {
			if (li.isDisplayed() && li.getText().trim().toLowerCase().contains(contains.toLowerCase())) {
				String chosen = li.getText().trim();
				new Actions(driver).moveToElement(li).click().perform();
				WaitUtil.nap(300);
				return chosen;
			}
		}
		return "";
	}

	// ======================= LIST screen =======================

	public boolean is_list_page() {
		return shown("createBtn");
	}

	public WebElement verify_create_btn() {
		return by_id("createBtn");
	}

	public WebElement verify_search_btn() {
		return by_id("searchBtn");
	}

	public WebElement verify_table_filter() {
		return driver.findElement(By.xpath("//input[@type='search']"));
	}

	public void Click_create() {
		Clear_toasts();
		safe_click(by_id("createBtn"));
	}

	public boolean search_form_open() {
		return shown("FromDate");
	}

	// true when the box has a real size on the screen (a form that is still sliding open cannot be typed into)
	private boolean box_ready(String id) {
		return Boolean.TRUE.equals(js("var e = document.getElementById(arguments[0]); if (!e) { return false; }"
				+ " var r = e.getBoundingClientRect(); return r.width > 5 && r.height > 5 && e.offsetParent !== null;", id));
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			safe_click(by_id("searchBtn"));
			wait_for(() -> search_form_open(), 4000);
		}
		wait_for(() -> box_ready("Selectemployee"), 5000);
		WaitUtil.nap(200);
	}

	public String search_employee_value() {
		return by_id("Selectemployee").getAttribute("value");
	}

	public String search_from_value() {
		return by_id("FromDate").getAttribute("value");
	}

	public String search_to_value() {
		return by_id("ToDate").getAttribute("value");
	}

	public boolean search_go_visible() {
		return shown("btnsearch");
	}

	// sets a date box of the search form (text dd/mm/yyyy) and tells the page that it changed
	private void set_search_date(String id, String ddmmyyyy) {
		js("var e = document.getElementById(arguments[0]); e.value = arguments[1];"
				+ " e.dispatchEvent(new Event('input', {bubbles: true})); e.dispatchEvent(new Event('change', {bubbles: true}));",
				id, ddmmyyyy);
	}

	// Fills the search form and presses its Search button. employee "" = leave the employee box empty;
	// the employee text is typed and the suggestion that contains it is clicked (the typed text stays when nothing is offered).
	public void Search(String employee, String from, String to) {
		Open_search_form();
		Clear_toasts();
		WebElement box = by_id("Selectemployee");
		wait_for(() -> box_ready("Selectemployee"), 5000);
		if (employee.isEmpty()) {
			box.clear();
		} else {
			if (pick(box, employee, employee).isEmpty()) {
				type(box, employee);
			}
		}
		set_search_date("FromDate", from);
		set_search_date("ToDate", to);
		safe_click(by_id("btnsearch"));
		WaitUtil.settle(driver, 3000);
	}

	// the employee suggestions the search form offers for a text
	public List<String> search_employee_suggestions(String text) {
		Open_search_form();
		return suggestions_for(by_id("Selectemployee"), text);
	}

	// Types in the "Search:" box above the grid (it filters the grid)
	public void Filter_table(String text) {
		// typed with the keyboard (select all, delete, type): the grid filters on key presses, clear() would not trigger it
		WebElement box = verify_table_filter();
		box.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
		if (!text.isEmpty()) {
			box.sendKeys(text);
		}
		WaitUtil.settle(driver, 1500);
	}

	public List<String> headers() {
		List<String> out = new ArrayList<String>();
		for (String t : list_of(js("return Array.prototype.map.call(document.querySelectorAll('#tblStockPreparationGrid thead th'),"
				+ " function (e) { return (e.innerText || '').replace(/\\s+/g, ' ').trim().toUpperCase(); });"))) {
			out.add(t);
		}
		return out;
	}

	// every real row of the grid (the "No Data Available" row is not counted), one list of cell texts per row
	public List<List<String>> rows() {
		List<List<String>> all = table_of(js("return Array.prototype.map.call(document.querySelectorAll('#tblStockPreparationGrid tbody tr'),"
				+ " function (r) { return Array.prototype.map.call(r.querySelectorAll('td'),"
				+ " function (c) { return (c.innerText || '').replace(/\\s+/g, ' ').trim(); }); });"));
		List<List<String>> real = new ArrayList<List<String>>();
		for (List<String> row : all) {
			if (row.size() >= 5) {
				real.add(row);
			}
		}
		return real;
	}

	public int row_count() {
		return rows().size();
	}

	public boolean no_data_shown() {
		return Boolean.TRUE.equals(js("return document.querySelector('#tblStockPreparationGrid tbody').innerText.toLowerCase().indexOf('no data') >= 0;"));
	}

	// the "Showing 1 to 2 of 2 entries" line ("" when it is missing)
	public String entries_text() {
		return String.valueOf(js("return (document.body.innerText.match(/Showing[^\\n]*/) || [''])[0];")).trim();
	}

	// index of the first row of this employee (text contained) and this date (dd-MM-yyyy) and, when not blank, this route; -1 = none
	public int find_row(String employee, String date_dash, String route) {
		List<List<String>> all = rows();
		for (int i = 0; i < all.size(); i++) {
			List<String> r = all.get(i);
			if (r.get(0).toLowerCase().contains(employee.toLowerCase()) && r.get(2).equals(date_dash)
					&& (route.isEmpty() || r.get(1).equalsIgnoreCase(route))) {
				return i;
			}
		}
		return -1;
	}

	// how many rows match employee + date + route (route blank = any)
	public int count_rows(String employee, String date_dash, String route) {
		int n = 0;
		for (List<String> r : rows()) {
			if (r.get(0).toLowerCase().contains(employee.toLowerCase()) && r.get(2).equals(date_dash)
					&& (route.isEmpty() || r.get(1).equalsIgnoreCase(route))) {
				n++;
			}
		}
		return n;
	}

	// icons of one row: the number of icons in the action cell
	public int action_icon_count(int row) {
		return ((Number) js("var rows = Array.prototype.filter.call(document.querySelectorAll('#tblStockPreparationGrid tbody tr'),"
				+ " function (r) { return r.querySelectorAll('td').length >= 5; });"
				+ " return rows[arguments[0]].querySelectorAll('td:last-child i, td:last-child a, td:last-child button').length;", row)).intValue();
	}

	private void click_row_icon(int row, String selector) {
		Clear_toasts();
		js("var rows = Array.prototype.filter.call(document.querySelectorAll('#tblStockPreparationGrid tbody tr'),"
				+ " function (r) { return r.querySelectorAll('td').length >= 5; });"
				+ " rows[arguments[0]].querySelector(arguments[1]).click();", row, selector);
	}

	public void Click_header(String name) {
		// the grid has a hidden copy of the header row: the visible one is clicked
		Object clicked = js("var n = arguments[0].toUpperCase();"
				+ " var list = Array.prototype.filter.call(document.querySelectorAll('table thead th'), function (th) {"
				+ " return th.offsetParent !== null && th.getBoundingClientRect().height > 0 && th.innerText.trim().toUpperCase() === n; });"
				+ " if (!list.length) { return false; } list[0].click(); return true;", name);
		if (!Boolean.TRUE.equals(clicked)) {
			throw new org.openqa.selenium.NoSuchElementException("Column header not found: " + name);
		}
		WaitUtil.settle(driver, 800);
	}

	// "asc", "desc" or "" (not sorted by this column)
	public String sort_state(String name) {
		return String.valueOf(js("var name = arguments[0].toUpperCase(); var r = ''; var found = false;"
				+ " Array.prototype.forEach.call(document.querySelectorAll('table thead th'), function (th) {"
				+ " if (!found && th.innerText.trim().toUpperCase() === name) { found = true;"
				+ " if (th.classList.contains('sorting_asc')) { r = 'asc'; } else if (th.classList.contains('sorting_desc')) { r = 'desc'; } } });"
				+ " return r;", name));
	}

	public boolean next_enabled() {
		return Boolean.TRUE.equals(js("var a = Array.prototype.filter.call(document.querySelectorAll('a.page-link'),"
				+ " function (e) { return e.innerText.trim() === 'Next'; })[0];"
				+ " return !!a && !a.parentElement.classList.contains('disabled');"));
	}

	public boolean previous_enabled() {
		return Boolean.TRUE.equals(js("var a = Array.prototype.filter.call(document.querySelectorAll('a.page-link'),"
				+ " function (e) { return e.innerText.trim() === 'Previous'; })[0];"
				+ " return !!a && !a.parentElement.classList.contains('disabled');"));
	}

	public void Click_next() {
		js("Array.prototype.filter.call(document.querySelectorAll('a.page-link'), function (e) { return e.innerText.trim() === 'Next'; })[0].click();");
		WaitUtil.settle(driver, 800);
	}

	public void Click_previous() {
		js("Array.prototype.filter.call(document.querySelectorAll('a.page-link'), function (e) { return e.innerText.trim() === 'Previous'; })[0].click();");
		WaitUtil.settle(driver, 800);
	}

	// ======================= VIEW pop-up =======================

	private boolean modal_open(String id) {
		try {
			return Boolean.TRUE.equals(js("var m = document.getElementById(arguments[0]); return !!m && m.classList.contains('show');", id));
		} catch (org.openqa.selenium.WebDriverException e) {
			return false;
		}
	}

	public void Click_view(int row) {
		click_row_icon(row, "i.fa-eye");
		WaitUtil.unblock(driver);
		wait_for(() -> modal_open("modal-xl-new"), 6000);
	}

	public boolean view_open() {
		return modal_open("modal-xl-new");
	}

	public String view_title() {
		return String.valueOf(js("var e = document.querySelector('#modal-xl-new .modal-title'); return e ? e.innerText.trim() : '';"));
	}

	// the head line of the pop-up (employee, delivery route, delivery date)
	public String view_header() {
		return String.valueOf(js("var e = document.querySelector('#modal-xl-new .modal-body'); if (!e) { return ''; }"
				+ " var t = e.innerText.replace(/\\s+/g, ' ').trim(); var i = t.toUpperCase().indexOf('SL NO'); return i > 0 ? t.substring(0, i).trim() : t;"));
	}

	public List<String> view_headers() {
		return list_of(js("return Array.prototype.map.call(document.querySelectorAll('#tblStockdetails thead th'),"
				+ " function (e) { return e.innerText.trim().toUpperCase(); });"));
	}

	// the items of the pop-up: sl no, item code, item name, unit, quantity
	public List<List<String>> view_items() {
		List<List<String>> out = new ArrayList<List<String>>();
		for (List<String> r : table_of(js("return Array.prototype.map.call(document.querySelectorAll('#tblStockdetails tbody tr'),"
				+ " function (r) { return Array.prototype.map.call(r.querySelectorAll('td'),"
				+ " function (c) { return (c.innerText || '').replace(/\\s+/g, ' ').trim(); }); });"))) {
			if (r.size() >= 5) {
				out.add(r);
			}
		}
		return out;
	}

	public void Close_view() {
		js("var b = document.querySelector('#modal-xl-new button.close'); if (b) { b.click(); }");
		if (!wait_for(() -> !modal_open("modal-xl-new"), 3000)) {
			js("if (window.jQuery) { jQuery('#modal-xl-new').modal('hide'); }");
			wait_for(() -> !modal_open("modal-xl-new"), 3000);
		}
		WaitUtil.settle(driver, 500);
	}

	// ======================= EDIT pop-up =======================

	public void Click_edit(int row) {
		click_row_icon(row, "i.fa-pencil-alt");
		WaitUtil.unblock(driver);
		wait_for(() -> modal_open("modal-edit-stock"), 6000);
		WaitUtil.nap(300);
	}

	public boolean edit_open() {
		return modal_open("modal-edit-stock");
	}

	public String edit_title() {
		return String.valueOf(js("var e = document.querySelector('#modal-edit-stock .modal-title'); return e ? e.innerText.trim() : '';"));
	}

	public String edit_employee() {
		return by_id("editSelectEmployee").getAttribute("value");
	}

	public boolean edit_employee_locked() {
		return Boolean.TRUE.equals(js("var e = document.getElementById('editSelectEmployee');"
				+ " return e.readOnly === true || e.disabled === true;"));
	}

	public String edit_date() {
		return by_id("editPrepareDate").getAttribute("value");
	}

	public boolean edit_downloaded() {
		return by_id("editIsDownloaded").isSelected();
	}

	public boolean edit_uploaded() {
		return by_id("editIsUploaded").isSelected();
	}

	// types a date into the date box of the edit pop-up (the format the box shows, for example 28-10-2026)
	public void Set_edit_date(String text) {
		WebElement box = by_id("editPrepareDate");
		box.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
		if (!text.isEmpty()) {
			box.sendKeys(text);
		}
		box.sendKeys(Keys.TAB);
		WaitUtil.nap(300);
		if (text.isEmpty() && !box.getAttribute("value").isEmpty()) {
			js("var e = document.getElementById('editPrepareDate'); e.value = '';"
					+ " e.dispatchEvent(new Event('change', {bubbles: true}));");
		}
	}

	public void Set_edit_flags(boolean downloaded, boolean uploaded) {
		if (by_id("editIsDownloaded").isSelected() != downloaded) {
			js("arguments[0].click();", by_id("editIsDownloaded"));
		}
		if (by_id("editIsUploaded").isSelected() != uploaded) {
			js("arguments[0].click();", by_id("editIsUploaded"));
		}
	}

	// presses Submit of the edit pop-up and waits until it closes or a message shows; the message is kept
	public String Submit_edit() {
		last_message = "";
		Clear_toasts();
		safe_click(by_id("btnSubmitEdit"));
		java.util.Set<String> seen = new LinkedHashSet<String>();
		wait_for(() -> {
			String a = Accept_alert();
			if (!a.isEmpty()) {
				seen.add("alert: " + a);
			}
			seen.addAll(toasts());
			return !edit_open() || !seen.isEmpty();
		}, 10000);
		WaitUtil.nap(300);
		seen.addAll(toasts());
		last_message = String.join(" | ", seen);
		WaitUtil.settle(driver, 1500);
		return last_message;
	}

	public void Cancel_edit() {
		js("var b = document.querySelector('#modal-edit-stock button.btn-danger'); if (b) { b.click(); }");
		if (!wait_for(() -> !edit_open(), 3000)) {
			js("if (window.jQuery) { jQuery('#modal-edit-stock').modal('hide'); }");
			wait_for(() -> !edit_open(), 3000);
		}
		WaitUtil.settle(driver, 500);
	}

	public void Close_edit_x() {
		js("var b = document.querySelector('#modal-edit-stock button.close'); if (b) { b.click(); }");
		wait_for(() -> !edit_open(), 3000);
		WaitUtil.settle(driver, 500);
	}

	// ======================= CREATE form =======================

	public void Open_create() {
		Click_create();
		wait.until(d -> create_form_open());
	}

	public boolean create_form_open() {
		return shown("txtEmployee");
	}

	public List<String> create_labels() {
		return list_of(js("return Array.prototype.filter.call(document.querySelectorAll('label'),"
				+ " function (e) { return e.offsetParent !== null && e.innerText.trim() !== ''; })"
				+ ".map(function (e) { return e.innerText.trim(); });"));
	}

	public List<String> items_headers() {
		return list_of(js("return Array.prototype.map.call(document.querySelectorAll('#tblItems thead th'),"
				+ " function (e) { return e.innerText.trim().toUpperCase(); });"));
	}

	public String employee_value() {
		return by_id("txtEmployee").getAttribute("value");
	}

	public String vehicle_value() {
		return by_id("txtVehicle").getAttribute("value");
	}

	public String date_value() {
		return by_id("txtRequestDate").getAttribute("value");
	}

	public String item_value() {
		return by_id("txtSearchItem").getAttribute("value");
	}

	public String qty_value() {
		return by_id("txtPrepQty").getAttribute("value");
	}

	public boolean route_enabled() {
		return by_id("ddlRoute").isEnabled();
	}

	public List<String> route_options() {
		return list_of(js("return Array.prototype.map.call(document.getElementById('ddlRoute').options, function (o) { return o.text.trim(); });"));
	}

	public String route_selected() {
		return String.valueOf(js("var s = document.getElementById('ddlRoute'); return s.options[s.selectedIndex].text.trim();"));
	}

	// the employee suggestions of the create form for a text
	public List<String> employee_suggestions(String text) {
		return suggestions_for(by_id("txtEmployee"), text);
	}

	// types the employee, clicks the suggestion and waits for the route list; returns the chosen text ("" = none offered)
	public String Choose_employee(String text) {
		String chosen = pick(by_id("txtEmployee"), text, text);
		if (!chosen.isEmpty()) {
			wait_for(() -> route_options().size() > 1, 8000);
		}
		return chosen;
	}

	// types an employee without clicking any suggestion
	public void Type_employee_only(String text) {
		type(by_id("txtEmployee"), text);
		WaitUtil.nap(400);
		js("document.body.click();");
	}

	// chooses the route whose text contains 'text'; returns the option chosen ("" = not in the list)
	public String Choose_route(String text) {
		Select select = new Select(by_id("ddlRoute"));
		for (int i = 0; i < select.getOptions().size(); i++) {
			String t = select.getOptions().get(i).getText().trim();
			if (t.toLowerCase().contains(text.toLowerCase())) {
				select.selectByIndex(i);
				WaitUtil.nap(300);
				return t;
			}
		}
		return "";
	}

	public void Type_vehicle(String text) {
		type(by_id("txtVehicle"), text);
	}

	// types a date (dd/mm/yyyy) into the Prepare For Date box
	public void Set_date(String ddmmyyyy) {
		WebElement box = by_id("txtRequestDate");
		box.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
		box.sendKeys(ddmmyyyy);
		box.sendKeys(Keys.TAB);
		WaitUtil.nap(300);
	}

	// empties the Prepare For Date box (it must really be empty afterwards)
	public void Clear_date() {
		WebElement box = by_id("txtRequestDate");
		box.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
		box.sendKeys(Keys.TAB);
		WaitUtil.nap(300);
		if (!box.getAttribute("value").isEmpty()) {
			js("var e = document.getElementById('txtRequestDate'); e.value = '';"
					+ " e.dispatchEvent(new Event('change', {bubbles: true}));");
		}
	}

	// the item suggestions for a text (the search box needs about 3 letters)
	public List<String> item_suggestions(String text) {
		return suggestions_for(by_id("txtSearchItem"), text);
	}

	// types the item and clicks the suggestion that contains 'contains'; returns its text ("" = none)
	public String Choose_item(String text, String contains) {
		return pick(by_id("txtSearchItem"), text, contains);
	}

	public void Type_item_only(String text) {
		type(by_id("txtSearchItem"), text);
		WaitUtil.nap(400);
		js("document.body.click();");
	}

	public void Set_qty(String text) {
		type(by_id("txtPrepQty"), text);
	}

	// presses Add Item and returns the message the page showed ("" = none)
	public String Click_add_item() {
		Clear_toasts();
		safe_click(by_id("btnAddItem"));
		return Wait_toast(1200);
	}

	// chooses the item, types the quantity and presses Add Item; returns the message of the page ("" = none).
	// When the item is not offered the message is "NOT OFFERED: <text>".
	public String Add_item(String search, String contains, String qty) {
		String chosen = Choose_item(search, contains);
		if (chosen.isEmpty()) {
			return "NOT OFFERED: " + search;
		}
		Set_qty(qty);
		return Click_add_item();
	}

	// the rows of the items table: sl no, item name, prepared qty
	public List<List<String>> item_rows() {
		List<List<String>> out = new ArrayList<List<String>>();
		for (List<String> r : table_of(js("return Array.prototype.map.call(document.querySelectorAll('#tblItems tbody tr'),"
				+ " function (r) { return Array.prototype.map.call(r.querySelectorAll('td'),"
				+ " function (c) { return (c.innerText || '').replace(/\\s+/g, ' ').trim(); }); });"))) {
			if (r.size() >= 3) {
				out.add(r);
			}
		}
		return out;
	}

	public void Remove_item(int row) {
		js("document.querySelectorAll('#tblItems tbody tr')[arguments[0]].querySelector('.btn-remove').click();", row);
		WaitUtil.nap(400);
	}

	// waits until the list screen is back (true) or the time is over (false)
	public boolean Wait_list_page(int millis) {
		boolean back = wait_for(() -> is_list_page(), millis);
		WaitUtil.settle(driver, 1500);
		return back;
	}

	public void Click_back() {
		Clear_toasts();
		safe_click(driver.findElement(By.xpath("//button[normalize-space()='Back']")));
		wait_for(() -> is_list_page(), 5000);
	}

	// Presses Submit of the create form. Waits until the list is back or a message shows; returns every message seen.
	public String Submit_create() {
		last_message = "";
		Clear_toasts();
		safe_click(by_id("btnSubmit"));
		Set<String> seen = new LinkedHashSet<String>();
		wait_for(() -> {
			String a = Accept_alert();
			if (!a.isEmpty()) {
				seen.add("alert: " + a);
			}
			seen.addAll(toasts());
			return !create_form_open() || !seen.isEmpty();
		}, 12000);
		WaitUtil.nap(300);
		seen.addAll(toasts());
		last_message = String.join(" | ", seen);
		return last_message;
	}
}
