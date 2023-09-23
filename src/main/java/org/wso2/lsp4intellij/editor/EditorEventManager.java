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
package org.wso2.lsp4intellij.editor;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.Hint;
import com.intellij.util.SmartList;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.actions.LSPReferencesAction;
import org.wso2.lsp4intellij.client.languageserver.ServerOptions;
import org.wso2.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.contributors.fixes.LSPCodeActionFix;
import org.wso2.lsp4intellij.contributors.fixes.LSPCommandFix;
import org.wso2.lsp4intellij.contributors.icon.LSPIconProvider;
import org.wso2.lsp4intellij.contributors.psi.LSPPsiElement;
import org.wso2.lsp4intellij.contributors.rename.LSPRenameProcessor;
import org.wso2.lsp4intellij.listeners.LSPCaretListenerImpl;
import org.wso2.lsp4intellij.requests.HoverHandler;
import org.wso2.lsp4intellij.requests.Timeouts;
import org.wso2.lsp4intellij.requests.WorkspaceEditHandler;
import org.wso2.lsp4intellij.utils.DocumentUtils;
import org.wso2.lsp4intellij.utils.FileUtils;
import org.wso2.lsp4intellij.utils.GUIUtils;

import javax.swing.*;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.wso2.lsp4intellij.editor.EditorEventManagerBase.*;
import static org.wso2.lsp4intellij.requests.Timeout.getTimeout;
import static org.wso2.lsp4intellij.requests.Timeouts.*;
import static org.wso2.lsp4intellij.utils.ApplicationUtils.*;
import static org.wso2.lsp4intellij.utils.DocumentUtils.toEither;
import static org.wso2.lsp4intellij.utils.GUIUtils.createAndShowEditorHint;

/**
 * Class handling events related to an Editor (a Document)
 * <p>
 * editor              The "watched" editor
 * mouseListener       A listener for mouse clicks
 * mouseMotionListener A listener for mouse movement
 * documentListener    A listener for keystrokes
 * selectionListener   A listener for selection changes in the editor
 * wrapper.getRequestManager()      The related wrapper.getRequestManager(), connected to the right LanguageServer
 * serverOptions       The options of the server regarding completion, signatureHelp, syncKind, etc
 * wrapper             The corresponding LanguageServerWrapper
 */
public class EditorEventManager {

    public final DocumentEventManager documentEventManager;
    protected Logger LOG = Logger.getInstance(EditorEventManager.class);

    public Editor editor;
    public LanguageServerWrapper wrapper;
    private Project project;
    private TextDocumentIdentifier identifier;
    private EditorMouseListener mouseListener;
    private EditorMouseMotionListener mouseMotionListener;
    private LSPCaretListenerImpl caretListener;

    public List<String> completionTriggers;
    private List<String> signatureTriggers;
    private TextDocumentSyncKind syncKind;
    private volatile boolean needSave = false;
    private long predTime = -1L;
    private long ctrlTime = -1L;
    private boolean isOpen = false;

    private boolean mouseInEditor = true;
    private Hint currentHint;

    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private AnnotationHolder anonHolder;
    private List<Annotation> annotations = new ArrayList<>();
    private volatile boolean diagnosticSyncRequired = true;
    private volatile boolean codeActionSyncRequired = false;

    private static final long CTRL_THRESH = EditorSettingsExternalizable.getInstance().getTooltipsDelay() * 1000000;

    public static final String SNIPPET_PLACEHOLDER_REGEX = "(\\$\\{\\d+:?([^{^}]*)}|\\$\\d+)";

    //Todo - Revisit arguments order and add remaining listeners
    public EditorEventManager(Editor editor, DocumentListener documentListener, EditorMouseListener mouseListener,
                              EditorMouseMotionListener mouseMotionListener, LSPCaretListenerImpl caretListener,
                              RequestManager requestmanager, ServerOptions serverOptions, LanguageServerWrapper wrapper) {

        this.editor = editor;
        this.mouseListener = mouseListener;
        this.mouseMotionListener = mouseMotionListener;
        this.wrapper = wrapper;
        this.caretListener = caretListener;

        this.identifier = new TextDocumentIdentifier(FileUtils.editorToURIString(editor));
        this.syncKind = serverOptions.syncKind;

        this.completionTriggers = (serverOptions.completionOptions != null
                && serverOptions.completionOptions.getTriggerCharacters() != null) ?
                serverOptions.completionOptions.getTriggerCharacters() :
                new ArrayList<>();

        this.signatureTriggers = (serverOptions.signatureHelpOptions != null
                && serverOptions.signatureHelpOptions.getTriggerCharacters() != null) ?
                serverOptions.signatureHelpOptions.getTriggerCharacters() :
                new ArrayList<>();

        this.project = editor.getProject();

        EditorEventManagerBase.registerManager(this);

        this.currentHint = null;

        this.documentEventManager = DocumentEventManager.getOrCreateDocumentManager(editor.getDocument(), documentListener, syncKind, wrapper);
    }

    @SuppressWarnings("unused")
    public Project getProject() {
        return project;
    }

    @SuppressWarnings("unused")
    public RequestManager getRequestManager() {
        return wrapper.getRequestManager();
    }

    @SuppressWarnings("unused")
    public TextDocumentIdentifier getIdentifier() {
        return identifier;
    }

    /**
     * Calls onTypeFormatting or signatureHelp if the character typed was a trigger character
     *
     * @param c The character just typed
     */
    public void characterTyped(char c) {
        if (signatureTriggers.contains(Character.toString(c))) {
            signatureHelp();
        }
    }

    /**
     * Tells the manager that the mouse is in the editor
     */
    public void mouseEntered() {
        mouseInEditor = true;
    }

    /**
     * Tells the manager that the mouse is not in the editor
     */
    public void mouseExited() {
        mouseInEditor = false;
    }

    /**
     * Will show documentation if the mouse doesn't move for a given time (Hover)
     *
     * @param e the event
     */
    public void mouseMoved(EditorMouseEvent e) {

        if (e.getEditor() != editor) {
            LOG.error("Wrong editor for EditorEventManager");
            return;
        }

        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }
        Language language = psiFile.getLanguage();
        if ((!LanguageDocumentation.INSTANCE.allForLanguage(language).isEmpty() && !isSupportedLanguageFile(psiFile))
                || (!getIsCtrlDown() && !EditorSettingsExternalizable.getInstance().isShowQuickDocOnMouseOverElement())) {
            return;
        }

        long curTime = System.nanoTime();
        if (predTime == (-1L) || ctrlTime == (-1L)) {
            predTime = curTime;
            ctrlTime = curTime;
        } else {
            LogicalPosition lPos = getPos(e);
            if (lPos == null || getIsKeyPressed() && !getIsCtrlDown()) {
                return;
            }

            int offset = editor.logicalPositionToOffset(lPos);
            if ((getIsCtrlDown() || EditorSettingsExternalizable.getInstance().isShowQuickDocOnMouseOverElement())
                    && curTime - ctrlTime > CTRL_THRESH) {
                if (getCtrlRange() == null || !getCtrlRange().highlightContainsOffset(offset)) {
                    if (currentHint != null) {
                        currentHint.hide();
                    }
                    currentHint = null;
                    if (getCtrlRange() != null) {
                        getCtrlRange().dispose();
                    }
                    setCtrlRange(null);
                    pool(() -> requestAndShowDoc(lPos, e.getMouseEvent().getPoint()));
                } else if (getCtrlRange().definitionContainsOffset(offset)) {
                    createAndShowEditorHint(editor, "Click to show usages", editor.offsetToXY(offset));
                } else {
                    editor.getContentComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
                ctrlTime = curTime;
            }
            predTime = curTime;
        }
    }

    private boolean isSupportedLanguageFile(PsiFile file) {
        return file.getLanguage().isKindOf(PlainTextLanguage.INSTANCE)
                || FileUtils.isFileSupported(file.getVirtualFile());
    }

    /**
     * Called when the mouse is clicked
     * At the moment, is used by CTRL+click to see references / goto definition
     *
     * @param e The mouse event
     */
    public void mouseClicked(EditorMouseEvent e) {
        if (e.getEditor() != editor) {
            LOG.error("Wrong editor for EditorEventManager");
            return;
        }

        if (getIsCtrlDown()) {
            // If CTRL/CMD key is pressed, triggers goto definition/references and hover.
            try {
                trySourceNavigationAndHover(e);
            } catch (Exception err) {
                LOG.warn("Error occurred when trying source navigation", err);
            }
        }
    }

    private void createCtrlRange(Position logicalPos, Range range) {
        Location location = requestDefinition(logicalPos);
        if (location == null || location.getRange() == null || editor.isDisposed()) {
            return;
        }
        Range corRange;
        if (range == null) {
            corRange = new Range(logicalPos, logicalPos);
        } else {
            corRange = range;
        }
        int startOffset = DocumentUtils.LSPPosToOffset(editor, corRange.getStart());
        int endOffset = DocumentUtils.LSPPosToOffset(editor, corRange.getEnd());
        boolean isDefinition = DocumentUtils.LSPPosToOffset(editor, location.getRange().getStart()) == startOffset;

        CtrlRangeMarker ctrlRange = getCtrlRange();
        if (!editor.isDisposed()) {
            if (ctrlRange != null) {
                ctrlRange.dispose();
            }
            setCtrlRange(new CtrlRangeMarker(location, editor, !isDefinition ?
                    (editor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, HighlighterLayer.HYPERLINK,
                            editor.getColorsScheme().getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR),
                            HighlighterTargetArea.EXACT_RANGE)) : null));
        }
    }

    /**
     * Returns the position of the definition given a position in the editor
     *
     * @param position The position
     * @return The location of the definition
     */
    private Location requestDefinition(Position position) {
        DefinitionParams params = new DefinitionParams(identifier, position);
        CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> request =
                wrapper.getRequestManager().definition(params);

        if (request == null) {
            return null;
        }
        try {
            Either<List<? extends Location>, List<? extends LocationLink>> definition =
                    request.get(getTimeout(DEFINITION), TimeUnit.MILLISECONDS);
            wrapper.notifySuccess(Timeouts.DEFINITION);
            if (definition.isLeft() && !definition.getLeft().isEmpty()) {
                return definition.getLeft().get(0);
            } else if (definition.isRight() && !definition.getRight().isEmpty()) {
                var def = definition.getRight().get(0);
                return new Location(def.getTargetUri(), def.getTargetRange());
            }
        } catch (TimeoutException e) {
            LOG.warn(e);
            wrapper.notifyFailure(Timeouts.DEFINITION);
            return null;
        } catch (InterruptedException | JsonRpcException | ExecutionException e) {
            LOG.warn(e);
            wrapper.crashed(e);
            return null;
        }
        return null;
    }

    public Pair<List<PsiElement>, List<VirtualFile>> references(int offset) {
        return references(offset, false, false);
    }

    /**
     * Returns the references given the position of the word to search for
     * Must be called from main thread
     *
     * @param offset The offset in the editor
     * @return An array of PsiElement
     */
    public Pair<List<PsiElement>, List<VirtualFile>> references(int offset, boolean getOriginalElement, boolean close) {
        Position lspPos = DocumentUtils.offsetToLSPPos(editor, offset);
        TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(FileUtils.editorToURIString(editor));
        ReferenceParams params = new ReferenceParams(textDocumentIdentifier, lspPos, new ReferenceContext(getOriginalElement));
        params.setPosition(lspPos);
        params.setTextDocument(identifier);
        CompletableFuture<List<? extends Location>> request = wrapper.getRequestManager().references(params);
        if (request != null) {
            try {
                List<? extends Location> res = request.get(getTimeout(REFERENCES), TimeUnit.MILLISECONDS);
                wrapper.notifySuccess(Timeouts.REFERENCES);
                if (res != null && res.size() > 0) {
                    List<VirtualFile> openedEditors = new ArrayList<>();
                    List<PsiElement> elements = new ArrayList<>();
                    res.forEach(l -> {
                        Position start = l.getRange().getStart();
                        Position end = l.getRange().getEnd();
                        String uri = FileUtils.sanitizeURI(l.getUri());
                        VirtualFile file = FileUtils.virtualFileFromURI(uri);
                        Editor curEditor = FileUtils.editorFromUri(uri, project);
                        if (curEditor == null && file != null) {
                            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, start.getLine(), start.getCharacter());
                            curEditor = computableWriteAction(
                                    () -> FileEditorManager.getInstance(project).openTextEditor(descriptor, false));
                            openedEditors.add(file);
                        }
                        if (curEditor == null) {
                            LOG.warn("Error occurred in LSP references.");
                            return;
                        }
                        int logicalStart = DocumentUtils.LSPPosToOffset(curEditor, start);
                        int logicalEnd = DocumentUtils.LSPPosToOffset(curEditor, end);
                        String name = curEditor.getDocument().getText(new TextRange(logicalStart, logicalEnd));
                        elements.add(new LSPPsiElement(name, project, logicalStart, logicalEnd,
                                PsiDocumentManager.getInstance(project).getPsiFile(curEditor.getDocument())));
                    });
                    if (close) {
                        writeAction(
                                () -> openedEditors.forEach(f -> FileEditorManager.getInstance(project).closeFile(f)));
                        openedEditors.clear();
                    }
                    return new Pair<>(elements, openedEditors);
                } else {
                    return new Pair<>(null, null);
                }
            } catch (TimeoutException e) {
                LOG.warn(e);
                wrapper.notifyFailure(Timeouts.REFERENCES);
                return new Pair<>(null, null);
            } catch (InterruptedException | JsonRpcException | ExecutionException e) {
                LOG.warn(e);
                wrapper.crashed(e);
                return new Pair<>(null, null);
            }
        }
        return new Pair<>(null, null);
    }

    /**
     * @return The current diagnostics highlights
     */
    public synchronized List<Diagnostic> getDiagnostics() {
        this.diagnosticSyncRequired = false;
        return this.diagnostics;
    }

    /**
     * @return The current diagnostic annotations
     */
    public synchronized List<Annotation> getAnnotations() {
        this.codeActionSyncRequired = false;
        return this.annotations;
    }

    public synchronized void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    public synchronized void setAnonHolder(AnnotationHolder holder) {
        this.anonHolder = holder;
    }

    public synchronized boolean isDiagnosticSyncRequired() {
        return this.diagnosticSyncRequired;
    }

    public synchronized boolean isCodeActionSyncRequired() {
        return this.codeActionSyncRequired;
    }

    /**
     * Applies the diagnostics to the document
     *
     * @param diagnostics The diagnostics to apply from the server
     */
    public void diagnostics(List<Diagnostic> diagnostics) {

        // If both of the old diagnostics and the received diagnostics are empty, we can simply return without
        // re-triggering the annotator.
        if (editor.isDisposed() || (this.diagnostics.isEmpty() && diagnostics.isEmpty())) {
            return;
        }

        synchronized (this.diagnostics) {
            this.diagnostics.clear();
            this.diagnostics.addAll(diagnostics);
            diagnosticSyncRequired = true;
            // Triggers force full DaemonCodeAnalyzer execution.
            updateErrorAnnotations();
        }
    }

    /**
     * Retrieves the commands needed to apply a CodeAction
     *
     * @param offset The cursor position(offset) which should be evaluated for code action request.
     * @return The list of commands, or null if none are given / the request times out
     */
    @SuppressWarnings("WeakerAccess")
    public List<Either<Command, CodeAction>> codeAction(int offset) {
        CodeActionParams params = new CodeActionParams();
        params.setTextDocument(identifier);
        Range range = new Range(DocumentUtils.offsetToLSPPos(editor, offset),
                DocumentUtils.offsetToLSPPos(editor, offset));
        params.setRange(range);

        // Calculates the diagnostic context.
        List<Diagnostic> diagnosticContext = new ArrayList<>();
        synchronized (this.diagnostics) {
            diagnostics.forEach(diagnostic -> {
                int startOffset = DocumentUtils.LSPPosToOffset(editor, diagnostic.getRange().getStart());
                int endOffset = DocumentUtils.LSPPosToOffset(editor, diagnostic.getRange().getEnd());
                if (offset >= startOffset && offset <= endOffset) {
                    diagnosticContext.add(diagnostic);
                }
            });
        }

        CodeActionContext context = new CodeActionContext(diagnosticContext);
        params.setContext(context);
        CompletableFuture<List<Either<Command, CodeAction>>> future = wrapper.getRequestManager().codeAction(params);
        if (future != null) {
            try {
                List<Either<Command, CodeAction>> res = future.get(getTimeout(CODEACTION), TimeUnit.MILLISECONDS);
                wrapper.notifySuccess(CODEACTION);
                return res;
            } catch (TimeoutException e) {
                LOG.warn(e);
                wrapper.notifyFailure(CODEACTION);
                return null;
            } catch (InterruptedException | JsonRpcException | ExecutionException e) {
                LOG.warn(e);
                wrapper.crashed(e);
                return null;
            }
        }
        return null;
    }

    /**
     * Calls signatureHelp at the current editor caret position
     */
    @SuppressWarnings("WeakerAccess")
    public void signatureHelp() {
        if (editor.isDisposed()) {
            return;
        }
        LogicalPosition lPos = editor.getCaretModel().getCurrentCaret().getLogicalPosition();
        Point point = editor.logicalPositionToXY(lPos);
        SignatureHelpParams params = new SignatureHelpParams(identifier, DocumentUtils.logicalToLSPPos(lPos, editor));
        pool(() -> {
            CompletableFuture<SignatureHelp> future = wrapper.getRequestManager().signatureHelp(params);
            if (future == null) {
                return;
            }
            try {
                SignatureHelp signatureResp = future.get(getTimeout(SIGNATURE), TimeUnit.MILLISECONDS);
                wrapper.notifySuccess(Timeouts.SIGNATURE);
                if (signatureResp == null) {
                    return;
                }
                List<SignatureInformation> signatures = signatureResp.getSignatures();
                if (signatures == null || signatures.isEmpty()) {
                    return;
                }
                int activeSignatureIndex = signatureResp.getActiveSignature();
                int activeParameterIndex = signatureResp.getActiveParameter();

                String activeParameter = signatures.get(activeSignatureIndex).getParameters().size() > activeParameterIndex ?
                        extractLabel(signatures.get(activeSignatureIndex), signatures.get(activeSignatureIndex).getParameters().get(activeParameterIndex).getLabel()) : "";
                Either<String, MarkupContent> signatureDescription = signatures.get(activeSignatureIndex).getDocumentation();

                StringBuilder builder = new StringBuilder();
                builder.append("<html>");
                if (signatureDescription == null) {
                    builder.append("<b>").append(signatures.get(activeSignatureIndex).getLabel().
                            replace(" " + activeParameter, String.format("<font color=\"orange\"> %s</font>",
                                    activeParameter))).append("</b>");
                } else if (signatureDescription.isLeft()) {
                    // Todo - Add parameter Documentation
                    String descriptionLeft = signatureDescription.getLeft().replace(System.lineSeparator(), "<br />");
                    builder.append("<b>").append(signatures.get(activeSignatureIndex).getLabel()
                            .replace(" " + activeParameter, String.format("<font color=\"orange\"> %s</font>",
                                    activeParameter))).append("</b>");
                    builder.append("<div>").append(descriptionLeft).append("</div>");
                } else if (signatureDescription.isRight()) {
                    // Todo - Add marked content parsing
                    builder.append("<b>").append(signatures.get(activeSignatureIndex).getLabel()).append("</b>");
                }

                builder.append("</html>");
                invokeLater(() -> currentHint = createAndShowEditorHint(editor, builder.toString(), point, HintManager.UNDER, HintManager.HIDE_BY_OTHER_HINT));

            } catch (TimeoutException e) {
                LOG.warn(e);
                wrapper.notifyFailure(Timeouts.SIGNATURE);
            } catch (JsonRpcException | ExecutionException | InterruptedException e) {
                LOG.warn(e);
                wrapper.crashed(e);
            } catch (Exception e) {
                LOG.warn("Internal error occurred when processing signature help");
            }
        });
    }

    private String extractLabel(SignatureInformation signatureInformation, Either<String, Tuple.Two<Integer, Integer>> label) {
        if (label.isLeft()) {
            return label.getLeft();
        } else if (label.isRight()) {
            return signatureInformation.getLabel().substring(label.getRight().getFirst(), label.getRight().getSecond());
        } else {
            return "";
        }
    }

    /**
     * Reformat the whole document
     */
    public void reformat() {
        pool(() -> {
            if (editor.isDisposed()) {
                return;
            }
            DocumentFormattingParams params = new DocumentFormattingParams();
            params.setTextDocument(identifier);
            FormattingOptions options = new FormattingOptions();
            options.setTabSize(DocumentUtils.getTabSize(editor));
            options.setInsertSpaces(DocumentUtils.shouldUseSpaces(editor));
            params.setOptions(options);

            CompletableFuture<List<? extends TextEdit>> request = wrapper.getRequestManager().formatting(params);
            if (request == null) {
                return;
            }
            request.thenAccept(formatting -> {
                if (formatting != null) {
                    invokeLater(() -> applyEdit(toEither((List<TextEdit>) formatting), "Reformat document", false));
                }
            });
        });
    }

    /**
     * Reformat the text currently selected in the editor
     */
    public void reformatSelection() {
        pool(() -> {
            if (editor.isDisposed()) {
                return;
            }
            DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
            params.setTextDocument(identifier);
            SelectionModel selectionModel = editor.getSelectionModel();
            int start = computableReadAction(selectionModel::getSelectionStart);
            int end = computableReadAction(selectionModel::getSelectionEnd);
            Position startingPos = DocumentUtils.offsetToLSPPos(editor, start);
            Position endPos = DocumentUtils.offsetToLSPPos(editor, end);
            params.setRange(new Range(startingPos, endPos));
            // Todo - Make Formatting Options configurable
            FormattingOptions options = new FormattingOptions();
            options.setTabSize(DocumentUtils.getTabSize(editor));
            options.setInsertSpaces(DocumentUtils.shouldUseSpaces(editor));
            params.setOptions(options);

            CompletableFuture<List<? extends TextEdit>> request = wrapper.getRequestManager().rangeFormatting(params);
            if (request == null) {
                return;
            }
            request.thenAccept(formatting -> {
                if (formatting == null) {
                    return;
                }
                invokeLater(() -> {
                    if (!editor.isDisposed()) {
                        applyEdit(toEither((List<TextEdit>) formatting), "Reformat selection", false);
                    }
                });
            });
        });
    }

    public void rename(String renameTo) {
        rename(renameTo, editor.getCaretModel().getCurrentCaret().getOffset());
    }

    /**
     * Rename a symbol in the document
     *
     * @param renameTo The new name
     */
    public void rename(String renameTo, int offset) {
        pool(() -> {
            if (editor.isDisposed()) {
                return;
            }
            Position servPos = DocumentUtils.offsetToLSPPos(editor, offset);
            RenameParams params = new RenameParams(identifier, servPos, renameTo);
            CompletableFuture<WorkspaceEdit> request = wrapper.getRequestManager().rename(params);
            if (request != null) {
                request.thenAccept(res -> {
                    WorkspaceEditHandler
                            .applyEdit(res, "Rename to " + renameTo, new ArrayList<>(LSPRenameProcessor.getEditors()));
                    LSPRenameProcessor.clearEditors();
                });
            }
        });
    }

    /**
     * Immediately requests the server for documentation at the current editor position
     *
     * @param editor The editor
     */
    public void quickDoc(Editor editor) {
        if (editor == this.editor) {
            LogicalPosition caretPos = editor.getCaretModel().getLogicalPosition();
            Point pointPos = editor.logicalPositionToXY(caretPos);
            long currentTime = System.nanoTime();
            pool(() -> requestAndShowDoc(caretPos, pointPos));
            predTime = currentTime;
        } else {
            LOG.warn("Not same editor!");
        }
    }

    /**
     * Gets the hover request and shows it
     *
     * @param editorPos The editor position
     * @param point     The point at which to show the hint
     */
    private void requestAndShowDoc(LogicalPosition editorPos, Point point) {
        Position serverPos = computableReadAction(() -> DocumentUtils.logicalToLSPPos(editorPos, editor));
        CompletableFuture<Hover> request = wrapper.getRequestManager().hover(new HoverParams(identifier, serverPos));
        if (request == null) {
            return;
        }
        try {
            Hover hover = request.get(getTimeout(HOVER), TimeUnit.MILLISECONDS);
            wrapper.notifySuccess(Timeouts.HOVER);

            if (hover == null) {
                LOG.debug(String.format("Hover is null for file %s and pos (%d;%d)", identifier.getUri(),
                        serverPos.getLine(), serverPos.getCharacter()));
                return;
            }

            String string = HoverHandler.getHoverString(hover);
            if (StringUtils.isEmpty(string)) {
                LOG.warn(String.format("Hover string returned is empty for file %s and pos (%d;%d)",
                        identifier.getUri(), serverPos.getLine(), serverPos.getCharacter()));
                return;
            }

            if (getIsCtrlDown()) {
                invokeLater(() -> {
                    if (!editor.isDisposed()) {
                        currentHint = createAndShowEditorHint(editor, string, point, HintManager.HIDE_BY_OTHER_HINT);
                    }
                });
            } else {
                invokeLater(() -> {
                    if (!editor.isDisposed()) {
                        currentHint = createAndShowEditorHint(editor, string, point);
                    }
                });
            }
        } catch (TimeoutException e) {
            LOG.warn(e);
            wrapper.notifyFailure(Timeouts.HOVER);
        } catch (InterruptedException | JsonRpcException | ExecutionException e) {
            LOG.warn(e);
            wrapper.crashed(e);
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
        CompletableFuture<Either<List<CompletionItem>, CompletionList>> request = wrapper.getRequestManager()
                .completion(new CompletionParams(identifier, pos));
        if (request == null) {
            return lookupItems;
        }

        try {
            Either<List<CompletionItem>, CompletionList> res = request.get(getTimeout(COMPLETION), TimeUnit.MILLISECONDS);
            wrapper.notifySuccess(Timeouts.COMPLETION);
            if (res == null) {
                return lookupItems;
            }
            if (res.getLeft() != null) {
                for (CompletionItem item : res.getLeft()) {
                    LookupElement lookupElement = createLookupItem(item);
                    if (lookupElement != null) {
                        lookupItems.add(lookupElement);
                    }
                }
            } else if (res.getRight() != null) {
                for (CompletionItem item : res.getRight().getItems()) {
                    LookupElement lookupElement = createLookupItem(item);
                    if (lookupElement != null) {
                        lookupItems.add(lookupElement);
                    }
                }
            }
        } catch (TimeoutException | InterruptedException e) {
            LOG.warn(e);
            wrapper.notifyFailure(Timeouts.COMPLETION);
        } catch (JsonRpcException | ExecutionException e) {
            LOG.warn(e);
            wrapper.crashed(e);
        } finally {
            return lookupItems;
        }
    }

    /**
     * Creates a LookupElement given a CompletionItem
     *
     * @param item The CompletionItem
     * @return The corresponding LookupElement
     */
    @SuppressWarnings("WeakerAccess")
    public LookupElement createLookupItem(CompletionItem item) {
        Command command = item.getCommand();
        String detail = item.getDetail();
        String insertText = item.getInsertText();
        CompletionItemKind kind = item.getKind();
        String label = item.getLabel();
        Either<TextEdit, InsertReplaceEdit> textEditEither = item.getTextEdit();
        TextEdit textEdit = (textEditEither != null) ? textEditEither.getLeft() : null;
        InsertReplaceEdit insertReplaceEdit = (textEditEither != null) ? textEditEither.getRight() : null;
        List<TextEdit> addTextEdits = item.getAdditionalTextEdits();
        String presentableText = StringUtils.isNotEmpty(label) ? label : (insertText != null) ? insertText : "";
        String tailText = (detail != null) ? detail : "";
        LSPIconProvider iconProvider = GUIUtils.getIconProviderFor(wrapper.getServerDefinition());
        Icon icon = iconProvider.getCompletionIcon(kind);
        LookupElementBuilder lookupElementBuilder;

        String lookupString = null;
        if (textEdit != null) {
            lookupString = textEdit.getNewText();
        } else if (insertReplaceEdit != null) {
            lookupString = insertReplaceEdit.getNewText();
        } else if (StringUtils.isNotEmpty(insertText)) {
            lookupString = insertText;
        } else if (StringUtils.isNotEmpty(label)) {
            lookupString = label;
        }
        if (StringUtils.isEmpty(lookupString)) {
            return null;
        }
        // Fixes IDEA internal assertion failure in windows.
        lookupString = lookupString.replace(DocumentUtils.WIN_SEPARATOR, DocumentUtils.LINUX_SEPARATOR);

        lookupElementBuilder = LookupElementBuilder.create(getLookupStringWithoutPlaceholders(item, lookupString));

        lookupElementBuilder = addCompletionInsertHandlers(item, lookupElementBuilder, lookupString);

        if (kind == CompletionItemKind.Keyword) {
            lookupElementBuilder = lookupElementBuilder.withBoldness(true);
        }

        return lookupElementBuilder.withPresentableText(presentableText).withTypeText(tailText, true).withIcon(icon)
                .withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT);
    }

    private String getLookupStringWithoutPlaceholders(CompletionItem item, String lookupString) {
        if (item.getInsertTextFormat() == InsertTextFormat.Snippet) {
            return convertPlaceHolders(lookupString);
        } else {
            return lookupString;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public LookupElementBuilder addCompletionInsertHandlers(CompletionItem item, LookupElementBuilder builder, String lookupString) {

        String label = item.getLabel();
        Command command = item.getCommand();
        List<TextEdit> addTextEdits = item.getAdditionalTextEdits();
        InsertTextFormat format = item.getInsertTextFormat();

        if (addTextEdits != null) {
            builder = builder.withInsertHandler((InsertionContext context, LookupElement lookupElement) -> invokeLater(() -> {
                applyInitialTextEdit(item, context, lookupString);

                if (format == InsertTextFormat.Snippet) {
                    context.commitDocument();
                    prepareAndRunSnippet(lookupString);
                }

                context.commitDocument();
                applyEdit(Integer.MAX_VALUE, toEither(addTextEdits), "Completion : " + label, false, false);
                if (command != null) {
                    executeCommands(Collections.singletonList(command));
                }
            }));
        } else if (command != null) {
            builder = builder.withInsertHandler((InsertionContext context, LookupElement lookupElement) -> {
                applyInitialTextEdit(item, context, lookupString);

                if (format == InsertTextFormat.Snippet) {
                    context.commitDocument();
                    prepareAndRunSnippet(lookupString);
                }
                context.commitDocument();
                executeCommands(Collections.singletonList(command));
            });
        } else {
            builder = builder.withInsertHandler((InsertionContext context, LookupElement lookupElement) -> {
                applyInitialTextEdit(item, context, lookupString);

                if (format == InsertTextFormat.Snippet) {
                    context.commitDocument();
                    prepareAndRunSnippet(lookupString);
                }
            });
        }

        return builder;
    }

    private void applyInitialTextEdit(CompletionItem item, InsertionContext context, String lookupString) {
        if (item.getTextEdit() != null) {
            // remove intellij edit, server is controlling insertion
            writeAction(() -> {
                Runnable runnable = () -> this.editor.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());

                CommandProcessor.getInstance()
                        .executeCommand(project, runnable, "Removing Intellij Completion", "LSPPlugin", editor.getDocument());
            });
            context.commitDocument();

            if(item.getTextEdit().isLeft()) {
                item.getTextEdit().getLeft().setNewText(getLookupStringWithoutPlaceholders(item, lookupString));
            }

            applyEdit(Integer.MAX_VALUE, Collections.singletonList(item.getTextEdit()), "text edit", false, true);
        } else {
            // client handles insertion, determine a prefix (to allow completions of partially matching items)
            int prefixLength = getCompletionPrefixLength(context.getStartOffset());

            writeAction(() -> {
                Runnable runnable = () -> this.editor.getDocument().deleteString(context.getStartOffset() - prefixLength, context.getStartOffset());

                CommandProcessor.getInstance()
                        .executeCommand(project, runnable, "Removing Prefix", "LSPPlugin", editor.getDocument());
            });
            context.commitDocument();

        }
    }

    private int getCompletionPrefixLength(int offset) {
        return getCompletionPrefix(this.editor, offset).length();
    }

    @NotNull
    public String getCompletionPrefix(Editor editor, int offset) {
        List<String> delimiters = new ArrayList<>(this.completionTriggers);
        // add whitespace as delimiter, otherwise forced completion does not work
        delimiters.addAll(Arrays.asList(" \t\n\r".split("")));

        StringBuilder s = new StringBuilder();
        String documentText = editor.getDocument().getText();
        for (int i = 0; i < offset; i++) {
            char singleLetter = documentText.charAt(offset - i - 1);
            if (delimiters.contains(String.valueOf(singleLetter))) {
                return s.reverse().toString();
            }
            s.append(singleLetter);
        }
        return "";
    }

    @SuppressWarnings("WeakerAccess")
    public void prepareAndRunSnippet(String insertText) {

        List<SnippetVariable> variables = new ArrayList<>();
        // Extracts variables using placeholder REGEX pattern.
        Matcher varMatcher = Pattern.compile(SNIPPET_PLACEHOLDER_REGEX).matcher(insertText);
        while (varMatcher.find()) {
            variables.add(new SnippetVariable(varMatcher.group(), varMatcher.start(), varMatcher.end()));
        }

        variables.sort(Comparator.comparingInt(o -> o.startIndex));
        final String[] finalInsertText = {insertText};
        variables.forEach(var -> finalInsertText[0] = finalInsertText[0].replace(var.lspSnippetText, "$"));

        String[] splitInsertText = finalInsertText[0].split("\\$");
        finalInsertText[0] = String.join("", splitInsertText);

        TemplateImpl template = (TemplateImpl) TemplateManager.getInstance(getProject()).createTemplate(finalInsertText[0],
                "lsp4intellij");
        template.parseSegments();

        // prevent "smart" indent of next line...
        template.setToIndent(false);

        final int[] varIndex = {0};
        variables.forEach(var -> {
            template.addTextSegment(splitInsertText[varIndex[0]]);
            template.addVariable(varIndex[0] + "_" + var.variableValue, new TextExpression(var.variableValue),
                    new TextExpression(var.variableValue), true, false);
            varIndex[0]++;
        });
        // If the snippet text ends with a placeholder, there will be no string segment left to append after the last
        // variable.
        if (splitInsertText.length != variables.size()) {
            template.addTextSegment(splitInsertText[splitInsertText.length - 1]);
        }
        template.setInline(true);
        if (variables.size() > 0) {
            EditorModificationUtil.moveCaretRelatively(editor, -template.getTemplateText().length());
        }
        TemplateManager.getInstance(getProject()).startTemplate(editor, template);
    }

    private String convertPlaceHolders(String insertText) {
        return insertText.replaceAll(SNIPPET_PLACEHOLDER_REGEX, "");
    }

    /**
     * Returns the logical position given a mouse event
     *
     * @param e The event
     * @return The position (or null if out of bounds)
     */
    private LogicalPosition getPos(EditorMouseEvent e) {
        Point mousePos = e.getMouseEvent().getPoint();
        LogicalPosition editorPos = editor.xyToLogicalPosition(mousePos);
        Document doc = e.getEditor().getDocument();
        int maxLines = doc.getLineCount();
        if (editorPos.line >= maxLines) {
            return null;
        } else {
            int minY = doc.getLineStartOffset(editorPos.line) - (editorPos.line > 0 ?
                    doc.getLineEndOffset(editorPos.line - 1) : 0);
            int maxY = doc.getLineEndOffset(editorPos.line) - (editorPos.line > 0 ?
                    doc.getLineEndOffset(editorPos.line - 1) : 0);
            return (editorPos.column > minY && editorPos.column < maxY) ? editorPos : null;
        }
    }

    boolean applyEdit(List<Either<TextEdit, InsertReplaceEdit>> edits, String name, boolean setCaret) {
        return applyEdit(Integer.MAX_VALUE, edits, name, false, setCaret);
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
    boolean applyEdit(int version, List<Either<TextEdit, InsertReplaceEdit>> edits, String name, boolean closeAfter, boolean setCaret) {
        Runnable runnable = getEditsRunnable(version, edits, name, setCaret);
        writeAction(() -> {
            if (runnable != null) {
                CommandProcessor.getInstance()
                        .executeCommand(project, runnable, name, "LSPPlugin", editor.getDocument());
            }
            if (closeAfter) {
                PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
                if (file != null) {
                    FileEditorManager.getInstance(project).closeFile(file.getVirtualFile());
                }
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
    public Runnable getEditsRunnable(int version, List<Either<TextEdit, InsertReplaceEdit>> edits, String name, boolean setCaret) {
        if (version < this.documentEventManager.getDocumentVersion()) {
            LOG.warn(String.format("Edit version %d is older than current version %d", version, this.documentEventManager.getDocumentVersion()));
            return null;
        }
        if (edits == null) {
            LOG.warn("Received edits list is null.");
            return null;
        }
        if (editor.isDisposed()) {
            LOG.warn("Text edits couldn't be applied as the editor is already disposed.");
            return null;
        }
        Document document = editor.getDocument();
        if (!document.isWritable()) {
            LOG.warn("Document is not writable");
            return null;
        }

        return () -> {
            // Creates a sorted edit list based on the insertion position and the edits will be applied from the bottom
            // to the top of the document. Otherwise all the other edit ranges will be invalid after the very first edit,
            // since the document is changed.
            List<LSPTextEdit> lspEdits = new ArrayList<>();
            edits.forEach(edit -> {
                if(edit.isLeft()) {
                    String text = edit.getLeft().getNewText();
                    Range range = edit.getLeft().getRange();
                    if (range != null) {
                        int start = DocumentUtils.LSPPosToOffset(editor, range.getStart());
                        int end = DocumentUtils.LSPPosToOffset(editor, range.getEnd());
                        lspEdits.add(new LSPTextEdit(text, start, end));
                    }
                } else if(edit.isRight()) {
                    String text = edit.getRight().getNewText();
                    Range range = edit.getRight().getInsert();

                    if (range != null) {
                        int start = DocumentUtils.LSPPosToOffset(editor, range.getStart());
                        int end = DocumentUtils.LSPPosToOffset(editor, range.getEnd());
                        lspEdits.add(new LSPTextEdit(text, start, end));
                    } else if ((range = edit.getRight().getReplace()) != null) {
                        int start = DocumentUtils.LSPPosToOffset(editor, range.getStart());
                        int end = DocumentUtils.LSPPosToOffset(editor, range.getEnd());
                        lspEdits.add(new LSPTextEdit(text, start, end));
                    }
                }
            });

            // Sort according to the start offset, in descending order.
            Collections.sort(lspEdits);

            lspEdits.forEach(edit -> {
                String text = edit.getText();
                int start = edit.getStartOffset();
                int end = edit.getEndOffset();
                if (StringUtils.isEmpty(text)) {
                    document.deleteString(start, end);
                    if (setCaret) {
                        editor.getCaretModel().moveToOffset(start);
                    }
                } else {
                    text = text.replace(DocumentUtils.WIN_SEPARATOR, DocumentUtils.LINUX_SEPARATOR);
                    if (end >= 0) {
                        if (end - start <= 0) {
                            document.insertString(start, text);
                        } else {
                            document.replaceString(start, end, text);
                        }
                    } else if (start == 0) {
                        document.setText(text);
                    } else if (start > 0) {
                        document.insertString(start, text);
                    }
                    if (setCaret) {
                        editor.getCaretModel().moveToOffset(start + text.length());
                    }
                }
                saveDocument();
            });
        };
    }

    /**
     * Sends commands to execute to the server and applies the changes returned if the future returns a WorkspaceEdit
     *
     * @param commands The commands to execute
     */
    public void executeCommands(List<Command> commands) {
        pool(() -> {
            if (editor.isDisposed()) {
                return;
            }
            commands.stream().map(c -> {
                ExecuteCommandParams params = new ExecuteCommandParams();
                params.setArguments(c.getArguments());
                params.setCommand(c.getCommand());
                return wrapper.getRequestManager().executeCommand(params);
            }).filter(Objects::nonNull).forEach(f -> {
                try {
                    f.get(getTimeout(EXECUTE_COMMAND), TimeUnit.MILLISECONDS);
                    wrapper.notifySuccess(Timeouts.EXECUTE_COMMAND);
                } catch (TimeoutException te) {
                    LOG.warn(te);
                    wrapper.notifyFailure(Timeouts.EXECUTE_COMMAND);
                } catch (JsonRpcException | ExecutionException | InterruptedException e) {
                    LOG.warn(e);
                    wrapper.crashed(e);
                }
            });
        });
    }

    private void saveDocument() {
        FileDocumentManager.getInstance().saveDocument(editor.getDocument());
    }

    /**
     * Adds all the listeners
     */
    public void registerListeners() {
        editor.addEditorMouseListener(mouseListener);
        editor.addEditorMouseMotionListener(mouseMotionListener);
        editor.getCaretModel().addCaretListener(caretListener);
        // Todo - Implement
        // editor.getSelectionModel.addSelectionListener(selectionListener)
    }

    /**
     * Removes all the listeners
     */
    public void removeListeners() {
        editor.removeEditorMouseListener(mouseListener);
        editor.removeEditorMouseMotionListener(mouseMotionListener);
        editor.getCaretModel().removeCaretListener(caretListener);
        // Todo - Implement
        // editor.getSelectionModel.removeSelectionListener(selectionListener)
    }

    /**
     * Notifies the server that the corresponding document has been closed
     */
    public void documentClosed() {
        pool(() -> {
            if (this.isOpen) {
                isOpen = false;

                documentEventManager.documentClosed();
                EditorEventManagerBase.unregisterManager(this);
            } else {
                LOG.warn("Editor " + identifier.getUri() + " was already closed");
            }
        });
    }

    public void documentOpened() {
        pool(() -> {
            if (editor.isDisposed()) {
                return;
            }
            if (isOpen) {
                LOG.warn("Editor " + editor + " was already open");
            } else {
                documentEventManager.documentOpened();

                isOpen = true;
            }
        });
    }

    public void documentChanged(DocumentEvent event) {
        if (editor.isDisposed()) {
            return;
        }
        if (event.getDocument() == editor.getDocument()) {
            documentEventManager.documentChanged(event);
        } else {
            LOG.error("Wrong document for the EditorEventManager");
        }
    }

    /**
     * Notifies the server that the corresponding document has been saved
     */
    public void documentSaved() {
        pool(() -> {
            if (!editor.isDisposed()) {
                DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(identifier, editor.getDocument().getText());
                wrapper.getRequestManager().didSave(params);
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
                    wrapper.getRequestManager().willSave(new WillSaveTextDocumentParams(identifier, TextDocumentSaveReason.Manual));
                }
            });
    }

    /**
     * If the server supports willSaveWaitUntil, the LSPVetoer will check if  a save is needed
     * (needSave will basically alternate between true or false, so the document will always be saved)
     */
    private void willSaveWaitUntil() {
        if (wrapper.isWillSaveWaitUntil()) {
            pool(() -> {
                if (editor.isDisposed()) {
                    return;
                }
                WillSaveTextDocumentParams params = new WillSaveTextDocumentParams(identifier,
                        TextDocumentSaveReason.Manual);
                CompletableFuture<List<TextEdit>> future = wrapper.getRequestManager().willSaveWaitUntil(params);
                if (future != null) {
                    try {
                        List<TextEdit> edits = future.get(getTimeout(WILLSAVE), TimeUnit.MILLISECONDS);
                        wrapper.notifySuccess(Timeouts.WILLSAVE);
                        if (edits != null) {
                            invokeLater(() -> applyEdit(toEither(edits), "WaitUntil edits", false));
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
            });
        } else {
            LOG.error("Server doesn't support WillSaveWaitUntil");
            needSave = true;
            saveDocument();
        }
    }

    // Tries to go to definition / show usages based on the element which is
    private void trySourceNavigationAndHover(EditorMouseEvent e) {
        if (editor.isDisposed()) {
            return;
        }

        createCtrlRange(DocumentUtils.logicalToLSPPos(editor.xyToLogicalPosition(e.getMouseEvent().getPoint()), editor),
                null);
        final CtrlRangeMarker ctrlRange = getCtrlRange();

        if (ctrlRange == null) {
            int offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(e.getMouseEvent().getPoint()));
            LSPReferencesAction referencesAction = (LSPReferencesAction) ActionManager.getInstance()
                    .getAction("LSPFindUsages");
            if (referencesAction != null) {
                referencesAction.forManagerAndOffset(this, offset);
            }
            return;
        }

        Location loc = ctrlRange.location;
        invokeLater(() -> {
            if (editor.isDisposed()) {
                return;
            }

            int offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(e.getMouseEvent().getPoint()));
            String locUri = FileUtils.sanitizeURI(loc.getUri());

            if (identifier.getUri().equals(locUri)
                    && offset >= DocumentUtils.LSPPosToOffset(editor, loc.getRange().getStart())
                    && offset <= DocumentUtils.LSPPosToOffset(editor, loc.getRange().getEnd())) {
                LSPReferencesAction referencesAction = (LSPReferencesAction) ActionManager.getInstance()
                        .getAction("LSPFindUsages");
                if (referencesAction != null) {
                    referencesAction.forManagerAndOffset(this, offset);
                }
            } else {
                gotoLocation(loc);
            }

            ctrlRange.dispose();
            setCtrlRange(null);
        });
    }

    public void gotoLocation(Location loc) {
        VirtualFile file = null;
        try {
            file = VfsUtil.findFileByURL(new URL(loc.getUri()));
        } catch (MalformedURLException e1) {
            LOG.warn("Syntax Exception occurred for uri: " + loc.getUri());
        }
        if (file != null) {
            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
            VirtualFile finalFile = file;
            writeAction(() -> {
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
                Editor srcEditor = FileUtils.editorFromVirtualFile(finalFile, project);
                if (srcEditor != null) {
                    Position start = loc.getRange().getStart();
                    LogicalPosition logicalPos = DocumentUtils.getTabsAwarePosition(srcEditor, start);
                    if (logicalPos != null) {
                        srcEditor.getCaretModel().moveToLogicalPosition(logicalPos);
                        srcEditor.getScrollingModel().scrollTo(logicalPos, ScrollType.CENTER);
                    }
                }
            });
        } else {
            LOG.warn("Empty file for " + loc.getUri());
        }
    }

    public void requestAndShowCodeActions() {
        invokeLater(() -> {
            if (editor.isDisposed()) {
                return;
            }
            if (annotations == null) {
                annotations = new ArrayList<>();
            }

            // sends code action request.
            int caretPos = editor.getCaretModel().getCurrentCaret().getOffset();
            List<Either<Command, CodeAction>> codeActionResp = codeAction(caretPos);
            if (codeActionResp == null || codeActionResp.isEmpty()) {
                return;
            }

            codeActionResp.forEach(element -> {
                if (element == null) {
                    return;
                }
                if (element.isLeft()) {
                    Command command = element.getLeft();
                    annotations.forEach(annotation -> {
                        int start = annotation.getStartOffset();
                        int end = annotation.getEndOffset();
                        if (start <= caretPos && end >= caretPos) {
                            annotation.registerFix(new LSPCommandFix(FileUtils.editorToURIString(editor), command),
                                    new TextRange(start, end));
                            codeActionSyncRequired = true;
                        }
                    });
                } else if (element.isRight()) {
                    CodeAction codeAction = element.getRight();
                    List<Diagnostic> diagnosticContext = codeAction.getDiagnostics();
                    annotations.forEach(annotation -> {
                        int start = annotation.getStartOffset();
                        int end = annotation.getEndOffset();
                        if (start <= caretPos && end >= caretPos) {
                            annotation.registerFix(new LSPCodeActionFix(FileUtils.editorToURIString(editor),
                                    codeAction), new TextRange(start, end));
                            codeActionSyncRequired = true;
                        }
                    });

                    // If the code actions does not have a diagnostics context, creates an intention action for
                    // the current line.
                    if ((diagnosticContext == null || diagnosticContext.isEmpty()) && anonHolder != null && !codeActionSyncRequired) {
                        // Calculates text range of the current line.
                        int line = editor.getCaretModel().getCurrentCaret().getLogicalPosition().line;
                        int startOffset = editor.getDocument().getLineStartOffset(line);
                        int endOffset = editor.getDocument().getLineEndOffset(line);
                        TextRange range = new TextRange(startOffset, endOffset);

                        this.anonHolder
                                .newAnnotation(HighlightSeverity.INFORMATION, codeAction.getTitle())
                                .range(range)
                                .withFix(new LSPCodeActionFix(FileUtils.editorToURIString(editor), codeAction))
                                .create();

                        SmartList<Annotation> asList = (SmartList<Annotation>) this.anonHolder;
                        this.annotations.add(asList.get(asList.size() - 1));


                        diagnosticSyncRequired = true;
                    }
                }
            });
            // If code actions are updated, forcefully triggers the inspection tool.
            if (codeActionSyncRequired) {
                // double-delay the update to ensure that the code analyzer finishes.
                invokeLater(this::updateErrorAnnotations);
            }
        });
    }

    /**
     * Triggers force full DaemonCodeAnalyzer execution.
     */
    private void updateErrorAnnotations() {
        computableReadAction(() -> {
            final PsiFile file = PsiDocumentManager.getInstance(project)
                    .getCachedPsiFile(editor.getDocument());
            if (file == null) {
                return null;
            }
            LOG.debug("Triggering force full DaemonCodeAnalyzer execution.");
            DaemonCodeAnalyzer.getInstance(project).restart(file);
            return null;
        });
    }

    private static class LSPTextEdit implements Comparable<LSPTextEdit> {
        private String text;
        private int startOffset;
        private int endOffset;

        LSPTextEdit(String text, int start, int end) {
            this.text = text;
            this.startOffset = start;
            this.endOffset = end;
        }

        String getText() {
            return text;
        }

        int getStartOffset() {
            return startOffset;
        }

        int getEndOffset() {
            return endOffset;
        }

        @Override
        public int compareTo(@NotNull LSPTextEdit te) {
            return te.getStartOffset() - getStartOffset();
        }
    }

    static class SnippetVariable {
        String lspSnippetText;
        int startIndex;
        int endIndex;
        String variableValue;
        String intellijSnippetText;

        SnippetVariable(String text, int start, int end) {
            this.lspSnippetText = text;
            this.startIndex = start;
            this.endIndex = end;
            this.variableValue = getVariableValue(text);
        }

        private String getVariableValue(String lspVarSnippet) {
            if (lspVarSnippet.contains(":")) {
                return lspVarSnippet.substring(lspVarSnippet.indexOf(':') + 1, lspVarSnippet.lastIndexOf('}'));
            }
            return " ";
        }
    }
}
