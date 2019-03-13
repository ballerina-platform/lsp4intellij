package com.github.lsp4intellij.actions;

import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.find.FindBundle;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightweightHint;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsageViewManagerImpl;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Action for references / see usages (SHIFT+ALT+F7)
 */
public class LSPReferencesAction extends DumbAwareAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            EditorEventManager eventManager = EditorEventManagerBase.forEditor(editor);
            if (eventManager == null) {
                return;
            }
            List<PsiElement2UsageTargetAdapter> targets = new ArrayList<>();
            Pair<List<PsiElement>, List<VirtualFile>> references = eventManager
                    .references(editor.getCaretModel().getCurrentCaret().getOffset());
            if (references.first != null && references.second != null) {
                references.first.forEach(element -> targets.add(new PsiElement2UsageTargetAdapter(element)));
            }
            showReferences(editor, targets, editor.getCaretModel().getCurrentCaret().getLogicalPosition());
        }
    }

    public void forManagerAndOffset(EditorEventManager manager, int offset) {
        List<PsiElement2UsageTargetAdapter> targets = new ArrayList<>();
        Pair<List<PsiElement>, List<VirtualFile>> references = manager.references(offset);
        if (references.first != null && references.second != null) {
            references.first.forEach(element -> targets.add(new PsiElement2UsageTargetAdapter(element)));
        }
        Editor editor = manager.editor;
        showReferences(editor, targets, editor.offsetToLogicalPosition(offset));
    }

    private void showReferences(Editor editor, List<PsiElement2UsageTargetAdapter> targets, LogicalPosition position) {
        if (targets.isEmpty()) {
            short constraint = HintManager.ABOVE;
            int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
            JLabel label = new JLabel("No references found");
            label.setBackground(new JBColor(new Color(150, 0, 0), new Color(150, 0, 0)));
            LightweightHint hint = new LightweightHint(label);
            Point p = HintManagerImpl.getHintPosition(hint, editor, position, constraint);
            HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, p, flags, 0, false,
                    HintManagerImpl.createHintHint(editor, p, hint, constraint).setContentActive(false));
        } else {
            List<Usage> usages = new ArrayList<>();
            targets.forEach(ut -> {
                PsiElement elem = ut.getElement();
                usages.add(new UsageInfo2UsageAdapter(new UsageInfo(elem, -1, -1, false)));
            });

            UsageViewPresentation presentation = createPresentation(targets.get(0).getElement(),
                    new FindUsagesOptions(editor.getProject()), false);

            new UsageViewManagerImpl(editor.getProject())
                    .showUsages(new UsageTarget[] { targets.get(0) }, usages.toArray(new Usage[usages.size()]),
                            presentation);
        }
    }

    private UsageViewPresentation createPresentation(PsiElement psiElement, FindUsagesOptions options,
            boolean toOpenInNewTab) {
        UsageViewPresentation presentation = new UsageViewPresentation();
        String scopeString = options.searchScope.getDisplayName();
        presentation.setScopeText(scopeString);
        String usagesString = options.generateUsagesString();
        presentation.setUsagesString(usagesString);
        String title = FindBundle.message("find.usages.of.element.in.scope.panel.title", usagesString,
                UsageViewUtil.getLongName(psiElement), scopeString);
        presentation.setTabText(title);
        presentation.setTabName(FindBundle
                .message("find.usages.of.element.tab.name", usagesString, UsageViewUtil.getShortName(psiElement)));
        presentation.setTargetsNodeText(StringUtil.capitalize(UsageViewUtil.getType(psiElement)));
        presentation.setOpenInNewTab(toOpenInNewTab);
        presentation.setShowCancelButton(true);
        return presentation;
    }
}
