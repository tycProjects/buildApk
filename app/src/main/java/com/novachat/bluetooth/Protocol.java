package com.novachat.bluetooth;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class Protocol {
    public static final int HELLO=1, TEXT=2, FILE=3, AUDIO=4, IMAGE=5, SYSTEM=6;

    private Protocol(){}

    public static void writeText(DataOutputStream out, String text) throws IOException {
        synchronized(out) {
            out.writeInt(TEXT);
            writeString(out, text);
            out.flush();
        }
    }

    public static void writeFile(DataOutputStream out, int type, String name, String mime, long size, InputStream in)
            throws IOException {
        synchronized(out) {
            out.writeInt(type);
            writeString(out, name);
            writeString(out, mime == null ? "application/octet-stream" : mime);
            out.writeLong(size);
            byte[] buf = new byte[16 * 1024];
            long left=size;
            while(left>0) {
                int want=(int)Math.min(buf.length,left);
                int n=in.read(buf,0,want);
                if(n<0) throw new EOFException("file ended early");
                out.writeInt(n);
                out.write(buf,0,n);
                left-=n;
            }
            out.flush();
        }
    }

    public static Object[] read(DataInputStream in, File dir) throws IOException {
        int type=in.readInt();
        if(type==TEXT) return new Object[]{type, readString(in)};
        if(type==SYSTEM) return new Object[]{type, readString(in)};
        if(type==FILE || type==IMAGE || type==AUDIO) {
            String name=readString(in);
            String mime=readString(in);
            long size=in.readLong();
            if(size<0 || size>100*1024*1024) throw new IOException("file too large");
            File f=new File(dir, System.currentTimeMillis()+"_"+safe(name));
            try(FileOutputStream fos=new FileOutputStream(f)) {
                long left=size;
                while(left>0) {
                    int n=in.readInt();
                    if(n<=0 || n>16*1024 || n>left) throw new IOException("bad chunk");
                    byte[] b=new byte[n];
                    in.readFully(b);
                    fos.write(b);
                    left-=n;
                }
            }
            return new Object[]{type,f,name,mime,size};
        }
        throw new IOException("unknown packet");
    }

    private static void writeString(DataOutputStream out,String s)throws IOException{
        byte[] b=s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length); out.write(b);
    }
    private static String readString(DataInputStream in)throws IOException{
        int n=in.readInt();
        if(n<0 || n>1024*1024) throw new IOException("bad string");
        byte[] b=new byte[n]; in.readFully(b);
        return new String(b,StandardCharsets.UTF_8);
    }
    private static String safe(String s){return s.replaceAll("[^a-zA-Z0-9._-]","_");}
}
