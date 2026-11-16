package cn.xiangyu.museum

import android.net.Uri

internal enum class ImageLicense(val title: String, val url: String) {
    PUBLIC_DOMAIN("公有领域", "https://creativecommons.org/publicdomain/mark/1.0/"),
    CC0("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/"),
    BY_3("CC BY 3.0", "https://creativecommons.org/licenses/by/3.0/"),
    BY_4("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
    BY_SA_2("CC BY-SA 2.0", "https://creativecommons.org/licenses/by-sa/2.0/"),
    BY_SA_4("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/")
}

internal data class CollectionSource(
    val material: String,
    val observation: String,
    val file: String,
    val credit: String,
    val license: ImageLicense,
    val imageLabel: String = "展品图像",
    val research: String? = null
) {
    val imageUrl get() = "https://commons.wikimedia.org/wiki/File:" + Uri.encode(file.replace(' ', '_'))
}

private const val MUSEUM_SILK_BOOK =
    "https://www.hnmuseum.com/sites/default/files/statics/zhutishoucang/03%E9%A9%AC%E7%8E%8B%E5%A0%86%E6%B1%89%E5%A2%93%E5%B8%9B%E4%B9%A6%E9%9B%86%E7%B2%B9/BookOnSilk/001.pdf"

internal val museumCollection = listOf(
    GuideExhibit("silk-plain-straight", "直裾素纱单衣", "织物", "西汉", "马王堆一号汉墓",
        R.drawable.collection_gauze,
        "把视线沿着袖子往外移，你会看到深色的袖口。再看看衣身：丝纱很薄，连展示台的浅色都能透出来。",
        "一号墓出土的直裾式素纱单衣重约49克。衣身以素纱制作，袖口、领缘和衣料的视觉差异有助于识别结构。同墓另有曲裾式单衣，两件的重量和形制各有不同。",
        "silk-plain-straight", CollectionSource("丝织物", "找一找袖口和衣领交叠的位置。",
            "直裾素纱襌衣, 2018-09-28.jpg", "Siyuwj", ImageLicense.BY_SA_4,
            research = "https://whhlyt.hunan.gov.cn/whhlyt/english/News/Updates/202406/t20240614_33327960.html")),
    GuideExhibit("t-banner", "T形帛画", "书画", "西汉", "马王堆一号汉墓", R.drawable.collection_banner,
        "这幅画画在丝织物上。顺着画的顶端看，圆圆的太阳和弯弯的月亮各在一边。再往下，你会发现许多小人物。",
        "一号墓帛画上宽下窄，以丝织物为载体。可沿上、中、下三个区域观察画面，也可联系出土位置阅读。图像的具体含义存在不同解读。",
        collection = CollectionSource("彩绘丝帛", "对照上端两侧的日、月图案。", "Mawangdui silk banner from tomb no1.jpg",
            "佚名（西汉）", ImageLicense.PUBLIC_DOMAIN, research = "https://wwj.baoji.gov.cn/col1892/col17273/202509/t20250926_1214798.html")),
    GuideExhibit("mwd-eared-cups", "马王堆漆耳杯", "漆器", "西汉", "长沙马王堆汉墓", R.drawable.collection_lacquer,
        "这两个小杯子左右各伸出一小片，像两只耳朵。古人拿起它时，可以捏住杯耳。",
        "马王堆所见耳杯常采用椭圆杯口和对称的耳状执手。可比较照片中外壁与内底的髹漆色彩，再观察杯中的书写痕迹。",
        collection = CollectionSource("漆木", "比较杯里和外壁的红、黑两色。", "Mawangdui Han Lacquered Eared Cups (10112570926).jpg",
            "Gary Todd", ImageLicense.CC0, imageLabel = "展陈照片")),
    GuideExhibit("mwd-daoyin", "导引图", "书画", "西汉", "马王堆三号汉墓", R.drawable.collection_daoyin,
        "有人把手往上伸，有人弯下腰。像看连环画一样比较他们的姿势，就能读到古人画下的活动。",
        "三号墓出土的导引图是了解汉代身体活动观念的图像材料。今见图卡是重绘本，便于比较各个人物的姿势和排列。",
        collection = CollectionSource("彩绘丝帛（原件）", "你能找到两个手臂朝向不同的人物吗？",
            "Daoyin tu - chart for leading and guiding people in exercise Wellcome L0036007 (cropped).jpg",
            "Wellcome Collection · L0036007", ImageLicense.BY_4, imageLabel = "复原图", research = MUSEUM_SILK_BOOK)),
    GuideExhibit("mwd-laozi-a", "马王堆帛书《老子》甲本", "简帛", "西汉", "马王堆三号汉墓", R.drawable.collection_laozi,
        "古人也能把字写在布一样的丝帛上。这是一小段残片。放大看看，字是一行一行怎样排的？",
        "马王堆三号墓出土《老子》甲、乙两种抄本。丝帛上的文字及篇章顺序，可与传世版本对读。这里展示甲本局部，并非全卷。",
        collection = CollectionSource("墨书丝帛", "沿一个方向读图，观察相邻两列文字。", "Mawangdui Tao Teh King fragment.png",
            "古代佚名书写", ImageLicense.PUBLIC_DOMAIN, imageLabel = "帛书局部", research = MUSEUM_SILK_BOOK)),
    GuideExhibit("mwd-lacquer-tripod", "云纹漆鼎", "漆器", "西汉", "马王堆一号汉墓", R.drawable.collection_tripod,
        "找找这件器物的三条腿。盖子和肚子上也有很多卷过来的线条，像一团团云。",
        "这件漆鼎结合带盖器身、双耳、三足和云纹装饰。与青铜方鼎并看，能比较同一器类在不同材料、形制及时代中的变化。",
        collection = CollectionSource("漆木", "从盖顶往下找耳、腹和足。", "Han Dynasty Lacquer Tripod with Cloud Design.jpg",
            "David Schroeter", ImageLicense.BY_SA_2)),
    GuideExhibit("min-fanglei", "皿方罍", "青铜器", "商代晚期", "湖南桃源", R.drawable.collection_lei,
        "这个大容器的盖子像屋顶。它曾经与器身分开很久，后来又在湖南团聚。",
        "皿方罍为方形青铜盛酒礼器。可沿器盖、双耳、扉棱及器身纹饰观察铸造细节。器身于2014年回归并与馆藏器盖合璧。",
        collection = CollectionSource("青铜", "找一找器身边角向外突出的长条。", "Min fanglei, Shang dynasty, Hunan Museum, 20180706.jpg",
            "Huangdan2060", ImageLicense.CC0, research = "https://wwj.hunan.gov.cn/c100310/c100313/201406/t20140626_10495274.html")),
    GuideExhibit("human-face-ding", "大禾人面纹方鼎", "青铜器", "商代晚期", "湖南宁乡", R.drawable.collection_ding,
        "你能从器身上认出眼睛、鼻子和嘴吗？它是方形的鼎，数一数下面能看到几条足。",
        "大禾人面纹方鼎以青铜铸成，器腹的主要装饰是人面纹。理解其形制时，可区分口沿与立耳、方腹及承重的四足。",
        collection = CollectionSource("青铜", "用一条想象中的竖线分开人面，看两边是否对称。",
            "Bronze square ding (cauldron) with human faces.jpg", "smartneddy（Flickr）", ImageLicense.BY_SA_4,
            research = "https://whhlyt.hunan.gov.cn/ggwh/201611/t20161129_5428691.html")),
    GuideExhibit("xiangyin-pottery-figurine", "唐代胡人陶俑", "陶瓷", "唐代", "湖南湘阴桐子山", R.drawable.collection_figurine,
        "从脸、发式和服装看，这个小陶人身上有很多可以找的细节。你先注意到哪里？",
        "这件陶俑1971年出土于湘阴桐子山。可以结合服饰、头部塑形和出土情境阅读唐代人物俑的表现方式。",
        collection = CollectionSource("陶", "对照头部与衣服的造型，找两处细节。",
            "Pottery figurine of Hu people, Tang dynasty, 1971, Hunan Museum.jpg", "Huangdan2060", ImageLicense.CC0)),
    GuideExhibit("mwd-painted-pottery", "彩绘陶罐", "陶瓷", "西汉", "马王堆一号汉墓", R.drawable.collection_box,
        "陶罐上有颜色和纹样。拿它和红黑色的漆耳杯对着看，哪一件的轮廓更圆？",
        "辛追墓出土的彩绘陶器可与同墓漆器比较。观察时应分别辨认陶胎、器物轮廓和器表彩绘，避免因色彩相似而混淆材质。",
        collection = CollectionSource("彩绘陶", "顺着器身弧线，观察彩绘纹样的分布。",
            "Painted pottery box, Mawangdui, Changsha, Hunan, Hunan Museum.jpg", "Huangdan2060", ImageLicense.BY_3)),
    GuideExhibit("changsha-kiln-teaware", "长沙窑茶具", "陶瓷", "宋代", "湖南长沙窑", R.drawable.collection_kiln,
        "这组东西有壶、有盏，也有渣斗。想一想，哪一件最方便把水倒出来？",
        "同一组展品中可对照壶、盏与渣斗的口沿、器腹和使用方式。它们属于湖南博物院展陈的宋代长沙窑出土茶器。",
        collection = CollectionSource("瓷", "观察茶壶和茶盏的口部形状有什么不同。",
            "Teapots, tea bowls and cinder hopper, Changsha Kiln in Changsha, Song dynasty, Hunan Museum.jpg",
            "Huangdan2060", ImageLicense.CC0, imageLabel = "组器展陈照片"))
)
