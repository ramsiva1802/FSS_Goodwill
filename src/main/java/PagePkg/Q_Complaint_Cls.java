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
 * Page class of Field Management > Complaint.
 *
 * The page has two screens:
 *   1. LIST screen : Search button (opens the search form: hierarchy type, hierarchy name, customer name, status),
 *                    table filter, result table (complaint number, complaint type, complaint date, customer name,
 *                    salesman name, complaint status and a view, an edit and a print icon per row), paging.
 *                    There is no Create button: complaints come from the mobile app.
 *   2. FORM screen : view (everything greyed out, only Back) / edit (Processing Remark and Complaint Status can be
 *                    changed, Back and Submit). A tab with the complaint type, Complaint No, Complaint Type,
 *                    Complaint Date, Customer ("N/A" without a customer), Salesman, Complaint Description, Audio,
 *                    Uploaded File, Upload Image (a slider; a click opens the "Complaint Photos" pop-up),
 *                    Complaint Status (drop down) and Processing Remark.
 * Typing: an empty field is typed into directly, without clear().
 */
public class Q_Complaint_Cls {

	WebDriver driver;
	WebDriverWait wait;

	static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	static final String LOWER = "abcdefghijklmnopqrstuvwxyz";

	// the suggestion lines of a search box
	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

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

	// ---------- FORM screen buttons: always the VISIBLE one ----------
	static final By SUBMIT = By.xpath("//*[self::button or self::a][normalize-space()='Submit']"
			+ "[not(ancestor::div[contains(@class,'modal')])]");
	static final By BACK = By.xpath("//*[self::button or self::a][normalize-space()='Back']");

	// ---------- Constructor ----------
	public Q_Complaint_Cls(WebDriver driver) {
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
		wait.until(d -> !select.getOptions().isEmpty());
		for (int i = 0; i < select.getOptions().size(); i++) {
			String option = select.getOptions().get(i).getText().trim().toLowerCase();
			if (option.equals(text.trim().toLowerCase()) || option.startsWith(text.trim().toLowerCase())) {
				select.selectByIndex(i);
				return;
			}
		}
		throw new NoSuchElementException("Option not found in the drop down: " + text);
	}

	// ALL the option texts of a drop down (the first option is a real value here, for example ALL or Factory)
	public List<String> all_options_of(WebElement dropdown) {
		List<String> texts = new ArrayList<String>();
		for (WebElement o : new Select(dropdown).getOptions()) {
			String t = o.getText().trim();
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

	private void pause(int millis) {
		WaitUtil.settle(driver, millis);
	}

	// a label found with capital letters ignored (the page shows its labels in capitals through its style)
	private static String label_ci(String text) {
		return "//label[translate(normalize-space(),'" + UPPER + "','" + LOWER + "')='" + text.toLowerCase() + "']";
	}

	private WebElement label_input(String label) {
		return first_displayed(By.xpath(label_ci(label) + "/following::input[not(@type='hidden')][1]"));
	}

	private WebElement label_textarea(String label) {
		return first_displayed(By.xpath(label_ci(label) + "/following::textarea[1]"));
	}

	private WebElement label_select(String label) {
		return first_displayed(By.xpath(label_ci(label) + "/following::select[1]"));
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

	// true when the field cannot be changed: disabled, read-only, or locked by its style / a locked parent
	public boolean is_locked(WebElement element) {
		Object result = ((JavascriptExecutor) driver).executeScript("var e = arguments[0];"
				+ " var s = window.getComputedStyle(e);"
				+ " return e.disabled === true || e.readOnly === true || s.pointerEvents === 'none'"
				+ " || e.hasAttribute('disabled') || e.hasAttribute('readonly')"
				+ " || e.classList.contains('disabled') || e.classList.contains('readonly')"
				+ " || e.closest('.disabled, [disabled], .readonly') !== null;", element);
		return Boolean.TRUE.equals(result);
	}
 
	// a form control found by the text of its label, whatever tag the label is in: the first visible element with
	// that text (capital letters, ':' and '*' ignored), then the first control that follows it in the page
	private WebElement js_field(String label, String selector) {
		wait.until(d -> find_field(label, selector) != null);
		return find_field(label, selector);
	}

	private WebElement find_field(String label, String selector) {
		Object found = ((JavascriptExecutor) driver).executeScript(
				"var want = arguments[0].toLowerCase(); var sel = arguments[1];"
				+ " var all = document.querySelectorAll('label, div, span, p, b, strong, h6, h5, small, legend');"
				+ " var lab = null;"
				+ " for (var i = 0; i < all.length; i++) {"
				+ "   var e = all[i]; if (e.offsetParent === null) { continue; }"
				+ "   if (e.children.length > 1) { continue; }"
				+ "   var t = (e.textContent || '').replace(/\\s+/g, ' ').replace(/[:*.]/g, '').trim().toLowerCase();"
				+ "   if (t === want) { lab = e; break; } }"
				+ " if (!lab) { return null; }"
				+ " var ctrls = document.querySelectorAll(sel);"
				+ " for (var j = 0; j < ctrls.length; j++) {"
				+ "   if (lab.compareDocumentPosition(ctrls[j]) & Node.DOCUMENT_POSITION_FOLLOWING) { return ctrls[j]; } }"
				+ " return null;", label, selector);
		return (WebElement) found;
	}

	// finds the "Complaint Photos" pop-up (not a standard modal) and gives its root element to the script
	static final String PHOTO_JS = "function container() {"
			+ " var all = document.querySelectorAll('h1, h2, h3, h4, h5, h6, div, span, p, b, strong');"
			+ " for (var i = 0; i < all.length; i++) {"
			+ "   var e = all[i]; var own = '';"
			+ "   for (var k = 0; k < e.childNodes.length; k++) { if (e.childNodes[k].nodeType === 3) { own += e.childNodes[k].textContent; } }"
			+ "   if (own.indexOf('Complaint Photos') >= 0 && e.offsetParent !== null) {"
			+ "     var p = e.parentElement;"
			+ "     while (p) { if (p.querySelector('img') && /\\d+ \\/ \\d+/.test(p.textContent.replace(/\\s+/g, ' '))) { return p; }"
			+ "       p = p.parentElement; } } }"
			+ "   return null; } ";
	// ======================= LIST screen =======================

	public WebElement verify_top_search() {
		return Top_search_btn;
	}

	public WebElement verify_entries() {
		return Entries_info;
	}

	public void Click_next() {
		click_safely(Next_btn);
	}

	public boolean next_enabled() {
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Next']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	public boolean previous_enabled() {
		return driver.findElements(By.xpath(
				"//*[normalize-space(text())='Previous']/ancestor-or-self::*[contains(@class,'disabled')]")).isEmpty();
	}

	// the "Search:" box above the table
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

	// the form screen (view or edit) is the one with a Back button
	public boolean is_form_screen() {
		try {
			return !displayed(BACK).isEmpty();
		} catch (Exception e) {
			return false;
		}
	}

	public boolean submit_visible() {
		try {
			return !displayed(SUBMIT).isEmpty();
		} catch (Exception e) {
			return false;
		}
	}

	// ======================= search form =======================

	public WebElement verify_type() {
		return label_select("Hierarchy Type");
	}

	// the hierarchy name box (labelled with the chosen hierarchy type, for example "Factory"): the text box after
	// the Hierarchy Type drop down
	public WebElement verify_hierarchy() {
		return first_displayed(By.xpath(label_ci("Hierarchy Type") + "/following::input[not(@type='hidden')][1]"));
	}

	public WebElement verify_customer() {
		return label_input("Customer Name");
	}

	public WebElement verify_status() {
		return label_select("Status");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public boolean search_form_open() {
		return !displayed(By.xpath(label_ci("Hierarchy Type"))).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			click_safely(Top_search_btn);
			pause(800);
		}
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches
	public void Search_complaints(String type, String hierarchy, String customer, String status) {
		Open_search_form();
		if (!type.isEmpty()) {
			select_text(verify_type(), type);
			pause(800);
		}
		if (!hierarchy.isEmpty()) {
			search_pick(verify_hierarchy(), hierarchy);
		}
		if (!customer.isEmpty()) {
			search_pick(verify_customer(), customer);
		}
		if (!status.isEmpty()) {
			select_text(verify_status(), status);
		}
		click_safely(Search_go);
		pause(2500);
	}

	// ======================= result table =======================

	// the table header texts in capital letters (line breaks turned into spaces)
	public List<String> headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : displayed(By.xpath("//table//th"))) {
			out.add(th.getText().trim().toUpperCase().replaceAll("\\s+", " "));
		}
		return out;
	}

	// the data rows of the result table (a "No data" row is not counted)
	private List<WebElement> result_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() > 3
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

	// the cell texts of every result row (line breaks turned into spaces): [complaint number, complaint type,
	// complaint date, customer name, salesman name, complaint status, action]
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

	// the cells of the row of one complaint number (null when it is not in the table)
	public List<String> row_of(String number) {
		for (List<String> cells : result_texts()) {
			if (cells.size() > 5 && cells.get(0).equalsIgnoreCase(number)) {
				return cells;
			}
		}
		return null;
	}

	// the view, edit and print icons of one row (links first, otherwise the icons themselves)
	private List<WebElement> action_icons(String number) {
		for (WebElement row : result_rows()) {
			if (row.findElements(By.tagName("td")).get(0).getText().trim().equalsIgnoreCase(number)) {
				List<WebElement> icons = row.findElements(By.xpath(".//td[last()]//a | .//td[last()]//button"));
				if (icons.size() < 3) {
					icons = row.findElements(By.xpath(".//td[last()]//i"));
				}
				return icons;
			}
		}
		throw new NoSuchElementException("Complaint not found in the list: " + number);
	}

	public int action_icon_count(String number) {
		return action_icons(number).size();
	}

	public void Click_view(String number) {
		click_safely(action_icons(number).get(0));
	}

	public void Click_edit(String number) {
		click_safely(action_icons(number).get(1));
	}

	// presses the print icon of a row. window.print is replaced first, so the browser print preview (which Selenium
	// cannot control) does not open. Returns what happened: whether print was called and the windows now open.
	public String Click_print(String number) {
		((JavascriptExecutor) driver).executeScript("window.__printCalled = false; window.print = function() { window.__printCalled = true; };");
		String main = driver.getWindowHandle();
		Set<String> before = driver.getWindowHandles();
		click_safely(action_icons(number).get(2));
		pause(2500);
		Object called = ((JavascriptExecutor) driver).executeScript("return window.__printCalled === true;");
		Set<String> after = driver.getWindowHandles();
		String info = "print called in the page: " + called + ", windows: " + before.size() + " -> " + after.size();
		for (String handle : after) {
			if (!before.contains(handle)) {
				driver.switchTo().window(handle);
				info = info + ", new window text: "
						+ String.valueOf(driver.findElement(By.tagName("body")).getText()).replaceAll("\\s+", " ");
				driver.close();
			}
		}
		driver.switchTo().window(main);
		return info;
	}

	// ======================= FORM screen (view / edit) =======================

	// the text of the tab at the top of the form ("Board Complaint")
	public String tab_text() {
		List<WebElement> tabs = displayed(By.xpath("//*[contains(@class,'nav-tabs') or contains(@class,'nav-pills')]"
				+ "//*[self::a or self::button]"));
		if (tabs.isEmpty()) {
			tabs = displayed(By.xpath("//button[contains(normalize-space(),'Complaint')]"));
		}
		return tabs.isEmpty() ? "" : tabs.get(0).getText().trim();
	}

	public WebElement verify_number() {
		wait.until(d -> number_field() != null);
		return number_field();
	}

	// the Complaint No box: found by its label, or by its value (a complaint number such as 26CGPGPP1000000002)
	private WebElement number_field() {
		WebElement f = find_field("Complaint No", "input:not([type=hidden])");
		if (f == null) {
			f = find_field("Complaint Number", "input:not([type=hidden])");
		}
		if (f == null) {
			f = (WebElement) ((JavascriptExecutor) driver).executeScript(
					"var list = document.querySelectorAll('input');"
					+ " for (var i = 0; i < list.length; i++) {"
					+ "   if (list[i].offsetParent !== null && /^\\d{2}[A-Z]+\\d+$/.test(list[i].value)) { return list[i]; } }"
					+ " return null;");
		}
		return f;
	}

	public WebElement verify_complaint_type() {
		return js_field("Complaint Type", "input:not([type=hidden])");
	}

	public WebElement verify_complaint_date() {
		return js_field("Complaint Date", "input:not([type=hidden])");
	}

	public WebElement verify_customer_field() {
		return js_field("Customer", "input:not([type=hidden])");
	}

	public WebElement verify_salesman() {
		return js_field("Salesman", "input:not([type=hidden])");
	}

	public WebElement verify_description() {
		return js_field("Complaint Description", "textarea");
	}

	public WebElement verify_remark() {
		return js_field("Processing Remark", "textarea");
	}

	public WebElement verify_complaint_status() {
		return js_field("Complaint Status", "select");
	}

	public String remark() {
		String v = verify_remark().getAttribute("value");
		return v == null ? "" : v;
	}

	// replaces the Processing Remark (edit screen): typed like a user for normal texts, set with JavaScript for long ones
	public void Set_remark(String text) {
		WebElement field = verify_remark();
		field.click();
		field.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
		if (text.isEmpty()) {
			return;
		}
		if (text.length() <= 300) {
			field.sendKeys(text);
		} else {
			((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];"
					+ " arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
					+ " arguments[0].dispatchEvent(new Event('change', {bubbles: true}));", field, text);
		}
	}

	// tries to choose another complaint status. Returns "locked: ..", "unchanged" or "changed from X to Y"
	public String Try_change_status() {
		Select select = new Select(verify_complaint_status());
		String before = select.getFirstSelectedOption().getText().trim();
		try {
			List<WebElement> options = select.getOptions();
			for (int i = 0; i < options.size(); i++) {
				if (!options.get(i).getText().trim().equals(before)) {
					select.selectByIndex(i);
					break;
				}
			}
		} catch (Exception e) {
			return "locked: " + e.getClass().getSimpleName();
		}
		String after = new Select(verify_complaint_status()).getFirstSelectedOption().getText().trim();
		return before.equals(after) ? "unchanged" : "changed from " + before + " to " + after;
	}

	public void Select_complaint_status(String status) {
		select_text(verify_complaint_status(), status);
	}

	public WebElement verify_submit() {
		return first_displayed(SUBMIT);
	}

	public WebElement verify_back() {
		return first_displayed(BACK);
	}

	public void Click_submit() {
		click_safely(first_displayed(SUBMIT));
	}

	public void Click_back() {
		click_safely(first_displayed(BACK));
	}

	// ---- audio and files ----
	public int audio_count() {
		return driver.findElements(By.tagName("audio")).size();
	}

	// the download links / icons next to the audio players
	public int download_count() {
		return displayed(By.xpath("//a[@download or contains(@href,'ownload')] | //*[contains(@class,'download')]")).size();
	}

	// ---- photos ----
	// the pictures of the Upload Image slider
	private List<WebElement> slider_images() {
		return displayed(By.xpath("//*[translate(normalize-space(text()),'" + UPPER + "','" + LOWER
				+ "')='upload image']/following::img[1]"));
	}

	public boolean has_photos() {
		return !slider_images().isEmpty();
	}

	// clicks the first picture of the slider and waits for the "Complaint Photos" pop-up
	public void Open_photos() {
		List<WebElement> images = slider_images();
		if (images.isEmpty()) {
			throw new NoSuchElementException("The complaint has no picture");
		}
		click_safely(images.get(0));
		first_displayed(By.xpath("//*[contains(normalize-space(text()),'Complaint Photos')]"));
		pause(1000);
	}

	public boolean photos_open() {
		return !displayed(By.xpath("//*[contains(normalize-space(text()),'Complaint Photos')]")).isEmpty();
	}

	// the "1 / 3" counter of the pop-up as {1, 3} ({0, 0} when it cannot be read)
	public int[] photo_counter() {
		Object text = ((JavascriptExecutor) driver).executeScript(PHOTO_JS
				+ " var c = container(); if (!c) { return '0/0'; }"
				+ " var m = c.textContent.replace(/\\s+/g, ' ').match(/(\\d+) \\/ (\\d+)/);"
				+ " return m ? m[1] + '/' + m[2] : '0/0';");
		String[] parts = String.valueOf(text).split("/");
		return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
	}

	// the number of pictures in the pop-up (the thumbnails and the big picture)
	public int photo_image_count() {
		Object n = ((JavascriptExecutor) driver).executeScript(PHOTO_JS
				+ " var c = container(); return c ? c.querySelectorAll('img').length : 0;");
		return ((Number) n).intValue();
	}

	// moves to the next / previous picture with the arrow of the pop-up (the arrow key when no arrow is found)
	private void photo_move(boolean next) {
		Object clicked = ((JavascriptExecutor) driver).executeScript(PHOTO_JS
				+ " var c = container(); if (!c) { return false; }"
				+ " var re = arguments[0] ? /next|right/i : /prev|left/i;"
				+ " var list = c.querySelectorAll('button, a');"
				+ " for (var i = 0; i < list.length; i++) { var e = list[i];"
				+ "   var s = (e.getAttribute('class') || '') + ' ' + (e.getAttribute('aria-label') || '') + ' '"
				+ "     + (e.getAttribute('title') || '') + ' ' + e.innerHTML;"
				+ "   if (re.test(s) && e.offsetParent !== null) { e.click(); return true; } }"
				+ " return false;", next);
		if (!Boolean.TRUE.equals(clicked)) {
			driver.findElement(By.tagName("body")).sendKeys(next ? Keys.ARROW_RIGHT : Keys.ARROW_LEFT);
		}
		pause(800);
	}

	public void Next_photo() {
		photo_move(true);
	}

	public void Previous_photo() {
		photo_move(false);
	}

	// closes the pop-up with its close control, or the Escape key
	public void Close_photos() {
		((JavascriptExecutor) driver).executeScript(PHOTO_JS
				+ " var c = container(); if (!c) { return; }"
				+ " var list = c.querySelectorAll('button, a, span, div');"
				+ " for (var i = 0; i < list.length; i++) { var e = list[i];"
				+ "   var s = ((e.getAttribute('class') || '') + ' ' + (e.getAttribute('aria-label') || '') + ' '"
				+ "     + (e.getAttribute('title') || '')).toLowerCase(); var t = (e.textContent || '').trim();"
				+ "   if (/close|dismiss/.test(s) || t === '\\u00d7' || t === '\\u2715') { e.click(); return; } }");
		pause(1000);
		if (photos_open()) {
			driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
			pause(800);
		}
	}

	// ======================= messages =======================

	// the red "Please ..." messages
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

	// a pop-up / toast / alert text after Submit (empty when there is none)
	public String notice() {
		try {
			List<WebElement> found = displayed(By.xpath("//div[contains(@class,'swal2-popup') or contains(@class,'toast')"
					+ " or contains(@class,'alert') or contains(@class,'notyf')]"));
			return found.isEmpty() ? "" : found.get(0).getText().trim();
		} catch (StaleElementReferenceException e) {
			return "";
		}
	}

	// accepts a browser alert if one is open and returns its text (empty when there is none)
	public String Accept_alert() {
		try {
			Alert a = driver.switchTo().alert();
			String text = a.getText();
			a.accept();
			return text;
		} catch (NoAlertPresentException e) {
			return "";
		}
	}

	// closes a sweet alert pop-up that is open
	public void Close_notice() {
		List<WebElement> ok = displayed(By.xpath("//button[contains(@class,'swal2-confirm')]"));
		if (!ok.isEmpty()) {
			click_safely(ok.get(0));
		}
	}
}