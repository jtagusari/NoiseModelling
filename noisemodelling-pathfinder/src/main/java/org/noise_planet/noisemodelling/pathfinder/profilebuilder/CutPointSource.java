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
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.SourcePointInfo;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;

/**
 * Represents a sound source point in a vertical cut profile.
 * This class extends CutPoint to include source-specific properties such as
 * source identification, line subdivision length for line sources, and orientation
 * information for directivity calculations.
 * 
 * @author NoiseModelling contributors
 */
public class CutPointSource  extends CutPoint {

    /**
     * External identifier of the source (from table primary key)
     */
    private long sourcePk = -1;
    /**
     * Index in the subdomain
     */
    @JsonIgnore
    private int id = -1;

    @JsonIgnore
    private SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty();

    /**
     * Default constructor for deserialization.
     */
    public CutPointSource() {
    }

    /**
     * Constructor with location coordinate.
     * 
     * @param location the 3D coordinate of the source
     */
    public CutPointSource(Coordinate location) {
        super(location);
    }

    /**
     * Constructor with coordinate and line length.
     * 
     * @param coordinate the 3D coordinate of the source
     * @param li the line subdivision length for line sources
     */
    public CutPointSource(Coordinate coordinate, double li) {
        super(coordinate);
        this.li = li;
    }

    public CutPointSource(CutPointSource cutPointSource) {
        super(cutPointSource);
        this.sourcePk = cutPointSource.sourcePk;
        this.li = cutPointSource.li;
        this.orientation = cutPointSource.orientation;
        this.id = cutPointSource.id;
        this.sourceBridgeProperty = cutPointSource.sourceBridgeProperty;
    }

    /**
     * Generate default point source without information on DEM (source at 0.05 above ground level).
     * 
     * @param sourcePointInfo source information containing coordinates and metadata
     */
    public CutPointSource(SourcePointInfo sourcePointInfo) {
        super(sourcePointInfo.getCoordinate(), sourcePointInfo.getCoordinate().z - 0.05, 0);
        this.sourcePk = sourcePointInfo.getSourcePk();
        this.li = sourcePointInfo.getLineLength();
        this.orientation = sourcePointInfo.getOrientation();
        this.id = sourcePointInfo.getSourceIndex();
    }

    /**
     * Copy constructor.
     * 
     * @param src the source cut point to copy
     */
    public CutPointSource(CutPoint src) {
        super(src);
    }

    public CutPointSource migrateFromSourcePointInfo(SourcePointInfo sourcePointInfo) {
        this.id = sourcePointInfo.getSourceIndex();
        this.li = sourcePointInfo.getLineLength();
        this.orientation = sourcePointInfo.getOrientation();
        this.sourceBridgeProperty = sourcePointInfo.getSourceBridgeProperty();
        this.sourcePk = sourcePointInfo.getSourcePk();
        return this;
    }

    /** Source line subdivision length (1.0 means a point is representing 1 meter of line sound source) */
    private double li = 1.0;

    /**
     * Orientation of the point (should be about the source or receiver point).
     * The orientation is related to the directivity associated to the object.
     */
    private Orientation orientation = new Orientation();

    /**
     * Get the source identifier in the subdomain.
     * 
     * @return the source ID
     */
    @JsonIgnore
    public int getSourceId() {
        return this.id;
    }

    /**
     * Set the source identifier in the subdomain.
     * 
     * @param index the source ID to set
     */
    public void setSourceId(int index) {
        this.id = index;
    }

    /**
     * Get the external source primary key.
     * 
     * @return the source primary key
     */
    public long getSourcePk() {
        return this.sourcePk;
    }

    /**
     * Set the external source primary key.
     * 
     * @param sourcePk the source primary key to set
     */
    public void setSourcePk(long sourcePk) {
        this.sourcePk = sourcePk;
    }


    public SourceBridgeProperty getSourceBridgeProperty() {
        return this.sourceBridgeProperty;
    }

    public void setSourceBridgeProperty(SourceBridgeProperty sourceBridgeProperty) {
        this.sourceBridgeProperty = sourceBridgeProperty;
    }

    /**
     * Get the line subdivision length for line sources.
     * 
     * @return the line length value
     */
    @JsonProperty("li")
    public double getLineLength() {
        return this.li;
    }

    /**
     * Set the line subdivision length for line sources.
     * 
     * @param li the line length value to set
     */
    public void setLineLength(double li) {
        this.li = li;
    }

    
    /**
     * Get the orientation for the sources.
     * 
     * @return the orientation value
     */
    public Orientation getOrientation() {
        return this.orientation;
    }

    /**
     * Set the orientation for the sources.
     * 
     * @param orientation the orientation value to set
     */
    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
    }

    @Override
    public String toString() {
        return "CutPointSource{" +
                "\nsourcePk=" + sourcePk +
                "\n, li=" + li +
                "\n, orientation=" + orientation +
                "\n, id=" + id +
                "\n, coordinate=" + coordinate +
                "\n, zGround=" + zGround +
                "\n, groundCoefficient=" + groundCoefficient +
                "\n}\n";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        CutPointSource that = (CutPointSource) o;
        return sourcePk == that.sourcePk && id == that.id;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(sourcePk);
        result = 31 * result + id;
        return result;
    }
}
