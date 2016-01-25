package kafkaqueue

import java.util.Properties
import java.util.concurrent.{Executors, ExecutorService}
import javax.inject.{Inject, Singleton}

import com.typesafe.config.ConfigFactory
import controllers.EmailTemplateMessage
import kafka.consumer.{Consumer => KafkaConsumer, ConsumerConfig}
import mailgunwrapper.MailSender
import play.api.Play
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Json
import service.{EmailSender, ErrorType}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

case class QueueResult(success: Boolean, error: Option[ErrorType.Value], id: Option[String], errorMsg: Option[String], msg: Option[String])

object QueueResult {
  def fromError(exception: Throwable, error: ErrorType.Value) = QueueResult(false, Some(error), None, Some(exception.getMessage), None)
  def success(id: String, msg: String) = QueueResult(true, None, Some(id), None, Some(msg))
}

@Singleton
class ConsumerProvider @Inject()(applicationLifecycle: ApplicationLifecycle) {
  implicit val format = Json.format[EmailTemplateMessage]
  private val executor: ExecutorService = Executors.newSingleThreadExecutor()
  val ec = ExecutionContext.fromExecutor(executor)

  def from = Play.application.configuration.getString("mail.from").get
  val consumer = new TopicConsumer(Play.application.configuration.getString("kafka.topic").get)

  /**
    * The shutdown could potentially lose messages that were queued to be sent rather than actually sent.
    * Due to the fact we're returning Futures we should have generally have everything processed
    * by the queue sent of to the mail server but this would require additional testing to ensure
    * that large batches of messages received around shutdown time are not lost permanently.
    * By marking the consumer as shut down, we record the offset in the receiving Kafka instance
    * so we only receive fresh messages when we start up again.
    * @return
    */
  def shutDown() = {
    consumer.consumer.shutdown()
    executor.shutdownNow()
  }
  applicationLifecycle.addStopHook(() => {
    Future.successful(shutDown())
  })
  //Not strictly necessary but useful for Dev mode etc. The shutdown is idempotent so we can call shutdown twice safely
  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      shutDown()
    }
  })
  Future {
    consumer.read().foreach(s => {
      s.foreach(msg => {
        val send: Future[MailSender.Response] = EmailSender.doSend(Json.parse(msg).as[EmailTemplateMessage].toEmail(from))
        send.onComplete {
          /**
            * We probably would want to add additional handling here.
            * We might want to save the successful messages to one table in the DB,
            * and the unsuccessful messages to another DB so that they might be replayed at a later date
            * to see if they can be delivered later (depending on the error).
            * Otherwise they will be dropped from the queue forever.
            */

          case Success(sent) => println(QueueResult.success(sent.id, sent.message))
          case Failure(failed) => println(QueueResult.fromError(failed, ErrorType.SendFailed))
        }
      })
    })
  }(ec)
}

class TopicConsumer(topic: String) {
  private val config = new ConsumerConfig(new KafkaConfig())
  val consumer = KafkaConsumer.create(config)
  private val consumerMap = consumer.createMessageStreams(Map(topic -> 1))
  private val stream = consumerMap.getOrElse(topic, List()).head

  def read(): Stream[Option[String]] = Stream.cons(stream.headOption.map(h => new String(h.message())), read())
}

class KafkaConfig extends Properties {
  val typesafeConfig = ConfigFactory.load()
  typesafeConfig.getObject("kafka").foreach({ case (k, v) => {
      put(k.replaceAll("-", "."), v.unwrapped())
    }
  })
}