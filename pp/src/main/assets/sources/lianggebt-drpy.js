var rule = {
  title: '两个BT',
  host: 'https://www.bttwo.me',
  class_name: '影片BT&最新电影&本月热门&国产剧&美剧&日韩剧',
  class_url: 'movie_bt&new-movie&hot-month&zgjun&meiju&jpsrtv',
  url: '/fyclass/page/fypage',
  searchUrl: '/xsssearch?q=**',
  searchable: 2,
  quickSearch: 1,
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
  一级: '.bt_img li;h3.dytit a&&Text;img&&data-original;.inzhuy&&Text;h3.dytit a&&href',
  推荐: '.bt_img li;h3.dytit a&&Text;img&&data-original;.inzhuy&&Text;h3.dytit a&&href',
  搜索: '.bt_img li;h3.dytit a&&Text;img&&data-original;.inzhuy&&Text;h3.dytit a&&href',
  二级: {
    title: 'h1&&Text',
    img: '.dyimg img&&src',
    desc: '.moviedteail_list&&Text',
    content: '.yp_context&&Text',
    tabs: '',
    lists: '.paly_list_btn a',
    list_text: 'a&&Text',
    list_url: 'a&&href'
  },
  play_parse: true,
  sniffer: 1,
  lazy: `js:
    input = {
      parse: 1,
      jx: 0,
      url: input,
      header: rule.play_headers || rule.headers
    };
  `,
  过滤: 'a[href*="/hot"],a[href*="zimuku.org"]'
};
