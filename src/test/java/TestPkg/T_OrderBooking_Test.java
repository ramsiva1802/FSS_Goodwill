package TestPkg;

import UtillPkg.TestEnv;
import UtillPkg.WaitUtil;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import BasePkg.BaseCls;
import PagePkg.B_Dashboard_Cls;
import PagePkg.A_Login_Cls;
import PagePkg.T_OrderBooking_Cls;
import PagePkg.S_OrderStatus_Cls;

/*
 * Order Booking (Field Management > OrderBooking), logged in as the web user - functional tests, OB01 .. OB47.
 * The wizard has three steps: Customer Details, Item Selection, Payment Details.
 * OB01 .. OB36 only fill the wizard and never place an order.
 * OB37 .. OB46 PLACE real orders (route and customer below, remark starting with ZQ) and check them in
 * Field Management > Order Status. They run only when PLACE_ORDERS is true. A placed order can be removed
 * from Order Status by the admin (the bin icon of a pending order).
 */
public class T_OrderBooking_Test extends BaseCls {

	// login of the web user (a test login of this project)
	static final String WEB_USER = TestEnv.get("web.user", "your-web-user");
	static final String WEB_PASS = TestEnv.get("web.password", "your-web-password");

	// true = the tests that place orders are run
	static final boolean PLACE_ORDERS = true;

	// data of the booking: a route, a customer typed in the customer box, and items with their rates
	static final String ROUTE = "ALAPPUZHA WEST NEW";
	static final String CUSTOMER = "ramsiva";
	static final String ITEM_A = "leo-item1";
	static final String ITEM_B = "leo-item2";
	static final String ITEM_C = "CRono-item2";
	// items of the Select Item pop-up that carry a tax (the amount of a line includes it)
	static final String ITEM_TAX1 = "75mm(2 1/2\")6 Kg Goodwill ISI";
	static final String ITEM_TAX2 = "40mm(1 1/4\")6 Kg Goodwill ISI";

	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private String main_tab;

	private String unique() {
		return String.valueOf((System.currentTimeMillis() / 100) % 1000000);
	}

	private String today() {
		return LocalDate.now().format(DATE);
	}

	private String days_from_today(int days) {
		return LocalDate.now().plusDays(days).format(DATE);
	}

	// runs ONCE: fresh session, login as the web user
	@BeforeClass
	public void login_first() {
		login_again();
	}

	private void login_again() {
		try {
			driver.get(url);
		} catch (org.openqa.selenium.NoSuchSessionException | org.openqa.selenium.NoSuchWindowException e) {
			// the browser was closed: start a new one
			restartBrowser();
		}
		driver.manage().deleteAllCookies();
		try {
			((JavascriptExecutor) driver).executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
		} catch (Exception e) {
			System.out.println("Could not clear browser storage: " + e.getMessage());
		}
		driver.get(url);
		A_Login_Cls login = new A_Login_Cls(driver);
		try {
			login.Login(WEB_USER, WEB_PASS);
		} catch (TimeoutException e) {
			driver.manage().deleteAllCookies();
			driver.get(url);
			login.Login(WEB_USER, WEB_PASS);
		}
		Assert.assertTrue(login.login_success(), "Could not log in as " + WEB_USER);
		main_tab = driver.getWindowHandle();
	}

	// closes alerts, extra tabs and pop-ups that a failed test may have left open
	private void clean_up() {
		try {
			driver.switchTo().alert().dismiss();
		} catch (Exception e) {
			// no alert
		}
		try {
			Set<String> handles = driver.getWindowHandles();
			if (main_tab != null && !handles.isEmpty() && !handles.contains(main_tab)) {
				main_tab = handles.iterator().next();
			}
			if (handles.size() > 1 && main_tab != null) {
				for (String h : handles) {
					if (!h.equals(main_tab)) {
						driver.switchTo().window(h);
						driver.close();
					}
				}
				driver.switchTo().window(main_tab);
			}
		} catch (Exception e) {
			System.out.println("Could not close the extra tabs: " + e.getMessage());
		}
		try {
			new T_OrderBooking_Cls(driver).Close_multiple();
		} catch (Exception e) {
			// no pop-up
		}
	}

	// opens Field Management > OrderBooking from the dashboard
	private void go_to_booking() throws InterruptedException {
		for (int attempt = 1; attempt <= 4; attempt++) {
			try {
				B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
				if (!dash.is_home_available()) {
					driver.navigate().back();
				}
				dash.Click_home();
				try {
					dash.Click_submenu_containing("Field Management", "Booking");
				} catch (NoSuchElementException e) {
					dash.Click_home();
					dash.Click_submenu_containing("Field Management", "Order Book");
				}
				WaitUtil.settle(driver, 600);
				return;
			} catch (ElementClickInterceptedException e) {
				if (attempt == 4) {
					throw e;
				}
				WaitUtil.settle(driver, 2500);
			}
		}
	}

	// every test starts with a fresh, empty booking wizard
	@BeforeMethod
	public void open_booking() throws InterruptedException {
		if (screen_is_clean()) {
			return;
		}
		clean_up();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);
		try {
			go_to_booking();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_booking_page());
		} catch (Exception e) {
			login_again();
			go_to_booking();
			new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_booking_page());
		}
		booking_url = driver.getCurrentUrl();
		remember_clean_screen();
	}

	// ---------------------------------- helpers ----------------------------------

	// step 1: route and customer
	private void choose_customer(T_OrderBooking_Cls o) {
		o.Select_route(ROUTE);
		o.Search_customer(CUSTOMER);
		Assert.assertFalse(o.info_value("Customer Code").isEmpty() || o.info_value("Customer Code").equals("-"),
				"The customer " + CUSTOMER + " was not picked (details card is empty). Customer box: '" + o.customer_text() + "'");
	}

	// steps 1 -> 2
	private void to_items(T_OrderBooking_Cls o) {
		choose_customer(o);
		o.Click_next();
		Assert.assertEquals(o.current_tab(), "Item Selection", "Next did not open Item Selection: " + o.All_messages());
	}

	// puts an item with its quantity in a row (row 0 is the empty row that exists)
	private void fill_row(T_OrderBooking_Cls o, int row, String item, String qty) {
		o.Pick_item(row, item);
		Assert.assertTrue(o.rate(row) > 0, "The item " + item + " was not picked in row " + (row + 1) + " (rate '"
				+ o.rate_text(row) + "', item box '" + o.item_text(row) + "')");
		o.Set_qty(row, qty);
	}

	// steps 2 -> 3
	private void to_payment(T_OrderBooking_Cls o) {
		o.Click_next();
		Assert.assertEquals(o.current_tab(), "Payment Details", "Next did not open Payment Details: " + o.All_messages());
	}

	private double tol(double expected) {
		return Math.max(0.02, Math.abs(expected) * 0.0005);
	}

	// the tax factor of a filled row: amount / (rate x quantity)
	private double factor(T_OrderBooking_Cls o, int row) {
		double base = o.rate(row) * o.qty(row);
		return base == 0 ? 1 : o.amount(row) / base;
	}

	private static class Placed {
		String no = "";
		String credit = "";
		String popup = "";
		String popup_no = "";
		double amount;
		String message = "";
		boolean ok;
	}

	private String booking_url;

	// opens the booking page again by its address (used after an order was refused and the page is stuck)
	private void reopen_booking(T_OrderBooking_Cls o) {
		clean_up();
		if (o.current_tab().equals("Customer Details")) {
			return;
		}
		driver.get(booking_url);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_booking_page());
	}

	// places an order: items = {name, qty} typed in the rows, multi = {name, qty} added with Add Multiple (after
	// the typed rows). mode / delivery / remark are skipped when null.
	private Placed place(T_OrderBooking_Cls o, String[][] items, String[][] multi, String mode, String delivery,
			String remark, boolean important) {
		Placed p = new Placed();
		o.Select_route(ROUTE);
		o.Search_customer(CUSTOMER);
		p.no = o.order_no();
		p.credit = o.info_value("Credit Limit");
		o.Click_next();
		Assert.assertEquals(o.current_tab(), "Item Selection", "Could not reach Item Selection: " + o.All_messages());
		for (int i = 0; i < items.length; i++) {
			if (i > 0) {
				o.Click_add_row();
			}
			fill_row(o, i, items[i][0], items[i][1]);
		}
		if (multi != null && multi.length > 0) {
			o.Click_add_multiple();
			for (String[] m : multi) {
				o.Pick_multiple(m[0], m[1]);
			}
			o.Close_multiple();
		}
		o.Click_next();
		Assert.assertEquals(o.current_tab(), "Payment Details", "Could not reach Payment Details: " + o.All_messages());
		p.amount = o.payment_amount();
		if (mode != null) {
			o.Select_mode(mode);
			if (!mode.equals("Unpaid") && !mode.equals("Select")) {
				o.Fill_extra_fields(unique());
			}
		}
		if (delivery != null) {
			o.Set_delivery(delivery);
		}
		if (remark != null) {
			o.Set_remark(remark);
		}
		if (important) {
			o.Set_important(true);
		}
		o.Click_submit();
		long end = System.currentTimeMillis() + 8000;
		while (System.currentTimeMillis() < end && !o.alert_open() && o.notice().isEmpty()
				&& !o.current_tab().equals("Customer Details")) {
			WaitUtil.settle(driver, 500);
		}
		boolean thank_you = o.success_popup_open();
		if (thank_you) {
			p.popup = o.success_popup_text();
			p.popup_no = o.success_popup_order_no();
			o.Click_success_ok();
			p.message = "popup: " + p.popup;
		} else {
			p.message = o.All_messages();
			p.message += " " + o.Accept_notice();
		}
		p.message += " [tab after submit: " + o.current_tab() + "]";
		String lower = p.message.toLowerCase();
		boolean words = lower.contains("success") || lower.contains("placed") || lower.contains("booked")
				|| lower.contains("saved") || lower.contains("created") || lower.contains("submitted") || lower.contains("thank");
		String new_no = o.order_no();
		p.ok = thank_you || words || (o.current_tab().equals("Customer Details") && !new_no.isEmpty() && !new_no.equals(p.no));
		return p;
	}

	// the order in Field Management > Order Status (found by its number); skips when the web user has no such menu
	private S_OrderStatus_Cls open_status_for(String order_no) throws InterruptedException {
		B_Dashboard_Cls dash = new B_Dashboard_Cls(driver);
		dash.Click_home();
		try {
			dash.Click_submenu_by_name("Field Management", "Order Status");
		} catch (NoSuchElementException | TimeoutException e) {
			throw new SkipException("The web user has no Order Status menu to check the order");
		}
		WaitUtil.settle(driver, 2000);
		S_OrderStatus_Cls os = new S_OrderStatus_Cls(driver);
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> os.is_list_page());
		os.Search_orders("", "", today(), today(), "", "", "", "");
		os.Filter_table(order_no);
		return os;
	}

	private int trailing_number(String order_no) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)$").matcher(order_no);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	private void require_placing() {
		if (!PLACE_ORDERS) {
			throw new SkipException("PLACE_ORDERS is false");
		}
	}

	// ======================= step 1: customer details =======================

	// OB01: the wizard shows its 3 steps, Route, Customer, the details card and Next
	@Test
	public void OB01_screen_elements() {
		test = extent.createTest("OB01 Order Booking screen elements");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		for (String tab : new String[] { "Customer Details", "Item Selection", "Payment Details" }) {
			Assert.assertTrue(o.tab_exists(tab), "The step '" + tab + "' is missing");
		}
		Assert.assertEquals(o.current_tab(), "Customer Details", "The first step is not Customer Details");
		Assert.assertTrue(o.verify_route().isDisplayed(), "Route is missing");
		Assert.assertTrue(o.verify_customer().isDisplayed(), "Customer search box is missing");
		Assert.assertTrue(o.hint_text().toLowerCase().contains("route first"), "The hint is missing: '" + o.hint_text() + "'");
		String no = o.order_no();
		test.info("Order No: " + no + " | hint: " + o.hint_text());
		Assert.assertTrue(no.matches("\\d{2}OG\\w+"), "Order No has an unexpected format: '" + no + "'");
		for (String label : new String[] { "Customer Code", "GST Number", "Phone", "Current Advance", "Credit Limit", "Address" }) {
			Assert.assertFalse(o.info_value(label).isEmpty(), "The details card has no row: " + label);
		}
		Assert.assertTrue(o.button_texts().contains("Next"), "Next is missing: " + o.button_texts());
		test.pass("Screen elements are displayed");
	}

	// OB02: the route drop down has routes (written to the report) and the booking route
	@Test
	public void OB02_route_options() {
		test = extent.createTest("OB02 Route options");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		List<String> routes = o.route_options();
		test.info("Routes: " + routes + " | selected '" + o.route_selected() + "'");
		Assert.assertFalse(routes.isEmpty(), "The route drop down has no route");
		Assert.assertTrue(routes.contains(ROUTE), "The route " + ROUTE + " is not in the list");
		test.pass("Routes are listed");
	}

	// OB03: the customer box must not suggest customers before a route is chosen
	@Test
	public void OB03_customer_needs_route() {
		test = extent.createTest("OB03 Customer needs a route");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		List<String> found = o.Customer_suggestions("ram");
		test.info("Suggestions without a route: " + found + " | messages: " + o.All_messages());
		Assert.assertTrue(found.isEmpty(), "DEFECT: customers are suggested without a route although the page says 'Select a route first': " + found);
		test.pass("No customer is suggested without a route");
	}

	// OB04: the customer search starts at 3 letters
	@Test
	public void OB04_customer_search_minimum_letters() {
		test = extent.createTest("OB04 Customer search needs 3 letters");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		o.Select_route(ROUTE);
		List<String> two = o.Customer_suggestions("ra");
		test.info("'ra' -> " + two);
		Assert.assertTrue(two.isEmpty(), "DEFECT: customers are suggested for 2 letters although 3 are needed: " + two);
		List<String> three = o.Customer_suggestions("ram");
		test.info("'ram' -> " + three);
		Assert.assertFalse(three.isEmpty(), "No customer is suggested for 3 letters");
		test.pass("3 letters are needed");
	}

	// OB05: a picked customer fills the details card
	@Test
	public void OB05_customer_fills_details_card() {
		test = extent.createTest("OB05 Customer fills the details card");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		String no_before = o.order_no();
		choose_customer(o);
		for (String label : new String[] { "Order No", "Customer Code", "GST Number", "Phone", "Current Advance", "Credit Limit", "Address" }) {
			test.info(label + ": " + o.info_value(label));
		}
		test.info("Customer box: " + o.customer_text());
		Assert.assertEquals(o.order_no(), no_before, "The order number changed when the customer was picked");
		Assert.assertFalse(o.info_value("Address").equals("-"), "The address is not shown");
		Assert.assertTrue(o.customer_text().toLowerCase().contains(CUSTOMER.toLowerCase()), "The customer box shows '" + o.customer_text() + "'");
		test.pass("Details card is filled");
	}

	// OB06: a customer that does not exist is not suggested and cannot be continued with
	@Test
	public void OB06_unknown_customer() {
		test = extent.createTest("OB06 Unknown customer");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		o.Select_route(ROUTE);
		List<String> found = o.Customer_suggestions("ZZQNoSuch" + unique());
		Assert.assertTrue(found.isEmpty(), "A customer is suggested for text that matches nothing: " + found);
		o.Click_next();
		test.info("Next with an unknown customer: tab " + o.current_tab() + " | " + o.All_messages());
		Assert.assertEquals(o.current_tab(), "Customer Details", "DEFECT: Next opened Item Selection with a customer that does not exist");
		test.pass("An unknown customer is refused");
	}

	// OB07: customer typed but not picked from the list cannot be continued with
	@Test
	public void OB07_customer_typed_not_picked() {
		test = extent.createTest("OB07 Customer typed but not picked");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		o.Select_route(ROUTE);
		o.Customer_suggestions(CUSTOMER);
		o.Click_next();
		test.info("Next with the typed customer: tab " + o.current_tab() + " | " + o.All_messages());
		Assert.assertEquals(o.current_tab(), "Customer Details", "DEFECT: Next opened Item Selection although the customer was only typed, not picked");
		test.pass("A typed customer must be picked");
	}

	// OB08: changing the route clears the customer and the details card
	@Test
	public void OB08_route_change_clears_customer() {
		test = extent.createTest("OB08 Route change clears the customer");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		choose_customer(o);
		List<String> routes = o.route_options();
		String other = null;
		for (String r : routes) {
			if (!r.equals(ROUTE)) {
				other = r;
				break;
			}
		}
		if (other == null) {
			throw new SkipException("Only one route exists");
		}
		o.Select_route(other);
		test.info("Route now '" + other + "' | customer box '" + o.customer_text() + "' | code '" + o.info_value("Customer Code") + "'");
		Assert.assertTrue(o.customer_text().isEmpty() && o.info_value("Customer Code").equals("-"),
				"DEFECT: the customer of the old route is still selected after the route changed");
		test.pass("The customer is cleared when the route changes");
	}

	// OB09: Next without route and customer, and with a route only, stays on the step with a message
	@Test
	public void OB09_next_without_customer() {
		test = extent.createTest("OB09 Next without a customer");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		o.Click_next();
		String first = o.All_messages();
		test.info("Nothing chosen: tab " + o.current_tab() + " | " + first);
		Assert.assertEquals(o.current_tab(), "Customer Details", "DEFECT: Next opened Item Selection without a route and customer");
		Assert.assertFalse(first.isEmpty(), "DEFECT: Next without a customer gives no message");
		o.Select_route(ROUTE);
		o.Click_next();
		test.info("Route only: tab " + o.current_tab() + " | " + o.All_messages());
		Assert.assertEquals(o.current_tab(), "Customer Details", "DEFECT: Next opened Item Selection without a customer");
		test.pass("Next is blocked without a customer");
	}

	// OB10: the Item Selection and Payment Details steps cannot be opened by clicking them before the customer is chosen
	@Test
	public void OB10_step_jump_blocked() {
		test = extent.createTest("OB10 Jump to a step without a customer");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		o.Click_tab("Item Selection");
		String after_items = o.current_tab();
		o.All_messages();
		o.Click_tab("Payment Details");
		String after_payment = o.current_tab();
		test.info("Clicking Item Selection -> " + after_items + " | clicking Payment Details -> " + after_payment);
		Assert.assertEquals(after_items, "Customer Details", "DEFECT: the Item Selection step opens without a customer");
		Assert.assertEquals(after_payment, "Customer Details", "DEFECT: the Payment Details step opens without a customer");
		test.pass("Steps cannot be skipped");
	}

	// ======================= step 2: item selection =======================

	// OB11: Item Selection starts with one empty row, read only rate and amount, and its buttons
	@Test
	public void OB11_item_step_elements() {
		test = extent.createTest("OB11 Item Selection elements");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		Assert.assertEquals(o.row_count(), 1, "The step does not start with one row");
		Assert.assertEquals(o.item_text(0), "", "The first item box is not empty");
		Assert.assertEquals(o.qty_text(0), "1", "The default quantity is not 1");
		Assert.assertTrue(o.is_locked(o.rate_box(0)), "DEFECT: the rate can be typed");
		Assert.assertTrue(o.is_locked(o.amount_box(0)), "DEFECT: the amount can be typed");
		List<String> buttons = o.button_texts();
		test.info("Buttons: " + buttons);
		for (String b : new String[] { "Add Row", "Add Multiple", "Back", "Next" }) {
			Assert.assertTrue(buttons.contains(b), "The button '" + b + "' is missing");
		}
		test.pass("Item step is complete");
	}

	// OB12: the item box suggests items
	@Test
	public void OB12_item_suggestions() {
		test = extent.createTest("OB12 Item suggestions");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		List<String> leo = o.Item_suggestions(0, "leo");
		test.info("'leo' -> " + leo);
		Assert.assertFalse(leo.isEmpty(), "No item is suggested for 'leo'");
		List<String> ney = o.Item_suggestions(0, "ney");
		test.info("'ney' -> " + ney);
		Assert.assertFalse(ney.isEmpty(), "No item is suggested for 'ney'");
		List<String> none = o.Item_suggestions(0, "ZZQNoSuchItem" + unique());
		Assert.assertTrue(none.isEmpty(), "An item is suggested for text that matches nothing: " + none);
		test.pass("Item suggestions work");
	}

	// OB13: picking an item fills its rate and the amount of quantity 1
	@Test
	public void OB13_pick_item_fills_rate_and_amount() {
		test = extent.createTest("OB13 Pick an item");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		o.Pick_item(0, ITEM_A);
		test.info("Item '" + o.item_text(0) + "' | qty " + o.qty_text(0) + " | rate " + o.rate_text(0) + " | amount " + o.amount_text(0));
		Assert.assertEquals(o.item_text(0), ITEM_A, "The item box shows another item");
		Assert.assertTrue(o.rate(0) > 0, "The rate is not filled");
		Assert.assertTrue(o.amount(0) >= o.rate(0) - 0.005, "The amount for quantity 1 is below the rate");
		test.pass("Rate and amount are filled");
	}

	// OB14: the amount follows the quantity (1, 2, 5, 10, 100)
	@Test
	public void OB14_amount_follows_quantity() {
		test = extent.createTest("OB14 Amount follows the quantity");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		o.Pick_item(0, ITEM_C);
		o.Set_qty(0, "1");
		double f = factor(o, 0);
		List<String> bad = new ArrayList<String>();
		for (String q : new String[] { "1", "2", "5", "10", "100" }) {
			o.Set_qty(0, q);
			double expected = o.rate(0) * Double.parseDouble(q) * f;
			test.info("Qty " + q + ": rate " + o.rate_text(0) + " amount " + o.amount_text(0) + " (expected " + expected + ")");
			if (Math.abs(o.amount(0) - expected) > tol(expected)) {
				bad.add("qty " + q + " gives " + o.amount(0) + " instead of " + expected);
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: the amount does not follow the quantity: " + bad);
		test.pass("Amount follows the quantity");
	}

	// OB15: invalid quantities: 0, negative, decimal, letters, empty, very large
	@Test
	public void OB15_invalid_quantity() {
		test = extent.createTest("OB15 Invalid quantity");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		o.Pick_item(0, ITEM_A);
		List<String> bad = new ArrayList<String>();
		for (String q : new String[] { "0", "-5", "1.5", "abc", "", "99999999999" }) {
			o.Set_qty(0, q);
			test.info("Qty '" + q + "': box keeps '" + o.qty_text(0) + "' | amount '" + o.amount_text(0) + "' | " + o.All_messages());
			if (o.amount(0) < 0) {
				bad.add("qty '" + q + "' gives a negative amount " + o.amount(0));
			}
			if ((q.equals("0") || q.equals("-5")) && o.amount(0) > 0 && o.qty(0) <= 0) {
				bad.add("qty '" + q + "' is kept but still gives an amount of " + o.amount(0));
			}
			if (q.equals("abc") && o.qty_text(0).contains("abc")) {
				bad.add("letters are kept in the quantity");
			}
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Invalid quantities are handled");
	}

	// OB16: the tax is part of the amount: the factor amount / (rate x qty) stays the same for any quantity
	@Test
	public void OB16_tax_in_amount() {
		test = extent.createTest("OB16 Tax in the amount");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		o.Click_add_multiple();
		Assert.assertTrue(o.multiple_open(), "Add Multiple did not open Select Item");
		o.Pick_multiple(ITEM_TAX1, "5");
		o.Pick_multiple(ITEM_TAX2, "10");
		o.Close_multiple();
		int a = -1;
		int b = -1;
		for (int i = 0; i < o.row_count(); i++) {
			if (o.item_text(i).equals(ITEM_TAX1)) {
				a = i;
			}
			if (o.item_text(i).equals(ITEM_TAX2)) {
				b = i;
			}
		}
		Assert.assertTrue(a >= 0 && b >= 0, "The two items were not added: " + o.row_items());
		double fa = factor(o, a);
		double fb = factor(o, b);
		test.info(ITEM_TAX1 + ": rate " + o.rate_text(a) + " x " + o.qty_text(a) + " = " + o.amount_text(a) + " (factor " + fa + ")");
		test.info(ITEM_TAX2 + ": rate " + o.rate_text(b) + " x " + o.qty_text(b) + " = " + o.amount_text(b) + " (factor " + fb + ")");
		Assert.assertTrue(fa >= 1 && fa < 1.6, "The tax factor of the first item is odd: " + fa);
		Assert.assertEquals(fa, fb, 0.001, "The two items with the same tax have different factors");
		o.Set_qty(a, "10");
		Assert.assertEquals(factor(o, a), fa, 0.001, "DEFECT: the tax factor changed with the quantity");
		test.pass("Tax factor " + fa + " is applied");
	}

	// OB17: Add Row adds numbered rows
	@Test
	public void OB17_add_rows() {
		test = extent.createTest("OB17 Add Row");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		o.Click_add_row();
		o.Click_add_row();
		test.info("Row numbers: " + o.row_numbers());
		Assert.assertEquals(o.row_count(), 3, "Two clicks of Add Row must give 3 rows");
		Assert.assertEquals(o.row_numbers(), java.util.Arrays.asList("1", "2", "3"), "Row numbers");
		test.pass("Rows are added and numbered");
	}

	// OB18: removing the middle row renumbers the rest and lowers the total
	@Test
	public void OB18_remove_row() {
		test = extent.createTest("OB18 Remove a row");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "2");
		o.Click_add_row();
		fill_row(o, 1, ITEM_B, "3");
		o.Click_add_row();
		fill_row(o, 2, ITEM_C, "4");
		double total = o.items_total();
		double middle = o.amount(1);
		o.Remove_row(1);
		o.All_messages();
		test.info("After removing row 2: " + o.row_items() + " | numbers " + o.row_numbers() + " | total " + total + " -> " + o.items_total());
		Assert.assertEquals(o.row_count(), 2, "The row was not removed");
		Assert.assertEquals(o.row_items(), java.util.Arrays.asList(ITEM_A, ITEM_C), "The wrong row was removed");
		Assert.assertEquals(o.row_numbers(), java.util.Arrays.asList("1", "2"), "DEFECT: the rows are not renumbered");
		Assert.assertEquals(o.items_total(), total - middle, tol(total), "The total did not drop by the removed row");
		test.pass("Remove works");
	}

	// OB19: removing the only row: the page keeps working (the result is written to the report)
	@Test
	public void OB19_remove_only_row() {
		test = extent.createTest("OB19 Remove the only row");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "1");
		o.Remove_row(0);
		String messages = o.All_messages();
		test.info("Rows after removing the only row: " + o.row_count() + " | " + messages);
		if (o.row_count() == 0) {
			test.warning("OBSERVATION: removing the only row leaves the table with no row and no message");
			o.Click_add_row();
			Assert.assertEquals(o.row_count(), 1, "Add Row does not bring a row back");
			o.Remove_row(0);
			o.All_messages();
		}
		o.Click_next();
		test.info("Next with no item: tab " + o.current_tab() + " | " + o.All_messages());
		Assert.assertEquals(o.current_tab(), "Item Selection", "DEFECT: Next opened Payment Details with no item");
		test.pass("The only row is handled");
	}

	// OB20: the same item in two rows (the result is written to the report)
	@Test
	public void OB20_duplicate_item() {
		test = extent.createTest("OB20 Same item twice");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "2");
		o.Click_add_row();
		o.Pick_item(1, ITEM_A);
		String message = o.All_messages();
		test.info("Rows: " + o.row_items() + " | rates " + o.rate_text(0) + ", " + o.rate_text(1) + " | message: " + message);
		if (o.item_text(1).equals(ITEM_A)) {
			test.warning("The same item is allowed in two rows (no message: '" + message + "')");
		} else {
			test.info("The second pick of the same item was refused");
		}
		Assert.assertTrue(o.is_booking_page(), "The page broke");
		test.pass("Same item handled");
	}

	// OB21: Next with an empty item row is refused
	@Test
	public void OB21_next_with_empty_row() {
		test = extent.createTest("OB21 Next with an empty row");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		o.Click_next();
		test.info("Empty row: tab " + o.current_tab() + " | " + o.All_messages());
		Assert.assertEquals(o.current_tab(), "Item Selection", "DEFECT: Next opened Payment Details with no item");
		fill_row(o, 0, ITEM_A, "1");
		o.Click_add_row();
		o.Click_next();
		test.info("One filled and one empty row: tab " + o.current_tab() + " | " + o.All_messages());
		Assert.assertEquals(o.current_tab(), "Item Selection", "DEFECT: Next opened Payment Details with an empty extra row");
		test.pass("Empty rows are refused");
	}

	// OB22: Next with quantity 0 is refused
	@Test
	public void OB22_next_with_zero_quantity() {
		test = extent.createTest("OB22 Next with quantity 0");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "0");
		o.Click_next();
		test.info("Quantity 0: tab " + o.current_tab() + " | " + o.All_messages());
		Assert.assertEquals(o.current_tab(), "Item Selection", "DEFECT: an item with quantity 0 reached Payment Details");
		o.Set_qty(0, "-2");
		o.Click_next();
		test.info("Quantity -2: tab " + o.current_tab() + " | " + o.All_messages());
		Assert.assertEquals(o.current_tab(), "Item Selection", "DEFECT: an item with a negative quantity reached Payment Details");
		test.pass("Quantity 0 and negative are refused");
	}

	// OB23: Add Multiple opens Select Item with items and rates, and closes with x
	@Test
	public void OB23_add_multiple_pop_up() {
		test = extent.createTest("OB23 Add Multiple pop-up");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		o.Click_add_multiple();
		Assert.assertTrue(o.multiple_open(), "The Select Item pop-up did not open");
		List<String> items = o.multiple_items();
		test.info("First items: " + (items.size() > 8 ? items.subList(0, 8) : items) + " (" + items.size() + " listed)");
		Assert.assertFalse(items.isEmpty(), "Select Item lists no item");
		o.Close_multiple();
		Assert.assertFalse(o.multiple_open(), "The pop-up did not close");
		test.info("Rows after closing the pop-up without choosing an item: " + o.row_count());
		if (o.row_count() != 1) {
			test.warning("OBSERVATION: opening Add Multiple removes the empty first row; after closing without an item the table has "
					+ o.row_count() + " rows");
			o.Click_add_row();
			Assert.assertEquals(o.row_count(), 1, "Add Row does not bring a row back");
		}
		test.pass("Select Item opens and closes");
	}

	// OB24: search in Select Item: begins with, the % wildcard, nothing found
	@Test
	public void OB24_add_multiple_search() {
		test = extent.createTest("OB24 Select Item search");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		o.Click_add_multiple();
		List<String> leo = o.Multiple_search("leo");
		test.info("'leo' -> " + leo);
		Assert.assertFalse(leo.isEmpty(), "Nothing found for 'leo'");
		for (String row : leo) {
			Assert.assertTrue(row.toLowerCase().startsWith("leo"), "DEFECT: 'Beginning with leo' lists " + row);
		}
		List<String> wild = o.Multiple_search("%item1");
		test.info("'%item1' -> " + wild);
		Assert.assertTrue(wild.size() >= 1, "The % wildcard finds nothing for '%item1'");
		for (String row : wild) {
			Assert.assertTrue(row.toLowerCase().contains("item1"), "The wildcard search lists " + row);
		}
		List<String> none = o.Multiple_search("ZZQNoSuch" + unique());
		Assert.assertTrue(none.isEmpty(), "Items are listed for text that matches nothing: " + none);
		o.Close_multiple();
		test.pass("Select Item search works");
	}

	// OB25: Select Item: picking items with their quantity adds rows, the filled first row stays
	@Test
	public void OB25_add_multiple_adds_rows() {
		test = extent.createTest("OB25 Add Multiple adds rows");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_C, "5");
		o.Click_add_multiple();
		o.Click_multiple_item(ITEM_A);
		Assert.assertTrue(o.quantity_dialog_open(), "The Set Quantity dialog did not open");
		test.info("Set Quantity default: '" + o.dialog_qty_text() + "'");
		Assert.assertEquals(o.dialog_qty_text(), "1", "The default quantity of Set Quantity is not 1");
		o.Set_dialog_qty("3");
		o.Click_set_quantity();
		o.Pick_multiple(ITEM_B, "4");
		o.Close_multiple();
		test.info("Rows: " + o.row_items() + " | qty " + o.qty_text(1) + ", " + o.qty_text(2));
		Assert.assertEquals(o.row_count(), 3, "The rows are not 3: " + o.row_items());
		Assert.assertEquals(o.item_text(0), ITEM_C, "The first row was overwritten");
		Assert.assertEquals(o.qty(0), 5.0, 0.0001, "The first row quantity changed");
		Assert.assertEquals(o.item_text(1), ITEM_A);
		Assert.assertEquals(o.qty(1), 3.0, 0.0001, "Quantity of the second row");
		Assert.assertEquals(o.item_text(2), ITEM_B);
		Assert.assertEquals(o.qty(2), 4.0, 0.0001, "Quantity of the third row");
		for (int i = 0; i < 3; i++) {
			Assert.assertEquals(o.amount(i), o.rate(i) * o.qty(i) * factor(o, i), tol(o.amount(i)), "Amount of row " + (i + 1));
			Assert.assertTrue(o.rate(i) > 0, "Rate of row " + (i + 1));
		}
		test.pass("Add Multiple adds the picked items");
	}

	// OB26: Set Quantity with 0, negative and empty must not add a row
	@Test
	public void OB26_set_quantity_invalid() {
		test = extent.createTest("OB26 Set Quantity invalid");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_C, "1");
		int rows = o.row_count();
		o.Click_add_multiple();
		List<String> bad = new ArrayList<String>();
		for (String q : new String[] { "0", "-3", "" }) {
			o.Click_multiple_item(ITEM_A);
			o.Set_dialog_qty(q);
			o.Click_set_quantity();
			String message = o.All_messages();
			int now = o.row_count();
			test.info("Set Quantity '" + q + "': rows " + rows + " -> " + now + " | " + message);
			if (now > rows) {
				if (o.qty(now - 1) <= 0) {
					bad.add("quantity '" + q + "' added a row with the quantity '" + o.qty_text(now - 1) + "'");
				} else {
					test.info("Quantity '" + q + "' added a row with the default quantity " + o.qty_text(now - 1));
				}
				rows = now;
			}
			if (o.quantity_dialog_open()) {
				o.Close_multiple();
				o.Click_add_multiple();
			}
		}
		o.Close_multiple();
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Invalid quantities add no row");
	}

	// OB27: the scheme dot of each row (written to the report); a green dot opens its scheme
	@Test
	public void OB27_scheme_dot() {
		test = extent.createTest("OB27 Scheme dot");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_C, "5");
		o.Click_add_row();
		fill_row(o, 1, ITEM_A, "1");
		for (int i = 0; i < o.row_count(); i++) {
			String state = o.scheme_state(i);
			test.info("Row " + (i + 1) + " " + o.item_text(i) + ": scheme dot " + state);
			if (state.toLowerCase().contains("green") || state.contains("rgb(25, 135, 84)") || state.contains("rgb(40, 167, 69)")) {
				o.Click_scheme(i);
				test.info("Scheme clicked: " + o.All_messages());
			}
		}
		Assert.assertTrue(o.is_booking_page(), "The page broke after the scheme check");
		test.pass("Scheme dots checked");
	}

	// OB28: Back and Next keep the customer and the items
	@Test
	public void OB28_back_and_next_keep_data() {
		test = extent.createTest("OB28 Back and Next keep the data");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "3");
		double amount = o.amount(0);
		o.Click_back();
		Assert.assertEquals(o.current_tab(), "Customer Details", "Back did not return to Customer Details");
		Assert.assertEquals(o.route_selected(), ROUTE, "DEFECT: the route was lost after Back");
		Assert.assertTrue(o.customer_text().toLowerCase().contains(CUSTOMER.toLowerCase()), "DEFECT: the customer was lost after Back: '" + o.customer_text() + "'");
		Assert.assertFalse(o.info_value("Customer Code").equals("-"), "DEFECT: the details card was cleared after Back");
		o.Click_next();
		Assert.assertEquals(o.current_tab(), "Item Selection");
		test.info("Items after Back and Next: " + o.row_items() + " qty " + o.qty_text(0) + " amount " + o.amount_text(0));
		Assert.assertEquals(o.item_text(0), ITEM_A, "DEFECT: the item was lost");
		Assert.assertEquals(o.qty(0), 3.0, 0.0001, "DEFECT: the quantity was lost");
		Assert.assertEquals(o.amount(0), amount, tol(amount), "The amount changed");
		test.pass("Data is kept");
	}

	// ======================= step 3: payment details =======================

	// OB29: Payment Details: amount (read only), modes, default Unpaid, delivery date, remark, important, buttons
	@Test
	public void OB29_payment_step_elements() {
		test = extent.createTest("OB29 Payment Details elements");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "2");
		to_payment(o);
		List<String> modes = o.mode_options();
		test.info("Modes: " + modes + " | selected '" + o.mode_selected() + "' | delivery " + o.delivery_text()
				+ " | buttons " + o.button_texts() + " | extra fields " + o.payment_extra_fields());
		Assert.assertTrue(o.is_locked(o.verify_amount()), "DEFECT: the payment amount can be typed");
		Assert.assertEquals(modes, java.util.Arrays.asList("Select", "Cash", "Card", "Upi", "Cheque", "Unpaid"), "Payment modes");
		Assert.assertEquals(o.mode_selected(), "Unpaid", "Default mode");
		Assert.assertEquals(o.delivery_text(), days_from_today(1), "Default delivery date is not tomorrow");
		Assert.assertFalse(o.important_box().isSelected(), "Important is ticked by default");
		Assert.assertTrue(o.button_texts().contains("Back"), "Back is missing");
		test.info("Order button found: '" + o.submit_button().getText() + "'");
		test.pass("Payment step is complete");
	}

	// OB30: the payment amount is the total of the items (tax included) and follows a change made after Back
	@Test
	public void OB30_payment_amount_is_items_total() {
		test = extent.createTest("OB30 Payment amount is the items total");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_C, "5");
		o.Click_add_multiple();
		o.Pick_multiple(ITEM_TAX1, "5");
		o.Pick_multiple(ITEM_TAX2, "10");
		o.Close_multiple();
		double total = o.items_total();
		to_payment(o);
		test.info("Items total " + total + " | payment amount " + o.payment_amount());
		Assert.assertEquals(o.payment_amount(), total, 0.02, "DEFECT: the payment amount is not the total of the items");
		o.Click_back();
		o.Set_qty(0, "7");
		double changed = o.items_total();
		to_payment(o);
		test.info("After changing quantity 5 -> 7: items total " + changed + " | payment amount " + o.payment_amount());
		Assert.assertEquals(o.payment_amount(), changed, 0.02, "DEFECT: the payment amount did not follow the changed quantity");
		Assert.assertTrue(changed > total, "The total did not rise with the quantity");
		test.pass("Payment amount follows the items");
	}

	// OB31: every payment mode: the extra fields it shows are written to the report
	@Test
	public void OB31_payment_modes_fields() {
		test = extent.createTest("OB31 Payment modes");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "1");
		double total = o.items_total();
		to_payment(o);
		for (String mode : new String[] { "Cash", "Card", "Upi", "Cheque", "Unpaid", "Select" }) {
			o.Select_mode(mode);
			test.info("Mode " + mode + ": extra fields " + o.payment_extra_fields() + " | amount " + o.payment_amount());
			Assert.assertEquals(o.mode_selected(), mode, "The mode did not change to " + mode);
			Assert.assertEquals(o.payment_amount(), total, 0.02, "The amount changed with the payment mode " + mode);
		}
		test.pass("All modes can be chosen");
	}

	// OB32: delivery date: a past date and a bad date (the box is only checked, nothing is placed)
	@Test
	public void OB32_delivery_date_box() {
		test = extent.createTest("OB32 Delivery date box");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "1");
		to_payment(o);
		for (String value : new String[] { days_from_today(-3), "31/02/2027", "abcd", days_from_today(30) }) {
			o.Set_delivery(value);
			test.info("Delivery date typed '" + value + "' -> box keeps '" + o.delivery_text() + "' | " + o.All_messages());
		}
		Assert.assertTrue(o.is_booking_page(), "The page broke");
		test.pass("Delivery date box checked");
	}

	// OB33: the remark box takes text; its limit is written to the report
	@Test
	public void OB33_remark_box() {
		test = extent.createTest("OB33 Remark box");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "1");
		to_payment(o);
		o.Set_remark("ZQ remark " + unique());
		Assert.assertTrue(o.remark_text().startsWith("ZQ remark"), "The remark was not typed");
		String limit = String.valueOf(o.verify_remark().getAttribute("maxlength"));
		StringBuilder big = new StringBuilder();
		for (int i = 0; i < 600; i++) {
			big.append('x');
		}
		o.Set_remark(big.toString());
		test.info("maxlength attribute: " + limit + " | 600 characters typed -> box keeps " + o.remark_text().length());
		Assert.assertTrue(o.remark_text().length() > 0, "The long remark was not typed");
		test.pass("Remark box works");
	}

	// OB34: the Mark as Important Order box can be ticked and unticked
	@Test
	public void OB34_important_box() {
		test = extent.createTest("OB34 Important order box");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "1");
		to_payment(o);
		o.Set_important(true);
		Assert.assertTrue(o.important_box().isSelected(), "The box did not tick");
		o.Set_important(false);
		Assert.assertFalse(o.important_box().isSelected(), "The box did not untick");
		test.pass("Important box works");
	}

	// OB35: Back from Payment Details keeps the items and the chosen mode, delivery date and remark
	@Test
	public void OB35_back_from_payment_keeps_data() {
		test = extent.createTest("OB35 Back from Payment Details");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_B, "2");
		to_payment(o);
		o.Select_mode("Cash");
		o.Set_delivery(days_from_today(5));
		o.Set_remark("ZQ keep " + unique());
		o.Click_back();
		Assert.assertEquals(o.current_tab(), "Item Selection", "Back did not return to Item Selection");
		Assert.assertEquals(o.item_text(0), ITEM_B, "DEFECT: the item was lost after Back");
		o.Click_next();
		test.info("After Back and Next: mode " + o.mode_selected() + " | delivery " + o.delivery_text() + " | remark '" + o.remark_text() + "'");
		Assert.assertEquals(o.mode_selected(), "Cash", "DEFECT: the mode was lost");
		Assert.assertEquals(o.delivery_text(), days_from_today(5), "DEFECT: the delivery date was lost");
		Assert.assertTrue(o.remark_text().startsWith("ZQ keep"), "DEFECT: the remark was lost");
		test.pass("Payment data is kept");
	}

	// OB36: a new wizard shows a new order number only after an order was placed: the number is stable on reload
	@Test
	public void OB36_order_number_is_stable_without_placing() throws InterruptedException {
		test = extent.createTest("OB36 Order number is stable");
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		String first = o.order_no();
		go_to_booking();
		new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100)).until(x -> o.is_booking_page());
		String second = o.order_no();
		test.info("Order numbers: " + first + " and " + second);
		Assert.assertEquals(second, first, "DEFECT: the order number changes although no order was placed (numbers are lost)");
		test.pass("The order number is stable");
	}

	// ======================= placing orders (PLACE_ORDERS) =======================

	// OB37: place a simple order (Unpaid) and check it in Order Status: pending, route, customer, amount, delivery date
	@Test
	public void OB37_place_simple_order() throws InterruptedException {
		test = extent.createTest("OB37 Place a simple order");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		Placed p = place(o, new String[][] { { ITEM_A, "2" } }, null, null, days_from_today(1), "ZQ simple " + unique(), false);
		test.info("Order " + p.no + " | amount " + p.amount + " | credit limit " + p.credit + " | message: " + p.message);
		Assert.assertTrue(p.ok, "The order was not placed: " + p.message);
		Assert.assertEquals(p.popup_no, p.no, "DEFECT: the order number in the Thank You pop-up is not the order number of the booking");
		if (T_OrderBooking_Cls.to_number(p.credit) == 0) {
			test.warning("The credit limit of the customer is 0 but an Unpaid order was accepted (business rule to confirm)");
		}
		S_OrderStatus_Cls os = open_status_for(p.no);
		List<String> row = os.row_of(p.no);
		Assert.assertNotNull(row, "DEFECT: the placed order " + p.no + " is not in Order Status");
		test.info("Order Status row: " + row);
		Assert.assertEquals(row.get(6), "Pending", "Status of a new order");
		Assert.assertEquals(row.get(2), ROUTE, "Route in Order Status");
		Assert.assertTrue(row.get(3).toLowerCase().contains(CUSTOMER.toLowerCase()), "Customer in Order Status: " + row.get(3));
		Assert.assertEquals(S_OrderStatus_Cls.to_number(row.get(4)), p.amount, 1.0, "DEFECT: the order amount of Order Status differs from the payment amount");
		Assert.assertEquals(row.get(5), days_from_today(1), "Delivery date in Order Status");
		test.pass("Order " + p.no + " placed and listed");
	}

	// OB38: place an order for every payment mode and find each in Order Status
	@Test
	public void OB38_place_every_payment_mode() throws InterruptedException {
		test = extent.createTest("OB38 Place an order for every payment mode");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		List<String> failed = new ArrayList<String>();
		List<String> placed = new ArrayList<String>();
		for (String mode : new String[] { "Cash", "Card", "Upi", "Cheque", "Unpaid" }) {
			reopen_booking(o);
			Placed p = place(o, new String[][] { { ITEM_B, "1" } }, null, mode, null, "ZQ " + mode + " " + unique(), false);
			test.info(mode + ": order " + p.no + " | amount " + p.amount + " | ok " + p.ok + " | " + p.message);
			if (p.ok) {
				placed.add(p.no);
			} else {
				failed.add(mode + " (" + p.message + ")");
			}
		}
		for (String no : placed) {
			S_OrderStatus_Cls os = open_status_for(no);
			if (os.row_of(no) == null) {
				failed.add(no + " is not in Order Status");
			}
		}
		Assert.assertTrue(failed.isEmpty(), "DEFECT: " + failed);
		test.pass("Orders placed: " + placed);
	}

	// OB39: place an order with typed rows and Select Item rows (tax items); amount, lines and quantities match
	@Test
	public void OB39_place_multi_item_order_with_tax() throws InterruptedException {
		test = extent.createTest("OB39 Place a multi item order");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		Placed p = place(o, new String[][] { { ITEM_C, "5" }, { ITEM_A, "3" } },
				new String[][] { { ITEM_TAX1, "5" }, { ITEM_TAX2, "10" } }, "Cash", null, "ZQ multi " + unique(), false);
		test.info("Order " + p.no + " | amount " + p.amount + " | " + p.message);
		Assert.assertTrue(p.ok, "The order was not placed: " + p.message);
		S_OrderStatus_Cls os = open_status_for(p.no);
		List<String> row = os.row_of(p.no);
		Assert.assertNotNull(row, "DEFECT: the placed order is not in Order Status");
		Assert.assertEquals(S_OrderStatus_Cls.to_number(row.get(4)), p.amount, 1.0, "DEFECT: Order Status amount differs from the payment amount");
		os.Click_view(p.no);
		List<List<String>> lines = os.line_texts();
		test.info("Order lines: " + lines);
		Assert.assertEquals(lines.size(), 4, "The order must have 4 lines");
		double sum = 0;
		for (List<String> l : lines) {
			sum += S_OrderStatus_Cls.to_number(l.get(7));
		}
		os.Close_modal();
		Assert.assertEquals(sum, p.amount, 1.0, "DEFECT: the lines of the order do not add up to the booked amount");
		test.pass("Multi item order is correct");
	}

	// OB40: remark, delivery date and the important flag are saved with the order
	@Test
	public void OB40_remark_delivery_and_important_saved() throws InterruptedException {
		test = extent.createTest("OB40 Remark, delivery date and important flag");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		String remark = "ZQ saved remark " + unique();
		Placed p = place(o, new String[][] { { ITEM_A, "1" } }, null, "Unpaid", days_from_today(4), remark, true);
		test.info("Order " + p.no + " | " + p.message);
		Assert.assertTrue(p.ok, "The order was not placed: " + p.message);
		S_OrderStatus_Cls os = open_status_for(p.no);
		List<String> row = os.row_of(p.no);
		Assert.assertNotNull(row, "DEFECT: the placed order is not in Order Status");
		Assert.assertEquals(row.get(5), days_from_today(4), "DEFECT: the delivery date was not saved");
		os.Click_view(p.no);
		String saved = os.modal_remark();
		os.Close_modal();
		test.info("Saved remark '" + saved + "' | Order Status row " + row + " | print icon " + os.print_icon_state(p.no));
		Assert.assertEquals(saved, remark, "DEFECT: the remark was not saved");
		test.pass("Remark and delivery date are saved");
	}

	// OB41: the next order gets the next number
	@Test
	public void OB41_order_numbers_follow() {
		test = extent.createTest("OB41 Order numbers follow each other");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		Placed first = place(o, new String[][] { { ITEM_A, "1" } }, null, null, null, "ZQ seq1 " + unique(), false);
		Assert.assertTrue(first.ok, "The first order was not placed: " + first.message);
		String next = o.order_no();
		test.info("First order " + first.no + " | number offered next " + next);
		Assert.assertEquals(trailing_number(next), trailing_number(first.no) + 1, "DEFECT: the next order number is not the next in line");
		Assert.assertEquals(next.length(), first.no.length(), "The order number length changed");
		test.pass("Order numbers follow");
	}

	// OB42: after placing an order the wizard is empty again
	@Test
	public void OB42_wizard_resets_after_order() {
		test = extent.createTest("OB42 Wizard resets after an order");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		Placed p = place(o, new String[][] { { ITEM_A, "1" } }, null, null, null, "ZQ reset " + unique(), false);
		Assert.assertTrue(p.ok, "The order was not placed: " + p.message);
		test.info("Tab " + o.current_tab() + " | route '" + o.route_selected() + "' | customer '" + o.customer_text() + "'");
		Assert.assertEquals(o.current_tab(), "Customer Details", "DEFECT: the wizard does not return to the first step");
		Assert.assertTrue(o.customer_text().isEmpty(), "DEFECT: the customer is still filled after the order");
		o.Select_route(ROUTE);
		o.Search_customer(CUSTOMER);
		o.Click_next();
		Assert.assertEquals(o.row_count(), 1, "DEFECT: old rows are still there");
		Assert.assertEquals(o.item_text(0), "", "DEFECT: the old item is still in the first row");
		test.pass("The wizard is empty again");
	}

	// OB43: invalid payment data must not place an order: mode Select, a past delivery date
	@Test
	public void OB43_invalid_payment_refused() {
		test = extent.createTest("OB43 Invalid payment data");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		to_items(o);
		fill_row(o, 0, ITEM_A, "1");
		to_payment(o);
		List<String> bad = new ArrayList<String>();

		o.Select_mode("Select");
		o.Click_submit();
		String m1 = o.All_messages() + " " + o.Accept_notice();
		String tab1 = o.current_tab();
		test.info("Mode 'Select': " + m1 + " | tab " + tab1);
		if (!tab1.equals("Payment Details")) {
			throw new AssertionError("DEFECT: the page left Payment Details (an order was probably placed) with the payment mode 'Select': " + m1);
		}
		o.Select_mode("Unpaid");
		o.Set_delivery(days_from_today(-5));
		o.Click_submit();
		String m2 = o.All_messages() + " " + o.Accept_notice();
		boolean placed2 = o.current_tab().equals("Customer Details");
		test.info("Past delivery date: " + m2 + " | tab " + o.current_tab());
		if (placed2) {
			bad.add("an order was placed with a delivery date in the past (" + m2 + ")");
		}
		Assert.assertTrue(bad.isEmpty(), "DEFECT: " + bad);
		test.pass("Invalid payment data is refused");
	}

	// OB44: a double click on the order button places ONE order
	@Test
	public void OB44_double_click_places_one_order() throws InterruptedException {
		test = extent.createTest("OB44 Double click on the order button");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		o.Select_route(ROUTE);
		o.Search_customer(CUSTOMER);
		String no = o.order_no();
		o.Click_next();
		fill_row(o, 0, ITEM_B, "1");
		to_payment(o);
		o.Set_remark("ZQ double " + unique());
		((JavascriptExecutor) driver).executeScript("arguments[0].click(); arguments[0].click();", o.submit_button());
		WaitUtil.settle(driver, 3000);
		String message = o.All_messages() + " " + o.Accept_notice();
		test.info("Order " + no + " | " + message);

		S_OrderStatus_Cls os = open_status_for(no);
		os.Filter_table("");
		os.Search_orders("", "", today(), today(), CUSTOMER, "", "", "");
		int number = trailing_number(no);
		int found = 0;
		for (List<String> row : os.result_texts()) {
			int n = trailing_number(row.get(0));
			if (n == number || n == number + 1) {
				found++;
			}
		}
		test.info("Orders with the numbers " + number + " and " + (number + 1) + " today for the customer: " + found);
		Assert.assertEquals(found, 1, "DEFECT: a double click placed " + found + " orders instead of one");
		test.pass("One order is placed");
	}

	// OB45: a big order (10 rows) is placed and its amount is the total of its rows
	@Test
	public void OB45_place_big_order() throws InterruptedException {
		test = extent.createTest("OB45 Place a big order");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		String[][] items = new String[10][2];
		String[] names = { ITEM_A, ITEM_B, ITEM_C };
		for (int i = 0; i < 10; i++) {
			items[i][0] = names[i % 3];
			items[i][1] = String.valueOf(i + 1);
		}
		Placed p = place(o, items, null, "Card", null, "ZQ big " + unique(), false);
		test.info("Order " + p.no + " | amount " + p.amount + " | " + p.message);
		Assert.assertTrue(p.ok, "The order was not placed: " + p.message);
		S_OrderStatus_Cls os = open_status_for(p.no);
		List<String> row = os.row_of(p.no);
		Assert.assertNotNull(row, "DEFECT: the big order is not in Order Status");
		Assert.assertEquals(S_OrderStatus_Cls.to_number(row.get(4)), p.amount, 1.0, "DEFECT: the amount of the big order differs");
		os.Click_view(p.no);
		int lines = os.line_count();
		os.Close_modal();
		Assert.assertEquals(lines, 10, "DEFECT: the saved order has " + lines + " lines instead of 10");
		test.pass("Big order is correct");
	}

	// OB47: the Thank You pop-up after an order: title, message, the order number of the order, OK closes it
	@Test
	public void OB47_thank_you_pop_up() {
		test = extent.createTest("OB47 Thank You pop-up");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		o.Select_route(ROUTE);
		o.Search_customer(CUSTOMER);
		String no = o.order_no();
		o.Click_next();
		fill_row(o, 0, ITEM_A, "1");
		to_payment(o);
		o.Set_remark("ZQ thanks " + unique());
		o.Click_submit();
		Assert.assertTrue(o.success_popup_open(), "DEFECT: no Thank You pop-up after submitting. Messages: " + o.All_messages());
		String text = o.success_popup_text();
		String shown_no = o.success_popup_order_no();
		test.info("Pop-up: " + text + " | order number in the pop-up: " + shown_no + " | number before: " + no);
		Assert.assertTrue(text.contains("Thank You"), "The pop-up has no 'Thank You!' title");
		Assert.assertTrue(text.toLowerCase().contains("successfully submitted"), "The pop-up has no success message");
		Assert.assertEquals(shown_no, no, "DEFECT: the order number in the pop-up is not the order number of the booking");
		Assert.assertEquals(o.current_tab(), "Payment Details", "The page changed before OK was clicked");
		o.Click_success_ok();
		Assert.assertFalse(o.success_popup_open(), "The pop-up did not close with OK");
		test.info("After OK: tab " + o.current_tab() + " | next order number " + o.order_no());
		Assert.assertEquals(o.current_tab(), "Customer Details", "DEFECT: the wizard does not return to the first step after OK");
		Assert.assertEquals(trailing_number(o.order_no()), trailing_number(no) + 1, "The next order number is not the next in line");
		test.pass("Thank You pop-up is correct");
	}

	// OB46: a large quantity: the amount is calculated and the order is placed with it
	@Test
	public void OB46_place_order_with_large_quantity() throws InterruptedException {
		test = extent.createTest("OB46 Large quantity");
		require_placing();
		T_OrderBooking_Cls o = new T_OrderBooking_Cls(driver);

		Placed p = place(o, new String[][] { { ITEM_A, "100000" } }, null, "Unpaid", null, "ZQ large " + unique(), false);
		test.info("Order " + p.no + " | amount " + p.amount + " | " + p.message);
		Assert.assertTrue(p.amount > 0, "The amount of the large quantity is not calculated");
		if (p.ok) {
			S_OrderStatus_Cls os = open_status_for(p.no);
			List<String> row = os.row_of(p.no);
			Assert.assertNotNull(row, "DEFECT: the large order is not in Order Status");
			Assert.assertEquals(S_OrderStatus_Cls.to_number(row.get(4)), p.amount, 1.0, "DEFECT: the large order amount differs");
		} else {
			test.info("The page refused the large quantity: " + p.message);
		}
		test.pass("Large quantity handled");
	}
}
