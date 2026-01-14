package dev.sweety.sql4j.api.query.chain;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.query.Query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class QueryChain {

    private final List<Query<?>> queries = new ArrayList<>();

    private QueryChain(Query<?> first) {
        queries.add(first);
    }

    public static QueryChain start(Query<?> first) {
        return new QueryChain(first);
    }

    public QueryChain then(Query<?> next) {
        queries.add(next);
        return this;
    }

    public CompletableFuture<Void> execute(SqlConnection connection) {
        return CompletableFuture.runAsync(() -> {
            try (Connection con = connection.connection()) {
                for (Query<?> q : queries) {
                    try (PreparedStatement ps = con.prepareStatement(
                            q.sql(),
                            q.returnGeneratedKeys()
                                    ? PreparedStatement.RETURN_GENERATED_KEYS
                                    : PreparedStatement.NO_GENERATED_KEYS)) {

                        q.bind(ps);
                        q.execute(ps);
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, SqlConnection.executor(connection.dialectType()));
    }
}
