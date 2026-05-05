package dev.sweety.sql4j.api.interceptor;

import dev.sweety.sql4j.api.query.Query;
import java.sql.Connection;

public interface QueryInterceptor {

    /**
     * Called before a query is executed.
     */
    default void preExecute(Query<?> query, Connection connection) {
    }

    /**
     * Called after a query has successfully executed.
     * @param query The query that was executed.
     * @param result The result of the execution.
     * @param durationNs Execution duration in nanoseconds.
     */
    default void postExecute(Query<?> query, Object result, long durationNs) {
    }

    /**
     * Called if a query execution fails.
     * @param query The query that failed.
     * @param error The exception thrown.
     * @param durationNs Execution duration until failure in nanoseconds.
     */
    default void onError(Query<?> query, Throwable error, long durationNs) {
    }
}
