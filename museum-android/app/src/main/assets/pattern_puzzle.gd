extends RefCounted

const POINTS = [Vector2(615, 305), Vector2(755, 365), Vector2(650, 455)]
const HOMES = [Vector2(1030, 250), Vector2(1100, 325), Vector2(1020, 390)]
var mask: int = 0

func place(index: int, point: Vector2) -> bool:
	if index < 0 or index >= POINTS.size() or point.distance_to(POINTS[index]) > 65:
		return false
	mask |= 1 << index
	return true

func complete() -> bool:
	return mask == 7
