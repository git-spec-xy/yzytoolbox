package com.example.yzytoolbox;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.yzytoolbox.module.ModuleManager;
import com.example.yzytoolbox.module.ModuleManifest;
import com.example.yzytoolbox.ui.ModuleAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements ModuleAdapter.OnModuleActionListener {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private ModuleManager moduleManager;
    private ModuleAdapter moduleAdapter;
    private ModuleManifest currentManifest;
    private Set<String> installedModules = new HashSet<>();
    
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvModules;
    private Spinner spinnerCategory;
    private LinearLayout webViewContainer;
    private WebView webView;
    private FloatingActionButton fabBack;
    
    private String currentCategory = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        checkPermissions();
        
        moduleManager = new ModuleManager(this);
        moduleManager.setOnDownloadListener(new ModuleManager.OnDownloadListener() {
            @Override
            public void onDownloadStart(String moduleId) {
                runOnUiThread(() -> {
                    moduleAdapter.setDownloading(moduleId, true);
                    Toast.makeText(MainActivity.this, "开始下载: " + moduleId, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onDownloadProgress(String moduleId, int progress) {
                runOnUiThread(() -> moduleAdapter.setDownloadProgress(moduleId, progress));
            }
            
            @Override
            public void onDownloadComplete(String moduleId, boolean success, String error) {
                runOnUiThread(() -> {
                    if (success) {
                        installedModules.add(moduleId);
                        moduleAdapter.updateModuleStatus(moduleId, true);
                        Toast.makeText(MainActivity.this, "下载完成", Toast.LENGTH_SHORT).show();
                    } else {
                        moduleAdapter.setDownloading(moduleId, false);
                        Toast.makeText(MainActivity.this, "下载失败: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        
        loadModules();
    }
    
    private void initViews() {
        swipeRefresh = findViewById(R.id.swipe_refresh);
        rvModules = findViewById(R.id.rv_modules);
        spinnerCategory = findViewById(R.id.spinner_category);
        webViewContainer = findViewById(R.id.webview_container);
        webView = findViewById(R.id.web_view);
        fabBack = findViewById(R.id.fab_back);
        
        swipeRefresh.setOnRefreshListener(this::loadModules);
        
        rvModules.setLayoutManager(new GridLayoutManager(this, 2));
        moduleAdapter = new ModuleAdapter();
        moduleAdapter.setOnModuleActionListener(this);
        rvModules.setAdapter(moduleAdapter);
        
        List<String> categories = new ArrayList<>();
        categories.add("全部");
        categories.add("生活");
        categories.add("工作");
        categories.add("开发");
        categories.add("娱乐");
        
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);
        
        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: currentCategory = "all"; break;
                    case 1: currentCategory = "life"; break;
                    case 2: currentCategory = "work"; break;
                    case 3: currentCategory = "developer"; break;
                    case 4: currentCategory = "entertainment"; break;
                }
                filterModules();
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        setupWebView();
        
        fabBack.setOnClickListener(v -> showModuleList());
    }
    
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
    }
    
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) 
                    != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.INTERNET, 
                                     Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "需要权限才能正常使用", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
    
    private void loadModules() {
        swipeRefresh.setRefreshing(true);
        
        moduleManager.fetchManifest(new ModuleManager.OnManifestListener() {
            @Override
            public void onManifestLoaded(ModuleManifest manifest) {
                runOnUiThread(() -> {
                    currentManifest = manifest;
                    checkInstalledModules();
                    filterModules();
                    swipeRefresh.setRefreshing(false);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to load manifest: " + error);
                    Toast.makeText(MainActivity.this, "加载失败: " + error, Toast.LENGTH_LONG).show();
                    swipeRefresh.setRefreshing(false);
                });
            }
        });
    }
    
    private void checkInstalledModules() {
        installedModules.clear();
        if (currentManifest != null && currentManifest.getModules() != null) {
            for (String moduleId : currentManifest.getModules().keySet()) {
                if (moduleManager.isModuleInstalled(moduleId)) {
                    installedModules.add(moduleId);
                }
            }
        }
    }
    
    private void filterModules() {
        if (currentManifest == null || currentManifest.getModules() == null) return;
        
        Map<String, ModuleManifest.ModuleInfo> filtered;
        if ("all".equals(currentCategory)) {
            filtered = currentManifest.getModules();
        } else {
            filtered = new java.util.HashMap<>();
            for (Map.Entry<String, ModuleManifest.ModuleInfo> entry : currentManifest.getModules().entrySet()) {
                if (currentCategory.equals(entry.getValue().getCategory())) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
        }
        
        moduleAdapter.setData(filtered, installedModules);
    }
    
    @Override
    public void onDownload(ModuleAdapter.ModuleItem item) {
        if (currentManifest == null) return;
        
        ModuleManifest.ModuleInfo info = currentManifest.getModules().get(item.id);
        if (info != null) {
            String version = info.getLatestVersion();
            ModuleManifest.VersionInfo versionInfo = info.getLatestVersionInfo();
            if (versionInfo != null) {
                moduleManager.downloadModule(item.id, version, versionInfo.getUrl());
            }
        }
    }
    
    @Override
    public void onLaunch(ModuleAdapter.ModuleItem item) {
        showWebView(item.id);
    }
    
    @Override
    public void onDelete(ModuleAdapter.ModuleItem item) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除 " + item.name + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    moduleManager.deleteModule(item.id);
                    installedModules.remove(item.id);
                    moduleAdapter.updateModuleStatus(item.id, false);
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    private void showWebView(String moduleId) {
        rvModules.setVisibility(View.GONE);
        spinnerCategory.setVisibility(View.GONE);
        webViewContainer.setVisibility(View.VISIBLE);
        fabBack.setVisibility(View.VISIBLE);
        
        moduleManager.launchModule(moduleId, webView);
    }
    
    private void showModuleList() {
        webViewContainer.setVisibility(View.GONE);
        rvModules.setVisibility(View.VISIBLE);
        spinnerCategory.setVisibility(View.VISIBLE);
        fabBack.setVisibility(View.GONE);
        
        webView.loadUrl("about:blank");
    }
    
    @Override
    public void onBackPressed() {
        if (webViewContainer.getVisibility() == View.VISIBLE) {
            showModuleList();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (moduleManager != null) {
            moduleManager.cleanup();
        }
    }
}