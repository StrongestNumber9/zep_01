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

package com.teragrep.zep_01.markdown;

import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.teragrep.zep_01.interpreter.Interpreter;
import com.teragrep.zep_01.interpreter.InterpreterContext;
import com.teragrep.zep_01.interpreter.InterpreterResult;
import com.teragrep.zep_01.interpreter.InterpreterResult.Code;
import com.teragrep.zep_01.interpreter.InterpreterUtils;
import com.teragrep.zep_01.interpreter.thrift.InterpreterCompletion;
import com.teragrep.zep_01.scheduler.Scheduler;
import com.teragrep.zep_01.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MarkdownInterpreter interpreter for Zeppelin.
 */
public class Markdown extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(Markdown.class);

  private MarkdownParser parser;

  private final String[] unsafeTags = new String[]{"script", "object", "iframe", "embed"};

  public Markdown(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    parser = new FlexmarkParser();
  }

  @Override
  public void close() {
  }

  @Override
  public InterpreterResult interpret(String markdownText, InterpreterContext interpreterContext) {
    String html;

    try {
      markdownText = sanitizeInput(markdownText);
      html = parser.render(markdownText);
    } catch (RuntimeException e) {
      LOGGER.error("Exception in MarkdownInterpreter while interpret ", e);
      return new InterpreterResult(Code.ERROR, InterpreterUtils.getMostRelevantMessage(e));
    }

    return new InterpreterResult(Code.SUCCESS, "%html " + html);
  }

  public String sanitizeInput(String input) {
    if (input != null) {
      for (String unsafeTag : unsafeTags) {
        String unsafeRegex = "<" + unsafeTag + ">(.*)</" + unsafeTag + ">";
        Pattern pattern = Pattern.compile(unsafeRegex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
          input = matcher.replaceAll("");
        }
      }

      String onclickRegex = "onclick=[\"'](.*)[\"']";
      Pattern pattern = Pattern.compile(onclickRegex);
      Matcher matcher = pattern.matcher(input);
      if (matcher.find()) {
        input = matcher.replaceAll("");
      }
    }
    return input;
  }

  @Override
  public void cancel(InterpreterContext context) {
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton()
        .createOrGetParallelScheduler(Markdown.class.getName() + this.hashCode(), 5);
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    return null;
  }
}
