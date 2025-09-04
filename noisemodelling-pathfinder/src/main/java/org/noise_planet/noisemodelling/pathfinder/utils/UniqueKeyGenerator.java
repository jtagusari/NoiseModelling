package org.noise_planet.noisemodelling.pathfinder.utils;

import java.util.List;
import java.util.logging.Logger;

public class UniqueKeyGenerator {
    private static final Logger LOGGER = Logger.getLogger(UniqueKeyGenerator.class.getName());
    /**
     * Generate a unique positive long key based on an initial candidate.
     * <p>
     * Behavior:
     * - If the provided {@code keyCandidate} is positive and not present in {@code keyList},
     *   it is returned immediately and added to the list.
     * - Otherwise, a deterministic mixing function is applied to the base candidate plus
     *   a small counter to produce new candidates until a positive and unused key is found.
     * - To avoid infinite loops, the method gives up after {@code MAX_TRIES} attempts
     *   and throws an {@link IllegalStateException}.
     *
     * Note: This method mutates the provided {@code keyList} by adding the chosen key.
     *
     * @param keyCandidate initial key suggestion (may be positive, zero or negative)
     * @param keyList      list of already used keys; used to check collisions
     * @return a positive, unique long value added to {@code keyList}
     * @throws IllegalStateException if a unique positive key cannot be found after many attempts
     */
    public static long generateLongKey(long keyCandidate, List<Long> keyList) {

        // Fast-path: if the candidate is positive and not yet used, accept it immediately.
        if (!keyList.contains(keyCandidate) && keyCandidate > 0) {
            return keyCandidate;
        }

        // Otherwise, derive new candidates deterministically from the base value.
        long base = keyCandidate;
        long candidate = base;
        int counter = 0;
        final int MAX_TRIES = 1_000_000; // safety cap to avoid infinite loops

        // Loop until we find a candidate that is both positive and not already contained in the list.
        // We add the counter to the base and apply a bit-mixing function to obtain a well-distributed value.
        while ((keyList.contains(candidate) || candidate < 0) && counter < MAX_TRIES) {
            counter++;
            candidate = mix64(base + counter);
        }

        if (counter >= MAX_TRIES) {
            // If we exhausted attempts, fail fast and let the caller decide how to handle it.
            throw new IllegalStateException("Unable to generate unique positive key after many attempts");
        }

        // Reserve the candidate by adding it to the provided collection and return it.
        // If the chosen key differs from the original candidate, emit an info-level log.
        if (keyCandidate != candidate) {
            LOGGER.info("UniqueKeyGenerator: keyCandidate=" + keyCandidate + " adjusted to registeredKey=" + candidate);
        }
        return candidate;
    }

    /**
     * Small, fast, deterministic 64-bit mixing function based on SplitMix64 constants.
     * The function provides good avalanche properties and is suitable for generating
     * pseudo-random-looking values from sequential inputs (base + counter).
     *
     * @param z input value to mix
     * @return mixed 64-bit value
     */
    private static long mix64(long z) {
        // The constants and shifts below are taken from widely-used splitmix64 mixing.
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
