package dev.sweety.sql4j.entity.phaseC;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.ManyToOne;

/** Child entity used to verify {@code FetchType.EAGER} auto-join behaviour. */
@Table.Info(name = "c_eager_children")
public class EagerChild {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "body")
    private String body;

    @ManyToOne(columnName = "parent_id")
    private EagerParent parent;

    public EagerChild() {}
    public EagerChild(String body, EagerParent parent) {
        this.body = body;
        this.parent = parent;
    }

    public Integer getId()       { return id; }
    public void setId(Integer i) { this.id = i; }
    public String getBody()      { return body; }
    public void setBody(String b){ this.body = b; }
    public EagerParent getParent()         { return parent; }
    public void setParent(EagerParent p)   { this.parent = p; }
}
