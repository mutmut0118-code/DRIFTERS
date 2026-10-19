"""Where a thread's time went, from a Java Flight Recorder profile (see the "jfr" line in smoke.py; tools/fine.jfc samples
every millisecond).

Usage: python tools/jfrtop.py <file.jfr> [--thread "Server thread"] [--rows 40] [--prefix com/dinglebob]
                              [--within DrifterEntity.tick] [--callers PathSearch.clear]
Prints, as a share of the thread's samples (or, with --within, of the samples inside that method): the methods it was in
at the moment of each sample ("self"), and the methods anywhere on the stack ("total", each counted once per sample).
--prefix limits the "total" table to those methods; --callers prints who calls a method instead. Needs the JDK's "jfr"
tool on the PATH.
"""
import argparse
import collections
import json
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('path')
parser.add_argument('--thread', default='Server thread')
parser.add_argument('--rows', type=int, default=40)
parser.add_argument('--prefix')
parser.add_argument('--within')
parser.add_argument('--callers')
args = parser.parse_args()

raw = subprocess.run(['jfr', 'print', '--json', '--stack-depth', '128', '--events', 'jdk.ExecutionSample', args.path],
                     capture_output=True, text=True, encoding='utf-8', errors='replace').stdout
events = json.loads(raw)['recording']['events']


def name(frame):
    method = frame['method']
    return method['type']['name'] + '.' + method['name']


chains = collections.Counter()
self_counts = collections.Counter()
total_counts = collections.Counter()
samples = 0
all_samples = 0
for event in events:
    values = event['values']
    sampled = values.get('sampledThread') or {}
    if sampled.get('javaName') != args.thread and sampled.get('osName') != args.thread:
        continue
    frames = (values.get('stackTrace') or {}).get('frames') or []
    if not frames:
        continue
    all_samples += 1
    names = [name(f) for f in frames]
    if args.within and not any(args.within in n for n in names):
        continue
    samples += 1
    self_counts[names[0]] += 1
    if args.callers:
        for i, n in enumerate(names):
            if args.callers in n:
                chains[' <- '.join(x.rsplit('/', 1)[-1] for x in names[i:i + 6])] += 1
                break
    for method in set(names):
        if args.prefix is None or method.startswith(args.prefix):
            total_counts[method] += 1

print('%d samples on "%s"%s' % (samples, args.thread,
                                '' if not args.within else ' inside %s (%.1f%% of its %d)' % (args.within, 100.0 * samples / max(1, all_samples), all_samples)))
if samples == 0:
    raise SystemExit
if args.callers:
    for chain, count in chains.most_common(args.rows):
        print('%5.1f%%  %s' % (100.0 * count / samples, chain))
    raise SystemExit
print('\n-- self (where it actually was) --')
for method, count in self_counts.most_common(args.rows):
    print('%5.1f%%  %s' % (100.0 * count / samples, method))
print('\n-- total (anywhere on the stack)%s --' % ('' if args.prefix is None else ', ' + args.prefix + ' only'))
for method, count in total_counts.most_common(args.rows):
    print('%5.1f%%  %s' % (100.0 * count / samples, method))
