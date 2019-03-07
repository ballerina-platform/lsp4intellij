# [Lsp4IntelliJ](#sp4intellij) - Language Server Protocol Support for Jetbrains Plugins

[![Build Status](https://travis-ci.com/NipunaRanasinghe/lsp4intellij.svg?branch=master)](https://travis-ci.com/NipunaRanasinghe/lsp4intellij)
[![](https://jitpack.io/v/NipunaRanasinghe/lsp4intellij.svg)](https://jitpack.io/#NipunaRanasinghe/lsp4intellij)

**Lsp4IntelliJ** provides language server support for IntelliJ IDEA and other Jetbrains IDEs.

This language client library is designed to be used with any IntelliJ plugin as its language server client, to get 
language server based features.
It also allows the plugin developers to use language specific language server protocol extensions via [JSON-RPC](https://en.wikipedia.org/wiki/JSON-RPC) 
protocol.


## Table of Contents
- [**How To Use**](#how-to-use)
- [**Features**](#features)
    - [Code Completion](#code-completion)
    - [Diagnostics](#diagnostics)
    - [Code Actions](#code-actions)
    - [Goto Definition](#go-to-definition)
- [**License**](#license)
- [**Inspiration**](#inspiration)
- [**Useful Links**](#useful-links)


## How To Use

Lets follow the below steps to use `Lsp4IntelliJ`  in your custom language plugin.

### 1. Adding `lsp4intellij` dependency in project build file
  
Refer **[jitpack/lsp4intellij](https://jitpack.io/#NipunaRanasinghe/lsp4intellij)** to learn how you can add 
  **Lsp4IntelliJ** as a dependency with different build tools, which are listed below.
  - gradle
  - maven
  - sbt
  - leiningen
  
  **Note** - Will be available soon in maven central as maven publishing process is WIP.

### 2. Adding language server definition

1. To add a language server, first you need to instantiate a concrete subclass of 
[LanguageServerDefinition](src/main/java/com/github/lsp4intellij/client/languageserver/serverdefinition/LanguageServerDefinition.java).

    You can use the following concrete class (more options will be added later):
    
    - **RawCommandServerDefinition(string fileExtension, string[] command)** 
        
        This definition simply runs the command 
        given.You can specify multiple extensions for one server by separating them with a comma. (e.g: "ts,js")
    
        Examples: 
        
        Ballerina Language Server 
        ```java
         new RawCommandServerDefinition("bal", new String[]{"path/to/launcher-script.sh"});
        ```
        
        BSL Language Server
        ```java
         String[] command = new String[]{"java","-jar","path/to/language-server.jar","--diagnosticLanguage"};
         new RawCommandServerDefinition("bsl,os",command);
        ```
        
    > Note that all these implementations will use server stdin/stdout to communicate.

2. Then to register any of the aforementioned options, you can implement a preloading activity in your plugin, as shown 
below.
(Refer [InteliJ Plugin initialization on startup](https://www.plugin-dev.com/intellij/general/plugin-initial-load/) 
to see other options you can use instead of implementing a preloading activity.)

    Example:
    
    ```java
    public class BallerinaPreloadingActivity extends PreloadingActivity {
        IntellijLanguageClient.addServerDefinition(new RawCommandServerDefinition("bal", new String[]{"path/to/launcher-script.sh"}));
    }
    ```

    With plugin.xml containing;
    
    ```
    <extensions defaultExtensionNs="com.intellij">
          <preloadingActivity implementation="io.ballerina.plugins.idea.preloading.BallerinaPreloadingActivity" id="io.ballerina.plugins.idea.preloading.BallerinaPreloadingActivity" />
      </extensions>
    ```


### 3. Adding configurations to plugin.xml 
   
  - `IntellijLanguageClient` must be added as an application component. 
       ```
        <application-components>
               <component>
                   <implementation-class>com.github.lsp4intellij.IntellijLanguageClient</implementation-class>
               </component>
           </application-components>
       ```
       
  - Add the following extensions to get the relevant features, as listed below.
  
    - Code completion
        ```
        <extensions defaultExtensionNs="com.intellij">
               <completion.contributor implementationClass="com.github.lsp4intellij.contributors.LSPCompletionContributor"
                                       id="LSPCompletionContributor" language="any"/>
           </extensions>
        ```
        
    - Diagnostics and code actions
        ```
         <extensions defaultExtensionNs="com.intellij">
                <inspectionToolProvider implementation="com.github.lsp4intellij.contributors.inspection.LSPInspectionProvider"
                                        id="LSPInspectionProvider"/>
            </extensions>
        ```
        
  - **Note** You won't need any additional configurations for the other features.
      
If you've connected to your language server successfully, you'll see a green icon at the bottom-right side of your 
IDE when opening a file which has a registered file extension, as shown below.

![](resources/images/lang-server-connect.gif)
   
You can also click on the icon to see the connected files and the timeouts.

![](resources/images/connected-and-timeouts.gif)
   

## Features 

#### Code Completion 
Press `CTRL+SPACE` to see the completion items list, which depends on your cursor position.(Code completion items 
will also auto pop-up based on your language server specific trigger characters.)

![](resources/images/lsp4intellij-completion.gif)


#### Diagnostics 
To see diagnostics (errors, warnings, etc), hover over them to see the message.

![](resources/images/lsp4intellij-dignostics.gif)

#### Code Actions
Hover to any diagnostic highlight and then you can view and apply related code actions using light bulb popup, as 
shown below.
![](resources/images/lsp4intellij-codeactions.gif)  


#### Go to Definition
You can use `CTRL+CLICK` on a symbol to navigate to its definition. (may need to try more than once at some contexts,
 to get it work.)
 
![](resources/images/lsp4intellij-gotodef.gif)


 **WIP** 
 - Code Formatting
 - Go to References / Find Usages
 - Hover Support
 - Signature Help
 
> **Note** - Currently tested with IntelliJ IDEA and
[Ballerina Language Server](https://github.com/ballerina-platform/ballerina-lang/tree/master/language-server). Need 
to be tested with other language servers and other Jetbrains IDEs as well.


## License

lsp4intellij code is distributed under [Apache license 2.0](LICENSE).

 
## Inspiration

`Lsp4IntelliJ` is heavily inspired by [intellij-lsp](https://github.com/gtache/intellij-lsp) plugin community. 
Credits should go to the original author for his awesome work.


# Useful Links

- [langserver.org](https://langserver.org/)
- [Language Server Protocol Specification](https://microsoft.github.io/language-server-protocol/specification)