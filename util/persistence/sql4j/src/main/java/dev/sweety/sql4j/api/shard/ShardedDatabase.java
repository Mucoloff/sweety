package dev.sweety.sql4j.api.shard;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.impl.BaseRepository;
import dev.sweety.sql4j.impl.cache.EntityCache;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sharded Database cluster router.
 *
 * <p>Wraps a list of physical node {@link SqlConnection}s and transparently routes operations
 * using {@link VirtualShardRouter}.
 */
public final class ShardedDatabase {

    private final List<SqlConnection> nodeConnections;
    private final VirtualShardRouter router;
    private final TableRegistry tableRegistry = TableRegistry.getDefault();
    private final QueryCache queryCache = new QueryCache();
    private final EntityCache entityCache = new EntityCache();
    private final ConcurrentHashMap<Class<?>, Repository<?>> repositories = new ConcurrentHashMap<>();

    public ShardedDatabase(List<SqlConnection> nodeConnections, VirtualShardRouter router) {
        this.nodeConnections = List.copyOf(Objects.requireNonNull(nodeConnections, "nodeConnections must not be null"));
        if (this.nodeConnections.isEmpty()) {
            throw new IllegalArgumentException("nodeConnections must not be empty");
        }
        this.router = Objects.requireNonNull(router, "router must not be null");
        this.entityCache.setEnabled(true);
    }

    public static ShardedDatabase of(List<SqlConnection> nodeConnections, VirtualShardRouter router) {
        return new ShardedDatabase(nodeConnections, router);
    }

    /**
     * Resolves the target physical connection for the given partition key.
     */
    public SqlConnection getConnectionForKey(long shardKey) {
        int nodeIndex = router.resolveNode(shardKey);
        return nodeConnections.get(nodeIndex);
    }

    /**
     * Resolves the target physical connection for the given string partition key.
     */
    public SqlConnection getConnectionForKey(String shardKey) {
        int nodeIndex = router.resolveNode(shardKey);
        return nodeConnections.get(nodeIndex);
    }

    public VirtualShardRouter getRouter() {
        return router;
    }

    public List<SqlConnection> getNodeConnections() {
        return nodeConnections;
    }

    @SuppressWarnings("unchecked")
    public <E> BaseRepository<E> createRepository(Class<E> entityClass) {
        return (BaseRepository<E>) repositories.computeIfAbsent(entityClass,
                k -> new BaseRepository<>(
                        tableRegistry.get(entityClass),
                        nodeConnections.get(0).dialect(),
                        queryCache,
                        tableRegistry,
                        entityCache));
    }
}
