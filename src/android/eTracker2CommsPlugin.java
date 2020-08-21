package com.mti.eTracker2CommsPlugin;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;

import com.telit.terminalio.TIOManager;
import com.telit.terminalio.TIOManagerCallback;
import com.telit.terminalio.TIOConnectionCallback;
import com.telit.terminalio.TIOPeripheral;
import com.telit.terminalio.TIOConnection;

import java.lang.*;

public class eTracker2CommsPlugin extends CordovaPlugin {

    // region Member variables

    private TIOManager tioManager;
    private CallbackContext callbackContext;
    private ETrackerCommand cmdReg;

    // endregion

    // region Constructor

    public eTracker2CommsPlugin() {
        cmdReg = new ETrackerCommand();
        cmdReg.InitCommandDictonary();
    }

    // endregion

    // region Public method

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {

        String msg = "";
        boolean flag = true;
        this.callbackContext = callbackContext;

        try {
            if (action == null || action.trim().equals("")) {
                throw new Exception("Invalid action received");
            }

            Context context = this.cordova.getActivity().getApplicationContext();
            if (context == null) {
                throw new Exception("Empty application context");
            }

            TIOManager.initialize(context);
            tioManager = TIOManager.getInstance();
            if (tioManager == null) {
                throw new Exception("Empty TIOManager instance");
            }

            if (action.equalsIgnoreCase(ETrackerAction.DISCOVER_STATIONS)) {
                new DeviceDiscoveryController().execute(tioManager, callbackContext);

            } else if (action.equalsIgnoreCase(ETrackerAction.CONNECT_STATION)) {
                String stationAddress = args.getString(0);
                new DeviceConnectionController(action).connect(tioManager, stationAddress,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.AUTHENTICATE_USER)) {
                JSONObject authData = args.getJSONObject(0);
                new PeripheralAuthController().execute(authData,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.GET_SDI12_SENSOR_DATA)) {
                String cmd = args.getString(0);
                int channel = Integer.parseInt(args.getString(1));
                new SDI12PassthroughController().execute(cmd, channel,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.DISCONNECT_STATION)) {
                new DeviceConnectionController(action)
                        .disconnectStation((PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.LOGOUT)) {
                new DeviceConnectionController(action).logout(context,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.SET_CONFIG)) {
                String fileData = args.getString(0);
                new FileTransferController().execute(fileData,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.COMMIT_CONFIG_UPDATE)) {
                new FileTransferController()
                        .commitUpdate((PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });
            } else if (action.equalsIgnoreCase(ETrackerAction.GET_CONFIG)) {
                new ConfigReaderController()
                        .execute((PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.GET_DEVICE_DETAILS)) {
                new DeviceInfoController()
                        .execute((PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.TIME_SYNC)) {
                new TimeSyncController()
                        .execute((PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.SWITCH_TO_LM)) {
                new GenericCommandController().execute(action,ETrackerCommand.CMD_SWITCH_LM,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.CHECK_SD_STATUS)) {
                new GenericCommandController().execute(ETrackerCommand.CMD_CHECK_SD,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.CHECK_NETWORK)) {
                int network_comm_method = Integer.parseInt(args.getString(0));
                new GenericCommandController().execute(ETrackerCommand.CMD_CHECK_NETWORK, network_comm_method,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.RESET_PASSWORD)) {
                String unique_code = args.getString(0);
                new GenericCommandController().execute(2, unique_code,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.REPORT_CONFIG)) {
                new GenericCommandController().execute(ETrackerCommand.CMD_REPORT_CONFIG,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.REPORT_READINGS)) {
                new GenericCommandController().execute(ETrackerCommand.CMD_REPORT_READINGS,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.SET_PASSWORD)) {
                String password = args.getString(0);
                new SetPasswordController().execute(password,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });

            } else if (action.equalsIgnoreCase(ETrackerAction.Scan_SDI12_CHANNEL)) {
                String channel = args.getString(0);
                new ScanSDI12Controller().execute(Integer.parseInt(channel),
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });
            } else if (action.equalsIgnoreCase(ETrackerAction.REAL_TIME_SENSOR_READINGS)) {
                new SensorReadingsController()
                        .execute((PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });
            } else if (action.equalsIgnoreCase(ETrackerAction.APPLY_ACTIONS)) {
                int subCmdId = Integer.parseInt(args.getString(0));
                String data = args.getString(1);
                new ApplyActionsController().execute(subCmdId, data,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });
            } else if (action.equalsIgnoreCase(ETrackerAction.TEMPORARY_OVERRIDE)) {
                String fileData = args.getString(0);
                int slot = Integer.parseInt(args.getString(1));
                int expires = Integer.parseInt(args.getString(2));
                new TemporaryOverrideController().execute(fileData, slot, expires,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });
            } else if (action.equalsIgnoreCase(ETrackerAction.CERTIFICATE_DOWNLOAD)) {
                String fileName = args.getString(0);
                new Certificate_DownloadController().execute(fileName,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });
            } else if (action.equalsIgnoreCase(ETrackerAction.FIRMWARE_UPLOAD)) {
                String fileName = args.getString(0);
                int majVer = Integer.parseInt(args.getString(1));
                int minVer = Integer.parseInt(args.getString(2));

                new Firmware_UpgradeController().execute(fileName, majVer, minVer,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });
            } else if (action.equalsIgnoreCase(ETrackerAction.CHECK_CRC)) {
                String fileName = args.getString(0);
                int crc = Integer.parseInt(args.getString(1));

                new GenericCommandController().execute(fileName, crc,
                        (PluginResult.Status execStat, String result, boolean keepCallbackOpen) -> {
                            sendPluginResult(execStat, result, keepCallbackOpen);
                        });
            }

        } catch (Exception ex) {
            String exMsg = "Plugin internal error. " + ex.toString();
            msg = PluginUtil.composePluginExecResult(false, false, exMsg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
            callbackContext.error(msg);
        }

        return flag;
    }

    // endregion

    // region Private methods

    private void sendPluginResult(PluginResult.Status status, String msg, boolean keepCallbackOpen) {

        try {
            PluginResult result = new PluginResult(status, msg);
            result.setKeepCallback(keepCallbackOpen);
            callbackContext.sendPluginResult(result);
        } catch (Exception ex) {
            String exMsg = "Plugin internal error. Error occured while sending plugin result. " + ex.toString();
            msg = PluginUtil.composePluginExecResult(false, false, exMsg, ETrackerStatusCode.PLUGIN_INTERNAL_ERROR);
            callbackContext.error(msg);
        }
    }
    // endregion

}
