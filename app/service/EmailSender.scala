package service

import mailgunwrapper.Email.Body
import mailgunwrapper.{Email, MailSender, Mailgun}
import play.api.Play
import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ErrorType extends Enumeration {
  type EmailError = Val
  val ParseFailed, SendFailed = Value
}


object EmailSender {
  val Sender: MailSender = new Mailgun(
    server = Play.application.configuration.getString("mailgun.server").get,
    apiKey = Play.application.configuration.getString("mailgun.key").get
  )

  def bodyVal(bodyText:Option[String],bodyHtml:Option[String]): Body = {
    (bodyText, bodyHtml) match {
      case  ( Some(text),Some(html) ) => Email.textAndHtml(text,html)
      case  ( None,Some(html) ) =>  {
        Email.html(html)
      }
      case  ( Some(text),None ) => Email.text(text)
      case  ( None,None ) => throw new IllegalArgumentException("Text or html is required")
    }
  }

  def doSend(email: Email): Future[MailSender.Response] = {
    Sender.send(email)
  }
}
