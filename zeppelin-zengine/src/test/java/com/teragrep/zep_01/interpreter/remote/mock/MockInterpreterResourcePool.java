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

package com.teragrep.zep_01.interpreter.remote.mock;

import com.google.gson.Gson;
import com.teragrep.zep_01.interpreter.Interpreter;
import com.teragrep.zep_01.interpreter.InterpreterContext;
import com.teragrep.zep_01.interpreter.InterpreterResult;
import com.teragrep.zep_01.interpreter.InterpreterResult.Code;
import com.teragrep.zep_01.interpreter.thrift.InterpreterCompletion;
import com.teragrep.zep_01.resource.Resource;
import com.teragrep.zep_01.resource.ResourcePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.fail;

public class MockInterpreterResourcePool extends Interpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(MockInterpreterResourcePool.class);

  AtomicInteger numWatch = new AtomicInteger(0);

  public MockInterpreterResourcePool(Properties property) {
    super(property);
  }

  @Override
  public void open() {
  }

  @Override
  public void close() {

  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    String[] stmt = st.split(" ");
    String cmd = stmt[0];
    String noteId = null;
    String paragraphId = null;
    String name = null;
    if (stmt.length >= 2) {
      String[] npn = stmt[1].split(":");
      if (npn.length >= 3) {
        noteId = npn[0];
        paragraphId = npn[1];
        name = npn[2];
      } else {
        name = stmt[1];
      }
    }
    String value = null;
    if (stmt.length >= 3) {
      value = stmt[2];
    }

    ResourcePool resourcePool = context.getResourcePool();
    Object ret = null;
    if (cmd.equals("put")) {
      resourcePool.put(noteId, paragraphId, name, value);
    } else if (cmd.equalsIgnoreCase("get")) {
      Resource resource = resourcePool.get(noteId, paragraphId, name);
      if (resource != null) {
        ret = resourcePool.get(noteId, paragraphId, name).get();
      } else {
        ret = "";
      }
    } else if (cmd.equals("remove")) {
      ret = resourcePool.remove(noteId, paragraphId, name);
    } else if (cmd.equals("getAll")) {
      ret = resourcePool.getAll();
    } else if (cmd.equals("invoke")) {
      Resource resource = resourcePool.get(noteId, paragraphId, name);
      LOGGER.debug("Resource: " + resource);
      if (stmt.length >=4) {
        Resource res = resource.invokeMethod(value, stmt[3]);
        LOGGER.debug("After invokeMethod: " + resource);
        ret = res.get();
      } else {
        ret = resource.invokeMethod(value);
        LOGGER.debug("After invokeMethod: " + ret);
      }
    }

    try {
      Thread.sleep(500); // wait for watcher executed
    } catch (InterruptedException e) {
      fail("Failure: " + e.getMessage());
    }

    Gson gson = new Gson();
    return new InterpreterResult(Code.SUCCESS, gson.toJson(ret));
  }

  @Override
  public void cancel(InterpreterContext context) {
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
                                                InterpreterContext interpreterContext) {
    return null;
  }
}
