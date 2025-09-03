package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that owns and processes buildings (keeps building list and building r-tree).
 *
 * <p>This service implements part of the responsibilities that used to belong to
 * {@code ProfileBuilder}. It stores building geometries, maintains the spatial
 * index, precomputes helper points used during profile construction and exposes
 * methods that {@code ProfileBuilder} previously provided when building
 * acoustic profiles.</p>
 */
public class BuildingService implements FrequencyInitializable, ElevationComputable, ClearableService, ProcessedFacetsExportable {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildingService.class);
    private final List<Building> buildings = new ArrayList<>();
    private final Map<Integer, ArrayList<Coordinate>> buildingsWideAnglePoints = new HashMap<>();
    private final STRtree buildingTree;
    // If true, building polygons contain Z coordinates that represent altitude
    private boolean zBuildings = false;
    /** GeometryFactory used by this service. */
    private final GeometryFactory geometryFactory;

    /**
     * Create a new BuildingService with the given STRtree node capacity.
     *
     * @param buildingNodeCapacity approximate node capacity for the building STRtree
     */
    public BuildingService(int buildingNodeCapacity) {
        this(buildingNodeCapacity, GeometryFactoryProvider.SHARED);
    }

    /**
     * Constructor allowing injection of a custom GeometryFactory for testing or precision control.
     */
    public BuildingService(int buildingNodeCapacity, GeometryFactory geometryFactory) {
        this.buildingTree = new STRtree(buildingNodeCapacity);
        this.geometryFactory = geometryFactory != null ? geometryFactory : GeometryFactoryProvider.SHARED;
    }

    /**
     * No-arg constructor with a default STRtree node capacity.
     */
    public BuildingService() {
        this(5);
    }

    /**
     * Return whether building polygons include Z coordinates representing altitude.
     *
     * @return {@code true} when building polygons contain elevation (Z) values
     */
    public boolean isZBuildings() {
        return zBuildings;
    }

    /**
     * Configure whether building polygons include Z coordinates representing altitude.
     *
     * @param zBuildings {@code true} when building polygons include altitude (Z) values
     */
    public void setZBuildings(boolean zBuildings) {
        this.zBuildings = zBuildings;
    }
    
    /**
     * Add a pre-constructed {@link Building} to the service and index it.
     *
     * @param building the building instance to add
     */
    public void addBuilding(Building building) {
        buildings.add(building);
        buildingTree.insert(building.poly.getEnvelopeInternal(), buildings.size());
    }
    
    /**
     * Return the list of buildings managed by this service.
     *
     * @return mutable list of managed buildings
     */
    public List<Building> getBuildings() {
    return new LoggingUnmodifiableList<>(buildings, "BuildingService.buildings");
    }

    /**
     * Return the number of buildings managed by this service.
     *
     * @return building count
     */
    public int getBuildingCount() {
        return buildings.size();
    }

    /**
     * Retrieve a building by its internal id (index into the building list).
     *
     * @param id index of the building
     * @return the requested Building
     */
    public Building getBuilding(int id) {
        return buildings.get(id);
    }
    
    /**
     * Retrieve a building by an explicit list index. Kept for compatibility with
     * older callers; identical to {@link #getBuilding(int)}.
     *
     * @param index list index of the building
     * @return the requested Building
     */
    public Building getBuildingByIndex(int index) {
        return buildings.get(index);
    }
    
    /**
     * Get precomputed wide-angle offset points for the building identified by {@code build}.
     * These points help avoid numerical issues at wide polygon vertices during profile tracing.
     *
     * @param build building identifier used as key in the precomputed map
     * @return list of offset coordinates or {@code null} if not present
     */
    public ArrayList<Coordinate> getPrecomputedWideAnglePoints(int build) {
        return buildingsWideAnglePoints.get(build);
    }

    /**
     * Return the spatial index (STRtree) containing building envelopes. Index
     * entries reference positions in the internal building list.
     *
     * @return the building STRtree index
     */
    public STRtree getBuildingRtree() {
        return buildingTree;
    }

    /**
    * Build processed wall facets from building polygons and populate the
    * provided {@link ProcessedWallService} with these walls. Also precomputes wide-angle
    * points for each building polygon and finalises the internal R-tree.
    *
    * <p>Contract with {@link ProcessedWallService}:
    * <ul>
    *   <li>This method inserts processed wall facets into {@code processedWallService}
    *       via {@link ProcessedWallService#addProcessedWall}.</li>
    *   <li>Callers must invoke {@link ProcessedWallService#buildProcessedWallRtree()}
    *       after all export methods (from all services) have been called and before
    *       issuing any spatial queries against the processed-wall index.</li>
    *   <li>When creating a processed wall facet, code commonly uses
    *       {@code processedWallService.getProcessedWalls().size()} to obtain the
    *       facet index to store on the object prior to calling
    *       {@link ProcessedWallService#addProcessedWall}.</li>
    * </ul>
    * @param processedWallService service that will receive processed walls
    * @param factory geometry factory used when adding walls
     */
    @Override
    public void exportFacetsToProcessedWalls(ProcessedWallService processedWallService, GeometryFactory factory) {
        buildingsWideAnglePoints.clear();
        for (int j = 0; j < buildings.size(); j++) {
            Building building = buildings.get(j);
            buildingsWideAnglePoints.put(j + 1,
                    getWideAnglePointsOnPolygon(building.poly.getExteriorRing(), 0, 2 * Math.PI));
            List<Wall> walls = new ArrayList<>();
            Coordinate[] coords = building.poly.getCoordinates();
            for (int i = 0; i < coords.length - 1; i++) {
                LineSegment lineSegment = new LineSegment(coords[i], coords[i + 1]);
                Wall w = new Wall(lineSegment, j, ProfileBuilder.IntersectionType.BUILDING).setProcessedWallIndex(processedWallService.getProcessedWalls().size());
                walls.add(w);
                w.setPrimaryKey(building.getPrimaryKey());
                w.copyAlphas(building);
                processedWallService.addProcessedWall(w, factory);
            }
            building.setWalls(walls);
        }
        buildingTree.build();
    }

    /**
     * Overload using this service's configured GeometryFactory.
     */
    @Override
    public void exportFacetsToProcessedWalls(ProcessedWallService processedWallService) {
        exportFacetsToProcessedWalls(processedWallService, this.geometryFactory);
    }


    /**
     * Update building polygon Z values using DEM/topography provided by
     * {@code ProfileBuilder}. For buildings without valid Z values this will
     * set elevations based on the building height plus the topographic update.
     *
     * @param profileBuilder provider of topography/elevation information
     */
    @Override
    public void computeElevations(ProfileBuilder profileBuilder) {
        for (Building b : buildings) {
            if (Double.isNaN(b.poly.getCoordinate().z) || b.poly.getCoordinate().z == 0.0 || !isZBuildings()) {
                b.poly2D_3D();
                b.poly.apply(new ElevationFilter.UpdateZ(b.height + b.updateZTopo(profileBuilder)));
            }
        }
    }


    /**
     * Initialize frequency-dependent arrays on all managed buildings.
     * @param exactFrequencyArray list of exact frequency values used to compute alphas
     */
    public void initializeFrequencyDependentData(List<Double> exactFrequencyArray) {
        if (exactFrequencyArray == null) {
            LOGGER.warn("BuildingService.initializeFrequencyDependentData: exactFrequencyArray is null");
            return;
        }
        LOGGER.debug("BuildingService.initializeFrequencyDependentData: called with exactFrequencyArray.size={}", exactFrequencyArray.size());
        for (Building b : buildings) {
            b.initialize(exactFrequencyArray);
        }
    }
    /**
     * Compute offset coordinates for polygon vertices that have a "wide"
     * interior angle. The returned list is closed (first point repeated at the
     * end) and can be used when tracing profiles near building corners.
     *
     * @param linearRing the polygon exterior ring to analyse
     * @param minAngle minimum interior angle (radians) to consider
     * @param maxAngle maximum interior angle (radians) to consider
     * @return list of offset coordinates for wide-angle vertices
     */
    public ArrayList<Coordinate> getWideAnglePointsOnPolygon(LinearRing linearRing, double minAngle, double maxAngle) {
        Coordinate[] ring = linearRing.getCoordinates().clone();
        if(!org.locationtech.jts.algorithm.Orientation.isCCW(ring)) {
            for (int i = 0; i < ring.length / 2; i++) {
                Coordinate temp = ring[i];
                ring[i] = ring[ring.length - 1 - i];
                ring[ring.length - 1 - i] = temp;
            }
        }
        ArrayList <Coordinate> verticesBuilding = new ArrayList<>(ring.length);
        for(int i=0; i < ring.length - 1; i++) {
            int i1 = i > 0 ? i-1 : ring.length - 2;
            int i3 = i + 1;
            double smallestAngle = Angle.angleBetweenOriented(ring[i1], ring[i], ring[i3]);
            double openAngle;
            if(smallestAngle >= 0) {
                openAngle = smallestAngle;
            } else {
                openAngle = 2 * Math.PI + smallestAngle;
            }
            if(openAngle > minAngle && openAngle < maxAngle) {
                double midAngle = openAngle / 2;
                double midAngleFromZero = Angle.angle(ring[i], ring[i1]) + midAngle;
                Coordinate offsetPt = new Coordinate(
                        ring[i].x + Math.cos(midAngleFromZero) * ProfileBuilder.wideAngleTranslationEpsilon,
                        ring[i].y + Math.sin(midAngleFromZero) * ProfileBuilder.wideAngleTranslationEpsilon,
                        ring[i].z + ProfileBuilder.wideAngleTranslationEpsilon);
                verticesBuilding.add(offsetPt);
            }
        }
        if (!verticesBuilding.isEmpty()) {
            // close the returned list: repeat first point at the end
            verticesBuilding.add(verticesBuilding.get(0));
        }
        return verticesBuilding;
    }

    /**
     * Clear all managed buildings and their precomputed helper structures.
     */
    public void clear() {
        buildings.clear();
        buildingsWideAnglePoints.clear();
    }

    /**
     * Handle a building intersection during profile construction. This logic
     * was ported from {@code ProfileBuilder.createBuildingCutPointAndCheckObstruction} and creates a
     * {@link CutPointWall} describing the intersection, computes the intersection
     * type (enter/exit) and updates the profile state.
     *
     * @param processedWallIndex index of the processed wall
     * @param intersection intersection coordinate on the wall
     * @param facetLine wall facet that was intersected
     * @param fullLine full profiling line segment
     * @param newCutPoints list to append the new cut point to
     * @param stopAtObstacleOverSourceReceiver controls behaviour when obstacle is above ray
     * @param profile the cut profile being constructed (mutated)
     * @return {@code false} when processing should stop; {@code true} to continue
     */
    public boolean createBuildingCutPointAndCheckObstruction(int processedWallIndex,
                                   Coordinate intersection,
                                   Wall facetLine,
                                   LineSegment fullLine,
                                   List<CutPoint> newCutPoints,
                                   boolean stopAtObstacleOverSourceReceiver,
                                   CutProfile profile) {
        CutPointWall wallCutPoint = new CutPointWall(processedWallIndex, intersection, facetLine.getLineSegment(),
                facetLine.getAlphas());
        if (facetLine.primaryKey >= 0) {
            wallCutPoint.setPk(facetLine.primaryKey);
        }
        newCutPoints.add(wallCutPoint);
        double zRayReceiverSource = Vertex.interpolateZ(intersection, fullLine.p0, fullLine.p1);
        Vector2D facetVector = Vector2D.create(facetLine.p0, facetLine.p1);
        Vector2D exteriorVector = facetVector.rotate(ProfileBuilder.LEFT_SIDE).normalize().multiply(ProfileBuilder.MILLIMETER);
        Coordinate exteriorPoint = exteriorVector.add(Vector2D.create(intersection)).toCoordinate();
        if (exteriorPoint.distance(fullLine.p0) < intersection.distance(fullLine.p0)) {
            wallCutPoint.intersectionType = CutPointWall.INTERSECTION_TYPE.BUILDING_ENTER;
        } else {
            wallCutPoint.intersectionType = CutPointWall.INTERSECTION_TYPE.BUILDING_EXIT;
        }

        if (zRayReceiverSource <= intersection.z) {
            profile.hasBuildingIntersection(true);
            return !stopAtObstacleOverSourceReceiver;
        } else {
            return true;
        }
    }
    /**
     * Add a building from a raw {@link Geometry} with attributes. Accepts only
     * Polygon geometries; other geometry types are ignored and logged as errors.
     *
     * @param geom geometry expected to be a {@link Polygon}
     * @param height building height
     * @param alphas facade absorption coefficients or similar per-vertex values
     * @param id primary identifier for the building
     */
    public void addBuilding(Geometry geom, double height, List<Double> alphas, int id) {
        if (!(geom instanceof Polygon)) {
            LOGGER.error("Building geometry should be Polygon");
            return;
        }
        Polygon poly = (Polygon) geom;
        Building b = new Building(poly, height, alphas, id, isZBuildings());
        addBuilding(b);
    }
    /**
     * Convenience overload: create a Polygon from an array of coordinates and
     * add the corresponding building. The method ensures the coordinate ring
     * is closed before creating the Polygon.
     *
     * @param coords array of polygon coordinates (may omit closing coordinate)
     * @param height building height
     * @param alphas per-vertex alpha values
     * @param id building identifier
     */
    public void addBuilding(Coordinate[] coords, double height, List<Double> alphas, int id) {
        if (coords == null || coords.length == 0) {
            return;
        }
        int l = coords.length;
        // Ensure coordinate ring is closed for polygon creation
        boolean closed = coords[0].equals2D(coords[l - 1]);
        Coordinate[] polyCoords = closed ? coords : java.util.Arrays.copyOf(coords, l + 1);
        if (!closed) {
            polyCoords[l] = new Coordinate(coords[0]);
        }
    Polygon poly = geometryFactory.createPolygon(polyCoords);
        addBuilding(poly, height, alphas, id);
    }
}
