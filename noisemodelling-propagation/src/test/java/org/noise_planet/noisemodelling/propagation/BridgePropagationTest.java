/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.propagation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgePoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointBridgeWall;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPathBuilder;
import org.noise_planet.noisemodelling.propagation.cnossos.PointPath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that a bridge deck present in the {@link CutProfile} flows through the CNOSSOS path
 * builder without error and that a source on the deck yields a diffracting path (the deck edge
 * acts as a barrier), whereas the same source/receiver with no deck gives a plain direct path.
 *
 * <p>v0 models the deck edge as a generic barrier. A dedicated bridge attenuation model is a
 * later iteration.
 */
public class BridgePropagationTest {

    private static final List<Double> FREQ = Arrays.asList(
            50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0,
            800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0,
            8000.0, 10000.0);

    private static ProfileBuilder flatGround() {
        ProfileBuilder pb = new ProfileBuilder();
        for (int x = -20; x <= 120; x += 35) {
            for (int y = -20; y <= 120; y += 35) {
                pb.addTopographicPoint(new Coordinate(x, y, 0.0));
            }
        }
        return pb;
    }

    private static void addDeck(ProfileBuilder pb) {
        List<Coordinate> pts = Arrays.asList(
                new Coordinate(0, 20, 10),
                new Coordinate(50, 20, 10),
                new Coordinate(100, 20, 10));
        List<BridgePoint> bridgePoints = new ArrayList<>();
        for (long i = 0; i < pts.size(); i++) {
            bridgePoints.add(new BridgePoint.Builder(i, 100L, pts.get((int) i))
                    .withHeightType(Scene.HeightType.ABSOLUTE)
                    .withDeckThickness(0.5)
                    .withWidth(5.0, 5.0)
                    .withBarrierHeight(1.0, 1.0)
                    .withPosition(BridgePoint.Position.CENTER)
                    .withGirderType(Bridge.GirderType.STEEL_BOX)
                    .withSlabType(Bridge.SlabType.STEEL)
                    .build());
        }
        pb.addBridge(new Bridge.Builder(bridgePoints)
                .setPrimaryKey(100L)
                .setGirderType(Bridge.GirderType.STEEL_BOX)
                .setSlabType(Bridge.SlabType.STEEL)
                .build());
    }

    private static List<CnossosPath> paths(ProfileBuilder pb, Coordinate src, Coordinate rcv) {
        CutProfile profile = pb.getProfile(src, rcv);
        return CnossosPathBuilder.computeCnossosPathsFromCutProfile(profile, false, FREQ, 0.0);
    }

    @Test
    public void sourceOnDeckProducesADiffractingPathWithoutError() {
        ProfileBuilder withDeck = flatGround();
        addDeck(withDeck);
        withDeck.finishFeeding();

        Coordinate src = new Coordinate(50, 20, 10.6); // on the deck
        Coordinate rcv = new Coordinate(50, 70, 2.0);  // on the ground, past the deck

        List<CnossosPath> withDeckPaths = paths(withDeck, src, rcv);
        assertFalse(withDeckPaths.isEmpty(), "a valid path is expected");
        int maxPoints = withDeckPaths.stream().mapToInt(p -> p.getPointList().size()).max().orElse(0);
        assertTrue(maxPoints > 2,
                "the deck edge should introduce at least one diffraction point (got " + maxPoints + " points)");
    }

    /**
     * Source on the deck, receiver below AND beside it (off-axis, inside the bridge footprint):
     * the deck's lateral ("OTHER") edge pivot is found by a shortest-path search along the edge,
     * so the resulting source -&gt; pivot -&gt; receiver path is not collinear with the straight
     * source/receiver line. The CutProfile's 2D-unfolded distance ({@code d}) therefore no longer
     * matches the true source/receiver distance ({@code dc}), and the CNOSSOS delta must be
     * computed from {@code dc}.
     */
    @Test
    public void sourceOnDeckToReceiverBelowAndBesideUsesTrueDirectDistance() {
        ProfileBuilder withDeck = flatGround();
        addDeck(withDeck);
        withDeck.finishFeeding();

        Coordinate src = new Coordinate(30, 20, 10.6); // on the deck
        Coordinate rcv = new Coordinate(70, 22, 2.0);  // below and beside the deck

        List<CnossosPath> withDeckPaths = paths(withDeck, src, rcv);
        assertFalse(withDeckPaths.isEmpty(), "a valid path is expected");

        double dc = src.distance3D(rcv);
        boolean foundPathUsingTrueDirectDistance = false;
        for (CnossosPath path : withDeckPaths) {
            assertEquals(dc, path.getSRSegment().dc, 1e-6,
                    "dc must always be the true source-receiver distance");
            assertTrue(Math.abs(path.getSRSegment().d - dc) > 0.5,
                    "this off-axis geometry should make the 2D-unfolded distance diverge from the true one");

            CutPoint pivotPoint = path.getCutPoints().get(1);
            assertTrue(pivotPoint instanceof CutPointBridgeWall
                            && ((CutPointBridgeWall) pivotPoint).getWallDirection() == CutPointBridgeWall.WallDirection.OTHER,
                    "expected the deck's off-axis (OTHER) edge to be the pivot for this scenario");

            Coordinate pivot = pivotPoint.getCoordinate();
            double expectedDeltaUsingDc = src.distance3D(pivot) + path.e + pivot.distance3D(rcv) - dc;
            if (Math.abs(expectedDeltaUsingDc - path.delta) < 1e-6) {
                foundPathUsingTrueDirectDistance = true;
            }
        }
        assertTrue(foundPathUsingTrueDirectDistance,
                "at least one path's delta must be computed from the true direct distance dc, "
                        + "not the invalid 2D-unfolded distance d");
    }

    /**
     * Golden regression for the source-on-deck path: v1 puts the source on the deck surface and the
     * ray diffracts over the parapet top. These numbers pin that geometry so a later refactor of the
     * bridge cut-profile / elevation handling can't silently change it.
     */
    @Test
    public void sourceOnDeckPathGeometryIsStable() {
        ProfileBuilder withDeck = flatGround();
        addDeck(withDeck);
        withDeck.finishFeeding();

        List<CnossosPath> p = paths(withDeck,
                new Coordinate(50, 20, 10.6), new Coordinate(50, 70, 2.0));
        assertEquals(2, p.size(), "homogeneous + favourable path");

        CnossosPath hom = p.get(0);
        List<PointPath> pts = hom.getPointList();
        // point list is in the 2-D source->receiver plane: coordinate.x = distance along the ray,
        // coordinate.y = height.
        assertEquals(3, pts.size(), "source, one diffraction point, receiver");
        assertEquals(PointPath.POINT_TYPE.SRCE, pts.get(0).type);
        assertEquals(PointPath.POINT_TYPE.DIFH, pts.get(1).type);
        assertEquals(PointPath.POINT_TYPE.RECV, pts.get(2).type);
        // source sits on the deck surface, height 10.6 (v1 on-deck elevation)
        assertEquals(10.6, pts.get(0).coordinate.y, 0.05, "source height, got " + pts.get(0).coordinate.y);
        // single diffraction over the receiver-side parapet top: 5 m along the ray, height 11 m (deck 10 + barrier 1)
        assertEquals(5.0, pts.get(1).coordinate.x, 0.1, "diffraction point distance, got " + pts.get(1).coordinate.x);
        assertEquals(11.0, pts.get(1).coordinate.y, 0.1, "diffraction over the parapet top, got " + pts.get(1).coordinate.y);
        assertEquals(0.0, hom.e, 1e-6, "single diffraction edge -> e = 0");
        assertEquals(50.73, hom.getSRSegment().d, 0.1, "SR distance, got " + hom.getSRSegment().d);
    }

    @Test
    public void sameGeometryWithoutDeckIsADirectPath() {
        ProfileBuilder noDeck = flatGround();
        noDeck.finishFeeding();

        Coordinate src = new Coordinate(50, 20, 10.6);
        Coordinate rcv = new Coordinate(50, 70, 2.0);

        List<CnossosPath> noDeckPaths = paths(noDeck, src, rcv);
        assertFalse(noDeckPaths.isEmpty());
        int maxPoints = noDeckPaths.stream().mapToInt(p -> p.getPointList().size()).max().orElse(0);
        assertTrue(maxPoints <= 2, "no deck : plain source-receiver path expected (got " + maxPoints + " points)");
    }
}
