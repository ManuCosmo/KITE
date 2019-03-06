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

package com.cosmo.kite.util;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class TestUtils {

  private static final Logger logger = Logger.getLogger(TestUtils.class.getName());

  static private final String IPV4_REGEX = "(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))";
  static private Pattern IPV4_PATTERN = Pattern.compile(IPV4_REGEX);


  /**
   * Copy the String s to clipboard
   *
   * @param s the String to be copied to the clipboard
   */
  public static void copyToClipboard(String s) {
    StringSelection selection = new StringSelection(s);
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    clipboard.setContents(selection, selection);
  }

  /**
   * Creates a Json Array (as a String) from a List of JsonObject provided. E.g.:
   * createJsonArray("result", myList) returns { "result" : [ jsonObject1, jsonObject2....] }
   *
   * @param key a JSON key for the json array
   * @param jsonList the list of JsonObject to combine into an array
   * @return the json object as a string
   */
  public static String createJsonArray(String key, List<JsonObject> jsonList) {
    String s = "{ \"" + key + "\" : ";
    s += jsonList.size() > 0 ? "\r\n  [\r\n" : "\"jsonList is empty\"}\r\n";
    for (JsonObject list : jsonList) {
      s += "    " + list.toString();
      s += jsonList.indexOf(list) == (jsonList.size() - 1) ? "\r\n" : ",\r\n";
    }
    s += jsonList.size() > 0 ? "  ]\r\n}" : "";
    return s;
  }

  /**
   * @param id an int between 0 and 999
   * @return a String with leading zero padding (e.g. 001, 029...)
   */
  public static String idToString(int id) {
    return "" + (id < 10 ? "00" + id : (id < 100 ? "0" + id : "" + id));
  }

  /**
   * Saves a screenshot of the webdriver/browser under "report/" + filename + ".png"
   *
   * @param driver the webdriver
   * @param filename the name of the file without path ("report/") and extension (" .png")
   * @return true if successful, false otherwise
   */
  public static boolean takeScreenshot(WebDriver driver, String path, String filename) {
    try {
      File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
      File dir = new File(path);
      if (!dir.isDirectory()) {
        dir.mkdirs();
      }
      String s = path + filename + ".png";
      FileUtils.copyFile(scrFile, new File(s));
      logger.info(s);
      return true;
    } catch (Exception e) {
      logger.error(
              "Exception in takeScreenshot: "
                      + e.getLocalizedMessage()
                      + "\r\n"
                      + TestUtils.getStackTrace(e));
      return false;
    }
  }

  /**
   * @return whether the peer connection exists
   * @throws InterruptedException
   */
  public static boolean checkPeerConnectionObject(WebDriver webDriver, int TIMEOUT, int INTERVAL)
      throws InterruptedException {
    String checkPeerConnectionExistScript =
        "var res;"
            + "try {res = pc} catch (exception) {} "
            + "if (res) {return true;} else {return false;}";

    for (int i = 0; i < TIMEOUT; i += INTERVAL) {
      boolean pcExist =
          (boolean) ((JavascriptExecutor) webDriver).executeScript(checkPeerConnectionExistScript);
      if (!pcExist) {
        Thread.sleep(INTERVAL);
      } else {
        return true;
      }
    }
    return false;
  }

  /**
   * @return whether the peer connection state is connected
   * @throws InterruptedException
   */
  public static boolean checkPeerConnectionState(WebDriver webDriver, int TIMEOUT, int INTERVAL)
      throws InterruptedException {
    String checkIceConnectionStateScript =
        "var res;"
            + "try {res = pc.iceConnectionState} catch (exception) {} "
            + "if (res==='connected' || res==='completed' ) {return true;} else {return false;}";

    for (int i = 0; i < TIMEOUT; i += INTERVAL) {
      boolean connected =
          (boolean) ((JavascriptExecutor) webDriver).executeScript(checkIceConnectionStateScript);
      if (!connected) {
        Thread.sleep(INTERVAL);
      } else {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the test's canvasCheck to check if the video identified by the given id is blank, and
   * if it changes overtime.
   *
   * @return the canvasCheck as string.
   */
  public static final String getVideoFrameValueSumById(String videoId) {
    return "function getSum(total, num) {"
        + "    return total + num;"
        + "};"
        + "var canvas = document.createElement('canvas');"
        + "var ctx = canvas.getContext('2d');"
        + "var video = document.getElementById('"
        + videoId
        + "');"
        + "ctx.drawImage(video,0,0,video.videoHeight-1,video.videoWidth-1);"
        + "var imageData = ctx.getImageData(0,0,video.videoHeight-1,video.videoWidth-1).data;"
        + "var sum = imageData.reduce(getSum);"
        + "if (sum===255*(Math.pow(video.videoHeight-1,(video.videoWidth-1)*(video.videoWidth-1))))"
        + "   return 0;"
        + "return sum;";
  }

  /**
   * Returns the test's canvasCheck to check if the video is blank.
   *
   * @param index index of the video on the list of video elements.
   * @return the canvasCheck as string.
   */
  public static final String getVideoFrameValueSumByIndex(int index) {
    return "function getSum(total, num) {"
        + "    return total + num;"
        + "};"
        + "var canvas = document.createElement('canvas');"
        + "var ctx = canvas.getContext('2d');"
        + "var videos = document.getElementsByTagName('video');"
        + "var video = videos["
        + index
        + "];"
        + "if(video){"
        + "ctx.drawImage(video,0,0,video.videoHeight-1,video.videoWidth-1);"
        + "var imageData = ctx.getImageData(0,0,video.videoHeight-1,video.videoWidth-1).data;"
        + "var sum = imageData.reduce(getSum);"
        + "if (sum===255*(Math.pow(video.videoHeight-1,(video.videoWidth-1)*(video.videoWidth-1))))"
        + "   return 0;"
        + "return sum;"
        + "} else {"
        + "return 0 "
        + "}";
  }

  /**
   * Returns the test's canvasCheck to check if the video identified by the given query selector is
   * blank, and if it changes overtime.
   *
   * @return the canvasCheck as string.
   */
  public static final String getVideoFrameValueSumByCssSelector(String cssSelection) {
    return "function getSum(total, num) {"
        + "    return total + num;"
        + "};"
        + "var canvas = document.createElement('canvas');"
        + "var ctx = canvas.getContext('2d');"
        + "var video = document.querySelector('"
        + cssSelection
        + "');"
        + "ctx.drawImage(video,0,0,video.videoHeight-1,video.videoWidth-1);"
        + "var imageData = ctx.getImageData(0,0,video.videoHeight-1,video.videoWidth-1).data;"
        + "var sum = imageData.reduce(getSum);"
        + "if (sum===255*(Math.pow(video.videoHeight-1,(video.videoWidth-1)*(video.videoWidth-1))))"
        + "   return 0;"
        + "return sum;";
  }

  /**
   * Returns the test's canvasCheck to check if the video is blank.
   *
   * @param index index of the video on the list of video elements.
   * @return the canvasCheck as string.
   */
  public static final String getCanvasFrameValueSumByIndex(int index) {
    return "function getSum(total, num) {"
        + "    return total + num;"
        + "};"
        + "var canvas = document.createElement('canvas');"
        + "var ctx = canvas.getContext('2d');"
        + "var canvass = document.getElementsByTagName('canvas');"
        + "var canvas = canvass["
        + index
        + "];"
        + "if(canvas){"
        + "ctx.drawImage(canvas,0,0,canvas.height-1,canvas.width-1);"
        + "var imageData = ctx.getImageData(0,0,canvas.height-1,canvas.width-1).data;"
        + "var sum = imageData.reduce(getSum);"
        + "if (sum===255*(Math.pow(canvas.height-1,(canvas.width-1)*(canvas.width-1))))"
        + "   return 0;"
        + "return sum;"
        + "} else {"
        + "return 0 "
        + "}";
  }

  /**
   * Calls the playVideoScript function to play the video.
   *
   * @param video_id id the video in the list of video elements.
   * @throws InterruptedException
   */
  public static void playVideo(WebDriver webDriver, String video_id) throws InterruptedException {
    ((JavascriptExecutor) webDriver)
        .executeScript("document.getElementById('" + video_id + "').play();");
  }

  /**
   * Check the video playback by verifying the pixel sum of 2 frame between a time interval of
   * 500ms. if (getSum(frame2) - getSum(frame1) != 0 ) => return "video", if getSum(frame2) ==
   * getSum(frame1) > 0 => return "still" if getSum(frame2) == getSum(frame1) == 0 => return "blank"
   *
   * @param webDriver webdriver that control the browser
   * @param index index of the video element on the page in question
   */
  public static JsonObject videoCheckSum(WebDriver webDriver, int index)
      throws InterruptedException {
    long canvasData1 = 0, canvasData2 = 0;
    String result = "blank";
    JsonObjectBuilder resultObject = Json.createObjectBuilder();
    String getVideoFrameValueSumByIndex =
        "function getSum(total, num) {"
            + "    return total + num;"
            + "};"
            + "var canvas = document.createElement('canvas');"
            + "var ctx = canvas.getContext('2d');"
            + "var videos = document.getElementsByTagName('video');"
            + "var video = videos["
            + index
            + "];"
            + "if(video){"
            + "ctx.drawImage(video,0,0,video.videoHeight-1,video.videoWidth-1);"
            + "var imageData = ctx.getImageData(0,0,video.videoHeight-1,video.videoWidth-1).data;"
            + "var sum = imageData.reduce(getSum);"
            + "if (sum===255*(Math.pow(video.videoHeight-1,(video.videoWidth-1)*(video.videoWidth-1))))"
            + "   return 0;"
            + "return sum;"
            + "} else {"
            + "return 0 "
            + "}";
    canvasData1 =
        (long) ((JavascriptExecutor) webDriver).executeScript(getVideoFrameValueSumByIndex);
    Thread.sleep(500);
    canvasData2 =
        (long) ((JavascriptExecutor) webDriver).executeScript(getVideoFrameValueSumByIndex);
    if (canvasData1 == 0 && canvasData2 == 0) {
      result = "blank";
    } else {
      long diff = Math.abs(canvasData2 - canvasData1);
      if (diff != 0) {
        result = "video";
      } else {
        result = "still";
      }
    }

    resultObject.add("checksum1", canvasData1).add("checksum2", canvasData2).add("result", result);
    return resultObject.build();
  }

  /**
   * Returns stack trace of the given exception.
   *
   * @param e Exception
   * @return string representation of e.printStackTrace()
   */
  public static String getStackTrace(Throwable e) {
    Writer writer = new StringWriter();
    e.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

  /**
   * Checks whether the given string is not null and is not an empty string.
   *
   * @param value string
   * @return true if the provided value is not null and is not empty.
   */
  public static boolean isNotNullAndNotEmpty(String value) {
    return value != null && !value.isEmpty();
  }

  /**
   * Input a vlue to a specific field with id.
   *
   * @param webDriver browser in question
   * @param id id of the field element
   * @param value value to input
   */
  public static void input(WebDriver webDriver, String id, String value) {
    String script = "document.getElementById('" + id + "').value = '" + value + "'";
    ((JavascriptExecutor) webDriver).executeScript(script);
  }

  /**
   * Input a vlue to a specific field with id.
   *
   * @param webDriver browser in question
   * @param id id of the field element
   */
  public static void click(WebDriver webDriver, String id) {
    String script = "document.getElementById('" + id + "').click()";
    ((JavascriptExecutor) webDriver).executeScript(script);
  }

  /**
   * Retrieves the log displayed in the page (on purpose)
   *
   * @return
   */
  public static String getLog(WebDriver webDriver) {
    String res = "";
    WebElement logElem = webDriver.findElement(By.id("logs"));
    if (logElem != null) {
      List<WebElement> logLines = logElem.findElements(By.tagName("li"));
      if (logLines.size() == 0) {
        return "N/A";
      } else {
        for (WebElement logLine : logLines) {
          try {
            res += logLine.getText() + "/r/n";
          } catch (Exception e) {
            // do nothing
          }
        }
        return res;
      }
    }
    return "N/A";
  }

  /**
   * Returns the test's getSDPOfferScript to retrieve simulcast.pc.localDescription.sdp or
   * simulcast.pc.remoteDescription.sdp. If it doesn't exist then the method returns 'unknown'.
   *
   * @param local boolean
   * @return the getSDPOfferScript as string.
   */
  public static final String getSDPOfferScript(boolean local) {
    if (local) {
      return "var SDP;"
          + "try {SDP = pc.localDescription.sdp;} catch (exception) {} "
          + "if (SDP) {return SDP;} else {return 'unknown';}";
    } else {
      return "var SDP;"
          + "try {SDP = pc.remoteDescription.sdp;} catch (exception) {} "
          + "if (SDP) {return SDP;} else {return 'unknown';}";
    }
  }

  /**
   * Puts all result components together.
   *
   * @param result of the test.
   * @param stats from the test.
   * @param message if exists.
   * @param alertMsg if exists.
   * @return JsonObject
   */
  public static JsonObject developResult(
      String browser,
      String result,
      JsonObjectBuilder stats,
      String message,
      String alertMsg,
      JsonArrayBuilder log) {
    stats.add("log", log).add("message", message);
    if (alertMsg != null) {
      stats.add("alert", alertMsg);
    } else {
      stats.add("alert", "NA").add("alert", alertMsg);
    }

    return Json.createObjectBuilder()
        .add("browser", browser)
        .add("result", result)
        .add("stats", stats)
        .build();
  }

  /**
   * @param driver the subject web driver that we want to get console log
   * @return List of log entries.
   */
  public static List<String> analyzeLog(WebDriver driver) {
    List<String> log = new ArrayList<>();
    Set<String> logTypes = driver.manage().logs().getAvailableLogTypes();
    if (logTypes.contains(LogType.BROWSER)) {
      LogEntries logEntries = driver.manage().logs().get(LogType.BROWSER);
      for (LogEntry entry : logEntries) {
        log.add(entry.getLevel() + " " + entry.getMessage().replaceAll("'", ""));
      }
    } else {
      log.add("This browser does not support getting console log.");
    }
    return log;
  }

  /**
   * Retrieves browser console log if possible.
   *
   * @return
   */
  public static JsonArrayBuilder getConsoleLog(WebDriver webDriver) {
    JsonArrayBuilder log = Json.createArrayBuilder();
    List<String> logEntries = analyzeLog(webDriver);
    for (String entry : logEntries) {
      log.add(entry);
    }
    return log;
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
      logger.error("Error parsing " + vmInstanceLongName + "\r\n" + TestUtils.getStackTrace(e), e);
    }
    return s;
  }


  /**
   *
   * @param s a string to be validated as a valid IPv4 address
   * @return true if it's a valid IPv4 address.
   */
  public static boolean isValidIPV4(final String s) {
    return IPV4_PATTERN.matcher(s).matches();
  }

  /**
   * Creates a Media recorder object on the video's source object, records for a given duration and
   * sends back the blob to a server to reconstruct the recorded video.
   *
   * @param webDriver browser running the test
   * @param videoIndex video index in page's video array
   * @param recordingDurationInMilisecond duration to record
   * @param callbackUrl server url to send video back to
   * @param details Json object containing details about the video file (name, type, ..)
   * @throws InterruptedException
   */
  public static boolean recordVideoStream(
      WebDriver webDriver,
      int videoIndex,
      int recordingDurationInMilisecond,
      JsonObject details,
      String callbackUrl)
      throws InterruptedException {
    String queryString = "";
    for (String key : details.keySet()) {
      queryString += "&" + key + "=" + details.getString(key);
    }
    String script =
        "var kite_videos = document.getElementsByTagName('video');"
            + "var kite_video = kite_videos["
            + videoIndex
            + "];"
            + "var title = document.title;"
            + "var kite_mediaRecorder = new MediaRecorder(kite_video.srcObject);"
            + "kite_mediaRecorder.ondataavailable ="
            + "function (event) {"
            + "   if (event.data && event.data.size > 0) {"
            + "      var httpRequest = new XMLHttpRequest();"
            + "      httpRequest.open('POST','"
            + callbackUrl
            + "?test=' + title + '"
            + queryString
            + "', true );"
            + "      httpRequest.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');"
            + "      httpRequest.send(event.data);"
            + "      kite_mediaRecorder.ondataavailable = null;"
            + "      var para = document.createElement('p');"
            + "      para.id = 'videoRecorded'+"
            + videoIndex
            + ";"
            + "      if(document.body != null){ "
            + "         setTimeout(function(){document.body.appendChild(para);}, "
            + recordingDurationInMilisecond
            + 500
            + "); "
            + "      }"
            + "   }"
            + "};"
            + "kite_mediaRecorder.start("
            + recordingDurationInMilisecond
            + ");"
            + "setTimeout(function(){kite_mediaRecorder.stop();}, "
            + recordingDurationInMilisecond
            + ");";
    try {
      ((JavascriptExecutor) webDriver).executeScript(script);
      // wait for the recording to finish before moving on
      WebDriverWait wait =
          new WebDriverWait(webDriver, (recordingDurationInMilisecond + 10000) / 1000);
      wait.until(ExpectedConditions.presenceOfElementLocated(By.id("videoRecorded" + videoIndex)));
      return true;
    } catch (Exception e) {
      logger.error("video recording (index: " + videoIndex + ") failed for " + details.toString());
      return false;
    }
  }

  /**
   * Handles alert popup if exists
   *
   * @param webDriver
   * @return String alert message
   */
  public static String alertHandling(WebDriver webDriver) {
    String alertMsg;
    try {
      Alert alert = webDriver.switchTo().alert();
      alertMsg = alert.getText();
      if (alertMsg != null) {
        alertMsg =
            ((RemoteWebDriver) webDriver).getCapabilities().getBrowserName()
                + " alert: "
                + alertMsg;
        alert.accept();
      }
    } catch (ClassCastException e) {
      alertMsg = " Cannot retrieve alert message due to alert.getText() class cast problem.";
      webDriver.switchTo().alert().accept();
    } catch (Exception e) {
      alertMsg = null;
    }
    return alertMsg;
  }

  /**
   * Open Chrome to the url given as parameter todo, this is on windows only, to do the same for
   * linux, mac...
   *
   * @param url the url of the page to open
   * @throws IOException
   */
  public static void openChromeToURL(String url) {
    if (url == null) {
      logger.warn("openChromeToURL(" + url + ")");
      return;
    }
    try {
      Thread.sleep(2000);
      if (System.getProperty("os.name").contains("Win")) {
        Runtime.getRuntime().exec(new String[] {"cmd", "/c", "start chrome " + url});
      } else {
          logger.warn("openChromeToURL() is only supported on Windows");
      }
    } catch (IOException | InterruptedException e) {
      logger.warn("Exception while opening up Google Chrome", e);
    }
  }
}
