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
package com.teragrep.zep_01.rest.message;

import com.google.gson.Gson;

import com.teragrep.zep_01.common.JsonSerializable;

/**
 * RestartInterpreter rest api request message.
 */
public class RestartInterpreterRequest implements JsonSerializable {
  private static final Gson GSON = new Gson();

  private String noteId;

  public RestartInterpreterRequest() {
  }

  public String getNoteId() {
    return noteId;
  }

  public String toJson() {
    return GSON.toJson(this);
  }

  public static RestartInterpreterRequest fromJson(String json) {
    return GSON.fromJson(json, RestartInterpreterRequest.class);
  }
}
