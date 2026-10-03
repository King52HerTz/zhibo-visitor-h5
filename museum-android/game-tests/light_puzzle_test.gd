extends SceneTree

const Puzzle = preload("res://light_puzzle.gd")
var failures := 0

func check(condition: bool, label: String) -> void:
	if not condition:
		failures += 1
		printerr(label)

func _initialize() -> void:
	var puzzle = Puzzle.new()
	check(not puzzle.observe(Vector2.ZERO), "Outside garment must not discover")
	check(puzzle.discovered() == 0, "Initial progress")
	check(puzzle.observe(Puzzle.POINTS[0]), "First point")
	check(not puzzle.observe(Puzzle.POINTS[0]), "Repeated contact is idempotent")
	check(not puzzle.complete(), "One point is not completion")
	var restored = Puzzle.new()
	restored.mask = puzzle.mask
	check(restored.discovered() == 1, "Checkpoint restores partial discovery")
	check(restored.observe(Puzzle.POINTS[2]), "Unordered exploration")
	check(not restored.complete(), "Two points are not completion")
	check(restored.observe(Puzzle.POINTS[1]), "Final point")
	check(restored.complete() and restored.discovered() == 3, "All points complete")
	print("Light puzzle: 10 checks, failures=", failures)
	quit(1 if failures else 0)
