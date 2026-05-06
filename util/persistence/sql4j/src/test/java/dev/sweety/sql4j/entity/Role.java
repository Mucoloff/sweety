package dev.sweety.sql4j.entity;

public enum Role {
    USER, ADMIN;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }

    public static Role fromString(String name) {
        return Role.valueOf(name.toUpperCase());
    }
}
