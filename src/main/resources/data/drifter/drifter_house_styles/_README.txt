House styles for the plan generator (milestone 15). One JSON file per style; the file name is the style's id.
Oak stands for the Drifter's main wood, spruce for its second wood, cobblestone for its stone: HousePalette swaps them.
Fields (all optional): weight, width [min,max], depth [min,max], two_floors (chance), wall_height [min,max],
floor, base (bottom wall row), frame (log for corners and posts), posts_every (0 none), beam (log along the wall top),
infill, upper_infill, window, window_spacing, tall_windows (chance), sill (a stair under windows, half the time),
roofs {"gable": w, "gable_z": w, "hip": w, "shed": w, "flat": w}, stair, slab, gable_fill, parapet (flat roofs), door, upper_floor,
porch (chance), porch_floor, porch_post, chimney (chance), chimney_block, door_off_center (chance),
loft (chance, one-storey gable houses 7+ across, not with a wing), tower (chance of a corner tower: 7+ wide,
6+ deep, not a shed roof, no loft or porch),
wing (chance of an L: a one-storey wing out of the back, only behind a "gable" roof), wing_depth [min,max].
A material may be a list of block ids to pick from (each plan picks one); "none" in a list means none (for base and frame).
Roofs 7 or more across get an attic window in each gable end; walls under 3 high get no porch.
