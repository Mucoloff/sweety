# 📘 Code Style Guide (Java + Kotlin)

## 🎯 Obiettivi
- Codice leggibile e prevedibile
- Ridurre bug (null, stato inconsistente)
- Evitare overengineering
- Ottimizzare solo quando serve davvero

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
    if (name == null || name.isBlank()) {
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
- Validazione **solo in factory**

### ✔️ Value objects → `record`
Usare `record` per oggetti immutabili senza logica. `equals`, `hashCode`, `toString` gratis.

```java
public record Point(int x, int y) {}
```

❌ Non usare `record` se il tipo ha stato mutabile o logica di business non banale.

---

# 2. Null Safety

## Regole
- ❌ Vietato passare `null` nelle API pubbliche
- ✔️ Validare subito (fail-fast)
- ✔️ `Optional` solo come return type
- ✔️ Annotare parametri e return types con `@NotNull` / `@Nullable`

### ✔️ Java
```java
public static Foo of(@NotNull String name) {
  Objects.requireNonNull(name, "name");
  return new Foo(name);
}

public @Nullable String findName() { ... }
```

### ✔️ Kotlin
```kotlin
require(x != null)
```

## Nota
Non fare controlli ovunque → solo ai **boundary (API / factory)**

### ❌ Optional anti-pattern
- `Optional` come **parametro** di metodo → usa overload o `@Nullable`
- `Optional` come **campo** → non ha senso semantico
- `Optional` in collezioni (`List<Optional<T>>`) → usa lista filtrata
- `Optional` solo come **return type** quando assenza è outcome normale

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

| Uso                     | Struttura              |
|-------------------------|------------------------|
| accesso per chiave      | `HashMap`              |
| ordinamento             | `TreeMap` / `TreeSet`  |
| lista sequenziale       | `ArrayList`            |
| set senza duplicati     | `HashSet`              |
| concorrenza read-heavy  | `CopyOnWriteArrayList` |
| concorrenza write-heavy | `ConcurrentHashMap`    |

## Note importanti
- `HashMap` → O(1) medio
- `Tree*` → O(log n)
- `List` → migliore per iterazione

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
- Preferire oggetti immutabili
- Campi `final` (Java)
- `val` (Kotlin)

### ✔️ Esempio Kotlin
```kotlin
data class User(val name: String)
```

---

# 5. Pooling oggetti

## Regola forte
❌ NON usare pooling di default

## Usalo solo se:
- oggetti molto costosi
- profiling dimostra problemi GC
- risorse condivise (DB, thread, buffer grandi)

## Alternative migliori
- immutabilità
- riuso locale
- flyweight pattern (se dati ripetuti)

---

# 6. Validazione

## Regole
- Solo nelle factory / entry points
- No duplicazioni
- Fail fast

### ✔️ Esempio
```java
if (x <= 0) throw new IllegalArgumentException();
```

---

# 7. Design classi

## Regole
- classi `final` dove possibile
- niente setter (preferire immutabilità)
- responsabilità singola

---

# 8. Performance

## Regola chiave
> Non ottimizzare senza misurare

## Linee guida
- evita micro-ottimizzazioni premature
- preferisci codice semplice
- usa profiling prima di cambiare design

### String concatenation in hot path
❌ Vietato `"prefix" + var` dentro loop o metodi chiamati frequentemente.
✔️ Usare `StringBuilder` o template `String.formatted(...)`.

```java
// ❌
String msg = "[" + level + "][" + name + "] " + text;

// ✔️
String msg = "[%s][%s] %s".formatted(level, name, text);
```

### Lazy evaluation nei log (e in generale)
Se un argomento è costoso da costruire, passare un `Supplier` invece del valore.

```java
// ❌ — always evaluated
logger.debug("State: " + heavyCompute());

// ✔️ — evaluated only if DEBUG enabled
logger.debug(() -> "State: " + heavyCompute());
```

### Pattern matching (`switch` / `instanceof`)
Preferire `switch` con pattern matching a catene `if-instanceof`.

```java
// ✔️
return switch (obj) {
    case String s  -> s;
    case Integer i -> i.toString();
    default        -> obj.toString();
};
```

---

# 9. Concorrenza

## Regole
- `volatile` solo per flag di visibilità (un singolo campo, no compound actions)
- `AtomicReference` per swap atomico di oggetti
- `ConcurrentHashMap.computeIfAbsent` per lazy init thread-safe
- `CopyOnWriteArrayList` per read-heavy, write-rare
- ❌ `synchronized` solo se davvero necessario — preferire strutture già thread-safe

### ✔️ Volatile corretto
```java
private volatile boolean running = true;   // flag semplice
```

### ✔️ AtomicReference per swap
```java
private final AtomicReference<Config> config = new AtomicReference<>(defaultConfig);
config.set(newConfig);
```

### ❌ Anti-pattern
- `volatile` su un campo e poi operazioni compound su di esso (race condition)
- `synchronized` su oggetti pubblici o statici condivisi
- `volatile double score; score += x;` — race condition, non atomico
- Due `volatile boolean` per double-checked init → usare `AtomicReference<State>`
- `volatile T field` con setter pubblico su classe statica = singleton globale mutabile → preferire DI

### Stato globale mutabile
❌ `private static volatile X instance; public static void setInstance(X x)` — coupling nascosto.
✔️ Passare la dipendenza via costruttore.
Se stai costruendo un hook runtime-swappable giustificato, usa `AtomicReference<X>` e documenta perché.

---

# 10. Java + Kotlin insieme

## Linee guida pratiche

### Interoperabilità
- Java → evita `null`
- Kotlin → sfrutta null-safety

### API condivise
- preferire tipi semplici
- evitare `Optional` lato Kotlin → usare nullable

### Naming
- Java → verboso e esplicito
- Kotlin → più conciso ma chiaro

---

# 11. Anti-pattern da evitare

- Factory ovunque senza motivo
- Pooling inutile
- Controlli null duplicati ovunque
- Uso scorretto delle strutture dati
- Ottimizzazioni premature
- Getter che espongono collezioni mutabili interne
- String concatenation in loop / hot path
- Lazy evaluation mancante su argomenti costosi

---

# 12. API / Implementation Style

## Interfacce
- Nome semplice e semantico (es. `Event`, `Logger`)
- Definiscono solo il contratto
- No prefissi tipo `I`

## Implementazioni
- Nome descrittivo (`FileLogger`, `AsyncLogger`)
- Evitare `*Impl`

## Factory
- Preferire factory statiche nell'interfaccia per casi semplici
- Per logica complessa → factory esterna

## Gerarchie
- Usare `sealed` per domini chiusi
- Usare `abstract class` per logica condivisa
- Evitare gerarchie profonde

## Organizzazione
- Piccoli progetti → flat
- Progetti grandi → `impl` separato

## Regola
Non creare interfacce senza motivo reale (multipla implementazione, test, estensibilità).

---

# 13. IO Files

## File I/O

### Scrittura
- Usare sempre stream (no byte[] per file grandi)
- Non scrivere mai direttamente sul file finale
- Usare pattern:
  1. write temp file
  2. fsync
  3. atomic rename

### Lettura
- Stream per file grandi
- readAllBytes solo per file piccoli (meglio non farlo mai)

### Note
- ATOMIC_MOVE richiede stesso filesystem
- Senza fsync → rischio perdita dati

---

# 14. Reflection vs Annotation Processing

## Reflection
- Usare solo ai boundary (plugin, integrazione)
- Evitare nel core business
- Limitare a casi necessari

## Annotation Processing / KSP
- Preferire per code generation
- Usare per eliminare reflection runtime
- Garantisce type safety e performance

## Regola
- Se puoi farlo a compile-time → fallo
- Reflection è ultima scelta, non default

---

# 15. Exception Handling

## Regole
- ❌ Mai swallowone: `catch (Exception ignored) {}` vietato
- ✔️ Catch su tipo specifico, non bare `Exception` / `Throwable`
- ✔️ O rethrow (con causa) **o** log con stack completo
- ❌ `throw new RuntimeException(e.getMessage())` — perde la causa
- ✔️ Wrap con causa: `throw new MyException("...", e)`
- ✔️ Ai top-level entry point (executor task, main loop) log + continue accettabile — commentare il perché

### ✔️ Esempio
```java
// ❌
try { x(); } catch (Exception ignored) {}

// ❌ — perde causa
} catch (IOException e) { throw new RuntimeException(e.getMessage()); }

// ✔️ — wrap con causa
} catch (IOException e) { throw new MyRuntimeException("load failed", e); }

// ✔️ — entry point, log + continue
} catch (IOException e) {
    LOG.error("Cleanup skip", e); // top-level loop, we must continue
}
```

---

# 16. Resource Management

## Regole
- ✔️ Sempre `try-with-resources` per `AutoCloseable` (Stream, Connection, Channel, ClassLoader closeable)
- ❌ `close()` manuale in blocco `finally` — error-prone
- ✔️ API che **restituisce** una risorsa deve documentare chi è il proprietario (chi chiude)
- ✔️ Risorse in catena: dichiarare ognuna su riga separata nell'head del try-with-resources

### ✔️ Esempio
```java
// ❌
InputStream in = Files.newInputStream(path);
try {
    parse(in);
} finally {
    in.close(); // dimenticabile, nasconde eccezioni
}

// ✔️
try (InputStream in = Files.newInputStream(path)) {
    parse(in);
}

// ✔️ catena
try (var conn = ds.getConnection();
     var stmt = conn.prepareStatement(sql)) {
    ...
}
```

---

# 17. equals / hashCode / toString

## Regole
- ✔️ Override `equals` → **obbligatorio** override `hashCode`
- ✔️ Entrambi dipendono dagli **stessi campi**
- ✔️ Per value types → preferire `record` (li dà gratis)
- ✔️ `toString()` per value types: usa `String.formatted`, non concatenazione
- ❌ Mai confrontare `float`/`double` con `==` in `equals` → `Double.compare`

### ✔️ Esempio
```java
// ✔️ record — equals/hashCode/toString gratis
public record Point(double x, double y) {}

// ✔️ classe manuale
@Override public boolean equals(Object o) {
    if (!(o instanceof Foo f)) return false;
    return Double.compare(f.value, value) == 0 && name.equals(f.name);
}
@Override public int hashCode() { return Objects.hash(name, value); }
@Override public String toString() { return "Foo[name=%s, value=%s]".formatted(name, value); }
```

---

# 18. Architettura applicativa (MVC / MVP / MVVM)

Per moduli **con UI** (desktop, web front-end, IntelliJ plugin). Sistemi headless/server → §19.

## MVC (Model-View-Controller)
| Strato     | Responsabilità                              |
|------------|---------------------------------------------|
| Model      | stato + business rules, zero riferimenti UI |
| View       | rendering passivo, ascolta/osserva Model    |
| Controller | riceve input, traduce in mutazioni Model    |

Usare quando: web server-side classico, framework con routing (Spring MVC, Ktor, Javalin).

## MVP (Model-View-Presenter)
| Strato    | Responsabilità                                                |
|-----------|---------------------------------------------------------------|
| Model     | dominio, come MVC                                             |
| View      | dumb — espone contratto (`setText`, `onClick`), niente logica |
| Presenter | logica UI, parla con Model via interfaccia View               |

Usare quando: View difficile da testare (Swing, SWT, Android pre-Jetpack). Presenter è testabile in isolamento perché View è un'interfaccia.

## MVVM (Model-View-ViewModel)
| Strato    | Responsabilità                                                             |
|-----------|----------------------------------------------------------------------------|
| Model     | dominio                                                                    |
| ViewModel | stato osservabile (Property, StateFlow, ObservableField) — zero ref a View |
| View      | binding dichiarativo a ViewModel                                           |

Usare quando: framework con data-binding nativo (JavaFX Property, Jetpack Compose, WPF).

## Regola di selezione
- Lascia che il **framework scelga il pattern**: Spring MVC → MVC, Jetpack → MVVM, Swing test-heavy → MVP.
- Non mescolare pattern diversi nella stessa app.
- Non applicare MVC/MVP/MVVM a moduli server/headless — usare §19.

---

# 19. Architettura modulare & Varianza (API / Implementation Split)

Per moduli backend, feature, network e tooling. Tre pillar: split netto `api`/`impl`, feature packaging speculare, event-driven leggero.

## A. Split `api` / `impl` & Feature Packaging

Niente overengineering esagonale (no proliferazione inutile di folder `domain/port/adapter/infra`). Struttura flat, lineare e speculare:

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

### Regole dipendenze
- `api/` → contratti puri, zero dipendenze pesanti, zero implementazioni esterne.
- `impl/` → dipende da `api/` e contiene la logica concreta, l'eventuale I/O e le librerie di terze parti.
- Moduli esterni → dipendono SOLO da `api/` (tramite `api(project(":...:api"))`), mai da `impl/`.

### Varianza & Polimorfismo
- Il layout `api`/`impl` garantisce massima varianza: permette di sostituire l'implementazione concreta (es. `sql4j` SQLite vs Postgres, o transport `Netty` vs `VirtualThreads`, o Mock per test) senza toccare una singola riga di codice nei consumatori di `api`.

## B. Event-driven

- Evento = fatto già accaduto → naming al passato (`OrderPlaced`, non `PlaceOrder`).
- Tipi evento immutabili: `record` o estensione di `Event<E>` in `feature/event/api`.
- Registrazione listener via `@LinkEvent` + KSP/Annotation processor — zero reflection runtime.
- Separazione: il produttore emette l'evento senza conoscere i consumatori.

## C. Modulare (Gradle)

- Ogni capability = subproject Gradle (`util/*`, `feature/*`, `network/*`, ecc.).
- Split `api`/`impl` quando: l'API è consumata da altri moduli o sono previste varianti di implementazione/test.
- Naming coerente: `<area>/<capability>[/{api,impl}]`.
- Dipendenze cicliche tra subproject: vietate.

---

# 20. DI e wiring

## Default: constructor injection

```java
// ✔️ — plain Java, zero magia (Constructor Injection)
public class DefaultUserService implements UserService {
    private final UserRepository repository;
    private final EventPublisher publisher;

    public DefaultUserService(UserRepository repository, EventPublisher publisher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }
}
```

## Annotation-driven DI

Usare `@ServiceComponent` + `@Inject` + `ServiceManager` (`feature/service`) **solo** per moduli con:
- plugin discovery dinamico
- lifecycle automatico (`onEnable`/`onDisable`)
- dependency graph non triviale

## Anti-pattern
- ❌ Service locator: `Globals.get(X.class)` — coupling nascosto
- ❌ Field injection fuori da `feature/service` impl
- ❌ Static factory globale per dipendenze runtime-swappable (usare `AtomicReference` + DI — vedi §9)

---

# 21. Modello plugin / Extension

Tre modi di estendere il sistema — scegliere uno per dominio:

| Modello                         | Quando                                                            | Moduli chiave                                       |
|---------------------------------|-------------------------------------------------------------------|-----------------------------------------------------|
| File-loaded `Extension`         | plugin caricati da JAR esterno, lifecycle toggle, class isolation | `feature/module/extension/{api,manager}`            |
| Versioned `UpdateableExtension` | come sopra + auto-update                                          | `feature/module/extension-versioning/{api,manager}` |
| DI `@ServiceComponent`          | componenti interni con DI e lifecycle, no class isolation         | `feature/service/{api,impl}`                        |

Regola: non mescolare modelli nello stesso modulo.

---

# 22. Mappe come insiemi

## Regola
Non usare `Map<K, Boolean>` quando l'unica semantica è la presenza della
chiave. Preferire `Set<K>` con `Set#contains`.

### ❌ Da evitare
```java
Map<String, Boolean> staticTargets = new HashMap<>();
staticTargets.put(name + desc, true);
if (Boolean.TRUE.equals(staticTargets.get(name + desc))) { ... }
```

### ✔️ Preferire
```java
Set<String> staticTargets = new HashSet<>();
staticTargets.add(name + desc);
if (staticTargets.contains(name + desc)) { ... }
```

`Map<K, Boolean>` è ammesso solo se servono valori `false` distinti
dall'assenza (es. tristate: present-true / present-false / absent).

---

# 23. Niente superclassi che fanno tutto

## Regola
Evitare classi monolitiche che gestiscono molte responsabilità
indipendenti (parser di N annotazioni, rewriter di N istruzioni, ecc.).
Quando si supera il limite ragionevole (≈ 600 righe o più di 5
responsabilità ortogonali), spezzare in classi/metodi separati ciascuno
con una sola responsabilità.

### Linee guida
- Ogni annotazione/feature → suo pass dedicato (`AccessorPass`,
  `InvokerPass`, `ShadowFieldPass`, …) con un singolo metodo statico
  `apply(...)`.
- L'orchestratore di alto livello chiama i pass uno dopo l'altro.
- Lo stato condiviso passa come parametri espliciti, non come campi
  dell'orchestratore.

### Sintomo del problema
Se aggiungere una nuova annotazione richiede modificare un file > 1000
righe in 3+ posti diversi, il design è già sbagliato — estrarre prima
di aggiungere.

---

# 24. SOLID

Principi già presenti nel documento in forma sparsa — qui unificati e con riferimento incrociato.

## S — Single Responsibility

Una classe/metodo ha **una sola ragione per cambiare**.

- Vedi §7 (Design classi) e §23 (Niente superclassi che fanno tutto) — soglia concreta: ≈600 righe o >5 responsabilità ortogonali → split.
- Sintomo: aggiungere una feature richiede toccare la stessa classe in 3+ posti diversi → estrarre prima di aggiungere.

## O — Open/Closed

Aperto a estensione, chiuso a modifica.

- Vedi §21 (Modello plugin/Extension) — `Extension`, `UpdateableExtension`, `@ServiceComponent`: si estende il sistema senza toccare il core.
- Vedi §18/§19 — `sealed` per domini chiusi e noti a compile-time, `abstract class`/interfacce per punti di estensione aperti. Non mescolare i due (un dominio è chiuso *o* aperto, non entrambi).

## L — Liskov Substitution

Ogni implementazione deve essere sostituibile al contratto dell'interfaccia senza sorprese.

- Vedi §1 (Factory methods) — la factory è il punto giusto per garantirlo: valida gli invarianti **una volta**, alla creazione, così ogni istanza restituita rispetta davvero il contratto del tipo per tutta la sua vita. Se una sottoclasse/implementazione ha bisogno di *rilassare* precondizioni o *rafforzare* postcondizioni rispetto al contratto dichiarato → non è una vera implementazione di quel contratto, serve un tipo diverso.
- Vedi §12 (Implementazioni) — `FileLogger`, `AsyncLogger` devono onorare lo stesso contratto di `Logger` senza eccezioni nascoste o comportamenti a sorpresa che il chiamante non può prevedere dal tipo dell'interfaccia.

## I — Interface Segregation

Preferire interfacce piccole e specifiche a una grande e generica.

- Vedi §12 — un'interfaccia "definisce solo il contratto": se il contratto cresce e i consumatori iniziano a implementare metodi che non usano (no-op, `UnsupportedOperationException`), l'interfaccia va spezzata per ruolo.
- Vedi §19.A (Port & Adapters) — `port/in`/`port/out` sono già segregate per use-case (`*UseCase`) e per ruolo (`*Repository`, `*Publisher`), non un'unica interfaccia "god" per modulo.

## D — Dependency Inversion

Dipendere da astrazioni, non da implementazioni concrete.

- Vedi §20 — default constructor injection su interfacce/port, non su classi concrete.
- Vedi §19.A — `application/` dipende solo da `domain/` + `port/`, mai da `adapter/` direttamente.
- Anti-pattern già in §20: service locator (`Globals.get(X.class)`) e static factory globale per dipendenze runtime-swappable — entrambi invertono la dipendenza nella direzione sbagliata.

---

# 25. Test-first

## Regola
Prima di scrivere qualsiasi riga di codice di produzione, scrivere il test che la richiede.
Nessuna eccezione per "è una cosa piccola" — se il codice non è nato da un test che falliva, non è
nato correttamente.

## Cosa va testato (JUnit, prima del codice)
Logica pura, deterministica, input→output senza stato esterno vivo:
- Algoritmi/scoring (selezione target, calcolo danno, arbitraggio priorità)
- Math/quantizzazione (GCD, wrapping angoli, interpolazioni)
- Parsing/encoding di protocollo (packet body, formati file)
- Regole di dominio (permessi, RBAC, derivazione chiavi, rollout/bucketing)
- Qualsiasi funzione che non tocca API di gioco live, rendering, rete viva

## Cosa NON è testabile via JUnit (verifica manuale, non scusa per saltare i test)
Codice che dipende da stato di un motore di gioco/finestra/rendering live non è mockabile in modo
sensato — mockare l'intero stato per testare tre righe di glue-code costa più del codice stesso e
produce test fragili che non provano nulla di reale. Per questa categoria la verifica resta manuale
(avvio reale + osservazione), non un unit test finto:
- Codice che legge/scrive stato di gioco live (posizione giocatore, mondo, inventario reale)
- Rendering (chiamate GL/render pipeline)
- Mixin (comportamento visibile solo a runtime nel gioco reale)
- Percorsi rete live end-to-end (il codec puro sì, il socket vivo no)

## Nota
"Non testabile via JUnit" non significa "non va verificato" — significa che la verifica si sposta a
runtime reale (vedi playbook di verifica manuale, prossima sezione se presente) invece che a un test
automatico. Il codice puro dietro quella glue va comunque isolato ed estratto per essere testabile.

---

# 26. Regola finale

> Codice semplice > codice "smart"
