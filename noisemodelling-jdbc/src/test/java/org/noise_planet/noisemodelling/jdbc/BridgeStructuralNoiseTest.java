/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.dBToW;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.wToDb;

/**
 * End-to-end for the bridge-deck emission-type split (merge_project.md §4.aa):
 * a road source on a deck is emitted twice - once at deck level (traffic noise,
 * {@code EMISSION_TYPE='ROAD'}) and once at the deck underside (structural noise,
 * {@code EMISSION_TYPE='BRIDGE'}) - both keyed on the same source pk. The
 * {@code checkIfEmissionIsAcceptable} gate routes each row to exactly one point.
 */
public class BridgeStructuralNoiseTest {

    private Connection connection;

    @BeforeEach
    public void tearUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(
                H2GISDBFactory.createSpatialDataBase(BridgeStructuralNoiseTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    /** ROAD only, BRIDGE only, or both structural + road rows in the emission table. */
    private enum Emit { ROAD_ONLY, BRIDGE_ONLY, BOTH }

    /**
     * @param downstreamBuilding when true, adds a building beyond the deck's far edge (y=25) so
     *                           a source under the deck must diffract both under the deck edge
     *                           and over the building - the bottom+top (BT) case - rather than
     *                           the single under-the-deck diffraction the base scene exercises.
     * @param receiverY          y-coordinate of the single receiver, POINTZ(50 receiverY 2).
     */
    private void createScene(Statement st, Emit emit, boolean downstreamBuilding, int receiverY) throws Exception {
        st.execute("DROP TABLE IF EXISTS BUILDINGS");
        st.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POLYGON), HEIGHT DOUBLE)");
        if (downstreamBuilding) {
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "('POLYGON((30 55, 70 55, 70 60, 30 60, 30 55))', 6.0)");
        }

        st.execute("DROP TABLE IF EXISTS DEM");
        st.execute("CREATE TABLE DEM(THE_GEOM GEOMETRY(POINTZ))");
        for (int x = -40; x <= 140; x += 30) {
            for (int y = -40; y <= 140; y += 30) {
                st.execute("INSERT INTO DEM VALUES ('POINTZ(" + x + " " + y + " 0)')");
            }
        }

        // one short line source on the deck (absolute Z ~ 10.6), inside the deck footprint (strip at y=20)
        st.execute("DROP TABLE IF EXISTS SOURCES_GEOM");
        st.execute("CREATE TABLE SOURCES_GEOM(PK INT PRIMARY KEY, THE_GEOM GEOMETRY(LINESTRINGZ))");
        st.execute("INSERT INTO SOURCES_GEOM VALUES(1, 'LINESTRING Z(40 20 10.6, 60 20 10.6)')");

        // emission table: per-period rows tagged ROAD (traffic) and/or BRIDGE (structural)
        st.execute("DROP TABLE IF EXISTS SOURCES_EMISSION");
        StringBuilder cols = new StringBuilder("PERIOD VARCHAR, IDSOURCE INT, EMISSION_TYPE VARCHAR");
        for (String f : new String[]{"63", "125", "250", "500", "1000", "2000", "4000", "8000"}) {
            cols.append(", LW").append(f).append(" DOUBLE");
        }
        st.execute("CREATE TABLE SOURCES_EMISSION(" + cols + ")");
        for (String p : new String[]{"D", "E", "N"}) {
            if (emit == Emit.ROAD_ONLY || emit == Emit.BOTH) {
                st.execute("INSERT INTO SOURCES_EMISSION VALUES('" + p + "', 1, 'ROAD', 90,90,90,90,90,90,90,90)");
            }
            if (emit == Emit.BRIDGE_ONLY || emit == Emit.BOTH) {
                st.execute("INSERT INTO SOURCES_EMISSION VALUES('" + p + "', 1, 'BRIDGE', 90,90,90,90,90,90,90,90)");
            }
        }

        // receiver on the ground, beyond the deck edge and below deck level
        st.execute("DROP TABLE IF EXISTS RECEIVERS");
        st.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ))");
        st.execute("INSERT INTO RECEIVERS(THE_GEOM) VALUES ('POINTZ(50 " + receiverY + " 2)')");

        // a straight deck at absolute altitude 10 m over the strip y in [15, 25]
        st.execute("DROP TABLE IF EXISTS BRIDGE_POINTS");
        st.execute("CREATE TABLE BRIDGE_POINTS(PK INT, BRIDGE_PK INT, THE_GEOM GEOMETRY(POINT), " +
                "ABSOLUTE_DECK_HEIGHT DOUBLE, RELATIVE_DECK_HEIGHT DOUBLE, DECK_THICKNESS DOUBLE, " +
                "RIGHT_WIDTH DOUBLE, LEFT_WIDTH DOUBLE, RIGHT_BARRIER_HEIGHT DOUBLE, LEFT_BARRIER_HEIGHT DOUBLE, " +
                "POSITION VARCHAR, GIRDER_TYPE VARCHAR, SLAB_TYPE VARCHAR)");
        for (int i = 0; i < 3; i++) {
            st.execute("INSERT INTO BRIDGE_POINTS VALUES(" + i + ", 100, 'POINT(" + (i * 50) + " 20)', " +
                    "10.0, NULL, 0.5, 5.0, 5.0, 1.0, 1.0, 'CENTER', 'STEEL_BOX', 'STEEL')");
        }
    }

    private double runAndGetLevel(Emit emit, boolean withBridge) throws Exception {
        return runAndGetLevel(emit, withBridge, false, 30);
    }

    private double runAndGetLevel(Emit emit, boolean withBridge, boolean downstreamBuilding, int receiverY) throws Exception {
        try (Statement st = connection.createStatement()) {
            createScene(st, emit, downstreamBuilding, receiverY);

            NoiseMapByReceiverMaker maker = new NoiseMapByReceiverMaker("BUILDINGS", "SOURCES_GEOM", "RECEIVERS");
            maker.sceneDatabaseInputSettings.setInputMode(SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW);
            maker.setSourcesEmissionTableName("SOURCES_EMISSION");
            maker.setDemTable("DEM");
            maker.setSourcesZIsAltitude(true);
            maker.setReceiversZIsAltitude(true);
            maker.setMaximumPropagationDistance(250.0);
            maker.setSoundReflectionOrder(0);
            maker.setThreadCount(1);
            maker.setFrequencyFieldPrepend("LW");
            maker.setComputeHorizontalDiffraction(true);
            maker.setComputeVerticalDiffraction(true);
            if (withBridge) {
                maker.setBridgePointsTableName("BRIDGE_POINTS");
            }

            maker.run(connection, new EmptyProgressVisitor());

            String levelTable = maker.getNoiseMapDatabaseParameters().receiversLevelTable;
            try (ResultSet rs = st.executeQuery("SELECT MAX(LEQ) FROM " + levelTable)) {
                assertTrue(rs.next(), "a level row is expected");
                return rs.getDouble(1);
            }
        }
    }

    @Test
    public void structuralNoiseRoutesToTheDeckUndersideWithoutDoubleCountingTheRoad() throws Exception {
        double roadOnly = runAndGetLevel(Emit.ROAD_ONLY, true);
        double bridgeOnly = runAndGetLevel(Emit.BRIDGE_ONLY, true);
        double both = runAndGetLevel(Emit.BOTH, true);

        // The BRIDGE row is emitted (from the deck-underside point) and measurably raises the level.
        assertTrue(both > roadOnly + 0.5,
                String.format("structural noise should add a contribution: roadOnly=%.2f both=%.2f", roadOnly, both));
        // The BRIDGE row alone still produces a level (it routes to the imaginary point, is not dropped).
        assertTrue(bridgeOnly > roadOnly - 25 && bridgeOnly < both,
                String.format("BRIDGE-only should route and contribute: bridgeOnly=%.2f roadOnly=%.2f both=%.2f",
                        bridgeOnly, roadOnly, both));
        // No double count / no cross-routing: BOTH == energy sum of the two single-type runs.
        double energySum = wToDb(dBToW(roadOnly) + dBToW(bridgeOnly));
        assertTrue(Math.abs(both - energySum) < 0.3,
                String.format("BOTH (%.2f) should equal the energy sum of ROAD (%.2f) and BRIDGE (%.2f) = %.2f",
                        both, roadOnly, bridgeOnly, energySum));
    }

    @Test
    public void aBridgeRowIsDroppedWhenTheSourceIsNotOnADeck() throws Exception {
        double roadWithDeck = runAndGetLevel(Emit.ROAD_ONLY, true);
        double bridgeNoDeck = runAndGetLevel(Emit.BRIDGE_ONLY, false);
        // With no deck the source is SOURCE_NOT_RELATED_TO_BRIDGE, which rejects BRIDGE rows: near silence.
        assertTrue(bridgeNoDeck < roadWithDeck - 40,
                String.format("a BRIDGE row with no deck should be dropped: bridgeNoDeck=%.2f roadWithDeck=%.2f",
                        bridgeNoDeck, roadWithDeck));
    }

    /**
     * The BT (bottom+top) diffraction geometry ({@code CnossosPathBuilder.spliceBridgeBottomEdge} +
     * {@code computeBridgeBottomTopRoutes}, merge_project.md §4.x) and the {@code EMISSION_TYPE}
     * structural-noise routing (§4.aa) are each covered separately elsewhere - the geometry-only
     * {@code BridgeAttenuationGoldenTest} always uses a flat 93 dB source, and this class's other
     * tests never place an obstacle beyond the deck. Neither exercises a structural-noise
     * ({@code EMISSION_TYPE='BRIDGE'}) source that must actually diffract both under the deck edge
     * and over a separate downstream obstacle to reach the receiver - this test closes that gap.
     *
     * <p>Receiver fixed at (50, 90, 2), well beyond a 6 m building spanning y=[55,60]; only the
     * building's presence differs between the two runs.
     */
    @Test
    public void structuralNoiseDiffractsUnderTheDeckAndOverADownstreamBuilding() throws Exception {
        double withoutBuilding = runAndGetLevel(Emit.BRIDGE_ONLY, true, false, 90);
        double withBuilding = runAndGetLevel(Emit.BRIDGE_ONLY, true, true, 90);

        // The structural-noise path still reaches the receiver (BT found a path, not silence).
        assertTrue(withBuilding > -100,
                String.format("structural noise should still reach the receiver despite the extra " +
                        "obstacle (BT path expected): withBuilding=%.2f", withBuilding));
        // The added building measurably attenuates the level further, proving the second obstacle
        // - not just the deck edge - is genuinely accounted for by the propagation path.
        assertTrue(withBuilding < withoutBuilding - 1.0,
                String.format("a downstream building should add measurable attenuation over the " +
                        "deck-only case: withoutBuilding=%.2f withBuilding=%.2f", withoutBuilding, withBuilding));
    }
}
