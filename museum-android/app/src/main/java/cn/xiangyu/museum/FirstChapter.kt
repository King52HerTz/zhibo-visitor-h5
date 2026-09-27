package cn.xiangyu.museum

enum class Audience(val label: String) { CHILD("儿童模式"), ADULT("成人模式") }

data class Relic(val id: String, val title: String, val childText: String, val adultText: String)

object FirstChapter {
    val relics = listOf(
        Relic("silk-plain-straight", "直裾素纱单衣",
            "这是一件西汉时期的素纱单衣，出土于长沙马王堆一号墓。它用丝纱制成。仔细看，衣料轻薄，让我们认识古人的织造本领。",
            "马王堆一号墓出土的素纱单衣，是观察汉代丝织工艺的一个窗口。这里讨论直裾对象，不混用两件单衣的形制、尺寸和重量。穿着方式的解释需要结合具体证据。"),
        Relic("silk-painted-robe", "印花敷彩纱丝绵袍",
            "这类衣物的纹样，让人想到植物的枝叶和花。有的部分印出来，有的部分由工匠绘画完成。你在生活中见过哪些衣服纹样？",
            "印花与绘彩相结合，让织物的装饰成为另一条线索。纹样包含植物化的枝蔓与花叶。这里是主题介绍，具体馆藏对象和图片仍待核验。")
    )
    const val sourceUrl = "https://new.artmuseumonline.org/art/art/visitGuide/xwgg/2026/07/04/2c906d9b9e0127fe019f3bdb331225cb.html"
    fun text(relic: Relic, audience: Audience) = when (audience) {
        Audience.CHILD -> relic.childText
        Audience.ADULT -> relic.adultText
    }
}
