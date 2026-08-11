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

Both registries hold their extension-or-filename-regex-keyed definitions in a shared
`org.wso2.lsp4intellij.services.DefinitionRegistry` (a small class each service composes an
instance of, exposed via `definitions()`) rather than each keeping its own map and re-implementing
the same lookup logic. The two services differ only in scope; the storage and lookup behavior is
identical by construction, not by convention.

### 2. Every existing public method keeps its exact signature

`IntellijLanguageClient`'s static methods (`addServerDefinition`, `addExtensionManager`,
`getAllServerWrappersFor`, `isExtensionSupported`, `editorOpened`, `editorClosed`, `removeWrapper`,
`getProjectToLanguageWrappers`, `didChangeConfiguration`, `getExtensionManagerForDefinition`,
`initProjectConnections`) and `LanguageServerWrapper`'s static finders (`forUri`, `forVirtualFile`,
`forEditor`, `forProject`) keep their exact signatures; each now delegates to one or both services
internally. Consuming plugins compile unchanged.

Signature-compatible is not the same claim as behavior-identical in every respect:
`getProjectToLanguageWrappers()`'s return-value *contract* narrows, deliberately — see the first
Consequences bullet below for exactly what changed and why it was judged safe to ship as-is rather
than preserve byte-for-byte.

`LspServerManager` and `LspApplicationServerRegistry` are `public` (required for cross-package
calls from `IntellijLanguageClient` and `LanguageServerWrapper`) but are not part of the supported
consumer-facing API — plugin developers should keep using the existing static methods.

### 3. No `@Deprecated` on the facades yet

The static methods are not marked deprecated. There is no replacement public API for a consumer to
migrate to until a later phase introduces composable per-feature overrides (see
`ARCHITECTURE.md` section 2.7). Deprecating `addServerDefinition` — the library's primary,
documented entry point — ahead of a real alternative would only produce a warning with no
actionable next step. Deprecation is deferred to whichever phase actually ships a replacement.

### 4. Two service accessors: `getInstance` creates, `getInstanceIfCreated` never throws

`Project.getService(...)` throws `AlreadyDisposedException` on a disposed project, and it creates
the service when it does not exist yet. The six maps this ADR replaces were plain
`ConcurrentHashMap`s: every lookup returned `null` or an empty set for an unknown project and none
of them ever threw. Preserving that required splitting the accessor in two:

- **`getInstance(Project)`** — `project.getService(...)`. Used only by call sites that *register*
  state (`processDefinition`, `initProjectConnections`, `getOrCreateWrapper`) and run while the
  project is known to be alive.
- **`getInstanceIfCreated(Project)`** — returns `null` if `project.isDisposed()`, otherwise
  `project.getServiceIfCreated(...)`. Used by every read path and every removal path.

The read paths need it because they run where a project can close underneath them:
`isExtensionSupported` is called from `LSPAnnotator`, `LSPRenameHandler`, `LSPReformatAction` and
`LSPShowReformatDialogAction` on the EDT; `forProject` is called from
`LSPServerStatusWidget.IconPresentation` on every status-bar repaint; `getAllServerWrappersFor` is
reached from `LSPFileEventManager` inside `ApplicationUtils.pool(...)`, so it runs after the event
that triggered it. Throwing there surfaces to the user as an IDE internal error.

Not creating the service when it is absent is correct for those paths as well: nothing can be
registered for a project without going through `getInstance` first, so a project with no service has
no definitions and no wrappers by construction.

### 5. Disposal: keep the proven-safe explicit trigger, add the platform's chain as a backstop

`LanguageServerWrapper.dispose()` has exactly one call site: `LspServerManager.dispose()`, which is
reached from `LSPProjectManagerListener.projectClosing` — invoked synchronously *before* the
project's own disposal sequence begins.

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

Two properties make the backstop actually work when it is the path that runs. First, each
`wrapper.dispose()` is wrapped in its own `try`/`catch`: one failure cannot abort disposal of the
remaining wrappers, since a partial run leaves a language server process alive. Second, `dispose()`
clears `wrappers`, `extToWrapper` and `uriToWrapper` itself instead of relying on each wrapper's
`unregister`/`unmapUri` callback — on the platform path those callbacks resolve the service through
`getInstanceIfCreated`, which by then returns `null` because the project is already disposing, so
they are skipped.

### 6. Per-project locking replaces the global lock, and only the creation path locks

`IntellijLanguageClient.updateLanguageWrapperContainers` was `static synchronized` — a single
JVM-wide lock. Its replacement, `LspServerManager.getOrCreateWrapper`, is `synchronized` on the
service *instance*, i.e., one lock per project. This is a direct consequence of moving the state
into a project-scoped service, not a separate change: wrapper creation in one project no longer
blocks wrapper creation in another.

`dispose()` is `synchronized` on the same monitor, so it cannot interleave with wrapper creation:
it sets the terminal `disposed` flag before iterating, and `getOrCreateWrapper` returns `null`
once that flag is set, so a wrapper can never be created after the point past which nothing would
dispose it.

`unregister` is deliberately **not** `synchronized`. It runs inside `LanguageServerWrapper.dispose()`,
which holds the wrapper's own monitor, while `dispose()` above takes the service monitor and *then*
each wrapper's — so locking `unregister` would establish the reverse acquisition order and make the
two paths deadlockable as soon as anything calls `wrapper.dispose()` off the service's own path.
Every field it touches is independently thread-safe (both maps are concurrent, `wrappers` is a
concurrent set, and `lastWrapper` is an `AtomicReference` cleared with a compare-and-set), so the
monitor bought no atomicity worth that risk. For the same reason `mapUri`/`unmapUri`/`wrapperForUri`
are lock-free.

The JVM shutdown hook in `IntellijLanguageClient.initComponent()` (which stopped every running
wrapper across every project on JVM exit) is deleted. Under a normal IDE shutdown, every open
project's `projectClosing` fires before JVM exit, which already disposes their wrappers through
the path above. The gap this leaves is a genuinely abrupt JVM termination that bypasses normal
project close — accepted, consistent with most IntelliJ plugins not maintaining their own JVM
shutdown hook for process cleanup.

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
- **`getProjectToLanguageWrappers()` is a documented, deliberate behavioral/API compatibility
  change, not a like-for-like relocation.** Before this ADR, it returned the actual live
  `ConcurrentHashMap` field: a caller could mutate it (corrupting the library's own registry, since
  it was the same object) or retain the reference and observe later updates through it. After this
  ADR, it returns a freshly built `HashMap` snapshot on every call: mutating the returned map has no
  effect on library state, and retaining it does not track subsequent changes. This was judged safe
  to ship without a compatibility shim because every known caller (in this codebase and in the
  consuming plugins this library is aware of) only ever enumerates the returned map once and
  discards it, never mutates it, and never retains it expecting live updates — but a consumer doing
  either of those things will observe a behavior change on upgrade. If that surfaces in practice,
  the fix is to return an explicitly immutable, still-live view rather than to revert to exposing
  the mutable internal map directly.
- `DefinitionRegistry.asMap()` returns an unmodifiable *live view* of the backing map, not a
  snapshot — the opposite of `getProjectToLanguageWrappers()` above. Mutating it throws;
  registrations made after the call are visible through it. Its only caller
  (`initProjectConnections`) iterates it once, and `ConcurrentHashMap` iteration is weakly
  consistent, so concurrent registration during that iteration is safe.
- `isExtensionSupported(VirtualFile)` keeps its existing signature, which takes no `Project`
  parameter and therefore keeps checking *every* open project's definitions plus the application
  registry — exactly the scope the original single global map covered. This is a pre-existing
  design quirk (the check isn't actually project-scoped despite most of its callers having a
  specific file in a specific project in hand) that this ADR preserves rather than fixes. It now
  does one `getServiceIfCreated` lookup per open project per call, on EDT annotator paths; projects
  that have never registered a definition have no service and are skipped without allocating one.
- The session/feature-layer redesign described in `ARCHITECTURE.md` section 2.3 (an `LspSession`
  state machine, `RequestExecutor`-driven capability gating at the session level, per-URI
  `DocumentSynchronizer`) is not part of this decision. `LspServerManager` is a registry, not the
  full session layer; `LanguageServerWrapper` still owns the lifecycle state machine described in
  [[0001-threading-and-concurrency-model]].

## Rules for new code

1. Project-scoped server/wrapper state is added to `LspServerManager`, not to a new static field.
2. State that is not project-scoped (has no natural relationship to a specific `Project`) is added
   to `LspApplicationServerRegistry`, not to a new static field.
3. Code that needs "the wrapper(s) for this project" goes through `LspServerManager`, never
   re-derives it from a URI string comparison.
4. Use `getInstanceIfCreated` unless the call site is registering state and the project is known to
   be alive. Anything reachable from a contributor, a status-bar widget, a pooled task, or a
   disposal path uses `getInstanceIfCreated` and handles `null`, so that it cannot throw
   `AlreadyDisposedException` where the pre-service map lookup returned `null`.
5. Any new disposal path added to `LspServerManager` must remain idempotent, since it is invoked
   from both the explicit `projectClosing` trigger and the platform's automatic service disposal.
6. Do not take the `LspServerManager` monitor from code that already holds a
   `LanguageServerWrapper` monitor. `dispose()` acquires them in the order service-then-wrapper;
   acquiring them in the other order deadlocks. In practice: new removal/cleanup methods called
   from inside the wrapper stay lock-free, as `unregister` and `unmapUri` are.
7. Do not add `@Deprecated` to `IntellijLanguageClient`'s or `LanguageServerWrapper`'s static
   methods until a replacement public API actually exists for consumers to migrate to.
