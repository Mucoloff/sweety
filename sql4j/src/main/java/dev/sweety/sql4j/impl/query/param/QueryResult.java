package dev.sweety.sql4j.impl.query.param;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record QueryResult(byte info, int affectedRows, List<Integer> generatedKeys, List<Map<String, Object>> result) {

    public static QueryResult fromStatement(PreparedStatement pst) throws SQLException {
        boolean hasResultSet = pst.execute();
        List<Map<String, Object>> resultList = null;
        int affectedRows = 0;
        List<Integer> generatedKeysList = new ArrayList<>();
        byte info = 0;

        if (hasResultSet) {
            info |= 0x01; // Has result set
            try (ResultSet rs = pst.getResultSet()) {
                resultList = deserializeResultSet(rs);
            }

        } else {
            affectedRows = pst.getUpdateCount();
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

    public static List<Map<String, Object>> deserializeResultSet(ResultSet rs) throws SQLException {
        List<Map<String, Object>> resultsList = new ArrayList<>();
        int columnCount = rs.getMetaData().getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
            }
            resultsList.add(row);
        }

        return resultsList;
    }
}
