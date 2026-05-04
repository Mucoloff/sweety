package dev.sweety.sql4j.impl.query.param;

import dev.sweety.sql4j.api.obj.Row;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a generic SQL execution via {@link ParamQuery}.
 *
 * <p>The {@code result} field (a {@link List} of {@link Row}) is populated for SELECT queries.
 * For DML queries (INSERT, UPDATE, DELETE), {@code affectedRows} is set and {@code result} is empty.
 */
public record QueryResult(byte info, int affectedRows, List<Integer> generatedKeys, List<Row> result) {

    public static QueryResult fromStatement(PreparedStatement pst) throws SQLException {
        boolean hasResultSet = pst.execute();
        List<Row> resultList;
        int affectedRows;
        List<Integer> generatedKeysList = new ArrayList<>();
        byte info = 0;

        if (hasResultSet) {
            info |= 0x01; // Has result set
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

    public boolean hasResultSet() {
        return (info & 0x01) != 0;
    }

    public boolean hasGeneratedKeys() {
        return (info & 0x02) != 0;
    }
}
