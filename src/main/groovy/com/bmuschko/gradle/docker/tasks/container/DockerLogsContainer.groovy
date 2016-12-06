/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker.tasks.container

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import org.gradle.util.ConfigureUtil


/**
 * Copies the container logs into standard out/err, the same as the `docker logs` command. The container output
 * to standard out will go to standard out, and standard err to standard err.
 */
class DockerLogsContainer extends DockerExistingContainer {
    /**
     * Set to true to follow the output, which will cause this task to block until the container exists.
     * Default is unspecified (docker defaults to false).
     */
    @Input
    @Optional
    Boolean follow

    /**
     * Set to true to copy all output since the container has started. For long running containers or containers
     * with a lot of output this could take a long time. This cannot be set if #tailCount is also set. Setting to false
     * leaves the decision of how many lines to copy to docker.
     * Default is unspecified (docker defaults to true).
     */
    @Input
    @Optional
    Boolean tailAll

    /**
     * Limit the number of lines of existing output. This cannot be set if #tailAll is also set.
     * Default is unspecified (docker defaults to all lines).
     */
    @Input
    @Optional
    Integer tailCount

    /**
     * Include standard out.
     * Default is true.
     */
    @Input
    @Optional
    boolean stdOut = true


    /**
     * Include standard err.
     * Default is true.
     */
    @Input
    @Optional
    boolean stdErr = true

    /**
     * Set to the true to include a timestamp for each line in the output.
     * Default is unspecified (docker defaults to false).
     */
    @Input
    @Optional
    Boolean showTimestamps

    /**
     * Limit the output to lines on or after the specified date.
     * Default is unspecified (docker defaults to all lines).
     */
    @Input
    @Optional
    Date since

    /**
     * Sink to write log output into
     */
    @Input
    @Optional
    Writer sink

    /**
     * Poll for a regular expression before we move into anything else with the log
     */
    @Input
    @Optional
    LogPoller poller

    @Override
    void runRemoteCommand(dockerClient) {
        if (getPoller()) {
          poll(dockerClient)
        }
        
        logger.quiet "Logs for container with ID '${getContainerId()}'."
        def logCommand = dockerClient.logContainerCmd(getContainerId())
        setContainerCommandConfig(logCommand)
        def loggingCallback = sink ? threadContextClassLoader.createLoggingCallback(sink) : threadContextClassLoader.createLoggingCallback(logger)
        logCommand.exec(loggingCallback)?.awaitCompletion()
    }

    private void setContainerCommandConfig(logsCommand) {
        if (getFollow() != null) {
            logsCommand.withFollowStream(getFollow())
        }

        if (getShowTimestamps() != null) {
            logsCommand.withTimestamps(getShowTimestamps())
        }

        logsCommand.withStdOut(getStdOut()).withStdErr(getStdErr())

        if (getTailAll() != null && getTailCount() != null) {
            throw new InvalidUserDataException("Conflicting parameters: only one of tailAll and tailCount can be specified")
        }

        if (getTailAll() != null) {
            logsCommand.withTailAll()
        } else if (getTailCount() != null) {
            logsCommand.withTail(getTailCount())
        }

        if (getSince()) {
            logsCommand.withSince((int) (getSince().time / 1000))
        }
    }
    
    void poller(regex) {
      poller(regex, LogPoller.DEFAULT_SLEEP, LogPoller.DEFAULT_TIMEOUT)
    }
    
    void poller(regex, sleep, timeout) {
      if(sleep <= 0) {
        throw new InvalidUserDataException("sleep should be greater than zero")
      }
  
      if(timeout <= 0) {
        throw new InvalidUserDataException("timeout should be greater than zero")
      }
  
      if(regex == null || regex == '') {
        throw new InvalidUserDataException("regex should be set to a valid string")
      }

      poller = new LogPoller(regex, sleep, timeout)
    }
    
    private void poll(dockerClient) {
      logger.quiet "Monitoring logs for container with ID '${getContainerId()}', for '${poller.regex}'."
  
      def logCommand = dockerClient.logContainerCmd(getContainerId())
      
      logCommand.withStdOut(true).withStdErr(true).withTailAll()
      
      Writer sink = new StringWriter()
  
      def loggingCallback = threadContextClassLoader.createLoggingCallback(sink)
      
      boolean finished = false
      int howLongWaiting = 0
  
      while(!finished) {
        logCommand.exec(loggingCallback)?.awaitCompletion()
        
        logger.quiet "found ${sink.toString()}"
        
        finished = (sink.toString() ==~ poller.regex)
  
        if(!finished) {
          Thread.sleep(poller.sleep)
  
          howLongWaiting += poller.sleep
  
          if(howLongWaiting >= poller.timeout) {
            throw new GradleException("Timed out waiting for '${poller.regex}' in container log")
          }
  
          logger.info "Log sink contains [${sink}]"
        }
      }
    }
    
    static class LogPoller {
      public static int DEFAULT_SLEEP = 250
      public static int DEFAULT_TIMEOUT = 120000
      
      /**
       * Set to the number of milliseconds to wait between checks of the log content
       * Default is 250 (milliseconds)
       */
      @Input
      @Optional
      int sleep = DEFAULT_SLEEP
    
      /**
       * Set to the number of milliseconds before timing out and aborting the task
       * Default is 120000 (milliseconds), which is 2 minutes
       */
      @Input
      @Optional
      int timeout = DEFAULT_TIMEOUT
    
      /**
       * Set to the string to find in the log. this should be a vaild regular expression
       */
      @Input
      String regex
      
      LogPoller(regex, sleep, timeout) {
        this.regex = regex
        this.sleep = sleep
        this.timeout = timeout
      }
    }
}

