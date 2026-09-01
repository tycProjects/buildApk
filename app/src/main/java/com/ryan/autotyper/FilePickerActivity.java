package com.ryan.autotyper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class FilePickerActivity extends Activity {
    private static final int REQUEST_TXT = 4101;
    public static final String ACTION_FILE_LIBRARY_CHANGED =
            "com.ryan.autotyper.ACTION_FILE_LIBRARY_CHANGED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openPicker();
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        startActivityForResult(intent, REQUEST_TXT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_TXT || resultCode != RESULT_OK || data == null) {
            finish();
            return;
        }
        Uri uri = data.getData();
        if (uri == null) { finish(); return; }
        try {
            String name = FileNameResolver.getDisplayName(this, uri);
            TextFileStore store = new TextFileStore(this);
            try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Không mở được file");
                store.importTxt(input, name);
            }
            sendBroadcast(new Intent(ACTION_FILE_LIBRARY_CHANGED).setPackage(getPackageName()));
        } catch (Exception error) {
            // Lỗi được trả về qua Activity result/state; không hiển thị popup.
        }
        finish();
    }
}
