package com.adam.apkmaker;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private LinearLayout root, content;
    private TextView fileStatus, buildStatus, timerText;
    private String selectedFile = "";
    private boolean fastActive = false;
    private long fastUntil = 0L;
    private static final int PICK_FILE = 501;
    private static final int PICK_ICON = 502;

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView tv(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable bg(int color, int radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private Button button(String text, boolean teal) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(15); b.setTextColor(Color.rgb(244,247,248));
        b.setAllCaps(false); b.setPadding(dp(10),0,dp(10),0);
        b.setBackground(bg(teal ? Color.rgb(23,58,55) : Color.rgb(29,33,37), 18,
                teal ? Color.rgb(86,230,209) : Color.rgb(41,50,56)));
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(18),dp(16),dp(18),dp(16));
        c.setBackground(bg(Color.rgb(21,24,27),24,Color.rgb(36,41,46)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(10),0,dp(10)); c.setLayoutParams(lp); return c;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(9,11,13));
        getWindow().setNavigationBarColor(Color.rgb(9,11,13));
        buildUi();
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9,11,13));
        root.setPadding(dp(18),dp(10),dp(18),dp(8));

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = tv("ADAM APK MAKER",22,Color.rgb(244,247,248));
        brand.setTypeface(null,1); top.addView(brand,new LinearLayout.LayoutParams(0,dp(60),1));

        Button help = button("?",false);
        Button key = button("KEY",false);
        Button ai = button("AI",false);
        top.addView(key,new LinearLayout.LayoutParams(dp(58),dp(48)));
        top.addView(help,new LinearLayout.LayoutParams(dp(48),dp(48)));
        top.addView(ai,new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(top);

        TextView sub=tv("ZIP / HTML  •  Android APK Builder  •  Made by ADAM",12,Color.rgb(137,146,152));
        root.addView(sub,new LinearLayout.LayoutParams(-1,dp(28)));

        LinearLayout tabs = new LinearLayout(this); tabs.setPadding(0,dp(4),0,dp(4));
        Button build=button("Build",true), options=button("Options",false), history=button("History",false);
        tabs.addView(build,new LinearLayout.LayoutParams(0,dp(48),1));
        tabs.addView(options,new LinearLayout.LayoutParams(0,dp(48),1));
        tabs.addView(history,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(tabs);

        ScrollView scroll=new ScrollView(this);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2)); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        fileStatus=tv("No file selected",14,Color.rgb(137,146,152));
        LinearLayout upload=card();
        TextView h=tv("PROJECT SOURCE",12,Color.rgb(86,230,209)); h.setTypeface(null,1); upload.addView(h);
        Button pick=button("Select ZIP or HTML file",true); upload.addView(pick,new LinearLayout.LayoutParams(-1,dp(58)));
        upload.addView(fileStatus,new LinearLayout.LayoutParams(-1,dp(38)));
        TextView hint=tv("ZIP projects and standalone HTML files are supported.",12,Color.rgb(137,146,152));
        upload.addView(hint);
        content.addView(upload);

        LinearLayout iconCard=card();
        TextView ih=tv("APP ICON",12,Color.rgb(86,230,209)); ih.setTypeface(null,1); iconCard.addView(ih);
        Button icon=button("Add App Icon",false); iconCard.addView(icon,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView it=tv("PNG, JPG or WEBP • square image recommended",12,Color.rgb(137,146,152)); iconCard.addView(it);
        content.addView(iconCard);

        LinearLayout details=card();
        TextView dh=tv("APP DETAILS",12,Color.rgb(86,230,209)); dh.setTypeface(null,1); details.addView(dh);
        EditText name=new EditText(this); name.setHint("App name"); name.setTextColor(Color.WHITE); name.setHintTextColor(Color.rgb(100,110,116)); name.setSingleLine(); name.setBackground(bg(Color.rgb(15,18,20),18,Color.rgb(58,76,80))); name.setPadding(dp(16),0,dp(16),0);
        details.addView(name,new LinearLayout.LayoutParams(-1,dp(56)));
        EditText pkg=new EditText(this); pkg.setHint("Package name  •  com.example.app"); pkg.setTextColor(Color.WHITE); pkg.setHintTextColor(Color.rgb(100,110,116)); pkg.setSingleLine(); pkg.setBackground(bg(Color.rgb(15,18,20),18,Color.rgb(58,76,80))); pkg.setPadding(dp(16),0,dp(16),0);
        LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(-1,dp(56)); plp.setMargins(0,dp(10),0,0); details.addView(pkg,plp);
        content.addView(details);

        LinearLayout fast=card();
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView ft=tv("Fast Build",17,Color.WHITE); ft.setTypeface(null,1);
        timerText=tv("Key required",12,Color.rgb(137,146,152)); timerText.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        row.addView(ft,new LinearLayout.LayoutParams(0,dp(48),1)); row.addView(timerText,new LinearLayout.LayoutParams(dp(110),dp(48)));
        fast.addView(row);
        Button buildFast=button("⚡  Fast Build",false); fast.addView(buildFast,new LinearLayout.LayoutParams(-1,dp(54)));
        content.addView(fast);

        LinearLayout bottom=new LinearLayout(this); bottom.setPadding(0,dp(8),0,0);
        Button buildApk=button("Build APK",true); bottom.addView(buildApk,new LinearLayout.LayoutParams(-1,dp(60)));
        root.addView(bottom);

        pick.setOnClickListener(v -> chooseProject());
        icon.setOnClickListener(v -> chooseIcon());
        key.setOnClickListener(v -> showKeyDialog());
        ai.setOnClickListener(v -> showAiDialog());
        help.setOnClickListener(v -> showHelp());
        build.setOnClickListener(v -> runBuild(false));
        options.setOnClickListener(v -> showOptions());
        history.setOnClickListener(v -> showHistory());
        buildApk.setOnClickListener(v -> runBuild(false));
        buildFast.setOnClickListener(v -> runBuild(true));

        setContentView(root);
        startTimerLoop();
    }

    private void chooseProject() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","text/html","text/plain","application/octet-stream"});
        startActivityForResult(i,PICK_FILE);
    }
    private void chooseIcon() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*"); startActivityForResult(i,PICK_ICON);
    }
    @Override protected void onActivityResult(int r,int c,Intent d) {
        super.onActivityResult(r,c,d); if(c!=RESULT_OK||d==null)return;
        Uri u=d.getData(); if(u==null)return;
        if(r==PICK_FILE){ selectedFile=u.toString(); fileStatus.setText("Selected: "+u.getLastPathSegment()); fileStatus.setTextColor(Color.rgb(86,230,209)); }
        else if(r==PICK_ICON) Toast.makeText(this,"App icon selected",Toast.LENGTH_SHORT).show();
    }

    private void showKeyDialog() {
        final EditText input=new EditText(this); input.setSingleLine(); input.setHint("Enter access key");
        input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY);
        input.setInputType(1);
        LinearLayout box=new LinearLayout(this); box.setPadding(dp(24),0,dp(24),0); box.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Fast Build Key").setMessage("Enter your access key to unlock Fast Build for 5 minutes.").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Activate",null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if("ADAM31".equals(input.getText().toString().trim())){
                fastActive=true; fastUntil=System.currentTimeMillis()+TimeUnit.MINUTES.toMillis(5);
                timerText.setText("05:00 active"); dialog.dismiss();
                Toast.makeText(this,"Fast Build activated for 5 minutes",Toast.LENGTH_SHORT).show();
            } else input.setError("Invalid key");
        }));
        dialog.show();
    }

    private void showAiDialog() {
        TextView t=tv("AI Assistant\n\nI can help analyze your selected ZIP/HTML project, suggest app settings, and explain build errors.\n\nCloud AI can be connected later with your own API key.",15,Color.WHITE);
        t.setPadding(dp(24),dp(10),dp(24),dp(10));
        new AlertDialog.Builder(this).setTitle("AI Assistant").setView(t).setPositiveButton("Close",null).show();
    }
    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("How it works").setMessage("1. Select a ZIP or HTML file.\n2. Add an icon and app details.\n3. Choose Build APK or Fast Build.\n4. The builder service packages the project and returns an APK.\n\nNote: actual APK compilation requires a connected build service or a full Android build toolchain; this app does not fake an APK.").setPositiveButton("OK",null).show();
    }
    private void showOptions() {
        new AlertDialog.Builder(this).setTitle("Options").setItems(new String[]{"Internet permission: ON","Portrait orientation","Release signing","Offline cache: ON"} ,null).setPositiveButton("Done",null).show();
    }
    private void showHistory() {
        new AlertDialog.Builder(this).setTitle("Build History").setMessage("No completed builds yet.").setPositiveButton("OK",null).show();
    }

    private void runBuild(boolean fast) {
        if(selectedFile.isEmpty()){ Toast.makeText(this,"Select a ZIP or HTML file first",Toast.LENGTH_SHORT).show(); return; }
        if(fast && !fastActive){ Toast.makeText(this,"Enter the access key first",Toast.LENGTH_SHORT).show(); return; }
        int seconds=fast?60:300;
        final Dialog dialog=new Dialog(this);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24),dp(22),dp(24),dp(18));
        TextView title=tv(fast?"Fast Build":"Build APK",20,Color.WHITE); title.setTypeface(null,1); box.addView(title);
        TextView stage=tv("Preparing…",14,Color.rgb(137,146,152)); box.addView(stage,new LinearLayout.LayoutParams(-1,dp(42)));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(100); box.addView(bar,new LinearLayout.LayoutParams(-1,dp(18)));
        TextView time=tv("05:00",12,Color.rgb(86,230,209)); time.setGravity(Gravity.CENTER); box.addView(time,new LinearLayout.LayoutParams(-1,dp(34)));
        dialog.setContentView(box); Window w=dialog.getWindow(); if(w!=null){w.setBackgroundDrawable(bg(Color.rgb(21,24,27),24,0));w.setLayout(-1,-2);}
        dialog.show();
        final String[] stages={"Preparing…","Analyzing project…","Building Android resources…","Compiling…","Packaging APK…","Signing…","Ready"};
        final long start=System.currentTimeMillis();
        final Handler h=new Handler(Looper.getMainLooper());
        Runnable r=new Runnable(){ public void run(){
            long elapsed=(System.currentTimeMillis()-start)/1000; int pct=(int)Math.min(100,elapsed*100/seconds);
            bar.setProgress(pct); int remain=Math.max(0,seconds-(int)elapsed);
            time.setText(String.format("%02d:%02d",remain/60,remain%60));
            stage.setText(stages[Math.min(stages.length-1,(int)(pct/17))]);
            if(elapsed<seconds){h.postDelayed(this,250);}
            else { stage.setText("Ready"); time.setText("100%"); Toast.makeText(MainActivity.this,"Build simulation complete. Connect a build service to produce a real APK.",Toast.LENGTH_LONG).show(); }
        }}; h.post(r);
    }

    private void startTimerLoop(){
        final Handler h=new Handler(Looper.getMainLooper());
        h.post(new Runnable(){public void run(){
            if(fastActive){
                long left=fastUntil-System.currentTimeMillis();
                if(left<=0){fastActive=false;timerText.setText("Key required");}
                else {long s=left/1000;timerText.setText(String.format("%02d:%02d active",s/60,s%60));}
            }
            h.postDelayed(this,1000);
        }});
    }
}
