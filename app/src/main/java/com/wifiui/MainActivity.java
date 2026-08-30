package com.wifiui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello from Java!");
        tv.setTextSize(20);
        tv.setPadding(40, 40, 40, 40);
        setContentView(tv);
    }
}
