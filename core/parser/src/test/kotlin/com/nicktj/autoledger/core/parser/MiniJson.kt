package com.nicktj.autoledger.core.parser

/**
 * 测试专用的极简 JSON 解析器。
 *
 * 只为了读 `src/test/resources/samples.json`，不引第三方依赖。
 * 支持对象、数组、字符串（含常见转义）、整数/小数、true/false/null。
 */
internal object MiniJson {

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.readValue()
        reader.skipWhitespace()
        require(reader.isAtEnd()) { "JSON 末尾存在多余内容（位置 ${reader.position}）" }
        return value
    }

    private class Reader(private val text: String) {

        var position: Int = 0
            private set

        fun isAtEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (!isAtEnd() && text[position].isWhitespace()) position++
        }

        fun readValue(): Any? {
            skipWhitespace()
            require(!isAtEnd()) { "JSON 意外结束" }
            return when (val ch = text[position]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> {
                    require(ch == '-' || ch.isDigit()) { "无法识别的 JSON 字符 '$ch'（位置 $position）" }
                    readNumber()
                }
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            val result = LinkedHashMap<String, Any?>()
            if (peek() == '}') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return result
                    }
                    else -> error("对象缺少逗号或右花括号（位置 $position）")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            val result = ArrayList<Any?>()
            if (peek() == ']') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                result += readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return result
                    }
                    else -> error("数组缺少逗号或右方括号（位置 $position）")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                require(!isAtEnd()) { "字符串未闭合" }
                val ch = text[position++]
                when (ch) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        require(!isAtEnd()) { "转义符未闭合" }
                        when (val escaped = text[position++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                require(position + 4 <= text.length) { "\\u 转义长度不足" }
                                val hex = text.substring(position, position + 4)
                                position += 4
                                builder.append(hex.toInt(16).toChar())
                            }
                            else -> error("不支持的转义 \\$escaped")
                        }
                    }
                    else -> builder.append(ch)
                }
            }
        }

        private fun readNumber(): Any {
            val start = position
            if (peek() == '-') position++
            while (!isAtEnd() && text[position].isDigit()) position++
            var floating = false
            if (peek() == '.') {
                floating = true
                position++
                while (!isAtEnd() && text[position].isDigit()) position++
            }
            if (peek() == 'e' || peek() == 'E') {
                floating = true
                position++
                if (peek() == '+' || peek() == '-') position++
                while (!isAtEnd() && text[position].isDigit()) position++
            }
            val token = text.substring(start, position)
            return if (floating) token.toDouble() else token.toLong()
        }

        private fun readLiteral(literal: String, value: Any?): Any? {
            require(text.startsWith(literal, position)) { "期望 $literal（位置 $position）" }
            position += literal.length
            return value
        }

        private fun expect(ch: Char) {
            require(peek() == ch) { "期望 '$ch'，实际 '${peek()}'（位置 $position）" }
            position++
        }

        private fun peek(): Char = if (isAtEnd()) '\u0000' else text[position]
    }
}
