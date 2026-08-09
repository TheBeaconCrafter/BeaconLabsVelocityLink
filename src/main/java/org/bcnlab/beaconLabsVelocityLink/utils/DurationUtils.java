package org.bcnlab.beaconLabsVelocityLink.utils;

public class DurationUtils {
    public static String formatDuration(long millis) {
        if (millis <= 0) return "Permanent";
        
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }
}
