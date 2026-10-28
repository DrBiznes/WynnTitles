"""Draws the hexagonal achievement icon frames (bronze, silver, gold) and the
locked shade as pixel art, no image library needed.

    python3 scripts/draw-icon-frames.py mod/src/main/resources/assets/wynntitles/textures/gui/sprites/icon [--preview out.png]

The item sits in the 16x16 box at (6, 7); AchievementIcon.java must match W, H
and that offset.
"""
import zlib,struct,sys,math
W,H=28,32; CX,CY=14.0,15.5; R0=15.5
S3=math.sqrt(3)
def png(path,px,w,h):
    raw=b''.join(b'\0'+bytes(c for p in px[y*w:(y+1)*w] for c in p) for y in range(h))
    def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    open(path,'wb').write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def c(s,a=255): return (int(s[1:3],16),int(s[3:5],16),int(s[5:7],16),a)
def hexd(dx,dy):  # pointy-top hexagon "radius" of a point
    return max(abs(dx)*2/S3, abs(dx)/S3+abs(dy))
OUT=c('#140d08'); FILL=c('#221d15'); FILL_LO=c('#1b1710')
WOOD={0:c('#8a5a3a'),5:c('#8a5a3a'),1:c('#6a4329'),4:c('#7a4e32'),2:c('#57361f'),3:c('#57361f')}
WOOD_HI=c('#a06c47')
TIERS={'bronze':(c('#f0b07a'),c('#c47c42'),c('#8e5226')),
       'silver':(c('#ffffff'),c('#c4c8d2'),c('#878b97')),
       'gold':  (c('#fff0a0'),c('#e9bd3a'),c('#b0801c'))}
RING_OUT, RING_IN, FILL_R = R0-1, R0-4, R0-5   # outline 1px, wood 3px, inner outline 1px
BCX,BCY,BR=14.0,27.5,4.5                          # badge hex
mid=(RING_OUT+RING_IN)/2
verts=[(CX+mid*math.cos(math.radians(a)),CY+mid*math.sin(math.radians(a))) for a in (-90,-30,30,90,150,210)]
def frame(tier):
    hi,metal,lo=TIERS[tier]; px=[(0,0,0,0)]*(W*H)
    for y in range(H):
        for x in range(W):
            px_,py_=x+.5,y+.5; dx,dy=px_-CX,py_-CY; h=hexd(dx,dy)
            col=None
            if h<=FILL_R: col=FILL if dy<2 else FILL_LO
            elif h<=RING_IN: col=OUT
            elif h<=RING_OUT:
                vd=min(math.hypot(px_-vx,py_-vy) for vx,vy in verts)
                if vd<2.4: col=hi if dy<0 and dx<0 else metal if dy<=0 else lo
                elif vd<3.2: col=OUT
                else:
                    side=int(((math.degrees(math.atan2(dy,dx))+90)%360)//60)
                    col=WOOD[side]
                    if h>RING_OUT-1 and side in(5,0,4): col=WOOD_HI
            elif h<=R0: col=OUT
            bd=hexd(px_-BCX,py_-BCY)
            if bd<=BR:
                if bd>BR-1: col=OUT
                else: col=hi if ((x-y)%5 in (0,1)) else metal if py_<BCY+1 else lo
            if col: px[y*W+x]=col
    return px
def shade():
    px=[(0,0,0,0)]*(W*H)
    for y in range(H):
        for x in range(W):
            if hexd(x+.5-CX,y+.5-CY)<=RING_IN: px[y*W+x]=c('#140d08',176)
    return px
d=sys.argv[1]
for t in TIERS: png(f'{d}/frame_{t}.png',frame(t),W,H)
png(f'{d}/locked_shade.png',shade(),W,H)

if '--preview' not in sys.argv: sys.exit()
out=sys.argv[sys.argv.index('--preview')+1]
# preview sheet at 8x on gray, with the item box in magenta
S=8; tiers=list(TIERS); PW=len(tiers)*(W*S+16)+16; PH=H*S+32
sheet=[[(70,70,70,255)]*PW for _ in range(PH)]
for k,t in enumerate(tiers):
    f=frame(t); ox=16+k*(W*S+16)
    for y in range(H*S):
        for x in range(W*S):
            p=f[(y//S)*W+x//S]
            if p[3]: sheet[16+y][ox+x]=p
    # item box outline for reference (16x16 at item offset)
    ix,iy=6,7
    for i in range(16*S):
        for (xx,yy) in [(ix*S+i,iy*S),(ix*S+i,(iy+16)*S-1),(ix*S,iy*S+i),((ix+16)*S-1,iy*S+i)]:
            sheet[16+yy][ox+xx]=(255,0,255,255)
flat=[p for r in sheet for p in r]
png(out,flat,PW,PH)
