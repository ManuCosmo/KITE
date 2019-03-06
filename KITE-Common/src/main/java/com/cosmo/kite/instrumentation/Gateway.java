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

import com.cosmo.kite.util.SSHManager;
import com.cosmo.kite.util.TestUtils;
import org.apache.log4j.Logger;
import javax.json.JsonObject;

public class Gateway extends InstanceBase {

  private static final Logger logger = Logger.getLogger(Gateway.class.getName());


  private final String publicIP;
  private final String eth0IP;
  private final String eth1IP;


  /**
   * Constructor builds the Gateway object from a json:
   *
   *  "gateways": [
   *    {
   *      "publicIP": "174.129.187.140",
   *      "eth0IP": "172.31.12.174",
   *      "eth1IP": "172.31.4.13",
   *      "username": "ec2-user",
   *      "keyFilePath": "/.ssh/CosmoKeyUS.pem",
   *      "commands": [
   *        "sudo tc qdisc add dev ens3 root netem loss 5%"
   *      ]
   *    }
   *  ]
   *
   * @param jsonObject
   */
  public Gateway(JsonObject jsonObject) throws Exception {
    super(jsonObject);
    publicIP = jsonObject.getString("publicIP");
    eth0IP = jsonObject.getString("eth0IP");
    eth1IP = jsonObject.getString("eth1IP");
  }

  /**
   * For debugging.
   * @return a String representation of this object
   */
  @Override
  public String toString() {
    String s = "\r\n";
    s += "publicIP: " + this.publicIP + "\r\n";
    s += "eth0IP: " + this.eth0IP + "\r\n";
    s += "eth1IP: " + this.eth1IP + "\r\n";
    s += super.toString();
    return s;
  }

  /**
   * Add a static route on the gateway on eth1 to the ip given as param
   *
   * @param ip the ip to add the static route to.
   */
  public void addStaticRoute(String ip) {
    String command = "sudo route add -host " + ip + " eth1";
    try {
      SSHManager sshManager = new SSHManager(this.keyFilePath, this.username,
          this.publicIP, command);
      if (sshManager.call().commandSuccessful()) {
        Thread.sleep(500);
        logger.info("static route to " + ip + " via eth1 added.");
      } else {
        logger.error("Failed to add static route to " + ip + " via eth1.");
      }
    } catch (Exception e) {
      logger.error("Error in Gateway.addStaticRouteOnGateway. Command:\r\n"
        + command + "\r\n"
        + TestUtils.getStackTrace(e));
    }
  }



  /**
   *  Runs the tc commands on the gateway.
   *  @return the command that has been run.
   */
  public String runCommands() {
    String command = getCommandLine();
    try {
      SSHManager sshManager = new SSHManager(this.keyFilePath, this.username,
        this.publicIP, command);
      if (sshManager.call().commandSuccessful()) {
        Thread.sleep(1500);
        logger.info("runCommands() : \r\n" + command);
        command += "  SUCCESS (on Gateway)";
      } else {
        logger.error("Failed runCommands() : \r\n" + command);
        command += "  FAILURE (on Gateway)";
      }
    } catch (Exception e) {
      logger.error(
        "runCommand(). Command:\r\n"
          + command
          + "\r\n"
          + TestUtils.getStackTrace(e));
      command = "Error " + e.getMessage();
    }
    return command;
  }

  /**
   *  Runs the tc cleanup commands on the GW network interfaces eth0 and/or eth1.
   *  The function will look in the commands for eth0 or eth1 and clean accordingly.
   *    sudo tc qdisc del dev eth0 root
   *    sudo tc qdisc del dev eth1 root
   */
  public void cleanCommand() {
    String command = "";
    if (getCommandLine().contains("eth0")) {
      command += "sudo tc qdisc del dev eth0 root";
    }
    if (getCommandLine().contains("eth1")) {
      if (command.length() > 0) {
        command += pipe;
      }
      command += "sudo tc qdisc del dev eth1 root";
    }
    if (command.length() > 0) {
      try {
        SSHManager sshManager =
            new SSHManager(this.keyFilePath, this.username, this.publicIP, command);
        logger.info("cleanCommand() : \r\n" + command);
        if (sshManager.call().commandSuccessful()) {
          Thread.sleep(500);
          logger.info("cleanUp successful");
        } else {
          logger.info("cleanUp failed");
        }
      } catch (Exception e) {
        logger.error(
            "cleanCommand(). Command:\r\n"
                + command
                + "\r\n"
                + TestUtils.getStackTrace(e));
      }
    } else {
      logger.warn("cleanCommand() : nothing to cleanup. Command doesn't contain et0 or eth1: \r\n" + getCommandLine());
    }
  }

  /**
   * Gets the eth1IP
   *
   * @return the eth1IP
   */
  public String getEth1IP() {
    return this.eth1IP;
  }
}
