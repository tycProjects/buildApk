package com.novachat.bluetooth;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

public final class MessageView {
    public static View bubble(Context c,String text,boolean mine){
        TextView v=new TextView(c);
        v.setText(text); v.setTextColor(Color.WHITE); v.setTextSize(16);
        v.setPadding(20,12,20,12);
        v.setBackgroundColor(Color.parseColor(mine?"#6C5CE7":"#222B3A"));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);
        p.gravity=mine?Gravity.START:Gravity.END; p.topMargin=5;p.bottomMargin=5;
        v.setLayoutParams(p); return v;
    }
    public static View file(Context c,String label,boolean mine){
        TextView v=(TextView)bubble(c,label,mine); v.setOnClickListener(x->{});
        return v;
    }
}
