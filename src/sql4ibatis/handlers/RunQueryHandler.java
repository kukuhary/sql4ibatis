package sql4ibatis.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.operation.IRunnableWithProgress;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import sql4ibatis.Activator;
import sql4ibatis.dialogs.ParameterInputDialog;
import sql4ibatis.preferences.PreferenceConstants;
import sql4ibatis.utils.DbExecutor;
import sql4ibatis.utils.SqlParser;
import sql4ibatis.views.QueryResultView;

/**
 * 에디터에서 Alt+Q 입력 시 XML 쿼리문을 파싱하고 변수 입력을 다이얼로그로 수집한 후,
 * 백그라운드 스레드에서 설정된 데이터베이스를 대상으로 SQL을 수행하여 결과를 결과 뷰에 표출하는 흐름 제어 핸들러입니다.
 * 모든 에러/안내 다이얼로그 텍스트를 영문(English)으로 전면 교체하여 한글 깨짐을 근본 예방했습니다.
 */
public class RunQueryHandler extends AbstractHandler {

	private static final String HISTORY_FILE_NAME = "parameter_history.properties";

	private static class CachedXmlInfo {
		final long modificationStamp;
		final String namespace;
		final String content;

		CachedXmlInfo(long modificationStamp, String namespace, String content) {
			this.modificationStamp = modificationStamp;
			this.namespace = namespace;
			this.content = content;
		}
	}

	private static final Map<org.eclipse.core.runtime.IPath, CachedXmlInfo> xmlCache = new java.util.concurrent.ConcurrentHashMap<>();

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		ITextEditor textEditor = getTextEditor(editor);
		
		if (textEditor == null) {
			MessageDialog.openWarning(HandlerUtil.getActiveShell(event), 
					"Execution Prevented", 
					"This feature can only be executed within a text editor.");
			return null;
		}

		ISelection selection = textEditor.getSelectionProvider().getSelection();
		
		if (!(selection instanceof ITextSelection)) {
			return null;
		}

		ITextSelection textSelection = (ITextSelection) selection;
		String selectedText = textSelection.getText();
		int offset = textSelection.getOffset();

		String rawSql = "";
		String queryId = "";
		String xmlText = "";

		// [1단계: SQL 및 쿼리 ID 추출]
		if (selectedText != null && !selectedText.trim().isEmpty()) {
			rawSql = selectedText.trim();
			queryId = "(Selected SQL)";
			IDocument doc = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
			if (doc != null) {
				xmlText = doc.get();
			}
		} else {
			IDocument doc = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
			if (doc != null) {
				xmlText = doc.get();
				rawSql = SqlParser.extractQueryAtCursor(xmlText, offset);
				queryId = SqlParser.extractQueryId(xmlText, offset);
			}
		}

		if (rawSql == null || rawSql.trim().isEmpty()) {
			MessageDialog.openWarning(HandlerUtil.getActiveShell(event), 
					"Execution Prevented", 
					"Cannot locate the SQL query tag area to execute.\n\nPlease place your cursor inside a query tag (<select>, <update>, <insert>, <delete>) or select the SQL query text.");
			return null;
		}

		// Collect all project XML files content to resolve cross-file includes (using progress dialog ONLY if query contains <include)
		Map<String, String> xmlContentsMap = new HashMap<>();

		if (rawSql.contains("<include")) {
			try {
				new org.eclipse.jface.dialogs.ProgressMonitorDialog(HandlerUtil.getActiveShell(event)).run(true, true, new IRunnableWithProgress() {
					@Override
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						monitor.beginTask("Scanning MyBatis XML/SQLX files in workspace...", IProgressMonitor.UNKNOWN);
						Map<String, String> result = collectWorkspaceXmlContentsWithMonitor(monitor);
						xmlContentsMap.putAll(result);
						monitor.done();
					}
				});
			} catch (InterruptedException e) {
				return null; // Canceled by user
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// [1.5단계: include 태그를 실제 <sql id="..."> 문자열로 인라인 치환 (프로젝트 교차 파일 스캔 지원)]
		String includeResolvedSql = SqlParser.resolveIncludes(rawSql, xmlText, xmlContentsMap);

		// [1.6단계: foreach 태그 언랩(Unwrap) 및 기저 변수 매핑 처리]
		String foreachUnwrappedSql = SqlParser.unwrapForeach(includeResolvedSql);

		// [2단계: 파라미터 추출 및 사용자 입력 처리]
		List<String> params = SqlParser.extractParameters(foreachUnwrappedSql);
		Map<String, String> paramValues = null;

		if (!params.isEmpty()) {
			Map<String, String> parameterHistory = loadParameterHistory();

			ParameterInputDialog dialog = new ParameterInputDialog(HandlerUtil.getActiveShell(event), params, parameterHistory);
			if (dialog.open() == Dialog.OK) {
				paramValues = dialog.getValues();
				saveParameterHistory(paramValues);
			} else {
				return null; // 취소 시 실행 중단
			}
		}

		// [3단계: MyBatis <if test="..."> 조건절을 평가하여 동적 SQL 파싱 처리]
		String dynamicProcessedSql = SqlParser.parseDynamicSql(foreachUnwrappedSql, paramValues);

		// [3.5단계: MyBatis <choose>/<when>/<otherwise> 분기 태그 평가 및 동적 SQL 파싱 처리]
		String chooseProcessedSql = SqlParser.parseChooseSql(dynamicProcessedSql, paramValues);

		// [4단계: 일반 파라미터 치환 처리]
		String executableSql = SqlParser.resolveParameters(chooseProcessedSql, paramValues);

		// [5단계: 데이터베이스 연결 및 쿼리 실행]
		executeDatabaseQuery(event, textEditor, queryId, executableSql);

		return null;
	}

	private Map<String, String> loadParameterHistory() {
		Map<String, String> history = new HashMap<>();
		try {
			File historyFile = Activator.getDefault().getStateLocation().append(HISTORY_FILE_NAME).toFile();
			if (historyFile.exists()) {
				java.util.Properties props = new java.util.Properties();
				try (FileInputStream in = new FileInputStream(historyFile)) {
					props.load(in);
					for (String key : props.stringPropertyNames()) {
						history.put(key, props.getProperty(key));
					}
				}
			}
		} catch (Exception e) {
			// 로드 실패 시 무시
		}
		return history;
	}

	private void saveParameterHistory(Map<String, String> newValues) {
		if (newValues == null || newValues.isEmpty()) {
			return;
		}
		try {
			File historyFile = Activator.getDefault().getStateLocation().append(HISTORY_FILE_NAME).toFile();
			java.util.Properties props = new java.util.Properties();

			if (historyFile.exists()) {
				try (FileInputStream in = new java.io.FileInputStream(historyFile)) {
					props.load(in);
				}
			}

			for (Map.Entry<String, String> entry : newValues.entrySet()) {
				String baseKey = entry.getKey().replace("(if) ", "").trim();
				props.setProperty(baseKey, entry.getValue());
			}

			try (FileOutputStream out = new FileOutputStream(historyFile)) {
				props.store(out, "sql4ibatis Parameter History");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void executeDatabaseQuery(ExecutionEvent event, ITextEditor textEditor, String queryId, String sql) {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		String jarPath = store.getString(PreferenceConstants.DB_DRIVER_JAR).trim();
		String driverClass = store.getString(PreferenceConstants.DB_DRIVER_CLASS).trim();
		String url = store.getString(PreferenceConstants.DB_URL).trim();
		String user = store.getString(PreferenceConstants.DB_USER).trim();
		String password = store.getString(PreferenceConstants.DB_PASSWORD);

		final int maxRows = store.contains(PreferenceConstants.DB_MAX_ROWS) ? store.getInt(PreferenceConstants.DB_MAX_ROWS) : 1000;

		if (jarPath.isEmpty() || driverClass.isEmpty() || url.isEmpty()) {
			MessageDialog.openWarning(HandlerUtil.getActiveShell(event), 
					"Missing Configuration", 
					"Database connection configuration is empty.\nPlease configure it in Window > Preferences > iBATIS SQL Executor.");
			return;
		}

		final List<String> headers = new ArrayList<>();
		final List<List<String>> rows = new ArrayList<>();
		final Throwable[] exceptionHolder = new Throwable[1];

		org.eclipse.jface.dialogs.ProgressMonitorDialog progressDialog = new org.eclipse.jface.dialogs.ProgressMonitorDialog(HandlerUtil.getActiveShell(event));
		try {
			progressDialog.run(true, true, monitor -> {
				try {
					DbExecutor.executeQuery(jarPath, driverClass, url, user, password, maxRows, sql, monitor, headers, rows);
				} catch (Throwable t) {
					exceptionHolder[0] = t;
				}
			});

			if (exceptionHolder[0] != null && !(exceptionHolder[0] instanceof InterruptedException)) {
				try {
					IWorkbenchPage page = textEditor.getSite().getPage();
					QueryResultView view = (QueryResultView) page.showView(QueryResultView.ID);
					
					List<String> errorHeaders = new ArrayList<>();
					errorHeaders.add("SQL_EXECUTION_ERROR");
					
					List<List<String>> errorRows = new ArrayList<>();
					List<String> errorRow = new ArrayList<>();
					errorRow.add("Error Message: " + exceptionHolder[0].getMessage());
					errorRows.add(errorRow);
					
					view.updateData(queryId, errorHeaders, errorRows, sql);
				} catch (Exception e) {
					// 뷰 로드 에러는 무시
				}
			}

			if (exceptionHolder[0] != null) {
				if (exceptionHolder[0] instanceof InterruptedException) {
					MessageDialog.openInformation(HandlerUtil.getActiveShell(event), 
							"Canceled", 
							"SQL execution has been canceled by the user.");
				} else {
					MessageDialog.openError(HandlerUtil.getActiveShell(event), 
							"SQL Execution Error", 
							"An error occurred during query execution:\n" + exceptionHolder[0].getMessage());
				}
				return;
			}

			IWorkbenchPage page = textEditor.getSite().getPage();
			QueryResultView view = (QueryResultView) page.showView(QueryResultView.ID);
			view.updateData(queryId, headers, rows, sql);

		} catch (PartInitException e) {
			MessageDialog.openError(HandlerUtil.getActiveShell(event), 
					"View Error", 
					"Failed to activate the query result view:\n" + e.getMessage());
		} catch (InvocationTargetException e) {
			MessageDialog.openError(HandlerUtil.getActiveShell(event), 
					"Execution Error", 
					"An error occurred during background execution:\n" + e.getTargetException().getMessage());
		} catch (InterruptedException e) {
			MessageDialog.openInformation(HandlerUtil.getActiveShell(event), 
					"Canceled", 
					"The task has been interrupted.");
		}
	}

	/**
	 * 활성화된 에디터가 MultiPageEditor(Design/Source 탭 구분)이거나 기타 커스텀 에디터 형태인 경우,
	 * 내부의 실제 ITextEditor를 리플렉션 및 어댑터 패턴으로 찾아 반환합니다.
	 */
	private ITextEditor getTextEditor(IEditorPart editor) {
		if (editor instanceof ITextEditor) {
			return (ITextEditor) editor;
		}
		if (editor != null) {
			ITextEditor adapter = editor.getAdapter(ITextEditor.class);
			if (adapter != null) {
				return adapter;
			}
			
			// MultiPageEditorPart 계열인 경우 리플렉션을 통해 활성 하부 에디터를 추출
			if (editor instanceof org.eclipse.ui.part.MultiPageEditorPart) {
				org.eclipse.ui.part.MultiPageEditorPart multiPageEditor = (org.eclipse.ui.part.MultiPageEditorPart) editor;
				try {
					java.lang.reflect.Method method = org.eclipse.ui.part.MultiPageEditorPart.class.getDeclaredMethod("getActiveEditor");
					method.setAccessible(true);
					Object activeEditor = method.invoke(multiPageEditor);
					if (activeEditor instanceof ITextEditor) {
						return (ITextEditor) activeEditor;
					}
				} catch (Exception ignored) {
				}
			}
		}
		return null;
	}

	/**
	 * Scans all XML and SQLX files in the entire workspace with a progress monitor to support cancel operations.
	 */
	private Map<String, String> collectWorkspaceXmlContentsWithMonitor(IProgressMonitor monitor) {
		Map<String, String> xmlMap = new HashMap<>();
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		if (root == null) {
			return xmlMap;
		}

		try {
			root.accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource resource) throws org.eclipse.core.runtime.CoreException {
					if (monitor.isCanceled()) {
						throw new OperationCanceledException();
					}

					// Skip closed projects to avoid errors and speed up scanning
					if (resource instanceof IProject) {
						IProject proj = (IProject) resource;
						if (!proj.isOpen()) {
							return false;
						}
					}

					if (resource instanceof IFile) {
						IFile file = (IFile) resource;
						String ext = file.getFileExtension();
						if ("xml".equalsIgnoreCase(ext) || "sqlx".equalsIgnoreCase(ext)) {
							monitor.subTask("Scanning file: " + file.getName());
							
							long currentStamp = file.getModificationStamp();
							org.eclipse.core.runtime.IPath path = file.getFullPath();
							CachedXmlInfo cached = xmlCache.get(path);
							
							String content = null;
							String namespace = null;
							
							if (cached != null && cached.modificationStamp == currentStamp) {
								content = cached.content;
								namespace = cached.namespace;
							} else {
								// 1. Try reading via Eclipse Resource API first
								try (InputStream is = file.getContents();
									 BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
									content = reader.lines().collect(Collectors.joining("\n"));
								} catch (Exception syncError) {
									// 2. Fallback: If Out of Sync or other exception occurs, read directly from physical disk
									org.eclipse.core.runtime.IPath location = file.getLocation();
									if (location != null) {
										java.io.File physFile = location.toFile();
										if (physFile.exists() && physFile.isFile()) {
											try (java.io.FileInputStream fis = new java.io.FileInputStream(physFile);
												 BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"))) {
												content = reader.lines().collect(Collectors.joining("\n"));
											} catch (Exception ignored) {
											}
										}
									}
								}
								
								if (content != null) {
									namespace = extractNamespace(content);
									xmlCache.put(path, new CachedXmlInfo(currentStamp, namespace, content));
								}
							}
							
							if (content != null && namespace != null && !namespace.isEmpty()) {
								xmlMap.put(namespace, content);
							}
						}
					}
					return true;
				}
			});
		} catch (OperationCanceledException e) {
			// Silently exit on cancellation
		} catch (Exception e) {
			e.printStackTrace();
		}

		return xmlMap;
	}

	private String extractNamespace(String xmlContent) {
		Pattern namespacePattern = Pattern.compile("namespace\\s*=\\s*(?:[']([^']*)[']|[\"]([^\"]*)[\"])");
		Matcher m = namespacePattern.matcher(xmlContent);
		if (m.find()) {
			return m.group(1) != null ? m.group(1) : m.group(2);
		}
		return null;
	}
}
