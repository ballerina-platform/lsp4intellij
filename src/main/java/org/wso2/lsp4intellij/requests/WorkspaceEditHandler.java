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
package org.wso2.lsp4intellij.requests;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.wso2.lsp4intellij.contributors.psi.LSPPsiElement;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.utils.ApplicationUtils;
import org.wso2.lsp4intellij.utils.DocumentUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.wso2.lsp4intellij.utils.ApplicationUtils.invokeLater;
import static org.wso2.lsp4intellij.utils.ApplicationUtils.writeAction;
import static org.wso2.lsp4intellij.utils.DocumentUtils.toEither;

/**
 * An Object handling WorkspaceEdits
 */
public class WorkspaceEditHandler {
    private static Logger LOG = Logger.getInstance(WorkspaceEditHandler.class);

    public static void applyEdit(PsiElement elem, String newName, UsageInfo[] infos,
                                 RefactoringElementListener listener, List<VirtualFile> openedEditors) {
        Map<String, List<TextEdit>> edits = new HashMap<>();
        if (elem instanceof LSPPsiElement) {
            LSPPsiElement lspElem = (LSPPsiElement) elem;
            if (Stream.of(infos).allMatch(info -> info.getElement() instanceof LSPPsiElement)) {
                Stream.of(infos).forEach(ui -> {
                    Editor editor = FileUtils.editorFromVirtualFile(ui.getVirtualFile(), ui.getProject());
                    TextRange range = ui.getElement().getTextRange();
                    Range lspRange = new Range(DocumentUtils.offsetToLSPPos(editor, range.getStartOffset()),
                            DocumentUtils.offsetToLSPPos(editor, range.getEndOffset()));
                    TextEdit edit = new TextEdit(lspRange, newName);
                    String uri = null;
                    try {
                        uri = FileUtils.sanitizeURI(
                                new URL(ui.getVirtualFile().getUrl().replace(" ", FileUtils.SPACE_ENCODED)).toURI()
                                        .toString());
                    } catch (MalformedURLException | URISyntaxException e) {
                        LOG.warn(e);
                    }
                    if (edits.keySet().contains(uri)) {
                        edits.get(uri).add(edit);
                    } else {
                        List<TextEdit> textEdits = new ArrayList<>();
                        textEdits.add(edit);
                        edits.put(uri, textEdits);
                    }
                });
                WorkspaceEdit workspaceEdit = new WorkspaceEdit(edits);
                applyEdit(workspaceEdit, "Rename " + lspElem.getName() + " to " + newName, openedEditors);
            }
        }
    }

    public static boolean applyEdit(WorkspaceEdit edit, String name) {
        return applyEdit(edit, name, new ArrayList<>());
    }

    /**
     * Applies a WorkspaceEdit
     *
     * @param edit    The edit
     * @param name    edit name
     * @param toClose files to be closed
     * @return True if everything was applied, false otherwise
     */
    public static boolean applyEdit(WorkspaceEdit edit, String name, List<VirtualFile> toClose) {
        final String newName = (name != null) ? name : "LSP edits";
        if (edit != null) {
            Map<String, List<TextEdit>> changes = edit.getChanges();
            List<Either<TextDocumentEdit, ResourceOperation>> dChanges = edit.getDocumentChanges();
            boolean[] didApply = new boolean[]{true};

            Project[] curProject = new Project[]{null};
            List<VirtualFile> openedEditors = new ArrayList<>();

            //Get the runnable of edits for each editor to apply them all in one command
            List<Runnable> toApply = new ArrayList<>();
            if (dChanges != null) {
                dChanges.forEach(tEdit -> {
                    if (tEdit.isLeft()) {
                        TextDocumentEdit textEdit = tEdit.getLeft();
                        VersionedTextDocumentIdentifier doc = textEdit.getTextDocument();
                        int version = doc.getVersion() != null ? doc.getVersion() : Integer.MAX_VALUE;
                        String uri = FileUtils.sanitizeURI(doc.getUri());
                        EditorEventManager manager = EditorEventManagerBase.forUri(uri);
                        if (manager != null) {
                            curProject[0] = manager.editor.getProject();
                            toApply.add(manager.getEditsRunnable(version, toEither(textEdit.getEdits()), newName, true));
                        } else {
                            toApply.add(
                                    manageUnopenedEditor(textEdit.getEdits(), uri, version, openedEditors, curProject,
                                            newName));
                        }
                    } else if (tEdit.isRight()) {
                        ResourceOperation resourceOp = tEdit.getRight();
                        //TODO
                    } else {
                        LOG.warn("Null edit");
                    }
                });

            } else if (changes != null) {
                changes.forEach((key, lChanges) -> {
                    String uri = FileUtils.sanitizeURI(key);

                    EditorEventManager manager = EditorEventManagerBase.forUri(uri);
                    if (manager != null) {
                        curProject[0] = manager.editor.getProject();
                        toApply.add(manager.getEditsRunnable(Integer.MAX_VALUE, toEither(lChanges), newName, true));
                    } else {
                        toApply.add(manageUnopenedEditor(lChanges, uri, Integer.MAX_VALUE, openedEditors, curProject,
                                newName));
                    }
                });
            }
            if (toApply.contains(null)) {
                LOG.warn("Didn't apply, null runnable");
                didApply[0] = false;
            } else {
                Runnable runnable = () -> toApply.forEach(Runnable::run);
                invokeLater(() -> writeAction(() -> {
                    CommandProcessor.getInstance()
                            .executeCommand(curProject[0], runnable, name, "LSPPlugin", UndoConfirmationPolicy.DEFAULT,
                                    false);
                    openedEditors.forEach(f -> FileEditorManager.getInstance(curProject[0]).closeFile(f));
                    toClose.forEach(f -> FileEditorManager.getInstance(curProject[0]).closeFile(f));
                }));
            }
            return didApply[0];
        } else {
            return false;
        }
    }

    /**
     * Opens an editor when needed and gets the Runnable
     *
     * @param edits         The text edits
     * @param uri           The uri of the file
     * @param version       The version of the file
     * @param openedEditors
     * @param curProject
     * @param name
     * @return The runnable containing the edits
     */
    private static Runnable manageUnopenedEditor(List<TextEdit> edits, String uri, int version,
                                                 List<VirtualFile> openedEditors, Project[] curProject, String name) {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        //Infer the project from the uri
        Project project = Stream.of(projects)
                .map(p -> new ImmutablePair<>(FileUtils.VFSToURI(ProjectUtil.guessProjectDir(p)), p))
                .filter(p -> uri.startsWith(p.getLeft())).sorted(Collections.reverseOrder())
                .map(ImmutablePair::getRight).findFirst().orElse(projects[0]);
        VirtualFile file = null;
        try {
            file = LocalFileSystem.getInstance().findFileByIoFile(new File(new URI(FileUtils.sanitizeURI(uri))));
        } catch (URISyntaxException e) {
            LOG.warn(e);
        }
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
        Editor editor = ApplicationUtils
                .computableWriteAction(() -> fileEditorManager.openTextEditor(descriptor, false));
        openedEditors.add(file);
        curProject[0] = editor.getProject();
        Runnable runnable = null;
        EditorEventManager manager = EditorEventManagerBase.forEditor(editor);
        if (manager != null) {
            runnable = manager.getEditsRunnable(version, toEither(edits), name, true);
        }
        return runnable;
    }
}
