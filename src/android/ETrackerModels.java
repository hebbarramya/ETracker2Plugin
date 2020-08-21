package com.mti.eTracker2CommsPlugin;

import com.telit.terminalio.TIOPeripheral;
import com.telit.terminalio.TIOConnection;

import org.apache.cordova.PluginResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Enumeration;
import java.util.Hashtable;

class PluginExecResult {
    public boolean IsActionSuccessful;
    public boolean IsConnected;
    public String Message;
    public int StatusCode;
}

class Station {
    String stationId;
    String stationName;

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }
}

class ScanSDI12_Response {
    String response;
    boolean isProgressACK;

    ScanSDI12_Response(String resp, boolean isPrgAck) {
        response = resp;
        isProgressACK = isPrgAck;
    }
}

class SDI12_Command_Response {
    String SensorAddress;
    String SDI12Compatibility;
    String CompanyName;
    String SensorModelNumber;
    String SensorVersion;
    String SerialNumber;
}

class ETrackerAction {
    public static final String DISCOVER_STATIONS = "DiscoverStations";
    public static final String CONNECT_STATION = "ConnectStation";
    public static final String DISCONNECT_STATION = "DisconnectStation";
    public static final String AUTHENTICATE_USER = "AuthenticateUser";
    public static final String GET_SDI12_SENSOR_DATA = "GetSDI12SensorData";
    public static final String LOGOUT = "Logout";
    public static final String GET_CONFIG = "GetConfiguration";
    public static final String SET_CONFIG = "SetConfiguration";
    public static final String GET_DEVICE_DETAILS = "GetConnectedDeviceDetails";
    public static final String TIME_SYNC = "TimeSync";
    public static final String SWITCH_TO_LM = "SwitchToLM";
    public static final String CHECK_NETWORK = "CheckNetwork";
    public static final String CHECK_SD_STATUS = "CheckSDCardStatus";
    public static final String RESET_PASSWORD = "ResetPassword";
    public static final String SET_PASSWORD = "SetPassword";
    public static final String Scan_SDI12_CHANNEL = "ScanSDI12channel";
    public static final String COMMIT_CONFIG_UPDATE = "CommitConfigUpdate";
    public static final String REAL_TIME_SENSOR_READINGS = "SensorReadings";
    public static final String APPLY_ACTIONS = "ApplyActions";
    public static final String TEMPORARY_OVERRIDE = "TemporaryOverride";
    public static final String REPORT_CONFIG = "ReportConfig";
    public static final String REPORT_READINGS = "ReportReadings";
    public static final String CERTIFICATE_DOWNLOAD = "CertificateDownload";
    public static final String FIRMWARE_UPLOAD = "FirmwareUpload";
    public static final String CHECK_CRC = "CheckCRC";
}

class ETrackerCommand {
    public static final int CMD_AUTHENTICATE_USER = 1;
    public static final int CMD_RESET_PASSWORD = 2;
    public static final int CMD_SET_PASSWORD = 3;
    public static final int CMD_SCAN_SDI_12_BUS = 14;
    public static final int CMD_APPLY_CONFIGURATION = 11;
    public static final int CMD_READ_CONFIGURATION = 16;
    public static final int CMD_UPLOAD_CONFIGURATION_START_TRANSFER = 1;
    public static final int CMD_UPLOAD_CONFIGURATION_READY_TO_RECEIVE = 2;
    public static final int CMD_UPLOAD_CONFIGURATION_SEND_CONFIG_FILE_PACKET = 3;
    public static final int CMD_UPLOAD_CONFIGURATION_FILE_ACK_PACKET = 4;
    public static final int CMD_UPLOAD_CONFIGURATION_COMMIT_CONFIG_UPDATE = 7;
    public static final int CMD_APPLY_LOCAL_CONFIGURATION = 8;
    public static final int CMD_ENABLE_LOGGING_MODE = 10;
    public static final int CMD_READ_CONFIG_READY_TO_TRANSMIT = 1;
    public static final int CMD_READ_CONFIG_READY_TO_RECEIVE_ACK = 2;
    public static final int CMD_READ_CONFIG_PACKET_RECEIVED = 3;
    public static final int CMD_READ_CONFIG_PACKET_RECEIVED_ACK = 4;
    public static final int CMD_READ_CONNECTED_DEVICE_DETAILS = 18;
    public static final int CMD_TIME_SYNC = 19;
    public static final int CMD_SWITCH_LM = 10;
    public static final int CMD_CHECK_NETWORK = 21;
    public static final int CMD_CHECK_SD = 20;
    public static final int CMD_RESET_PWD = 2;
    public static final int CMD_SET_PWD = 3;
    public static final int CMD_SCAN_SDI12 = 4;
    public static final int CMD_SCAN_SDI12_REQUEST = 1;
    public static final int CMD_SCAN_SDI12_INFO_ACK = 4;
    public static final int CMD_REPORT_REAL_TIME_SENSOR = 22;
    public static final int CMD_SDI12_DATA_PACKET_RECEIVED_ACK = 6;
    public static final int CMD_SDI12_PROGRESS_ACK = 2;
    public static final int CMD_SDI12_INFO = 3;
    public static final int CMD_SDI12_DATA_RECEIVED = 5;
    public static final int CMD_TEMPORARY_OVERRIDE = 8;
    public static final int CMD_TEMPORARY_OVERRIDE_START_TRANSFER = 1;
    public static final int CMD_TEMPORARY_OVERRIDE_READY_TO_RECEIVE = 2;
    public static final int CMD_TEMPORARY_OVERRIDE_FILE_ACK_PACKET = 4;
    public static final int CMD_TEMPORARY_OVERRIDE_SEND_DATA_FILE_PACKET = 3;
    public static final int CMD_REPORT_CONFIG = 6;
    public static final int CMD_REPORT_READINGS = 5;
    public static final int CMD_APPLY_ACTIONS = 9;
    public static final int CMD_CERTIFICATE_DOWNLOAD = 23;
    public static final int CMD_CERTIFICATE_DOWNLOAD_START_TRANSFER = 1;
    public static final int CMD_CERTIFICATE_DOWNLOAD_READY_TO_RECEIVE = 2;
    public static final int CMD_CERTIFICATE_DOWNLOAD_SEND_FILE_PACKET = 3;
    public static final int CMD_CERTIFICATE_DOWNLOAD_FILE_ACK_PACKET = 4;
    public static final int CMD_FIRMWARE_UPLOAD = 13;
    public static final int CMD_FIRMWARE_UPLOAD_START_TRANSFER = 1;
    public static final int CMD_FIRMWARE_UPLOAD_READY_TO_RECEIVE = 2;
    public static final int CMD_FIRMWARE_UPLOAD_SEND_FILE_PACKET = 3;
    public static final int CMD_FIRMWARE_UPLOAD_FILE_ACK_PACKET = 4;

    Hashtable<Integer, String> cmdDict = new Hashtable<>();

    public void InitCommandDictonary() {
        cmdDict.put(ETrackerCommand.CMD_AUTHENTICATE_USER, ETrackerAction.AUTHENTICATE_USER);
        cmdDict.put(ETrackerCommand.CMD_SCAN_SDI_12_BUS, ETrackerAction.GET_SDI12_SENSOR_DATA);
    }

    public String GetAction(int cmdId) {
        String action = "";
        if (cmdDict.containsKey(cmdId)) {
            action = cmdDict.get(cmdId);
        }
        return action;
    }
}

class ETrackerStatusCode {
    public static final int BLE_NOT_CONNECTED = 100;
    public static final int PLUGIN_INTERNAL_ERROR = 101;
    public static final int BLE_INVALID_CONNECTION_CONTEXT = 102;
    public static final int BLE_CONNECTED = 103;
    public static final int BLE_CONNECTION_FAILURE = 104;
    public static final int BLE_DISCONNECTED = 105;
    public static final int BLE_ALREADY_CONNECTED = 106;
    public static final int BLE_ALREADY_CONNECTING = 107;
    public static final int BLE_ALREADY_DISCONNECTING = 108;
    public static final int BLE_DEVICE_UNAVAILABLE = 109;
    public static final int INVALID_CONNECTION_CONTEXT = 110;
    public static final int INVALID_DEVICE_ADDRESS = 110;
    public static final int INVALID_REQUEST_DATA = 111;
    public static final int USER_AUTHORIZATION_SUCCESS = 112;
    public static final int USER_AUTHORIZATION_FAILURE = 113;
    public static final int INVALID_RESPONSE_DATA = 114;
    public static final int COMMAND_RESPONSE_RECEIVED = 115;
    public static final int RESPONSE_TIMEOUT = 116;
    public static final int CONFIG_FILE_UPDATED = 117;
    public static final int CONFIG_FILE_PACKET_TRANSFER_ATTEMPT_LIMIT_EXCEEDED = 118;
    public static final int CONFIG_UPDATE_COMMIT_FAILED = 119;
    public static final int INVALID_CONFIG_METADATA = 120;
    public static final int BLE_CONFIG_READ_SUCCESS = 121;
    public static final int BLE_CONFIG_READ_FAILURE = 122;
    public static final int TIME_SYNC_SUCCESS = 123;
    public static final int TIME_SYNC_FAILURE = 124;
    public static final int SDI12_CMD_PROGRESS_UPDATE = 132;
    public static final int SDI12_CMD_READ_SUCCESS = 131;
    public static final int CONFIG_FILE_UPLOADED = 133;
    public static final int READ_SENSOR_READINGS_PROGRESS_UPDATE = 134;
    public static final int SET_PASSWORD_LENGTH_EXCEEDED = 135;
    public static final int SET_PASSWORD_SUCCESS = 136;
    public static final int SET_PASSWORD_FAILURE = 137;
    public static final int APPLY_ACTIONS_SUCCESS = 138;
    public static final int APPLY_ACTIONS_FAILURE = 139;
    public static final int BLE_ACTION_SUCCESS = 140;
    public static final int BLE_ACTION_FAILURE = 141;
    public static final int FILE_NOT_FOUND = 140;
    public static final int CERTIFICATE_FILE_READ_SUCCESS = 141;
    public static final int CERTIFICATE_FILE_UPLOAD_SUCCESS = 142;
    public static final int INVALID_FILE_CONTENT = 143;
    public static final int FIRMWARE_FILE_UPLOAD_SUCCESS = 144;
    public static final int FILE_SIZE_EXCEEDED = 145;
    public static final int CRC_CHECK_SUCCESS = 146;
    public static final int CRC_CHECK_FAILURE = 147;
}

class DeviceContainer {

    private static DeviceContainer container;
    private static Device device;

    private class Device {

        private TIOPeripheral peripheral;
        private TIOConnection connection;

        TIOPeripheral getConnectedPeripheral() {
            return peripheral;
        }

        TIOConnection getPeripheralConnection() {
            return connection;
        }

        void setConnectedPeripheral(TIOPeripheral peripheral) {
            this.peripheral = peripheral;
        }

        void setPeripheralConnection(TIOConnection connection) {
            this.connection = connection;
        }

        void reset() {
            this.peripheral = null;
            this.connection = null;
        }
    }

    private DeviceContainer() {
        device = new Device();
    }

    static DeviceContainer getInstance() {
        if (container == null) {
            container = new DeviceContainer();
        }

        return container;
    }

    void addConnectedPeripheral(TIOPeripheral peripheral) {
        device.setConnectedPeripheral(peripheral);
    }

    void addConnectedPeripheral(TIOPeripheral peripheral, TIOConnection connection) {
        device.setConnectedPeripheral(peripheral);
        device.setPeripheralConnection(connection);
    }

    TIOPeripheral getConnectedPeripheral() {
        return device.getConnectedPeripheral();
    }

    TIOConnection getPeripheralConnection() {
        return device.getPeripheralConnection();
    }

    static void emptyContainer() {
        device.peripheral = null;
        device.connection = null;
    }

    boolean isContainerEmpty() {
        return device.peripheral == null;
    }
}

interface IPluginResult {
    void sendResult(PluginResult.Status status, String msg, boolean keepCallbackOpen);
}

class DeviceInfo {
    String station_name;
    String sui;
    String time_stamp;
    String hwVersion;
    String fwVersion;
    String voltage;
    String gps_coordinates;
    String time_zone;

}

enum REAL_TIME_SENSOR_SUBMCMDID {

    REAL_TIME_SENSOR_REQUEST_SUBCMD_ID(01), REAL_TIME_SENSOR_PROGRESS_ACK_SUBCMD_ID(02),
    REAL_TIME_SENSOR_INFO_PACKET_SUBCMD_ID(03), REAL_TIME_SENSOR_INFO_PACKET_ACK_SUBCMD_ID(04),
    REAL_TIME_SENSOR_DATA_PACKET_SUBCMD_ID(05), REAL_TIME_SENSOR_DATA_PACKET_ACK_SUBCMD_ID(06);

    public int SubCmdId;

    REAL_TIME_SENSOR_SUBMCMDID(int SubCmdId) {
        this.SubCmdId = SubCmdId;
    }
}