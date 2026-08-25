from __future__ import annotations

import smtplib

from app.domain.models import Channel, SentNotification


class SMTPNotifier:
    def __init__(self, address: str, from_address: str):
        self.address = address
        self.from_address = from_address

    def send(self, notification: SentNotification) -> None:
        host, port_text = self.address.rsplit(":", 1)
        message = ( 
            f"From: {self.from_address}\r\n"
            f"To: {notification.recipient_email}\r\n"
            f"Subject: {notification.subject}\r\n"
            "\r\n"
            f"{notification.subject}\r\n"
        )
        with smtplib.SMTP(host, int(port_text)) as smtp:
            smtp.sendmail(
                self.from_address,
                [notification.recipient_email],
                message.encode("utf-8"),
            )


class InAppNotifier:
    def send(self, notification: SentNotification) -> None:
        return None


class CompositeNotifier:
    def __init__(self, email_notifier, in_app_notifier, metrics=None):
        self.email_notifier = email_notifier
        self.in_app_notifier = in_app_notifier
        self.metrics = metrics

    def send(self, notification: SentNotification) -> None:
        error = None
        try:
            if notification.channel == Channel.EMAIL:
                self.email_notifier.send(notification)
            elif notification.channel == Channel.IN_APP:
                self.in_app_notifier.send(notification)
            else:
                raise ValueError(f"unsupported channel: {notification.channel}")
        except Exception as exc:
            error = exc
        finally:
            if self.metrics is not None:
                self.metrics.record_delivery(
                    notification.channel.value if hasattr(notification.channel, "value") else str(notification.channel),
                    "FAILED" if error else "SENT",
                )
        if error is not None:
            raise error
