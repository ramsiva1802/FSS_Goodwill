package UtillPkg;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.testng.SkipException;

/*
 * NOTES - DB_Util
 * ---------------
 * Reads the QA database directly to prove that data created in the UI is really stored.
 * Connection details come from src/test/resources/db.properties, so no password is in the code.
 * If db.properties is missing or incomplete, DB checks are SKIPPED, not failed.
 * Only SELECT queries are used: this class never changes the database.
 */
public class DB_Util {

	static final String CONFIG = ".//src//test//resources//db.properties";

	static Properties props = new Properties();

	// load the settings file once, when the class is first used
	static {
		try (FileInputStream fis = new FileInputStream(CONFIG)) {
			props.load(fis);
		} catch (IOException e) {
			System.out.println("db.properties not found: DB checks will be skipped");
		}
	}

	static String get(String key) {
		return props.getProperty(key, "").trim();
	}

	// true only when host, db, user, password and table are all filled in
	public static boolean is_configured() {
		return !get("db.host").isEmpty() && !get("db.name").isEmpty() && !get("db.user").isEmpty()
				&& !get("db.password").isEmpty() && !get("db.table").isEmpty();
	}

	private static Connection connect() throws SQLException {
		if (!is_configured()) {
			throw new SkipException("Database details are not filled in db.properties, skipping the DB check");
		}
		String jdbc = "jdbc:sqlserver://" + get("db.host") + ":" + get("db.port") + ";databaseName=" + get("db.name")
				+ ";encrypt=false;trustServerCertificate=true";
		return DriverManager.getConnection(jdbc, get("db.user"), get("db.password"));
	}

	// number of rows in the company table with this code (? placeholder avoids SQL injection)
	public static int count_company(String code) throws SQLException {
		String sql = "SELECT COUNT(*) FROM " + get("db.table") + " WHERE " + get("db.code_column") + " = ?";
		try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, code);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getInt(1);
			}
		}
	}

	// reads one column of the row with this code (null if there is no such row)
	public static String get_value(String column, String code) throws SQLException {
		String sql = "SELECT " + column + " FROM " + get("db.table") + " WHERE " + get("db.code_column") + " = ?";
		try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, code);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getString(1) : null;
			}
		}
	}

	// the stored company name of this code (column name comes from db.properties)
	public static String get_company_name(String code) throws SQLException {
		return get_value(get("db.name_column"), code);
	}

	// lists the column names of the company table, used to find the real column names
	public static List<String> get_columns() throws SQLException {
		String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
		List<String> columns = new ArrayList<String>();
		try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, get("db.table"));
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					columns.add(rs.getString(1));
				}
			}
		}
		return columns;
	}
}