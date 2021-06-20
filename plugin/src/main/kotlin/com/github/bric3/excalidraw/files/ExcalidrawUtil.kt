package com.github.bric3.excalidraw.files

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.vfs.VirtualFile
import java.io.InputStreamReader
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.events.Comment
import javax.xml.stream.events.XMLEvent


class ExcalidrawUtil private constructor() {
    companion object {
        val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }


        fun isExcalidrawFile(file: VirtualFile?): Boolean {
            if (file == null) {
                return false
            }

            if (file.isDirectory || !file.exists()) {
                return false
            }
            //check for the right file extension
            val extensions = arrayOf(".excalidraw", ".excalidraw.svg", ".excalidraw.json")
            // Short-circuit for well-known file names. Allows to start with an empty file and open it in the editor.
            if (extensions.any { ext -> file.name.endsWith(ext) }) {
                return true
            }

            // check if svg file has a comment with 'payload-type:application/vnd.excalidraw+json'
            if (file.name.endsWith(".svg")) {
                val factory: XMLInputFactory = XMLInputFactory.newInstance()

                file.inputStream.use {
                    val eventReader: XMLEventReader = factory.createXMLEventReader(InputStreamReader(it))
                    while (eventReader.hasNext()) {
                        val event: XMLEvent = eventReader.nextEvent()

                        if (event.eventType == XMLStreamConstants.COMMENT) {
                            val text: String =  (event as Comment).text
                            if(text.contains("payload-type:application/vnd.excalidraw+json")) {
                                return true
                            }
                        }
                    }
                }


//                val factory = DocumentBuilderFactory.newInstance()
//                val builder = factory.newDocumentBuilder()
//                // check if file has a comment with 'payload-type:application/vnd.excalidraw+json'
//                file.inputStream.use {
//                    val doc = builder.parse(it)
//                    val xPathfactory = XPathFactory.newInstance()
//                    val xpath = xPathfactory.newXPath()
//                    val expr = xpath.compile("/svg/@content")
//                    val content = expr.evaluate(doc, XPathConstants.STRING)
//                    if (content.toString().startsWith("<mxfile ")) {
//                        return true
//                    }
//                }
            }

            // check if json file is an excalidraw document
            if (file.name.endsWith(".json")) {
                // parse json
            }

            return false
        }

        fun extractScene(svgContent: String): String {
            // basically reimplements decodeSvgMetadata
            // https://github.com/excalidraw/excalidraw/blob/5c73c5813ce92d3c7b0610530f78ccb06a47d983/src/data/image.ts#L106-L136

            // The file should be already verified to contain the following excalidraw payload type
            // payload-type:application/vnd.excalidraw+json

            // const match = svg.match(/<!-- payload-start -->(.+?)<!-- payload-end -->/);
            val base64Payload = svgContent.substringAfter("<!-- payload-start -->").substringBefore("<!-- payload-end -->")


            // const versionMatch = svg.match(/<!-- payload-version:(\d+) -->/);
            // const version = versionMatch?.[1] || "1";
            // const isByteString = version !== "1";

            val payloadVersion = when (val mr=Regex("<!-- payload-version:(\\d+) -->").find(svgContent)) {
                null -> "1"
                else -> mr.groupValues[1]
            }
            val isByteString = !payloadVersion.equals("1")

            // const json = await base64ToString(match[1], isByteString);
            //   isByteString ? atob(base64) : byteStringToString(atob(base64));
            //    atob : ASCII to Binary
            //    byteStringToString : new TextDecoder("utf-8").decode(byteStringToArrayBuffer(byteString))
            //
            //    byteStringToArrayBuffer : (byteString: string) => {
            //                                const buffer = new ArrayBuffer(byteString.length);
            //                                const bufferView = new Uint8Array(buffer);
            //                                for (let i = 0, len = byteString.length; i < len; i++) {
            //                                  bufferView[i] = byteString.charCodeAt(i);
            //                                }
            //                                return buffer;
            //                              };
            //
            //    TextDecocer.decode : decode(ab: ArrayBuffer) {
            //                           return new Uint8Array(ab).reduce(
            //                             (acc, c) => acc + String.fromCharCode(c),
            //                             "",
            //                           );
            //                         }

            if (!isByteString) {
                throw IllegalArgumentException("payload version 1 not supported at this time")
            }

            // for payload version > 1
//            val jsonString = Base64.decode(base64Payload)

            val bytes = java.util.Base64.getDecoder().decode(base64Payload)

            // const encodedData = JSON.parse(json);
            val encodedData = mapper.readValue<EncodedPayload>(bytes)


            // if (!("encoded" in encodedData)) {
            //   // legacy, un-encoded scene JSON
            //   if (
            //     "type" in encodedData &&
            //     encodedData.type === EXPORT_DATA_TYPES.excalidraw
            //   ) {
            //     return json;
            //   }
            //   throw new Error("FAILED");
            // }
            if(encodedData.encoded == null) {
                // legacy not handled yet
                throw IllegalArgumentException("legacy non encoded payload not supported at this time")
            }

            // return await decode(encodedData);
            // export const decode = async (data: EncodedData): Promise<string> => {
            //  let decoded: string;
            //
            //  switch (data.encoding) {
            //    case "bstring":
            //      // if compressed, do not double decode the bstring
            //      decoded = data.compressed
            //        ? data.encoded
            //        : await byteStringToString(data.encoded);
            //      break;
            //    default:
            //      throw new Error(`decode: unknown encoding "${data.encoding}"`);
            //  }
            //
            //  if (data.compressed) {
            //    return inflate(new Uint8Array(byteStringToArrayBuffer(decoded)), {
            //      to: "string",
            //    });
            //  }
            //
            //  return decoded;
            //};

            val compressed = encodedData.compressed

            val decoded:ByteArray = when (val encoding = encodedData.encoding) {
                "bstring" -> encodedData.encoded
                else -> throw IllegalArgumentException("decode: unknown encoding \"${encoding}\"")
            }
            val map:String = decoded.let {
                if (compressed == true) {
                    // inflate(new Uint8Array(byteStringToArrayBuffer(decoded)), {
                    //     to: "string",
                    // })
                    //                        return inflated;
                    ""
                } else {
                    //                        return byteStringToString(data.encoded);
                    ""
                }
            }

            return map
        }
    }

    data class EncodedPayload(
        val version: String?,
        val encoding: String?,
        val compressed: Boolean?,
//        @JsonDeserialize(using = ByteArraySerializer::class)
        val encoded: ByteArray?
    )

    class ByteArraySerializer : JsonDeserializer<ByteArray?>() {
        override fun deserialize(jp: JsonParser, dc: DeserializationContext): ByteArray? {
//            val binaryValue = jp.binaryValue
            val node:JsonNode = jp.codec.readTree(jp)

            return null
        }
    }
}