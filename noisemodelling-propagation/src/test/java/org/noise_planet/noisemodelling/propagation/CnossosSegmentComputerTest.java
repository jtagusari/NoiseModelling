package org.noise_planet.noisemodelling.propagation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPathBuilder;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPathExt;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosSegmentComputer;
import org.noise_planet.noisemodelling.propagation.cnossos.SegmentPath;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CnossosSegmentComputer and raySourceReceiverDirectivity.
 *
 * CnossosSegmentComputer is exercised purely with coordinate inputs (no JSON fixtures).
 * raySourceReceiverDirectivity is tested via CnossosPathBuilder with minimal CutProfiles.
 */
public class CnossosSegmentComputerTest {

    private static final double DELTA = 1e-4;
    private static final double[] FLAT_MEAN_PLANE = {0.0, 0.0};
    private static final List<Double> OCTAVE_FREQ =
            Arrays.asList(63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0);

    // ----------------------------------------------------------------
    // CnossosSegmentComputer
    // ----------------------------------------------------------------

    @Test
    void testBasicGeometricProperties() {
        // src=(0,1) rcv=(100,2) on flat plane y=0
        // sourceOnPlane=(0,0), receiverOnPlane=(100,0)
        // d = sqrt(100^2+1^2), dp = 100, zsH = 1, zrH = 2
        Coordinate src = new Coordinate(0, 1);
        Coordinate rcv = new Coordinate(100, 2);

        SegmentPath seg = CnossosSegmentComputer.createSegmentPath(src, rcv, FLAT_MEAN_PLANE);

        assertEquals(0.0, seg.s.x, DELTA);
        assertEquals(1.0, seg.s.y, DELTA);
        assertEquals(100.0, seg.r.x, DELTA);
        assertEquals(2.0, seg.r.y, DELTA);

        assertEquals(0.0, seg.sMeanPlane.x, DELTA);
        assertEquals(0.0, seg.sMeanPlane.y, DELTA);
        assertEquals(100.0, seg.rMeanPlane.x, DELTA);
        assertEquals(0.0, seg.rMeanPlane.y, DELTA);

        assertEquals(Math.sqrt(100.0 * 100.0 + 1.0), seg.d, DELTA);
        assertEquals(100.0, seg.dp, DELTA);
        assertEquals(1.0, seg.zsH, DELTA);
        assertEquals(2.0, seg.zrH, DELTA);
        assertEquals(0.0, seg.a, DELTA);
        assertEquals(0.0, seg.b, DELTA);
    }

    @Test
    void testMirrorPoints() {
        // sPrime / rPrime are reflections of source/receiver across the mean plane.
        // With flat plane at y=0: sPrime=(0,-1), rPrime=(100,-2)
        Coordinate src = new Coordinate(0, 1);
        Coordinate rcv = new Coordinate(100, 2);

        SegmentPath seg = CnossosSegmentComputer.createSegmentPath(src, rcv, FLAT_MEAN_PLANE);

        assertEquals(0.0, seg.sPrime.x, DELTA);
        assertEquals(-1.0, seg.sPrime.y, DELTA);
        assertEquals(100.0, seg.rPrime.x, DELTA);
        assertEquals(-2.0, seg.rPrime.y, DELTA);
    }

    @Test
    void testGroundFactorInterpolationWhenTestFormHBelowOne() {
        // Short path (dp=10): testFormH = 10/(30*(1+2)) ≈ 0.111 < 1
        // → gPathPrime interpolated: weightedG * testFormH + gAtSource * (1-testFormH)
        Coordinate src = new Coordinate(0, 1);
        Coordinate rcv = new Coordinate(10, 2);
        double weightedG = 0.5;
        double gAtSource = 0.3;

        SegmentPath seg = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
                src, rcv, FLAT_MEAN_PLANE, weightedG, gAtSource);

        double expectedTestFormH = 10.0 / (30.0 * (1.0 + 2.0));
        assertEquals(expectedTestFormH, seg.testFormH, DELTA);
        assertTrue(seg.testFormH < 1.0);

        double expectedGPathPrime = weightedG * expectedTestFormH + gAtSource * (1 - expectedTestFormH);
        assertEquals(expectedGPathPrime, seg.gPathPrime, DELTA);
    }

    @Test
    void testGroundFactorNoInterpolationWhenTestFormHAboveOne() {
        // Long path (dp=100): testFormH = 100/(30*(1+2)) ≈ 1.11 > 1
        // → gPathPrime = weightedG (no interpolation)
        Coordinate src = new Coordinate(0, 1);
        Coordinate rcv = new Coordinate(100, 2);
        double weightedG = 0.7;
        double gAtSource = 0.3;

        SegmentPath seg = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
                src, rcv, FLAT_MEAN_PLANE, weightedG, gAtSource);

        assertTrue(seg.testFormH > 1.0);
        assertEquals(weightedG, seg.gPathPrime, DELTA);
    }

    @Test
    void testFresnelZoneCorrections() {
        // src=(0,1) rcv=(100,2): zsH=1, zrH=2, dp=100
        // CNOSSOS Eq.2.5.19: deltaZT=0.2, deltaZS=0.1111, deltaZR=0.4444
        Coordinate src = new Coordinate(0, 1);
        Coordinate rcv = new Coordinate(100, 2);
        double zsH = 1.0, zrH = 2.0, dp = 100.0;
        double alpha0 = 2e-4;

        SegmentPath seg = CnossosSegmentComputer.createSegmentPath(src, rcv, FLAT_MEAN_PLANE);

        double deltaZT = 6e-3 * dp / (zsH + zrH);
        double deltaZS = alpha0 * Math.pow(zsH / (zsH + zrH), 2) * (dp * dp / 2);
        double deltaZR = alpha0 * Math.pow(zrH / (zsH + zrH), 2) * (dp * dp / 2);

        assertEquals(zsH + deltaZS + deltaZT, seg.zsF, DELTA);
        assertEquals(zrH + deltaZR + deltaZT, seg.zrF, DELTA);
        assertEquals(dp / (30 * (seg.zsF + seg.zrF)), seg.testFormF, DELTA);
    }

    // ----------------------------------------------------------------
    // raySourceReceiverDirectivity
    // ----------------------------------------------------------------

    private static CutProfile buildSimpleProfile(Coordinate srcCoord, Orientation srcOrientation,
                                                  Coordinate rcvCoord) {
        CutProfile profile = new CutProfile();
        CutPointSource source = new CutPointSource(srcCoord);
        source.setOrientation(srcOrientation);
        profile.setSource(source);
        profile.setReceiver(new CutPointReceiver(rcvCoord));
        return profile;
    }

    @Test
    void testRaySourceReceiverDirectivityIsNullWhenSourceOrientationIsNull() {
        // computeOrientation() returns null when sourceOrientation is null
        CutProfile profile = buildSimpleProfile(
                new Coordinate(0, 0, 1), null,
                new Coordinate(100, 0, 2));

        CnossosPathExt result = CnossosPathBuilder.buildCnossosPath(profile, OCTAVE_FREQ, 0, false);
        assertNull(result.raySourceReceiverDirectivity);
    }

    @Test
    void testRaySourceReceiverDirectivityEastPath() {
        // Source facing north (0,0,0), receiver due east (+x): yaw should be ~90°
        // outgoingRay ≈ (1, 0, ~0) → fromVector: yaw = atan2(1, 0) = 90°
        CutProfile profile = buildSimpleProfile(
                new Coordinate(0, 0, 1), new Orientation(0, 0, 0),
                new Coordinate(100, 0, 2));

        CnossosPathExt result = CnossosPathBuilder.buildCnossosPath(profile, OCTAVE_FREQ, 0, false);

        assertNotNull(result.raySourceReceiverDirectivity);
        assertEquals(90.0, result.raySourceReceiverDirectivity.yaw, 1.0);
    }

    @Test
    void testRaySourceReceiverDirectivityNorthPath() {
        // Source facing north (0,0,0), receiver due north (+y): yaw should be ~0°
        // outgoingRay ≈ (0, 1, ~0) → fromVector: yaw = atan2(0, 1) = 0°
        CutProfile profile = buildSimpleProfile(
                new Coordinate(0, 0, 1), new Orientation(0, 0, 0),
                new Coordinate(0, 100, 2));

        CnossosPathExt result = CnossosPathBuilder.buildCnossosPath(profile, OCTAVE_FREQ, 0, false);

        assertNotNull(result.raySourceReceiverDirectivity);
        assertEquals(0.0, result.raySourceReceiverDirectivity.yaw, 1.0);
    }
}
