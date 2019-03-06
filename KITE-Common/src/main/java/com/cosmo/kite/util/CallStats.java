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

import javax.json.*;
import java.util.ArrayList;
import java.util.List;

public class CallStats {

  private MediaStat audio;
  private MediaStat video;

  public CallStats() {
    this.audio = new MediaStat("audio");
    this.video = new MediaStat("video");
    // Maybe we will want something more here.

  }

  public CallStats(JsonArray statJsonArray, int duration) throws Exception {
    if (statJsonArray.size() < 2) {
      throw new Exception("Not enough stats were provided.");
    }
    this.audio = new MediaStat("audio", statJsonArray, duration);
    this.video = new MediaStat("video", statJsonArray, duration);
    // Maybe we will want something more here.

  }

  public JsonObjectBuilder getJsonObjectBuilder(){
    return Json.createObjectBuilder().add("video", video.getJsonObjectBuilder())
        .add("audio", audio.getJsonObjectBuilder());
  }

  private class MediaStat{
    private String type;
    private String jitter = "0";
    private double bytesPerSecond = 0;
    private double packetsPerSecond = 0;
    private int packetsLost = 0;
    private int frameHeight = 0;
    private int frameWidth = 0;
    private double audioLevel = 0;

    public MediaStat(String type){
      this.type = type;
    }

    public MediaStat(String type, JsonArray statJsonArray, int duration) {
      this.type = type;
      if (statJsonArray != null) {
        List<JsonObject> RTCInbounds = new ArrayList<>();
        JsonObject RTCTrack = null;
        // For now assuming that there are only 2 objects in the array.

        // getting inbound rtp streams
        for (JsonValue stats : statJsonArray) {
          JsonObject inboundStreams = ((JsonObject) stats).getJsonObject("inbound-rtp");
          for (String inboundStreamName : inboundStreams.keySet()) {
            JsonObject inboundStream = inboundStreams.getJsonObject(inboundStreamName);
            if (inboundStream.getString("mediaType", "NA").equalsIgnoreCase(this.type)) {
              RTCInbounds.add(inboundStream);
            }
          }
          // getting only track from the the second stat object
          JsonObject tracks = ((JsonObject) stats).getJsonObject("track");
          for (String trackName : tracks.keySet()) {
            JsonObject track = tracks.getJsonObject(trackName);
            if (this.type.equalsIgnoreCase("audio")) {
              if (!track.getString("audioLevel").equalsIgnoreCase("NA")) {
                RTCTrack = track;
              }
            } else { // video
              if (track.getString("audioLevel").equalsIgnoreCase("NA")) {
                RTCTrack = track;
              }
            }
          }
        }

        for (JsonObject RTCinbound : RTCInbounds) {
          this.jitter = RTCinbound.getString("jitter");
          this.packetsLost = Integer.parseInt(RTCinbound.getString("packetsLost"));

          if (this.bytesPerSecond
              == 0) { // first RTCInbound values, assuming that there's always bytes received
            this.bytesPerSecond = Double.parseDouble(RTCinbound.getString("bytesReceived"));
            this.packetsPerSecond = Double.parseDouble(RTCinbound.getString("packetsReceived"));
          } else {
            this.bytesPerSecond =
                (Double.parseDouble(RTCinbound.getString("bytesReceived")) - this.bytesPerSecond)
                    / duration;
            this.packetsPerSecond =
                (Double.parseDouble(RTCinbound.getString("packetsReceived"))
                        - this.packetsPerSecond)
                    / duration;
          }
        }

        if (this.type.equalsIgnoreCase("audio")) {
          this.audioLevel = Double.parseDouble(RTCTrack.getString("audioLevel"));
        } else {
          this.frameHeight = Integer.parseInt(RTCTrack.getString("frameHeight"));
          this.frameWidth = Integer.parseInt(RTCTrack.getString("frameWidth"));
        }
      }
    }

    public JsonObjectBuilder getJsonObjectBuilder(){
      JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
      jsonObjectBuilder.add("jitter", this.jitter)
          .add("bytesPerSecond",bytesPerSecond)
          .add("packetsPerSecond",packetsPerSecond)
          .add("packetsLost",packetsLost);
      if (this.type.equalsIgnoreCase("audio")){
        jsonObjectBuilder.add("audioLevel", this.audioLevel);
      } else {
        jsonObjectBuilder.add("frameHeight", this.frameHeight).add("frameWidth", this.frameWidth);
      }
      return jsonObjectBuilder;
    }

  }
}
