package com.github.lsp4intellij.client.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface StreamConnectionProvider {

    void start() throws IOException;

    InputStream getInputStream();

    OutputStream getOutputStream();

    void stop();

}
