package com.mti.eTracker2CommsPlugin;

import android.util.Log;

import com.google.gson.Gson;
import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SensorReadingsController extends TimerTask implements TIOConnectionCallback {
    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private ReentrantReadWriteLock lock;
    private boolean isWaitingForBleResponse;
    private int responseWaitCounter;
    private static final int timerExecInterval = 5 * 10000;
    private int expectedPcktCount = 0;
    private int expectedDataSize = 0;
    private int expectedPacketSize = 0;
    private int currentPcktSequence = 0;
    private byte[] dataBuffer;
    private byte[] finalData;
    private int bufferIndexPointer = 0;
    private int finalDataIndexPointer = 0;
    private String TAG = "Current Sensor Readings";
    private boolean isDataTransferStarted = false;

    // endregion

    SensorReadingsController() {
        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();
        this.connection.setListener(this);

        timer = new Timer(true);
        lock = new ReentrantReadWriteLock();
    }

    public void execute(IPluginResult callback) {
        try {
            this.callback = callback;
            byte[] reqDataPacket = getRealTimeSensorReadingReqPackt();
            LOG.d(TAG, "Real Time Sensor Readings Request packet: " + Arrays.toString(reqDataPacket));
            connection.transmit(reqDataPacket);
            Log.d(TAG, "Real Time Sensor Readings Request packet sent");
            isWaitingForBleResponse = true;
            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);

        } catch (Exception ex) {
            detachConnectionEventHandlers();
            stopTimer();
            String msg = PluginUtil.composePluginExecResult(true, false,
                    "Error occurred while fetching sensor readings. " + ex.toString(),
                    ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    // region Peripheral connection event handlers

    @Override
    public void onConnected(TIOConnection tioConnection) {
    }

    @Override
    public void onConnectFailed(TIOConnection tioConnection, String errorMsg) {
        stopProcessAndSendResult(PluginResult.Status.ERROR, true, false, "Underlying connection failure",
                ETrackerStatusCode.BLE_CONNECTION_FAILURE);
    }

    @Override
    public void onDisconnected(TIOConnection tioConnection, String errorMsg) {
        stopProcessAndSendResult(PluginResult.Status.ERROR, true, false, "Underlying connection got disconnect",
                ETrackerStatusCode.BLE_DISCONNECTED);
    }

    @Override
    public void onDataReceived(TIOConnection tioConnection, byte[] respData) {

        try {
            if (respData == null || respData.length == 0) {
                Log.d(TAG, "Invalid response received from BLE");
                stopProcessAndSendResult(PluginResult.Status.ERROR, true, false,
                        "Invalid response received from BLE device", ETrackerStatusCode.INVALID_RESPONSE_DATA);

            } else {
                lock.writeLock().lock();
                try {
                    resetResponseWaitCounter();
                    isWaitingForBleResponse = false;
                    processResponse(respData);

                } finally {
                    lock.writeLock().unlock();
                }
            }
        } catch (Exception ex) {
            stopProcessAndSendResult(PluginResult.Status.ERROR, true, false, "Plugin internal error. " + ex.toString(),
                    ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
        }
    }

    @Override
    public void onDataTransmitted(TIOConnection tioConnection, int status, int bytesWritten) {
        LOG.d(TAG, "Data transmitted. Status: " + status + " Bytes written: " + bytesWritten);
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
        int responseWaitLimit = 3;

        if (lock.isWriteLocked())
            return;

        if (isWaitingForBleResponse) {
            responseWaitCounter++;
            if (responseWaitCounter > responseWaitLimit) {
                stopProcessAndSendResult(PluginResult.Status.ERROR, true, false, "Response timeout occurred",
                        ETrackerStatusCode.RESPONSE_TIMEOUT);
            }
        }
    }

    // endregion

    // region Private methods

    private byte[] getRealTimeSensorReadingReqPackt() {

        byte[] reqPckt = new byte[4];

        reqPckt[0] = ETrackerCommand.CMD_REPORT_REAL_TIME_SENSOR;
        reqPckt[1] = 0;
        reqPckt[2] = 1;
        reqPckt[3] = (byte) REAL_TIME_SENSOR_SUBMCMDID.REAL_TIME_SENSOR_REQUEST_SUBCMD_ID.SubCmdId;

        return reqPckt;

    }

    private void processResponse(byte[] respData) throws IOException {
        if (!isDataTransferStarted) {
            if (respData[3] == REAL_TIME_SENSOR_SUBMCMDID.REAL_TIME_SENSOR_PROGRESS_ACK_SUBCMD_ID.SubCmdId) {
                int sensornumber = respData[5];
                int totalSensorCount = respData[4];
                LOG.d(TAG, "Real Time Sensor Readings Progress ACK Packet Received: " + Arrays.toString(respData));

                // inform ui of the progress since command takes a long time
                ScanSDI12_Response sdi_response = new ScanSDI12_Response(String.valueOf(sensornumber) + "," + String.valueOf(totalSensorCount), true);
                Gson gson = new Gson();
                String msg = gson.toJson(sdi_response);
                String result = PluginUtil.composePluginExecResult(true, true, msg,
                        ETrackerStatusCode.READ_SENSOR_READINGS_PROGRESS_UPDATE);
                callback.sendResult(PluginResult.Status.OK, result, true);

            } else if (respData[3] == REAL_TIME_SENSOR_SUBMCMDID.REAL_TIME_SENSOR_INFO_PACKET_SUBCMD_ID.SubCmdId) {
                LOG.d(TAG, "Real Time Sensor Readings Info Packet Received: " + Arrays.toString(respData));
                expectedPcktCount = getPacketCount(respData);
                expectedDataSize = getFinalDataSize(respData);
                finalData = new byte[expectedDataSize];
                sendInfoPacketAck(true);
            } else if (respData[3] == REAL_TIME_SENSOR_SUBMCMDID.REAL_TIME_SENSOR_DATA_PACKET_SUBCMD_ID.SubCmdId) {
                isDataTransferStarted = true;
                currentPcktSequence = getPacketSequenceNumber(respData);
                expectedPacketSize = getCurrentPacketSize(respData);

                LOG.d(TAG, "Data packet received. Sequence number: " + currentPcktSequence + " Packet size: "
                        + expectedPacketSize);
                LOG.d(TAG, "Data packet: " + Arrays.toString(respData));

                dataBuffer = new byte[expectedPacketSize - 3];
                appendDataIntoBuffer(respData, 6);
                isWaitingForBleResponse = true;
            }

        } else {
            if (respData.length <= 20)
                LOG.d(TAG, "Data chunk received. Packet length: " + respData.length + " ### packet data: "
                        + Arrays.toString(respData));
            else
                LOG.d(TAG, "Data packet received. Packet length: " + respData.length + " ### packet data: "
                        + Arrays.toString(respData));

            appendDataIntoBuffer(respData, 0);
            isWaitingForBleResponse = true;
        }

        if (isEntirePacketReceived()) {

            LOG.d(TAG, "Entire data packet received. Packet sequence number: " + currentPcktSequence);
            bufferIndexPointer = 0;
            isDataTransferStarted = false;
            flushFinalDataBuffer();
            sendPcktReceivedAck(true, currentPcktSequence);

            if (isEntireDataReceived()) {
                isWaitingForBleResponse = false;
                Gson gson = new Gson();

                String sdi12_response = new String(finalData);
                ScanSDI12_Response response = new ScanSDI12_Response(sdi12_response, false);
                String msg = gson.toJson(response);

                LOG.d(TAG, "Data array length: " + finalData.length);
                LOG.d(TAG, "Real Time Sensor Readings Response: " + sdi12_response);

                stopProcessAndSendResult(PluginResult.Status.OK, true, true, msg,
                        ETrackerStatusCode.SDI12_CMD_READ_SUCCESS);
            }
        }
    }

    private void sendInfoPacketAck(boolean isValidInfoPacket) throws IOException {
        byte[] RealTimeSensorInfoACK = new byte[5];

        RealTimeSensorInfoACK[0] = ETrackerCommand.CMD_REPORT_REAL_TIME_SENSOR;
        RealTimeSensorInfoACK[1] = 0;
        RealTimeSensorInfoACK[2] = 2;
        RealTimeSensorInfoACK[3] = (byte) REAL_TIME_SENSOR_SUBMCMDID.REAL_TIME_SENSOR_INFO_PACKET_ACK_SUBCMD_ID.SubCmdId;
        RealTimeSensorInfoACK[4] = isValidInfoPacket ? (byte) 0 : (byte) 1;

        isWaitingForBleResponse = true;

        connection.transmit(RealTimeSensorInfoACK);

        LOG.d(TAG, "Real Time Sensor Readings Info Packet ACK sent. " + Arrays.toString(RealTimeSensorInfoACK));
    }

    private void sendPcktReceivedAck(boolean isValidPckt, int pcktSeqNum) throws IOException {
        byte[] ackPckt = new byte[7];

        ackPckt[0] = ETrackerCommand.CMD_REPORT_REAL_TIME_SENSOR;
        ackPckt[1] = 0;
        ackPckt[2] = 4;
        ackPckt[3] = (byte) REAL_TIME_SENSOR_SUBMCMDID.REAL_TIME_SENSOR_DATA_PACKET_ACK_SUBCMD_ID.SubCmdId;
        ackPckt[4] = isValidPckt ? (byte) 0 : (byte) 1;
        ackPckt[5] = pcktSeqNum > 127 ? (byte) pcktSeqNum : 0;
        ackPckt[6] = pcktSeqNum <= 127 ? (byte) pcktSeqNum : 0;

        isWaitingForBleResponse = true;

        connection.transmit(ackPckt);
        Log.d(TAG, "Data packet receive ACK sent" + Arrays.toString(ackPckt));
        LOG.d(TAG, "Data packet receive ACK sent for packet number: " + pcktSeqNum);
    }

    private void stopProcessAndSendResult(PluginResult.Status status, boolean isConnected, boolean isActionSuccess,
                                          String msg, int statusCode) {
        detachConnectionEventHandlers();
        stopTimer();
        String result = PluginUtil.composePluginExecResult(isConnected, isActionSuccess, msg, statusCode);
        callback.sendResult(status, result, false);
    }

    private void appendDataIntoBuffer(byte[] pcktChunk, int startIndex) {
        try {
            for (int index = startIndex; index < pcktChunk.length; index++) {
                dataBuffer[bufferIndexPointer++] = pcktChunk[index];
            }
        } catch (Exception ex) {
            LOG.d(TAG, "Error occurred while copying config into buffer. Error details: " + ex.toString());
        }
    }

    private void flushFinalDataBuffer() {
        try {
            for (int index = 0; index < dataBuffer.length; index++) {
                finalData[finalDataIndexPointer++] = dataBuffer[index];
            }
        } catch (Exception ex) {
            LOG.d(TAG, "Error occurred while flushing data buffer. Error details: " + ex.toString());
        }
    }

    private int getPacketSequenceNumber(byte[] respData) {
        int seqNum = ((respData[4] & 0xff) << 8) | (respData[5] & 0xff);
        return seqNum;
    }

    private int getCurrentPacketSize(byte[] respData) {
        int count = ((respData[1] & 0xff) << 8) | (respData[2] & 0xff);
        return count;
    }

    private int getPacketCount(byte[] respData) {
        int count = ((respData[4] & 0xff) << 8) | (respData[5] & 0xff);
        return count;
    }

    private int getFinalDataSize(byte[] respData) {

        int fileSize = ((respData[6] & 0xff) << 24) | ((respData[7] & 0xff) << 16) | ((respData[8] & 0xff) << 8)
                | (respData[9] & 0xff);
        return fileSize;
    }

    private void resetResponseWaitCounter() {
        responseWaitCounter = 0;
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private void detachConnectionEventHandlers() {
        connection.setListener(null);
    }

    private boolean isEntireDataReceived() {
        return expectedPcktCount == currentPcktSequence; // && expectedConfigSize == config.length
    }

    private boolean isEntirePacketReceived() {
        LOG.d(TAG, "Buffer index: " + bufferIndexPointer + " Expected packet size: " + expectedPacketSize);
        return dataBuffer != null && bufferIndexPointer == expectedPacketSize - 3;
    }

    // endregion
}

