package com.leon.auravnclone;

import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.net.Uri;import android.provider.Settings;import android.view.*;import android.widget.*;import java.io.*;import java.util.zip.*;

public class MainActivity extends Activity {
    LinearLayout root,body; TextView logView; final String KEY="Leon"; final int PICK=7001;
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);} TextView tv(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setPadding(dp(14),dp(10),dp(14),dp(10));return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);showLogin();}
    void base(){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(18),dp(14),dp(14));root.setBackgroundColor(Color.rgb(7,9,13));setContentView(root);}
    void showLogin(){base(); Space sp=new Space(this);root.addView(sp,new LinearLayout.LayoutParams(1,dp(100)));TextView title=tv("LEON\nAURAVN EDITION",28);title.setTextColor(Color.rgb(255,107,61));root.addView(title);root.addView(tv("Trình quản lý file • Log • Công cụ",14));EditText key=new EditText(this);key.setHint("Nhập key");key.setTextColor(Color.WHITE);key.setHintTextColor(Color.GRAY);root.addView(key);Button go=btn("ĐĂNG NHẬP");root.addView(go);go.setOnClickListener(v->{if(KEY.equals(key.getText().toString().trim()))showMain();else Toast.makeText(this,"Key không đúng",Toast.LENGTH_SHORT).show();});}
    void showMain(){base();root.addView(tv("LEON  /  CONTROL PANEL",24));root.addView(tv("V4.2 • Ready",13));ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
      Button files=btn("📁  QUẢN LÝ FILE"); body.addView(files);files.setOnClickListener(v->pick());
      Button zip=btn("📦  XEM ZIP / AA.ZIP");body.addView(zip);zip.setOnClickListener(v->pick());
      Button launch=btn("▶  MỞ FREE FIRE BÌNH THƯỜNG");body.addView(launch);launch.setOnClickListener(v->launchFF());
      Button log=btn("🧾  LOG SONG SONG");body.addView(log);log.setOnClickListener(v->showLog());
      Button settings=btn("⚙  CẤU HÌNH");body.addView(settings);settings.setOnClickListener(v->showConfig());
      TextView status=tv("\nLEON • Local mode\nKhông có máy chủ đăng nhập",13);status.setTextColor(Color.LTGRAY);body.addView(status);
    }
    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r!=PICK||c!=RESULT_OK||d==null)return;Uri u=d.getData();append("FILE: "+u);try{if(u.toString().toLowerCase().endsWith(".zip")) listZip(u);else append("Đã chọn file: "+u.getLastPathSegment());}catch(Exception e){append("ERROR: "+e.getMessage());}}
    void listZip(Uri u)throws Exception{ZipInputStream z=new ZipInputStream(getContentResolver().openInputStream(u));ZipEntry e;int n=0;while((e=z.getNextEntry())!=null&&n<100){append("ZIP > "+e.getName());n++;}z.close();}
    void showLog(){base();root.addView(tv("LOG SONG SONG",24));logView=tv("",12);root.addView(logView,new LinearLayout.LayoutParams(-1,0,1));Button clear=btn("XÓA LOG");root.addView(clear);clear.setOnClickListener(v->logView.setText(""));Button back=btn("← QUAY LẠI");root.addView(back);back.setOnClickListener(v->showMain());}
    void append(String s){if(logView!=null)logView.append("\n[LEON] "+s);else Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    void showConfig(){base();root.addView(tv("CẤU HÌNH",24));Switch patch=new Switch(this);patch.setText("testCodePatch");patch.setTextColor(Color.WHITE);patch.setChecked(true);root.addView(patch);Switch guest=new Switch(this);guest.setText("resetGuest");guest.setTextColor(Color.WHITE);root.addView(guest);root.addView(tv("Các công tắc này chỉ lưu trạng thái giao diện; bản Leon không tự sửa hoặc inject vào game.",12));Button back=btn("← QUAY LẠI");root.addView(back);back.setOnClickListener(v->showMain());}
    void launchFF(){try{Intent i=getPackageManager().getLaunchIntentForPackage("com.dts.freefiremax");if(i==null)Toast.makeText(this,"Không tìm thấy Free Fire MAX",Toast.LENGTH_LONG).show();else startActivity(i);}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
}
