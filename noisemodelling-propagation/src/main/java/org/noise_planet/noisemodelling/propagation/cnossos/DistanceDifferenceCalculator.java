package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

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
 * <p>All methods operate on the provided {@link CnossosPathExt} and
 * {@link AcousticPathConfiguration} objects and mutate the {@link CnossosPathExt}
 * instance in-place (they do not create or return new CnossosPathExt objects).
 */
public class DistanceDifferenceCalculator {   


    /**
     * Calculate the basic path-length difference (delta H) used in homogeneous
        * diffraction screening: orientation * (s-&gt;o + o-&gt;r - s-&gt;r).
     *
     * @param sourceToReceiverLineSegment straight-line segment between source and receiver
     * @param diffractionPointCoordinate candidate diffraction point coordinate
     * @return signed path-length difference used as screening metric
     */
    public static double computeDeltaH(LineSegment sourceToReceiverLineSegment, Coordinate diffractionPointCoordinate) {
        double orientationIndex = sourceToReceiverLineSegment.orientationIndex(diffractionPointCoordinate);
        double sourceToObstacleDistance = sourceToReceiverLineSegment.p0.distance(diffractionPointCoordinate);
        double obstacleToReceiverDistance = diffractionPointCoordinate.distance(sourceToReceiverLineSegment.p1);
        double directDistance = sourceToReceiverLineSegment.getLength();
        return orientationIndex * (sourceToObstacleDistance + obstacleToReceiverDistance - directDistance);
    }
    
    public static double computeDeltaH(Coordinate sourceCoordinate, Coordinate diffractionPointCoordinate, Coordinate receiverCoordinate) {
        LineSegment sourceToReceiverLineSegment = new LineSegment(sourceCoordinate, receiverCoordinate);
        return computeDeltaH(sourceToReceiverLineSegment, diffractionPointCoordinate);
    }

    
    public static double computeDeltaH(Coordinate sourceCoordinate, Coordinate firstDiffractionPointCoordinate, double e, Coordinate lastDiffractionPointCoordinate,Coordinate receiverCoordinate, Coordinate orientationPointCoordinate) {
        
        LineSegment sourceToReceiverLineSegment = new LineSegment(sourceCoordinate, receiverCoordinate);

        double orientationIndex = sourceToReceiverLineSegment.orientationIndex(orientationPointCoordinate);
        double sourceToFirstObstacleDistance = sourceCoordinate.distance(firstDiffractionPointCoordinate);
        double obstacleDistance = e;
        double lastObstacleToReceiverDistance = lastDiffractionPointCoordinate.distance(receiverCoordinate);

        return orientationIndex*(sourceToFirstObstacleDistance + obstacleDistance + lastObstacleToReceiverDistance - sourceToReceiverLineSegment.getLength());
    }

    
    public static double computeDeltaH(Coordinate sourceCoordinate, Coordinate firstDiffractionPointCoordinate, double e, Coordinate lastDiffractionPointCoordinate,Coordinate receiverCoordinate) {
        
        return computeDeltaH(sourceCoordinate, firstDiffractionPointCoordinate, e, lastDiffractionPointCoordinate, receiverCoordinate, firstDiffractionPointCoordinate);
    }
    
    public static double computeVpathDeltaH(Coordinate sourceCoordinate, Coordinate firstDiffractionPointCoordinate, double e, Coordinate lastDiffractionPointCoordinate,Coordinate receiverCoordinate, Coordinate orientationPointCoordinate, double directDistance) {
        
        LineSegment sourceToReceiverLineSegment = new LineSegment(sourceCoordinate, receiverCoordinate);

        double orientationIndex = sourceToReceiverLineSegment.orientationIndex(orientationPointCoordinate);
        double sourceToFirstObstacleDistance = sourceCoordinate.distance(firstDiffractionPointCoordinate);
        double obstacleDistance = e;
        double lastObstacleToReceiverDistance = lastDiffractionPointCoordinate.distance(receiverCoordinate);

        return orientationIndex*(sourceToFirstObstacleDistance + obstacleDistance + lastObstacleToReceiverDistance - directDistance);
        
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
    public static double computeDeltaF(LineSegment sourceToReceiverLineSegment, Coordinate diffractionPointCoordinate) {

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

    
    public static double computeDeltaF(Coordinate sourceCoordinate, Coordinate diffractionPointCoordinate, Coordinate receiverCoordinate) {
        LineSegment sourceToReceiverLineSegment = new LineSegment(sourceCoordinate, receiverCoordinate);
        return computeDeltaF(sourceToReceiverLineSegment, diffractionPointCoordinate);
    }
    

    public static double computeDeltaF(Coordinate sourceCoordinate, Coordinate firstDiffractionPointCoordinate, double e, Coordinate lastDiffractionPointCoordinate, Coordinate receiverCoordinate) {
        
        LineSegment sourceToReceiverLineSegment = new LineSegment(sourceCoordinate, receiverCoordinate);
        return computeDeltaF(sourceCoordinate, firstDiffractionPointCoordinate, e, lastDiffractionPointCoordinate, receiverCoordinate, 1, sourceToReceiverLineSegment);

    }

    public static double computeDeltaF(Coordinate sourceCoordinate, Coordinate firstDiffractionPointCoordinate, double e, Coordinate lastDiffractionPointCoordinate, Coordinate receiverCoordinate, Coordinate orientationCoordinate) {
        
        LineSegment sourceToReceiverLineSegment = new LineSegment(sourceCoordinate, receiverCoordinate);

        
        int orientationIndex = sourceToReceiverLineSegment.orientationIndex(orientationCoordinate);
        return computeDeltaF(sourceCoordinate, firstDiffractionPointCoordinate, e, lastDiffractionPointCoordinate, receiverCoordinate, orientationIndex, sourceToReceiverLineSegment);

    }

    public static double computeDeltaF(Coordinate sourceCoordinate, Coordinate firstDiffractionPointCoordinate, double e, Coordinate lastDiffractionPointCoordinate, Coordinate receiverCoordinate, Coordinate orientationCoordinate, LineSegment quasiObstacleLocatingLineSegment) {
        
        LineSegment sourceToReceiverLineSegment = new LineSegment(sourceCoordinate, receiverCoordinate);
                
        int orientationIndex = sourceToReceiverLineSegment.orientationIndex(orientationCoordinate);
        return computeDeltaF(sourceCoordinate, firstDiffractionPointCoordinate, e, lastDiffractionPointCoordinate, receiverCoordinate, orientationIndex, quasiObstacleLocatingLineSegment);

    }

    public static double computeDeltaF(Coordinate sourceCoordinate, Coordinate firstDiffractionPointCoordinate, double e, Coordinate lastDiffractionPointCoordinate, Coordinate receiverCoordinate, int orientationIndex, LineSegment quasiObstacleLocatingLineSegment) {
        
        LineSegment sourceToReceiverLineSegment = new LineSegment(sourceCoordinate, receiverCoordinate);
        
        double sourceToFirstObstacleDistance = convertToCurvedPath(sourceCoordinate.distance(firstDiffractionPointCoordinate), sourceToReceiverLineSegment.getLength());
        double obstacleDistance = convertToCurvedPath(e, sourceToReceiverLineSegment.getLength());
        double lastObstacleToReceiverDistance = convertToCurvedPath(lastDiffractionPointCoordinate.distance(receiverCoordinate), sourceToReceiverLineSegment.getLength());
        double directCurvedDistance = convertToCurvedPath(sourceToReceiverLineSegment.getLength(), sourceToReceiverLineSegment.getLength());
        
        if (orientationIndex == 1) {
            return sourceToFirstObstacleDistance+ obstacleDistance + lastObstacleToReceiverDistance - directCurvedDistance;
        } else {
            
            double sourceToObstacleDistanceX = firstDiffractionPointCoordinate.x - sourceCoordinate.x;
            double sourceToReceiverDistanceX = receiverCoordinate.x - sourceCoordinate.x;
            Coordinate quasiObstacleCoordinate = quasiObstacleLocatingLineSegment.pointAlong(sourceToObstacleDistanceX / sourceToReceiverDistanceX);

            double sourceToQuasiObstacleCurvedDistance = convertToCurvedPath(sourceCoordinate.distance(quasiObstacleCoordinate), sourceToReceiverLineSegment.getLength());
            double quasiObstacleToReceiverCurvedDistance = convertToCurvedPath(quasiObstacleCoordinate.distance(receiverCoordinate), sourceToReceiverLineSegment.getLength());


            return 2 * sourceToQuasiObstacleCurvedDistance + 
                                  2 * quasiObstacleToReceiverCurvedDistance - 
                                  sourceToFirstObstacleDistance - lastObstacleToReceiverDistance - directCurvedDistance;
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