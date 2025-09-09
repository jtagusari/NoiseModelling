package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointVEdgeDiffraction;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.noise_planet.noisemodelling.propagation.cnossos.PointPath.POINT_TYPE.*;
import static java.lang.Math.*;
import static java.lang.Math.max;

/**
 * Handles direct propagation processing for acoustic paths.
 * This class contains the exact original logic from processDirectPropagationInstance.
 */                              
/**
 * Process direct propagation scenario.
 * This method contains the exact original logic from processDirectPropagationInstance.
 * 
 * @param cutProfile The cut profile containing source and receiver
 * @param pathParameters The path parameters to update
 * @param srPath The source-receiver segment
 * @param segments The segments list to update
 * @param points The points list to update
 * @param pts2D 2D coordinates of cut points
 * @param pts2DGround Array of ground coordinates
 * @param cut2DGroundIndex Ground index mapping
 * @param firstPts2D First point in 2D coordinates
 * @param lastPts2D Last point in 2D coordinates
 * @return true if direct propagation was processed successfully
 */
public class DirectPropagationProcessor {
    private List<Double> exactFrequencyArray;
    public DirectPropagationProcessor(List<Double> exactFrequencyArray) {
        this.exactFrequencyArray = exactFrequencyArray;
    }

    public static boolean processDirectPropagation(AcousticPathConfiguration pathConfiguration,
                                                  SegmentPath srPath,
                                                  List<SegmentPath> segments,
                                                  List<PointPath> points) {
        
        CutProfile cutProfile = pathConfiguration.getCutProfile();
        CnossosPath pathParameters = pathConfiguration.getPathParameters();
        List<Coordinate> pts2D = pathConfiguration.getCutPointCoordinates2D();
        Coordinate[] pts2DGround = pathConfiguration.getElevationProfile2D();
        List<Integer> cut2DGroundIndex = pathConfiguration.getCut2DGroundIndex();
        List<Double> exactFrequencyArray = pathConfiguration.getExactFrequencyArray();
        
        Coordinate firstPts2D = pts2D.get(0);
        Coordinate lastPts2D = pts2D.get(pts2D.size() - 1);
    
        // Direct propagation (no diffraction over obstructing objects)
        boolean horizontalPlaneDiffraction = cutProfile.getCutPoints().stream()
                .anyMatch(cutPoint -> cutPoint instanceof CutPointVEdgeDiffraction);
        
        List<SegmentPath> rayleighSegments = new ArrayList<>();
        List<PointPath> rayleighPoints = new ArrayList<>();
        
        // do not check for rayleigh if the path is not direct between R and S
        if(!horizontalPlaneDiffraction) {
            // Check for Rayleigh criterion for segments computation
            LineSegment dSR = new LineSegment(firstPts2D, lastPts2D);
            // Look for diffraction over edge on free field (frequency dependent)
            computeRayleighDiff(srPath, pathConfiguration, 
                    dSR, rayleighSegments, rayleighPoints);
        }
        
        if(rayleighSegments.isEmpty()) {
            // We don't have a Rayleigh diffraction over DEM. Only direct SR path
            if(segments.isEmpty()) {
                segments.add(pathParameters.getSRSegment());
            }
            // Compute cumulated distance between the first diffraction and the last diffraction point
            pathParameters.e = 0;
            List<PointPath> diffPoints = points.stream().filter(pointPath -> pointPath.type != REFL).collect(Collectors.toList());
            for(int idPoint = 1; idPoint < diffPoints.size() - 2; idPoint++) {
                pathParameters.e += diffPoints.get(idPoint).coordinate.distance(diffPoints.get(idPoint+1).coordinate);
            }
            long difVPointCount = pathParameters.getPointList().stream().
                    filter(pointPath -> pointPath.type.equals(DIFV)).count();
            double distance = difVPointCount == 0 ? pathParameters.getSRSegment().d : pathParameters.getSRSegment().dc;
            pathParameters.deltaH = segments.get(0).d + pathParameters.e + segments.get(segments.size()-1).d - distance;
            pathParameters.deltaF = pathParameters.deltaH;
        } else {
            segments.addAll(rayleighSegments);
            points.addAll(1, rayleighPoints);
        }
        return true;  // Direct propagation processing completed
    }

    
    public static final double ALPHA0 = 2e-4;
    private static final double SOUND_SPEED = 340.0;

    /**
     * Main method to compute Rayleigh diffraction effects.
     * Processes each potential diffraction point to determine if it significantly affects the acoustic path.
     */
    public static void computeRayleighDiff(SegmentPath srSeg, AcousticPathConfiguration pathConfiguration,
                           LineSegment dSR, List<SegmentPath> segments, List<PointPath> points) {
        
        CutProfile cutProfile = pathConfiguration.getCutProfile();
        CnossosPath pathParameters = pathConfiguration.getPathParameters();
        List<Coordinate> pts2D = pathConfiguration.getCutPointCoordinates2D();
        Coordinate[] pts2DGround = pathConfiguration.getElevationProfile2D();
        List<Integer> cut2DGroundIndex = pathConfiguration.getCut2DGroundIndex();
        List<Double> exactFrequencyArray = pathConfiguration.getExactFrequencyArray();
        
        // Initialize basic coordinates and cut points
        DiffractionContext context = initializeDiffractionContext(cutProfile, pts2D, pts2DGround, cut2DGroundIndex);
        
        // Process each potential diffraction point
        for (int cutIndex = 1; cutIndex < context.cuts.size() - 1; cutIndex++) {
            processPotentialDiffractionPoint(cutIndex, context, srSeg, pathParameters, 
                                           dSR, segments, points, exactFrequencyArray);
        }
    }
    
    /**
     * Context class to hold diffraction calculation data.
     */
    private static class DiffractionContext {
        final List<CutPoint> cuts;
        final Coordinate src, rcv;
        final CutPoint srcCut, rcvCut;
        final Coordinate[] pts2DGround;
        final List<Integer> cut2DGroundIndex;
        final CutProfile cutProfile;
        
        DiffractionContext(CutProfile cutProfile, List<Coordinate> pts2D, 
                          Coordinate[] pts2DGround, List<Integer> cut2DGroundIndex) {
            this.cutProfile = cutProfile;
            this.cuts = cutProfile.getCutPoints();
            this.pts2DGround = pts2DGround;
            this.cut2DGroundIndex = cut2DGroundIndex;
            this.src = pts2D.get(0);
            this.rcv = pts2D.get(pts2D.size() - 1);
            this.srcCut = cutProfile.getSource();
            this.rcvCut = cutProfile.getReceiver();
        }
    }
    
    /**
     * Initialize diffraction computation context.
     */
    private static DiffractionContext initializeDiffractionContext(CutProfile cutProfile, 
                                                                  List<Coordinate> pts2D,
                                                                  Coordinate[] pts2DGround, 
                                                                  List<Integer> cut2DGroundIndex) {
        return new DiffractionContext(cutProfile, pts2D, pts2DGround, cut2DGroundIndex);
    }
    
    /**
     * Process a single potential diffraction point.
     */
    private static void processPotentialDiffractionPoint(int cutIndex, DiffractionContext context,
                                                        SegmentPath srSeg, CnossosPath pathParameters,
                                                        LineSegment dSR, List<SegmentPath> segments,
                                                        List<PointPath> points, List<Double> exactFrequencyArray) {
        
        // Get diffraction point coordinates
        int groundIndex = context.cut2DGroundIndex.get(cutIndex);
        Coordinate diffractionPoint = context.pts2DGround[groundIndex];
        
        // Calculate basic path differences
        double deltaH = calculatePathDifference(context.src, context.rcv, diffractionPoint, dSR, srSeg.d);
        
        // First criterion: frequency-based screening
        if (!passesFrequencyScreening(deltaH, exactFrequencyArray)) {
            return; // Skip this point if it doesn't meet frequency criteria
        }
        
        // Calculate detailed diffraction parameters
        DiffractionCalculation calc = calculateDiffractionParameters(context, cutIndex, groundIndex, 
                                                                   diffractionPoint, srSeg);
        
        // Second criterion: detailed diffraction screening
        if (!passesDetailedScreening(deltaH, calc.deltaPrimeH, exactFrequencyArray)) {
            return; // Skip this point if it doesn't meet detailed criteria
        }
        
        // Point passes all criteria - add to path
        addDiffractionPointToPath(context, cutIndex, calc, deltaH, pathParameters, 
                                srSeg, dSR, segments, points);
    }
    
    /**
     * Calculate the basic path difference for diffraction screening.
     */
    private static double calculatePathDifference(Coordinate src, Coordinate rcv, Coordinate diffractionPoint,
                                                LineSegment dSR, double directDistance) {
        double dSO = src.distance(diffractionPoint);
        double dOR = diffractionPoint.distance(rcv);
        return dSR.orientationIndex(diffractionPoint) * (dSO + dOR - directDistance);
    }
    
    /**
     * Check if diffraction point passes frequency-based screening.
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
     * Container for diffraction calculation results.
     */
    private static class DiffractionCalculation {
        final SegmentPath seg1, seg2;
        final Coordinate srcPrime, rcvPrime;
        final double deltaPrimeH;
        final LineSegment dSPrimeRPrime;
        
        DiffractionCalculation(SegmentPath seg1, SegmentPath seg2, Coordinate srcPrime, 
                              Coordinate rcvPrime, double deltaPrimeH, LineSegment dSPrimeRPrime) {
            this.seg1 = seg1;
            this.seg2 = seg2;
            this.srcPrime = srcPrime;
            this.rcvPrime = rcvPrime;
            this.deltaPrimeH = deltaPrimeH;
            this.dSPrimeRPrime = dSPrimeRPrime;
        }
    }
    
    /**
     * Calculate detailed diffraction parameters including mirror points.
     */
    private static DiffractionCalculation calculateDiffractionParameters(DiffractionContext context, 
                                                                        int cutIndex, int groundIndex,
                                                                        Coordinate diffractionPoint, 
                                                                        SegmentPath srSeg) {
        // Create segments S->O and O->R
        SegmentPath seg1 = createSourceToObstacleSegment(context, groundIndex, diffractionPoint);
        SegmentPath seg2 = createObstacleToReceiverSegment(context, groundIndex, diffractionPoint);
        
        // Calculate mirror points (prime coordinates)
        Coordinate srcPrime = calculateMirrorPoint(context.src, seg1.sMeanPlane);
        Coordinate rcvPrime = calculateMirrorPoint(context.rcv, seg2.rMeanPlane);
        
        // Calculate distances for mirror path
        LineSegment dSPrimeRPrime = new LineSegment(srcPrime, rcvPrime);
        srSeg.dPrime = srcPrime.distance(rcvPrime);
        seg1.dPrime = srcPrime.distance(diffractionPoint);
        seg2.dPrime = diffractionPoint.distance(rcvPrime);
        
        // Calculate path difference for mirror configuration
        double deltaPrimeH = dSPrimeRPrime.orientationIndex(diffractionPoint) * 
                           (seg1.dPrime + seg2.dPrime - srSeg.dPrime);
        
        return new DiffractionCalculation(seg1, seg2, srcPrime, rcvPrime, deltaPrimeH, dSPrimeRPrime);
    }
    
    /**
     * Create segment from source to obstacle.
     */
    private static SegmentPath createSourceToObstacleSegment(DiffractionContext context, 
                                                           int groundIndex, Coordinate obstacle) {
        Coordinate[] soCoords = Arrays.copyOfRange(context.pts2DGround, 0, groundIndex + 1);
        double[] meanPlaneCoeffs = JTSUtility.getMeanPlaneCoefficients(soCoords);
        return CnossosSegmentComputer.createSegmentPath(context.src, obstacle, meanPlaneCoeffs);
    }
    
    /**
     * Create segment from obstacle to receiver.
     */
    private static SegmentPath createObstacleToReceiverSegment(DiffractionContext context, 
                                                             int groundIndex, Coordinate obstacle) {
        Coordinate[] orCoords = Arrays.copyOfRange(context.pts2DGround, groundIndex, context.pts2DGround.length);
        double[] meanPlaneCoeffs = JTSUtility.getMeanPlaneCoefficients(orCoords);
        return CnossosSegmentComputer.createSegmentPath(obstacle, context.rcv, meanPlaneCoeffs);
    }
    
    /**
     * Calculate mirror point for diffraction analysis.
     */
    private static Coordinate calculateMirrorPoint(Coordinate original, Coordinate planePoint) {
        return new Coordinate(
            original.x + (planePoint.x - original.x) * 2,
            original.y + (planePoint.y - original.y) * 2
        );
    }
    
    /**
     * Check if diffraction point passes detailed screening criteria.
     */
    private static boolean passesDetailedScreening(double deltaH, double deltaPrimeH, 
                                                  List<Double> exactFrequencyArray) {
        for (double frequency : exactFrequencyArray) {
            if (deltaH > (SOUND_SPEED / frequency) / 4 - deltaPrimeH) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Add validated diffraction point to the acoustic path.
     */
    private static void addDiffractionPointToPath(DiffractionContext context, int cutIndex,
                                                 DiffractionCalculation calc, double deltaH,
                                                 CnossosPath pathParameters, SegmentPath srSeg,
                                                 LineSegment dSR, List<SegmentPath> segments,
                                                 List<PointPath> points) {
        // Set path parameters
        pathParameters.deltaH = deltaH;
        pathParameters.deltaPrimeH = calc.deltaPrimeH;
        
        // Configure segment ground paths
        configureSegmentGroundPaths(context, cutIndex, calc);
        
        // Calculate Fresnel corrections
        calculateFresnelCorrections(context, calc, pathParameters, srSeg, dSR);
        
        // Calculate additional path corrections
        calculateAdditionalPathCorrections(context, calc, pathParameters, srSeg);
        
        // Add segments and point to path
        segments.add(calc.seg1);
        segments.add(calc.seg2);
        points.add(new PointPath(context.pts2DGround[context.cut2DGroundIndex.get(cutIndex)], 
                                context.pts2DGround[context.cut2DGroundIndex.get(cutIndex)].z, 
                                new ArrayList<>(), DIFH_RCRIT));
    }
    
    /**
     * Configure ground paths for segments.
     */
    private static void configureSegmentGroundPaths(DiffractionContext context, int cutIndex,
                                                   DiffractionCalculation calc) {
        calc.seg1.setGpath(
            context.cutProfile.calculateWeightedGroundAbsorption(context.srcCut, context.cuts.get(cutIndex), Scene.DEFAULT_G_BUILDING),
            context.srcCut.getGroundCoefficient()
        );
        calc.seg2.setGpath(
            context.cutProfile.calculateWeightedGroundAbsorption(context.cuts.get(cutIndex), context.rcvCut, Scene.DEFAULT_G_BUILDING),
            context.srcCut.getGroundCoefficient()
        );
    }
    
    /**
     * Calculate Fresnel corrections for diffraction.
     */
    private static void calculateFresnelCorrections(DiffractionContext context, DiffractionCalculation calc,
                                                   CnossosPath pathParameters, SegmentPath srSeg,
                                                   LineSegment dSR) {
        // Use the current diffraction point from the calculation
        Coordinate obstacle = calc.seg1.r; // The obstacle point from segment calculation
        
        double dSO = context.src.distance(obstacle);
        double dOR = obstacle.distance(context.rcv);
        
        if (dSR.orientationIndex(obstacle) == 1) {
            pathParameters.deltaF = toCurve(dSO, srSeg.d) + toCurve(dOR, srSeg.d) - toCurve(srSeg.d, srSeg.d);
        } else {
            Coordinate pA = dSR.pointAlong((obstacle.x - context.src.x) / (context.rcv.x - context.src.x));
            pathParameters.deltaF = 2 * toCurve(context.src.distance(pA), srSeg.d) + 
                                  2 * toCurve(pA.distance(context.rcv), srSeg.d) - 
                                  toCurve(dSO, srSeg.d) - toCurve(dOR, srSeg.d) - toCurve(srSeg.d, srSeg.d);
        }
        
        // Calculate deltaPrimeF
        if (calc.dSPrimeRPrime.orientationIndex(obstacle) == 1) {
            pathParameters.deltaPrimeF = toCurve(calc.seg1.dPrime, srSeg.dPrime) + 
                                       toCurve(calc.seg2.dPrime, srSeg.dPrime) - 
                                       toCurve(srSeg.dPrime, srSeg.dPrime);
        } else {
            Coordinate pA = calc.dSPrimeRPrime.pointAlong((obstacle.x - calc.srcPrime.x) / 
                                                        (calc.rcvPrime.x - calc.srcPrime.x));
            pathParameters.deltaPrimeF = 2 * toCurve(calc.srcPrime.distance(pA), srSeg.dPrime) + 
                                       2 * toCurve(pA.distance(calc.srcPrime), srSeg.dPrime) - 
                                       toCurve(calc.seg1.dPrime, srSeg.dPrime) - 
                                       toCurve(calc.seg2.dPrime, srSeg.d) - 
                                       toCurve(srSeg.dPrime, srSeg.dPrime);
        }
    }
    
    /**
     * Calculate additional path corrections.
     */
    private static void calculateAdditionalPathCorrections(DiffractionContext context, DiffractionCalculation calc,
                                                          CnossosPath pathParameters, SegmentPath srSeg) {
        // Use the current diffraction point from the calculation
        Coordinate obstacle = calc.seg1.r; // The obstacle point from segment calculation
        
        double dOR = obstacle.distance(context.rcv);
        double dSO = context.src.distance(obstacle);
        
        // Calculate deltaSPrimeRH
        LineSegment sPrimeR = new LineSegment(calc.seg1.sPrime, context.rcv);
        double dSPrimeO = calc.seg1.sPrime.distance(obstacle);
        double dSPrimeR = calc.seg1.sPrime.distance(context.rcv);
        pathParameters.deltaSPrimeRH = sPrimeR.orientationIndex(obstacle) * (dSPrimeO + dOR - dSPrimeR);
        
        // Calculate deltaSRPrimeH
        LineSegment sRPrime = new LineSegment(context.src, calc.seg2.rPrime);
        double dORPrime = obstacle.distance(calc.seg2.rPrime);
        double dSRPrime = context.src.distance(calc.seg2.rPrime);
        pathParameters.deltaSRPrimeH = sRPrime.orientationIndex(obstacle) * (dSO + dORPrime - dSRPrime);
    }


    /**
     * Eq.2.5.24 and Eq. 2.5.25
     * @param mn
     * @param d
     * @return
     */
    public static double toCurve(double mn, double d){
        return 2*max(1000, 8*d)* asin(mn/(2*max(1000, 8*d)));
    }
}
