package com.mti.eTracker2CommsPlugin;

import android.util.Log;

import java.io.IOException;
import java.lang.*;

import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class TemporaryOverrideController extends TimerTask implements TIOConnectionCallback {

    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private final int sizePerPacket = 128;
    private int packetCount;
    private int pcktDispatchCount;
    private byte[] overrideData;
    private boolean isWaitingForACK;
    private ReentrantReadWriteLock lock;
    private int responseWatchCounter;
    private static final String TAG = "Temporary Override";
    private int pcktTransmitAttemptCount;
    private static final int timerExecInterval = 5 * 1000;

    enum ACK_FILE_STATUS {
        Success(0), Failure(1);

        private int numVal;

        ACK_FILE_STATUS(int numVal) {
            this.numVal = numVal;
        }
    }

    // endregion

    // region Constructor

    TemporaryOverrideController() {

        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();
        this.connection.setListener(this);

        timer = new Timer(true);
        lock = new ReentrantReadWriteLock();
    }

    // endregion

    // region Public methods

    public void execute(String fileData, int slot, int expires, IPluginResult callback) {
        try {

            this.callback = callback;

            if (fileData == null || fileData.isEmpty()) {
                String msg = PluginUtil.composePluginExecResult(true, false, "Invalid data received",
                        ETrackerStatusCode.INVALID_REQUEST_DATA);
                callback.sendResult(PluginResult.Status.ERROR, msg, false);
                return;
            }

            overrideData = fileData.getBytes();
            packetCount = getFilePacketsCount(overrideData);
            int size = overrideData.length;
            byte[] reqPacket = getFileTransferReqPacket(packetCount, size, slot, expires);
            LOG.d(TAG, "Temporary override request packet: " + Arrays.toString(reqPacket));
            connection.transmit(reqPacket);
            isWaitingForACK = true;
            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);

        } catch (Exception ex) {
            connection.setListener(null);
            String msg = PluginUtil.composePluginExecResult(true, false, "Error occurred during config file transfer",
                    ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
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
        stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Underlying connection failure",
                ETrackerStatusCode.BLE_CONNECTION_FAILURE);
    }

    @Override
    public void onDisconnected(TIOConnection tioConnection, String errorMsg) {
        stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Underlying connection got disconnect",
                ETrackerStatusCode.BLE_DISCONNECTED);
    }

    @Override
    public void onDataReceived(TIOConnection tioConnection, byte[] respData) {
        try {
            if (respData == null) {
                stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Invalid response data received",
                        ETrackerStatusCode.INVALID_RESPONSE_DATA);
                return;
            }

            LOG.d(TAG, "Response Data: " + Arrays.toString(respData));
            lock.writeLock().lock();
            try {
                responseWatchCounter = 0;
                isWaitingForACK = false;
                processResponse(respData);
            } finally {
                lock.writeLock().unlock();
            }
        } catch (Exception ex) {
            LOG.d(TAG, "PLugin response handle error. " + ex.toString());
            stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false,
                    "Error occurred while processing config file transfer response",
                    ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
        }
    }

    @Override
    public void onDataTransmitted(TIOConnection tioConnection, int status, int bytesWritten) {
        Log.d(TAG, "Request sent for request packet: " + pcktDispatchCount + " Transmission status: " + status
                + " Bytes written: " + bytesWritten);
    }

    @Override
    public void onReadRemoteRssi(TIOConnection tioConnection, int status, int rssi) {
    }

    @Override
    public void onLocalUARTMtuSizeUpdated(TIOConnection tioConnection, int mtuSize) {
    }

    @Override
    public void onRemoteUARTMtuSizeUpdated(TIOConnection tioConnection, int mtuSize) {
    }

    // endregion

    // region Timer event handler

    @Override
    public void run() {

        int responseWatchLimit = 3;

        if (lock.isWriteLocked())
            return;

        if (isWaitingForACK) {
            responseWatchCounter++;
            if (responseWatchCounter > responseWatchLimit) {
                stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Response timeout occurred",
                        ETrackerStatusCode.RESPONSE_TIMEOUT);
            }
        }
    }

    // endregion

    // region Private methods

    private int getFilePacketsCount(byte[] overrideBytesData) {
        int count;

        int dataLength = overrideBytesData.length;

        if (dataLength % sizePerPacket == 0)
            count = dataLength / sizePerPacket;
        else
            count = (dataLength / sizePerPacket) + 1;

        return count;
    }

    private byte[] getFileTransferReqPacket(int packetCount, int dataSize, int slot, int expires) {

        byte[] reqPckt = new byte[17];

        reqPckt[0] = ETrackerCommand.CMD_TEMPORARY_OVERRIDE;
        reqPckt[1] = 0;
        reqPckt[2] = (byte) 14;
        reqPckt[3] = ETrackerCommand.CMD_TEMPORARY_OVERRIDE_START_TRANSFER;
        reqPckt[4] = (byte) ((packetCount >> 8) & 0XFF);
        reqPckt[5] = (byte) (packetCount & 0XFF);

        reqPckt[6] = (byte) ((dataSize & 0XFF000000) >> 24);
        reqPckt[7] = (byte) ((dataSize & 0X00FF0000) >> 16);
        reqPckt[8] = (byte) ((dataSize & 0X0000FF00) >> 8);
        reqPckt[9] = (byte) ((dataSize & 0X000000FF) >> 0);

        reqPckt[10] = (byte) slot; // slot 9 or 10
        reqPckt[11] = 0; // timeout in seconds
        reqPckt[12] = (byte) expires; // timeout in seconds
        reqPckt[13] = 0;
        reqPckt[14] = 0;
        reqPckt[15] = 0;
        reqPckt[16] = 0;

        return reqPckt;
    }

    private void processResponse(byte[] respData) throws IOException, InterruptedException {

        int cmdId = respData[3];

        if (cmdId == ETrackerCommand.CMD_TEMPORARY_OVERRIDE_READY_TO_RECEIVE) {
            LOG.d(TAG, "Ready to receive response: " + Arrays.toString(respData));
            byte[] pcktData = createFileDataPacket();
            isWaitingForACK = true;
            connection.transmit(pcktData);
            pcktDispatchCount++;

        } else if (cmdId == ETrackerCommand.CMD_TEMPORARY_OVERRIDE_FILE_ACK_PACKET) {
            LOG.d(TAG, "ACK for packet number : " + pcktDispatchCount + " #### Response: " + Arrays.toString(respData));
            handleConfigUpdateAck(respData);
        }
    }

    private void handleConfigUpdateAck(byte[] respData) throws IOException, InterruptedException {

        int pcktTransmitAttemptLimit = 2;

        int pcktTransmitStatus = respData[4];

        if (pcktTransmitStatus == ACK_FILE_STATUS.Success.numVal) {

            Log.d(TAG, "Data packet: " + pcktDispatchCount + " validation SUCCESS");
            pcktTransmitAttemptCount = 0;

            if (isAllPacketSent()) {
                Log.d(TAG, "All packets sent");
                stopFileTransferAndSendResult(PluginResult.Status.OK, true, true, "Override JSON uploaded successfully",
                        ETrackerStatusCode.CONFIG_FILE_UPLOADED);
            } else {
                byte[] pcktData = createFileDataPacket();
                connection.transmit(pcktData);
                isWaitingForACK = true;
                pcktDispatchCount++;
            }
        } else {

            Log.d(TAG, "Config packet: " + pcktDispatchCount + " validation FAILED ");

            if (pcktTransmitAttemptCount < pcktTransmitAttemptLimit) {
                pcktDispatchCount--;
                byte[] pcktData = createFileDataPacket();
                connection.transmit(pcktData);
                pcktTransmitAttemptCount++;
            } else {
                Log.d(TAG, "Packet transfer attempt count exceeded limit. Packet number " + pcktDispatchCount);
                stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false,
                        "Packet transfer attempt count exceeded limit. Packet number: " + pcktDispatchCount,
                        ETrackerStatusCode.CONFIG_FILE_PACKET_TRANSFER_ATTEMPT_LIMIT_EXCEEDED);
            }
        }
    }

    private byte[] createFileDataPacket() {
        int dataLen;
        int sizeTransmitted = (pcktDispatchCount * sizePerPacket);
        boolean flag = isLastPacket();
        if (flag) {
            dataLen = overrideData.length - sizeTransmitted + 3;
        } else {
            dataLen = sizePerPacket + 3;
        }

        Log.d(TAG,
                "Size transmitted: " + sizeTransmitted + " ## Data length: " + dataLen + " ## is last packet: " + flag);

        int packetNum = pcktDispatchCount + 1;

        byte[] pckt = new byte[dataLen + 3];

        pckt[0] = ETrackerCommand.CMD_TEMPORARY_OVERRIDE;
        pckt[1] = (byte) ((dataLen >> 8) & 0XFF);
        pckt[2] = (byte) (dataLen & 0XFF);
        pckt[3] = ETrackerCommand.CMD_TEMPORARY_OVERRIDE_SEND_DATA_FILE_PACKET;
        pckt[4] = (byte) ((packetNum >> 8) & 0XFF);
        pckt[5] = (byte) ((packetNum) & 0XFF);

        int currentIndex = sizeTransmitted;
        for (int i = 6; i < pckt.length; i++, currentIndex++) {
            pckt[i] = overrideData[currentIndex];
        }

        Log.d(TAG, "Data packet number: " + packetNum + " ### packet data: " + Arrays.toString(pckt));
        Log.d("Override JSON: ", new String(pckt));

        return pckt;
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private boolean isAllPacketSent() {
        Log.d("Check all packet sent ",
                "Packet dispatch count: " + pcktDispatchCount + " ## Packet count: " + packetCount);
        return pcktDispatchCount == packetCount;
    }

    private boolean isLastPacket() {
        return (pcktDispatchCount + 1) == packetCount;
    }

    private void stopFileTransferAndSendResult(PluginResult.Status pluginExecStatus, boolean isConnected,
            boolean isActionSuccess, String msg, int statusCode) {
        connection.setListener(null);
        stopTimer();
        String result = PluginUtil.composePluginExecResult(isConnected, isActionSuccess, msg, statusCode);
        callback.sendResult(pluginExecStatus, result, false);
    }

    // endregion
}
