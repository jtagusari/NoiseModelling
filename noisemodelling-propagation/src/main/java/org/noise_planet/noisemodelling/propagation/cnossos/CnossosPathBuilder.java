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
 *   <li>validate and adjust reflection points against wall constraints,</li>
 *   <li>assemble path geometry (points and segments) via
 *       {@link AcousticPathBuilder}, and</li>
 *   <li>convert the assembled geometry into a {@link CnossosPathExt} with
 *       acoustic parameters.</li>
 * </ol>
 *
 * <p>Heavy computations are delegated to helper classes such as
 * {@link PivotPointCalculator}, {@link ReflectionPointValidator},
 * {@link AcousticPathBuilder}, and {@link CnossosPathProcessor}.
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
    public static CnossosPathExt buildCnossosPath(CutProfile cutProfile, List<Double> exactFrequencyArray, double groundAttenuationCoefficient, boolean bodyBarrier) {

        AcousticPathConfiguration pathConfiguration = new AcousticPathConfiguration(
            cutProfile, exactFrequencyArray, groundAttenuationCoefficient, bodyBarrier
        );

        // Delegate to the internal computation method
        if (pathConfiguration.getCutPointCoordinates2D().size() < 2) {
            throw new IllegalArgumentException("The cut profile must contain at least two points");
        }

        // Compute convex hull diffraction points for acoustic path calculation
        List<PivotPoint> horizontalEdgePivotPoints = PivotPointCalculator.computeHorizontalEdgePivotPoints(pathConfiguration);

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

        // pathConfiguration.setHorizontalEdgePivotPoints(horizontalEdgePivotPoints);

        // Create segments and points using the new API
        // Path path = AcousticPathBuilder.createPath(pathConfiguration);
        // Path path;
        AcousticPath acousticPath = new AcousticPath(horizontalEdgePivotPoints, pathConfiguration);
        for (int segmentIndex = 1; segmentIndex < horizontalEdgePivotPoints.size(); segmentIndex++) {
            acousticPath.updateWithSegmentIndex(segmentIndex);
        }
        
        // path = acousticPath.getPath();
        CnossosPathExt cnossosPath = CnossosPathProcessor.createCnossosPath(acousticPath, pathConfiguration);
        return cnossosPath;
    }
    
    
}
