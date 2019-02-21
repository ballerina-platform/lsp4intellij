# [lsp4intellij]() - Language Server Client Library for Jetbrains Plugins

[![Build Status](https://travis-ci.com/NipunaRanasinghe/lsp4intellij.svg?branch=master)](https://travis-ci.com/NipunaRanasinghe/lsp4intellij)
[![](https://jitpack.io/v/NipunaRanasinghe/lsp4intellij.svg)](https://jitpack.io/#NipunaRanasinghe/lsp4intellij)

Adds Language Server Protocol support for IntelliJ IDEA and other Jetbrains IDEs.


This light-weight dependency library is designed to be used with any IntelliJ plugin such that the plugin can easily 
use this as a 
language server client to get language server based features, just by changing the plugin configuration file (aka. plugin.xml).

This library also allows the plugin developers to use language specific language server protocol extensions. 

## How To Use

- Please refer [jitpack/lsp4intellij](https://jitpack.io/#NipunaRanasinghe/lsp4intellij) to learn how to add the **lsp4intellij** as a dependancy to your **gradle/maven/sbt/leiningen** project.

- Please refer [lsp4intellij-plugin](https://github.com/NipunaRanasinghe/lsp4intellij-plugin/tree/master) to learn how to use `lsp4intellij` as the language server client in your plugin, connect to a language server and extend the client 
library to support your language server specific protocol extensions if required.

> **Note** - Currently tested with IntelliJ IDEA and
[Ballerina Language Server](https://github.com/ballerina-platform/ballerina-lang/tree/master/language-server). Need 
to be tested with other language servers and other Jetbrains IDEs as well.

## Features 
- Code Completion 
- Diagnostics 
- Code Actions

 **WIP** 
 - Code Formatting
