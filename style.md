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

| Uso | Struttura |
|-----|----------|
| accesso per chiave | `HashMap` |
| ordinamento | `TreeMap` / `TreeSet` |
| lista sequenziale | `ArrayList` |
| set senza duplicati | `HashSet` |
| concorrenza read-heavy | `CopyOnWriteArrayList` |
| concorrenza write-heavy | `ConcurrentHashMap` |

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

# 18. Regola finale

> Codice semplice > codice "smart"
