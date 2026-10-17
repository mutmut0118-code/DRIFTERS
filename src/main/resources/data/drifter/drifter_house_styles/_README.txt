House styles for the plan generator (milestone 15). One JSON file per style; the file name is the style's id.
Oak stands for the Drifter's main wood, spruce for its second wood, cobblestone for its stone: HousePalette swaps them.
Fields (all optional): weight, width [min,max], depth [min,max], two_floors (chance), wall_height [min,max],
floor, base (bottom wall row), frame (log for corners and posts), posts_every (0 none), beam (log along the wall top),
infill, upper_infill, window, window_spacing, tall_windows (chance), sill (a stair under windows, half the time),
roofs {"gable": w, "gable_z": w, "shed": w, "flat": w}, stair, slab, gable_fill, parapet (flat roofs), door, upper_floor.
