package com.github.lsp4intellij.editor;

import com.github.lsp4intellij.client.languageserver.ServerOptions;
import com.github.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import com.github.lsp4intellij.contributors.icon.LSPIconProvider;
import com.github.lsp4intellij.requests.Timeouts;
import com.github.lsp4intellij.requests.WorkspaceEditHandler;
import com.github.lsp4intellij.utils.DocumentUtils;
import com.github.lsp4intellij.utils.FileUtils;
import com.github.lsp4intellij.utils.GUIUtils;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSaveReason;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.*;

import static com.github.lsp4intellij.requests.Timeout.COMPLETION_TIMEOUT;
import static com.github.lsp4intellij.requests.Timeout.EXECUTE_COMMAND_TIMEOUT;
import static com.github.lsp4intellij.requests.Timeout.WILLSAVE_TIMEOUT;
import static com.github.lsp4intellij.utils.ApplicationUtils.invokeLater;
import static com.github.lsp4intellij.utils.ApplicationUtils.pool;
import static com.github.lsp4intellij.utils.ApplicationUtils.writeAction;

/**
 * Class handling events related to an Editor (a Document)
 * <p>
 * editor              The "watched" editor
 * mouseListener       A listener for mouse clicks
 * mouseMotionListener A listener for mouse movement
 * documentListener    A listener for keystrokes
 * selectionListener   A listener for selection changes in the editor
 * requestManager      The related RequestManager, connected to the right LanguageServer
 * serverOptions       The options of the server regarding completion, signatureHelp, syncKind, etc
 * wrapper             The corresponding LanguageServerWrapper
 */
public class EditorEventManager {

    protected Logger LOG = Logger.getInstance(EditorEventManager.class);

    public Editor editor;
    protected DocumentListener documentListener;
    protected RequestManager requestManager;
    protected ServerOptions serverOptions;
    public LanguageServerWrapper wrapper;
    protected TextDocumentIdentifier identifier;
    protected DidChangeTextDocumentParams changesParams;
    protected TextDocumentSyncKind syncKind;
    protected List<String> completionTriggers;
    protected Project project;
    volatile boolean needSave = false;
    private int version = -1;
    private boolean isOpen = false;

    //Todo - Revisit and add remaining listeners
    public EditorEventManager(Editor editor, DocumentListener documentListener, RequestManager requestManager,
            ServerOptions serverOptions, LanguageServerWrapper wrapper) {
        this.editor = editor;
        this.documentListener = documentListener;
        this.requestManager = requestManager;
        this.serverOptions = serverOptions;
        this.wrapper = wrapper;

        this.identifier = new TextDocumentIdentifier(FileUtils.editorToURIString(editor));
        this.changesParams = new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(),
                Collections.singletonList(new TextDocumentContentChangeEvent()));
        this.syncKind = serverOptions.syncKind;

        completionTriggers = (serverOptions.completionOptions != null
                && serverOptions.completionOptions.getTriggerCharacters() != null) ?
                serverOptions.completionOptions.getTriggerCharacters() :
                new ArrayList<>();

        this.project = editor.getProject();

        EditorEventManagerBase.uriToManager.put(FileUtils.editorToURIString(editor), this);
        EditorEventManagerBase.editorToManager.put(editor, this);
        changesParams.getTextDocument().setUri(identifier.getUri());
    }

    /**
     * Calls onTypeFormatting or signatureHelp if the character typed was a trigger character
     *
     * @param c The character just typed
     */
    public void characterTyped(char c) {
        if (completionTriggers.contains(c)) {
            completion(DocumentUtils.offsetToLSPPos(editor, editor.getCaretModel().getCurrentCaret().getOffset()));
        }
    }

    /**
     * Returns the completion suggestions given a position
     *
     * @param pos The LSP position
     * @return The suggestions
     */
    public Iterable<? extends LookupElement> completion(Position pos) {
        List<LookupElement> lookupItems = new ArrayList<>();
        CompletableFuture<Either<List<CompletionItem>, CompletionList>> request = requestManager
                .completion(new CompletionParams(identifier, pos));
        if (request != null) {
            try {
                Either<List<CompletionItem>, CompletionList> res = request
                        .get(COMPLETION_TIMEOUT, TimeUnit.MILLISECONDS);
                wrapper.notifySuccess(Timeouts.COMPLETION);
                if (res != null) {
                    if (res.getLeft() != null) {
                        for (CompletionItem item : res.getLeft()) {
                            lookupItems.add(createLookupItem(item));
                        }
                    } else if (res.getRight() != null) {
                        for (CompletionItem item : res.getRight().getItems()) {
                            lookupItems.add(createLookupItem(item));
                        }
                    }
                }
            } catch (TimeoutException e) {
                LOG.warn(e);
                wrapper.notifyFailure(Timeouts.COMPLETION);
            } catch (JsonRpcException | ExecutionException | InterruptedException e) {
                LOG.warn(e);
                wrapper.crashed(e);
            }
        }
        return lookupItems;
    }

    /**
     * Creates a LookupElement given a CompletionItem
     *
     * @param item The CompletionItem
     * @return The corresponding LookupElement
     */
    private LookupElement createLookupItem(CompletionItem item) {
        Command command = item.getCommand();
        Object data = item.getData();
        String detail = item.getDetail();
        Either<String, MarkupContent> doc = item.getDocumentation();
        String filterText = item.getFilterText();
        String insertText = item.getInsertText();
        InsertTextFormat insertFormat = item.getInsertTextFormat();
        CompletionItemKind kind = item.getKind();
        String label = item.getLabel();
        TextEdit textEdit = item.getTextEdit();
        List<TextEdit> addTextEdits = item.getAdditionalTextEdits();
        String sortText = item.getSortText();
        String presentableText = (label != null && label != "") ? label : (insertText != null) ? insertText : "";
        String tailText = (detail != null) ? detail : "";
        LSPIconProvider iconProvider = GUIUtils.getIconProviderFor(wrapper.getServerDefinition());
        Icon icon = iconProvider.getCompletionIcon(kind);
        LookupElementBuilder lookupElementBuilder = null;

        if (textEdit != null) {
            if (addTextEdits != null) {
                addTextEdits.add(textEdit);
                lookupElementBuilder = LookupElementBuilder.create("")
                        .withInsertHandler((InsertionContext context, LookupElement lookupElement) -> {
                            context.commitDocument();
                            invokeLater(() -> {
                                applyEdit(addTextEdits, "Completion : " + label);
                                if (command != null) {
                                    executeCommands(command);
                                }
                            });
                        });
            } else {
                lookupElementBuilder = LookupElementBuilder.create("")
                        .withInsertHandler((InsertionContext context, LookupElement lookupElement) -> {
                            context.commitDocument();
                            invokeLater(() -> {
                                applyEdit(textEdit, "Completion : " + label);
                                if (command != null) {
                                    executeCommands(command);
                                }
                            });
                        });
            }
        } else if (addTextEdits != null) {
            lookupElementBuilder = LookupElementBuilder.create("")
                    .withInsertHandler((InsertionContext context, LookupElement lookupElement) -> {
                        context.commitDocument();
                        invokeLater(() -> {
                            applyEdit(addTextEdits, "Completion : " + label);
                            if (command != null) {
                                executeCommands(command);
                            }
                        });
                    });
        } else {
            lookupElementBuilder = LookupElementBuilder
                    .create((insertText != null && insertText != "") ? insertText : label);
            if (command != null)
                lookupElementBuilder = lookupElementBuilder
                        .withInsertHandler((InsertionContext context, LookupElement lookupElement) -> {
                            context.commitDocument();
                            invokeLater(() -> {
                                executeCommands(command);
                            });
                        });
        }
        if (kind == CompletionItemKind.Keyword)
            lookupElementBuilder = lookupElementBuilder.withBoldness(true);
        return lookupElementBuilder.withPresentableText(presentableText).withTailText(tailText, true).withIcon(icon)
                .withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT);
    }

    boolean applyEdit(TextEdit edit, String name) {
        List<TextEdit> textEdits = new ArrayList<>();
        textEdits.add(edit);
        return applyEdit(textEdits, name);
    }

    boolean applyEdit(List<TextEdit> edits, String name) {
        return applyEdit(Integer.MAX_VALUE, edits, name, false);
    }

    /**
     * Applies the given edits to the document
     *
     * @param version    The version of the edits (will be discarded if older than current version)
     * @param edits      The edits to apply
     * @param name       The name of the edits (Rename, for example)
     * @param closeAfter will close the file after edits if set to true
     * @return True if the edits were applied, false otherwise
     */
    boolean applyEdit(int version, List<TextEdit> edits, String name, boolean closeAfter) {
        Runnable runnable = getEditsRunnable(version, edits, name);
        writeAction(() -> {
            if (runnable != null) {
                CommandProcessor.getInstance()
                        .executeCommand(project, runnable, name, "LSPPlugin", editor.getDocument());
            }
            if (closeAfter) {
                FileEditorManager.getInstance(project).closeFile(
                        PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()).getVirtualFile());
            }
        });
        return runnable != null;
    }

    /**
     * Returns a Runnable used to apply the given edits and save the document
     * Used by WorkspaceEditHandler (allows to revert a rename for example)
     *
     * @param version The edit version
     * @param edits   The edits
     * @param name    The name of the edit
     * @return The runnable
     */
    public Runnable getEditsRunnable(int version, Iterable<TextEdit> edits, String name) {
        if (version >= this.version) {
            Document document = editor.getDocument();
            if (document.isWritable()) {
                return () -> {
                    edits.forEach(edit -> {
                        String text = edit.getNewText();
                        Range range = edit.getRange();
                        int start = DocumentUtils.LSPPosToOffset(editor, range.getStart());
                        int end = DocumentUtils.LSPPosToOffset(editor, range.getEnd());
                        if (text == null || text.equals("")) {
                            document.deleteString(start, end);
                        } else if (end - start <= 0) {
                            document.insertString(start, text);
                        } else {
                            document.replaceString(start, end, text);
                        }
                    });
                    saveDocument();
                };
            } else {
                LOG.warn("Document is not writable");
                return null;
            }
        } else {
            LOG.warn("Edit version " + version + " is older than current version " + this.version);
            return null;
        }
    }

    void executeCommands(Command command) {
        List<Command> commands = new ArrayList<>();
        commands.add(command);
        executeCommands(commands);
    }

    /**
     * Sends commands to execute to the server and applies the changes returned if the future returns a WorkspaceEdit
     *
     * @param commands The commands to execute
     */
    void executeCommands(List<Command> commands) {
        pool(() -> {
            if (!editor.isDisposed()) {
                commands.stream().map(c -> {
                    ExecuteCommandParams params = new ExecuteCommandParams();
                    params.setArguments(c.getArguments());
                    params.setCommand(c.getCommand());
                    return requestManager.executeCommand(params);
                }).forEach(f -> {
                    if (f != null) {
                        try {
                            Object ret = f.get(EXECUTE_COMMAND_TIMEOUT, TimeUnit.MILLISECONDS);
                            wrapper.notifySuccess(Timeouts.EXECUTE_COMMAND);
                            if (ret instanceof WorkspaceEdit) {
                                WorkspaceEditHandler.applyEdit((WorkspaceEdit) ret, "Execute command");
                            }
                        } catch (TimeoutException te) {
                            LOG.warn(te);
                            wrapper.notifyFailure(Timeouts.EXECUTE_COMMAND);
                        } catch (JsonRpcException | ExecutionException | InterruptedException e) {
                            LOG.warn(e);
                            wrapper.crashed(e);
                        }
                    }
                });
            }
        });
    }

    private void saveDocument() {
        invokeLater(() -> writeAction(() -> FileDocumentManager.getInstance().saveDocument(editor.getDocument())));
    }

    /**
     * Adds all the listeners
     */
    public void registerListeners() {
        editor.getDocument().addDocumentListener(documentListener);
        // Todo - Implement
        // editor.addEditorMouseListener(mouseListener)
        // editor.addEditorMouseMotionListener(mouseMotionListener)
        // editor.getSelectionModel.addSelectionListener(selectionListener)
    }

    /**
     * Removes all the listeners
     */
    public void removeListeners() {
        editor.getDocument().removeDocumentListener(documentListener);
        // Todo - Implement
        // editor.removeEditorMouseMotionListener(mouseMotionListener)
        // editor.removeEditorMouseListener(mouseListener)
        // editor.getSelectionModel.removeSelectionListener(selectionListener)
    }

    /**
     * Notifies the server that the corresponding document has been closed
     */
    public void documentClosed() {
        pool(() -> {
            if (this.isOpen) {
                requestManager.didClose(new DidCloseTextDocumentParams(identifier));
                isOpen = false;
                EditorEventManagerBase.editorToManager.remove(editor);
                EditorEventManagerBase.uriToManager.remove(FileUtils.editorToURIString(editor));
            } else {
                LOG.warn("Editor " + identifier.getUri() + " was already closed");
            }
        });
    }

    public void documentOpened() {
        pool(() -> {
            if (!editor.isDisposed()) {
                if (isOpen) {
                    LOG.warn("Editor " + editor + " was already open");
                } else {
                    requestManager.didOpen(new DidOpenTextDocumentParams(
                            new TextDocumentItem(identifier.getUri(), wrapper.serverDefinition.id, version++,
                                    editor.getDocument().getText())));
                    isOpen = true;
                }
            }
        });
    }

    public void documentChanged(DocumentEvent event) {
        if (!editor.isDisposed()) {
            if (event.getDocument() == editor.getDocument()) {
                //Todo - restore when adding hover support
                // long predTime = System.nanoTime(); //So that there are no hover events while typing
                changesParams.getTextDocument().setVersion(version++);

                if (syncKind == TextDocumentSyncKind.Incremental) {
                    TextDocumentContentChangeEvent changeEvent = changesParams.getContentChanges().get(0);
                    CharSequence newText = event.getNewFragment();
                    int offset = event.getOffset();
                    int newTextLength = event.getNewLength();
                    Position lspPosition = DocumentUtils.offsetToLSPPos(editor, offset);
                    int startLine = lspPosition.getLine();
                    int startColumn = lspPosition.getCharacter();
                    CharSequence oldText = event.getOldFragment();

                    //if text was deleted/replaced, calculate the end position of inserted/deleted text
                    int endLine, endColumn = -1;
                    if (oldText.length() > 0) {
                        endLine = startLine + StringUtil.countNewLines(oldText);
                        String[] oldLines = oldText.toString().split("\n");
                        int oldTextLength = oldLines.length == 0 ? 0 : oldLines[oldLines.length - 1].length();
                        endColumn = oldLines.length == 1 ? startColumn + oldTextLength : oldTextLength;
                    } else { //if insert or no text change, the end position is the same
                        endLine = startLine;
                        endColumn = startColumn;
                    }
                    Range range = new Range(new Position(startLine, startColumn), new Position(endLine, endColumn));
                    changeEvent.setRange(range);
                    changeEvent.setRangeLength(newTextLength);
                    changeEvent.setText(newText.toString());
                } else if (syncKind == TextDocumentSyncKind.Full) {
                    changesParams.getContentChanges().get(0).setText(editor.getDocument().getText());
                }
                requestManager.didChange(changesParams);
            } else {
                LOG.error("Wrong document for the EditorEventManager");
            }
        }
    }

    /**
     * Notifies the server that the corresponding document has been saved
     */
    public void documentSaved() {
        pool(() -> {
            if (!editor.isDisposed()) {
                DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(identifier,
                        editor.getDocument().getText());
                requestManager.didSave(params);
            }
        });
    }

    /**
     * Indicates that the document will be saved
     */
    //TODO Manual
    public void willSave() {
        if (wrapper.isWillSaveWaitUntil() && !needSave) {
            willSaveWaitUntil();
        } else
            pool(() -> {
                if (!editor.isDisposed()) {
                    requestManager.willSave(new WillSaveTextDocumentParams(identifier, TextDocumentSaveReason.Manual));
                }
            });
    }

    /**
     * If the server supports willSaveWaitUntil, the LSPVetoer will check if  a save is needed
     * (needSave will basically alterate between true or false, so the document will always be saved)
     */
    private void willSaveWaitUntil() {
        if (wrapper.isWillSaveWaitUntil()) {
            pool(() -> {
                if (!editor.isDisposed()) {
                    WillSaveTextDocumentParams params = new WillSaveTextDocumentParams(identifier,
                            TextDocumentSaveReason.Manual);
                    CompletableFuture<List<TextEdit>> future = requestManager.willSaveWaitUntil(params);
                    if (future != null) {
                        try {
                            List<TextEdit> edits = future.get(WILLSAVE_TIMEOUT, TimeUnit.MILLISECONDS);
                            wrapper.notifySuccess(Timeouts.WILLSAVE);
                            if (edits != null) {
                                invokeLater(() -> applyEdit(edits, "WaitUntil edits"));
                            }
                        } catch (TimeoutException e) {
                            LOG.warn(e);
                            wrapper.notifyFailure(Timeouts.WILLSAVE);
                        } catch (JsonRpcException | ExecutionException | InterruptedException e) {
                            LOG.warn(e);
                            wrapper.crashed(e);
                        } finally {
                            needSave = true;
                            saveDocument();
                        }
                    } else {
                        needSave = true;
                        saveDocument();
                    }
                }
            });
        } else {
            LOG.error("Server doesn't support WillSaveWaitUntil");
            needSave = true;
            saveDocument();
        }
    }
}
