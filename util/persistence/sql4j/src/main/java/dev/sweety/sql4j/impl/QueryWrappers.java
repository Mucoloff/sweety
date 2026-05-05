package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.query.InsertQuery;
import dev.sweety.sql4j.api.query.UpdateQuery;
import dev.sweety.sql4j.api.query.DeleteQuery;
import dev.sweety.sql4j.api.query.MutationResult;
import java.util.concurrent.CompletableFuture;

class InsertQueryWrapper<T> implements InsertQuery<T> {
    private final InsertQuery<T> delegate;
    private final Runnable postAction;

    InsertQueryWrapper(InsertQuery<T> delegate, Runnable postAction) {
        this.delegate = delegate;
        this.postAction = postAction;
    }

    @Override
    public CompletableFuture<MutationResult<T>> execute(SqlConnection con) {
        return delegate.execute(con).thenApply(res -> {
            postAction.run();
            return res;
        });
    }

    @Override public String sql() { return delegate.sql(); }
    @Override public void bind(java.sql.PreparedStatement ps) throws java.sql.SQLException { delegate.bind(ps); }
    @Override public MutationResult<T> execute(java.sql.PreparedStatement ps) throws java.sql.SQLException { 
        MutationResult<T> res = delegate.execute(ps);
        postAction.run();
        return res;
    }
}

class UpdateQueryWrapper<T> implements UpdateQuery<T> {
    private final UpdateQuery<T> delegate;
    private final Runnable postAction;

    UpdateQueryWrapper(UpdateQuery<T> delegate, Runnable postAction) {
        this.delegate = delegate;
        this.postAction = postAction;
    }

    @Override
    public CompletableFuture<Integer> execute(SqlConnection con) {
        return delegate.execute(con).thenApply(res -> {
            if (res > 0) postAction.run();
            return res;
        });
    }

    @Override public String sql() { return delegate.sql(); }
    @Override public void bind(java.sql.PreparedStatement ps) throws java.sql.SQLException { delegate.bind(ps); }
    @Override public Integer execute(java.sql.PreparedStatement ps) throws java.sql.SQLException { 
        Integer res = delegate.execute(ps);
        if (res > 0) postAction.run();
        return res;
    }
}

class DeleteQueryWrapper<T> implements DeleteQuery<T> {
    private final DeleteQuery<T> delegate;
    private final Runnable postAction;

    DeleteQueryWrapper(DeleteQuery<T> delegate, Runnable postAction) {
        this.delegate = delegate;
        this.postAction = postAction;
    }

    @Override
    public CompletableFuture<Integer> execute(SqlConnection con) {
        return delegate.execute(con).thenApply(res -> {
            if (res > 0) postAction.run();
            return res;
        });
    }

    @Override public String sql() { return delegate.sql(); }
    @Override public void bind(java.sql.PreparedStatement ps) throws java.sql.SQLException { delegate.bind(ps); }
    @Override public Integer execute(java.sql.PreparedStatement ps) throws java.sql.SQLException { 
        Integer res = delegate.execute(ps);
        if (res > 0) postAction.run();
        return res;
    }

    @Override public DeleteQuery<T> hardDelete() { return new DeleteQueryWrapper<>(delegate.hardDelete(), postAction); }
    @Override public DeleteQuery<T> softDelete() { return new DeleteQueryWrapper<>(delegate.softDelete(), postAction); }
}
