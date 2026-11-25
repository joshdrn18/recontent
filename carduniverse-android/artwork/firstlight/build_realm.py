"""Generate the original Firstlight environments and their glTF exports."""
import argparse
import math
import random
import sys
from collections import defaultdict
from pathlib import Path

import bpy
from mathutils import Vector
from mathutils.noise import fractal

ROOT = Path(__file__).parent
TAU = math.tau
parser = argparse.ArgumentParser()
parser.add_argument('output', type=Path)
parser.add_argument('scene', choices=['gate', 'arena', 'tower'])
parser.add_argument('--preview', action='store_true')
opts = parser.parse_args(sys.argv[sys.argv.index('--') + 1:])
opts.output.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
rng = random.Random(2207)
material_objects = defaultdict(list)


def material(name, color, roughness, metal=0, texture=None, emission=0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    tree = mat.node_tree
    shader = tree.nodes.get('Principled BSDF')
    shader.inputs['Base Color'].default_value = (*color, 1)
    shader.inputs['Roughness'].default_value = roughness
    shader.inputs['Metallic'].default_value = metal
    if texture:
        image = tree.nodes.new('ShaderNodeTexImage')
        image.image = bpy.data.images.load(str(ROOT / (texture+'.png')), check_existing=True)
        tree.links.new(image.outputs['Color'], shader.inputs['Base Color'])
        normal = tree.nodes.new('ShaderNodeTexImage')
        normal.image = bpy.data.images.load(str(ROOT / (texture+'_normal.png')), check_existing=True)
        normal.image.colorspace_settings.name = 'Non-Color'
        bump = tree.nodes.new('ShaderNodeNormalMap')
        bump.inputs['Strength'].default_value = 0.73
        tree.links.new(normal.outputs['Color'], bump.inputs['Color'])
        tree.links.new(bump.outputs['Normal'], shader.inputs['Normal'])
    if emission:
        shader.inputs['Emission Color'].default_value = (*color, 1)
        shader.inputs['Emission Strength'].default_value = emission
    return mat


STONE = material('Weathered granite', (.33, .33, .36), .81, texture='blue_stone')
TRIM = material('Carved limestone', (.46, .43, .38), .68, texture='limestone')
BASALT = material('Basalt', (.19, .19, .2), .91, texture='basalt')
BRONZE = material('Weathered bronze', (.51, .33, .17), .43, .71, 'bronze')
LAND = material('Glacial bedrock', (.16, .19, .23), .95, texture='blue_stone')
TEAL = material('Aether glass', (.06, .56, .63), .24, .25, emission=2.6)
AMBER = material('Lantern light', (.82, .35, .105), .32, emission=3.7)
VESSEL = material('Polished obsidian', (.08, .10, .15), .36, .44)


def finish(obj, mat, grain=1.9, variation=.12):
    mesh = obj.data
    mesh.materials.append(mat)
    uv = mesh.uv_layers.new(name='Stone UV') if not mesh.uv_layers else mesh.uv_layers[0]
    tint = mesh.color_attributes.new(name='Weathering', type='FLOAT_COLOR', domain='CORNER')
    tone = rng.uniform(1-variation, 1)
    offset = rng.random()
    for polygon in mesh.polygons:
        face = max(range(3), key=lambda k: abs(polygon.normal[k]))
        axes = ((1, 2), (0, 2), (0, 1))[face]
        for index in polygon.loop_indices:
            vertex = obj.matrix_world @ mesh.vertices[mesh.loops[index].vertex_index].co
            uv.data[index].uv = (vertex[axes[0]] / grain + offset, vertex[axes[1]] / grain)
            tint.data[index].color = (tone, tone, tone, 1)
    material_objects[mat].append(obj)


def block(x, y, z, w, d, h, mat=STONE, bevel=.024, angle=0):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(x, y, z), rotation=(0, 0, angle))
    obj = bpy.context.object
    obj.dimensions = (w, d, h)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel:
        mod = obj.modifiers.new('Chipped edges', 'BEVEL')
        mod.width = bevel
        mod.segments = 1
    finish(obj, mat)
    return obj


def polygon(name, vertices, faces, mat=STONE):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    finish(obj, mat)
    return obj


def drum(x, y, z, top, bottom, height, mat=STONE, sides=24, bevel=.028):
    bpy.ops.mesh.primitive_cone_add(vertices=sides, radius1=bottom, radius2=top, depth=height,
                                  location=(x,y,z))
    obj = bpy.context.object
    if bevel:
        mod = obj.modifiers.new('Worked edges', 'BEVEL')
        mod.width = bevel
        mod.segments = 1
    finish(obj, mat)
    return obj


def curved_wedge(name, x, y, mid, a, b, inner, outer, depth, mat, rotation=0):
    bounds = [(a, inner), (a, outer), (b, outer), (b, inner)]
    vertices = [(x+math.cos(t)*r, y+dy, mid+math.sin(t)*r) for dy in [-depth*.5, depth*.5] for t,r in bounds]
    if rotation:
        c,s=math.cos(rotation),math.sin(rotation)
        vertices=[(x+(vx-x)*c-(vy-y)*s,y+(vx-x)*s+(vy-y)*c,vz) for vx,vy,vz in vertices]
    obj=polygon(name, vertices, [(0,1,2,3),(7,6,5,4),(0,4,5,1),(1,5,6,2),(2,6,7,3),(3,7,4,0)], mat)
    edge = obj.modifiers.new('Rounded masonry', 'BEVEL')
    edge.width = .014
    edge.segments = 1
    return obj


def pillar(x, y, height=5.3, bottom=0, radius=.43):
    for w, h, z, mat in [(1.16,.23,.12,BASALT),(.96,.18,.32,STONE),(.78,.16,.49,TRIM)]:
        block(x,y,bottom+z,w,w,h,mat,.035)
    shaft = max(1, height-1.23)
    for i in range(6):
        h=shaft/6
        drum(x,y,bottom+.55+h*(i+.5),radius*(1-i*.025),radius*(1-i*.019),h-.019,
             TRIM if i == 5 else STONE,12)
    drum(x,y,bottom+height-.58,radius+.18,radius-.04,.27,TRIM,12)
    block(x,y,bottom+height-.34,1.05,1.05,.2,TRIM,.034)
    block(x,y,bottom+height-.14,.94,.94,.18,STONE,.03)


def lantern(x, y, z, cool=False):
    size=.22
    drum(x,y,z+.04,.29,.21,.12,BRONZE,12)
    drum(x,y,z+.21,size,size,.24,TEAL if cool else AMBER,8,.009)
    drum(x,y,z+.37,.08,.29,.17,BRONZE,8)
    for k in range(4):
        a=TAU*k/4+TAU/8
        block(x+size*math.cos(a),y+size*math.sin(a),z+.23,.035,.035,.31,BRONZE,.006)
    light=bpy.data.lights.new('Aether lamp', 'POINT')
    light.energy=48 if cool else 73
    light.color=(.20,.74,1.) if cool else (1,.53,.24)
    light.shadow_soft_size=.18
    lamp=bpy.data.objects.new('Aether lamp',light)
    bpy.context.collection.objects.link(lamp)
    lamp.location=(x,y-.1,z+.24)


def arch(y, middle, radius, width, stone_mat=TRIM, rotation=0):
    segments=18
    for i in range(segments):
        a=math.pi*i/segments
        b=math.pi*(i+1)/segments
        curved_wedge('Carved arch',0,y,middle,a+.010,b-.010,radius,radius+width,.65,stone_mat,rotation)
        curved_wedge('Bronze inlay',0,y-.35,middle,a+.012,b-.012,radius-.065,radius+.04,.09,BRONZE,rotation)


def floor(radius, depth=.5, height=0):
    drum(0,0,height-depth*.52,radius,radius+.11,depth,BASALT,48)
    for i in range(48):
        a=TAU*(i+.5)/48
        block((radius-.12)*math.cos(a),(radius-.12)*math.sin(a),height-.13,
              .45,radius*TAU/48*.94,.21,TRIM,.025,a)
    for ix in range(-12,13):
        for iy in range(-10,11):
            x=(ix+(iy%2)*.5)*.78
            y=iy*.68
            if math.hypot(x,y)>radius-.34: continue
            h=rng.uniform(.052,.094)
            block(x,y,height+h*.05,.763,.661,h,STONE if rng.random()>.12 else BASALT,.018,rng.uniform(-.015,.015))


def stairs(y, width, count, step=.27, landing=.43, z=0):
    for i in range(count):
        height=(i+1)*step
        block(0,y+i*landing,z+height*.5,width,.48,height,STONE,.033)
        block(0,y+i*landing-.14,z+height,width+.10,.17,.125,TRIM,.020)


def peaks():
    for row,(height,start,end) in enumerate([(10.3,22.,71.),(5.6,14.,31.)]):
        vertices=[]
        count=52 if row==0 else 48
        rings=15 if row==0 else 12
        for r in range(rings):
            distance=start+(end-start)*r/(rings-1)
            for k in range(count+1):
                angle=TAU*k/count
                x=math.cos(angle)*distance
                y=math.sin(angle)*distance
                large=fractal(Vector((x*.102+row*55,y*.102,.13)),.95,2.05,3)
                rock=fractal(Vector((x*.37,y*.37,.13)),1.04,2.2,3)
                border=math.sin(math.pi*r/(rings-1))**.8
                z=-3.4+border*(max(.2,.85+large*1.2)*height+rock*1.42)
                vertices.append((x,y,z))
        faces=[]
        for r in range(rings-1):
            for k in range(count):
                a=r*(count+1)+k
                b=a+count+1
                faces.extend([(a,b+1,a+1),(a,b,b+1)])
        obj=polygon('Surrounding mountains',vertices,faces,LAND)
        for p in obj.data.polygons: p.use_smooth=True


def gate():
    floor(6.65)
    stairs(-1.95,4.60,5)
    block(0,.98,.60,6.13,3.21,1.25,BASALT,.09)
    block(0,.87,1.24,6.04,3.14,.19,TRIM,.06)
    for sign in [-1,1]:
        pillar(2.52*sign,1.65,4.7,1.32)
        pillar(4.6*sign,2.52,6.1,1.05,.46)
        pillar(5.45*sign,3.91,4.8,.52,.39)
        for i in range(8):
            z=1.43+i*.49
            block(sign*(3.39+(i%2)*.1),1.85,z,1.37,1.55,.43,STONE,.06)
        for i in range(9):
            if i<6 or i%3!=0:
                block(sign*(4.94+(i%2)*.16),3.13,1.+i*.49,1.5,1.13,.42,STONE,.045)
        lantern(sign*3.1,.22,2.72)
        for i in range(3):
            block(sign*(3.45+i*.53),-1.15+i*.73,.28,.60,.84,rng.uniform(.18,.41),BASALT,.05,.33*i)
    arch(1.65,4.55,2.18,.76)
    for i in range(28):
        a=TAU*i/28
        curved_wedge('Aether ring',0,1.28,4.13,a+.008,a+TAU/28-.008,1.69,1.75,.06,TEAL)
        curved_wedge('Engraved mechanism',0,1.36,4.13,a+.027,a+TAU/28-.024,1.76,1.91,.18,BRONZE)
    # Black viewing recess provides a consistent silhouette against the sky.
    drum(0,1.56,4.13,1.67,1.67,.13,VESSEL,48).rotation_euler.x=math.pi/2
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, location=(0,1.30,4.15),scale=(.41,.18,.64))
    finish(bpy.context.object,TEAL)
    for x,y in [(-5.2,-2.15),(5.2,-2.15),(-5.98,1.8),(5.98,1.8)]:
        drum(x,y,.18,.31,.37,.31,TRIM,10)
        lantern(x,y,.31,True)


def arena():
    floor(7.35)
    for ring,z,mat in [(4.88,.06,BASALT),(4.19,.13,BRONZE),(4.06,.16,STONE),(2.35,.19,TRIM),(2.22,.22,BASALT)]:
        drum(0,0,z,ring,ring,.09,mat,48,.02)
    for side in [-1,1]:
        x=2.73*side
        for z,r,mat in [(.18,1.32,STONE),(.32,1.19,TRIM),(.43,1.03,BASALT)]:
            drum(x,.3,z,r,r+.05,.17,mat,16)
        for a in [-.5,.5,1.7]:
            lantern(x+.70*math.cos(a),.30+.70*math.sin(a),.49,side<0)
    for i in range(12):
        a=i*TAU/12+.1
        if math.sin(a)<-.36: continue
        x,y=6.30*math.cos(a),6.30*math.sin(a)
        height=rng.uniform(2.55,3.45)
        pillar(x,y,height,radius=.36)
        lantern(x,y,height-.045, i%2==0)
    for i in range(12):
        a=i*TAU/12+.33
        if math.sin(a)<-.53:continue
        x,y=6.68*math.cos(a),6.68*math.sin(a)
        for z in [.55,.98,1.42]:
            block(x,y,z,2.45,.58,.39,STONE,.055,a+math.pi/2)


def tower():
    floor(5.8)
    for level in range(6):
        z=level*1.15
        radius=2.96-level*.23
        for offset, step, mat in [(.1,0,TRIM),(.57,-.14,STONE),(1.03,.05,BRONZE)]:
            drum(0,1.8,z+offset,radius+step,radius+step,.26 if offset!=.57 else .72,mat,12)
        for i in range(10):
            a=i*TAU/10
            x,y=radius*math.cos(a),1.8+radius*math.sin(a)
            block(x,y,z+.57,.37,.45,.91, TRIM if i%2==0 else BASALT,.035,a)
            if i%2==0 and level%2==0: lantern(x,y,z+1.05,level%3==0)
    stairs(-3.12,3.12,5)
    for x in [-1.65,1.65]:
        pillar(x,-.24,2.91,1.25,.27)
        lantern(x,-.42,4.10,True)
    arch(-.25,3.18,1.22,.36)
    for i in range(14):
        a=TAU*i/14
        block(1.97*math.cos(a),1.8+1.97*math.sin(a),7.16,.33,.35,.84,TRIM,.03,a)
    for z,r in [(7.42,1.77),(7.8,1.62),(8.08,1.38)]:
        drum(0,1.8,z,r,r+.05,.35,STONE,12,.04)
    drum(0,1.8,8.63,.93,1.41,.67,BRONZE,10,.03)
    drum(0,1.8,9.25,.27,.83,.58,BRONZE,10,.03)
    drum(0,1.8,9.74,.035,.32,.50,TEAL,10,.02)


def merge_geometry():
    # One draw group per surface type, including its UVs, packed texture and vertex tint.
    for mat, items in material_objects.items():
        bpy.ops.object.select_all(action='DESELECT')
        for obj in items:
            bpy.context.view_layer.objects.active = obj
            for mod in list(obj.modifiers):
                bpy.ops.object.modifier_apply(modifier=mod.name)
            obj.select_set(True)
        bpy.context.view_layer.objects.active = items[0]
        bpy.ops.object.join()
        bpy.context.object.name = mat.name
        bpy.context.view_layer.objects.active = bpy.context.object
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

peaks()
{'gate':gate,'arena':arena,'tower':tower}[opts.scene]()
merge_geometry()
bpy.ops.export_scene.gltf(filepath=str(opts.output/(opts.scene+'.glb')), export_format='GLB',
                         export_image_format='JPEG', export_image_quality=85, export_lights=True,
                         export_all_vertex_colors=False, export_vertex_color='ACTIVE')
if not opts.preview:
    raise SystemExit(0)

scene=bpy.context.scene
scene.world=bpy.data.worlds.new('Firstlight dusk')
scene.world.use_nodes=True
tree=scene.world.node_tree
env=tree.nodes.new('ShaderNodeTexEnvironment')
env.image=bpy.data.images.load(str(ROOT/'firstlight_sky.png'))
tree.links.new(env.outputs['Color'],tree.nodes.get('Background').inputs[0])
tree.nodes.get('Background').inputs[1].default_value=1.0
sun=bpy.data.lights.new('Last sunlight','SUN')
sun.energy=2.8
sun.angle=.075
sun.color=(1.,.79,.60)
key=bpy.data.objects.new('Last sunlight',sun)
bpy.context.collection.objects.link(key)
key.rotation_euler=(Vector((.51,.39,-.78))).to_track_quat('-Z','Y').to_euler()
fill=bpy.data.lights.new('Moonlight','AREA')
fill.energy=720
fill.color=(.42,.61,1)
fill.shape='DISK'
fill.size=13.
lamp=bpy.data.objects.new('Moonlight',fill)
bpy.context.collection.objects.link(lamp)
lamp.location=(5,-4,11)
lamp.rotation_euler=(Vector((0,0,2))-lamp.location).to_track_quat('-Z','Y').to_euler()
cpos,target,lens={'gate':((4.1,-15.7,6.8),(0,1.30,3.56),32),
                  'arena':((4.5,-14.1,8.1),(0,0,1.2),31),
                  'tower':((6.4,-17.4,7.5),(0,1.0,4.9),31)}[opts.scene]
camera=bpy.data.objects.new('Camera',bpy.data.cameras.new('Camera'))
bpy.context.collection.objects.link(camera)
camera.location=cpos
camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler()
camera.data.lens=lens
scene.camera=camera
scene.render.engine='CYCLES'
scene.cycles.samples=36
scene.cycles.use_denoising=False
scene.view_settings.view_transform='AgX'
scene.use_nodes=True
nodes=scene.node_tree.nodes
nodes.clear()
layers=nodes.new('CompositorNodeRLayers')
glare=nodes.new('CompositorNodeGlare')
glare.glare_type='FOG_GLOW'
glare.threshold=1.3
scene.node_tree.links.new(layers.outputs['Image'],glare.inputs['Image'])
scene.node_tree.links.new(glare.outputs['Image'],nodes.new('CompositorNodeComposite').inputs['Image'])
scene.render.resolution_x=936
scene.render.resolution_y=466
scene.render.threads_mode='FIXED'
scene.render.threads=3
scene.render.filepath=str(opts.output/(opts.scene+'.png'))
bpy.ops.render.render(write_still=True)
