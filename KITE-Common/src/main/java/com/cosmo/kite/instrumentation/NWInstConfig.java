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

import com.cosmo.kite.util.TestUtils;
import org.checkerframework.checker.units.qual.C;

import javax.json.JsonArray;
import javax.json.JsonObject;
import java.util.Vector;


/**
 * Network instrumentation config helper class.
 *
 *  Example:
 *
 *    "instrumentation": {
 *      "phase": "rampup",
 *      "allClients": false,
 *      "serverIPs": ["18.215.197.60", "18.213.125.68"],
 *      "gateway": {
 *        "publicIP": "174.129.187.140",
 *        "eth0IP": "172.31.12.174",
 *        "eth1IP": "172.31.4.13",
 *        "username": "ec2-user",
 *        "keyFilePath": "/.ssh/CosmoKeyUS.pem",
 *        "cleanUp": true,
 *        "commands": [
 *          "sudo tc qdisc add dev ens3 root netem loss 5%"
 *        ]
 *      },
 *      "clients": [
 *        {
 *          "id": 1,
 *          "username": "ubuntu",
 *          "keyFilePath": "/.ssh/CosmoKeyUS.pem",
 *          "commands": [
 *            "sudo tc qdisc add dev ens3 handle ffff: ingress || true && sudo tc filter add dev ens3 parent ffff: protocol ip prio 50 u32 match ip src 18.215.197.60 police rate 640kbit burst 10k drop flowid :1 || true && sudo tc filter add dev ens3 parent ffff: protocol ip prio 50 u32 match ip src 18.213.125.68 police rate 640kbit burst 10k drop flowid :1"
 *         ]
 *       }
 *      ],
 *
 * Default value: phase="rampup", allClients=false.
 *   gateway is optional (NW instrumentation can be done on the clients only).
 *   clients cannot be empty.
 *   all fields in client and gateway are mandatory.
 *   serverIPs is mandatory when a gateway is specified.
 *
 *
 * phase:  rampup, loadreached, both (case insensitive)
 *
 */
public class NWInstConfig {

  // NW instrumentation stuffs
  private String phase = "rampup"; //rampup | loadreached | both
  private boolean allClients = false;
  private final Vector<String> serverIPs = new Vector<>();

  private final Vector<Client> clients = new Vector<>();
  private final Gateway gateway;
  private final boolean gatewaySetup;
  private boolean cleanUp = true;


  public NWInstConfig(JsonObject jsonPayload) throws Exception {
    phase = jsonPayload.getString("phase", phase);
    allClients = jsonPayload.getBoolean("allClients", allClients);
    cleanUp = jsonPayload.getBoolean("cleanUp", cleanUp);

    JsonObject jsonObject = jsonPayload.getJsonObject("gateway");
    gateway = jsonObject != null ? new Gateway(jsonObject) : null;

    JsonArray jsonArray = jsonPayload.getJsonArray("clients");
    for (int i = 0; i < jsonArray.size(); i++) {
      clients.add(new Client(jsonArray.getJsonObject(i)));
    }
    jsonArray = jsonPayload.getJsonArray("serverIPs");
    if (jsonArray != null) {
      for (int i = 0; i < jsonArray.size(); i++) {
        serverIPs.add(jsonArray.getString(i));
      }
    }
    gatewaySetup = serverIPs.size() > 0 && gateway != null;
    if (gateway != null && serverIPs.size() < 1) {
      throw new Exception("For a gateway setup, serverIPs is a mandatory parameter.");
    }
  }

    /**
     * Checks if the tester ID is included in the list of clients to be tested.
     * In the json
     *
     *
     * @param id the id of the client
     * @return true if this tester ID is included in the config file, or if the config is set to [0]
     */
  public boolean contains(int id) {
    if (this.allClients) {
      return true;
    }
    for (Client c : clients) {
      if (id == c.getId()) {
        return true;
      }
    }
    return false;
  }


  /**
   * Gets the client corresponding to the id.
   *
   * @param id
   * @return the client corresponding to this id
   */
  public Client getClient(int id) {
    for (Client c : clients) {
      if (id == c.getId()) {
        return c;
      }
    }
    return null;
  }


  /**
   * Checks if the tester ID is included in the list of clients to be tested and if the
   * NW instrumentation should be done during ramp up.
   *
   * @param id the id of the client
   * @return true if the NW instrumentation is to be executed during the ramp up phase
   * and this id is included in the list provided in the config file
   */
  public boolean rampup(int id) {
    return "both".equals(this.phase.toLowerCase()) || "rampup".equals(this.phase.toLowerCase())
      && this.contains(id);
  }


  /**
   * Checks if the tester ID is included in the list of clients to be tested and if the
   * NW instrumentation should be done during load reached.
   *
   * @param id the id of the client
   * @return true if the NW instrumentation is to be executed during the load reached phase
   * and this id is included in the list provided in the config file
   */
  public boolean loadreached(int id) {
    return "both".equals(this.phase.toLowerCase()) || "loadreached".equals(this.phase.toLowerCase())
      && this.contains(id);
  }

  /**
   * Runs the cleanUp commands on the gateway.
   */
  public void cleanUp() {
    if (cleanUp) {
      gateway.cleanCommand();
    }
  }

  /**
   * Runs the NW instrumentation commands on the gateway.
   */
  public String runCommands(int id, String nodeIP) {
    if (gatewaySetup) {
      return gateway.runCommands();
    } else {
      return getClient(id).runCommands(nodeIP);
    }
  }

  /**
   * For debugging.
   * @return a String representation of this object
   */
  @Override
  public String toString() {
    String s = "\r\n";
    s += "Phase: " + this.phase + "\r\n";
    s += "allClients: " + this.allClients + "\r\n";
    s += "gateway: " + "\r\n";
    s += " " + gateway.toString() +  "\r\n";

    s += "clients: ";
    for (Client c : clients) {
      s += " " + c.toString() +  "\r\n";
    }
    return s;
  }


  /**
   * Adding a static route to the client private IP on all the gateways.
   *
   * @param ip
   */
  public void addStaticRouteOnGateway(String ip) {
    gateway.addStaticRoute(ip);
  }


  /**
   * Adds the static routes on the client node VM to route the traffic to
   * the server IPs via the gateway.
   *
   * @param id
   * @param nodeIp
   */
  public void addStaticRouteOnClient(int id, String nodeIp) {
    getClient(id).addStaticRoutes(nodeIp, serverIPs, gateway.getEth1IP());
  }



  /**
   * @return true if this is a gateway setup, false otherwise.
   */
  public boolean gatewaySetup() {
    return gatewaySetup;
  }



}
