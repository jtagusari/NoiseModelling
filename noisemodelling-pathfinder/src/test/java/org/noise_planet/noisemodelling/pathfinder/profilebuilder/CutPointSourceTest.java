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
import org.noise_planet.noisemodelling.pathfinder.SourcePointInfo;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CutPointSource class.
 * Tests source-specific functionality including source identification,
 * line subdivision properties, and orientation handling.
 */
public class CutPointSourceTest {

    private CutPointSource source;
    private Coordinate testCoordinate;

    @BeforeEach
    public void setUp() {
        testCoordinate = new Coordinate(10.0, 20.0, 5.0);
        source = new CutPointSource(testCoordinate);
    }

    @Test
    public void testDefaultConstructor() {
        CutPointSource defaultSource = new CutPointSource();
        assertNotNull(defaultSource.getCoordinate());
        assertEquals(-1, defaultSource.getSourcePk());
        assertEquals(-1, defaultSource.getSourceId());
        assertEquals(1.0, defaultSource.getLineLength(), 1e-9);
    }

    @Test
    public void testCoordinateConstructor() {
        assertEquals(testCoordinate.x, source.getCoordinate().x, 1e-9);
        assertEquals(testCoordinate.y, source.getCoordinate().y, 1e-9);
        assertEquals(testCoordinate.z, source.getCoordinate().z, 1e-9);
    }

    @Test
    public void testCoordinateAndLineConstructor() {
        double lineLength = 2.5;
        CutPointSource sourceWithLine = new CutPointSource(testCoordinate, lineLength);
        
        assertEquals(testCoordinate.x, sourceWithLine.getCoordinate().x, 1e-9);
        assertEquals(testCoordinate.y, sourceWithLine.getCoordinate().y, 1e-9);
        assertEquals(testCoordinate.z, sourceWithLine.getCoordinate().z, 1e-9);
        assertEquals(lineLength, sourceWithLine.getLineLength(), 1e-9);
    }

    @Test
    public void testCopyConstructor() {
        source.setSourcePk(123L);
        source.setSourceId(456);
        source.setLineLength(3.5);
        source.setGroundCoefficient(0.7);
        
        CutPointSource copy = new CutPointSource(source);
        
        assertEquals(source.getCoordinate().x, copy.getCoordinate().x, 1e-9);
        assertEquals(source.getCoordinate().y, copy.getCoordinate().y, 1e-9);
        assertEquals(source.getCoordinate().z, copy.getCoordinate().z, 1e-9);
        assertEquals(source.getGroundCoefficient(), copy.getGroundCoefficient(), 1e-9);
        
        // Verify it's a different object
        assertNotSame(source, copy);
    }

    @Test
    public void testSourcePointInfoConstructor() {
        // Create actual SourcePointInfo
        Coordinate sourceCoord = new Coordinate(15.0, 25.0, 8.0);
        int sourceIndex = 101;
        long sourcePk = 789L;
        double lineLength = 1.5;
        Orientation orientation = new Orientation();
        
        SourcePointInfo sourceInfo = new SourcePointInfo(sourceIndex, sourcePk, sourceCoord, lineLength, orientation);
        CutPointSource sourceFromInfo = new CutPointSource(sourceInfo);
        
        assertEquals(15.0, sourceFromInfo.getCoordinate().x, 1e-9);
        assertEquals(25.0, sourceFromInfo.getCoordinate().y, 1e-9);
        assertEquals(8.0, sourceFromInfo.getCoordinate().z, 1e-9);
        assertEquals(789L, sourceFromInfo.getSourcePk());
        assertEquals(1.5, sourceFromInfo.getLineLength(), 1e-9);
        assertEquals(101, sourceFromInfo.getSourceId());
        // zGround should be 0.05 below the coordinate z (8.0 - 0.05 = 7.95)
        assertEquals(7.95, sourceFromInfo.getzGround(), 1e-9);
    }

    @Test
    public void testSourceIdGetterSetter() {
        int sourceId = 42;
        source.setSourceId(sourceId);
        assertEquals(sourceId, source.getSourceId());
    }

    @Test
    public void testSourcePkGetterSetter() {
        long sourcePk = 12345L;
        source.setSourcePk(sourcePk);
        assertEquals(sourcePk, source.getSourcePk());
    }

    @Test
    public void testLineLengthGetterSetter() {
        double lineLength = 4.7;
        source.setLineLength(lineLength);
        assertEquals(lineLength, source.getLineLength(), 1e-9);
    }

    @Test
    public void testDefaultLineLength() {
        CutPointSource defaultSource = new CutPointSource();
        assertEquals(1.0, defaultSource.getLineLength(), 1e-9);
    }

    @Test
    public void testToString() {
        source.setSourcePk(999L);
        source.setSourceId(888);
        source.setLineLength(2.2);
        source.setGroundCoefficient(0.5);
        
        String str = source.toString();
        assertNotNull(str);
        assertTrue(str.contains("CutPointSource"));
        assertTrue(str.contains("sourcePk=999"));
        assertTrue(str.contains("id=888"));
        assertTrue(str.contains("li=2.2"));
        assertTrue(str.contains("groundCoefficient=0.5"));
    }

    @Test
    public void testEquals() {
        CutPointSource source1 = new CutPointSource();
        source1.setSourcePk(100L);
        source1.setSourceId(200);
        
        CutPointSource source2 = new CutPointSource();
        source2.setSourcePk(100L);
        source2.setSourceId(200);
        
        CutPointSource source3 = new CutPointSource();
        source3.setSourcePk(100L);
        source3.setSourceId(300); // Different ID
        
        CutPointSource source4 = new CutPointSource();
        source4.setSourcePk(400L); // Different PK
        source4.setSourceId(200);
        
        assertEquals(source1, source2);
        assertNotEquals(source1, source3);
        assertNotEquals(source1, source4);
        assertNotEquals(source1, null);
        assertNotEquals(source1, "not a source");
    }

    @Test
    public void testHashCode() {
        CutPointSource source1 = new CutPointSource();
        source1.setSourcePk(100L);
        source1.setSourceId(200);
        
        CutPointSource source2 = new CutPointSource();
        source2.setSourcePk(100L);
        source2.setSourceId(200);
        
        assertEquals(source1.hashCode(), source2.hashCode());
    }

    @Test
    public void testDefaultValues() {
        CutPointSource defaultSource = new CutPointSource();
        
        assertEquals(-1, defaultSource.getSourcePk());
        assertEquals(-1, defaultSource.getSourceId());
        assertEquals(1.0, defaultSource.getLineLength(), 1e-9);
        assertTrue(Double.isNaN(defaultSource.getGroundCoefficient()));
        assertTrue(Double.isNaN(defaultSource.getzGround()));
    }

    @Test
    public void testInheritedMethods() {
        // Test that inherited methods from CutPoint work correctly
        source.setGroundCoefficient(0.8);
        source.setZGround(12.5);
        
        assertEquals(0.8, source.getGroundCoefficient(), 1e-9);
        assertEquals(12.5, source.getzGround(), 1e-9);
    }

    @Test
    public void testCompareTo() {
        CutPointSource source1 = new CutPointSource(new Coordinate(0.0, 0.0, 0.0));
        CutPointSource source2 = new CutPointSource(new Coordinate(1.0, 0.0, 0.0));
        
        assertTrue(source1.compareTo(source2) < 0);
        assertTrue(source2.compareTo(source1) > 0);
        assertEquals(0, source1.compareTo(source1));
    }
}
