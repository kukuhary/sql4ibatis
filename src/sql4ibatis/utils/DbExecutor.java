package sql4ibatis.utils;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Helper class responsible for loading dynamic cached JDBC drivers and executing database queries.
 */
public class DbExecutor {

	// Static cache fields to prevent JDBC driver loading overhead
	private static String cachedJarPath = null;
	private static String cachedDriverClass = null;
	private static Driver cachedDriver = null;
	private static URLClassLoader cachedClassLoader = null;

	/**
	 * Loads the specified JDBC driver, executes the query, and parses the result.
	 * 
	 * @param jarPath     JDBC Driver Jar path
	 * @param driverClass JDBC Driver class name
	 * @param url         DB Connection URL
	 * @param user        DB User name
	 * @param password    DB Password
	 * @param maxRows     Max row limit
	 * @param sql         Executed complete SQL string
	 * @param monitor     Progress monitor
	 * @param outHeaders  Output list to receive headers
	 * @param outRows     Output list to receive data rows
	 * @throws Exception  Any exception occurred during execution
	 */
	public static void executeQuery(
			String jarPath, String driverClass, String url, String user, String password, int maxRows,
			String sql, IProgressMonitor monitor, List<String> outHeaders, List<List<String>> outRows) throws Exception {

		File jarFile = new File(jarPath);
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;

		try {
			// 1. Utilize cached JDBC driver and classloader if available
			Driver driver = null;
			
			monitor.subTask("Checking JDBC Driver Cache...");
			if (jarPath.equals(cachedJarPath) && driverClass.equals(cachedDriverClass) && cachedDriver != null) {
				driver = cachedDriver;
			} else {
				monitor.subTask("Loading JDBC Driver Jar from Disk...");
				// Close previous classloader
				if (cachedClassLoader != null) {
					try { cachedClassLoader.close(); } catch (Exception ignored) {}
				}
				
				URL[] urls = new URL[] { jarFile.toURI().toURL() };
				cachedClassLoader = new URLClassLoader(urls, DbExecutor.class.getClassLoader());
				Class<?> clazz = Class.forName(driverClass, true, cachedClassLoader);
				cachedDriver = (Driver) clazz.getDeclaredConstructor().newInstance();
				
				cachedJarPath = jarPath;
				cachedDriverClass = driverClass;
				driver = cachedDriver;
			}

			Properties props = new Properties();
			props.setProperty("user", user);
			props.setProperty("password", password);

			if (monitor.isCanceled()) {
				throw new InterruptedException("Query execution canceled by user.");
			}

			// 2. Establish database connection
			monitor.subTask("Connecting to database server...");
			conn = driver.connect(url, props);
			stmt = conn.createStatement();

			// Apply max rows limit
			stmt.setMaxRows(maxRows);

			if (monitor.isCanceled()) {
				throw new InterruptedException("Query execution canceled by user.");
			}

			// 3. Execute SQL statement
			monitor.subTask("Executing SQL query statement...");
			String cleanSql = sql.trim().toLowerCase();
			if (cleanSql.startsWith("select") || cleanSql.startsWith("with")) {
				rs = stmt.executeQuery(sql);
				ResultSetMetaData metaData = rs.getMetaData();
				int columnCount = metaData.getColumnCount();

				// Extract header column names
				for (int i = 1; i <= columnCount; i++) {
					outHeaders.add(metaData.getColumnName(i));
				}

				// Extract data rows
				while (rs.next()) {
					if (monitor.isCanceled()) {
						throw new InterruptedException("Fetching data canceled by user.");
					}
					List<String> row = new ArrayList<>();
					for (int i = 1; i <= columnCount; i++) {
						Object val = rs.getObject(i);
						row.add(val == null ? "NULL" : val.toString());
					}
					outRows.add(row);
				}
			} else {
				// Execute DML / DDL statement
				int affectedRows = stmt.executeUpdate(sql);
				outHeaders.add("RESULT");
				
				List<String> row = new ArrayList<>();
				row.add("Successfully executed. Affected rows: " + affectedRows);
				outRows.add(row);
			}

		} finally {
			// Safely close resources
			if (rs != null) {
				try { rs.close(); } catch (SQLException ignored) {}
			}
			if (stmt != null) {
				try { stmt.close(); } catch (SQLException ignored) {}
			}
			if (conn != null) {
				try { conn.close(); } catch (SQLException ignored) {}
			}
		}
	}
}
