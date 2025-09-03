/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CutProfile represents a vertical profile cut between a sound source and receiver.
 * This class manages cut points that represent intersections with buildings, topography,
 * and other obstacles along the propagation path.
 * 
 * The profile is used to calculate acoustic path properties including ground absorption
 * coefficients and to determine if the path is in free field conditions.
 * 
 * @author NoiseModelling contributors
 */
public class CutProfile {
    /** List of cut points.
     * First point is source, last point is receiver */
    private ArrayList<CutPoint> cutPoints = new ArrayList<>();

    /** True if Source-Receiver linestring is below building intersection */
    private boolean hasBuildingIntersection = false;
    /** True if Source-Receiver linestring intersects with bridge diffraction conditions */
    private boolean hasBridgeIntersection = false;
    /** True if Source-Receiver linestring is below topography cutting point. */
    private boolean hasTopographyIntersection = false;

    /**
     * Empty constructor for deserialization
     */
    public CutProfile() {
    }

    /**
     * Creates a new CutProfile with source and receiver points.
     * 
     * @param source the source point of the acoustic path
     * @param receiver the receiver point of the acoustic path
     */
    public CutProfile(CutPointSource source, CutPointReceiver receiver) {
        cutPoints.add(source);
        cutPoints.add(receiver);
    }

    /**
     * Insert and sort cut points into the profile.
     * The method ensures that the source remains the first point and the receiver remains the last point.
     * 
     * @param sortBySourcePosition if true, sort points by distance from the source after insertion
     * @param cutPointsToInsert array of cut points to insert into the profile
     */
    public void insertCutPoint(boolean sortBySourcePosition, CutPoint... cutPointsToInsert) {
        if (cutPoints.isEmpty() || !(cutPoints.get(0) instanceof CutPointSource) || !(cutPoints.get(cutPoints.size() - 1) instanceof CutPointReceiver)) {
            throw new IllegalStateException("No source or receiver point exists in the profile");
        }
        CutPointSource sourcePoint = (CutPointSource) cutPoints.get(0);
        CutPointReceiver receiverPoint = (CutPointReceiver) cutPoints.get(cutPoints.size() - 1);
        cutPoints.addAll(1, Arrays.asList(cutPointsToInsert));
        if(sortBySourcePosition) {
            sort(sourcePoint.coordinate);
            // move source as the first point
            int sourceIndex = cutPoints.indexOf(sourcePoint);
            if (sourceIndex != 0) {
                cutPoints.remove(sourceIndex);
                cutPoints.add(0, sourcePoint);
            }
            // move receiver as the last point
            int receiverIndex = cutPoints.indexOf(receiverPoint);
            if (receiverIndex != cutPoints.size() - 1) {
                cutPoints.remove(receiverIndex);
                cutPoints.add(cutPoints.size(), receiverPoint);
            }
        }
    }

    /**
     * Sort the CutPoints by distance from a reference coordinate.
     * 
     * @param c0 the reference coordinate to calculate distances from
     */
    public void sort(Coordinate c0) {
        cutPoints.sort(new CutPointDistanceComparator(c0));
    }

    /**
     * Compute the weighted ground absorption coefficient for the path between two points.
     * The method considers whether the path segment is above obstacles (roofs) or on ground.
     * 
     * @param p0 starting point of the path segment
     * @param p1 ending point of the path segment  
     * @param roofG ground absorption coefficient for paths above obstacles (buildings/bridges)
     * @return the weighted absorption coefficient of this path segment
     */
    @JsonIgnore
    public double getGPath(CutPoint p0, CutPoint p1, double roofG) {
        double totalLength = 0;
        double rsLength = 0.0;

        // Extract part of the path from the specified argument
        int i0 = cutPoints.indexOf(p0);
        int i1 = cutPoints.indexOf(p1);
        if(i0 == -1 || i1 == -1 || i1 < i0) {
            return 0.0;
        }

        boolean aboveRoof = false;
        for(int index = 0; index < i1; index++) {
            CutPoint current = cutPoints.get(index);
            if(current instanceof CutPointWall || current instanceof CutPointBridgeWall) {
                aboveRoof = checkAboveRoof((CutPointWall) current);
            }
            if(index >= i0) {
                double segmentLength = current.getCoordinate().distance(cutPoints.get(index + 1).getCoordinate());
                rsLength += segmentLength * (aboveRoof ? roofG : current.getGroundCoefficient());
                totalLength += segmentLength;
            }
        }
        return rsLength / totalLength;
    }

    @JsonIgnore
    private boolean checkAboveRoof(CutPointWall wall){
        if(wall.getIntersectionType().equals(CutPointWall.INTERSECTION_TYPE.BUILDING_ENTER)) {
            return true;
        } else if(wall.getIntersectionType().equals(CutPointWall.INTERSECTION_TYPE.BUILDING_EXIT)) {
            return false;
        }
        return false;
    }

    @JsonIgnore
    private boolean checkAboveRoof(CutPointBridgeWall wall){
        /* TODO implement bridge enter/exit logic */
        return false;
    }

    /**
     * Compute the weighted ground absorption coefficient for the entire path from source to receiver.
     * Uses the default building absorption coefficient.
     * 
     * @return the weighted absorption coefficient for the complete path, or 0 if no points exist
     */
    @JsonIgnore
    public double getGPath() {
        if(!cutPoints.isEmpty()) {
            return getGPath(cutPoints.get(0), cutPoints.get(cutPoints.size() - 1), Scene.DEFAULT_G_BUILDING);
        } else {
            return 0;
        }
    }

    /**
     * Determines if the acoustic path is in free field conditions.
     * A path is considered free field if it has no intersections with buildings,
     * bridges, or significant topographic obstacles.
     * 
     * @return true if the path is in free field conditions, false otherwise
     */
    @JsonIgnore
    public boolean isFreeField() {
        return !hasBuildingIntersection && !hasBridgeIntersection && !hasTopographyIntersection;
    }


    @Override
    public String toString() {
        return "CutProfile{" +
                "pts=" + cutPoints +
                ", hasBuildingIntersection=" + hasBuildingIntersection +
                ", hasBridgeIntersection=" + hasBridgeIntersection +
                ", hasTopographyIntersection=" + hasTopographyIntersection +
                '}';
    }

    /**
     * From the vertical plane cut, extract only the top elevation points
     * (buildings/walls top or ground if no buildings) then re-project it into
     * a 2d coordinate system. The first point is always x=0.
     * @return the computed 2D coordinate list of DEM
     */
    public List<Coordinate> computePts2DGround() {
        return computePts2DGround(0, null);
    }


    /**
     * Convert all cut points to a 2D coordinate system.
     * The coordinates are re-projected so that the first point is at x=0.
     * 
     * @return the computed 2D coordinate list of all cut points
     */
    public List<Coordinate> computePts2D() {
        List<Coordinate> pts2D = cutPoints.stream()
                .map(CutPoint::getCoordinate)
                .collect(Collectors.toList());
        pts2D = JTSUtility.getNewCoordinateSystem(pts2D);
        return pts2D;
    }

    /**
     * From the vertical plane cut, extract only the top elevation points
     * (buildings/walls top or ground if no buildings) then re-project it into
     * a 2D coordinate system. The first point is always x=0.
     * 
     * @param index if provided, will contain corresponding indices from parameter to return list items
     * @return the computed 2D coordinate list of DEM (Digital Elevation Model)
     */
    public List<Coordinate> computePts2DGround(List<Integer> index) {
        return computePts2DGround(0, index);
    }

    private class ElevationProfile {

    }

    /**
     * From the vertical plane cut, extract only the top elevation points
     * (buildings/walls top or ground if no buildings).
     * This static method processes a list of cut points to generate the ground profile.
     * 
     * @param pts list of cut points to process
     * @param index if provided, will contain corresponding indices from parameter to return list items
     * @return the computed coordinate list of the vertical cut representing the ground profile
     */
    public static List<Coordinate> computePtsGround(List<CutPoint> pts, List<Integer> index) {

        List<Coordinate> pts2D = new ArrayList<>(pts.size());
        if(pts.isEmpty()) {
            return pts2D;
        }
        // keep track of the obstacle under our current position.
        // Note: Both buildings and bridges are treated as obstacles in the same way
        boolean overArea = false;
        for (CutPoint cut : pts) {
            if (cut instanceof CutPointWall) {
                CutPointWall cutPointWall = (CutPointWall) cut;
                if (cutPointWall.getIntersectionType().equals(CutPointWall.INTERSECTION_TYPE.BUILDING_EXIT)) {
                    overArea = true;
                } else {
                    break;
                }
            }
        }
        for (CutPoint cut : pts) {
            if (cut instanceof CutPointGroundEffect) {
                if (index != null) {
                    index.add(pts2D.size() - 1);
                }
                continue;
            }
            if (cut instanceof CutPointWall) {
                // Z ground profile must add intermediate ground points before adding the top level of building/wall/bridge
                CutPointWall cutPointWall = (CutPointWall) cut;
                // Handle both Building and Bridge obstacles in the same way for ground profile calculation
                if (cutPointWall.getIntersectionType().equals(CutPointWall.INTERSECTION_TYPE.BUILDING_ENTER) ||
                        cutPointWall.getIntersectionType().equals(CutPointWall.INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT)) {
                    pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getCoordinate().z));
                    overArea = true;
                }
                pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getCoordinate().z));
                if (cutPointWall.getIntersectionType().equals(CutPointWall.INTERSECTION_TYPE.BUILDING_EXIT) ||
                        cutPointWall.getIntersectionType().equals(CutPointWall.INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT)) {
                    pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
                    overArea = false;
                }
            } else if (cut instanceof CutPointReflection) {
                // Z ground profile is duplicated for reflection point before and after
                pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
                pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
                pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
            } else {
                // we will ignore topographic point if we are over a building
                if (!(overArea && cut instanceof CutPointTopography)) {
                    pts2D.add(new Coordinate(cut.getCoordinate().x, cut.getCoordinate().y, cut.getzGround()));
                }
            }
            if (index != null) {
                index.add(pts2D.size() - 1);
            }
        }
        return pts2D;
    }

    /**
     * From the vertical plane cut, extract only the top elevation points
     * (buildings/walls top or ground if no buildings) then re-project it into
     * a 2D coordinate system with line simplification. The first point is always x=0.
     * 
     * @param pts list of cut points to process
     * @param tolerance simplify the point list by not adding points where the distance from the line segments
     *                 formed from the previous and the next point is inferior to this tolerance (remove intermediate collinear points)
     * @param index if provided, will contain corresponding indices from parameter to return list items
     * @return the computed 2D coordinate list of DEM with simplified geometry
     */
    public static List<Coordinate> computePts2DGround(List<CutPoint> pts, double tolerance, List<Integer> index) {
        return JTSUtility.getNewCoordinateSystem(computePtsGround(pts, index), tolerance);
    }

    /**
     * From the vertical plane cut, extract only the top elevation points
     * (buildings/walls top or ground if no buildings) then re-project it into
     * a 2D coordinate system with line simplification. The first point is always x=0.
     * 
     * @param tolerance simplify the point list by not adding points where the distance from the line segments
     *                 formed from the previous and the next point is inferior to this tolerance (remove intermediate collinear points)
     * @param index if provided, will contain corresponding indices from parameter to return list items
     * @return the computed 2D coordinate list of DEM with simplified geometry
     */
    public List<Coordinate> computePts2DGround(double tolerance, List<Integer> index) {
        return computePts2DGround(this.cutPoints, tolerance, index);
    }

    /**
     * Get a copy of all cut points in the profile.
     * 
     * @return a new ArrayList containing copies of all cut points
     */
    public ArrayList<CutPoint> getCutPoints() {
        return new ArrayList<>(cutPoints);
    }

    /**
     * Set the cut points for this profile.
     * 
     * @param cutPoints the cut points to set
     */
    public void setCutPoints(ArrayList<CutPoint> cutPoints) {
        this.cutPoints = new ArrayList<>(cutPoints);
    }

    /**
     * Get the source point of the acoustic path.
     * 
     * @return a copy of the source point, or null if no source exists
     */
    @JsonIgnore
    public CutPointSource getSource() {
        if (cutPoints.isEmpty() || !(cutPoints.get(0) instanceof CutPointSource)) {
            throw new IllegalStateException("No source point exists in the profile");
        }
        CutPointSource original = (CutPointSource) cutPoints.get(0);
        return new CutPointSource(original);
    }

    /**
     * Get the receiver point of the acoustic path.
     * 
     * @return a copy of the receiver point, or null if no receiver exists
     */
    @JsonIgnore
    public CutPointReceiver getReceiver() {
        if (cutPoints.isEmpty() || !(cutPoints.get(cutPoints.size() - 1) instanceof CutPointReceiver)) {
            throw new IllegalStateException("No receiver point exists in the profile");
        }
        CutPointReceiver original = (CutPointReceiver) cutPoints.get(cutPoints.size() - 1);
        return new CutPointReceiver(original);
    }

    /**
     * Set the source point of the acoustic path.
     * 
     * @param source the new source point to set
     * @throws IllegalArgumentException if source is null
     */
    @JsonIgnore
    public void setSource(CutPointSource source) {
        if (source == null) {
            throw new IllegalArgumentException("Source cannot be null");
        }
        if (!cutPoints.isEmpty() && cutPoints.get(0) instanceof CutPointSource) {
            cutPoints.set(0, new CutPointSource(source));
        } else {
            cutPoints.add(0, new CutPointSource(source));
        }
    }

    /**
     * Set the receiver point of the acoustic path.
     * 
     * @param receiver the new receiver point to set
     * @throws IllegalArgumentException if receiver is null
     */
    @JsonIgnore
    public void setReceiver(CutPointReceiver receiver) {
        if (receiver == null) {
            throw new IllegalArgumentException("Receiver cannot be null");
        }
        if (!cutPoints.isEmpty() && cutPoints.get(cutPoints.size() - 1) instanceof CutPointReceiver) {
            cutPoints.set(cutPoints.size() - 1, new CutPointReceiver(receiver));
        } else {
            cutPoints.add(new CutPointReceiver(receiver));
        }
    }

    /**
     * Set whether the path has topography intersection.
     * 
     * @param hasIntersection true if the path intersects with topographic obstacles
     */
    public void hasTopographyIntersection(boolean hasIntersection) {
        this.hasTopographyIntersection = hasIntersection;
    }

    /**
     * Check if the path has topography intersection.
     * 
     * @return true if the path intersects with topographic obstacles
     */
    @JsonProperty("hasTopographyIntersection")
    public boolean hasTopographyIntersection() {
        return hasTopographyIntersection;
    }

    /**
     * Set whether the path has building intersection.
     * 
     * @param hasIntersection true if the path intersects with buildings
     */
    public void hasBuildingIntersection(boolean hasIntersection) {
        this.hasBuildingIntersection = hasIntersection;
    }

    /**
     * Check if the path has building intersection.
     * 
     * @return true if the path intersects with buildings
     */
    @JsonProperty("hasBuildingIntersection")
    public boolean hasBuildingIntersection() {
        return hasBuildingIntersection;
    }

    /**
     * Set whether the path has bridge intersection.
     * 
     * @param hasIntersection true if the path intersects with bridges
     */
    public void hasBridgeIntersection(boolean hasIntersection) {
        this.hasBridgeIntersection = hasIntersection;
    }

    /**
     * Check if the path has bridge intersection.
     * 
     * @return true if the path intersects with bridges
     */
    @JsonProperty("hasBridgeIntersection")
    public boolean hasBridgeIntersection() {
        return hasBridgeIntersection;
    }

}
