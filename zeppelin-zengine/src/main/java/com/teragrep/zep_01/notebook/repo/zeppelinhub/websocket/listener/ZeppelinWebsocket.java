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
package com.teragrep.zep_01.notebook.repo.zeppelinhub.websocket.listener;

import com.teragrep.zep_01.notebook.repo.zeppelinhub.websocket.ZeppelinClient;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelin websocket listener class.
 *
 */
public class ZeppelinWebsocket implements WebSocketListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinWebsocket.class);
  public Session connection;
  public String noteId;

  public ZeppelinWebsocket(String noteId) {
    this.noteId = noteId;
  }

  @Override
  public void onWebSocketBinary(byte[] arg0, int arg1, int arg2) {

  }

  @Override
  public void onWebSocketClose(int code, String message) {
    LOGGER.info("Zeppelin connection closed with code: {}, message: {}", code, message);
    ZeppelinClient.getInstance().removeNoteConnection(noteId);
  }

  @Override
  public void onWebSocketConnect(Session session) {
    LOGGER.info("Zeppelin connection opened");
    this.connection = session;
  }

  @Override
  public void onWebSocketError(Throwable e) {
    LOGGER.warn("Zeppelin socket connection error ", e);
    ZeppelinClient.getInstance().removeNoteConnection(noteId);
  }

  @Override
  public void onWebSocketText(String data) {
    LOGGER.debug("Zeppelin client received Message: {}", data);
    // propagate to ZeppelinHub
    try {
      ZeppelinClient zeppelinClient = ZeppelinClient.getInstance();
      if (zeppelinClient != null) {
        zeppelinClient.handleMsgFromZeppelin(data, noteId);
      }
    } catch (Exception e) {
      LOGGER.error("Failed to send message to ZeppelinHub: {}", e.toString());
    }
  }

}
