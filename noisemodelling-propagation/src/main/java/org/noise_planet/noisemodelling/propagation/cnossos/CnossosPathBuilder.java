package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.math.Vector3D;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

import java.util.ArrayList;
import java.util.List;

import static org.noise_planet.noisemodelling.propagation.cnossos.PointPath.POINT_TYPE.*;

/**
 * Builds a {@link CnossosPath} from a vertical cut-profile.
 *
 * <p>This class coordinates the full path construction pipeline:
 * <ol>
 *   <li>create an {@link AcousticPathConfiguration} from a {@link CutProfile},</li>
 *   <li>compute diffraction candidate points (convex-hull based),</li>
 *   <li>validate and adjust reflection points against walls,</li>
 *   <li>assemble path segments and points via {@link AcousticPathBuilder},</li>
 *   <li>handle direct propagation special-case, and</li>
 *   <li>compute final acoustic parameters stored in {@link CnossosPath}.</li>
 * </ol>
 *
 * The class is a coordinator; the heavy work is delegated to helpers such as
 * {@link DiffractionPointComputer}, {@link ReflectionPointValidator},
 * {@link AcousticPathBuilder}, and {@link CnossosParameterCalculator}.
 */
public class CnossosPathBuilder {

    // Core configuration and processing components
    private final AcousticPathConfiguration baseConfiguration;
    
    // Working data for current computation
    private AcousticPathConfiguration pathConfiguration;
    private Path pathResult;
    private CnossosPath pathParameters;

    /**
     * Create a path builder initialized with common configuration values.
     *
     * @param exactFrequencyArray frequency bands used for computations
     * @param groundAttenuationCoefficient default ground attenuation coefficient
     */
    public CnossosPathBuilder(List<Double> exactFrequencyArray, double groundAttenuationCoefficient) {
        // Create base configuration with constructor parameters
        this.baseConfiguration = AcousticPathConfiguration.builder()
                .withExactFrequencyArray(exactFrequencyArray)
                .withGroundAttenuationCoefficient(groundAttenuationCoefficient)
                .build();
    }

    public CnossosPath main(CutProfile cutProfile, boolean bodyBarrier) {
        // Initialize configuration using base configuration
        initializePathComputation(cutProfile, bodyBarrier);
        
        // Validate input data
        if (!validateInputData()) {
            throw new IllegalArgumentException("The two arrays size should be the same");
        }

        // Setup path parameters and compute initial segment
        setupPathParameters(cutProfile);
        
        // Compute the acoustic path
        return computePathWithConfiguration(bodyBarrier);
    }
    
    /**
     * Build a runtime {@link AcousticPathConfiguration} for the given cut profile
     * and prepare empty containers that will hold the computed points and segments.
     *
     * This method clones the base configuration and injects the runtime
     * {@link CutProfile} and the bodyBarrier flag.
     */
    private void initializePathComputation(CutProfile cutProfile, boolean bodyBarrier) {
        // Create path configuration by extending base configuration with runtime data
        this.pathConfiguration = AcousticPathConfiguration.builder(baseConfiguration)
                .withCutProfile(cutProfile)
                .withBodyBarrier(bodyBarrier)
                .build();
        
        // Create result containers
        this.pathResult = new Path(new ArrayList<>(), new ArrayList<>());
    }
    
    /**
     * Quick sanity check that ensures the 2D cut-point coordinate list and the
     * cut-profile points list have the same length. Returns {@code true} when
     * the basic input arrays are consistent for further processing.
     */
    private boolean validateInputData() {
        return pathConfiguration.getCutPointCoordinates2D().size() == pathConfiguration.getCutProfilePoints().size();
    }
    
    /**
     * Prepare path-level parameters and compute the source-receiver (SR) segment.
     *
     * <p>This method computes the mean ground plane, builds the SR {@link SegmentPath}
     * (using {@link CnossosSegmentComputer}), stores the SR segment into a
     * {@link CnossosPath} instance and updates the runtime configuration with
     * the computed SR segment and path parameters.
     */
    private void setupPathParameters(CutProfile cutProfile) {
        double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(pathConfiguration.getElevationProfile2D());
        
        SegmentPath srPath = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
            pathConfiguration.getCutPointCoordinates2D(), 
            meanPlane, 
            cutProfile.calculateWeightedGroundAbsorption(), 
            cutProfile.getGroundAbsorptionAtSource()
        );
        srPath.setElevationProfile2D(pathConfiguration.getElevationProfile2D());
        srPath.setDirectRayDistance(
            CGAlgorithms3D.distance(
                cutProfile.getReceiver().getCoordinate(),
                cutProfile.getSource().getCoordinate()
            )
        );

        this.pathParameters = new CnossosPath(cutProfile);
        this.pathParameters.setFavorable(true);
        this.pathParameters.setPointList(pathResult.getPointList());
        this.pathParameters.setSegmentList(pathResult.getSegmentList());
        this.pathParameters.setSRSegment(srPath);
        this.pathParameters.init(pathConfiguration.getExactFrequencyArray().size());
        
        // Update configuration with path parameters and srPath
        this.pathConfiguration = AcousticPathConfiguration.builder(pathConfiguration)
                .withPathParameters(this.pathParameters)
                .withSrPath(srPath)
                .build();
    }


    /**
     * Compute the full acoustic path from the prepared configuration.
     *
     * Steps performed:
     * <ol>
     *   <li>compute diffraction candidate points (convex-hull based),</li>
     *   <li>validate and adjust reflection points for wall constraints,</li>
     *   <li>create segments/points by delegating to {@link AcousticPathBuilder},</li>
     *   <li>handle direct propagation if no diffraction points are present,</li>
     *   <li>finalize and return the {@link CnossosPath} containing computed parameters.</li>
     * </ol>
     *
     * Returns {@code null} when a required validation or construction step fails.
     */
    private CnossosPath computePathWithConfiguration(boolean bodyBarrier) {
        if (pathConfiguration.getCutPointCoordinates2D().size() < 2) {
            return null;
        }

        // Compute convex hull diffraction points for acoustic path calculation
        List<Coordinate> diffractionPoints = DiffractionPointComputer.computeDiffractionPoints(pathConfiguration);

        
        // Validate and adjust reflection points based on wall constraints
        if (!ReflectionPointValidator.validateAndAdjustReflectionPoints(
                diffractionPoints, 
                pathConfiguration.getCutProfilePoints(), 
                pathConfiguration.getCutPointCoordinates2D()
            )
        ) {
            return null;
        }

        // Update configuration with diffraction points
        AcousticPathConfiguration updatedConfig = AcousticPathConfiguration.builder(pathConfiguration)
                .withDiffractionPoints(diffractionPoints)
                .build();

        // Create segments and points using the new API
        AcousticPathBuilder pathBuilder = new AcousticPathBuilder(updatedConfig);
        this.pathResult = pathBuilder.createSegments();

        this.pathParameters.setPointList(pathResult.getPointList());
        this.pathParameters.setSegmentList(pathResult.getSegmentList());
        this.pathParameters.setRaySourceReceiverDirectivity(pathResult.getRaySourceReceiverDirectivity());

        return processPathResult(updatedConfig);
    }
    
    /**
     * Finalize the computed result: if no diffraction point is present handle
     * direct propagation; otherwise compute final path parameters (delta etc.)
     * and populate the {@link CnossosPath} instance returned to the caller.
     *
     * @param src source coordinate used as reference for final computations
     */
    private CnossosPath processPathResult(AcousticPathConfiguration configuration) {
        Coordinate src = configuration.getSourceCoordinate();
        Coordinate rcv = pathResult.getPointList().get(pathResult.getPointCount() - 1).coordinate;
        PointPath p0 = pathResult.getPointList().stream()
                .filter(p -> p.type.equals(DIFH))
                .findFirst()
                .orElse(null);
        
        if (p0 == null) {
            // Process direct propagation (no diffraction over obstructing objects)
            boolean directPropagationProcessed = DirectPropagationProcessor.processDirectPropagation(
                pathConfiguration,
                pathConfiguration.getSrPath(), 
                pathResult.getSegmentList(), 
                pathResult.getPointList()
            );
            if (directPropagationProcessed) {
                return pathConfiguration.getPathParameters();
            }
        }
        
        // Compute final path parameters and delta values for diffraction
        CnossosParameterCalculator.computeFinalPathParameters(
                src, rcv, 
                pathResult.getPointList(), 
                pathResult.getSegmentList(), 
                pathConfiguration.getPathParameters(), 
                pathConfiguration.getSrPath());

        return pathConfiguration.getPathParameters();
    }


    /**
     *
     * @param sourceOrientation
     * @param src
     * @param next
     * @return
     */
    public static Orientation computeOrientation(Orientation sourceOrientation, Coordinate src, Coordinate next){
        if(sourceOrientation == null) {
            return null;
        }
        Vector3D outgoingRay = new Vector3D(new Coordinate(next.x - src.x,
                next.y - src.y,
                next.z - src.z)).normalize();
        return Orientation.fromVector(Orientation.rotate(sourceOrientation, outgoingRay, true), 0);
    }
}
