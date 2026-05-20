package com.example.yzytoolbox.module;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class ModuleManifest {
    
    @SerializedName("version")
    private String version;
    
    @SerializedName("updated")
    private String updated;
    
    @SerializedName("modules")
    private Map<String, ModuleInfo> modules;
    
    public String getVersion() {
        return version;
    }
    
    public String getUpdated() {
        return updated;
    }
    
    public Map<String, ModuleInfo> getModules() {
        return modules;
    }
    
    public static class ModuleInfo {
        @SerializedName("name")
        private String name;
        
        @SerializedName("description")
        private String description;
        
        @SerializedName("category")
        private String category;
        
        @SerializedName("icon")
        private String icon;
        
        @SerializedName("type")
        private String type;
        
        @SerializedName("versions")
        private Map<String, VersionInfo> versions;
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getCategory() {
            return category;
        }
        
        public String getIcon() {
            return icon;
        }
        
        public String getType() {
            return type;
        }
        
        public Map<String, VersionInfo> getVersions() {
            return versions;
        }
        
        public String getLatestVersion() {
            if (versions == null || versions.isEmpty()) {
                return null;
            }
            String latest = null;
            for (String version : versions.keySet()) {
                if (latest == null || version.compareTo(latest) > 0) {
                    latest = version;
                }
            }
            return latest;
        }
        
        public VersionInfo getLatestVersionInfo() {
            String latestVersion = getLatestVersion();
            if (latestVersion != null && versions != null) {
                return versions.get(latestVersion);
            }
            return null;
        }
    }
    
    public static class VersionInfo {
        @SerializedName("url")
        private String url;
        
        @SerializedName("size")
        private long size;
        
        @SerializedName("md5")
        private String md5;
        
        public String getUrl() {
            return url;
        }
        
        public long getSize() {
            return size;
        }
        
        public String getMd5() {
            return md5;
        }
        
        public String getFormattedSize() {
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format("%.1f KB", size / 1024.0);
            } else {
                return String.format("%.1f MB", size / (1024.0 * 1024.0));
            }
        }
    }
}
