package dev.sweety.microsoft.auth;

import java.util.Objects;
import java.util.UUID;

public class AltStorage {
    public String username;
    public UUID uuid;

    public String accessToken;
    public String refreshToken;
    public AccountType type;
    /** True when this account was authenticated via the device-code flow (uses DEVICE_CLIENT_ID). */
    public boolean deviceCode = false;

    public boolean isCracked() {
        return type == AccountType.CRACKED;
    }

    public AltStorage() {
    }

    public String username() {
        return username;
    }

    public UUID uuid() {
        return uuid;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public AccountType type() {
        return type;
    }

    public AltStorage(String username, UUID uuid, String accessToken, String refreshToken, AccountType type) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.type = type;
    }

    public AltStorage setUsername(String username) {
        this.username = username;
        return this;
    }

    public AltStorage setUuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public AltStorage setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        return this;
    }

    public AltStorage setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
        return this;
    }

    public AltStorage setType(AccountType type) {
        this.type = type;
        return this;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AltStorage that)) return false;

        return Objects.equals(username, that.username) && 
               Objects.equals(uuid, that.uuid) && 
               Objects.equals(accessToken, that.accessToken) && 
               Objects.equals(refreshToken, that.refreshToken) && 
               type == that.type;
    }

    @Override
    public String toString() {
        return "AltStorage{username='%s', uuid=%s, accessToken='%s', refreshToken='%s', type=%s}".formatted(username, uuid, accessToken, refreshToken, type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, uuid, accessToken, refreshToken, type);
    }
}