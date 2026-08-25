from __future__ import annotations

from dataclasses import dataclass

from opentelemetry import metrics, propagate, trace
from opentelemetry.baggage.propagation import W3CBaggagePropagator
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.metrics import Meter
from opentelemetry.propagators.composite import CompositePropagator
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator


class NotificationMetrics:
    def __init__(self, meter: Meter):
        self.http_requests_total = meter.create_counter("http.server.requests")
        self.http_request_duration = meter.create_histogram("http.server.request.duration", unit="s")
        self.notifications_delivered = meter.create_counter("notification.delivered")

    def record_http(self, method: str, route: str, status_code: int, duration_seconds: float) -> None:
        attributes = {
            "http.method": method,
            "http.route": route,
            "http.status_code": status_code,
        }
        self.http_requests_total.add(1, attributes)
        self.http_request_duration.record(duration_seconds, attributes)

    def record_delivery(self, channel: str, status: str) -> None:
        self.notifications_delivered.add(1, {"channel": channel, "status": status})


@dataclass(slots=True)
class TelemetryProviders:
    tracer_provider: TracerProvider
    meter_provider: MeterProvider
    tracer: object
    metrics: NotificationMetrics

    def shutdown(self) -> None:
        self.meter_provider.shutdown()
        self.tracer_provider.shutdown()


def setup_telemetry(service_name: str, environment: str, endpoint: str, insecure: bool) -> TelemetryProviders:
    resource = Resource.create(
        {
            "service.name": service_name,
            "deployment.environment": environment,
        }
    )

    tracer_provider = TracerProvider(resource=resource)
    tracer_provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(endpoint=endpoint, insecure=insecure)))
    trace.set_tracer_provider(tracer_provider)

    metric_exporter = OTLPMetricExporter(endpoint=endpoint, insecure=insecure)
    metric_reader = PeriodicExportingMetricReader(metric_exporter)
    meter_provider = MeterProvider(resource=resource, metric_readers=[metric_reader])
    metrics.set_meter_provider(meter_provider)

    # Match the Go service's W3C trace-context propagation across HTTP/AMQP hops.
    propagate.set_global_textmap(CompositePropagator([TraceContextTextMapPropagator(), W3CBaggagePropagator()]))

    meter = meter_provider.get_meter(service_name)
    return TelemetryProviders(
        tracer_provider=tracer_provider,
        meter_provider=meter_provider,
        tracer=tracer_provider.get_tracer(service_name),
        metrics=NotificationMetrics(meter),
    )
