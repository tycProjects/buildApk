package com.example.robloxgraphicstool;
import android.app.*; import android.os.*; import android.content.*; import android.content.pm.PackageManager; import android.graphics.Color; import android.view.*; import android.widget.*; import java.io.*; import java.lang.reflect.Method; import rikka.shizuku.*;
public class MainActivity extends Activity {
 static final int REQ=100; TextView status; Button auth; String pkg="com.roblox.client.vnggames";
 final Shizuku.OnRequestPermissionResultListener listener=(c,g)->{if(c==REQ)update();};
 public void onCreate(Bundle b){super.onCreate(b); LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(30,30,30,20);
 TextView t=new TextView(this);t.setText("🎮 Roblox Graphics Tool");t.setTextSize(25);t.setTextColor(Color.BLACK);x.addView(t);
 status=new TextView(this);status.setTextSize(16);x.addView(status);
 auth=new Button(this);auth.setText("🔐 Ủy quyền Shizuku");auth.setOnClickListener(v->request());x.addView(auth);
 add(x,"⚡ Giảm đồ họa / tiết kiệm GPU",v->mode("battery"));add(x,"🚀 Ưu tiên hiệu năng / FPS",v->mode("performance"));add(x,"🟢 Khôi phục mặc định",v->mode("standard"));add(x,"🎮 Mở Roblox",v->open());
 TextView n=new TextView(this);n.setText("\nDùng Android Game Mode; hiệu quả tùy máy/OEM.");x.addView(n);setContentView(x);Shizuku.addRequestPermissionResultListener(listener);update();}
 void add(LinearLayout x,String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);x.addView(b);}
 boolean ok(){try{return !Shizuku.isPreV11()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Exception e){return false;}}
 void update(){try{if(!Shizuku.pingBinder()){status.setText("🔴 Shizuku chưa chạy");auth.setEnabled(true);}else if(ok()){status.setText("🟢 Shizuku: ĐÃ ỦY QUYỀN\n📦 "+pkg);auth.setText("✅ Đã ủy quyền");auth.setEnabled(false);}else{status.setText("🟡 Shizuku đang chạy nhưng CHƯA ỦY QUYỀN");auth.setEnabled(true);}}catch(Exception e){status.setText("🔴 Không kết nối Shizuku");}}
 void request(){try{if(!Shizuku.pingBinder()){toast("Mở Shizuku và khởi động dịch vụ.");return;}if(!ok())Shizuku.requestPermission(REQ);else update();}catch(Exception e){toast("Không thể yêu cầu quyền.");}}
 void detect(){PackageManager p=getPackageManager();if(p.getLaunchIntentForPackage("com.roblox.client.vnggames")!=null)pkg="com.roblox.client.vnggames";else if(p.getLaunchIntentForPackage("com.roblox.client")!=null)pkg="com.roblox.client";}
 void mode(String m){if(!ok()){request();return;}detect();status.setText("⏳ Đang áp dụng "+m+"...");new Thread(()->{String r=run(new String[]{"cmd","game","mode",m,pkg});runOnUiThread(()->{status.setText(r.startsWith("OK:")?"🟢 Đã áp dụng "+m+" cho "+pkg+"\n"+r.substring(3):"❌ "+r);toast(r.startsWith("OK:")?"Đã áp dụng "+m:"Thiết bị có thể không hỗ trợ Game Mode");});}).start();}
 String run(String[] c){try{Method m=Shizuku.class.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);m.setAccessible(true);ShizukuRemoteProcess p=(ShizukuRemoteProcess)m.invoke(null,c,null,null);BufferedReader o=new BufferedReader(new InputStreamReader(p.getInputStream())),e=new BufferedReader(new InputStreamReader(p.getErrorStream()));StringBuilder a=new StringBuilder(),z=new StringBuilder();String s;while((s=o.readLine())!=null)a.append(s).append('\n');while((s=e.readLine())!=null)z.append(s).append('\n');int q=p.waitFor();p.destroy();return q==0?"OK:"+a.toString().trim():"exit="+q+" "+z.toString().trim();}catch(Exception e){return "Lỗi: "+e.getClass().getSimpleName()+": "+e.getMessage();}}
 void open(){detect();Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null)startActivity(i);else toast("Không tìm thấy Roblox.");}
 void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
 protected void onDestroy(){Shizuku.removeRequestPermissionResultListener(listener);super.onDestroy();}
}