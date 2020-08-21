package com.mti.eTracker2CommsPlugin;

import android.util.Log;

import java.io.IOException;
import java.lang.*;

import org.apache.cordova.PluginResult;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import java.nio.charset.StandardCharsets;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class ApplyActionsController extends TimerTask implements TIOConnectionCallback {

    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private int responseWatchCounter;
    private ReentrantReadWriteLock lock;
    private boolean isResponseProcessing;
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

    public ApplyActionsController() {

        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();
        this.connection.setListener(this);

        timer = new Timer(true);
        lock = new ReentrantReadWriteLock();
    }

    // endregion

    // region Public method

    public void execute(int subCmdId, String data, IPluginResult callback) throws IOException {

        this.callback = callback;

        switch (subCmdId) {
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:

            try {
                if (isValidToggleData(data)) {
                    byte[] requestPacket = getToggleRequestPacket(subCmdId, data);
                    connection.transmit(requestPacket);

                    timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);
                } else {
                    sendErrorMsg("Invalid data");
                }

            } catch (Exception ex) {
                sendErrorMsg("Error occurred during apply actions");
            }
            break;

        case 7:
        case 8:

            try {
                if (isValidRSData(data)) {
                    byte[] requestPacket = getRSExecuteRequestPacket(subCmdId, data);
                    connection.transmit(requestPacket);

                    timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);
                } else {
                    sendErrorMsg("Invalid data");
                }
            } catch (Exception ex) {
                sendErrorMsg("Error occurred during apply actions");
            }
            break;
        case 9:
        case 10:

            try {
                if (isValidSDIData(data)) {
                    byte[] requestPacket = getSDIExecuteRequestPacket(subCmdId, data);
                    connection.transmit(requestPacket);

                    timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);
                } else {
                    sendErrorMsg("Invalid data");
                }

            } catch (Exception ex) {
                sendErrorMsg("Error occurred during apply actions");
            }

            break;

        default:
            sendErrorMsg("Invalid Sub Command Id");
            break;
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
        Log.d("Apply Actions", "Response ACK status" + respData[4]);
        if (respData[4] == BLE_RESPONSE_STATUS.Success.numVal) {
            msg = PluginUtil.composePluginExecResult(true, true, "Apply Success.", ETrackerStatusCode.APPLY_ACTIONS_SUCCESS);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        } else if (respData[4] == BLE_RESPONSE_STATUS.Failure.numVal) {
            msg = PluginUtil.composePluginExecResult(true, false, "Apply Failure.", ETrackerStatusCode.APPLY_ACTIONS_FAILURE);
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

    private void sendErrorMsg(String message) {
        String msg = PluginUtil.composePluginExecResult(true, false, message, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
        callback.sendResult(PluginResult.Status.ERROR, msg, false);
    }

    private byte[] getToggleRequestPacket(int subCmdId, String timeInMillis) {
        byte[] packet = new byte[6];
        byte[] data = timeInMillis.getBytes(StandardCharsets.UTF_8);
        packet[0] = ETrackerCommand.CMD_APPLY_ACTIONS;
        packet[1] = 0;
        packet[2] = 3;
        packet[3] = (byte) subCmdId;

        for (int i = 4; i < 6; i++) {
            packet[i] = data[i - 4];
        }

        return packet;

    }

    private byte[] getRSExecuteRequestPacket(int subCmdId, String data) {
        int datalength = data.getBytes().length;
        byte[] packet = new byte[4 + datalength];
        byte[] dataInBytes = data.getBytes(StandardCharsets.UTF_8);
        packet[0] = ETrackerCommand.CMD_APPLY_ACTIONS;
        packet[1] = 0;
        packet[2] = (byte) datalength;
        packet[3] = (byte) subCmdId;

        for (int i = 4; i < datalength + 4; i++) {
            packet[i] = dataInBytes[i - 4];
        }

        return packet;

    }

    private byte[] getSDIExecuteRequestPacket(int subCmdId, String data) {
        int datalength = data.getBytes().length;
        byte[] packet = new byte[4 + datalength];
        byte[] dataInBytes = data.getBytes(StandardCharsets.UTF_8);
        packet[0] = ETrackerCommand.CMD_APPLY_ACTIONS;
        packet[1] = 0;
        packet[2] = (byte) datalength;
        packet[3] = (byte)subCmdId;

        for (int i = 4; i < datalength + 4; i++) {
            packet[i] = dataInBytes[i - 4];
        }

        return packet;

    }

    private boolean isValidToggleData(String data) {
        int timeInMillis = Integer.parseInt(data);
        if (data == null || timeInMillis < 0 || timeInMillis > 65500)
            return false;
        return true;
    }

    private boolean isValidRSData(String data) {
        if (data == null || data.getBytes().length <= 0 || data.getBytes().length > 48)
            return false;
        return true;
    }

    private boolean isValidSDIData(String data) {
        if (data == null || data.getBytes().length <= 0 || data.getBytes().length > 98)
            return false;
        return true;
    }

    private void stopTimer() {

        if (timer != null) {
            timer.cancel();
        }
    }

    // endregion
}
