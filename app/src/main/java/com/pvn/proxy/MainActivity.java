package com.pvn.proxy;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int VPN_REQUEST_CODE = 100;
    private Button btnToggle;
    private TextView tvStatus;
    private Spinner spinnerPort;
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = findViewById(R.id.btnToggle);
        tvStatus = findViewById(R.id.tvStatus);
        spinnerPort = findViewById(R.id.spinnerPort);

        Integer[] ports = {80, 443, 1080, 3128, 8080, 3389, 22, 51820, 1194};
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ports);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPort.setAdapter(adapter);
        spinnerPort.setSelection(4); // Mặc định chọn port 8080

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRunning) {
                    stopProxy();
                } else {
                    startProxy();
                }
            }
        });
    }

    private void startProxy() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            int port = (int) spinnerPort.getSelectedItem();
            Intent serviceIntent = new Intent(this, ProxyService.class);
            serviceIntent.putExtra("proxy_port", port);
            serviceIntent.putExtra("proxy_ip", "121.14.28.215");
            startService(serviceIntent);
            isRunning = true;
            updateUI();
            Toast.makeText(this, "Proxy đã bật!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Cần cấp quyền VPN để dùng proxy", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopProxy() {
        Intent serviceIntent = new Intent(this, ProxyService.class);
        stopService(serviceIntent);
        isRunning = false;
        updateUI();
        Toast.makeText(this, "Proxy đã tắt!", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        if (isRunning) {
            tvStatus.setText("Trạng thái: ĐANG BẬT");
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            btnToggle.setText("⏹ TẮT PROXY");
            btnToggle.setBackgroundColor(getColor(android.R.color.holo_red_dark));
        } else {
            tvStatus.setText("Trạng thái: TẮT");
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            btnToggle.setText("▶ BẬT PROXY");
            btnToggle.setBackgroundColor(getColor(android.R.color.holo_green_dark));
        }
    }
}
