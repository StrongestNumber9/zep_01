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

package com.teragrep.zep_01;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class WebDriverManager {

  public final static Logger LOG = LoggerFactory.getLogger(WebDriverManager.class);

  private static String downLoadsDir = "";

  private static String GECKODRIVER_VERSION = "0.27.0";

  public static WebDriver getWebDriver() {
    WebDriver driver = null;

    try {
      int firefoxVersion = 1; // WebDriverManager.getFirefoxVersion();
      LOG.debug("Firefox version " + firefoxVersion + " detected");

      downLoadsDir = new File("target/").getAbsolutePath();

      String tempPath = downLoadsDir + "/firefox/";

      // downloadGeekoDriver(firefoxVersion, tempPath);

      FirefoxProfile profile = new FirefoxProfile();
      profile.setPreference("browser.download.folderList", 2);
      profile.setPreference("browser.download.dir", downLoadsDir);
      profile.setPreference("browser.helperApps.alwaysAsk.force", false);
      profile.setPreference("browser.download.manager.showWhenStarting", false);
      profile.setPreference("browser.download.manager.showAlertOnComplete", false);
      profile.setPreference("browser.download.manager.closeWhenDone", true);
      profile.setPreference("app.update.auto", false);
      profile.setPreference("app.update.enabled", false);
      profile.setPreference("dom.max_script_run_time", 0);
      profile.setPreference("dom.max_chrome_script_run_time", 0);
      profile.setPreference("browser.helperApps.neverAsk.saveToDisk",
          "application/x-ustar,application/octet-stream,application/zip,text/csv,text/plain");
      profile.setPreference("network.proxy.type", 0);

      FirefoxOptions firefoxOptions = new FirefoxOptions();
      firefoxOptions.setProfile(profile);

      ImmutableMap<String, String> displayImmutable = ImmutableMap.<String, String>builder().build();
      if ("true".equals(System.getenv("TRAVIS"))) {
        // Run with DISPLAY 99 for TRAVIS or other build machine
        displayImmutable = ImmutableMap.of("DISPLAY", ":99");
      }

      System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "/dev/null");
      System.setProperty(FirefoxDriver.SystemProperty.DRIVER_USE_MARIONETTE,"true");

      driver = new FirefoxDriver(
             new GeckoDriverService.Builder()
               .usingDriverExecutable(new File(tempPath + "geckodriver"))
               .withEnvironment(displayImmutable)
               .build(), firefoxOptions);

    } catch (Exception e) {
      throw new RuntimeException("Exception in WebDriverManager while FireFox Driver ", e);
    }

    if (driver == null) {
      try {
        driver = new ChromeDriver();
      } catch (Exception e) {
        throw new RuntimeException("Exception in WebDriverManager while ChromeDriver ", e);
      }
    }

    if (driver == null) {
      try {
        driver = new SafariDriver();
      } catch (Exception e) {
        throw new RuntimeException("Exception in WebDriverManager while SafariDriver ", e);
      }
    }

    String url;
    if (System.getenv("url") != null) {
      url = System.getenv("url");
    } else {
      url = "http://localhost:8080";
    }

    long start = System.currentTimeMillis();
    boolean loaded = false;
    driver.manage().timeouts().implicitlyWait(AbstractZeppelinIT.MAX_IMPLICIT_WAIT,
        TimeUnit.SECONDS);
    driver.get(url);

    while (System.currentTimeMillis() - start < 60 * 1000) {
      // wait for page load
      try {
        (new WebDriverWait(driver, 30)).until(new ExpectedCondition<Boolean>() {
          @Override
          public Boolean apply(WebDriver d) {
            return d.findElement(By.xpath("//i[@uib-tooltip='WebSocket Connected']"))
                .isDisplayed();
          }
        });
        loaded = true;
        break;
      } catch (TimeoutException e) {
        throw new RuntimeException("Failure: ", e);
      }
    }

    if (loaded == false) {
      throw new RuntimeException("Failed to load driver");
    }

    driver.manage().window().maximize();
    return driver;
  }

  /*
  public static void downloadGeekoDriver(int firefoxVersion, String tempPath) {
    String geekoDriverUrlString =
        "https://github.com/mozilla/geckodriver/releases/download/v" + GECKODRIVER_VERSION
            + "/geckodriver-v" + GECKODRIVER_VERSION + "-";

    LOG.debug("Geeko version: " + firefoxVersion + ", will be downloaded to " + tempPath);
    try {
      if (SystemUtils.IS_OS_WINDOWS) {
        if (System.getProperty("sun.arch.data.model").equals("64")) {
          geekoDriverUrlString += "win64.zip";
        } else {
          geekoDriverUrlString += "win32.zip";
        }
      } else if (SystemUtils.IS_OS_LINUX) {
        if (System.getProperty("sun.arch.data.model").equals("64")) {
          geekoDriverUrlString += "linux64.tar.gz";
        } else {
          geekoDriverUrlString += "linux32.tar.gz";
        }
      } else if (SystemUtils.IS_OS_MAC_OSX) {
        geekoDriverUrlString += "macos.tar.gz";
      }

      File geekoDriver = new File(tempPath + "geckodriver");
      File geekoDriverZip = new File(tempPath + "geckodriver.tar");
      File geekoDriverDir = new File(tempPath);
      URL geekoDriverUrl = new URL(geekoDriverUrlString);
      if (!geekoDriver.exists()) {
        FileUtils.copyURLToFile(geekoDriverUrl, geekoDriverZip);
        if (SystemUtils.IS_OS_WINDOWS) {
          Archiver archiver = ArchiverFactory.createArchiver("zip");
          archiver.extract(geekoDriverZip, geekoDriverDir);
        } else {
          Archiver archiver = ArchiverFactory.createArchiver("tar", "gz");
          archiver.extract(geekoDriverZip, geekoDriverDir);
        }
      }

    } catch (IOException e) {
      throw new RuntimeException("Download of Geeko version: " + firefoxVersion + ", falied in path " + tempPath);
    }
    LOG.debug("Download of Geeko version: " + firefoxVersion + ", successful");
  }
   */

  /*
  public static int getFirefoxVersion() {
    try {
      String firefoxVersionCmd = "firefox -v";
      if (System.getProperty("os.name").startsWith("Mac OS")) {
        firefoxVersionCmd = "/Applications/Firefox.app/Contents/MacOS/" + firefoxVersionCmd;
      }
      String versionString = (String) CommandExecutor
          .executeCommandLocalHost(firefoxVersionCmd, false, ProcessData.Types_Of_Data.OUTPUT);
      return Integer
          .valueOf(versionString.replaceAll("Mozilla Firefox", "").trim().substring(0, 2));
    } catch (Exception e) {
      LOG.error("Exception in WebDriverManager while getWebDriver ", e);
      return -1;
    }
  }
  */
}
