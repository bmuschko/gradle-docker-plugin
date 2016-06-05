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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerExecContainer extends DockerExistingContainer {

  	@Input
  	String[] cmd
  	
  	@Input
  	@Optional
  	Boolean attachStdout = true
  	
  	@Input
  	@Optional
  	Boolean attachStderr = true
  	

  	@Override
	void runRemoteCommand(dockerClient) {
  		logger.quiet "Executing '${getCmd()}' on container with ID '${getContainerId()}'."
    	def execCallback = threadContextClassLoader.createExecCallback(System.out, System.err)
    	def execCmd = dockerClient.execCreateCmd(getContainerId())
    	setContainerCommandConfig(execCmd)
    	dockerClient.execStartCmd(execCmd.exec().getId()).withDetach(false).exec(execCallback).awaitCompletion();  
  	}
  	
  	private void setContainerCommandConfig(containerCommand) {  	
  	    if (getCmd()) {
  	    	containerCommand.withCmd(getCmd())
  	    }
  	
        if (getAttachStderr()) {
  	    	containerCommand.withAttachStderr(getAttachStderr())
        }
        
        if (getAttachStdout()) {
  	    	containerCommand.withAttachStdout(getAttachStdout())
        }
    }
}
