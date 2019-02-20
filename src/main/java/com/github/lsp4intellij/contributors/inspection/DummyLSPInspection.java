package com.github.lsp4intellij.contributors.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

/*
 * This is used just to register a local inspection tool provider. `LSPInspection` contains the actual implementation
 * to handle inspections, after receiving diagnostics notification.
 */
public class DummyLSPInspection extends LocalInspectionTool {

    @NotNull
    @Override
    public String getDisplayName() {
        return getShortName();
    }

    @NotNull
    @Override
    public String getShortName() {
        return "LSP";
    }

    @NotNull
    @Override
    @Pattern("[a-zA-Z_0-9.-]+")
    public String getID() {
        return "LSP";
    }

    @NotNull
    @Override
    public String getGroupDisplayName() {
        return "LSP";
    }

    @Override
    public String getStaticDescription() {
        return "Reports errors by the LSP server";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
