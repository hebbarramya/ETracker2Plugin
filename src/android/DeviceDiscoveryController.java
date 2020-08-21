package com.mti.eTracker2CommsPlugin;

import java.lang.*;

import com.google.gson.*;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;

import org.apache.cordova.CallbackContext;

import com.telit.terminalio.TIOManager;
import com.telit.terminalio.TIOManagerCallback;
import com.telit.terminalio.TIOPeripheral;

import org.apache.cordova.PluginResult;

public class DeviceDiscoveryController implements TIOManagerCallback {

    private TIOManager tioManager;

    public void execute(TIOManager tioManager, CallbackContext callbackContext) {

        Handler mScanHandler = new Handler();
        ArrayList<Station> stationList = new ArrayList<Station>();
        this.tioManager = tioManager;
        tioManager.removeAllPeripherals();

        mScanHandler.postDelayed(() -> {

            try {
                tioManager.stopScan();
                TIOPeripheral[] peripherals = tioManager.getPeripherals();

                for (int i = 0; peripherals != null && i < peripherals.length; i++) {

                    String data;
                    Station station = new Station();

                    TIOPeripheral peripheral = peripherals[i];

                    if(peripheral.getAdvertisement() != null){
                        data = peripheral.getAddress();

                        if (data != null && data != "") {
                            station.setStationId(data);
                            data = peripheral.getName();
                            station.setStationName(data);
                            stationList.add(station);
                        }
                    }

                }

                Log.d("Device Discovery", "No of Peripherals found: " + peripherals.length);

                Gson gson = new Gson();
                String json = gson.toJson(stationList);

                PluginResult result = new PluginResult(PluginResult.Status.OK, json);
                result.setKeepCallback(false);
                callbackContext.sendPluginResult(result);

            } catch (Exception ex) {
                String msg = "Error occured during device discovery. " + ex.toString();
                msg = PluginUtil.composePluginExecResult(false, false, msg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
                callbackContext.error(msg);
            }
        }, 10000);

        tioManager.startScan(this);

    }

    @Override
    public void onPeripheralFound(TIOPeripheral tioPeripheral) {
//        tioPeripheral.setShallBeSaved(false);
//        tioManager.savePeripherals();
    }

    @Override
    public void onPeripheralUpdate(TIOPeripheral tioPeripheral) {

    }

}