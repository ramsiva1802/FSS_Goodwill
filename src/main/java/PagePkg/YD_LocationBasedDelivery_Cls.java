package PagePkg;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

/*
 * Page class of Reports > Location-Based Delivery (logged in as the web user). A read only list report.
 *
 * Top: Search button (opens the search form), Download PDF, the table filter "Search:", "Show N entries".
 * Search form: Hierarchy Type (drop down), Supply By (search box), Route (search box), Employee Type (drop down),
 * Salesman (search box), From Date *, To Date * (dd/MM/yyyy; both are today when the screen opens), Search, Clear.
 * Result table (scrolls sideways): SL.NO, ORDER NO, ORDER DATE AND TIME (dd-MM-yyyy hh:mm a), CUSTOMER CODE,
 * CUSTOMER NAME, PLANT LOCATION, DISTANCE (KM), DELIVERY SEQUENCE, ORDER VALUE, ORDER STATUS (a badge: Pending / Approved).
 * Download PDF opens the page LocationBasedDeliveryReportPdfExport in a new tab: the plant, From Date / To Date
 * (yyyy-MM-dd), Generated On, the title LOCATION-BASED DELIVERY REPORT, the same table and a Print button.
 */
public class YD_LocationBasedDelivery_Cls extends TableBase {

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[1]")
	WebElement Top_search_btn;

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[2]")
	WebElement Search_go;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Clear']")
	WebElement Clear_btn;

	@FindBy(xpath = "//*[self::button or self::a][contains(normalize-space(),'Download PDF')]")
	WebElement Pdf_btn;

	public YD_LocationBasedDelivery_Cls(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	public WebElement verify_top_search() {
		return Top_search_btn;
	}

	public WebElement verify_pdf() {
		return Pdf_btn;
	}

	public WebElement verify_clear() {
		return Clear_btn;
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public boolean is_list_page() {
		try {
			return Top_search_btn.isDisplayed() && Pdf_btn.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	// ======================= search form =======================

	public WebElement verify_hierarchy_type() {
		return label_select("Hierarchy Type");
	}

	public WebElement verify_supply_by() {
		return label_input("Supply By");
	}

	public WebElement verify_route() {
		return label_input("Route");
	}

	public WebElement verify_employee_type() {
		return label_select("Employee Type");
	}

	public WebElement verify_salesman() {
		return label_input("Salesman");
	}

	public WebElement verify_from() {
		return label_input("From Date");
	}

	public WebElement verify_to() {
		return label_input("To Date");
	}

	public boolean search_form_open() {
		return !displayed(By.xpath(label_xpath("Employee Type"))).isEmpty();
	}

	public void Open_search_form() {
		if (!search_form_open()) {
			click_safely(Top_search_btn);
			pause(900);
		}
	}

	// opens the search form when it is hidden, fills it (an empty value is left as it is) and searches
	public void Search_report(String hierarchy_type, String supply_by, String route, String employee_type,
			String salesman, String from, String to) {
		Open_search_form();
		if (!hierarchy_type.isEmpty()) {
			select_text(verify_hierarchy_type(), hierarchy_type);
			pause(800);
		}
		if (!supply_by.isEmpty()) {
			search_pick(verify_supply_by(), supply_by);
		}
		if (!route.isEmpty()) {
			search_pick(verify_route(), route);
		}
		if (!employee_type.isEmpty()) {
			select_text(verify_employee_type(), employee_type);
			pause(500);
		}
		if (!salesman.isEmpty()) {
			search_pick(verify_salesman(), salesman);
		}
		if (!from.isEmpty()) {
			set_date(verify_from(), from);
		}
		if (!to.isEmpty()) {
			set_date(verify_to(), to);
		}
		click_safely(Search_go);
		pause(3500);
	}

	// the usual search: the dates only
	public void Search_dates(String from, String to) {
		Search_report("", "", "", "", "", from, to);
	}

	// searches for the dates; when the site answers with a browser alert its text is returned (and the alert is closed)
	public String Search_dates_message(String from, String to) {
		try {
			Search_dates(from, to);
			return "";
		} catch (UnhandledAlertException e) {
			return alert_text_of(e);
		}
	}

	// empties From Date and / or To Date (which = "from", "to" or "both"), searches and returns the alert text (or "")
	public String Search_blank(String which) {
		try {
			Open_search_form();
			if (which.equals("from") || which.equals("both")) {
				type(verify_from(), "");
			}
			if (which.equals("to") || which.equals("both")) {
				type(verify_to(), "");
			}
			click_safely(Search_go);
			pause(2500);
			return "";
		} catch (UnhandledAlertException e) {
			return alert_text_of(e);
		}
	}

	private String alert_text_of(UnhandledAlertException e) {
		String text = e.getAlertText();
		try {
			driver.switchTo().alert().accept();
		} catch (Exception ex) {
			// the alert was already closed
		}
		return text == null ? "" : text;
	}

	public void Click_clear() {
		Open_search_form();
		click_safely(Clear_btn);
		pause(2500);
	}

	// ======================= Download PDF =======================

	// clicks Download PDF; the report page opens in a new tab and that tab is switched to. false = no new tab
	public boolean Click_pdf() {
		Set<String> before = driver.getWindowHandles();
		click_safely(Pdf_btn);
		long end = System.currentTimeMillis() + 12000;
		while (System.currentTimeMillis() < end) {
			Set<String> now = driver.getWindowHandles();
			for (String h : now) {
				if (!before.contains(h)) {
					driver.switchTo().window(h);
					pause(2000);
					return true;
				}
			}
			pause(500);
		}
		return false;
	}

	public String pdf_url() {
		return driver.getCurrentUrl();
	}

	public String pdf_text() {
		return driver.findElement(By.tagName("body")).getText();
	}

	public boolean pdf_has_button(String text) {
		return !displayed(By.xpath("//*[self::button or self::a][normalize-space()='" + text + "']")).isEmpty();
	}

	public List<String> pdf_headers() {
		List<String> out = new ArrayList<String>();
		for (WebElement th : driver.findElements(By.xpath("//table//th"))) {
			out.add(th.getText().trim().replaceAll("\\s+", " ").toUpperCase());
		}
		return out;
	}

	// every row of the PDF table: [Sl.No, Order No, ...] (white space inside a cell, even a break inside an order number, removed for the number)
	public List<List<String>> pdf_rows() {
		List<List<String>> out = new ArrayList<List<String>>();
		for (WebElement row : driver.findElements(By.xpath("//table//tbody/tr[td]"))) {
			List<String> cells = new ArrayList<String>();
			for (WebElement td : row.findElements(By.tagName("td"))) {
				cells.add(td.getAttribute("textContent").trim().replaceAll("\\s+", " "));
			}
			out.add(cells);
		}
		return out;
	}

	// closes the PDF tab and goes back to 'main_tab'
	public void Close_pdf(String main_tab) {
		try {
			for (String h : driver.getWindowHandles()) {
				if (!h.equals(main_tab)) {
					driver.switchTo().window(h);
					driver.close();
				}
			}
		} finally {
			driver.switchTo().window(main_tab);
		}
	}
}
