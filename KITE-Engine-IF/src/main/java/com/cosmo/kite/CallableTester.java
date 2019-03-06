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

import com.cosmo.kite.getstats.BasePCStatsArray;
import com.cosmo.kite.getstats.MultiPCStatsArray;
import com.cosmo.kite.getstats.SinglePCStatsArray;
import com.cosmo.kite.instrumentation.NWInstConfig;
import com.cosmo.kite.util.GetStatsUtils;
import com.cosmo.kite.util.TestStats;
import com.cosmo.kite.util.TestUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/** */
public abstract class CallableTester implements Callable<JsonObjectBuilder> {

  public static final String RESULT_PASS = "pass";
  public static final String RESULT_FAIL = "fail";

  protected static final int INTERVAL = 500;

  private static final Logger logger = Logger.getLogger(CallableTester.class.getName());
  protected static final SimpleDateFormat fileTS = new SimpleDateFormat("yyyyMMdd_HHmmss");

  protected final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
  protected final int id;
  private final int index;
  protected final String screenShotPath;
  protected String url;

  private final Map<String, String> sessionData;
  private long testStartTS = 0;

  protected String logHeader = "[LogHeader not initialised] ";
  protected String botName = null;
  protected final WebDriver webDriver;
  protected WebDriverWait wait = null;
  protected String screenshotFilename = fileTS.format(new Date()) + "_" + botName;
  protected boolean receiveOnly = false;

  protected KiteLoadTest loadTest;
  protected final NWInstConfig nwInstConfig;

  private String vmName = null;
  private boolean rampUp = true;
  private boolean cleanUpneeded = false;

  private boolean skipNWInstrumentation = false;

  // On Janus and OpenVidu, all streams are in differents peerconnection objects
  private List<String> multiPcTests = Arrays.asList("Janus", "openvidu", "mediasoup", "parsys");


  /**
   * Constructor of the CallableTester
   *
   * @param webDriver the webDriver used to execute the tests.
   * @param sessionData Map containing the node VM info for a given webDriver.
   * @param url the url of the web page to be tested.
   * @param loadTest KiteLoadTest object
   */
  protected CallableTester(
    WebDriver webDriver, Map<String, String> sessionData, String url, KiteLoadTest loadTest) {
    this.webDriver = webDriver;
    this.sessionData = sessionData;
    this.id = loadTest.driverId++;
    this.index = this.id % loadTest.getIncrement();
    this.url = url;
    if (this.url.contains("user")) {
      this.url += TestUtils.idToString(id);
    }
    this.loadTest = loadTest;
    this.screenShotPath = loadTest.getScreenshotPath();
    this.nwInstConfig = loadTest.getNWInstConfig();
  }

  @Override
  public JsonObjectBuilder call() throws Exception {
    if (botName == null) {
      botName = botName(webDriver, this.id);
      logHeader = String.format("%1$-28s", botName);
    }
    cleanUpneeded = false;
    JsonObjectBuilder steps = Json.createObjectBuilder();
    steps.add("botname", botName);
    steps.add("vmName", this.logHeader(webDriver).trim());
    String commandName = loadTest.getCommandName();
    if (commandName != null) {
      logger.info("NW Instrumentation command name: " + commandName);
      steps.add("commandName", commandName);
    }
    steps.add("timeStamp", dateFormat.format(new Date()));
    steps.add("url", this.url);
    try {
      if (rampUp) {
        long testScriptExecutionDelay = index * loadTest.getInterval() * 1000;
        logger.info(logHeader + "- Waiting for " + testScriptExecutionDelay + "ms before starting the Ramp Up script.");
        Thread.sleep(testScriptExecutionDelay);
        if (loadTest.getStayInTime() > 0) {
          rampUpCallReconnect(steps);
        } else {
          rampUpCall(steps);
        }
      } else {
        long testScriptExecutionDelay = index * loadTest.getInterval() * 1000;
        logger.info(logHeader + "- Waiting for " + testScriptExecutionDelay + "ms before starting the Load Reached script.");
        Thread.sleep(testScriptExecutionDelay);
        loadReachedCall(steps);
      }
    } catch (Exception e) {
      throw e;
    } finally {
      rampUp = false;
    }
    rampUp = false;
    return steps;
  }

  protected void loadPage() {
    logger.info(logHeader + "- Loading " + this.url);
    webDriver.get(this.url);
    logger.info(logHeader + "- Waiting for document.readyState to be complete");
    wait.until(webDriver ->
      ((JavascriptExecutor) webDriver)
        .executeScript("return document.readyState")
        .equals("complete"));
  }

  private void rampUpCallReconnect(JsonObjectBuilder steps) throws Exception {
    long exitTime = System.currentTimeMillis() + loadTest.getStayInTime() * 1000;
    int id = 0;
    while(System.currentTimeMillis() < exitTime) {
      JsonObjectBuilder substeps = Json.createObjectBuilder();
      rampUpCall(substeps);
      steps.add("reconnect_" + (id++), substeps);

      long sleep = loadTest.getRejoinInterval();
      logger.info(logHeader + "- reconnecting to the room in " + sleep + "s");
      Thread.sleep(sleep * 1000);
      skipNWInstrumentation = true;
    }
  }

  /**
   * call() function executed during the rampup phase of the load test.
   *
   * @param steps JsonObjectBuilder object to fill with result data.
   * @throws Exception
   */
  private void rampUpCall(JsonObjectBuilder steps) throws Exception {
    String prefix = loadTest.CALL + "_";
    long pageLoadingTime = 0;
    long testCompletionTime = 0;
    testStartTS = System.currentTimeMillis();
    try {
      boolean problem = false;

      if (nwInstConfig != null) {
        if (nwInstConfig.rampup(id)) {
          problem |= !nwInstPreloadSteps();
        }
      }
      //        steps.add("profileName", "not implemented yet");
      wait = new WebDriverWait(webDriver, loadTest.getTestTimeout());
      try {
        screenshotFilename = prefix + fileTS.format(new Date()) + "_" + botName;
        if (url.toLowerCase().contains("slack")) {
          // function to override with the step tests
          problem |= !testSteps(steps);
        } else {
          loadPage();
          logger.info(logHeader + "- Page loaded.");
          pageLoadingTime = System.currentTimeMillis() - testStartTS;
          // function to override with the step tests
          problem |= !testSteps(steps);

          if (!loadTest.fastRampUp() && loadTest.getStats()) {
            try {
              stepStatsAllVideos(steps, prefix);
            } catch (Exception e) {
              logger.error(logHeader + "Exception:\r\n" + TestUtils.getStackTrace(e), e);
            }
          }
          /*
          if (nwInstConfig != null) {
            if (nwInstConfig.rampup(id)) {
              Thread.sleep(4000);
              problem |= !nwInstPostLoadSteps(steps);
              Thread.sleep(4000);
              if (loadTest.getStats()) {
                try {
                  stepStatsAllVideos(steps, "post_");
                } catch (Exception e) {
                  logger.error(logHeader + "Exception:\r\n" + TestUtils.getStackTrace(e), e);
                }
              }
            } else {
              steps.add("node IP", "");
              steps.add("NW Inst command", "");
              stepStatsAllVideos(steps, "post_");
            }
          }
          */
          stepBrowserLogs(prefix);

        }
      } catch (TimeoutException e) {
        logger.error(logHeader + "Exception waiting for element visibility: ", e);
      }
      if (!loadTest.fastRampUp() && loadTest.takeScreenshotForEachTest()) {
        TestUtils.takeScreenshot(webDriver, this.screenShotPath, screenshotFilename + "_1");
      }
      testCompletionTime = System.currentTimeMillis() - testStartTS;
      steps.add("pageLoadingTime", pageLoadingTime);
      steps.add("testCompletionTime", testCompletionTime);
      if (problem) {
        steps.add("result", RESULT_FAIL);
        TestUtils.takeScreenshot(
          webDriver, this.screenShotPath + "error/", screenshotFilename + "_err");
      } else {
        steps.add("result", RESULT_PASS);
      }
    } catch (Exception ex) {
      logger.error("Exception in CallableTester.rampUpCall()" + "\r\n" + TestUtils.getStackTrace(ex));
      JsonObjectBuilder errorJson = Json.createObjectBuilder();
      errorJson.add("Exception", ex.getLocalizedMessage());
      errorJson.add("ErrorSteps", steps);
      TestStats.getInstance(prefix)
        .printJsonTofile(prefix + "Error_" + botName, errorJson.build().toString(), loadTest.getResultPath() + "reports/");
      throw ex;
    } finally{
      if (nwInstConfig != null && cleanUpneeded) {
        nwInstConfig.cleanUp();
      }
    }
  }


  /**
   * call() function executed when the full load has been reached.
   *
   * @param steps JsonObjectBuilder object to fill with result data.
   * @throws Exception
   */
  private void loadReachedCall(JsonObjectBuilder steps) throws Exception {
    String prefix = loadTest.END + "_";
    long testCompletionTime = 0;
    testStartTS = System.currentTimeMillis();
    try {
      boolean problem = false;
      try {
        screenshotFilename = prefix + fileTS.format(new Date()) + "_" + botName;
        if (url.toLowerCase().contains("slack")) {
          // do nothing
        } else {
          // function to override with the step tests (optional)
          problem |= !endSteps(steps);

          if (nwInstConfig != null && nwInstConfig.loadreached(id)) {
            problem |= nwInstPostLoadSteps(steps);
          }

          if (loadTest.getStats()) {
            stepStatsAllVideos(steps, prefix);
          }

          stepBrowserLogs(prefix);
        }
      } catch (TimeoutException e) {
        logger.error(logHeader + "Exception waiting for element visibility: ", e);
      }
      if (loadTest.takeScreenshotForEachTest()) {
        TestUtils.takeScreenshot(webDriver, this.screenShotPath, screenshotFilename + "_1");
      }
      testCompletionTime = System.currentTimeMillis() - testStartTS;
      steps.add("testCompletionTime", testCompletionTime);
      if (problem) {
        steps.add("result", RESULT_FAIL);
        TestUtils.takeScreenshot(
          webDriver, this.screenShotPath + "error/", screenshotFilename);
      } else {
        steps.add("result", RESULT_PASS);
      }
    } catch (Exception ex) {
      logger.error("Exception in CallableTester.call()" + "\r\n" + TestUtils.getStackTrace(ex));
      JsonObjectBuilder errorJson = Json.createObjectBuilder();
      errorJson.add("Exception", ex.getLocalizedMessage());
      errorJson.add("ErrorSteps", steps);
      TestStats.getInstance(prefix)
        .printJsonTofile(prefix + "Error_" + botName, errorJson.build().toString(), loadTest.getResultPath() + "reports/");
      throw ex;
    } finally{
      if (nwInstConfig != null && cleanUpneeded) {
        nwInstConfig.cleanUp();
      }
    }
  }

  /**
   * @param vmInstanceLongName long VM name: ec2-34-230-3-140.compute-1.amazonaws.com
   * @return ec2-34-230-3-140
   */
  private String getVMName(String vmInstanceLongName) {
    String s = "error_parsing_name";
    try {
      s = vmInstanceLongName.substring(0, vmInstanceLongName.indexOf("."));
    } catch (Exception e) {
      logger.error(
        "Error parsing " + vmInstanceLongName + "\r\n" + TestUtils.getStackTrace(e), e);
    }
    return s;
  }

  /** @return vnName (e.g.: ec2-34-230-3-140) from sessionData */
  protected String vmName() {
    if (vmName == null) {
      vmName = (this.sessionData == null) ? "" : this.getVMName(this.sessionData.get("public_dns"));
    }
    return vmName;
  }

  /** @return the node public IP (e.g.: 34.230.3.140) from sessionData */
  protected String nodePublicIp() {
    return vmName().substring(4).replace('-', '.');
  }

  /** @return the node private IP from sessionData */
  protected String nodePrivateIp() {
    return this.sessionData.get("private_ip");
  }

  /**
   * @param driver the webDriver
   * @param id the unique id of the driver
   * @return a unique name of the client bot, e.g.: ec2-34-230-3-140FF001
   */
  private String botName(WebDriver driver, int id) {
    String b = ((RemoteWebDriver) webDriver).getCapabilities().getBrowserName().toLowerCase();
    b = b.contains("fox") ? "FF" : (b.contains("chrome") ? "CH" : b);
    return loadTest.getUid()
      + "_"
      + vmName()
      + b
      + TestUtils.idToString(id);
  }

  /**
   *
   * @param driver the webDriver
   * @return a header String for the log, e.g.: 443_ec2-52-87-218-66CH013 (<uid>_<vmname><browser FF or CH><tester id>)
   */
  private String logHeader(WebDriver driver) {
    return (this.sessionData == null)
      ? ""
      : String.format("%1$-19s", this.getVMName(this.sessionData.get("public_dns")));
  }

  //Todo refactor all the stepVideo functions

  /**
   * Find the video on the page and computes the
   *
   * @param webDriver WebDriver, the Selenium WebDriver object to perform the test.
   * @param steps JsonObjectBuilder containing the test results
   * @param index index of the video to check in the videos array
   * @return true if successful, false otherwise.
   */
  protected boolean stepVideo(WebDriver webDriver, JsonObjectBuilder steps, int index) {
    boolean success;
    JsonObjectBuilder video = Json.createObjectBuilder();
    long videoLoadingTime = 0;
    String result = RESULT_FAIL;
    String canvasData = "blank";
    try {
      logger.info(
        logHeader
          + "- Looking for video object, timeout after "
          + loadTest.getTestTimeout()
          + "s.");
      long currentTime = System.currentTimeMillis();
      if (webDriver.getPageSource().contains(">404</h1>")) {
        result = "404";
        throw new Exception("Error 404");
      }
      List<WebElement> videos = new ArrayList<>();
      for (int i = 0; i < loadTest.getTestTimeout() * 1000; i += INTERVAL) {
        videos = webDriver.findElements(By.tagName("video"));
        if (videos.isEmpty()) {
          Thread.sleep(INTERVAL);
        } else {
          videoLoadingTime = System.currentTimeMillis() - currentTime;
          break;
        }
      }
      if (videos.isEmpty()) {
        result = RESULT_FAIL;
        videoLoadingTime = 0;
      } else {
        logger.info(
          logHeader + "- Video found, computing canvas (timeout after "
            + loadTest.getTestTimeout()
            + "s)");
        // Checking video display
        String errMsg = "";
        for (int i = 0; i < loadTest.getTestTimeout() * 1000; i += INTERVAL) {
          try {
            JsonObject checksumResult = TestUtils.videoCheckSum(webDriver, 0);
            canvasData = checksumResult.getString("result").toLowerCase();
            if (canvasData.equalsIgnoreCase("blank")
              || canvasData.equalsIgnoreCase("still")) {
              Thread.sleep(INTERVAL);
            } else {
              result = RESULT_PASS;
              break;
            }
          } catch (InterruptedException e) {
            logger.warn(
              logHeader + "- stepVideo(): InterruptedException " + e.getLocalizedMessage());
            Thread.currentThread().interrupt();
          } catch (JavascriptException e) {
            if (errMsg.equals(e.getLocalizedMessage())) {
              // no need to try again 60 times.
              break;
            }
            logger.warn(
              logHeader
                + "- stepVideo(): Attempt "
                + (i + 1)
                + "/"
                + loadTest.getTestTimeout()
                + " to get video failed: "
                + e.getLocalizedMessage()
                + "\r\n"
                + TestUtils.getStackTrace(e));
            Thread.sleep(INTERVAL);
            errMsg = e.getLocalizedMessage();
          }
        }
      }
    } catch (Exception e) {
      logger.error(logHeader + "Exception in stepVideo " + e.getMessage());
    } finally {
      // make sure the 3 variables are always added to the video object
      video.add("videoFound", result);
      video.add("videoLoadingTime (ms)", videoLoadingTime);
      video.add("canvas", canvasData);
      steps.add("video", video);
      success = result.equalsIgnoreCase(RESULT_PASS);
    }
    return success;
  }

  /**
   * Find the video on the page and computes the
   *
   * @param webDriver WebDriver, the Selenium WebDriver object to perform the test.
   * @param steps JsonObjectBuilder containing the test results
   * @param testName the name of the test
   * @return true if successful, false otherwise.
   */
  protected boolean stepLocalVideo(WebDriver webDriver, JsonObjectBuilder steps, String testName) {
    String videoId = "Invalid test name";
    String canvasData = "blank";
    if (testName == "Janus") {
      videoId = "myvideo";
    } else if (testName == "Jitsi") {
      videoId = "localVideo_";
    } else if (testName == "OpenVidu") {
      videoId = "local-video-CAMERA";
    }

    boolean success;
    JsonObjectBuilder video = Json.createObjectBuilder();
    long videoLoadingTime = 0;
    String result = RESULT_FAIL;
    try {
      logger.info(
        logHeader
          + "- Looking for video object, timeout after "
          + loadTest.getTestTimeout()
          + "s.");
      long currentTime = System.currentTimeMillis();
      if (webDriver.getPageSource().contains(">404</h1>")) {
        result = "404";
        throw new Exception("Error 404");
      }
      List<WebElement> videos = new ArrayList<>();
      for (int i = 0; i < loadTest.getTestTimeout() * 1000; i += INTERVAL) {
        videos = webDriver.findElements(By.cssSelector("[id^=" + videoId + "]"));
        if (videos.isEmpty()) {
          Thread.sleep(INTERVAL);
        } else {
          videoLoadingTime = System.currentTimeMillis() - currentTime;
          break;
        }
      }
      if (videos.isEmpty()) {
        result = RESULT_FAIL;
        videoLoadingTime = 0;
      } else {
        logger.info(
          logHeader + "- Local video found, computing canvas (timeout after "
            + loadTest.getTestTimeout()
            + "s)");
        // Checking video display
        for (int i = 0; i < loadTest.getTestTimeout() * 1000; i += INTERVAL) {
          try {
            JsonObject checksumResult = TestUtils.videoCheckSum(webDriver,
              loadTest.pageTitle.equalsIgnoreCase("Jitsi") ? 1 : 0);

            canvasData = checksumResult.getString("result").toLowerCase();
            if (canvasData.equalsIgnoreCase("blank")
              || canvasData.equalsIgnoreCase("still")) {
              Thread.sleep(INTERVAL);
            } else {
              result = RESULT_PASS;
              break;
            }
          } catch (InterruptedException e) {
            logger.warn(
              logHeader + "- stepLocalVideo(): InterruptedException " + e.getLocalizedMessage());
            Thread.currentThread().interrupt();
          } catch (JavascriptException e) {
            logger.warn(
              logHeader
                + "- stepLocalVideo(): Attempt "
                + (i + 1)
                + "/"
                + loadTest.getTestTimeout()
                + " to get video failed: "
                + e.getLocalizedMessage());
            Thread.sleep(INTERVAL);
          }
        }
      }
    } catch (Exception e) {
      logger.error(logHeader + "Exception in stepLocalVideo " + e.getMessage());
    } finally {
      // make sure the 3 variables are always added to the video object
      video.add("videoFound", result);
      video.add("videoLoadingTime", videoLoadingTime);
      video.add("videoCheck", canvasData);
      steps.add("localVideo", video);
      success = result == RESULT_PASS;
    }
    return success;
  }


  /**
   * Method that will check all the videos displayed on the page and compute the canvas data.
   *
   * @param webDriver
   * @return true if the canvas is > 0 for all videos.
   */
  protected boolean stepAllVideos(WebDriver webDriver, JsonObjectBuilder steps, int startIndex) {
    String canvasAllVideos = "";
    boolean success;
    JsonObjectBuilder video = Json.createObjectBuilder();
    long videosLoadingTime = 0;
    String result = RESULT_FAIL;
    int currentStep = this.id / loadTest.increment + 1;
    int numberOfVideos =
      Math.min(currentStep * loadTest.increment, loadTest.maxUsersPerRoom);
    try {
      logger.info(
        logHeader
          + "- Looking for presence of "
          + numberOfVideos
          + " videos (timeout after "
          + loadTest.getTestTimeout()
          + "s)");
      long currentTime = System.currentTimeMillis();
      if (webDriver.getPageSource().contains(">404</h1>")) {
        result = "404";
        throw new Exception("Error 404");
      }
      List<WebElement> videos = new ArrayList<>();
      for (int i = 0; i < loadTest.getTestTimeout() * 1000; i += INTERVAL) {
        videos = webDriver.findElements(By.tagName("video"));
        if (videos.size() < numberOfVideos) {
          Thread.sleep(INTERVAL);
        } else {
          videosLoadingTime = System.currentTimeMillis() - currentTime;
          break;
        }
      }
      if (videos.size() < numberOfVideos) {
        result = RESULT_FAIL;
        logger.info(
          logHeader
            + "- "
            + videos.size()
            + " videos found.");
      } else {
        String[] canvasData = new String[videos.size()];
        logger.info(
          logHeader + "- All requested videos found after " + videosLoadingTime / 1000 + "s.");
        int validVideos = 0;
        logger.info(
          logHeader
            + "- Computing canvas data of "
            + videos.size()
            + " videos.");
        // Checking video display
        String errMsg = "";
        for (int i = startIndex; i < videos.size(); i++) {
          canvasData[i] = "blank";
          for (int j = 0; j < loadTest.getTestTimeout() * 100; j += INTERVAL) {
            try {
              JsonObject checksumResult = TestUtils.videoCheckSum(webDriver, i);
              canvasData[i] = checksumResult.getString("result").toLowerCase();
              if (canvasData[i].equalsIgnoreCase("blank")
                || canvasData[i].equalsIgnoreCase("still")) {
                Thread.sleep(INTERVAL);
              } else {
                validVideos++;
                break;
              }
            } catch (InterruptedException e) {
              logger.warn(logHeader
                + "- stepCountVideos(): InterruptedException "
                + e.getLocalizedMessage());
              Thread.currentThread().interrupt();
            } catch (JavascriptException e) {
              if (errMsg.equals(e.getLocalizedMessage())) {
                // no need to try again 60 times.
                break;
              }
              logger.warn(
                logHeader
                  + "- stepCountVideos(): Attempt "
                  + (j + 1)
                  + "/"
                  + loadTest.getTestTimeout()
                  + " to get video "
                  + (i + 1)
                  + "/"
                  + videos.size()
                  + " failed: "
                  + e.getLocalizedMessage()
                  + "\r\n"
                  + TestUtils.getStackTrace(e));
              errMsg = e.getLocalizedMessage();
            }
          }
          canvasAllVideos += canvasData[i] + (i < videos.size() - 1 ? "|" : "");
        }

        if (validVideos == videos.size() - startIndex || validVideos >= loadTest.maxUsersPerRoom) {
          result = RESULT_PASS;
        } else {
          logger.warn(
            logHeader
              + "- Error in stepCountVideo(), "
              + validVideos
              + "/"
              + (videos.size() - startIndex)
              + " valid videos found");
        }
      }
    } catch (Exception e) {
      logger.error(logHeader + "Exception in stepCountVideos " + e.getMessage());
    } finally {
      // make sure the 3 variables are always added to the video object
      video.add("noVideoFound", numberOfVideos);
      video.add("totalLoadingTime", videosLoadingTime);
      video.add("videoCheck", canvasAllVideos);
      steps.add("allVideos", video);
      success = result == RESULT_PASS;
    }
    return success;
  }

  protected boolean stepAllVideos(WebDriver webDriver, JsonObjectBuilder steps) {
    return stepAllVideos(webDriver, steps,0);
  }

  protected void stepStatsAllVideos(JsonObjectBuilder steps, String prefix) throws InterruptedException {
    int collectionTime = loadTest.getStatsCollectionTime();
    int collectionInterval = loadTest.getStatsCollectionInterval();
    if (collectionTime > 0 && collectionInterval > 0) {
      long startTime = System.currentTimeMillis();
      BasePCStatsArray statsArray = multiPcTests.contains(loadTest.pageTitle)
        ? new MultiPCStatsArray() : new SinglePCStatsArray();
      if (statsArray instanceof MultiPCStatsArray) {
        logger.info(logHeader + " getting stats as MultiPCStatsArray for " + loadTest.testName);
      } else {
        logger.info(logHeader + " getting stats as OnePCStatsArray for " + loadTest.testName);
      }

      for (int i = 0; i < collectionTime; i += collectionInterval) {
        Thread.sleep(collectionInterval * 1000);
        JsonObject jsonStatObject = stepStatOnce(steps, prefix);
        if (jsonStatObject != null) {
          //print all stats into an individual json file, into a separate folder for each node
          TestStats.getInstance(prefix)
            .printJsonTofile(prefix + "getstats_" + this.botName, jsonStatObject.toString(),
              loadTest.getResultPath() + "getstats/" + this.botName + "/");
          statsArray.add(jsonStatObject);
        }
      }
      logger.info(logHeader + "- Time to get Stats : " + ((System.currentTimeMillis() - startTime) / 1000) + "s");
      steps.add(prefix + "videoStats", allVideoStats(statsArray));
      steps.add(prefix + "audioStats", allAudioStats(statsArray));
    } else {
      logger.error("Check config file, both collectionTime and collectionInterval must be positive, now" +
        " collectionTime = " + collectionTime + ", collectionInterval = " + collectionInterval);
    }
  }

  private JsonObject stepStatOnce(JsonObjectBuilder steps, String prefix) throws InterruptedException {
    JsonObjectBuilder statObjectBuilder = Json.createObjectBuilder();

    if (loadTest.pageTitle.equals("Jitsi")) {
      Object allPcStatArray = GetStatsUtils.getStatsOnce("jitsi", webDriver);
      if (allPcStatArray == null) {
        logger.error(logHeader + " - failed to getStatsOnce(\"jitsi\")");
        return null;
      }
      ArrayList allStatsArray = (ArrayList) allPcStatArray;
      // allStatsArray is supposed to only have one item (localStats), but add the others if they exist.
      for (int i = 0; i < allStatsArray.size(); i++) {
        statObjectBuilder.add((i == 0? "localStats" : "PC_" + Integer.toString(i) + "_stats"), GetStatsUtils.buildStatArray(allStatsArray.get(i)));
      }
    } else if (loadTest.pageTitle.equals("parsys")) {
      Object remoteStats = GetStatsUtils.getStatsOnce("remote", webDriver);
      if (remoteStats == null) {
        logger.error(logHeader + " - failed to getStatsOnce(\"remote\")");
      } else {
        ArrayList remoteStatsArray = (ArrayList) remoteStats;
        for (int i = 0; i < remoteStatsArray.size(); i++) {
          statObjectBuilder.add(i == 0 ? "local" : "remoteStats" + Integer.toString(i), GetStatsUtils.buildStatArray(remoteStatsArray.get(i)));
        }
      }
    } else {
      Object localStatArray = GetStatsUtils.getStatsOnce("local", webDriver);
      if (localStatArray == null) {
        logger.error(logHeader + " - failed to getStatsOnce(\"local\")");
        return null;
      }

      statObjectBuilder.add("localStats", GetStatsUtils.buildStatArray(localStatArray));

      if (multiPcTests.contains(loadTest.pageTitle)) {
        Object remoteStats = GetStatsUtils.getStatsOnce("remote", webDriver);
        if (remoteStats == null) {
          logger.error(logHeader + " - failed to getStatsOnce(\"remote\")");
        } else {
          ArrayList remoteStatsArray = (ArrayList) remoteStats;
          for (int i = 0; i < remoteStatsArray.size(); i++) {
            statObjectBuilder.add("remoteStats" + Integer.toString(i), GetStatsUtils.buildStatArray(remoteStatsArray.get(i)));
          }
        }
      }
    }

    JsonObject jsonStatObject = statObjectBuilder.build();
    TestStats.getInstance(prefix)
      .printJsonTofile(prefix + "getstats_" + this.botName, jsonStatObject.toString(), loadTest.getResultPath() + "getstats/");
    steps.add("stats" + prefix, selectedStats(jsonStatObject));
    return jsonStatObject;
  }

  private JsonObjectBuilder selectedStats(JsonObject jsonStatObject) {
    JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
    JsonObject bweForVideo = jsonStatObject.getJsonObject("localStats") != null ?
      jsonStatObject.getJsonObject("localStats").getJsonObject("bweforvideo") : null;
    if (receiveOnly) {
      jsonObjectBuilder.add("googAvailableReceiveBandwidth",
        bweForVideo != null ?
          bweForVideo.getString("googAvailableReceiveBandwidth") : "");

      JsonObject connaudio10 = jsonStatObject.getJsonObject("localStats") != null ?
        jsonStatObject.getJsonObject("localStats").getJsonObject("Conn-audio-1-0") : null;
      jsonObjectBuilder.add("googRtt",
        connaudio10 != null ?
          connaudio10.getString("googRtt") : "");
      jsonObjectBuilder.add("googRemoteAddress",
        connaudio10 != null ?
          connaudio10.getString("googRemoteAddress") : "");
    } else {
      jsonObjectBuilder.add(
        "googAvailableSendBandwidth",
        bweForVideo != null ? bweForVideo.getString("googAvailableSendBandwidth") : "");
      jsonObjectBuilder.add(
        "googActualEncBitrate",
        bweForVideo != null ? bweForVideo.getString("googActualEncBitrate") : "");
      jsonObjectBuilder.add(
        "googTransmitBitrate",
        bweForVideo != null ? bweForVideo.getString("googTransmitBitrate") : "");
      jsonObjectBuilder.add(
        "googTargetEncBitrate",
        bweForVideo != null ? bweForVideo.getString("googTargetEncBitrate") : "");
    }
    return jsonObjectBuilder;
  }

  private JsonObjectBuilder allVideoStats(BasePCStatsArray statsArray) {
    JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
    Map<String, String> totalVideosBytes = statsArray.getTotalAVBytes(BasePCStatsArray.VIDEO);
    Map<String, String> videosAvgBitrate = statsArray.getAVAvgBitrate(BasePCStatsArray.VIDEO);
    Map<String, String> videosPacketLoss = statsArray.getAVPacketLoss(BasePCStatsArray.VIDEO);
    Map<String, String> frameRate = statsArray.getFrameRate();
    String googRtt = statsArray.getSentVideoRtt();
    jsonObjectBuilder = addFields(jsonObjectBuilder, "totalBytes", totalVideosBytes);
    jsonObjectBuilder = addFields(jsonObjectBuilder, "avgBitrate", videosAvgBitrate);
    jsonObjectBuilder = addFields(jsonObjectBuilder, "packetLoss", videosPacketLoss);
    jsonObjectBuilder = addFields(jsonObjectBuilder, "googFrameRate", frameRate);
    if (!receiveOnly) {
      jsonObjectBuilder.add("senderGoogRtt", googRtt);
    }
    return jsonObjectBuilder;
  }

  private JsonObjectBuilder allAudioStats(BasePCStatsArray statsArray) {
    JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
    Map<String, String> totalAudiosBytes = statsArray.getTotalAVBytes(BasePCStatsArray.AUDIO);
    Map<String, String> audiosAvgBitrate = statsArray.getAVAvgBitrate(BasePCStatsArray.AUDIO);
    Map<String, String> audiosPacketLoss = statsArray.getAVPacketLoss(BasePCStatsArray.AUDIO);
    Map<String, String> audiosJitter = statsArray.getAudiosJitter();
    jsonObjectBuilder = addFields(jsonObjectBuilder, "totalBytes", totalAudiosBytes);
    jsonObjectBuilder = addFields(jsonObjectBuilder, "avgBitrate", audiosAvgBitrate);
    jsonObjectBuilder = addFields(jsonObjectBuilder, "packetLoss", audiosPacketLoss);
    jsonObjectBuilder = addFields(jsonObjectBuilder, "jitter", audiosJitter);
    return jsonObjectBuilder;
  }

  private JsonObjectBuilder addFields(JsonObjectBuilder initialBuilder, String fieldName, Map<String, String> values) {
    JsonObjectBuilder jsonObjectBuilder = initialBuilder;
    int maxFields = receiveOnly ? 1 : loadTest.maxUsersPerRoom;
    int noFields = 0;
    //first add the sender stats (we want the sender to always be the first
    for (String key : values.keySet()) {
      if (key.contains("send") && noFields < 1) {
        jsonObjectBuilder.add(fieldName + "_send", values.get(key));
        noFields++;
      }
    }
    //then add the received video stats
    for (String key : values.keySet()) {
      if (!key.contains("send")) {
        if (noFields < maxFields) {
          jsonObjectBuilder.add(fieldName + "_recv_" + noFields, values.get(key));
          noFields++;
        }
      }
    }
    //fill with empty data if not full
    for (int i = noFields; i < maxFields; i++) {
      jsonObjectBuilder.add(fieldName + "_recv_" + (i + 1), "pad");
    }
    return jsonObjectBuilder;
  }

  /**
   * Saves logs into a file.
   *
   * @return true if the file has been written, false otherwise
   */
  protected boolean stepBrowserLogs(String prefix) {
    boolean success = true;
    try {
      List<String> logs = TestUtils.analyzeLog(webDriver);
      String filename =
        prefix + "BrowserLogs"
          + this.botName
          + "_"
          + new SimpleDateFormat("yyyyMMdd_hhmmss").format(new Date())
          + ".log";
      String path = loadTest.getResultPath() + "consoleLogs/";
      File dir = new File(path);
      if (!dir.isDirectory()) {
        dir.mkdirs();
      }
      FileOutputStream fo = new FileOutputStream(path + filename);
      PrintWriter pw = new PrintWriter(fo);
      for (String str : logs) {
        pw.println(str);
      }
      pw.close();
      fo.close();
    } catch (Exception e) {
      logger.error("\r\n" + TestUtils.getStackTrace(e));
      success = false;
    }
    return success;
  }


  /**
   * Performs the network instrumentation test steps:
   *  if this tester ID is part of the clientIDs, connect to the VM with SSH
   *  and send the commands.
   *
   * @return true the test steps are all successful, false otherwise
   * @throws Exception
   */
  protected boolean nwInstPreloadSteps() throws Exception {
    boolean success = true;
    if (!skipNWInstrumentation && nwInstConfig.gatewaySetup()) {
      // pre-load steps are only applicable in case of GW setup
      try {
        // logger.debug(" NW inst dump: " + nwInstConfig.toString());
        String nodeIp = nodePublicIp();
        String privateIp = this.nodePrivateIp();
        logger.info("NW inst. pre-load steps for client ID " + this.id + " on the gateway.");
        nwInstConfig.addStaticRouteOnGateway(privateIp);
        nwInstConfig.addStaticRouteOnClient(id, nodeIp);
        Thread.sleep(1500);
      } catch (Exception e) {
        logger.error(" Error in nwInstSteps : " + TestUtils.getStackTrace(e));
        success = false;
      }
    }
    return success;
  }




  /**
   * Performs the network instrumentation test steps:
   *  if this tester ID is part of the clientIDs, connect to the VM with SSH
   *  and send the commands.
   *
   * @param steps
   * @return true the test steps are all successful, false otherwise
   * @throws Exception
   */
  protected boolean nwInstPostLoadSteps(JsonObjectBuilder steps) throws Exception {
    boolean success = true;
    String commandLine = "";
    String nodeIp = "";
    if (skipNWInstrumentation) {
      return true;
    }
    try {
      logger.debug(" NW inst dump: " + nwInstConfig.toString());
      nodeIp = nodePublicIp();
      if (nwInstConfig.gatewaySetup()) {
        logger.info("NW inst. post-load steps for client ID " + this.id + " on the gateway.");
        cleanUpneeded = true;
      } else {
        logger.info("NW inst. post-load steps for client ID " + this.id + "  on the client only.");
      }
      commandLine = nwInstConfig.runCommands(id, nodeIp);
      Thread.sleep(1500);
    } catch (Exception e) {
      logger.error(" Error in nwInstSteps : " + TestUtils.getStackTrace(e));
      success = false;
    }
    steps.add("node IP", nodeIp);
    steps.add("NW Inst command", commandLine);
    return success;
  }


  /**
   * Computes the end to end latency by taking a screenshot of the loopback page
   * and the diff of the timestamp between the publisher and viewer frames.
   *
   * @param xPub x coord of the publisher frame
   * @param xVie x coord of the viewer frame
   * @param y y coord of the viewer and publisher frame
   * @param w width of the viewer and publisher frame
   * @param h height of the viewer and publisher frame
   * @throws IOException
   */
  protected void endToEndLatency(int xPub, int xVie, int y, int w, int h)
    throws IOException {
    //stub
  }

  /**
   * method to be overriden by the implementing class
   *
   * @param steps
   * @return true the test steps are all successful, false otherwise
   */
  protected abstract boolean testSteps(JsonObjectBuilder steps) throws Exception;

  /**
   * method to be overriden by the implementing class
   *
   * @param steps
   * @return true the test steps are all successful, false otherwise
   */
  protected abstract boolean endSteps(JsonObjectBuilder steps) throws Exception;

}
