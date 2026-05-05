package dev.sweety.sql4j.impl.query.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ResultSetStream<T> {

    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public static <T> Stream<T> create(Connection con, PreparedStatement ps, ResultSet rs, RowMapper<T> mapper) {
        Spliterator<T> spliterator = new Spliterators.AbstractSpliterator<T>(Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                try {
                    if (rs.next()) {
                        action.accept(mapper.map(rs));
                        return true;
                    }
                    return false;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        return StreamSupport.stream(spliterator, false).onClose(() -> {
            try {
                rs.close();
                ps.close();
                con.close();
            } catch (SQLException e) {
                // Ignore or log
            }
        });
    }
}
