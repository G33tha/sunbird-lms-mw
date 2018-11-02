package org.sunbird.learner.actors.bulkupload;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.actorutil.org.OrganisationClient;
import org.sunbird.actorutil.org.impl.OrganisationClientImpl;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.*;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.actors.bulkupload.model.BulkUploadProcess;
import org.sunbird.learner.actors.bulkupload.model.BulkUploadProcessTask;
import org.sunbird.learner.util.Util;
import org.sunbird.models.organisation.Organisation;

@ActorConfig(
  tasks = {},
  asyncTasks = {"orgBulkUploadBackground"}
)
public class OrgBulkUploadBackGroundJobActor extends BaseBulkUploadBackgroundJobActor {
  private OrganisationClient orgClient = new OrganisationClientImpl();

  @Override
  public void onReceive(Request request) throws Throwable {
    String operation = request.getOperation();
    Util.initializeContext(request, TelemetryEnvKey.ORGANISATION);
    ExecutionContext.setRequestId(request.getRequestId());
    if (operation.equalsIgnoreCase("orgBulkUploadBackground")) {
      handleBulkUploadBackground(
          request,
          (baseBulkUpload) -> {
            processBulkUpload(
                (BulkUploadProcess) baseBulkUpload,
                (tasks) -> {
                  processTasks((List<BulkUploadProcessTask>) tasks);
                  return null;
                });
            return null;
          });
    } else {
      onReceiveUnsupportedOperation("OrgBulkUploadBackGroundJobActor");
    }
  }

  private void processTasks(List<BulkUploadProcessTask> bulkUploadProcessTasks) {
    for (BulkUploadProcessTask task : bulkUploadProcessTasks) {
      try {
        if (task.getStatus() != null
            && task.getStatus() != ProjectUtil.BulkProcessStatus.COMPLETED.getValue()) {
          processOrg(task);
          task.setLastUpdatedOn(new Timestamp(System.currentTimeMillis()));
          task.setIterationId(task.getIterationId() + 1);
        }
      } catch (Exception ex) {
        task.setFailureResult(ex.getMessage());
      }
    }
  }

  private void processOrg(BulkUploadProcessTask task) {
    ProjectLogger.log("OrgBulkUploadBackGroundJobActor: processOrg called", LoggerEnum.INFO);
    String data = task.getData();
    try {
      Map<String, Object> orgMap = mapper.readValue(data, Map.class);
      Organisation organisation = mapper.convertValue(orgMap, Organisation.class);
      organisation.setId((String) orgMap.get(JsonKey.ORGANISATION_ID));
      if (StringUtils.isEmpty(organisation.getId())) {
        callCreateOrg(organisation, task);
      } else {
        callUpdateOrg(organisation, task);
      }
    } catch (IOException e) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.getErrorMessage());
    }
  }

  private void callCreateOrg(Organisation org, BulkUploadProcessTask task)
      throws JsonProcessingException {
    Map<String, Object> row = mapper.convertValue(org, Map.class);
    String orgId;
    try {
      orgId = orgClient.createOrg(getActorRef(ActorOperations.CREATE_ORG.getValue()), row);
    } catch (Exception ex) {
      ProjectLogger.log(
          "OrgBulkUploadBackGroundJobActor:callCreateOrg: Exception occurred with error message = "
              + ex.getMessage(),
          LoggerEnum.INFO);
      setTaskStatus(
          task, ProjectUtil.BulkProcessStatus.FAILED, ex.getMessage(), row, JsonKey.CREATE);
      return;
    }

    if (StringUtils.isEmpty(orgId)) {
      ProjectLogger.log(
          "OrgBulkUploadBackGroundJobActor:callCreateOrg: Org ID is null !", LoggerEnum.ERROR);
      setTaskStatus(
          task,
          ProjectUtil.BulkProcessStatus.FAILED,
          ResponseCode.internalError.getErrorMessage(),
          row,
          JsonKey.CREATE);
    } else {
      row.put(JsonKey.ORGANISATION_ID, orgId);
      setSuccessTaskStatus(task, ProjectUtil.BulkProcessStatus.COMPLETED, row, JsonKey.CREATE);
    }
  }

  private void callUpdateOrg(Organisation org, BulkUploadProcessTask task)
      throws JsonProcessingException {
    Map<String, Object> row = mapper.convertValue(org, Map.class);
    try {
      row.put(JsonKey.ORGANISATION_ID, org.getId());
      orgClient.updateOrg(getActorRef(ActorOperations.UPDATE_ORG.getValue()), row);
    } catch (Exception ex) {
      ProjectLogger.log(
          "OrgBulkUploadBackGroundJobActor:callUpdateOrg: Exception occurred with error message = "
              + ex.getMessage(),
          LoggerEnum.INFO);
      row.put(JsonKey.ERROR_MSG, ex.getMessage());
      setTaskStatus(
          task, ProjectUtil.BulkProcessStatus.FAILED, ex.getMessage(), row, JsonKey.UPDATE);
    }

    task.setData(mapper.writeValueAsString(row));
    setSuccessTaskStatus(task, ProjectUtil.BulkProcessStatus.COMPLETED, row, JsonKey.UPDATE);
  }
}
