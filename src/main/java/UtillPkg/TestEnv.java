package UtillPkg;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/*
 * TestEnv - environment values (server URL, test logins) are NOT written in the code.
 *
 * Order of lookup:  1. a JVM argument           (-Dfss.url=http://host:port/  -Dweb.user=...  -Dweb.password=...)
 *                   2. env.properties            (project root, or src/test/resources) - this file is NOT committed (see .gitignore)
 *                   3. the default given in the code (a placeholder)
 *
 * Copy env.properties.example to env.properties and fill in your own values.
 */
public final class TestEnv {

	private static final Properties FILE = new Properties();

	static {
		for (String path : new String[] { "env.properties", "src/test/resources/env.properties" }) {
			File f = new File(path);
			if (f.isFile()) {
				try (FileInputStream in = new FileInputStream(f)) {
					FILE.load(in);
				} catch (IOException e) {
					System.err.println("TestEnv: could not read " + path + ": " + e.getMessage());
				}
				break;
			}
		}
	}

	private TestEnv() {
	}

	public static String get(String key, String default_value) {
		String v = System.getProperty(key);
		if (v == null || v.isEmpty()) {
			v = FILE.getProperty(key);
		}
		return (v == null || v.isEmpty()) ? default_value : v;
	}
}
