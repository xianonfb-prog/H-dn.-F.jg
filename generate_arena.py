#!/usr/bin/env python3
"""
Minimal, spec-correct NBT writer (big-endian, gzip-compressed) - built by hand since this
environment has no network access to pull in an NBT library. Generates a real Minecraft
structure (.nbt) file: a circular crimson-themed arena matching MutinyArena's FULL_RADIUS.

Structure NBT format (root compound, unnamed):
  DataVersion: Int
  size: List<Int> [x, y, z]
  entities: List<Compound>  (empty)
  blocks: List<Compound> { pos: List<Int>, state: Int }
  palette: List<Compound> { Name: String }
"""

import gzip
import struct

# --- Tag type constants ---
TAG_End = 0
TAG_Byte = 1
TAG_Int = 3
TAG_String = 8
TAG_List = 9
TAG_Compound = 10

def w_string(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b

def w_named(tag_type: int, name: str, payload: bytes) -> bytes:
    return struct.pack(">B", tag_type) + w_string(name) + payload

def p_int(value: int) -> bytes:
    return struct.pack(">i", value)

def p_string(value: str) -> bytes:
    return w_string(value)

def p_list(element_tag_type: int, element_payloads: list[bytes]) -> bytes:
    out = struct.pack(">B", element_tag_type)
    out += struct.pack(">i", len(element_payloads))
    for payload in element_payloads:
        out += payload
    return out

def p_compound(named_tags: bytes) -> bytes:
    return named_tags + struct.pack(">B", TAG_End)

def compound_int_list(name: str, values: list[int]) -> bytes:
    payload = p_list(TAG_Int, [p_int(v) for v in values])
    return w_named(TAG_List, name, payload)


# ---------------------------------------------------------------
# Arena generation
# ---------------------------------------------------------------

RADIUS = 40          # matches MutinyArena.FULL_RADIUS
WALL_HEIGHT = 5
DIAMETER = RADIUS * 2 + 1
SIZE_Y = WALL_HEIGHT + 2

FLOOR_MAIN = "minecraft:crimson_nylium"
FLOOR_ACCENT_A = "minecraft:nether_wart_block"
FLOOR_ACCENT_B = "minecraft:soul_soil"
WALL_MAIN = "minecraft:nether_bricks"
WALL_LIGHT = "minecraft:shroomlight"
AIR = "minecraft:air"

def build_arena():
    palette_index = {}
    palette_order = []

    def index_for(name: str) -> int:
        if name not in palette_index:
            palette_index[name] = len(palette_order)
            palette_order.append(name)
        return palette_index[name]

    blocks = []  # list of (x, y, z, palette_idx)

    cx = cz = RADIUS  # local grid center

    for x in range(DIAMETER):
        for z in range(DIAMETER):
            dx = x - cx
            dz = z - cz
            dist = (dx * dx + dz * dz) ** 0.5

            if dist <= RADIUS - 1.5:
                # Floor, with a light deterministic texture variation so it doesn't read as
                # one flat solid color.
                accent = (x * 31 + z * 17) % 13
                if accent == 0:
                    name = FLOOR_ACCENT_A
                elif accent == 6:
                    name = FLOOR_ACCENT_B
                else:
                    name = FLOOR_MAIN
                blocks.append((x, 0, z, index_for(name)))

            elif RADIUS - 1.5 < dist <= RADIUS + 0.5:
                # Boundary wall ring - matches the hard-wall radius the plugin already enforces,
                # so the visual edge lines up with where players actually get stopped.
                for y in range(1, WALL_HEIGHT + 1):
                    along_ring = (x + z * 3) % 9
                    if y == 3 and along_ring == 0:
                        name = WALL_LIGHT  # periodic light source along the wall
                    else:
                        name = WALL_MAIN
                    blocks.append((x, y, z, index_for(name)))

    return blocks, palette_order


def write_structure_nbt(path: str, blocks, palette_names):
    # palette: List<Compound{Name: String}>
    palette_payload = p_list(
        TAG_Compound,
        [p_compound(w_named(TAG_String, "Name", p_string(name))) for name in palette_names],
    )

    # blocks: List<Compound{pos: List<Int>, state: Int}>
    block_payloads = []
    for (x, y, z, state) in blocks:
        pos_tag = compound_int_list("pos", [x, y, z])
        state_tag = w_named(TAG_Int, "state", p_int(state))
        block_payloads.append(p_compound(pos_tag + state_tag))
    blocks_payload = p_list(TAG_Compound, block_payloads)

    # entities: empty list - TAG_End as element type is the standard convention for an empty list
    entities_payload = p_list(TAG_End, [])

    root_body = b""
    root_body += w_named(TAG_Int, "DataVersion", p_int(3465))  # see note below re: version
    root_body += compound_int_list("size", [DIAMETER, SIZE_Y, DIAMETER])
    root_body += w_named(TAG_List, "entities", entities_payload)
    root_body += w_named(TAG_List, "blocks", blocks_payload)
    root_body += w_named(TAG_List, "palette", palette_payload)

    root = w_named(TAG_Compound, "", p_compound(root_body))

    with gzip.open(path, "wb") as f:
        f.write(root)


if __name__ == "__main__":
    import os

    # Writes straight into the plugin's resources folder (relative to repo root), so this
    # works both run locally from the repo root AND in the GitHub Actions workflow - no
    # hardcoded local sandbox path, no separate "find and copy" step needed afterward.
    output_dir = os.path.join("exotic", "src", "main", "resources", "structures")
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "crimson_mutiny_arena.nbt")

    blocks, palette = build_arena()
    write_structure_nbt(output_path, blocks, palette)
    print(f"Wrote {output_path}")
    print(f"Blocks: {len(blocks)}, palette entries: {palette}")
