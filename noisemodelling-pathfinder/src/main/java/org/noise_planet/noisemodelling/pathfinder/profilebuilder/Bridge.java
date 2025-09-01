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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridge class represents a bridge structure for acoustic calculations.
 * This class serves as the main coordinator that delegates responsibilities to specialized components:
 * 
 * Responsibility Distribution:
 * - BridgePointManager: Manages bridge point collections and height interpolation
 * - BridgeGeometryBuilder: Creates 3D deck geometry and edge structures
 * - BridgeTriangulation: Handles triangular interpolation for point-in-polygon operations
 * - BridgeQueryHelper: Performs spatial queries and point position determinations
 * 
 * Acoustic Capabilities:
 * - Sound diffraction at bridge edges for sources located above the bridge
 * - Mirror image sources from back surface reflections
 * - Virtual sources at bridge bottom for structural noise propagation
 * - Bridge geometry management through integrated components
 */
public class Bridge extends Obstruction {

    public enum GirderType {
        STEEL_BOX,
        STEEL_PLATE,
        CONCRETE_BOX,
        CONCRETE_PLATE,
        CONCRETE_HOLLOW_SLAB
    }

    public enum SlabType {
        CONCRETE,
        STEEL
    }

    private GirderType girderType = null;
    private SlabType slabType = null;
    
    /** Manager for bridge points collection and operations */
    private BridgePointManager pointManager;
    
    /** Builder for creating bridge deck geometry */
    private BridgeGeometryBuilder geometryBuilder;
    
    /** Triangulation handler for interpolation operations */
    private BridgeTriangulation triangulation;
    
    /** Query helper for spatial operations */
    private BridgeQueryHelper queryHelper;
    
    /** Bridge deck geometry polygon with Z coordinates */
    private Polygon deckGeometry;



    /** List of bridge edges for diffraction calculations. */
    private List<LineString> edges = new ArrayList<>();
    
    /** Primary key of the bridge in the database. */
    private long primaryKey = -1;
        
    /**
     * Create Bridge instances from a list of BridgePoints grouped by their bridgePrimaryKey.
     * This method groups the provided BridgePoints by their bridge primary key and creates
     * a Bridge instance for each group.
     * 
     * @param bridgePoints List of bridge points to be grouped and converted to Bridges
     * @param defaultAlphas Default absorption coefficients by frequency band
     * @return List of Bridge instances, one for each unique bridgePrimaryKey
     */
    public static List<Bridge> createBridgesFromPoints(List<BridgePoint> bridgePoints, 
                                                      List<Double> defaultAlphas) {
        List<Bridge> bridges = new ArrayList<>();
        
        if (bridgePoints == null || bridgePoints.isEmpty()) {
            return bridges;
        }
        
        // Group bridge points by bridgePrimaryKey
        Map<Long, List<BridgePoint>> bridgePointGroups = new HashMap<>();
        for (BridgePoint point : bridgePoints) {
            long bridgePrimaryKey = point.getBridgePrimaryKey();
            bridgePointGroups.computeIfAbsent(bridgePrimaryKey, k -> new ArrayList<>()).add(point);
        }
        
        // Create Bridge instances for each group
        for (Map.Entry<Long, List<BridgePoint>> entry : bridgePointGroups.entrySet()) {
            long bridgePrimaryKey = entry.getKey();
            List<BridgePoint> pointsForBridge = entry.getValue();
            
            // Create Bridge instance using the bridge points
            Bridge bridge = new Bridge(pointsForBridge, defaultAlphas, bridgePrimaryKey);
            bridges.add(bridge);
        }
        
        return bridges;
    }

    /**
     * Create Bridge instances from a list of BridgePoints grouped by their bridgePrimaryKey
     * with default values.
     * 
     * @param bridgePoints List of bridge points to be grouped and converted to Bridges
     * @return List of Bridge instances, one for each unique bridgePrimaryKey
     */
    public static List<Bridge> createBridgesFromPoints(List<BridgePoint> bridgePoints) {
        return createBridgesFromPoints(bridgePoints, null);
    }

    /**
     * Main constructor using bridge points.
     * @param bridgePoints List of bridge points defining the bridge geometry
     * @param alphas Absorption coefficients by frequency band
     * @param primaryKey Primary key in database
     */
    public Bridge(List<BridgePoint> bridgePoints, List<Double> alphas, long primaryKey) {
        super();
        this.primaryKey = primaryKey;
        
        // Initialize components
        this.pointManager = new BridgePointManager(bridgePoints);
        this.geometryBuilder = new BridgeGeometryBuilder();
        this.triangulation = new BridgeTriangulation();
        // Create query helper and provide builders so it can generate footprint when needed
        this.queryHelper = new BridgeQueryHelper(null, null, triangulation, pointManager, geometryBuilder);
        

        // Set absorption coefficients
        if (alphas != null) {
            setAlpha(alphas);
        }
    }

    /**
     * Main constructor.
     * @param deckGeometry 3D bridge deck geometry with Z coordinates representing deck height
     * @param alphas Absorption coefficients by frequency band
     * @param primaryKey Primary key in database
     */
    public Bridge(Polygon deckGeometry, List<Double> alphas, long primaryKey) {
        super();
        this.primaryKey = primaryKey;
        this.deckGeometry = deckGeometry;
        
        // Initialize components
        this.pointManager = new BridgePointManager();
        this.geometryBuilder = new BridgeGeometryBuilder();
        this.triangulation = new BridgeTriangulation();
        
        // Let BridgeQueryHelper generate 2D footprint from deckGeometry when needed
        this.queryHelper = new BridgeQueryHelper(deckGeometry, null, triangulation);
        
        // Initialize edges if geometry is provided
        if (deckGeometry != null) {
            this.edges = geometryBuilder.createEdges(deckGeometry);
        }
        
        // Set absorption coefficients
        if (alphas != null) {
            setAlpha(alphas);
        }
    }

    /**
     * Get the bridge deck absolute elevation at a specific point using triangulation interpolation.
     * Works for points inside the bridge deck polygon and on the boundary.
     * @param point Point to get deck elevation for
     * @return Absolute deck elevation at the specified point, or Double.NaN if point is outside polygon
     */
    public double getDeckHeightAtPoint(Coordinate point) {
        return triangulation.getDeckHeightAtPoint(point);
    }

    /**
     * Get the deck thickness at a specific point.
     * @param point Point to get deck thickness for
     * @return Deck thickness at the specified point, or NaN if not available
     */
    public double getDeckThicknessAtPoint(Coordinate point) {
        return triangulation.getDeckThicknessAtPoint(point);
    }

    /**
     * Get the barrier height at a specific point.
     * @param point Point to get barrier height for
     * @return Barrier height at the specified point
     */
    public double getBarrierHeightAtPoint(Coordinate point) {
        return triangulation.getBarrierHeightAtPoint(point);
    }

    /**
     * Create bridge deck geometry from bridge points.
     * @param profileBuilder Profile builder for ground height calculation
     * @return Created deck geometry polygon
     */
    public void createDeckGeometry(ProfileBuilder profileBuilder) {
        // Create deck geometry using the builder
        this.deckGeometry = geometryBuilder.createDeckGeometry(pointManager, profileBuilder);
        
        if (deckGeometry != null) {
            // Create edge points for triangulation from the point manager
            BridgePointManager edgePointManager = new BridgePointManager(BridgePointManager.SortOrder.COUNTER_CLOCKWISE);
            edgePointManager.addBridgePoints(geometryBuilder.createBridgeEdgePoints(pointManager, profileBuilder, BridgeGeometryBuilder.Direction.RIGHT));
            edgePointManager.addBridgePoints(geometryBuilder.createBridgeEdgePoints(pointManager, profileBuilder, BridgeGeometryBuilder.Direction.LEFT));
            
            // Create triangulation for interpolation
            triangulation.triangulateGeometry(edgePointManager.getBridgePoints());
            
            // Create edges for acoustic calculations
            this.edges = geometryBuilder.createEdges(deckGeometry);
            
            // Update query helper with new geometry; BridgeQueryHelper will generate 2D footprint
            queryHelper.updateGeometry(deckGeometry, null, triangulation);
        }
    }

    // ...removed removeZFromPolygon: footprint generation moved to BridgeQueryHelper

    /**
     * Calculate diffraction points at bridge edges for sources above the bridge.
     * @param sourcePosition Position of the sound source (absolute coordinates)
     * @param receiverPosition Position of the receiver (absolute coordinates)
     * @param profileBuilder Profile builder for ground height calculation (can be null)
     * @return List of diffraction points
     */
    public List<Coordinate> calculateDiffractionPoints(Coordinate sourcePosition, Coordinate receiverPosition, ProfileBuilder profileBuilder) {
        List<Coordinate> diffractionPoints = new ArrayList<>();
        
        // Check if source is above the bridge
        if (!isPointAboveBridge(sourcePosition)) {
            return diffractionPoints;
        }
        
        // Find relevant edges for diffraction
        for (LineString edge : edges) {
            Coordinate edgeStart = edge.getStartPoint().getCoordinate();
            Coordinate edgeEnd = edge.getEndPoint().getCoordinate();
            
            // Use the Z coordinates from the 3D deck geometry (absolute elevations)
            double startZ = !Double.isNaN(edgeStart.z) ? edgeStart.z : getDeckHeightAtPoint(edgeStart);
            double endZ = !Double.isNaN(edgeEnd.z) ? edgeEnd.z : getDeckHeightAtPoint(edgeEnd);
            
            Coordinate elevatedStart = new Coordinate(edgeStart.x, edgeStart.y, startZ);
            Coordinate elevatedEnd = new Coordinate(edgeEnd.x, edgeEnd.y, endZ);
            
            // Check if this edge can cause diffraction
            if (canCauseDiffraction(sourcePosition, receiverPosition, elevatedStart, elevatedEnd)) {
                // Find the diffraction point on this edge
                Coordinate diffractionPoint = findDiffractionPoint(sourcePosition, receiverPosition, elevatedStart, elevatedEnd);
                if (diffractionPoint != null) {
                    diffractionPoints.add(diffractionPoint);
                }
            }
        }
        
        return diffractionPoints;
    }

    /**
     * Calculate mirror image source positions for back surface reflections.
     * @param sourcePosition Original source position
     * @return List of mirror image source positions
     */
    public List<Coordinate> calculateMirrorImageSources(Coordinate sourcePosition) {
        List<Coordinate> mirrorSources = new ArrayList<>();
        
        if (deckGeometry == null || !isPointBelowBridge(sourcePosition)) {
            return mirrorSources;
        }
        
        // Calculate mirror image above the bridge deck (reflection from bridge bottom surface)
        double bridgeBottom = getDeckHeightAtPoint(sourcePosition) - getDeckThicknessAtPoint(sourcePosition);
        double mirrorZ = 2 * bridgeBottom - sourcePosition.z;
        
        Coordinate mirrorSource = new Coordinate(sourcePosition.x, sourcePosition.y, mirrorZ);
        mirrorSources.add(mirrorSource);
        
        return mirrorSources;
    }

    /**
     * Check if this bridge is relevant for reflection calculation between source and receiver.
     * @param sourcePos Source position
     * @param receiverPos Receiver position
     * @param maxReflectionDistance Maximum reflection distance
     * @return true if bridge could create reflections for this source-receiver pair
     */
    public boolean isRelevantForReflection(Coordinate sourcePos, Coordinate receiverPos, double maxReflectionDistance) {
        return queryHelper.isRelevantForReflection(sourcePos, receiverPos, maxReflectionDistance);
    }


    /**
     * Generate virtual source position at bridge bottom for structural noise propagation.
     * Virtual source is created at the bridge bottom surface to model structural sound transmission
     * through the bridge structure and re-radiation from the bottom surface.
     * This method only generates virtual source when the original source is located on the bridge deck,
     * as structural noise requires the source to be in direct contact with the bridge structure.
     * @param sourcePosition Original source position
     * @return Virtual source position at bridge bottom, or null if conditions not met
     */
    public Coordinate generateVirtualSourceAtBridgeBottom(Coordinate sourcePosition) {
        // Only generate virtual sources if source is on the bridge deck
        // This represents structural sound transmission through the bridge structure
        if (deckGeometry == null || !isPointOnBridge(sourcePosition)) {
            return null;
        }
        
        // Calculate bridge bottom height using absolute elevation
        double bridgeBottom = getDeckHeightAtPoint(sourcePosition) - getDeckThicknessAtPoint(sourcePosition);

        // Use unified method with comprehensive strategy for bridge bottom
        return new Coordinate(sourcePosition.x, sourcePosition.y, bridgeBottom);
    }

    /**
     * Check if a sound source is positioned above the bridge.
     * @param pointGeometry Point coordinate (absolute z value)
     * @return true if source is above bridge
     */
    private boolean isPointAboveBridge(Coordinate pointGeometry) {
        return queryHelper.isPointAboveBridge(pointGeometry);
    }

    /**
     * Check if a sound source is positioned below the bridge.
     * @param pointGeometry Point coordinate (absolute z value)
     * @return true if source is below bridge deck
     */
    private boolean isPointBelowBridge(Coordinate pointGeometry) {
        return queryHelper.isPointBelowBridge(pointGeometry);
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
        return queryHelper.isPointOnBridge(pointGeometry, tolerance);
    }

    /**
     * Check if a point is located on this bridge.
     * This method is designed for structural noise calculations and includes sources
     * that are in direct contact with or very close to the bridge deck.
     * @param pointGeometry Point source coordinate
     * @return true if point source is on bridge deck (with tolerance for structural coupling)
     */
    public boolean isPointOnBridge(Coordinate pointGeometry) {
        return queryHelper.isPointOnBridge(pointGeometry);
    }

    /**
     * Check if a point is within the bridge footprint (2D projection).
     * Uses covers() instead of contains() to include boundary points.
     * @param point Point to check
     * @return true if point is within bridge footprint (including boundary)
     */
    public boolean isPointWithinBridgeFootprint(Coordinate point) {
        return queryHelper.isPointWithinBridgeFootprint(point);
    }

    public boolean intersects(Geometry geom) {
        return deckGeometry.intersects(geom);
    }


    /**
     * Check if an edge can cause diffraction between source and receiver.
     * Performs a simplified geometric check using line intersection with buffered edge.
     * @param source Source coordinate
     * @param receiver Receiver coordinate  
     * @param edgeStart Start coordinate of the edge
     * @param edgeEnd End coordinate of the edge
     * @return true if the edge can cause diffraction between source and receiver
     */
    private boolean canCauseDiffraction(Coordinate source, Coordinate receiver, Coordinate edgeStart, Coordinate edgeEnd) {
        // Simplified geometric check - in practice would use more sophisticated fresnel zone analysis
        GeometryFactory factory = GeometryFactoryProvider.SHARED;
        LineString directPath = factory.createLineString(new Coordinate[]{source, receiver});
        LineString edge = factory.createLineString(new Coordinate[]{edgeStart, edgeEnd});

        return directPath.intersects(edge.buffer(1.0)); // 1 meter tolerance
    }

    /**
     * Find the optimal diffraction point on an edge.
     * Currently returns the midpoint of the edge as a simplified implementation.
     * @param source Source coordinate
     * @param receiver Receiver coordinate
     * @param edgeStart Start coordinate of the edge
     * @param edgeEnd End coordinate of the edge
     * @return Optimal diffraction point on the edge
     */
    private Coordinate findDiffractionPoint(Coordinate source, Coordinate receiver, Coordinate edgeStart, Coordinate edgeEnd) {
        // Simplified - return midpoint of edge
        // In practice, would find point minimizing total path length
        return new Coordinate(
            (edgeStart.x + edgeEnd.x) / 2,
            (edgeStart.y + edgeEnd.y) / 2,
            (edgeStart.z + edgeEnd.z) / 2
        );
    }

    // Getters and Setters
    
    public Envelope getEnvelope2D() {
        return queryHelper.getEnvelope2D();
    }

    /**
     * Get the 3D deck geometry of the bridge (may be null if not created).
     * @return deck geometry polygon (with Z) or null
     */
    public Geometry getDeckGeometry() {
        return deckGeometry;
    }

    /**
     * Get the 2D footprint geometry of the bridge (projection).
     * If a footprint was explicitly created it is returned. Otherwise if a deck
     * geometry exists a 2D copy without Z is produced and returned.
     * @return footprint polygon (2D) or null
     */
    public Geometry getFootprintGeometry() {
    return queryHelper.getFootprintGeometry();
    }

    /**
     * Get the bridge point manager.
     * @return Bridge point manager containing bridge points
     */
    public BridgePointManager getPointManager() {
        return pointManager;
    }

    public List<LineString> getEdges() {
        return new ArrayList<>(edges);
    }

    public long getPrimaryKey() {
        return primaryKey;
    }

    /**
     * Add a bridge point to the collection.
     * @param bridgePoint Bridge point to add
     */
    public void addBridgePoint(BridgePoint bridgePoint) {
        pointManager.addBridgePoint(bridgePoint);
    }
    
    /**
     * Remove a bridge point from the collection by primary key.
     * @param pointPrimaryKey Primary key of the bridge point to remove
     * @return true if the point was removed
     */
    public boolean removeBridgePoint(long pointPrimaryKey) {
        return pointManager.removeBridgePoint(pointPrimaryKey);
    }
    
    /**
     * Get the list of bridge points (read-only).
     * @return Copy of bridge points list
     */
    public List<BridgePoint> getBridgePoints() {
        return pointManager.getBridgePoints();
    }
    
    /**
     * Get the number of bridge points.
     * @return Number of bridge points
     */
    public int getBridgePointCount() {
        return pointManager.size();
    }
    
    /**
     * Check if the bridge points collection is empty.
     * @return true if empty
     */
    public boolean hasBridgePoints() {
        return !pointManager.isEmpty();
    }


    public GirderType getGirderType() {
        return girderType;
    }

    public void setGirderType(GirderType girderType) {
        this.girderType = girderType;
    }

    public SlabType getSlabType() {
        return slabType;
    }

    public void setSlabType(SlabType slabType) {
        this.slabType = slabType;
    }
    

    /**
     * Generate sources on the bridge deck.
     * This method creates sources on the bridge deck.
     * 
     * @param sourcePos Source position
     * @param sourceHeight Source height on the deck
     * @return List of virtual source positions
     */
    public List<Coordinate> generateSourcesOnBridge(Coordinate sourcePos, double sourceHeight) {
        List<Coordinate> onSources = new ArrayList<>();
        
        if (deckGeometry == null || !queryHelper.isPointBelowBridge(sourcePos)) {
            return onSources; // No sources if source not below bridge
        }
        
        // Create source positions on the bridge deck
        double deckHeight = triangulation.getDeckHeightAtPoint(sourcePos);
        if (!Double.isNaN(deckHeight)) {
            // Place source on the deck
            double absoluteSourceHeight = deckHeight + sourceHeight;

            Coordinate onSource = new Coordinate(sourcePos.x, sourcePos.y, absoluteSourceHeight);
            onSources.add(onSource);
        }

        return onSources;
    }    

    /**
     * Generate sources on the bridge deck.
     * This method creates sources on the bridge deck.
     * 
     * @param sourcePos Source position
     * @return List of virtual source positions
     */
    public List<Coordinate> generateSourcesOnBridge(Coordinate sourcePos) {
        return generateSourcesOnBridge(sourcePos, 0.05);
    }

    /**
     * Generate virtual sources at bridge bottom for structural noise propagation.
     * This method creates virtual sources below the bridge deck to simulate sound 
     * transmission through the bridge structure.
     * 
     * @param sourcePos Source position
     * @return List of virtual source positions
     */
    public List<Coordinate> generateVirtualSourcesAtBridgeBottom(Coordinate sourcePos) {
        List<Coordinate> virtualSources = new ArrayList<>();
        
        if (deckGeometry == null || !queryHelper.isPointOnBridge(sourcePos)) {
            return virtualSources; // No virtual sources if source not on bridge
        }
        
        // Create virtual source positions below the bridge deck
        // For simplicity, we create one virtual source directly below the original source
        double deckHeight = getDeckHeightAtPoint(sourcePos);
        if (!Double.isNaN(deckHeight)) {
            // Place virtual source 1 millimeter below deck bottom (considering deck thickness)
            double virtualSourceHeight = deckHeight - (getDeckThicknessAtPoint(sourcePos) + 0.001);
            
            Coordinate virtualSource = new Coordinate(sourcePos.x, sourcePos.y, virtualSourceHeight);
            virtualSources.add(virtualSource);
        }
        
        return virtualSources;
    }

    
    /**
     * Generate mirror image sources by the bridge bottom.
     * This method creates virtual sources above the bridge deck to simulate sound 
     * reflection by the bridge plane.
     * 
     * @param sourcePos Source position
     * @return List of virtual source positions
     */
    public List<Coordinate> generateMirrorImageSourcesByBridge(Coordinate sourcePos) {
        List<Coordinate> mirrorSources = new ArrayList<>();
        
        if (deckGeometry == null || !queryHelper.isPointBelowBridge(sourcePos)) {
            return mirrorSources; // No mirror sources if source not below bridge
        }
        
        // Create mirror source positions above the bridge deck
        double deckHeight = getDeckHeightAtPoint(sourcePos);
        if (!Double.isNaN(deckHeight)) {
            // Place mirror source 
            double bridgeBottom = deckHeight - getDeckThicknessAtPoint(sourcePos);
            double mirrorSourceHeight = sourcePos.z + 2 * (bridgeBottom - sourcePos.z);

            Coordinate mirrorSource = new Coordinate(sourcePos.x, sourcePos.y, mirrorSourceHeight);
            mirrorSources.add(mirrorSource);
        }

        return mirrorSources;
    }

}