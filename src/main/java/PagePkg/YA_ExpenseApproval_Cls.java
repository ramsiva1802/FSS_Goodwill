package PagePkg;

import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

/*
 * Page class of Field Management > Expense Approval (logged in as the web user).
 *
 * Top: Search button (opens the search form), the "Approve All" check box, the table filter "Search:".
 * Search form: Employee Type (drop down), Employee Name (search box), Expense Type (drop down), Status (drop down),
 * From Date, To Date (dd/MM/yyyy), Factory Name (search box), Search.
 * Result table (scrolls sideways): FACTORY, EMPLOYEE TYPE, EMPLOYEE, EXPENSE TYPE, EXPENSE DATE, EXPENSE NUMBER,
 * EXPENSE AMOUNT, REMARKS, AGAINST ATTENDANCE, IN TIME, OUT TIME, START LOCATION, END LOCATION, ODO START KM, IMAGE,
 * ODO END KM, IMAGE 2, DISTANCE, CALCULATED DISTANCE, ACTION.
 * ACTION holds a check box (a row that can be decided), a disabled check box (it cannot be decided yet) or the text
 * Approved / Rejected. records() adds "ACTION STATE" = enabled | disabled | text to every row.
 * Deciding a row: tick its check box > pop-up "Do you want to approve or reject this data?" (Start Time, End Time,
 * Kilometer (Km), Remarks, Yes, Approve It, Reject) > Approve: confirmation "Approve Expense?" (Yes, Approve / Cancel);
 * Reject: Remarks * and Yes, Reject It / Cancel > confirmation "Reject Expense?" (Yes, Reject / Cancel).
 */
public class YA_ExpenseApproval_Cls extends TableBase {

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[1]")
	WebElement Top_search_btn;

	@FindBy(xpath = "(//*[self::button or self::a][normalize-space()='Search'])[2]")
	WebElement Search_go;

	public YA_ExpenseApproval_Cls(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	// ======================= top =======================

	public WebElement verify_top_search() {
		return Top_search_btn;
	}

	public WebElement verify_approve_all() {
		return first_displayed(By.xpath("//label[normalize-space()='Approve All']//input[@type='checkbox']"
				+ " | //input[@type='checkbox'][contains(translate(@id,'APROVEL','aprovel'),'approveall')]"
				+ " | //*[normalize-space(text())='Approve All']/preceding::input[@type='checkbox'][1]"));
	}

	public boolean is_list_page() {
		try {
			return Top_search_btn.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	// ======================= search form =======================

	public WebElement verify_employee_type() {
		return label_select("Employee Type");
	}

	public WebElement verify_employee_name() {
		return label_input("Employee Name");
	}

	public WebElement verify_expense_type() {
		return label_select("Expense Type");
	}

	public WebElement verify_status() {
		return label_select("Status");
	}

	public WebElement verify_from() {
		return label_input("From Date");
	}

	public WebElement verify_to() {
		return label_input("To Date");
	}

	public WebElement verify_factory() {
		return label_input("Factory Name");
	}

	public WebElement verify_search_go() {
		return Search_go;
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
	public void Search_expense(String employee_type, String employee, String expense_type, String status, String factory,
			String from, String to) {
		Open_search_form();
		if (!employee_type.isEmpty()) {
			select_text(verify_employee_type(), employee_type);
			pause(500);
		}
		if (!employee.isEmpty()) {
			search_pick(verify_employee_name(), employee);
		}
		if (!expense_type.isEmpty()) {
			select_text(verify_expense_type(), expense_type);
		}
		if (!status.isEmpty()) {
			select_text(verify_status(), status);
		}
		if (!factory.isEmpty()) {
			search_pick(verify_factory(), factory);
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
		Search_expense("", "", "", "", "", from, to);
	}

	// ======================= rows =======================

	// "ACTION STATE": enabled (a check box that can be ticked), disabled (a check box that cannot) or text
	@Override
	protected void decorate(WebElement row, Map<String, String> record) {
		String state = "text";
		try {
			List<WebElement> cells = row.findElements(By.tagName("td"));
			List<WebElement> boxes = cells.get(cells.size() - 1).findElements(By.xpath(".//input[@type='checkbox']"));
			if (!boxes.isEmpty()) {
				state = boxes.get(0).isEnabled() ? "enabled" : "disabled";
			}
		} catch (StaleElementReferenceException e) {
			state = "unknown";
		}
		record.put("ACTION STATE", state);
	}

	// the row of an expense number: the table is filtered with the number first
	public WebElement row_of(String number) {
		Filter_table(number);
		for (WebElement r : driver.findElements(By.xpath("//table//tbody/tr[td[normalize-space()='" + number + "']]"))) {
			try {
				if (r.isDisplayed()) {
					return r;
				}
			} catch (StaleElementReferenceException e) {
				// skip
			}
		}
		throw new NoSuchElementException("No row with the expense number " + number);
	}

	public WebElement row_checkbox(String number) {
		WebElement row = row_of(number);
		List<WebElement> cells = row.findElements(By.tagName("td"));
		List<WebElement> boxes = cells.get(cells.size() - 1).findElements(By.xpath(".//input[@type='checkbox']"));
		if (boxes.isEmpty()) {
			throw new NoSuchElementException("The row of " + number + " has no check box");
		}
		return boxes.get(0);
	}

	// the record (with ACTION STATE) of an expense number on the current search result; null when it is not listed
	public Map<String, String> record_of(String number) {
		Filter_table(number);
		for (Map<String, String> rec : records()) {
			if (number.equals(rec.get("EXPENSE NUMBER"))) {
				return rec;
			}
		}
		return null;
	}

	// ======================= decision pop-up =======================

	public boolean decision_open() {
		return !displayed(By.xpath("//*[contains(normalize-space(text()),'approve or reject this data')]")).isEmpty();
	}

	// ticks the check box of the row and waits for the pop-up
	public void Open_decision(String number) {
		click_safely(row_checkbox(number));
		wait.until(d -> decision_open());
		pause(400);
	}

	public WebElement modal_start() {
		return first_displayed(By.xpath("//*[normalize-space(text())='Start Time']/following::input[1]"));
	}

	public WebElement modal_end() {
		return first_displayed(By.xpath("//*[normalize-space(text())='End Time']/following::input[1]"));
	}

	public WebElement modal_km() {
		return first_displayed(By.xpath("//*[starts-with(normalize-space(text()),'Kilometer')]/following::input[1]"));
	}

	public boolean modal_has_km() {
		return !displayed(By.xpath("//*[starts-with(normalize-space(text()),'Kilometer')]")).isEmpty();
	}

	public WebElement modal_remarks() {
		return first_displayed(By.xpath("//*[starts-with(normalize-space(text()),'Remarks')][not(self::th)]/following::textarea[1]"));
	}

	public WebElement button(String text) {
		return first_displayed(By.xpath("//button[normalize-space()='" + text + "'] | //a[normalize-space()='" + text
				+ "'] | //input[@value='" + text + "']"));
	}

	public boolean has_button(String text) {
		return !displayed(By.xpath("//button[normalize-space()='" + text + "'] | //a[normalize-space()='" + text
				+ "'] | //input[@value='" + text + "']")).isEmpty();
	}

	public void Click_button(String text) {
		click_safely(button(text));
		pause(800);
	}

	// the X of the pop-up
	public void Close_decision() {
		click_safely(first_displayed(By.xpath("//*[self::button or self::span or self::a]"
				+ "[normalize-space()='X' or normalize-space()='x' or normalize-space()='×']")));
		pause(800);
	}

	// the Cancel of the pop-up (not the one of a confirmation)
	public void Cancel_decision() {
		click_safely(first_displayed(By.xpath("//button[normalize-space()='Cancel'][not(ancestor::*[contains(@class,'swal2')])]")));
		pause(800);
	}

	// ======================= confirmations =======================

	public boolean swal_visible() {
		return !displayed(By.xpath("//div[contains(@class,'swal2-popup')]")).isEmpty();
	}

	public String swal_text() {
		List<WebElement> found = displayed(By.xpath("//div[contains(@class,'swal2-popup')]"));
		return found.isEmpty() ? "" : found.get(0).getText().trim().replaceAll("\\s+", " ");
	}

	public void Confirm_swal() {
		click_safely(first_displayed(By.xpath("//div[contains(@class,'swal2-popup')]//button[contains(@class,'swal2-confirm')]")));
		pause(800);
	}

	public void Cancel_swal() {
		click_safely(first_displayed(By.xpath("//div[contains(@class,'swal2-popup')]//button[contains(@class,'swal2-cancel')]")));
		pause(800);
	}

	// closes the result messages (for example "Approved!" with OK) that follow a decision
	public void Finish_messages() {
		for (int i = 0; i < 4 && swal_visible(); i++) {
			try {
				Confirm_swal();
			} catch (Exception e) {
				break;
			}
			pause(800);
		}
	}

	// ======================= decisions =======================

	// approves an expense: the pop-up, the km and remarks (empty = leave), Yes Approve It, Yes Approve, the message
	public void Approve(String number, String km, String remarks) {
		Open_decision(number);
		if (!km.isEmpty()) {
			type(modal_km(), km);
		}
		if (!remarks.isEmpty()) {
			type(modal_remarks(), remarks);
		}
		Click_button("Yes, Approve It");
		wait.until(d -> swal_visible());
		Confirm_swal();
		pause(2500);
		Finish_messages();
	}

	// rejects an expense: the pop-up, Reject, the remarks, Yes Reject It, Yes Reject, the message
	public void Reject(String number, String remarks) {
		Open_decision(number);
		Click_button("Reject");
		type(modal_remarks(), remarks);
		Click_button("Yes, Reject It");
		wait.until(d -> swal_visible());
		Confirm_swal();
		pause(2500);
		Finish_messages();
	}
}
