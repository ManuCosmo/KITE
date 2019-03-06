/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.webrtc.kite;

import org.openqa.selenium.WebDriver;

import javax.json.JsonValue;
import java.util.List;

/**
 * Parent class for a test case.
 * <p>
 * It provides a list of WebDriver objects to the child classes to execute the test algorithm.
 * <p>
 * It also contains the details of the node to which the test case is assigned.
 */
public abstract class KiteTest {

  private JsonValue payload;

  private List<WebDriver> webDriverList;

  private String commandName;


  protected JsonValue getPayload() {
    return this.payload;
  }

  /**
   * Method to set a payload.
   *
   * @param payload JsonValue
   */
  public void setPayload(JsonValue payload) {
    this.payload = payload;
  }

  protected List<WebDriver> getWebDriverList() {
    return this.webDriverList;
  }




  /**
   * Method to set a web driver list.
   *
   * @param webDriverList Web Driver List
   */
  public void setWebDriverList(List<WebDriver> webDriverList) {
    this.webDriverList = webDriverList;
  }

  /**
   * Sets the command name for the NW instrumentation.
   *
   * @param commandName name of the command
   */
  public void setCommandName(String commandName) {
    this.commandName = commandName;
  }

  /**
   * Gets the command name for the NW instrumentation.
   *
   * @return the name of the command
   */
  public String getCommandName() {
    return this.commandName;
  }

  /**
   * Tests against List<WebDriver>
   *
   * @return Any object with a toString() implementation.
   * @throws Exception if an Exception occurs while method execution.
   */
  public abstract Object testScript() throws Exception;

}
