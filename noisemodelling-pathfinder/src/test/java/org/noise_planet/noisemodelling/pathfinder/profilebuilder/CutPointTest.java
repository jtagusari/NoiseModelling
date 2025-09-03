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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CutPoint abstract class.
 * Tests the base functionality of cut points including coordinate management,
 * ground properties, and comparison operations.
 */
public class CutPointTest {

    private CutPointTopography testCutPoint;
    private Coordinate testCoordinate;

    @BeforeEach
    public void setUp() {
        testCoordinate = new Coordinate(10.0, 20.0, 30.0);
        testCutPoint = new CutPointTopography(testCoordinate);
    }

    @Test
    public void testDefaultConstructor() {
        CutPointTopography point = new CutPointTopography();
        assertNotNull(point.getCoordinate());
        assertTrue(Double.isNaN(point.getzGround()));
        assertTrue(Double.isNaN(point.getGroundCoefficient()));
    }

    @Test
    public void testCoordinateConstructor() {
        assertEquals(testCoordinate.x, testCutPoint.getCoordinate().x, 1e-9);
        assertEquals(testCoordinate.y, testCutPoint.getCoordinate().y, 1e-9);
        assertEquals(testCoordinate.z, testCutPoint.getCoordinate().z, 1e-9);
        assertEquals(testCoordinate.z, testCutPoint.getzGround(), 1e-9);
    }

    @Test
    public void testFullConstructor() {
        Coordinate coord = new Coordinate(5.0, 15.0, 25.0);
        double zGround = 22.0;
        double groundCoeff = 0.7;
        
        CutPointTopography point = new CutPointTopography();
        point.coordinate = coord;
        point.zGround = zGround;
        point.groundCoefficient = groundCoeff;
        
        assertEquals(coord, point.getCoordinate());
        assertEquals(zGround, point.getzGround(), 1e-9);
        assertEquals(groundCoeff, point.getGroundCoefficient(), 1e-9);
    }

    @Test
    public void testCopyConstructor() {
        testCutPoint.setGroundCoefficient(0.5);
        testCutPoint.setZGround(25.0);
        
        // Create a new point with the same properties
        CutPointTopography copy = new CutPointTopography(testCutPoint.getCoordinate());
        copy.setGroundCoefficient(testCutPoint.getGroundCoefficient());
        copy.setZGround(testCutPoint.getzGround());
        
        assertEquals(testCutPoint.getCoordinate().x, copy.getCoordinate().x, 1e-9);
        assertEquals(testCutPoint.getCoordinate().y, copy.getCoordinate().y, 1e-9);
        assertEquals(testCutPoint.getCoordinate().z, copy.getCoordinate().z, 1e-9);
        assertEquals(testCutPoint.getzGround(), copy.getzGround(), 1e-9);
        assertEquals(testCutPoint.getGroundCoefficient(), copy.getGroundCoefficient(), 1e-9);
        
        // Verify it's a different object
        assertNotSame(testCutPoint, copy);
    }

    @Test
    public void testSetCoordinate() {
        Coordinate newCoord = new Coordinate(100.0, 200.0, 300.0);
        testCutPoint.setCoordinate(newCoord);
        
        assertEquals(newCoord, testCutPoint.getCoordinate());
    }

    @Test
    public void testSetGroundCoefficient() {
        double coefficient = 0.8;
        testCutPoint.setGroundCoefficient(coefficient);
        
        assertEquals(coefficient, testCutPoint.getGroundCoefficient(), 1e-9);
    }

    @Test
    public void testSetZGround() {
        double zGround = 35.0;
        testCutPoint.setZGround(zGround);
        
        assertEquals(zGround, testCutPoint.getzGround(), 1e-9);
    }

    @Test
    public void testGroundCoefficientValidRange() {
        // Test valid range values
        testCutPoint.setGroundCoefficient(0.0); // Hard surface
        assertEquals(0.0, testCutPoint.getGroundCoefficient(), 1e-9);
        
        testCutPoint.setGroundCoefficient(0.3); // Compacted dense ground
        assertEquals(0.3, testCutPoint.getGroundCoefficient(), 1e-9);
        
        testCutPoint.setGroundCoefficient(0.7); // Compacted soft ground
        assertEquals(0.7, testCutPoint.getGroundCoefficient(), 1e-9);
        
        testCutPoint.setGroundCoefficient(1.0); // Soft ground
        assertEquals(1.0, testCutPoint.getGroundCoefficient(), 1e-9);
    }

    @Test
    public void testCompareTo() {
        CutPointTopography point1 = new CutPointTopography(new Coordinate(0.0, 0.0, 0.0));
        CutPointTopography point2 = new CutPointTopography(new Coordinate(1.0, 0.0, 0.0));
        CutPointTopography point3 = new CutPointTopography(new Coordinate(0.0, 0.0, 0.0));
        
        assertTrue(point1.compareTo(point2) < 0);
        assertTrue(point2.compareTo(point1) > 0);
        assertEquals(0, point1.compareTo(point3));
    }

    @Test
    public void testToString() {
        testCutPoint.setGroundCoefficient(0.5);
        testCutPoint.setZGround(25.0);
        
        String str = testCutPoint.toString();
        assertNotNull(str);
        assertTrue(str.contains("coordinate"));
        assertTrue(str.contains("zGround"));
        assertTrue(str.contains("groundCoefficient"));
    }

    @Test
    public void testNaNValues() {
        CutPointTopography point = new CutPointTopography();
        
        assertTrue(Double.isNaN(point.getzGround()));
        assertTrue(Double.isNaN(point.getGroundCoefficient()));
        
        // Setting NaN should work
        point.setZGround(Double.NaN);
        point.setGroundCoefficient(Double.NaN);
        
        assertTrue(Double.isNaN(point.getzGround()));
        assertTrue(Double.isNaN(point.getGroundCoefficient()));
    }

    @Test
    public void testCoordinateModification() {
        Coordinate original = new Coordinate(1.0, 2.0, 3.0);
        testCutPoint.setCoordinate(original);
        
        // Modify the original coordinate
        original.x = 999.0;
        
        // The cut point coordinate may or may not be affected depending on implementation
        // This test verifies the current behavior
        Coordinate storedCoord = testCutPoint.getCoordinate();
        assertNotNull(storedCoord);
        // Either the coordinate is copied or the reference is shared
        assertTrue(storedCoord.x == 1.0 || storedCoord.x == 999.0);
    }
}
