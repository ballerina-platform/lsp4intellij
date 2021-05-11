package org.wso2.lsp4intellij.client.languageserver.serverdefinition;

import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;

/**
 * TcpServerDefinition is a {@link LanguageServerDefinition} that connects to an LSP implementation over a TCP socket.
 */
@SuppressWarnings("unused")
public class TcpServerDefinition extends LanguageServerDefinition {
    private final TcpStreamConnectionProvider connectionProvider;

    /**
     * Creates a new TcpServerDefinition with no language IDs
     *
     * @param ext the extension this lsp definition is for
     * @param host the host of the language server
     * @param port the port of the language server
     */
    public TcpServerDefinition(String ext, String host, int port) {
        this(ext, host, port, Collections.emptyMap());
    }

    /**
     * Create a new TcpServerDefinition
     *
     * @param ext the extension this lsp definition is for
     * @param host the host of the language server
     * @param port the port of the language server
     * @param langIds The language server ids mapping to extension(s).
     */
    public TcpServerDefinition(String ext, String host, int port, Map<String, String> langIds) {
        this.languageIds = langIds;
        this.ext = ext;

        // We can do this upfront as we don't care about the working directory
        connectionProvider = new TcpStreamConnectionProvider(host, port);
    }

    @Override
    public StreamConnectionProvider createConnectionProvider(String workingDir) {
        return connectionProvider;
    }

    private static class TcpStreamConnectionProvider implements StreamConnectionProvider {
        private final String host;
        private final int port;

        private InputStream is;
        private OutputStream os;

        private Socket socket;

        private TcpStreamConnectionProvider(String host, int port) {
            this.host = host;
            this.port = port;
        }


        @Override
        public void start() throws IOException {
            socket = new Socket(host, port);

            // Do this here as these methods can throw IO exception, which we don't want to handle that in the getters
            is = socket.getInputStream();
            os = socket.getOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return is;
        }

        @Override
        public OutputStream getOutputStream() {
            return os;
        }

        @Override
        public void stop() {
            try {
                socket.close();
            } catch (IOException e) {
               // ignore... nothing we (anybody?) can do about this
            }
        }
    }
}
