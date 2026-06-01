package com.pro.screenrecorder;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordingsActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private TextView     tvEmpty;
    private List<File>   files   = new ArrayList<>();
    private VideoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recordings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Recordings");
        }

        recycler = findViewById(R.id.recycler);
        tvEmpty  = findViewById(R.id.tv_empty);

        adapter  = new VideoAdapter(files,
            this::playVideo,
            this::confirmDelete);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        loadFiles();
    }

    private void loadFiles() {
        files.clear();

        File dir = new File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES),
            "ScreenRecorderPro");

        if (dir.exists() && dir.isDirectory()) {
            File[] arr = dir.listFiles(
                f -> f.isFile() && f.getName().endsWith(".mp4"));
            if (arr != null && arr.length > 0) {
                Arrays.sort(arr, (a, b) ->
                    Long.compare(b.lastModified(), a.lastModified()));
                for (File f : arr) files.add(f);
            }
        }

        adapter.notifyDataSetChanged();

        boolean empty = files.isEmpty();
        tvEmpty .setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE   : View.VISIBLE);
    }

    private void playVideo(File f) {
        try {
            Uri uri = Uri.fromFile(f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "video/mp4");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Play with..."));
        } catch (Exception e) {
            Toast.makeText(this,
                "Install a video player app", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(File f) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Delete")
            .setMessage("Delete \"" + f.getName() + "\"?")
            .setPositiveButton("Delete", (d, w) -> {
                f.delete();
                loadFiles();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ── Adapter ──────────────────────────────────────────────────
    static class VideoAdapter
            extends RecyclerView.Adapter<VideoAdapter.VH> {

        interface Action { void run(File f); }

        private final List<File> data;
        private final Action     onPlay;
        private final Action     onDelete;

        VideoAdapter(List<File> data, Action onPlay, Action onDelete) {
            this.data     = data;
            this.onPlay   = onPlay;
            this.onDelete = onDelete;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recording, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            File f = data.get(pos);

            h.tvName.setText(f.getName()
                .replace("REC_", "")
                .replace(".mp4", "")
                .replace("_", " "));

            long   kb   = f.length() / 1024;
            String size = kb > 1024
                ? String.format(Locale.getDefault(), "%.1f MB", kb / 1024f)
                : kb + " KB";
            String date = new SimpleDateFormat(
                "MMM dd, yyyy  HH:mm",
                Locale.getDefault()).format(new Date(f.lastModified()));

            h.tvMeta.setText(date + "   •   " + size);

            h.itemView.setOnClickListener(v -> onPlay.run(f));
            h.btnDelete.setOnClickListener(v -> onDelete.run(f));
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvMeta, btnDelete;
            VH(View v) {
                super(v);
                tvName    = v.findViewById(R.id.tv_name);
                tvMeta    = v.findViewById(R.id.tv_meta);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
