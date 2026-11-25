const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

function fixture() {
  const elements = new Map();
  function element() {
    return { innerHTML: '', hidden: false, textContent: '', dataset: {},
      setAttribute() {}, append() {}, classList: { add() {}, remove() {}, toggle() {} },
      querySelector(id) { return elements.get(id) || elements.set(id, element()).get(id); } };
  }
  const storage = new Map();
  const context = vm.createContext({
    document: { getElementById(id) { return elements.get(id) || elements.set(id, element()).get(id); },
      createElement: element, querySelector: element, querySelectorAll: () => [], addEventListener() {} },
    window: { scrollTo() {} }, navigator: {}, console, setTimeout, clearTimeout,
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) }
  });
  vm.runInContext(fs.readFileSync('src/mobile-app.js', 'utf8').replace(/home\(false\);\s*$/, ''), context);
  return { context, elements, run: code => vm.runInContext(code, context) };
}

test('10,000 episodes render only 25; saved episode opens its correct page and stays highlighted', () => {
  const f = fixture();
  f.run(`state.current = {id:'series', videos: Array.from({length:10000}, (_,i) => ({id:'ep'+(i+1), title:'Tập '+(i+1)}))}; rememberEpisode('ep9876'); renderEpisodes();`);
  const html = f.elements.get('episode-browser').innerHTML;
  assert.equal((html.match(/class="episode-chip /g) || []).length, 25);
  assert.match(html, /is-current[^]*data-id="ep9876" aria-current="true"/);
  assert.match(html, /value="395" selected/);
  assert.match(html, /Xem tiếp: Tập 9876/);
  f.run('renderEpisodes(999999)');
  assert.equal(f.run('state.episodePage'), 399);
});

test('search text survives state recreation including clearing', () => {
  const f = fixture();
  f.run(`writeStored('vietstream-search', 'Conan');`);
  assert.equal(f.run(`readStored('vietstream-search', '')`), 'Conan');
  f.run(`writeStored('vietstream-search', '');`);
  assert.equal(f.run(`readStored('vietstream-search', 'fallback')`), '');
});

test('mode must be selected before episodes become available; only selected version episodes appear', async () => {
  const f = fixture();
  f.run(`state.view='detail'; state.current={id:'show',videos:[{id:'ep1'},{id:'ep2'}],audioVersions:[{name:'Vietsub',videos:[{id:'ep1'},{id:'ep2'}]},{name:'Lồng tiếng',videos:[{id:'ep1'}]}]}; state.audioModes=[]; state.selectedAudio='';`);
  await f.run('prepareAudioPicker(state.current)');
  assert.equal(f.elements.get('movie-play').disabled, true);
  assert.doesNotMatch(f.elements.get('episode-browser').innerHTML, /class="episode-chip/);
  f.elements.get('audio-mode').onchange({target:{value:'Lồng tiếng'}});
  assert.equal(f.elements.get('movie-play').disabled, false);
  assert.equal((f.elements.get('episode-browser').innerHTML.match(/class="episode-chip/g)||[]).length, 1);
  assert.equal(f.run(`readStored('vietstream-audio:show',null).audio`), 'Lồng tiếng');
});

function player(f, episode = 'ep1') {
  const handlers = {};
  const video = { duration: 120, currentTime: 109, paused: false, isConnected: true,
    addEventListener(name, fn) { handlers[name] = fn; }, removeEventListener(name) { delete handlers[name]; }, pause() {} };
  let panel;
  f.context.video = video;
  f.context.wrap = { append(p) { panel = p; } };
  f.run(`state.current = {id:'series', videos:[{id:'ep1'},{id:'ep2'}]}; state.view='player'; state.episodeId='${episode}'; calls=[]; advanceNextEpisode=(...args)=>calls.push(args); setupNextEpisode(video,wrap);`);
  return { video, handlers, get panel() { return panel; } };
}

test('audio preference survives quality or provider changes and never falls back to another language', () => {
  const f = fixture();
  f.run(`state.current={id:'movie'}; rememberSource({name:'KKPhim 1080p · Lồng tiếng'});`);
  assert.equal(f.run(`readStored('vietstream-audio:movie', null).audio`), 'Lồng tiếng');
  f.run(`choices=[{name:'KKPhim HD · Vietsub'}, {name:'OPhim 720p · Lồng tiếng'}, {name:'KKPhim HD · Thuyết minh'}];`);
  assert.equal(f.run(`preferredStream(choices, state.audioPreference).name`), 'OPhim 720p · Lồng tiếng');
  assert.equal(f.run(`preferredStream([choices[0],choices[2]], state.audioPreference)`), null);
  assert.equal(f.run(`audioMode(choices[2])`), 'Thuyết minh');
});

test('opening episode with multiple audio versions presents choices instead of auto-playing first source', async () => {
  const f = fixture();
  f.context.fetch = async () => ({ok:true,json:async()=>({streams:[{name:'VN · Vietsub',url:'sub.m3u8'},{name:'VN · Lồng tiếng',url:'dub.m3u8'}]})});
  f.run(`state.current={id:'movie',type:'series'}; play=()=>{throw Error('Must offer audio choices')};`);
  await f.run(`streams('ep1',true,true)`);
  const html = f.elements.get('app-content').innerHTML;
  assert.match(html, /data-mode="Lồng tiếng"/);
  assert.match(html, /data-mode="Vietsub"/);
  assert.equal(f.run('state.view'), 'streams');
});

test('suggests at 10s, waits through pause/buffering, then advances exactly once at ended', () => {
  const f = fixture(); const p = player(f);
  p.handlers.timeupdate(); assert.equal(p.panel.hidden, true);
  p.video.currentTime = 110; p.handlers.timeupdate(); assert.equal(p.panel.hidden, false);
  p.video.paused = true; p.handlers.pause();
  assert.equal(f.run('calls.length'), 0);
  p.handlers.ended(); p.handlers.ended();
  assert.equal(f.run('calls.length'), 1);
  assert.equal(f.run('calls[0][0].id'), 'ep2');
  assert.equal(f.run('calls[0][1] === video'), true);
});

test('cancel prevents automatic next; navigating away removes listeners', () => {
  const f = fixture(); const p = player(f);
  f.elements.get('#next-cancel').onclick(); p.handlers.ended();
  assert.equal(f.run('calls.length'), 0);
  f.run('state.playbackCleanup()');
  assert.equal(Object.keys(p.handlers).length, 0);
});

test('last episode never suggests a nonexistent next episode', () => {
  const f = fixture(); const p = player(f, 'ep2');
  assert.equal(p.panel, undefined);
  assert.equal(Object.keys(p.handlers).length, 0);
});

 test('next episode changes source in place without rebuilding fullscreen video or navigation', async () => {
  const f = fixture();
  f.context.fetch = async () => ({ok:true,json:async()=>({streams:[{name:'VN',url:'next.m3u8'}]})});
  f.context.video = {isConnected:true};
  f.context.wrap = {};
  f.context.panel = {hidden:true,querySelector:()=>({textContent:''}),remove(){}};
  f.run(`state.view='player'; state.current={id:'series',type:'series'}; state.episodeId='ep1'; state.preferredSource='VN'; loaded=null; setupNextEpisode=()=>{}; loadVideoSource=(v,s)=>{loaded={v,s}}; setView=()=>{throw Error('Must not navigate or rebuild fullscreen')};`);
  await f.run(`advanceNextEpisode({id:'ep2'},video,wrap,panel,()=>{})`);
  assert.equal(f.run('state.episodeId'), 'ep2');
  assert.equal(f.run('loaded.v === video'), true);
  assert.equal(f.run('loaded.s.url'), 'next.m3u8');
});
