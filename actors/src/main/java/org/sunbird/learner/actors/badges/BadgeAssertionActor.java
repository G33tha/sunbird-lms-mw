/**
 * 
 */
package org.sunbird.learner.actors.badges;

import java.io.IOException;
import java.util.Map;

import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.BadgingUtil;
import org.sunbird.learner.util.Util;

import akka.actor.UntypedAbstractActor;

/**
 * @author Manzarul
 *
 */
public class BadgeAssertionActor extends UntypedAbstractActor {



  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private Util.DbInfo badgesDbInfo = Util.dbInfoMap.get(JsonKey.BADGES_DB);
  private Util.DbInfo userBadgesDbInfo = Util.dbInfoMap.get(JsonKey.USER_BADGES_DB);

  @Override
  public void onReceive(Object message) throws Throwable {
    if (message instanceof Request) {
      try {
        ProjectLogger.log("BadgeAssertionActor  onReceive called",LoggerEnum.INFO.name());
        Request actorMessage = (Request) message;
        Util.initializeContext(actorMessage, JsonKey.USER);
        // set request id fto thread loacl...
        ExecutionContext.setRequestId(actorMessage.getRequestId());
        if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.CREATE_BADGE_ASSERTION.getValue())) {
         createAssertion(actorMessage);	
        } else if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.GET_BADGE_ASSERTION.getValue())) {
        } else if (actorMessage.getOperation()
            .equalsIgnoreCase(ActorOperations.GET_BADGE_ASSERTION_LIST.getValue())) {
        } else if (actorMessage.getOperation().equalsIgnoreCase(ActorOperations.REVOKE_BADGE.getValue())) {
        }
        else {
          ProjectLogger.log("UNSUPPORTED OPERATION");
          ProjectCommonException exception =
              new ProjectCommonException(ResponseCode.invalidOperationName.getErrorCode(),
                  ResponseCode.invalidOperationName.getErrorMessage(),
                  ResponseCode.CLIENT_ERROR.getResponseCode());
          sender().tell(exception, self());
        }
      } catch (Exception ex) {
        ProjectLogger.log(ex.getMessage(), ex);
        sender().tell(ex, self());
      }
    } else {
      // Throw exception as message body
      ProjectLogger.log("UNSUPPORTED MESSAGE");
      ProjectCommonException exception =
          new ProjectCommonException(ResponseCode.invalidRequestData.getErrorCode(),
              ResponseCode.invalidRequestData.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
      sender().tell(exception, self());
    }

  }
   
   /**
    * This method will call the badger server to create badge assertion.
    * @param actorMessage Request
    */
	private void createAssertion(Request actorMessage) {
        Map<String,Object> requestedData = actorMessage.getRequest();
        String requestBody = BadgingUtil.createAssertionReqData(requestedData);
        String url = BadgingUtil.createAssertionUrl(requestedData);
        try {
		  String response = HttpUtil.sendPostRequest(url, requestBody, BadgingUtil.createBadgerHeader());
		  Response result = new Response();
		  result.put(JsonKey.RESPONSE, response);
		  sender().tell(result, self());
		} catch (IOException e) {
			 sender().tell(e, self());
		}
	}
	
	
	
	

}
