package com.example.yzytoolbox.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.yzytoolbox.R;
import com.example.yzytoolbox.module.ModuleManifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModuleAdapter extends RecyclerView.Adapter<ModuleAdapter.ViewHolder> {
    
    private List<ModuleItem> moduleItems = new ArrayList<>();
    private OnModuleActionListener actionListener;
    
    public interface OnModuleActionListener {
        void onDownload(ModuleItem item);
        void onLaunch(ModuleItem item);
        void onDelete(ModuleItem item);
    }
    
    public static class ModuleItem {
        public String id;
        public String name;
        public String description;
        public String category;
        public String type;
        public boolean installed;
        public boolean downloading;
        public int downloadProgress;
        public ModuleManifest.VersionInfo versionInfo;
        
        public ModuleItem(String id, ModuleManifest.ModuleInfo info, boolean installed) {
            this.id = id;
            this.name = info.getName();
            this.description = info.getDescription();
            this.category = info.getCategory();
            this.type = info.getType();
            this.installed = installed;
            this.versionInfo = info.getLatestVersionInfo();
        }
    }
    
    public void setOnModuleActionListener(OnModuleActionListener listener) {
        this.actionListener = listener;
    }
    
    public void setData(Map<String, ModuleManifest.ModuleInfo> modules, java.util.Set<String> installedModules) {
        moduleItems.clear();
        if (modules != null) {
            for (Map.Entry<String, ModuleManifest.ModuleInfo> entry : modules.entrySet()) {
                boolean installed = installedModules.contains(entry.getKey());
                moduleItems.add(new ModuleItem(entry.getKey(), entry.getValue(), installed));
            }
        }
        notifyDataSetChanged();
    }
    
    public void updateModuleStatus(String moduleId, boolean installed) {
        for (ModuleItem item : moduleItems) {
            if (item.id.equals(moduleId)) {
                item.installed = installed;
                item.downloading = false;
                item.downloadProgress = 0;
                notifyItemChanged(moduleItems.indexOf(item));
                break;
            }
        }
    }
    
    public void setDownloading(String moduleId, boolean downloading) {
        for (ModuleItem item : moduleItems) {
            if (item.id.equals(moduleId)) {
                item.downloading = downloading;
                notifyItemChanged(moduleItems.indexOf(item));
                break;
            }
        }
    }
    
    public void setDownloadProgress(String moduleId, int progress) {
        for (ModuleItem item : moduleItems) {
            if (item.id.equals(moduleId)) {
                item.downloadProgress = progress;
                notifyItemChanged(moduleItems.indexOf(item));
                break;
            }
        }
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_module, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModuleItem item = moduleItems.get(position);
        holder.bind(item);
    }
    
    @Override
    public int getItemCount() {
        return moduleItems.size();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivIcon;
        private final TextView tvName;
        private final TextView tvDescription;
        private final TextView tvSize;
        private final Button btnAction;
        private final Button btnDelete;
        private final ProgressBar progressBar;
        
        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvName = itemView.findViewById(R.id.tv_name);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvSize = itemView.findViewById(R.id.tv_size);
            btnAction = itemView.findViewById(R.id.btn_action);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            progressBar = itemView.findViewById(R.id.progress_bar);
        }
        
        void bind(ModuleItem item) {
            tvName.setText(item.name);
            tvDescription.setText(item.description != null ? item.description : "");
            
            if (item.versionInfo != null) {
                tvSize.setText(item.versionInfo.getFormattedSize());
            } else {
                tvSize.setText("");
            }
            
            ivIcon.setImageResource(getIconResource(item.category));
            
            if (item.downloading) {
                btnAction.setVisibility(View.GONE);
                btnDelete.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(item.downloadProgress);
            } else if (item.installed) {
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText("打开");
                btnDelete.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                
                btnAction.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onLaunch(item);
                    }
                });
                
                btnDelete.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onDelete(item);
                    }
                });
            } else {
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText("下载");
                btnDelete.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                
                btnAction.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onDownload(item);
                    }
                });
            }
        }
        
        private int getIconResource(String category) {
            if (category == null) return R.drawable.ic_tool;
            switch (category) {
                case "life":
                    return R.drawable.ic_life;
                case "work":
                    return R.drawable.ic_work;
                case "developer":
                    return R.drawable.ic_developer;
                case "entertainment":
                    return R.drawable.ic_entertainment;
                default:
                    return R.drawable.ic_tool;
            }
        }
    }
}
