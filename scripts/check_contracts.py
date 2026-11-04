"""Validate generated contracts with the standard OpenAPI validator."""
import json
from pathlib import Path
from openapi_spec_validator import validate
ROOT=Path(__file__).resolve().parent.parent
for path in (ROOT/'documentation/contracts/openapi').glob('*.json'):
    validate(json.loads(path.read_text(encoding='utf-8')))
print('OpenAPI validation passed')
