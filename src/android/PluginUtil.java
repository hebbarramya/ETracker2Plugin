package com.mti.eTracker2CommsPlugin;

import com.google.gson.*;

class PluginUtil {

    public static String composePluginExecResult(boolean isConnected, boolean isActionSuccess, String msg, int statusCode) {

        PluginExecResult state = new PluginExecResult();
        state.IsConnected = isConnected;
        state.Message = msg;
        state.StatusCode = statusCode;
        state.IsActionSuccessful = isActionSuccess;

        Gson gson = new Gson();
        String json = gson.toJson(state);
        return json;
    }

    public static boolean isValidStationAddress(String stationAddress) {
        return stationAddress != null && stationAddress.trim() != "";
    }
}
