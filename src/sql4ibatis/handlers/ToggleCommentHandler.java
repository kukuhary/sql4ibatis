package sql4ibatis.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Handles the custom comment toggle action (Ctrl + /) for XML and SQLX files.
 * Inserts "-- " at the absolute beginning of each selected line to comment it out,
 * and removes "-- " or "--" to uncomment.
 */
public class ToggleCommentHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (!(editor instanceof ITextEditor)) {
			return null;
		}

		ITextEditor textEditor = (ITextEditor) editor;
		IDocument doc = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		ISelection selection = textEditor.getSelectionProvider().getSelection();

		if (doc == null || !(selection instanceof ITextSelection)) {
			return null;
		}

		ITextSelection textSelection = (ITextSelection) selection;
		int startLine = textSelection.getStartLine();
		int endLine = textSelection.getEndLine();

		try {
			// 1. Determine if all selected non-empty lines are already commented with "--"
			boolean allCommented = true;
			boolean hasNonEmptyLine = false;

			for (int i = startLine; i <= endLine; i++) {
				int lineOffset = doc.getLineOffset(i);
				int lineLength = doc.getLineLength(i);
				String lineText = doc.get(lineOffset, lineLength);

				if (lineText.trim().isEmpty()) {
					continue;
				}
				hasNonEmptyLine = true;
				if (!lineText.startsWith("--")) {
					allCommented = false;
					break;
				}
			}

			// 2. Perform Toggle Commenting / Uncommenting
			if (hasNonEmptyLine && allCommented) {
				// Uncomment: Remove "-- " or "--" from the absolute beginning of each selected line
				for (int i = startLine; i <= endLine; i++) {
					int lineOffset = doc.getLineOffset(i);
					int lineLength = doc.getLineLength(i);
					String lineText = doc.get(lineOffset, lineLength);

					if (lineText.startsWith("-- ")) {
						doc.replace(lineOffset, 3, "");
					} else if (lineText.startsWith("--")) {
						doc.replace(lineOffset, 2, "");
					}
				}
			} else {
				// Comment: Insert "-- " at the absolute beginning of each selected line
				for (int i = startLine; i <= endLine; i++) {
					int lineOffset = doc.getLineOffset(i);
					int lineLength = doc.getLineLength(i);
					String lineText = doc.get(lineOffset, lineLength);

					if (lineText.trim().isEmpty()) {
						continue;
					}
					doc.replace(lineOffset, 0, "-- ");
				}
			}

		} catch (BadLocationException e) {
			e.printStackTrace();
		}

		return null;
	}
}
