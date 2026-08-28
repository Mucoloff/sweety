package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.Encrypted;
import dev.sweety.sql4j.api.shard.ShardKey;

@Table.Info(name = "sharded_user_accounts")
public class ShardedUserAccount {

    @Column.Info(name = "user_id", primaryKey = true)
    @ShardKey
    private long userId;

    @Column.Info(name = "username")
    private String username;

    @Column.Info(name = "email")
    @Encrypted
    private String email;

    public ShardedUserAccount() {}

    public ShardedUserAccount(long userId, String username, String email) {
        this.userId = userId;
        this.username = username;
        this.email = email;
    }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
