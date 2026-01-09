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
import org.locationtech.jts.geom.Coordinate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BridgePoint class.
 * Tests data container functionality, validation methods, and copy operations.
 */
public class BridgePointTest {

    @Test
    public void testDefaultConstructor() {
        BridgePoint point = new BridgePoint();
        
        assertNull(point.getCoordinate(), "Default coordinate should be null");
        assertEquals(-1, point.getPrimaryKey(), "Default primary key should be -1");
        assertEquals(-1, point.getBridgePrimaryKey(), "Default bridge primary key should be -1");
        assertEquals(BridgePoint.Position.CENTER, point.getPosition(), "Default position should be CENTER");
        assertTrue(Double.isNaN(point.getAbsoluteDeckHeight()), "Default absolute height should be NaN");
        assertTrue(Double.isNaN(point.getRelativeDeckHeight()), "Default relative height should be NaN");
        assertTrue(Double.isNaN(point.getDeckThickness()), "Default thickness should be NaN");
        assertTrue(Double.isNaN(point.getRightWidth()), "Default right width should be NaN");
        assertTrue(Double.isNaN(point.getLeftWidth()), "Default left width should be NaN");
        assertTrue(Double.isNaN(point.getRightBarrierHeight()), "Default right barrier height should be NaN");
        assertTrue(Double.isNaN(point.getLeftBarrierHeight()), "Default left barrier height should be NaN");
    }

    @Test
    public void testConstructorWithCoordinate() {
        Coordinate coord = new Coordinate(100.0, 200.0, 15.0);
        BridgePoint point = new BridgePoint(coord);
        
        assertEquals(coord, point.getCoordinate(), "Coordinate should be set");
        assertEquals(-1, point.getPrimaryKey(), "Primary key should use default");
        assertEquals(BridgePoint.Position.CENTER, point.getPosition(), "Position should use default");
    }

    @Test
    public void testFullConstructor() {
        Coordinate coord = new Coordinate(100.0, 200.0, 15.0);
        BridgePoint point = new BridgePoint(coord, 1L, 100L, 10.0, 2.0, 0.5, 5.0, 6.0, 2.0, 3.0);
        
        assertEquals(coord, point.getCoordinate(), "Coordinate should be set");
        assertEquals(1L, point.getPrimaryKey(), "Primary key should be set");
        assertEquals(100L, point.getBridgePrimaryKey(), "Bridge primary key should be set");
        assertEquals(10.0, point.getAbsoluteDeckHeight(), 0.001, "Absolute deck height should be set");
        assertEquals(2.0, point.getRelativeDeckHeight(), 0.001, "Relative deck height should be set");
        assertEquals(0.5, point.getDeckThickness(), 0.001, "Deck thickness should be set");
        assertEquals(5.0, point.getRightWidth(), 0.001, "Right width should be set");
        assertEquals(6.0, point.getLeftWidth(), 0.001, "Left width should be set");
        assertEquals(2.0, point.getRightBarrierHeight(), 0.001, "Right barrier height should be set");
        assertEquals(3.0, point.getLeftBarrierHeight(), 0.001, "Left barrier height should be set");
    }

    @Test
    public void testCopyConstructor() {
        Coordinate coord = new Coordinate(100.0, 200.0, 15.0);
        BridgePoint original = new BridgePoint(coord, 1L, 100L, 10.0, 2.0, 0.5, 5.0, 6.0, 2.0, 3.0);
        original.setPosition(BridgePoint.Position.LEFT);
        
        BridgePoint copy = new BridgePoint(original);
        
        assertNotSame(original, copy, "Copy should be a different object");
        assertNotSame(original.getCoordinate(), copy.getCoordinate(), "Coordinate should be deep copied");
        assertEquals(original.getCoordinate().x, copy.getCoordinate().x, 0.001, "X coordinate should match");
        assertEquals(original.getCoordinate().y, copy.getCoordinate().y, 0.001, "Y coordinate should match");
        assertEquals(original.getCoordinate().z, copy.getCoordinate().z, 0.001, "Z coordinate should match");
        assertEquals(original.getPrimaryKey(), copy.getPrimaryKey(), "Primary key should match");
        assertEquals(original.getBridgePrimaryKey(), copy.getBridgePrimaryKey(), "Bridge primary key should match");
        assertEquals(original.getPosition(), copy.getPosition(), "Position should match");
        assertEquals(original.getAbsoluteDeckHeight(), copy.getAbsoluteDeckHeight(), 0.001, "Absolute deck height should match");
        assertEquals(original.getRelativeDeckHeight(), copy.getRelativeDeckHeight(), 0.001, "Relative deck height should match");
        assertEquals(original.getDeckThickness(), copy.getDeckThickness(), 0.001, "Deck thickness should match");
        assertEquals(original.getRightWidth(), copy.getRightWidth(), 0.001, "Right width should match");
        assertEquals(original.getLeftWidth(), copy.getLeftWidth(), 0.001, "Left width should match");
        assertEquals(original.getRightBarrierHeight(), copy.getRightBarrierHeight(), 0.001, "Right barrier height should match");
        assertEquals(original.getLeftBarrierHeight(), copy.getLeftBarrierHeight(), 0.001, "Left barrier height should match");
    }

    @Test
    public void testCopyConstructorWithNull() {
        assertThrows(RuntimeException.class, () -> {
            BridgePoint nullPoint = null;
            new BridgePoint(nullPoint);
        }, "Copy constructor should handle null gracefully");
    }

    @Test
    public void testCopyConstructorWithNullCoordinate() {
        BridgePoint original = new BridgePoint();
        original.setPrimaryKey(1L);
        // coordinate is null
        
        BridgePoint copy = new BridgePoint(original);
        
        assertNull(copy.getCoordinate(), "Null coordinate should remain null");
        assertEquals(1L, copy.getPrimaryKey(), "Other fields should be copied");
    }

    @Test
    public void testSettersAndGetters() {
        BridgePoint point = new BridgePoint();
        
        Coordinate coord = new Coordinate(50.0, 60.0, 20.0);
        point.setCoordinate(coord);
        assertEquals(coord, point.getCoordinate(), "Coordinate setter/getter");
        
        point.setPrimaryKey(5L);
        assertEquals(5L, point.getPrimaryKey(), "Primary key setter/getter");
        
        point.setBridgePrimaryKey(200L);
        assertEquals(200L, point.getBridgePrimaryKey(), "Bridge primary key setter/getter");
        
        point.setPosition(BridgePoint.Position.RIGHT);
        assertEquals(BridgePoint.Position.RIGHT, point.getPosition(), "Position setter/getter");
        
        point.setAbsoluteDeckHeight(25.0);
        assertEquals(25.0, point.getAbsoluteDeckHeight(), 0.001, "Absolute deck height setter/getter");
        
        point.setRelativeDeckHeight(8.0);
        assertEquals(8.0, point.getRelativeDeckHeight(), 0.001, "Relative deck height setter/getter");
        
        point.setDeckThickness(1.2);
        assertEquals(1.2, point.getDeckThickness(), 0.001, "Deck thickness setter/getter");
        
        point.setRightWidth(7.5);
        assertEquals(7.5, point.getRightWidth(), 0.001, "Right width setter/getter");
        
        point.setLeftWidth(6.5);
        assertEquals(6.5, point.getLeftWidth(), 0.001, "Left width setter/getter");
        
        point.setRightBarrierHeight(2.5);
        assertEquals(2.5, point.getRightBarrierHeight(), 0.001, "Right barrier height setter/getter");
        
        point.setLeftBarrierHeight(3.5);
        assertEquals(3.5, point.getLeftBarrierHeight(), 0.001, "Left barrier height setter/getter");
    }

    @Test
    public void testHasValidCoordinate() {
        BridgePoint point = new BridgePoint();
        
        assertFalse(point.hasValidCoordinate(), "Should return false for null coordinate");
        
        point.setCoordinate(new Coordinate(100.0, 200.0));
        assertTrue(point.hasValidCoordinate(), "Should return true for valid coordinate");
    }

    @Test
    public void testHasAbsoluteDeckHeight() {
        BridgePoint point = new BridgePoint();
        
        assertFalse(point.hasAbsoluteDeckHeight(), "Should return false for NaN height");
        
        point.setAbsoluteDeckHeight(15.0);
        assertTrue(point.hasAbsoluteDeckHeight(), "Should return true for valid height");
        
        point.setAbsoluteDeckHeight(Double.NaN);
        assertFalse(point.hasAbsoluteDeckHeight(), "Should return false after setting to NaN");
    }

    @Test
    public void testHasRelativeDeckHeight() {
        BridgePoint point = new BridgePoint();
        
        assertFalse(point.hasRelativeDeckHeight(), "Should return false for NaN height");
        
        point.setRelativeDeckHeight(5.0);
        assertTrue(point.hasRelativeDeckHeight(), "Should return true for valid height");
        
        point.setRelativeDeckHeight(Double.NaN);
        assertFalse(point.hasRelativeDeckHeight(), "Should return false after setting to NaN");
    }

    @Test
    public void testHasWidthData() {
        BridgePoint point = new BridgePoint();
        
        assertFalse(point.hasWidthData(), "Should return false when both widths are NaN");
        
        point.setRightWidth(5.0);
        assertTrue(point.hasWidthData(), "Should return true when right width is set");
        
        point.setRightWidth(Double.NaN);
        point.setLeftWidth(6.0);
        assertTrue(point.hasWidthData(), "Should return true when left width is set");
        
        point.setLeftWidth(Double.NaN);
        assertFalse(point.hasWidthData(), "Should return false when both widths are NaN again");
        
        point.setRightWidth(5.0);
        point.setLeftWidth(6.0);
        assertTrue(point.hasWidthData(), "Should return true when both widths are set");
    }

    @Test
    public void testHasBarrierHeightData() {
        BridgePoint point = new BridgePoint();
        
        assertFalse(point.hasBarrierHeightData(), "Should return false when both barrier heights are NaN");
        
        point.setRightBarrierHeight(2.0);
        assertTrue(point.hasBarrierHeightData(), "Should return true when right barrier height is set");
        
        point.setRightBarrierHeight(Double.NaN);
        point.setLeftBarrierHeight(3.0);
        assertTrue(point.hasBarrierHeightData(), "Should return true when left barrier height is set");
        
        point.setLeftBarrierHeight(Double.NaN);
        assertFalse(point.hasBarrierHeightData(), "Should return false when both barrier heights are NaN again");
        
        point.setRightBarrierHeight(2.0);
        point.setLeftBarrierHeight(3.0);
        assertTrue(point.hasBarrierHeightData(), "Should return true when both barrier heights are set");
    }

    @Test
    public void testPositionEnum() {
        assertEquals(3, BridgePoint.Position.values().length, "Should have 3 position values");
        assertTrue(java.util.Arrays.asList(BridgePoint.Position.values()).contains(BridgePoint.Position.CENTER), "Should contain CENTER");
        assertTrue(java.util.Arrays.asList(BridgePoint.Position.values()).contains(BridgePoint.Position.LEFT), "Should contain LEFT");
        assertTrue(java.util.Arrays.asList(BridgePoint.Position.values()).contains(BridgePoint.Position.RIGHT), "Should contain RIGHT");
    }

    @Test
    public void testToString() {
        BridgePoint point = new BridgePoint();
        point.setBridgePrimaryKey(100L);
        point.setPosition(BridgePoint.Position.LEFT);
        point.setCoordinate(new Coordinate(10.0, 20.0, 15.0));
        point.setAbsoluteDeckHeight(25.0);
        point.setRelativeDeckHeight(5.0);
        point.setDeckThickness(0.8);
        point.setRightWidth(4.0);
        point.setLeftWidth(4.5);
        point.setRightBarrierHeight(1.5);
        point.setLeftBarrierHeight(2.0);
        
        String result = point.toString();
        
        assertNotNull(result, "toString should not return null");
        assertTrue(result.contains("BridgePoint{"), "Should start with class name");
        assertTrue(result.contains("bridgePrimaryKey=100"), "Should contain bridge primary key");
        assertTrue(result.contains("position=LEFT"), "Should contain position");
        assertTrue(result.contains("coordinate="), "Should contain coordinate");
        assertTrue(result.contains("absoluteDeckHeight=25.0"), "Should contain absolute deck height");
        assertTrue(result.contains("relativeDeckHeight=5.0"), "Should contain relative deck height");
        assertTrue(result.contains("deckThickness=0.8"), "Should contain deck thickness");
        assertTrue(result.contains("rightWidth=4.0"), "Should contain right width");
        assertTrue(result.contains("leftWidth=4.5"), "Should contain left width");
        assertTrue(result.contains("rightBarrierHeight=1.5"), "Should contain right barrier height");
        assertTrue(result.contains("leftBarrierHeight=2.0"), "Should contain left barrier height");
        assertTrue(result.endsWith("}"), "Should end with closing brace");
    }

    @Test
    public void testToStringWithNaNValues() {
        BridgePoint point = new BridgePoint();
        point.setBridgePrimaryKey(100L);
        point.setPosition(BridgePoint.Position.CENTER);
        // All other values remain NaN
        
        String result = point.toString();
        
        assertNotNull(result, "toString should not return null");
        assertTrue(result.contains("BridgePoint{"), "Should start with class name");
        assertTrue(result.contains("bridgePrimaryKey=100"), "Should contain bridge primary key");
        assertTrue(result.contains("position=CENTER"), "Should contain position");
        assertFalse(result.contains("absoluteDeckHeight="), "Should not contain NaN absolute deck height");
        assertFalse(result.contains("relativeDeckHeight="), "Should not contain NaN relative deck height");
        assertFalse(result.contains("deckThickness="), "Should not contain NaN deck thickness");
        assertFalse(result.contains("rightWidth="), "Should not contain NaN right width");
        assertFalse(result.contains("leftWidth="), "Should not contain NaN left width");
        assertFalse(result.contains("rightBarrierHeight="), "Should not contain NaN right barrier height");
        assertFalse(result.contains("leftBarrierHeight="), "Should not contain NaN left barrier height");
    }

    @Test
    public void testToStringWithNullCoordinate() {
        BridgePoint point = new BridgePoint();
        point.setBridgePrimaryKey(100L);
        point.setPosition(BridgePoint.Position.RIGHT);
        // coordinate remains null
        
        String result = point.toString();
        
        assertNotNull(result, "toString should not return null");
        assertTrue(result.contains("BridgePoint{"), "Should start with class name");
        assertTrue(result.contains("bridgePrimaryKey=100"), "Should contain bridge primary key");
        assertTrue(result.contains("position=RIGHT"), "Should contain position");
        assertFalse(result.contains("coordinate="), "Should not contain null coordinate");
    }

    @Test
    public void testNegativeValues() {
        BridgePoint point = new BridgePoint();
        
        // Test that negative values are allowed (they might be valid in some contexts)
        point.setAbsoluteDeckHeight(-5.0);
        assertEquals(-5.0, point.getAbsoluteDeckHeight(), 0.001, "Should allow negative absolute height");
        
        point.setRelativeDeckHeight(-2.0);
        assertEquals(-2.0, point.getRelativeDeckHeight(), 0.001, "Should allow negative relative height");
        
        point.setDeckThickness(-0.5);
        assertEquals(-0.5, point.getDeckThickness(), 0.001, "Should allow negative thickness");
        
        point.setRightWidth(-3.0);
        assertEquals(-3.0, point.getRightWidth(), 0.001, "Should allow negative right width");
        
        point.setLeftWidth(-2.5);
        assertEquals(-2.5, point.getLeftWidth(), 0.001, "Should allow negative left width");
        
        point.setRightBarrierHeight(-1.0);
        assertEquals(-1.0, point.getRightBarrierHeight(), 0.001, "Should allow negative right barrier height");
        
        point.setLeftBarrierHeight(-1.5);
        assertEquals(-1.5, point.getLeftBarrierHeight(), 0.001, "Should allow negative left barrier height");
    }

    @Test
    public void testZeroValues() {
        BridgePoint point = new BridgePoint();
        
        point.setAbsoluteDeckHeight(0.0);
        assertEquals(0.0, point.getAbsoluteDeckHeight(), 0.001, "Should handle zero absolute height");
        assertTrue(point.hasAbsoluteDeckHeight(), "Zero should be considered a valid height");
        
        point.setRelativeDeckHeight(0.0);
        assertEquals(0.0, point.getRelativeDeckHeight(), 0.001, "Should handle zero relative height");
        assertTrue(point.hasRelativeDeckHeight(), "Zero should be considered a valid height");
        
        point.setDeckThickness(0.0);
        assertEquals(0.0, point.getDeckThickness(), 0.001, "Should handle zero thickness");
        
        point.setRightWidth(0.0);
        assertEquals(0.0, point.getRightWidth(), 0.001, "Should handle zero right width");
        assertTrue(point.hasWidthData(), "Zero width should be considered valid");
        
        point.setLeftWidth(0.0);
        assertEquals(0.0, point.getLeftWidth(), 0.001, "Should handle zero left width");
        
        point.setRightBarrierHeight(0.0);
        assertEquals(0.0, point.getRightBarrierHeight(), 0.001, "Should handle zero right barrier height");
        assertTrue(point.hasBarrierHeightData(), "Zero barrier height should be considered valid");
        
        point.setLeftBarrierHeight(0.0);
        assertEquals(0.0, point.getLeftBarrierHeight(), 0.001, "Should handle zero left barrier height");
    }

    @Test
    public void testExtremeValues() {
        BridgePoint point = new BridgePoint();
        
        // Test very large values
        point.setAbsoluteDeckHeight(Double.MAX_VALUE);
        assertEquals(Double.MAX_VALUE, point.getAbsoluteDeckHeight(), "Should handle maximum double value");
        
        point.setAbsoluteDeckHeight(Double.MIN_VALUE);
        assertEquals(Double.MIN_VALUE, point.getAbsoluteDeckHeight(), "Should handle minimum double value");
        
        // Test infinite values
        point.setRelativeDeckHeight(Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, point.getRelativeDeckHeight(), "Should handle positive infinity");
        assertTrue(point.hasRelativeDeckHeight(), "Positive infinity should be considered valid");
        
        point.setRelativeDeckHeight(Double.NEGATIVE_INFINITY);
        assertEquals(Double.NEGATIVE_INFINITY, point.getRelativeDeckHeight(), "Should handle negative infinity");
        assertTrue(point.hasRelativeDeckHeight(), "Negative infinity should be considered valid");
    }

    @Test
    public void testCoordinateIndependence() {
        Coordinate originalCoord = new Coordinate(100.0, 200.0, 15.0);
        BridgePoint point1 = new BridgePoint(originalCoord);
        BridgePoint point2 = new BridgePoint(point1);
        
        // Modify the original coordinate
        originalCoord.x = 999.0;
        originalCoord.y = 888.0;
        originalCoord.z = 777.0;
        
        // point1 should not be affected (assuming coordinate is copied in constructor)
        // This test verifies the behavior - if coordinate is stored by reference, this test will fail
        assertNotEquals(999.0, point1.getCoordinate().x, "Point1 coordinate should not be affected by original coordinate modification");
        
        // point2 should definitely not be affected (deep copy in copy constructor)
        assertNotEquals(999.0, point2.getCoordinate().x, "Point2 coordinate should not be affected by original coordinate modification");
        assertEquals(100.0, point2.getCoordinate().x, 0.001, "Point2 should have original coordinate values");
    }

    @Test
    public void testCoordinateModificationIndependence() {
        BridgePoint point1 = new BridgePoint(new Coordinate(100.0, 200.0, 15.0));
        BridgePoint point2 = new BridgePoint(point1);
        
        // Modify point1's coordinate
        point1.getCoordinate().x = 999.0;
        
        // point2 should not be affected (deep copy)
        assertEquals(100.0, point2.getCoordinate().x, 0.001, "Point2 coordinate should not be affected by point1 coordinate modification");
    }

    @Test
    public void testDefaultValuesConsistency() {
        BridgePoint point1 = new BridgePoint();
        BridgePoint point2 = new BridgePoint();
        
        assertEquals(point1.getPrimaryKey(), point2.getPrimaryKey(), "Default primary keys should be consistent");
        assertEquals(point1.getBridgePrimaryKey(), point2.getBridgePrimaryKey(), "Default bridge primary keys should be consistent");
        assertEquals(point1.getPosition(), point2.getPosition(), "Default positions should be consistent");
        
        // All NaN values should be consistently NaN
        assertTrue(Double.isNaN(point1.getAbsoluteDeckHeight()) && Double.isNaN(point2.getAbsoluteDeckHeight()), 
                  "Default absolute deck heights should both be NaN");
        assertTrue(Double.isNaN(point1.getRelativeDeckHeight()) && Double.isNaN(point2.getRelativeDeckHeight()), 
                  "Default relative deck heights should both be NaN");
        assertTrue(Double.isNaN(point1.getDeckThickness()) && Double.isNaN(point2.getDeckThickness()), 
                  "Default deck thicknesses should both be NaN");
        assertTrue(Double.isNaN(point1.getRightWidth()) && Double.isNaN(point2.getRightWidth()), 
                  "Default right widths should both be NaN");
        assertTrue(Double.isNaN(point1.getLeftWidth()) && Double.isNaN(point2.getLeftWidth()), 
                  "Default left widths should both be NaN");
        assertTrue(Double.isNaN(point1.getRightBarrierHeight()) && Double.isNaN(point2.getRightBarrierHeight()), 
                  "Default right barrier heights should both be NaN");
        assertTrue(Double.isNaN(point1.getLeftBarrierHeight()) && Double.isNaN(point2.getLeftBarrierHeight()), 
                  "Default left barrier heights should both be NaN");
    }

    @Test
    public void testDataIntegrityAfterOperations() {
        BridgePoint point = new BridgePoint();
        
        // Set all values
        Coordinate coord = new Coordinate(50.0, 60.0, 20.0);
        point.setCoordinate(coord);
        point.setPrimaryKey(123L);
        point.setBridgePrimaryKey(456L);
        point.setPosition(BridgePoint.Position.LEFT);
        point.setAbsoluteDeckHeight(30.0);
        point.setRelativeDeckHeight(10.0);
        point.setDeckThickness(1.5);
        point.setRightWidth(8.0);
        point.setLeftWidth(7.0);
        point.setRightBarrierHeight(3.0);
        point.setLeftBarrierHeight(2.5);
        
        // Create copy
        BridgePoint copy = new BridgePoint(point);
        
        // Modify original
        point.setAbsoluteDeckHeight(999.0);
        point.setPosition(BridgePoint.Position.RIGHT);
        point.getCoordinate().x = 999.0;
        
        // Verify copy is unaffected
        assertEquals(30.0, copy.getAbsoluteDeckHeight(), 0.001, "Copy should retain original absolute height");
        assertEquals(BridgePoint.Position.LEFT, copy.getPosition(), "Copy should retain original position");
        assertEquals(50.0, copy.getCoordinate().x, 0.001, "Copy coordinate should be unaffected");
        assertEquals(123L, copy.getPrimaryKey(), "Copy should retain original primary key");
        assertEquals(456L, copy.getBridgePrimaryKey(), "Copy should retain original bridge primary key");
        assertEquals(10.0, copy.getRelativeDeckHeight(), 0.001, "Copy should retain original relative height");
        assertEquals(1.5, copy.getDeckThickness(), 0.001, "Copy should retain original thickness");
        assertEquals(8.0, copy.getRightWidth(), 0.001, "Copy should retain original right width");
        assertEquals(7.0, copy.getLeftWidth(), 0.001, "Copy should retain original left width");
        assertEquals(3.0, copy.getRightBarrierHeight(), 0.001, "Copy should retain original right barrier height");
        assertEquals(2.5, copy.getLeftBarrierHeight(), 0.001, "Copy should retain original left barrier height");
    }
}
