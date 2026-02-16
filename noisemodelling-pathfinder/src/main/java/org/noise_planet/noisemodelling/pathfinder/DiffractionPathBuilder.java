package org.noise_planet.noisemodelling.pathfinder;

import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

// Unused imports removed after moving implementation

/**
 * Diffraction helpers moved from PathFinder. Computes vertical-edge
 * diffraction profiles and side hulls.
 */
public final class DiffractionPathBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiffractionPathBuilder.class);
    
    private DiffractionPathBuilder() {}

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final double MAX_RATIO_HULL_DIRECT_PATH = 4;

    /**
     * Compute vertical edge diffraction profile between receiver and source.
     *
     * @param rcv receiver point information
     * @param src source point information  
     * @param scene scene data with geometry and materials
     * @param side computation side (LEFT or RIGHT)
     * @return computed diffraction profile or null if computation fails
     */
    public static CutProfile computeVEdgeDiffraction(ReceiverPointInfo rcv, SourcePointInfo src,
                                                    org.noise_planet.noisemodelling.pathfinder.path.Scene scene, PathFinder.ComputationSide side) {

        LOGGER.debug("Starting computeVEdgeDiffraction: src={}, rcv={}, side={}", src.getCoordinate(), rcv.getCoordinate(), side);
        long startTime = System.currentTimeMillis();
        
        List<Coordinate> coordinates = computeSideHull(side == PathFinder.ComputationSide.LEFT,
                new Coordinate(src.getCoordinate()), new Coordinate(rcv.getCoordinate()), scene.getProfileBuilder(), scene.getMaxSrcDist());

        long hullTime = System.currentTimeMillis();
        LOGGER.debug("computeSideHull completed in {}ms, coordinates count: {}", hullTime - startTime, coordinates.size());

        if (coordinates.size() <= 2) {
            LOGGER.debug("Insufficient coordinates ({}) for diffraction path, returning null", coordinates.size());
            return null;
        }

        List<CutPoint> cutPoints = buildDiffractionCutPoints(coordinates, src, rcv, scene);
        CutProfile result = assembleDiffractionProfile(cutPoints, src, rcv, scene);
        
        long totalTime = System.currentTimeMillis();
        LOGGER.debug("computeVEdgeDiffraction completed in {}ms total", totalTime - startTime);
        
        return result;
    }

    /**
     * Build cut points for diffraction path from coordinates.
     *
     * @param coordinates diffraction path coordinates
     * @param src source point information
     * @param rcv receiver point information
     * @param scene scene data
     * @return list of cut points representing the diffraction path
     */
    private static List<CutPoint> buildDiffractionCutPoints(List<Coordinate> coordinates, 
            SourcePointInfo src, ReceiverPointInfo rcv, org.noise_planet.noisemodelling.pathfinder.path.Scene scene) {
        
        List<CutPoint> cutPoints = new ArrayList<>();

        for (int i = 0; i < coordinates.size() - 1; i++) {
            CutProfile profile = scene.getProfile(coordinates.get(i), coordinates.get(i + 1), 
                    scene.getDefaultGroundAttenuation(), false, src);

            // Add source point (first segment) or diffraction point (subsequent segments)
            if (i > 0) {
                cutPoints.add(new org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointVEdgeDiffraction(profile.getSource()));
            } else {
                CutPointSource cutPointSource = profile.getSource();
                cutPointSource.setSourceId(src.getSourceIndex());
                cutPoints.add(cutPointSource);
            }

            // Add intermediate cut points (excluding source and receiver)
            cutPoints.addAll(profile.getCutPoints().subList(1, profile.getCutPoints().size() - 1));

            // Add receiver point (last segment)
            if (i + 1 == coordinates.size() - 1) {
                CutPointReceiver cutPointReceiver = profile.getReceiver();
                cutPointReceiver.setReceiverId(rcv.getReceiverIndex());
                cutPoints.add(cutPointReceiver);
            }
        }

        return cutPoints;
    }

    /**
     * Assemble the final diffraction profile from cut points.
     *
     * @param cutPoints list of cut points for the diffraction path
     * @param src source point information
     * @param rcv receiver point information
     * @param scene scene data
     * @return assembled CutProfile with complete diffraction path
     */
    private static CutProfile assembleDiffractionProfile(List<CutPoint> cutPoints, 
            SourcePointInfo src, ReceiverPointInfo rcv, org.noise_planet.noisemodelling.pathfinder.path.Scene scene) {
        
        CutProfile mainProfile = new CutProfile((CutPointSource) cutPoints.get(0),
                (CutPointReceiver) cutPoints.get(cutPoints.size() - 1));

        // Insert intermediate cut points
        mainProfile.insertCutPoint(false,
                cutPoints.subList(1, cutPoints.size() - 1).toArray(CutPoint[]::new));

        // Configure source point / receiver point
        mainProfile.setSource(mainProfile.getSource().migrateFromSourcePointInfo(src));
        mainProfile.setReceiver(mainProfile.getReceiver().migrateFromReceiverPointInfo(rcv));

        return mainProfile;
    }

    /**
     * Set properties for receiver cut point.
     *
     * @param cutPointReceiver receiver cut point to configure
     * @param rcv receiver point information
     */
    private static void setReceiverPointProperties(CutPointReceiver cutPointReceiver, ReceiverPointInfo rcv) {
        cutPointReceiver.setReceiverId(rcv.getReceiverIndex());
        cutPointReceiver.setReceiverPk(rcv.getReceiverPk());
    }

    /**
     * Compute side hull for diffraction path calculation.
     *
     * @param left true for left side, false for right side
     * @param p1 source coordinate
     * @param p2 receiver coordinate
     * @param profileBuilder profile builder for obstacle detection
     * @param maxSrcDist maximum source distance limit
     * @return list of coordinates forming the diffraction path
     */
    public static List<Coordinate> computeSideHull(boolean left, Coordinate p1, Coordinate p2, 
            ProfileBuilder profileBuilder, double maxSrcDist) {
        
        LOGGER.debug("computeSideHull: left={}, p1={}, p2={}, maxSrcDist={}", left, p1, p2, maxSrcDist);
        
        if (p1.equals(p2)) {
            LOGGER.debug("computeSideHull: source equals receiver, returning empty list");
            return new ArrayList<>();
        }

        List<Coordinate> input = initializeHullInput(p1, p2, profileBuilder, left);
        LOGGER.debug("computeSideHull: initialized input with {} coordinates", input.size());
        
        return computeIterativeConvexHull(input, p1, p2, profileBuilder, maxSrcDist, left);
    }

    /**
     * Initialize input points for hull computation by detecting initial obstacles.
     *
     * @param p1 source coordinate
     * @param p2 receiver coordinate
     * @param profileBuilder profile builder for obstacle detection
     * @param left true for left side computation
     * @return initial list of input coordinates
     */
    private static List<Coordinate> initializeHullInput(Coordinate p1, Coordinate p2, 
            ProfileBuilder profileBuilder, boolean left) {
        
        List<Coordinate> input = new ArrayList<>();
        input.add(p1);
        input.add(p2);

        Plane cutPlane = PathFinder.computeZeroRadPlane(p1, p2);
        org.noise_planet.noisemodelling.pathfinder.profilebuilder.BuildingIntersectionPathVisitor visitor = 
                new org.noise_planet.noisemodelling.pathfinder.profilebuilder.BuildingIntersectionPathVisitor(
                        p1, p2, left, profileBuilder, input, cutPlane);

        profileBuilder.getWallsOnPath(p1, p2, visitor);
        return input;
    }

    /**
     * Compute iterative convex hull until no more intersections are found.
     *
     * @param input initial input coordinates
     * @param p1 source coordinate
     * @param p2 receiver coordinate
     * @param profileBuilder profile builder for obstacle detection
     * @param maxSrcDist maximum source distance
     * @param left true for left side computation
     * @return final hull path coordinates
     */
    private static List<Coordinate> computeIterativeConvexHull(List<Coordinate> input, 
            Coordinate p1, Coordinate p2, ProfileBuilder profileBuilder, double maxSrcDist, boolean left) {
        
        LOGGER.debug("Starting computeIterativeConvexHull: p1={}, p2={}, left={}, inputSize={}", p1, p2, left, input.size());
        long startTime = System.currentTimeMillis();
        
        Set<LineSegment> freeFieldSegments = new HashSet<>();
        Coordinate[] coordinates = new Coordinate[0];
        boolean convexHullIntersects = true;
        
        // Create visitor once and reuse it - this is critical to prevent infinite loops
        // The visitor maintains state about already processed buildings/walls
        Plane cutPlane = PathFinder.computeZeroRadPlane(p1, p2);
        org.noise_planet.noisemodelling.pathfinder.profilebuilder.BuildingIntersectionPathVisitor visitor = 
                new org.noise_planet.noisemodelling.pathfinder.profilebuilder.BuildingIntersectionPathVisitor(
                        p1, p2, left, profileBuilder, input, cutPlane);
        
        // Add iteration limit to prevent infinite loops, but allow more iterations for complex cases
        int maxIterations = 1000; 
        int iteration = 0;

        while (convexHullIntersects && iteration < maxIterations) {
            iteration++;
            LOGGER.debug("Iteration {}: Starting convex hull computation", iteration);
            long iterationStart = System.currentTimeMillis();
            
            ConvexHull convexHull = new ConvexHull(input.toArray(new Coordinate[0]), GEOMETRY_FACTORY);
            Geometry convexhull = convexHull.getConvexHull();
            coordinates = convexhull.getCoordinates();

            LOGGER.debug("Iteration {}: ConvexHull created with {} coordinates in {}ms", 
                iteration, coordinates.length, System.currentTimeMillis() - iterationStart);

            // Check if hull is too long
            if (isHullTooLong(coordinates, p1, p2, maxSrcDist)) {
                LOGGER.debug("Iteration {}: Hull too long, terminating", iteration);
                return new ArrayList<>();
            }

            // Reorder coordinates starting from source point
            coordinates = reorderCoordinatesFromSource(coordinates, p1, p2);
            if (coordinates == null) {
                LOGGER.debug("Iteration {}: Failed to reorder coordinates, terminating", iteration);
                return new ArrayList<>();
            }

            // Check for new intersections - reuse the same visitor to maintain state
            long intersectionStart = System.currentTimeMillis();
            HullIntersectionResult result = checkHullIntersections(coordinates, p1, p2, 
                    profileBuilder, freeFieldSegments, input, left, visitor);
            
            long intersectionTime = System.currentTimeMillis() - intersectionStart;
            LOGGER.debug("Iteration {}: Intersection check completed in {}ms, hasIntersections={}", 
                iteration, intersectionTime, result.hasIntersections);
            
            convexHullIntersects = result.hasIntersections;
            if (result.shouldTerminate) {
                LOGGER.debug("Iteration {}: Termination requested", iteration);
                return new ArrayList<>();
            }
            
            long iterationTotal = System.currentTimeMillis() - iterationStart;
            LOGGER.debug("Iteration {}: Completed in {}ms", iteration, iterationTotal);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        // If maximum iterations exceeded, return current best result instead of empty
        if (iteration >= maxIterations) {
            LOGGER.warn("computeIterativeConvexHull exceeded maximum iterations ({}) for source {} to receiver {} in {}ms. " +
                "Returning current best path with {} coordinates.", 
                maxIterations, p1, p2, totalTime, coordinates.length);
            // Return the current hull path instead of empty list
            return extractFinalPath(coordinates, p1, p2, left);
        }

        LOGGER.debug("computeIterativeConvexHull completed successfully in {} iterations, {}ms total", iteration, totalTime);
        return extractFinalPath(coordinates, p1, p2, left);
    }

    /**
     * Check if the computed hull is too long compared to direct path.
     *
     * @param coordinates hull coordinates
     * @param p1 source coordinate
     * @param p2 receiver coordinate
     * @param maxSrcDist maximum allowed distance
     * @return true if hull is too long
     */
    private static boolean isHullTooLong(Coordinate[] coordinates, Coordinate p1, Coordinate p2, double maxSrcDist) {
        double convexHullLength = org.locationtech.jts.algorithm.Length.ofLine(
                CoordinateArraySequenceFactory.instance()
                        .create(Arrays.copyOfRange(coordinates, 0, coordinates.length - 1)));
        
        return convexHullLength / p1.distance(p2) > MAX_RATIO_HULL_DIRECT_PATH || 
               convexHullLength >= maxSrcDist;
    }

    /**
     * Reorder coordinates array to start from source point.
     *
     * @param coordinates original coordinates
     * @param p1 source coordinate
     * @param p2 receiver coordinate
     * @return reordered coordinates starting from source, or null if source not found
     */
    private static Coordinate[] reorderCoordinatesFromSource(Coordinate[] coordinates, Coordinate p1, Coordinate p2) {
        // Find source point index
        int indexp1 = findCoordinateIndex(coordinates, p1);
        if (indexp1 == -1) {
            return null;
        }

        // Shift array to start from source
        Coordinate[] coordinatesShifted = new Coordinate[coordinates.length];
        int len = (coordinates.length - 1) - indexp1;
        System.arraycopy(coordinates, indexp1, coordinatesShifted, 0, len);
        System.arraycopy(coordinates, 0, coordinatesShifted, len, coordinates.length - len - 1);
        coordinatesShifted[coordinatesShifted.length - 1] = coordinatesShifted[0];

        return coordinatesShifted;
    }

    /**
     * Find index of coordinate in array.
     *
     * @param coordinates coordinate array
     * @param target target coordinate to find
     * @return index of coordinate or -1 if not found
     */
    private static int findCoordinateIndex(Coordinate[] coordinates, Coordinate target) {
        for (int i = 0; i < coordinates.length - 1; i++) {
            if (coordinates[i].equals(target)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check for intersections along hull segments.
     */
    private static class HullIntersectionResult {
        boolean hasIntersections = false;
        boolean shouldTerminate = false;
    }

    /**
     * Check hull segments for intersections with obstacles.
     *
     * @param coordinates hull coordinates
     * @param p1 source coordinate
     * @param p2 receiver coordinate
     * @param profileBuilder profile builder
     * @param freeFieldSegments cache of known free field segments
     * @param input input coordinate list (modified)
     * @param left true for left side computation
     * @return intersection check result
     */
    private static HullIntersectionResult checkHullIntersections(Coordinate[] coordinates, 
            Coordinate p1, Coordinate p2, ProfileBuilder profileBuilder, 
            Set<LineSegment> freeFieldSegments, List<Coordinate> input, boolean left,
            org.noise_planet.noisemodelling.pathfinder.profilebuilder.BuildingIntersectionPathVisitor visitor) {
        
        HullIntersectionResult result = new HullIntersectionResult();
        
        int indexp2 = findCoordinateIndex(coordinates, p2);
        if (indexp2 == -1) {
            result.shouldTerminate = true;
            return result;
        }

        input.clear();
        input.addAll(Arrays.asList(coordinates));

        for (int k = 0; k < coordinates.length - 1; k++) {
            LineSegment freeFieldTestSegment = new LineSegment(coordinates[k], coordinates[k + 1]);

            if (shouldTestSegment(k, indexp2, left)) {
                if (!freeFieldSegments.contains(freeFieldTestSegment)) {
                    int inputPointsBefore = input.size();
                    
                    // Set the intersection line for this segment on the reused visitor
                    visitor.setIntersectionLine(freeFieldTestSegment);
                    profileBuilder.getWallsOnPath(coordinates[k], coordinates[k + 1], visitor);

                    if (inputPointsBefore == input.size()) {
                        freeFieldSegments.add(freeFieldTestSegment);
                    } else {
                        result.hasIntersections = true;
                        break;
                    }
                }
            }
        }

        return result;
    }

    /**
     * Determine if a segment should be tested for intersections.
     *
     * @param segmentIndex current segment index
     * @param receiverIndex receiver point index
     * @param left true for left side computation
     * @return true if segment should be tested
     */
    private static boolean shouldTestSegment(int segmentIndex, int receiverIndex, boolean left) {
        return (left && segmentIndex < receiverIndex) || (!left && segmentIndex >= receiverIndex);
    }

    /**
     * Extract final path from computed hull coordinates.
     *
     * @param coordinates hull coordinates
     * @param p1 source coordinate
     * @param p2 receiver coordinate
     * @param left true for left side computation
     * @return final path coordinates
     */
    private static List<Coordinate> extractFinalPath(Coordinate[] coordinates, Coordinate p1, Coordinate p2, boolean left) {
        // Validate coordinates (no negative heights)
        for (Coordinate p : coordinates) {
            if (p.z < 0) {
                return new ArrayList<>();
            }
        }

        int indexp1 = findCoordinateIndex(coordinates, p1);
        int indexp2 = findCoordinateIndex(coordinates, p2);

        if (indexp1 == -1 || indexp2 == -1) {
            return new ArrayList<>();
        }

        List<Coordinate> sideHullPath;
        if (left) {
            sideHullPath = Arrays.asList(Arrays.copyOfRange(coordinates, indexp1, indexp2 + 1));
        } else {
            List<Coordinate> inversePath = Arrays.asList(Arrays.copyOfRange(coordinates, indexp2, coordinates.length));
            Collections.reverse(inversePath);
            sideHullPath = inversePath;
        }
        
        return sideHullPath;
    }
}
