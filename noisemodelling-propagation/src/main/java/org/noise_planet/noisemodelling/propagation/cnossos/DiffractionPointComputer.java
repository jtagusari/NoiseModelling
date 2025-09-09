package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointTopography;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointWall;
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
public class DiffractionPointComputer {


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
    public static List<Coordinate> computeDiffractionPoints(AcousticPathConfiguration configuration) {

        // Collect valid diffraction points
        List<Coordinate> convexHullInput = collectValidDiffractionPoints(configuration);

        // Compute convex hull and process results
        return calculateConvexHull(configuration, convexHullInput);
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
     * @param convexHullInput input coordinates (usually source, candidate diffractors, receiver)
     * @return processed list of coordinates forming the ordered diffraction candidates
     */
    private static List<Coordinate> calculateConvexHull(AcousticPathConfiguration configuration,List<Coordinate> convexHullInput) {

        List<Coordinate> cutPointCoordiantes2D = configuration.getCutPointCoordinates2D();
        Coordinate firstPt = cutPointCoordiantes2D.get(0);
        Coordinate lastPt = cutPointCoordiantes2D.get(cutPointCoordiantes2D.size() - 1);
        CutProfile cutProfile = configuration.getCutProfile();
        List<Coordinate> convexHullPoints = new ArrayList<>();

        if (convexHullInput.size() > 2) {
            GeometryFactory geomFactory = new GeometryFactory();
            Coordinate[] coordsArray = convexHullInput.toArray(new Coordinate[0]);
            ConvexHull convexHull = new ConvexHull(coordsArray, geomFactory);
            Coordinate[] convexHullCoords = convexHull.getConvexHull().getCoordinates();
            int indexFirst = Arrays.asList(convexHull.getConvexHull().getCoordinates()).indexOf(firstPt);
            int indexLast = Arrays.asList(convexHull.getConvexHull().getCoordinates()).lastIndexOf(lastPt);
            
            if (indexFirst == -1 || indexLast == -1 || indexFirst > indexLast) {
                throw new IllegalArgumentException("Wrong input data " + cutProfile.toString());
            }
            
            convexHullCoords = Arrays.copyOfRange(convexHullCoords, indexFirst, indexLast + 1);
            CoordinateSequence coordSequence = geomFactory.getCoordinateSequenceFactory().create(convexHullCoords);
            Geometry geom = geomFactory.createLineString(coordSequence);
            Geometry uniqueGeom = geom.union(); // Removes duplicate coordinates
            convexHullCoords = uniqueGeom.getCoordinates();
            
            // Process the convex hull results
            convexHullPoints = processConvexHullResults(convexHullCoords);
        } else {
            convexHullPoints = convexHullInput;
        }
        
        return convexHullPoints;
    }

    /**
     * Filter and convert convex-hull coordinate array into the final list used
     * as diffraction candidates.
     *
     * Behavior:
     * <ul>
     *   <li>If the hull has exactly three coordinates (minimal hull), all three
     *       are returned.</li>
     *   <li>Otherwise coordinates with invalid Y values (equal to
     *       {@code Double.MAX_VALUE} or infinite) are removed.</li>
     * </ul>
     *
     * @param convexHullCoords coordinates returned by the hull union/cleanup step
     * @return list of valid coordinates in hull order
     */
    private static List<Coordinate> processConvexHullResults(Coordinate[] convexHullCoords) {
        List<Coordinate> convexHullPoints = new ArrayList<>();
        
        // Convert the result back to your format (List<Point2D> pts)
        if (convexHullCoords.length == 3) {
            convexHullPoints = Arrays.asList(convexHullCoords);
        } else {
            for (int j = 0; j < convexHullCoords.length; j++) {
                // Check if the y-coordinate is valid (not equal to Double.MAX_VALUE and not infinite)
                if (convexHullCoords[j].y == Double.MAX_VALUE || Double.isInfinite(convexHullCoords[j].y)) {
                    continue; // Skip this point as it's not part of the hull
                }
                convexHullPoints.add(convexHullCoords[j]);
            }
        }
        
        return convexHullPoints;
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
    private static List<Coordinate> collectValidDiffractionPoints(AcousticPathConfiguration configuration){
        List<Coordinate> cutPointCoordinates2D = configuration.getCutPointCoordinates2D();
        List<CutPoint> cutProfilePoints = configuration.getCutProfilePoints();
        List<Coordinate> convexHullInput = new ArrayList<>();
        
        // Add source position
        convexHullInput.add(cutPointCoordinates2D.get(0));
        
        // Add valid diffraction point, building/walls/dem
        for (int idPoint = 1; idPoint < cutProfilePoints.size() - 1; idPoint++) {
            CutPoint currentPoint = cutProfilePoints.get(idPoint);
            // We only add the point at the top of the wall, not the point at the bottom of the wall
            if (currentPoint instanceof CutPointTopography
                    || (currentPoint instanceof CutPointWall
                    && Double.compare(currentPoint.getCoordinate().z, currentPoint.getzGround()) != 0)) {
                convexHullInput.add(cutPointCoordinates2D.get(idPoint));
            }
        }
        
        // Add receiver position
        convexHullInput.add(cutPointCoordinates2D.get(cutPointCoordinates2D.size() - 1));
        
        return convexHullInput;
    }

}
