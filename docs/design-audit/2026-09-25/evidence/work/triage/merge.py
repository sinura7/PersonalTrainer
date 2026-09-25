import re, glob, os, sys
S="/tmp/claude-0/-home-user-PersonalTrainer/cf46fb84-1fb9-5154-8302-bc82a0b34c05/scratchpad"
files=sorted(glob.glob(f"{S}/passes/B*.md"), key=lambda p: (len(os.path.basename(p)), os.path.basename(p)))
def sections(text):
    # split on '## N.' headings
    parts=re.split(r'^## (\d+)\.', text, flags=re.M)
    out={}
    for i in range(1,len(parts),2):
        out[parts[i]]=parts[i+1]
    return out
def rows(sec):
    r=[]
    for line in sec.splitlines():
        if line.startswith('|') and not re.match(r'^\|\s*-', line) and not re.match(r'^\|\s*ID\s*\|', line, re.I):
            cells=[c.strip() for c in line.strip().strip('|').split('|')]
            if len(cells)>=4 and re.match(r'^[A-Z]{1,2}-\d+', cells[0]):
                r.append(cells)
    return r
p12=[];p3=[];prior=[];counts=[]
for f in files:
    name=os.path.basename(f)[:-3]
    t=open(f,encoding='utf-8',errors='replace').read()
    sec=sections(t)
    r2=rows(sec.get('2','')); r3=rows(sec.get('3',''))
    for c in r2: p12.append((name,c))
    for c in r3: p3.append((name,c))
    # prior table rows in §1
    for line in sec.get('1','').splitlines():
        if line.startswith('|') and not re.match(r'^\|\s*-', line) and not re.match(r'^\|\s*Prior', line, re.I):
            prior.append((name,line.strip()))
    counts.append((name, len(t.splitlines()), len(r2), len(r3)))
with open(f"{S}/triage/merged.md","w") as o:
    o.write("# Merged findings (auto-extracted)\n\n## Report sizes\n| Pass | lines | §2 rows | §3 rows |\n|---|---|---|---|\n")
    for n,l,a,b in counts: o.write(f"| {n} | {l} | {a} | {b} |\n")
    o.write(f"\nTotal §2 (P1/P2): {len(p12)} · §3 (P3): {len(p3)} · prior rows: {len(prior)}\n\n## §2 rows (P1/P2)\n\n| Pass | ID | Sev | Type | C/R | Prior | Files | Claim | Plain | Probe |\n|---|---|---|---|---|---|---|---|---|---|\n")
    for n,c in p12:
        c=(c+['']*10)[:10]
        o.write("| "+n+" | "+" | ".join(x.replace('\n',' ') for x in c)+" |\n")
    o.write("\n## §3 rows (P3)\n\n| Pass | ID | Sev | Type | C/R | Prior | Files | Claim |\n|---|---|---|---|---|---|---|---|\n")
    for n,c in p3:
        c=(c+['']*8)[:8]
        o.write("| "+n+" | "+" | ".join(x for x in c[:8])+" |\n")
    o.write("\n## §1 prior-status rows\n\n")
    for n,l in prior: o.write(f"{n}: {l}\n")
print(open(f"{S}/triage/merged.md").read()[:1200])
