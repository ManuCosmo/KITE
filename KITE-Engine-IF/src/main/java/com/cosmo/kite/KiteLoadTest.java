/*
 *
 *  * Copyright 2019 Cosmo Software Consulting Pte. Ltd.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     https://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.cosmo.kite;

import com.cosmo.kite.instrumentation.NWInstConfig;
import com.cosmo.kite.util.RoomManager;
import com.cosmo.kite.util.TestStats;
import com.cosmo.kite.util.TestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.webrtc.kite.KiteTest;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** The type Kite load test. */
public abstract class KiteLoadTest extends KiteTest {

  // unique ID for this test, used mainly for segregating reports and screenshots.
  private final String uid = new SimpleDateFormat("yyyyMMdd_hhmmss").format(new Date());
  private static final Logger logger = Logger.getLogger(KiteLoadTest.class.getName());


  public static final String CALL = "ru"; //ramp up
  public static final String END = "lr"; //load reached


  private String resultPath = "results/" + uid + "/";

  private String screenshotPath = resultPath + "screenshots/";


  protected String url = "https://www.youtube.com/watch?v=b7Z_E4SRGro&feature=youtu.be";

  protected String pageTitle = "Kite";
  private boolean takeScreenshotForEachTest = false; // false by default

  public static int driverId = 0;

  protected int increment = 1;
  protected int interval = 5; //5 seconds
  protected int maxUsersPerRoom = 1;
  protected int count = 1;

  private boolean createJsonFile = true;
  private boolean createCSVReport = true;

  protected int roomId;
  private String hubIpOrDns;
  private String configFile = null;

  private boolean getStats = false;
  private int statsCollectionTime = 10;
  private int statsCollectionInterval = 1;
  private int latencyCollectionTime = 14440;
  private int latencyCollectionInterval = 6;
  private int stayInTime = 0;
  private int rejoinInterval = 0;
  private int rejoinRandomInterval = 0;
  private int testTimeout = 60;
  private boolean fastRampUp = false;

  protected int expectedTestDuration = 60; //in minutes

  private NWInstConfig nwInstConfig = null;

  protected static RoomManager roomManager = null;

  /** The Successful tests. */
  protected int successfulTests = 0;

  protected String testName = "KiteLoadTest";
  private static int accumulatedTestCount = 0;


  private Map<WebDriver, Map<String, String>> sessionData;
  private Vector<CallableTester> testerList = new Vector<>();


  /**
   * Gets successful tests.
   *
   * @return the successful tests
   */
  public int getSuccessfulTests() {
    return successfulTests;
  }

  /**
   * Gets the name of the test.
   *
   * @return testName
   */
  public String getTestName() {
    return testName;
  }


  /**
   * @return true to call and collect getStats, false otherwise, as set in the config file.
   */
  public boolean getStats() { return getStats; }


  /**
   * @return true for fastRampUp
   */
  public boolean fastRampUp() { return fastRampUp; }

  public void setGetStats(boolean getStats) { this.getStats = getStats; }


  /**
   * @return the testTimeout as set in the config file.
   */
  public int getTestTimeout() { return this.testTimeout; }

  /**
   * @return statsCollectionTime Time in seconds to collect stats (Default 10)
   */
  public int getStatsCollectionTime() { return statsCollectionTime; }

  /**
   * @return statsCollectionInterval Time interval between each getStats call (Default 1)
   */
  public int getStatsCollectionInterval() { return statsCollectionInterval; }

  /**
   * @return latencyCollectionTime Time in seconds to collect latency (Default 14400s)
   */
  public int getLatencyCollectionTime() { return latencyCollectionTime; }

  /**
   * @return latencyCollectionInterval Time interval between each OCR latency checks call (Default 6s)
   */
  public int getLatencyCollectionInterval() { return latencyCollectionInterval; }

  /**
   * @return stayInTime (in seconds): how long to stay in the room during rampup.
   */
  public int getStayInTime() { return stayInTime; }

  /**
   * This is the sum of rejoinInterval and Math.random() * rejoinRandomInterval.
   *
   * @return rejoinInterval (in seconds): how often to reload the page and re-join the same room.
   */
  public long getRejoinInterval() {
    return Math.round(rejoinInterval + (Math.random() * rejoinRandomInterval));
  }

  /**
   * @return true or false as set in config file
   */
  public boolean takeScreenshotForEachTest() { return takeScreenshotForEachTest; }


  /**
   * Gets the path where the results files (screenshots, browser logs, getStats json files) should be saved
   *
   * @return resultPath
   */
  public String getResultPath() {
    return resultPath;
  }

  /**
   * Gets session data.
   *
   * @return the session data
   */
  public Map<WebDriver, Map<String, String>> getSessionData() {
    return sessionData;
  }

  /**
   * Sets session data.
   *
   * @param sessionData the session data
   */
  public void setSessionData(Map<WebDriver, Map<String, String>> sessionData) {
    this.sessionData = sessionData;
  }

  /**
   * Sets the hub IP or DNS
   *
   * @param hubIpOrDns the hub IP or DNS
   */
  public void setHubIpOrDns(String hubIpOrDns) {
    this.hubIpOrDns = hubIpOrDns;
  }

  /**
   * sets the testName from the config file.
   * @param testName
   */
  public void setTestName(String testName) {
    this.testName = testName;
    this.resultPath = "results/" + uid + "_" +  testName + "/";
    this.screenshotPath = resultPath + "screenshots/";
  }

  /**
   * @return the pageTitle as set in the config file.
   */
  public String getPageTitle() { return this.pageTitle = testName; }

  /**
   * This method can be overridden to return the test statistics at anytime especially if it fails
   * due to some exception.
   * By default it will process each callableTester.getUserData() method and build the csv report file
   *
   * @return Some custom stats object with a toString() implementation. By default, it returns a Vector<JsonObject>
   *     containing the results of the CallableTester.getUserData();
   */
  public Object getUserData() {
    logger.info("\r\n          ==========================================================\r\n"
      + "                              KiteLoadTest.getUserData()"
      + "\r\n          ==========================================================\r\n");
    Vector<JsonObject> results = new Vector<>();
    for (int i = 0; i < testerList.size(); i += this.increment) {
      try {
        Vector<CallableTester> callableTesters = new Vector<>();
        for (int j = i; j < i + this.increment; j++) {
          if (j < testerList.size()) {
            callableTesters.add(testerList.elementAt(j));
          } else {
            break;
          }
        }
        ExecutorService executorService = Executors.newFixedThreadPool(this.increment);
        List<Future<JsonObjectBuilder>> futureList =
          executorService.invokeAll(callableTesters, expectedTestDuration, TimeUnit.MINUTES);

        executorService.shutdown();

        int successCount = 0;
        for (Future<JsonObjectBuilder> future : futureList) {
          try {
            JsonObjectBuilder jsonObjBuilder = future.get();
            JsonObject jsonObject = jsonObjBuilder.build();
            results.add(jsonObject);
            if (createCSVReport) {
              TestStats.getInstance(END).println(jsonObject, resultPath);
            }
            String url = jsonObject.getString("url", "null");
            url = url.contains("/") ? url.substring(url.lastIndexOf("/")) : url;
            String res = jsonObject.getString("result", "null");
            successCount += res.contains(CallableTester.RESULT_PASS) ? 1 : 0;
            logger.info("Load Reached Test for "  +url + " = " + res);
          } catch (Exception e) {
            logger.error(
              "Exception in KiteLoadTest: "
                + e.getLocalizedMessage()
                + "\r\n"
                + TestUtils.getStackTrace(e));
          }
        }
      } catch (Exception e) {
        logger.error(TestUtils.getStackTrace(e));
      }
    }
    String resultStr = TestUtils.createJsonArray("resultObj", results);

    //logger.info("Test resultObj = \r\n" + resultStr);
    if (createJsonFile) {
      TestStats.getInstance(END).printJsonTofile(END + "_" + testName, resultStr, resultPath + "reports/");
    }
    //TestStats.getInstance(END).close();
    return results;
  }

  /**
   * Restructuring the test according to options given in payload object from config file. This
   * function processes the parameters common to all load tests.
   */
  protected void payloadHandling() {
    JsonObject jsonPayload = (JsonObject) this.getPayload();
    if (jsonPayload != null) {
      testTimeout = jsonPayload.getInt("testTimeout", testTimeout);
      if (jsonPayload.containsKey("loadTestTimeout")) {
        testTimeout = jsonPayload.getInt("loadTestTimeout");
      }
      url = jsonPayload.getString("url", null);
      pageTitle = jsonPayload.getString("pageTitle", pageTitle);
      takeScreenshotForEachTest =
        jsonPayload.getBoolean("takeScreenshotForEachTest", takeScreenshotForEachTest);
      getStats = jsonPayload.getBoolean("getStats", false);
      statsCollectionTime = jsonPayload.getInt("statsCollectionTime", statsCollectionTime);
      statsCollectionInterval = jsonPayload.getInt("statsCollectionInterval", statsCollectionInterval);
      latencyCollectionTime = jsonPayload.getInt("latencyCollectionTime", latencyCollectionTime);
      latencyCollectionInterval = jsonPayload.getInt("latencyCollectionInterval", latencyCollectionInterval);
      stayInTime = jsonPayload.getInt("stayInTime", stayInTime);
      if (stayInTime > 2 * 60 * testTimeout) {
        //increase test timeout (in min) to be > stayingTime (in s)
        testTimeout = (stayInTime * 2) / 60;
      }
      rejoinInterval = jsonPayload.getInt("rejoinInterval", rejoinInterval);
      rejoinRandomInterval = jsonPayload.getInt("rejoinRandomInterval", rejoinRandomInterval);
      createJsonFile = jsonPayload.getBoolean("csvReport", createJsonFile);
      fastRampUp = jsonPayload.getBoolean("fastRampUp", fastRampUp);
      try {
        JsonObject obj = jsonPayload.getJsonObject("instrumentation");
        if (obj != null) {
          nwInstConfig = new NWInstConfig(obj);
        }
      } catch (Exception e) {
        logger.error("Invalid network instrumentation config.\r\n" + TestUtils.getStackTrace(e));
      }
    }
  }

  /** Set increment according to option given in browsers object from config file. */
  public void setIncrement(int increment) {
    this.increment = increment;
  }

  /** Set interval according to option given in browsers object from config file. */
  public void setInterval(int interval) {
    this.interval = interval;
  }

  /**
   * @return the increment value
   */
  public int getIncrement() {
    return this.increment;
  }


  /**
   * @return the interval value
   */
  public int getInterval() {
    return this.interval;
  }

  /** path to the config file used for this test. */
  public void setConfigFile(String configFile) throws IOException {
    if (configFile == null) {
      this.configFile = configFile;
      FileUtils.copyFile(new File(this.configFile), new File(resultPath + this.configFile));
    } else {
      this.configFile = configFile;
    }
  }

  /** Set count according to option given in browsers object from config file. */
  public void setCount(int count) {
    this.count = count;
  }

  /** Set the random roomId */
  public void setRoomId() {
    this.roomId = (int) (Math.random() * 10000 + 1);
  }

  /**
   * @return the roomId
   */
  public int getRoomId() {
    return this.roomId;
  }

  /** Running the test in multi-threaded manner */
  protected int takeAction() throws Exception {
    this.payloadHandling();
    Vector<JsonObject> jsonList = new Vector<JsonObject>();
    List<WebDriver> webDriverList = this.getWebDriverList();
    if (roomManager != null && this.hubIpOrDns != null && !roomManager.roomListProvided()) {
      this.url = RoomManager.getInstance().getRoomUrl(this.hubIpOrDns);
    }
    logger.info("Testing " + this.getWebDriverList().size() + " " + url + ". testTimeout = " + testTimeout + " minutes.");
    List<CallableTester> callableTesters = getTesterList(webDriverList);
    testerList.addAll(callableTesters);
    ExecutorService executorService = Executors.newFixedThreadPool(webDriverList.size());
    List<Future<JsonObjectBuilder>> futureList =
      executorService.invokeAll(callableTesters, expectedTestDuration, TimeUnit.MINUTES);
    executorService.shutdown();

    int successCount = 0;
    for (Future<JsonObjectBuilder> future : futureList) {
      try {
        JsonObjectBuilder jsonObjBuilder = future.get();
        JsonObject jsonObject = jsonObjBuilder.build();
        jsonList.add(jsonObject);
        if (createCSVReport) {
          TestStats.getInstance(CALL).println(jsonObject, resultPath);
        }
        String url = jsonObject.getString("url", "null");
        url = url.contains("/") ? url.substring(url.lastIndexOf("/")) : url;
        String res = jsonObject.getString("result", "null");
        successCount += res.contains(CallableTester.RESULT_PASS) ? 1 : 0;
        logger.info("Ramp Up Test for "  +url + " = " + res);
      } catch (Exception e) {
        logger.error(
          "Exception in KiteLoadTest: "
            + e.getLocalizedMessage()
            + "\r\n"
            + TestUtils.getStackTrace(e));
      }
    }
    this.successfulTests = successCount;
    String resultStr = TestUtils.createJsonArray("resultObj", jsonList);
    //logger.info("Test resultObj = \r\n" + resultStr);
    if (createJsonFile) {
      TestStats.getInstance(CALL).printJsonTofile(CALL + "_" + testName, resultStr, resultPath + "reports/");
    }
    synchronized (this) {
      KiteLoadTest.accumulatedTestCount += this.successfulTests;
      logger.info(
        "Given WebDrivers: "
          + this.getWebDriverList().size()
          + ", Successful tests: "
          + this.successfulTests
          + ", Accumulated test count: "
          + KiteLoadTest.accumulatedTestCount);
    }
    return successCount;
  }

  /**
   * Sets HTML page title
   *
   * @param pageTitle
   */
  public void setPageTitle(String pageTitle) {
    this.pageTitle = pageTitle;
  }

  @Override
  public Object testScript() throws Exception {
    this.successfulTests = this.takeAction();
    return "";
  }

  /**
   * @param webDriverList
   * @return list of CallableTester
   */
  protected abstract List<CallableTester> getTesterList(List<WebDriver> webDriverList);

  /**
   * Gets the unique ID
   * @return the unique ID
   */
  public String getUid() {
    return this.uid;
  }

  /**
   *
   * @return the path to the screenshot folder
   */
  public String getScreenshotPath() {
    return this.screenshotPath;
  }

  /**
   *
   * @return the network instrumentation config
   */
  public NWInstConfig getNWInstConfig() {
    return this.nwInstConfig;
  }
}
