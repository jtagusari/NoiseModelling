package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.Coordinate;
import java.util.List;
import java.util.ArrayList;

/**
 * Coordinates the construction of complete acoustic paths.
 * Manages multiple acoustic path processors and aggregates their results
 * to build comprehensive SegmentPath and PointPath collections from diffraction coordinates.
 */
public class AcousticPathBuilder {
    private AcousticPathBuilder() {}

    public static Path createPath(AcousticPathConfiguration configuration) {
        AcousticPathProcessor acousticPathProcessor = new AcousticPathProcessor(configuration);
        
        if (configuration.getDiffractionPoints().size() < 2) {
            throw new IllegalArgumentException("At least source and receiver points are required.");
        }
        
        if (configuration.getDiffractionPoints().size() == 2) {
            acousticPathProcessor.buildWithSegmentIndex(1);
            return acousticPathProcessor.getPath();
        }
        
        for (int segmentIndex = 1; segmentIndex < configuration.getDiffractionPoints().size(); segmentIndex++) {
            acousticPathProcessor.buildWithSegmentIndex(segmentIndex);
        }
        
        return acousticPathProcessor.getPath();
    }

}
