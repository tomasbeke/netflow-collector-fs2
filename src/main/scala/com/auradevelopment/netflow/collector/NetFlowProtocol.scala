package com.auradevelopment.netflow.collector

import java.nio.ByteBuffer
import scala.annotation.tailrec
import scala.util.Try

// Intermediate format
case class NetFlowRecord(first: Int, last: Int, srcIp: String, dstIp: String, srcPort: Int, dstPort: Int, packets: Int, octets: Int, protocolType: Int)
case class NetFlowHeader(
                          version: Int,
                          flows: Int,
                          uptimeMs: Int,
                          timeSec: Int,
                          timeNano: Int,
                          flowSeq: Int,
                          engineType: Int,
                          engineId: Int,
                          samplingInterval: Int,
                          records: List[NetFlowRecord]
                        )

// Output format
case class ProcessedNetFlowRecord(flowSeq: Int, start: Long, end: Long, srcIp: String, dstIp: String, srcPort: Int, dstPort: Int, packets: Int, octets: Int, protocolType: Int)

object NetFlowUtil {
  /**
   * For data format, refer to https://www.cisco.com/c/en/us/td/docs/net_mgmt/netflow_collection_engine/3-6/user/guide/format.html
   */
  def decodeNetFlow(data: ByteBuffer): Either[Throwable, NetFlowHeader] = {
    def unsignedAt(offset: Int): Int = at(offset) & 0xff
    def at(offset: Int): Byte = data.get(offset)

    def uint8(offset: Int): Either[Throwable, Int] =
      Try(unsignedAt(offset)).toEither

    def uint16(offset: Int): Either[Throwable, Int] =
      Try(ByteBuffer.wrap(Array[Byte](0, 0, at(offset), at(offset + 1))).getInt).toEither

    def uint32(offset: Int): Either[Throwable, Int] =
      Try(ByteBuffer.wrap(Array[Byte](at(offset), at(offset + 1), at(offset + 2), at(offset + 3))).getInt).toEither

    def ip(offset: Int): Either[Throwable, String] = {
      Try(s"${unsignedAt(offset)}:${unsignedAt(offset + 1)}:${unsignedAt(offset + 2)}:${unsignedAt(offset + 3)}").toEither
    }

    @tailrec
    def readRecords(data: ByteBuffer, base: Int, remaining: Int, result: List[NetFlowRecord]): List[NetFlowRecord] =
      if (remaining == 0) result
      else {
        val eitherRecord: Either[Throwable, NetFlowRecord] = for {
          srcIp <- ip(base)
          dstIp <- ip(base + 4)
          first <- uint32(base + 24)
          last <- uint32(base + 28)
          srcPort <- uint16(base + 32)
          dstPort <- uint16(base + 34)
          packets <- uint32(base + 16)
          octets <- uint32(base + 20)
          protocolType <- uint8(base + 38)
        } yield NetFlowRecord(first, last, srcIp, dstIp, srcPort, dstPort, packets, octets, protocolType)

        eitherRecord match {
          case Left(_) => readRecords(data, base + 48, remaining - 1, result)
          case Right(record) => readRecords(data, base + 48, remaining - 1, result :+ record)
        }
      }

    for {
      version <- uint16(0)
      flows <- uint16(2)
      uptimeMs <- uint32(4)
      timeSec <- uint32(8)
      timeNano <- uint32(12)
      flowSeq <- uint32(16)
      engineType <- uint8(17)
      engineId <- uint8(18)
      samplingInterval <- uint16(20)
      records <- Right(readRecords(data, 24, flows, List[NetFlowRecord]()))
    } yield NetFlowHeader(version, flows, uptimeMs, timeSec, timeNano, flowSeq, engineType, engineId, samplingInterval, records)
  }

  def processNetFlowRecords(netFlowHeader: NetFlowHeader): List[ProcessedNetFlowRecord] = {
    val headerTimeMillis: Long = (netFlowHeader.timeSec.toLong * 1000) + (netFlowHeader.timeNano.toLong / 1000000)
    val uptimeOffset = headerTimeMillis - netFlowHeader.uptimeMs
    netFlowHeader.records.map { record =>
      ProcessedNetFlowRecord(
        netFlowHeader.flowSeq,
        uptimeOffset + record.first,
        uptimeOffset + record.last,
        record.srcIp,
        record.dstIp,
        record.srcPort,
        record.dstPort,
        record.packets,
        record.octets,
        record.protocolType
      )
    }
  }
}
