# 📘 Code Style Guide (Java + Kotlin)

## 🎯 Obiettivi
- Codice leggibile, prevedibile e type-safe al 100%
- Ridurre bug a compile-time (null safety rigorosa, stato inconsistente)
- Sfruttare le feature moderne del linguaggio (JSpecify, Manifold, Java 21+, Kotlin)
- Evitare overengineering e astrazioni inutili
- Ottimizzare solo quando serve davvero e dove misurato

---

# 1. Creazione oggetti

## Regola
Preferire **factory methods** ai costruttori pubblici.

### ✔️ Esempio
```java
public final class User {
  private final String name;

  private User(String name) {
    this.name = name;
  }

  public static User of(String name) {
    if (name.isBlank()) {
      throw new IllegalArgumentException("Invalid name");
    }
    return new User(name);
  }
}
```

## Linee guida
- Costruttori → `private`
- Factory → nomi chiari:
  - `of(...)`
  - `from(...)`
  - `create(...)`
- Validazione **solo in factory / entry point**

### ✔️ Value objects → `record`
Usare `record` per oggetti immutabili senza logica. `equals`, `hashCode`, `toString` gratis.

```java
public record Point(int x, int y) {}
```

❌ Non usare `record` se il tipo ha stato mutabile o logica di business non banale.

---

# 2. Null Safety (JSpecify Standard)

## Il modello: Non-Null di Default (Kotlin Parity)
Adottare lo standard moderno **JSpecify** (`org.jspecify:jspecify:1.0.0`).
Tutto il codice Java del progetto adotta la semantica **Non-Null by Default**: ogni riferimento a un tipo `Type` è garantito non-null dal compilatore e dagli strumenti di analisi statica, esattamente come in Kotlin.

### Regole Fondamentali
- ✔️ **Dichiarare `@NullMarked` a livello di package** tramite file `package-info.java` per ogni package Java:
  ```java
  @org.jspecify.annotations.NullMarked
  package dev.luce.protocol.auth.crypto;
  ```
- ✔️ **`Type` = Non-Null**: In un contesto `@NullMarked`, qualsiasi tipo dichiarato (`String`, `Session`, `UUID`) **non può mai essere null**.
- ✔️ **`@Nullable` solo se esplicito**: Usare `org.jspecify.annotations.Nullable` **esclusivamente** dove l'assenza di un valore è un esito semantico lecito e gestito (`String?` in Kotlin).
  ```java
  public @Nullable Session findSession(String sessionId) { ... }
  ```
- ❌ **Vietato usare annotazioni legacy frammentate**: Non usare `@NotNull`, `@NonNull` di JetBrains, Lombok, Spring, Checker Framework o Android. L'unico standard ammesso è **JSpecify**.
- ❌ **Vietato passare `null` nelle API pubbliche** salvo dove il parametro è esplicitamente marcato `@Nullable`.
- ✔️ **`Optional` solo come return type** quando l'assenza di risultato è un outcome normale di una query/lookup.

### Boundary vs Core Checks (Zero Duplicazioni)
- **All'interno del core `@NullMarked`**: NON duplicare controlli difensivi manuali (`if (x == null)` o `Objects.requireNonNull(x)`). Il compilatore, NullAway e l'IDE garantiscono già che `x` non sia null.
- **Ai Boundary esterni (API untrusted / Network / Deserializzazione)**: Validare subito con fail-fast:
  ```java
  public static Foo of(String name) {
    Objects.requireNonNull(name, "name cannot be null");
    return new Foo(name);
  }
  ```

### ❌ Optional anti-pattern
- `Optional` come **parametro** di metodo → usa overload o `@Nullable`
- `Optional` come **campo** di classe/record → non ha senso semantico, usa `@Nullable`
- `Optional` in collezioni (`List<Optional<T>>`) → filtra a monte
- `Optional` solo come **return type**

```java
// ❌
void process(Optional<String> name) { ... }

// ✔️
void process(@Nullable String name) { ... }
void process() { process(null); }
```

---

# 3. Strutture dati

## Regola generale
Usa la struttura giusta per il problema, non "map ovunque".

| Uso                     | Struttura              | Note                           |
|-------------------------|------------------------|--------------------------------|
| accesso per chiave      | `HashMap`              | O(1) medio                     |
| ordinamento             | `TreeMap` / `TreeSet`  | O(log n)                       |
| lista sequenziale       | `ArrayList`            | Ideale per iterazione sequenziale |
| set senza duplicati     | `HashSet`              | O(1) medio                     |
| concorrenza read-heavy  | `CopyOnWriteArrayList` | Read senza lock, write copiano |
| concorrenza write-heavy | `ConcurrentHashMap`    | Partizionamento a bucket       |

### Getter di collezioni → sempre difensivo
Non esporre mai la collezione interna direttamente.

```java
// ✔️
public List<String> items() { return List.copyOf(items); }

// ❌
public List<String> items() { return items; }
```

### ❌ Anti-pattern
- usare `Map` quando serve una lista
- usare `TreeSet` senza bisogno di ordinamento
- esporre `List` mutabile come campo interno

---

# 4. Immutabilità

## Regole
- Preferire sempre oggetti immutabili
- In Java: campi `private final` e `record`
- In Kotlin: proprietà `val` e `data class`

### ✔️ Esempio Kotlin
```kotlin
data class User(val name: String)
```

---

# 5. Pooling oggetti

## Regola forte
❌ NON usare pooling di default. La JVM e la Garbage Collection moderna (ZGC / G1) allocano ed eliminano oggetti a ciclo di vita breve a costo quasi zero.

## Usalo solo se:
- Oggetti molto pesanti con risorse native allocate (off-heap buffer, DB connection, socket worker)
- Il profiling (async-profiler, JFR) dimostra concreta saturazione GC o allocazioni in hot-loop a 20+ TPS
- Buffer grandi e riciclabili in pipeline di rete ad alto throughput (es. Netty `ByteBufAllocator.DEFAULT`)

## Alternative migliori
- Immutabilità
- Riuso locale su stack
- Flyweight pattern

---

# 6. Validazione

## Regole
- Solo nelle factory / boundary entry points
- No controlli duplicati nel core
- Fail fast

### ✔️ Esempio
```java
if (x <= 0) throw new IllegalArgumentException("Value must be positive: " + x);
```

---

# 7. Design classi

## Regole
- Classi `final` dove possibile
- Niente setter (preferire immutabilità o builder con ritorno di nuova istanza)
- Responsabilità singola (Single Responsibility)

---

# 8. Performance

## Regola chiave
> Non ottimizzare senza misurare. Usa async-profiler o JMH prima di cambiare design.

## Linee guida

### String Interpolation & Concatenation
- ❌ **Vietato** concatenare con `+` dentro loop o hot-path.
- ✔️ **Manifold Strings** (se attivo nel modulo): Usare string interpolation nativa `$var` e `${expr}`:
  ```java
  // ✔️ Con Manifold: massima leggibilità, zero overhead
  String msg = "[$level][$name] $text";
  ```
- ✔️ **Plain Java**: Usare `String.formatted(...)` per log/errori fuori dall'hot path, o `StringBuilder` pre-dimensionato per loop ad alte prestazioni.

### Lazy evaluation nei log
Se un argomento è costoso da costruire, passare un `Supplier` invece del valore calcolato:
```java
// ❌ — sempre valutato
logger.debug("State: " + heavyCompute());

// ✔️ — valutato solo se il livello di log è attivo
logger.debug(() -> "State: " + heavyCompute());
```

### Pattern matching (`switch` / `instanceof`)
Preferire `switch` con pattern matching a lunghe catene di `if-instanceof`:
```java
return switch (obj) {
    case String s  -> s;
    case Integer i -> i.toString();
    default        -> obj.toString();
};
```

---

# 9. Concorrenza

## Regole
- `volatile` solo per flag di visibilità (un singolo campo booleano o enum, no compound actions)
- `AtomicReference` per swap atomico di oggetti di stato/configurazione
- `ConcurrentHashMap.computeIfAbsent` per lazy init thread-safe senza lock espliciti
- `CopyOnWriteArrayList` per collezioni read-heavy con registrazioni listener rare
- ❌ `synchronized` solo se strettamente necessario — preferire primitive `java.util.concurrent`

### ✔️ Swap atomico
```java
private final AtomicReference<Config> config = new AtomicReference<>(defaultConfig);
config.set(newConfig);
```

### ❌ Anti-pattern
- `volatile double score; score += x;` — race condition, non atomico
- `volatile` su un campo e poi operazioni compound su di esso
- `synchronized` su oggetti pubblici, stringhe o istanze esterne

---

# 10. Java + Kotlin insieme

## Integrazione Bidirezionale Fluida
Il workspace adotta un'architettura ibrida pragmatica: Kotlin per modelli dati, script e componenti ad alta densità sintattica; Java 21+ per networking ad alte prestazioni, runtime core e protocolli Netty.

### Parità di Null Safety tramite JSpecify
- I moduli Java marcati con `@NullMarked` vengono interpretati dal compilatore Kotlin **senza generare platform types (`Type!`)**.
- Un metodo Java `public Session getSession()` in package `@NullMarked` è visto da Kotlin rigorosamente come `Session` (non nullable).
- Un metodo Java `public @Nullable Session findSession()` è visto da Kotlin come `Session?`.
- Viceversa, i tipi Kotlin `Type` e `Type?` sono immediatamente consumabili da Java rispettando il medesimo contratto.

### Modelli Dati & Entità Persistence
- Preferire **Kotlin `data class`** per entità DB (`sql4j`), DTO di protocollo e payload di configurazione complessi: generano automaticamente `copy()`, component destructors, getters/setters JVM e garantiscono compile-time null safety nativa.

### Estensioni e Metodi Fluent
- Sfruttare sia le Kotlin Extension Functions che le Manifold Extension Methods per arricchire API esterne in modo coerente e pulito.

---

# 11. Manifold Framework: Analisi, Linee Guida e Matrice di Adozione

[Manifold](http://manifold.systems) estende il compilatore Java (`javac`) tramite compiler plugin senza generare build steps intermedi. Nel nostro ecosistema le feature di Manifold sono categorizzate e regolamentate come segue:

## Matrice di Adozione

| Feature | Modulo / Dipendenza | Stato | Utilizzo nel Progetto |
| :--- | :--- | :--- | :--- |
| **Operator Overloading** | `manifold-ext` | ✔️ **CORE APPROVED** | Vettori (`Vec2`, `Vec3`, `Matrix`), math (`BigDecimal`), indicizzazione con parentesi quadre `[]`, range e operatori relazionali. |
| **Extension Methods** | `manifold-ext` | ✔️ **CORE APPROVED** | Estensione fluida di classi standard (`ByteBuf`, `String`, `Path`) senza verbose classi wrapper. |
| **String Interpolation** | `manifold-strings` | ✔️ **CORE APPROVED** | Sintassi `$var` e `${expr}` al posto di concatenazioni e template verbosi. |
| **Checked Exceptions** | `manifold-exceptions` | ✔️ **CORE APPROVED** | Eliminazione di `try/catch` boilerplate dentro functional interfaces e Streams. |
| **Tuples & Multi-Return** | `manifold-tuple` | ✔️ **CORE APPROVED** | Tuple anonime e nominali per ritorni multipli leggeri in metodi interni privati. |
| **Preprocessor** | `manifold-preprocessor` | ✔️ **FEATURE SPECIFIC** | Direttive `#if`, `#elif` per supportare molteplici versioni di target (Paper/Fabric, protocolli Minecraft, flag `#if DEBUG`). |
| **Type-Safe JSON/YAML** | `manifold-json`, `manifold-yaml` | ✔️ **FEATURE SPECIFIC** | Parsing e typing da schema senza generatori di classi esterni per configurazioni o API esterne. |
| **Structural Typing (`@Structural`)**| `manifold-ext` | ✔️ **ADAPTER ONLY** | Duck-typing type-safe per adattare classi terze non modificabili a nostre interfacce. |
| **Type-Safe Reflection (`@Jailbreak`)**| `manifold-ext` | ⚠️ **TESTS ONLY** | **Ammesso solo in `src/test`** per testare edge case interni senza reflection runtime manuale. **Vietato in `src/main`**. |
| **Properties (`@var`, `@val`)** | `manifold-props` | 🟡 **OPZIONALE** | Utile in DTO Java pure; per entità e DTO preferire `record` o Kotlin data classes. |
| **Units & Science** | `manifold-science` | 🟡 **OPZIONALE** | Indicato per motori fisici, calcolo tick loop e tempo (`50.ms`, `20.tps`). |
| **SQL / Templates (ManTL)** | `manifold-sql`, `templates` | ❌ **NON ADOTTATO** | Nel progetto usiamo già `sql4j` per il database e `jte` per il rendering HTML. |

---

## Dettaglio Feature Manifold Adottate

### 1. Operator Overloading (`manifold-ext`)
Consente di implementare operatori aritmetici, indicizzati e relazionali tramite convenzioni di metodi standard:
- `plus(T)`, `minus(T)`, `times(T)`, `div(T)`, `rem(T)` → `+`, `-`, `*`, `/`, `%`
- `compareTo(T)` → `<`, `>`, `<=`, `>=`
- `get(K)` e `set(K, V)` → indicizzazione `obj[k] = v`
- `inc()` e `dec()` → `++`, `--`

```java
// ✔️ Esempio: Vettori e Calcolo Matematico
public record Vec3(double x, double y, double z) {
  public Vec3 plus(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
  public Vec3 times(double s) { return new Vec3(x * s, y * s, z * s); }
}

Vec3 pos = new Vec3(1, 2, 3);
Vec3 next = (pos + new Vec3(0, 1, 0)) * 2.0; // Sintassi pulita nativa
```

### 2. Extension Methods (`manifold-ext`)
Permette di estendere tipi esistenti senza ereditarietà o classi `*Utils` dispersive:
- Collocare le estensioni in package dedicati `dev.<progetto>.extensions`.
- Naming classe: `<TargetClass>Extensions` (es. `ByteBufExtensions`).
- Metodi `public static` con il primo parametro marcato `@This`.

```java
package dev.luce.network.extensions;

import io.netty.buffer.ByteBuf;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;

@Extension
public final class ByteBufExtensions {
  public static int readVarInt(@This ByteBuf buf) {
    int value = 0, position = 0;
    byte currentByte;
    while (true) {
      currentByte = buf.readByte();
      value |= (currentByte & 0x7F) << position;
      if ((currentByte & 0x80) == 0) break;
      position += 7;
    }
    return value;
  }
}

// Uso diretto su qualsiasi istanza:
int length = buf.readVarInt();
```

### 3. String Interpolation (`manifold-strings`)
Evita la verbosità di `String.formatted` o concatenazioni `+`:
```java
String user = "Alice";
int score = 42;
logger.info("Player $user achieved score: ${score * 10}");
```

### 4. Gestione Eccezioni nei Lambdas (`manifold-exceptions`)
Tratta le eccezioni checked come in Kotlin, eliminando wrapper superflui nei functional streams:
```java
// ✔️ Con Manifold: nessun try-catch wrap obbligatorio dentro .map()
List<URI> uris = hosts.stream()
    .map(URI::create)
    .toList();
```

### 5. Tuple ed Espressioni di Ritorno Multiplo (`manifold-tuple`)
Usare per aggregazioni temporanee private senza dover inquinare il package con record fittizi:
```java
var result = (status: 200, message: "OK", timestamp: System.currentTimeMillis());
System.out.println("Status: " + result.status);
```

### 6. Test-Only Type-Safe Reflection (`@Jailbreak`)
- **Regola Rigida**: Ammesso **esclusivamente** in `src/test/java`.
- Sostituisce i fragili `field.setAccessible(true)` per testare stati privati o iniettare failure scenarios:
```java
// Solo in src/test!
@Test
void testInternalCounterReset() {
  @Jailbreak Session session = new Session("alice");
  session.internalErrorCount = 5; // Accesso diretto type-safe al campo privato
  session.reset();
  assertEquals(0, session.internalErrorCount);
}
```

---

# 12. Anti-pattern da evitare

- Factory ovunque senza motivo reale
- Pooling inutile su heap memory ordinaria
- Controlli null duplicati nel core (usare `@NullMarked`)
- Uso scorretto delle strutture dati (`Map<K, Boolean>` invece di `Set<K>`)
- Ottimizzazioni premature senza profiling
- Getter che espongono collezioni mutabili interne
- String concatenation in loop ad alte frequenze
- `@Jailbreak` o Reflection diretta nel codice di produzione (`src/main`)

---

# 13. API / Implementation Style

## Interfacce
- Nome semplice e semantico (es. `Event`, `Logger`, `PacketCodec`)
- Definiscono solo il contratto puro
- No prefissi arcaici tipo `I` (es. no `ILogger`)

## Implementazioni
- Nome descrittivo (`FileLogger`, `EpollPacketCodec`)
- Evitare il suffisso generico `*Impl` a meno di implementazioni base astratte

## Factory
- Preferire factory statiche nell'interfaccia per casi semplici
- Per logica complessa → factory o builder dedicato

## Gerarchie
- Usare `sealed` per domini chiusi e finiti a compile-time (pattern matching completo)
- Usare `abstract class` per logica condivisa
- Evitare gerarchie profonde di ereditarietà (favorire composizione)

---

# 14. IO Files

## File I/O

### Scrittura
- Usare sempre stream (no array `byte[]` in memoria per file grandi)
- Non scrivere mai direttamente sul file finale di destinazione
- Usare il pattern sicuro a 3 passi:
  1. Write su file temporaneo (`.tmp`)
  2. `fsync` sul canale
  3. Atomic rename / move sul file finale

### Lettura
- Stream o buffer dedicati per file grandi
- `Files.readAllBytes` ammesso solo per piccoli manifest/header garantiti (< 64 KB)

---

# 15. Reflection vs Annotation Processing

## Reflection Runtime
- Usare solo ai boundary esterni estremi (plugin terzi, integrazioni legacy)
- **Severamente vietata** nel core business e nei loop di rete
- Nessuna manipolazione di campi privati in `src/main`

## Annotation Processing / KSP / Manifold Metaprogramming
- Preferire sempre code generation a compile-time (Kapt, KSP, Java Annotation Processors)
- Elimina l'overhead di reflection a runtime
- Garantisce type-safety e feedback immediato dal compilatore

---

# 16. Exception Handling

## Regole
- ❌ Mai swalloware le eccezioni: `catch (Exception ignored) {}` è severamente vietato
- ✔️ Catch su eccezione specifica, mai catturare `Throwable` o `Exception` nuda
- ✔️ Wrap con causa: `throw new RuntimeException("operation failed", cause)`
- ✔️ Ai top-level loop (Netty pipeline, tick executor) loggare lo stack trace completo e continuare con spiegazione esplicita a commento

```java
// ❌ — perde la causa
} catch (IOException e) { throw new RuntimeException(e.getMessage()); }

// ✔️ — preserva la catena delle eccezioni
} catch (IOException e) { throw new StorageException("Failed to persist session", e); }
```

---

# 17. Resource Management

## Regole
- ✔️ Usare sempre `try-with-resources` per qualsiasi oggetto `AutoCloseable`
- ❌ Mai chiamare `.close()` manualmente dentro blocchi `finally` (error-prone)
- ✔️ Nelle catene di risorse, dichiarare ogni risorsa su riga separata nell'header del `try`

```java
try (var in = Files.newInputStream(path);
     var channel = Channels.newChannel(in)) {
  process(channel);
}
```

---

# 18. equals / hashCode / toString

## Regole
- Se si fa l'override di `equals`, è **obbligatorio** l'override di `hashCode`
- Entrambi devono dipendere **esattamente dagli stessi campi immutabili**
- Per value types semplici: usare `record` (Java) o `data class` (Kotlin) che li implementano gratis
- ❌ Mai confrontare `float` o `double` con `==` in `equals` → usare `Double.compare`

---

# 19. Architettura applicativa (MVC / MVP / MVVM)

Per moduli con interfaccia grafica (Desktop, IntelliJ plugin, Web front-end):
- **MVC**: Controller gestisce input utente, aggiorna il Model, la View osserva.
- **MVP**: Per View disaccoppiate e testabili in isolamento tramite interfaccia contrattuale.
- **MVVM**: Per framework con data-binding reattivo (StateFlow, Property).
- Moduli headless e server non usano MVC/MVP/MVVM, ma l'architettura modulare di §20.

---

# 20. Architettura modulare & Varianza (API / Implementation Split)

Per moduli backend, network, security e business core:

```
<module>/
├─ api/                        # CONTRATTI PUBBLICI
│  ├─ <category>/             # subpackage per feature/dominio
│  │  ├─ *Service / *Engine   # interfacce pure di contratto
│  │  ├─ *Record / *Config    # DTO e value objects immutabili
│  │  └─ annotation/          # annotazioni di marcatura
│  └─ SPI / Provider          # punti di estensione aperti
│
└─ impl/                       # LOGICA CONCRETA & WIRING
   ├─ <category>/              # STESSA suddivisione speculare di api/
   │  ├─ Default* / Base*      # implementazioni concrete dell'engine
   │  └─ internal/             # helper privati, algoritmi, codec
   └─ bootstrap / wiring       # lifecycle, setup, configurazione
```

### Regole Dipendenze
- `api/` → contratti puri, zero dipendenze pesanti.
- `impl/` → dipende da `api/` e implementa la logica concreta.
- Moduli esterni dipendono esclusivamente da `api/`.

---

# 21. DI e Wiring

## Default: Constructor Injection
Costruttori espliciti con passaggio delle dipendenze:
```java
public class DefaultSessionManager implements SessionManager {
  private final SessionRepository repository;
  private final EventPublisher publisher;

  public DefaultSessionManager(SessionRepository repository, EventPublisher publisher) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
  }
}
```

## Anti-pattern
- ❌ Service Locator globale (`Globals.get(...)`)
- ❌ Field injection con riflessione
- ❌ Singleton globale mutabile statico

---

# 22. Modello Plugin / Extension

Tre modalità di estensione del sistema — sceglierne una per dominio:
1. **File-loaded `Extension`**: JAR esterni con classloader isolato.
2. **Versioned `UpdateableExtension`**: Plugin caricabili con auto-update e migrazione.
3. **DI `@ServiceComponent`**: Componenti interni scoperti a compile-time o tramite SPI.

---

# 23. Mappe come insiemi

## Regola
Non usare `Map<K, Boolean>` quando l'unica semantica è la presenza della chiave. Preferire `Set<K>` con `Set#contains`.

```java
// ❌ Da evitare
Map<String, Boolean> activeUsers = new HashMap<>();
activeUsers.put(id, true);
if (Boolean.TRUE.equals(activeUsers.get(id))) { ... }

// ✔️ Preferire
Set<String> activeUsers = new HashSet<>();
activeUsers.add(id);
if (activeUsers.contains(id)) { ... }
```

---

# 24. Niente superclassi che fanno tutto

Evitare classi monolitiche che accumulano centinaia di responsabilità ortogonali (> 600 righe di codice o > 5 responsabilità distinte).
- Spezzare la logica in **Pipeline Passes** dedicati (es. `HeaderPass`, `PayloadPass`, `CryptoPass`).
- Ogni pass ha un singolo compito e un punto di ingresso statico chiaro.

---

# 25. SOLID

- **S — Single Responsibility**: Una classe ha una sola ragione per cambiare.
- **O — Open/Closed**: Aperto a estensione (tramite interfacce/SPI), chiuso a modifica (tramite `sealed` o API stabili).
- **L — Liskov Substitution**: Le sottoclassi/implementazioni devono rispettare il contratto dichiarato dall'interfaccia senza lanciare eccezioni impreviste.
- **I — Interface Segregation**: Interfacce piccole e focalizzate su un ruolo.
- **D — Dependency Inversion**: Dipendere da contratti e interfacce astratte (`api/`), mai da implementazioni concrete (`impl/`).

---

# 26. Test-first

## Regola
Prima di scrivere qualsiasi riga di logica di business, scrivere il test unitario che ne convalida l'esito.

## Cosa va testato con JUnit
- Algoritmi, crittografia, keystream, digest e token parsing
- Math, matrici, vettori e collisioni
- Codec di rete e serializzazione pacchetti
- Regole di business e autorizzazioni

## Cosa richiede verifica runtime reale
- Socket di rete live Netty con handshake completo
- Rendering GL e finestre native
- Mixin applicati a runtime nel bytecode del gioco

## Manifold `@Jailbreak` nei test
Nei test JUnit è ammesso l'uso di `@Jailbreak` per accedere a membri protetti/privati al fine di testare rami complessi e casi limite senza ricorrere a fragile Java Reflection.

---

# 27. Regola finale

> Codice semplice > codice "smart".
> La type-safety a compile-time vince sempre sulle verifiche manuali a runtime.
