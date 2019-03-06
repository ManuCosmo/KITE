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

package com.cosmo.kite.instrumentation;

import javax.json.JsonArray;
import javax.json.JsonObject;


/**
 * Parent class for Client and Gateway containing common methods and variables.
 */
public class InstanceBase {


  protected final String username;
  protected final String keyFilePath;
  protected final String[] commands;

  protected final String pipe = " || true && ";

  protected InstanceBase(JsonObject jsonObject) throws Exception {
    username = jsonObject.getString("username");
    keyFilePath = System.getProperty("user.home") + jsonObject.getString("keyFilePath");
    JsonArray jsArray = jsonObject.getJsonArray("commands");
    if (jsArray == null || jsArray.size() < 0) {
      throw new Exception("Error in json config client, commands are invalid.");
    }
    commands = new String[jsArray.size()];
    for (int i = 0; i < jsArray.size(); i++) {
      commands[i] = jsArray.getString(i);
    }
  }




  /**
   * Gets the command line to be executed by SSH
   * @return the commandLine
   */
  public String getCommandLine() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < commands.length; i++) {
      builder.append(commands[i]);
      if (i < commands.length - 1) {
        builder.append(pipe);
      }
    }
    return builder.toString();
  }




  /**
   * For debugging.
   * @return a String representation of this object
   */
  @Override
  public String toString() {
    String s = "\r\n";
    s += "username: " + this.username + "\r\n";
    s += "keyFilePath: " + this.keyFilePath + "\r\n";
    s += "commands: " + "\r\n";
    for (String g : commands) {
      s += " " + g +  "\r\n";
    }
    return s;
  }

}
