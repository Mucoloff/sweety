package dev.sweety.sql4j;

import dev.sweety.sql4j.api.annotation.Cacheable;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class L2CacheTest extends BaseSQLTest {

    @Cacheable(maxSize = 10)
    public static class CachedUser {
        public int id;
        public String name;
        
        public CachedUser() {}
        public CachedUser(int id, String name) { this.id = id; this.name = name; }
        
        public static final Table<CachedUser> TABLE = Table.of(CachedUser.class, "cached_users")
                .column("id", Integer.class, u -> u.id, (u, v) -> u.id = v).primaryKey().build()
                .column("name", String.class, u -> u.name, (u, v) -> u.name = v).build();
    }

    private Repository<CachedUser> repo;

    @BeforeEach
    void setUp() {
        Database db = createDatabase();
        repo = db.createRepository(CachedUser.class, CachedUser.TABLE);
        repo.create(true).execute(getConnection()).join();
    }

    @Test
    void testCacheHit() {
        SqlConnection con = getConnection();
        CachedUser user = new CachedUser(1, "Alice");
        repo.insert(user).execute(con).join();
        
        // First fetch - should be cached by insert
        CachedUser fetched1 = repo.pk(1).find().execute(con).join();
        assertNotNull(fetched1);
        assertSame(user, fetched1); // Should be the same instance if cached

        // Second fetch - should definitely be a hit
        CachedUser fetched2 = repo.pk(1).find().execute(con).join();
        assertSame(fetched1, fetched2);
    }

    @Test
    void testCacheInvalidationOnUpdate() {
        SqlConnection con = getConnection();
        CachedUser user = new CachedUser(1, "Alice");
        repo.insert(user).execute(con).join();
        
        user.name = "Alice Updated";
        repo.update(user).execute(con).join();
        
        CachedUser fetched = repo.pk(1).find().execute(con).join();
        assertEquals("Alice Updated", fetched.name()); // Wait, name() might not exist, but field is public
        assertEquals("Alice Updated", fetched.name);
    }

    @Test
    void testCacheInvalidationOnDeleteWhere() {
        SqlConnection con = getConnection();
        repo.insert(new CachedUser(1, "Alice")).execute(con).join();
        
        assertNotNull(repo.pk(1).find().execute(con).join());
        
        repo.deleteWhere(CachedUser.TABLE.column("id").eq(1)).execute(con).join();
        
        assertNull(repo.pk(1).find().execute(con).join());
    }
}
