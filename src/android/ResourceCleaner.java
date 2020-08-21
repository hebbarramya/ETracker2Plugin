package com.mti.eTracker2CommsPlugin;

import com.telit.terminalio.TIOManager;

import android.content.Context;

import org.apache.cordova.CordovaPlugin;

public class ResourceCleaner extends CordovaPlugin {

    public ResourceCleaner() {
    }

    void disposeResources() {

        try {
            Context context = this.cordova.getActivity().getApplicationContext();
            if (context == null) return;

            TIOManager.initialize(context);
            TIOManager tioManager = TIOManager.getInstance();
            tioManager.done();
        } catch (Exception ex) {
            // TODO: log error
        }
    }

    void disposeResources(Context context) {
        try {
            if (context == null) return;
                TIOManager.initialize(context);
                TIOManager.getInstance().done();
        } catch (Exception ex) {
            // TODO: log error
        }
    }
}