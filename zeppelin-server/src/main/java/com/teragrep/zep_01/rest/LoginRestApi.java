/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.teragrep.zep_01.rest;

import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.gson.Gson;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.Subject;
import com.teragrep.zep_01.annotation.ZeppelinApi;
import com.teragrep.zep_01.conf.ZeppelinConfiguration;
import com.teragrep.zep_01.notebook.AuthorizationService;
import com.teragrep.zep_01.notebook.Notebook;
import com.teragrep.zep_01.realm.jwt.JWTAuthenticationToken;
import com.teragrep.zep_01.realm.jwt.KnoxJwtRealm;
import com.teragrep.zep_01.realm.kerberos.KerberosRealm;
import com.teragrep.zep_01.realm.kerberos.KerberosToken;
import com.teragrep.zep_01.server.JsonResponse;
import com.teragrep.zep_01.service.AuthenticationService;
import com.teragrep.zep_01.ticket.TicketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created for com.teragrep.zep_01.rest.message.
 */
@Path("/login")
@Produces("application/json")
@Singleton
public class LoginRestApi {
  private static final Logger LOG = LoggerFactory.getLogger(LoginRestApi.class);
  private static final Gson GSON = new Gson();
  private final ZeppelinConfiguration zConf;

  private final AuthenticationService authenticationService;
  private final AuthorizationService authorizationService;

  @Inject
  public LoginRestApi(Notebook notebook,
                      AuthenticationService authenticationService,
                      AuthorizationService authorizationService) {
    this.zConf = notebook.getConf();
    this.authenticationService = authenticationService;
    this.authorizationService = authorizationService;
  }

  @GET
  @ZeppelinApi
  public Response getLogin(@Context HttpHeaders headers) {
    JsonResponse<Map<String, String>> response = null;
    if (isKnoxSSOEnabled()) {
      KnoxJwtRealm knoxJwtRealm = getJTWRealm();
      Cookie cookie = headers.getCookies().get(knoxJwtRealm.getCookieName());
      if (cookie != null && cookie.getValue() != null) {
        Subject currentUser = SecurityUtils.getSubject();
        JWTAuthenticationToken token = new JWTAuthenticationToken(null, cookie.getValue());
        try {
          String name = knoxJwtRealm.getName(token);
          if (!currentUser.isAuthenticated() || !currentUser.getPrincipal().equals(name)) {
            response = proceedToLogin(currentUser, token);
          }
        } catch (ParseException e) {
          LOG.error("ParseException in LoginRestApi: ", e);
        }
      }
      if (response == null) {
        Map<String, String> data = new HashMap<>();
        data.put("redirectURL",
            constructUrl(knoxJwtRealm.getProviderUrl(), knoxJwtRealm.getRedirectParam(),
                knoxJwtRealm.getLogin()));
        response = new JsonResponse<>(Status.OK, "", data);
      }
      return response.build();
    }

    KerberosRealm kerberosRealm = getKerberosRealm();
    if (null != kerberosRealm) {
      try {
        Map<String, Cookie> cookies = headers.getCookies();
        KerberosToken kerberosToken = KerberosRealm.getKerberosTokenFromCookies(cookies);
        if (null != kerberosToken) {
          Subject currentUser = SecurityUtils.getSubject();
          String name = (String) kerberosToken.getPrincipal();
          if (!currentUser.isAuthenticated() || !currentUser.getPrincipal().equals(name)) {
            response = proceedToLogin(currentUser, kerberosToken);
          }
        }
        if (null == response) {
          LOG.warn("No Kerberos token received");
          response = new JsonResponse<>(Status.UNAUTHORIZED, "", null);
        }
        return response.build();
      } catch (AuthenticationException e){
        LOG.error("Error in Login", e);
      }
    }
    return new JsonResponse<>(Status.METHOD_NOT_ALLOWED).build();
  }

  private KerberosRealm getKerberosRealm() {
    Collection<Realm> realmsList = authenticationService.getRealmsList();
    if (realmsList != null) {
      for (Realm realm : realmsList) {
        String name = realm.getClass().getName();

        LOG.debug("RealmClass.getName: {}", name);

        if (name.equals("com.teragrep.zep_01.realm.kerberos.KerberosRealm")) {
          return (KerberosRealm) realm;
        }
      }
    }
    return null;
  }

  private KnoxJwtRealm getJTWRealm() {
    Collection<Realm> realmsList = authenticationService.getRealmsList();
    if (realmsList != null) {
      for (Realm realm : realmsList) {
        if (realm instanceof KnoxJwtRealm) {
          return (KnoxJwtRealm) realm;
        }
      }
    }
    return null;
  }

  private boolean isKnoxSSOEnabled() {
    Collection<Realm> realmsList = authenticationService.getRealmsList();
    if (realmsList != null) {
      for (Realm realm : realmsList) {
        if (realm instanceof KnoxJwtRealm) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isKerberosRealmEnabled() {
    Collection<Realm> realmsList = authenticationService.getRealmsList();
    if (realmsList != null) {
      for (Realm realm : realmsList) {
        if (realm instanceof KerberosRealm) {
          return true;
        }
      }
    }
    return false;
  }

  private JsonResponse<Map<String, String>> proceedToLogin(Subject currentUser, AuthenticationToken token) {
    JsonResponse<Map<String, String>> response = null;
    try {
      logoutCurrentUser();
      currentUser.getSession(true);
      currentUser.login(token);

      Set<String> roles = authenticationService.getAssociatedRoles();
      String principal = authenticationService.getPrincipal();
      TicketContainer.Entry ticketEntry = "anonymous".equals(principal) ?
              TicketContainer.ANONYMOUS_ENTRY : TicketContainer.instance.getTicketEntry(principal, roles);

      Map<String, String> data = new HashMap<>();
      data.put("principal", ticketEntry.getPrincipal());
      data.put("roles", GSON.toJson(ticketEntry.getRoles()));
      data.put("ticket", ticketEntry.getTicket());

      response = new JsonResponse<>(Status.OK, "", data);
      // if no exception, that's it, we're done!

      // set roles for user in NotebookAuthorization module
      authorizationService.setRoles(principal, roles);
    } catch (AuthenticationException uae) {
      // username wasn't in the system, show them an error message?
      // password didn't match, try again?
      // account for that username is locked - can't login.  Show them a message?
      // unexpected condition - error?
      LOG.error("Exception in login: ", uae);
    }
    return response;
  }

  /**
   * Post Login
   * Returns userName & password
   * for anonymous access, username is always anonymous.
   * After getting this ticket, access through websockets become safe
   *
   * @return 200 response
   */
  @POST
  @ZeppelinApi
  public Response postLogin(@FormParam("userName") String userName,
      @FormParam("password") String password) {
    LOG.debug("userName: {}", userName);
    // ticket set to anonymous for anonymous user. Simplify testing.
    Subject currentUser = SecurityUtils.getSubject();
    if (currentUser.isAuthenticated()) {
      currentUser.logout();
    }
    LOG.debug("currentUser: {}", currentUser);
    JsonResponse<Map<String, String>> response = null;
    if (!currentUser.isAuthenticated()) {

      UsernamePasswordToken token = new UsernamePasswordToken(userName, password);

      response = proceedToLogin(currentUser, token);
    }

    if (response == null) {
      response = new JsonResponse<>(Response.Status.FORBIDDEN, "", null);
    }

    LOG.info(response.toString());
    return response.build();
  }

  @POST
  @Path("logout")
  @ZeppelinApi
  public Response logout() {
    logoutCurrentUser();
    Status status;
    Map<String, String> data = new HashMap<>();
    if (zConf.isAuthorizationHeaderClear()) {
      status = Status.UNAUTHORIZED;
      data.put("clearAuthorizationHeader", "true");
    } else {
      status = Status.FORBIDDEN;
      data.put("clearAuthorizationHeader", "false");
    }
    if (isKnoxSSOEnabled()) {
      KnoxJwtRealm knoxJwtRealm = getJTWRealm();
      data.put("redirectURL",
          constructUrl(knoxJwtRealm.getProviderUrl(), knoxJwtRealm.getRedirectParam(),
              knoxJwtRealm.getLogout()));
      data.put("isLogoutAPI", knoxJwtRealm.getLogoutAPI().toString());
    } else if (isKerberosRealmEnabled()) {
      KerberosRealm kerberosRealm = getKerberosRealm();
      data.put("redirectURL",
          constructUrl(kerberosRealm.getProviderUrl(), kerberosRealm.getRedirectParam(),
              kerberosRealm.getLogout()));
      data.put("isLogoutAPI", kerberosRealm.getLogoutAPI().toString());
    }
    JsonResponse<Map<String, String>> response = new JsonResponse<>(status, "", data);
    LOG.info(response.toString());
    return response.build();
  }

  private String constructUrl(String providerURL, String redirectParam,
      String path) {
    StringBuilder redirectURL = new StringBuilder(providerURL);
    redirectURL.append(path);
    if (redirectParam != null) {
      redirectURL.append("?").append(redirectParam).append("=");
    }
    return redirectURL.toString();
  }

  private void logoutCurrentUser() {
    Subject currentUser = SecurityUtils.getSubject();
    TicketContainer.instance.removeTicket(authenticationService.getPrincipal());
    currentUser.getSession().stop();
    currentUser.logout();
  }
}
