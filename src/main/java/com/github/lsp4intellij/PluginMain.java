package com.github.lsp4intellij;

import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapperImpl;
import com.github.lsp4intellij.editor.listeners.EditorListener;
import com.github.lsp4intellij.editor.listeners.FileDocumentManagerListenerImpl;
import com.github.lsp4intellij.editor.listeners.VFSListener;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PluginMain implements ApplicationComponent {
    private static Logger LOG = Logger.getInstance(PluginMain.class);

    private static final String SPLIT_CHAR = ";";

    private static final Map<Pair<String, String>, LanguageServerWrapper> extToLanguageWrapper = new HashMap<>();
    private static Map<String, Set<LanguageServerWrapper>> projectToLanguageWrappers = new HashMap<>();
    private static Map<String, LanguageServerDefinition> extToServerDefinition = new HashMap<>();
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
                wrapper.stop();
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
                    synchronized (extToLanguageWrapper) {
                        LanguageServerWrapper wrapper = extToLanguageWrapper.get(new MutablePair<>(ext, rootUri));
                        if (wrapper == null) {
                            LOG.info("Instantiating wrapper for " + ext + " : " + rootUri);
                            wrapper = new LanguageServerWrapperImpl(serverDefinition, project);
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
        ApplicationUtils.pool(() -> {
            LanguageServerWrapper serverWrapper = LanguageServerWrapperImpl.forEditor(editor);
            if (serverWrapper != null) {
                LOG.info("Disconnecting " + FileUtils.editorToURIString(editor));
                serverWrapper.disconnect(editor);
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

    //    /**
    //     * Returns the corresponding workspaceSymbols given a name and a project
    //     *
    //     * @param name                   The name to search for
    //     * @param pattern                The pattern (unused)
    //     * @param project                The project in which to search
    //     * @param includeNonProjectItems Whether to search in libraries for example (unused)
    //     * @param onlyKind               Filter the results to only the kinds in the set (all by default)
    //     * @return An array of NavigationItem
    //     */
    //    public NavigationItem[]  workspaceSymbols(String name, String pattern, Project project, boolean includeNonProjectItems, Set<SymbolKind> onlyKind) {
    //        Set<LanguageServerWrapper> wrappers = projectToLanguageWrappers.get(
    //                FileUtils.pathToUri(project.getBasePath()));
    //        if(wrappers != null && !wrappers.isEmpty()){
    //            WorkspaceSymbolParams params = new WorkspaceSymbolParams(name);
    //            List<AbstractMap.SimpleEntry<LanguageServerWrapper, CompletableFuture<List<? extends SymbolInformation>>>>
    //                    servDefToReq = wrappers.stream()
    //                    .filter(w -> w.getStatus() == ServerStatus.STARTED && w.getRequestManager() != null)
    //                    .map(w -> new AbstractMap.SimpleEntry<>(w, w.getRequestManager().symbol(params)))
    //                    .filter(w -> w.getValue() != null)
    //                    .collect(Collectors.toList());
    //
    //
    //            if (!servDefToReq.contains(null)) {
    //                val servDefToSymb = servDefToReq.stream().map(w -> {
    //                try {
    //                    val symbols = w.getValue().get(Timeout.SYMBOLS_TIMEOUT, TimeUnit.MILLISECONDS);
    //                    w.getKey().notifyResult(Timeouts.SYMBOLS, true);
    //
    //                    (w.getKey(), if (symbols != null) symbols.
    //                            .filter(s -> if (onlyKind.isEmpty) true else onlyKind.contains(s.getKind)) else null)
    //
    //                    return new AbstractMap.SimpleEntry<>(w.getKey(), (symbols != null) ? );
    //
    //                } catch (TimeoutException e) {
    //                        LOG.warn(e);
    //                        w.getKey().notifyResult(Timeouts.SYMBOLS, false);
    //                        return null;
    //                }
    //          }
    //          ).filter(r => r._2 != null)
    //                servDefToSymb.flatMap(res => {
    //                        val definition = res._1
    //                        val symbols = res._2
    //                        symbols.map(symb => {
    //                                val start = symb.getLocation.getRange.getStart
    //                                val uri = FileUtils.URIToVFS(symb.getLocation.getUri)
    //                                val iconProvider = GUIUtils.getIconProviderFor(definition.getServerDefinition)
    //                                LSPNavigationItem(symb.getName, symb.getContainerName, project, uri, start.getLine, start.getCharacter, iconProvider.getSymbolIcon(symb.getKind))
    //                        })
    //          }).toArray.asInstanceOf[Array[NavigationItem]]
    //
    //            } else {
    //                return new NavigationItem[]{};
    //            }
    //        }else {
    //            LOG.info("No wrapper for project " + project.getBasePath());
    //            return new NavigationItem[]{};
    //        }
    //    }

    // Todo - Implement
    //    public void setForcedAssociations(Map<String[], String[]> associations) {
    //        Map<String[], String[]> scAssociations = associations;
    //        boolean isBadArrayLength = false;
    //        for (Map.Entry<String[], String[]> entry : scAssociations.entrySet()) {
    //            if (entry.getKey().length == 2) {
    //                LOG.warn("Unable to set forced associations : bad array length");
    //                isBadArrayLength = true;
    //                break;
    //            }
    //        }
    //        if (!isBadArrayLength){
    //            this.forcedAssociations = mutable.Map()++ scAssociations.map(mapping = > (mapping._1(0), mapping._1(1)) ->
    //            LanguageServerDefinition.fromArray(mapping._2))
    //
    //        }
    //    }
}
