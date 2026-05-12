package dev.sweety.sql4j.entity.phaseC;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.FetchType;
import dev.sweety.sql4j.api.obj.annotation.OneToMany;

import java.util.ArrayList;
import java.util.List;

/** Parent entity used to verify {@code FetchType.EAGER} auto-join behaviour. */
@Table.Info(name = "c_eager_parents")
public class EagerParent {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "title")
    private String title;

    @OneToMany(mappedBy = "parent_id", fetchType = FetchType.EAGER)
    private List<EagerChild> children = new ArrayList<>();

    public EagerParent() {}
    public EagerParent(String title) { this.title = title; }

    public Integer getId()          { return id; }
    public void setId(Integer i)    { this.id = i; }
    public String getTitle()        { return title; }
    public void setTitle(String t)  { this.title = t; }
    public List<EagerChild> getChildren() { return children; }
}
