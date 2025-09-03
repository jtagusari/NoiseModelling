// package org.noise_planet.noisemodelling.jdbc.input;

// import org.locationtech.jts.geom.Coordinate;
// import org.locationtech.jts.geom.Geometry;
import org.noise_planet.noisemodelling.pathfinder.BridgeSourceProcessor;

// import java.util.ArrayList;

/**
 * Pure builder that creates an in-memory representation of a virtual source
 * without modifying the Scene. Registration into the Scene is performed by
 * {SceneVirtualSourceRegistrar}.
 */
public class VirtualSourceBuilder {

    // public static class VirtualSourceData {
    //     public final Geometry geometry;
    //     public final Double gs;
    //     public final Integer emissionAttenuation;
    //     public final org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation orientation;
    //     public final Long bridgePk;
    //     public final ArrayList<SceneWithEmission.PeriodEmission> emissions;

    //     public VirtualSourceData(Geometry geometry, Double gs, Integer emissionAttenuation,
    //                              org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation orientation,
    //                              Long bridgePk,
    //                              ArrayList<SceneWithEmission.PeriodEmission> emissions) {
    //         this.geometry = geometry;
    //         this.gs = gs;
    //         this.emissionAttenuation = emissionAttenuation;
    //         this.orientation = orientation;
    //         this.bridgePk = bridgePk;
    //         this.emissions = emissions;
    //     }
    // }

    // public static VirtualSourceData build(SceneWithEmission scene, Long originalPk, Geometry originalGeom, BridgeSourceProcessor.AdditionalSource src) {
    //     // Copy geometry and set coordinates
    //     Geometry virtualGeom = (Geometry) originalGeom.copy();
    //     Coordinate[] coords = virtualGeom.getCoordinates();
    //     for (Coordinate c : coords) {
    //         c.x = src.position.x;
    //         c.y = src.position.y;
    //         c.z = src.position.z;
    //     }

    //     // Copy attributes from original (defensive)
    //     Double gs = scene.sourceGs.containsKey(originalPk) ? scene.sourceGs.get(originalPk) : null;
    //     Integer emissionAttenuation = scene.sourceEmissionAttenuation.containsKey(originalPk) ? scene.sourceEmissionAttenuation.get(originalPk) : null;
    //     org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation orientation = scene.getSourceOrientations().get(originalPk);
    //     Long bridgePk = scene.sourceBridgePk.get(originalPk);

    //     ArrayList<SceneWithEmission.PeriodEmission> copiedEm = null;
    //     if (scene.getWjSources().containsKey(originalPk)) {
    //         ArrayList<SceneWithEmission.PeriodEmission> originalEm = scene.getWjSources().get(originalPk);
    //         copiedEm = new ArrayList<>();
    //         for (SceneWithEmission.PeriodEmission pe : originalEm) {
    //             copiedEm.add(new SceneWithEmission.PeriodEmission(pe.period, pe.emission.clone()));
    //         }
    //     }

    //     return new VirtualSourceData(virtualGeom, gs, emissionAttenuation, orientation, bridgePk, copiedEm);
    // }
}
