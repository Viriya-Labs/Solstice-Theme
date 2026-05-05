package com.phonemyat.midnightharbor;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

final class SolsticeThemeSync {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private SolsticeThemeSync() {
    }

    static void ensureInstalled(@NotNull Project project) {
        syncNow();
        if (INSTALLED.compareAndSet(false, true)) {
            ApplicationManager.getApplication().getMessageBus().connect()
                    .subscribe(LafManagerListener.TOPIC, new LafManagerListener() {
                        @Override
                        public void lookAndFeelChanged(@NotNull LafManager source) {
                            syncNow();
                        }
                    });
        }
    }

    private static void syncNow() {
        Application application = ApplicationManager.getApplication();
        application.invokeLater(() -> {
            LafManager lafManager = LafManager.getInstance();
            if (lafManager == null) {
                return;
            }

            UIThemeLookAndFeelInfo currentTheme = lafManager.getCurrentUIThemeLookAndFeel();
            if (currentTheme == null || currentTheme.getName() == null || !currentTheme.getName().startsWith("Solstice ")) {
                return;
            }

            EditorColorsManager colorsManager = EditorColorsManager.getInstance();
            EditorColorsScheme scheme = colorsManager.getSchemeForCurrentUITheme();
            if (scheme == null) {
                String schemeId = currentTheme.getEditorSchemeId();
                if (schemeId != null) {
                    scheme = colorsManager.getScheme(schemeId);
                }
            }

            if (scheme != null && colorsManager.getGlobalScheme() != scheme) {
                colorsManager.setCurrentSchemeOnLafChange(scheme);
                colorsManager.setGlobalScheme(scheme);
            }

            for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
                FileStatusManager.getInstance(openProject).fileStatusesChanged();

                FileEditorManager editorManager = FileEditorManager.getInstance(openProject);
                for (VirtualFile file : editorManager.getOpenFiles()) {
                    editorManager.updateFilePresentation(file);
                    editorManager.updateFileColor(file);
                }
            }
        });
    }
}
