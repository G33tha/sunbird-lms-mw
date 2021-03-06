package org.sunbird.systemsettings.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.MessageFormat;
import java.util.*;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.actorutil.user.UserClient;
import org.sunbird.actorutil.user.impl.UserClientImpl;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.*;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.Util;
import org.sunbird.models.systemsetting.SystemSetting;
import org.sunbird.systemsettings.dao.impl.SystemSettingDaoImpl;

@ActorConfig(
  tasks = {"getSystemSetting", "getAllSystemSettings", "setSystemSetting"},
  asyncTasks = {}
)
public class SystemSettingsActor extends BaseActor {
  private final ObjectMapper mapper = new ObjectMapper();
  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private UserClient userClient = new UserClientImpl();
  private final SystemSettingDaoImpl systemSettingDaoImpl =
      new SystemSettingDaoImpl(cassandraOperation);

  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.SYSTEM_SETTINGS);
    ExecutionContext.setRequestId(request.getRequestId());

    switch (request.getOperation()) {
      case "getSystemSetting":
        getSystemSetting(request);
        break;
      case "getAllSystemSettings":
        getAllSystemSettings();
        break;
      case "setSystemSetting":
        setSystemSetting(request);
        break;
      default:
        onReceiveUnsupportedOperation(request.getOperation());
        break;
    }
  }

  @SuppressWarnings("unchecked")
  private void getSystemSetting(Request actorMessage) {
    ProjectLogger.log("SystemSettingsActor: getSystemSetting called", LoggerEnum.INFO.name());
    ProjectLogger.log(
        "SystemSettingsActor:getSystemSetting: request is " + actorMessage.getRequest(),
        LoggerEnum.INFO.name());
    SystemSetting setting =
        systemSettingDaoImpl.readByField((String) actorMessage.getContext().get(JsonKey.FIELD));

    if (setting == null) {
      throw new ProjectCommonException(
          ResponseCode.resourceNotFound.getErrorCode(),
          ResponseCode.resourceNotFound.getErrorMessage(),
          ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }

    Response response = new Response();
    response.put(JsonKey.RESPONSE, setting);
    sender().tell(response, self());
  }

  @SuppressWarnings("unchecked")
  private void getAllSystemSettings() {
    ProjectLogger.log("SystemSettingsActor: getAllSystemSettings called", LoggerEnum.DEBUG.name());
    List<SystemSetting> allSystemSettings = systemSettingDaoImpl.readAll();
    Response response = new Response();
    response.put(JsonKey.RESPONSE, allSystemSettings);
    sender().tell(response, self());
  }

  private void setSystemSetting(Request actorMessage) {
    ProjectLogger.log("SystemSettingsActor: setSystemSetting called", LoggerEnum.DEBUG.name());

    Map<String, Object> request = actorMessage.getRequest();
    String id = (String) request.get(JsonKey.ID);
    String field = (String) request.get(JsonKey.FIELD);
    if (JsonKey.PHONE_UNIQUE.equalsIgnoreCase(field)
        || JsonKey.EMAIL_UNIQUE.equalsIgnoreCase(field)
        || JsonKey.PHONE_UNIQUE.equalsIgnoreCase(id)
        || JsonKey.EMAIL_UNIQUE.equalsIgnoreCase(id)) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.errorUpdateSettingNotAllowed,
          MessageFormat.format(ResponseCode.errorUpdateSettingNotAllowed.getErrorMessage(), field));
    }

    SystemSetting systemSetting = mapper.convertValue(request, SystemSetting.class);
    Response response = systemSettingDaoImpl.write(systemSetting);
    sender().tell(response, self());
  }
}
