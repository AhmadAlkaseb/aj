"""Distinguish intentional path-based skips from blocked or missing required jobs."""
import argparse
import json
import os
from pathlib import Path


def evaluate(expected, results):
    errors = []
    rows = []
    for name in results.keys() - expected.keys():
        errors.append(name + ': no declared gate policy')
    for name, required in expected.items():
        result = results.get(name, {}).get('result', 'missing')
        accepted = result == 'success' or (result == 'skipped' and not required)
        if not accepted:
            errors.append(name + ': ' + result)
        reason = 'Required track' if required else 'No matching changes / optional track'
        rows.append((name, result, reason, accepted))
    return rows, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected', default=os.environ.get('EXPECTED', '{}'))
    parser.add_argument('--results', default=os.environ.get('RESULTS', '{}'))
    parser.add_argument('--title', default='Pipeline gate')
    args = parser.parse_args()
    rows, errors = evaluate(json.loads(args.expected), json.loads(args.results))
    lines = ['## ' + args.title, '', '| Stage | Result | Policy | Accepted |', '| --- | --- | --- | --- |']
    lines += [f'| {name} | {result} | {reason} | {"yes" if ok else "NO"} |' for name, result, reason, ok in rows]
    lines += ['', '**' + ('BLOCKED: ' + ', '.join(errors) if errors else 'PASS · every selected stage completed successfully') + '**', '']
    output = '\n'.join(lines)
    print(output)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as out:
            out.write(output)
    if errors:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
