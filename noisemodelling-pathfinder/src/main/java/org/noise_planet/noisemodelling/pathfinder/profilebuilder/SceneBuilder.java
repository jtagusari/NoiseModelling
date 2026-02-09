/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.path.BridgeRelationship;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

public class SceneBuilder {
    private static final GeometryFactory FACTORY = GeometryFactoryProvider.SHARED;

    private final Scene data;

    public SceneBuilder(ProfileBuilder profileBuilder) {
        data = new Scene(profileBuilder);
    }

    /**
     *
     * @param x
     * @param y
     * @param z
     * @return
     */
    public SceneBuilder addSource(double x, double y, double z) {
        data.addSource(0L, FACTORY.createPoint(new Coordinate(x, y, z)));
        return this;
    }

    /**
     * Add a source with geometry and default relative height type.
     *
     * @param geom Source geometry (Point or LineString)
     * @return Builder instance for method chaining
     */
    public SceneBuilder addSource(Geometry geom) {
        data.addSource(0L, geom);
        return this;
    }

    /**
     * Add a source with primary key, geometry, height type, orientation, and bridge properties.
     * 
     * @param pk Source primary key for database correlation
     * @param geom Source geometry (Point or LineString)
     * @param heightType Height interpretation (RELATIVE or ABSOLUTE)
     * @param orientation Directional orientation (yaw, pitch, roll)
     * @param bridgeRelationship Bridge properties
     * @return Builder instance for method chaining
     */
    public SceneBuilder addSource(long pk, Geometry geom, Scene.HeightType heightType, 
                                   Orientation orientation, BridgeRelationship bridgeRelationship) {
        data.addSource(pk, geom, heightType, orientation, bridgeRelationship);
        return this;
    }

    /**
     * Add a receiver with coordinates using auto-generated primary key and default absolute height type.
     * This is a convenience method for test scenarios. Production code should use 
     * addReceiver(long pk, double x, double y, double z, Scene.HeightType heightType).
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Builder instance for method chaining
     */
    public SceneBuilder addReceiver(double x, double y, double z) {
        data.addReceiver(0L, new Coordinate(x, y, z), Scene.HeightType.ABSOLUTE);
        return this;
    }

    /**
     * Add a receiver with primary key and specified height type.
     * 
     * @param pk Receiver primary key for database correlation
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param heightType Height interpretation (RELATIVE or ABSOLUTE)
     * @return Builder instance for method chaining
     */
    public SceneBuilder addReceiver(long pk, double x, double y, double z, Scene.HeightType heightType) {
        data.addReceiver(pk, new Coordinate(x, y, z), heightType);
        return this;
    }

    /**
     *
     * @param hDiff
     * @return
     */
    public SceneBuilder vEdgeDiff(boolean hDiff) {
        data.setComputeHorizontalDiffraction(hDiff);
        return this;
    }

    /**
     *
     * @param vDiff
     * @return
     */
    public SceneBuilder hEdgeDiff(boolean vDiff) {
        data.setComputeVerticalDiffraction(vDiff);
        return this;
    }

    /**
     *
     * @param gs
     * @return
     */
    public SceneBuilder setGs(double gs) {
        data.setDefaultGroundAttenuation(gs);
        return this;
    }

    /**
     * Set maximum reflection distance.
     * 
     * @param maxRefDist Maximum reflection distance in meters
     * @return Builder instance for method chaining
     */
    public SceneBuilder maxRefDist(double maxRefDist) {
        data.maxRefDist = maxRefDist;
        return this;
    }

    /**
     * Set maximum source distance.
     * 
     * @param maxSrcDist Maximum source distance in meters
     * @return Builder instance for method chaining
     */
    public SceneBuilder maxSrcDist(double maxSrcDist) {
        data.maxSrcDist = maxSrcDist;
        return this;
    }

    /**
     * Set maximum reflection order.
     * 
     * @param reflexionOrder Maximum number of reflections (typically 0-3)
     * @return Builder instance for method chaining
     */
    public SceneBuilder reflexionOrder(int reflexionOrder) {
        data.setReflexionOrder(reflexionOrder);
        return this;
    }

    /**
     *
     * @return
     */
    public Scene build() {
        return data;
    }
}
