package com.yuqiangdede.platform.common.util;

public final class RuntimeEnvironmentCompat {

    private static final String SKIP_OPENCV_PROPERTY = "vision-mind.skip-opencv";
    private static final String TEST_MODE_PROPERTY = "vision-mind.test-mode";
    private static final String SUREFIRE_TEST_CLASSPATH = "surefire.test.class.path";
    private static final String GRADLE_TEST_WORKER = "org.gradle.test.worker";
    private static final String IDEA_TEST_BUFFER = "idea.test.cyclic.buffer.size";
    private static final String JAVA_COMMAND = "sun.java.command";

    private RuntimeEnvironmentCompat() {
    }

    public static boolean shouldSkipNativeLoad() {
        return Boolean.getBoolean(SKIP_OPENCV_PROPERTY) || isTestEnvironment();
    }

    private static boolean isTestEnvironment() {
        return Boolean.getBoolean(TEST_MODE_PROPERTY)
                || System.getProperty(SUREFIRE_TEST_CLASSPATH) != null
                || System.getProperty(GRADLE_TEST_WORKER) != null
                || System.getProperty(IDEA_TEST_BUFFER) != null
                || isKnownTestCommand(System.getProperty(JAVA_COMMAND));
    }

    private static boolean isKnownTestCommand(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        return command.contains("com.intellij.rt.junit.JUnitStarter")
                || command.contains("org.junit.platform.console.ConsoleLauncher")
                || command.contains("org.eclipse.jdt.internal.junit.runner.RemoteTestRunner");
    }
}
