from __future__ import annotations

import logging
import time
from collections.abc import Mapping
from typing import Any
from uuid import UUID

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from opentelemetry import propagate, trace
from opentelemetry.trace import SpanKind
from pydantic import BaseModel, ConfigDict
from starlette.concurrency import run_in_threadpool

from app.application.commands import GetNotificationQuery, SendNotificationCommand
from app.domain.models import Channel, NotificationNotFoundError, SentNotification

logger = logging.getLogger("notification-api")


class SendNotificationRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")

    recipient_id: UUID | None = None
    recipient_email: str = ""
    channel: str = ""
    subject: str = ""
    template_code: str | None = None
    template_vars: dict[str, str] | None = None
    source_service: str | None = None
    source_event_id: UUID | None = None


def _error(status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(status_code=status, content={"error_code": code, "message": message})


def _notification_payload(notification: SentNotification) -> dict[str, Any]:
    result: dict[str, Any] = {
        "id": notification.id,
        "recipient_id": notification.recipient_id,
        "channel": notification.channel.value,
        "send_status": notification.send_status.value,
        "subject": notification.subject,
    }
    if notification.sent_at is not None:
        result["sent_at"] = notification.sent_at.isoformat().replace("+00:00", "Z")
    return result


def _add_http_telemetry(app: FastAPI, metrics=None, tracer=None) -> None:
    http_tracer = tracer or trace.get_tracer("notification-service/adapters/inbound/http")

    @app.middleware("http")
    async def telemetry_middleware(request: Request, call_next):
        start = time.perf_counter()
        carrier = {key: value for key, value in request.headers.items()}
        parent_context = propagate.extract(carrier=carrier)
        with http_tracer.start_as_current_span(
            "notification-api",
            context=parent_context,
            kind=SpanKind.SERVER,
            attributes={
                "http.request.method": request.method,
                "url.path": request.url.path,
            },
        ) as span:
            response = await call_next(request)
            span.set_attribute("http.response.status_code", response.status_code)
        if metrics is not None:
            metrics.record_http(
                request.method,
                request.url.path,
                response.status_code,
                time.perf_counter() - start,
            )
        return response


def _register_health_routes(app: FastAPI, checks: Mapping[str, Any]) -> None:
    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/ready")
    def ready() -> JSONResponse:
        results: list[dict[str, Any]] = []
        all_ok = True
        for name in sorted(checks):
            item: dict[str, Any] = {"name": name, "ok": True}
            try:
                checks[name].ping()
            except Exception as exc:
                item["ok"] = False
                item["error"] = str(exc)
                all_ok = False
            results.append(item)
        return JSONResponse(
            status_code=200 if all_ok else 503,
            content={"status": "ok" if all_ok else "degraded", "checks": results},
        )


def create_app(
    send_use_case: Any,
    get_use_case: Any,
    readiness_checks: Mapping[str, Any] | None = None,
    *,
    metrics=None,
    tracer=None,
) -> FastAPI:
    app = FastAPI(title="notification-service", docs_url=None, redoc_url=None, openapi_url=None)
    checks = dict(readiness_checks or {})
    _add_http_telemetry(app, metrics=metrics, tracer=tracer)
    _register_health_routes(app, checks)

    @app.post("/notifications")
    async def send_notification(request: Request) -> JSONResponse:
        try:
            raw = await request.json()
            req = SendNotificationRequest.model_validate(raw)
        except Exception as exc:
            return _error(400, "VALIDATION_ERROR", f"payload invalido: {exc}")

        zero_uuid = UUID(int=0)
        if req.recipient_id is None or req.recipient_id == zero_uuid or req.recipient_email == "" or req.subject == "":
            return _error(400, "VALIDATION_ERROR", "recipient_id, recipient_email y subject son requeridos")
        if req.channel not in {Channel.EMAIL.value, Channel.IN_APP.value}:
            return _error(400, "VALIDATION_ERROR", "channel debe ser EMAIL o IN_APP")

        command = SendNotificationCommand(
            recipient_id=str(req.recipient_id),
            recipient_email=req.recipient_email,
            channel=Channel(req.channel),
            subject=req.subject,
            template_code=req.template_code or "",
            template_vars=req.template_vars or {},
            source_service=req.source_service or "",
            source_event_id=str(req.source_event_id) if req.source_event_id else "",
        )
        try:
            notification = await run_in_threadpool(send_use_case.handle, command)
        except Exception as exc:
            logger.exception("failed to persist notification: %s", exc)
            return _error(503, "DEPENDENCY_UNAVAILABLE", "no se pudo persistir la notificacion")
        return JSONResponse(status_code=202, content=_notification_payload(notification))

    @app.get("/notifications/{notification_id}")
    def get_notification(notification_id: str) -> JSONResponse:
        try:
            UUID(notification_id)
        except ValueError:
            return _error(400, "VALIDATION_ERROR", "id must be a valid UUID")
        try:
            notification = get_use_case.handle(GetNotificationQuery(id=notification_id))
        except NotificationNotFoundError:
            return _error(404, "NOT_FOUND", "notification not found")
        except Exception as exc:
            logger.exception("error retrieving notification: %s", exc)
            return _error(503, "DEPENDENCY_UNAVAILABLE", "error retrieving notification")
        return JSONResponse(status_code=200, content=_notification_payload(notification))

    return app


def create_health_app(readiness_checks: Mapping[str, Any], *, metrics=None, tracer=None) -> FastAPI:
    app = FastAPI(title="notification-worker-health", docs_url=None, redoc_url=None, openapi_url=None)
    _add_http_telemetry(app, metrics=metrics, tracer=tracer)
    _register_health_routes(app, dict(readiness_checks))
    return app
