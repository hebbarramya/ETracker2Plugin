package com.mti.eTracker2CommsPlugin;

import java.io.IOException;
import java.lang.*;
import java.nio.charset.StandardCharsets;

import org.apache.cordova.PluginResult;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import java.util.Timer;
import java.util.TimerTask;

class SDI12PassthroughController extends TimerTask implements TIOConnectionCallback {

    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private int responseWatchCounter;
    private int respDataSize;
    private byte[] finalResponse;
    private int respPcktCount;
    private int currentRespDataIndex;
    private final Object lock;
    private boolean isResponseProcessing;

    // endregion

    // region Constructor

    public SDI12PassthroughController() {

        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();
        this.connection.setListener(this);

        timer = new Timer(true);
        lock = new Object();
    }

    // endregion

    // region Public method

    public void execute(String cmd, int channel, IPluginResult callback) throws IOException {

        this.callback = callback;

        try {
            if (cmd == null || cmd.isEmpty()) {
                String msg = PluginUtil.composePluginExecResult(true, false, "Invalid command received",
                        ETrackerStatusCode.INVALID_REQUEST_DATA);
                callback.sendResult(PluginResult.Status.ERROR, msg, false);
                return;
            }

            byte[] data = getSDI12DataPackt(cmd, channel);
            connection.transmit(data);

            timer.scheduleAtFixedRate(this, 60 * 1000, 60 * 1000);
        } catch (Exception ex) {
            String msg = PluginUtil.composePluginExecResult(true, false,
                    "Error occurred while processing SDI 12 command", ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
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

        synchronized (lock) {
            isResponseProcessing = true;
        }

        respPcktCount++;

        if (respPcktCount == 1) {
            int size = respData[1];
            if (size > 0) {
                respDataSize = size + respData[2];
            } else {
                respDataSize = respData[2];
            }

            finalResponse = new byte[respDataSize];

            for (int i = 3; i < respData.length; i++) {
                finalResponse[i - 3] = respData[i];
                currentRespDataIndex = i - 3;
            }

        } else {
            for (int i = 3, j = currentRespDataIndex + 1; i < respData.length; i++, j++) {
                finalResponse[j] = respData[i];
                currentRespDataIndex = j;
            }
        }

        if (isCompleteResponseReceived()) {
            stopTimer();
            String msg = new String(finalResponse, StandardCharsets.UTF_8);
            msg = PluginUtil.composePluginExecResult(true, true, msg, ETrackerStatusCode.COMMAND_RESPONSE_RECEIVED);
            callback.sendResult(PluginResult.Status.OK, msg, false);
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
        // Check if responseWatchCounter is maxed out or not !
        // If maxed out stop timer.
        // Check for response data.
        // If complete response received, then return corresponding success response.
        // If complete response not received, then return corresponding error response.

        // If not maxed out
        // wait.

        synchronized (lock) {
            if (isResponseProcessing)
                return;
        }
            stopTimer();
            if (isCompleteResponseReceived()) {
                String msg = new String(finalResponse, StandardCharsets.UTF_8);
                msg = PluginUtil.composePluginExecResult(true, true, msg, ETrackerStatusCode.COMMAND_RESPONSE_RECEIVED);
                callback.sendResult(PluginResult.Status.OK, msg, false);
            } else {
                String msg = "Response timeout occurred";
                msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.RESPONSE_TIMEOUT);
                callback.sendResult(PluginResult.Status.ERROR, msg, false);
            }
    }

    // endregion

    // region Private methods

    private byte[] getSDI12DataPackt(String text, int channel) {

        int dataLength = text.getBytes().length;
        int totalLength = dataLength + 4;// Appending length of channel(1/0)

        byte[] dataPckt = new byte[totalLength];

        dataPckt[0] = ETrackerCommand.CMD_SCAN_SDI_12_BUS;
        dataPckt[1] = (byte) ((dataLength >> 8) & 0XFF);
        dataPckt[2] = (byte) ((dataLength + 1) & 0XFF);
        dataPckt[3] = (byte) channel;

        byte[] asciiData = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 4; i < dataLength + 4; i++) {
            dataPckt[i] = asciiData[i - 4];
        }

        return dataPckt;
    }

    private void stopTimer() {

        if (timer != null) {
            timer.cancel();
        }
    }

    private boolean isCompleteResponseReceived() {
        return currentRespDataIndex + 1 == respDataSize;
    }

    // endregion
}
