package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.RTreeUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunay;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunayError;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerTinfour;
import org.noise_planet.noisemodelling.pathfinder.delaunay.Triangle;
import org.noise_planet.noisemodelling.pathfinder.utils.IntegerTuple;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service responsible for topography (Delaunay) processing and spatial index build.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Collect raw topographic input (points and line segments) provided by callers.</li>
 *   <li>Run a Delaunay triangulation process (via {@code LayerDelaunay}) to build a TIN.</li>
 *   <li>Store resulting triangles, neighbor relationships and vertex coordinates.</li>
 *   <li>Build and expose an STRtree spatial index of triangle envelopes for fast spatial queries.</li>
 *   <li>Provide utilities to sample elevation (Z), query triangle membership and compute
 *       intersections between segments and the triangulation used by {@link ProfileBuilder}.</li>
 * </ul>
 *
 * <p>This class encapsulates triangulation-related responsibilities so that {@link ProfileBuilder}
 * remains an orchestrator which delegates geometric/Delaunay work. Input collection (via
 * {@link #addTopographicPoint(Coordinate)} / {@link #addTopographicLine(LineString)}) is
 * separated from processing ({@link #buildDelaunayTriangulation()}) to allow batching of inputs.
 */
public class TopographyService implements ElevationComputable, ClearableService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TopographyService.class);
    private static final double DELTA = 1e-3;
    private final GeometryFactory factory;
    private final int nodeCapacity;

    private List<Triangle> triangles = new ArrayList<>();
    private List<Triangle> neighbors = new ArrayList<>();
    private List<Coordinate> vertices = new ArrayList<>();
    private STRtree topoTree;
    // store input topo points/lines inside the service
    private final List<Coordinate> topoPoints = new ArrayList<>();
    private final List<LineString> topoLines = new ArrayList<>();

    public TopographyService(int nodeCapacity) {
        this(nodeCapacity, GeometryFactoryProvider.SHARED);
    }

    /**
     * Constructor allowing a custom GeometryFactory to be provided.
     */
    public TopographyService(int nodeCapacity, GeometryFactory factory) {
        this.nodeCapacity = nodeCapacity;
        this.factory = factory != null ? factory : GeometryFactoryProvider.SHARED;
    }

    /**
     * No-arg constructor with a default STRtree node capacity for convenience in tests.
     */
    public TopographyService() {
    this(5);
    }

    /**
     * Add a topographic point to the internal collection.
     *
     * <p>The point is stored for later triangulation. This method does not trigger
     * triangulation; call {@link #buildDelaunayTriangulation()} when all inputs have been fed.</p>
     *
     * @param point 3D coordinate (x,y,z) representing a topographic sample
     */
    public void addTopographicPoint(Coordinate point) {
        if (point != null) {
            topoPoints.add(point);
        }
    }


    /**
     * Add a topographic line constraint to the internal collection.
     *
     * <p>Lines are used as constrained segments during triangulation and are stored
     * until {@link #buildDelaunayTriangulation()} is invoked.</p>
     *
     * @param line line geometry representing linear topographic input
     */
    public void addTopographicLine(LineString line) {
        if (line != null) {
            topoLines.add(line);
        }
    }

    /**
     * Convenience overload: process using internally stored topo points/lines.
     */
    public boolean buildDelaunayTriangulation() {
        return buildDelaunayTriangulation(this.topoPoints, this.topoLines);
    }

    /**
     * Process the given topo points and lines using the underlying LayerDelaunay implementation.
     *
     * <p>This method performs the following steps:
     * <ol>
     *   <li>Validates input and returns {@code false} when insufficient data is provided.</li>
     *   <li>Feeds points and constraint lines to the {@code LayerDelaunay} implementation.</li>
     *   <li>Runs the triangulation and retrieves triangles, neighbors and vertices.</li>
     *   <li>Builds an STRtree spatial index of triangle envelopes for fast geometric queries.</li>
     * </ol>
     *
     * The method returns {@code false} when the Delaunay layer reports an error or when
     * there is not enough data to build a triangulation. On success internal structures
     * ({@code triangles}, {@code neighbors}, {@code vertices} and {@code topoTree}) are populated.
     *
     * @param topoPoints list of point samples to triangulate (may be null)
     * @param topoLines list of line constraints for triangulation (may be null)
     * @return {@code true} on successful triangulation and index build; {@code false} otherwise
     */
    public boolean buildDelaunayTriangulation(List<Coordinate> topoPoints, List<LineString> topoLines) {
        List<Coordinate> safeTopoPoints = validateAndPreparePoints(topoPoints);
        List<LineString> safeTopoLines = validateAndPrepareLines(topoLines);
        
        if (!hasMinimumDataForTriangulation(safeTopoPoints, safeTopoLines)) {
            return false;
        }

        LayerDelaunay layerDelaunay = createDelaunayLayer();
        
        if (!addPointsToDelaunayLayer(layerDelaunay, safeTopoPoints)) {
            return false;
        }
        
        if (!addLinesToDelaunayLayer(layerDelaunay, safeTopoLines)) {
            return false;
        }
        
        if (!processTriangulation(layerDelaunay)) {
            return false;
        }
        
        if (!retrieveTriangulationResults(layerDelaunay)) {
            return false;
        }
        
        buildSpatialIndex();
        logTriangulationResults(safeTopoPoints, safeTopoLines);
        return true;
    }
    
    /**
     * Validate and prepare input points, ensuring null safety.
     */
    private List<Coordinate> validateAndPreparePoints(List<Coordinate> topoPoints) {
        return topoPoints != null ? topoPoints : new ArrayList<>();
    }
    
    /**
     * Validate and prepare input lines, ensuring null safety.
     */
    private List<LineString> validateAndPrepareLines(List<LineString> topoLines) {
        return topoLines != null ? topoLines : new ArrayList<>();
    }
    
    /**
     * Check if there is minimum data required for triangulation.
     */
    private boolean hasMinimumDataForTriangulation(List<Coordinate> points, List<LineString> lines) {
        return points.size() + lines.size() > 1;
    }
    
    /**
     * Create and configure a new Delaunay layer instance.
     */
    private LayerDelaunay createDelaunayLayer() {
        LayerDelaunay layerDelaunay = new LayerTinfour();
        layerDelaunay.setRetrieveNeighbors(true);
        return layerDelaunay;
    }
    
    /**
     * Add all points to the Delaunay layer.
     */
    private boolean addPointsToDelaunayLayer(LayerDelaunay layerDelaunay, List<Coordinate> points) {
        try {
            for (Coordinate topoPoint : points) {
                layerDelaunay.addVertex(topoPoint);
            }
            return true;
        } catch (LayerDelaunayError e) {
            return false;
        }
    }
    
    /**
     * Add all constraint lines to the Delaunay layer.
     */
    private boolean addLinesToDelaunayLayer(LayerDelaunay layerDelaunay, List<LineString> lines) {
        try {
            for (LineString topoLine : lines) {
                layerDelaunay.addLineString(topoLine, -1);
            }
            return true;
        } catch (LayerDelaunayError e) {
            return false;
        }
    }
    
    /**
     * Execute the triangulation process.
     */
    private boolean processTriangulation(LayerDelaunay layerDelaunay) {
        try {
            layerDelaunay.processDelaunay();
            return true;
        } catch (LayerDelaunayError e) {
            return false;
        }
    }
    
    /**
     * Retrieve triangulation results from the Delaunay layer.
     */
    private boolean retrieveTriangulationResults(LayerDelaunay layerDelaunay) {
        try {
            this.triangles = layerDelaunay.getTriangles();
            this.neighbors = layerDelaunay.getNeighbors();
            this.vertices = layerDelaunay.getVertices();
            return true;
        } catch (LayerDelaunayError e) {
            return false;
        }
    }
    
    /**
     * Build spatial index (STRtree) for triangle envelopes.
     */
    private void buildSpatialIndex() {
        topoTree = new STRtree(nodeCapacity);
        Set<IntegerTuple> wallIndex = new HashSet<>();
        
        for (int i = 0; i < triangles.size(); i++) {
            final Triangle tri = triangles.get(i);
            addTriangleToWallIndex(wallIndex, tri, i);
            addTriangleToSpatialIndex(tri, i);
        }
        topoTree.build();
    }
    
    /**
     * Add triangle segments to wall index.
     */
    private void addTriangleToWallIndex(Set<IntegerTuple> wallIndex, Triangle tri, int triangleIndex) {
        wallIndex.add(new IntegerTuple(tri.getA(), tri.getB(), triangleIndex));
        wallIndex.add(new IntegerTuple(tri.getB(), tri.getC(), triangleIndex));
        wallIndex.add(new IntegerTuple(tri.getC(), tri.getA(), triangleIndex));
    }
    
    /**
     * Add triangle envelope to spatial index.
     */
    private void addTriangleToSpatialIndex(Triangle tri, int triangleIndex) {
        Coordinate vA = vertices.get(tri.getA());
        Coordinate vB = vertices.get(tri.getB());
        Coordinate vC = vertices.get(tri.getC());
        Envelope env = factory.createLineString(new Coordinate[]{vA, vB, vC}).getEnvelopeInternal();
        topoTree.insert(env, triangleIndex);
    }
    
    /**
     * Log triangulation results for debugging.
     */
    private void logTriangulationResults(List<Coordinate> points, List<LineString> lines) {
        LOGGER.debug("buildDelaunayTriangulation: triangles={}, vertices={}, topoLines={}, topoPoints={}", 
            triangles == null ? 0 : triangles.size(), 
            vertices == null ? 0 : vertices.size(), 
            lines == null ? 0 : lines.size(), 
            points == null ? 0 : points.size());
    }

    @Override
    public void computeElevations(ProfileBuilder profileBuilder) {
        // TopographyService doesn't compute object elevations itself, but it
        // exposes elevation sampling for other services via getZGround.
        // Keep a no-op adapter to satisfy ElevationComputable.
    }

    @Override
    public void clear() {
        // Reset internal topology and inputs
        this.triangles = new ArrayList<>();
        this.neighbors = new ArrayList<>();
        this.vertices = new ArrayList<>();
        this.topoTree = null;
        this.topoPoints.clear();
        this.topoLines.clear();
    }

    /**
     * Return the list of triangles produced by the last successful triangulation.
     *
     * <p>Each {@code Triangle} object contains indices referencing the {@link #getVertices()}
     * list. The list may be empty if no triangulation has been processed yet.</p>
     *
     * @return list of triangles (possibly empty)
     */
    public List<Triangle> getTriangles() {
        return triangles;
    }

    /**
     * Return the neighbor relationships computed during triangulation.
     *
     * <p>For each triangle index, the corresponding {@code Triangle} element in this list
     * encodes adjacent triangle indices (or -1 when no neighbor exists on a side).</p>
     *
     * @return list of triangle neighbors (possibly empty)
     */
    public List<Triangle> getNeighbors() {
        return neighbors;
    }

    /**
     * Return the list of vertex coordinates used by the triangulation.
     *
     * <p>Vertices are referenced by index from {@link Triangle} instances. Coordinates
     * are expected to contain X/Y and Z (elevation) values.</p>
     *
     * @return list of vertex coordinates (possibly empty)
     */
    public List<Coordinate> getVertices() {
        return vertices;
    }

    /**
     * Return the spatial index (STRtree) built over triangle envelopes.
     *
     * <p>The index can be used to quickly query triangles overlapping an envelope.
     * It may be {@code null} if no triangulation has been processed yet.</p>
     *
     * @return STRtree index of triangles or {@code null}
     */
    public STRtree getTopoRtree() {
        return topoTree;
    }

    /**
    * Return triangle vertices coordinates for the given triangle index.
    *
    * <p>Coordinates are returned in the order A, B, C and contain X/Y/Z values.
    * The caller must ensure {@link #getTriangles()} has been populated (e.g. by
    * calling {@link #buildDelaunayTriangulation()}). An {@link IndexOutOfBoundsException}
    * may be thrown if {@code triIndex} is invalid.</p>
    *
    * @param triIndex triangle index
    * @return array of three {@link Coordinate} objects (A, B, C)
    * @throws IndexOutOfBoundsException when triIndex is out of range
     */
    public Coordinate[] getTriangleVertices(int triIndex) {
        final Triangle tri = this.triangles.get(triIndex);
        return new Coordinate[] {vertices.get(tri.getA()), vertices.get(tri.getB()), vertices.get(tri.getC())};
    }

    /**
     * Return triangle coordinates (non-closed).
     *
     * <p>Useful when building geometries that do not require the closing vertex.
     * See {@link #getClosedTriangle(int)} when a closed ring (first=last) is needed.</p>
     *
     * @param triIndex triangle index
     * @return array of three {@link Coordinate} objects
     * @throws IndexOutOfBoundsException when triIndex is invalid
     */
    public Coordinate[] getTriangle(int triIndex) {
        final Triangle tri = this.triangles.get(triIndex);
        return new Coordinate[]{this.vertices.get(tri.getA()), this.vertices.get(tri.getB()), this.vertices.get(tri.getC())};
    }

    /**
     * Return closed triangle coordinates (last equals first).
     *
     * <p>This is convenient for constructing {@link Polygon} instances where the
     * ring must be explicitly closed.</p>
     *
     * @param triIndex triangle index
     * @return array of four {@link Coordinate} objects (A,B,C,A)
     * @throws IndexOutOfBoundsException when triIndex is invalid
     */
    public Coordinate[] getClosedTriangle(int triIndex) {
        final Triangle tri = this.triangles.get(triIndex);
        return new Coordinate[]{this.vertices.get(tri.getA()), this.vertices.get(tri.getB()), this.vertices.get(tri.getC()), this.vertices.get(tri.getA())};
    }

    /**
     * Find a triangle id that contains the given point using the {@link #topoTree} spatial index.
     *
     * <p>The method queries the STRtree for nearby triangles and computes a barycentric
     * error measure to select the best candidate. Returns {@code -1} when the point is
     * outside the triangulation or when no suitable triangle is found.</p>
     *
     * @param pt 2D/3D coordinate to locate
     * @return triangle index containing the point, or {@code -1} if none found
     */
    public int getTriangleIdByCoordinate(Coordinate pt) {
        Envelope ptEnv = new Envelope(pt);
        ptEnv.expandBy(1);
        STRtree tree = this.topoTree;
        if (tree == null) return -1;
        var res = RTreeUtils.query(tree, new Envelope(ptEnv));
        double minDistance = Double.MAX_VALUE;
        int minDistanceTriangle = -1;
        for (Object objInd : res) {
            int triId = (Integer) objInd;
            Coordinate[] tri = getTriangle(triId);
            AtomicReference<Double> err = new AtomicReference<>(0.);
            JTSUtility.dotInTri(pt, tri[0], tri[1], tri[2], err);
            if (err.get() < minDistance) {
                minDistance = err.get();
                minDistanceTriangle = triId;
            }
        }
        return minDistanceTriangle;
    }

    // Add debug logging wrapper for getTriangleIdByCoordinate

    /**
     * Find the closest intersection of the given segment with triangles in the {@link #topoTree}.
     *
     * <p>The method searches triangles whose envelope intersects the segment's envelope,
     * computes actual geometry intersections and returns the intersection point closest
     * to {@code segment.p0}. If an intersection is found the {@code intersection} coordinate
     * is set (including interpolated Z) and {@code intersectionTriangle} receives the
     * matching triangle index.</p>
     *
     * @param segment input segment to test
     * @param intersection output parameter to receive the intersection coordinate (modified when true)
     * @param intersectionTriangle output parameter set to triangle index when an intersection is found
     * @return {@code true} if an intersection was found and outputs were populated; {@code false} otherwise
     */
    public boolean findClosestTriangleIntersection(LineSegment segment, final Coordinate intersection, AtomicInteger intersectionTriangle) {
        if (this.topoTree == null) {
            return false;
        }
        
        Envelope queryEnvelope = createQueryEnvelope(segment);
        List<?> candidateTriangles = RTreeUtils.query(this.topoTree, queryEnvelope);
        
        ClosestIntersectionResult result = findClosestIntersectionAmongCandidates(segment, candidateTriangles);
        
        if (result.isFound()) {
            populateIntersectionResult(intersection, intersectionTriangle, result);
            return true;
        }
        
        return false;
    }
    
    /**
     * Create query envelope for spatial search, with minimum size to ensure coverage.
     */
    private Envelope createQueryEnvelope(LineSegment segment) {
        Envelope queryEnvelope = new Envelope(segment.p0);
        queryEnvelope.expandToInclude(segment.p1);
        if (queryEnvelope.getHeight() < 1.0 || queryEnvelope.getWidth() < 1.0) {
            queryEnvelope.expandBy(1.0);
        }
        return queryEnvelope;
    }
    
    /**
     * Find the closest intersection among candidate triangles.
     */
    private ClosestIntersectionResult findClosestIntersectionAmongCandidates(LineSegment segment, List<?> candidateTriangles) {
        double minDistance = Double.MAX_VALUE;
        int minDistanceTriangle = -1;
        Coordinate intersectionPt = null;
        
        LineString lineString = factory.createLineString(new Coordinate[]{segment.p0, segment.p1});
        
        for (Object objInd : candidateTriangles) {
            int triId = (Integer) objInd;
            IntersectionCandidate candidate = checkTriangleIntersection(segment, lineString, triId);
            
            if (candidate.isValid() && candidate.distance < minDistance) {
                minDistance = candidate.distance;
                minDistanceTriangle = triId;
                intersectionPt = candidate.intersectionPoint;
            }
        }
        
        return new ClosestIntersectionResult(minDistanceTriangle, intersectionPt);
    }
    
    /**
     * Check if a specific triangle intersects with the segment.
     */
    private IntersectionCandidate checkTriangleIntersection(LineSegment segment, LineString lineString, int triId) {
        Coordinate[] tri = getTriangle(triId);
        Geometry triangleGeometry = factory.createPolygon(new Coordinate[]{tri[0], tri[1], tri[2], tri[0]});
        
        if (!triangleGeometry.intersects(lineString)) {
            return IntersectionCandidate.invalid();
        }
        
        Coordinate[] nearestCoordinates = DistanceOp.nearestPoints(triangleGeometry, lineString);
        double minDistance = Double.MAX_VALUE;
        Coordinate bestIntersection = null;
        
        for (Coordinate nearestCoordinate : nearestCoordinates) {
            double distance = nearestCoordinate.distance(segment.p0);
            if (distance < minDistance) {
                minDistance = distance;
                bestIntersection = nearestCoordinate;
            }
        }
        
        return new IntersectionCandidate(bestIntersection, minDistance);
    }
    
    /**
     * Populate the output parameters with intersection results.
     */
    private void populateIntersectionResult(Coordinate intersection, AtomicInteger intersectionTriangle, ClosestIntersectionResult result) {
        Coordinate[] tri = getTriangle(result.triangleId);
        result.intersectionPoint.setZ(Vertex.interpolateZ(result.intersectionPoint, tri[0], tri[1], tri[2]));
        intersection.setCoordinate(result.intersectionPoint);
        intersectionTriangle.set(result.triangleId);
    }
    
    /**
     * Helper class to represent intersection candidate data.
     */
    private static class IntersectionCandidate {
        final Coordinate intersectionPoint;
        final double distance;
        final boolean valid;
        
        IntersectionCandidate(Coordinate intersectionPoint, double distance) {
            this.intersectionPoint = intersectionPoint;
            this.distance = distance;
            this.valid = intersectionPoint != null;
        }
        
        static IntersectionCandidate invalid() {
            return new IntersectionCandidate(null, Double.MAX_VALUE);
        }
        
        boolean isValid() {
            return valid;
        }
    }
    
    /**
     * Helper class to represent closest intersection result.
     */
    private static class ClosestIntersectionResult {
        final int triangleId;
        final Coordinate intersectionPoint;
        
        ClosestIntersectionResult(int triangleId, Coordinate intersectionPoint) {
            this.triangleId = triangleId;
            this.intersectionPoint = intersectionPoint;
        }
        
        boolean isFound() {
            return triangleId != -1;
        }
    }

    /**
     * Compute the next triangle index along the propagation direction.
     *
     * <p>Given a triangle index and a propagation {@code LineSegment}, this method
     * inspects each triangle edge to determine which neighbor triangle is first
     * intersected by the propagation. {@code navigationHistory} is used to avoid
     * backtracking. When a next triangle is found the method fills
     * {@code segmentIntersection} with the intersection coordinate (including Z)
     * and returns the neighbor index. Returns {@code -1} when no suitable neighbor
     * is found.</p>
     *
     * @param triIndex current triangle index
     * @param propagationLine line describing propagation direction
     * @param navigationHistory set of already visited triangle indices to prevent loops
     * @param segmentIntersection output parameter receiving the intersection coordinate when found
     * @return neighbor triangle index to continue propagation, or {@code -1} if none
     */
    public int getNextTri(final int triIndex,
                          final LineSegment propagationLine,
                          HashSet<Integer> navigationHistory,
                          final Coordinate segmentIntersection) {
        LOGGER.debug("getNextTri: START triIndex={}, propagationLine={} to {}", triIndex, propagationLine.p0, propagationLine.p1);
        
        final Triangle tri = this.triangles.get(triIndex);
        final Triangle triNeighbors = this.neighbors.get(triIndex);
        
        double nearestIntersectionPtDist = Double.MAX_VALUE;
        int nearestIntersectionSide = -1;
        
        List<Coordinate> verts = this.vertices;
        final Coordinate aTri = verts.get(tri.getA());
        final Coordinate bTri = verts.get(tri.getB());
        final Coordinate cTri = verts.get(tri.getC());
        
        LOGGER.debug("getNextTri: triangle vertices A={}, B={}, C={}", aTri, bTri, cTri);
        LOGGER.debug("getNextTri: triangle neighbors A-B={}, B-C={}, C-A={}", 
                    triNeighbors.getA(), triNeighbors.getB(), triNeighbors.getC());
        
        // Check intersection with each side of the triangle
        TriangleSideIntersectionResult result = new TriangleSideIntersectionResult(nearestIntersectionPtDist, nearestIntersectionSide);
        
        result = checkTriangleSideIntersection(
            propagationLine, navigationHistory, triNeighbors, segmentIntersection,
            aTri, bTri, 2, result);
        LOGGER.debug("getNextTri: side A-B check result nearestSide={}, dist={}", result.nearestSide, result.nearestDistance);
        
        result = checkTriangleSideIntersection(
            propagationLine, navigationHistory, triNeighbors, segmentIntersection,
            bTri, cTri, 0, result);
        LOGGER.debug("getNextTri: side B-C check result nearestSide={}, dist={}", result.nearestSide, result.nearestDistance);
        
        result = checkTriangleSideIntersection(
            propagationLine, navigationHistory, triNeighbors, segmentIntersection,
            cTri, aTri, 1, result);
        LOGGER.debug("getNextTri: side C-A check result nearestSide={}, dist={}", result.nearestSide, result.nearestDistance);
        
        int nextTri = result.nearestSide > -1 ? triNeighbors.get(result.nearestSide) : -1;
        LOGGER.debug("getNextTri: RESULT nextTri={}, nearestSide={}", nextTri, result.nearestSide);
        
        return nextTri;
    }
    
    /**
     * Helper class to track triangle side intersection results.
     */
    private static class TriangleSideIntersectionResult {
        double nearestDistance;
        int nearestSide;
        
        TriangleSideIntersectionResult(double nearestDistance, int nearestSide) {
            this.nearestDistance = nearestDistance;
            this.nearestSide = nearestSide;
        }
    }
    
    /**
     * Check intersection with a specific triangle side and update nearest intersection if closer.
     */
    private TriangleSideIntersectionResult checkTriangleSideIntersection(
            final LineSegment propagationLine,
            HashSet<Integer> navigationHistory,
            final Triangle triNeighbors,
            final Coordinate segmentIntersection,
            final Coordinate sideStart,
            final Coordinate sideEnd,
            int sideIndex,
            TriangleSideIntersectionResult currentResult) {
        
        int idNeighbor = triNeighbors.get(sideIndex);
        if (navigationHistory.contains(idNeighbor)) {
            return currentResult;
        }
        
        LineSegment triSegment = new LineSegment(sideStart, sideEnd);
        Coordinate intersectionTest = findSegmentIntersection(propagationLine, triSegment);
        
        if (intersectionTest != null) {
            double distToIntersection = propagationLine.p1.distance(intersectionTest);
            if (distToIntersection < currentResult.nearestDistance) {
                segmentIntersection.setCoordinate(intersectionTest);
                return new TriangleSideIntersectionResult(distToIntersection, sideIndex);
            }
        }
        
        return currentResult;
    }
    
    /**
     * Find intersection between propagation line and triangle segment.
     */
    private Coordinate findSegmentIntersection(final LineSegment propagationLine, final LineSegment triSegment) {
        Coordinate[] closestPoints = propagationLine.closestPoints(triSegment);
        
        if (closestPoints.length == 2 && 
            closestPoints[0].distance(closestPoints[1]) < JTSUtility.TRIANGLE_INTERSECTION_EPSILON) {
            
            return new Coordinate(
                closestPoints[0].x, 
                closestPoints[0].y, 
                Vertex.interpolateZ(closestPoints[0], triSegment.p0, triSegment.p1)
            );
        }
        
        return null;
    }

    /**
     * Fetch all intersections with the internal TIN between two points.
     *
     * <p>This method traces a straight segment from {@code p1} to {@code p2} and
     * returns a list of 3D coordinates representing significant plane changes or
     * triangle edge intersections. The first and last entries correspond to
     * {@code p1} and {@code p2} projected on the TIN (Z interpolated).</p>
     *
     * <p>If {@code stopAtObstacleOverSourceReceiver} is {@code true} the method
     * stops early and returns {@code false} when the TIN contains an obstruction
     * (i.e. TIN elevation rises above the line-of-sight).</p>
     *
     * @param outputPoints list to which intersection coordinates will be appended (must be mutable)
     * @param p1 start coordinate (X,Y); Z may be ignored and will be interpolated
     * @param p2 end coordinate (X,Y); Z may be ignored and will be interpolated
     * @param stopAtObstacleOverSourceReceiver stop early when obstruction over source/receiver is detected
     * @return {@code true} if the segment p1-p2 is free of DEM intersections (no obstruction); {@code false} otherwise
     */
    public boolean fetchTopographicProfile(List<Coordinate> outputPoints, Coordinate p1, Coordinate p2, boolean stopAtObstacleOverSourceReceiver) {
        LOGGER.debug("fetchTopographicProfile: START p1={}, p2={}, topoTree={}", p1, p2, (topoTree != null ? "available" : "null"));
        if(this.topoTree == null) {
            LOGGER.debug("fetchTopographicProfile: no topo tree available, returning early");
            return true;
        }
        
        long startTime = System.currentTimeMillis();
        // get origin triangle id
        int curTriP1 = getTriangleIdByCoordinate(p1);
        LOGGER.debug("fetchTopographicProfile: curTriP1={} for p1={}", curTriP1, p1);
        
        LineSegment propaLine = new LineSegment(p1, p2);
        if(curTriP1 == -1) {
            LOGGER.debug("fetchTopographicProfile: outside triangle bounds, searching closest triangle");
            // we are outside the bounds of the triangles
            // Find the closest triangle to p1 on the line p1 to p2
            Coordinate intersectionPt = new Coordinate();
            AtomicInteger minDistanceTriangle = new AtomicInteger();
            if(findClosestTriangleIntersection(propaLine, intersectionPt, minDistanceTriangle)) {
                Coordinate[] triangleVertex = getTriangleVertices(minDistanceTriangle.get());
                double interp = Vertex.interpolateZ(p2, triangleVertex[0], triangleVertex[1], triangleVertex[2]);
                outputPoints.add(new Coordinate(p1.x, p1.y, interp));
                LOGGER.debug("fetchTopographicProfile: outside TIN, found closest tri={}, interpZForP2={}", minDistanceTriangle.get(), interp);
                curTriP1 = minDistanceTriangle.get();
            } else {
                // out of DEM propagation area
                LOGGER.debug("fetchTopographicProfile: out of DEM propagation area, returning early");
                return true;
            }
        }
        HashSet<Integer> navigationHistory = new HashSet<Integer>();
        int navigationTri = curTriP1;
        // Add p1 coordinate
        Coordinate[] triangleVertex = getTriangleVertices(curTriP1);
        outputPoints.add(new Coordinate(p1.x, p1.y, Vertex.interpolateZ(p1, triangleVertex[0], triangleVertex[1], triangleVertex[2])));
        boolean freeField = true;
        
        int navigationSteps = 0;
        LOGGER.debug("fetchTopographicProfile: starting navigation from tri={}", navigationTri);
        
    while (navigationTri != -1) {
            navigationSteps++;
            if (navigationSteps % 100 == 0) {
                LOGGER.debug("fetchTopographicProfile: navigation step {}, current tri={}, history size={}", navigationSteps, navigationTri, navigationHistory.size());
            }
            if (navigationSteps > 10000) {
                LOGGER.warn("fetchTopographicProfile: EXCESSIVE navigation steps ({}), possible infinite loop! Breaking.", navigationSteps);
                break;
            }
            
            navigationHistory.add(navigationTri);
            Coordinate intersectionPt = new Coordinate();
            int propaTri = this.getNextTri(navigationTri, propaLine, navigationHistory, intersectionPt);
            LOGGER.debug("fetchTopographicProfile: getNextTri returned propaTri={} for navigationTri={}", propaTri, navigationTri);
            
            if(propaTri == -1) {
                // Add p2 coordinate
                triangleVertex = getTriangleVertices(navigationTri);
        double interp = Vertex.interpolateZ(p2, triangleVertex[0], triangleVertex[1], triangleVertex[2]);
        outputPoints.add(new Coordinate(p2.x, p2.y, interp));
        LOGGER.debug("fetchTopographicProfile: end of navigation at tri={}, interpZForP2={}", navigationTri, interp);
            } else {
                // Found next triangle (if propaTri >= 0)
                // extract X,Y,Z values of intersection with triangle segment
                if(!Double.isNaN(intersectionPt.z)) {
                    Coordinate closestPointOnPropagationLine = propaLine.closestPoint(intersectionPt);
                    double interpolatedZ = Vertex.interpolateZ(closestPointOnPropagationLine, propaLine.p0, propaLine.p1);
                    outputPoints.add(intersectionPt);
                    LOGGER.debug("fetchTopographicProfile: intersection tri={}, pt={}, interpolatedZOnLine={}", propaTri, intersectionPt, interpolatedZ);
                    if(interpolatedZ < intersectionPt.z) {
                        freeField = false;
                        if(stopAtObstacleOverSourceReceiver) {
                            LOGGER.debug("fetchTopographicProfile: obstacle detected, stopping early");
                            return false;
                        }
                    }
                }
            }
            navigationTri = propaTri;
        }
        
        long endTime = System.currentTimeMillis();
        LOGGER.debug("fetchTopographicProfile: COMPLETED navigation steps={}, outputPoints={}, freeField={}, time={}ms", 
                    navigationSteps, outputPoints.size(), freeField, (endTime - startTime));
        return freeField;
    }

    /**
     * Add topography cut points to the provided {@link CutProfile} using internal TIN data.
     *
     * <p>The method queries the TIN along the segment p1-p2 and inserts intermediate
     * topographic cut points into the {@code profile}. It also sets {@code zGround}
     * on the profile's source and receiver and the {@code hasTopographyIntersection}
     * flag when an obstruction is detected.</p>
     *
     * @param p1 start coordinate
     * @param p2 end coordinate
     * @param profile cut profile to enrich with topography cut points
     * @param stopAtObstacleOverSourceReceiver stop early when obstruction is detected
     */
    public void addTopoCutPts(Coordinate p1, Coordinate p2, CutProfile profile, boolean stopAtObstacleOverSourceReceiver) {
        LOGGER.debug("TopographyService.addTopoCutPts - Starting");
        LOGGER.debug("  p1: x={}, y={}, z={}", p1.x, p1.y, p1.z);
        LOGGER.debug("  p2: x={}, y={}, z={}", p2.x, p2.y, p2.z);
        LOGGER.debug("  stopAtObstacleOverSourceReceiver: {}", stopAtObstacleOverSourceReceiver);
        
        long startTime = System.currentTimeMillis();
        
        List<Coordinate> coordinates = new ArrayList<>();
        LOGGER.debug("  Calling fetchTopographicProfile...");
        long fetchStartTime = System.currentTimeMillis();
        boolean freeField = fetchTopographicProfile(coordinates, p1, p2, stopAtObstacleOverSourceReceiver);
        long fetchDuration = System.currentTimeMillis() - fetchStartTime;
        LOGGER.debug("  fetchTopographicProfile completed in {} ms, freeField={}, coordinates.size()={}", fetchDuration, freeField, coordinates.size());
        
        if(coordinates.size() >= 2) {
            CutPointSource cutPointSource = profile.getSource();
            cutPointSource.setZGround(coordinates.get(0).z);
            profile.setSource(cutPointSource);

            CutPointReceiver cutPointReceiver = profile.getReceiver();
            cutPointReceiver.setZGround(coordinates.get(coordinates.size() - 1).z);
            profile.setReceiver(cutPointReceiver);

        } else {
            LOGGER.warn(String.format(java.util.Locale.ROOT, "Propagation out of the DEM area from %s to %s",
                    p1.toString(), p2.toString()));
            return;
        }
        profile.hasTopographyIntersection(!freeField);

        LOGGER.debug("  Processing {} coordinates for cut points...", coordinates.size());
        List<CutPointTopography> topographyList = new ArrayList<>(coordinates.size());
        for(int idPoint = 1; idPoint < coordinates.size() - 1; idPoint++) {
            final Coordinate previous = coordinates.get(idPoint - 1);
            final Coordinate current = coordinates.get(idPoint);
            final Coordinate next = coordinates.get(idPoint+1);
            // Do not add topographic points which are simply the linear interpolation between two points
            // triangulation add a lot of interpolated lines from line segment DEM
            if(org.locationtech.jts.algorithm.CGAlgorithms3D.distancePointSegment(current, previous, next) >= DELTA) {
                topographyList.add(new CutPointTopography(current));
            }
        }
        profile.insertCutPoint(true, topographyList.toArray(CutPoint[]::new));
        
        long totalDuration = System.currentTimeMillis() - startTime;
        LOGGER.debug("TopographyService.addTopoCutPts - Completed in {} ms: inserted topography cut points={}, profileSourceZ={}, profileReceiverZ={}, hasIntersection={}", 
                totalDuration, topographyList.size(), profile.getSource().zGround, profile.getReceiver().zGround, profile.hasTopographyIntersection());
    }

    /**
     * Return the current DEM as a {@link MultiPolygon} built from triangles.
     *
     * <p>Each triangle is converted into a {@link Polygon} and the collection
     * is returned as a {@code MultiPolygon}. When no triangulation is available
     * an empty {@code MultiPolygon} is returned.</p>
     *
     * @return MultiPolygon representing the DEM (may be empty)
     */
    public MultiPolygon demAsMultiPolygon() {
    GeometryFactory GF = this.factory;
        List<Triangle> tris = this.getTriangles();
        if(tris != null && !tris.isEmpty()) {
            List<Polygon> polyTri = new ArrayList<>(tris.size());
            for (int i = 0; i < tris.size(); i++) {
                polyTri.add(GF.createPolygon(getClosedTriangle(i)));
            }
            return GF.createMultiPolygon(polyTri.toArray(Polygon[]::new));
        } else {
            return GF.createMultiPolygon();
        }
    }

    /**
     * Fetch altitude (Z) in meters from sea level at a location using the internal TIN.
     *
     * <p>If a valid {@code triangleHint} is provided (non-negative) and still contains
     * the point, the method uses it directly to avoid an STRtree lookup — useful when
     * sampling many points within the same area. When the point is outside the TIN or
     * no triangle is found the method returns {@code 0.0}.</p>
     *
     * @param coordinate location (X,Y) to sample; Z is ignored and replaced by interpolated value
     * @param triangleHint input/output hint for triangle index to speed repeated queries (may be {@code -1})
     * @return interpolated Z value in the TIN at the given (X,Y), or {@code 0.0} when outside triangulation
     */
    public double getZGround(Coordinate coordinate, AtomicInteger triangleHint) {
        if (this.topoTree == null) {
            return 0.0;
        }
        
        int triangleIndex = findValidTriangleIndex(coordinate, triangleHint);
        if (triangleIndex == -1) {
            return 0.0;
        }
        
        return interpolateZAtTriangle(coordinate, triangleIndex, triangleHint);
    }
    
    /**
     * Find a valid triangle index for the given coordinate, using hint if possible.
     */
    private int findValidTriangleIndex(Coordinate coordinate, AtomicInteger triangleHint) {
        int candidateIndex = triangleHint.get();
        
        if (isValidTriangleHint(candidateIndex, coordinate)) {
            return candidateIndex;
        }
        
        return getTriangleIdByCoordinate(coordinate);
    }
    
    /**
     * Check if the triangle hint is valid and contains the coordinate.
     */
    private boolean isValidTriangleHint(int triangleIndex, Coordinate coordinate) {
        if (triangleIndex < 0 || triangles == null || triangleIndex >= triangles.size()) {
            return false;
        }
        
        final Triangle tri = triangles.get(triangleIndex);
        final Coordinate p1 = vertices.get(tri.getA());
        final Coordinate p2 = vertices.get(tri.getB());
        final Coordinate p3 = vertices.get(tri.getC());
        
        return JTSUtility.dotInTri(coordinate, p1, p2, p3);
    }
    
    /**
     * Interpolate Z value at the given triangle and update hint.
     */
    private double interpolateZAtTriangle(Coordinate coordinate, int triangleIndex, AtomicInteger triangleHint) {
        final Triangle tri = this.triangles.get(triangleIndex);
        final Coordinate p1 = this.vertices.get(tri.getA());
        final Coordinate p2 = this.vertices.get(tri.getB());
        final Coordinate p3 = this.vertices.get(tri.getC());
        
        if (JTSUtility.dotInTri(coordinate, p1, p2, p3)) {
            triangleHint.set(triangleIndex);
            return Vertex.interpolateZ(coordinate, p1, p2, p3);
        }
        
        return 0.0;
    }

    public double getZGround(Coordinate coordinate) {
        return getZGround(coordinate, new AtomicInteger(-1));
    }
}
