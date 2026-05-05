# Features

LSP4IntelliJ surfaces these LSP capabilities in JetBrains IDEs.

> **Note:** Features are mainly tested on IntelliJ IDEA.

## Table of Contents

<!-- The TOC below is auto-generated. To regenerate, run: npx markdown-toc -i docs/features.md --maxdepth 3 -->

<!-- toc -->

- [Code Completion (with code snippet support)](#code-completion-with-code-snippet-support)
- [Code Formatting](#code-formatting)
- [Diagnostics](#diagnostics)
- [Code Actions](#code-actions)
- [Go to Definition](#go-to-definition)
- [Go to References / Find Usages](#go-to-references--find-usages)
- [Hover Support](#hover-support)
- [Workspace Symbols](#workspace-symbols)
- [Renaming Support](#renaming-support)
- [Work in progress](#work-in-progress)

<!-- tocstop -->

---

## Code Completion (with code snippet support)

Press `Ctrl+Space` to see the completion items list, which depends on your cursor position. Code completion items also pop up automatically based on your language-server-specific trigger characters.

![Code completion popup showing language-server-provided suggestions](../resources/images/lsp4intellij-completion.gif)

For code snippets, use `Tab`/`Enter` to navigate to the next placeholder position, or `Esc` to apply the snippet with default values.

![Inserting a code snippet and tabbing through its placeholders](../resources/images/lsp4intellij-snippets.gif)

## Code Formatting

Navigate to **Code → Reformat Code** to open a dialog that lets you format the whole file or a selected range.

![Reformatting a file via the Reformat Code dialog](../resources/images/lsp4intellij-formatting.gif)

## Diagnostics

To see diagnostics (errors, warnings, etc.), hover over them to view the message.

![Hovering over a diagnostic to view the error message](../resources/images/lsp4intellij-diagnostics.gif)

## Code Actions

Hover over any diagnostic highlight to view and apply related code actions via the light bulb that pops up.

![Light bulb showing available code actions for a diagnostic](../resources/images/lsp4intellij-codeactions.gif)

## Go to Definition

Use `Ctrl+Click` (`Cmd+Click` on macOS) to navigate to a symbol's definition.

![Ctrl+Click navigation to a symbol's definition](../resources/images/lsp4intellij-gotodef.gif)

## Go to References / Find Usages

Use `Ctrl+Click` (`Cmd+Click` on macOS) or `Shift+Alt+F7` on a symbol to view its references/usages.

![Listing references and usages of a symbol](../resources/images/lsp4intellij-gotoref.gif)

## Hover Support

Hover over an element while pressing `Ctrl` (`Cmd` on macOS) to view its documentation, if available.

![Hover popup showing documentation for a symbol](../resources/images/lsp4intellij-hover.gif)

## Workspace Symbols

Click **Navigate** in the top menu, then **Symbol…**, and enter the name of the symbol you want to search for.

![Searching for a workspace symbol via the Navigate menu](../resources/images/lsp4intellij-workspacesymbols.gif)

## Renaming Support

Place the cursor on the element to rename and press `Shift+F6` to trigger in-place renaming.

![In-place rename of a symbol triggered with Shift+F6](../resources/images/lsp4intellij-renaming.gif)

---

## Work in progress

- Signature Help
