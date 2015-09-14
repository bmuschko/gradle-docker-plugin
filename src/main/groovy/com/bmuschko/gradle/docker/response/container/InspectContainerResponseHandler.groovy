package com.bmuschko.gradle.docker.response.container

import com.bmuschko.gradle.docker.response.ResponseHandler
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class InspectContainerResponseHandler implements ResponseHandler<String, Object> {
    Logger logger = Logging.getLogger(InspectContainerResponseHandler)

    @Override
    String handle(Object container) {
        logger.quiet "Image ID    : $container.imageId"
        logger.quiet "Name        : $container.name"
        logger.quiet "Links       : $container.hostConfig.links"
        logger.quiet "Volumes     : $container.volumes"
        logger.quiet "VolumesFrom : $container.hostConfig.volumesFrom"
    }
}
