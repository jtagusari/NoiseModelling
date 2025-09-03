package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty.SourceType;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointBridgeWall.WallDirection;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder.IntersectionType;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.RTreeUtils;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    public int getBridgeCount() {
        return bridges.size();
    }

    public Bridge getBridge(int id) {
        return bridges.get(id);
    }

    public Bridge getBridgeByPk(long pk) {
        return bridges.stream().filter(b -> b.getPrimaryKey() == pk).findFirst().orElse(null);
    }

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
            List<LineString> bridgeEdges = bridge.getEdges();
            for (LineString edge : bridgeEdges) {
                Coordinate[] coords = edge.getCoordinates();
                for (int i = 0; i < coords.length - 1; i++) {
                    LineSegment lineSegment = new LineSegment(coords[i], coords[i + 1]);
                    Wall w = new Wall(lineSegment, j, ProfileBuilder.IntersectionType.BRIDGE).setProcessedWallIndex(processedWallService.getProcessedWalls().size());
                    w.setPrimaryKey(bridge.getPrimaryKey());
                    w.copyAlphas(bridge);
                    processedWallService.addProcessedWall(w, factory);
                }
            }
        }
        bridgeTree.build();
    }

    @Override
    public void exportFacetsToProcessedWalls(ProcessedWallService processedWallService) {
        exportFacetsToProcessedWalls(processedWallService, this.geometryFactory);
    }


    public STRtree getBridgeRtree() {
        return bridgeTree;
    }

    /**
     * Return processed walls that intersect the given envelope and are of type BUILDING or WALL.
     *
     * @param env query envelope
     * @return list of processed walls intersecting the envelope
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

    
    public CutPointBridgeWall calculateBridgeMirrorCutpoint(CutProfile profile) {
        CutPointSource src = profile.getSource();
        CutPointReceiver rcv = profile.getReceiver(); 
        long bridgePkAbove = src.getSourceBridgeProperty().getBridgePkAbove();
        Bridge bridge = this.getBridgeByPk(bridgePkAbove);
        boolean receiverIsWithinFootprint = bridge.isPointWithinBridgeFootprint(rcv.getCoordinate());

        List<DiffractionPoint> diffractionPoints = new ArrayList<>();
        for (LineString edge : bridge.getEdges()) {
            DiffractionPoint dp = new DiffractionPoint(src.getCoordinate(), rcv.getCoordinate(), edge, geometryFactory, !receiverIsWithinFootprint);
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

        CutPointBridgeWall bridgeCutPoint = new CutPointBridgeWall(-1, intersection3D, minDistanceDp.getLineSegmentFromEdge(), new ArrayList<>(bridge.getAlphas()), WallDirection.DOWNWARD);
        bridgeCutPoint.setMirrorRelax(true);
        return bridgeCutPoint;
    }

    private class DiffractionPoint{
        private Coordinate source;
        private LineString edge;
        private Coordinate receiver;
        private Coordinate diffractionPoint2D = null;
        private double diffractionDistance2D;
        private GeometryFactory geometryFactory;

        public DiffractionPoint(Coordinate source, Coordinate receiver, LineString edge, GeometryFactory geometryFactory, boolean isMirror) {
            this.source = source;
            this.edge = edge;
            this.receiver = receiver;
            this.geometryFactory = geometryFactory;
            if (isMirror){
                calculate2DDiffractionMirror();
            } else {
                calculate2DDiffraction();
            }
        }

        private void calculate2DDiffraction(){            
            LineString srcToRcv = geometryFactory.createLineString(
                new Coordinate[]{new Coordinate(source.x, source.y), new Coordinate(receiver.x, receiver.y)}
            );
            this.diffractionDistance2D = Math.sqrt(Math.pow(receiver.x - source.x, 2) + Math.pow(receiver.y - source.y, 2));
            
            Geometry intersection2D = srcToRcv.intersection(edge);
            if (intersection2D == null || intersection2D.isEmpty()) {
                return;
            }
            this.diffractionPoint2D = new Coordinate(intersection2D.getCoordinate());
        }

        private void calculate2DDiffractionMirror(){
            
            Point receiverGeom = this.geometryFactory.createPoint(receiver);
            DistanceOp distanceOp = new DistanceOp(receiverGeom, edge);
            Coordinate[] closestPoints = distanceOp.nearestPoints();
            
            if (closestPoints.length >= 2) {
                Coordinate closestOnLine = closestPoints[1];
                
                double mirrorX = 2 * closestOnLine.x - source.x;
                double mirrorY = 2 * closestOnLine.y - source.y;
                this.diffractionDistance2D = Math.sqrt(Math.pow(mirrorX - source.x, 2) + Math.pow(mirrorY - source.y, 2));

                
                LineString srcToMirrorRcv = geometryFactory.createLineString(
                    new Coordinate[]{new Coordinate(source.x, source.y), new Coordinate(mirrorX, mirrorY)}
                );
                
                Geometry intersection2D = srcToMirrorRcv.intersection(edge);
                if (intersection2D == null || intersection2D.isEmpty()) {
                    return;
                }
                this.diffractionPoint2D = new Coordinate(intersection2D.getCoordinate());
                return;
            }
        }

        public double getDiffractionDistance2D() {
            return diffractionDistance2D;
        }
        public Coordinate getDiffractionPoint2D() {
            return new Coordinate(diffractionPoint2D);
        }

        public LineSegment getLineSegmentFromEdge(){
            if (diffractionPoint2D == null || edge == null) {
                throw new IllegalStateException("Diffraction point or edge is not set");
            }
            
            Coordinate[] coords = edge.getCoordinates();
            if (coords.length < 2) {
                throw new IllegalStateException("Edge does not contain enough points");
            }
            
            // Find the edge segment that contains the diffraction point
            for (int i = 0; i < coords.length - 1; i++) {
                LineSegment segment = new LineSegment(coords[i], coords[i + 1]);
                
                // Check if the diffraction point lies on this segment
                double distance = segment.distance(diffractionPoint2D);
                if (distance < 1e-3) { // Small tolerance for floating point comparison
                    // Create 2D coordinates (remove Z component if present)
                    Coordinate p0_2D = new Coordinate(coords[i].x, coords[i].y);
                    Coordinate p1_2D = new Coordinate(coords[i + 1].x, coords[i + 1].y);
                    return new LineSegment(p0_2D, p1_2D);
                }
            }
            
            throw new IllegalStateException("Diffraction point or edge is not set");
        }
        
        public Coordinate getDiffractionPoint3D(Bridge bridge) {
            double z = bridge.getDeckHeightAtPoint(diffractionPoint2D) - bridge.getDeckThicknessAtPoint(diffractionPoint2D);
            return new Coordinate(diffractionPoint2D.x, diffractionPoint2D.y, z);
        }
    }
    
    /**
     * Handle bridge-specific intersection processing.
     *
     * <p>This method creates a bridge {@link CutPointBridgeWall} describing the
     * intersection, computes whether the intersection corresponds to an
     * enter/exit event (used by profile assembly), and decides whether the
     * propagation should stop when the bridge (barrier) is higher than the
     * straight source-receiver ray.
     *
     * <p>The method sets {@code profile.hasBridgeIntersection} when the bridge
     * is considered an obstacle and returns {@code false} when processing should
     * be aborted (controlled by {@code stopAtObstacleOverSourceReceiver}).
     *
     * @param processedWallIndex index of the processed wall facet
     * @param intersection intersection coordinate on the bridge facet
     * @param facetLine wall facet that was intersected
     * @param fullLine full profiling line segment (source->receiver)
     * @param newCutPoints list to append the new cut point to
     * @param stopAtObstacleOverSourceReceiver controls behaviour when obstacle is above ray
     * @param profile the cut profile being constructed (mutated)
     * @return {@code false} when processing should stop; {@code true} to continue
     */
    public boolean createBridgeCutPointAndCheckObstruction(int processedWallIndex,
                                 Coordinate intersection,
                                 Wall facetLine,
                                 LineSegment fullLine,
                                 List<CutPoint> newCutPoints,
                                 boolean stopAtObstacleOverSourceReceiver,
                                 CutProfile profile) {
        
        boolean hasObstacleIntersection = false;
        CutPointSource cutPointSource = profile.getSource();
        CutPointBridgeWall bridgeCutPoint = new CutPointBridgeWall(processedWallIndex, intersection, facetLine.getLineSegment(), facetLine.getAlphas());

        long facetBridgePk = facetLine.getPrimaryKey();
        if (facetBridgePk < 0) {
            throw new IllegalStateException("Primary key of bridge must be set to calculate a profile with bridges");
        }

        SourceType sourceType = cutPointSource.getSourceBridgeProperty().getSourceType();
        if (sourceType == SourceType.MIRROR_SOURCE || sourceType == SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE) {
            boolean mirrorRelax = hasMirrorRelaxCutPoint(newCutPoints);
            if (mirrorRelax) {
                break;
            }
            long bridgeAbovePk = cutPointSource.getSourceBridgeProperty().getBridgePkAbove();
            if (facetBridgePk != bridgeAbovePk) {
                return false;
            }
            bridgeCutPoint.setWallDirection(WallDirection.DOWNWARD);
            bridgeCutPoint.setMirrorRelax(true);
            newCutPoints.add(bridgeCutPoint);
        } 

        newCutPoints.add(bridgeCutPoint);
        double zRayReceiverSource = Vertex.interpolateZ(intersection, fullLine.p0, fullLine.p1);
        
        boolean isIntersectionEntry = ProfileUtils.isIntersectionEntry(intersection, fullLine, facetLine);
        
        if (isIntersectionEntry) {
            bridgeCutPoint.intersectionType = CutPointBridgeWall.INTERSECTION_TYPE.BUILDING_ENTER;
        } else {
            bridgeCutPoint.intersectionType = CutPointBridgeWall.INTERSECTION_TYPE.BUILDING_EXIT;
        }

        Bridge bridge = this.getBridge(facetLine.getOriginId());
        double barrierHeight = bridge.getBarrierHeightAtPoint(intersection);
        double deckThickness = bridge.getDeckThicknessAtPoint(intersection);
        boolean bridgeDiffractionAboveDeck = zRayReceiverSource <= intersection.z + barrierHeight;
        boolean bridgeDiffractionBelowBottom = zRayReceiverSource >= intersection.z - deckThickness;

        if (bridgeDiffractionAboveDeck) {
            profile.hasBridgeIntersection(true);
            return !stopAtObstacleOverSourceReceiver;
        } else if (bridgeDiffractionBelowBottom) {
            return true;
        } else {
            profile.hasBridgeIntersection(true);
            return !stopAtObstacleOverSourceReceiver;
        }
    }

    private static boolean hasMirrorRelaxCutPoint(List<CutPoint> newCutPoints) {
        for (int i = 0; i < newCutPoints.size(); i++) {
            CutPoint pnt = newCutPoints.get(i);
            if (pnt instanceof CutPointBridgeWall) {
                if (((CutPointBridgeWall) pnt).getMirrorRelax()) {
                    return true;
                }
            }
        }
        return false;
    }

}
