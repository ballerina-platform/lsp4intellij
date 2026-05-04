# Features

LSP4IntelliJ surfaces these LSP capabilities in JetBrains IDEs. For a quick at-a-glance summary, see the [Features table](../README.md#features) in the README.

> **Note:** Features are mainly tested on IntelliJ IDEA.

## Table of Contents

<!-- The TOC below is auto-generated. To regenerate, run: npx markdown-toc -i docs/features.md --maxdepth 3 -->
<!-- toc -->
<!-- tocstop -->

---

## Code Completion (with code snippet support)

Press `Ctrl+Space` to see the completion items list, which depends on your cursor position. Code completion items also pop up automatically based on your language-server-specific trigger characters.

![](../resources/images/lsp4intellij-completion.gif)

For code snippets, use `Tab`/`Enter` to navigate to the next placeholder position, or `Esc` to apply the snippet with default values.

![](../resources/images/lsp4intellij-snippets.gif)

## Code Formatting

Navigate to **Code → Reformat Code** to open a dialog that lets you format the whole file or a selected range.

![](../resources/images/lsp4intellij-formatting.gif)

## Diagnostics

To see diagnostics (errors, warnings, etc.), hover over them to view the message.

![](../resources/images/lsp4intellij-diagnostics.gif)

## Code Actions

Hover over any diagnostic highlight to view and apply related code actions via the light bulb that pops up.

![](../resources/images/lsp4intellij-codeactions.gif)

## Go to Definition

Use `Ctrl+Click` (`Cmd+Click` on macOS) to navigate to a symbol's definition.

![](../resources/images/lsp4intellij-gotodef.gif)

## Go to References / Find Usages

Use `Ctrl+Click` (`Cmd+Click` on macOS) or `Shift+Alt+F7` on a symbol to view its references/usages.

![](../resources/images/lsp4intellij-gotoref.gif)

## Hover Support

Hover over an element while pressing `Ctrl` (`Cmd` on macOS) to view its documentation, if available.

![](../resources/images/lsp4intellij-hover.gif)

## Workspace Symbols

Click **Navigate** in the top menu, then **Symbol…**, and enter the name of the symbol you want to search for.

![](../resources/images/lsp4intellij-workspacesymbols.gif)

## Renaming Support

Place the cursor on the element to rename and press `Shift+F6` to trigger in-place renaming.

![](../resources/images/lsp4intellij-renaming.gif)

---

## Work in progress

- Signature Help
