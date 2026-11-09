"""jfr_query.py <collapsed file from JfrCollapse.java> [--thread Server] [--within Method] [--rows 40] [--self] [--prefix x] [--callers M] [--children M]"""
import argparse, collections
p = argparse.ArgumentParser()
p.add_argument('path'); p.add_argument('--thread', default='Server thread'); p.add_argument('--within'); p.add_argument('--rows', type=int, default=40)
p.add_argument('--prefix'); p.add_argument('--callers'); p.add_argument('--children')
a = p.parse_args()
tot = collections.Counter(); selfc = collections.Counter(); n = 0; callers = collections.Counter(); children = collections.Counter()
for line in open(a.path, encoding='utf-8'):
    c, rest = line.rstrip('\n').split(' ', 1)
    c = int(c)
    parts = rest.split(';')
    if parts[0] != a.thread:
        continue
    fr = parts[1:]
    if a.within:
        idx = [i for i, f in enumerate(fr) if f.endswith(a.within)]
        if not idx:
            continue
        fr = fr[idx[0]:]
    n += c
    if fr:
        selfc[fr[-1]] += c
    for f in set(fr):
        if a.prefix is None or a.prefix in f:
            tot[f] += c
    if a.callers:
        for i, f in enumerate(fr):
            if f.endswith(a.callers) and i > 0:
                callers[fr[i-1]] += c; break
    if a.children:
        for i, f in enumerate(fr):
            if f.endswith(a.children):
                children[fr[i+1] if i + 1 < len(fr) else '(self)'] += c; break
print('samples', n)
if a.callers:
    for f, c in callers.most_common(a.rows): print('%6.2f%% %s' % (100.0*c/n, f))
elif a.children:
    for f, c in children.most_common(a.rows): print('%6.2f%% %s' % (100.0*c/n, f))
else:
    print('--- total'); [print('%6.2f%% %s' % (100.0*c/n, f)) for f, c in tot.most_common(a.rows)]
    print('--- self'); [print('%6.2f%% %s' % (100.0*c/n, f)) for f, c in selfc.most_common(a.rows // 2)]
