package org.noise_planet.noisemodelling.jdbc.input;

// import org.h2gis.utilities.SpatialResultSet;
// import org.locationtech.jts.geom.Coordinate;
// import org.locationtech.jts.geom.Geometry;
// import org.noise_planet.noisemodelling.pathfinder.BridgeSourceProcessor;

// import java.sql.SQLException;
// import java.util.List;
// import java.util.Map;

// /**
//  * Service that orchestrates bridge-related adjustments for sources.
//  * It delegates virtual source creation to {@link VirtualSourceCreator}.
//  */
public class BridgeAdjustmentService {

//     /**
//      * Run bridge adjustments for a given source. Returns the number of virtual sources created.
//      */
//     public static int generateVirtualSourcesForSource(SceneWithEmission scene, long pk, Geometry geom, SpatialResultSet rs) throws SQLException {
//         try {
//             Coordinate coord = geom.getCoordinate();
//             if (coord == null) {
//                 return 0;
//             }

//             org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation orientation =
//                     scene.getSourceOrientations().get(pk);

//             Map<Long, BridgeSourceProcessor.BridgeInfo> bridgeInfo =
//                     org.noise_planet.noisemodelling.pathfinder.BridgeInformationCollector.collectBridgeInformation(scene);

//             BridgeSourceProcessor processor = new BridgeSourceProcessor(scene.profileBuilder, bridgeInfo);

//             List<BridgeSourceProcessor.AdditionalSource> additional =
//                     processor.processBridgeAdjustments(pk, coord, 1.0, orientation);

//             int added = 0;
//             for (BridgeSourceProcessor.AdditionalSource src : additional) {
//                 VirtualSourceBuilder.VirtualSourceData vs = VirtualSourceBuilder.build(scene, pk, geom, src);
//                 Long assigned = SceneVirtualSourceRegistrar.register(scene, pk, vs);
//                 if (assigned != null) added++;
//             }
//             return added;
//         } catch (Exception e) {
//             System.err.println("Warning: generateVirtualSourcesForSource failed for source " + pk + ": " + e.getMessage());
//             return 0;
//         }
//     }
}
