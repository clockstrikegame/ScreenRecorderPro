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
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordingsActivity extends AppCompatActivity {

    private RecyclerView  recycler;
    private TextView      tvEmpty;
    private List<File>    files = new ArrayList<>();
    private VideoAdapter  adapter;

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

        adapter = new VideoAdapter(files,
            file -> playVideo(file),
            file -> confirmDelete(file));

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        loadFiles();
    }

    private void loadFiles() {
        File dir = new File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES),
            "ScreenRecorderPro");

        files.clear();
        if (dir.exists()) {
            File[] arr = dir.listFiles(f ->
                f.isFile() && f.getName().endsWith(".mp4"));
            if (arr != null) {
                Arrays.sort(arr, (a, b) ->
                    Long.compare(b.lastModified(), a.lastModified()));
                files.addAll(Arrays.asList(arr));
            }
        }

        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(files.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void playVideo(File f) {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".provider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "video/mp4");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No video player found", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(File f) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Delete Recording")
            .setMessage("Delete \"" + f.getName() + "\"?")
            .setPositiveButton("Delete", (d, w) -> {
                if (f.delete()) loadFiles();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    // ── Adapter ────────────────────────────────────────────────────────────
    static class VideoAdapter
            extends RecyclerView.Adapter<VideoAdapter.VH> {

        interface OnClick { void on(File f); }

        private final List<File> data;
        private final OnClick    play, delete;

        VideoAdapter(List<File> d, OnClick play, OnClick delete) {
            this.data   = d;
            this.play   = play;
            this.delete = delete;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_recording, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            File f = data.get(pos);
            h.tvName.setText(f.getName());

            long   kb  = f.length() / 1024;
            String sz  = kb > 1024
                ? String.format(Locale.getDefault(), "%.1f MB", kb / 1024f)
                : kb + " KB";
            String date = new SimpleDateFormat("MMM dd, HH:mm",
                Locale.getDefault()).format(new Date(f.lastModified()));
            h.tvMeta.setText(date + " · " + sz);

            h.itemView.setOnClickListener(v -> play.on(f));
            h.btnDelete.setOnClickListener(v -> delete.on(f));
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvMeta;
            View     btnDelete;
            VH(View v) {
                super(v);
                tvName    = v.findViewById(R.id.tv_name);
                tvMeta    = v.findViewById(R.id.tv_meta);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
