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

import com.jcraft.jsch.*;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

/**
 * Class handling SSH command running.
 */
public class SSHManager implements Callable<SSHManager> {

  /**
   * The Constant logger.
   */
  private static final Logger logger = Logger.getLogger(SSHManager.class.getName());

  static {
    JSch.setConfig("StrictHostKeyChecking", "no");
  }

  /**
   * The key file address.
   */
  private String keyFilePath;

  /**
   * The username.
   */
  private String username;

  /**
   * The password.
   */
  private String password;

  /**
   * The host ip or name.
   */
  private String hostIpOrName;

  /**
   * The command line.
   */
  private String commandLine;

  private int index = 0;

  private int count = 0;

  private int exitStatus = -1;


  /**
   * Instantiates a new Ssh manager.
   *
   * @param keyFilePath  the key file path
   * @param username     the username
   * @param hostIpOrName the host ip or name
   * @param commandLine  the command line
   */
  public SSHManager(String keyFilePath, String username, String hostIpOrName, String commandLine) {
    this.keyFilePath = keyFilePath;
    this.username = username;
    this.hostIpOrName = hostIpOrName;
    this.commandLine = commandLine;
  }

  /**
   * Instantiates a new Ssh manager.
   *
   * @param keyFilePath  the key file path
   * @param username     the username
   * @param password     the password
   * @param hostIpOrName the host ip or name
   * @param commandLine  the command line
   * @param index        the index
   * @param count        the count
   */
  public SSHManager(String keyFilePath, String username, String password, String hostIpOrName,
                    String commandLine, int index, int count) {
    this.keyFilePath = keyFilePath;
    this.username = username;
    this.password = password;
    this.hostIpOrName = hostIpOrName;
    this.commandLine = commandLine;
    this.index = index;
    this.count = count;
  }

  /**
   * Gets the host ip or name.
   *
   * @return the host ip or name
   */
  public String getHostIpOrName() {
    return hostIpOrName;
  }

  /**
   * Call.
   *
   * @return The same object in case of successful run
   * @throws Exception of KiteInsufficientValueException type if username and
   *                   keyFileAddress is not given and of SSHManagerException type if
   *                   there found an error while connecting to the host using SSH
   */
  @Override public SSHManager call() throws Exception {
    MDC.put("tag", this.hostIpOrName);
    Session session = null;
    Channel channel = null;
    InputStream inputStream = null;
    try {
      JSch jsch = new JSch();
      jsch.addIdentity(this.keyFilePath);

      // enter your own EC2 instance IP here
      session = jsch.getSession(this.username, this.hostIpOrName, 22);
      if (this.password != null) {
        session.setPassword(this.password);
      }
      session.connect();

      // run stuff
      String command = this.commandLine;
      channel = session.openChannel("exec");
      ((ChannelExec) channel).setCommand(command);
      ((ChannelExec) channel).setErrStream(System.err);
      if (count > 0) {
        logger.info(
            "Running ("
                + (this.index + 1)
                + "/"
                + this.count
                + ")' on \" + this.hostIpOrName + \" : "
                + this.commandLine);
      } else {
        logger.info("Running the following command on " + this.hostIpOrName + " : "
                + this.commandLine);
      }
      exitStatus = -1;
      channel.connect();

      inputStream = channel.getInputStream();
      // start reading the input from the executed commands on the shell
      int maxLines = 10;
      byte[] tmp = new byte[1024];
      while (maxLines-- > 0) {
        Thread.sleep(1000);
        while (inputStream.available() > 0) {
          int l = inputStream.read(tmp, 0, 1024);
          if (l < 0)
            break;
          logger.info(new String(tmp, 0, l));
        }
        if (channel.isClosed()) {
          this.exitStatus = channel.getExitStatus();
          logger.info("exit-status: " + this.exitStatus);
          break;
        }
      }

      channel.disconnect();
      session.disconnect();
    } catch (JSchException | IOException e) {
      logger.warn(e.getClass().getSimpleName() + " in SSHManager: " + e.getLocalizedMessage());
      // throw new SSHManagerException(this, e);
    } finally {
      if (inputStream != null)
        try {
          inputStream.close();
        } catch (IOException e) {
          logger.warn(e);
        }
      if (channel != null) {
        channel.disconnect();
      }
      if (session != null) {
        session.disconnect();
      }
    }

    return this;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#finalize()
   */
  @Override
  protected void finalize() throws Throwable {
    MDC.remove("tag");
  }


  public boolean commandSuccessful() {
    return this.exitStatus == 0;
  }


}
