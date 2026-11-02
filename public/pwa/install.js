if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js').catch(() => {}));
const standalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone;
if (!standalone && /iPad|iPhone|iPod/.test(navigator.userAgent) && !sessionStorage.getItem('install-dismissed')) {
  const tip = document.createElement('aside');
  tip.className = 'install-tip';
  tip.setAttribute('aria-label', 'Cài VietStream trên iPhone');
  tip.innerHTML = '<strong>Cài VietStream trên iPhone</strong><p>Trong Safari, nhấn Chia sẻ ↑ rồi chọn “Thêm vào Màn hình chính”.</p><button type="button" aria-label="Đóng hướng dẫn cài đặt">Đã hiểu</button>';
  tip.querySelector('button').onclick = () => { sessionStorage.setItem('install-dismissed', '1'); tip.remove(); };
  document.body.append(tip);
}
