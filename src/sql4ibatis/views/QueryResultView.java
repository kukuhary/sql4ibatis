package sql4ibatis.views;

import java.util.List;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

/**
 * A view part to display query execution results in a tabular format, showing query IDs, loaded row counts, and executed raw SQL strings.
 * Visually highlights NULL values with an empty cell and a yellow background color.
 */
public class QueryResultView extends ViewPart {

	public static final String ID = "sql4ibatis.views.QueryResultView";
	private Table table;
	private Label queryIdLabel;
	private Text sqlText;
	
	private int lastClickedColumnIndex = -1;

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(1, false));

		// 1. Query information label
		queryIdLabel = new Label(parent, SWT.NONE);
		queryIdLabel.setText("Query ID: (None)  |  Loaded Rows: 0 row(s)");
		queryIdLabel.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));
		queryIdLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		SashForm sashForm = new SashForm(parent, SWT.VERTICAL);
		sashForm.setLayoutData(new GridData(GridData.FILL_BOTH));

		// 2. Upper pane: SWT Table to display query result rows
		table = new Table(sashForm, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		
		Color headerBg = new Color(table.getDisplay(), 240, 240, 240);
		table.setHeaderBackground(headerBg);
		table.addDisposeListener(e -> headerBg.dispose());

		// Capture mouse click coordinates to determine the target cell index
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				Point pt = new Point(e.x, e.y);
				TableItem item = table.getItem(pt);
				if (item != null) {
					int colCount = table.getColumnCount();
					for (int i = 0; i < colCount; i++) {
						if (item.getBounds(i).contains(pt)) {
							lastClickedColumnIndex = i;
							break;
						}
					}
				}
			}
		});

		// Ctrl+C key listener for clipboard copying
		table.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (((e.stateMask & SWT.CTRL) == SWT.CTRL || (e.stateMask & SWT.COMMAND) == SWT.COMMAND) 
						&& (e.keyCode == 'c' || e.keyCode == 'C')) {
					copySelectionToClipboard();
				}
			}
		});

		// 3. Lower pane: Text area to display the executed SQL string
		Composite sqlComposite = new Composite(sashForm, SWT.NONE);
		GridLayout sqlLayout = new GridLayout(1, false);
		sqlLayout.marginHeight = 0;
		sqlLayout.marginWidth = 0;
		sqlComposite.setLayout(sqlLayout);

		Label sqlTitleLabel = new Label(sqlComposite, SWT.NONE);
		sqlTitleLabel.setText("[ Executed SQL String ]");
		sqlTitleLabel.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));
		
		sqlText = new Text(sqlComposite, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);
		sqlText.setLayoutData(new GridData(GridData.FILL_BOTH));
		sqlText.setFont(JFaceResources.getTextFont());

		// Configure SashForm split weight ratio (Table 50% : SQL Text 50% - 반반 분할)
		sashForm.setWeights(new int[] { 50, 50 });
	}

	private void copySelectionToClipboard() {
		TableItem[] selection = table.getSelection();
		if (selection == null || selection.length == 0 || lastClickedColumnIndex == -1) {
			return;
		}

		TableItem item = selection[0];
		String cellText = item.getText(lastClickedColumnIndex);

		Clipboard clipboard = new Clipboard(table.getDisplay());
		try {
			clipboard.setContents(new Object[] { cellText }, new Transfer[] { TextTransfer.getInstance() });
		} finally {
			clipboard.dispose();
		}
	}

	@Override
	public void setFocus() {
		if (table != null && !table.isDisposed()) {
			table.setFocus();
		}
	}

	public void updateData(final String queryId, final List<String> headers, final List<List<String>> rows, final String sql) {
		if (table == null || table.isDisposed()) {
			return;
		}

		table.getDisplay().asyncExec(() -> {
			if (table.isDisposed()) {
				return;
			}

			int rowCount = (rows != null) ? rows.size() : 0;

			if (queryId != null && !queryId.trim().isEmpty()) {
				queryIdLabel.setText(String.format("Query ID: %s  |  Loaded Rows: %d row(s)", queryId, rowCount));
				setPartName("Query Result - " + queryId);
			} else {
				queryIdLabel.setText(String.format("Query ID: (Direct / Dragged SQL)  |  Loaded Rows: %d row(s)", rowCount));
				setPartName("Query Result");
			}

			if (sql != null) {
				sqlText.setText(sql);
			} else {
				sqlText.setText("");
			}

			table.setRedraw(false);

			for (TableColumn column : table.getColumns()) {
				column.dispose();
			}

			table.removeAll();
			lastClickedColumnIndex = -1;

			if (headers != null) {
				for (String header : headers) {
					TableColumn col = new TableColumn(table, SWT.LEFT);
					col.setText(header);
					col.setWidth(100);
				}
			}

			// Visual enhancement: display null data as empty cells and paint background in yellow
			if (rows != null) {
				// Get standard soft system yellow color (COLOR_INFO_BACKGROUND) for highlights
				Color yellowColor = table.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND);

				for (List<String> rowData : rows) {
					TableItem item = new TableItem(table, SWT.NONE);
					int colSize = rowData.size();
					for (int j = 0; j < colSize; j++) {
						String val = rowData.get(j);
						
						// Determine if the value represents a database NULL
						if (val == null || "NULL".equalsIgnoreCase(val.trim())) {
							item.setText(j, ""); // Keep text empty
							item.setBackground(j, yellowColor); // Highlight the cell background
						} else {
							item.setText(j, val);
						}
					}
				}
			}

			for (TableColumn col : table.getColumns()) {
				col.pack();
			}

			table.setRedraw(true);
		});
	}
}
