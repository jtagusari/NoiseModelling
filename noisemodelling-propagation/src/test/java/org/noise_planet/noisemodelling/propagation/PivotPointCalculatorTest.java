package org.noise_planet.noisemodelling.propagation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReflection;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.propagation.cnossos.AcousticPathConfiguration;
import org.noise_planet.noisemodelling.propagation.cnossos.PivotPoint;
import org.noise_planet.noisemodelling.propagation.cnossos.PivotPointCalculator;
import org.noise_planet.noisemodelling.propagation.cnossos.ReflectionPointValidator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PivotPointCalculatorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PivotPointCalculatorTest.class);

    private static CutProfile loadCutProfile(String utName) throws IOException {
        String testCaseFileName = utName + ".json";
        try (InputStream inputStream = PathFinder.class.getResourceAsStream("test_cases/" + testCaseFileName)) {
            Objects.requireNonNull(inputStream, "Missing test resource: test_cases/" + testCaseFileName);
            return new ObjectMapper().readValue(inputStream, CutProfile.class);
        }
    }

    private static void writePivotPointJson(String utName, AcousticPathConfiguration configuration, List<PivotPoint> pivotPoints) throws IOException {
        Map<String, Object> out = new HashMap<>();
        out.put("testCase", utName);
        out.put("source2D", Map.of("x", configuration.getSourceCoordinate2D().x, "y", configuration.getSourceCoordinate2D().y));
        out.put("receiver2D", Map.of("x", configuration.getReceiverCoordinate2D().x, "y", configuration.getReceiverCoordinate2D().y));
        List<Map<String, Object>> pivots = pivotPoints.stream().map(p -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", p.getPivotType().toString());
            entry.put("x", p.x);
            entry.put("z", p.y);
            return entry;
        }).collect(Collectors.toList());
        out.put("pivots", pivots);

        Path outDir = Paths.get("src", "test", "resources", "org", "noise_planet", "noisemodelling", "propagation");
        Files.createDirectories(outDir);
        File outFile = outDir.resolve("PivotPointCalculatorTest-" + utName + ".json").toFile();
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outFile, out);
        LOGGER.info("Wrote JSON output to {}", outFile.getAbsolutePath());
    }

    @Test
    void computeHorizontalEdgePivotPoints_returnsSourceAndReceiverForDirectPath() throws IOException {
        CutProfile cutProfile = loadCutProfile("TC01_Direct");
        AcousticPathConfiguration configuration = new AcousticPathConfiguration(cutProfile, Arrays.asList(125.0d), 0.0d, false);
        List<PivotPoint> pivotPoints = PivotPointCalculator.computeHorizontalEdgePivotPoints(configuration);

        LOGGER.info("TC01_Direct case:");
        LOGGER.info("  Pivot points count: {}", pivotPoints.size());
        for (PivotPoint p : pivotPoints) {
            LOGGER.info("  Pivot: type={}, x={}, z={}", p.getPivotType(), p.x, p.y);
        }

        writePivotPointJson("TC01_Direct", configuration, pivotPoints);

        assertNotNull(pivotPoints);
        assertEquals(2, pivotPoints.size());
        assertEquals(PivotPoint.PivotType.SOURCE, pivotPoints.get(0).getPivotType());
        assertEquals(PivotPoint.PivotType.RECEIVER, pivotPoints.get(1).getPivotType());
        assertEquals(configuration.getSourceCoordinate2D().x, pivotPoints.get(0).x, 1e-9);
        assertEquals(configuration.getReceiverCoordinate2D().x, pivotPoints.get(1).x, 1e-9);
    }

    @Test
    void computeHorizontalEdgePivotPoints_returnsSourceAndReceiverForTC08Direct() throws IOException {
        CutProfile cutProfile = loadCutProfile("TC08_Direct");
        AcousticPathConfiguration configuration = new AcousticPathConfiguration(cutProfile, Arrays.asList(125.0d), 0.0d, false);
        List<PivotPoint> pivotPoints = PivotPointCalculator.computeHorizontalEdgePivotPoints(configuration);

        LOGGER.info("TC08_Direct case:");
        LOGGER.info("  Pivot points count: {}", pivotPoints.size());
        for (PivotPoint p : pivotPoints) {
            LOGGER.info("  Pivot: type={}, x={}, z={}", p.getPivotType(), p.x, p.y);
        }

        writePivotPointJson("TC08_Direct", configuration, pivotPoints);

        assertNotNull(pivotPoints);
        assertTrue(pivotPoints.size() >= 2);
        assertEquals(PivotPoint.PivotType.SOURCE, pivotPoints.get(0).getPivotType());
        int last = pivotPoints.size() - 1;
        assertEquals(PivotPoint.PivotType.RECEIVER, pivotPoints.get(last).getPivotType());
        assertEquals(configuration.getSourceCoordinate2D().x, pivotPoints.get(0).x, 1e-9);
        assertEquals(configuration.getReceiverCoordinate2D().x, pivotPoints.get(last).x, 1e-9);
    }

    @Test
    void computeHorizontalEdgePivotPoints_returnsSourceAndReceiverForTC16Reflection() throws IOException {
        CutProfile cutProfile = loadCutProfile("TC16_Reflection");
        AcousticPathConfiguration configuration = new AcousticPathConfiguration(cutProfile, Arrays.asList(125.0d), 0.0d, false);
        List<PivotPoint> pivotPoints = PivotPointCalculator.computeHorizontalEdgePivotPoints(configuration);

        LOGGER.info("[TC16] Pivot points count: {}", pivotPoints.size());
        for (PivotPoint p : pivotPoints) {
            LOGGER.info("[TC16] Pivot: type={}, x={}, z={}", p.getPivotType(), p.x, p.y);
        }

        writePivotPointJson("TC16_Reflection", configuration, pivotPoints);

        assertNotNull(pivotPoints);
        assertTrue(pivotPoints.size() >= 2);
        assertEquals(PivotPoint.PivotType.SOURCE, pivotPoints.get(0).getPivotType());
        int last = pivotPoints.size() - 1;
        assertEquals(PivotPoint.PivotType.RECEIVER, pivotPoints.get(last).getPivotType());
        assertEquals(configuration.getSourceCoordinate2D().x, pivotPoints.get(0).x, 1e-9);
        assertEquals(configuration.getReceiverCoordinate2D().x, pivotPoints.get(last).x, 1e-9);
    }

    @Test
    void validateAndAdjustReflectionPoints_adjustsInteriorReflectionPoint() throws IOException {
        CutProfile cutProfile = loadCutProfile("TC17_Reflection");
        AcousticPathConfiguration configuration = new AcousticPathConfiguration(cutProfile, Arrays.asList(125.0d), 0.0d, false);

        // Build a minimal acoustic path with a single interior point that should be treated as a reflection candidate.
        List<PivotPoint> pathPivotPoints = new ArrayList<>();
        pathPivotPoints.add(new PivotPoint(configuration.getSourceCoordinate2D(), PivotPoint.PivotType.SOURCE));
        pathPivotPoints.add(new PivotPoint(configuration.getCutPointCoordinates2D().get(2), PivotPoint.PivotType.TOP_OF_OBSTACLE));
        pathPivotPoints.add(new PivotPoint(configuration.getReceiverCoordinate2D(), PivotPoint.PivotType.RECEIVER));

        // Use the original cut profile and 2D coordinates as inputs to the validator.
        List<CutPoint> cutProfilePoints = configuration.getCutProfilePoints();
        List<Coordinate> cutPointCoordinates2D = configuration.getCutPointCoordinates2D();

        // Find the reflection point so we can assert that it gets adjusted in place.
        int reflectionIndex = -1;
        for (int i = 0; i < cutProfilePoints.size(); i++) {
            if (cutProfilePoints.get(i) instanceof CutPointReflection) {
                reflectionIndex = i;
                break;
            }
        }
        assertTrue(reflectionIndex >= 0);

        // Compute the expected adjusted position by projecting the reflection point onto the path segment.
        Coordinate originalReflection2D = new Coordinate(cutPointCoordinates2D.get(reflectionIndex));
        Coordinate expectedAdjusted = new LineSegment(pathPivotPoints.get(1), pathPivotPoints.get(2)).closestPoint(originalReflection2D);

        // Validate and adjust the reflection point using the production validator.
        boolean valid = ReflectionPointValidator.validateAndAdjustReflectionPoints(pathPivotPoints, cutProfilePoints, cutPointCoordinates2D);

        LOGGER.info("[TC17] Reflection index: {}", reflectionIndex);
        LOGGER.info("[TC17] Original reflection 2D: x={}, y={}", originalReflection2D.x, originalReflection2D.y);
        LOGGER.info("[TC17] Expected adjusted reflection 2D: x={}, y={}", expectedAdjusted.x, expectedAdjusted.y);
        LOGGER.info("[TC17] Actual adjusted reflection 2D: x={}, y={}", cutPointCoordinates2D.get(reflectionIndex).x, cutPointCoordinates2D.get(reflectionIndex).y);

        assertTrue(valid);
        assertEquals(expectedAdjusted.y, cutPointCoordinates2D.get(reflectionIndex).y, 1e-9);
        assertEquals(expectedAdjusted.y, cutProfilePoints.get(reflectionIndex).getCoordinate().z, 1e-9);
    }
}