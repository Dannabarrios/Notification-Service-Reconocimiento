from fastapi.testclient import TestClient

from app.adapters.inbound.http import create_app
from app.domain.models import Channel, SendStatus, SentNotification


class FakeSendUseCase:
    def __init__(self):
        self.called = False
        self.command = None

    def handle(self, command):
        self.called = True
        self.command = command
        return SentNotification(
            id="11111111-1111-1111-1111-111111111111",
            recipient_id=command.recipient_id,
            recipient_email=command.recipient_email,
            channel=Channel.EMAIL,
            subject=command.subject,
            send_status=SendStatus.PENDING,
        )


class FakeGetUseCase:
    def handle(self, query):
        raise AssertionError("not used")


class Healthy:
    def ping(self):
        return None


def test_post_notifications_returns_202_and_maps_contract():
    send = FakeSendUseCase()
    app = create_app(send, FakeGetUseCase(), {"database": Healthy()})
    client = TestClient(app)

    response = client.post(
        "/notifications",
        json={
            "recipient_id": "22222222-2222-2222-2222-222222222222",
            "recipient_email": "a@b.co",
            "channel": "EMAIL",
            "subject": "hi",
        },
    )

    assert response.status_code == 202
    assert send.called
    assert send.command.recipient_email == "a@b.co"
    assert send.command.channel == Channel.EMAIL
    assert response.json() == {
        "id": "11111111-1111-1111-1111-111111111111",
        "recipient_id": "22222222-2222-2222-2222-222222222222",
        "channel": "EMAIL",
        "subject": "hi",
        "send_status": "PENDING",
    }


class ConfigurableGetUseCase:
    def __init__(self, result=None, error=None):
        self.result = result
        self.error = error

    def handle(self, query):
        if self.error:
            raise self.error
        return self.result


class Unhealthy:
    def ping(self):
        raise RuntimeError("db down")


def test_post_missing_fields_returns_go_validation_shape():
    send = FakeSendUseCase()
    client = TestClient(create_app(send, FakeGetUseCase()))
    response = client.post("/notifications", json={"channel": "EMAIL"})
    assert response.status_code == 400
    assert response.json() == {
        "error_code": "VALIDATION_ERROR",
        "message": "recipient_id, recipient_email y subject son requeridos",
    }
    assert not send.called


def test_post_zero_uuid_is_treated_as_missing_like_go():
    send = FakeSendUseCase()
    client = TestClient(create_app(send, FakeGetUseCase()))
    response = client.post(
        "/notifications",
        json={
            "recipient_id": "00000000-0000-0000-0000-000000000000",
            "recipient_email": "a@b.co",
            "channel": "EMAIL",
            "subject": "hi",
        },
    )
    assert response.status_code == 400
    assert response.json()["error_code"] == "VALIDATION_ERROR"
    assert not send.called


def test_post_invalid_channel_returns_400():
    send = FakeSendUseCase()
    client = TestClient(create_app(send, FakeGetUseCase()))
    response = client.post(
        "/notifications",
        json={
            "recipient_id": "22222222-2222-2222-2222-222222222222",
            "recipient_email": "a@b.co",
            "channel": "SMS",
            "subject": "hi",
        },
    )
    assert response.status_code == 400
    assert response.json()["message"] == "channel debe ser EMAIL o IN_APP"


def test_get_existing_notification_returns_200():
    notification = SentNotification(
        id="11111111-1111-1111-1111-111111111111",
        recipient_id="22222222-2222-2222-2222-222222222222",
        channel=Channel.EMAIL,
        subject="hello",
        send_status=SendStatus.SENT,
    )
    client = TestClient(create_app(FakeSendUseCase(), ConfigurableGetUseCase(result=notification)))
    response = client.get("/notifications/11111111-1111-1111-1111-111111111111")
    assert response.status_code == 200
    assert response.json()["send_status"] == "SENT"


def test_get_missing_notification_returns_404():
    from app.domain.models import NotificationNotFoundError

    client = TestClient(create_app(FakeSendUseCase(), ConfigurableGetUseCase(error=NotificationNotFoundError())))
    response = client.get("/notifications/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    assert response.status_code == 404
    assert response.json()["error_code"] == "NOT_FOUND"


def test_get_invalid_uuid_returns_400():
    client = TestClient(create_app(FakeSendUseCase(), ConfigurableGetUseCase()))
    response = client.get("/notifications/not-a-uuid")
    assert response.status_code == 400
    assert response.json()["message"] == "id must be a valid UUID"


def test_health_and_readiness_match_go_contract():
    client = TestClient(create_app(FakeSendUseCase(), FakeGetUseCase(), {"database": Healthy()}))
    assert client.get("/health").json() == {"status": "ok"}
    ready = client.get("/ready")
    assert ready.status_code == 200
    assert ready.json() == {"status": "ok", "checks": [{"name": "database", "ok": True}]}


def test_readiness_returns_503_and_sorted_checks_when_dependency_fails():
    client = TestClient(
        create_app(
            FakeSendUseCase(),
            FakeGetUseCase(),
            {"z-broker": Healthy(), "database": Unhealthy()},
        )
    )
    ready = client.get("/ready")
    assert ready.status_code == 503
    assert ready.json() == {
        "status": "degraded",
        "checks": [
            {"name": "database", "ok": False, "error": "db down"},
            {"name": "z-broker", "ok": True},
        ],
    }
