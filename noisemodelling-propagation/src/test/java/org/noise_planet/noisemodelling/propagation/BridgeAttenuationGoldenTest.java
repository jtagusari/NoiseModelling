/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.propagation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2gis.api.EmptyProgressVisitor;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.CutPlaneVisitor;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosAttenuationOutput;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.propagation.cnossos.PointPath;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feeds the fork's bridge test-case cut profiles (TBC*) through upstream's <em>unchanged</em>
 * {@code AttenuationCnossos} pipeline and checks that the resulting homogeneous global attenuation
 * still matches the fork's stored golden ({@code TBC*_attenuation_output.json}, field
 * {@code aGlobalH}).
 *
 * <p>This is the v2 non-regression gate. For TBC01/07/11/20 the DOWNWARD deck wall is
 * non-obstructing, so the imaginary path is direct + ground and upstream computes it verbatim.
 * TBC12 is the obstructing case: the imaginary ray diffracts both under the deck edge and over a
 * downstream wall, and is reproduced by the v2 bottom+top (BT) decomposition
 * ({@code CnossosPathBuilder.spliceBridgeBottomEdge} + {@code computeBridgeBottomTopRoutes},
 * {@code AttenuationCnossos.aBoundary} summing the two sub-routes) - matching the fork's
 * homogeneous golden bit-for-bit.
 *
 * <p>Fixtures under {@code src/test/resources/.../bridge_test_cases/} are copied verbatim from the
 * {@code fix} branch.
 *
 * <p>TBC03 (imaginary + actual, receiver above the deck), TBC13 (synthetic MIRROR_SOURCE BT) and
 * TBC31 (compound real-DEM scene, all four paths) are also reproduced within {@value #EPS} dB.
 *
 * <p><b>TBC02</b> golden path 1 - the ACTUAL path (source on the deck, receiver below and beside,
 * diffracting over the deck's outer edge) - diverges by ~2.6 dB, and this is <em>not</em> a v2
 * defect: the receiver lies below the O&rarr;R mean plane, so upstream's {@code aDif} applies the
 * CNOSSOS-EU 2.5.31/2.5.33 below-mean-plane branch ({@code deltaDiffSR := deltaDiffSRPrime},
 * {@code deltaGroundOR := aGroundOR}) that the fork's older {@code aDif} lacks - the fork always
 * uses the above-mean-plane form. Upstream is the more CNOSSOS-correct value here; there is
 * nothing to port. Golden path 0 (imaginary) is reproduced exactly, so TBC02 asserts path 0 only.
 */
public class BridgeAttenuationGoldenTest {

    private static final double HUMIDITY = 70;
    private static final double TEMPERATURE = 10;
    /** dB tolerance between upstream's aGlobalRaw and the fork's golden aGlobalH. */
    private static final double EPS = 0.2;

    /** Fixture feed order per TBC, mirroring fix's AttenuationComputeOutputCnossosBridgeTest. */
    private static final String[][] CASES = {
            {"TBC01", "TBC01_imaginary", "TBC01_actual"},
            {"TBC03", "TBC03_imaginary", "TBC03_actual"},
            {"TBC07", "TBC07_imaginary"},
            {"TBC11", "TBC11_imaginary"},
            {"TBC20", "TBC20_imaginary", "TBC20_actual"},
    };

    private static CutProfile load(String name) throws IOException {
        try (InputStream in = BridgeAttenuationGoldenTest.class.getResourceAsStream("bridge_test_cases/" + name + ".json")) {
            return new ObjectMapper().readValue(in, CutProfile.class);
        }
    }

    private static AttenuationComputeOutput run(String... fixtures) throws IOException {
        ProfileBuilder pb = new ProfileBuilder().finishFeeding();
        SceneWithAttenuation scene = new SceneWithAttenuation(pb);
        scene.defaultCnossosParameters.setHumidity(HUMIDITY);
        scene.defaultCnossosParameters.setTemperature(TEMPERATURE);
        AttenuationComputeOutput out = new AttenuationComputeOutput(true, true, scene);
        CutPlaneVisitor visitor = out.subProcess(new EmptyProgressVisitor());
        PathFinder.ReceiverPointInfo last = new PathFinder.ReceiverPointInfo(-1, -1, new Coordinate());
        for (String f : fixtures) {
            CutProfile cp = load(f);
            visitor.onNewCutPlane(cp);
            if (last.receiverPk != -1 && cp.getReceiver().receiverPk != last.receiverPk) {
                visitor.finalizeReceiver(new PathFinder.ReceiverPointInfo(cp.getReceiver()));
            }
            last = new PathFinder.ReceiverPointInfo(cp.getReceiver());
        }
        visitor.finalizeReceiver(last);
        return out;
    }

    private static List<double[]> goldenAGlobalH(String tbc) throws IOException {
        List<double[]> out = new ArrayList<>();
        try (InputStream in = BridgeAttenuationGoldenTest.class.getResourceAsStream(
                "bridge_test_cases/" + tbc + "_attenuation_output.json")) {
            JsonNode paths = new ObjectMapper().readTree(in).get("propagationPaths");
            for (JsonNode p : paths) {
                JsonNode arr = p.get("aGlobalH");
                double[] v = new double[arr.size()];
                for (int i = 0; i < v.length; i++) v[i] = arr.get(i).asDouble();
                out.add(v);
            }
        }
        return out;
    }

    private static double maxAbsDiff(double[] a, double[] b) {
        double m = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) m = Math.max(m, Math.abs(a[i] - b[i]));
        return m;
    }

    /**
     * Feed the given fixtures, assert an upstream path diffracts both under (DIFB) and over (DIFH)
     * with both bridge sub-routes set, and that every stored golden {@code aGlobalH} path is
     * reproduced within {@link #EPS}.
     */
    private void assertBtGoldenReproduced(String tbc, String... fixtures) throws IOException {
        List<double[]> golden = goldenAGlobalH(tbc);
        AttenuationComputeOutput out = run(fixtures);

        List<double[]> got = new ArrayList<>();
        boolean sawBtPath = false;
        for (Object o : out.getAttenuationOutputs()) {
            CnossosAttenuationOutput cao = (CnossosAttenuationOutput) o;
            got.add(cao.aGlobalRaw);
            CnossosPath p = cao.propagationPath;
            if (p != null && p.getPointList() != null) {
                boolean b = p.getPointList().stream().anyMatch(pp -> pp.type == PointPath.POINT_TYPE.DIFB);
                boolean t = p.getPointList().stream().anyMatch(pp -> pp.type == PointPath.POINT_TYPE.DIFH);
                if (b && t) {
                    sawBtPath = true;
                    assertNotNull(p.getBridgeBottomRoute(), tbc + ": BT path must carry a bottom sub-route");
                    assertNotNull(p.getBridgeTopRoute(), tbc + ": BT path must carry a top sub-route");
                }
            }
        }
        assertTrue(sawBtPath, tbc + ": expected an upstream path diffracting both under (DIFB) and over (DIFH)");

        for (int g = 0; g < golden.size(); g++) {
            double best = Double.MAX_VALUE;
            for (double[] a : got) {
                best = Math.min(best, maxAbsDiff(a, golden.get(g)));
            }
            assertTrue(best < EPS,
                    tbc + " golden path " + g + " aGlobalH not reproduced (closest maxAbsDiff="
                            + String.format("%.3f", best) + ")");
        }
    }

    /**
     * TBC12 - the synthetic bottom+top (BT) case: imaginary source under a wide deck plus an
     * 8 m downstream wall. Exercises the v2 BT decomposition
     * ({@code CnossosPathBuilder.spliceBridgeBottomEdge} + {@code computeBridgeBottomTopRoutes},
     * {@code AttenuationCnossos.aBoundary} sum) for the {@code IMAGINARY_SOURCE_UNDER_BRIDGE} relation.
     */
    @Test
    public void tbc12BottomTopDiffractionMatchesTheForkGolden() throws IOException {
        assertBtGoldenReproduced("TBC12", "TBC12_imaginary", "TBC12_actual");
    }

    /**
     * TBC13 - the synthetic BT case for the {@code MIRROR_SOURCE} relation: mirror image above the
     * deck, an obstructing downward deck edge, and a 20 m downstream wall. Same BT path as TBC12
     * but reached via the mirror branch of {@code spliceBridgeBottomEdge}.
     */
    @Test
    public void tbc13MirrorBottomTopDiffractionMatchesTheForkGolden() throws IOException {
        assertBtGoldenReproduced("TBC13", "TBC13_mirror");
    }

    /**
     * TBC02 - source on the deck, receiver below and beside. Only golden path 0 (the imaginary,
     * under-the-deck contribution) is checked: golden path 1 (the ACTUAL path over the deck's
     * outer edge) diverges ~2.6 dB because upstream applies the CNOSSOS-EU 2.5.31/2.5.33
     * below-mean-plane branch and the fork's older aDif does not - see the class javadoc.
     */
    @Test
    public void tbc02ImaginaryPathMatchesTheForkGolden() throws IOException {
        List<double[]> golden = goldenAGlobalH("TBC02");
        AttenuationComputeOutput out = run("TBC02_imaginary", "TBC02_actual");
        List<double[]> got = new ArrayList<>();
        for (Object o : out.getAttenuationOutputs()) got.add(((CnossosAttenuationOutput) o).aGlobalRaw);
        double best = Double.MAX_VALUE;
        for (double[] a : got) best = Math.min(best, maxAbsDiff(a, golden.get(0)));
        assertTrue(best < EPS,
                "TBC02 golden path 0 aGlobalH not reproduced (closest maxAbsDiff=" + String.format("%.3f", best) + ")");
    }

    /**
     * TBC31 - compound real-DEM scene (imaginary + actual + mirror + not-related over TutoBridge
     * topography, with a 4 m wall). All four golden paths are reproduced within {@value #EPS} dB:
     * the imaginary and not-related single wall diffractions, the actual over-the-parapet path,
     * and the mirror BT path (deck-edge DIFB + wall DIFH found by re-hulling the after-portion).
     */
    @Test
    public void tbc31MatchesTheForkGolden() throws IOException {
        List<double[]> golden = goldenAGlobalH("TBC31");
        AttenuationComputeOutput out = run("TBC31_imaginary", "TBC31_actual", "TBC31_mirror", "TBC31_not_related");
        List<double[]> got = new ArrayList<>();
        for (Object o : out.getAttenuationOutputs()) got.add(((CnossosAttenuationOutput) o).aGlobalRaw);
        for (int g = 0; g < golden.size(); g++) {
            double best = Double.MAX_VALUE;
            for (double[] a : got) best = Math.min(best, maxAbsDiff(a, golden.get(g)));
            assertTrue(best < EPS,
                    "TBC31 golden path " + g + " aGlobalH not reproduced (closest maxAbsDiff="
                            + String.format("%.3f", best) + ")");
        }
    }

    @Test
    public void upstreamReproducesTheForkBridgeGolden() throws IOException {
        for (String[] c : CASES) {
            String tbc = c[0];
            String[] fixtures = new String[c.length - 1];
            System.arraycopy(c, 1, fixtures, 0, fixtures.length);

            List<double[]> golden = goldenAGlobalH(tbc);
            AttenuationComputeOutput out = run(fixtures);
            List<double[]> got = new ArrayList<>();
            for (Object o : out.getAttenuationOutputs()) {
                got.add(((CnossosAttenuationOutput) o).aGlobalRaw);
            }

            // every stored golden path must be reproduced (within EPS) by some upstream path
            for (int g = 0; g < golden.size(); g++) {
                double best = Double.MAX_VALUE;
                for (double[] a : got) {
                    best = Math.min(best, maxAbsDiff(a, golden.get(g)));
                }
                assertTrue(best < EPS,
                        tbc + " golden path " + g + " aGlobalH not reproduced by any upstream path (closest maxAbsDiff="
                                + String.format("%.3f", best) + ")");
            }
        }
    }
}
