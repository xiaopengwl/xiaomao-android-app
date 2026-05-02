var rule = {
  title: '两个BT',
  host: 'https://www.bttwo.me',
  class_name: '电影&电视剧&动漫&片单',
  class_url: 'movie&tv&anime&playlists',
  url: '/fyclass',
  searchUrl: '/search?q=**',
  searchable: 2,
  quickSearch: 0,
  filterable: 0,
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36',
    Referer: 'https://www.bttwo.me/'
  },
  play_headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36',
    Referer: 'https://www.bttwo.me/'
  },
  timeout: 10000,
  "\u4e00\u7ea7": 'article.movie-card;h3&&Text;img&&src;img&&alt;a&&href',
  "\u63a8\u8350": 'article.movie-card;h3&&Text;img&&src;img&&alt;a&&href',
  "\u641c\u7d22": 'a.block[href^="/play/"];h3&&Text;img&&src;p.text-xs&&Text;a&&href',
  "\u4e8c\u7ea7": {
    title: 'h1.text-xl&&Text',
    img: 'meta[property="og:image"]&&content',
    desc: 'meta[name="keywords"]&&content',
    content: 'meta[name="description"]&&content',
    tabs: '',
    lists: 'a.episode-link',
    list_text: 'span&&Text',
    list_url: 'a&&href',
    list_group_name: '在线播放'
  },
  play_parse: false,
  sniffer: 1,
  lazy: '',
  杩囨护: 'a[href*="/hot"],a[href*="zimuku.org"]'
};
