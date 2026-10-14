"""Headless smoke test: runs the dev server, feeds console commands with delays, logs everything.

Usage: python smoke.py <project_dir> <script_file> <log_file>
Script lines: "wait <seconds>", "until <text> <timeout>" (underscores in <text> stand for spaces), "sprint <ticks>"
(run that many game ticks as fast as possible with /tick sprint and wait for it to finish), or a console command. Start scripts with "until Done_( 900" (server ready) and "forceload add ~-112 ~-112 ~112 ~112": with no player
online only force-loaded chunks tick, so Drifters elsewhere freeze. Spawn Drifters apart ("execute positioned ~40 ~ ~
run drifter spawn Bob"): /drifter commands act on the nearest one. Run from the project root; the world is run/world.
Local sessions only (never in the cloud).
"""
import subprocess
import sys
import threading
import time

project, script, log_path = sys.argv[1], sys.argv[2], sys.argv[3]
log = open(log_path, 'w', encoding='utf-8', errors='replace')
lines = []
lock = threading.Lock()

proc = subprocess.Popen(['cmd', '/c', __import__('os').path.join(project, 'gradlew.bat'), 'runServer', '--console=plain'], cwd=project,
                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)


def reader():
    for raw in proc.stdout:
        text = raw.decode('utf-8', errors='replace')
        with lock:
            lines.append(text)
            log.write(text)
            log.flush()


threading.Thread(target=reader, daemon=True).start()


def seen(text, start):
    with lock:
        return any(text in l for l in lines[start:])


def send(cmd):
    with lock:
        log.write('>>> ' + cmd + '\n')
        log.flush()
    proc.stdin.write((cmd + '\n').encode())
    proc.stdin.flush()


for line in open(script, encoding='utf-8'):
    line = line.strip()
    if not line or line.startswith('#'):
        continue
    if proc.poll() is not None:
        break
    if line.startswith('wait '):
        time.sleep(float(line.split()[1]))
    elif line.startswith('sprint '):
        # Runs that many game ticks as fast as the machine can (/tick sprint), then waits for the server to say it's
        # done. Same ticks in the same order as normal play, just no waiting between them (user's idea, session 2).
        with lock:
            start = len(lines)
        send('tick sprint ' + line.split()[1])
        end = time.time() + 900
        while time.time() < end and not seen('Sprint completed', start) and proc.poll() is None:
            time.sleep(0.5)
    elif line.startswith('until '):
        _, text, timeout = line.split(' ', 2)
        text = text.replace('_', ' ')
        end = time.time() + float(timeout)
        while time.time() < end and not seen(text, 0) and proc.poll() is None:
            time.sleep(1)
    else:
        send(line)

try:
    send('stop')
    proc.wait(timeout=120)
except Exception:
    proc.kill()
log.write('\n[smoke] exit code %s\n' % proc.returncode)
log.close()
