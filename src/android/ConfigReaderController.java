package com.mti.eTracker2CommsPlugin;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import org.apache.cordova.PluginResult;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.io.IOException;

import org.apache.cordova.LOG;

import android.util.Log;

class ConfigReaderController extends TimerTask implements TIOConnectionCallback {

    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private ReentrantReadWriteLock lock;
    private boolean isWaitingForBleResponse;
    private int responseWaitCounter;
    private static final int timerExecInterval = 6 * 10000;
    private int expectedPcktCount = 0;
    private int expectedConfigSize = 0;
    private int expectedPacketSize = 0;
    private int currentPcktSequence = 0;
    private byte[] config;
    private int configSizeCounter = 0;
    private int configIndexPointer = 0;
    private String TAG = "ConfigDownload";
    private boolean isConfigTransferInProcess = false;

    // endregion

    ConfigReaderController() {
        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();

        timer = new Timer(true);
        lock = new ReentrantReadWriteLock();
    }

    public void execute(IPluginResult callback) {
        try {
            this.callback = callback;
            if(connection == null) {
                stopProcessAndSendResult(PluginResult.Status.ERROR, false, false, "Device Not Connected.",
                        ETrackerStatusCode.BLE_NOT_CONNECTED);
            }
            this.connection.setListener(this);
            byte[] reqDataPacket = getConfigReadReqPacket();
            LOG.d(TAG, " Config read request packet: " + Arrays.toString(reqDataPacket));
            connection.transmit(reqDataPacket);
            Log.d(TAG, "Config read request packet sent");
            isWaitingForBleResponse = true;
            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);

        } catch (Exception ex) {
            detachConnectionEventHandlers();
            stopTimer();
            String msg = PluginUtil.composePluginExecResult(true, false, "Error occurred during config file transfer. " +
                    ex.toString(), ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
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
                stopProcessAndSendResult(PluginResult.Status.ERROR, true, false, "Invalid response received from BLE device", ETrackerStatusCode.INVALID_RESPONSE_DATA);

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

    private byte[] getConfigReadReqPacket() {

        byte[] reqPckt = new byte[20];

        reqPckt[0] = ETrackerCommand.CMD_READ_CONFIGURATION;
        reqPckt[1] = 0;
        reqPckt[2] = (byte) 11;
        reqPckt[3] = 1;
        reqPckt[4] = 0;
        reqPckt[5] = 0;

        reqPckt[6] = 0;
        reqPckt[7] = 0;
        reqPckt[8] = 0;
        reqPckt[9] = 0;

        reqPckt[10] = 0;
        reqPckt[11] = 0;
        reqPckt[12] = 0;
        reqPckt[13] = 0;

        return reqPckt;

    }

    private void processResponse(byte[] respData) throws IOException {

        resetResponseWaitCounter();

        if (isConfigTransferInProcess) {

            if (respData.length <= 20)
                LOG.d(TAG, "Data chunk received. Packet length: " + respData.length + " ### packet data: " +
                        Arrays.toString(respData));
            else
                LOG.d(TAG, "Data packet received. Packet length: " + respData.length + " ### packet data: " +
                        Arrays.toString(respData));

            appendConfigIntoBuffer(respData, 0);

            isWaitingForBleResponse = true;

        } else {
            int subCmdId = respData[3];

            if (ETrackerCommand.CMD_READ_CONFIG_READY_TO_TRANSMIT == subCmdId) {

                LOG.d(TAG, "Ready to transmit received: " + Arrays.toString(respData) + " packet sequence number: " +
                        currentPcktSequence);

                expectedPcktCount = getPacketCount(respData);
                expectedConfigSize = getConfigSize(respData);
                LOG.d(TAG, "Expected packet count: " + expectedPcktCount + " ####### Expected file size: " +
                        expectedConfigSize);

                if (expectedPcktCount == 0 || expectedConfigSize == 0) {
                    stopProcessAndSendResult(PluginResult.Status.ERROR, true, false, "Invalid config metadata",
                            ETrackerStatusCode.INVALID_CONFIG_METADATA);
                } else {
                    config = new byte[expectedConfigSize];
                    sendReadyToReceiveAck();
                    isWaitingForBleResponse = true;
                }
            } else if (ETrackerCommand.CMD_READ_CONFIG_PACKET_RECEIVED == subCmdId) {

                isConfigTransferInProcess = true;
                currentPcktSequence = getPacketSequenceNumber(respData);
                expectedPacketSize = getPacketSize(respData);
                LOG.d(TAG, "Config packet received. Sequence number: " + currentPcktSequence + " Packet size: " + expectedPacketSize);
                LOG.d(TAG, "Config packet data: " + Arrays.toString(respData));

                appendConfigIntoBuffer(respData, 6);
                isWaitingForBleResponse = true;
            }
        }

        if (isEntirePacketReceived()) {
            configSizeCounter = 0;
            isConfigTransferInProcess = false;
            sendPcktReceivedAck(true, currentPcktSequence);
            LOG.d(TAG, "Entire data packet received. Packet sequence number: " + currentPcktSequence + " ####  Packet data: " + Arrays.toString(config) + " #### Config: " + new String(config));
            if (isEntireConfigReceived()) {

                String bleConfig = new String(config);
                LOG.d(TAG, "Config array length: " + config.length);
                LOG.d(TAG, "BLE Config: " + bleConfig);

                stopProcessAndSendResult(PluginResult.Status.OK, true, true, bleConfig,
                        ETrackerStatusCode.BLE_CONFIG_READ_SUCCESS);
            }
        }
    }

    private void sendReadyToReceiveAck() throws IOException {
        byte[] ackPckt = new byte[6];

        ackPckt[0] = ETrackerCommand.CMD_READ_CONFIGURATION;
        ackPckt[1] = 0;
        ackPckt[2] = 3;
        ackPckt[3] = ETrackerCommand.CMD_READ_CONFIG_READY_TO_RECEIVE_ACK;
        ackPckt[4] = 0;
        ackPckt[5] = 0;

        connection.transmit(ackPckt);

        LOG.d(TAG, "Ready to receive ACK sent.");
    }

    private void sendPcktReceivedAck(boolean isValidPckt, int pcktSeqNum) throws IOException {
        byte[] ackPckt = new byte[8];

        ackPckt[0] = ETrackerCommand.CMD_READ_CONFIGURATION;
        ackPckt[1] = 0;
        ackPckt[2] = 5;
        ackPckt[3] = ETrackerCommand.CMD_READ_CONFIG_PACKET_RECEIVED_ACK;
        ackPckt[4] = isValidPckt ? (byte) 0 : (byte) 1;
        ackPckt[5] = pcktSeqNum > 127 ? (byte) pcktSeqNum : 0;
        ackPckt[6] = pcktSeqNum <= 127 ? (byte) pcktSeqNum : 0;
        ackPckt[7] = 0;

        connection.transmit(ackPckt);
        Log.d(TAG, "Config packet receive ACK sent" + Arrays.toString(ackPckt));
        LOG.d(TAG, "Config packet receive ACK sent for packet number: " + pcktSeqNum);
    }

    private void stopProcessAndSendResult(PluginResult.Status status, boolean isConnected, boolean isActionSuccess, String
            msg, int statusCode) {
        if(isConnected){
            detachConnectionEventHandlers();
        }
        stopTimer();
        String result = PluginUtil.composePluginExecResult(isConnected, isActionSuccess, msg, statusCode);
        callback.sendResult(status, result, false);
    }

    private void appendConfigIntoBuffer(byte[] pcktChunk, int startIndex) {
        try {
            for (int index = startIndex; index < pcktChunk.length; index++, configSizeCounter++) {
                config[configIndexPointer++] = pcktChunk[index];
            }

            /*System.arraycopy(pcktChunk,startIndex,config,configIndexPointer,pcktChunk.length-startIndex);
            configIndexPointer += pcktChunk.length - startIndex;*/

        } catch (Exception ex) {
            LOG.d(TAG, "Error occurred while copying config into buffer. Error details: " + ex.toString());
        }
    }

    private int getPacketSequenceNumber(byte[] respData) {
        int seqNum = 0;
        int pcktCount = respData[4];
        if (pcktCount > 0) {
            seqNum = pcktCount + respData[5];
        } else {
            seqNum = respData[5];
        }
        return seqNum;
    }

    private int getPacketSize(byte[] respData) {
        int count = 0;

        int pcktCount = respData[1];
        if (pcktCount > 0) {
            count = pcktCount + respData[2];
        } else {
            count = respData[2];
        }

        if (count < 0) {
            count = count & 0XFF;
        }

        return count;
    }

    private int getPacketCount(byte[] respData) {
        int count = (byte) (respData[4] | respData[5]);
        return count;
    }

    private int getConfigSize(byte[] respData) {

        byte[] bData = Arrays.copyOfRange(respData, 6, 10);
        int fileSize = ByteBuffer.wrap(bData).getInt();
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

    private boolean isEntireConfigReceived() {
        return expectedPcktCount == currentPcktSequence;
    }

    private boolean isEntirePacketReceived() {
        LOG.d(TAG, "Buffer index: " + configSizeCounter + " Expected packet size: " + expectedPacketSize);
        return configSizeCounter == expectedPacketSize - 3;
    }

    // endregion

}
