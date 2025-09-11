package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.math.Vector2D;

import java.util.List;

import static java.lang.Math.*;
import static org.noise_planet.noisemodelling.pathfinder.utils.geometry.GeometryUtils.projectPointOnLine;

/**
 * Computes CNOSSOS-EU compliant acoustic segment properties.
 * Implements standard equations for geometric and acoustic calculations.
 */
public class CnossosSegmentComputer {
    /** CNOSSOS-EU atmospheric refraction coefficient */
    private static final double ALPHA0 = 2e-4;

    /**
     * Create basic segment path from coordinate list with default ground parameters.
     */
    public static SegmentPath createSegmentPath(List<Coordinate> pts, double[] meanPlane) {
        return createSegmentPathWithGroundFactors(pts.get(0), pts.get(pts.size() - 1), meanPlane, 0, 0);
    }

    /**
     * Create segment path from coordinate list with specified ground parameters.
     */
    public static SegmentPath createSegmentPathWithGroundFactors(List<Coordinate> pts, double[] meanPlane, double weightedGroundAbsorption, double groundAbsorptionAtSource) {
        return createSegmentPathWithGroundFactors(pts.get(0), pts.get(pts.size() - 1), meanPlane, weightedGroundAbsorption, groundAbsorptionAtSource);
    }

    /**
     * Create basic segment path between source and receiver with default ground parameters.
     * @param src Source coordinate
     * @param rcv Receiver coordinate  
     * @param meanPlane Mean plane coefficients [a, b]
     * @return Calculated segment path
     */
    public static SegmentPath createSegmentPath(Coordinate src, Coordinate rcv, double[] meanPlane) {
        return createSegmentPathWithGroundFactors(src, rcv, meanPlane, 0, 0);
    }

    /**
     * Create complete acoustic segment path with ground parameters.
     * Builds a SegmentPath object containing all geometric and acoustic properties
     * needed for sound propagation calculations according to CNOSSOS-EU standard.
     * 
     * @param src Source coordinate
     * @param rcv Receiver coordinate
     * @param meanPlane Mean plane coefficients [a, b] 
     * @param weightedGroundAbsorption Ground factor along the path
     * @param groundAbsorptionAtSource Ground factor at source area
     * @return Complete segment path with geometric and acoustic parameters
     */
    public static SegmentPath createSegmentPathWithGroundFactors(Coordinate src, Coordinate rcv, double[] meanPlane, double weightedGroundAbsorption, double groundAbsorptionAtSource) {
        SegmentPath segment = new SegmentPath();
        
        // Initialize geometric properties and coordinate projections
        initializeGeometricProperties(segment, src, rcv, meanPlane);
        
        // Compute segment distances and height measurements
        computeDistanceAndHeightMetrics(segment);
        
        // Apply CNOSSOS-EU ground factor calculations (Eq. 2.5.14)
        applyGroundFactorCalculations(segment, weightedGroundAbsorption, groundAbsorptionAtSource);
        
        // Apply CNOSSOS-EU Fresnel zone corrections (Eq. 2.5.19)
        applyFresnelZoneCorrections(segment);
        
        return segment;
    }
    
    /**
     * Initialize geometric properties and coordinate projections for the segment.
     * Sets up basic coordinates, mean plane projections, and mirror points.
     */
    private static void initializeGeometricProperties(SegmentPath segment, Coordinate src, Coordinate rcv, double[] meanPlane) {
        // Project source and receiver onto mean plane
        Coordinate sourceOnPlane = projectPointOnLine(src, meanPlane[0], meanPlane[1]);
        Coordinate receiverOnPlane = projectPointOnLine(rcv, meanPlane[0], meanPlane[1]);
        
        // Calculate mirror points (sPrime, rPrime)
        Vector2D sourceToPlane = Vector2D.create(src, sourceOnPlane);
        Vector2D receiverToPlane = Vector2D.create(rcv, receiverOnPlane);
        
        // Set basic coordinates
        segment.s = src;
        segment.r = rcv;
        segment.sMeanPlane = sourceOnPlane;
        segment.rMeanPlane = receiverOnPlane;
        segment.sPrime = Vector2D.create(sourceOnPlane).add(sourceToPlane).toCoordinate();
        segment.rPrime = Vector2D.create(receiverOnPlane).add(receiverToPlane).toCoordinate();
        
        // Set mean plane coefficients
        segment.a = meanPlane[0];
        segment.b = meanPlane[1];
    }
    
    /**
     * Compute segment distances and height measurements.
     * Calculates direct distances and heights above the mean plane.
     */
    private static void computeDistanceAndHeightMetrics(SegmentPath segment) {
        // Direct distance and projected distance
        segment.d = segment.s.distance(segment.r);
        segment.dp = segment.sMeanPlane.distance(segment.rMeanPlane);
        
        // Heights above mean plane
        segment.zsH = segment.s.distance(segment.sMeanPlane);
        segment.zrH = segment.r.distance(segment.rMeanPlane);
    }
    
    /**
     * Apply CNOSSOS-EU ground factor calculations according to equation 2.5.14.
     * Computes ground factors with height-based interpolation.
     */
    private static void applyGroundFactorCalculations(SegmentPath segment, double weightedGroundAbsorption, double groundAbsorptionAtSource) {
        segment.setWeightedGroundAbsorption(weightedGroundAbsorption);
        
        // Test form for height-based ground factor interpolation
        segment.testFormH = segment.dp / (30 * (segment.zsH + segment.zrH));
        
        // Ground factor with height-based interpolation (Eq. 2.5.14)
        if (segment.testFormH <= 1) {
            segment.gPathPrime = segment.getWeightedGroundAbsorption() * segment.testFormH + groundAbsorptionAtSource * (1 - segment.testFormH);
        } else {
            segment.gPathPrime = segment.getWeightedGroundAbsorption();
        }
    }
    
    /**
     * Apply CNOSSOS-EU Fresnel zone corrections according to equation 2.5.19.
     * Calculates height corrections including turbulence and atmospheric effects.
     */
    private static void applyFresnelZoneCorrections(SegmentPath segment) {
        // Common turbulence correction term
        double deltaZT = 6e-3 * segment.dp / (segment.zsH + segment.zrH);
        
        // Source height correction (Eq. 2.5.19)
        double deltaZS = ALPHA0 * pow(segment.zsH / (segment.zsH + segment.zrH), 2) 
                         * (segment.dp * segment.dp / 2);
        segment.zsF = segment.zsH + deltaZS + deltaZT;
        
        // Receiver height correction
        double deltaZR = ALPHA0 * pow(segment.zrH / (segment.zsH + segment.zrH), 2) 
                         * (segment.dp * segment.dp / 2);
        segment.zrF = segment.zrH + deltaZR + deltaZT;
        
        // Test form for Fresnel heights
        segment.testFormF = segment.dp / (30 * (segment.zsF + segment.zrF));
    }

}
