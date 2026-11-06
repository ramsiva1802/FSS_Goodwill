package UtillPkg;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * IndividualSchemeData - the test-data matrix of the Individual Scheme module: the 100 combinations that are still to be created.
 *
 *   Promotion Base Type (Amount / Quantity / Weight)  x  Scheme Applicable Type (9)  x  Applicable For (Category / SubCategory /
 *   Item / Menu Card), without the 8 combinations that exist already (Amount base with the Amount and Qty promotions at the four
 *   levels): Amount base 7 x 4 = 28, Quantity base 9 x 4 = 36, Weight base 9 x 4 = 36.
 *
 * The test class (YF_IndividualScheme_Test) and the matrix file are made from this one list, so they can not differ.
 * Write the matrix:  java -cp <classes> UtillPkg.IndividualSchemeData   ->  TestData\IndividualScheme_Matrix.csv
 *
 * Scheme code:  IND_<BASE>_<PROMOTION>_<SEQUENCE>_<LEVEL>, the sequence is 001 Category, 002 SubCategory, 003 Item, 004 Menu Card.
 * Scheme name:  Individual <Base Type> - <Promotion Type> <Applicable Level>
 * The master data is rotated over the 25 rows of a level, so every category, subcategory, item and menu card is used.
 */
public final class IndividualSchemeData {

	private IndividualSchemeData() {
	}

	// one row of the matrix
	public static final class Row {
		public int no;
		public String code, name;
		public String base_code, base_option, base_name;
		public String promo_code, promo_option, promo_sub, promo_name;
		public String level_code, level_option, level_name;
		public String master, master_alt, parent;
		public String min, max, value, extra;
		public boolean free_item;
	}

	// { code part, drop down text, name part, Min, Max }
	public static final String[][] BASES = {
			{ "AMT", "Amount", "Amount", "500", "5000" },
			{ "QTY", "Quantity", "Quantity", "5", "20" },
			{ "WGT", "Weight", "Weight", "5", "50" } };

	// { code part, drop down text, Amount / Quantity of Each and Multiple, name part, sample value, free item (Y / N) }
	public static final String[][] PROMOTIONS = {
			{ "AMT", "Amount", "", "Amount", "100", "N" },
			{ "QTY", "Quantity", "", "Qty", "2", "Y" },
			{ "PER", "Percentage", "", "Percentage", "10", "N" },
			{ "EAMT", "Each", "Amount", "Each Amount", "20", "N" },
			{ "EQTY", "Each", "Quantity", "Each Quantity", "1", "Y" },
			{ "MAMT", "Multiple", "Amount", "Multiple Amount", "50", "N" },
			{ "MQTY", "Multiple", "Quantity", "Multiple Quantity", "2", "Y" },
			{ "RATE", "Special Rate", "", "Special Rate", "75", "N" },
			{ "WGT", "Weight", "", "Weight", "1", "Y" } };

	// { code part, drop down text of Applicable For, name part }
	public static final String[][] LEVELS = {
			{ "CAT", "Category", "Category" },
			{ "SUB", "SubCategory", "SubCategory" },
			{ "ITEM", "Item", "Item" },
			{ "MENU", "Menu Card", "Menu" } };

	public static final String FREE_ITEM = "leo-item1";
	public static final String PER_UNIT = "1";

	// master data (the records of the application): categories; { subcategory, category }; { item, subcategory, category };
	// { menu card name, menu card code, items }
	static final String[] CATEGORIES = { "Rono-Cate", "Leo-Cate", "Ney-Cate" };
	static final String[][] SUBCATEGORIES = { { "Rono-Sub", "Rono-Cate" }, { "Rono-Sub2", "Rono-Cate" }, { "Leo-Sub", "Leo-Cate" },
			{ "Leo-Sub2", "Leo-Cate" }, { "Ney-Sub", "Ney-Cate" }, { "Ney-Sub2", "Ney-Cate" } };
	// { item name, subcategory, category, stock code } (the stock codes are those of the Football menu card list)
	static final String[][] ITEMS = { { "CRono-item1", "Rono-Sub", "Rono-Cate", "cr1" }, { "CRono-item2", "Rono-Sub", "Rono-Cate", "cr2" },
			{ "CRono-item3", "Rono-Sub2", "Rono-Cate", "cr3" }, { "CRono-item4", "Rono-Sub2", "Rono-Cate", "cr4" },
			{ "leo-item1", "Leo-Sub", "Leo-Cate", "leo1" }, { "leo-item2", "Leo-Sub", "Leo-Cate", "leo2" },
			{ "leo-item3", "Leo-Sub2", "Leo-Cate", "leo3" }, { "leo-item4", "Leo-Sub2", "Leo-Cate", "leo4" },
			{ "Ney-Item1", "Ney-Sub", "Ney-Cate", "ney1" }, { "Ney-Item2", "Ney-Sub", "Ney-Cate", "ney2" },
			{ "Ney-Item3", "Ney-Sub2", "Ney-Cate", "ney3" }, { "Ney-Item4", "Ney-Sub2", "Ney-Cate", "ney4" } };	static final String[][] MENUS = { { "Football", "GOAT", "12 items" }, { "MN01", "Menu_1", "7 items" } };

	// the combinations that exist already (they are NOT created again)
	static boolean exists_already(String base, String promo) {
		return base.equals("AMT") && (promo.equals("AMT") || promo.equals("QTY"));
	}

	public static List<Row> rows() {
		List<Row> rows = new ArrayList<Row>();
		int[] used = new int[LEVELS.length];
		int no = 0;
		for (String[] b : BASES) {
			for (String[] p : PROMOTIONS) {
				if (exists_already(b[0], p[0])) {
					continue;
				}
				for (int l = 0; l < LEVELS.length; l++) {
					String[] lv = LEVELS[l];
					Row r = new Row();
					r.no = ++no;
					r.base_code = b[0];
					r.base_option = b[1];
					r.base_name = b[2];
					r.min = b[3];
					r.max = b[4];
					r.promo_code = p[0];
					r.promo_option = p[1];
					r.promo_sub = p[2];
					r.promo_name = p[3];
					r.value = p[4];
					r.free_item = p[5].equals("Y");
					r.level_code = lv[0];
					r.level_option = lv[1];
					r.level_name = lv[2];
					r.code = "IND_" + b[0] + "_" + p[0] + "_" + String.format("%03d", l + 1) + "_" + lv[0];
					r.name = "Individual " + b[2] + " - " + p[3] + " " + lv[2];
					int k = used[l]++;
					switch (lv[0]) {
					case "CAT":
						r.master = CATEGORIES[k % CATEGORIES.length];
						r.parent = "";
						break;
					case "SUB":
						r.master = SUBCATEGORIES[k % SUBCATEGORIES.length][0];
						r.parent = SUBCATEGORIES[k % SUBCATEGORIES.length][1];
						break;
					case "ITEM":
						r.master = ITEMS[k % ITEMS.length][0];
						r.master_alt = ITEMS[k % ITEMS.length][3];
						r.parent = ITEMS[k % ITEMS.length][1] + " / " + ITEMS[k % ITEMS.length][2];
						break;
					default:
						r.master = MENUS[k % MENUS.length][0];
						r.master_alt = MENUS[k % MENUS.length][1];
						r.parent = "code " + MENUS[k % MENUS.length][1] + ", " + MENUS[k % MENUS.length][2];
						break;
					}
					StringBuilder extra = new StringBuilder();
					if (p[0].equals("RATE")) {
						extra.append("Per Unit ").append(PER_UNIT);
					}
					if (r.free_item) {
						extra.append(extra.length() == 0 ? "" : "; ").append("Free Item ").append(FREE_ITEM).append(" Qty ").append(r.value);
					}
					r.extra = extra.toString();
					rows.add(r);
				}
			}
		}
		return rows;
	}

	private static String cell(String s) {
		return "\"" + (s == null ? "" : s.replace("\"", "'")) + "\"";
	}

	// TestData\IndividualScheme_Matrix.csv: the complete matrix (the values are the QA sample values of the task)
	public static void write_matrix(File file) throws IOException {
		file.getParentFile().mkdirs();
		try (FileWriter w = new FileWriter(file)) {
			w.write("No,Scheme Code,Scheme Name,Promotion Base Type,Scheme Applicable Type,Applicable For,Selected master data,"
					+ "Parent / details,Min,Max,Value,Extra fields,From Date,To Date,Is Active,Method\n");
			for (Row r : rows()) {
				String type = r.promo_option + (r.promo_sub.isEmpty() ? "" : " - " + r.promo_sub);
				String[] cells = { String.valueOf(r.no), r.code, r.name, r.base_option, type, r.level_option,
						r.master + (r.master_alt == null ? "" : " (" + r.master_alt + ")"), r.parent, r.min, r.max, r.value, r.extra,
						"04-11-2026", "30-11-2026", "Checked", "Auto Apply" };
				StringBuilder line = new StringBuilder();
				for (int i = 0; i < cells.length; i++) {
					line.append(i == 0 ? "" : ",").append(cell(cells[i]));
				}
				w.write(line.append('\n').toString());
			}
		}
	}

	public static void main(String[] args) throws IOException {
		File f = new File("TestData/IndividualScheme_Matrix.csv");
		write_matrix(f);
		System.out.println(rows().size() + " rows written to " + f.getAbsolutePath());
	}
}
