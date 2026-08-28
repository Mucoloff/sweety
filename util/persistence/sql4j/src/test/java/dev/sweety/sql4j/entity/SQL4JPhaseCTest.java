package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.api.util.SqlLogger;
import dev.sweety.sql4j.entity.phaseC.CItem;
import dev.sweety.sql4j.entity.phaseC.EagerChild;
import dev.sweety.sql4j.entity.phaseC.EagerParent;
import dev.sweety.sql4j.impl.Database;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature tests for Phase C deliverables:
 * <ul>
 *   <li>C1 — Batch chunking ({@code batchChunkSize})</li>
 *   <li>C3 — {@code FetchType.EAGER} auto-join on {@code select()}</li>
 * </ul>
 * All tests run against H2 in-memory (no file I/O, no Docker required).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName.class)
class SQL4JPhaseCTest {

    private Database db;
    private SqlConnection con;
    private Repository<CItem>       items;
    private Repository<EagerParent> parents;
    private Repository<EagerChild>  children;

    @BeforeAll
    void openDatabase() {
        String path = "mem:phase_c_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        db = SQL4J.connect()
                .h2(path, "sa", "")
                .batchChunkSize(3)    // chunk size intentionally small to hit the chunked path
                .open();
        con     = db.getConnection();
        items   = db.createRepository(CItem.class);
        parents = db.createRepository(EagerParent.class);
        children= db.createRepository(EagerChild.class);
    }

    @BeforeEach
    void createTables() {
        items.createTable().execute(con).join();
        parents.createTable().execute(con).join();
        children.createTable().execute(con).join();
    }

    @AfterEach
    void dropTables() {
        try { children.dropTable().execute(con).join(); } catch (Exception ignored) {}
        try { parents.dropTable().execute(con).join();  } catch (Exception ignored) {}
        try { items.dropTable().execute(con).join();    } catch (Exception ignored) {}
    }

    @AfterAll
    void closeDatabase() throws Exception {
        db.close();
    }

    // ─── C1: Batch chunking ──────────────────────────────────────────────────────

    @Test
    @DisplayName("C1-T1: insertBatch with 7 rows and chunkSize=3 persists all 7 rows")
    void c1_insertBatch_chunked_persistsAllRows() {
        int rowCount = 7; // intentionally not a multiple of chunkSize(3)
        List<CItem> batch = IntStream.range(0, rowCount)
                .mapToObj(i -> new CItem("label-" + i))
                .toList();

        int[] counts = items.insertBatch(batch).execute(con).join();

        // All rows must be accounted for in the returned count arrays
        int totalAffected = 0;
        for (int c : counts) totalAffected += c;
        assertEquals(rowCount, totalAffected, "total affected rows must equal batch size");

        // Verify the DB actually contains all 7 rows
        List<CItem> persisted = items.select().execute(con).join();
        assertEquals(rowCount, persisted.size(),
                "select() must return all " + rowCount + " inserted rows");
    }

    @Test
    @DisplayName("C1-T2: insertBatch with exactly chunkSize rows uses the unchunked path")
    void c1_insertBatch_exactChunk_persists() {
        // batchChunkSize = 3, batch = 3 → single executeBatch, same as no-chunk
        List<CItem> batch = List.of(new CItem("a"), new CItem("b"), new CItem("c"));
        items.insertBatch(batch).execute(con).join();

        assertEquals(3, items.select().execute(con).join().size());
    }

    @Test
    @DisplayName("C1-T3: updateBatch with 7 rows and chunkSize=3 updates all values")
    void c1_updateBatch_chunked_updatesAll() {
        List<CItem> inserted = IntStream.range(0, 7)
                .mapToObj(i -> new CItem("orig-" + i))
                .toList();
        items.insertBatch(inserted).execute(con).join();
        List<CItem> saved = items.select().execute(con).join();
        assertEquals(7, saved.size());

        saved.forEach(it -> it.setLabel(it.getLabel().replace("orig", "upd")));
        items.updateBatch(saved).execute(con).join();

        List<CItem> updated = items.select().execute(con).join();
        assertTrue(updated.stream().allMatch(it -> it.getLabel().startsWith("upd")),
                "All rows must have the updated label after chunked updateBatch");
    }

    // ─── C3: FetchType.EAGER auto-join ──────────────────────────────────────────

    @Test
    @DisplayName("C3-T1: FetchType.EAGER causes select() to auto-load children")
    void c3_fetchTypeEager_selectAutoLoadsChildren() {
        EagerParent parent = new EagerParent("ParentA");
        parents.insert(parent).execute(con).join();

        EagerChild c1 = new EagerChild("child1", parent);
        EagerChild c2 = new EagerChild("child2", parent);
        children.insert(c1).execute(con).join();
        children.insert(c2).execute(con).join();

        List<EagerParent> fetched = parents.select().execute(con).join();
        assertEquals(1, fetched.size());
        EagerParent p = fetched.getFirst();
        assertEquals(2, p.getChildren().size(),
                "EAGER relation must auto-load children without an explicit .fetch() call");
    }

    @Test
    @DisplayName("C3-T2: FetchType.EAGER select() issues a single SQL query (no N+1)")
    void c3_fetchTypeEager_singleQuery() {
        EagerParent parent = new EagerParent("ParentB");
        parents.insert(parent).execute(con).join();
        children.insert(new EagerChild("c1", parent)).execute(con).join();
        children.insert(new EagerChild("c2", parent)).execute(con).join();

        AtomicInteger queryCount = new AtomicInteger();
        SqlRunner.setLogger(msg -> {
            if (msg.contains("Executing SQL")) queryCount.incrementAndGet();
        });

        parents.select().execute(con).join();

        SqlRunner.setLogger(SqlLogger.nop());

        assertEquals(1, queryCount.get(),
                "EAGER select() must issue exactly 1 JOIN query, not N+1; actual: " + queryCount.get());
    }

    @Test
    @DisplayName("C3-T3: Table.Relation.fetchType() reflects the annotation value")
    void c3_tableRelation_fetchType_isEager() {
        Table<EagerParent> table = db.createRepository(EagerParent.class).table();
        boolean hasEagerRelation = table.relations().stream()
                .anyMatch(r -> r.fetchType() == dev.sweety.sql4j.api.obj.annotation.FetchType.EAGER
                            && r.type() == Table.Relation.Type.ONE_TO_MANY);
        assertTrue(hasEagerRelation,
                "EagerParent must have at least one ONE_TO_MANY relation with FetchType.EAGER");
    }
}
