package com.mti.eTracker2CommsPlugin;

import java.io.IOException;
import java.lang.*;
import java.nio.charset.StandardCharsets;

import android.util.Log;

import org.apache.cordova.PluginResult;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.util.Arrays;

public class SetPasswordController extends TimerTask implements TIOConnectionCallback {
    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private int responseWatchCounter;
    private ReentrantReadWriteLock lock;
    private static final int timerExecInterval = 5 * 10000;
    private static final String TAG = "Set Password";

    enum BLE_RESPONSE_STATUS {
        Success(0), Failure(1);

        private int numVal;

        BLE_RESPONSE_STATUS(int numVal) {
            this.numVal = numVal;
        }
    }

    // endregion

    // region Constructor

    public SetPasswordController() {

        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();
        this.connection.setListener(this);

        timer = new Timer(true);
        lock = new ReentrantReadWriteLock();
    }

    // endregion

    // region Public method

    public void execute(String password, IPluginResult callback) throws IOException {

        this.callback = callback;

        try {
            boolean isValidPassword = validatePassword(password);
            if(isValidPassword) {
                int len = passwordlen(password);
                byte[] requestPacket = prepareSetPassword(password, len);
                connection.transmit(requestPacket);
                Log.d(TAG, "Set Password Request packet sent-" + Arrays.toString(requestPacket));
            } else {
                String msg = PluginUtil.composePluginExecResult(true, false, "Password length has crossed length 20! Please try again.", ETrackerStatusCode.SET_PASSWORD_LENGTH_EXCEEDED);
                callback.sendResult(PluginResult.Status.ERROR, msg, false);
            }

            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);
        } catch (Exception ex) {
            String msg = "Error occurred while creating set password request packet.";
            msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
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

        Log.d(TAG, "Set Password Ack" + Arrays.toString(respData));//[3, 0, 1, 0]

        String msg = "";
        if (respData[3] == BLE_RESPONSE_STATUS.Success.numVal) {
            msg = PluginUtil.composePluginExecResult(true, true, "Password set successfully.",
                    ETrackerStatusCode.SET_PASSWORD_SUCCESS);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        } else if (respData[3] == BLE_RESPONSE_STATUS.Failure.numVal) {
            msg = PluginUtil.composePluginExecResult(true, false, "Failed to set password.",
                    ETrackerStatusCode.SET_PASSWORD_FAILURE);
            callback.sendResult(PluginResult.Status.OK, msg, false);
        } else {
            msg = PluginUtil.composePluginExecResult(true, false, "Invalid Response.",
                    ETrackerStatusCode.INVALID_RESPONSE_DATA);
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

    private void stopTimer() {

        if (timer != null) {
            timer.cancel();
        }
    }

    private boolean validatePassword(String pwd) {
        int passwlen = passwordlen(pwd);
        if (passwlen > 20 || passwlen == 0)
            return false;
        else
            return true;
    }

    private int passwordlen(String password) {
        int passwordlen = password.getBytes().length;
        return passwordlen;
    }

    private byte[] prepareSetPassword(String password, int passwordlen) {
        byte[] passwordData = new byte[passwordlen + 3];
        passwordData[0] = ETrackerCommand.CMD_SET_PASSWORD;//03
        passwordData[1] = (byte) ((passwordlen >> 8) & 0XFF);//0
        passwordData[2] = (byte) (passwordlen & 0XFF);//20

        byte[] password_asciiData = password.getBytes(StandardCharsets.UTF_8);
        for (int i = 3; i < passwordlen+3; i++) {
            passwordData[i] = password_asciiData[i - 3];
        }
        return passwordData;
    }
    // endregion
}

