package org.sunbird.user.actors;

import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.pattern.Patterns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.actorutil.InterServiceCommunication;
import org.sunbird.actorutil.InterServiceCommunicationFactory;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.Request;
import org.sunbird.user.util.UserActorOperations;
import scala.concurrent.Future;

@ActorConfig(
  tasks = {"saveUserAttributes"},
  asyncTasks = {"saveUserAttributes"}
)
public class UserProfileUpdateActor extends BaseActor {

  private static InterServiceCommunication interServiceCommunication =
      InterServiceCommunicationFactory.getInstance();

  @Override
  public void onReceive(Request request) throws Throwable {
    if (UserActorOperations.SAVE_USER_ATTRIBUTES
        .getValue()
        .equalsIgnoreCase(request.getOperation())) {
      saveUserAttributes(request);
    } else {
      onReceiveUnsupportedOperation("UserAttributesProcessingActor");
    }
  }

  @SuppressWarnings("unchecked")
  private void saveUserAttributes(Request request) {
    Response response = new Response();
    Map<String, Object> userMap = request.getRequest();
    List<Future<Object>> futures = new ArrayList<>();
    String operationType = (String) userMap.get(JsonKey.OPERATION_TYPE);
    userMap.remove(JsonKey.OPERATION_TYPE);
    if (userMap.containsKey(JsonKey.ADDRESS)
        && CollectionUtils.isNotEmpty((List<Map<String, Object>>) userMap.get(JsonKey.ADDRESS))) {
      futures.add(saveAddress(userMap, operationType));
    }
    if (userMap.containsKey(JsonKey.EDUCATION)
        && CollectionUtils.isNotEmpty((List<Map<String, Object>>) userMap.get(JsonKey.EDUCATION))) {
      futures.add(saveEducation(userMap, operationType));
    }
    if (userMap.containsKey(JsonKey.JOB_PROFILE)
        && CollectionUtils.isNotEmpty(
            (List<Map<String, Object>>) userMap.get(JsonKey.JOB_PROFILE))) {
      futures.add(saveJobProfile(userMap, operationType));
    }
    if (CollectionUtils.isNotEmpty((List<Map<String, String>>) userMap.get(JsonKey.EXTERNAL_IDS))) {
      futures.add(saveUserExternalIds(userMap, operationType));
    }
    if (StringUtils.isNotBlank((String) userMap.get(JsonKey.ORGANISATION_ID))
        || StringUtils.isNotBlank((String) userMap.get(JsonKey.ROOT_ORG_ID))) {
      futures.add(saveUserOrgDetails(userMap, operationType));
    }
    Future<Iterable<Object>> futuresSequence = Futures.sequence(futures, getContext().dispatcher());
    Future<Map<String, Object>> futureResponse =
        futuresSequence.map(
            new Mapper<Iterable<Object>, Map<String, Object>>() {
              Map<String, Object> map = new HashMap<>();
              List<Object> errorList = new ArrayList<>();

              @Override
              public Map<String, Object> apply(Iterable<Object> futureResult) {
                for (Object object : futureResult) {
                  if (object instanceof Response) {
                    Response response = (Response) object;
                    Map<String, Object> result = response.getResult();
                    String key = (String) result.get(JsonKey.KEY);
                    List<String> errMsgList = (List<String>) result.get(JsonKey.ERROR_MSG);
                    if (CollectionUtils.isNotEmpty(errMsgList)) {
                      for (String err : errMsgList) {
                        errorList.add(key + ":" + err);
                      }
                    }
                  } else if (object instanceof ProjectCommonException) {
                    errorList.add(((ProjectCommonException) object).getMessage());
                  } else if (object instanceof Exception) {
                    errorList.add(((Exception) object).getMessage());
                  }
                }
                map.put(JsonKey.ERROR_MSG, errorList);
                return map;
              }
            },
            getContext().dispatcher());
    Patterns.pipe(futureResponse, getContext().dispatcher()).to(sender());
    sender().tell(response, self());
  }

  @SuppressWarnings("unchecked")
  private Future<Object> saveUserExternalIds(Map<String, Object> userMap, String operationType) {
    try {
      if (CollectionUtils.isNotEmpty(
          (List<Map<String, String>>) userMap.get(JsonKey.EXTERNAL_IDS))) {
        Request userExternalIdsRequest = new Request();
        userExternalIdsRequest.getRequest().putAll(userMap);
        userExternalIdsRequest.setOperation(
            UserActorOperations.UPSERT_USER_EXTERNAL_IDENTITY_DETAILS.getValue());
        return interServiceCommunication.getFuture(
            getActorRef(UserActorOperations.UPSERT_USER_EXTERNAL_IDENTITY_DETAILS.getValue()),
            userExternalIdsRequest);
      }
    } catch (Exception ex) {
      ProjectLogger.log(
          "UserProfileUpdateActor:saveUserExternalIds: Exception occurred while saving user externalIds.",
          ex);
    }
    return null;
  }

  private Future<Object> saveUserOrgDetails(Map<String, Object> userMap, String operationType) {
    try {
      if (StringUtils.isNotBlank((String) userMap.get(JsonKey.ORGANISATION_ID))
          || StringUtils.isNotBlank((String) userMap.get(JsonKey.ROOT_ORG_ID))) {
        Request userOrgRequest = new Request();
        userOrgRequest.getRequest().putAll(userMap);
        String actorOperation = "";
        if (JsonKey.CREATE.equalsIgnoreCase(operationType)) {
          actorOperation = UserActorOperations.INSERT_USER_ORG_DETAILS.getValue();
          userOrgRequest.setOperation(actorOperation);
        } else {
          actorOperation = UserActorOperations.UPDATE_USER_ORG_DETAILS.getValue();
          userOrgRequest.setOperation(actorOperation);
        }
        return interServiceCommunication.getFuture(getActorRef(actorOperation), userOrgRequest);
      }
    } catch (Exception ex) {
      ProjectLogger.log(
          "UserProfileUpdateActor:saveUserOrgDetails: Exception occurred while saving user org details.",
          ex);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private Future<Object> saveJobProfile(Map<String, Object> userMap, String operationType) {
    try {
      if (userMap.containsKey(JsonKey.JOB_PROFILE)
          && CollectionUtils.isNotEmpty(
              (List<Map<String, Object>>) userMap.get(JsonKey.JOB_PROFILE))) {
        Request jobProfileRequest = new Request();
        jobProfileRequest.getRequest().putAll(userMap);
        String actorOperation = "";
        if (JsonKey.CREATE.equalsIgnoreCase(operationType)) {
          actorOperation = UserActorOperations.INSERT_USER_JOB_PROFILE.getValue();
          jobProfileRequest.setOperation(actorOperation);
        } else {
          actorOperation = UserActorOperations.UPDATE_USER_JOB_PROFILE.getValue();
          jobProfileRequest.setOperation(actorOperation);
        }
        return interServiceCommunication.getFuture(getActorRef(actorOperation), jobProfileRequest);
      }
    } catch (Exception ex) {
      ProjectLogger.log(
          "UserProfileUpdateActor:saveJobProfile: Exception occurred while saving user job profile details.",
          ex);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private Future<Object> saveEducation(Map<String, Object> userMap, String operationType) {
    try {
      if (userMap.containsKey(JsonKey.EDUCATION)
          && CollectionUtils.isNotEmpty(
              (List<Map<String, Object>>) userMap.get(JsonKey.EDUCATION))) {
        Request educationRequest = new Request();
        educationRequest.getRequest().putAll(userMap);
        String actorOperation = "";
        if (JsonKey.CREATE.equalsIgnoreCase(operationType)) {
          actorOperation = UserActorOperations.INSERT_USER_EDUCATION.getValue();
          educationRequest.setOperation(actorOperation);
        } else {
          actorOperation = UserActorOperations.UPDATE_USER_EDUCATION.getValue();
          educationRequest.setOperation(actorOperation);
        }
        return interServiceCommunication.getFuture(getActorRef(actorOperation), educationRequest);
      }
    } catch (Exception ex) {
      ProjectLogger.log(
          "UserProfileUpdateActor:saveEducation: Exception occurred while saving user education details.",
          ex);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private Future<Object> saveAddress(Map<String, Object> userMap, String operationType) {
    try {
      if (userMap.containsKey(JsonKey.ADDRESS)
          && CollectionUtils.isNotEmpty((List<Map<String, Object>>) userMap.get(JsonKey.ADDRESS))) {
        Request addressRequest = new Request();
        addressRequest.getRequest().putAll(userMap);
        String actorOperation = "";
        if (JsonKey.CREATE.equalsIgnoreCase(operationType)) {
          actorOperation = UserActorOperations.INSERT_USER_ADDRESS.getValue();
          addressRequest.setOperation(actorOperation);
        } else {
          actorOperation = UserActorOperations.UPDATE_USER_ADDRESS.getValue();
          addressRequest.setOperation(actorOperation);
        }
        return interServiceCommunication.getFuture(getActorRef(actorOperation), addressRequest);
      }
    } catch (Exception ex) {
      ProjectLogger.log(
          "UserProfileUpdateActor:saveAddress: Exception occurred while saving user address details.",
          ex);
    }
    return null;
  }
}
