package org.sunbird.user.actors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.request.UserRequestValidator;
import org.sunbird.services.sso.SSOManager;
import org.sunbird.services.sso.SSOServiceFactory;
import org.sunbird.telemetry.util.TelemetryUtil;
import org.sunbird.user.dao.UserDao;
import org.sunbird.user.dao.UserDaoFactory;

import akka.actor.ActorRef;

public abstract class UserBaseActor extends BaseActor {

  private UserRequestValidator userRequestValidator = new UserRequestValidator();
  private boolean isSSOEnabled =
      Boolean.parseBoolean(PropertiesCache.getInstance().getProperty(JsonKey.IS_SSO_ENABLED));
  private ActorRef systemSettingActorRef =
      getActorRef(ActorOperations.GET_SYSTEM_SETTING.getValue());
  private SSOManager ssoManager = SSOServiceFactory.getInstance();
  private UserDao userDao = UserDaoFactory.getUserDao();

  protected void generateTeleEventForUser(
      Map<String, Object> requestMap, String userId, String objectType) {
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    Map<String, Object> targetObject =
        TelemetryUtil.generateTargetObject(userId, JsonKey.USER, JsonKey.UPDATE, null);
    Map<String, Object> telemetryAction = new HashMap<>();
    if (objectType.equalsIgnoreCase("orgLevel")) {
      telemetryAction.put("AssignRole", "role assigned at org level");
      if (null != requestMap) {
        TelemetryUtil.generateCorrelatedObject(
            (String) requestMap.get(JsonKey.ORGANISATION_ID),
            JsonKey.ORGANISATION,
            null,
            correlatedObject);
      }
    } else {
      if (objectType.equalsIgnoreCase("userLevel")) {
        telemetryAction.put("AssignRole", "role assigned at user level");
      } else if (objectType.equalsIgnoreCase("blockUser")) {
        telemetryAction.put("BlockUser", "user blocked");
      } else if (objectType.equalsIgnoreCase("unBlockUser")) {
        telemetryAction.put("UnBlockUser", "user unblocked");
      } else if (objectType.equalsIgnoreCase("profileVisibility")) {
        telemetryAction.put("ProfileVisibility", "profile Visibility setting changed");
      }
    }
    TelemetryUtil.telemetryProcessingCall(telemetryAction, targetObject, correlatedObject);
  }

  protected ActorRef getSystemSettingActorRef() {
    return systemSettingActorRef;
  }

  protected SSOManager getSsoManager() {
    return ssoManager;
  }

  protected boolean isSSOEnabled() {
    return isSSOEnabled;
  }

  protected UserRequestValidator getUserRequestValidator() {
    return userRequestValidator;
  }
  
  protected UserDao getUserDao() {
	  return userDao;
  }

}
