package com.transport.tracker.bdd;

import com.intuit.karate.junit5.Karate;

/**
 * Karate Test Suite Runner for Transport API
 * Executes all Karate BDD feature files
 */
public class KarateTestSuite {

    @Karate.Test
    public Karate testAll() {
        return Karate.run().relativeTo(getClass()).karateEnv("test").tags("@regression");
    }

}
