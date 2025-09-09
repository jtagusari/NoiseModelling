package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
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
 * @param sourceToReceiverPath The source-receiver segment
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
    private final AcousticPathConfiguration pathConfiguration;
    public DirectPropagationProcessor(AcousticPathConfiguration pathConfiguration) {
        this.pathConfiguration = pathConfiguration;
    }

    public boolean processDirectPropagation(SegmentPath sourceToReceiverPath,List<SegmentPath> segments, List<PointPath> points) {
        
        
        CnossosPath pathParameters = pathConfiguration.getPathParameters();

    
        // Direct propagation (no diffraction over obstructing objects)
        boolean horizontalPlaneDiffraction = pathConfiguration.getCutProfilePoints().stream()
                .anyMatch(cutPoint -> cutPoint instanceof CutPointVEdgeDiffraction);
        
        List<SegmentPath> rayleighSegments = new ArrayList<>();
        List<PointPath> rayleighPoints = new ArrayList<>();
        
        // do not check for rayleigh if the path is not direct between R and S
        if(!horizontalPlaneDiffraction) {
            // Check for Rayleigh criterion for segments computation
            LineSegment sourceToReceiverDistance = new LineSegment(pathConfiguration.getSourceCoordinate2D(), pathConfiguration.getReceiverCoordinate2D());
            // Look for diffraction over edge on free field (frequency dependent)
            computeRayleighDiff(sourceToReceiverPath, 
                    sourceToReceiverDistance, rayleighSegments, rayleighPoints);
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
    public void computeRayleighDiff(SegmentPath sourceToReceiverSegment, 
                           LineSegment sourceToReceiverDistance, List<SegmentPath> segments, List<PointPath> points) {
        
        CnossosPath pathParameters = pathConfiguration.getPathParameters();
        
        // Initialize basic coordinates and cut points
        
        // Process each potential diffraction point
        for (int cutIndex = 1; cutIndex < pathConfiguration.getCutProfilePoints().size() - 1; cutIndex++) {
            int groundIndex = pathConfiguration.getGroundEffectPointIndices().get(cutIndex);
            Coordinate diffractionPoint = pathConfiguration.getElevationProfile2D()[groundIndex];
            
            // Calculate basic path differences
            double deltaH = calculatePathDifference(pathConfiguration.getSourceCoordinate2D(), pathConfiguration.getReceiverCoordinate2D(), diffractionPoint, sourceToReceiverDistance, sourceToReceiverSegment.d);
            
            // First criterion: frequency-based screening
            if (!passesFrequencyScreening(deltaH)) {
                continue; // Skip this point if it doesn't meet frequency criteria
            }
            
            // Calculate detailed diffraction parameters
            DiffractionCalculation calc = calculateDiffractionParameters(groundIndex, diffractionPoint, sourceToReceiverSegment);
            
            // Second criterion: detailed diffraction screening
            if (!passesDetailedScreening(deltaH, calc.deltaPrimeH)) {
                continue; // Skip this point if it doesn't meet detailed criteria
            }
            
            // Point passes all criteria - add to path
            addDiffractionPointToPath(cutIndex, calc, deltaH, pathParameters, sourceToReceiverSegment, sourceToReceiverDistance, segments, points);
        }
    }
    

    
    
    /**
     * Calculate the basic path difference for diffraction screening.
     */
    private static double calculatePathDifference(Coordinate sourceCoordinate2D, Coordinate receiverCoordinate2D, Coordinate diffractionPoint, LineSegment sourceToReceiverSegment, double directDistance) {
        double sourceToObstacleDistance = sourceCoordinate2D.distance(diffractionPoint);
        double obstacleToReceiverDistance = diffractionPoint.distance(receiverCoordinate2D);
        return sourceToReceiverSegment.orientationIndex(diffractionPoint) * (sourceToObstacleDistance + obstacleToReceiverDistance - directDistance);
    }
    
    /**
     * Check if diffraction point passes frequency-based screening.
     */
    private boolean passesFrequencyScreening(double deltaH) {
        for (double frequency : pathConfiguration.getExactFrequencyArray()) {
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
    private DiffractionCalculation calculateDiffractionParameters(int groundIndex, Coordinate diffractionPointCoordinate, SegmentPath sourceToReceiverSegment) {

        // Create segments S->O and O->R
        SegmentPath sourceToObstacleSegment = createSourceToObstacleSegment(groundIndex, diffractionPointCoordinate);
        SegmentPath obstacleToReceiverSegment = createObstacleToReceiverSegment(groundIndex, diffractionPointCoordinate);

        // Calculate mirror points (prime coordinates)
        Coordinate sourceMirrorCoordinate2D = calculateMirrorPoint(pathConfiguration.getSourceCoordinate2D(), sourceToObstacleSegment.sMeanPlane);
        Coordinate receiverMirrorCoordinate2D = calculateMirrorPoint(pathConfiguration.getReceiverCoordinate2D(), obstacleToReceiverSegment.rMeanPlane);
        
        // Calculate distances for mirror path
        LineSegment sourceMirrorToReceiverMirrorSegment = new LineSegment(sourceMirrorCoordinate2D, receiverMirrorCoordinate2D);
        sourceToReceiverSegment.dPrime = sourceMirrorCoordinate2D.distance(receiverMirrorCoordinate2D);
        sourceToObstacleSegment.dPrime = sourceMirrorCoordinate2D.distance(diffractionPointCoordinate);
        obstacleToReceiverSegment.dPrime = diffractionPointCoordinate.distance(receiverMirrorCoordinate2D);
        
        // Calculate path difference for mirror configuration
        double deltaPrimeH = sourceMirrorToReceiverMirrorSegment.orientationIndex(diffractionPointCoordinate) * 
                           (sourceToObstacleSegment.dPrime + obstacleToReceiverSegment.dPrime - sourceToReceiverSegment.dPrime);
        
        return new DiffractionCalculation(sourceToObstacleSegment, obstacleToReceiverSegment, sourceMirrorCoordinate2D, receiverMirrorCoordinate2D, deltaPrimeH, sourceMirrorToReceiverMirrorSegment);
    }
    
    /**
     * Create segment from source to obstacleCoordinate.
     */
    private SegmentPath createSourceToObstacleSegment(int groundIndex, Coordinate obstacleCoordinate) {
        Coordinate[] sourceToObstacleCoordinates = Arrays.copyOfRange(pathConfiguration.getElevationProfile2D(), 0, groundIndex + 1);
        // Coordinate sourceCoordinate = pathConfiguration.getCutPointCoordinates2D().get(0);
        double[] meanPlaneCoeffs = JTSUtility.getMeanPlaneCoefficients(sourceToObstacleCoordinates);
        return CnossosSegmentComputer.createSegmentPath(pathConfiguration.getSourceCoordinate2D(), obstacleCoordinate, meanPlaneCoeffs);
    }
    
    /**
     * Create segment from obstacleCoordinate to receiver.
     */
    private SegmentPath createObstacleToReceiverSegment(int groundIndex, Coordinate obstacleCoordinate) {
        Coordinate[] obtacleToReceiverCoordinates = Arrays.copyOfRange(pathConfiguration.getElevationProfile2D(), groundIndex, pathConfiguration.getElevationProfile2D().length);
        
        double[] meanPlaneCoeffs = JTSUtility.getMeanPlaneCoefficients(obtacleToReceiverCoordinates);
        // Coordinate receiverCoordinate = pathConfiguration.getCutPointCoordinates2D().get(pathConfiguration.getCutPointCoordinates2D().size() - 1);
        return CnossosSegmentComputer.createSegmentPath(obstacleCoordinate, pathConfiguration.getReceiverCoordinate2D(), meanPlaneCoeffs);
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
    private boolean passesDetailedScreening(double deltaH, double deltaPrimeH) {
        for (double frequency : pathConfiguration.getExactFrequencyArray()) {
            if (deltaH > (SOUND_SPEED / frequency) / 4 - deltaPrimeH) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Add validated diffraction point to the acoustic path.
     */
    private void addDiffractionPointToPath(int cutIndex, DiffractionCalculation calc, double deltaH, CnossosPath pathParameters, SegmentPath sourceToReceiverSegment, LineSegment sourceToReceiverDistance, List<SegmentPath> segments, List<PointPath> points) {
        // Set path parameters
        pathParameters.deltaH = deltaH;
        pathParameters.deltaPrimeH = calc.deltaPrimeH;
        
        // Configure segment ground paths
        configureSegmentGroundPaths(calc, cutIndex);
        
        // Calculate Fresnel corrections
        calculateFresnelCorrections(calc, pathParameters, sourceToReceiverSegment, sourceToReceiverDistance);
        
        // Calculate additional path corrections
        calculateAdditionalPathCorrections(calc, pathParameters, sourceToReceiverSegment);
        
        // Add segments and point to path
        segments.add(calc.seg1);
        segments.add(calc.seg2);
        points.add(
            new PointPath(
                pathConfiguration.getElevationProfile2D()[pathConfiguration.getGroundEffectPointIndices().get(cutIndex)],
                pathConfiguration.getElevationProfile2D()[pathConfiguration.getGroundEffectPointIndices().get(cutIndex)].z,
                new ArrayList<>(), 
                DIFH_RCRIT
            )
        );
    }
    
    /**
     * Configure ground paths for segments.
     */
    private void configureSegmentGroundPaths(DiffractionCalculation calc, int cutIndex) {

        CutPointSource cutPointSource = (CutPointSource) pathConfiguration.getCutProfile().getSource();
        CutPointReceiver cutPointReceiver = (CutPointReceiver) pathConfiguration.getCutProfile().getReceiver();
        calc.seg1.setGpath(
            pathConfiguration.getCutProfile().calculateWeightedGroundAbsorption(cutPointSource, pathConfiguration.getCutProfilePoints().get(cutIndex), Scene.DEFAULT_G_BUILDING),
            cutPointSource.getGroundCoefficient()
        );

        calc.seg2.setGpath(
            pathConfiguration.getCutProfile().calculateWeightedGroundAbsorption(pathConfiguration.getCutProfilePoints().get(cutIndex), cutPointReceiver, Scene.DEFAULT_G_BUILDING),
            cutPointReceiver.getGroundCoefficient()
        );
    }
    
    /**
     * Calculate Fresnel corrections for diffraction.
     */
    private void calculateFresnelCorrections( DiffractionCalculation calc,
                                                   CnossosPath pathParameters, SegmentPath sourceToReceiverSegment,
                                                   LineSegment sourceToReceiverDistance) {
        // Use the current diffraction point from the calculation
        Coordinate obstacleCoordinate = calc.seg1.r; // The obstacleCoordinate point from segment calculation
        
        double dSO = pathConfiguration.getSourceCoordinate2D().distance(obstacleCoordinate);
        double dOR = obstacleCoordinate.distance(pathConfiguration.getReceiverCoordinate2D());
        
        if (sourceToReceiverDistance.orientationIndex(obstacleCoordinate) == 1) {
            pathParameters.deltaF = toCurve(dSO, sourceToReceiverSegment.d) + toCurve(dOR, sourceToReceiverSegment.d) - toCurve(sourceToReceiverSegment.d, sourceToReceiverSegment.d);
        } else {
            Coordinate pA = sourceToReceiverDistance.pointAlong((obstacleCoordinate.x - pathConfiguration.getSourceCoordinate2D().x) / (pathConfiguration.getReceiverCoordinate2D().x - pathConfiguration.getSourceCoordinate2D().x));
            pathParameters.deltaF = 2 * toCurve(pathConfiguration.getSourceCoordinate2D().distance(pA), sourceToReceiverSegment.d) + 
                                  2 * toCurve(pA.distance(pathConfiguration.getReceiverCoordinate2D()), sourceToReceiverSegment.d) - 
                                  toCurve(dSO, sourceToReceiverSegment.d) - toCurve(dOR, sourceToReceiverSegment.d) - toCurve(sourceToReceiverSegment.d, sourceToReceiverSegment.d);
        }
        
        // Calculate deltaPrimeF
        if (calc.dSPrimeRPrime.orientationIndex(obstacleCoordinate) == 1) {
            pathParameters.deltaPrimeF = toCurve(calc.seg1.dPrime, sourceToReceiverSegment.dPrime) + 
                                       toCurve(calc.seg2.dPrime, sourceToReceiverSegment.dPrime) - 
                                       toCurve(sourceToReceiverSegment.dPrime, sourceToReceiverSegment.dPrime);
        } else {
            Coordinate pA = calc.dSPrimeRPrime.pointAlong((obstacleCoordinate.x - calc.srcPrime.x) / 
                                                        (calc.rcvPrime.x - calc.srcPrime.x));
            pathParameters.deltaPrimeF = 2 * toCurve(calc.srcPrime.distance(pA), sourceToReceiverSegment.dPrime) + 
                                       2 * toCurve(pA.distance(calc.srcPrime), sourceToReceiverSegment.dPrime) - 
                                       toCurve(calc.seg1.dPrime, sourceToReceiverSegment.dPrime) - 
                                       toCurve(calc.seg2.dPrime, sourceToReceiverSegment.d) - 
                                       toCurve(sourceToReceiverSegment.dPrime, sourceToReceiverSegment.dPrime);
        }
    }
    
    /**
     * Calculate additional path corrections.
     */
    private void calculateAdditionalPathCorrections(DiffractionCalculation calc,
                                                          CnossosPath pathParameters, SegmentPath sourceToReceiverSegment) {
        // Use the current diffraction point from the calculation
        Coordinate obstacleCoordinate = calc.seg1.r; // The obstacleCoordinate point from segment calculation
        
        double dSO = pathConfiguration.getSourceCoordinate2D().distance(obstacleCoordinate);
        double dOR = obstacleCoordinate.distance(pathConfiguration.getReceiverCoordinate2D());
        
        // Calculate deltaSPrimeRH
        LineSegment sPrimeR = new LineSegment(calc.seg1.sPrime, pathConfiguration.getReceiverCoordinate2D());
        double dSPrimeO = calc.seg1.sPrime.distance(obstacleCoordinate);
        double dSPrimeR = calc.seg1.sPrime.distance(pathConfiguration.getReceiverCoordinate2D());
        pathParameters.deltaSPrimeRH = sPrimeR.orientationIndex(obstacleCoordinate) * (dSPrimeO + dOR - dSPrimeR);
        
        // Calculate deltaSRPrimeH
        LineSegment sRPrime = new LineSegment(pathConfiguration.getSourceCoordinate2D(), calc.seg2.rPrime);
        double dORPrime = obstacleCoordinate.distance(calc.seg2.rPrime);
        double sourceToReceiverDistancePrime = pathConfiguration.getSourceCoordinate2D().distance(calc.seg2.rPrime);
        pathParameters.deltaSRPrimeH = sRPrime.orientationIndex(obstacleCoordinate) * (dSO + dORPrime - sourceToReceiverDistancePrime);
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
