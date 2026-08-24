package com.tycept.chatapp.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeUtils {

    private TimeUtils() {}

    public static String formatTimestamp(long timestampMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return sdf.format(new Date(timestampMillis));
    }

    public static String timeAgo(long timestampMillis) {
        long diff = System.currentTimeMillis() - timestampMillis;
        long minutes = diff / (60 * 1000);
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        return days + "d";
    }
}
