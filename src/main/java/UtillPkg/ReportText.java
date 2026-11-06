package UtillPkg;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.testng.ITestResult;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.model.Log;

/*
 * ReportText - turns the result of a test into plain sentences for the report.
 *
 * For a FAILED test the report shows: what the test had done so far (the steps it logged), the step that went wrong
 * (the page action or the check), WHAT went wrong in plain words (and whether it looks like an application problem
 * or a script / page-speed problem), where it happened in the code, and what the screen showed at that moment.
 * For a PASSED test it shows what was done and checked and the conclusion.
 * For a SKIPPED test it shows why it did not run.
 * Used by BaseCls after every test; the tests themselves need no change.
 */
public final class ReportText {

	private ReportText() {
	}

	// a plain description of a problem
	public static final class Finding {
		public final String category;   // what kind of problem it is
		public final String what;       // what went wrong, in plain words

		Finding(String category, String what) {
			this.category = category;
			this.what = what;
		}
	}

	private static final int MAX_STEPS = 14;
	private static final int MAX_LINE = 220;

	// ======================= explaining an exception =======================

	private static String first_line(Throwable t) {
		String message = t.getMessage();
		if (message == null) {
			return "";
		}
		message = message.trim();
		int cut = message.indexOf('\n');
		return (cut >= 0 ? message.substring(0, cut) : message).trim();
	}

	private static String shorten(String text, int max) {
		String clean = text == null ? "" : text.replaceAll("\\s+", " ").trim();
		return clean.length() > max ? clean.substring(0, max - 3) + "..." : clean;
	}

	public static Finding explain(Throwable original) {
		Throwable t = original;
		while (t instanceof InvocationTargetException && t.getCause() != null) {
			t = t.getCause();
		}
		String msg = t.getMessage() == null ? "" : t.getMessage();
		String line = first_line(t);

		if (t instanceof AssertionError) {
			return explain_assertion(msg);
		}
		if (t instanceof UnhandledAlertException) {
			Matcher m = Pattern.compile("(?s)Alert text\\s*:\\s*(.*?)\\}").matcher(msg);
			String text = m.find() ? m.group(1).trim() : "";
			return new Finding("UNEXPECTED BROWSER ALERT",
					"A browser pop-up (alert) opened while the test was working on the page" + (text.isEmpty() ? "" : ": \"" + text + "\"")
							+ ". The test could not continue with the page behind it. If this message is a site message, "
							+ "the site raised it for the action just before the error.");
		}
		if (t instanceof NoSuchElementException && msg.contains("Unable to locate element")) {
			Matcher m = Pattern.compile("\"selector\":\"(.*?)\"\\}").matcher(msg);
			String selector = m.find() ? m.group(1).replace("\\\"", "\"") : line;
			return new Finding("ITEM NOT FOUND ON THE PAGE",
					"The test could not find an item on the screen (a button, box, link, drop down or table cell). "
							+ "Either the screen is different from what the test expects (possible application change or defect) "
							+ "or the page was not ready. Item looked for: " + selector);
		}
		if (t instanceof NoSuchElementException) {
			return new Finding("ITEM NOT FOUND ON THE PAGE", "The test looked for something on the screen and it was not there: "
					+ line);
		}
		if (t instanceof TimeoutException) {
			Matcher m = Pattern.compile("waiting for (.*?) \\(tried for (\\d+) second").matcher(msg.replace('\n', ' '));
			if (m.find()) {
				String condition = m.group(1);
				if (condition.contains("Lambda")) {
					condition = "a screen change that the test was waiting for";
				}
				return new Finding("WAIT TIMED OUT",
						"The test waited " + m.group(2) + " seconds for: " + shorten(condition, 160)
								+ ". It did not happen in time, so the screen either did not show the expected result "
								+ "(possible application defect) or was too slow.");
			}
			return new Finding("WAIT TIMED OUT", "The test waited for something on the screen and it did not happen in time. "
					+ shorten(line, 200));
		}
		if (t instanceof ElementClickInterceptedException) {
			return new Finding("CLICK BLOCKED",
					"The test tried to click a button / link but another item (a pop-up, a loading cover or the page footer) "
							+ "was on top of it, so the click did not reach it. " + shorten(line, 160));
		}
		if (t instanceof ElementNotInteractableException) {
			return new Finding("ITEM CANNOT BE USED",
					"The item is on the page but cannot be used now (it is hidden, disabled or read-only). " + shorten(line, 160));
		}
		if (t instanceof StaleElementReferenceException) {
			return new Finding("PAGE CHANGED WHILE IN USE",
					"The page refreshed or redrew itself while the test was using an item on it, so the item was gone. "
							+ "Running the test again usually passes; if it fails every time the page keeps reloading.");
		}
		if (t instanceof NoSuchSessionException || t instanceof NoSuchWindowException
				|| (t instanceof WebDriverException && (msg.contains("invalid session id") || msg.contains("no such window")
						|| msg.contains("disconnected") || msg.contains("chrome not reachable")))) {
			return new Finding("BROWSER CLOSED OR CRASHED",
					"The Chrome window was closed or stopped responding while the test was running (not an application problem). "
							+ "Run the test again and keep the browser window open.");
		}
		if (t instanceof NoAlertPresentException) {
			return new Finding("EXPECTED ALERT NOT SHOWN", "The test expected a browser alert but none was open.");
		}
		if (t instanceof IndexOutOfBoundsException) {
			return new Finding("EXPECTED ITEM MISSING",
					"The test expected more items (rows, options, boxes) than the screen showed: " + shorten(line, 200));
		}
		if (t instanceof NullPointerException) {
			return new Finding("SCRIPT PROBLEM", "The test used a value that was empty (nothing was found or saved by an earlier step).");
		}
		if (t instanceof WebDriverException) {
			return new Finding("BROWSER COMMAND FAILED", "The browser refused a command from the test: " + shorten(line, 220));
		}
		return new Finding("UNEXPECTED PROBLEM", t.getClass().getSimpleName() + (line.isEmpty() ? "" : ": " + shorten(line, 220)));
	}

	// TestNG writes a failed check as "<message> expected [<expected>] but found [<actual>]"
	private static Finding explain_assertion(String message) {
		String msg = message == null ? "" : message.trim();
		boolean defect = msg.toUpperCase().startsWith("DEFECT");
		String category = defect ? "APPLICATION DEFECT (probable)" : "CHECK FAILED (application did not behave as expected)";
		Matcher m = Pattern.compile("(?s)^(.*?)\\s*expected \\[(.*?)\\] but found \\[(.*?)\\]\\s*$").matcher(msg);
		if (m.matches()) {
			String check = m.group(1).trim();
			String expected = m.group(2);
			String found = m.group(3);
			if (check.isEmpty()) {
				check = "a value on the screen";
			}
			if (expected.equals("true") || expected.equals("false")) {
				return new Finding(category, "This check did not hold: \"" + shorten(check, 260) + "\" (expected "
						+ expected + ", but it was " + found + ").");
			}
			return new Finding(category, "The check \"" + shorten(check, 200) + "\" failed. Expected: [" + shorten(expected, 200)
					+ "]  but the screen / data had: [" + shorten(found, 200) + "].");
		}
		if (msg.isEmpty()) {
			return new Finding(category, "A check in the test failed (no message was written for it).");
		}
		return new Finding(category, "The check failed: " + shorten(msg, 420));
	}

	// ======================= where it happened =======================

	private static String humanize(String method) {
		return method.replace('_', ' ').trim();
	}

	// "E_Employee_Cls" -> "Employee"
	private static String page_name(String className) {
		String simple = className.substring(className.lastIndexOf('.') + 1);
		simple = simple.replaceFirst("_(Cls|Test)$", "").replaceFirst("^[A-Z]{1,2}_", "");
		return simple.replace('_', ' ');
	}

	private static boolean usable(StackTraceElement e) {
		return !e.getMethodName().contains("lambda$") && !e.getMethodName().startsWith("access$");
	}

	// the frame of the test method (TestPkg) that is closest to the error
	private static StackTraceElement test_frame(Throwable t) {
		for (StackTraceElement e : t.getStackTrace()) {
			if (e.getClassName().startsWith("TestPkg.") && usable(e)) {
				return e;
			}
		}
		return null;
	}

	// the page action the test called (the PagePkg frame just below the test frame)
	private static StackTraceElement outer_page_frame(Throwable t) {
		StackTraceElement found = null;
		for (StackTraceElement e : t.getStackTrace()) {
			if (e.getClassName().startsWith("TestPkg.")) {
				break;
			}
			if (e.getClassName().startsWith("PagePkg.") && usable(e)) {
				found = e;
			}
		}
		return found;
	}

	// the page frame closest to the error
	private static StackTraceElement inner_page_frame(Throwable t) {
		for (StackTraceElement e : t.getStackTrace()) {
			if (e.getClassName().startsWith("PagePkg.") && usable(e)) {
				return e;
			}
		}
		return null;
	}

	private static String at(StackTraceElement e) {
		return e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1) + "." + e.getMethodName() + "  ("
				+ e.getFileName() + ":" + e.getLineNumber() + ")";
	}

	// ======================= the steps the test logged =======================

	private static List<String> steps(ExtentTest test) {
		List<String> out = new ArrayList<String>();
		try {
			for (Log log : test.getModel().getLogs()) {
				Status s = log.getStatus();
				String details = log.getDetails();
				if (details == null || details.trim().isEmpty() || s == Status.FAIL || s == Status.SKIP) {
					continue;
				}
				if (details.startsWith("RESULT") || details.startsWith("test case ")) {
					continue;
				}
				String prefix = s == Status.PASS ? "[OK] " : s == Status.WARNING ? "[NOTE] " : "";
				out.add(prefix + shorten(details, MAX_LINE));
			}
		} catch (Exception e) {
			// the report model could not be read: the steps are left out
		}
		return out;
	}

	private static String last_pass(ExtentTest test) {
		String found = "";
		try {
			for (Log log : test.getModel().getLogs()) {
				if (log.getStatus() == Status.PASS && log.getDetails() != null && !log.getDetails().startsWith("test case ")
						&& !log.getDetails().startsWith("RESULT")) {
					found = log.getDetails();
				}
			}
		} catch (Exception e) {
			// ignored
		}
		return shorten(found, 300);
	}

	private static void add_steps(StringBuilder sb, List<String> steps, boolean tail) {
		if (steps.isEmpty()) {
			sb.append("  (this test did not write any step details)\n");
			return;
		}
		int from = 0;
		if (steps.size() > MAX_STEPS) {
			from = tail ? steps.size() - MAX_STEPS : 0;
			if (tail) {
				sb.append("  ... ").append(from).append(" earlier steps left out ...\n");
			}
		}
		int to = steps.size() > MAX_STEPS && !tail ? MAX_STEPS : steps.size();
		for (int i = from; i < to; i++) {
			sb.append("  ").append(i + 1).append(". ").append(steps.get(i)).append('\n');
		}
		if (to < steps.size()) {
			sb.append("  ... ").append(steps.size() - to).append(" more steps ...\n");
		}
	}

	// ======================= the screen at the time =======================

	private static final String SCREEN_JS = "return (function () { var out = [];"
			+ " var sel = '.swal2-popup, .toast, .alert, .notyf__message, .text-danger, .invalid-feedback, .field-validation-error';"
			+ " document.querySelectorAll(sel).forEach(function (e) {"
			+ "   if (e.offsetParent !== null || e.classList.contains('swal2-popup')) {"
			+ "     var t = (e.innerText || '').replace(/\\s+/g, ' ').trim();"
			+ "     if (t && out.indexOf(t) < 0) { out.push(t.slice(0, 160)); } } });"
			+ " return out.slice(0, 6).join(' || '); })()";

	private static String screen_text(WebDriver driver) {
		StringBuilder sb = new StringBuilder();
		try {
			sb.append("  Page address : ").append(driver.getCurrentUrl()).append('\n');
			Object messages = ((JavascriptExecutor) driver).executeScript(SCREEN_JS);
			String text = messages == null ? "" : String.valueOf(messages).trim();
			sb.append("  Messages shown on the screen : ").append(text.isEmpty() ? "(none)" : text).append('\n');
		} catch (Exception e) {
			sb.append("  (the screen could not be read: the browser is closed or a browser alert is open)\n");
		}
		return sb.toString();
	}

	// ======================= the report texts =======================

	public static String title(ExtentTest test, ITestResult result) {
		try {
			return test.getModel().getName();
		} catch (Exception e) {
			return result.getName();
		}
	}

	public static String headline_for_failure(Finding f, Throwable t) {
		StringBuilder sb = new StringBuilder("FAILED - ");
		StackTraceElement action = outer_page_frame(t);
		if (action != null) {
			sb.append("while doing '").append(humanize(action.getMethodName())).append("' on the ")
					.append(page_name(action.getClassName())).append(" page. ");
		}
		sb.append(shorten(f.what, 300));
		return sb.toString();
	}

	public static String failure_block(ExtentTest test, ITestResult result, WebDriver driver) {
		Throwable t = result.getThrowable();
		Finding f = explain(t);
		StackTraceElement test_at = test_frame(t);
		StackTraceElement action = outer_page_frame(t);
		StackTraceElement inner = inner_page_frame(t);
		List<String> done = steps(test);

		StringBuilder sb = new StringBuilder();
		sb.append("RESULT   : FAILED\n");
		sb.append("TEST     : ").append(title(test, result)).append('\n');
		sb.append("TYPE     : ").append(f.category).append("\n\n");

		sb.append("WHAT THE TEST HAD DONE BEFORE THE ERROR\n");
		add_steps(sb, done, true);

		sb.append("\nTHE STEP THAT WENT WRONG\n");
		if (action != null) {
			sb.append("  The test was doing: ").append(humanize(action.getMethodName())).append("  (").append(page_name(action.getClassName()))
					.append(" page)\n");
		} else if (test_at != null) {
			sb.append("  The test was checking the result of the previous steps (test line ").append(test_at.getLineNumber()).append(")\n");
		} else {
			sb.append("  The error happened outside the test steps (set-up or clean-up)\n");
		}

		sb.append("\nWHAT WENT WRONG\n  ").append(f.what).append("\n\n");

		sb.append("SCREEN WHEN IT FAILED\n").append(screen_text(driver)).append('\n');

		sb.append("WHERE IN THE CODE (for the automation / development team)\n");
		if (test_at != null) {
			sb.append("  Test step   : ").append(at(test_at)).append('\n');
		}
		if (action != null) {
			sb.append("  Page action : ").append(at(action)).append('\n');
		}
		if (inner != null && inner != action) {
			sb.append("  Failed inside: ").append(at(inner)).append('\n');
		}
		sb.append("  Exception   : ").append(t.getClass().getName()).append(": ").append(shorten(first_line(t), 300)).append('\n');
		return sb.toString();
	}

	public static String pass_block(ExtentTest test, ITestResult result) {
		List<String> done = steps(test);
		String conclusion = last_pass(test);
		StringBuilder sb = new StringBuilder();
		sb.append("RESULT   : PASSED\n");
		sb.append("TEST     : ").append(title(test, result)).append("\n\n");
		sb.append("WHAT WAS DONE AND CHECKED\n");
		add_steps(sb, done, false);
		sb.append("\nCONCLUSION\n  ");
		sb.append(conclusion.isEmpty() ? "Every check in this test passed; no error was found." : conclusion).append('\n');
		return sb.toString();
	}

	public static String pass_headline(ExtentTest test) {
		String conclusion = last_pass(test);
		return "PASSED - " + (conclusion.isEmpty() ? "every check in this test passed." : conclusion);
	}

	public static String skip_block(ExtentTest test, ITestResult result) {
		Throwable t = result.getThrowable();
		String msg = t == null || t.getMessage() == null ? "" : t.getMessage();
		String why;
		if (msg.contains("depends on not successfully finished methods")) {
			Matcher m = Pattern.compile("depends on not successfully finished methods\\s*(.*)").matcher(msg.replace('\r', ' '));
			String needed = m.find() ? shorten(m.group(1), 200) : "";
			why = "This test needs an earlier test to pass first and that test did not pass (" + needed
					+ "). Fix the earlier failure; this test was not run.";
		} else if (msg.isEmpty()) {
			why = "It was not run, usually because a set-up step before it (login, opening the module) failed. "
					+ "Look at the first failed test above it.";
		} else {
			why = shorten(msg, 300);
		}
		return "RESULT   : SKIPPED (not run)\nTEST     : " + title(test, result) + "\n\nWHY IT DID NOT RUN\n  " + why + "\n";
	}

	public static String skip_headline(ITestResult result) {
		return "SKIPPED - the test did not run (see the reason below).";
	}
}
