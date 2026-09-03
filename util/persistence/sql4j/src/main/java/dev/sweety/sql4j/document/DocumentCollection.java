package dev.sweety.sql4j.document;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * High-performance Document / NoSQL Collection API backed by SQL4J relational tables.
 * Supports storing rich objects, maps, records, and YAML/JSON configuration sections.
 *
 * @param <T>  The document payload type.
 * @param <ID> The document primary key identifier type (e.g. String, UUID, Long).
 */
public interface DocumentCollection<T, ID> {

    /**
     * The name of this collection.
     */
    String name();

    /**
     * The serialization format (YAML, JSON, RAW) of documents in this collection.
     */
    DocumentFormat format();

    /**
     * Stores or updates a document with the given identifier.
     */
    void put(ID id, T document);

    /**
     * Asynchronously stores or updates a document.
     */
    CompletableFuture<Void> putAsync(ID id, T document);

    /**
     * Retrieves a document by its identifier.
     */
    Optional<T> get(ID id);

    /**
     * Asynchronously retrieves a document by its identifier.
     */
    CompletableFuture<Optional<T>> getAsync(ID id);

    /**
     * Retrieves a document by its identifier, or creates and stores a default if missing.
     */
    T computeIfAbsent(ID id, java.util.function.Function<ID, T> mappingFunction);

    /**
     * Deletes a document by its identifier.
     * @return {@code true} if a document was deleted, {@code false} if it didn't exist.
     */
    boolean delete(ID id);

    /**
     * Asynchronously deletes a document by its identifier.
     */
    CompletableFuture<Boolean> deleteAsync(ID id);

    /**
     * Checks if a document with the given identifier exists in the collection.
     */
    boolean exists(ID id);

    /**
     * Retrieves all documents currently stored in this collection.
     */
    List<T> findAll();

    /**
     * Retrieves a map of all document IDs to their document payloads.
     */
    Map<ID, T> findAllMap();

    /**
     * Finds all documents matching the given in-memory filter predicate.
     */
    List<T> find(Predicate<T> filter);

    /**
     * Returns the total count of documents in this collection.
     */
    long count();

    /**
     * Clears all documents from this collection.
     */
    void clear();

    /**
     * Exports all documents in this collection to a single YAML or JSON file on disk.
     */
    void exportToFile(Path destination) throws IOException;

    /**
     * Imports documents from a YAML or JSON file on disk into this collection.
     */
    void importFromFile(Path source) throws IOException;
}
