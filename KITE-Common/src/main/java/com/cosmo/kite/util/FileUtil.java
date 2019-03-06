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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class FileUtil {


  /**
   *
   * Reads the file content into a String
   *
   * @param path to the file
   * @return the content of the file as a String
   * @throws IOException
   */
  public static String readFile(String path) throws IOException {
    String result = "";
    FileInputStream fin = new FileInputStream(path);
    BufferedReader buf = new BufferedReader(new InputStreamReader(fin));
    String line = buf.readLine();
    while (line != null) {
      result += line + "\r\n";
      line = buf.readLine();
    }
    buf.close();
    fin.close();
    return result;
  }
}
