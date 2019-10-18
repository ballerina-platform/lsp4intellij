/*
 * Copyright (c) 2018-2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.Hint;
import com.intellij.ui.LightweightHint;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.contributors.icon.LSPDefaultIconProvider;
import org.wso2.lsp4intellij.contributors.icon.LSPIconProvider;
import org.wso2.lsp4intellij.extensions.LSPExtensionManager;

import java.awt.*;
import javax.swing.*;

public class GUIUtils {
    private static final LSPDefaultIconProvider DEFAULT_ICON_PROVIDER = new LSPDefaultIconProvider();

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
        LightweightHint hint = new LightweightHint(new JLabel(string));
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
}
