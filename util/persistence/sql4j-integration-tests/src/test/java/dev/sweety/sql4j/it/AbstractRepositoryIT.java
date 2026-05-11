package dev.sweety.sql4j.it;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.api.util.SqlLogger;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.it.entity.CommentIT;
import dev.sweety.sql4j.it.entity.ItemIT;
import dev.sweety.sql4j.it.entity.ItemITTable;
import dev.sweety.sql4j.it.entity.PostIT;
import dev.sweety.sql4j.it.entity.PostITTable;
import dev.sweety.sql4j.it.entity.PostITTable;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base integration test class exercising the full Repository contract.
 *
 * <p>Each concrete subclass supplies a {@link DatabaseConfig} pointing to the target DB.
 * Tables are created in {@code @BeforeEach} and dropped in {@code @AfterEach} for full
 * isolation between tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName.class)
abstract class AbstractRepositoryIT {

    protected Database db;
    protected SqlConnection con;
    protected Repository<ItemIT> items;
    protected Repository<PostIT> posts;
    protected Repository<CommentIT> comments;

    /** Subclasses supply their own {@link DatabaseConfig}. */
    protected abstract DatabaseConfig openConfig();

    @BeforeAll
    void setupDatabase() {
        DatabaseConfig config = openConfig();
        ConnectionType ct = ConnectionType.fromDialectType(config.dialectType());
        con = ct.create(config);
        db = new Database(con);
        items = db.createRepository(ItemIT.class);
        posts = db.createRepository(PostIT.class);
        comments = db.createRepository(CommentIT.class);
    }

    @BeforeEach
    void createTables() {
        items.createTable().execute(con).join();
        posts.createTable().execute(con).join();
        comments.createTable().execute(con).join();
    }

    @AfterEach
    void dropTables() {
        // Drop in child-first order to avoid FK violations on referential-integrity DBs
        try { comments.dropTable().execute(con).join(); } catch (Exception ignored) {}
        try { posts.dropTable().execute(con).join(); } catch (Exception ignored) {}
        try { items.dropTable().execute(con).join(); } catch (Exception ignored) {}
        SqlRunner.setLogger(SqlLogger.nop());
        db.entityCache().clear();
    }

    @AfterAll
    void closeDatabase() throws Exception {
        db.close();
    }

    // ─── IT-01 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-01 createTable succeeds")
    void it01CreateTable() {
        // createTable() is called in @BeforeEach; reaching this line means no exception was thrown
    }

    // ─── IT-02 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-02 insert + selectById returns entity")
    void it02InsertAndSelectById() {
        ItemIT item = new ItemIT("Alpha");
        items.insert(item).execute(con).join();

        assertNotNull(item.getId(), "Auto-generated PK must be set after insert");

        ItemIT found = items.pk(item.getId()).find().execute(con).join();
        assertNotNull(found);
        assertEquals(item.getId(), found.getId());
        assertEquals("Alpha", found.getName());
    }

    // ─── IT-03 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-03 update changes persisted value")
    void it03Update() {
        ItemIT item = new ItemIT("Beta");
        items.insert(item).execute(con).join();

        item.setName("BetaUpdated");
        items.update(item).execute(con).join();

        ItemIT found = items.pk(item.getId()).find().execute(con).join();
        assertNotNull(found);
        assertEquals("BetaUpdated", found.getName());
    }

    // ─── IT-04 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-04 delete removes record")
    void it04Delete() {
        ItemIT item = new ItemIT("Gamma");
        items.insert(item).execute(con).join();

        items.delete(item).execute(con).join();

        List<ItemIT> all = items.select().execute(con).join();
        assertTrue(all.isEmpty(), "Table should be empty after delete");
    }

    // ─── IT-05 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-05 upsert inserts then updates")
    void it05Upsert() {
        ItemIT item = new ItemIT("Delta");
        items.upsert(item).execute(con).join();
        assertNotNull(item.getId());

        item.setName("DeltaV2");
        items.upsert(item).execute(con).join();

        List<ItemIT> all = items.select().execute(con).join();
        assertEquals(1, all.size(), "Upsert must not create a duplicate row");
        assertEquals("DeltaV2", all.getFirst().getName());
    }

    // ─── IT-06 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-06 insertBatch persists all rows")
    void it06InsertBatch() {
        final int batchSize = 5;
        List<ItemIT> batch = List.of(
                new ItemIT("B1"), new ItemIT("B2"), new ItemIT("B3"),
                new ItemIT("B4"), new ItemIT("B5"));

        items.insertBatch(batch).execute(con).join();

        List<ItemIT> all = items.select().execute(con).join();
        assertEquals(batchSize, all.size(), "All batch rows must be persisted");
    }

    // ─── IT-07 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-07 updateBatch updates all rows")
    void it07UpdateBatch() {
        List<ItemIT> batch = List.of(new ItemIT("C1"), new ItemIT("C2"), new ItemIT("C3"));
        items.insertBatch(batch).execute(con).join();

        List<ItemIT> inserted = items.select().execute(con).join();
        inserted.forEach(i -> i.setName(i.getName() + "_upd"));
        items.updateBatch(inserted).execute(con).join();

        List<ItemIT> updated = items.select().execute(con).join();
        assertTrue(updated.stream().allMatch(i -> i.getName().endsWith("_upd")),
                "Every row must reflect the new name after updateBatch");
    }

    // ─── IT-08 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-08 deleteWhere removes matching rows; others survive")
    void it08DeleteWhere() {
        items.insertBatch(List.of(new ItemIT("keep"), new ItemIT("remove1"), new ItemIT("remove2"))).execute(con).join();

        items.deleteWhere(ItemITTable.NAME.eq("remove1").or(ItemITTable.NAME.eq("remove2"))).execute(con).join();

        List<ItemIT> surviving = items.select().execute(con).join();
        assertEquals(1, surviving.size());
        assertEquals("keep", surviving.getFirst().getName());
    }

    // ─── IT-09 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-09 selectRaw with parameter returns correct subset")
    void it09SelectRaw() {
        items.insertBatch(List.of(new ItemIT("raw1"), new ItemIT("raw2"), new ItemIT("other"))).execute(con).join();

        var rows = items.selectRawAll()
                .where(ItemITTable.NAME.eq("raw1"))
                .execute(con).join();

        assertEquals(1, rows.size());
        assertEquals("raw1", rows.getFirst().getString("name"));
    }

    // ─── IT-10 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-10 dropTable + createTable succeeds")
    void it10DropAndRecreate() {
        // @AfterEach drops; here we drop manually and immediately recreate
        items.dropTable().execute(con).join();
        assertDoesNotThrow(() -> items.createTable().execute(con).join(),
                "createTable must succeed after dropTable");
    }

    // ─── IT-11 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-11 SoftDelete hides row from select; physical row still present")
    void it11SoftDelete() throws Exception {
        ItemIT item = new ItemIT("SoftTarget");
        items.insert(item).execute(con).join();

        // Soft-delete the entity
        items.delete(item).execute(con).join();

        // Managed select must return empty (soft-delete filter applied)
        List<ItemIT> visible = items.select().execute(con).join();
        assertTrue(visible.isEmpty(), "select() must not return soft-deleted rows");

        // Physical count via direct JDBC must be 1
        try (Connection rawCon = con.connection();
             PreparedStatement ps = rawCon.prepareStatement("SELECT COUNT(*) FROM it_items");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Soft-deleted row must still be physically present");
        }
    }

    // ─── IT-12 ───────────────────────────────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("IT-12 No N+1 on @OneToMany — fetch executes exactly 1 query")
    void it12NoNPlus1() {
        // Set up parent + children
        PostIT post = new PostIT("My Post");
        posts.insert(post).execute(con).join();

        CommentIT c1 = new CommentIT("First comment", post);
        CommentIT c2 = new CommentIT("Second comment", post);
        comments.insert(c1).execute(con).join();
        comments.insert(c2).execute(con).join();

        // Count SQL executions via the logger interceptor
        AtomicInteger queryCount = new AtomicInteger();
        SqlRunner.setLogger(msg -> {
            if (msg.contains("Executing SQL")) queryCount.incrementAndGet();
        });

        List<PostIT> fetched = posts.select()
                .fetch(PostITTable.COMMENTS_REL)
                .execute(con).join();

        assertEquals(1, queryCount.get(),
                "fetch() must produce exactly 1 SQL query (JOIN), not N+1; actual: " + queryCount.get());
        assertEquals(1, fetched.size());
        assertEquals(2, fetched.getFirst().getComments().size(),
                "Parent must have both children loaded");
    }
}
