package org.noise_planet.noisemodelling.propagation;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Minimal ResultSet fake implementing the getters used by SceneWithAttenuation.addSourceDb */
public abstract class FakeResultSet implements ResultSet {
    private final ResultSetMetaData meta;
    private final Map<Integer, Float> floatValues = new HashMap<>();
    private final Map<Integer, Double> doubleValues = new HashMap<>();
    private final Map<Integer, Boolean> booleanValues = new HashMap<>();
    private final Map<Integer, Long> longValues = new HashMap<>();
    private final Map<Integer, Integer> intValues = new HashMap<>();

    public FakeResultSet(ResultSetMetaData meta) {
        this.meta = meta;
    }

    public void setFloatValue(int index, float value) {
        floatValues.put(index, value);
    }

    public void setDoubleValue(int index, double value) {
        doubleValues.put(index, value);
    }

    public void setBooleanValue(int index, boolean value) {
        booleanValues.put(index, value);
    }

    public void setLongValue(int index, long value) {
        longValues.put(index, value);
    }

    public void setIntValue(int index, int value) {
        intValues.put(index, value);
    }

    @Override public ResultSetMetaData getMetaData() throws SQLException { return meta; }
    @Override public float getFloat(int columnIndex) throws SQLException { return floatValues.getOrDefault(columnIndex, 0.0f); }
    @Override public double getDouble(int columnIndex) throws SQLException { return doubleValues.getOrDefault(columnIndex, 0.0); }
    @Override public boolean getBoolean(int columnIndex) throws SQLException { return booleanValues.getOrDefault(columnIndex, false); }
    @Override public long getLong(int columnIndex) throws SQLException { return longValues.getOrDefault(columnIndex, 0L); }
    @Override public int getInt(int columnIndex) throws SQLException { return intValues.getOrDefault(columnIndex, 0); }

    // --- Many ResultSet methods are not needed for the test; throw when called ---
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not implemented"); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
    @Override public boolean next() throws SQLException { return false; }
    @Override public void close() throws SQLException { }
    @Override public boolean wasNull() throws SQLException { return false; }
    @Override public String getString(int columnIndex) throws SQLException { return null; }

    // Rest of methods: throw or return defaults
    @Override public byte getByte(int columnIndex) throws SQLException { return 0; }
    @Override public short getShort(int columnIndex) throws SQLException { return 0; }
    @Override public int getInt(String columnLabel) throws SQLException { return 0; }
    @Override public long getLong(String columnLabel) throws SQLException { return 0; }
    @Override public float getFloat(String columnLabel) throws SQLException { return 0; }
    @Override public double getDouble(String columnLabel) throws SQLException { return 0; }
    @Override public boolean getBoolean(String columnLabel) throws SQLException { return false; }
    @Override public String getString(String columnLabel) throws SQLException { return null; }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        // Minimal test stub: return null for requested typed object.
        // Tests in this project only use primitive getters; this stub prevents
        // ResultSet interface abstract method errors during compilation.
        return null;
    }

    // The JDBC ResultSet interface has many methods; implement minimal stubs to satisfy compile.
    @Override public int findColumn(String columnLabel) throws SQLException { return 0; }
    @Override public java.io.InputStream getAsciiStream(int columnIndex) throws SQLException { throw new SQLException("Not implemented"); }
    @Override public java.io.InputStream getUnicodeStream(int columnIndex) throws SQLException { throw new SQLException("Not implemented"); }
    @Override public java.io.InputStream getBinaryStream(int columnIndex) throws SQLException { throw new SQLException("Not implemented"); }
    // ... rest omitted for brevity; the testing JVM will not invoke them
}
