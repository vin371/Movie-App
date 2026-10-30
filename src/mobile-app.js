const app = document.getElementById('app-content');
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const api = async path => { const response = await fetch(path); if (!response.ok) throw Error(`API ${response.status}`); return response.json(); };

// Quản lý state tập trung, giữ query tìm kiếm & lịch sử điều hướng (back stack)
const state = {
  view: 'home',
  items: [],
  current: null,
  streams: [],
  lastDetail: null,
  hls: null,
  searchQuery: '',
  searchResults: [],
  history: []
};

const saved = () => { try { return JSON.parse(localStorage.getItem('vietstream-library') || '[]'); } catch { return []; } };
const save = items => localStorage.setItem('vietstream-library', JSON.stringify(items));
const status = (message) => { app.innerHTML = `<div class="loading">${escapeHtml(message)}</div>`; };
const error = (e) => { app.innerHTML = `<div class="page"><h1 class="page-title">Không tải được nội dung</h1><p class="notice">${escapeHtml(e.message)}</p><button class="primary" data-action="home">Thử lại</button></div>`; };

// Thông báo cho Android app lock/unlock xoay màn hình
function notifyNativeOrientation(mode) {
  if (window.VietStreamAndroid) {
    if (mode === 'player') {
      if (typeof window.VietStreamAndroid.enterPlayerMode === 'function') {
        window.VietStreamAndroid.enterPlayerMode();
      }
    } else {
      if (typeof window.VietStreamAndroid.enterPortraitMode === 'function') {
        window.VietStreamAndroid.enterPortraitMode();
      }
    }
  }
}

function setView(view, push = true) {
  if (push && state.view && state.view !== view) {
    state.history.push({ view: state.view, lastDetail: state.lastDetail ? { ...state.lastDetail } : null });
  }
  state.view = view;
  document.querySelectorAll('[data-view]').forEach(b => b.classList.toggle('active', b.dataset.view === view));
  window.scrollTo(0, 0);

  // Khóa màn hình xoay dọc khi ở các màn thông thường, cho phép xoay khi ở player
  if (view === 'player') {
    notifyNativeOrientation('player');
  } else {
    notifyNativeOrientation('portrait');
  }
}

function goBack() {
  if (state.view === 'player') {
    if (state.lastDetail) {
      streams(state.lastDetail.id, false);
      return;
    }
  }
  if (state.view === 'streams') {
    if (state.lastDetail) {
      detail(state.lastDetail.type, state.lastDetail.id, false);
      return;
    }
  }

  const prev = state.history.pop();
  if (!prev) {
    home(false);
    return;
  }

  if (prev.view === 'search') {
    search(false);
  } else if (prev.view === 'series') {
    series(false);
  } else if (prev.view === 'library') {
    library(false);
  } else if (prev.view === 'detail' && prev.lastDetail) {
    detail(prev.lastDetail.type, prev.lastDetail.id, false);
  } else {
    home(false);
  }
}

// Bắt phím Back của Android thông qua WebView
window.handleAndroidBack = function() {
  if (state.view === 'home' && state.history.length === 0) {
    return false; // Cho phép Android thoát hoặc xử lý bình thường
  }
  goBack();
  return true;
};

function card(item) {
  return `<button class="card" data-action="detail" data-id="${escapeHtml(item.id)}" data-type="${escapeHtml(item.type || 'movie')}"><img class="poster" src="${escapeHtml(item.poster || item.background || '')}" alt="" loading="lazy"><div class="card-body"><div class="card-title">${escapeHtml(item.name)}</div><div class="card-info">${escapeHtml(item.releaseInfo || item.year || 'Phim')}</div></div></button>`;
}

const catalog = async (type, id) => { try { return (await api(`/catalog/${type}/${id}.json`)).metas || []; } catch { return []; } };

async function home(push = true) {
  setView('home', push);
  status('Đang tải phim...');
  try {
    const [featured, other, seriesList] = await Promise.all([
      catalog('movie', 'phim4k-phim-le'),
      catalog('movie', 'nguonc-phim-le'),
      catalog('series', 'nguonc-phim-bo')
    ]);
    state.items = [...featured, ...other, ...seriesList];
    const hero = featured[0] || other[0];
    if (!hero) {
      app.innerHTML = '<div class="empty">Hiện chưa có dữ liệu phim từ các nguồn.</div>';
      return;
    }
    app.innerHTML = `<section class="hero"><img class="hero-image" src="${escapeHtml(hero.background || hero.poster)}" alt=""><div class="hero-content"><div class="eyebrow">Phim nổi bật · ${escapeHtml(hero.releaseInfo || hero.year)}</div><h1>${escapeHtml(hero.name)}</h1><p>Chọn phim và nguồn phát để bắt đầu xem. Danh sách được cập nhật trực tiếp từ máy chủ VietStream.</p><div class="actions"><button class="primary" data-action="detail" data-id="${escapeHtml(hero.id)}" data-type="${escapeHtml(hero.type || 'movie')}">▶ Xem ngay</button><button class="secondary" data-action="library-toggle" data-id="${escapeHtml(hero.id)}">＋ Tủ phim</button></div></div></section><section><div class="section-head"><h2>Phim mới cập nhật</h2><small>${featured.length} phim</small></div><div class="grid">${featured.map(card).join('')}</div></section><section><div class="section-head"><h2>Khám phá thêm</h2><small>NguonC</small></div><div class="grid">${other.map(card).join('')}</div></section><section><div class="section-head"><h2>Phim bộ</h2><small>NguonC</small></div><div class="grid">${seriesList.slice(0, 10).map(card).join('')}</div></section>`;
  } catch (e) {
    error(e);
  }
}

async function series(push = true) {
  setView('series', push);
  status('Đang tải phim bộ...');
  try {
    const items = await catalog('series', 'nguonc-phim-bo');
    state.items.push(...items);
    app.innerHTML = `<div class="page"><h1 class="page-title">Phim bộ</h1><p class="page-subtitle">Chọn phim để xem danh sách tập và nguồn phát.</p>${items.length ? `<div class="grid">${items.map(card).join('')}</div>` : '<div class="empty">Chưa có phim bộ từ nguồn hiện tại.</div>'}</div>`;
  } catch (e) {
    error(e);
  }
}

function library(push = true) {
  setView('library', push);
  const items = saved();
  app.innerHTML = `<div class="page"><h1 class="page-title">Tủ phim</h1><p class="page-subtitle">Các phim bạn đã lưu trên thiết bị này.</p>${items.length ? `<div class="grid">${items.map(card).join('')}</div>` : '<div class="empty">Tủ phim còn trống. Mở chi tiết phim và chọn “Lưu vào tủ phim”.</div>'}</div>`;
}

function search(push = true) {
  setView('search', push);
  const prevQuery = state.searchQuery || '';
  app.innerHTML = `<div class="page"><h1 class="page-title">Tìm kiếm</h1><p class="page-subtitle">Tìm phim theo tên từ các nguồn đang hoạt động.</p><input class="search-input" id="search-box" placeholder="Nhập tên phim..." autocomplete="off" value="${escapeHtml(prevQuery)}"><div id="search-results"></div></div>`;
  const input = document.getElementById('search-box');
  const output = document.getElementById('search-results');

  // Khôi phục kết quả đã tìm kiếm trước đó nếu có
  if (state.searchResults && state.searchResults.length > 0) {
    output.innerHTML = `<div class="grid">${state.searchResults.map(card).join('')}</div>`;
  }

  let timer;
  input.oninput = () => {
    clearTimeout(timer);
    timer = setTimeout(() => runSearch(input.value.trim()), 300);
  };
  input.focus();
}

async function runSearch(query) {
  state.searchQuery = query;
  const output = document.getElementById('search-results');
  if (!output) return;
  if (!query) {
    state.searchResults = [];
    output.replaceChildren();
    return;
  }
  output.innerHTML = '<div class="loading">Đang tìm...</div>';
  const configs = [['movie', 'phim4k-phim-le'], ['movie', 'nguonc-phim-le'], ['series', 'nguonc-phim-bo']];
  try {
    const items = (await Promise.all(configs.map(async ([type, id]) => {
      try {
        return (await api(`/catalog/${type}/${id}/search=${encodeURIComponent(query)}.json`)).metas || [];
      } catch {
        return [];
      }
    }))).flat();

    if (document.getElementById('search-box')?.value.trim() !== query) return;
    state.searchResults = items;
    state.items.push(...items);
    output.innerHTML = items.length ? `<div class="grid">${items.map(card).join('')}</div>` : '<div class="empty">Không tìm thấy phim phù hợp.</div>';
  } catch {
    output.innerHTML = '<div class="empty">Lỗi khi tìm kiếm, vui lòng thử lại.</div>';
  }
}

async function detail(type, id, push = true) {
  setView('detail', push);
  status('Đang tải chi tiết phim...');
  try {
    const { meta: m } = await api(`/meta/${type}/${encodeURIComponent(id)}.json`);
    state.current = m;
    state.lastDetail = { type, id };
    const inLibrary = saved().some(x => x.id === m.id);
    app.innerHTML = `<div class="page"><button class="back" data-action="back">← Quay lại</button><div class="detail"><img class="detail-poster" src="${escapeHtml(m.poster || m.background)}" alt=""><div><div class="eyebrow">${type === 'series' ? 'Phim bộ' : 'Phim lẻ'}</div><h1>${escapeHtml(m.name)}</h1><div class="meta-line">${escapeHtml(m.releaseInfo || m.year || '')} ${m.runtime ? `· ${escapeHtml(m.runtime)}` : ''}</div><p class="description">${escapeHtml(m.description || 'Chưa có mô tả.')}</p><div class="actions"><button class="primary" data-action="streams" data-id="${escapeHtml(m.id)}">▶ Xem phim</button><button class="secondary" data-action="library-toggle" data-id="${escapeHtml(m.id)}">${inLibrary ? '✓ Đã lưu' : '＋ Lưu vào tủ phim'}</button></div></div></div>${m.videos?.length ? `<div class="section-head"><h2>Danh sách tập</h2><small>${m.videos.length} tập</small></div><div class="episode-list">${m.videos.map((v, i) => `<button class="row-button" data-action="streams" data-id="${escapeHtml(v.id)}"><span>${escapeHtml(v.title || `Tập ${i + 1}`)}</span><span>▶</span></button>`).join('')}</div>` : ''}</div>`;
  } catch (e) {
    error(e);
  }
}

async function streams(id, push = true) {
  const m = state.current;
  if (!m) return;
  setView('streams', push);
  status('Đang lấy nguồn phát...');
  try {
    const { streams: list } = await api(`/stream/${m.type || 'movie'}/${encodeURIComponent(id)}.json`);
    state.streams = list || [];
    app.innerHTML = `<div class="page"><button class="back" data-action="detail-back">← Chi tiết phim</button><h1 class="page-title">${escapeHtml(m.name)}</h1><p class="page-subtitle">Chọn nguồn phát</p>${list?.length ? `<div class="source-list">${list.map((s, i) => `<button class="row-button" data-action="play" data-index="${i}"><span>${escapeHtml(s.name || s.title || `Nguồn ${i + 1}`)}</span><span>▶</span></button>`).join('')}</div>` : '<div class="empty">Phim này chưa có nguồn phát. Hãy thử phim khác.</div>'}</div>`;
  } catch (e) {
    error(e);
  }
}

function play(index, push = true) {
  const source = state.streams[index];
  if (!source) return;
  setView('player', push);
  state.hls?.destroy();
  state.hls = null;
  app.innerHTML = `<div class="page"><button class="back" data-action="streams-back">← Chọn nguồn khác</button><h1 class="page-title">${escapeHtml(state.current?.name)}</h1><p class="page-subtitle">${escapeHtml(source.name || source.title)}</p><div class="player-wrap" id="player-wrap"></div><p class="notice" id="player-status" hidden></p></div>`;
  const wrap = document.getElementById('player-wrap');
  const statusEl = document.getElementById('player-status');

  if (source.externalUrl) {
    const frame = document.createElement('iframe');
    frame.title = 'Trình phát phim';
    frame.allow = 'autoplay; fullscreen; picture-in-picture';
    frame.allowFullscreen = true;
    frame.src = source.externalUrl;
    wrap.append(frame);
    return;
  }
  if (!source.url) {
    statusEl.hidden = false;
    statusEl.textContent = 'Nguồn này không có liên kết phát.';
    return;
  }

  const video = document.createElement('video');
  video.controls = true;
  video.autoplay = true;
  video.playsInline = true;
  wrap.append(video);
  video.onerror = () => {
    statusEl.hidden = false;
    statusEl.textContent = 'Không phát được nguồn này. Hãy quay lại chọn nguồn khác.';
  };

  const attach = () => {
    if (window.Hls?.isSupported()) {
      // Cấu hình Hls.js buffer tối đa & giải mã mượt mà chống giật lag
      const h = new Hls({
        maxBufferLength: 60,
        maxMaxBufferLength: 120,
        maxBufferSize: 60 * 1000 * 1000,
        enableWorker: true,
        lowLatencyMode: false,
        backBufferLength: 30,
        fragLoadingTimeOut: 20000,
        manifestLoadingTimeOut: 15000
      });
      state.hls = h;
      h.loadSource(source.url);
      h.attachMedia(video);
      h.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              h.startLoad();
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              h.recoverMediaError();
              break;
            default:
              statusEl.hidden = false;
              statusEl.textContent = `Lỗi luồng phát: ${data.details}`;
              break;
          }
        }
      });
    } else {
      video.src = source.url;
    }
  };

  if (window.Hls) {
    attach();
  } else {
    const script = document.createElement('script');
    script.src = '/hls.min.js';
    script.onload = attach;
    script.onerror = () => { video.src = source.url; };
    document.head.append(script);
  }
}

function toggleLibrary(id) {
  const item = state.current?.id === id ? state.current : state.items.find(x => x.id === id);
  if (!item) return;
  let list = saved();
  list = list.some(x => x.id === id) ? list.filter(x => x.id !== id) : [...list, item];
  save(list);
  if (state.view === 'detail') detail(state.lastDetail.type, state.lastDetail.id, false);
  else if (state.view === 'library') library(false);
  else {
    const button = document.querySelector('[data-action="library-toggle"]');
    if (button) button.textContent = list.some(x => x.id === id) ? '✓ Đã lưu' : '＋ Tủ phim';
  }
}

document.addEventListener('click', event => {
  const button = event.target.closest('[data-action],[data-view]');
  if (!button) return;
  const { action, view, id, type, index } = button.dataset;
  if (view) {
    event.preventDefault();
    ({ home, series, search, library })[view]();
    return;
  }
  if (action === 'detail') detail(type, id);
  else if (action === 'streams') streams(id);
  else if (action === 'play') play(Number(index));
  else if (action === 'library-toggle') toggleLibrary(id);
  else if (action === 'detail-back') {
    if (state.lastDetail) detail(state.lastDetail.type, state.lastDetail.id, false);
    else goBack();
  }
  else if (action === 'streams-back') {
    if (state.lastDetail) streams(state.lastDetail.id, false);
    else goBack();
  }
  else if (action === 'back') goBack();
  else if (action === 'home') home();
});

document.getElementById('header-search').onclick = () => search();
document.querySelector('.brand').onclick = e => {
  e.preventDefault();
  home();
};

home(false);
