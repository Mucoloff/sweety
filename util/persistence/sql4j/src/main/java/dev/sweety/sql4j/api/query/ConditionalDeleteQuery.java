package dev.sweety.sql4j.api.query;

public interface ConditionalDeleteQuery<T> extends DeleteQuery<T> {
    ConditionalDeleteQuery<T> where(Criterion criterion);
    
    @Override

    ConditionalDeleteQuery<T> hardDelete();
}
