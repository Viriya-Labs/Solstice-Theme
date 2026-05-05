package com.phonemyat.midnightharbor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;

public final class HarborPulseStatusBarWidgetFactory implements StatusBarWidgetFactory {
    private static final String ID = "MidnightHarborPulse";
    private static final int ACTIVE_FRAME_DELAY_MS = 180;

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Midnight Harbor Pulse";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return ProAccessManager.isProEnabled(project);
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        SolsticeThemeSync.ensureInstalled(project);
        return new HarborPulseStatusBarWidget();
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    private static final class HarborPulseStatusBarWidget implements CustomStatusBarWidget {
        private final HarborTideActivity activity;
        private final HarborPulseComponent component;

        private HarborPulseStatusBarWidget() {
            this.activity = new HarborTideActivity();
            this.component = new HarborPulseComponent(activity);
            activity.setPulseListener(component::wake);
        }

        @Override
        public @NotNull String ID() {
            return ID;
        }

        @Override
        public @NotNull JComponent getComponent() {
            return component;
        }

        @Override
        public void dispose() {
            component.stop();
            Disposer.dispose(activity);
        }
    }

    private static final class HarborPulseComponent extends JComponent {
        private final HarborTideActivity activity;
        private final Timer timer;
        private double frame;

        private HarborPulseComponent(HarborTideActivity activity) {
            this.activity = activity;
            setPreferredSize(new Dimension(94, 24));
            setMinimumSize(new Dimension(94, 24));
            setToolTipText("Midnight Harbor horizon reacts softly to editor activity");

            timer = new Timer(ACTIVE_FRAME_DELAY_MS, event -> tick());
            timer.setRepeats(true);
            timer.setCoalesce(true);
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    updateTimerState();
                }
            });
        }

        private void stop() {
            timer.stop();
        }

        private void wake() {
            if (!isShowing()) {
                return;
            }
            if (activity.currentEnergy() <= 0.0) {
                repaint();
                return;
            }
            if (!timer.isRunning()) {
                timer.setDelay(ACTIVE_FRAME_DELAY_MS);
                timer.start();
            }
            repaint();
        }

        private void tick() {
            if (!isShowing()) {
                timer.stop();
                return;
            }
            double energy = activity.currentEnergy();
            if (energy <= 0.0) {
                timer.stop();
                repaint();
                return;
            }
            frame = (frame + 0.65 + energy * 0.7) % 360.0;
            repaint();
        }

        @Override
        public void addNotify() {
            super.addNotify();
            updateTimerState();
        }

        @Override
        public void removeNotify() {
            timer.stop();
            super.removeNotify();
        }

        private void updateTimerState() {
            if (isShowing()) {
                wake();
            } else {
                timer.stop();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidth();
                int height = getHeight();
                double energy = activity.currentEnergy();
                int horizonY = height / 2 + 2;

                g.setColor(new Color(18, 32, 51, 170));
                g.fillRoundRect(4, 5, width - 8, height - 10, 8, 8);

                paintSkyGlow(g, width, height, energy);
                paintSun(g, 18, 8, energy);
                paintMoon(g, width - 24, 7, energy);
                paintHorizon(g, width, horizonY);
                paintWave(g, width, horizonY + 1, 5.2, 1.4, frame * 0.9, new Color(44, 141, 140, 120));
                paintWave(g, width, horizonY + 4, 7.4, 1.8 + energy * 1.2, frame * 1.25 + 35.0, new Color(114, 167, 255, 150));
                paintWave(g, width, horizonY + 7, 9.2, 2.2 + energy * 1.7, frame * 1.7 + 80.0, new Color(123, 216, 143, 165));
                paintRippleGlow(g, width, height, energy);
            } finally {
                g.dispose();
            }
        }

        private void paintSkyGlow(Graphics2D g, int width, int height, double energy) {
            int alpha = 32 + (int) Math.round(energy * 28.0);
            g.setColor(new Color(242, 193, 78, alpha));
            g.fillOval(8, 4, 22, 12);
            g.setColor(new Color(143, 163, 184, 38));
            g.fillOval(width - 28, 3, 18, 12);
            paintStars(g, width, energy);
            g.setColor(new Color(29, 43, 58, 170));
            g.fillRect(5, height / 2, width - 10, height / 2 - 1);
        }

        private void paintStars(Graphics2D g, int width, double energy) {
            paintStar(g, 26, 6, 0.55, energy);
            paintStar(g, width / 2 - 5, 5, 1.2, energy);
            paintStar(g, width - 34, 8, 2.0, energy);
            paintStar(g, width - 50, 5, 2.7, energy);
        }

        private void paintStar(Graphics2D g, int x, int y, double phase, double energy) {
            double shimmer = (Math.sin(frame / 10.0 + phase) + 1.0) * 0.5;
            int alpha = 65 + (int) Math.round(shimmer * 90.0) + (int) Math.round(energy * 25.0);

            g.setColor(new Color(255, 245, 214, Math.min(220, alpha)));
            g.fillOval(x, y, 2, 2);

            if (shimmer > 0.58) {
                g.setColor(new Color(255, 236, 180, Math.min(180, alpha - 10)));
                g.drawLine(x - 1, y + 1, x + 2, y + 1);
                g.drawLine(x + 1, y - 1, x + 1, y + 2);
            }
        }

        private void paintSun(Graphics2D g, int x, int y, double energy) {
            int radius = 5 + (int) Math.round(energy * 0.8);
            g.setColor(new Color(242, 193, 78, 220));
            g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            g.setColor(new Color(242, 193, 78, 70));
            g.fillOval(x - radius - 3, y - radius - 3, radius * 2 + 6, radius * 2 + 6);
        }

        private void paintMoon(Graphics2D g, int x, int y, double energy) {
            int radius = 4 + (int) Math.round(energy * 0.5);
            g.setColor(new Color(221, 230, 243, 215));
            g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            g.setColor(new Color(16, 24, 32, 235));
            g.fillOval(x - radius + 3, y - radius - 1, radius * 2, radius * 2);
        }

        private void paintHorizon(Graphics2D g, int width, int y) {
            g.setColor(new Color(48, 70, 91, 160));
            g.drawLine(8, y, width - 8, y);
        }

        private void paintWave(Graphics2D g, int width, int baseY, double wavelength, double amplitude, double phase, Color color) {
            g.setColor(color);
            for (int x = 8; x < width - 8; x += 2) {
                int y = baseY + (int) Math.round(Math.sin((x + phase) / wavelength) * amplitude);
                g.fillRect(x, y, 2, 1);
            }
        }

        private void paintRippleGlow(Graphics2D g, int width, int height, double energy) {
            if (energy < 0.03) {
                return;
            }
            int rippleWidth = 14 + (int) Math.round(energy * 24.0);
            int x = (int) Math.round(22 + (Math.sin(frame / 18.0) + 1.0) * 0.5 * (width - 44));
            g.setColor(new Color(114, 167, 255, 26 + (int) Math.round(energy * 34.0)));
            g.fillRoundRect(x, height - 11, rippleWidth, 4, 4, 4);
        }
    }
}
