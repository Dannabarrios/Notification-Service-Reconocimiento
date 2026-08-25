from app.domain.models import Recipient


class StubRecipientResolver:
    def __init__(self, email: str):
        self.email = email

    def resolve(self, entity_type: str, entity_id: str) -> Recipient:
        return Recipient(id=entity_id, email=self.email)
