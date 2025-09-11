package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointVEdgeDiffraction;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.noise_planet.noisemodelling.propagation.cnossos.PointPath.POINT_TYPE.*;
import static java.lang.Math.*;


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
 * <p>All methods operate on the provided {@link CnossosPath} and
 * {@link AcousticPathConfiguration} objects and mutate the {@link CnossosPath}
 * instance in-place (they do not create or return new CnossosPath objects).
 */
public class CnossosPathProcessor {
    private static final double SOUND_SPEED = 340.0;


    public static CnossosPath createCnossosPathFromPath(Path path, AcousticPathConfiguration configuration) {

        double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(configuration.getElevationProfile2D());
        
        SegmentPath sourceToReceiverPath = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
            configuration.getCutPointCoordinates2D(), 
            meanPlane, 
            configuration.getCutProfile().calculateWeightedGroundAbsorption(), 
            configuration.getCutProfile().getGroundAbsorptionAtSource()
        );
        sourceToReceiverPath.setElevationProfile2D(configuration.getElevationProfile2D());
        sourceToReceiverPath.setDirectRayDistance(
            CGAlgorithms3D.distance(
                configuration.getCutProfile().getReceiver().getCoordinate(),
                configuration.getCutProfile().getSource().getCoordinate()
            )
        );
        
        CnossosPath cnossosPath = new CnossosPath(configuration.getCutProfile());
        cnossosPath.setFavorable(true);
        cnossosPath.setSRSegment(sourceToReceiverPath);        
        cnossosPath.setPointList(path.getPointList());
        cnossosPath.setSegmentList(path.getSegmentList());
        cnossosPath.setRaySourceReceiverDirectivity(path.getRaySourceReceiverDirectivity());
        cnossosPath.init(configuration.getExactFrequencyArray().size());
        setCnossosPathParameters(cnossosPath, configuration);

        return cnossosPath;
    }
    
    private static void setCnossosPathParameters(CnossosPath cnossosPath, AcousticPathConfiguration configuration) {

        boolean hasDiffraction = cnossosPath.getPointList().stream()
                .anyMatch(p -> p.type.equals(DIFH));

        if (!hasDiffraction) {
            setDirectPathParameters(cnossosPath, configuration);
            return;
        }
        
        // Compute final path parameters and delta values for diffraction
        setDiffractionPathParameters(cnossosPath, configuration);

        // return pathConfiguration.getPathParameters();
        return;
    }

    /**
     * Populate the supplied {@link CnossosPath} for the direct-propagation
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
     *       appended to {@code cnossosPathInput} and the method returns true
     *       via the side-effects.</li>
     *   <li>If no Rayleigh obstacle is added, deltaH/deltaF for the straight
     *       source->receiver path are computed and stored in
     *       {@code cnossosPathInput}.</li>
     * </ul>
     *
     * <p>This method mutates the provided {@link CnossosPath} instance and
     * does not return a new object.
     *
     * @param cnossosPathInput path data object to populate (mutated)
     * @param pathConfiguration configuration object containing cut-profile and coordinates
     */
    private static void setDirectPathParameters(CnossosPath cnossosPathInput, AcousticPathConfiguration pathConfiguration) {
    
        // CnossosPath cnossosPathOutput = new CnossosPath(cnossosPathInput);
        // If any vertical-edge diffraction point exists in the cut profile,
        // the path is already treated as a diffraction path (not a pure
        // direct path) so we must not attempt Rayleigh screening.
        boolean hasVEdgeDiffractionPoint = pathConfiguration.getCutProfilePoints().stream()
            .anyMatch(cutPoint -> cutPoint instanceof CutPointVEdgeDiffraction);

        
        boolean hasRayleighDiffraction = false;
        // Only attempt Rayleigh-type diffraction screening when the path is
        // currently direct (no explicit vertical-edge diffraction points).
        if (!hasVEdgeDiffractionPoint) {
            hasRayleighDiffraction = setRayleighDiffractionEffects(cnossosPathInput, pathConfiguration);
        }
        
        if(!hasRayleighDiffraction) {
            // Ensure there is at least the straight source-receiver segment
            if (cnossosPathInput.getSegmentList().isEmpty()) {
            cnossosPathInput.addSegment(cnossosPathInput.getSRSegment());
            }

            // Compute the cumulative distance e between the first and last
            // non-reflection points. This is used to calculate deltaH/deltaF
            // for the straight-path case.
            cnossosPathInput.e = 0;
            List<PointPath> diffractionPoints = cnossosPathInput.getPointList().stream()
                .filter(pointPath -> pointPath.type != REFL).collect(Collectors.toList());

            for (int idPoint = 1; idPoint < diffractionPoints.size() - 2; idPoint++) {
                Coordinate diffractionPointCoordinate = diffractionPoints.get(idPoint).coordinate;
                Coordinate nextDiffractionPointCoordinate = diffractionPoints.get(idPoint + 1).coordinate;
                cnossosPathInput.e += diffractionPointCoordinate.distance(nextDiffractionPointCoordinate);
            }

            // If there are no vertical-edge diffractions, use the straight-line
            // distance d; otherwise use the corrected distance dc.
            long difVPointCount = cnossosPathInput.getPointList().stream().
                filter(pointPath -> pointPath.type.equals(DIFV)).count();

            double distance = difVPointCount == 0 ? cnossosPathInput.getSRSegment().d : cnossosPathInput.getSRSegment().dc;
            cnossosPathInput.deltaH = cnossosPathInput.getSegmentList().get(0).d
                + cnossosPathInput.e
                + cnossosPathInput.getSegmentList().get(cnossosPathInput.getSegmentList().size() - 1).d
                - distance;
            cnossosPathInput.deltaF = cnossosPathInput.deltaH;
        }
        return;

        // return cnossosPathInput;  // Direct propagation processing completed
    }

    
    /**
     * Compute and set CNOSSOS diffraction-related delta parameters on the
     * provided {@link CnossosPath} when horizontal diffraction points exist.
     *
     * <p>The method locates the first and last horizontal diffraction points
     * in the path, computes cumulative distances, and fills delta values
     * such as deltaH, deltaF, deltaPrimeH, deltaPrimeF, deltaSRPrimeH and
     * deltaSPrimeRF used by the CNOSSOS attenuation model.
     *
     * @param cnossosPath path object to update (must contain segments and points)
     * @param configuration runtime configuration containing geometry and profile
     */
    private static void setDiffractionPathParameters(CnossosPath cnossosPath, AcousticPathConfiguration configuration){ 
                                                // List<PointPath> points, 
                                                // List<SegmentPath> segments, 
                                                // CnossosPath cnossosPath) {
        Coordinate sourceCoordinate = configuration.getSourceCoordinate2D();
        Coordinate receiverCoordinate = configuration.getReceiverCoordinate2D();
        SegmentPath srPath = cnossosPath.getSRSegment();
        List<SegmentPath> segments = cnossosPath.getSegmentList();
        List<PointPath> points = cnossosPath.getPointList();

        // Find first and last horizontal diffraction points        
        
        PointPath firstDiffractionPoint = points.stream()
                .filter(p -> p.type.equals(DIFH)).findFirst().orElse(null);
        PointPath lastDiffractionPoint = points.stream()
                .filter(p -> p.type.equals(DIFH)).reduce((first, second) -> second).orElse(null);
        if(firstDiffractionPoint == null || lastDiffractionPoint == null) {
            return; // No horizontal diffraction points to process
        }

        // Coordinate c0 = firstDiffractionPoint.coordinate;
        // PointPath pn = points.stream().filter(p -> p.type.equals(DIFH)).reduce((first, second) -> second).orElse(null);
        // if(pn == null) {
        //     return; // Should not happen if p0 exists
        // }
        // Coordinate cn = pn.coordinate;

        Coordinate firstDiffractionPointCoordinate = firstDiffractionPoint.coordinate;
        Coordinate lastDiffractionPointCoordinate = lastDiffractionPoint.coordinate;

        SegmentPath sourceToFirstDiffractionPointPath = segments.get(0);
        SegmentPath lastDiffractionPointToReceiverPath = segments.get(segments.size()-1);

        double sourceToFirstDiffractionPointDistance = sourceToFirstDiffractionPointPath.d;
        double lastDiffractionPointToReceiverDistance = lastDiffractionPointToReceiverPath.d;
        LineSegment sourceToReceiverLineSegment = new LineSegment(sourceCoordinate, receiverCoordinate);

        LineSegment sPrimeR = new LineSegment(sourceToFirstDiffractionPointPath.sPrime, receiverCoordinate);
        double dSPrimeR = sourceToFirstDiffractionPointPath.sPrime.distance(receiverCoordinate);
        double dSPrimeO = sourceToFirstDiffractionPointPath.sPrime.distance(firstDiffractionPointCoordinate);

        // Compute cumulated distance between the first diffraction and the last diffraction point
        cnossosPath.e = 0;
        List<PointPath> pointsWithoutREFL = points.stream()
                .filter(pointPath -> pointPath.type != REFL)
                .collect(Collectors.toList());

        for(int idPoint = 1; idPoint < pointsWithoutREFL.size() - 2; idPoint++) {
            cnossosPath.e += pointsWithoutREFL.get(idPoint).coordinate.distance(pointsWithoutREFL.get(idPoint+1).coordinate);
        }

        cnossosPath.deltaSPrimeRH = sPrimeR.orientationIndex(firstDiffractionPointCoordinate)*(dSPrimeO + cnossosPath.e + lastDiffractionPointToReceiverDistance - dSPrimeR);
        cnossosPath.deltaSPrimeRF = convertToCurvedPath(dSPrimeO, dSPrimeR) + convertToCurvedPath(cnossosPath.e, dSPrimeR) + convertToCurvedPath(lastDiffractionPointToReceiverDistance, dSPrimeR) - convertToCurvedPath(dSPrimeR, dSPrimeR);

        LineSegment sRPrime = new LineSegment(sourceCoordinate, lastDiffractionPointToReceiverPath.rPrime);
        double dSRPrime = sourceCoordinate.distance(lastDiffractionPointToReceiverPath.rPrime);
        double dORPrime = lastDiffractionPointCoordinate.distance(lastDiffractionPointToReceiverPath.rPrime);
        cnossosPath.deltaSRPrimeH = (sourceCoordinate.x>lastDiffractionPointToReceiverPath.rPrime.x?-1:1)*sRPrime.orientationIndex(lastDiffractionPointCoordinate)*(sourceToFirstDiffractionPointDistance + cnossosPath.e + dORPrime - dSRPrime);
        cnossosPath.deltaSRPrimeF = convertToCurvedPath(sourceToFirstDiffractionPointDistance, dSRPrime) + convertToCurvedPath(cnossosPath.e, dSRPrime) + convertToCurvedPath(dORPrime, dSRPrime) - convertToCurvedPath(dSRPrime, dSRPrime);

        Coordinate srcPrime = new Coordinate(sourceCoordinate.x + (sourceToFirstDiffractionPointPath.sMeanPlane.x - sourceCoordinate.x) * 2, sourceCoordinate.y + (sourceToFirstDiffractionPointPath.sMeanPlane.y - sourceCoordinate.y) * 2);
        Coordinate rcvPrime = new Coordinate(receiverCoordinate.x + (lastDiffractionPointToReceiverPath.rMeanPlane.x - receiverCoordinate.x) * 2, receiverCoordinate.y + (lastDiffractionPointToReceiverPath.rMeanPlane.y - receiverCoordinate.y) * 2);

        LineSegment dSPrimeRPrime = new LineSegment(srcPrime, rcvPrime);
        srPath.dPrime = srcPrime.distance(rcvPrime);
        sourceToFirstDiffractionPointPath.dPrime = srcPrime.distance(firstDiffractionPointCoordinate);
        lastDiffractionPointToReceiverPath.dPrime = lastDiffractionPointCoordinate.distance(rcvPrime);

        long difVPointCount = cnossosPath.getPointList().stream().
                filter(pointPath -> pointPath.type.equals(DIFV)).count();
        double distance = difVPointCount == 0 ? cnossosPath.getSRSegment().d : cnossosPath.getSRSegment().dc;
        cnossosPath.deltaH = sourceToReceiverLineSegment.orientationIndex(firstDiffractionPointCoordinate) * (sourceToFirstDiffractionPointDistance + cnossosPath.e + lastDiffractionPointToReceiverDistance - distance);
        if (sourceToReceiverLineSegment.orientationIndex(firstDiffractionPointCoordinate) == 1) {
            cnossosPath.deltaF = convertToCurvedPath(sourceToFirstDiffractionPointPath.d, srPath.d) + convertToCurvedPath(cnossosPath.e, srPath.d) + convertToCurvedPath(lastDiffractionPointToReceiverPath.d, srPath.d) - convertToCurvedPath(srPath.d, srPath.d);
        } else {
            Coordinate pA = sourceToReceiverLineSegment.pointAlong((firstDiffractionPointCoordinate.x - srcPrime.x) / (rcvPrime.x - srcPrime.x));
            cnossosPath.deltaF = 2 * convertToCurvedPath(srcPrime.distance(pA), srPath.dPrime) + 2 * convertToCurvedPath(pA.distance(rcvPrime), srPath.dPrime) - convertToCurvedPath(sourceToFirstDiffractionPointPath.dPrime, srPath.dPrime) - convertToCurvedPath(lastDiffractionPointToReceiverPath.dPrime, srPath.dPrime) - convertToCurvedPath(srPath.dPrime, srPath.dPrime);
        }

        cnossosPath.deltaPrimeH = dSPrimeRPrime.orientationIndex(firstDiffractionPointCoordinate) * (sourceToFirstDiffractionPointPath.dPrime + cnossosPath.e + lastDiffractionPointToReceiverPath.dPrime - srPath.dPrime);

        cnossosPath.deltaPrimeH = dSPrimeRPrime.orientationIndex(firstDiffractionPointCoordinate) * (sourceToFirstDiffractionPointPath.dPrime + lastDiffractionPointToReceiverPath.dPrime - srPath.dPrime);
        if(dSPrimeRPrime.orientationIndex(firstDiffractionPointCoordinate) == 1) {
            cnossosPath.deltaPrimeF = convertToCurvedPath(sourceToFirstDiffractionPointPath.dPrime, srPath.dPrime) + convertToCurvedPath(lastDiffractionPointToReceiverPath.dPrime, srPath.dPrime) - convertToCurvedPath(srPath.dPrime, srPath.dPrime);
        } else {
            Coordinate pA = dSPrimeRPrime.pointAlong((firstDiffractionPointCoordinate.x-srcPrime.x)/(rcvPrime.x-srcPrime.x));
            cnossosPath.deltaPrimeF =2*convertToCurvedPath(srcPrime.distance(pA), srPath.dPrime) + 2*convertToCurvedPath(pA.distance(rcvPrime), srPath.dPrime) - convertToCurvedPath(sourceToFirstDiffractionPointPath.dPrime, srPath.dPrime) - convertToCurvedPath(lastDiffractionPointToReceiverPath.dPrime, srPath.d) - convertToCurvedPath(srPath.dPrime, srPath.dPrime);
        }
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
    private static boolean setRayleighDiffractionEffects(CnossosPath cnossosPath, AcousticPathConfiguration pathConfiguration) {
        
        LineSegment sourceToReceiverLineSegment = new LineSegment(
            pathConfiguration.getSourceCoordinate2D(), 
            pathConfiguration.getReceiverCoordinate2D()
        );

        List<SegmentPath> segmentList = new ArrayList<>();
        List<PointPath> pointList = new ArrayList<>();
        
        // Iterate over interior cut-profile points (exclude source and receiver)
        for (int cutPointIndex = 1; cutPointIndex < pathConfiguration.getCutProfilePoints().size() - 1; cutPointIndex++) {

            int cutPointExpandedIndex = pathConfiguration.getCutPointExpandedIndices().get(cutPointIndex);
            Coordinate targetCoordinate = pathConfiguration.getElevationProfile2D()[cutPointExpandedIndex];
            
            // Calculate basic path difference deltaH used for the fast frequency screen
            cnossosPath.deltaH = calculatePathDistanceDifferenceH(
                sourceToReceiverLineSegment, 
                targetCoordinate
            );
            
            // First criterion: frequency-based screening (cheap test per-frequency)
            if (!passesFrequencyScreening(cnossosPath.deltaH, pathConfiguration.getExactFrequencyArray())) {
                continue; // Skip this point if it doesn't meet frequency criteria
            }
            
            SegmentPath sourceToObstaclePath = createSourceToObstaclePath(cutPointExpandedIndex, targetCoordinate, pathConfiguration);
            SegmentPath obstacleToReceiverPath = createObstacleToReceiverPath(cutPointExpandedIndex, targetCoordinate, pathConfiguration);

            // Compute mirror (image) points of the source/receiver with respect
            // to the local mean planes of the two sub-segments. These mirror
            // points are used in the detailed geometric screening below.
            Coordinate mirrorSourceCoordinate2D = calculateMirrorPoint(
                pathConfiguration.getSourceCoordinate2D(), 
                sourceToObstaclePath.sMeanPlane
            );
            Coordinate mirrorReceiverCoordinate2D = calculateMirrorPoint(
                pathConfiguration.getReceiverCoordinate2D(), 
                obstacleToReceiverPath.rMeanPlane
            );
            
            // Calculate distances for mirror path
            LineSegment mirrorSourceToMirrorReceiverLineSegment = new LineSegment(
                mirrorSourceCoordinate2D, 
                mirrorReceiverCoordinate2D
            );
            cnossosPath.getSRSegment().dPrime = mirrorSourceCoordinate2D.distance(mirrorReceiverCoordinate2D);
            sourceToObstaclePath.dPrime = mirrorSourceCoordinate2D.distance(targetCoordinate);
            obstacleToReceiverPath.dPrime = targetCoordinate.distance(mirrorReceiverCoordinate2D);
            
            cnossosPath.deltaPrimeH = calculatePathDistanceDifferenceH(
                mirrorSourceToMirrorReceiverLineSegment,
                targetCoordinate
            );
            
            // Second criterion: detailed diffraction screening using the mirror-path
            // geometry and delta-prime comparisons per-frequency.
            if (!passesDetailedScreening(cnossosPath.deltaH, cnossosPath.deltaPrimeH, pathConfiguration.getExactFrequencyArray())) {
                continue; // Skip this point if it doesn't meet detailed criteria
            }

            
            LineSegment mirrorSourceToReceiverLineSegment = new LineSegment(
                mirrorSourceCoordinate2D, 
                pathConfiguration.getReceiverCoordinate2D()
            );
            cnossosPath.deltaSPrimeRH = calculatePathDistanceDifferenceH(mirrorSourceToReceiverLineSegment, targetCoordinate);

            LineSegment sourceToMirrorReceiverLineSegment = new LineSegment(
                pathConfiguration.getSourceCoordinate2D(), 
                mirrorReceiverCoordinate2D
            );            
            cnossosPath.deltaSRPrimeH = calculatePathDistanceDifferenceH(sourceToMirrorReceiverLineSegment, targetCoordinate);

            cnossosPath.deltaF = calculatePathDistanceDifferenceF(sourceToReceiverLineSegment, targetCoordinate);
            cnossosPath.deltaPrimeF = calculatePathDistanceDifferenceF(mirrorSourceToMirrorReceiverLineSegment, targetCoordinate);
            
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
            // to be appended to the main CnossosPath if at least one qualifying
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
        if (!segmentList.isEmpty()) {
            cnossosPath.addSegmentAll(segmentList);
            cnossosPath.addPointAll(1, pointList);
            return true;
        }
        return false;
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
        // Coordinate receiverCoordinate = pathConfiguration.getCutPointCoordinates2D().get(pathConfiguration.getCutPointCoordinates2D().size() - 1);
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
    

    
    /**
     * Calculate the basic path-length difference (delta H) used in homogeneous
     * diffraction screening: orientation * (s->o + o->r - s->r).
     *
     * @param sourceToReceiverLineSegment straight-line segment between source and receiver
     * @param diffractionPointCoordinate candidate diffraction point coordinate
     * @return signed path-length difference used as screening metric
     */
    private static double calculatePathDistanceDifferenceH(LineSegment sourceToReceiverLineSegment, Coordinate diffractionPointCoordinate) {
        double orientationIndex = sourceToReceiverLineSegment.orientationIndex(diffractionPointCoordinate);
        double sourceToObstacleDistance = sourceToReceiverLineSegment.p0.distance(diffractionPointCoordinate);
        double obstacleToReceiverDistance = diffractionPointCoordinate.distance(sourceToReceiverLineSegment.p1);
        double directDistance = sourceToReceiverLineSegment.getLength();
        return orientationIndex * (sourceToObstacleDistance + obstacleToReceiverDistance - directDistance);
    }

    
    
    /**
     * Calculate the curved-path difference (delta F) used in frequency-based
     * screening. The method transforms linear distances into the curved-path
     * metric and returns the resulting delta value.
     *
     * @param sourceToReceiverLineSegment straight-line segment between source and receiver
     * @param diffractionPointCoordinate candidate diffraction point coordinate
     * @return curved-path difference used in frequency-based screening
     */
    private static double calculatePathDistanceDifferenceF(LineSegment sourceToReceiverLineSegment, Coordinate diffractionPointCoordinate) {

        double sourceToObstacleDistance = sourceToReceiverLineSegment.p0.distance(diffractionPointCoordinate);
        double obstacleToReceiverDistance = diffractionPointCoordinate.distance(sourceToReceiverLineSegment.p1);

        double sourceToObstacleCurvedDistance = convertToCurvedPath(sourceToObstacleDistance, sourceToReceiverLineSegment.getLength());
        double obstacleToReceiverCurvedDistance = convertToCurvedPath(obstacleToReceiverDistance, sourceToReceiverLineSegment.getLength());
        double directCurvedDistance = convertToCurvedPath(sourceToReceiverLineSegment.getLength(), sourceToReceiverLineSegment.getLength());

        if (sourceToReceiverLineSegment.orientationIndex(diffractionPointCoordinate) == 1) {

            return sourceToObstacleCurvedDistance + obstacleToReceiverCurvedDistance - directCurvedDistance;

        } else {
            double sourceToObstacleDistanceX = diffractionPointCoordinate.x - sourceToReceiverLineSegment.p0.x;
            double sourceToReceiverDistanceX = sourceToReceiverLineSegment.p1.x - sourceToReceiverLineSegment.p0.x;
            Coordinate quasiObstacleCoordinate = sourceToReceiverLineSegment.pointAlong(sourceToObstacleDistanceX / sourceToReceiverDistanceX);

            double sourceToQuasiObstacleCurvedDistance = convertToCurvedPath(sourceToReceiverLineSegment.p0.distance(quasiObstacleCoordinate), sourceToReceiverLineSegment.getLength());
            double quasiObstacleToReceiverCurvedDistance = convertToCurvedPath(quasiObstacleCoordinate.distance(sourceToReceiverLineSegment.p1), sourceToReceiverLineSegment.getLength());


            return 2 * sourceToQuasiObstacleCurvedDistance + 
                                  2 * quasiObstacleToReceiverCurvedDistance - 
                                  sourceToObstacleCurvedDistance - obstacleToReceiverCurvedDistance - directCurvedDistance;
        }
    }
    

    /**
     * Convert a linear distance to a curved-path metric used by the model.
     *
     * <p>Implementation follows equations used by CNOSSOS (Eq.2.5.24 and
     * Eq.2.5.25 in the original specification). The method applies a lower
     * bound to the denominator to avoid numerical instability for small d.
     *
     * @param mn linear sub-distance (mn)
     * @param d  reference distance (d)
     * @return curved-path equivalent for the given parameters
     */
    private static double convertToCurvedPath(double mn, double d){
        return 2*max(1000, 8*d)* asin(mn/(2*max(1000, 8*d)));
    }
}
