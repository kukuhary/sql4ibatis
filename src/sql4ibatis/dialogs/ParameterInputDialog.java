package sql4ibatis.dialogs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * 쿼리 수행에 필요한 파라미터 변수값을 기입받는 다이얼로그입니다. 모든 UI 명칭 및 툴팁을 영문(English)으로 치환 완료했습니다.
 */
public class ParameterInputDialog extends Dialog {

	private final List<String> parameters;
	private final Map<String, String> initialValues;
	private final Map<String, String> values = new HashMap<>();
	private final Map<String, Text> textFields = new HashMap<>();
	private final Map<String, Button> quoteButtons = new HashMap<>();
	private final Set<String> manuallyToggled = new HashSet<>();

	private static final int CLEAR_ALL_ID = 9999;

	public ParameterInputDialog(Shell parentShell, List<String> parameters, Map<String, String> initialValues) {
		super(parentShell);
		this.parameters = parameters;
		this.initialValues = initialValues != null ? initialValues : new HashMap<>();
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("iBATIS/MyBatis Query Parameter Input");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		
		GridLayout layout = new GridLayout(4, false);
		layout.marginHeight = 15;
		layout.marginWidth = 15;
		layout.verticalSpacing = 10;
		container.setLayout(layout);

		Label infoLabel = new Label(container, SWT.NONE);
		infoLabel.setText("Please enter the parameter values to bind to the query and IF conditions.");
		GridData infoGd = new GridData(GridData.FILL_HORIZONTAL);
		infoGd.horizontalSpan = 4;
		infoLabel.setLayoutData(infoGd);

		Label separator = new Label(container, SWT.SEPARATOR | SWT.HORIZONTAL);
		GridData sepGd = new GridData(GridData.FILL_HORIZONTAL);
		sepGd.horizontalSpan = 4;
		separator.setLayoutData(sepGd);

		Set<String> ifBaseVars = new HashSet<>();
		for (String param : parameters) {
			if (param.startsWith("(if) ")) {
				ifBaseVars.add(getBaseVariableName(param));
			}
		}

		for (String param : parameters) {
			CLabel label = new CLabel(container, SWT.NONE);
			label.setText(param + " :");
			label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			final String baseVarName = getBaseVariableName(param);

			if (ifBaseVars.contains(baseVarName)) {
				label.setForeground(container.getDisplay().getSystemColor(SWT.COLOR_BLUE));
			}

			Text text = new Text(container, SWT.BORDER);
			text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			
			String prevValue = initialValues.get(baseVarName);
			boolean defaultQuote = true;
			String displayValue = "";
			if (prevValue != null) {
				if (prevValue.startsWith("'") && prevValue.endsWith("'")) {
					displayValue = prevValue.substring(1, prevValue.length() - 1);
					defaultQuote = true;
					manuallyToggled.add(baseVarName);
				} else {
					displayValue = prevValue;
					defaultQuote = !prevValue.matches("-?\\d+(\\.\\d+)?");
					if (prevValue.matches("-?\\d+(\\.\\d+)?")) {
						manuallyToggled.add(baseVarName);
					}
				}
			}
			text.setText(displayValue);
			textFields.put(param, text);

			Button clearBtn = new Button(container, SWT.PUSH);
			clearBtn.setText("X");
			GridData btnGd = new GridData(SWT.CENTER, SWT.CENTER, false, false);
			btnGd.widthHint = 25;
			clearBtn.setLayoutData(btnGd);
			clearBtn.setToolTipText("Clear this input field.");
			
			clearBtn.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					text.setText("");
				}
			});

			Button quoteBtn = new Button(container, SWT.CHECK);
			quoteBtn.setText("Quote");
			quoteBtn.setToolTipText("Wrap parameter value in single quotes (String type).");
			quoteBtn.setSelection(defaultQuote);
			quoteButtons.put(param, quoteBtn);

			quoteBtn.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					manuallyToggled.add(baseVarName);
					boolean isSelected = quoteBtn.getSelection();
					for (Map.Entry<String, Button> entry : quoteButtons.entrySet()) {
						String targetParam = entry.getKey();
						Button targetQuoteBtn = entry.getValue();
						if (getBaseVariableName(targetParam).equals(baseVarName)) {
							targetQuoteBtn.setSelection(isSelected);
						}
					}
				}
			});

			text.addModifyListener(e -> {
				String currentVal = text.getText();
				if (!manuallyToggled.contains(baseVarName)) {
					boolean isNumeric = currentVal.matches("-?\\d+(\\.\\d+)?");
					quoteBtn.setSelection(!isNumeric);
				}

				for (Map.Entry<String, Text> entry : textFields.entrySet()) {
					String targetParam = entry.getKey();
					Text targetText = entry.getValue();

					if (targetText == text) {
						continue;
					}

					String targetBase = getBaseVariableName(targetParam);
					if (targetBase.equals(baseVarName)) {
						if (!targetText.getText().equals(currentVal)) {
							targetText.setText(currentVal);
						}
						Button targetQuoteBtn = quoteButtons.get(targetParam);
						if (targetQuoteBtn != null) {
							targetQuoteBtn.setSelection(quoteBtn.getSelection());
						}
					}
				}
			});
		}

		return container;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		Button clearAllBtn = createButton(parent, CLEAR_ALL_ID, "Clear All", false);
		clearAllBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				manuallyToggled.clear();
				for (Text text : textFields.values()) {
					text.setText("");
				}
				for (Button btn : quoteButtons.values()) {
					btn.setSelection(true);
				}
			}
		});

		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	private String getBaseVariableName(String paramName) {
		return paramName.replace("(if) ", "").trim();
	}

	@Override
	protected void okPressed() {
		for (String param : parameters) {
			Text text = textFields.get(param);
			Button quoteBtn = quoteButtons.get(param);
			if (text != null) {
				String plainText = text.getText().trim();
				if (quoteBtn != null && quoteBtn.getSelection()) {
					values.put(param, "'" + plainText + "'");
				} else {
					values.put(param, plainText);
				}
			}
		}
		super.okPressed();
	}

	public Map<String, String> getValues() {
		return values;
	}
	
	@Override
	protected boolean isResizable() {
		return true;
	}
}
