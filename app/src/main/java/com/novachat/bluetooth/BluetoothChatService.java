package com.novachat.bluetooth;

import android.bluetooth.*;
import android.content.Context;
import android.os.Handler;
import java.io.*;
import java.util.UUID;

public class BluetoothChatService {
    public interface Listener {
        void onState(String s);
        void onText(String text, boolean mine);
        void onFile(File file,String name,String mime,long size,int type,boolean mine);
        void onDisconnected();
    }

    private final BluetoothAdapter adapter;
    private final Handler main;
    private final Listener listener;
    private final File receiveDir;
    private final UUID uuid=UUID.fromString("8d4b8e20-6f1e-4b9d-9f5b-8c9a0a5e4a11");
    private BluetoothServerSocket server;
    private BluetoothSocket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread reader;
    private volatile boolean closed=true;

    public BluetoothChatService(Context c,Listener l){
        adapter=BluetoothAdapter.getDefaultAdapter();
        main=new Handler(c.getMainLooper());
        listener=l;
        receiveDir=new File(c.getExternalFilesDir(null),"received");
        if(!receiveDir.exists()) receiveDir.mkdirs();
    }

    public void host(){
        close();
        closed=false;
        new Thread(()->{
            try{
                listener.onState("🟡 بانتظار اتصال صديقك...");
                server=adapter.listenUsingRfcommWithServiceRecord("NovaChat Bluetooth",uuid);
                socket=server.accept();
                try{server.close();}catch(Exception ignored){}
                connected();
            }catch(Exception e){ listener.onState("تعذر بدء الاستضافة"); close(); }
        }).start();
    }

    public void connect(BluetoothDevice device){
        close(); closed=false;
        new Thread(()->{
            try{
                listener.onState("🟡 جارٍ الاتصال...");
                adapter.cancelDiscovery();
                socket=device.createRfcommSocketToServiceRecord(uuid);
                socket.connect();
                connected();
            }catch(Exception e){listener.onState("❌ فشل الاتصال");close();}
        }).start();
    }

    private void connected() throws IOException{
        in=new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        listener.onState("🟢 متصل");
        reader=new Thread(()->{
            try{
                while(!closed){
                    Object[] p=Protocol.read(in,receiveDir);
                    int type=(Integer)p[0];
                    if(type==Protocol.TEXT || type==Protocol.SYSTEM) listener.onText((String)p[1],false);
                    else listener.onFile((File)p[1],(String)p[2],(String)p[3],(Long)p[4],type,false);
                }
            }catch(Exception ignored){} finally { if(!closed) listener.onDisconnected(); }
        });
        reader.start();
    }

    public void sendText(String s){
        if(out==null)return;
        new Thread(()->{try{Protocol.writeText(out,s);listener.onText(s,true);}catch(Exception e){listener.onState("❌ تعذر الإرسال");}}).start();
    }

    public void sendFile(File f,String mime,int type){
        if(out==null)return;
        new Thread(()->{
            try(FileInputStream fis=new FileInputStream(f)){
                Protocol.writeFile(out,type,f.getName(),mime,f.length(),fis);
                listener.onFile(f,f.getName(),mime,f.length(),type,true);
            }catch(Exception e){listener.onState("❌ تعذر إرسال الملف");}
        }).start();
    }

    public boolean isConnected(){return out!=null && socket!=null && socket.isConnected() && !closed;}

    public void close(){
        closed=true;
        try{if(server!=null)server.close();}catch(Exception ignored){}
        try{if(socket!=null)socket.close();}catch(Exception ignored){}
        server=null;socket=null;in=null;out=null;
    }
}
