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
 * Page class of Field Management > OffTakeReport (a LIST report, logged in as the web user).
 *
 * Top: Search button (opens the search form), the table filter "Search:" and "Show N entries".
 * Search form: Salesman (search box), Customer (drop down), From Date, To Date (dd/MM/yyyy, both start on today),
 * Search and Clear.
 * Result table columns (cell index): 0 salesman, 1 date, 2 customer, 3 no. of items, 4 action
 * (eye = View Details, picture icon = photos).
 * "Item Details" pop-up: Item Name, Last Offtake Qty, Purchase Qty, Sales Return Qty, Current Qty, Net Sale Qty.
 * "Off-Take Photos" pop-up: title, caption "customer (date)", big picture, controls (zoom in, zoom out, fit,
 * rotate, counter "1 / 3"), previous / next arrows and thumbnails.
 * Typing: an empty field is typed into directly, without clear().
 */
public class V_OffTakeReport_Cls {

	WebDriver driver;
	WebDriverWait wait;

	static final String SUGGESTIONS = "//ul[contains(@class,'ui-autocomplete')]//li"
			+ " | //li[contains(@class,'ui-menu-item')]"
			+ " | //div[contains(@class,'autocomplete')]//*[self::li or self::div][normalize-space()!='' and not(*)]"
			+ " | //*[contains(@class,'suggest')]//*[self::li or self::div or self::a][normalize-space()!='' and not(*)]"
			+ " | //ul[contains(@class,'list-group')]//li"
			+ " | //ul[contains(@class,'dropdown-menu') and contains(@class,'show')]//li";

	static final String MODAL_SHOWN = "//div[contains(@class,'modal') and contains(@class,'show')]";
	static final String ITEM_MODAL = MODAL_SHOWN + "[.//*[contains(normalize-space(text()),'Item Details')]]";
	// the title has an icon before its text, so the whole text of the pop-up is searched
	static final String PHOTO_MODAL = "//div[@id='offTakeGalleryDrawer'][contains(@class,'open')]";

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
	public V_OffTakeReport_Cls(WebDriver driver) {
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
		pause(800);
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

	// types into a search box; returns the suggestions that are displayed (nothing is picked)
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

	public String entries_text() {
		try {
			return Entries_info.getText().trim();
		} catch (Exception e) {
			return "";
		}
	}

	// the total of "Showing 1 to 2 of 2 entries" (0 when it cannot be read)
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

	// the "Show N entries" drop down
	public WebElement verify_page_length() {
		return first_displayed(By.xpath("//div[contains(@class,'dataTables_length')]//select | //select[contains(@name,'length')]"
				+ " | //label[contains(normalize-space(),'Show')]//select"));
	}

	public List<String> page_length_options() {
		List<String> out = new ArrayList<String>();
		for (WebElement o : new Select(verify_page_length()).getOptions()) {
			out.add(o.getText().trim());
		}
		return out;
	}

	public void Set_page_length(String value) {
		new Select(verify_page_length()).selectByVisibleText(value);
		pause(1200);
	}

	public String page_length_selected() {
		return selected(verify_page_length());
	}

	public boolean is_list_page() {
		try {
			return Top_search_btn.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	// ======================= search form =======================

	public WebElement verify_salesman() {
		return label_input("Salesman");
	}

	public WebElement verify_customer() {
		return label_select("Customer");
	}

	public WebElement verify_from() {
		return label_input("From Date");
	}

	public WebElement verify_to() {
		return label_input("To Date");
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public WebElement verify_clear() {
		return Clear_btn;
	}

	public boolean search_form_open() {
		return !displayed(By.xpath(label_xpath("Salesman"))).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			click_safely(Top_search_btn);
			pause(800);
		}
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches
	public void Search_report(String salesman, String customer, String from, String to) {
		Open_search_form();
		if (!salesman.isEmpty()) {
			search_pick(verify_salesman(), salesman);
		}
		if (!customer.isEmpty()) {
			select_text(verify_customer(), customer);
			pause(500);
		}
		if (!from.isEmpty()) {
			set_date(verify_from(), from);
		}
		if (!to.isEmpty()) {
			set_date(verify_to(), to);
		}
		click_safely(Search_go);
		pause(3000);
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
			String t = th.getText().trim().toUpperCase().replaceAll("\\s+", " ");
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	private List<WebElement> result_rows() {
		List<WebElement> rows = new ArrayList<WebElement>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td][not(ancestor::div[contains(@class,'modal')])]"))) {
			try {
				if (row.isDisplayed() && row.findElements(By.tagName("td")).size() >= 5
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

	// the cell texts of every result row: [salesman, date, customer, no. of items, action]
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

	private List<WebElement> action_icons(int row) {
		List<WebElement> tds = result_rows().get(row).findElements(By.tagName("td"));
		return tds.get(tds.size() - 1).findElements(By.xpath(".//a | .//button | .//span[@onclick]"
				+ " | .//i[not(ancestor::a) and not(ancestor::button) and not(ancestor::span[@onclick])]"));
	}

	private String html_of(WebElement e) {
		try {
			return String.valueOf(((JavascriptExecutor) driver).executeScript(
					"return ((arguments[0].outerHTML || '') + ' ' + (arguments[0].title || '')).toLowerCase();", e));
		} catch (StaleElementReferenceException ex) {
			return "";
		}
	}

	private String kind_of(WebElement icon) {
		String h = html_of(icon);
		// the photo icon is checked first: its title "view images (3)" also contains the word view
		if (h.contains("fa-image") || h.contains("gallery") || h.contains("images") || h.contains("photo")
				|| h.contains("picture") || h.contains("camera")) {
			return "photos";
		}
		if (h.contains("eye") || h.contains("view")) {
			return "view";
		}
		return "other";
	}

	// the title of the photo icon of a row ("view images (3)"), "" when the row has none
	public String photo_icon_title(int row) {
		for (WebElement icon : action_icons(row)) {
			if (kind_of(icon).equals("photos")) {
				String t = icon.getAttribute("title");
				return t == null ? "" : t;
			}
		}
		return "";
	}

	// the customer options of the search form (without the placeholder)
	public List<String> customer_options() {
		Open_search_form();
		return options_of(verify_customer());
	}

	// the kinds of the action icons of a row (by index from 0): view, photos
	public List<String> actions_of(int row) {
		List<String> kinds = new ArrayList<String>();
		for (WebElement icon : action_icons(row)) {
			kinds.add(kind_of(icon));
		}
		return kinds;
	}

	public String actions_html(int row) {
		StringBuilder sb = new StringBuilder();
		for (WebElement icon : action_icons(row)) {
			sb.append(html_of(icon).replaceAll("\\s+", " ")).append(" || ");
		}
		return sb.toString();
	}

	private void click_action(int row, String kind) {
		for (WebElement icon : action_icons(row)) {
			if (kind_of(icon).equals(kind)) {
				js_click(icon);
				pause(1200);
				return;
			}
		}
		throw new NoSuchElementException("The row " + (row + 1) + " has no " + kind + " action");
	}

	public void Click_view(int row) {
		click_action(row, "view");
	}

	public void Click_photos(int row) {
		click_action(row, "photos");
	}

	// ======================= pop-ups =======================

	// closes any open pop-up with its x (or Escape, or by force)
	public void Close_modal() {
		// the photo viewer is a side drawer, not a pop-up: it has its own close button
		List<WebElement> drawer_close = displayed(By.xpath("//div[@id='offTakeGalleryDrawer'][contains(@class,'open')]"
				+ "//button[contains(@onclick,'closeOffTakeGallery')]"));
		if (!drawer_close.isEmpty()) {
			click_safely(drawer_close.get(0));
			pause(600);
		}
		for (int round = 0; round < 3; round++) {
			List<WebElement> modals = displayed(By.xpath(MODAL_SHOWN));
			if (modals.isEmpty()) {
				return;
			}
			List<WebElement> x = modals.get(0).findElements(By.xpath(".//button[contains(@class,'close') or contains(@class,'btn-close')"
					+ " or normalize-space()='×' or @data-bs-dismiss='modal' or @data-dismiss='modal' or @aria-label='Close']"));
			boolean clicked = false;
			for (WebElement b : x) {
				if (b.isDisplayed()) {
					click_safely(b);
					clicked = true;
					break;
				}
			}
			if (!clicked) {
				driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
			}
			pause(1000);
		}
		if (!displayed(By.xpath(MODAL_SHOWN)).isEmpty()) {
			((JavascriptExecutor) driver).executeScript(
					"document.querySelectorAll('.modal.show').forEach(function(m){ m.classList.remove('show'); m.style.display='none'; });"
					+ " document.querySelectorAll('.modal-backdrop').forEach(function(b){ b.remove(); });"
					+ " document.body.classList.remove('modal-open'); document.body.style.overflow='';");
			pause(500);
		}
	}

	public boolean modal_open() {
		return !displayed(By.xpath(MODAL_SHOWN)).isEmpty() || !displayed(By.xpath(PHOTO_MODAL)).isEmpty();
	}

	// closes the open pop-up with the Escape key only
	public void Close_modal_with_escape() {
		driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
		pause(1000);
	}

	// ---- Item Details ----

	public boolean item_modal_open() {
		return !displayed(By.xpath(ITEM_MODAL)).isEmpty();
	}

	public List<String> item_headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : displayed(By.xpath(ITEM_MODAL + "//table//th"))) {
			out.add(th.getText().trim().toUpperCase().replaceAll("\\s+", " "));
		}
		return out;
	}

	// the cell texts of every item row of the Item Details pop-up
	public List<List<String>> item_rows() {
		List<List<String>> out = new ArrayList<List<String>>();
		for (WebElement row : displayed(By.xpath(ITEM_MODAL + "//table//tbody/tr[td]"))) {
			try {
				if (row.getText().toLowerCase().contains("no data")) {
					continue;
				}
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

	// ---- Off-Take Photos ----

	public boolean photo_modal_open() {
		return !displayed(By.xpath(PHOTO_MODAL)).isEmpty();
	}

	public String photo_modal_text() {
		List<WebElement> m = displayed(By.xpath(PHOTO_MODAL));
		return m.isEmpty() ? "" : m.get(0).getText().trim().replaceAll("\\s+", " ");
	}

	// the line under the title: "CUSTOMER (dd/MM/yyyy)"
	public String photo_caption() {
		List<WebElement> found = displayed(By.xpath(PHOTO_MODAL + "//*[not(*)][contains(text(),'/20')]"));
		return found.isEmpty() ? "" : found.get(0).getText().trim();
	}

	// the position text "1 / 3" -> {1, 3}; {0, 0} when it is not shown
	public int[] photo_position() {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(photo_modal_text().replaceAll("\\d{2}/\\d{2}/\\d{4}", ""));
		return m.find() ? new int[] { Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) } : new int[] { 0, 0 };
	}

	private List<WebElement> photo_buttons() {
		return displayed(By.xpath(PHOTO_MODAL + "//button | " + PHOTO_MODAL + "//a[contains(@class,'btn') or @role='button']"
				+ " | " + PHOTO_MODAL + "//*[@onclick]"));
	}

	// finds a control of the viewer by a word in its HTML (right/next, left/prev, zoom-in, zoom-out, rotate, expand)
	private WebElement photo_control(String... words) {
		for (WebElement b : photo_buttons()) {
			String h = html_of(b);
			for (String w : words) {
				if (h.contains(w)) {
					return b;
				}
			}
		}
		return null;
	}

	public boolean photo_has_control(String... words) {
		return photo_control(words) != null;
	}

	public void Click_photo_control(String... words) {
		WebElement b = photo_control(words);
		if (b == null) {
			throw new NoSuchElementException("The photo viewer has no control: " + String.join("/", words));
		}
		js_click(b);
		pause(800);
	}

	public void Photo_next() {
		Click_photo_control("chevron-right", "angle-right", "arrow-right", "fa-next", "next", "right");
	}

	public void Photo_previous() {
		Click_photo_control("chevron-left", "angle-left", "arrow-left", "prev", "left");
	}

	// the thumbnails under the big picture
	private List<WebElement> photo_thumbs() {
		List<WebElement> out = new ArrayList<WebElement>();
		for (WebElement img : displayed(By.xpath(PHOTO_MODAL + "//img"))) {
			try {
				double w = ((Number) ((JavascriptExecutor) driver).executeScript("return arguments[0].getBoundingClientRect().width;", img)).doubleValue();
				if (w > 0 && w < 150) {
					out.add(img);
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		return out;
	}

	public int photo_thumb_count() {
		return photo_thumbs().size();
	}

	public void Click_thumb(int index) {
		js_click(photo_thumbs().get(index));
		pause(800);
	}

	// the big picture: the widest image of the pop-up
	private WebElement main_image() {
		WebElement best = null;
		double bestWidth = 0;
		for (WebElement img : displayed(By.xpath(PHOTO_MODAL + "//img"))) {
			try {
				double w = ((Number) ((JavascriptExecutor) driver).executeScript("return arguments[0].getBoundingClientRect().width;", img)).doubleValue();
				if (w > bestWidth) {
					bestWidth = w;
					best = img;
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		if (best == null) {
			throw new NoSuchElementException("The big picture of the photo viewer was not found");
		}
		return best;
	}

	public String main_image_src() {
		return String.valueOf(main_image().getAttribute("src"));
	}

	// the shown width of the big picture (changes with zoom)
	public double main_image_width() {
		return ((Number) ((JavascriptExecutor) driver).executeScript("return arguments[0].getBoundingClientRect().width;", main_image())).doubleValue();
	}

	// the CSS transform of the big picture (and of its parent) - changes with zoom and rotate
	public String main_image_transform() {
		return String.valueOf(((JavascriptExecutor) driver).executeScript(
				"var e = arguments[0]; return window.getComputedStyle(e).transform + ' | ' + (e.parentElement ? window.getComputedStyle(e.parentElement).transform : '');",
				main_image()));
	}

	// true when the big picture really loaded (a broken image has no natural width)
	public boolean main_image_loaded() {
		return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript(
				"return arguments[0].complete && arguments[0].naturalWidth > 0;", main_image()));
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
