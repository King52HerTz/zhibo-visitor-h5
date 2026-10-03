extends SceneTree

const Rules = preload("res://story_slice_rules.gd")
var failures := 0
var checks := 0

func check(condition: bool, label: String) -> void:
	checks += 1
	if not condition:
		failures += 1
		printerr(label)

func _initialize() -> void:
	var rules = Rules.new()
	check(rules.stage() == 1, "Fresh player starts with light comparison")
	check(not rules.mark(Rules.JUDGED), "Cannot judge before observations")
	check(rules.mark(Rules.LIGHT) and rules.stage() == 2, "Light advances to collar")
	var restored = Rules.new()
	restored.mask = rules.mask
	check(restored.stage() == 2, "Checkpoint restores partial progress")
	check(restored.mark(Rules.COLLAR) and restored.stage() == 3, "Collar advances to judgement")
	check(restored.mark(Rules.JUDGED) and restored.stage() == 4, "Judgement unlocks response")
	check(restored.mask == Rules.COMPLETE, "Only all three observations finish")
	check(not restored.mark(512), "Invalid marker rejected")
	check(not restored.mark(Rules.TOMB), "Cannot skip painting and pose observations")
	check(restored.unlocked(1) and not restored.unlocked(2), "Cloth unlocks painting only")
	for bit in [8, 16, 32]:
		check(restored.mark(bit), "Painting checkpoint advances in order")
	check(restored.complete(1) and restored.unlocked(2), "All placements unlock daoyin")
	check(not restored.mark(Rules.POSE_COMPARE), "Cannot compare before matching")
	for bit in [64, 128, 256]:
		check(restored.mark(bit), "Daoyin checkpoint advances in order")
	check(restored.mask == 511 and restored.complete(2), "Only nine confirmed actions complete chapter")
	check(restored.mark(Rules.JUDGED) and restored.mask == 511, "Replay does not erase or duplicate progress")
	print("Story chapter rules: ", checks, " checks, failures=", failures)
	quit(1 if failures else 0)
