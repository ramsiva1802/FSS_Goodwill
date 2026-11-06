# FSS_GOODWILL_optimized

Same project as FSS-Goodwill (same package and class names, same tests). Only the way the code waits and opens
screens was changed.

## What is different
1. Waits - `UtillPkg/WaitUtil.java`
   - Every `Thread.sleep(n)` / `pause(n)` is now `WaitUtil.settle(driver, n)`: it waits AT MOST n ms and returns as soon as
     the page is ready, no request is running, no loader is visible and the page has stopped changing.
   - Explicit waits poll every 100 ms (was 500 ms). No implicit wait is used.
2. Opening a module - `PagePkg/B_Dashboard_Cls.java` + `BasePkg/BaseCls.java`
   - The first test opens a module by Home > menu > sub menu and the address is remembered. Later tests open the address
     directly (no Home click, no menu walk). The Login and Dashboard tests still click everything (FAST_NAV = false).
   - `@BeforeMethod` of every module test first asks `screen_is_clean()`: if the screen still looks exactly as it did when
     the module was opened (address, pop-ups, every box value, first row, row count, "Showing x of y", sort), the module is NOT
     opened again.
3. Browser - `BaseCls.newBrowser()`
   - Flags that keep a minimized / hidden Chrome running at full speed; `-Dheadless=true` runs without any window.
   - When the window is hidden `WaitUtil.unblock()` switches off the page preloader and CSS transitions (otherwise a hidden
     window never finishes its fade-outs, so menus are covered and pop-ups stay "not displayed").
   - `restartBrowser()` starts a new Chrome when the old one is gone; the screenshot after a failed test can no longer skip the rest.
4. Off Take Report: the photo viewer is a side drawer (`#offTakeGalleryDrawer`), not a pop-up; its tests now find it.

## Run headless (no window, works while you do other work)
Run configuration > Arguments > VM arguments:  `-Dheadless=true`
