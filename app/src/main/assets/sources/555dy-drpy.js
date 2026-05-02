var rule = {
  title: '555电影',
  host: 'https://www.555k7.com',
  url: '/vodshow/fyclass--------fypage---.html',
  searchUrl: '/vodsearch/**-------------.html',
  searchable: 2,
  quickSearch: 0,
  filterable: 0,
  headers: {
    'User-Agent': 'Mozilla/5.0',
    'Referer': 'https://www.555k7.com/'
  },
  timeout: 60000,
  class_name: '\u7535\u5f71&\u8fde\u7eed\u5267&\u7efc\u827a\u7eaa\u5f55&\u52a8\u6f2b&\u798f\u5229&\u64e6\u8fb9\u77ed\u5267',
  class_url: '1&2&3&4&124&126',

  play_parse: true,
  sniffer: 1,
  play_headers: {
    'Referer': 'https://www.555k7.com/',
    'User-Agent': 'Mozilla/5.0'
  },

  lazy: `js:
let html=request(input,{headers:rule.headers||{}})||'';
let m=html.match(/<iframe[^>]+src=["']([^"']*player\.html\?v=[^"']+)["']/i);
let h=rule.play_headers||rule.headers||{};
if(m){
  let u=m[1];
  if(!/^https?:\/\//.test(u)){
    if(u.startsWith('/'))u=rule.host+u;
    else u=rule.host+'/'+u;
  }
  h.Referer=input;
  input={parse:1,jx:0,url:u,header:h};
}else{
  input={parse:1,jx:0,url:input,header:h};
}
`,

  "\u63a8\u8350": 'a.module-poster-item.module-item;.module-poster-item-title&&Text;img&&data-original;.module-item-note&&Text;a&&href',
  "\u4e00\u7ea7": 'a.module-poster-item.module-item;.module-poster-item-title&&Text;img&&data-original;.module-item-note&&Text;a&&href',
  "\u641c\u7d22": 'a.module-poster-item.module-item;.module-poster-item-title&&Text;img&&data-original;.module-item-note&&Text;a&&href',

  "\u4e8c\u7ea7": {
    title: 'h1&&Text',
    img: '.module-item-pic img&&data-original',
    desc: '.module-info-tag&&Text',
    content: '.module-info-introduction-content&&Text',
    tabs: '.module-tab-item',
    lists: '.module-play-list:eq(#id)&&a',
    tab_text: 'span&&Text',
    list_text: 'span&&Text',
    list_url: 'a&&href'
  }
};
