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

import com.teragrep.zep_01.display.AngularObject;
import com.teragrep.zep_01.display.AngularObjectRegistry;
import com.teragrep.zep_01.display.AngularObjectRegistryListener;
import com.teragrep.zep_01.interpreter.AbstractInterpreterTest;
import com.teragrep.zep_01.interpreter.InterpreterContext;
import com.teragrep.zep_01.interpreter.InterpreterException;
import com.teragrep.zep_01.interpreter.InterpreterResult;
import com.teragrep.zep_01.interpreter.InterpreterSetting;
import com.teragrep.zep_01.notebook.Note;
import com.teragrep.zep_01.notebook.NoteInfo;
import com.teragrep.zep_01.resource.LocalResourcePool;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

public class RemoteAngularObjectTest extends AbstractInterpreterTest
    implements AngularObjectRegistryListener {

  private RemoteInterpreter intp;
  private InterpreterContext context;
  private RemoteAngularObjectRegistry localRegistry;
  private InterpreterSetting interpreterSetting;

  private AtomicInteger onAdd;
  private AtomicInteger onUpdate;
  private AtomicInteger onRemove;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    Note note1 = new Note(new NoteInfo("note1", "/note_1"));
    when(mockNotebook.getNote("note1")).thenReturn(note1);

    onAdd = new AtomicInteger(0);
    onUpdate = new AtomicInteger(0);
    onRemove = new AtomicInteger(0);

    interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("test");
    intp = (RemoteInterpreter) interpreterSetting.getInterpreter("user1", "note1", "mock_ao");
    localRegistry = (RemoteAngularObjectRegistry) intp.getInterpreterGroup().getAngularObjectRegistry();

    context = InterpreterContext.builder()
        .setNoteId("note")
        .setParagraphId("id")
        .setAngularObjectRegistry(new AngularObjectRegistry(intp.getInterpreterGroup().getId(), null))
        .setResourcePool(new LocalResourcePool("pool1"))
        .build();

    intp.open();

  }

  @Ignore(value="Contains sleep, timeout, while loops or something similar waiting/cycleburning")
  @Test
  public void testAngularObjectInterpreterSideCRUD() throws InterruptedException, InterpreterException {
    InterpreterResult ret = intp.interpret("get", context);
    Thread.sleep(500); // waitFor eventpoller pool event
    String[] result = ret.message().get(0).getData().split(" ");
    assertEquals("0", result[0]); // size of registry
    assertEquals("0", result[1]); // num watcher called

    // create object
    ret = intp.interpret("add n1 v1", context);
    Thread.sleep(500);
    result = ret.message().get(0).getData().split(" ");
    assertEquals("1", result[0]); // size of registry
    assertEquals("0", result[1]); // num watcher called
    assertEquals("v1", localRegistry.get("n1", "note", null).get());

    // update object
    ret = intp.interpret("update n1 v11", context);
    result = ret.message().get(0).getData().split(" ");
    Thread.sleep(500);
    assertEquals("1", result[0]); // size of registry
    assertEquals("1", result[1]); // num watcher called
    assertEquals("v11", localRegistry.get("n1", "note", null).get());

    // remove object
    ret = intp.interpret("remove n1", context);
    result = ret.message().get(0).getData().split(" ");
    Thread.sleep(500);
    assertEquals("0", result[0]); // size of registry
    assertEquals("1", result[1]); // num watcher called
    assertNull(localRegistry.get("n1", "note", null));
  }

  @Ignore(value="Contains sleep, timeout, while loops or something similar waiting/cycleburning")
  @Test
  public void testAngularObjectRemovalOnZeppelinServerSide() throws InterruptedException, InterpreterException {
    // test if angularobject removal from server side propagate to interpreter process's registry.
    // will happen when notebook is removed.

    InterpreterResult ret = intp.interpret("get", context);
    Thread.sleep(500); // waitFor eventpoller pool event
    String[] result = ret.message().get(0).getData().split(" ");
    assertEquals("0", result[0]); // size of registry

    // create object
    ret = intp.interpret("add n1 v1", context);
    Thread.sleep(500);
    result = ret.message().get(0).getData().split(" ");
    assertEquals("1", result[0]); // size of registry
    assertEquals("v1", localRegistry.get("n1", "note", null).get());

    // remove object in local registry.
    localRegistry.removeAndNotifyRemoteProcess("n1", "note", null);
    ret = intp.interpret("get", context);
    Thread.sleep(500); // waitFor eventpoller pool event
    result = ret.message().get(0).getData().split(" ");
    assertEquals("0", result[0]); // size of registry
  }

  @Ignore(value="Contains sleep, timeout, while loops or something similar waiting/cycleburning")
  @Test
  public void testAngularObjectAddOnZeppelinServerSide() throws InterruptedException, InterpreterException {
    // test if angularobject add from server side propagate to interpreter process's registry.
    // will happen when zeppelin server loads notebook and restore the object into registry

    InterpreterResult ret = intp.interpret("get", context);
    Thread.sleep(500); // waitFor eventpoller pool event
    String[] result = ret.message().get(0).getData().split(" ");
    assertEquals("0", result[0]); // size of registry

    // create object
    localRegistry.addAndNotifyRemoteProcess("n1", "v1", "note", null);

    // get from remote registry
    ret = intp.interpret("get", context);
    Thread.sleep(500); // waitFor eventpoller pool event
    result = ret.message().get(0).getData().split(" ");
    assertEquals("1", result[0]); // size of registry
  }

  @Override
  public void onAddAngularObject(String interpreterGroupId, AngularObject angularObject) {
    onAdd.incrementAndGet();
  }

  @Override
  public void onUpdateAngularObject(String interpreterGroupId, AngularObject angularObject) {
    onUpdate.incrementAndGet();
  }

  @Override
  public void onRemoveAngularObject(String interpreterGroupId, AngularObject angularObject) {
    onRemove.incrementAndGet();
  }

}
