package com.phonemyat.midnightharbor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class HarborMarkersToolWindowFactory implements ToolWindowFactory, DumbAware {
    private static final int MAX_SCAN_DEPTH = 16;
    private static final int MAX_MARKERS = 500;
    private static final int MAX_SCAN_CHARS = 512 * 1024;
    private static final long MAX_SCAN_FILE_BYTES = MAX_SCAN_CHARS;
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git",
            ".gradle",
            ".idea",
            ".mvn",
            ".next",
            ".venv",
            "build",
            "coverage",
            "dist",
            "node_modules",
            "out",
            "target",
            "venv"
    );

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        if (!ProAccessManager.isProEnabled(project)) {
            return;
        }
        SolsticeThemeSync.ensureInstalled(project);
        HarborMarkersPanel panel = new HarborMarkersPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "Marker Board", false);
        toolWindow.getContentManager().addContent(content);
        panel.refresh();
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return ProAccessManager.isProEnabled(project);
    }

    private static final class HarborMarkersPanel extends JPanel {
        private final Project project;
        private final DefaultListModel<MarkerHit> model = new DefaultListModel<>();
        private final JList<MarkerHit> list = new JList<>(model);
        private final MarkerFilterStrip filterStrip;
        private final JLabel summary = new JLabel("Scanning...");
        private final HarborScenePanel scene = new HarborScenePanel();
        private List<MarkerHit> allHits = new ArrayList<>();
        private String selectedFilter = "All";

        private HarborMarkersPanel(Project project) {
            super(new BorderLayout(0, 0));
            this.project = project;
            setOpaque(true);
            setBackground(BoardColors.BACKGROUND);
            setBorder(new EmptyBorder(0, 0, 0, 0));
            filterStrip = new MarkerFilterStrip(this::selectFilter);

            JButton refresh = new FlatRefreshButton("Refresh");
            refresh.addActionListener(event -> refresh());

            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setOpaque(true);
            header.setBackground(BoardColors.BACKGROUND);
            header.setBorder(new EmptyBorder(12, 14, 8, 14));

            JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 22, 0));
            tabs.setOpaque(false);
            JLabel title = boardLabel("Marker Board", BoardColors.TEXT, Font.BOLD, 15f);
            JLabel overview = boardLabel("Overview", BoardColors.MUTED, Font.PLAIN, 14f);
            tabs.add(title);
            tabs.add(overview);
            header.add(tabs, BorderLayout.WEST);
            header.add(refresh, BorderLayout.EAST);

            JPanel controls = new JPanel(new BorderLayout(0, 8));
            controls.setOpaque(true);
            controls.setBackground(BoardColors.BACKGROUND);
            controls.setBorder(new EmptyBorder(0, 14, 8, 14));
            controls.add(filterStrip, BorderLayout.NORTH);
            controls.add(boardLabel("By Priority", BoardColors.TEXT, Font.PLAIN, 13f), BorderLayout.SOUTH);

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(true);
            top.setBackground(BoardColors.BACKGROUND);
            top.add(header, BorderLayout.NORTH);
            top.add(controls, BorderLayout.SOUTH);

            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new MarkerRenderer());
            list.setBackground(BoardColors.BACKGROUND);
            list.setForeground(BoardColors.TEXT);
            list.setFixedCellHeight(58);
            list.setBorder(new EmptyBorder(0, 8, 6, 8));
            list.setOpaque(true);
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() >= 2) {
                        navigateSelected();
                    }
                }
            });

            JScrollPane scrollPane = new JScrollPane(list);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getViewport().setOpaque(true);
            scrollPane.getViewport().setBackground(BoardColors.BACKGROUND);
            scrollPane.setBackground(BoardColors.BACKGROUND);

            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, scene);
            splitPane.setResizeWeight(0.58);
            splitPane.setDividerSize(6);
            splitPane.setBorder(null);
            splitPane.setOpaque(true);
            splitPane.setBackground(BoardColors.BACKGROUND);

            summary.setOpaque(true);
            summary.setBackground(BoardColors.BACKGROUND);
            summary.setForeground(BoardColors.MUTED);
            summary.setBorder(new EmptyBorder(8, 14, 12, 14));
            summary.setFont(summary.getFont().deriveFont(Font.PLAIN, 12f));

            add(top, BorderLayout.NORTH);
            add(splitPane, BorderLayout.CENTER);
            add(summary, BorderLayout.SOUTH);
            setMinimumSize(new Dimension(320, 360));
        }

        private void refresh() {
            allHits = scanProject(project);
            scene.setCounts(allHits);
            filterStrip.setCounts(allHits);
            applyFilter();
        }

        private void applyFilter() {
            model.clear();
            for (MarkerHit hit : allHits) {
                if (matchesFilter(hit, selectedFilter)) {
                    model.addElement(hit);
                }
            }
            summary.setText(model.size() + " marker" + (model.size() == 1 ? "" : "s") + " shown");
            list.revalidate();
            list.repaint();
        }

        private boolean matchesFilter(MarkerHit hit, String filter) {
            return switch (filter) {
                case "BUG" -> hit.type.equals("BUG");
                case "TODO" -> hit.type.equals("TODO");
                case "IDEA" -> hit.type.equals("IDEA");
                case "REVIEW" -> hit.type.equals("REVIEW");
                default -> true;
            };
        }

        private void selectFilter(String filter) {
            selectedFilter = filter;
            filterStrip.setSelectedFilter(filter);
            applyFilter();
        }

        private void navigateSelected() {
            MarkerHit hit = list.getSelectedValue();
            if (hit == null) {
                return;
            }

            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(hit.path);
            if (file != null) {
                new OpenFileDescriptor(project, file, hit.line - 1, 0).navigate(true);
            }
        }

        private static JLabel boardLabel(String text, Color color, int style, float size) {
            JLabel label = new JLabel(text);
            label.setForeground(color);
            label.setFont(label.getFont().deriveFont(style, size));
            return label;
        }
    }

    private static final class MarkerRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            if (value instanceof MarkerHit hit) {
                return new MarkerRow(hit, index, selected);
            }
            return super.getListCellRendererComponent(list, value, index, selected, focused);
        }
    }

    private static final class MarkerRow extends JPanel {
        private final boolean selected;
        private final MarkerHit hit;

        private MarkerRow(MarkerHit hit, int index, boolean selected) {
            super(new BorderLayout(8, 4));
            this.hit = hit;
            this.selected = selected;
            setOpaque(false);
            setBorder(new EmptyBorder(7, 10, 7, 10));

            JPanel line = new JPanel(new BorderLayout(8, 0));
            line.setOpaque(false);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            left.add(new BadgeLabel(hit.type));
            JLabel text = new JLabel(shorten(hit.text, 54));
            text.setForeground(BoardColors.TEXT);
            text.setFont(text.getFont().deriveFont(Font.PLAIN, 13f));
            left.add(text);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);
            JLabel id = new JLabel("H-" + (110 + Math.floorMod(hit.relativePath.hashCode() + hit.line, 90)));
            id.setForeground(BoardColors.MUTED);
            id.setFont(id.getFont().deriveFont(Font.PLAIN, 12f));
            right.add(id);
            right.add(new PriorityLabel(priorityFor(hit.type)));

            line.add(left, BorderLayout.CENTER);
            line.add(right, BorderLayout.EAST);

            JLabel path = new JLabel(hit.relativePath + ":" + hit.line);
            path.setForeground(BoardColors.MUTED);
            path.setFont(path.getFont().deriveFont(Font.PLAIN, 12f));

            add(line, BorderLayout.CENTER);
            add(path, BorderLayout.SOUTH);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth() - 1;
                int height = getHeight() - 2;
                Color accent = colorForType(hit.type);
                g.setColor(selected ? BoardColors.CARD_SELECTED : BoardColors.CARD);
                g.fillRoundRect(2, 4, width - 4, height - 6, 8, 8);
                g.setColor(selected ? withAlpha(accent, 145) : BoardColors.BORDER);
                g.setStroke(new BasicStroke(selected ? 1.4f : 1.0f));
                g.drawRoundRect(2, 4, width - 4, height - 6, 8, 8);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }

        private static String shorten(String text, int max) {
            if (text.length() <= max) {
                return text;
            }
            return text.substring(0, Math.max(0, max - 1)).trim() + "...";
        }
    }

    private static final class BadgeLabel extends JLabel {
        private final String type;

        private BadgeLabel(String type) {
            super(type);
            this.type = type;
            setOpaque(false);
            setForeground(colorForType(type));
            setBorder(new EmptyBorder(2, 9, 2, 9));
            setFont(getFont().deriveFont(Font.BOLD, 11f));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = colorForType(type);
                g.setColor(withAlpha(color, 36));
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
                g.setColor(withAlpha(color, 160));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static final class PriorityLabel extends JLabel {
        private final String priority;

        private PriorityLabel(String priority) {
            super(priority);
            this.priority = priority;
            setOpaque(false);
            setForeground(priorityColor(priority));
            setBorder(new EmptyBorder(2, 7, 2, 7));
            setFont(getFont().deriveFont(Font.BOLD, 11f));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = priorityColor(priority);
                g.setColor(withAlpha(color, 30));
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g.setColor(withAlpha(color, 135));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static final class MarkerFilterStrip extends JPanel {
        private final List<FilterButton> buttons = new ArrayList<>();
        private final Consumer<String> onSelect;

        private MarkerFilterStrip(Consumer<String> onSelect) {
            super(new GridLayout(1, 5, 10, 0));
            this.onSelect = onSelect;
            setOpaque(false);
            addButton("All");
            addButton("BUG");
            addButton("TODO");
            addButton("IDEA");
            addButton("REVIEW");
            setSelectedFilter("All");
        }

        private void addButton(String filter) {
            FilterButton button = new FilterButton(filter);
            button.addActionListener(event -> onSelect.accept(button.filter));
            buttons.add(button);
            add(button);
        }

        private void setCounts(List<MarkerHit> hits) {
            int bugs = 0;
            int todos = 0;
            int ideas = 0;
            int reviews = 0;
            for (MarkerHit hit : hits) {
                switch (hit.type) {
                    case "BUG" -> bugs++;
                    case "TODO" -> todos++;
                    case "IDEA" -> ideas++;
                    case "REVIEW" -> reviews++;
                    default -> {
                    }
                }
            }
            for (FilterButton button : buttons) {
                switch (button.filter) {
                    case "All" -> button.setCount(hits.size());
                    case "BUG" -> button.setCount(bugs);
                    case "TODO" -> button.setCount(todos);
                    case "IDEA" -> button.setCount(ideas);
                    case "REVIEW" -> button.setCount(reviews);
                    default -> button.setCount(0);
                }
            }
        }

        private void setSelectedFilter(String filter) {
            for (FilterButton button : buttons) {
                button.setSelectedState(button.filter.equals(filter));
            }
        }
    }

    private static final class FilterButton extends JButton {
        private final String filter;
        private boolean selected;

        private FilterButton(String filter) {
            super(filter);
            this.filter = filter;
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(BoardColors.TEXT);
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setPreferredSize(new Dimension(72, 36));
        }

        private void setCount(int count) {
            setText("All".equals(filter) ? "All  " + count : count + "");
            setToolTipText(filterName(filter) + ": " + count);
            repaint();
        }

        private void setSelectedState(boolean selected) {
            this.selected = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color accent = "All".equals(filter) ? BoardColors.ACTIVE : colorForType(filter);
                g.setColor(selected ? withAlpha(accent, 48) : BoardColors.CARD);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
                g.setColor(selected ? withAlpha(accent, 160) : BoardColors.BORDER);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
                if (!"All".equals(filter)) {
                    int dot = 8;
                    g.setColor(accent);
                    g.fillOval(16, (getHeight() - dot) / 2, dot, dot);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }

        @Override
        public void setText(String text) {
            super.setText(text);
            setForeground(BoardColors.TEXT);
        }
    }

    private static final class FlatRefreshButton extends JButton {
        private FlatRefreshButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(BoardColors.MUTED);
            setFont(getFont().deriveFont(Font.PLAIN, 12f));
            setPreferredSize(new Dimension(74, 28));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(BoardColors.CARD);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
                g.setColor(BoardColors.BORDER);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static Color colorForType(String type) {
        return switch (type) {
            case "BUG" -> new Color(255, 81, 125);
            case "TODO" -> new Color(153, 96, 255);
            case "REVIEW" -> new Color(82, 164, 255);
            case "IDEA" -> new Color(255, 195, 66);
            default -> BoardColors.TEXT;
        };
    }

    private static Color priorityColor(String priority) {
        return switch (priority) {
            case "P1" -> new Color(255, 81, 125);
            case "P2" -> new Color(255, 195, 66);
            default -> new Color(82, 164, 255);
        };
    }

    private static int priorityRank(String type) {
        return switch (type) {
            case "BUG" -> 0;
            case "TODO", "IDEA" -> 1;
            case "REVIEW" -> 2;
            default -> 3;
        };
    }

    private static String priorityFor(String type) {
        return switch (priorityRank(type)) {
            case 0 -> "P1";
            case 1 -> "P2";
            default -> "P3";
        };
    }

    private static String filterName(String filter) {
        return switch (filter) {
            case "BUG" -> "Bugs";
            case "TODO" -> "Todos";
            case "IDEA" -> "Ideas";
            case "REVIEW" -> "Reviews";
            default -> "All markers";
        };
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static List<MarkerHit> scanProject(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return List.of();
        }

        Path root = Path.of(basePath);
        List<MarkerHit> hits = new ArrayList<>();
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), MAX_SCAN_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && shouldSkipDirectory(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return hits.size() >= MAX_MARKERS ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (hits.size() >= MAX_MARKERS) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (attrs.size() <= MAX_SCAN_FILE_BYTES && shouldScan(path)) {
                        scanFile(root, path, hits);
                    }
                    return hits.size() >= MAX_MARKERS ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            return hits;
        }

        hits.sort(Comparator.comparingInt((MarkerHit hit) -> priorityRank(hit.type))
                .thenComparing(hit -> hit.relativePath)
                .thenComparingInt(hit -> hit.line));
        return hits;
    }

    private static boolean shouldSkipDirectory(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && SKIPPED_DIRECTORIES.contains(fileName.toString().toLowerCase(Locale.ROOT));
    }

    private static boolean shouldScan(Path path) {
        String normalized = path.toString().toLowerCase(Locale.ROOT);
        return normalized.endsWith(".java")
                || normalized.endsWith(".kt")
                || normalized.endsWith(".js")
                || normalized.endsWith(".ts")
                || normalized.endsWith(".tsx")
                || normalized.endsWith(".jsx")
                || normalized.endsWith(".css")
                || normalized.endsWith(".html")
                || normalized.endsWith(".xml")
                || normalized.endsWith(".json")
                || normalized.endsWith(".md")
                || normalized.endsWith(".gradle")
                || normalized.endsWith(".kts");
    }

    private static void scanFile(Path root, Path path, List<MarkerHit> hits) {
        String text = readLiveText(path);
        if (text == null) {
            return;
        }

        int line = 1;
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (hits.size() >= MAX_MARKERS) {
                return;
            }
            if (i != text.length() && text.charAt(i) != '\n') {
                continue;
            }
            String lineText = text.substring(lineStart, i);
            String type = markerType(lineText);
            if (type != null && looksLikeComment(lineText)) {
                hits.add(new MarkerHit(type, cleanMarkerText(lineText), path, root.relativize(path).toString(), line));
            }
            line++;
            lineStart = i + 1;
        }
    }

    private static String readLiveText(Path path) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(path);
        if (file != null) {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document != null) {
                if (document.getTextLength() > MAX_SCAN_CHARS) {
                    return null;
                }
                return document.getText();
            }
        }

        try {
            if (Files.size(path) > MAX_SCAN_FILE_BYTES) {
                return null;
            }
            return Files.readString(path);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String markerType(String text) {
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

    private record MarkerHit(String type, String text, Path path, String relativePath, int line) {
    }

    private static final class BoardColors {
        private static final Color BACKGROUND = new Color(8, 17, 33);
        private static final Color CARD = new Color(13, 28, 50);
        private static final Color CARD_SELECTED = new Color(24, 41, 72);
        private static final Color BORDER = new Color(34, 53, 82);
        private static final Color TEXT = new Color(229, 237, 249);
        private static final Color MUTED = new Color(139, 159, 184);
        private static final Color ACTIVE = new Color(86, 137, 255);

        private BoardColors() {
        }
    }

    private static final class HarborScenePanel extends JPanel {
        private static final BufferedImage OVERVIEW_IMAGE = loadOverviewImage();
        private int bugCount;
        private int todoCount;
        private int ideaCount;
        private int reviewCount;

        private HarborScenePanel() {
            setOpaque(true);
            setBackground(BoardColors.BACKGROUND);
            setPreferredSize(new Dimension(360, 310));
            setMinimumSize(new Dimension(300, 250));
            setBorder(new EmptyBorder(8, 8, 10, 8));
        }

        private void setCounts(List<MarkerHit> hits) {
            int bugs = 0;
            int todos = 0;
            int ideas = 0;
            int reviews = 0;

            for (MarkerHit hit : hits) {
                switch (hit.type) {
                    case "BUG" -> bugs++;
                    case "TODO" -> todos++;
                    case "IDEA" -> ideas++;
                    case "REVIEW" -> reviews++;
                    default -> {
                    }
                }
            }

            bugCount = bugs;
            todoCount = todos;
            ideaCount = ideas;
            reviewCount = reviews;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int width = getWidth();
            int height = getHeight();
            int left = 8;
            int top = 8;
            int sceneTop = top + 36;
            int sceneBottom = height - 76;

            g.setPaint(new GradientPaint(0, top, new Color(10, 20, 38), 0, height, new Color(5, 12, 24)));
            g.fillRoundRect(left, top, width - 16, height - 18, 8, 8);
            g.setColor(new Color(43, 63, 96));
            g.drawRoundRect(left, top, width - 16, height - 18, 8, 8);

            g.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            g.setColor(BoardColors.TEXT);
            g.drawString("Solstice Overview", left + 14, top + 23);
            g.setColor(BoardColors.MUTED);
            g.drawString("v", left + 134, top + 23);

            paintHarbor(g, left + 1, sceneTop, width - 18, sceneBottom - sceneTop);
            paintSignposts(g, left + 20, sceneTop, width - 56, sceneBottom - sceneTop);
            paintTotals(g, left + 14, sceneBottom + 22, width - 40, height - sceneBottom - 36);

            g.dispose();
        }

        private void paintHarbor(Graphics2D g, int x, int y, int width, int height) {
            int skyBottom = y + (int) (height * 0.6);
            RoundRectangle2D clip = new RoundRectangle2D.Double(x, y, width, height, 7, 7);
            Composite oldComposite = g.getComposite();
            Shape oldClip = g.getClip();
            g.clip(clip);

            if (OVERVIEW_IMAGE != null) {
                paintOverviewImage(g, OVERVIEW_IMAGE, x, y, width, height);
                paintImageDepth(g, x, y, width, height);
            } else {
                g.setPaint(new GradientPaint(0, y, new Color(5, 11, 25), 0, skyBottom, new Color(16, 31, 58)));
                g.fillRect(x, y, width, skyBottom - y);
                paintAtmosphere(g, x, y, width, height);
                paintStars(g, x, y, width, skyBottom - y);
                paintMilkyWay(g, x, y, width, skyBottom - y);
                paintMoon(g, x + 28, y + 34);
                paintMountains(g, x, skyBottom, width);
                paintTown(g, x, skyBottom, width);
                paintWater(g, x, skyBottom, width, y + height - skyBottom);
                paintLighthouse(g, x, skyBottom, width);
                paintDock(g, x + width / 3, skyBottom + 44, Math.max(84, width / 3), height);
                paintBoats(g, x, skyBottom, width, height);
                paintVignette(g, x, y, width, height);
            }

            g.setClip(oldClip);
            g.setComposite(oldComposite);
        }

        private static BufferedImage loadOverviewImage() {
            try (InputStream stream = HarborMarkersToolWindowFactory.class.getResourceAsStream("/images/solstice-overview.png")) {
                return stream == null ? null : ImageIO.read(stream);
            } catch (IOException ignored) {
                return null;
            }
        }

        private void paintOverviewImage(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
            double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
            int drawWidth = (int) Math.ceil(image.getWidth() * scale);
            int drawHeight = (int) Math.ceil(image.getHeight() * scale);
            int drawX = x + (width - drawWidth) / 2;
            int drawY = y + (height - drawHeight) / 2;
            g.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
        }

        private void paintImageDepth(Graphics2D g, int x, int y, int width, int height) {
            g.setPaint(new GradientPaint(0, y, new Color(2, 7, 18, 80), 0, y + height, new Color(3, 9, 20, 130)));
            g.fillRect(x, y, width, height);
            paintVignette(g, x, y, width, height);
        }

        private void paintAtmosphere(Graphics2D g, int x, int y, int width, int height) {
            g.setPaint(new RadialGradientPaint(
                    x + width * 0.66f,
                    y + height * 0.34f,
                    Math.max(width, height) * 0.64f,
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{
                            new Color(91, 65, 126, 55),
                            new Color(30, 45, 82, 30),
                            new Color(4, 10, 24, 0)
                    }
            ));
            g.fillRect(x, y, width, height);
        }

        private void paintStars(Graphics2D g, int x, int y, int width, int height) {
            for (int i = 0; i < 92; i++) {
                int starX = x + 16 + (i * 37 + i * i * 3) % Math.max(width - 28, 1);
                int starY = y + 10 + (i * 19 + i * i) % Math.max(height - 18, 1);
                int alpha = 75 + (i * 23) % 145;
                int size = i % 17 == 0 ? 3 : 1 + i % 2;
                g.setColor(new Color(255, 231, 177, alpha));
                g.fill(new Ellipse2D.Double(starX, starY, size, size));
                if (i % 23 == 0) {
                    g.setColor(new Color(170, 205, 255, alpha / 2));
                    g.drawLine(starX - 2, starY + 1, starX + 3, starY + 1);
                    g.drawLine(starX + 1, starY - 2, starX + 1, starY + 3);
                }
            }
        }

        private void paintMilkyWay(Graphics2D g, int x, int y, int width, int height) {
            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.SrcOver.derive(0.38f));
            g.setStroke(new BasicStroke(Math.max(24f, width * 0.055f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D band = new Path2D.Double();
            band.moveTo(x + width * 0.34, y + height * 0.08);
            band.curveTo(x + width * 0.52, y + height * 0.12, x + width * 0.58, y + height * 0.52, x + width * 0.83, y + height * 0.5);
            g.setPaint(new GradientPaint(x, y, new Color(120, 144, 255, 0), x + width, y + height, new Color(224, 197, 255, 95)));
            g.draw(band);
            g.setStroke(new BasicStroke(Math.max(7f, width * 0.014f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(232, 220, 255, 75));
            g.draw(band);
            g.setComposite(oldComposite);
        }

        private void paintMoon(Graphics2D g, int moonX, int moonY) {
            g.setPaint(new RadialGradientPaint(
                    moonX + 9,
                    moonY + 10,
                    35,
                    new float[]{0f, 0.36f, 1f},
                    new Color[]{
                            new Color(255, 214, 135, 130),
                            new Color(255, 214, 135, 42),
                            new Color(255, 214, 135, 0)
                    }
            ));
            g.fill(new Ellipse2D.Double(moonX - 22, moonY - 20, 70, 70));

            g.setColor(new Color(255, 224, 158, 235));
            g.fill(new Ellipse2D.Double(moonX, moonY, 25, 25));
            g.setColor(new Color(12, 22, 43, 238));
            g.fill(new Ellipse2D.Double(moonX + 9, moonY - 1, 24, 26));
            g.setColor(new Color(255, 234, 190, 105));
            g.drawArc(moonX, moonY, 25, 25, 80, 205);
        }

        private void paintMountains(Graphics2D g, int x, int skyBottom, int width) {
            paintMountainLayer(g, x, skyBottom + 6, width, new Color(12, 22, 39, 238), 42, 0);
            paintMountainLayer(g, x, skyBottom + 2, width, new Color(20, 31, 54, 228), 58, 37);
            paintMountainLayer(g, x, skyBottom - 4, width, new Color(27, 39, 68, 208), 70, 79);
        }

        private void paintMountainLayer(Graphics2D g, int x, int baseY, int width, Color color, int maxHeight, int offset) {
            Path2D ridge = new Path2D.Double();
            ridge.moveTo(x - 10, baseY + 30);
            for (int i = -1; i <= 8; i++) {
                int peakX = x + i * Math.max(42, width / 7) + offset % 35;
                int peakY = baseY - 18 - ((i * 29 + offset) % Math.max(maxHeight, 1));
                ridge.lineTo(peakX, peakY);
                ridge.lineTo(peakX + Math.max(24, width / 14), baseY - 6 - ((i * 11 + offset) % 18));
            }
            ridge.lineTo(x + width + 12, baseY + 36);
            ridge.closePath();
            g.setColor(color);
            g.fill(ridge);

            g.setColor(new Color(157, 177, 213, 34));
            g.setStroke(new BasicStroke(1.0f));
            g.draw(ridge);
        }

        private void paintMoonReflection(Graphics2D g, int x, int waterTop, int width) {
            int reflectionX = x + width - 112;
            for (int i = 0; i < 11; i++) {
                int reflectionY = waterTop + 10 + i * 7;
                int reflectionWidth = Math.max(12, 50 - i * 3);
                g.setColor(new Color(255, 201, 108, Math.max(12, 96 - i * 8)));
                g.fillRoundRect(reflectionX - reflectionWidth / 2 + (i % 2) * 5, reflectionY, reflectionWidth, 2, 3, 3);
            }
        }

        private void paintTown(Graphics2D g, int x, int waterTop, int width) {
            int baseY = waterTop - 8;
            g.setColor(new Color(8, 19, 34, 238));
            for (int i = 0; i < 24; i++) {
                int treeX = x + 16 + i * 18;
                if (treeX > x + width - 18) {
                    break;
                }
                int treeH = 18 + (i % 5) * 6;
                Path2D tree = new Path2D.Double();
                tree.moveTo(treeX, baseY - treeH);
                tree.lineTo(treeX - 8, baseY);
                tree.lineTo(treeX + 8, baseY);
                tree.closePath();
                g.fill(tree);
            }
            for (int i = 0; i < 12; i++) {
                int houseX = x + width / 2 - 64 + i * 12;
                int houseH = 12 + (i % 4) * 4;
                g.setColor(new Color(15, 29, 46, 235));
                g.fillRect(houseX, baseY - houseH, 9, houseH);
                g.setColor(new Color(255, 207, 112, 140));
                g.fillRect(houseX + 3, baseY - houseH + 4, 2, 2);
            }
        }

        private void paintLighthouse(Graphics2D g, int x, int waterTop, int width) {
            int baseY = waterTop - 8;
            int towerX = x + width - 78;
            paintLighthouseBeam(g, towerX + 16, baseY - 58);

            g.setColor(new Color(5, 12, 22, 210));
            Path2D island = new Path2D.Double();
            island.moveTo(towerX - 44, baseY + 2);
            island.curveTo(towerX - 12, baseY - 22, towerX + 34, baseY - 18, towerX + 60, baseY + 4);
            island.lineTo(towerX + 72, baseY + 18);
            island.lineTo(towerX - 56, baseY + 18);
            island.closePath();
            g.fill(island);

            g.setColor(new Color(9, 20, 36, 240));
            Path2D tower = new Path2D.Double();
            tower.moveTo(towerX, baseY);
            tower.lineTo(towerX + 8, baseY - 61);
            tower.lineTo(towerX + 22, baseY - 61);
            tower.lineTo(towerX + 31, baseY);
            tower.closePath();
            g.fill(tower);
            g.setColor(new Color(218, 227, 239, 72));
            g.draw(tower);

            g.setColor(new Color(255, 220, 144, 210));
            g.fillRect(towerX + 9, baseY - 52, 12, 7);
            g.setPaint(new RadialGradientPaint(
                    towerX + 15,
                    baseY - 48,
                    36,
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{
                            new Color(255, 210, 132, 128),
                            new Color(255, 210, 132, 38),
                            new Color(255, 210, 132, 0)
                    }
            ));
            g.fillOval(towerX - 24, baseY - 84, 78, 70);
            g.setColor(new Color(16, 32, 52));
            g.fillRoundRect(towerX + 4, baseY - 66, 25, 10, 5, 5);
            g.setColor(new Color(255, 220, 144, 180));
            g.drawLine(towerX + 16, baseY - 66, towerX + 16, baseY - 72);
        }

        private void paintLighthouseBeam(Graphics2D g, int lampX, int lampY) {
            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.SrcOver.derive(0.55f));

            Path2D wideGlow = new Path2D.Double();
            wideGlow.moveTo(lampX, lampY);
            wideGlow.lineTo(lampX - 170, lampY - 26);
            wideGlow.lineTo(lampX - 168, lampY + 22);
            wideGlow.closePath();
            g.setPaint(new GradientPaint(
                    lampX,
                    lampY,
                    new Color(255, 218, 145, 74),
                    lampX - 170,
                    lampY,
                    new Color(255, 218, 145, 0)
            ));
            g.fill(wideGlow);

            g.setComposite(AlphaComposite.SrcOver.derive(0.34f));
            Path2D coreGlow = new Path2D.Double();
            coreGlow.moveTo(lampX, lampY);
            coreGlow.lineTo(lampX - 142, lampY - 13);
            coreGlow.lineTo(lampX - 142, lampY + 8);
            coreGlow.closePath();
            g.setPaint(new GradientPaint(
                    lampX,
                    lampY,
                    new Color(255, 228, 166, 95),
                    lampX - 142,
                    lampY,
                    new Color(255, 228, 166, 0)
            ));
            g.fill(coreGlow);

            g.setComposite(oldComposite);
        }

        private void paintDock(Graphics2D g, int x, int y, int width, int sceneHeight) {
            int endX = x + Math.max(70, width - 28);
            int endY = y + Math.max(24, sceneHeight / 5);
            Path2D dock = new Path2D.Double();
            dock.moveTo(x + 4, y - 2);
            dock.lineTo(endX, endY);
            dock.lineTo(endX + 18, endY + 10);
            dock.lineTo(x + 24, y + 12);
            dock.closePath();
            g.setPaint(new GradientPaint(x, y, new Color(32, 24, 20, 214), endX, endY, new Color(10, 12, 20, 220)));
            g.fill(dock);
            g.setColor(new Color(190, 132, 73, 76));
            g.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 7; i++) {
                double t = i / 6.0;
                int plankX = (int) Math.round(x + 12 + t * (endX - x - 8));
                int plankY = (int) Math.round(y + t * (endY - y));
                g.drawLine(plankX, plankY - 1, plankX + 13, plankY + 6);
            }
            g.setColor(new Color(4, 9, 17, 170));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 4; i++) {
                double t = i / 3.0;
                int postX = (int) Math.round(x + 16 + t * (endX - x));
                int postY = (int) Math.round(y + 6 + t * (endY - y));
                g.drawLine(postX, postY, postX, postY + 24);
            }
            for (int i = 0; i < 2; i++) {
                int lampX = x + 24 + i * Math.max(34, width / 4);
                int lampY = y + 3 + i * 13;
                g.setPaint(new RadialGradientPaint(lampX + 2, lampY - 8, 18, new float[]{0f, 1f}, new Color[]{new Color(255, 198, 111, 62), new Color(255, 198, 111, 0)}));
                g.fillOval(lampX - 15, lampY - 25, 34, 34);
                g.setColor(new Color(255, 196, 112, 180));
                g.fillRoundRect(lampX, lampY - 11, 5, 7, 4, 4);
            }
        }

        private void paintWater(Graphics2D g, int x, int y, int width, int height) {
            g.setPaint(new GradientPaint(0, y, new Color(13, 46, 72), 0, y + height, new Color(5, 14, 27)));
            g.fillRect(x, y, width, height);
            g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int row = 0; row < 15; row++) {
                int waveY = y + 7 + row * 8;
                g.setColor(new Color(100, 165, 203, 26 + row * 3));
                for (int waveX = x + 5 - row * 3; waveX < x + width; waveX += 28) {
                    g.drawArc(waveX, waveY, 22 + row % 4, 4 + row % 3, 190, 150);
                }
            }
            paintMoonReflection(g, x, y, width);
        }

        private void paintBoats(Graphics2D g, int x, int waterTop, int width, int height) {
            paintSailboat(g, x + Math.max(34, width / 8), waterTop + Math.max(54, height / 3), 1.08);
            paintHarborBoat(g, x + width - Math.max(118, width / 4), waterTop + Math.max(48, height / 4), 0.82);
        }

        private void paintSailboat(Graphics2D g, int x, int y, double scale) {
            AffineTransform oldTransform = g.getTransform();
            g.translate(x, y);
            g.scale(scale, scale);

            g.setColor(new Color(101, 174, 214, 54));
            g.drawArc(-34, 14, 82, 10, 190, 145);

            Path2D hull = new Path2D.Double();
            hull.moveTo(-35, 0);
            hull.curveTo(-16, 12, 20, 12, 38, 0);
            hull.curveTo(27, 18, -20, 18, -35, 0);
            hull.closePath();
            g.setPaint(new GradientPaint(-30, 0, new Color(8, 16, 27, 242), 32, 16, new Color(29, 20, 18, 232)));
            g.fill(hull);
            g.setColor(new Color(238, 177, 94, 132));
            g.fillRoundRect(-8, 3, 12, 5, 3, 3);
            g.fillRoundRect(12, 2, 9, 5, 3, 3);

            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(157, 204, 232, 172));
            g.drawLine(-2, 0, -2, -54);

            Path2D mainSail = new Path2D.Double();
            mainSail.moveTo(0, -52);
            mainSail.curveTo(12, -34, 23, -20, 31, -8);
            mainSail.curveTo(19, -11, 8, -10, 0, -5);
            mainSail.closePath();
            g.setPaint(new GradientPaint(0, -52, new Color(74, 150, 210, 150), 30, -8, new Color(12, 36, 64, 174)));
            g.fill(mainSail);

            Path2D frontSail = new Path2D.Double();
            frontSail.moveTo(-5, -48);
            frontSail.curveTo(-19, -30, -26, -17, -29, -7);
            frontSail.curveTo(-18, -10, -9, -8, -5, -5);
            frontSail.closePath();
            g.setPaint(new GradientPaint(-5, -48, new Color(202, 226, 240, 86), -28, -8, new Color(20, 45, 72, 170)));
            g.fill(frontSail);

            g.setColor(new Color(215, 238, 248, 58));
            g.draw(mainSail);
            g.draw(frontSail);
            g.setTransform(oldTransform);
        }

        private void paintHarborBoat(Graphics2D g, int x, int y, double scale) {
            AffineTransform oldTransform = g.getTransform();
            g.translate(x, y);
            g.scale(scale, scale);

            g.setColor(new Color(255, 197, 108, 68));
            g.fillRoundRect(-18, 20, 54, 3, 4, 4);

            Path2D hull = new Path2D.Double();
            hull.moveTo(-32, 2);
            hull.curveTo(-8, 12, 30, 11, 46, 0);
            hull.lineTo(36, 12);
            hull.curveTo(15, 18, -15, 17, -26, 10);
            hull.closePath();
            g.setPaint(new GradientPaint(-30, 0, new Color(8, 15, 25, 244), 42, 14, new Color(22, 20, 24, 235)));
            g.fill(hull);

            Path2D cabin = new Path2D.Double();
            cabin.moveTo(-5, -2);
            cabin.lineTo(16, -10);
            cabin.lineTo(29, -2);
            cabin.lineTo(27, 6);
            cabin.lineTo(-4, 6);
            cabin.closePath();
            g.setPaint(new GradientPaint(0, -10, new Color(24, 43, 60, 238), 0, 8, new Color(8, 18, 31, 238)));
            g.fill(cabin);
            g.setColor(new Color(255, 218, 142, 190));
            g.fillRoundRect(2, -3, 7, 5, 2, 2);
            g.fillRoundRect(15, -4, 7, 5, 2, 2);

            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(172, 209, 228, 120));
            g.drawLine(34, -2, 44, -18);
            g.drawLine(-18, 1, -14, -14);
            g.setColor(new Color(103, 169, 205, 70));
            g.drawArc(-38, 15, 88, 9, 190, 145);
            g.setTransform(oldTransform);
        }

        private void paintSignposts(Graphics2D g, int x, int y, int width, int height) {
            int baseY = y + height - 30;
            int boxWidth = Math.max(62, (width - 48) / 4);
            int cardHeight = Math.max(64, Math.min(102, Math.round(boxWidth * 0.55f)));
            int gap = Math.max(8, (width - boxWidth * 4) / 3);
            int lift = Math.max(0, cardHeight - 64);
            paintSignpost(g, x, baseY - cardHeight - 8 - lift / 4, boxWidth, cardHeight, "Bugs", bugCount, colorForType("BUG"), baseY);
            paintSignpost(g, x + boxWidth + gap, baseY - cardHeight - 28 - lift / 2, boxWidth, cardHeight, "Todos", todoCount, colorForType("TODO"), baseY);
            paintSignpost(g, x + (boxWidth + gap) * 2, baseY - cardHeight - 14 - lift / 3, boxWidth, cardHeight, "Ideas", ideaCount, colorForType("IDEA"), baseY);
            paintSignpost(g, x + (boxWidth + gap) * 3, baseY - cardHeight - 22 - lift / 2, boxWidth, cardHeight, "Reviews", reviewCount, colorForType("REVIEW"), baseY);
        }

        private void paintSignpost(Graphics2D g, int x, int y, int width, int cardHeight, String label, int value, Color accent, int baseY) {
            int postX = x + width / 2;
            g.setColor(withAlpha(accent, 35));
            for (int i = 0; i < 3; i++) {
                g.drawOval(postX - 13 - i * 5, baseY + 3 - i * 2, 26 + i * 10, 7 + i * 4);
            }
            g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(6, 14, 24, 160));
            g.drawLine(postX + 2, y + cardHeight - 1, postX + 2, baseY + 22);
            g.setColor(withAlpha(accent, 110));
            g.drawLine(postX, y + cardHeight - 1, postX, baseY + 22);

            g.setPaint(new RadialGradientPaint(
                    x + width / 2f,
                    y + cardHeight / 2f,
                    width * 0.78f,
                    new float[]{0f, 0.58f, 1f},
                    new Color[]{
                            withAlpha(accent, 72),
                            withAlpha(accent, 28),
                            withAlpha(accent, 0)
                    }
            ));
            g.fillRoundRect(x - 10, y - 10, width + 20, cardHeight + 16, 16, 16);

            g.setPaint(new GradientPaint(x, y, new Color(15, 29, 54, 238), x, y + cardHeight, new Color(6, 14, 28, 232)));
            g.fillRoundRect(x, y, width, cardHeight, 8, 8);
            g.setColor(new Color(255, 255, 255, 22));
            g.fillRoundRect(x + 2, y + 2, width - 4, Math.max(17, cardHeight / 3), 7, 7);
            g.setColor(withAlpha(accent, 170));
            g.setStroke(new BasicStroke(1.4f));
            g.drawRoundRect(x, y, width, cardHeight, 8, 8);

            float labelSize = Math.max(13f, Math.min(18f, width / 7.2f));
            float valueSize = Math.max(24f, Math.min(40f, width / 3.2f));
            g.setFont(getFont().deriveFont(Font.PLAIN, labelSize));
            g.setColor(new Color(203, 211, 228, 218));
            drawCentered(g, label, x, y + Math.max(10, cardHeight * 0.18), width);

            g.setFont(getFont().deriveFont(Font.BOLD, valueSize));
            g.setColor(new Color(239, 244, 255, 235));
            drawCentered(g, String.valueOf(value), x, y + cardHeight * 0.48, width);
        }

        private void paintVignette(Graphics2D g, int x, int y, int width, int height) {
            g.setPaint(new RadialGradientPaint(
                    x + width / 2f,
                    y + height / 2f,
                    Math.max(width, height) * 0.72f,
                    new float[]{0f, 0.72f, 1f},
                    new Color[]{
                            new Color(0, 0, 0, 0),
                            new Color(0, 0, 0, 30),
                            new Color(0, 0, 0, 155)
                    }
            ));
            g.fillRect(x, y, width, height);
        }

        private void paintTotals(Graphics2D g, int x, int y, int width, int height) {
            int total = bugCount + todoCount + ideaCount + reviewCount;
            g.setFont(getFont().deriveFont(Font.PLAIN, 14f));
            g.setColor(new Color(154, 164, 188));
            g.drawString("Total Markers", x, y + 4);
            g.setFont(getFont().deriveFont(Font.PLAIN, 26f));
            g.setColor(new Color(239, 244, 255, 238));
            g.drawString(String.valueOf(total), x, y + 37);

            int barX = x + width - 150;
            int[] values = {bugCount, todoCount, ideaCount, reviewCount};
            Color[] colors = {colorForType("BUG"), colorForType("TODO"), colorForType("IDEA"), colorForType("REVIEW")};
            int max = Math.max(1, Math.max(Math.max(values[0], values[1]), Math.max(values[2], values[3])));
            for (int i = 0; i < values.length; i++) {
                int barHeight = 18 + Math.round(values[i] * 32f / max);
                int drawX = barX + i * 38;
                int drawY = y + 37 - barHeight;
                g.setPaint(new GradientPaint(drawX, drawY, withAlpha(colors[i], 238), drawX, y + 37, withAlpha(colors[i], 142)));
                g.fillRect(drawX, drawY, 18, barHeight);
                g.setColor(withAlpha(new Color(255, 255, 255), 55));
                g.drawLine(drawX + 1, drawY + 1, drawX + 16, drawY + 1);
            }
        }

        private void drawCentered(Graphics2D g, String text, double x, double y, double width) {
            FontMetrics metrics = g.getFontMetrics();
            int drawX = (int) Math.round(x + (width - metrics.stringWidth(text)) / 2.0);
            int drawY = (int) Math.round(y + metrics.getAscent());
            g.drawString(text, drawX, drawY);
        }
    }
}
