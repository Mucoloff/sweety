package dev.sweety.sql4j.it.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.ManyToOne;

import java.util.Objects;

@Table.Info(name = "it_comments")
public class CommentIT {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "text")
    private String text;

    @ManyToOne(columnName = "post_id")
    private PostIT post;

    public CommentIT() {}

    public CommentIT(String text, PostIT post) {
        this.text = text;
        this.post = post;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public PostIT getPost() { return post; }
    public void setPost(PostIT post) { this.post = post; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommentIT other)) return false;
        return Objects.equals(id, other.id) && Objects.equals(text, other.text);
    }

    @Override
    public int hashCode() { return Objects.hash(id, text); }

    @Override
    public String toString() { return "CommentIT{id=" + id + ", text='" + text + "'}"; }
}
