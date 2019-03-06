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

import com.cosmo.kite.util.MeetingStatus;
import com.cosmo.kite.util.RoomManager;
import com.cosmo.kite.util.TestUtils;
import org.openqa.selenium.*;
import org.apache.log4j.Logger;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.cosmo.kite.util.TestUtils.getStackTrace;

/**
 * JanusTest implementation of KiteLoadTest.
 * <p>
 *     todo write test description
 */
public class JanusTest extends KiteLoadTest {


  private static final Logger logger = Logger.getLogger(JanusTest.class.getName());

  private final String CONF_1ROOM = "1room";
  private final String CONF_KROOM = "krooms";
  private final String INTEROP = "interop";
  private final String STR_VIEWER = "viewer";
  private final String STR_PUBLISHER = "publisher";
  private String testType;


  private int loadReachTime = 0;
  private int reconnectionDelay = 0;
  private int reconnectionRandom = 0;
  private int chanceOfMeetingEnded = 0;
  private int checkStatusPeriod = 0;

  /**
   * Constructor for unit test to allow setting the url without any config.json
   *
   * @param url (e.g.: https://lbclient.cosmosoftware.io/videoroomtest_large.html)
   */
  public JanusTest(String url, String pageTitle) {
    this.url = url;
//    this.testType = url.contains("publisher") ? STR_PUBLISHER :
//            (url.contains("viewer") ? STR_VIEWER : CONF_1ROOM);
    this.pageTitle = pageTitle;
    testType = INTEROP;
    setMaxUsersPerRoom();
  }

  /**
   * default constructor
   */
  public JanusTest() {}

  /**
   * Restructuring the test according to options given in payload object from config file. This
   * function will not be the same for every test.
   */
  @Override
  protected void payloadHandling() {
    super.payloadHandling();
    JsonObject jsonPayload = (JsonObject) this.getPayload();
    String[] rooms = null;
    if (jsonPayload != null) {
      testName = jsonPayload.getString("name", "Janus Load Test");
      testType = jsonPayload.getString("testType", null);
      loadReachTime = jsonPayload.getInt("loadReachTime", loadReachTime);
      expectedTestDuration = Math.max(expectedTestDuration, (loadReachTime + 300)/60);
      reconnectionDelay = jsonPayload.getInt("reconnectionDelay", reconnectionDelay);
      reconnectionRandom = jsonPayload.getInt("reconnectionRandom", reconnectionRandom);
      maxUsersPerRoom = jsonPayload.getInt("usersPerRoom", 0);
      chanceOfMeetingEnded = jsonPayload.getInt("chanceOfMeetingEnded", 0);
      checkStatusPeriod = jsonPayload.getInt("checkStatusPeriod", 0);
      JsonArray jsonArray = jsonPayload.getJsonArray("rooms");
      rooms = new String[jsonArray.size()];
      for (int i = 0; i < jsonArray.size(); i++) {
        rooms[i] = jsonArray.getString(i);
      }
    }
    if (testType == null) {
      logger.error("testType cannot be empty. Options are:\r\nviewer, publisher, 1room, krooms");
    }
    testType = testType.toLowerCase();
    setMaxUsersPerRoom();
    if (rooms != null) {
      roomManager.setRoomNames(rooms);
    }
  }

  protected void setMaxUsersPerRoom() {
    switch (testType) {
      case INTEROP:
        // interop is one room with 2 participants
        // and we're using increment as the number of participants
        KiteLoadTest.roomManager = RoomManager.getInstance(url, maxUsersPerRoom);
        break;

      case CONF_1ROOM:
      case CONF_KROOM:
        if (roomManager == null) {
          maxUsersPerRoom = maxUsersPerRoom == 0 ? increment : maxUsersPerRoom;
          KiteLoadTest.roomManager = RoomManager.getInstance(url, maxUsersPerRoom);
        }
        break;

      case STR_PUBLISHER:
      case STR_VIEWER:
        //force increment to 1
        maxUsersPerRoom = 1;
        break;
    }
  }

  /**
   *
   * @param webDriverList
   * @return list of CallableTester
   */
  @Override
  protected List<CallableTester> getTesterList(List<WebDriver> webDriverList ) {
    List<CallableTester> testerList = new ArrayList<CallableTester>();
    try {
      for (WebDriver webDriver : webDriverList) {
        testerList.add(
          new JanusTester(webDriver, this.getSessionData() != null ?
            this.getSessionData().get(webDriver) : null, url, this));
      }
    } catch (Exception e) {
      logger.error("Error in " + this.getClass().getName() + ".getTesterList(): \r\n" + TestUtils.getStackTrace(e));
    }
    return testerList;
  }

  private class JanusTester extends CallableTester {

    private String meetingId = "";
    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    public JanusTester(WebDriver webDriver, Map<String, String> sessionData, String url, KiteLoadTest loadTest)
      throws Exception {
      super(webDriver, sessionData, url, loadTest);
      if (testType.equals(STR_PUBLISHER) || testType.equals(STR_VIEWER)) {
        //do nothing
      } else {
        this.url = RoomManager.getInstance().getRoomUrl()  + "&username=user" + TestUtils.idToString(id);
        this.meetingId = this.url.substring(this.url.indexOf("roomId=") + "roomId=".length(),
          this.url.indexOf("&username=user"));
      }
    }

    /**
     * method where the test steps are implemented.
     * @param steps
     * @return true the test steps are all successful, false otherwise
     */
    @Override
    protected boolean testSteps(JsonObjectBuilder steps) throws InterruptedException {
      boolean problem = false;

      switch(testType) {
        case STR_VIEWER:
          receiveOnly = true;
          problem = viewerSteps(steps);
          break;

        case STR_PUBLISHER:
          problem = publisherSteps(steps);
          break;

        default:
          problem = confSteps(steps);
          break;
      }

      return !problem;
    }

    private boolean viewerSteps(JsonObjectBuilder steps) throws InterruptedException {
      boolean problem = false;
      logger.info(logHeader + "- Waiting for remote video");
      Thread.sleep(10000);
      problem |= !stepVideo(webDriver, steps, 0);
      logger.info(logHeader + "- stepVideo() " + (problem ? "Fail" : "Success"));
      return problem;
    }


    private boolean publisherSteps(JsonObjectBuilder steps) throws InterruptedException {

      boolean problem = false;
      logger.info(logHeader + "- Waiting for local video");
      Thread.sleep(10000);
      problem |= !stepVideo(webDriver, steps, 0);
      logger.info(logHeader + "- stepVideo() " + (problem ? "Fail" : "Success"));
      return problem;
    }


    private boolean confSteps(JsonObjectBuilder steps) throws InterruptedException {

      boolean problem = false;

      logger.info(logHeader + "- Waiting for local video");
      for (int i = 0; i < 3; i++) {
        try {
          WebDriverWait wait2 = new WebDriverWait(webDriver, 10);
          WebElement publishing =
            wait2.until(
              ExpectedConditions.presenceOfElementLocated(
                By.xpath("//b[text()='Publishing...']")));
          wait2.until(ExpectedConditions.invisibilityOf(publishing));
          break;
        } catch (TimeoutException e) {
          logger.warn(logHeader + "- " + e.getLocalizedMessage());
          logger.info(logHeader + "- reloading the page (" + (i + 1) + "/3)");
          loadPage();
        }
      }
      problem |= !stepVideo(webDriver, steps, 0);
      logger.info(logHeader
        + "- stepLocalVideo() "
        + (problem ? "Fail" : "Success"));


      problem |= !stepAllVideos(webDriver, steps);
      logger.info(logHeader + "- stepAllVideos() " + (problem ? "Fail" : "Success"));

      problem |= !stepRemoteSpinners(webDriver);
      logger.info(logHeader + "- stepRemoteSpinners() " + (problem ? "Fail" : "Success"));


      if (loadTest.takeScreenshotForEachTest()) {
        TestUtils.takeScreenshot(
          webDriver, this.screenShotPath, screenshotFilename + "_0");
      }
      return problem;
    }


    private boolean stepRemoteSpinners(WebDriver webDriver) {
      boolean success;
      long spinnerLoadingTime = 0;
      String result = RESULT_FAIL;
      try {
        logger.info(logHeader + "- Looking for absence of all remote spinners, timeout after " + this.loadTest.getTestTimeout() + "s.");
        long currentTime = System.currentTimeMillis();
        if (webDriver.getPageSource().contains(">404</h1>")) {
          result = "404";
          throw new Exception("Error 404");
        }
        List<WebElement> spinners = new ArrayList<>();
        for (int i = 0; i < this.loadTest.getTestTimeout(); i++) {
          spinners = webDriver.findElements(By.className("spinner"));
          if (!spinners.isEmpty()) {
            Thread.sleep(1000);
          } else {
            spinnerLoadingTime = System.currentTimeMillis() - currentTime;
            break;
          }
        }
        if (!spinners.isEmpty()) {
          result = RESULT_FAIL;
          logger.info(logHeader + "- At least one remote spinners left after " + this.loadTest.getTestTimeout() + "s.");
        } else {
          logger.info(logHeader + "- Absence of all remote spinners after " + spinnerLoadingTime/1000 + "s.");
          result = RESULT_PASS;
        }
      } catch (Exception e) {
        logger.error(logHeader + "Exception in stepRemoteSpinners " + e.getMessage());
      } finally {
        success = result == RESULT_PASS;
      }
      return success;
    }

    /**
     * method where the test steps are implemented, which are executed once the target load is
     * reached.
     *
     * @param steps
     * @return true the test steps are all successful, false otherwise
     */
    @Override
    protected boolean endSteps(JsonObjectBuilder steps) {
      if (STR_PUBLISHER.equalsIgnoreCase(testType)) {
        return endStepsStreamingTest(steps);
      } else {
        return endStepsConf(steps);
      }
    }



    /**
     * method where the test steps are implemented for Janus video conf
     * configuration (meeting room plugin)
     *
     *
     * @param steps
     * @return true the test steps are all successful, false otherwise
     */
    private boolean endStepsConf(JsonObjectBuilder steps) {
      boolean success = true;
      long startTS = System.currentTimeMillis();
      long meetingEndTime = startTS + loadReachTime * 1000;
      try {
        long nextDeco = System.currentTimeMillis() + reconnectionDelay * 1000
          + (int)(Math.random() * reconnectionRandom * 1000);
        logger.info(logHeader + " endStepsConf() STARTS - Page refresh at " + df.format(new Date(nextDeco)));
        while (System.currentTimeMillis() < meetingEndTime) {
          MeetingStatus status = roomManager.get(meetingId);

          if (status != null && checkStatusPeriod > 0 && status.meetingEnded(chanceOfMeetingEnded, checkStatusPeriod)) {
            logger.info(logHeader + "\n\r"
              + "********************************************************************************\r\n"
              + "   The meeting " + meetingId + " has ended, \r\n"
              + "   refreshing the page.\n\r"
              + "********************************************************************************");
            nextDeco = reconnectToMeeting();
            Thread.sleep(checkStatusPeriod * 1000);
          } else {

            if (status == null) {
              logger.error(logHeader + " meeting " + meetingId + " not found. RoomManager roomId dump:");
              for (String key : roomManager.keySet()) {
                logger.warn(logHeader + "   "+ key.toString());
              }
              return false;
            }

            if (System.currentTimeMillis() > nextDeco) {
              nextDeco = reconnectToMeeting();
            }
            Thread.sleep(1000);
          }
        }
        logger.info(logHeader + " endStepsConf() SUCCESSFULLY COMPLETED after "
          + (System.currentTimeMillis() - startTS)/1000 + "s." );
      } catch (Exception e) {
        logger.error("Exception in endSteps: " + getStackTrace(e));
        return false;
      }
      return success;
    }


    /**
     * Reconnects to a meeting by reloading the page.
     *
     * @return the timestamp for the next reconnection.
     */
    private long reconnectToMeeting() {
      screenshotFilename = "rl_" + fileTS.format(new Date()) + "_" + botName;
      long nextDeco = System.currentTimeMillis() + reconnectionDelay * 1000
        + (int)(Math.random() * (reconnectionRandom * 1000));
      boolean success = stepVideo(webDriver, Json.createObjectBuilder(), 0);
      if (loadTest.takeScreenshotForEachTest()) {
        TestUtils.takeScreenshot(webDriver, this.screenShotPath, screenshotFilename);
      }
      webDriver.navigate().refresh();
      logger.info(logHeader + " Page refreshed, video check " + (success ? "SUCCESSFUL" : "FAILED")
        + ". Next refresh at " + df.format(new Date(nextDeco)) + ".");
      return nextDeco;
    }


    /**
     * method where the test steps are implemented for Janus in Streaming configuration (SOLEIL)
     *
     * @param steps
     * @return true the test steps are all successful, false otherwise
     */
    private boolean endStepsStreamingTest(JsonObjectBuilder steps) {
      try {
        endToEndLatency(80, 764, 471, 267, 41);
      } catch (IOException e) {
        logger.warn("IOException in endSteps " + e.getLocalizedMessage());
        return false;
      }
      return true;
    }
  }



}
