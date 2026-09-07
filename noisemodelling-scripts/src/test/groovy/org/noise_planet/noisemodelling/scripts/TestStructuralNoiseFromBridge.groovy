/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.scripts

import groovy.sql.Sql
import org.junit.jupiter.api.Test
import org.noise_planet.noisemodelling.jdbc.NoiseMapDatabaseParameters
import org.noise_planet.noisemodelling.scripts.NoiseModelling.Noise_level_from_source
import org.noise_planet.noisemodelling.scripts.NoiseModelling.Structural_noise_from_bridge

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Structural_noise_from_bridge turns a deck + its heavy-vehicle traffic into an ASJ RTN-Model 2023
 * structure-radiated line source (one row per deck and period).
 */
class TestStructuralNoiseFromBridge extends JdbcTestCase {

    private static final List<Integer> BANDS = [63, 125, 250, 500, 1000, 2000, 4000, 8000]

    private void buildScene(Sql sql, double dayMv, double dayHgv) {
        sql.execute("DROP TABLE IF EXISTS BRIDGE_POINTS")
        sql.execute("CREATE TABLE BRIDGE_POINTS(PK INT, BRIDGE_PK INT, THE_GEOM GEOMETRY(POINT, 2154), " +
                "ABSOLUTE_DECK_HEIGHT DOUBLE, GIRDER_TYPE VARCHAR, SLAB_TYPE VARCHAR)")
        for (int i = 0; i < 3; i++) {
            sql.execute("INSERT INTO BRIDGE_POINTS VALUES(${i}, 1, " +
                    "ST_SetSRID(ST_MakePoint(${i * 50}, 20), 2154), 8.0, 'STEEL_BOX', 'STEEL')")
        }

        sql.execute("DROP TABLE IF EXISTS BRIDGE_TRAFFIC")
        sql.execute("CREATE TABLE BRIDGE_TRAFFIC(BRIDGE_PK INT, PERIOD VARCHAR, " +
                "MV DOUBLE, HGV DOUBLE, MV_SPD DOUBLE, HGV_SPD DOUBLE)")
        sql.execute("INSERT INTO BRIDGE_TRAFFIC VALUES(1, 'D', ${dayMv}, ${dayHgv}, 80, 80)")
        sql.execute("INSERT INTO BRIDGE_TRAFFIC VALUES(1, 'E', ${dayMv / 2}, ${dayHgv / 2}, 80, 80)")
        sql.execute("INSERT INTO BRIDGE_TRAFFIC VALUES(1, 'N', ${dayMv / 10}, ${dayHgv / 10}, 90, 90)")
    }

    private Map<String, Map<Integer, Double>> run(Sql sql) {
        new Structural_noise_from_bridge().exec(connection,
                ["tableBridgePoints": "BRIDGE_POINTS", "tableRoads": "BRIDGE_TRAFFIC"])
        Map<String, Map<Integer, Double>> byPeriod = [:]
        String cols = BANDS.collect { "HZ" + it }.join(", ")
        sql.rows("SELECT PERIOD, ST_GeometryType(THE_GEOM) AS GT, " + cols + " FROM BRIDGE_STRUCT_LW").each { row ->
            assertEquals("LINESTRING", (row["GT"] as String).replace("ST_", "").toUpperCase().take(10))
            Map<Integer, Double> spec = [:]
            BANDS.each { spec[it] = row["HZ" + it] as Double }
            byPeriod[row["PERIOD"] as String] = spec
        }
        return byPeriod
    }

    @Test
    void deckWithHeavyTrafficRadiatesAStructuralSource() {
        Sql sql = new Sql(connection)
        buildScene(sql, 200.0, 120.0)
        Map<String, Map<Integer, Double>> r = run(sql)

        assertEquals(3, r.size(), "one row per period (D/E/N)")
        r.each { period, spec ->
            BANDS.each { f ->
                double v = spec[f]
                assertTrue(Double.isFinite(v), "${period} ${f} Hz must be a finite level, got ${v}")
                assertTrue(v > -100.0 && v < 130.0, "${period} ${f} Hz out of range: ${v}")
            }
        }

        // structure-radiated noise is low-frequency dominant
        assertTrue(r["D"][63] > r["D"][4000],
                "structural noise should be louder at 63 Hz (${r["D"][63]}) than 4 kHz (${r["D"][4000]})")

        // more heavy traffic in the day than at night -> louder by day
        assertTrue(r["D"][125] > r["N"][125] + 3.0,
                "day should be louder than night: D=${r["D"][125]}, N=${r["N"][125]}")
    }

    @Test
    void moreHeavyVehiclesMeansMoreStructuralNoise() {
        Sql sql = new Sql(connection)
        buildScene(sql, 100.0, 50.0)
        double quiet = run(sql)["D"][125]
        buildScene(sql, 400.0, 200.0)
        double busy = run(sql)["D"][125]
        assertTrue(busy > quiet + 2.0, "4x the heavy traffic should raise the level: quiet=${quiet}, busy=${busy}")
    }

    @Test
    void noHeavyTrafficMeansSilentSource() {
        Sql sql = new Sql(connection)
        buildScene(sql, 0.0, 0.0)
        Map<String, Map<Integer, Double>> r = run(sql)
        r.each { period, spec ->
            BANDS.each { f -> assertEquals(-99.0, spec[f], 1e-6, "${period} ${f} Hz should be the silent sentinel") }
        }
    }

    /** Day-period per-octave receiver level for the given LW source table (HZD/HZE/HZN wide format). */
    private Map<Integer, Double> receiverLevel(Sql sql, String sourceTable) {
        String cols = BANDS.collect { "HZ" + it }.join(", ")
        sql.execute("DROP TABLE IF EXISTS " + NoiseMapDatabaseParameters.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME)
        new Noise_level_from_source().exec(connection,
                ["tableBuilding"           : "BUILDINGS",
                 "tableSources"            : sourceTable,
                 "tableReceivers"          : "RECEIVERS",
                 "tableDEM"                : "DEM",
                 "confSourcesZIsAltitude"  : true,
                 "confReceiversZIsAltitude": true,
                 "confMaxSrcDist"          : 250,
                 "confReflOrder"           : 0,
                 "confDiffHorizontal"      : true])
        def row = sql.firstRow("SELECT " + cols + " FROM " +
                NoiseMapDatabaseParameters.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME + " WHERE PERIOD = 'D'")
        Map<Integer, Double> spec = [:]
        BANDS.each { spec[it] = row["HZ" + it] as Double }
        return spec
    }

    @Test
    void structuralNoiseAddsToTheReceiverLevel() {
        Sql sql = new Sql(connection)
        buildScene(sql, 300.0, 200.0)

        sql.execute("DROP TABLE IF EXISTS BUILDINGS")
        sql.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POLYGON, 2154), HEIGHT DOUBLE)")
        sql.execute("DROP TABLE IF EXISTS DEM")
        sql.execute("CREATE TABLE DEM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ, 2154))")
        for (int x = -40; x <= 140; x += 30) {
            for (int y = -40; y <= 140; y += 30) {
                sql.execute("INSERT INTO DEM(THE_GEOM) VALUES (ST_SetSRID(ST_MakePoint(${x}, ${y}, 0), 2154))")
            }
        }
        sql.execute("DROP TABLE IF EXISTS RECEIVERS")
        sql.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ, 2154))")
        sql.execute("INSERT INTO RECEIVERS(THE_GEOM) VALUES (ST_SetSRID(ST_MakePoint(50, 35, 2), 2154))")

        // ASJ structural source line for the deck
        new Structural_noise_from_bridge().exec(connection,
                ["tableBridgePoints": "BRIDGE_POINTS", "tableRoads": "BRIDGE_TRAFFIC"])

        // wide (HZD/HZE/HZN) LW columns
        String wideCols = ["D", "E", "N"].collectMany { p -> BANDS.collect { "HZ${p}${it} DOUBLE" } }.join(", ")
        String wideNames = ["D", "E", "N"].collectMany { p -> BANDS.collect { "HZ${p}${it}" } }.join(", ")
        // a road-shaped vehicle spectrum: quiet at 63 Hz, peak near 1 kHz - so the ASJ structural
        // source (loudest at low frequency) is not simply buried by the tyre/road noise.
        Map<Integer, Double> vehSpec = [63: 74.0, 125: 82.0, 250: 87.0, 500: 90.0,
                                        1000: 92.0, 2000: 90.0, 4000: 85.0, 8000: 78.0]
        String vehVals = ["D", "E", "N"].collectMany { p -> BANDS.collect { vehSpec[it].toString() } }.join(", ")

        // vehicle-only source on the deck
        sql.execute("DROP TABLE IF EXISTS SRC_VEH")
        sql.execute("CREATE TABLE SRC_VEH(PK INT PRIMARY KEY, THE_GEOM GEOMETRY(LINESTRINGZ, 2154), " + wideCols + ")")
        sql.execute("INSERT INTO SRC_VEH VALUES(1, ST_SetSRID('LINESTRING Z(0 20 8.05, 100 20 8.05)'::geometry, 2154), " + vehVals + ")")

        // vehicles + the structural source, pivoting BRIDGE_STRUCT_LW's period rows into wide columns
        String pivot = ["D", "E", "N"].collectMany { p ->
            BANDS.collect { "MAX(CASE WHEN PERIOD='${p}' THEN HZ${it} END) AS HZ${p}${it}" }
        }.join(", ")
        sql.execute("DROP TABLE IF EXISTS SRC_ALL")
        sql.execute("CREATE TABLE SRC_ALL(PK INT PRIMARY KEY, THE_GEOM GEOMETRY(LINESTRINGZ, 2154), " + wideCols + ")")
        sql.execute("INSERT INTO SRC_ALL SELECT * FROM SRC_VEH")
        sql.execute("INSERT INTO SRC_ALL(PK, THE_GEOM, " + wideNames + ") " +
                "SELECT 2, ST_SetSRID(THE_GEOM, 2154), " + pivot + " FROM BRIDGE_STRUCT_LW GROUP BY THE_GEOM")

        Map<Integer, Double> veh = receiverLevel(sql, "SRC_VEH")
        Map<Integer, Double> tot = receiverLevel(sql, "SRC_ALL")

        // adding a second real source can only raise a receiver level
        BANDS.each { f ->
            assertTrue(tot[f] >= veh[f] - 0.05,
                    "${f} Hz: adding the structural source lowered the level: veh=${veh[f]} tot=${tot[f]}")
        }
        // the ASJ steel-bridge source is low-frequency: near the deck it rivals the tyre/road
        // noise at 63 Hz and lifts that band by several dB, while barely touching 4 kHz.
        double lowGain = tot[63] - veh[63]
        double highGain = tot[4000] - veh[4000]
        assertTrue(lowGain > 3.0,
                "structural noise should lift the 63 Hz band by several dB: veh=${veh[63]} tot=${tot[63]} (gain ${lowGain})")
        assertTrue(highGain < 0.5,
                "structural noise should barely touch 4 kHz: veh=${veh[4000]} tot=${tot[4000]} (gain ${highGain})")
    }
}
