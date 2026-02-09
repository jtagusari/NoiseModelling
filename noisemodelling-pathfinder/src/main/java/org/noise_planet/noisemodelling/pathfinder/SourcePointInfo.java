package org.noise_planet.noisemodelling.pathfinder;

import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.noise_planet.noisemodelling.pathfinder.path.BridgeRelationship;
import org.locationtech.jts.geom.Coordinate;
import java.util.Objects;
import static java.lang.Double.isNaN;
/**
 * Attribute of the source point.
 * 
 * <p>The position coordinate contains absolute elevation in the Z component:
 * <ul>
 * <li>For HEIGHT_TYPE=RELATIVE sources: Z coordinate is converted to absolute elevation
 *     during sampling (Step 7) using calculateAbsoluteElevation()</li>
 * <li>For HEIGHT_TYPE=ABSOLUTE sources: Z coordinate is already absolute elevation from database</li>
 * </ul>
 * All SourcePointInfo instances contain absolute elevations ready for propagation calculation.
 */

public class SourcePointInfo implements Comparable<SourcePointInfo> {
    // Light-weight holder for source attributes used during search.
    // Note: equals/hashCode rely on sourceIndex and position. If position is
    // mutated after insertion into collections, behaviour may be surprising.
    // Consider making fields final and providing a builder or factory.
    private final double li;
    private final int sourceIndex;
    private final long sourcePk;
    /** Source position with absolute elevation in Z coordinate (elevation in DEM coordinate system) */
    private final Coordinate position;
    private final Orientation orientation;
    private final BridgeRelationship bridgeRelationship;


    /**
     * Create a SourcePointInfo from explicit values.
     * 
     * @param sourceIndex Source index in Scene
     * @param sourcePrimaryKey Source primary key for emission data lookup
     * @param position Source position with absolute elevation in Z coordinate (not relative height)
     * @param li Line segment length
     * @param orientation Source orientation
     * @param bridgeRelationship Bridge properties
     */
    public SourcePointInfo(int sourceIndex, long sourcePrimaryKey, Coordinate position, double li,
                            Orientation orientation, BridgeRelationship bridgeRelationship) {
        this.sourceIndex = sourceIndex;
        this.sourcePk = sourcePrimaryKey;
        if (isNaN(position.z)) {
            this.position = new Coordinate(position.x, position.y, 0);
        } else {
            this.position = position;
        }
        this.li = li;
        this.orientation = orientation;
        this.bridgeRelationship = bridgeRelationship != null ? 
                                   bridgeRelationship : new BridgeRelationship();
    }

    public SourcePointInfo(int sourceIndex, long sourcePrimaryKey, Coordinate position, double li,
                            Orientation orientation) {
        this.sourceIndex = sourceIndex;
        this.sourcePk = sourcePrimaryKey;
        if (isNaN(position.z)) {
            this.position = new Coordinate(position.x, position.y, 0);
        } else {
            this.position = position;
        }
        this.li = li;
        this.orientation = orientation;
        this.bridgeRelationship = new BridgeRelationship();
    }

    public SourcePointInfo(CutPointSource source) {
        this.sourceIndex = source.getSourceId();
        this.sourcePk = source.getSourcePk();
        this.position = source.getCoordinate();
        this.li = source.getLineLength();
        this.orientation = source.getOrientation();
        this.bridgeRelationship = source.getBridgeRelationship() != null ? 
                                   source.getBridgeRelationship() : new BridgeRelationship();
    }

    public SourcePointInfo(){
        this.sourceIndex = -1;
        this.sourcePk = -1;
        this.position = new Coordinate(0, 0, 0);
        this.li = 0;
        this.orientation = new Orientation();
        this.bridgeRelationship = new BridgeRelationship();
    }

    public SourcePointInfo(Coordinate coordinate){
        this.sourceIndex = -1;
        this.sourcePk = -1;
        this.position = new Coordinate(coordinate);
        this.li = 0;
        this.orientation = new Orientation();
        this.bridgeRelationship = new BridgeRelationship();
    }
    public Orientation getOrientation() {
        return orientation;
    }

    public BridgeRelationship getBridgeRelationship(){
        return bridgeRelationship;
    }

    /**
     * Get source position coordinate.
     * 
     * @return Coordinate with absolute elevation in Z component (elevation in DEM coordinate system)
     */
    public Coordinate getCoordinate() {
        return position;
    }

    public int getSourceIndex() {
        return sourceIndex;
    }

    public double getLineLength() {
        return li;
    }

    public long getSourcePk() {
        return sourcePk;
    }

    /**
     * Compare by source index; used to keep stable ordering.
     */
    @Override
    public int compareTo(SourcePointInfo sourcePointInfo) {
        return Integer.compare(sourceIndex, sourcePointInfo.sourceIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        SourcePointInfo that = (SourcePointInfo) o;
        return sourceIndex == that.sourceIndex && 
               Objects.equals(position, that.position) && 
               Objects.equals(bridgeRelationship, that.bridgeRelationship);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceIndex, position, bridgeRelationship);
    }
}
