import re,subprocess,sys,time
def dump():
    subprocess.run(["adb","shell","uiautomator","dump","/sdcard/u.xml"],capture_output=True)
    return subprocess.run(["adb","shell","cat","/sdcard/u.xml"],capture_output=True,text=True).stdout
def find(x,want):
    nth = 1
    # "=PLAY>>FireRed_Hack": the first match AFTER the anchor text in tree order
    if ">>" in want:
        want, anchor = want.split(">>",1)
        m = re.search(r'text="\s*' + re.escape(anchor), x)
        if not m: return None
        x = x[m.start():]
    if "#" in want: want, n = want.rsplit("#",1); nth = int(n)
    for m in re.finditer(r'(?:text|content-desc)="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        t = m.group(1).strip().lower()
        w = want.lower()
        hit = (t == w[1:]) if w.startswith("=") else (w in t)
        if hit:
            nth -= 1
            if nth: continue
            a,b,c,d = map(int,m.groups()[1:])
            return (a+c)//2,(b+d)//2,m.group(1).strip()
    return None
def texts():
    return sorted(set(re.findall(r'text="([^"]+)"', dump())))
if sys.argv[1]=="ls":
    for t in texts(): print(t)
    sys.exit()
for want in sys.argv[1:]:
    r=find(dump(),want)
    if not r: print("NOT FOUND:",want); sys.exit(1)
    x,y,label=r
    subprocess.run(["adb","shell","input","tap",str(x),str(y)],capture_output=True)
    print("tapped %r" % label); time.sleep(2.5)
