package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.math.Vector2D;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty.SourceType;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.RTreeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Small helper utilities extracted from {@code ProfileBuilder} to centralize
 * line-splitting and obstacle query logic so {@code ProfileBuilder} can
 * delegate responsibilities and remain focused on orchestration.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Split long source->receiver segments into shorter pieces for stable
 *       spatial-index queries and for accurate interpolation of Z values.</li>
 *   <li>Query obstacle/service spatial indexes (processed walls) and dispatch
 *       intersection handling to the appropriate service (building, wall,
 *       bridge, ground) to produce profile cut points.</li>
 *   <li>Collect cut points discovered by obstacle handlers and append them
 *       to the provided {@link CutProfile}.</li>
 * </ul>
 *
 * <p>Design notes: methods in this utility are stateless and operate only on
 * their inputs. They require that service arguments (for example
 * {@link ProcessedWallService#getProcessedRtree()} and related indexes) have been
 * prepared by the caller (indexed) before invocation.</p>
 */
public final class ProfileUtils {
    private ProfileUtils() {}

    public static List<LineSegment> splitSegment(Coordinate c0, Coordinate c1, double maxLineLength) {
        List<LineSegment> lines = new ArrayList<>();
    LineSegment fullLine = new LineSegment(c0, c1);
    double l = c0.distance(c1);
        if(l < maxLineLength) {
            lines.add(fullLine);
        }
        else {
            double frac = maxLineLength / l;
            for(int i = 0; i < l / maxLineLength; i++) {
                Coordinate p0 = fullLine.pointAlong(i * frac);
                p0.z = c0.z + (c1.z - c0.z) * i * frac;
                Coordinate p1 = fullLine.pointAlong(Math.min((i + 1) * frac, 1.0));
                p1.z = c0.z + (c1.z - c0.z) * Math.min((i + 1) * frac, 1.0);
                lines.add(new LineSegment(p0, p1));
            }
        }
        return lines;
    }

    /**
     * Split the segment between {@code c0} and {@code c1} into smaller
     * {@link LineSegment}s whose length does not exceed {@code maxLineLength}.
     *
     * <p>The method preserves and interpolates Z coordinates linearly when
     * creating intermediate segment endpoints. If the original segment length
     * is less than {@code maxLineLength}, a single segment equal to the full
     * line is returned.</p>
     *
     * @param c0 segment start coordinate (may contain z)
     * @param c1 segment end coordinate (may contain z)
     * @param maxLineLength maximum allowed length per returned segment
     * @return list of LineSegment parts covering the full segment in order
     */

    /**
     * Add obstacle cut points to the profile. This is a static port of the logic
     * previously implemented inside ProfileBuilder.addObstacleCutPts.
     */
    public static void addObstacleCutPts(LineSegment fullLine,
                                         CutProfile profile,
                                         boolean stopAtObstacleOverSourceReceiver,
                                         double maxLineLength,
                                         BuildingService buildingService,
                                         WallService wallService,
                                         BridgeService bridgeService,
                                         GroundService groundService,
                                         ProcessedWallService processedWallService,
                                         GeometryFactory factory) {
    /**
     * Add obstacle cut points to the profile. This is a static port of the logic
     * previously implemented inside ProfileBuilder.addObstacleCutPts.
     *
     * <p>Behavior summary:
     * <ul>
     *   <li>Split the full profiling line using {@link #splitSegment} to avoid
     *       oversized envelopes when querying spatial indexes.</li>
     *   <li>For each sub-segment, query {@code ProcessedWallService.getProcessedRtree()}
     *       and for each hit dispatch to the specific handler depending on the
     *       processed wall type (BUILDING/WALL/BRIDGE/GROUND_EFFECT).</li>
     *   <li>Handlers (for example {@link BuildingService#createBuildingCutPointAndCheckObstruction}
     *       or {@link ProcessedWallService#createWallCutPointAndCheckObstruction}) may decide to stop processing
     *       early by returning {@code false}; in that case this method returns
     *       immediately and the pending cut-points discovered so far are still
     *       appended to the profile in the finally block.</li>
     * </ul>
     *
     * <p>Inputs:
     * <ul>
     *   <li>{@code fullLine} the full source->receiver segment.</li>
     *   <li>{@code profile} the CutProfile to which discovered cut points will be appended.</li>
     *   <li>Service parameters provide access to processed walls, buildings,
     *       bridges and ground effects. These services are queried but not
     *       mutated by this method.</li>
     * </ul>
     *
     * Side-effect: discovered cut points are inserted into {@code profile}
     * at the end of the method (in the finally block) via
     * {@link CutProfile#insertCutPoint}.</p>
     */
        java.util.Set<Integer> processed = new HashSet<>();
        List<LineSegment> lines = splitSegment(fullLine.p0, fullLine.p1, maxLineLength);
        List<CutPoint> newCutPoints = new LinkedList<>();
        boolean sortCutPoints = true;
        
        SourceType sourceType = profile.getSource().getSourceBridgeProperty().getSourceType();
        if (sourceType == SourceType.MIRROR_SOURCE || sourceType == SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE) {
            CutPointBridgeWall bridgeCutPoint = bridgeService.calculateBridgeMirrorCutpoint(profile);
            newCutPoints.add(bridgeCutPoint);
            sortCutPoints = false;
            lines = splitSegment(bridgeCutPoint.getCoordinate(), fullLine.p1, maxLineLength);
        }

        for (int j = 0; j < lines.size()
                && !((profile.hasBuildingIntersection() || profile.hasBridgeIntersection()) && stopAtObstacleOverSourceReceiver); j++) {
            LineSegment line = lines.get(j);
            for (Object result : RTreeUtils.query(processedWallService.getProcessedRtree(), new org.locationtech.jts.geom.Envelope(line.p0, line.p1))) {
                if (!(result instanceof Integer) || processed.contains((Integer) result)) {
                    continue;
                }
                processed.add((Integer) result);
                int i = (Integer) result;
                Wall processedWall = processedWallService.getProcessedWalls().get(i);
                org.locationtech.jts.geom.Coordinate intersection = fullLine.intersection(processedWall.getLineSegment());
                if (intersection == null) {
                    continue;
                }
                intersection = new org.locationtech.jts.geom.Coordinate(intersection);
                if (!Double.isNaN(processedWall.p0.z) && !Double.isNaN(processedWall.p1.z)) {
                    if (Double.compare(processedWall.p0.z, processedWall.p1.z) == 0) {
                        intersection.z = processedWall.p0.z;
                    } else {
                        intersection.z = org.locationtech.jts.triangulate.quadedge.Vertex.interpolateZ(intersection, processedWall.p0, processedWall.p1);
                    }
                }
                boolean continueCalculation = createGroundCutPointAndCheckObstruction(buildingService, wallService, bridgeService, groundService, processedWallService, processedWall.type, i, intersection, processedWall, fullLine, newCutPoints, stopAtObstacleOverSourceReceiver, profile, factory);
                if (!continueCalculation) {
                    return;
                }
            }
        }
        profile.insertCutPoint(sortCutPoints, newCutPoints.toArray(CutPoint[]::new));
    }

    private static boolean createGroundCutPointAndCheckObstruction(BuildingService buildingService,
                                         WallService wallService,
                                         BridgeService bridgeService,
                                         GroundService groundService,
                                         ProcessedWallService processedWallService,ProfileBuilder.IntersectionType wallType, int wallIndex, Coordinate intersection, Wall processedWall, LineSegment fullLine, List<CutPoint> newCutPoints, boolean stopAtObstacleOverSourceReceiver, CutProfile profile, GeometryFactory factory) {
        boolean hasObstacleIntersection;
        switch (wallType) {
            case BUILDING:
                hasObstacleIntersection = buildingService.createBuildingCutPointAndCheckObstruction(wallIndex, intersection, processedWall, fullLine, newCutPoints);
                profile.hasBuildingIntersection(profile.hasBuildingIntersection() || hasObstacleIntersection);
                return profile.hasBuildingIntersection() && stopAtObstacleOverSourceReceiver ? false : true;

            case WALL:
                hasObstacleIntersection =  wallService.createWallCutPointAndCheckObstruction(wallIndex, intersection, processedWall, fullLine, newCutPoints);
                profile.hasBuildingIntersection(profile.hasBuildingIntersection() || hasObstacleIntersection);
                return profile.hasBuildingIntersection() && stopAtObstacleOverSourceReceiver ? false : true;
            case BRIDGE:
                return bridgeService.createBridgeCutPointAndCheckObstruction(wallIndex, intersection, processedWall, fullLine, newCutPoints, stopAtObstacleOverSourceReceiver, profile);
            case GROUND_EFFECT:
                return groundService.createGroundCutPointAndCheckObstruction(wallIndex, intersection, processedWall, fullLine, newCutPoints, stopAtObstacleOverSourceReceiver, profile, factory);
            default:
                throw new IllegalArgumentException("Unknown wall type: " + wallType);
        }
    }

    private static final double MILLIMETER = 1.0;
    private static final double LEFT_SIDE = Math.PI / 2;

    public static boolean isIntersectionEntry(Coordinate intersection, LineSegment propagationLine, LineSegment polygonFacetLine) {
        Vector2D facetVector = Vector2D.create(polygonFacetLine.p0, polygonFacetLine.p1);
        Vector2D exteriorVector = facetVector.rotate(LEFT_SIDE).normalize().multiply(MILLIMETER);
        Coordinate exteriorPoint = exteriorVector.add(Vector2D.create(intersection)).toCoordinate();
        return  exteriorPoint.distance(propagationLine.p0) < intersection.distance(propagationLine.p0);
    }
    
    public static boolean isIntersectionEntry(Coordinate intersection, LineSegment propagationLine, Wall wall) {
        LineSegment polygonFaceLine = wall.getLineSegment();
        return isIntersectionEntry(intersection, propagationLine, polygonFaceLine);
    }
}
