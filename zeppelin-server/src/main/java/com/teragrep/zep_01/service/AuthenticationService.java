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

package com.teragrep.zep_01.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.shiro.realm.Realm;

/**
 * Interface for Zeppelin Security.
 * //TODO(zjffdu) rename it to AuthenticationService
 */
public interface AuthenticationService {

  /**
   * Get current principal/username.
   * @return
   */
  String getPrincipal();

  /**
   * Get roles associated with current principal
   * @return
   */
  Set<String> getAssociatedRoles();

  Collection<Realm> getRealmsList();

  boolean isAuthenticated();

  /**
   * Used for user auto-completion
   * @param searchText
   * @param numUsersToFetch
   * @return
   */
  List<String> getMatchedUsers(String searchText, int numUsersToFetch);

  /**
   * Used for role auto-completion
   * @return
   */
  List<String> getMatchedRoles();
}
