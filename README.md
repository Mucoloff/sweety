# Sweety - panoramica codebase

## Cos e questo progetto
Sweety e una codebase Java/Kotlin multi-modulo (Gradle) orientata a componenti riusabili per:
- networking (Netty + packet system + load balancer),
- versioning/update delivery (launcher client + update server + protocollo),
- plugin/extension loading,
- utilita trasversali (logger, thread, persistenza, math, vector, ecc.),
- integrazioni esterne (Spotify, Microsoft auth),
- supporto Minecraft (network ping + versioning helpers).

Il repository contiene anche un modulo `project/` usato come sandbox di test/uso esempi.

## Struttura ad alto livello

### 1) `feature/`
Area delle feature core applicative.

- `feature:event`
  - `api`: contratti eventi/listener (`IEventSystem`, `Listener`, priorita/stato).
  - `impl`: implementazione `EventSystem` con subscribe/unsubscribe, dispatch pre/post, cancellazione e wrapping operazioni.

- `feature:module:extension`
  - `api`: modello base estensioni (`Extension`, `Toggleable`).
  - `common`: metadata/error handling (`ExtensionInfo`, eccezioni).
  - `manager`: caricamento JAR dinamici (`ExtensionManager`, classloader dedicato, download da URL).

- `feature:module:versioning`
  - `protocol`: packet e modelli condivisi (`Handshake*`, `ReleasePacket`, `Version`, `Artifact`, `PacketRegistry`).
  - `client:launcher`: entrypoint `dev.sweety.launcher.MainLauncher`; gestisce handshake update, patch apply e bootstrap dell app.
  - `client:app`: app minimale di esempio (`AppMain`, `ExampleDiff`).
  - `update-server`: entrypoint `dev.sweety.versioning.server.MainServer`; espone API HTTP + canale Netty per release, patch, download e broadcast.

- `feature:asm-patch`
  - `core`: motore diff/patch ASM, formati bin/json, validazione hash, integrazione git patch.
  - `generator`: generazione patch.
  - `applier`: applicazione patch su artifact.

- `feature:service`
  - `api`: registry/provider/service keys.
  - `impl`: `ServiceManager` e `TypedServiceManager` per DI/service-locator leggero con lifecycle.

### 2) `network/`
Infrastruttura di comunicazione.

- `network:netty`
  - astrazioni client/server,
  - codec packet buffer,
  - registry packet,
  - gestione transazioni e reconnect.

- `network:netty-loadbalancer`
  - architettura a 3 sottoprogetti: `packet` (contratti/metriche/queue), `server` (hub LB), `backend` (nodi worker),
  - coda ingress con backpressure (`LB_MAX_PENDING`) e path opzionale ad alte prestazioni via LMAX Disruptor,
  - routing transaction-aware con timeout (`HUB_REQUEST_TIMEOUT_SECONDS`) e reorder risposte per connessione,
  - pool backend dinamico con recovery ctx/canale, cache su reconnect e selezione round-robin + limiti in-flight,
  - scoring nodo basato su metriche runtime (CPU/RAM/open files/thread pressure/system load + latenza/banda),
  - supporto fire-and-forget per bypassare la coda quando non e richiesta una risposta.

- `network:netty-saas`
  - stack SaaS completo su 3 sottoprogetti: `packet` (protocollo/config bootstrap), `hub` (orchestrazione), `service` (runtime servizi),
  - `ServiceHub` estende il load balancer e instrada per `ServiceType` (`ForwardData.receiverId -> ServicesPool.get(type)`),
  - handshake di identificazione tra nodo e hub (`SystemConnectionTransaction`) con supporto a placeholder/promozione nodo,
  - sicurezza in pipeline: rate-limit per IP (`HUB_RATE_LIMIT_MAX_CONN`, `HUB_RATE_LIMIT_WINDOW_SECONDS`) + whitelist IP dinamica,
  - health HTTP integrata (`HubHealthServer`): `/api/health`, `/api/health/ready`, `/api/health/{TYPE}` su `HUB_HEALTH_PORT`,
  - monitoring applicativo: i servizi inviano metriche periodiche via `MonitoringMetricReportTransaction`.

### 3) `util/`
Librerie trasversali condivise dalla maggior parte dei moduli.

- logging: `util:logger` (`SimpleLogger`, backend console/file/SLF4J/JUL),
- data/math/filter: `util:data`, `util:math`, `util:filter`,
- file/system/time/thread/vector/color/animation,
- eccezioni: `util:exception`,
- persistenza:
  - `util:persistence:configuration` (json/yaml/toml/binary config),
  - `util:persistence:ormlite-util`,
  - `util:persistence:sql4j` (DSL/query layer + dialect + connection provider),
- processor:
  - `event-processor`,
  - `packet-processor`,
  - `record-getter-processor`,
  - `intellij-plugin`.

### 4) `hook/`
Integrazioni esterne.

- `hook:spotify`: client/API wrapper Spotify con OAuth e modelli playback/track/device.
- `hook:microsoft-auth`: autenticazione account Microsoft + storage/config locale.

### 5) `minecraft/`
Componenti dedicati a utility Minecraft.

- `minecraft:network`: ping protocol utilities e packet stream reader/writer.
- `minecraft:version`: confronto/version helpers.
- `minecraft` root: utility base (es. `ChatColor`).
- `minecraft:plugin`: modulo previsto ma senza sorgenti principali evidenti.

### 6) `project/` (sandbox)
Modulo con classi demo/probe (`TestClient`, `TestServer`, test packet/sql/mail, esempi load balancer).

Nota: questo modulo sembra disallineato con la struttura moderna dei path Gradle (dipendenze legacy in `project/build.gradle`), quindi va considerato piu come area sperimentale che come prodotto core.

## Build system e stack
- Build: Gradle multi-project.
- Linguaggi: Java + Kotlin.
- Java toolchain configurata: `24` (root `build.gradle.kts`).
- Plugin principali: `java`, `org.jetbrains.kotlin.jvm`, `maven-publish` (con pubblicazione per subproject).
- Dipendenze comuni (root): Netty, Gson, SnakeYAML, TOML parser, ASM, Guava, SLF4J, FastUtil, Caffeine, Apache Commons, Manifold, JUnit 5.

## Entry points principali
- Launcher client update:
  - `feature/module/versioning/client/launcher/src/main/java/dev/sweety/launcher/MainLauncher.java`
- Update server:
  - `feature/module/versioning/update-server/src/main/java/dev/sweety/versioning/server/MainServer.java`
- App client esempio:
  - `feature/module/versioning/client/app/src/main/java/dev/sweety/app/AppMain.java`
- Bootstrap config/service SaaS (demo):
  - `network/netty-saas/packet/src/main/java/dev/sweety/saas/service/Main.java`

## Stato qualitativo attuale (quick take)
- Architettura modulare ampia e ricca di componenti riusabili.
- Forte focus su networking e pipeline di update/versioning.
- Molti moduli hanno codice reale in `src/main`; invece risultano pochi/no test in `src/test`.
- Presenti ancora alcune aree placeholder o legacy (`minecraft:plugin`, `project/`).

## Comandi utili
```bash
./gradlew buildAll
./gradlew publishAll
./gradlew :feature:module:versioning:update-server:run
./gradlew :feature:module:versioning:client:launcher:run
```