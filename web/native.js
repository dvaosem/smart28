/* smart28 – doplnky pre Android APK (widget, aktualizácie) + obľúbené ★. V prehliadači sú obľúbené len lokálne. */
(function(){
  'use strict';
  var B = window.AndroidBridge;
  var favs = [];
  try { favs = JSON.parse(localStorage.getItem('s28fav') || '[]'); } catch (e) {}

  function saveFavs(){
    try { localStorage.setItem('s28fav', JSON.stringify(favs)); } catch (e) {}
    if (B && B.saveFavorites) B.saveFavorites(JSON.stringify(favs));
  }
  function pushSession(){
    if (!B || !B.saveSession || !window.DVAOSEM_USER) return;
    try { B.saveSession(DVAOSEM_FIREBASE_CONFIG.apiKey, window.DVAOSEM_USER.refreshToken, API); } catch (e) {}
  }
  function pushDevices(){
    if (!B || !B.saveDevices) return;
    try {
      B.saveDevices(JSON.stringify(devices.map(function(d){ return {id:d.id, name:d.name, category:d.category, online:d.online, status:d.status}; })));
    } catch (e) {}
  }

  // ★ na kartách
  var origCard = window.card;
  if (typeof origCard === 'function') {
    window.card = function(d){
      var c = origCard(d), h = c.querySelector('h3');
      if (h) {
        var s = document.createElement('span');
        var paint = function(){ s.textContent = favs.indexOf(d.id) >= 0 ? '★' : '☆'; };
        paint();
        s.style.cssText = 'cursor:pointer;float:right;font-size:18px;line-height:1';
        s.title = 'Zobraziť vo widgete';
        s.onclick = function(){
          favs = favs.indexOf(d.id) >= 0 ? favs.filter(function(x){ return x !== d.id; }) : favs.concat([d.id]);
          saveFavs(); paint();
        };
        h.insertBefore(s, h.firstChild);
      }
      return c;
    };
  }

  // po každom vykreslení pošli stav do APK (widget)
  var origRender = window.renderDevs;
  if (typeof origRender === 'function') {
    window.renderDevs = function(){ origRender(); pushSession(); pushDevices(); };
  }
  document.addEventListener('dvaosem-auth-ok', function(){ pushSession(); if (B && B.saveFavorites) B.saveFavorites(JSON.stringify(favs)); });

  if (!B || !B.checkUpdate) return; // zvyšok len v APK

  // lišta aktualizácie + pätička s verziou a widgetom
  var bar = document.createElement('div');
  bar.style.cssText = 'display:none;position:fixed;left:0;right:0;bottom:0;z-index:20;background:#b8a994;color:#22201d;padding:10px 16px;text-align:center';
  document.body.appendChild(bar);
  var info = document.createElement('div');
  info.className = 'mut'; info.style.cssText = 'text-align:center;padding:0 0 24px';
  var ver = '';
  try { ver = 'APK ' + B.getAppVersion() + ' (build ' + B.getAppBuild() + ')'; } catch (e) {}
  info.textContent = ver + ' · ';
  var wb = document.createElement('button'); wb.textContent = 'Pridať widget';
  wb.onclick = function(){ if (!B.pinWidget()) toast('Launcher nepodporuje pripnutie – pridaj widget ručne', true); };
  var ub = document.createElement('button'); ub.textContent = 'Skontrolovať aktualizáciu'; ub.style.marginLeft = '6px';
  ub.onclick = function(){ B.checkUpdate(true); };
  info.appendChild(wb); info.appendChild(ub);
  document.body.insertBefore(info, document.querySelector('footer'));

  window.s28OnUpdate = function(r){
    if (r.error) { if (r.manual) toast('Kontrola aktualizácie zlyhala', true); return; }
    if (r.latest > r.current) {
      bar.style.display = 'block';
      bar.textContent = 'Nová verzia APK (build ' + r.latest + ') ';
      var b = document.createElement('button'); b.textContent = 'Aktualizovať';
      b.onclick = function(){ b.disabled = true; B.installUpdate(); };
      bar.appendChild(b);
    } else if (r.manual) toast('APK je aktuálne');
  };
  window.s28OnUpdateProgress = function(p){
    if (p === -2) toast('Povoľ inštaláciu z tohto zdroja a skús znova', true);
    else if (p === -1) { toast('Aktualizácia zlyhala', true); bar.style.display = 'none'; }
    else if (p === 101) bar.style.display = 'none';
    else if (p >= 0) { bar.firstChild.textContent = 'Sťahujem ' + p + ' % '; }
  };
  var last = 0;
  function check(){ var n = Date.now(); if (n - last < 30 * 60 * 1000) return; last = n; B.checkUpdate(false); }
  check();
  document.addEventListener('visibilitychange', function(){ if (!document.hidden) check(); });
})();
