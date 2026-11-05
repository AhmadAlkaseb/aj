# Local description drafts

Products exposes administrator-only `POST /api/v1/products/description-draft`.
The frontend's product form calls it, inserts plain text into the description field,
and requires the administrator to review and save. No price, availability or payment
field can be changed by the adapter; the adapter has no database access.

Only validated brand, model and year are sent to Ollama. Customer/payment information,
free-form prompt instructions and credentials are not forwarded. The request asks for a
bounded JSON description, validates the returned plain text and applies a 45-second model
timeout. Proxy/browser budgets allow that operation; unavailable/invalid/slow models return
a friendly error and manual text entry stays available.

The verified machine already had `llama3.2:3b` installed. Configure OLLAMA_URL and
OLLAMA_MODEL; Compose defaults to the host's local Ollama endpoint through
host.docker.internal. No paid API/account is required. `keep_alive=0` releases model memory
after the request. AI and Kubernetes can be demonstrated separately on the 16 GB machine.

`DescriptionDraftsIT` covers valid output, invalid output/input, timeout and unavailable
model. `python scripts/browser_check.py --ai` exercises an actual model through the complete
frontend/proxy/backend path. `test-results/ai-actual.json` records a real adapter result.
Model wording is variable and is not evidence that every factual claim is correct; human
approval is part of the feature.

Ollama's [structured-output API](https://github.com/ollama/ollama/blob/main/docs/api.md)
provides the JSON-schema format used by this adapter.
