package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty.SourceType;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointTopography;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointWall;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointBridgeWall;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Compute candidate diffraction points for CNOSSOS path construction.
 *
 * <p>Given an {@link AcousticPathConfiguration} (which contains the 2D transformed
 * cut-point coordinates and the original {@link CutProfile}), this class
 * identifies the subset of points that may produce diffraction (walls, topography)
 * and reduces them by computing the convex hull along the profile. The resulting
 * ordered list of {@link Coordinate} values is suitable as input for downstream
 * path building (e.g. selection of diffraction vertices).
 */
public class DiffractionPointCalculator {


    /**
     * Compute diffraction candidate points from the runtime configuration.
     *
     * <p>Workflow:
     * <ol>
     *   <li>collect valid cut-profile points that can act as diffractors (source,
     *       receiver, topographic crests, wall tops),</li>
     *   <li>if more than two points are collected, compute the convex hull and
     *       reduce/clean the hull coordinates (remove duplicates and invalid
     *       coordinates), otherwise return the collected points unchanged.</li>
     * </ol>
     *
     * @param configuration runtime configuration containing {@code cutPointCoordinates2D}
     *                      and the original {@link CutProfile}
     * @return ordered list of 2D {@link Coordinate} representing diffraction candidates
     */
    public static List<Coordinate> computeHorizontalEdgePivotPoints(AcousticPathConfiguration configuration) {

        // Collect valid diffraction points
        List<Coordinate> candidateCoordinates = collectHorizontalEdgePivotCandidates(configuration);

        SourceBridgeProperty sourceProperty = configuration.getCutProfile().getSource().getSourceBridgeProperty();
        SourceType sourceType = SourceType.SOURCE_NOT_RELATED_TO_BRIDGE;
        if (sourceProperty != null){
            sourceType = configuration.getCutProfile().getSource().getSourceBridgeProperty().getSourceType();
        }

        if (sourceType == SourceType.ACTUAL_SOURCE_ON_BRIDGE || sourceType == SourceType.SOURCE_NOT_RELATED_TO_BRIDGE) {
        return extractPivotPointsUsingConvexHull(configuration, candidateCoordinates);
    }

        List<Coordinate> downwardBridgeEdges = collectDownwardBridgeEdges(configuration);
        if (downwardBridgeEdges == null || downwardBridgeEdges.size() == 0) {
            return extractPivotPointsUsingConvexHull(configuration, candidateCoordinates);
        }
        return extractPivotPointsUsingConvexHull(configuration, candidateCoordinates, downwardBridgeEdges);
    }

    /**
     * Compute a trimmed convex-hull along the cut-profile and return the
     * coordinates that lie between the profile's first and last point.
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Uses JTS {@link ConvexHull} to compute the hull from the provided
     *       input coordinates.</li>
     *   <li>Finds the indices of the profile's first and last transformed points
     *       on the hull and extracts the sub-array in between (inclusive).</li>
     *   <li>Performs a union() on the LineString to remove duplicated coordinates
     *       that can appear because of closed rings produced by the hull
     *       computation.</li>
     *   <li>Delegates filtering of invalid / infinite coordinates to
     *       {@link #processConvexHullResults}.</li>
     * </ul>
     *
     * @param configuration runtime configuration (used to access cut profile & coordinates)
     * @param candidateCoordinates input coordinates (usually source, candidate diffractors, receiver)
     * @return processed list of coordinates forming the ordered diffraction candidates
     */
    private static List<Coordinate> extractPivotPointsUsingConvexHull(AcousticPathConfiguration configuration,List<Coordinate> candidateCoordinates, List<Coordinate> downwardBridgeEdges) {

        if (downwardBridgeEdges == null || downwardBridgeEdges.size() == 0) {
            throw new IllegalArgumentException("No downward bridge edge provided");
        }

        /** TODO Only the first downward bridge edge is considered at this moment*/
        Coordinate downwardEdgeCoordinate = downwardBridgeEdges.get(0);

        List<Coordinate> pivotPointsWithoutDownwardEdges = extractPivotPointsUsingConvexHull(configuration, candidateCoordinates);
        LineSegment segment = new LineSegment(
            pivotPointsWithoutDownwardEdges.get(0),
            pivotPointsWithoutDownwardEdges.get(1)
        );

        if (downwardEdgeCoordinate.x <= segment.p0.x || downwardEdgeCoordinate.x >= segment.p1.x) {
            throw new IllegalArgumentException("Downward bridge edge is out of the range of source and the first cut-point");
        }

        double ratio = (downwardEdgeCoordinate.x - segment.p0.x) / (segment.p1.x - segment.p0.x);
        double interpY = segment.p0.y + ratio * (segment.p1.y - segment.p0.y);
        if (downwardEdgeCoordinate.y > interpY) {
            return pivotPointsWithoutDownwardEdges;
        }

        List<Coordinate> newCandidateCoordinates = new ArrayList<>();
        newCandidateCoordinates.add(downwardEdgeCoordinate);
        for (int i = 1; i < candidateCoordinates.size(); i++) {
            newCandidateCoordinates.add(candidateCoordinates.get(i));
        }
        List<Coordinate> pivotPointsWithDownwardEdges = extractPivotPointsUsingConvexHull(configuration, newCandidateCoordinates);
        pivotPointsWithDownwardEdges.add(0, candidateCoordinates.get(0));
        return pivotPointsWithDownwardEdges;

    }


    private static List<Coordinate> extractPivotPointsUsingConvexHull(AcousticPathConfiguration configuration,List<Coordinate> candidateCoordinates) {
        int startIndex = 0;
        int endIndex = candidateCoordinates.size() - 1;
        return extractPivotPointsUsingConvexHull(configuration, candidateCoordinates, startIndex, endIndex);
    }

    private static List<Coordinate> extractPivotPointsUsingConvexHull(AcousticPathConfiguration configuration,List<Coordinate> candidateCoordinates, int startIndex, int endIndex) {

        if (startIndex < 0 || endIndex >= candidateCoordinates.size() || startIndex > endIndex) {
            throw new IllegalArgumentException("Invalid start/end indices");
        }

        List<Coordinate> slicedCandidateCoordinates = candidateCoordinates.subList(startIndex, endIndex + 1);

        if (slicedCandidateCoordinates.size() < 2) {
            throw new IllegalArgumentException("At least two input points are required for convex hull computation");
        }
        
        if (slicedCandidateCoordinates.size() == 2) {
            return slicedCandidateCoordinates;
        }
        
        GeometryFactory geomFactory = new GeometryFactory();
        Coordinate[] coordsArray = slicedCandidateCoordinates.toArray(new Coordinate[0]);
        ConvexHull convexHull = new ConvexHull(coordsArray, geomFactory);
        Coordinate[] convexHullCoordinates = convexHull.getConvexHull().getCoordinates();

        Coordinate firstPt = coordsArray[0];
        Coordinate lastPt = coordsArray[coordsArray.length - 1];
        int indexFirst = Arrays.asList(convexHullCoordinates).indexOf(firstPt);
        int indexLast = Arrays.asList(convexHullCoordinates).lastIndexOf(lastPt);
        
        if (indexFirst == -1 || indexLast == -1 || indexFirst > indexLast) {
            CutProfile cutProfile = configuration.getCutProfile();
            throw new IllegalArgumentException("Wrong input data " + cutProfile.toString());
        }
        
        Coordinate[] upperConvexHullCoordinates = Arrays.copyOfRange(convexHullCoordinates, indexFirst, indexLast + 1);
        CoordinateSequence coordSequence = geomFactory.getCoordinateSequenceFactory().create(upperConvexHullCoordinates);
        Geometry geom = geomFactory.createLineString(coordSequence);
        Geometry uniqueGeom = geom.union(); // Removes duplicate coordinates
        upperConvexHullCoordinates = uniqueGeom.getCoordinates();

        List<Coordinate> pathPoints = new ArrayList<>();

        if (upperConvexHullCoordinates.length == 3) {
            return Arrays.asList(upperConvexHullCoordinates);
        }

        for (int j = 0; j < upperConvexHullCoordinates.length; j++) {
            if (upperConvexHullCoordinates[j].y == Double.MAX_VALUE || Double.isInfinite(upperConvexHullCoordinates[j].y)) {
                    continue; // Skip this point as it's not part of the hull
                }
            pathPoints.add(upperConvexHullCoordinates[j]);
        }
        
        return pathPoints;
    }

    /**
     * Build the input list of coordinates to be considered for convex-hull
     * computation. The returned list always starts with the source transformed
     * coordinate and ends with the receiver transformed coordinate.
     *
     * <p>Intermediate points are included when they represent either a
     * topographic crest ({@link CutPointTopography}) or the top of a wall
     * ({@link CutPointWall})—specifically when the point's Z differs from the
     * ground Z (the wall top as opposed to its base).
     *
     * @param configuration runtime configuration containing the transformed 2D
     *                      cut point coordinates and the original {@link CutProfile}
     * @return list of coordinates to feed the convex-hull routine
     */
    private static List<Coordinate> collectHorizontalEdgePivotCandidates(AcousticPathConfiguration configuration){
        List<Coordinate> cutPointCoordinates2D = configuration.getCutPointCoordinates2D();
        List<CutPoint> cutProfilePoints = configuration.getCutProfilePoints();
        List<Coordinate> horizontalEdgePivotCandidates = new ArrayList<>();
        SourceBridgeProperty sourceProperty = configuration.getCutProfile().getSource().getSourceBridgeProperty();
        SourceType sourceType = SourceType.SOURCE_NOT_RELATED_TO_BRIDGE;
        if (sourceProperty != null) {
            sourceType = configuration.getCutProfile().getSource().getSourceBridgeProperty().getSourceType();
        };
        
        // Add source position
        horizontalEdgePivotCandidates.add(cutPointCoordinates2D.get(0));
        
        // Add valid diffraction point, building/walls/dem
        for (int idPoint = 1; idPoint < cutProfilePoints.size() - 1; idPoint++) {
            CutPoint currentPoint = cutProfilePoints.get(idPoint);
            Coordinate currentPointCoordinate2D = cutPointCoordinates2D.get(idPoint);
            // Only add the point at the top of the wall, not the point at the bottom of the wall
            if (
                currentPoint instanceof CutPointTopography
                || (currentPoint instanceof CutPointWall && currentPoint.hasObstacle())
            ) {
                horizontalEdgePivotCandidates.add(currentPointCoordinate2D);
            } else if(currentPoint instanceof CutPointBridgeWall && sourceType == SourceType.ACTUAL_SOURCE_ON_BRIDGE) {
                horizontalEdgePivotCandidates.add(currentPointCoordinate2D);
            }
        }
        
        // Add receiver position
        horizontalEdgePivotCandidates.add(cutPointCoordinates2D.get(cutPointCoordinates2D.size() - 1));

        return horizontalEdgePivotCandidates;
    }

    private static List<Coordinate> collectDownwardBridgeEdges(AcousticPathConfiguration configuration){
        SourceType sourceType = configuration.getCutProfile().getSource().getSourceBridgeProperty().getSourceType();
        List<Coordinate> downwardBridgeEdges = new ArrayList<>();

        if (sourceType != SourceType.ACTUAL_SOURCE_ON_BRIDGE || sourceType != SourceType.SOURCE_NOT_RELATED_TO_BRIDGE) {
            return downwardBridgeEdges;
        }

        List<CutPoint> cutProfilePoints = configuration.getCutProfilePoints();
        List<Coordinate> cutPointCoordinates2D = configuration.getCutPointCoordinates2D();
        for (int idPoint = 1; idPoint < cutProfilePoints.size() - 1; idPoint++) {
            CutPoint currentPoint = cutProfilePoints.get(idPoint);
            Coordinate currentPointCoordinate2D = cutPointCoordinates2D.get(idPoint);
            // Only add the point at the top of the wall, not the point at the bottom of the wall
            if (currentPoint instanceof CutPointBridgeWall && ((CutPointBridgeWall)currentPoint).getWallDirection() == CutPointBridgeWall.WallDirection.DOWNWARD) {
                downwardBridgeEdges.add(currentPointCoordinate2D);
            }
        }
        return downwardBridgeEdges;
    }
}
