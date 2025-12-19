import java.util.Random;

/**
 * Centralized deterministic randomness.
 *
 * Default seed is fixed to make runs reproducible. Override via:
 * - JVM property: -Dseed=<long>
 * - CLI args: --seed <long> or --seed=<long>
 */
public final class SeededRandom {

    private static volatile long seed = 3L;

    private SeededRandom() {
    }

    public static long getSeed() {
        return seed;
    }

    public static void setSeed(long newSeed) {
        seed = newSeed;
    }

    /**
     * Initialize seed from system property or CLI args.
     *
     * @return true if an explicit seed was provided, false otherwise.
     */
    public static boolean initFromArgs(String[] args) {
        String prop = System.getProperty("seed");
        if (prop != null && !prop.isBlank()) {
            try {
                setSeed(Long.parseLong(prop.trim()));
                return true;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }

        if (args == null) {
            return false;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            }
            if (arg.startsWith("--seed=")) {
                String value = arg.substring("--seed=".length()).trim();
                if (!value.isEmpty()) {
                    setSeed(Long.parseLong(value));
                    return true;
                }
            }
            if (arg.equals("--seed") && i + 1 < args.length) {
                String value = args[i + 1];
                if (value != null && !value.isBlank()) {
                    setSeed(Long.parseLong(value.trim()));
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns a deterministic Random instance scoped by a string.
     *
     * Using a scope avoids accidental coupling to call order.
     */
    public static Random forScope(String scope) {
        long scopeHash = fnv1a64(scope == null ? "" : scope);
        long mixed = mix64(seed ^ scopeHash);
        return new Random(mixed);
    }

    private static long fnv1a64(String s) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    // SplitMix64 finalizer
    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
