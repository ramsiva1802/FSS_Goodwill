package PagePkg;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

/*
 * Page class of Reports > Attendance Expense Reports (logged in as the web user). A read only list report.
 *
 * Top: Search button (opens the search form), Export button, the table filter "Search:".
 * Search form: Hierarchy Type (drop down), Hierarchy Name (search box), Employee Type (drop down),
 * Employee Name (search box), From Date, To Date (dd/MM/yyyy; both are today when the screen opens), Search, Clear.
 * Result table (scrolls sideways): DATE, SALES PERSON NAME, DESIGNATION, FACTORY, EXPENSE NAME, IN TIME, OUT TIME,
 * ODO START KM, IMAGE, ODO END KM, IMAGE 2, KM CLAIMED, GPS KM ESTIMATE, RATEPERKM, EXPENSE AMOUNT, PA, POINTS, DA,
 * LEAVE REQUESTED (Y/N).
 * Only EXPENSES THAT WERE APPROVED on the Expense Approval screen are listed here.
 */
public class YB_AttendanceExpenseReport_Cls extends TableBase {

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[1]")
	WebElement Top_search_btn;

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[2]")
	WebElement Search_go;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Clear']")
	WebElement Clear_btn;

	@FindBy(xpath = "//*[self::button or self::a][normalize-space()='Export']")
	WebElement Export_btn;

	public YB_AttendanceExpenseReport_Cls(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	public WebElement verify_top_search() {
		return Top_search_btn;
	}

	public WebElement verify_export() {
		return Export_btn;
	}

	public WebElement verify_clear() {
		return Clear_btn;
	}

	public WebElement verify_search_go() {
		return Search_go;
	}

	public boolean is_list_page() {
		try {
			return Top_search_btn.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	// ======================= search form =======================

	public WebElement verify_hierarchy_type() {
		return label_select("Hierarchy Type");
	}

	public WebElement verify_hierarchy_name() {
		return label_input("Hierarchy Name");
	}

	public WebElement verify_employee_type() {
		return label_select("Employee Type");
	}

	public WebElement verify_employee_name() {
		return label_input("Employee Name");
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
	public void Search_report(String hierarchy_type, String hierarchy_name, String employee_type, String employee,
			String from, String to) {
		Open_search_form();
		if (!hierarchy_type.isEmpty()) {
			select_text(verify_hierarchy_type(), hierarchy_type);
			pause(800);
		}
		if (!hierarchy_name.isEmpty()) {
			search_pick(verify_hierarchy_name(), hierarchy_name);
		}
		if (!employee_type.isEmpty()) {
			select_text(verify_employee_type(), employee_type);
			pause(500);
		}
		if (!employee.isEmpty()) {
			search_pick(verify_employee_name(), employee);
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
		Search_report("", "", "", "", from, to);
	}

	public void Click_clear() {
		Open_search_form();
		click_safely(Clear_btn);
		pause(2500);
	}

	public void Click_export() {
		click_safely(Export_btn);
		pause(2500);
	}
}
