package com.phonemyat.midnightharbor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class HarborEditorGutterMarkerListener implements EditorFactoryListener, DumbAware {
    private static final Key<MarkerController> MARKER_CONTROLLER_KEY = Key.create("Solstice.MarkerController");

    @Override
// Solstice.BUG: void editorCreated(@NotNull EditorFactoryEvent event) { Editor editor = event.getEditor...
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (editor.isOneLineMode() || editor.getProject() == null) {
            return;
        }
// Solstice.BUG: if (!ProAccessManager.isProEnabled(editor.getProject())) { return; }
        if (!ProAccessManager.isProEnabled(editor.getProject())) {
            return;
        }
        SolsticeThemeSync.ensureInstalled(editor.getProject());

// Solstice.IDEA: kerController previous = editor.getUserData(MARKER_CONTROLLER_KEY); if (previous != nul...
        MarkerController previous = editor.getUserData(MARKER_CONTROLLER_KEY);
// Solstice.TODO: if (previous != null) { previous.dispose(); }
        if (previous != null) {
            previous.dispose();
        }
// Solstice.REVIEW: er controller = new MarkerController(editor); editor.putUserData(MARKER_CONTROLLER_KEY,...
        MarkerController controller = new MarkerController(editor);
        editor.putUserData(MARKER_CONTROLLER_KEY, controller);
        controller.install();
        controller.refresh();
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        MarkerController controller = editor.getUserData(MARKER_CONTROLLER_KEY);
        if (controller != null) {
            controller.dispose();
            editor.putUserData(MARKER_CONTROLLER_KEY, null);
        }
    }

    private static final class MarkerController implements Disposable {
        private final Editor editor;
        private final List<RangeHighlighter> highlighters = new ArrayList<>();
        private final Timer refreshTimer;
        private final CaretListener caretListener;
        private String lastText = "";
        private boolean disposed;

        private MarkerController(Editor editor) {
            this.editor = editor;
            this.refreshTimer = new Timer(250, event -> refresh());
            this.refreshTimer.setRepeats(false);
            this.caretListener = new CaretListener() {
                @Override
                public void caretPositionChanged(@NotNull CaretEvent event) {
                    scheduleRefresh();
                }
            };
        }

        private void install() {
            editor.getCaretModel().addCaretListener(caretListener);
        }

        private void scheduleRefresh() {
            if (disposed || editor.isDisposed()) {
                return;
            }
            refreshTimer.restart();
        }

        private void refresh() {
// BUG: if (editor.isDisposed()) { refreshTimer.stop(); clear(); return; }
            if (disposed || editor.isDisposed()) {
                refreshTimer.stop();
                clear();
                return;
            }

            Document document = editor.getDocument();
            String text = document.getText();
            if (text.equals(lastText)) {
                return;
            }
            lastText = text;

            clear();
            List<MarkerHit> hits = collectMarkers(text);
            for (MarkerHit hit : hits) {
                RangeHighlighter highlighter = editor.getMarkupModel().addLineHighlighter(
                        hit.line - 1,
                        HighlighterLayer.SELECTION - 1,
                        lineAttributes(hit.type)
                );
                highlighter.setErrorStripeMarkColor(hit.type.primary);
                highlighter.setErrorStripeTooltip(tooltip(hit));
                highlighter.setGutterIconRenderer(new HarborDirectGutterIconRenderer(editor, hit));
                highlighters.add(highlighter);
            }
        }

        private void clear() {
            if (editor.isDisposed()) {
                highlighters.clear();
                return;
            }
            for (RangeHighlighter highlighter : highlighters) {
                if (highlighter.isValid()) {
                    editor.getMarkupModel().removeHighlighter(highlighter);
                }
            }
            highlighters.clear();
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            refreshTimer.stop();
            editor.getCaretModel().removeCaretListener(caretListener);
            clear();
        }
    }

    private static final class HarborDirectGutterIconRenderer extends GutterIconRenderer implements DumbAware {
        private final Editor editor;
        private final MarkerHit hit;

        private HarborDirectGutterIconRenderer(Editor editor, MarkerHit hit) {
            this.editor = editor;
            this.hit = hit;
        }

        @Override
        public @NotNull Icon getIcon() {
            return hit.type.icon();
        }

        @Override
        public @Nullable String getTooltipText() {
            return tooltip(hit);
        }

        @Override
        public @Nullable AnAction getClickAction() {
            return new MarkerAction("Show " + hit.type.label + " Details", () -> showDetails(editor.getProject(), hit));
        }

        @Override
        public @Nullable ActionGroup getPopupMenuActions() {
            DefaultActionGroup group = new DefaultActionGroup();
            group.add(new MarkerAction("Show Details", () -> showDetails(editor.getProject(), hit)));
            group.add(new MarkerAction("Copy Marker Text", () -> CopyPasteManager.copyTextToClipboard(hit.text)));
            group.addSeparator();
            group.add(new MarkerAction("Go to Next Solstice Marker", () -> navigateRelative(editor, hit, 1)));
            group.add(new MarkerAction("Go to Previous Solstice Marker", () -> navigateRelative(editor, hit, -1)));
            group.addSeparator();
            group.add(new MarkerAction("Show All Solstice Markers in File", () -> showAllMarkers(editor)));
            group.add(new MarkerAction("Copy All Solstice Markers in File", () -> copyAllMarkers(editor)));
            return group;
        }

        @Override
        public @NotNull Alignment getAlignment() {
            return Alignment.RIGHT;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof HarborDirectGutterIconRenderer renderer)) {
                return false;
            }
            return hit.offset == renderer.hit.offset
                    && hit.type == renderer.hit.type
                    && Objects.equals(hit.text, renderer.hit.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hit.offset, hit.type, hit.text);
        }
    }

    private static final class MarkerAction extends AnAction implements DumbAware {
        private final Runnable action;

        private MarkerAction(String text, Runnable action) {
            super(text);
            this.action = action;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            action.run();
        }
    }

    private enum MarkerType {
        BUG("BUG", new Color(255, 81, 125), new Color(255, 120, 150)),
        REVIEW("REVIEW", new Color(82, 164, 255), new Color(126, 196, 255)),
        IDEA("IDEA", new Color(255, 195, 66), new Color(255, 220, 120)),
        TODO("TODO", new Color(153, 96, 255), new Color(188, 141, 255));

        private final String label;
        private final Color primary;
        private final Color accent;
        private Icon icon;

        MarkerType(String label, Color primary, Color accent) {
            this.label = label;
            this.primary = primary;
            this.accent = accent;
        }

        private Icon icon() {
            if (icon == null) {
                icon = new HarborMarkerIcon(this);
            }
            return icon;
        }

        private static @Nullable MarkerType fromText(String text) {
            String markerType = SolsticeMarkerSyntax.typeIn(text);
            for (MarkerType type : values()) {
                if (type.label.equals(markerType)) {
                    return type;
                }
            }
            return null;
        }

        private String token() {
            return SolsticeMarkerSyntax.token(label);
        }

        private String shortLabel() {
            return switch (this) {
                case REVIEW -> "REV";
                default -> label;
            };
        }
    }

    private static final class HarborMarkerIcon implements Icon {
        private final MarkerType type;

        private HarborMarkerIcon(MarkerType type) {
            this.type = type;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getIconWidth() - 1;
                int height = getIconHeight() - 1;
                g.setPaint(new GradientPaint(x, y, withAlpha(type.primary, 52), x + width, y + height, withAlpha(type.primary, 24)));
                g.fillRoundRect(x, y + 1, width, height - 2, 7, 7);
                g.setColor(withAlpha(type.primary, 175));
                g.setStroke(new BasicStroke(1.15f));
                g.drawRoundRect(x, y + 1, width, height - 2, 7, 7);

                g.setColor(type.primary);
                int dot = 5;
                g.fillOval(x + 5, y + (height - dot) / 2 + 1, dot, dot);

                Font font = component == null ? new Font(Font.SANS_SERIF, Font.BOLD, 9) : component.getFont().deriveFont(Font.BOLD, 9f);
                g.setFont(font);
                FontMetrics metrics = g.getFontMetrics();
                String text = type.shortLabel();
                int textX = x + 14;
                int textY = y + ((height - metrics.getHeight()) / 2) + metrics.getAscent() + 1;
                g.setColor(new Color(231, 240, 250, 235));
                g.drawString(text, textX, textY);
            } finally {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return switch (type) {
                case REVIEW -> 48;
                default -> 42;
            };
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(alpha, 255));
    }

    private static TextAttributes lineAttributes(MarkerType type) {
        return new TextAttributes(null, withAlpha(type.primary, 28), null, null, Font.PLAIN);
    }

    private static String tooltip(MarkerHit hit) {
        return hit.type.token() + ": " + hit.text;
    }

    private static void showDetails(Project project, MarkerHit hit) {
        Messages.showInfoMessage(project, hit.type.token() + "\n\n" + hit.text, "Solstice Marker");
    }

    private static void navigateRelative(Editor editor, MarkerHit current, int direction) {
        List<MarkerHit> hits = collectMarkers(editor.getDocument().getText());
        if (hits.isEmpty()) {
            return;
        }

        MarkerHit target = direction > 0 ? hits.get(0) : hits.get(hits.size() - 1);
        if (direction > 0) {
            for (MarkerHit hit : hits) {
                if (hit.offset > current.offset) {
                    target = hit;
                    break;
                }
            }
        } else {
            for (int i = hits.size() - 1; i >= 0; i--) {
                MarkerHit hit = hits.get(i);
                if (hit.offset < current.offset) {
                    target = hit;
                    break;
                }
            }
        }
        navigateTo(editor, target.offset);
    }

    private static void showAllMarkers(Editor editor) {
        List<MarkerHit> hits = collectMarkers(editor.getDocument().getText());
        Messages.showInfoMessage(editor.getProject(), formatMarkers(hits), "Solstice Markers");
    }

    private static void copyAllMarkers(Editor editor) {
        List<MarkerHit> hits = collectMarkers(editor.getDocument().getText());
        CopyPasteManager.copyTextToClipboard(formatMarkers(hits));
    }

    private static void navigateTo(Editor editor, int offset) {
        Project project = editor.getProject();
        VirtualFile virtualFile = editor.getVirtualFile();
        if (project != null && virtualFile != null) {
            new OpenFileDescriptor(project, virtualFile, offset).navigate(true);
            return;
        }
        editor.getCaretModel().moveToOffset(offset);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }

    private static String formatMarkers(List<MarkerHit> hits) {
        if (hits.isEmpty()) {
            return "No Solstice markers found in this file.";
        }

        StringBuilder builder = new StringBuilder();
        for (MarkerHit hit : hits) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(hit.line).append(": ").append(hit.type.token()).append(" - ").append(hit.text);
        }
        return builder.toString();
    }

    private static List<MarkerHit> collectMarkers(String text) {
        List<MarkerHit> hits = new ArrayList<>();
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                String lineText = text.substring(lineStart, i);
                MarkerType type = MarkerType.fromText(lineText);
                if (type != null && looksLikeComment(lineText)) {
                    int markerOffset = Math.max(0, SolsticeMarkerSyntax.markerOffset(lineText));
                    hits.add(new MarkerHit(type, line, lineStart + markerOffset, cleanMarkerText(lineText)));
                }
                line++;
                lineStart = i + 1;
            }
        }
        hits.sort(Comparator.comparingInt(hit -> hit.offset));
        return hits;
    }

    private static boolean looksLikeComment(String text) {
        return SolsticeMarkerSyntax.isComment(text);
    }

    private static String cleanMarkerText(String text) {
        return SolsticeMarkerSyntax.cleanMarkerText(text);
    }

    private record MarkerHit(MarkerType type, int line, int offset, String text) {
    }
}
