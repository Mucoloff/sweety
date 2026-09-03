package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.configuration.SQL4JConfig;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.annotation.Sql4jRepository;
import dev.sweety.sql4j.api.exception.Sql4jMappingException;
import dev.sweety.sql4j.api.interceptor.QueryInterceptor;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.query.chain.QueryChain;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.impl.connection.provider.HikariConnectionProvider;
import dev.sweety.sql4j.impl.connection.provider.DriverManagerConnectionProvider;
import dev.sweety.sql4j.api.obj.Table;

import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.impl.cache.EntityCache;

import dev.sweety.sql4j.api.connection.provider.ConnectionProvider;
import dev.sweety.sql4j.impl.query.table.CreateTable;
import dev.sweety.sql4j.impl.transaction.TransactionManager;

import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.thread.ThreadUtil;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Function;

import dev.sweety.sql4j.api.repository.Repository;

public class Database implements AutoCloseable {

    private final Map<Class<?>, Repository<?>> repositories = new ConcurrentHashMap<>();
    private final TableRegistry tableRegistry = TableRegistry.getDefault();
    private final QueryCache queryCache = new QueryCache();
    private final SqlConnection connection;
    private final Dialect dialect;
    private final TransactionManager transactionManager;
    private final EntityCache entityCache = new EntityCache();
    // CopyOnWriteArrayList: read by every query execution path off the connection/executor
    // threads while addInterceptor() can be called concurrently — see SqlConnection for the
    // same fix on the connection-scoped interceptor list.
    private final List<QueryInterceptor> interceptors = new CopyOnWriteArrayList<>();
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
        Executor executor = config.executor() != null ? config.executor() : ThreadUtil.cachedThreadPool("sql4j-db");
        boolean ownsExecutor = config.executor() == null;
        ConnectionProvider provider = config.useHikari()
                ? new HikariConnectionProvider(config)
                : new DriverManagerConnectionProvider(config);
        return new SqlConnection(config.dialect(), provider, executor, ownsExecutor);
    }

    /**
     * @deprecated Use {@link #Database(DatabaseConfig, Executor)} or {@link #Database(SqlConnection)} instead.
     */
    @Deprecated
    public Database(final ConnectionType connectionType, final String... params) {
        this(connectionType.create(ThreadUtil.cachedThreadPool("sql4j-db"), params));
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
        for (Table<?> t : tableRegistry.allTables()) {
            new CreateTable(t, dialect, true).execute(connection).join();
            for (String indexSql : CreateTable.buildIndices(t, dialect, true)) {
                connection.executeAsync(new AbstractQuery<Void>() {
                    @Override protected String buildSql() { return indexSql; }
                    @Override public void bind(PreparedStatement ps) {}
                    @Override public Void execute(PreparedStatement ps) throws SQLException {
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
        return Collections.unmodifiableList(interceptors);
    }

    public EntityCache entityCache() {
        return entityCache;
    }

    /**
     * Creates or retrieves a typed NoSQL document collection.
     *
     * @param collectionName the name of the collection
     * @param documentClass the document class
     * @param format the serialization format (YAML, JSON, RAW)
     * @param idClass the identifier class (String, UUID, Long, Integer)
     */
    public <T, ID> dev.sweety.sql4j.document.DocumentCollection<T, ID> documentCollection(String collectionName, Class<T> documentClass, dev.sweety.sql4j.document.DocumentFormat format, Class<ID> idClass) {
        return new dev.sweety.sql4j.document.DocumentCollectionImpl<>(collectionName, documentClass, format, idClass, connection);
    }

    /**
     * Creates or retrieves a string-keyed typed document collection.
     */
    public <T> dev.sweety.sql4j.document.DocumentCollection<T, String> documentCollection(String collectionName, Class<T> documentClass, dev.sweety.sql4j.document.DocumentFormat format) {
        return documentCollection(collectionName, documentClass, format, String.class);
    }

    /**
     * Creates or retrieves a YAML-backed ConfigurationSection document collection.
     */
    public dev.sweety.sql4j.document.DocumentCollection<dev.sweety.config.common.ConfigurationSection, String> yamlDocuments(String collectionName) {
        return documentCollection(collectionName, dev.sweety.config.common.ConfigurationSection.class, dev.sweety.sql4j.document.DocumentFormat.YAML, String.class);
    }

    /**
     * Creates or retrieves a JSON-backed ConfigurationSection document collection.
     */
    public dev.sweety.sql4j.document.DocumentCollection<dev.sweety.config.common.ConfigurationSection, String> jsonDocuments(String collectionName) {
        return documentCollection(collectionName, dev.sweety.config.common.ConfigurationSection.class, dev.sweety.sql4j.document.DocumentFormat.JSON, String.class);
    }

    @Override
    public void close() {
        connection.close();
    }
}
