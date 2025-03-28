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
package com.teragrep.zep_01.resource;

/**
 * Connect resource pools running in remote process
 */
public interface ResourcePoolConnector {
  /**
   * Get list of resources from all other resource pools in remote processes
   * @return
   */
  ResourceSet getAllResources();

  /**
   * Read remote object
   * @return
   */
  Object readResource(ResourceId id);

  /**
   * Invoke method of Resource and get return
   * @return
   */
  Object invokeMethod(
      ResourceId id,
      String methodName,
      Class[] paramTypes,
      Object[] params);

  /**
   * Invoke method, put result into resource pool and return
   */
  Resource invokeMethod(
      ResourceId id,
      String methodName,
      Class[] paramTypes,
      Object[] params,
      String returnResourceName);
}
