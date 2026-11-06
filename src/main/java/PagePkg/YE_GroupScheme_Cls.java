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
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * Page class of Scheme > Group Scheme (logged in as the web user), read from the screenshots of the screen.
 *
 * LIST: buttons Search and Create, the table filter "Search:", the table SCHEME CODE / SCHEME NAME / CATEGORY / PROMOTION TYPE /
 *   SCHEME TYPE / APPLICABLE LEVEL / FROM DATE / TO DATE / STATUS / ACTION. The Search button opens the search form: Scheme Code,
 *   Scheme Name, Scheme Category (Group Scheme, locked), From Date, To Date, Reset and Search.
 * CREATE form (every box is found by its label):
 *   Company Name (locked), Scheme Code *, Scheme Name *, Promotion Base Type (Amount / Quantity / Percentage / Bill Count /
 *   Bill Amount / Weight), From Date *, To Date *, Scheme Applicable Type (Amount / Quantity / Percentage / Each / Multiple /
 *   Special Rate / Weight), Applicable For (Category / SubCategory / Item / Menu Card / Bill), Group Combination (Flexible /
 *   Fixed / Key Item Combination), Min and Max (Amount / Quantity / Weight: the label follows the base type), Is Active, Scheme
 *   Application Method (Auto Apply / Salesman Selectable / Request Approval), Back and Submit.
 *   Under the boxes SECTIONS appear that depend on the choices, for example "Promotion Category" (a search box "Search items...",
 *   Remove, and a column such as Amount) and "Free Item" (search box, Qty, Remove), each with an "Add Item" button, and a
 *   check box "Free Item Selectable". A section is found by its "Add Item" button, its header is the first line of its text.
 * What the form shows after a choice is not fixed (the choices change the sections and their boxes): the methods
 * find the boxes by the words in their placeholder / name / column and REPORT what they found (describe_form) instead of
 * guessing. Typing: an empty field is typed into directly, without clear().
 */
public class YE_GroupScheme_Cls {

	WebDriver driver;
	WebDriverWait wait;

	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li"
			+ " | //*[@role='option'][normalize-space()!='']";

	static final String SEARCH_BOX = ".//input[contains(@placeholder,'Search')]";
	static final String VALUE_BOX = ".//input[not(@type='hidden') and not(@type='checkbox') and not(@type='radio')"
			+ " and not(contains(@placeholder,'Search'))]";

	// ---------- Constructor ----------
	public YE_GroupScheme_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
	}

	// ======================= helpers =======================

	private JavascriptExecutor js() {
		return (JavascriptExecutor) driver;
	}

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

	private List<WebElement> displayed_in(WebElement parent, By by) {
		List<WebElement> list = new ArrayList<WebElement>();
		for (WebElement e : parent.findElements(by)) {
			try {
				if (e.isDisplayed()) {
					list.add(e);
				}
			} catch (StaleElementReferenceException ex) {
				// skip
			}
		}
		return list;
	}

	private WebElement first_displayed(By by) {
		wait.until(d -> !displayed(by).isEmpty());
		return displayed(by).get(0);
	}

	private void click_safely(WebElement element) {
		js().executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			element.click();
		} catch (ElementClickInterceptedException e) {
			js().executeScript("arguments[0].click();", element);
		}
	}

	private void js_click(WebElement element) {
		js().executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", element);
		pause(500);
	}

	private void pause(int millis) {
		WaitUtil.settle(driver, millis);
	}

	// types into a field. An empty field is typed into directly (no clear()); a field with text is emptied with Ctrl+A and Delete.
	private void type(WebElement field, String value) {
		js().executeScript("arguments[0].scrollIntoView({block:'center'});", field);
		String current = field.getAttribute("value");
		if (current != null && !current.isEmpty()) {
			field.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
		}
		if (!value.isEmpty()) {
			field.sendKeys(value);
		}
	}

	// writes a value into a box with a script and tells the page (date boxes: the typed format depends on the browser language)
	private void set_value(WebElement field, String value) {
		js().executeScript("var e = arguments[0], v = arguments[1];"
				+ " var set = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;"
				+ " set.call(e, v);"
				+ " e.dispatchEvent(new Event('input', {bubbles: true}));"
				+ " e.dispatchEvent(new Event('change', {bubbles: true}));", field, value);
	}

	private void select_text(WebElement dropdown, String text) {
		Select select = new Select(dropdown);
		wait.until(d -> select.getOptions().size() > 1);
		for (int i = 0; i < select.getOptions().size(); i++) {
			if (select.getOptions().get(i).getText().trim().equalsIgnoreCase(text.trim())) {
				select.selectByIndex(i);
				return;
			}
		}
		for (int i = 0; i < select.getOptions().size(); i++) {
			if (select.getOptions().get(i).getText().trim().toLowerCase().startsWith(text.trim().toLowerCase())) {
				select.selectByIndex(i);
				return;
			}
		}
		throw new NoSuchElementException("Option '" + text + "' is not in the drop down. It offers: " + options_of(dropdown));
	}

	// the option texts of a drop down, without the placeholder ("Select") option
	public List<String> options_of(WebElement dropdown) {
		List<String> texts = new ArrayList<String>();
		for (WebElement o : new Select(dropdown).getOptions()) {
			String t = o.getText().trim();
			if (!t.isEmpty() && !t.equalsIgnoreCase("Select")) {
				texts.add(t);
			}
		}
		return texts;
	}

	private String label_xpath(String label) {
		return "//label[normalize-space()='" + label + "' or normalize-space()='" + label + " *' or normalize-space()='" + label
				+ ":']";
	}

	private WebElement label_input(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::input[not(@type='hidden')][1]"));
	}

	public WebElement label_select(String label) {
		return first_displayed(By.xpath(label_xpath(label) + "/following::select[1]"));
	}

	// The suggestion row of a search box is looked for on the SCREEN, not by a class name: the visible element whose own text
	// contains the typed name, below the box (or, when there is none, just above it) and in the box's column, the nearest one first.
	// An exact text wins over a longer one ("Rono-Sub" before "Rono-Sub2").
	static final String FIND_ROW_JS = "var box = arguments[0], text = arguments[1].toLowerCase(), relaxed = arguments[2];"
			+ " var br = box.getBoundingClientRect(), best = null, bestScore = 1e9;"
			+ " var all = document.body.querySelectorAll('*');"
			+ " for (var i = 0; i < all.length; i++) { var e = all[i];"
			+ "   if (e === box || e.contains(box) || box.contains(e)) { continue; }"
			+ "   if (/^(SCRIPT|STYLE|INPUT|TEXTAREA|OPTION|SELECT|OPTGROUP|HTML|BODY|HEAD)$/.test(e.tagName)) { continue; }"
			+ "   var r = e.getBoundingClientRect(); if (r.width === 0 || r.height === 0) { continue; }"
			+ "   var cs = window.getComputedStyle(e); if (cs.visibility === 'hidden' || cs.display === 'none') { continue; }"
			+ "   var own = ''; for (var c = e.firstChild; c; c = c.nextSibling) { if (c.nodeType === 3) { own += c.nodeValue; } }"
			+ "   own = own.replace(/\\s+/g, ' ').trim().toLowerCase();"
			+ "   if (!own || own.indexOf(text) < 0) { continue; }"
			+ "   if (r.left > br.right || r.right < br.left) { continue; }"
			+ "   var below = r.top - br.bottom;"
			+ "   if (!relaxed && below < -4) { continue; }"
			+ "   if (relaxed && Math.abs(below) > 400) { continue; }"
			+ "   var score = (own === text ? 0 : 1000) + Math.abs(below);"
			+ "   if (score < bestScore) { best = e; bestScore = score; } }"
			+ " return best;";

	// the texts that are on the screen under the box (to tell what the dropdown showed when the name was not in it)
	static final String NEAR_TEXTS_JS = "var box = arguments[0], br = box.getBoundingClientRect(), out = [];"
			+ " document.body.querySelectorAll('*').forEach(function (e) {"
			+ "   if (e === box || e.contains(box) || box.contains(e) || out.length >= 15) { return; }"
			+ "   var r = e.getBoundingClientRect(); if (r.width === 0 || r.height === 0) { return; }"
			+ "   if (r.top < br.bottom - 4 || r.top > br.bottom + 400 || r.left > br.right || r.right < br.left) { return; }"
			+ "   var own = ''; for (var c = e.firstChild; c; c = c.nextSibling) { if (c.nodeType === 3) { own += c.nodeValue; } }"
			+ "   own = own.replace(/\\s+/g, ' ').trim(); if (own && out.indexOf(own) < 0) { out.push(own); } });"
			+ " return out.join(' | ');";

	// focuses the box, types the name, waits for the suggestion row of that name and CLICKS it; returns the text of the row that
	// was clicked. When no row shows the name, the exception tells what the dropdown showed.
	private String search_pick(WebElement field, String text) {
		js().executeScript("arguments[0].scrollIntoView({block:'center'});", field);
		try {
			field.click();
		} catch (ElementClickInterceptedException e) {
			js().executeScript("arguments[0].click();", field);
		}
		// The dropdown asks the server (the item list is long): now and then the list is still the unfiltered first page when the
		// wait ends. The name is then typed again (up to 3 times, a longer wait each time) before the row is given up.
		WebElement row = null;
		for (int attempt = 1; attempt <= 3 && row == null; attempt++) {
			if (attempt > 1) {
				type(field, "");
				pause(800);
			}
			type(field, text);
			long end = System.currentTimeMillis() + (attempt == 1 ? 8000 : 12000);
			while (row == null && System.currentTimeMillis() < end) {
				pause(400);
				row = (WebElement) js().executeScript(FIND_ROW_JS, field, text, false);
			}
			if (row == null) {
				row = (WebElement) js().executeScript(FIND_ROW_JS, field, text, true);
			}
		}
		if (row == null) {
			throw new NoSuchElementException("No suggestion row with '" + text + "' appeared under the search box. The screen under the box shows: ["
					+ js().executeScript(NEAR_TEXTS_JS, field) + "]");
		}
		String clicked = row.getText().trim();
		click_safely(row);
		pause(900);
		try {
			if (row.isDisplayed()) {
				// the row is still there: the click did not choose it
				js().executeScript("arguments[0].click();", row);
				pause(900);
			}
		} catch (StaleElementReferenceException e) {
			// the row is gone: it was chosen
		}
		return clicked;
	}
	// ======================= list page =======================

	public boolean is_list_page() {
		try {
			return !displayed(By.xpath("//*[self::button or self::a][normalize-space()='Create']")).isEmpty()
					&& displayed(By.xpath("//label[normalize-space()='Scheme Code *']")).isEmpty();
		} catch (Exception e) {
			return false;
		}
	}

	public boolean is_create_page() {
		try {
			return !displayed(By.xpath(label_xpath("Scheme Code"))).isEmpty()
					&& !displayed(By.xpath("//*[self::button or self::input][normalize-space()='Submit' or @value='Submit']")).isEmpty();
		} catch (Exception e) {
			return false;
		}
	}

	public void Click_create() {
		click_safely(first_displayed(By.xpath("//*[self::button or self::a][normalize-space()='Create']")));
	}

	public void Click_back() {
		click_safely(first_displayed(By.xpath("//*[self::button or self::a][normalize-space()='Back']")));
	}

	// the first Search button (top left) opens / closes the search form
	private void open_search_form() {
		if (displayed(By.xpath(label_xpath("Scheme Code"))).isEmpty()) {
			click_safely(first_displayed(By.xpath("(//*[self::button or self::a][normalize-space()='Search'])[1]")));
			pause(500);
		}
	}

	// true when the listing holds a scheme with this code (the search form of the list is used: it asks the server)
	public boolean scheme_exists(String code) {
		open_search_form();
		type(label_input("Scheme Code"), code);
		click_safely(first_displayed(By.xpath("(//*[self::button or self::a][normalize-space()='Search'])[last()]")));
		pause(1500);
		wait.until(d -> !displayed(By.xpath("//table//tbody/tr")).isEmpty());
		for (WebElement row : displayed(By.xpath("//table//tbody/tr"))) {
			List<WebElement> cells = row.findElements(By.tagName("td"));
			if (cells.size() > 1 && cells.get(0).getText().trim().equalsIgnoreCase(code)) {
				return true;
			}
		}
		return false;
	}

	// the cells of the row of a scheme code in the listing (empty when it is not listed): code, name, category, promotion type ...
	public List<String> listing_row(String code) {
		List<String> out = new ArrayList<String>();
		for (WebElement row : displayed(By.xpath("//table//tbody/tr"))) {
			List<WebElement> cells = row.findElements(By.tagName("td"));
			if (cells.size() > 1 && cells.get(0).getText().trim().equalsIgnoreCase(code)) {
				for (WebElement c : cells) {
					out.add(c.getText().trim());
				}
				return out;
			}
		}
		return out;
	}

	// ======================= create form: the common boxes =======================

	public void Enter_code(String value) {
		type(label_input("Scheme Code"), value);
	}

	public void Enter_name(String value) {
		type(label_input("Scheme Name"), value);
	}

	public void Select_group_combination(String text) {
		select_text(label_select("Group Combination"), text);
		pause(600);
	}

	public void Select_base_type(String text) {
		select_text(label_select("Promotion Base Type"), text);
		pause(600);
	}

	// chooses the Scheme Applicable Type; for "Each" / "Multiple" the form may show one more drop down (Amount / Quantity):
	// it is chosen with 'sub_type' and its text is returned ("" when no new drop down appeared)
	public String Select_applicable_type(String text, String sub_type) {
		mark_known_boxes();
		select_text(label_select("Scheme Applicable Type"), text);
		pause(800);
		if (sub_type == null || sub_type.isEmpty()) {
			return "";
		}
		for (WebElement s : displayed(By.xpath("//select[not(@data-known)]"))) {
			List<String> offered = options_of(s);
			for (String o : offered) {
				if (o.toLowerCase().startsWith(sub_type.toLowerCase())) {
					select_text(s, o);
					pause(600);
					return o;
				}
			}
		}
		return "";
	}

	public void Select_applicable_for(String text) {
		select_text(label_select("Applicable For"), text);
		pause(800);
	}

	// Min / Max of the base (Min Amount, Min Quantity, Min Weight ...): the label starts with "Min " / "Max "
	public void Enter_min(String value) {
		type(first_displayed(By.xpath("//label[starts-with(normalize-space(),'Min ')]/following::input[not(@type='hidden')][1]")), value);
	}

	public void Enter_max(String value) {
		type(first_displayed(By.xpath("//label[starts-with(normalize-space(),'Max ')]/following::input[not(@type='hidden')][1]")), value);
	}

	public String min_label() {
		return first_displayed(By.xpath("//label[starts-with(normalize-space(),'Min ')]")).getText().replace("*", "").trim();
	}

	// the date boxes: 'iso' is yyyy-MM-dd
	public void Enter_from_date(String iso) {
		set_value(label_input("From Date"), iso);
	}

	public void Enter_to_date(String iso) {
		set_value(label_input("To Date"), iso);
	}

	// Is Active: the check box of the label is made checked / unchecked
	public void Set_active(boolean on) {
		js().executeScript("var l = Array.prototype.filter.call(document.querySelectorAll('label'),"
				+ " function (x) { return x.innerText.trim() === 'Is Active'; })[0];"
				+ " if (!l) { return; }"
				+ " var b = l.control || document.getElementById(l.htmlFor) || l.querySelector('input')"
				+ "   || (l.previousElementSibling && l.previousElementSibling.tagName === 'INPUT' ? l.previousElementSibling : null);"
				+ " if (b && b.checked !== arguments[0]) { b.click(); }", on);
	}

	// Scheme Application Method: Auto Apply / Salesman Selectable / Request Approval
	public void Select_method(String text) {
		click_safely(first_displayed(By.xpath("//*[self::label or self::span or self::button or self::div][normalize-space()='" + text
				+ "' and not(*)]")));
		pause(300);
	}

	public void Click_submit() {
		click_safely(first_displayed(By.xpath("//*[self::button or self::input][normalize-space()='Submit' or @value='Submit']")));
	}

	// ======================= create form: the sections =======================

	// marks every drop down that exists now, so a drop down that the form adds later can be told apart
	private void mark_known_boxes() {
		js().executeScript("document.querySelectorAll('select').forEach(function (s) { s.setAttribute('data-known', '1'); });");
	}

	// the "Add Item" buttons of the form, top to bottom: one per section
	private List<WebElement> add_buttons() {
		return displayed(By.xpath("//button[normalize-space()='Add Item'] | //a[normalize-space()='Add Item']"));
	}

	public int section_count() {
		return add_buttons().size();
	}

	// the box (container) of the section whose Add Item button is number 'index'
	private WebElement section(int index) {
		return add_buttons().get(index).findElement(By.xpath("ancestor::div[" + SEARCH_BOX + "][1]"));
	}

	// the first line of the text of a section ("Promotion Category", "Free Item" ...)
	public String section_header(int index) {
		Object text = js().executeScript("return (arguments[0].innerText || '').split('\\n')[0].trim();", section(index));
		return String.valueOf(text);
	}

	public boolean is_free_section(int index) {
		return section_header(index).toLowerCase().contains("free");
	}

	// puts the items into the section: the first row is used, "Add Item" adds one more row for every other item
	public List<String> Fill_items(int index, List<String> names) {
		List<String> clicked = new ArrayList<String>();
		for (int k = 0; k < names.size(); k++) {
			if (k > 0) {
				js_click(add_buttons().get(index));
			}
			List<WebElement> boxes = displayed_in(section(index), By.xpath(SEARCH_BOX));
			if (boxes.size() <= k) {
				throw new NoSuchElementException("The section '" + section_header(index) + "' has no search box for item number " + (k + 1));
			}
			clicked.add(search_pick(boxes.get(k), names.get(k)));
		}
		return clicked;
	}

	// the heading written above a box: the nearest visible text above it that overlaps its column ("Amount", "Min.Key Value" ...)
	static final String HEADER_JS = "var box = arguments[0], br = box.getBoundingClientRect(), best = null, bestTop = -1e9;"
			+ " document.body.querySelectorAll('*').forEach(function (e) {"
			+ "   if (e === box || e.contains(box) || box.contains(e)) { return; }"
			+ "   var r = e.getBoundingClientRect(); if (r.width === 0 || r.height === 0) { return; }"
			+ "   if (r.top >= br.top || br.top - r.bottom > 140 || r.left >= br.right || r.right <= br.left) { return; }"
			+ "   var own = ''; for (var c = e.firstChild; c; c = c.nextSibling) { if (c.nodeType === 3) { own += c.nodeValue; } }"
			+ "   own = own.replace(/\\s+/g, ' ').trim(); if (!own) { return; }"
			+ "   if (r.top > bestTop) { best = own; bestTop = r.top; } });"
			+ " return best === null ? '' : best;";

	// the key of a value box: the words of its placeholder / name / id / label and of the heading of its column (lower case)
	private String key_of(WebElement box) {
		Object key = js().executeScript("var e = arguments[0];"
				+ " var col = e.closest('[class*=col]'); var lab = col ? col.querySelector('label') : null;"
				+ " return ((e.placeholder || '') + ' ' + (e.name || '') + ' ' + (e.id || '') + ' ' + (e.getAttribute('aria-label') || '')"
				+ "   + ' ' + (lab ? lab.innerText : '')).toLowerCase();", box);
		return (String.valueOf(key) + " " + String.valueOf(js().executeScript(HEADER_JS, box)).toLowerCase()).trim();
	}
	// the value boxes of a section that are not locked
	private List<WebElement> value_boxes(int index) {
		List<WebElement> out = new ArrayList<WebElement>();
		for (WebElement b : displayed_in(section(index), By.xpath(VALUE_BOX))) {
			if (b.isEnabled() && b.getAttribute("readonly") == null) {
				out.add(b);
			}
		}
		return out;
	}

	// Puts 'value' into the value boxes of the section whose key has one of the words ("amount", "qty", "percent" ...); when no
	// box has one of the words and the section has exactly one value box, that box is used. Returns the keys of the boxes that
	// were filled ("" when none): the caller reports it, nothing is guessed beyond that.
	public String Fill_value(int index, String value, String... words) {
		List<WebElement> boxes = value_boxes(index);
		List<String> filled = new ArrayList<String>();
		for (WebElement b : boxes) {
			String key = key_of(b);
			boolean match = false;
			for (String w : words) {
				if (key.contains(w)) {
					match = true;
				}
			}
			if (match) {
				type(b, value);
				filled.add(key.trim());
			}
		}
		if (filled.isEmpty() && boxes.size() == 1) {
			type(boxes.get(0), value);
			filled.add(key_of(boxes.get(0)).trim() + " (the only box)");
		}
		return String.join("; ", filled);
	}

	// the keys of the value boxes of a section that are still empty or 0 (the form asks for them, nothing was typed): reported by the test
	public List<String> Unfilled_boxes(int index) {
		List<String> out = new ArrayList<String>();
		for (WebElement b : value_boxes(index)) {
			String v = b.getAttribute("value");
			if (v == null || v.trim().isEmpty() || v.trim().equals("0")) {
				out.add(key_of(b));
			}
		}
		return out;
	}
	// ======================= what the form shows =======================

	// one line per visible box of the form: label | kind | placeholder | value | options (a drop down) | locked
	public String describe_form() {
		Object text = js().executeScript("var out = [];"
				+ " document.querySelectorAll('input:not([type=hidden]), select, textarea').forEach(function (e) {"
				+ "   if (e.offsetParent === null) { return; }"
				+ "   var col = e.closest('[class*=col]'); var lab = col ? col.querySelector('label') : null;"
				+ "   var kind = e.tagName === 'SELECT' ? 'select' : (e.type || e.tagName);"
				+ "   var opts = e.tagName === 'SELECT' ? Array.prototype.map.call(e.options, function (o) { return o.text.trim(); }).join(' / ') : '';"
				+ "   var val = (kind === 'checkbox' || kind === 'radio') ? String(e.checked) : (e.tagName === 'SELECT' ? (e.selectedOptions[0] ? e.selectedOptions[0].text.trim() : '') : e.value);"
				+ "   out.push((lab ? lab.innerText.trim() : '(no label)') + ' | ' + kind + ' | ' + (e.placeholder || '') + ' | ' + val"
				+ "     + (opts ? ' | options: ' + opts : '') + ((e.disabled || e.readOnly) ? ' | locked' : ''));"
				+ " });"
				+ " document.querySelectorAll('button').forEach(function (b) { if (b.offsetParent !== null && b.innerText.trim()) { out.push('[button] ' + b.innerText.trim()); } });"
				+ " return out.join('\\n');");
		return String.valueOf(text);
	}

	// the options of a drop down of the form by its label (for the inspection test)
	public List<String> options_by_label(String label) {
		return options_of(label_select(label));
	}

	// ======================= after Submit =======================

	// everything the page tells after Submit: pop-up (SweetAlert), toast, alert bars and the messages under the boxes
	public String read_messages() {
		Object text = js().executeScript("var out = [];"
				+ " function add(sel) { document.querySelectorAll(sel).forEach(function (e) {"
				+ "   if (e.offsetParent !== null || e.classList.contains('swal2-popup')) { var t = (e.innerText || '').replace(/\\s+/g, ' ').trim();"
				+ "     if (t && out.indexOf(t) < 0) { out.push(t); } } }); }"
				+ " add('.swal2-popup'); add('.toast-message'); add('.toast'); add('.alert'); add('.modal.show .modal-body');"
				+ " add('.text-danger'); add('.invalid-feedback'); add('.field-validation-error'); add('.validation-summary-errors');"
				+ " return out.join(' || ');");
		return String.valueOf(text);
	}

	// clicks OK on the pop-up that Submit may show; returns its text
	public String dismiss_popup() {
		String text = "";
		List<WebElement> popups = displayed(By.xpath("//div[contains(@class,'swal2-popup')]"));
		if (!popups.isEmpty()) {
			text = popups.get(0).getText().replace("\n", " ").trim();
			List<WebElement> ok = displayed(By.xpath("//button[contains(@class,'swal2-confirm')]"));
			if (!ok.isEmpty()) {
				click_safely(ok.get(0));
				pause(500);
			}
		}
		return text;
	}

	// waits until Submit has an answer: the list page again, or a pop-up / message; returns what was seen
	public String wait_for_answer(int seconds) {
		long end = System.currentTimeMillis() + seconds * 1000L;
		while (System.currentTimeMillis() < end) {
			if (!displayed(By.xpath("//div[contains(@class,'swal2-popup')]")).isEmpty() || is_list_page()) {
				break;
			}
			pause(400);
		}
		return read_messages();
	}
}
