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

package com.teragrep.zep_01.integration;

import org.apache.commons.io.FileUtils;
import com.teragrep.zep_01.conf.ZeppelinConfiguration;
import com.teragrep.zep_01.display.AngularObjectRegistryListener;
import com.teragrep.zep_01.interpreter.InterpreterFactory;
import com.teragrep.zep_01.interpreter.InterpreterSettingManager;
import com.teragrep.zep_01.interpreter.remote.RemoteInterpreterProcessListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.mockito.Mockito.mock;

public class MiniZeppelin {

  protected static final Logger LOGGER = LoggerFactory.getLogger(MiniZeppelin.class);

  protected InterpreterSettingManager interpreterSettingManager;
  protected InterpreterFactory interpreterFactory;
  protected File zeppelinHome;
  private File confDir;
  private File notebookDir;
  protected ZeppelinConfiguration conf;

  public void start(Class clazz) throws IOException {
    zeppelinHome = new File("target");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(),
        zeppelinHome.getAbsolutePath());
    confDir = new File(zeppelinHome, "conf_" + clazz.getSimpleName());
    notebookDir = new File(zeppelinHome, "notebook_" + clazz.getSimpleName());
    confDir.mkdirs();
    notebookDir.mkdirs();
    LOGGER.debug("ZEPPELIN_HOME: " + zeppelinHome.getAbsolutePath());
    FileUtils.copyFile(new File(zeppelinHome, "conf/log4j.properties"), new File(confDir, "log4j.properties"));
    FileUtils.copyFile(new File(zeppelinHome, "conf/log4j2.properties"), new File(confDir, "log4j2.properties"));
    FileUtils.copyFile(new File(zeppelinHome, "conf/log4j_yarn_cluster.properties"), new File(confDir, "log4j_yarn_cluster.properties"));
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName(), confDir.getAbsolutePath());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "120000");
    conf = ZeppelinConfiguration.create();
    interpreterSettingManager = new InterpreterSettingManager(conf,
        mock(AngularObjectRegistryListener.class), mock(RemoteInterpreterProcessListener.class));
    interpreterFactory = new InterpreterFactory(interpreterSettingManager);
  }

  public void stop() throws IOException {
    interpreterSettingManager.close();
  }

  public File getZeppelinHome() {
    return zeppelinHome;
  }

  public File getZeppelinConfDir() {
    return confDir;
  }

  public InterpreterFactory getInterpreterFactory() {
    return interpreterFactory;
  }

  public InterpreterSettingManager getInterpreterSettingManager() {
    return interpreterSettingManager;
  }
}
