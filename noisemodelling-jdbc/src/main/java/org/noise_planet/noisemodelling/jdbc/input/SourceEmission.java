/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.dBToW;

public class SourceEmission {
    /**
     * Represents the emitted spectrum for a single period.
     *
     * <p>The {@code emission} array contains power values (Watts) for
     * the frequency bands defined by the {@link ProfileBuilder} used to
     * build the scene. The {@code period} field is a short identifier
     * such as "D", "E" or "N" and may be empty when the spectrum is
     * not period-specific.
     */
    public static final String DEN_PERIOD = "DEN";
    public static final String[] STANDARD_PERIOD_VALUE = {"D", "E", "N"};
    public static final double DAY_RATIO = 12. / 24.;
    public static final double EVENING_RATIO = 4. / 24. * dBToW(5.0);
    public static final double NIGHT_RATIO = 8. / 24. * dBToW(10.0);
    public static final double[] RATIOS = new double[] {DAY_RATIO, EVENING_RATIO, NIGHT_RATIO};

    private static Logger LOGGER = LoggerFactory.getLogger(SourceEmission.class);
    public enum StandardPeriod {DAY, EVENING, NIGHT};
    public final String period;
    public enum EmissionType {
        ROAD, 
        BRIDGE,
        ROLLING,
        TRACTIONA,
        TRACTIONB,
        AERODYNAMICA,
        AERODYNAMICB,
        NULL;
        public static EmissionType fromString(String str) {
            for (EmissionType type : EmissionType.values()) {
                if (type.name().equalsIgnoreCase(str)) {
                    return type;
                }
            }
            if (str == null || str.isEmpty()) {
                LOGGER.debug("Empty EmissionType string, defaulting to NULL");
                return NULL;
            }
            throw new IllegalArgumentException("Unknown EmissionType: " + str);
        }
    }
    private final EmissionType emissionType;
    /** Power spectrum (Watts) per frequency band in the profile builder. */
    public final double[] emissionInWatts;

    /**
     * Create a new SourceEmission.
     *
     * @param emissionType the type of emission (e.g., ROAD, BRIDGE)
     * @param period the period identifier (may be empty)
     * @param emissionInWatts the spectrum array (Watts) matching profile frequencies
     */
    public SourceEmission(EmissionType emissionType, String period, double[] emissionInWatts) {
        this.emissionType = emissionType;
        this.period = period;
        this.emissionInWatts = emissionInWatts;
    }

    public SourceEmission(String period, double[] emissionInWatts) {
        this.emissionType = EmissionType.ROAD;
        this.period = period;
        this.emissionInWatts = emissionInWatts;
    }

    public EmissionType getEmissionType() {
        return emissionType;
    }

    public double[] getEmissionInWatts() {
        return emissionInWatts;
    }

    public String getPeriod() {
        return period;
    }

}
    