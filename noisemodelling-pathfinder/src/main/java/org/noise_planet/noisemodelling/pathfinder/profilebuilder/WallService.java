package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder.IntersectionType;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for managing raw walls and their processed facets used
 * during profile construction.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Store raw wall definitions (edges) and expose basic accessors.</li>
 *   <li>Maintain a spatial index (`wallTree`) for raw walls to support fast lookup.</li>
 *   <li>Handle thin-wall intersection processing and produce cut-points for
 *       profile assembly ({@link #createWallCutPoint(RayWallIntersection)}).</li>
 *   <li>Update wall endpoints Z values from topography/DEM via
 *       {@link #computeElevations(ProfileBuilder)}.</li>
 * </ul>
 *
 * <p>The service separates raw geometry ingestion from the processed facets
 * used by acoustic path computations; callers typically feed walls, then call
 * {@link #exportFacetsToProcessedWalls} and
 * {@link ProcessedWallService#buildProcessedWallRtree()} before
 * running profile computations.</p>
 */
public class WallService implements FrequencyInitializable, ElevationComputable, ClearableService, ProcessedFacetsExportable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WallService.class);
    private final List<Wall> walls = new ArrayList<>();
    private final STRtree wallTree;
    private final GeometryFactory geometryFactory;

    /**
     * Create a WallService with the given STRtree node capacity.
     *
     * @param nodeCapacity node capacity for created STRtrees
     */
    public WallService(int nodeCapacity) {
        this(nodeCapacity, GeometryFactoryProvider.SHARED);
    }

    /**
     * Constructor accepting a custom GeometryFactory.
     */
    public WallService(int nodeCapacity, GeometryFactory geometryFactory) {
        this.wallTree = new STRtree(nodeCapacity);
        this.geometryFactory = geometryFactory != null ? geometryFactory : GeometryFactoryProvider.SHARED;
    }

    /**
     * No-arg constructor with default node capacity for STRtrees.
     */
    public WallService() {
    this(5);
    }

    /**
     * Insert a raw wall into the service and index it in the raw wall tree.
     * The raw wall is stored unchanged; conversion to processed facets happens
     * when {@link #exportFacetsToProcessedWalls} is called.
     *
     * @param wall wall geometry and metadata to add
     */
    public void addWall(Wall wall) {
        walls.add(wall);
        wallTree.insert(new Envelope(wall.getP0(), wall.getP1()), walls.size());
    }

    /**
     * Process raw walls into processed wall facets and insert them into processedRtree.
     * This was previously done inside ProfileBuilder.finishFeeding().
     *
    * <p>Each raw wall may produce one or more processed wall facets. Processed
    * walls carry additional metadata (processed index, primary key, alphas)
    * and are the objects used during intersection queries.</p>
    *
    * <p>Contract with {@link ProcessedWallService}:
    * <ul>
    *   <li>This method inserts processed wall facets into {@code processedWallService}
    *       using {@link ProcessedWallService#addProcessedWall}.</li>
    *   <li>Callers must invoke {@link ProcessedWallService#buildProcessedWallRtree()}
    *       after all export methods from all services have been executed and before
    *       issuing any queries against the processed-wall index.</li>
    * </ul>
    */
    @Override
    public void exportFacetsToProcessedWalls(ProcessedWallService processedWallService, GeometryFactory factory) {
        for (int j = 0; j < getWallCount(); j++) {
            Wall wall = getWall(j);
            Coordinate[] coords = new Coordinate[]{wall.getP0(), wall.getP1()};
            for (int i = 0; i < coords.length - 1; i++) {
                LineSegment lineSegment = new LineSegment(coords[i], coords[i + 1]);
                Wall w = new Wall(lineSegment, j, IntersectionType.WALL).setProcessedWallIndex(processedWallService.getProcessedWalls().size());
                w.copyAlphas(wall);
                w.setPrimaryKey(wall.primaryKey);
                processedWallService.addProcessedWall(w, factory);
            }
        }
    }
    

    /**
     * Create a thin-wall cut point from a processed-wall intersection.
     *
     * <p>This method converts {@link RayWallIntersection} data into a
     * {@link CutPointWall} used by profile assembly. It assigns
     * {@link CutPointWall.INTERSECTION_TYPE#THIN_WALL_ENTER_EXIT}, propagates
     * the wall primary key when available, and computes obstruction state by
     * comparing the intersection elevation with the interpolated source-receiver
     * ray elevation at the same XY location.</p>
     *
     * @param rayWallIntersection intersection context containing wall index,
     *                            intersection coordinate, full source-receiver ray,
     *                            and processed wall metadata
     * @return populated wall cut point ready to be inserted into a {@link CutProfile}
     */
    public CutPointWall createWallCutPoint(RayWallIntersection rayWallIntersection) {
        int wallIndex = rayWallIntersection.getWallIndex();
        Coordinate intersection = rayWallIntersection.getIntersection();
        LineSegment fullLine = rayWallIntersection.getRay();
        Wall wall = rayWallIntersection.getProcessedWall();


        CutPointWall cutPointWall = new CutPointWall(wallIndex, intersection, wall.getLineSegment(), wall.getAlphas());
        cutPointWall.intersectionType = CutPointWall.INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT;
        if (wall.primaryKey >= 0) {
            cutPointWall.setPk(wall.primaryKey);
        }
        
        double zRayReceiverSource = Vertex.interpolateZ(intersection, fullLine.p0, fullLine.p1);
        cutPointWall.setObstructingAcousticRay(zRayReceiverSource <= intersection.z);
        
        return cutPointWall;
    }

    /**
     * Use the service-level configured GeometryFactory to build processed wall facets.
     */

    @Override
    public void exportFacetsToProcessedWalls(ProcessedWallService processedWallService) {
        exportFacetsToProcessedWalls(processedWallService, this.geometryFactory);
    }


    public List<Wall> getWalls() {
        return new LoggingUnmodifiableList<>(walls, "WallService.walls");
    }

    public int getWallCount() {
        return walls.size();
    }

    public Wall getWall(int id) {
        return walls.get(id);
    }

    
    public void clear() {
        walls.clear();
    }


    public STRtree getWallTree() {
        return wallTree;
    }
    /**
     * Update wall endpoints Z values using the provided {@link ProfileBuilder}
     * which exposes topography via {@link ProfileBuilder#getZGround(org.locationtech.jts.geom.Coordinate)}.
     *
     * <p>If an endpoint Z is NaN or zero, this method sets it to
     * {@code wall.height + zGround} so processed wall geometry reflects ground
     * elevation before further profile calculations.</p>
     *
     * @param profileBuilder ProfileBuilder used to fetch ground elevations
     */
    
    @Override
    public void computeElevations(ProfileBuilder profileBuilder) {
        for (Wall w : walls) {
            if (Double.isNaN(w.getP0().z) || w.getP0().z == 0.0) {
                w.getP0().z = w.height + profileBuilder.getZGround(w.getP0());
            }
            if (Double.isNaN(w.getP1().z) || w.getP1().z == 0.0) {
                w.getP1().z = w.height + profileBuilder.getZGround(w.getP1());
            }
        }
    }

    /**
     * Initialize frequency-dependent arrays on raw and processed walls managed by this service.
     * @param exactFrequencyArray list of exact frequency values used to compute alphas
     */
    @Override
    public void initializeFrequencyDependentData(List<Double> exactFrequencyArray) {
        if (exactFrequencyArray == null) {
            LOGGER.warn("WallService.initializeFrequencyDependentData: exactFrequencyArray is null");
            return;
        }
        LOGGER.debug("WallService.initializeFrequencyDependentData: called with exactFrequencyArray.size={}", exactFrequencyArray.size());
        for (Wall w : walls) {
            w.initialize(exactFrequencyArray);
        }
    }

    /**
     * Compute a hash code representing the current state of this WallService.
     * The hash is based on the number of walls and their basic properties.
     * 
     * @return Hash code representing the service state
     */
    @Override
    public int hashCode() {
        return Objects.hash(walls);
    }
}
