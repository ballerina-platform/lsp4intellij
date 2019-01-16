package com.github.lsp4intellij.requests;

import com.github.lsp4intellij.utils.ApplicationUtils;
import com.github.lsp4intellij.utils.DocumentUtils;
import com.github.lsp4intellij.utils.FileUtils;
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

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * An Object handling WorkspaceEdits
 */
public class WorkspaceEditHandler {
    private Logger LOG = Logger.getInstance(WorkspaceEditHandler.class);

    public void applyEdit(PsiElement elem, String newName, UsageInfo[] infos, RefactoringElementListener listener,
                          Iterable<VirtualFile> openedEditors) {
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
                    String uri = FileUtils.sanitizeURI(
                            new URL(ui.getVirtualFile().getUrl().replace(" ", FileUtils.SPACE_ENCODED)).toURI
                                    .toString());
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

    /**
     * Applies a WorkspaceEdit
     *
     * @param edit The edit
     * @return True if everything was applied, false otherwise
     */
    public boolean applyEdit(WorkspaceEdit edit, String name, Iterable<VirtualFile> toClose) {
        final String newName = (name == null) ? "LSP edits" : name;

        if (edit != null) {
            Map<String, List<TextEdit>> changes = (edit.getChanges() != null) ? edit.getChanges() : null;
            List<Either<TextDocumentEdit, ResourceOperation>> dChanges =
                    (edit.getDocumentChanges() != null) ? edit.getDocumentChanges() : null;
            boolean[] didApply = new boolean[]{true};

            ApplicationUtils.invokeLater(() -> {
                Project[] curProject = new Project[]{null};
                List<VirtualFile> openedEditors = new ArrayList<>();

                //Get the runnable of edits for each editor to apply them all in one command
                List<Runnable> toApply = new ArrayList<>();
                if (dChanges != null) {
                    dChanges.stream().forEach(tEdit -> {
                        if (tEdit.isLeft()) {
                            TextDocumentEdit textEdit = tEdit.getLeft();
                            VersionedTextDocumentIdentifier doc = textEdit.getTextDocument();
                            int version = doc.getVersion();
                            String uri = FileUtils.sanitizeURI(doc.getUri());
                            EditorEventManager manager = EditorEventManager.forUri(uri);
                            if (manager != null) {
                                curProject[0] = manager.editor().getProject();
                                toApply.add(manager.getEditsRunnable(version, textEdit.getEdits(), newName));
                            } else {
                                toApply.add(manageUnopenedEditor(textEdit.getEdits(), uri, version, openedEditors, curProject, newName));
                            }
                            ;
                        } else if (tEdit.isRight()) {
                            ResourceOperation resourceOp = tEdit.getRight();
                            //TODO
                        } else {
                            LOG.warn("Null edit");
                        }

                    });

                } else if (changes != null) {
                    changes.entrySet().stream().forEach(rEdit -> {
                        String uri = FileUtils.sanitizeURI(rEdit.getKey());
                        List<TextEdit> lChanges = rEdit.getValue();

                        EditorEventManager manager = EditorEventManager.forUri(uri);
                        if (manager != null) {
                            curProject[0] = manager.editor().getProject();
                            toApply.add(manager.getEditsRunnable(Integer.MAX_VALUE, lChanges, newName));
                        } else {
                            toApply.add(manageUnopenedEditor(lChanges, uri, Integer.MAX_VALUE, openedEditors, curProject, newName));
                        }
                        ;
                    });
                }
                if (toApply.contains(null)) {
                    LOG.warn("Didn't apply, null runnable");
                    didApply[0] = false;
                } else {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            toApply.forEach(r -> r.run());
                        }
                    };
                    ApplicationUtils.invokeLater(() -> ApplicationUtils.writeAction(() -> {
                        CommandProcessor.getInstance().executeCommand(curProject, runnable, name, "LSPPlugin",
                                                                      UndoConfirmationPolicy.DEFAULT, false);
                        openedEditors.forEach(f -> FileEditorManager.getInstance(curProject[0]).closeFile(f));
                        toClose.forEach(f -> FileEditorManager.getInstance(curProject[0]).closeFile(f));
                    }));
                }
            });
            return didApply[0];
        } else {
            return false;
        }
    }

    /**
     * Opens an editor when needed and gets the Runnable
     *
     * @param edits   The text edits
     * @param uri     The uri of the file
     * @param version The version of the file
     * @param openedEditors
     * @param curProject
     * @param name
     * @return The runnable containing the edits
     */
    public Runnable manageUnopenedEditor(Iterable<TextEdit> edits, String uri, int version,
                                         List<VirtualFile> openedEditors,
                                         Project[] curProject, String name){
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        //Infer the project from the uri
        Project project = Stream.of(projects)
                .map(p -> new ImmutablePair<String, Project>(
                        FileUtils.VFSToURI(ProjectUtil.guessProjectDir(p)), p))
                .filter(p -> uri.startsWith(p.getLeft()))
                .sorted((o1, o2) -> o1.getLeft().length())
                .sorted(Collections.reverseOrder())
                .map(p -> p.getRight())
                .findFirst()
                .orElse(projects[0]);
        VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(
                new File(new URI(FileUtils.sanitizeURI(uri))));
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
        Editor editor = ApplicationUtils.computableWriteAction(() -> {
            return fileEditorManager.openTextEditor(descriptor, false);
        });
        openedEditors.add(file);
        curProject[0] = editor.getProject();
        Runnable runnable = null;
        EditorEventManager manager = EditorEventManager.forEditor(editor);
        if (manager != null) {
            runnable = manager.getEditsRunnable(version, edits, name);
        }
        return runnable;
    }

    class Foo implements  Runnable {

        @Override
        public void run() {

        }
    }
}
