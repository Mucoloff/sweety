package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.crypto.CryptoKeyService;
import dev.sweety.sql4j.api.shard.ShardedDatabase;
import dev.sweety.sql4j.api.shard.ShardedRepository;
import dev.sweety.sql4j.api.shard.VirtualShardRouter;
import dev.sweety.sql4j.entity.ShardedUserAccount;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShardedRepositoryTest {

    private String db0Path;
    private String db1Path;
    private String db2Path;
    private String db3Path;

    private SqlConnection conn0;
    private SqlConnection conn1;
    private SqlConnection conn2;
    private SqlConnection conn3;

    @BeforeEach
    void setup() {
        long seed = System.nanoTime();
        db0Path = "sharded_0_" + seed + ".db";
        db1Path = "sharded_1_" + seed + ".db";
        db2Path = "sharded_2_" + seed + ".db";
        db3Path = "sharded_3_" + seed + ".db";

        conn0 = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), db0Path);
        conn1 = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), db1Path);
        conn2 = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), db2Path);
        conn3 = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), db3Path);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (conn0 != null) conn0.close();
        if (conn1 != null) conn1.close();
        if (conn2 != null) conn2.close();
        if (conn3 != null) conn3.close();

        Files.deleteIfExists(Path.of(db0Path));
        Files.deleteIfExists(Path.of(db1Path));
        Files.deleteIfExists(Path.of(db2Path));
        Files.deleteIfExists(Path.of(db3Path));
    }

    @Test
    void testShardedRepositoryRouting() {
        List<SqlConnection> nodes = List.of(conn0, conn1, conn2, conn3);
        VirtualShardRouter router = VirtualShardRouter.createDefault(4); // 8192 shards across 4 nodes

        ShardedDatabase shardedDb = ShardedDatabase.of(nodes, router);
        CryptoKeyService crypto = CryptoKeyService.ofSecret("super_secure_master_kek_32_bytes_len");

        ShardedRepository<ShardedUserAccount> repository = ShardedRepository.of(shardedDb, ShardedUserAccount.class, crypto);
        assertNotNull(repository);

        long user1 = 1001L;
        long user2 = 2002L;

        int nodeForUser1 = router.resolveNode(user1);
        int nodeForUser2 = router.resolveNode(user2);

        assertEquals(nodes.get(nodeForUser1), shardedDb.getConnectionForKey(user1));
        assertEquals(nodes.get(nodeForUser2), shardedDb.getConnectionForKey(user2));
    }
}
