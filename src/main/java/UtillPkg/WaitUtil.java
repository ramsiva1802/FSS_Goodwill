package UtillPkg;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

/*
 * WaitUtil - replaces fixed Thread.sleep pauses everywhere in the project.
 *
 * settle(driver, maxMs) waits for AT MOST maxMs, but returns as soon as the page is quiet:
 *  - a short grace time first (so an action that was just clicked has started: autocomplete boxes wait
 *    about 300 ms before they call the server),
 *  - then it polls (every 60 ms) until the document is ready, no jQuery request is running and no loader /
 *    spinner / preloader is visible.
 * The old pause value is therefore the LONGEST wait, not the usual one.
 *
 * A browser alert is never touched: with the alert still open the method returns at once, so the test can
 * read it (any other driver command would dismiss it).
 * Everything here works for a minimized or hidden window: only page state is read, nothing needs focus.
 */
public final class WaitUtil {

	private WaitUtil() {
	}

	private static final String IDLE_JS = "return (function () {"
			+ " if (document.readyState !== 'complete') { return false; }"
			+ " if (window.jQuery && window.jQuery.active > 0) { return false; }"
			+ " var list = document.querySelectorAll('.preloader, #preloader, .loader, .loading, .spinner-border,"
			+ " .dataTables_processing, .swal2-loading, .blockUI, .overlay-loader');"
			+ " for (var i = 0; i < list.length; i++) {"
			+ "   var s = window.getComputedStyle(list[i]); var r = list[i].getBoundingClientRect();"
			+ "   if (s.display !== 'none' && s.visibility !== 'hidden' && parseFloat(s.opacity) > 0.05"
			+ "       && r.width > 0 && r.height > 0) { return false; } }"
			+ " return true; })()";

	// a fingerprint of what the page shows: it stops changing when the page has finished updating
	private static final String SIGNATURE_JS = "return document.body ? (document.body.innerText.length + ':'"
			+ " + document.getElementsByTagName('*').length) : '';";

	// sleeps without making the caller handle InterruptedException
	public static void nap(long millis) {
		if (millis <= 0) {
			return;
		}
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static boolean alert_open(WebDriver driver) {
		try {
			driver.switchTo().alert();
			return true;
		} catch (NoAlertPresentException e) {
			return false;
		} catch (WebDriverException e) {
			return false;
		}
	}

	// When the window is minimized or hidden Chrome pauses the page animations, so the page's "preloader" cover is
	// never faded out and swallows every click. It is only an overlay: when the page is hidden it is switched off.
	public static void unblock(WebDriver driver) {
		// any script command dismisses an open browser alert (unhandledPromptBehavior), so an alert is never touched here
		if (alert_open(driver)) {
			return;
		}
		try {
			// a hidden window also never finishes a fade (a pop-up stays at opacity 0, "not displayed"), so
			// animations and transitions are switched off as well
			((JavascriptExecutor) driver).executeScript("if (document.hidden || document.visibilityState === 'hidden') {"
					+ " document.querySelectorAll('.preloader, #preloader').forEach(function (e) { e.style.display = 'none'; });"
					+ " if (!document.getElementById('fss-no-animation')) { var s = document.createElement('style');"
					+ "   s.id = 'fss-no-animation'; s.textContent = '*, *::before, *::after { transition: none !important; animation: none !important; }';"
					+ "   document.head.appendChild(s); } }");
		} catch (WebDriverException e) {
			// an alert is open or the page is changing: nothing to do
		}
	}

	// waits up to maxMs for the page to be quiet (see the notes at the top)
	public static void settle(WebDriver driver, long maxMs) {
		if (maxMs <= 0) {
			return;
		}
		long grace = Math.max(120, Math.min(700, maxMs / 4));
		if (maxMs <= grace) {
			nap(maxMs);
			return;
		}
		nap(grace);
		if (alert_open(driver)) {
			return;   // the click opened a browser alert (for example a delete confirmation): the test must answer it
		}
		unblock(driver);
		long end = System.currentTimeMillis() + (maxMs - grace);
		String last = null;
		long since = 0;
		while (System.currentTimeMillis() < end) {
			if (alert_open(driver)) {
				return;
			}
			try {
				JavascriptExecutor js = (JavascriptExecutor) driver;
				if (!Boolean.TRUE.equals(js.executeScript(IDLE_JS))) {
					last = null; // still loading
				} else {
					// idle: also wait until what the page shows has stopped changing (a table filter or a
					// search box updates its rows a moment after the typing, without any server request)
					String signature = String.valueOf(js.executeScript(SIGNATURE_JS));
					long now = System.currentTimeMillis();
					if (signature.equals(last)) {
						if (now - since >= 300) {
							return;
						}
					} else {
						last = signature;
						since = now;
					}
				}
			} catch (WebDriverException e) {
				return; // the page is changing: the caller's own waits take over
			}
			nap(60);
		}
	}
}
