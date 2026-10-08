"""Convert Schedule I's skateboards from the user's own install into a private Minecraft resource pack.

Reads the game files offline (never launches or touches the running game). For every skateboard prefab it
writes, into <pack>/assets/s1skate/s1/:
  boards.json             index of boards with their physics data (settings, rigidbody, hover points, camera)
  <board>.mesh.json       the visible model in board-local Unity space, one part per submesh
and the textures into <pack>/assets/s1skate/textures/s1/.

Nothing extracted here may be redistributed; the pack stays on this machine.

usage: python extract_s1_skateboards.py "<Schedule I folder>" "<.minecraft/resourcepacks/S1Skate-Local>"
needs: UnityPy (1.25+), TypeTreeGeneratorAPI, Pillow
"""
import json
import math
import os
import re
import sys

import UnityPy
from UnityPy.helpers.MeshHelper import MeshHandler
from UnityPy.helpers.TypeTreeGenerator import TypeTreeGenerator

SKATE_SCRIPTS = {"Skateboard", "SkateboardCamera", "SkateboardVisuals"}
MAX_TEX = 512
PACK_FORMAT = 97  # Minecraft 26.3 resource pack format


# ---------- small math (Unity conventions) ----------
def q_mul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
            aw * bw - ax * bx - ay * by - az * bz)


def q_rot(q, v):
    x, y, z, w = q
    vx, vy, vz = v
    tx = 2 * (y * vz - z * vy)
    ty = 2 * (z * vx - x * vz)
    tz = 2 * (x * vy - y * vx)
    return (vx + w * tx + (y * tz - z * ty),
            vy + w * ty + (z * tx - x * tz),
            vz + w * tz + (x * ty - y * tx))


class Xf:
    """Affine transform as (position, rotation, scale) composed parent * child, good enough for the
    uniform/axis-aligned scales used in these prefabs."""

    def __init__(self, p=(0, 0, 0), q=(0, 0, 0, 1), s=(1, 1, 1)):
        self.p, self.q, self.s = p, q, s

    def point(self, v):
        v = (v[0] * self.s[0], v[1] * self.s[1], v[2] * self.s[2])
        r = q_rot(self.q, v)
        return (r[0] + self.p[0], r[1] + self.p[1], r[2] + self.p[2])

    def direction(self, v):
        # normals: inverse-transpose of the scale, then rotate, then renormalise
        v = (v[0] / (self.s[0] or 1), v[1] / (self.s[1] or 1), v[2] / (self.s[2] or 1))
        r = q_rot(self.q, v)
        n = math.sqrt(r[0] ** 2 + r[1] ** 2 + r[2] ** 2) or 1
        return (r[0] / n, r[1] / n, r[2] / n)

    def then(self, child):
        """self is the parent; returns parent * child."""
        p = self.point(child.p)
        q = q_mul(self.q, child.q)
        s = (self.s[0] * child.s[0], self.s[1] * child.s[1], self.s[2] * child.s[2])
        return Xf(p, q, s)


def v3(d):
    return (d["x"], d["y"], d["z"])


def q4(d):
    return (d["x"], d["y"], d["z"], d["w"])


# ---------- asset access ----------
class Assets:
    def __init__(self, game_root):
        data = os.path.join(game_root, "Schedule I_Data")
        self.data = data
        gen = TypeTreeGenerator(self._unity_version(data))
        gen.load_local_game(game_root)
        self.env = UnityPy.load(os.path.join(data, "sharedassets0.assets"))
        self.env.typetree_generator = gen
        self.file = list(self.env.files.values())[0]
        self.objs = {o.path_id: o for o in self.env.objects}
        self._tt = {}
        self.scripts = self._skate_scripts(data)

    @staticmethod
    def _unity_version(data):
        env = UnityPy.load(os.path.join(data, "globalgamemanagers"))
        return list(env.files.values())[0].unity_version

    def _skate_scripts(self, data):
        gg = UnityPy.load(os.path.join(data, "globalgamemanagers.assets"))
        out = {}
        for o in gg.objects:
            if o.type.name == "MonoScript":
                t = o.read_typetree()
                if t["m_Namespace"] == "ScheduleOne.Skating" and t["m_ClassName"] in SKATE_SCRIPTS:
                    out[o.path_id] = t["m_ClassName"]
        self.gg_index = [i + 1 for i, e in enumerate(self.file.externals)
                         if e.path.endswith("globalgamemanagers.assets")]
        return out

    def tt(self, pid):
        if pid not in self._tt:
            self._tt[pid] = self.objs[pid].read_typetree()
        return self._tt[pid]

    def script_of(self, obj):
        """Class name of a MonoBehaviour's script, read from its header without a full typetree."""
        r = obj.reader
        r.Position = obj.byte_start
        r.read_int(); r.read_long()          # m_GameObject
        r.read_u_byte(); r.align_stream()    # m_Enabled
        fid = r.read_int(); pid = r.read_long()
        if fid in self.gg_index:
            return self.scripts.get(pid)
        return None

    def components(self, go_pid):
        for c in self.tt(go_pid)["m_Component"]:
            pid = c["component"]["m_PathID"]
            yield pid, self.objs[pid]

    def transform_of(self, go_pid):
        for pid, o in self.components(go_pid):
            if o.type.name in ("Transform", "RectTransform"):
                return pid
        raise KeyError(go_pid)

    def local_xf(self, tr_pid):
        t = self.tt(tr_pid)
        return Xf(v3(t["m_LocalPosition"]), q4(t["m_LocalRotation"]), v3(t["m_LocalScale"]))

    def xf_to_root(self, tr_pid, root_tr):
        """Transform from tr_pid's local space into the root transform's local space."""
        chain = []
        cur = tr_pid
        while cur != root_tr:
            chain.append(cur)
            cur = self.tt(cur)["m_Father"]["m_PathID"]
            if cur == 0:
                raise ValueError("transform is not under root")
        xf = Xf()
        for pid in reversed(chain):
            xf = xf.then(self.local_xf(pid))
        return xf

    def walk(self, tr_pid):
        yield tr_pid
        for ch in self.tt(tr_pid)["m_Children"]:
            yield from self.walk(ch["m_PathID"])


# ---------- extraction ----------
def curve(c):
    return [[k["time"], k["value"], k["inSlope"], k["outSlope"], k["weightedMode"], k["inWeight"], k["outWeight"]]
            for k in c["m_Curve"]]


def settings(s):
    out = {}
    for k, v in s.items():
        if isinstance(v, dict) and "m_Curve" in v:
            out[k] = curve(v)
        elif isinstance(v, (int, float)):
            out[k] = v
    return out


def slug(name):
    s = re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    return s.replace("_data_default", "").replace("skateboard", "board").strip("_") or "board"


def export_texture(a, tex_pid, out_dir, written):
    if tex_pid in written:
        return written[tex_pid]
    tex = a.objs[tex_pid].read()
    img = tex.image.convert("RGBA")
    if max(img.size) > MAX_TEX:
        f = MAX_TEX / max(img.size)
        img = img.resize((max(1, int(img.width * f)), max(1, int(img.height * f))))
    name = re.sub(r"[^a-z0-9_]+", "_", tex.m_Name.lower()).strip("_") + ".png"
    img.save(os.path.join(out_dir, name))
    written[tex_pid] = name
    return name


def material_info(a, mat_ref, tex_dir, written):
    if mat_ref["m_FileID"] != 0 or not mat_ref["m_PathID"]:
        return {"texture": None, "color": [1, 1, 1, 1]}
    m = a.tt(mat_ref["m_PathID"])
    props = m["m_SavedProperties"]
    texs = dict((k, v) for k, v in props["m_TexEnvs"])
    cols = dict((k, v) for k, v in props["m_Colors"])
    tex = None
    for key in ("_BaseMap", "_MainTex"):
        t = texs.get(key)
        if t and t["m_Texture"]["m_FileID"] == 0 and t["m_Texture"]["m_PathID"]:
            tex = export_texture(a, t["m_Texture"]["m_PathID"], tex_dir, written)
            scale = v3(dict(t["m_Scale"], z=0))[:2]
            offset = v3(dict(t["m_Offset"], z=0))[:2]
            break
    else:
        scale, offset = (1, 1), (0, 0)
    c = cols.get("_BaseColor") or cols.get("_Color") or {"r": 1, "g": 1, "b": 1, "a": 1}
    return {"texture": tex, "color": [c["r"], c["g"], c["b"], c["a"]], "uvScale": list(scale), "uvOffset": list(offset)}


def export_model(a, root_tr, tex_dir, written):
    parts = []
    for tr in a.walk(root_tr):
        go = a.tt(tr)["m_GameObject"]["m_PathID"]
        if not a.tt(go).get("m_IsActive", 1):
            continue
        mesh_pid = renderer = None
        for pid, o in a.components(go):
            if o.type.name == "MeshFilter":
                mesh_pid = a.tt(pid)["m_Mesh"]["m_PathID"]
            elif o.type.name == "MeshRenderer":
                renderer = a.tt(pid)
        if not mesh_pid or not renderer or not renderer["m_Enabled"]:
            continue
        xf = a.xf_to_root(tr, root_tr)
        mesh = a.objs[mesh_pid].read()
        h = MeshHandler(mesh)
        h.process()
        verts = [xf.point(v) for v in h.m_Vertices]
        norms = [xf.direction(n) for n in (h.m_Normals or [(0, 1, 0)] * len(verts))]
        uvs = h.m_UV0 or [(0, 0)] * len(verts)
        mats = renderer["m_Materials"]
        for i, tris in enumerate(h.get_triangles()):
            info = material_info(a, mats[min(i, len(mats) - 1)], tex_dir, written)
            used = sorted({j for t in tris for j in t})
            remap = {j: n for n, j in enumerate(used)}
            parts.append({
                "name": "%s.%d" % (a.tt(go)["m_Name"], i),
                **info,
                "positions": [round(c, 5) for j in used for c in verts[j]],
                "normals": [round(c, 4) for j in used for c in norms[j]],
                "uvs": [round(c, 5) for j in used for c in uvs[j][:2]],
                "indices": [remap[j] for t in tris for j in t],
            })
    return parts


def find_boards(a):
    boards = {}
    for o in a.env.objects:
        if o.type.name != "MonoBehaviour":
            continue
        cls = a.script_of(o)
        if cls:
            go = a.tt(o.path_id)["m_GameObject"]["m_PathID"]
            boards.setdefault(go, {})[cls] = o.path_id
    return {go: c for go, c in boards.items() if "Skateboard" in c}


def main(game_root, pack_dir):
    a = Assets(game_root)
    s1_dir = os.path.join(pack_dir, "assets", "s1skate", "s1")
    tex_dir = os.path.join(pack_dir, "assets", "s1skate", "textures", "s1")
    os.makedirs(s1_dir, exist_ok=True)
    os.makedirs(tex_dir, exist_ok=True)
    written = {}
    index = {"source": "Schedule I (user's own install), unity %s" % a._unity_version(a.data), "boards": {}}

    for go, comps in sorted(find_boards(a).items()):
        sb = a.tt(comps["Skateboard"])
        data_ref = sb["_defaultData"]["m_PathID"]
        if not data_ref:
            continue
        data = a.tt(data_ref)
        key = slug(data["m_Name"])
        if key in index["boards"]:
            continue
        root_tr = a.transform_of(go)

        def local_pos(tr_ref):
            return list(a.xf_to_root(tr_ref["m_PathID"], root_tr).p)

        rb = a.tt(sb["Rb"]["m_PathID"])
        board = {
            "dataName": data["m_Name"],
            "settings": settings(data["Settings"]),
            "rain": settings(a.tt(sb["_rainOverrideData"]["m_PathID"])["Settings"]) if sb["_rainOverrideData"]["m_PathID"] else None,
            "slowOnTerrain": bool(sb["SlowOnTerrain"]),
            "rigidbody": {"mass": rb["m_Mass"], "drag": rb["m_Drag"], "angularDrag": rb["m_AngularDrag"],
                          "inertiaTensor": list(v3(rb["m_InertiaTensor"]))},
            "centerOfMass": local_pos(sb["CoM"]),
            "hoverPoints": [local_pos(h) for h in sb["HoverPoints"]],
            "frontAxle": local_pos(sb["FrontAxlePosition"]),
            "rearAxle": local_pos(sb["RearAxlePosition"]),
            "model": key + ".mesh.json",
        }
        if "SkateboardCamera" in comps:
            cam = a.tt(comps["SkateboardCamera"])
            board["camera"] = {k: cam[k] for k in ("HorizontalOffset", "VerticalOffset", "CameraFollowSpeed",
                                                   "FOVMultiplier_MinSpeed", "FOVMultiplier_MaxSpeed",
                                                   "FOVMultiplierChangeRate")}
            board["camera"]["origin"] = local_pos(cam["cameraOrigin"])
        if "SkateboardVisuals" in comps:
            vis = a.tt(comps["SkateboardVisuals"])
            board["visuals"] = {"MaxBoardLean": vis["MaxBoardLean"], "BoardLeanRate": vis["BoardLeanRate"]}

        parts = export_model(a, root_tr, tex_dir, written)
        with open(os.path.join(s1_dir, board["model"]), "w") as f:
            json.dump({"parts": parts}, f, separators=(",", ":"))
        index["boards"][key] = board
        print("board %-12s %-32s parts=%d tris=%d" % (key, data["m_Name"], len(parts),
              sum(len(p["indices"]) // 3 for p in parts)))

    with open(os.path.join(s1_dir, "boards.json"), "w") as f:
        json.dump(index, f, indent=1)
    with open(os.path.join(pack_dir, "pack.mcmeta"), "w") as f:
        json.dump({"pack": {"description": "Schedule I skateboards (local, from your own install - do not share)",
                            "min_format": PACK_FORMAT, "max_format": PACK_FORMAT}}, f, indent=1)
    print("wrote %d boards, %d textures -> %s" % (len(index["boards"]), len(written), pack_dir))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], sys.argv[2])
