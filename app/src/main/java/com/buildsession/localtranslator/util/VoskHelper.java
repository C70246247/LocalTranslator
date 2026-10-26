package com.buildsession.localtranslator.util;

import org.vosk.Recognizer;
import java.lang.reflect.Method;

public class VoskHelper {
    
    private static Method getResultMethod;
    private static Method getPartialResultMethod;
    
    static {
        try {
            Class<?> clazz = Recognizer.class;
            try {
                getResultMethod = clazz.getMethod("getResult");
            } catch (NoSuchMethodException e) {
                getResultMethod = clazz.getMethod("result");
            }
            
            try {
                getPartialResultMethod = clazz.getMethod("getPartialResult");
            } catch (NoSuchMethodException e) {
                getPartialResultMethod = clazz.getMethod("partialResult");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getResult(Recognizer recognizer) {
        if (recognizer == null || getResultMethod == null) return "";
        try {
            return (String) getResultMethod.invoke(recognizer);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    
    public static String getPartialResult(Recognizer recognizer) {
        if (recognizer == null || getPartialResultMethod == null) return "";
        try {
            return (String) getPartialResultMethod.invoke(recognizer);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    
    public static boolean acceptWaveForm(Recognizer recognizer, short[] buffer, int len) {
        if (recognizer == null) return false;
        return recognizer.acceptWaveForm(buffer, len);
    }
}
