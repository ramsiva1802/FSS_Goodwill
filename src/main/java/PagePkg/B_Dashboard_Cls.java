package PagePkg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * NOTES - B_Dashboard_Cls (Page Object Model)
 * -----------------------------------------
 * Page object for the page shown after login: /Home/DashBoard.
 * Fixed elements (company name, Home, profile, Logout, section headings) use @FindBy.
 * The menu modules and their sub modules are found by their TEXT at run time,
 * so one method works for every module instead of one @FindBy per menu item.
 */
public class B_Dashboard_Cls {

	WebDriver driver;
	WebDriverWait wait;

	// names of the top menu modules, exactly as shown on screen
	public static final String[] MODULES = { "Company Info", "Employee Info", "Stock Item", "Customer Details",
			"Device", "Field Management", "Reports", "Scheme" };

	// ---------- WebElements ----------

	// Company name at the top left (clicking it should refresh the dashboard)
	@FindBy(xpath = "//*[contains(text(),'GOODWILL POLYMERS FSS')]")
	WebElement Company_name;

	@FindBy(xpath = "(//a[normalize-space()='Home'])[1]")
	WebElement Home;

	// "Hi, AppAdmin" button at the top right, opens the profile menu
	@FindBy(xpath = "//*[contains(text(),'AppAdmin')]")
	WebElement Profile;

	// Logout option inside the profile menu
	@FindBy(xpath = "//a[@class='btn btn-default btn-flat float-right']")
	WebElement Logout;

	// Section headings on the dashboard
	@FindBy(xpath = "//*[contains(text(),'Monthly Sales Trend')]")
	WebElement Sales_trend;

	@FindBy(xpath = "//*[contains(text(),\"Today's Activity\")]")
	WebElement Today_activity;

	@FindBy(xpath = "//*[contains(text(),'Monthly Sales Volume')]")
	WebElement Sales_volume;

	@FindBy(xpath = "//*[contains(text(),'Monthly Distribution')]")
	WebElement Monthly_distribution;

	// ---------- Constructor ----------
	public B_Dashboard_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
		PageFactory.initElements(driver, this);
	}

	// ---------- Getter methods for verification ----------
	public WebElement verify_company() {
		return Company_name;
	}

	public WebElement verify_home() {
		return Home;
	}

	public WebElement verify_profile() {
		return Profile;
	}

	public WebElement verify_sales_trend() {
		return Sales_trend;
	}

	public WebElement verify_activity() {
		return Today_activity;
	}

	public WebElement verify_volume() {
		return Sales_volume;
	}

	public WebElement verify_distribution() {
		return Monthly_distribution;
	}

	// The module link in the top menu, found by its text
	public WebElement verify_module(String module) {
		return driver.findElement(module_by(module));
	}

	// Logout is only visible after the profile menu is opened
	public WebElement verify_logout() {
		wait.until(ExpectedConditions.visibilityOf(Logout));
		return Logout;
	}

	// ---------- Locator builders (text based) ----------

	// top menu link only: its text starts with the module name and it is NOT inside a dropdown
	private By module_by(String module) {
		return By.xpath("//a[starts-with(normalize-space(),'" + module
				+ "')][not(ancestor::*[contains(@class,'dropdown-menu')])]");
	}

	// the links inside the dropdown that belongs to this module
	private By submenu_by(String module) {
		return By.xpath("//a[starts-with(normalize-space(),'" + module
				+ "')][not(ancestor::*[contains(@class,'dropdown-menu')])]"
				+ "/following-sibling::*[contains(@class,'dropdown-menu')]//a");
	}

	// a normal click; when something covers the element (the preloader of a hidden window) the cover is removed
	// and the click is repeated, at last with JavaScript
	private void safe_click(WebElement element) {
		try {
			element.click();
		} catch (org.openqa.selenium.ElementClickInterceptedException e) {
			UtillPkg.WaitUtil.unblock(driver);
			try {
				element.click();
			} catch (org.openqa.selenium.ElementClickInterceptedException again) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
	}

	// ---------- Fast navigation (module tests) ----------
	// FAST_NAV (set by BaseCls: false for the Login and Dashboard tests): after a module was opened once by the
	// menu, its address is remembered. Next time Click_home does nothing (it is only noted) and Click_submenu_*
	// opens the address directly, so a test does not walk Home > menu > sub menu again.
	public static boolean FAST_NAV = true;
	private static final java.util.Map<String, String> NAV_CACHE = new java.util.HashMap<String, String>();
	private static boolean home_deferred = false;

	// opens the remembered address of a sub module; false when it is not known yet
	private boolean fast_open(String key) {
		String address = FAST_NAV ? NAV_CACHE.get(key) : null;
		if (address == null) {
			return false;
		}
		home_deferred = false;
		driver.get(address);
		return true;
	}

	// the real Home click that Click_home skipped (needed only when the address is not known yet)
	private void do_deferred_home() {
		if (home_deferred) {
			home_deferred = false;
			wait.until(ExpectedConditions.elementToBeClickable(Home));
			safe_click(Home);
		}
	}

	// remembers the address of the page that the menu click opened
	private void remember(String key, String before) {
		if (!FAST_NAV) {
			return;
		}
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10), Duration.ofMillis(100)).until(d -> !d.getCurrentUrl().equals(before));
			NAV_CACHE.put(key, driver.getCurrentUrl());
		} catch (TimeoutException e) {
			// the address could not be read: the menu is used again next time
		}
	}

	public void Click_home() {
		if (FAST_NAV && !NAV_CACHE.isEmpty()) {
			home_deferred = true;
			return;
		}
		home_deferred = false;
		wait.until(ExpectedConditions.elementToBeClickable(Home));
		safe_click(Home);
	}

	// true if the Home link is on the current page (answers instantly, no waiting).
	// Used to detect error pages that have no top menu.
	public boolean is_home_available() {
		return !driver.findElements(By.xpath("(//a[normalize-space()='Home'])[1]")).isEmpty();
	}

	public void Click_company() {
		wait.until(ExpectedConditions.elementToBeClickable(Company_name));
		safe_click(Company_name);
	}

	public void Click_profile() {
		wait.until(ExpectedConditions.elementToBeClickable(Profile));
		safe_click(Profile);
	}

	public void Click_logout() {
		wait.until(ExpectedConditions.elementToBeClickable(Logout));
		safe_click(Logout);
	}

	// Clicks a top menu module (this opens its dropdown)
	public void Click_module(String module) {
		wait.until(ExpectedConditions.elementToBeClickable(module_by(module)));
		safe_click(driver.findElement(module_by(module)));
	}

	// Visible dropdown links of a module (empty list if the module has no dropdown)
	private List<WebElement> visible_submenus(String module) {
		List<WebElement> visible = new ArrayList<WebElement>();
		for (WebElement e : driver.findElements(submenu_by(module))) {
			if (e.isDisplayed()) {
				visible.add(e);
			}
		}
		return visible;
	}

	// Opens the module and returns the names of its sub modules
	public List<String> get_submenu_names(String module) {
		Click_module(module);
		List<String> names = new ArrayList<String>();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(3), Duration.ofMillis(100)).until(d -> !visible_submenus(module).isEmpty());
		} catch (TimeoutException e) {
			return names; // no dropdown: this module is a direct link
		}
		for (WebElement e : visible_submenus(module)) {
			names.add(e.getText().trim());
		}
		return names;
	}

	// Opens the module and clicks its sub module number 'index' (0 = first)
	public void Click_submenu(String module, int index) {
		String key = module + "|#" + index;
		if (fast_open(key)) {
			return;
		}
		do_deferred_home();
		String before = driver.getCurrentUrl();
		Click_module(module);
		wait.until(d -> !visible_submenus(module).isEmpty());
		safe_click(visible_submenus(module).get(index));
		remember(key, before);
	}

	// Opens the module and clicks the sub module with this exact name
	public void Click_submenu_by_name(String module, String name) {
		String key = module + "|=" + name.toLowerCase();
		if (fast_open(key)) {
			return;
		}
		do_deferred_home();
		String before = driver.getCurrentUrl();
		Click_module(module);
		wait.until(d -> !visible_submenus(module).isEmpty());
		for (WebElement e : visible_submenus(module)) {
			if (e.getText().trim().equalsIgnoreCase(name)) {
				safe_click(e);
				remember(key, before);
				return;
			}
		}
		throw new NoSuchElementException("Sub module not found: " + name);
	}

	// Opens the module and clicks the first sub module whose name CONTAINS this text
	// (capital letters ignored), for example "Hierarchy" for "Hierarchy Details"
	public void Click_submenu_containing(String module, String part) {
		String key = module + "|~" + part.toLowerCase();
		if (fast_open(key)) {
			return;
		}
		do_deferred_home();
		String before = driver.getCurrentUrl();
		Click_module(module);
		wait.until(d -> !visible_submenus(module).isEmpty());
		for (WebElement e : visible_submenus(module)) {
			if (e.getText().toLowerCase().contains(part.toLowerCase())) {
				safe_click(e);
				remember(key, before);
				return;
			}
		}
		throw new NoSuchElementException("Sub module containing '" + part + "' not found");
	}

	// ---------- Page refresh check ----------
	// Leaves a marker on the current page. A real reload wipes it.
	public void set_marker() {
		((JavascriptExecutor) driver).executeScript("window.refreshMarker = 'before';");
	}

	// true if the marker is gone within 10s, meaning the page was reloaded
	public boolean page_refreshed() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10), Duration.ofMillis(100)).ignoring(WebDriverException.class)
					.until(d -> ((JavascriptExecutor) d).executeScript("return window.refreshMarker;") == null);
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}
}