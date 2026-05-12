package dev.sweety.sql4j.entity.phaseC;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;

/** Minimal entity for Phase C batch-chunk and cache tests. */
@Table.Info(name = "c_items")
public class CItem {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "label")
    private String label;

    public CItem() {}
    public CItem(String label) { this.label = label; }

    public Integer getId()      { return id; }
    public void setId(Integer i){ this.id = i; }
    public String getLabel()    { return label; }
    public void setLabel(String l){ this.label = l; }
}
