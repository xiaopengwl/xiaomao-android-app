var rule = {
  title: '吃瓜网',
  host: 'https://hxnxz1.isppven.com',
  url: '/category/fyclass/fypage/',
  searchUrl: '/search/**/fypage/',
  searchable: 2,
  quickSearch: 0,
  filterable: 0,
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://hxnxz1.isppven.com/',
    'Cookie': 'user-choose=true; newuser=1'
  },
  play_headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://hxnxz1.isppven.com/',
    'Cookie': 'user-choose=true; newuser=1'
  },
  timeout: 8000,
  limit: 40,
  pagecount: 999,
  play_parse: true,
  sniffer: 1,
  double: true,
  class_name: '今日吃瓜&学生校园&网红黑料&热门大瓜&吃瓜榜单&必看大瓜&看片娱乐&每日大赛&伦理道德&国产剧情&网黄合集&探花精选&免费短剧&骚男骚女&明星黑料&海外吃瓜&人人吃瓜&领导干部&吃瓜看戏&擦边聊骚&51涨知识&51品茶&原创博主&51剧场',
  class_url: 'wpcz&xsxy&whhl&rdsj&mrdg&bkdg&ysyl&mrds&lldd&gcjq&whhj&thjx&cbdj&snsn&whmx&hwcg&rrcg&ldcg&qubk&dcbq&zzs&51by&yczq&51djc',
  预处理: `js:
var UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
var GATE_COOKIE = 'user-choose=true; newuser=1';
if (!window.__xmCg) {
  window.__xmCg = {};
}
var cg = window.__xmCg;
cg.hosts = [
  'https://hxnxz1.isppven.com',
  'https://51cgm25.com',
  'https://cg51.com',
  'https://tyu35.cc',
  'https://tyu7.cc',
  'https://51cg1.com',
  'https://chigua.com',
  'https://snfkr.isppven.com'
];
cg.currentHost = cg.currentHost || rule.host || cg.hosts[0];
cg.syncHost = function(host) {
  host = host || cg.currentHost || cg.hosts[0];
  cg.currentHost = host;
  rule.host = host;
  rule.headers = rule.headers || {};
  rule.play_headers = rule.play_headers || {};
  rule.headers['User-Agent'] = UA;
  rule.play_headers['User-Agent'] = UA;
  rule.headers['Referer'] = host + '/';
  rule.play_headers['Referer'] = host + '/';
  rule.headers['Cookie'] = GATE_COOKIE;
  rule.play_headers['Cookie'] = GATE_COOKIE;
};
cg.syncHost(cg.currentHost);
cg.decode = function(text) {
  text = String(text || '');
  text = text
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#34;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>');
  while (text.indexOf('\\\\/') >= 0) {
    text = text.replace(/\\\\\//g, '/');
  }
  while (text.indexOf('\\/') >= 0) {
    text = text.replace(/\\\//g, '/');
  }
  return text;
};
cg.text = function(html) {
  html = String(html || '');
  var out = '';
  var inTag = false;
  for (var i = 0; i < html.length; i += 1) {
    var ch = html.charAt(i);
    if (ch === '<') {
      inTag = true;
      continue;
    }
    if (ch === '>') {
      inTag = false;
      out += ' ';
      continue;
    }
    if (!inTag) {
      out += ch;
    }
  }
  out = cg.decode(out).replace(/\\s+/g, ' ').trim();
  return out;
};
cg.abs = function(url, host) {
  url = cg.decode(url).trim();
  if (!url) {
    return '';
  }
  if (url.indexOf('http://') === 0 || url.indexOf('https://') === 0) {
    return url;
  }
  if (url.indexOf('//') === 0) {
    return 'https:' + url;
  }
  host = host || cg.currentHost || rule.host || '';
  if (!host) {
    return url;
  }
  if (url.charAt(0) === '/') {
    return host + url;
  }
  return host + '/' + url;
};
cg.normalizePath = function(target) {
  target = String(target || '').trim();
  if (!target) {
    return '/';
  }
  var match = target.match(/^https?:\\/\\/[^/]+(\\/.*)$/i);
  if (match && match[1]) {
    return match[1];
  }
  if (target.charAt(0) !== '/') {
    return '/' + target;
  }
  return target;
};
cg.looksBlocked = function(html) {
  html = String(html || '');
  if (!html) {
    return true;
  }
  return /user-choose|wanrningconfirm|warningconfirm|newuser|adult|18\+|only adults/i.test(html)
    && html.indexOf('/archives/') < 0
    && html.indexOf('class="dplayer"') < 0
    && html.indexOf('post-card-title') < 0;
};
cg.fetch = function(target) {
  var path = cg.normalizePath(target);
  var html = '';
  for (var i = 0; i < cg.hosts.length; i += 1) {
    var host = cg.hosts[i];
    try {
      cg.syncHost(host);
      html = request(host + path, {headers: rule.headers}) || '';
      if (html && !cg.looksBlocked(html) && (html.indexOf('/archives/') >= 0 || html.indexOf('class="dplayer"') >= 0 || html.indexOf('post-card-title') >= 0)) {
        return html;
      }
    } catch (e) {
    }
  }
  return html || '';
};
cg.pick = function(text, re) {
  var match = String(text || '').match(re);
  return match && match[1] ? match[1] : '';
};
cg.parseList = function(html) {
  var out = [];
  var seen = {};
  var pos = 0;
  while (true) {
    var start = html.indexOf('<article', pos);
    if (start < 0) {
      break;
    }
    var end = html.indexOf('</article>', start);
    if (end < 0) {
      break;
    }
    var seg = html.substring(start, end + 10);
    pos = end + 10;
    if (seg.indexOf('/archives/') < 0 || seg.indexOf('post-card-title') < 0) {
      continue;
    }
    var href = cg.pick(seg, /<a[^>]+href="([^"]*\\/archives\\/[^"]+)"/i);
    if (!href) {
      continue;
    }
    var url = cg.abs(href);
    if (!url || seen[url]) {
      continue;
    }
    var title = cg.text(cg.pick(seg, /<h2[^>]*class="post-card-title"[^>]*>([\\s\\S]*?)<\\/h2>/i)).replace(/热搜 HOT/g, '').trim();
    if (!title) {
      continue;
    }
    var img = cg.abs(cg.pick(seg, /loadBannerDirect\\(\\s*['"]([^'"]+)['"]/i));
    var desc = cg.text(cg.pick(seg, /<div[^>]*class="post-card-info"[^>]*>([\\s\\S]*?)<\\/div>/i));
    seen[url] = 1;
    out.push({
      title: title,
      img: img,
      desc: desc,
      url: url,
      vod_id: url,
      vod_name: title,
      vod_pic: img,
      vod_remarks: desc
    });
  }
  return out;
};
cg.parseConfig = function(raw) {
  raw = cg.decode(raw);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch (e) {
    return null;
  }
};
cg.extractVideoUrl = function(raw) {
  raw = cg.decode(raw);
  if (!raw) {
    return '';
  }
  var obj = cg.parseConfig(raw);
  if (obj && obj.video && obj.video.url) {
    return cg.decode(obj.video.url);
  }
  var match = raw.match(/"url"\\s*:\\s*"([^"]+)"/);
  if (match && match[1]) {
    return cg.decode(match[1]);
  }
  match = raw.match(/https?:[^"'\\s]+\\.(?:m3u8|mp4)[^"'\\s]*/i);
  return match && match[0] ? cg.decode(match[0]) : '';
};
`,
  lazy: `js:
var cg = window.__xmCg || {};
var rawInput = String(input || '');
var detailUrl = rawInput;
var playIndex = 0;
if (rawInput.indexOf('@@') > 0) {
  var parts = rawInput.split('@@');
  detailUrl = parts[0] || '';
  playIndex = parseInt(parts[1] || '0', 10);
  if (!(playIndex >= 0)) {
    playIndex = 0;
  }
}
var html = cg.fetch ? cg.fetch(detailUrl) : request(detailUrl, {headers: rule.headers});
var blocks = String(html || '').split('class="dplayer"');
var mediaUrl = '';
if (blocks.length > playIndex + 1) {
  var block = blocks[playIndex + 1];
  var cfg = cg.pick(block, /data-config=(?:"([^"]+)"|'([^']+)')/i) || '';
  if (!cfg) {
    var cfgMatch = block.match(/data-config=(?:"([^"]+)"|'([^']+)')/i);
    if (cfgMatch) {
      cfg = cfgMatch[1] || cfgMatch[2] || '';
    }
  }
  mediaUrl = cg.extractVideoUrl ? cg.extractVideoUrl(cfg) : '';
}
if (!mediaUrl) {
  mediaUrl = cg.extractVideoUrl ? cg.extractVideoUrl(html) : '';
}
if (mediaUrl) {
  input = {
    parse: 0,
    jx: 0,
    url: mediaUrl,
    header: {
      'User-Agent': rule.play_headers['User-Agent'] || rule.headers['User-Agent'] || '',
      'Referer': (cg.currentHost || rule.host || '') + '/'
    }
  };
} else {
  input = {
    parse: 1,
    jx: 0,
    url: detailUrl,
    header: rule.play_headers || rule.headers || {}
  };
}
`,
  推荐: `js:
var cg = window.__xmCg;
var html = cg.fetch('/');
VODS = cg.parseList(html);
`,
  一级: `js:
var cg = window.__xmCg;
var html = cg.fetch(input);
VODS = cg.parseList(html);
`,
  二级: `js:
var cg = window.__xmCg;
var html = cg.fetch(input);
var title = cg.text(cg.pick(html, /<h1[^>]*>([\\s\\S]*?)<\\/h1>/i));
var content = cg.decode(cg.pick(html, /<meta[^>]+name="description"[^>]+content="([^"]*)"/i) || cg.pick(html, /<meta[^>]+property="og:description"[^>]+content="([^"]*)"/i));
var img = cg.pick(html, /data-xkrkllgl=(?:"([^"]+)"|'([^']+)')/i);
if (!img) {
  var imgMatch = html.match(/data-xkrkllgl=(?:"([^"]+)"|'([^']+)')/i);
  if (imgMatch) {
    img = imgMatch[1] || imgMatch[2] || '';
  }
}
img = cg.abs(img);
var plays = [];
var blocks = String(html || '').split('class="dplayer"');
for (var i = 1; i < blocks.length; i += 1) {
  var block = blocks[i];
  var nameMatch = block.match(/data-video_title=(?:"([^"]+)"|'([^']+)')/i);
  var name = '';
  if (nameMatch) {
    name = cg.text(nameMatch[1] || nameMatch[2] || '');
  }
  if (!name) {
    name = '播放' + i;
  }
  plays.push(name + '$' + input + '@@' + (i - 1));
}
if (plays.length < 1) {
  plays.push('嗅探播放$' + input);
}
VOD = {
  vod_id: input,
  vod_name: title || '详情',
  vod_pic: img,
  vod_remarks: content,
  vod_content: content,
  vod_play_from: '站内播放',
  vod_play_url: plays.join('#')
};
`,
  搜索: `js:
var cg = window.__xmCg;
var html = cg.fetch(input);
VODS = cg.parseList(html);
`
};
