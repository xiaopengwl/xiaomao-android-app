var rule = {
  title: '麻豆视频',
  host: 'https://www.madou8.top/enter',
  url: '/type/fyclass-fypage.html',
  searchUrl: '/search/-.html?wd=**',
  searchable: 2,
  quickSearch: 0,
  filterable: 0,
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.madou8.top/enter',
  },
  timeout: 15000,
  class_name: '麻豆视频&91制片厂&天美传媒&蜜桃传媒&皇家华人&星空传媒&精东影业&乐播传媒&乌鸦传媒&兔子先生&杏吧原创&玩偶姐姐&mini传媒&大象传媒&开心鬼传媒&PsychoPorn&糖心Vlog&萝莉社&性视界&日本无码&国产视频&欧美高清&成人动漫',
  class_url: '1&2&3&4&5&6&7&8&9&10&11&12&13&14&15&16&17&18&20&21&22&23&24',
  play_parse: false,
  sniffer: 0,
  lazy: 'js:let s = input.replace(/\\\\/g,"");let r = "";for(let i=0;i<s.length;i++){let c = s.charCodeAt(i); r += String.fromCharCode(c > 127 ? (c ^ 83) : c);} try { r = atob(r); } catch(e) {} input = { parse: 0, url: r };',
  play_headers: {
    'Referer': 'https://www.madou8.top/enter',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  },
  推荐: '.stui-vodlist__box;a&&title;a&&data-original;;a&&href',
  二级: {
    title: 'h1.title a&&Text',
    img: '.stui-player__video img&&src',
    desc: '',
    content: '',
    tabs: '',
    lists: '.stui-player__play&&.title'
  },
  搜索: '.stui-vodlist__box;a&&title;a&&data-original;;a&&href',
};
