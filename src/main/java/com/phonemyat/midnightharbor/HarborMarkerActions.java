package com.phonemyat.midnightharbor;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

abstract class HarborMarkerActions extends AnAction implements DumbAware {
    private static final String BOARD_START = "// <harbor-markers>";
    private static final String BOARD_END = "// </harbor-markers>";

    HarborMarkerActions(String text) {
        super(text);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getData(CommonDataKeys.EDITOR) != null
                && event.getData(CommonDataKeys.PROJECT) != null
                && ProAccessManager.isProEnabled(event.getData(CommonDataKeys.PROJECT)));
    }

    static final class MarkBug extends MarkSelectionAction {
        MarkBug() {
            super("Mark Selection as Solstice.BUG", "BUG");
        }
    }

    static final class MarkTodo extends MarkSelectionAction {
        MarkTodo() {
            super("Mark Selection as Solstice.TODO", "TODO");
        }
    }

    static final class MarkIdea extends MarkSelectionAction {
        MarkIdea() {
            super("Mark Selection as Solstice.IDEA", "IDEA");
        }
    }

    static final class MarkReview extends MarkSelectionAction {
        MarkReview() {
            super("Mark Selection as Solstice.REVIEW", "REVIEW");
        }
    }

    static final class RefreshBoard extends HarborMarkerActions {
        RefreshBoard() {
            super("Refresh Solstice Marker Board");
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            Project project = event.getData(CommonDataKeys.PROJECT);
            Editor editor = event.getData(CommonDataKeys.EDITOR);
            if (project == null || editor == null || !ProAccessManager.isProEnabled(project)) {
                return;
            }

            WriteCommandAction.runWriteCommandAction(project, "Refresh Solstice Marker Board", null, () -> {
                Document document = editor.getDocument();
                String updated = withoutExistingBoard(document.getText());
                String board = buildBoard(updated);
                document.setText(board + updated);
                editor.getCaretModel().moveToOffset(Math.min(board.length(), document.getTextLength()));
            });
        }
    }

    private abstract static class MarkSelectionAction extends HarborMarkerActions {
        private final String marker;

        private MarkSelectionAction(String text, String marker) {
            super(text);
            this.marker = marker;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            Project project = event.getData(CommonDataKeys.PROJECT);
            Editor editor = event.getData(CommonDataKeys.EDITOR);
            if (project == null || editor == null || !ProAccessManager.isProEnabled(project)) {
                return;
            }

            WriteCommandAction.runWriteCommandAction(project, "Mark Selection as " + SolsticeMarkerSyntax.token(marker), null, () -> {
                Document document = editor.getDocument();
                SelectionModel selection = editor.getSelectionModel();

                int anchorOffset = selection.hasSelection()
                        ? selection.getSelectionStart()
                        : editor.getCaretModel().getOffset();
                int line = document.getLineNumber(anchorOffset);
                int lineStart = document.getLineStartOffset(line);

                String selectedText = selection.hasSelection()
                        ? selection.getSelectedText()
                        : lineText(document, line);
                String summary = summarize(selectedText);
                String markerLine = "// " + SolsticeMarkerSyntax.token(marker) + ": " + summary + "\n";

                document.insertString(lineStart, markerLine);
                editor.getCaretModel().moveToOffset(lineStart + markerLine.length());
                selection.removeSelection();
            });
        }
    }

    private static String lineText(Document document, int line) {
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        return document.getText().substring(start, end);
    }

    private static String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "selected code";
        }
        String cleaned = text.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > 90) {
            return cleaned.substring(0, 87) + "...";
        }
        return cleaned;
    }

    private static String withoutExistingBoard(String text) {
        int start = text.indexOf(BOARD_START);
        int end = text.indexOf(BOARD_END);
        if (start < 0 || end < start) {
            return text;
        }
        int afterEnd = end + BOARD_END.length();
        if (afterEnd < text.length() && text.charAt(afterEnd) == '\n') {
            afterEnd++;
        }
        return text.substring(0, start) + text.substring(afterEnd);
    }

    private static String buildBoard(String text) {
        List<String> hits = collectMarkerLines(text);
        StringBuilder board = new StringBuilder();
        board.append(BOARD_START).append('\n');
        if (hits.isEmpty()) {
            board.append("// No Solstice markers yet.").append('\n');
        } else {
            for (String hit : hits) {
                board.append("// ").append(hit).append('\n');
            }
        }
        board.append(BOARD_END).append("\n\n");
        return board.toString();
    }

    private static List<String> collectMarkerLines(String text) {
        List<String> hits = new ArrayList<>();
        int lineNumber = 1;
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                String line = text.substring(lineStart, i);
                String marker = markerIn(line);
                if (marker != null && looksLikeComment(line)) {
                    hits.add(lineNumber + ": " + SolsticeMarkerSyntax.token(marker) + " - " + cleanMarkerText(line));
                }
                lineNumber++;
                lineStart = i + 1;
            }
        }
        return hits;
    }

    private static String markerIn(String text) {
        return SolsticeMarkerSyntax.typeIn(text);
    }

    private static boolean looksLikeComment(String text) {
        String trimmed = text.trim();
        return !trimmed.contains("<harbor-markers>")
                && !trimmed.contains("</harbor-markers>")
                && (trimmed.startsWith("//")
                || trimmed.startsWith("/*")
                || trimmed.startsWith("*")
                || trimmed.startsWith("#")
                || trimmed.startsWith("<!--"));
    }

    private static String cleanMarkerText(String text) {
        return SolsticeMarkerSyntax.cleanMarkerText(text);
    }
}
