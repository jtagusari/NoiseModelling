package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.*;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;

import java.util.List;

/**
 * Constructs a {@link CnossosPathExt} from a vertical {@link CutProfile}.
 *
 * <p>This class orchestrates the acoustic path construction pipeline. It
 * prepares a runtime {@link AcousticPathConfiguration} and delegates the
 * computational tasks to helper components. The main responsibilities are:
 * <ol>
 *   <li>build a runtime configuration from the provided {@link CutProfile},</li>
 *   <li>identify diffraction candidate points (convex-hull based),</li>
 *   <li>validate and adjust reflection points against wall constraints, and</li>
 *   <li>convert the assembled geometry into a {@link CnossosPathExt} with
 *       acoustic parameters.</li>
 * </ol>
 *
 * <p>Heavy computations are delegated to helper classes such as
 * {@link PivotPointCalculator}, {@link ReflectionPointValidator},
 and {@link CnossosPathProcessor}.
 */
public class CnossosPathBuilder {

    /**
     * Construct and compute a {@link CnossosPathExt} from the supplied inputs.
     *
     * <p>This static convenience method builds a runtime
     * {@link AcousticPathConfiguration} using the provided {@link CutProfile},
     * frequency array and ground attenuation coefficient, then performs the
     * full path computation (diffraction candidate selection, reflection
     * validation and path assembly).
     *
     * @param cutProfile vertical cut profile used to derive path geometry
     * @param exactFrequencyArray list of frequency bands used in calculations
     * @param groundAttenuationCoefficient default ground attenuation coefficient
     * @param bodyBarrier whether the cut represents a body barrier
     * @return a completed {@link CnossosPathExt}
     * @throws IllegalArgumentException if the cut profile is invalid (fewer
     *         than two points) or if reflection validation fails
     */
    public static CnossosPathExt buildCnossosPath(CutProfile cutProfile, List<Double> exactFrequencyArray, double groundAttenuationCoefficient, boolean bodyBarrier, boolean omitBridgeDownwardEdge) {

        AcousticPathConfiguration pathConfiguration = new AcousticPathConfiguration(
            cutProfile, exactFrequencyArray, groundAttenuationCoefficient, bodyBarrier
        );

        // Compute convex hull diffraction points for acoustic path calculation
        List<PivotPoint> horizontalEdgePivotPoints = buildHorizontalEdgePivotPoints(pathConfiguration, omitBridgeDownwardEdge);

        // Create segments and points using the new API
        AcousticPath acousticPath = new AcousticPath(horizontalEdgePivotPoints, pathConfiguration);
        
        // path = acousticPath.getPath();
        CnossosPathExt cnossosPath = CnossosPathProcessor.createCnossosPath(acousticPath, pathConfiguration);
        return cnossosPath;
    }
    
    public static CnossosPathExt buildCnossosPath(CutProfile cutProfile, List<Double> exactFrequencyArray, double groundAttenuationCoefficient, boolean bodyBarrier) {
        return buildCnossosPath(cutProfile, exactFrequencyArray, groundAttenuationCoefficient, bodyBarrier, false);
    }

    private static List<PivotPoint> buildHorizontalEdgePivotPoints(AcousticPathConfiguration pathConfiguration, boolean omitBridgeDownwardEdge) {

        // Compute convex hull diffraction points for acoustic path calculation
        List<PivotPoint> horizontalEdgePivotPoints = PivotPointCalculator.computeHorizontalEdgePivotPoints(pathConfiguration, omitBridgeDownwardEdge);

        if (horizontalEdgePivotPoints.size() < 2) {
            throw new IllegalArgumentException("At least source and receiver points are required.");
        }
        
        // Validate and adjust reflection points based on wall constraints
        boolean isValidReflectionPoints = ReflectionPointValidator.validateAndAdjustReflectionPoints(
            horizontalEdgePivotPoints, 
            pathConfiguration.getCutProfilePoints(), 
            pathConfiguration.getCutPointCoordinates2D()
        );
        if (!isValidReflectionPoints) {
            throw new IllegalArgumentException("Invalid reflection points");
        }

        return horizontalEdgePivotPoints;
    }
    
    
}
