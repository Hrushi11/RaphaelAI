"""Regenerate protocol/config.schema.json from the pydantic config model.

    python tools/export_schema.py
"""

import json
from pathlib import Path

from raphael.protocol import RaphaelConfig

OUT = Path(__file__).resolve().parents[2] / "protocol" / "config.schema.json"

schema = RaphaelConfig.model_json_schema()
schema = {"$schema": "https://json-schema.org/draft/2020-12/schema", "title": "RaphaelConfig", **schema}
OUT.write_text(json.dumps(schema, indent=2) + "\n")
print(f"wrote {OUT}")
