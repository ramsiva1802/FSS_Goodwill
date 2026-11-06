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
 * Page class of Scheme > Individual Scheme (logged in as the web user), read from the screenshots of the screen.
 *
 * LIST: buttons Search and Create, the table filter "Search:", the table SCHEME CODE / SCHEME NAME / CATEGORY / PROMOTION TYPE /
 *   SCHEME TYPE / APPLICABLE LEVEL / FROM DATE / TO DATE / STATUS / ACTION (10 rows a page).
 * CREATE form: Company Name (locked), Scheme Code *, Scheme Name *, Promotion Base Type, From Date *, To Date *, Scheme Applicable
 *   Type, Applicable For (Category / SubCategory / Item / Menu Card), Is Active, Scheme Application Method, Cancel, Submit.
 *   There is NO Group Combination and NO Min / Max under the boxes: Min and Max are boxes of every ROW.
 * ROWS (shown after Applicable For is chosen, they depend on the Scheme Applicable Type):
 *   Amount        [Promotion <level> search] [Amount] [Min Amount] [Max Amount] [Remove]
 *   Percentage    [search] [Percentage (%)] [Min] [Max]          Each  [search] [Each] [Min] [Max]
 *   Multiple      [search] [Multiple] [Min] [Max]                Weight [search] [Weight] [Min] [Max]
 *   Special Rate  [search] [Min] [Max] [Special Rate] [Per Unit]
 *   Quantity      a table: [search] [Min] [Max], and under it the table FREE ITEM: [search] [Qty] [Remove / Add Item], the check box
 *                 "Free Item Selectable" with a drop down and a locked Qty.
 * The layouts differ, so the boxes are not found by a fixed place: every search box ("Search items...") is a ROW; the boxes in
 * the same horizontal line are its value boxes; a box is named by its placeholder and the heading written ABOVE it (found by
 * position on the screen); the heading above a search box tells whether it is a Free Item row.
 */
public class YF_IndividualScheme_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// the heading written above a box: the nearest visible text above it that overlaps its column
	static final String HEADER_JS = "var box = arguments[0], br = box.getBoundingClientRect(), best = null, bestTop = -1e9;"
			+ " document.body.querySelectorAll('*').forEach(function (e) {"
			+ "   if (e === box || e.contains(box) || box.contains(e)) { return; }"
			+ "   var r = e.getBoundingClientRect(); if (r.width === 0 || r.height === 0) { return; }"
			+ "   if (r.top >= br.top || br.top - r.bottom > 140 || r.left >= br.right || r.right <= br.left) { return; }"
			+ "   var own = ''; for (var c = e.firstChild; c; c = c.nextSibling) { if (c.nodeType === 3) { own += c.nodeValue; } }"
			+ "   own = own.replace(/\\s+/g, ' ').trim(); if (!own) { return; }"
			+ "   if (r.top > bestTop) { best = own; bestTop = r.top; } });"
			+ " return best === null ? '' : best;";

	// the value boxes in the same line as a search box (left to right): visible, not locked, not check boxes, not search boxes
	static final String ROW_BOXES_JS = "var box = arguments[0], br = box.getBoundingClientRect(), cy = (br.top + br.bottom) / 2, out = [];"
			+ " document.querySelectorAll('input').forEach(function (e) {"
			+ "   if (e === box || e.type === 'hidden' || e.type === 'checkbox' || e.type === 'radio' || e.disabled || e.readOnly) { return; }"
			+ "   if ((e.placeholder || '').indexOf('Search') >= 0) { return; }"
			+ "   var r = e.getBoundingClientRect(); if (r.width === 0 || r.height === 0) { return; }"
			+ "   if (Math.abs((r.top + r.bottom) / 2 - cy) < 25) { out.push(e); } });"
			+ " out.sort(function (a, b) { return a.getBoundingClientRect().left - b.getBoundingClientRect().left; });"
			+ " return out;";

	// the suggestion row of a search box is looked for on the screen: the visible element whose own text contains the typed name,
	// below the box (or just above it) in the box's column, the nearest first; an exact text wins over a longer one
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

	// ---------- Constructor ----------
	public YF_IndividualScheme_Cls(WebDriver driver) {
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

	public List<String> options_by_label(String label) {
		return options_of(label_select(label));
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

	public void Click_cancel() {
		click_safely(first_displayed(By.xpath("//*[self::button or self::a][normalize-space()='Cancel' or normalize-space()='Back']")));
	}

	// the table filter "Search:" at the top right of the list (it searches every page of the table); null when there is none
	private WebElement table_filter() {
		List<WebElement> f = displayed(By.xpath("//div[contains(@class,'dataTables_filter')]//input"
				+ " | //label[normalize-space()='Search:']//input | //label[normalize-space()='Search:']/following::input[1]"));
		return f.isEmpty() ? null : f.get(0);
	}

	// the first Search button (top left) opens / closes the search form of the list
	private void open_search_form() {
		if (displayed(By.xpath(label_xpath("Scheme Code"))).isEmpty()) {
			click_safely(first_displayed(By.xpath("(//*[self::button or self::a][normalize-space()='Search'])[1]")));
			pause(500);
		}
	}

	// shows only the rows of a scheme code: the table filter, or (without one) the search form of the list
	private void filter_by_code(String code) {
		WebElement filter = table_filter();
		if (filter != null) {
			type(filter, code);
			pause(900);
			return;
		}
		open_search_form();
		type(label_input("Scheme Code"), code);
		click_safely(first_displayed(By.xpath("(//*[self::button or self::a][normalize-space()='Search'])[last()]")));
		pause(1500);
	}

	// true when the listing holds a scheme with this code
	public boolean scheme_exists(String code) {
		filter_by_code(code);
		wait.until(d -> !displayed(By.xpath("//table//tbody/tr")).isEmpty());
		return !listing_row(code).isEmpty();
	}

	// the cells of the row of a scheme code in the listing (empty when it is not listed)
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

	public void Select_base_type(String text) {
		select_text(label_select("Promotion Base Type"), text);
		pause(600);
	}

	// chooses the Scheme Applicable Type; for "Each" / "Multiple" the form may show one more drop down (Amount / Quantity): it is
	// chosen with 'sub_type' and its text is returned ("" when no new drop down appeared)
	public String Select_applicable_type(String text, String sub_type) {
		js().executeScript("document.querySelectorAll('select').forEach(function (s) { s.setAttribute('data-known', '1'); });");
		select_text(label_select("Scheme Applicable Type"), text);
		pause(800);
		if (sub_type == null || sub_type.isEmpty()) {
			return "";
		}
		for (WebElement s : displayed(By.xpath("//select[not(@data-known)]"))) {
			for (String o : options_of(s)) {
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
		pause(1000);
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

	// ======================= create form: the rows =======================

	// the search boxes of the form, top to bottom: one per row
	public List<WebElement> search_boxes() {
		return displayed(By.xpath("//input[contains(@placeholder,'Search')]"));
	}

	public String header_of(WebElement box) {
		return String.valueOf(js().executeScript(HEADER_JS, box));
	}

	// the key of a value box: its placeholder and the heading above it (lower case)
	private String key_of(WebElement box) {
		return ((box.getAttribute("placeholder") == null ? "" : box.getAttribute("placeholder")) + " " + header_of(box)).toLowerCase();
	}

	@SuppressWarnings("unchecked")
	private List<WebElement> row_boxes(WebElement search) {
		return (List<WebElement>) js().executeScript(ROW_BOXES_JS, search);
	}

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

	// picks 'text'; when the dropdown has no such row and 'alternative' is given (the code of a menu card), that is tried
	private String pick(WebElement field, String text, String alternative) {
		try {
			return search_pick(field, text);
		} catch (NoSuchElementException e) {
			if (alternative == null) {
				throw e;
			}
			return search_pick(field, alternative);
		}
	}

	private static boolean has_word(String key, String... words) {
		for (String w : words) {
			if (key.contains(w)) {
				return true;
			}
		}
		return false;
	}

	// Fills every row of the form and returns one report line per action (a line that starts with WARNING is a box that got nothing).
	//   promotion rows: the item / category / menu card (item_text, or item_alt when the first text is not in the dropdown), then
	//     Min and Max, the Per Unit box (when per_unit is given), and the value box whose key has one of promo_words
	//   Free Item rows: the free item and its Qty box (value)
	public List<String> Fill_rows(String item_text, String item_alt, String min, String max, String[] promo_words, String value,
			String per_unit, String free_item) {
		List<String> report = new ArrayList<String>();
		int count = search_boxes().size();
		if (count == 0) {
			throw new NoSuchElementException("The form shows no search box for the item / category / menu card after Applicable For was chosen");
		}
		for (int i = 0; i < count; i++) {
			WebElement search = search_boxes().get(i);
			String header = header_of(search);
			boolean free = header.toLowerCase().contains("free");
			String clicked = free ? search_pick(search, free_item) : pick(search, item_text, item_alt);
			report.add("[" + header + "] dropdown row clicked: " + clicked);
			search = search_boxes().get(i);
			for (WebElement box : row_boxes(search)) {
				String key = key_of(box).trim();
				String what = null;
				String put = null;
				if (free) {
					if (has_word(key, "qty", "quantity", "weight")) {
						put = value;
						what = "free Qty";
					}
				} else if (key.contains("min")) {
					put = min;
					what = "Min";
				} else if (key.contains("max")) {
					put = max;
					what = "Max";
				} else if (per_unit != null && key.contains("unit")) {
					put = per_unit;
					what = "Per Unit";
				} else if (has_word(key, promo_words)) {
					put = value;
					what = "value";
				}
				if (put == null) {
					report.add("WARNING [" + header + "] the box '" + key + "' got nothing (no rule for it)");
				} else {
					type(box, put);
					report.add("[" + header + "] " + what + " " + put + " -> '" + key + "'");
				}
			}
		}
		return report;
	}

	// ======================= what the form shows =======================

	// one line per visible box of the form: heading | kind | placeholder | value | options (a drop down) | locked
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
