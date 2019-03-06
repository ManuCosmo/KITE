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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Map;

/** RTCPeerConnectionStats, with attributes dataChannelsOpened, dataChannelsClosed */
public abstract class BasePCStatsArray extends ArrayList<JsonObject> {

  private static final Logger logger = Logger.getLogger(BasePCStatsArray.class.getName());

  protected static final DecimalFormat df = new DecimalFormat("#0.0000");
  public static final String VIDEO = "video";
  public static final String AUDIO = "audio";

  public abstract Map<String, String> getTotalAVBytes(String mediaType);

  public abstract Map<String, String> getAVAvgBitrate(String mediaType);


  /**
   * Get the sender's googRtt from the PC's
   * ssrc_4030852498_send object with mediaType = "video"
   *
   * @return the sender's video googRtt value (as a String)
   */
  public abstract String getSentVideoRtt();

  public abstract Map<String, String> getAVPacketLoss(String mediaType);

  public abstract Map<String, String> getAudiosJitter();
  public abstract Map<String, String> getFrameRate();




  protected boolean isRecvAV(JsonObject statObject, String mediaType) {
    return (statObject.getString("id").contains("ssrc_")
            && statObject.getString("id").contains("_recv")
            && statObject.getString("mediaType").equals(mediaType)
            && !statObject.getString("googTrackId").contains("fake-unified-plan")
            && !statObject.getString("googCodecName").equals(""));
  }

  protected boolean isSendAV(JsonObject statObject, String mediaType) {
    return (statObject.getString("id").contains("ssrc_")
            && statObject.getString("id").contains("_send")
            && statObject.getString("mediaType").equals(mediaType)
            && !statObject.getString("googTrackId").contains("fake-unified-plan")
            && !statObject.getString("googCodecName").equals(""));
  }

  protected boolean isAV(JsonObject statObject, String mediaType) {
    return isSendAV(statObject, mediaType) || isRecvAV(statObject, mediaType);
  }

  protected long computeAvgBitrate(
          JsonObject firstItem, JsonObject lastItem, boolean sentVideo) {
    long bitrate = -1;
    try {
      long startTS = Long.parseLong(firstItem.getString("timestamp"));
      long endTS = Long.parseLong(lastItem.getString("timestamp"));
      long duration = endTS - startTS;
      if (duration > 0) {
        long startBytes =
            Long.parseLong(firstItem.getString(sentVideo ? "bytesSent" : "bytesReceived"));
        long endBytes =
            Long.parseLong(lastItem.getString(sentVideo ? "bytesSent" : "bytesReceived"));
        bitrate = ((endBytes - startBytes) * 8000) / duration;
      }
    } catch (Exception e) {
      logger.error("Error in computeAvgBitrate(): " + TestUtils.getStackTrace(e));
//      logger.error("Error in computeAvgBitrate(): " + e.getLocalizedMessage());
    }
    return bitrate;
  }

  protected JsonObject getLastObject() {
    return size() > 1 ? get(size() - 1) : null;
  }
}
