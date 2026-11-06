package TestPkg;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.A_Login_Cls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.E_Employee_Cls;
import PagePkg.J_RouteDayMapping_Cls;
import PagePkg.O_Expense_Cls;
import UtillPkg.WaitUtil;

/*
 * NOTES - ZF_ApkEmployee_Setup_Test (bulk entry: it creates data and keeps it)
 * -------------------------------------------------------------------------
 * Creates ONE new employee that is ready for the APK login, in 4 steps:
 *   1. Employee Info > Employee : Sales Executive, APK user (Is Web User = No), belongs to PLANT / GOODWILL POLYMERS FSS 2026-2027.
 *      Employee code = 4 random capital letters, email = 4 random small letters + @gmail.com, so every run uses NEW values.
 *   2. The employee is searched in the Employee list (verification of step 1).
 *   3. Customer Details > Route Day Mapping : the employee, settings code GPSE1, a NEW vehicle (new name every run),
 *      from date = today and the 5 routes in ROUTES.
 *   4. Field Management > Expense : Is Attendance Expense = Yes, all 5 tick boxes, a rate, zone GOODWILL POLYMERS,
 *      Employee Wise, designation Sales Executive and the new employee ticked.
 * At the end the email and the employee code are printed in the console (and written to the report).
 * Nothing is deleted: the employee, the mapping, the vehicle and the expense are meant to stay.
 * If you want another expense type, change EXPENSE_TYPE (blank = the first attendance-like type offered by the box).
 */
public class ZF_ApkEmployee_Setup_Test extends BaseCls {

	static final String HIERARCHY_TYPE = "PLANT";
	static final String HIERARCHY_NAME = "GOODWILL POLYMERS FSS 2026-2027";
	static final String SETTING = "GPSE1";
	static final String[] ROUTES = { "ALAPPUZHA EAST", "ALAPPUZHA WEST NEW", "ALAPPUZHA WEST", "CITY ROUTE 1",
			"PROJECT - CITY 1" };

	static final String ZONE = "GOODWILL POLYMERS";
	static final String DESIGNATION = "Sales Executive";
	static final String RATE = "10";
	// blank = the box is asked for these texts one after the other and the first suggestion found is used
	static final String EXPENSE_TYPE = "";
	static final String[] EXPENSE_TYPE_TRIES = { "Attendance", "TA", "Travel", "Allowance", "a", "e" };
	static final String[] CHECKBOXES = { "perkm", "editable", "odometer", "image", "endkm" };

	// the data of this run
	String emp_code = "";
	String emp_email = "";
	String emp_first = "";
	String emp_last = "";
	String vehicle = "";
	String expense_type = "";

	// what the last Submit showed
	String last_alert = "";
	String last_notice = "";

	boolean employee_created = false;
	boolean employee_found = false;
	boolean mapping_created = false;
	boolean expense_created = false;

	private static String letters(int count, boolean capital) {
		Random random = new Random();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			sb.append((char) ((capital ? 'A' : 'a') + random.nextInt(26)));
		}
		return sb.toString();
	}

	// runs ONCE: fresh session, login through the form
	@BeforeClass
	public void login_first() {
		driver.get(url);
		driver.manage().deleteAllCookies();
		try {
			((JavascriptExecutor) driver).executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
		} catch (Exception e) {
			System.out.println("Could not clear browser storage: " + e.getMessage());
		}
		driver.get(url);
		A_Login_Cls login = new A_Login_Cls(driver);
		login.Login(admin_user, admin_pass);
		Assert.assertTrue(login.login_success(), "Could not log in");
	}

	// ======================= navigation =======================

	private void open_module(String module, String name) {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
		if (!dash.is_home_available()) {
			driver.navigate().back();
		}
		dash.Click_home();
		try {
			dash.Click_submenu_by_name(module, name);
		} catch (NoSuchElementException e) {
			dash.Click_home();
			dash.Click_submenu_containing(module, name);
		}
		WaitUtil.settle(driver, 1500);
	}

	private void go_to_employee() {
		E_Employee_Cls e = new E_Employee_Cls(driver);
		open_module("Employee Info", "Employee");
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> e.is_list_page());
	}

	private void go_to_mapping() {
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);
		open_module("Customer Details", "Route Day Mapping");
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> m.is_list_page());
	}

	private void go_to_expense() {
		O_Expense_Cls x = new O_Expense_Cls(driver);
		open_module("Field Management", "Expense");
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> x.is_list_page());
	}

	// ======================= STEP 1: create the employee =======================

	@Test(priority = 1)
	public void step1_create_employee() throws InterruptedException {
		test = extent.createTest("Step 1 Create APK employee (Sales Executive)");
		E_Employee_Cls e = new E_Employee_Cls(driver);

		go_to_employee();

		for (int attempt = 1; attempt <= 3 && !employee_created; attempt++) {
			emp_code = letters(4, true);
			emp_email = letters(4, false) + "@gmail.com";
			emp_first = "Apk" + letters(3, false);
			emp_last = "User" + letters(3, false);

			// a new code only: the list must not know it yet
			e.Search_employee(emp_code, "");
			WaitUtil.settle(driver, 1500);
			if (e.result_count() > 0) {
				test.info("Code " + emp_code + " already exists, taking another one");
				go_to_employee();
				continue;
			}
			go_to_employee();

			e.Click_create();
			WaitUtil.settle(driver, 1500);
			test.info("Attempt " + attempt + ": code " + emp_code + ", email " + emp_email);
			e.Fill_mandatory(emp_code, emp_first, emp_last, emp_email, "Auto Address");
			e.Select_web_user(false);   // APK user only, not a web user
			e.Click_submit();
			WaitUtil.settle(driver, 3000);

			String message = e.get_message();
			test.info("Message after submit: '" + message + "' | validation: " + e.validation_messages());
			if (!e.is_create_form()) {
				employee_created = true;
			} else {
				test.info("The form is still open, trying again with new values");
				go_to_employee();
			}
		}
		Assert.assertTrue(employee_created, "The employee could not be created. Last code " + emp_code + ", email " + emp_email);
		test.pass("Employee submitted: " + emp_code + " / " + emp_email);
	}

	// ======================= STEP 2: search it =======================

	@Test(priority = 2, dependsOnMethods = "step1_create_employee")
	public void step2_search_employee() {
		test = extent.createTest("Step 2 Search the new employee");
		E_Employee_Cls e = new E_Employee_Cls(driver);

		go_to_employee();
		e.Search_employee(emp_code, "");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(100)).until(d -> e.result_count() > 0);
		} catch (Exception ex) {
			// the assertion below reports it
		}
		test.info("Rows shown: " + e.result_texts());
		Assert.assertEquals(e.result_count(), 1, "The new employee " + emp_code + " was not found in the Employee list");
		Assert.assertEquals(e.first_row_code(), emp_code, "The found row is not the new employee");
		employee_found = true;
		test.pass("The employee " + emp_code + " is in the Employee list: " + e.result_texts());
	}

	// ======================= STEP 3: route day mapping =======================

	private boolean offers(List<String> suggestions, String text) {
		for (String s : suggestions) {
			if (s.toLowerCase().contains(text.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	private void open_mapping_form(J_RouteDayMapping_Cls m) {
		m.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> m.is_form_screen());
		m.Select_type(HIERARCHY_TYPE);
		m.Select_hierarchy(HIERARCHY_NAME);
	}

	@Test(priority = 3, dependsOnMethods = "step2_search_employee")
	public void step3_map_route() {
		test = extent.createTest("Step 3 Route Day Mapping with a new vehicle");
		J_RouteDayMapping_Cls m = new J_RouteDayMapping_Cls(driver);

		go_to_mapping();
		open_mapping_form(m);

		// a new vehicle with a new name every run
		vehicle = letters(5, true);
		m.Open_add_vehicle();
		m.Add_vehicle(vehicle, vehicle);
		test.info("Add vehicle said: " + m.validation_messages() + " | notice: " + m.notice() + " | alert: " + m.Accept_alert());
		m.Close_notice();
		if (m.add_vehicle_open()) {
			m.Close_add_vehicle();
		}

		// the new vehicle is offered at once, or only after the form is opened again
		if (!offers(m.vehicle_suggestions(vehicle), vehicle)) {
			test.info("The vehicle is not offered yet, opening the form again");
			go_to_mapping();
			open_mapping_form(m);
			Assert.assertTrue(offers(m.vehicle_suggestions(vehicle), vehicle), "The new vehicle " + vehicle + " is not offered");
		}
		m.verify_vehicle().clear();

		m.Select_employee(emp_code);
		String shown = m.verify_employee().getAttribute("value");
		test.info("Employee box shows: " + shown);
		Assert.assertTrue(shown.toLowerCase().contains(emp_code.toLowerCase()) || !shown.trim().isEmpty(),
				"The employee " + emp_code + " could not be chosen in the Employee box");

		m.Select_settings(SETTING);
		m.Set_from_date(LocalDate.now().toString());
		m.Select_vehicle(vehicle);
		test.info("Vehicle box shows: " + m.verify_vehicle().getAttribute("value"));

		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> m.route_row_count() > 0);
		// each route is searched in the "Filter routes" box and the first row shown is ticked
		// (when the search shows several rows, e.g. WEST and WEST NEW, the one with the exact name is taken)
		for (String route : ROUTES) {
			m.Filter_routes(route);
			try {
				new WebDriverWait(driver, Duration.ofSeconds(10), Duration.ofMillis(100)).until(d -> m.route_row_count() > 0);
			} catch (Exception ex) {
				// the assertion below reports it
			}
			List<String> rows = m.route_names();
			Assert.assertFalse(rows.isEmpty(), "Route " + route + " is not listed");
			int index = 0;
			for (int i = 0; i < rows.size(); i++) {
				if (rows.get(i).trim().equalsIgnoreCase(route)) {
					index = i;
					break;
				}
			}
			test.info("Search '" + route + "' shows " + rows + " -> ticking " + rows.get(index));
			m.Tick_route(index);
			Assert.assertTrue(m.route_ticked(index), "Route not ticked: " + route);
		}
		m.Filter_routes("");

		submit_mapping(m);
		test.info("Site said: " + m.validation_messages() + " | notice: " + last_notice + " | alert: " + last_alert);
		Assert.assertTrue(m.is_list_page(), "Not back on the Route Day Mapping list. Messages: " + m.validation_messages()
				+ " | notice: " + last_notice + " | alert: " + last_alert);

		m.Filter_table(emp_code);
		Assert.assertTrue(m.Wait_for_row(emp_code), "The new mapping of " + emp_code + " is not in the list");
		List<String> row = m.row_of(emp_code);
		test.info("Mapping row: " + row);
		Assert.assertEquals(row.get(1), SETTING, "Setting code");
		Assert.assertEquals(row.get(3), String.valueOf(ROUTES.length), "No. of routes");
		mapping_created = true;
		test.pass("Mapping created for " + emp_code + " with vehicle " + vehicle);
	}

	private void submit_mapping(J_RouteDayMapping_Cls m) {
		last_alert = "";
		last_notice = "";
		m.Click_submit();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> {
				String a = m.Accept_alert();
				if (!a.isEmpty()) {
					last_alert = a;
					return true;
				}
				return m.is_list_page() || !m.validation_messages().isEmpty() || !m.notice().isEmpty();
			});
		} catch (Exception e) {
			// nothing appeared: the caller checks the screen
		}
		last_notice = m.notice();
		m.Close_notice();
		WaitUtil.settle(driver, 800);
		String late = m.Accept_alert();
		if (!late.isEmpty()) {
			last_alert = last_alert + " " + late;
		}
	}

	// ======================= STEP 4: expense =======================

	// the expense type to use: EXPENSE_TYPE, or the first suggestion found for the texts in EXPENSE_TYPE_TRIES
	private String choose_expense_type(O_Expense_Cls x) {
		String[] tries = EXPENSE_TYPE.isEmpty() ? EXPENSE_TYPE_TRIES : new String[] { EXPENSE_TYPE };
		for (String text : tries) {
			List<String> found = x.expense_type_suggestions(text);
			test.info("Expense type box offers for '" + text + "': " + found);
			if (!found.isEmpty()) {
				for (String f : found) {
					if (f.equalsIgnoreCase(text)) {
						return f;
					}
				}
				return found.get(0);
			}
		}
		throw new NoSuchElementException("The Expense Type box offered nothing for " + java.util.Arrays.toString(tries));
	}

	@Test(priority = 4, dependsOnMethods = "step3_map_route")
	public void step4_create_expense() {
		test = extent.createTest("Step 4 Attendance expense for the employee");
		O_Expense_Cls x = new O_Expense_Cls(driver);

		go_to_expense();
		x.Click_create();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> x.is_form_screen());

		expense_type = choose_expense_type(x);
		x.Select_expense_type(expense_type);
		test.info("Expense type chosen: " + expense_type + " | box shows: " + x.verify_expense_type().getAttribute("value"));

		x.Type_rate(RATE);
		x.Set_radio(O_Expense_Cls.ATTENDANCE, "Yes");
		Assert.assertEquals(x.radio_selected(O_Expense_Cls.ATTENDANCE), "Yes", "Is Attendance Expense");
		for (String box : CHECKBOXES) {
			x.Set_checkbox(box, true);
			Assert.assertTrue(x.checkbox(box).isSelected(), "Tick box not ticked: " + box);
		}
		if (x.verify_rate().getAttribute("value").trim().isEmpty()) {
			x.Type_rate(RATE);   // a tick box cleared the rate: enter it again
		}

		x.Select_zone(ZONE);
		x.Select_filter_type("Employee Wise");
		x.Select_designation(DESIGNATION);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).ignoring(RuntimeException.class)
				.until(d -> x.table_row_count() > 0 && contains_code(x));
		x.Tick_table_row(emp_code);
		Assert.assertEquals(x.ticked_rows(), 1, "Employees ticked in the table");

		last_alert = "";
		last_notice = "";
		x.Click_submit();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(d -> {
				String a = x.Accept_alert();
				if (!a.isEmpty()) {
					last_alert = a;
					return true;
				}
				return x.is_list_page() || !x.validation_messages().isEmpty() || !x.notice().isEmpty();
			});
		} catch (Exception e) {
			// nothing appeared: the check below reports it
		}
		last_notice = x.notice();
		x.Close_notice();
		WaitUtil.settle(driver, 800);
		test.info("Site said: " + x.validation_messages() + " | notice: " + last_notice + " | alert: " + last_alert);
		Assert.assertTrue(x.is_list_page(), "Not back on the Expense list. Messages: " + x.validation_messages()
				+ " | notice: " + last_notice + " | alert: " + last_alert);

		x.Filter_table(emp_code);
		Assert.assertTrue(x.Wait_for_row(expense_type, emp_code), "The expense row of " + emp_code + " is not in the list");
		test.info("Expense row: " + x.row_of(expense_type, emp_code));
		expense_created = true;
		test.pass("Expense " + expense_type + " created for " + emp_code);
	}

	private boolean contains_code(O_Expense_Cls x) {
		for (String t : x.table_texts()) {
			if (t.toLowerCase().contains(emp_code.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	// ======================= RESULT =======================

	@Test(priority = 5, alwaysRun = true)
	public void step5_print_result() {
		test = extent.createTest("Step 5 Result of the APK employee setup");

		String line = "====================================================";
		System.out.println(line);
		System.out.println("APK EMPLOYEE SETUP");
		System.out.println("Employee code : " + emp_code);
		System.out.println("Email         : " + emp_email);
		System.out.println("Employee name : " + emp_first + " " + emp_last);
		System.out.println("Vehicle       : " + vehicle);
		System.out.println("Expense type  : " + expense_type);
		System.out.println("Employee created: " + employee_created + " | found in list: " + employee_found
				+ " | route mapping: " + mapping_created + " | expense: " + expense_created);
		System.out.println(line);

		test.info("Employee code: " + emp_code + " | Email: " + emp_email);
		test.info("Employee created: " + employee_created + ", found: " + employee_found + ", route mapping: "
				+ mapping_created + ", expense: " + expense_created);

		Assert.assertTrue(employee_created && employee_found && mapping_created && expense_created,
				"The setup is not complete (see the step results above). Employee code " + emp_code + ", email " + emp_email);
		test.pass("The employee for the APK is created successfully: " + emp_code + " / " + emp_email);
	}
}
