var rule = {
  title: '吃瓜网t3-js-drpy-v32-playurl-fix',
  host: 'https://band.nnfndyhn.cc',
  url: '/category/fyclass/fypage/',
  searchUrl: '/search/**/fypage/',
  searchable: 2,
  quickSearch: 0,
  filterable: 0,
  filter: '',
  filter_url: '',
  filter_def: {},
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36',
    'Referer': 'https://band.nnfndyhn.cc/'
  },
  timeout: 15000,
  limit: 40,
  pagecount: 999,
  class_parse: '',
  cate_exclude: '',
  play_parse: true,
  sniffer: 1,
  double: true,
  class_name: '今日吃瓜&学生校园&网红黑料&热门大瓜&吃瓜榜单&必看大瓜&看片娱乐&每日大赛&伦理道德&国产剧情&网黄合集&探花精选&免费短剧&骚男骚女&明星黑料&海外吃瓜&人人吃瓜&领导干部&吃瓜看戏&擦边聊骚&51涨知识&51品茶&原创博主&51剧场',
  class_url: 'wpcz&xsxy&whhl&rdsj&mrdg&bkdg&ysyl&mrds&lldd&gcjq&whhj&thjx&cbdj&snsn&whmx&hwcg&rrcg&ldcg&qubk&dcbq&zzs&51by&yczq&51djc',
  lazy: `js:
var I=input||'';
var U='';
var BS=String.fromCharCode(92);
var T=function(s){s=s||'';s=s.split('&quot;').join('"').split('&#34;').join('"').split('&#39;').join("'").split('&amp;').join('&');while(s.indexOf(BS+'/')>=0)s=s.split(BS+'/').join('/');while(s.indexOf(BS+BS+'/')>=0)s=s.split(BS+BS+'/').join('/');return s;};
var C=function(s,a,b){var x=s.indexOf(a);if(x<0)return '';x+=a.length;var y=s.indexOf(b,x);return y>x?s.substring(x,y):'';};
var J=T(I);
if(J.indexOf('@@')>0){
  var sp=J.split('@@');
  var detail=sp[0]||'';
  var idx=parseInt(sp[1]||'0',10);
  if(!(idx>=0))idx=0;
  var H=request(detail,{headers:rule.headers})||'';
  var arr=H.split('class="dplayer"');
  if(arr.length>idx+1){
    var blk=arr[idx+1];
    var cfg=C(blk,"data-config='","'");
    if(!cfg)cfg=C(blk,'data-config=\"','\"');
    cfg=T(cfg);
    try{var O2=JSON.parse(cfg);if(O2&&O2.video&&O2.video.url)U=T(O2.video.url);}catch(e){}
    if(!U){U=C(cfg,'"url":"','"');}
    if(!U){U=C(cfg,"'url':'","'");}
    if(!U){var m30=cfg.indexOf('.m3u8');if(m30>0){var st0=cfg.lastIndexOf('http',m30);if(st0>=0){var en0=cfg.indexOf('"',m30);var en20=cfg.indexOf("'",m30);var en30=cfg.indexOf(',',m30);var end0=cfg.length;if(en0>m30&&en0<end0)end0=en0;if(en20>m30&&en20<end0)end0=en20;if(en30>m30&&en30<end0)end0=en30;U=cfg.substring(st0,end0);}}}
    if(!U){var mp0=cfg.indexOf('.mp4');if(mp0>0){var st20=cfg.lastIndexOf('http',mp0);if(st20>=0){var e10=cfg.indexOf('"',mp0);var e20=cfg.indexOf("'",mp0);var e30=cfg.indexOf(',',mp0);var ed0=cfg.length;if(e10>mp0&&e10<ed0)ed0=e10;if(e20>mp0&&e20<ed0)ed0=e20;if(e30>mp0&&e30<ed0)ed0=e30;U=cfg.substring(st20,ed0);}}}
  }
}
if(!U){try{var O=JSON.parse(J);if(O&&O.video&&O.video.url)U=T(O.video.url);}catch(e){}}
if(!U){U=C(J,'"url":"','"');}
if(!U){U=C(J,"'url':'","'");}
if(!U){var m3=J.indexOf('.m3u8');if(m3>0){var st=J.lastIndexOf('http',m3);if(st>=0){var en=J.indexOf('"',m3);var en2=J.indexOf("'",m3);var en3=J.indexOf(',',m3);var end=J.length;if(en>m3&&en<end)end=en;if(en2>m3&&en2<end)end=en2;if(en3>m3&&en3<end)end=en3;U=J.substring(st,end);}}}
if(!U){var mp=J.indexOf('.mp4');if(mp>0){var st2=J.lastIndexOf('http',mp);if(st2>=0){var e1=J.indexOf('"',mp);var e2=J.indexOf("'",mp);var e3=J.indexOf(',',mp);var ed=J.length;if(e1>mp&&e1<ed)ed=e1;if(e2>mp&&e2<ed)ed=e2;if(e3>mp&&e3<ed)ed=e3;U=J.substring(st2,ed);}}}
U=T(U);
if(U){input={parse:0,jx:0,url:U,header:rule.headers};}else{input={parse:1,jx:0,url:J,header:rule.headers};}
`,
  推荐: `js:
var H='https://band.nnfndyhn.cc';
var IMG_PROXY='http://tpjx.yuexiboke.com/?url=';
var abs=function(u){u=u||'';if(u.indexOf('http://')==0||u.indexOf('https://')==0)return u;if(u.indexOf('//')==0)return 'https:'+u;if(u.charAt(0)=='/')return H+u;return H+'/'+u;};
var txt=function(s){s=s||'';var o='';var inTag=0;for(var n=0;n<s.length;n++){var ch=s.charAt(n);if(ch=='<'){inTag=1;continue;}if(ch=='>'){inTag=0;o+=' ';continue;}if(!inTag)o+=ch;}o=o.split('&nbsp;').join(' ').split('&amp;').join('&').split('&quot;').join('"').split('&#39;').join("'");while(o.indexOf('  ')>=0)o=o.split('  ').join(' ');return o.trim();};
var between=function(s,a,b,from){var x=s.indexOf(a,from||0);if(x<0)return '';var y=s.indexOf('>',x);if(y<0)return '';var z=s.indexOf(b,y+1);if(z<0)return '';return s.substring(y+1,z);};
var pic=function(seg){var k=seg.indexOf('loadBannerDirect');if(k<0)return '';var q1=seg.indexOf("'",k);var q2=seg.indexOf('"',k);var q=q1<0?q2:(q2<0?q1:(q1<q2?q1:q2));if(q<0)return '';var c=seg.charAt(q);var e=seg.indexOf(c,q+1);return e>q?seg.substring(q+1,e):'';};
var parse=function(html){var out=[];var seen={};var pos=0;while(true){var a=html.indexOf('/archives/',pos);if(a<0)break;pos=a+10;var hs=html.lastIndexOf('href=',a);if(hs<0||a-hs>200)continue;var q=html.charAt(hs+5);if(q!='"'&&q!="'")continue;var he=html.indexOf(q,hs+6);if(he<0)continue;var url=abs(html.substring(hs+6,he));if(seen[url])continue;var art=html.lastIndexOf('<article',a);if(art<0||a-art>7000)continue;var end=html.indexOf('</article>',a);if(end<0||end-art>8000)end=a+5000;var seg=html.substring(art,end);if(seg.indexOf('post-card-title')<0)continue;var title=txt(between(seg,'<h2','</h2>',0)).split('热搜 HOT').join('');var ip=seg.indexOf('post-card-info');var desc='';if(ip>=0){var ds=seg.lastIndexOf('<div',ip);var de=seg.indexOf('</div>',ip);if(ds>=0&&de>ds)desc=txt(seg.substring(ds,de+6));}var img=abs(pic(seg));if(img.indexOf('data:image')==0)img='';if(img&&IMG_PROXY.indexOf('你的-worker域名')<0&&(img.indexOf('/xiao/')>=0||img.indexOf('/upload_01/')>=0||img.indexOf('/uploads/')>=0||img.indexOf('/upload/upload/')>=0)){img=IMG_PROXY+encodeURIComponent(img);}if(title){seen[url]=1;out.push({title:title,img:img,desc:desc,url:url,vod_id:url,vod_name:title,vod_pic:img,vod_remarks:desc});}}return out;};
var HS=['https://band.nnfndyhn.cc','https://band.bdleshd.cc','https://band.azoxidnu.cc','https://band.atvfvqjv.cc','http://band.atvfvqjv.cc','http://chigua.com','https://chigua.com'];var html='';var used=H;for(var hi=0;hi<HS.length;hi++){try{H=HS[hi];used=H;rule.host=H;rule.headers.Referer=H+'/';html=request(H+'/',{headers:rule.headers})||'';if(html&&html.indexOf('/archives/')>=0)break;}catch(e){}}var rs=parse(html);if(rs.length<1){rs=[{title:'调试：首页href扫描失败',img:'',desc:'host '+used+' html长度 '+html.length+' archives '+html.indexOf('/archives/')+' article '+html.indexOf('<article'),url:used+'/',vod_id:used+'/',vod_name:'调试：首页href扫描失败',vod_pic:'',vod_remarks:'host '+used+' html长度 '+html.length+' archives '+html.indexOf('/archives/')+' article '+html.indexOf('<article')}];}setResult(rs);
`,
  一级: `js:
var H='https://band.nnfndyhn.cc';var U=input||'';var IMG_PROXY='http://tpjx.yuexiboke.com/?url=';var PG=1;var pm=U.split('/');for(var pi=0;pi<pm.length;pi++){if(pm[pi]&&pm[pi]*1>0)PG=pm[pi]*1;}if(U.indexOf('/category/')>-1&&U.lastIndexOf('/1/')==U.length-3)U=U.substring(0,U.length-3);
var abs=function(u){u=u||'';if(u.indexOf('http://')==0||u.indexOf('https://')==0)return u;if(u.indexOf('//')==0)return 'https:'+u;if(u.charAt(0)=='/')return H+u;return H+'/'+u;};
var txt=function(s){s=s||'';var o='';var inTag=0;for(var n=0;n<s.length;n++){var ch=s.charAt(n);if(ch=='<'){inTag=1;continue;}if(ch=='>'){inTag=0;o+=' ';continue;}if(!inTag)o+=ch;}o=o.split('&nbsp;').join(' ').split('&amp;').join('&').split('&quot;').join('"').split('&#39;').join("'");while(o.indexOf('  ')>=0)o=o.split('  ').join(' ');return o.trim();};
var between=function(s,a,b,from){var x=s.indexOf(a,from||0);if(x<0)return '';var y=s.indexOf('>',x);if(y<0)return '';var z=s.indexOf(b,y+1);if(z<0)return '';return s.substring(y+1,z);};
var pic=function(seg){var k=seg.indexOf('loadBannerDirect');if(k<0)return '';var q1=seg.indexOf("'",k);var q2=seg.indexOf('"',k);var q=q1<0?q2:(q2<0?q1:(q1<q2?q1:q2));if(q<0)return '';var c=seg.charAt(q);var e=seg.indexOf(c,q+1);return e>q?seg.substring(q+1,e):'';};
var parse=function(html){var out=[];var seen={};var pos=0;while(true){var a=html.indexOf('/archives/',pos);if(a<0)break;pos=a+10;var hs=html.lastIndexOf('href=',a);if(hs<0||a-hs>200)continue;var q=html.charAt(hs+5);if(q!='"'&&q!="'")continue;var he=html.indexOf(q,hs+6);if(he<0)continue;var url=abs(html.substring(hs+6,he));if(seen[url])continue;var art=html.lastIndexOf('<article',a);if(art<0||a-art>7000)continue;var end=html.indexOf('</article>',a);if(end<0||end-art>8000)end=a+5000;var seg=html.substring(art,end);if(seg.indexOf('post-card-title')<0)continue;var title=txt(between(seg,'<h2','</h2>',0)).split('热搜 HOT').join('');var ip=seg.indexOf('post-card-info');var desc='';if(ip>=0){var ds=seg.lastIndexOf('<div',ip);var de=seg.indexOf('</div>',ip);if(ds>=0&&de>ds)desc=txt(seg.substring(ds,de+6));}var img=abs(pic(seg));if(img.indexOf('data:image')==0)img='';if(img&&IMG_PROXY.indexOf('你的-worker域名')<0&&(img.indexOf('/xiao/')>=0||img.indexOf('/upload_01/')>=0||img.indexOf('/uploads/')>=0||img.indexOf('/upload/upload/')>=0)){img=IMG_PROXY+encodeURIComponent(img);}if(title){seen[url]=1;out.push({title:title,img:img,desc:desc,url:url,vod_id:url,vod_name:title,vod_pic:img,vod_remarks:desc});}}return out;};
var HS=['https://band.nnfndyhn.cc','https://band.bdleshd.cc','https://band.azoxidnu.cc','https://band.atvfvqjv.cc','http://band.atvfvqjv.cc','http://chigua.com','https://chigua.com'];var html='';var used=H;var path=U;if(path.indexOf('http://')==0||path.indexOf('https://')==0){var x=path.indexOf('/category/');if(x>=0)path=path.substring(x);}for(var hi=0;hi<HS.length;hi++){try{H=HS[hi];used=H;rule.host=H;rule.headers.Referer=H+'/';var full=(path.indexOf('http')==0)?path:(H+path);html=request(full,{headers:rule.headers})||'';if(html&&html.indexOf('/archives/')>=0)break;}catch(e){}}var rs=parse(html);if(rs.length<1){rs=[{title:'调试：分类href扫描失败',img:'',desc:'host '+used+' html长度 '+html.length+' archives '+html.indexOf('/archives/')+' article '+html.indexOf('<article'),url:used+path,vod_id:used+path,vod_name:'调试：分类href扫描失败',vod_pic:'',vod_remarks:'host '+used+' html长度 '+html.length+' archives '+html.indexOf('/archives/')+' article '+html.indexOf('<article')}];}try{MY_PAGE=PG;MY_PAGECOUNT=999;MY_TOTAL=99999;}catch(e){}setResult(rs);
`,
  二级: `js:
var IMG_PROXY='http://tpjx.yuexiboke.com/?url=';
var CUT=function(ss,aa,bb,ff){var ii=ss.indexOf(aa,ff||0);if(ii<0)return '';ii+=aa.length;var jj=ss.indexOf(bb,ii);if(jj<0)return '';return ss.substring(ii,jj);};
var TXT=function(ss){ss=ss||'';var oo='';var tg=0;for(var nn=0;nn<ss.length;nn++){var ch=ss.charAt(nn);if(ch=='<'){tg=1;continue;}if(ch=='>'){tg=0;oo+=' ';continue;}if(!tg)oo+=ch;}oo=oo.split('&nbsp;').join(' ').split('&amp;').join('&').split('&quot;').join('\"').split('&#34;').join('\"').split('&#39;').join("'");while(oo.indexOf('  ')>=0)oo=oo.split('  ').join(' ');return oo.trim();};
var META=function(hh,name){var kk=hh.indexOf(name);if(kk<0)return '';var ss=hh.lastIndexOf('<meta',kk);var ee=hh.indexOf('>',kk);if(ss<0||ee<0)return '';var tag=hh.substring(ss,ee+1);var cc=CUT(tag,'content=\"','\"');if(!cc)cc=CUT(tag,"content='","'");return cc;};
var ABS=function(u){u=u||'';if(u.indexOf('http://')==0||u.indexOf('https://')==0)return u;if(u.indexOf('//')==0)return 'https:'+u;if(u.charAt(0)=='/')return rule.host+u;return rule.host+'/'+u;};
var PXY=function(img){img=ABS(img||'');if(img&&IMG_PROXY.indexOf('你的-worker域名')<0&&(img.indexOf('/xiao/')>=0||img.indexOf('/upload_01/')>=0||img.indexOf('/uploads/')>=0||img.indexOf('/upload/upload/')>=0)){img=IMG_PROXY+encodeURIComponent(img);}return img;};
var CG_HTML=request(input,{headers:rule.headers})||'';
var h1b=CUT(CG_HTML,'<h1','</h1>');var h1p=h1b.indexOf('>');if(h1p>=0)h1b=h1b.substring(h1p+1);var title=TXT(h1b);
var content=META(CG_HTML,'name=\"description\"');if(!content)content=META(CG_HTML,"name='description'");content=TXT(content);
var img='';var ps=CG_HTML.indexOf('post-content');if(ps<0)ps=CG_HTML.indexOf('article-content');if(ps<0)ps=0;
var im=CG_HTML.indexOf('data-xkrkllgl=',ps);
if(im>=0){var q=CG_HTML.charAt(im+14);if(q=='\"'||q=="'"){var ie=CG_HTML.indexOf(q,im+15);if(ie>im)img=CG_HTML.substring(im+15,ie);}}
if(!img){img=META(CG_HTML,'itemprop="image"');if(!img)img=META(CG_HTML,"itemprop='image'");}
img=PXY(img);
var plays=[];var bs=CG_HTML.split('class="dplayer"');for(var ii=1;ii<bs.length;ii++){var block=bs[ii];var vt=CUT(block,'data-video_title="','"');if(!vt)vt=CUT(block,"data-video_title='","'");vt=TXT(vt)||('播放'+ii);if(block.indexOf('data-config=')>=0)plays.push(vt+'$'+input+'@@'+(ii-1));}
if(plays.length<1)plays.push('嗅探播放$'+input);
VOD={vod_id:input,vod_name:title||'详情',vod_pic:img,vod_remarks:content,vod_content:content,vod_play_from:'道长在线',vod_play_url:plays.join('#'),type_name:'类型'};
`,
  搜索: `js:
var H='https://band.nnfndyhn.cc';var U=input||'';var IMG_PROXY='http://tpjx.yuexiboke.com/?url=';var PG=1;var pm=U.split('/');for(var pi=0;pi<pm.length;pi++){if(pm[pi]&&pm[pi]*1>0)PG=pm[pi]*1;}if(U.indexOf('/search/')>-1&&U.lastIndexOf('/1/')==U.length-3)U=U.substring(0,U.length-3);
var abs=function(u){u=u||'';if(u.indexOf('http://')==0||u.indexOf('https://')==0)return u;if(u.indexOf('//')==0)return 'https:'+u;if(u.charAt(0)=='/')return H+u;return H+'/'+u;};
var txt=function(s){s=s||'';var o='';var inTag=0;for(var n=0;n<s.length;n++){var ch=s.charAt(n);if(ch=='<'){inTag=1;continue;}if(ch=='>'){inTag=0;o+=' ';continue;}if(!inTag)o+=ch;}o=o.split('&nbsp;').join(' ').split('&amp;').join('&').split('&quot;').join('"').split('&#39;').join("'");while(o.indexOf('  ')>=0)o=o.split('  ').join(' ');return o.trim();};
var between=function(s,a,b,from){var x=s.indexOf(a,from||0);if(x<0)return '';var y=s.indexOf('>',x);if(y<0)return '';var z=s.indexOf(b,y+1);if(z<0)return '';return s.substring(y+1,z);};
var pic=function(seg){var k=seg.indexOf('loadBannerDirect');if(k<0)return '';var q1=seg.indexOf("'",k);var q2=seg.indexOf('"',k);var q=q1<0?q2:(q2<0?q1:(q1<q2?q1:q2));if(q<0)return '';var c=seg.charAt(q);var e=seg.indexOf(c,q+1);return e>q?seg.substring(q+1,e):'';};
var parse=function(CG_HTML){var out=[];var seen={};var pos=0;while(true){var a=CG_HTML.indexOf('/archives/',pos);if(a<0)break;pos=a+10;var hs=CG_HTML.lastIndexOf('href=',a);if(hs<0||a-hs>200)continue;var q=CG_HTML.charAt(hs+5);if(q!='"'&&q!="'")continue;var he=CG_HTML.indexOf(q,hs+6);if(he<0)continue;var url=abs(CG_HTML.substring(hs+6,he));if(seen[url])continue;var art=CG_HTML.lastIndexOf('<article',a);if(art<0||a-art>7000)continue;var end=CG_HTML.indexOf('</article>',a);if(end<0||end-art>8000)end=a+5000;var seg=CG_HTML.substring(art,end);if(seg.indexOf('post-card-title')<0)continue;var title=txt(between(seg,'<h2','</h2>',0)).split('热搜 HOT').join('');var ip=seg.indexOf('post-card-info');var desc='';if(ip>=0){var ds=seg.lastIndexOf('<div',ip);var de=seg.indexOf('</div>',ip);if(ds>=0&&de>ds)desc=txt(seg.substring(ds,de+6));}var img=abs(pic(seg));if(img.indexOf('data:image')==0)img='';if(img&&IMG_PROXY.indexOf('你的-worker域名')<0&&(img.indexOf('/xiao/')>=0||img.indexOf('/upload_01/')>=0||img.indexOf('/uploads/')>=0||img.indexOf('/upload/upload/')>=0)){img=IMG_PROXY+encodeURIComponent(img);}if(title){seen[url]=1;out.push({title:title,img:img,desc:desc,url:url,vod_id:url,vod_name:title,vod_pic:img,vod_remarks:desc});}}return out;};
var HS=['https://band.nnfndyhn.cc','https://band.bdleshd.cc','https://band.azoxidnu.cc','https://band.atvfvqjv.cc','http://band.atvfvqjv.cc','http://chigua.com','https://chigua.com'];var CG_HTML='';var used=H;var path=U;if(path.indexOf('http://')==0||path.indexOf('https://')==0){var x=path.indexOf('/search/');if(x>=0)path=path.substring(x);}for(var hi=0;hi<HS.length;hi++){try{H=HS[hi];used=H;rule.host=H;rule.headers.Referer=H+'/';var full=(path.indexOf('http')==0)?path:(H+path);CG_HTML=request(full,{headers:rule.headers})||'';if(CG_HTML&&CG_HTML.indexOf('/archives/')>=0)break;}catch(e){}}var rs=parse(CG_HTML);if(rs.length<1){rs=[{title:'调试：搜索href扫描失败',img:'',desc:'host '+used+' html长度 '+CG_HTML.length+' archives '+CG_HTML.indexOf('/archives/')+' article '+CG_HTML.indexOf('<article'),url:used+path,vod_id:used+path,vod_name:'调试：搜索href扫描失败',vod_pic:'',vod_remarks:'host '+used+' html长度 '+CG_HTML.length+' archives '+CG_HTML.indexOf('/archives/')+' article '+CG_HTML.indexOf('<article')}];}try{MY_PAGE=PG;MY_PAGECOUNT=999;MY_TOTAL=99999;}catch(e){}setResult(rs);
`
};
