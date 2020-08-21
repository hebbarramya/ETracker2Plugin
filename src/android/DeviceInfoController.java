package com.mti.eTracker2CommsPlugin;

import java.io.IOException;
import java.lang.*;

import org.apache.cordova.PluginResult;

import com.telit.terminalio.TIOConnection;
import com.telit.terminalio.TIOConnectionCallback;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Date;

import org.apache.cordova.LOG;

import com.google.gson.*;

class DeviceInfoController extends TimerTask implements TIOConnectionCallback {

    // region Member variables

    private TIOConnection connection;
    private IPluginResult callback;
    private Timer timer;
    private int responseWatchCounter;
    private boolean isResponseProcessing;
    private boolean isFirstChunk = true;
    private byte[] deviceDetailsData;
    private int expectedDataSize;
    private int dataIndexPointer = 0;
    private static final int timerExecInterval = 5 * 10000;

    // endregion

    // region Constructor

    public DeviceInfoController() {

        DeviceContainer container = DeviceContainer.getInstance();
        this.connection = container.getPeripheralConnection();
        this.connection.setListener(this);

        timer = new Timer(true);
    }

    // endregion

    // region Public method

    public void execute(IPluginResult callback) throws IOException {

        this.callback = callback;

        try {
            byte[] data = getConnectedDeviceReqPackt();
            connection.transmit(data);

            timer.scheduleAtFixedRate(this, timerExecInterval, timerExecInterval);
        } catch (Exception ex) {
            String msg = PluginUtil.composePluginExecResult(true, false,
                    "Error occurred while fetching Connected Device details.",
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
        if (respData == null || respData.length == 0) {
            String msg = "Invalid response received from BLE";
            msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.INVALID_RESPONSE_DATA);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }

        try {
            isResponseProcessing = true;

            if (isFirstChunk) {
                expectedDataSize = respData[1] + (respData[2] & 0XFF);
                deviceDetailsData = new byte[expectedDataSize];
                isFirstChunk = false;
                appendData(respData, 3);
            } else {
                appendData(respData, 0);
            }

            if (dataIndexPointer == expectedDataSize) {
                stopTimer();
                DeviceInfo deviceInfo = parseConnectedDeviceInfo();

                Gson gson = new Gson();
                String json = gson.toJson(deviceInfo);
                String msg = PluginUtil.composePluginExecResult(true, true, json,
                        ETrackerStatusCode.COMMAND_RESPONSE_RECEIVED);
                callback.sendResult(PluginResult.Status.OK, msg, false);
            }

            isResponseProcessing = false;
        } catch (Exception ex) {
            LOG.d("Device details", "Error occurred while processing response. Error details: " + ex.toString());
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

        // If not maxed out
        // wait.

        if (isResponseProcessing)
            return;

        responseWatchCounter++;

        int responseWatchLimit = 3;
        if (responseWatchCounter > responseWatchLimit) {
            stopTimer();
            String msg = "Response timeout occurred";
            msg = PluginUtil.composePluginExecResult(true, false, msg, ETrackerStatusCode.RESPONSE_TIMEOUT);
            callback.sendResult(PluginResult.Status.ERROR, msg, false);
        }
    }

    // endregion

    // region Private methods

    private byte[] getConnectedDeviceReqPackt() {
        byte[] reqPackt = new byte[3];
        reqPackt[0] = ETrackerCommand.CMD_READ_CONNECTED_DEVICE_DETAILS;
        reqPackt[1] = 0;
        reqPackt[2] = 0;

        return reqPackt;
    }

    private DeviceInfo parseConnectedDeviceInfo() {
        int readPointer = 0;
        DeviceInfo deviceInfo = new DeviceInfo();
        try {
            LOG.d("Device details", "Parsing station name");
            // Station Name
            byte[] station_name = new byte[deviceDetailsData[readPointer++]];
            for (int i = 0; i < station_name.length; i++) {
                station_name[i] = deviceDetailsData[readPointer++];
            }
            deviceInfo.station_name = new String(station_name);

            LOG.d("Device details", "Parsing SUI");
            // SUI
            byte[] sui = new byte[8];
            for (int i = 0; i < 8; i++) {
                sui[i] = deviceDetailsData[readPointer++];
            }
            LOG.d("Device details", "Parsing device details result: "+ Arrays.toString(sui));
            deviceInfo.sui = Long.toHexString(ByteBuffer.wrap(sui).getLong());

            LOG.d("Device details", "Parsing station time");
            // Current Time of Station
            byte[] current_time_station = new byte[4];
            for (int i = 0; i < 4; i++) {
                current_time_station[i] = deviceDetailsData[readPointer++];
            }
            //deviceInfo.stationDateTime = LocalDateTime.parse(new String(current_time_station));
            LOG.d("Device details", "Parsing station time result : "+ Arrays.toString(current_time_station));
            // Date station_time = new Date(ByteBuffer.wrap(current_time_station).getInt() * 1000L);
            // SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

            // deviceInfo.time_stamp = String.valueOf(dateFormat.format(station_time));

            deviceInfo.time_stamp = String.valueOf(ByteBuffer.wrap(current_time_station).getInt());

            LOG.d("Device details", "Parsing HW version");
            // Current HW version
            byte[] hw_version = new byte[2];
            for (int i = 0; i < 2; i++) {
                hw_version[i] = deviceDetailsData[readPointer++];
            }
            LOG.d("Device details", "Parsing HW version result: "+ Arrays.toString(hw_version));
            deviceInfo.hwVersion = hw_version[0] + "." + hw_version[1];

            LOG.d("Device details", "Parsing FW version");
            // Current FW version
            byte[] fw_version = new byte[2];
            for (int i = 0; i < 2; i++) {
                fw_version[i] = deviceDetailsData[readPointer++];
            }
            LOG.d("Device details", "Parsing FW version result: "+ Arrays.toString(fw_version));
            deviceInfo.fwVersion = fw_version[0] + "." + fw_version[1];

            LOG.d("Device details", "Parsing battery voltage");
            // Current battery voltage
            byte[] battery_voltage = new byte[4];
            for (int i = 0; i < 4; i++) {
                battery_voltage[i] = deviceDetailsData[readPointer++];
            }
            LOG.d("Device details", "Parsing battery voltage result: "+ Arrays.toString(battery_voltage));
            deviceInfo.voltage = String.valueOf(ByteBuffer.wrap(battery_voltage).getFloat());

            LOG.d("Device details", "Parsing GPS cordinates");
            // GPS Coordinates Length
            byte[] gps_coords = new byte[deviceDetailsData[readPointer++]];
            for (int i = 0; i < gps_coords.length; i++) {
                gps_coords[i] = deviceDetailsData[readPointer++];
            }
            LOG.d("Device details", "Parsing GPS cordinates result: "+ Arrays.toString(gps_coords));
            deviceInfo.gps_coordinates = new String(gps_coords);

            LOG.d("Device details", "Parsing time zone");
            // Time Zone
            byte[] time_zone = new byte[deviceDetailsData[readPointer++]];
            for (int i = 0; i < time_zone.length; i++) {
                time_zone[i] = deviceDetailsData[readPointer++];
            }
            LOG.d("Device details", "arsing time zone result: "+ Arrays.toString(time_zone));
            deviceInfo.time_zone = new String(time_zone);

        } catch (Exception ex) {
            LOG.d("Device details", "Error occurred while parsing device details. Error details: " + ex.toString());
        }

        return deviceInfo;
    }

    private void appendData(byte[] data, int startIndex) {
        try {
            for (int i = startIndex; i < data.length; i++) {
                deviceDetailsData[dataIndexPointer++] = data[i];
            }
        } catch (Exception ex) {
            LOG.d("Device details", "Error occurred while appending data into Final Device Details List. Error details: " + ex.toString());
        }
    }

    private void stopTimer() {

        if (timer != null) {
            timer.cancel();
        }
    }
    // endregion
}
