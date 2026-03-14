package com.yuqiangdede.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class RuntimeEnvironmentTest {

    @Test
    void isTestEnvironment_returnsFalseWhenNoTestMarkersExist() {
        withClearedProperties(() -> assertFalse(RuntimeEnvironment.isTestEnvironment()));
    }

    @Test
    void isTestEnvironment_returnsTrueWhenVisionMindTestModeEnabled() {
        withClearedProperties(() -> {
            System.setProperty("vision-mind.test-mode", "true");
            assertTrue(RuntimeEnvironment.isTestEnvironment());
        });
    }

    @Test
    void shouldSkipNativeLoad_returnsTrueWhenSkipPropertyEnabled() {
        withClearedProperties(() -> {
            System.setProperty("vision-mind.skip-opencv", "true");
            assertTrue(RuntimeEnvironment.shouldSkipNativeLoad());
        });
    }

    @Test
    void isTestEnvironment_returnsTrueWhenKnownTestCommandPresent() {
        withClearedProperties(() -> {
            System.setProperty("sun.java.command", "com.intellij.rt.junit.JUnitStarter -ideVersion5");
            assertTrue(RuntimeEnvironment.isTestEnvironment());
        });
    }

    private void withClearedProperties(Runnable assertion) {
        String originalSkip = System.getProperty("vision-mind.skip-opencv");
        String originalTestMode = System.getProperty("vision-mind.test-mode");
        String originalSurefire = System.getProperty("surefire.test.class.path");
        String originalGradleWorker = System.getProperty("org.gradle.test.worker");
        String originalIdeaBuffer = System.getProperty("idea.test.cyclic.buffer.size");
        String originalJavaCommand = System.getProperty("sun.java.command");
        try {
            clearProperty("vision-mind.skip-opencv");
            clearProperty("vision-mind.test-mode");
            clearProperty("surefire.test.class.path");
            clearProperty("org.gradle.test.worker");
            clearProperty("idea.test.cyclic.buffer.size");
            clearProperty("sun.java.command");
            assertion.run();
        } finally {
            restoreProperty("vision-mind.skip-opencv", originalSkip);
            restoreProperty("vision-mind.test-mode", originalTestMode);
            restoreProperty("surefire.test.class.path", originalSurefire);
            restoreProperty("org.gradle.test.worker", originalGradleWorker);
            restoreProperty("idea.test.cyclic.buffer.size", originalIdeaBuffer);
            restoreProperty("sun.java.command", originalJavaCommand);
        }
    }

    private void clearProperty(String property) {
        System.clearProperty(property);
    }

    private void restoreProperty(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
            return;
        }
        System.setProperty(property, value);
    }
}
