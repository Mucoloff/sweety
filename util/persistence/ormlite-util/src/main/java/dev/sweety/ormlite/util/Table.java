package dev.sweety.ormlite.util;

public abstract class Table<ID> {

    public abstract ID getId();

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Table<?> entity && entity.getId().equals(getId());
    }

    public Table() {
    }
}