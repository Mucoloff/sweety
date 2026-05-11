package dev.sweety.sql4j.it.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.OneToMany;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Table.Info(name = "it_posts")
public class PostIT {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "title")
    private String title;

    @OneToMany(mappedBy = "post_id")
    private List<CommentIT> comments = new ArrayList<>();

    public PostIT() {}

    public PostIT(String title) { this.title = title; }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<CommentIT> getComments() { return comments; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostIT other)) return false;
        return Objects.equals(id, other.id) && Objects.equals(title, other.title);
    }

    @Override
    public int hashCode() { return Objects.hash(id, title); }

    @Override
    public String toString() { return "PostIT{id=" + id + ", title='" + title + "'}"; }
}
