package com.github.lsp4intellij.contributors.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

import java.awt.*;
import javax.swing.*;

/**
 * The Options panel for the LSP inspection
 */
public class LSPInspectionPanel extends JPanel {
    String label;
    InspectionProfileEntry owner;

    LSPInspectionPanel(String label, InspectionProfileEntry owner) {
        super(new GridBagLayout());
        this.label = label;
        this.owner = owner;
    }
}
