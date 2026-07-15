# ADR 0001: Threading and concurrency model

- Status: Accepted
- Date: 2026-07-04

## Context

Before this decision, the library had the following threading behavior:

- All asynchronous work for every language server of every project was submitted to one global
  single-threaded executor (`ApplicationUtils.pool()`). One slow or hung server delayed requests and
  document-synchronization notifications of all other servers.
- Several request paths blocked the Swing event dispatch thread (EDT) with `Future.get(timeout)`
  calls of up to several seconds (code actions, rename, ctrl-click navigation, find usages), which
  froze the IDE UI for the duration of the wait.
- The try/catch block implementing the timeout and error policy for blocking waits was duplicated at
  nine call sites with small accidental differences.
- Executors were created per listener or per launch and never shut down, leaking one thread per
  editor and one thread pool per server start.
- Collections shared between the EDT, the executor thread, and lsp4j reader threads were
  unsynchronized (`HashSet`, `HashMap`, plain `int` counters).

## Decision

### 1. One dispatcher thread per server

Each `LanguageServerWrapper` owns a single-threaded executor (a daemon thread named
`lsp4intellij-<ext>`), exposed as `wrapper.pool(Runnable)`. All work belonging to one server runs on
its dispatcher: request waits, `didOpen`/`didChange`/`didClose`/`didSave` notifications, editor
connect/disconnect, and restart.

Rationale: the LSP requires client-to-server messages of one session to be ordered (a `didChange`
must not overtake the `didOpen` of the same document). A single thread per server preserves that
order without cross-server contention. The dispatcher is shut down in `dispose()`; tasks submitted
after disposal are dropped.

The global `ApplicationUtils.pool()` remains only for work that has no wrapper yet: resolving which
server definition matches an opened editor, and VFS-driven file events.

### 2. All blocking request waits go through `RequestExecutor`

`RequestExecutor.waitFor(future, timeoutType)` is the only place that blocks on a request future.
It implements the complete policy:

- waits with the timeout configured for the request type (`Timeouts`),
- reports success or failure to the server status widget,
- `TimeoutException`: log, report failure, return null,
- `InterruptedException`: restore the interrupt flag and return null without invoking the crash
  handler (the dispatcher is interrupted during disposal; a crash-triggered reconnect at that point
  would restart a server that is being torn down),
- `JsonRpcException` / `ExecutionException`: route to `wrapper.crashed(e)`,
- returns null for "no result"; callers must handle null.

New request code must not call `Future.get` directly.

### 3. The EDT never waits for a server

`RequestExecutor.waitFor` must not be called on the EDT. The pattern for a feature triggered from
the UI is:

1. capture editor state (offsets, positions) on the calling thread, using a read action where
   required;
2. submit the request work to `wrapper.pool(...)`;
3. apply results (annotations, hints, markup, navigation, popups) on the EDT via `invokeLater`,
   re-checking `editor.isDisposed()` inside the runnable.

Write actions run on the EDT. Code that must open or close editors from a background thread
marshals that step with `invokeAndWait`; it must not hold the read lock while doing so
(`EditorEventManager.openEditor` is the reference implementation).

### 4. Executor lifecycle

- The lsp4j launcher executor is created in `start()` and shut down in `stop()`.
- The wrapper dispatcher is shut down in `dispose()`.
- Listener debouncing uses the shared application scheduled pool
  (`AppExecutorUtil.getAppScheduledExecutorService()`); creating a thread or executor per editor,
  per listener, or per request is not allowed.
- `stop(boolean)` is `synchronized`, so concurrent stop attempts (a stop queued on the dispatcher,
  `dispose()`, the JVM shutdown hook) run one at a time and the status guard turns later callers
  into no-ops. On the last editor disconnect, `stop` is submitted to the dispatcher instead of
  called inline, so the queued `didClose` notification is sent before the shutdown request.

### 5. Shared state is thread-safe by construction

State reachable from more than one of {EDT, dispatcher, lsp4j reader threads} uses concurrent
collections (`ConcurrentHashMap`, `ConcurrentHashMap.newKeySet()`) or atomic types
(`AtomicInteger` for crash counts and document versions). New fields on `LanguageServerWrapper`,
`EditorEventManager`, or `DocumentEventManager` follow the same rule.

## Consequences

- The IDE UI cannot be frozen by a slow language server: no code path blocks the EDT on a server
  response.
- Requests and notifications of one server are processed in submission order. A blocking wait on
  the dispatcher delays other work queued for the same server by up to that request's timeout; this
  is accepted for now. Removing it requires composing `CompletableFuture` chains instead of
  blocking waits, which is planned as a later change and does not alter the rules above.
- Timeout values, failure reporting, and crash routing behave identically for every request type,
  and change in one file.
- Tests can drive a full editor-open to server-shutdown cycle deterministically
  (`LspServerIntegrationTest`), because each server's work is confined to one known thread.

## Rules for new code

1. Per-server work goes through `wrapper.pool(...)`, not a new executor and not the global pool.
2. Blocking on a request future is done only via `RequestExecutor.waitFor`, never on the EDT.
3. UI mutation happens on the EDT via `invokeLater`, with an `editor.isDisposed()` re-check.
4. No per-editor, per-listener, or per-request threads; use the shared application pools.
5. Fields accessed from more than one thread use concurrent or atomic types.
