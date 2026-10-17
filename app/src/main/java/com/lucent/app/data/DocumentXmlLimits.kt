package com.lucent.app.data

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

internal object DocumentXmlLimits {
    fun check(xml: String) {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) { "DOCX不允许DTD或实体声明" }
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        val reader = factory.newSAXParser().xmlReader
        runCatching { reader.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        reader.setEntityResolver { _, _ -> throw SAXException("不允许外部XML实体") }
        reader.contentHandler = object : DefaultHandler() {
            private var depth = 0
            private var elements = 0
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                depth++
                elements++
                if (depth > 128 || elements > 200_000) throw SAXException("DOCX XML复杂度超过限制")
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) { depth-- }
        }
        reader.parse(InputSource(StringReader(xml)))
    }
}
