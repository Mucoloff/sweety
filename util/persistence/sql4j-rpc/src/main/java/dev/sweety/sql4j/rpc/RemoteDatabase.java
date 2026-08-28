package dev.sweety.sql4j.rpc;

import dev.sweety.netty.service.ServiceClient;
import dev.sweety.sql4j.api.annotation.Sql4jRepository;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.exception.Sql4jMappingException;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.impl.BaseRepository;
import dev.sweety.sql4j.impl.cache.EntityCache;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drop-in replacement for {@link dev.sweety.sql4j.impl.Database} that routes all SQL through a
 * remote gateway via {@link RemoteSqlConnection} instead of opening a local JDBC pool.
 *
 * <p>Does not extend {@code Database}: it mirrors the repository-factory API but the schema is
 * owned by the gateway ({@link #migrateAll()} is a no-op).
 */
public final class RemoteDatabase {

    private final RemoteSqlConnection connection;
    private final DialectType dialectType;
    private final TableRegistry tableRegistry = TableRegistry.getDefault();
    private final QueryCache queryCache = new QueryCache();
    private final EntityCache entityCache = new EntityCache();
    private final ConcurrentHashMap<Class<?>, Repository<?>> repositories = new ConcurrentHashMap<>();

    /**
     * @param client      the service client used to send RPC packets to the gateway
     * @param dbServiceId the generic service id of the remote SQL gateway
     * @param dialect     the database dialect — must match what the gateway runs
     */
    public RemoteDatabase(ServiceClient client, int dbServiceId, DialectType dialect) {
        this.dialectType = dialect;
        this.connection = new RemoteSqlConnection(client, dbServiceId, dialect);
        this.entityCache.setEnabled(true);
    }

    public SqlConnection getConnection() {
        return connection;
    }

    @SuppressWarnings("unchecked")
    public <E> BaseRepository<E> createRepository(Class<E> entityClass) {
        return (BaseRepository<E>) repositories.computeIfAbsent(entityClass,
                k -> new BaseRepository<>(
                        tableRegistry.get(entityClass),
                        dialectType.dialect(),
                        queryCache,
                        tableRegistry,
                        entityCache));
    }

    public <E> BaseRepository<E> getRepository(Class<E> clazz) {
        return createRepository(clazz);
    }

    /**
     * Resolves a generated custom-repository implementation ({@code <Iface>Impl}) and binds it to this
     * remote database. Ported verbatim from {@link dev.sweety.sql4j.impl.Database#getCustomRepository}:
     * the generated {@code *Impl} extends {@code BaseRepository} and binds NO connection at construction —
     * it uses the {@link SqlConnection} passed at {@code .execute(conn)}, so RPC dispatch works when callers
     * pass {@link #getConnection()}.
     */
    @SuppressWarnings("unchecked")
    public <R extends Repository<E>, E> R getCustomRepository(Class<R> repositoryInterface) {
        if (repositories.containsKey(repositoryInterface)) {
            return (R) repositories.get(repositoryInterface);
        }
        try {
            // Find the generated implementation
            String implName = repositoryInterface.getPackageName() + "." + repositoryInterface.getSimpleName() + "Impl";
            Class<?> implClass = Class.forName(implName);

            // Get the entity type from the Sql4jRepository annotation
            Sql4jRepository ann = repositoryInterface.getAnnotation(Sql4jRepository.class);
            if (ann == null) throw new Sql4jMappingException("Interface " + repositoryInterface.getName() + " is not annotated with @Sql4jRepository");

            Class<?> entityClass = ann.entity();

            // Instantiate with standard dependencies
            Constructor<?> ctor = implClass.getConstructor(
                Table.class,
                Dialect.class,
                QueryCache.class,
                TableRegistry.class,
                EntityCache.class
            );

            R repo = (R) ctor.newInstance(
                tableRegistry.get(entityClass),
                dialectType.dialect(),
                queryCache,
                tableRegistry,
                entityCache
            );
            repositories.put(repositoryInterface, repo);
            return repo;
        } catch (Sql4jMappingException e) {
            throw e;
        } catch (Exception e) {
            throw new Sql4jMappingException("Failed to load repository implementation for " + repositoryInterface.getName(), e);
        }
    }

    /** No-op: table DDL is managed by the remote gateway, not by RPC clients. */
    public void migrateAll() {
        // intentional no-op — the gateway owns the schema
    }

    @SuppressWarnings("unchecked")
    public <E> void registerRepository(Class<E> entityClass, Repository<E> repo) {
        repositories.put(entityClass, repo);
    }

    public void close() {
        connection.close();
    }
}
