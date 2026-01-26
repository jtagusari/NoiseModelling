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
    public void testBridgePointDefaultConstructor() {
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
    public void testBridgePointConstructorWithCoordinate() {
        Coordinate coord = new Coordinate(100.0, 200.0, 15.0);
        BridgePoint point = new BridgePoint(coord);
        
        assertEquals(coord, point.getCoordinate(), "Coordinate should be set");
        assertEquals(-1, point.getPrimaryKey(), "Primary key should use default");
        assertEquals(BridgePoint.Position.CENTER, point.getPosition(), "Position should use default");
    }

    @Test
    public void testBridgePointFullConstructor() {
        Coordinate coord = new Coordinate(100.0, 200.0, 15.0);
        BridgePoint point = new BridgePoint(coord, 1L, 100L, 10.0, 2.0, 0.5, 5.0, 6.0, 2.0, 3.0, null, null);
        
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
    public void testBridgePointCopyConstructor() {
        Coordinate coord = new Coordinate(100.0, 200.0, 15.0);
        BridgePoint original = new BridgePoint(coord, 1L, 100L, 10.0, 2.0, 0.5, 5.0, 6.0, 2.0, 3.0, null, null);
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
    public void testBridgePointCopyConstructorWithNull() {
        assertThrows(RuntimeException.class, () -> {
            BridgePoint nullPoint = null;
            new BridgePoint(nullPoint);
        }, "Copy constructor should handle null gracefully");
    }

    @Test
    public void testBridgePointCopyConstructorWithNullCoordinate() {
        BridgePoint original = new BridgePoint();
        original.setPrimaryKey(1L);
        // coordinate is null
        
        BridgePoint copy = new BridgePoint(original);
        
        assertNull(copy.getCoordinate(), "Null coordinate should remain null");
        assertEquals(1L, copy.getPrimaryKey(), "Other fields should be copied");
    }

    @Test
    public void testBridgePointSettersAndGetters() {
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
    public void testBridgePointCoordinateIndependence() {
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
    public void testBridgePointDataIntegrityAfterOperations() {
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
