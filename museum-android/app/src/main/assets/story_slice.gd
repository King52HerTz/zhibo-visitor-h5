extends Node2D

const Rules = preload("res://story_slice_rules.gd")
const INK = Color("253d4a")
const PAPER = Color("fff7e8")
const BLUE = Color("4b9fb0")
const ORANGE = Color("d96b3d")
const MUTED = Color("52676c")
const EXIT = Rect2(1100, 16, 160, 88)
const HOME = Rect2(920, 16, 160, 88)
const NEXT = Rect2(850, 600, 350, 96)
const CLIP_TITLES = ["展开横卷", "走进观察台", "收下衣的发现", "同一座墓的线索", "走进画中", "收下画的发现", "另一座墓的动作图", "我们的三件发现"]
const CLIP_LINES = ["三件文物，等着我们发现。先看看轻薄的衣。", "让光照过两块体验布，看看有什么不同。", "衣的线索收好了，下一件发现藏在画里。", "衣与这幅帛画，都来自辛追墓。", "先看上、中、下，再把三条线索送回原处。", "古人把他们想象的天地画进了帛画。", "这件文物来自三号墓。只观察图，不用跟着做。", "三件文物的线索，都收进同一幅横卷。"]

var rules = Rules.new()
var bridge: Object
var font: Font = ThemeDB.fallback_font
var page := 0
var compared := 0
var selected := -1
var revealed := 0
var placed := 0
var feedback := ""
var pending_bit := 0
var stamped := false
var slots: Array[int] = []
var slot_target := 1
var waiting := false
var hint_open := false

func _ready() -> void:
	for path in ["/system/fonts/NotoSansCJK-Regular.ttc", "C:/Windows/Fonts/msyh.ttc"]:
		if FileAccess.file_exists(path):
			var local_font := FontFile.new()
			local_font.data = FileAccess.get_file_as_bytes(path)
			font = local_font
			break
	if Engine.has_singleton("MuseumPuzzle"):
		bridge = Engine.get_singleton("MuseumPuzzle")
		rules.mask = bridge.load_story_slice_checkpoint()
		stamped = bridge.is_story_stamped()
		bridge.story_clip_finished.connect(_clip_finished)
		bridge.story_action.connect(_accessible_action)
	placed = (rules.mask >> 3) & 7
	if rules.mask == 0:
		begin_slots([0, 1], 1)
	else:
		page = 10 if stamped else rules.stage()
	queue_redraw()

func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_GO_BACK_REQUEST:
		if waiting: return
		if hint_open:
			hint_open = false
		elif page != 10:
			page = 10
		else:
			leave()
		queue_redraw()

func leave() -> void:
	if bridge: bridge.leave_game()
	else: get_tree().quit()

func begin_slots(ids: Array[int], target: int) -> void:
	slots.assign(ids)
	slot_target = target
	page = 0
	feedback = ""
	queue_redraw()

func advance_slot() -> void:
	waiting = false
	if not slots.is_empty(): slots.pop_front()
	if slots.is_empty(): page = slot_target
	queue_redraw()

func _clip_finished(_status: String) -> void:
	if waiting: advance_slot()

func _accessible_action(point: String) -> void:
	var coordinates := point.split(",")
	handle_tap(Vector2(coordinates[0].to_float(), coordinates[1].to_float()))

func record(bit: int) -> bool:
	var previous: int = rules.mask
	if not rules.mark(bit): return false
	if bridge and not bridge.save_story_slice_checkpoint(rules.mask):
		rules.mask = previous
		pending_bit = bit
		feedback = "还没保存好，点右下角重试。"
		queue_redraw()
		return false
	pending_bit = 0
	feedback = ""
	return true

func advance_action(bit: int) -> void:
	if not record(bit): return
	match bit:
		Rules.LIGHT: page = 2
		Rules.COLLAR: page = 3
		Rules.JUDGED: page = 4
		Rules.PAINTING_TOP, Rules.PAINTING_MIDDLE, Rules.PAINTING_BOTTOM:
			placed |= bit >> 3
			selected = -1
			if placed == 7: page = 6
		Rules.POSE_MATCH: page = 7
		Rules.POSE_COMPARE: page = 8
		Rules.TOMB: page = 9
	queue_redraw()

func open_chapter(chapter: int) -> void:
	if not rules.unlocked(chapter):
		feedback = "先收好前一件发现，再来看看这里。"
		return
	compared = 0
	selected = -1
	revealed = 0
	placed = 0
	feedback = ""
	if chapter == 0: begin_slots([1], 1)
	elif chapter == 1: begin_slots([4], 5)
	else: begin_slots([6], 11)

func _input(event: InputEvent) -> void:
	if not (event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed): return
	handle_tap(get_global_mouse_position())

func handle_tap(point: Vector2) -> void:
	if waiting: return
	if EXIT.has_point(point):
		leave()
		return
	if HOME.has_point(point):
		page = 10
		feedback = ""
		hint_open = false
		queue_redraw()
		return
	if Rect2(760, 16, 144, 88).has_point(point):
		hint_open = not hint_open
		queue_redraw()
		return
	if hint_open:
		hint_open = false
		queue_redraw()
		return
	if pending_bit != 0:
		if NEXT.has_point(point): advance_action(pending_bit)
		return
	match page:
		0:
			if NEXT.has_point(point):
				waiting = bridge != null and bridge.play_story_clip(slots[0])
				if not waiting: advance_slot()
		1:
			if Rect2(172, 270, 418, 270).has_point(point): compared |= 1
			if Rect2(690, 270, 418, 270).has_point(point): compared |= 2
			if compared == 3 and NEXT.has_point(point): advance_action(Rules.LIGHT)
		2:
			if Rect2(420, 270, 450, 290).has_point(point): advance_action(Rules.COLLAR)
		3:
			if Rect2(150, 350, 420, 190).has_point(point): advance_action(Rules.JUDGED)
			elif Rect2(710, 350, 420, 190).has_point(point): feedback = "这是猜想，还没有证据。透光才是你看见的线索。"
		4:
			if NEXT.has_point(point): begin_slots([2, 3, 4], 5)
		5:
			for index in range(3):
				if Rect2(155, 280 + index * 104, 320, 88).has_point(point) and not placed & (1 << index):
					selected = index
					revealed |= 1 << index
			for index in range(3):
				if Rect2(650, 275 + index * 104, 450, 88).has_point(point) and selected >= 0:
					if index != selected: feedback = "位置还不一样，看看线索卡上的位置。"
					elif selected != [0, 1, 3, 7].find(placed): feedback = "先从上部开始，再看中部、下部。"
					else: advance_action(1 << (selected + 3))
		6:
			if NEXT.has_point(point): begin_slots([5, 6], 11)
		11:
			if Rect2(790, 295, 320, 240).has_point(point): advance_action(Rules.POSE_MATCH)
			elif Rect2(420, 295, 320, 240).has_point(point): feedback = "看看手臂：方向不一样，可以再试。"
		7:
			for index in range(3):
				if Rect2(155 + index * 350, 290, 320, 245).has_point(point):
					if index == 1: advance_action(Rules.POSE_COMPARE)
					else: feedback = "找一找：哪一张的手臂与刚才不同？"
		8:
			if Rect2(710, 350, 420, 190).has_point(point): advance_action(Rules.TOMB)
			elif Rect2(150, 350, 420, 190).has_point(point): feedback = "再看展签：《导引图》来自三号墓。"
		9:
			if NEXT.has_point(point): begin_slots([7], 10)
		10:
			for index in range(3):
				if Rect2(130 + index * 350, 265, 320, 250).has_point(point): open_chapter(index)
			if NEXT.has_point(point) and rules.mask == Rules.CHAPTER_COMPLETE:
				stamped = bridge == null or bridge.stamp_story()
				feedback = "三件发现已收好。随时点上面的卡片再看看。" if stamped else "纪念章还没保存好，请再试一次。"
	queue_redraw()

func block(rect: Rect2, color: Color) -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = color
	style.set_corner_radius_all(14)
	draw_style_box(style, rect)

func label(text: String, x: float, y: float, size: int = 28, color: Color = INK) -> void:
	draw_string(font, Vector2(x, y), text, HORIZONTAL_ALIGNMENT_LEFT, -1, size, color)

func button(text: String, rect: Rect2, active := true) -> void:
	block(rect, ORANGE if active else Color("b9beb5"))
	label(text, rect.position.x + 24, rect.position.y + rect.size.y * 0.62, 28, Color.WHITE)

func title(text: String, note: String) -> void:
	label(text, 140, 205, 36)
	label(note, 140, 250, 22, MUTED)

func accessible_action(text: String, x: float, y: float, enabled := true) -> Dictionary:
	return {"text": text, "x": x, "y": y, "enabled": enabled}

func hint_text() -> String:
	var hints = {0: "短片的位置已经留好，点继续就能接着探索。", 1: "分别点两块布，然后看看它们让光透过多少。", 2: "点圆圆的观察窗，看看领口的轮廓。", 3: "选你刚才亲眼看见的，不用猜古人的生活。", 5: "先点左边线索卡，再点右边相同位置的空间。", 11: "只看示意图的手臂，找方向相同的一张。", 7: "三张里，有一张手臂方向与另外两张不同。", 8: "展签写着三号墓；衣和这幅帛画才来自一号墓。", 10: "点已经解锁的卡片重看；三件发现齐了才能盖新章。"}
	return hints.get(page, "发现卡已经收好，点右下角继续。")

func accessible_controls() -> void:
	if not bridge: return
	var descriptions = {
		1: "衣：点亮两块体验布，比较透光。这是互动示意。",
		2: "衣：观察衣领。这是形制示意，不是触摸原件。",
		3: "衣：哪句话是刚才亲眼看到的？选错可以再试。",
		4: "衣的发现：比较透光，观察衣领。素纱单衣来自马王堆一号墓。不能由此证明辛追亲手穿过。",
		5: "画：分层位置示意，不是真文物图。先点线索，再送回上部、中部、下部。",
		6: "画的发现：这幅 T 形帛画来自辛追墓。古人画出了他们想象的天地。",
		11: "图：静态姿态比较示意。参考图手臂朝上，选择 A 手臂朝下，选择 B 手臂朝上。只观察，不用跟着做。",
		7: "图：三张静态示意，A 和 C 手臂朝上，B 手臂朝下。哪张与另外两张不同？",
		8: "图：为《导引图》选择展签。出土线索是马王堆三号墓。",
		9: "图的发现：《导引图》来自三号墓。古人用图记录身体活动。它不是辛追墓出土物，也不是她使用过的证据。",
		10: "发现横卷：素纱单衣、这幅 T 形帛画来自一号墓，《导引图》来自三号墓。"
	}
	var text: String = descriptions.get(page, "")
	var actions: Array = []
	if page == 0: text = CLIP_TITLES[slots[0]] + "。" + CLIP_LINES[slots[0]] + "短片预留，可继续探索。"
	if pending_bit != 0:
		actions.append(accessible_action("重试保存", 1000, 650))
	else:
		match page:
			0, 4, 6, 9: actions.append(accessible_action("继续探索", 1000, 650, not waiting))
			1:
				actions.append(accessible_action("观察厚布：透过的光较少" if compared & 1 else "点亮厚布示意", 350, 400))
				actions.append(accessible_action("观察纱衣：透过的光较多" if compared & 2 else "点亮纱衣示意", 850, 400))
				actions.append(accessible_action("我看出不同了", 1000, 650, compared == 3))
			2: actions.append(accessible_action("观察衣领轮廓", 640, 400))
			3:
				actions.append(accessible_action("两块布透光不同", 350, 440))
				actions.append(accessible_action("辛追亲手穿过它", 850, 440))
			5:
				for index in range(3):
					var name: String = ["上部", "中部", "下部"][index]
					actions.append(accessible_action("观察%s线索%s" % [name, "：已归位" if placed & (1 << index) else ""], 300, 320 + index * 104, not placed & (1 << index)))
				for index in range(3): actions.append(accessible_action("送到画的" + ["上部", "中部", "下部"][index], 850, 320 + index * 104, selected >= 0))
			11:
				actions.append(accessible_action("选择 A：手臂朝下", 600, 400))
				actions.append(accessible_action("选择 B：手臂朝上", 950, 400))
			7:
				for index in range(3): actions.append(accessible_action("选择 " + ["A", "B", "C"][index], 300 + index * 350, 400))
			8:
				actions.append(accessible_action("一号墓", 350, 440))
				actions.append(accessible_action("三号墓", 850, 440))
			10:
				for index in range(3): actions.append(accessible_action(["探索素纱单衣", "探索 T 形帛画", "探索《导引图》"][index], 300 + index * 350, 390, rules.unlocked(index)))
				actions.append(accessible_action("纪念章已收好" if stamped else "盖上三件发现章", 1000, 650, rules.mask == 511 and not stamped))
	if feedback != "": text += "\n" + feedback
	if hint_open:
		text += "\n" + hint_text()
		actions = [accessible_action("收起提示", 640, 400)]
	else:
		actions.append(accessible_action("阿帛的提示", 830, 60))
	actions.append(accessible_action("返回发现横卷", 1000, 60))
	actions.append(accessible_action("退出探索", 1180, 60))
	bridge.update_story_accessibility(text, JSON.stringify(actions))

func _draw() -> void:
	accessible_controls()
	block(Rect2(0, 0, 1280, 720), Color("dceef0"))
	block(Rect2(50, 115, 1180, 590), PAPER)
	label("湘遇 · 马王堆的三件发现", 82, 65, 28)
	label("衣  /  画  /  图    ·    已收好 %d / 3" % [int(rules.complete(0)) + int(rules.complete(1)) + int(rules.complete(2))], 82, 101, 20, MUTED)
	button("提示", Rect2(760, 16, 144, 88))
	button("横卷", HOME)
	button("退出", EXIT)
	match page:
		0: clip_slot()
		1: light_page()
		2: collar_page()
		3: judgement_page()
		4: response_page()
		5: painting_page()
		6: painting_response()
		11: pose_match()
		7: pose_compare()
		8: tomb_page()
		9: daoyin_response()
		10: journal_page()
	if feedback != "": label(feedback, 140, 585, 22, Color("9d392b"))
	if pending_bit != 0: button("重试保存", NEXT)
	if hint_open:
		block(Rect2(120, 270, 1040, 270), Color("eff3dc"))
		label("阿帛的小提示", 170, 338, 34)
		label(hint_text(), 170, 410, 25)
		label("点任意位置收起提示", 170, 488, 22, MUTED)

func scroll(rect: Rect2) -> void:
	block(rect, Color("efd9b1"))
	for x in [rect.position.x - 18, rect.end.x - 14]:
		block(Rect2(x, rect.position.y - 20, 32, rect.size.y + 40), Color("ab7655"))

func clip_slot() -> void:
	var index: int = slots[0]
	title(CLIP_TITLES[index], CLIP_LINES[index])
	scroll(Rect2(150, 300, 980, 220))
	label("短片预留区", 505, 390, 38)
	label("视频稍后加入，现在可以继续探索", 355, 458, 25, MUTED)
	button("继续  →", NEXT)

func light_page() -> void:
	title("衣 · 让光穿过两块体验布", "互动示意。分别点亮两边，看看有什么不同。")
	block(Rect2(172, 278, 418, 240), Color("d6b6a4") if compared & 1 else Color("e6d4c5"))
	block(Rect2(690, 278, 418, 240), Color("a9dce0") if compared & 2 else Color("e8e1ce"))
	for index in range(6):
		draw_line(Vector2(205 + index * 58, 300), Vector2(205 + index * 58, 492), Color("fff9ed", 0.4 if compared & 1 else 0.9), 9)
		draw_line(Vector2(720 + index * 58, 300), Vector2(720 + index * 58, 492), Color("fff9ed", 0.9 if compared & 2 else 0.2), 3)
	label("厚布示意 · %s" % ("已观察" if compared & 1 else "点这里"), 200, 557, 24)
	label("纱衣示意 · %s" % ("已观察" if compared & 2 else "点这里"), 718, 557, 24)
	button("我看出不同了  →", NEXT, compared == 3)

func collar_page() -> void:
	title("衣 · 靠近看看衣领", "点观察窗，看清衣领轮廓。这不是触摸原件。")
	block(Rect2(420, 310, 450, 240), Color("e7d3ac"))
	draw_line(Vector2(480, 340), Vector2(640, 470), BLUE, 12)
	draw_line(Vector2(800, 340), Vector2(640, 470), BLUE, 12)
	draw_circle(Vector2(640, 390), 95, Color("ffffff", 0.35))
	draw_arc(Vector2(640, 390), 95, 0, TAU, 60, ORANGE, 8)
	label("形制观察示意", 490, 610, 25, MUTED)

func judgement_page() -> void:
	title("衣 · 哪句话是你亲眼观察到的？", "选错也能再试，不用猜古人的生活。")
	block(Rect2(150, 350, 420, 190), Color("b5dde1"))
	block(Rect2(710, 350, 420, 190), Color("f4d8c3"))
	label("两块布透光不同", 182, 443, 29)
	label("辛追亲手穿过它", 738, 443, 29)
	label("我看到了", 182, 493, 22, MUTED)
	label("有没有证据？", 738, 493, 22, MUTED)

func response_page() -> void:
	title("衣的发现 · 织造巧思", "第一件发现已经收好。接着走进画中。")
	block(Rect2(178, 290, 920, 250), Color("b8dcde"))
	label("你比较了透光，也观察了衣领。", 230, 352, 29)
	label("素纱单衣出土于马王堆一号墓。", 230, 415, 28)
	label("我们不由此猜测，辛追是否亲手穿过它。", 230, 480, 24, MUTED)
	button("继续看画  →", NEXT)

func painting_page() -> void:
	title("画 · 把线索送回上、中、下", "分层位置示意，不是真文物图。点线索，再点相同的位置。")
	var names = ["上部", "中部", "下部"]
	for index in range(3):
		var done: bool = (placed & (1 << index)) != 0
		var card := Rect2(155, 280 + index * 104, 320, 88)
		block(card, Color("c8dbbb") if done else Color("b5dde1") if selected == index else Color("ebdfc7"))
		label("线索 %s · %s" % [names[index], "已归位" if done else "已看清" if revealed & (1 << index) else "点开看看"], 180, 336 + index * 104, 24)
		var target := Rect2(650, 275 + index * 104, 450, 88)
		if index > 0:
			target.position.x += 85
			target.size.x -= 170
		block(target, Color("c8dbbb") if done else Color("dfc39c"))
		label("画的%s%s" % [names[index], " · 已归位" if done else ""], target.position.x + 26, target.position.y + 55, 24)
	if selected >= 0: label("你看到的线索，在画的%s。" % names[selected], 160, 638, 25)

func painting_response() -> void:
	title("画的发现 · 想象中的天地", "第二件发现已经收好。第三件来自另一座墓。")
	block(Rect2(178, 290, 920, 250), Color("ead9bf"))
	label("你把上部、中部和下部的线索送回了原处。", 230, 350, 27)
	label("这幅 T 形帛画，来自辛追墓。", 230, 415, 28)
	label("古人画出了他们想象的天地。", 230, 478, 27)
	button("继续看动作图  →", NEXT)

func figure(center: Vector2, raised: bool, color: Color = INK) -> void:
	draw_circle(center + Vector2(0, -55), 18, color)
	draw_line(center + Vector2(0, -30), center + Vector2(0, 50), color, 8)
	draw_line(center + Vector2(0, -10), center + Vector2(-55, -42 if raised else 32), color, 8)
	draw_line(center + Vector2(0, -10), center + Vector2(55, -42 if raised else 32), color, 8)
	draw_line(center + Vector2(0, 50), center + Vector2(-35, 98), color, 8)
	draw_line(center + Vector2(0, 50), center + Vector2(35, 98), color, 8)

func pose_match() -> void:
	title("图 · 哪一张的手臂方向相同？", "姿态比较示意，不是原图局部。只观察图，不用跟着做。")
	for index in range(3):
		block(Rect2(80 + index * 370, 295, 320, 240), Color("ebdfc7") if index == 0 else Color("b5dde1"))
		figure(Vector2(240 + index * 370, 400), index != 1)
		label(["观察这张", "选择 A", "选择 B"][index], 160 + index * 370, 565, 26)

func pose_compare() -> void:
	title("图 · 找到手臂方向不同的一张", "三张静态示意图，哪一张与另外两张不同？")
	for index in range(3):
		block(Rect2(155 + index * 350, 290, 320, 245), Color("b5dde1"))
		figure(Vector2(315 + index * 350, 400), index != 1)
		label(["A", "B", "C"][index], 300 + index * 350, 565, 28)

func tomb_page() -> void:
	title("图 · 给《导引图》选一张展签", "展签线索：《导引图》出土于马王堆三号墓。")
	block(Rect2(150, 350, 420, 190), Color("ead9bf"))
	block(Rect2(710, 350, 420, 190), Color("b5dde1"))
	label("一号墓", 265, 450, 38)
	label("三号墓", 825, 450, 38)

func daoyin_response() -> void:
	title("图的发现 · 古人的动作图", "三件发现齐了。一起回到横卷看看。")
	block(Rect2(178, 290, 920, 250), Color("c8dbbb"))
	label("《导引图》出土于马王堆三号墓。", 230, 350, 29)
	label("古人用图记录身体活动。", 230, 415, 28)
	label("它不是辛追墓出土物，也不是她使用过的证据。", 230, 480, 24)
	button("回到发现横卷  →", NEXT)

func journal_page() -> void:
	title("把三件发现收进同一幅横卷", "点已解锁的卡片，可以重新观察。重玩会保留你的发现。")
	scroll(Rect2(120, 280, 1070, 250))
	var names = ["素纱单衣", "T 形帛画", "《导引图》"]
	var lessons = ["织造巧思", "想象中的天地", "古人的动作图"]
	for index in range(3):
		var x := 145 + index * 350
		label(names[index], x + 15, 350, 29)
		label("一号墓" if index < 2 else "三号墓", x + 15, 404, 25)
		label(lessons[index] if rules.complete(index) else "可以探索" if rules.unlocked(index) else "等待前一件发现", x + 15, 466, 25, MUTED)
		label("已收好 · 点此重看" if rules.complete(index) else "点这里开始" if rules.unlocked(index) else "暂未解锁", x + 15, 510, 22)
	if rules.mask == Rules.CHAPTER_COMPLETE: button("纪念章已收好" if stamped else "盖上三件发现章", NEXT)
	else: label("三件发现齐了，再盖上属于这段旅程的新章。", 150, 647, 24, MUTED)
