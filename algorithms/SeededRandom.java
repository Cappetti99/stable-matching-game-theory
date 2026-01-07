import java.util.Random;
import java.security.SecureRandom;

/**
 * Centralized deterministic randomness.
 *
 * Default seed is fixed to make runs reproducible. Override via:
 * - JVM property: -Dseed=<long>
 * - CLI args: --seed <long> or --seed=<long>
 *
 * Control whether to use seed via setUseSeed(boolean):
 * - true (default): Use deterministic seed for reproducible results
 * - false: Use SecureRandom for truly random results
 */
public final class SeededRandom {

    private static volatile long seed = 3L;
    private static volatile boolean useSeed = true;
    private static volatile boolean useFixedSeed = false;  // If true, all runs use same seed
    private static final SecureRandom secureRandom = new SecureRandom();

    private SeededRandom() {
    }

    public static long getSeed() {
        return seed;
    }

    public static void setSeed(long newSeed) {
        seed = newSeed;
    }

    public static boolean isUseSeed() {
        return useSeed;
    }

    public static void setUseSeed(boolean useSeeding) {
        useSeed = useSeeding;
    }

    public static boolean isUseFixedSeed() {
        return useFixedSeed;
    }

    public static void setUseFixedSeed(boolean useFixed) {
        useFixedSeed = useFixed;
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

        boolean seedProvided = false;
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            }
            
            // Check for --fixed-seed flag
            if (arg.equals("--fixed-seed")) {
                setUseFixedSeed(true);
                System.out.println("Fixed seed mode enabled: all runs will use the same seed");
                continue;
            }
            
            if (arg.startsWith("--seed=")) {
                String value = arg.substring("--seed=".length()).trim();
                if (!value.isEmpty()) {
                    setSeed(Long.parseLong(value));
                    seedProvided = true;
                }
            }
            if (arg.equals("--seed") && i + 1 < args.length) {
                String value = args[i + 1];
                if (value != null && !value.isBlank()) {
                    setSeed(Long.parseLong(value.trim()));
                    seedProvided = true;
                }
            }
        }

        return seedProvided;
    }

    /**
     * Returns a deterministic Random instance scoped by a string.
     *
     * Using a scope avoids accidental coupling to call order.
     *
     * If useSeed is false, returns a Random with a truly random seed from SecureRandom.
     */
    public static Random forScope(String scope) {
        if (!useSeed) {
            // Use SecureRandom for truly random seed
            return new Random(secureRandom.nextLong());
        }
        
        long scopeHash = fnv1a64(scope == null ? "" : scope);
        long mixed = mix64(seed ^ scopeHash);
        return new Random(mixed);
    }

    /**
     * Returns a deterministic Random instance scoped by a string and run index.
     * Used for multiple runs with different random values each time.
     *
     * If useSeed is false, returns a Random with a truly random seed from SecureRandom,
     * mixed with runIdx to ensure different results per run.
     * 
     * If useFixedSeed is true, ignores runIdx and returns the same seed for all runs
     * (useful for comparing ablation vs normal runs with identical data).
     *
     * @param scope  Scope identifier (e.g., "data-loader")
     * @param runIdx Run index (0, 1, 2, ...) - ignored if useFixedSeed is true
     * @return Random instance with seed varied by run index (unless useFixedSeed is true)
     */
    public static Random forScopeAndRun(String scope, int runIdx) {
        if (!useSeed) {
            // Use SecureRandom mixed with runIdx for truly random but varied per run
            long randomBase = secureRandom.nextLong();
            long runSeed = randomBase ^ ((long) runIdx * 0x9e3779b97f4a7c15L);
            return new Random(runSeed);
        }
        
        long scopeHash = fnv1a64(scope == null ? "" : scope);
        
        // If fixed seed mode, ignore runIdx
        if (useFixedSeed) {
            long mixed = mix64(seed ^ scopeHash);
            return new Random(mixed);
        }
        
        // Normal mode: vary seed by run index
        long runSeed = seed ^ scopeHash ^ ((long) runIdx * 0x9e3779b97f4a7c15L);
        long mixed = mix64(runSeed);
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
