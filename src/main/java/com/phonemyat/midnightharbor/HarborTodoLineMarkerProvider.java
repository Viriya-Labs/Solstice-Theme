package com.phonemyat.midnightharbor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class HarborTodoLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {
        if (!(element instanceof PsiComment)) {
            return null;
        }

        MarkerType type = MarkerType.fromText(element.getText());
        if (type == null) {
            return null;
        }

        return new HarborLineMarkerInfo(element, type);
    }

    private static final class HarborLineMarkerInfo extends LineMarkerInfo<PsiElement> {
        private final MarkerType type;
        private final String markerText;

        private HarborLineMarkerInfo(PsiElement element, MarkerType type) {
            super(
                    element,
                    element.getTextRange(),
                    type.icon(),
                    psi -> tooltip(type, psi.getText()),
                    null,
                    GutterIconRenderer.Alignment.LEFT,
                    () -> type.token() + " marker"
            );
            this.type = type;
            this.markerText = element.getText();
        }

        @Override
        public GutterIconRenderer createGutterRenderer() {
            return new HarborGutterIconRenderer(this, type, markerText);
        }
    }

    private static final class HarborGutterIconRenderer extends GutterIconRenderer implements DumbAware {
        private final HarborLineMarkerInfo info;
        private final MarkerType type;
        private final String markerText;

        private HarborGutterIconRenderer(HarborLineMarkerInfo info, MarkerType type, String markerText) {
            this.info = info;
            this.type = type;
            this.markerText = markerText;
        }

        @Override
        public @NotNull Icon getIcon() {
            return type.icon();
        }

        @Override
        public @Nullable String getTooltipText() {
            return tooltip(type, markerText);
        }

        @Override
        public @Nullable AnAction getClickAction() {
            return new MarkerAction("Show " + type.label + " Details", info.getElement(), element -> showDetails(element, type));
        }

        @Override
        public @Nullable ActionGroup getPopupMenuActions() {
            DefaultActionGroup group = new DefaultActionGroup();
            PsiElement element = info.getElement();
            group.add(new MarkerAction("Show Details", element, item -> showDetails(item, type)));
            group.add(new MarkerAction("Copy Marker Text", element, item -> CopyPasteManager.copyTextToClipboard(cleanMarkerText(item.getText()))));
            group.addSeparator();
            group.add(new MarkerAction("Go to Next Solstice Marker", element, item -> navigateRelative(item, 1)));
            group.add(new MarkerAction("Go to Previous Solstice Marker", element, item -> navigateRelative(item, -1)));
            group.addSeparator();
            group.add(new MarkerAction("Show All Solstice Markers in File", element, HarborTodoLineMarkerProvider::showAllMarkers));
            group.add(new MarkerAction("Copy All Solstice Markers in File", element, HarborTodoLineMarkerProvider::copyAllMarkers));
            return group;
        }

        @Override
        public @NotNull Alignment getAlignment() {
            return Alignment.LEFT;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof HarborGutterIconRenderer renderer)) {
                return false;
            }
            return info.startOffset == renderer.info.startOffset
                    && type == renderer.type
                    && Objects.equals(markerText, renderer.markerText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(info.startOffset, type, markerText);
        }
    }

    private static final class MarkerAction extends AnAction implements DumbAware {
        private final PsiElement element;
        private final MarkerConsumer consumer;

        private MarkerAction(String text, PsiElement element, MarkerConsumer consumer) {
            super(text);
            this.element = element;
            this.consumer = consumer;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            if (element != null && element.isValid()) {
                consumer.accept(element);
            }
        }
    }

    private interface MarkerConsumer {
        void accept(PsiElement element);
    }

    private enum MarkerType {
        BUG("BUG", new Color(240, 113, 120), new Color(255, 92, 116)),
        REVIEW("REVIEW", new Color(114, 167, 255), new Color(123, 216, 143)),
        IDEA("IDEA", new Color(242, 193, 78), new Color(123, 216, 143)),
        TODO("TODO", new Color(123, 216, 143), new Color(44, 141, 140));

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

                int pulse = 2;
                int radius = 4 + pulse / 2;

                g.setColor(withAlpha(type.primary, 50 + pulse * 24));
                g.fillOval(x + 1, y + 1, 14, 14);

                g.setColor(withAlpha(type.primary, 220));
                g.fillOval(x + 8 - radius, y + 8 - radius, radius * 2, radius * 2);

                g.setStroke(new BasicStroke(1.4f));
                g.setColor(withAlpha(type.accent, 235));
                int waveY = y + 11;
                if (type == MarkerType.BUG) {
                    g.drawLine(x + 4, y + 12 - pulse / 2, x + 12, y + 4 + pulse / 2);
                    g.drawLine(x + 4, y + 4 + pulse / 2, x + 12, y + 12 - pulse / 2);
                } else if (type == MarkerType.IDEA) {
                    g.drawOval(x + 4, y + 3, 8, 8);
                    g.drawLine(x + 8, y + 11, x + 8, y + 14);
                } else {
                    g.drawArc(x + 3, waveY - pulse / 2, 10, 5 + pulse / 2, 190, 160);
                }

                g.setColor(new Color(248, 251, 255, 215));
                g.fillOval(x + 10, y + 3, 3, 3);
            } finally {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(alpha, 255));
    }

    private static String tooltip(MarkerType type, String text) {
        return type.token() + ": " + cleanMarkerText(text);
    }

    private static String cleanMarkerText(String text) {
        return SolsticeMarkerSyntax.cleanMarkerText(text);
    }

    private static void showDetails(PsiElement element, MarkerType type) {
        Messages.showInfoMessage(
                element.getProject(),
                type.token() + "\n\n" + cleanMarkerText(element.getText()),
                "Solstice Marker"
        );
    }

    private static void navigateRelative(PsiElement element, int direction) {
        List<MarkerHit> hits = collectMarkers(element.getContainingFile());
        if (hits.isEmpty()) {
            return;
        }

        int currentOffset = element.getTextRange().getStartOffset();
        MarkerHit target = direction > 0 ? hits.get(0) : hits.get(hits.size() - 1);
        if (direction > 0) {
            for (MarkerHit hit : hits) {
                if (hit.offset > currentOffset) {
                    target = hit;
                    break;
                }
            }
        } else {
            for (int i = hits.size() - 1; i >= 0; i--) {
                MarkerHit hit = hits.get(i);
                if (hit.offset < currentOffset) {
                    target = hit;
                    break;
                }
            }
        }
        navigateTo(element.getProject(), element.getContainingFile(), target.offset);
    }

    private static void showAllMarkers(PsiElement element) {
        List<MarkerHit> hits = collectMarkers(element.getContainingFile());
        if (hits.isEmpty()) {
            Messages.showInfoMessage(element.getProject(), "No Solstice markers found in this file.", "Solstice Markers");
            return;
        }
        Messages.showInfoMessage(element.getProject(), formatMarkers(hits), "Solstice Markers");
    }

    private static void copyAllMarkers(PsiElement element) {
        List<MarkerHit> hits = collectMarkers(element.getContainingFile());
        CopyPasteManager.copyTextToClipboard(formatMarkers(hits));
    }

    private static void navigateTo(Project project, PsiFile file, int offset) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
            new OpenFileDescriptor(project, virtualFile, offset).navigate(true);
        }
    }

    private static String formatMarkers(List<MarkerHit> hits) {
        StringBuilder builder = new StringBuilder();
        for (MarkerHit hit : hits) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(hit.line).append(": ").append(hit.type.token()).append(" - ").append(hit.text);
        }
        return builder.toString();
    }

    private static List<MarkerHit> collectMarkers(PsiFile file) {
        List<MarkerHit> hits = new ArrayList<>();
        if (file == null) {
            return hits;
        }

        String text = file.getText();
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                String lineText = text.substring(lineStart, i);
                MarkerType type = MarkerType.fromText(lineText);
                if (type != null && looksLikeComment(lineText)) {
                    hits.add(new MarkerHit(type, line, lineStart + Math.max(0, SolsticeMarkerSyntax.markerOffset(lineText)), cleanMarkerText(lineText)));
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

    private record MarkerHit(MarkerType type, int line, int offset, String text) {
    }
}
