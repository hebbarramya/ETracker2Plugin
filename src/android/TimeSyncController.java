package com.mti.eTracker2CommsPlugin;

import android.util.Log;

import java.io.IOException;
import java.lang.*;

import org.apache.cordova.PluginResult;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import java.sql.Timestamp;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class TimeSyncController extends TimerTask implements TIOConnectionCallback {

    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private int responseWatchCounter;
    private ReentrantReadWriteLock lock;
    private long syncedTimeValue;
    private static final int timerExecInterval = 5 * 10000;

    enum BLE_RESPONSE_STATUS {
        Success(0), Failure(1);

        private int numVal;

        BLE_RESPONSE_STATUS(int numVal) {
            this.numVal = numVal;
        }
    }

    // endregion

    // region Constructor

    public TimeSyncController() {

        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();
        this.connection.setListener(this);

        timer = new Timer(true);
        lock = new ReentrantReadWriteLock();
    }

    // endregion

    // region Public method

    public void execute(IPluginResult callback) throws IOException {

        this.callback = callback;

        try {
            byte[] data = getTimeSyncDataPackt();
            connection.transmit(data);

            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);
        } catch (Exception ex) {
            String msg = PluginUtil.composePluginExecResult(true, false,
                    "Error occurred during device time sync", ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
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
        String msg = PluginUtil.composePluginExecResult(false, false, "BLE disconnected",
                ETrackerStatusCode.BLE_DISCONNECTED);
        callback.sendResult(PluginResult.Status.OK, msg, false);
    }

    @Override
    public void onDataReceived(TIOConnection tioConnection, byte[] respData) {

        if (respData == null || (respData[1] == 0 && respData[2] == 0)) {
            stopTimer();
            String msg = "Invalid response received from peripheral device";
            msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.INVALID_RESPONSE_DATA);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
            return;
        }

        lock.writeLock().lock();

        stopTimer();

        String msg;
        Log.d("Time Sync", "Response ACK status" + respData[3]);
        if (respData[3] == BLE_RESPONSE_STATUS.Success.numVal) {
            msg = PluginUtil.composePluginExecResult(true, true, String.valueOf(this.syncedTimeValue),
                    ETrackerStatusCode.TIME_SYNC_SUCCESS);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        } else if (respData[3] == BLE_RESPONSE_STATUS.Failure.numVal) {
            msg = PluginUtil.composePluginExecResult(true, false, "Failed to sync time with device.",
                    ETrackerStatusCode.TIME_SYNC_FAILURE);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        } else {
            msg = PluginUtil.composePluginExecResult(true, false, "Invalid response received from peripheral device",
                    ETrackerStatusCode.INVALID_RESPONSE_DATA);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }

        lock.writeLock().unlock();
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
        // Check if responseWatchCounter is maxed out or not !
        // If maxed out stop timer.
        // Check for response data.
        // If complete response received, then return corresponding success response.
        // If complete response not received, then return corresponding error response.

        // If not maxed out
        // wait.

        if (lock.isWriteLocked())
            return;

        responseWatchCounter++;

        int responseWatchLimit = 3;
        if (responseWatchCounter > responseWatchLimit) {
            String msg = "Response timeout occurred";
            msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.RESPONSE_TIMEOUT);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    // endregion

    // region Private methods

    private byte[] getTimeSyncDataPackt() {
        byte[] dataPckt = new byte[7];
        this.syncedTimeValue = System.currentTimeMillis();
        int epochTime = (int) (this.syncedTimeValue / 1000);

        dataPckt[0] = ETrackerCommand.CMD_TIME_SYNC;
        dataPckt[1] = 0;
        dataPckt[2] = 4;

        byte[] asciiData = {(byte) (epochTime >> 24), (byte) (epochTime >> 16), (byte) (epochTime >> 8),
                (byte) epochTime};

        for (int i = 0; i < 4; i++) {
            dataPckt[i + 3] = asciiData[i];
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
