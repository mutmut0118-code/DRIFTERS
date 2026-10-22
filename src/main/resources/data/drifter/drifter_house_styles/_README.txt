House styles for the plan generator (HouseGenerator, 0.22.7: the base-building guidebook's rules). One JSON file per style;
the file name is the style's id. Oak stands for the Drifter's main wood, spruce for its second wood, cobblestone for its
stone: HousePalette swaps them (the second wood always a different one from the main, for contrast).
(The first generator's styles are in ../drifter_house_styles_legacy: kept so a house begun from one of its plans keeps it.)

Size (the pillar formula: a wall is bays x (gap + 1) + 1 long, so a post lands on both corners):
  gaps [..]        blocks between posts (3, 4 or 5; an even gap makes the door a double door)
  long_bays [..]   bays along the long side; short_bays [..] bays along the short side (each plan picks one of each)
  max_ratio        longest the house may be for its width (default 2.1); a square house always gets a hip roof
  max_length       longest the house may be at all
  floors [min,max] storeys, each four high (three of room and a beam row); stairs along the back wall go up
Materials (a block id, or a list to pick one from; "none" leaves it out):
  foundation, plinth (stairs round the foundation; also the door step), floor, floor_border (a ring round the room's edge),
  frame (logs: posts and beams), infill (the wall, a block behind the frame; a log here lies along the wall),
  upper_infill, gable_fill, window, sill (stairs under windows, half the plans), stair (the roof), slab, roof_block
  (default: the stair's full block), door, chimney_block
Chances (0 to 1):
  roofs {"gable": w, "steep": w, "hip": w}  (steep: two up for one across)
  gable_front (the door in the gable end), lintels (stairs over windows), ridge_log (a log along the ridge),
  lanterns (hanging under the beams and at the door, else torches), tall_windows, wide_windows (three wide in a 5 bay),
  vaulted (a second layer under the roof, always on roofs 15 or wider), chimney (outside a gable end: none of the
  mod's styles have one, the user took them out of their edits, and a builder can't reach its top),
  wing (out of the back, 50-70% of the house's width, nine or more, its ridge at least two lower), wing_t (in the middle,
  else to one side), tower (at a front corner, away from the door; one storey houses), partition (a wall between two
  rooms in a house 17 or longer, upstairs if there is one)
