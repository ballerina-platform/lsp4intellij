# ADR 0002: Project- and application-scoped server registries

- Status: Accepted
- Date: 2026-07-04

## Context

Before this decision, server and wrapper bookkeeping lived in six independent `static` maps
across two classes:

- `IntellijLanguageClient`: `extToLanguageWrapper`, `projectToLanguageWrappers`,
  `extToServerDefinition`, `extToExtManager` — keyed by `(ext, projectUri)` string pairs (or `ext`
  alone), with `projectUri == ""` used as a sentinel for "applies to any project".
- `LanguageServerWrapper`: `uriToLanguageServerWrapper` (keyed by `(fileUri, projectUri)`),
  `projectToLanguageServerWrapper` (keyed by the `Project` instance itself).

This had concrete consequences:

- The same wrapper was indexed in two independent maps (`extToLanguageWrapper` and
  `uriToLanguageServerWrapper`) that could drift out of sync, since nothing enforced they were
  updated together.
- `projectToLanguageServerWrapper` mapped a `Project` to a single wrapper — the last one
  constructed — so `LanguageServerWrapper.forProject()` returned the wrong server whenever a
  project ran more than one language server.
- `projectToLanguageServerWrapper` held a strong `Project` reference with no automatic removal
  path other than the code in `IntellijLanguageClient.removeWrapper` running to completion; a
  `Project` could outlive its own close if any removal path was skipped.
- Wrapper creation (`IntellijLanguageClient.updateLanguageWrapperContainers`) was a JVM-wide
  `synchronized` static method: creating a wrapper for one project blocked wrapper creation for
  every other open project, with no relationship between the two.
- Cleanup was manual and scattered: a JVM shutdown hook in `IntellijLanguageClient.initComponent()`
  stopped every running wrapper on JVM exit; `LSPProjectManagerListener.projectClosing` separately
  looked up and disposed the wrappers of one project by re-deriving its URI; `removeWrapper` undid
  the ext/URI registrations. None of these shared a single owner.
- The static-registry design made this whole surface untestable in isolation — state could not be
  reset between tests, and a test-scoped IntelliJ `Project` had no way to get its own clean set of
  wrappers.

## Decision

### 1. Two IntelliJ Platform services replace the six static maps

- **`org.wso2.lsp4intellij.services.LspServerManager`** — `@Service(Service.Level.PROJECT)`, one
  instance per project, obtained via `LspServerManager.getInstance(project)`. Owns everything that
  was project-scoped: project-level server definitions, the ext-to-wrapper map, the URI-to-wrapper
  map, and the set of wrappers running for that project (plus a "last registered wrapper" field
  that reproduces the pre-existing `forProject()` behavior — see Consequences).
- **`org.wso2.lsp4intellij.services.LspApplicationServerRegistry`** — `@Service(Service.Level.APP)`,
  one instance for the whole IDE process, obtained via `LspApplicationServerRegistry.getInstance()`.
  Owns what was never project-scoped to begin with: server definitions registered without a
  project (the old `projectUri == ""` sentinel), and the LSP extension managers (which were already
  keyed by extension alone, with no project dimension in the original design).

Both are *light services*: annotated with `@Service`, requiring no `plugin.xml` registration,
consistent with this library shipping no descriptor of its own. `LspServerManager` takes a
`Project` constructor parameter, which the platform supplies automatically.

A small package-private `DefinitionMatcher` holds the extension-or-filename-regex matching logic
shared by both registries' definition maps, so the two classes cannot implement that lookup
differently by accident.

### 2. Every existing public method keeps its exact signature

`IntellijLanguageClient`'s static methods (`addServerDefinition`, `addExtensionManager`,
`getAllServerWrappersFor`, `isExtensionSupported`, `editorOpened`, `editorClosed`, `removeWrapper`,
`getProjectToLanguageWrappers`, `didChangeConfiguration`, `getExtensionManagerForDefinition`,
`initProjectConnections`) and `LanguageServerWrapper`'s static finders (`forUri`, `forVirtualFile`,
`forEditor`, `forProject`) are unchanged in signature and behavior; each now delegates to one or
both services internally. Consuming plugins compile and run unchanged.

`LspServerManager` and `LspApplicationServerRegistry` are `public` (required for cross-package
calls from `IntellijLanguageClient` and `LanguageServerWrapper`) but are not part of the supported
consumer-facing API — plugin developers should keep using the existing static methods.

### 3. No `@Deprecated` on the facades yet

The static methods are not marked deprecated. There is no replacement public API for a consumer to
migrate to until a later phase introduces composable per-feature overrides (see
`ARCHITECTURE.md` section 2.7). Deprecating `addServerDefinition` — the library's primary,
documented entry point — ahead of a real alternative would only produce a warning with no
actionable next step. Deprecation is deferred to whichever phase actually ships a replacement.

### 4. Disposal: keep the proven-safe explicit trigger, add the platform's chain as a backstop

`LanguageServerWrapper.dispose()` has exactly one call site: `LSPProjectManagerListener
.projectClosing`, which the platform invokes synchronously *before* the project's own disposal
sequence begins. That ordering is what makes it safe for the disposal path to call
`project.getService(...)` — a project mid/post-disposal will throw on that call.

`LspServerManager` implements `Disposable`, and its `dispose()` method (disposing every wrapper
registered for that project) is invoked from two places:

- explicitly, synchronously, from `LSPProjectManagerListener.projectClosing` — the ordering
  already known to be safe;
- automatically, by the platform, as part of the project's own `Disposer` chain when the project
  is actually disposed.

`dispose()` is idempotent (disposing an already-empty wrapper set is a no-op, and
`LanguageServerWrapper.dispose()` itself already tolerates repeat calls), so invoking it from both
places is safe. This was a deliberate choice over relying solely on automatic platform disposal:
the explicit trigger's timing is already proven correct in this codebase, and the automatic path
is added as a backstop rather than a replacement, without having a live IDE to empirically verify
the platform's disposal ordering in every edge case.

The JVM shutdown hook in `IntellijLanguageClient.initComponent()` (which stopped every running
wrapper across every project on JVM exit) is deleted. Under a normal IDE shutdown, every open
project's `projectClosing` fires before JVM exit, which already disposes their wrappers through
the path above. The gap this leaves is a genuinely abrupt JVM termination that bypasses normal
project close — accepted, consistent with most IntelliJ plugins not maintaining their own JVM
shutdown hook for process cleanup.

### 5. Per-project locking replaces the global lock

`IntellijLanguageClient.updateLanguageWrapperContainers` was `static synchronized` — a single
JVM-wide lock. Its replacement, `LspServerManager.getOrCreateWrapper`, is `synchronized` on the
service *instance*, i.e., one lock per project. This is a direct consequence of moving the state
into a project-scoped service, not a separate change: wrapper creation in one project no longer
blocks wrapper creation in another.

`LanguageServerWrapper`'s constructor no longer touches any static or service state — registering
the new wrapper (ext-to-wrapper map, wrapper set, "last wrapper") is the caller's responsibility,
done once, atomically, inside `LspServerManager.getOrCreateWrapper`.

## Consequences

- Wrapper state for one project can no longer drift out of sync with wrapper state for another —
  each project's `LspServerManager` instance is self-contained.
- The `forProject()` "returns the wrong wrapper when a project runs multiple servers" limitation
  is preserved exactly as it was. This ADR relocates existing state; it does not redesign
  `forProject()`'s contract. Fixing it (e.g., by having callers pass an extension or definition to
  disambiguate) is left to a later phase.
- `getProjectToLanguageWrappers()` now reconstructs its result by scanning
  `ProjectManager.getOpenProjects()` and querying each project's service, rather than returning a
  single live map. Callers only ever read a snapshot from it today, so this is behavior-compatible;
  it is also a safer contract (external code can no longer accidentally mutate library-internal
  state through the returned map).
- `isExtensionSupported(VirtualFile)` keeps its existing signature, which takes no `Project`
  parameter and therefore keeps checking *every* open project's definitions plus the application
  registry — exactly the scope the original single global map covered. This is a pre-existing
  design quirk (the check isn't actually project-scoped despite most of its callers having a
  specific file in a specific project in hand) that this ADR preserves rather than fixes.
- The session/feature-layer redesign described in `ARCHITECTURE.md` section 2.3 (an `LspSession`
  state machine, `RequestExecutor`-driven capability gating at the session level, per-URI
  `DocumentSynchronizer`) is not part of this decision. `LspServerManager` is a registry, not the
  full session layer; `LanguageServerWrapper` still owns the lifecycle state machine described in
  [[0001-threading-and-concurrency-model]].

## Rules for new code

1. Project-scoped server/wrapper state is added to `LspServerManager`, not to a new static field.
2. State that is not project-scoped (has no natural relationship to a specific `Project`) is added
   to `LspApplicationServerRegistry`, not to a new static field.
3. Code that needs "the wrapper(s) for this project" calls
   `LspServerManager.getInstance(project)`, never re-derives it from a URI string comparison.
4. Any new disposal path added to `LspServerManager` must remain idempotent, since it is invoked
   from both the explicit `projectClosing` trigger and the platform's automatic service disposal.
5. Do not add `@Deprecated` to `IntellijLanguageClient`'s or `LanguageServerWrapper`'s static
   methods until a replacement public API actually exists for consumers to migrate to.
