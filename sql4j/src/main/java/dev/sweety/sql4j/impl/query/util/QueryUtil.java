package dev.sweety.sql4j.impl.query.util;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.query.functions.QueryBinder;
import dev.sweety.sql4j.api.query.functions.QueryExecutor;
import dev.sweety.sql4j.impl.query.ParamQuery;

import java.util.concurrent.CompletableFuture;

public class QueryUtil {

    public static <T> CompletableFuture<T> execute(final SqlConnection connection,final  String query,final  QueryBinder bind,final  QueryExecutor<T> execute) {
        return connection.executeAsync(generate(query, bind, execute));
    }

    public static <T> Query<T> generate(final String query,final  QueryBinder bind,final  QueryExecutor<T> execute) {
        return new ParamQuery<>(query, bind, execute);
    }

}
