# [lsp4intellij]() - Language Server Client Library for Jetbrains Plugins

[![Build Status](https://travis-ci.com/NipunaRanasinghe/lsp4intellij.svg?branch=master)](https://travis-ci.com/NipunaRanasinghe/lsp4intellij)

Adds Language Server Protocol support for IntelliJ IDEA and other Jetbrains IDEs.


This light-weight dependency library is designed to be used with any IntelliJ plugin such that the plugin can easily 
use this as a 
language server client to get language server based features, just by changing the plugin configuration file (aka. plugin.xml).

This library also allows the plugin developers to use language specific language server protocol extensions. 

**Please refer 
[lsp4intellij-plugin](https://github.com/NipunaRanasinghe/lsp4intellij-plugin/tree/master) example to learn how to use 
`lsp4intellij` as the language server client in your plugin to connect to a language server and extend the client 
library to support your language server specific protocol extensions.**

Currently tested with IntelliJ IDEA and
[Ballerina Language Server](https://github.com/ballerina-platform/ballerina-lang/tree/master/language-server). Need 
to be tested with other language servers and other Jetbrains IDEs as well.

> Please note that this library is currently capable of serving language server based completions and other features 
are WIP.
