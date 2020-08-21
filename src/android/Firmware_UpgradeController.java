package com.mti.eTracker2CommsPlugin;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.*;

import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import android.os.Environment;

public class Firmware_UpgradeController extends TimerTask implements TIOConnectionCallback {
    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private final int sizePerPacket = 128;
    private int packetCount;
    private int pcktDispatchCount;
    private byte[] firmwareBytes;
    private String firmware_data;
    private boolean isWaitingForACK;
    private ReentrantReadWriteLock lock;
    private static final String TAG = "Firmware Upgrade";
    private int pcktTransmitAttemptCount;
    private int pcktTransmitAttemptLimit = 2;
    private static final int timerExecInterval = 900 * 1000;

    enum ACK_FILE_STATUS {
        Success(0),
        Failure(1);

        private int numVal;

        ACK_FILE_STATUS(int numVal) {
            this.numVal = numVal;
        }
    }

    // endregion

    // region Constructor

    Firmware_UpgradeController() {
        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();
        this.connection.setListener(this);

        timer = new Timer(true);
        lock = new ReentrantReadWriteLock();
    }

    // endregion

    // region Public methods

    public void execute(String fileName, int majVer, int minVer, IPluginResult callback) {
        try {

            this.callback = callback;
//            String ext = fileName.substring(fileName.lastIndexOf('.') + 1);

            getFirmwareFileDataInBinary(fileName);
            //Log.d(TAG, "Firmware File Data: " + firmware_data);

//            if(ext.equalsIgnoreCase("hex")){
//                firmware_data = getFirmwareBinaryData(firmware_data);
//                Log.d(TAG, "Firmware Binary Data: " + firmware_data);
//            }

//            if (firmware_data.isEmpty()){
//                stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Invalid File Content", ETrackerStatusCode.INVALID_FILE_CONTENT);
//            }

//            Log.d(TAG, firmware_data);

            //firmwareBytes = firmware_data.getBytes();
            packetCount = getFilePacketsCount(firmwareBytes);
            int size = firmwareBytes.length;
            int crc = getCrcForTotalPayload(firmwareBytes, size);
            Log.d(TAG, "No of packets: " + packetCount + ", Size of data: " + size + ", CRC: " + crc);

            byte[] reqPacket = getFileTransferReqPacket(packetCount, size, majVer, minVer, crc);
            LOG.d(TAG, "Firmware upload request packet: " + Arrays.toString(reqPacket));

            connection.transmit(reqPacket);
            isWaitingForACK = true;
            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);

        } catch (Exception ex) {
            connection.setListener(null);
            String msg = PluginUtil.composePluginExecResult(true, false, "Error occurred during config file transfer", ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
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
        stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Underlying connection failure", ETrackerStatusCode.BLE_CONNECTION_FAILURE);
    }

    @Override
    public void onDisconnected(TIOConnection tioConnection, String errorMsg) {
        stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Underlying connection got disconnect", ETrackerStatusCode.BLE_DISCONNECTED);
    }

    @Override
    public void onDataReceived(TIOConnection tioConnection, byte[] respData) {
        try {
            if (respData == null) {
                stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Invalid response data received", ETrackerStatusCode.INVALID_RESPONSE_DATA);
                return;
            }
            LOG.d(TAG, "Response Data: " + Arrays.toString(respData));
            lock.writeLock().lock();
            try {
                isWaitingForACK = false;
                processResponse(respData);
            } finally {
                lock.writeLock().unlock();
            }
        } catch (Exception ex) {
            LOG.d(TAG, "PLugin response handle error. " + ex.toString());
            stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Error occurred while processing Firmware data transfer response", ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
        }
    }

    @Override
    public void onDataTransmitted(TIOConnection tioConnection, int status, int bytesWritten) {
        Log.d(TAG, "Request sent for request packet: " + pcktDispatchCount +
                " Transmission status: " + status + " Bytes written: " + bytesWritten);
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

        if (lock.isWriteLocked())
            return;

        if (isWaitingForACK) {
            if (pcktTransmitAttemptCount > pcktTransmitAttemptLimit) {
                stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Response timeout occurred", ETrackerStatusCode.RESPONSE_TIMEOUT);
            } else {
                try {
                    pcktDispatchCount -= 1;
                    byte[] pcktData = createFileDataPacket();
                    connection.transmit(pcktData);
                    pcktDispatchCount++;
                    pcktTransmitAttemptCount++;
                } catch (Exception ex){

                }

            }
        }
    }

    // endregion

    // region Private methods

    private int getFilePacketsCount(byte[] configBytesData) {
        int count;

        int dataLength = configBytesData.length;

        if (dataLength % sizePerPacket == 0)
            count = dataLength / sizePerPacket;
        else
            count = (dataLength / sizePerPacket) + 1;

        return count;
    }

    private byte[] getFileTransferReqPacket(int packetCount, int firmware_dataSize, int majorVer, int minorVer, int crc) {

        byte[] reqPckt = new byte[14];

        reqPckt[0] = ETrackerCommand.CMD_FIRMWARE_UPLOAD;
        reqPckt[1] = 0;
        reqPckt[2] = (byte) 11;
        reqPckt[3] = ETrackerCommand.CMD_FIRMWARE_UPLOAD_START_TRANSFER;
        reqPckt[4] = (byte) ((packetCount >> 8) & 0XFF);
        reqPckt[5] = (byte) (packetCount & 0XFF);

        reqPckt[6] = (byte) ((firmware_dataSize & 0XFF000000) >> 24);
        reqPckt[7] = (byte) ((firmware_dataSize & 0X00FF0000) >> 16);
        reqPckt[8] = (byte) ((firmware_dataSize & 0X0000FF00) >> 8);
        reqPckt[9] = (byte) ((firmware_dataSize & 0X000000FF) >> 0);

        // File Version
        reqPckt[10] = (byte) majorVer;
        reqPckt[11] = (byte) minorVer;
        // CRC
        reqPckt[12] = (byte) ((crc >> 8) & 0XFF);
        reqPckt[13] = (byte) (crc & 0XFF);

        return reqPckt;
    }

    private void processResponse(byte[] respData) throws IOException, InterruptedException {

        int cmdId = respData[3];

        if (cmdId == ETrackerCommand.CMD_FIRMWARE_UPLOAD_READY_TO_RECEIVE) {
            LOG.d(TAG, "Ready to receive response: " + Arrays.toString(respData));
            byte[] pcktData = createFileDataPacket();
            connection.transmit(pcktData);
            pcktDispatchCount++;

        } else if (cmdId == ETrackerCommand.CMD_FIRMWARE_UPLOAD_FILE_ACK_PACKET) {
            LOG.d(TAG, "ACK for packet number : " + pcktDispatchCount + " #### Response: " + Arrays.toString(respData));
            handleConfigUpdateAck(respData);
        }
    }

    private void handleConfigUpdateAck(byte[] respData) throws IOException, InterruptedException {

        int pcktTransmitStatus = respData[4];

        if (pcktTransmitStatus == Firmware_UpgradeController.ACK_FILE_STATUS.Success.numVal) {
            pcktTransmitAttemptCount = 0;
            pcktTransmitAttemptLimit = 2;
            Log.d(TAG, "Cert Data packet: " + pcktDispatchCount + " validation SUCCESS");

            if (isAllPacketSent()) {
                Log.d(TAG, "All packets sent");
                stopFileTransferAndSendResult(PluginResult.Status.OK, true, true, "Firmware Data uploaded successfully", ETrackerStatusCode.FIRMWARE_FILE_UPLOAD_SUCCESS);
            } else {
                byte[] pcktData = createFileDataPacket();
                connection.transmit(pcktData);
                isWaitingForACK = true;
                pcktDispatchCount++;
            }
        } else {

            Log.d(TAG, "Cert Data packet: " + pcktDispatchCount + " validation FAILED ");

            if (pcktTransmitAttemptCount < pcktTransmitAttemptLimit) {
                pcktDispatchCount -= 1;
                byte[] pcktData = createFileDataPacket();
                connection.transmit(pcktData);
                pcktDispatchCount++;
                pcktTransmitAttemptCount++;
            } else {
                Log.d(TAG, "Packet transfer attempt count exceeded limit. Packet number " + pcktDispatchCount);
                stopFileTransferAndSendResult(PluginResult.Status.ERROR, true, false, "Packet transfer attempt count exceeded limit. Packet number: " + pcktDispatchCount, ETrackerStatusCode.CONFIG_FILE_PACKET_TRANSFER_ATTEMPT_LIMIT_EXCEEDED);
            }
        }
    }

    private byte[] createFileDataPacket() {
        int dataLen;
        int sizeTransmitted = (pcktDispatchCount * sizePerPacket);
        boolean flag = isLastPacket();
        if (flag) {
            dataLen = firmwareBytes.length - sizeTransmitted + 3;
        } else {
            dataLen = sizePerPacket + 3;
        }

        Log.d(TAG, "Size transmitted: " + sizeTransmitted + " ## Data length: " + dataLen + " ## is last packet: " + flag);

        int packetNum = pcktDispatchCount + 1;

        byte[] pckt = new byte[dataLen + 3];

        pckt[0] = ETrackerCommand.CMD_FIRMWARE_UPLOAD;
        pckt[1] = (byte) ((dataLen >> 8) & 0XFF);
        pckt[2] = (byte) (dataLen & 0XFF);
        pckt[3] = ETrackerCommand.CMD_FIRMWARE_UPLOAD_SEND_FILE_PACKET;
        pckt[4] = (byte) ((packetNum >> 8) & 0XFF);
        pckt[5] = (byte) ((packetNum) & 0XFF);

        int currentIndex = sizeTransmitted;
        for (int i = 6; i < pckt.length; i++, currentIndex++) {
            pckt[i] = firmwareBytes[currentIndex];
        }

        Log.d(TAG, "Firmware Data packet number: " + packetNum + " ### packet data: " + Arrays.toString(pckt));

        return pckt;
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private boolean isAllPacketSent() {
        Log.d("Check all packet sent ", "Packet dispatch count: " + pcktDispatchCount + " ## Packet count: " + packetCount);
        return pcktDispatchCount == packetCount;
    }

    private boolean isLastPacket() {
        return (pcktDispatchCount + 1) == packetCount;
    }

    private void stopFileTransferAndSendResult(PluginResult.Status pluginExecStatus, boolean isConnected, boolean isActionSuccess, String msg, int statusCode) {
        //connection.setListener(null);
        stopTimer();
        String result = PluginUtil.composePluginExecResult(isConnected, isActionSuccess, msg, statusCode);
        callback.sendResult(pluginExecStatus, result, false);
    }

    private void getFirmwareFileDataInBinary(String fileName) throws IOException {
        String root = Environment.getExternalStorageDirectory().toString();
        Log.d(TAG, "Path of Firmware files: " + root);
        File firmware_file = new File(root + "/eTracker2Firmware");

        File file = new File(firmware_file, fileName);
        InputStream inputStream = new FileInputStream(file);
        int OTADatalen = (int) file.length();

        firmwareBytes = new byte[OTADatalen];
        inputStream.read(firmwareBytes);
        inputStream.close();

        Log.d(TAG, "Binary File Array length" + firmwareBytes.length);//253392
    }

    private String getFirmwareBinaryData(String hexString){
        String binString = "";
        try{
            binString = new BigInteger(hexString, 16).toString(2);
        } catch (Exception ex){
            Log.d(TAG, "Error while converting hex to binary." + ex.toString());
        }
        return binString;
    }

    private int getCrcForTotalPayload(byte[] payload, int length){
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
    // endregion
}
