package com.phonemyat.midnightharbor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicLong;

final class HarborTideActivity implements Disposable {
    private final AtomicLong lastActivityAt = new AtomicLong(0L);
    private volatile Runnable pulseListener = () -> {
    };

    HarborTideActivity() {
        EditorFactory.getInstance().getEventMulticaster().addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(CaretEvent event) {
                pulse();
            }
        }, this);

        EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(new EditorMouseListener() {
            @Override
            public void mouseClicked(EditorMouseEvent event) {
                pulse();
            }

            @Override
            public void mousePressed(EditorMouseEvent event) {
                pulse();
            }
        }, this);
    }

    void pulse() {
        lastActivityAt.set(System.currentTimeMillis());
        Runnable listener = pulseListener;
        if (SwingUtilities.isEventDispatchThread()) {
            listener.run();
        } else {
            SwingUtilities.invokeLater(listener);
        }
    }

    void setPulseListener(Runnable pulseListener) {
        this.pulseListener = pulseListener != null ? pulseListener : () -> {
        };
    }

    double currentEnergy() {
        long lastActivity = lastActivityAt.get();
        if (lastActivity == 0L) {
            return 0.0;
        }
        long age = Math.max(0L, System.currentTimeMillis() - lastActivity);
        if (age >= 1800L) {
            return 0.0;
        }
        return 1.0 - (age / 1800.0);
    }

    @Override
    public void dispose() {
        setPulseListener(null);
    }
}
