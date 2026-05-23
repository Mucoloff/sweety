package dev.sweety.ormlite.util;

import java.util.Objects;

public abstract class Table<ID> {

    public abstract ID getId();

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        return obj instanceof Table<?> entity && Objects.equals(this.getId(), entity.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.getId());
    }

    public Table() {
    }
}