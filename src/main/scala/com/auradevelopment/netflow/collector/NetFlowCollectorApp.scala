package com.auradevelopment.netflow.collector

import cats.effect._
import cats.{Applicative, Functor}
import com.comcast.ip4s._
import fs2.io.net.{Datagram, DatagramSocket, Network}
import fs2.kafka.{KafkaProducer, ProducerRecord, ProducerRecords, ProducerSettings}
import fs2.{Chunk, Pipe, Stream, io, text}
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._

import java.nio.ByteBuffer

given logging: LoggerFactory[IO] = Slf4jFactory[IO]

object NetFlowStreamProcessors {
  def datagramToByteBuffer[F[_]]: Pipe[F, Datagram, ByteBuffer] = in =>
    in.map(datagram => datagram.bytes.toByteBuffer)

  def decodeNetFlow[F[_] : LoggerFactory : Functor]: Pipe[F, ByteBuffer, ProcessedNetFlowRecord] = { in =>
    val logger = LoggerFactory[F].getLogger
    in
      .map(NetFlowUtil.decodeNetFlow)
      .evalTap {
        case Left(e) => logger.error(e)("Unable to decode data")
        case Right(record) => logger.info(record.toString)
      }
      .collect {
        case Right(header: NetFlowHeader) => header
      }
      .map(header => NetFlowUtil.processNetFlowRecords(header))
      .flatMap(Stream.emits(_))
  }

  def toCSV[F[_], T <: Product]: Pipe[F, T, String] = in =>
    in.map(data => data.productIterator.map {
      case Some(value) => value
      case None => ""
      case rest => rest
    }.mkString(","))
}

object KafkaStreamProcessors {
  def toKeylessProducerRecord[F[_]](topicName: String): Pipe[F, String, ProducerRecords[Unit, Unit, String]] = in =>
    in.map { value =>
      val record: ProducerRecord[Unit, String] = ProducerRecord(topicName, (), value)
      ProducerRecords.one(record)
    }
}

object NetFlowCollectorApp extends IOApp.Simple {
  import NetFlowStreamProcessors._
  import KafkaStreamProcessors._
  import cats.syntax.flatMap._
  import cats.syntax.functor._

  private val bootstrapServers = System.getenv("BOOTSTRAP_SERVER")
    .ensuring(_ != null, "Set 'BOOTSTRAP_SERVER'")

  private val topicName = System.getenv("TOPIC_NAME")
    .ensuring(_ != null, "Set 'TOPIC_NAME'")

  private val bindPort = System.getenv("BIND_PORT")
    .ensuring(_ != null, "Set 'BIND_PORT'")

  val port: Option[Port] = Port.fromString(bindPort)

  def producerSettings[F[_] : Sync] = ProducerSettings[F, Unit, String]
    .withBootstrapServers(bootstrapServers)

  def udpServer[F[_] : Network : Async] =
    Stream.resource(Network[F].openDatagramSocket(port = port)).flatMap { (socket: DatagramSocket[F]) =>
      socket.reads
        .through(datagramToByteBuffer)
        .through(decodeNetFlow)
        .through(toCSV)
        .through(toKeylessProducerRecord(topicName))
        .through(KafkaProducer.pipe(producerSettings))
    }.compile.drain

  def app[F[_] : Network : Async]: F[Unit] = {
    val logger = LoggerFactory[F].getLogger
    for {
      _ <- logger.info(s"Starting NetFlow collector server on port $bindPort")
      _ <- udpServer
    } yield ()
  }

  override def run: IO[Unit] = app[IO]
}
