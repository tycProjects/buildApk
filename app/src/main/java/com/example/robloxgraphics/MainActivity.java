package com.example.robloxgraphics;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    static final int REQ = 100;
    String robloxPkg;
    TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        detectRoblox();
        updatePermission();
    }

    TextView tv(String s, int size) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(Color.WHITE);
        v.setPadding(24,18,24,18); return v;
    }

    void buildUi() {
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24,24,24,24);
        box.setBackgroundColor(Color.rgb(18,18,22));

        TextView title=tv("🎮 ROBLOX GRAPHICS TOOL",24); box.addView(title);
        status=tv("Đang kiểm tra...",15); box.addView(status);

        Button auth=new Button(this); auth.setText("🔐 Ủy quyền Shizuku"); box.addView(auth);
        auth.setOnClickListener(v -> {
            if (!Shizuku.pingBinder()) { toast("Hãy mở Shizuku trước."); return; }
            if (Build.VERSION.SDK_INT >= 23 && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED)
                toast("Đã được cấp quyền Shizuku.");
            else Shizuku.requestPermission(REQ);
        });

        Button low=new Button(this); low.setText("⚡ Giảm đồ họa / tiết kiệm GPU"); box.addView(low);
        low.setOnClickListener(v -> runGameMode("battery"));

        Button perf=new Button(this); perf.setText("🚀 Ưu tiên hiệu năng / FPS"); box.addView(perf);
        perf.setOnClickListener(v -> runGameMode("performance"));

        Button normal=new Button(this); normal.setText("🟢 Khôi phục mặc định"); box.addView(normal);
        normal.setOnClickListener(v -> runGameMode("standard"));

        Button open=new Button(this); open.setText("🎮 Mở Roblox"); box.addView(open);
        open.setOnClickListener(v -> openRoblox());

        TextView note=tv("Bản v4: không còn phụ thuộc duy nhất vào 'battery'. App sẽ thử lệnh Game Mode tương thích và báo kết quả.",13);
        note.setTextColor(Color.LTGRAY); box.addView(note);

        setContentView(box);
    }

    void updatePermission() {
        if (!Shizuku.pingBinder()) { status.setText("🔴 Shizuku chưa chạy"); return; }
        if (Build.VERSION.SDK_INT >= 23 && Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
            status.setText("🟡 Shizuku đang chạy nhưng app chưa được cấp quyền");
        } else status.setText("🟢 Shizuku đã sẵn sàng • Roblox: "+(robloxPkg==null?"không tìm thấy":robloxPkg));
    }

    void detectRoblox() {
        PackageManager pm=getPackageManager();
        if (isInstalled(pm,"com.roblox.client.vnggames")) robloxPkg="com.roblox.client.vnggames";
        else if (isInstalled(pm,"com.roblox.client")) robloxPkg="com.roblox.client";
    }

    boolean isInstalled(PackageManager pm,String p) {
        try { pm.getPackageInfo(p,0); return true; } catch(Exception e){ return false; }
    }

    void runGameMode(String mode) {
        detectRoblox();
        if (robloxPkg==null) { toast("Không tìm thấy Roblox."); return; }
        if (!hasShizuku()) return;

        new Thread(() -> {
            String[] cmds = {
                "cmd game mode "+mode+" "+robloxPkg,
                "cmd game mode "+mode+" --user 0 "+robloxPkg
            };
            String out="";
            for(String c:cmds) {
                String r=exec(c);
                out += "\\n$ "+c+"\\n"+r;
                if(!r.toLowerCase().contains("error") && !r.toLowerCase().contains("unknown") && !r.toLowerCase().contains("invalid")) break;
            }
            final String result=out;
            runOnUiThread(() -> {
                status.setText("Kết quả Game Mode:"+result);
                Toast.makeText(this, result.length()>220?result.substring(0,220):result, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    boolean hasShizuku() {
        if(!Shizuku.pingBinder()) { toast("Mở Shizuku trước."); return false; }
        if(Build.VERSION.SDK_INT>=23 && Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQ); return false;
        }
        return true;
    }

    String exec(String command) {
        try {
            Method m=Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            Object p=m.invoke(null, new Object[]{new String[]{"sh","-c",command}, null, null});
            java.lang.reflect.Method wait=p.getClass().getMethod("waitFor");
            java.lang.reflect.Method getOut=p.getClass().getMethod("getInputStream");
            InputStream is=(InputStream)getOut.invoke(p);
            byte[] data=is.readAllBytes();
            wait.invoke(p);
            return new String(data).trim();
        } catch(Throwable e) { return "ERROR: "+e.getClass().getSimpleName()+" - "+e.getMessage(); }
    }

    void openRoblox() {
        detectRoblox();
        if(robloxPkg==null){toast("Không tìm thấy Roblox.");return;}
        Intent i=getPackageManager().getLaunchIntentForPackage(robloxPkg);
        if(i!=null) startActivity(i); else toast("Không mở được Roblox.");
    }

    void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }

    @Override protected void onResume() { super.onResume(); updatePermission(); detectRoblox(); }
}
