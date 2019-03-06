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

public class MultiPCStatsArray extends BasePCStatsArray {

  private static final Logger logger = Logger.getLogger(MultiPCStatsArray.class.getName());



  /**
   * Get the list of googFrameRateReceived values as Map
   * Map<String, String>
   *
   * @return a Map object with the googFrameRateReceived
   */
  public Map<String, String> getFrameRate() {
    JsonObject lastStatObject = this.getLastObject();
    Map<String, String> resultMap = new HashMap<>();
    if (lastStatObject != null) {
      for (String statObjectKey : lastStatObject.keySet()) {
        JsonObject statsObject = lastStatObject.getJsonObject(statObjectKey);
        for (String itemKey : statsObject.keySet()) {
          if (statsObject.containsKey(itemKey)) {
            String frameRate = "";
            try {
              if (isRecvAV(statsObject.getJsonObject(itemKey), VIDEO)) {
                frameRate = statsObject.getJsonObject(itemKey).getString("googFrameRateReceived");
              }
              if (isSendAV(statsObject.getJsonObject(itemKey), VIDEO)) {
                frameRate = statsObject.getJsonObject(itemKey).getString("googFrameRateSent");
              }
            } catch (NullPointerException e) {
              logger.error("Error in getFrameRate: \r\n" + TestUtils.getStackTrace(e));
            }
            resultMap.put(itemKey, frameRate);
          }
        }
      }
    }
    return resultMap;
  }


  public Map<String, String> getAudiosJitter() {
    JsonObject lastStatObject = this.getLastObject();
    Map<String, String> jitterMap = new HashMap<>();
    if (lastStatObject != null) {
      for (String statObjectKey : lastStatObject.keySet()) {
        JsonObject statsObject = lastStatObject.getJsonObject(statObjectKey);
        for (String itemKey : statsObject.keySet()) {
          boolean sent = isSendAV(statsObject.getJsonObject(itemKey), AUDIO);
          if (statsObject.containsKey(itemKey)
              && (isRecvAV(statsObject.getJsonObject(itemKey), AUDIO) || sent)) {
            String jitter = statsObject.getJsonObject(itemKey).getString("googJitterReceived");
            jitterMap.put(itemKey, "" + jitter);
          }
        }
      }
    }
    return jitterMap;
  }

  public Map<String, String> getAVPacketLoss(String mediaType) {
    JsonObject lastStatObject = this.getLastObject();
    Map<String, String> packetLoss = new HashMap<>();
    if (lastStatObject != null) {
      for (String statObjectKey : lastStatObject.keySet()) {
        JsonObject statsObject = lastStatObject.getJsonObject(statObjectKey);
        for (String itemKey : statsObject.keySet()) {
          boolean sent = isSendAV(statsObject.getJsonObject(itemKey), mediaType);
          if (statsObject.containsKey(itemKey)
              && (isRecvAV(statsObject.getJsonObject(itemKey), mediaType) || sent)) {
            long packetsLost =
                Long.parseLong(statsObject.getJsonObject(itemKey).getString("packetsLost"));
            long packetsCount =
                Long.parseLong(
                    statsObject
                        .getJsonObject(itemKey)
                        .getString(sent ? "packetsSent" : "packetsReceived"));
            double loss = (100 * packetsLost) / (packetsCount + packetsLost);
            packetLoss.put(itemKey, df.format(loss / 100));
          }
        }
      }
    }
    return packetLoss;
  }

  public Map<String, String> getTotalAVBytes(String mediaType) {
    JsonObject lastStatObject = this.getLastObject();
    Map<String, String> totalVideosBytes = new HashMap<>();
    if (lastStatObject != null) {
      for (String statObjectKey : lastStatObject.keySet()) {
        JsonObject statsObject = lastStatObject.getJsonObject(statObjectKey);
        for (String itemKey : statsObject.keySet()) {
          if (isRecvAV(statsObject.getJsonObject(itemKey), mediaType)) {
            totalVideosBytes.put(
                itemKey, statsObject.getJsonObject(itemKey).getString("bytesReceived"));
          }
          if (isSendAV(statsObject.getJsonObject(itemKey), mediaType)) {
            totalVideosBytes.put(
                itemKey, statsObject.getJsonObject(itemKey).getString("bytesSent"));
          }
        }
      }
    }
    return totalVideosBytes;
  }

  public Map<String, String> getAVAvgBitrate(String mediaType) {
    Map<String, String> videosAvgBitrate = new HashMap<>();
    if (this.size() <= 1) {
      return videosAvgBitrate;
    }
    JsonObject lastStatObject = this.getLastObject();

    if (lastStatObject != null) {
      for (String statObjectKey : lastStatObject.keySet()) {
        JsonObject statsObject = lastStatObject.getJsonObject(statObjectKey);
        for (String itemKey : statsObject.keySet()) {
          try {
            JsonObject jsonObj = statsObject.getJsonObject(itemKey);
            boolean sentVideo = isSendAV(jsonObj, mediaType);
            if (isRecvAV(jsonObj, mediaType) || sentVideo) {
              JsonObject lastItem = jsonObj;
              JsonObject firstItem = this.getFirstAppearanceOf(itemKey);
              long averageBitrate = computeAvgBitrate(firstItem, lastItem, sentVideo);
              String value = (averageBitrate > -1) ? "" + averageBitrate : "";
              videosAvgBitrate.put(itemKey, value);
            }
          } catch (Exception e) {
            logger.error(TestUtils.getStackTrace(e));
          }
        }
      }
    }
    return videosAvgBitrate;
  }


  /**
   * Get the sender's googRtt from the last PC
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

  private JsonObject getFirstAppearanceOf(String itemKey) {
    for (int i = 0; i < this.size(); i++) {
      for (String statObjectKey : this.get(i).keySet()) {
        JsonObject statsObject = this.get(i).getJsonObject(statObjectKey);
        if (statsObject.containsKey(itemKey)){
          return statsObject.getJsonObject(itemKey);
        }
      }
    }
    return null;
  }
}
