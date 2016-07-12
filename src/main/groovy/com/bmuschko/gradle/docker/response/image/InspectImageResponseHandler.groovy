package com.bmuschko.gradle.docker.response.image

import com.bmuschko.gradle.docker.response.ResponseHandler
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class InspectImageResponseHandler implements ResponseHandler<Void, Object> {
    private final Logger logger

    InspectImageResponseHandler() {
        this(Logging.getLogger(InspectImageResponseHandler))
    }

    private InspectImageResponseHandler(Logger logger) {
        this.logger = logger
    }

    @Override
    Void handle(Object image) {
        logger.quiet "ID               : $image.id"
        logger.quiet "Author           : $image.author"
        logger.quiet "Created          : $image.created"
        logger.quiet "Comment          : $image.comment"
        logger.quiet "Architecture     : $image.arch"
        logger.quiet "Operating System : $image.os"
        logger.quiet "Parent           : $image.parent"
        logger.quiet "Size             : $image.size"
        logger.quiet "Docker Version   : $image.dockerVersion"
        logger.quiet "Labels           : $image.config.labels"
    }
}
