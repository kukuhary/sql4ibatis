package sql4ibatis.preferences;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import sql4ibatis.Activator;

/**
 * 데이터베이스 접속 환경 설정 페이지입니다. 모든 사용자 메시지 및 레이아웃을 영문(English)으로 전면 교체하여 한글 인코딩 깨짐을 근본적으로 방지했습니다.
 */
public class DatabasePreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	private FileFieldEditor jarEditor;
	private StringFieldEditor driverClassEditor;
	private StringFieldEditor urlEditor;
	private StringFieldEditor userEditor;
	private StringFieldEditor passwordEditor;
	private IntegerFieldEditor maxRowsEditor;

	public DatabasePreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Configure database connection properties and options for iBATIS/MyBatis SQL execution.");
	}

	@Override
	public void createFieldEditors() {
		jarEditor = new FileFieldEditor(PreferenceConstants.DB_DRIVER_JAR, 
				"JDBC Driver Jar (*.jar):", getFieldEditorParent());
		jarEditor.setFileExtensions(new String[] { "*.jar" });
		addField(jarEditor);
		
		driverClassEditor = new StringFieldEditor(PreferenceConstants.DB_DRIVER_CLASS, 
				"JDBC Driver Class:", getFieldEditorParent());
		addField(driverClassEditor);
		
		urlEditor = new StringFieldEditor(PreferenceConstants.DB_URL, 
				"JDBC Connection URL:", getFieldEditorParent());
		addField(urlEditor);
		
		userEditor = new StringFieldEditor(PreferenceConstants.DB_USER, 
				"Database User:", getFieldEditorParent());
		addField(userEditor);
		
		passwordEditor = new StringFieldEditor(PreferenceConstants.DB_PASSWORD, 
				"Database Password:", getFieldEditorParent()) {
			@Override
			protected void doFillIntoGrid(Composite parent, int numColumns) {
				super.doFillIntoGrid(parent, numColumns);
				getTextControl().setEchoChar('*');
			}
		};
		addField(passwordEditor);

		maxRowsEditor = new IntegerFieldEditor(PreferenceConstants.DB_MAX_ROWS, 
				"Max Rows Limit:", getFieldEditorParent());
		maxRowsEditor.setValidRange(1, 50000);
		addField(maxRowsEditor);

		Composite buttonParent = getFieldEditorParent();
		Button testButton = new Button(buttonParent, SWT.PUSH);
		testButton.setText("Test Connection");
		
		GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gd.horizontalSpan = 3;
		testButton.setLayoutData(gd);
		
		testButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				performConnectionTest();
			}
		});
	}

	/**
	 * 입력 필드의 정보를 바탕으로 드라이버 JAR를 동적으로 로드하고 연결을 테스트합니다.
	 */
	private void performConnectionTest() {
		String jarPath = jarEditor.getStringValue().trim();
		String driverClassStr = driverClassEditor.getStringValue().trim();
		String url = urlEditor.getStringValue().trim();
		String user = userEditor.getStringValue().trim();
		String password = passwordEditor.getStringValue();

		if (jarPath.isEmpty() || driverClassStr.isEmpty() || url.isEmpty()) {
			MessageDialog.openWarning(getShell(), 
					"Input Validation", 
					"Jar Path, Driver Class, and Connection URL are required fields.");
			return;
		}

		File jarFile = new File(jarPath);
		if (!jarFile.exists()) {
			MessageDialog.openError(getShell(), 
					"File Error", 
					"The specified JDBC Driver Jar file does not exist.\nPlease check the path.");
			return;
		}

		Connection conn = null;
		URLClassLoader urlLoader = null;
		try {
			URL[] urls = new URL[] { jarFile.toURI().toURL() };
			urlLoader = new URLClassLoader(urls, this.getClass().getClassLoader());
			
			Class<?> clazz = Class.forName(driverClassStr, true, urlLoader);
			Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
			
			Properties props = new Properties();
			props.setProperty("user", user);
			props.setProperty("password", password);
			
			conn = driver.connect(url, props);
			
			if (conn != null) {
				MessageDialog.openInformation(getShell(), 
						"Connection Success", 
						"Database connection established successfully!");
			} else {
				MessageDialog.openError(getShell(), 
						"Connection Failed", 
						"The driver failed to return a connection. Please check the URL.");
			}
		} catch (Exception ex) {
			MessageDialog.openError(getShell(), 
					"Connection Failed", 
					"An error occurred while attempting to connect:\n" + ex.getMessage());
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ignored) {}
			}
			if (urlLoader != null) {
				try {
					urlLoader.close();
				} catch (Exception ignored) {}
			}
		}
	}

	@Override
	public void init(IWorkbench workbench) {
	}
}
