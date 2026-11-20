package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.PaySource
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceholderTest {

    @Test
    fun `stub parser returns null`() {
        val parser = object : TxnParser {
            override val source: PaySource = PaySource.MANUAL

            override fun parse(
                pkg: String,
                title: String?,
                text: String?,
                bigText: String?,
            ) = null
        }

        assertNull(parser.parse(pkg = "com.example", title = null, text = "测试", bigText = null))
    }
}
