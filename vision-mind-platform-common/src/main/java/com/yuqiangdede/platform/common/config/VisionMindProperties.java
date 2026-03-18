package com.yuqiangdede.platform.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vision-mind")
public class VisionMindProperties {

    private final Resource resource = new Resource();
    private final Native nativeLoad = new Native();
    private final Runtime runtime = new Runtime();
    private final VectorStore vectorStore = new VectorStore();

    public Resource getResource() {
        return resource;
    }

    public Native getNativeLoad() {
        return nativeLoad;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public static class Resource {
        private String root = "./resource";
        private String fallbackEnv = "VISION_MIND_PATH";
        private boolean validateOnStartup = true;
        private String manifest = "resource/manifest.json";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public String getFallbackEnv() {
            return fallbackEnv;
        }

        public void setFallbackEnv(String fallbackEnv) {
            this.fallbackEnv = fallbackEnv;
        }

        public boolean isValidateOnStartup() {
            return validateOnStartup;
        }

        public void setValidateOnStartup(boolean validateOnStartup) {
            this.validateOnStartup = validateOnStartup;
        }

        public String getManifest() {
            return manifest;
        }

        public void setManifest(String manifest) {
            this.manifest = manifest;
        }
    }

    public static class Native {
        private boolean skipOpenCv;

        public boolean isSkipOpenCv() {
            return skipOpenCv;
        }

        public void setSkipOpenCv(boolean skipOpenCv) {
            this.skipOpenCv = skipOpenCv;
        }
    }

    public static class Runtime {
        private String profile = "default";

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }
    }

    public static class VectorStore {
        private String mode = "memory";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }
}
