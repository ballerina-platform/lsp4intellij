package com.github.lsp4intellij.contributors.inspection;

import com.intellij.codeInspection.InspectionToolProvider;
import org.jetbrains.annotations.NotNull;

/**
 * The provider for the LSP Inspection
 * Returns a single class, DummyLSPInspection
 */
public class LSPInspectionProvider implements InspectionToolProvider {
    @NotNull
    @Override
    public Class[] getInspectionClasses() {
        return new Class[] { DummyLSPInspection.class };
    }
}
