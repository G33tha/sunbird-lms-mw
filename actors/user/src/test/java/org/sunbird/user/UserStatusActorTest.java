package org.sunbird.user;

import static akka.testkit.JavaTestKit.duration;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.KeyCloakConnectionProvider;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.models.user.User;
import org.sunbird.user.actors.UserStatusActor;
import org.sunbird.user.service.UserService;
import org.sunbird.user.service.impl.UserServiceImpl;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  UserServiceImpl.class,
  UserService.class,
  User.class,
  KeyCloakConnectionProvider.class,
  Keycloak.class,
  UserResource.class,
  UserRepresentation.class,
  UsersResource.class,
  RealmResource.class,
  CassandraOperationImpl.class,
  ServiceFactory.class
})
@PowerMockIgnore("javax.management.*")
public class UserStatusActorTest {

  private static final Props props = Props.create(UserStatusActor.class);
  private static ActorSystem system = ActorSystem.create("system");
  private String userId = "someUserId";
  private TestKit probe = new TestKit(system);
  private ActorRef subject = system.actorOf(props);
  private UserService userService;
  private User user;
  private Keycloak keycloak;
  private UserResource resource;
  private UsersResource usersResource;
  private UserRepresentation ur;
  private RealmResource realmResource;
  private CassandraOperationImpl cassandraOperation;

  @BeforeClass
  public static void beforeClass() {
    PowerMockito.mockStatic(UserServiceImpl.class);
  }

  @Before
  public void init() {

    PowerMockito.mockStatic(ServiceFactory.class);
    resource = mock(UserResource.class);
    usersResource = mock(UsersResource.class);
    ur = mock(UserRepresentation.class);
    realmResource = mock(RealmResource.class);
    cassandraOperation = mock(CassandraOperationImpl.class);

    when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
    keycloak = mock(Keycloak.class);
    //    PowerMockito.mockStatic(UserServiceImpl.class);
    userService = mock(UserService.class);
    when(UserServiceImpl.getInstance()).thenReturn(userService);
    PowerMockito.mockStatic(KeyCloakConnectionProvider.class);
    when(KeyCloakConnectionProvider.getConnection()).thenReturn(keycloak);
    when(keycloak.realm(Mockito.anyString())).thenReturn(realmResource);
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.get(Mockito.any())).thenReturn(resource);
    when(resource.toRepresentation()).thenReturn(ur);
    ur.setEnabled(Mockito.anyBoolean());
    user = mock(User.class);
    when(userService.getUserById(Mockito.anyString())).thenReturn(user);
  }

  /*@After
  public void teardown() {

    Mockito.reset(resource);
    Mockito.reset(usersResource);
    Mockito.reset(ur);
    Mockito.reset(realmResource);
    Mockito.reset(keycloak);
    Mockito.reset(userService);
    //    Mockito.reset(user);
    //    Mockito.reset(cassandraOperation);
  }*/

  @Test
  public void testBlockUserSuccess() {

    when(user.getIsDeleted()).thenReturn(false);
    Response response = createCassandraUpdateSuccessResponse();
    when(cassandraOperation.updateRecord(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(response);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.BLOCK_USER.getValue());
    reqObj.put(JsonKey.USER_ID, userId);
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(duration("10000 second"), Response.class);
    Assert.assertTrue(null != res && res.getResponseCode() == ResponseCode.OK);
  }

  @Test
  public void testBlockUserFailureWithUserAlreadyInactive() {

    when(user.getIsDeleted()).thenReturn(true);
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.BLOCK_USER.getValue());
    reqObj.put(JsonKey.USER_ID, userId);
    subject.tell(reqObj, probe.getRef());
    ProjectCommonException exception =
        probe.expectMsgClass(duration("10000 second"), ProjectCommonException.class);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.userAlreadyInactive.getErrorCode()));
  }

  @Test
  public void testUnBlockUserSuccess() {

    when(user.getIsDeleted()).thenReturn(true);
    Response response = createCassandraUpdateSuccessResponse();
    when(cassandraOperation.updateRecord(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(response);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.UNBLOCK_USER.getValue());
    reqObj.put(JsonKey.USER_ID, userId);
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(duration("10 second"), Response.class);
    Assert.assertTrue(null != res && res.getResponseCode() == ResponseCode.OK);
  }

  @Test
  public void testUnBlockUserFailureWithUserAlreadyActive() {

    when(user.getIsDeleted()).thenReturn(false);
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.UNBLOCK_USER.getValue());
    reqObj.put(JsonKey.USER_ID, userId);
    subject.tell(reqObj, probe.getRef());
    ProjectCommonException exception =
        probe.expectMsgClass(duration("10 second"), ProjectCommonException.class);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.userAlreadyActive.getErrorCode()));
  }

  private Response createCassandraUpdateSuccessResponse() {
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    return response;
  }
}
