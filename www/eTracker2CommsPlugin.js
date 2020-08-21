var exec = require("cordova/exec");

var eTracker2CommsPlugin = function () {
  console.log("Custom plugin instanced");
};

eTracker2CommsPlugin.prototype.DiscoverStations = function (onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "DiscoverStations",
    []
  );
};

eTracker2CommsPlugin.prototype.ConnectStation = function (
  pData,
  onSuccess,
  onError
) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "ConnectStation",
    [pData]
  );
};

eTracker2CommsPlugin.prototype.AuthenticateUser = function (
  pData,
  onSuccess,
  onError
) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "AuthenticateUser",
    [pData]
  );
};

eTracker2CommsPlugin.prototype.DisconnectStation = function (
  pData,
  onSuccess,
  onError
) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "DisconnectStation",
    []
  );
};

eTracker2CommsPlugin.prototype.GetSDI12SensorData = function (
  pData,
  channel,
  onSuccess,
  onError
) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "GetSDI12SensorData",
    [pData, channel]
  );
};

eTracker2CommsPlugin.prototype.GetConfiguration = function (
  pSUI,
  onSuccess,
  onError
) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "GetConfiguration",
    []
  );
};

eTracker2CommsPlugin.prototype.SetConfiguration = function (
  pData,
  onSuccess,
  onError
) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "SetConfiguration",
    [pData]
  );
};

eTracker2CommsPlugin.prototype.CommitConfiguration = function (
  onSuccess,
  onError
) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "CommitConfigUpdate",
    []
  );
};

eTracker2CommsPlugin.prototype.Logout = function (pData, onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(successCallback, errorCallback, "eTracker2CommsPlugin", "Logout", []);
};

eTracker2CommsPlugin.prototype.GetConnectedDeviceDetails = function (
  onSuccess,
  onError
) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "GetConnectedDeviceDetails",
    []
  );
};

eTracker2CommsPlugin.prototype.DoTimeSync = function (onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(successCallback, errorCallback, "eTracker2CommsPlugin", "TimeSync", []);
};

eTracker2CommsPlugin.prototype.ScanSDI12Channel = function (
  pData,
  onSuccess,
  onError
) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "ScanSDI12channel",
    [pData]
  );
};

eTracker2CommsPlugin.prototype.CheckSDCardStatus = function (
  onSuccess,
  onError
) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "CheckSDCardStatus",
    []
  );
};

eTracker2CommsPlugin.prototype.ResetPassword = function (pData, onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "ResetPassword",
    [pData]
  );
};

eTracker2CommsPlugin.prototype.SetPassword = function (pData, onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "SetPassword",
    [pData]
  );
};

eTracker2CommsPlugin.prototype.SwitchToLM = function (onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "SwitchToLM",
    []
  );
};

eTracker2CommsPlugin.prototype.CheckNetwork = function (pData, onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "CheckNetwork",
    [pData]
  );
};

eTracker2CommsPlugin.prototype.GetSensorReadings = function (onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "SensorReadings",
    []
  );
};

eTracker2CommsPlugin.prototype.ApplyActions = function (cmdId, pData, onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "ApplyActions",
    [cmdId, pData]
  );
};

eTracker2CommsPlugin.prototype.TemporaryOverride = function (pData, slot, expires, onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "TemporaryOverride",
    [pData, slot, expires]
  );
};

eTracker2CommsPlugin.prototype.ReportReadingsToServer = function (onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "ReportReadings",
    []
  );
};

eTracker2CommsPlugin.prototype.ReportConfigToServer = function (onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "ReportConfig",
    []
  );
};

eTracker2CommsPlugin.prototype.CertificateDownload = function (pData, onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "CertificateDownload",
    [pData]
  );
};

eTracker2CommsPlugin.prototype.UploadFirmware = function (pData, majversion, minversion, onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "FirmwareUpload",
    [pData, majversion, minversion]
  );
};

eTracker2CommsPlugin.prototype.CheckCRC = function (filename, crc, onSuccess, onError) {
  var errorCallback = function (obj) {
    onError(obj);
  };

  var successCallback = function (obj) {
    onSuccess(obj);
  };

  exec(
    successCallback,
    errorCallback,
    "eTracker2CommsPlugin",
    "CheckCRC",
    [filename, crc]
  );
};

if (typeof module != "undefined" && module.exports) {
  module.exports = eTracker2CommsPlugin;
}
