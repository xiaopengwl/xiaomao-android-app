var rule = {
  title: '西瓜影院',
  host: 'https://www.sszzyy.com',
  url: '/index.php/vod/type/id/fyclass.html?page=fypage',
  searchUrl: '/index.php/vod/search.html?wd=**',
  searchable: 2,
  quickSearch: 0,
  filterable: 0,
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36',
    'Referer': 'https://www.sszzyy.com/'
  },
  class_name: '\u7535\u5f71&\u8fde\u7eed\u5267&\u52a8\u6f2b&\u7efc\u827a&B\u7ad9&\u4eba\u4eba\u4e13\u533a',
  class_url: '20&37&43&45&47&60',
  play_parse: true,
  sniffer: 1,
  "\u63a8\u8350": 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',
  "\u4e00\u7ea7": 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',
  "\u4e8c\u7ea7": {
    title: 'h1.title&&Text',
    img: '.stui-content__thumb img&&data-original',
    desc: '.stui-content__detail p.data:eq(0)&&Text;.stui-content__detail p.data:eq(1)&&Text;.stui-content__detail p.data:eq(2)&&Text;.stui-content__detail p.data:eq(3)&&Text',
    content: '.detail-sketch&&Text',
    tabs: 'ul.nav-tabs li a',
    tab_text: 'Text',
    lists: 'ul.stui-content__playlist:eq(#id)&&a',
    list_text: 'Text',
    list_url: 'a&&href'
  },
  "\u641c\u7d22": 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',
  lazy: $js.toString(() => {
    let html = document.html || request(input, { headers: rule.headers }) || '';
    let matched = html.match(/var\s+player_aaaa\s*=\s*(\{[\s\S]*?\})\s*;/);
    if (matched && matched[1]) {
      let obj = JSON.parse(matched[1]);
      let url = obj.url || '';
      if (obj.encrypt === 1) {
        url = unescape(url);
      } else if (obj.encrypt === 2) {
        url = unescape(base64Decode(url));
      }
      input = {
        jx: 0,
        parse: 0,
        url: String(url || '').replace(/\\\//g, '/'),
        header: rule.headers,
      };
      return;
    }
    input = {
      jx: 0,
      parse: 1,
      url: input,
      header: rule.headers,
    };
  }),
};
