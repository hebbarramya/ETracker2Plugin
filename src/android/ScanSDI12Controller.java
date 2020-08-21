package com.mti.eTracker2CommsPlugin;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import org.apache.cordova.PluginResult;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.gson.*;

import java.io.IOException;

import org.apache.cordova.LOG;

import android.util.Log;

class ScanSDI12Controller extends TimerTask implements TIOConnectionCallback {

    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private ReentrantReadWriteLock lock;
    private boolean isWaitingForBleResponse;
    private int responseWaitCounter;
    private static final int timerExecInterval = 8 * 10000;
    private int expectedPcktCount = 0;
    private int expectedDataSize = 0;
    private int expectedPacketSize = 0;
    private int connectedSensorCount = 0;
    private int currentPcktSequence = 0;
    private byte[] dataBuffer;
    private byte[] finalData;
    private int bufferIndexPointer = 0;
    private int finalDataIndexPointer = 0;
    private String TAG = "Scan SDI_12";
    private boolean isDataTransferStarted = false;

    // endregion

    // region constructor
    ScanSDI12Controller() {
        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();
        this.connection.setListener(this);

        timer = new Timer(true);
        lock = new ReentrantReadWriteLock();
    }
    // endregion

    // region public method
    public void execute(int channel, IPluginResult callback) {
        try {
            this.callback = callback;
            byte[] reqDataPacket = getScanSDI12ReqPacket(channel);
            LOG.d(TAG, " Scan SDI12 request packet: " + Arrays.toString(reqDataPacket));
            connection.transmit(reqDataPacket);
            Log.d(TAG, "Scan SDI12 request packet sent");
            isWaitingForBleResponse = true;
            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);

        } catch (Exception ex) {
            connection.setListener(null);
            stopTimer();
            String msg = PluginUtil.composePluginExecResult(true, false,
                    "Error occurred during config file transfer. " + ex.toString(),
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

    private byte[] getScanSDI12ReqPacket(int channel) {

        byte[] reqPckt = new byte[5];

        reqPckt[0] = ETrackerCommand.CMD_SCAN_SDI12;
        reqPckt[1] = 0;
        reqPckt[2] = (byte) 2;
        reqPckt[3] = ETrackerCommand.CMD_SCAN_SDI12_REQUEST;
        reqPckt[4] = (byte) channel;

        return reqPckt;

    }

    private void processResponse(byte[] respData) throws IOException {
        if (!isDataTransferStarted) {
            if (respData[3] == ETrackerCommand.CMD_SDI12_PROGRESS_ACK) {
                int sensorCount = respData[4];

                LOG.d(TAG, "SDI 12 Progress ACK Packet Received: " + Arrays.toString(respData));

                // inform ui of this progress since command takes a long time
                ScanSDI12_Response sdi_response = new ScanSDI12_Response(String.valueOf(sensorCount), true);
                Gson gson = new Gson();
                String msg = gson.toJson(sdi_response);
                String result = PluginUtil.composePluginExecResult(true, true, msg,
                        ETrackerStatusCode.SDI12_CMD_PROGRESS_UPDATE);
                callback.sendResult(PluginResult.Status.OK, result, true);

            } else if (respData[3] == ETrackerCommand.CMD_SDI12_INFO) {
                LOG.d(TAG, "SDI 12 Info Packet Received: " + Arrays.toString(respData));
                expectedPcktCount = getPacketCount(respData);
                expectedDataSize = getFinalDataSize(respData);
                connectedSensorCount = respData[10];
                LOG.d(TAG, "Expected Packet Count: " + expectedPcktCount);
                LOG.d(TAG, "Expected Data Size: " + expectedDataSize);
                LOG.d(TAG, "Connected sensor Count: " + connectedSensorCount);
                finalData = new byte[expectedDataSize];
                sendInfoPacketAck(true);
                if(connectedSensorCount == 0){
                    this.connection.setListener(null);
                    ScanSDI12_Response sdi_response = new ScanSDI12_Response(String.valueOf(0), false);
                    Gson gson = new Gson();
                    String msg = gson.toJson(sdi_response);
                    String result = PluginUtil.composePluginExecResult(true, true, msg,
                            ETrackerStatusCode.SDI12_CMD_PROGRESS_UPDATE);
                    callback.sendResult(PluginResult.Status.OK, result, false);
                }
            } else if (respData[3] == ETrackerCommand.CMD_SDI12_DATA_RECEIVED) {
                isDataTransferStarted = true;

                LOG.d(TAG, "Data packet received. Sequence number: " + currentPcktSequence + " Packet size: "
                        + expectedPacketSize);
                LOG.d(TAG, "Data packet: " + Arrays.toString(respData));

                currentPcktSequence = getPacketSequenceNumber(respData);
                expectedPacketSize = getCurrentPacketSize(respData);
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
                LOG.d(TAG, "Final Data array: " + Arrays.toString(finalData));
                LOG.d(TAG, "Data array length: " + finalData.length);
                isWaitingForBleResponse = false;
                Gson gson = new Gson();
                SDI12_Command_Response[] scan_sdi_resp = parseSDI12CommandData();
                String serialized_sdi_response = gson.toJson(scan_sdi_resp);
                ScanSDI12_Response response = new ScanSDI12_Response(serialized_sdi_response, false);
                String msg = gson.toJson(response);

                LOG.d(TAG, "Serialized Scan SDI12 Cmd Response: " + serialized_sdi_response);

                stopProcessAndSendResult(PluginResult.Status.OK, true, true, msg,
                        ETrackerStatusCode.SDI12_CMD_READ_SUCCESS);
            }
        }
    }

    private SDI12_Command_Response parseSingleSensorDetails(String sdi_resp) {
        SDI12_Command_Response sdi12_cmd_response = new SDI12_Command_Response();

        sdi12_cmd_response.SensorAddress = sdi_resp.substring(0, 1);
        sdi12_cmd_response.SDI12Compatibility = sdi_resp.substring(1, 3);
        sdi12_cmd_response.CompanyName = sdi_resp.substring(3, 11);
        sdi12_cmd_response.SensorModelNumber = sdi_resp.substring(11, 17);
        sdi12_cmd_response.SensorVersion = sdi_resp.substring(17, 20);
        sdi12_cmd_response.SerialNumber = sdi_resp.substring(20);

        return sdi12_cmd_response;
    }

    private SDI12_Command_Response[] parseSDI12CommandData() {
        SDI12_Command_Response scan_sdi_resp[] = new SDI12_Command_Response[connectedSensorCount];
//        int index = 0; int individualsensorlen;
//        try{
//            for (int i = 0; i < connectedSensorCount; i++) {
//                individualsensorlen = finalData[index];
//                index++;
//                byte[] single_sensor_details = new byte[individualsensorlen];
//                for (int j = 0; j < individualsensorlen; j++, index++) {
//                    single_sensor_details[j] = finalData[index];
//                }
//                SDI12_Command_Response sdi_response = parseSingleSensorDetails(new String(single_sensor_details));
//                scan_sdi_resp[i] = sdi_response;
//            }
//        }
//        catch (Exception ex){
//            LOG.d(TAG, "Error occurred while parsing data: " + ex.toString());
//            stopProcessAndSendResult(PluginResult.Status.ERROR, true, false, "Plugin internal error. " + ex.toString(),
//                    ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
//        }
        String sdi12_response = new String(finalData);
        String sdi12_resArr[] = sdi12_response.split("!");
        for (int i=1; i<connectedSensorCount + 1; i++){
            SDI12_Command_Response sdi_response = parseSingleSensorDetails(sdi12_resArr[i]);
            scan_sdi_resp[i-1] = sdi_response;
        }
        LOG.d(TAG, "Final Response: " + sdi12_response);
        return scan_sdi_resp;
    }

    private void sendInfoPacketAck(boolean isValidInfoPacket) throws IOException {
        byte[] ackPckt = new byte[5];

        ackPckt[0] = ETrackerCommand.CMD_SCAN_SDI12;
        ackPckt[1] = 0;
        ackPckt[2] = (byte) 2;
        ackPckt[3] = ETrackerCommand.CMD_SCAN_SDI12_INFO_ACK;
        ackPckt[4] = isValidInfoPacket ? (byte) 0 : (byte) 1;

        isWaitingForBleResponse = true;

        connection.transmit(ackPckt);

        LOG.d(TAG, "SDI 12 Info Packet ACK sent. " + Arrays.toString(ackPckt));
    }

    private void sendPcktReceivedAck(boolean isValidPckt, int pcktSeqNum) throws IOException {
        byte[] ackPckt = new byte[7];

        ackPckt[0] = ETrackerCommand.CMD_SCAN_SDI12;
        ackPckt[1] = 0;
        ackPckt[2] = 4;
        ackPckt[3] = ETrackerCommand.CMD_SDI12_DATA_PACKET_RECEIVED_ACK;
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
        connection.setListener(null);
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

    private boolean isEntireDataReceived() {
        return expectedPcktCount == currentPcktSequence; // && expectedConfigSize == config.length
    }

    private boolean isEntirePacketReceived() {
        LOG.d(TAG, "Buffer index: " + bufferIndexPointer + " Expected packet size: " + expectedPacketSize);
        return dataBuffer != null && bufferIndexPointer == expectedPacketSize - 3;
    }

    // endregion

}
