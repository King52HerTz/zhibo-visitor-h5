extends RefCounted

const LIGHT := 1
const COLLAR := 2
const JUDGED := 4
const COMPLETE := LIGHT | COLLAR | JUDGED
const PAINTING_TOP := 8
const PAINTING_MIDDLE := 16
const PAINTING_BOTTOM := 32
const POSE_MATCH := 64
const POSE_COMPARE := 128
const TOMB := 256
const CHAPTER_COMPLETE := 511

var mask := 0

func mark(bit: int) -> bool:
	if bit not in [LIGHT, COLLAR, JUDGED, PAINTING_TOP, PAINTING_MIDDLE, PAINTING_BOTTOM, POSE_MATCH, POSE_COMPARE, TOMB]:
		return false
	if mask & bit:
		return true
	if bit != mask + 1:
		return false
	mask |= bit
	return true

func stage() -> int:
	if mask == CHAPTER_COMPLETE:
		return 9
	if mask >= 255:
		return 8
	if mask >= 127:
		return 7
	if mask >= 63:
		return 6
	if mask >= 15:
		return 5
	if mask & JUDGED:
		return 4
	if mask & COLLAR:
		return 3
	if mask & LIGHT:
		return 2
	return 1

func unlocked(chapter: int) -> bool:
	return chapter == 0 or (chapter == 1 and mask & COMPLETE == COMPLETE) or (chapter == 2 and mask & 63 == 63)

func complete(chapter: int) -> bool:
	var required: int = [7, 63, CHAPTER_COMPLETE][chapter]
	return mask & required == required
