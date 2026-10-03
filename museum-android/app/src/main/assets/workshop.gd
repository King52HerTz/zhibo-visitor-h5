extends Node2D

const Puzzle = preload("res://light_puzzle.gd")
const Pattern = preload("res://pattern_puzzle.gd")
const INK = Color("24494b")
const PAPER = Color("fff3d9")
const ORANGE = Color("ed9960")
var puzzle = Puzzle.new()
var bridge: Object
var beam := Vector2(390, 370)
var dragging := false
var time := 0.0
var idle := 0.0
var saved := false
var hint := "把窗边的光，移到衣服上看看"
var font: Font = ThemeDB.fallback_font
var abo: Texture2D
var background: Texture2D
var sprites: Texture2D
var pattern = Pattern.new()
enum Stage { LIGHT, PATTERN, STAMP, FINISHED }
var stage: int = Stage.LIGHT
var selected := -1
var piece := Vector2.ZERO
var stamping := false
var stamp_selected := false
var stamp_position := Vector2(990, 375)
var transition := 0.0
var sparkles := 0.0
var chime: AudioStreamPlayer

func raw_texture(path: String) -> Texture2D:
	if not ResourceLoader.exists(path):
		return null
	return load(path) as Texture2D

func _ready() -> void:
	if Engine.has_singleton("MuseumPuzzle") and Engine.get_singleton("MuseumPuzzle").is_story_slice():
		get_tree().change_scene_to_file.call_deferred("res://story_slice.tscn")
		return
	chime = AudioStreamPlayer.new()
	chime.volume_db = -20
	add_child(chime)
	for path in ["/system/fonts/NotoSansCJK-Regular.ttc", "C:/Windows/Fonts/msyh.ttc"]:
		if FileAccess.file_exists(path):
			var local_font := FontFile.new()
			local_font.data = FileAccess.get_file_as_bytes(path)
			font = local_font
			break
	abo = raw_texture("res://abo.png")
	background = raw_texture("res://puzzle_workshop_bg.png")
	sprites = raw_texture("res://puzzle_items.png")
	if Engine.has_singleton("MuseumPuzzle"):
		bridge = Engine.get_singleton("MuseumPuzzle")
		puzzle.mask = bridge.load_checkpoint()
		if puzzle.complete():
			saved = bridge.complete_light()
			if saved:
				pattern.mask = bridge.load_pattern_checkpoint()
				stage = Stage.PATTERN
				hint = "另一件衣服藏着花叶，把纹样送回对应的位置"
				if pattern.complete() and bridge.complete_pattern():
					stage = Stage.FINISHED if bridge.is_stamped() else Stage.STAMP
					hint = "这页故事已经补好了，随时可以再来" if stage == Stage.FINISHED else "拿起右边的印章，盖在手帐的圆圈里"
			else:
				hint = "发现已恢复，点衣服重试保存"
		else:
			bridge.play_workshop()
	queue_redraw()

func _process(delta: float) -> void:
	time += delta
	idle += delta
	sparkles = maxf(0, sparkles - delta)
	if transition > 0:
		transition -= delta
		if transition <= 0 and saved and stage == Stage.LIGHT:
			stage = Stage.PATTERN
			hint = "另一件衣服藏着花叶，把纹样送回对应的位置"
	queue_redraw()

func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_PAUSED:
		dragging = false
		stamping = false
		stamp_selected = false
		selected = -1
	if what == NOTIFICATION_WM_GO_BACK_REQUEST:
		leave()

func leave() -> void:
	if bridge:
		bridge.leave_game()
	else:
		get_tree().quit()

func _input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		var point := get_global_mouse_position()
		if event.pressed:
			idle = 0
			if Rect2(1100, 656, 170, 64).has_point(point):
				if bridge:
					var licenses := Engine.get_license_text()
					var details: Dictionary = Engine.get_license_info()
					for name in details:
						licenses += "\n\n" + str(name) + "\n" + str(details[name])
					bridge.show_licenses(licenses)
			elif Rect2(30, 24, 120, 62).has_point(point):
				leave()
			elif Rect2(1130, 24, 120, 62).has_point(point):
				if stage == Stage.PATTERN and pattern.complete():
					place_pattern(point)
					return
				hint = "移动光团，照亮衣服上的三枚小叶子" if stage == Stage.LIGHT else "选右边的一片花叶，再点衣服上相同的轮廓" if stage == Stage.PATTERN else "把右边的印章拖进手帐圆圈，也可以先点印章再点圆圈"
				idle = 20
			elif stage == Stage.PATTERN:
				if selected >= 0:
					place_pattern(point)
				else:
					for index in Pattern.HOMES.size():
						if not pattern.mask & (1 << index) and point.distance_to(Pattern.HOMES[index]) < 65:
							selected = index
							piece = point
			elif stage == Stage.STAMP:
				if point.distance_to(stamp_position) < 85:
					stamping = true
					stamp_selected = true
				elif stamp_selected:
					apply_stamp(point)
			elif stage == Stage.LIGHT and (point.distance_to(beam) < 100 or Rect2(470, 215, 415, 325).has_point(point)):
				dragging = true
				move_light(point)
		else:
			dragging = false
			if selected >= 0 and piece.distance_to(point) > 3:
				place_pattern(point)
			elif selected >= 0 and point.x < 900:
				place_pattern(point)
			if stamping:
				if point.distance_to(Vector2(990, 375)) > 85:
					apply_stamp(point)
				stamping = false
	elif event is InputEventMouseMotion and dragging:
		move_light(get_global_mouse_position())
	elif event is InputEventMouseMotion and selected >= 0:
		piece = get_global_mouse_position()
	elif event is InputEventMouseMotion and stamping:
		stamp_position = get_global_mouse_position()

func place_pattern(point: Vector2) -> void:
	var previous: int = pattern.mask
	if pattern.place(selected, point):
		if bridge and not bridge.save_pattern_checkpoint(pattern.mask):
			pattern.mask = previous
			hint = "暂时没能保存，请再试一次"
		else:
			sparkles = 1.0
			play_chime(630)
			hint = "花叶回来了！再看看其他的形状"
	else:
		hint = "仔细看轮廓，这片花叶更像哪一个？"
	selected = -1
	if pattern.complete():
		if not bridge or bridge.complete_pattern():
			stage = Stage.STAMP
			hint = "两条线索都找到了！拿起右边的印章"
		else:
			hint = "发现已记住，点右上角提示重试保存"

func apply_stamp(point: Vector2) -> void:
	if point.distance_to(Vector2(660, 365)) < 115:
		if not bridge or bridge.stamp():
			stage = Stage.FINISHED
			sparkles = 2.5
			play_chime(840)
			hint = "首章纪念章，属于你的衣坊发现！"
			if bridge:
				bridge.play_ending()
		else:
			hint = "印章暂时没能保存，请再盖一次"
	stamp_position = Vector2(990, 375)
	stamping = false
	stamp_selected = false

func move_light(point: Vector2) -> void:
	beam = point.clamp(Vector2(320, 205), Vector2(905, 535))
	var before: int = puzzle.mask
	if puzzle.observe(beam):
		if bridge and not bridge.save_checkpoint(puzzle.mask):
			puzzle.mask = before
			hint = "暂时没能保存，试着再照一次"
			return
		play_chime(430 + puzzle.discovered() * 100)
		hint = "光穿过了细细的纱线，再找找另一处" if not puzzle.complete() else "发现了！纱衣轻薄，光能透过它"
	if puzzle.complete() and not saved:
		saved = bridge.complete_light() if bridge else true
		if saved:
			transition = 2.8
			sparkles = 2.8
		if not saved:
			hint = "发现已记住，点一下衣服重试保存"

func panel(rect: Rect2, color: Color, radius: float = 18) -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = color
	style.set_corner_radius_all(int(radius))
	draw_style_box(style, rect)

func play_chime(frequency: float) -> void:
	var pcm := PackedByteArray()
	var count := 6615
	pcm.resize(count * 2)
	for index in range(count):
		var t := float(index) / 22050.0
		var sample := sin(t * frequency * TAU) * exp(-t * 15) * 12000
		pcm.encode_s16(index * 2, int(sample))
	var sound := AudioStreamWAV.new()
	sound.format = AudioStreamWAV.FORMAT_16_BITS
	sound.mix_rate = 22050
	sound.data = pcm
	chime.stream = sound
	chime.play()

func text_at(text: String, point: Vector2, size: int = 24, color: Color = INK) -> void:
	draw_string(font, point, text, HORIZONTAL_ALIGNMENT_LEFT, -1, size, color)

func sprite_at(index: int, rect: Rect2, alpha: float = 1.0) -> void:
	var half: float = sprites.get_width() / 2.0
	var split: float = sprites.get_height() * 0.5625
	var source := Rect2((index % 2) * half, 0 if index < 2 else split,
		half, split if index < 2 else sprites.get_height() - split)
	draw_texture_rect_region(sprites, rect, source, Color(1, 1, 1, alpha))

func _draw() -> void:
	# Background, garment, interaction objects and feedback are independent layers.
	draw_rect(Rect2(0, 0, 1280, 720), Color("d5e5d8"))
	draw_rect(Rect2(0, 0, 1280, 460), Color("eaf0df"))
	draw_rect(Rect2(0, 460, 1280, 260), Color("beccb8"))
	for x in range(0, 1300, 160):
		draw_line(Vector2(x, 460), Vector2(x - 120, 720), Color("a4b8a5"), 2)
	draw_line(Vector2(0, 590), Vector2(1280, 590), Color("a4b8a5"), 2)
	panel(Rect2(78, 105, 265, 285), Color("819e83"), 90)
	panel(Rect2(94, 120, 233, 252), Color("f9ddb1"), 80)
	draw_line(Vector2(210, 126), Vector2(210, 369), Color("819e83"), 12)
	draw_line(Vector2(100, 250), Vector2(320, 250), Color("819e83"), 12)
	draw_colored_polygon(PackedVector2Array([Vector2(120, 370), Vector2(315, 320), Vector2(895, 530), Vector2(490, 570)]), Color(1, 0.94, 0.7, 0.22))
	if background:
		draw_texture_rect(background, Rect2(0, 0, 1280, 720), false)
	# Tiny drifting paper dust and sway tie the room together without distracting motion.
	for index in range(16):
		var p := Vector2(90 + fmod(index * 73 + time * 7, 1110), 165 + fmod(index * 51 + time * 3, 365))
		draw_circle(p, 2.0, Color(1, 0.92, 0.63, 0.28))
	if stage == Stage.STAMP or stage == Stage.FINISHED:
		draw_journal()
		draw_hud()
		return
	# Clothes rail, backdrop, garment and thread detail occupy separate layers.
	draw_line(Vector2(495, 160), Vector2(875, 160), Color("ac7b50"), 13)
	draw_line(Vector2(515, 150), Vector2(505, 564), Color("ac7b50"), 12)
	draw_line(Vector2(850, 150), Vector2(867, 564), Color("ac7b50"), 12)
	var cloth := PackedVector2Array([Vector2(612, 224), Vector2(573, 253), Vector2(477, 325), Vector2(508, 377), Vector2(595, 334), Vector2(583, 519), Vector2(779, 519), Vector2(765, 334), Vector2(853, 376), Vector2(882, 325), Vector2(786, 253), Vector2(748, 224), Vector2(683, 266)])
	if sprites:
		sprite_at(0 if stage == Stage.LIGHT else 1, Rect2(478 + sin(time * 1.4) * 2, 161, 412, 388), 0.88 if stage == Stage.LIGHT else 1.0)
	else:
		draw_colored_polygon(cloth, Color(0.96, 0.94, 0.83, 0.78) if stage == Stage.LIGHT else Color("e6be9b"))
		draw_polyline(cloth + PackedVector2Array([cloth[0]]), Color("b6ac82"), 3, true)
		for x in range(605, 772, 9):
			draw_line(Vector2(x, 320), Vector2(x, 505), Color(0.6, 0.55, 0.34, 0.12), 1)
		for y in range(330, 510, 9):
			draw_line(Vector2(603, y), Vector2(770, y), Color(0.6, 0.55, 0.34, 0.12), 1)
	# Light responds directly to the hand; halo makes the movable object legible.
	if stage == Stage.LIGHT:
		for radius in range(110, 15, -12):
			draw_circle(beam, radius, Color(1, 0.95, 0.65, 0.035))
		draw_circle(beam, 21 + sin(time * 2) * 3, Color("ffe9a3"))
		draw_arc(beam, 32, 0, TAU, 36, Color("edbd6c"), 2, true)
	for index in Puzzle.POINTS.size():
		var point: Vector2 = Puzzle.POINTS[index]
		var found: bool = ((puzzle.mask if stage == Stage.LIGHT else pattern.mask) & (1 << index)) != 0
		var pulse := 1.0 + sin(time * 3 + index) * 0.1
		if found or idle > 12:
			draw_arc(point, 31 * pulse, 0, TAU, 32, Color("79a28a") if found else ORANGE, 3, true)
		var color := Color("71a894") if found else Color("b6ba9f")
		if stage == Stage.LIGHT and not found:
			color.a = clampf(1.0 - point.distance_to(beam) / 160.0, 0.12, 1.0)
		leaf(index, point, color)
	if stage == Stage.PATTERN:
		for index in Pattern.HOMES.size():
			if not pattern.mask & (1 << index):
				panel(Rect2(Pattern.HOMES[index] - Vector2(45, 45), Vector2(90, 90)), PAPER, 30)
				leaf(index, piece if index == selected else Pattern.HOMES[index], ORANGE)
	# A small desk is part of the room, not a navigation bar.
	if not background:
		panel(Rect2(947, 427, 253, 24), Color("bb8661"), 8)
		draw_line(Vector2(970, 450), Vector2(955, 577), Color("967356"), 13)
		draw_line(Vector2(1170, 450), Vector2(1185, 577), Color("967356"), 13)
	panel(Rect2(986, 432, 170, 46), PAPER, 6)
	for index in range(3):
		draw_circle(Vector2(1018 + index * 45, 455), 10, ORANGE if (puzzle.mask if stage == Stage.LIGHT else pattern.mask) & (1 << index) else Color("d7d9c8"))
	if abo:
		draw_texture_rect(abo, Rect2(106, 409 + sin(time * 1.8) * 5, 216, 200), false)
	else:
		draw_colored_polygon(PackedVector2Array([Vector2(138, 493), Vector2(208, 432), Vector2(288, 492), Vector2(210, 564)]), PAPER)
	panel(Rect2(30, 24, 120, 62), PAPER)
	text_at("← 返回", Vector2(44, 64), 25)
	panel(Rect2(1130, 24, 120, 62), PAPER)
	text_at("? 提示", Vector2(1144, 64), 25)
	text_at("阿帛的衣坊", Vector2(495, 87), 32)
	text_at("素纱单衣 · 发现轻与薄" if stage == Stage.LIGHT else "印花敷彩纱丝绵袍 · 寻找花叶", Vector2(455, 119), 20, INK)
	panel(Rect2(302, 598, 760, 83), PAPER, 28)
	text_at(hint, Vector2(335, 633), 24)
	text_at("原创故事 · 不触摸真实文物" if stage == Stage.PATTERN else "轻与薄  %d / 3%s" % [puzzle.discovered(), " · 已收进手帐" if saved else ""], Vector2(335, 662), 19, Color("68877b"))
	draw_sparkles()
	text_at("Godot · 开源许可", Vector2(1103, 700), 16, PAPER)

func leaf(index: int, point: Vector2, color: Color) -> void:
	if index == 0:
		draw_colored_polygon(PackedVector2Array([point + Vector2(-2, 24), point + Vector2(-26, -3), point + Vector2(-4, -29), point + Vector2(24, -7)]), color)
		draw_line(point + Vector2(-2, 24), point + Vector2(-4, -22), PAPER, 2)
	elif index == 1:
		for i in range(5):
			draw_circle(point + Vector2.from_angle(i * TAU / 5) * 17, 12, color)
		draw_circle(point, 8, PAPER)
	else:
		draw_line(point + Vector2(-26, 20), point + Vector2(23, -23), color, 5)
		for i in range(3):
			draw_circle(point + Vector2(-17 + i * 17, 2 - i * 12), 10, color)

func draw_journal() -> void:
	if sprites:
		sprite_at(2, Rect2(355, 132, 585, 432))
	else:
		panel(Rect2(400, 163, 480, 380), Color("9c7155"), 24)
		panel(Rect2(413, 150, 453, 374), PAPER, 20)
		draw_line(Vector2(452, 172), Vector2(452, 505), Color("d8ba87"), 3)
	text_at("衣服里的秘密", Vector2(488, 213), 30)
	text_at("光透过细纱 · 花叶藏在衣上", Vector2(485, 250), 19)
	draw_arc(Vector2(660, 365), 94, 0, TAU, 64, Color("d6bd95"), 3, true)
	if stage == Stage.FINISHED:
		draw_circle(Vector2(660, 365), 80, Color("d98b57"))
		draw_arc(Vector2(660, 365), 71, 0, TAU, 64, PAPER, 3, true)
		leaf(1, Vector2(660, 354), PAPER)
		text_at("衣坊发现", Vector2(612, 405), 24, PAPER)
	else:
		if sprites:
			sprite_at(3, Rect2(stamp_position - Vector2(66, 99), Vector2(132, 132)))
		else:
			panel(Rect2(stamp_position - Vector2(36, 21), Vector2(72, 46)), INK, 8)
			panel(Rect2(stamp_position - Vector2(16, 74), Vector2(32, 58)), ORANGE, 12)
		if idle > 10:
			draw_arc(stamp_position, 75 + sin(time * 3) * 5, 0, TAU, 48, ORANGE, 3, true)
	text_at("原创纪念 · 非文物修复", Vector2(495, 480), 18, Color("68877b"))
	if abo:
		draw_texture_rect(abo, Rect2(105, 405 + sin(time * 1.8) * 5, 230, 210), false)
	draw_sparkles()

func draw_hud() -> void:
	panel(Rect2(30, 24, 120, 62), PAPER)
	text_at("← 返回", Vector2(44, 64), 25)
	panel(Rect2(1130, 24, 120, 62), PAPER)
	text_at("? 提示", Vector2(1144, 64), 25)
	panel(Rect2(302, 598, 760, 83), PAPER, 28)
	text_at(hint, Vector2(335, 638), 23)
	text_at("返回后可以在我的手帐查看纪念页", Vector2(335, 666), 18, Color("68877b"))
	text_at("Godot · 开源许可", Vector2(1103, 700), 16, PAPER)

func draw_sparkles() -> void:
	if sparkles <= 0:
		return
	for i in range(18):
		var p := Vector2(660, 360) + Vector2.from_angle(i * TAU / 18 + time) * (90 + (2.8 - sparkles) * 55)
		draw_circle(p, 4, Color(1, 0.73, 0.34, minf(1, sparkles)))
