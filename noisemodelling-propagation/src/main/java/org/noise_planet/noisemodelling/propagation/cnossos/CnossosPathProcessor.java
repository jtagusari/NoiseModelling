package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointWall;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointBridgeWall;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointVEdgeDiffraction;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.noise_planet.noisemodelling.propagation.cnossos.PointPath.POINT_TYPE.*;


/**
 * Processes direct propagation and simple diffraction-related adjustments
 * for an acoustic path following CNOSSOS-EU style logic.
 *
 * <p>This utility class provides routines that: build the straight
 * source-receiver segment with ground factors, detect whether the path
 * should be treated as a pure direct path or requires Rayleigh-type
 * diffraction treatment, and compute the CNOSSOS-specific delta values
 * (deltaH, deltaF, deltaPrimeH, etc.) used downstream in level
 * calculations.
 *
 * <p>All methods operate on the provided {@link CnossosPathExt} and
 * {@link AcousticPathConfiguration} objects and mutate the {@link CnossosPathExt}
 * instance in-place (they do not create or return new CnossosPathExt objects).
 */
public class CnossosPathProcessor {
    private static final double SOUND_SPEED = 340.0;


    /**
     * Build and initialise a CNOSSOS-style acoustic path from the
     * provided Pathfinder `Path` and runtime `AcousticPathConfiguration`.
     *
     * Behaviour and side-effects:
     * - Computes a straight source->receiver `SegmentPath` including
     *   ground factors and stores it as the SR segment on the resulting
     *   `CnossosPathExt`.
     * - Copies the point and segment lists and ray directivity from the
     *   input `Path` into the `CnossosPathExt` instance.
     * - Initialises internal arrays sized to the number of exact
     *   frequencies in `configuration` and computes CNOSSOS-specific
     *   delta parameters by calling `setDiffractionPathParameters` or `setDirectPathParameters`.
     *
     * Note: the method mutates the returned `CnossosPathExt` (it is
     * initialised and populated) but does not modify the input
     * `configuration` or `acousticPath` objects.
     *
     * @param acousticPath acousticPath produced by the pathfinder (contains points/segments)
     * @param configuration runtime configuration containing the cut-profile,
     *                      elevation profile and frequency array
     * @return a fully initialised `CnossosPathExt` ready for attenuation
     *         computations (contains SR segment, points, segments and
     *         CNOSSOS delta parameters)
     */
    public static CnossosPathExt createCnossosPath(AcousticPath acousticPath, AcousticPathConfiguration configuration) {

        CnossosPathExt cnossosPath = new CnossosPathExt(acousticPath.getPath(), configuration);
        cnossosPath.init(configuration.getExactFrequencyArray().size());

        boolean hasDifB = false;
        boolean hasDifT = false;

        for (PointPath p : cnossosPath.getPointList()) {
            if (p.type.equals(DIFB)) hasDifB = true;
            else if (p.type.equals(DIFH)) hasDifT = true;
            
            if (hasDifB && hasDifT) break;
        }

        if (hasDifB && hasDifT) {
            setDiffractionPathParametersBT(cnossosPath, configuration);
        } else if (hasDifB) {
            setDiffractionPathParametersB(cnossosPath, configuration);
        } else if (hasDifT) {
            setDiffractionPathParametersT(cnossosPath, configuration);
        } else {
            setDirectPathParameters(cnossosPath, configuration);
        }

        return cnossosPath;
    }

    /**
     * Populate the supplied {@link CnossosPathExt} for the direct-propagation
     * case or detect and insert single-point Rayleigh diffraction
     * contributions when required.
     *
     * <p>Behaviour summary:
     * <ul>
     *   <li>If the cut-profile already contains a vertical-edge diffraction
     *       point (V-edge), the path is treated as a diffraction path and
     *       Rayleigh screening is skipped.</li>
     *   <li>Otherwise the method attempts Rayleigh screening on interior
     *       cut-profile points. If qualifying obstacle points are found, the
     *       corresponding two-segment paths and DIFH_RCRIT point(s) are
     *       appended to {@code cnossosPath} and the method returns true
     *       via the side-effects.</li>
     *   <li>If no Rayleigh obstacle is added, deltaH/deltaF for the straight
     *       source->receiver path are computed and stored in
     *       {@code cnossosPath}.</li>
     * </ul>
     *
     * <p>This method mutates the provided {@link CnossosPathExt} instance and
     * does not return a new object.
     *
     * @param cnossosPath path data object to populate (mutated)
     * @param pathConfiguration configuration object containing cut-profile and coordinates
     */
    private static void setDirectPathParameters(CnossosPathExt cnossosPath, AcousticPathConfiguration pathConfiguration) {
    
        // If any vertical-edge diffraction point exists in the cut profile,
        // the path is already treated as a diffraction path (not a pure
        // direct path) so we must not attempt Rayleigh screening.
        boolean hasVEdgeDiffractionPoint = pathConfiguration.getCutProfilePoints().stream()
            .anyMatch(cutPoint -> cutPoint instanceof CutPointVEdgeDiffraction);

        
        boolean hasRayleighDiffraction = false;
        // Only attempt Rayleigh-type diffraction screening when the path is
        // currently direct (no explicit vertical-edge diffraction points).
        if (!hasVEdgeDiffractionPoint) {
            hasRayleighDiffraction = setRayleighDiffractionEffects(cnossosPath, pathConfiguration);
        }
        
        if(!hasRayleighDiffraction) {
            // Ensure there is at least the straight source-receiver segment
            if (cnossosPath.getSegmentList().isEmpty()) {
                cnossosPath.addSegment(cnossosPath.getSRSegment());
            }

            // Compute the cumulative distance e between the first and last
            // non-reflection points. This is used to calculate deltaH/deltaF
            // for the straight-path case.            
            cnossosPath.e = calculateDistanceBetweenDiffractionPoints(cnossosPath.getPointList());

            // If there are no vertical-edge diffractions, use the straight-line
            // distance d; otherwise use the corrected distance dc.
            long verticalEdgeDiffractionCount = cnossosPath.getPointList().stream()
                .filter(pointPath -> pointPath.type.equals(DIFV)).count();

            double distance = verticalEdgeDiffractionCount == 0 ? cnossosPath.getSRSegment().d : cnossosPath.getSRSegment().dc;
            cnossosPath.deltaH = cnossosPath.getSegmentList().get(0).d + cnossosPath.e + cnossosPath.getSegmentList().get(cnossosPath.getSegmentList().size() - 1).d - distance;
            cnossosPath.deltaF = cnossosPath.deltaH;
        }
        return;

    }

    
    private static void setDiffractionPathParametersBT(CnossosPathExt cnossosPath, AcousticPathConfiguration configuration){ 
        Coordinate sourceCoordinate = configuration.getSourceCoordinate2D();
        Coordinate receiverCoordinate = configuration.getReceiverCoordinate2D();
        
        // Find the diffraction point of U
        PointPath diffractionPointB = cnossosPath.getPointList().stream()
        .filter(p -> p.type.equals(DIFB)).findFirst().orElse(null);
        if(diffractionPointB == null) {
            throw new IllegalArgumentException("No horizontal diffraction points found in a diffraction path");
        }

        double deltaB = -1 * DistanceDifferenceCalculator.computeDeltaH(
            sourceCoordinate, 
            diffractionPointB.coordinate, 
            receiverCoordinate
        );

        CutProfile cutProfileWithoutBottomEdge = newCutProfileWithoutBottomEdge(configuration.getCutProfile());

        CnossosPathExt cnossosPathWithoutBottomEdge = CnossosPathBuilder.buildCnossosPath(
            cutProfileWithoutBottomEdge, 
            configuration.getExactFrequencyArray(), 
            configuration.getGroundAttenuationCoefficient(), 
            configuration.isBodyBarrier()
        );
        double deltaH = cnossosPathWithoutBottomEdge.deltaH;
        
        if (deltaB >= deltaH) {
            CutProfile cutProfileOnlyWithBottomEdge = newCutProfileOnlyWithBottomEdge(configuration.getCutProfile());
            
            CnossosPathExt cnossosPathOnlyWithBottomEdge = CnossosPathBuilder.buildCnossosPath(
                cutProfileOnlyWithBottomEdge, 
                configuration.getExactFrequencyArray(), 
                configuration.getGroundAttenuationCoefficient(), 
                configuration.isBodyBarrier()
            );
            cnossosPath.setCnossosPathBottomRoute(cnossosPathOnlyWithBottomEdge);
            
            CutProfile cutProfileAfterBottomEdge = newCutProfileAfterBottomEdge(configuration.getCutProfile());
            
            CnossosPathExt cnossosPathAfterBottomEdge = CnossosPathBuilder.buildCnossosPath(
                cutProfileAfterBottomEdge, 
                configuration.getExactFrequencyArray(), 
                configuration.getGroundAttenuationCoefficient(), 
                configuration.isBodyBarrier()
            );
            cnossosPath.setCnossosPathTopRoute(cnossosPathAfterBottomEdge);
        } else {
            
            PointPath diffractionPointT = cnossosPath.getPointList().stream()
            .filter(p -> p.type.equals(DIFH)).findFirst().orElse(null);

            CutProfile cutProfileOnlyBeforeTopEdge = newCutProfileBeforeTopEdge(configuration.getCutProfile(), diffractionPointT.coordinate);

            CnossosPathExt cnossosPathOnlyBeforeTopEdge = CnossosPathBuilder.buildCnossosPath(
                cutProfileOnlyBeforeTopEdge, 
                configuration.getExactFrequencyArray(), 
                configuration.getGroundAttenuationCoefficient(), 
                configuration.isBodyBarrier()
            );
            
            cnossosPath.setCnossosPathBottomRoute(cnossosPathOnlyBeforeTopEdge);
            cnossosPath.setCnossosPathTopRoute(cnossosPathWithoutBottomEdge);
        }
    }

    
    private static CutProfile newCutProfileWithoutBottomEdge(CutProfile cutProfile){
        CutProfile newCutProfile = new CutProfile(cutProfile);
        ArrayList<CutPoint> existCutPoints = cutProfile.getCutPoints();
        ArrayList<CutPoint> newCutPoints = new ArrayList<>();
        
        for (CutPoint cp: existCutPoints) {
            if (cp instanceof CutPointBridgeWall && ((CutPointBridgeWall) cp).getWallDirection() != CutPointBridgeWall.WallDirection.UPWARD) {
                continue;
            }
            newCutPoints.add(cp);
        }
        newCutProfile.setCutPoints(newCutPoints);
        return newCutProfile;
    }

    private static CutProfile newCutProfileOnlyWithBottomEdge(CutProfile cutProfile){
        CutProfile newCutProfile = new CutProfile(cutProfile);
        ArrayList<CutPoint> existCutPoints = cutProfile.getCutPoints();
        ArrayList<CutPoint> newCutPoints = new ArrayList<>();
        
        for (CutPoint cp: existCutPoints) {
            if (cp instanceof CutPointWall) {
                continue;
            } else if (cp instanceof CutPointBridgeWall && ((CutPointBridgeWall) cp).getWallDirection() == CutPointBridgeWall.WallDirection.UPWARD) {
                continue;
            }
            newCutPoints.add(cp);
        }
        newCutProfile.setCutPoints(newCutPoints);
        return newCutProfile;
    }

    
    private static CutProfile newCutProfileAfterBottomEdge(CutProfile cutProfile){
        CutProfile newCutProfile = new CutProfile(cutProfile);
        ArrayList<CutPoint> existCutPoints = cutProfile.getCutPoints();
        ArrayList<CutPoint> newCutPoints = new ArrayList<>();
        boolean beforeBottomEdge = true;
        
        for (CutPoint cp: existCutPoints) {
            if (beforeBottomEdge) {
                if (cp instanceof CutPointBridgeWall && ((CutPointBridgeWall) cp).getWallDirection() == CutPointBridgeWall.WallDirection.DOWNWARD) {
                    
                    CutPointSource newSource = new CutPointSource(cutProfile.getSource());
                    newSource.setCoordinate(cp.getCoordinate());
                    newCutPoints.add(newSource);
                    beforeBottomEdge = false;
                }
            } else {
                newCutPoints.add(cp);
            }
        }
        newCutProfile.setCutPoints(newCutPoints);
        return newCutProfile;
    }

    
    private static CutProfile newCutProfileBeforeTopEdge(CutProfile cutProfile, Coordinate topEdgeCoordinate){
        CutProfile newCutProfile = new CutProfile(cutProfile);
        ArrayList<CutPoint> existCutPoints = cutProfile.getCutPoints();
        ArrayList<CutPoint> newCutPoints = new ArrayList<>();
        boolean beforeTopEdge = true;
        
        for (CutPoint cp: existCutPoints) {
            if (beforeTopEdge) {
                if (cp.getCoordinate() != topEdgeCoordinate) {
                    newCutPoints.add(cp);
                } else {
                    CutPointReceiver newReceiver = new CutPointReceiver(cutProfile.getReceiver());
                    newReceiver.setCoordinate(cp.getCoordinate());
                    newCutPoints.add(newReceiver);
                    beforeTopEdge = false;
                }
            }
        }
        newCutProfile.setCutPoints(newCutPoints);
        return newCutProfile;
    }

    private static void setDiffractionPathParametersB(CnossosPathExt cnossosPath, AcousticPathConfiguration configuration){ 
        Coordinate sourceCoordinate = configuration.getSourceCoordinate2D();
        Coordinate receiverCoordinate = configuration.getReceiverCoordinate2D();
        
        // Find the diffraction edge U
        PointPath diffractionPoint = cnossosPath.getPointList().stream()
        .filter(p -> p.type.equals(DIFB)).findFirst().orElse(null);
        if(diffractionPoint == null) {
            throw new IllegalArgumentException("No horizontal diffraction points found in a diffraction path");
        }
        
        Coordinate diffractionPointCoordinate = diffractionPoint.coordinate;

        // neither mirror source and receiver is considered for U-edge diffraction

        boolean hasVirticalEdgeDiffraction = cnossosPath.getPointList().stream()
                .anyMatch(pointPath -> pointPath.type.equals(DIFV));
        
        if (hasVirticalEdgeDiffraction) {
            PointPath firstVDiffractionPoint = cnossosPath.getPointList().stream()
                .filter(p -> p.type.equals(DIFV)).findFirst().orElse(null);
            double directDistance = cnossosPath.getSRSegment().dc;

            // note the orientation is reversed for bottom-edge diffraction
            cnossosPath.deltaH = -1 * DistanceDifferenceCalculator.computeVpathDeltaH(
                sourceCoordinate, 
                firstVDiffractionPoint.coordinate, 
                cnossosPath.e, 
                diffractionPointCoordinate, 
                receiverCoordinate,
                diffractionPointCoordinate,
                directDistance
            );
        } else {
            // note the orientation is reversed for bottom-edge diffraction
            cnossosPath.deltaH = -1 * DistanceDifferenceCalculator.computeDeltaH(
                sourceCoordinate, 
                diffractionPointCoordinate, 
                receiverCoordinate
            );
        }
        
    }
    
    /**
     * Compute and set CNOSSOS diffraction-related delta parameters on the
     * provided {@link CnossosPathExt} when horizontal diffraction points exist.
     *
     * <p>The method locates the first and last horizontal diffraction points
     * in the path, computes cumulative distances, and fills delta values
     * such as deltaH, deltaF, deltaPrimeH, deltaPrimeF, deltaSRPrimeH and
     * deltaSPrimeRF used by the CNOSSOS attenuation model.
     *
     * @param cnossosPath path object to update (must contain segments and points)
     * @param configuration runtime configuration containing geometry and profile
     */
    private static void setDiffractionPathParametersT(CnossosPathExt cnossosPath, AcousticPathConfiguration configuration){ 
        Coordinate sourceCoordinate = configuration.getSourceCoordinate2D();
        Coordinate receiverCoordinate = configuration.getReceiverCoordinate2D();
        
        // Find first and last horizontal diffraction points
        // note: the last diffraction point corresponds to the first diffraction point if there is only one diffracting edge
        PointPath firstDiffractionPoint = cnossosPath.getPointList().stream()
        .filter(p -> p.type.equals(DIFH)).findFirst().orElse(null);
        PointPath lastDiffractionPoint = cnossosPath.getPointList().stream()
        .filter(p -> p.type.equals(DIFH)).reduce((first, second) -> second).orElse(null);
        if(firstDiffractionPoint == null || lastDiffractionPoint == null) {
            throw new IllegalArgumentException("No horizontal diffraction points found in a diffraction path");
        }
        
        Coordinate firstDiffractionPointCoordinate = firstDiffractionPoint.coordinate;
        Coordinate lastDiffractionPointCoordinate = lastDiffractionPoint.coordinate;

        SegmentPath sourceToFirstDiffractionPointPath = cnossosPath.getSegmentList().get(0);
        SegmentPath lastDiffractionPointToReceiverPath = cnossosPath.getSegmentList().get(cnossosPath.getSegmentList().size()-1);


        Coordinate mirrorSourceCoordinate = calculateMirrorPoint(sourceCoordinate, sourceToFirstDiffractionPointPath.sMeanPlane);
        Coordinate mirrorReceiverCoordinate = calculateMirrorPoint(receiverCoordinate, lastDiffractionPointToReceiverPath.rMeanPlane);

        // Compute cumulated distance between the first diffraction and the last diffraction point
        cnossosPath.e = calculateDistanceBetweenDiffractionPoints(cnossosPath.getPointList());

        cnossosPath.deltaSPrimeRH = DistanceDifferenceCalculator.computeDeltaH(
            mirrorSourceCoordinate, 
            firstDiffractionPointCoordinate, 
            cnossosPath.e, 
            lastDiffractionPointCoordinate, 
            receiverCoordinate
        );

        cnossosPath.deltaSPrimeRF = DistanceDifferenceCalculator.computeDeltaF(
            mirrorSourceCoordinate, 
            firstDiffractionPointCoordinate, 
            cnossosPath.e, 
            lastDiffractionPointCoordinate, 
            receiverCoordinate
        );

        cnossosPath.deltaSRPrimeH = DistanceDifferenceCalculator.computeDeltaH(
            sourceCoordinate, 
            firstDiffractionPointCoordinate, 
            cnossosPath.e, 
            lastDiffractionPointCoordinate, 
            mirrorReceiverCoordinate,
            lastDiffractionPointCoordinate
        );
        
        if (sourceCoordinate.x > mirrorReceiverCoordinate.x) {
            cnossosPath.deltaSRPrimeH = -cnossosPath.deltaSRPrimeH;
        }   

        cnossosPath.deltaSRPrimeF = DistanceDifferenceCalculator.computeDeltaF(
            sourceCoordinate, 
            firstDiffractionPointCoordinate, 
            cnossosPath.e, 
            lastDiffractionPointCoordinate, 
            mirrorReceiverCoordinate
        );

        cnossosPath.setSRSegmentDPrime(mirrorSourceCoordinate.distance(mirrorReceiverCoordinate));
        sourceToFirstDiffractionPointPath.dPrime = mirrorSourceCoordinate.distance(firstDiffractionPointCoordinate);
        lastDiffractionPointToReceiverPath.dPrime = lastDiffractionPointCoordinate.distance(mirrorReceiverCoordinate);

        boolean hasVirticalEdgeDiffraction = cnossosPath.getPointList().stream()
                .anyMatch(pointPath -> pointPath.type.equals(DIFV));
        
        if (hasVirticalEdgeDiffraction) {
            PointPath firstVDiffractionPoint = cnossosPath.getPointList().stream()
                .filter(p -> p.type.equals(DIFV)).findFirst().orElse(null);
            double directDistance = cnossosPath.getSRSegment().dc;

            cnossosPath.deltaH = DistanceDifferenceCalculator.computeVpathDeltaH(
                sourceCoordinate, 
                firstVDiffractionPoint.coordinate, 
                cnossosPath.e, 
                lastDiffractionPointCoordinate, 
                receiverCoordinate,
                firstDiffractionPointCoordinate,
                directDistance
            );
        } else {
            cnossosPath.deltaH = DistanceDifferenceCalculator.computeDeltaH(
                sourceCoordinate, 
                firstDiffractionPointCoordinate, 
                cnossosPath.e, 
                lastDiffractionPointCoordinate, 
                receiverCoordinate
            );
        }
        
        cnossosPath.deltaF = DistanceDifferenceCalculator.computeDeltaF(
            sourceCoordinate,
            firstDiffractionPointCoordinate,
            cnossosPath.e,
            lastDiffractionPointCoordinate,
            receiverCoordinate,
            firstDiffractionPointCoordinate,
            new LineSegment(sourceCoordinate, receiverCoordinate)
        );

        cnossosPath.deltaPrimeH = DistanceDifferenceCalculator.computeDeltaH(
            mirrorSourceCoordinate,
            firstDiffractionPointCoordinate,
            cnossosPath.e,
            lastDiffractionPointCoordinate,
            mirrorReceiverCoordinate
        );

        cnossosPath.deltaPrimeF = DistanceDifferenceCalculator.computeDeltaF(
            mirrorSourceCoordinate,
            firstDiffractionPointCoordinate,
            cnossosPath.e,
            lastDiffractionPointCoordinate,
            mirrorReceiverCoordinate
        );
    }

    /**
     * Test all interior cut-profile points for Rayleigh-type diffraction
     * contribution and append qualifying obstacle paths/points to
     * {@code cnossosPath}.
     *
     * <p>The screening performs a cheap per-frequency test (fast deltaH
     * threshold) followed by a more detailed geometric check using mirror
     * image points and delta-prime comparisons. When at least one qualifying
     * obstacle is found the method appends the two-segment decomposition
     * (source->obstacle, obstacle->receiver) and a DIFH_RCRIT point and
     * returns {@code true}.
     *
     * @param cnossosPath path object that will be mutated to include new segments/points
     * @param pathConfiguration runtime configuration containing profile and coordinates
     * @return {@code true} if one or more Rayleigh obstacle points were inserted
     */
    private static boolean setRayleighDiffractionEffects(CnossosPathExt cnossosPath, AcousticPathConfiguration pathConfiguration) {
        
        Coordinate sourceCoordinate = pathConfiguration.getSourceCoordinate2D();
        Coordinate receiverCoordinate = pathConfiguration.getReceiverCoordinate2D();

        List<SegmentPath> segmentList = new ArrayList<>();
        List<PointPath> pointList = new ArrayList<>();
        
        // Iterate over interior cut-profile points (exclude source and receiver)
        for (int cutPointIndex = 1; cutPointIndex < pathConfiguration.getCutProfilePoints().size() - 1; cutPointIndex++) {

            int cutPointExpandedIndex = pathConfiguration.getCutPointExpandedIndices().get(cutPointIndex);
            Coordinate targetCoordinate = pathConfiguration.getElevationProfile2D()[cutPointExpandedIndex];
            
            // Calculate basic path difference deltaH used for the fast frequency screen
            cnossosPath.deltaH = DistanceDifferenceCalculator.computeDeltaH(sourceCoordinate, targetCoordinate, receiverCoordinate);
            
            // First criterion: frequency-based screening (cheap test per-frequency)
            if (!passesFrequencyScreening(cnossosPath.deltaH, pathConfiguration.getExactFrequencyArray())) {
                continue; // Skip this point if it doesn't meet frequency criteria
            }
            
            SegmentPath sourceToObstaclePath = createSourceToObstaclePath(cutPointExpandedIndex, targetCoordinate, pathConfiguration);
            SegmentPath obstacleToReceiverPath = createObstacleToReceiverPath(cutPointExpandedIndex, targetCoordinate, pathConfiguration);

            // Compute mirror (image) points of the source/receiver with respect
            // to the local mean planes of the two sub-segments. These mirror
            // points are used in the detailed geometric screening below.
            Coordinate mirrorSourceCoordinate = calculateMirrorPoint(sourceCoordinate, sourceToObstaclePath.sMeanPlane);
            Coordinate mirrorReceiverCoordinate = calculateMirrorPoint(receiverCoordinate, obstacleToReceiverPath.rMeanPlane);
            
            // Calculate distances for mirror path
            cnossosPath.setSRSegmentDPrime(mirrorSourceCoordinate.distance(mirrorReceiverCoordinate));
            sourceToObstaclePath.dPrime = mirrorSourceCoordinate.distance(targetCoordinate);
            obstacleToReceiverPath.dPrime = targetCoordinate.distance(mirrorReceiverCoordinate);
            
            cnossosPath.deltaPrimeH = DistanceDifferenceCalculator.computeDeltaH(mirrorSourceCoordinate, targetCoordinate, mirrorReceiverCoordinate);
            
            // Second criterion: detailed diffraction screening using the mirror-path
            // geometry and delta-prime comparisons per-frequency.
            if (!passesDetailedScreening(cnossosPath.deltaH, cnossosPath.deltaPrimeH, pathConfiguration.getExactFrequencyArray())) {
                continue; // Skip this point if it doesn't meet detailed criteria
            }

            cnossosPath.deltaSPrimeRH = DistanceDifferenceCalculator.computeDeltaH(mirrorSourceCoordinate, targetCoordinate, receiverCoordinate);
            cnossosPath.deltaSRPrimeH = DistanceDifferenceCalculator.computeDeltaH(sourceCoordinate, targetCoordinate, mirrorReceiverCoordinate);

            cnossosPath.deltaF = DistanceDifferenceCalculator.computeDeltaF(sourceCoordinate, targetCoordinate, receiverCoordinate);
            cnossosPath.deltaPrimeF = DistanceDifferenceCalculator.computeDeltaF(mirrorSourceCoordinate, targetCoordinate, mirrorReceiverCoordinate);
            
            // Compute and set weighted ground absorption for the two sub-paths.
            sourceToObstaclePath.setGpath(
                pathConfiguration.getCutProfile().calculateWeightedGroundAbsorption(
                    0, cutPointIndex,
                    Scene.DEFAULT_G_BUILDING
                ),
                pathConfiguration.getCutProfilePoints().get(0).getGroundCoefficient()
            );

            obstacleToReceiverPath.setGpath(
                pathConfiguration.getCutProfile().calculateWeightedGroundAbsorption(
                    cutPointIndex, 
                    pathConfiguration.getCutProfilePoints().size() - 1,
                    Scene.DEFAULT_G_BUILDING
                ),
                pathConfiguration.getCutProfilePoints().get(pathConfiguration.getCutProfilePoints().size() - 1).getGroundCoefficient()
            );
        

            // Collect the two path segments and a corresponding diffraction point
            // to be appended to the main CnossosPathExt if at least one qualifying
            // obstacle is found.
            segmentList.add(sourceToObstaclePath);
            segmentList.add(obstacleToReceiverPath);
            pointList.add(
                new PointPath(
                    pathConfiguration.getElevationProfile2D()[cutPointExpandedIndex],
                    pathConfiguration.getElevationProfile2D()[cutPointExpandedIndex].z,
                    new ArrayList<>(), 
                    DIFH_RCRIT
                )
            );
        }
        
        if (segmentList.isEmpty()) {
            return false;
        } else {
            cnossosPath.addSegmentAll(segmentList);
            cnossosPath.addPointAll(1, pointList);
            return true;
        }
    }
    
    
    
    /**
     * Perform a lightweight per-frequency screening test for a candidate
     * diffraction point.
     *
     * @param deltaH path-length difference used as screening metric
     * @param exactFrequencyArray frequencies to test
     * @return {@code true} if any frequency satisfies the cheap screening rule
     */
    private static boolean passesFrequencyScreening(double deltaH, List<Double> exactFrequencyArray) {
        for (double frequency : exactFrequencyArray) {
            if (deltaH > -(SOUND_SPEED / frequency) / 20) {
                return true;
            }
        }
        return false;
    }
    
    
    /**
     * Create a {@link SegmentPath} representing the sub-path from source to
     * the obstacle coordinate (inclusive). The method computes the mean
     * plane coefficients for the sub-profile and delegates to
     * {@link CnossosSegmentComputer#createSegmentPath}.
     *
     * @param cutPointExpandedIndex index into the elevation profile array
     * @param obstacleCoordinate target obstacle coordinate
     * @param pathConfiguration runtime configuration with elevation profile
     * @return a configured {@link SegmentPath}
     */
    private static SegmentPath createSourceToObstaclePath(int cutPointExpandedIndex, Coordinate obstacleCoordinate, AcousticPathConfiguration pathConfiguration) {
        Coordinate[] sourceToObstacleCoordinates = Arrays.copyOfRange(
            pathConfiguration.getElevationProfile2D(), 
            0, 
            cutPointExpandedIndex + 1
        );
        // Coordinate sourceCoordinate = pathConfiguration.getCutPointCoordinates2D().get(0);
        double[] meanPlaneCoeffs = JTSUtility.getMeanPlaneCoefficients(sourceToObstacleCoordinates);

        return CnossosSegmentComputer.createSegmentPath(
            pathConfiguration.getSourceCoordinate2D(), 
            obstacleCoordinate, 
            meanPlaneCoeffs
        );
    }
    
    /**
     * Create a {@link SegmentPath} representing the sub-path from the
     * obstacle coordinate to the receiver. Computes the local mean plane
     * coefficients for the sub-profile and delegates to
     * {@link CnossosSegmentComputer#createSegmentPath}.
     *
     * @param cutPointExpandedIndex index into the elevation profile array
     * @param obstacleCoordinate obstacle coordinate
     * @param pathConfiguration runtime configuration
     * @return a configured {@link SegmentPath}
     */
    private static SegmentPath createObstacleToReceiverPath(int cutPointExpandedIndex, Coordinate obstacleCoordinate, AcousticPathConfiguration pathConfiguration) {
        Coordinate[] obtacleToReceiverCoordinates = Arrays.copyOfRange(pathConfiguration.getElevationProfile2D(), cutPointExpandedIndex, pathConfiguration.getElevationProfile2D().length);
        
        double[] meanPlaneCoeffs = JTSUtility.getMeanPlaneCoefficients(obtacleToReceiverCoordinates);
        
        return CnossosSegmentComputer.createSegmentPath(obstacleCoordinate, pathConfiguration.getReceiverCoordinate2D(), meanPlaneCoeffs);
    }
    
    /**
     * Calculate the 2D mirror (image) point of {@code original} with respect
     * to a local plane point {@code planePoint}. This is a simple reflection
     * across the plane point used in mirror-path geometric tests.
     *
     * @param original original coordinate (source or receiver)
     * @param planePoint reference point on the local mean plane
     * @return mirrored coordinate
     */
    private static Coordinate calculateMirrorPoint(Coordinate original, Coordinate planePoint) {
        return new Coordinate(
            original.x + (planePoint.x - original.x) * 2,
            original.y + (planePoint.y - original.y) * 2
        );
    }
    
    /**
     * Perform the detailed screening using mirror-path geometry and
     * delta-prime comparisons across frequencies.
     *
     * @param deltaH primary path-length difference
     * @param deltaPrimeH mirror-path delta used for detailed checks
     * @param exactFrequencyArray frequency bands used in the checks
     * @return {@code true} when the point passes detailed screening for any frequency
     */
    private static boolean passesDetailedScreening(double deltaH, double deltaPrimeH, List<Double> exactFrequencyArray) {
        for (double frequency : exactFrequencyArray) {
            if (deltaH > (SOUND_SPEED / frequency) / 4 - deltaPrimeH) {
                return true;
            }
        }
        return false;
    }

    private static double calculateDistanceBetweenDiffractionPoints(List<PointPath> pointPathList){
        double result = 0;
        List<PointPath> pointsWithoutREFL = pointPathList.stream()
                .filter(pointPath -> pointPath.type != REFL)
                .filter(pointPath -> pointPath.type != DIFB)
                .collect(Collectors.toList());

        for(int i = 1; i < pointsWithoutREFL.size() - 2; i++) {
            result += pointsWithoutREFL.get(i).coordinate.distance(pointsWithoutREFL.get(i+1).coordinate);
        }
        return result;
    }
    
}
