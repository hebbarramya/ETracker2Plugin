package com.mti.eTracker2CommsPlugin;

import android.util.Log;

import java.lang.*;
import java.util.Arrays;

import com.google.gson.*;

import android.os.Handler;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;
import com.telit.terminalio.TIOManager;
import com.telit.terminalio.TIOPeripheral;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;

import android.content.Context;

public class DeviceConnectionController implements TIOConnectionCallback {

    private TIOConnection connection;
    private Timer timer;
    private String currentAction;
    private DeviceConnectionController that = this;
    private IPluginResult callback;
    private static final String TAG = "DeviceConnection";
    private static final int timerExecInterval = 5 * 1000;
    private DeviceConnectionController classcontext = this;
    private int connectAttemptCount;
    private TIOPeripheral peripheralToConnect;
    private Context context;

    public DeviceConnectionController(String currentAction) {
        //timer = new Timer(true);
        this.currentAction = currentAction;
    }

    public void connect(TIOManager tioManager, String stationAddress, IPluginResult callback) {

        this.callback = callback;

        if (!PluginUtil.isValidStationAddress(stationAddress)) {
            String msg = PluginUtil.composePluginExecResult(false, false, "Invalid device address", ETrackerStatusCode.INVALID_DEVICE_ADDRESS);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }

//        DeviceContainer container = DeviceContainer.getInstance();
//        if (!container.isContainerEmpty()) {
//            String msg = PluginUtil.composePluginExecResult(false, false, "Already connected with BLE device", ETrackerStatusCode.BLE_ALREADY_CONNECTED);
//            callback.sendResult(PluginResult.Status.ERROR, msg, false);
//            return;
//        }

        peripheralToConnect = tioManager.findPeripheral(stationAddress);

        LOG.d(TAG, "Peripheral Found." + peripheralToConnect.toString());

        Handler mScanHandler = new Handler();
        mScanHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {

                    if (peripheralToConnect == null) {
                        String msg = PluginUtil.composePluginExecResult(false, false, "Peripheral device not found.", ETrackerStatusCode.BLE_DEVICE_UNAVAILABLE);
                        callback.sendResult(PluginResult.Status.ERROR, msg, false);
                        return;
                    }

                    int connectionState = peripheralToConnect.getConnectionState();
                    if (connectionState == TIOConnection.STATE_DISCONNECTED) {
                        LOG.d(TAG, "Going to connect.");
                        //connectAttemptCount++;
                        //LOG.d(TAG, "Connection attempt count: " + connectAttemptCount);
                        peripheralToConnect.connect(that);
                        //timer.scheduleAtFixedRate(classcontext, timerExecInterval, timerExecInterval);
                    } else {
                        PluginExecResult state = new PluginExecResult();
                        state.IsConnected = false;

                        if (connectionState == TIOConnection.STATE_CONNECTED) {
                            state.StatusCode = ETrackerStatusCode.BLE_ALREADY_CONNECTED;
                            state.Message = "Peripheral device is already connected.";
                        } else if (connectionState == TIOConnection.STATE_CONNECTING) {
                            state.StatusCode = ETrackerStatusCode.BLE_ALREADY_CONNECTING;
                            state.Message = "Peripheral device is in connecting state.";
                        } else if (connectionState == TIOConnection.STATE_DISCONNECTING) {
                            state.StatusCode = ETrackerStatusCode.BLE_ALREADY_DISCONNECTING;
                            state.Message = "Peripheral device in disconneting state.";
                        }

                        String json = new Gson().toJson(state);
                        callback.sendResult(PluginResult.Status.OK, json, false);
                    }
                } catch (Exception ex) {
                    String msg = "Error occured while connecting BLE device." + ex.toString();
                    msg = PluginUtil.composePluginExecResult(false, false, msg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
                    callback.sendResult(PluginResult.Status.ERROR, msg, false);
                }
            }
        }, 5000);
    }

    public void disconnectStation(IPluginResult callback) {
        this.callback = callback;
        try {
            DeviceContainer container = DeviceContainer.getInstance();
            TIOConnection connection = container.getPeripheralConnection();
            if (connection.getConnectionState() == TIOConnection.STATE_CONNECTED) {
                connection.disconnect();
                DeviceContainer.emptyContainer();
                String msg = PluginUtil.composePluginExecResult(false, true, "BLE disconnected",
                        ETrackerStatusCode.BLE_DISCONNECTED);
                callback.sendResult(PluginResult.Status.OK, msg, false);
            } else {
                String msg = "Peripheral device is not in connected state. ";
                msg = PluginUtil.composePluginExecResult(false, false, msg, ETrackerStatusCode.BLE_NOT_CONNECTED);
                callback.sendResult(PluginResult.Status.ERROR, msg, false);
            }
        } catch (Exception ex) {
            String msg = "Error occured while disconnecting device. " + ex.toString();
            msg = PluginUtil.composePluginExecResult(false, false, msg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    public void logout(Context context, IPluginResult callback) {
        this.context = context;
        this.callback = callback;
        try {
            DeviceContainer container = DeviceContainer.getInstance();
            this.connection = container.getPeripheralConnection();
            this.connection.setListener(that);
            if (connection != null) {
                if (connection.getConnectionState() == TIOConnection.STATE_CONNECTED) {
                    LOG.d(TAG, "Peripheral in connected state, calling disconnect method.");
                    connection.disconnect();

                    LOG.d(TAG, "Emptying container and disposing resources.");
                    DeviceContainer.emptyContainer();
                    new ResourceCleaner().disposeResources(context);
                } else {
                    LOG.d(TAG, "Emptying container and disposing resources.");
                    DeviceContainer.emptyContainer();
                    new ResourceCleaner().disposeResources(context);

                    String msg = PluginUtil.composePluginExecResult(false, true, "Logout success",
                            ETrackerStatusCode.BLE_DISCONNECTED);
                    callback.sendResult(PluginResult.Status.OK, msg, false);
                }
            } else {
                String msg = PluginUtil.composePluginExecResult(false, true, "BLE not Connected",
                        ETrackerStatusCode.BLE_NOT_CONNECTED);
                callback.sendResult(PluginResult.Status.OK, msg, false);
            }
            // ED-54 probable error scenario.

        } catch (Exception ex) {
            String exMsg = "Plugin internal error. " + ex.toString();
            String msg = PluginUtil.composePluginExecResult(false, false, exMsg,
                    ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    // region Timer event handler

//    @Override
//    public void run() {
//        try {
//            if(connectAttemptCount > 3){
//                stopTimer();
//                LOG.d(TAG, "Connection attempt exceeded, Sending response to UI.");
//                String msg = "Unable to connect to BLE device.";
//                msg = PluginUtil.composePluginExecResult(false, false, msg, ETrackerStatusCode.BLE_CONNECTION_FAILURE);
//                callback.sendResult(PluginResult.Status.OK, msg, false);
//            }
//
//            LOG.d(TAG, "Going to connect.");
//            connectAttemptCount++;
//            LOG.d(TAG, "Connection attempt count: " + connectAttemptCount);
//            peripheralToConnect.connect(that);
//
//        } catch (Exception ex) {
//            String msg = "Error occured while connecting BLE device." + ex.toString();
//            msg = PluginUtil.composePluginExecResult(false, false, msg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
//            callback.sendResult(PluginResult.Status.ERROR, msg, false);
//        }
//
//    }

    // endregion

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    // region Peripheral connection event handlers

    @Override
    public void onConnected(TIOConnection connection) {

        LOG.d(TAG, "Device Connected.");
        String stationAddress = "";
        String msg = "";

        //stopTimer();

        if (connection == null) {
            msg = PluginUtil.composePluginExecResult(true, true,
                    "Peripheral device connected but invalid connection object",
                    ETrackerStatusCode.BLE_INVALID_CONNECTION_CONTEXT);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        } else {
            TIOPeripheral peripheral = connection.getPeripheral();
            if (peripheral != null) {

                DeviceContainer container = DeviceContainer.getInstance();
                container.addConnectedPeripheral(peripheral, connection);

                stationAddress = peripheral.getAddress();
            }

            msg = PluginUtil.composePluginExecResult(true, true, "Peripheral device connected",
                    ETrackerStatusCode.BLE_CONNECTED);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        }
    }

    @Override
    public void onConnectFailed(TIOConnection connection, String errorMsg) {

        LOG.d(TAG, "Unable to connect.");
        DeviceContainer.emptyContainer();

        String msg = "Peripheral device connection failed. " + errorMsg.toString();
        msg = PluginUtil.composePluginExecResult(false, false, msg, ETrackerStatusCode.BLE_CONNECTION_FAILURE);

        callback.sendResult(PluginResult.Status.ERROR, msg, false);
    }

    @Override
    public void onDisconnected(TIOConnection connection, String errorMsg) {

        DeviceContainer.emptyContainer();
        LOG.d(TAG, "Device Disconnected.");
        LOG.d(TAG, "Current Action:" + this.currentAction);

        if (this.currentAction == ETrackerAction.CONNECT_STATION) {
            String msg = PluginUtil.composePluginExecResult(false, false, "BLE disconnected",
                    ETrackerStatusCode.BLE_DISCONNECTED);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        } else if (this.currentAction == ETrackerAction.DISCONNECT_STATION) {
            String msg = PluginUtil.composePluginExecResult(false, true, "BLE disconnected",
                    ETrackerStatusCode.BLE_DISCONNECTED);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        } else if (this.currentAction == ETrackerAction.LOGOUT) {
            String msg = PluginUtil.composePluginExecResult(false, true, "User log out success",
                    ETrackerStatusCode.BLE_DISCONNECTED);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        } else {
            String msg = PluginUtil.composePluginExecResult(false, false, "BLE disconnected",
                    ETrackerStatusCode.BLE_DISCONNECTED);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        }
    }

    @Override
    public void onDataReceived(TIOConnection connection, byte[] data) {
        if (data == null) {
            String msg = PluginUtil.composePluginExecResult(true, false, "Invalid data received from ble device",
                    ETrackerStatusCode.INVALID_RESPONSE_DATA);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    @Override
    public void onDataTransmitted(TIOConnection connection, int status, int bytesWritten) {
        int dataTransmitStat = status;
        int dataLenWritten = bytesWritten;
    }

    @Override
    public void onReadRemoteRssi(TIOConnection connection, int status, int rssi) {
    }

    @Override
    public void onLocalUARTMtuSizeUpdated(TIOConnection connection, int mtuSize) {
    }

    @Override
    public void onRemoteUARTMtuSizeUpdated(TIOConnection connection, int mtuSize) {
    }

    // endregion

}