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
  class_name: '国产视频&日产视频&欧美视频&动漫视频&制服人类&自拍自售&群交颜射&写真诱惑',
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
  推荐: '.stui-vodlist__box;a&&title;a&&data-original;.pic-text&&Text;a&&href',
  一级: '.stui-vodlist__box;a&&title;a&&data-original;.pic-text&&Text;a&&href',
  二级: {
    title: 'h1.title&&Text',
    img: '.stui-content__thumb img&&data-original',
    desc: '.stui-content__detail&&Text',
    content: '#desc .col-pd&&Text',
    tabs: '.playlist h3.title',
    lists: '.stui-content__playlist a',
    list_text: 'a&&Text',
    list_url: 'a&&href'
  },
  搜索: '.stui-vodlist__box;a&&title;a&&data-original;.pic-text&&Text;a&&href',
};