package com.novachat.bluetooth;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity implements BluetoothReceiver.Listener, BluetoothChatService.Listener {
    private static final int REQ=50, PICK_FILE=51, PICK_IMAGE=52;
    private BluetoothAdapter adapter;
    private BluetoothChatService chat;
    private LinearLayout messages, deviceBox;
    private TextView status,title,recordState;
    private EditText input;
    private Spinner deviceSpinner;
    private final ArrayList<BluetoothDevice> devices=new ArrayList<>();
    private final ArrayList<String> names=new ArrayList<>();
    private AudioRecorder recorder;
    private File cacheDir;
    private int logoTaps=0; private long lastTap=0;
    private final android.content.SharedPreferences prefs;

    public MainActivity(){
        super();
        prefs=null;
    }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        cacheDir=new File(getExternalFilesDir(null),"outgoing"); if(!cacheDir.exists())cacheDir.mkdirs();
        prefs=getSharedPreferences("private",0);
        adapter=BluetoothAdapter.getDefaultAdapter();
        NotificationHelper.create(this);
        buildUi();
        requestPermissionsIfNeeded();
        BluetoothReceiver.listener=this;
        loadPaired();
    }

    private TextView tv(String s,float z){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.WHITE);return v;
    }
    private Button btn(String s){
        Button b=new Button(this);b.setText(s);return b;
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(12,8,12,8);root.setBackgroundColor(Color.rgb(13,17,24));

        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        title=tv("✦ NovaChat",24);title.setGravity(Gravity.CENTER);
        bar.addView(title,new LinearLayout.LayoutParams(0,58,1));
        Button menu=btn("☰");bar.addView(menu,new LinearLayout.LayoutParams(58,58));
        root.addView(bar);
        title.setOnClickListener(v->adminTap());

        status=tv("🔴 غير متصل",13);status.setGravity(Gravity.CENTER);root.addView(status);

        ScrollView controlsScroll=new ScrollView(this);
        deviceBox=new LinearLayout(this);deviceBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row1=new LinearLayout(this);
        Button host=btn("📡 استضافة"); Button scan=btn("🔍 بحث"); Button connect=btn("🔗 اتصال");
        row1.addView(host,new LinearLayout.LayoutParams(0,55,1));row1.addView(scan,new LinearLayout.LayoutParams(0,55,1));row1.addView(connect,new LinearLayout.LayoutParams(0,55,1));
        deviceBox.addView(row1);
        deviceSpinner=new Spinner(this);deviceBox.addView(deviceSpinner,new LinearLayout.LayoutParams(-1,55));
        controlsScroll.addView(deviceBox);root.addView(controlsScroll,new LinearLayout.LayoutParams(-1,125));

        messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);messages.setPadding(6,10,6,10);
        ScrollView scroll=new ScrollView(this);scroll.addView(messages);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        recordState=tv("",12);recordState.setGravity(Gravity.CENTER);root.addView(recordState);
        LinearLayout composer=new LinearLayout(this);
        Button image=btn("🖼");Button file=btn("📎");Button mic=btn("🎤");
        input=new EditText(this);input.setHint("اكتب رسالة...");input.setSingleLine(true);input.setTextColor(Color.WHITE);input.setHintTextColor(Color.GRAY);
        Button send=btn("➤");
        composer.addView(image,new LinearLayout.LayoutParams(52,58));composer.addView(file,new LinearLayout.LayoutParams(52,58));
        composer.addView(input,new LinearLayout.LayoutParams(0,58,1));composer.addView(mic,new LinearLayout.LayoutParams(52,58));composer.addView(send,new LinearLayout.LayoutParams(55,58));
        root.addView(composer);setContentView(root);

        chat=new BluetoothChatService(this,this);
        host.setOnClickListener(v->host());
        scan.setOnClickListener(v->discover());
        connect.setOnClickListener(v->connectSelected());
        send.setOnClickListener(v->sendText());
        input.setOnEditorActionListener((v,a,e)->{sendText();return true;});
        image.setOnClickListener(v->pickImage());
        file.setOnClickListener(v->pickFile());
        mic.setOnClickListener(v->toggleRecord(mic));
        menu.setOnClickListener(v->showMenu());
    }

    private void requestPermissionsIfNeeded(){
        ArrayList<String> p=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31){p.add(Manifest.permission.BLUETOOTH_CONNECT);p.add(Manifest.permission.BLUETOOTH_SCAN);p.add(Manifest.permission.BLUETOOTH_ADVERTISE);}
        if(Build.VERSION.SDK_INT>=33)p.add(Manifest.permission.POST_NOTIFICATIONS);
        p.add(Manifest.permission.RECORD_AUDIO);
        if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),REQ);
        if(adapter!=null && !adapter.isEnabled())startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),80);
    }

    private boolean allowed(String address){return !prefs.getBoolean("ban_"+address,false);}
    private void loadPaired(){
        try{
            devices.clear();names.clear();
            Set<BluetoothDevice> set=adapter.getBondedDevices();
            for(BluetoothDevice d:set)if(allowed(d.getAddress())){devices.add(d);names.add((d.getName()==null?"جهاز":d.getName())+" • "+d.getAddress());}
            deviceSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
            status.setText("🔵 Bluetooth جاهز • الأجهزة المقترنة: "+devices.size());
        }catch(SecurityException e){status.setText("⚠️ اسمح بصلاحيات Bluetooth");}
    }

    private void discover(){
        try{
            if(adapter.isDiscovering())adapter.cancelDiscovery();
            BluetoothReceiver.found.clear();
            adapter.startDiscovery();
            status.setText("🔎 جارٍ البحث عن أجهزة قريبة...");
        }catch(Exception e){status.setText("تعذر البحث");}
    }

    @Override public void onFound(BluetoothDevice d){
        runOnUiThread(()->{
            if(!allowed(d.getAddress()))return;
            if(!devices.contains(d))devices.add(d);
            names.clear();for(BluetoothDevice x:devices)names.add((safeName(x))+" • "+x.getAddress());
            deviceSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        });
    }
    @Override public void onFinished(){runOnUiThread(()->status.setText("🔵 انتهى البحث"));}

    private String safeName(BluetoothDevice d){try{return d.getName()==null?"جهاز":d.getName();}catch(Exception e){return "جهاز";}}

    private void host(){chat.host();}
    private void connectSelected(){
        if(devices.isEmpty()){toast("لا يوجد جهاز. اقترن بالهاتف أولاً.");return;}
        int i=deviceSpinner.getSelectedItemPosition();if(i<0||i>=devices.size())return;
        chat.connect(devices.get(i));
    }

    private void sendText(){
        String s=input.getText().toString().trim();if(s.isEmpty())return;
        if(!chat.isConnected()){toast("اتصل بصديقك أولاً");return;}
        input.setText("");chat.sendText(s);
    }

    private void pickImage(){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_IMAGE);}
    private void pickFile(){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_FILE);}

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(c!=RESULT_OK||d==null||d.getData()==null)return;
        if(r==PICK_IMAGE||r==PICK_FILE){
            try{
                Uri u=d.getData();String mime=getContentResolver().getType(u);if(mime==null)mime="application/octet-stream";
                String name="file_"+System.currentTimeMillis();
                android.database.Cursor cur=getContentResolver().query(u,null,null,null,null);
                if(cur!=null){int n=cur.getColumnIndex("_display_name");if(cur.moveToFirst()&&n>=0)name=cur.getString(n);cur.close();}
                File f=new File(cacheDir,name);
                try(InputStream in=getContentResolver().openInputStream(u);FileOutputStream out=new FileOutputStream(f)){
                    byte[] b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);
                }
                int type=(r==PICK_IMAGE)?Protocol.IMAGE:Protocol.FILE;
                chat.sendFile(f,mime,type);
            }catch(Exception e){toast("تعذر قراءة الملف");}
        }
    }

    private void toggleRecord(Button b){
        if(recorder==null){
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ);return;}
            try{recorder=new AudioRecorder();recorder.start(cacheDir);recordState.setText("🔴 تسجيل صوتي... اضغط 🎤 للإيقاف");b.setText("⏹");}
            catch(Exception e){recorder=null;toast("تعذر بدء التسجيل");}
        }else{
            File f=recorder.stop();recorder=null;b.setText("🎤");recordState.setText("");
            if(f!=null&&f.exists())chat.sendFile(f,"audio/mp4",Protocol.AUDIO);
        }
    }

    private void addText(String s,boolean mine){
        messages.addView(MessageView.bubble(this,s,mine));
        scrollBottom();
    }
    private void addFile(File f,String name,String mime,long size,int type,boolean mine){
        String prefix=type==Protocol.IMAGE?"🖼 صورة":type==Protocol.AUDIO?"🎤 رسالة صوتية":"📎 ملف";
        View v=MessageView.file(this,prefix+" • "+name+(size>0?" • "+(size/1024)+" KB":""),mine);
        v.setOnClickListener(x->{
            if(type==Protocol.AUDIO){playAudio(f);}
            else if(type==Protocol.IMAGE){Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(Uri.fromFile(f),mime);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(i);}catch(Exception e){toast("لا يوجد تطبيق لفتح الملف");}}
            else{toast("تم حفظ الملف في مجلد NovaChat/received");}
        });
        messages.addView(v);scrollBottom();
    }
    private void playAudio(File f){
        try{MediaPlayer mp=MediaPlayer.create(this,Uri.fromFile(f));mp.setOnCompletionListener(MediaPlayer::release);mp.start();toast("▶ تشغيل الرسالة الصوتية");}
        catch(Exception e){toast("تعذر تشغيل الصوت");}
    }
    private void scrollBottom(){messages.post(()->{View p=messages.getParent();if(p instanceof ScrollView)((ScrollView)p).fullScroll(View.FOCUS_DOWN);});}

    @Override public void onState(String s){runOnUiThread(()->status.setText(s));}
    @Override public void onText(String s,boolean mine){runOnUiThread(()->{addText(s,mine);if(!mine)NotificationHelper.show(this,"NovaChat",s);});}
    @Override public void onFile(File f,String n,String m,long z,int t,boolean mine){runOnUiThread(()->{addFile(f,n,m,z,t,mine);if(!mine)NotificationHelper.show(this,"NovaChat",t==Protocol.IMAGE?"📷 صورة":t==Protocol.AUDIO?"🎤 رسالة صوتية":"📎 ملف");});}
    @Override public void onDisconnected(){runOnUiThread(()->status.setText("🔴 انقطع الاتصال"));}

    private void showMenu(){
        final String[] items={"👤 ملفي الشخصي","🧹 مسح المحادثة","🛡️ لوحة الإدارة","ℹ️ عن التطبيق"};
        new AlertDialog.Builder(this).setTitle("NovaChat").setItems(items,(d,w)->{
            if(w==0)profile();
            else if(w==1){messages.removeAllViews();toast("تم مسح العرض المحلي");}
            else if(w==2)admin();
            else new AlertDialog.Builder(this).setTitle("NovaChat Bluetooth").setMessage("محادثة خاصة عبر Bluetooth بدون إنترنت أو خادم.").setPositiveButton("حسنًا",null).show();
        }).show();
    }

    private void profile(){
        EditText e=new EditText(this);e.setHint("اسمك");
        e.setText(prefs.getString("name","أنا"));
        new AlertDialog.Builder(this).setTitle("الملف الشخصي").setView(e).setPositiveButton("حفظ",(d,w)->prefs.edit().putString("name",e.getText().toString()).apply()).setNegativeButton("إلغاء",null).show();
    }

    private void adminTap(){
        long now=System.currentTimeMillis();if(now-lastTap>1800)logoTaps=0;lastTap=now;logoTaps++;
        if(logoTaps>=7){logoTaps=0;admin();}
    }

    private void admin(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(20,5,20,5);
        TextView t=tv("لوحة خاصة للمشرف\nيمكنك فصل الصديق وحظر جهاز Bluetooth.",16);box.addView(t);
        Button kick=btn("🚪 إخراج / قطع الاتصال");Button ban=btn("🚫 حظر الجهاز المحدد");Button unban=btn("♻️ إلغاء الحظر");
        box.addView(kick);box.addView(ban);box.addView(unban);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("🛡️ الإدارة").setView(box).setNegativeButton("إغلاق",null).create();
        kick.setOnClickListener(v->{chat.close();status.setText("🔴 تم قطع الاتصال بواسطة الإدارة");dlg.dismiss();});
        ban.setOnClickListener(v->{BluetoothDevice d=selected();if(d!=null){prefs.edit().putBoolean("ban_"+d.getAddress(),true).apply();chat.close();loadPaired();toast("تم حظر الجهاز");}dlg.dismiss();});
        unban.setOnClickListener(v->{BluetoothDevice d=selected();if(d!=null){prefs.edit().putBoolean("ban_"+d.getAddress(),false).apply();loadPaired();toast("تم إلغاء الحظر");}dlg.dismiss();});
        dlg.show();
    }
    private BluetoothDevice selected(){int i=deviceSpinner.getSelectedItemPosition();return i>=0&&i<devices.size()?devices.get(i):null;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override protected void onDestroy(){if(chat!=null)chat.close();super.onDestroy();}
}
