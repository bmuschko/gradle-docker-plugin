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

class DockerStartContainer extends DockerExistingContainer {
    @Input
    @Optional
    LogPoller poller
    
    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Starting container with ID '${getContainerId()}'."
        def containerCommand = dockerClient.startContainerCmd(getContainerId())
        containerCommand.exec()

        if (getPoller()) {
            poll(dockerClient)
        }
    }
    
    void poller(Closure closure) {
      poller = new LogPoller()
      
      ConfigureUtil.configure(closure, poller)
      
      if(poller.sleep <= 0) {
        throw new InvalidUserDataException("sleep should be greater than zero")
      }
  
      if(poller.timeout <= 0) {
        throw new InvalidUserDataException("timeout should be greater than zero")
      }
  
      if(poller.regex == null || poller.regex == '') {
        throw new InvalidUserDataException("regex should be set to a valid string")
      }
    }
    
    void poll(dockerClient) {
      logger.quiet "Monitoring logs for container with ID '${getContainerId()}', for '${poller.regex}'."
  
      def logCommand = dockerClient.logContainerCmd(getContainerId())
  
      logCommand.withStdOut(true).withStdErr(true).withTailAll()
  
      Writer sink = new StringWriter()
  
      def loggingCallback = threadContextClassLoader.createLoggingCallback(sink)
  
      boolean finished = false
      int howLongWaiting = 0
  
      while(!finished) {
        logCommand.exec(loggingCallback)?.awaitCompletion()
  
        finished = sink.toString().contains(poller.regex)
  
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
      /**
       * Set to the number of milliseconds to wait between checks of the log content
       * Default is 250 (milliseconds)
       */
      @Input
      @Optional
      int sleep = 250
    
      /**
       * Set to the number of milliseconds before timing out and aborting the task
       * Default is 120000 (milliseconds), which is 2 minutes
       */
      @Input
      @Optional
      int timeout = 120000
    
      /**
       * Set to the string to find in the log
       */
      @Input
      String regex
    }
}

