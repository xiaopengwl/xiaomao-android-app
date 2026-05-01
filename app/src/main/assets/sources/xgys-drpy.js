var rule = {
  title: '西瓜影院',
  host: 'https://sszzyy.com',
  url: '/index.php/vod/type/id/fyclass.html?page=fypage',
  detailUrl: '/index.php/vod/detail/id/fvid.html',
  playUrl: '/index.php/vod/play/id/fvid/sid/fysid/nid/fyid.html',
  searchUrl: '/index.php/vod/search.html?wd=**',
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  },
  // 分类（真实 ID）
  class_name: '电影&连续剧&动漫&综艺&人人专区',
  class_url: '20&37&43&45&60',
  // 首页/一级/搜索 列表
  推荐: 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',
  一级: 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',
  搜索: 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',
  // 详情页（二级：去掉 href 属性含中文的不可用选择器）
  二级: {
    title: 'h1.title&&Text',
    img: '.stui-content__thumb a.pic img&&data-original',
    desc: '.desc--span&&Text',
    content: '.detail-sketch&&Text',
    tabs: 'ul.nav-tabs li a',
    lists: 'ul.stui-content__playlist:eq(#id).li a',
    tab_text: 'a&&Text',
    list_text: 'a&&Text',
    list_url: 'a&&href'
  },
  过滤: 'a[href*="mx-pc-link"],a[href*="mx-mb-link"],a[href*="recommend"]',
  play_parse: true,
  sniffer: 1,
  lazy: $js.toString(() => {
    let m = document.html.match(/var\s+player_aaaa\s*=\s*(\{.*?\})\s*<;/s);
    if (m) {
      let obj = JSON.parse(m[1]);
      if (obj.url) {
        input = {jx: 0, parse: 0, url: obj.url.replace(/\\\//g, '/')};
        return;
      }
    }
    input = '';
  }),
};
