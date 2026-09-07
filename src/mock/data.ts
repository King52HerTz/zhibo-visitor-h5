import type { Exhibit, Source } from '../types'
import silkImage from '../assets/exhibits/silk.svg'
import lacquerImage from '../assets/exhibits/lacquer.svg'
import silkBookImage from '../assets/exhibits/silk-book.svg'

// 阶段 1 仅使用演示数据；接入游客网关前不得把这里当作正式馆藏数据源。
export const demoExhibits: Exhibit[] = [
  {
    id: 'demo-su-sha',
    name: '素纱单衣',
    era: '西汉',
    material: '丝织物',
    category: '服饰工艺',
    summary: '一件轻薄通透的汉代衣物，从纤维、形制和保存状态都能看见当时的织造技术。',
    image: silkImage,
    tags: ['马王堆精选', '服饰工艺', '汉代生活'],
    sourceTitle: '马王堆汉墓出土文物图录（演示资料）',
    observations: [
      { title: '先看衣身的薄透程度', detail: '轻薄感不只是视觉效果，也提示了丝纤维处理和织造工艺的复杂性。' },
      { title: '再看交领与直裾', detail: '领口和衣襟的组合可以帮助我们理解汉代服饰的形制结构。' },
      { title: '最后看织物边缘', detail: '边缘的收束和纹理，是观察织造与保存状态的一个入口。' },
    ],
  },
  {
    id: 'demo-lacquer-coffin',
    name: '黑地彩绘棺',
    era: '西汉',
    material: '漆木',
    category: '汉代生活',
    summary: '黑地与彩绘共同构成庄重而繁复的视觉秩序，可以从纹样读出当时的观念世界。',
    image: lacquerImage,
    tags: ['马王堆精选', '汉代生活'],
    sourceTitle: '马王堆汉墓漆器研究资料（演示资料）',
    observations: [
      { title: '看黑地与彩绘的层次', detail: '底色与纹样的对比让器物获得更强的仪式感。' },
      { title: '寻找连续纹样', detail: '连续纹样常常帮助我们理解器物表面的秩序和观看节奏。' },
      { title: '注意漆面的光泽', detail: '光泽既来自工艺，也和保存环境、年代变化有关。' },
    ],
  },
  {
    id: 'demo-silk-book',
    name: '帛书残片',
    era: '西汉',
    material: '帛',
    category: '文献与思想',
    summary: '柔软的帛面承载着文字和思想，也让我们看到古人如何保存、阅读和传递知识。',
    image: silkBookImage,
    tags: ['汉代生活', '文献与思想'],
    sourceTitle: '出土帛书整理说明（演示资料）',
    observations: [
      { title: '观察文字的排列', detail: '文字的方向和行距，是进入古代书写习惯的第一步。' },
      { title: '看帛面留下的折痕', detail: '折痕和缺损记录了材料被使用、收纳与埋藏的经历。' },
      { title: '想象它的阅读场景', detail: '在证据允许的范围内，我们可以讨论它可能服务的知识活动。' },
    ],
  },
]

export const demoSources: Source[] = [
  {
    title: '马王堆汉墓出土文物图录（演示资料）',
    page: '第 18 页',
    excerpt: '资料将衣物的材质、形制与出土信息分别记录，适合用于核对展品的基础事实。',
    status: 'verified',
  },
  {
    title: '汉代丝织技术观察笔记（演示资料）',
    page: '第 6 页',
    excerpt: '从织物结构和保存状态出发，说明观察薄透感、边缘和纹理的意义。',
    status: 'interpretation',
  },
]

export const demoThemes = [
  { name: '马王堆精选', description: '从几件代表性文物开始认识马王堆', tone: 'ochre' },
  { name: '汉代生活', description: '衣食住行里藏着怎样的历史', tone: 'sage' },
  { name: '服饰工艺', description: '看见丝线、纹样与形制的细节', tone: 'rose' },
]

export const demoQuestions = [
  '为什么这件衣服可以做得这么薄？',
  '它和普通汉代服饰有什么不同？',
  '先带我看一个最值得注意的细节。',
]
