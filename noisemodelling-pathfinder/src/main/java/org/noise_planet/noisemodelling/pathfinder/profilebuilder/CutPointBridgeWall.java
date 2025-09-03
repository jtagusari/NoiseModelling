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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import java.util.Collections;
import java.util.List;

public class CutPointBridgeWall  extends CutPointWall {
    
    public enum WallDirection {
        UPWARD,
        DOWNWARD
    }

    private WallDirection wallDirection = WallDirection.UPWARD;
    private boolean mirrorRelax;

    public void setWallDirection(WallDirection wallDirection){
        this.wallDirection = wallDirection;
    }

    public boolean getMirrorRelax() {
        return mirrorRelax;
    }

    public void setMirrorRelax(boolean mirrorRelax) {
        this.mirrorRelax = mirrorRelax;
    }

    /** This point encounter this kind of limit
     * - We can enter or exit a polygon
     * - pass a line (a wall without width) */
    public enum INTERSECTION_TYPE {BUILDING_ENTER, BUILDING_EXIT, THIN_WALL_ENTER_EXIT}

    public INTERSECTION_TYPE intersectionType = INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT;

    /** Database primary key value of the obstacle */
    private Long wallPk = null;

    /**
     * Empty constructor for deserialization
     */
    public CutPointBridgeWall(int processedWallIndex, Coordinate intersection, LineSegment wallSegment, List<Double> wallAlpha, WallDirection wallDirection) {
        super(processedWallIndex, intersection, wallSegment, wallAlpha);
        this.wallDirection = wallDirection;
    }
    
    public CutPointBridgeWall(int processedWallIndex, Coordinate intersection, LineSegment wallSegment, List<Double> wallAlpha) {
        super(processedWallIndex, intersection, wallSegment, wallAlpha);
        this.wallDirection = WallDirection.UPWARD;
    }

    /**
     *
     * @param pk External primary key value, will be updated if {@literal >=} 0
     * @return this
     */
    public CutPointBridgeWall setPk(long pk) {
        if(pk >= 0) {
            this.wallPk = pk;
        }
        return this;
    }

    /**
     * @return Convert alpha values to a java array
     */
    public double[] alphaAsArray() {
        return wallAlpha.stream().mapToDouble(aDouble -> aDouble).toArray();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("wallPk")
    public Long getWallPk() {
        return wallPk;
    }

    @JsonProperty("wallAlpha")
    public List<Double> getWallAlpha() {
        return wallAlpha;
    }

    public void setWallAlpha(List<Double> wallAlpha) {
        this.wallAlpha = wallAlpha;
    }

    @Override
    public String toString() {
        return "CutPointWall{" +
                "groundCoefficient=" + groundCoefficient +
                ", zGround=" + zGround +
                ", coordinate=" + coordinate +
                ", processedWallIndex=" + processedWallIndex +
                ", wallAlpha=" + wallAlpha +
                ", wall=" + wall +
                '}';
    }
}
