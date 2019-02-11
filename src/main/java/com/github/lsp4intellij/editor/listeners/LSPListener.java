package com.github.lsp4intellij.editor.listeners;

import com.github.lsp4intellij.editor.EditorEventManager;
import com.intellij.openapi.diagnostic.Logger;

public class LSPListener {
    private Logger LOG = Logger.getInstance(LSPListener.class);
    protected EditorEventManager manager = null;

    /**
     * Sets the manager for this listener
     *
     * @param manager The manager
     */
    public void setManager(EditorEventManager manager) {
        this.manager = manager;
    }

    /**
     * Checks if a manager is set, and logs and error if not the case
     *
     * @return true or false depending on if the manager is set
     */
    protected boolean checkManager() {
        if (manager == null) {
            LOG.error("Manager is null");
            return false;
        }
        return true;
    }
}
