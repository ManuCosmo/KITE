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

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.*;

// XXX todo:  move to KITE engine, then extend this for individual tests

/**
 * TestStats is a Singleton class that collects and save KITE load testing stats into a CSV file.
 */
public class TestStats {

  private static HashMap<String, TestStats> instance = new HashMap<String, TestStats>();
  private static final Logger logger = Logger.getLogger(TestStats.class.getName());

  private static Map<String, String> keyValMap = new LinkedHashMap<String, String>();

  private final String filename;
  private FileOutputStream fout = null;
  private PrintWriter pw = null;

  private int testID = 1; // start count at 1
  private boolean initialized = false;

  private TestStats(String prefix) {
    filename = prefix + "report_" + new SimpleDateFormat("yyyyMMdd_hhmmss").format(new Date()) + ".csv";
  }

  /**
   * @return and instance of TestStats
   */
  public static TestStats getInstance(String prefix) {
    try {
      if (!instance.containsKey(prefix)) {
        instance.put(prefix,new TestStats(prefix));
      }
    } catch (Exception e) {
      logger.error("\r\n" + TestUtils.getStackTrace(e));
    }
    return instance.get(prefix);
  }

  /** @return a new test ID */
  public int newTestID() {
    return this.testID++;
  }

  /**
   * Print the test statistic line.
   *
   * @param o Object object containing the test results. Either a JsonObject or any Object with a
   *     toString() method
   * @param path the file path where to save the file.
   */
  public synchronized void println(Object o, String path) {
    try {
      if (!initialized) {
        File dir = new File(path);
        if (!dir.isDirectory()) {
          dir.mkdirs();
        }
        fout = new FileOutputStream(path + filename);
        pw = new PrintWriter(fout, true);
      }
      if (o instanceof JsonObject) {
        //logger.info("TestStats.println(JsonObject) " + o.toString());
        JsonObject jsonObject = (JsonObject) o;
        Map<String, String> map = TestStats.jsonToHashMap(jsonObject);
        if (!initialized) {
          pw.println(keysLine(map));
        }
        pw.println(valuesLine(map));
      } else {
        //logger.info("TestStats.println(String) " + o.toString());
        pw.println(o.toString());
      }
    } catch (Exception e) {
      logger.error("\r\n" + TestUtils.getStackTrace(e));
    }
    initialized = true;
  }

  /**
   * Saves a JSON object into a file, with line breaks and indents.
   *
   * @param testName the name of the test, which will be inlcuded in the file name
   * @param jsonStr the json object as a String.
   * @param path the file path where to save the file.
   */
  public void printJsonTofile(String testName, String jsonStr, String path) {
    try {
      Map<String, Object> properties = new LinkedHashMap<>(1);
      properties.put(JsonGenerator.PRETTY_PRINTING, true);
      String jsonFilename =
        testName.replace(" ", "")
          + "_"
          + new SimpleDateFormat("yyyyMMdd_hhmmss").format(new Date())
          + ".json";
      File dir = new File(path);
      if (!dir.isDirectory()) {
        dir.mkdirs();
      }
      FileOutputStream fo = new FileOutputStream(path + jsonFilename);
      PrintWriter pw = new PrintWriter(fo, true);
      JsonWriterFactory writerFactory = Json.createWriterFactory(properties);
      JsonWriter jsonWriter = writerFactory.createWriter(pw);
      JsonObject obj = Json.createReader(new StringReader(jsonStr)).readObject();
      jsonWriter.writeObject(obj);
      jsonWriter.close();
      pw.close();
      fo.close();
    } catch (Exception e) {
      logger.error("\r\n" + TestUtils.getStackTrace(e));
    }
  }

  /**
   * Convert the JSON Object into a line of values that can be printed in the CSV file.
   *
   * @param map
   * @return line String to be printed in the CSV file
   */
  private String valuesLine(Map<String, String> map) {
    String line = "";
    int i = 0;
    for (String key : map.keySet()) {
      line += map.get(key) + (i++ < map.size() ? "," : "");
    }
    return line;
  }

  /**
   * Convert the JSON Object into a line of keys that can be printed as the header of the CSV file.
   *
   * @param map
   * @return line String to be printed in the CSV file
   */
  private String keysLine(Map<String, String> map) {
    String line = "";
    int i = 0;
    for (String key : map.keySet()) {
      line += key + (i++ < map.size() ? "," : "");
    }
    return line;
  }

  /**
   * Print the CSV file header. Make sure to call this right after calling getInstance() for the
   * first time, and before calling print(Stat s).
   *
   * @param header a String for the CSV header
   */
  private void printHeader(String header) {
    pw.println(header);
  }

  /** Close the printWriter object. It must be called once the test is over. */
  public void close() {
    try {
      if (pw != null) {
        logger.debug("Closing " + filename);
        pw.close();
        fout.close();
      }
    } catch (Exception e) {
      logger.error("\r\n" + TestUtils.getStackTrace(e));
    }
  }

  /**
   * Translate the JsonObject into a Map<String,Object> where Object is either the json value or
   * another Map<String, Object>. String is always the json key.
   *
   * @param json the JsonObject
   * @return Map<String key, Object: either json value or another Map<String, Object>
   * @throws JsonException
   */
  private static Map<String, Object> jsonToMap(JsonObject json) throws JsonException {
    Map<String, Object> retMap = new LinkedHashMap<String, Object>();
    keyValMap = new LinkedHashMap<String, String>(); // re-initialise it in case.
    if (json != JsonObject.NULL) {
      retMap = toMap(json, "");
    }
    if (logger.isDebugEnabled()) {
      logger.debug("jsonToMap() dump");
      for (String key : keyValMap.keySet()) {
        logger.debug("keyList[" + key + "] = " + keyValMap.get(key));
      }
    }
    return retMap;
  }

  /**
   * Translate the JsonObject into a flat Map<String,String> of key - value pairs For nested
   * objects, the key becomes parentkey.key, to achieve the flat Map.
   *
   * @param json the JsonObject
   * @return Map<String key, Object: either json value or another Map<String, Object>
   * @throws JsonException
   */
  private static Map<String, String> jsonToHashMap(JsonObject json) throws JsonException {
    Map<String, Object> retMap = new LinkedHashMap<String, Object>();
    keyValMap = new LinkedHashMap<String, String>(); // re-initialise it in case.
    StringBuilder keyBuilder = new StringBuilder("");
    if (json != JsonObject.NULL) {
      retMap = toMap(json, "");
    }
    if (logger.isDebugEnabled()) {
      logger.debug("jsonToHashMap() dump");
      for (String key : keyValMap.keySet()) {
        logger.debug("keyList[" + key + "] = " + keyValMap.get(key));
      }
    }
    return keyValMap;
  }

  /**
   * Recursively browse the jsonObject and returns a Map<String key, Object: either json value or
   * another map
   *
   * @param object JsonObject
   * @param parent json key of the parent json node.
   * @return Map<String key, Object: either json value or another Map<String, Object>
   * @throws JsonException
   */
  private static Map<String, Object> toMap(JsonObject object, String parent) throws JsonException {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    Iterator<String> keysItr = object.keySet().iterator();
    while (keysItr.hasNext()) {
      String key = keysItr.next();
      Object value = object.get(key);
      if (value instanceof JsonArray) {
        value = toList((JsonArray) value, key);
      } else if (value instanceof JsonObject) {
        value = toMap((JsonObject) value, key);
      } else {
        String keyFull = parent + (parent.length() > 0 ? "." : "") + key;
        keyValMap.put(keyFull, value.toString());
      }
      map.put(key, value);
    }
    return map;
  }

  /**
   * Recursively browse the jsonObject and returns a List<Object1> where Object is either a
   * List<Object> or another Map<String, Object> (see toMap)
   *
   * @param array JsonArray
   * @param parent json key of the parent json node.
   * @return List<Object1> where Object is either a List<Object> or another Map<String, Object> (see
   *     toMap)
   * @throws JsonException
   */
  private static List<Object> toList(JsonArray array, String parent) throws JsonException {
    List<Object> list = new ArrayList<Object>();
    for (int i = 0; i < array.size(); i++) {
      Object value = array.get(i);
      parent = parent + "[" + i + "]";
      if (value instanceof JsonArray) {
        value = toList((JsonArray) value, parent);
      } else if (value instanceof JsonObject) {
        value = toMap((JsonObject) value, parent);
      }
      list.add(value);
    }
    return list;
  }
}
