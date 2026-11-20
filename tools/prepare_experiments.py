"""Freeze one source set and derive controlled experiment copies; never edits E2 source."""
import argparse, hashlib, json, subprocess, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FEATURES = ('gallery', 'audio_guide', 'itinerary', 'food')
CASES = {'E0': (False, 'install-time'), 'E1': (True, 'install-time'),
         'E2': (True, 'on-demand'), 'E2_OFF': (False, 'on-demand')}

def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args], text=True).strip()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--allow-dirty', action='store_true', help='Readiness only; records uncommitted input explicitly.')
    args = parser.parse_args()
    status = git('status', '--porcelain')
    if status and not args.allow_dirty:
        raise SystemExit('Commit the source first, or use --allow-dirty for a labelled readiness build.')
    paths = sorted(set(git('ls-files', '--cached', '--others', '--exclude-standard').splitlines()))
    source = {}
    for name in paths:
        path = ROOT / name
        if path.is_file():
            if path.suffix.lower() in {'.aab', '.apks', '.apk', '.jks', '.keystore', '.log'} or path.name in {'local.properties', '.env'}:
                raise SystemExit('Unexpected source input: ' + name)
            source[name] = path.read_bytes()
    hashes = {p: hashlib.sha256(b).hexdigest() for p, b in source.items()}
    fingerprint = hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()
    run = ROOT / '.gradle' / 'b-experiments' / (datetime.datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + fingerprint[:8])
    run.mkdir(parents=True, exist_ok=False)
    variants = {}
    expected = {f'feature/{f}/src/main/AndroidManifest.xml' for f in FEATURES}
    for case, (optimize, delivery) in CASES.items():
        variant = dict(source)
        for name in expected:
            assert variant[name].count(b'<dist:on-demand />') == 1, name
            if delivery == 'install-time':
                variant[name] = variant[name].replace(b'<dist:on-demand />', b'<dist:install-time />')
        for name, content in variant.items():
            target = run / case / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
        variants[case] = {p: hashlib.sha256(b).hexdigest() for p, b in variant.items()}
    assert variants['E0'] == variants['E1'], 'E0/E1 source must be identical'
    assert variants['E2'] == variants['E2_OFF'], 'ON/OFF on-demand source must be identical'
    assert {p for p in source if variants['E1'][p] != variants['E2'][p]} == expected
    metadata = {'base_commit': git('rev-parse', 'HEAD'), 'dirty': bool(status), 'status': status,
                'source_sha256': fingerprint, 'source_files': hashes,
                'cases': {c: {'sizeOptimization': o, 'delivery': d, 'files': variants[c]} for c, (o, d) in CASES.items()},
                'comparison_checks': 'PASS: E0/E1 identical source; E1/E2 only four delivery tags differ; E2_OFF/E2 identical source',
                'signing': 'Unchanged release configuration; use the same explicit APK signing credentials for every case.',
                'device_spec': 'Member D must supply the SAME actual device-spec.json for all measurements.'}
    (run / 'snapshot.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print(run)

if __name__ == '__main__':
    main()
