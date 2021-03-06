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

package org.webrtc.kite.dao;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** A class in charged of the connection to the database. */
public class DBConnectionManager {

  private static final Log log = LogFactory.getLog(DBConnectionManager.class);
  private String dbURL;
  private Connection connection;

  /**
   * Constructs a new DBConnectionManager object that creates a connection to the database.
   *
   * @param dbURL path to the database.
   */
  public DBConnectionManager(String dbURL) throws ClassNotFoundException, SQLException {
    Class.forName("org.sqlite.JDBC");
    this.dbURL = dbURL;
    this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbURL);
  }

  /** Returns the created connection to the database. */
  public Connection getConnection() {
    try {
      if (this.connection.isClosed()) {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.dbURL);
      }
    } catch (SQLException e) {
      log.warn("DB connection was closed.");
      log.info("Opening new connection.");
    }
    return this.connection;
  }
}
