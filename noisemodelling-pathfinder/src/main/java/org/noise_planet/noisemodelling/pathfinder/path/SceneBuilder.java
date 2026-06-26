/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.path;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

public class SceneBuilder {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private final Scene scene;

    public SceneBuilder(ProfileBuilder profileBuilder) {
        scene = new Scene(profileBuilder);
    }

    /**
     *
     * @param x
     * @param y
     * @param z
     * @return
     */
    public SceneBuilder addSource(double x, double y, double z) {
        scene.addSource(0L, FACTORY.createPoint(new Coordinate(x, y, z)));
        return this;
    }

    /**
     * Add a source with geometry and default relative height type.
     *
     * @param geom Source geometry (Point or LineString)
     * @return Builder instance for method chaining
     */
    public SceneBuilder addSource(Geometry geom) {
        scene.addSource(0L, geom);
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
        scene.addSource(pk, geom, heightType, orientation, bridgeRelationship);
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
        if (scene.countReceivers() > 0){
            throw new IllegalStateException("addReceiver(double x, double y, double z) can only be used when no receivers have been added yet.");
        }
        scene.addReceiver(0L, new Coordinate(x, y, z), Scene.HeightType.ABSOLUTE);
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
        scene.addReceiver(pk, new Coordinate(x, y, z), heightType);
        return this;
    }

    /**
     *
     * @param hDiff
     * @return
     */
    public SceneBuilder vEdgeDiff(boolean hDiff) {
        scene.setComputeHorizontalDiffraction(hDiff);
        return this;
    }

    /**
     *
     * @param vDiff
     * @return
     */
    public SceneBuilder hEdgeDiff(boolean vDiff) {
        scene.setComputeVerticalDiffraction(vDiff);
        return this;
    }

    /**
     *
     * @param gs
     * @return
     */
    public SceneBuilder setGs(double gs) {
        scene.setDefaultGroundAttenuation(gs);
        return this;
    }

    /**
     * Maximum source distance
     * @param maximumPropagationDistance Maximum source distance
     * @return
     */
    public SceneBuilder setMaximumPropagationDistance(double maximumPropagationDistance) {
        scene.maxSrcDist = maximumPropagationDistance;
        return this;
    }

    
    public SceneBuilder setMaximumReflectionDistance(double maximumReflectionDistance) {
        scene.maxRefDist = maximumReflectionDistance;
        return this;
    }

    
    
    public SceneBuilder setMaximumReflectionOrder(int maximumReflectionOrder) {
        scene.reflexionOrder = maximumReflectionOrder;
        return this;
    }

    /**
     *
     * @return
     */
    public Scene build() {
        return scene;
    }
}
