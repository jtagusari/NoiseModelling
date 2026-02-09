package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.path.BridgeRelationship;
import org.noise_planet.noisemodelling.pathfinder.path.BridgeRelationship.RelationType;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointBridgeWall.WallDirection;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder.IntersectionType;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.RTreeUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for managing bridges and building the spatial index
 * used during profile construction.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Store bridge objects and expose basic accessors (list/count/lookup).</li>
 *   <li>Build an STRtree spatial index over bridge footprints for fast spatial queries.</li>
 *   <li>Export bridge edges as processed wall facets (via {@link #exportFacetsToProcessedWalls}) so
 *       bridge geometry participates in profile intersection logic in the same way
 *       buildings/walls do.</li>
 *   <li>Provide helpers to detect and handle bridge-specific intersections during
 *       profile construction (see {@link #createBridgeCutPointAndCheckObstruction}).</li>
 * </ul>
 *
 * <p>This service encapsulates bridge-specific handling so that {@link ProfileBuilder}
 * and other profile construction code can treat bridges uniformly with walls/buildings
 * while keeping bridge behaviour isolated and testable.</p>
 */
public class BridgeService implements FrequencyInitializable, ElevationComputable, ClearableService, ProcessedFacetsExportable {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeService.class);
    private int nodeCapacity = 5;

    private final List<Bridge> bridges = new ArrayList<>();
    private STRtree bridgeTree;
    private final GeometryFactory geometryFactory;

    public BridgeService(int nodeCapacity) {
        this.nodeCapacity = nodeCapacity;
        this.geometryFactory = GeometryFactoryProvider.SHARED;
    }

    /**
     * No-arg constructor used by callers that rely on a default node capacity.
     * Defaults to a small internal node capacity so STRtrees are usable.
     */
    public BridgeService() {
        this(5);
    }

    /**
     * Constructor accepting a custom GeometryFactory for consistency with other services.
     * (BridgeService itself does not store the factory but the constructor keeps signature compatibility.)
     */
    public BridgeService(int nodeCapacity, org.locationtech.jts.geom.GeometryFactory geometryFactory) {
        this.nodeCapacity = nodeCapacity;
        this.geometryFactory = geometryFactory != null ? geometryFactory : GeometryFactoryProvider.SHARED;
    }

    /**
     * Add a bridge to the in-memory list. The bridge will be included when
     * building the spatial index via {@link #exportFacetsToProcessedWalls}.
     *
     * @param bridge Bridge instance to store
     */
    public void addBridge(Bridge bridge) {
        this.bridges.add(bridge);
    }

    /**
     * Return the list of stored bridges. The returned list is mutable and
     * intended for read-only consumption by callers.
     *
     * @return list of bridges
     */
    public List<Bridge> getBridges() {
    return new LoggingUnmodifiableList<>(bridges, "BridgeService.bridges");
    }

    /**
     * Get the number of stored bridges.
     *
     * @return count of bridges in the service
     */
    public int getBridgeCount() {
        return bridges.size();
    }

    /**
     * Get a bridge by its index in the storage list.
     *
     * @param id zero-based index of the bridge
     * @return bridge at the specified index
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public Bridge getBridge(int id) {
        return bridges.get(id);
    }

    /**
     * Find a bridge by its primary key.
     *
     * @param pk primary key of the bridge to find
     * @return bridge with matching primary key, or {@code null} if not found
     */
    public Bridge getBridgeByPk(long pk) {
        return bridges.stream().filter(b -> b.getPrimaryKey() == pk).findFirst().orElse(null);
    }

    public int getBridgeIdByPk(long pk) {
        for (int i = 0; i < bridges.size(); i++) {
            if (bridges.get(i).getPrimaryKey() == pk) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Clear all stored bridges and reset the service state.
     */

    public void clear() {
        bridges.clear();
    }

    /**
     * Build the bridge spatial index and export bridge edges as processed wall
     * facets into the provided {@link ProcessedWallService}.
     *
     * <p>This method performs these steps:
     * <ol>
     *   <li>Create/recreate the internal STRtree index for stored bridge footprints.</li>
     *   <li>For each bridge, insert the footprint envelope into the index.</li>
     *   <li>Iterate bridge edge geometries and convert each edge segment into a
     *       {@link Wall} processed-facet which is then added to {@code ProcessedWallService} so
     *       profile intersection logic treats bridge edges like wall facets.</li>
     * </ol>
     *
    * @param processedWallService Destination service that will receive processed wall facets
    * @param factory GeometryFactory used to build any temporary geometries
    *
    * <p>Contract with {@link ProcessedWallService}:
    * <ul>
    *   <li>This method inserts processed wall facets into {@code processedWallService}
    *       using {@link ProcessedWallService#addProcessedWall}.</li>
    *   <li>After all services have exported their facets callers must call
    *       {@link ProcessedWallService#buildProcessedWallRtree()} before issuing
    *       any queries against the processed-wall index.</li>
    * </ul>
     */
    @Override
    public void exportFacetsToProcessedWalls(ProcessedWallService processedWallService, GeometryFactory factory) {
        bridgeTree = new STRtree(nodeCapacity);
        for (int j = 0; j < bridges.size(); j++) {
            Bridge bridge = bridges.get(j);
            Geometry bridgeGeom = bridge.getFootprintGeometry();
            if (bridgeGeom != null) {
                bridgeTree.insert(bridgeGeom.getEnvelopeInternal(), j);
            }
            Coordinate[] coords = bridge.getEdge().getCoordinates();
            for (int i = 0; i < coords.length - 1; i++) {
                LineSegment lineSegment = new LineSegment(coords[i], coords[i + 1]);
                Wall w = new Wall(lineSegment, j, ProfileBuilder.IntersectionType.BRIDGE).setProcessedWallIndex(processedWallService.getProcessedWalls().size());
                w.setPrimaryKey(bridge.getPrimaryKey());
                w.copyAlphas(bridge);
                processedWallService.addProcessedWall(w, factory);
            }
        }
        bridgeTree.build();
    }

    @Override
    public void exportFacetsToProcessedWalls(ProcessedWallService processedWallService) {
        exportFacetsToProcessedWalls(processedWallService, this.geometryFactory);
    }


    /**
     * Get the spatial index (R-tree) for bridge footprints.
     * 
     * <p>The R-tree is built during {@link #exportFacetsToProcessedWalls} and contains
     * envelope information for all stored bridge footprints, enabling fast spatial queries.
     *
     * @return STRtree spatial index, or {@code null} if not yet built
     */
    public STRtree getBridgeRtree() {
        return bridgeTree;
    }

    /**
     * Return bridges that intersect the given envelope.
     *
     * @param env query envelope
     * @return list of bridges intersecting the envelope
     */
    public List<Bridge> getBridgesIn(Envelope env) {
        List<Bridge> list = new ArrayList<>();
        List<Integer> indexes = RTreeUtils.query(getBridgeRtree(), env);
        for (int i : indexes) {
            Bridge b = getBridges().get(i);
            list.add(b);
        }
        return list;
    }

    /**
     * Compute or update bridge deck geometry (3D polygon) for all stored bridges.
     * <p>
     * For bridges lacking a deck polygon or Z coordinates this method will delegate
     * to {@link Bridge#createDeckGeometry(ProfileBuilder)} which uses the provided
     * {@code ProfileBuilder} to sample ground heights when building the deck.
     *
     * @param profileBuilder provider of topography/elevation information
     */
    @Override
    public void computeElevations(ProfileBuilder profileBuilder) {
        for (Bridge b : bridges) {
            Geometry deck = b.getDeckGeometry();
            if (deck == null || Double.isNaN(deck.getCoordinate().z)) {
                b.createDeckGeometry(profileBuilder);
            }
        }
    }

    /**
     * Initialize frequency-dependent arrays on all managed bridges.
     * @param exactFrequencyArray list of exact frequency values used to compute alphas
     */
    public void initializeFrequencyDependentData(List<Double> exactFrequencyArray) {
        if (exactFrequencyArray == null) {
            LOGGER.warn("BridgeService.initializeFrequencyDependentData: exactFrequencyArray is null");
            return;
        }
        LOGGER.debug("BridgeService.initializeFrequencyDependentData: called with exactFrequencyArray.size={}", exactFrequencyArray.size());
        for (Bridge b : bridges) {
            b.initialize(exactFrequencyArray);
        }
    }

    /**
     * Calculate bridge mirror cut point for mirror source scenarios.
     * 
     * <p>This method computes the optimal diffraction point on a bridge edge
     * for mirror source propagation by:
     * <ol>
     *   <li>Identifying if the receiver is within the bridge footprint</li>
     *   <li>Computing diffraction points for all bridge edge segments</li>
     *   <li>Selecting the diffraction point with minimum distance</li>
     *   <li>Creating a CutPointBridgeWall with appropriate mirror relaxation</li>
     * </ol>
     *
     * @param profile the cut profile containing source and receiver information
     * @return bridge cut point configured for mirror propagation
     * @throws IllegalStateException if no valid diffraction point is found
     */
    public CutPointBridgeWall calculateFirstBridgeCutpoint(CutProfile profile, PropagationType propagationType) {
        CutPointSource src = profile.getSource();
        CutPointReceiver rcv = profile.getReceiver(); 
        long bridgePkTarget = -1;
        CutPointBridgeWall.WallDirection wallDirection = null;
        if (propagationType == PropagationType.IMAGINARY_SOURCE_TO_UPPER_RECEIVER) {
            bridgePkTarget = src.getBridgeRelationship().getBridgePkAbove();
            wallDirection = CutPointBridgeWall.WallDirection.DOWNWARD;
        } else if (propagationType == PropagationType.ACTUAL_SOURCE_TO_LOWER_RECEIVER) {
            bridgePkTarget = src.getBridgeRelationship().getBridgePkOn();
            wallDirection = CutPointBridgeWall.WallDirection.OTHER;
        } else {
            throw new IllegalStateException("Unsupported propagation type for bridge cut point calculation: " + propagationType);
        }
        int bridgeId = getBridgeIdByPk(bridgePkTarget);
        Bridge bridge = this.getBridgeByPk(bridgePkTarget);

        List<DiffractionPoint> diffractionPoints = new ArrayList<>();
        Coordinate[] edgeCoords = bridge.getEdge().getCoordinates();
        for (int i = 0; i < edgeCoords.length - 1; i++) {
            LineSegment diffractionSegment = new LineSegment(edgeCoords[i], edgeCoords[i + 1]);
            DiffractionPoint dp = new DiffractionPoint(src.getCoordinate(), rcv.getCoordinate(), diffractionSegment, geometryFactory);
            diffractionPoints.add(dp);
        }

        DiffractionPoint minDistanceDp = diffractionPoints.stream()
            .filter(dp -> dp.getDiffractionPoint2D() != null)  
            .min(Comparator.comparingDouble(DiffractionPoint::getDiffractionDistance2D))
            .orElse(null);

        if (minDistanceDp == null) {
            throw new IllegalStateException("No valid diffraction point found for bridge mirror cut point.");
        }
        Coordinate intersection3D = minDistanceDp.getDiffractionPoint3D(bridge);

        CutPointBridgeWall bridgeCutPoint = new CutPointBridgeWall(bridgeId, intersection3D, minDistanceDp.getSegment(), new ArrayList<>(bridge.getAlphas()), wallDirection, bridgePkTarget);

        if (propagationType == PropagationType.ACTUAL_SOURCE_TO_LOWER_RECEIVER) {
            bridgeCutPoint.setMirrorRelax(true);
        }
        bridgeCutPoint.modifyIntersectionHeight(bridge);
        long bridgePkOn = src.getBridgeRelationship().getBridgePkOn();
        if (bridgePkOn >= 0) {
            bridgeCutPoint.setBridgeHeight(bridge.getDeckHeightAtPoint(intersection3D));
        }

        return bridgeCutPoint;
    }

    /**
     * Inner class representing a diffraction point calculation for bridge edge segments.
     * 
     * <p>This class calculates the optimal diffraction point on a bridge edge segment
     * for sound propagation from a source to receiver, supporting both direct
     * diffraction and mirror image diffraction scenarios.
     */
    private class DiffractionPoint{
        private final LineSegment seg;
        private Coordinate diffractionPoint2D = null;
        private double diffractionDistance2D;
        private final GeometryFactory geometryFactory;

        public DiffractionPoint(Coordinate source, Coordinate receiver, LineSegment segment, GeometryFactory geometryFactory) {
            this.seg = segment;
            this.geometryFactory = geometryFactory;
            
            calculate2DDiffractionMirror(source, receiver, seg);
        }

        /**
         * Calculate 2D diffraction point for direct propagation.
         * 
         * <p>Finds the optimal diffraction point on the segment by checking:
         * <ol>
         *   <li>Direct intersection between source-receiver line and segment</li>
         *   <li>If no intersection, selects the segment endpoint with shorter total path</li>
         * </ol>
         *
         * @param source source coordinate
         * @param receiver receiver coordinate  
         * @param seg bridge edge segment
         */
        private void calculate2DDiffraction(Coordinate source, Coordinate receiver, LineSegment seg){            
            Coordinate source2D = new Coordinate(source.x, source.y);
            Coordinate receiver2D = new Coordinate(receiver.x, receiver.y);
            LineSegment seg2D = new LineSegment(seg.p0.x, seg.p0.y, seg.p1.x, seg.p1.y);
            LineIntersector intersector = new RobustLineIntersector();
            intersector.computeIntersection(source2D, receiver2D, seg2D.p0, seg2D.p1);

            if (intersector.hasIntersection()) {
                this.diffractionPoint2D = new Coordinate(intersector.getIntersection(0));
                LineString srcToMirrorRcv = geometryFactory.createLineString(
                    new Coordinate[]{source2D, receiver2D}
                );
                this.diffractionDistance2D = srcToMirrorRcv.getLength();
                return;
            }

            LineString srcToRcvViaP0 = geometryFactory.createLineString(
                new Coordinate[]{source2D, seg2D.p0, receiver2D}
            );
            double lengthViaP0 = srcToRcvViaP0.getLength();

            LineString srcToRcvViaP1 = geometryFactory.createLineString(
                new Coordinate[]{source2D, seg2D.p1, receiver2D}
            );
            double lengthViaP1 = srcToRcvViaP1.getLength();

            if (lengthViaP0 < lengthViaP1) {
                this.diffractionPoint2D = new Coordinate(seg2D.p0);
                this.diffractionDistance2D = lengthViaP0;
                return;
            } else {
                this.diffractionPoint2D = new Coordinate(seg2D.p1);
                this.diffractionDistance2D = lengthViaP1;
                return;
            }
        }

        /**
         * Calculate 2D diffraction point for mirror source scenarios.
         * 
         * <p>Creates a mirror image of the receiver relative to the bridge edge segment
         * and then calculates diffraction using the mirror receiver position.
         *
         * @param source source coordinate
         * @param receiver receiver coordinate
         * @param seg bridge edge segment
         */
        private void calculate2DDiffractionMirror(Coordinate source, Coordinate receiver, LineSegment seg){
            Coordinate receiver2D = new Coordinate(receiver.x, receiver.y);
            LineSegment seg2D = new LineSegment(seg.p0.x, seg.p0.y, seg.p1.x, seg.p1.y);
            Coordinate mirrorReceiver = seg2D.reflect(receiver2D);
            calculate2DDiffraction(source, mirrorReceiver, seg);
        }

        public double getDiffractionDistance2D() {
            return diffractionDistance2D;
        }
        public Coordinate getDiffractionPoint2D() {
            return new Coordinate(diffractionPoint2D);
        }

        public LineSegment getSegment(){
            return new LineSegment(seg);
        }
        
        public Coordinate getDiffractionPoint3D(Bridge bridge) {
            double z = bridge.getDeckHeightAtPoint(diffractionPoint2D) - bridge.getDeckThicknessAtPoint(diffractionPoint2D);
            return new Coordinate(diffractionPoint2D.x, diffractionPoint2D.y, z);
        }
    }

    /**
     * Enumeration of propagation types for bridge-related sound calculations.
     * 
     * <p>These types classify different scenarios of sound propagation in the presence
     * of bridges, determining how noise calculations should be performed.
     */
    public enum PropagationType {
        /** Source is not related to any bridge structure */
        NOT_RELATED_TO_BRIDGE,
        /** Sound propagates from bridge to receiver outside bridge footprint */
        BRIDGE_TO_OUTSIDE_RECEIVER,
        /** Actual source on bridge propagating to receiver above deck level */
        ACTUAL_SOURCE_TO_UPPER_RECEIVER,
        /** Actual source on bridge propagating to receiver below deck level */
        ACTUAL_SOURCE_TO_LOWER_RECEIVER,
        /** Imaginary source under bridge propagating to receiver above deck level */
        IMAGINARY_SOURCE_TO_UPPER_RECEIVER,
        /** Imaginary source under bridge propagating to receiver below deck level */
        IMAGINARY_SOURCE_TO_LOWER_RECEIVER,
        /** TODO */
        /** Low source outside bridge propagating to receiver */
        LOW_OUTSIDE_SOURCE_TO_RECEIVER,
        /** TODO */
        /** High source outside bridge propagating to receiver */
        HIGH_OUTSIDE_SOURCE_TO_RECEIVER
    }

    /**
     * Determine the type of propagation scenario based on source type and receiver position.
     * 
     * <p>This method analyzes the source bridge properties and receiver position to
     * classify the propagation scenario, which affects how noise calculation is performed.
     * The classification considers:
     * <ul>
     *   <li>Whether the source is related to a bridge</li>
     *   <li>Source type (actual source on bridge, imaginary source under bridge, mirror source)</li>
     *   <li>Receiver position relative to bridge (inside/outside footprint, above/below deck)</li>
     * </ul>
     *
     * @param profile the cut profile containing source and receiver information
     * @return propagation type enum value describing the scenario
     * @throws IllegalStateException if bridge primary keys are invalid or source type is unsupported
     */
    public PropagationType checkPropagationType(CutProfile profile) {
        CutPointSource src = profile.getSource();
        BridgeRelationship srcProperty = src.getBridgeRelationship();
        if (srcProperty == null) {
            return PropagationType.NOT_RELATED_TO_BRIDGE;
        }

        RelationType srcType = srcProperty.getRelationType();
        if (srcType == RelationType.SOURCE_NOT_RELATED_TO_BRIDGE) {
            return PropagationType.NOT_RELATED_TO_BRIDGE;
        }

        if (srcProperty.getBridgePkOn() < 0 && srcProperty.getBridgePkAbove() < 0) {
            throw new IllegalStateException("BridgeRelationship must have valid bridge primary keys for ON and/or ABOVE bridges.");
        }

        long bridgePkTarget;
        if (srcType == RelationType.ACTUAL_SOURCE_ON_BRIDGE) {
            bridgePkTarget = srcProperty.getBridgePkOn();
        } else if (srcType == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE || srcType == RelationType.MIRROR_SOURCE) {
            bridgePkTarget = srcProperty.getBridgePkAbove();
        } else {
            throw new IllegalStateException("Unsupported RelationType for propagation type check: " + srcType);
        }

        Bridge bridge = this.getBridgeByPk(bridgePkTarget);
        if (bridge == null) {
            throw new IllegalStateException("Bridge with primary key " + bridgePkTarget + " not found in BridgeService.");
        }

        CutPointReceiver rcv = profile.getReceiver();         
        if (!bridge.isPointWithinBridgeFootprint(rcv.getCoordinate())) {
            return PropagationType.BRIDGE_TO_OUTSIDE_RECEIVER;
        }

        boolean isRcvAboveBridgeDeck = bridge.isPointAboveBridge(rcv.getCoordinate());

        if (srcType == RelationType.ACTUAL_SOURCE_ON_BRIDGE) {
            return isRcvAboveBridgeDeck ? PropagationType.ACTUAL_SOURCE_TO_UPPER_RECEIVER : PropagationType.ACTUAL_SOURCE_TO_LOWER_RECEIVER;
        } else if (srcType == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE || srcType == RelationType.MIRROR_SOURCE) {
            return isRcvAboveBridgeDeck ? PropagationType.IMAGINARY_SOURCE_TO_UPPER_RECEIVER : PropagationType.IMAGINARY_SOURCE_TO_LOWER_RECEIVER;
        } else {
            throw new IllegalStateException("Unsupported RelationType for propagation type check: " + srcType);
        }
    }
    
    /**
     * Handle bridge-specific intersection processing during profile construction.
     *
     * <p>This method creates a bridge {@link CutPointBridgeWall} describing the
     * intersection, computes whether the intersection corresponds to an
     * enter/exit event (used by profile assembly), and determines whether the
     * ray is obstructed by the bridge barrier.
     *
     * <p>For mirror and imaginary sources, this method validates that a mirror
     * relax cut point is present in the cut points list.
     *
     * @param processedWallIndex index of the processed wall facet
     * @param intersection intersection coordinate on the bridge facet
     * @param facetLine wall facet that was intersected
     * @param fullLine full profiling line segment (source->receiver)
     * @param newCutPoints list to append the new cut point to
     * @param profile the cut profile being constructed (mutated)
     * @return {@code true} if ray is obstructed by bridge barrier; {@code false} if ray passes through
     * @throws IllegalStateException if bridge primary key is invalid or mirror relax point is missing
     */
    public boolean createBridgeCutPointAndCheckObstruction(int processedWallIndex,
                                 Coordinate intersection,
                                 Wall facetLine,
                                 LineSegment fullLine,
                                 List<CutPoint> newCutPoints,
                                 CutProfile profile) {
        
        CutPointSource cutPointSource = profile.getSource();
        
        long bridgePk = facetLine.getPrimaryKey();
        if (bridgePk < 0) {
            throw new IllegalStateException("Primary key of bridge must be set to calculate a profile with bridges");
        }
        CutPointBridgeWall bridgeCutPoint = new CutPointBridgeWall(processedWallIndex, intersection, facetLine.getLineSegment(), facetLine.getAlphas(), bridgePk);
        
        WallDirection wallDirection = WallDirection.UPWARD;
        RelationType relationType = cutPointSource.getBridgeRelationship().getRelationType();
        if (relationType == RelationType.MIRROR_SOURCE || relationType == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE) {
            if (bridgePk == cutPointSource.getBridgeRelationship().getBridgePkAbove()) {
                bridgeCutPoint.setMirrorRelax(true);
                wallDirection = WallDirection.DOWNWARD;
            }
        }

        bridgeCutPoint.setWallDirection(wallDirection);
        
        Bridge bridge = this.getBridge(facetLine.getOriginId());
        double bridgeHeightAtIntersection = 0.0;
        if (wallDirection == WallDirection.UPWARD) {
            bridgeHeightAtIntersection = bridge.getDeckHeightAtPoint(intersection) + bridge.getBarrierHeightAtPoint(intersection);
        } else {
            bridgeHeightAtIntersection = bridge.getDeckHeightAtPoint(intersection) - bridge.getDeckThicknessAtPoint(intersection);
        }
        bridgeCutPoint.modifyIntersectionHeight(bridgeHeightAtIntersection);
        bridgeCutPoint.setBridgeHeight(bridge.getDeckHeightAtPoint(intersection));

        
        double zRayReceiverSource = Vertex.interpolateZ(intersection, fullLine.p0, fullLine.p1);
        
        boolean isIntersectionEntry = ProfileUtils.isIntersectionEntry(intersection, fullLine, facetLine);
        
        if (isIntersectionEntry) {
            bridgeCutPoint.intersectionType = CutPointBridgeWall.INTERSECTION_TYPE.BUILDING_ENTER;
        } else {
            bridgeCutPoint.intersectionType = CutPointBridgeWall.INTERSECTION_TYPE.BUILDING_EXIT;
            for (int i = newCutPoints.size() -1; i >= 0; i--) {
                CutPoint point = newCutPoints.get(i);
                if (!(point instanceof CutPointBridgeWall)) {continue; }
                CutPointBridgeWall bridgePoint = (CutPointBridgeWall) point;
                if (bridgePoint.getBridgePk() == bridgeCutPoint.getBridgePk()) {
                    break;
                }
                if (bridgePoint.getIntersectionType() == CutPointBridgeWall.INTERSECTION_TYPE.BUILDING_EXIT && bridgePoint.getBridgeHeight() >= bridgeCutPoint.getBridgeHeight()) {
                    bridgePoint.setNextBridgePk(bridgeCutPoint.getBridgePk());
                    bridgePoint.setNextBridgeHeight(bridgeCutPoint.getBridgeHeight());
                    break;
                }
            }
        }
        newCutPoints.add(bridgeCutPoint);


        boolean hasBridgeIntersection = true;
        if (wallDirection == WallDirection.UPWARD) {
            hasBridgeIntersection = bridgeHeightAtIntersection >= zRayReceiverSource;
        } else {
            hasBridgeIntersection = bridgeHeightAtIntersection <= zRayReceiverSource;
        }

        return hasBridgeIntersection;
    }

    public void setEffectiveBridgeCutPoint(CutProfile profile) {
        List<Coordinate> cutPointCoordinates2D = profile.generateCutPointCoordinates2D();
        ArrayList<CutPoint> newCutPoints3D = new ArrayList<>();
        long bridgePkOn = profile.getSource().getBridgeRelationship().getBridgePkOn();
        long bridgePkAbove = profile.getSource().getBridgeRelationship().getBridgePkAbove();
        double bridgeHeightOn = profile.getSource().getBridgeHeight();

        List<CutPointBridgeWall> nextBridges = new ArrayList<>();
        for (int i = cutPointCoordinates2D.size() - 1; i >= 0; i--) {
            CutPoint currentPoint = profile.getCutPoints().get(i);
            if (!(currentPoint instanceof CutPointBridgeWall)) continue;
            CutPointBridgeWall bridgePoint = (CutPointBridgeWall) currentPoint;
            if (bridgePoint.getBridgePk() == bridgePkOn || bridgePoint.getBridgePk() == bridgePkAbove) continue;
            if (bridgePoint.getIntersectionType() == CutPointBridgeWall.INTERSECTION_TYPE.BUILDING_EXIT) {
                nextBridges.add(bridgePoint);
            } else if (nextBridges.contains(bridgePoint)) {
                nextBridges.remove(nextBridges.indexOf(bridgePoint));
            }
        }
        
        int mirrorRelaxIndex = cutPointCoordinates2D.size() - 1;
        for (int i = 0; i < cutPointCoordinates2D.size() - 1; i++) {
            CutPoint currentPoint = profile.getCutPoints().get(i);
            if (!(currentPoint instanceof CutPointBridgeWall)) { continue; }
            if (((CutPointBridgeWall) currentPoint).getMirrorRelax()) {
                mirrorRelaxIndex = i;
            }
        }
        
        LineSegment toReceiverLineSegment = new LineSegment(
            cutPointCoordinates2D.get(0), 
            cutPointCoordinates2D.get(cutPointCoordinates2D.size() - 1)
        );
        LineSegment toMirrorRelaxLineSegment = new LineSegment(
            cutPointCoordinates2D.get(0), 
            cutPointCoordinates2D.get(mirrorRelaxIndex)
        );

        for (int i = 0; i < cutPointCoordinates2D.size(); i++) {
            CutPoint currentPoint = profile.getCutPoints().get(i);
            Coordinate currentCoordinate2D = cutPointCoordinates2D.get(i);
            LineSegment referenceLineSegment = i <= mirrorRelaxIndex ? toMirrorRelaxLineSegment : toReceiverLineSegment;
            if (!(currentPoint instanceof CutPointBridgeWall)) {
                newCutPoints3D.add(currentPoint);
                if (!isPointUnderPath(currentCoordinate2D, referenceLineSegment)) {
                    referenceLineSegment.setCoordinates(currentCoordinate2D, referenceLineSegment.p1);
                }
                continue;
            }
            CutPointBridgeWall bridgePoint = (CutPointBridgeWall) currentPoint;
            
            if (i == mirrorRelaxIndex) {
                if (isPointUnderPath(currentCoordinate2D, toReceiverLineSegment)) {
                    toReceiverLineSegment.setCoordinates(currentCoordinate2D, toReceiverLineSegment.p1);
                }
                newCutPoints3D.add(bridgePoint);
                continue;
            }

            if (bridgePoint.getIntersectionType() == CutPointBridgeWall.INTERSECTION_TYPE.BUILDING_ENTER) {
                if (!isPointUnderPath(currentCoordinate2D, referenceLineSegment)) {
                    // if the bridge is above the main sound ray, it is excluded from the profile and do nothing here.
                    continue;
                }
                if (Double.isNaN(bridgeHeightOn) || bridgeHeightOn < bridgePoint.getBridgeHeight()){
                    bridgeHeightOn = bridgePoint.getBridgeHeight();
                    newCutPoints3D.add(bridgePoint);
                    continue;
                }
                nextBridges.add(bridgePoint);
                
            } else if (bridgePoint.getIntersectionType() == CutPointBridgeWall.INTERSECTION_TYPE.BUILDING_EXIT) {
                if (Double.isNaN(bridgeHeightOn)) continue;
                if (!isPointUnderPath(currentCoordinate2D, referenceLineSegment)) {
                    referenceLineSegment.setCoordinates(currentCoordinate2D, referenceLineSegment.p1);
                }
                if (nextBridges.contains(bridgePoint)) {
                    nextBridges.remove(nextBridges.indexOf(bridgePoint));
                }
                if (nextBridges.size() > 0) {
                    CutPointBridgeWall nextBridgePoint = nextBridges.get(0);
                    for (int j = 1; j < nextBridges.size(); j++) {
                        CutPointBridgeWall bridgePointCandidate = nextBridges.get(j);
                        if (bridgePointCandidate.getBridgeHeight() > nextBridgePoint.getBridgeHeight()) {
                            nextBridgePoint = bridgePointCandidate;
                        }
                    }
                    bridgePoint.setNextBridgePk(nextBridgePoint.getBridgePk());
                    bridgePoint.setNextBridgeHeight(nextBridgePoint.getBridgeHeight());
                    nextBridges.remove(nextBridges.indexOf(nextBridgePoint));
                }
                
                newCutPoints3D.add(bridgePoint);
                bridgeHeightOn = bridgePoint.getNextBridgeHeight();
            }
        }

        profile.setCutPoints(newCutPoints3D);
    }

    private static boolean isPointUnderPath(Coordinate coordinate, LineSegment lineSegment) {        
        return coordinate.y < lineSegment.pointAlong((coordinate.x - lineSegment.p0.x) / (lineSegment.p1.x - lineSegment.p0.x)).y;
    }

    /**
     * Compute a hash code representing the current state of this BridgeService.
     * The hash is based on the number of bridges and their basic properties.
     * 
     * @return Hash code representing the service state
     */
    @Override
    public int hashCode() {
        return Objects.hash(bridges);
    }

}
