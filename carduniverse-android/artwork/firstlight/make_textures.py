from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
root = Path(__file__).parent
rng = np.random.default_rng(2218)

def field(size, frequency):
    coarse = rng.normal(0,1,(frequency,frequency))
    x = (np.arange(size)/size*frequency).reshape(1, -1)
    y = x.T
    u,v=np.floor(x).astype(int),np.floor(y).astype(int)
    a,b=x-u,y-v
    a,b=a*a*(3-2*a),b*b*(3-2*b)
    return (1-b)*((1-a)*coarse[v,u]+a*coarse[v,(u+1)%frequency])+b*((1-a)*coarse[(v+1)%frequency,u]+a*coarse[(v+1)%frequency,(u+1)%frequency])

def save(name, arr):
    Image.fromarray(np.asarray(arr, dtype=np.uint8)).save(root / (name+'.png'), optimize=True)

def stone(name, palette):
    broad = field(512,7) + field(512,17)*.45
    fine = field(512,39)*.46 + field(512,85)*.32 + field(512,171)*.10 + field(512,257)*.09
    vein = np.exp(-abs(field(512,25)+broad*.52)*17.)*np.clip(broad-.32,0,1)
    pores = np.maximum(field(512,100)-.45,0)**1.3
    noise = broad*.13+fine*.12-pores*.12-vein*.23
    color = np.array(palette)[None,None,:] * (1+noise[:,:,None])
    tint = np.clip((broad*.7+fine-.55),0,1)
    color += tint[:,:,None] * np.array([-3,4,-5])
    save(name, np.clip(color,0,255))
    height= fine*.31+broad*.09-vein*.43-pores*.22
    sx = (np.roll(height,-1,1)-np.roll(height,1,1))*1.65
    sy = (np.roll(height,-1,0)-np.roll(height,1,0))*1.65
    norm = np.sqrt(1+sx*sx+sy*sy)
    save(name+'_normal',(np.dstack((-sx/norm,sy/norm,1/norm))*.5+.5)*255)

for name, palette in [('limestone',(122,117,108)),('blue_stone',(84,90,96)),('basalt',(67,67,68)),('bronze',(155,113,62))]:
    stone(name,palette)

width,height=2048,1024
lon=np.linspace(-np.pi,np.pi,width,endpoint=False)[None,:]
lat=np.linspace(np.pi/2,-np.pi/2,height)[:,None]
ya=np.maximum(np.sin(lat),0)
haze=np.exp(-abs(lat)*6)
horizon=np.exp(-((lon+.84) % (2*np.pi)-np.pi)**2*.33)*haze
sky=np.zeros((height,width,3))+np.array([10.,19.,35.])
sky+=haze[:,:,None]*np.array([43,50,58])
sky+=horizon[:,:,None]*np.array([57,26,1])
sky*=1-ya[:,:,None]*.52
cloud=(field(1024,7)+.55*field(1024,18)+.33*field(1024,43))[:,:]
cloud=np.tile(cloud,(1,2))
opacity=np.clip((cloud-.05)*.5,0,.7)*haze
sky=sky*(1-opacity[:,:,None])+opacity[:,:,None]*np.array([33,34,52])
stars=Image.fromarray(np.uint8(np.clip(sky,0,255)))
draw=ImageDraw.Draw(stars, 'RGB')
for _ in range(650):
    x=int(rng.integers(0,width))
    y=int(rng.integers(30,475))
    opacity=float(rng.uniform(.05,.50))
    ink=tuple(int(c+(245-c)*opacity) for c in stars.getpixel((x,y)))
    draw.point((x,y),fill=ink)
stars.save(root/'firstlight_sky.png', optimize=True)
