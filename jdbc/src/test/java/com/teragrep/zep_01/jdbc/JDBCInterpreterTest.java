/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.teragrep.zep_01.jdbc;


import com.teragrep.zep_01.completer.CompletionType;
import com.teragrep.zep_01.interpreter.InterpreterContext;
import com.teragrep.zep_01.interpreter.InterpreterException;

import org.apache.commons.lang3.StringUtils;
import com.teragrep.zep_01.interpreter.InterpreterOutput;
import com.teragrep.zep_01.interpreter.InterpreterResult;
import com.teragrep.zep_01.interpreter.InterpreterResultMessage;
import com.teragrep.zep_01.interpreter.thrift.InterpreterCompletion;
import com.teragrep.zep_01.scheduler.FIFOScheduler;
import com.teragrep.zep_01.scheduler.ParallelScheduler;
import com.teragrep.zep_01.scheduler.Scheduler;
import com.teragrep.zep_01.user.AuthenticationInfo;
import com.teragrep.zep_01.user.UserCredentials;
import com.teragrep.zep_01.user.UsernamePassword;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import com.mockrunner.jdbc.BasicJDBCTestCaseAdapter;
import net.jodah.concurrentunit.Waiter;

import static java.lang.String.format;
import static com.teragrep.zep_01.jdbc.JDBCInterpreter.COMMON_MAX_LINE;
import static com.teragrep.zep_01.jdbc.JDBCInterpreter.DEFAULT_DRIVER;
import static com.teragrep.zep_01.jdbc.JDBCInterpreter.DEFAULT_PASSWORD;
import static com.teragrep.zep_01.jdbc.JDBCInterpreter.DEFAULT_PRECODE;
import static com.teragrep.zep_01.jdbc.JDBCInterpreter.DEFAULT_STATEMENT_PRECODE;
import static com.teragrep.zep_01.jdbc.JDBCInterpreter.DEFAULT_URL;
import static com.teragrep.zep_01.jdbc.JDBCInterpreter.DEFAULT_USER;
import static com.teragrep.zep_01.jdbc.JDBCInterpreter.PRECODE_KEY_TEMPLATE;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * JDBC interpreter unit tests.
 */
public class JDBCInterpreterTest extends BasicJDBCTestCaseAdapter {
  static String jdbcConnection;
  InterpreterContext context;

  private static String getJdbcConnection() throws IOException {
    if (null == jdbcConnection) {
      Path tmpDir = new File("target/h2-test").toPath().toAbsolutePath();
      jdbcConnection = format("jdbc:h2:%s", tmpDir);
    }
    return jdbcConnection;
  }

  public static Properties getJDBCTestProperties() {
    Properties p = new Properties();
    p.setProperty("default.driver", "org.postgresql.Driver");
    p.setProperty("default.url", "jdbc:postgresql://localhost:5432/");
    p.setProperty("default.user", "gpadmin");
    p.setProperty("default.password", "");
    p.setProperty("common.max_count", "1000");

    return p;
  }

  @Override
  @BeforeEach
  public void setUp() throws Exception {
    Class.forName("org.h2.Driver");
    Connection connection = DriverManager.getConnection(getJdbcConnection());
    Statement statement = connection.createStatement();
    statement.execute(
        "DROP TABLE IF EXISTS test_table; " +
        "CREATE TABLE test_table(id varchar(255), name varchar(255));");
    statement.execute(
        "CREATE USER IF NOT EXISTS dbuser PASSWORD 'dbpassword';" +
        "CREATE USER IF NOT EXISTS user1Id PASSWORD 'user1Pw';" +
        "CREATE USER IF NOT EXISTS user2Id PASSWORD 'user2Pw';"
    );

    PreparedStatement insertStatement = connection.prepareStatement(
            "insert into test_table(id, name) values ('a', 'a_name'),('b', 'b_name'),('c', ?);");
    insertStatement.setString(1, null);
    insertStatement.execute();
    context = InterpreterContext.builder()
        .setAuthenticationInfo(new AuthenticationInfo("testUser"))
        .setParagraphId("paragraphId")
        .setInterpreterOut(new InterpreterOutput())
        .build();
  }

  @Test
  void testForParsePropertyKey() {
    JDBCInterpreter t = new JDBCInterpreter(new Properties());
    Map<String, String> localProperties = new HashMap<>();
    InterpreterContext interpreterContext = InterpreterContext.builder()
        .setLocalProperties(localProperties)
        .build();
    assertEquals(JDBCInterpreter.DEFAULT_KEY, t.getDBPrefix(interpreterContext));

    localProperties = new HashMap<>();
    localProperties.put("db", "mysql");
    interpreterContext = InterpreterContext.builder()
        .setLocalProperties(localProperties)
        .build();
    assertEquals("mysql", t.getDBPrefix(interpreterContext));

    localProperties = new HashMap<>();
    localProperties.put("hive", "hive");
    interpreterContext = InterpreterContext.builder()
        .setLocalProperties(localProperties)
        .build();
    assertEquals("hive", t.getDBPrefix(interpreterContext));
  }

  /**
   * DBprefix like %jdbc(db=mysql) or %jdbc(mysql) is not supported anymore
   * JDBC Interpreter would try to use default config.
   */
  @Test
  void testDBPrefixProhibited() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table";
    Map<String, String> localProperties = new HashMap<>();
    localProperties.put("db", "fake");
    InterpreterContext context = InterpreterContext.builder()
        .setAuthenticationInfo(new AuthenticationInfo("testUser"))
        .setLocalProperties(localProperties)
        .setParagraphId("paragraphId")
        .setInterpreterOut(new InterpreterOutput())
        .build();
    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);

    // The result should be the same as that run with default config
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\nc\tnull\n",
            resultMessages.get(0).getData());
  }

  @Test
  void testDefaultProperties() {
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(getJDBCTestProperties());

    assertEquals("org.postgresql.Driver", jdbcInterpreter.getProperty(DEFAULT_DRIVER));
    assertEquals("jdbc:postgresql://localhost:5432/", jdbcInterpreter.getProperty(DEFAULT_URL));
    assertEquals("gpadmin", jdbcInterpreter.getProperty(DEFAULT_USER));
    assertEquals("", jdbcInterpreter.getProperty(DEFAULT_PASSWORD));
    assertEquals("1000", jdbcInterpreter.getProperty(COMMON_MAX_LINE));
  }

  @Test
  void testSelectQuery() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table WHERE ID in ('a', 'b'); ";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\n", resultMessages.get(0).getData());

    context = getInterpreterContext();
    context.getLocalProperties().put("limit", "1");
    interpreterResult = t.interpret(sqlQuery, context);

    resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("ID\tNAME\na\ta_name\n", resultMessages.get(0).getData());
  }

  @Disabled(value="Contains bunch of sleeps and awaits")
  @Test
  void testSelectWithRefresh() throws IOException, InterruptedException, TimeoutException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    final Waiter waiter = new Waiter();
    context.getLocalProperties().put("refreshInterval", "1000");
    Thread thread = new Thread(() -> {
      String sqlQuery = "select * from test_table WHERE ID in ('a', 'b');";
      try {
        InterpreterResult interpreterResult = t.interpret(sqlQuery, context);
        assertEquals(InterpreterResult.Code.ERROR, interpreterResult.code());
      } catch (InterpreterException e) {
        fail("Should not be here");
      }
      waiter.resume();
    });

    thread.start();

    Thread.sleep(5000);
    t.cancel(context);
    waiter.await(5000);
  }

  @Test
  void testInvalidSelectWithRefresh() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    context.getLocalProperties().put("refreshInterval", "1000");
    String sqlQuery = "select * from invalid_table;";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);
    assertEquals(InterpreterResult.Code.ERROR, interpreterResult.code());
    assertTrue(interpreterResult.message()
            .get(0).getData().contains("Table \"INVALID_TABLE\" not found;"),
            interpreterResult.toString());
  }

  @Test
  void testColumnAliasQuery() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select NAME as SOME_OTHER_NAME from test_table limit 1";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code(),
            interpreterResult.toString());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("SOME_OTHER_NAME\na_name\n", resultMessages.get(0).getData());
  }

  @Test
  void testSplitSqlQuery() {
    String sqlQuery = "insert into test_table(id, name) values ('a', ';\"');" +
        "select * from test_table;" +
        "select * from test_table WHERE ID = \";'\";" +
        "select * from test_table WHERE ID = ';';" +
        "select '\n', ';';" +
        "select replace('A\\;B', '\\', 'text');" +
        "select '\\', ';';" +
        "select '''', ';';" +
        "select /*+ scan */ * from test_table;" +
        "--singleLineComment\nselect * from test_table;";


    Properties properties = new Properties();
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();
    List<String> multipleSqlArray = t.splitSqlQueries(sqlQuery);
    assertEquals(10, multipleSqlArray.size());
    assertEquals("insert into test_table(id, name) values ('a', ';\"')", multipleSqlArray.get(0));
    assertEquals("select * from test_table", multipleSqlArray.get(1));
    assertEquals("select * from test_table WHERE ID = \";'\"", multipleSqlArray.get(2));
    assertEquals("select * from test_table WHERE ID = ';'", multipleSqlArray.get(3));
    assertEquals("select '\n', ';'", multipleSqlArray.get(4));
    assertEquals("\nselect replace('A\\;B', '\\', 'text')", multipleSqlArray.get(5));
    assertEquals("\nselect '\\', ';'", multipleSqlArray.get(6));
    assertEquals("\nselect '''', ';'", multipleSqlArray.get(7));
    assertEquals("\nselect /*+ scan */ * from test_table", multipleSqlArray.get(8));
    assertEquals("\n\nselect * from test_table", multipleSqlArray.get(9));
  }

  @Test
  void testQueryWithEscapedCharacters() throws IOException,
          InterpreterException {
    String sqlQuery = "select '\\n', ';';" +
        "select replace('A\\;B', '\\', 'text');" +
        "select '\\', ';';" +
        "select '''', ';'";

    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty("default.splitQueries", "true");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(1).getType());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(2).getType());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(3).getType());
    assertEquals("'\\n'\t';'\n\\n\t;\n", resultMessages.get(0).getData());
    assertEquals("'Atext;B'\nAtext;B\n", resultMessages.get(1).getData());
    assertEquals("'\\'\t';'\n\\\t;\n", resultMessages.get(2).getData());
    assertEquals("''''\t';'\n'\t;\n", resultMessages.get(3).getData());
  }

  @Test
  void testSelectMultipleQueries() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty("default.splitQueries", "true");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table;" +
        "select * from test_table WHERE ID = ';';";
    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());

    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(2, resultMessages.size());

    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\nc\tnull\n",
            resultMessages.get(0).getData());

    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(1).getType());
    assertEquals("ID\tNAME\n", resultMessages.get(1).getData());
  }

  @Test
  void testDefaultSplitQuries() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table;" +
        "select * from test_table WHERE ID = ';';";
    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());

    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(2, resultMessages.size());

    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("ID\tNAME\na\ta_name\nb\tb_name\nc\tnull\n",
            resultMessages.get(0).getData());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(1).getType());
    assertEquals("ID\tNAME\n",
            resultMessages.get(1).getData());
  }

  @Test
  void testSelectQueryWithNull() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table WHERE ID = 'c'";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);

    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("ID\tNAME\nc\tnull\n", resultMessages.get(0).getData());
  }


  @Test
  void testSelectQueryMaxResult() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "select * from test_table";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());

    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("ID\tNAME\na\ta_name\n", resultMessages.get(0).getData());
    assertEquals(InterpreterResult.Type.HTML, resultMessages.get(1).getType());
    assertTrue(resultMessages.get(1).getData().contains("Output is truncated"));
  }

  @Test
  void concurrentSettingTest() {
    Properties properties = new Properties();
    properties.setProperty("zeppelin.jdbc.concurrent.use", "true");
    properties.setProperty("zeppelin.jdbc.concurrent.max_connection", "10");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);

    assertTrue(jdbcInterpreter.isConcurrentExecution());
    assertEquals(10, jdbcInterpreter.getMaxConcurrentConnection());

    Scheduler scheduler = jdbcInterpreter.getScheduler();
    assertInstanceOf(ParallelScheduler.class, scheduler);

    properties.clear();
    properties.setProperty("zeppelin.jdbc.concurrent.use", "false");
    jdbcInterpreter = new JDBCInterpreter(properties);

    assertFalse(jdbcInterpreter.isConcurrentExecution());

    scheduler = jdbcInterpreter.getScheduler();
    assertInstanceOf(FIFOScheduler.class, scheduler);
  }

  @Test
  void testAutoCompletion() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();

    jdbcInterpreter.interpret("", context);

    List<InterpreterCompletion> completionList = jdbcInterpreter.completion("sel", 3,
            context);

    InterpreterCompletion correctCompletionKeyword = new InterpreterCompletion("select", "select",
            CompletionType.keyword.name());

    assertEquals(1, completionList.size());
    assertTrue(completionList.contains(correctCompletionKeyword));
  }

  private Properties getDBProperty(String dbPrefix,
                                   String dbUser,
                                   String dbPassowrd) throws IOException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    if (!StringUtils.isBlank(dbPrefix)) {
      properties.setProperty(dbPrefix + ".driver", "org.h2.Driver");
      properties.setProperty(dbPrefix + ".url", getJdbcConnection());
      properties.setProperty(dbPrefix + ".user", dbUser);
      properties.setProperty(dbPrefix + ".password", dbPassowrd);
    } else {
      properties.setProperty("default.driver", "org.h2.Driver");
      properties.setProperty("default.url", getJdbcConnection());
      properties.setProperty("default.user", dbUser);
      properties.setProperty("default.password", dbPassowrd);
    }
    return properties;
  }

  private AuthenticationInfo getUserAuth(String user, String entityName, String dbUser,
      String dbPassword) {
    UserCredentials userCredentials = new UserCredentials();
    if (entityName != null && dbUser != null && dbPassword != null) {
      UsernamePassword up = new UsernamePassword(dbUser, dbPassword);
      userCredentials.putUsernamePassword(entityName, up);
    }
    AuthenticationInfo authInfo = new AuthenticationInfo();
    authInfo.setUserCredentials(userCredentials);
    authInfo.setUser(user);
    return authInfo;
  }

  @Test
  void testMultiTenant_1() throws IOException, InterpreterException {
    // user1 %jdbc  select from default db
    // user2 %jdbc  select from default db

    Properties properties = getDBProperty("default", "dbuser", "dbpassword");
    properties.putAll(getDBProperty("hive", "", ""));

    JDBCInterpreter jdbc = new JDBCInterpreter(properties);
    AuthenticationInfo user1Credential = getUserAuth("user1", null, null, null);
    AuthenticationInfo user2Credential = getUserAuth("user2", "hive", "user2Id", "user2Pw");
    jdbc.open();

    // user1 runs default
    InterpreterContext context = InterpreterContext.builder()
            .setAuthenticationInfo(user1Credential)
            .setInterpreterOut(new InterpreterOutput())
            .setReplName("jdbc")
            .build();
    jdbc.interpret("", context);

    JDBCUserConfigurations user1JDBC1Conf = jdbc.getJDBCConfiguration("user1");
    assertEquals("dbuser", user1JDBC1Conf.getProperty().get("user"));
    assertEquals("dbpassword", user1JDBC1Conf.getProperty().get("password"));

    // user2 run default
    context = InterpreterContext.builder()
        .setAuthenticationInfo(user2Credential)
        .setInterpreterOut(new InterpreterOutput())
        .setReplName("jdbc")
        .build();
    jdbc.interpret("", context);

    JDBCUserConfigurations user2JDBC1Conf = jdbc.getJDBCConfiguration("user2");
    assertEquals("dbuser", user2JDBC1Conf.getProperty().get("user"));
    assertEquals("dbpassword", user2JDBC1Conf.getProperty().get("password"));


    jdbc.close();
  }

  @Test
  void testMultiTenant_2() throws IOException, InterpreterException {
    // user1 %hive  select from default db
    // user2 %hive  select from default db
    Properties properties = getDBProperty("default", "", "");
    JDBCInterpreter jdbc = new JDBCInterpreter(properties);
    AuthenticationInfo user1Credential = getUserAuth("user1", "hive", "user1Id", "user1Pw");
    AuthenticationInfo user2Credential = getUserAuth("user2", "hive", "user2Id", "user2Pw");
    jdbc.open();

    // user1 runs default
    InterpreterContext context = InterpreterContext.builder()
            .setAuthenticationInfo(user1Credential)
            .setInterpreterOut(new InterpreterOutput())
            .setReplName("hive")
            .build();
    jdbc.interpret("", context);

    JDBCUserConfigurations user1JDBC1Conf = jdbc.getJDBCConfiguration("user1");
    assertEquals("user1Id", user1JDBC1Conf.getProperty().get("user"));
    assertEquals("user1Pw", user1JDBC1Conf.getProperty().get("password"));

    // user2 run default
    context = InterpreterContext.builder()
            .setAuthenticationInfo(user2Credential)
            .setInterpreterOut(new InterpreterOutput())
            .setReplName("hive")
            .build();
    jdbc.interpret("", context);

    JDBCUserConfigurations user2JDBC1Conf = jdbc.getJDBCConfiguration("user2");
    assertEquals("user2Id", user2JDBC1Conf.getProperty().get("user"));
    assertEquals("user2Pw", user2JDBC1Conf.getProperty().get("password"));

    jdbc.close();
  }

  @Test
  void testPrecode() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty(DEFAULT_PRECODE,
            "create table test_precode (id int); insert into test_precode values (1);");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();
    jdbcInterpreter.executePrecode(context);

    String sqlQuery = "select * from test_precode";

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, context);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(3, resultMessages.size());
    assertEquals(InterpreterResult.Type.TEXT, resultMessages.get(0).getType());
    assertEquals("Query executed successfully. Affected rows : 0\n\n",
            resultMessages.get(0).getData());
    assertEquals(InterpreterResult.Type.TEXT, resultMessages.get(1).getType());
    assertEquals("Query executed successfully. Affected rows : 1\n",
            resultMessages.get(1).getData());
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(2).getType());
    assertEquals("ID\n1\n", resultMessages.get(2).getData());
  }

  @Test
  void testIncorrectPrecode() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty(DEFAULT_PRECODE, "select 1");
    properties.setProperty("incorrect.driver", "org.h2.Driver");
    properties.setProperty("incorrect.url", getJdbcConnection());
    properties.setProperty("incorrect.user", "");
    properties.setProperty("incorrect.password", "");
    properties.setProperty(String.format(PRECODE_KEY_TEMPLATE, "incorrect"), "incorrect command");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();
    InterpreterResult interpreterResult = jdbcInterpreter.executePrecode(context);

    assertEquals(InterpreterResult.Code.ERROR, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TEXT, interpreterResult.message().get(0).getType());
  }


  @Test
  void testStatementPrecode() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty(DEFAULT_STATEMENT_PRECODE, "set @v='statement'");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();

    String sqlQuery = "select @v";

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, context);

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("@V\nstatement\n", resultMessages.get(0).getData());
  }

  @Test
  void testIncorrectStatementPrecode() throws IOException,
          InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty(DEFAULT_STATEMENT_PRECODE, "set incorrect");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();

    String sqlQuery = "select 1";

    InterpreterResult interpreterResult = jdbcInterpreter.interpret(sqlQuery, context);

    assertEquals(InterpreterResult.Code.ERROR, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TEXT, interpreterResult.message().get(0).getType());
    assertTrue(interpreterResult.message().get(0).getData().contains("Syntax error"),
            interpreterResult.toString());
  }

  @Test
  void testSplitSqlQueryWithComments() throws IOException,
          InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("common.max_count", "1000");
    properties.setProperty("common.max_retry", "3");
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection());
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    properties.setProperty("default.splitQueries", "true");
    JDBCInterpreter t = new JDBCInterpreter(properties);
    t.open();

    String sqlQuery = "/* ; */\n" +
        "-- /* comment\n" +
        "--select * from test_table\n" +
        "select * from test_table; /* some comment ; */\n" +
        "/*\n" +
        "select * from test_table;\n" +
        "*/\n" +
        "-- a ; b\n" +
        "select * from test_table WHERE ID = ';--';\n" +
        "select * from test_table WHERE ID = '/*'; -- test";

    InterpreterResult interpreterResult = t.interpret(sqlQuery, context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(3, resultMessages.size());
  }

  @Test
  void testValidateConnectionUrl() throws IOException, InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("default.driver", "org.h2.Driver");
    properties.setProperty("default.url", getJdbcConnection() + ";allowLoadLocalInfile=true");
    properties.setProperty("default.user", "");
    properties.setProperty("default.password", "");
    JDBCInterpreter jdbcInterpreter = new JDBCInterpreter(properties);
    jdbcInterpreter.open();
    InterpreterResult interpreterResult = jdbcInterpreter.interpret("SELECT 1", context);
    assertEquals(InterpreterResult.Code.ERROR, interpreterResult.code());
    assertEquals("Connection URL contains improper configuration",
            interpreterResult.message().get(0).getData());
  }

  private InterpreterContext getInterpreterContext() {
    return InterpreterContext.builder()
            .setAuthenticationInfo(new AuthenticationInfo("testUser"))
            .setParagraphId("paragraphId")
            .setInterpreterOut(new InterpreterOutput())
            .build();
  }
}
