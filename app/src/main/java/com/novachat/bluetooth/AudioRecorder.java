package com.novachat.bluetooth;

import android.media.MediaRecorder;
import java.io.File;

public class AudioRecorder {
    private MediaRecorder recorder;
    private File file;
    public File start(File dir) throws Exception {
        file=new File(dir,"voice_"+System.currentTimeMillis()+".m4a");
        recorder=new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioSamplingRate(44100);
        recorder.setAudioEncodingBitRate(96000);
        recorder.setOutputFile(file.getAbsolutePath());
        recorder.prepare(); recorder.start();
        return file;
    }
    public File stop(){
        try{recorder.stop();}catch(Exception ignored){}
        try{recorder.release();}catch(Exception ignored){}
        recorder=null; return file;
    }
}
