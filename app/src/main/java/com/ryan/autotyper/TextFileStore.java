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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Kho file TXT riêng của ứng dụng. Không phụ thuộc quyền đọc bộ nhớ ngoài. */
public final class TextFileStore {
    private static final String PREFS = "text_file_store";
    private static final String SELECTED_FILE = "selected_file";
    private static final String SELECTED_FILES = "selected_files";
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
        if (input == null) throw new IllegalArgumentException("Không thể đọc file TXT");
        String safeName = sanitizeName(originalName);
        File target = uniqueFile(safeName);
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        Set<String> selected = selectedSet();
        selected.add(target.getName());
        saveSelected(selected, target.getName());
        return target.getName();
    }

    public synchronized List<File> listFiles() {
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".txt"));
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName());
            }
        });
        return new ArrayList<>(Arrays.asList(files));
    }

    /** Tên đầu tiên được chọn, giữ tương thích với code cũ. */
    public synchronized String getSelectedName() {
        List<String> names = getSelectedNames();
        return names.isEmpty() ? "" : names.get(0);
    }

    public synchronized List<String> getSelectedNames() {
        Set<String> selected = selectedSet();
        List<String> names = new ArrayList<>();
        for (String name : selected) {
            File file = new File(directory, sanitizeName(name));
            if (file.isFile()) names.add(file.getName());
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public synchronized boolean isSelected(String fileName) {
        return getSelectedNames().contains(sanitizeName(fileName));
    }

    public synchronized void setSelection(String fileName, boolean selected) {
        String safeName = sanitizeName(fileName);
        File file = new File(directory, safeName);
        if (!file.isFile()) return;
        Set<String> names = selectedSet();
        if (selected) names.add(file.getName());
        else names.remove(file.getName());
        saveSelected(names, names.isEmpty() ? "" : names.iterator().next());
    }

    public synchronized void toggleSelection(String fileName) {
        setSelection(fileName, !isSelected(fileName));
    }

    /** API cũ: chuyển sang chọn duy nhất một file. */
    public synchronized void select(String fileName) {
        String safeName = sanitizeName(fileName);
        File file = new File(directory, safeName);
        if (!file.isFile()) return;
        Set<String> names = new HashSet<>();
        names.add(file.getName());
        saveSelected(names, file.getName());
    }

    public synchronized boolean delete(String fileName) {
        File file = new File(directory, sanitizeName(fileName));
        boolean deleted = file.isFile() && file.delete();
        Set<String> names = selectedSet();
        names.remove(file.getName());
        saveSelected(names, names.isEmpty() ? "" : names.iterator().next());
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

    private Set<String> selectedSet() {
        Set<String> selected = new HashSet<>(preferences.getStringSet(SELECTED_FILES, new HashSet<>()));
        // Di chuyển lựa chọn cũ sang danh sách nhiều file khi người dùng nâng cấp.
        String legacy = preferences.getString(SELECTED_FILE, "");
        if (selected.isEmpty() && !legacy.isEmpty() && new File(directory, sanitizeName(legacy)).isFile()) {
            selected.add(sanitizeName(legacy));
            saveSelected(selected, sanitizeName(legacy));
        }
        Iterator<String> iterator = selected.iterator();
        while (iterator.hasNext()) {
            String name = iterator.next();
            if (!new File(directory, sanitizeName(name)).isFile()) iterator.remove();
        }
        return selected;
    }

    private void saveSelected(Set<String> names, String legacyName) {
        preferences.edit()
                .putStringSet(SELECTED_FILES, new HashSet<>(names))
                .putString(SELECTED_FILE, legacyName == null ? "" : legacyName)
                .apply();
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
        name = name.trim().replaceAll("[\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (name.isEmpty()) name = "untitled.txt";
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) name += ".txt";
        return name;
    }
}
