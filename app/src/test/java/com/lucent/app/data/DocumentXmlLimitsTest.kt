package com.lucent.app.data

import org.junit.Assert.*
import org.junit.Test

class DocumentXmlLimitsTest {
    @Test fun acceptsOrdinaryXmlAndRejectsDeepInputBeforeDom() {
        DocumentXmlLimits.check("<document><body><p>正文</p></body></document>")
        val deep = "<p>".repeat(130) + "text" + "</p>".repeat(130)
        assertTrue(runCatching { DocumentXmlLimits.check(deep) }.exceptionOrNull() is Exception)
    }

    @Test fun entityDeclarationsAreRejected() {
        assertTrue(runCatching { DocumentXmlLimits.check("<!DOCTYPE p [<!ENTITY x 'boom'>]><p>&x;</p>") }.exceptionOrNull() is IllegalArgumentException)
    }
}
