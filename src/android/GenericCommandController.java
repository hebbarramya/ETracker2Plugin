package com.mti.eTracker2CommsPlugin;

import android.util.Log;

import java.io.IOException;
import java.lang.*;
import java.nio.charset.StandardCharsets;

import org.apache.cordova.PluginResult;

import android.os.Environment;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class GenericCommandController extends TimerTask implements TIOConnectionCallback {

    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private String currentAction;
    private int responseWatchCounter;
    private ReentrantReadWriteLock lock;
    private static final int timerExecInterval = 5 * 10000;
    private static String TAG = "Generic Command Controller";

    enum BLE_RESPONSE_STATUS {
        Success(0), Failure(1);

        private int numVal;

        BLE_RESPONSE_STATUS(int numVal) {
            this.numVal = numVal;
        }
    }

    // endregion

    // region Constructor

    public GenericCommandController() {
        try {
            DeviceContainer container = DeviceContainer.getInstance();
            this.connection = container.getPeripheralConnection();
            this.connection.setListener(this);
        } catch (Exception ex) {
            String msg = "No device is connected.";
            msg = PluginUtil.composePluginExecResult(false, false, msg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }

        timer = new Timer(true);
        lock = new ReentrantReadWriteLock();
    }

    // endregion

    // region Public method

    public void execute(String action, int cmdId, IPluginResult callback) throws IOException {
        this.callback = callback;
        this.currentAction = action;
        try {
            byte[] requestPacket = {(byte) cmdId, 0, 0};
            connection.transmit(requestPacket);
            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);
        } catch (Exception ex) {
            stopTimer();
            String msg = "";
            msg = "Error occured while performing action. " + ex.toString();
            msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    public void execute(int cmdId, String uniqueCode, IPluginResult callback) throws IOException {
        this.callback = callback;

        try {
            byte[] requestPacket = getResetPasswordRequestPacket(cmdId, uniqueCode);
            connection.transmit(requestPacket);

            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);
        } catch (Exception ex) {
            String msg = "";
            msg = "Error occured while performing action. " + ex.toString();
            msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    public void execute(int cmdId, int networkCommMethod, IPluginResult callback) throws IOException {
        this.callback = callback;

        try {
            byte[] requestPacket = {(byte) cmdId, 0, 1, (byte) networkCommMethod};
            connection.transmit(requestPacket);
            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);
        } catch (Exception ex) {
            String msg = "";
            msg = "Error occured while performing action. " + ex.toString();
            msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    public void execute(String filename, int crc, IPluginResult callback) throws IOException {
        byte[] firmware_bytes = getFirmwareFileDataInBinary(filename);
        int crc_value = getCrcForTotalPayload(firmware_bytes, firmware_bytes.length);
        String msg = "";

        if (crc_value == crc) {
            msg = PluginUtil.composePluginExecResult(true, true, "CRC Check Success.", ETrackerStatusCode.CRC_CHECK_SUCCESS);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        } else {
            msg = PluginUtil.composePluginExecResult(true, false, "CRC Check Failure.", ETrackerStatusCode.CRC_CHECK_FAILURE);
            callback.sendResult(PluginResult.Status.OK, msg, false);
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

        String msg = "";
        if (respData[3] == BLE_RESPONSE_STATUS.Success.numVal) {

            msg = PluginUtil.composePluginExecResult(true, true, "Action Success",
                    ETrackerStatusCode.BLE_ACTION_SUCCESS);
            callback.sendResult(PluginResult.Status.OK, msg, false);

            if (currentAction.equals(ETrackerAction.SWITCH_TO_LM)) {
                LOG.d(TAG, "Emptying container and disposing resources.");
                DeviceContainer.emptyContainer();
                new ResourceCleaner().disposeResources(context);
            }
        } else if (respData[3] == BLE_RESPONSE_STATUS.Failure.numVal) {
            msg = PluginUtil.composePluginExecResult(true, false, "Action Failure",
                    ETrackerStatusCode.BLE_ACTION_FAILURE);
            callback.sendResult(PluginResult.Status.OK, msg, false);
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


        int responseWatchLimit = 3;
        if (lock.isWriteLocked())
            return;

        responseWatchCounter++;


        if (responseWatchCounter > responseWatchLimit) {
            stopTimer();
            String msg = "Response timeout occurred";
            msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.RESPONSE_TIMEOUT);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    // endregion

    // region Private methods

    private void stopTimer() {

        if (timer != null) {
            timer.cancel();
        }
    }

    private byte[] getResetPasswordRequestPacket(int cmdId, String unique_code) {
        byte[] packet = new byte[11];
        byte[] unique_code_bytes = hexStringToByteArray(unique_code);
        packet[0] = (byte) cmdId;
        packet[1] = 0;
        packet[2] = (byte) 8;

        for (int i = 3; i < 11; i++) {
            packet[i] = unique_code_bytes[i - 3];
        }

        Log.d("Reset Password", "resetPaswordReqPkt:" + Arrays.toString(packet));//[2, 0, 8, 14, -113, -3, 61, -98, -91, 2, 58]
        return packet;
    }

    private byte[] hexStringToByteArray(String unique_code) {
        int len = unique_code.length();
        byte[] resetdata = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                resetdata[i / 2] = (byte) ((Character.digit(unique_code.charAt(i), 16) << 4)
                        + Character.digit(unique_code.charAt(i + 1), 16));
            }
        } catch (Exception ex) {
            Log.d("Reset Password", "Error in converting 16 bit to 8 bit" + ex.toString());
        }
        return resetdata;
    }

    private int getCrcForTotalPayload(byte[] payload, int length) {
        // CRC Values
        int crc_buff = 0;
        int x16;
        int input;
        int bytes;
        int count;

        for (bytes = 0; bytes < length; bytes++) {
            input = payload[bytes];
            for (count = 0; count < 8; count++) {
                if ((crc_buff & 0x0001) != (input & 0x01))
                    x16 = 0x8408;
                else
                    x16 = 0x0000;
                // shift crc buffer
                crc_buff = crc_buff >> 1;
                // XOR in the x16 value
                crc_buff ^= x16;
                // shift input for next iteration
                input = input >> 1;

            }

        }
        System.out.println("CRC16-CCITT for Total Payld= " + Integer.toHexString(crc_buff));//f3b1
        return crc_buff;
    }

    private byte[] getFirmwareFileDataInBinary(String fileName) throws IOException {
        String root = Environment.getExternalStorageDirectory().toString();
        Log.d(TAG, "Path of Firmware files: " + root);
        File firmware_file = new File(root + "/eTracker2Firmware");

        File file = new File(firmware_file, fileName);
        InputStream inputStream = new FileInputStream(file);
        int OTADatalen = (int) file.length();

        byte[] firmwareBytes = new byte[OTADatalen];
        inputStream.read(firmwareBytes);
        inputStream.close();

        Log.d(TAG, "Binary File Array length" + firmwareBytes.length);//253392

        return firmwareBytes;
    }
    // endregion
}
