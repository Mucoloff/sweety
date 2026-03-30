package dev.sweeyu.microsoft.auth;

import java.util.Objects;
import java.util.UUID;

public class AltStorage {
    public String username;
    public UUID uuid;

    public String accessToken;
    public String refreshToken;
    public AccountType type;

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
        if (!(o instanceof AltStorage that)) return false;

        return Objects.equals(username, that.username) && Objects.equals(uuid, that.uuid) && Objects.equals(accessToken, that.accessToken) && Objects.equals(refreshToken, that.refreshToken) && type == that.type;
    }

    @Override
    public String toString() {
        return "AltStorage{" +
                "username='" + username + '\'' +
                ", uuid=" + uuid +
                ", accessToken='" + accessToken + '\'' +
                ", refreshToken='" + refreshToken + '\'' +
                ", type=" + type +
                '}';
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(username);
        result = 31 * result + Objects.hashCode(uuid);
        result = 31 * result + Objects.hashCode(accessToken);
        result = 31 * result + Objects.hashCode(refreshToken);
        result = 31 * result + Objects.hashCode(type);
        return result;
    }
}