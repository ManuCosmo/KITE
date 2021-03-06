/*
 * Copyright 2018 Cosmo Software
 */

package org.webrtc.kite.exception;

import org.webrtc.kite.SSHManager;

/**
 * The type Ssh manager exception.
 */
public class SSHManagerException extends Exception {

  /**
   * The Constant serialVersionUID.
   */
  private static final long serialVersionUID = 1L;

  /**
   * The command runner.
   */
  private SSHManager sshManager;

  /**
   * The exception.
   */
  private Exception exception;

  /**
   * Instantiates a new Ssh manager exception.
   *
   * @param sshManager the command runner
   * @param exception  the exception
   */
  public SSHManagerException(SSHManager sshManager, Exception exception) {
    this.sshManager = sshManager;
    this.exception = exception;
  }

  /**
   * Gets command runner.
   *
   * @return the command runner
   */
  public SSHManager getSSHManager() {
    return sshManager;
  }

  /**
   * Gets exception.
   *
   * @return the exception
   */
  public Exception getException() {
    return exception;
  }

}
