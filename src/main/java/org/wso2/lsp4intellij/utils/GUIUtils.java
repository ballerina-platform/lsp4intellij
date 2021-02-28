/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.lsp4intellij.utils;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Hint;
import com.intellij.ui.LightweightHint;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.contributors.icon.LSPDefaultIconProvider;
import org.wso2.lsp4intellij.contributors.icon.LSPIconProvider;
import org.wso2.lsp4intellij.contributors.label.LSPDefaultLabelProvider;
import org.wso2.lsp4intellij.extensions.LSPExtensionManager;
import org.wso2.lsp4intellij.contributors.label.LSPLabelProvider;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;

import static org.wso2.lsp4intellij.utils.ApplicationUtils.writeAction;

public final class GUIUtils {
    private static final LSPDefaultIconProvider DEFAULT_ICON_PROVIDER = new LSPDefaultIconProvider();

    private static final LSPLabelProvider DEFAULT_LABEL_PROVIDER = new LSPDefaultLabelProvider();

    private static final Logger LOGGER = Logger.getInstance(GUIUtils.class);

    private GUIUtils() {
    }

    public static Hint createAndShowEditorHint(Editor editor, String string, Point point) {
        return createAndShowEditorHint(editor, string, point, HintManager.ABOVE,
                HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING);
    }

    public static Hint createAndShowEditorHint(Editor editor, String string, Point point, int flags) {
        return createAndShowEditorHint(editor, string, point, HintManager.ABOVE, flags);
    }

    /**
     * Shows a hint in the editor
     *
     * @param editor     The editor
     * @param string     The message / text of the hint
     * @param point      The position of the hint
     * @param constraint The constraint (under/above)
     * @param flags      The flags (when the hint will disappear)
     * @return The hint
     */
    public static Hint createAndShowEditorHint(Editor editor, String string, Point point, short constraint, int flags) {
        JTextPane textPane = new JTextPane();
        textPane.setEditorKit(new HTMLEditorKit());
        textPane.setText(string);
        int width = textPane.getPreferredSize().width;
        if (width > 600) {
            // max-width does not seem to be supported, so use this rather ugly hack...
            textPane.setText(string.replace("<style>", "<style>p {width: 600px}\n"));
        }
        textPane.setEditable(false);
        textPane.addHyperlinkListener(e -> {
            if ((e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
                    && Objects.nonNull(e.getURL())) {
                try {
                    if ("http".equals(e.getURL().getProtocol())) {
                        BrowserLauncher.getInstance().browse(e.getURL().toURI());
                    } else {
                        final Project project = editor.getProject();
                        Optional<? extends Pair<Project, VirtualFile>> fileToOpen = Optional.ofNullable(project).map(
                                p -> Optional.ofNullable(VfsUtil.findFileByURL(e.getURL()))
                                        .map(f -> new ImmutablePair<>(p, f))).orElse(Optional.empty());

                        fileToOpen.ifPresent(f -> {
                            final OpenFileDescriptor descriptor = new OpenFileDescriptor(f.getLeft(), f.getRight());
                            writeAction(() -> FileEditorManager.getInstance(f.getLeft()).openTextEditor(descriptor, true));
                        });
                    }
                } catch (URISyntaxException ex) {
                    Messages.showErrorDialog("Invalid syntax in URL", "Open URL Error");
                    LOGGER.debug("Invalid URL was found.", ex);
                }
            }
        });
        LightweightHint hint = new LightweightHint(textPane);
        Point p = HintManagerImpl.getHintPosition(hint, editor, editor.xyToLogicalPosition(point), constraint);
        HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, p, flags, 0, false,
                HintManagerImpl.createHintHint(editor, p, hint, constraint).setContentActive(false));
        return hint;
    }

    /**
     * Returns a suitable LSPIconProvider given a ServerDefinition
     *
     * @param serverDefinition The serverDefinition
     * @return The LSPIconProvider, or LSPDefaultIconProvider if none are found
     */
    public static LSPIconProvider getIconProviderFor(LanguageServerDefinition serverDefinition) {
        return IntellijLanguageClient.getExtensionManagerForDefinition(serverDefinition)
                .map(LSPExtensionManager::getIconProvider).orElse(DEFAULT_ICON_PROVIDER);
    }

    /**
     * Returns a suitable LSPLabelProvider given a ServerDefinition
     *
     * @param serverDefinition The serverDefinition
     * @return The LSPLabelProvider, or the default if none are found
     */
    public static LSPLabelProvider getLabelProviderFor(LanguageServerDefinition serverDefinition) {
        return IntellijLanguageClient.getExtensionManagerForDefinition(serverDefinition)
                .map(LSPExtensionManager::getLabelProvider).orElse(DEFAULT_LABEL_PROVIDER);
    }

}
