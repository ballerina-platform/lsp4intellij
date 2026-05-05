# Developer Guide

This guide walks through integrating `lsp4intellij` into your custom JetBrains plugin in detail. For the minimum viable integration, see the [Quick Start](../README.md#quick-start) in the README.

## Table of Contents

<!-- The TOC below is auto-generated. To regenerate, run: npx markdown-toc -i docs/developer-guide.md --maxdepth 4 -->

<!-- toc -->

- [1. Add the `lsp4intellij` dependency](#1-add-the-lsp4intellij-dependency)
- [2. Add a `plugin.xml` file](#2-add-a-pluginxml-file)
- [3. Configure a preloading activity](#3-configure-a-preloading-activity)
- [4. Confirm the language server connection](#4-confirm-the-language-server-connection)
- [Alternative ways to connect to a language server](#alternative-ways-to-connect-to-a-language-server)
  * [RawCommandServerDefinition](#rawcommandserverdefinition)
  * [ProcessBuilderServerDefinition](#processbuilderserverdefinition)
- [Custom initialization parameters](#custom-initialization-parameters)
- [Configuration](#configuration)
  * [Timeouts](#timeouts)
- [Appendix: Legacy components-based setup](#appendix-legacy-components-based-setup)

<!-- tocstop -->

---

## 1. Add the `lsp4intellij` dependency

Include `lsp4intellij` in your project's build file. Instructions for popular build tools are available at [jitpack/lsp4intellij](https://jitpack.io/#ballerina-platform/lsp4intellij).

Supported build tools:
- Gradle
- Maven
- SBT

> **Info:** Maven Central publishing is a work in progress. Support for Maven Central will be available soon.

## 2. Add a `plugin.xml` file

Define the required configurations in your `plugin.xml` file. Copy the example from [resources/plugin.xml.example](../resources/plugin.xml.example), place it under `src/resources/META-INF`, and adjust it as needed.

## 3. Configure a preloading activity

Add a preloading activity to initialize and configure LSP support:

```java
public class BallerinaPreloadingActivity extends PreloadingActivity {
    @Override
    public void preload(ProgressIndicator indicator) {
        IntellijLanguageClient.addServerDefinition(new RawCommandServerDefinition("bal", new String[]{"path/to/launcher-script.sh"}));
    }
}
```

Update your `plugin.xml` to include the preloading activity:

```xml
<extensions defaultExtensionNs="com.intellij">
    <preloadingActivity implementation="io.ballerina.plugins.idea.preloading.BallerinaPreloadingActivity"
                        id="io.ballerina.plugins.idea.preloading.BallerinaPreloadingActivity" />
</extensions>
```

> **Tip:** For other options instead of a preloading activity, see [IntelliJ Plugin initialization on startup](https://www.plugin-dev.com/intellij/general/plugin-initial-load/).

## 4. Confirm the language server connection

After successfully connecting to the language server, a green icon will appear in the bottom-right corner of your IDE. Clicking on the icon will display connection details and timeouts.

![Green status icon appearing in the IDE's bottom-right corner after the language server connects](../resources/images/lang-server-connect.gif)

> **Tip:** A green icon in the IDE's bottom-right corner indicates a successful connection to the language server. Clicking on the icon will display connection details and timeouts.

![Clicking the status icon to open the connection details and timeouts panel](../resources/images/connected-and-timeouts.gif)

---

## Alternative ways to connect to a language server

In addition to `RawCommandServerDefinition`, several classes implement [LanguageServerDefinition](../src/main/java/org/wso2/lsp4intellij/client/languageserver/serverdefinition/LanguageServerDefinition.java), allowing you to connect to a language server in different ways.

> **Note:** All implementations use stdin/stdout for server communication.

### RawCommandServerDefinition

The first argument is a file extension or a comma-separated list of file extensions (e.g., `"ts,js"`). Matching is based on file extensions only; filename patterns or regex-style values such as `"application*.properties"` are not supported by this API.

**Example usage:**

```java
new RawCommandServerDefinition("bal", new String[]{"path/to/launcher-script.sh"});
```

```java
String[] command = new String[]{"java", "-jar", "path/to/language-server.jar"};
new RawCommandServerDefinition("bsl,os", command);
```

### ProcessBuilderServerDefinition

This definition is an extended form of `RawCommandServerDefinition` that accepts `java.lang.ProcessBuilder` instances, giving you more control over the language server process.

You can specify one or more file extensions for a server definition. To associate multiple extensions with the same server, separate them with a comma (e.g., `"ts,js"`).

**Example usage:**

```java
ProcessBuilder process = new ProcessBuilder("path/to/launcher-script.sh");
new ProcessBuilderServerDefinition("bal", process);
```

```java
ProcessBuilder process = new ProcessBuilder("java", "-jar", "path/to/language-server.jar");
new ProcessBuilderServerDefinition("bsl,os", process);
```

## Custom initialization parameters

If your language server requires custom initialization options, extend `ProcessBuilderServerDefinition` or `RawCommandServerDefinition` and override the `customizeInitializeParams` method:

```java
public class MyServerDefinition extends ProcessBuilderServerDefinition {
    public MyServerDefinition(String ext, ProcessBuilder process) {
        super(ext, process);
    }

    @Override
    public void customizeInitializeParams(InitializeParams params) {
        params.setClientInfo(new ClientInfo("MyName", "MyVersion"));
    }
}
```

Then assign your class as a server definition:

```java
ProcessBuilder process = new ProcessBuilder("path/to/launcher-script.sh");
IntellijLanguageClient.addServerDefinition(new MyServerDefinition("xxx", process));
```

See [#311](https://github.com/ballerina-platform/lsp4intellij/pull/311) for more details.

---

## Configuration

### Timeouts

The LSP4IntelliJ language client applies the following default timeouts to LSP requests:

| Type            | Default timeout (ms) |
|-----------------|:--------------------:|
| Code Actions    |         2000         |
| Completion      |         1000         |
| Goto Definition |         2000         |
| Execute Command |         2000         |
| Formatting      |         2000         |
| Hover Support   |         2000         |
| Initialization  |        10000         |
| References      |         2000         |
| Shutdown        |         5000         |
| WillSave        |         2000         |

The client exposes the following methods for inspecting and overriding these values at runtime:

- **`getTimeouts()`** — returns the current timeout values (in milliseconds).

  ```java
  Map<Timeouts, Integer> timeouts = IntellijLanguageClient.getTimeouts();
  ```

- **`getTimeout(Timeouts timeoutType)`** — returns the current timeout value for a given type (in milliseconds).

  ```java
  int timeout = IntellijLanguageClient.getTimeout(Timeouts.INIT);
  ```

- **`setTimeouts(Map<Timeouts, Integer> newTimeouts)`** — overrides the defaults with a given set of timeout values.

  ```java
  Map<Timeouts, Integer> newTimeouts = new HashMap<>();
  newTimeouts.put(Timeouts.INIT, 15000);
  newTimeouts.put(Timeouts.COMPLETION, 1000);
  IntellijLanguageClient.setTimeouts(newTimeouts);
  ```

- **`setTimeout(Timeouts timeout, int value)`** — overrides a single timeout value.

  ```java
  IntellijLanguageClient.setTimeout(Timeouts.INIT, 15000);
  ```

---

## Appendix: Legacy components-based setup

> **Deprecated:** This setup style is no longer recommended. New plugins should use the modern extension-based setup above. This appendix is preserved for older plugins that still use application components.

1. Add `IntellijLanguageClient` as an application component:

   ```xml
   <application-components>
       <component>
           <implementation-class>org.wso2.lsp4intellij.IntellijLanguageClient</implementation-class>
       </component>
   </application-components>
   ```

2. Add the following extensions to enable individual features:

   - **Code completion** (replace the `language` attribute if you have your own [custom language implementation](https://www.jetbrains.org/intellij/sdk/docs/tutorials/custom_language_support/language_and_filetype.html#define-a-language)):

     ```xml
     <extensions defaultExtensionNs="com.intellij">
         <completion.contributor implementationClass="org.wso2.lsp4intellij.contributors.LSPCompletionContributor"
                                 id="LSPCompletionContributor" language="any"/>
     </extensions>
     ```

   - **Code formatting:**

     ```xml
     <actions>
        <action class="org.wso2.lsp4intellij.actions.LSPReformatAction" id="ReformatCode" use-shortcut-of="ReformatCode"
                overrides="true" text="Reformat Code"/>
        <action class="org.wso2.lsp4intellij.actions.LSPShowReformatDialogAction" id="ShowReformatFileDialog"
                use-shortcut-of="ShowReformatFileDialog" overrides="true" text="Show Reformat File Dialog"/>
     </actions>
     ```

   - **Diagnostics and code actions** (replace the `language` attribute if you have your own [custom language implementation](https://www.jetbrains.org/intellij/sdk/docs/tutorials/custom_language_support/language_and_filetype.html#define-a-language)):

     ```xml
     <extensions defaultExtensionNs="com.intellij">
        <externalAnnotator id="LSPAnnotator" language="TEXT" implementationClass="org.wso2.lsp4intellij.contributors.annotator.LSPAnnotator"/>
     </extensions>
     ```

   - **Find Usages:**

     ```xml
     <actions>
         <action class="org.wso2.lsp4intellij.actions.LSPReferencesAction" id="LSPFindUsages">
              <keyboard-shortcut first-keystroke="shift alt F7" keymap="$default"/>
         </action>
     </actions>
     ```

   - **Workspace symbols:**

     ```xml
     <extensions defaultExtensionNs="com.intellij">
         <gotoSymbolContributor implementation="org.wso2.lsp4intellij.contributors.symbol.LSPSymbolContributor"
                                       id="LSPSymbolContributor"/>
     </extensions>
     ```

   - **Renaming Support:**

     ```xml
     <extensions defaultExtensionNs="com.intellij">
         <renameHandler implementation="org.wso2.lsp4intellij.contributors.rename.LSPRenameHandler"
                        id="LSPRenameHandler" order="first"/>
         <renamePsiElementProcessor implementation="org.wso2.lsp4intellij.contributors.rename.LSPRenameProcessor"
                                    id="LSPRenameProcessor" order="first"/>
     </extensions>
     ```

   - **Signature Help:**

     ```xml
     <extensions defaultExtensionNs="com.intellij">
         <typedHandler implementation="org.wso2.lsp4intellij.listeners.LSPTypedHandler"
                       id="LSPTypedHandler"/>
     </extensions>
     ```

> **Note:** No additional configuration is required for the other features.
