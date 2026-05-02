var rule = {
  title: 'dongkadi',
  host: 'https://www.dongkadi.com',
  url: '/sortlist/fyclass/time-fypage.html',
  searchUrl: '/sousuo.html?keyword=**',
  searchable: 2,
  quickSearch: 0,
  filterable: 0,
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  },
  timeout: 15000,
  class_name: '鍥戒骇瑙嗛&鏃ヤ骇瑙嗛&娆х編瑙嗛&鍔ㄦ极瑙嗛&鍒舵湇浜虹被&鑷媿鑷敭&缇や氦棰滃皠&鍐欑湡璇辨儜',
  class_url: '9858&9859&9860&9861&9862&9863&9864&9865',
  play_parse: true,
  lazy: `js:
let url = input;
let html = request(url);
let m = html.match(/thisUrl\\s*=\\s*["']([^"']+)["']/);
if (m) {
  url = m[1];
} else {
  let iframe = html.match(/src="([^"?]+\\/dplayer\\/index\\.php[^"]+)"/);
  if (iframe) url = iframe[1];
}
input = { parse: 0, url: url };
`,
  "\u63a8\u8350": '.stui-vodlist__box;a&&title;a&&data-original;.pic-text&&Text;a&&href',
  "\u4e00\u7ea7": '.stui-vodlist__box;a&&title;a&&data-original;.pic-text&&Text;a&&href',
  "\u4e8c\u7ea7": {
    title: 'h1.title&&Text',
    img: '.stui-content__thumb img&&data-original',
    desc: '.stui-content__detail&&Text',
    content: '#desc .col-pd&&Text',
    tabs: '.playlist h3.title',
    lists: '.stui-content__playlist a',
    list_text: 'a&&Text',
    list_url: 'a&&href'
  },
  "\u641c\u7d22": '.stui-vodlist__box;a&&title;a&&data-original;.pic-text&&Text;a&&href',
};
