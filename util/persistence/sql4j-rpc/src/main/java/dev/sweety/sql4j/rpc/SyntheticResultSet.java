package dev.sweety.sql4j.rpc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A read-only {@link ResultSet} backed by a pre-materialized {@code Object[][]} matrix.
 * Used by {@link RpcPreparedStatement} to return RPC query results to sql4j's entity mappers.
 *
 * <p>Only the methods actually called by sql4j's entity mapping code are implemented;
 * everything else throws {@link SQLFeatureNotSupportedException}.
 */
public final class SyntheticResultSet implements ResultSet {

    private final Object[][] rows;
    private final String[] columnNames;
    private final Map<String, Integer> columnIndex;
    private int cursor = -1;
    private boolean lastWasNull;

    /** Wrap the provided rows matrix. {@code rows[r][c]} is column {@code c+1} of row {@code r}. */
    public SyntheticResultSet(Object[][] rows) {
        this(rows, null);
    }

    /**
     * Wrap the rows matrix together with the real column names, enabling label-based lookups
     * ({@link #findColumn(String)}, {@code getObject(String)}). {@code columnNames} may be
     * {@code null}, in which case only index-based access is available.
     */
    public SyntheticResultSet(Object[][] rows, String[] columnNames) {
        this.rows = rows;
        int colCount = columnNames != null ? columnNames.length
                : (rows.length > 0 ? rows[0].length : 0);
        this.columnNames = columnNames != null ? columnNames.clone() : new String[colCount];
        this.columnIndex = new HashMap<>(this.columnNames.length * 2);
        for (int i = 0; i < this.columnNames.length; i++) {
            String name = this.columnNames[i];
            if (name != null) columnIndex.putIfAbsent(name.toLowerCase(Locale.ROOT), i + 1);
        }
    }

    /** Single-value result set for generated-key responses. */
    public static SyntheticResultSet singleValue(Object value) {
        return new SyntheticResultSet(new Object[][]{{value}});
    }

    @Override
    public boolean next() {
        cursor++;
        return cursor < rows.length;
    }

    @Override
    public void close() { /* no-op */ }

    @Override
    public boolean wasNull() {
        return lastWasNull;
    }

    @Override
    public Object getObject(int columnIndex) {
        Object v = rows[cursor][columnIndex - 1];
        lastWasNull = v == null;
        return v;
    }

    @Override
    public Object getObject(String columnLabel) {
        return getObject(findColumn(columnLabel));
    }

    @Override
    public String getString(int columnIndex) {
        Object v = getObject(columnIndex);
        return v == null ? null : v.toString();
    }

    @Override
    public boolean getBoolean(int columnIndex) {
        Object v = getObject(columnIndex);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(v.toString());
    }

    @Override
    public byte getByte(int columnIndex) {
        Object v = getObject(columnIndex);
        return v == null ? 0 : ((Number) v).byteValue();
    }

    @Override
    public short getShort(int columnIndex) {
        Object v = getObject(columnIndex);
        return v == null ? 0 : ((Number) v).shortValue();
    }

    @Override
    public int getInt(int columnIndex) {
        Object v = getObject(columnIndex);
        return v == null ? 0 : ((Number) v).intValue();
    }

    @Override
    public long getLong(int columnIndex) {
        Object v = getObject(columnIndex);
        return v == null ? 0L : ((Number) v).longValue();
    }

    @Override
    public float getFloat(int columnIndex) {
        Object v = getObject(columnIndex);
        return v == null ? 0f : ((Number) v).floatValue();
    }

    @Override
    public double getDouble(int columnIndex) {
        Object v = getObject(columnIndex);
        return v == null ? 0.0 : ((Number) v).doubleValue();
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale) {
        return getBigDecimal(columnIndex);
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) {
        Object v = getObject(columnIndex);
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) {
        Object v = getObject(columnIndex);
        if (v == null) return null;
        if (v instanceof Timestamp t) return t;
        if (v instanceof Long l) return new Timestamp(l);
        return null;
    }

    @Override
    public ResultSetMetaData getMetaData() {
        return new SyntheticMetaData(columnNames);
    }

    // ─── Unsupported ─────────────────────────────────────────────────────────

    private static SQLFeatureNotSupportedException unsupported(String method) {
        return new SQLFeatureNotSupportedException("SyntheticResultSet." + method + " not supported");
    }

    @Override public byte[] getBytes(int col) { throw new UnsupportedOperationException(); }
    @Override public Date getDate(int col) { throw new UnsupportedOperationException(); }
    @Override public Time getTime(int col) { throw new UnsupportedOperationException(); }
    @Override public InputStream getAsciiStream(int col) { throw new UnsupportedOperationException(); }
    @Override public InputStream getUnicodeStream(int col) { throw new UnsupportedOperationException(); }
    @Override public InputStream getBinaryStream(int col) { throw new UnsupportedOperationException(); }
    @Override public String getString(String col) { return getString(findColumn(col)); }
    @Override public boolean getBoolean(String col) { return getBoolean(findColumn(col)); }
    @Override public byte getByte(String col) { return getByte(findColumn(col)); }
    @Override public short getShort(String col) { return getShort(findColumn(col)); }
    @Override public int getInt(String col) { return getInt(findColumn(col)); }
    @Override public long getLong(String col) { return getLong(findColumn(col)); }
    @Override public float getFloat(String col) { return getFloat(findColumn(col)); }
    @Override public double getDouble(String col) { return getDouble(findColumn(col)); }
    @Override public BigDecimal getBigDecimal(String col, int scale) { return getBigDecimal(findColumn(col)); }
    @Override public byte[] getBytes(String col) { throw new UnsupportedOperationException(); }
    @Override public Date getDate(String col) { throw new UnsupportedOperationException(); }
    @Override public Time getTime(String col) { throw new UnsupportedOperationException(); }
    @Override public Timestamp getTimestamp(String col) { return getTimestamp(findColumn(col)); }
    @Override public InputStream getAsciiStream(String col) { throw new UnsupportedOperationException(); }
    @Override public InputStream getUnicodeStream(String col) { throw new UnsupportedOperationException(); }
    @Override public InputStream getBinaryStream(String col) { throw new UnsupportedOperationException(); }
    @Override public SQLWarning getWarnings() { return null; }
    @Override public void clearWarnings() {}
    @Override public String getCursorName() { throw new UnsupportedOperationException(); }
    @Override public Reader getCharacterStream(int col) { throw new UnsupportedOperationException(); }
    @Override public Reader getCharacterStream(String col) { throw new UnsupportedOperationException(); }
    @Override public BigDecimal getBigDecimal(String col) { return getBigDecimal(findColumn(col)); }
    @Override public boolean isBeforeFirst() { return cursor < 0; }
    @Override public boolean isAfterLast() { return cursor >= rows.length; }
    @Override public boolean isFirst() { return cursor == 0; }
    @Override public boolean isLast() { return cursor == rows.length - 1; }
    @Override public void beforeFirst() { cursor = -1; }
    @Override public void afterLast() { cursor = rows.length; }
    @Override public boolean first() { cursor = 0; return rows.length > 0; }
    @Override public boolean last() { cursor = rows.length - 1; return rows.length > 0; }
    @Override public int getRow() { return cursor + 1; }
    @Override public boolean absolute(int row) { cursor = row - 1; return cursor >= 0 && cursor < rows.length; }
    @Override public boolean relative(int rows) { cursor += rows; return cursor >= 0 && cursor < this.rows.length; }
    @Override public boolean previous() { cursor--; return cursor >= 0; }
    @Override public void setFetchDirection(int dir) {}
    @Override public int getFetchDirection() { return FETCH_FORWARD; }
    @Override public void setFetchSize(int rows) {}
    @Override public int getFetchSize() { return 0; }
    @Override public int getType() { return TYPE_FORWARD_ONLY; }
    @Override public int getConcurrency() { return CONCUR_READ_ONLY; }
    @Override public boolean rowUpdated() { return false; }
    @Override public boolean rowInserted() { return false; }
    @Override public boolean rowDeleted() { return false; }
    @Override public void updateNull(int col) { throw new UnsupportedOperationException(); }
    @Override public void updateBoolean(int col, boolean x) { throw new UnsupportedOperationException(); }
    @Override public void updateByte(int col, byte x) { throw new UnsupportedOperationException(); }
    @Override public void updateShort(int col, short x) { throw new UnsupportedOperationException(); }
    @Override public void updateInt(int col, int x) { throw new UnsupportedOperationException(); }
    @Override public void updateLong(int col, long x) { throw new UnsupportedOperationException(); }
    @Override public void updateFloat(int col, float x) { throw new UnsupportedOperationException(); }
    @Override public void updateDouble(int col, double x) { throw new UnsupportedOperationException(); }
    @Override public void updateBigDecimal(int col, BigDecimal x) { throw new UnsupportedOperationException(); }
    @Override public void updateString(int col, String x) { throw new UnsupportedOperationException(); }
    @Override public void updateBytes(int col, byte[] x) { throw new UnsupportedOperationException(); }
    @Override public void updateDate(int col, Date x) { throw new UnsupportedOperationException(); }
    @Override public void updateTime(int col, Time x) { throw new UnsupportedOperationException(); }
    @Override public void updateTimestamp(int col, Timestamp x) { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(int col, InputStream x, int l) { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(int col, InputStream x, int l) { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(int col, Reader x, int l) { throw new UnsupportedOperationException(); }
    @Override public void updateObject(int col, Object x, int scale) { throw new UnsupportedOperationException(); }
    @Override public void updateObject(int col, Object x) { throw new UnsupportedOperationException(); }
    @Override public void updateNull(String col) { throw new UnsupportedOperationException(); }
    @Override public void updateBoolean(String col, boolean x) { throw new UnsupportedOperationException(); }
    @Override public void updateByte(String col, byte x) { throw new UnsupportedOperationException(); }
    @Override public void updateShort(String col, short x) { throw new UnsupportedOperationException(); }
    @Override public void updateInt(String col, int x) { throw new UnsupportedOperationException(); }
    @Override public void updateLong(String col, long x) { throw new UnsupportedOperationException(); }
    @Override public void updateFloat(String col, float x) { throw new UnsupportedOperationException(); }
    @Override public void updateDouble(String col, double x) { throw new UnsupportedOperationException(); }
    @Override public void updateBigDecimal(String col, BigDecimal x) { throw new UnsupportedOperationException(); }
    @Override public void updateString(String col, String x) { throw new UnsupportedOperationException(); }
    @Override public void updateBytes(String col, byte[] x) { throw new UnsupportedOperationException(); }
    @Override public void updateDate(String col, Date x) { throw new UnsupportedOperationException(); }
    @Override public void updateTime(String col, Time x) { throw new UnsupportedOperationException(); }
    @Override public void updateTimestamp(String col, Timestamp x) { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(String col, InputStream x, int l) { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(String col, InputStream x, int l) { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(String col, Reader x, int l) { throw new UnsupportedOperationException(); }
    @Override public void updateObject(String col, Object x, int scale) { throw new UnsupportedOperationException(); }
    @Override public void updateObject(String col, Object x) { throw new UnsupportedOperationException(); }
    @Override public void insertRow() { throw new UnsupportedOperationException(); }
    @Override public void updateRow() { throw new UnsupportedOperationException(); }
    @Override public void deleteRow() { throw new UnsupportedOperationException(); }
    @Override public void refreshRow() { throw new UnsupportedOperationException(); }
    @Override public void cancelRowUpdates() { throw new UnsupportedOperationException(); }
    @Override public void moveToInsertRow() { throw new UnsupportedOperationException(); }
    @Override public void moveToCurrentRow() { throw new UnsupportedOperationException(); }
    @Override public Statement getStatement() { return null; }
    @Override public Object getObject(int col, Map<String, Class<?>> m) { return getObject(col); }
    @Override public Ref getRef(int col) { throw new UnsupportedOperationException(); }
    @Override public Blob getBlob(int col) { throw new UnsupportedOperationException(); }
    @Override public Clob getClob(int col) { throw new UnsupportedOperationException(); }
    @Override public Array getArray(int col) { throw new UnsupportedOperationException(); }
    @Override public Object getObject(String col, Map<String, Class<?>> m) { return getObject(findColumn(col)); }
    @Override public Ref getRef(String col) { throw new UnsupportedOperationException(); }
    @Override public Blob getBlob(String col) { throw new UnsupportedOperationException(); }
    @Override public Clob getClob(String col) { throw new UnsupportedOperationException(); }
    @Override public Array getArray(String col) { throw new UnsupportedOperationException(); }
    @Override public Date getDate(int col, Calendar cal) { throw new UnsupportedOperationException(); }
    @Override public Date getDate(String col, Calendar cal) { throw new UnsupportedOperationException(); }
    @Override public Time getTime(int col, Calendar cal) { throw new UnsupportedOperationException(); }
    @Override public Time getTime(String col, Calendar cal) { throw new UnsupportedOperationException(); }
    @Override public Timestamp getTimestamp(int col, Calendar cal) { throw new UnsupportedOperationException(); }
    @Override public Timestamp getTimestamp(String col, Calendar cal) { throw new UnsupportedOperationException(); }
    @Override public URL getURL(int col) { throw new UnsupportedOperationException(); }
    @Override public URL getURL(String col) { throw new UnsupportedOperationException(); }
    @Override public void updateRef(int col, Ref x) { throw new UnsupportedOperationException(); }
    @Override public void updateRef(String col, Ref x) { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(int col, Blob x) { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(String col, Blob x) { throw new UnsupportedOperationException(); }
    @Override public void updateClob(int col, Clob x) { throw new UnsupportedOperationException(); }
    @Override public void updateClob(String col, Clob x) { throw new UnsupportedOperationException(); }
    @Override public void updateArray(int col, Array x) { throw new UnsupportedOperationException(); }
    @Override public void updateArray(String col, Array x) { throw new UnsupportedOperationException(); }
    @Override public RowId getRowId(int col) { throw new UnsupportedOperationException(); }
    @Override public RowId getRowId(String col) { throw new UnsupportedOperationException(); }
    @Override public void updateRowId(int col, RowId x) { throw new UnsupportedOperationException(); }
    @Override public void updateRowId(String col, RowId x) { throw new UnsupportedOperationException(); }
    @Override public int getHoldability() { return HOLD_CURSORS_OVER_COMMIT; }
    @Override public boolean isClosed() { return false; }
    @Override public void updateNString(int col, String x) { throw new UnsupportedOperationException(); }
    @Override public void updateNString(String col, String x) { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(int col, NClob x) { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(String col, NClob x) { throw new UnsupportedOperationException(); }
    @Override public NClob getNClob(int col) { throw new UnsupportedOperationException(); }
    @Override public NClob getNClob(String col) { throw new UnsupportedOperationException(); }
    @Override public SQLXML getSQLXML(int col) { throw new UnsupportedOperationException(); }
    @Override public SQLXML getSQLXML(String col) { throw new UnsupportedOperationException(); }
    @Override public void updateSQLXML(int col, SQLXML x) { throw new UnsupportedOperationException(); }
    @Override public void updateSQLXML(String col, SQLXML x) { throw new UnsupportedOperationException(); }
    @Override public String getNString(int col) { throw new UnsupportedOperationException(); }
    @Override public String getNString(String col) { throw new UnsupportedOperationException(); }
    @Override public Reader getNCharacterStream(int col) { throw new UnsupportedOperationException(); }
    @Override public Reader getNCharacterStream(String col) { throw new UnsupportedOperationException(); }
    @Override public void updateNCharacterStream(int col, Reader x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateNCharacterStream(String col, Reader x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(int col, InputStream x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(int col, InputStream x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(int col, Reader x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(String col, InputStream x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(String col, InputStream x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(String col, Reader x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(int col, InputStream x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(String col, InputStream x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateClob(int col, Reader x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateClob(String col, Reader x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(int col, Reader x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(String col, Reader x, long l) { throw new UnsupportedOperationException(); }
    @Override public void updateNCharacterStream(int col, Reader x) { throw new UnsupportedOperationException(); }
    @Override public void updateNCharacterStream(String col, Reader x) { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(int col, InputStream x) { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(int col, InputStream x) { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(int col, Reader x) { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(String col, InputStream x) { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(String col, InputStream x) { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(String col, Reader x) { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(int col, InputStream x) { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(String col, InputStream x) { throw new UnsupportedOperationException(); }
    @Override public void updateClob(int col, Reader x) { throw new UnsupportedOperationException(); }
    @Override public void updateClob(String col, Reader x) { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(int col, Reader x) { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(String col, Reader x) { throw new UnsupportedOperationException(); }
    @Override public <T> T getObject(int col, Class<T> type) { return type.cast(getObject(col)); }
    @Override public <T> T getObject(String col, Class<T> type) { return type.cast(getObject(findColumn(col))); }

    /** 1-based, case-insensitive column lookup by name. */
    @Override public int findColumn(String columnLabel) {
        Integer index = columnLabel != null ? columnIndex.get(columnLabel.toLowerCase(Locale.ROOT)) : null;
        if (index != null) return index;
        throw new UnsupportedOperationException(
                "SyntheticResultSet: no column named '" + columnLabel + "'");
    }
    @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }

    // ─── Minimal metadata ────────────────────────────────────────────────────

    private static final class SyntheticMetaData implements ResultSetMetaData {
        private final String[] columnNames;

        SyntheticMetaData(String[] columnNames) {
            this.columnNames = columnNames != null ? columnNames : new String[0];
        }

        private String nameOf(int col) {
            String name = (col >= 1 && col <= columnNames.length) ? columnNames[col - 1] : null;
            return (name == null || name.isEmpty()) ? "col" + col : name;
        }

        @Override public int getColumnCount() { return columnNames.length; }
        @Override public boolean isAutoIncrement(int col) { return false; }
        @Override public boolean isCaseSensitive(int col) { return true; }
        @Override public boolean isSearchable(int col) { return true; }
        @Override public boolean isCurrency(int col) { return false; }
        @Override public int isNullable(int col) { return columnNullable; }
        @Override public boolean isSigned(int col) { return true; }
        @Override public int getColumnDisplaySize(int col) { return 255; }
        @Override public String getColumnLabel(int col) { return nameOf(col); }
        @Override public String getColumnName(int col) { return nameOf(col); }
        @Override public String getSchemaName(int col) { return ""; }
        @Override public int getPrecision(int col) { return 0; }
        @Override public int getScale(int col) { return 0; }
        @Override public String getTableName(int col) { return ""; }
        @Override public String getCatalogName(int col) { return ""; }
        @Override public int getColumnType(int col) { return Types.OTHER; }
        @Override public String getColumnTypeName(int col) { return "OBJECT"; }
        @Override public boolean isReadOnly(int col) { return true; }
        @Override public boolean isWritable(int col) { return false; }
        @Override public boolean isDefinitelyWritable(int col) { return false; }
        @Override public String getColumnClassName(int col) { return Object.class.getName(); }
        @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
