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

import org.apache.log4j.Logger;

public class MeetingStatus {

  private final Logger logger = Logger.getLogger(this.getClass().getName());

  private final String meetingID;
  private boolean meetingEnded = false;
  private long lastCheck = System.currentTimeMillis();


  /**
   * Constructor
   *
   * @param id the meeting ID
   */
  public MeetingStatus(String id) {
    this.meetingID = id;
  }

  /**
   * The meeting will end with a probability of 1/chanceOfMeetingEnded.
   * This check is done every checkStatusPeriod seconds.
   *
   * @param chanceOfMeetingEnded denomitator of the probability. Probability of meeting ended
   *                             = 1 / chanceOfMeetingEnded
   * @param checkStatusPeriod how often to check if the meeting should be ended (in seconds)
   * @return
   */
  public synchronized boolean meetingEnded(int chanceOfMeetingEnded, int checkStatusPeriod) {
    long now = System.currentTimeMillis();
    if (now - lastCheck < checkStatusPeriod * 1000) {
      return meetingEnded;
    }
    lastCheck = now;
    int prob = (int)(Math.random() * chanceOfMeetingEnded) + 1;
    meetingEnded = (prob == chanceOfMeetingEnded);
    if (meetingEnded) {
      logger.info("The meeting " + meetingID + " has ended, all clients will refresh.");
    }
    return meetingEnded;
  }

}
