package com.mti.eTracker2CommsPlugin;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import org.apache.cordova.PluginResult;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

class PeripheralAuthController extends TimerTask implements TIOConnectionCallback {

    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private final Object lock;
    private boolean isResponseProcessing;
    private int responseWatchCounter;
    private boolean isAuthenticated;
    private static final int timerExecInterval = 5 * 1000;

    enum AUTH_STATUS {
        Success(0),
        Failure(1);

        private int numVal;

        AUTH_STATUS(int numVal) {
            this.numVal = numVal;
        }
    }


    // endregion

    // region Constructor

    public PeripheralAuthController() {
        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();
        this.connection.setListener(this);
        timer = new Timer(true);
        lock = new Object();
    }

    // endregion

    // region Public method

    public void execute(JSONObject authData, IPluginResult callback) {

        try {

            this.callback = callback;

            if (authData == null) {
                String msg = PluginUtil.composePluginExecResult(true, false, "Invalid auth data received", ETrackerStatusCode.INVALID_REQUEST_DATA);
                callback.sendResult(PluginResult.Status.ERROR, msg, false);
                return;
            }

            DeviceContainer container = DeviceContainer.getInstance();
            TIOConnection connection = container.getPeripheralConnection();

            String pwd = authData.has("Password") ? authData.get("Password").toString() : "";
            byte[] authDataPckt = getAuthDataPacket(pwd);
            connection.transmit(authDataPckt);

            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);

        } catch (Exception ex) {
            connection.setListener(null);
            String msg = PluginUtil.composePluginExecResult(true, false, "Error occurred while authenticating user", ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    // endregion

    // region Peripheral connection event handlers

    @Override
    public void onConnected(TIOConnection tioConnection) {
    }

    @Override
    public void onConnectFailed(TIOConnection tioConnection, String errorMsg) {
        String msg = "Peripheral device connection failed. " + errorMsg.toString();
        msg = PluginUtil.composePluginExecResult(false, false, msg, ETrackerStatusCode.BLE_CONNECTION_FAILURE);
        callback.sendResult(PluginResult.Status.ERROR, msg, false);
    }

    @Override
    public void onDisconnected(TIOConnection tioConnection, String s) {
        String msg = PluginUtil.composePluginExecResult(false, false, "BLE disconnected", ETrackerStatusCode.BLE_DISCONNECTED);
        callback.sendResult(PluginResult.Status.OK, msg, false);
    }

    @Override
    public void onDataReceived(TIOConnection tioConnection, byte[] data) {

        stopTimer();

        if (data == null) {
            String msg = PluginUtil.composePluginExecResult(true, false, "Invalid data received from ble device", ETrackerStatusCode.INVALID_RESPONSE_DATA);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
            return;
        }

        synchronized (lock) {
            isResponseProcessing = true;
        }

        if (data.length == 2 && data[1] == AUTH_STATUS.Success.numVal) {
            isAuthenticated = true;
        } else if (data.length > 2 && data[3] == AUTH_STATUS.Success.numVal) {
            isAuthenticated = true;
        }

        if (isAuthenticated) {
            String msg = PluginUtil.composePluginExecResult(true, true, "User authenticated", ETrackerStatusCode.USER_AUTHORIZATION_SUCCESS);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        } else {
            try{
                tioConnection.disconnect();
                DeviceContainer container = DeviceContainer.getInstance();
                container.emptyContainer();
            } catch (Exception ex){
                String msg = PluginUtil.composePluginExecResult(false, false, "Error occured while disconnecting device.", ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
                callback.sendResult(PluginResult.Status.ERROR, msg, false);
            }

            String msg = PluginUtil.composePluginExecResult(true, false, "User authentication failed", ETrackerStatusCode.USER_AUTHORIZATION_FAILURE);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);

        }

        synchronized (lock) {
            isResponseProcessing = false;
        }
    }

    @Override
    public void onDataTransmitted(TIOConnection tioConnection, int i, int i1) {

    }

    @Override
    public void onReadRemoteRssi(TIOConnection tioConnection, int i, int i1) {

    }

    @Override
    public void onLocalUARTMtuSizeUpdated(TIOConnection tioConnection, int i) {

    }

    @Override
    public void onRemoteUARTMtuSizeUpdated(TIOConnection tioConnection, int i) {

    }

    // endregion

    // region Timer event handler

    @Override
    public void run() {
        int responseWatchLimit = 3;

        synchronized (lock) {
            if (isResponseProcessing)
                return;
        }

        responseWatchCounter++;
        if (responseWatchCounter > responseWatchLimit) {
            stopTimer();
            if (isAuthenticated) {
                String msg = PluginUtil.composePluginExecResult(true, true, "User authenticated", ETrackerStatusCode.USER_AUTHORIZATION_SUCCESS);
                callback.sendResult(PluginResult.Status.OK, msg, false);
            } else {
                String msg = "Response timeout occurred";
                msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.RESPONSE_TIMEOUT);
                callback.sendResult(PluginResult.Status.ERROR, msg, false);
            }
        }
    }

    // endregion

    // region Private methods

    private byte[] getAuthDataPacket(String password) {
        byte[] dataPckt = new byte[20];

        int dataLength = password.getBytes().length;

        dataPckt[0] = (byte) ETrackerCommand.CMD_AUTHENTICATE_USER;
        dataPckt[1] = (byte) ((dataLength >> 8) & 0XFF);
        dataPckt[2] = (byte) (dataLength & 0XFF);

        byte[] asciidata = password.getBytes(StandardCharsets.UTF_8);
        for (int i = 3; i < dataLength + 3; i++) {
            dataPckt[i] = asciidata[i - 3];
        }

        return dataPckt;
    }

    private void stopTimer() {

        if (timer != null) {
            timer.cancel();
        }
    }

    // endregion
}
