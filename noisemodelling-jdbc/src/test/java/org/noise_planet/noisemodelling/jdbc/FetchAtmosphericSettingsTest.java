package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.propagation.AttenuationParameters;
import org.noise_planet.noisemodelling.jdbc.input.DefaultTableLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FetchAtmosphericSettingsTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(FetchAtmosphericSettingsTest.class);
    private Connection connection;

    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(
                FetchAtmosphericSettingsTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createAtmosphericTableAndInsert() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS ATMOSPHERIC_SETTINGS");
            st.execute("CREATE TABLE ATMOSPHERIC_SETTINGS (PERIOD VARCHAR(10), WINDROSE DOUBLE ARRAY, PRESSURE DOUBLE, HUMIDITY DOUBLE, GDISC BOOLEAN, PRIME2520 BOOLEAN, TEMPERATURE DOUBLE)");
        }
        String insertSQL = "INSERT INTO ATMOSPHERIC_SETTINGS (PERIOD, WINDROSE, PRESSURE, HUMIDITY, GDISC, PRIME2520, TEMPERATURE) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(insertSQL)) {
            Array sqlArr = connection.createArrayOf("DOUBLE", new Double[]{0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5});
            ps.setString(1, "D");
            ps.setArray(2, sqlArr);
            ps.setDouble(3, 101325.0);
            ps.setDouble(4, 60.0);
            ps.setBoolean(5, true);
            ps.setBoolean(6, false);
            ps.setDouble(7, 15.0);
            ps.executeUpdate();

            Array sqlArr2 = connection.createArrayOf("DOUBLE", new Double[]{0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2});
            ps.setString(1, "N");
            ps.setArray(2, sqlArr2);
            ps.setDouble(3, 101200.0);
            ps.setDouble(4, 50.0);
            ps.setBoolean(5, false);
            ps.setBoolean(6, true);
            ps.setDouble(7, 10.0);
            ps.executeUpdate();
        }
    }

    @Test
    public void testReadFromDatabaseDirect() throws Exception {
        createAtmosphericTableAndInsert();

        Map<String, AttenuationParameters> map = new HashMap<>();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM ATMOSPHERIC_SETTINGS ORDER BY PERIOD")) {
            while (rs.next()) {
                AttenuationParameters.readFromDatabase(rs, map);
            }
        }

        assertEquals(2, map.size());
        assertTrue(map.containsKey("D"));
        assertTrue(map.containsKey("N"));

        AttenuationParameters pD = map.get("D");
        assertEquals(15.0, pD.getTemperature(), 1e-9);
        assertEquals(60.0, pD.getHumidity(), 1e-9);
        assertEquals(101325.0, pD.getPressure(), 1e-9);
        assertEquals(16, pD.getWindRose().length);

        AttenuationParameters pN = map.get("N");
        assertEquals(10.0, pN.getTemperature(), 1e-9);
        assertEquals(50.0, pN.getHumidity(), 1e-9);
    }

    @Test
    public void testDefaultTableLoaderLoadAtmosphericTableSettingsViaReflection() throws Exception {
        createAtmosphericTableAndInsert();

        DefaultTableLoader loader = new DefaultTableLoader();

        // Call private method loadAtmosphericTableSettings via reflection
        Method m = DefaultTableLoader.class.getDeclaredMethod("loadAtmosphericTableSettings", Connection.class, String.class);
        m.setAccessible(true);
        m.invoke(loader, connection, "ATMOSPHERIC_SETTINGS");

        Map<String, AttenuationParameters> loaded = loader.getCnossosParametersPerPeriod();
        assertEquals(2, loaded.size());
        assertTrue(loaded.containsKey("D"));
        assertTrue(loaded.containsKey("N"));

        AttenuationParameters pD = loaded.get("D");
        assertEquals(15.0, pD.getTemperature(), 1e-9);
        LOGGER.info("Loaded atmospheric parameters for D: temp={} hum={} pressure={}", pD.getTemperature(), pD.getHumidity(), pD.getPressure());
    }
}
