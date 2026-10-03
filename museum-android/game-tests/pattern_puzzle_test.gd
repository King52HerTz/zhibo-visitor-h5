extends SceneTree
const Pattern = preload("res://pattern_puzzle.gd")
var failures := 0

func check(condition: bool, label: String) -> void:
	if not condition:
		failures += 1
		printerr(label)

func _initialize() -> void:
	var puzzle = Pattern.new()
	check(not puzzle.place(-1, Vector2.ZERO), "Invalid piece")
	check(not puzzle.place(3, Vector2.ZERO), "Out-of-range piece")
	check(not puzzle.place(0, Pattern.POINTS[1]), "Wrong slot")
	check(puzzle.mask == 0, "Wrong match cannot alter progress")
	check(puzzle.place(2, Pattern.POINTS[2]), "Out-of-order matching")
	check(not puzzle.complete(), "One piece is not completion")
	check(puzzle.place(2, Pattern.POINTS[2]) and puzzle.mask == 4, "Repeated match idempotent")
	var restored = Pattern.new()
	restored.mask = puzzle.mask
	check(restored.place(0, Pattern.POINTS[0]), "Resume checkpoint")
	check(restored.place(1, Pattern.POINTS[1]), "Last piece")
	check(restored.complete(), "All pieces completed")
	print("Pattern puzzle: 10 checks, failures=", failures)
	quit(1 if failures else 0)
