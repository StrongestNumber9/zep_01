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
package com.teragrep.zep_01.notebook.repo.zeppelinhub.websocket.protocol;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import com.teragrep.zep_01.common.JsonSerializable;
import com.teragrep.zep_01.common.Message;
import com.teragrep.zep_01.common.Message.OP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Zeppelinhub message class.
 *
 */
public class ZeppelinhubMessage implements JsonSerializable {
  private static final Gson gson = new Gson();
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinhubMessage.class);
  public static final ZeppelinhubMessage EMPTY = new ZeppelinhubMessage();

  public Object op;
  public Object data;
  public Map<String, String> meta = new HashMap<>();

  private ZeppelinhubMessage() {
    this.op = OP.LIST_NOTES;
    this.data = null;
  }

  private ZeppelinhubMessage(Object op, Object data, Map<String, String> meta) {
    this.op = op;
    this.data = data;
    this.meta = meta;
  }

  public static ZeppelinhubMessage newMessage(Object op, Object data, Map<String, String> meta) {
    return new ZeppelinhubMessage(op, data, meta);
  }

  public static ZeppelinhubMessage newMessage(Message zeppelinMsg, Map<String, String> meta) {
    if (zeppelinMsg == null) {
      return EMPTY;
    }
    return new ZeppelinhubMessage(zeppelinMsg.op, zeppelinMsg.data, meta);
  }

  @Override
  public String toJson() {
    return gson.toJson(this, ZeppelinhubMessage.class);
  }

  public static ZeppelinhubMessage fromJson(String zeppelinhubMessage) {
    if (StringUtils.isBlank(zeppelinhubMessage)) {
      return EMPTY;
    }
    ZeppelinhubMessage msg;
    try {
      msg = gson.fromJson(zeppelinhubMessage, ZeppelinhubMessage.class);
    } catch (JsonSyntaxException ex) {
      LOGGER.error("Cannot fromJson zeppelinhub message", ex);
      msg = EMPTY;
    }
    return msg;
  }

}
