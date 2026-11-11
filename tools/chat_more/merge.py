"""Adds new lines to the canned chat pools (tools/chat_more/pools.py), checked, without reformatting the files.

Usage: python tools/chat_more/merge.py <project dir> <additions.json>... [--dry]

An additions file: {"<pool id>": {"<voice>": ["line", ...]}, ...} (or "<pool id>": [lines] for everyone's).
Each new line is checked: plain ASCII, not too long, only fill words the pool already uses (and the ones every one of
its lines has, except {player}/{name}), no banned words, not a repeat or near-repeat of a line already in the pool
(any voice). Rejected lines are listed; the rest are spliced into the JSON text as it is (inline lists stay inline,
one-per-line lists stay one per line).
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
import pools as P  # noqa: E402

MAX_LEN = 110
NEVER_REQUIRED = {'player', 'name'}
# From drifter_names' banned_parts, less the ones that are ordinary in crime and war lines (murder, kill).
BANNED = ['fuck', 'shit', 'cunt', 'nigg', 'fag', 'rape', 'nazi', 'hitler', 'kkk', 'porn', 'sex', 'cock', 'dick', 'pussy',
          'whore', 'slut', 'bitch', 'retard', 'tranny', 'chink', 'kike', 'coon', 'wank', 'twat', 'penis', 'vagina', 'anal',
          'cum', 'tits', 'boob', 'nude', 'hentai', 'isis', 'jihad', 'suicide', 'damn', 'god', 'jesus', 'lord', 'hell']
# Whole words only for the short ones ('cum' in 'cucumber', 'anal' in 'canal', 'god' in 'godly' is fine to skip).
BANNED_WORD = {'cum', 'anal', 'sex', 'coon', 'god', 'lord', 'hell', 'tits', 'isis', 'kkk', 'fag', 'dick', 'cock', 'damn'}


def norm(line):
    """For repeat checks: lowercase, fill words out, letters only, stretched letters once."""
    s = P.WORD.sub(' ', line.lower())
    s = re.sub(r"[^a-z0-9 ]", ' ', s)
    s = re.sub(r'(.)\1+', r'\1', s)
    return ' '.join(s.split())


def banned(line):
    low = line.lower()
    for b in BANNED:
        if b in BANNED_WORD:
            if re.search(r'(?<![a-z])' + re.escape(b) + r'(?![a-z])', low):
                return b
        elif b in low:
            return b
    return None


def check(pool, line, seen):
    if not isinstance(line, str) or not line.strip():
        return 'empty'
    if line != line.strip():
        return 'spaces'
    if any(ord(c) > 126 or ord(c) < 32 for c in line):
        return 'not plain ascii'
    if len(line) > MAX_LEN:
        return 'too long'
    w = P.words(line)
    extra = w - pool.optional
    if extra:
        return 'fill words the pool never uses: ' + ','.join(sorted(extra))
    missing = (pool.required - NEVER_REQUIRED) - w
    if missing:
        return 'missing fill words: ' + ','.join(sorted(missing))
    b = banned(line)
    if b:
        return 'banned word: ' + b
    n = norm(line)
    if not n:
        n = line  # punctuation-only lines (?, ...) compare as they are
    if n in seen:
        return 'repeat of: ' + seen[n]
    key = ' '.join(sorted(n.split()))
    if ('#' + key) in seen and len(n.split()) > 2:
        return 'same words as: ' + seen['#' + key]
    return None


# ---------------------------------------------------------------------------------------------
# A JSON reader that remembers where each value is in the text
# ---------------------------------------------------------------------------------------------

class Span:
    def __init__(self, kind, start, end, children):
        self.kind, self.start, self.end, self.children = kind, start, end, children  # children: dict or list of Span


def parse(text):
    dec = json.JSONDecoder()
    ws = re.compile(r'\s*')

    def skip(i):
        return ws.match(text, i).end()

    def value(i):
        i = skip(i)
        c = text[i]
        if c == '{':
            kids = {}
            j = skip(i + 1)
            if text[j] == '}':
                return Span('obj', i, j + 1, kids)
            while True:
                key, j = dec.raw_decode(text, skip(j))
                j = skip(j)
                assert text[j] == ':'
                kids[key] = value(j + 1)
                j = skip(kids[key].end)
                if text[j] == ',':
                    j += 1
                    continue
                assert text[j] == '}', text[j - 40:j + 40]
                return Span('obj', i, j + 1, kids)
        if c == '[':
            kids = []
            j = skip(i + 1)
            if text[j] == ']':
                return Span('arr', i, j + 1, kids)
            while True:
                kids.append(value(j))
                j = skip(kids[-1].end)
                if text[j] == ',':
                    j += 1
                    continue
                assert text[j] == ']'
                return Span('arr', i, j + 1, kids)
        _, j = dec.raw_decode(text, i)
        return Span('val', i, j, None)

    return value(0)


def at(span, path):
    for p in path:
        span = span.children[p]
    return span


def line_indent(text, pos):
    start = text.rfind('\n', 0, pos) + 1
    m = re.match(r'[ \t]*', text[start:])
    return m.group(0)


def array_insert(text, arr, new, nl):
    """Text to put just before the array's closing bracket, adding these strings."""
    items = [json.dumps(s, ensure_ascii=False) for s in new]
    if not arr.children:
        return arr.end - 1, ', '.join(items)
    last = arr.children[-1]
    one_line = '\n' not in text[arr.start:arr.end]
    if one_line:
        # Inline: keep going inline, wrapped onto new lines indented one more than the line the list starts on.
        ind = line_indent(text, arr.start)
        step = '\t' if ind.startswith('\t') or ('\t' in text[:2000] and '  ' not in text[:200]) else '  '
        pad = ind + step + step
        out, cur = '', ''
        for it in items:
            if cur and len(pad) + len(cur) + len(it) + 2 > 130:
                out += ',' + nl + pad + cur.rstrip(', ').rstrip(',')
                cur = ''
            cur += it + ', '
        if cur:
            out += ',' + nl + pad + cur.rstrip(', ')
        return last.end, out
    ind = line_indent(text, last.start)
    return last.end, ''.join(',' + nl + ind + it for it in items)


def object_insert(text, obj, key, new, nl):
    """Text adding '"key": [new]' to an object (a voice the key didn't have)."""
    items = ', '.join(json.dumps(s, ensure_ascii=False) for s in new)
    if not obj.children:
        return obj.end - 1, json.dumps(key) + ': [' + items + ']'
    last = list(obj.children.values())[-1]
    one_line = '\n' not in text[obj.start:obj.end]
    if one_line:
        return last.end, ', ' + json.dumps(key) + ': [' + items + ']'
    ind = line_indent(text, last.start)
    first = list(obj.children.values())[0]
    multi = '\n' in text[first.start:first.end]
    if multi:
        step = ind + ('\t' if ind.startswith('\t') else '  ')
        body = ('[' + ''.join(nl + step + json.dumps(s, ensure_ascii=False) + ',' for s in new)).rstrip(',') + nl + ind + ']'
    else:
        body = '[' + items + ']'
    # key line: find the indent of the last key (the line its value starts on)
    return last.end, ',' + nl + ind + json.dumps(key) + ': ' + body


def main(argv):
    dry = '--dry' in argv
    args = [a for a in argv if a != '--dry']
    root, files = args[0], args[1:]
    pools = {p.id: p for p in P.all_pools(root)}
    adds = {}
    for f in files:
        for pid, v in json.load(open(f, encoding='utf-8')).items():
            if isinstance(v, list):
                v = {'any': v}
            for voice, lines in v.items():
                adds.setdefault(pid, {}).setdefault(voice, []).extend(lines)

    rejected, accepted, by_file = [], {}, {}
    for pid, voices in adds.items():
        pool = pools.get(pid)
        if pool is None:
            rejected.append((pid, '-', '-', 'no such pool'))
            continue
        seen = {}
        for vlines in pool.voices.values():
            for l in vlines:
                n = norm(l) or l
                seen.setdefault(n, l)
                seen.setdefault('#' + ' '.join(sorted(n.split())), l)
        for voice, lines in voices.items():
            if pid.startswith('whim:') and voice != 'any':
                rejected.append((pid, voice, '*', 'whims have no voices'))
                continue
            if voice not in P.VOICES:
                rejected.append((pid, voice, '*', 'unknown voice'))
                continue
            for line in lines:
                why = check(pool, line, seen)
                if why:
                    rejected.append((pid, voice, line, why))
                    continue
                n = norm(line) or line
                seen[n] = line
                seen['#' + ' '.join(sorted(n.split()))] = line
                accepted.setdefault(pid, {}).setdefault(voice, []).append(line)
        if pid in accepted:
            by_file.setdefault(pool.file, []).append(pid)

    for pid, voice, line, why in rejected:
        print(f'REJECT {pid} [{voice}] {line!r}: {why}')

    total = 0
    for rel, pids in by_file.items():
        path = os.path.join(root, rel)
        text = open(path, encoding='utf-8', newline='').read()
        nl = '\r\n' if '\r\n' in text else '\n'
        tree = parse(text)
        edits = []
        for pid in pids:
            pool = pools[pid]
            for voice, lines in accepted[pid].items():
                total += len(lines)
                if pid.startswith('whim:'):
                    edits.append(array_insert(text, at(tree, pool.path), lines, nl))
                    continue
                node = at(tree, pool.path)
                if node.kind == 'arr':
                    if voice != 'any':
                        raise SystemExit(f'{pid}: a plain list, voices need it as an object first')
                    edits.append(array_insert(text, node, lines, nl))
                elif voice in node.children:
                    edits.append(array_insert(text, node.children[voice], lines, nl))
                else:
                    edits.append(object_insert(text, node, voice, lines, nl))
        for pos, ins in sorted(edits, key=lambda e: -e[0]):
            text = text[:pos] + ins + text[pos:]
        json.loads(text)  # still JSON
        if not dry:
            open(path, 'w', encoding='utf-8', newline='').write(text)
    print(f'{"would add" if dry else "added"} {total} lines to {sum(len(v) for v in by_file.values())} pools; rejected {len(rejected)}')


if __name__ == '__main__':
    main(sys.argv[1:])
