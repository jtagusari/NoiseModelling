package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.math.Vector2D;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.RTreeUtils;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Geometry;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder.IntersectionType;

/**
 * Service responsible for managing ground absorption areas (ground effects)
 * and providing fast spatial lookup for those areas.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Store ground absorption features (polygons) with their coefficients.</li>
 *   <li>Index ground effect footprints in an STRtree for fast spatial queries.</li>
 *   <li>Export ground polygon edges as processed wall facets so ground
 *       effects participate in profile cut-point construction
 *       ({@link #exportFacetsToProcessedWalls}).</li>
 *   <li>Provide a query method to find the first ground absorption area
 *       intersecting a geometry ({@link #getIntersectingGroundAbsorption}).</li>
 *   <li>Handle ground-effect intersections during profile construction and
 *       append appropriate {@link CutPointGroundEffect} objects
 *       ({@link #createGroundCutPointAndCheckObstruction}).</li>
 * </ul>
 *
 * <p>Typical usage: callers add ground absorption polygons, call
 * {@link #exportFacetsToProcessedWalls} before runtime, then query via
 * {@link #getIntersectingGroundAbsorption} or let profile assembly call
 * {@link #createGroundCutPointAndCheckObstruction} when scanning a profile line.</p>
 */
public class GroundService implements ClearableService, ProcessedFacetsExportable {
    private final List<GroundAbsorption> groundAbsorptions = new ArrayList<>();
    private final STRtree groundEffectsRtree;
    private final GeometryFactory geometryFactory;

    public GroundService(int nodeCapacity) {
        this.groundEffectsRtree = new STRtree(nodeCapacity);
        this.geometryFactory = GeometryFactoryProvider.SHARED;
    }

    /**
     * Constructor allowing a custom GeometryFactory to be provided.
     */
    public GroundService(int nodeCapacity, GeometryFactory geometryFactory) {
        this.groundEffectsRtree = new STRtree(nodeCapacity);
        this.geometryFactory = geometryFactory != null ? geometryFactory : GeometryFactoryProvider.SHARED;
    }

    /**
     * No-arg constructor with a default node capacity.
     */
    public GroundService() {
        this(5);
    }

    public List<GroundAbsorption> getGroundAbsorptions() {
        return groundAbsorptions;
    }

    /**
     * Add a ground absorption feature to the service. Callers should index
     * ground effects by calling {@link #exportFacetsToProcessedWalls} before
     * using the R-tree for queries.
     *
     * @param g ground absorption feature to add
     */
    public void addGroundAbsorption(GroundAbsorption g) {
        groundAbsorptions.add(g);
    }

    public void insertGroundEffect(Envelope env, int index) {
        groundEffectsRtree.insert(env, index);
    }

    /**
     * Process ground absorption polygons into processed wall facets and insert into given ProcessedWallService.
     * This moved from ProfileBuilder.finishFeeding().
     *
     * <p>Each polygon is added to the internal R-tree and its boundary edges are
     * converted to processed wall facets so that the profile building logic can
     * treat ground surface boundaries consistently with other obstacles.</p>
     *
    * @param processedWallService destination service receiving processed wall facets
    * @param factory geometry factory used to build temporary geometries
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
        for (int j = 0; j < groundAbsorptions.size(); j++) {
            GroundAbsorption effect = groundAbsorptions.get(j);
            List<Polygon> polygons = new ArrayList<>();
            if (effect.geom instanceof Polygon) {
                polygons.add((Polygon) effect.geom);
            }
            if (effect.geom instanceof MultiPolygon) {
                MultiPolygon multi = (MultiPolygon) effect.geom;
                for (int i = 0; i < multi.getNumGeometries(); i++) {
                    polygons.add((Polygon) multi.getGeometryN(i));
                }
            }
            for (Polygon poly : polygons) {
                insertGroundEffect(poly.getEnvelopeInternal(), j);
                Coordinate[] coords = poly.getCoordinates();
                for (int k = 0; k < coords.length - 1; k++) {
                    LineSegment line = new LineSegment(coords[k], coords[k + 1]);
                    Wall w = new Wall(line, j, IntersectionType.GROUND_EFFECT).setProcessedWallIndex(processedWallService.getProcessedWalls().size());
                    // prefer service-level factory if caller provided null; otherwise use provided factory
                    GeometryFactory useFactory = factory != null ? factory : this.geometryFactory;
                    processedWallService.addProcessedWall(w, useFactory);
                }
            }
        }
    // Build the R-tree index after all inserts for consistent behaviour with other services
    this.groundEffectsRtree.build();
    }

    @Override
    public void exportFacetsToProcessedWalls(ProcessedWallService processedWallService) {
        exportFacetsToProcessedWalls(processedWallService, this.geometryFactory);
    }


    public void buildGroundEffectsRtree() {
        groundEffectsRtree.build();
    }

    public void clear() {
        groundAbsorptions.clear();
    }

    public STRtree getGroundEffectsRtree() {
        return groundEffectsRtree;
    }

    /**
     * Return the first ground absorption index that intersects the given geometry, or -1 if none.
     *
     * @param query geometry to test against ground effect footprints
     * @return index of matching ground absorption or -1 when none found
     */
    public int getIntersectingGroundAbsorption(Geometry query) {
        STRtree tree = this.getGroundEffectsRtree();
        if (tree != null) {
            List<?> res = RTreeUtils.query(tree, query.getEnvelopeInternal());
            for (Object groundEffectAreaIndex : res) {
                if (groundEffectAreaIndex instanceof Integer) {
                    GroundAbsorption groundAbsorption = this.groundAbsorptions.get((Integer) groundEffectAreaIndex);
                    if (groundAbsorption.geom.intersects(query)) {
                        return (Integer) groundEffectAreaIndex;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Handle a ground-effect intersection during profile construction.
     * <p>This method decides whether the profile enters a new ground effect
     * after the intersection point and appends a {@link CutPointGroundEffect}
     * describing the ground coefficient to {@code newCutPoints}.
     *
     * @param processedWallIndex processed wall index corresponding to this boundary
     * @param intersection intersection coordinate on the boundary
     * @param facetLine processed wall facet representing the boundary
     * @param fullLine full source->receiver line segment
     * @param newCutPoints list to append new cut points
    * @param stopAtObstacleOverSourceReceiver not used for ground effects but kept for signature compatibility
    * @param profile current CutProfile being constructed
    * @param factory GeometryFactory used to build temporary points/geometries
     * @return always true (ground effects do not abort profile assembly)
     */
    public boolean createGroundCutPointAndCheckObstruction(int processedWallIndex,
                                       Coordinate intersection,
                                       Wall facetLine,
                                       LineSegment fullLine,
                                       List<CutPoint> newCutPoints,
                                       boolean stopAtObstacleOverSourceReceiver,
                                       CutProfile profile,
                                       GeometryFactory factory) {
        // retrieve the ground coefficient after the intersection in the direction of the profile
        Vector2D directionAfter = Vector2D.create(fullLine.p0, fullLine.p1).normalize().multiply(ProfileBuilder.MILLIMETER);
        Point afterIntersectionPoint = factory.createPoint(Vector2D.create(intersection).add(directionAfter).toCoordinate());
        GroundAbsorption groundAbsorption = this.groundAbsorptions.get(facetLine.getOriginId());
        if (groundAbsorption.geom.intersects(afterIntersectionPoint)) {
            // we enter a new ground effect
            newCutPoints.add(new CutPointGroundEffect(processedWallIndex, intersection, groundAbsorption.getCoefficient()));
        } else {
            // we exit a ground surface, check for another ground surface at this point
            int groundSurfaceIndex = this.getIntersectingGroundAbsorption(afterIntersectionPoint);
            if (groundSurfaceIndex == -1) {
                // no new ground effect, fall back to default
                newCutPoints.add(new CutPointGroundEffect(-1, intersection, Scene.DEFAULT_G));
            } else {
                GroundAbsorption nextGroundAbsorption = this.groundAbsorptions.get(groundSurfaceIndex);
                if (!nextGroundAbsorption.geom.touches(groundAbsorption.geom)) {
                    newCutPoints.add(new CutPointGroundEffect(groundSurfaceIndex,
                            afterIntersectionPoint.getCoordinate(),
                            nextGroundAbsorption.getCoefficient()));
                }
            }
        }
        return true;
    }

    /**
     * Compute a hash code representing the current state of this GroundService.
     * The hash is based on the number of ground absorption areas.
     * 
     * @return Hash code representing the service state
     */
    @Override
    public int hashCode() {
        return Objects.hash(groundAbsorptions);
    }
}
