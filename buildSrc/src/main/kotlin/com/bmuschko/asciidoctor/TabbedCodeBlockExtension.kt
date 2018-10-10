package com.bmuschko.asciidoctor

import org.asciidoctor.Asciidoctor
import org.asciidoctor.extension.spi.ExtensionRegistry

class TabbedCodeBlockExtension: ExtensionRegistry {
    override fun register(asciidoctor: Asciidoctor) {
        asciidoctor.javaExtensionRegistry().docinfoProcessor(TabbedCodeBlockDocinfoProcessor::class.java)
    }
}