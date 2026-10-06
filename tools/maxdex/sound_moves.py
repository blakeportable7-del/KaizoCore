"""MaxDex 1.0's sound moves: runs its check at 0x081C80BC (a switch on the move id, returning nonzero for a sound move)
on every move id with a minimal Thumb interpreter, and prints the ids MaxDexMoveTypes.SOUND holds (Liquid Voice).

    python tools/maxdex/sound_moves.py <firered-maxdex.gba> <tracker-gba/src/main/resources/maxdex/moves.tsv>
"""
import struct,sys
d=open(sys.argv[1],"rb").read()
def h(a): return struct.unpack_from("<H",d,a-0x08000000)[0]
def w(a): return struct.unpack_from("<I",d,a-0x08000000)[0]
def run(entry,r0):
    r=[0]*16; r[0]=r0; pc=entry; N=Z=C=V=0; steps=0
    def setnz(x): 
        nonlocal N,Z; x&=0xFFFFFFFF; N=x>>31; Z=int(x==0); return x
    def sub(a,b):
        nonlocal N,Z,C,V
        res=(a-b)&0xFFFFFFFF; N=res>>31; Z=int(res==0); C=int(a>=b)
        V=int(((a^b)&(a^res))>>31&1); return res
    while steps<500:
        steps+=1; i=h(pc); nxt=pc+2
        if (i&0xFE00)==0xB400 or (i&0xFE00)==0xB500: pass  # push
        elif (i&0xFE00)==0xBC00 or (i&0xFE00)==0xBD00: return r[0]  # pop -> return
        elif (i&0xFF80)==0x4700: return r[0]  # bx
        elif (i&0xF800)==0x0000: rd=i&7; rs=(i>>3)&7; sh=(i>>6)&31; r[rd]=setnz(r[rs]<<sh)
        elif (i&0xF800)==0x0800: rd=i&7; rs=(i>>3)&7; sh=(i>>6)&31 or 32; r[rd]=setnz(r[rs]>>sh)
        elif (i&0xFE00)==0x1C00: rd=i&7; rs=(i>>3)&7; imm=(i>>6)&7; r[rd]=setnz(r[rs]+imm)
        elif (i&0xFE00)==0x1E00: rd=i&7; rs=(i>>3)&7; imm=(i>>6)&7; r[rd]=sub(r[rs],imm)
        elif (i&0xFE00)==0x1800: rd=i&7; rs=(i>>3)&7; rn=(i>>6)&7; r[rd]=setnz(r[rs]+r[rn])
        elif (i&0xFE00)==0x1A00: rd=i&7; rs=(i>>3)&7; rn=(i>>6)&7; r[rd]=sub(r[rs],r[rn])
        elif (i&0xF800)==0x2000: rd=(i>>8)&7; r[rd]=setnz(i&0xFF)
        elif (i&0xF800)==0x2800: rd=(i>>8)&7; sub(r[rd],i&0xFF)
        elif (i&0xF800)==0x3000: rd=(i>>8)&7; r[rd]=setnz(r[rd]+(i&0xFF))
        elif (i&0xF800)==0x3800: rd=(i>>8)&7; r[rd]=sub(r[rd],i&0xFF)
        elif (i&0xFFC0)==0x4280: sub(r[i&7],r[(i>>3)&7])
        elif (i&0xF800)==0x4800: rd=(i>>8)&7; r[rd]=w(((pc+4)&~3)+(i&0xFF)*4)
        elif (i&0xF000)==0xD000 and (i&0x0F00)!=0x0F00:
            cond=(i>>8)&0xF; off=i&0xFF; off= off-256 if off&0x80 else off
            t={0:Z,1:1-Z,2:C,3:1-C,4:N,5:1-N,10:int(N==V),11:int(N!=V),12:int(Z==0 and N==V),13:int(Z==1 or N!=V),8:int(C and not Z),9:int((not C) or Z)}[cond]
            if t: nxt=pc+4+off*2
        elif (i&0xF800)==0xE000:
            off=i&0x7FF; off= off-2048 if off&0x400 else off; nxt=pc+4+off*2
        else: raise Exception("op %04x at %x"%(i,pc))
        pc=nxt
    raise Exception("loop")
res=[m for m in range(1,842) if run(0x081c80bc,m)&0xFF]
names={}
for l in open(sys.argv[2],encoding="utf-8"):
    a,b=l.rstrip("\n").split("\t",1); names[int(a)]=b
print(len(res), [(m,names.get(m)) for m in res])
