package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.obj.Row;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a generic SQL execution via {@link ParamQuery}.
 *
 * <p>For SELECT queries, {@link #result()} contains the rows and {@link #affectedRows()} is 0.
 * For DML queries (INSERT, UPDATE, DELETE), {@link #affectedRows()} is the row-count and
 * {@link #result()} is empty.
 */
public record QueryResult(byte info, int affectedRows, List<Integer> generatedKeys, List<Row> result) {

    /**
     * Executes the statement and constructs a {@code QueryResult} from its outcome.
     */
    public static QueryResult fromStatement(PreparedStatement pst) throws SQLException {
        boolean hasResultSet = pst.execute();
        List<Row> resultList;
        int affectedRows;
        List<Integer> generatedKeysList = new ArrayList<>();
        byte info = 0;

        if (hasResultSet) {
            info |= 0x01;
            affectedRows = 0;
            try (ResultSet rs = pst.getResultSet()) {
                resultList = Row.fromResultSetAll(rs);
            }
        } else {
            affectedRows = pst.getUpdateCount();
            resultList = List.of();
        }

        try (ResultSet gkRs = pst.getGeneratedKeys()) {
            if (gkRs != null) {
                while (gkRs.next()) generatedKeysList.add(gkRs.getInt(1));
                if (!generatedKeysList.isEmpty()) info |= 0x02;
            }
        }

        return new QueryResult(info, affectedRows, generatedKeysList, resultList);
    }

    /** @return {@code true} if the execution produced a result set. */
    public boolean hasResultSet() {
        return (info & 0x01) != 0;
    }

    /** @return {@code true} if generated keys were returned. */
    public boolean hasGeneratedKeys() {
        return (info & 0x02) != 0;
    }
}
