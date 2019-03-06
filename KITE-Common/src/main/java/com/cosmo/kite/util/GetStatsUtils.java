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


import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.util.ArrayList;
import java.util.Map;

/**
 * Utility class with static methods to obtain and process getStats()
 */
public class GetStatsUtils {

    private static final int INTERVAL = 500;

    /**
     * Returns the test's getSDPOfferScript to retrieve
     * appController.call_.pcClient_.pc_.remoteDescription. If it doesn't exist then the method
     * returns 'unknown'.
     *
     * @return the getSDPOfferScript as string.
     */
    private static final String getSDPOfferScript() {
        return "var SDP;"
                + "try {SDP = appController.call_.pcClient_.pc_.remoteDescription;} catch (exception) {} "
                + "if (SDP) {return SDP;} else {return 'unknown';}";
    }

    /**
     * Returns the test's getSDPAnswerScript to retrieve
     * appController.call_.pcClient_.pc_.localDescription. If it doesn't exist then the method returns
     * 'unknown'.
     *
     * @return the getSDPAnswerScript as string.
     */
    private static final String getSDPAnswerScript() {
        return "var SDP;"
                + "try {SDP = appController.call_.pcClient_.pc_.localDescription;} catch (exception) {} "
                + "if (SDP) {return SDP;} else {return 'unknown';}";
    }


    /**
     * Stashes and retrieves stats from the peer connection object once.
     *
     * @return stat Object.
     * @throws InterruptedException
     */

    /**
     * Calls getStats from the peer connection object and stash them in a global variable.
     * JS function depends on the test type.
     *
     * @param statsType type of needed stats:
     *                  - "kite" for data.values which should be filtered after
     *                  - "local" for stats from the response of pc.getStats()
     *                  - "remote" for stats of all remote pc
     *                  - "jitsi" in the case of a Jitsi test (pc in Jitsi is not exposed as window.pc)
     *
     * @return the stashStatsScript as string.
     */
    private static final String stashStatsScript(String statsType) {
        String jsQuery = "";
        switch (statsType) {
            case "kite":
                jsQuery = "const getStatsValues = () =>"
                        + "  pc.getStats()"
                        + "    .then(data => {"
                        + "      return [...data.values()];"
                        + "    });"
                        + "const stashStats = async () => {"
                        + "  window.KITEStats = await getStatsValues();"
                        + "  return window.KITEStats;"
                        + "};"
                        + "stashStats();";
                break;
            case "local":
                jsQuery = "const getLocalStatsValues = () =>"
                        + "  pc.getStats(function (res) {"
                        + "            var items = [];"
                        + "            res.result().forEach(function (result) {"
                        + "                var item = {};"
                        + "                result.names().forEach(function (name) {"
                        + "                    item[name] = result.stat(name);"
                        + "                });"
                        + "                item.id = result.id;"
                        + "                item.type = result.type;"
                        + "                item.timestamp = result.timestamp.getTime().toString();"
                        + "                items.push(item);"
                        + "            });"
                        + "            window.LocalStats = items;"
                        + "        });"
                        + "const stashLocalStats = async () => {"
                        + "  await getLocalStatsValues();"
                        + "  return window.LocalStats;"
                        + "};"
                        + "stashLocalStats();";
                break;
            case "remote":
                jsQuery = "const getRemoteStatsValues = (i) =>"
                        + "  remotePc[i].getStats(function (res) {"
                        + "            var items = [];"
                        + "            res.result().forEach(function (result) {"
                        + "                var item = {};"
                        + "                result.names().forEach(function (name) {"
                        + "                    item[name] = result.stat(name);"
                        + "                });"
                        + "                item.id = result.id;"
                        + "                item.type = result.type;"
                        + "                item.timestamp = result.timestamp.getTime().toString();"
                        + "                items.push(item);"
                        + "            });"
                        + "            if (!window.RemoteStats) window.RemoteStats = [items]; "
                        + "            else window.RemoteStats.push(items);"
                        + "        });"
                        + "const stashRemoteStats = async () => {"
                        + "  window.RemoteStats = [];"
                        + "  for (i in remotePc) await getRemoteStatsValues(i);"
                        + "  return window.RemoteStats;"
                        + "};"
                        + "stashRemoteStats();";
                break;
            case "jitsi":
                jsQuery = "const getJitsiStatsValues = (p) =>"
                        + "  p.getStats(function (res) {"
                        + "            var items = [];"
                        + "            res.result().forEach(function (result) {"
                        + "                var item = {};"
                        + "                result.names().forEach(function (name) {"
                        + "                    item[name] = result.stat(name);"
                        + "                });"
                        + "                item.id = result.id;"
                        + "                item.type = result.type;"
                        + "                item.timestamp = result.timestamp.getTime().toString();"
                        + "                items.push(item);"
                        + "            });"
                        + "            if (!window.JitsiStats) window.JitsiStats = [items]; "
                        + "            else window.JitsiStats.push(items);"
                        + "        });"
                        + "const stashJitsiStats = async () => {"
                        + "  window.JitsiStats = [];"
                        + "  APP.conference._room.rtc.peerConnections.forEach(await function(p){getJitsiStatsValues(p);});"
                        + "  return window.JitsiStats;"
                        + "};"
                        + "stashJitsiStats();";
                break;
        }
        return jsQuery;
    }



    /**
     * Returns JavaScript to collect browser stats using getStats() API
     *
     * @return the JavaScript as string.
     */
    private static final String stashStatsScript() {
        return "function getAllStats() {\n"
          + "    return new Promise( (resolve, reject) => {\n"
          + "        try{\n"
          + "            window.remotePc.getStats().then((report) => {\n"
          + "                let statTypes = new Set();\n"
          + "                // type -> stat1, stat2, stat3\n"
          + "                let statTree = new Map();\n"
          + "                for (let stat of report.values()) {\n"
          + "                    const curType = stat.type;\n"
          + "                    const prvStat = statTree.get(curType);\n"
          + "                    if(prvStat) {\n"
          + "                        const _tmp = [...prvStat, stat];\n"
          + "                        statTree.set(curType, _tmp)\n"
          + "                    }else{\n"
          + "                        const _tmp = [stat];\n"
          + "                        statTree.set(curType, _tmp)\n"
          + "                    }\n"
          + "                }\n"
          + "                let retval = {};\n"
          + "                for (const [key, statsArr] of statTree) {\n"
          + "                    let keysArr = [];\n"
          + "                    for(const curStat of statsArr){\n"
          + "                        const keys = Object.keys(curStat);\n"
          + "                        keysArr = [ ...keysArr, ...keys ];\n"
          + "                    }\n"
          + "                    retval[key] = keysArr;\n"
          + "                }\n"
          + "                resolve(retval);\n"
          + "        });\n"
          + "        } catch(err) {\n"
          + "            reject(err);\n"
          + "        }\n"
          + "    });\n"
          + "}\n"
          + "function stashStats() {\n"
          + "    getAllStats().then( (data)=> {\n"
          + "        window.KITEStatsDiff = data;\n"
          + "    }, err => {\n"
          + "        console.log('error',err);\n"
          + "    });\n"
          + "}\n"
          + "stashStats()\n";
    }

    /** @return the JavaScript as string, depending on needed stats. */
    private static final String getStatsScript(String statsType) {
        String jsQuery = "";
        switch (statsType) {
            case "kite":
                jsQuery = "return window.KITEStats;";
                break;
            case "local":
                jsQuery = "return window.LocalStats;";
                break;
            case "remote":
                jsQuery = "return window.RemoteStats;";
                break;
            case "jitsi":
                jsQuery = "return window.JitsiStats;";
                break;
        }
        return jsQuery;
    }

    public static Object getStatsOnce(String statsType, WebDriver webDriver) throws InterruptedException {
        ((JavascriptExecutor) webDriver).executeScript(stashStatsScript(statsType));
        if (statsType.equals("kite"))
            Thread.sleep(INTERVAL);
        return ((JavascriptExecutor) webDriver).executeScript(getStatsScript(statsType));
    }

    /**
     * Build a stats Json object from a stats array
     *
     * @param statArray array of stats from js function
     * @return stats JsonObject.
     * @throws InterruptedException
     */
    public static JsonObject buildStatArray(Object statArray) {
        JsonObjectBuilder statObjectBuilder = Json.createObjectBuilder();
        for (Object map : (ArrayList) statArray) {
            if (map != null) {
                Map<Object, Object> statMap = (Map<Object, Object>) map;
                String id = (String) statMap.get("id");
                JsonObjectBuilder tmp = Json.createObjectBuilder();
                if (!statMap.isEmpty()) {
                    for (Object item : statMap.keySet())
                        tmp.add(item.toString(), statMap.get(item).toString());
                }
                statObjectBuilder.add(id, tmp.build());
            }
        }
        return statObjectBuilder.build();
    }
}
