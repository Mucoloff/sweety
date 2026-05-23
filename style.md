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

---

# 2. Null Safety

## Regole
- ❌ Vietato passare `null` nelle API pubbliche
- ✔️ Validare subito (fail-fast)
- ✔️ `Optional` solo come return type

### ✔️ Java
```java
Objects.requireNonNull(x, "x");
```

### ✔️ Kotlin
```kotlin
require(x != null)
```

## Nota
Non fare controlli ovunque → solo ai **boundary (API / factory)**

---

# 3. Strutture dati

## Regola generale
Usa la struttura giusta per il problema, non “map ovunque”.

| Uso | Struttura |
|-----|----------|
| accesso per chiave | `HashMap` |
| ordinamento | `TreeMap` / `TreeSet` |
| lista sequenziale | `ArrayList` |
| set senza duplicati | `HashSet` |

## Note importanti
- `HashMap` → O(1) medio
- `Tree*` → O(log n)
- `List` → migliore per iterazione

### ❌ Anti-pattern
- usare `Map` quando serve una lista
- usare `TreeSet` senza bisogno di ordinamento

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

---

# 9. Java + Kotlin insieme

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

# 10. Anti-pattern da evitare

- Factory ovunque senza motivo
- Pooling inutile
- Controlli null duplicati ovunque
- Uso scorretto delle strutture dati
- Ottimizzazioni premature

---

# 11. Regola finale

> Codice semplice > codice “smart”
