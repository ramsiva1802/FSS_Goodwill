package PagePkg;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/*
 * NOTES - A_Login_Cls (Page Object Model)
 * -------------------------------------
 * One class per web page. This class holds:
 *   1. the locators of the login page elements (WebElements)
 *   2. methods to verify and interact with those elements
 * The test class (A_Login_Test) never uses locators directly,
 * it only calls the methods here. If the page changes, only this file changes.
 */
public class A_Login_Cls {

	// driver is passed in from the test class (it comes from BaseCls)
	WebDriver driver;

	// explicit wait: waits up to 20s for a condition, then continues as soon as it is true
	WebDriverWait wait;

	/*
	 * ---------- WebElements ----------
	 * @FindBy tells Selenium how to find the element.
	 * PageFactory.initElements() (called in the constructor) fills these fields.
	 * Elements are looked up lazily, when a method first uses them.
	 */

	// Logo images: located by their CSS class using xpath
	@FindBy(xpath = "//img[@class='left-logo']")
	WebElement left_logo;

	@FindBy(xpath = "//img[@class='card-logo-small']")
	WebElement card_logo;

	// "Welcome back" heading: located by its visible text.
	// normalize-space() trims extra spaces so the match is reliable
	@FindBy(xpath = "//*[normalize-space()='Welcome back']")
	WebElement welcome_heading;

	// Input fields: id is the best locator because it is unique and fast
	@FindBy(id = "email")
	WebElement Username;

	@FindBy(id = "password")
	WebElement Password;

	// Sign In button: located by class name
	@FindBy(xpath = "//button[@class='btn-login']")
	WebElement Signin_btn;

	// Link located by its visible text
	@FindBy(xpath = "//a[normalize-space()='Forgot password?']")
	WebElement Forgot_pwd;

	// TODO: do one wrong login, inspect the error message, and replace this locator.
	// This one is a guess: it matches any element whose class contains error/alert/toast.
	@FindBy(xpath = "//*[contains(@class,'error') or contains(@class,'alert') or contains(@class,'toast')]")
	WebElement Error_msg;

	/*
	 * ---------- Constructor ----------
	 * Runs when the test does: new A_Login_Cls(driver)
	 * Order matters: set driver first, then create wait (it needs the driver),
	 * then initElements so the @FindBy fields are ready.
	 */
	public A_Login_Cls(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(20), Duration.ofMillis(100));
		PageFactory.initElements(driver, this);
	}

	/*
	 * ---------- Getter methods for verification ----------
	 * They only return the element. The test decides what to check,
	 * for example: Assert.assertTrue(login.verify_username().isDisplayed())
	 */
	public WebElement verify_left_logo() {
		return left_logo;
	}

	public WebElement verify_card_logo() {
		return card_logo;
	}

	public WebElement verify_heading() {
		return welcome_heading;
	}

	public WebElement verify_username() {
		return Username;
	}

	public WebElement verify_password() {
		return Password;
	}

	public WebElement verify_signin() {
		return Signin_btn;
	}

	public WebElement verify_forgot() {
		return Forgot_pwd;
	}

	// Waits until the error message is visible, then returns it (test reads .getText())
	public WebElement Error_message() {
		wait.until(ExpectedConditions.visibilityOf(Error_msg));
		return Error_msg;
	}

	/*
	 * ---------- Action methods ----------
	 */

	// Full login in one call: wait for the field, clear old text, type, click Sign In.
	// clear() makes sure each test case starts with empty fields.
	public void Login(String user, String pass) {
		wait.until(ExpectedConditions.visibilityOf(Username));
		Username.clear();
		Username.sendKeys(user);
		Password.clear();
		Password.sendKeys(pass);
		Signin_btn.click();
	}

	// Same login, but submits by pressing ENTER in the password field
	// instead of clicking the Sign In button
	public void Login_with_enter(String user, String pass) {
		wait.until(ExpectedConditions.visibilityOf(Username));
		Username.clear();
		Username.sendKeys(user);
		Password.clear();
		Password.sendKeys(pass + Keys.ENTER);
	}

	// elementToBeClickable waits until the link is visible AND enabled
	public void Click_forgot() {
		wait.until(ExpectedConditions.elementToBeClickable(Forgot_pwd));
		Forgot_pwd.click();
	}

	// Browser back button, used to return to the login page
	public void Back() {
		driver.navigate().back();
	}

	/*
	 * ---------- Result check methods ----------
	 * They return true/false so the test can assert on the outcome of a login.
	 */

	// true if the login form is still on screen -> used after a FAILED login.
	// findElements (plural) returns an empty list instead of throwing an error
	// when the element is gone, so it is safe to use here.
	public boolean is_login_page() {
		return driver.findElements(By.id("email")).stream().anyMatch(WebElement::isDisplayed);
	}

	// true if the login form disappears within 10s -> used after a VALID login,
	// meaning the app moved to the next page.
	// A separate 10s wait is used so failed cases do not wait the full 20s.
	public boolean login_success() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10), Duration.ofMillis(100)).until(ExpectedConditions.invisibilityOf(Username));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}
}