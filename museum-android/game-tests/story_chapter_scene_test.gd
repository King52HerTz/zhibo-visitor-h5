extends SceneTree

const Scene = preload("res://story_slice.tscn")
var checks := 0
var failures := 0

class FailingBridge extends RefCounted:
	var fail_save := true
	func save_story_slice_checkpoint(_mask: int) -> bool: return not fail_save

func check(condition: bool, text: String) -> void:
	checks += 1
	if not condition:
		failures += 1
		printerr(text)

func _initialize() -> void:
	call_deferred("run_tests")

func run_tests() -> void:
	var scene = Scene.instantiate()
	root.add_child(scene)
	await process_frame
	scene.handle_tap(Vector2(1000, 650))
	scene.handle_tap(Vector2(1000, 650))
	check(scene.page == 1 and scene.rules.mask == 0, "Missing clips reach cloth without granting progress")
	scene.handle_tap(Vector2(1000, 650))
	check(scene.page == 1, "Cannot continue before both cloth observations")
	scene.handle_tap(Vector2(350, 400))
	scene.handle_tap(Vector2(850, 400))
	var bridge = FailingBridge.new()
	scene.bridge = bridge
	scene.handle_tap(Vector2(1000, 650))
	check(scene.rules.mask == 0 and scene.page == 1 and scene.pending_bit == 1, "Failed save never unlocks next action")
	bridge.fail_save = false
	scene.handle_tap(Vector2(1000, 650))
	check(scene.rules.mask == 1 and scene.page == 2, "Retry commits the confirmed action")
	scene.bridge = null
	scene.handle_tap(Vector2(640, 400))
	scene.handle_tap(Vector2(850, 440))
	check(scene.rules.mask == 3 and scene.page == 3, "Unsupported claim does not complete cloth")
	scene.handle_tap(Vector2(350, 440))
	check(scene.rules.mask == 7 and scene.page == 4, "Observed claim completes cloth")
	for index in range(4): scene.handle_tap(Vector2(1000, 650))
	check(scene.page == 5, "Response and family clips end at painting")
	scene.handle_tap(Vector2(850, 320))
	check(scene.rules.mask == 7, "Cannot place a card without observing it")
	scene.handle_tap(Vector2(300, 320))
	scene.handle_tap(Vector2(850, 420))
	check(scene.rules.mask == 7, "Wrong placement does not grant progress")
	for index in range(3):
		scene.handle_tap(Vector2(300, 320 + index * 104))
		scene.handle_tap(Vector2(850, 320 + index * 104))
	check(scene.rules.mask == 63 and scene.page == 6, "Three correct placements complete painting")
	for index in range(3): scene.handle_tap(Vector2(1000, 650))
	check(scene.page == 11, "Missing daoyin clip reaches matching task")
	scene.handle_tap(Vector2(600, 400))
	check(scene.rules.mask == 63, "Wrong matching pose does not grant progress")
	scene.handle_tap(Vector2(950, 400))
	check(scene.rules.mask == 127 and scene.page == 7, "Matching pose advances")
	scene.handle_tap(Vector2(300, 400))
	check(scene.rules.mask == 127, "Wrong comparison does not grant progress")
	scene.handle_tap(Vector2(650, 400))
	check(scene.rules.mask == 255 and scene.page == 8, "Different pose advances")
	scene.handle_tap(Vector2(350, 440))
	check(scene.rules.mask == 255, "Tomb one cannot complete daoyin")
	scene.handle_tap(Vector2(850, 440))
	check(scene.rules.mask == 511 and scene.page == 9, "Tomb three completes chapter")
	scene.handle_tap(Vector2(1000, 650))
	scene.handle_tap(Vector2(1000, 650))
	scene.handle_tap(Vector2(1000, 650))
	check(scene.page == 10 and scene.stamped, "Ending clip only returns to earned chapter stamp")
	scene.open_chapter(1)
	scene.handle_tap(Vector2(1000, 650))
	check(scene.page == 5 and scene.placed == 0 and scene.rules.mask == 511, "Replay resets interaction, preserves earned progress")
	scene.free()
	print("Story chapter scene: ", checks, " checks, failures=", failures)
	quit(1 if failures else 0)
