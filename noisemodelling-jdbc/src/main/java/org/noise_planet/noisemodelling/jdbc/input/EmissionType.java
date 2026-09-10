/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

/**
 * Kind of noise a {@link SceneWithEmission.PeriodEmission} row carries, so the output stage can
 * route it to the right source point for a bridge deck.
 *
 * <p>Only relevant when the scene has bridges. A source point sitting on a deck is split into an
 * {@code ACTUAL_SOURCE_ON_BRIDGE} point (traffic noise, at deck level) and an
 * {@code IMAGINARY_SOURCE_UNDER_BRIDGE} point (structural / deck-radiated noise, at the deck
 * underside). {@code AttenuationOutputSingleThread.checkIfEmissionIsAcceptable} feeds {@code ROAD}
 * rows only to the former and {@code BRIDGE} rows only to the latter.
 *
 * <p>An emission table with no {@code EMISSION_TYPE} column - the common case - yields
 * {@link #ROAD} for every row (see {@link #fromString}), so the deck-underside point stays silent
 * unless a {@code BRIDGE} row is explicitly provided, and a scene that does not use the split
 * behaves exactly as before. (The fork maps an absent column to a permissive {@code NULL} that a
 * deck-underside point would also emit; treating absent as {@code ROAD} avoids that double count.)
 */
public enum EmissionType {
    ROAD,
    BRIDGE;

    /**
     * @param str column value (case-insensitive); {@code null} or empty maps to {@link #ROAD}
     * @return the matching constant
     * @throws IllegalArgumentException if {@code str} is a non-empty unknown value
     */
    public static EmissionType fromString(String str) {
        if (str == null || str.isEmpty()) {
            return ROAD;
        }
        for (EmissionType type : values()) {
            if (type.name().equalsIgnoreCase(str)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EMISSION_TYPE: " + str);
    }
}
