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

package com.cosmo.kite.getstats;

import com.cosmo.kite.util.TestUtils;
import org.apache.log4j.Logger;

import javax.json.JsonObject;
import java.util.HashMap;
import java.util.Map;

public class SinglePCStatsArray extends BasePCStatsArray {

  private static final Logger logger = Logger.getLogger(SinglePCStatsArray.class.getName());

  public Map<String, String> getTotalAVBytes(String mediaType) {
    JsonObject lastStatObject = this.getLastObject();
    Map<String, String> totalVideosBytesMap = new HashMap<>();
    if (lastStatObject != null && lastStatObject.containsKey("localStats")) {
      JsonObject localStats = lastStatObject.getJsonObject("localStats");
      for (String itemKey : localStats.keySet()) {
        if (isRecvAV(localStats.getJsonObject(itemKey), mediaType)) {
          totalVideosBytesMap.put(
            itemKey, localStats.getJsonObject(itemKey).getString("bytesReceived"));
        }
        if (isSendAV(localStats.getJsonObject(itemKey), mediaType)) {
          totalVideosBytesMap.put(
            itemKey, localStats.getJsonObject(itemKey).getString("bytesSent"));
        }
      }
    }
    return totalVideosBytesMap;
  }

  /**
   * Get the sender's googRtt from the PC's
   * ssrc_4030852498_send object with mediaType = "video"
   *
   * @return the sender's video googRtt value (as a String)
   */
  public String getSentVideoRtt() {
    JsonObject lastStatObject = this.getLastObject();
    if (lastStatObject != null && lastStatObject.containsKey("localStats")) {
      JsonObject localStats = lastStatObject.getJsonObject("localStats");
      for (String itemKey : localStats.keySet()) {
        if (isSendAV(localStats.getJsonObject(itemKey), VIDEO)) {
          return localStats.getJsonObject(itemKey).getString("googRtt");
        }
      }
    }
    return "";
  }


  /**
   * Get the list of googFrameRateReceived/googFrameRateSent values as Map
   * Map<String, String>
   *
   * @return a Map object with the googFrameRateReceived
   */
  public Map<String, String> getFrameRate() {
    JsonObject lastStatObject = this.getLastObject();
    Map<String, String> resultMap = new HashMap<>();
    if (lastStatObject != null && lastStatObject.containsKey("localStats")) {
      JsonObject localStats = lastStatObject.getJsonObject("localStats");
      for (String itemKey : localStats.keySet()) {
        JsonObject jsonObject = localStats.getJsonObject(itemKey);
        if (localStats.containsKey(itemKey) && isAV(jsonObject, VIDEO)) {
          String frameRate = "-1";
          try {
            if (isRecvAV(jsonObject, VIDEO)) {
              frameRate = jsonObject.getString("googFrameRateReceived");
            }
            if (isSendAV(jsonObject, VIDEO)) {
              frameRate = jsonObject.getString("googFrameRateSent");
            }
          } catch (NullPointerException e) {
            logger.error("Error in getFrameRate(): "
              + " googFrameRateReceived/googFrameRateSent not present in " + itemKey);
          }
          if (!resultMap.containsKey(itemKey) || "-1".equals(resultMap.get(itemKey))) {
            resultMap.put(itemKey, frameRate);
          }
        }
      }
    }
    return resultMap;
  }

  /**
   * Get the list of googJitterReceived values as Map
   * Map<String, String>
   *
   * @return a Map object with the googJitterReceived
   */
  public Map<String, String> getAudiosJitter() {
    JsonObject lastStatObject = this.getLastObject();
    Map<String, String> jitterMap = new HashMap<>();
    if (lastStatObject != null && lastStatObject.containsKey("localStats")) {
      JsonObject localStats = lastStatObject.getJsonObject("localStats");
      for (String itemKey : localStats.keySet()) {
        JsonObject jsonObject = localStats.getJsonObject(itemKey);
        if (localStats.containsKey(itemKey) && isAV(jsonObject, AUDIO)) {
          String jitter = "-1";
          try {
            if (isRecvAV(jsonObject, AUDIO)) {
              jitter = jsonObject.getString("googJitterReceived");
            }
            if (isSendAV(jsonObject, AUDIO)) {
              jitter = "NA";
            }
          } catch (NullPointerException e) {
            logger.error("Error in getAudiosJitter(): "
              + " googJitterReceived not present in " + itemKey);
          }
          if (!jitterMap.containsKey(itemKey) || "-1".equals(jitterMap.get(itemKey))) {
            jitterMap.put(itemKey, jitter);
          }
        }
      }
    }
    return jitterMap;
  }

  public Map<String, String> getAVPacketLoss(String mediaType) {
    JsonObject lastStatObject = this.getLastObject();
    Map<String, String> packetLossMap = new HashMap<>();
    if (lastStatObject != null && lastStatObject.containsKey("localStats")) {
      JsonObject localStats = lastStatObject.getJsonObject("localStats");
      for (String itemKey : localStats.keySet()) {
        JsonObject jsonObject = localStats.getJsonObject(itemKey);
        boolean sent = isSendAV(jsonObject, mediaType);
        if (localStats.containsKey(itemKey)
          && (isRecvAV(jsonObject, mediaType)
          || sent)) {
          double loss = -1.0;
          try {
            double packetsLost = (double)Long.parseLong(jsonObject.getString("packetsLost"));
            double packetsCount = (double)Long.parseLong(jsonObject.getString(
              sent ? "packetsSent" : "packetsReceived"));
            loss = (100 * packetsLost) / (packetsCount + packetsLost);
          } catch (NullPointerException e) {
            logger.warn("getAVPacketLoss(" + mediaType + ") " + (sent ? "sent" : "recv")
              + " packetsLost not present in " + itemKey);
          }
          packetLossMap.put(itemKey, df.format(loss));
        }
      }
    }
    return packetLossMap;
  }

  public Map<String, String> getAVAvgBitrate(String mediaType) {
    Map<String, String> videosAvgBitrateMap = new HashMap<>();
    if (this.size() <= 1) {
      return videosAvgBitrateMap;
    }
    JsonObject lastStatObject = this.getLastObject();
    if (lastStatObject != null && lastStatObject.containsKey("localStats")) {
      JsonObject localStats = lastStatObject.getJsonObject("localStats");
      for (String itemKey : localStats.keySet()) {
        try {
          JsonObject jsonObj = localStats.getJsonObject(itemKey);
          boolean sentVideo = isSendAV(jsonObj, mediaType);
          if (isRecvAV(jsonObj, mediaType) || sentVideo) {
            JsonObject lastItem = jsonObj;
            JsonObject firstItem = this.getFirstAppearanceOf(itemKey, mediaType);
            long averageBitrate = computeAvgBitrate(firstItem, lastItem, sentVideo);
            String value = (averageBitrate > -1) ? "" + averageBitrate : "";
            videosAvgBitrateMap.put(itemKey, value);
          }
        } catch (Exception e) {
          logger.error(TestUtils.getStackTrace(e));
        }
      }
    }
    return videosAvgBitrateMap;
  }

  private JsonObject getFirstAppearanceOf(String itemKey, String mediaType) {
    for (int i = 0; i < this.size(); i++) {
      if (this.get(i).containsKey("localStats")) {
        JsonObject localStats = this.get(i).getJsonObject("localStats");
        if (localStats != null && localStats.containsKey(itemKey)
          && (isRecvAV(localStats.getJsonObject(itemKey), mediaType)
          || isSendAV(localStats.getJsonObject(itemKey), mediaType))) {
          return localStats.getJsonObject(itemKey);
        }
      }
    }
    return null;
  }
}
