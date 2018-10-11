package com.bmuschko.asciidoctor

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.DocinfoProcessor

class TabbedCodeBlockDocinfoProcessor: DocinfoProcessor {
    constructor() : super()
    constructor(config: MutableMap<String, Any>) : super(config)

    override fun process(document: Document?): String {
        val css = TabbedCodeBlockDocinfoProcessor::class.java.getResource("/codeBlockSwitch.css").readText()
        val javascript = TabbedCodeBlockDocinfoProcessor::class.java.getResource("/codeBlockSwitch.js").readText()
        return """<style>
$css
</style>
<script src="http://cdnjs.cloudflare.com/ajax/libs/zepto/1.2.0/zepto.min.js"></script>
<script type="text/javascript">
$javascript
</script>
"""
    }
}