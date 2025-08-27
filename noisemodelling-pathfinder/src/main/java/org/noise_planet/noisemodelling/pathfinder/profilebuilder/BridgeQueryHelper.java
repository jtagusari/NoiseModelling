/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.*;

/**
 * BridgeQueryHelper provides query operations for bridge geometry.
 * 
 * Primary Responsibilities:
 * - Point-in-bridge detection (above, below, on bridge)
 * - Bridge footprint containment checks
 * - Spatial relationship queries between points and bridge geometry
 * - Reflection relevance calculations for acoustic modeling
 * 
 * This class focuses on spatial queries and geometric relationships and does not handle:
 * - Bridge point data management (delegated to BridgePointManager)
 * - Detailed interpolation within triangles (delegated to BridgeTriangulation)
 * - 3D geometry construction (delegated to BridgeGeometryBuilder)
 */
public class BridgeQueryHelper {
    
    /** Bridge deck polygon geometry */
    private Polygon deckGeometry;
    
    /** Bridge triangulation for interpolation */
    private BridgeTriangulation triangulation;
    
    /** Default deck thickness in meters */
    private static final double DEFAULT_DECK_THICKNESS = 0.5;
    
    /**
     * Constructor.
     * @param deckGeometry Bridge deck polygon geometry
     * @param triangulation Bridge triangulation for interpolation
     */
    public BridgeQueryHelper(Polygon deckGeometry, BridgeTriangulation triangulation) {
        this.deckGeometry = deckGeometry;
        this.triangulation = triangulation;
    }
    
    /**
     * Check if a point is within the bridge footprint (2D projection).
     * Uses covers() instead of contains() to include boundary points.
     * @param point Point to check
     * @return true if point is within bridge footprint (including boundary)
     */
    public boolean isPointWithinBridgeFootprint(Coordinate point) {
        if (deckGeometry == null || point == null) return false;
        
        Point testPoint = deckGeometry.getFactory().createPoint(new Coordinate(point.x, point.y));
        // Use covers() instead of contains() to include boundary points
        return deckGeometry.covers(testPoint);
    }

    /**
     * Check if a sound source is positioned above the bridge.
     * @param pointGeometry Point coordinate (absolute z value)
     * @return true if source is above bridge
     */
    public boolean isPointAboveBridge(Coordinate pointGeometry) {
        if (deckGeometry == null || triangulation == null) return false;
        
        // Check if point is within bridge footprint first
        if (!isPointWithinBridgeFootprint(pointGeometry)) {
            return false;
        }
        
        double deckAbsoluteHeight = triangulation.getDeckHeightAtPoint(pointGeometry);
        
        return !Double.isNaN(deckAbsoluteHeight) && 
               pointGeometry.z >= deckAbsoluteHeight;
    }

    /**
     * Check if a sound source is positioned below the bridge.
     * @param pointGeometry Point coordinate (absolute z value)
     * @return true if source is below bridge deck
     */
    public boolean isPointBelowBridge(Coordinate pointGeometry) {
        if (deckGeometry == null || triangulation == null) return false;
        
        // Check if point is within bridge footprint first
        if (!isPointWithinBridgeFootprint(pointGeometry)) {
            return false;
        }
        
        double deckAbsoluteHeight = triangulation.getDeckHeightAtPoint(pointGeometry);
        double effectiveThickness = getEffectiveDeckThickness(pointGeometry);
        
        return !Double.isNaN(deckAbsoluteHeight) &&
               pointGeometry.z < (deckAbsoluteHeight - effectiveThickness);
    }

    /**
     * Check if a point is located on this bridge.
     * This method is designed for structural noise calculations and includes sources
     * that are in direct contact with or very close to the bridge deck.
     * @param pointGeometry Point source coordinate
     * @param tolerance Tolerance in meters for structural coupling
     * @return true if point source is on bridge deck (with tolerance for structural coupling)
     */
    public boolean isPointOnBridge(Coordinate pointGeometry, double tolerance) {
        if (deckGeometry == null || triangulation == null) return false;
        
        // Check if point is within bridge footprint first
        if (!isPointWithinBridgeFootprint(pointGeometry)) {
            return false;
        }
        
        double deckAbsoluteHeight = triangulation.getDeckHeightAtPoint(pointGeometry);
        
        return !Double.isNaN(deckAbsoluteHeight) && 
               Math.abs(pointGeometry.z - deckAbsoluteHeight) <= tolerance;
    }

    /**
     * Check if a point is located on this bridge with default tolerance.
     * This method is designed for structural noise calculations and includes sources
     * that are in direct contact with or very close to the bridge deck.
     * @param pointGeometry Point source coordinate
     * @return true if point source is on bridge deck (with tolerance for structural coupling)
     */
    public boolean isPointOnBridge(Coordinate pointGeometry) {
        return isPointOnBridge(pointGeometry, 2.0);
    }
    
    /**
     * Check if this bridge is relevant for reflection calculation between source and receiver.
     * @param sourcePos Source position
     * @param receiverPos Receiver position
     * @param maxReflectionDistance Maximum reflection distance
     * @return true if bridge could create reflections for this source-receiver pair
     */
    public boolean isRelevantForReflection(Coordinate sourcePos, Coordinate receiverPos, double maxReflectionDistance) {
        if (deckGeometry == null) {
            return false;
        }
        
        // First check if source is below bridge (required for reflection)
        if (!isPointBelowBridge(sourcePos)) {
            return false;
        }
        
        // Create line segment between source and receiver
        GeometryFactory factory = new GeometryFactory();
        LineSegment sourceReceiverLine = new LineSegment(sourcePos, receiverPos);
        
        // Check if bridge deck intersects with the source-receiver line vicinity
        double distanceToBridge = deckGeometry.distance(sourceReceiverLine.toGeometry(factory));

        return distanceToBridge <= maxReflectionDistance;
    }
    
    /**
     * Get the effective deck thickness used for calculations.
     * @param point Point coordinate to get deck thickness for
     * @return Effective deck thickness, or default if not available
     */
    private double getEffectiveDeckThickness(Coordinate point) {
        if (triangulation != null) {
            double thickness = triangulation.getDeckThicknessAtPoint(point);
            return Double.isNaN(thickness) ? DEFAULT_DECK_THICKNESS : thickness;
        }
        return DEFAULT_DECK_THICKNESS;
    }
    
    /**
     * Get the deck geometry envelope for spatial indexing.
     * @return 2D envelope of the bridge deck, or empty envelope if no geometry
     */
    public Envelope getEnvelope2D() {
        return deckGeometry != null ? deckGeometry.getEnvelopeInternal() : new Envelope();
    }
    
    /**
     * Get the deck geometry.
     * @return Bridge deck polygon geometry
     */
    public Geometry getGeometry() {
        return deckGeometry;
    }
    
    /**
     * Update the deck geometry and related components.
     * @param deckGeometry New deck geometry
     * @param triangulation New triangulation
     */
    public void updateGeometry(Polygon deckGeometry, BridgeTriangulation triangulation) {
        this.deckGeometry = deckGeometry;
        this.triangulation = triangulation;
    }
}
