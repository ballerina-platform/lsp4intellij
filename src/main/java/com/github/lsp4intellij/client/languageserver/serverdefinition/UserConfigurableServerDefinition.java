package com.github.lsp4intellij.client.languageserver.serverdefinition;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Arrays;

/**
  * A UserConfigurableServerDefinition is a server definition which can be manually entered by the user in the IntellliJ settings
  */
public abstract class UserConfigurableServerDefinition extends LanguageServerDefinition {
  private Logger LOG = Logger.getInstance(UserConfigurableServerDefinition.class);
  /**
   * @return the type of the server definition
   */
  protected String typ;
  /**
   * @return the type of the server definition in a nicer way
   */
  protected String presentableTyp;

  public UserConfigurableServerDefinition() {
    this.typ  = "userConfigurable";
    this.presentableTyp = "Configurable";
  }

  public static String head(String[] arr){
    return arr[0];
  }

  public static String[] tail(String[] arr){
    return Arrays.copyOfRange(arr, 1, arr.length - 1);
  }
}
