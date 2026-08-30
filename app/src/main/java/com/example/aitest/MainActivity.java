package com.example.aitest;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Intentional bug, left in on purpose: this method is never defined
        // anywhere in the project. javac will fail with "cannot find
        // symbol". This doesn't match any of the deterministic repair rules
        // (JVM target mismatch, AndroidX/Jetifier, repositories mode
        // conflicts, XML comments, private drawables, missing SDK path) --
        // so the build should fail, the rule-based repair should find
        // nothing to fix, and it should fall through to the AI repair
        // fallback for a real end-to-end test.
        initializeThingThatDoesNotExist();
    }
}
