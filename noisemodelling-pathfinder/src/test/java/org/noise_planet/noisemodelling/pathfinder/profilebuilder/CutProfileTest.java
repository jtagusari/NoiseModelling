/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CutProfile class.
 * Tests the functionality of vertical profile cuts between sound sources and receivers,
 * including cut point management, ground absorption calculations, and coordinate transformations.
 */
public class CutProfileTest {

    private CutProfile cutProfile;
    private CutPointSource source;
    private CutPointReceiver receiver;

    @BeforeEach
    public void setUp() {
        // Create test source and receiver points
        source = new CutPointSource(new Coordinate(0, 0, 5));
        source.setGroundCoefficient(0.5);
        
        receiver = new CutPointReceiver(new Coordinate(100, 0, 3));
        receiver.setGroundCoefficient(0.3);
        
        cutProfile = new CutProfile(source, receiver);
    }

    @Test
    public void testEmptyConstructor() {
        CutProfile emptyProfile = new CutProfile();
        assertTrue(emptyProfile.getCutPoints().isEmpty());
        assertFalse(emptyProfile.hasBuildingIntersection());
        assertFalse(emptyProfile.hasBridgeIntersection());
        assertFalse(emptyProfile.hasTopographyIntersection());
    }

    @Test
    public void testConstructorWithSourceAndReceiver() {
        assertNotNull(cutProfile);
        assertEquals(2, cutProfile.getCutPoints().size());
        
        CutPointSource retrievedSource = cutProfile.getSource();
        CutPointReceiver retrievedReceiver = cutProfile.getReceiver();
        
        assertNotNull(retrievedSource);
        assertNotNull(retrievedReceiver);
        assertEquals(0, retrievedSource.getCoordinate().x, 1e-9);
        assertEquals(100, retrievedReceiver.getCoordinate().x, 1e-9);
    }

    @Test
    public void testInsertCutPointWithoutSorting() {
        CutPointTopography topo1 = new CutPointTopography(new Coordinate(25, 0, 10));
        CutPointTopography topo2 = new CutPointTopography(new Coordinate(75, 0, 8));
        
        cutProfile.insertCutPoint(false, topo1, topo2);
        
        assertEquals(4, cutProfile.getCutPoints().size());
        // Without sorting, points should be inserted in order after source
        assertEquals(topo1, cutProfile.getCutPoints().get(1));
        assertEquals(topo2, cutProfile.getCutPoints().get(2));
    }

    @Test
    public void testInsertCutPointWithSorting() {
        CutPointTopography topo1 = new CutPointTopography(new Coordinate(75, 0, 8));
        CutPointTopography topo2 = new CutPointTopography(new Coordinate(25, 0, 10));
        
        // Test without sorting first
        CutProfile testProfile = new CutProfile(source, receiver);
        testProfile.insertCutPoint(false, topo1, topo2);
        
        assertEquals(4, testProfile.getCutPoints().size());
        // With sorting disabled, points should be inserted in order after source
        assertEquals(topo1, testProfile.getCutPoints().get(1));
        assertEquals(topo2, testProfile.getCutPoints().get(2));
        
        // Now test with sorting enabled - this should work with equals() method implemented
        CutProfile sortedProfile = new CutProfile(source, receiver);
        
        try {
            // This should now work properly with equals() method
            sortedProfile.insertCutPoint(true, topo1, topo2);
            
            List<CutPoint> points = sortedProfile.getCutPoints();
            assertEquals(4, points.size());
            
            // Verify source is still first
            assertTrue(points.get(0) instanceof CutPointSource);
            // Verify receiver is still last
            assertTrue(points.get(points.size() - 1) instanceof CutPointReceiver);
            
            // Verify points are sorted by distance from source
            for (int i = 0; i < points.size() - 1; i++) {
                double dist1 = points.get(i).getCoordinate().distance(source.getCoordinate());
                double dist2 = points.get(i + 1).getCoordinate().distance(source.getCoordinate());
                assertTrue(dist1 <= dist2, "Points should be sorted by distance from source");
            }
            
            // After sorting, topo2 (x=25) should come before topo1 (x=75)
            // Order should be: source (x=0), topo2 (x=25), topo1 (x=75), receiver (x=100)
            assertEquals(25, points.get(1).getCoordinate().x, 1e-9);
            assertEquals(75, points.get(2).getCoordinate().x, 1e-9);
            
        } catch (IndexOutOfBoundsException e) {
            fail("insertCutPoint with sorting should work now with equals() method implemented: " + e.getMessage());
        }
    }

    @Test
    public void testSort() {
        CutPointTopography topo1 = new CutPointTopography(new Coordinate(75, 0, 8));
        CutPointTopography topo2 = new CutPointTopography(new Coordinate(25, 0, 10));
        
        cutProfile.insertCutPoint(false, topo1, topo2);
        cutProfile.sort(new Coordinate(0, 0, 0));
        
        // After sorting, points should be ordered by distance from origin
        List<CutPoint> points = cutProfile.getCutPoints();
        assertTrue(points.get(0).getCoordinate().x <= points.get(1).getCoordinate().x);
        assertTrue(points.get(1).getCoordinate().x <= points.get(2).getCoordinate().x);
        assertTrue(points.get(2).getCoordinate().x <= points.get(3).getCoordinate().x);
    }

    @Test
    public void testGetGPathSimple() {
        double gPath = cutProfile.calculateWeightedGroundAbsorption();
        
        // Should calculate weighted average of ground coefficients
        // Expected: (0.5 * segmentLength) / totalLength
        // Since we have only one segment from source to receiver
        assertTrue(gPath >= 0.0 && gPath <= 1.0);
    }

    @Test
    public void testGetGPathBetweenPoints() {
        CutPointTopography midPoint = new CutPointTopography(new Coordinate(50, 0, 4));
        midPoint.setGroundCoefficient(0.8);
        
        // Create a new profile to avoid the sorting issue
        CutProfile testProfile = new CutProfile(source, receiver);
        testProfile.insertCutPoint(false, midPoint);
        
        List<CutPoint> points = testProfile.getCutPoints();
        CutPoint src = points.get(0);
        CutPoint rcv = points.get(points.size() - 1);
        
        double gPath = testProfile.calculateWeightedGroundAbsorption(src, rcv, Scene.DEFAULT_G_BUILDING);
        assertTrue(gPath >= 0.0 && gPath <= 1.0);
    }

    @Test
    public void testIsFreeField() {
        assertTrue(cutProfile.isFreeField());
        
        cutProfile.hasBuildingIntersection(true);
        assertFalse(cutProfile.isFreeField());
        
        cutProfile.hasBuildingIntersection(false);
        cutProfile.hasBridgeIntersection(true);
        assertFalse(cutProfile.isFreeField());
        
        cutProfile.hasBridgeIntersection(false);
        cutProfile.hasTopographyIntersection(true);
        assertFalse(cutProfile.isFreeField());
    }

    @Test
    public void testIntersectionFlags() {
        // Test building intersection
        assertFalse(cutProfile.hasBuildingIntersection());
        cutProfile.hasBuildingIntersection(true);
        assertTrue(cutProfile.hasBuildingIntersection());
        
        // Test bridge intersection
        assertFalse(cutProfile.hasBridgeIntersection());
        cutProfile.hasBridgeIntersection(true);
        assertTrue(cutProfile.hasBridgeIntersection());
        
        // Test topography intersection
        assertFalse(cutProfile.hasTopographyIntersection());
        cutProfile.hasTopographyIntersection(true);
        assertTrue(cutProfile.hasTopographyIntersection());
    }

    @Test
    public void testComputePts2D() {
        List<Coordinate> pts2D = cutProfile.generateCutPointCoordinates2D();
        
        assertNotNull(pts2D);
        assertEquals(2, pts2D.size());
        // First point should be at x=0 after coordinate system transformation
        assertEquals(0.0, pts2D.get(0).x, 1e-9);
    }

    @Test
    public void testComputePts2DGround() {
        List<Coordinate> groundPts = cutProfile.generateElevationProfile2D();
        
        assertNotNull(groundPts);
        assertEquals(2, groundPts.size());
        // Should use ground elevation (zGround) instead of actual z coordinate
    }

    @Test
    public void testComputePts2DGroundWithIndex() {
        List<Integer> indices = new ArrayList<>();
        List<Coordinate> groundPts = cutProfile.generateElevationProfile2D(indices);
        
        assertNotNull(groundPts);
        assertNotNull(indices);
        assertEquals(groundPts.size(), indices.size());
    }

    @Test
    public void testComputePts2DGroundWithTolerance() {
        // Create a new profile to avoid sorting issues
        CutProfile testProfile = new CutProfile(source, receiver);
        
        // Add some intermediate points
        CutPointTopography topo1 = new CutPointTopography(new Coordinate(33, 0, 4));
        CutPointTopography topo2 = new CutPointTopography(new Coordinate(66, 0, 4));
        
        testProfile.insertCutPoint(false, topo1, topo2);
        
        List<Integer> indices = new ArrayList<>();
        List<Coordinate> groundPts = testProfile.generateElevationProfile2D(0.1, indices);
        
        assertNotNull(groundPts);
        // With tolerance, collinear points might be simplified
        assertTrue(groundPts.size() >= 2);
    }

    @Test
    public void testSetSource() {
        CutPointSource newSource = new CutPointSource(new Coordinate(10, 5, 8));
        cutProfile.setSource(newSource);
        
        CutPointSource retrievedSource = cutProfile.getSource();
        assertNotNull(retrievedSource);
        assertEquals(10, retrievedSource.getCoordinate().x, 1e-9);
        assertEquals(5, retrievedSource.getCoordinate().y, 1e-9);
        assertEquals(8, retrievedSource.getCoordinate().z, 1e-9);
    }

    @Test
    public void testSetReceiver() {
        CutPointReceiver newReceiver = new CutPointReceiver(new Coordinate(200, 10, 12));
        cutProfile.setReceiver(newReceiver);
        
        CutPointReceiver retrievedReceiver = cutProfile.getReceiver();
        assertNotNull(retrievedReceiver);
        assertEquals(200, retrievedReceiver.getCoordinate().x, 1e-9);
        assertEquals(10, retrievedReceiver.getCoordinate().y, 1e-9);
        assertEquals(12, retrievedReceiver.getCoordinate().z, 1e-9);
    }

    @Test
    public void testSetSourceNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            cutProfile.setSource(null);
        });
    }

    @Test
    public void testSetReceiverNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            cutProfile.setReceiver(null);
        });
    }

    @Test
    public void testToString() {
        String str = cutProfile.toString();
        assertNotNull(str);
        assertTrue(str.contains("CutProfile"));
        assertTrue(str.contains("hasBuildingIntersection"));
        assertTrue(str.contains("hasBridgeIntersection"));
        assertTrue(str.contains("hasTopographyIntersection"));
    }

    @Test
    public void testGetCutPointsReturnsDefensiveCopy() {
        List<CutPoint> points1 = cutProfile.getCutPoints();
        List<CutPoint> points2 = cutProfile.getCutPoints();
        
        // Should return different list instances (defensive copies)
        assertNotSame(points1, points2);
        assertEquals(points1.size(), points2.size());
    }


    @Test
    public void testEmptyProfileOperations() {
        CutProfile emptyProfile = new CutProfile();
        
        assertEquals(0, emptyProfile.calculateWeightedGroundAbsorption(), 1e-9);
        try {
            assertNull(emptyProfile.getSource());
            assertNull(emptyProfile.getReceiver());
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
        assertTrue(emptyProfile.isFreeField());
        
        List<Coordinate> pts2D = emptyProfile.generateCutPointCoordinates2D();
        assertTrue(pts2D.isEmpty());
        
        List<Coordinate> groundPts = emptyProfile.generateElevationProfile2D();
        assertTrue(groundPts.isEmpty());
    }

    @Test
    public void testInsertCutPointSortingWithEqualsMethod() {
        // Test that the equals() method fixes the insertCutPoint sorting issue
        CutPointTopography topo1 = new CutPointTopography(new Coordinate(75, 0, 8));
        CutPointTopography topo2 = new CutPointTopography(new Coordinate(25, 0, 10));
        
        CutProfile profile = new CutProfile(source, receiver);
        
        // This should now work without IndexOutOfBoundsException
        assertDoesNotThrow(() -> {
            profile.insertCutPoint(true, topo1, topo2);
        });
        
        List<CutPoint> points = profile.getCutPoints();
        assertEquals(4, points.size());
        
        // Verify source remains first and receiver remains last
        assertTrue(points.get(0) instanceof CutPointSource);
        assertTrue(points.get(points.size() - 1) instanceof CutPointReceiver);
        
        // Verify sorting by distance from source
        assertEquals(0, points.get(0).getCoordinate().x, 1e-9);   // source
        assertEquals(25, points.get(1).getCoordinate().x, 1e-9);  // topo2 (closer)
        assertEquals(75, points.get(2).getCoordinate().x, 1e-9);  // topo1 (farther)
        assertEquals(100, points.get(3).getCoordinate().x, 1e-9); // receiver
    }

    @Test
    public void testCutPointEqualsMethod() {
        // Test the newly implemented equals method
        CutPointTopography point1 = new CutPointTopography(new Coordinate(25, 0, 10));
        point1.setZGround(5.0);
        point1.setGroundCoefficient(0.5);
        
        CutPointTopography point2 = new CutPointTopography(new Coordinate(25, 0, 10));
        point2.setZGround(5.0);
        point2.setGroundCoefficient(0.5);
        
        CutPointTopography point3 = new CutPointTopography(new Coordinate(30, 0, 10));
        point3.setZGround(5.0);
        point3.setGroundCoefficient(0.5);
        
        // Test equality
        assertEquals(point1, point2);
        assertEquals(point1.hashCode(), point2.hashCode());
        
        // Test inequality
        assertNotEquals(point1, point3);
        
        // Test with copied objects (like getSource() and getReceiver() return)
        CutPointSource originalSource = new CutPointSource(new Coordinate(0, 0, 5));
        originalSource.setGroundCoefficient(0.5);
        
        CutPointSource copiedSource = new CutPointSource(originalSource);
        
        assertEquals(originalSource, copiedSource);
        assertEquals(originalSource.hashCode(), copiedSource.hashCode());
    }
    

    private static CutProfile loadCutProfile(String utName) throws IOException {
        String testCaseFileName = utName + ".json";
        try(InputStream inputStream = PathFinder.class.getResourceAsStream("test_cases/"+testCaseFileName)) {
            Objects.requireNonNull(inputStream, "Missing test resource: test_cases/" + testCaseFileName);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(inputStream, CutProfile.class);
        }
    }

    @Test
    public void TBCCoordinates2D() throws Exception {
        CutProfile cutProfileCase = loadCutProfile("TBC06");
        List<Coordinate> cutPointCoordinates2D = cutProfileCase.generateCutPointCoordinates2D();
        assertNotNull(cutPointCoordinates2D);
    }
}
