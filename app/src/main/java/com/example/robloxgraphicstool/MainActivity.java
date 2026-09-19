package com.example.robloxgraphicstool;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    LinearLayout box;
    TextView status;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(40,50,40,40);
        TextView title=new TextView(this); title.setText("🎮 Roblox Graphics Tool"); title.setTextSize(26); title.setTextColor(Color.BLACK);
        box.addView(title);
        status=new TextView(this); status.setText("\nShizuku: kiểm tra khi chạy."); status.setTextSize(16); box.addView(status);

        add("⚡ Giảm đồ họa", "LOW");
        add("🟡 Đồ họa trung bình", "MEDIUM");
        add("🟢 Khôi phục", "RESET");
        add("🚀 Mở Roblox", "OPEN");

        setContentView(box);
    }

    void add(String text,String action){
        Button btn=new Button(this); btn.setText(text); btn.setOnClickListener(v -> runAction(action));
        box.addView(btn,new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    void runAction(String action){
        if(action.equals("OPEN")){
            try { startActivity(getPackageManager().getLaunchIntentForPackage("com.roblox.client")); }
            catch(Exception e){ toast("Không tìm thấy Roblox."); }
            return;
        }

        // Đây là khung an toàn để nối Shizuku.
        // Không tự ý sửa dữ liệu/game files của Roblox.
        status.setText("\nĐã chọn: "+action+"\nCần thêm implementation Shizuku phù hợp với thiết bị để áp dụng setting.");
        toast("Đã chọn "+action);
    }

    void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
}
