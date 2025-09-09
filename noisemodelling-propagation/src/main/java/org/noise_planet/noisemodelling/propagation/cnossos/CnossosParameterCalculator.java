package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import java.util.List;
import java.util.stream.Collectors;

import static org.noise_planet.noisemodelling.propagation.cnossos.PointPath.POINT_TYPE.*;

/**
 * Handles the computation of final Cnossos parameters and delta values.
 * This class contains the exact original logic from computeFinalPathParametersInstance.
 */
public class CnossosParameterCalculator {

    /**
     * Compute final path parameters and delta values.
     * This method contains the exact original logic from computeFinalPathParametersInstance.
     * 
     * @param src Source coordinate
     * @param rcv Receiver coordinate
     * @param points List of path points
     * @param segments List of path segments
     * @param pathParameters Path parameters to be updated
     * @param srPath Source-receiver path segment
     */
    public static void computeFinalPathParameters(Coordinate src, Coordinate rcv, 
                                                List<PointPath> points, 
                                                List<SegmentPath> segments, 
                                                CnossosPath pathParameters, 
                                                SegmentPath srPath) {
        // Find first and last horizontal diffraction points
        PointPath p0 = points.stream().filter(p -> p.type.equals(DIFH)).findFirst().orElse(null);
        if(p0 == null) {
            return; // No horizontal diffraction points to process
        }
        
        Coordinate c0 = p0.coordinate;
        PointPath pn = points.stream().filter(p -> p.type.equals(DIFH)).reduce((first, second) -> second).orElse(null);
        if(pn == null) {
            return; // Should not happen if p0 exists
        }
        Coordinate cn = pn.coordinate;

        SegmentPath seg1 = segments.get(0);
        SegmentPath seg2 = segments.get(segments.size()-1);

        double dSO0 = seg1.d;
        double dOnR = seg2.d;
        LineSegment sr = new LineSegment(src, rcv);

        LineSegment sPrimeR = new LineSegment(seg1.sPrime, rcv);
        double dSPrimeR = seg1.sPrime.distance(rcv);
        double dSPrimeO = seg1.sPrime.distance(c0);
        // Compute cumulated distance between the first diffraction and the last diffraction point
        pathParameters.e = 0;
        List<PointPath> diffPoints = points.stream().filter(pointPath -> pointPath.type != REFL).collect(Collectors.toList());
        for(int idPoint = 1; idPoint < diffPoints.size() - 2; idPoint++) {
            pathParameters.e += diffPoints.get(idPoint).coordinate.distance(diffPoints.get(idPoint+1).coordinate);
        }
        pathParameters.deltaSPrimeRH = sPrimeR.orientationIndex(c0)*(dSPrimeO + pathParameters.e + dOnR - dSPrimeR);
        pathParameters.deltaSPrimeRF = convertToCurvedPath(dSPrimeO, dSPrimeR) + convertToCurvedPath(pathParameters.e, dSPrimeR) + convertToCurvedPath(dOnR, dSPrimeR) - convertToCurvedPath(dSPrimeR, dSPrimeR);

        LineSegment sRPrime = new LineSegment(src, seg2.rPrime);
        double dSRPrime = src.distance(seg2.rPrime);
        double dORPrime = cn.distance(seg2.rPrime);
        pathParameters.deltaSRPrimeH = (src.x>seg2.rPrime.x?-1:1)*sRPrime.orientationIndex(cn)*(dSO0 + pathParameters.e + dORPrime - dSRPrime);
        pathParameters.deltaSRPrimeF = convertToCurvedPath(dSO0, dSRPrime) + convertToCurvedPath(pathParameters.e, dSRPrime) + convertToCurvedPath(dORPrime, dSRPrime) - convertToCurvedPath(dSRPrime, dSRPrime);

        Coordinate srcPrime = new Coordinate(src.x + (seg1.sMeanPlane.x - src.x) * 2, src.y + (seg1.sMeanPlane.y - src.y) * 2);
        Coordinate rcvPrime = new Coordinate(rcv.x + (seg2.rMeanPlane.x - rcv.x) * 2, rcv.y + (seg2.rMeanPlane.y - rcv.y) * 2);

        LineSegment dSPrimeRPrime = new LineSegment(srcPrime, rcvPrime);
        srPath.dPrime = srcPrime.distance(rcvPrime);
        seg1.dPrime = srcPrime.distance(c0);
        seg2.dPrime = cn.distance(rcvPrime);

        long difVPointCount = pathParameters.getPointList().stream().
                filter(pointPath -> pointPath.type.equals(DIFV)).count();
        double distance = difVPointCount == 0 ? pathParameters.getSRSegment().d : pathParameters.getSRSegment().dc;
        pathParameters.deltaH = sr.orientationIndex(c0) * (dSO0 + pathParameters.e + dOnR - distance);
        if (sr.orientationIndex(c0) == 1) {
            pathParameters.deltaF = convertToCurvedPath(seg1.d, srPath.d) + convertToCurvedPath(pathParameters.e, srPath.d) + convertToCurvedPath(seg2.d, srPath.d) - convertToCurvedPath(srPath.d, srPath.d);
        } else {
            Coordinate pA = sr.pointAlong((c0.x - srcPrime.x) / (rcvPrime.x - srcPrime.x));
            pathParameters.deltaF = 2 * convertToCurvedPath(srcPrime.distance(pA), srPath.dPrime) + 2 * convertToCurvedPath(pA.distance(rcvPrime), srPath.dPrime) - convertToCurvedPath(seg1.dPrime, srPath.dPrime) - convertToCurvedPath(seg2.dPrime, srPath.dPrime) - convertToCurvedPath(srPath.dPrime, srPath.dPrime);
        }

        pathParameters.deltaPrimeH = dSPrimeRPrime.orientationIndex(c0) * (seg1.dPrime + pathParameters.e + seg2.dPrime - srPath.dPrime);

        pathParameters.deltaPrimeH = dSPrimeRPrime.orientationIndex(c0) * (seg1.dPrime + seg2.dPrime - srPath.dPrime);
        if(dSPrimeRPrime.orientationIndex(c0) == 1) {
            pathParameters.deltaPrimeF = convertToCurvedPath(seg1.dPrime, srPath.dPrime) + convertToCurvedPath(seg2.dPrime, srPath.dPrime) - convertToCurvedPath(srPath.dPrime, srPath.dPrime);
        } else {
            Coordinate pA = dSPrimeRPrime.pointAlong((c0.x-srcPrime.x)/(rcvPrime.x-srcPrime.x));
            pathParameters.deltaPrimeF =2*convertToCurvedPath(srcPrime.distance(pA), srPath.dPrime) + 2*convertToCurvedPath(pA.distance(rcvPrime), srPath.dPrime) - convertToCurvedPath(seg1.dPrime, srPath.dPrime) - convertToCurvedPath(seg2.dPrime, srPath.d) - convertToCurvedPath(srPath.dPrime, srPath.dPrime);
        }
    }
    
    /**
     * Convert path length to curved path according to CNOSSOS-EU equations 2.5.24 and 2.5.25.
     * Applies earth curvature correction for long-distance propagation.
     * 
     * @param mn Path length parameter
     * @param d Distance parameter
     * @return Curved path length correction
     */
    public static double convertToCurvedPath(double mn, double d){
        return 2* Math.max(1000, 8*d)* Math.asin(mn/(2*Math.max(1000, 8*d)));
    }

}
