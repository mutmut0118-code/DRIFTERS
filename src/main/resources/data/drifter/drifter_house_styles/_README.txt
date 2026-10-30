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
  (default: the stair's full block), door, chimney_block, railing (a balcony's; default: the fence of the frame's wood)
Chances (0 to 1):
  roofs {"gable": w, "steep": w, "gambrel": w, "hip": w}  (steep: two up for one across; gambrel: a barn roof, steep
  for the lower half, then 45 degrees to the ridge)
  gable_front (the door in the gable end), lintels (stairs over windows), ridge_log (a log along the ridge),
  lanterns (hanging under the beams and at the door, else torches), tall_windows, wide_windows (three wide in a 5 bay),
  vaulted (a second layer under the roof, always on roofs 15 or wider), chimney (outside a gable end: none of the
  mod's styles have one, the user took them out of their edits, and a builder can't reach its top),
  wing (out of the back, 50-70% of the house's width, nine or more, its ridge at least two lower), wing_t (in the middle,
  else to one side), tower (at a front corner, away from the door; one storey houses), partition (a wall between two
  rooms in a house 17 or longer, upstairs if there is one)
Parts added later (0.22.37-side.2): only a style that names one of these draws for it, so the first twelve styles'
plans stay exactly as they were in old worlds. Don't add these to those twelve: their plans would change.
  dormers (a little gabled window out of a 45-degree gable roof 11 or wider, odd gaps only; over the middle bay or
  post and every other bay out, both slopes, not over a wing or tower), balcony (two storeys or more: a floor two deep
  with a railing over the front door, the storey above's door out onto it), cross (with wing 1 and wing_t 1: the T
  wing's twin out of the front, the front door in its gable end)
A house begun from a plan keeps it even when new styles change which plans a world makes (GeneratedPlans.again
makes it again from its name).
Flat roofs (the user, 2026-10-30: "the same houses but with square roofs instead of actual roofs, because every house
kind of looks similar"; "remove the hole in the roof and just make it a flat roof"): every style here gets a flat twin
("cottage_flat", drawn half as often as the style; HouseStyle.flatTwin): the same house, a flat lid level with the walls'
tops (no parapet, nothing on it), a block out all round; 4 in 10 reach 2 out with wood underneath; half the lids are full
blocks (planks, or stone where the slab is stone), half slabs. A twin leaves out dormers, a tower, a cross and the door
in the gable end. A flat-roofed square is drawn again. Roof fields (a style can set them; the twins set the first ones):
  roofs {"flat": w}   a flat roof
  parapet [a,b]       0: the plain lid; 1 or 2: a capped parapet that high over a slab deck, the posts carried up
  cantilever          the chance the lid reaches out further (overhang) with soffit slabs (wood) underneath
  overhang [a,b]      how far (2 for the twins)
  lid_full            the chance the lid is full blocks, not slabs
  deck, cap, soffit   the lid's or deck's slab (default: slab), the parapet's cap (default: deck), the underside's slab
  viga, vigas         log ends out under a parapet (Pueblo) and their chance
  ribbon              the chance of windows filling every bay of the upper storeys' long walls
  roof_stairs         the chance of stairs up through a slab deck to a little stair tower on the roof (the user found it
                      "a bit too realistic, especially with the things on top": no style uses it now)
  wing                on two storeys or more: a one-storey wing out of the back with its own flat roof
