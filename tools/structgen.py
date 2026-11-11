"""
Gerador de estruturas .nbt (formato de "structure template" do Minecraft 1.21.1, DataVersion 3955).
Sem dependências externas. Usado pelos módulos de estruturas.

Exemplo:
    import sys; sys.path.insert(0, "tools")
    from structgen import Structure
    s = Structure(9, 7, 9)
    s.fill(0, 0, 0, 8, 0, 8, "minecraft:stone_bricks")
    s.hollow_box(0, 1, 0, 8, 5, 8, "minecraft:mossy_stone_bricks")
    s.set(4, 1, 4, "minecraft:chest[facing=north]", nbt={"LootTable": "isekai_mahou:chests/shrine_fire"})
    s.set(4, 1, 2, "isekai_mahou:shrine_altar")         # blocos do mod pelo id
    s.entity(4.5, 1, 6.5, "isekai_mahou:goblin")          # entidade (opcional)
    s.save("src/main/resources/data/isekai_mahou/structure/shrine_fire.nbt")

Estado de bloco: "namespace:id[prop=valor,prop2=valor]". Ar ("minecraft:air") NÃO é salvo por padrão
(blocos não definidos ficam como estão no mundo); use keep_air=True no save para salvar o ar explicitamente
(útil para esvaziar o interior de estruturas enterradas) ou "minecraft:structure_void" para "não mexer".
Para peças de jigsaw use s.jigsaw(x,y,z, orientation, name, target, pool, final_state).
"""
import gzip
import struct

DATA_VERSION = 3955  # Minecraft 1.21.1

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 7, 8, 9, 10, 11, 12


class Int(int):
    pass


class Byte(int):
    pass


class Double(float):
    pass


class Float(float):
    pass


def _tag_type(v):
    if isinstance(v, Byte) or isinstance(v, bool):
        return TAG_BYTE
    if isinstance(v, Double):
        return TAG_DOUBLE
    if isinstance(v, Float):
        return TAG_FLOAT
    if isinstance(v, int):
        return TAG_INT
    if isinstance(v, float):
        return TAG_DOUBLE
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, dict):
        return TAG_COMPOUND
    if isinstance(v, list):
        return TAG_LIST
    raise TypeError(type(v))


def _write_payload(out, t, v):
    if t == TAG_BYTE:
        out += struct.pack(">b", int(v))
    elif t == TAG_INT:
        out += struct.pack(">i", int(v))
    elif t == TAG_DOUBLE:
        out += struct.pack(">d", float(v))
    elif t == TAG_FLOAT:
        out += struct.pack(">f", float(v))
    elif t == TAG_STRING:
        b = v.encode("utf-8")
        out += struct.pack(">H", len(b)) + b
    elif t == TAG_LIST:
        et = _tag_type(v[0]) if v else TAG_END
        out += struct.pack(">bi", et, len(v))
        for item in v:
            _write_payload(out, et, item)
    elif t == TAG_COMPOUND:
        for k, item in v.items():
            it = _tag_type(item)
            out += struct.pack(">b", it)
            kb = k.encode("utf-8")
            out += struct.pack(">H", len(kb)) + kb
            _write_payload(out, it, item)
        out += struct.pack(">b", TAG_END)


def _parse_state(state):
    if "[" in state:
        name, props = state[:-1].split("[", 1)
        p = {}
        for kv in props.split(","):
            if kv.strip():
                k, v = kv.split("=")
                p[k.strip()] = v.strip()
        return name.strip(), p
    return state.strip(), {}


class Structure:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}      # (x,y,z) -> (state, nbt or None)
        self.entities = []

    def set(self, x, y, z, state, nbt=None):
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            self.blocks[(x, y, z)] = (state, nbt)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, state)

    def hollow_box(self, x0, y0, z0, x1, y1, z1, state, inside="minecraft:air"):
        self.fill(x0, y0, z0, x1, y1, z1, state)
        if inside is not None:
            self.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, inside)

    def walls(self, x0, y0, z0, x1, y1, z1, state):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, state); self.set(x, y, z1, state)
            for z in range(z0, z1 + 1):
                self.set(x0, y, z, state); self.set(x1, y, z, state)

    def cylinder(self, cx, y0, cz, r, h, state, hollow=False):
        for y in range(y0, y0 + h):
            for x in range(self.size[0]):
                for z in range(self.size[2]):
                    d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
                    if d <= r + 0.5 and (not hollow or d >= r - 0.5):
                        self.set(x, y, z, state)

    def chest(self, x, y, z, loot_table, facing="north"):
        self.set(x, y, z, f"minecraft:chest[facing={facing}]", nbt={"LootTable": loot_table})

    def spawner(self, x, y, z, entity_id):
        self.set(x, y, z, "minecraft:spawner", nbt={"SpawnData": {"entity": {"id": entity_id}}, "Delay": Int(20)})

    def jigsaw(self, x, y, z, orientation, name, target, pool, final_state="minecraft:air", joint="rollable"):
        """orientation ex.: 'north_up', 'up_north'. name/target: ids de conexão. pool: template_pool alvo."""
        self.set(x, y, z, f"minecraft:jigsaw[orientation={orientation}]", nbt={
            "name": name, "target": target, "pool": pool, "final_state": final_state, "joint": joint,
            "placement_priority": Int(0), "selection_priority": Int(0)})

    def entity(self, x, y, z, entity_id, extra=None):
        nbt = {"id": entity_id}
        if extra:
            nbt.update(extra)
        self.entities.append({"pos": [Double(x), Double(y), Double(z)],
                              "blockPos": [Int(int(x)), Int(int(y)), Int(int(z))], "nbt": nbt})

    def save(self, path, keep_air=False):
        palette = []
        index = {}
        blocks = []
        for (x, y, z), (state, nbt) in sorted(self.blocks.items()):
            if state == "minecraft:air" and not keep_air:
                continue
            if state not in index:
                name, props = _parse_state(state)
                entry = {"Name": name}
                if props:
                    entry["Properties"] = props
                index[state] = len(palette)
                palette.append(entry)
            b = {"pos": [Int(x), Int(y), Int(z)], "state": Int(index[state])}
            if nbt:
                b["nbt"] = nbt
            blocks.append(b)
        if not palette:
            palette.append({"Name": "minecraft:air"})
        root = {
            "DataVersion": Int(DATA_VERSION),
            "size": [Int(self.size[0]), Int(self.size[1]), Int(self.size[2])],
            "palette": palette,
            "blocks": blocks,
            "entities": self.entities,
        }
        out = bytearray()
        out += struct.pack(">b", TAG_COMPOUND) + struct.pack(">H", 0)
        _write_payload(out, TAG_COMPOUND, root)
        import os
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with gzip.open(path, "wb") as f:
            f.write(bytes(out))
        return len(blocks)
