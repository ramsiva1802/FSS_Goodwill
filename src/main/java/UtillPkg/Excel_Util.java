package UtillPkg;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/*
 * NOTES - Excel_Util
 * ------------------
 * Reads test data from an .xlsx file using Apache POI (poi-ooxml in pom.xml).
 * Used with a TestNG @DataProvider so one test method runs once per Excel row.
 * Every method has two forms:
 *   - with a file path    -> works with any workbook (LoginData.xlsx, StockItemData.xlsx ...)
 *   - without a file path -> uses the default LoginData.xlsx, so old tests still work
 * The workbook must be CLOSED in Excel while the tests read or write it.
 */
public class Excel_Util {

	// default workbook (login tests); other workbooks are passed in by path
	public static final String PATH = ".//src//test//resources//LoginData.xlsx";

	// DataFormatter turns ANY cell type (text, number, date) into a String,
	// so a numeric value like 9876543210 does not come back as 9.87654321E9
	static DataFormatter formatter = new DataFormatter();

	// ---------- READ ----------

	// Reads the whole sheet into a 2D array: [row][column].
	// Row 0 is the header and is skipped. Blank rows are skipped too.
	public static Object[][] getData(String path, String sheetName) throws IOException {

		// try-with-resources closes the file automatically
		try (FileInputStream fis = new FileInputStream(path); Workbook wb = new XSSFWorkbook(fis)) {

			Sheet sheet = wb.getSheet(sheetName);
			if (sheet == null) {
				throw new IllegalArgumentException("Sheet not found: " + sheetName + " in " + path);
			}

			int rows = sheet.getLastRowNum();                 // last data row index
			int cols = sheet.getRow(0).getLastCellNum();      // number of header columns

			List<Object[]> list = new ArrayList<Object[]>();

			for (int i = 1; i <= rows; i++) {
				Row row = sheet.getRow(i);
				if (row == null) {
					continue;   // completely blank row
				}
				Object[] values = new Object[cols];
				boolean any_value = false;
				for (int j = 0; j < cols; j++) {
					// a missing cell becomes an empty string (useful for empty-input tests)
					String text = row.getCell(j) == null ? "" : formatter.formatCellValue(row.getCell(j));
					values[j] = text;
					if (!text.trim().isEmpty()) {
						any_value = true;
					}
				}
				if (any_value) {
					list.add(values);   // a row with every cell empty is skipped
				}
			}
			return list.toArray(new Object[0][]);
		}
	}

	public static Object[][] getData(String sheetName) throws IOException {
		return getData(PATH, sheetName);
	}

	// Reads a single cell: row and column are 0-based (row 0 = header)
	public static String getCellData(String path, String sheetName, int rowNum, int colNum) throws IOException {
		try (FileInputStream fis = new FileInputStream(path); Workbook wb = new XSSFWorkbook(fis)) {
			Row row = wb.getSheet(sheetName).getRow(rowNum);
			if (row == null || row.getCell(colNum) == null) {
				return "";
			}
			return formatter.formatCellValue(row.getCell(colNum));
		}
	}

	public static String getCellData(String sheetName, int rowNum, int colNum) throws IOException {
		return getCellData(PATH, sheetName, rowNum, colNum);
	}

	// Number of data rows (header not counted)
	public static int getRowCount(String path, String sheetName) throws IOException {
		try (FileInputStream fis = new FileInputStream(path); Workbook wb = new XSSFWorkbook(fis)) {
			return wb.getSheet(sheetName).getLastRowNum();
		}
	}

	public static int getRowCount(String sheetName) throws IOException {
		return getRowCount(PATH, sheetName);
	}

	// ---------- WRITE ----------

	// Writes a value into one cell, for example "Created" / "Rejected" in a Result column.
	// Row and column are 0-based. The workbook must be closed in Excel, or the save fails.
	public static void setCellData(String path, String sheetName, int rowNum, int colNum, String value)
			throws IOException {
		Workbook wb;
		try (FileInputStream fis = new FileInputStream(path)) {
			wb = new XSSFWorkbook(fis);
		}
		try {
			Sheet sheet = wb.getSheet(sheetName);
			Row row = sheet.getRow(rowNum) == null ? sheet.createRow(rowNum) : sheet.getRow(rowNum);
			Cell cell = row.getCell(colNum) == null ? row.createCell(colNum) : row.getCell(colNum);
			cell.setCellValue(value);

			try (FileOutputStream fos = new FileOutputStream(path)) {
				wb.write(fos);
			}
		} finally {
			wb.close();
		}
	}
}