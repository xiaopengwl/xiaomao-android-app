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
  class_name: '电影&连续剧&综艺纪录&动漫&福利&擦边短剧',
  class_url: '1&2&3&4&124&126',

  play_parse: true,
  sniffer: 1,
  play_headers: {
    'Referer': 'https://www.555k7.com/',
    'User-Agent': 'Mozilla/5.0'
  },

  lazy: 'js:let html=request(input);let m=html.match(/<iframe[^>]+src=["\\\']([^"\\\']*player\\\\.html\\\\?v=[^"\\\']+)["\\\']/i);if(m){let u=m[1];if(!/^https?:\\\\/\\\\//.test(u)){if(u.startsWith(\"/\"))u=rule.host+u;else u=rule.host+\"/\"+u;}input={parse:0,jx:0,url:u,header:rule.play_headers||rule.headers};}',

  "推荐": 'a.module-poster-item.module-item;.module-poster-item-title&&Text;img&&data-original;.module-item-note&&Text;a&&href',
  "一级": 'a.module-poster-item.module-item;.module-poster-item-title&&Text;img&&data-original;.module-item-note&&Text;a&&href',
  "搜索": 'a.module-poster-item.module-item;.module-poster-item-title&&Text;img&&data-original;.module-item-note&&Text;a&&href',

  "二级": {
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
