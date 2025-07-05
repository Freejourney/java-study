package com.javastudy.distributedid;

/**
 * Common interface for all distributed ID generators.
 * This interface defines the basic contract for generating unique IDs.
 */
public interface IdGenerator<T> {
    
    /**
     * Generate a new unique ID.
     * @return a unique ID of type T
     */
    T generate();
    
    /**
     * Generate a new unique ID as a String.
     * @return a unique ID as a String
     */
    String generateAsString();
    
    /**
     * Get the name/type of this ID generator.
     * @return the name of the generator
     */
    String getGeneratorName();
} 