package org.wso2.lsp4intellij.client.languageserver.serverdefinition;

import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

public interface ServerListener {

  public static final ServerListener NO_OP = new ServerListener() {
  };


  public default void initilize(@NotNull LanguageServer server, @NotNull InitializeResult result) {
  }
}
