package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ProfileRetriever class.
 * Tests the functionality of profile building between sound sources and receivers,
 * including topography processing, obstacle detection, and ground coefficient propagation.
 */
public class ProfileRetrieverTest {

    private GeometryFactory geometryFactory;
    private BuildingService buildingService;
    private WallService wallService;
    private BridgeService bridgeService;
    private TopographyService topographyService;
    private GroundService groundService;
    private ProcessedWallService processedWallService;

    @BeforeEach
    public void setUp() {
        geometryFactory = new GeometryFactory();
        buildingService = new BuildingService(4);
        wallService = new WallService(4);
        bridgeService = new BridgeService();
        topographyService = new TopographyService(4);
        groundService = new GroundService(4);
        processedWallService = new ProcessedWallService(4);
    }

    @Test
    public void testGetProfile_basicGroundPropagationAndObstacles() {
        // No ground effects -> default coefficient should be propagated
        Coordinate src = new Coordinate(0,0,0);
        Coordinate rcv = new Coordinate(10,0,0);
        double defaultG = 0.33;

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, false, 100.0,
                buildingService, wallService, bridgeService, topographyService, groundService, processedWallService, geometryFactory);

        assertNotNull(profile);
        // source and receiver should have groundCoefficient set
        assertEquals(defaultG, profile.getSource().getGroundCoefficient());
        assertEquals(defaultG, profile.getReceiver().getGroundCoefficient());
        // with no topo and no obstacles, only source and receiver cut points
        assertTrue(profile.getCutPoints().size() >= 2);
    }

    @Test
    public void testGetProfile_withDefaultGroundCoefficient() {
        Coordinate src = new Coordinate(0, 0, 5);
        Coordinate rcv = new Coordinate(100, 0, 3);
        double defaultG = 0.5;

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, false, 1000.0,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        assertEquals(2, profile.getCutPoints().size());
        
        // Verify default ground coefficient is applied
        assertEquals(defaultG, profile.getSource().getGroundCoefficient(), 1e-9);
        assertEquals(defaultG, profile.getReceiver().getGroundCoefficient(), 1e-9);
    }

    @Test
    public void testGetProfile_noTopographyService() {
        Coordinate src = new Coordinate(0, 0, 5);
        Coordinate rcv = new Coordinate(50, 0, 3);
        double defaultG = 0.3;

        // Create services without topography
        TopographyService emptyTopoService = new TopographyService(4);
        // Don't call processDelaunay() to leave topo tree null

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, false, 1000.0,
                buildingService, wallService, bridgeService, emptyTopoService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        
        // Should have default ground elevations set to 0.0
        assertEquals(0.0, profile.getSource().getzGround(), 1e-9);
        assertEquals(0.0, profile.getReceiver().getzGround(), 1e-9);
    }

    @Test
    public void testGetProfile_stopAtTopographyIntersection() {
        Coordinate src = new Coordinate(0, 0, 5);
        Coordinate rcv = new Coordinate(100, 0, 3);
        double defaultG = 0.4;

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, true, 1000.0,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        // Even with stopAtObstacle=true, should return a valid profile
        assertTrue(profile.getCutPoints().size() >= 2);
    }

    @Test
    public void testGetProfile_stopAtBuildingIntersection() {
        Coordinate src = new Coordinate(0, 0, 5);
        Coordinate rcv = new Coordinate(100, 0, 3);
        double defaultG = 0.6;

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, true, 1000.0,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        // Should work even with early stopping enabled
        assertTrue(profile.getCutPoints().size() >= 2);
    }

    @Test
    public void testGetProfile_withShortMaxLineLength() {
        Coordinate src = new Coordinate(0, 0, 2);
        Coordinate rcv = new Coordinate(100, 0, 2);
        double defaultG = 0.2;
        double shortMaxLength = 5.0; // Force segment splitting

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, false, shortMaxLength,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        assertTrue(profile.getCutPoints().size() >= 2);
        
        // Verify ground coefficients are propagated correctly
        for (CutPoint point : profile.getCutPoints()) {
            assertFalse(Double.isNaN(point.getGroundCoefficient()));
        }
    }

    @Test
    public void testGetProfile_groundCoefficientPropagation() {
        Coordinate src = new Coordinate(0, 0, 4);
        Coordinate rcv = new Coordinate(200, 0, 4);
        double defaultG = 0.7;

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, false, 1000.0,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        
        // All points should have ground coefficients (no NaN values)
        for (CutPoint point : profile.getCutPoints()) {
            assertFalse(Double.isNaN(point.getGroundCoefficient()), 
                "All cut points should have ground coefficients after propagation");
            assertTrue(point.getGroundCoefficient() >= 0.0 && point.getGroundCoefficient() <= 1.0,
                "Ground coefficient should be in valid range [0.0, 1.0]");
        }
    }

    @Test
    public void testGetProfile_elevationInterpolation() {
        Coordinate src = new Coordinate(0, 0, 5);
        Coordinate rcv = new Coordinate(300, 0, 8);
        double defaultG = 0.25;

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, false, 1000.0,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        
        // Check that intermediate points have interpolated elevations
        if (profile.getCutPoints().size() > 2) {
            for (int i = 1; i < profile.getCutPoints().size() - 1; i++) {
                CutPoint point = profile.getCutPoints().get(i);
                assertFalse(Double.isNaN(point.getzGround()), 
                    "Intermediate points should have interpolated ground elevations");
            }
        }
    }

    @Test
    public void testGetProfile_coordinateValidation() {
        Coordinate src = new Coordinate(10, 20, 6);
        Coordinate rcv = new Coordinate(90, 80, 4);
        double defaultG = 0.15;

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, false, 1000.0,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        
        // Verify source and receiver coordinates match input
        assertEquals(src.x, profile.getSource().getCoordinate().x, 1e-9);
        assertEquals(src.y, profile.getSource().getCoordinate().y, 1e-9);
        assertEquals(src.z, profile.getSource().getCoordinate().z, 1e-9);
        
        assertEquals(rcv.x, profile.getReceiver().getCoordinate().x, 1e-9);
        assertEquals(rcv.y, profile.getReceiver().getCoordinate().y, 1e-9);
        assertEquals(rcv.z, profile.getReceiver().getCoordinate().z, 1e-9);
    }

    @Test
    public void testGetProfile_extremeCoordinates() {
        // Test with very close coordinates
        Coordinate src = new Coordinate(0, 0, 5);
        Coordinate rcv = new Coordinate(0.1, 0, 5);
        double defaultG = 0.8;

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, false, 1000.0,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        assertEquals(2, profile.getCutPoints().size());
        assertTrue(profile.isFreeField());
    }

    @Test
    public void testGetProfile_largeDistance() {
        // Test with large distance between source and receiver
        Coordinate src = new Coordinate(0, 0, 10);
        Coordinate rcv = new Coordinate(10000, 0, 10);
        double defaultG = 0.4;

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, false, 100.0,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        assertTrue(profile.getCutPoints().size() >= 2);
        
        // Should still maintain correct ground coefficients
        assertEquals(defaultG, profile.getSource().getGroundCoefficient(), 1e-9);
        assertEquals(defaultG, profile.getReceiver().getGroundCoefficient(), 1e-9);
    }

    @Test
    public void testGetProfile_differentHeights() {
        Coordinate src = new Coordinate(0, 0, 2);
        Coordinate rcv = new Coordinate(50, 0, 15);
        double defaultG = 0.9;

        CutProfile profile = ProfileRetriever.getProfile(src, rcv, defaultG, false, 1000.0,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        
        // Verify height difference is preserved
        assertEquals(2, profile.getSource().getCoordinate().z, 1e-9);
        assertEquals(15, profile.getReceiver().getCoordinate().z, 1e-9);
    }

    @Test
    public void testGetProfile_invalidGroundCoefficient() {
        Coordinate src = new Coordinate(0, 0, 5);
        Coordinate rcv = new Coordinate(100, 0, 5);
        double invalidG = -0.5; // Invalid coefficient

        // Should still work - the method doesn't validate input ranges
        CutProfile profile = ProfileRetriever.getProfile(src, rcv, invalidG, false, 1000.0,
                buildingService, wallService, bridgeService, topographyService, groundService, 
                processedWallService, geometryFactory);

        assertNotNull(profile);
        assertEquals(invalidG, profile.getSource().getGroundCoefficient(), 1e-9);
    }

    @Test
    public void testGetProfile_nullGeometryFactory() {
        Coordinate src = new Coordinate(0, 0, 5);
        Coordinate rcv = new Coordinate(100, 0, 5);
        double defaultG = 0.5;

        // This should throw an exception due to null geometry factory
        assertThrows(NullPointerException.class, () -> {
            ProfileRetriever.getProfile(src, rcv, defaultG, false, 1000.0,
                    buildingService, wallService, bridgeService, topographyService, groundService, 
                    processedWallService, null);
        });
    }
}
