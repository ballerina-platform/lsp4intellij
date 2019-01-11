package com.github.lsp4intellij.client.languageserver.serverdefinition;

import com.github.lsp4intellij.client.LanguageClientImpl;
import com.github.lsp4intellij.client.connection.StreamConnectionProvider;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
  * A trait representing a ServerDefinition
  */
public abstract class LanguageServerDefinition {

private Logger LOG = Logger.getInstance(LanguageServerDefinition.class);

  private Set<String> mappedExtensions = new HashSet<>();
  private Map<String, StreamConnectionProvider> streamConnectionProviders = new HashMap<>();

  //TODO: Verify logic here in scala
  /**
    * @return The extension that the language server manages
    */
  public String ext;

  //TODO: Verify logic here in scala
  /**
    * @return The id of the language server (same as extension)
    */
  public String id =  ext;

  /**
    * Starts a Language server for the given directory and returns a tuple (InputStream, OutputStream)
    *
    * @param workingDir The root directory
    * @return The input and output streams of the server
    */
  public Pair<InputStream, OutputStream> start(String workingDir) throws IOException {
    StreamConnectionProvider streamConnectionProvider = streamConnectionProviders.get(workingDir);
    if(streamConnectionProvider != null) {
      return new ImmutablePair<>(streamConnectionProvider.getInputStream(), streamConnectionProvider.getOutputStream());
    } else {
      streamConnectionProvider = createConnectionProvider(workingDir);
      streamConnectionProvider.start();
      streamConnectionProviders.put(workingDir, streamConnectionProvider);
      return new ImmutablePair<>(streamConnectionProvider.getInputStream(), streamConnectionProvider.getOutputStream());
    }
  }

  /**
    * Stops the Language server corresponding to the given working directory
    *
    * @param workingDir The root directory
    */
  public void stop(String workingDir){
    StreamConnectionProvider streamConnectionProvider = streamConnectionProviders.get(workingDir);
    if(streamConnectionProvider != null) {
      streamConnectionProvider.stop();
    } else {
      LOG.warn("No connection for workingDir " + workingDir + " and ext " + ext);
    }
  }

  /**
    * Adds a file extension for this LanguageServer
    *
    * @param ext the extension
    */
  public void addMappedExtension(String ext) {
    mappedExtensions.add(ext);
  }

  /**
    * Removes a file extension for this LanguageServer
    *
    * @param ext the extension
    */
  public void removeMappedExtension(String ext) {
    mappedExtensions.remove(ext);
  }

  /**
    * @return the extensions linked to this LanguageServer
    */
  public Set<String> getMappedExtensions() {
    return new HashSet<>(mappedExtensions);
  }

  /**
    * @return the LanguageClient for this LanguageServer
    */
  public LanguageClientImpl createLanguageClient() {
     return new LanguageClientImpl();
  }

  public Object getInitializationOptions(URI uri) {
    return null;
  }

  @Override
  public String toString(){
    return "ServerDefinition for " + ext;
  }

  /**
    * @return The array corresponding to the server definition
    */
  public String[] toArray(){
    throw new UnsupportedOperationException();
  }

  /**
    * Creates a StreamConnectionProvider given the working directory
    *
    * @param workingDir The root directory
    * @return The stream connection provider
    */
  abstract StreamConnectionProvider createConnectionProvider(String workingDir);
}
