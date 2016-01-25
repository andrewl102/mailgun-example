package controllers

import java.util.concurrent.TimeoutException

import com.google.inject.Inject
import kafkaqueue.ConsumerProvider
import mailgunwrapper.{Email, MailSender}
import mailgunwrapper.MailSender.Response
import play.api.Play
import play.api.Play.current
import play.api.libs.json.Json
import play.api.mvc._
import service.EmailSender

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

trait Message {
  def isValid = errorMessage.isEmpty
  def toEmail(from: String): Email
  def errorMessage: Option[String]
}

/**
  * Case class for JSON of the step 1 message.
  */
case class SimpleMessage(to: String, subject: String, body: String) extends Message {
  //Very poor validation, just a placeholder for now
  def errorMessage = {
    if (to.isEmpty) {
      Some("To field is empty")
    } else if (!to.contains("@")) {
      Some("To is missing @")
    } else if (body.isEmpty) {
      Some("Body is empty")
    }
    else None
  }

  def toEmail(from: String) = {
    Email(
      to = Email.Addr(to, None),
      from = Email.Addr(from, None),
      subject = subject,
      body = EmailSender.bodyVal(Some(body), None)
    )
  }
}

/**
  * Case class for JSON of the step 2 message, supporting a template name and a map or arguments.
  */
case class EmailTemplateMessage(to: String, subject: String, template: String, params: Map[String, String]) extends Message {
  //Very poor validation, just a placeholder for now
  def errorMessage = {
    val paramErrors = if (to.isEmpty) {
      Some("To field is empty")
    } else if (!to.contains("@")) {
      Some("To is missing @")
    } else None

    paramErrors.orElse(
      convertToHtml(template, params) match {
        case Left(x) => Some(x)
        case Right(y) => None
      }
    )
  }

  /**
    * Here we do compile type validation / conversion of name and parameters to a fixed template.
    * This works fairly well for a small number of templates but it's more likely that eventually
    * this would be replace with a generic, dynamic Map of arguments style template.
    */
  def convertToHtml(template: String, params: Map[String, String]) = {
    template match {
      case "welcome" => {
        params.get("firstName").map(f => {
          Right(views.html.welcomeEmail(f).toString())
        }).getOrElse(Left("Missing first name parameter"))
      }
      case "resetPassword" => {
        Right(views.html.passwordReset("http://www.google.com").toString())
      }
      case _ => Left("Invalid template name")
    }
  }

  def toEmail(from: String) = {
    Email(
      to = Email.Addr(to, None),
      from = Email.Addr(from, None),
      subject = subject,
      body = EmailSender.bodyVal(None, Some(convertToHtml(template, params).right.get))
    )
  }
}

class Application @Inject()(provider: ConsumerProvider) extends Controller {
  implicit val format = Json.format[EmailTemplateMessage]
  implicit val simpleFormat = Json.format[SimpleMessage]
  implicit val responseFormat = Json.format[Response]

  def index = Action {
    Ok("Running successfully")
  }

  private def from = getConfig("mail.from")
  private def mailTimeout = getConfig("mail.timeout")

  private def getConfig(param: String): String = Play.application.configuration.getString(param).get

  def withBasicAuth[A](f: (Request[A]) => Result)(implicit request: Request[A]): Result = {
    val (username,password) = (getConfig("username"), getConfig("password"))

    request.headers.get("Authorization").flatMap { authorization =>
      authorization.split(" ").drop(1).headOption.filter { encoded =>
        new String(org.apache.commons.codec.binary.Base64.decodeBase64(encoded.getBytes)).split(":").toList match {
          case u :: p :: Nil if u == username && password == p => true
          case _ => false
        }
      }
    }.map(_ => { f(request)}).getOrElse(Unauthorized.withHeaders("WWW-Authenticate" -> """Basic realm="Secured Area""""))
  }

  def processMessage(msg: Message) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    if (!msg.isValid) {
      BadRequest(msg.errorMessage.get)
    } else {
      try {
        val sent = EmailSender.doSend(msg.toEmail(from))
        sent.onComplete( {
          case Success(x) => print(s"Sent successfully : $x")
          case Failure(y) => print(s"Failed due to : ${y.getMessage}")
        })
        val result = Await.result(sent, Duration(mailTimeout))
        Ok(Json.toJson(result))
      } catch {
        case e: MailSender.Error => InternalServerError(s"Received an error sending message :${e.getMessage}")
        case t: TimeoutException => InternalServerError(s"Unable to receive reply for server in $mailTimeout")
        case t: Throwable => InternalServerError(s"Unable to send due : $t")
      }
    }
  }

  def submitTemplateEmail() = Action(parse.json) { implicit request =>
    withBasicAuth { implicit r:Request[_] =>
      request.body.validate[EmailTemplateMessage].fold(
        errors => BadRequest(errors.mkString),
        msg =>   processMessage(msg))
    }
  }

  def submitSimpleEmail() = Action(parse.json) { implicit request =>
    withBasicAuth { implicit r:Request[_] =>
      request.body.validate[SimpleMessage].fold(
        errors => BadRequest(errors.mkString),
        msg =>   processMessage(msg))
    }
  }


}