package com.ryan.autotyper;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Kho file TXT riêng của ứng dụng. Không phụ thuộc quyền đọc bộ nhớ ngoài. */
public final class TextFileStore {
    private static final String PREFS = "text_file_store";
    private static final String SELECTED_FILE = "selected_file";
    private final Context context;
    private final File directory;
    private final SharedPreferences preferences;

    public TextFileStore(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(this.context.getFilesDir(), "txt_library");
        if (!directory.exists()) directory.mkdirs();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized String importTxt(InputStream input, String originalName) throws Exception {
        String safeName = sanitizeName(originalName);
        File target = uniqueFile(safeName);
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        preferences.edit().putString(SELECTED_FILE, target.getName()).apply();
        return target.getName();
    }

    public synchronized List<File> listFiles() {
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return new ArrayList<>(Arrays.asList(files));
    }

    public synchronized String getSelectedName() {
        return preferences.getString(SELECTED_FILE, "");
    }

    public synchronized void select(String fileName) {
        File file = new File(directory, sanitizeName(fileName));
        if (file.isFile()) preferences.edit().putString(SELECTED_FILE, file.getName()).apply();
    }

    public synchronized boolean delete(String fileName) {
        File file = new File(directory, sanitizeName(fileName));
        boolean deleted = file.isFile() && file.delete();
        if (file.getName().equals(getSelectedName())) preferences.edit().remove(SELECTED_FILE).apply();
        return deleted;
    }

    public synchronized List<String> readLines(String fileName) throws Exception {
        File file = new File(directory, sanitizeName(fileName));
        if (!file.isFile()) throw new IllegalArgumentException("Không tìm thấy file TXT");
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8), 64 * 1024)) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    private File uniqueFile(String name) {
        File file = new File(directory, name);
        if (!file.exists()) return file;
        String base = name.substring(0, name.length() - 4);
        int index = 2;
        do { file = new File(directory, base + " (" + index++ + ").txt"); }
        while (file.exists());
        return file;
    }

    private String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) name = "untitled.txt";
        name = name.replaceAll("[^a-zA-Z0-9._() -]", "_");
        if (!name.toLowerCase().endsWith(".txt")) name += ".txt";
        return name;
    }
}
