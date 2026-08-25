def render_template(template: str, variables: dict[str, str] | None) -> str:
    if not variables:
        return template
    result = template
    for key, value in variables.items():
        result = result.replace("{{" + key + "}}", value)
    return result
