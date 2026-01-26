/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;


import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.print.DocFlavor.READER;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Bridge class.
 * Tests bridge construction, acoustic calculations, point position determination,
 * diffraction calculations, mirror image sources, and virtual source generation.
 */
public class BridgeTest {

    private GeometryFactory geometryFactory;
    private List<Double> defaultAlphas;
    private Connection connection;

    @BeforeEach
    public void setUp() throws Exception {
        geometryFactory = new GeometryFactory();
        defaultAlphas = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8);
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(BridgeTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // Test helper methods

    /**
     * Create test bridge points for a simple rectangular bridge.
     */
    private List<BridgePoint> createTestBridgePoints() {
        List<BridgePoint> points = new ArrayList<>();
        
        // Create bridge points matching testCreateBridgeFromDatabase: Y=20, X=0, 50, 100
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 20), 1, 100, 10.0, Double.NaN, 0.5, 5.0, 5.0, 1.0, 1.0, Bridge.GirderType.STEEL_BOX, Bridge.SlabType.STEEL);
        bp1.setPosition(BridgePoint.Position.CENTER);
        points.add(bp1);
        
        BridgePoint bp2 = new BridgePoint(new Coordinate(50, 20), 2, 100, 10.0, Double.NaN, 0.5, 5.0, 5.0, 1.0, 1.0, Bridge.GirderType.STEEL_BOX, Bridge.SlabType.STEEL);
        bp2.setPosition(BridgePoint.Position.CENTER);
        points.add(bp2);
        
        BridgePoint bp3 = new BridgePoint(new Coordinate(100, 20), 3, 100, 10.0, Double.NaN, 0.5, 5.0, 5.0, 1.0, 1.0, Bridge.GirderType.STEEL_BOX, Bridge.SlabType.STEEL);
        bp3.setPosition(BridgePoint.Position.CENTER);
        points.add(bp3);
        
        return points;
    }

    
    /**
     * Create test bridge points for a simple rectangular bridge.
     */
    private List<BridgePoint> createTestBridgePointsWithRelativeHeight() {
        List<BridgePoint> points = new ArrayList<>();
        
        // Create bridge points matching testCreateBridgeFromDatabase: Y=20, X=0, 50, 100
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 20), 1, 100, Double.NaN,10.0,  0.5, 5.0, 5.0, 1.0, 1.0, Bridge.GirderType.STEEL_BOX, Bridge.SlabType.STEEL);
        bp1.setPosition(BridgePoint.Position.CENTER);
        points.add(bp1);
        
        BridgePoint bp2 = new BridgePoint(new Coordinate(50, 20), 2, 100, Double.NaN, 10.0, 0.5, 5.0, 5.0, 1.0, 1.0, Bridge.GirderType.STEEL_BOX, Bridge.SlabType.STEEL);
        bp2.setPosition(BridgePoint.Position.CENTER);
        points.add(bp2);
        
        BridgePoint bp3 = new BridgePoint(new Coordinate(100, 20), 3, 100, Double.NaN, 10.0, 0.5, 5.0, 5.0, 1.0, 1.0, Bridge.GirderType.STEEL_BOX, Bridge.SlabType.STEEL);
        bp3.setPosition(BridgePoint.Position.CENTER);
        points.add(bp3);
        
        return points;
    }

    private List<BridgePoint> createTestBridgePoints2() {
        List<BridgePoint> points = new ArrayList<>();
        
        // Create bridge points matching testCreateBridgeFromDatabase: Y=20, X=0, 50, 100
        BridgePoint bp1 = new BridgePoint(new Coordinate(70, 0), 1, 101, 10.0, 10.0, 0.5, 5.0, 5.0, 1.0, 1.0, Bridge.GirderType.CONCRETE_PLATE, Bridge.SlabType.CONCRETE);
        bp1.setPosition(BridgePoint.Position.CENTER);
        points.add(bp1);
        
        
        BridgePoint bp3 = new BridgePoint(new Coordinate(70, 80), 3, 101, 10.0, 10.0, 0.5, 5.0, 5.0, 1.0, 1.0, Bridge.GirderType.CONCRETE_PLATE, Bridge.SlabType.CONCRETE);
        bp3.setPosition(BridgePoint.Position.CENTER);
        points.add(bp3);
        
        return points;
    }

    
    /**
     * Create test bridge points for a simple rectangular bridge.
     */
    private List<BridgePoint> createTestBridgePointsFromDatabase(Connection connection) throws Exception {
        // Create BRIDGE_POINTS table with test data
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE BRIDGE_POINTS (" +
                    "PK LONG PRIMARY KEY, " +
                    "THE_GEOM GEOMETRY, " +
                    "BRIDGE_PK LONG, " +
                    "POSITION VARCHAR(10), " +
                    "ABSOLUTE_DECK_HEIGHT DOUBLE PRECISION, " +
                    "RELATIVE_DECK_HEIGHT DOUBLE PRECISION, " +
                    "DECK_THICKNESS DOUBLE PRECISION, " +
                    "RIGHT_WIDTH DOUBLE PRECISION, " +
                    "LEFT_WIDTH DOUBLE PRECISION, " +
                    "RIGHT_BARRIER_HEIGHT DOUBLE PRECISION, " +
                    "LEFT_BARRIER_HEIGHT DOUBLE PRECISION, " +
                    "GIRDER_TYPE VARCHAR(30), " +
                    "SLAB_TYPE VARCHAR(20))");

            // Insert bridge points for Bridge 100
            // Center points at Y=20, X=0, 50, 100
            st.execute("INSERT INTO BRIDGE_POINTS VALUES (1, ST_GeomFromText('POINT(0 20)'), 100, 'CENTER', 10.0, NULL, 0.5, 5.0, 5.0, 1.0, 1.0, 'STEEL_BOX', 'STEEL')");
            st.execute("INSERT INTO BRIDGE_POINTS VALUES (2, ST_GeomFromText('POINT(50 20)'), 100, 'CENTER', 10.0, NULL, 0.5, 5.0, 5.0, 1.0, 1.0, 'STEEL_BOX', 'STEEL')");
            st.execute("INSERT INTO BRIDGE_POINTS VALUES (3, ST_GeomFromText('POINT(100 20)'), 100, 'CENTER', 10.0, NULL, 0.5, 5.0, 5.0, 1.0, 1.0, 'STEEL_BOX', 'STEEL')");
        }

        // Load bridge points from database
        List<BridgePoint> bridgePointsList = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                    "SELECT PK, ST_X(THE_GEOM) as X, ST_Y(THE_GEOM) as Y, " +
                    "BRIDGE_PK, POSITION, ABSOLUTE_DECK_HEIGHT, RELATIVE_DECK_HEIGHT, " +
                    "DECK_THICKNESS, RIGHT_WIDTH, LEFT_WIDTH, RIGHT_BARRIER_HEIGHT, LEFT_BARRIER_HEIGHT, GIRDER_TYPE, SLAB_TYPE " +
                    "FROM BRIDGE_POINTS WHERE BRIDGE_PK=100 ORDER BY PK")) {

            while (rs.next()) {
                Coordinate coord = new Coordinate(rs.getDouble("X"), rs.getDouble("Y"));
                BridgePoint.Position position = BridgePoint.Position.valueOf(rs.getString("POSITION"));

                double absHeight = rs.getDouble("ABSOLUTE_DECK_HEIGHT");
                double relHeight = rs.getDouble("RELATIVE_DECK_HEIGHT");
                if (rs.wasNull()) {
                    relHeight = Double.NaN;
                }

                BridgePoint bridgePoint = new BridgePoint(
                        coord,
                        rs.getLong("PK"), // primaryKey from database
                        100L, // bridgePrimaryKey
                        absHeight,
                        relHeight,
                        rs.getDouble("DECK_THICKNESS"),
                        rs.getDouble("RIGHT_WIDTH"),
                        rs.getDouble("LEFT_WIDTH"),
                        rs.getDouble("RIGHT_BARRIER_HEIGHT"),
                        rs.getDouble("LEFT_BARRIER_HEIGHT"),
                        Bridge.GirderType.fromString(rs.getString("GIRDER_TYPE")),
                        Bridge.SlabType.fromString(rs.getString("SLAB_TYPE"))
                );
                bridgePoint.setPosition(position);
                bridgePointsList.add(bridgePoint);
            }
        }

        assertEquals(3, bridgePointsList.size(), "Should have loaded 3 CENTER bridge points");
        return bridgePointsList;
    }

    private ProfileBuilder createProfileBuilder() {
        ProfileBuilder profileBuilder =  new ProfileBuilder();
        
        // Add 3x3 grid points in x=[0,100], y=[0,100] with varied z values between 0-3
        profileBuilder.addTopographicPoint(new Coordinate(0, 0, 0.5));
        profileBuilder.addTopographicPoint(new Coordinate(50, 0, 1.2));
        profileBuilder.addTopographicPoint(new Coordinate(100, 0, 2.8));
        profileBuilder.addTopographicPoint(new Coordinate(0, 50, 0.1));
        profileBuilder.addTopographicPoint(new Coordinate(50, 50, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(100, 50, 1.5));
        profileBuilder.addTopographicPoint(new Coordinate(0, 100, 2.1));
        profileBuilder.addTopographicPoint(new Coordinate(50, 100, 0.8));
        profileBuilder.addTopographicPoint(new Coordinate(100, 100, 2.5));
        profileBuilder.finishFeeding();

        return profileBuilder;
    }

    /**
     * Create a simple 3D bridge deck polygon matching testCreateBridgeFromDatabase.
     */
    private Polygon createTestDeckGeometry() {
        Coordinate[] coords = {
            new Coordinate(0, 20, 10.0),
            new Coordinate(50, 20, 10.0),
            new Coordinate(100, 20, 10.0),
            new Coordinate(0, 20, 10.0)
        };
        return geometryFactory.createPolygon(coords);
    }


    /**
     * Tests Bridge construction from BridgePoints and validates various method operations.
     * Verifies bridge creation, equality, properties, and geometric operations.
     */
    @Test
    public void testBridgeConstructionAndValidation() throws Exception  {
        // Create bridge points for testing
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        List<BridgePoint> bridgePointsRel = createTestBridgePointsWithRelativeHeight();
        List<BridgePoint> bridgePoints2 = createTestBridgePoints2();
        List<BridgePoint> bridgePointsDb = createTestBridgePointsFromDatabase(connection);

        // Create bridges using different constructors
        Bridge bridge1 = new Bridge(bridgePoints, defaultAlphas, 1L, Bridge.GirderType.STEEL_BOX, Bridge.SlabType.STEEL);
        Bridge bridgeRel = new Bridge(bridgePointsRel, defaultAlphas, 1L, Bridge.GirderType.STEEL_BOX, Bridge.SlabType.STEEL);
        Bridge bridge2 = new Bridge(bridgePoints2, defaultAlphas, 2L, Bridge.GirderType.CONCRETE_PLATE, Bridge.SlabType.CONCRETE);
        Bridge bridgeDb = new Bridge(bridgePointsDb, defaultAlphas, 1L, Bridge.GirderType.STEEL_BOX, Bridge.SlabType.STEEL);

        // Generate deck geometries for the bridges
        ProfileBuilder profileBuilder = createProfileBuilder();
        bridge1.createDeckGeometry(profileBuilder);
        bridgeRel.createDeckGeometry(profileBuilder);
        bridge2.createDeckGeometry(profileBuilder);
        bridgeDb.createDeckGeometry(profileBuilder);

        // Assert that bridges created from different methods are equal
        assertEquals(bridge1, bridgeDb, "Bridges created from different methods should be equal");
        assertEquals(bridge1.hashCode(), bridgeDb.hashCode(), "Hash codes should be equal for equal bridges");

        // Assert that bridges with different points are not equal
        assertNotEquals(bridge1, bridge2, "Bridges with different points should not be equal");
        assertNotEquals(bridge1.hashCode(), bridge2.hashCode(), "Hash codes should differ for different bridges");

        for (Coordinate deckGeometryCoord : bridge1.getDeckGeometry().getCoordinates()) {
            assertEquals(10.0, deckGeometryCoord.getZ(), 0.001, "Deck geometry Z coordinate should be 10.0");
        }

        for (Coordinate deckGeometryCoord : bridgeRel.getDeckGeometry().getCoordinates()) {
            assertTrue(deckGeometryCoord.getZ() > 10, "Deck geometry Z coordinate should be greater than 10.0");
        }

        // Assert basic bridge properties
        assertEquals(1L, bridge1.getPrimaryKey(), "Primary key should be set");

        // Assert geometry-related properties
        Polygon edge = bridge1.getEdge();
        assertNotNull(edge, "Edge should not be null");

        assertNotNull(bridge1.getDeckGeometry(), "Bridge deck geometry should be generated");

        assertEquals(10.0, bridge1.getAverageAbsoluteDeckHeight(), 0.001, "Average height should be (10+10+10)/3 = 10.0");

        // Assert bridge material properties
        assertEquals(bridge1.getGirderType(), Bridge.GirderType.STEEL_BOX, "Girder type should match bridge points");
        assertEquals(bridge1.getSlabType(), Bridge.SlabType.STEEL, "Slab type should match bridge points");
        assertEquals(bridge2.getGirderType(), Bridge.GirderType.CONCRETE_PLATE, "Girder type should match bridge points");
        assertEquals(bridge2.getSlabType(), Bridge.SlabType.CONCRETE, "Slab type should match bridge points");

        // Test points inside the bridge footprint
        List<Coordinate> pointsInsideOrOnBridge = List.of(
            new Coordinate(25, 17,10),
            new Coordinate(50, 20, 10),
            new Coordinate(75, 24, 11) // slightly above deck, within tolerance
        );

        for (Coordinate pointInsideOrOnBridge : pointsInsideOrOnBridge) {
            assertTrue(bridge1.isPointWithinBridgeFootprint(pointInsideOrOnBridge),
                    "Point should be within bridge footprint");

            assertEquals(10.0, bridge1.getDeckHeightAtPoint(pointInsideOrOnBridge), 0.001, "Height at point should match absolute deck height of bridge points");
            assertFalse(Double.isNaN(bridge1.getDeckHeightAtPoint(pointInsideOrOnBridge)), "Height should be calculable for point inside bridge");

            assertEquals(0.5, bridge1.getDeckThicknessAtPoint(pointInsideOrOnBridge), 0.001, "Deck thickness at point should match expected value");

            assertTrue(bridge1.isPointWithinBridgeFootprint(pointInsideOrOnBridge),
                   "Point should be within bridge footprint");

            assertTrue(bridge1.isPointAboveBridge(pointInsideOrOnBridge), "Point on deck or slightly above deck should be considered above bridge");

            assertFalse(bridge1.isPointBelowBridge(pointInsideOrOnBridge), "Point on deck or slightly above deck should not be considered below bridge");
            
            assertTrue(bridge1.isPointOnBridge(pointInsideOrOnBridge), "Method should complete without exception");
            
            assertEquals(0.0, bridge1.getBarrierHeightAtPoint(pointInsideOrOnBridge), 0.001, "Barrier height should be 0.0 for inside points");
        }

        
        // Test points inside the bridge footprint
        List<Coordinate> pointsInsideButBelowBridge = List.of(
            new Coordinate(25, 17,5),
            new Coordinate(50, 20, 3),
            new Coordinate(75, 24, 1)
        );

        for (Coordinate pointInsideButBelowBridge : pointsInsideButBelowBridge) {
            assertTrue(bridge1.isPointWithinBridgeFootprint(pointInsideButBelowBridge),
                    "Point should be within bridge footprint");

            assertTrue(bridge1.isPointBelowBridge(pointInsideButBelowBridge), "Point below deck should be considered below bridge");

            assertFalse(bridge1.isPointAboveBridge(pointInsideButBelowBridge), "Point below deck should not be considered above bridge");

            assertFalse(bridge1.isPointOnBridge(pointInsideButBelowBridge), "Method should complete without exception");
        }


        // Test points on the bridge boundary
        List<Coordinate> pointsOnSideBoundary = List.of(
            new Coordinate(10, 15, 10),
            new Coordinate(80, 25, 100) // z value is not relevant for boundary check
        );

        for (Coordinate pointOnSideBoundary : pointsOnSideBoundary) {
            assertTrue(bridge1.isPointWithinBridgeFootprint(pointOnSideBoundary),
                    "Point on boundary should be within bridge footprint");

            assertEquals(1.0, bridge1.getBarrierHeightAtPoint(pointOnSideBoundary), 0.001, "Barrier height should match bridge point values");
        }

        // Test points outside the bridge footprint
        List<Coordinate> pointsOutsideBridge = List.of(
            new Coordinate(-10, 20, 10.0),
            new Coordinate(150, 20, 10.0),
            new Coordinate(50, 50, 10.0)
        );

        for (Coordinate pointOutsideBridge : pointsOutsideBridge) {
            assertFalse(bridge1.isPointWithinBridgeFootprint(pointOutsideBridge),
                   "Point should not be within bridge footprint");

            assertTrue(Double.isNaN(bridge1.getDeckHeightAtPoint(pointOutsideBridge)), "Height should be NaN for point outside bridge");

            assertTrue(Double.isNaN(bridge1.getDeckThicknessAtPoint(pointOutsideBridge)), "Deck thickness should be NaN for point outside bridge");

            assertFalse(bridge1.isPointOnBridge(pointOutsideBridge), "Point outside footprint should not be on bridge");
        }
    }

    @Test
    public void testConstructorWithDeckGeometry() {
        Polygon deckGeometry = createTestDeckGeometry();
        Bridge bridge = new Bridge(deckGeometry, defaultAlphas, 2L);
        
        assertNotNull(bridge, "Bridge should be created");
        assertEquals(2L, bridge.getPrimaryKey(), "Primary key should be set");
        assertNotNull(bridge.getDeckGeometry(), "Deck geometry should be set");
        assertFalse(bridge.getEdge().isEmpty(), "Edge should be created");
    }


    @Test
    public void testConstructorWithNullAlphas() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        Bridge bridge = new Bridge(bridgePoints, null, 1L);
        
        assertNotNull(bridge, "Bridge should be created even with null alphas");
    }

    // Static factory method tests

    @Test
    public void testCreateBridgesFromPoints() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        List<BridgePoint> bridgePoints2 = createTestBridgePoints2();
        bridgePoints.addAll(bridgePoints2);
        
        List<Bridge> bridges = Bridge.createBridgesFromPoints(bridgePoints, defaultAlphas);
        
        assertEquals(2, bridges.size(), "Should create two bridges");

        assertEquals(100L, bridges.get(0).getPrimaryKey(), "Bridge should have correct primary key");
        assertEquals(3, bridges.get(0).getBridgePointCount(), "Bridge should have all points");
        
        assertEquals(101L, bridges.get(1).getPrimaryKey(), "Bridge should have correct primary key");
        assertEquals(2, bridges.get(1).getBridgePointCount(), "Bridge should have all points");
    }



    // Bridge point management tests

    @Test
    public void testAddAndRemoveBridgePoint() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        Bridge bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        
        BridgePoint newPoint = new BridgePoint(new Coordinate(120, 25), 5, 100, 15.5, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bridge.addBridgePoint(newPoint);
        
        assertEquals(4, bridge.getBridgePointCount(), "Should have 4 bridge points");
        assertTrue(bridge.getBridgePoints().contains(newPoint), "New point should be in collection");
        
        boolean removed = bridge.removeBridgePoint(1L);
        assertTrue(removed, "Should remove existing point");
        assertEquals(3, bridge.getBridgePointCount(), "Should have 3 bridge points after removal");
        
        boolean removedAgain = bridge.removeBridgePoint(999L);
        assertFalse(removedAgain, "Should not remove non-existing point");
    }


    // Geometry tests

    @Test
    public void testGetEnvelope2D() {
        Polygon deckGeometry = createTestDeckGeometry();
        Bridge bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        Envelope envelope = bridge.getEnvelope2D();
        assertNotNull(envelope, "Envelope should not be null");
    }

}
