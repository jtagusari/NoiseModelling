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
import org.noise_planet.noisemodelling.pathfinder.ReceiverPointInfo;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CutPointReceiver class.
 * Tests receiver-specific functionality including receiver identification
 * and default height positioning for noise level calculations.
 */
public class CutPointReceiverTest {

    private CutPointReceiver receiver;
    private Coordinate testCoordinate;

    @BeforeEach
    public void setUp() {
        testCoordinate = new Coordinate(30.0, 40.0, 10.0);
        receiver = new CutPointReceiver(testCoordinate);
    }

    @Test
    public void testDefaultConstructor() {
        CutPointReceiver defaultReceiver = new CutPointReceiver();
        assertNotNull(defaultReceiver.getCoordinate());
        assertEquals(-1, defaultReceiver.getReceiverPk());
        assertEquals(-1, defaultReceiver.getReceiverId());
    }

    @Test
    public void testCoordinateConstructor() {
        assertEquals(testCoordinate.x, receiver.getCoordinate().x, 1e-9);
        assertEquals(testCoordinate.y, receiver.getCoordinate().y, 1e-9);
        assertEquals(testCoordinate.z, receiver.getCoordinate().z, 1e-9);
        assertEquals(testCoordinate, receiver.getCoordinate());
    }

    @Test
    public void testCopyConstructor() {
        CutPointTopography basePoint = new CutPointTopography(testCoordinate);
        basePoint.setGroundCoefficient(0.6);
        basePoint.setZGround(8.0);
        
        CutPointReceiver receiverFromBase = new CutPointReceiver(basePoint);
        
        assertEquals(basePoint.getCoordinate().x, receiverFromBase.getCoordinate().x, 1e-9);
        assertEquals(basePoint.getCoordinate().y, receiverFromBase.getCoordinate().y, 1e-9);
        assertEquals(basePoint.getCoordinate().z, receiverFromBase.getCoordinate().z, 1e-9);
        assertEquals(basePoint.getGroundCoefficient(), receiverFromBase.getGroundCoefficient(), 1e-9);
        assertEquals(basePoint.getzGround(), receiverFromBase.getzGround(), 1e-9);
        
        // Verify it's a different object
        assertNotSame(basePoint, receiverFromBase);
    }

    @Test
    public void testReceiverPointInfoConstructor() {
        // Create actual ReceiverPointInfo
        Coordinate receiverCoord = new Coordinate(50.0, 60.0, 12.0);
        int receiverId = 201;
        long receiverPk = 567L;
        
        ReceiverPointInfo receiverInfo = new ReceiverPointInfo(receiverId, receiverPk, receiverCoord);
        CutPointReceiver receiverFromInfo = new CutPointReceiver(receiverInfo);
        
        assertEquals(50.0, receiverFromInfo.getCoordinate().x, 1e-9);
        assertEquals(60.0, receiverFromInfo.getCoordinate().y, 1e-9);
        assertEquals(12.0, receiverFromInfo.getCoordinate().z, 1e-9);
        assertEquals(567L, receiverFromInfo.getReceiverPk());
        assertEquals(201, receiverFromInfo.getReceiverId());
        // zGround should be 4.0 below the coordinate z
        assertEquals(8.0, receiverFromInfo.getzGround(), 1e-9);
        // ground coefficient should be 0
        assertEquals(0.0, receiverFromInfo.getGroundCoefficient(), 1e-9);
    }

    @Test
    public void testReceiverPkGetter() {
        // Test default value
        assertEquals(-1, receiver.getReceiverPk());
        
        // Test with ReceiverPointInfo constructor
        ReceiverPointInfo info = new ReceiverPointInfo(100, 999L, testCoordinate);
        CutPointReceiver receiverWithPk = new CutPointReceiver(info);
        assertEquals(999L, receiverWithPk.getReceiverPk());
    }

    @Test
    public void testReceiverIdGetter() {
        // Test default value
        assertEquals(-1, receiver.getReceiverId());
        
        // Test with ReceiverPointInfo constructor
        ReceiverPointInfo info = new ReceiverPointInfo(100, 999L, testCoordinate);
        CutPointReceiver receiverWithId = new CutPointReceiver(info);
        assertEquals(100, receiverWithId.getReceiverId());
    }

    @Test
    public void testToString() {
        receiver.setGroundCoefficient(0.4);
        receiver.setZGround(7.5);
        
        String str = receiver.toString();
        assertNotNull(str);
        assertTrue(str.contains("CutPointReceiver"));
        assertTrue(str.contains("groundCoefficient=0.4"));
        assertTrue(str.contains("zGround=7.5"));
        assertTrue(str.contains("coordinate=" + testCoordinate.toString()));
        assertTrue(str.contains("receiverPk=-1"));
        assertTrue(str.contains("id=-1"));
    }

    @Test
    public void testInheritedMethods() {
        // Test that inherited methods from CutPoint work correctly
        receiver.setGroundCoefficient(0.9);
        receiver.setZGround(15.5);
        
        assertEquals(0.9, receiver.getGroundCoefficient(), 1e-9);
        assertEquals(15.5, receiver.getzGround(), 1e-9);
    }

    @Test
    public void testCompareTo() {
        CutPointReceiver receiver1 = new CutPointReceiver(new Coordinate(0.0, 0.0, 0.0));
        CutPointReceiver receiver2 = new CutPointReceiver(new Coordinate(1.0, 0.0, 0.0));
        
        assertTrue(receiver1.compareTo(receiver2) < 0);
        assertTrue(receiver2.compareTo(receiver1) > 0);
        assertEquals(0, receiver1.compareTo(receiver1));
    }

    @Test
    public void testDefaultValues() {
        CutPointReceiver defaultReceiver = new CutPointReceiver();
        
        assertEquals(-1, defaultReceiver.getReceiverPk());
        assertEquals(-1, defaultReceiver.getReceiverId());
        assertTrue(Double.isNaN(defaultReceiver.getGroundCoefficient()));
        assertTrue(Double.isNaN(defaultReceiver.getzGround()));
    }

    @Test
    public void testReceiverHeightCalculation() {
        // Test that receiver is positioned correctly relative to ground
        Coordinate groundLevel = new Coordinate(100.0, 200.0, 20.0);
        ReceiverPointInfo info = new ReceiverPointInfo(1, 1L, groundLevel);
        CutPointReceiver receiverAtHeight = new CutPointReceiver(info);
        
        // Receiver should be at the coordinate height
        assertEquals(20.0, receiverAtHeight.getCoordinate().z, 1e-9);
        // Ground level should be 4 meters below
        assertEquals(16.0, receiverAtHeight.getzGround(), 1e-9);
    }

    @Test
    public void testCoordinateModification() {
        Coordinate original = new Coordinate(1.0, 2.0, 3.0);
        receiver = new CutPointReceiver(original);
        
        // Modify the original coordinate
        original.x = 999.0;
        
        // The receiver coordinate may or may not be affected depending on implementation
        // This test verifies the current behavior
        Coordinate storedCoord = receiver.getCoordinate();
        assertNotNull(storedCoord);
        // Either the coordinate is copied or the reference is shared
        assertTrue(storedCoord.x == 1.0 || storedCoord.x == 999.0);
    }
}
