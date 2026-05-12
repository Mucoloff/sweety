package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.configuration.SQL4JConfig;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.exception.Sql4jMappingException;
import dev.sweety.sql4j.api.interceptor.QueryInterceptor;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.query.chain.QueryChain;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.impl.connection.provider.HikariConnectionProvider;
import dev.sweety.sql4j.impl.connection.provider.DriverManagerConnectionProvider;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.impl.cache.EntityCache;
import dev.sweety.sql4j.impl.transaction.TransactionManager;

import dev.sweety.sql4j.api.obj.Table;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

import dev.sweety.sql4j.api.repository.Repository;

public class Database implements AutoCloseable {

    private final Map<Class<?>, dev.sweety.sql4j.api.repository.Repository<?>> repositories = new ConcurrentHashMap<>();
    private final TableRegistry tableRegistry = TableRegistry.getDefault();
    private final QueryCache queryCache = new QueryCache();
    private final SqlConnection connection;
    private final Dialect dialect;
    private final TransactionManager transactionManager;
    private final EntityCache entityCache = new EntityCache();
    private final List<QueryInterceptor> interceptors = new ArrayList<>();
    private int batchChunkSize = 0;

    public Database(final SqlConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection cannot be null");
        this.dialect = Objects.requireNonNull(connection.dialect(), "dialect cannot be null");
        this.transactionManager = new TransactionManager(connection);
    }

    public Database(final DatabaseConfig config, final Executor executor) {
        this(ConnectionType.valueOf(config.dialectType().name()).create(config, executor));
    }

    /**
     * Creates a {@code Database} from the canonical, fully-resolved {@link SQL4JConfig}.
     * This is the preferred constructor when using the step builder.
     */
    public Database(final SQL4JConfig config) {
        this(buildConnection(config));
        entityCache.setEnabled(config.cacheEnabled());
        this.batchChunkSize = config.batchChunkSize();
    }

    private static SqlConnection buildConnection(SQL4JConfig config) {
        Executor executor = config.executor() != null ? config.executor() : Executors.newCachedThreadPool();
        boolean ownsExecutor = config.executor() == null;
        dev.sweety.sql4j.api.connection.provider.ConnectionProvider provider = config.useHikari()
                ? new HikariConnectionProvider(config)
                : new DriverManagerConnectionProvider(config);
        return new SqlConnection(config.dialect(), provider, executor, ownsExecutor);
    }

    /**
     * @deprecated Use {@link #Database(DatabaseConfig, Executor)} or {@link #Database(SqlConnection)} instead.
     */
    @Deprecated
    public Database(final ConnectionType connectionType, final String... params) {
        this(connectionType.create(Executors.newCachedThreadPool(), params));
    }

    public SqlConnection getConnection() {
        return connection;
    }

    public <R extends Repository<E>, E> R createRepository(final Class<E> entityClass) {
        return getOrCreateRepository(entityClass,
                clazz -> new BaseRepository<>(tableRegistry.get(clazz), dialect, queryCache, tableRegistry, entityCache, batchChunkSize));
    }

    public <R extends Repository<E>, E> R createRepository(final Class<E> entityClass, String customTableName) {
        //noinspection unchecked
        return (R) new BaseRepository<>(tableRegistry.getOrCreate(entityClass, customTableName),
                dialect, queryCache, tableRegistry, entityCache, batchChunkSize);
    }

    public <R extends Repository<E>, E> R getCustomRepository(Class<R> repositoryInterface) {
        if (repositories.containsKey(repositoryInterface)) {
            //noinspection unchecked
            return (R) repositories.get(repositoryInterface);
        }
        try {
            // Find the generated implementation
            String implName = repositoryInterface.getPackageName() + "." + repositoryInterface.getSimpleName() + "Impl";
            Class<?> implClass = Class.forName(implName);
            
            // Get the entity type from the Sql4jRepository annotation
            dev.sweety.sql4j.api.annotation.Sql4jRepository ann = repositoryInterface.getAnnotation(dev.sweety.sql4j.api.annotation.Sql4jRepository.class);
            if (ann == null) throw new Sql4jMappingException("Interface " + repositoryInterface.getName() + " is not annotated with @Sql4jRepository");
            
            Class<?> entityClass = ann.entity();
            
            // Instantiate with standard dependencies
            java.lang.reflect.Constructor<?> ctor = implClass.getConstructor(
                dev.sweety.sql4j.api.obj.Table.class,
                dev.sweety.sql4j.api.connection.dialect.Dialect.class,
                dev.sweety.sql4j.impl.query.QueryCache.class,
                dev.sweety.sql4j.api.obj.table.TableRegistry.class,
                dev.sweety.sql4j.impl.cache.EntityCache.class
            );

            //noinspection unchecked
            R repo = (R) ctor.newInstance(
                tableRegistry.get(entityClass),
                dialect,
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

    public <R extends Repository<E>, E> R getOrCreateRepository(final Class<E> entityClass,
                                                                  Function<Class<E>, Repository<E>> factory) {
        //noinspection unchecked
        return (R) repositories.computeIfAbsent(entityClass, k -> {
            //noinspection unchecked
            Repository<E> repo = factory.apply((Class<E>) k);
            migrateAll();
            return repo;
        });
    }

    public void migrateAll() {
        for (dev.sweety.sql4j.api.obj.Table<?> t : tableRegistry.allTables()) {
            new dev.sweety.sql4j.impl.query.table.CreateTable(t, dialect, true).execute(connection).join();
            for (String indexSql : dev.sweety.sql4j.impl.query.table.CreateTable.buildIndices(t, dialect, true)) {
                connection.executeAsync(new dev.sweety.sql4j.api.query.AbstractQuery<Void>() {
                    @Override protected String buildSql() { return indexSql; }
                    @Override public void bind(java.sql.PreparedStatement ps) {}
                    @Override public Void execute(java.sql.PreparedStatement ps) throws java.sql.SQLException {
                        ps.execute();
                        return null;
                    }
                }).join();
            }
            new BaseRepository<>((Table<Object>) t, dialect, queryCache, tableRegistry, null).migrateSchema(connection);
        }
    }

    public TableRegistry tableRegistry() {
        return tableRegistry;
    }

    public <R extends Repository<E>, E> R getRepository(final Class<E> clazz) {
        //noinspection unchecked
        return (R) repositories.get(clazz);
    }

    public Dialect dialect() {
        return dialect;
    }

    public Collection<Repository<?>> repositories() {
        return repositories.values();
    }

    public <T> CompletableFuture<T> transaction(final QueryChain<T> chain) {
        return transactionManager.transaction(chain);
    }

    /**
     * Executes a transactional block with savepoint support.
     * Use this for complex transactions that require savepoints or conditional rollbacks.
     *
     * <pre>{@code
     * db.transact(tx -> {
     *     tx.execute(repo.insert(entity));
     *     tx.savepoint("after_insert");
     *     try {
     *         tx.execute(riskyQuery);
     *     } catch (SQLException e) {
     *         tx.rollbackTo("after_insert");
     *     }
     * }).join();
     * }</pre>
     */
    public CompletableFuture<Void> transact(final TransactionManager.TransactionBlock block) {
        return transactionManager.transaction(block);
    }

    public void addInterceptor(QueryInterceptor interceptor) {
        interceptors.add(Objects.requireNonNull(interceptor));
        connection.addInterceptor(interceptor);
    }

    public List<QueryInterceptor> interceptors() {
        return java.util.Collections.unmodifiableList(interceptors);
    }

    public EntityCache entityCache() {
        return entityCache;
    }

    @Override
    public void close() {
        connection.close();
    }
}
