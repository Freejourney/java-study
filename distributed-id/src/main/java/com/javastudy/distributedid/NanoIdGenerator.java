package com.javastudy.distributedid;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Nano ID generator implementation.
 * 
 * Nano IDs are URL-safe, unique string IDs that are:
 * - Smaller than UUIDs (21 characters by default vs 36)
 * - URL-safe (no special characters)
 * - More secure than UUIDs (uses hardware random generator)
 * - Customizable alphabet and size
 * 
 * The default alphabet is URL-safe and contains 64 characters:
 * A-Z, a-z, 0-9, _, -
 */
public class NanoIdGenerator implements IdGenerator<String> {
    
    // Default URL-safe alphabet (64 characters)
    public static final String DEFAULT_ALPHABET = 
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-";
    
    // Default size (21 characters provides same collision probability as UUID)
    public static final int DEFAULT_SIZE = 21;
    
    private final Random random;
    private final String alphabet;
    private final int size;
    
    /**
     * Create a Nano ID generator with default settings.
     */
    public NanoIdGenerator() {
        this(new SecureRandom(), DEFAULT_ALPHABET, DEFAULT_SIZE);
    }
    
    /**
     * Create a Nano ID generator with custom size.
     * @param size the length of generated IDs
     */
    public NanoIdGenerator(int size) {
        this(new SecureRandom(), DEFAULT_ALPHABET, size);
    }
    
    /**
     * Create a Nano ID generator with custom alphabet and size.
     * @param alphabet the alphabet to use for generation
     * @param size the length of generated IDs
     */
    public NanoIdGenerator(String alphabet, int size) {
        this(new SecureRandom(), alphabet, size);
    }
    
    /**
     * Create a Nano ID generator with all custom parameters.
     * @param random the random source to use
     * @param alphabet the alphabet to use for generation
     * @param size the length of generated IDs
     */
    public NanoIdGenerator(Random random, String alphabet, int size) {
        if (alphabet == null || alphabet.isEmpty()) {
            throw new IllegalArgumentException("Alphabet cannot be null or empty");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive");
        }
        if (alphabet.length() > 256) {
            throw new IllegalArgumentException("Alphabet cannot contain more than 256 characters");
        }
        
        this.random = random;
        this.alphabet = alphabet;
        this.size = size;
    }
    
    @Override
    public String generate() {
        return generateNanoId();
    }
    
    @Override
    public String generateAsString() {
        return generate();
    }
    
    @Override
    public String getGeneratorName() {
        return "NanoID";
    }
    
    /**
     * Generate a Nano ID with the configured parameters.
     * @return a Nano ID string
     */
    private String generateNanoId() {
        char[] result = new char[size];
        int alphabetLength = alphabet.length();
        
        // Use efficient bit manipulation for power-of-2 alphabets
        if (isPowerOfTwo(alphabetLength)) {
            int mask = alphabetLength - 1;
            for (int i = 0; i < size; i++) {
                result[i] = alphabet.charAt(random.nextInt(alphabetLength) & mask);
            }
        } else {
            // Use rejection sampling for non-power-of-2 alphabets
            int bitsNeeded = (int) Math.ceil(Math.log(alphabetLength) / Math.log(2));
            int mask = (1 << bitsNeeded) - 1;
            
            for (int i = 0; i < size; i++) {
                int randomValue;
                do {
                    randomValue = random.nextInt(1 << bitsNeeded) & mask;
                } while (randomValue >= alphabetLength);
                
                result[i] = alphabet.charAt(randomValue);
            }
        }
        
        return new String(result);
    }
    
    /**
     * Check if a number is a power of 2.
     */
    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
    
    /**
     * Generate a Nano ID with custom parameters (static method).
     * @param random the random source to use
     * @param alphabet the alphabet to use
     * @param size the length of the ID
     * @return a Nano ID string
     */
    public static String generateWith(Random random, String alphabet, int size) {
        return new NanoIdGenerator(random, alphabet, size).generate();
    }
    
    /**
     * Generate a Nano ID with custom size (static method).
     * @param size the length of the ID
     * @return a Nano ID string
     */
    public static String generateWith(int size) {
        return new NanoIdGenerator(size).generate();
    }
    
    /**
     * Generate a Nano ID with default parameters (static method).
     * @return a Nano ID string
     */
    public static String generateDefault() {
        return new NanoIdGenerator().generate();
    }
    
    /**
     * Get the alphabet used by this generator.
     * @return the alphabet string
     */
    public String getAlphabet() {
        return alphabet;
    }
    
    /**
     * Get the size of IDs generated by this generator.
     * @return the size
     */
    public int getSize() {
        return size;
    }
    
    /**
     * Calculate the number of IDs that can be generated before collision probability reaches 1%.
     * @return the number of IDs
     */
    public long getCollisionResistance() {
        return (long) Math.pow(alphabet.length(), size) / 100;
    }
    
    /**
     * Predefined alphabet for numbers only.
     */
    public static final String NUMBERS_ALPHABET = "0123456789";
    
    /**
     * Predefined alphabet for lowercase letters only.
     */
    public static final String LOWERCASE_ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    
    /**
     * Predefined alphabet for uppercase letters only.
     */
    public static final String UPPERCASE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    /**
     * Predefined alphabet for letters and numbers (no special characters).
     */
    public static final String ALPHANUMERIC_ALPHABET = 
        NUMBERS_ALPHABET + LOWERCASE_ALPHABET + UPPERCASE_ALPHABET;
    
    /**
     * Predefined alphabet without ambiguous characters (0, O, I, l, 1).
     */
    public static final String NO_AMBIGUOUS_ALPHABET = 
        "23456789ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
} 