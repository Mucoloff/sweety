package dev.sweety.project.sql4j;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table.Info(name = "users")
public class User {

    @Column.Info(primaryKey = true, autoIncrement = true, name = "id")
    private long id;

    @Column.Info(name = "name")
    private String name;

    @Column.Info(name = "email")
    private String email;

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }
}
