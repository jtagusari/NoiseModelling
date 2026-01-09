package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles geometric calculations for acoustic path computation.
 * Responsible for convex hull calculations, coordinate transformations,
 * and 2D geometry operations.
 */
public class AcousticPathGeometryCalculator {

    /**
     * Compute convex hull diffraction points for acoustic path calculation.
     * This method identifies potential diffraction points by analyzing the convex hull
     * of profile points and determining which points could affect sound propagation.
     * 
     * @param cutProfile The vertical cut profile
     * @param pts2D 2D coordinates of cut points
     * @param cutProfilePoints Cut points from the profile
     * @return List of coordinates representing diffraction points
     */
    public static List<Coordinate> computeConvexHullDiffractionPoints(CutProfile cutProfile, 
                                                                     List<Coordinate> pts2D,
                                                                     List<CutPoint> cutProfilePoints) {
        List<Coordinate> pts = new ArrayList<>();
        
        // Create array for convex hull computation, excluding source and receiver
        Coordinate[] coordinates = new Coordinate[pts2D.size() - 2];
        for (int i = 1; i < pts2D.size() - 1; i++) {
            coordinates[i - 1] = pts2D.get(i);
        }

        if (coordinates.length > 0) {
            // Compute convex hull to find potential diffraction points
            ConvexHull convexHull = new ConvexHull(coordinates, new GeometryFactory());
            Geometry hull = convexHull.getConvexHull();
            
            if (hull != null) {
                // Extract coordinates from the convex hull
                Coordinate[] hullCoordinates = hull.getCoordinates();
                
                // Filter points that are actually part of the original profile
                for (Coordinate hullCoord : hullCoordinates) {
                    for (int i = 1; i < pts2D.size() - 1; i++) {
                        if (pts2D.get(i).equals2D(hullCoord)) {
                            CutPoint cutPoint = cutProfilePoints.get(i);
                            // Add points that can cause diffraction (exclude source and receiver)
                            if (!(cutPoint instanceof CutPointReceiver) && 
                                !(cutPoint instanceof CutPointSource)) {
                                pts.add(new Coordinate(hullCoord.x, hullCoord.y, cutPoint.getzGround()));
                            }
                            break;
                        }
                    }
                }
            }
        }

        return pts;
    }

    /**
     * Compute the mean plane coefficients for the given ground points.
     * 
     * @param pts2DGround Array of ground coordinates
     * @return Mean plane coefficients [a, b, c, d] for the plane equation ax + by + cz + d = 0
     */
    public static double[] computeMeanPlaneCoefficients(Coordinate[] pts2DGround) {
        return JTSUtility.getMeanPlaneCoefficients(pts2DGround);
    }

    /**
     * Check if two coordinates are equal in 2D (ignoring Z coordinate).
     * 
     * @param coord1 First coordinate
     * @param coord2 Second coordinate
     * @return true if coordinates are equal in 2D space
     */
    public static boolean equals2D(Coordinate coord1, Coordinate coord2) {
        return coord1.equals2D(coord2);
    }

    /**
     * Calculate the distance between two coordinates in 3D space.
     * 
     * @param coord1 First coordinate
     * @param coord2 Second coordinate
     * @return 3D distance between the coordinates
     */
    public static double distance3D(Coordinate coord1, Coordinate coord2) {
        double dx = coord1.x - coord2.x;
        double dy = coord1.y - coord2.y;
        double dz = coord1.z - coord2.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
