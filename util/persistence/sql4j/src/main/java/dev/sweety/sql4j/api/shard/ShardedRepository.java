package dev.sweety.sql4j.api.shard;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.crypto.CryptoKeyService;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.impl.BaseRepository;
import dev.sweety.sql4j.api.query.InsertQuery;
import dev.sweety.sql4j.api.query.UpdateQuery;
import dev.sweety.sql4j.api.query.DeleteQuery;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Sharded repository proxy that transparently routes CRUD operations
 * to the appropriate physical database connection based on {@link VirtualShardRouter}.
 */
public final class ShardedRepository<E> {

    private final ShardedDatabase shardedDatabase;
    private final Class<E> entityClass;
    private final BaseRepository<E> baseRepository;
    private final CryptoKeyService cryptoKeyService;

    public ShardedRepository(ShardedDatabase shardedDatabase, Class<E> entityClass, CryptoKeyService cryptoKeyService) {
        this.shardedDatabase = Objects.requireNonNull(shardedDatabase, "shardedDatabase must not be null");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
        this.baseRepository = shardedDatabase.createRepository(entityClass);
        this.cryptoKeyService = cryptoKeyService;
    }

    public static <E> ShardedRepository<E> of(ShardedDatabase shardedDatabase, Class<E> entityClass, CryptoKeyService cryptoKeyService) {
        return new ShardedRepository<>(shardedDatabase, entityClass, cryptoKeyService);
    }

    /**
     * Resolves target connection for the shard key and executes an insert.
     */
    public CompletableFuture<?> insert(long shardKey, E entity) {
        SqlConnection conn = shardedDatabase.getConnectionForKey(shardKey);
        InsertQuery<E> query = baseRepository.insert(entity);
        return conn.executeAsync(query);
    }

    /**
     * Resolves target connection for the shard key and executes an update.
     */
    public CompletableFuture<?> update(long shardKey, E entity) {
        SqlConnection conn = shardedDatabase.getConnectionForKey(shardKey);
        UpdateQuery<E> query = baseRepository.update(entity);
        return conn.executeAsync(query);
    }

    /**
     * Resolves target connection for the shard key and executes a delete.
     */
    public CompletableFuture<?> delete(long shardKey, E entity) {
        SqlConnection conn = shardedDatabase.getConnectionForKey(shardKey);
        DeleteQuery<E> query = baseRepository.delete(entity);
        return conn.executeAsync(query);
    }

    public Table<E> table() {
        return baseRepository.table();
    }

    public ShardedDatabase getShardedDatabase() {
        return shardedDatabase;
    }

    public Class<E> getEntityClass() {
        return entityClass;
    }

    public BaseRepository<E> getBaseRepository() {
        return baseRepository;
    }

    public CryptoKeyService getCryptoKeyService() {
        return cryptoKeyService;
    }
}
