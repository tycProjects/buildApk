package com.novachat.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import java.util.ArrayList;

public class BluetoothReceiver extends BroadcastReceiver {
    public static final ArrayList<BluetoothDevice> found=new ArrayList<>();
    public static Listener listener;
    public interface Listener { void onFound(BluetoothDevice d); void onFinished(); }
    @Override public void onReceive(Context c,Intent i){
        String a=i.getAction();
        if(BluetoothDevice.ACTION_FOUND.equals(a)){
            BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(d!=null && !found.contains(d)){found.add(d);if(listener!=null)listener.onFound(d);}
        }else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(a) && listener!=null) listener.onFinished();
    }
}