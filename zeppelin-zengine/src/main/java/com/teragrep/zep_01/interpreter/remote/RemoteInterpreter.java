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

package com.teragrep.zep_01.interpreter.remote;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.thrift.TException;
import com.teragrep.zep_01.display.AngularObject;
import com.teragrep.zep_01.display.AngularObjectRegistry;
import com.teragrep.zep_01.display.GUI;
import com.teragrep.zep_01.display.Input;
import com.teragrep.zep_01.interpreter.ConfInterpreter;
import com.teragrep.zep_01.interpreter.Interpreter;
import com.teragrep.zep_01.interpreter.InterpreterContext;
import com.teragrep.zep_01.interpreter.InterpreterException;
import com.teragrep.zep_01.interpreter.InterpreterResult;
import com.teragrep.zep_01.interpreter.LifecycleManager;
import com.teragrep.zep_01.interpreter.ManagedInterpreterGroup;
import com.teragrep.zep_01.interpreter.thrift.InterpreterCompletion;
import com.teragrep.zep_01.interpreter.thrift.RemoteInterpreterContext;
import com.teragrep.zep_01.interpreter.thrift.RemoteInterpreterResult;
import com.teragrep.zep_01.interpreter.thrift.RemoteInterpreterResultMessage;
import com.teragrep.zep_01.interpreter.thrift.RemoteInterpreterService.Client;
import com.teragrep.zep_01.scheduler.Job;
import com.teragrep.zep_01.scheduler.RemoteScheduler;
import com.teragrep.zep_01.scheduler.Scheduler;
import com.teragrep.zep_01.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Proxy for Interpreter instance that runs on separate process
 */
public class RemoteInterpreter extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreter.class);
  private static final Gson GSON = new Gson();


  private String className;
  private String sessionId;
  private FormType formType;

  private RemoteInterpreterProcess interpreterProcess;
  private volatile boolean isOpened = false;
  private volatile boolean isCreated = false;

  /**
   * Remote interpreter and manage interpreter process
   */
  public RemoteInterpreter(Properties properties,
                           String sessionId,
                           String className,
                           String userName) {
    super(properties);
    this.sessionId = sessionId;
    this.className = className;
    this.setUserName(userName);
  }

  public boolean isOpened() {
    return isOpened;
  }

  @VisibleForTesting
  public void setOpened(boolean opened) {
    isOpened = opened;
  }

  @Override
  public String getClassName() {
    return className;
  }

  public String getSessionId() {
    return this.sessionId;
  }

  public synchronized RemoteInterpreterProcess getOrCreateInterpreterProcess() throws IOException {
    if (this.interpreterProcess != null) {
      return this.interpreterProcess;
    }
    ManagedInterpreterGroup intpGroup = getInterpreterGroup();
    this.interpreterProcess = intpGroup.getOrCreateInterpreterProcess(getUserName(), properties);
    return interpreterProcess;
  }

  @Override
  public ManagedInterpreterGroup getInterpreterGroup() {
    return (ManagedInterpreterGroup) super.getInterpreterGroup();
  }

  @Override
  public void open() throws InterpreterException {
    synchronized (this) {
      if (!isOpened) {
        // create all the interpreters of the same session first, then Open the internal interpreter
        // of this RemoteInterpreter.
        // The why we we create all the interpreter of the session is because some interpreter
        // depends on other interpreter. e.g. PySparkInterpreter depends on SparkInterpreter.
        // also see method Interpreter.getInterpreterInTheSameSessionByClassName
        for (Interpreter interpreter : getInterpreterGroup()
                                        .getOrCreateSession(this.getUserName(), sessionId)) {
          try {
            if (!(interpreter instanceof ConfInterpreter)) {
              ((RemoteInterpreter) interpreter).internal_create();
            }
          } catch (IOException e) {
            throw new InterpreterException(e);
          }
        }

        interpreterProcess.callRemoteFunction(client -> {
          LOGGER.info("Open RemoteInterpreter {}", getClassName());
          // open interpreter here instead of in the jobRun method in RemoteInterpreterServer
          // client.open(sessionId, className);
          // Push angular object loaded from JSON file to remote interpreter
          synchronized (getInterpreterGroup()) {
            if (!getInterpreterGroup().isAngularRegistryPushed()) {
              pushAngularObjectRegistryToRemote(client);
              getInterpreterGroup().setAngularRegistryPushed(true);
            }
          }
          return null;
        });
        isOpened = true;
      }
    }
  }

  private void internal_create() throws IOException {
    synchronized (this) {
      if (!isCreated) {
        this.interpreterProcess = getOrCreateInterpreterProcess();
        if (!interpreterProcess.isRunning()) {
          throw new IOException("Interpreter process is not running\n" +
                  interpreterProcess.getErrorMessage());
        }
        interpreterProcess.callRemoteFunction(client -> {
          LOGGER.info("Create RemoteInterpreter {}", getClassName());
          client.createInterpreter(getInterpreterGroup().getId(), sessionId,
              className, (Map) properties, getUserName());
          return null;
        });
        isCreated = true;
      }
    }
  }


  @Override
  public void close() throws InterpreterException {
    if (isOpened) {
      RemoteInterpreterProcess interpreterProcess = null;
      try {
        interpreterProcess = getOrCreateInterpreterProcess();
      } catch (IOException e) {
        throw new InterpreterException(e);
      }
      interpreterProcess.callRemoteFunction(client -> {
        client.close(sessionId, className);
        return null;
      });
      isOpened = false;
    } else {
      LOGGER.warn("close is called when RemoterInterpreter is not opened for {}", className);
    }
  }

  @Override
  public InterpreterResult interpret(final String st, final InterpreterContext context)
      throws InterpreterException {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("st:\n{}", st);
    }

    final FormType form = getFormType();
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    if (!interpreterProcess.isRunning()) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
              "Interpreter process is not running\n" + interpreterProcess.getErrorMessage());
    }
    return interpreterProcess.callRemoteFunction(client -> {
          RemoteInterpreterResult remoteResult = client.interpret(
              sessionId, className, st, convert(context));
          Map<String, Object> remoteConfig = (Map<String, Object>) GSON.fromJson(
              remoteResult.getConfig(), new TypeToken<Map<String, Object>>() {
              }.getType());
          context.getConfig().clear();
          if (remoteConfig != null) {
            context.getConfig().putAll(remoteConfig);
          }
          GUI currentGUI = context.getGui();
          GUI currentNoteGUI = context.getNoteGui();
          if (form == FormType.NATIVE) {
            GUI remoteGui = GUI.fromJson(remoteResult.getGui());
            GUI remoteNoteGui = GUI.fromJson(remoteResult.getNoteGui());
            currentGUI.clear();
            currentGUI.setParams(remoteGui.getParams());
            currentGUI.setForms(remoteGui.getForms());
            currentNoteGUI.setParams(remoteNoteGui.getParams());
            currentNoteGUI.setForms(remoteNoteGui.getForms());
          } else if (form == FormType.SIMPLE) {
            final Map<String, Input> currentForms = currentGUI.getForms();
            final Map<String, Object> currentParams = currentGUI.getParams();
            final GUI remoteGUI = GUI.fromJson(remoteResult.getGui());
            final Map<String, Input> remoteForms = remoteGUI.getForms();
            final Map<String, Object> remoteParams = remoteGUI.getParams();
            currentForms.putAll(remoteForms);
            currentParams.putAll(remoteParams);
          }

          return convert(remoteResult);
        }
    );

  }

  @Override
  public void cancel(final InterpreterContext context) throws InterpreterException {
    if (!isOpened) {
      LOGGER.warn("Cancel is called when RemoterInterpreter is not opened for {}", className);
      return;
    }
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    interpreterProcess.callRemoteFunction(client -> {
      client.cancel(sessionId, className, convert(context));
      return null;
    });
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    if (formType != null) {
      return formType;
    }

    // it is possible to call getFormType before it is opened
    synchronized (this) {
      if (!isOpened) {
        open();
      }
    }
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }

    return interpreterProcess.callRemoteFunction(client -> {
          formType = FormType.valueOf(client.getFormType(sessionId, className));
          return formType;
    });
  }


  @Override
  public int getProgress(final InterpreterContext context) throws InterpreterException {
    if (!isOpened) {
      LOGGER.warn("getProgress is called when RemoterInterpreter is not opened for {}", className);
      return 0;
    }
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    return interpreterProcess.callRemoteFunction(client ->
            client.getProgress(sessionId, className, convert(context)));
  }


  @Override
  public List<InterpreterCompletion> completion(final String buf, final int cursor,
                                                final InterpreterContext interpreterContext)
      throws InterpreterException {
    if (!isOpened) {
      open();
    }
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    return interpreterProcess.callRemoteFunction(client ->
            client.completion(sessionId, className, buf, cursor, convert(interpreterContext)));
  }

  public String getStatus(final String jobId) {
    if (!isOpened) {
      LOGGER.warn("getStatus is called when RemoteInterpreter is not opened for {}", className);
      return Job.Status.UNKNOWN.name();
    }
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return interpreterProcess.callRemoteFunction(client -> {
      return client.getStatus(sessionId, jobId);
    });
  }


  @Override
  public Scheduler getScheduler() {
    // one session own one Scheduler, so that when one session is closed, all the jobs/paragraphs
    // running under the scheduler of this session will be aborted.
    String executionMode = getProperty(".execution.mode", "paragraph");
    if (executionMode.equals("paragraph")) {
      Scheduler s = new RemoteScheduler(
              RemoteInterpreter.class.getSimpleName() + "-" + getInterpreterGroup().getId() + "-" + sessionId,
              SchedulerFactory.singleton().getExecutor(),
              this);
      return SchedulerFactory.singleton().createOrGetScheduler(s);
    } else if (executionMode.equals("note")) {
      String noteId = getProperty(".noteId");
      Scheduler s = new RemoteScheduler(
              RemoteInterpreter.class.getSimpleName() + "-" + noteId,
              SchedulerFactory.singleton().getExecutor(),
              this);
      return SchedulerFactory.singleton().createOrGetScheduler(s);
    } else {
      throw new RuntimeException("Invalid execution mode: " + executionMode);
    }

  }

  private RemoteInterpreterContext convert(InterpreterContext ic) {
    return new RemoteInterpreterContext(ic.getNoteId(), ic.getNoteName(), ic.getParagraphId(),
        ic.getReplName(), ic.getParagraphTitle(), ic.getParagraphText(),
        GSON.toJson(ic.getAuthenticationInfo()), GSON.toJson(ic.getConfig()), ic.getGui().toJson(),
        GSON.toJson(ic.getNoteGui()),
        ic.getLocalProperties());
  }

  private InterpreterResult convert(RemoteInterpreterResult result) {
    InterpreterResult r = new InterpreterResult(
        InterpreterResult.Code.valueOf(result.getCode()));

    for (RemoteInterpreterResultMessage m : result.getMsg()) {
      r.add(InterpreterResult.Type.valueOf(m.getType()), m.getData());
    }

    return r;
  }

  /**
   * Push local angular object registry to
   * remote interpreter. This method should be
   * call ONLY once when the first Interpreter is created
   */
  private void pushAngularObjectRegistryToRemote(Client client) throws TException {
    final AngularObjectRegistry angularObjectRegistry = this.getInterpreterGroup()
        .getAngularObjectRegistry();
    if (angularObjectRegistry != null && angularObjectRegistry.getRegistry() != null) {
      final Map<String, Map<String, AngularObject>> registry = angularObjectRegistry
          .getRegistry();
      LOGGER.info("Push local angular object registry from ZeppelinServer to" +
          " remote interpreter group {}", this.getInterpreterGroup().getId());
      final java.lang.reflect.Type registryType = new TypeToken<Map<String,
          Map<String, AngularObject>>>() {
      }.getType();
      client.angularRegistryPush(GSON.toJson(registry, registryType));
    }
  }

  @Override
  public String toString() {
    return "RemoteInterpreter_" + className + "_" + sessionId;
  }
}
