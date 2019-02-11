package com.github.lsp4intellij.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;

public class ApplicationUtils {

    static public void invokeLater(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable);
    }

    static public void pool(Runnable runnable) {
        ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }

    static public <T> T computableReadAction(Computable<T> computable) {
        return ApplicationManager.getApplication().runReadAction(computable);
    }

    static public void writeAction(Runnable runnable) {
        ApplicationManager.getApplication().runWriteAction(runnable);
    }

    static public <T> T computableWriteAction(Computable<T> computable) {
        return ApplicationManager.getApplication().runWriteAction(computable);
    }
}
