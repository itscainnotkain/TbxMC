# Tebex Java SDK migration

## Decisions

- Preserve all 12 platform modules, operator commands, permissions, configuration and GUIs.
- Consume the pinned `tebex-java-sdk` submodule unchanged through a Gradle composite build.
- Use SDK API clients, models and exceptions; keep Minecraft orchestration in one Java 8 `minecraft-common` module.
- Do not run TXE: its string-only command hook cannot preserve Floodgate identity resolution and delayed-delivery context. Its checkout/sendlink/ban/lookup handlers are stubs.
- Remove the old `io.tebex.sdk` Java API; no third-party compatibility facade.
- Use one command registry and shared shop presentation, retaining native platform scheduling/rendering.
- Preserve YAML keys and BuycraftX configuration import. Validate credentials before adoption. Scope callbacks and delivery state to their authenticated connection.
- Delivery deduplication is in-memory, not a promise of exactly-once delivery across crashes.

## Progress

Statuses: pending / in progress / blocked / done. Update this file with every implementation batch. A task is done only with verification evidence. Work is uncommitted unless a commit is explicitly recorded.

| ID | Phase | Status | Evidence / remaining work |
| --- | --- | --- | --- |
| P0 | Capture baseline and tracker | done | Inspected all modules and pinned SDK; original `:sdk:compileJava` passed before migration. |
| P1 | Composite dependency and shared build | done | Composite dependency, 12 JARs and both common modules verified. Shared shading/naming, descriptors, native/shared Java targets and isolated SDK classloading checks passed. |
| P2 | Shared runtime | done | SDK-backed connection, delivery ledger, commands, identity, reporting and shop presenter implemented; deterministic tests passed. Native integration remains under P3/P4. |
| P3 | Bukkit reference integration | in progress | Shared Bukkit bindings and non-destructive BuycraftX import implemented and compiled. Native smoke testing remains. |
| P4 | Remaining 11 platforms | in progress | All adapters migrated and all 12 platform `compileJava` tasks passed. Native smoke testing remains. |
| P5 | Remove old SDK and validate | in progress | Removal, source audit, CI configuration, scripts and README complete; full local automated verification passed. Native release checklist and remote CI execution remain unverified. |

## Platform verification

Compilation alone does not complete a platform. Record actual checks, never inferred passes. GUI is not applicable on proxies.

| Platform | Migration | Compile | Artifact | Startup | Delivery/commands | GUI | Shutdown |
| --- | --- | --- | --- | --- | --- | --- | --- |
| bukkit | implemented | passed | passed | pending | pending | pending | pending |
| folia | implemented | passed | passed | pending | pending | pending | pending |
| bungeecord | implemented | passed | passed | pending | pending | n/a | pending |
| velocity | implemented | passed | passed | pending | pending | n/a | pending |
| fabric-26.1 | implemented | passed | passed | pending | pending | pending | pending |
| fabric-26.2 | implemented | passed | passed | pending | pending | pending | pending |
| forge-1.20.1 | implemented | passed | passed | pending | pending | pending | pending |
| forge-1.21.1 | implemented | passed | passed | pending | pending | pending | pending |
| forge-26.1 | implemented | passed | passed | pending | pending | pending | pending |
| neoforge-1.20.2 | implemented | passed | passed | pending | pending | pending | pending |
| neoforge-1.21.1 | implemented | passed | passed | pending | pending | pending | pending |
| neoforge-26.1 | implemented | passed | passed | pending | pending | pending | pending |

## Acceptance and test plan

- SDK suite plus local HTTP integration fixtures; no production credentials in automated tests.
- Authentication failure, malformed responses, outage, reload, secret changes and stale callbacks.
- Delays, disconnect/reconnect, inventory requirements, repeated polls, ordered dispatch, failed dispatch, acknowledgement retry and shutdown.
- Java/offline/Geyser identities, Floodgate linked users, unavailable Floodgate and Bedrock checkout messages.
- Commands, aliases, permissions/admin override, console inputs, proxy subsets, help/completion.
- Category navigation, back buttons, configured item/lore, sale prices, empty/failed catalogue and checkout errors.
- All 12 builds and packaged dependency/descriptor/Java-target checks; oldest Bukkit and modern Paper compatibility; Folia scheduler ownership.
- No production `io.tebex.sdk` imports, custom Tebex HTTP implementation, platform-local polling/business command handlers/shop presentation.
- Preserve SDK submodule content and staged user additions. Record environmental blockers and remaining server smoke tests explicitly.

## Architecture

`minecraft-common` contains the runtime, connection state, configuration, commands, queue coordinator, identity and shop presenter. Its platform boundary covers asynchronous completed dispatch, player-owned/server execution, cancellation, player inspection, messaging, logging and metadata. SDK DTOs are used directly; presentation and runtime state are not wire models.

`bukkit-common` shares Bukkit/Folia bindings compiled against Java 8. Mod loaders keep incompatible native item/container/permission types in their version modules. Build conventions centralize shading and artifact naming; loader remapping stays local.

One queue coordinator honors remote next_check, deduplicates pending commands, and retries acknowledgements without redispatch in the same instance. Dispatch attempts reporting failure are logged and acknowledged, matching existing behavior; skipped delivery is not acknowledged. Network work never blocks a platform thread. Shutdown cancels work and rejects late callbacks.

## Evidence and deviations

- Initial SDK revision: `153f935693c6da1e3bfabc0f44175781c557f40f`.
- The removed in-repository SDK tests had real-credential placeholders and JUnit Platform disabled; replacement consumer tests use local fixtures and mocks.
- Existing build uses Gradle 9.4.1; Java targets span 8, 17, 21 and 25. README/build/test scripts were stale and have been updated.
- Velocity previously had a separate command implementation; Bungee exposes help/forcecheck/reload/secret/debug, Velocity additionally exposes ban.

### Verification log (2026-09-05)

- Baseline old SDK compilation passed before edits.
- All 12 native adapters plus both common modules compiled successfully after migration.
- Initial consumer suite and standalone pinned SDK suites passed. Added same-key reload, malformed authentication, retired-request and shutdown regression cases during the final audit.
- Initial all-in-one packaging attempt exhausted Windows paging/native memory with concurrent Gradle workers; full verification subsequently passed with one worker, no project parallelism and a 2 GiB Gradle heap. Repository defaults now cap workers at two and disable project parallelism. Paging pressure also briefly prevented a PowerShell launch during a later build; no source change was lost.
- Bounded-worker verification exposed an intermittent upstream SDK failure: `PluginLogTest.rejectedKeyIsLoggedButNotReported` saw `Could not refresh the store catalogue: interrupted` events from prior TXE work. SDK sources remain untouched. A subsequent full-suite rerun passed without changes or exclusions; retain this intermittent upstream issue for follow-up. The Minecraft runtime does not start TXE.

### Native smoke checklist (still required)

Use disposable test servers and a test store. Do not point smoke tests at a production delivery queue. No server EULA or purchase is accepted by this migration.

- [ ] Start oldest supported Bukkit and modern Paper, plus each platform row; verify descriptors, dependency loading and configuration import without deleting BuycraftX files.
- [ ] Exercise console/player commands, per-command permissions/admin override, aliases, help and completion; verify proxy subsets and Velocity optional ban arguments.
- [ ] Verify valid/invalid credentials, same-key reload, changed-key reload, slow/out-of-order responses and API outage recovery.
- [ ] Deliver immediate/offline/delayed commands, disconnect and reconnect during delay, test full inventory, command failure and acknowledgement retries.
- [ ] Verify Java online/offline mode, Geyser/Floodgate linked and unlinked identities, missing Floodgate, Bedrock and clickable Java checkout links.
- [ ] Browse nested/empty/large catalogues and configured materials/lore, sale prices, back/pagination and failed checkout.
- [ ] On Folia, check inventory/player access occurs on the entity scheduler and console dispatch on the global scheduler, including teleport/disconnect.
- [ ] Stop/restart during HTTP requests and delayed deliveries; confirm no post-disable dispatch and no surviving Minecraft coordinator workers. The SDK owns a process-wide daemon HTTP executor without a close API; consumer futures are cancelled but that daemon is not forcibly terminated.

Implementation is uncommitted. Automated checks do not substitute for this native release sign-off.

### Implemented changes

- [x] Replace `:sdk` with `:minecraft-common`; use pinned SDK `io.tebex:tbx:3.0.0` through composite substitution.
- [x] Use SDK Plugin/Headless clients, models, telemetry and event types; remove the old HTTP/DTO/triage/placeholder layers.
- [x] Centralize polling/deadlines, catalogue, joins/log batching, dispatch completion and acknowledgement retry.
- [x] Validate credentials before adoption/persistence, retain delivery state on same-key reload, reject stale callbacks and cancel pending consumer futures on shutdown.
- [x] Share delayed delivery, inventory/online checks, identity/Floodgate handling and native Java/Bedrock checkout messages.
- [x] Share command registry, permissions/help and argument handling, with proxy subsets and Velocity optional ban arguments.
- [x] Share configured shop presentation, navigation, sale handling and pagination; keep native renderers local.
- [x] Share Bukkit/Folia bootstrap, commands, listeners, materials and GUI; override Folia global/entity scheduling.
- [x] Preserve configuration keys and non-destructive BuycraftX import, including boolean parsing/defaults.
- [x] Remove duplicate platform timers, business handlers, build naming helpers and unused XSeries/trove dependencies.
- [x] Add deterministic tests, SDK suite integration, packaged descriptor/bytecode checks, isolated SDK classloading and CI configuration.
- [x] Update README/build/test scripts and remove temporary migration scripts and task-generated crash logs.
- [ ] Complete native server checklist and remote CI before release.

### Reproduce automated verification

```powershell
.\gradlew.bat verifyMigration --max-workers=1 --no-parallel "-Dorg.gradle.jvmargs=-Xmx2G" --console=plain
```

Final full run: `BUILD SUCCESSFUL` (2m 3s), 79 actionable tasks. 12 artifacts in `builds/`; `tbx` 171 tests, no failures; generated `headless-api` 339 tests, 29 upstream skips, no failures. The consumer suite passed 36 tests with no failures or skips. Reports are in each module's `build/reports/tests/test/`. Artifact validation rejects old SDK classes and unrelocated HTTP/Gson dependencies, instantiates SDK clients from an isolated classloader, and decodes a shaded SDK model.

The submodule remains at `153f935693c6da1e3bfabc0f44175781c557f40f` with a clean worktree. Original staged `.gitmodules` and gitlink additions are preserved; no commit was created. Final `git diff --check` passed. The task-owned Gradle daemon was stopped after verification to release build memory.
