extends RefCounted

const POINTS = [Vector2(615, 305), Vector2(755, 365), Vector2(650, 455)]
const RADIUS = 62.0
var mask: int = 0

func observe(point: Vector2) -> bool:
	var previous := mask
	for index in POINTS.size():
		if point.distance_to(POINTS[index]) <= RADIUS:
			mask |= 1 << index
	return previous != mask

func discovered() -> int:
	var count := 0
	for index in POINTS.size():
		if mask & (1 << index):
			count += 1
	return count

func complete() -> bool:
	return mask == 7
