package com.github.lsp4intellij;

import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapperImpl;
import com.github.lsp4intellij.editor.listeners.EditorListener;
import com.github.lsp4intellij.editor.listeners.FileDocumentManagerListenerImpl;
import com.github.lsp4intellij.editor.listeners.VFSListener;
import com.github.lsp4intellij.extensions.LSPExtensionManager;
import com.github.lsp4intellij.utils.ApplicationUtils;
import com.github.lsp4intellij.utils.FileUtils;
import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PluginMain implements ApplicationComponent {
    private static Logger LOG = Logger.getInstance(PluginMain.class);

    private static final String SPLIT_CHAR = ";";

    private static final Map<Pair<String, String>, LanguageServerWrapper> extToLanguageWrapper = new ConcurrentHashMap<>();
    private static Map<String, Set<LanguageServerWrapper>> projectToLanguageWrappers = new ConcurrentHashMap<>();
    private static Map<String, LanguageServerDefinition> extToServerDefinition = new ConcurrentHashMap<>();
    private static Map<String, LSPExtensionManager> extToExtManager = new ConcurrentHashMap<>();
    private static boolean loadedExtensions = false;

    @Override
    public void initComponent() {
        // LSPState.getInstance.getState(); //Need that to trigger loadState
        EditorFactory.getInstance().addEditorFactoryListener(new EditorListener(), Disposer.newDisposable());
        VirtualFileManager.getInstance().addVirtualFileListener(new VFSListener());
        ApplicationManager.getApplication().getMessageBus().connect()
                .subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListenerImpl());
        LOG.info("PluginMain init finished");
    }

    public static void addLSPExtension(String ext, LSPExtensionManager manager) {
        extToExtManager.put(ext, manager);
    }

    /**
     * @return All instantiated ServerWrappers
     */
    public static Set<LanguageServerWrapper> getAllServerWrappers() {
        Set<LanguageServerWrapper> result = new HashSet<>();
        for (Set<LanguageServerWrapper> wrappers : projectToLanguageWrappers.values()) {
            result.addAll(wrappers);
        }
        return result;
    }

    /**
     * @param ext An extension
     * @return True if there is a LanguageServer supporting this extension, false otherwise
     */
    public static boolean isExtensionSupported(String ext) {
        return extToServerDefinition.keySet().contains(ext);
    }

    /**
     * Sets the extensions->languageServer mapping
     *
     * @param newExt a Java Map
     */
    public static void setExtToServerDefinition(Map<String, LanguageServerDefinition> newExt) {
        List<Map.Entry<String, LanguageServerDefinition>> nullDef = newExt.entrySet().stream()
                .filter(d -> d.getValue() == null).collect(Collectors.toList());
        Map<String, LanguageServerDefinition> oldServerDef = extToServerDefinition;
        Map<String, LanguageServerDefinition> flattened = newExt.entrySet().stream().filter(d -> d.getValue() != null)
                .flatMap(t -> Stream.of(t.getKey().split(SPLIT_CHAR))
                        .map(ext -> new AbstractMap.SimpleEntry<>(ext, t.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        extToServerDefinition = flattened;
        flattenExt();
        nullDef.forEach(ext -> LOG.error("Definition for " + ext + " is null"));

        ApplicationUtils.pool(() -> {
            Set<String> added = flattened.keySet().stream().filter(e -> !oldServerDef.keySet().contains(e))
                    .collect(Collectors.toSet());
            Set<String> removed = oldServerDef.keySet().stream().filter(e -> !flattened.keySet().contains(e))
                    .collect(Collectors.toSet());

            extToLanguageWrapper.keySet().stream().filter(k -> removed.contains(k.getKey())).forEach(k -> {
                LanguageServerWrapper wrapper = extToLanguageWrapper.get(k);
                wrapper.stop(false);
                wrapper.removeWidget();
                extToLanguageWrapper.remove(k);
            });

            List<Editor> openedEditors = ApplicationUtils.computableReadAction(
                    () -> Arrays.stream(ProjectManager.getInstance().getOpenProjects())
                            .flatMap(proj -> Arrays.stream(FileEditorManager.getInstance(proj).getAllEditors()))
                            .filter(TextEditor.class::isInstance).map(TextEditor.class::cast).map(TextEditor::getEditor)
                            .collect(Collectors.toList()));

            List<VirtualFile> files = openedEditors.stream()
                    .map(e -> FileDocumentManager.getInstance().getFile(e.getDocument())).collect(Collectors.toList());

            IntStream.range(0, openedEditors.size()).forEach(i -> {
                if (added.contains(files.get(i).getExtension())) {
                    editorOpened(openedEditors.get(i));
                }
            });
        });
    }

    /**
     * Called when an editor is opened. Instantiates a LanguageServerWrapper if necessary, and adds the Editor to the Wrapper
     *
     * @param editor the editor
     */
    public static void editorOpened(Editor editor) {
        addExtensions();
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        Project project = editor.getProject();
        String rootPath = FileUtils.editorToProjectFolderPath(editor);
        String rootUri = FileUtils.pathToUri(rootPath);
        if (file != null) {
            ApplicationUtils.pool(() -> {
                String ext = file.getExtension();
                LOG.info("Opened " + file.getName());
                LanguageServerDefinition serverDefinition = extToServerDefinition.get(ext);
                if (serverDefinition != null) {
                    LanguageServerWrapper wrapper = extToLanguageWrapper.get(new MutablePair<>(ext, rootUri));
                    if (wrapper == null) {
                        LOG.info("Instantiating wrapper for " + ext + " : " + rootUri);
                        if (extToExtManager.get(ext) != null) {
                            wrapper = new LanguageServerWrapperImpl(serverDefinition, project,
                                    extToExtManager.get(ext));
                        } else {
                            wrapper = new LanguageServerWrapperImpl(serverDefinition, project);
                        }
                        String[] exts = serverDefinition.ext.split(LanguageServerDefinition.SPLIT_CHAR);
                        for (String exension : exts) {
                            extToLanguageWrapper.put(new ImmutablePair<>(exension, rootUri), wrapper);
                        }
                    } else {
                        LOG.info("Wrapper already existing for " + ext + " , " + rootUri);
                    }
                    LOG.info("Adding file " + file.getName());
                    wrapper.connect(editor);
                }
            });
        } else {
            LOG.warn("File for editor " + editor.getDocument().getText() + " is null");
        }
    }

    private static void addExtensions() {
        if (!loadedExtensions) {
            List<LanguageServerDefinition> extensions = LanguageServerDefinition.getInstance().getAllDefinitions()
                    .stream().filter(s -> !extToServerDefinition.keySet().contains(s.ext)).collect(Collectors.toList());
            LOG.info("Added serverDefinitions " + extensions + " from plugins");
            for (LanguageServerDefinition s : extensions) {
                extToServerDefinition.put(s.ext, s);
            }
            //Todo - Add this after fixing
            //   flattenExt();
            loadedExtensions = true;
        }
    }

    private static void flattenExt() {
        extToServerDefinition = extToServerDefinition.entrySet().stream().flatMap(p -> {
            String ext = p.getKey();
            LanguageServerDefinition sDef = p.getValue();
            String[] split = ext.split(SPLIT_CHAR);
            Stream<AbstractMap.SimpleEntry<String, LanguageServerDefinition>> stream = Stream.of(split)
                    .map(s -> new AbstractMap.SimpleEntry<>(s, sDef));
            return Stream.concat(stream, Stream.of(new AbstractMap.SimpleEntry<>(ext, sDef)));
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Called when an editor is closed. Notifies the LanguageServerWrapper if needed
     *
     * @param editor the editor.
     */
    public static void editorClosed(Editor editor) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        ApplicationUtils.pool(() -> {
            String ext = file.getExtension();
            LanguageServerWrapper serverWrapper = LanguageServerWrapperImpl.forEditor(editor);
            if (serverWrapper != null) {
                LOG.info("Disconnecting " + FileUtils.editorToURIString(editor));
                serverWrapper.disconnect(editor);
                extToLanguageWrapper.remove(ext);
            }
        });
    }

    public static void removeWrapper(LanguageServerWrapper wrapper) {
        if (wrapper.getProject() != null) {
            extToLanguageWrapper.remove(new MutablePair<>(wrapper.getServerDefinition().ext,
                    FileUtils.pathToUri(wrapper.getProject().getBasePath())));
        } else {
            LOG.error("No attached projects found for wrapper");

        }
    }

    // Todo - Implement workspace symbols support
}
