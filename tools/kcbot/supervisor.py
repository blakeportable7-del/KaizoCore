"""Runs the test bots for as long as it is left running: one job after another on one device, each watched.

    python supervisor.py [--plan plan.json] [--out C:/Users/bepor/KaizoCore-bot-shots]

A plan is a list of jobs, run in turn and then from the top again:

    [{"name": "yellow-story", "cmd": ["run_pokebot.py", "yellow"], "hours": 4},
     {"name": "firered-soak", "cmd": ["run_ironmon_soak.py", "firered-v11"], "hours": 6}]

Every minute it checks the device (adb answers, the app is running), the app's memory, the crash buffer and ANRs,
and whether the job's log is still growing. A job whose log has been quiet for 15 minutes is stopped and the next
one starts. Everything goes into `<out>/supervisor.jsonl`, and once a day `<out>/report-<date>.md` sums it up:
jobs run, crashes and freezes (with their log lines), the app's memory over the day, and the screenshots taken.

One device, one bot at a time: this is the only thing that may drive the device while it runs.
"""
import argparse
import glob
import json
import os
import subprocess
import sys
import time

from kcbot.device import Device

HERE = os.path.dirname(os.path.abspath(__file__))
PYTHON = sys.executable
QUIET_MINUTES = 15

DEFAULT_PLAN = [
    {'name': 'yellow-story', 'cmd': ['run_pokebot.py', 'yellow'], 'hours': 4},
]


class Supervisor:
    def __init__(self, plan, out, serial=None):
        self.plan, self.out = plan, out
        self.d = Device(serial)
        os.makedirs(out, exist_ok=True)
        self.day = time.strftime('%Y-%m-%d')

    def note(self, kind, **fields):
        fields.update(kind=kind, time=time.strftime('%Y-%m-%d %H:%M:%S'))
        with open(os.path.join(self.out, 'supervisor.jsonl'), 'a', encoding='utf-8') as f:
            f.write(json.dumps(fields) + '\n')
        print(kind, {k: v for k, v in fields.items() if k not in ('log',)}, flush=True)

    # ---- the device -------------------------------------------------------------------------------------------

    def device_up(self):
        r = self.d.adb('get-state', timeout=20)
        return r.returncode == 0 and b'device' in r.stdout

    def check_device(self):
        crashes = self.d.crashes()
        if 'com.ironmonone.app' in crashes:
            self.note('crash', log=crashes[-6000:])
        self.d.clear_crashes()
        for line in self.d.anrs():
            self.note('anr', line=line)
        mem = self.d.memory_kb()
        self.note('memory', kb=mem, pid=self.d.pid())

    # ---- a job ------------------------------------------------------------------------------------------------

    def run_job(self, job):
        log_path = os.path.join(self.out, f'{job["name"]}.log')
        cmd = [PYTHON, '-u'] + [os.path.join(HERE, job['cmd'][0])] + job['cmd'][1:] + ['--shots', self.out] \
            if job['cmd'][0] == 'run_pokebot.py' else [PYTHON, '-u', os.path.join(HERE, job['cmd'][0])] + job['cmd'][1:]
        self.note('job-start', job=job['name'], cmd=' '.join(cmd))
        with open(log_path, 'a', encoding='utf-8') as log:
            log.write(f'=== {time.strftime("%Y-%m-%d %H:%M:%S")} supervisor starts {job["name"]}\n')
            log.flush()
            proc = subprocess.Popen(cmd, cwd=HERE, stdout=log, stderr=subprocess.STDOUT,
                                    env=dict(os.environ, PYTHONIOENCODING='utf-8'))
        end = time.time() + job['hours'] * 3600
        last_size, last_growth = -1, time.time()
        why = 'time'
        while time.time() < end:
            time.sleep(60)
            if proc.poll() is not None:
                why = f'exited {proc.returncode}'
                break
            if not self.device_up():
                why = 'device gone'
                break
            self.check_device()
            size = os.path.getsize(log_path)
            if size != last_size:
                last_size, last_growth = size, time.time()
            elif time.time() - last_growth > QUIET_MINUTES * 60:
                why = f'log quiet {QUIET_MINUTES} min'
                break
            self.daily_report_if_due()
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(20)
            except subprocess.TimeoutExpired:
                proc.kill()
        self.note('job-end', job=job['name'], why=why)
        if why == 'device gone':
            self.note('waiting-for-device')
            while not self.device_up():
                time.sleep(60)

    def loop(self):
        while True:
            for job in self.plan:
                self.run_job(job)

    # ---- the daily report -------------------------------------------------------------------------------------

    def daily_report_if_due(self):
        today = time.strftime('%Y-%m-%d')
        if today != self.day:
            self.report(self.day)
            self.day = today

    def report(self, day):
        rows = []
        path = os.path.join(self.out, 'supervisor.jsonl')
        if os.path.exists(path):
            with open(path, encoding='utf-8') as f:
                rows = [json.loads(l) for l in f if l.startswith('{') and f'"time": "{day}' in l]
        jobs = [r for r in rows if r['kind'] in ('job-start', 'job-end')]
        bad = [r for r in rows if r['kind'] in ('crash', 'anr')]
        mem = [r['kb'] for r in rows if r['kind'] == 'memory' and r.get('kb')]
        shots = sorted(p for p in glob.glob(os.path.join(self.out, f'*{day.replace("-", "")}*.png')))
        lines = [f'# KaizoCore bots, {day}', '']
        lines += [f'- Jobs: {sum(1 for r in jobs if r["kind"] == "job-start")} started, '
                  f'ended for: {", ".join(r["why"] for r in jobs if r["kind"] == "job-end") or "none yet"}']
        lines += [f'- Crashes: {sum(1 for r in bad if r["kind"] == "crash")}, freezes (ANR): '
                  f'{sum(1 for r in bad if r["kind"] == "anr")}']
        if mem:
            lines += [f'- App memory: {min(mem) // 1024} to {max(mem) // 1024} MB, last {mem[-1] // 1024} MB']
        lines += [f'- Screenshots: {len(shots)}', '']
        for r in bad:
            lines += [f'## {r["kind"]} at {r["time"]}', '', '```', (r.get('log') or r.get('line') or '')[-3000:], '```', '']
        for s in shots:
            lines += [f'- {os.path.basename(s)}']
        with open(os.path.join(self.out, f'report-{day}.md'), 'w', encoding='utf-8') as f:
            f.write('\n'.join(lines) + '\n')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--plan')
    ap.add_argument('--out', default='C:/Users/bepor/KaizoCore-bot-shots')
    ap.add_argument('--serial')
    ap.add_argument('--report', help='write the report for this day (YYYY-MM-DD) and stop')
    a = ap.parse_args()
    plan = DEFAULT_PLAN
    if a.plan:
        with open(a.plan, encoding='utf-8') as f:
            plan = json.load(f)
    s = Supervisor(plan, a.out, a.serial)
    if a.report:
        s.report(a.report)
        return
    s.loop()


if __name__ == '__main__':
    main()
