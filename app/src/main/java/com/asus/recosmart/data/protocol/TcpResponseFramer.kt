package com.asus.recosmart.data.protocol

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

/**
 * Handles TCP JSON response framing matching original com.sanjet.communication.v2.DeviceResponseHandler.
 * Tracks JSON string quotes and escape slashes so braces '{' and '}' inside quoted string values
 * do not corrupt TCP frame boundaries.
 */
object TcpResponseFramer {

    /**
     * Reads from [inputStream] byte-by-byte into [buffer] until a complete brace-balanced JSON object is assembled.
     */
    fun readNextJsonResponse(inputStream: InputStream, buffer: ByteArrayOutputStream = ByteArrayOutputStream()): String {
        var braceDepth = 0
        var foundStartBrace = false
        var inString = false
        var isEscaped = false
        val frameBuffer = ByteArrayOutputStream()

        while (true) {
            val readByte = inputStream.read()
            if (readByte == -1) {
                if (frameBuffer.size() == 0) {
                    throw EOFException("Socket input stream closed (EOF)")
                } else {
                    break
                }
            }

            val char = readByte.toChar()
            buffer.write(readByte)

            if (foundStartBrace) {
                frameBuffer.write(readByte)

                if (isEscaped) {
                    isEscaped = false
                } else if (char == '\\' && inString) {
                    isEscaped = true
                } else if (char == '"') {
                    inString = !inString
                } else if (!inString) {
                    if (char == '{') {
                        braceDepth++
                    } else if (char == '}') {
                        braceDepth--
                        if (braceDepth <= 0) {
                            break
                        }
                    }
                }
            } else if (char == '{') {
                foundStartBrace = true
                braceDepth = 1
                frameBuffer.write(readByte)
            }
        }

        val raw = frameBuffer.toString("UTF-8").trim('\u0000', '\r', '\n', ' ')
        if (raw.isEmpty()) {
            throw EOFException("Empty JSON frame received")
        }
        return raw
    }

    /**
     * Extracts distinct JSON object strings from a string payload containing multiple concatenated JSON objects.
     */
    fun extractJsonFrames(input: String): List<String> {
        val results = mutableListOf<String>()
        var braceDepth = 0
        var startIndex = -1
        var inString = false
        var isEscaped = false

        for (i in input.indices) {
            val char = input[i]

            if (startIndex != -1) {
                if (isEscaped) {
                    isEscaped = false
                } else if (char == '\\' && inString) {
                    isEscaped = true
                } else if (char == '"') {
                    inString = !inString
                } else if (!inString) {
                    if (char == '{') {
                        braceDepth++
                    } else if (char == '}') {
                        braceDepth--
                        if (braceDepth <= 0) {
                            results.add(input.substring(startIndex, i + 1))
                            startIndex = -1
                        }
                    }
                }
            } else if (char == '{') {
                startIndex = i
                braceDepth = 1
                inString = false
                isEscaped = false
            }
        }
        return results
    }
}
