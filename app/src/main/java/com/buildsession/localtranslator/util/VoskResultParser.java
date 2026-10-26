package com.buildsession.localtranslator.util;

import org.json.JSONObject;
import org.json.JSONException;

public class VoskResultParser {
    
    public static String parseResult(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(jsonStr);
            return json.optString("text", "");
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String parsePartialResult(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(jsonStr);
            return json.optString("partial", "");
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }
}
