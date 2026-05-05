# LSP4IntelliJ - Language Server Protocol Support for JetBrains Plugins

[![Build status](https://github.com/ballerina-platform/lsp4intellij/actions/workflows/build.yml/badge.svg)](https://github.com/ballerina-platform/lsp4intellij/actions/workflows/build.yml)
[![JitPack](https://jitpack.io/v/ballerina-platform/lsp4intellij.svg)](https://jitpack.io/#ballerina-platform/lsp4intellij)
[![License](https://img.shields.io/github/license/ballerina-platform/lsp4intellij.svg)](LICENSE)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.3%2B-blue.svg)](#compatibility-matrix)
[![GitHub last commit](https://img.shields.io/github/last-commit/ballerina-platform/lsp4intellij.svg)](https://github.com/ballerina-platform/lsp4intellij/commits/master)
[![Gitter](https://badges.gitter.im/ballerina-platform-lsp4intellij/community.svg)](https://gitter.im/ballerina-platform-lsp4intellij/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

**LSP4IntelliJ** is a client library that enables Language Server Protocol (LSP) support for IntelliJ IDEA and other JetBrains IDEs.

Designed for plugin developers, it facilitates integration with LSP-based features and supports language-specific extensions via the [JSON-RPC](https://en.wikipedia.org/wiki/JSON-RPC) protocol.

---

## Table of Contents

<!-- The TOC below is auto-generated. To regenerate after editing headings, run: npx markdown-toc -i README.md --maxdepth 4 -->

<!-- toc -->

- [Compatibility Matrix](#compatibility-matrix)
- [Projects Powered by LSP4IntelliJ](#projects-powered-by-lsp4intellij)
- [Quick Start](#quick-start)
- [Developer Guide](#developer-guide)
- [Features](#features)
- [License](#license)
- [Contributors](#contributors)
- [Inspiration](#inspiration)
- [Useful Links](#useful-links)

<!-- tocstop -->

---

## Compatibility Matrix

The table below groups released `lsp4intellij` artifacts by the IntelliJ IDEA version range they are known to be compatible with and the LSP specification version they support (derived from the bundled `org.eclipse.lsp4j` runtime). Because `lsp4intellij` is consumed as a library, your plugin's own `plugin.xml` ultimately controls runtime compatibility — but picking a library version whose range overlaps your plugin's target avoids API surface mismatches.

| LSP4IntelliJ          | Compatible IDEA versions | Supported LSP spec |
|-----------------------|--------------------------|--------------------|
| `0.95.1` – `0.96.2`   | 2021.1 – 2024.2          | 3.17               |
| `0.95.0`              | 2021.1 – 2024.2          | 3.16               |
| `0.94.0` – `0.94.2`   | 2017.3 – 2020.3          | 3.14               |
| `0.1.0` – `0.92.1`    | 2017.3 – 2020.3          | 3.13               |

> **Note:** Ranges reflect the IDEA versions covered while each release line was actively maintained. No upper bound (`untilBuild`) is enforced in the artifact itself, so newer IDEs are not artificially blocked — but compatibility outside the listed range is unverified and depends on whether the IntelliJ Platform APIs used by the library remain available in the target IDE.

---

## Projects Powered by LSP4IntelliJ

Here are some open-source projects that use `lsp4intellij` to integrate their language servers. Feel free to open a PR to add or remove an entry.

- [1C:Enterprise BSL Language Support](https://github.com/1c-syntax/intellij-language-1c-bsl)
- [AWS Smithy IntelliJ](https://github.com/awslabs/smithy-intellij)
- [Ballerina IntelliJ Plugin](https://github.com/ballerina-platform/plugin-intellij)
- [Cadence for IntelliJ Platform](https://github.com/cadence-tools/cadence-for-intellij-platform)
- [Chester IntelliJ Plugin](https://github.com/chester-lang/chester)
- [CSL IntelliJ Extension](https://github.com/nullptr-0/csl)
- [DiveKit Language Plugin](https://github.com/divekit/divekit-language-plugin-intellij)
- [EO IntelliJ LSP Plugin](https://github.com/GeorgySabaev/eo-intellij-lsp-plugin)
- [IntelliJ Jsonnet](https://github.com/zzehring/intellij-jsonnet)
- [IntelliJ V](https://github.com/nedpals/intellij-vlang)
- [Marko.js IntelliJ Plugin](https://github.com/biaspro/markojs-intellij-plugin)
- [nimtellij](https://github.com/observant2/nimtellij)
- [Robot Framework LSP (IntelliJ client)](https://github.com/robocorp/robotframework-lsp)
- [Seedwing Enforcer IntelliJ](https://github.com/seedwing-io/seedwing-enforcer-intellij-plugin)
- [Spring Tools for IDEA](https://github.com/gayanper/idea-spring-tools)
- [Valkyrie IntelliJ](https://github.com/valkyrie-language/valkyrie-intellij)
- [WDL IDE](https://github.com/broadinstitute/wdl-ide)
- [Yarn Spinner JetBrains Plugin](https://github.com/dogboydog/yarnspinner-jetbrains-plugin)
- [Yggdrasil IntelliJ](https://github.com/ygg-lang/yggdrasil-intellij)

---

## Quick Start

The minimum integration is three pieces: add the dependency, register a language server in a preloading activity, and wire that activity into `plugin.xml`. Replace the command and file extension with your own.

**`build.gradle`** — see [JitPack](https://jitpack.io/#ballerina-platform/lsp4intellij) for Maven and SBT snippets.

```gradle
implementation 'com.github.ballerina-platform:lsp4intellij:<version>'
```

**Preloading activity**

```java
public class MyLspPreloader extends PreloadingActivity {
    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
        IntellijLanguageClient.addServerDefinition(
            new RawCommandServerDefinition("mylang", new String[]{"path/to/language-server"}));
    }
}
```

**`plugin.xml`**

```xml
<extensions defaultExtensionNs="com.intellij">
    <preloadingActivity implementation="com.example.MyLspPreloader"
                        id="com.example.MyLspPreloader"/>
</extensions>
```

A green icon in the IDE's bottom-right confirms a successful connection.

---

## Developer Guide

The [Developer Guide](docs/developer-guide.md) provides in-depth instructions and reference material for plugin authors, including:

- [Step-by-step setup walkthrough](docs/developer-guide.md#1-add-the-lsp4intellij-dependency)
- [Alternative server-definition styles](docs/developer-guide.md#alternative-ways-to-connect-to-a-language-server) — `RawCommandServerDefinition` and `ProcessBuilderServerDefinition`
- [Custom LSP Initialization Parameters](docs/developer-guide.md#custom-initialization-parameters)
- [Request timeout tuning](docs/developer-guide.md#configuration)
- [Legacy components-based setup](docs/developer-guide.md#appendix-legacy-components-based-setup)

---

## Features

LSP4IntelliJ supports a wide range of LSP capabilities.

See the [Features guide](docs/features.md) for descriptions, triggers, and demos of each capability.

> **Note:** Features are mainly tested on IntelliJ IDEA.

---

## License

The LSP4Intellij code is distributed under the [Apache license 2.0](LICENSE).


## Contributors

A huge thanks to all the amazing contributors! 🚀

<a href="https://github.com/ballerina-platform/lsp4intellij/pulse"> <img align="center" src="https://contrib.rocks/image?max=100&repo=ballerina-platform/lsp4intellij" /> </a> 


## Inspiration

`LSP4IntelliJ` is heavily inspired by the [intellij-lsp](https://github.com/gtache/intellij-lsp) plugin community.
Credits should go to the original author for his astounding work.


## Useful Links

- [langserver.org](https://langserver.org/)
- [Language Server Protocol Specification](https://microsoft.github.io/language-server-protocol/specification)
