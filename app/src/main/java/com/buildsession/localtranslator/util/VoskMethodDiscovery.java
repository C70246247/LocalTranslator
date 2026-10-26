package com.buildsession.localtranslator.util;

import android.util.Log;
import org.vosk.Recognizer;
import java.lang.reflect.Method;

public class VoskMethodDiscovery {
    public static void discoverMethods() {
        try {
            Class<?> clazz = Recognizer.class;
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                Log.d("VoskDiscovery", "Method: " + method.getName() + " Return type: " + method.getReturnType().getName());
            }
        } catch (Exception e) {
            Log.e("VoskDiscovery", "Error discovering methods", e);
        }
    }
}
