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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFileBase;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.extensions.LSPExtensionManager;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.wso2.lsp4intellij.utils.ApplicationUtils.computableReadAction;

/**
 * Various file / uri related methods
 */
public class FileUtils {
    private final static OS os = (System.getProperty("os.name").toLowerCase().contains("win")) ? OS.WINDOWS : OS.UNIX;
    private final static String COLON_ENCODED = "%3A";
    public final static String SPACE_ENCODED = "%20";
    private final static String URI_FILE_BEGIN = "file:";
    private final static String URI_VALID_FILE_BEGIN = "file:///";
    private final static char URI_PATH_SEP = '/';

    private static final Logger LOG = Logger.getInstance(FileUtils.class);

    public static List<Editor> getAllOpenedEditors(Project project) {
        return computableReadAction(() -> {
            List<Editor> editors = new ArrayList<>();
            FileEditor[] allEditors = FileEditorManager.getInstance(project).getAllEditors();
            for (FileEditor fEditor : allEditors) {
                if (fEditor instanceof TextEditor) {
                    Editor editor = ((TextEditor) fEditor).getEditor();
                    if (editor.isDisposed() || !isEditorSupported(editor)) {
                        continue;
                    }
                    editors.add(editor);
                }
            }
            return editors;
        });
    }

    public static List<Editor> getAllOpenedEditorsForUri(Project project, String uri) {
        VirtualFile file = virtualFileFromURI(uri);
        return getAllOpenedEditorsForVirtualFile(project, file);
    }

    public static List<Editor> getAllOpenedEditorsForVirtualFile(Project project, VirtualFile file) {
        return computableReadAction(() -> {
            List<Editor> editors = new ArrayList<>();
            FileEditor[] allEditors = FileEditorManager.getInstance(project).getAllEditors(file);
            for (FileEditor fEditor : allEditors) {
                if (fEditor instanceof TextEditor) {
                    Editor editor = ((TextEditor) fEditor).getEditor();
                    if (editor.isDisposed() || !isEditorSupported(editor)) {
                        continue;
                    }
                    editors.add(editor);
                }
            }
            return editors;
        });
    }

    /**
     * This can be used to instantly apply a language server definition without restarting the IDE.
     */
    public static void reloadAllEditors() {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : openProjects) {
            reloadEditors(project);
        }
    }

    /**
     * This can be used to instantly apply a project-specific language server definition without restarting the
     * project/IDE.
     *
     * @param project The project instance which need to be restarted
     */
    public static void reloadEditors(@NotNull Project project) {
        try {
            List<Editor> allOpenedEditors = FileUtils.getAllOpenedEditors(project);
            allOpenedEditors.forEach(IntellijLanguageClient::editorClosed);
            allOpenedEditors.forEach(IntellijLanguageClient::editorOpened);
        } catch (Exception e) {
            LOG.warn(String.format("Refreshing project: %s is failed due to: ", project.getName()), e);
        }
    }

    public static Editor editorFromPsiFile(PsiFile psiFile) {
        return editorFromVirtualFile(psiFile.getVirtualFile(), psiFile.getProject());
    }

    public static Editor editorFromUri(String uri, Project project) {
        return editorFromVirtualFile(virtualFileFromURI(uri), project);
    }

    @Nullable
    public static Editor editorFromVirtualFile(VirtualFile file, Project project) {
        FileEditor[] allEditors = FileEditorManager.getInstance(project).getAllEditors(file);
        if (allEditors.length > 0 && allEditors[0] instanceof TextEditor) {
            return ((TextEditor) allEditors[0]).getEditor();
        }
        return null;
    }

    public static VirtualFile virtualFileFromURI(String uri) {
        try {
            return LocalFileSystem.getInstance().findFileByIoFile(new File(new URI(sanitizeURI(uri))));
        } catch (URISyntaxException e) {
            LOG.warn(e);
            return null;
        }
    }

    /**
     * Returns a file type given an editor
     *
     * @param editor The editor
     * @return The FileType
     */
    public static FileType fileTypeFromEditor(Editor editor) {
        return FileDocumentManager.getInstance().getFile(editor.getDocument()).getFileType();
    }

    /**
     * Transforms an editor (Document) identifier to an LSP identifier
     *
     * @param editor The editor
     * @return The TextDocumentIdentifier
     */
    public static TextDocumentIdentifier editorToLSPIdentifier(Editor editor) {
        return new TextDocumentIdentifier(editorToURIString(editor));
    }

    /**
     * Returns the URI string corresponding to an Editor (Document)
     *
     * @param editor The Editor
     * @return The URI
     */
    public static String editorToURIString(Editor editor) {
        return sanitizeURI(VFSToURI(FileDocumentManager.getInstance().getFile(editor.getDocument())));
    }

    public static VirtualFile virtualFileFromEditor(Editor editor) {
        return FileDocumentManager.getInstance().getFile(editor.getDocument());
    }

    /**
     * Returns the URI string corresponding to a VirtualFileSystem file
     *
     * @param file The file
     * @return the URI
     */
    public static String VFSToURI(VirtualFile file) {
        try {
            return sanitizeURI(new URL(file.getUrl().replace(" ", SPACE_ENCODED)).toURI().toString());
        } catch (MalformedURLException | URISyntaxException e) {
            LOG.warn(e);
            return null;
        }
    }

    /**
     * Fixes common problems in uri, mainly related to Windows
     *
     * @param uri The uri to sanitize
     * @return The sanitized uri
     */
    public static String sanitizeURI(String uri) {
        if (uri != null) {
            StringBuilder reconstructed = new StringBuilder();
            String uriCp = uri.replaceAll(" ", SPACE_ENCODED); //Don't trust servers
            if (!uri.startsWith(URI_FILE_BEGIN)) {
                LOG.warn("Malformed uri : " + uri);
                return uri; //Probably not an uri
            } else {
                uriCp = uriCp.substring(URI_FILE_BEGIN.length());
                while (uriCp.startsWith(Character.toString(URI_PATH_SEP))) {
                    uriCp = uriCp.substring(1);
                }
                reconstructed.append(URI_VALID_FILE_BEGIN);
                if (os == OS.UNIX) {
                    return reconstructed.append(uriCp).toString();
                } else {
                    reconstructed.append(uriCp.substring(0, uriCp.indexOf(URI_PATH_SEP)));
                    char driveLetter = reconstructed.charAt(URI_VALID_FILE_BEGIN.length());
                    if (Character.isLowerCase(driveLetter)) {
                        reconstructed.setCharAt(URI_VALID_FILE_BEGIN.length(), Character.toUpperCase(driveLetter));
                    }
                    if (reconstructed.toString().endsWith(COLON_ENCODED)) {
                        reconstructed.delete(reconstructed.length() - 3, reconstructed.length());
                    }
                    if (!reconstructed.toString().endsWith(":")) {
                        reconstructed.append(":");
                    }
                    return reconstructed.append(uriCp.substring(uriCp.indexOf(URI_PATH_SEP))).toString();
                }
            }
        } else {
            return null;
        }
    }

    /**
     * Transforms an URI string into a VFS file
     *
     * @param uri The uri
     * @return The virtual file
     */
    public static VirtualFile URIToVFS(String uri) {
        try {
            return LocalFileSystem.getInstance().findFileByIoFile(new File(new URI(sanitizeURI(uri))));
        } catch (URISyntaxException e) {
            LOG.warn(e);
            return null;
        }
    }

    /**
     * Returns the project base dir uri given an editor
     *
     * @param editor The editor
     * @return The project whose the editor belongs
     */
    public static String editorToProjectFolderUri(Editor editor) {
        return pathToUri(editorToProjectFolderPath(editor));
    }

    public static String editorToProjectFolderPath(Editor editor) {
        if (editor != null && editor.getProject() != null && editor.getProject().getBasePath() != null) {
            return new File(editor.getProject().getBasePath()).getAbsolutePath();
        }
        return null;
    }

    /**
     * Transforms a path into an URI string
     *
     * @param path The path
     * @return The uri
     */
    public static String pathToUri(@Nullable String path) {
        return path != null ? sanitizeURI(new File(path.replace(" ", SPACE_ENCODED)).toURI().toString()) : null;
    }

    public static String projectToUri(Project project) {
        if (project != null && project.getBasePath() != null) {
            return pathToUri(new File(project.getBasePath()).getAbsolutePath());
        }
        return null;
    }

    public static String documentToUri(Document document) {
        return sanitizeURI(VFSToURI(FileDocumentManager.getInstance().getFile(document)));
    }

    /**
     * Object representing the OS type (Windows or Unix)
     */
    public enum OS {
        WINDOWS, UNIX
    }

    /**
     * Checks if the given virtual file instance is supported by this LS client library.
     */
    public static boolean isFileSupported(@Nullable VirtualFile file) {
        if (file == null) {
            return false;
        }

        if (file instanceof LightVirtualFileBase) {
            return false;
        }

        if (file.getUrl().isEmpty() || file.getUrl().startsWith("jar:")) {
            return false;
        }

        return IntellijLanguageClient.isExtensionSupported(file);
    }

    /**
     * Find projects which contains the given file. This search runs among all open projects.
     */
    @NotNull
    public static Set<Project> findProjectsFor(@NotNull VirtualFile file) {
        return Arrays.stream(ProjectManager.getInstance().getOpenProjects())
                .flatMap(p -> Arrays.stream(searchFiles(file.getName(), p)))
                .filter(f -> f.getVirtualFile().getPath().equals(file.getPath())).map(PsiElement::getProject)
                .collect(Collectors.toSet());
    }

    public static PsiFile[] searchFiles(String fileName, Project p) {
        try {
            return computableReadAction(() -> FilenameIndex.getFilesByName(p, fileName, GlobalSearchScope.projectScope(p)));
        } catch (Throwable t) {
            // Todo - Find a proper way to handle when IDEA file indexing is in-progress.
            return new PsiFile[0];
        }
    }

    /**
     * Checks if the file in editor is supported by this LS client library.
     */
    public static boolean isEditorSupported(@NotNull Editor editor) {
        return isFileSupported(virtualFileFromEditor(editor)) &&
                isFileContentSupported(editor);
    }

    // Always returns true unless the user has registered filtering to validate file content via LS protocol extension
    // manager implementation.
    private static boolean isFileContentSupported(Editor editor) {
        return computableReadAction(() -> {
            if (editor.getProject() == null) {
                return true;
            }
            PsiFile file = PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(editor.getDocument());
            if (file == null) {
                return true;
            }
            LSPExtensionManager lspExtManager = IntellijLanguageClient.getExtensionManagerFor(file.getVirtualFile().getExtension());
            if (lspExtManager == null) {
                return true;
            }
            return lspExtManager.isFileContentSupported(file);
        });
    }
}
