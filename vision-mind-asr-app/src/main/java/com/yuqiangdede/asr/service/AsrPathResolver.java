package com.yuqiangdede.asr.service;

import com.yuqiangdede.asr.config.AsrRuntimeProperties;
import com.yuqiangdede.platform.common.resource.ResourcePathResolver;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Getter
public class AsrPathResolver {

    private static final Pattern SHERPA_JAVA_VERSIONED_JAR = Pattern.compile("sherpa-onnx-v([0-9.]+)-java(\\d+)\\.jar");
    private static final Pattern SHERPA_JAVA_JAR_VERSION = Pattern.compile("sherpa-onnx-v([0-9.]+)\\.jar");
    private static final Pattern SHERPA_JAVA_ANY_JAR = Pattern.compile("sherpa-onnx-v([0-9.]+)(?:-java(\\d+))?\\.jar");

    private final AsrRuntimeProperties properties;
    private final ResourcePathResolver resourcePathResolver;

    private Path projectRoot;
    private Path modelDir;
    private Path punctuationModelDir;
    private Path configDir;
    private Path uploadDir;
    private Path hotwordsDir;
    private Path runtimeJavaJar;
    private Path runtimeNativeJar;

    public AsrPathResolver(AsrRuntimeProperties properties, ResourcePathResolver resourcePathResolver) {
        this.properties = properties;
        this.resourcePathResolver = resourcePathResolver;
    }

    @PostConstruct
    public void init() {
        projectRoot = resourcePathResolver.resourceRoot();
        modelDir = resolve(properties.getModelDir());
        punctuationModelDir = resolve(properties.getPunctuationModelDir());
        configDir = resolve(properties.getConfigDir());
        uploadDir = resolve(properties.getUploadDir());
        hotwordsDir = resolve(properties.getHotwordsDir());
        runtimeJavaJar = resolveRuntimeJavaJar();
        runtimeNativeJar = resolveRuntimeNativeJar();
    }

    public Path resolve(String pathValue) {
        return resourcePathResolver.resolve(pathValue);
    }

    public Path hotwordsConfigFile() {
        return configDir.resolve("hotwords.yaml");
    }

    public Path phraseRulesConfigFile() {
        return configDir.resolve("phrase-rules.yaml");
    }

    public void ensureMutableDirectories() {
        createDirectory(configDir);
        createDirectory(uploadDir);
        createDirectory(hotwordsDir);
    }

    public String getProvider() {
        return properties.getRuntimeProvider();
    }

    public int getNumThreads() {
        return properties.getNumThreads();
    }

    public float getHotwordsScore() {
        return properties.getHotwordsScore();
    }

    public String getDecodingMethod() {
        return properties.getDecodingMethod();
    }

    private Path resolveRuntimeNativeJar() {
        String configured = valueOrEmpty(properties.getRuntimeNativeJar());
        if (!configured.isEmpty() && !"auto".equalsIgnoreCase(configured)) {
            return resolve(configured);
        }
        String version = extractSherpaVersion(runtimeJavaJar.getFileName().toString());
        String platformTag = resolveSherpaPlatformTag();
        String nativeJarName = String.format("sherpa-onnx-native-lib-%s-v%s.jar", platformTag, version);
        return runtimeJavaJar.getParent().resolve(nativeJarName).normalize();
    }

    private Path resolveRuntimeJavaJar() {
        String configured = valueOrEmpty(properties.getRuntimeJavaJar());
        if (!configured.isEmpty() && !"auto".equalsIgnoreCase(configured)) {
            return resolve(configured);
        }

        Path sherpaDir = resolve("resource/lib/sherpa-onnx");
        int currentJavaFeature = Runtime.version().feature();
        Path best = null;
        int bestFeature = Integer.MIN_VALUE;
        boolean bestIsGeneric = true;

        try (var stream = Files.list(sherpaDir)) {
            for (Path candidate : stream.filter(Files::isRegularFile).toList()) {
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
            throw new IllegalStateException("扫描 sherpa Java API jar 失败: " + sherpaDir, e);
        }

        if (best != null) {
            return best.normalize();
        }
        throw new IllegalStateException("未找到可用的 sherpa Java API jar。请放入 sherpa-onnx-v<版本>.jar 或 sherpa-onnx-v<版本>-java<版本>.jar");
    }

    private String extractSherpaVersion(String javaJarName) {
        Matcher versionedMatcher = SHERPA_JAVA_VERSIONED_JAR.matcher(javaJarName);
        if (versionedMatcher.matches()) {
            return versionedMatcher.group(1);
        }
        Matcher matcher = SHERPA_JAVA_JAR_VERSION.matcher(javaJarName);
        if (!matcher.matches()) {
            throw new IllegalStateException("无法从 sherpa Java jar 文件名解析版本: " + javaJarName);
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
            throw new IllegalStateException("暂不支持的 Linux 架构: " + archName);
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            if (archName.contains("aarch64") || archName.contains("arm64")) {
                return "osx-aarch64";
            }
            if (archName.contains("amd64") || archName.contains("x86_64")) {
                return "osx-x64";
            }
            throw new IllegalStateException("暂不支持的 macOS 架构: " + archName);
        }
        throw new IllegalStateException("暂不支持的操作系统: " + osName);
    }

    private void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            throw new IllegalStateException("创建目录失败: " + path, e);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
