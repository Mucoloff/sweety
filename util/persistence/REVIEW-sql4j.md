# sql4j — Performance Review

Full review of `sql4j` (core ORM), `sql4j-processor` (codegen), `sql4j-rpc` (netty DB-over-RPC
gateway), backed by a new JMH benchmark module (`sql4j-benchmarks`, ported from Sweety's
`util/persistence/sql4j-benchmarks` and extended) plus JFR allocation profiling.

## Round 4 — PS caching, co-located on VPS2-stg (still inconclusive, direction flips)

Round 3's SSH-tunnel result was thrown out as network-latency-dominated (~50ms/insert is not
a real DB number). To get a clean read, the whole benchmark module (repo minus `.git`/`build`/
`.gradle`, rsynced) was built and run **on VPS2-stg itself**, against a MariaDB container bound
to `127.0.0.1` on that same host — zero network hop between JMH and the DB this time.

**Result: `psCacheOn` 2,322 ± 2,169 ms/op vs `psCacheOff` 1,859 ± 579 ms/op** (same 500-insert
batch, `avgt`). Per-insert cost dropped ~13x from round 3 (confirms round 3's numbers really
were network noise, not DB cost) — but `psCacheOff` is now nominally *faster*, and `psCacheOn`'s
own CI is wide enough (`[153, 4492]` ms/op at 99.9%) to swallow both means. Still not a
statistically defensible result either direction; five iterations of a 500-row loop is thin
for a benchmark riding on GC/JIT warmup variance and container-shared-host noise (VPS2-stg also
runs the live auth/admin/hub/core stack under Docker).

**Verdict unchanged: not applying app-layer PS pooling.** Two rounds now, two different
confounders (WAN latency, then measurement noise on a shared host), zero clean signal that
PS caching helps this workload. The architectural cost (PS pooling needs to survive across
`SqlConnection`'s per-query connection borrow — a real lifecycle change) was never justified by
a measured win, and still isn't. Calling this closed unless new evidence shows up — a dedicated
idle bare-metal/VM run with more iterations would be the next step if it's ever worth revisiting,
but this is no longer worth further chase for a fix that keeps failing to show a benefit.

**Infra side-effects of this round**: same VPS2-stg copy used in round 3 (`/root/luce-bench`,
disposable, not the deploy checkout at `/opt/luce`). Its local `settings.gradle.kts` had the
whole `stonecutter { create("client") {...} }` block commented out (VPS-copy only, never
committed) — the real block registers Fabric/NeoForge client variants that all pull
`com.terraformersmc:modmenu` from `maven.terraformersmc.com`, which was truncating every
download on that host (reproduced even from a healthy local connection — a broken/flaky
artifact host, not a VPS network issue) and blocked Gradle's whole-project configuration phase
for a task that only needed `sql4j-benchmarks`. MariaDB container torn down after the run;
Docker stays installed (per round 3).

## Round 3 — PreparedStatement caching, tested on real MariaDB (inconclusive)

Round 2 left PS pooling as "investigated, not applied" partly because round 1's H2 benchmark
was flat (no driver-side PS cache to test against). To settle it, spun up a real MariaDB
(`mariadb:11` in Docker) on the VPS2-stg staging box, tunneled it back over SSH, and added
`MariaDbPsCacheBenchmark`: 500 sequential single-row inserts, same SQL text every call —
`psCacheOn` uses luce's actual default MariaDB tuning (`HikariConnectionProvider.
applyDialectTuning`: `cachePrepStmts`/`useServerPrepStmts`/`prepStmtCacheSize`/
`prepStmtCacheSqlLimit`); `psCacheOff` uses a raw `HikariConfig` via `SQL4J.connect()....
withHikariConfig(...)` (documented power-user escape hatch) with those same properties
explicitly set to `false`/unset, same MariaDB, same driver, same everything else.

**Result: `psCacheOn` 24,414 ± 6,639 ms/op vs `psCacheOff` 26,472 ± 22,462 ms/op** (500-insert
batch, `avgt`). On is nominally ~8% faster, but the error bars materially overlap (off's range
alone spans 4,010–48,932ms) — **not a statistically defensible difference at this sample
size.** The real problem: both numbers are absurdly large (~50ms/insert) because the benchmark
ran over an SSH tunnel to a VPS from a home connection — network round-trip latency to the
remote MariaDB completely dominates and swamps whatever the PS-cache saves at the
protocol-parse level. This setup cannot isolate the effect; it needs the JMH process running
*on* the same host as the database (no WAN hop) for a clean read.

**Verdict: still not applying app-layer PS pooling.** The MySQL/MariaDB-specific evidence gap
flagged in round 2 is now filled with a *measurement*, not just an absence of one — but the
measurement itself is inconclusive by construction (network noise), not a green light. A
follow-up run co-located with the DB (either the JMH module deployed to VPS2-stg itself, or a
local MariaDB via `brew install mariadb`/Colima if either becomes available) is needed before
this can be resolved either way. The architectural objection from round 2 (PS caching needs to
survive across `SqlConnection`'s per-query connection borrow — a real lifecycle change, not a
local fix) still stands independently of this result.

**Infra side-effects of this round** (both already committed to the working tree / VPS,
listed for transparency):
- **Docker installed on VPS2-stg** (`docker-ce` 29.6.2, official apt repo) — was previously
  absent. Left installed (not removed) in case useful for a future co-located benchmark run;
  the `mariadb:11` container itself was torn down (`docker rm -f`) after this test.
- **WireGuard requirement removed from docs**: `setup.md` and `deploy.sh` referenced a
  `colleague-wg0` WireGuard tunnel as mandatory to reach VPS2-stg (`10.0.0.5`) — the VPS was
  since disconnected from that WG tunnel and is directly reachable at a new host
  (`207.180.250.207.nip.io`, confirmed reachable via plain SSH on port 22 as `root`). Updated
  both files to drop the WG prerequisite; `deploy.sh`'s unreachable-VPS error message no longer
  blames WireGuard.

### Reproduce round 3
```bash
# On a host near the DB (avoid the WAN-latency trap above):
docker run -d --name sql4j-bench-mariadb -p 127.0.0.1:13306:3306 \
  -e MARIADB_ROOT_PASSWORD=benchpass -e MARIADB_DATABASE=sql4jbench mariadb:11
./gradlew :util:persistence:sql4j-benchmarks:jmh -Djmh.include=MariaDbPsCacheBenchmark
```

---

## Round 2 — high-concurrency load ("dashboard" scenario) + applied optimizations

Round 1 (below) was entirely single-threaded. This round adds `DashboardLoadBenchmark`
(`@Threads(8)`/`@Threads(32)`, 500-row shared H2 table, 80/20 read/write mix + pure
read/scan variants) to answer the concrete question: **why does data arrive late in a
dashboard-style UI under real concurrent load?** — and applies the three biggest fixes the
round-1 report had left REPORT-ONLY.

### The dashboard-latency root cause, found and confirmed by JFR

| Benchmark | Threads | mean | p99 | p99.9 | p99.99 | p100 (max) |
|---|---|---|---|---|---|---|
| `dashboardMixed8` | 8 (< pool size) | 0.048ms | 0.092ms | 0.170ms | 4.38ms | 5.03ms |
| `dashboardMixed32` | 32 (> pool size) | 0.215ms | 0.974ms | **23.5ms** | **62.0ms** | **125.0ms** |
| `concurrentReadOnly32` | 32 | 0.193ms | 0.839ms | 24.4ms | 73.9ms | 136.6ms |
| `concurrentScanAll32` | 32 | 1.212ms | 11.6ms | 58.6ms | 190.3ms | 196.3ms |

At 8 concurrent clients (under Hikari's `maxPoolSize=10`) the tail is tight — p99.9 stays
under 0.2ms. At 32 clients (over the pool size) the **p99.9+ tail explodes 100-1000x** even
though the median barely moves (0.044→0.079ms) — classic connection-pool-exhaustion signature:
most requests are fast, a growing minority queue for a free connection and pay tens of
milliseconds waiting.

**JFR confirms it directly**, not just inferred from the shape of the curve: `jdk.ThreadPark`
samples on the `dashboardMixed8`/32-thread run show worker threads parked in
`java.util.concurrent.SynchronousQueue$Transferer.xferLifo` under `HikariPool-1` frames —
Hikari's internal connection-handoff queue, i.e. threads genuinely blocked waiting for a
physical connection to free up. Every `SqlConnection.executeAsync` call borrows-and-returns a
connection per query (`SqlConnection.java:114-118`), so pool size is a hard ceiling on
in-flight query concurrency regardless of CPU headroom.

**This is very likely exactly the "dashboard data arrives after…" symptom**: N dashboard
widgets firing concurrent queries against a pool sized for a lower concurrency assumption. It
is **not a sql4j code defect** — it's a configuration/capacity mismatch. Action: size
`SQL4J.connect()....pool(t -> t.maxPoolSize(...))` (`SQL4J.java`, `HikariTuningBuilder`) to the
real expected concurrent-query count for that workload, not the driver default (10). No code
change proposed here — this is an operational tuning finding, reproducible on demand via
`DashboardLoadBenchmark` with a different `maxPoolSize` in its `@Setup`.

### Applied this round (verified: full `sql4j`+`sql4j-rpc` test suite green, before/after JMH)

1. **RPC batching — real fix, not report-only anymore.** Added `DbBatchMutationRequest`/
   `DbBatchMutationResponse` packets, `RpcCodec.encodeBatch/decodeBatch/encodeBatchResult/
   decodeBatchResult`, `SqlGatewayHandler.handleBatchMutation` (one JDBC
   `addBatch()`/`executeBatch()` sequence per gateway call), and wired
   `RpcPreparedStatement.addBatch()`/`executeBatch()` for real (previously a no-op / throw) via
   a new `RpcDispatcher.dispatchBatch` method — defaults to N sequential `dispatch()` calls for
   any dispatcher that doesn't override it (backward compatible), and `RemoteSqlConnection` now
   overrides it to send one `DbBatchMutationRequest`. New tests:
   `sql4j-rpc/src/test/.../RpcBatchRoundtripTest.java` (3 cases: real batch roundtrip, empty
   batch is a no-dispatch no-op, default-fallback correctness for dispatch()-only
   implementers).
   **Measured:** `RpcRoundtripBenchmark.batchInsert` vs `.sequentialInserts` at the same `rows`:
   0.032ms vs 0.069ms @50 rows (**2.2x**), 0.132ms vs 0.280ms @200 rows (**2.1x**) — consistent
   with collapsing N roundtrips into 1, matching the round-1 JFR allocation evidence (codec/
   buffer allocation sites that ran once per row now run once per batch).
2. **`SelectJoin` O(n·m) → O(1) identity-map collapse.** Replaced the linear scan over each
   entity's live relation collection (`SelectJoin.java`, was `:247-252`) with a
   `Map<Table.Relation, Map<sourcePk, Set<targetPk>>>` dedup check, O(1) instead of O(m) per
   row. Verified against `SQL4JFullTest`'s existing 2-tasks-under-one-project hierarchy
   assertion (unchanged pass) — no dedicated ordering regression test was added since
   `LinkedHashMap`/insertion-order semantics are unchanged, only the membership check.
   `eagerJoin` numbers are within round-1 noise at the tested sizes (5/20/50 projects — too
   small for O(m) to have dominated), consistent with round 1's own conclusion that this
   wasn't yet the bottleneck at these sizes; the fix removes the *risk* at larger fan-outs
   without a measured win at current scale.
3. **App-layer `PreparedStatement` cache — investigated, NOT applied.** `SqlConnection.
   executeAsync` borrows a fresh JDBC `Connection` per query and returns it immediately
   (`try (final Connection con = connection())`, `SqlConnection.java:114-118`) — a real
   cross-query PS cache needs to survive across separate borrows of the *same physical*
   pooled connection, which means hooking Hikari's connection lifecycle (customizer/eviction
   listener) and intercepting `PreparedStatement.close()` to not actually close cached
   statements. That's a real architecture change (moving from borrow-per-query to a
   session-aware or connection-decorator model), not a safe/local fix, and round 1's own
   `BatchChunkSizeBenchmark` evidence was inconclusive on H2 (flat across chunk sizes) — no
   driver-caching-enabled dialect benchmark exists yet to justify the complexity. Left
   report-only, same as round 1, but now with the concrete architectural reason documented
   instead of just "needs more evidence."

### New benchmark inventory (this round)

- `DashboardLoadBenchmark` — `dashboardMixed8`/`32` (80/20 read/write), `concurrentReadOnly32`,
  `concurrentScanAll32`. `SampleTime` mode (captures the full latency distribution, not just
  the mean) specifically because tail latency, not average, is the dashboard symptom.
- `RpcRoundtripBenchmark.batchInsert` — added alongside the existing `sequentialInserts`.

### Reproduce round 2
```bash
./gradlew :util:persistence:sql4j-benchmarks:jmhJfr -Djmh.fork=1 -Djmh.wi=2 -Djmh.i=3
# JFR (dashboard 32-thread run): build/jfr-captures/dashboard-mixed8-load.jfr
jfr print --events jdk.ThreadPark build/jfr-captures/dashboard-mixed8-load.jfr | grep -A5 HikariPool
```

---

## Round 1 — single-threaded baseline

**Reproduce:**
```bash
./gradlew :util:persistence:sql4j-benchmarks:jmh                 # numbers only
./gradlew :util:persistence:sql4j-benchmarks:jmhJfr -Djmh.fork=1 # + build/sql4j-jmh.jfr
```
`jmh.fork`/`jmh.wi`/`jmh.i` system props tune fork/warmup/measurement counts (see the module's
`build.gradle.kts`). JFR overwrites one file per fork — capture one benchmark class at a time
for isolated recordings (see `build/jfr-captures/*.jfr` from this review's runs).

## Method

- 6 JMH benchmark classes, H2 in-memory, `Fork=1, Warmup=2×1s, Measurement=3×1s` (fast-CI
  settings — bump `-Djmh.wi`/`-Djmh.i` for publishable numbers, these are directional).
- JFR (`-XX:StartFlightRecording=...,settings=profile`) captured for the RPC path
  (`RpcRoundtripBenchmark`, 200-row sequential insert) and cross-checked against a second
  capture ending on the same benchmark — allocation-sample stacks (`jdk.ObjectAllocationSample`)
  are the primary evidence cited below; no lock contention (`jdk.JavaMonitorEnter`) samples
  were observed at this concurrency level (single-threaded benchmarks — see Known gaps).
- Every claim below is backed by either a JMH number (this run) or a file:line (code read
  directly, some cross-checked against JMH-generated mirror classes in
  `sql4j/build/generated/sources/annotationProcessor`).

### Raw JMH results (this run)

| Benchmark | Param | Score | Units |
|---|---|---|---|
| InsertThroughputBenchmark.singleInsert | batchSize=100/1000 | 83.6–84.5 | ops/ms |
| InsertThroughputBenchmark.batchInsert | batchSize=100 | 15.2 | ops/ms |
| InsertThroughputBenchmark.batchInsert | batchSize=1000 | 1.5–1.7 | ops/ms |
| BatchChunkSizeBenchmark.insertBatch | chunkSize=0/100/250/500 | 0.60–0.67 | ms/op (flat — see finding RPC-adjacent-1) |
| CacheHitVsMissBenchmark.cacheHit | — | ≈10⁻⁵ | ms/op |
| CacheHitVsMissBenchmark.cacheMiss | — | 0.012 | ms/op |
| DispatcherAccessBenchmark.generatedDispatcherSet | — | ≈10⁻⁶ | ms/op |
| DispatcherAccessBenchmark.reflectiveFieldSet | — | ≈10⁻⁵ | ms/op |
| HydrationBenchmark.selectByPk | rowCount=1/100/1000 | 0.007 (flat) | ms/op |
| HydrationBenchmark.selectAll | rowCount=1/100/1000 | 0.007 / 0.022–0.026 / 0.18–0.19 | ms/op |
| RelationJoinBenchmark.eagerJoin | projectCount=5/20/50 | 0.03 / 0.10 / 0.25–0.26 | ms/op |
| RelationJoinBenchmark.lazyNPlusOne | projectCount=5/20/50 | 0.05 / 0.16–0.17 / 0.47–0.60 | ms/op |
| RpcRoundtripBenchmark.selectRows | rows=1/50/200 | 0.001 / 0.005 / 0.017–0.018 | ms/op |
| RpcRoundtripBenchmark.sequentialInserts | rows=1/50/200 | 0.001–0.002 / 0.071–0.084 / 0.29–0.35 | ms/op |

## Findings — ranked by measured impact

### 1. [FIXED] `EntityCache` unbounded when `@Cacheable(maxSize<=0)` — correctness/memory
**File:** `sql4j/src/main/java/dev/luce/sql4j/impl/cache/EntityCache.java:15,82-85` (pre-fix).
`DEFAULT_MAX_SIZE=1000` was declared but never wired into the Caffeine builder — any entity
class using `@Cacheable` (default `maxSize()=-1`) or omitting `maxSize()` got an **unbounded**
per-class cache. `EntityCacheConcurrencyTest` never exercised this because its test entities
all specify `maxSize`. **Impact:** any long-lived process caching a high-cardinality entity
without an explicit `maxSize` leaks heap without bound.
**Fix applied:** `getCache()` now falls back to `DEFAULT_MAX_SIZE` instead of `-1`, so
`maximumSize(...)` is always set. Zero blast radius — every existing `@Cacheable` caller with
an explicit `maxSize` is unaffected; ones without get bounded instead of unbounded.

### 2. [REPORT-ONLY] RPC path has no batching — `sequentialInserts` scales linearly with blocking roundtrips
**File:** `sql4j-rpc/.../RpcPreparedStatement.java:166-167` (`addBatch` no-op, `executeBatch`
throws `UnsupportedOperationException`). **Evidence:** JMH — `sequentialInserts` goes
0.001ms (1 row) → 0.071–0.084ms (50 rows) → 0.29–0.35ms (200 rows), i.e. **linear in row
count** because each row is a full blocking `dispatch()` roundtrip
(`RemoteSqlConnection.java:65`, `future.get(25s)`). Contrast with in-process `insertBatch`
(`InsertThroughputBenchmark.batchInsert`, 1000 rows) at 1.5–1.7 ops/ms = ~0.6ms for the whole
batch in ONE JDBC roundtrip — the RPC path pays per-row where the core path pays once.
**JFR:** allocation samples on the same benchmark show `RpcPreparedStatement.executeUpdate`,
`RpcCodec.{encodeQuery,decodeQuery,decodeMutation,toBytes}`, `PacketBuffer.<init>`, and
`AbstractBuffer.{readString,writeString}` as the top-9 allocating call sites (24-63 samples
each out of ~350 total `dev.sweety.*` samples) — every one of those runs **once per row**, not
once per batch, because there is no batch path to amortize them over.
**Fix proposal (not applied — blast radius: `SqlGatewayHandler`, `RpcCodec`, wire protocol
version):** add a real batch RPC (`DbBatchMutationRequest`, array-of-param-arrays payload,
single gateway dispatch executing a JDBC `addBatch()/executeBatch()` under one `dispatch()`
call) and wire `RpcPreparedStatement.addBatch/executeBatch` to it. Any `insertBatch()` call
routed through `RemoteDatabase` today silently degrades to N roundtrips with no error —
worth flagging to anything currently relying on `server/core`'s SQL-gateway path for bulk
writes.

### 3. [FIXED] Query-thread-visible interceptor lists were plain `ArrayList` — correctness/race
**File:** `sql4j/src/main/java/dev/luce/sql4j/api/connection/SqlConnection.java:39` and
`sql4j/src/main/java/dev/luce/sql4j/impl/Database.java:48` (pre-fix). `interceptors` is read
on every query execution (`SqlRunner.execute(con, query, interceptors)`,
`SqlConnection.java:118`) from whatever thread borrowed the connection, while
`addInterceptor()` (`SqlConnection.java:129`, `Database.java:215`) can be called concurrently
from another thread with no synchronization — a classic unsynchronized-mutation-during-read
hazard (`ConcurrentModificationException` or a torn read, depending on timing).
**Fix applied:** both now use `CopyOnWriteArrayList` — reads never see a torn/concurrently-
mutated list, writes (rare — interceptors are typically registered at startup, not per-query)
pay a copy-on-add cost that's irrelevant at that call frequency. Zero blast radius: same
`List<QueryInterceptor>` interface, same iteration semantics.

### 4. [FIXED] `EntityCache.enabled` non-volatile — visibility bug
**File:** `EntityCache.java:22` (pre-fix). `setEnabled()`/`isEnabled()` cross threads (cache
toggled from one thread, read on query-execution threads) with a plain `boolean` — no
guaranteed visibility of a toggle-off across threads without a memory barrier.
**Fix applied:** `volatile boolean enabled`. Zero blast radius, no API change.

### 5. [FIXED] `Table.initialize()` unconditional `System.err` debug prints
**File:** `sql4j/src/main/java/dev/luce/sql4j/api/obj/Table.java:237,245` (pre-fix,
line numbers shifted after fix). Printed `[DEBUG] TABLE ... found AutoInc column` /
`NO AutoInc column found` on every table's first initialization, unconditionally, on every
process boot — pure log noise, no perf impact (init is one-time/CAS-guarded) but pollutes
stderr on every consumer (auth/admin/core all init tables at boot).
**Fix applied:** gated behind `Boolean.getBoolean("sql4j.debug")` (module has no logger
dependency — see `build.gradle.kts` — so a system-property flag was the zero-new-dependency
option). Set `-Dsql4j.debug=true` to restore the old always-on behavior for debugging.

### 6. [REPORT-ONLY] `SelectJoin.mapToHierarchy` — O(n·m) nested scan per relation, measured
**File:** `sql4j/src/main/java/dev/luce/sql4j/impl/query/SelectJoin.java:186-233,247-252`.
**Evidence:** JMH — `eagerJoin` vs `lazyNPlusOne` on the same `BenchUser→N BenchProject→5
BenchTask` fixture: eager wins at every size tested (0.03 vs 0.05ms @5 projects; 0.10 vs
0.16-0.17ms @20; 0.25-0.26 vs 0.47-0.60ms @50) — so the EAGER join path is still the right
default choice over N+1 despite its own O(n·m) identity-map collapse; the nested-scan cost
doesn't yet dominate at these sizes (25/100/250 total rows). **Not a regression to fix
urgently** — flagging because the growth curve of `eagerJoin` (0.03→0.10→0.25, roughly
linear-per-project rather than flat) is worth re-measuring at 500+ projects /
many-relations-deep hierarchies before assuming it stays ahead of N+1 indefinitely; the O(n·m)
nested scan (`:247-252`) is the candidate culprit if it stops scaling linearly.
**Fix proposal (not applied):** replace the linear nested-scan membership check in the
identity-map collapse with a `Map`-keyed lookup (O(1) instead of O(m)) — safe, local to
`SelectJoin`, no API change, but not exercised as risk-free by this review (no test currently
asserts join-result ordering that a Map-based collapse might perturb).

### 7. [REPORT-ONLY] No app-layer `PreparedStatement` pooling — confirmed flat/no-op at H2 batch chunk sizes
**File:** `SqlRunner.java:98`, `SelectEntity.java:471`, `InsertBatch.java:133` — every
execution calls `con.prepareStatement(sql)` fresh; relies entirely on driver-side caching
(off for SQLite/H2, on for MySQL/MariaDB/Postgres via `HikariConnectionProvider.java:87-105`).
**Evidence:** `BatchChunkSizeBenchmark.insertBatch` is flat across chunk sizes 0/100/250/500
(0.60–0.67ms, no statistically distinct trend within this run's error bars) — on H2, unchunked
already wins or ties every chunked variant, consistent with the missing driver-side PS cache
making a single `executeBatch()` no worse than several. **This benchmark does not by itself
prove PS-pooling would help** — it shows chunking doesn't matter on H2/SQLite specifically;
re-run against MySQL/MariaDB (where driver PS caching is enabled) before concluding whether
app-layer pooling is worth the complexity. Left report-only: no MySQL/MariaDB benchmark exists
yet in this module.

### 8. [REPORT-ONLY, low priority] `DispatcherAccessBenchmark` confirms cached-reflection field
access, not true reflection-free — as flagged pre-review
**File:** `sql4j-processor/.../SQL4JProcessor.java` `buildDispatcher`/mirror-gen — generated
`get_/set_` accessors call cached `Field.get/set`, not `VarHandle`/`MethodHandle`.
**Evidence:** JMH — `generatedDispatcherSet` (≈10⁻⁶ms) is measurably faster than
`reflectiveFieldSet` (≈10⁻⁵ms, ~10x), confirming the dispatcher switch + method-call
indirection IS worth its cost over ad-hoc `HashMap<String,Field>` reflection — but both are
in the "single-digit nanoseconds to low microseconds" range, i.e. this is not a hot-path
bottleneck at realistic entity field counts. VarHandle migration would likely shave more, but
isn't justified by current evidence — this cost is dwarfed by JDBC roundtrip cost (µs-ms range)
everywhere else in the benchmark suite. **No action recommended.**

### 9. [REPORT-ONLY] `SyntheticResultSet.findColumn` — linear scan, not exercised at width in this review
**File:** `sql4j-rpc/.../SyntheticResultSet.java:330-338`. Not directly benchmarked — none of
this review's RPC benchmarks hydrate wide entities over RPC (only `id/name/num`, 3 columns).
Flagged in pre-review exploration as O(cols) per label lookup; at 3 columns this is
negligible. **Left unmeasured** — if `server/core`'s SQL gateway is used for wide entities
(the `HydrationBenchmark.WideItem`-style, 14 columns) this should be re-benchmarked with an
RPC-path wide-entity case before prioritizing a fix.

## Known gaps (explicitly not covered this pass)
- **No multi-threaded contention data.** All benchmarks are single-threaded
  (`@Threads` unset = 1) — the flagged `CopyOnWriteArrayList`/`volatile` fixes above are
  correctness fixes for a real hazard identified by code reading, not validated under actual
  concurrent load in this pass (no `jdk.JavaMonitorEnter` samples were possible to observe
  with 1 thread). A follow-up `@Threads(N)` variant of `InsertThroughputBenchmark` would give
  real contention numbers.
- **Gateway saturation (`Semaphore(SQL_GW_MAX_INFLIGHT=200)` backpressure path) not load-tested**
  — `SqlGatewayHandler.java:33-37,50-54` confirmed by code read only; no test drove >200
  concurrent in-flight requests to observe the OVERLOAD response under this review's time
  budget.
- **MySQL/MariaDB/PostgreSQL not benchmarked** — only H2 in-memory. Finding 7 above
  specifically needs a driver-PS-caching-enabled dialect to be conclusive.
- JFR captured allocation-sample evidence only for the RPC path (highest-value target per the
  no-batching finding); CPU hot-method (`jdk.ExecutionSample`) counts were too sparse at this
  run's duration (short warmup/measurement) to rank method-level CPU cost with confidence —
  re-run with `-Djmh.wi=5 -Djmh.i=10` and a longer per-iteration time for a CPU-flamegraph-
  quality capture if that level of detail is needed.

## Applied this pass (safe, zero-blast-radius)
1. `EntityCache` — bounded default cache size (`DEFAULT_MAX_SIZE` wired in).
2. `SqlConnection`/`Database` — interceptor lists → `CopyOnWriteArrayList`.
3. `EntityCache.enabled` → `volatile`.
4. `Table.initialize()` debug prints → gated behind `-Dsql4j.debug=true`.

All four verified via `./gradlew :util:persistence:sql4j:test :util:persistence:sql4j-rpc:test`
(green, no regressions) plus a full JMH re-run (`CacheHitVsMissBenchmark` numbers unchanged
within noise — expected, `BenchItem` already declares an explicit `@Cacheable(maxSize=10_000)`
so it was never on the unbounded path).

## Not applied (needs a separate approved plan)
- App-layer PreparedStatement pooling (finding 7) — architecture conflict documented in Round 2
  (needs moving off the borrow-per-query connection model); MySQL/MariaDB benchmark evidence
  still absent too.
- `SyntheticResultSet.findColumn` index (finding 9) — needs a wide-entity RPC benchmark first.

**Applied in Round 2** (see above): RPC batch API (finding 2, was report-only) and `SelectJoin`
O(n·m)→O(1) collapse (finding 6, was report-only) — both landed, tested, and re-benchmarked.
