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

package com.cosmo.kite;

import junit.framework.TestCase;
import org.openqa.selenium.WebDriver;
import org.webrtc.kite.Utility;
import org.webrtc.kite.WebDriverFactory;
import org.webrtc.kite.config.Browser;

import java.util.ArrayList;
import java.util.List;

public class JanusTestTest extends TestCase {

  private static final String SELENIUM_SERVER_URL = "http://localhost:4444/wd/hub";
    private static String url = "https://lbclient.cosmosoftware.io/videoroomtest_videoanalysis.html?roomId=";
//  private static String url = "https://soleil.kite.cosmosoftware.io/viewer/";
  private static final String TEST_NAME = "Janus UnitTest";

  private List<WebDriver> webDriverList = new ArrayList<WebDriver>();

  private static final String platform = Utility.getPlatform();

  public void setUp() throws Exception {
    super.setUp();
    final Browser browser = new Browser(
            "chrome",
            "70",
            platform);
    browser.setRemoteAddress(SELENIUM_SERVER_URL);
    webDriverList.add(WebDriverFactory.createWebDriver(browser, TEST_NAME));
    webDriverList.add(WebDriverFactory.createWebDriver(browser, TEST_NAME));
  }

  public void tearDown() throws Exception {
    // Close all the browsers
    for (WebDriver webDriver : this.webDriverList)
      try {
        webDriver.quit();
      } catch (Exception e) {
        e.printStackTrace();
      }
  }

  public void testTestScript() throws Exception {
    KiteLoadTest test = new JanusTest(url, "Janus");
    test.setGetStats(true);
    test.setWebDriverList(this.webDriverList);
    System.out.println(test.testScript());
  }
}
