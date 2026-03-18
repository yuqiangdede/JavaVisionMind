package com.yuqiangdede.tts.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class TtsPathResolver {

    private static final Pattern SHERPA_JAVA_VERSIONED_JAR = Pattern.compile("sherpa-onnx-v([0-9.]+)-java(\\d+)\\.jar");
    private static final Pattern SHERPA_JAVA_JAR_VERSION = Pattern.compile("sherpa-onnx-v([0-9.]+)\\.jar");
    private static final Pattern SHERPA_JAVA_ANY_JAR = Pattern.compile("sherpa-onnx-v([0-9.]+)(?:-java(\\d+))?\\.jar");
    private static final String RESOURCE_PREFIX = "resource/";

    @Value("${tts.model-root:resource/tts/model}")
    private String modelRootValue;

    @Value("${tts.runtime-java-jar:auto}")
    private String runtimeJavaJarValue;

    @Value("${tts.runtime-native-jar:auto}")
    private String runtimeNativeJarValue;

    @Value("${tts.runtime-provider:cpu}")
    private String provider;

    @Value("${tts.num-threads:2}")
    private int numThreads;

    @Value("${tts.default-speaker-id:0}")
    private int defaultSpeakerId;

    @Value("${tts.default-model:sherpa-onnx-vits-zh-ll}")
    private String defaultModel;

    @Value("${tts.max-input-length:300}")
    private int maxInputLength;

    @Value("${tts.default-speed:1.0}")
    private float defaultSpeed;

    @Value("${tts.silence-scale:0.2}")
    private float silenceScale;

    @Value("${tts.vits-length-scale:1.0}")
    private float lengthScale;

    @Value("${tts.vits-noise-scale:0.667}")
    private float noiseScale;

    @Value("${tts.vits-noise-scale-w:0.8}")
    private float noiseScaleW;

    private Path projectRoot;
    private Path extractionRoot;
    private Path modelRoot;
    private Path runtimeJavaJar;
    private Path runtimeNativeJar;

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    @PostConstruct
    public void init() {
        projectRoot = locateProjectRoot(Paths.get("").toAbsolutePath().normalize());
        extractionRoot = resolveExtractionRoot();
        modelRoot = resolveReadOnly(modelRootValue);
    }

    public Path getModelRoot() {
        return modelRoot;
    }

    public Path getModelDir(String modelId) {
        return modelRoot.resolve(modelId).normalize();
    }

    public Path getRuntimeJavaJar() {
        if (runtimeJavaJar == null) {
            runtimeJavaJar = resolveRuntimeJavaJar();
        }
        return runtimeJavaJar;
    }

    public Path getRuntimeNativeJar() {
        if (runtimeNativeJar == null) {
            runtimeNativeJar = resolveRuntimeNativeJar();
        }
        return runtimeNativeJar;
    }

    public String getProvider() {
        return provider;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public int getDefaultSpeakerId() {
        return defaultSpeakerId;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public int getMaxInputLength() {
        return maxInputLength;
    }

    public float getDefaultSpeed() {
        return defaultSpeed;
    }

    public float getSilenceScale() {
        return silenceScale;
    }

    public float getLengthScale() {
        return lengthScale;
    }

    public float getNoiseScale() {
        return noiseScale;
    }

    public float getNoiseScaleW() {
        return noiseScaleW;
    }

    public String getRuleFsts(Path modelDir) {
        return java.util.stream.Stream.of(
                        modelDir.resolve("phone.fst"),
                        modelDir.resolve("date.fst"),
                        modelDir.resolve("number.fst"),
                        modelDir.resolve("new_heteronym.fst"))
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .collect(Collectors.joining(","));
    }

    private Path resolveRuntimeNativeJar() {
        String configured = runtimeNativeJarValue == null ? "" : runtimeNativeJarValue.trim();
        if (!configured.isEmpty() && !"auto".equalsIgnoreCase(configured)) {
            return resolve(configured);
        }

        Path javaJar = getRuntimeJavaJar();
        String version = extractSherpaVersion(javaJar.getFileName().toString());
        String platformTag = resolveSherpaPlatformTag();
        String nativeJarName = String.format("sherpa-onnx-native-lib-%s-v%s.jar", platformTag, version);
        return javaJar.getParent().resolve(nativeJarName).normalize();
    }

    private Path resolveRuntimeJavaJar() {
        String configured = runtimeJavaJarValue == null ? "" : runtimeJavaJarValue.trim();
        if (!configured.isEmpty() && !"auto".equalsIgnoreCase(configured)) {
            return resolve(configured);
        }

        Path sherpaDir = resolveReadOnly("resource/lib/sherpa-onnx");
        int currentJavaFeature = Runtime.version().feature();
        Path best = null;
        int bestFeature = Integer.MIN_VALUE;
        boolean bestIsGeneric = true;

        try (java.util.stream.Stream<Path> stream = Files.list(sherpaDir)) {
            for (Path candidate : stream.filter(Files::isRegularFile).collect(Collectors.toList())) {
                Matcher matcher = SHERPA_JAVA_ANY_JAR.matcher(candidate.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }

                String featureGroup = matcher.group(2);
                boolean genericJar = featureGroup == null || featureGroup.isBlank();
                int jarFeature = genericJar ? Integer.MIN_VALUE : Integer.parseInt(featureGroup);
                if (!genericJar && jarFeature > currentJavaFeature) {
                    continue;
                }

                if (best == null) {
                    best = candidate;
                    bestFeature = jarFeature;
                    bestIsGeneric = genericJar;
                    continue;
                }

                if (bestIsGeneric && !genericJar) {
                    best = candidate;
                    bestFeature = jarFeature;
                    bestIsGeneric = false;
                    continue;
                }

                if (bestIsGeneric == genericJar && jarFeature > bestFeature) {
                    best = candidate;
                    bestFeature = jarFeature;
                    bestIsGeneric = genericJar;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to scan sherpa Java API jars: " + sherpaDir, e);
        }

        if (best != null) {
            return best.normalize();
        }

        throw new IllegalStateException("No usable sherpa Java API jar was found.");
    }

    private String extractSherpaVersion(String javaJarName) {
        Matcher versionedMatcher = SHERPA_JAVA_VERSIONED_JAR.matcher(javaJarName);
        if (versionedMatcher.matches()) {
            return versionedMatcher.group(1);
        }

        Matcher matcher = SHERPA_JAVA_JAR_VERSION.matcher(javaJarName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Failed to parse sherpa version from jar name: " + javaJarName);
        }
        return matcher.group(1);
    }

    private String resolveSherpaPlatformTag() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String archName = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "win-x64";
        }
        if (osName.contains("linux")) {
            if (archName.contains("aarch64") || archName.contains("arm64")) {
                return "linux-aarch64";
            }
            if (archName.contains("amd64") || archName.contains("x86_64")) {
                return "linux-x64";
            }
            throw new IllegalStateException("Unsupported Linux architecture: " + archName);
        }
        throw new IllegalStateException("Unsupported operating system: " + osName);
    }

    private Path resolve(String pathValue) {
        Path path = Paths.get(pathValue);
        if (path.isAbsolute()) {
            return path.normalize();
        }

        String normalizedValue = normalizeResourcePath(pathValue);
        Path primary = projectRoot.resolve(path).normalize();
        if (!Files.exists(primary) && normalizedValue.startsWith(RESOURCE_PREFIX) && classpathDirectoryExists(normalizedValue)) {
            extractClasspathDirectory(normalizedValue, primary, false);
        }
        return primary;
    }

    private Path resolveReadOnly(String pathValue) {
        Path externalPath = resolve(pathValue);
        if (Files.exists(externalPath)) {
            return externalPath;
        }

        String normalizedValue = normalizeResourcePath(pathValue);
        if (normalizedValue.startsWith(RESOURCE_PREFIX) && classpathDirectoryExists(normalizedValue)) {
            return extractClasspathDirectory(normalizedValue, extractionRoot.resolve(normalizedValue).normalize(), false);
        }
        return externalPath;
    }

    private Path resolveExtractionRoot() {
        try {
            URI location = TtsPathResolver.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path codeSourcePath = Paths.get(location).toAbsolutePath().normalize();
            Path baseDir = Files.isRegularFile(codeSourcePath) ? codeSourcePath.getParent() : codeSourcePath;
            if (baseDir != null) {
                return baseDir.resolve("tts-java-bundle").normalize();
            }
        } catch (Exception ignored) {
        }
        return projectRoot.resolve("tts-java-bundle").normalize();
    }

    private Path locateProjectRoot(Path start) {
        Path current = start;
        while (current != null) {
            boolean hasResource = Files.isDirectory(current.resolve("resource"));
            boolean hasPom = Files.isRegularFile(current.resolve("pom.xml"));
            if (hasResource && hasPom) {
                return current.normalize();
            }
            current = current.getParent();
        }
        return start;
    }

    private String normalizeResourcePath(String pathValue) {
        String normalizedValue = pathValue.replace("\\", "/");
        if (projectRoot.getFileName() != null && "resource".equalsIgnoreCase(projectRoot.getFileName().toString())
                && normalizedValue.startsWith(RESOURCE_PREFIX)) {
            return normalizedValue.substring(RESOURCE_PREFIX.length());
        }
        return normalizedValue;
    }

    private boolean classpathDirectoryExists(String normalizedValue) {
        try {
            Resource[] resources = resourceResolver.getResources("classpath*:" + normalizedValue + "/**");
            for (Resource resource : resources) {
                if (resource.exists() && resource.isReadable()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private Path extractClasspathDirectory(String normalizedValue, Path targetDir, boolean overwriteExisting) {
        try {
            Files.createDirectories(targetDir);
            Resource[] resources = resourceResolver.getResources("classpath*:" + normalizedValue + "/**");
            String marker = normalizedValue + "/";
            for (Resource resource : resources) {
                if (!resource.exists() || !resource.isReadable()) {
                    continue;
                }

                String relativePath = resolveRelativePath(resource, marker);
                if (relativePath.isEmpty()) {
                    continue;
                }

                Path targetFile = targetDir.resolve(relativePath).normalize();
                Files.createDirectories(targetFile.getParent());
                if (Files.exists(targetFile) && !overwriteExisting) {
                    continue;
                }

                try (InputStream inputStream = resource.getInputStream()) {
                    if (overwriteExisting) {
                        Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Files.copy(inputStream, targetFile);
                    }
                }
            }
            cleanupEmptyDirectories(targetDir);
            return targetDir;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract bundled resources: " + normalizedValue, e);
        }
    }

    private String resolveRelativePath(Resource resource, String marker) throws Exception {
        String url = resource.getURL().toString().replace("\\", "/");
        int index = url.indexOf(marker);
        if (index < 0) {
            return "";
        }

        String relativePath = url.substring(index + marker.length());
        int nestedJarIndex = relativePath.indexOf("!/");
        if (nestedJarIndex >= 0) {
            relativePath = relativePath.substring(nestedJarIndex + 2);
        }
        if (relativePath.endsWith("/")) {
            return "";
        }
        return relativePath;
    }

    private void cleanupEmptyDirectories(Path targetDir) {
        try (java.util.stream.Stream<Path> stream = Files.walk(targetDir)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(Files::isDirectory)
                    .forEach(path -> {
                        try (java.util.stream.Stream<Path> children = Files.list(path)) {
                            if (children.findFirst().isEmpty() && !path.equals(targetDir)) {
                                Files.deleteIfExists(path);
                            }
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }
}
