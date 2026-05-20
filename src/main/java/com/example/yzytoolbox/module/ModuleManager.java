package com.example.yzytoolbox.module;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.WebView;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModuleManager {
    private static final String TAG = "ModuleManager";
    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/git-spec-xy/yzytoolbox/main/manifest.json";
    private static final String MODULES_DIR = "modules";
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final DownloadManager downloadManager;
    private long currentDownloadId = -1;
    private OnDownloadListener downloadListener;
    
    private BroadcastReceiver downloadReceiver;
    
    public interface OnDownloadListener {
        void onDownloadStart(String moduleId);
        void onDownloadProgress(String moduleId, int progress);
        void onDownloadComplete(String moduleId, boolean success, String error);
    }
    
    public ModuleManager(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        ensureModulesDir();
        registerDownloadReceiver();
    }
    
    private void ensureModulesDir() {
        File modulesDir = new File(context.getFilesDir(), MODULES_DIR);
        if (!modulesDir.exists()) {
            modulesDir.mkdirs();
        }
    }
    
    private void registerDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId == currentDownloadId) {
                    handleDownloadComplete(downloadId);
                }
            }
        };
        context.registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }
    
    public void setOnDownloadListener(OnDownloadListener listener) {
        this.downloadListener = listener;
    }
    
    public void fetchManifest(final OnManifestListener listener) {
        Request request = new Request.Builder()
                .url(MANIFEST_URL)
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to fetch manifest", e);
                if (listener != null) {
                    runOnUiThread(() -> listener.onError(e.getMessage()));
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (listener != null) {
                        runOnUiThread(() -> listener.onError("HTTP " + response.code()));
                    }
                    return;
                }
                
                String json = response.body().string();
                try {
                    ModuleManifest manifest = gson.fromJson(json, ModuleManifest.class);
                    if (listener != null) {
                        runOnUiThread(() -> listener.onManifestLoaded(manifest));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse manifest", e);
                    if (listener != null) {
                        runOnUiThread(() -> listener.onError("Parse error: " + e.getMessage()));
                    }
                }
            }
        });
    }
    
    public void downloadModule(String moduleId, String version, String url) {
        if (downloadListener != null) {
            runOnUiThread(() -> downloadListener.onDownloadStart(moduleId));
        }
        
        String fileName = moduleId + "-" + version + ".zip";
        File modulesDir = new File(context.getFilesDir(), MODULES_DIR);
        
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle("下载模块: " + moduleId)
                .setDescription("版本 " + version)
                .setDestinationUri(Uri.fromFile(new File(modulesDir, fileName)))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);
        
        currentDownloadId = downloadManager.enqueue(request);
    }
    
    private void handleDownloadComplete(long downloadId) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        Cursor cursor = downloadManager.query(query);
        
        if (cursor.moveToFirst()) {
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            String uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                File zipFile = new File(Uri.parse(uri).getPath());
                String moduleId = extractModuleIdFromFileName(zipFile.getName());
                
                boolean success = unzipModule(zipFile, moduleId);
                zipFile.delete();
                
                if (downloadListener != null) {
                    runOnUiThread(() -> downloadListener.onDownloadComplete(moduleId, success, 
                            success ? null : "解压失败"));
                }
            } else {
                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                Log.e(TAG, "Download failed: " + reason);
                if (downloadListener != null) {
                    runOnUiThread(() -> downloadListener.onDownloadComplete(null, false, "下载失败: " + reason));
                }
            }
        }
        cursor.close();
    }
    
    private String extractModuleIdFromFileName(String fileName) {
        int dashIndex = fileName.indexOf("-");
        if (dashIndex > 0) {
            return fileName.substring(0, dashIndex);
        }
        int dotIndex = fileName.indexOf(".");
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }
    
    private boolean unzipModule(File zipFile, String moduleId) {
        File moduleDir = new File(context.getFilesDir(), MODULES_DIR + "/" + moduleId);
        if (!moduleDir.exists()) {
            moduleDir.mkdirs();
        }
        
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryFile = new File(moduleDir, entry.getName());
                
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs();
                    try (InputStream is = zip.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                    }
                }
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to unzip module", e);
            return false;
        }
    }
    
    public void deleteModule(String moduleId) {
        File moduleDir = new File(context.getFilesDir(), MODULES_DIR + "/" + moduleId);
        if (moduleDir.exists()) {
            deleteRecursive(moduleDir);
        }
    }
    
    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
    
    public boolean isModuleInstalled(String moduleId) {
        File moduleDir = new File(context.getFilesDir(), MODULES_DIR + "/" + moduleId);
        if (!moduleDir.exists() || !moduleDir.isDirectory()) {
            return false;
        }
        
        File indexFile = new File(moduleDir, "index.html");
        return indexFile.exists();
    }
    
    public void launchModule(String moduleId, WebView webView) {
        File moduleDir = new File(context.getFilesDir(), MODULES_DIR + "/" + moduleId);
        File indexFile = new File(moduleDir, "index.html");
        
        if (indexFile.exists()) {
            webView.loadUrl("file://" + indexFile.getAbsolutePath());
        } else {
            Log.e(TAG, "Module not found: " + moduleId);
        }
    }
    
    public void cleanup() {
        if (downloadReceiver != null) {
            context.unregisterReceiver(downloadReceiver);
        }
    }
    
    private void runOnUiThread(Runnable action) {
        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
        mainHandler.post(action);
    }
    
    public interface OnManifestListener {
        void onManifestLoaded(ModuleManifest manifest);
        void onError(String error);
    }
}
