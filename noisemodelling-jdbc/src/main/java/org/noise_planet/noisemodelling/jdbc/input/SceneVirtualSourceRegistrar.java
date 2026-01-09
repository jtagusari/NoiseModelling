package org.noise_planet.noisemodelling.jdbc.input;

// import java.util.ArrayList;
// import java.util.concurrent.atomic.AtomicLong;

// /**
//  * Responsible for registering built virtual sources into the SceneWithEmission.
//  */
public class SceneVirtualSourceRegistrar {
//     private static final AtomicLong fallbackCounter = new AtomicLong(1000000000L);

//     /**
//      * Register the virtual source into the scene, returning the assigned PK.
//      */
//     public static Long register(SceneWithEmission scene, Long originalPk, VirtualSourceBuilder.VirtualSourceData vs) {
//         long candidate;
//         synchronized (scene.getSourcePks()) {
//             long maxPk = scene.getSourcePks().stream().mapToLong(Long::longValue).max().orElse(0L);
//             candidate = Math.max(maxPk + 1L, fallbackCounter.getAndIncrement());
//             while (scene.getSourcePks().contains(candidate)) {
//                 candidate = fallbackCounter.getAndIncrement();
//             }
//             scene.getSourcePks().add(candidate);
//         }

//         try {
//             scene.sourceGeometries.add(vs.geometry);
//             scene.sourcesIndex.appendGeometry(vs.geometry, scene.sourceGeometries.size() - 1);

//             Long virtualPk = candidate;

//             if (vs.gs != null) scene.sourceGs.put(virtualPk, vs.gs);
//             if (vs.emissionAttenuation != null) scene.sourceEmissionAttenuation.put(virtualPk, vs.emissionAttenuation);
//             if (vs.orientation != null) scene.getSourceOrientations().put(virtualPk, vs.orientation);
//             scene.sourceIsVirtualSource.put(virtualPk, true);
//             if (vs.bridgePk != null) scene.sourceBridgePk.put(virtualPk, vs.bridgePk);

//             if (vs.emissions != null) {
//                 ArrayList<SceneWithEmission.PeriodEmission> copied = new ArrayList<>();
//                 for (SceneWithEmission.PeriodEmission pe : vs.emissions) {
//                     copied.add(new SceneWithEmission.PeriodEmission(pe.period, pe.emission.clone()));
//                 }
//                 scene.wjSources.put(virtualPk, copied);
//             }

//             return virtualPk;
//         } catch (Exception e) {
//             scene.removeSourceByPk(candidate);
//             System.err.println("Failed to register virtual source for " + originalPk + ": " + e.getMessage());
//             return null;
//         }
//     }
}
